/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.text.NumberFormat;
import java.util.Date;

public class ProgressInfo implements Serializable {
    private final Date startTime;
    private final Date nowTime;
    private final long totalItems;
    private final long nowItems;

    public ProgressInfo(
            final Date startTime,
            final long totalItems,
            final long nowItems
    )
    {
        this.startTime = startTime;
        this.nowTime = new Date();
        this.totalItems = totalItems;
        this.nowItems = nowItems;
    }

    public Percent percentComplete() {
        return new Percent(nowItems,totalItems);
    }

    public TimeDuration elapsed() {
        return new TimeDuration(startTime, nowTime);
    }

    public long itemsRemaining() {
        return totalItems - nowItems;
    }

    public float itemsPerMs() {
        final long elapsedMs = elapsed().getTotalMilliseconds();
        if (elapsedMs <= 0) {
            return 0;
        }
        final BigDecimal itemsPerMs = new BigDecimal(nowItems).divide(new BigDecimal(elapsedMs),MathContext.DECIMAL32);
        return itemsPerMs.floatValue();
    }

    public TimeDuration remainingDuration() {
        final float itemsPerMs = itemsPerMs();
        if (itemsPerMs <= 0) {
            return TimeDuration.ZERO;
        }
        final BigDecimal remainingMs = new BigDecimal(itemsRemaining()).divide(new BigDecimal(itemsPerMs),MathContext.DECIMAL32);
        return new TimeDuration(remainingMs.longValue());
    }

    public Date estimatedCompletion() {
        final TimeDuration remainingDuration = remainingDuration();
        return new Date(System.currentTimeMillis() + remainingDuration.getTotalMilliseconds());
    }

    public String debugOutput() {
        final TimeDuration remainingTime = remainingDuration();
        final NumberFormat numberFormat = NumberFormat.getNumberInstance();
        return "processed " + numberFormat.format(nowItems) + " of " + numberFormat.format(totalItems)
                + " (" + percentComplete().pretty(2) + ")"
                + ", remaining " + numberFormat.format(itemsRemaining()) + " in " + remainingTime.asCompactString();
    }
}
