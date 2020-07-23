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

import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.servlet.oauth.OAuthMachine;
import password.pwm.http.servlet.oauth.OAuthSettings;

import java.io.IOException;

public class OAuthFilterAuthenticationProvider implements PwmHttpFilterAuthenticationProvider
{

    private boolean redirected = false;

    public void attemptAuthentication(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException
    {
        final OAuthSettings oauthSettings = OAuthSettings.forSSOAuthentication( pwmRequest.getConfig() );
        if ( !oauthSettings.oAuthIsConfigured() )
        {
            return;
        }

        final String originalURL = pwmRequest.getURLwithQueryString();
        final OAuthMachine oAuthMachine = new OAuthMachine( pwmRequest.getLabel(), oauthSettings );
        oAuthMachine.redirectUserToOAuthServer( pwmRequest, originalURL, null, null );
        redirected = true;
    }

    @Override
    public boolean hasRedirectedResponse( )
    {
        return redirected;
    }
}
