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

package password.pwm.util.java;

import java.time.Duration;
import java.time.Instant;
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
public interface ConditionalTaskExecutor
{
    /**
     * Execute the task if the conditional has been met.  Exceptions when running the task will be logged but not returned.
     */
    void conditionallyExecuteTask( );

    static ConditionalTaskExecutor forPeriodicTask(
            final Runnable task,
            final Duration timeDuration
    )
    {
        return makeThreadSafeExecutor( task, makeTimeDurationPredicate( timeDuration ) );
    }

    private static ConditionalTaskExecutor makeThreadSafeExecutor( final Runnable task, final BooleanSupplier predicate )
    {
        Objects.requireNonNull( task );
        Objects.requireNonNull( predicate );

        final Lock lock = new ReentrantLock();
        return () ->
        {
            lock.lock();
            try
            {
                if ( predicate.getAsBoolean() )
                {
                    task.run();
                }
            }
            finally
            {
                lock.unlock();
            }
        };
    }

    private static BooleanSupplier makeTimeDurationPredicate( final Duration timeDuration )
    {
        Objects.requireNonNull( timeDuration );
        final Instant firstTimestamp = Instant.now().plus( timeDuration );
        final AtomicReference<Instant> nextExecuteTimestamp = new AtomicReference<>( firstTimestamp );

        return () ->
        {
            final Instant now = Instant.now();
            if ( now.isAfter( nextExecuteTimestamp.get() ) )
            {
                nextExecuteTimestamp.set( now.plus( timeDuration ) );
                return true;
            }
            return false;
        };
    }
}
