/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.user.UserInfo;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class UserMacros
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( UserMacros.class );

    static final List<UserMacro> USER_MACROS = JavaHelper.instancesOfSealedInterface( UserMacro.class );

    sealed interface UserMacro extends Macro
    {
    }

    abstract static class AbstractUserMacro extends AbstractMacro
    {
        @Override
        public Scope getScope()
        {
            return Macro.Scope.User;
        }
    }

    abstract static class AbstractUserLdapMacro extends AbstractUserMacro
    {
        @Override
        public String replaceValue(
                final String matchValue,
                final MacroRequest macroRequest
        )
                throws MacroParseException
        {
            final List<String> parameters = splitMacroParameters( matchValue, ignoreWords() );

            final String ldapAttr;
            if ( parameters.size() > 0 && !parameters.get( 0 ).isEmpty() )
            {
                ldapAttr = parameters.get( 0 );
            }
            else
            {
                throw new MacroParseException( "required attribute name parameter is missing" );
            }

            final int length = readLengthParam( parameters, macroRequest );

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

            final String ldapValue = readLdapValue( macroRequest, ldapAttr, matchValue );

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

        private String readLdapValue(
                final MacroRequest macroRequest,
                final String ldapAttr,
                final String matchValue

        )
        {
            final UserInfo userInfo;
            {
                final Optional<UserInfo> optionalUserInfo = loadUserInfo( macroRequest );
                if ( optionalUserInfo.isEmpty() )
                {
                    return "";
                }
                userInfo = optionalUserInfo.get();
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
                    LOGGER.trace( macroRequest.sessionLabel(), () -> "could not replace value for '" + matchValue + "', ldap error: " + e.getMessage() );
                    return "";
                }

                if ( ldapValue == null || ldapValue.length() < 1 )
                {
                    LOGGER.trace( macroRequest.sessionLabel(), () -> "could not replace value for '" + matchValue + "', user does not have value for '" + ldapAttr + "'" );
                    return "";
                }
            }
            return ldapValue;
        }

        private static int readLengthParam(
                final List<String> parameters,
                final MacroRequest macroRequest
        )
                throws MacroParseException
        {
            int length = 0;
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

                final int maxLengthPermitted = Integer.parseInt(
                        macroRequest.pwmApplication() != null
                                ?  macroRequest.pwmApplication().getConfig().readAppProperty( AppProperty.MACRO_LDAP_ATTR_CHAR_MAX_LENGTH )
                                :  AppProperty.MACRO_LDAP_ATTR_CHAR_MAX_LENGTH.getDefaultValue()
                );

                if ( length > maxLengthPermitted )
                {
                    throw new MacroParseException( "maximum permitted length of LDAP attribute (" + maxLengthPermitted + ") exceeded" );
                }
                else if ( length <= 0 )
                {
                    throw new MacroParseException( "length parameter must be greater than zero" );
                }
            }

            return length;
        }

        abstract Optional<UserInfo> loadUserInfo( MacroRequest macroRequest );

        abstract List<String> ignoreWords();
    }

    public static final class UserLdapMacro extends AbstractUserLdapMacro implements UserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@(User:LDAP|LDAP)" + PATTERN_OPTIONAL_PARAMETER_MATCH + "@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        Optional<UserInfo> loadUserInfo( final MacroRequest macroRequest )
        {
            return Optional.ofNullable( macroRequest.userInfo() );
        }

        @Override
        List<String> ignoreWords()
        {
            return Arrays.asList( "User", "LDAP" );
        }
    }

    public static final class TargetUserLdapMacro extends AbstractUserLdapMacro implements UserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@TargetUser:LDAP" + PATTERN_OPTIONAL_PARAMETER_MATCH + "@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public Scope getScope()
        {
            return Scope.TargetUser;
        }

        @Override
        Optional<UserInfo> loadUserInfo( final MacroRequest macroRequest )
        {
            return Optional.ofNullable( macroRequest.targetUserInfo() );
        }

        @Override
        List<String> ignoreWords()
        {
            return Arrays.asList( "TargetUser", "LDAP" );
        }
    }

    abstract static class AbstractUserIDMacro extends AbstractUserMacro
    {
        @Override
        public String replaceValue(
                final String matchValue,
                final MacroRequest macroRequest
        )
        {
            final Optional<UserInfo> optionalUserInfo = loadUserInfo( macroRequest );

            try
            {
                if ( optionalUserInfo.isPresent() )
                {
                    final String username = optionalUserInfo.get().getUsername();
                    if ( StringUtil.notEmpty( username ) )
                    {
                        return username;
                    }
                }

                return "";
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( macroRequest.sessionLabel(), () -> "error reading username during macro replacement: " + e.getMessage() );
                return "";
            }
        }

        abstract Optional<UserInfo> loadUserInfo( MacroRequest macroRequest );
    }

    abstract static class AbstractUserPwExpirationTimeMacro extends AbstractUserMacro
    {
        @Override
        public String replaceValue(
                final String matchValue,
                final MacroRequest macroRequest

        )
                throws MacroParseException
        {
            final Optional<UserInfo> userInfo = loadUserInfo( macroRequest );

            if ( !userInfo.isPresent() )
            {
                return "";
            }

            final Instant pwdExpirationTime;
            try
            {
                pwdExpirationTime = userInfo.get().getPasswordExpirationTime();
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( macroRequest.sessionLabel(), () -> "error reading pwdExpirationTime during macro replacement: " + e.getMessage() );
                return "";
            }

            return processTimeOutputMacro( pwdExpirationTime, matchValue, ignoreWords() );
        }

        abstract Optional<UserInfo> loadUserInfo( MacroRequest macroRequest );

        abstract List<String> ignoreWords();
    }

    public static final class UserIDMacro extends AbstractUserIDMacro implements UserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:ID@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }


        @Override
        Optional<UserInfo> loadUserInfo( final MacroRequest macroRequest )
        {
            return Optional.ofNullable( macroRequest.userInfo() );
        }
    }

    public static final class UserLdapProfileIDMacro extends AbstractUserMacro implements UserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:LdapProfile:ID@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public String replaceValue(
                final String matchValue,
                final MacroRequest macroRequest

        )
        {
            final UserInfo userInfo = macroRequest.userInfo();

            if ( userInfo != null )
            {
                final UserIdentity userIdentity = userInfo.getUserIdentity();
                if ( userIdentity != null )
                {
                    return userIdentity.getLdapProfileID().stringValue();
                }
            }

            return "";
        }
    }

    public static final class DomainIdMacro extends AbstractUserMacro implements UserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:Domain:ID@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public String replaceValue(
                final String matchValue,
                final MacroRequest macroRequest

        )
        {
            final UserInfo userInfo = macroRequest.userInfo();

            if ( userInfo != null )
            {
                final UserIdentity userIdentity = userInfo.getUserIdentity();
                if ( userIdentity != null )
                {
                    return userIdentity.getDomainID().stringValue();
                }
            }

            return "";
        }
    }

    public static final class UserLdapProfileNameMacro extends AbstractUserMacro implements UserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:LdapProfile:Name@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public String replaceValue(
                final String matchValue,
                final MacroRequest macroRequest

        )
        {
            final UserInfo userInfo = macroRequest.userInfo();

            if ( userInfo != null )
            {
                final UserIdentity userIdentity = userInfo.getUserIdentity();
                if ( userIdentity != null )
                {
                    final LdapProfile ldapProfile = userIdentity.getLdapProfile( macroRequest.pwmApplication().getConfig() );
                    if ( ldapProfile != null )
                    {
                        final Locale userLocale = macroRequest.userLocale() == null ? PwmConstants.DEFAULT_LOCALE : macroRequest.userLocale();
                        return ldapProfile.getDisplayName( userLocale );
                    }
                }
            }

            return "";
        }
    }

    public static final class TargetUserIDMacro extends AbstractUserIDMacro implements UserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@TargetUser:ID@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public Scope getScope()
        {
            return Scope.TargetUser;
        }

        @Override
        Optional<UserInfo> loadUserInfo( final MacroRequest macroRequest )
        {
            return Optional.ofNullable( macroRequest.targetUserInfo() );
        }
    }

    public static final class UserPwExpirationTimeMacro extends AbstractUserPwExpirationTimeMacro implements UserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:PwExpireTime" + PATTERN_OPTIONAL_PARAMETER_MATCH + "@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        Optional<UserInfo> loadUserInfo( final MacroRequest macroRequest )
        {
            return Optional.ofNullable( macroRequest.userInfo() );
        }

        @Override
        List<String> ignoreWords()
        {
            return Arrays.asList( "User", "PwExpireTime" );
        }
    }

    public static final class TargetUserPwExpirationTimeMacro extends AbstractUserPwExpirationTimeMacro implements UserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@TargetUser:PwExpireTime" + PATTERN_OPTIONAL_PARAMETER_MATCH + "@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public Scope getScope()
        {
            return Scope.TargetUser;
        }

        @Override
        Optional<UserInfo> loadUserInfo( final MacroRequest macroRequest )
        {
            return Optional.ofNullable( macroRequest.targetUserInfo() );
        }

        @Override
        List<String> ignoreWords()
        {
            return Arrays.asList( "TargetUser", "PwExpireTime" );
        }
    }

    public static final class UserDaysUntilPwExpireMacro extends AbstractUserDaysUntilPwExpireMacro implements UserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:DaysUntilPwExpire@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        Optional<UserInfo> loadUserInfo( final MacroRequest macroRequest )
        {
            return Optional.ofNullable( macroRequest.userInfo() );
        }
    }

    public static final class TargetUserDaysUntilPwExpireMacro extends AbstractUserDaysUntilPwExpireMacro implements UserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@TargetUser:DaysUntilPwExpire@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public Scope getScope()
        {
            return Scope.TargetUser;
        }

        @Override
        Optional<UserInfo> loadUserInfo( final MacroRequest macroRequest )
        {
            return Optional.ofNullable( macroRequest.targetUserInfo() );
        }
    }

    public abstract static class AbstractUserDaysUntilPwExpireMacro extends AbstractUserMacro
    {
        @Override
        public String replaceValue(
                final String matchValue,
                final MacroRequest macroRequest
        )
        {
            final Optional<UserInfo> userInfo = loadUserInfo( macroRequest );

            if ( !userInfo.isPresent() )
            {
                return "";
            }

            try
            {
                final Instant pwdExpirationTime = userInfo.get().getPasswordExpirationTime();
                final TimeDuration timeUntilExpiration = TimeDuration.fromCurrent( pwdExpirationTime );
                final long daysUntilExpiration = timeUntilExpiration.as( TimeDuration.Unit.DAYS );
                return String.valueOf( daysUntilExpiration );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( macroRequest.sessionLabel(), () -> "error reading pwdExpirationTime during macro replacement: " + e.getMessage() );
                return "";
            }
        }

        abstract Optional<UserInfo> loadUserInfo( MacroRequest macroRequest );
    }

    abstract static class AbstractUserEmailMacro extends AbstractUserMacro
    {

        @Override
        public String replaceValue(
                final String matchValue,
                final MacroRequest macroRequest
        )
        {
            final Optional<UserInfo> optionalUserInfo = loadUserInfo( macroRequest );

            try
            {
                if ( optionalUserInfo.isPresent() )
                {
                    final String emailAddress = optionalUserInfo.get().getUserEmailAddress();
                    if ( StringUtil.notEmpty( emailAddress ) )
                    {

                        return emailAddress;

                    }
                }

            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( macroRequest.sessionLabel(), () -> "error reading user email address during macro replacement: " + e.getMessage() );
                return "";
            }

            return "";
        }

        abstract Optional<UserInfo> loadUserInfo( MacroRequest macroRequest );
    }

    public static final class UserEmailMacro extends AbstractUserEmailMacro implements UserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:Email@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        Optional<UserInfo> loadUserInfo( final MacroRequest macroRequest )
        {
            return Optional.ofNullable( macroRequest.userInfo() );
        }
    }

    public static final class TargetUserEmailMacro extends AbstractUserEmailMacro implements UserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@TargetUser:Email@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public Scope getScope()
        {
            return Scope.TargetUser;
        }

        @Override
        Optional<UserInfo> loadUserInfo( final MacroRequest macroRequest )
        {
            return Optional.ofNullable( macroRequest.targetUserInfo() );
        }
    }

    public static final class UserPasswordMacro extends AbstractUserMacro implements UserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:Password@" );
        private static final Set<MacroDefinitionFlag> FLAGS = Collections.singleton( MacroDefinitionFlag.SensitiveValue );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public String replaceValue(
                final String matchValue,
                final MacroRequest request
        )
        {
            final LoginInfoBean loginInfoBean = request.loginInfoBean();

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
                LOGGER.error( request.sessionLabel(), () -> "error decrypting in memory password during macro replacement: " + e.getMessage() );
                return "";
            }
        }

        @Override
        public Set<MacroDefinitionFlag> flags( )
        {
            return FLAGS;
        }
    }

    public static final class DefaultDomainEmailFromAddressMacro extends AbstractUserMacro implements UserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@DefaultEmailFromAddress@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public String replaceValue(
                final String matchValue,
                final MacroRequest request
        )
                throws MacroParseException
        {
            final UserInfo userInfo = request.userInfo();
            if ( userInfo == null )
            {
                throw new MacroParseException( "[DefaultEmailFromAddress]: userInfo unspecified on macro request" );
            }

            final UserIdentity userIdentity = userInfo.getUserIdentity();
            if ( userIdentity == null )
            {
                throw new MacroParseException( "[DefaultEmailFromAddress]: userIdentity unspecified on macro request" );
            }

            final DomainID domainID = userIdentity.getDomainID();
            if ( domainID == null )
            {
                throw new MacroParseException( "[DefaultEmailFromAddress]: domain unspecified on macro request" );
            }

            final PwmDomain pwmDomain = request.pwmApplication().domains().get( domainID );
            if ( pwmDomain == null )
            {
                throw new MacroParseException( "[DefaultEmailFromAddress]: domain invalid on macro request" );
            }

            return pwmDomain.getConfig().readSettingAsString( PwmSetting.EMAIL_DOMAIN_FROM_ADDRESS );
        }
    }

    public static final class OtpSetupTimeMacro extends AbstractUserMacro implements UserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@OtpSetupTime" + PATTERN_OPTIONAL_PARAMETER_MATCH + "@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public String replaceValue( final String matchValue, final MacroRequest request )
                throws MacroParseException
        {
            Instant otpSetupTime = null;
            try
            {
                final UserInfo userInfo = request.userInfo();
                if ( userInfo != null && userInfo.getOtpUserRecord() != null && userInfo.getOtpUserRecord().getTimestamp() != null )
                {
                    otpSetupTime = userInfo.getOtpUserRecord().getTimestamp();
                }
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( request.sessionLabel(),  () -> "error reading otp setup time during macro replacement: " + e.getMessage() );
            }

            return processTimeOutputMacro( otpSetupTime, matchValue, Collections.singletonList( "OtpSetupTime" ) );
        }
    }

    public static final class ResponseSetupTimeMacro extends AbstractUserMacro implements UserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@ResponseSetupTime" + PATTERN_OPTIONAL_PARAMETER_MATCH + "@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public String replaceValue( final String matchValue, final MacroRequest request )
                throws MacroParseException
        {
            Instant responseSetupTime = null;
            try
            {
                final UserInfo userInfo = request.userInfo();
                if ( userInfo != null && userInfo.getResponseInfoBean() != null && userInfo.getResponseInfoBean().getTimestamp() != null )
                {
                    responseSetupTime = userInfo.getResponseInfoBean().getTimestamp();
                }
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( request.sessionLabel(), () -> "error reading response setup time macro replacement: " + e.getMessage() );
            }

            return processTimeOutputMacro( responseSetupTime, matchValue, Collections.singletonList( "ResponseSetupTime" ) );
        }
    }
}
