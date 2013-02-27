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

package password.pwm.event;

import password.pwm.bean.UserInfoBean;

import java.io.Serializable;
import java.util.Date;

/**
 * Sortable list of records.  Since we allow duplicate records with the same timestamp in a Set,
 * this Comparable implementation has a compareTo() that is NOT consistent with equals
 */
public class AuditRecord implements Comparable, Serializable {
    private final AuditEvent eventCode;
    private final String perpetratorID;
    private final String perpetratorDN;
    private final Date timestamp;
    private final String message;
    private final String targetID;
    private final String targetDN;

    public AuditRecord(
            final AuditEvent eventCode,
            final String perpetratorID,
            final String perpetratorDN,
            final Date timestamp,
            final String message,
            final String targetID,
            final String targetDN
    ) {
        this.eventCode = eventCode;
        this.perpetratorID = perpetratorID;
        this.perpetratorDN = perpetratorDN;
        this.timestamp = timestamp;
        this.message = message;
        this.targetID = targetID;
        this.targetDN = targetDN;

    }

    public AuditRecord(
            final AuditEvent eventCode,
            final String perpetratorID,
            final String perpetratorDN
    ) {
        this(eventCode,perpetratorID,perpetratorDN,new Date(),null,perpetratorID,perpetratorDN);
    }

    public AuditRecord(
            final AuditEvent eventCode,
            final UserInfoBean userInfoBean
    ) {
        this(eventCode,userInfoBean.getUserID(),userInfoBean.getUserDN(),new Date(),null,userInfoBean.getUserID(),userInfoBean.getUserDN());
    }

    public AuditEvent getEventCode() {
        return eventCode;
    }

    public String getPerpetratorID() {
        return perpetratorID;
    }

    public String getPerpetratorDN() {
        return perpetratorDN;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }

    public String getTargetID() {
        return targetID;
    }

    public String getTargetDN() {
        return targetDN;
    }

    public int compareTo(final Object o) {
        final AuditRecord otherRecord = (AuditRecord) o;

        if (otherRecord.equals(this)) {
            return 0;
        }

        return otherRecord.getTimestamp().compareTo(this.getTimestamp());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AuditRecord that = (AuditRecord) o;

        if (eventCode != that.eventCode) return false;
        if (!timestamp.equals(that.timestamp)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = eventCode.hashCode();
        result = 31 * result + timestamp.hashCode();
        return result;
    }
}
