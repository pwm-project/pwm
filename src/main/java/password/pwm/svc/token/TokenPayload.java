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

package password.pwm.svc.token;

import password.pwm.bean.UserIdentity;

import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;

public class TokenPayload implements Serializable {
    private final java.util.Date date;
    private final String name;
    private final Map<String,String> data;
    private final UserIdentity user;
    private final Set<String> dest;
    private final String guid;

    TokenPayload(final String name, final Map<String, String> data, final UserIdentity user, final Set<String> dest, final String guid) {
        this.date = new Date();
        this.data = data == null ? Collections.<String,String>emptyMap() : Collections.unmodifiableMap(data);
        this.name = name;
        this.user = user;
        this.dest = dest == null ? Collections.<String>emptySet() : Collections.unmodifiableSet(dest);
        this.guid = guid;
    }

    public Date getDate() {
        return date;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getData() {
        return data;
    }

    public UserIdentity getUserIdentity() {
        return user;
    }

    public Set<String> getDest() {
        return dest;
    }

    public String getGuid() {
        return guid;
    }
}
