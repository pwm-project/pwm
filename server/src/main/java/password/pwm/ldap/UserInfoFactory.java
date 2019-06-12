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

package password.pwm.ldap;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.CommonValues;
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
        final String userLdapProfile = userIdentity.getLdapProfileID();
        final ChaiProvider provider = pwmApplication.getProxyChaiProvider( userLdapProfile );
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
        final Locale ldapLocale = LdapOperationsHelper.readLdapStoredLanguage( pwmApplication, userIdentity );
        final ChaiProvider provider = pwmApplication.getProxyChaiProvider( userIdentity.getLdapProfileID() );
        return newUserInfo( pwmApplication, sessionLabel, ldapLocale, userIdentity, provider, null );
    }

    public static UserInfo newUserInfoUsingProxy(
            final CommonValues commonValues,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final ChaiProvider provider = commonValues.getPwmApplication().getProxyChaiProvider( userIdentity.getLdapProfileID() );
        return newUserInfo( commonValues.getPwmApplication(), commonValues.getSessionLabel(), commonValues.getLocale(), userIdentity, provider, null );
    }

    public static UserInfo newUserInfoUsingProxy(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final Locale userLocale
    )
            throws PwmUnrecoverableException
    {
        final ChaiProvider provider = pwmApplication.getProxyChaiProvider( userIdentity.getLdapProfileID() );
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
        catch ( ChaiUnavailableException e )
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
        catch ( ChaiUnavailableException e )
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
        return UserInfoReader.create( userIdentity, currentPassword, sessionLabel, userLocale, pwmApplication, provider );
    }


}
