/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.ldap;

import com.novell.ldapchai.ChaiEntry;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiSetting;
import com.novell.ldapchai.util.ChaiUtility;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LdapDebugDataGenerator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LdapDebugDataGenerator.class );

    public static List<LdapDebugInfo> makeLdapDebugInfos(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final Configuration configuration,
            final Locale locale
    )

    {
        final List<LdapDebugInfo> returnList = new ArrayList<>();
        for ( final LdapProfile ldapProfile : configuration.getLdapProfiles().values() )
        {
            final LdapDebugInfo ldapDebugInfo = new LdapDebugInfo();
            ldapDebugInfo.setProfileName( ldapProfile.getIdentifier() );
            ldapDebugInfo.setDisplayName( ldapProfile.getDisplayName( locale ) );
            try
            {
                final ChaiProvider chaiProvider = LdapOperationsHelper.createChaiProvider(
                        pwmApplication,
                        null,
                        ldapProfile,
                        configuration,
                        ldapProfile.readSettingAsString( PwmSetting.LDAP_PROXY_USER_DN ),
                        ldapProfile.readSettingAsPassword( PwmSetting.LDAP_PROXY_USER_PASSWORD )
                );
                final Collection<ChaiConfiguration> chaiConfigurations = ChaiUtility.splitConfigurationPerReplica( chaiProvider.getChaiConfiguration(), null );
                final List<LdapDebugServerInfo> ldapDebugServerInfos = new ArrayList<>();
                for ( final ChaiConfiguration chaiConfiguration : chaiConfigurations )
                {
                    final LdapDebugServerInfo ldapDebugServerInfo = new LdapDebugServerInfo();
                    ldapDebugServerInfo.setLdapServerlUrl( chaiConfiguration.getSetting( ChaiSetting.BIND_URLS ) );
                    final ChaiProvider loopProvider = chaiProvider.getProviderFactory().newProvider( chaiConfiguration );

                    {
                        final ChaiEntry rootDSEentry = ChaiUtility.getRootDSE( loopProvider );
                        final Map<String, List<String>> rootDSEdata = LdapOperationsHelper.readAllEntryAttributeValues( rootDSEentry );
                        ldapDebugServerInfo.setRootDseAttributes( rootDSEdata );
                    }

                    {
                        final String proxyUserDN = ldapProfile.readSettingAsString( PwmSetting.LDAP_PROXY_USER_DN );
                        if ( proxyUserDN != null )
                        {
                            ldapDebugServerInfo.setProxyDN( proxyUserDN );
                            final ChaiEntry proxyUserEntry = chaiProvider.getEntryFactory().newChaiEntry( proxyUserDN );
                            if ( proxyUserEntry.exists() )
                            {
                                final Map<String, List<String>> proxyUserData = LdapOperationsHelper.readAllEntryAttributeValues( proxyUserEntry );
                                ldapDebugServerInfo.setProxyUserAttributes( proxyUserData );
                            }
                        }
                    }

                    {

                        final String testUserDN = ldapProfile.readSettingAsString( PwmSetting.LDAP_TEST_USER_DN );
                        if ( testUserDN != null )
                        {
                            ldapDebugServerInfo.setTestUserDN( testUserDN );
                            final ChaiEntry testUserEntry = chaiProvider.getEntryFactory().newChaiEntry( testUserDN );
                            if ( testUserEntry.exists() )
                            {
                                final Map<String, List<String>> testUserdata = LdapOperationsHelper.readAllEntryAttributeValues( testUserEntry );
                                ldapDebugServerInfo.setTestUserAttributes( testUserdata );
                            }
                        }
                    }

                    ldapDebugServerInfos.add( ldapDebugServerInfo );
                }
                ldapDebugInfo.setServerInfo( ldapDebugServerInfos );
                returnList.add( ldapDebugInfo );

            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "error during output of ldap profile debug data profile: " + ldapProfile + ", error: " + e.getMessage() );
            }
        }
        return returnList;
    }

    private Map<String, List<String>> readUserAttributeData( final ChaiProvider chaiProvider, final String userDN )
            throws ChaiUnavailableException, ChaiOperationException
    {
        final ChaiEntry testUserEntry = chaiProvider.getEntryFactory().newChaiEntry( userDN );
        if ( testUserEntry.exists() )
        {
            final Map<String, List<String>> returnData = new LinkedHashMap<>();
            final Map<String, List<String>> testUserdata = LdapOperationsHelper.readAllEntryAttributeValues( testUserEntry );
            testUserdata.put( "dn", Collections.singletonList( userDN ) );
            return returnData;
        }
        return null;
    }


    public static class LdapDebugInfo implements Serializable
    {
        private String profileName;
        private String displayName;
        private List<LdapDebugServerInfo> serverInfo;

        public String getProfileName( )
        {
            return profileName;
        }

        public void setProfileName( final String profileName )
        {
            this.profileName = profileName;
        }

        public String getDisplayName( )
        {
            return displayName;
        }

        public void setDisplayName( final String displayName )
        {
            this.displayName = displayName;
        }

        public List<LdapDebugServerInfo> getServerInfo( )
        {
            return serverInfo;
        }

        public void setServerInfo( final List<LdapDebugServerInfo> serverInfo )
        {
            this.serverInfo = serverInfo;
        }
    }

    public static class LdapDebugServerInfo implements Serializable
    {
        private String ldapServerlUrl;
        private String testUserDN;
        private Map<String, List<String>> testUserAttributes;
        private String proxyDN;
        private Map<String, List<String>> proxyUserAttributes;
        private Map<String, List<String>> rootDseAttributes;

        public String getLdapServerlUrl( )
        {
            return ldapServerlUrl;
        }

        public void setLdapServerlUrl( final String ldapServerlUrl )
        {
            this.ldapServerlUrl = ldapServerlUrl;
        }

        public String getTestUserDN( )
        {
            return testUserDN;
        }

        public void setTestUserDN( final String testUserDN )
        {
            this.testUserDN = testUserDN;
        }

        public Map<String, List<String>> getTestUserAttributes( )
        {
            return testUserAttributes;
        }

        public void setTestUserAttributes( final Map<String, List<String>> testUserAttributes )
        {
            this.testUserAttributes = testUserAttributes;
        }

        public String getProxyDN( )
        {
            return proxyDN;
        }

        public void setProxyDN( final String proxyDN )
        {
            this.proxyDN = proxyDN;
        }

        public Map<String, List<String>> getProxyUserAttributes( )
        {
            return proxyUserAttributes;
        }

        public void setProxyUserAttributes( final Map<String, List<String>> proxyUserAttributes )
        {
            this.proxyUserAttributes = proxyUserAttributes;
        }

        public Map<String, List<String>> getRootDseAttributes( )
        {
            return rootDseAttributes;
        }

        public void setRootDseAttributes( final Map<String, List<String>> rootDseAttributes )
        {
            this.rootDseAttributes = rootDseAttributes;
        }
    }
}
