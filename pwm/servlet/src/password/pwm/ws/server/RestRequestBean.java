/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

package password.pwm.ws.server;

import password.pwm.PwmApplication;
import password.pwm.PwmSession;

import java.io.Serializable;

public class RestRequestBean implements Serializable {
    private boolean authenticated;
    private boolean external;
    private String userDN;
    private PwmSession pwmSession;
    private PwmApplication pwmApplication;

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public boolean isExternal() {
        return external;
    }

    public void setExternal(boolean external) {
        this.external = external;
    }

    public String getUserDN() {
        return userDN;
    }

    public void setUserDN(String userDN) {
        this.userDN = userDN;
    }

    public PwmSession getPwmSession() {
        return pwmSession;
    }

    public void setPwmSession(PwmSession pwmSession) {
        this.pwmSession = pwmSession;
    }

    public PwmApplication getPwmApplication() {
        return pwmApplication;
    }

    public void setPwmApplication(PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
    }
}
