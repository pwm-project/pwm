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

package password.pwm.http.servlet.configguide;

import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingTemplate;
import password.pwm.config.StoredValue;
import password.pwm.config.UserPermission;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.config.value.*;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.ConfigGuideBean;
import password.pwm.util.PasswordData;
import password.pwm.util.logging.PwmLogger;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigGuideForm {

    private static final PwmLogger LOGGER = PwmLogger.forClass(ConfigGuideForm.class);

    public static Map<FormParameter,String> defaultForm() {
        final Map<FormParameter,String> defaultLdapForm = new HashMap<>();
        for (final FormParameter formParameter : FormParameter.values()) {
            defaultLdapForm.put(formParameter, "");
        }

        defaultLdapForm.put(FormParameter.PARAM_LDAP_PORT,"636");
        defaultLdapForm.put(FormParameter.PARAM_LDAP_SECURE,"true");

        return Collections.unmodifiableMap(defaultLdapForm);
    }


    public enum FormParameter {
        PARAM_TEMPLATE_LDAP(PwmSetting.TEMPLATE_LDAP),
        PARAM_TEMPLATE_STORAGE(PwmSetting.TEMPLATE_STORAGE),

        PARAM_APP_SITEURL(PwmSetting.PWM_SITE_URL),

        PARAM_LDAP_HOST(null),
        PARAM_LDAP_PORT(null),
        PARAM_LDAP_SECURE(null),
        PARAM_LDAP_PROXY_DN(PwmSetting.LDAP_PROXY_USER_DN),
        PARAM_LDAP_PROXY_PW(PwmSetting.LDAP_PROXY_USER_PASSWORD),

        PARAM_LDAP_CONTEXT(PwmSetting.LDAP_CONTEXTLESS_ROOT),
        PARAM_LDAP_TEST_USER(PwmSetting.LDAP_TEST_USER_DN),
        PARAM_LDAP_ADMIN_GROUP(PwmSetting.QUERY_MATCH_PWM_ADMIN),

        PARAM_DB_CLASSNAME(PwmSetting.DATABASE_CLASS),
        PARAM_DB_CONNECT_URL(PwmSetting.DATABASE_URL),
        PARAM_DB_USERNAME(PwmSetting.DATABASE_USERNAME),
        PARAM_DB_PASSWORD(PwmSetting.DATABASE_PASSWORD),
        PARAM_DB_VENDOR(PwmSetting.DB_VENDOR_TEMPLATE),

        PARAM_CONFIG_PASSWORD(null),

        ;

        private final PwmSetting pwmSetting;

        FormParameter(PwmSetting pwmSetting) {
            this.pwmSetting = pwmSetting;
        }

        public PwmSetting getPwmSetting() {
            return pwmSetting;
        }
    }

    public static StoredConfigurationImpl generateStoredConfig(
            final ConfigGuideBean configGuideBean
    )
            throws PwmUnrecoverableException
    {
        final String LDAP_PROFILE_NAME = "default";

        final Map<ConfigGuideForm.FormParameter, String> formData = configGuideBean.getFormData();
        final StoredConfigurationImpl storedConfiguration = StoredConfigurationImpl.newStoredConfiguration();

        // templates
        storedConfiguration.writeSetting(PwmSetting.TEMPLATE_LDAP, null, new StringValue(
                PwmSettingTemplate.templateForString(formData.get(FormParameter.PARAM_TEMPLATE_LDAP), PwmSettingTemplate.Type.LDAP_VENDOR).toString()
        ), null);
        storedConfiguration.writeSetting(PwmSetting.TEMPLATE_STORAGE, null, new StringValue(
                PwmSettingTemplate.templateForString(formData.get(FormParameter.PARAM_TEMPLATE_STORAGE), PwmSettingTemplate.Type.STORAGE).toString()
        ), null);
        storedConfiguration.writeSetting(PwmSetting.DB_VENDOR_TEMPLATE, null, new StringValue(
                PwmSettingTemplate.templateForString(formData.get(FormParameter.PARAM_DB_VENDOR), PwmSettingTemplate.Type.DB_VENDOR).toString()
        ), null);

        // establish a default ldap profile
        storedConfiguration.writeSetting(PwmSetting.LDAP_PROFILE_LIST, null, new StringArrayValue(Collections.singletonList(LDAP_PROFILE_NAME)), null);


        {
            final String newLdapURI = figureLdapUrlFromFormConfig(formData);
            final StringArrayValue newValue = new StringArrayValue(Collections.singletonList(newLdapURI));
            storedConfiguration.writeSetting(PwmSetting.LDAP_SERVER_URLS, LDAP_PROFILE_NAME, newValue, null);
        }

        if (configGuideBean.isUseConfiguredCerts()) {
            final StoredValue newStoredValue = new X509CertificateValue(configGuideBean.getLdapCertificates());
            storedConfiguration.writeSetting(PwmSetting.LDAP_SERVER_CERTS, LDAP_PROFILE_NAME, newStoredValue, null);
        }

        { // proxy/admin account
            final String ldapAdminDN = formData.get(ConfigGuideForm.FormParameter.PARAM_LDAP_PROXY_DN);
            final String ldapAdminPW = formData.get(ConfigGuideForm.FormParameter.PARAM_LDAP_PROXY_PW);
            storedConfiguration.writeSetting(PwmSetting.LDAP_PROXY_USER_DN, LDAP_PROFILE_NAME, new StringValue(ldapAdminDN), null);
            final PasswordValue passwordValue = new PasswordValue(PasswordData.forStringValue(ldapAdminPW));
            storedConfiguration.writeSetting(PwmSetting.LDAP_PROXY_USER_PASSWORD, LDAP_PROFILE_NAME, passwordValue, null);
        }

        storedConfiguration.writeSetting(PwmSetting.LDAP_CONTEXTLESS_ROOT, LDAP_PROFILE_NAME, new StringArrayValue(Collections.singletonList(formData.get(ConfigGuideForm.FormParameter.PARAM_LDAP_CONTEXT))), null);

        {
            final String ldapContext = formData.get(ConfigGuideForm.FormParameter.PARAM_LDAP_CONTEXT);
            storedConfiguration.writeSetting(PwmSetting.LDAP_CONTEXTLESS_ROOT, LDAP_PROFILE_NAME, new StringArrayValue(Collections.singletonList(ldapContext)), null);
        }

        {
            final String ldapTestUserDN = formData.get(ConfigGuideForm.FormParameter.PARAM_LDAP_TEST_USER);
            storedConfiguration.writeSetting(PwmSetting.LDAP_TEST_USER_DN, LDAP_PROFILE_NAME, new StringValue(ldapTestUserDN), null);
        }

        {  // set admin query
            final String groupDN = formData.get(ConfigGuideForm.FormParameter.PARAM_LDAP_ADMIN_GROUP);
            final List<UserPermission> userPermissions = Collections.singletonList(new UserPermission(UserPermission.Type.ldapGroup, null, null, groupDN));
            storedConfiguration.writeSetting(PwmSetting.QUERY_MATCH_PWM_ADMIN, new UserPermissionValue(userPermissions), null);
        }

        {  // database

            final String dbClass = formData.get(ConfigGuideForm.FormParameter.PARAM_DB_CLASSNAME);
            storedConfiguration.writeSetting(PwmSetting.DATABASE_CLASS, null, new StringValue(dbClass), null);

            final String dbUrl = formData.get(ConfigGuideForm.FormParameter.PARAM_DB_CONNECT_URL);
            storedConfiguration.writeSetting(PwmSetting.DATABASE_URL, null, new StringValue(dbUrl), null);

            final String dbUser = formData.get(ConfigGuideForm.FormParameter.PARAM_DB_USERNAME);
            storedConfiguration.writeSetting(PwmSetting.DATABASE_USERNAME, null, new StringValue(dbUser), null);

            final String dbPassword = formData.get(ConfigGuideForm.FormParameter.PARAM_DB_PASSWORD);
            final PasswordValue passwordValue = new PasswordValue(PasswordData.forStringValue(dbPassword));
            storedConfiguration.writeSetting(PwmSetting.DATABASE_PASSWORD, null, passwordValue, null);

            final FileValue jdbcDriver = configGuideBean.getDatabaseDriver();
            if (jdbcDriver != null) {
                storedConfiguration.writeSetting(PwmSetting.DATABASE_JDBC_DRIVER, null, jdbcDriver, null);
            }
        }

        // set site url
        storedConfiguration.writeSetting(PwmSetting.PWM_SITE_URL, new StringValue(formData.get(ConfigGuideForm.FormParameter.PARAM_APP_SITEURL)), null);

        return storedConfiguration;
    }

    static String figureLdapUrlFromFormConfig(final Map<ConfigGuideForm.FormParameter, String> ldapForm) {
        final String ldapServerIP = ldapForm.get(ConfigGuideForm.FormParameter.PARAM_LDAP_HOST);
        final String ldapServerPort = ldapForm.get(ConfigGuideForm.FormParameter.PARAM_LDAP_PORT);
        final boolean ldapServerSecure = "true".equalsIgnoreCase(ldapForm.get(ConfigGuideForm.FormParameter.PARAM_LDAP_SECURE));

        return "ldap" + (ldapServerSecure ? "s" : "") +  "://" + ldapServerIP + ":" + ldapServerPort;
    }

    public static String figureLdapHostnameExample(final ConfigGuideBean configGuideBean)
    {
        try {
            final StoredConfigurationImpl storedConfiguration = generateStoredConfig(configGuideBean);
            final String uriString = PwmSetting.LDAP_SERVER_URLS.getExample(storedConfiguration.getTemplateSet());
            final URI uri = new URI(uriString);
            return uri.getHost();
        } catch (Exception e) {
            LOGGER.error("error calculating ldap hostname example: " + e.getMessage());
        }
        return "ldap.example.com";
    }
}
