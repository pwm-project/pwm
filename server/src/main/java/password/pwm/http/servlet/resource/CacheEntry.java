/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.http.servlet.resource;

import password.pwm.http.bean.ImmutableByteArray;

import java.io.Serializable;
import java.util.Map;

final class CacheEntry implements Serializable {
    private final ImmutableByteArray entity;
    private final Map<String, String> headerStrings;

    CacheEntry(final byte[] entity, final Map<String, String> headerStrings) {
        this.entity = new ImmutableByteArray(entity);
        this.headerStrings = headerStrings;
    }

    public byte[] getEntity() {
        return entity.getBytes();
    }

    public Map<String, String> getHeaderStrings() {
        return headerStrings;
    }
}
