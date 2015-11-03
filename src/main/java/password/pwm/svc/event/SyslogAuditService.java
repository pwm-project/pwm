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

package password.pwm.svc.event;

import org.graylog2.syslog4j.SyslogIF;
import org.graylog2.syslog4j.impl.AbstractSyslogConfigIF;
import org.graylog2.syslog4j.impl.AbstractSyslogWriter;
import org.graylog2.syslog4j.impl.backlog.NullSyslogBackLogHandler;
import org.graylog2.syslog4j.impl.net.AbstractNetSyslog;
import org.graylog2.syslog4j.impl.net.tcp.TCPNetSyslog;
import org.graylog2.syslog4j.impl.net.tcp.TCPNetSyslogConfig;
import org.graylog2.syslog4j.impl.net.tcp.ssl.SSLTCPNetSyslog;
import org.graylog2.syslog4j.impl.net.tcp.ssl.SSLTCPNetSyslogConfig;
import org.graylog2.syslog4j.impl.net.tcp.ssl.SSLTCPNetSyslogWriter;
import org.graylog2.syslog4j.impl.net.udp.UDPNetSyslog;
import org.graylog2.syslog4j.impl.net.udp.UDPNetSyslogConfig;
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
import password.pwm.health.HealthTopic;
import password.pwm.util.Helper;
import password.pwm.util.JsonUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.X509Utils;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.localdb.LocalDBStoredQueue;
import password.pwm.util.logging.PwmLogger;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import java.io.Serializable;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.*;

public class SyslogAuditService {
    private static final PwmLogger LOGGER = PwmLogger.forClass(SyslogAuditService.class);

    private static final int WARNING_WINDOW_MS = 30 * 60 * 1000;
    private static final String QUEUE_STORAGE_DELIMINATOR = "###";
    private static final String SYSLOG_INSTANCE_NAME = "syslog-audit";

    private final int MAX_QUEUE_SIZE;
    private final long MAX_AGE_MS;
    private final long RETRY_TIMEOUT_MS;
    private final LocalDBStoredQueue syslogQueue;

    private volatile Date lastSendError;
    private SyslogIF syslogInstance = null;
    private ErrorInformation lastError = null;
    private X509Certificate[] certificates = null;

    private final Configuration configuration;
    private final Timer timer;

