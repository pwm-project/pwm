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

package password.pwm.bean.servlet;

import password.pwm.PwmConstants;
import password.pwm.bean.PwmSessionBean;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredConfiguration;
import password.pwm.servlet.ConfigManagerServlet;

import java.util.Date;

public class ConfigManagerBean implements PwmSessionBean {
    private StoredConfiguration configuration;
    private ConfigManagerServlet.EDIT_MODE editMode = ConfigManagerServlet.EDIT_MODE.NONE;
    private java.util.Date configurationLoadTime;
    private int level = 0;
    private boolean showDescr = false;
    private PwmSetting.Category category = PwmSetting.Category.LDAP;
    private PwmConstants.EDITABLE_LOCALE_BUNDLES localeBundle;
    private boolean passwordVerified;
    private boolean passwordRequired;

    public ConfigManagerBean() {
    }

    public Date getConfigurationLoadTime() {
        return configurationLoadTime;
    }

    public void setConfigurationLoadTime(final Date configurationLoadTime) {
        this.configurationLoadTime = configurationLoadTime;
    }

    public StoredConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(final StoredConfiguration configuration) {
        this.configuration = configuration;
    }

    public ConfigManagerServlet.EDIT_MODE getEditMode() {
        return editMode;
    }

    public void setEditMode(final ConfigManagerServlet.EDIT_MODE editMode) {
        this.editMode = editMode;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public boolean isShowDescr() {
        return showDescr;
    }

    public void setShowDescr(final boolean showDescr) {
        this.showDescr = showDescr;
    }

    public PwmSetting.Category getCategory() {
        return category;
    }

    public void setCategory(final PwmSetting.Category category) {
        this.category = category;
    }

    public PwmConstants.EDITABLE_LOCALE_BUNDLES getLocaleBundle() {
        return localeBundle;
    }

    public void setLocaleBundle(final PwmConstants.EDITABLE_LOCALE_BUNDLES localeBundle) {
        this.localeBundle = localeBundle;
    }

    public boolean isPasswordVerified() {
        return passwordVerified;
    }

    public void setPasswordVerified(boolean passwordVerified) {
        this.passwordVerified = passwordVerified;
    }

    public boolean isPasswordRequired() {
        return passwordRequired;
    }

    public void setPasswordRequired(boolean passwordRequired) {
        this.passwordRequired = passwordRequired;
    }
}
