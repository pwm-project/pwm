/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

    private final TimeDuration setting_LowGoal;
    private final TimeDuration setting_HighGoal;
    private final TimeDuration setting_OutOfRange;
    private final int setting_MaxTransactions;
    private final int setting_MinTransactions;

    private volatile int transactionSize;

    public TransactionSizeCalculator(
            final long lowTimeMS,
            final long highTimeMS,
            final int minTransactions,
            final int maxTransactions
    )
    {
        this(lowTimeMS, highTimeMS, minTransactions, maxTransactions, minTransactions + 1);
    }

    public TransactionSizeCalculator(
            final long lowTimeMS,
            final long highTimeMS,
            final int minTransactions,
            final int maxTransactions,
            final int initialTransactionSize
    )
    {
        this.setting_LowGoal = new TimeDuration(lowTimeMS);
        this.setting_HighGoal = new TimeDuration(highTimeMS);
        this.setting_OutOfRange = new TimeDuration(setting_HighGoal.getTotalMilliseconds() * 5);
        this.setting_MaxTransactions = maxTransactions;
        this.setting_MinTransactions = minTransactions;
        this.transactionSize = initialTransactionSize;
        if (transactionSize > maxTransactions) {
            transactionSize = maxTransactions;
        }
        if (transactionSize < minTransactions) {
            transactionSize = minTransactions;
        }
    }

    public void recordLastTransactionDuration(final long duration) {
        recordLastTransactionDuration(new TimeDuration(duration));
    }

    public void recordLastTransactionDuration(final TimeDuration duration)
    {
        int newTransactionSize;

        if (duration.isShorterThan(setting_LowGoal)) {
            newTransactionSize = ((int) (transactionSize + (transactionSize * 0.1)) + 1);
        } else if (duration.isLongerThan(setting_HighGoal) && duration.isShorterThan(setting_OutOfRange)) {
            newTransactionSize = ((int) (transactionSize - (transactionSize * 0.1)) - 1);
        } else if (duration.isLongerThan(setting_OutOfRange)) {
            newTransactionSize = (int) (transactionSize * 0.5);
        } else {
            newTransactionSize = transactionSize + PwmRandom.getInstance().nextInt(10);
        }

        newTransactionSize = newTransactionSize > setting_MaxTransactions ? setting_MaxTransactions : newTransactionSize;
        newTransactionSize = newTransactionSize < setting_MinTransactions ? setting_MinTransactions : newTransactionSize;
        this.transactionSize = newTransactionSize;
    }

    public int getTransactionSize() {
        return transactionSize;
    }
}
