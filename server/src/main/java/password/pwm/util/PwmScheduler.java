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

package password.pwm.util;

import org.jetbrains.annotations.NotNull;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PwmScheduler
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmScheduler.class );
    private static final AtomicLoopIntIncrementer THREAD_ID_COUNTER = new AtomicLoopIntIncrementer();

    private final ScheduledExecutorService applicationExecutorService;
    private final PwmApplication pwmApplication;

    public PwmScheduler( final PwmApplication pwmApplication )
    {
        this.pwmApplication = Objects.requireNonNull( pwmApplication );
        applicationExecutorService = makeSingleThreadExecutorService( pwmApplication.getInstanceID(), this.getClass() );
    }

    public void shutdown()
    {
        applicationExecutorService.shutdown();
    }

    public Future<?> immediateExecuteRunnableInNewThread(
            final Runnable runnable,
            final String threadName
    )
    {
        return immediateExecuteCallableInNewThread( Executors.callable( runnable ), threadName );
    }

    public <V> Future<V> immediateExecuteCallableInNewThread(
            final Callable<V> callable,
            final String threadName
    )
    {
        if ( checkIfSchedulerClosed() )
        {
            return null;
        }

        Objects.requireNonNull( callable );

        final String name = "runtime thread #" + THREAD_ID_COUNTER.next() + " " + threadName;

        final ScheduledExecutorService executor = makeSingleThreadExecutorService( pwmApplication.getInstanceID(), callable.getClass() );

        final Callable<V> runnableWrapper = () ->
        {
            final Instant itemStartTime = Instant.now();
            LOGGER.trace( () -> "started " + name );
            try
            {
                final V result = callable.call();
                LOGGER.trace( () -> "completed " + name, () -> TimeDuration.fromCurrent( itemStartTime ) );
                executor.shutdown();
                return result;
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "error running scheduled immediate task: " + name + ", error: " + e.getMessage(), e );
                throw e;
            }
        };

        return executor.submit( runnableWrapper );
    }

    public void scheduleDailyZuluZeroStartJob(
            final Runnable runnable,
            final ExecutorService executorService,
            final TimeDuration offset
    )
    {
        final TimeDuration delayTillNextZulu = TimeDuration.fromCurrent( nextZuluZeroTime() );
        final TimeDuration delayTillNextOffset = delayTillNextZulu.add( offset );
        scheduleFixedRateJob( runnable, executorService, delayTillNextOffset, TimeDuration.DAY );
    }

    public Future<?> scheduleJob(
            final Runnable runnable,
            final ExecutorService executor,
            final TimeDuration delay
    )
    {
        if ( checkIfSchedulerClosed() )
        {
            return null;
        }

        Objects.requireNonNull( runnable );
        Objects.requireNonNull( executor );
        Objects.requireNonNull( delay );

        if ( applicationExecutorService.isShutdown() )
        {
            throw new IllegalStateException( "can not schedule job with shutdown scheduler" );
        }

        final FutureRunner wrappedRunner = new FutureRunner( runnable, executor );
        applicationExecutorService.schedule( wrappedRunner, delay.asMillis(), TimeUnit.MILLISECONDS );
        return wrappedRunner.getFuture();
    }

    public void executeImmediateThreadPerJobAndAwaitCompletion(
            final List<Callable<?>> runnableList,
            final String threadNames
    )
            throws PwmUnrecoverableException
    {
        if ( checkIfSchedulerClosed() )
        {
            return;
        }


        final List<Future<?>> futures = new ArrayList<>();
        for ( final Callable<?> callable : runnableList )
        {
            futures.add( this.immediateExecuteCallableInNewThread( () ->
            {
                callable.call();
                return null;
            }, threadNames ) );
        }

        for ( final Future<?> f : futures )
        {
            awaitFutureCompletion( f );
        }
    }

    private static void awaitFutureCompletion( final Future<?> future )
            throws PwmUnrecoverableException
    {
        try
        {
            future.get();
        }
        catch ( final InterruptedException e )
        {
            final String msg = "error in thread, error: " + e.getMessage();
            LOGGER.warn( () -> msg, e );
            throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, msg );
        }
        catch ( final ExecutionException e )
        {
            final String msg = "error in thread, error: " + e.getMessage();
            final Throwable realException = e.getCause();
            if ( realException instanceof PwmUnrecoverableException )
            {
                throw ( PwmUnrecoverableException ) e.getCause();
            }
            throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, msg );
        }
    }


    public void scheduleFixedRateJob(
            final Runnable runnable,
            final ExecutorService executor,
            final TimeDuration initialDelay,
            final TimeDuration frequency
    )
    {
        if ( checkIfSchedulerClosed() )
        {
            return;
        }


        if ( initialDelay != null )
        {
            applicationExecutorService.schedule( new FutureRunner( runnable, executor ), initialDelay.asMillis(), TimeUnit.MILLISECONDS );
        }

        final Runnable jobWithNextScheduler = () ->
        {
            new FutureRunner( runnable, executor ).run();
            scheduleFixedRateJob( runnable, executor, null, frequency );
        };

        applicationExecutorService.schedule(  jobWithNextScheduler, frequency.asMillis(), TimeUnit.MILLISECONDS );
    }

    public static ExecutorService makeBackgroundExecutor(
            final PwmApplication pwmApplication,
            final Class clazz
    )
    {
        if ( pwmApplication.getPwmScheduler().checkIfSchedulerClosed() )
        {
            return null;
        }

        final ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                10, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                makePwmThreadFactory(
                        makeThreadName( pwmApplication, clazz ) + "-",
                        true
                ) );
        executor.allowCoreThreadTimeOut( true );
        return executor;
    }


    public static String makeThreadName( final PwmApplication pwmApplication, final Class theClass )
    {
        String instanceName = "-";
        if ( pwmApplication != null )
        {
            instanceName = pwmApplication.getInstanceID();
        }

        return makeThreadName( instanceName, theClass );
    }

    public static String makeThreadName( final String instanceID, final Class theClass )
    {
        String instanceName = "-";
        if ( StringUtil.notEmpty( instanceID ) )
        {
            instanceName = instanceID;
        }

        return PwmConstants.PWM_APP_NAME + "-" + instanceName + "-" + theClass.getSimpleName();
    }

    public static ThreadFactory makePwmThreadFactory( final String namePrefix, final boolean daemon )
    {
        return new ThreadFactory()
        {
            private final ThreadFactory realThreadFactory = Executors.defaultThreadFactory();

            @Override
            public Thread newThread( final Runnable runnable )
            {
                final Thread t = realThreadFactory.newThread( runnable );
                t.setDaemon( daemon );
                if ( namePrefix != null )
                {
                    final String newName = namePrefix + t.getName();
                    t.setName( newName );
                }
                return t;
            }
        };
    }

    public static ScheduledExecutorService makeSingleThreadExecutorService(
            final PwmApplication pwmApplication,
            final Class theClass
    )
    {
        return makeSingleThreadExecutorService( pwmApplication.getInstanceID(), theClass );
    }

    public static ScheduledExecutorService makeSingleThreadExecutorService(
            final String instanceID,
            final Class theClass
    )
    {
        return Executors.newSingleThreadScheduledExecutor(
                makePwmThreadFactory(
                        makeThreadName( instanceID, theClass ) + "-",
                        true
                ) );
    }

    private static class FutureRunner implements Runnable
    {
        private final Runnable runnable;
        private final ExecutorService executor;
        private volatile Future innerFuture;
        private volatile boolean hasFailed;

        enum Flag
        {
            ShutdownExecutorAfterExecution,
        }

        FutureRunner( final Runnable runnable, final ExecutorService executor )
        {
            this.runnable = runnable;
            this.executor = executor;
        }

        Future getFuture()
        {
            return new Future()
            {
                @Override
                public boolean cancel( final boolean mayInterruptIfRunning )
                {
                    return false;
                }

                @Override
                public boolean isCancelled()
                {
                    return hasFailed;
                }

                @Override
                public boolean isDone()
                {
                    return hasFailed || ( innerFuture != null && innerFuture.isDone() );
                }

                @Override
                public Object get()
                {
                    return null;
                }

                @Override
                public Object get( final long timeout, @NotNull final TimeUnit unit )
                {
                    return null;
                }
            };
        }

        @Override
        public void run()
        {
            try
            {
                if ( !executor.isShutdown() )
                {
                    innerFuture = executor.submit( runnable );
                }
                else
                {
                    hasFailed = true;
                }
            }
            catch ( final Throwable t )
            {
                LOGGER.error( () -> "unexpected error running scheduled job: " + t.getMessage(), t );
                hasFailed = true;
            }
        }
    }

    private boolean checkIfSchedulerClosed()
    {
        return false;
        /*
        return pwmApplication.getApplicationMode() == PwmApplicationMode.READ_ONLY
                || pwmApplication.getPwmEnvironment().isInternalRuntimeInstance()
                || applicationExecutorService.isShutdown();
        */
    }

    public static Instant nextZuluZeroTime( )
    {
        final Calendar nextZuluMidnight = GregorianCalendar.getInstance( TimeZone.getTimeZone( "Zulu" ) );
        nextZuluMidnight.set( Calendar.HOUR_OF_DAY, 0 );
        nextZuluMidnight.set( Calendar.MINUTE, 0 );
        nextZuluMidnight.set( Calendar.SECOND, 0 );
        nextZuluMidnight.add( Calendar.HOUR, 24 );
        return nextZuluMidnight.toInstant();
    }
}
