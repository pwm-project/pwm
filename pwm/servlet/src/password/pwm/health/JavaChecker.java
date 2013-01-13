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
import password.pwm.i18n.Admin;
import password.pwm.i18n.LocaleHelper;

import java.util.ArrayList;
import java.util.List;

public class JavaChecker implements HealthChecker {
    private static final String TOPIC = "Java Platform";

    public List<HealthRecord> doHealthCheck(final PwmApplication pwmApplication) {
        final List<HealthRecord> records = new ArrayList<HealthRecord>();

        if (Thread.activeCount() > 1000) {
            records.add(new HealthRecord(HealthStatus.CAUTION, TOPIC, localizedString(pwmApplication,"Health_Java_HighThreads",String.valueOf(Thread.activeCount()))));
        }

        if (Runtime.getRuntime().maxMemory() <= 64 * 1024 * 1024) {
            records.add(new HealthRecord(HealthStatus.CAUTION, TOPIC, localizedString(pwmApplication,"Health_Java_SmallHeap")));
        }

        if (records.isEmpty()) {
            records.add(new HealthRecord(HealthStatus.GOOD, TOPIC, localizedString(pwmApplication,"Health_Java_OK")));
        }

        return records;
    }

    private String localizedString(final PwmApplication pwmApplication, final String key, final String... values) {
        return LocaleHelper.getLocalizedMessage(null, key, pwmApplication.getConfig(), Admin.class, values);
    }

}
