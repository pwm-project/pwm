/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2013 The PWM Project
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

import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LdapProfile {
    final protected static List<PwmSetting> LDAP_SETTINGS = Collections.unmodifiableList(PwmSetting.getSettings(PwmSetting.Category.LDAP));

    final protected String identifier;
    final protected Map<PwmSetting,StoredValue> storedValueMap;

    protected LdapProfile(String identifier, Map<PwmSetting, StoredValue> storedValueMap) {
        this.identifier = identifier;
        this.storedValueMap = storedValueMap;
    }

    public String getIdentifier() {
        return identifier;
    }

    static LdapProfile makeFromStoredConfiguration(final StoredConfiguration storedConfiguration, final String identifier) {
        final Map<PwmSetting,StoredValue> valueMap = new LinkedHashMap<PwmSetting, StoredValue>();
        for (final PwmSetting setting : LDAP_SETTINGS) {
            final StoredValue value = storedConfiguration.readSetting(setting, identifier);
            valueMap.put(setting, value);
        }
        return new LdapProfile(identifier, valueMap);

    }

    public String readSettingAsString(final PwmSetting setting) {
         return Configuration.Converter.valueToString(storedValueMap.get(setting));
    }

    public List<String> readSettingAsStringArray(final PwmSetting setting) {
        return Configuration.Converter.valueToStringArray(storedValueMap.get(setting));
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


    /*
    LDAP_SERVER_URLS(
            "ldap.serverUrls", PwmSettingSyntax.STRING_ARRAY, Category.LDAP),
    LDAP_SERVER_CERTS(
            "ldap.serverCerts", PwmSettingSyntax.X509CERT, Category.LDAP),
    LDAP_PROXY_USER_DN(
            "ldap.proxy.username", PwmSettingSyntax.STRING, Category.LDAP),
    LDAP_PROXY_USER_PASSWORD(
            "ldap.proxy.password", PwmSettingSyntax.PASSWORD, Category.LDAP),
    LDAP_CONTEXTLESS_ROOT(
            "ldap.rootContexts", PwmSettingSyntax.STRING_ARRAY, Category.LDAP),
    LDAP_TEST_USER_DN(
            "ldap.testuser.username", PwmSettingSyntax.STRING, Category.LDAP),
    QUERY_MATCH_PWM_ADMIN(
            "pwmAdmin.queryMatch", PwmSettingSyntax.STRING, Category.LDAP),
    LDAP_USERNAME_SEARCH_FILTER(
            "ldap.usernameSearchFilter", PwmSettingSyntax.STRING, Category.LDAP),
    AUTO_ADD_OBJECT_CLASSES(
            "ldap.addObjectClasses", PwmSettingSyntax.STRING_ARRAY, Category.LDAP),
    LDAP_NAMING_ATTRIBUTE(
            "ldap.namingAttribute", PwmSettingSyntax.STRING, Category.LDAP),
    LDAP_CHAI_SETTINGS(
            "ldapChaiSettings", PwmSettingSyntax.STRING_ARRAY, Category.LDAP),
    LDAP_USERNAME_ATTRIBUTE(
            "ldap.username.attr", PwmSettingSyntax.STRING, Category.LDAP),
    LDAP_FOLLOW_REFERRALS(
            "ldap.followReferrals", PwmSettingSyntax.BOOLEAN, Category.LDAP),
    LDAP_LOGIN_CONTEXTS(
            "ldap.selectableContexts", PwmSettingSyntax.STRING_ARRAY, Category.LDAP),
*/
}
