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

import com.novell.ldapchai.ChaiEntry;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.*;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmPasswordPolicy;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.LdapProfile;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Admin;
import password.pwm.i18n.LocaleHelper;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.UserStatusHelper;
import password.pwm.util.PwmLogger;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.TimeDuration;
import password.pwm.util.operations.PasswordUtility;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.*;

public class LDAPStatusChecker implements HealthChecker {

    final private static PwmLogger LOGGER = PwmLogger.getLogger(LDAPStatusChecker.class);
    final private static String TOPIC = "LDAP";

    private ChaiProvider.DIRECTORY_VENDOR directoryVendor = null;

    public List<HealthRecord> doHealthCheck(final PwmApplication pwmApplication)
    {
        final Configuration config = pwmApplication.getConfig();
        final List<HealthRecord> returnRecords = new ArrayList<HealthRecord>();
        final Map<String,LdapProfile> ldapProfiles = pwmApplication.getConfig().getLdapProfiles();

        for (final String profileID : ldapProfiles.keySet()) {
            final List<HealthRecord> profileRecords = new ArrayList<HealthRecord>();
            profileRecords.addAll(
                    checkBasicLdapConnectivity(pwmApplication, config, ldapProfiles.get(profileID), true));

            if (profileRecords.isEmpty()) {
                profileRecords.addAll(checkLdapServerUrls(config, ldapProfiles.get(profileID)));
            }

            if (profileRecords.isEmpty()) {
                profileRecords.add(new HealthRecord(
                        HealthStatus.GOOD,
                        makeLdapTopic(profileID, config),
                        LocaleHelper.getLocalizedMessage("Health_LDAP_OK",config,Admin.class)
                ));

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
                    returnRecords.add(
                            new HealthRecord(
                                    HealthStatus.CAUTION,
                                    makeLdapTopic(ldapProfile,pwmApplication.getConfig()),
                                    LocaleHelper.getLocalizedMessage(null,"Health_LDAP_RecentlyUnreachable",config,Admin.class,new String[]{ageString,errorDate,errorMsg})
                            ));
                }
            }
        }

