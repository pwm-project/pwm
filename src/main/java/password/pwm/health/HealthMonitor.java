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

package password.pwm.health;

import password.pwm.PwmApplication;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.svc.PwmService;
import password.pwm.util.Helper;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;

public class HealthMonitor implements PwmService {
    private static final PwmLogger LOGGER = PwmLogger.forClass(HealthMonitor.class);

    private PwmApplication pwmApplication;

    private final Set<HealthRecord> healthRecords = new TreeSet<>();

    private static final List<HealthChecker> HEALTH_CHECKERS;
    static {
        final List<HealthChecker> records = new ArrayList<>();
        records.add(new LDAPStatusChecker());
        records.add(new JavaChecker());
        records.add(new ConfigurationChecker());
        records.add(new LocalDBHealthChecker());
        records.add(new CertificateChecker());
        HEALTH_CHECKERS = records;
    }

    private ScheduledExecutorService executorService;
    private HealthMonitorSettings settings;

    private volatile Date lastHealthCheckTime = new Date(0);
    private volatile Date lastRequestedUpdateTime = new Date(0);

    private Map<HealthMonitorFlag, Serializable> healthProperties = new HashMap<>();

    private STATUS status = STATUS.NEW;

    enum HealthMonitorFlag {
        LdapVendorSameCheck,
        AdPasswordPolicyApiCheck,
    }

    public enum CheckTimeliness {
        /* Execute update immediately and wait for results */
        Immediate,

        /* Take current data unless its ancient */
        CurrentButNotAncient,

        /* Take current data even if its ancient and never block */
        NeverBlock,
    }

    public HealthMonitor() {
    }

    public Date getLastHealthCheckTime() {
        return lastHealthCheckTime;
    }

    public HealthStatus getMostSevereHealthStatus(CheckTimeliness timeliness) {
        return getMostSevereHealthStatus(getHealthRecords(timeliness));
    }

    public static HealthStatus getMostSevereHealthStatus(final Collection<HealthRecord> healthRecords) {
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

    public STATUS status() {
        return status;
    }

    public void init(PwmApplication pwmApplication) throws PwmException {
        status = STATUS.OPENING;
        this.pwmApplication = pwmApplication;
        settings = HealthMonitorSettings.fromConfiguration(pwmApplication.getConfig());

        executorService = Executors.newSingleThreadScheduledExecutor(
                Helper.makePwmThreadFactory(
                        Helper.makeThreadName(pwmApplication, this.getClass()) + "-",
                        true
                ));


        executorService.scheduleAtFixedRate(new ScheduledUpdater(), 0, settings.getNominalCheckInterval().getTotalMilliseconds(), TimeUnit.MILLISECONDS);

        status = STATUS.OPEN;
    }

    public Set<HealthRecord> getHealthRecords(final CheckTimeliness timeliness) {
        lastRequestedUpdateTime = new Date();

        {
            final boolean recordsAreStale = TimeDuration.fromCurrent(lastHealthCheckTime).isLongerThan(settings.getMaximumRecordAge());
            if (timeliness == CheckTimeliness.Immediate || (timeliness == CheckTimeliness.CurrentButNotAncient && recordsAreStale)) {
                final ScheduledFuture updateTask = executorService.schedule(new ImmediateUpdater(), 0, TimeUnit.NANOSECONDS);
                final Date beginWaitTime = new Date();
                while (!updateTask.isDone() && TimeDuration.fromCurrent(beginWaitTime).isShorterThan(settings.getMaximumForceCheckWait())) {
                    Helper.pause(500);
                }
            }
        }

        final boolean recordsAreStale = TimeDuration.fromCurrent(lastHealthCheckTime).isLongerThan(settings.getMaximumRecordAge());
        if (recordsAreStale) {
            return Collections.singleton(HealthRecord.forMessage(HealthMessage.NoData));
        }

        return Collections.unmodifiableSet(healthRecords);
    }

    public void close() {
        if (executorService != null) {
            executorService.shutdown();
        }
        healthRecords.clear();
        status = STATUS.CLOSED;
    }

    public List<HealthRecord> healthCheck() {
        return Collections.emptyList();
    }

    private void doHealthChecks() {
        if (status != STATUS.OPEN) {
            return;
        }

        final TimeDuration timeSinceLastUpdate = TimeDuration.fromCurrent(lastHealthCheckTime);
        if (timeSinceLastUpdate.isShorterThan(settings.getMinimumCheckInterval().getTotalMilliseconds(), TimeUnit.MILLISECONDS)) {
            return;
        }

        final Date startTime = new Date();
        LOGGER.trace("beginning background health check process");
        final List<HealthRecord> tempResults = new ArrayList<>();
        for (final HealthChecker loopChecker : HEALTH_CHECKERS) {
            try {
                final List<HealthRecord> loopResults = loopChecker.doHealthCheck(pwmApplication);
                if (loopResults != null) {
                    tempResults.addAll(loopResults);
                }
            } catch (Exception e) {
                LOGGER.warn("unexpected error during healthCheck: " + e.getMessage(), e);
            }
        }
        for (final PwmService service : pwmApplication.getPwmServices()) {
            try {
                final List<HealthRecord> loopResults = service.healthCheck();
                if (loopResults != null) {
                    tempResults.addAll(loopResults);
                }
            } catch (Exception e) {
                LOGGER.warn("unexpected error during healthCheck: " + e.getMessage(), e);
            }
        }
        healthRecords.clear();
        healthRecords.addAll(tempResults);
        lastHealthCheckTime = new Date();
        LOGGER.trace("health check process completed (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")");
    }

    public ServiceInfo serviceInfo()
    {
        return new ServiceInfo(Collections.<DataStorageMethod>emptyList());
    }

    public Map<HealthMonitorFlag, Serializable> getHealthProperties()
    {
        return healthProperties;
    }

    private class ScheduledUpdater implements Runnable {
        @Override
        public void run() {
            final TimeDuration timeSinceLastRequest = TimeDuration.fromCurrent(lastRequestedUpdateTime);
            if (timeSinceLastRequest.isShorterThan(settings.getNominalCheckInterval().getTotalMilliseconds() + 1000, TimeUnit.MILLISECONDS)) {
                try {
                    doHealthChecks();
                } catch (Throwable e) {
                    LOGGER.error("error during health check execution: " + e.getMessage(), e);

                }
            }
        }
    }

    private class ImmediateUpdater implements Runnable {
        @Override
        public void run() {
            final TimeDuration timeSinceLastUpdate = TimeDuration.fromCurrent(lastHealthCheckTime);
            if (timeSinceLastUpdate.isLongerThan(settings.getMinimumCheckInterval().getTotalMilliseconds(), TimeUnit.MILLISECONDS)){
                try {
                    doHealthChecks();
                } catch (Throwable e) {
                    LOGGER.error("error during health check execution: " + e.getMessage(), e);
                }
            }
        }
    }

}
