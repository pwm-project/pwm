/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

package password.pwm.util;

import password.pwm.Helper;

/**
 * Sleep for a percentage of time.  The percentage is determined by the
 * loadFactor, which should be a value from 0-100.  The loadFactor is applied as
 * a percentage of total time that should be spent sleeping.
 *
 * @author Jason D. Rivard
 */
public class Sleeper {
// ------------------------------ FIELDS ------------------------------

    private static final long MAX_SLEEP_TIME = 500;
    private static final long STANDARD_SLEEP_TIME = 20;

    private final boolean doSleep;
    private final int loadFactor;

    private long startTime = System.currentTimeMillis();
    private long sleepTime = 0;

// --------------------------- CONSTRUCTORS ---------------------------

    public Sleeper(final int loadFactor)
    {
        this.loadFactor = loadFactor >= 0 ? loadFactor : 0;

        doSleep = loadFactor > 0;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public int getLoadFactor()
    {
        return loadFactor;
    }

// -------------------------- OTHER METHODS --------------------------

    public void reset() {
        startTime = System.currentTimeMillis();
        sleepTime = 0;
    }

    public void sleep()
    {
        if (!doSleep) {
            return;
        }

        final long totalRunTime = System.currentTimeMillis() - startTime;
        final float factor = loadFactor / 100f;
        final long desiredTotalSleepTime = (long)(totalRunTime * factor);

        final long beginSleepTime = System.currentTimeMillis();
        while (sleepTime < desiredTotalSleepTime) {
            sleepTime += Helper.pause(STANDARD_SLEEP_TIME);

            final long currentSleepTime = System.currentTimeMillis() - beginSleepTime;
            if (currentSleepTime > MAX_SLEEP_TIME ) {
                return;
            }
        }
    }
}
