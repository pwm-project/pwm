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
    private static final int MIN_INTERVAL_SECONDS = 30;
    private static final int MAX_INTERVAL_SECONDS = 60 * 60 * 24;

    private final ContextManager contextManager;
    private final List<HealthRecord> healthRecords = new ArrayList<HealthRecord>();
    private final List<HealthChecker> healthCheckers = new ArrayList<HealthChecker>();

    private Date lastHealthCheckDate = null;
    private int intervalSeconds = 0;
    private boolean open = true;

    public HealthMonitor(final ContextManager contextManager) {
        this.contextManager = contextManager;
        this.intervalSeconds = contextManager.getConfig().readSettingAsInt(PwmSetting.EVENTS_HEALTH_CHECK_MIN_INTERVAL);

        if (intervalSeconds < MIN_INTERVAL_SECONDS) {
            intervalSeconds = MIN_INTERVAL_SECONDS;
        } else if (intervalSeconds > MAX_INTERVAL_SECONDS) {
            intervalSeconds = MAX_INTERVAL_SECONDS;
        }

        final HealthRecord hr = new HealthRecord(HealthRecord.HealthStatus.CAUTION, HealthMonitor.class.getSimpleName(), "Health Check operation has not been performed since PWM has started.");
        healthRecords.add(hr);
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
        if (lastHealthCheckDate == null) {
            doHealthChecks();
        } else {
            final long lastHealthCheckMs = lastHealthCheckDate.getTime();
            final long lastValidHealthCheckMs = System.currentTimeMillis() - (intervalSeconds * 1000);
            if (lastHealthCheckMs < lastValidHealthCheckMs) {
                doHealthChecks();
            }
        }
        return Collections.unmodifiableList(healthRecords);
    }

    public void close() {
        healthRecords.clear();
        healthRecords.add(new HealthRecord(HealthRecord.HealthStatus.CAUTION, HealthMonitor.class.getSimpleName(), "Health Monitor has been closed."));
        open = false;
    }

    private void doHealthChecks() {
        if (!open) {
            return;
        }

        LOGGER.trace("beginning health check process");
        final List<HealthRecord> newResults = new ArrayList<HealthRecord>();
        for (final HealthChecker loopChecker : healthCheckers) {
            try {
                final List<HealthRecord> loopResults = loopChecker.doHealthCheck(contextManager);
                if (loopResults != null) {
                    newResults.addAll(loopResults);
                }
            } catch (Exception e) {
                LOGGER.warn("unexpected error during healthCheck: " + e.getMessage(), e);
            }
        }
        healthRecords.clear();
        healthRecords.addAll(newResults);
        lastHealthCheckDate = new Date();
        LOGGER.trace("health check process completed");
    }
}
