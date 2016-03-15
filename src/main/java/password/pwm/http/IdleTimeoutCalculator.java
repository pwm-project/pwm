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

package password.pwm.http;

import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.util.concurrent.TimeUnit;

public class IdleTimeoutCalculator {
    final static private PwmLogger LOGGER = PwmLogger.forClass(IdleTimeoutCalculator.class);

    public static TimeDuration figureMaxIdleTimeout(final PwmApplication pwmApplication, final PwmSession pwmSession) {
        final Configuration configuration = pwmApplication.getConfig();
        long idleSeconds = configuration.readSettingAsLong(PwmSetting.IDLE_TIMEOUT_SECONDS);

        if (configuration.readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE)) {
            final HelpdeskProfile helpdeskProfile = pwmSession.getSessionManager().getHelpdeskProfile(pwmApplication);
            if (helpdeskProfile != null) {
                final long helpdeskIdleTimeout = helpdeskProfile.readSettingAsLong(PwmSetting.HELPDESK_IDLE_TIMEOUT_SECONDS);
                idleSeconds = Math.max(idleSeconds, helpdeskIdleTimeout);
            }
        }

        if (configuration.readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_ENABLE)) {
            final long peopleSearchIdleTimeout = configuration.readSettingAsLong(PwmSetting.PEOPLE_SEARCH_IDLE_TIMEOUT_SECONDS);
            idleSeconds = Math.max(idleSeconds, peopleSearchIdleTimeout);
        }

        if (pwmApplication.getApplicationMode() == PwmApplicationMode.NEW) {
            final long configGuideIdleTimeout = Long.parseLong(configuration.readAppProperty(AppProperty.CONFIG_GUIDE_IDLE_TIMEOUT));
            idleSeconds = Math.max(idleSeconds, configGuideIdleTimeout);
        }

        try {
            if (pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.PWMADMIN)) {
                final long configEditorIdleTimeout = Long.parseLong(configuration.readAppProperty(AppProperty.CONFIG_EDITOR_IDLE_TIMEOUT));
                idleSeconds = Math.max(idleSeconds, configEditorIdleTimeout);
            }
        } catch (PwmUnrecoverableException e) {
            LOGGER.error(pwmSession,"error while figuring max idle timeout for session: " + e.getMessage());
        }

        return new TimeDuration(idleSeconds, TimeUnit.SECONDS);
    }

    public static TimeDuration idleTimeoutForRequest(final PwmRequest pwmRequest) {
        return idleTimeoutForRequest(pwmRequest.getURL(),pwmRequest.getPwmApplication(),pwmRequest.getPwmSession());
    }

    public static TimeDuration idleTimeoutForRequest(final PwmURL pwmURL, final PwmApplication pwmApplication, final PwmSession pwmSession) {
        final Configuration config = pwmApplication.getConfig();
        if (pwmURL.isPwmServletURL(PwmServletDefinition.Helpdesk)) {
            if (config.readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE)) {
                final HelpdeskProfile helpdeskProfile = pwmSession.getSessionManager().getHelpdeskProfile(pwmApplication);
                if (helpdeskProfile != null) {
                    final long helpdeskIdleTimeout = helpdeskProfile.readSettingAsLong(PwmSetting.HELPDESK_IDLE_TIMEOUT_SECONDS);
                    if (helpdeskIdleTimeout > 0) {
                        return new TimeDuration(helpdeskIdleTimeout, TimeUnit.SECONDS);
                    }
                }
            }
        }

        if (pwmURL.isPwmServletURL(PwmServletDefinition.PeopleSearch) && pwmURL.isPrivateUrl()) {
            if (config.readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_ENABLE)) {
                final long peopleSearchIdleTimeout = config.readSettingAsLong(PwmSetting.PEOPLE_SEARCH_IDLE_TIMEOUT_SECONDS);
                if (peopleSearchIdleTimeout > 0) {
                    return new TimeDuration(peopleSearchIdleTimeout, TimeUnit.SECONDS);
                }
            }
        }

        if (pwmURL.isPwmServletURL(PwmServletDefinition.ConfigEditor)) {
            try {
                if (pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.PWMADMIN)) {
                    final long configEditorIdleTimeout = Long.parseLong(config.readAppProperty(AppProperty.CONFIG_EDITOR_IDLE_TIMEOUT));
                    if (configEditorIdleTimeout > 0) {
                        return new TimeDuration(configEditorIdleTimeout, TimeUnit.SECONDS);
                    }
                }
            } catch (PwmUnrecoverableException e) {
                LOGGER.error(pwmSession,"error while figuring max idle timeout for session: " + e.getMessage());
            }
        }

        if (pwmURL.isPwmServletURL(PwmServletDefinition.ConfigGuide)) {
            if (pwmApplication.getApplicationMode() == PwmApplicationMode.NEW) {
                final long configGuideIdleTimeout = Long.parseLong(config.readAppProperty(AppProperty.CONFIG_GUIDE_IDLE_TIMEOUT));
                if (configGuideIdleTimeout > 0) {
                    return new TimeDuration(configGuideIdleTimeout, TimeUnit.SECONDS);
                }
            }
        }

        final long idleTimeout = config.readSettingAsLong(PwmSetting.IDLE_TIMEOUT_SECONDS);
        return new TimeDuration(idleTimeout, TimeUnit.SECONDS);
    }
}
