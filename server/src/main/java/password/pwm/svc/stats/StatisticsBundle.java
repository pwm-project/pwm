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

import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class StatisticsBundle
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( StatisticsBundle.class );

    static final SimpleDateFormat STORED_DATETIME_FORMATTER = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss Z" );

    static
    {
        STORED_DATETIME_FORMATTER.setTimeZone( TimeZone.getTimeZone( "Zulu" ) );
    }


    private final Map<Statistic, String> valueMap = new HashMap<>();

    public StatisticsBundle( )
    {
    }

    public String output( )
    {
        return JsonUtil.serializeMap( valueMap );
    }

    public static StatisticsBundle input( final String inputString )
    {
        final Map<Statistic, String> srcMap = new HashMap<>();
        final Map<String, String> loadedMap = JsonUtil.deserializeStringMap( inputString );
        for ( final Map.Entry<String, String> entry : loadedMap.entrySet() )
        {
            final String key = entry.getKey();
            try
            {
                srcMap.put( Statistic.valueOf( key ), entry.getValue() );
            }
            catch ( IllegalArgumentException e )
            {
                LOGGER.error( "error parsing statistic key '" + key + "', reason: " + e.getMessage() );
            }
        }
        final StatisticsBundle bundle = new StatisticsBundle();

        for ( final Statistic loopStat : Statistic.values() )
        {
            final String value = srcMap.get( loopStat );
            if ( !StringUtil.isEmpty( value ) )
            {
                bundle.valueMap.put( loopStat, value );
            }
        }

        return bundle;
    }

    public synchronized void incrementValue( final Statistic statistic )
    {
        if ( Statistic.Type.INCREMENTER != statistic.getType() )
        {
            LOGGER.error( "attempt to increment non-counter/incremental stat " + statistic );
            return;
        }

        BigInteger currentValue = BigInteger.ZERO;
        try
        {
            if ( valueMap.containsKey( statistic ) )
            {
                currentValue = new BigInteger( valueMap.get( statistic ) );
            }
            else
            {
                currentValue = BigInteger.ZERO;
            }
        }
        catch ( NumberFormatException e )
        {
            LOGGER.error( "error reading counter/incremental stat " + statistic );
        }
        final BigInteger newValue = currentValue.add( BigInteger.ONE );
        valueMap.put( statistic, newValue.toString() );
    }

    public synchronized void updateAverageValue( final Statistic statistic, final long timeDuration )
    {
        if ( Statistic.Type.AVERAGE != statistic.getType() )
        {
            LOGGER.error( "attempt to update average value of non-average stat " + statistic );
            return;
        }

        final String avgStrValue = valueMap.get( statistic );

        AverageBean avgBean = new AverageBean();
        if ( avgStrValue != null && avgStrValue.length() > 0 )
        {
            try
            {
                avgBean = JsonUtil.deserialize( avgStrValue, AverageBean.class );
            }
            catch ( Exception e )
            {
                LOGGER.trace( () -> "unable to parse statistics value for stat " + statistic.toString() + ", value=" + avgStrValue );
            }
        }

        avgBean.appendValue( timeDuration );
        valueMap.put( statistic, JsonUtil.serialize( avgBean ) );
    }

    public String getStatistic( final Statistic statistic )
    {
        switch ( statistic.getType() )
        {
            case INCREMENTER:
                return valueMap.containsKey( statistic ) ? valueMap.get( statistic ) : "0";

            case AVERAGE:
                final String avgStrValue = valueMap.get( statistic );

                AverageBean avgBean = new AverageBean();
                if ( avgStrValue != null && avgStrValue.length() > 0 )
                {
                    try
                    {
                        avgBean = JsonUtil.deserialize( avgStrValue, AverageBean.class );
                    }
                    catch ( Exception e )
                    {
                        LOGGER.trace( () ->  "unable to parse statistics value for stat " + statistic.toString() + ", value=" + avgStrValue );
                    }
                }
                return avgBean.getAverage().toString();

            default:
                return "";
        }
    }

    private static class AverageBean implements Serializable
    {
        BigInteger total = BigInteger.ZERO;
        BigInteger count = BigInteger.ZERO;

        AverageBean( )
        {
        }

        BigInteger getAverage( )
        {
            if ( BigInteger.ZERO.equals( count ) )
            {
                return BigInteger.ZERO;
            }

            return total.divide( count );
        }

        void appendValue( final long value )
        {
            count = count.add( BigInteger.ONE );
            total = total.add( BigInteger.valueOf( value ) );
        }
    }
}
