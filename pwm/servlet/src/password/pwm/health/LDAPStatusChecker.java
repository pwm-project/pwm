/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.TimeDuration;
import password.pwm.wordlist.SeedlistManager;

import java.util.*;

public class LDAPStatusChecker implements HealthChecker {

    final private static PwmLogger LOGGER = PwmLogger.getLogger(LDAPStatusChecker.class);
    final private static String TOPIC = "LDAP";

    public List<HealthRecord> doHealthCheck(final PwmApplication pwmApplication)
    {
        final List<HealthRecord> returnRecords = new ArrayList<HealthRecord>();
        final StoredConfiguration storedConfig = pwmApplication.getConfigReader().getStoredConfiguration();

        { // check ldap server
            final ErrorInformation result = doLdapStatusCheck(storedConfig);
            if (result.getError().equals(PwmError.CONFIG_LDAP_SUCCESS)) {
                returnRecords.add(new HealthRecord(HealthStatus.GOOD, TOPIC, "All configured LDAP servers are reachable"));
            } else {
                returnRecords.add(new HealthRecord(HealthStatus.WARN, TOPIC, result.toDebugStr()));
                pwmApplication.setLastLdapFailure(result);
                return returnRecords;
            }
        }

        { // check recent ldap status
            final ErrorInformation errorInfo = pwmApplication.getLastLdapFailure();
            if (errorInfo != null) {
                final TimeDuration errorAge = TimeDuration.fromCurrent(errorInfo.getDate().getTime());

                if (errorAge.isShorterThan(TimeDuration.DAY)) {
                    returnRecords.add(new HealthRecord(HealthStatus.CAUTION, TOPIC, "LDAP server was recently unavailable (" + errorAge.asLongString() + " ago at " + errorInfo.getDate().toString()+ "): " + errorInfo.toDebugStr()));
                }
            }
        }

        { // check test user
            final HealthRecord hr = doLdapTestUserCheck(storedConfig, pwmApplication);
            if (hr != null) {
                returnRecords.add(hr);
            }
        }

        return returnRecords;
    }

    private static HealthRecord doLdapTestUserCheck(final StoredConfiguration storedconfiguration, final PwmApplication pwmApplication)
    {
        final Configuration config = new Configuration(storedconfiguration);
        final String testUserDN = config.readSettingAsString(PwmSetting.LDAP_TEST_USER_DN);
        final String proxyUserDN = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
        final String proxyUserPW = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD);

        if (testUserDN == null || testUserDN.length() < 1) {
            return null;
        }

        if (proxyUserDN.equalsIgnoreCase(testUserDN)) {
            final StringBuilder errorMsg = new StringBuilder();
            errorMsg.append(PwmSetting.LDAP_TEST_USER_DN.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE));
            errorMsg.append(" -> ");
            errorMsg.append(PwmSetting.LDAP_TEST_USER_DN.getLabel(PwmConstants.DEFAULT_LOCALE));
            errorMsg.append(" setting is the same value as the ");
            errorMsg.append(PwmSetting.LDAP_PROXY_USER_DN.getLabel(PwmConstants.DEFAULT_LOCALE));
            errorMsg.append(" setting");

