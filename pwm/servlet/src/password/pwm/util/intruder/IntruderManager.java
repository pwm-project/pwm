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

package password.pwm.util.intruder;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import password.pwm.*;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.IntruderStorageMethod;
import password.pwm.error.*;
import password.pwm.event.AuditEvent;
import password.pwm.event.AuditRecord;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.util.*;
import password.pwm.util.db.DatabaseDataStore;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBDataStore;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.*;

// ------------------------------ FIELDS ------------------------------

public class IntruderManager implements Serializable, PwmService {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(IntruderManager.class);

    private PwmApplication pwmApplication;
    private STATUS status = STATUS.NEW;
    private ErrorInformation startupError;
    private Timer timer;

    private final Map<RecordType, RecordManager> recordManagers = new HashMap<RecordType, password.pwm.util.intruder.RecordManager>();

    public IntruderManager() {
        for (RecordType recordType : RecordType.values()) {
            recordManagers.put(recordType, new StubRecordManager());
        }
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
        final DataStore dataStore;
        {
            final IntruderStorageMethod intruderStorageMethod = pwmApplication.getConfig().readSettingAsEnum(PwmSetting.INTRUDER_STORAGE_METHOD, IntruderStorageMethod.class);
            final String debugMsg;
            switch (intruderStorageMethod) {
                case AUTO:
                    dataStore = DataStoreFactory.autoDbOrLocalDBstore(pwmApplication, DatabaseTable.INTRUDER, LocalDB.DB.INTRUDER);
                    if (dataStore instanceof DatabaseDataStore) {
                        debugMsg = "starting IntruderManager using auto-confgured data store, Remote Database selected";
                    } else {
                        debugMsg = "starting IntruderManager using auto-confgured data store, Remote Database selected";
                    }
                    break;

                case DATABASE:
                    dataStore = new DatabaseDataStore(pwmApplication.getDatabaseAccessor(), DatabaseTable.INTRUDER);
                    debugMsg = "starting IntruderManager using Remote Database data store";
                    break;

                case LOCALDB:
                    dataStore = new LocalDBDataStore(pwmApplication.getLocalDB(), LocalDB.DB.INTRUDER);
                    debugMsg = "starting IntruderManager using LocalDB data store";
                    break;

                default:
                    startupError = new ErrorInformation(PwmError.ERROR_UNKNOWN,"unknown storageMethod selected: " + intruderStorageMethod);
                    status = STATUS.CLOSED;
                    return;
            }
            LOGGER.info(debugMsg);

        }
        final RecordStore recordStore;
        {
            recordStore = new DataStoreRecordStore(dataStore, this);
            timer = new Timer(PwmConstants.PWM_APP_NAME + "-IntruderManager cleaner",true);
            final long maxRecordAge = Long.parseLong(pwmApplication.readAppProperty(AppProperty.INTRUDER_RETENTION_TIME_MS));
            final long cleanerRunFrequency = Long.parseLong(pwmApplication.readAppProperty(AppProperty.INTRUDER_CLEANUP_FREQUENCY_MS));
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        recordStore.cleanup(new TimeDuration(maxRecordAge));
                    } catch (Exception e) {
                        LOGGER.error("error cleaning recordStore: " + e.getMessage(),e);
                    }
                }
            },1000,cleanerRunFrequency);
        }

        try {
            {
                final IntruderSettings settings = new IntruderSettings();
                settings.setCheckCount((int)config.readSettingAsLong(PwmSetting.INTRUDER_USER_MAX_ATTEMPTS));
                settings.setResetDuration(new TimeDuration(1000 * config.readSettingAsLong(PwmSetting.INTRUDER_USER_RESET_TIME)));
                settings.setCheckDuration(new TimeDuration(1000 * config.readSettingAsLong(PwmSetting.INTRUDER_USER_CHECK_TIME)));
                if (settings.getCheckCount() == 0 || settings.getCheckDuration().getTotalMilliseconds() == 0 || settings.getResetDuration().getTotalMilliseconds() == 0) {
                    LOGGER.info("intruder user checking will remain disabled due to configuration settings");
                } else {
                    recordManagers.put(RecordType.USERNAME, new RecordManagerImpl(RecordType.USERNAME, recordStore, settings));
                    recordManagers.put(RecordType.USER_DN, new RecordManagerImpl(RecordType.USER_DN, recordStore, settings));
                    recordManagers.put(RecordType.ATTRIBUTE, new RecordManagerImpl(RecordType.ATTRIBUTE, recordStore, settings));
                    recordManagers.put(RecordType.TOKEN_DEST, new RecordManagerImpl(RecordType.TOKEN_DEST, recordStore, settings));
                }
            }
            {
                final IntruderSettings settings = new IntruderSettings();
                settings.setCheckCount((int)config.readSettingAsLong(PwmSetting.INTRUDER_ADDRESS_MAX_ATTEMPTS));
                settings.setResetDuration(new TimeDuration(1000 * config.readSettingAsLong(PwmSetting.INTRUDER_ADDRESS_RESET_TIME)));
                settings.setCheckDuration(new TimeDuration(1000 * config.readSettingAsLong(PwmSetting.INTRUDER_ADDRESS_CHECK_TIME)));
                if (settings.getCheckCount() == 0 || settings.getCheckDuration().getTotalMilliseconds() == 0 || settings.getResetDuration().getTotalMilliseconds() == 0) {
                    LOGGER.info("intruder address checking will remain disabled due to configuration settings");
                } else {
                    recordManagers.put(RecordType.ADDRESS, new RecordManagerImpl(RecordType.ADDRESS, recordStore, settings));
                }
            }
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

    public void check(final RecordType recordType, final String subject, final PwmSession pwmSession)
            throws PwmUnrecoverableException
    {
        if (recordType == null) {
            throw new IllegalArgumentException("recordType is required");
        }

        if (subject == null || subject.length() < 1) {
            throw new IllegalArgumentException("subject is required");
        }

        final RecordManager manager = recordManagers.get(recordType);
        final boolean locked = manager.checkSubject(subject);

        if (locked) {
            if (!manager.isAlerted(subject) ) {
                if (recordType == RecordType.USER_DN) {
                    try {
                        final ChaiUser chaiUser = ChaiFactory.createChaiUser(subject, pwmApplication.getProxyChaiProvider());
                        final String userID = Helper.readLdapUserIDValue(pwmApplication,chaiUser);
                        final AuditRecord auditRecord = new AuditRecord(AuditEvent.INTRUDER_LOCK ,userID, subject, pwmSession);
                        pwmApplication.getAuditManager().submitAuditRecord(auditRecord);
                    } catch (ChaiException e) {
                        LOGGER.error("unexpected ldap error generating audit lockout event: " + e.getMessage());
                    }
                }
                sendAlert(manager.readIntruderRecord(subject));
                manager.markAlerted(subject);

                final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
                if (statisticsManager != null && statisticsManager.status() == STATUS.OPEN) {
                    pwmApplication.getStatisticsManager().incrementValue(Statistic.INTRUDER_ATTEMPTS);
                    pwmApplication.getStatisticsManager().updateEps(Statistic.EpsType.INTRUDER_ATTEMPTS,1);
                }
            }

            if (recordType == RecordType.ADDRESS) {
                throw new PwmUnrecoverableException(PwmError.ERROR_INTRUDER_ADDRESS);
            } else {
                throw new PwmUnrecoverableException(PwmError.ERROR_INTRUDER_USER);
            }
        }
    }

    public void clear(final RecordType recordType, final String subject, final PwmSession pwmSession)
            throws PwmUnrecoverableException
    {
        if (recordType == null) {
            throw new IllegalArgumentException("recordType is required");
        }

        if (subject == null || subject.length() < 1) {
            throw new IllegalArgumentException("subject is required");
        }

        final RecordManager manager = recordManagers.get(recordType);
        manager.clearSubject(subject);
    }

    public void mark(final RecordType recordType, final String subject, final PwmSession pwmSession)
            throws PwmUnrecoverableException
    {
        if (recordType == null) {
            throw new IllegalArgumentException("recordType is required");
        }

        if (subject == null || subject.length() < 1) {
            throw new IllegalArgumentException("subject is required");
        }

        if (recordType == RecordType.ADDRESS) {
            try {
                final InetAddress inetAddress = InetAddress.getByName(subject);
                if (inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress()) {
                    LOGGER.debug("disregarding local address intruder attempt from: " + subject);
                    return;
                }
            } catch(Exception e) {
                LOGGER.error("error examining address: " + subject);
            }
        }

        final RecordManager manager = recordManagers.get(recordType);
        manager.markSubject(subject);
        check(recordType, subject, pwmSession);
        delayPenalty(manager.readIntruderRecord(subject), pwmSession);
    }


    private void delayPenalty(final IntruderRecord intruderRecord, final PwmSession pwmSession) {
        int points = 0;
        if (intruderRecord != null) {
            points += intruderRecord.getAttemptCount();
            long delayPenalty = Long.parseLong(pwmApplication.readAppProperty(AppProperty.INTRUDER_MIN_DELAY_PENALTY_MS)); // minimum
            delayPenalty += points * Long.parseLong(pwmApplication.readAppProperty(AppProperty.INTRUDER_DELAY_PER_COUNT_MS));
            delayPenalty += PwmRandom.getInstance().nextInt((int)Long.parseLong(pwmApplication.readAppProperty(AppProperty.INTRUDER_DELAY_MAX_JITTER_MS))); // add some randomness;
            delayPenalty = delayPenalty > Long.parseLong(pwmApplication.readAppProperty(AppProperty.INTRUDER_MAX_DELAY_PENALTY_MS)) ? Long.parseLong(pwmApplication.readAppProperty(AppProperty.INTRUDER_MAX_DELAY_PENALTY_MS)) : delayPenalty;
            LOGGER.trace(pwmSession, "delaying response " + delayPenalty + "ms due to intruder record: " + Helper.getGson().toJson(intruderRecord));
            Helper.pause(delayPenalty);
        }
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

    public List<Map<String,Object>> getRecords(final RecordType recordType, int maximum)
            throws PwmOperationalException
    {
        final RecordManager manager = recordManagers.get(recordType);
        final ArrayList<Map<String,Object>> returnList = new ArrayList<Map<String,Object>>();
        for (final Iterator<IntruderRecord> theIterator = manager.iterator(); theIterator.hasNext() && returnList.size() < maximum; ) {
            final IntruderRecord intruderRecord = theIterator.next();
            if (intruderRecord != null && intruderRecord.getType() == recordType) {
                final Map<String,Object> rowData = new HashMap<String,Object>();
                rowData.put("subject",intruderRecord.getSubject());
                rowData.put("timestamp",intruderRecord.getTimeStamp());
                rowData.put("count",String.valueOf(intruderRecord.getAttemptCount()));
                try {
                    check(recordType, intruderRecord.getSubject(), null);
                    rowData.put("status","watching");
                } catch (PwmException e) {
                    rowData.put("status","locked");
                }
                returnList.add(rowData);
            }
        }
        return returnList;
    }

    public Convenience convenience() {
        return new Convenience();
    }

    public class Convenience {
        protected Convenience() {
        }

        public void markAddressAndSession(final PwmSession pwmSession)
                throws PwmUnrecoverableException
        {
            if (pwmSession != null) {
                final String subject = pwmSession.getSessionStateBean().getSrcAddress();
                pwmSession.getSessionStateBean().incrementIntruderAttempts();
                mark(RecordType.ADDRESS, subject, pwmSession);
            }
        }

        public void checkAddressAndSession(final PwmSession pwmSession)
                throws PwmUnrecoverableException
        {
            if (pwmSession != null) {
                final String subject = pwmSession.getSessionStateBean().getSrcAddress();
                check(RecordType.ADDRESS, subject, pwmSession);
                final int maxAllowedAttempts = (int)pwmApplication.getConfig().readSettingAsLong(PwmSetting.INTRUDER_SESSION_MAX_ATTEMPTS);
                if (maxAllowedAttempts != 0 && pwmSession.getSessionStateBean().getIntruderAttempts() > maxAllowedAttempts) {
                    throw new PwmUnrecoverableException(PwmError.ERROR_INTRUDER_SESSION);
                }
            }
        }

        public void clearAddressAndSession(final PwmSession pwmSession)
                throws PwmUnrecoverableException
        {
            if (pwmSession != null) {
                final String subject = pwmSession.getSessionStateBean().getSrcAddress();
                clear(RecordType.ADDRESS, subject, pwmSession);
                pwmSession.getSessionStateBean().clearIntruderAttempts();
            }
        }

        public void markAttributes(final Map<FormConfiguration, String> formValues, final PwmSession pwmSession)
                throws PwmUnrecoverableException
        {
            final List<String> subjects = attributeFormToList(formValues);
            for (String subject : subjects) {
                mark(RecordType.ATTRIBUTE, subject, pwmSession);
            }
        }

        public void clearAttributes(final Map<FormConfiguration, String> formValues, final PwmSession pwmSession)
                throws PwmUnrecoverableException
        {
            final List<String> subjects = attributeFormToList(formValues);
            for (String subject : subjects) {
                clear(RecordType.ATTRIBUTE, subject, pwmSession);
            }
        }

        public void checkAttributes(final Map<FormConfiguration, String> formValues, final PwmSession pwmSession)
                throws PwmUnrecoverableException
        {
            final List<String> subjects = attributeFormToList(formValues);
            for (String subject : subjects) {
                check(RecordType.ATTRIBUTE, subject, pwmSession);
            }
        }

        private List<String> attributeFormToList(final Map<FormConfiguration, String> formValues) {
            final List<String> returnList = new ArrayList<String>();
            if (formValues != null) {
                for (final FormConfiguration formConfiguration : formValues.keySet()) {
                    final String value = formValues.get(formConfiguration);
                    if (value != null && value.length() > 0) {
                        returnList.add(formConfiguration.getName() + ":" + value);
                    }
                }
            }
            return returnList;
        }

    }
}