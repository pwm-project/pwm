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

class CryptoTokenMachine implements TokenMachine
{

    private final TokenService tokenService;

    CryptoTokenMachine( final TokenService tokenService )
            throws PwmOperationalException
    {
        this.tokenService = tokenService;
    }

    @Override
    public String generateToken(
            final SessionLabel sessionLabel,
            final TokenPayload tokenPayload
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final int wrapLength = 60;
        final StringBuilder returnString = new StringBuilder( tokenService.toEncryptedString( tokenPayload ) );
        for ( int i = wrapLength - 1; i < returnString.length(); i += wrapLength )
        {
            returnString.insert( i, "\n" );
        }
        return returnString.toString();
    }

    @Override
    public Optional<TokenPayload> retrieveToken( final SessionLabel sessionLabel, final TokenKey tokenKey )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        if ( tokenKey == null || tokenKey.getStoredHash().length() < 1 )
        {
            return Optional.empty();
        }
        return Optional.of( tokenService.fromEncryptedString( tokenKey.getStoredHash() ) );
    }

    public void storeToken( final TokenKey tokenKey, final TokenPayload tokenPayload ) throws PwmOperationalException, PwmUnrecoverableException
    {
    }

    public void removeToken( final TokenKey tokenKey ) throws PwmOperationalException, PwmUnrecoverableException
    {
    }

    public long size( ) throws PwmOperationalException, PwmUnrecoverableException
    {
        return 0;
    }

    public void cleanup( )
    {
    }

    public boolean supportsName( )
    {
        return true;
    }

    public TokenKey keyFromKey( final String key ) throws PwmUnrecoverableException
    {
        return new CryptoTokenKey( key );
    }

    @Override
    public TokenKey keyFromStoredHash( final String storedHash )
    {
        return new CryptoTokenKey( storedHash );
    }

    private static class CryptoTokenKey implements TokenKey
    {
        private String value;

        CryptoTokenKey( final String value )
        {
            this.value = value;
        }

        @Override
        public String getStoredHash( )
        {
            return value;
        }
    }
}
