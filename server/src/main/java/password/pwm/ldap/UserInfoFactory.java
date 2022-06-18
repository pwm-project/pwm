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

package password.pwm.ldap;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmApplication;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequestContext;
import password.pwm.user.UserInfo;
import password.pwm.util.PasswordData;

import java.util.Locale;

public class UserInfoFactory
{

    private UserInfoFactory( )
    {
    }

    public static UserInfo newUserInfoUsingProxy(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final Locale locale,
            final PasswordData currentPassword
    )
            throws PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmApplication.domains().get( userIdentity.getDomainID() );
        final String userLdapProfile = userIdentity.getLdapProfileID();
        final ChaiProvider provider = pwmDomain.getProxyChaiProvider( sessionLabel, userLdapProfile );
        return newUserInfo(
                pwmApplication,
                sessionLabel,
                locale,
                userIdentity,
                provider,
                currentPassword
        );
    }

    public static UserInfo newUserInfoUsingProxyForOfflineUser(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmApplication.domains().get( userIdentity.getDomainID() );
        final Locale ldapLocale = LdapOperationsHelper.readLdapStoredLanguage( sessionLabel, pwmDomain, userIdentity );
        final ChaiProvider provider = pwmDomain.getProxyChaiProvider( sessionLabel, userIdentity.getLdapProfileID() );
        return newUserInfo( pwmApplication, sessionLabel, ldapLocale, userIdentity, provider, null );
    }

    public static UserInfo newUserInfoUsingProxy(
            final PwmRequestContext pwmRequestContext,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final SessionLabel sessionLabel = pwmRequestContext.getSessionLabel();
        final ChaiProvider provider = pwmRequestContext.getPwmDomain().getProxyChaiProvider( sessionLabel, userIdentity.getLdapProfileID() );
        return newUserInfo( pwmRequestContext.getPwmApplication(), pwmRequestContext.getSessionLabel(), pwmRequestContext.getLocale(), userIdentity, provider, null );
    }

    public static UserInfo newUserInfoUsingProxy(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final Locale userLocale
    )
            throws PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmApplication.domains().get( userIdentity.getDomainID() );
        final ChaiProvider provider = pwmDomain.getProxyChaiProvider( sessionLabel, userIdentity.getLdapProfileID() );
        return newUserInfo( pwmApplication, sessionLabel, userLocale, userIdentity, provider, null );
    }

    public static UserInfo newUserInfo(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final Locale userLocale,
            final UserIdentity userIdentity,
            final ChaiProvider provider
    )
            throws PwmUnrecoverableException
    {
        try
        {
            return makeUserInfoImpl( pwmApplication, sessionLabel, userLocale, userIdentity, provider, null );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage() ) );
        }
    }

    public static UserInfo newUserInfo(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final Locale userLocale,
            final UserIdentity userIdentity,
            final ChaiProvider provider,
            final PasswordData currentPassword
    )
            throws PwmUnrecoverableException
    {
        try
        {
            return makeUserInfoImpl( pwmApplication, sessionLabel, userLocale, userIdentity, provider, currentPassword );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage() ) );
        }
    }

    private static UserInfo makeUserInfoImpl(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final Locale userLocale,
            final UserIdentity userIdentity,
            final ChaiProvider provider,
            final PasswordData currentPassword
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        return LdapUserInfoReader.create( userIdentity, currentPassword, sessionLabel, userLocale, pwmApplication, provider );
    }


}
