/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.StringUtil;
import password.pwm.util.secure.SecureService;

class StoredTokenKey implements TokenKey
{
    private static final String SUFFIX = "-hash";

    private final String storedHash;

    private StoredTokenKey( final String storedHash )
    {
        this.storedHash = storedHash;
    }

    @Override
    public String getStoredHash( )
    {
        return storedHash;
    }

    static StoredTokenKey fromStoredHash( final String storedHash )
    {
        if ( storedHash == null )
        {
            throw new NullPointerException();
        }

        if ( !storedHash.endsWith( SUFFIX ) )
        {
            throw new IllegalArgumentException( "stored hash value has improper suffix" );
        }

        return new StoredTokenKey( storedHash );
    }

    static StoredTokenKey fromKeyValue( final PwmApplication pwmApplication, final String input )
            throws PwmUnrecoverableException
    {
        if ( input == null )
        {
            throw new NullPointerException();
        }

        if ( input.endsWith( SUFFIX ) )
        {
            throw new IllegalArgumentException( "new key value has stored suffix" );
        }

        final int maxHashLength = Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.TOKEN_STORAGE_MAX_KEY_LENGTH ) );
        final SecureService secureService = pwmApplication.getSecureService();
        final String generatedHash = secureService.hash( input );
        final String storedHash = StringUtil.truncate( generatedHash, maxHashLength ) + SUFFIX;

        return new StoredTokenKey( storedHash );
    }
}
