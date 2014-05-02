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

package password.pwm.health;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.*;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.servlet.NewUserServlet;
import password.pwm.util.operations.PasswordUtility;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ConfigurationChecker implements HealthChecker {
    public List<HealthRecord> doHealthCheck(final PwmApplication pwmApplication)
    {
        if (pwmApplication.getConfig() == null) {
            return Collections.emptyList();
        }

        final Configuration config = pwmApplication.getConfig();
        final List<HealthRecord> records = new ArrayList<HealthRecord>();

        if (config.readSettingAsBoolean(PwmSetting.HIDE_CONFIGURATION_HEALTH_WARNINGS)) {
            return records;
        }

        if (pwmApplication.getApplicationMode() == PwmApplication.MODE.CONFIGURATION) {
            records.add(HealthRecord.forMessage(HealthMessage.Config_ConfigMode));
        }

        if (PwmConstants.UNCONFIGURED_URL_VALUE.equals(pwmApplication.getSiteURL())) {
            records.add(
                    HealthRecord.forMessage(HealthMessage.Config_NoSiteURL, settingToOutputText(PwmSetting.PWM_URL)));
        }

        if (!config.readSettingAsBoolean(PwmSetting.REQUIRE_HTTPS)) {
            records.add(HealthRecord.forMessage(HealthMessage.Config_RequireHttps,settingToOutputText(PwmSetting.REQUIRE_HTTPS)));
        }

        if (Boolean.parseBoolean(config.readAppProperty(AppProperty.LDAP_PROMISCUOUS_ENABLE))) {
            final String appPropertyKey = "AppProperty -> " + AppProperty.LDAP_PROMISCUOUS_ENABLE.getKey();
            records.add(HealthRecord.forMessage(HealthMessage.Config_PromiscuousLDAP, appPropertyKey));
        }

        if (config.readSettingAsBoolean(PwmSetting.DISPLAY_SHOW_DETAILED_ERRORS)) {
            records.add(HealthRecord.forMessage(HealthMessage.Config_ShowDetailedErrors,settingToOutputText(PwmSetting.DISPLAY_SHOW_DETAILED_ERRORS)));
        }

        for (final LdapProfile ldapProfile : config.getLdapProfiles().values()) {
            final String testUserDN = ldapProfile.readSettingAsString(PwmSetting.LDAP_TEST_USER_DN);
            if (testUserDN == null || testUserDN.length() < 1) {
                records.add(HealthRecord.forMessage(HealthMessage.Config_AddTestUser,settingToOutputText(PwmSetting.LDAP_TEST_USER_DN,ldapProfile)));
            }
        }

        {
            for (final LdapProfile ldapProfile : config.getLdapProfiles().values()) {
                final List<String> ldapServerURLs = ldapProfile.readSettingAsStringArray(PwmSetting.LDAP_SERVER_URLS);
                if (ldapServerURLs != null && !ldapServerURLs.isEmpty()) {
                    for (final String urlStringValue : ldapServerURLs) {
                        try {
                            final URI url = new URI(urlStringValue);
                            final boolean secure = "ldaps".equalsIgnoreCase(url.getScheme());
                            if (!secure) {
                                records.add(HealthRecord.forMessage(
                                        HealthMessage.Config_LDAPUnsecure,
                                        settingToOutputText(PwmSetting.LDAP_SERVER_URLS, ldapProfile)
                                ));
                            }
                        } catch (URISyntaxException e) {
                            records.add(HealthRecord.forMessage(HealthMessage.Config_ParseError,
                                    e.getMessage(),
                                    settingToOutputText(PwmSetting.LDAP_SERVER_URLS, ldapProfile),
                                    urlStringValue
                            ));
                        }
                    }
                }
            }
        }

        {
            for (final PwmSetting setting : PwmSetting.values()) {
                if (PwmSettingSyntax.PASSWORD == setting.getSyntax() && !config.isDefaultValue(
                        setting) && setting.getCategory().getType() != PwmSetting.Category.Type.PROFILE) {
                    final String passwordValue = config.readSettingAsString(setting);
                    final int strength = PasswordUtility.checkPasswordStrength(config, passwordValue);
                    if (strength < 50) {
                        records.add(HealthRecord.forMessage(HealthMessage.Config_WeakPassword,
                                settingToOutputText(setting, null), String.valueOf(strength)));
                    }
                }
            }
            for (final LdapProfile profile : config.getLdapProfiles().values()) {
                final String passwordValue = profile.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD);
                final int strength = PasswordUtility.checkPasswordStrength(config, passwordValue);
                if (strength < 50) {
                    records.add(HealthRecord.forMessage(HealthMessage.Config_WeakPassword,
                            settingToOutputText(PwmSetting.LDAP_PROXY_USER_PASSWORD, profile),
                            String.valueOf(strength)));
                }
            }
        }


        {
            final String novellUserAppURL = config.readSettingAsString(PwmSetting.EDIRECTORY_PWD_MGT_WEBSERVICE_URL);
            if (novellUserAppURL != null && novellUserAppURL.length() > 0) {
                try {
                    final URI url = new URI(novellUserAppURL);
                    final boolean secure = "https".equalsIgnoreCase(url.getScheme());
                    if (!secure) {
                        records.add(HealthRecord.forMessage(HealthMessage.Config_URLNotSecure,settingToOutputText(PwmSetting.EDIRECTORY_PWD_MGT_WEBSERVICE_URL)));
                    }
                } catch (URISyntaxException e) {
                    records.add(HealthRecord.forMessage(HealthMessage.Config_ParseError,
                            e.getMessage(),
                            settingToOutputText(PwmSetting.EDIRECTORY_PWD_MGT_WEBSERVICE_URL, null),
                            novellUserAppURL
                    ));
                }
            }
        }

        if (config.readSettingAsBoolean(PwmSetting.FORGOTTEN_PASSWORD_ENABLE)) {
            if (!config.readSettingAsBoolean(PwmSetting.CHALLENGE_REQUIRE_RESPONSES)) {
                if (config.readSettingAsTokenSendMethod(PwmSetting.CHALLENGE_TOKEN_SEND_METHOD) == MessageSendMethod.NONE) {
                    final Collection<FormConfiguration> formSettings = config.readSettingAsForm(PwmSetting.CHALLENGE_REQUIRED_ATTRIBUTES);
                    if (formSettings == null || formSettings.isEmpty()) {
                        records.add(HealthRecord.forMessage(HealthMessage.Config_NoRecoveryEnabled));
                    }
                }
            }
        }


        if (!config.hasDbConfigured()) {
            if (config.helper().shouldHaveDbConfigured()) {
                records.add(HealthRecord.forMessage(HealthMessage.Config_MissingDB));
            }
        }

        final boolean hasResponseAttribute = config.readSettingAsString(PwmSetting.CHALLENGE_USER_ATTRIBUTE) != null && config.readSettingAsString(PwmSetting.CHALLENGE_USER_ATTRIBUTE).length() > 0;
        if (!hasResponseAttribute) {
            for (final PwmSetting loopSetting : new PwmSetting[] {PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE, PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE}) {
                if (config.getResponseStorageLocations(loopSetting).contains(DataStorageMethod.LDAP)) {
                    records.add(HealthRecord.forMessage(HealthMessage.Config_MissingLDAPResponseAttr,
                            settingToOutputText(loopSetting),
                            settingToOutputText(PwmSetting.CHALLENGE_USER_ATTRIBUTE)
                            ));
                }
            }
        }

        {
            final List<HealthRecord> healthRecords = NewUserServlet.checkConfiguration(config,
                    PwmConstants.DEFAULT_LOCALE);
            if (healthRecords != null && !healthRecords.isEmpty()) {
                records.addAll(healthRecords);
            }
        }

        if (config.getResponseStorageLocations(PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE).contains(DataStorageMethod.LOCALDB)) {
            records.add(HealthRecord.forMessage(HealthMessage.Config_UsingLocalDBResponseStorage, settingToOutputText(PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE)));
        }

        return records;
    }

    public static String settingToOutputText(
            final PwmSetting setting
    ) {
        return settingToOutputText(setting,null);
    }

    public static String settingToOutputText(
            final PwmSetting setting,
            final Profile profile
    ) {
        return setting.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE)
                + " -> " + setting.getLabel(PwmConstants.DEFAULT_LOCALE) +
                (profile != null ? " (" + profile.getDisplayName(PwmConstants.DEFAULT_LOCALE) + ")" : "");
    }
}
