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

import com.google.gson.Gson;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.*;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.util.pwmdb.PwmDB;
import password.pwm.util.pwmdb.PwmDBException;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.*;

/**
 * IntruderManager watches for login errors by users and from IP addresses.  When to many bad attempts
 * occur, IntruderManager denies further login attempts.
 * <p/>
 * An singleton instance of IntruderManager is held by the PwmApplication singlenton.
 * <p/>
 * IntruderManager itself does not restrict/test logins, it relies on servlets or servlet filters
 * to call it appropriately.
 *
 * @author Jason D. Rivard
 */
public class IntruderManager implements Serializable, PwmService {
// ------------------------------ FIELDS ------------------------------


    private static final PwmLogger LOGGER = PwmLogger.getLogger(IntruderManager.class);
    private Timer taskMaster;
    private STATUS status = STATUS.NEW;
    private PwmApplication pwmApplication;
    private PwmDB pwmDB;
    private ErrorInformation healthError;

    private volatile int lockedUserCount;
    private volatile int lockedAddressCount;
    private volatile int recordTicks = CLEANER_MIN_RECORD_TICKS;

    private long configUserResetTime;
    private int configUserMaxAttempts;
    private long configAddressResetTime;
    private int configAddressMaxAttempts;
    private int configMaxRecordAgeMS = PwmConstants.INTRUDER_RETENTION_TIME_MS;

    private static final PwmDB.DB DB_USER = PwmDB.DB.INTRUDER_USER;
    private static final PwmDB.DB DB_ADDRESS = PwmDB.DB.INTRUDER_ADDRESS;
    private static final int CLEANER_MIN_RECORD_TICKS = 500;

// --------------------------- CONSTRUCTORS ---------------------------

    public IntruderManager() {
    }

// -------------------------- OTHER METHODS --------------------------

    /**
     * Mark an IP address as having a bad attempt.
     *
     * @param pwmSession Session state
     */
    public void addIntruderAttempt(final String username, final PwmSession pwmSession) throws PwmUnrecoverableException {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        ssBean.incrementIncorrectLogins();
        incrementStats();

        final String addressString = ssBean.getSrcAddress();
        if (addressString != null) {
            addBadAddressAttempt(addressString, pwmSession);
        }

        if (username != null && username.length() > 0) {
            addBadUserAttempt(username, pwmSession);
        }
    }

    private void addBadAddressAttempt(final String addressString, final PwmSession pwmSession) {
        if (configAddressMaxAttempts <= 0 || configAddressResetTime <= 0) {
            return;
        }

        try {
            final InetAddress address = InetAddress.getByName(addressString);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()) {
                LOGGER.trace(pwmSession, "disregarding local intruder attempt from: " + addressString);
            }
        } catch(Exception e) {
            LOGGER.error(pwmSession, "error examining address: " + addressString);
        }

        markBadAttempt(DB_ADDRESS, addressString, configAddressResetTime);
        final IntruderRecord record = readIntruderRecord(DB_ADDRESS, addressString);

        try {
            this.checkAddress(pwmSession);
        } catch (PwmUnrecoverableException e) {
            lockAddress(record, addressString);
        }

