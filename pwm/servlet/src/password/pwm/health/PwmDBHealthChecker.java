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

package password.pwm.health;

import password.pwm.PwmApplication;
import password.pwm.util.pwmdb.PwmDB;

import java.util.ArrayList;
import java.util.List;

public class PwmDBHealthChecker implements HealthChecker {
    public List<HealthRecord> doHealthCheck(final PwmApplication pwmApplication) {
        if (pwmApplication == null) {
            return null;
        }

        final List<HealthRecord> healthRecords = new ArrayList<HealthRecord>();

        final PwmDB pwmDB = pwmApplication.getPwmDB();

        if (pwmDB == null) {
            healthRecords.add(new HealthRecord(HealthStatus.WARN, "PwmDB", "PwmDB is not available, statistics, online logging, wordlists and other features are disabled.  Check startup logs to troubleshoot"));
            return healthRecords;
        }

        if (PwmDB.Status.NEW == pwmDB.status()) {
            healthRecords.add(new HealthRecord(HealthStatus.WARN, "PwmDB", "PwmDB status is NEW (loading) state, until PwmDB loads, statistics, online logging, wordlists and other features are disabled"));
            return healthRecords;
        }

        if (PwmDB.Status.CLOSED == pwmDB.status()) {
            healthRecords.add(new HealthRecord(HealthStatus.WARN, "PwmDB", "PwmDB is CLOSED, statistics, online logging, wordlists and other features are disabled.  Check logs to troubleshoot"));
            return healthRecords;
        }

        if (healthRecords.isEmpty()) {
            healthRecords.add(new HealthRecord(HealthStatus.GOOD, "PwmDB", "PwmDB and related services are operating correctly"));
        }

        return healthRecords;
    }
}
