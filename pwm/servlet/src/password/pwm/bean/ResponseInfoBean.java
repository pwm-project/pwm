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

import com.novell.ldapchai.cr.Challenge;

import java.io.Serializable;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class ResponseInfoBean implements Serializable {
    final private Map<Challenge,String> crMap;
    final private Map<Challenge,String> helpdeskCrMap;
    final private Locale locale;
    final private int minRandoms;
    final private String csIdentifier;
    private Date timestamp;

    public ResponseInfoBean(Map<Challenge, String> crMap, Map<Challenge,String> helpdeskCrMap, Locale locale, int minRandoms, String csIdentifier) {
        this.crMap = crMap;
        this.helpdeskCrMap = helpdeskCrMap;
        this.locale = locale;
        this.minRandoms = minRandoms;
        this.csIdentifier = csIdentifier;
    }

    public Map<Challenge, String> getCrMap() {
        return crMap;
    }

    public Locale getLocale() {
        return locale;
    }

    public int getMinRandoms() {
        return minRandoms;
    }

    public String getCsIdentifier() {
        return csIdentifier;
    }

    public Map<Challenge, String> getHelpdeskCrMap() {
        return helpdeskCrMap;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
}
