package password.pwm.util;

public class TransactionSizeCalculator {

    private final TimeDuration setting_LowGoal;
    private final TimeDuration setting_HighGoal;
    private final TimeDuration setting_OutOfRange;
    private final int setting_MaxTransactions;
    private final int setting_MinTransactions;

    private int transactionSize = 3;

    public TransactionSizeCalculator(
            final long lowTimeMS,
            final long highTimeMS,
            final int minTransactions,
            final int maxTransactions
    )
    {
        this.setting_LowGoal = new TimeDuration(lowTimeMS);
        this.setting_HighGoal = new TimeDuration(highTimeMS);
        this.setting_OutOfRange = new TimeDuration(setting_HighGoal.getTotalMilliseconds() * 5);
        this.setting_MaxTransactions = maxTransactions;
        this.setting_MinTransactions = minTransactions;
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
