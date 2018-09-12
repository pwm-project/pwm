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

package password.pwm.util.logging;

import password.pwm.PwmApplication;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmNumberFormat;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.localdb.LocalDBStoredQueue;

import java.io.IOException;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Saves a recent copy of PWM events in the pwmDB.
 *
 * @author Jason D. Rivard
 */
public class LocalDBLogger implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LocalDBLogger.class );

    private final LocalDB localDB;
    private final LocalDBLoggerSettings settings;
    private final LocalDBStoredQueue localDBListQueue;
    private final Queue<PwmLogEvent> eventQueue;
    private final ScheduledExecutorService cleanerService;
    private final ScheduledExecutorService writerService;
    private final AtomicBoolean cleanOnWriteFlag = new AtomicBoolean( false );

    private volatile STATUS status = STATUS.NEW;
    private boolean hasShownReadError = false;

    private static final String STORAGE_FORMAT_VERSION = "3";

    public LocalDBLogger( final PwmApplication pwmApplication, final LocalDB localDB, final LocalDBLoggerSettings settings )
            throws LocalDBException
    {
        if ( localDB == null )
        {
            throw new NullPointerException( "localDB can not be null" );
        }

        final Instant startTime = Instant.now();
        status = STATUS.OPENING;
        this.settings = settings;
        this.localDB = localDB;
        this.localDBListQueue = LocalDBStoredQueue.createLocalDBStoredQueue(
                pwmApplication,
                localDB,
                LocalDB.DB.EVENTLOG_EVENTS
        );

        if ( settings.getMaxEvents() == 0 )
        {
            LOGGER.info( "maxEvents set to zero, clearing LocalDBLogger history and LocalDBLogger will remain closed" );
            localDBListQueue.clear();
            throw new IllegalArgumentException( "maxEvents=0, will remain closed" );
        }

        if ( localDB == null )
        {
            throw new IllegalArgumentException( "LocalDB is not available" );
        }

        eventQueue = new ArrayBlockingQueue<>( settings.getMaxBufferSize(), true );

        if ( pwmApplication != null )
        {
            final String currentFormat = pwmApplication.readAppAttribute( PwmApplication.AppAttribute.LOCALDB_LOGGER_STORAGE_FORMAT, String.class );
            if ( !STORAGE_FORMAT_VERSION.equals( currentFormat ) )
            {
                LOGGER.warn( "localdb logger is using outdated format, clearing existing records (existing='"
                        + currentFormat + "', current='" + STORAGE_FORMAT_VERSION + "')" );

                localDBListQueue.clear();
                pwmApplication.writeAppAttribute( PwmApplication.AppAttribute.LOCALDB_LOGGER_STORAGE_FORMAT, STORAGE_FORMAT_VERSION );
            }
        }

        status = STATUS.OPEN;

        cleanerService = Executors.newSingleThreadScheduledExecutor(
                JavaHelper.makePwmThreadFactory(
                        JavaHelper.makeThreadName( pwmApplication, this.getClass() ) + "-cleaner-",
                        true
                ) );

        writerService = Executors.newSingleThreadScheduledExecutor(
                JavaHelper.makePwmThreadFactory(
                        JavaHelper.makeThreadName( pwmApplication, this.getClass() ) + "-writer-",
                        true
                ) );

        cleanerService.scheduleAtFixedRate( new CleanupTask(), 0, 1, TimeUnit.MINUTES );
        writerService.scheduleWithFixedDelay( new FlushTask(), 0, 103, TimeUnit.MILLISECONDS );

        cleanOnWriteFlag.set( eventQueue.size() >= settings.getMaxEvents() );

        final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );
        LOGGER.info( "open in " + timeDuration.asCompactString() + ", " + debugStats() );
    }


    public Instant getTailDate( )
    {
        final PwmLogEvent loopEvent;
        if ( localDBListQueue.isEmpty() )
        {
            return null;
        }
        try
        {
            loopEvent = readEvent( localDBListQueue.getLast() );
            if ( loopEvent != null )
            {
                final Instant tailDate = loopEvent.getDate();
                if ( tailDate != null )
                {
                    return tailDate;
                }
            }
        }
        catch ( Exception e )
        {
            LOGGER.error( "unexpected error attempting to determine tail event timestamp: " + e.getMessage() );
        }

        return null;
    }


    private String debugStats( )
    {
        final StringBuilder sb = new StringBuilder();
        sb.append( "events=" ).append( localDBListQueue.size() );
        final Instant tailAge = getTailDate();
        sb.append( ", tailAge=" ).append( tailAge == null ? "n/a" : TimeDuration.fromCurrent( tailAge ).asCompactString() );
        sb.append( ", maxEvents=" ).append( settings.getMaxEvents() );
        sb.append( ", maxAge=" ).append( settings.getMaxAge().asCompactString() );
        sb.append( ", localDBSize=" ).append( StringUtil.formatDiskSize( FileSystemUtility.getFileDirectorySize( localDB.getFileLocation() ) ) );
        return sb.toString();
    }

    public void close( )
    {
        if ( status != STATUS.CLOSED )
        {
            LOGGER.debug( "LocalDBLogger closing... (" + debugStats() + ")" );
            if ( cleanerService != null )
            {
                cleanerService.shutdown();
            }
            writerService.execute( new FlushTask() );
            JavaHelper.closeAndWaitExecutor( writerService, TimeDuration.SECONDS_10 );
        }
        status = STATUS.CLOSED;

        LOGGER.debug( "LocalDBLogger close completed (" + debugStats() + ")" );
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
        final Instant tailTimestamp = getTailDate();
        if ( tailTimestamp == null )
        {
            return 1;
        }

        // purge excess events by age;
        final TimeDuration tailAge = TimeDuration.fromCurrent( tailTimestamp );
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
        catch ( Throwable e )
        {
            if ( !hasShownReadError )
            {
                hasShownReadError = true;
                LOGGER.error( "error reading localDBLogger event: " + e.getMessage() );
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
        catch ( PatternSyntaxException e )
        {
            LOGGER.trace( "invalid regex syntax for " + searchParameters.getUsername() + ", reverting to plaintext search" );
        }
        if ( pattern != null )
        {
            final Matcher matcher = pattern.matcher( event.getActor() == null ? "" : event.getActor() );
            if ( !matcher.find() )
            {
                eventMatchesParams = false;
            }
        }
        else if ( eventMatchesParams && ( searchParameters.getUsername() != null && searchParameters.getUsername().length() > 1 ) )
        {
            final String eventUsername = event.getActor();
            if ( eventUsername == null || !eventUsername.equalsIgnoreCase( searchParameters.getUsername() ) )
            {
                eventMatchesParams = false;
            }
        }

        if ( eventMatchesParams && ( searchParameters.getText() != null && searchParameters.getText().length() > 0 ) )
        {
            final String eventMessage = event.getMessage();
            final String textLowercase = searchParameters.getText().toLowerCase();
            boolean isAMatch = false;
            if ( eventMessage != null && eventMessage.length() > 0 )
            {
                if ( eventMessage.toLowerCase().contains( textLowercase ) )
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

        if ( searchParameters.getEventType() != null )
        {
            if ( searchParameters.getEventType() == EventType.System )
            {
                if ( event.getActor() != null && event.getActor().length() > 0 )
                {
                    eventMatchesParams = false;
                }
            }
            else if ( searchParameters.getEventType() == EventType.User )
            {
                if ( event.getActor() == null || event.getActor().length() < 1 )
                {
                    eventMatchesParams = false;
                }
            }
        }

        return eventMatchesParams;
    }

    public void writeEvent( final PwmLogEvent event )
    {
        if ( status == STATUS.OPEN )
        {
            if ( settings.getMaxEvents() > 0 )
            {
                final Instant startTime = Instant.now();
                while ( !eventQueue.offer( event ) )
                {
                    if ( TimeDuration.fromCurrent( startTime ).isLongerThan( settings.getMaxBufferWaitTime() ) )
                    {
                        LOGGER.warn( "discarded event after waiting max buffer wait time of " + settings.getMaxBufferWaitTime().asCompactString() );
                        return;
                    }
                    JavaHelper.pause( 100 );
                }
            }
        }
    }

    private void flushEvents( )
    {
        final List<String> localBuffer = new ArrayList<>();
        while ( localBuffer.size() < ( settings.getMaxBufferSize() ) - 1 && !eventQueue.isEmpty() )
        {
            final PwmLogEvent pwmLogEvent = eventQueue.poll();
            try
            {
                localBuffer.add( pwmLogEvent.toEncodedString() );
            }
            catch ( IOException e )
            {
                LOGGER.warn( "error flushing events to localDB: " + e.getMessage(), e );
            }
        }

        try
        {
            if ( cleanOnWriteFlag.get() )
            {
                localDBListQueue.removeLast( localBuffer.size() );
            }
            localDBListQueue.addAll( localBuffer );
        }
        catch ( Exception e )
        {
            LOGGER.error( "error writing to localDBLogger: " + e.getMessage(), e );
        }
    }

    private class FlushTask implements Runnable
    {
        @Override
        public void run( )
        {
            try
            {
                while ( !eventQueue.isEmpty() && status == STATUS.OPEN )
                {
                    flushEvents();
                }
            }
            catch ( Throwable t )
            {
                LOGGER.fatal( "localDBLogger flush thread has failed: " + t.getMessage(), t );
            }
        }
    }

    private class CleanupTask implements Runnable
    {
        public void run( )
        {
            try
            {
                int cleanupCount = 1;
                while ( cleanupCount > 0 && ( status == STATUS.OPEN && localDBListQueue.getLocalDB().status() == LocalDB.Status.OPEN ) )
                {
                    cleanupCount = determineTailRemovalCount();
                    if ( cleanupCount > 0 )
                    {
                        cleanOnWriteFlag.set( true );
                        final Instant startTime = Instant.now();
                        localDBListQueue.removeLast( cleanupCount );
                        final TimeDuration purgeTime = TimeDuration.fromCurrent( startTime );
                        final TimeDuration pauseTime = new TimeDuration( JavaHelper.rangeCheck( 20, 2000, ( int ) purgeTime.getTotalMilliseconds() ) );
                        pauseTime.pause();
                    }
                }
            }
            catch ( Exception e )
            {
                LOGGER.fatal( "unexpected error during LocalDBLogger log event cleanup: " + e.getMessage(), e );
            }
            cleanOnWriteFlag.set( localDBListQueue.size() >= settings.getMaxEvents() );
        }
    }

    public STATUS status( )
    {
        return status;
    }

    public List<HealthRecord> healthCheck( )
    {
        final List<HealthRecord> healthRecords = new ArrayList<>();

        if ( status != STATUS.OPEN )
        {
            healthRecords.add( HealthRecord.forMessage( HealthMessage.LocalDBLogger_NOTOPEN, status.toString() ) );
            return healthRecords;
        }

        final int eventCount = getStoredEventCount();
        if ( eventCount > settings.getMaxEvents() + 5000 )
        {
            final PwmNumberFormat numberFormat = PwmNumberFormat.forDefaultLocale();
            healthRecords.add( HealthRecord.forMessage(
                    HealthMessage.LocalDBLogger_HighRecordCount,
                    numberFormat.format( eventCount ),
                    numberFormat.format( settings.getMaxEvents() ) )
            );
        }

        final Instant tailDate = getTailDate();
        if ( tailDate != null )
        {
            final TimeDuration timeDuration = TimeDuration.fromCurrent( tailDate );
            final TimeDuration maxTimeDuration = settings.getMaxAge().add( TimeDuration.HOUR );
            if ( timeDuration.isLongerThan( maxTimeDuration ) )
            {
                // older than max age + 1h
                healthRecords.add( HealthRecord.forMessage( HealthMessage.LocalDBLogger_OldRecordPresent, timeDuration.asCompactString(), maxTimeDuration.asCompactString() ) );
            }
        }

        return healthRecords;
    }

    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
    }


    public String sizeToDebugString( )
    {
        final int storedEvents = this.getStoredEventCount();
        final int maxEvents = settings.getMaxEvents();
        final double percentFull = ( double ) storedEvents / ( double ) maxEvents * 100f;
        final NumberFormat numberFormat = NumberFormat.getNumberInstance();
        numberFormat.setMaximumFractionDigits( 3 );
        numberFormat.setMinimumFractionDigits( 3 );

        return String.valueOf( this.getStoredEventCount() ) + " / " + maxEvents + " (" + numberFormat.format( percentFull ) + "%)";
    }

    public ServiceInfoBean serviceInfo( )
    {
        return new ServiceInfoBean( Collections.singletonList( DataStorageMethod.LOCALDB ) );
    }

}

