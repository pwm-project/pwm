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

import password.pwm.i18n.Health;
import password.pwm.util.LocaleHelper;

import java.util.Locale;

public enum HealthStatus {
    WARN(4),
    CAUTION(3),
    CONFIG(2),
    GOOD(1),
    INFO(0),
    DEBUG(-1),

    ;

    private int severityLevel;

    HealthStatus(int severityLevel) {
        this.severityLevel = severityLevel;
    }

    public String getKey() {
        return HealthStatus.class.getSimpleName() + "_" + this.toString();
    }

    public String getDescription(final Locale locale, final password.pwm.config.Configuration config) {
        return LocaleHelper.getLocalizedMessage(locale, this.getKey(), config, Health.class);
    }

    public int getSeverityLevel() {
        return severityLevel;
    }
}
