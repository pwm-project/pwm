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
import java.util.Date;

class OAuthState implements Serializable {

    private static int oauthStateIdCounter = 0;

    private final int stateID = oauthStateIdCounter++;
    private final Date issueTime = new Date();
    private String sessionID;
    private String nextUrl;

    public OAuthState(String sessionID, String nextUrl) {
        this.sessionID = sessionID;
        this.nextUrl = nextUrl;
    }

    public static int getOauthStateIdCounter() {
        return oauthStateIdCounter;
    }

    public int getStateID() {
        return stateID;
    }

    public Date getIssueTime() {
        return issueTime;
    }

    public String getSessionID() {
        return sessionID;
    }

    public String getNextUrl() {
        return nextUrl;
    }
}
