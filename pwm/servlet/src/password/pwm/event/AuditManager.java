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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.productivity.java.syslog4j.Syslog;
import org.productivity.java.syslog4j.SyslogConfigIF;
import org.productivity.java.syslog4j.SyslogIF;
import org.productivity.java.syslog4j.impl.net.tcp.TCPNetSyslogConfig;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmService;
import password.pwm.PwmSession;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;
import password.pwm.util.csv.CsvWriter;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBStoredQueue;

import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AuditManager implements PwmService {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(AuditManager.class);
    private static final int MAX_REMOVALS_PER_ADD = 100;


    private STATUS status = STATUS.NEW;
    private PwmApplication pwmApplication;
    private LocalDBStoredQueue auditDB;

    private TimeDuration maxRecordAge = new TimeDuration(TimeDuration.DAY.getTotalMilliseconds() * 30);
    private Date oldestRecord = null;

    private SyslogManager syslogManager;
    private ErrorInformation lastError;

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
        this.maxRecordAge = new TimeDuration(pwmApplication.getConfig().readSettingAsLong(PwmSetting.EVENTS_AUDIT_MAX_AGE) * 1000);

        if (pwmApplication.getLocalDB() == null || pwmApplication.getLocalDB().status() != LocalDB.Status.OPEN) {
            this.status = STATUS.CLOSED;
            LOGGER.warn("unable to start - LocalDB is not available");
            return;
        }

        this.auditDB = LocalDBStoredQueue.createPwmDBStoredQueue(pwmApplication.getLocalDB(), LocalDB.DB.AUDIT_EVENTS);
        final List<String> syslogConfigStrings = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.AUDIT_SYSLOG_SERVERS);
        if (!syslogConfigStrings.isEmpty()) {
            try {
                syslogManager = new SyslogManager(pwmApplication.getConfig());
            } catch (Exception e) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SYSLOG_WRITE_ERROR, "startup error: " + e.getMessage());
                LOGGER.error(errorInformation.toDebugStr());
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

    public Iterator<AuditRecord> readLocalDB() {
        if (status != STATUS.OPEN) {
            return new Iterator<AuditRecord>() {
                public boolean hasNext() {
                    return false;
                }

                public AuditRecord next() {
                    return null;
                }

                public void remove() {
                }
            };
        }
        return new IteratorWrapper<AuditRecord>(auditDB.descendingIterator());
    }

    public List<AuditRecord> readUserAuditRecords(final PwmSession pwmSession)
            throws PwmUnrecoverableException
    {
        return readUserAuditRecords(pwmSession.getUserInfoBean());
    }

    public List<AuditRecord> readUserAuditRecords(final UserInfoBean userInfoBean)
            throws PwmUnrecoverableException
    {
        try {
            return UserLdapHistory.readUserHistory(pwmApplication, userInfoBean);
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,e.getMessage()));
        }
    }

    public int localSize() {
        if (status != STATUS.OPEN || auditDB == null) {
            return -1;
        }

        return auditDB.size();
    }

    public void submitAuditRecord(final AuditEvent auditEvent, final UserInfoBean userInfoBean, final PwmSession pwmSession)
            throws PwmUnrecoverableException
    {
        final AuditRecord auditRecord = new AuditRecord(auditEvent, userInfoBean, pwmSession);
        submitAuditRecord(auditRecord);
    }

    public void submitAuditRecord(final AuditRecord auditRecord)
            throws PwmUnrecoverableException
    {
        final String gsonRecord = new Gson().toJson(auditRecord);

        if (status != STATUS.OPEN) {
            LOGGER.warn("discarding audit event (AuditManager is not open), " + gsonRecord);
            return;
        }

        // add to debug log
        LOGGER.info("audit event: " + gsonRecord);

        // add to audit db
        if (status == STATUS.OPEN && auditDB != null) {
            auditDB.addLast(gsonRecord);
            trimDB();
        }

        // add to user ldap record
        try {
            UserLdapHistory.updateUserHistory(pwmApplication, auditRecord);
        } catch (ChaiUnavailableException e) {
            LOGGER.error("error updating ldap user history: " + e.getMessage());
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

    private void trimDB() {
        if (oldestRecord != null && TimeDuration.fromCurrent(oldestRecord).isLongerThan(maxRecordAge)) {
            return;
        }

        if (auditDB.isEmpty()) {
            return;
        }

        int workActions = 0;
        while (workActions < MAX_REMOVALS_PER_ADD && !auditDB.isEmpty()) {
            final String stringFirstRecord = auditDB.getFirst();
            final AuditRecord firstRecord = new Gson().fromJson(stringFirstRecord,AuditRecord.class);
            oldestRecord = firstRecord.getTimestamp();
            if (TimeDuration.fromCurrent(oldestRecord).isLongerThan(maxRecordAge)) {
                auditDB.removeFirst();
                workActions++;
            } else {
                return;
            }
        }
    }

    private static class IteratorWrapper<AuditRecord> implements Iterator<AuditRecord> {
        private Iterator<String> innerIter;

        private IteratorWrapper(Iterator<String> innerIter) {
            this.innerIter = innerIter;
        }

        @Override
        public boolean hasNext() {
            return innerIter.hasNext();
        }

        @Override
        public AuditRecord next() {
            final String value = innerIter.next();
            return (AuditRecord)new Gson().fromJson(value,password.pwm.event.AuditRecord.class);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }



    public static class SyslogConfig implements Serializable {
        public enum Protocol { tcp, udp }

        private Protocol protocol;
        private String host;
        private int port;

        public SyslogConfig(Protocol protocol, String host, int port) {
            this.protocol = protocol;
            this.host = host;
            this.port = port;
        }

        public Protocol getProtocol() {
            return protocol;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public static SyslogConfig fromConfigString(final String input) throws IllegalArgumentException {
            if (input == null) {
                throw new IllegalArgumentException("input cannot be null");
            }

            final String parts[] = input.split(",");
            if (parts.length != 3) {
                throw new IllegalArgumentException("input must have three comma separated parts.");
            }

            final Protocol protocol;
            try {
                protocol = Protocol.valueOf(parts[0]);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown protocol '" + parts[0] + "'");
            }

            final int port;
            try {
                port = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid port number '" + parts[2] + "'");
            }

            return new SyslogConfig(protocol,parts[1],port);
        }

        public String toString() {
            final StringBuilder message = new StringBuilder();
            message.append(this.getProtocol().toString()).append(",");
            message.append(this.getHost()).append(",");
            message.append(this.getPort());
            return message.toString();
        }
    }


    private static class SyslogManager extends TimerTask {
        private static final int MAX_QUEUE_SIZE = 1000;
        private static final int TIMER_FREQUENCY_MS = 1000;
        private static final int WARNING_WINDOW_MS = 30 * 60 * 1000;
        private final Map<SyslogConfig,SyslogIF> syslogInstances = new LinkedHashMap<SyslogConfig,SyslogIF>();
        private final Map<SyslogConfig,ErrorInformation> syslogErrors = new LinkedHashMap<SyslogConfig,ErrorInformation>();
        private final Queue<AuditRecord> syslogQueue = new ConcurrentLinkedQueue<AuditRecord>();

        private final Configuration configuration;
        private final Timer timer = new Timer(PwmConstants.PWM_APP_NAME + "-AuditManager syslog writer",true);

        public SyslogManager(final Configuration configuration) {
            this.configuration = configuration;
            final List<String> syslogConfigStrings = configuration.readSettingAsStringArray(PwmSetting.AUDIT_SYSLOG_SERVERS);
            for (final String loopStr : syslogConfigStrings) {
                final SyslogConfig syslogConfig;
                try {
                    syslogConfig = SyslogConfig.fromConfigString(loopStr);
                    syslogInstances.put(syslogConfig, makeSyslogInstance(syslogConfig));
                } catch (IllegalArgumentException e) {
                    LOGGER.error("error parsing syslog configuration for '" + loopStr + "', error: " + e.getMessage());
                    break;
                }
            }
            timer.schedule(this,TIMER_FREQUENCY_MS,TIMER_FREQUENCY_MS);
        }

        private static SyslogIF makeSyslogInstance(final SyslogConfig syslogConfig)
        {
            final SyslogConfigIF syslogConfigIF;
            switch (syslogConfig.getProtocol()) {
                case tcp:
                    final TCPNetSyslogConfig tcpConfig = new TCPNetSyslogConfig();
                    tcpConfig.setThreaded(false);
                    tcpConfig.setMaxQueueSize(MAX_QUEUE_SIZE);
                    syslogConfigIF = tcpConfig;
                    break;

                case udp:
                    final TCPNetSyslogConfig udpConfig = new TCPNetSyslogConfig();
                    udpConfig.setThreaded(false);
                    udpConfig.setMaxQueueSize(MAX_QUEUE_SIZE);
                    syslogConfigIF = udpConfig;
                    break;

                default:
                    throw new IllegalArgumentException("unknown protocol type");
            }
            syslogConfigIF.setHost(syslogConfig.getHost());
            syslogConfigIF.setPort(syslogConfig.getPort());
            syslogConfigIF.setThrowExceptionOnWrite(true);

            return Syslog.createInstance(syslogConfig.toString(),syslogConfigIF);
        }

        private void add(AuditRecord event) throws PwmOperationalException {
            if (syslogQueue.size() >= MAX_QUEUE_SIZE) {
                final String errorMsg = "dropping audit record event due to queue full " + event.toString() + ", queue length=" + syslogQueue.size();
                LOGGER.warn(errorMsg);
                throw new PwmOperationalException(PwmError.ERROR_SYSLOG_WRITE_ERROR,errorMsg);
            }

            syslogQueue.offer(event);
        }

        public List<HealthRecord> healthCheck() {
            final List<HealthRecord> healthRecords = new ArrayList<HealthRecord>();
            for (final SyslogConfig syslogConfig : syslogErrors.keySet()) {
                final ErrorInformation errorInformation = syslogErrors.get(syslogConfig);
                if (TimeDuration.fromCurrent(errorInformation.getDate()).isShorterThan(WARNING_WINDOW_MS)) {
                    healthRecords.add(new HealthRecord(HealthStatus.WARN,"AuditManager",errorInformation.toUserStr(PwmConstants.DEFAULT_LOCALE, configuration)));
                }
            }
            return healthRecords;
        }


        @Override
        public void run() {
            while (!syslogQueue.isEmpty()) {
                processEvent(syslogQueue.poll());
            }
        }

        private void processEvent(final AuditRecord auditRecord) {
            final Gson gs = new GsonBuilder()
                    .disableHtmlEscaping()
                    .create();

            final StringBuilder sb = new StringBuilder();
            sb.append(PwmConstants.PWM_APP_NAME);
            sb.append(" ");
            sb.append(gs.toJson(auditRecord));

            for (final SyslogConfig syslogConfig : syslogInstances.keySet()) {
                final SyslogIF syslogIF = syslogInstances.get(syslogConfig);
                try {
                    syslogIF.info(sb.toString());
                    syslogErrors.remove(syslogConfig);
                } catch (Exception e) {
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SYSLOG_WRITE_ERROR, e.getMessage(), new String[]{syslogConfig.toString(),e.getMessage()});
                    syslogErrors.put(syslogConfig, errorInformation);
                    LOGGER.error(errorInformation.toDebugStr());
                }
            }
        }

        public void close() {
            for (final SyslogConfig syslogConfig : syslogInstances.keySet()) {
                final SyslogIF syslogIF = syslogInstances.get(syslogConfig);
                Syslog.destroyInstance(syslogConfig.toString());
                syslogIF.shutdown();
            }
            timer.cancel();
            run();
            syslogInstances.clear();
        }

    }

    public int outputLocalDBToCsv(final Writer writer, final boolean includeHeader)
            throws IOException
    {
        CsvWriter csvWriter = new CsvWriter(writer,',');

        if (includeHeader) {
            final List<String> headers = new ArrayList<String>();
            headers.add("Event");
            headers.add("Timestamp");
            headers.add("Perpetrator ID");
            headers.add("Perpetrator DN");
            headers.add("Target ID");
            headers.add("Target DN");
            headers.add("Source Address");
            headers.add("Source Hostname");
            headers.add("Message");
            csvWriter.writeRecord(headers.toArray(new String[headers.size()]));
        }

        int counter = 0;
        for (final Iterator<AuditRecord> recordIterator = readLocalDB(); recordIterator.hasNext();) {
            final AuditRecord loopRecord = recordIterator.next();
            counter++;
            final List<String> lineOutput = new ArrayList<String>();
            lineOutput.add(loopRecord.getEventCode().toString());
            lineOutput.add(PwmConstants.DEFAULT_DATETIME_FORMAT.format(loopRecord.getTimestamp()));
            lineOutput.add(loopRecord.getPerpetratorID());
            lineOutput.add(loopRecord.getPerpetratorDN());
            lineOutput.add(loopRecord.getTargetID());
            lineOutput.add(loopRecord.getTargetDN());
            lineOutput.add(loopRecord.getSourceAddress());
            lineOutput.add(loopRecord.getSourceHost());
            lineOutput.add(loopRecord.getMessage() == null ? "" : loopRecord.getMessage());
            csvWriter.writeRecord(lineOutput.toArray(new String[lineOutput.size()]));
        }

        writer.flush();

        return counter;
    }
}
