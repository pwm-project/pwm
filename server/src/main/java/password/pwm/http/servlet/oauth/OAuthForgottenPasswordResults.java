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

package password.pwm.http.servlet.oauth;

import java.io.Serializable;
import java.time.Instant;

/**
 * This Json object gets sent as a redirect from the oauth consumer servlet to the ForgttenPasswordServlet.
 */
public class OAuthForgottenPasswordResults implements Serializable
{
    private final boolean authenticated;
    private final String username;
    private final Instant timestamp;

    public OAuthForgottenPasswordResults( final boolean authenticated, final String username )
    {
        this.authenticated = authenticated;
        this.username = username;
        this.timestamp = Instant.now();
    }

    public boolean isAuthenticated( )
    {
        return authenticated;
    }

    public String getUsername( )
    {
        return username;
    }

    public Instant getTimestamp( )
    {
        return timestamp;
    }
}
