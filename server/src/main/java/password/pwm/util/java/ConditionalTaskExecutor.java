/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.util.java;

import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/**
 * <p>Executes a predefined task if a conditional has occurred.  Both the task and the conditional must be supplied by the caller.
 * All processing occurs in the current thread, no new threads will be created.</p>
 *
 * <p>The user of this class must periodically call {@code conditionallyExecuteTask(}) or the task will never be run.  Because of this
 * reliance, the conditional is only evaluated during execution of {@code conditionallyExecuteTask()} so the conditional on its own is not
 * a strictly reliable indicator of how frequently the task will execute.</p>
 */
public class ConditionalTaskExecutor
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ConditionalTaskExecutor.class );

    private final Runnable task;
    private final BooleanSupplier predicate;
    private final Lock lock = new ReentrantLock();

    /**
     * Execute the task if the conditional has been met.  Exceptions when running the task will be logged but not returned.
     */
    public void conditionallyExecuteTask( )
    {
        lock.lock();
        try
        {
            if ( predicate.getAsBoolean() )
            {
                try
                {
                    task.run();
                }
                catch ( final Throwable t )
                {
                    LOGGER.warn( () -> "unexpected error executing conditional task: " + t.getMessage(), t );
                }

            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public ConditionalTaskExecutor( final Runnable task, final BooleanSupplier predicate )
    {
        this.task = Objects.requireNonNull( task );
        this.predicate = Objects.requireNonNull( predicate );
    }

    public static ConditionalTaskExecutor forPeriodicTask( final Runnable task, final TimeDuration timeDuration )
    {
        return new ConditionalTaskExecutor( task, new TimeDurationPredicate( timeDuration ) );
    }

    public static ConditionalTaskExecutor forPeriodicTask(
            final Runnable task,
            final TimeDuration timeDuration,
            final TimeDuration firstExecutionDelay )
    {
        return new ConditionalTaskExecutor( task, new TimeDurationPredicate( timeDuration, firstExecutionDelay ) );
    }

    private static class TimeDurationPredicate implements BooleanSupplier
    {
        private final TimeDuration timeDuration;
        private final AtomicReference<Instant> nextExecuteTimestamp = new AtomicReference<>();

        TimeDurationPredicate( final TimeDuration timeDuration )
        {
            this.timeDuration = timeDuration;
            setNextTimeFromNow( timeDuration );
        }

        TimeDurationPredicate( final TimeDuration timeDuration, final TimeDuration firstExecutionDelay )
        {
            this.timeDuration = timeDuration;
            setNextTimeFromNow( firstExecutionDelay );
        }

        private void setNextTimeFromNow( final TimeDuration duration )
        {
            nextExecuteTimestamp.set( Instant.now().plus( duration.asMillis(), ChronoUnit.MILLIS ) );
        }

        @Override
        public boolean getAsBoolean()
        {
            if ( Instant.now().isAfter( nextExecuteTimestamp.get() ) )
            {
                setNextTimeFromNow( timeDuration );
                return true;
            }
            return false;
        }
    }
}
