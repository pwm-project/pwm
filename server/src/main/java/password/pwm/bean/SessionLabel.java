/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

@Getter
@AllArgsConstructor
public class SessionLabel implements Serializable
{
    public static final SessionLabel SYSTEM_LABEL = null;
    public static final String SESSION_LABEL_SESSION_ID = "#";
    public static final SessionLabel PW_EXP_NOTICE_LABEL = new SessionLabel( SESSION_LABEL_SESSION_ID, null, "pwExpireNotice", null, null );
    public static final SessionLabel TOKEN_SESSION_LABEL = new SessionLabel( SESSION_LABEL_SESSION_ID, null, "token", null, null );
    public static final SessionLabel CLI_SESSION_LABEL = new SessionLabel( SESSION_LABEL_SESSION_ID, null, "cli", null, null );
    public static final SessionLabel HEALTH_SESSION_LABEL = new SessionLabel( SESSION_LABEL_SESSION_ID, null, "health", null, null );
    public static final SessionLabel REPORTING_SESSION_LABEL = new SessionLabel( SESSION_LABEL_SESSION_ID, null, "reporting", null, null );
    public static final SessionLabel AUDITING_SESSION_LABEL = new SessionLabel( SESSION_LABEL_SESSION_ID, null, "auditing", null, null );
    public static final SessionLabel TELEMETRY_SESSION_LABEL = new SessionLabel( SESSION_LABEL_SESSION_ID, null, "telemetry", null, null );

    private final String sessionID;
    private final UserIdentity userIdentity;
    private final String username;
    private final String srcAddress;
    private final String srcHostname;

    public String toString( )
    {
        if ( this.getSessionID() == null || this.getSessionID().isEmpty() )
        {
            return "";
        }
        return "{" + this.getSessionID()
                + ( this.getUsername() != null && !this.getUsername().isEmpty() ? "," + this.getUsername() : "" )
                + "}";

    }
}
