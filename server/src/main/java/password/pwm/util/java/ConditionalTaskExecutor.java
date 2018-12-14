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

package password.pwm.util.java;

import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

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

    private Runnable task;
    private Supplier<Boolean> predicate;
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Execute the task if the conditional has been met.  Exceptions when running the task will be logged but not returned.
     */
    public void conditionallyExecuteTask( )
    {
        lock.lock();
        try
        {
            if ( predicate.get() )
            {
                try
                {
                    task.run();
                }
                catch ( Throwable t )
                {
                    LOGGER.warn( "unexpected error executing conditional task: " + t.getMessage(), t );
                }

            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public ConditionalTaskExecutor( final Runnable task, final Supplier<Boolean> predicate )
    {
        this.task = task;
        this.predicate = predicate;
    }

    public static ConditionalTaskExecutor forPeriodicTask( final Runnable task, final TimeDuration timeDuration )
    {
        return new ConditionalTaskExecutor( task, new TimeDurationPredicate( timeDuration ) );
    }

    public static class TimeDurationPredicate implements Supplier<Boolean>
    {
        private final TimeDuration timeDuration;
        private volatile Instant nextExecuteTimestamp;

        public TimeDurationPredicate( final TimeDuration timeDuration )
        {
            this.timeDuration = timeDuration;
            setNextTimeFromNow( timeDuration );

        }

        public TimeDurationPredicate( final long value, final TimeDuration.Unit unit )
        {
            this( TimeDuration.of( value, unit ) );
        }

        public TimeDurationPredicate setNextTimeFromNow( final TimeDuration duration )
        {
            nextExecuteTimestamp = Instant.now().plus( duration.asMillis(), ChronoUnit.MILLIS );
            return this;
        }

        @Override
        public Boolean get()
        {
            if ( Instant.now().isAfter( nextExecuteTimestamp ) )
            {
                setNextTimeFromNow( timeDuration );
                return true;
            }
            return false;
        }
    }
}
