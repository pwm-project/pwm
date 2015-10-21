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

package password.pwm.svc.cache;

import password.pwm.bean.UserIdentity;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.SecureEngine;

public class CacheKey {
    private final String cacheKey;
    private String hash;

    private CacheKey(final String cacheKey)
    {
        if (cacheKey == null) {
            throw new NullPointerException("key can not be null");
        }
        this.cacheKey = cacheKey;
    }

    String getHash()
            throws PwmUnrecoverableException
    {
        if (hash != null) {
            return hash;
        }
        hash = SecureEngine.hash(this.cacheKey, PwmHashAlgorithm.SHA256);
        return hash;
    }

    String getStorageValue() {
        return cacheKey;

    }

    static CacheKey fromStorageValue(final String input) {
        return new CacheKey(input);
    }

    public static CacheKey makeCacheKey(
            final Class srcClass,
            final UserIdentity userIdentity,
            final String valueID
    ) {
        if (srcClass == null) {
            throw new NullPointerException("srcClass can not be null");
        }
        if (valueID == null) {
            throw new NullPointerException("valueID can not be null");
        }
        if (valueID.isEmpty()) {
            throw new IllegalArgumentException("valueID can not be empty");
        }
        return new CacheKey(srcClass.getName() + "!" + (userIdentity == null ? "null" : userIdentity.toDelimitedKey()) + "!" + valueID);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CacheKey cacheKey1 = (CacheKey) o;

        if (!cacheKey.equals(cacheKey1.cacheKey)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return cacheKey.hashCode();
    }
    
    public String toString() {
        return cacheKey;
    }
}
