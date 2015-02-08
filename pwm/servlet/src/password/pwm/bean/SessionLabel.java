/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.bean;

import java.io.Serializable;

public class SessionLabel implements Serializable {
    public static final SessionLabel SYSTEM_LABEL = null;

    private final String sessionID;
    private final UserIdentity userIdentity;
    private final String username;
    private final String srcAddress;
    private final String srcHostname;

    public SessionLabel(String sessionID, UserIdentity userIdentity, String username, final String srcAddress, final String srcHostname)
    {
        this.sessionID = sessionID;
        this.userIdentity = userIdentity;
        this.username = username;
        this.srcAddress = srcAddress;
        this.srcHostname = srcHostname;
    }

    public String getSessionID()
    {
        return sessionID;
    }

    public String getUsername()
    {
        return username;
    }

    public UserIdentity getUserIdentity()
    {
        return userIdentity;
    }

    public String getSrcAddress()
    {
        return srcAddress;
    }

    public String getSrcHostname()
    {
        return srcHostname;
    }
    
    public String toString() {
        if (this.getSessionID() == null || this.getSessionID().isEmpty()) {
            return "";
        }
        return "{" + this.getSessionID() 
                + (this.getUsername() != null && !this.getUsername().isEmpty() ? "," + this.getUsername() : "")
                + "}";
        
    }
}