    public SyslogAuditService(final PwmApplication pwmApplication)
            throws LocalDBException
    {
        timer = new Timer(Helper.makeThreadName(pwmApplication,SyslogAuditService.class),true);

        MAX_QUEUE_SIZE = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_SYSLOG_MAX_COUNT));
        MAX_AGE_MS = Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_SYSLOG_MAX_AGE_MS));
        RETRY_TIMEOUT_MS = Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_SYSLOG_RETRY_TIMEOUT_MS));

        syslogQueue = LocalDBStoredQueue.createLocalDBStoredQueue(pwmApplication, pwmApplication.getLocalDB(), LocalDB.DB.SYSLOG_QUEUE);

        this.configuration = pwmApplication.getConfig();
        this.certificates = configuration.readSettingAsCertificate(PwmSetting.AUDIT_SYSLOG_CERTIFICATES);

        final String syslogConfigString = configuration.readSettingAsString(PwmSetting.AUDIT_SYSLOG_SERVERS);
        final SyslogConfig syslogConfig;
        try {
            syslogConfig = SyslogConfig.fromConfigString(syslogConfigString);
            syslogInstance = makeSyslogInstance(syslogConfig);
            timer.schedule(new WriterTask(),1000);
            LOGGER.trace("queued service running for " + syslogConfig);
        } catch (IllegalArgumentException e) {
            LOGGER.error("error parsing syslog configuration for '" + syslogConfigString + "', error: " + e.getMessage());
        }
    }

    private SyslogIF makeSyslogInstance(final SyslogConfig syslogConfig)
    {
        final AbstractSyslogConfigIF syslogConfigIF;
        final AbstractNetSyslog syslogInstance;

        switch (syslogConfig.getProtocol()) {
            case sslTcp:
            case tls: {
                syslogConfigIF = new SSLTCPNetSyslogConfig();
                ((SSLTCPNetSyslogConfig)syslogConfigIF).setBackLogHandlers(Collections.singletonList(new NullSyslogBackLogHandler()));
                syslogInstance = new LocalTrustSSLTCPNetSyslog();
            }
            break;

            case tcp: {
                syslogConfigIF = new TCPNetSyslogConfig();
                ((TCPNetSyslogConfig) syslogConfigIF).setBackLogHandlers(Collections.singletonList(new NullSyslogBackLogHandler()));
                syslogInstance = new TCPNetSyslog();
            }
            break;

            case udp: {
                syslogConfigIF = new UDPNetSyslogConfig();
                syslogInstance = new UDPNetSyslog();
            }
            break;

            default:
                throw new IllegalArgumentException("unknown protocol type");
        }

        syslogConfigIF.setThreaded(false);
        syslogConfigIF.setMaxQueueSize(0);
        syslogConfigIF.setThrowExceptionOnWrite(true);
        syslogConfigIF.setHost(syslogConfig.getHost());
        syslogConfigIF.setPort(syslogConfig.getPort());
        syslogInstance.initialize(SYSLOG_INSTANCE_NAME, syslogConfigIF);
        return syslogInstance;
    }

    public void add(AuditRecord event) throws PwmOperationalException {
        if (syslogQueue.size() >= MAX_QUEUE_SIZE) {
            final String errorMsg = "dropping audit record event due to queue full " + event.toString() + ", queue length=" + syslogQueue.size();
            LOGGER.warn(errorMsg);
            throw new PwmOperationalException(PwmError.ERROR_SYSLOG_WRITE_ERROR,errorMsg);
        }

        final String prefix = event.getClass().getCanonicalName();
        final String jsonValue = prefix + QUEUE_STORAGE_DELIMINATOR + JsonUtil.serialize(event);
        syslogQueue.offerLast(jsonValue);
        timer.schedule(new WriterTask(),1);
    }

    public List<HealthRecord> healthCheck() {
        final List<HealthRecord> healthRecords = new ArrayList<>();
        if (lastError != null) {
            final ErrorInformation errorInformation = lastError;
            if (TimeDuration.fromCurrent(errorInformation.getDate()).isShorterThan(WARNING_WINDOW_MS)) {
                healthRecords.add(new HealthRecord(HealthStatus.WARN, HealthTopic.Audit,
                        errorInformation.toUserStr(PwmConstants.DEFAULT_LOCALE, configuration)));
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
                    final String storedString = syslogQueue.peekFirst();
                    final String[] splitString = storedString.split(QUEUE_STORAGE_DELIMINATOR,2);
                    final String className = splitString[0];
                    final String jsonString = splitString[1];
                    record = (AuditRecord) JsonUtil.deserialize(jsonString,Class.forName(className));
                } catch (Exception e) {
                    LOGGER.error("error decoding stored syslog event, discarding; error: " + e.getMessage());
                    syslogQueue.removeFirst();
                }
                if (record != null) {
                    final TimeDuration recordAge = TimeDuration.fromCurrent(record.getTimestamp());
                    if (recordAge.isLongerThan(MAX_AGE_MS)) {
                        LOGGER.info("discarding syslog audit event, maximum queued age exceeded: " + JsonUtil.serialize(record));
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
        final StringBuilder sb = new StringBuilder();
        sb.append(PwmConstants.PWM_APP_NAME);
        sb.append(" ");
        sb.append(JsonUtil.serialize(auditRecord));

        final SyslogIF syslogIF = syslogInstance;
        try {
            syslogIF.info(sb.toString());
            LOGGER.trace("delivered syslog audit event: " + sb.toString());
            lastError = null;
            return true;
        } catch (Exception e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SYSLOG_WRITE_ERROR, e.getMessage(), new String[]{e.getMessage()});
            lastError = errorInformation;
            LOGGER.error(errorInformation.toDebugStr());
        }

        return false;
    }

    public void close() {
        final SyslogIF syslogIF = syslogInstance;
        syslogIF.shutdown();
        timer.cancel();
        syslogInstance = null;
    }

    public static class SyslogConfig implements Serializable {
        public enum Protocol { sslTcp, tcp, udp, tls }

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
            return JsonUtil.serialize(this);
        }
    }

    public int queueSize() {
        return syslogQueue != null ? syslogQueue.size() : 0;
    }

    private class LocalTrustSyslogWriterClass extends SSLTCPNetSyslogWriter {
        private LocalTrustSyslogWriterClass()
        {
            super();
        }

        @Override
        protected SocketFactory obtainSocketFactory()
        {
            if (certificates != null && certificates.length >= 1) {
                try {
                    final SSLContext sc = SSLContext.getInstance("SSL");
                    sc.init(null, new X509TrustManager[]{new X509Utils.CertMatchingTrustManager(configuration, certificates)},
                            new java.security.SecureRandom());
                    return sc.getSocketFactory();
                } catch (NoSuchAlgorithmException | KeyManagementException e) {
                    LOGGER.error("unexpected error loading syslog certificates: " + e.getMessage());
                }
            }

            return super.obtainSocketFactory();
        }
    }

    private class LocalTrustSSLTCPNetSyslog extends SSLTCPNetSyslog {


        @Override
        public AbstractSyslogWriter createWriter()
        {
            LocalTrustSyslogWriterClass newClass = new LocalTrustSyslogWriterClass();
            newClass.initialize(this);
            return newClass;
        }
    }
}
