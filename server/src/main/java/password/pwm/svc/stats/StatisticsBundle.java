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

import password.pwm.util.java.AtomicLoopLongIncrementer;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class StatisticsBundle
{
    private final Map<Statistic, AtomicLoopLongIncrementer> incrementerMap = new EnumMap<>( Statistic.class );
    private final Map<AvgStatistic, AverageBean> avgMap = new EnumMap<>( AvgStatistic.class );

    StatisticsBundle( )
    {
        for ( final Statistic statistic : Statistic.values() )
        {
            incrementerMap.put( statistic, new AtomicLoopLongIncrementer( 0, Long.MAX_VALUE ) );
        }
        for ( final AvgStatistic avgStatistic : AvgStatistic.values() )
        {
            avgMap.put( avgStatistic, new AverageBean() );
        }
    }

    public String output( )
    {
        final Map<String, String> outputMap = new LinkedHashMap<>();

        for ( final Statistic statistic : Statistic.values() )
        {
            final long currentValue = incrementerMap.get( statistic ).get();
            if ( currentValue > 0 )
            {
                outputMap.put( statistic.name(), Long.toString( currentValue ) );
            }
        }
        for ( final AvgStatistic epsStatistic : AvgStatistic.values() )
        {
            final AverageBean averageBean = avgMap.get( epsStatistic );
            if ( !averageBean.isZero() )
            {
                outputMap.put( epsStatistic.name(), JsonUtil.serialize( averageBean ) );
            }
        }

        return JsonUtil.serializeMap( outputMap );
    }

    public static StatisticsBundle input( final String inputString )
    {
        final Map<String, String> loadedMap = JsonUtil.deserializeStringMap( inputString );
        final StatisticsBundle bundle = new StatisticsBundle();

        for ( final Statistic loopStat : Statistic.values() )
        {
            final String value = loadedMap.get( loopStat.name() );
            if ( !StringUtil.isEmpty( value ) )
            {
                final long longValue = JavaHelper.silentParseLong( value, 0 );
                final AtomicLoopLongIncrementer incrementer = new AtomicLoopLongIncrementer( longValue, Long.MAX_VALUE );
                bundle.incrementerMap.put( loopStat, incrementer );
            }
        }

        for ( final AvgStatistic loopStat : AvgStatistic.values() )
        {
            final String value = loadedMap.get( loopStat.name() );
            if ( !StringUtil.isEmpty( value ) )
            {
                final AverageBean avgBean = JsonUtil.deserialize( value, AverageBean.class );
                bundle.avgMap.put( loopStat, avgBean );
            }
        }

        return bundle;
    }

    void incrementValue( final Statistic statistic )
    {
        incrementerMap.get( statistic ).incrementAndGet();
    }

    void updateAverageValue( final AvgStatistic statistic, final long timeDuration )
    {
        avgMap.get( statistic ).appendValue( timeDuration );
    }

    public String getStatistic( final Statistic statistic )
    {
        return Long.toString( incrementerMap.get( statistic ).get() );
    }

    public String getAvgStatistic( final AvgStatistic statistic )
    {
        return avgMap.get( statistic ).getAverage().toString();
    }

    private static class AverageBean implements Serializable
    {
        BigInteger total = BigInteger.ZERO;
        BigInteger count = BigInteger.ZERO;

        AverageBean( )
        {
        }

        synchronized BigInteger getAverage( )
        {
            if ( BigInteger.ZERO.equals( count ) )
            {
                return BigInteger.ZERO;
            }

            return total.divide( count );
        }

        synchronized void appendValue( final long value )
        {
            count = count.add( BigInteger.ONE );
            total = total.add( BigInteger.valueOf( value ) );
        }

        synchronized boolean isZero()
        {
            return total.equals( BigInteger.ZERO );
        }
    }
}
