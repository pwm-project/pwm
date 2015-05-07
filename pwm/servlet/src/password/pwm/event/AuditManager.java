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

package password.pwm.event;

import org.apache.commons.csv.CSVPrinter;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmService;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.option.UserEventStorageMethod;
import password.pwm.error.*;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.health.HealthTopic;
import password.pwm.http.PwmSession;
import password.pwm.i18n.LocaleHelper;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.util.Helper;
import password.pwm.util.JsonUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.*;

public class AuditManager implements PwmService {
    private static final PwmLogger LOGGER = PwmLogger.forClass(AuditManager.class);

    private STATUS status = STATUS.NEW;
    private Settings settings = new Settings();
    private ServiceInfo serviceInfo = new ServiceInfo(Collections.<DataStorageMethod>emptyList());

    private SyslogAuditService syslogManager;
    private ErrorInformation lastError;
    private UserHistoryStore userHistoryStore;
    private AuditVault auditVault;

    private PwmApplication pwmApplication;

    public AuditManager() {
    }

    public HelpdeskAuditRecord createHelpdeskAuditRecord(
            final AuditEvent eventCode,
            final UserIdentity perpetrator,
            final String message,
            final UserIdentity target,
            final String sourceAddress,
            final String sourceHost
    )
    {
        String perpUserDN = null, perpUserID = null, perpLdapProfile = null, targetUserDN = null, targetUserID = null, targetLdapProfile = null;
        if (perpetrator != null) {
            perpUserDN = perpetrator.getUserDN();
            perpLdapProfile = perpetrator.getLdapProfileID();
            try {
                perpUserID = LdapOperationsHelper.readLdapUsernameValue(pwmApplication,perpetrator);
            } catch (Exception e) {
                LOGGER.error("unable to read userID for " + perpetrator + ", error: " + e.getMessage());
            }
        }
        if (target != null) {
            targetUserDN = target.getUserDN();
            targetLdapProfile = target.getLdapProfileID();
            try {
                targetUserID = LdapOperationsHelper.readLdapUsernameValue(pwmApplication,target);
            } catch (Exception e) {
                LOGGER.error("unable to read userID for " + perpetrator + ", error: " + e.getMessage());
            }
        }

        return HelpdeskAuditRecord.create(eventCode, perpUserID, perpUserDN, perpLdapProfile, message, targetUserID, targetUserDN,
                targetLdapProfile, sourceAddress, sourceHost);
    }

    public UserAuditRecord createUserAuditRecord(
            final AuditEvent eventCode,
            final UserIdentity perpetrator,
            final String message,
            final String sourceAddress,
            final String sourceHost
    )
    {
        String perpUserDN = null, perpUserID = null, perpLdapProfile = null, targetUserDN = null, targetUserID = null, targetLdapProfile = null;
        if (perpetrator != null) {
            perpUserDN = perpetrator.getUserDN();
            perpLdapProfile = perpetrator.getLdapProfileID();
            try {
                perpUserID = LdapOperationsHelper.readLdapUsernameValue(pwmApplication,perpetrator);
            } catch (Exception e) {
                LOGGER.error("unable to read userID for " + perpetrator + ", error: " + e.getMessage());
            }
        }

        return HelpdeskAuditRecord.create(eventCode, perpUserID, perpUserDN, perpLdapProfile, message, targetUserID, targetUserDN,
                targetLdapProfile, sourceAddress, sourceHost);
    }

    public SystemAuditRecord createSystemAuditRecord(
            final AuditEvent eventCode,
            final String message
    )
    {
        return SystemAuditRecord.create(eventCode, message, pwmApplication.getInstanceID());
    }

    public UserAuditRecord createUserAuditRecord(
            final AuditEvent eventCode,
            final UserIdentity perpetrator,
            final SessionLabel sessionLabel
    )
    {
        return createUserAuditRecord(
                eventCode,
                perpetrator,
                sessionLabel,
                null
        );
    }

    public UserAuditRecord createUserAuditRecord(
            final AuditEvent eventCode,
            final UserIdentity perpetrator,
            final SessionLabel sessionLabel,
            final String message
    )
    {
        return createUserAuditRecord(
                eventCode,
                perpetrator,
                message,
                sessionLabel != null ? sessionLabel.getSrcAddress() : null,
                sessionLabel != null ? sessionLabel.getSrcHostname() : null
        );
    }

    public UserAuditRecord createUserAuditRecord(
            final AuditEvent eventCode,
            final UserInfoBean userInfoBean,
            final PwmSession pwmSession
    )
    {
        return createUserAuditRecord(
                eventCode,
                userInfoBean.getUserIdentity(),
                null,
                pwmSession.getSessionStateBean().getSrcAddress(),
                pwmSession.getSessionStateBean().getSrcHostname()
        );
    }


