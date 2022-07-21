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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PwmScheduler
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmScheduler.class );
    private static final AtomicLoopIntIncrementer THREAD_ID_COUNTER = new AtomicLoopIntIncrementer();

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

    public void scheduleDailyZuluZeroStartJob(
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
                .collect( Collectors.toUnmodifiableList() );


        final List<T> results = new ArrayList<>();
        for ( final Future<T> f : futures )
        {
            results.add( awaitFutureCompletion( f ) );
        }

        executor.shutdown();
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


    public void scheduleFixedRateJob(
            final Runnable runnable,
            final ScheduledExecutorService executor,
            final TimeDuration initialDelay,
            final TimeDuration frequency
    )
    {
        checkIfSchedulerClosed();

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
                1,
                maxThreadCount,
                1, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                makePwmThreadFactory(
                        makeThreadName( sessionLabel, instanceID, theClass, threadNameSuffix ) + "-",
                        true
                ) );
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
                        makeThreadName( sessionLabel, pwmApplication, clazz, threadNameSuffix ) + "-",
                        true
                ) );
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
}
