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

package password.pwm.util.localdb;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.EventRateMeter;
import password.pwm.util.PwmScheduler;
import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.MovingAverage;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * A work item queue manager.   Items submitted to the queue will eventually be worked on by the client side @code {@link ItemProcessor}.
 */
public final class WorkQueueProcessor<W extends Serializable>
{
    private static final TimeDuration SUBMIT_QUEUE_FULL_RETRY_CYCLE_INTERVAL = TimeDuration.of( 100, TimeDuration.Unit.MILLISECONDS );
    private static final TimeDuration CLOSE_RETRY_CYCLE_INTERVAL = TimeDuration.of( 5, TimeDuration.Unit.MILLISECONDS );

    private final Deque<String> queue;
    private final Settings settings;
    private final ItemProcessor<W> itemProcessor;
    private final SessionLabel sessionLabel;

    private final PwmLogger logger;

    private volatile WorkerThread workerThread;

    private final AtomicLoopIntIncrementer idGenerator = new AtomicLoopIntIncrementer();
    private Instant eldestItem = null;

    private ThreadPoolExecutor executorService;

    private final MovingAverage avgLagTime = new MovingAverage( TimeDuration.MINUTE.asDuration() );
    private final EventRateMeter sendRate = new EventRateMeter( TimeDuration.MINUTE );

    private final StatisticCounterBundle<WorkQueueStat> workQueueStats = new StatisticCounterBundle<>( WorkQueueStat.class );

    enum WorkQueueStat
    {
        preQueueSubmit,
        preQueueBypass,
        preQueueFallback,
        queueProcessItems,
    }

    public enum ProcessResult
    {
        SUCCESS,
        FAILED,
        RETRY,
        NOOP,
    }

    public WorkQueueProcessor(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final Deque<String> queue,
            final Settings settings,
            final ItemProcessor<W> itemProcessor,
            final Class<?> sourceClass
    )
    {
        this.sessionLabel = sessionLabel;
        this.settings = settings;
        this.queue = queue;
        this.itemProcessor = itemProcessor;
        this.logger = PwmLogger.getLogger( sourceClass.getName() + "_" + this.getClass().getSimpleName() );

        if ( !queue.isEmpty() )
        {
            logger.debug( () -> "opening with " + queue.size() + " items in work queue" );
        }
        logger.trace( () -> "initializing worker thread with settings " + JsonFactory.get().serialize( settings ) );

        this.workerThread = new WorkerThread();
        workerThread.setDaemon( true );
        workerThread.setName( PwmScheduler.makeThreadName( sessionLabel, pwmApplication, sourceClass ) + "-worker-" );
        workerThread.start();

        if ( settings.getPreThreads() > 0 )
        {
            final ThreadFactory threadFactory = PwmScheduler.makePwmThreadFactory( PwmScheduler.makeThreadName( sessionLabel, pwmApplication, sourceClass ), true );
            executorService = new ThreadPoolExecutor(
                    1,
                    settings.getPreThreads(),
                    1,
                    TimeUnit.MINUTES,
                    new ArrayBlockingQueue<>( settings.getPreThreads() + 1 ),
                    threadFactory
            );
            executorService.getActiveCount();
            executorService.allowCoreThreadTimeOut( true );
        }
    }

