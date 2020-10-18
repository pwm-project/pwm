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

import password.pwm.AppProperty;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserInfo;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UserMacros
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( UserMacros.class );

    static final List<Macro> USER_MACROS = Collections.unmodifiableList( Stream.of(
            new UserIDMacro(),
            new UserLdapMacro(),
            new UserPwExpirationTimeMacro(),
            new UserDaysUntilPwExpireMacro(),
            new UserEmailMacro(),
            new UserPasswordMacro(),
            new UserLdapProfileMacro(),
            new OtpSetupTimeMacro(),
            new ResponseSetupTimeMacro()
    ).collect( Collectors.toList() ) );

    private abstract static class AbstractUserMacro extends AbstractMacro
    {
        @Override
        public Scope getScope()
        {
            return Macro.Scope.User;
        }
    }

    public static class UserLdapMacro extends AbstractUserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@(User:LDAP|LDAP)" + PATTERN_OPTIONAL_PARAMETER_MATCH + "@" );

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
            final UserInfo userInfo = request.getUserInfo();

            if ( userInfo == null )
            {
                return "";
            }

            final List<String> parameters = splitMacroParameters( matchValue, "User", "LDAP" );

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

                final int maxLengthPermitted = Integer.parseInt(
                        request.getPwmApplication() != null
                                ?  request.getPwmApplication().getConfig().readAppProperty( AppProperty.MACRO_LDAP_ATTR_CHAR_MAX_LENGTH )
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
                    LOGGER.trace( request.getSessionLabel(), () -> "could not replace value for '" + matchValue + "', ldap error: " + e.getMessage() );
                    return "";
                }

                if ( ldapValue == null || ldapValue.length() < 1 )
                {
                    LOGGER.trace( request.getSessionLabel(), () -> "could not replace value for '" + matchValue + "', user does not have value for '" + ldapAttr + "'" );
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


    public static class UserIDMacro extends AbstractUserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:ID@" );

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
            final UserInfo userInfo = request.getUserInfo();

            try
            {
                if ( userInfo == null || StringUtil.isEmpty( userInfo.getUsername() ) )
                {
                    return "";
                }

                return userInfo.getUsername();
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( request.getSessionLabel(), () -> "error reading username during macro replacement: " + e.getMessage() );
                return "";
            }
        }
    }

    public static class UserPwExpirationTimeMacro extends AbstractUserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:PwExpireTime" + PATTERN_OPTIONAL_PARAMETER_MATCH + "@" );

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
            final UserInfo userInfo = request.getUserInfo();

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
                LOGGER.error( request.getSessionLabel(), () -> "error reading pwdExpirationTime during macro replacement: " + e.getMessage() );
                return "";
            }

            return processTimeOutputMacro( pwdExpirationTime, matchValue, "User", "PwExpireTime" );
        }
    }

    public static class UserDaysUntilPwExpireMacro extends AbstractUserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:DaysUntilPwExpire@" );

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
            final UserInfo userInfo = request.getUserInfo();

            if ( userInfo == null )
            {
                LOGGER.error( request.getSessionLabel(), () -> "could not replace value for '" + matchValue + "', userInfoBean is null" );
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
                LOGGER.error( request.getSessionLabel(), () -> "error reading pwdExpirationTime during macro replacement: " + e.getMessage() );
                return "";
            }
        }
    }

    public static class UserEmailMacro extends AbstractUserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:Email@" );

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
            final UserInfo userInfo = request.getUserInfo();

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
                LOGGER.error( request.getSessionLabel(), () -> "error reading user email address during macro replacement: " + e.getMessage() );
                return "";
            }
        }
    }

    public static class UserPasswordMacro extends AbstractUserMacro
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
            final LoginInfoBean loginInfoBean = request.getLoginInfoBean();

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
                LOGGER.error( request.getSessionLabel(), () -> "error decrypting in memory password during macro replacement: " + e.getMessage() );
                return "";
            }
        }

        @Override
        public Set<MacroDefinitionFlag> flags( )
        {
            return FLAGS;
        }
    }

    public static class UserLdapProfileMacro extends AbstractUserMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@User:LdapProfile@" );

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
            final UserInfo userInfo = request.getUserInfo();

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

    public static class OtpSetupTimeMacro extends AbstractUserMacro
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
                final UserInfo userInfo = request.getUserInfo();
                if ( userInfo != null && userInfo.getOtpUserRecord() != null && userInfo.getOtpUserRecord().getTimestamp() != null )
                {
                    otpSetupTime = userInfo.getOtpUserRecord().getTimestamp();
                }
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( request.getSessionLabel(),  () -> "error reading otp setup time during macro replacement: " + e.getMessage() );
            }

            return processTimeOutputMacro( otpSetupTime, matchValue, "OtpSetupTime" );
        }
    }

    public static class ResponseSetupTimeMacro extends AbstractUserMacro
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
                final UserInfo userInfo = request.getUserInfo();
                if ( userInfo != null && userInfo.getResponseInfoBean() != null && userInfo.getResponseInfoBean().getTimestamp() != null )
                {
                    responseSetupTime = userInfo.getResponseInfoBean().getTimestamp();
                }
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( request.getSessionLabel(), () -> "error reading response setup time macro replacement: " + e.getMessage() );
            }

            return processTimeOutputMacro( responseSetupTime, matchValue, "ResponseSetupTime" );
        }
    }
}
