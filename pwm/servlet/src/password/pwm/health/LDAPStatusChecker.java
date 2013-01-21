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
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiPasswordPolicyException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
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

import java.util.*;

public class LDAPStatusChecker implements HealthChecker {

    final private static PwmLogger LOGGER = PwmLogger.getLogger(LDAPStatusChecker.class);
    final private static String TOPIC = "LDAP";

    public List<HealthRecord> doHealthCheck(final PwmApplication pwmApplication)
    {
        final List<HealthRecord> returnRecords = new ArrayList<HealthRecord>();
        final Configuration config = pwmApplication.getConfig();

        { // check ldap server
            returnRecords.addAll(checkBasicLdapConnectivity(pwmApplication,config));

            if (returnRecords.isEmpty()) {
                returnRecords.addAll(checkLdapServerUrls(config));
            }

            if (returnRecords.isEmpty()) {
                returnRecords.add(new HealthRecord(
                        HealthStatus.GOOD,
                        TOPIC,
                        LocaleHelper.getLocalizedMessage("Health_LDAP_OK",config,Admin.class)
                ));

                final HealthRecord hr = doLdapTestUserCheck(config, pwmApplication);
                if (hr != null) {
                    returnRecords.add(hr);
                }

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

    private static HealthRecord doLdapTestUserCheck(final Configuration config, final PwmApplication pwmApplication)
    {
        final String testUserDN = config.readSettingAsString(PwmSetting.LDAP_TEST_USER_DN);
        final String proxyUserDN = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
        final String proxyUserPW = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD);

        if (testUserDN == null || testUserDN.length() < 1) {
            return null;
        }

        if (proxyUserDN.equalsIgnoreCase(testUserDN)) {
            final String setting1 = PwmSetting.LDAP_TEST_USER_DN.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE)
                    + " -> "
                    + PwmSetting.LDAP_TEST_USER_DN.getLabel(PwmConstants.DEFAULT_LOCALE);
            final String setting2 = PwmSetting.LDAP_PROXY_USER_DN.getLabel(PwmConstants.DEFAULT_LOCALE);

            return new HealthRecord(
                    HealthStatus.WARN,
                    TOPIC,
                    LocaleHelper.getLocalizedMessage(null,"Health_LDAP_ProxyTestSameUser",config,Admin.class,new String[]{setting1,setting2})
            );
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
                return new HealthRecord(HealthStatus.WARN, TOPIC,
                        LocaleHelper.getLocalizedMessage(null,"Health_LDAP_TestUserUnavailable",config,Admin.class,new String[]{e.getMessage()}));
            } catch (Throwable e) {
                return new HealthRecord(HealthStatus.WARN, TOPIC,
                        LocaleHelper.getLocalizedMessage(null,"Health_LDAP_TestUserUnexpected",config,Admin.class,new String[]{e.getMessage()}));
            }

            try {
                theUser.readObjectClass();
            } catch (ChaiException e) {
                return new HealthRecord(HealthStatus.WARN, TOPIC,
                        LocaleHelper.getLocalizedMessage(null,"Health_LDAP_TestUserError",config,Admin.class,new String[]{e.getMessage()}));
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
                        return new HealthRecord(HealthStatus.WARN, TOPIC,
                                LocaleHelper.getLocalizedMessage(null,"Health_LDAP_TestUserPolicyError",config,Admin.class,new String[]{e.getMessage()}));
                    } catch (Exception e) {
                        return new HealthRecord(HealthStatus.WARN, TOPIC,
                                LocaleHelper.getLocalizedMessage(null,"Health_LDAP_TestUserUnexpected",config,Admin.class,new String[]{e.getMessage()}));
                    }
                }
            }

            if (userPassword == null) {
                return new HealthRecord(HealthStatus.WARN, TOPIC,
                        LocaleHelper.getLocalizedMessage(null,"Health_LDAP_TestUserNoTempPass",config,Admin.class));
            }

            try {
                UserStatusHelper.populateUserInfoBean(null, new UserInfoBean(), pwmApplication, PwmConstants.DEFAULT_LOCALE, theUser.getEntryDN(), userPassword, chaiProvider);
            } catch (ChaiUnavailableException e) {
                return new HealthRecord(HealthStatus.WARN, TOPIC, "unable to read test user data: " + e.getMessage());
            } catch (PwmUnrecoverableException e) {
                return new HealthRecord(HealthStatus.WARN, TOPIC, "unable to read test user data: " + e.getMessage());
            }
        } finally {
            if (chaiProvider != null) {
                try { chaiProvider.close(); } catch (Exception e) { /* ignore */ }
            }
        }


        return new HealthRecord(HealthStatus.GOOD, TOPIC, LocaleHelper.getLocalizedMessage("Health_LDAP_TestUserOK",config,Admin.class));
    }


    private static List<HealthRecord> checkLdapServerUrls(final Configuration config)
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

    private static List<HealthRecord> checkBasicLdapConnectivity(final PwmApplication pwmApplication, final Configuration config) {

        final List<HealthRecord> returnRecords = new ArrayList<HealthRecord>();
        ChaiProvider chaiProvider = null;
        try{
            try {
                final String proxyDN = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
                final String proxyPW = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD);
                chaiProvider = Helper.createChaiProvider(config,proxyDN,proxyPW,30*1000);
                final ChaiEntry adminEntry = ChaiFactory.createChaiEntry(config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN),chaiProvider);
                adminEntry.isValid();
            } catch (Exception e) {
                final String errorString = "error connecting to ldap directory: " + e.getMessage();
                returnRecords.add(new HealthRecord(HealthStatus.WARN, TOPIC, errorString));
                pwmApplication.setLastLdapFailure(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,errorString));
                return returnRecords;
            }


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
        } finally {
            if (chaiProvider != null) {
                try { chaiProvider.close(); } catch (Exception e) { /* ignore */ }
            }
        }

        return returnRecords;
    }
}
