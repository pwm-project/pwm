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

package password.pwm.http.servlet.configguide;

import password.pwm.config.PwmSettingTemplate;
import password.pwm.http.bean.ConfigGuideBean;
import password.pwm.util.logging.PwmLogger;

public enum GuideStep {
    START(null),
    TEMPLATE(null),
    LDAP_SERVER(null),
    LDAP_CERT(null),
    LDAP_PROXY(null),
    LDAP_CONTEXT(null),
    LDAP_ADMINS(null),
    LDAP_TESTUSER(null),
    CR_STORAGE(null),
    LDAP_SCHEMA(LdapSchemeVisibilityCheck.class),
    DATABASE(DbVisibilityCheck.class),
    APP(null),
    PASSWORD(null),
    END(null),
    FINISH(null),

    ;

    private static final PwmLogger LOGGER = PwmLogger.forClass(GuideStep.class);

    private final Class<? extends VisibilityCheck> visibilityCheckClass;

    GuideStep(Class<? extends VisibilityCheck> visibilityCheckClass) {
        this.visibilityCheckClass = visibilityCheckClass;
    }

    public GuideStep next() {
        return peer(+1);
    }

    public GuideStep previous() {
        return peer(-1);
    }

    private GuideStep peer(int distance) {
        return values()[(this.ordinal()+distance) % values().length];
    }

    boolean visible(ConfigGuideBean configGuideBean) {
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
        public boolean visible(ConfigGuideBean configGuideBean) {
            final ConfigGuideForm.Cr_Storage_Pref selectedPref = ConfigGuideForm.Cr_Storage_Pref.valueOf(configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_CR_STORAGE_PREF));
            return ConfigGuideForm.Cr_Storage_Pref.LDAP == selectedPref
                    && (configGuideBean.getSelectedTemplate() == PwmSettingTemplate.NOVL || configGuideBean.getSelectedTemplate() == PwmSettingTemplate.NOVL_IDM);
        }
    }

    static class DbVisibilityCheck implements VisibilityCheck {
        public boolean visible(ConfigGuideBean configGuideBean) {
            final ConfigGuideForm.Cr_Storage_Pref selectedPref = ConfigGuideForm.Cr_Storage_Pref.valueOf(configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_CR_STORAGE_PREF));
            return ConfigGuideForm.Cr_Storage_Pref.DB == selectedPref;
        }
    }
}
