/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

import password.pwm.util.java.EnumUtil;
import password.pwm.util.java.StringUtil;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class StatisticsBundleKey implements Comparable<StatisticsBundleKey>
{
    private static final String DB_KEY_PREFIX_DAILY = "DAILY_";

    static final Comparator<StatisticsBundleKey> COMPARATOR = Comparator
            .comparing( StatisticsBundleKey::getKeyType )
            .thenComparingInt( StatisticsBundleKey::getYear )
            .thenComparingInt( StatisticsBundleKey::getDay );

    static final StatisticsBundleKey CUMULATIVE = new StatisticsBundleKey( KeyType.CUMULATIVE, -1, -1 );
    static final StatisticsBundleKey CURRENT  = new StatisticsBundleKey( KeyType.CURRENT, -1, -1 );

    enum KeyType
    {
        CUMULATIVE( "Cumulative - since install" ),
        CURRENT( "Current - since startup" ),
        DAILY( null ),;


        private final String label;

        KeyType( final String label )
        {
            this.label = label;
        }

        public String getLabel( final Locale locale )
        {
            return label;
        }
    }

    private final KeyType keyType;
    private final int year;
    private final int day;

    private StatisticsBundleKey( final KeyType keyType, final int year, final int day )
    {
        this.keyType = Objects.requireNonNull( keyType );
        this.year = year;
        this.day = day;
        checkParameterValidity();
    }

    private void checkParameterValidity()
    {
        if ( keyType == KeyType.DAILY )
        {
            if ( year <= 0 || year > 99999 )
            {
                throw new IllegalArgumentException( "invalid year value '" + year + "'" );
            }
            if ( day <= 0 || day > 366 )
            {
                throw new IllegalArgumentException( "invalid day value '" + day + "'" );
            }
        }
    }

    @Override
    public String toString( )
    {
        if ( keyType == KeyType.CURRENT || keyType == KeyType.CUMULATIVE )
        {
            return keyType.name();
        }

        return DB_KEY_PREFIX_DAILY + year + "_" + day;
    }

    @Override
    public int compareTo( final StatisticsBundleKey otherKey )
    {
        return COMPARATOR.compare( this, otherKey );
    }

    public KeyType getKeyType()
    {
        return keyType;
    }

    public int getYear()
    {
        return year;
    }

    public int getDay()
    {
        return day;
    }

    @Override
    public boolean equals( final Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( o == null || getClass() != o.getClass() )
        {
            return false;
        }
        final StatisticsBundleKey that = ( StatisticsBundleKey ) o;
        return year == that.year && day == that.day && keyType == that.keyType;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash( keyType, year, day );
    }

    public static StatisticsBundleKey fromString( final String input )
    {
        final Optional<KeyType> optionalInputKeyType = EnumUtil.readEnumFromString( KeyType.class, input );

        if ( optionalInputKeyType.isPresent() )
        {
            final KeyType theKey = optionalInputKeyType.get();
            if ( theKey == KeyType.CUMULATIVE )
            {
                return CUMULATIVE;
            }
            if ( theKey == KeyType.CURRENT )
            {
                return CURRENT;
            }
        }

        final String strippedValue = input.substring( DB_KEY_PREFIX_DAILY.length() );
        final String[] splitValue = strippedValue.split( "_" );
        final int year = Integer.parseInt( splitValue[ 0 ] );
        final int day = Integer.parseInt( splitValue[ 1 ] );
        return new StatisticsBundleKey( KeyType.DAILY, year, day );
    }

    public static StatisticsBundleKey fromStringOrDefaultCumulative( final String input )
    {
        if ( StringUtil.isEmpty( input ) )
        {
            return CUMULATIVE;
        }

        try
        {
            return fromString( input );
        }
        catch ( final Exception e )
        {
            /* ignore */
        }

        return CUMULATIVE;
    }

    public static StatisticsBundleKey forToday()
    {
        final LocalDate localDate = LocalDate.now();
        final int year = localDate.getYear();
        final int day = localDate.getDayOfYear();

        return new StatisticsBundleKey( KeyType.DAILY, year, day );
    }

    public StatisticsBundleKey previous( )
    {
        final LocalDate thisDay = localDate();
        final LocalDate previousDay = thisDay.minusDays( 1 );
        return new StatisticsBundleKey( KeyType.DAILY, previousDay.getYear(), previousDay.getDayOfYear() );
    }

    public LocalDate localDate()
    {
        return LocalDate.ofYearDay( year, day );
    }

    public String getLabel( final Locale locale )
    {
        if ( keyType == KeyType.DAILY )
        {
            return localDate().format( DateTimeFormatter.ISO_LOCAL_DATE.localizedBy( locale ) );
        }
        return keyType.getLabel( locale );
    }

    public static Set<StatisticsBundleKey> range( final StatisticsBundleKey key1, final StatisticsBundleKey key2 )
    {
        Objects.requireNonNull( key1 );
        Objects.requireNonNull( key2 );

        if ( key1.getKeyType() != KeyType.DAILY || key2.getKeyType() != KeyType.DAILY )
        {
            throw new IllegalArgumentException( "both keys must be of type DAILY" );
        }
        if ( key1.equals( key2 ) )
        {
            return Collections.singleton( key1 );
        }

        final List<StatisticsBundleKey> sortedInput = new ArrayList<>();
        sortedInput.add( key1 );
        sortedInput.add( key2 );
        sortedInput.sort( COMPARATOR );

        final StatisticsBundleKey firstKey = sortedInput.get( 0 );
        final StatisticsBundleKey lastKey = sortedInput.get( 1 );

        final List<StatisticsBundleKey> results = new ArrayList<>();
        results.add( firstKey );
        StatisticsBundleKey loopKey = lastKey;
        int safetyCounter = 0;
        while ( !loopKey.equals( firstKey ) && safetyCounter < 50000 )
        {
            results.add( loopKey );
            loopKey = loopKey.previous();
            safetyCounter++;
        }
        results.sort( COMPARATOR );
        return Collections.unmodifiableSet( new LinkedHashSet<>( results ) );
    }
}
