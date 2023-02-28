/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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
import com.novell.ldapchai.ChaiEntryFactory;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiProviderFactory;
import com.novell.ldapchai.provider.DirectoryVendor;
import com.novell.ldapchai.provider.SearchScope;
import com.novell.ldapchai.util.ChaiUtility;
import com.novell.ldapchai.util.SearchHelper;
import lombok.Builder;
import lombok.Value;
import password.pwm.DomainProperty;
import password.pwm.bean.DomainID;
import password.pwm.bean.ProfileID;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.config.DomainConfig;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.CollectorUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

public class LdapBrowser
{
    public static final String PARAM_DN = "dn";
    public static final String PARAM_PROFILE = "profile";

    private static final String ATTR_SUBORDINATE_COUNT = "subordinateCount";

    private static final PwmLogger LOGGER = PwmLogger.forClass( LdapBrowser.class );
    private final StoredConfiguration storedConfiguration;

    private final SessionLabel sessionLabel;
    private final ChaiProviderFactory chaiProviderFactory;
    private final Map<ProfileID, ChaiProvider> providerCache = new HashMap<>();

    private enum DnType
    {
        navigable,
        selectable,
    }

    public LdapBrowser(
            final SessionLabel sessionLabel,
            final ChaiProviderFactory chaiProviderFactory,
            final StoredConfiguration storedConfiguration
    )
    {
        this.sessionLabel = sessionLabel;
        this.chaiProviderFactory = chaiProviderFactory;
        this.storedConfiguration = storedConfiguration;
    }

