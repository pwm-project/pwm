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

package password.pwm.http.auth;

import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.logging.PwmLogger;

public class BasicFilterAuthenticationProvider implements PwmHttpFilterAuthenticationProvider
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( BasicFilterAuthenticationProvider.class );

    @Override
    public void attemptAuthentication(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        if ( !pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.BASIC_AUTH_ENABLED ) )
        {
            return;
        }

        if ( pwmRequest.isAuthenticated() )
        {
            return;
        }

        final BasicAuthInfo basicAuthInfo = BasicAuthInfo.parseAuthHeader( pwmRequest.getPwmApplication(), pwmRequest );
        if ( basicAuthInfo == null )
        {
            return;
        }

        try
        {
            final PwmSession pwmSession = pwmRequest.getPwmSession();
            final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

            //user isn't already authenticated and has an auth header, so try to auth them.
            LOGGER.debug( pwmSession, () -> "attempting to authenticate user using basic auth header (username=" + basicAuthInfo.getUsername() + ")" );
            final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(
                    pwmApplication,
                    pwmSession,
                    PwmAuthenticationSource.BASIC_AUTH
            );
            final UserSearchEngine userSearchEngine = pwmApplication.getUserSearchEngine();
            final UserIdentity userIdentity = userSearchEngine.resolveUsername( basicAuthInfo.getUsername(), null, null, pwmSession.getLabel() );
            sessionAuthenticator.authenticateUser( userIdentity, basicAuthInfo.getPassword() );
            pwmSession.getLoginInfoBean().setBasicAuth( basicAuthInfo );

        }
        catch ( PwmException e )
        {
            if ( e.getError() == PwmError.ERROR_DIRECTORY_UNAVAILABLE )
            {
                StatisticsManager.incrementStat( pwmRequest, Statistic.LDAP_UNAVAILABLE_COUNT );
            }
            throw new PwmUnrecoverableException( e.getError() );
        }
    }

    @Override
    public boolean hasRedirectedResponse( )
    {
        return false;
    }
}
