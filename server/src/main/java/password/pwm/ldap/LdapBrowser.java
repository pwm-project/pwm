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
import password.pwm.AppProperty;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.config.DomainConfig;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class LdapBrowser
{
    public static final String PARAM_DN = "dn";
    public static final String PARAM_PROFILE = "profile";

    private static final PwmLogger LOGGER = PwmLogger.forClass( LdapBrowser.class );
    private final StoredConfiguration storedConfiguration;

    private final SessionLabel sessionLabel;
    private final ChaiProviderFactory chaiProviderFactory;
    private final Map<String, ChaiProvider> providerCache = new HashMap<>();

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
            final String profile,
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
            final String profileID,
            final String dn
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, ChaiOperationException
    {
        final LdapBrowseResult.LdapBrowseResultBuilder result = LdapBrowseResult.builder();

        updateBrowseResultChildren( domainID, profileID, dn, result );

        result.dn( dn );
        result.profileID( profileID );
        final DomainConfig domainConfig = new AppConfig( storedConfiguration ).getDomainConfigs().get( domainID );

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
            final String profileID,
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
            final DNInformation dnInformation = new DNInformation( entryNameFromDN( childDN ), childDN );

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
        result.maxResults( childDNs.size() >= getMaxSizeLimit() );
    }

    private ChaiProvider getChaiProvider( final DomainID domainID, final String profile ) throws PwmUnrecoverableException
    {
        if ( !providerCache.containsKey( profile ) )
        {
            final DomainConfig domainConfig = new AppConfig( storedConfiguration ).getDomainConfigs().get( domainID );
            final LdapProfile ldapProfile = domainConfig.getLdapProfiles().get( profile );
            final ChaiProvider chaiProvider = LdapOperationsHelper.openProxyChaiProvider( chaiProviderFactory, sessionLabel, ldapProfile, domainConfig, null );
            providerCache.put( profile, chaiProvider );
        }
        return providerCache.get( profile );
    }

    private int getMaxSizeLimit( )
    {
        final AppConfig appConfig = new AppConfig( storedConfiguration );
        return Integer.parseInt( appConfig.readAppProperty( AppProperty.LDAP_BROWSER_MAX_ENTRIES ) );
    }

    private Map<String, DnType> getChildEntries(
            final DomainID domainID,
            final String profile,
            final String dn
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, ChaiOperationException
    {

        final HashMap<String, DnType> returnMap = new HashMap<>();
        final ChaiProvider chaiProvider = getChaiProvider( domainID, profile );
        if ( StringUtil.isEmpty( dn ) && chaiProvider.getDirectoryVendor() == DirectoryVendor.ACTIVE_DIRECTORY )
        {
            final Set<String> adRootDNList = adRootDNList( domainID, profile );
            for ( final String rootDN : adRootDNList )
            {
                returnMap.put( rootDN, DnType.navigable );
            }
        }
        else
        {

            final Map<String, Map<String, List<String>>> results;
            {
                final SearchHelper searchHelper = new SearchHelper();
                searchHelper.setFilter( "(objectclass=*)" );
                searchHelper.setMaxResults( getMaxSizeLimit() );
                searchHelper.setAttributes( "subordinateCount" );
                searchHelper.setSearchScope( SearchScope.ONE );
                results = chaiProvider.searchMultiValues( dn, searchHelper );
            }

            for ( final Map.Entry<String, Map<String, List<String>>> entry : results.entrySet() )
            {
                final String resultDN = entry.getKey();
                final Map<String, List<String>> attributeResults = entry.getValue();
                boolean hasSubs = false;
                if ( attributeResults.containsKey( "subordinateCount" ) )
                {
                    // only eDir actually returns this operational attribute
                    final int subordinateCount = Integer.parseInt( attributeResults.get( "subordinateCount" ).iterator().next() );
                    hasSubs = subordinateCount > 0;
                }
                else
                {
                    final SearchHelper searchHelper = new SearchHelper();
                    searchHelper.setFilter( "(objectclass=*)" );
                    searchHelper.setMaxResults( 1 );
                    searchHelper.setAttributes( Collections.emptyList() );
                    searchHelper.setSearchScope( SearchScope.ONE );
                    try
                    {
                        final Map<String, Map<String, String>> subSearchResults = chaiProvider.search( resultDN, searchHelper );
                        hasSubs = !subSearchResults.isEmpty();
                    }
                    catch ( final Exception e )
                    {
                        LOGGER.debug( sessionLabel, () -> "error during subordinate entry count of " + dn + ", error: " + e.getMessage() );
                    }
                }
                returnMap.put( resultDN, hasSubs ? DnType.navigable : DnType.selectable );
            }
        }
        return Collections.unmodifiableMap( returnMap );
    }

    private Set<String> adRootDNList( final DomainID domainID, final String profile ) throws ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException
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

    private static String entryNameFromDN( final String dn )
    {
        int start = dn.indexOf( "=" );
        start = start == -1 ? 0 : start + 1;
        int end = dn.indexOf( "," );
        if ( end == -1 )
        {
            end = dn.length();
        }

        return dn.substring( start, end );
    }

    @Value
    @Builder
    public static class LdapBrowseResult implements Serializable
    {
        private String dn;
        private String profileID;
        private String parentDN;
        private List<String> profileList;
        private boolean maxResults;

        private List<DNInformation> navigableDNlist;
        private List<DNInformation> selectableDNlist;
    }

    @Value
    public static class DNInformation implements Serializable
    {
        private final String entryName;
        private final String dn;
    }
}
