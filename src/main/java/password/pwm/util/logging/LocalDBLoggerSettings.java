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

import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.util.TimeDuration;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class LocalDBLoggerSettings implements Serializable {
    final static int MINIMUM_MAXIMUM_EVENTS = 100;
    final static TimeDuration MINIMUM_MAX_AGE = TimeDuration.HOUR;

    private final int maxEvents;
    private final TimeDuration maxAge;
    private final Set<Flag> flags;
    private final int maxBufferSize;
    private final TimeDuration maxBufferWaitTime;
    private final int maxTrimSize;

    public enum Flag {
        DevDebug,
    }

    private LocalDBLoggerSettings(
            int maxEvents,
            TimeDuration maxAge,
            Set<Flag> flags,
            int maxBufferSize,
            TimeDuration maxBufferWaitTime,
            int maxTrimSize
    ) {
        this.maxEvents = maxEvents < 1 ? 0 : Math.max(MINIMUM_MAXIMUM_EVENTS, maxEvents);
        this.maxAge = maxAge == null || maxAge.isShorterThan(MINIMUM_MAX_AGE) ? MINIMUM_MAX_AGE : maxAge;
        this.flags = flags == null ? Collections.<Flag>emptySet() : Collections.unmodifiableSet(flags);
        this.maxBufferSize = maxBufferSize;
        this.maxBufferWaitTime = maxBufferWaitTime;
        this.maxTrimSize = maxTrimSize;
    }

    public int getMaxEvents() {
        return maxEvents;
    }

    public TimeDuration getMaxBufferWaitTime() {
        return maxBufferWaitTime;
    }

    public TimeDuration getMaxAge() {
        return maxAge;
    }

    public Set<Flag> getFlags() {
        return flags;
    }

    public int getMaxBufferSize() {
        return maxBufferSize;
    }

    public int getMaxTrimSize() {
        return maxTrimSize;
    }

    public static LocalDBLoggerSettings fromConfiguration(final Configuration configuration) {
        final Set<Flag> flags = new HashSet<>();
        if (configuration.isDevDebugMode()) {
            flags.add(Flag.DevDebug);
        }
        final int maxEvents = (int) configuration.readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_EVENTS);
        final long maxAgeMS = 1000 * configuration.readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_AGE);
        final TimeDuration maxAge = new TimeDuration(maxAgeMS);
        final int maxBufferSize = Integer.parseInt(configuration.readAppProperty(AppProperty.LOCALDB_LOGWRITER_BUFFER_SIZE));
        final TimeDuration maxBufferWaitTime = new TimeDuration(Long.parseLong(configuration.readAppProperty(AppProperty.LOCALDB_LOGWRITER_MAX_BUFFER_WAIT_MS)));
        final int maxTrimSize = Integer.parseInt(configuration.readAppProperty(AppProperty.LOCALDB_LOGWRITER_MAX_TRIM_SIZE));

        return new Builder()
                .setMaxEvents(maxEvents)
                .setMaxAge(maxAge)
                .setFlags(flags)
                .setMaxBufferSize(maxBufferSize)
                .setMaxBufferWaitTime(maxBufferWaitTime)
                .setMaxTrimSize(maxTrimSize)
                .createLocalDBLoggerSettings();
    }

    public static class Builder {
        private int maxEvents = 1 * 1000 * 1000;
        private TimeDuration maxAge = new TimeDuration(7, TimeUnit.DAYS);
        private Set<Flag> flags = Collections.emptySet();
        private int maxBufferSize = 1000;
        private TimeDuration maxBufferWaitTime = new TimeDuration(1, TimeUnit.MINUTES);
        private int maxTrimSize = 501;

        public Builder setMaxEvents(int maxEvents) {
            this.maxEvents = maxEvents;
            return this;
        }

        public Builder setMaxAge(TimeDuration maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        public Builder setFlags(Set<Flag> flags) {
            this.flags = flags;
            return this;
        }

        public Builder setMaxTrimSize(int maxTrimSize) {
            this.maxTrimSize = maxTrimSize;
            return this;
        }

        public Builder setMaxBufferSize(int maxBufferSize) {
            this.maxBufferSize = maxBufferSize;
            return this;
        }

        public Builder setMaxBufferWaitTime(TimeDuration maxBufferWaitTime) {
            this.maxBufferWaitTime = maxBufferWaitTime;
            return this;
        }

        public LocalDBLoggerSettings createLocalDBLoggerSettings() {
            return new LocalDBLoggerSettings(maxEvents, maxAge, flags, maxBufferSize, maxBufferWaitTime, maxTrimSize);
        }
    }
}
