/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

import lombok.AllArgsConstructor;
import lombok.Data;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.config.PwmSetting;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MacroMachine
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( MacroMachine.class );

    private static final Map<Pattern, Macro> BUILTIN_MACROS = makeImplementations();

    private static final StatisticCounterBundle<DebugItem> STATISTIC_COUNTER_BUNDLE = new StatisticCounterBundle<>( DebugItem.class );

    enum DebugItem
    {
        Matches,
        Replacements,
        ExternalInvokes,
    }

    private static Map<Pattern, Macro> makeImplementations( )
    {
        final List<Macro> implementations;
        {
            final List<Macro> list = new ArrayList<>();
            list.addAll( SystemMacros.SYSTEM_MACROS );
            list.addAll( StaticMacros.STATIC_MACROS );
            list.addAll( UserMacros.USER_MACROS );
            implementations = Collections.unmodifiableList( list );
        }

        return implementations
                .stream()
                .sorted( Comparator.comparing( Macro::getSequence ) )
                .collect(
                        Collectors.toMap(
                                Macro::getRegExPattern,
                                macroImplementation -> macroImplementation,
                                ( k, v ) ->
                                {
                                    throw new IllegalStateException();
                                },
                                LinkedHashMap::new
                        )
                );

    }

    private static Map<Pattern, Macro> makeExternalImplementations( final PwmApplication pwmApplication )
    {
        final LinkedHashMap<Pattern, Macro> map = new LinkedHashMap<>();
        final List<String> externalMethods = ( pwmApplication == null )
                ? Collections.emptyList()
                : pwmApplication.getConfig().readSettingAsStringArray( PwmSetting.EXTERNAL_MACROS_REST_URLS );

        int iteration = 0;
        for ( final String url : externalMethods )
        {
            iteration++;
            final Macro macroImplementation = new ExternalRestMacro( iteration, url );
            final Pattern pattern = macroImplementation.getRegExPattern();
            map.put( pattern, macroImplementation );
        }
        return map;
    }


    public static String expandMacros(
            final MacroRequest macroRequest,
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

        final Set<Macro.Scope> scopes = effectiveScopesForRequest( macroRequest );

        final Map<Pattern, Macro> macroImplementations = new LinkedHashMap<>( BUILTIN_MACROS );

        //First the User macros
        if ( scopes.contains( Macro.Scope.User ) )
        {
            macroImplementations.putAll( makeExternalImplementations( macroRequest.getPwmApplication() ) );
        }

        final ReplaceWorkData workData = new ReplaceWorkData( input, input, macroRequest );

        macroImplementations.entrySet().stream()
                .filter( entry -> scopes.contains( entry.getValue().getScope() ) )
                .forEachOrdered( entry -> doRequest( workData,  entry.getKey(), entry.getValue() ) );

        return workData.getWorkingString();
    }

    @Data
    @AllArgsConstructor
    private static class ReplaceWorkData
    {
        private String originalString;
        private String workingString;
        private MacroRequest macroRequestInfo;
    }

    private static void doRequest(
            final ReplaceWorkData replaceWorkData,
            final Pattern pattern,
            final Macro pwmMacro
    )
    {
        boolean matched;
        int safetyCounter = 0;
        do
        {
            safetyCounter++;
            final Matcher matcher = pattern.matcher( replaceWorkData.getWorkingString() );
            matched = matcher.find();
            if ( matched )
            {
                STATISTIC_COUNTER_BUNDLE.increment( DebugItem.Matches );
                replaceWorkData.setWorkingString( doReplace( replaceWorkData.getWorkingString(), pwmMacro, matcher, replaceWorkData.getMacroRequestInfo() ) );
                if ( replaceWorkData.getWorkingString().equals( replaceWorkData.getOriginalString() ) )
                {
                    LOGGER.warn(
                            replaceWorkData.getMacroRequestInfo().getSessionLabel(),
                            () -> "macro replace was called but input string was not modified.  " + " macro="
                                    + pwmMacro.getClass().getName() + ", pattern=" + pwmMacro.getRegExPattern().toString() );
                    break;
                }
            }
        }
        while ( matched && safetyCounter < 1000 );
    }

    private static String doReplace(
            final String input,
            final Macro macroImplementation,
            final Matcher matcher,
            final MacroRequest macroRequestInfo
    )
    {
        final SessionLabel sessionLabel = macroRequestInfo.getSessionLabel();
        final PwmApplication pwmApplication = macroRequestInfo.getPwmApplication();
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

        final MacroReplacer macroReplacer = macroRequestInfo.getMacroReplacer();
        if ( macroReplacer != null )
        {
            try
            {
                replaceStr = macroReplacer.replace( matchedStr, replaceStr );
                STATISTIC_COUNTER_BUNDLE.increment( DebugItem.Replacements );
            }
            catch ( final Exception e )
            {
                LOGGER.error( sessionLabel, () -> "unexpected error while executing '" + matchedStr + "' during StringReplacer.replace(), error: " + e.getMessage() );
            }
        }

        if ( replaceStr != null && replaceStr.length() > 0 )
        {
            final boolean sensitive = macroImplementation.flags().contains( Macro.MacroDefinitionFlag.SensitiveValue );
            final boolean debugOnlyLogging = macroImplementation.flags().contains( Macro.MacroDefinitionFlag.OnlyDebugLogging );
            if ( !debugOnlyLogging || ( pwmApplication != null && pwmApplication.getConfig().isDevDebugMode() ) )
            {
                final String finalReplaceStr = replaceStr;
                LOGGER.trace( sessionLabel, () -> "replaced macro " + matchedStr + " with value: "
                                + ( sensitive ? PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT : finalReplaceStr ),
                        () -> TimeDuration.fromCurrent( startTime ) );
            }
        }
        return new StringBuilder( input ).replace( startPos, endPos, replaceStr ).toString();
    }

    private static Set<Macro.Scope> effectiveScopesForRequest( final MacroRequest macroRequestInfo )
    {
        final Set<Macro.Scope> scopes = EnumSet.noneOf( Macro.Scope.class );
        scopes.add( Macro.Scope.Static );

        final PwmApplication pwmApplication = macroRequestInfo.getPwmApplication();
        final PwmApplicationMode mode = pwmApplication != null ? pwmApplication.getApplicationMode() : PwmApplicationMode.ERROR;

        if (
                mode == PwmApplicationMode.RUNNING
                        || mode == PwmApplicationMode.CONFIGURATION
        )
        {
            scopes.add( Macro.Scope.System );
        }

        if ( macroRequestInfo.getUserInfo() != null )
        {
            scopes.add( Macro.Scope.User );
        }

        if ( macroRequestInfo.getTargetUserInfo() != null )
        {
            scopes.add( Macro.Scope.TargetUser );
        }

        return Collections.unmodifiableSet( scopes );
    }
}
