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

import java.security.SecureRandom;
import java.util.UUID;

public class PwmRandom
{

    private final SecureRandom internalRand = new SecureRandom();

    private static final PwmRandom SINGLETON = new PwmRandom();

    private static final String ALPHANUMERIC_STRING = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private PwmRandom( )
    {
    }

    public static PwmRandom getInstance( )
    {
        return SINGLETON;
    }

    public long nextLong( )
    {
        return internalRand.nextLong();
    }

    public int nextInt( )
    {
        return internalRand.nextInt();
    }

    public int nextInt( final int n )
    {
        return internalRand.nextInt( n );
    }

    public boolean nextBoolean( )
    {
        return internalRand.nextBoolean();
    }

    public String getAlgorithm( )
    {
        return internalRand.getAlgorithm();
    }

    public String alphaNumericString( final int length )
    {
        return alphaNumericString( ALPHANUMERIC_STRING, length );
    }

    public String alphaNumericString( final String characters, final int length )
    {
        final StringBuilder sb = new StringBuilder();
        while ( sb.length() < length )
        {
            sb.append( nextChar( characters ) );
        }
        return sb.toString();
    }

    public char nextChar( )
    {
        return nextChar( ALPHANUMERIC_STRING );
    }

    public char nextChar( final String characters )
    {
        if ( characters == null )
        {
            throw new NullPointerException( "characters cannot be null" );
        }
        return characters.charAt( nextInt( characters.length() ) );
    }

    public void nextBytes( final byte[] secArray )
    {
        internalRand.nextBytes( secArray );
    }

    public UUID randomUUID( )
    {
        return UUID.randomUUID();
    }

    public byte[] newBytes( final int length )
    {
        final byte[] newBytes = new byte[ length ];
        nextBytes( newBytes );
        return newBytes;
    }
}
