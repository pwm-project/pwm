/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

import password.pwm.util.db.PwmDB;
import password.pwm.util.db.PwmDBException;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class StatisticsManager {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(StatisticsManager.class);

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getInstance();

    private static final int DB_WRITE_FREQUENCY_MS = 3 * 60 * 1000;  // 3 minutes
    private static final int MAX_AVG_HISTORY_SIZE = 500;

    public enum Statistic {
        PWM_AUTHENTICATIONS(Type.INCREMENTOR),
        PWM_STARTUPS(Type.INCREMENTOR),
        PASSWORD_CHANGES(Type.INCREMENTOR),
        FAILED_LOGIN_ATTEMPTS(Type.INCREMENTOR),
        RECOVERY_SUCCESSES(Type.INCREMENTOR),
        RECOVERY_ATTEMPTS(Type.INCREMENTOR),
        EMAIL_SEND_SUCCESSES(Type.INCREMENTOR),
        EMAIL_SEND_FAILURES(Type.INCREMENTOR),
        PASSWORD_RULE_CHECKS(Type.INCREMENTOR),
        HTTP_REQUESTS(Type.INCREMENTOR),
        ACTIVATED_USERS(Type.INCREMENTOR),
        NEW_USERS(Type.INCREMENTOR),
        LOCKED_USERS(Type.INCREMENTOR),
        LOCKED_ADDRESSES(Type.INCREMENTOR),
        CAPTCHA_SUCCESSES(Type.INCREMENTOR),
        CAPTCHA_FAILURES(Type.INCREMENTOR),
        LDAP_UNAVAILABLE_COUNT(Type.INCREMENTOR),
        HTTP_SESSIONS(Type.INCREMENTOR),

        CURENT_LDAP_CONNECTIONS(Type.COUNTER),

        PWM_START_TIME(Type.TIMESTAMP),
        PWM_INSTALL_TIME(Type.TIMESTAMP),
        LDAP_UNAVAILABLE_TIME(Type.TIMESTAMP),

        AVG_PASSWORD_SYNC_TIME(Type.AVERAGE),
        AVG_WORDLIST_CHECK_TIME(Type.AVERAGE);

        private final Type type;

        Statistic(final Type type) {
            this.type = type;
        }

        public Type getType() {
            return type;
        }
    }

    public enum Type {
        INCREMENTOR,
        COUNTER,
        AVERAGE,
        TIMESTAMP,
    }

    private final Map<Statistic, BigInteger> counterMap = new HashMap<Statistic, BigInteger>();
    private final Map<Statistic, LinkedList<Long>> averageMap = new HashMap<Statistic, LinkedList<Long>>();
    private final Map<Statistic, Date> timestampMap = new HashMap<Statistic, Date>();

    private Map<Statistic, String> dbStats = new HashMap<Statistic, String>();

    private final PwmDB pwmDB;

    private long lastDbWrite;
    private volatile Thread flusherThread;

    public StatisticsManager(final PwmDB pwmDB) {
        this.pwmDB = pwmDB;

        for (final Statistic stat : Statistic.values()) {
            switch (stat.getType()) {
                case COUNTER:
                case INCREMENTOR:
                    counterMap.put(stat, BigInteger.ZERO);
                    break;
                case AVERAGE:
                    averageMap.put(stat, new LinkedList<Long>());
                    break;
            }
        }

        try {
            dbStats = loadDbValues(pwmDB);
        } catch (PwmDBException e) {
            LOGGER.error("error loading db statistics values: " + e.getMessage());
        }
    }

    public synchronized void incrementValue(final Statistic statistic) {
        if (Type.INCREMENTOR != statistic.getType() && Type.COUNTER != statistic.getType()) {
            LOGGER.error("attempt to increment non-counter/incrementor stat " + statistic);
            return;
        }

        final BigInteger newValue = counterMap.get(statistic).add(BigInteger.ONE);
        counterMap.put(statistic,newValue);
        checkIfDbWriteRequired();
    }

    public synchronized void decrementValue(final Statistic statistic) {
        if (Type.COUNTER != statistic.getType()) {
            LOGGER.error("attempt to decrement non-counter stat " + statistic);
            return;
        }

        final BigInteger newValue = counterMap.get(statistic).subtract(BigInteger.ONE);
        counterMap.put(statistic,newValue);
        checkIfDbWriteRequired();
    }

    public synchronized void updateAverageValue(final Statistic statistic, final long timeDuration) {
        if (Type.AVERAGE != statistic.getType()) {
            LOGGER.error("attempt to update average value of non-average stat " + statistic);
            return;
        }

        final LinkedList<Long> currentList = averageMap.get(statistic);

        currentList.addLast(timeDuration);
        while (currentList.size() > MAX_AVG_HISTORY_SIZE) {
            currentList.removeFirst();
        }
    }

    public synchronized void updateTimestamp(final Statistic statistic) {
        if (Type.TIMESTAMP != statistic.getType()) {
            LOGGER.error("attempt to update timestamp value of non-timestamp stat " + statistic);
            return;
        }

        timestampMap.put(statistic, new Date());
        checkIfDbWriteRequired();
    }


    public String getCurrentStat(final Statistic statistic) {
        switch (statistic.getType()) {
            case INCREMENTOR:
            case COUNTER:
                return NUMBER_FORMAT.format(new BigInteger(counterMap.get(statistic).toString()));
            
            case AVERAGE:
                if (!averageMap.containsKey(statistic) || averageMap.get(statistic).isEmpty()) {
                    return "0";
                } else {
                    BigInteger totalTime = BigInteger.ZERO;
                    for (final Long aLong : averageMap.get(statistic)) {
                        totalTime = totalTime.add(BigInteger.valueOf(aLong));
                    }
                    return totalTime.divide(BigInteger.valueOf(averageMap.get(statistic).size())).toString();
                }

            case TIMESTAMP:
                final Date value = timestampMap.get(statistic);
                return value == null ? getCumulativeStat(statistic) : value.toString();

            default:
                return "";
        }
    }

    public String getCumulativeStat(final Statistic statistic) {
        if (dbStats == null) {
            return null;
        }

        switch (statistic.getType()) {
            case INCREMENTOR:
                final String dbValue = dbStats.containsKey(statistic) ? dbStats.get(statistic) : "0";
                final BigInteger bigInt = counterMap.get(statistic).add(new BigInteger(dbValue));
                return NUMBER_FORMAT.format(bigInt);

            case COUNTER:
            case AVERAGE:
                return null;

            case TIMESTAMP:
                final Date dbTimeValue = dbStats.containsKey(statistic) ? new Date(Long.parseLong(dbStats.get(statistic))) : null;
                final Date timeValue = timestampMap.get(statistic);
                if (timeValue == null) {
                    return dbTimeValue == null ? "Never" : dbTimeValue.toString();
                } else {
                    return timeValue == null ? "Never" : timeValue.toString();
                }

            default:
                return "";
        }
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();

        for (final Statistic m : Statistic.values()) {
            sb.append(m.toString());
            sb.append("=");
            sb.append(getCurrentStat(m));
            sb.append(", ");
        }

        if (sb.length() > 2) {
            sb.delete(sb.length() -2 , sb.length());
        }

        return sb.toString();
    }

    private static Map<Statistic,String> loadDbValues(final PwmDB pwmDB) throws PwmDBException {
        final Map<Statistic,String> returnMap = new HashMap<Statistic,String>();

        if (pwmDB != null) {
            for (final Statistic stat : Statistic.values()) {
                final String currentValue = pwmDB.get(PwmDB.DB.PWM_STATS,stat.toString());
                if (currentValue != null) {
                    returnMap.put(stat,currentValue);
                }
            }
        }

        return returnMap;
    }

    private void writeDbValues() {
        lastDbWrite = System.currentTimeMillis();
        if (pwmDB != null) {

            try {
                for (final Statistic stat : Statistic.values()) {
                    String value = null;
                    switch (stat.getType()) {
                        case INCREMENTOR:
                            final String dbValue = dbStats.containsKey(stat) ? dbStats.get(stat) : "0";
                            final BigInteger bigInt = counterMap.get(stat).add(new BigInteger(dbValue));
                            value = bigInt.toString();
                            break;
                        case TIMESTAMP:
                            if (timestampMap.containsKey(stat)) {
                                value = Long.toString(timestampMap.get(stat).getTime());
                            }
                    }

                    if (value != null && !"0".equals(value)) {
                        pwmDB.put(PwmDB.DB.PWM_STATS, stat.toString(), value);
                    }
                }
            } catch (PwmDBException e) {
                LOGGER.error("error outputing pwm statistics: " + e.getMessage());
            }
        }
        LOGGER.trace("output current statistics to pwmDB");
    }

    public boolean hasCummulativeValues() {
        return dbStats != null;
    }

    public void flush() {
        writeDbValues();
    }

    private void checkIfDbWriteRequired() {
        if (dbStats != null && flusherThread == null) {
            if ((System.currentTimeMillis() - lastDbWrite) > DB_WRITE_FREQUENCY_MS) {
                flusherThread = new Thread(new FlusherThread());
                flusherThread.start();
            }
        }

    }

    private class FlusherThread implements Runnable {
        public void run() {
            writeDbValues();
            flusherThread = null;
        }
    }
}
