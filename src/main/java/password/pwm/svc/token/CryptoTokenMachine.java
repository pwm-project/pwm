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

package password.pwm.svc.token;

import password.pwm.bean.SessionLabel;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.ClosableIterator;

class CryptoTokenMachine implements TokenMachine {

    private final TokenService tokenService;

    CryptoTokenMachine(final TokenService tokenService)
            throws PwmOperationalException
    {
        this.tokenService = tokenService;
    }

    public String generateToken(
            final SessionLabel sessionLabel,
            final TokenPayload tokenPayload
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final int WRAP_LENGTH = 60;
        final StringBuilder returnString = new StringBuilder(tokenService.toEncryptedString(tokenPayload));
        for (int i = WRAP_LENGTH - 1; i < returnString.length(); i += WRAP_LENGTH) {
            returnString.insert(i,"\n");
        }
        return returnString.toString();
    }

    public TokenPayload retrieveToken(final TokenKey tokenKey)
            throws PwmOperationalException, PwmUnrecoverableException
    {
        if (tokenKey == null || tokenKey.getStoredHash().length() < 1) {
            return null;
        }
        return tokenService.fromEncryptedString(tokenKey.getStoredHash());
    }

    public void storeToken(final TokenKey tokenKey, final TokenPayload tokenPayload) throws PwmOperationalException, PwmUnrecoverableException {
    }

    public void removeToken(final TokenKey tokenKey) throws PwmOperationalException, PwmUnrecoverableException {
    }

    public int size() throws PwmOperationalException, PwmUnrecoverableException {
        return 0;
    }

    public ClosableIterator<TokenKey> keyIterator() throws PwmOperationalException, PwmUnrecoverableException {
        return new ClosableIterator<TokenKey>() {
            @Override
            public void close() {
            }

            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public TokenKey next() {
                return null;
            }
        };
    }

    public void cleanup() {
    }

    public boolean supportsName() {
        return true;
    }

    public TokenKey keyFromKey(final String key) throws PwmUnrecoverableException {
        return new CryptoTokenKey(key);
    }

    @Override
    public TokenKey keyFromStoredHash(final String storedHash) {
        return new CryptoTokenKey(storedHash);
    }

    private static class CryptoTokenKey implements TokenKey {
        private String value;

        CryptoTokenKey(final String value) {
            this.value = value;
        }

        @Override
        public String getStoredHash() {
            return value;
        }
    }
}
