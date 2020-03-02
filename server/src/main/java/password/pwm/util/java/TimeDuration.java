/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

import com.novell.ldapchai.util.StringHelper;
import lombok.Value;
import password.pwm.PwmConstants;
import password.pwm.i18n.Display;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.secure.PwmRandom;
import password.pwm.util.secure.SecureService;

import javax.annotation.CheckReturnValue;
import javax.annotation.meta.When;
import java.io.Serializable;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * <p>An immutable class representing a time period.  The internal value of the time period is
 * stored as milliseconds.</p>
 *
 * <p>Negative time durations are not permitted.  Operations that would result in a negative value
 * are ignored and will instead result in a zero value.</p>
 *
 * @author Jason D. Rivard
 */
public class TimeDuration implements Comparable, Serializable
{
    public enum Unit
    {
        MILLISECONDS( ChronoUnit.MILLIS, TimeUnit.MILLISECONDS ),
        SECONDS( ChronoUnit.SECONDS, TimeUnit.SECONDS ),
        MINUTES( ChronoUnit.MINUTES, TimeUnit.MINUTES ),
        HOURS( ChronoUnit.HOURS, TimeUnit.HOURS ),
        DAYS( ChronoUnit.DAYS, TimeUnit.DAYS ),;

        private final TemporalUnit temporalUnit;
        private final TimeUnit timeUnit;

        Unit( final TemporalUnit temporalUnit, final TimeUnit timeUnit )
        {
            this.temporalUnit = temporalUnit;
            this.timeUnit = timeUnit;
        }

        public TemporalUnit getTemporalUnit( )
        {
            return temporalUnit;
        }

        public TimeUnit getTimeUnit( )
        {
            return timeUnit;
        }
    }

    public static final TimeDuration ZERO = new TimeDuration( 0 );
    public static final TimeDuration MILLISECOND = new TimeDuration( 1 );
    public static final TimeDuration MILLISECONDS_2 = new TimeDuration( 2 );
    public static final TimeDuration MILLISECONDS_3 = new TimeDuration( 3 );
    public static final TimeDuration SECOND = new TimeDuration( 1000 );
    public static final TimeDuration SECONDS_10 = new TimeDuration( 10 * 1000 );
    public static final TimeDuration SECONDS_30 = new TimeDuration( 30 * 1000 );
    public static final TimeDuration MINUTE = new TimeDuration( 60 * 1000 );
    public static final TimeDuration HOUR = new TimeDuration( 60 * 60 * 1000 );
    public static final TimeDuration DAY = new TimeDuration( 24 * 60 * 60 * 1000 );

    private final long ms;

    /**
     * Create a new TimeDuration using the specified duration, in milliseconds.
     *
     * @param durationMilliseconds a time period in milliseconds
     */
    private TimeDuration( final long durationMilliseconds )
    {
        this.ms = durationMilliseconds < 0 ? 0 : durationMilliseconds;
    }

    private static TimeDuration newTimeDuration( final long durationMilliseconds )
    {
        switch ( (int) durationMilliseconds )
        {
            case 0: return ZERO;
            case 1: return MILLISECOND;
            case 2: return MILLISECONDS_2;
            case 3: return MILLISECONDS_3;
            case 1000: return SECOND;
            case 10 * 1000: return SECONDS_10;
            case 30 * 1000: return SECONDS_30;
            case 60 * 1000: return MINUTE;
            case 60 * 60 * 1000: return HOUR;
            case 24 * 60 * 60 * 1000: return DAY;
            default: return new TimeDuration( durationMilliseconds );
        }
    }

    public static TimeDuration fromCurrent( final long ms )
    {
        return between( Instant.now(), Instant.ofEpochMilli( ms ) );
    }

    public static TimeDuration fromCurrent( final Instant instant )
    {
        return between( Instant.now(), instant );
    }

    public static String compactFromCurrent( final Instant instant )
    {
        return TimeDuration.fromCurrent( instant ).asCompactString();
    }

    public static String asCompactString( final long ms )
    {
        return newTimeDuration( ms ).asCompactString();
    }

    public long asMillis()
    {
        return ms;
    }

    public String asIso()
    {
        return this.asDuration().toString();
    }

    /**
     * Create a new TimeDuration using the absolute difference as the time
     * period between the two supplied timestamps.
     *
     * @param instant1 timestamp in Instant format
     * @param instant2 timestamp in Instant format
     */
    public static TimeDuration between( final Instant instant1, final Instant instant2 )
    {
        return newTimeDuration( Math.abs( instant1.toEpochMilli() - instant2.toEpochMilli() ) );
    }

    public boolean equals( final Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( !( o instanceof TimeDuration ) )
        {
            return false;
        }

        final TimeDuration timeDuration = ( TimeDuration ) o;

        return ms == timeDuration.ms;
    }

    public int hashCode( )
    {
        return ( int ) ( ms ^ ( ms >>> 32 ) );
    }

