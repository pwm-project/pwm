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

package password.pwm.util.java;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class AverageTracker
{
    private final int maxSamples;
    private final Queue<BigInteger> samples = new ArrayDeque<>();

    private final transient ReadWriteLock lock = new ReentrantReadWriteLock();

    public AverageTracker( final int maxSamples )
    {
        this.maxSamples = maxSamples;
    }

    public void addSample( final long input )
    {
        lock.writeLock().lock();
        try
        {
            samples.add( BigInteger.valueOf( input ) );
            while ( samples.size() > maxSamples )
            {
                samples.remove();
            }
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
            if ( samples.isEmpty() )
            {
                return BigDecimal.ZERO;
            }

            final BigInteger total = samples.stream().reduce( BigInteger::add ).get();
            final BigDecimal sampleSize = new BigDecimal( samples.size() );
            return new BigDecimal( total ).divide( sampleSize, MathContext.DECIMAL128 );
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
}
