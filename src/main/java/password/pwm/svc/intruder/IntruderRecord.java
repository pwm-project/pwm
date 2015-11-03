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

package password.pwm.svc.intruder;

import java.io.Serializable;
import java.util.Date;

public class IntruderRecord implements Serializable {
    private RecordType type;
    private String subject;
    private Date timeStamp = new Date();
    private int attemptCount = 0;
    private boolean alerted = false;

    IntruderRecord() {
    }

    public IntruderRecord(final RecordType type, final String subject) {
        if (type == null) {
            throw new IllegalArgumentException("type must have a value");
        }
        if (subject == null || subject.length() < 1) {
            throw new IllegalArgumentException("subject must have a value");
        }
        this.type = type;
        this.subject = subject;
    }

    public RecordType getType() {
        return type;
    }

    public String getSubject() {
        return subject;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    void incrementAttemptCount() {
        timeStamp = new Date();
        attemptCount++;
    }

    void clearAttemptCount() {
        alerted = false;
        attemptCount = 0;
    }

    public boolean isAlerted() {
        return alerted;
    }

    void setAlerted() {
        this.alerted = true;
    }
}
