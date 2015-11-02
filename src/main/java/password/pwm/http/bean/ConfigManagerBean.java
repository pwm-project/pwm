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

package password.pwm.http.bean;

import password.pwm.config.stored.StoredConfigurationImpl;

public class ConfigManagerBean implements PwmSessionBean {
    private StoredConfigurationImpl storedConfiguration;
    private boolean passwordVerified;
    private boolean configUnlockedWarningShown;

    private String prePasswordEntryUrl;

    public ConfigManagerBean() {
    }

    public StoredConfigurationImpl getStoredConfiguration() {
        return storedConfiguration;
    }

    public void setConfiguration(final StoredConfigurationImpl storedConfiguration) {
        this.storedConfiguration = storedConfiguration;
    }

    public boolean isPasswordVerified() {
        return passwordVerified;
    }

    public void setPasswordVerified(boolean passwordVerified) {
        this.passwordVerified = passwordVerified;
    }

    public String getPrePasswordEntryUrl()
    {
        return prePasswordEntryUrl;
    }

    public void setPrePasswordEntryUrl(String prePasswordEntryUrl)
    {
        this.prePasswordEntryUrl = prePasswordEntryUrl;
    }

    public boolean isConfigUnlockedWarningShown() {
        return configUnlockedWarningShown;
    }

    public void setConfigUnlockedWarningShown(boolean configUnlockedWarningShown) {
        this.configUnlockedWarningShown = configUnlockedWarningShown;
    }
}