    public LdapBrowseResult doBrowse(
            final DomainID domainID,
            final ProfileID profile,
            final String dn
    )
            throws PwmUnrecoverableException
    {
        try
        {
            return doBrowseImpl( domainID, profile, dn );
        }
        catch ( final ChaiUnavailableException | ChaiOperationException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
        catch ( final Exception e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_LDAP_DATA_ERROR, e.getMessage() ) );
        }
    }

    public void close( )
    {
        for ( final ChaiProvider chaiProvider : providerCache.values() )
        {
            chaiProvider.close();
        }
        providerCache.clear();
    }

    private LdapBrowseResult doBrowseImpl(
            final DomainID domainID,
            final ProfileID profileID,
            final String dn
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, ChaiOperationException
    {
        final LdapBrowseResult.LdapBrowseResultBuilder result = LdapBrowseResult.builder();

        updateBrowseResultChildren( domainID, profileID, dn, result );

        result.dn( dn );
        result.profileID( profileID );
        final DomainConfig domainConfig = AppConfig.forStoredConfig( storedConfiguration ).getDomainConfigs().get( domainID );

        if ( domainConfig.getLdapProfiles().size() > 1 )
        {
            result.profileList( new ArrayList<>( domainConfig.getLdapProfiles().keySet() ) );
        }

        if ( adRootDNList( domainID, profileID ).contains( dn ) )
        {
            result.parentDN( "" );
        }
        else if ( StringUtil.notEmpty( dn ) )
        {
            final ChaiEntry dnEntry = getChaiProvider( domainID, profileID ).getEntryFactory().newChaiEntry( dn );
            final ChaiEntry parentEntry = dnEntry.getParentEntry();
            if ( parentEntry == null )
            {
                result.parentDN( "" );
            }
            else
            {
                result.parentDN( parentEntry.getEntryDN() );
            }
        }

        return result.build();
    }

    private void updateBrowseResultChildren(
            final DomainID domainID,
            final ProfileID profileID,
            final String dn,
            final LdapBrowseResult.LdapBrowseResultBuilder result
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, ChaiOperationException
    {
        final Map<String, DnType> childDNs = new TreeMap<>( getChildEntries( domainID, profileID, dn ) );

        final List<DNInformation> navigableDNs = new ArrayList<>();
        final List<DNInformation> selectableDNs = new ArrayList<>();
        for ( final Map.Entry<String, DnType> entry : childDNs.entrySet() )
        {
            final String childDN = entry.getKey();
            final DNInformation dnInformation = new DNInformation( rdnNameFromDN( childDN ), childDN );

            if ( entry.getValue() == DnType.navigable )
            {
                navigableDNs.add( dnInformation );
            }
            else
            {
                selectableDNs.add( dnInformation );
            }
        }
        result.navigableDNlist( navigableDNs );
        result.selectableDNlist( selectableDNs );
        result.maxResults( childDNs.size() >= getMaxSizeLimit( domainID, storedConfiguration ) );
    }

    private ChaiProvider getChaiProvider( final DomainID domainID, final ProfileID profile )
            throws PwmUnrecoverableException
    {
        if ( !providerCache.containsKey( profile ) )
        {
            final DomainConfig domainConfig = AppConfig.forStoredConfig( storedConfiguration ).getDomainConfigs().get( domainID );
            final LdapProfile ldapProfile = domainConfig.getLdapProfiles().get( profile );
            final ChaiProvider chaiProvider = LdapOperationsHelper.openProxyChaiProvider( chaiProviderFactory, sessionLabel, ldapProfile, domainConfig, null );
            providerCache.put( profile, chaiProvider );
        }
        return providerCache.get( profile );
    }

    private static int getMaxSizeLimit(
            final DomainID domainID,
            final StoredConfiguration storedConfiguration
    )
    {
        final DomainConfig domainConfig = AppConfig.forStoredConfig( storedConfiguration ).getDomainConfigs().get( domainID );
        return Integer.parseInt( domainConfig.readDomainProperty( DomainProperty.LDAP_BROWSER_MAX_ENTRIES ) );
    }

    private Map<String, DnType> getChildEntries(
            final DomainID domainID,
            final ProfileID profile,
            final String dn
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, ChaiOperationException
    {

        final ChaiProvider chaiProvider = getChaiProvider( domainID, profile );

        if ( StringUtil.isEmpty( dn ) && chaiProvider.getDirectoryVendor() == DirectoryVendor.ACTIVE_DIRECTORY )
        {
            return Collections.unmodifiableMap( adRootDNList( domainID, profile ).stream().collect( CollectorUtil.toLinkedMap(
                    Function.identity(),
                    rootDN -> DnType.navigable
            ) ) );
        }

        final Set<String> results = doLdapSearch( domainID, dn, chaiProvider );

        final HashMap<String, DnType> returnMap = new LinkedHashMap<>( results.size() );
        for ( final String resultDN : results )
        {
            final DnType dnType = dnHasSubordinates( resultDN, chaiProvider );
            returnMap.put( resultDN, dnType );
        }

        return Collections.unmodifiableMap( returnMap );
    }

    private DnType dnHasSubordinates(
            final String dn,
            final ChaiProvider chaiProvider
    )
            throws ChaiUnavailableException, ChaiOperationException
    {
        final ChaiEntry chaiEntry = ChaiEntryFactory.newChaiFactory( chaiProvider ).newChaiEntry( dn );
        return chaiEntry.hasChildren()
                ? DnType.navigable
                : DnType.selectable;
    }

    private Set<String> doLdapSearch(
            final DomainID domainID,
            final String dn,
            final ChaiProvider chaiProvider
    )
            throws ChaiUnavailableException, ChaiOperationException
    {
        final SearchHelper searchHelper = new SearchHelper();
        searchHelper.setFilter( SearchHelper.DEFAULT_FILTER );
        searchHelper.setAttributes( Collections.emptyList() );
        searchHelper.setMaxResults( getMaxSizeLimit( domainID, storedConfiguration ) );
        searchHelper.setSearchScope( SearchScope.ONE );

        return chaiProvider.search( dn, searchHelper ).keySet();
    }

    private Set<String> adRootDNList( final DomainID domainID, final ProfileID profile )
            throws ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException
    {
        final ChaiProvider chaiProvider = getChaiProvider( domainID, profile );
        final Set<String> adRootValues = new HashSet<>();
        if ( chaiProvider.getDirectoryVendor() == DirectoryVendor.ACTIVE_DIRECTORY )
        {
            final ChaiEntry chaiEntry = ChaiUtility.getRootDSE( chaiProvider );
            adRootValues.addAll( chaiEntry.readMultiStringAttribute( "namingContexts" ) );
        }
        return adRootValues;
    }

    private static String rdnNameFromDN( final String dn )
    {
        int end = dn.indexOf( ',' );
        if ( end == -1 )
        {
            end = dn.length();
        }

        return dn.substring( 0, end );
    }

    @Value
    @Builder
    public static class LdapBrowseResult
    {
        private String dn;
        private ProfileID profileID;
        private String parentDN;
        private List<ProfileID> profileList;
        private boolean maxResults;

        private List<DNInformation> navigableDNlist;
        private List<DNInformation> selectableDNlist;
    }

    @Value
    public static class DNInformation
    {
        private final String entryName;
        private final String dn;
    }
}
