/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.util.secure;

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
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
    private final Map<Type, SecretKey> keyCache = new HashMap<>();

    public PwmSecurityKey( final byte[] keyData )
    {
        this.keyData = Arrays.copyOf( keyData, keyData.length );
    }

    public PwmSecurityKey( final String keyData ) throws PwmUnrecoverableException
    {
        this.keyData = stringToKeyData( keyData );
    }

    byte[] stringToKeyData( final String input ) throws PwmUnrecoverableException
    {
        try
        {
            return input.getBytes( "iso-8859-1" );
        }
        catch ( UnsupportedEncodingException e )
        {
            final String errorMsg = "unexpected error converting input text to crypto key bytes: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CRYPT_ERROR, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    SecretKey getKey( final Type keyType )
            throws PwmUnrecoverableException
    {
        if ( !keyCache.containsKey( keyType ) )
        {
            keyCache.put( keyType, getKeyImpl( keyType ) );
        }
        return keyCache.get( keyType );
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
        catch ( Exception e )
        {
            final String errorMsg = "unexpected error generating simple crypto key: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CRYPT_ERROR, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }
}
