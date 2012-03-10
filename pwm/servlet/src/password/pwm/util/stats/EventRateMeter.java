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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

public class EventRateMeter {
    final ConcurrentLinkedQueue<EventElement> tracker = new ConcurrentLinkedQueue<EventElement>();
    private final TimeDuration maxDuration;

    public EventRateMeter(final TimeDuration maxDuration) {
        if (maxDuration == null) {
            throw new NullPointerException("maxDuration cannot be null");
        }
        this.maxDuration = maxDuration;
    }

    public void markEvents(final int eventCount) {
        tracker.add(new EventElement(eventCount,new Date()));
        clean();
    }

    public BigDecimal readEventRate(final TimeDuration duration, final TimeDuration unit) {
        if (duration == null || duration.isLongerThan(maxDuration)) {
            throw new IllegalArgumentException("invalid duration (must be less than " + maxDuration.getTotalMilliseconds()+ "ms)");
        }

        long total = 0;
        for (final EventElement element : tracker) {
            final TimeDuration loopDuration = TimeDuration.fromCurrent(element.timestamp);
            if (duration.isLongerThan(loopDuration)) {
                total += element.eventCount;
            }
        }
        if (unit != null && unit.getTotalMilliseconds() > 0) {
            final int precision = 1;
            final BigDecimal numerator = (new BigDecimal(duration.getTotalMilliseconds())).divide(new BigDecimal(unit.getTotalMilliseconds()), precision, RoundingMode.HALF_UP);
            return (new BigDecimal(total)).divide(numerator, precision, RoundingMode.HALF_UP);
        } else {
            return new BigDecimal(total);
        }
    }

    private void clean() {
        for (Iterator<EventElement> iterator = tracker.iterator(); iterator.hasNext(); ) {
            final EventElement element = iterator.next();
            if (maxDuration.isShorterThan(TimeDuration.fromCurrent(element.timestamp))) {
                iterator.remove();
            }
        }
    }

    private static class EventElement {
        private final int eventCount;
        private final Date timestamp;

        private EventElement(final int eventCount, final Date timestamp) {
            this.eventCount = eventCount;
            this.timestamp = timestamp;
        }
    }

}
