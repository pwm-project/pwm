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

package password.pwm.http.servlet.peoplesearch;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.i18n.Display;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.LdapPermissionTester;
import password.pwm.ldap.PhotoDataBean;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.ldap.search.UserSearchResults;
import password.pwm.svc.cache.CacheKey;
import password.pwm.svc.cache.CachePolicy;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.LocaleHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

class PeopleSearchDataReader
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PeopleSearchDataReader.class );

    private final PwmRequest pwmRequest;
    private final PeopleSearchConfiguration peopleSearchConfiguration;

    PeopleSearchDataReader( final PwmRequest pwmRequest )
    {
        this.pwmRequest = pwmRequest;
        this.peopleSearchConfiguration = PeopleSearchConfiguration.fromConfiguration( pwmRequest.getPwmApplication() );
    }

    SearchResultBean makeSearchResultBean(
            final String searchData,
            final boolean includeDisplayName
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final CacheKey cacheKey = makeCacheKey( SearchResultBean.class.getSimpleName(), searchData + "|" + includeDisplayName );

        {
            // try to serve from cache first
            final String cachedOutput = pwmRequest.getPwmApplication().getCacheService().get( cacheKey );
            if ( cachedOutput != null )
            {
                final SearchResultBean searchResultBean = JsonUtil.deserialize( cachedOutput, SearchResultBean.class );
                searchResultBean.setFromCache( true );
                StatisticsManager.incrementStat( pwmRequest, Statistic.PEOPLESEARCH_CACHE_HITS );
                return searchResultBean;
            }
            else
            {
                StatisticsManager.incrementStat( pwmRequest, Statistic.PEOPLESEARCH_CACHE_MISSES );
            }
        }

        // if not in cache, build results from ldap
        final SearchResultBean searchResultBean = makeSearchResultsImpl( pwmRequest, searchData, includeDisplayName );
        searchResultBean.setFromCache( false );
        StatisticsManager.incrementStat( pwmRequest, Statistic.PEOPLESEARCH_SEARCHES );
        storeDataInCache( pwmRequest.getPwmApplication(), cacheKey, searchResultBean );
        LOGGER.trace( pwmRequest, "returning " + searchResultBean.getSearchResults().size() + " results for search request '" + searchData + "'" );
        return searchResultBean;
    }

    OrgChartDataBean makeOrgChartData(
            final UserIdentity userIdentity,
            final boolean noChildren

    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        final CacheKey cacheKey = makeCacheKey(
                OrgChartDataBean.class.getSimpleName(),
                userIdentity.toDelimitedKey() + "|" + noChildren
        );

        {
            // if value is cached then return;
            final String cachedOutput = pwmRequest.getPwmApplication().getCacheService().get( cacheKey );
            if ( cachedOutput != null )
            {
                StatisticsManager.incrementStat( pwmRequest, Statistic.PEOPLESEARCH_CACHE_HITS );
                LOGGER.trace( pwmRequest, "completed makeOrgChartData of " + userIdentity.toDisplayString() + " from cache" );
                return JsonUtil.deserialize( cachedOutput, OrgChartDataBean.class );
            }
            else
            {
                StatisticsManager.incrementStat( pwmRequest, Statistic.PEOPLESEARCH_CACHE_MISSES );
            }
        }

        final OrgChartDataBean orgChartData = new OrgChartDataBean();

        // make self reference
        orgChartData.setSelf( makeOrgChartReferenceForIdentity( userIdentity ) );

        {
            // make parent reference
            final List<UserIdentity> parentIdentities = readUserDNAttributeValues( userIdentity, peopleSearchConfiguration.getOrgChartParentAttr() );
            if ( parentIdentities != null && !parentIdentities.isEmpty() )
            {
                final UserIdentity parentIdentity = parentIdentities.iterator().next();
                orgChartData.setParent( makeOrgChartReferenceForIdentity( parentIdentity ) );
            }
        }

        int childCount = 0;
        if ( !noChildren )
        {
            // make children reference
            final Map<String, OrgChartReferenceBean> sortedChildren = new TreeMap<>();
            final List<UserIdentity> childIdentities = readUserDNAttributeValues( userIdentity, peopleSearchConfiguration.getOrgChartChildAttr() );
            for ( final UserIdentity childIdentity : childIdentities )
            {
                final OrgChartReferenceBean childReference = makeOrgChartReferenceForIdentity( childIdentity );
                if ( childReference != null )
                {
                    if ( childReference.getDisplayNames() != null && !childReference.getDisplayNames().isEmpty() )
                    {
                        final String firstDisplayName = childReference.getDisplayNames().iterator().next();
                        sortedChildren.put( firstDisplayName, childReference );
                    }
                    else
                    {
                        sortedChildren.put( String.valueOf( childCount ), childReference );
                    }
                    childCount++;
                }
            }
            orgChartData.setChildren( Collections.unmodifiableList( new ArrayList<>( sortedChildren.values() ) ) );
        }

        if ( !StringUtil.isEmpty( peopleSearchConfiguration.getOrgChartAssistantAttr() ) )
        {
            final List<UserIdentity> assistantIdentities = readUserDNAttributeValues( userIdentity, peopleSearchConfiguration.getOrgChartAssistantAttr() );
            if ( assistantIdentities != null && !assistantIdentities.isEmpty() )
            {
                final UserIdentity assistantIdentity = assistantIdentities.iterator().next();
                final OrgChartReferenceBean assistantReference = makeOrgChartReferenceForIdentity( assistantIdentity );
                if ( assistantReference != null )
                {
                    orgChartData.setAssistant( assistantReference );
                }
            }
        }

        final TimeDuration totalTime = TimeDuration.fromCurrent( startTime );
        storeDataInCache( pwmRequest.getPwmApplication(), cacheKey, orgChartData );
        LOGGER.trace( pwmRequest, "completed makeOrgChartData in " + totalTime.asCompactString() + " with " + childCount + " children" );
        return orgChartData;
    }

    UserDetailBean makeUserDetailRequest(
            final String userKey
    )
            throws PwmUnrecoverableException, PwmOperationalException, ChaiUnavailableException
    {
        final Instant startTime = Instant.now();
        final UserIdentity userIdentity = UserIdentity.fromKey( userKey, pwmRequest.getPwmApplication() );

        final CacheKey cacheKey = makeCacheKey( UserDetailBean.class.getSimpleName(), userIdentity.toDelimitedKey() );
        {
            final String cachedOutput = pwmRequest.getPwmApplication().getCacheService().get( cacheKey );
            if ( cachedOutput != null )
            {
                StatisticsManager.incrementStat( pwmRequest, Statistic.PEOPLESEARCH_CACHE_HITS );
                return JsonUtil.deserialize( cachedOutput, UserDetailBean.class );
            }
            else
            {
                StatisticsManager.incrementStat( pwmRequest, Statistic.PEOPLESEARCH_CACHE_MISSES );
            }
        }

        try
        {
            checkIfUserIdentityViewable( userIdentity );
        }
        catch ( PwmOperationalException e )
        {
            LOGGER.error( pwmRequest.getPwmSession(), "error during detail results request while checking if requested userIdentity is within search scope: " + e.getMessage() );
            throw e;
        }

        final UserSearchResults detailResults = doDetailLookup( userIdentity );
        final Map<String, String> searchResults = detailResults.getResults().get( userIdentity );

        final UserDetailBean userDetailBean = new UserDetailBean();
        userDetailBean.setUserKey( userKey );
        final List<FormConfiguration> detailFormConfig = pwmRequest.getConfig().readSettingAsForm( PwmSetting.PEOPLE_SEARCH_DETAIL_FORM );
        final Map<String, AttributeDetailBean> attributeBeans = convertResultMapToBeans( pwmRequest, userIdentity, detailFormConfig, searchResults );

        userDetailBean.setDetail( attributeBeans );
        final String photoURL = figurePhotoURL( pwmRequest, userIdentity );
        if ( photoURL != null )
        {
            userDetailBean.setPhotoURL( photoURL );
        }
        final List<String> displayName = figureDisplaynames( pwmRequest, userIdentity );
        if ( displayName != null )
        {
            userDetailBean.setDisplayNames( displayName );
        }

        userDetailBean.setLinks( makeUserDetailLinks( userIdentity ) );

        LOGGER.trace( pwmRequest.getPwmSession(), "finished building userDetail result in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        storeDataInCache( pwmRequest.getPwmApplication(), cacheKey, userDetailBean );
        return userDetailBean;
    }

    private List<LinkReferenceBean> makeUserDetailLinks( final UserIdentity actorIdentity ) throws PwmUnrecoverableException
    {
        final String userLinksStr = pwmRequest.getConfig().readAppProperty( AppProperty.PEOPLESEARCH_VIEW_DETAIL_LINKS );
        if ( StringUtil.isEmpty( userLinksStr ) )
        {
            return Collections.emptyList();
        }
        final Map<String, String> linkMap;
        try
        {
            linkMap = JsonUtil.deserializeStringMap( userLinksStr );
        }
        catch ( Exception e )
        {
            LOGGER.warn( pwmRequest, "error de-serializing configured app property json for detail links: " + e.getMessage() );
            return Collections.emptyList();
        }
        final List<LinkReferenceBean> returnList = new ArrayList<>();
        final MacroMachine macroMachine = getMacroMachine( actorIdentity );
        for ( final Map.Entry<String, String> entry : linkMap.entrySet() )
        {
            final String key = entry.getKey();
            final String value = entry.getValue();
            final String parsedValue = macroMachine.expandMacros( value );
            final LinkReferenceBean linkReference = new LinkReferenceBean();
            linkReference.setName( key );
            linkReference.setLink( parsedValue );
            returnList.add( linkReference );
        }
        return returnList;
    }

    private List<String> readUserMultiAttributeValues(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity,
            final String attributeName
    )
            throws PwmUnrecoverableException
    {

        final List<String> returnObj = new ArrayList<>();

        final int maxValues = Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.PEOPLESEARCH_VALUE_MAXCOUNT ) );
        final ChaiUser chaiUser = getChaiUser( userIdentity );
        try
        {
            final Set<String> ldapValues = chaiUser.readMultiStringAttribute( attributeName );
            if ( ldapValues != null )
            {
                returnObj.addAll( ldapValues );
            }
            while ( returnObj.size() > maxValues )
            {
                returnObj.remove( returnObj.size() - 1 );
            }
            return Collections.unmodifiableList( returnObj );
        }
        catch ( ChaiOperationException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_DIRECTORY_UNAVAILABLE,
                    "error reading attribute value '" + attributeName + "', error:" + e.getMessage()
            ) );
        }
        catch ( ChaiUnavailableException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage() ) );
        }

    }

    private CacheKey makeCacheKey(
            final String operationIdentifier,
            final String dataIdentifier
    )
            throws PwmUnrecoverableException
    {
        final UserIdentity userIdentity;
        if ( pwmRequest.isAuthenticated() && !useProxy() )
        {
            userIdentity = pwmRequest.getUserInfoIfLoggedIn();
        }
        else
        {
            userIdentity = null;
        }
        final String keyString = operationIdentifier + "|" + pwmRequest.getPwmApplication().getSecureService().hash( dataIdentifier );
        return CacheKey.makeCacheKey(
                this.getClass(),
                userIdentity,
                keyString );
    }

    private static Set<String> getSearchAttributes( final Configuration configuration )
    {
        final List<String> searchResultForm = configuration.readSettingAsStringArray( PwmSetting.PEOPLE_SEARCH_SEARCH_ATTRIBUTES );
        return Collections.unmodifiableSet( new HashSet<>( searchResultForm ) );
    }

    private OrgChartReferenceBean makeOrgChartReferenceForIdentity(
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final OrgChartReferenceBean orgChartReferenceBean = new OrgChartReferenceBean();
        orgChartReferenceBean.setUserKey( userIdentity.toObfuscatedKey( pwmRequest.getPwmApplication() ) );
        orgChartReferenceBean.setPhotoURL( figurePhotoURL( pwmRequest, userIdentity ) );

        final List<String> displayLabels = figureDisplaynames( pwmRequest, userIdentity );
        orgChartReferenceBean.setDisplayNames( displayLabels );

        return orgChartReferenceBean;
    }

    private List<UserIdentity> readUserDNAttributeValues(
            final UserIdentity userIdentity,
            final String attributeName
    )
            throws PwmUnrecoverableException
    {

        final List<UserIdentity> returnObj = new ArrayList<>();

        final int maxValues = Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.PEOPLESEARCH_VALUE_MAXCOUNT ) );
        final ChaiUser chaiUser = getChaiUser( userIdentity );
        final Set<String> ldapValues;
        try
        {
            ldapValues = chaiUser.readMultiStringAttribute( attributeName );
        }
        catch ( ChaiOperationException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_DIRECTORY_UNAVAILABLE,
                    "error reading attribute value '" + attributeName + "', error:" + e.getMessage()
            ) );
        }
        catch ( ChaiUnavailableException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage() ) );
        }


        final boolean checkUserDNValues = Boolean.parseBoolean( pwmRequest.getConfig().readAppProperty( AppProperty.PEOPLESEARCH_MAX_VALUE_VERIFYUSERDN ) );
        for ( final String userDN : ldapValues )
        {
            final UserIdentity loopIdentity = new UserIdentity( userDN, userIdentity.getLdapProfileID() );
            if ( returnObj.size() < maxValues )
            {
                try
                {
                    if ( checkUserDNValues )
                    {
                        checkIfUserIdentityViewable( loopIdentity );
                    }
                    returnObj.add( loopIdentity );
                }
                catch ( PwmOperationalException e )
                {
                    LOGGER.debug( pwmRequest, "discarding userDN " + userDN + " from attribute " + attributeName + " because it does not match search filter" );
                }
            }
            else
            {
                LOGGER.trace( pwmRequest, "discarding userDN " + userDN + " from attribute " + attributeName + " because maximum value count has been reached" );
            }

        }
        return returnObj;
    }

    private static void storeDataInCache(
            final PwmApplication pwmApplication,
            final CacheKey cacheKey,
            final Serializable data
    )
            throws PwmUnrecoverableException
    {
        final long maxCacheSeconds = pwmApplication.getConfig().readSettingAsLong( PwmSetting.PEOPLE_SEARCH_MAX_CACHE_SECONDS );
        if ( maxCacheSeconds > 0 )
        {
            final CachePolicy cachePolicy = CachePolicy.makePolicyWithExpirationMS( maxCacheSeconds * 1000 );
            pwmApplication.getCacheService().put( cacheKey, cachePolicy, JsonUtil.serialize( data ) );
        }
    }

    private String figurePhotoURL(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final boolean enabled = peopleSearchConfiguration.isPhotosEnabled( pwmRequest.getUserInfoIfLoggedIn(), pwmRequest.getSessionLabel() );
        if ( !enabled )
        {
            LOGGER.debug( pwmRequest, "detailed user data lookup for " + userIdentity.toString() + ", failed photo query filter, denying photo view" );
            return null;
        }

        final String overrideURL = peopleSearchConfiguration.getPhotoUrlOverride( userIdentity );
        try
        {
            if ( overrideURL != null && !overrideURL.isEmpty() )
            {
                final MacroMachine macroMachine = getMacroMachine( userIdentity );
                return macroMachine.expandMacros( overrideURL );
            }

            try
            {
                readPhotoDataFromLdap( userIdentity );
            }
            catch ( PwmOperationalException e )
            {
                LOGGER.debug( pwmRequest, "determined " + userIdentity + " does not have photo data available while generating detail data" );
                return null;
            }
        }
        catch ( ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }

        String returnUrl = pwmRequest.getURLwithoutQueryString();
        returnUrl = PwmURL.appendAndEncodeUrlParameters( returnUrl, PwmConstants.PARAM_ACTION_REQUEST, PeopleSearchServlet.PeopleSearchActions.photo.name() );
        returnUrl = PwmURL.appendAndEncodeUrlParameters( returnUrl, PwmConstants.PARAM_USERKEY,  userIdentity.toObfuscatedKey( pwmApplication ) );
        return returnUrl;
    }

    private String figureDisplaynameValue(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final MacroMachine macroMachine = getMacroMachine( userIdentity );
        final String settingValue = pwmRequest.getConfig().readSettingAsString( PwmSetting.PEOPLE_SEARCH_DISPLAY_NAME );
        return macroMachine.expandMacros( settingValue );
    }

    private List<String> figureDisplaynames(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final List<String> displayLabels = new ArrayList<>();
        final List<String> displayStringSettings = pwmRequest.getConfig().readSettingAsStringArray( PwmSetting.PEOPLE_SEARCH_DISPLAY_NAMES_CARD_LABELS );
        if ( displayStringSettings != null )
        {
            final MacroMachine macroMachine = getMacroMachine( userIdentity );
            for ( final String displayStringSetting : displayStringSettings )
            {
                final String displayLabel = macroMachine.expandMacros( displayStringSetting );
                displayLabels.add( displayLabel );
            }
        }
        return displayLabels;
    }

    private Map<String, AttributeDetailBean> convertResultMapToBeans(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity,
            final List<FormConfiguration> detailForm,
            final Map<String, String> searchResults
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final Set<String> searchAttributes = getSearchAttributes( pwmRequest.getConfig() );
        final Map<String, AttributeDetailBean> returnObj = new LinkedHashMap<>();
        for ( final FormConfiguration formConfiguration : detailForm )
        {
            if ( formConfiguration.isRequired() || searchResults.containsKey( formConfiguration.getName() ) )
            {
                final AttributeDetailBean bean = new AttributeDetailBean();
                bean.setName( formConfiguration.getName() );
                bean.setLabel( formConfiguration.getLabel( pwmRequest.getLocale() ) );
                bean.setType( formConfiguration.getType() );
                if ( searchAttributes.contains( formConfiguration.getName() ) )
                {
                    if ( formConfiguration.getType() != FormConfiguration.Type.userDN )
                    {
                        bean.setSearchable( true );
                    }
                }
                if ( formConfiguration.getType() == FormConfiguration.Type.userDN )
                {
                    if ( searchResults.containsKey( formConfiguration.getName() ) )
                    {
                        final List<UserIdentity> identityValues = readUserDNAttributeValues( userIdentity, formConfiguration.getName() );
                        final TreeMap<String, UserReferenceBean> userReferences = new TreeMap<>();
                        for ( final UserIdentity loopIdentity : identityValues )
                        {
                            final String displayValue = figureDisplaynameValue( pwmRequest, loopIdentity );
                            final UserReferenceBean userReference = new UserReferenceBean();
                            userReference.setUserKey( loopIdentity.toObfuscatedKey( pwmRequest.getPwmApplication() ) );
                            userReference.setDisplayName( displayValue );
                            userReferences.put( displayValue, userReference );
                        }
                        bean.setUserReferences( userReferences.values() );
                    }
                }
                else
                {
                    if ( formConfiguration.isMultivalue() )
                    {
                        bean.setValues( readUserMultiAttributeValues( pwmRequest, userIdentity, formConfiguration.getName() ) );
                    }
                    else
                    {
                        if ( searchResults.containsKey( formConfiguration.getName() ) )
                        {
                            bean.setValues( Collections.singletonList( searchResults.get( formConfiguration.getName() ) ) );
                        }
                        else
                        {
                            bean.setValues( Collections.<String>emptyList() );
                        }
                    }
                }
                returnObj.put( formConfiguration.getName(), bean );
            }
        }
        return returnObj;
    }


    private ChaiUser getChaiUser(
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final boolean useProxy = useProxy();
        return useProxy
                ? pwmRequest.getPwmApplication().getProxiedChaiUser( userIdentity )
                : pwmRequest.getPwmSession().getSessionManager().getActor( pwmRequest.getPwmApplication(), userIdentity );
    }

    private MacroMachine getMacroMachine(
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final Locale locale = pwmRequest.getLocale();
        final ChaiProvider chaiProvider = pwmRequest.getPwmApplication().getProxiedChaiUser( userIdentity ).getChaiProvider();
        final UserInfo userInfo = UserInfoFactory.newUserInfo(
                pwmRequest.getPwmApplication(),
                pwmRequest.getSessionLabel(),
                locale,
                userIdentity,
                chaiProvider
        );
        return MacroMachine.forUser( pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel(), userInfo, null );
    }

    void checkIfUserIdentityViewable(
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final String filterSetting = getSearchFilter( pwmRequest.getConfig() );
        String filterString = filterSetting.replace( PwmConstants.VALUE_REPLACEMENT_USERNAME, "*" );
        while ( filterString.contains( "**" ) )
        {
            filterString = filterString.replace( "**", "*" );
        }

        final boolean match = LdapPermissionTester.testQueryMatch( pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel(), userIdentity, filterString );
        if ( !match )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "requested userDN is not available within configured search filter" ) );
        }
    }

    private static String getSearchFilter( final Configuration configuration )
    {
        final String configuredFilter = configuration.readSettingAsString( PwmSetting.PEOPLE_SEARCH_SEARCH_FILTER );
        if ( configuredFilter != null && !configuredFilter.isEmpty() )
        {
            return configuredFilter;
        }

        final List<String> defaultObjectClasses = configuration.readSettingAsStringArray( PwmSetting.DEFAULT_OBJECT_CLASSES );
        final Set<String> searchAttributes = getSearchAttributes( configuration );
        final StringBuilder filter = new StringBuilder();

        //open AND clause for objectclasses and attributes
        filter.append( "(&" );
        for ( final String objectClass : defaultObjectClasses )
        {
            filter.append( "(objectClass=" ).append( objectClass ).append( ")" );
        }

        // open OR clause for attributes
        filter.append( "(|" );

        for ( final String searchAttribute : searchAttributes )
        {
            filter.append( "(" ).append( searchAttribute ).append( "=*" ).append( PwmConstants.VALUE_REPLACEMENT_USERNAME ).append( "*)" );
        }

        // close OR clause
        filter.append( ")" );

        // close AND clause
        filter.append( ")" );
        return filter.toString();
    }

    private boolean useProxy( )
    {

        final boolean useProxy = pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_USE_PROXY );
        final boolean publicAccessEnabled = pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_PUBLIC );

        return useProxy || !pwmRequest.isAuthenticated() && publicAccessEnabled;
    }

    private UserSearchResults doDetailLookup(
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final List<FormConfiguration> detailFormConfig = pwmRequest.getConfig().readSettingAsForm( PwmSetting.PEOPLE_SEARCH_DETAIL_FORM );
        final Map<String, String> attributeHeaderMap = UserSearchResults.fromFormConfiguration(
                detailFormConfig, pwmRequest.getLocale() );

        if ( peopleSearchConfiguration.isOrgChartEnabled() )
        {
            final String orgChartParentAttr = peopleSearchConfiguration.getOrgChartParentAttr();
            if ( !attributeHeaderMap.containsKey( orgChartParentAttr ) )
            {
                attributeHeaderMap.put( orgChartParentAttr, orgChartParentAttr );
            }
            final String orgChartChildAttr = peopleSearchConfiguration.getOrgChartParentAttr();
            if ( !attributeHeaderMap.containsKey( orgChartChildAttr ) )
            {
                attributeHeaderMap.put( orgChartChildAttr, orgChartChildAttr );
            }
        }

        try
        {
            final ChaiUser theUser = getChaiUser( userIdentity );
            final Map<String, String> values = theUser.readStringAttributes( attributeHeaderMap.keySet() );
            return new UserSearchResults(
                    attributeHeaderMap,
                    Collections.singletonMap( userIdentity, values ),
                    false
            );
        }
        catch ( ChaiException e )
        {
            LOGGER.error( "unexpected error during detail lookup of '" + userIdentity + "', error: " + e.getMessage() );
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

    PhotoDataBean readPhotoDataFromLdap(
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        final ChaiUser chaiUser = getChaiUser( userIdentity );
        return LdapOperationsHelper.readPhotoDataFromLdap(
                pwmRequest.getConfig(),
                chaiUser,
                userIdentity
        );
    }

    private SearchResultBean makeSearchResultsImpl(
            final PwmRequest pwmRequest,
            final String username,
            final boolean includeDisplayName
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        if ( username == null || username.length() < 1 )
        {
            return new SearchResultBean();
        }

        final boolean useProxy = useProxy();
        final UserSearchEngine userSearchEngine = pwmRequest.getPwmApplication().getUserSearchEngine();

        final SearchConfiguration searchConfiguration;
        {
            final SearchConfiguration.SearchConfigurationBuilder builder = SearchConfiguration.builder();
            builder.contexts( pwmRequest.getConfig().readSettingAsStringArray( PwmSetting.PEOPLE_SEARCH_SEARCH_BASE ) );
            builder.enableContextValidation( false );
            builder.username( username );
            builder.enableValueEscaping( false );
            builder.filter( getSearchFilter( pwmRequest.getConfig() ) );
            builder.enableSplitWhitespace( true );

            if ( !useProxy )
            {
                builder.ldapProfile( pwmRequest.getPwmSession().getUserInfo().getUserIdentity().getLdapProfileID() );
                builder.chaiProvider( pwmRequest.getPwmSession().getSessionManager().getChaiProvider() );
            }
            searchConfiguration = builder.build();
        }

        final UserSearchResults results;
        final boolean sizeExceeded;
        try
        {
            final List<FormConfiguration> searchForm = pwmRequest.getConfig().readSettingAsForm(
                    PwmSetting.PEOPLE_SEARCH_RESULT_FORM );
            final int maxResults = ( int ) pwmRequest.getConfig().readSettingAsLong(
                    PwmSetting.PEOPLE_SEARCH_RESULT_LIMIT );
            final Locale locale = pwmRequest.getLocale();
            results = userSearchEngine.performMultiUserSearchFromForm( locale, searchConfiguration, maxResults, searchForm, pwmRequest.getSessionLabel() );
            sizeExceeded = results.isSizeExceeded();
        }
        catch ( PwmOperationalException e )
        {
            final ErrorInformation errorInformation = e.getErrorInformation();
            LOGGER.error( pwmRequest.getSessionLabel(), errorInformation.toDebugStr() );
            throw new PwmUnrecoverableException( errorInformation );
        }

        final List<Map<String, Object>> resultOutput = new ArrayList<>( results.resultsAsJsonOutput( pwmRequest.getPwmApplication(), null ) );
        if ( includeDisplayName )
        {
            for ( final Map<String, Object> map : resultOutput )
            {
                final String userKey = ( String ) map.get( "userKey" );
                if ( userKey != null )
                {
                    final UserIdentity userIdentity = UserIdentity.fromKey( userKey, pwmRequest.getPwmApplication() );
                    final String displayValue = figureDisplaynameValue( pwmRequest, userIdentity );
                    map.put( "_displayName", displayValue );
                }
            }
        }

        final TimeDuration searchDuration = TimeDuration.fromCurrent( startTime );
        LOGGER.trace( pwmRequest.getPwmSession(), "finished rest peoplesearch search in "
                + searchDuration.asCompactString() + " not using cache, size=" + results.getResults().size() );

        final SearchResultBean searchResultBean = new SearchResultBean();
        searchResultBean.setSearchResults( resultOutput );
        searchResultBean.setSizeExceeded( sizeExceeded );
        final String aboutMessage = LocaleHelper.getLocalizedMessage(
                pwmRequest.getLocale(),
                Display.Display_SearchResultsInfo.getKey(),
                pwmRequest.getConfig(),
                Display.class,
                new String[]
                        {
                                String.valueOf( results.getResults().size() ), searchDuration.asLongString( pwmRequest.getLocale() ),
                        }
        );
        searchResultBean.setAboutResultMessage( aboutMessage );
        return searchResultBean;
    }
}
