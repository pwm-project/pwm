/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

import password.pwm.util.java.Percent;
import password.pwm.util.java.TimeDuration;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.text.NumberFormat;
import java.time.Instant;

public class ProgressInfo implements Serializable
{
    private final Instant startTime;
    private final Instant nowTime;
    private final long totalItems;
    private final long nowItems;

    public ProgressInfo(
            final Instant startTime,
            final long totalItems,
            final long nowItems
    )
    {
        this.startTime = startTime;
        this.nowTime = Instant.now();
        this.totalItems = totalItems;
        this.nowItems = nowItems;
    }

    public Percent percentComplete( )
    {
        return new Percent( nowItems, totalItems );
    }

    public TimeDuration elapsed( )
    {
        return new TimeDuration( startTime, nowTime );
    }

    public long itemsRemaining( )
    {
        return totalItems - nowItems;
    }

    public float itemsPerMs( )
    {
        final long elapsedMs = elapsed().getTotalMilliseconds();
        if ( elapsedMs <= 0 )
        {
            return 0;
        }
        final BigDecimal itemsPerMs = new BigDecimal( nowItems ).divide( new BigDecimal( elapsedMs ), MathContext.DECIMAL32 );
        return itemsPerMs.floatValue();
    }

    public TimeDuration remainingDuration( )
    {
        final float itemsPerMs = itemsPerMs();
        if ( itemsPerMs <= 0 )
        {
            return TimeDuration.ZERO;
        }
        final BigDecimal remainingMs = new BigDecimal( itemsRemaining() ).divide( new BigDecimal( itemsPerMs ), MathContext.DECIMAL32 );
        return new TimeDuration( remainingMs.longValue() );
    }

    public Instant estimatedCompletion( )
    {
        final TimeDuration remainingDuration = remainingDuration();
        return Instant.ofEpochMilli( System.currentTimeMillis() + remainingDuration.getTotalMilliseconds() );
    }

    public String debugOutput( )
    {
        final TimeDuration remainingTime = remainingDuration();
        final NumberFormat numberFormat = NumberFormat.getNumberInstance();
        return "processed " + numberFormat.format( nowItems ) + " of " + numberFormat.format( totalItems )
                + " (" + percentComplete().pretty( 2 ) + ")"
                + ", remaining " + numberFormat.format( itemsRemaining() ) + " in " + remainingTime.asCompactString();
    }
}
