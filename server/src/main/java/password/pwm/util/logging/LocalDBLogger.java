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

package password.pwm.util.logging;

import password.pwm.AppAttribute;
import password.pwm.PwmApplication;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.PwmScheduler;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.MiscUtil;
import password.pwm.util.java.PwmNumberFormat;
import password.pwm.util.java.StatisticAverageBundle;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.localdb.LocalDBStoredQueue;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Saves a recent copy of PWM events in the pwmDB.
 *
 * @author Jason D. Rivard
 */
public class LocalDBLogger extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LocalDBLogger.class );

    private static final SessionLabel SESSION_LABEL = SessionLabel.SYSTEM_LABEL;

    private final LocalDBLoggerSettings settings;
    private final LocalDBStoredQueue localDBListQueue;
    private final Queue<PwmLogMessage> tempMemoryEventQueue;
    private final ScheduledExecutorService cleanerService;
    private final ScheduledExecutorService writerService;
    private final AtomicBoolean cleanOnWriteFlag = new AtomicBoolean( false );
    private final AtomicBoolean flushScheduled = new AtomicBoolean( true );
    private final PwmLogLevel minimumLevel;

    private final StatisticCounterBundle<CounterStat> stats = new StatisticCounterBundle<>( CounterStat.class );
    private final StatisticAverageBundle<AverageStat> averages = new StatisticAverageBundle<>( AverageStat.class );

    private final ConditionalTaskExecutor debugOutputter = ConditionalTaskExecutor.forPeriodicTask(
            this::periodicDebugOutput, TimeDuration.MINUTE.asDuration() );

    private static final int LOG_OUTPUT_INCREMENTS = 10_000;
    private final AtomicLong lastLogOutput = new AtomicLong( LOG_OUTPUT_INCREMENTS );

    enum CounterStat
    {
        BufferFlushCycles,
        CleanerCycles,
        EventsRemoved,
        EventsWritten,
    }

    enum AverageStat
    {
        avgFlushLatency,
        avgFlushCount,
    }

    private boolean hasShownReadError = false;

    private static final String STORAGE_FORMAT_VERSION = "4";

    public LocalDBLogger(
            final PwmApplication pwmApplication,
            final LocalDB localDB,
            final PwmLogLevel minimumLevel,
            final LocalDBLoggerSettings settings
    )
            throws LocalDBException
    {
        Objects.requireNonNull( localDB, "localDB can not be null" );

        this.minimumLevel = Objects.requireNonNull( minimumLevel );

        this.settings = settings == null
                ? LocalDBLoggerSettings.builder().build().applyValueChecks()
                : settings.applyValueChecks();

        this.localDBListQueue = LocalDBStoredQueue.createLocalDBStoredQueue(
                pwmApplication,
                localDB,
                LocalDB.DB.EVENTLOG_EVENTS
        );

        if ( this.settings.getMaxEvents() == 0 )
        {
            LOGGER.info( SESSION_LABEL, () -> "maxEvents set to zero, clearing LocalDBLogger history and LocalDBLogger will remain closed" );
            localDBListQueue.clear();
            throw new IllegalArgumentException( "maxEvents=0, will remain closed" );
        }

        tempMemoryEventQueue = new ArrayBlockingQueue<>( this.settings.getMaxBufferSize(), true );

        if ( pwmApplication != null )
        {
            pwmApplication.readAppAttribute( AppAttribute.LOCALDB_LOGGER_STORAGE_FORMAT, String.class )
                    .ifPresent( ( currentFormat ) ->
                    {
                        if ( !STORAGE_FORMAT_VERSION.equals( currentFormat ) )
                        {
                            LOGGER.warn( SESSION_LABEL, () -> "localdb logger is using outdated format, clearing existing records (existing='"
                                    + currentFormat + "', current='" + STORAGE_FORMAT_VERSION + "')" );

                            localDBListQueue.clear();
                            pwmApplication.writeAppAttribute( AppAttribute.LOCALDB_LOGGER_STORAGE_FORMAT, STORAGE_FORMAT_VERSION );
                        }
                    } );
        }

        setStatus( STATUS.OPEN );

        cleanerService = PwmScheduler.makeBackgroundServiceExecutor(
                pwmApplication, getSessionLabel(), LocalDBLogger.class, "cleaner" );

        writerService = PwmScheduler.makeBackgroundServiceExecutor(
                pwmApplication, getSessionLabel(), LocalDBLogger.class, "writer" );

        cleanerService.scheduleAtFixedRate( new CleanupTask(), 0, this.settings.cleanerFrequency().asMillis(), TimeUnit.MILLISECONDS );

        cleanOnWriteFlag.set( tempMemoryEventQueue.size() >= this.settings.getMaxEvents() );

        setStatus( STATUS.OPEN );
    }


    public Optional<Instant> getTailDate( )
    {
        final PwmLogEvent loopEvent;
        if ( localDBListQueue.isEmpty() )
        {
            return Optional.empty();
        }
        try
        {
            loopEvent = readEvent( localDBListQueue.getLast() );
            if ( loopEvent != null )
            {
                final Instant tailDate = loopEvent.getTimestamp();
                if ( tailDate != null )
                {
                    return Optional.of( tailDate );
                }
            }
        }
        catch ( final Exception e )
        {
            LOGGER.error( SESSION_LABEL, () -> "unexpected error attempting to determine tail event timestamp: " + e.getMessage() );
        }

        return Optional.empty();
    }

    private void scheduleNextFlush()
    {
        if ( tempMemoryEventQueue.size() > settings.getMaxBufferSize() / 2 )
        {
            writerService.schedule( new FlushTask(), 0, TimeUnit.SECONDS );
            return;
        }

        if ( flushScheduled.get() )
        {
            return;
        }

        flushScheduled.set( true );
        writerService.schedule( new FlushTask(), 5, TimeUnit.SECONDS );
    }


    private Map<String, String> debugStats( )
    {
        final Map<String, String> debugData = new TreeMap<>();
        {
            final Instant tailAge = getTailDate().orElse( null );
            debugData.put( "EventsTailAge", tailAge == null ? "n/a" : TimeDuration.fromCurrent( tailAge ).asCompactString() );
        }

        final TimeDuration latency = TimeDuration.of( (long) averages.getAverage( AverageStat.avgFlushLatency ), TimeDuration.Unit.MILLISECONDS );
        debugData.put( "EventsStored", String.valueOf( localDBListQueue.size() ) );
        debugData.put( "ConfiguredMaxEvents", MiscUtil.forDefaultLocale().format( settings.getMaxEvents() ) );
        debugData.put( "ConfiguredMaxAge", settings.getMaxAge().asCompactString() );
        debugData.put( "BufferAverageLatency", latency.asCompactString() );
        debugData.put( "BufferAverageSize", averages.getFormattedAverage( AverageStat.avgFlushCount ) );
        debugData.put( "BufferItemCount", String.valueOf( tempMemoryEventQueue.size() ) );

        debugData.putAll( averages.debugStats() );

        return Collections.unmodifiableMap( debugData );
    }

    private void periodicDebugOutput()
    {
        if ( lastLogOutput.get() + stats.get( CounterStat.EventsWritten ) > LOG_OUTPUT_INCREMENTS )
        {
            LOGGER.trace( SESSION_LABEL, () -> "periodic debug output: " + StringUtil.mapToString( debugStats() ) );
            lastLogOutput.set( stats.get( CounterStat.EventsWritten ) );
        }
    }

    @Override
    public void shutdownImpl( )
    {
        final Instant startTime = Instant.now();

        final int flushedEvents;
        if ( status() != STATUS.CLOSED )
        {
            LOGGER.trace( SESSION_LABEL, () -> "LocalDBLogger closing" );
            flushedEvents = tempMemoryEventQueue.size();
            if ( cleanerService != null )
            {
                cleanerService.shutdown();
            }
            writerService.execute( new FlushTask() );
            JavaHelper.closeAndWaitExecutor( writerService, TimeDuration.SECONDS_10 );
        }
        else
        {
            flushedEvents = 0;
        }

        setStatus( STATUS.CLOSED );

        if ( flushedEvents > 0 )
        {
            LOGGER.trace( SESSION_LABEL, () -> "LocalDBLogger close completed (flushed during close: " + flushedEvents + ")", TimeDuration.fromCurrent( startTime ) );
        }
        else
        {
            LOGGER.trace( SESSION_LABEL, () -> "LocalDBLogger close completed", TimeDuration.fromCurrent( startTime ) );
        }
    }

    public int getStoredEventCount( )
    {
        return localDBListQueue.size();
    }

    private int determineTailRemovalCount( )
    {
        final int maxTrailSize = settings.getMaxTrimSize();

        final int currentItemCount = localDBListQueue.size();

        // must keep at least one position populated
        if ( currentItemCount <= LocalDBLoggerSettings.MINIMUM_MAXIMUM_EVENTS )
        {
            return 0;
        }

        // purge excess events by count
        if ( currentItemCount > settings.getMaxEvents() )
        {
            return Math.min( maxTrailSize, currentItemCount - settings.getMaxEvents() );
        }

        // purge the tail if it is missing or has invalid timestamp
        final Optional<Instant> tailTimestamp = getTailDate();
        if ( tailTimestamp.isEmpty() )
        {
            return 1;
        }

        // purge excess events by age;
        final TimeDuration tailAge = TimeDuration.fromCurrent( tailTimestamp.get() );
        if ( tailAge.isLongerThan( settings.getMaxAge() ) )
        {
            final long maxRemovalPercentageOfSize = getStoredEventCount() / maxTrailSize;
            if ( maxRemovalPercentageOfSize > 100 )
            {
                return maxTrailSize;
            }
            else
            {
                return 1;
            }
        }
        return 0;
    }


    public enum EventType
    {
        User, System, Both
    }

    public LocalDBSearchResults readStoredEvents(
            final LocalDBSearchQuery searchParameters
    )
    {
        return new LocalDBSearchResults( this, localDBListQueue.iterator(), searchParameters );
    }

    PwmLogEvent readEvent( final String value )
    {
        try
        {
            return PwmLogEvent.fromEncodedString( value );
        }
        catch ( final Throwable e )
        {
            if ( !hasShownReadError )
            {
                hasShownReadError = true;
                LOGGER.error( SESSION_LABEL, () -> "error reading localDBLogger event: " + e.getMessage() );
            }
        }
        return null;
    }

    boolean checkEventForParams(
            final PwmLogEvent event,
            final LocalDBSearchQuery searchParameters
    )
    {
        if ( event == null )
        {
            return false;
        }

        boolean eventMatchesParams = true;

        if ( searchParameters.getMinimumLevel() != null )
        {
            if ( event.getLevel().compareTo( searchParameters.getMinimumLevel() ) <= -1 )
            {
                eventMatchesParams = false;
            }
        }

        Pattern pattern = null;
        try
        {
            if ( searchParameters.getUsername() != null && searchParameters.getUsername().length() > 0 )
            {
                pattern = Pattern.compile( searchParameters.getUsername() );
            }
        }
        catch ( final PatternSyntaxException e )
        {
            LOGGER.trace( SESSION_LABEL, () -> "invalid regex syntax for " + searchParameters.getUsername() + ", reverting to plaintext search" );
        }

        if ( pattern != null )
        {
            final Matcher matcher = pattern.matcher( event.getUsername() == null ? "" : event.getUsername() );
            if ( !matcher.find() )
            {
                eventMatchesParams = false;
            }
        }
        else if ( eventMatchesParams && ( searchParameters.getUsername() != null && searchParameters.getUsername().length() > 1 ) )
        {
            final String eventUsername = event.getUsername();
            if ( eventUsername == null || !eventUsername.equalsIgnoreCase( searchParameters.getUsername() ) )
            {
                eventMatchesParams = false;
            }
        }

        {
            final String searchParamText = searchParameters.getText();
            if ( eventMatchesParams && !StringUtil.isEmpty( searchParamText ) )
            {
                final String eventMessage = event.getMessage();
                if ( eventMessage != null && eventMessage.length() > 0 )
                {
                    final String textLowercase = searchParamText.toLowerCase();

                    boolean isAMatch = false;
                    if ( eventMessage.toLowerCase().contains( textLowercase ) )
                    {
                        isAMatch = true;
                    }
                    else if ( event.getTopic() != null && event.getTopic().toLowerCase().contains( textLowercase ) )
                    {
                        isAMatch = true;
                    }
                    else if ( event.getTopic() != null && event.getTopic().length() > 0 )
                    {
                        if ( event.getTopic().toLowerCase().contains( textLowercase ) )
                        {
                            isAMatch = true;
                        }
                    }
                    if ( !isAMatch )
                    {
                        eventMatchesParams = false;
                    }
                }
            }
        }

        if ( searchParameters.getEventType() != null )
        {
            if ( searchParameters.getEventType() == EventType.System )
            {
                if ( !StringUtil.isEmpty( event.getUsername() ) )
                {
                    eventMatchesParams = false;
                }
            }
            else if ( searchParameters.getEventType() == EventType.User )
            {
                if ( StringUtil.isEmpty( event.getUsername() ) )
                {
                    eventMatchesParams = false;
                }
            }
        }

        return eventMatchesParams;
    }

    public void writeEvent( final PwmLogMessage event )
    {
        if ( ignoreLogEvent( event ) )
        {
            return;
        }

        scheduleNextFlush();

        final Instant startTime = Instant.now();
        while ( !tempMemoryEventQueue.offer( event ) )
        {
            if ( TimeDuration.fromCurrent( startTime ).isLongerThan( settings.getMaxBufferWaitTime() ) )
            {
                LOGGER.warn( SESSION_LABEL, () -> "discarded event after waiting max buffer wait time of " + settings.getMaxBufferWaitTime().asCompactString() );
                return;
            }
            TimeDuration.of( 100, TimeDuration.Unit.MILLISECONDS ).pause();
        }
    }

    private boolean ignoreLogEvent( final PwmLogMessage event )
    {
        return status() != STATUS.OPEN
                || settings.getMaxEvents() <= 0
                ||         !event.getLevel().isGreaterOrSameAs( minimumLevel );
    }

    private void flushEvents( )
    {
        if ( tempMemoryEventQueue.isEmpty() )
        {
            return;
        }

        Instant eldestEntry = Instant.now();
        final List<String> localBuffer = new ArrayList<>( Math.min( tempMemoryEventQueue.size(), settings.getMaxBufferSize() ) );
        while ( localBuffer.size() < ( settings.getMaxBufferSize() ) - 1 && !tempMemoryEventQueue.isEmpty() )
        {
            final PwmLogMessage pwmLogEvent = tempMemoryEventQueue.poll();
            localBuffer.add( pwmLogEvent.toLogEvent().toEncodedString() );
            eldestEntry = pwmLogEvent.getTimestamp();
        }

        try
        {
            if ( cleanOnWriteFlag.get() )
            {
                localDBListQueue.removeLast( localBuffer.size() );
                stats.increment( CounterStat.EventsRemoved, localBuffer.size() );
            }
            localDBListQueue.addAll( localBuffer );

            stats.increment( CounterStat.BufferFlushCycles );
            stats.increment( CounterStat.EventsWritten, localBuffer.size() );
            averages.update( AverageStat.avgFlushLatency, TimeDuration.fromCurrent( eldestEntry ).asDuration() );
            averages.update( AverageStat.avgFlushCount, localBuffer.size() );
        }
        catch ( final Exception e )
        {
            LOGGER.error( SESSION_LABEL, () -> "error writing to localDBLogger: " + e.getMessage(), e );
        }

        debugOutputter.conditionallyExecuteTask();
    }

    private class FlushTask implements Runnable
    {
        @Override
        public void run( )
        {
            try
            {
                while ( !tempMemoryEventQueue.isEmpty() && status() == STATUS.OPEN )
                {
                    flushEvents();
                }
            }
            catch ( final Throwable t )
            {
                LOGGER.fatal( () -> "localDBLogger flush thread has failed: " + t.getMessage(), t );
            }

            flushScheduled.set( false );
        }
    }

    private class CleanupTask implements Runnable
    {
        @Override
        public void run( )
        {
            try
            {
                int cleanupCount = 1;
                while ( cleanupCount > 0 && ( status() == STATUS.OPEN && localDBListQueue.getLocalDB().status() == LocalDB.Status.OPEN ) )
                {
                    cleanupCount = determineTailRemovalCount();
                    if ( cleanupCount > 0 )
                    {
                        cleanOnWriteFlag.set( true );
                        final Instant startTime = Instant.now();
                        localDBListQueue.removeLast( cleanupCount );
                        stats.increment( CounterStat.EventsRemoved, cleanupCount );
                        final TimeDuration purgeTime = TimeDuration.fromCurrent( startTime );
                        final TimeDuration pauseTime = TimeDuration.of( JavaHelper.rangeCheck( 20, 2000, ( int ) purgeTime.asMillis() ), TimeDuration.Unit.MILLISECONDS );
                        pauseTime.pause();
                    }
                }
                stats.increment( CounterStat.CleanerCycles );
            }
            catch ( final Exception e )
            {
                LOGGER.fatal( () -> "unexpected error during LocalDBLogger log event cleanup: " + e.getMessage(), e );
            }
            cleanOnWriteFlag.set( localDBListQueue.size() >= settings.getMaxEvents() );
        }
    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        final List<HealthRecord> healthRecords = new ArrayList<>();

        if ( status() != STATUS.OPEN )
        {
            healthRecords.add( HealthRecord.forMessage(
                    DomainID.systemId(),
                    HealthMessage.LocalDBLogger_Closed,
                    status().toString() ) );
            return healthRecords;
        }

        final int eventCount = getStoredEventCount();
        if ( eventCount > settings.getMaxEvents() + 5000 )
        {
            final PwmNumberFormat numberFormat = MiscUtil.forDefaultLocale();
            healthRecords.add( HealthRecord.forMessage(
                    DomainID.systemId(),
                    HealthMessage.LocalDBLogger_HighRecordCount,
                    numberFormat.format( eventCount ),
                    numberFormat.format( settings.getMaxEvents() ) )
            );
        }

        final Optional<Instant> tailDate = getTailDate();
        if ( tailDate.isPresent() )
        {
            final TimeDuration timeDuration = TimeDuration.fromCurrent( tailDate.get() );
            final TimeDuration maxTimeDuration = settings.getMaxAge().add( TimeDuration.HOUR );
            if ( timeDuration.isLongerThan( maxTimeDuration ) )
            {
                // older than max age + 1h
                healthRecords.add( HealthRecord.forMessage(
                        DomainID.systemId(),
                        HealthMessage.LocalDBLogger_OldRecordPresent,
                        timeDuration.asCompactString(),
                        maxTimeDuration.asCompactString() ) );
            }
        }

        return healthRecords;
    }

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID ) throws PwmException
    {
        // actual init happens from constructor when loaded by PwmApplication
        return STATUS.OPEN;
    }

    public String sizeToDebugString( )
    {
        final int storedEvents = this.getStoredEventCount();
        final int maxEvents = settings.getMaxEvents();
        final double percentFull = ( double ) storedEvents / ( double ) maxEvents * 100f;
        final NumberFormat numberFormat = NumberFormat.getNumberInstance();
        numberFormat.setMaximumFractionDigits( 3 );
        numberFormat.setMinimumFractionDigits( 3 );

        return this.getStoredEventCount() + " / " + maxEvents + " (" + numberFormat.format( percentFull ) + "%)";
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return ServiceInfoBean.builder()
                .storageMethod( DataStorageMethod.LOCALDB )
                .debugProperties( debugStats() )
                .build();
    }

}

