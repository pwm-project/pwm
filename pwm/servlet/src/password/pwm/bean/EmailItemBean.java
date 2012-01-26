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

package password.pwm.bean;

import java.io.Serializable;

public class EmailItemBean implements Serializable {
    private String to;
    private String from;
    private String subject;
    private String bodyPlain;
    private String bodyHtml;


    // --------------------------- CONSTRUCTORS ---------------------------
    private EmailItemBean() {
    }

    public EmailItemBean(
            final String to,
            final String from,
            final String subject,
            final String bodyPlain,
            final String bodyHtml
    ) {
        this.to = to;
        this.from = from;
        this.subject = subject;
        this.bodyPlain = bodyPlain;
        this.bodyHtml = bodyHtml;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getBodyPlain() {
        return bodyPlain;
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

    public String getBodyHtml() {
        return bodyHtml;
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("from: ").append(from).append(", to: ").append(to).append(", subject: ").append(subject);
        return sb.toString();
    }

}
