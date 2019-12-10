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

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings( "checkstyle:MultipleStringLiterals" )
public class TypicalHashMachine extends AbstractHashMachine implements ResponseHashMachineSpi
{

    private static final Map<ResponseHashAlgorithm, String> SUPPORTED_FORMATS;

    enum VERSION
    {
        // original version had bug where only one iteration was ever actually performed regardless of hashCount value
        A,

        // nominal working version
        B,
    }

    static
    {
        final Map<ResponseHashAlgorithm, String> map = new HashMap<>();
        map.put( ResponseHashAlgorithm.MD5, "MD5" );
        map.put( ResponseHashAlgorithm.SHA1, "SHA1" );
        map.put( ResponseHashAlgorithm.SHA1_SALT, "SHA1" );
        map.put( ResponseHashAlgorithm.SHA256_SALT, "SHA-256" );
        map.put( ResponseHashAlgorithm.SHA512_SALT, "SHA-512" );
        SUPPORTED_FORMATS = Collections.unmodifiableMap( map );
    }

    private ResponseHashAlgorithm responseHashAlgorithm;

    public TypicalHashMachine( )
    {
    }

    public void init( final ResponseHashAlgorithm responseHashAlgorithm )
    {
        this.responseHashAlgorithm = responseHashAlgorithm;
        if ( !SUPPORTED_FORMATS.containsKey( responseHashAlgorithm ) )
        {
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
        final String newHash = doHash( input, hashedResponse.getIterations(), ResponseHashAlgorithm.SHA1_SALT, VERSION.B );
        return newHash.equals( hashedResponse.getHash() );
    }

    static String doHash(
            final String input,
            final int hashCount,
            final ResponseHashAlgorithm formatType,
            final VERSION version
    )
            throws IllegalStateException
    {
        final String algorithm = SUPPORTED_FORMATS.get( formatType );
        final MessageDigest md;
        try
        {
            md = MessageDigest.getInstance( algorithm );
        }
        catch ( final NoSuchAlgorithmException e )
        {
            throw new IllegalStateException( "unable to load " + algorithm + " message digest algorithm: " + e.getMessage() );
        }


        byte[] hashedBytes;
        try
        {
            hashedBytes = input.getBytes( "UTF-8" );
        }
        catch ( final UnsupportedEncodingException e )
        {
            throw new IllegalStateException( "unsupported UTF8 byte encoding: " + e.getMessage() );
        }

        switch ( version )
        {
            case A:
                hashedBytes = md.digest( hashedBytes );
                return Base64.encodeBytes( hashedBytes );

            case B:
                for ( int i = 0; i < hashCount; i++ )
                {
                    hashedBytes = md.digest( hashedBytes );
                }
                return Base64.encodeBytes( hashedBytes );

            default:
                throw new IllegalStateException( "unexpected version enum in hash method" );
        }
    }
}