    public TimeDuration add( final TimeDuration duration )
    {
        return newTimeDuration( this.ms + duration.ms );
    }

    public Instant incrementFromInstant( final Instant input )
    {
        final long inputMillis = input.toEpochMilli();
        final long nextMills = inputMillis + this.ms;
        return Instant.ofEpochMilli( nextMills );
    }

    public long as( final Unit unit )
    {
        return unit.getTimeUnit().convert( ms, TimeUnit.MILLISECONDS );
    }

    public String asCompactString( )
    {
        final FractionalTimeDetail fractionalTimeDetail = new FractionalTimeDetail( ms );
        final StringBuilder sb = new StringBuilder();

        if ( this.equals( ZERO ) )
        {
            return "0ms";
        }

        if ( this.equals( DAY ) || this.isLongerThan( DAY ) )
        {
            sb.append( fractionalTimeDetail.getDays() );
            sb.append( "d" );
        }

        if ( this.equals( HOUR ) || this.isLongerThan( HOUR ) )
        {
            if ( fractionalTimeDetail.getHours() != 0 && fractionalTimeDetail.getHours() != 24 )
            {
                if ( sb.length() > 0 )
                {
                    sb.append( ":" );
                }
                sb.append( fractionalTimeDetail.getHours() );
                sb.append( "h" );
            }
        }

        if ( this.equals( MINUTE ) || this.isLongerThan( MINUTE ) )
        {
            if ( fractionalTimeDetail.getMinutes() != 0 )
            {
                if ( sb.length() > 0 )
                {
                    sb.append( ":" );
                }
                sb.append( fractionalTimeDetail.getMinutes() );
                sb.append( "m" );
            }
        }

        if ( this.isShorterThan( 1000 * 60 * 10 ) && this.isLongerThan( 1000 * 3 ) )
        {
            // 10 minutes to 3 seconds
            if ( fractionalTimeDetail.getSeconds() != 0 )
            {
                if ( sb.length() > 0 )
                {
                    sb.append( ":" );
                }
                sb.append( fractionalTimeDetail.getSeconds() );
                sb.append( "s" );
            }
        }

        if ( this.isShorterThan( 1000 * 3 ) )
        {
            // 3 seconds
            if ( ms != 0 )
            {
                if ( sb.length() > 0 )
                {
                    sb.append( ":" );
                }
                sb.append( ms );
                sb.append( "ms" );
            }
        }

        if ( sb.length() == 0 )
        {
            sb.append( 0 );
            sb.append( "s" );
        }

        return sb.toString();
    }

    public boolean isLongerThan( final TimeDuration duration )
    {
        return this.compareTo( duration ) > 0;
    }

    public int compareTo( final Object o )
    {
        final TimeDuration td = ( TimeDuration ) o;
        final long otherMS = td.as( Unit.MILLISECONDS );
        return Long.compare( ms, otherMS );
    }

    public boolean isLongerThan( final long durationMS )
    {
        return this.isLongerThan( newTimeDuration( durationMS ) );
    }

    public boolean isLongerThan( final long duration, final Unit timeUnit )
    {
        return this.isLongerThan( TimeDuration.of( duration, timeUnit ) );
    }

    public boolean isShorterThan( final long duration, final Unit timeUnit )
    {
        return this.isShorterThan( TimeDuration.of( duration, timeUnit ) );
    }

    public boolean isShorterThan( final long durationMS )
    {
        return this.isShorterThan( newTimeDuration( durationMS ) );
    }

    public String asLongString( )
    {
        return asLongString( PwmConstants.DEFAULT_LOCALE );
    }

    public String asLongString( final Locale locale )
    {
        final FractionalTimeDetail fractionalTimeDetail = new FractionalTimeDetail( ms );
        final List<String> segments = new ArrayList<>();

        //output number of days
        if ( fractionalTimeDetail.days > 0 )
        {
            segments.add( fractionalTimeDetail.days
                    + " "
                    + ( fractionalTimeDetail.days == 1
                    ? LocaleHelper.getLocalizedMessage( locale, Display.Display_Day, null )
                    : LocaleHelper.getLocalizedMessage( locale, Display.Display_Days, null ) )
            );
        }

        //output number of hours
        if ( fractionalTimeDetail.hours > 0 )
        {
            segments.add( fractionalTimeDetail.hours
                    + " "
                    + ( fractionalTimeDetail.hours == 1
                    ? LocaleHelper.getLocalizedMessage( locale, Display.Display_Hour, null )
                    : LocaleHelper.getLocalizedMessage( locale, Display.Display_Hours, null ) )
            );
        }

        //output number of minutes
        if ( fractionalTimeDetail.minutes > 0 )
        {
            segments.add( fractionalTimeDetail.minutes
                    + " "
                    + ( fractionalTimeDetail.minutes == 1
                    ? LocaleHelper.getLocalizedMessage( locale, Display.Display_Minute, null )
                    : LocaleHelper.getLocalizedMessage( locale, Display.Display_Minutes, null ) )
            );
        }

        //seconds & ms
        if ( fractionalTimeDetail.seconds > 0 || segments.isEmpty() )
        {
            final StringBuilder sb = new StringBuilder();
            if ( sb.length() == 0 )
            {
                if ( ms < 5000 )
                {
                    final BigDecimal msDecimal = new BigDecimal( ms ).movePointLeft( 3 );

                    final DecimalFormat formatter;

                    if ( ms > 2000 )
                    {
                        formatter = new DecimalFormat( "#.#" );
                    }
                    else if ( ms > 1000 )
                    {
                        formatter = new DecimalFormat( "#.##" );
                    }
                    else
                    {
                        formatter = new DecimalFormat( "#.###" );
                    }

                    sb.append( formatter.format( msDecimal ) );
                }
                else
                {
                    sb.append( fractionalTimeDetail.seconds );
                }
            }
            else
            {
                sb.append( fractionalTimeDetail.seconds );
            }
            sb.append( " " );
            sb.append( ms == 1000
                    ? LocaleHelper.getLocalizedMessage( locale, Display.Display_Second, null )
                    : LocaleHelper.getLocalizedMessage( locale, Display.Display_Seconds, null )
            );
            segments.add( sb.toString() );
        }

        return StringHelper.stringCollectionToString( segments, ", " );
    }

