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

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserInfo;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmDateFormat;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;

public abstract class StandardMacros
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( StandardMacros.class );

    static final Map<Class<? extends MacroImplementation>, MacroImplementation.Scope> STANDARD_MACROS;

    static
    {
        final Map<Class<? extends MacroImplementation>, MacroImplementation.Scope> defaultMacros = new LinkedHashMap<>();

        // system macros
        defaultMacros.put( CurrentTimeMacro.class, MacroImplementation.Scope.System );
        defaultMacros.put( Iso8601DateTimeMacro.class, MacroImplementation.Scope.System );
        defaultMacros.put( InstanceIDMacro.class, MacroImplementation.Scope.System );
        defaultMacros.put( DefaultEmailFromAddressMacro.class, MacroImplementation.Scope.System );
        defaultMacros.put( SiteURLMacro.class, MacroImplementation.Scope.System );
        defaultMacros.put( SiteHostMacro.class, MacroImplementation.Scope.System );
        defaultMacros.put( RandomCharMacro.class, MacroImplementation.Scope.System );
        defaultMacros.put( RandomNumberMacro.class, MacroImplementation.Scope.System );
        defaultMacros.put( UUIDMacro.class, MacroImplementation.Scope.System );

        // user or system macros
        defaultMacros.put( UserIDMacro.class, MacroImplementation.Scope.System );

        // user macros
        defaultMacros.put( LdapMacro.class, MacroImplementation.Scope.User );
        defaultMacros.put( UserPwExpirationTimeMacro.class, MacroImplementation.Scope.User );
        defaultMacros.put( UserPwExpirationTimeDefaultMacro.class, MacroImplementation.Scope.User );
        defaultMacros.put( UserDaysUntilPwExpireMacro.class, MacroImplementation.Scope.User );
        defaultMacros.put( UserEmailMacro.class, MacroImplementation.Scope.User );
        defaultMacros.put( UserPasswordMacro.class, MacroImplementation.Scope.User );
        defaultMacros.put( UserLdapProfileMacro.class, MacroImplementation.Scope.User );
        defaultMacros.put( OtpSetupTimeMacro.class, MacroImplementation.Scope.User );
        defaultMacros.put( ResponseSetupTimeMacro.class, MacroImplementation.Scope.User );

        STANDARD_MACROS = Collections.unmodifiableMap( defaultMacros );
    }


    public static class LdapMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@LDAP" + PATTERN_OPTIONAL_PARAMETER_MATCH + "@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        ) throws MacroParseException
        {
            final UserInfo userInfo = macroRequestInfo.getUserInfo();

            if ( userInfo == null )
            {
                return "";
            }

            final List<String> parameters = splitMacroParameters( matchValue, "LDAP" );

            final String ldapAttr;
            if ( parameters.size() > 0 && !parameters.get( 0 ).isEmpty() )
            {
                ldapAttr = parameters.get( 0 );
            }
            else
            {
                throw new MacroParseException( "required attribute name parameter is missing" );
            }

            final int length;
            if ( parameters.size() > 1 && !parameters.get( 1 ).isEmpty() )
            {
                try
                {
                    length = Integer.parseInt( parameters.get( 1 ) );
                }
                catch ( final NumberFormatException e )
                {
                    throw new MacroParseException( "error parsing length parameter: " + e.getMessage() );
                }

                final int maxLengthPermitted = Integer.parseInt( macroRequestInfo.getPwmApplication().getConfig().readAppProperty( AppProperty.MACRO_LDAP_ATTR_CHAR_MAX_LENGTH ) );
                if ( length > maxLengthPermitted )
                {
                    throw new MacroParseException( "maximum permitted length of LDAP attribute (" + maxLengthPermitted + ") exceeded" );
                }
                else if ( length <= 0 )
                {
                    throw new MacroParseException( "length parameter must be greater than zero" );
                }
            }
            else
            {
                length = 0;
            }

            final String paddingChar;
            if ( parameters.size() > 2 && !parameters.get( 2 ).isEmpty() )
            {
                paddingChar = parameters.get( 2 );
            }
            else
            {
                paddingChar = "";
            }

            if ( parameters.size() > 3 )
            {
                throw new MacroParseException( "too many parameters" );
            }

            final String ldapValue;
            if ( "dn".equalsIgnoreCase( ldapAttr ) )
            {
                ldapValue = userInfo.getUserIdentity().getUserDN();
            }
            else
            {
                try
                {
                    ldapValue = userInfo.readStringAttribute( ldapAttr );
                }
                catch ( final PwmUnrecoverableException e )
                {
                    LOGGER.trace( () -> "could not replace value for '" + matchValue + "', ldap error: " + e.getMessage() );
                    return "";
                }

                if ( ldapValue == null || ldapValue.length() < 1 )
                {
                    LOGGER.trace( () -> "could not replace value for '" + matchValue + "', user does not have value for '" + ldapAttr + "'" );
                    return "";
                }
            }

            final StringBuilder returnValue = new StringBuilder();
            returnValue.append( ldapValue == null
                    ? ""
                    : ldapValue );

            if ( length > 0 && length < returnValue.length() )
            {
                returnValue.delete( length, returnValue.length() );
            }

            if ( length > 0 && paddingChar.length() > 0 )
            {
                while ( returnValue.length() < length )
                {
                    returnValue.append( paddingChar );
                }
            }

            return returnValue.toString();
        }
    }

    public static class InstanceIDMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@InstanceID@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        )
        {
            final PwmApplication pwmApplication = macroRequestInfo.getPwmApplication();

            if ( pwmApplication == null )
            {
                LOGGER.error( () -> "could not replace value for '" + matchValue + "', pwmApplication is null" );
                return "";
            }

            return pwmApplication.getInstanceID();
        }
    }

    public static class CurrentTimeMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@CurrentTime" + PATTERN_OPTIONAL_PARAMETER_MATCH + "@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo

        ) throws MacroParseException
        {
            final List<String> parameters = splitMacroParameters( matchValue, "CurrentTime" );

            final String dateFormatStr;
            if ( parameters.size() > 0 && !parameters.get( 0 ).isEmpty() )
            {
                dateFormatStr = parameters.get( 0 );
            }
            else
            {
                dateFormatStr = PwmConstants.DEFAULT_DATETIME_FORMAT_STR;
            }

            final TimeZone tz;
            if ( parameters.size() > 1 && !parameters.get( 1 ).isEmpty() )
            {
                final String desiredTz = parameters.get( 1 );
                final List<String> availableIDs = Arrays.asList( TimeZone.getAvailableIDs() );
                if ( !availableIDs.contains( desiredTz ) )
                {
                    throw new MacroParseException( "unknown timezone" );
                }
                tz = TimeZone.getTimeZone( desiredTz );
            }
            else
            {
                tz = PwmConstants.DEFAULT_TIMEZONE;
            }

            if ( parameters.size() > 2 )
            {
                throw new MacroParseException( "too many parameters" );
            }

            try
            {
                final PwmDateFormat pwmDateFormat = PwmDateFormat.newPwmDateFormat( dateFormatStr, PwmConstants.DEFAULT_LOCALE, tz );
                return pwmDateFormat.format( Instant.now() );
            }
            catch ( final IllegalArgumentException e )
            {
                throw new MacroParseException( e.getMessage() );
            }
        }
    }

    public static class Iso8601DateTimeMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@Iso8601" + PATTERN_OPTIONAL_PARAMETER_MATCH + "@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo

        ) throws MacroParseException
        {
            final List<String> parameters = splitMacroParameters( matchValue, "Iso8601" );

            if ( JavaHelper.isEmpty(  parameters ) || parameters.size() != 1 )
            {
                throw new MacroParseException( "exactly one parameter is required" );
            }

            final String param = parameters.get( 0 );

            if ( "DateTime".equalsIgnoreCase( param ) )
            {
                return JavaHelper.toIsoDate( Instant.now() );
            }
            else if ( "Date".equalsIgnoreCase( param ) )
            {
                return Instant.now().atOffset( ZoneOffset.UTC ).format( DateTimeFormatter.ofPattern( "uuuu-MM-dd" ) );
            }
            else if ( "Time".equalsIgnoreCase( param ) )
            {
                return Instant.now().atOffset( ZoneOffset.UTC ).format( DateTimeFormatter.ofPattern( "HH:mm:ss" ) );
            }

            throw new MacroParseException( "unknown parameter" );
        }
    }

    public static class UserPwExpirationTimeMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:PwExpireTime" + PATTERN_OPTIONAL_PARAMETER_MATCH + "@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo

        ) throws MacroParseException
        {
            final UserInfo userInfo = macroRequestInfo.getUserInfo();

            if ( userInfo == null )
            {
                return "";
            }

            final Instant pwdExpirationTime;
            try
            {
                pwdExpirationTime = userInfo.getPasswordExpirationTime();
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( () -> "error reading pwdExpirationTime during macro replacement: " + e.getMessage() );
                return "";
            }

            if ( pwdExpirationTime == null )
            {
                return "";
            }

            final String datePattern = matchValue.substring( 19, matchValue.length() - 1 );
            if ( datePattern.length() > 0 )
            {
                try
                {
                    final PwmDateFormat dateFormat = PwmDateFormat.newPwmDateFormat( datePattern );
                    return dateFormat.format( pwdExpirationTime );
                }
                catch ( final IllegalArgumentException e )
                {
                    throw new MacroParseException( e.getMessage() );
                }
            }

            return JavaHelper.toIsoDate( pwdExpirationTime );
        }
    }

    public static class UserPwExpirationTimeDefaultMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:PwExpireTime@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        )
        {
            final UserInfo userInfo = macroRequestInfo.getUserInfo();

            if ( userInfo == null )
            {
                return "";
            }

            try
            {
                final Instant pwdExpirationTime = userInfo.getPasswordExpirationTime();
                if ( pwdExpirationTime == null )
                {
                    return "";
                }

                return JavaHelper.toIsoDate( pwdExpirationTime );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( () -> "error reading pwdExpirationTime during macro replacement: " + e.getMessage() );
                return "";
            }
        }
    }

    public static class UserDaysUntilPwExpireMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:DaysUntilPwExpire@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        )
        {
            final UserInfo userInfo = macroRequestInfo.getUserInfo();

            if ( userInfo == null )
            {
                LOGGER.error( () -> "could not replace value for '" + matchValue + "', userInfoBean is null" );
                return "";
            }

            try
            {
                final Instant pwdExpirationTime = userInfo.getPasswordExpirationTime();
                final TimeDuration timeUntilExpiration = TimeDuration.fromCurrent( pwdExpirationTime );
                final long daysUntilExpiration = timeUntilExpiration.as( TimeDuration.Unit.DAYS );
                return String.valueOf( daysUntilExpiration );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( () -> "error reading pwdExpirationTime during macro replacement: " + e.getMessage() );
                return "";
            }
        }
    }

    public static class UserIDMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:ID@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        )
        {
            final UserInfo userInfo = macroRequestInfo.getUserInfo();

            try
            {
                if ( userInfo == null || userInfo.getUsername() == null )
                {
                    return "";
                }

                return userInfo.getUsername();
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( () -> "error reading username during macro replacement: " + e.getMessage() );
                return "";
            }
        }
    }

    public static class UserLdapProfileMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:LdapProfile@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        )
        {
            final UserInfo userInfo = macroRequestInfo.getUserInfo();

            if ( userInfo != null )
            {
                final UserIdentity userIdentity = userInfo.getUserIdentity();
                if ( userIdentity != null )
                {
                    return userIdentity.getLdapProfileID();
                }
            }

            return "";
        }
    }

    public static class UserEmailMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:Email@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        )
        {
            final UserInfo userInfo = macroRequestInfo.getUserInfo();

            try
            {
                if ( userInfo == null || userInfo.getUserEmailAddress() == null )
                {
                    return "";
                }

                return userInfo.getUserEmailAddress();
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( () -> "error reading user email address during macro replacement: " + e.getMessage() );
                return "";
            }
        }
    }

    public static class UserPasswordMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:Password@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        )
        {
            final LoginInfoBean loginInfoBean = macroRequestInfo.getLoginInfoBean();

            try
            {
                if ( loginInfoBean == null || loginInfoBean.getUserCurrentPassword() == null )
                {
                    return "";
                }

                return loginInfoBean.getUserCurrentPassword().getStringValue();
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( () -> "error decrypting in memory password during macro replacement: " + e.getMessage() );
                return "";
            }
        }

        public MacroDefinitionFlag[] flags( )
        {
            return new MacroDefinitionFlag[]
                    {
                            MacroDefinitionFlag.SensitiveValue,
                    };
        }
    }

    public static class SiteURLMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@SiteURL@|@Site:URL@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        )
        {
            return macroRequestInfo.getPwmApplication().getConfig().readSettingAsString( PwmSetting.PWM_SITE_URL );
        }
    }

    public static class DefaultEmailFromAddressMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@DefaultEmailFromAddress@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        )
        {
            return macroRequestInfo.getPwmApplication().getConfig().readSettingAsString( PwmSetting.EMAIL_DEFAULT_FROM_ADDRESS );
        }
    }

    public static class SiteHostMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@SiteHost@|@Site:Host@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        )
        {
            try
            {
                final String siteUrl = macroRequestInfo.getPwmApplication().getConfig().readSettingAsString( PwmSetting.PWM_SITE_URL );
                final URL url = new URL( siteUrl );
                return url.getHost();
            }
            catch ( final MalformedURLException e )
            {
                LOGGER.error( () -> "unable to parse configured/detected site URL: " + e.getMessage() );
            }
            return "";
        }
    }

    public static class RandomCharMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@RandomChar(:[^@]*)?@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        )
                throws MacroParseException
        {
            if ( matchValue == null || matchValue.length() < 1 )
            {
                return "";
            }

            final List<String> parameters = splitMacroParameters( matchValue, "RandomChar" );
            int length = 1;
            if ( parameters.size() > 0 && !parameters.get( 0 ).isEmpty() )
            {
                final int maxLengthPermitted = Integer.parseInt( macroRequestInfo.getPwmApplication().getConfig().readAppProperty( AppProperty.MACRO_RANDOM_CHAR_MAX_LENGTH ) );
                try
                {
                    length = Integer.parseInt( parameters.get( 0 ) );
                    if ( length > maxLengthPermitted )
                    {
                        throw new MacroParseException( "maximum permitted length of RandomChar (" + maxLengthPermitted + ") exceeded" );
                    }
                    else if ( length <= 0 )
                    {
                        throw new MacroParseException( "length of RandomChar (" + maxLengthPermitted + ") must be greater than zero" );
                    }
                }
                catch ( final NumberFormatException e )
                {
                    throw new MacroParseException( "error parsing length parameter of RandomChar: " + e.getMessage() );
                }
            }

            if ( parameters.size() > 1 && !parameters.get( 1 ).isEmpty() )
            {
                final String chars = parameters.get( 1 );
                return PwmRandom.getInstance().alphaNumericString( chars, length );
            }
            else
            {
                return PwmRandom.getInstance().alphaNumericString( length );
            }
        }
    }

    public static class RandomNumberMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@RandomNumber(:[^@]*)?@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        )
                throws MacroParseException
        {
            if ( matchValue == null || matchValue.length() < 1 )
            {
                return "";
            }

            final List<String> parameters = splitMacroParameters( matchValue, "RandomNumber" );
            if ( parameters.size() != 2 )
            {
                throw new MacroParseException( "incorrect number of parameter of RandomNumber: "
                        + parameters.size() + ", should be 2" );
            }

            final int min;
            final int max;
            try
            {
                min = Integer.parseInt( parameters.get( 0 ) );
            }
            catch ( final NumberFormatException e )
            {
                throw new MacroParseException( "error parsing minimum value parameter of RandomNumber: " + e.getMessage() );
            }

            try
            {
                max = Integer.parseInt( parameters.get( 1 ) );
            }
            catch ( final NumberFormatException e )
            {
                throw new MacroParseException( "error parsing maximum value parameter of RandomNumber: " + e.getMessage() );
            }

            if ( min > max )
            {
                throw new MacroParseException( "minimum value is less than maximum value parameter of RandomNumber" );
            }

            final int range = max - min;
            return String.valueOf( PwmRandom.getInstance().nextInt( range ) + min );
        }
    }

    public static class UUIDMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@UUID@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        )
        {
            return PwmRandom.getInstance().randomUUID().toString();
        }
    }

    public static class OtpSetupTimeMacro extends InternalMacros.InternalAbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@OtpSetupTime@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue( final String matchValue, final MacroRequestInfo macroRequestInfo )
        {
            try
            {
                final UserInfo userInfo = macroRequestInfo.getUserInfo();
                if ( userInfo != null && userInfo.getOtpUserRecord() != null && userInfo.getOtpUserRecord().getTimestamp() != null )
                {
                    return JavaHelper.toIsoDate( userInfo.getOtpUserRecord().getTimestamp() );
                }
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( () -> "error reading otp setup time during macro replacement: " + e.getMessage() );
            }

            return "";
        }
    }

    public static class ResponseSetupTimeMacro extends InternalMacros.InternalAbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@ResponseSetupTime@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue( final String matchValue, final MacroRequestInfo macroRequestInfo )
        {
            try
            {
                final UserInfo userInfo = macroRequestInfo.getUserInfo();
                if ( userInfo != null && userInfo.getResponseInfoBean() != null && userInfo.getResponseInfoBean().getTimestamp() != null )
                {
                    return JavaHelper.toIsoDate( userInfo.getResponseInfoBean().getTimestamp() );
                }
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( () -> "error reading response setup time macro replacement: " + e.getMessage() );
            }
            return "";
        }
    }
}
