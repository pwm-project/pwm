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

package password.pwm.cr.hash;

import net.iharder.Base64;
import password.pwm.cr.api.StoredResponseItem;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class PBKDF2HashMachine extends AbstractHashMachine implements ResponseHashMachineSpi
{

    private ResponseHashAlgorithm responseHashAlgorithm;

    PBKDF2HashMachine( )
    {
    }

    public void init( final ResponseHashAlgorithm responseHashAlgorithm )
    {
        this.responseHashAlgorithm = responseHashAlgorithm;
        switch ( responseHashAlgorithm )
        {
            case PBKDF2:
            case PBKDF2_SHA256:
            case PBKDF2_SHA512:
                break;

            default:
                throw new IllegalArgumentException( "implementation does not support hash algorithm " + responseHashAlgorithm );
        }
    }

    @Override
    public Map<String, String> defaultParameters( )
    {
        final Map<String, String> map = new HashMap<>();
        map.put( HashParameter.caseSensitive.toString(), String.valueOf( false ) );
        return Collections.unmodifiableMap( map );
    }

    @Override
    public StoredResponseItem generate( final String input )
    {
        //@todo
        return null;
    }

    @Override
    public boolean test( final StoredResponseItem hashedResponse, final String input )
    {
        final String newHash = hashValue( input, hashedResponse.getIterations(), hashedResponse.getSalt() );
        return newHash.equals( hashedResponse.getHash() );
    }

    private String hashValue( final String input, final int iterations, final String salt )
    {
        try
        {
            final PBEKeySpec spec;
            final SecretKeyFactory skf;
            {
                final String methodName;
                final int keyLength;
                switch ( responseHashAlgorithm )
                {
                    case PBKDF2:
                        methodName = "PBKDF2WithHmacSHA1";
                        keyLength = 64 * 8;
                        break;

                    case PBKDF2_SHA256:
                        methodName = "PBKDF2WithHmacSHA256";
                        keyLength = 128 * 8;
                        break;

                    case PBKDF2_SHA512:
                        methodName = "PBKDF2WithHmacSHA512";
                        keyLength = 192 * 8;
                        break;

                    default:
                        throw new IllegalStateException( "formatType not supported: " + responseHashAlgorithm.toString() );

                }

                final char[] chars = input.toCharArray();
                final byte[] saltBytes = salt.getBytes( "UTF-8" );

                spec = new PBEKeySpec( chars, saltBytes, iterations, keyLength );
                skf = SecretKeyFactory.getInstance( methodName );
            }
            final byte[] hash = skf.generateSecret( spec ).getEncoded();
            return Base64.encodeBytes( hash );
        }
        catch ( final Exception e )
        {
            throw new IllegalStateException( "unable to perform PBKDF2 hashing operation: " + e.getMessage() );
        }
    }

}
