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

package password.pwm.svc.stats;

import org.apache.commons.csv.CSVPrinter;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.http.PwmRequest;
import password.pwm.svc.PwmService;
import password.pwm.util.EventRateMeter;
import password.pwm.util.PwmScheduler;
import password.pwm.util.java.JavaHelper;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;

public class StatisticsManager implements PwmService
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( StatisticsManager.class );

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

    private ExecutorService executorService;

    private final StatisticsBundle statsCurrent = new StatisticsBundle();
    private StatisticsBundle statsDaily = new StatisticsBundle();
    private StatisticsBundle statsCummulative = new StatisticsBundle();
    private Map<EpsKey, EventRateMeter> epsMeterMap = new HashMap<>();

    private PwmApplication pwmApplication;

    private STATUS status = STATUS.NEW;


    private final Map<String, StatisticsBundle> cachedStoredStats = new LinkedHashMap<String, StatisticsBundle>()
    {
        @Override
        protected boolean removeEldestEntry( final Map.Entry<String, StatisticsBundle> eldest )
        {
            return this.size() > 50;
        }
    };

    public StatisticsManager( )
    {
        for ( final EpsKey epsKey : EpsKey.allKeys() )
        {
            epsMeterMap.put( epsKey, new EventRateMeter( epsKey.getEpsDuration().getTimeDuration() ) );
        }
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

        if ( currentDailyKey.toString().equals( key ) )
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
            final String storedStat = localDB.get( LocalDB.DB.PWM_STATS, key );
            final StatisticsBundle returnBundle;
            if ( !StringUtil.isEmpty( storedStat ) )
            {
                returnBundle = StatisticsBundle.input( storedStat );
            }
            else
            {
                returnBundle = new StatisticsBundle();
            }
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
            sb.append( m.toString() );
            sb.append( "=" );
            sb.append( statsCurrent.getStatistic( m ) );
            sb.append( ", " );
        }

        if ( sb.length() > 2 )
        {
            sb.delete( sb.length() - 2, sb.length() );
        }

        return sb.toString();
    }

    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
        status = STATUS.OPENING;
        this.localDB = pwmApplication.getLocalDB();
        this.pwmApplication = pwmApplication;

        if ( localDB == null )
        {
            LOGGER.error( () -> "LocalDB is not available, will remain closed" );
            status = STATUS.CLOSED;
            return;
        }

        {
            final String storedCumulativeBundleSir = localDB.get( LocalDB.DB.PWM_STATS, DB_KEY_CUMULATIVE );
            if ( !StringUtil.isEmpty( storedCumulativeBundleSir ) )
            {
                try
                {
                    statsCummulative = StatisticsBundle.input( storedCumulativeBundleSir );
                }
                catch ( final Exception e )
                {
                    LOGGER.warn( () -> "error loading saved stored cumulative statistics: " + e.getMessage() );
                }
            }
        }

        {
            final String storedInitialString = localDB.get( LocalDB.DB.PWM_STATS, DB_KEY_INITIAL_DAILY_KEY );
            if ( !StringUtil.isEmpty( storedInitialString ) )
            {
                initialDailyKey = new DailyKey( storedInitialString );
            }
        }

        {
            currentDailyKey = DailyKey.forToday();
            final String storedDailyStr = localDB.get( LocalDB.DB.PWM_STATS, currentDailyKey.toString() );
            if ( !StringUtil.isEmpty( storedDailyStr ) )
            {
                statsDaily = StatisticsBundle.input( storedDailyStr );
            }
        }

        try
        {
            localDB.put( LocalDB.DB.PWM_STATS, DB_KEY_TEMP, JavaHelper.toIsoDate( Instant.now() ) );
        }
        catch ( final IllegalStateException e )
        {
            LOGGER.error( () -> "unable to write to localDB, will remain closed, error: " + e.getMessage() );
            status = STATUS.CLOSED;
            return;
        }

        localDB.put( LocalDB.DB.PWM_STATS, DB_KEY_VERSION, DB_VALUE_VERSION );
        localDB.put( LocalDB.DB.PWM_STATS, DB_KEY_INITIAL_DAILY_KEY, initialDailyKey.toString() );

        {
            // setup a timer to roll over at 0 Zulu and one to write current stats regularly
            executorService = PwmScheduler.makeBackgroundExecutor( pwmApplication, this.getClass() );
            pwmApplication.getPwmScheduler().scheduleFixedRateJob( new FlushTask(), executorService, DB_WRITE_FREQUENCY, DB_WRITE_FREQUENCY );
            pwmApplication.getPwmScheduler().scheduleDailyZuluZeroStartJob( new NightlyTask(), executorService, TimeDuration.ZERO );
        }

        status = STATUS.OPEN;
    }

    private void writeDbValues( )
    {
        if ( localDB != null && status == STATUS.OPEN )
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
        final Map<String, String> emailValues = new LinkedHashMap<>();
        for ( final Statistic statistic : Statistic.values() )
        {
            final String key = statistic.getLabel( PwmConstants.DEFAULT_LOCALE );
            final String value = statsDaily.getStatistic( statistic );
            emailValues.put( key, value );
        }

        return Collections.unmodifiableMap( emailValues );
    }

    private void resetDailyStats( )
    {
        currentDailyKey = DailyKey.forToday();
        statsDaily = new StatisticsBundle();
        LOGGER.debug( () -> "reset daily statistics" );
    }

    public STATUS status( )
    {
        return status;
    }


    public void close( )
    {
        try
        {
            writeDbValues();
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "unexpected error closing: " + e.getMessage() );
        }

        JavaHelper.closeAndWaitExecutor( executorService, TimeDuration.of( 3, TimeDuration.Unit.SECONDS ) );

        status = STATUS.CLOSED;
    }

    public List<HealthRecord> healthCheck( )
    {
        return Collections.emptyList();
    }


    private class NightlyTask extends TimerTask
    {
        public void run( )
        {
            writeDbValues();
            resetDailyStats();
        }
    }

    private class FlushTask extends TimerTask
    {
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
        return epsMeterMap.get( epsKey ).readEventRate();
    }


    public int outputStatsToCsv( final OutputStream outputStream, final Locale locale, final boolean includeHeader )
            throws IOException
    {
        LOGGER.trace( () -> "beginning output stats to csv process" );
        final Instant startTime = Instant.now();

        final StatisticsManager statsManger = pwmApplication.getStatisticsManager();
        final CSVPrinter csvPrinter = JavaHelper.makeCsvPrinter( outputStream );

        if ( includeHeader )
        {
            final List<String> headers = new ArrayList<>();
            headers.add( "KEY" );
            headers.add( "YEAR" );
            headers.add( "DAY" );
            for ( final Statistic stat : Statistic.values() )
            {
                headers.add( stat.getLabel( locale ) );
            }
            csvPrinter.printRecord( headers );
        }

        int counter = 0;
        final Map<DailyKey, String> keys = statsManger.getAvailableKeys( PwmConstants.DEFAULT_LOCALE );
        for ( final DailyKey loopKey : keys.keySet() )
        {
            counter++;
            final StatisticsBundle bundle = statsManger.getStatBundleForKey( loopKey.toString() );
            final List<String> lineOutput = new ArrayList<>();
            lineOutput.add( loopKey.toString() );
            lineOutput.add( String.valueOf( loopKey.getYear() ) );
            lineOutput.add( String.valueOf( loopKey.getDay() ) );
            for ( final Statistic stat : Statistic.values() )
            {
                lineOutput.add( bundle.getStatistic( stat ) );
            }
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

    public ServiceInfoBean serviceInfo( )
    {
        return status() == STATUS.OPEN
                ? new ServiceInfoBean( Collections.singletonList( DataStorageMethod.LOCALDB ) )
                : new ServiceInfoBean( Collections.emptyList() );
    }

    public static void incrementStat(
            final PwmRequest pwmRequest,
            final Statistic statistic
    )
    {
        incrementStat( pwmRequest.getPwmApplication(), statistic );
    }

    public static void incrementStat(
            final PwmApplication pwmApplication,
            final Statistic statistic
    )
    {
        if ( pwmApplication == null )
        {
            LOGGER.error( () -> "skipping requested statistic increment of " + statistic + " due to null pwmApplication" );
            return;
        }

        final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
        if ( statisticsManager == null )
        {
            LOGGER.error( () -> "skipping requested statistic increment of " + statistic + " due to null statisticsManager" );
            return;
        }

        if ( statisticsManager.status() != STATUS.OPEN )
        {
            LOGGER.trace(
                    () -> "skipping requested statistic increment of " + statistic + " due to StatisticsManager being closed" );
            return;
        }

        statisticsManager.incrementValue( statistic );
    }

}
