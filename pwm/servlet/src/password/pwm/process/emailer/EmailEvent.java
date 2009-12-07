/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

package password.pwm.process.emailer;

import java.io.Serializable;

/**
 * @author Jason D. Rivard
 */
public class EmailEvent implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private final String to;
    private final String from;
    private final String subject;
    private final String body;

    private long errorCount = 0;

// --------------------------- CONSTRUCTORS ---------------------------

    public EmailEvent(final String to, final String from, final String subject, final String body) {
        this.to = to;
        this.from = from;
        this.subject = subject;
        this.body = body;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getBody() {
        return body;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public String getFrom() {
        return from;
    }

    public String getSubject() {
        return subject;
    }

    public String getTo() {
        return to;
    }

// ------------------------ CANONICAL METHODS ------------------------

    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final EmailEvent event = (EmailEvent) o;

        if (body != null ? !body.equals(event.body) : event.body != null)
            return false;
        if (from != null ? !from.equals(event.from) : event.from != null)
            return false;
        if (subject != null ? !subject.equals(event.subject) : event.subject != null)
            return false;
        if (to != null ? !to.equals(event.to) : event.to != null) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = (to != null ? to.hashCode() : 0);
        result = 29 * result + (from != null ? from.hashCode() : 0);
        result = 29 * result + (subject != null ? subject.hashCode() : 0);
        result = 29 * result + (body != null ? body.hashCode() : 0);
        return result;
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("from: ").append(from).append(", to: ").append(to).append(", subject: ").append(subject);
        return sb.toString();
    }

// -------------------------- OTHER METHODS --------------------------

    public void incrementErrorCount() {
        errorCount++;
    }
}

