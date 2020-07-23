/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.svc.token;

import password.pwm.bean.SessionLabel;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;

import java.util.Optional;

interface TokenMachine
{
    String generateToken( SessionLabel sessionLabel, TokenPayload tokenPayload )
            throws PwmUnrecoverableException, PwmOperationalException;

    Optional<TokenPayload> retrieveToken( SessionLabel sessionLabel, TokenKey tokenKey )
            throws PwmOperationalException, PwmUnrecoverableException;

    void storeToken( TokenKey tokenKey, TokenPayload tokenPayload )
            throws PwmOperationalException, PwmUnrecoverableException;

    void removeToken( TokenKey tokenKey )
            throws PwmOperationalException, PwmUnrecoverableException;

    long size( )
            throws PwmOperationalException, PwmUnrecoverableException;

    void cleanup( )
            throws PwmUnrecoverableException, PwmOperationalException;

    boolean supportsName( );

    TokenKey keyFromKey( String key ) throws PwmUnrecoverableException;

    TokenKey keyFromStoredHash( String storedHash );
}
