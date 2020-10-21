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

package password.pwm.util;

import password.pwm.util.java.MovingAverage;
import password.pwm.util.java.TimeDuration;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class EventRateMeter implements Serializable
{
    private final TimeDuration maxDuration;
    private final Lock lock = new ReentrantLock();

    private MovingAverage movingAverage;
    private double remainder;

    public EventRateMeter( final TimeDuration maxDuration )
    {
        if ( maxDuration == null )
        {
            throw new NullPointerException( "maxDuration cannot be null" );
        }
        this.maxDuration = maxDuration;
        reset();
    }

    public void reset( )
    {
        lock.lock();
        try
        {
            movingAverage = new MovingAverage( maxDuration.asMillis() );
            remainder = 0;
        }
        finally
        {
            lock.unlock();
        }
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

    public BigDecimal readEventRate( )
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
