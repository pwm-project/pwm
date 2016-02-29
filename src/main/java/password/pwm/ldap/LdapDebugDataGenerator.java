/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

package password.pwm.ldap;

import com.novell.ldapchai.ChaiEntry;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiProviderFactory;
import com.novell.ldapchai.provider.ChaiSetting;
import com.novell.ldapchai.util.ChaiUtility;
import password.pwm.bean.SessionLabel;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.*;

public class LdapDebugDataGenerator {
    private static final PwmLogger LOGGER = PwmLogger.forClass(LdapDebugDataGenerator.class);

    public static List<LdapDebugInfo> makeLdapDebugInfos(
            final SessionLabel sessionLabel,
            final Configuration configuration,
            final Locale locale
    )

    {
        final List<LdapDebugInfo> returnList = new ArrayList<>();
        for (LdapProfile ldapProfile : configuration.getLdapProfiles().values()) {
            final LdapDebugInfo ldapDebugInfo = new LdapDebugInfo();
            ldapDebugInfo.setProfileName(ldapProfile.getIdentifier());
            ldapDebugInfo.setDisplayName(ldapProfile.getDisplayName(locale));
            try {
                final ChaiProvider chaiProvider = LdapOperationsHelper.createChaiProvider(
                        null,
                        ldapProfile,
                        configuration,
                        ldapProfile.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN),
                        ldapProfile.readSettingAsPassword(PwmSetting.LDAP_PROXY_USER_PASSWORD)
                );
                final Collection<ChaiConfiguration> chaiConfigurations = ChaiUtility.splitConfigurationPerReplica(chaiProvider.getChaiConfiguration(), null);
                final List<LdapDebugServerInfo> ldapDebugServerInfos = new ArrayList<>();
                for (final ChaiConfiguration chaiConfiguration : chaiConfigurations) {
                    final LdapDebugServerInfo ldapDebugServerInfo = new LdapDebugServerInfo();
                    ldapDebugServerInfo.setLdapServerlUrl(chaiConfiguration.getSetting(ChaiSetting.BIND_URLS));
                    final ChaiProvider loopProvider = ChaiProviderFactory.createProvider(chaiConfiguration);

                    {
                        final ChaiEntry rootDSEentry = ChaiUtility.getRootDSE(loopProvider);
                        final Map<String, List<String>> rootDSEdata = LdapOperationsHelper.readAllEntryAttributeValues(rootDSEentry);
                        ldapDebugServerInfo.setRootDseAttributes(rootDSEdata);
                    }

                    {
                        final String proxyUserDN = ldapProfile.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
                        if (proxyUserDN != null) {
                            ldapDebugServerInfo.setProxyDN(proxyUserDN);
                            final ChaiEntry proxyUserEntry = ChaiFactory.createChaiEntry(proxyUserDN, chaiProvider);
                            if (proxyUserEntry.isValid()) {
                                final Map<String, List<String>> proxyUserData = LdapOperationsHelper.readAllEntryAttributeValues(proxyUserEntry);
                                ldapDebugServerInfo.setProxyUserAttributes(proxyUserData);
                            }
                        }
                    }

                    {

                        final String testUserDN = ldapProfile.readSettingAsString(PwmSetting.LDAP_TEST_USER_DN);
                        if (testUserDN != null) {
                            ldapDebugServerInfo.setTestUserDN(testUserDN);
                            final ChaiEntry testUserEntry = ChaiFactory.createChaiEntry(testUserDN, chaiProvider);
                            if (testUserEntry.isValid()) {
                                final Map<String, List<String>> testUserdata = LdapOperationsHelper.readAllEntryAttributeValues(testUserEntry);
                                ldapDebugServerInfo.setTestUserAttributes(testUserdata);
                            }
                        }
                    }

                    ldapDebugServerInfos.add(ldapDebugServerInfo);
                }
                ldapDebugInfo.setServerInfo(ldapDebugServerInfos);
                returnList.add(ldapDebugInfo);

            } catch (Exception e) {
                LOGGER.error("error during output of ldap profile debug data profile: " + ldapProfile + ", error: " + e.getMessage());
            }
        }
        return returnList;
    }

    private Map<String,List<String>> readUserAttributeData(final ChaiProvider chaiProvider, final String userDN)
            throws ChaiUnavailableException, ChaiOperationException
    {
        final ChaiEntry testUserEntry = ChaiFactory.createChaiEntry(userDN, chaiProvider);
        if (testUserEntry.isValid()) {
            final Map<String,List<String>> returnData = new LinkedHashMap<>();
            final Map<String, List<String>> testUserdata = LdapOperationsHelper.readAllEntryAttributeValues(testUserEntry);
            testUserdata.put("dn",Collections.singletonList(userDN));
            return returnData;
        }
        return null;
    }


    public static class LdapDebugInfo implements Serializable {
        private String profileName;
        private String displayName;
        private List<LdapDebugServerInfo> serverInfo;

        public String getProfileName() {
            return profileName;
        }

        public void setProfileName(String profileName) {
            this.profileName = profileName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public List<LdapDebugServerInfo> getServerInfo() {
            return serverInfo;
        }

        public void setServerInfo(List<LdapDebugServerInfo> serverInfo) {
            this.serverInfo = serverInfo;
        }
    }

    public static class LdapDebugServerInfo implements Serializable {
        private String ldapServerlUrl;
        private String testUserDN;
        private Map<String, List<String>> testUserAttributes;
        private String proxyDN;
        private Map<String, List<String>> proxyUserAttributes;
        private Map<String, List<String>> rootDseAttributes;

        public String getLdapServerlUrl() {
            return ldapServerlUrl;
        }

        public void setLdapServerlUrl(String ldapServerlUrl) {
            this.ldapServerlUrl = ldapServerlUrl;
        }

        public String getTestUserDN() {
            return testUserDN;
        }

        public void setTestUserDN(String testUserDN) {
            this.testUserDN = testUserDN;
        }

        public Map<String, List<String>> getTestUserAttributes() {
            return testUserAttributes;
        }

        public void setTestUserAttributes(Map<String, List<String>> testUserAttributes) {
            this.testUserAttributes = testUserAttributes;
        }

        public String getProxyDN() {
            return proxyDN;
        }

        public void setProxyDN(String proxyDN) {
            this.proxyDN = proxyDN;
        }

        public Map<String, List<String>> getProxyUserAttributes() {
            return proxyUserAttributes;
        }

        public void setProxyUserAttributes(Map<String, List<String>> proxyUserAttributes) {
            this.proxyUserAttributes = proxyUserAttributes;
        }

        public Map<String, List<String>> getRootDseAttributes() {
            return rootDseAttributes;
        }

        public void setRootDseAttributes(Map<String, List<String>> rootDseAttributes) {
            this.rootDseAttributes = rootDseAttributes;
        }
    }
}
