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

package password.pwm.util;

import password.pwm.util.java.PwmNumberFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class EventRateMeter implements Serializable
{
    private final long maxDuration;
    private final Lock lock = new ReentrantLock();

    private volatile MovingAverage movingAverage;
    private volatile double remainder;

    public EventRateMeter( final Duration maxDuration )
    {
        this.maxDuration = Objects.requireNonNull( maxDuration ) .toMillis();
        reset();
    }

    public void reset( )
    {
        lock.lock();
        try
        {
            movingAverage = new MovingAverage( Duration.ofMillis( maxDuration ) );
            remainder = 0;
        }
        finally
        {
            lock.unlock();
        }
    }

    public void markEvent()
    {
        markEvents( 1 );
    }

    public void markEvents( final int eventCount )
    {
        lock.lock();
        try
        {
            final long timeSinceLastUpdate = System.currentTimeMillis() - movingAverage.getLastMillis();
            if ( timeSinceLastUpdate != 0 )
            {
                final double eventRate = ( eventCount + remainder ) / timeSinceLastUpdate;
                movingAverage.update( eventRate * 1000 );
                remainder = 0;
            }
            else
            {
                remainder += eventCount;
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public String prettyEps( final Locale locale )
    {
        return PwmNumberFormat.prettyBigDecimal( rawEps(), 3, locale );
    }

    public BigDecimal rawEps( )
    {
        lock.lock();
        try
        {
            return BigDecimal.valueOf( this.movingAverage.getAverage() );
        }
        finally
        {
            lock.unlock();
        }
    }

}
