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

import password.pwm.bean.SessionLabel;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.localdb.LocalDB;

class LocalDBTokenMachine implements TokenMachine {
    private LocalDB localDB;
    private TokenService tokenService;

    LocalDBTokenMachine(
            TokenService tokenService,
            LocalDB localDB
    ) {
        this.tokenService = tokenService;
        this.localDB = localDB;
    }

    public String generateToken(
            SessionLabel sessionLabel,
            TokenPayload tokenPayload
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        return tokenService.makeUniqueTokenForMachine(sessionLabel, this);
    }

    public TokenPayload retrieveToken(String tokenKey)
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final String md5sumToken = TokenService.makeTokenHash(tokenKey);
        final String storedRawValue = localDB.get(LocalDB.DB.TOKENS, md5sumToken);

        if (storedRawValue != null && storedRawValue.length() > 0 ) {
            return tokenService.fromEncryptedString(storedRawValue);
        }

        return null;
    }

    public void storeToken(String tokenKey, TokenPayload tokenPayload) throws PwmOperationalException, PwmUnrecoverableException {
        final String rawValue = tokenService.toEncryptedString(tokenPayload);
        final String md5sumToken = TokenService.makeTokenHash(tokenKey);
        localDB.put(LocalDB.DB.TOKENS, md5sumToken, rawValue);
    }

    public void removeToken(String tokenKey)
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final String md5sumToken = TokenService.makeTokenHash(tokenKey);
        localDB.remove(LocalDB.DB.TOKENS, tokenKey);
        localDB.remove(LocalDB.DB.TOKENS, md5sumToken);
    }

    public int size() throws PwmOperationalException {
        return localDB.size(LocalDB.DB.TOKENS);
    }

    public LocalDB.LocalDBIterator<String> keyIterator() throws PwmOperationalException {
        return localDB.iterator(LocalDB.DB.TOKENS);
    }

    public void cleanup() throws PwmUnrecoverableException, PwmOperationalException {
        tokenService.purgeOutdatedTokens();
    }

    public boolean supportsName() {
        return true;
    }
}
