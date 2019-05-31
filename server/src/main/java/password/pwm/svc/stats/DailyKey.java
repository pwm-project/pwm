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

package password.pwm.svc.stats;

import lombok.Value;

import java.time.LocalDate;

@Value
public class DailyKey
{
    private static final String DB_KEY_PREFIX_DAILY = "DAILY_";
    private int year;
    private int day;

    private DailyKey()
    {
        final LocalDate localDate = LocalDate.now();
        year = localDate.getYear();
        day = localDate.getDayOfYear();
    }

    DailyKey( final String value )
    {
        final String strippedValue = value.substring( DB_KEY_PREFIX_DAILY.length() );
        final String[] splitValue = strippedValue.split( "_" );
        year = Integer.parseInt( splitValue[ 0 ] );
        day = Integer.parseInt( splitValue[ 1 ] );
    }

    private DailyKey( final int year, final int day )
    {
        this.year = year;
        this.day = day;
    }

    @Override
    public String toString( )
    {
        return DB_KEY_PREFIX_DAILY + year + "_" + day;
    }

    public static DailyKey forToday()
    {
        return new DailyKey( );
    }

    public DailyKey previous( )
    {
        final LocalDate thisDay = localDate();
        final LocalDate previousDay = thisDay.minusDays( 1 );
        return new DailyKey( previousDay.getYear(), previousDay.getDayOfYear() );
    }

    public LocalDate localDate()
    {
        return LocalDate.ofYearDay( year, day );
    }
}
