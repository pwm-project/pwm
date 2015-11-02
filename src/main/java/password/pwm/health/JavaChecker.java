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

import password.pwm.AppProperty;
import password.pwm.PwmApplication;

import java.util.ArrayList;
import java.util.List;

public class JavaChecker implements HealthChecker {
    public List<HealthRecord> doHealthCheck(final PwmApplication pwmApplication) {
        final List<HealthRecord> records = new ArrayList<>();

        final int maxActiveThreads = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.HEALTH_JAVA_MAX_THREADS));
        if (Thread.activeCount() > maxActiveThreads) {
            records.add(HealthRecord.forMessage(HealthMessage.Java_HighThreads));
        }

        final long minMemory = Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.HEALTH_JAVA_MIN_HEAP_BYTES));
        if (Runtime.getRuntime().maxMemory() <= minMemory) {
            records.add(HealthRecord.forMessage(HealthMessage.Java_SmallHeap));
        }

        if (records.isEmpty()) {
            records.add(HealthRecord.forMessage(HealthMessage.Java_OK));
        }

        return records;
    }
}
