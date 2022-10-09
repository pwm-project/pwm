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

package password.pwm.util.java;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class AverageTracker
{
    private final int maxSamples;
    private final AtomicLongArray samples;
    private final AtomicInteger index = new AtomicInteger();
    private final AtomicInteger top = new AtomicInteger();

    private final transient ReadWriteLock lock = new ReentrantReadWriteLock();

    public AverageTracker( final int maxSamples )
    {
        this.maxSamples = maxSamples - 1;
        this.samples = new AtomicLongArray( maxSamples );
    }

    public void addSample( final long input )
    {
        lock.writeLock().lock();
        try
        {
            samples.set( index.get(), input );
            index.updateAndGet( current -> current >= maxSamples ? 0 : current + 1 );
            top.updateAndGet( current -> current >= maxSamples ? maxSamples : current + 1 );
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    public BigDecimal avg( )
    {
        lock.readLock().lock();
        try
        {
            if ( top.get() == 0 )
            {
                return BigDecimal.ZERO;
            }

            return primitiveSum();
        }
        catch ( final ArithmeticException e )
        {
            return bigSum();
        }
        finally
        {
            lock.readLock().unlock();
        }

    }

    public long avgAsLong( )
    {
        return avg().longValue();
    }

    private BigDecimal primitiveSum()
            throws ArithmeticException
    {
        long total = 0;
        for ( int i = 0; i <= top.get(); i++ )
        {
            // math add exact throws exception on overflow
            total = Math.addExact( total, samples.get( i ) );
        }
        return calcAvg( BigDecimal.valueOf( total ) );
    }

    private BigDecimal bigSum()
    {
        BigInteger total = BigInteger.ZERO;
        for ( int i = 0; i <= top.get(); i++ )
        {
            total = total.add( BigInteger.valueOf( samples.get( i ) ) );
        }

        return calcAvg( new BigDecimal( total ) );
    }

    private BigDecimal calcAvg( final BigDecimal total )
    {
        final BigDecimal sampleSize = new BigDecimal( top.get() + 1 );
        return total.divide( sampleSize, MathContext.DECIMAL128 );
    }
}
