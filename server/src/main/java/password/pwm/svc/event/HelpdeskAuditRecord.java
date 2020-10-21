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

package password.pwm.svc.event;

import java.time.Instant;

public class HelpdeskAuditRecord extends UserAuditRecord
{
    protected String targetID;
    protected String targetDN;
    protected String targetLdapProfile;

    @SuppressWarnings( "checkstyle:ParameterNumber" )
    HelpdeskAuditRecord(
            final Instant timestamp,
            final AuditEvent eventCode,
            final String perpetratorID,
            final String perpetratorDN,
            final String perpetratorLdapProfile,
            final String message,
            final String targetID,
            final String targetDN,
            final String targetLdapProfile,
            final String sourceAddress,
            final String sourceHost
    )
    {
        super( timestamp, eventCode, perpetratorID, perpetratorDN, perpetratorLdapProfile, message, sourceHost, sourceAddress );
        this.perpetratorID = perpetratorID;
        this.perpetratorDN = perpetratorDN;
        this.perpetratorLdapProfile = perpetratorLdapProfile;
        this.targetID = targetID;
        this.targetDN = targetDN;
        this.targetLdapProfile = targetLdapProfile;
        this.sourceAddress = sourceAddress;
        this.sourceHost = sourceHost;
    }


    public String getTargetID( )
    {
        return targetID;
    }

    public String getTargetDN( )
    {
        return targetDN;
    }

    public String getTargetLdapProfile( )
    {
        return targetLdapProfile;
    }
}
