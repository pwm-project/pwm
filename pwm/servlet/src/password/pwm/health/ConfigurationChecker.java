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
import password.pwm.config.PwmSetting;
import password.pwm.util.PwmLogger;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConfigurationChecker implements HealthChecker {
    private static final String TOPIC = "Configuration Checker";
    private static final PwmLogger LOGGER = PwmLogger.getLogger(HealthChecker.class);

    public List<HealthRecord> doHealthCheck(final ContextManager contextManager) {
        if (contextManager.getConfig() == null) {
            final HealthRecord hr = new HealthRecord(HealthRecord.HealthStatus.WARN, TOPIC, "Unable to read configuration");
            return Collections.singletonList(hr);
        }

        final List<HealthRecord> records = new ArrayList<HealthRecord>();
        if (contextManager.getConfig().readSettingAsBoolean(PwmSetting.LDAP_PROMISCUOUS_SSL)) {
            records.add(new HealthRecord(HealthRecord.HealthStatus.CAUTION, TOPIC, "Promiscuous LDAP SSL connection is being used"));
        }

        {
            final String novellUserAppURL = contextManager.getConfig().readSettingAsString(PwmSetting.EDIRECTORY_PWD_MGT_WEBSERVICE_URL);
            if (novellUserAppURL != null && novellUserAppURL.length() > 0) {
                try {
                    final URL url = new URL(novellUserAppURL);
                    final boolean secure = "https".equalsIgnoreCase(url.getProtocol());
                    if (!secure) {
                        records.add(new HealthRecord(HealthRecord.HealthStatus.CAUTION, TOPIC, "UserApp Password SOAP Service URL is not secure (should be https)"));
                    }
                } catch (MalformedURLException e) {
                    LOGGER.debug("error parsing Novell PwdMgt Web Service URL: " + e.getMessage());
                }
            }
        }

        if (contextManager.getConfigReader().modifiedSincePWMSave()) {
            records.add(new HealthRecord(HealthRecord.HealthStatus.CAUTION, TOPIC, "Configuration file has been modified outside of PWM.  Please edit and save the configuration using the ConfigManager to be sure all settings are valid."));
        }

        if (records.isEmpty()) {
            records.add(new HealthRecord(HealthRecord.HealthStatus.GOOD, TOPIC, "No configuration issues detected"));
        }

        return records;
    }
}
