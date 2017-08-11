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

interface TokenMachine {
    String generateToken( SessionLabel sessionLabel,  TokenPayload tokenPayload)
            throws PwmUnrecoverableException, PwmOperationalException;

    TokenPayload retrieveToken(TokenKey tokenKey)
            throws PwmOperationalException, PwmUnrecoverableException;

    void storeToken(TokenKey tokenKey, TokenPayload tokenPayload)
            throws PwmOperationalException, PwmUnrecoverableException;

    void removeToken(TokenKey tokenKey)
            throws PwmOperationalException, PwmUnrecoverableException;

    int size()
            throws PwmOperationalException, PwmUnrecoverableException;

    void cleanup()
            throws PwmUnrecoverableException, PwmOperationalException;

    boolean supportsName();

    TokenKey keyFromKey(String key) throws PwmUnrecoverableException;

    TokenKey keyFromStoredHash(String storedHash);
}
