/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.g
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

package password.pwm.http.servlet.configguide;

import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingTemplate;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.ConfigGuideBean;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;

import java.util.Set;

public enum GuideStep {
    START(null),
    EULA(EulaVisibilityCheck.class),
    MENU(null),
    TELEMETRY(TelemetryVisibilityCheck.class),
    TEMPLATE(null),
    LDAP_SERVER(null),
    LDAP_CERT(null),
    LDAP_PROXY(null),
    LDAP_CONTEXT(null),
    LDAP_ADMINS(null),
    STORAGE(null),
    LDAP_SCHEMA(LdapSchemeVisibilityCheck.class),
    DATABASE(DbVisibilityCheck.class),
    LDAP_PERMISSIONS(LdapSchemeVisibilityCheck.class),
    LDAP_TESTUSER(null),
    APP(null),
    PASSWORD(null),
    END(null),
    FINISH(null),

    NEXT(NeverVisible.class),
    PREVIOUS(NeverVisible.class),

    ;

    private static final PwmLogger LOGGER = PwmLogger.forClass(GuideStep.class);

    private final Class<? extends VisibilityCheck> visibilityCheckClass;

    GuideStep(final Class<? extends VisibilityCheck> visibilityCheckClass) {
        this.visibilityCheckClass = visibilityCheckClass;
    }

    public GuideStep next() {
        return peer(+1);
    }

    public GuideStep previous() {
        return peer(-1);
    }

    private GuideStep peer(final int distance) {
        if (distance != -1 && distance != 1) {
            throw new IllegalArgumentException("distance must be +1 or -1");
        }

        final int nextOrdinal = JavaHelper.rangeCheck(
                START.ordinal(),
                FINISH.ordinal(),
                this.ordinal() + distance
        );

        return GuideStep.values()[nextOrdinal];
    }

    boolean visible(final ConfigGuideBean configGuideBean) {
        if (this == NEXT || this == PREVIOUS) {
            return false;
        }

        if (visibilityCheckClass != null) {
            final VisibilityCheck visibilityCheckImpl;
            try {
                visibilityCheckImpl = visibilityCheckClass.newInstance();
                return visibilityCheckImpl.visible(configGuideBean);
            } catch (ReflectiveOperationException e) {
                LOGGER.error("unexpected error during step visibility check: " + e.getMessage(), e);
            }
        }

        return true;
    }

    interface VisibilityCheck {
        boolean visible(ConfigGuideBean configGuideBean);
    }

    static class LdapSchemeVisibilityCheck implements VisibilityCheck {
        public boolean visible(final ConfigGuideBean configGuideBean) {
            try {
                final Set<PwmSettingTemplate> templates = ConfigGuideForm.generateStoredConfig(configGuideBean).getTemplateSet().getTemplates();
                return templates.contains(PwmSettingTemplate.LDAP);
            } catch (PwmUnrecoverableException e) {
                return true;
            }
        }
    }

    static class DbVisibilityCheck implements VisibilityCheck {
        public boolean visible(final ConfigGuideBean configGuideBean) {
            try {
                final Set<PwmSettingTemplate> templates = ConfigGuideForm.generateStoredConfig(configGuideBean).getTemplateSet().getTemplates();
                return templates.contains(PwmSettingTemplate.DB);
            } catch (PwmUnrecoverableException e) {
                return true;
            }
        }
    }

    static class EulaVisibilityCheck implements VisibilityCheck {
        public boolean visible(final ConfigGuideBean configGuideBean) {
            return PwmConstants.ENABLE_EULA_DISPLAY;
        }
    }

    static class TelemetryVisibilityCheck implements VisibilityCheck {
        public boolean visible(final ConfigGuideBean configGuideBean) {
            return !PwmSetting.PUBLISH_STATS_ENABLE.isHidden() &&
                    !PwmSettingCategory.TELEMETRY.isHidden();
        }
    }

    static class NeverVisible implements VisibilityCheck {
        public boolean visible(final ConfigGuideBean configGuideBean) {
            return false;
        }
    }
}
