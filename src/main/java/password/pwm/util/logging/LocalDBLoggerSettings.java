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

package password.pwm.util.logging;

import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.util.TimeDuration;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class LocalDBLoggerSettings implements Serializable {
    final static int MINIMUM_MAXIMUM_EVENTS = 100;
    final static TimeDuration MINIMUM_MAX_AGE = TimeDuration.HOUR;

    private final int maxEvents;
    private final TimeDuration maxAge;
    private final Set<Flag> flags;

    public enum Flag {
        DevDebug,
    }

    public LocalDBLoggerSettings(int maxEvents, TimeDuration maxAge, Set<Flag> flags) {
        this.maxEvents = maxEvents < 1 ? 0 : Math.max(MINIMUM_MAXIMUM_EVENTS, maxEvents);
        this.maxAge = maxAge == null || maxAge.isShorterThan(MINIMUM_MAX_AGE) ? MINIMUM_MAX_AGE : maxAge;
        this.flags = flags == null ? Collections.<Flag>emptySet() : Collections.unmodifiableSet(flags);
    }

    public int getMaxEvents() {
        return maxEvents;
    }

    public TimeDuration getMaxAge() {
        return maxAge;
    }

    public Set<Flag> getFlags() {
        return flags;
    }

    public static LocalDBLoggerSettings fromConfiguration(final Configuration configuration) {
        final Set<Flag> flags = new HashSet<>();
        if (configuration.isDevDebugMode()) {
            flags.add(Flag.DevDebug);
        }
        final int maxEvents = (int) configuration.readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_EVENTS);
        final long maxAgeMS = 1000 * configuration.readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_AGE);
        final TimeDuration maxAge = new TimeDuration(maxAgeMS);
        return new LocalDBLoggerSettings(maxEvents, maxAge, flags);
    }
}
