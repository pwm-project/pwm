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

import java.math.BigDecimal;
import java.util.Date;

/**
 * An immutable class representing a time period.  The internal value of the time period is
 * stored as milliseconds.
 * <p/>
 * Negative time durations are not permitted.  Operations that would result in a negative value
 * are negated and will instead result in postive values.
 *
 * @author  Jason D. Rivard
 */
public class TimeDuration implements Comparable {
// ------------------------------ FIELDS ------------------------------

    public static final TimeDuration ZERO = new TimeDuration(0);
    public static final TimeDuration MILLISECOND = new TimeDuration(1);
    public static final TimeDuration SECOND = new TimeDuration(1000);
    public static final TimeDuration MINUTE = new TimeDuration(1000 * 60);
    public static final TimeDuration HOUR = new TimeDuration(1000 * 60 * 60);
    public static final TimeDuration DAY = new TimeDuration(1000 * 60 * 60 * 24);
    private long ms;
    private TimeDetail cachedTimeDetail;

// --------------------------- CONSTRUCTORS ---------------------------

    /**
     * Create a new TimeDuration using the specified duration, in milliseconds
     *
     * @param duration a time period in milliseconds
     */
    public TimeDuration(final long duration) {
        if (duration < 0) {
            this.ms = 0;
        } else {
            this.ms = duration;
        }
    }

    public static TimeDuration fromCurrent(final long ms) {
        return new TimeDuration(System.currentTimeMillis(),ms);
    }

    public static String compactFromCurrent(final long ms) {
        return new TimeDuration(System.currentTimeMillis(),ms).asCompactString();
    }

    public static String asCompactString(final long ms) {
        return new TimeDuration(ms).asCompactString();
    }


    /**
     * Create a new TimeDuration using the absolute difference as the time
     * period between the two supplied timestamps
     *
     * @param date timestamp in Date format
     * @param milliseconds timestamp in ms
     */
    public TimeDuration(final Date date, final long milliseconds) {
        this(date.getTime(), milliseconds);
    }

    /**
     * Create a new TimeDuration using the absolute difference as the time
     * period between the two supplied timestamps
     *
     * @param date timestamp in Date format
     * @param milliseconds timestamp in ms
     */
    public TimeDuration(final long milliseconds, final Date date) {
        this(milliseconds, date.getTime());
    }

    /**
     * Create a new TimeDuration using the absolute difference as the time
     * period between the two supplied timestamps
     *
     * @param date1 timestamp in Date format
     * @param date2 timestamp in Date format
     */
    public TimeDuration(final Date date1, final Date date2) {
        this(date1.getTime(), date2.getTime());
    }

    /**
     * Create a new TimeDuration using the absolute difference as the time
     * period between the two supplied timestamps
     *
     * @param milliseconds1 timestamp in ms
     * @param milliseconds2 timestamp in ms
     */
    public TimeDuration(final long milliseconds1, final long milliseconds2) {
        this(Math.abs(milliseconds1 - milliseconds2));
    }

// ------------------------ CANONICAL METHODS ------------------------

    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TimeDuration)) {
            return false;
        }

        final TimeDuration timeDuration = (TimeDuration) o;

        return ms == timeDuration.ms;
    }

    public int hashCode() {
        return (int) (ms ^ (ms >>> 32));
    }


