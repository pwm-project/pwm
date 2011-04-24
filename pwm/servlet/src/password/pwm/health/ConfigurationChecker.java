/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

import password.pwm.ContextManager;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.util.PwmLogger;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

public class ConfigurationChecker implements HealthChecker {
    private static final String TOPIC = "Configuration Checker";
    private static final PwmLogger LOGGER = PwmLogger.getLogger(HealthChecker.class);

    public List<HealthRecord> doHealthCheck(final ContextManager contextManager) {
        if (contextManager.getConfig() == null) {
            final HealthRecord hr = new HealthRecord(HealthStatus.WARN, TOPIC, "Unable to read configuration");
            return Collections.singletonList(hr);
        }

        final Configuration config = contextManager.getConfig();

        final List<HealthRecord> records = new ArrayList<HealthRecord>();
        if (config.readSettingAsBoolean(PwmSetting.LDAP_PROMISCUOUS_SSL)) {
            records.add(new HealthRecord(HealthStatus.CAUTION, TOPIC, "Promiscuous LDAP SSL connection is being used"));
        }

        {
            final List<String> ldapServerURLs = config.readStringArraySetting(PwmSetting.LDAP_SERVER_URLS);
            boolean foundUnsecure = false;
            if (ldapServerURLs != null && !ldapServerURLs.isEmpty()) {
                for (final String urlStringValue : ldapServerURLs) {
                    try {
                        final URI url = new URI(urlStringValue);
                        final boolean secure = "ldaps".equalsIgnoreCase(url.getScheme());
                        if (!secure) {
                            foundUnsecure = true;
                        }
                    } catch (URISyntaxException  e) {
                        LOGGER.debug("error parsing ldapServerURLs: " + e.getMessage());
                        records.add(new HealthRecord(HealthStatus.CAUTION, TOPIC, "error parsing ldapServerURLs: " + e.getMessage()));
                    }
                }
            }
            if (foundUnsecure) {
                records.add(new HealthRecord(HealthStatus.CAUTION, TOPIC, "One or more LDAP URLs are configured using non-secure connections."));
            }
        }

        {
            final String novellUserAppURL = config.readSettingAsString(PwmSetting.EDIRECTORY_PWD_MGT_WEBSERVICE_URL);
            if (novellUserAppURL != null && novellUserAppURL.length() > 0) {
                try {
                    final URL url = new URL(novellUserAppURL);
                    final boolean secure = "https".equalsIgnoreCase(url.getProtocol());
                    if (!secure) {
                        records.add(new HealthRecord(HealthStatus.CAUTION, TOPIC, "UserApp Password SOAP Service URL is not secure (should be https)"));
                    }
                } catch (MalformedURLException e) {
                    LOGGER.debug("error parsing Novell PwdMgt Web Service URL: " + e.getMessage());
                    records.add(new HealthRecord(HealthStatus.CAUTION, TOPIC, "error parsing Novell PwdMgt Web Service URL: " + e.getMessage()));
                }
            }
        }

        if (config.readSettingAsBoolean(PwmSetting.FORGOTTEN_PASSWORD_ENABLE)) {
            if (!config.readSettingAsBoolean(PwmSetting.CHALLENGE_REQUIRE_RESPONSES)) {
                if (!config.readSettingAsBoolean(PwmSetting.CHALLENGE_TOKEN_ENABLE)) {
                    final Collection<String> configValues = config.readFormSetting(PwmSetting.CHALLENGE_REQUIRED_ATTRIBUTES, null);
                    final Map<String, FormConfiguration> formSettings = Configuration.convertMapToFormConfiguration(configValues);
                    if (formSettings.isEmpty()) {
                        records.add(new HealthRecord(HealthStatus.WARN, TOPIC, "No forgotten password recovery options are enabled"));
                    }
                }
            }
        }

        if (contextManager.getConfigReader().modifiedSincePWMSave()) {
            records.add(new HealthRecord(HealthStatus.CAUTION, TOPIC, "Configuration file has been modified outside of PWM.  Please edit and save the configuration using the ConfigManager to be sure all settings are valid."));
        }

        if (records.isEmpty()) {
            records.add(new HealthRecord(HealthStatus.GOOD, TOPIC, "No configuration issues detected"));
        }

        return records;
    }
}
