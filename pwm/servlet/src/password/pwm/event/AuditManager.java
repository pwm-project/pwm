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

package password.pwm.event;

import com.google.gson.reflect.TypeToken;
import password.pwm.*;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.UserEventStorageMethod;
import password.pwm.error.*;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;
import password.pwm.util.csv.CsvWriter;
import password.pwm.util.localdb.LocalDB;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

public class AuditManager implements PwmService {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(AuditManager.class);

    private STATUS status = STATUS.NEW;
    private Settings settings = new Settings();

    private SyslogAuditService syslogManager;
    private ErrorInformation lastError;
    private UserHistoryStore userHistoryStore;
    private AuditVault auditVault;

    private PwmApplication pwmApplication;

    public AuditManager() {
    }

    @Override
    public STATUS status() {
        return status;
    }

    @Override
    public void init(PwmApplication pwmApplication) throws PwmException {
        this.status = STATUS.OPENING;
        this.pwmApplication = pwmApplication;

        settings.systemEmailAddresses = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.AUDIT_EMAIL_SYSTEM_TO);
        settings.userEmailAddresses = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.AUDIT_EMAIL_USER_TO);
        settings.alertFromAddress  = pwmApplication.getConfig().readAppProperty(AppProperty.AUDIT_EVENTS_EMAILFROM);

        if (pwmApplication.getLocalDB() == null || pwmApplication.getLocalDB().status() != LocalDB.Status.OPEN) {
            this.status = STATUS.CLOSED;
            LOGGER.warn("unable to start - LocalDB is not available");
            return;
        }

        final List<String> syslogConfigStrings = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.AUDIT_SYSLOG_SERVERS);
        if (!syslogConfigStrings.isEmpty()) {
            try {
                syslogManager = new SyslogAuditService(pwmApplication.getConfig());
            } catch (Exception e) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SYSLOG_WRITE_ERROR, "startup error: " + e.getMessage());
                LOGGER.error(errorInformation.toDebugStr());
            }
        }
        {
            final UserEventStorageMethod userEventStorageMethod = pwmApplication.getConfig().readSettingAsEnum(PwmSetting.INTRUDER_STORAGE_METHOD, UserEventStorageMethod.class);
            final String debugMsg;
            switch (userEventStorageMethod) {
                case AUTO:
                    if (pwmApplication.getConfig().hasDbConfigured()) {
                        debugMsg = "starting using auto-configured data store, Remote Database selected";
                        this.userHistoryStore = new DatabaseUserHistory(pwmApplication);
                    } else {
                        debugMsg = "starting using auto-configured data store, LDAP selected";
                        this.userHistoryStore = new LdapXmlUserHistory(pwmApplication);
                    }
                    break;

                case DATABASE:
                    this.userHistoryStore = new DatabaseUserHistory(pwmApplication);
                    debugMsg = "starting using Remote Database data store";
                    break;

                case LDAP:
                    this.userHistoryStore = new LdapXmlUserHistory(pwmApplication);
                    debugMsg = "starting using LocalDB data store";
                    break;

                default:
                    lastError = new ErrorInformation(PwmError.ERROR_UNKNOWN,"unknown storageMethod selected: " + userEventStorageMethod);
                    status = STATUS.CLOSED;
                    return;
            }
            LOGGER.info(debugMsg);

        }
        {
            final TimeDuration maxRecordAge = new TimeDuration(pwmApplication.getConfig().readSettingAsLong(PwmSetting.EVENTS_AUDIT_MAX_AGE) * 1000);
            final int maxRecords = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.AUDIT_VAULT_MAX_RECORDS));
            final AuditVault.Settings settings = new AuditVault.Settings(
                    maxRecords,
                    maxRecordAge
            );

            if (pwmApplication.getLocalDB() != null && pwmApplication.getApplicationMode() != PwmApplication.MODE.READ_ONLY) {
                auditVault = new LocalDbAuditVault(pwmApplication.getLocalDB());
                auditVault.init(settings);
            }
        }

        {
            final String ignoredStringList = pwmApplication.getConfig().readAppProperty(AppProperty.AUDIT_EVENTS_IGNORELIST);
            if (ignoredStringList != null) {
                for (final String ignoredString : ignoredStringList.split(AppProperty.VALUE_SEPARATOR)) {
                    if (ignoredString != null && ignoredString.length() > 0) {
                        try {
                            final AuditEvent event = AuditEvent.valueOf(ignoredString);
                            if (event != null) {
                                settings.ignoredEvents.add(event);
                                LOGGER.info("will ignore all events of type '" + event.toString() + "' due to AppProperty setting");
                            }
                        } catch (IllegalArgumentException e) {
                            LOGGER.error("unknown event type '" + ignoredString + "' in AppProperty " + AppProperty.AUDIT_EVENTS_IGNORELIST.getKey());
                        }
                    }
                }
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

        final List<HealthRecord> healthRecords = new ArrayList<HealthRecord>();
        if (syslogManager != null) {
            healthRecords.addAll(syslogManager.healthCheck());
        }

        if (lastError != null) {
            healthRecords.add(new HealthRecord(HealthStatus.WARN,"AuditManager", lastError.toDebugStr()));
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

    protected void sendAsEmail(final AuditRecord record) {
        if (record == null || record.getEventCode() == null) {
            return;
        }
        if (settings.alertFromAddress == null || settings.alertFromAddress.length() < 1) {
            return;
        }

        switch (record.getEventCode().getType()) {
            case SYSTEM:
                for (final String toAddress : settings.systemEmailAddresses) {
                    sendAsEmail(pwmApplication, record, toAddress, settings.alertFromAddress);
                }
                break;

            case USER:
                for (final String toAddress : settings.userEmailAddresses) {
                    sendAsEmail(pwmApplication, record, toAddress, settings.alertFromAddress);
                }
                break;
        }
    }

    private static void sendAsEmail(
            final PwmApplication pwmApplication,
            final AuditRecord record,
            final String toAddress,
            final String fromAddress

    ) {
        final String subject = PwmConstants.PWM_APP_NAME + " - Audit Event - " + record.getEventCode().toString();

        final StringBuilder body = new StringBuilder();
        final String jsonRecord = Helper.getGson().toJson(record);
        final Map<String,String> mapRecord = Helper.getGson().fromJson(jsonRecord, new TypeToken <Map<String, String >>() {
        }.getType());

        for (final String key : mapRecord.keySet()) {
            body.append(key);
            body.append("=");
            body.append(mapRecord.get(key));
            body.append("\n");
        }

        final EmailItemBean emailItem = new EmailItemBean(toAddress, fromAddress, subject, body.toString(), null);
        pwmApplication.getEmailQueue().submit(emailItem, null, null);
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
        final UserAuditRecord auditRecord = new UserAuditRecord(auditEvent, userInfoBean, pwmSession);
        submit(auditRecord);
    }

    public void submit(final AuditRecord auditRecord)
            throws PwmUnrecoverableException
    {

        final String gsonRecord = Helper.getGson().toJson(auditRecord);

        if (status != STATUS.OPEN) {
            LOGGER.warn("discarding audit event (AuditManager is not open); event=" + gsonRecord);
            return;
        }

        if (auditRecord.getEventCode() == null) {
            LOGGER.error("discarding audit event, missing event type; event=" + gsonRecord);
            return;
        }

        if (settings.ignoredEvents.contains(auditRecord.getEventCode())) {
            LOGGER.warn("discarding audit event, '" + auditRecord.getEventCode() + "', are being ignored; event=" + gsonRecord);
            return;
        }

        // add to debug log
        LOGGER.info("audit event: " + gsonRecord);

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


    public int outpuVaultToCsv(final Writer writer, final boolean includeHeader)
            throws IOException
    {
        CsvWriter csvWriter = new CsvWriter(writer,',');
        csvWriter.writeComment(" " + PwmConstants.PWM_APP_NAME + " audit record output ");
        csvWriter.writeComment(" " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date()));

        if (includeHeader) {
            final List<String> headers = new ArrayList<String>();
            headers.add("Type");
            headers.add("Event");
            headers.add("Timestamp");
            headers.add("Message");
            headers.add("Instance");
            headers.add("Perpetrator ID");
            headers.add("Perpetrator DN");
            headers.add("Target ID");
            headers.add("Target DN");
            headers.add("Source Address");
            headers.add("Source Hostname");
            csvWriter.writeRecord(headers.toArray(new String[headers.size()]));
        }

        int counter = 0;
        for (final Iterator<AuditRecord> recordIterator = readVault(); recordIterator.hasNext();) {
            final AuditRecord loopRecord = recordIterator.next();
            counter++;

            final List<String> lineOutput = new ArrayList<String>();
            lineOutput.add(loopRecord.getEventCode().getType().toString());
            lineOutput.add(loopRecord.getEventCode().toString());
            lineOutput.add(PwmConstants.DEFAULT_DATETIME_FORMAT.format(loopRecord.getTimestamp()));
            lineOutput.add(loopRecord.getMessage() == null ? "" : loopRecord.getMessage());
            if (loopRecord instanceof SystemAuditRecord) {
                lineOutput.add(((SystemAuditRecord)loopRecord).getInstance());
            }
            if (loopRecord instanceof UserAuditRecord) {
                lineOutput.add(((UserAuditRecord)loopRecord).getPerpetratorID());
                lineOutput.add(((UserAuditRecord)loopRecord).getPerpetratorDN());
                lineOutput.add(((UserAuditRecord)loopRecord).getTargetID());
                lineOutput.add(((UserAuditRecord)loopRecord).getTargetDN());
                lineOutput.add(((UserAuditRecord)loopRecord).getSourceAddress());
                lineOutput.add(((UserAuditRecord)loopRecord).getSourceHost());
            }
            csvWriter.writeRecord(lineOutput.toArray(new String[lineOutput.size()]));
        }

        writer.flush();

        return counter;
    }

    private static class Settings {
        private List<String> systemEmailAddresses = new ArrayList<String>();
        private List<String> userEmailAddresses = new ArrayList<String>();
        private String alertFromAddress = "";
        private Set<AuditEvent> ignoredEvents = new HashSet<AuditEvent>();
    }
}
