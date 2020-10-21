/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiSetting;
import com.novell.ldapchai.util.ChaiUtility;
import lombok.Builder;
import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.PwmException;
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
            final List<LdapDebugServerInfo> ldapDebugServerInfos = new ArrayList<>();

            try
            {
                final ChaiConfiguration profileChaiConf = LdapOperationsHelper.createChaiConfiguration( configuration, ldapProfile );
                final Collection<ChaiConfiguration> chaiConfigurations = ChaiUtility.splitConfigurationPerReplica( profileChaiConf, null );

                for ( final ChaiConfiguration chaiConfiguration : chaiConfigurations )
                {
                    try
                    {
                        final ChaiProvider chaiProvider = LdapOperationsHelper.createChaiProvider(
                                pwmApplication,
                                sessionLabel,
                                ldapProfile,
                                configuration,
                                ldapProfile.readSettingAsString( PwmSetting.LDAP_PROXY_USER_DN ),
                                ldapProfile.readSettingAsPassword( PwmSetting.LDAP_PROXY_USER_PASSWORD )
                        );

                        final LdapDebugServerInfo ldapDebugServerInfo = makeLdapDebugServerInfo( chaiConfiguration, chaiProvider, ldapProfile );
                        ldapDebugServerInfos.add( ldapDebugServerInfo );
                    }
                    catch ( final PwmException | ChaiException e )
                    {
                        LOGGER.error( () -> "error during output of ldap profile debug data profile: "
                                + ldapProfile + ", error: " + e.getMessage() );
                    }
                }

                final LdapDebugInfo ldapDebugInfo = LdapDebugInfo.builder()
                        .profileName( ldapProfile.getIdentifier() )
                        .displayName( ldapProfile.getDisplayName( locale ) )
                        .serverInfo( ldapDebugServerInfos )
                        .build();

                returnList.add( ldapDebugInfo );

            }
            catch ( final PwmException e )
            {
                LOGGER.error( () -> "error during output of ldap profile debug data profile: "
                        + ldapProfile + ", error: " + e.getMessage() );
            }
        }
        return returnList;
    }

    private static LdapDebugDataGenerator.LdapDebugServerInfo makeLdapDebugServerInfo(
            final ChaiConfiguration chaiConfiguration,
            final ChaiProvider chaiProvider,
            final LdapProfile ldapProfile
    )
            throws ChaiUnavailableException, ChaiOperationException
    {
        final LdapDebugServerInfo.LdapDebugServerInfoBuilder builder = LdapDebugServerInfo.builder();

        builder.ldapServerlUrl( chaiConfiguration.getSetting( ChaiSetting.BIND_URLS ) );
        final ChaiProvider loopProvider = chaiProvider.getProviderFactory().newProvider( chaiConfiguration );

        {
            final ChaiEntry rootDSEentry = ChaiUtility.getRootDSE( loopProvider );
            final Map<String, List<String>> rootDSEdata = LdapOperationsHelper.readAllEntryAttributeValues( rootDSEentry );
            builder.rootDseAttributes( rootDSEdata );
        }

        {
            final String proxyUserDN = ldapProfile.readSettingAsString( PwmSetting.LDAP_PROXY_USER_DN );
            if ( proxyUserDN != null )
            {
                builder.proxyDN( proxyUserDN );
                final ChaiEntry proxyUserEntry = chaiProvider.getEntryFactory().newChaiEntry( proxyUserDN );
                if ( proxyUserEntry.exists() )
                {
                    final Map<String, List<String>> proxyUserData = LdapOperationsHelper.readAllEntryAttributeValues( proxyUserEntry );
                    builder.proxyUserAttributes( proxyUserData );
                }
            }
        }

        {

            final String testUserDN = ldapProfile.readSettingAsString( PwmSetting.LDAP_TEST_USER_DN );
            if ( testUserDN != null )
            {
                builder.testUserDN( testUserDN );
                final ChaiEntry testUserEntry = chaiProvider.getEntryFactory().newChaiEntry( testUserDN );
                if ( testUserEntry.exists() )
                {
                    final Map<String, List<String>> testUserdata = LdapOperationsHelper.readAllEntryAttributeValues( testUserEntry );
                    builder.testUserAttributes( testUserdata );
                }
            }
        }

        return builder.build();
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

    @Value
    @Builder
    public static class LdapDebugInfo implements Serializable
    {
        private String profileName;
        private String displayName;
        private List<LdapDebugServerInfo> serverInfo;
    }

    @Value
    @Builder
    public static class LdapDebugServerInfo implements Serializable
    {
        private String ldapServerlUrl;
        private String testUserDN;
        private Map<String, List<String>> testUserAttributes;
        private String proxyDN;
        private Map<String, List<String>> proxyUserAttributes;
        private Map<String, List<String>> rootDseAttributes;
    }
}
