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

package password.pwm.http.auth;

import password.pwm.PwmApplication;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
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
                    pwmRequest,
                    PwmAuthenticationSource.SSO_HEADER
            );

            try
            {
                sessionAuthenticator.authUserWithUnknownPassword( headerValue, AuthenticationType.AUTH_WITHOUT_PASSWORD );
            }
            catch ( final PwmOperationalException e )
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
