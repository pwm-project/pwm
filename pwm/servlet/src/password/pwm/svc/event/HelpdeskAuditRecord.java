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

package password.pwm.svc.event;

import java.util.Date;

public class HelpdeskAuditRecord extends UserAuditRecord {
    protected String targetID;
    protected String targetDN;
    protected String targetLdapProfile;

    private HelpdeskAuditRecord(
            final Date timestamp,
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
    ) {
        super(timestamp, eventCode, perpetratorID, perpetratorDN, perpetratorLdapProfile, message, sourceHost, sourceAddress);
        this.perpetratorID = perpetratorID;
        this.perpetratorDN = perpetratorDN;
        this.perpetratorLdapProfile = perpetratorLdapProfile;
        this.targetID = targetID;
        this.targetDN = targetDN;
        this.targetLdapProfile = targetLdapProfile;
        this.sourceAddress = sourceAddress;
        this.sourceHost = sourceHost;
    }


    static HelpdeskAuditRecord create(
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
        return new HelpdeskAuditRecord(new Date(), eventCode, perpetratorID, perpetratorDN, perpetratorLdapProfile, message, targetID, targetDN,
                targetLdapProfile, sourceAddress, sourceHost);
    }

    static UserAuditRecord create(
            final Date timestamp,
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
        return new HelpdeskAuditRecord(timestamp, eventCode, perpetratorID, perpetratorDN, perpetratorLdapProfile, message, targetID, targetDN,
                targetLdapProfile, sourceAddress, sourceHost);
    }

    public String getTargetID() {
        return targetID;
    }

    public String getTargetDN() {
        return targetDN;
    }

    public String getTargetLdapProfile()
    {
        return targetLdapProfile;
    }
}