    public Instant getInstantAfter( final Instant specifiedDate )
    {
        return specifiedDate.minusMillis( ms );
    }

    public Instant getInstantAfterNow( )
    {
        return Instant.now().minusMillis( ms );
    }


    public boolean isShorterThan( final TimeDuration duration )
    {
        return this.compareTo( duration ) < 0;
    }

    public TimeDuration subtract( final TimeDuration duration )
    {
        return newTimeDuration( Math.abs( ms - duration.ms ) );
    }

    @Override
    public String toString( )
    {
        return "TimeDuration[" + this.asCompactString() + "]";
    }


    /**
     * Pause the calling thread the specified amount of time.
     *
     * @return time actually spent sleeping
     */
    @CheckReturnValue( when = When.NEVER )
    public TimeDuration pause( )
    {
        return pause( this, () -> false );
    }

    /**
     * Pause the calling thread the specified amount of time.
     *
     * @return time actually spent sleeping
     */
    @CheckReturnValue( when = When.NEVER )
    public TimeDuration jitterPause( final SecureService secureService, final float factor )
    {
        final PwmRandom pwmRandom = secureService.pwmRandom();
        final long jitterMs = (long) ( this.ms * factor );
        final long deviation = pwmRandom.nextBoolean() ? jitterMs + this.ms : jitterMs - this.ms;
        return pause( TimeDuration.of( deviation, Unit.MILLISECONDS ), () -> false );
    }

    @CheckReturnValue( when = When.NEVER )
    public TimeDuration pause(
            final BooleanSupplier interruptBoolean
    )
    {
        final long interruptMs = this.asMillis() / 100;
        return pause( TimeDuration.of( interruptMs, Unit.MILLISECONDS ), interruptBoolean );
    }

    @CheckReturnValue( when = When.NEVER )
    public TimeDuration pause(
            final TimeDuration interruptCheckInterval,
            final BooleanSupplier interruptBoolean
    )
    {
        final long startTime = System.currentTimeMillis();
        final long pauseTime = JavaHelper.rangeCheck( 5, 1000, interruptCheckInterval.asMillis() );

        while ( ( System.currentTimeMillis() - startTime ) < this.asMillis() && !interruptBoolean.getAsBoolean() )
        {
            try
            {
                Thread.sleep( pauseTime );
            }
            catch ( final InterruptedException e )
            {
                // ignore
            }
        }

        return TimeDuration.fromCurrent( startTime );
    }

    public Duration asDuration()
    {
        return Duration.of( this.ms, ChronoUnit.MILLIS );
    }

    public static TimeDuration fromDuration( final Duration duration )
    {
        return newTimeDuration( duration.toMillis() );
    }

    public static TimeDuration of ( final long value, final Unit timeUnit )
    {
        return fromDuration( Duration.of( value, timeUnit.getTemporalUnit() ) );
    }

    @Value
    private static class FractionalTimeDetail implements Serializable
    {
        private final long milliseconds;
        private final long seconds;
        private final long minutes;
        private final long hours;
        private final long days;

        FractionalTimeDetail( final long duration )
        {
            final long totalSeconds = new BigDecimal( duration ).movePointLeft( 3 ).longValue();
            milliseconds = duration % 1000;
            seconds = ( totalSeconds ) % 60;
            minutes = ( totalSeconds / 60 ) % 60;
            hours = ( ( totalSeconds / 60 ) / 60 ) % 24;
            days = ( ( ( totalSeconds / 60 ) / 60 ) / 24 );
        }
    }

    public boolean isZero()
    {
        return ms <= 0;
    }
}

