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

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.error.PwmError;
import password.pwm.error.PwmInternalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class PwmScheduler
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmScheduler.class );

    private final PwmApplication pwmApplication;

    public PwmScheduler( final PwmApplication pwmApplication )
    {
        this.pwmApplication = Objects.requireNonNull( pwmApplication );
    }

    public void shutdown()
    {
    }

    public void immediateExecuteRunnableInNewThread(
            final Runnable runnable,
            final SessionLabel sessionLabel,
            final String threadName
    )
    {
        checkIfSchedulerClosed();

        Objects.requireNonNull( runnable );

        final ExecutorService executor = makeMultiThreadExecutor( 1, pwmApplication, sessionLabel, runnable.getClass(), threadName );

        executor.submit( runnable );
    }

    public static void scheduleDailyZuluZeroStartJob(
            final Runnable runnable,
            final ScheduledExecutorService executorService,
            final TimeDuration zuluOffset
    )
    {
        final TimeDuration delayTillNextZulu = TimeDuration.fromCurrent( nextZuluZeroTime() );
        final TimeDuration delayTillNextOffset = zuluOffset == null ? TimeDuration.ZERO : delayTillNextZulu.add( zuluOffset );
        scheduleFixedRateJob( runnable, executorService, delayTillNextOffset, TimeDuration.DAY );
    }

    public ScheduledFuture<?> scheduleJob(
            final Runnable runnable,
            final ScheduledExecutorService executor,
            final TimeDuration delay
    )
    {
        checkIfSchedulerClosed();

        Objects.requireNonNull( runnable );
        Objects.requireNonNull( executor );
        Objects.requireNonNull( delay );

        return executor.schedule( runnable, delay.asMillis(), TimeUnit.MILLISECONDS );
    }

    public <T> List<T> executeImmediateThreadPerJobAndAwaitCompletion(
            final int maxThreadCount,
            final List<Callable<T>> callables,
            final SessionLabel sessionLabel,
            final Class<?> theClass
    )
            throws PwmUnrecoverableException
    {
        checkIfSchedulerClosed();

        final ExecutorService executor = makeMultiThreadExecutor( maxThreadCount, pwmApplication, sessionLabel, theClass, null );

        final List<Future<T>> futures = callables.stream()
                .map( executor::submit )
                .toList();


        final List<T> results = new ArrayList<>();
        for ( final Future<T> f : futures )
        {
            results.add( awaitFutureCompletion( f ) );
        }

        executor.shutdownNow();
        return Collections.unmodifiableList( results );
    }

    private static <T> T awaitFutureCompletion( final Future<T> future )
            throws PwmUnrecoverableException
    {
        try
        {
            return future.get();
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


    public static void scheduleFixedRateJob(
            final Runnable runnable,
            final ScheduledExecutorService executor,
            final TimeDuration initialDelay,
            final TimeDuration frequency
    )
    {
        executor.scheduleAtFixedRate( runnable, initialDelay.asMillis(), frequency.asMillis(), TimeUnit.MILLISECONDS );
    }

    public static String makeThreadName(
            final SessionLabel sessionLabel,
            final PwmApplication pwmApplication,
            final Class<?> theClass )
    {
        return makeThreadName( sessionLabel, pwmApplication, theClass, null );
    }

    public static String makeThreadName(
            final SessionLabel sessionLabel,
            final PwmApplication pwmApplication,
            final Class<?> theClass,
            final String threadNameSuffix
    )
    {
        final String instanceName = pwmApplication == null
                ? "-"
                : pwmApplication.getInstanceID();


        return makeThreadName( sessionLabel, instanceName, theClass, threadNameSuffix );
    }

    public static String makeThreadName(
            final SessionLabel sessionLabel,
            final String instanceID,
            final Class<?> theClass,
            final String threadNameSuffix
    )
    {
        final StringBuilder output = new StringBuilder();

        output.append( PwmConstants.PWM_APP_NAME );

        if ( StringUtil.notEmpty( instanceID ) )
        {
            output.append( "-" );
            output.append( instanceID );
        }

        if ( theClass != null )
        {
            output.append( "-" );
            output.append( theClass.getSimpleName() );
        }

        if ( sessionLabel != null && !StringUtil.isEmpty( sessionLabel.getDomain() ) )
        {
            output.append( "-" );
            output.append( sessionLabel.getDomain() );
        }

        if ( !StringUtil.isEmpty( threadNameSuffix ) )
        {
            output.append( "-" );
            output.append( threadNameSuffix );
        }

        return output.toString();
    }

    public static ThreadFactory makePwmThreadFactory( final String namePrefix )
    {
        return new ThreadFactory()
        {
            private final AtomicLoopIntIncrementer counter = new AtomicLoopIntIncrementer();

            @Override
            public Thread newThread( final Runnable runnable )
            {
                final String strippedNamePrefix = StringUtil.stripEdgeChars( namePrefix, '-' );
                final Thread t = new Thread( runnable );
                t.setDaemon( true );
                t.setName( strippedNamePrefix + "-" + counter.next() );
                return t;
            }
        };
    }

    public static ThreadPoolExecutor makeMultiThreadExecutor(
            final int maxThreadCount,
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final Class<?> theClass
    )
    {
        return makeMultiThreadExecutor( maxThreadCount, pwmApplication, sessionLabel, theClass, null );
    }

    public static ThreadPoolExecutor makeMultiThreadExecutor(
            final int maxThreadCount,
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final Class<?> theClass,
            final String threadNameSuffix
    )
    {
        return makeMultiThreadExecutor( maxThreadCount, pwmApplication.getInstanceID(), sessionLabel, theClass, threadNameSuffix );
    }

    public static ThreadPoolExecutor makeMultiThreadExecutor(
            final int maxThreadCount,
            final String instanceID,
            final SessionLabel sessionLabel,
            final Class<?> theClass,
            final String threadNameSuffix
    )
    {
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(
                maxThreadCount,
                maxThreadCount,
                1, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                makePwmThreadFactory( makeThreadName( sessionLabel, instanceID, theClass, threadNameSuffix ) + "-" ) );
        executor.allowCoreThreadTimeOut( true );
        return executor;
    }

    public static ScheduledExecutorService makeBackgroundServiceExecutor(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final Class<?> clazz
    )
    {
        return makeBackgroundServiceExecutor( pwmApplication, sessionLabel, clazz, null );
    }

    public static ScheduledExecutorService makeBackgroundServiceExecutor(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final Class<?> clazz,
            final String threadNameSuffix
    )
    {
        final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                1,
                makePwmThreadFactory(
                        makeThreadName( sessionLabel, pwmApplication, clazz, threadNameSuffix ) + "-" ) );
        executor.setKeepAliveTime( 5, TimeUnit.SECONDS );
        executor.allowCoreThreadTimeOut( true );
        return executor;
    }

    private void checkIfSchedulerClosed()
    {
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

    /**
     * Execute a task within the time period specified by {@code maxWaitDuration}.  If the task exceeds the time allotted, it is
     * cancelled and the results are discarded.  The calling thread will block until the callable returns
     * a result or the {@code maxWaitDuration} is reached, whichever occurs first.
     * @param pwmApplication application to use for thread naming and other housekeeping.
     *
     * @param label thread labels.
     * @param maxWaitDuration maximum time to wait for result.
     * @param callable task to execute.
     * @param <T> return value of the callable.
     * @return The {@code callable}'s return value.
     * @throws PwmUnrecoverableException if the task times out.  Uses {@link PwmError#ERROR_TIMEOUT}.
     * @throws Throwable any throwable generated by the {@code callable}
     */
    public static <T> T executeWithTimeout(
            final PwmApplication pwmApplication,
            final SessionLabel label,
            final TimeDuration maxWaitDuration,
            final Callable<T> callable
    )
            throws PwmUnrecoverableException, Throwable
    {

        final ThreadPoolExecutor executor = PwmScheduler.makeMultiThreadExecutor(
                1, pwmApplication, label, callable.getClass() );

        try
        {
            final Future<T> future = executor.submit( callable );

            return future.get( maxWaitDuration.asMillis(), TimeUnit.MILLISECONDS );
        }
        catch ( final ExecutionException e )
        {
            throw e.getCause();
        }
        catch ( final TimeoutException e )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_TIMEOUT, "operation timed out: " + e.getMessage() );
        }
        catch ( final Exception e )
        {
            throw PwmInternalException.fromPwmException( e );
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    /**
     * Close executor and wait up to the specified TimeDuration for all executor jobs to terminate.  There is no guarantee that either all jobs will
     * terminate or the entire duration will be waited for, though the duration should not be exceeded.
     * @param executor Executor to close
     * @param timeDuration TimeDuration to wait for
     * @param pwmLogger log errors or failed closures to.
     */
    public static void closeAndWaitExecutor(
            final ExecutorService executor,
            final TimeDuration timeDuration,
            final PwmLogger pwmLogger,
            final SessionLabel sessionLabel
    )
    {
        if ( executor == null )
        {
            return;
        }

        executor.shutdownNow();

        try
        {
            executor.awaitTermination( timeDuration.asMillis(), TimeUnit.MILLISECONDS );
        }
        catch ( final InterruptedException e )
        {
            pwmLogger.error( sessionLabel, () -> "attempted to shutdown thread '" + executor + "' however shutdown was interrupted: " + e.getMessage() );
        }

        if ( pwmLogger != null && !executor.isShutdown() )
        {
            pwmLogger.error( sessionLabel, () -> "attempted to shutdown thread '" + executor + "' however thread is not shutdown" );
        }

        if ( pwmLogger != null && !executor.isTerminated() )
        {
            pwmLogger.error( sessionLabel, () -> "attempted to shutdown thread '" + executor + "' however thread is not terminated" );
        }
    }

}
