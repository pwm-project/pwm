/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

package password.pwm.http.servlet.oauth;

import java.io.Serializable;
import java.time.Instant;

/**
 * This Json object gets sent as a redirect from the oauth consumer servlet to the ForgttenPasswordServlet.
 */
public class OAuthForgottenPasswordResults implements Serializable {
    private final boolean authenticated;
    private final String username;
    private final Instant timestamp;

    public OAuthForgottenPasswordResults(final boolean authenticated, final String username) {
        this.authenticated = authenticated;
        this.username = username;
        this.timestamp = Instant.now();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getUsername() {
        return username;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
