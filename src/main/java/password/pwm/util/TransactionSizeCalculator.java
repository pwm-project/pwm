/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

public class TransactionSizeCalculator {

    private final TimeDuration setting_Goal;
    private final TimeDuration setting_OutOfRange;
    private final int setting_MaxTransactions;
    private final int setting_MinTransactions;
    private final int setting_MaxOutOfRangeCount = 3;

    private final int initialTransactionSize;

    private volatile int transactionSize;
    private volatile int outOfRangeCount;

    public TransactionSizeCalculator(
            final long goalTimeMS,
            final int minTransactions,
            final int maxTransactions
    )
    {
        this(goalTimeMS, minTransactions, maxTransactions, minTransactions + 1);
    }

    public TransactionSizeCalculator(
            final long goalTimeMS,
            final int minTransactions,
            final int maxTransactions,
            final int initialTransactionSize
    )
    {
        this.setting_Goal = new TimeDuration(goalTimeMS);
        this.setting_OutOfRange = new TimeDuration(setting_Goal.getTotalMilliseconds() * 5);
        this.setting_MaxTransactions = maxTransactions;
        this.setting_MinTransactions = minTransactions;
        {
            int initialSize = initialTransactionSize;
            if (initialSize > maxTransactions) {
                initialSize = maxTransactions;
            }
            if (initialSize < minTransactions) {
                initialSize = minTransactions;
            }
            this.initialTransactionSize = initialSize;
        }
        transactionSize = initialTransactionSize;
    }

    public void reset() {
        transactionSize = initialTransactionSize;
    }

    public void recordLastTransactionDuration(final long duration) {
        recordLastTransactionDuration(new TimeDuration(duration));
    }

    public void recordLastTransactionDuration(final TimeDuration duration)
    {
        final long difference = Math.abs(duration.getTotalMilliseconds() - setting_Goal.getTotalMilliseconds());

        int newTransactionSize;
        boolean outOfRange = false;
        if (duration.isShorterThan(setting_Goal)) {
            if (difference > 100) {
                newTransactionSize = ((int) (transactionSize + (transactionSize * 0.1)) + 1);
            } else {
                newTransactionSize = transactionSize + 1;
            }

        } else if (duration.isLongerThan(setting_Goal) && duration.isShorterThan(setting_OutOfRange)) {
            if (difference > 100) {
            newTransactionSize = ((int) (transactionSize - (transactionSize * 0.1)) - 1);
            } else {
            newTransactionSize = transactionSize - 1;
            }

            if (duration.isLongerThan(setting_OutOfRange)) {
                outOfRange = true;
            }
        } else {
            newTransactionSize = transactionSize;
        }

        if (outOfRange) {
            outOfRangeCount++;
            if (outOfRangeCount > setting_MaxOutOfRangeCount) {
                newTransactionSize = initialTransactionSize;
                outOfRangeCount = 0;
            }
        } else {
            outOfRangeCount = 0;
        }

        newTransactionSize = newTransactionSize > setting_MaxTransactions ? setting_MaxTransactions : newTransactionSize;
        newTransactionSize = newTransactionSize < setting_MinTransactions ? setting_MinTransactions : newTransactionSize;
        this.transactionSize = newTransactionSize;
    }

    public int getTransactionSize() {
        return transactionSize;
    }
}
