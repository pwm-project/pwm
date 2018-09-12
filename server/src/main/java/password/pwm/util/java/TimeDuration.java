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

package password.pwm.util.java;

import com.novell.ldapchai.util.StringHelper;
import password.pwm.PwmConstants;
import password.pwm.i18n.Display;
import password.pwm.util.LocaleHelper;

import javax.annotation.CheckReturnValue;
import javax.annotation.meta.When;
import java.io.Serializable;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * <p>An immutable class representing a time period.  The internal value of the time period is
 * stored as milliseconds.</p>
 *
 * <p>Negative time durations are not permitted.  Operations that would result in a negative value
 * are negated and will instead result in positive values.</p>
 *
 * @author Jason D. Rivard
 */
public class TimeDuration implements Comparable, Serializable
{
    public static final TimeDuration ZERO = new TimeDuration( 0 );
    public static final TimeDuration MILLISECOND = new TimeDuration( 1, TimeUnit.MILLISECONDS );
    public static final TimeDuration SECOND = new TimeDuration( 1, TimeUnit.SECONDS );
    public static final TimeDuration SECONDS_10 = new TimeDuration( 10, TimeUnit.SECONDS );
    public static final TimeDuration SECONDS_30 = new TimeDuration( 30, TimeUnit.SECONDS );
    public static final TimeDuration MINUTE = new TimeDuration( 1, TimeUnit.MINUTES );
    public static final TimeDuration HOUR = new TimeDuration( 1, TimeUnit.HOURS );
    public static final TimeDuration DAY = new TimeDuration( 1, TimeUnit.DAYS );

    private final long ms;
    private transient TimeDetail cachedTimeDetail;

    /**
     * Create a new TimeDuration using the specified duration, in milliseconds.
     *
     * @param durationMilliseconds a time period in milliseconds
     */
    public TimeDuration( final long durationMilliseconds )
    {
        if ( durationMilliseconds < 0 )
        {
            this.ms = 0;
        }
        else
        {
            this.ms = durationMilliseconds;
        }
    }

    public TimeDuration( final long duration, final TimeUnit timeUnit )
    {
        this( timeUnit.toMillis( duration ) );
    }

    public static TimeDuration fromCurrent( final long ms )
    {
        return new TimeDuration( System.currentTimeMillis(), ms );
    }

    public static TimeDuration fromCurrent( final Date date )
    {
        return new TimeDuration( System.currentTimeMillis(), date.getTime() );
    }

    public static TimeDuration fromCurrent( final Instant instant )
    {
        return new TimeDuration( System.currentTimeMillis(), instant.toEpochMilli() );
    }

    public static TimeDuration between( final Instant start, final Instant finish )
    {
        return new TimeDuration( start, finish );
    }

    public static String compactFromCurrent( final Instant instant )
    {
        return TimeDuration.fromCurrent( instant ).asCompactString();
    }

    public static String asCompactString( final long ms )
    {
        return new TimeDuration( ms ).asCompactString();
    }


    /**
     * Create a new TimeDuration using the absolute difference as the time
     * period between the two supplied timestamps.
     *
     * @param date         timestamp in Date format
     * @param milliseconds timestamp in ms
     */
    public TimeDuration( final Date date, final long milliseconds )
    {
        this( date.getTime(), milliseconds );
    }

    /**
     * Create a new TimeDuration using the absolute difference as the time
     * period between the two supplied timestamps.
     *
     * @param date         timestamp in Date format
     * @param milliseconds timestamp in ms
     */
    public TimeDuration( final long milliseconds, final Date date )
    {
        this( milliseconds, date.getTime() );
    }

    /**
     * Create a new TimeDuration using the absolute difference as the time
     * period between the two supplied timestamps.
     *
     * @param instant1 timestamp in Instant format
     * @param instant2 timestamp in Instant format
     */
    public TimeDuration( final Instant instant1, final Instant instant2 )
    {
        this( instant1.toEpochMilli(), instant2.toEpochMilli() );
    }

    /**
     * Create a new TimeDuration using the absolute difference as the time
     * period between the two supplied timestamps.
     *
     * @param date1 timestamp in Date format
     * @param date2 timestamp in Date format
     */
    public TimeDuration( final Date date1, final Date date2 )
    {
        this( date1.getTime(), date2.getTime() );
    }

    /**
     * Create a new TimeDuration using the absolute difference as the time
     * period between the two supplied timestamps.
     *
     * @param milliseconds1 timestamp in ms
     * @param milliseconds2 timestamp in ms
     */
    public TimeDuration( final long milliseconds1, final long milliseconds2 )
    {
        this( Math.abs( milliseconds1 - milliseconds2 ) );
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
        return new TimeDuration( this.getTotalMilliseconds() + duration.getTotalMilliseconds() );
    }