    public STATUS status() {
        return status;
    }

    public void init(PwmApplication pwmApplication) throws PwmException {
        this.status = STATUS.OPENING;
        this.pwmApplication = pwmApplication;

        settings.systemEmailAddresses = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.AUDIT_EMAIL_SYSTEM_TO);
        settings.userEmailAddresses = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.AUDIT_EMAIL_USER_TO);
        settings.alertFromAddress = pwmApplication.getConfig().readAppProperty(AppProperty.AUDIT_EVENTS_EMAILFROM);
        settings.permittedEvents = figurePermittedEvents(pwmApplication.getConfig());

        if (pwmApplication.getApplicationMode() == null || pwmApplication.getApplicationMode() == PwmApplication.MODE.READ_ONLY) {
            this.status = STATUS.CLOSED;
            LOGGER.warn("unable to start - Application is in read-only mode");
            return;
        }

        if (pwmApplication.getLocalDB() == null || pwmApplication.getLocalDB().status() != LocalDB.Status.OPEN) {
            this.status = STATUS.CLOSED;
            LOGGER.warn("unable to start - LocalDB is not available");
            return;
        }

        final String syslogConfigString = pwmApplication.getConfig().readSettingAsString(PwmSetting.AUDIT_SYSLOG_SERVERS);
        if (syslogConfigString != null && !syslogConfigString.isEmpty()) {
            try {
                syslogManager = new SyslogAuditService(pwmApplication);
            } catch (Exception e) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SYSLOG_WRITE_ERROR, "startup error: " + e.getMessage());
                LOGGER.error(errorInformation.toDebugStr());
            }
        }
        {
            final UserEventStorageMethod userEventStorageMethod = pwmApplication.getConfig().readSettingAsEnum(PwmSetting.EVENTS_USER_STORAGE_METHOD, UserEventStorageMethod.class);
            final String debugMsg;
            final DataStorageMethod storageMethodUsed;
            switch (userEventStorageMethod) {
                case AUTO:
                    if (pwmApplication.getConfig().hasDbConfigured()) {
                        debugMsg = "starting using auto-configured data store, Remote Database selected";
                        this.userHistoryStore = new DatabaseUserHistory(pwmApplication);
                        storageMethodUsed = DataStorageMethod.DB;
                    } else {
                        debugMsg = "starting using auto-configured data store, LDAP selected";
                        this.userHistoryStore = new LdapXmlUserHistory(pwmApplication);
                        storageMethodUsed = DataStorageMethod.LDAP;
                    }
                    break;

                case DATABASE:
                    this.userHistoryStore = new DatabaseUserHistory(pwmApplication);
                    debugMsg = "starting using Remote Database data store";
                    storageMethodUsed = DataStorageMethod.DB;
                    break;

                case LDAP:
                    this.userHistoryStore = new LdapXmlUserHistory(pwmApplication);
                    debugMsg = "starting using LocalDB data store";
                    storageMethodUsed = DataStorageMethod.LDAP;
                    break;

                default:
                    lastError = new ErrorInformation(PwmError.ERROR_UNKNOWN,"unknown storageMethod selected: " + userEventStorageMethod);
                    status = STATUS.CLOSED;
                    return;
            }
            LOGGER.info(debugMsg);
            serviceInfo = new ServiceInfo(Collections.singletonList(storageMethodUsed));
        }
        {
            final TimeDuration maxRecordAge = new TimeDuration(pwmApplication.getConfig().readSettingAsLong(PwmSetting.EVENTS_AUDIT_MAX_AGE) * 1000);
            final int maxRecords = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.AUDIT_VAULT_MAX_RECORDS));
            final AuditVault.Settings settings = new AuditVault.Settings(
                    maxRecords,
                    maxRecordAge
            );

            if (pwmApplication.getLocalDB() != null && pwmApplication.getApplicationMode() != PwmApplication.MODE.READ_ONLY) {
                auditVault = new LocalDbAuditVault(pwmApplication, pwmApplication.getLocalDB());
                auditVault.init(settings);
            }
        }

        this.status = STATUS.OPEN;
    }

    @Override
    public void close() {
        if (syslogManager != null) {
            syslogManager.close();
        }
        this.status = STATUS.CLOSED;
    }

    @Override
    public List<HealthRecord> healthCheck() {
        if (status != STATUS.OPEN) {
            return Collections.emptyList();
        }

        final List<HealthRecord> healthRecords = new ArrayList<>();
        if (syslogManager != null) {
            healthRecords.addAll(syslogManager.healthCheck());
        }

        if (lastError != null) {
            healthRecords.add(new HealthRecord(HealthStatus.WARN, HealthTopic.Audit, lastError.toDebugStr()));
        }

        return healthRecords;
    }

    public Iterator<AuditRecord> readVault() {
        return auditVault.readVault();
    }

    public List<UserAuditRecord> readUserHistory(final PwmSession pwmSession)
            throws PwmUnrecoverableException
    {
        return readUserHistory(pwmSession.getUserInfoBean());
    }

    public List<UserAuditRecord> readUserHistory(final UserInfoBean userInfoBean)
            throws PwmUnrecoverableException
    {
        return userHistoryStore.readUserHistory(userInfoBean);
    }

    protected void sendAsEmail(final AuditRecord record)
            throws PwmUnrecoverableException
    {
        if (record == null || record.getEventCode() == null) {
            return;
        }
        if (settings.alertFromAddress == null || settings.alertFromAddress.length() < 1) {
            return;
        }

        switch (record.getEventCode().getType()) {
            case SYSTEM:
                for (final String toAddress : settings.systemEmailAddresses) {
                    sendAsEmail(pwmApplication, null, record, toAddress, settings.alertFromAddress);
                }
                break;

            case USER:
                for (final String toAddress : settings.userEmailAddresses) {
                    sendAsEmail(pwmApplication, null, record, toAddress, settings.alertFromAddress);
                }
                break;
        }
    }

    private static void sendAsEmail(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final AuditRecord record,
            final String toAddress,
            final String fromAddress

    )
            throws PwmUnrecoverableException
    {
        final String subject = PwmConstants.PWM_APP_NAME + " - Audit Event - " + record.getEventCode().toString();

        final StringBuilder body = new StringBuilder();
        final String jsonRecord = JsonUtil.serialize(record);
        HashMap<String,Serializable> mapRecord = new HashMap<>();
        mapRecord = JsonUtil.deserialize(jsonRecord,mapRecord.getClass());

        for (final String key : mapRecord.keySet()) {
            body.append(key);
            body.append("=");
            body.append(mapRecord.get(key));
            body.append("\n");
        }

        final EmailItemBean emailItem = new EmailItemBean(toAddress, fromAddress, subject, body.toString(), null);
        final MacroMachine macroMachine = MacroMachine.forNonUserSpecific(pwmApplication, sessionLabel);
        pwmApplication.getEmailQueue().submitEmail(emailItem, null, macroMachine);
    }

    public int vaultSize() {
        if (status != STATUS.OPEN || auditVault == null) {
            return -1;
        }

        return auditVault.size();
    }

    public void submit(final AuditEvent auditEvent, final UserInfoBean userInfoBean, final PwmSession pwmSession)
            throws PwmUnrecoverableException
    {
        final UserAuditRecord auditRecord = createUserAuditRecord(auditEvent, userInfoBean, pwmSession);
        submit(auditRecord);
    }

    public void submit(final AuditRecord auditRecord)
            throws PwmUnrecoverableException
    {

        final String jsonRecord = JsonUtil.serialize(auditRecord);

        if (status != STATUS.OPEN) {
            LOGGER.debug("discarding audit event (AuditManager is not open); event=" + jsonRecord);
            return;
        }

        if (auditRecord.getEventCode() == null) {
            LOGGER.error("discarding audit event, missing event type; event=" + jsonRecord);
            return;
        }

        if (!settings.permittedEvents.contains(auditRecord.getEventCode())) {
            LOGGER.debug("discarding event, " + auditRecord.getEventCode() + " are being ignored; event=" + jsonRecord);
            return;
        }

        // add to debug log
        LOGGER.info("audit event: " + jsonRecord);

        // add to audit db
        if (auditVault != null) {
            auditVault.add(auditRecord);
        }

        // email alert
        sendAsEmail(auditRecord);

        // add to user ldap record
        if (auditRecord instanceof UserAuditRecord && auditRecord.getEventCode().isStoreOnUser()) {
            userHistoryStore.updateUserHistory((UserAuditRecord)auditRecord);
        }

        // send to syslog
        if (syslogManager != null) {
            try {
                syslogManager.add(auditRecord);
            } catch (PwmOperationalException e) {
                lastError = e.getErrorInformation();
            }
        }
    }


    public int outputVaultToCsv(OutputStream outputStream, final Locale locale, final boolean includeHeader)
            throws IOException
    {
        final Configuration config = null;

        final CSVPrinter csvPrinter = Helper.makeCsvPrinter(outputStream);

        csvPrinter.printComment(" " + PwmConstants.PWM_APP_NAME + " audit record output ");
        csvPrinter.printComment(" " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date()));

        if (includeHeader) {
            final List<String> headers = new ArrayList<>();
            headers.add("Type");
            headers.add(LocaleHelper.getLocalizedMessage(locale,"Field_Audit_EventCode",config,password.pwm.i18n.Admin.class));
            headers.add(LocaleHelper.getLocalizedMessage(locale,"Field_Audit_Timestamp",config,password.pwm.i18n.Admin.class));
            headers.add(LocaleHelper.getLocalizedMessage(locale,"Field_Audit_GUID",config,password.pwm.i18n.Admin.class));
            headers.add(LocaleHelper.getLocalizedMessage(locale,"Field_Audit_Message",config,password.pwm.i18n.Admin.class));
            headers.add(LocaleHelper.getLocalizedMessage(locale,"Field_Audit_Instance",config,password.pwm.i18n.Admin.class));
            headers.add(LocaleHelper.getLocalizedMessage(locale,"Field_Audit_PerpetratorID",config,password.pwm.i18n.Admin.class));
            headers.add(LocaleHelper.getLocalizedMessage(locale,"Field_Audit_PerpetratorDN",config,password.pwm.i18n.Admin.class));
            headers.add(LocaleHelper.getLocalizedMessage(locale,"Field_Audit_TargetID",config,password.pwm.i18n.Admin.class));
            headers.add(LocaleHelper.getLocalizedMessage(locale,"Field_Audit_TargetDN",config,password.pwm.i18n.Admin.class));
            headers.add(LocaleHelper.getLocalizedMessage(locale,"Field_Audit_SourceAddress",config,password.pwm.i18n.Admin.class));
            headers.add(LocaleHelper.getLocalizedMessage(locale,"Field_Audit_SourceHost",config,password.pwm.i18n.Admin.class));
            csvPrinter.printRecord(headers);
        }

        int counter = 0;
        for (final Iterator<AuditRecord> recordIterator = readVault(); recordIterator.hasNext();) {
            final AuditRecord loopRecord = recordIterator.next();
            counter++;

            final List<String> lineOutput = new ArrayList<>();
            lineOutput.add(loopRecord.getEventCode().getType().toString());
            lineOutput.add(loopRecord.getEventCode().toString());
            lineOutput.add(PwmConstants.DEFAULT_DATETIME_FORMAT.format(loopRecord.getTimestamp()));
            lineOutput.add(loopRecord.getGuid());
            lineOutput.add(loopRecord.getMessage() == null ? "" : loopRecord.getMessage());
            if (loopRecord instanceof SystemAuditRecord) {
                lineOutput.add(((SystemAuditRecord)loopRecord).getInstance());
            }
            if (loopRecord instanceof UserAuditRecord) {
                lineOutput.add(((UserAuditRecord)loopRecord).getPerpetratorID());
                lineOutput.add(((UserAuditRecord)loopRecord).getPerpetratorDN());
                lineOutput.add("");
                lineOutput.add("");
                lineOutput.add(((UserAuditRecord)loopRecord).getSourceAddress());
                lineOutput.add(((UserAuditRecord)loopRecord).getSourceHost());
            }
            if (loopRecord instanceof HelpdeskAuditRecord) {
                lineOutput.add(((HelpdeskAuditRecord)loopRecord).getPerpetratorID());
                lineOutput.add(((HelpdeskAuditRecord)loopRecord).getPerpetratorDN());
                lineOutput.add(((HelpdeskAuditRecord)loopRecord).getTargetID());
                lineOutput.add(((HelpdeskAuditRecord)loopRecord).getTargetDN());
                lineOutput.add(((HelpdeskAuditRecord)loopRecord).getSourceAddress());
                lineOutput.add(((HelpdeskAuditRecord)loopRecord).getSourceHost());
            }
            csvPrinter.printRecord(lineOutput);
        }
        csvPrinter.flush();

        return counter;
    }

    private static class Settings {
        private List<String> systemEmailAddresses = new ArrayList<>();
        private List<String> userEmailAddresses = new ArrayList<>();
        private String alertFromAddress = "";
        private Set<AuditEvent> permittedEvents = new HashSet<>();
    }

    public ServiceInfo serviceInfo()
    {
        return serviceInfo;
    }

    public int syslogQueueSize() {
        return syslogManager != null ? syslogManager.queueSize() : 0;
    }

    private static Set<AuditEvent> figurePermittedEvents(final Configuration configuration) {
        final Set<AuditEvent> eventSet = new HashSet<>();
        eventSet.addAll(configuration.readSettingAsOptionList(PwmSetting.AUDIT_SYSTEM_EVENTS,AuditEvent.class));
        eventSet.addAll(configuration.readSettingAsOptionList(PwmSetting.AUDIT_USER_EVENTS,AuditEvent.class));
        return Collections.unmodifiableSet(eventSet);
    }
}
