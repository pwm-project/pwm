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

package password.pwm.bean;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.io.Serializable;

@Value
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
    public static final SessionLabel PWNOTIFY_SESSION_LABEL = new SessionLabel( SESSION_LABEL_SESSION_ID, null, "pwnotify", null, null );
    public static final SessionLabel CONTEXT_SESSION_LABEL = new SessionLabel( SESSION_LABEL_SESSION_ID, null, "context", null, null );

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
