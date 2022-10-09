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

import org.apache.commons.csv.CSVPrinter;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.DailySummaryJob;
import password.pwm.util.EventRateMeter;
import password.pwm.util.java.CollectorUtil;
import password.pwm.util.java.EnumUtil;
import password.pwm.util.java.PwmUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TimerTask;
import java.util.stream.Collectors;

public class StatisticsService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( StatisticsService.class );

    // 1 minutes
    private static final TimeDuration DB_WRITE_FREQUENCY = TimeDuration.MINUTE;

    private static final String DB_KEY_VERSION = "STATS_VERSION";
    private static final String DB_KEY_CUMULATIVE = "CUMULATIVE";
    private static final String DB_KEY_INITIAL_DAILY_KEY = "INITIAL_DAILY_KEY";
    private static final String DB_KEY_TEMP = "TEMP_KEY";

    private static final String DB_VALUE_VERSION = "1";

    public static final String KEY_CURRENT = "CURRENT";
    public static final String KEY_CUMULATIVE = "CUMULATIVE";

    private LocalDB localDB;

    private DailyKey currentDailyKey = DailyKey.forToday();
    private DailyKey initialDailyKey = DailyKey.forToday();

    private final StatisticsBundle statsCurrent = new StatisticsBundle();
    private final Map<EpsKey, EventRateMeter> epsMeterMap;
    private StatisticsBundle statsDaily = new StatisticsBundle();
    private StatisticsBundle statsCummulative = new StatisticsBundle();


    private final Map<String, StatisticsBundle> cachedStoredStats = new LinkedHashMap<>()
    {
        @Override
        protected boolean removeEldestEntry( final Map.Entry<String, StatisticsBundle> eldest )
        {
            return this.size() > 50;
        }
    };

    public StatisticsService( )
    {
        epsMeterMap = EpsKey.allKeys().stream()
                .map( key -> Map.entry( key, new EventRateMeter( key.getEpsDuration().getTimeDuration().asDuration() ) ) )
                .collect( Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue ) );
    }

    public void incrementValue( final Statistic statistic )
    {
        statsCurrent.incrementValue( statistic );
        statsDaily.incrementValue( statistic );
        statsCummulative.incrementValue( statistic );
    }

    public void updateAverageValue( final AvgStatistic statistic, final long value )
    {
        statsCurrent.updateAverageValue( statistic, value );
        statsDaily.updateAverageValue( statistic, value );
        statsCummulative.updateAverageValue( statistic, value );
    }

    public Map<String, String> getStatHistory( final Statistic statistic, final int days )
    {
        final Map<String, String> returnMap = new LinkedHashMap<>();
        DailyKey loopKey = currentDailyKey;
        int counter = days;
        while ( counter > 0 )
        {
            final StatisticsBundle bundle = getStatBundleForKey( loopKey.toString() );
            if ( bundle != null )
            {
                final String key = loopKey.toString();
                final String value = bundle.getStatistic( statistic );
                returnMap.put( key, value );
            }
            loopKey = loopKey.previous();
            counter--;
        }
        return returnMap;
    }

    public StatisticsBundle getStatBundleForKey( final String key )
    {
        if ( key == null || key.length() < 1 || KEY_CUMULATIVE.equals( key ) )
        {
            return statsCummulative;
        }

        if ( KEY_CURRENT.equals( key ) )
        {
            return statsCurrent;
        }

        if ( Objects.equals( currentDailyKey.toString(), key ) )
        {
            return statsDaily;
        }

        if ( cachedStoredStats.containsKey( key ) )
        {
            return cachedStoredStats.get( key );
        }

        if ( localDB == null )
        {
            return null;
        }

        try
        {
            final Optional<String> storedStat = localDB.get( LocalDB.DB.PWM_STATS, key );
            final StatisticsBundle returnBundle;
            returnBundle = storedStat.map( StatisticsBundle::input ).orElseGet( StatisticsBundle::new );
            cachedStoredStats.put( key, returnBundle );
            return returnBundle;
        }
        catch ( final LocalDBException e )
        {
            LOGGER.error( () -> "error retrieving stored stat for " + key + ": " + e.getMessage() );
        }

        return null;
    }

    public Map<DailyKey, String> getAvailableKeys( final Locale locale )
    {
        final Map<DailyKey, String> returnMap = new LinkedHashMap<>();

        // if no historical data then we're done
        if ( currentDailyKey.equals( initialDailyKey ) )
        {
            return returnMap;
        }

        DailyKey loopKey = currentDailyKey;
        int safetyCounter = 0;
        while ( !loopKey.equals( initialDailyKey ) && safetyCounter < 5000 )
        {
            final String display = loopKey.toString();
            returnMap.put( loopKey, display );
            loopKey = loopKey.previous();
            safetyCounter++;
        }
        return returnMap;
    }

    public String toString( )
    {
        final StringBuilder sb = new StringBuilder();

        for ( final Statistic m : Statistic.values() )
        {
            sb.append( m );
            sb.append( '=' );
            sb.append( statsCurrent.getStatistic( m ) );
            sb.append( ", " );
        }

        if ( sb.length() > 2 )
        {
            sb.delete( sb.length() - 2, sb.length() );
        }

        return sb.toString();
    }

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        this.localDB = pwmApplication.getLocalDB();

        if ( localDB == null )
        {
            LOGGER.debug( () -> "LocalDB is not available, will remain closed" );
            return STATUS.CLOSED;
        }

        {
            final Optional<String> storedCumulativeBundleSir = localDB.get( LocalDB.DB.PWM_STATS, DB_KEY_CUMULATIVE );
            if ( storedCumulativeBundleSir.isPresent() )
            {
                try
                {
                    statsCummulative = StatisticsBundle.input( storedCumulativeBundleSir.get() );
                }
                catch ( final Exception e )
                {
                    LOGGER.warn( getSessionLabel(), () -> "error loading saved stored cumulative statistics: " + e.getMessage() );
                }
            }
        }

        {
            final Optional<String> storedInitialString = localDB.get( LocalDB.DB.PWM_STATS, DB_KEY_INITIAL_DAILY_KEY );
            storedInitialString.ifPresent( s -> initialDailyKey = new DailyKey( s ) );
        }

        {
            currentDailyKey = DailyKey.forToday();
            final Optional<String> storedDailyStr = localDB.get( LocalDB.DB.PWM_STATS, currentDailyKey.toString() );
            storedDailyStr.ifPresent( s -> statsDaily = StatisticsBundle.input( s ) );
        }

        try
        {
            localDB.put( LocalDB.DB.PWM_STATS, DB_KEY_TEMP, StringUtil.toIsoDate( Instant.now() ) );
        }
        catch ( final IllegalStateException e )
        {
            LOGGER.error( () -> "unable to write to localDB, will remain closed, error: " + e.getMessage() );
            return STATUS.CLOSED;
        }

        localDB.put( LocalDB.DB.PWM_STATS, DB_KEY_VERSION, DB_VALUE_VERSION );
        localDB.put( LocalDB.DB.PWM_STATS, DB_KEY_INITIAL_DAILY_KEY, initialDailyKey.toString() );

        {
            // setup a timer to roll over at 0 Zulu and one to write current stats regularly
            scheduleDailyZuluZeroStartJob( new DailySummaryJob( pwmApplication ), TimeDuration.ZERO );
            scheduleFixedRateJob( new FlushTask(), DB_WRITE_FREQUENCY, DB_WRITE_FREQUENCY );
            scheduleDailyZuluZeroStartJob( new NightlyTask(), TimeDuration.ZERO );
        }

        return STATUS.OPEN;
    }

    private void writeDbValues( )
    {
        if ( localDB != null && status() == STATUS.OPEN )
        {
            try
            {
                final Map<String, String> dbData = new LinkedHashMap<>();
                dbData.put( DB_KEY_CUMULATIVE, statsCummulative.output() );
                dbData.put( currentDailyKey.toString(), statsDaily.output() );
                localDB.putAll( LocalDB.DB.PWM_STATS, dbData );
            }
            catch ( final LocalDBException e )
            {
                LOGGER.error( () -> "error outputting pwm statistics: " + e.getMessage() );
            }
        }
    }

    public Map<String, String> dailyStatisticsAsLabelValueMap()
    {
        return EnumUtil.enumStream( Statistic.class )
                .collect( CollectorUtil.toUnmodifiableLinkedMap(
                        statistic -> statistic.getLabel( PwmConstants.DEFAULT_LOCALE ),
                        statistic -> statsDaily.getStatistic( statistic ) ) );
    }

    private void resetDailyStats( )
    {
        currentDailyKey = DailyKey.forToday();
        statsDaily = new StatisticsBundle();
        LOGGER.debug( () -> "reset daily statistics" );
    }

    @Override
    public void shutdownImpl( )
    {
        try
        {
            writeDbValues();
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "unexpected error closing: " + e.getMessage() );
        }

        setStatus( STATUS.CLOSED );
    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        return Collections.emptyList();
    }

    private class NightlyTask extends TimerTask
    {
        @Override
        public void run( )
        {
            writeDbValues();
            resetDailyStats();
        }
    }

    private class FlushTask extends TimerTask
    {
        @Override
        public void run( )
        {
            writeDbValues();
        }
    }

    public void updateEps( final EpsStatistic type, final int itemCount )
    {
        for ( final Statistic.EpsDuration duration : Statistic.EpsDuration.values() )
        {
            final EpsKey epsKey = new EpsKey( type, duration );
            epsMeterMap.get( epsKey ).markEvents( itemCount );
        }
    }

    public BigDecimal readEps( final EpsStatistic type, final Statistic.EpsDuration duration )
    {
        final EpsKey epsKey = new EpsKey( type, duration );
        return epsMeterMap.get( epsKey ).rawEps();
    }


    public int outputStatsToCsv( final OutputStream outputStream, final Locale locale, final boolean includeHeader )
            throws IOException
    {
        LOGGER.trace( () -> "beginning output stats to csv process" );
        final Instant startTime = Instant.now();

        final StatisticsService statsManger = getPwmApplication().getStatisticsManager();
        final CSVPrinter csvPrinter = PwmUtil.makeCsvPrinter( outputStream );

        if ( includeHeader )
        {
            final List<String> headers = Statistic.asSet().stream()
                    .map( stat -> stat.getLabel( locale ) )
                    .collect( Collectors.toList() );

            headers.add( "KEY" );
            headers.add( "YEAR" );
            headers.add( "DAY" );

            csvPrinter.printRecord( headers );
        }

        int counter = 0;
        final Map<DailyKey, String> keys = statsManger.getAvailableKeys( PwmConstants.DEFAULT_LOCALE );
        for ( final DailyKey loopKey : keys.keySet() )
        {
            counter++;
            final StatisticsBundle bundle = statsManger.getStatBundleForKey( loopKey.toString() );

            final List<String> lineOutput = new ArrayList<>( Statistic.asSet().size() );

            lineOutput.add( loopKey.toString() );
            lineOutput.add( String.valueOf( loopKey.getYear() ) );
            lineOutput.add( String.valueOf( loopKey.getDay() ) );

            lineOutput.addAll( EnumUtil.enumStream( Statistic.class )
                    .map( bundle::getStatistic )
                    .collect( Collectors.toList() ) );

            csvPrinter.printRecord( lineOutput );
        }

        csvPrinter.flush();
        {
            final int finalCounter = counter;
            LOGGER.trace( () -> "completed output stats to csv process; output " + finalCounter + " records in "
                    + TimeDuration.compactFromCurrent( startTime ) );
        }
        return counter;
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return status() == STATUS.OPEN
                ? ServiceInfoBean.builder().storageMethod( DataStorageMethod.LOCALDB ).build()
                : ServiceInfoBean.builder().build();
    }

}
