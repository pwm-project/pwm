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

package password.pwm.cr.hash;

import net.iharder.Base64;
import password.pwm.cr.api.StoredResponseItem;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
        catch ( NoSuchAlgorithmException e )
        {
            throw new IllegalStateException( "unable to load " + algorithm + " message digest algorithm: " + e.getMessage() );
        }


        byte[] hashedBytes;
        try
        {
            hashedBytes = input.getBytes( "UTF-8" );
        }
        catch ( UnsupportedEncodingException e )
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
