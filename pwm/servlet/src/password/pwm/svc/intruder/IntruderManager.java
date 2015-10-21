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

package password.pwm.svc.intruder;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.option.IntruderStorageMethod;
import password.pwm.error.*;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.health.HealthTopic;
import password.pwm.http.PwmSession;
import password.pwm.ldap.LdapUserDataReader;
import password.pwm.ldap.UserStatusReader;
import password.pwm.svc.PwmService;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.SystemAuditRecord;
import password.pwm.svc.event.UserAuditRecord;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.*;
import password.pwm.util.db.DatabaseDataStore;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBDataStore;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.secure.PwmRandom;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.*;

// ------------------------------ FIELDS ------------------------------

public class IntruderManager implements Serializable, PwmService {
    private static final PwmLogger LOGGER = PwmLogger.forClass(IntruderManager.class);

    private PwmApplication pwmApplication;
    private STATUS status = STATUS.NEW;
    private ErrorInformation startupError;
    private Timer timer;

    private final Map<RecordType, RecordManager> recordManagers = new HashMap<>();

    private ServiceInfo serviceInfo = new ServiceInfo(Collections.<DataStorageMethod>emptyList());

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
        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.INTRUDER_ENABLE)) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,"intruder module not enabled");
            LOGGER.error(errorInformation.toDebugStr());
            status = STATUS.CLOSED;
            return;
        }
        final DataStore dataStore;
        {
            final IntruderStorageMethod intruderStorageMethod = pwmApplication.getConfig().readSettingAsEnum(PwmSetting.INTRUDER_STORAGE_METHOD, IntruderStorageMethod.class);
            final String debugMsg;
            final DataStorageMethod storageMethodUsed;
            switch (intruderStorageMethod) {
                case AUTO:
                    dataStore = DataStoreFactory.autoDbOrLocalDBstore(pwmApplication, DatabaseTable.INTRUDER, LocalDB.DB.INTRUDER);
                    if (dataStore instanceof DatabaseDataStore) {
                        debugMsg = "starting using auto-configured data store, Remote Database selected";
                        storageMethodUsed = DataStorageMethod.DB;
                    } else {
                        debugMsg = "starting using auto-configured data store, LocalDB selected";
                        storageMethodUsed = DataStorageMethod.LOCALDB;
                    }
                    break;

                case DATABASE:
                    dataStore = new DatabaseDataStore(pwmApplication.getDatabaseAccessor(), DatabaseTable.INTRUDER);
                    debugMsg = "starting using Remote Database data store";
                    storageMethodUsed = DataStorageMethod.DB;
                    break;

                case LOCALDB:
                    dataStore = new LocalDBDataStore(pwmApplication.getLocalDB(), LocalDB.DB.INTRUDER);
                    debugMsg = "starting using LocalDB data store";
                    storageMethodUsed = DataStorageMethod.LOCALDB;
                    break;

                default:
                    startupError = new ErrorInformation(PwmError.ERROR_UNKNOWN,"unknown storageMethod selected: " + intruderStorageMethod);
                    status = STATUS.CLOSED;
                    return;
            }
            LOGGER.info(debugMsg);
            serviceInfo = new ServiceInfo(Collections.singletonList(storageMethodUsed));
        }
        final RecordStore recordStore;
        {
            recordStore = new DataStoreRecordStore(dataStore, this);
            final String threadName = Helper.makeThreadName(pwmApplication, this.getClass()) + " timer";
            timer = new Timer(threadName, true);
            final long maxRecordAge = Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.INTRUDER_RETENTION_TIME_MS));
            final long cleanerRunFrequency = Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.INTRUDER_CLEANUP_FREQUENCY_MS));
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
                    recordManagers.put(RecordType.USER_ID, new RecordManagerImpl(RecordType.USER_ID, recordStore, settings));
                }
            }
            {
                final IntruderSettings settings = new IntruderSettings();
                settings.setCheckCount((int)config.readSettingAsLong(PwmSetting.INTRUDER_ATTRIBUTE_MAX_ATTEMPTS));
                settings.setResetDuration(new TimeDuration(1000 * config.readSettingAsLong(PwmSetting.INTRUDER_ATTRIBUTE_RESET_TIME)));
                settings.setCheckDuration(new TimeDuration(1000 * config.readSettingAsLong(PwmSetting.INTRUDER_ATTRIBUTE_CHECK_TIME)));
                if (settings.getCheckCount() == 0 || settings.getCheckDuration().getTotalMilliseconds() == 0 || settings.getResetDuration().getTotalMilliseconds() == 0) {
                    LOGGER.info("intruder user checking will remain disabled due to configuration settings");
                } else {
                    recordManagers.put(RecordType.ATTRIBUTE, new RecordManagerImpl(RecordType.ATTRIBUTE, recordStore, settings));
                }
            }
            {
                final IntruderSettings settings = new IntruderSettings();
                settings.setCheckCount((int)config.readSettingAsLong(PwmSetting.INTRUDER_TOKEN_DEST_MAX_ATTEMPTS));
                settings.setResetDuration(new TimeDuration(1000 * config.readSettingAsLong(PwmSetting.INTRUDER_TOKEN_DEST_RESET_TIME)));
                settings.setCheckDuration(new TimeDuration(1000 * config.readSettingAsLong(PwmSetting.INTRUDER_TOKEN_DEST_CHECK_TIME)));
                if (settings.getCheckCount() == 0 || settings.getCheckDuration().getTotalMilliseconds() == 0 || settings.getResetDuration().getTotalMilliseconds() == 0) {
                    LOGGER.info("intruder user checking will remain disabled due to configuration settings");
                } else {
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

    public void clear() {

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
            return Collections.singletonList(new HealthRecord(HealthStatus.WARN, HealthTopic.Application,"unable to start: " + startupError.toDebugStr()));
        }
        return Collections.emptyList();
    }

    public void check(final RecordType recordType, final String subject)
            throws PwmUnrecoverableException
    {
        if (recordType == null) {
            throw new IllegalArgumentException("recordType is required");
        }

        if (subject == null || subject.length() < 1) {
            return;
        }

        final RecordManager manager = recordManagers.get(recordType);
        final boolean locked = manager.checkSubject(subject);

        if (locked) {
            switch (recordType) {
                case ADDRESS:
                    throw new PwmUnrecoverableException(PwmError.ERROR_INTRUDER_ADDRESS);

                case ATTRIBUTE:
                    throw new PwmUnrecoverableException(PwmError.ERROR_INTRUDER_ATTR_SEARCH);

                case TOKEN_DEST:
                    throw new PwmUnrecoverableException(PwmError.ERROR_INTRUDER_TOKEN_DEST);
            }
            throw new PwmUnrecoverableException(PwmError.ERROR_INTRUDER_USER);
        }
    }

    public void clear(final RecordType recordType, final String subject)
            throws PwmUnrecoverableException
    {
        if (recordType == null) {
            throw new IllegalArgumentException("recordType is required");
        }

        if (subject == null || subject.length() < 1) {
            return;
        }

        final RecordManager manager = recordManagers.get(recordType);
        manager.clearSubject(subject);
    }

    public void mark(final RecordType recordType, final String subject, final SessionLabel sessionLabel)
            throws PwmUnrecoverableException
    {
        if (recordType == null) {
            throw new IllegalArgumentException("recordType is required");
        }

        if (subject == null || subject.length() < 1) {
            return;
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

        { // send intruder attempt audit event
            final Map<String,Object> messageObj = new LinkedHashMap<>();
            messageObj.put("type", recordType);
            messageObj.put("subject", subject);
            final String message = JsonUtil.serializeMap(messageObj);
            final SystemAuditRecord auditRecord = pwmApplication.getAuditManager().createSystemAuditRecord(AuditEvent.INTRUDER_ATTEMPT,message);
            pwmApplication.getAuditManager().submit(auditRecord);
        }

        try {
            check(recordType, subject);
        } catch (PwmUnrecoverableException e) {
            if (!manager.isAlerted(subject) ) {
                { // send intruder attempt lock event
                    final Map<String,Object> messageObj = new LinkedHashMap<>();
                    messageObj.put("type", recordType);
                    messageObj.put("subject", subject);
                    final String message = JsonUtil.serializeMap(messageObj);
                    final SystemAuditRecord auditRecord = pwmApplication.getAuditManager().createSystemAuditRecord(AuditEvent.INTRUDER_LOCK,message);
                    pwmApplication.getAuditManager().submit(auditRecord);
                }

                if (recordType == RecordType.USER_ID) {
                    final UserIdentity userIdentity = UserIdentity.fromKey(subject, pwmApplication);
                    final UserAuditRecord auditRecord = pwmApplication.getAuditManager().createUserAuditRecord(
                            AuditEvent.INTRUDER_USER,
                            userIdentity,
                            sessionLabel
                    );
                    pwmApplication.getAuditManager().submit(auditRecord);
                    sendAlert(manager.readIntruderRecord(subject), sessionLabel);
                }

                manager.markAlerted(subject);
                final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
                if (statisticsManager != null && statisticsManager.status() == STATUS.OPEN) {
                    statisticsManager.incrementValue(Statistic.INTRUDER_ATTEMPTS);
                    statisticsManager.updateEps(Statistic.EpsType.INTRUDER_ATTEMPTS,1);
                    statisticsManager.incrementValue(recordType.getLockStatistic());
                }
            }
            throw e;
        }

        delayPenalty(manager.readIntruderRecord(subject), sessionLabel == null ? null : sessionLabel);
    }


    private void delayPenalty(final IntruderRecord intruderRecord, final SessionLabel sessionLabel) {
        int points = 0;
        if (intruderRecord != null) {
            points += intruderRecord.getAttemptCount();
            long delayPenalty = Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.INTRUDER_MIN_DELAY_PENALTY_MS)); // minimum
            delayPenalty += points * Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.INTRUDER_DELAY_PER_COUNT_MS));
            delayPenalty += PwmRandom.getInstance().nextInt((int)Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.INTRUDER_DELAY_MAX_JITTER_MS))); // add some randomness;
            delayPenalty = delayPenalty > Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.INTRUDER_MAX_DELAY_PENALTY_MS)) ? Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.INTRUDER_MAX_DELAY_PENALTY_MS)) : delayPenalty;
            LOGGER.trace(sessionLabel, "delaying response " + delayPenalty + "ms due to intruder record: " + JsonUtil.serialize(intruderRecord));
            Helper.pause(delayPenalty);
        }
    }

    private void sendAlert(final IntruderRecord intruderRecord, final SessionLabel sessionLabel) {
        if (intruderRecord == null) {
            return;
        }

        if (intruderRecord.getType() == RecordType.USER_ID) {
            try {
                final UserIdentity identity = UserIdentity.fromDelimitedKey(intruderRecord.getSubject());
                sendIntruderNoticeEmail(pwmApplication, sessionLabel, identity);
            } catch (PwmUnrecoverableException e) {
                LOGGER.error("unable to send intruder mail, can't read userDN/ldapProfile from stored record: " + e.getMessage());
            }
        }
    }

    public List<Map<String,Object>> getRecords(final RecordType recordType, int maximum)
            throws PwmOperationalException
    {
        final RecordManager manager = recordManagers.get(recordType);
        final ArrayList<Map<String,Object>> returnList = new ArrayList<>();

        ClosableIterator<IntruderRecord> theIterator = null;
        try {
            theIterator = manager.iterator();
            while (theIterator.hasNext() && returnList.size() < maximum) {
                final IntruderRecord intruderRecord = theIterator.next();
                if (intruderRecord != null && intruderRecord.getType() == recordType) {
                    final Map<String, Object> rowData = new HashMap<>();
                    rowData.put("subject", intruderRecord.getSubject());
                    rowData.put("timestamp", intruderRecord.getTimeStamp());
                    rowData.put("count", String.valueOf(intruderRecord.getAttemptCount()));
                    try {
                        check(recordType, intruderRecord.getSubject());
                        rowData.put("status", "watching");
                    } catch (PwmException e) {
                        rowData.put("status", "locked");
                    }
                    returnList.add(rowData);
                }
            }
        } finally {
            if (theIterator != null) {
                theIterator.close();
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
                mark(RecordType.ADDRESS, subject, pwmSession.getLabel());
            }
        }

        public void checkAddressAndSession(final PwmSession pwmSession)
                throws PwmUnrecoverableException
        {
            if (pwmSession != null) {
                final String subject = pwmSession.getSessionStateBean().getSrcAddress();
                check(RecordType.ADDRESS, subject);
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
                clear(RecordType.ADDRESS, subject);
                pwmSession.getSessionStateBean().clearIntruderAttempts();
            }
        }

        public void markUserIdentity(final UserIdentity userIdentity, final SessionLabel sessionLabel)
                throws PwmUnrecoverableException
        {
            if (userIdentity != null) {
                final String subject = userIdentity.toDelimitedKey();
                mark(RecordType.USER_ID, subject, sessionLabel);
            }
        }

        public void markUserIdentity(final UserIdentity userIdentity, final PwmSession pwmSession)
                throws PwmUnrecoverableException
        {
            if (userIdentity != null) {
                final String subject = userIdentity.toDelimitedKey();
                mark(RecordType.USER_ID, subject, pwmSession.getLabel());
            }
        }

        public void checkUserIdentity(final UserIdentity userIdentity)
                throws PwmUnrecoverableException
        {
            if (userIdentity != null) {
                final String subject = userIdentity.toDelimitedKey();
                check(RecordType.USER_ID, subject);
            }
        }

        public void clearUserIdentity(final UserIdentity userIdentity)
                throws PwmUnrecoverableException
        {
            if (userIdentity != null) {
                final String subject = userIdentity.toDelimitedKey();
                clear(RecordType.USER_ID, subject);
            }
        }

        public void markAttributes(final Map<FormConfiguration, String> formValues, final PwmSession pwmSession)
                throws PwmUnrecoverableException
        {
            final List<String> subjects = attributeFormToList(formValues);
            for (String subject : subjects) {
                mark(RecordType.ATTRIBUTE, subject, pwmSession.getLabel());
            }
        }

        public void clearAttributes(final Map<FormConfiguration, String> formValues)
                throws PwmUnrecoverableException
        {
            final List<String> subjects = attributeFormToList(formValues);
            for (String subject : subjects) {
                clear(RecordType.ATTRIBUTE, subject);
            }
        }

        public void checkAttributes(final Map<FormConfiguration, String> formValues)
                throws PwmUnrecoverableException
        {
            final List<String> subjects = attributeFormToList(formValues);
            for (String subject : subjects) {
                check(RecordType.ATTRIBUTE, subject);
            }
        }

        private List<String> attributeFormToList(final Map<FormConfiguration, String> formValues) {
            final List<String> returnList = new ArrayList<>();
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

    private static void sendIntruderNoticeEmail(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
    {
        final Configuration config = pwmApplication.getConfig();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_INTRUDERNOTICE, PwmConstants.DEFAULT_LOCALE);

        if (configuredEmailSetting == null) {
            return;
        }

        try {
            final UserStatusReader userStatusReader = new UserStatusReader(pwmApplication, null);
            final UserInfoBean userInfoBean = userStatusReader.populateUserInfoBean(
                    PwmConstants.DEFAULT_LOCALE,
                    userIdentity
            );

            final MacroMachine macroMachine = new MacroMachine(
                    pwmApplication,
                    sessionLabel,
                    userInfoBean,
                    null,
                    LdapUserDataReader.appProxiedReader(pwmApplication, userIdentity));

            pwmApplication.getEmailQueue().submitEmail(configuredEmailSetting, userInfoBean, macroMachine);
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("error reading user info while sending intruder notice for user " + userIdentity + ", error: " + e.getMessage());
        }

    }

    public ServiceInfo serviceInfo()
    {
        return serviceInfo;
    }
}