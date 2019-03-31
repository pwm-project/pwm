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
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.util.logging.PwmLogger;

public class SSOHeaderFilterAuthenticationProvider implements PwmHttpFilterAuthenticationProvider
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SSOHeaderFilterAuthenticationProvider.class );

    @Override
    public void attemptAuthentication(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        {
            final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
            final PwmSession pwmSession = pwmRequest.getPwmSession();

            final String headerName = pwmApplication.getConfig().readSettingAsString( PwmSetting.SSO_AUTH_HEADER_NAME );
            if ( headerName == null || headerName.length() < 1 )
            {
                return;
            }


            final String headerValue = pwmRequest.readHeaderValueAsString( headerName );
            if ( headerValue == null || headerValue.length() < 1 )
            {
                return;
            }

            LOGGER.debug( pwmRequest, () -> "SSO Authentication header present in request, will search for user value of '" + headerValue + "'" );
            final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(
                    pwmApplication,
                    pwmSession,
                    PwmAuthenticationSource.SSO_HEADER
            );

            try
            {
                sessionAuthenticator.authUserWithUnknownPassword( headerValue, AuthenticationType.AUTH_WITHOUT_PASSWORD );
            }
            catch ( PwmOperationalException e )
            {
                throw new PwmUnrecoverableException( e.getErrorInformation() );
            }
        }
    }

    @Override
    public boolean hasRedirectedResponse( )
    {
        return false;
    }
}
