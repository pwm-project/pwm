/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.event;

/**
 * UserAuditRecord data
 */
public class UserAuditRecord extends AuditRecord {
    protected String perpetratorID;
    protected String perpetratorDN;
    protected String targetID;
    protected String targetDN;
    protected String sourceAddress;
    protected String sourceHost;

    private UserAuditRecord(
            final AuditEvent eventCode,
            final String perpetratorID,
            final String perpetratorDN,
            final String message,
            final String targetID,
            final String targetDN,
            final String sourceAddress,
            final String sourceHost
    ) {
        super(eventCode, message);
        this.perpetratorID = perpetratorID;
        this.perpetratorDN = perpetratorDN;
        this.targetID = targetID;
        this.targetDN = targetDN;
        this.sourceAddress = sourceAddress;
        this.sourceHost = sourceHost;
    }

    static UserAuditRecord create(
            final AuditEvent eventCode,
            final String perpetratorID,
            final String perpetratorDN,
            final String message,
            final String targetID,
            final String targetDN,
            final String sourceAddress,
            final String sourceHost
    )
    {
        return new UserAuditRecord(eventCode, perpetratorID, perpetratorDN, message, targetID, targetDN,
                sourceAddress, sourceHost);
    }


    public String getPerpetratorID() {
        return perpetratorID;
    }

    public String getPerpetratorDN() {
        return perpetratorDN;
    }

    public String getTargetID() {
        return targetID;
    }

    public String getTargetDN() {
        return targetDN;
    }

    public String getSourceAddress() {
        return sourceAddress;
    }

    public String getSourceHost() {
        return sourceHost;
    }
}
