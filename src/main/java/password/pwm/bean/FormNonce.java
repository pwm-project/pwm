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

package password.pwm.bean;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.Date;

public class FormNonce implements Serializable {

    @SerializedName("g")
    private final String sessionGUID;

    @SerializedName("t")
    private final Date timestamp;

    @SerializedName("c")
    private final int reqCounter;

    @SerializedName("p")
    private final String payload;

    public FormNonce(String sessionGUID, Date timestamp,  int reqCounter, String payload) {
        this.sessionGUID = sessionGUID;
        this.timestamp = timestamp;
        this.reqCounter = reqCounter;
        this.payload = payload;
    }

    public String getSessionGUID() {
        return sessionGUID;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public int getRequestID() {
        return reqCounter;
    }

    public String getPayload() {
        return payload;
    }
}
