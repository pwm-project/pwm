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

import java.security.cert.X509Certificate;
import java.util.*;

public class LdapProfile implements Profile {
    final protected static List<PwmSetting> LDAP_SETTINGS = Collections.unmodifiableList(PwmSetting.getSettings(PwmSetting.Category.LDAP_PROFILE));

    final protected String identifier;
    final protected Map<PwmSetting,StoredValue> storedValueMap;

    protected LdapProfile(String identifier, Map<PwmSetting, StoredValue> storedValueMap) {
        this.identifier = identifier;
        this.storedValueMap = storedValueMap;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    static LdapProfile makeFromStoredConfiguration(final StoredConfiguration storedConfiguration, final String identifier) {
        final Map<PwmSetting,StoredValue> valueMap = new LinkedHashMap<PwmSetting, StoredValue>();
        for (final PwmSetting setting : LDAP_SETTINGS) {
            final StoredValue value = storedConfiguration.readSetting(setting, PwmConstants.DEFAULT_LDAP_PROFILE.equals(identifier) ? "" : identifier);
            valueMap.put(setting, value);
        }
        return new LdapProfile(identifier, valueMap);

    }

    public String readSettingAsString(final PwmSetting setting) {
         return Configuration.JavaTypeConverter.valueToString(storedValueMap.get(setting));
    }

    public List<String> readSettingAsStringArray(final PwmSetting setting) {
        return Configuration.JavaTypeConverter.valueToStringArray(storedValueMap.get(setting));
    }

    public X509Certificate[] readSettingAsCertificate(final PwmSetting setting) {
        if (PwmSettingSyntax.X509CERT != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read X509CERT value for setting: " + setting.toString());
        }
        if (storedValueMap.containsKey(setting)) {
            return (X509Certificate[])storedValueMap.get(setting).toNativeObject();
        }
        return new X509Certificate[0];
    }

    public boolean readSettingAsBoolean(final PwmSetting setting) {
        return Configuration.JavaTypeConverter.valueToBoolean(storedValueMap.get(setting));
    }

    public Map<String, String> getLoginContexts() {
        final List<String> values = readSettingAsStringArray(PwmSetting.LDAP_LOGIN_CONTEXTS);
        return Configuration.convertStringListToNameValuePair(values, ":::");
    }

    public String readSettingAsLocalizedString(final PwmSetting setting, final Locale locale) {
        return Configuration.JavaTypeConverter.valueToLocalizedString(storedValueMap.get(setting), locale);
    }

    @Override
    public String getDisplayName(final Locale locale) {
        final String displayName = readSettingAsLocalizedString(PwmSetting.LDAP_PROFILE_DISPLAY_NAME,locale);
        final String returnDisplayName = displayName == null || displayName.length() < 1 ? identifier : displayName;
        return PwmConstants.DEFAULT_LDAP_PROFILE.equals(returnDisplayName) ? "Default" : returnDisplayName;
    }

}
