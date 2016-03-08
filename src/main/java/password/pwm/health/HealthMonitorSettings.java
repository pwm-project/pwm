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

import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.util.TimeDuration;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

class HealthMonitorSettings implements Serializable {
    private TimeDuration nominalCheckInterval = new TimeDuration(1, TimeUnit.MINUTES);
    private TimeDuration minimumCheckInterval = new TimeDuration(30, TimeUnit.SECONDS);
    private TimeDuration maximumRecordAge = new TimeDuration(5, TimeUnit.MINUTES);
    private TimeDuration maximumForceCheckWait = new TimeDuration(30, TimeUnit.SECONDS);

    public TimeDuration getMaximumRecordAge() {
        return maximumRecordAge;
    }

    public TimeDuration getMinimumCheckInterval() {
        return minimumCheckInterval;
    }

    public TimeDuration getNominalCheckInterval() {
        return nominalCheckInterval;
    }

    public TimeDuration getMaximumForceCheckWait() {
        return maximumForceCheckWait;
    }

    public static HealthMonitorSettings fromConfiguration(final Configuration config) {
        final HealthMonitorSettings settings = new HealthMonitorSettings();
        settings.nominalCheckInterval = new TimeDuration(Long.parseLong(config.readAppProperty(AppProperty.HEALTHCHECK_NOMINAL_CHECK_INTERVAL)), TimeUnit.SECONDS);
        settings.minimumCheckInterval = new TimeDuration(Long.parseLong(config.readAppProperty(AppProperty.HEALTHCHECK_MIN_CHECK_INTERVAL)), TimeUnit.SECONDS);
        settings.maximumRecordAge = new TimeDuration(Long.parseLong(config.readAppProperty(AppProperty.HEALTHCHECK_MAX_RECORD_AGE)), TimeUnit.SECONDS);
        settings.maximumForceCheckWait = new TimeDuration(Long.parseLong(config.readAppProperty(AppProperty.HEALTHCHECK_MAX_FORCE_WAIT)), TimeUnit.SECONDS);
        return settings;
    }
}
