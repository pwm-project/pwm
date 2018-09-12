/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;

public abstract class StandardMacros
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( StandardMacros.class );

    public static final Map<Class<? extends MacroImplementation>, MacroImplementation.Scope> STANDARD_MACROS;

    static
    {
        final Map<Class<? extends MacroImplementation>, MacroImplementation.Scope> defaultMacros = new LinkedHashMap<>();

        // system macros
        defaultMacros.put( CurrentTimeMacro.class, MacroImplementation.Scope.System );
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
                catch ( NumberFormatException e )
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
                catch ( PwmUnrecoverableException e )
                {
                    LOGGER.trace( "could not replace value for '" + matchValue + "', ldap error: " + e.getMessage() );
                    return "";
                }

                if ( ldapValue == null || ldapValue.length() < 1 )
                {
                    LOGGER.trace( "could not replace value for '" + matchValue + "', user does not have value for '" + ldapAttr + "'" );
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
                LOGGER.error( "could not replace value for '" + matchValue + "', pwmApplication is null" );
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

            final DateFormat dateFormat;
            if ( parameters.size() > 0 && !parameters.get( 0 ).isEmpty() )
            {
                try
                {
                    dateFormat = new SimpleDateFormat( parameters.get( 0 ) );
                }
                catch ( IllegalArgumentException e )
                {
                    throw new MacroParseException( e.getMessage() );
                }
            }
            else
            {
                dateFormat = new SimpleDateFormat( PwmConstants.DEFAULT_DATETIME_FORMAT_STR );
            }

            final TimeZone tz;
            if ( parameters.size() > 1 && !parameters.get( 1 ).isEmpty() )
            {
                final String desiredTz = parameters.get( 1 );
                final List<String> avalibleIDs = Arrays.asList( TimeZone.getAvailableIDs() );
                if ( !avalibleIDs.contains( desiredTz ) )
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

            dateFormat.setTimeZone( tz );
            return dateFormat.format( new Date() );
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
            catch ( PwmUnrecoverableException e )
            {
                LOGGER.error( "error reading pwdExpirationTime during macro replacement: " + e.getMessage() );
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
                    final DateFormat dateFormat = new SimpleDateFormat( datePattern );
                    return dateFormat.format( pwdExpirationTime );
                }
                catch ( IllegalArgumentException e )
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
            catch ( PwmUnrecoverableException e )
            {
                LOGGER.error( "error reading pwdExpirationTime during macro replacement: " + e.getMessage() );
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
                LOGGER.error( "could not replace value for '" + matchValue + "', userInfoBean is null" );
                return "";
            }

            try
            {
                final Instant pwdExpirationTime = userInfo.getPasswordExpirationTime();
                final TimeDuration timeUntilExpiration = TimeDuration.fromCurrent( pwdExpirationTime );
                final long daysUntilExpiration = timeUntilExpiration.getDays();
                return String.valueOf( daysUntilExpiration );
            }
            catch ( PwmUnrecoverableException e )
            {
                LOGGER.error( "error reading pwdExpirationTime during macro replacement: " + e.getMessage() );
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
            catch ( PwmUnrecoverableException e )
            {
                LOGGER.error( "error reading username during macro replacement: " + e.getMessage() );
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
            catch ( PwmUnrecoverableException e )
            {
                LOGGER.error( "error reading user email address during macro replacement: " + e.getMessage() );
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
            catch ( PwmUnrecoverableException e )
            {
                LOGGER.error( "error decrypting in memory password during macro replacement: " + e.getMessage() );
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
            catch ( MalformedURLException e )
            {
                LOGGER.error( "unable to parse configured/detected site URL: " + e.getMessage() );
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
                catch ( NumberFormatException e )
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
            catch ( NumberFormatException e )
            {
                throw new MacroParseException( "error parsing minimum value parameter of RandomNumber: " + e.getMessage() );
            }

            try
            {
                max = Integer.parseInt( parameters.get( 1 ) );
            }
            catch ( NumberFormatException e )
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
            catch ( PwmUnrecoverableException e )
            {
                LOGGER.error( "error reading otp setup time during macro replacement: " + e.getMessage() );
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
            catch ( PwmUnrecoverableException e )
            {
                LOGGER.error( "error reading response setup time macro replacement: " + e.getMessage() );
            }
            return "";
        }
    }
}
