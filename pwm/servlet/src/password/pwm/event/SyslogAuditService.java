/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2013 The PWM Project
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
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

class SyslogAuditService extends TimerTask {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(SyslogAuditService.class);

    private static final int MAX_QUEUE_SIZE = 1000;
    private static final int TIMER_FREQUENCY_MS = 1000;
    private static final int WARNING_WINDOW_MS = 30 * 60 * 1000;
    private final Map<SyslogConfig,SyslogIF> syslogInstances = new LinkedHashMap<SyslogConfig,SyslogIF>();
    private final Map<SyslogConfig,ErrorInformation> syslogErrors = new LinkedHashMap<SyslogConfig,ErrorInformation>();
    private final Queue<AuditRecord> syslogQueue = new ConcurrentLinkedQueue<AuditRecord>();

    private final Configuration configuration;
    private final Timer timer = new Timer(PwmConstants.PWM_APP_NAME + "-AuditManager syslog writer",true);

    public SyslogAuditService(final Configuration configuration) {
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

        return Syslog.createInstance(syslogConfig.toString(), syslogConfigIF);
    }

    public void add(AuditRecord event) throws PwmOperationalException {
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
}
