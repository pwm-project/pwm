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

import com.novell.ldapchai.ChaiEntry;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.*;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmPasswordPolicy;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Admin;
import password.pwm.i18n.LocaleHelper;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.TimeDuration;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.operations.UserStatusHelper;

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
        final List<HealthRecord> returnRecords = new ArrayList<HealthRecord>();
        final Configuration config = pwmApplication.getConfig();

        { // check ldap server
            returnRecords.addAll(checkBasicLdapConnectivity(pwmApplication,config,true));

            if (returnRecords.isEmpty()) {
                returnRecords.addAll(checkLdapServerUrls(config));
            }

            if (returnRecords.isEmpty()) {
                returnRecords.add(new HealthRecord(
                        HealthStatus.GOOD,
                        TOPIC,
                        LocaleHelper.getLocalizedMessage("Health_LDAP_OK",config,Admin.class)
                ));

                returnRecords.addAll(doLdapTestUserCheck(config, pwmApplication));

                final ErrorInformation errorInfo = pwmApplication.getLastLdapFailure();
                if (errorInfo != null) {
                    final TimeDuration errorAge = TimeDuration.fromCurrent(errorInfo.getDate().getTime());

                    if (errorAge.isShorterThan(PwmConstants.LDAP_CHECKER_RECENT_ERRORS_DURATION)) {
                        final String ageString = errorAge.asLongString();
                        final String errorDate = PwmConstants.DEFAULT_DATETIME_FORMAT.format(errorInfo.getDate());
                        final String errorMsg = errorInfo.toDebugStr();
                        returnRecords.add(
                                new HealthRecord(
                                        HealthStatus.CAUTION,
                                        TOPIC,
                                        LocaleHelper.getLocalizedMessage(null,"Health_LDAP_Unreachable",config,Admin.class,new String[]{ageString,errorDate,errorMsg})
                                ));
                    }
                }
            }
        }

        return returnRecords;
    }

    public List<HealthRecord> doLdapTestUserCheck(final Configuration config, final PwmApplication pwmApplication)
    {
        final String testUserDN = config.readSettingAsString(PwmSetting.LDAP_TEST_USER_DN);
        final String proxyUserDN = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
        final String proxyUserPW = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD);
        ChaiProvider.DIRECTORY_VENDOR vendor = null;

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
                    TOPIC,
                    LocaleHelper.getLocalizedMessage(null,"Health_LDAP_ProxyTestSameUser",config,Admin.class,new String[]{setting1,setting2})
            ));
            return returnRecords;
        }

        ChaiUser theUser = null;
        ChaiProvider chaiProvider = null;
        try {
            try {
                chaiProvider = Helper.createChaiProvider(
                        config,
                        proxyUserDN,
                        proxyUserPW,
                        PwmConstants.LDAP_CHECKER_CONNECTION_TIMEOUT
                );

                theUser = ChaiFactory.createChaiUser(testUserDN, chaiProvider);
            } catch (ChaiUnavailableException e) {
                returnRecords.add(new HealthRecord(HealthStatus.WARN, TOPIC,
                        LocaleHelper.getLocalizedMessage(null,"Health_LDAP_TestUserUnavailable",config,Admin.class,new String[]{e.getMessage()})));
                return returnRecords;
            } catch (Throwable e) {
                returnRecords.add(new HealthRecord(HealthStatus.WARN, TOPIC,
                        LocaleHelper.getLocalizedMessage(null,"Health_LDAP_TestUserUnexpected",config,Admin.class,new String[]{e.getMessage()})));
                return returnRecords;
            }

            try {
                theUser.readObjectClass();
            } catch (ChaiException e) {
                returnRecords.add(new HealthRecord(HealthStatus.WARN, TOPIC,
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
                        final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(pwmApplication, null, theUser, locale);
                        final String newPassword = RandomPasswordGenerator.createRandomPassword(null, passwordPolicy, pwmApplication);
                        theUser.setPassword(newPassword);
                        userPassword = newPassword;
                    } catch (ChaiPasswordPolicyException e) {
                        returnRecords.add(new HealthRecord(HealthStatus.WARN, TOPIC,
                                LocaleHelper.getLocalizedMessage(null,"Health_LDAP_TestUserPolicyError",config,Admin.class,new String[]{e.getMessage()})));
                        return returnRecords;
                    } catch (Exception e) {
                        returnRecords.add(new HealthRecord(HealthStatus.WARN, TOPIC,
                                LocaleHelper.getLocalizedMessage(null,"Health_LDAP_TestUserUnexpected",config,Admin.class,new String[]{e.getMessage()})));
                        return returnRecords;
                    }
                }
            }

            if (userPassword == null) {
                returnRecords.add(new HealthRecord(HealthStatus.WARN, TOPIC,
                        LocaleHelper.getLocalizedMessage(null,"Health_LDAP_TestUserNoTempPass",config,Admin.class)));
                return returnRecords;
            }

            try {
                UserStatusHelper.populateUserInfoBean(null, new UserInfoBean(), pwmApplication, PwmConstants.DEFAULT_LOCALE, theUser.getEntryDN(), userPassword, chaiProvider);
            } catch (ChaiUnavailableException e) {
                returnRecords.add(new HealthRecord(HealthStatus.WARN, TOPIC, "unable to read test user data: " + e.getMessage()));
                return returnRecords;
            } catch (PwmUnrecoverableException e) {
                returnRecords.add(new HealthRecord(HealthStatus.WARN, TOPIC, "unable to read test user data: " + e.getMessage()));
                return returnRecords;
            }

        } finally {
            if (chaiProvider != null) {
                try { chaiProvider.close(); } catch (Exception e) { /* ignore */ }
            }
        }

        returnRecords.add(new HealthRecord(HealthStatus.GOOD, TOPIC, LocaleHelper.getLocalizedMessage("Health_LDAP_TestUserOK",config,Admin.class)));
        return returnRecords;
    }


    public List<HealthRecord> checkLdapServerUrls(final Configuration config)
    {
        final List<HealthRecord> returnRecords = new ArrayList<HealthRecord>();
        final List<String> serverURLs = config.readSettingAsStringArray(PwmSetting.LDAP_SERVER_URLS);
        for (final String loopURL : serverURLs) {
            final String proxyDN = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
            ChaiProvider chaiProvider = null;
            try {
                chaiProvider = Helper.createChaiProvider(
                        config,
                        Collections.singletonList(loopURL),
                        proxyDN,
                        config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD),
                        PwmConstants.LDAP_CHECKER_CONNECTION_TIMEOUT);
                final ChaiUser proxyUser = ChaiFactory.createChaiUser(proxyDN, chaiProvider);
                proxyUser.isValid();
            } catch (Exception e) {
                final String errorString = "error connecting to ldap server '" + loopURL + "': " + e.getMessage();
                returnRecords.add(new HealthRecord(HealthStatus.WARN, TOPIC, errorString));
            } finally {
                if (chaiProvider != null) {
                    try { chaiProvider.close(); } catch (Exception e) { /* ignore */ }
                }
            }
        }
        return returnRecords;
    }

    public List<HealthRecord> checkBasicLdapConnectivity(final PwmApplication pwmApplication, final Configuration config, final boolean testContextlessRoot) {

        final List<HealthRecord> returnRecords = new ArrayList<HealthRecord>();
        ChaiProvider chaiProvider = null;
        try{
            try {
                final String proxyDN = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
                final String proxyPW = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD);
                if (proxyDN == null || proxyDN.length() < 1) {
                    return Collections.singletonList(new HealthRecord(HealthStatus.WARN,"LDAP","Missing Proxy User DN"));
                }
                if (proxyPW == null || proxyPW.length() < 1) {
                    return Collections.singletonList(new HealthRecord(HealthStatus.WARN,"LDAP","Missing Proxy User Password"));
                }
                chaiProvider = Helper.createChaiProvider(config,proxyDN,proxyPW,30*1000);
                final ChaiEntry adminEntry = ChaiFactory.createChaiEntry(config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN),chaiProvider);
                adminEntry.isValid();
                directoryVendor = chaiProvider.getDirectoryVendor();
            } catch (ChaiException e) {
                final ChaiError chaiError = ChaiErrors.getErrorForMessage(e.getMessage());
                final PwmError pwmError = PwmError.forChaiError(chaiError);
                final StringBuilder errorString = new StringBuilder();
                errorString.append("error connecting to ldap directory: ").append(e.getMessage());
                if (chaiError != null && chaiError != ChaiError.UNKNOWN) {
                    errorString.append(" (");
                    errorString.append(chaiError.toString());
                    if (pwmError != null && pwmError != PwmError.ERROR_UNKNOWN) {
                        errorString.append(" - ");
                        errorString.append(PwmError.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE, pwmError, pwmApplication.getConfig()));
                    }
                    errorString.append(")");
                }
                returnRecords.add(new HealthRecord(HealthStatus.WARN, TOPIC, errorString.toString()));
                pwmApplication.setLastLdapFailure(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,errorString.toString()));
                return returnRecords;
            } catch (Exception e) {
                HealthRecord record = new HealthRecord(HealthStatus.WARN, HealthTopic.LDAP, HealthMessage.LDAP_No_Connection, new String[]{e.getMessage()});
                returnRecords.add(record);
                pwmApplication.setLastLdapFailure(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,record.getDetail(PwmConstants.DEFAULT_LOCALE,pwmApplication.getConfig())));
                return returnRecords;
            }

            if (directoryVendor != null && directoryVendor == ChaiProvider.DIRECTORY_VENDOR.MICROSOFT_ACTIVE_DIRECTORY) {
                returnRecords.addAll(checkAd(pwmApplication, config));
            }

            if (testContextlessRoot) {
            for (final String loopContext : config.readSettingAsStringArray(PwmSetting.LDAP_CONTEXTLESS_ROOT)) {
                try {
                    final ChaiEntry contextEntry = ChaiFactory.createChaiEntry(loopContext,chaiProvider);
                    final Set<String> objectClasses = contextEntry.readObjectClass();

                    if (objectClasses == null || objectClasses.isEmpty()) {
                        final String errorString = "ldap context setting '" + loopContext + "' is not valid";
                        returnRecords.add(new HealthRecord(HealthStatus.WARN, TOPIC, errorString));
                    }
                } catch (Exception e) {
                    final String errorString = "ldap root context '" + loopContext + "' is not valid: " + e.getMessage();
                    returnRecords.add(new HealthRecord(HealthStatus.WARN, TOPIC, errorString));
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

    private static List<HealthRecord> checkAd(final PwmApplication pwmApplication, final Configuration config) {
        List<HealthRecord> returnList = new ArrayList<HealthRecord>();
        final List<String> serverURLs = config.readSettingAsStringArray(PwmSetting.LDAP_SERVER_URLS);
        for (final String loopURL : serverURLs) {
            try {
                if (!urlUsingHostname(loopURL)) {
                    final String msg = localizedString(pwmApplication,"Health_LDAP_AD_StaticIP",loopURL);
                    returnList.add(new HealthRecord(HealthStatus.WARN, TOPIC, loopURL + " should be configured using a dns hostname instead of an IP address.  Active Directory can sometimes have errors when using an IP address for configuration."));
                }

                final URI uri= URI.create(loopURL);
                final String scheme = uri.getScheme();
                if ("ldap".equalsIgnoreCase(scheme)) {
                    final String msg = localizedString(pwmApplication,"Health_LDAP_AD_Unsecure",loopURL);
                    returnList.add(new HealthRecord(HealthStatus.WARN, TOPIC, msg));
                }
            } catch (MalformedURLException e) {
                returnList.add(new HealthRecord(HealthStatus.WARN, TOPIC, loopURL + " is not a valid url"));
            } catch (UnknownHostException e) {
                returnList.add(new HealthRecord(HealthStatus.WARN, TOPIC, loopURL + " is not a valid host"));
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
}
