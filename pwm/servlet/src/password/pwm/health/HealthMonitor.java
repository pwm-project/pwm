package password.pwm.health;

/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

import password.pwm.ContextManager;
import password.pwm.config.PwmSetting;
import password.pwm.util.PwmLogger;

import java.io.Serializable;
import java.util.*;

public class HealthMonitor implements Serializable {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(HealthMonitor.class);

    private final ContextManager contextManager;
    private final List<HealthRecord> healthRecords = new ArrayList<HealthRecord>();
    private final Timer timer = new Timer("pwm-HealthMonitor timer",true);
    private final List<HealthChecker> healthCheckers = new ArrayList<HealthChecker>();

    private Date lastHealthCheckDate = null;

    public HealthMonitor(final ContextManager contextManager) {
        this.contextManager = contextManager;
        startupMonitor();
    }

    private void startupMonitor() {
        if (contextManager.getConfig() != null) {
            final int intervalSeconds = contextManager.getConfig().readSettingAsInt(PwmSetting.EVENTS_HEALTH_CHECK_INTERVAL);
            if (intervalSeconds > 0) {
                LOGGER.trace("starting health check monitor task");
                timer.scheduleAtFixedRate(new HealthCheckTimerTask(),60 * 1000, intervalSeconds * 1000);
            }
        }

        final HealthRecord hr = new HealthRecord(HealthRecord.HealthStatus.CAUTION, HealthMonitor.class.getSimpleName(), "Health Check operation has not been performed since PWM has started.");
        healthRecords.add(hr);

        registerHealthCheck(new HealthMonitorHealthCheck());
    }

    public void checkImmediately() {
        LOGGER.trace("immediate health check requested");
        doHealthChecks();
    }

    public Date getLastHealthCheckDate() {
        return lastHealthCheckDate;
    }

    public void registerHealthCheck(final HealthChecker healthChecker) {
        healthCheckers.add(healthChecker);
    }

    public List<HealthRecord> getHealthRecords() {
        return Collections.unmodifiableList(healthRecords);
    }

    public void close() {
        timer.cancel();
        healthRecords.clear();
        healthRecords.add(new HealthRecord(HealthRecord.HealthStatus.CAUTION, HealthMonitor.class.getSimpleName(), "Health Monitor has been closed."));
    }

    private void doHealthChecks() {
        LOGGER.trace("beginning health check process");
        final List<HealthRecord> newResults = new ArrayList<HealthRecord>();
        for (final HealthChecker loopChecker : healthCheckers) {
            try {
                final List<HealthRecord> loopResults = loopChecker.doHealthCheck(contextManager);
                if (loopResults != null) {
                    newResults.addAll(loopResults);
                }
            } catch (Exception e) {
                LOGGER.warn("unexpected error during healthCheck: " + e.getMessage(),e);
            }
        }
        healthRecords.clear();
        healthRecords.addAll(newResults);
        lastHealthCheckDate = new Date();
        LOGGER.trace("health check process completed");
    }

    private class HealthCheckTimerTask extends TimerTask {
        @Override
        public void run() {
            doHealthChecks();
        }
    }

    public class HealthMonitorHealthCheck implements HealthChecker {
        public List<HealthRecord> doHealthCheck(final ContextManager contextManager) {
            final HealthRecord hr = new HealthRecord(HealthRecord.HealthStatus.GOOD, HealthMonitor.class.getSimpleName(), "HealthMonitor is operating normally");
            return Collections.singletonList(hr);
        }
    }
}