// -------------------------- OTHER METHODS --------------------------

    public TimeDuration add(final TimeDuration duration) {
        return new TimeDuration(this.getTotalMilliseconds() + duration.getTotalMilliseconds());
    }

    public long getTotalMilliseconds() {
        return ms;
    }

    public String asCompactString() {
        final StringBuilder sb = new StringBuilder();

        if (this.equals(ZERO)) {
            return "0ms";
        }

        if (this.equals(DAY) || this.isLongerThan(DAY)) {
            sb.append(this.getDays());
            sb.append("d");
        }

        if (this.equals(HOUR) || this.isLongerThan(HOUR)) {
            if (getHours() != 0) {
                if (sb.length() > 0) {
                    sb.append(":");
                }
                sb.append(getHours());
                sb.append("h");
            }
        }

        if (this.equals(MINUTE) || this.isLongerThan(MINUTE)) {
            if (getMinutes() != 0) {
                if (sb.length() > 0) {
                    sb.append(":");
                }
                sb.append(getMinutes());
                sb.append("m");
            }
        }

        if (this.isShorterThan(1000 * 60 * 10) && this.isLongerThan(1000 * 3)) { // 10 minutes to 3 seconds
            if (getSeconds() != 0) {
                if (sb.length() > 0) {
                    sb.append(":");
                }
                sb.append(getSeconds());
                sb.append("s");
            }
        }

        if (this.isShorterThan(1000 * 3)) { // 3 seconds
            if (getMilliseconds() != 0) {
                if (sb.length() > 0) {
                    sb.append(":");
                }
                sb.append(getMilliseconds());
                sb.append("ms");
            }
        }

        if (sb.length() == 0) {
            sb.append(0);
            sb.append("s");
        }

        return sb.toString();
    }

    public long getDays() {
        return getTimeDetail().days;
    }

    private TimeDetail getTimeDetail() {
        // lazy init, but not sync'd, no big deal if dupes created
        if (cachedTimeDetail == null) {
            cachedTimeDetail = new TimeDetail(ms);
        }
        return cachedTimeDetail;
    }

    public long getHours() {
        return getTimeDetail().hours;
    }

    public boolean isLongerThan(final TimeDuration duration) {
        return this.compareTo(duration) == 1;
    }

    public int compareTo(final Object o) {
        final TimeDuration td = (TimeDuration) o;
        final long otherMS = td.getTotalMilliseconds();
        return (ms == otherMS ? 0 : (ms < otherMS ? -1 : 1));
    }

    public long getMinutes() {
        return getTimeDetail().minutes;
    }

    public boolean isLongerThan(final long durationMS) {
        return this.isLongerThan(new TimeDuration(durationMS));
    }

    public long getSeconds() {
        return getTimeDetail().seconds;
    }

    public boolean isShorterThan(final long durationMS) {
        return this.isShorterThan(new TimeDuration(durationMS));
    }

    public long getMilliseconds() {
        return getTimeDetail().milliseconds;
    }

    public String asLongString() {
        final TimeDetail timeDetail = getTimeDetail();

        final StringBuilder sb = new StringBuilder(16);

        //output number of days
        if (timeDetail.days > 0) {
            sb.append(this.getDaysString());
            sb.append(", ");
        }

        //output number of hours
        if (timeDetail.hours > 0) {
            sb.append(this.getHoursString());
            sb.append(", ");
        }

        //output number of minutes
        if (timeDetail.days > 0 || timeDetail.hours > 0 || timeDetail.minutes > 0) {
            sb.append(timeDetail.minutes);
            sb.append(" Minutes, ");
        }

        //seconds
        {
            sb.append(timeDetail.seconds);
            sb.append(" Seconds");
        }

        return sb.toString();
    }

    public String getDaysString() {
        final TimeDetail timeDetail = getTimeDetail();
        final StringBuilder sb = new StringBuilder(3);
        sb.append(timeDetail.days);
        sb.append(" Day");
        if (timeDetail.days != 1) {
            sb.append('s');
        }
        return sb.toString();
    }

    public String getHoursString() {
        final TimeDetail timeDetail = getTimeDetail();
        final StringBuilder sb = new StringBuilder(3);
        sb.append(timeDetail.hours);
        sb.append(" Hour");
        if (timeDetail.hours != 1) {
            sb.append('s');
        }
        return sb.toString();
    }

    public Date getDateAfterNow() {
        return this.getDateAfter(new Date(System.currentTimeMillis()));
    }

    public Date getDateAfter(final Date specifiedDate) {
        return new Date(specifiedDate.getTime() + ms);
    }

    public Date getDateBeforeNow() {
        return this.getDateBefore(new Date(System.currentTimeMillis()));
    }

    public Date getDateBefore(final Date specifiedDate) {
        return new Date(specifiedDate.getTime() - ms);
    }

    public boolean isShorterThan(final TimeDuration duration) {
        return this.compareTo(duration) == -1;
    }

    public TimeDuration subtract(final TimeDuration duration) {
        return new TimeDuration(Math.abs(this.getTotalMilliseconds() - duration.getTotalMilliseconds()));
    }

// -------------------------- INNER CLASSES --------------------------

    private static class TimeDetail {
        private final long milliseconds;
        private final long seconds;
        private final long minutes;
        private final long hours;
        private final long days;

        TimeDetail(final long duration) {
            final long totalSeconds = new BigDecimal(duration).movePointLeft(3).longValue();
            milliseconds = duration % 1000;
            seconds = (totalSeconds) % 60;
            minutes = (totalSeconds / 60) % 60;
            hours = ((totalSeconds / 60) / 60) % 60;
            days = (((totalSeconds / 60) / 60) / 24);
        }
    }
}

