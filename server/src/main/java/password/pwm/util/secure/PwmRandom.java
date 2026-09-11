/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2023 The PWM Project
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

import java.security.SecureRandom;
import java.util.UUID;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class PwmRandom 
{
    private final SecureRandom internalRand;

    private static final PwmRandom SINGLETON = new PwmRandom( new SecureRandom( ) );

    private static final String ALPHANUMERIC_STRING = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private PwmRandom( final SecureRandom internalRand )
    {
        this.internalRand = internalRand;
    }

    public static PwmRandom getInstance( )
    {
        return SINGLETON;
    }

    
    public long nextLong( )
    {
        return internalRand.nextLong();
    }

    public long nextLong( final long n )
    {
        long randomLong;
        do
        {
            randomLong = internalRand.nextLong();
        }
        while ( randomLong < 0 );

        return randomLong % n;
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

    
    public float nextFloat( )
    {
        return internalRand.nextFloat();
    }

    
    public double nextDouble( )
    {
        return internalRand.nextDouble();
    }

    
    public double nextGaussian( )
    {
            return internalRand.nextGaussian();
    }

    
    public IntStream ints( final long streamSize )
    {
        return internalRand.ints( streamSize );
    }

    
    public IntStream ints( )
    {
        return internalRand.ints();
    }

    
    public IntStream ints( final long streamSize, final int randomNumberOrigin, final int randomNumberBound )
    {
        return internalRand.ints( streamSize, randomNumberOrigin, randomNumberBound );
    }

    
    public IntStream ints( final int randomNumberOrigin, final int randomNumberBound )
    {
        return internalRand.ints( randomNumberOrigin, randomNumberBound );
    }

    
    public LongStream longs( final long streamSize )
    {
        return internalRand.longs( streamSize );
    }

    
    public LongStream longs( )
    {
        return internalRand.longs();
    }

    
    public LongStream longs( final long streamSize, final long randomNumberOrigin, final long randomNumberBound )
    {
        return internalRand.longs( streamSize, randomNumberOrigin, randomNumberBound );
    }

    
    public LongStream longs( final long randomNumberOrigin, final long randomNumberBound )
    {
        return internalRand.longs( randomNumberOrigin, randomNumberBound );
    }

    
    public DoubleStream doubles( final long streamSize )
    {
        return internalRand.doubles( streamSize );
    }

    
    public DoubleStream doubles( )
    {
        return internalRand.doubles();
    }

    
    public DoubleStream doubles( final long streamSize, final double randomNumberOrigin, final double randomNumberBound )
    {
        return internalRand.doubles( streamSize, randomNumberOrigin, randomNumberBound );
    }

    
    public DoubleStream doubles( final double randomNumberOrigin, final double randomNumberBound )
    {
        return internalRand.doubles( randomNumberOrigin, randomNumberBound );
    }

    public SecureRandom secureRandomInstance()
    {
        return SINGLETON.internalRand;
    }
}
