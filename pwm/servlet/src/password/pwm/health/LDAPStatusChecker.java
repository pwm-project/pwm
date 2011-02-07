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

import com.novell.ldapchai.ChaiEntry;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiPasswordPolicyException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.ContextManager;
import password.pwm.Helper;
import password.pwm.PwmPasswordPolicy;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.util.PwmLogger;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.wordlist.SeedlistManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LDAPStatusChecker implements HealthChecker {

    final private static PwmLogger LOGGER = PwmLogger.getLogger(LDAPStatusChecker.class);
    final private static String TOPIC = "LDAP Connectivity";

    public List<HealthRecord> doHealthCheck(final ContextManager contextManager) {
        final List<HealthRecord> returnRecords = new ArrayList<HealthRecord>();
        final StoredConfiguration storedConfig = contextManager.getConfigReader().getStoredConfiguration();


        { // check ldap server
            final ErrorInformation result = doLdapStatusCheck(storedConfig);
            if (result.getError().equals(PwmError.CONFIG_LDAP_SUCCESS)) {
                returnRecords.add(new HealthRecord(HealthRecord.HealthStatus.GOOD, TOPIC, "All configured LDAP servers are reachable"));
            } else {
                returnRecords.add(new HealthRecord(HealthRecord.HealthStatus.WARN, TOPIC, result.toDebugStr()));
                return returnRecords;
            }
        }

        { // check test user
            final HealthRecord hr = doLdapTestUserCheck(storedConfig, contextManager);
            if (hr != null) {
                returnRecords.add(hr);
            }
        }


        return returnRecords;
    }

    private static HealthRecord doLdapTestUserCheck(final StoredConfiguration storedconfiguration, final ContextManager contextManager) {
        final Configuration config = new Configuration(storedconfiguration);
        final String testUserDN = config.readSettingAsString(PwmSetting.LDAP_TEST_USER_DN);
        final String proxyUserDN = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
        final String proxyUserPW = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD);

        if (testUserDN == null || testUserDN.length() < 1) {
            return new HealthRecord(HealthRecord.HealthStatus.CAUTION, TOPIC, "LDAP test user is not configured");
        }

        final ChaiUser theUser;
        try {
            final ChaiProvider chaiProvider = Helper.createChaiProvider(
                    config,
                    proxyUserDN,
                    proxyUserPW,
                    config.readSettingAsInt(PwmSetting.LDAP_PROXY_IDLE_TIMEOUT));

            theUser = ChaiFactory.createChaiUser(testUserDN, chaiProvider);
            if (!theUser.isValid()) {
                return new HealthRecord(HealthRecord.HealthStatus.WARN, TOPIC, "LDAP test user '" + testUserDN + "' does not exist");
            }

        } catch (ChaiUnavailableException e) {
            return new HealthRecord(HealthRecord.HealthStatus.WARN, TOPIC, "LDAP unavailable error while testing ldap test user: " + e.getMessage());
        } catch (Throwable e) {
            return new HealthRecord(HealthRecord.HealthStatus.WARN, TOPIC, "unexpected error while testing ldap test user: " + e.getMessage());
        }


        String userPassword = null;
        boolean readPassword = false;
        {
            try {
                final String passwordFromLdap = theUser.readPassword();
                if (passwordFromLdap != null && passwordFromLdap.length() > 0) {
                    userPassword = passwordFromLdap;
                    readPassword = true;
                }
            } catch (Exception e) {
                LOGGER.trace("error retrieving user password from directory, this is probably okay; " + e.getMessage());
            }

            if (userPassword == null) {
                try {
                    final Locale locale = Locale.getDefault();
                    final PwmPasswordPolicy passwordPolicy = PwmPasswordPolicy.createPwmPasswordPolicy(config, locale, theUser, null);
                    final SeedlistManager seedlistManager = contextManager.getSeedlistManager();
                    final String newPassword = RandomPasswordGenerator.createRandomPassword(null, passwordPolicy, seedlistManager, contextManager);
                    theUser.setPassword(newPassword);
                    userPassword = newPassword;
                } catch (ChaiPasswordPolicyException e) {
                    return new HealthRecord(HealthRecord.HealthStatus.WARN, TOPIC, "unable to read test user password, and unexpected error while writing test user temporary random password: " + e.getMessage());
                } catch (ChaiException e) {
                    return new HealthRecord(HealthRecord.HealthStatus.WARN, TOPIC, "unable to read test user password, and unexpected error while writing test user temporary random password: " + e.getMessage());
                }
            }
        }

        if (userPassword == null) {
            return new HealthRecord(HealthRecord.HealthStatus.WARN, TOPIC, "unable to read test user password, and unable to set test user password to temporary random value");
        }

        return new HealthRecord(HealthRecord.HealthStatus.GOOD, TOPIC, "LDAP test user account is functioning normally");
    }


    private static ErrorInformation doLdapStatusCheck(final StoredConfiguration storedconfiguration) {
        final Configuration config = new Configuration(storedconfiguration);

        ChaiProvider chaiProvider = null;
        try {
            chaiProvider = getChaiProviderForTesting(config);
            final String contextlessRootSettingName = PwmSetting.LDAP_CONTEXTLESS_ROOT.getCategory().getLabel(Locale.getDefault()) + "-" + PwmSetting.LDAP_CONTEXTLESS_ROOT.getLabel(Locale.getDefault());
            try {
                final ChaiEntry contextlessRootEntry = ChaiFactory.createChaiEntry(config.readSettingAsString(PwmSetting.LDAP_CONTEXTLESS_ROOT), chaiProvider);
                if (!contextlessRootEntry.isValid()) {
                    final String errorString = "setting '" + contextlessRootSettingName + "' value does not appear to be correct";
                    return new ErrorInformation(PwmError.CONFIG_LDAP_FAILURE, errorString, errorString);
                }
            } catch (Exception e) {
                final String errorString = "error verifying setting '" + contextlessRootSettingName + "' " + e.getMessage();
                return new ErrorInformation(PwmError.CONFIG_LDAP_FAILURE, errorString, errorString);
            }

            return new ErrorInformation(PwmError.CONFIG_LDAP_SUCCESS);
        } catch (Exception e) {
            final String errorString = "error connecting to ldap server: " + e.getMessage();
            return new ErrorInformation(PwmError.CONFIG_LDAP_FAILURE, errorString, errorString);
        } finally {
            if (chaiProvider != null) {
                try {
                    chaiProvider.close();
                } catch (Exception e) {
                    // don't care.
                }
            }
        }
    }


    private static ChaiProvider getChaiProviderForTesting(final Configuration config)
            throws ChaiUnavailableException {
        ChaiProvider chaiProvider = null;
        chaiProvider = Helper.createChaiProvider(
                config,
                config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN),
                config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD),
                config.readSettingAsInt(PwmSetting.LDAP_PROXY_IDLE_TIMEOUT));
        chaiProvider.getDirectoryVendor();

        return chaiProvider;
    }
}
