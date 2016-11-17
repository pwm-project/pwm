/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

package password.pwm.util;

import java.util.concurrent.TimeUnit;

public class TransactionSizeCalculator {

    private final Settings settings;

    private volatile int transactionSize;
    private volatile long lastDuration = 1;

    public TransactionSizeCalculator(final Settings settings) {
        this.settings = settings;
        reset();
    }

    public void reset() {
        transactionSize = settings.getMinTransactions();
        lastDuration = settings.getDurationGoal().getTotalMilliseconds();
    }

    public void recordLastTransactionDuration(final long duration) {
        recordLastTransactionDuration(new TimeDuration(duration));
    }

    public void pause() {
        Helper.pause(Math.min(lastDuration,settings.getDurationGoal().getTotalMilliseconds() * 2));
    }

    public void recordLastTransactionDuration(final TimeDuration duration)
    {
        lastDuration = duration.getTotalMilliseconds();
        final long durationGoalMs = settings.getDurationGoal().getTotalMilliseconds();
        final long difference = Math.abs(duration.getTotalMilliseconds() - durationGoalMs);
        final int closeThreshold = (int)(durationGoalMs * .15f);

        int newTransactionSize;
        if (duration.isShorterThan(settings.getDurationGoal())) {
            if (difference > closeThreshold) {
                newTransactionSize = ((int) (transactionSize + (transactionSize * 0.1)) + 1);
            } else {
                newTransactionSize = transactionSize + 1;
            }
        } else if (duration.isLongerThan(settings.getDurationGoal())) {
            if (difference > (10 * durationGoalMs)) {
                newTransactionSize = settings.getMinTransactions();
            } else if (difference > (2 * durationGoalMs)) {
                newTransactionSize = transactionSize / 2;
            } else if (difference > closeThreshold) {
                newTransactionSize = ((int) (transactionSize - (transactionSize * 0.1)) - 1);
            } else {
                newTransactionSize = transactionSize - 1;
            }
        } else {
            newTransactionSize = transactionSize;
        }

        newTransactionSize = Math.min(newTransactionSize, settings.getMaxTransactions());
        newTransactionSize = Math.max(newTransactionSize, settings.getMinTransactions());
        this.transactionSize = newTransactionSize;
    }

    public int getTransactionSize() {
        return transactionSize;
    }

    public static class Settings {
        private final TimeDuration durationGoal;
        private final int maxTransactions;
        private final int minTransactions;

        private Settings(final TimeDuration durationGoal, final int maxTransactions, final int minTransactions) {
            this.durationGoal = durationGoal;
            this.maxTransactions = maxTransactions;
            this.minTransactions = minTransactions;

            if (minTransactions < 1) {
                throw new IllegalArgumentException("minTransactions must be a positive integer");
            }

            if (maxTransactions < 1) {
                throw new IllegalArgumentException("maxTransactions must be a positive integer");
            }

            if (minTransactions > maxTransactions) {
                throw new IllegalArgumentException("minTransactions must be less than maxTransactions");
            }

            if (durationGoal == null) {
                throw new IllegalArgumentException("durationGoal must not be null");
            }

            if (durationGoal.getTotalMilliseconds() < 1) {
                throw new IllegalArgumentException("durationGoal must be greater than 0ms");
            }
        }



        public TimeDuration getDurationGoal() {
            return durationGoal;
        }

        public int getMaxTransactions() {
            return maxTransactions;
        }

        public int getMinTransactions() {
            return minTransactions;
        }
    }

    public static class SettingsBuilder {
        private TimeDuration durationGoal = new TimeDuration(100, TimeUnit.MILLISECONDS);
        private int maxTransactions = 5003;
        private int minTransactions = 3;

        public SettingsBuilder setDurationGoal(final TimeDuration durationGoal) {
            this.durationGoal = durationGoal;
            return this;
        }

        public SettingsBuilder setMaxTransactions(final int maxTransactions) {
            this.maxTransactions = maxTransactions;
            return this;
        }

        public SettingsBuilder setMinTransactions(final int minTransactions) {
            this.minTransactions = minTransactions;
            return this;
        }

        public Settings createSettings() {
            return new Settings(durationGoal, maxTransactions, minTransactions);
        }
    }
}
