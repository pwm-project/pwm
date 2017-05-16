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

package password.pwm.svc.token;

import password.pwm.PwmApplication;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.secure.SecureService;

class StoredTokenKey implements TokenKey {
    private static final String SUFFIX = "-hash";

    private final String storedHash;

    private StoredTokenKey(final String storedHash) {
        this.storedHash = storedHash;
    }

    public String getStoredHash() {
        return storedHash;
    }
    
    static StoredTokenKey fromStoredHash(final String storedHash) {
        if (storedHash == null) {
            throw new NullPointerException();
        }
        
        if (!storedHash.endsWith(SUFFIX)) {
            throw new IllegalArgumentException("stored hash value has improper suffix");
        }
        
        return new StoredTokenKey(storedHash);
    }

    static StoredTokenKey fromKeyValue(final PwmApplication pwmApplication, final String input)
            throws PwmUnrecoverableException
    {
        if (input == null) {
            throw new NullPointerException();
        }

        if (input.endsWith(SUFFIX)) {
            throw new IllegalArgumentException("new key value has stored suffix");
        }

        final SecureService secureService = pwmApplication.getSecureService();
        final String storedHash = secureService.hash(input) + SUFFIX;

        return new StoredTokenKey(storedHash);
    }
}
