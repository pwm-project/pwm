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

public class SmsItemBean implements Serializable {
    private String to;
    private String from;
    private String message;
    private Integer partlength;
    private Integer pos = 0;

    // --------------------------- CONSTRUCTORS ---------------------------
    public SmsItemBean(
            final String to,
            final String from,
            final String message,
            final Integer partlength
    ) {
        this.to = to;
        this.from = from;
        this.message = message;
        this.partlength = partlength;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }
    
    public boolean hasNextPart() {
        return (pos < message.length());
    }

    public String getNextPart() {
        String ret = "";
        Integer l = message.length();
        Integer s = l - pos;
        if (s > partlength) {
            s = partlength;
        }
        if (s > 0) {
            ret = message.substring(pos, pos+s);
            pos += s;
        }
        return ret;
    }

    public void reset() {
        pos = 0;
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("from: ").append(from).append(", to: ").append(to);
        return sb.toString();
    }

    public Integer getPartlength() {
        return partlength;
    }
}
