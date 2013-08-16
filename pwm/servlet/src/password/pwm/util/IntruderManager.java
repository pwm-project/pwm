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
import com.novell.ldapchai.exception.ChaiException;
import password.pwm.*;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.event.AuditEvent;
import password.pwm.event.AuditRecord;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

// ------------------------------ FIELDS ------------------------------

public class IntruderManager implements Serializable, PwmService {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(IntruderManager.class);
    private static final long CLEANER_RUN_FREQUENCY_MS = PwmConstants.INTRUDER_CLEANUP_FREQUENCY_MS;
    private static final long MAX_RECORD_AGE_MS = PwmConstants.INTRUDER_RETENTION_TIME_MS;

    private PwmApplication pwmApplication;
    private STATUS status = STATUS.NEW;
    private RecordManager userManager = new StubRecordManager();
    private RecordManager addressManager = new StubRecordManager();
    private ErrorInformation startupError;
    private Timer timer;

    public IntruderManager() {
    }

    @Override
    public STATUS status() {
        return status;
    }

    @Override
    public void init(PwmApplication pwmApplication)
            throws PwmException
    {
        this.pwmApplication = pwmApplication;
        final Configuration config = pwmApplication.getConfig();
        status = STATUS.OPENING;
        if (pwmApplication.getLocalDB() == null || pwmApplication.getLocalDB().status() != LocalDB.Status.OPEN) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,"unable to start IntruderManager, LocalDB unavailable");
            LOGGER.error(errorInformation.toDebugStr());
            startupError = errorInformation;
            status = STATUS.CLOSED;
            return;
        }

        try {
            final PwmDBRecordStore userStore = new PwmDBRecordStore(this, pwmApplication.getLocalDB(), LocalDB.DB.INTRUDER_USER);
            final PwmDBRecordStore addressStore = new PwmDBRecordStore(this, pwmApplication.getLocalDB(), LocalDB.DB.INTRUDER_ADDRESS);
            {
                final int checkCount = (int)config.readSettingAsLong(PwmSetting.INTRUDER_USER_MAX_ATTEMPTS);
                final TimeDuration resetDuration = new TimeDuration(1000 * config.readSettingAsLong(PwmSetting.INTRUDER_USER_RESET_TIME));
                final TimeDuration checkDuration = new TimeDuration(1000 * config.readSettingAsLong(PwmSetting.INTRUDER_USER_CHECK_TIME));
                if (checkCount == 0 || resetDuration.getTotalMilliseconds() == 0 || checkDuration.getTotalMilliseconds() == 0) {
                    LOGGER.info("intruder user checking will remain disabled due to configuration settings");
                } else {
                    userManager = new PwmDBRecordManager(userStore, checkDuration, checkCount, resetDuration);
                }
            }
            {
                final int checkCount = (int)config.readSettingAsLong(PwmSetting.INTRUDER_ADDRESS_MAX_ATTEMPTS);
                final TimeDuration resetDuration = new TimeDuration(1000 * config.readSettingAsLong(PwmSetting.INTRUDER_ADDRESS_RESET_TIME));
                final TimeDuration checkDuration = new TimeDuration(1000 * config.readSettingAsLong(PwmSetting.INTRUDER_ADDRESS_CHECK_TIME));
                if (checkCount == 0 || resetDuration.getTotalMilliseconds() == 0 || checkDuration.getTotalMilliseconds() == 0) {
                    LOGGER.info("intruder address checking will remain disabled due to configuration settings");
                } else {
                    addressManager = new PwmDBRecordManager(addressStore, checkDuration, checkCount, resetDuration);
                }
            }
            timer = new Timer(PwmConstants.PWM_APP_NAME + "-IntruderManager cleaner",true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        userStore.cleanup(new TimeDuration(MAX_RECORD_AGE_MS));
                    } catch (Exception e) {
                        LOGGER.error("error cleaning userStore: " + e.getMessage(),e);
                    }
                    try {
                        addressStore.cleanup(new TimeDuration(MAX_RECORD_AGE_MS));
                    } catch (Exception e) {
                        LOGGER.error("error cleaning addressStore: " + e.getMessage(),e);
                    }
                }
            },1000,CLEANER_RUN_FREQUENCY_MS);
            status = STATUS.OPEN;
        } catch (Exception e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,"unexpected error starting intruder manager: " + e.getMessage());
            LOGGER.error(errorInformation.toDebugStr());
            startupError = errorInformation;
            close();
        }
    }




    @Override
    public void close() {
        status = STATUS.CLOSED;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    @Override
    public List<HealthRecord> healthCheck() {
        if (startupError != null && status != STATUS.OPEN) {
            return Collections.singletonList(new HealthRecord(HealthStatus.WARN,this.getClass().getSimpleName(),"unable to start: " + startupError.toDebugStr()));
        }
        return Collections.emptyList();
    }

    public void clear(final String username, String userDN, final PwmSession pwmSession)
            throws PwmUnrecoverableException
    {
        if (pwmSession != null) {

        }
        final String address = pwmSession != null ? pwmSession.getSessionStateBean().getSrcAddress() : null;
        if (userDN == null && pwmSession != null && pwmSession.getSessionStateBean().isAuthenticated()) {
            userDN = pwmSession.getUserInfoBean().getUserDN();
        }
        clear(username, userDN, address);
    }

    public void mark(final String username, String userDN, final PwmSession pwmSession)
            throws PwmUnrecoverableException
    {
        final String address = pwmSession != null ? pwmSession.getSessionStateBean().getSrcAddress() : null;
        if (userDN == null && pwmSession != null && pwmSession.getSessionStateBean().isAuthenticated()) {
            userDN = pwmSession.getUserInfoBean().getUserDN();
        }
        mark(username, userDN, address, pwmSession);
    }


    public void check(final String username, String userDN, final PwmSession pwmSession)
            throws PwmUnrecoverableException
    {
        final String address = pwmSession != null ? pwmSession.getSessionStateBean().getSrcAddress() : null;
        if (userDN == null && pwmSession != null && pwmSession.getSessionStateBean().isAuthenticated()) {
            userDN = pwmSession.getUserInfoBean().getUserDN();
        }
        check(username, userDN, address, pwmSession);
    }

    public void check(final String username, final String userDN, final String address, final PwmSession pwmSession)
            throws PwmUnrecoverableException
    {
        if (userDN != null && userManager.checkSubject(userDN)) {
            if (!userManager.isAlerted(userDN)) {
                // mark audit record on user
                try {
                    final ChaiUser chaiUser = ChaiFactory.createChaiUser(userDN, pwmApplication.getProxyChaiProvider());
                    final String userID = Helper.readLdapUserIDValue(pwmApplication,chaiUser);
                    final AuditRecord auditRecord = new AuditRecord(AuditEvent.INTRUDER_LOCK ,userID, userDN, pwmSession);
                    pwmApplication.getAuditManager().submitAuditRecord(auditRecord);
                } catch (ChaiException e) {
                    LOGGER.error("unexpected ldap error generating audit lockout event: " + e.getMessage());
                }
                sendAlert(userManager.readIntruderRecord(userDN, IntruderRecord.Type.userDN));
                userManager.markAlerted(userDN);
            }
            delayPenalty();
            throw new PwmUnrecoverableException(PwmError.ERROR_INTRUDER_USER);
        }
        if (username != null && userManager.checkSubject(username)) {
            if (!userManager.isAlerted(username)) {
                sendAlert(userManager.readIntruderRecord(username, IntruderRecord.Type.username));
                userManager.markAlerted(username);
            }
            delayPenalty();
            throw new PwmUnrecoverableException(PwmError.ERROR_INTRUDER_USER);
        }
        if (address != null && addressManager.checkSubject(address)) {
            if (!addressManager.isAlerted(address)) {
                sendAlert(addressManager.readIntruderRecord(address, IntruderRecord.Type.address));
                addressManager.markAlerted(address);
            }
            delayPenalty();
            throw new PwmUnrecoverableException(PwmError.ERROR_INTRUDER_ADDRESS);
        }
    }

    public void clear(final String username, final String userDN, final String address) {
        if (username != null) {
            userManager.clearSubject(username);
        }
        if (userDN != null) {
            userManager.clearSubject(userDN);
        }
        if (address != null) {
            addressManager.clearSubject(address);
        }
    }

    public void mark(final String username, final String userDN, final String address, final PwmSession pwmSession)
            throws PwmUnrecoverableException
    {
        boolean marked = false;
        if (username != null) {
            userManager.markSubject(username);
            marked = true;
        }

        if (userDN != null) {
            userManager.markSubject(userDN);
            marked = true;
        }

        if (address != null) {
            try {
                final InetAddress inetAddress = InetAddress.getByName(address);
                if (inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress()) {
                    LOGGER.debug("disregarding local intruder attempt from: " + address);
                } else {
                    addressManager.markSubject(address);
                    marked = true;
                }
            } catch(Exception e) {
                LOGGER.error("error examining address: " + address);
            }
        }


        if (marked && pwmApplication != null) {
            final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
            if (statisticsManager != null && statisticsManager.status() == STATUS.OPEN) {
                pwmApplication.getStatisticsManager().incrementValue(Statistic.INTRUDER_ATTEMPTS);
                pwmApplication.getStatisticsManager().updateEps(Statistic.EpsType.INTRUDER_ATTEMPTS,1);
            }

            check(username, userDN, address, pwmSession);

            delayPenalty();
        }
    }

    private void delayPenalty() {
        long delayPenalty = PwmConstants.INTRUDER_MIN_DELAY_PENALTY_MS; // minimum
        delayPenalty += PwmRandom.getInstance().nextInt((int)PwmConstants.INTRUDER_DELAY_MAX_JITTER_MS); // add some randomness;
        delayPenalty = delayPenalty > PwmConstants.INTRUDER_MAX_DELAY_PENALTY_MS ? PwmConstants.INTRUDER_MAX_DELAY_PENALTY_MS : delayPenalty;
        Helper.pause(delayPenalty);
    }

    private void sendAlert(final IntruderRecord intruderRecord) {
        if (intruderRecord == null) {
            return;
        }

        final Map<String,String> values = new LinkedHashMap<String, String>();
        values.put("type", intruderRecord.getType().toString());
        values.put(intruderRecord.getType().toString(),intruderRecord.getSubject());
        values.put("attempts", String.valueOf(intruderRecord.getAttemptCount()));
        values.put("age", TimeDuration.fromCurrent(intruderRecord.getTimeStamp()).asCompactString());
        AlertHandler.alertIntruder(pwmApplication, values);
    }

    public int addressRecordCount() {
        return addressManager.recordCount();
    }

    public int userRecordCount() {
        return userManager.recordCount();
    }

    public List<Map<String,Object>> getUserRecords(int maximum)
            throws PwmOperationalException
    {
        return getRecords(userManager,maximum);
    }

    public List<Map<String,Object>> getAddressRecords(int maximum)
            throws PwmOperationalException
    {
        return getRecords(addressManager,maximum);
    }

    private List<Map<String,Object>> getRecords(RecordManager recordManager, int maximum)
            throws PwmOperationalException
    {
        final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        final ArrayList<Map<String,Object>> returnList = new ArrayList<Map<String,Object>>();
        for (final IntruderManager.RecordIterator theIterator = recordManager.iterator(); theIterator.hasNext() && returnList.size() < maximum; ) {
            final IntruderManager.IntruderRecord intruderRecord = theIterator.next();
            if (intruderRecord != null) {
                final Map<String,Object> rowData = new HashMap<String,Object>();
                rowData.put("subject",intruderRecord.getSubject());
                rowData.put("timestamp",dateFormat.format(intruderRecord.getTimeStamp()));
                rowData.put("count",String.valueOf(intruderRecord.getAttemptCount()));
                try {
                    check(intruderRecord.getSubject(), intruderRecord.getSubject(), "",null);
                    rowData.put("status","watching");
                } catch (PwmException e) {
                    rowData.put("status","locked");
                }
                returnList.add(rowData);
            }
        }
        return returnList;
    }

    private static class IntruderRecord implements Serializable {
        public enum Type { username, userDN, address }
        private Type type;
        private String subject;
        private Date timeStamp;
        private int attemptCount;

        public IntruderRecord(Type type, String subject, Date timeStamp, int attemptCount) {
            this.type = type;
            this.subject = subject;
            this.timeStamp = timeStamp;
            this.attemptCount = attemptCount;
        }

        public Type getType() {
            return type;
        }

        public String getSubject() {
            return subject;
        }

        public Date getTimeStamp() {
            return timeStamp;
        }

        public int getAttemptCount() {
            return attemptCount;
        }
    }

    static class InternalRecord implements Serializable {
        private String subject;
        private Date timeStamp = new Date();
        private int attemptCount = 0;
        private boolean alerted = false;

        private InternalRecord() {
        }

        public InternalRecord(final String subject) {
            if (subject == null || subject.length() < 1) {
                throw new IllegalArgumentException("subject must have a value");
            }
            this.subject = subject;
        }

        public String getSubject() {
            return subject;
        }

        public Date getTimeStamp() {
            return timeStamp;
        }

        public int getAttemptCount() {
            return attemptCount;
        }

        public void incrementAttemptCount() {
            timeStamp = new Date();
            attemptCount++;
        }

        public void clearAttemptCount() {
            alerted = false;
            attemptCount = 0;
        }

        public boolean isAlerted() {
            return alerted;
        }

        public void setAlerted() {
            this.alerted = true;
        }

        public IntruderRecord asIntruderRecord(final IntruderRecord.Type type) {
            return new IntruderRecord(type, this.getSubject(), this.getTimeStamp(), this.getAttemptCount());
        }
    }

    static interface RecordManager {
        public boolean checkSubject(final String subject);
        public void markSubject(final String subject);
        public void clearSubject(final String subject);
        public boolean isAlerted(final String subject);
        public void markAlerted(final String subject);
        public IntruderRecord readIntruderRecord(final String subject, final IntruderRecord.Type type);
        public int recordCount();
        public RecordIterator iterator() throws PwmOperationalException;
    }

    static class StubRecordManager implements RecordManager {
        public boolean checkSubject(String subject) {
            return false;
        }

        public void markSubject(String subject) {
        }

        public void clearSubject(String subject) {
        }

        public boolean isAlerted(String subject) {
            return false;
        }

        public void markAlerted(String subject) {
        }

        @Override
        public IntruderRecord readIntruderRecord(String subject, IntruderRecord.Type type) {
            return null;
        }

        @Override
        public int recordCount() {
            return 0;

        }

        @Override
        public RecordIterator iterator() throws PwmOperationalException {
            return new RecordIterator(null);
        }
    }

    static class PwmDBRecordManager implements RecordManager {
        private PwmDBRecordStore recordStore;
        private TimeDuration setting_check_duration;
        private int setting_check_count;
        private TimeDuration setting_reset_duration;

        PwmDBRecordManager(PwmDBRecordStore intruderStore, TimeDuration setting_check_duration, int setting_check_count, TimeDuration setting_reset_duration) {
            this.recordStore = intruderStore;
            this.setting_check_duration = setting_check_duration;
            this.setting_check_count = setting_check_count;
            this.setting_reset_duration = setting_reset_duration;
        }

        public boolean checkSubject(final String subject) {
            return checkSubject(recordStore.read(subject));
        }

        private boolean checkSubject(final InternalRecord record) {
            if (record == null) {
                return false;
            }
            if (TimeDuration.fromCurrent(record.getTimeStamp()).isLongerThan(setting_reset_duration)) {
                return false;
            }
            if (record.getAttemptCount() >= setting_check_count) {
                return true;
            }
            return false;
        }

        public void markSubject(final String subject)
        {
            if (subject == null || subject.length() < 1) {
                return;
            }

            InternalRecord record = recordStore.read(subject);
            if (record == null) {
                record = new InternalRecord(subject);
            }
            if (record.isAlerted()) {
                return;
            }
            if (TimeDuration.fromCurrent(record.getTimeStamp()).isShorterThan(setting_check_duration)) {
                record.incrementAttemptCount();
            } else {
                record.clearAttemptCount();
                record.incrementAttemptCount();
            }

            try {
                recordStore.write(record);
            } catch (PwmOperationalException e) {
                LOGGER.warn("unexpected error attempting to mark subject '" + subject + "' to stored records: " + e.getMessage());
            }
        }

        public void clearSubject(final String subject) {
            final InternalRecord record = recordStore.read(subject);
            if (record == null) {
                return;
            }

            if (record.getAttemptCount() == 0) {
                return;
            }

            record.clearAttemptCount();
            try {
                recordStore.write(record);
            } catch (PwmOperationalException e) {
                LOGGER.warn("unexpected error attempting to clear subject '" + subject + "' from stored records: " + e.getMessage());
            }
        }

        public boolean isAlerted(final String subject) {
            final InternalRecord record = recordStore.read(subject);
            return record != null && record.isAlerted();
        }

        public void markAlerted(final String subject)
        {
            final InternalRecord record = recordStore.read(subject);
            if (record == null || record.isAlerted()) {
                return;
            }
            record.setAlerted();
            try {
                recordStore.write(record);
            } catch (PwmOperationalException e) {
                LOGGER.warn("unexpected error attempting to mark subject '" + subject + "' as alerted: " + e.getMessage());
            }
        }

        @Override
        public IntruderRecord readIntruderRecord(String subject, IntruderRecord.Type type) {
            final InternalRecord record = recordStore.read(subject);
            if (record == null) {
                return null;
            }

            return new IntruderRecord(type,record.getSubject(),record.getTimeStamp(),record.getAttemptCount());
        }

        @Override
        public int recordCount() {
            return recordStore.recordCount();
        }

        @Override
        public RecordIterator iterator() throws PwmOperationalException {
            return new RecordIterator(recordStore);
        }
    }

    public static class RecordIterator implements Iterator<IntruderRecord> {
        private LocalDB.PwmDBIterator<String> keyIterator;
        private PwmDBRecordStore recordStore;

        public RecordIterator(final PwmDBRecordStore recordStore) throws PwmOperationalException {
            this.recordStore = recordStore;
            if (recordStore != null) {
                this.keyIterator = recordStore.keyIterator();
            }
        }

        public boolean hasNext() {
            if (recordStore == null) {
                return false;
            }
            boolean hasNext =  keyIterator.hasNext();
            if (!hasNext) {
                close();
            }
            return hasNext;
        }

        public void close() {
            if (recordStore == null) {
                return;
            }
            keyIterator.close();
        }

        public IntruderRecord next() {
            if (recordStore == null) {
                return null;
            }

            final InternalRecord internalRecord = recordStore.readKey(keyIterator.next());
            if (internalRecord != null) {
                return internalRecord.asIntruderRecord(IntruderRecord.Type.userDN); //@todo dont hardcode type
            } else {
                return null;
            }
        }

        public void remove() {
        }
    }

    static class PwmDBRecordStore {
        private static final int MAX_REMOVALS_PER_CYCLE = 10 * 1000;

        private final IntruderManager intruderManager;
        private final LocalDB localDB;
        private final LocalDB.DB db;

        private Date eldestRecord = new Date(0);

        public PwmDBRecordStore(final IntruderManager intruderManager, LocalDB localDB, LocalDB.DB db) {
            this.intruderManager = intruderManager;
            this.localDB = localDB;
            this.db = db;
        }

        private InternalRecord read(final String subject) {
            if (subject == null || subject.length() < 1) {
                return null;
            }

            final String md5sum;
            try {
                md5sum = Helper.md5sum(subject);
            } catch (IOException e) {
                LOGGER.error("error generating md5sum for intruder record subject");
                return null;
            }

            return readKey(md5sum);
        }

        private InternalRecord readKey(final String key) {
            if (key == null || key.length() < 1) {
                return null;
            }

            final String value;
            try {
                value = localDB.get(db, key);
            } catch (LocalDBException e) {
                LOGGER.error("error reading stored intruder record: " + e.getMessage());
                return null;
            }

            if (value == null || value.length() < 1) {
                return null;
            }

            try {
                return new Gson().fromJson(value,InternalRecord.class);
            } catch (Exception e) {
                LOGGER.error("error decoding InternalRecord:" + e.getMessage());
            }

            //read failed, try to delete record
            try { localDB.remove(db, key); } catch (LocalDBException e) { /*noop*/ }
            return null;
        }

        private void write(final InternalRecord record) throws PwmOperationalException {
            final String md5sum;
            try {
                md5sum = Helper.md5sum(record.getSubject());
            } catch (IOException e) {
                throw new PwmOperationalException(PwmError.ERROR_UNKNOWN,"error generating md5sum for intruder record: " + e.getMessage());
            }

            final String jsonRecord = new Gson().toJson(record);
            try {
                localDB.put(db, md5sum, jsonRecord);
            } catch (LocalDBException e) {
                throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,"error writing to LocalDB: " + e.getMessage()));
            }
        }

        private LocalDB.PwmDBIterator<String> keyIterator() throws PwmOperationalException {
            try {
                return localDB.iterator(db);
            } catch (LocalDBException e) {
                throw new PwmOperationalException(PwmError.ERROR_UNKNOWN,"iterator unavailable:" + e.getMessage());
            }
        }

        private void cleanup(final TimeDuration maxRecordAge)
                throws LocalDBException
        {
            if (TimeDuration.fromCurrent(eldestRecord).isShorterThan(maxRecordAge)) {
                return;
            }
            eldestRecord = new Date();

            final long startTime = System.currentTimeMillis();
            int recordsExamined = 0;
            int recordsRemoved = 0;

            final List<String> recordsToRemove = new ArrayList<String>();
            boolean complete = false;

            while (!complete && intruderManager.status() == STATUS.OPEN) {
                LocalDB.PwmDBIterator<String> keyIterator = null;
                try {
                    keyIterator = localDB.iterator(db);
                    if (!keyIterator.hasNext()) {
                        complete = true;
                    }
                    while (keyIterator.hasNext() && recordsToRemove.size() < MAX_REMOVALS_PER_CYCLE && intruderManager.status == STATUS.OPEN) {
                        recordsExamined++;
                        final String key = keyIterator.next();
                        final InternalRecord record = readKey(key);
                        if (record != null) {
                            if (TimeDuration.fromCurrent(record.getTimeStamp()).isLongerThan(maxRecordAge)) {
                                recordsToRemove.add(key);
                            }
                            if (eldestRecord.compareTo(record.getTimeStamp()) == 1) {
                                eldestRecord = record.getTimeStamp();
                            }
                        }

                        if (!keyIterator.hasNext()) {
                            complete = true;
                        }
                    }
                } finally {
                    if (keyIterator != null) {
                        keyIterator.close();
                    }
                }
                localDB.removeAll(db, recordsToRemove);
                recordsRemoved += recordsToRemove.size();
                recordsToRemove.clear();
            }
            final TimeDuration totalDuration = TimeDuration.fromCurrent(startTime);
            LOGGER.trace("completed cleanup of " + db.toString() + " in " + totalDuration.asCompactString() + ", recordsExamined=" + recordsExamined + ", recordsRemoved=" + recordsRemoved);
        }

        private int recordCount() {
            try {
                return localDB.size(db);
            } catch (LocalDBException e) {
                LOGGER.error("error determining size count for records " + e.getMessage());
                return -2;
            }
        }
    }
}