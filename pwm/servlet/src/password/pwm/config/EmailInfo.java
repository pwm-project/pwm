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

package password.pwm.config;

/**
 * @author Jason D. Rivard
 */
public class EmailInfo {
// ------------------------------ FIELDS ------------------------------

    public static final EmailInfo EMPTY_EMAIL_INFO = new EmailInfo(null, null, null, null);
    private final String to;
    private final String from;
    private final String body;
    private final String subject;

// --------------------------- CONSTRUCTORS ---------------------------

    public EmailInfo(final String to, final String from, final String body, final String subject) {
        this.to = to;
        this.from = from;
        this.body = body;
        this.subject = subject;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getBody() {
        return body;
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

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Email Info: ");
        if (from != null) {
            sb.append("from: ").append(from);
        }
        if (to != null) {
            sb.append("to: ").append(to);
        }
        if (subject != null) {
            sb.append("subject: ").append(subject);
        }
        if (body != null) {
            sb.append("body: ").append(body);
        }

        return sb.toString();
    }
}

