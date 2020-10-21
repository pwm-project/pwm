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

import java.io.Serializable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>MovingAverage.java</p>
 *
 * <p>Copyright 2009-2010 Comcast Interactive Media, LLC.</p>
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at</p>
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0</p>
 *
 * <p>Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.</p>
 *
 * <p>This class implements an exponential moving average, using the
 * algorithm described at <a href="http://en.wikipedia.org/wiki/Moving_average">http://en.wikipedia.org/wiki/Moving_average</a>. The average does not
 * sample itself; it merely computes the new average when updated with
 * a sample by an external mechanism.</p>
 **/
public class MovingAverage implements Serializable
{
    private long windowMillis;
    private long lastMillis;
    private double average;

    private final Lock lock = new ReentrantLock();

    /**
     * Construct a {@link MovingAverage}, providing the time window
     * we want the average over. For example, providing a value of
     * 3,600,000 provides a moving average over the last hour.
     *
     * @param windowMillis the length of the sliding window in
     *                     milliseconds
     */
    public MovingAverage( final long windowMillis )
    {
        this.windowMillis = windowMillis;
    }

    public MovingAverage( final TimeDuration timeDuration )
    {
        this.windowMillis = timeDuration.asMillis();
    }

    /**
     * Updates the average with the latest measurement.
     *
     * @param sample the latest measurement in the rolling average
     */
    public void update( final double sample )
    {
        lock.lock();
        try
        {
            final long now = System.currentTimeMillis();

            if ( lastMillis == 0 )
            {
                // first sample
                average = sample;
                lastMillis = now;
                return;
            }
            final long deltaTime = now - lastMillis;
            final double coeff = Math.exp( -1.0 * ( ( double ) deltaTime / windowMillis ) );
            average = ( 1.0 - coeff ) * sample + coeff * average;

            lastMillis = now;
        }
        finally
        {
            lock.unlock();
        }
    }

    /**
     * Returns the last computed average value.
     *
     * @return current average value
     */
    public double getAverage( )
    {
        update( 0 );
        return average;
    }

    public long getLastMillis( )
    {
        return lastMillis;
    }
}