        return returnRecords;
    }

    public List<HealthRecord> doLdapTestUserCheck(final Configuration config, final LdapProfile ldapProfile, final PwmApplication pwmApplication)
    {
        final String testUserDN = ldapProfile.readSettingAsString(PwmSetting.LDAP_TEST_USER_DN);
        final String proxyUserDN = ldapProfile.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
        final String proxyUserPW = ldapProfile.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD);

        final List<HealthRecord> returnRecords = new ArrayList<HealthRecord>();

        if (testUserDN == null || testUserDN.length() < 1) {
            return returnRecords;
        }

        if (proxyUserDN.equalsIgnoreCase(testUserDN)) {
            final String setting1 = PwmSetting.LDAP_TEST_USER_DN.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE)
                    + " -> "
                    + PwmSetting.LDAP_TEST_USER_DN.getLabel(PwmConstants.DEFAULT_LOCALE);
            final String setting2 = PwmSetting.LDAP_PROXY_USER_DN.getLabel(PwmConstants.DEFAULT_LOCALE);

            returnRecords.add(new HealthRecord(
                    HealthStatus.WARN,
                    makeLdapTopic(ldapProfile, config),
                    LocaleHelper.getLocalizedMessage(null,"Health_LDAP_ProxyTestSameUser",config,Admin.class,new String[]{setting1,setting2})
            ));
            return returnRecords;
        }

        ChaiUser theUser = null;
        ChaiProvider chaiProvider = null;

        try {
            try {

                chaiProvider = LdapOperationsHelper.createChaiProvider(
                        ldapProfile,
                        config,
                        proxyUserDN,
                        proxyUserPW
                );

                theUser = ChaiFactory.createChaiUser(testUserDN, chaiProvider);

            } catch (ChaiUnavailableException e) {
                returnRecords.add(new HealthRecord(
                        HealthStatus.WARN,
                        makeLdapTopic(ldapProfile, config),
                        LocaleHelper.getLocalizedMessage(null,"Health_LDAP_TestUserUnavailable",config,Admin.class,new String[]{e.getMessage()})));
                return returnRecords;
            } catch (Throwable e) {
                returnRecords.add(new HealthRecord(
                        HealthStatus.WARN,
                        makeLdapTopic(ldapProfile, config),
                        LocaleHelper.getLocalizedMessage(null,"Health_LDAP_TestUserUnexpected",config,Admin.class,new String[]{e.getMessage()})));
                return returnRecords;
            }

            try {
                theUser.readObjectClass();
            } catch (ChaiException e) {
                returnRecords.add(new HealthRecord(
                        HealthStatus.WARN,
                        makeLdapTopic(ldapProfile, config),
                        LocaleHelper.getLocalizedMessage(null,"Health_LDAP_TestUserError",config,Admin.class,new String[]{e.getMessage()})));
                return returnRecords;
            }

            String userPassword = null;
            {
                try {
                    final String passwordFromLdap = theUser.readPassword();
                    if (passwordFromLdap != null && passwordFromLdap.length() > 0) {
                        userPassword = passwordFromLdap;
                    }
                } catch (Exception e) {
                    LOGGER.trace("error retrieving user password from directory, this is probably okay; " + e.getMessage());
                }

                if (userPassword == null) {
                    try {
                        final Locale locale = PwmConstants.DEFAULT_LOCALE;
                        final UserIdentity userIdentity = new UserIdentity(testUserDN, ldapProfile.getIdentifier());

                        final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(
                                pwmApplication, null, userIdentity, theUser, locale);
                        final String newPassword = RandomPasswordGenerator.createRandomPassword(null, passwordPolicy,
                                pwmApplication);
                        theUser.setPassword(newPassword);
                        userPassword = newPassword;
                    } catch (ChaiPasswordPolicyException e) {
                        returnRecords.add(new HealthRecord(
                                HealthStatus.WARN,
                                makeLdapTopic(ldapProfile, config),
                                LocaleHelper.getLocalizedMessage(null,"Health_LDAP_TestUserPolicyError",config,Admin.class,new String[]{e.getMessage()})));
                        return returnRecords;
                    } catch (Exception e) {
                        returnRecords.add(new HealthRecord(
                                HealthStatus.WARN,
                                makeLdapTopic(ldapProfile, config),
                                LocaleHelper.getLocalizedMessage(null,"Health_LDAP_TestUserUnexpected",config,Admin.class,new String[]{e.getMessage()})));
                        return returnRecords;
                    }
                }
            }

            if (userPassword == null) {
                returnRecords.add(new HealthRecord(
                        HealthStatus.WARN,
                        makeLdapTopic(ldapProfile, config),
                        LocaleHelper.getLocalizedMessage(null,"Health_LDAP_TestUserNoTempPass",config,Admin.class)));
                return returnRecords;
            }

            try {
                final UserIdentity userIdentity = new UserIdentity(theUser.getEntryDN(),ldapProfile.getIdentifier());
                UserStatusHelper.populateUserInfoBean(pwmApplication, null, new UserInfoBean(),
                        PwmConstants.DEFAULT_LOCALE, userIdentity, userPassword, chaiProvider);
            } catch (ChaiUnavailableException e) {
                returnRecords.add(new HealthRecord(
                        HealthStatus.WARN,
                        makeLdapTopic(ldapProfile, config),
                        "unable to read test user data: " + e.getMessage()));
                return returnRecords;
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

        returnRecords.add(new HealthRecord(HealthStatus.GOOD, makeLdapTopic(ldapProfile, config), LocaleHelper.getLocalizedMessage("Health_LDAP_TestUserOK",config,Admin.class)));
        return returnRecords;
    }


    public List<HealthRecord> checkLdapServerUrls(final Configuration config, final LdapProfile ldapProfile)
    {
        final List<HealthRecord> returnRecords = new ArrayList<HealthRecord>();
        final List<String> serverURLs = ldapProfile.readSettingAsStringArray(PwmSetting.LDAP_SERVER_URLS);
        for (final String loopURL : serverURLs) {
            final String proxyDN = ldapProfile.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
            ChaiProvider chaiProvider = null;
            try {
                chaiProvider = LdapOperationsHelper.createChaiProvider(
                        config,
                        ldapProfile,
                        Collections.singletonList(loopURL),
                        proxyDN,
                        ldapProfile.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD)
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

        final List<HealthRecord> returnRecords = new ArrayList<HealthRecord>();
        ChaiProvider chaiProvider = null;
        try{
            try {
                final String proxyDN = ldapProfile.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
                final String proxyPW = ldapProfile.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD);
                if (proxyDN == null || proxyDN.length() < 1) {
                    return Collections.singletonList(new HealthRecord(HealthStatus.WARN,"LDAP","Missing Proxy User DN"));
                }
                if (proxyPW == null || proxyPW.length() < 1) {
                    return Collections.singletonList(new HealthRecord(HealthStatus.WARN,"LDAP","Missing Proxy User Password"));
                }
                chaiProvider = LdapOperationsHelper.createChaiProvider(ldapProfile,config,proxyDN,proxyPW);
                final ChaiEntry adminEntry = ChaiFactory.createChaiEntry(proxyDN,chaiProvider);
                adminEntry.isValid();
                directoryVendor = chaiProvider.getDirectoryVendor();
            } catch (ChaiException e) {
                final ChaiError chaiError = ChaiErrors.getErrorForMessage(e.getMessage());
                final PwmError pwmError = PwmError.forChaiError(chaiError);
                final StringBuilder errorString = new StringBuilder();
                final String profileName = PwmConstants.DEFAULT_LDAP_PROFILE.equals(ldapProfile.getIdentifier()) ? "Default" : ldapProfile.getIdentifier();
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
                HealthRecord record = new HealthRecord(
                        HealthStatus.WARN,
                        HealthTopic.LDAP,
                        HealthMessage.LDAP_No_Connection,
                        new String[]{e.getMessage()});
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
        List<HealthRecord> returnList = new ArrayList<HealthRecord>();
        final List<String> serverURLs = ldapProfile.readSettingAsStringArray(PwmSetting.LDAP_SERVER_URLS);
        for (final String loopURL : serverURLs) {
            try {
                if (!urlUsingHostname(loopURL)) {
                    final String msg = localizedString(pwmApplication,"Health_LDAP_AD_StaticIP",loopURL);
                    returnList.add(new HealthRecord(HealthStatus.WARN, makeLdapTopic(ldapProfile, config), loopURL + " should be configured using a dns hostname instead of an IP address.  Active Directory can sometimes have errors when using an IP address for configuration."));
                }

                final URI uri= URI.create(loopURL);
                final String scheme = uri.getScheme();
                if ("ldap".equalsIgnoreCase(scheme)) {
                    final String msg = localizedString(pwmApplication,"Health_LDAP_AD_Unsecure",loopURL);
                    returnList.add(new HealthRecord(HealthStatus.WARN, makeLdapTopic(ldapProfile, config), msg));
                }
            } catch (MalformedURLException e) {
                returnList.add(new HealthRecord(HealthStatus.WARN, makeLdapTopic(ldapProfile, config), loopURL + " is not a valid url"));
            } catch (UnknownHostException e) {
                returnList.add(new HealthRecord(HealthStatus.WARN, makeLdapTopic(ldapProfile, config), loopURL + " is not a valid host"));
            }
        }
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

    private static String localizedString(final PwmApplication pwmApplication, final String key, final String... values) {
        return LocaleHelper.getLocalizedMessage(null,key,pwmApplication.getConfig(),Admin.class,values);
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
        return TOPIC + "-" + (PwmConstants.DEFAULT_LDAP_PROFILE.equals(profileID) ? "Default" : profileID);
    }
}
