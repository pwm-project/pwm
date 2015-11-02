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

import java.util.Collections;
import java.util.Iterator;

class CryptoTokenMachine implements TokenMachine {

    private TokenService tokenService;

    CryptoTokenMachine(TokenService tokenService)
            throws PwmOperationalException
    {
        this.tokenService = tokenService;
    }

    public String generateToken(
            SessionLabel sessionLabel,
            TokenPayload tokenPayload
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

    public TokenPayload retrieveToken(String tokenKey)
            throws PwmOperationalException, PwmUnrecoverableException
    {
        if (tokenKey == null || tokenKey.length() < 1) {
            return null;
        }
        return tokenService.fromEncryptedString(tokenKey);
    }

    public void storeToken(String tokenKey, TokenPayload tokenPayload) throws PwmOperationalException, PwmUnrecoverableException {
    }

    public void removeToken(String tokenKey) throws PwmOperationalException, PwmUnrecoverableException {
    }

    public int size() throws PwmOperationalException, PwmUnrecoverableException {
        return 0;
    }

    public Iterator keyIterator() throws PwmOperationalException, PwmUnrecoverableException {
        return Collections.<String>emptyList().iterator();
    }

    public void cleanup() {
    }

    public boolean supportsName() {
        return true;
    }

}