            return new HealthRecord(HealthStatus.WARN, TOPIC, errorMsg.toString());

        }

        final ChaiUser theUser;
        try {
            final ChaiProvider chaiProvider = Helper.createChaiProvider(
                    config,
                    proxyUserDN,
                    proxyUserPW);

            theUser = ChaiFactory.createChaiUser(testUserDN, chaiProvider);

        } catch (ChaiUnavailableException e) {
            return new HealthRecord(HealthStatus.WARN, TOPIC, "LDAP unavailable error while testing ldap test user: " + e.getMessage());
        } catch (Throwable e) {
            return new HealthRecord(HealthStatus.WARN, TOPIC, "unexpected error while testing ldap test user: " + e.getMessage());
        }

        try {
            theUser.readObjectClass();
        } catch (ChaiException e) {
            return new HealthRecord(HealthStatus.WARN, TOPIC, "error verifying test user account: " + e.getMessage());
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
                    final PwmPasswordPolicy passwordPolicy = PwmPasswordPolicy.createPwmPasswordPolicy(null, config, locale, theUser);
                    final SeedlistManager seedlistManager = pwmApplication.getSeedlistManager();
                    final String newPassword = RandomPasswordGenerator.createRandomPassword(null, passwordPolicy, seedlistManager, pwmApplication);
                    theUser.setPassword(newPassword);
                    userPassword = newPassword;
                } catch (ChaiPasswordPolicyException e) {
                    return new HealthRecord(HealthStatus.WARN, TOPIC, "unexpected policy error while writing test user temporary random password: " + e.getMessage());
                } catch (ChaiException e) {
                    return new HealthRecord(HealthStatus.WARN, TOPIC, "unexpected ldap error while writing test user temporary random password: " + e.getMessage());
                } catch (PwmUnrecoverableException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
        }

        if (userPassword == null) {
            return new HealthRecord(HealthStatus.WARN, TOPIC, "unable to read test user password, and unable to set test user password to temporary random value");
        }

        return new HealthRecord(HealthStatus.GOOD, TOPIC, "LDAP test user account is functioning normally");
    }


    private static ErrorInformation doLdapStatusCheck(final StoredConfiguration storedconfiguration)
    {
        final List<Configuration> configs = generateConfigPerLdapURL(storedconfiguration);
        ChaiProvider chaiProvider = null;
        try {
            String loopUrl = "";
            try {
                for (final Configuration config : configs) {
                    loopUrl = config.readSettingAsStringArray(PwmSetting.LDAP_SERVER_URLS).get(0);
                    chaiProvider = getChaiProviderForTesting(config);
                    final String proxyDN = storedconfiguration.readSetting(PwmSetting.LDAP_PROXY_USER_DN);
                    final ChaiUser proxyUser = ChaiFactory.createChaiUser(proxyDN, chaiProvider);
                    proxyUser.isValid();
                }
            } catch (Exception e) {
                final String errorString = "error connecting to ldap server '" + loopUrl + "': " + e.getMessage();
                return new ErrorInformation(PwmError.CONFIG_LDAP_FAILURE, errorString);
            }


            try {
                final String usernameContext = storedconfiguration.readSetting(PwmSetting.LDAP_CONTEXTLESS_ROOT);
                final ChaiEntry contextEntry = ChaiFactory.createChaiEntry(usernameContext,chaiProvider);
                final Set<String> objectClasses = contextEntry.readObjectClass();
                if (objectClasses == null || objectClasses.isEmpty()) {
                    final String errorString = "ldap root context setting is not valid";
                    return new ErrorInformation(PwmError.CONFIG_LDAP_FAILURE, errorString);
                }
            } catch (Exception e) {
                final String errorString = "error validating root ldap context setting: " + e.getMessage();
                return new ErrorInformation(PwmError.CONFIG_LDAP_FAILURE, errorString);
            }
        } finally {
            if (chaiProvider != null) {
                try {
                    chaiProvider.close();
                } catch (Exception e) {
                    // don't care.
                }
            }
        }

        return new ErrorInformation(PwmError.CONFIG_LDAP_SUCCESS);
    }


    private static ChaiProvider getChaiProviderForTesting(final Configuration config)
            throws ChaiUnavailableException {
        final ChaiProvider chaiProvider = Helper.createChaiProvider(
                config,
                config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN),
                config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD));

        chaiProvider.getDirectoryVendor();

        return chaiProvider;
    }

    private static List<Configuration> generateConfigPerLdapURL(final StoredConfiguration storedconfiguration)
    {
        final List<String> serverURLs = storedconfiguration.readStringArraySetting(PwmSetting.LDAP_SERVER_URLS);
        final List<Configuration> configs = new ArrayList<Configuration>();
        for (final String loopURL : serverURLs) {

            try {
                final StoredConfiguration loopConfig = (StoredConfiguration)storedconfiguration.clone();
                loopConfig.writeStringArraySetting(PwmSetting.LDAP_SERVER_URLS, Collections.singletonList(loopURL));
                configs.add(new Configuration(loopConfig));
            } catch (CloneNotSupportedException e) {
                LOGGER.error("unexpected internal error: " + e.getMessage(),e);
            }
        }
        return configs;
    }

}
