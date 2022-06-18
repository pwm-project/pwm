/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.util.secure;

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.secure.SecureService;
import password.pwm.util.java.JavaHelper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

public class PwmSecurityKey
{
    enum Type
    {
        AES,
        AES_256,
        HMAC_256,
        HMAC_512,
    }

    private final byte[] keyData;
    private final Map<Type, SecretKey> keyCache = new EnumMap<>( Type.class );

    public PwmSecurityKey( final byte[] keyData )
    {
        this.keyData = Arrays.copyOf( keyData, keyData.length );
    }

    public PwmSecurityKey( final String keyData ) throws PwmUnrecoverableException
    {
        this.keyData = stringToKeyData( keyData );
    }

    public String keyHash( final SecureService domainSecureService )
            throws PwmUnrecoverableException
    {
        return domainSecureService.hash( keyData );
    }

    private byte[] stringToKeyData( final String input ) throws PwmUnrecoverableException
    {
        return input.getBytes( StandardCharsets.ISO_8859_1 );
    }

    SecretKey getKey( final Type keyType )
            throws PwmUnrecoverableException
    {
        final SecretKey theKey = keyCache.get( keyType );
        if ( theKey == null )
        {
            final SecretKey newKey = getKeyImpl( keyType );
            keyCache.put( keyType, newKey );
            return newKey;
        }
        return theKey;
    }

    private SecretKey getKeyImpl( final Type keyType )
            throws PwmUnrecoverableException
    {
        switch ( keyType )
        {
            case AES:
            {
                return shaBasedKey( "AES", PwmHashAlgorithm.SHA1, 16 );
            }

            case AES_256:
            {
                return shaBasedKey( "AES", PwmHashAlgorithm.SHA256, 16 );
            }

            case HMAC_256:
            {
                return new SecretKeySpec( keyData, "HmacSHA256" );
            }

            case HMAC_512:
            {
                return new SecretKeySpec( keyData, "HmacSHA512" );
            }

            default:
                throw new UnsupportedOperationException( "unknown key type: " + keyType );
        }
    }

    private SecretKey shaBasedKey( final String keySpecName, final PwmHashAlgorithm pwmHashAlgorithm, final int keyLength ) throws PwmUnrecoverableException
    {
        try
        {
            final byte[] sha1Hash = SecureEngine.computeHashToBytes( new ByteArrayInputStream( keyData ), pwmHashAlgorithm );
            final byte[] key = Arrays.copyOfRange( sha1Hash, 0, keyLength );
            return new SecretKeySpec( key, keySpecName );
        }
        catch ( final Exception e )
        {
            final String errorMsg = "unexpected error generating simple crypto key: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CRYPT_ERROR, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    public PwmSecurityKey add( final PwmSecurityKey otherKey )
    {
        final byte[] newKeyMaterial = JavaHelper.concatByteArrays( this.keyData, otherKey.keyData );
        return new PwmSecurityKey( newKeyMaterial );
    }

}
