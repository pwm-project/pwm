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
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
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

    private LocalDB localDB;

    private StatisticsBundleKey currentDailyKey = StatisticsBundleKey.forToday();
    private StatisticsBundleKey initialDailyKey = StatisticsBundleKey.forToday();

    private final StatisticsBundle statsCurrent = new StatisticsBundle();
    private final Map<EpsKey, EventRateMeter> epsMeterMap;
    private StatisticsBundle statsDaily = new StatisticsBundle();
    private StatisticsBundle statsCumulative = new StatisticsBundle();


    public StatisticsService( )
    {
        epsMeterMap = EpsKey.allKeys().stream()
                .map( key -> Map.entry( key, new EventRateMeter( key.epsDuration().getTimeDuration().asDuration() ) ) )
                .collect( Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue ) );
    }

    public void incrementValue( final Statistic statistic )
    {
        statsCurrent.incrementValue( statistic );
        statsDaily.incrementValue( statistic );
        statsCumulative.incrementValue( statistic );
    }

    public void updateAverageValue( final AvgStatistic statistic, final long value )
    {
        statsCurrent.updateAverageValue( statistic, value );
        statsDaily.updateAverageValue( statistic, value );
        statsCumulative.updateAverageValue( statistic, value );
    }

    public Map<StatisticsBundleKey, String> getStatHistory( final Statistic statistic, final int days )
    {
        final Map<StatisticsBundleKey, String> returnMap = new LinkedHashMap<>();
        StatisticsBundleKey loopKey = currentDailyKey;
        int counter = days;
        while ( counter > 0 )
        {
            final StatisticsBundleKey finalKey = loopKey;
            getStatBundleForKey( loopKey ).ifPresent( bundle ->
            {
                final String value = bundle.getStatistic( statistic );
                returnMap.put( finalKey, value );
            } );

            loopKey = loopKey.previous();
            counter--;
        }
        return returnMap;
    }

    public StatisticsBundle getCumulativeBundle()
    {
        return getStatBundleForKey( StatisticsBundleKey.CUMULATIVE ).orElseThrow();
    }

    public StatisticsBundle getCurrentBundle()
    {
        return getStatBundleForKey( StatisticsBundleKey.CURRENT ).orElseThrow();
    }

    public Optional<StatisticsBundle> getStatBundleForKey( final StatisticsBundleKey key )
    {
        Objects.requireNonNull( key );

        if ( StatisticsBundleKey.CUMULATIVE == key )
        {
            return Optional.of( statsCumulative );
        }

        if ( StatisticsBundleKey.CURRENT == key )
        {
            return Optional.of( statsCurrent );
        }

        if ( Objects.equals( currentDailyKey, key ) )
        {
            return Optional.of( statsDaily );
        }

        if ( localDB == null )
        {
            return Optional.empty();
        }

        try
        {
            final Optional<String> storedStat = localDB.get( LocalDB.DB.PWM_STATS, key.toString() );
            return storedStat.map( StatisticsBundle::input );
        }
        catch ( final LocalDBException e )
        {
            LOGGER.error( () -> "error retrieving stored stat for " + key + ": " + e.getMessage() );
        }

        return Optional.empty();
    }

    StatisticsBundleKey getCurrentDailyKey()
    {
        return currentDailyKey;
    }

    StatisticsBundleKey getInitialDailyKey()
    {
        return initialDailyKey;
    }

    public SortedSet<StatisticsBundleKey> allKeys()
    {
        return StatisticsUtils.allKeys( this );
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
                    statsCumulative = StatisticsBundle.input( storedCumulativeBundleSir.get() );
                }
                catch ( final Exception e )
                {
                    LOGGER.warn( getSessionLabel(), () -> "error loading saved stored cumulative statistics: " + e.getMessage() );
                }
            }
        }

        {
            final Optional<String> storedInitialString = localDB.get( LocalDB.DB.PWM_STATS, DB_KEY_INITIAL_DAILY_KEY );
            storedInitialString.ifPresent( s -> initialDailyKey = StatisticsBundleKey.fromString( s ) );
        }

        {
            currentDailyKey = StatisticsBundleKey.forToday();
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
                dbData.put( DB_KEY_CUMULATIVE, statsCumulative.output() );
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
        currentDailyKey = StatisticsBundleKey.forToday();
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

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return status() == STATUS.OPEN
                ? ServiceInfoBean.builder().storageMethod( DataStorageMethod.LOCALDB ).build()
                : ServiceInfoBean.builder().build();
    }

}
