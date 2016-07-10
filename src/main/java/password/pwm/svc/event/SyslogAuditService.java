/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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
import password.pwm.util.*;
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
    private static final String SYSLOG_INSTANCE_NAME = "syslog-audit";

    private SyslogIF syslogInstance = null;
    private ErrorInformation lastError = null;
    private X509Certificate[] certificates = null;

    private WorkQueueProcessor<AuditRecord> workQueueProcessor;


    private final Configuration configuration;

    public SyslogAuditService(final PwmApplication pwmApplication)
            throws LocalDBException
    {
        this.configuration = pwmApplication.getConfig();
        this.certificates = configuration.readSettingAsCertificate(PwmSetting.AUDIT_SYSLOG_CERTIFICATES);

        final String syslogConfigString = configuration.readSettingAsString(PwmSetting.AUDIT_SYSLOG_SERVERS);
        final SyslogConfig syslogConfig;
        try {
            syslogConfig = SyslogConfig.fromConfigString(syslogConfigString);
            syslogInstance = makeSyslogInstance(syslogConfig);
            LOGGER.trace("queued service running for " + syslogConfig);
        } catch (IllegalArgumentException e) {
            LOGGER.error("error parsing syslog configuration for '" + syslogConfigString + "', error: " + e.getMessage());
        }

        final WorkQueueProcessor.Settings settings = new WorkQueueProcessor.Settings();
        settings.setMaxEvents(Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_SYSLOG_MAX_COUNT)));
        settings.setRetryDiscardAge(new TimeDuration(Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_SYSLOG_MAX_AGE_MS))));
        settings.setRetryInterval(new TimeDuration(Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_SYSLOG_RETRY_TIMEOUT_MS))));

        final LocalDBStoredQueue localDBStoredQueue = LocalDBStoredQueue.createLocalDBStoredQueue(pwmApplication, pwmApplication.getLocalDB(), LocalDB.DB.SYSLOG_QUEUE);

        workQueueProcessor = new WorkQueueProcessor<>(pwmApplication, localDBStoredQueue, settings, new SyslogItemProcessor(), this.getClass());
    }

    private class SyslogItemProcessor implements WorkQueueProcessor.ItemProcessor<AuditRecord> {
        @Override
        public WorkQueueProcessor.ProcessResult process(AuditRecord workItem) {
            return processEvent(workItem);
        }

        @Override
        public String convertToDebugString(AuditRecord workItem) {
            return JsonUtil.serialize(workItem);
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
        try {
            workQueueProcessor.submit(event);
        } catch (PwmOperationalException e) {
            LOGGER.warn("unable to add email to queue: " + e.getMessage());
        }
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

    private WorkQueueProcessor.ProcessResult processEvent(final AuditRecord auditRecord) {
        final String syslogEventString = JsonUtil.serialize(auditRecord);

        final SyslogIF syslogIF = syslogInstance;
        try {
            syslogIF.info(syslogEventString);
            LOGGER.trace("delivered syslog audit event: " + syslogEventString);
            lastError = null;
            return WorkQueueProcessor.ProcessResult.SUCCESS;
        } catch (Exception e) {
            final String errorMsg = "error while sending syslog message to remote service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SYSLOG_WRITE_ERROR, errorMsg, new String[]{e.getMessage()});
            lastError = errorInformation;
            LOGGER.error(errorInformation.toDebugStr());
        }

        return WorkQueueProcessor.ProcessResult.RETRY;
    }

    public void close() {
        final SyslogIF syslogIF = syslogInstance;
        syslogIF.shutdown();
        workQueueProcessor.close();
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
        return workQueueProcessor.queueSize();
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
