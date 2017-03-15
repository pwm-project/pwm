/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Executes a predefined task if a conditional has occurred.  Both the task and the conditional must be supplied by the caller.
 * All processing occurs in the current thread, no new threads will be created.
 *
 * The user of this class must periodically call {@code conditionallyExecuteTask(}) or the task will never be run.  Because of this
 * reliance, the conditional is only evaluated during execution of {@code conditionallyExecuteTask()} so the conditional on its own is not
 * a strictly reliable indicator of how frequently the task will execute.
 */
public class ConditionalTaskExecutor {
    private static final PwmLogger LOGGER = PwmLogger.forClass(ConditionalTaskExecutor.class);

    private Runnable task;
    private Predicate predicate;

    /**
     * Execute the task if the conditional has been met.  Exceptions when running the task will be logged but not returned.
     */
    public void conditionallyExecuteTask() {
        if (predicate.test(null)) {
            try {
                task.run();
            } catch (Throwable t) {
                LOGGER.warn("unexpected error executing conditional task: " + t.getMessage(), t);
            }

        }
    }

    public ConditionalTaskExecutor(final Runnable task, final Predicate predicate) {
        this.task = task;
        this.predicate = predicate;
    }


    public static class TimeDurationPredicate implements Predicate {
        private final TimeDuration timeDuration;
        private long nextExecuteTimestamp;

        public TimeDurationPredicate(final TimeDuration timeDuration) {
            this.timeDuration = timeDuration;
            nextExecuteTimestamp = System.currentTimeMillis() + timeDuration.getTotalMilliseconds();
        }

        public TimeDurationPredicate(final long value, final TimeUnit unit) {
            this(new TimeDuration(value, unit));
        }

        public TimeDurationPredicate setNextTimeFromNow(final long value, final TimeUnit unit) {
            nextExecuteTimestamp = System.currentTimeMillis() + unit.toMillis(value);
            return this;
        }

        @Override
        public boolean test(final Object o) {
            if (nextExecuteTimestamp <= System.currentTimeMillis()) {
                nextExecuteTimestamp = System.currentTimeMillis() + timeDuration.getTotalMilliseconds();
                return true;
            }
            return false;
        }
    }
}
