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

package password.pwm.util;

import org.jetbrains.annotations.NotNull;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Objects;
import java.util.TimeZone;
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
    private final ScheduledExecutorService applicationExecutorService;
    private final String instanceID;

    public PwmScheduler( final String instanceID )
    {
        this.instanceID = instanceID;
        applicationExecutorService = makeSingleThreadExecutorService( instanceID, this.getClass() );
    }

    public void shutdown()
    {
        applicationExecutorService.shutdown();
    }

    public Future immediateExecuteInNewThread(
            final Runnable runnable
    )
    {
        Objects.requireNonNull( runnable );

        final ScheduledExecutorService executor = makeSingleThreadExecutorService( instanceID, runnable.getClass() );

        if ( applicationExecutorService.isShutdown() )
        {
            return null;
        }

        final WrappedRunner wrappedRunner = new WrappedRunner( runnable, executor, WrappedRunner.Flag.ShutdownExecutorAfterExecution );
        applicationExecutorService.submit( wrappedRunner );
        return wrappedRunner.getFuture();
    }

    public void scheduleDailyZuluZeroStartJob(
            final Runnable runnable,
            final ExecutorService executorService,
            final TimeDuration offset
    )
    {
        final TimeDuration delayTillNextZulu = TimeDuration.fromCurrent( nextZuluZeroTime() );
        final TimeDuration delayTillNextOFfiset = delayTillNextZulu.add( offset );
        scheduleFixedRateJob( runnable, executorService, delayTillNextOFfiset, TimeDuration.DAY );
    }

    public Future scheduleJob(
            final Runnable runnable,
            final ExecutorService executor,
            final TimeDuration delay
    )
    {
        Objects.requireNonNull( runnable );
        Objects.requireNonNull( executor );
        Objects.requireNonNull( delay );

        if ( applicationExecutorService.isShutdown() )
        {
            return null;
        }

        final WrappedRunner wrappedRunner = new WrappedRunner( runnable, executor );
        applicationExecutorService.schedule( wrappedRunner, delay.asMillis(), TimeUnit.MILLISECONDS );
        return wrappedRunner.getFuture();
    }

    public void scheduleFixedRateJob(
            final Runnable runnable,
            final ExecutorService executor,
            final TimeDuration initialDelay,
            final TimeDuration frequency
    )
    {
        if ( applicationExecutorService.isShutdown() )
        {
            return;
        }

        if ( initialDelay != null )
        {
            applicationExecutorService.schedule( new WrappedRunner( runnable, executor ), initialDelay.asMillis(), TimeUnit.MILLISECONDS );
        }

        final Runnable jobWithNextScheduler = () ->
        {
            new WrappedRunner( runnable, executor ).run();
            scheduleFixedRateJob( runnable, executor, null, frequency );
        };

        applicationExecutorService.schedule(  jobWithNextScheduler, frequency.asMillis(), TimeUnit.MILLISECONDS );
    }

    public static ExecutorService makeBackgroundExecutor(
            final PwmApplication pwmApplication,
            final Class clazz
    )
    {
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
        if ( !StringUtil.isEmpty( instanceID ) )
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

    private static class WrappedRunner implements Runnable
    {
        private final Runnable runnable;
        private final ExecutorService executor;
        private final Flag[] flags;
        private volatile Future innerFuture;
        private volatile boolean hasFailed;

        enum Flag
        {
            ShutdownExecutorAfterExecution,
        }

        WrappedRunner( final Runnable runnable, final ExecutorService executor, final Flag... flags )
        {
            this.runnable = runnable;
            this.executor = executor;
            this.flags = flags;
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
                    return hasFailed || innerFuture != null && innerFuture.isDone();
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

            if ( JavaHelper.enumArrayContainsValue( flags, Flag.ShutdownExecutorAfterExecution ) )
            {
                executor.shutdown();
            }
        }
    }

    public static Instant nextZuluZeroTime( )
    {
        final Calendar nextZuluMidnight = GregorianCalendar.getInstance( TimeZone.getTimeZone( "Zulu" ) );
        nextZuluMidnight.set( Calendar.HOUR_OF_DAY, 0 );
        nextZuluMidnight.set( Calendar.MINUTE, 0 );
        nextZuluMidnight.set( Calendar.SECOND, 0 );
        nextZuluMidnight.add( Calendar.HOUR, 24 );
        return nextZuluMidnight.getTime().toInstant();
    }
}