        {
            final StringBuilder sb = new StringBuilder();
            sb.append("incrementing count");
            sb.append(" address=").append(addressString);
            sb.append(", attemptCount=").append(record.getAttemptCount());
            if (isLocked(record,configAddressResetTime, configAddressMaxAttempts)) {
                sb.append(", locked=true");
                sb.append(", alerted=").append(record.isAlerted());
                sb.append(", age=").append(TimeDuration.fromCurrent(record.getTimeStamp()).asCompactString());
            }
            LOGGER.debug(pwmSession, sb.toString());
        }
    }

    private void incrementStats() {
        pwmApplication.getStatisticsManager().incrementValue(Statistic.INTRUDER_ATTEMPTS);
        pwmApplication.getStatisticsManager().updateEps(StatisticsManager.EpsType.INTRUDER_ATTEMPTS_60,1);
        pwmApplication.getStatisticsManager().updateEps(StatisticsManager.EpsType.INTRUDER_ATTEMPTS_240,1);
        pwmApplication.getStatisticsManager().updateEps(StatisticsManager.EpsType.INTRUDER_ATTEMPTS_1440,1);
    }

    private void markBadAttempt(final PwmDB.DB table, final String key, final long maxDuration) {
        IntruderRecord record = readIntruderRecord(table, key);
        if (record == null) {
            record = new IntruderRecord();
            recordTicks++;
        }
        record.incrementAttemptCount(maxDuration);
        writeIntruderRecord(table,key,record);
    }

    /**
     * Checks to see if an ip address has been locked out.  If the ip address is locked out, a PWMException is thrown, and
     * the ssBean's error status is set appropriately
     *
     * @param pwmSession the session bean of the logged in user
     * @throws password.pwm.error.PwmUnrecoverableException
     *          if the user is locked out
     */
    public void checkAddress(final PwmSession pwmSession)
            throws PwmUnrecoverableException {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final String addressString = ssBean.getSrcAddress();

        if (addressString != null) {
            final IntruderRecord record = readIntruderRecord(DB_ADDRESS, addressString);
            if (record != null && isLocked(record, configAddressResetTime, configAddressMaxAttempts)) {
                LOGGER.warn(pwmSession, "address intruder limit exceeded for " + addressString);
                final ErrorInformation error = new ErrorInformation(PwmError.ERROR_INTRUDER_ADDRESS);
                ssBean.setSessionError(error);
                incrementStats();
                throw new PwmUnrecoverableException(error);
            }
        }
    }

    private void addBadUserAttempt(final String username, final PwmSession pwmSession) throws PwmUnrecoverableException {
        if (configUserMaxAttempts <= 0 || configUserResetTime <= 0) {
            return;
        }

        markBadAttempt(DB_USER, username, configUserResetTime);
        final IntruderRecord record = readIntruderRecord(DB_USER, username);

        try {
            this.checkUser(username, pwmSession);
        } catch (PwmUnrecoverableException e) {
            lockUser(pwmSession, record, username);
        }

        {
            final StringBuilder sb = new StringBuilder();
            sb.append("incrementing count");
            sb.append(" user=").append(username);
            sb.append(", attemptCount=").append(record.getAttemptCount());
            if (isLocked(record, configUserResetTime, configUserMaxAttempts)) {
                sb.append(", locked=true");
                sb.append(", alerted=").append(record.isAlerted());
                sb.append(", age=").append(TimeDuration.fromCurrent(record.getTimeStamp()).asCompactString());
            }
            LOGGER.debug(pwmSession, sb.toString());
        }
    }

    public void delayPenalty(final String username, final PwmSession pwmSession) {
        Helper.pause(PwmRandom.getInstance().nextInt(2 * 1000) + 1000); // delay penalty of 1-3 seconds
    }

    private void lockUser(final PwmSession pwmSession, final IntruderRecord record, final String username) {
        if (record.isAlerted()) {
            return;
        }
        record.setAlerted(true);
        writeIntruderRecord(DB_USER, username, record);

        final Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("type", "user");
        values.put("username", username);
        values.put("attempts", String.valueOf(record.getAttemptCount()));
        values.put("age", TimeDuration.fromCurrent(record.getTimeStamp()).asCompactString());
        AlertHandler.alertIntruder(pwmApplication, values);

        lockedUserCount++;
        pwmApplication.getStatisticsManager().incrementValue(Statistic.LOCKED_USERS);

        try {
            final String userDN = UserStatusHelper.convertUsernameFieldtoDN(username, pwmSession, pwmApplication, null);
            final ChaiUser user = ChaiFactory.createChaiUser(userDN, pwmApplication.getProxyChaiProvider());
            UserHistory.updateUserHistory(pwmSession, pwmApplication, user, UserHistory.Record.Event.INTRUDER_LOCK, "");
            LOGGER.debug(pwmSession, "updated user history for " + userDN + " with intruder lock event");
        } catch (ChaiUnavailableException e) {
            LOGGER.debug(pwmSession, "error updating user history for " + username + " " + e.getMessage());

        } catch (PwmException e) {
            LOGGER.debug(pwmSession, "error updating user history for " + username + " " + e.getMessage());
        }
    }

    private void lockAddress(final IntruderRecord record, final String addressString) {
        if (record.isAlerted()) {
            return;
        }
        record.setAlerted(true);
        writeIntruderRecord(DB_ADDRESS, addressString, record);

        final Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("type", "address");
        values.put("address", addressString);
        values.put("attempts", String.valueOf(record.getAttemptCount()));
        values.put("age", TimeDuration.fromCurrent(record.getTimeStamp()).asCompactString());
        AlertHandler.alertIntruder(pwmApplication, values);

        lockedAddressCount++;
        pwmApplication.getStatisticsManager().incrementValue(Statistic.LOCKED_ADDRESSES);
    }

    /**
     * Checks to see if a userDN has been locked out.  If the userDN is locked out, a PWMException is thrown, and
     * the ssBean's error status is set appropriately
     *
     * @param username   the userDN to test
     * @param pwmSession the session bean of the logged in user
     * @throws password.pwm.error.PwmUnrecoverableException
     *          if the user is locked out
     */
    public void checkUser(final String username, final PwmSession pwmSession)
            throws PwmUnrecoverableException {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        final IntruderRecord record = readIntruderRecord(DB_USER,username);
        if (record != null && isLocked(record, configUserResetTime, configUserMaxAttempts)) {
            LOGGER.info(pwmSession, "user intruder limit exceeded for " + username);
            final ErrorInformation error = new ErrorInformation(PwmError.ERROR_INTRUDER_USER);
            ssBean.setSessionError(error);
            incrementStats();
            throw new PwmUnrecoverableException(error);
        }
    }

    public void addGoodAddressAttempt(final PwmSession pwmSession) {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        ssBean.resetIncorrectLogins();
        doGoodAttempt(DB_ADDRESS, ssBean.getSrcAddress());
        LOGGER.debug(pwmSession, "address intruder count reset for " + ssBean.getSrcAddress());
    }

    private void doGoodAttempt(final PwmDB.DB table, final String key) {
        if (key != null) {
            final IntruderRecord record = readIntruderRecord(table,key);
            if (record != null) {
                if (isLocked(record,table)) {
                    if (table == DB_USER) {
                        lockedUserCount--;
                    } else if (table == DB_ADDRESS) {
                        lockedAddressCount--;
                    }
                }
                record.clearAttemptCount();
                writeIntruderRecord(table,key,record);
            }
        }
    }

    public void addGoodUserAttempt(final String username, final PwmSession pwmSession) {
        doGoodAttempt(DB_USER, username);
        LOGGER.debug(pwmSession, "user intruder count reset for " + username);
    }

    public int currentLockedAddresses() {
        return lockedAddressCount;
    }

    public Map<String, IntruderRecord> getAddressLockTable() {
        if (status != STATUS.OPEN) {
            return Collections.emptyMap();
        }
        return getViewableRecordTable(DB_ADDRESS);
    }

    public int currentLockedUsers() {
        return lockedUserCount;
    }

    public Map<String, IntruderRecord> getUserLockTable() {
        if (status != STATUS.OPEN) {
            return Collections.emptyMap();
        }
        return getViewableRecordTable(DB_USER);
    }

    public int currentAddressTableSize() {
        if (status != STATUS.OPEN) {
            return 0;
        }
        try {
            return pwmDB.size(DB_ADDRESS);
        } catch (PwmDBException e) {
            LOGGER.error("error reading table size: " + e.getMessage());
        }
        return 0;
    }

    public int currentUserTableSize() {
        if (status != STATUS.OPENING) {
            return 0;
        }
        try {
            return pwmDB.size(DB_ADDRESS);
        } catch (PwmDBException e) {
            LOGGER.error("error reading table size: " + e.getMessage());
        }
        return 0;
    }

    public STATUS status() {
        return status;
    }

    public void init(PwmApplication pwmApplication) throws PwmException {
        this.status = STATUS.OPENING;
        this.pwmApplication = pwmApplication;
        this.taskMaster = new Timer("pwm-IntruderManager timer",true);
        this.pwmDB = pwmApplication.getPwmDB();

        final Configuration config = pwmApplication.getConfig();
        configUserResetTime = config.readSettingAsLong(PwmSetting.INTRUDER_USER_RESET_TIME) * 1000;
        configUserMaxAttempts = (int)config.readSettingAsLong(PwmSetting.INTRUDER_USER_MAX_ATTEMPTS);
        configAddressResetTime = config.readSettingAsLong(PwmSetting.INTRUDER_ADDRESS_RESET_TIME) * 1000;
        configAddressMaxAttempts = (int)config.readSettingAsLong(PwmSetting.INTRUDER_ADDRESS_MAX_ATTEMPTS);

        if (pwmDB == null || pwmDB.status() != PwmDB.Status.OPEN) {
            healthError = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, "IntruderManager can not open, pwmDB is not open");
            this.close();
            return;
        }

        taskMaster.schedule(new InitializeTables(), 1);
        taskMaster.schedule(new CleanerTask(), 2, PwmConstants.INTRUDER_CLEANUP_FREQUENCY_MS);
        this.status = STATUS.OPEN;
    }

    public void close() {
        if (taskMaster != null) {
            try {
                taskMaster.cancel();
            } catch (Exception e) {
                LOGGER.error("error closing taskMaster: " + e.getMessage(),e);
            }
            taskMaster = null;
        }
        status = STATUS.CLOSED;
    }

    public List<HealthRecord> healthCheck() {
        return healthError == null ? null : Collections.singletonList(new HealthRecord(HealthStatus.WARN, "IntruderManager", healthError.toDebugStr()));
    }

    private IntruderRecord readIntruderRecord(final PwmDB.DB table, final String key) {
        if (status != STATUS.OPEN) {
            return null;
        }

        final String strValue;
        try {
            strValue = pwmDB.get(table,key);
        } catch (Exception e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,"unexpected error reading intruder record from pwmDB: " + e.getMessage());
            LOGGER.error(errorInformation.toDebugStr());
            return null;
        }

        try {
            if (strValue != null) {
                final Gson gson = new Gson();
                final IntruderRecord record = gson.fromJson(strValue,IntruderRecord.class);
                if (record != null) {
                    if (TimeDuration.fromCurrent(record.timeStamp).isLongerThan(configMaxRecordAgeMS)) {
                        pwmDB.remove(table, key);
                        return null;
                    }
                }
                return record;
            }
        } catch (Exception e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,"unexpected error parsing intruder record from pwmDB: " + e.getMessage());
            LOGGER.error(errorInformation.toDebugStr());
            try {
                pwmDB.remove(table,key);
            } catch (Exception e2) {
                final ErrorInformation errorInformation2 = new ErrorInformation(PwmError.ERROR_UNKNOWN,"unexpected error clearing malformed intruder record from pwmDB: " + e2.getMessage());
                LOGGER.error(errorInformation2.toDebugStr());
            }
        }
        return null;
    }

    private void writeIntruderRecord(final PwmDB.DB table, final String key, final IntruderRecord record) {
        if (status != STATUS.OPEN) {
            return;
        }

        try {
            if (record != null) {
                final Gson gson = new Gson();
                pwmDB.put(table,key,gson.toJson(record));
            } else {
                pwmDB.remove(table,key);
            }
        } catch (Exception e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,"unexpected error writing intruder record to pwmDB: " + e.getMessage());
            LOGGER.error(errorInformation.toDebugStr());
        }
    }

    private Map<String,IntruderRecord> getViewableRecordTable(final PwmDB.DB table) {
        final int maxSize = PwmConstants.INTRUDER_TABLE_SIZE_VIEW_MAX;
        final Map<String,IntruderRecord> returnTable = new TreeMap<String,IntruderRecord>();
        final long startTime = System.currentTimeMillis();

        int counter = 0;
        LOGGER.trace("beginning full table read of table " + table);
        synchronized (table) {
            Iterator<String> tableIterator = null;
            try {
                tableIterator = pwmDB.iterator(table);
                while (tableIterator.hasNext() && counter < maxSize) {
                    final String key = tableIterator.next();
                    final IntruderRecord loopRecord = readIntruderRecord(table,key);
                    if (loopRecord != null) {
                        returnTable.put(key,loopRecord);
                        counter++;
                    }
                }
            } catch (PwmDBException e) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,  "unexpected error during lock counting for table ''" + table + ", error: " + e.getMessage());
                LOGGER.error(errorInformation.toDebugStr());
            } finally {
                if (tableIterator != null) {
                    try { pwmDB.returnIterator(table); } catch (Exception e) {/**/}
                }
            }
        }
        LOGGER.debug("completed full table read for table " + table + ", duration=" + TimeDuration.fromCurrent(startTime).asCompactString() + ", records=" + counter);
        return Collections.unmodifiableMap(returnTable);
    }

    public boolean isLocked(IntruderRecord record, PwmDB.DB table) {
        if (table == DB_USER) {
            return isLocked(record, configUserResetTime, configUserMaxAttempts);
        } else if (table == DB_ADDRESS) {
            return isLocked(record, configAddressResetTime, configAddressMaxAttempts);
        }
        return false;
    }

    private static boolean isLocked(IntruderRecord record, final long duration, final int maxCount) {
        if (record == null) {
            return false;
        }

        final TimeDuration recordAge = TimeDuration.fromCurrent(record.getTimeStamp());
        if (recordAge.isShorterThan(duration)) {
            if (record.getAttemptCount() >= maxCount) {
                return true;
            }
        }
        return false;
    }


