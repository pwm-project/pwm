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

package password.pwm.health;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.i18n.Admin;
import password.pwm.i18n.LocaleHelper;
import password.pwm.servlet.NewUserServlet;
import password.pwm.util.PwmLogger;
import password.pwm.util.operations.PasswordUtility;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ConfigurationChecker implements HealthChecker {
    private static final String TOPIC = "Configuration";
    private static final PwmLogger LOGGER = PwmLogger.getLogger(HealthChecker.class);

    public List<HealthRecord> doHealthCheck(final PwmApplication pwmApplication) {
        if (pwmApplication.getConfig() == null) {
            final HealthRecord hr = new HealthRecord(HealthStatus.WARN, TOPIC, "Unable to read configuration");
            return Collections.singletonList(hr);
        }

        final Configuration config = pwmApplication.getConfig();
        final List<HealthRecord> records = new ArrayList<HealthRecord>();

        if (config.readSettingAsBoolean(PwmSetting.HIDE_CONFIGURATION_HEALTH_WARNINGS)) {
            return records;
        }

        if (pwmApplication.getApplicationMode() == PwmApplication.MODE.CONFIGURATION) {
            records.add(new HealthRecord(HealthStatus.CONFIG, TOPIC, localizedString(pwmApplication,"Health_Config_ConfigMode")));
        }

        if (PwmConstants.UNCONFIGURED_URL_VALUE.equals(pwmApplication.getSiteURL())) {
            final String value = PwmSetting.PWM_URL.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE)
                    + " -> " + PwmSetting.PWM_URL.getLabel(PwmConstants.DEFAULT_LOCALE);

            records.add(new HealthRecord(HealthStatus.WARN, TOPIC, localizedString(pwmApplication,"Health_Config_NoSiteURL",value)));
        }

        if (!config.readSettingAsBoolean(PwmSetting.REQUIRE_HTTPS)) {
            final String value = PwmSetting.REQUIRE_HTTPS.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE)
                    + " -> " + PwmSetting.REQUIRE_HTTPS.getLabel(PwmConstants.DEFAULT_LOCALE);
            records.add(new HealthRecord(HealthStatus.CONFIG, TOPIC, localizedString(pwmApplication,"Health_Config_RequireHttps",value)));
        }

        if (config.readSettingAsBoolean(PwmSetting.LDAP_PROMISCUOUS_SSL)) {
            final String value = PwmSetting.LDAP_PROMISCUOUS_SSL.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE)
                    + " -> " + PwmSetting.LDAP_PROMISCUOUS_SSL.getLabel(PwmConstants.DEFAULT_LOCALE);
            records.add(new HealthRecord(HealthStatus.CONFIG, TOPIC, localizedString(pwmApplication,"Health_Config_PromiscuousLDAP",value)));
        }

        if (config.readSettingAsBoolean(PwmSetting.DISPLAY_SHOW_DETAILED_ERRORS)) {
            final String value = PwmSetting.DISPLAY_SHOW_DETAILED_ERRORS.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE)
                    + " -> " + PwmSetting.DISPLAY_SHOW_DETAILED_ERRORS.getLabel(PwmConstants.DEFAULT_LOCALE);
            records.add(new HealthRecord(HealthStatus.CONFIG, TOPIC, localizedString(pwmApplication, "Health_Config_ShowDetailedErrors", value)));
        }

        if (config.readSettingAsString(PwmSetting.LDAP_TEST_USER_DN) == null || config.readSettingAsString(PwmSetting.LDAP_TEST_USER_DN).length() < 1 ) {
            final String value = PwmSetting.LDAP_TEST_USER_DN.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE)
                    + " -> " + PwmSetting.LDAP_TEST_USER_DN.getLabel(PwmConstants.DEFAULT_LOCALE);
            records.add(new HealthRecord(HealthStatus.CONFIG, TOPIC, localizedString(pwmApplication, "Health_Config_AddTestUser", value)));
        }

        {
            final List<String> ldapServerURLs = config.readSettingAsStringArray(PwmSetting.LDAP_SERVER_URLS);
            if (ldapServerURLs != null && !ldapServerURLs.isEmpty()) {
                for (final String urlStringValue : ldapServerURLs) {
                    try {
                        final URI url = new URI(urlStringValue);
                        final boolean secure = "ldaps".equalsIgnoreCase(url.getScheme());
                        if (!secure) {
                            final String value = PwmSetting.LDAP_SERVER_URLS.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE)
                                    + " -> " + PwmSetting.LDAP_SERVER_URLS.getLabel(PwmConstants.DEFAULT_LOCALE);
                            records.add(new HealthRecord(HealthStatus.CONFIG, TOPIC, localizedString(pwmApplication, "Health_Config_LDAPUnsecure", value, urlStringValue)));
                        }
                    } catch (URISyntaxException  e) {
                        final String value = PwmSetting.LDAP_SERVER_URLS.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE)
                                + " -> " + PwmSetting.LDAP_SERVER_URLS.getLabel(PwmConstants.DEFAULT_LOCALE);
                        records.add(new HealthRecord(HealthStatus.CONFIG, TOPIC, localizedString(pwmApplication, "Health_Config_LDAPParseError", value, urlStringValue)));
                    }
                }
            }
        }

        for (final PwmSetting setting : PwmSetting.values()) {
            if (PwmSettingSyntax.PASSWORD == setting.getSyntax() && !config.isDefaultValue(setting)) {
                final String passwordValue = config.readSettingAsString(setting);
                final int strength = PasswordUtility.checkPasswordStrength(config, passwordValue);
                if (strength < 50) {
                    final String value = setting.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE)
                            + " -> " + setting.getLabel(PwmConstants.DEFAULT_LOCALE);
                    records.add(new HealthRecord(HealthStatus.CONFIG, TOPIC, localizedString(pwmApplication, "Health_Config_WeakPassword", value, String.valueOf(strength))));
                }
            }
        }

        {
            final String novellUserAppURL = config.readSettingAsString(PwmSetting.EDIRECTORY_PWD_MGT_WEBSERVICE_URL);
            if (novellUserAppURL != null && novellUserAppURL.length() > 0) {
                try {
                    final URL url = new URL(novellUserAppURL);
                    final boolean secure = "https".equalsIgnoreCase(url.getProtocol());
                    if (!secure) {
                        records.add(new HealthRecord(HealthStatus.CONFIG, TOPIC, "UserApp Password SOAP Service URL is not secure (https)"));
                    }
                } catch (MalformedURLException e) {
                    LOGGER.debug("error parsing Novell PwdMgt Web Service URL: " + e.getMessage());
                    records.add(new HealthRecord(HealthStatus.CONFIG, TOPIC, "error parsing Novell PwdMgt Web Service URL: " + e.getMessage()));
                }
            }
        }

        if (config.readSettingAsBoolean(PwmSetting.FORGOTTEN_PASSWORD_ENABLE)) {
            if (!config.readSettingAsBoolean(PwmSetting.CHALLENGE_REQUIRE_RESPONSES)) {
                if (config.readSettingAsTokenSendMethod(PwmSetting.CHALLENGE_TOKEN_SEND_METHOD) == PwmSetting.MessageSendMethod.NONE) {
                    final Collection<String> formSettings = config.readSettingAsStringArray(PwmSetting.CHALLENGE_REQUIRED_ATTRIBUTES);
                    if (formSettings == null || formSettings.isEmpty()) {
                        records.add(new HealthRecord(HealthStatus.CONFIG, TOPIC, localizedString(pwmApplication,"Health_Config_NoRecoveryEnabled")));
                    }
                }
            }
        }

        //if (pwmApplication.getConfigReader().modifiedSincePWMSave()) {
        //    records.add(new HealthRecord(HealthStatus.CAUTION, TOPIC, "Configuration file has been modified outside of PWM.  Please edit and save the configuration using the ConfigManager to be sure all settings are valid."));
        //}

        boolean hasDbConfiguration = true;
        {
            if (config.readSettingAsString(PwmSetting.DATABASE_CLASS) == null || config.readSettingAsString(PwmSetting.DATABASE_CLASS).length() < 1) {
                hasDbConfiguration = false;
            }
            if (config.readSettingAsString(PwmSetting.DATABASE_URL) == null || config.readSettingAsString(PwmSetting.DATABASE_URL).length() < 1) {
                hasDbConfiguration = false;
            }
            if (config.readSettingAsString(PwmSetting.DATABASE_USERNAME) == null || config.readSettingAsString(PwmSetting.DATABASE_USERNAME).length() < 1) {
                hasDbConfiguration = false;
            }
            if (config.readSettingAsString(PwmSetting.DATABASE_PASSWORD) == null || config.readSettingAsString(PwmSetting.DATABASE_PASSWORD).length() < 1) {
                hasDbConfiguration = false;
            }
        }

        if (!hasDbConfiguration) {
            if (config.shouldHaveDbConfigured()) {
                records.add(new HealthRecord(HealthStatus.CONFIG, TOPIC, localizedString(pwmApplication,"Health_Config_MissingDB")));
            }
        }

        final boolean hasResponseAttribute = config.readSettingAsString(PwmSetting.CHALLENGE_USER_ATTRIBUTE) != null && config.readSettingAsString(PwmSetting.CHALLENGE_USER_ATTRIBUTE).length() > 0;
        if (!hasResponseAttribute) {
            for (final PwmSetting loopSetting : new PwmSetting[] {PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE, PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE}) {
                if (config.getResponseStorageLocations(loopSetting).contains(Configuration.STORAGE_METHOD.LDAP)) {
                    final String value1 = loopSetting.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE)
                            + " -> " + loopSetting.getLabel(PwmConstants.DEFAULT_LOCALE);
                    final String value2 = PwmSetting.CHALLENGE_USER_ATTRIBUTE.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE)
                            + " -> " + PwmSetting.CHALLENGE_USER_ATTRIBUTE.getLabel(PwmConstants.DEFAULT_LOCALE);
                    records.add(new HealthRecord(HealthStatus.CONFIG, TOPIC, localizedString(pwmApplication,"Health_Config_MissingLDAP",value1,value2)));
                }
            }
        }

        {
            final List<HealthRecord> healthRecords = NewUserServlet.checkConfiguration(config,PwmConstants.DEFAULT_LOCALE);
            if (healthRecords != null && !healthRecords.isEmpty()) {
                records.addAll(healthRecords);
            }
        }

        return records;
    }

    private String localizedString(final PwmApplication pwmApplication, final String key, final String... values) {
        return LocaleHelper.getLocalizedMessage(null,key,pwmApplication.getConfig(),Admin.class,values);
    }
}