    public Instant incrementFromInstant( final Instant input )
    {
        final long inputMillis = input.toEpochMilli();
        final long nextMills = inputMillis + this.getTotalMilliseconds();
        return Instant.ofEpochMilli( nextMills );
    }

    public long getTotalMilliseconds( )
    {
        return ms;
    }

    public long getTotalSeconds( )
    {
        return ms / 1000;
    }

    public long getTotalMinutes( )
    {
        return ms / ( 60 * 1000 );
    }

    public long getTotalDays( )
    {
        return ms / ( 60 * 1000 * 60 * 24 );
    }

    public String asCompactString( )
    {
        final StringBuilder sb = new StringBuilder();

        if ( this.equals( ZERO ) )
        {
            return "0ms";
        }

        if ( this.equals( DAY ) || this.isLongerThan( DAY ) )
        {
            sb.append( this.getDays() );
            sb.append( "d" );
        }

        if ( this.equals( HOUR ) || this.isLongerThan( HOUR ) )
        {
            if ( getHours() != 0 && getHours() != 24 )
            {
                if ( sb.length() > 0 )
                {
                    sb.append( ":" );
                }
                sb.append( getHours() );
                sb.append( "h" );
            }
        }

        if ( this.equals( MINUTE ) || this.isLongerThan( MINUTE ) )
        {
            if ( getMinutes() != 0 )
            {
                if ( sb.length() > 0 )
                {
                    sb.append( ":" );
                }
                sb.append( getMinutes() );
                sb.append( "m" );
            }
        }

        if ( this.isShorterThan( 1000 * 60 * 10 ) && this.isLongerThan( 1000 * 3 ) )
        {
            // 10 minutes to 3 seconds
            if ( getSeconds() != 0 )
            {
                if ( sb.length() > 0 )
                {
                    sb.append( ":" );
                }
                sb.append( getSeconds() );
                sb.append( "s" );
            }
        }

        if ( this.isShorterThan( 1000 * 3 ) )
        {
            // 3 seconds
            if ( getMilliseconds() != 0 )
            {
                if ( sb.length() > 0 )
                {
                    sb.append( ":" );
                }
                sb.append( getMilliseconds() );
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

    public long getDays( )
    {
        return getTimeDetail().days;
    }

    private TimeDetail getTimeDetail( )
    {
        // lazy init, but not sync'd, no big deal if dupes created
        if ( cachedTimeDetail == null )
        {
            cachedTimeDetail = new TimeDetail( ms );
        }
        return cachedTimeDetail;
    }

    public long getHours( )
    {
        return getTimeDetail().hours;
    }

    public boolean isLongerThan( final TimeDuration duration )
    {
        return this.compareTo( duration ) > 0;
    }

    public int compareTo( final Object o )
    {
        final TimeDuration td = ( TimeDuration ) o;
        final long otherMS = td.getTotalMilliseconds();
        return ( ms == otherMS ? 0 : ( ms < otherMS ? -1 : 1 ) );
    }

    public long getMinutes( )
    {
        return getTimeDetail().minutes;
    }

    public boolean isLongerThan( final long durationMS )
    {
        return this.isLongerThan( new TimeDuration( durationMS ) );
    }

    public boolean isLongerThan( final long duration, final TimeUnit timeUnit )
    {
        return this.isLongerThan( timeUnit.toMillis( duration ) );
    }

    public boolean isShorterThan( final long duration, final TimeUnit timeUnit )
    {
        return this.isShorterThan( timeUnit.toMillis( duration ) );
    }

    public long getSeconds( )
    {
        return getTimeDetail().seconds;
    }

    public boolean isShorterThan( final long durationMS )
    {
        return this.isShorterThan( new TimeDuration( durationMS ) );
    }

    public long getMilliseconds( )
    {
        return ms;
    }

    public String asLongString( )
    {
        return asLongString( PwmConstants.DEFAULT_LOCALE );
    }

    public String asLongString( final Locale locale )
    {
        final TimeDetail timeDetail = getTimeDetail();
        final List<String> segments = new ArrayList<>();

        //output number of days
        if ( timeDetail.days > 0 )
        {
            final StringBuilder sb = new StringBuilder();
            sb.append( timeDetail.days );
            sb.append( " " );
            sb.append( timeDetail.days == 1
                    ? LocaleHelper.getLocalizedMessage( locale, Display.Display_Day, null )
                    : LocaleHelper.getLocalizedMessage( locale, Display.Display_Days, null ) );
            segments.add( sb.toString() );
        }

        //output number of hours
        if ( timeDetail.hours > 0 )
        {
            final StringBuilder sb = new StringBuilder();
            sb.append( timeDetail.hours );
            sb.append( " " );
            sb.append( timeDetail.hours == 1
                    ? LocaleHelper.getLocalizedMessage( locale, Display.Display_Hour, null )
                    : LocaleHelper.getLocalizedMessage( locale, Display.Display_Hours, null ) );
            segments.add( sb.toString() );
        }

        //output number of minutes
        if ( timeDetail.minutes > 0 )
        {
            final StringBuilder sb = new StringBuilder();
            sb.append( timeDetail.minutes );
            sb.append( " " );
            sb.append( timeDetail.minutes == 1
                    ? LocaleHelper.getLocalizedMessage( locale, Display.Display_Minute, null )
                    : LocaleHelper.getLocalizedMessage( locale, Display.Display_Minutes, null ) );
            segments.add( sb.toString() );
        }

        //seconds & ms
        if ( timeDetail.seconds > 0 || segments.isEmpty() )
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
                    sb.append( timeDetail.seconds );
                }
            }
            else
            {
                sb.append( timeDetail.seconds );
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

    public Date getDateAfterNow( )
    {
        return this.getDateAfter( new Date( System.currentTimeMillis() ) );
    }

    public Date getDateAfter( final Date specifiedDate )
    {
        return new Date( specifiedDate.getTime() + ms );
    }

    public Instant getInstantAfter( final Instant specifiedDate )
    {
        return specifiedDate.minusMillis( ms );
    }

    public Instant getInstantAfterNow( )
    {
        return Instant.now().minusMillis( ms );
    }

    public Date getDateBeforeNow( )
    {
        return this.getDateBefore( new Date( System.currentTimeMillis() ) );
    }

    public Date getDateBefore( final Date specifiedDate )
    {
        return new Date( specifiedDate.getTime() - ms );
    }

    public boolean isShorterThan( final TimeDuration duration )
    {
        return this.compareTo( duration ) < 0;
    }

    public TimeDuration subtract( final TimeDuration duration )
    {
        return new TimeDuration( Math.abs( this.getTotalMilliseconds() - duration.getTotalMilliseconds() ) );
    }

    @Override
    public String toString( )
    {
        return "TimeDuration[" + this.asCompactString() + "]";
    }

    /**
     * Pause the calling thread the specified amount of time.
     *
     * @param sleepTimeMS - a time duration in milliseconds
     * @return time actually spent sleeping
     */
    public static TimeDuration pause( final long sleepTimeMS )
    {
        if ( sleepTimeMS < 1 )
        {
            return TimeDuration.ZERO;
        }

        final long startTime = System.currentTimeMillis();
        do
        {
            try
            {
                final long sleepTime = sleepTimeMS - ( System.currentTimeMillis() - startTime );
                Thread.sleep( sleepTime > 0 ? sleepTime : 5 );
            }
            catch ( InterruptedException e )
            {
                //who cares
            }
        }
        while ( ( System.currentTimeMillis() - startTime ) < sleepTimeMS );

        return TimeDuration.fromCurrent( startTime );
    }

    /**
     * Pause the calling thread the specified amount of time.
     *
     * @return time actually spent sleeping
     */
    @CheckReturnValue( when = When.NEVER )
    public TimeDuration pause( )
    {
        return pause( this.getTotalMilliseconds() );
    }

    public Duration asDuration()
    {
        return Duration.of( this.ms, ChronoUnit.MILLIS );
    }

    public static TimeDuration fromDuration( final Duration duration )
    {
        return new TimeDuration( duration.get( ChronoUnit.MILLIS ) );
    }

    public static TimeDuration of ( final long value, final TimeUnit timeUnit )
    {
        return new TimeDuration( value, timeUnit );
    }

    private static class TimeDetail implements Serializable
    {
        private final long milliseconds;
        private final long seconds;
        private final long minutes;
        private final long hours;
        private final long days;

        TimeDetail( final long duration )
        {
            final long totalSeconds = new BigDecimal( duration ).movePointLeft( 3 ).longValue();
            milliseconds = duration % 1000;
            seconds = ( totalSeconds ) % 60;
            minutes = ( totalSeconds / 60 ) % 60;
            hours = ( ( totalSeconds / 60 ) / 60 ) % 24;
            days = ( ( ( totalSeconds / 60 ) / 60 ) / 24 );
        }
    }
}

