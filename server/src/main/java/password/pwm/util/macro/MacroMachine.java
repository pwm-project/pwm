/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.util.macro;

import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.CommonValues;
import password.pwm.http.PwmRequest;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MacroMachine
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( MacroMachine.class );

    private final PwmApplication pwmApplication;
    private final SessionLabel sessionLabel;
    private final UserInfo userInfo;
    private final LoginInfoBean loginInfoBean;
    private final StringReplacer stringReplacer;

    private static final Map<MacroImplementation.Scope, Map<Pattern, MacroImplementation>> BUILTIN_MACROS = makeImplementations();

    public MacroMachine(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserInfo userInfo,
            final LoginInfoBean loginInfoBean,
            final StringReplacer stringReplacer
    )
    {
        this.pwmApplication = pwmApplication;
        this.sessionLabel = sessionLabel;
        this.userInfo = userInfo;
        this.loginInfoBean = loginInfoBean;
        this.stringReplacer = stringReplacer;
    }

    private static Map<MacroImplementation.Scope, Map<Pattern, MacroImplementation>> makeImplementations( )
    {
        final Map<Class<? extends MacroImplementation>, MacroImplementation.Scope> implementations = new LinkedHashMap<>();
        implementations.putAll( StandardMacros.STANDARD_MACROS );
        implementations.putAll( InternalMacros.INTERNAL_MACROS );
        final LinkedHashMap<MacroImplementation.Scope, Map<Pattern, MacroImplementation>> map = new LinkedHashMap<>();

        for ( final Map.Entry<Class<? extends MacroImplementation>, MacroImplementation.Scope> entry : implementations.entrySet() )
        {
            final Class macroClass = entry.getKey();
            final MacroImplementation.Scope scope = entry.getValue();
            try
            {
                final MacroImplementation macroImplementation = ( MacroImplementation ) macroClass.newInstance();
                final Pattern pattern = macroImplementation.getRegExPattern();
                if ( !map.containsKey( scope ) )
                {
                    map.put( scope, new LinkedHashMap<>() );
                }
                map.get( scope ).put( pattern, macroImplementation );
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "unable to load macro class " + macroClass.getName() + ", error: " + e.getMessage() );
            }
        }

        return map;
    }

    private Map<Pattern, MacroImplementation> makeExternalImplementations( final PwmApplication pwmApplication )
    {
        final LinkedHashMap<Pattern, MacroImplementation> map = new LinkedHashMap<>();
        final List<String> externalMethods = ( pwmApplication == null )
                ? Collections.emptyList()
                : pwmApplication.getConfig().readSettingAsStringArray( PwmSetting.EXTERNAL_MACROS_REST_URLS );

        int iteration = 0;
        for ( final String url : externalMethods )
        {
            iteration++;
            final MacroImplementation macroImplementation = new ExternalRestMacro( iteration, url );
            final Pattern pattern = macroImplementation.getRegExPattern();
            map.put( pattern, macroImplementation );
        }
        return map;
    }


    public String expandMacros(
            final String input
    )
    {
        if ( input == null )
        {
            return null;
        }

        if ( input.length() < 1 )
        {
            return input;
        }

        final MacroImplementation.MacroRequestInfo macroRequestInfo = new MacroImplementation.MacroRequestInfo()
        {
            @Override
            public PwmApplication getPwmApplication( )
            {
                return pwmApplication;
            }

            @Override
            public UserInfo getUserInfo( )
            {
                return userInfo;
            }

            @Override
            public LoginInfoBean getLoginInfoBean( )
            {
                return loginInfoBean;
            }
        };

        final Set<MacroImplementation.Scope> scopes = effectiveScopes( macroRequestInfo );
        final Map<Pattern, MacroImplementation> macroImplementations = new LinkedHashMap<>();
        //First the User macros
        if ( scopes.contains( MacroImplementation.Scope.User ) )
        {
            macroImplementations.putAll( makeExternalImplementations( pwmApplication ) );
        }
        //last the buitin macros for Encrypt/Encode to work properly
        for ( final MacroImplementation.Scope scope : scopes )
        {
            macroImplementations.putAll( BUILTIN_MACROS.get( scope ) );
        }


        String workingString = input;
        final String previousString = workingString;

        for ( final MacroImplementation.Sequence sequence : MacroImplementation.Sequence.values() )
        {
            for ( final Map.Entry<Pattern, MacroImplementation> entry : macroImplementations.entrySet() )
            {
                final Pattern pattern = entry.getKey();
                final MacroImplementation pwmMacro = entry.getValue();
                if ( pwmMacro.getSequence() == sequence )
                {
                    boolean matched = true;
                    while ( matched )
                    {
                        final Matcher matcher = pattern.matcher( workingString );
                        if ( matcher.find() )
                        {
                            workingString = doReplace( workingString, pwmMacro, matcher, macroRequestInfo );
                            if ( workingString.equals( previousString ) )
                            {
                                LOGGER.warn( sessionLabel, () -> "macro replace was called but input string was not modified.  "
                                        + " macro=" + pwmMacro.getClass().getName() + ", pattern=" + pwmMacro.getRegExPattern().toString() );
                                break;
                            }
                        }
                        else
                        {
                            matched = false;
                        }
                    }
                }
            }
        }

        return workingString;
    }

    private static Set<MacroImplementation.Scope> effectiveScopes( final MacroImplementation.MacroRequestInfo macroRequestInfo )
    {
        final Set<MacroImplementation.Scope> scopes = new HashSet<>();
        scopes.add( MacroImplementation.Scope.Static );

        final PwmApplication pwmApplication = macroRequestInfo.getPwmApplication();
        final PwmApplicationMode mode = pwmApplication != null ? pwmApplication.getApplicationMode() : PwmApplicationMode.ERROR;
        final boolean appModeOk = mode == PwmApplicationMode.RUNNING || mode == PwmApplicationMode.CONFIGURATION;
        if ( appModeOk )
        {
            scopes.add( MacroImplementation.Scope.System );

            if ( macroRequestInfo.getUserInfo() != null )
            {
                scopes.add( MacroImplementation.Scope.User );
            }
        }
        return Collections.unmodifiableSet( scopes );
    }


    private String doReplace(
            final String input,
            final MacroImplementation macroImplementation,
            final Matcher matcher,
            final MacroImplementation.MacroRequestInfo macroRequestInfo
    )
    {
        final Instant startTime = Instant.now();
        final String matchedStr = matcher.group();
        final int startPos = matcher.start();
        final int endPos = matcher.end();
        String replaceStr = "";
        try
        {
            replaceStr = macroImplementation.replaceValue( matchedStr, macroRequestInfo );
        }
        catch ( final MacroParseException e )
        {
            LOGGER.debug( sessionLabel, () -> "macro parse error replacing macro '" + matchedStr + "', error: " + e.getMessage() );
            if ( pwmApplication != null )
            {
                replaceStr = "[" + e.getErrorInformation().toUserStr( PwmConstants.DEFAULT_LOCALE, macroRequestInfo.getPwmApplication().getConfig() ) + "]";
            }
            else
            {
                replaceStr = "[" + e.getErrorInformation().toUserStr( PwmConstants.DEFAULT_LOCALE, null ) + "]";
            }
        }
        catch ( final Exception e )
        {
            LOGGER.error( sessionLabel, () -> "error while replacing macro '" + matchedStr + "', error: " + e.getMessage() );
        }

        if ( replaceStr == null )
        {
            return input;
        }

        if ( stringReplacer != null )
        {
            try
            {
                replaceStr = stringReplacer.replace( matchedStr, replaceStr );
            }
            catch ( final Exception e )
            {
                LOGGER.error( sessionLabel, () -> "unexpected error while executing '" + matchedStr + "' during StringReplacer.replace(), error: " + e.getMessage() );
            }
        }

        if ( replaceStr != null && replaceStr.length() > 0 )
        {
            final boolean sensitive = JavaHelper.enumArrayContainsValue( macroImplementation.flags(), MacroImplementation.MacroDefinitionFlag.SensitiveValue );
            final boolean debugOnlyLogging = JavaHelper.enumArrayContainsValue( macroImplementation.flags(), MacroImplementation.MacroDefinitionFlag.OnlyDebugLogging );
            if ( !debugOnlyLogging || ( pwmApplication != null && pwmApplication.getConfig().isDevDebugMode() ) )
            {
                final String finalReplaceStr = replaceStr;
                LOGGER.trace( sessionLabel, () -> "replaced macro " + matchedStr + " with value: "
                        + ( sensitive ? PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT : finalReplaceStr )
                        + " (" + TimeDuration.compactFromCurrent( startTime ) + ")" );
            }
        }
        return new StringBuilder( input ).replace( startPos, endPos, replaceStr ).toString();
    }

    public static MacroMachine forStatic( )
    {
        return new MacroMachine( null, null, null, null, null );
    }

    public interface StringReplacer
    {
        String replace( String matchedMacro, String newValue );
    }

    public static MacroMachine forUser(
            final CommonValues commonValues,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        return forUser( commonValues.getPwmApplication(), commonValues.getLocale(), commonValues.getSessionLabel(), userIdentity );
    }

    public static MacroMachine forUser(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        return forUser( pwmRequest.getPwmApplication(), pwmRequest.getLocale(), pwmRequest.getLabel(), userIdentity );
    }

    public static MacroMachine forUser(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity,
            final StringReplacer stringReplacer
    )
            throws PwmUnrecoverableException
    {
        return forUser( pwmRequest.getPwmApplication(), pwmRequest.getLocale(), pwmRequest.getLabel(), userIdentity, stringReplacer );
    }

    public static MacroMachine forUser(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserInfo userInfo,
            final LoginInfoBean loginInfoBean
    )
    {
        return new MacroMachine( pwmApplication, sessionLabel, userInfo, loginInfoBean, null );
    }

    public static MacroMachine forUser(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserInfo userInfo,
            final LoginInfoBean loginInfoBean,
            final StringReplacer stringReplacer
    )
    {
        return new MacroMachine( pwmApplication, sessionLabel, userInfo, loginInfoBean, stringReplacer );
    }

    public static MacroMachine forUser(
            final PwmApplication pwmApplication,
            final Locale userLocale,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final UserInfo userInfoBean = UserInfoFactory.newUserInfoUsingProxy( pwmApplication, sessionLabel, userIdentity, userLocale );
        return new MacroMachine( pwmApplication, sessionLabel, userInfoBean, null, null );
    }

    public static MacroMachine forUser(
            final PwmApplication pwmApplication,
            final Locale userLocale,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final StringReplacer stringReplacer
    )
            throws PwmUnrecoverableException
    {
        final UserInfo userInfoBean = UserInfoFactory.newUserInfoUsingProxy( pwmApplication, sessionLabel, userIdentity, userLocale );
        return new MacroMachine( pwmApplication, sessionLabel, userInfoBean, null, stringReplacer );
    }

    public static MacroMachine forNonUserSpecific(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel
    )
            throws PwmUnrecoverableException
    {
        return new MacroMachine( pwmApplication, sessionLabel, null, null, null );
    }
}
