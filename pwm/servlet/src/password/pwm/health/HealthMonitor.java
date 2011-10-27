/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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

package password.pwm.health;

import password.pwm.PwmApplication;
import password.pwm.PwmService;
import password.pwm.config.PwmSetting;
import password.pwm.util.PwmLogger;

import java.io.Serializable;
import java.util.*;

public class HealthMonitor implements Serializable {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(HealthMonitor.class);
    private static final int MIN_INTERVAL_SECONDS = 30;
    private static final int MAX_INTERVAL_SECONDS = 60 * 60 * 24;

    private final PwmApplication pwmApplication;
    private Set<HealthRecord> healthRecords = Collections.emptySet();
    private final List<HealthChecker> healthCheckers = new ArrayList<HealthChecker>();

    private Date lastHealthCheckDate = null;
    private int intervalSeconds = 0;

    private boolean open = true;

    public HealthMonitor(final PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
        this.intervalSeconds = (int) pwmApplication.getConfig().readSettingAsLong(PwmSetting.EVENTS_HEALTH_CHECK_MIN_INTERVAL);

        if (intervalSeconds < MIN_INTERVAL_SECONDS) {
            intervalSeconds = MIN_INTERVAL_SECONDS;
        } else if (intervalSeconds > MAX_INTERVAL_SECONDS) {
            intervalSeconds = MAX_INTERVAL_SECONDS;
        }

        final Set<HealthRecord> newHealthRecords = new HashSet<HealthRecord>();
        newHealthRecords.add(new HealthRecord(HealthStatus.CAUTION, HealthMonitor.class.getSimpleName(), "Health Check operation has not been performed since PWM has started."));
        healthRecords = Collections.unmodifiableSet(newHealthRecords);
    }

    public Date getLastHealthCheckDate() {
        return lastHealthCheckDate;
    }

    public HealthStatus getMostSevereHealthStatus() {
        final Set<HealthRecord> healthRecords = getHealthRecords();
        HealthStatus returnStatus = HealthStatus.GOOD;
        if (healthRecords != null) {
            for (HealthRecord record : healthRecords) {
                if (record.getStatus().getSeverityLevel() > returnStatus.getSeverityLevel()) {
                    returnStatus = record.getStatus();
                }
            }
        }
        return returnStatus;
    }

    public void registerHealthCheck(final HealthChecker healthChecker) {
        healthCheckers.add(healthChecker);
    }

    public Set<HealthRecord> getHealthRecords() {
        return getHealthRecords(false);
    }

    public Set<HealthRecord> getHealthRecords(final boolean refreshImmediate) {
        if (lastHealthCheckDate == null || refreshImmediate) {
            doHealthChecks();
        } else {
            final long lastHealthCheckMs = lastHealthCheckDate.getTime();
            final long lastValidHealthCheckMs = System.currentTimeMillis() - (intervalSeconds * 1000);
            if (lastHealthCheckMs < lastValidHealthCheckMs) {
                doHealthChecks();
            }
        }
        return healthRecords;
    }

    public void close() {
        final Set<HealthRecord> closeSet = new HashSet<HealthRecord>();
        closeSet.add(new HealthRecord(HealthStatus.CAUTION, HealthMonitor.class.getSimpleName(), "Health Monitor has been closed."));
        healthRecords = Collections.unmodifiableSet(closeSet);
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
                final List<HealthRecord> loopResults = loopChecker.doHealthCheck(pwmApplication);
                if (loopResults != null) {
                    newResults.addAll(loopResults);
                }
            } catch (Exception e) {
                LOGGER.warn("unexpected error during healthCheck: " + e.getMessage(), e);
            }
        }
        for (final PwmService service : pwmApplication.getPwmServices()) {
            final List<HealthRecord> loopResults = service.healthCheck();
            if (loopResults != null) {
                newResults.addAll(loopResults);
            }
        }
        final Set<HealthRecord> sortedRecordList = new TreeSet<HealthRecord>();
        sortedRecordList.addAll(newResults);
        healthRecords = Collections.unmodifiableSet(sortedRecordList);
        lastHealthCheckDate = new Date();
        LOGGER.trace("health check process completed");
    }
}
