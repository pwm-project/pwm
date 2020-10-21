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
