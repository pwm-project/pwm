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

import password.pwm.ContextManager;
import password.pwm.config.PwmSetting;
import password.pwm.util.PwmDBLogger;
import password.pwm.util.TimeDuration;
import password.pwm.util.db.PwmDB;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

public class PwmDBHealthChecker implements HealthChecker {
    public List<HealthRecord> doHealthCheck(final ContextManager contextManager) {
        if (contextManager == null) {
            return null;
        }

        final List<HealthRecord> healthRecords = new ArrayList<HealthRecord>();

        final PwmDB pwmDB = contextManager.getPwmDB();

        if (pwmDB == null) {
            healthRecords.add(new HealthRecord(HealthStatus.WARN, "PwmDB", "PwmDB is not available, statistics, online logging, wordlists and other features are disabled.  Check startup logs to troubleshoot"));
            return healthRecords;
        }

        if (PwmDB.Status.NEW == pwmDB.getStatus()) {
            healthRecords.add(new HealthRecord(HealthStatus.WARN, "PwmDB", "PwmDB status is NEW (loading) state, until PwmDB loads, statistics, online logging, wordlists and other features are disabled"));
            return healthRecords;
        }

        if (PwmDB.Status.CLOSED == pwmDB.getStatus()) {
            healthRecords.add(new HealthRecord(HealthStatus.WARN, "PwmDB", "PwmDB is CLOSED, statistics, online logging, wordlists and other features are disabled.  Check logs to troubleshoot"));
            return healthRecords;
        }

        if (contextManager.getConfig() != null) {
            final PwmDBLogger pwmDBLogger = contextManager.getPwmDBLogger();
            if (pwmDBLogger != null) {
                final int eventCount = pwmDBLogger.getStoredEventCount();
                final int maxEventCount = (int) contextManager.getConfig().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_EVENTS);
                if (eventCount > maxEventCount + 5000) {
                    healthRecords.add(new HealthRecord(HealthStatus.WARN, "PwmDB", "PwmDB Logger contains " + NumberFormat.getInstance().format(eventCount) + " records, more than the configured maximum of " + NumberFormat.getInstance().format(maxEventCount)));
                }

                final long maxTailMs = contextManager.getConfig().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_AGE) * 1000L;
                final long tailDate = pwmDBLogger.getTailTimestamp();
                final long maxTailDate = System.currentTimeMillis() - maxTailMs;
                if (tailDate < maxTailDate - (60 * 60 * 1000)) { // older than an hour past tail date
                    healthRecords.add(new HealthRecord(HealthStatus.WARN, "PwmDB", "PwmDB Logger contains records older than the configured maximum of " + new TimeDuration(maxTailMs).asLongString()));
                }
            } else {
                healthRecords.add(new HealthRecord(HealthStatus.WARN, "PwmDB", "PwmDB Logger is not running"));
            }
        }

        if (healthRecords.isEmpty()) {
            healthRecords.add(new HealthRecord(HealthStatus.GOOD, "PwmDB", "PwmDB and related services are operating correctly"));
        }

        return healthRecords;
    }
}