// -------------------------- INNER CLASSES --------------------------

    static public class IntruderRecord implements Serializable {
        private long timeStamp;
        private int attemptCount;
        private boolean alerted;

        public IntruderRecord() {
            this.timeStamp = System.currentTimeMillis();
            this.attemptCount = 0;
            this.alerted = false;
        }

        public long getTimeStamp() {
            return timeStamp;
        }

        public int getAttemptCount() {
            return attemptCount;
        }

        public void incrementAttemptCount(final long maxDuration) {
            if (TimeDuration.fromCurrent(timeStamp).isLongerThan(maxDuration)) {
                attemptCount = 0;
            }
            timeStamp = System.currentTimeMillis();
            attemptCount++;
        }

        public void clearAttemptCount() {
            alerted = false;
            attemptCount = 0;
        }

        public boolean isAlerted() {
            return alerted;
        }

        public void setAlerted(final boolean alerted) {
            this.alerted = alerted;
        }
    }

    public class CleanerTask extends TimerTask {
        public void run() {
            if (recordTicks >= CLEANER_MIN_RECORD_TICKS) {
                cleanup(DB_USER);
                cleanup(DB_ADDRESS);
                recordTicks = 0;
            }
        }

        private void cleanup(final PwmDB.DB table) {
            final long startTime = System.currentTimeMillis();
            int cleanedRecords = 0;
            LOGGER.trace("beginning intruder table cleanup process for table " + table);
            synchronized (table) {
                Iterator<String> tableIterator = null;
                try {
                    tableIterator = pwmDB.iterator(table);
                    while (tableIterator.hasNext()) {
                        final String key = tableIterator.next();
                        final IntruderRecord loopRecord = readIntruderRecord(table,key);
                        if (loopRecord != null) {
                            //LOGGER.trace("  record key=" + key + ", timestamp=" + new Date(loopRecord.getTimeStamp()) + ", attempts=" + loopRecord.getAttemptCount());
                            if (TimeDuration.fromCurrent(loopRecord.getTimeStamp()).isLongerThan(configMaxRecordAgeMS)) {
                                writeIntruderRecord(table,key,null);
                                cleanedRecords++;
                            }
                        }
                    }
                } catch (PwmDBException e) {
                    healthError = new ErrorInformation(PwmError.ERROR_UNKNOWN, "unexpected error during pwmDB cleanup: " + e.getMessage());
                    LOGGER.error(healthError.toDebugStr());
                } finally {
                    if (tableIterator != null) {
                        try { pwmDB.returnIterator(table); } catch (Exception e) {/**/}
                    }
                }
            }
            LOGGER.trace("completed intruder table cleanup process for table " + table + ", duration=" + TimeDuration.fromCurrent(startTime).asCompactString() + ", records removed=" + cleanedRecords);
        }
    }

    public class InitializeTables extends TimerTask {
        public void run() {
            lockedUserCount += countLocks(DB_USER);
            lockedAddressCount += countLocks(DB_ADDRESS);
            try {
                pwmDB.size(DB_USER);
                pwmDB.size(DB_ADDRESS);
            } catch (PwmDBException e) {
                LOGGER.error("unexpected error examining intruder table sizes: " + e.getMessage());
            }
        }

        private int countLocks(final PwmDB.DB table) {
            final long startTime = System.currentTimeMillis();
            int counter = 0;
            LOGGER.trace("beginning intruder count process for table " + table);
            synchronized (table) {
                Iterator<String> tableIterator = null;
                try {
                    tableIterator = pwmDB.iterator(table);
                    while (tableIterator.hasNext()) {
                        final String key = tableIterator.next();
                        final IntruderRecord loopRecord = readIntruderRecord(table,key);
                        if (loopRecord != null && isLocked(loopRecord, table)) {
                            counter++;
                        }
                    }
                } catch (PwmDBException e) {
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,  "unexpected error during lock counting for table ''" + table + ", error: " + e.getMessage());
                    LOGGER.error(errorInformation.toDebugStr());
                } finally {
                    if (tableIterator != null) {
                        try { pwmDB.returnIterator(table); } catch (Exception e) {/**/}
                    }
                }
            }
            LOGGER.trace("completed intruder table count process for table " + table + ", duration=" + TimeDuration.fromCurrent(startTime).asCompactString() + ", locked records=" + counter);
            return counter;
        }
    }
}

