/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.config;

import password.pwm.PwmConstants;

import java.util.*;

public class LdapProfile extends AbstractProfile implements Profile {
    final protected static List<PwmSetting> LDAP_SETTINGS = Collections.unmodifiableList(PwmSetting.getSettings(
            PwmSettingCategory.LDAP_PROFILE));

    protected LdapProfile(String identifier, Map<PwmSetting, StoredValue> storedValueMap) {
        super(identifier, storedValueMap);
    }

    static LdapProfile makeFromStoredConfiguration(final StoredConfiguration storedConfiguration, final String identifier) {
        final Map<PwmSetting,StoredValue> valueMap = new LinkedHashMap<>();
        for (final PwmSetting setting : LDAP_SETTINGS) {
            final StoredValue value = storedConfiguration.readSetting(setting, PwmConstants.PROFILE_ID_DEFAULT.equals(identifier) ? "" : identifier);
            valueMap.put(setting, value);
        }
        return new LdapProfile(identifier, valueMap);

    }

    public Map<String, String> getLoginContexts() {
        final List<String> values = readSettingAsStringArray(PwmSetting.LDAP_LOGIN_CONTEXTS);
        return Configuration.convertStringListToNameValuePair(values, ":::");
    }

    @Override
    public String getDisplayName(final Locale locale) {
        final String displayName = readSettingAsLocalizedString(PwmSetting.LDAP_PROFILE_DISPLAY_NAME,locale);
        final String returnDisplayName = displayName == null || displayName.length() < 1 ? identifier : displayName;
        return PwmConstants.PROFILE_ID_DEFAULT.equals(returnDisplayName) ? "Default" : returnDisplayName;
    }

    public String getUsernameAttribute() {
        final String configUsernameAttr = this.readSettingAsString(PwmSetting.LDAP_USERNAME_ATTRIBUTE);
        final String ldapNamingAttribute = this.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
        return configUsernameAttr != null && configUsernameAttr.length() > 0 ? configUsernameAttr : ldapNamingAttribute;
    }
}
