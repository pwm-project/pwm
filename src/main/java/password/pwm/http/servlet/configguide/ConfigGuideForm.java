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

import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingTemplate;
import password.pwm.util.logging.PwmLogger;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ConfigGuideForm {

    private static final PwmLogger LOGGER = PwmLogger.forClass(ConfigGuideForm.class);

    static Map<FormParameter,String> defaultForm(PwmSettingTemplate template) {
        final Map<FormParameter,String> defaultLdapForm = new HashMap<>();
        for (final FormParameter formParameter : FormParameter.values()) {
            defaultLdapForm.put(formParameter, "");
        }

        try {
            final String defaultLdapUrlString = PwmSetting.LDAP_SERVER_URLS.getPlaceholder(template);
            final URI uri = new URI(defaultLdapUrlString);

            defaultLdapForm.put(FormParameter.PARAM_LDAP_PORT, String.valueOf(uri.getPort()));
            defaultLdapForm.put(FormParameter.PARAM_LDAP_SECURE, "ldaps".equalsIgnoreCase(uri.getScheme()) ? "true" : "false");
            defaultLdapForm.put(FormParameter.PARAM_CR_STORAGE_PREF, (String) PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE.getDefaultValue(template).toNativeObject());

        } catch (Exception e) {
            LOGGER.error("error building static form values using default configuration: " + e.getMessage(),e);
        }

        return Collections.unmodifiableMap(defaultLdapForm);
    }

    public static Map<FormParameter,String> placeholderForm(PwmSettingTemplate template) {
        final Map<FormParameter,String> placeholderForm = new HashMap<>();
        placeholderForm.putAll(defaultForm(template));

        try {
            final String defaultLdapUrlString = PwmSetting.LDAP_SERVER_URLS.getPlaceholder(template);
            final URI uri = new URI(defaultLdapUrlString);

            placeholderForm.put(FormParameter.PARAM_LDAP_HOST, uri.getHost());
            placeholderForm.put(FormParameter.PARAM_LDAP_PORT, String.valueOf(uri.getPort()));
            placeholderForm.put(FormParameter.PARAM_LDAP_SECURE, "ldaps".equalsIgnoreCase(uri.getScheme()) ? "true" : "false");

            placeholderForm.put(FormParameter.PARAM_LDAP_PROXY_DN, PwmSetting.LDAP_PROXY_USER_DN.getPlaceholder(template));
            placeholderForm.put(FormParameter.PARAM_LDAP_PROXY_PW, "");

            placeholderForm.put(FormParameter.PARAM_LDAP_CONTEXT, PwmSetting.LDAP_CONTEXTLESS_ROOT.getPlaceholder(template));
            placeholderForm.put(FormParameter.PARAM_LDAP_TEST_USER, PwmSetting.LDAP_TEST_USER_DN.getPlaceholder(template));
            placeholderForm.put(FormParameter.PARAM_LDAP_ADMIN_GROUP, PwmSetting.LDAP_TEST_USER_DN.getPlaceholder(template));

            placeholderForm.put(FormParameter.PARAM_CR_STORAGE_PREF, (String) PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE.getDefaultValue(template).toNativeObject());

        } catch (Exception e) {
            LOGGER.error("error building static form values using default configuration: " + e.getMessage(),e);
        }

        return Collections.unmodifiableMap(placeholderForm);
    }

    public enum FormParameter {
        PARAM_TEMPLATE_NAME,

        PARAM_APP_SITEURL,

        PARAM_LDAP_HOST,
        PARAM_LDAP_PORT,
        PARAM_LDAP_SECURE,
        PARAM_LDAP_PROXY_DN,
        PARAM_LDAP_PROXY_PW,

        PARAM_LDAP_CONTEXT,
        PARAM_LDAP_TEST_USER,
        PARAM_LDAP_ADMIN_GROUP,

        PARAM_CR_STORAGE_PREF,

        PARAM_DB_CLASSNAME,
        PARAM_DB_CONNECT_URL,
        PARAM_DB_USERNAME,
        PARAM_DB_PASSWORD,

        PARAM_CONFIG_PASSWORD,
        PARAM_CONFIG_PASSWORD_VERIFY,
    }

    public enum Cr_Storage_Pref {
        DB,
        LDAP,
        LOCALDB,
    }
}