    public void close( )
    {
        if ( workerThread == null )
        {
            return;
        }

        if ( executorService != null )
        {
            executorService.shutdown();
        }

        final WorkerThread localWorkerThread = workerThread;
        workerThread = null;

        final Instant startTime = Instant.now();
        logger.debug( sessionLabel, () -> "attempting to flush queue prior to shutdown, items in queue=" + queueSize() );

        localWorkerThread.flushQueueAndClose();

        if ( localWorkerThread.isRunning() )
        {
            settings.getMaxShutdownWaitTime().pause( CLOSE_RETRY_CYCLE_INTERVAL, () -> !localWorkerThread.isRunning() );
        }

        final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );
        final String msg = "shutting down with " + queue.size() + " items remaining in work queue (" + timeDuration.asCompactString() + ")";
        if ( !queue.isEmpty() )
        {
            logger.warn( sessionLabel,  () -> msg );
        }
        else
        {
            logger.debug( sessionLabel, () -> msg );
        }
    }

    public void submitImmediate( final W workItem )
    {
        final ItemWrapper<W> itemWrapper = new ItemWrapper<>( Instant.now(), workItem, String.valueOf( idGenerator.next() ) );
        sendAndQueueIfNecessary( itemWrapper );
    }

    public void submit( final W workItem )
            throws PwmOperationalException
    {
        final ItemWrapper<W> itemWrapper = new ItemWrapper<>( Instant.now(), workItem, String.valueOf( idGenerator.next() ) );

        if ( settings.getPreThreads() < 0 )
        {
            sendAndQueueIfNecessary( itemWrapper );
            return;
        }

        if ( settings.getPreThreads() > 0 )
        {
            try
            {
                executorService.execute( ( ) -> sendAndQueueIfNecessary( itemWrapper ) );
                workQueueStats.increment( WorkQueueStat.preQueueSubmit );
            }
            catch ( final RejectedExecutionException e )
            {
                submitToQueue( itemWrapper );
                workQueueStats.increment( WorkQueueStat.preQueueBypass );
            }
        }
        else
        {
            submitToQueue( itemWrapper );
        }
    }

    private void sendAndQueueIfNecessary( final ItemWrapper<W> itemWrapper )
    {
        final Instant processStartTime = Instant.now();
        try
        {
            final ProcessResult processResult = itemProcessor.process( itemWrapper.getWorkItem() );
            if ( processResult == ProcessResult.SUCCESS )
            {
                logAndStatUpdateForSuccess( itemWrapper, TimeDuration.fromCurrent( processStartTime ) );
            }
            else if ( processResult == ProcessResult.RETRY || processResult == ProcessResult.NOOP )
            {
                workQueueStats.increment( WorkQueueStat.preQueueFallback );
                try
                {
                    submitToQueue( itemWrapper );
                }
                catch ( final Exception e )
                {
                    logger.error( sessionLabel, () -> "error submitting to work queue after executor returned retry status: " + e.getMessage() );
                }
            }
        }
        catch ( final PwmOperationalException e )
        {
            logger.error( sessionLabel, () -> "unexpected error while processing itemWrapper: " + e.getMessage(), e );
        }
    }

    private void submitToQueue( final ItemWrapper<W> itemWrapper )
            throws PwmOperationalException
    {
        if ( workerThread == null )
        {
            final String errorMsg = this.getClass().getName() + " has been closed, unable to submit new item";
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg ) );
        }

        if ( settings.getMaxEvents() <= 0 )
        {
            return;
        }

        final Instant startTime = Instant.now();
        int attempts = 1;

        final String asString = JsonFactory.get().serialize( itemWrapper );
        while ( !queue.offerLast( asString ) )
        {
            attempts++;
            final TimeDuration waitTime = TimeDuration.fromCurrent( startTime );
            if ( waitTime.isLongerThan( settings.getMaxSubmitWaitTime() ) )
            {
                final String errorMsg = "unable to submit item to worker queue after " + waitTime.asCompactString()
                        + " and " + attempts + " attempts, item=" + itemProcessor.convertToDebugString( itemWrapper.getWorkItem() );
                throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg ) );
            }
            SUBMIT_QUEUE_FULL_RETRY_CYCLE_INTERVAL.pause();
        }

        eldestItem = itemWrapper.getDate();
        workerThread.notifyWorkPending();

        if ( attempts > 1 )
        {
            logger.trace( sessionLabel, () -> "item submitted directly to queue: " + makeDebugText( itemWrapper ),
                    TimeDuration.fromCurrent( startTime ) );
        }
        else
        {
            final int finalAttempts = attempts;
            logger.debug( sessionLabel, () -> "item submitted to queue after " + finalAttempts + " attempts: "
                    + makeDebugText( itemWrapper ), TimeDuration.fromCurrent( startTime ) );
        }
    }

    public int queueSize( )
    {
        return queue.size();
    }

    public Instant eldestItem( )
    {
        return eldestItem;
    }

    private String makeDebugText( final ItemWrapper<W> itemWrapper )
    {
        final int itemsInQueue = WorkQueueProcessor.this.queueSize();

        String itemMsg;
        try
        {
            itemMsg = itemWrapper.toDebugString( itemProcessor );
        }
        catch ( final PwmOperationalException e )
        {
            itemMsg = "error";
        }

        String traceMsg = "[" + itemMsg + "]";
        if ( itemsInQueue > 0 )
        {
            traceMsg += ", " + itemsInQueue + " items in queue";
        }
        return traceMsg;
    }

    private class WorkerThread extends Thread
    {

        private final AtomicBoolean running = new AtomicBoolean( false );
        private final AtomicBoolean shutdownFlag = new AtomicBoolean( false );
        private final AtomicBoolean notifyWorkFlag = new AtomicBoolean( true );

        private Instant retryWakeupTime;

        @Override
        public void run( )
        {
            running.set( true );
            try
            {
                while ( !shutdownFlag.get() )
                {
                    processNextItem();
                    waitForWork();
                }
            }
            catch ( final Throwable t )
            {
                logger.error( sessionLabel, () -> "unexpected error processing work item queue: " + JavaHelper.readHostileExceptionMessage( t ), t );
            }

            logger.trace( sessionLabel, () -> "worker thread beginning shutdown..." );

            if ( !queue.isEmpty() )
            {
                logger.trace( sessionLabel, () -> "processing remaining " + queue.size() + " items" );

                try
                {
                    final Instant shutdownStartTime = Instant.now();
                    while ( retryWakeupTime == null && !queue.isEmpty() && TimeDuration.fromCurrent( shutdownStartTime ).isLongerThan( settings.getMaxShutdownWaitTime() ) )
                    {
                        processNextItem();
                    }
                }
                catch ( final Throwable t )
                {
                    logger.error( sessionLabel, () -> "unexpected error processing work item queue: " + JavaHelper.readHostileExceptionMessage( t ), t );
                }
            }

            logger.trace( sessionLabel, () -> "thread exiting..." );
            running.set( false );
        }

        void flushQueueAndClose( )
        {
            shutdownFlag.set( true );
            logger.trace( sessionLabel, () -> "shutdown flag set" );
            notifyWorkPending();

            // rest until not running for up to 10 seconds....
            if ( running.get() )
            {
                logger.trace( sessionLabel, () -> "running = " + running.get() );
                final Instant startTime = Instant.now();
                TimeDuration.of( 10, TimeDuration.Unit.SECONDS ).pause( CLOSE_RETRY_CYCLE_INTERVAL, () -> !running.get() );
                final TimeDuration waitTime = TimeDuration.fromCurrent( startTime );
                logger.trace( sessionLabel, () -> "waited " + waitTime.asCompactString() + " workQueueSize=" + queue.size() + " running=" + running.get() );
            }
        }

        void notifyWorkPending( )
        {
            notifyWorkFlag.set( true );
            LockSupport.unpark( this );
        }

        private void waitForWork( )
        {
            if ( !shutdownFlag.get() )
            {
                if ( retryWakeupTime != null )
                {
                    while ( retryWakeupTime.isAfter( Instant.now() ) && !shutdownFlag.get() )
                    {
                        LockSupport.parkUntil( this, retryWakeupTime.toEpochMilli() );
                    }
                    retryWakeupTime = null;
                }
                else
                {
                    if ( queue.isEmpty() && !notifyWorkFlag.get() )
                    {
                        eldestItem = null;
                        LockSupport.park( this );
                    }
                }
            }

            notifyWorkFlag.set( false );
        }

        public boolean isRunning( )
        {
            return running.get();
        }

        void processNextItem( )
        {
            final Instant processStartTime = Instant.now();
            final String nextStrValue = queue.peekFirst();
            if ( nextStrValue == null )
            {
                return;
            }

            final ItemWrapper<W> itemWrapper;
            try
            {
                itemWrapper = JsonFactory.get().deserialize( nextStrValue, ItemWrapper.class );
                if ( TimeDuration.fromCurrent( itemWrapper.getDate() ).isLongerThan( settings.getRetryDiscardAge() ) )
                {
                    removeQueueTop();
                    logger.warn( () -> "discarding queued item due to age, item=" + makeDebugText( itemWrapper ) );
                    return;
                }
            }
            catch ( final Throwable e )
            {
                removeQueueTop();
                logger.warn( () -> "discarding stored record due to parsing error: " + e.getMessage() + ", record=" + nextStrValue );
                return;
            }

            final ProcessResult processResult;
            try
            {
                workQueueStats.increment( WorkQueueStat.queueProcessItems );
                processResult = itemProcessor.process( itemWrapper.getWorkItem() );
                if ( processResult == null )
                {
                    removeQueueTop();
                    logger.warn( sessionLabel, () -> "itemProcessor.process() returned null, removing; item=" + makeDebugText( itemWrapper ) );
                }
                else
                {
                    switch ( processResult )
                    {
                        case FAILED:
                        {
                            removeQueueTop();
                            logger.error( sessionLabel, () -> "discarding item after process failure, item=" + makeDebugText( itemWrapper ) );
                        }
                        break;

                        case RETRY:
                        {
                            retryWakeupTime = Instant.ofEpochMilli( System.currentTimeMillis() + settings.getRetryInterval().asMillis() );
                            logger.debug( sessionLabel, () -> "will retry item after failure, item=" + makeDebugText( itemWrapper ) );
                        }
                        break;

                        case SUCCESS:
                        {
                            removeQueueTop();
                            logAndStatUpdateForSuccess( itemWrapper, TimeDuration.fromCurrent( processStartTime ) );
                        }
                        break;

                        case NOOP:
                            break;


                        default:
                            throw new IllegalStateException( "unexpected processResult type " + processResult );
                    }
                }
            }
            catch ( final Throwable e )
            {
                if ( !shutdownFlag.get() )
                {
                    removeQueueTop();
                    logger.error( sessionLabel, () -> "unexpected error while processing work queue: " + e.getMessage() );
                }
            }

        }

        private void removeQueueTop( )
        {
            queue.removeFirst();
            retryWakeupTime = null;
        }
    }

    private static class ItemWrapper<W extends Serializable> implements Serializable
    {
        @SerializedName( "t" )
        private final Instant timestamp;

        @SerializedName( "m" )
        private final String item;

        @SerializedName( "c" )
        private final String className;

        @SerializedName( "i" )
        private final String id;

        ItemWrapper( final Instant submitDate, final W workItem, final String itemId )
        {
            this.timestamp = submitDate;
            this.item = JsonFactory.get().serialize( workItem );
            this.className = workItem.getClass().getName();
            this.id = itemId;
        }

        Instant getDate( )
        {
            return timestamp;
        }

        W getWorkItem( ) throws PwmOperationalException
        {
            try
            {
                final Class clazz = Class.forName( className );
                final Object o = JsonFactory.get().deserialize( item, clazz );
                return ( W ) o;
            }
            catch ( final Exception e )
            {
                throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_INTERNAL, "unexpected error deserializing work queue item: " + e.getMessage() ) );
            }
        }

        String getId( )
        {
            return id;
        }

        String toDebugString( final ItemProcessor<W> itemProcessor ) throws PwmOperationalException
        {
            final Map<String, String> debugOutput = new LinkedHashMap<>();
            debugOutput.put( "date", getDate().toString() );
            debugOutput.put( "id", getId() );
            debugOutput.put( "item", itemProcessor.convertToDebugString( getWorkItem() ) );
            return StringUtil.mapToString( debugOutput, "=", "," );
        }
    }

    /**
     * Implementation of {@link ItemProcessor} must be included with the construction of a {@link WorkQueueProcessor}.
     */
    public interface ItemProcessor<W extends Serializable>
    {
        ProcessResult process( W workItem );

        String convertToDebugString( W workItem );
    }

    @Value
    @Builder
    public static class Settings implements Serializable
    {
        @Builder.Default
        private int maxEvents = 1000;

        @Builder.Default
        private int preThreads = 0;

        @Builder.Default
        private TimeDuration maxSubmitWaitTime = TimeDuration.of( 5, TimeDuration.Unit.SECONDS );

        @Builder.Default
        private TimeDuration retryInterval = TimeDuration.of( 30, TimeDuration.Unit.SECONDS );

        @Builder.Default
        private TimeDuration retryDiscardAge = TimeDuration.of( 1, TimeDuration.Unit.HOURS );

        @Builder.Default
        private TimeDuration maxShutdownWaitTime = TimeDuration.of( 30, TimeDuration.Unit.SECONDS );
    }

    private void logAndStatUpdateForSuccess( final ItemWrapper<W> itemWrapper, final TimeDuration processDuration )
            throws PwmOperationalException
    {
        final TimeDuration lagTime = TimeDuration.fromCurrent( itemWrapper.getDate() );
        avgLagTime.update( lagTime.asMillis() );
        sendRate.markEvents( 1 );
        logger.trace( sessionLabel, () -> "processed item=" + makeDebugText( itemWrapper ) + "; lagTime=" + lagTime.asCompactString()
                + "; " + StringUtil.mapToString( debugInfo() ), processDuration );
    }

    public Map<String, String> debugInfo( )
    {
        final Map<String, String> output = new HashMap<>();
        output.put( "avgLagTime", TimeDuration.fromDuration( avgLagTime.getAverageAsDuration() ).asCompactString() );
        output.put( "sendRate", sendRate.readEventRate().setScale( 2, RoundingMode.DOWN ) + "/s" );
        if ( executorService != null )
        {
            output.put( "preQueueThreads", String.valueOf( executorService.getActiveCount() ) );
        }
        if ( workerThread != null )
        {
            output.put( "postQueueThreads", workerThread.isRunning() ? "1" : "0" );
        }
        output.putAll( workQueueStats.debugStats() );
        return Collections.unmodifiableMap( output );
    }
}
