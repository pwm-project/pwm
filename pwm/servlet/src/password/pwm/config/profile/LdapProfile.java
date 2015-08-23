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

package password.pwm.config.profile;

import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmApplication;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.StoredValue;
import password.pwm.config.UserPermission;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.StringUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LdapProfile extends AbstractProfile implements Profile {
    protected LdapProfile(String identifier, Map<PwmSetting, StoredValue> storedValueMap) {
        super(identifier, storedValueMap);
    }

    public static LdapProfile makeFromStoredConfiguration(final StoredConfigurationImpl storedConfiguration, final String profileID) {
        final Map<PwmSetting,StoredValue> valueMap = new LinkedHashMap<>();
        for (final PwmSetting setting : PwmSettingCategory.LDAP_PROFILE.getSettings()) {
            final StoredValue value = storedConfiguration.readSetting(setting, profileID);
            valueMap.put(setting, value);
        }
        return new LdapProfile(profileID, valueMap);

    }

    public Map<String, String> getLoginContexts() {
        final List<String> values = readSettingAsStringArray(PwmSetting.LDAP_LOGIN_CONTEXTS);
        return StringUtil.convertStringListToNameValuePair(values, ":::");
    }

    @Override
    public String getDisplayName(final Locale locale) {
        final String displayName = readSettingAsLocalizedString(PwmSetting.LDAP_PROFILE_DISPLAY_NAME,locale);
        return displayName == null || displayName.length() < 1 ? identifier : displayName;
    }

    public String getUsernameAttribute() {
        final String configUsernameAttr = this.readSettingAsString(PwmSetting.LDAP_USERNAME_ATTRIBUTE);
        final String ldapNamingAttribute = this.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
        return configUsernameAttr != null && configUsernameAttr.length() > 0 ? configUsernameAttr : ldapNamingAttribute;
    }

    public ChaiProvider getProxyChaiProvider(final PwmApplication pwmApplication) throws PwmUnrecoverableException {
        return pwmApplication.getProxyChaiProvider(this.getIdentifier());
    }

    @Override
    public ProfileType profileType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<UserPermission> getPermissionMatches() {
        throw new UnsupportedOperationException();
    }
}
