/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

package password.pwm.util.stats;

import password.pwm.util.TimeDuration;

import java.io.Serializable;
import java.math.BigDecimal;

public class EventRateMeter implements Serializable {
    private MovingAverage movingAverage;
    private double remainder = 0;

    public EventRateMeter(final TimeDuration maxDuration) {
        if (maxDuration == null) {
            throw new NullPointerException("maxDuration cannot be null");
        }
        movingAverage = new MovingAverage(maxDuration.getTotalMilliseconds());
    }

    public synchronized void markEvents(final int eventCount) {
        long timeSinceLastUpdate = System.currentTimeMillis() - movingAverage.getLastMillis();
        if (timeSinceLastUpdate != 0) {
            double eventRate  = (eventCount + remainder) / timeSinceLastUpdate;
            movingAverage.update(eventRate * 1000);
            remainder = 0;
        } else {
            remainder += eventCount;
        }
    }

    public BigDecimal readEventRate() {
        return new BigDecimal(this.movingAverage.getAverage());
    }

    /** MovingAverage.java
     *
     * Copyright 2009-2010 Comcast Interactive Media, LLC.
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *      http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     *
     *
     *  This class implements an exponential moving average, using the
     *  algorithm described at <a href="http://en.wikipedia.org/wiki/Moving_average">http://en.wikipedia.org/wiki/Moving_average</a>. The average does not
     *  sample itself; it merely computes the new average when updated with
     *  a sample by an external mechanism.
     **/
    private static class MovingAverage implements Serializable {
        private long windowMillis;
        private long lastMillis;
        private double average;

        /** Construct a {@link MovingAverage}, providing the time window
         *  we want the average over. For example, providing a value of
         *  3,600,000 provides a moving average over the last hour.
         *  @param windowMillis the length of the sliding window in
         *    milliseconds */
        public MovingAverage(long windowMillis) {
            this.windowMillis = windowMillis;
        }

        /** Updates the average with the latest measurement.
         *  @param sample the latest measurement in the rolling average */
        public synchronized void update(double sample) {
            long now = System.currentTimeMillis();

            if (lastMillis == 0) {  // first sample
                average = sample;
                lastMillis = now;
                return;
            }
            long deltaTime = now - lastMillis;
            double coeff = Math.exp(-1.0 * ((double)deltaTime / windowMillis));
            average = (1.0 - coeff) * sample + coeff * average;

            lastMillis = now;
        }

        /** Returns the last computed average value. */
        public double getAverage() {
            update(0);
            return average;
        }

        public long getLastMillis() {
            return lastMillis;
        }
    }
}
