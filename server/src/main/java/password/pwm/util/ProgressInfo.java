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
        return TimeDuration.between( startTime, nowTime );
    }

    public long itemsRemaining( )
    {
        return totalItems - nowItems;
    }

    public float itemsPerMs( )
    {
        final long elapsedMs = elapsed().asMillis();
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
        return TimeDuration.of( remainingMs.longValue(), TimeDuration.Unit.MILLISECONDS );
    }

    public Instant estimatedCompletion( )
    {
        final TimeDuration remainingDuration = remainingDuration();
        return Instant.ofEpochMilli( System.currentTimeMillis() + remainingDuration.asMillis() );
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
