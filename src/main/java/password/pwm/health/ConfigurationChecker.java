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

package password.pwm.health;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.config.profile.Profile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Config;
import password.pwm.util.LocaleHelper;
import password.pwm.util.PasswordData;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.operations.PasswordUtility;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ConfigurationChecker implements HealthChecker {
    private static final PwmLogger LOGGER = PwmLogger.forClass(ConfigurationChecker.class);

    public List<HealthRecord> doHealthCheck(final PwmApplication pwmApplication) {
        if (pwmApplication.getConfig() == null) {
            return Collections.emptyList();
        }

        final Configuration config = pwmApplication.getConfig();

        final List<HealthRecord> records = new ArrayList<>();

        if (pwmApplication.getApplicationMode() == PwmApplication.MODE.CONFIGURATION) {
            records.add(HealthRecord.forMessage(HealthMessage.Config_ConfigMode));
        }

        if (config.readSettingAsBoolean(PwmSetting.NEWUSER_ENABLE)) {
            for (final NewUserProfile newUserProfile : config.getNewUserProfiles().values()) {
                try {
                    newUserProfile.getNewUserPasswordPolicy(pwmApplication, PwmConstants.DEFAULT_LOCALE);
                } catch (PwmUnrecoverableException e) {
                    records.add(new HealthRecord(HealthStatus.WARN,HealthTopic.Configuration,e.getMessage()));
                }
            }
        }

        records.addAll(doHealthCheck(config, PwmConstants.DEFAULT_LOCALE));

        return records;
    }

    public List<HealthRecord> doHealthCheck(final Configuration config, final Locale locale)
    {


        final List<HealthRecord> records = new ArrayList<>();

        if (config.readSettingAsBoolean(PwmSetting.HIDE_CONFIGURATION_HEALTH_WARNINGS)) {
            return records;
        }

        final String siteUrl = config.readSettingAsString(PwmSetting.PWM_SITE_URL);
        final String SEPARATOR = LocaleHelper.getLocalizedMessage(locale, Config.Display_SettingNavigationSeparator, null);
        try {
            if (siteUrl == null || siteUrl.isEmpty() || siteUrl.equals(
                    PwmSetting.PWM_SITE_URL.getDefaultValue(config.getTemplate()).toNativeObject())) {
                records.add(
                        HealthRecord.forMessage(HealthMessage.Config_NoSiteURL, settingToOutputText(PwmSetting.PWM_SITE_URL)));
            }
        } catch (PwmException e) {
            LOGGER.error(PwmConstants.HEALTH_SESSION_LABEL,"error while inspecting site URL setting: " + e.getMessage());
        }

        if (!config.readSettingAsBoolean(PwmSetting.REQUIRE_HTTPS)) {
            records.add(HealthRecord.forMessage(HealthMessage.Config_RequireHttps,settingToOutputText(PwmSetting.REQUIRE_HTTPS)));
        }

        if (config.readSettingAsBoolean(PwmSetting.LDAP_ENABLE_WIRE_TRACE)) {
            records.add(HealthRecord.forMessage(HealthMessage.Config_LDAPWireTrace,settingToOutputText(PwmSetting.LDAP_ENABLE_WIRE_TRACE)));
        }

        if (Boolean.parseBoolean(config.readAppProperty(AppProperty.LDAP_PROMISCUOUS_ENABLE))) {
            final String appPropertyKey = "AppProperty" +  SEPARATOR + AppProperty.LDAP_PROMISCUOUS_ENABLE.getKey();
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

        {
            for (final PwmSetting setting : PwmSetting.values()) {
                if (setting.getSyntax() == PwmSettingSyntax.PASSWORD) {
                    if (!setting.getCategory().hasProfiles()) {
                        if (!config.isDefaultValue(setting)) {
                            try {
                                final PasswordData passwordValue = config.readSettingAsPassword(setting);
                                final int strength = PasswordUtility.judgePasswordStrength(
                                        passwordValue.getStringValue());
                                if (strength < 50) {
                                    records.add(HealthRecord.forMessage(HealthMessage.Config_WeakPassword,
                                            settingToOutputText(setting, null), String.valueOf(strength)));
                                }
                            } catch (Exception e) {
                                LOGGER.error(PwmConstants.HEALTH_SESSION_LABEL,"error while inspecting setting " + settingToOutputText(setting, null) +  ", error: " + e.getMessage());
                            }
                        }
                    }
                }
            }
            for (final LdapProfile profile : config.getLdapProfiles().values()) {
                final PwmSetting setting = PwmSetting.LDAP_PROXY_USER_PASSWORD;
                try {
                    final PasswordData passwordValue = profile.readSettingAsPassword(setting);
                    final int strength = PasswordUtility.judgePasswordStrength(passwordValue == null ? null : passwordValue.getStringValue());
                    if (strength < 50) {
                        records.add(HealthRecord.forMessage(HealthMessage.Config_WeakPassword,
                                settingToOutputText(setting, profile),
                                String.valueOf(strength)));
                    }
                } catch (PwmException e) {
                    LOGGER.error(PwmConstants.HEALTH_SESSION_LABEL,"error while inspecting setting " + settingToOutputText(setting, profile) +  ", error: " + e.getMessage());
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

        for (final String profileID : config.getPasswordProfileIDs()) {
            try {
                final PwmPasswordPolicy pwmPasswordPolicy = config.getPasswordPolicy(profileID, locale);
                records.addAll(pwmPasswordPolicy.health(locale));
            } catch (Exception e) {
                LOGGER.error("unexpected error during password policy health check: " + e.getMessage(),e);
            }
        }

        /*
        if (config.readSettingAsBoolean(PwmSetting.FORGOTTEN_PASSWORD_ENABLE)) {
            if (!config.readSettingAsBoolean(PwmSetting.CHALLENGE_REQUIRE_RESPONSES)) {
                if (config.readSettingAsTokenSendMethod(PwmSetting.RECOVERY_TOKEN_SEND_METHOD) == MessageSendMethod.NONE) {
                    final Collection<FormConfiguration> formSettings = config.readSettingAsForm(PwmSetting.RECOVERY_ATTRIBUTE_FORM);
                    if (formSettings == null || formSettings.isEmpty()) {
                        records.add(HealthRecord.forMessage(HealthMessage.Config_NoRecoveryEnabled));
                    }
                }
            }
        }
        */





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

        if (config.getResponseStorageLocations(PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE).contains(DataStorageMethod.LOCALDB)) {
            records.add(HealthRecord.forMessage(HealthMessage.Config_UsingLocalDBResponseStorage, settingToOutputText(PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE)));
        }

        if (config.getOtpSecretStorageLocations(PwmSetting.OTP_SECRET_WRITE_PREFERENCE).contains(DataStorageMethod.LOCALDB)) {
            records.add(HealthRecord.forMessage(HealthMessage.Config_UsingLocalDBResponseStorage, settingToOutputText(PwmSetting.OTP_SECRET_WRITE_PREFERENCE)));
        }

        return records;
    }

    private static String settingToOutputText(
            final PwmSetting setting
    ) {
        return settingToOutputText(setting,null);
    }

    private static String settingToOutputText(
            final PwmSetting setting,
            final Profile profile
    ) {
        final String profileID = profile == null ? null : profile.getIdentifier();
        return setting.toMenuLocationDebug(profileID, PwmConstants.DEFAULT_LOCALE);
    }
}
