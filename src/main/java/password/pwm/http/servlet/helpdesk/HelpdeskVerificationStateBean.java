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

package password.pwm.http.servlet.helpdesk;

import password.pwm.bean.UserIdentity;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

public class HelpdeskVerificationStateBean implements Serializable {
    private UserIdentity actor;
    private List<HelpdeskValidationRecord> records;

    public HelpdeskVerificationStateBean(UserIdentity actor, List<HelpdeskValidationRecord> records) {
        this.actor = actor;
        this.records = records;
    }

    public UserIdentity getActor() {
        return actor;
    }

    public List<HelpdeskValidationRecord> getRecords() {
        return records;
    }

    static class HelpdeskValidationRecord {
        private Date timestamp;
        private UserIdentity identity;

        public HelpdeskValidationRecord(Date timestamp, UserIdentity identity) {
            this.timestamp = timestamp;
            this.identity = identity;
        }

        public Date getTimestamp() {
            return timestamp;
        }

        public UserIdentity getIdentity() {
            return identity;
        }
    }
}
