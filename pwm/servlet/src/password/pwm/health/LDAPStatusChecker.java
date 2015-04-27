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

import com.novell.ldapchai.ChaiEntry;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.*;
import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiProviderFactory;
import com.novell.ldapchai.provider.ChaiSetting;
import com.novell.ldapchai.util.ChaiUtility;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.UserStatusReader;
import password.pwm.util.JsonUtil;
import password.pwm.util.PasswordData;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.ws.server.rest.bean.HealthData;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.*;

public class LDAPStatusChecker implements HealthChecker {

    final private static PwmLogger LOGGER = PwmLogger.forClass(LDAPStatusChecker.class);
    final private static String TOPIC = "LDAP";

    private ChaiProvider.DIRECTORY_VENDOR directoryVendor = null;

    public List<HealthRecord> doHealthCheck(final PwmApplication pwmApplication)
    {
        final Configuration config = pwmApplication.getConfig();
        final List<HealthRecord> returnRecords = new ArrayList<>();
        final Map<String,LdapProfile> ldapProfiles = pwmApplication.getConfig().getLdapProfiles();

        for (final String profileID : ldapProfiles.keySet()) {
            final List<HealthRecord> profileRecords = new ArrayList<>();
            profileRecords.addAll(
                    checkBasicLdapConnectivity(pwmApplication, config, ldapProfiles.get(profileID), true));

            if (profileRecords.isEmpty()) {
                profileRecords.addAll(checkLdapServerUrls(config, ldapProfiles.get(profileID)));
            }

            if (profileRecords.isEmpty()) {
                profileRecords.add(HealthRecord.forMessage(HealthMessage.LDAP_OK));
                profileRecords.addAll(doLdapTestUserCheck(config, ldapProfiles.get(profileID), pwmApplication));

            }
            returnRecords.addAll(profileRecords);
        }

        for (LdapProfile ldapProfile : pwmApplication.getLdapConnectionService().getLastLdapFailure().keySet()) {
            final ErrorInformation errorInfo = pwmApplication.getLdapConnectionService().getLastLdapFailure().get(ldapProfile);
            if (errorInfo != null) {
                final TimeDuration errorAge = TimeDuration.fromCurrent(errorInfo.getDate().getTime());

                final long cautionDurationMS = Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.HEALTH_LDAP_CAUTION_DURATION_MS));
                if (errorAge.isShorterThan(cautionDurationMS)) {
                    final String ageString = errorAge.asLongString();
                    final String errorDate = PwmConstants.DEFAULT_DATETIME_FORMAT.format(errorInfo.getDate());
                    final String errorMsg = errorInfo.toDebugStr();
                    returnRecords.add(HealthRecord.forMessage(
                            HealthMessage.LDAP_RecentlyUnreachable,
                            ldapProfile.getDisplayName(PwmConstants.DEFAULT_LOCALE),
                            ageString,
                            errorDate,
                            errorMsg
                    ));
                }
            }
        }

        returnRecords.addAll(checkVendorSameness(pwmApplication));

        return returnRecords;
    }

    public List<HealthRecord> doLdapTestUserCheck(final Configuration config, final LdapProfile ldapProfile, final PwmApplication pwmApplication)
    {
        final String testUserDN = ldapProfile.readSettingAsString(PwmSetting.LDAP_TEST_USER_DN);
        final String proxyUserDN = ldapProfile.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
        final PasswordData proxyUserPW = ldapProfile.readSettingAsPassword(PwmSetting.LDAP_PROXY_USER_PASSWORD);

        final List<HealthRecord> returnRecords = new ArrayList<>();

        if (testUserDN == null || testUserDN.length() < 1) {
            return returnRecords;
        }

        if (proxyUserDN.equalsIgnoreCase(testUserDN)) {
            returnRecords.add(HealthRecord.forMessage(HealthMessage.LDAP_ProxyTestSameUser,
                    ConfigurationChecker.settingToOutputText(PwmSetting.LDAP_TEST_USER_DN,ldapProfile),
                    ConfigurationChecker.settingToOutputText(PwmSetting.LDAP_PROXY_USER_DN,ldapProfile)
            ));
            return returnRecords;
        }

        ChaiUser theUser = null;
        ChaiProvider chaiProvider = null;

        try {
            try {

                chaiProvider = LdapOperationsHelper.createChaiProvider(
                        PwmConstants.HEALTH_SESSION_LABEL,
                        ldapProfile,
                        config,
                        proxyUserDN,
                        proxyUserPW
                );

                theUser = ChaiFactory.createChaiUser(testUserDN, chaiProvider);

            } catch (ChaiUnavailableException e) {
                returnRecords.add(HealthRecord.forMessage(HealthMessage.LDAP_TestUserUnavailable,
                        ConfigurationChecker.settingToOutputText(PwmSetting.LDAP_TEST_USER_DN,ldapProfile),
                        e.getMessage()
                ));
                return returnRecords;
            } catch (Throwable e) {
                returnRecords.add(HealthRecord.forMessage(HealthMessage.LDAP_TestUserUnexpected,
                        ConfigurationChecker.settingToOutputText(PwmSetting.LDAP_TEST_USER_DN,ldapProfile),
                        e.getMessage()
                ));
                return returnRecords;
            }

            try {
                theUser.readObjectClass();
            } catch (ChaiException e) {
                returnRecords.add(HealthRecord.forMessage(HealthMessage.LDAP_TestUserError,
                        ConfigurationChecker.settingToOutputText(PwmSetting.LDAP_TEST_USER_DN,ldapProfile),
                        e.getMessage()
                ));
                return returnRecords;
            }

            PasswordData userPassword = null;
            {
                try {
                    final String passwordFromLdap = theUser.readPassword();
                    if (passwordFromLdap != null && passwordFromLdap.length() > 0) {
                        userPassword = new PasswordData(passwordFromLdap);
                    }
                } catch (Exception e) {
                    LOGGER.trace(PwmConstants.HEALTH_SESSION_LABEL,"error retrieving user password from directory, this is probably okay; " + e.getMessage());
                }

                if (userPassword == null) {
                    try {
                        final Locale locale = PwmConstants.DEFAULT_LOCALE;
                        final UserIdentity userIdentity = new UserIdentity(testUserDN, ldapProfile.getIdentifier());

                        final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(
                                pwmApplication, null, userIdentity, theUser, locale);
                        final PasswordData newPassword = RandomPasswordGenerator.createRandomPassword(null, passwordPolicy,
                                pwmApplication);
                        theUser.setPassword(newPassword.getStringValue());
                        userPassword = newPassword;
                    } catch (ChaiPasswordPolicyException e) {
                        returnRecords.add(HealthRecord.forMessage(HealthMessage.LDAP_TestUserPolicyError,
                                ConfigurationChecker.settingToOutputText(PwmSetting.LDAP_TEST_USER_DN,ldapProfile),
                                e.getMessage()
                        ));
                        return returnRecords;
                    } catch (Exception e) {
                        returnRecords.add(HealthRecord.forMessage(HealthMessage.LDAP_TestUserUnexpected,
                                ConfigurationChecker.settingToOutputText(PwmSetting.LDAP_TEST_USER_DN,ldapProfile),
                                e.getMessage()
                        ));
                        return returnRecords;
                    }
                }
            }

            if (userPassword == null) {
                returnRecords.add(HealthRecord.forMessage(HealthMessage.LDAP_TestUserNoTempPass,
                        ConfigurationChecker.settingToOutputText(PwmSetting.LDAP_TEST_USER_DN,ldapProfile)
                ));
                return returnRecords;
            }

            try {
                final UserIdentity userIdentity = new UserIdentity(theUser.getEntryDN(),ldapProfile.getIdentifier());
                final UserStatusReader.Settings readerSettings = new UserStatusReader.Settings();
                readerSettings.setSkipReportUpdate(true);
                final UserStatusReader userStatusReader = new UserStatusReader(
                        pwmApplication,
                        PwmConstants.HEALTH_SESSION_LABEL,
                        readerSettings
                );
                userStatusReader.populateUserInfoBean(
                        new UserInfoBean(),
                        PwmConstants.DEFAULT_LOCALE,
                        userIdentity,
                        chaiProvider
                );
            } catch (PwmUnrecoverableException e) {
                returnRecords.add(new HealthRecord(
                        HealthStatus.WARN,
                        makeLdapTopic(ldapProfile, config),
                        "unable to read test user data: " + e.getMessage()));
                return returnRecords;
            }

        } finally {
            if (chaiProvider != null) {
                try { chaiProvider.close(); } catch (Exception e) {
                    // ignore
                }
            }
        }

        returnRecords.add(HealthRecord.forMessage(HealthMessage.LDAP_TestUserOK, ldapProfile.getDisplayName(PwmConstants.DEFAULT_LOCALE)));
        return returnRecords;
    }


    public List<HealthRecord> checkLdapServerUrls(final Configuration config, final LdapProfile ldapProfile)
    {
        final List<HealthRecord> returnRecords = new ArrayList<>();
        final List<String> serverURLs = ldapProfile.readSettingAsStringArray(PwmSetting.LDAP_SERVER_URLS);
        for (final String loopURL : serverURLs) {
            final String proxyDN = ldapProfile.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
            ChaiProvider chaiProvider = null;
            try {
                chaiProvider = LdapOperationsHelper.createChaiProvider(
                        PwmConstants.HEALTH_SESSION_LABEL,
                        config,
                        ldapProfile,
                        Collections.singletonList(loopURL),
                        proxyDN,
                        ldapProfile.readSettingAsPassword(PwmSetting.LDAP_PROXY_USER_PASSWORD)
                );
                final ChaiUser proxyUser = ChaiFactory.createChaiUser(proxyDN, chaiProvider);
                proxyUser.isValid();
            } catch (Exception e) {
                final String errorString = "error connecting to ldap server '" + loopURL + "': " + e.getMessage();
                returnRecords.add(new HealthRecord(
                        HealthStatus.WARN,
                        makeLdapTopic(ldapProfile, config),
                        errorString));
            } finally {
                if (chaiProvider != null) {
                    try { chaiProvider.close(); } catch (Exception e) { /* ignore */ }
                }
            }
        }
        return returnRecords;
    }

    public List<HealthRecord> checkBasicLdapConnectivity(
            final PwmApplication pwmApplication,
            final Configuration config,
            final LdapProfile ldapProfile,
            final boolean testContextlessRoot
    ) {

        final List<HealthRecord> returnRecords = new ArrayList<>();
        ChaiProvider chaiProvider = null;
        try{
            try {
                final String proxyDN = ldapProfile.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
                final PasswordData proxyPW = ldapProfile.readSettingAsPassword(PwmSetting.LDAP_PROXY_USER_PASSWORD);
                if (proxyDN == null || proxyDN.length() < 1) {
                    return Collections.singletonList(new HealthRecord(HealthStatus.WARN,HealthTopic.LDAP,"Missing Proxy User DN"));
                }
                if (proxyPW == null) {
                    return Collections.singletonList(new HealthRecord(HealthStatus.WARN,HealthTopic.LDAP,"Missing Proxy User Password"));
                }
                chaiProvider = LdapOperationsHelper.createChaiProvider(PwmConstants.HEALTH_SESSION_LABEL,ldapProfile,config,proxyDN,proxyPW);
                final ChaiEntry adminEntry = ChaiFactory.createChaiEntry(proxyDN,chaiProvider);
                adminEntry.isValid();
                directoryVendor = chaiProvider.getDirectoryVendor();
            } catch (ChaiException e) {
                final ChaiError chaiError = ChaiErrors.getErrorForMessage(e.getMessage());
                final PwmError pwmError = PwmError.forChaiError(chaiError);
                final StringBuilder errorString = new StringBuilder();
                final String profileName = ldapProfile.getIdentifier();
                errorString.append("error connecting to ldap directory (").append(profileName).append("), error: ").append(e.getMessage());
                if (chaiError != null && chaiError != ChaiError.UNKNOWN) {
                    errorString.append(" (");
                    errorString.append(chaiError.toString());
                    if (pwmError != null && pwmError != PwmError.ERROR_UNKNOWN) {
                        errorString.append(" - ");
                        errorString.append(pwmError.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE, pwmApplication.getConfig()));
                    }
                    errorString.append(")");
                }
                returnRecords.add(new HealthRecord(
                        HealthStatus.WARN,
                        makeLdapTopic(ldapProfile, config),
                        errorString.toString()));
                pwmApplication.getLdapConnectionService().setLastLdapFailure(ldapProfile,
                        new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,errorString.toString()));
                return returnRecords;
            } catch (Exception e) {
                HealthRecord record = HealthRecord.forMessage(HealthMessage.LDAP_No_Connection, e.getMessage());
                returnRecords.add(record);
                pwmApplication.getLdapConnectionService().setLastLdapFailure(ldapProfile,
                        new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,record.getDetail(PwmConstants.DEFAULT_LOCALE,pwmApplication.getConfig())));
                return returnRecords;
            }

            if (directoryVendor != null && directoryVendor == ChaiProvider.DIRECTORY_VENDOR.MICROSOFT_ACTIVE_DIRECTORY) {
                returnRecords.addAll(checkAd(pwmApplication, config, ldapProfile));
            }

            if (testContextlessRoot) {
                for (final String loopContext : ldapProfile.readSettingAsStringArray(PwmSetting.LDAP_CONTEXTLESS_ROOT)) {
                    try {
                        final ChaiEntry contextEntry = ChaiFactory.createChaiEntry(loopContext,chaiProvider);
                        final Set<String> objectClasses = contextEntry.readObjectClass();

                        if (objectClasses == null || objectClasses.isEmpty()) {
                            final String errorString = "ldap context setting '" + loopContext + "' is not valid";
                            returnRecords.add(new HealthRecord(HealthStatus.WARN, makeLdapTopic(ldapProfile, config), errorString));
                        }
                    } catch (Exception e) {
                        final String errorString = "ldap root context '" + loopContext + "' is not valid: " + e.getMessage();
                        returnRecords.add(new HealthRecord(HealthStatus.WARN, makeLdapTopic(ldapProfile, config), errorString));
                    }
                }
            }
        } finally {
            if (chaiProvider != null) {
                try { chaiProvider.close(); } catch (Exception e) { /* ignore */ }
            }
        }

        return returnRecords;
    }

    private static List<HealthRecord> checkAd(final PwmApplication pwmApplication, final Configuration config, final LdapProfile ldapProfile) {
        List<HealthRecord> returnList = new ArrayList<>();
        final List<String> serverURLs = ldapProfile.readSettingAsStringArray(PwmSetting.LDAP_SERVER_URLS);
        for (final String loopURL : serverURLs) {
            try {
                if (!urlUsingHostname(loopURL)) {
                    returnList.add(HealthRecord.forMessage(
                            HealthMessage.LDAP_AD_StaticIP,
                            loopURL
                    ));
                }

                final URI uri= URI.create(loopURL);
                final String scheme = uri.getScheme();
                if ("ldap".equalsIgnoreCase(scheme)) {
                    returnList.add(HealthRecord.forMessage(
                            HealthMessage.LDAP_AD_Unsecure,
                            loopURL
                    ));
                }
            } catch (MalformedURLException | UnknownHostException e) {
                returnList.add(HealthRecord.forMessage(
                        HealthMessage.Config_ParseError,
                        e.getMessage(),
                        ConfigurationChecker.settingToOutputText(PwmSetting.LDAP_SERVER_URLS,ldapProfile),
                        loopURL
                ));
            }
        }

        returnList.addAll(checkAdPasswordPolicyApi(pwmApplication));

        return returnList;
    }

    private static boolean urlUsingHostname(final String inputURL) throws MalformedURLException, UnknownHostException {
        final URI uri = URI.create(inputURL);
        final String host = uri.getHost();
        final InetAddress inetAddress = InetAddress.getByName(host);
        if (inetAddress != null && inetAddress.getHostName() != null && inetAddress.getHostName().equalsIgnoreCase(host)) {
            return true;
        }
        return false;
    }

    private static String makeLdapTopic(
            final LdapProfile ldapProfile,
            final Configuration configuration
    ) {
        return makeLdapTopic(ldapProfile.getIdentifier(), configuration);
    }

    private static String makeLdapTopic(
            final String profileID,
            final Configuration configuration
    ) {
        if (configuration.getLdapProfiles().isEmpty() || configuration.getLdapProfiles().size() < 2) {
            return TOPIC;
        }
        return TOPIC + "-" + profileID;
    }

    private List<HealthRecord> checkVendorSameness(final PwmApplication pwmApplication) {
        final Map<HealthMonitor.HealthProperty,Serializable> healthProperties = pwmApplication.getHealthMonitor().getHealthProperties();
        if (healthProperties.containsKey(HealthMonitor.HealthProperty.LdapVendorSameCheck)) {
            return (List<HealthRecord>)healthProperties.get(HealthMonitor.HealthProperty.LdapVendorSameCheck);
        }

        LOGGER.trace(PwmConstants.HEALTH_SESSION_LABEL,"beginning check for replica vendor sameness");
        boolean errorReachingServer = false;
        final Map<String,ChaiProvider.DIRECTORY_VENDOR> replicaVendorMap = new HashMap<>();

        try {
            for (final LdapProfile ldapProfile : pwmApplication.getConfig().getLdapProfiles().values()) {
                final ChaiConfiguration profileChaiConfiguration = LdapOperationsHelper.createChaiConfiguration(
                        pwmApplication.getConfig(),
                        ldapProfile
                );
                final Collection<ChaiConfiguration> replicaConfigs = ChaiUtility.splitConfigurationPerReplica(profileChaiConfiguration, Collections.<ChaiSetting,String>emptyMap());
                for (final ChaiConfiguration chaiConfiguration : replicaConfigs) {
                    final ChaiProvider loopProvider = ChaiProviderFactory.createProvider(chaiConfiguration);
                    replicaVendorMap.put(chaiConfiguration.getSetting(ChaiSetting.BIND_URLS),loopProvider.getDirectoryVendor());
                }
            }
        } catch (Exception e) {
            errorReachingServer = true;
            LOGGER.error(PwmConstants.HEALTH_SESSION_LABEL,"error during replica vendor sameness check: " + e.getMessage());
        }

        final ArrayList<HealthRecord> healthRecords = new ArrayList<>();
        final Set<ChaiProvider.DIRECTORY_VENDOR> discoveredVendors = new HashSet<>(replicaVendorMap.values());

        if (discoveredVendors.size() >= 2) {
            final String mapAsJson = JsonUtil.serializeMap(replicaVendorMap);
            healthRecords.add(HealthRecord.forMessage(HealthMessage.LDAP_VendorsNotSame, mapAsJson));
            // cache the error
            healthProperties.put(HealthMonitor.HealthProperty.LdapVendorSameCheck, healthRecords);

            LOGGER.warn(PwmConstants.HEALTH_SESSION_LABEL,"multiple ldap vendors found: " + mapAsJson);
        } else if (discoveredVendors.size() == 1) {
            if (!errorReachingServer) {
                // cache the no errors
                healthProperties.put(HealthMonitor.HealthProperty.LdapVendorSameCheck, healthRecords);
            }
        }

        return healthRecords;
    }

    private static List<HealthRecord> checkAdPasswordPolicyApi(final PwmApplication pwmApplication) {


        final boolean passwordPolicyApiEnabled = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.AD_ENFORCE_PW_HISTORY_ON_SET);
        if (!passwordPolicyApiEnabled) {
            return Collections.emptyList();
        }

        final Map<HealthMonitor.HealthProperty,Serializable> healthProperties = pwmApplication.getHealthMonitor().getHealthProperties();
        if (healthProperties.containsKey(HealthMonitor.HealthProperty.AdPasswordPolicyApiCheck)) {
            return (List<HealthRecord>)healthProperties.get(HealthMonitor.HealthProperty.AdPasswordPolicyApiCheck);
        }

        LOGGER.trace(PwmConstants.HEALTH_SESSION_LABEL,"beginning check for ad api password policy (asn " + PwmConstants.LDAP_AD_PASSWORD_POLICY_CONTROL_ASN + ") support");
        boolean errorReachingServer = false;
        final ArrayList<HealthRecord> healthRecords = new ArrayList<>();

        try {
            for (final LdapProfile ldapProfile : pwmApplication.getConfig().getLdapProfiles().values()) {
                final ChaiConfiguration profileChaiConfiguration = LdapOperationsHelper.createChaiConfiguration(
                        pwmApplication.getConfig(),
                        ldapProfile
                );
                final Collection<ChaiConfiguration> replicaConfigs = ChaiUtility.splitConfigurationPerReplica(profileChaiConfiguration, Collections.<ChaiSetting,String>emptyMap());
                for (final ChaiConfiguration chaiConfiguration : replicaConfigs) {
                    final ChaiProvider loopProvider = ChaiProviderFactory.createProvider(chaiConfiguration);
                    final ChaiEntry rootDSE = ChaiUtility.getRootDSE(loopProvider);
                    final Set<String> controls = rootDSE.readMultiStringAttribute("supportedControl");
                    final boolean asnSupported = controls.contains(PwmConstants.LDAP_AD_PASSWORD_POLICY_CONTROL_ASN);
                    if (!asnSupported) {
                        final String url = chaiConfiguration.getSetting(ChaiSetting.BIND_URLS);
                        final HealthRecord record = HealthRecord.forMessage(
                                HealthMessage.LDAP_Ad_History_Asn_Missing,
                                ConfigurationChecker.settingToOutputText(PwmSetting.AD_ENFORCE_PW_HISTORY_ON_SET, null),
                                url
                        );
                        healthRecords.add(record);
                        LOGGER.warn(record.toDebugString(PwmConstants.DEFAULT_LOCALE,pwmApplication.getConfig()));
                    }
                }
            }
        } catch (Exception e) {
            errorReachingServer = true;
            LOGGER.error(PwmConstants.HEALTH_SESSION_LABEL,
                    "error during ad api password policy (asn " + PwmConstants.LDAP_AD_PASSWORD_POLICY_CONTROL_ASN + ") check: " + e.getMessage());
        }

        if (!errorReachingServer) {
            healthProperties.put(HealthMonitor.HealthProperty.AdPasswordPolicyApiCheck, healthRecords);
        }

        return healthRecords;
    }

    public static HealthData healthForNewConfiguration(
            final PwmApplication pwmApplication,
            final Configuration config,
            final Locale locale,
            final String profileID,
            boolean testContextless,
            boolean fullTest

    )
                throws PwmUnrecoverableException
    {
        final PwmApplication tempApplication = new PwmApplication.PwmEnvironment()
                .setConfig(config)
                .setApplicationMode(PwmApplication.MODE.NEW)
                .setApplicationPath(pwmApplication.getApplicationPath())
                .setInitLogging(false)
                .setConfigurationFile(null)
                .setWebInfPath(pwmApplication.getWebInfPath())
                .createPwmApplication();

        final LDAPStatusChecker ldapStatusChecker = new LDAPStatusChecker();
        final List<HealthRecord> profileRecords = new ArrayList<>();

        final LdapProfile ldapProfile = config.getLdapProfiles().get(profileID);
        profileRecords.addAll(ldapStatusChecker.checkBasicLdapConnectivity(tempApplication, config, ldapProfile,
                testContextless));
        if (fullTest) {
            profileRecords.addAll(ldapStatusChecker.checkLdapServerUrls(config, ldapProfile));
        }

        if (profileRecords.isEmpty()) {
            profileRecords.add(HealthRecord.forMessage(HealthMessage.LDAP_OK));
        }

        if (fullTest) {
            profileRecords.addAll(ldapStatusChecker.doLdapTestUserCheck(config, ldapProfile, tempApplication));
        }

        return HealthRecord.asHealthDataBean(config, locale, profileRecords);
    }
}
