/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiProviderFactory;
import com.novell.ldapchai.provider.DirectoryVendor;
import com.novell.ldapchai.provider.SearchScope;
import com.novell.ldapchai.util.ChaiUtility;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
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
    private static final PwmLogger LOGGER = PwmLogger.forClass( LdapBrowser.class );
    private final StoredConfigurationImpl storedConfiguration;

    private final ChaiProviderFactory chaiProviderFactory;
    private final Map<String, ChaiProvider> providerCache = new HashMap<>();

    public LdapBrowser(
            final ChaiProviderFactory chaiProviderFactory,
            final StoredConfigurationImpl storedConfiguration
    )
            throws PwmUnrecoverableException
    {
        this.chaiProviderFactory = chaiProviderFactory;
        this.storedConfiguration = storedConfiguration;
    }

    public LdapBrowseResult doBrowse( final String profile, final String dn ) throws PwmUnrecoverableException
    {
        try
        {
            return doBrowseImpl( figureLdapProfileID( profile ), dn );
        }
        catch ( ChaiUnavailableException | ChaiOperationException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
        catch ( Exception e )
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

    private LdapBrowseResult doBrowseImpl( final String profileID, final String dn ) throws PwmUnrecoverableException, ChaiUnavailableException, ChaiOperationException
    {

        final LdapBrowseResult result = new LdapBrowseResult();
        {
            final Map<String, Boolean> childDNs = new TreeMap<>();
            childDNs.putAll( getChildEntries( profileID, dn ) );

            for ( final Map.Entry<String, Boolean> entry : childDNs.entrySet() )
            {
                final String childDN = entry.getKey();
                final DNInformation dnInformation = new DNInformation();
                dnInformation.setDn( childDN );
                dnInformation.setEntryName( entryNameFromDN( childDN ) );
                if ( entry.getValue() )
                {
                    result.getNavigableDNlist().add( dnInformation );
                }
                else
                {
                    result.getSelectableDNlist().add( dnInformation );
                }
            }
            result.setMaxResults( childDNs.size() >= getMaxSizeLimit() );

        }
        result.setDn( dn );
        result.setProfileID( profileID );
        final Configuration configuration = new Configuration( storedConfiguration );
        if ( configuration.getLdapProfiles().size() > 1 )
        {
            result.getProfileList().addAll( configuration.getLdapProfiles().keySet() );
        }

        if ( adRootDNList( profileID ).contains( dn ) )
        {
            result.setParentDN( "" );
        }
        else if ( dn != null && !dn.isEmpty() )
        {
            final ChaiEntry dnEntry = getChaiProvider( profileID ).getEntryFactory().newChaiEntry( dn );
            final ChaiEntry parentEntry = dnEntry.getParentEntry();
            if ( parentEntry == null )
            {
                result.setParentDN( "" );
            }
            else
            {
                result.setParentDN( parentEntry.getEntryDN() );
            }
        }

        return result;
    }

    private ChaiProvider getChaiProvider( final String profile ) throws PwmUnrecoverableException
    {
        if ( !providerCache.containsKey( profile ) )
        {
            final Configuration configuration = new Configuration( storedConfiguration );
            final LdapProfile ldapProfile = LdapProfile.makeFromStoredConfiguration( storedConfiguration, profile );
            final ChaiProvider chaiProvider = LdapOperationsHelper.openProxyChaiProvider( chaiProviderFactory, null, ldapProfile, configuration, null );
            providerCache.put( profile, chaiProvider );
        }
        return providerCache.get( profile );
    }

    private String figureLdapProfileID( final String profile )
    {
        final Configuration configuration = new Configuration( storedConfiguration );
        if ( configuration.getLdapProfiles().keySet().contains( profile ) )
        {
            return profile;
        }
        return configuration.getLdapProfiles().keySet().iterator().next();
    }

    private int getMaxSizeLimit( )
    {
        final Configuration configuration = new Configuration( storedConfiguration );
        return Integer.parseInt( configuration.readAppProperty( AppProperty.LDAP_BROWSER_MAX_ENTRIES ) );
    }

    private Map<String, Boolean> getChildEntries(
            final String profile,
            final String dn
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, ChaiOperationException
    {

        final HashMap<String, Boolean> returnMap = new HashMap<>();
        final ChaiProvider chaiProvider = getChaiProvider( profile );
        if ( ( dn == null || dn.isEmpty() ) && chaiProvider.getDirectoryVendor() == DirectoryVendor.ACTIVE_DIRECTORY )
        {
            final Set<String> adRootDNList = adRootDNList( profile );
            for ( final String rootDN : adRootDNList )
            {
                returnMap.put( rootDN, true );
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
                    final Integer subordinateCount = Integer.parseInt( attributeResults.get( "subordinateCount" ).iterator().next() );
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
                    catch ( Exception e )
                    {
                        LOGGER.debug( "error during subordinate entry count of " + dn + ", error: " + e.getMessage() );
                    }
                }
                returnMap.put( resultDN, hasSubs );
            }
        }
        return returnMap;
    }

    private Set<String> adRootDNList( final String profile ) throws ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException
    {
        final ChaiProvider chaiProvider = getChaiProvider( profile );
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

    public static class LdapBrowseResult implements Serializable
    {
        private String dn;
        private String profileID;
        private String parentDN;
        private List<String> profileList = new ArrayList<>();
        private boolean maxResults;

        private List<DNInformation> navigableDNlist = new ArrayList<>();
        private List<DNInformation> selectableDNlist = new ArrayList<>();

        public String getDn( )
        {
            return dn;
        }

        public void setDn( final String dn )
        {
            this.dn = dn;
        }

        public String getProfileID( )
        {
            return profileID;
        }

        public void setProfileID( final String profileID )
        {
            this.profileID = profileID;
        }

        public String getParentDN( )
        {
            return parentDN;
        }

        public void setParentDN( final String parentDN )
        {
            this.parentDN = parentDN;
        }

        public List<String> getProfileList( )
        {
            return profileList;
        }

        public boolean isMaxResults( )
        {
            return maxResults;
        }

        public void setMaxResults( final boolean maxResults )
        {
            this.maxResults = maxResults;
        }

        public List<DNInformation> getNavigableDNlist( )
        {
            return navigableDNlist;
        }

        public List<DNInformation> getSelectableDNlist( )
        {
            return selectableDNlist;
        }
    }

    public static class DNInformation implements Serializable
    {
        private String entryName;
        private String dn;

        public String getEntryName( )
        {
            return entryName;
        }

        public void setEntryName( final String entryName )
        {
            this.entryName = entryName;
        }

        public String getDn( )
        {
            return dn;
        }

        public void setDn( final String dn )
        {
            this.dn = dn;
        }
    }
}
