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

import password.pwm.PwmSession;
import password.pwm.bean.UserInfoBean;

import java.io.Serializable;
import java.util.Date;

/**
 * AuditRecord data
 */
public class AuditRecord implements Serializable {
    private AuditEvent eventCode;
    private String perpetratorID;
    private String perpetratorDN;
    private Date timestamp = new Date();
    private String message;
    private String targetID;
    private String targetDN;
    private String sourceAddress;
    private String sourceHost;

    public AuditRecord(
            final AuditEvent eventCode,
            final String perpetratorID,
            final String perpetratorDN,
            final Date timestamp,
            final String message,
            final String targetID,
            final String targetDN,
            final String sourceAddress,
            final String sourceHost
    ) {
        this.eventCode = eventCode;
        this.perpetratorID = perpetratorID;
        this.perpetratorDN = perpetratorDN;
        this.timestamp = timestamp;
        this.message = message;
        this.targetID = targetID;
        this.targetDN = targetDN;
        this.sourceAddress = sourceAddress;
        this.sourceHost = sourceHost;
    }

    public AuditRecord(
            final AuditEvent eventCode,
            final String perpetratorID,
            final String perpetratorDN,
            final PwmSession pwmSession
    ) {
        this(
                eventCode,
                perpetratorID,
                perpetratorDN,
                new Date(),
                null,
                perpetratorID,
                perpetratorDN,
                pwmSession.getSessionStateBean().getSrcAddress(),
                pwmSession.getSessionStateBean().getSrcHostname()
        );
    }

    public AuditRecord(
            final AuditEvent eventCode,
            final UserInfoBean userInfoBean,
            final PwmSession pwmSession
    ) {
        this(eventCode,
                userInfoBean.getUserID(),
                userInfoBean.getUserDN(),
                new Date(),
                null,
                userInfoBean.getUserID(),
                userInfoBean.getUserDN(),
                pwmSession.getSessionStateBean().getSrcAddress(),
                pwmSession.getSessionStateBean().getSrcHostname()
        );
    }

    public AuditEvent getEventCode() {
        return eventCode;
    }

    public void setEventCode(AuditEvent eventCode) {
        this.eventCode = eventCode;
    }

    public String getPerpetratorID() {
        return perpetratorID;
    }

    public void setPerpetratorID(String perpetratorID) {
        this.perpetratorID = perpetratorID;
    }

    public String getPerpetratorDN() {
        return perpetratorDN;
    }

    public void setPerpetratorDN(String perpetratorDN) {
        this.perpetratorDN = perpetratorDN;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTargetID() {
        return targetID;
    }

    public void setTargetID(String targetID) {
        this.targetID = targetID;
    }

    public String getTargetDN() {
        return targetDN;
    }

    public void setTargetDN(String targetDN) {
        this.targetDN = targetDN;
    }

    public String getSourceAddress() {
        return sourceAddress;
    }

    public void setSourceAddress(String sourceAddress) {
        this.sourceAddress = sourceAddress;
    }

    public String getSourceHost() {
        return sourceHost;
    }

    public void setSourceHost(String sourceHost) {
        this.sourceHost = sourceHost;
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
