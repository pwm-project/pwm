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

import org.apache.commons.csv.CSVPrinter;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.http.PwmRequest;
import password.pwm.svc.PwmService;
import password.pwm.util.AlertHandler;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TimerTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StatisticsManager implements PwmService
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( StatisticsManager.class );

    // 1 minutes
    private static final TimeDuration DB_WRITE_FREQUENCY = new TimeDuration( 1, TimeUnit.MINUTES );

    private static final String DB_KEY_VERSION = "STATS_VERSION";
    private static final String DB_KEY_CUMULATIVE = "CUMULATIVE";
    private static final String DB_KEY_INITIAL_DAILY_KEY = "INITIAL_DAILY_KEY";
    private static final String DB_KEY_PREFIX_DAILY = "DAILY_";
    private static final String DB_KEY_TEMP = "TEMP_KEY";

    private static final String DB_VALUE_VERSION = "1";

    public static final String KEY_CURRENT = "CURRENT";
    public static final String KEY_CUMULATIVE = "CUMULATIVE";

    private LocalDB localDB;

    private DailyKey currentDailyKey = new DailyKey( new Date() );
    private DailyKey initialDailyKey = new DailyKey( new Date() );

    private ScheduledExecutorService executorService;

    private final StatisticsBundle statsCurrent = new StatisticsBundle();
    private StatisticsBundle statsDaily = new StatisticsBundle();
    private StatisticsBundle statsCummulative = new StatisticsBundle();
    private Map<String, EventRateMeter> epsMeterMap = new HashMap<>();

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
    }

    public synchronized void incrementValue( final Statistic statistic )
    {
        statsCurrent.incrementValue( statistic );
        statsDaily.incrementValue( statistic );
        statsCummulative.incrementValue( statistic );
    }

    public synchronized void updateAverageValue( final Statistic statistic, final long value )
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
                final String key = ( new SimpleDateFormat( "MMM dd" ) ).format( loopKey.calendar().getTime() );
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
            if ( storedStat != null && storedStat.length() > 0 )
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
        catch ( LocalDBException e )
        {
            LOGGER.error( "error retrieving stored stat for " + key + ": " + e.getMessage() );
        }

        return null;
    }

    public Map<DailyKey, String> getAvailableKeys( final Locale locale )
    {
        final DateFormat dateFormatter = SimpleDateFormat.getDateInstance( SimpleDateFormat.DEFAULT, locale );
        final Map<DailyKey, String> returnMap = new LinkedHashMap<DailyKey, String>();

        // add current time;
        returnMap.put( currentDailyKey, dateFormatter.format( new Date() ) );

        // if now historical data then we're done
        if ( currentDailyKey.equals( initialDailyKey ) )
        {
            return returnMap;
        }

        DailyKey loopKey = currentDailyKey;
        int safetyCounter = 0;
        while ( !loopKey.equals( initialDailyKey ) && safetyCounter < 5000 )
        {
            final Calendar c = loopKey.calendar();
            final String display = dateFormatter.format( c.getTime() );
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
        for ( final EpsStatistic type : EpsStatistic.values() )
        {
            for ( final Statistic.EpsDuration duration : Statistic.EpsDuration.values() )
            {
                epsMeterMap.put( type.toString() + duration.toString(), new EventRateMeter( duration.getTimeDuration() ) );
            }
        }

        status = STATUS.OPENING;
        this.localDB = pwmApplication.getLocalDB();
        this.pwmApplication = pwmApplication;

        if ( localDB == null )
        {
            LOGGER.error( "LocalDB is not available, will remain closed" );
            status = STATUS.CLOSED;
            return;
        }

        {
            final String storedCummulativeBundleStr = localDB.get( LocalDB.DB.PWM_STATS, DB_KEY_CUMULATIVE );
            if ( storedCummulativeBundleStr != null && storedCummulativeBundleStr.length() > 0 )
            {
                try
                {
                    statsCummulative = StatisticsBundle.input( storedCummulativeBundleStr );
                }
                catch ( Exception e )
                {
                    LOGGER.warn( "error loading saved stored statistics: " + e.getMessage() );
                }
            }
        }

        {
            for ( final EpsStatistic loopEpsType : EpsStatistic.values() )
            {
                for ( final EpsStatistic loopEpsDuration : EpsStatistic.values() )
                {
                    final String key = "EPS-" + loopEpsType.toString() + loopEpsDuration.toString();
                    final String storedValue = localDB.get( LocalDB.DB.PWM_STATS, key );
                    if ( storedValue != null && storedValue.length() > 0 )
                    {
                        try
                        {
                            final EventRateMeter eventRateMeter = JsonUtil.deserialize( storedValue, EventRateMeter.class );
                            epsMeterMap.put( loopEpsType.toString() + loopEpsDuration.toString(), eventRateMeter );
                        }
                        catch ( Exception e )
                        {
                            LOGGER.error( "unexpected error reading last EPS rate for " + loopEpsType + " from LocalDB: " + e.getMessage() );
                        }
                    }
                }
            }

        }

        {
            final String storedInitialString = localDB.get( LocalDB.DB.PWM_STATS, DB_KEY_INITIAL_DAILY_KEY );
            if ( storedInitialString != null && storedInitialString.length() > 0 )
            {
                initialDailyKey = new DailyKey( storedInitialString );
            }
        }

        {
            currentDailyKey = new DailyKey( new Date() );
            final String storedDailyStr = localDB.get( LocalDB.DB.PWM_STATS, currentDailyKey.toString() );
            if ( storedDailyStr != null && storedDailyStr.length() > 0 )
            {
                statsDaily = StatisticsBundle.input( storedDailyStr );
            }
        }

        try
        {
            localDB.put( LocalDB.DB.PWM_STATS, DB_KEY_TEMP, JavaHelper.toIsoDate( new Date() ) );
        }
        catch ( IllegalStateException e )
        {
            LOGGER.error( "unable to write to localDB, will remain closed, error: " + e.getMessage() );
            status = STATUS.CLOSED;
            return;
        }

        localDB.put( LocalDB.DB.PWM_STATS, DB_KEY_VERSION, DB_VALUE_VERSION );
        localDB.put( LocalDB.DB.PWM_STATS, DB_KEY_INITIAL_DAILY_KEY, initialDailyKey.toString() );

        {
            // setup a timer to roll over at 0 Zula and one to write current stats every 10 seconds
            executorService = JavaHelper.makeSingleThreadExecutorService( pwmApplication, this.getClass() );
            executorService.scheduleAtFixedRate( new FlushTask(), 10 * 1000, DB_WRITE_FREQUENCY.getTotalMilliseconds(), TimeUnit.MILLISECONDS );
            final TimeDuration delayTillNextZulu = TimeDuration.fromCurrent( JavaHelper.nextZuluZeroTime() );
            executorService.scheduleAtFixedRate( new NightlyTask(), delayTillNextZulu.getTotalMilliseconds(), TimeUnit.DAYS.toMillis( 1 ), TimeUnit.MILLISECONDS );
        }

        status = STATUS.OPEN;
    }

    private void writeDbValues( )
    {
        if ( localDB != null )
        {
            try
            {
                localDB.put( LocalDB.DB.PWM_STATS, DB_KEY_CUMULATIVE, statsCummulative.output() );
                localDB.put( LocalDB.DB.PWM_STATS, currentDailyKey.toString(), statsDaily.output() );

                for ( final EpsStatistic loopEpsType : EpsStatistic.values() )
                {
                    for ( final Statistic.EpsDuration loopEpsDuration : Statistic.EpsDuration.values() )
                    {
                        final String key = "EPS-" + loopEpsType.toString();
                        final String mapKey = loopEpsType.toString() + loopEpsDuration.toString();
                        final String value = JsonUtil.serialize( this.epsMeterMap.get( mapKey ) );
                        localDB.put( LocalDB.DB.PWM_STATS, key, value );
                    }
                }
            }
            catch ( LocalDBException e )
            {
                LOGGER.error( "error outputting pwm statistics: " + e.getMessage() );
            }
        }

    }

    private void resetDailyStats( )
    {
        try
        {
            final Map<String, String> emailValues = new LinkedHashMap<>();
            for ( final Statistic statistic : Statistic.values() )
            {
                final String key = statistic.getLabel( PwmConstants.DEFAULT_LOCALE );
                final String value = statsDaily.getStatistic( statistic );
                emailValues.put( key, value );
            }

            AlertHandler.alertDailyStats( pwmApplication, emailValues );
        }
        catch ( Exception e )
        {
            LOGGER.error( "error while generating daily alert statistics: " + e.getMessage() );
        }

        currentDailyKey = new DailyKey( new Date() );
        statsDaily = new StatisticsBundle();
        LOGGER.debug( "reset daily statistics" );
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
        catch ( Exception e )
        {
            LOGGER.error( "unexpected error closing: " + e.getMessage() );
        }

        JavaHelper.closeAndWaitExecutor( executorService, new TimeDuration( 3, TimeUnit.SECONDS ) );

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


    public static class DailyKey
    {
        int year;
        int day;

        public DailyKey( final Date date )
        {
            final Calendar calendar = Calendar.getInstance( TimeZone.getTimeZone( "Zulu" ) );
            calendar.setTime( date );
            year = calendar.get( Calendar.YEAR );
            day = calendar.get( Calendar.DAY_OF_YEAR );
        }

        public DailyKey( final String value )
        {
            final String strippedValue = value.substring( DB_KEY_PREFIX_DAILY.length(), value.length() );
            final String[] splitValue = strippedValue.split( "_" );
            year = Integer.parseInt( splitValue[ 0 ] );
            day = Integer.parseInt( splitValue[ 1 ] );
        }

        private DailyKey( )
        {
        }

        @Override
        public String toString( )
        {
            return DB_KEY_PREFIX_DAILY + String.valueOf( year ) + "_" + String.valueOf( day );
        }

        public DailyKey previous( )
        {
            final Calendar calendar = calendar();
            calendar.add( Calendar.HOUR, -24 );
            final DailyKey newKey = new DailyKey();
            newKey.year = calendar.get( Calendar.YEAR );
            newKey.day = calendar.get( Calendar.DAY_OF_YEAR );
            return newKey;
        }

        public Calendar calendar( )
        {
            final Calendar calendar = Calendar.getInstance( TimeZone.getTimeZone( "Zulu" ) );
            calendar.set( Calendar.YEAR, year );
            calendar.set( Calendar.DAY_OF_YEAR, day );
            return calendar;
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

            final DailyKey key = ( DailyKey ) o;

            if ( day != key.day )
            {
                return false;
            }
            if ( year != key.year )
            {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode( )
        {
            int result = year;
            result = 31 * result + day;
            return result;
        }
    }

    public void updateEps( final EpsStatistic type, final int itemCount )
    {
        for ( final Statistic.EpsDuration duration : Statistic.EpsDuration.values() )
        {
            epsMeterMap.get( type.toString() + duration.toString() ).markEvents( itemCount );
        }
    }

    public BigDecimal readEps( final EpsStatistic type, final Statistic.EpsDuration duration )
    {
        return epsMeterMap.get( type.toString() + duration.toString() ).readEventRate();
    }


    public int outputStatsToCsv( final OutputStream outputStream, final Locale locale, final boolean includeHeader )
            throws IOException
    {
        LOGGER.trace( "beginning output stats to csv process" );
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
        final Map<StatisticsManager.DailyKey, String> keys = statsManger.getAvailableKeys( PwmConstants.DEFAULT_LOCALE );
        for ( final StatisticsManager.DailyKey loopKey : keys.keySet() )
        {
            counter++;
            final StatisticsBundle bundle = statsManger.getStatBundleForKey( loopKey.toString() );
            final List<String> lineOutput = new ArrayList<>();
            lineOutput.add( loopKey.toString() );
            lineOutput.add( String.valueOf( loopKey.year ) );
            lineOutput.add( String.valueOf( loopKey.day ) );
            for ( final Statistic stat : Statistic.values() )
            {
                lineOutput.add( bundle.getStatistic( stat ) );
            }
            csvPrinter.printRecord( lineOutput );
        }

        csvPrinter.flush();
        LOGGER.trace( "completed output stats to csv process; output " + counter + " records in " + TimeDuration.fromCurrent(
                startTime ).asCompactString() );
        return counter;
    }

    public ServiceInfoBean serviceInfo( )
    {
        if ( status() == STATUS.OPEN )
        {
            return new ServiceInfoBean( Collections.singletonList( DataStorageMethod.LOCALDB ) );
        }
        else
        {
            return new ServiceInfoBean( Collections.<DataStorageMethod>emptyList() );
        }
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
            LOGGER.error( "skipping requested statistic increment of " + statistic + " due to null pwmApplication" );
            return;
        }

        final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
        if ( statisticsManager == null )
        {
            LOGGER.error( "skipping requested statistic increment of " + statistic + " due to null statisticsManager" );
            return;
        }

        if ( statisticsManager.status() != STATUS.OPEN )
        {
            LOGGER.trace(
                    "skipping requested statistic increment of " + statistic + " due to StatisticsManager being closed" );
            return;
        }

        statisticsManager.incrementValue( statistic );
    }

}
