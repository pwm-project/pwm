/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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
import org.productivity.java.syslog4j.Syslog;
import org.productivity.java.syslog4j.SyslogConfigIF;
import org.productivity.java.syslog4j.SyslogIF;
import org.productivity.java.syslog4j.impl.net.tcp.TCPNetSyslogConfig;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.localdb.LocalDBStoredQueue;

import java.io.Serializable;
import java.util.*;

class SyslogAuditService {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(SyslogAuditService.class);

    private static final int WARNING_WINDOW_MS = 30 * 60 * 1000;
    private static final String QUEUE_STORAGE_DELIMINATOR = "###";

    private final int MAX_QUEUE_SIZE;
    private final long MAX_AGE_MS;
    private final long RETRY_TIMEOUT_MS;
    private final Map<SyslogConfig,SyslogIF> syslogInstances = new LinkedHashMap<SyslogConfig,SyslogIF>();
    private final Map<SyslogConfig,ErrorInformation> syslogErrors = new LinkedHashMap<SyslogConfig,ErrorInformation>();
    private final LocalDBStoredQueue syslogQueue;

    private volatile Date lastSendError;

    private final Configuration configuration;
    private final Timer timer;

    public SyslogAuditService(final PwmApplication pwmApplication)
            throws LocalDBException
    {
        timer = new Timer(Helper.makeThreadName(pwmApplication,SyslogAuditService.class),true);

        MAX_QUEUE_SIZE = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_SYSLOG_MAX_COUNT));
        MAX_AGE_MS = Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_SYSLOG_MAX_AGE_MS));
        RETRY_TIMEOUT_MS = Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_SYSLOG_RETRY_TIMEOUT_MS));

        syslogQueue = LocalDBStoredQueue.createLocalDBStoredQueue(pwmApplication.getLocalDB(), LocalDB.DB.SYSLOG_QUEUE);

        this.configuration = pwmApplication.getConfig();
        final String syslogConfigStrings = configuration.readSettingAsString(PwmSetting.AUDIT_SYSLOG_SERVERS);
        final SyslogConfig syslogConfig;
        try {
            syslogConfig = SyslogConfig.fromConfigString(syslogConfigStrings);
            syslogInstances.put(syslogConfig, makeSyslogInstance(syslogConfig));
            timer.schedule(new WriterTask(),1000);
            LOGGER.trace("queued service running for " + syslogConfig);
        } catch (IllegalArgumentException e) {
            LOGGER.error("error parsing syslog configuration for '" + syslogConfigStrings + "', error: " + e.getMessage());
        }
    }

    private SyslogIF makeSyslogInstance(final SyslogConfig syslogConfig)
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

        return Syslog.createInstance(syslogConfig.toString(), syslogConfigIF);
    }

    public void add(AuditRecord event) throws PwmOperationalException {
        if (syslogQueue.size() >= MAX_QUEUE_SIZE) {
            final String errorMsg = "dropping audit record event due to queue full " + event.toString() + ", queue length=" + syslogQueue.size();
            LOGGER.warn(errorMsg);
            throw new PwmOperationalException(PwmError.ERROR_SYSLOG_WRITE_ERROR,errorMsg);
        }

        final String prefix = event.getClass().getCanonicalName();
        final String jsonValue = prefix + QUEUE_STORAGE_DELIMINATOR + Helper.getGson().toJson(event);
        syslogQueue.offerLast(jsonValue);
        timer.schedule(new WriterTask(),1);
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

    private class WriterTask extends TimerTask {
        @Override
        public void run() {
            if (lastSendError != null) {
                if (TimeDuration.fromCurrent(lastSendError).isLongerThan(RETRY_TIMEOUT_MS)) {
                    lastSendError = null;
                }
            }

            while (!syslogQueue.isEmpty() && lastSendError == null) {
                AuditRecord record = null;
                try {
                    final String storedString = syslogQueue.peek();
                    final String[] splitString = storedString.split(QUEUE_STORAGE_DELIMINATOR,2);
                    final String className = splitString[0];
                    final String jsonString = splitString[1];
                    record = (AuditRecord)Helper.getGson().fromJson(jsonString,Class.forName(className));
                } catch (Exception e) {
                    LOGGER.error("error decoding stored syslog event, discarding; error: " + e.getMessage());
                    syslogQueue.removeFirst();
                }
                if (record != null) {
                    final TimeDuration recordAge = TimeDuration.fromCurrent(record.getTimestamp());
                    if (recordAge.isLongerThan(MAX_AGE_MS)) {
                        LOGGER.info("discarding syslog audit event, maximum queued age exceeded: " + Helper.getGson().toJson(record));
                        syslogQueue.removeFirst();
                    } else {
                        boolean sendSuccess = processEvent(record);
                        if (sendSuccess) {
                            syslogQueue.removeFirst();
                            lastSendError = null;
                        } else {
                            lastSendError = new Date();
                            timer.schedule(new WriterTask(),RETRY_TIMEOUT_MS + 1);
                        }
                    }
                }
            }
        }
    }

    private boolean processEvent(final AuditRecord auditRecord) {
        final Gson gs = Helper.getGson(new GsonBuilder().disableHtmlEscaping());
        final StringBuilder sb = new StringBuilder();
        sb.append(PwmConstants.PWM_APP_NAME);
        sb.append(" ");
        sb.append(gs.toJson(auditRecord));

        for (final SyslogConfig syslogConfig : syslogInstances.keySet()) {
            final SyslogIF syslogIF = syslogInstances.get(syslogConfig);
            try {
                syslogIF.info(sb.toString());
                syslogErrors.remove(syslogConfig);
                return true;
            } catch (Exception e) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SYSLOG_WRITE_ERROR, e.getMessage(), new String[]{syslogConfig.toString(),e.getMessage()});
                syslogErrors.put(syslogConfig, errorInformation);
                LOGGER.error(errorInformation.toDebugStr());
            }
        }

        return false;
    }

    public void close() {
        for (final SyslogConfig syslogConfig : syslogInstances.keySet()) {
            final SyslogIF syslogIF = syslogInstances.get(syslogConfig);
            Syslog.destroyInstance(syslogConfig.toString());
            syslogIF.shutdown();
        }
        timer.cancel();
        syslogInstances.clear();
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
            return Helper.getGson().toJson(this);
        }
    }
}
