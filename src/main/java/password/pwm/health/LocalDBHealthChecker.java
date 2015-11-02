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

package password.pwm.health;

import password.pwm.PwmApplication;
import password.pwm.util.localdb.LocalDB;

import java.util.ArrayList;
import java.util.List;

public class LocalDBHealthChecker implements HealthChecker {
    public List<HealthRecord> doHealthCheck(final PwmApplication pwmApplication) {
        if (pwmApplication == null) {
            return null;
        }

        final List<HealthRecord> healthRecords = new ArrayList<>();

        final LocalDB localDB = pwmApplication.getLocalDB();

        if (localDB == null) {
            final String detailedError = pwmApplication.getLastLocalDBFailure() == null ? "unknown, check logs" : pwmApplication.getLastLocalDBFailure().toDebugStr();
            healthRecords.add(HealthRecord.forMessage(HealthMessage.LocalDB_BAD,detailedError));
            return healthRecords;
        }

        if (LocalDB.Status.NEW == localDB.status()) {
            healthRecords.add(HealthRecord.forMessage(HealthMessage.LocalDB_NEW));
            return healthRecords;
        }

        if (LocalDB.Status.CLOSED == localDB.status()) {
            healthRecords.add(HealthRecord.forMessage(HealthMessage.LocalDB_CLOSED));
            return healthRecords;
        }

        if (healthRecords.isEmpty()) {
            healthRecords.add(HealthRecord.forMessage(HealthMessage.LocalDB_OK));
        }

        return healthRecords;
    }
}
