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
import lombok.Value;
import org.apache.commons.csv.CSVPrinter;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.http.servlet.helpdesk.HelpdeskServletUtil;
import password.pwm.http.servlet.peoplesearch.bean.AttributeDetailBean;
import password.pwm.http.servlet.peoplesearch.bean.LinkReferenceBean;
import password.pwm.http.servlet.peoplesearch.bean.OrgChartDataBean;
import password.pwm.http.servlet.peoplesearch.bean.OrgChartReferenceBean;
import password.pwm.http.servlet.peoplesearch.bean.SearchResultBean;
import password.pwm.http.servlet.peoplesearch.bean.UserDetailBean;
import password.pwm.http.servlet.peoplesearch.bean.UserReferenceBean;
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
import password.pwm.svc.cache.CacheLoader;
import password.pwm.svc.cache.CachePolicy;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

class PeopleSearchDataReader
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PeopleSearchDataReader.class );

    private final PwmRequest pwmRequest;
    private final PeopleSearchConfiguration peopleSearchConfiguration;

    private enum CacheIdentifier
    {
        attributeRead,
        checkIfViewable,
        searchResultBean,
    }

    PeopleSearchDataReader( final PwmRequest pwmRequest )
    {
        this.pwmRequest = pwmRequest;
        this.peopleSearchConfiguration = PeopleSearchConfiguration.forRequest( pwmRequest );
    }

    SearchResultBean makeSearchResultBean(
            final SearchRequestBean searchRequestBean
    )
            throws PwmUnrecoverableException
    {
        final CacheKey cacheKey = makeCacheKey( SearchResultBean.class.getSimpleName(), JsonUtil.serialize( searchRequestBean ) );

        {
            // try to serve from cache first
            final SearchResultBean cachedResult = pwmRequest.getPwmApplication().getCacheService().get( cacheKey, SearchResultBean.class );
            if ( cachedResult != null )
            {
                final SearchResultBean copyWithCacheSet = cachedResult.toBuilder().fromCache( true ).build();
                StatisticsManager.incrementStat( pwmRequest, Statistic.PEOPLESEARCH_CACHE_HITS );
                return copyWithCacheSet;
            }
            else
            {
                StatisticsManager.incrementStat( pwmRequest, Statistic.PEOPLESEARCH_CACHE_MISSES );
            }
        }

        // if not in cache, build results from ldap
        final SearchResultBean searchResultBean = makeSearchResultsImpl( searchRequestBean )
                .toBuilder().fromCache( false ).build();

        StatisticsManager.incrementStat( pwmRequest, Statistic.PEOPLESEARCH_SEARCHES );
        storeDataInCache( pwmRequest.getPwmApplication(), cacheKey, searchResultBean );
        LOGGER.trace( pwmRequest, () -> "returning " + searchResultBean.getSearchResults().size()
                + " results for search request "
                + JsonUtil.serialize( searchRequestBean ) );
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
            final OrgChartDataBean cachedOutput = pwmRequest.getPwmApplication().getCacheService().get( cacheKey, OrgChartDataBean.class );
            if ( cachedOutput != null )
            {
                StatisticsManager.incrementStat( pwmRequest, Statistic.PEOPLESEARCH_CACHE_HITS );
                LOGGER.trace( pwmRequest, () -> "completed makeOrgChartData of " + userIdentity.toDisplayString() + " from cache" );
                return cachedOutput;
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
            final List<UserIdentity> parentIdentities = readUserDNAttributeValues( userIdentity, peopleSearchConfiguration.getOrgChartParentAttr( userIdentity ) );
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
            final List<UserIdentity> childIdentities = readUserDNAttributeValues( userIdentity, peopleSearchConfiguration.getOrgChartChildAttr( userIdentity ) );
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

        if ( !StringUtil.isEmpty( peopleSearchConfiguration.getOrgChartAssistantAttr( userIdentity ) ) )
        {
            final List<UserIdentity> assistantIdentities = readUserDNAttributeValues( userIdentity, peopleSearchConfiguration.getOrgChartAssistantAttr( userIdentity ) );
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
        {
            final int finalChildCount = childCount;
            LOGGER.trace( pwmRequest, () -> "completed makeOrgChartData of " + userIdentity.toDisplayString()
                    + " in " + totalTime.asCompactString() + " with " + finalChildCount + " children" );
        }
        return orgChartData;
    }

    UserDetailBean makeUserDetailRequest(
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        final CacheKey cacheKey = makeCacheKey( UserDetailBean.class.getSimpleName(), userIdentity.toDelimitedKey() );
        {
            final UserDetailBean cachedOutput = pwmRequest.getPwmApplication().getCacheService().get( cacheKey, UserDetailBean.class );
            if ( cachedOutput != null )
            {
                StatisticsManager.incrementStat( pwmRequest, Statistic.PEOPLESEARCH_CACHE_HITS );
                return cachedOutput;
            }
            else
            {
                StatisticsManager.incrementStat( pwmRequest, Statistic.PEOPLESEARCH_CACHE_MISSES );
            }
        }

        checkIfUserIdentityViewable( userIdentity );

        final UserSearchResults detailResults = doDetailLookup( userIdentity );
        final Map<String, String> searchResults = detailResults.getResults().get( userIdentity );

        final UserDetailBean userDetailBean = new UserDetailBean();
        userDetailBean.setUserKey( userIdentity.toObfuscatedKey( pwmRequest.getPwmApplication() ) );
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

        LOGGER.trace( pwmRequest, () -> "finished building userDetail result in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
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
        return CacheKey.newKey(
                this.getClass(),
                userIdentity,
                keyString );
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
                if ( checkUserDNValues )
                {
                    checkIfUserIdentityViewable( loopIdentity );
                }
                returnObj.add( loopIdentity );
            }
            else
            {
                LOGGER.trace( pwmRequest, () -> "discarding userDN " + userDN + " from attribute " + attributeName + " because maximum value count has been reached" );
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
            pwmApplication.getCacheService().put( cacheKey, cachePolicy, data );
        }
    }

    private <T extends Serializable> T storeDataInCache(
            final CacheIdentifier operationIdentifier,
            final String dataIdentifier,
            final Class<T> classOfT,
            final CacheLoader<T> cacheLoader
    )
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final CacheKey cacheKey = makeCacheKey( operationIdentifier.name(), dataIdentifier );
        final long maxCacheSeconds = pwmApplication.getConfig().readSettingAsLong( PwmSetting.PEOPLE_SEARCH_MAX_CACHE_SECONDS );
        final CachePolicy cachePolicy = CachePolicy.makePolicyWithExpirationMS( maxCacheSeconds * 1000 );
        return pwmApplication.getCacheService().get( cacheKey, cachePolicy, classOfT, cacheLoader );
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
            return null;
        }

        {
            final List<UserPermission> permissions = pwmApplication.getConfig().readSettingAsUserPermission( PwmSetting.PEOPLE_SEARCH_PHOTO_QUERY_FILTER );
            final boolean hasPermission = LdapPermissionTester.testUserPermissions( pwmApplication, pwmRequest.getSessionLabel(), userIdentity, permissions );
            if ( !hasPermission )
            {
                LOGGER.debug( pwmRequest, () -> "user " + userIdentity.toString() + " failed photo query filter, denying photo view" );
                return null;
            }
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
                LOGGER.debug( pwmRequest, () -> "determined " + userIdentity + " does not have photo data available while generating detail data" );
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
            throws PwmUnrecoverableException
    {
        final Set<String> searchAttributes = peopleSearchConfiguration.getSearchAttributes();
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
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final CacheLoader<Boolean> cacheLoader = () ->
        {
            final String filterSetting = makeSimpleSearchFilter( );
            String filterString = filterSetting.replace( PwmConstants.VALUE_REPLACEMENT_USERNAME, "*" );
            while ( filterString.contains( "**" ) )
            {
                filterString = filterString.replace( "**", "*" );
            }

            return LdapPermissionTester.testQueryMatch( pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel(), userIdentity, filterString );
        };

        final boolean result = storeDataInCache( CacheIdentifier.checkIfViewable, userIdentity.toDelimitedKey(), Boolean.class, cacheLoader );
        try
        {
            if ( !result )
            {
                final String msg = "attempt to read data of out-of-scope userDN '" + userIdentity.toDisplayString() + "' by user " + userIdentity.toDisplayString();
                LOGGER.warn( pwmRequest, msg );
                throw PwmUnrecoverableException.newException( PwmError.ERROR_SERVICE_NOT_AVAILABLE, msg );
            }
        }
        finally
        {
            LOGGER.trace( pwmRequest, () -> "completed checkIfUserViewable for " + userIdentity.toDisplayString() + " in " + TimeDuration.compactFromCurrent( startTime ) );
        }
    }

    private String makeSimpleSearchFilter()
    {
        final String configuredFilter = pwmRequest.getConfig().readSettingAsString( PwmSetting.PEOPLE_SEARCH_SEARCH_FILTER );
        if ( configuredFilter != null && !configuredFilter.isEmpty() )
        {
            return configuredFilter;
        }

        final List<String> defaultObjectClasses = pwmRequest.getConfig().readSettingAsStringArray( PwmSetting.DEFAULT_OBJECT_CLASSES );
        final Set<String> searchAttributes = peopleSearchConfiguration.getSearchAttributes();
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

    private String makeAdvancedFilter( final Map<String, String> attributesInSearchRequest )
    {
        final List<String> defaultObjectClasses = pwmRequest.getConfig().readSettingAsStringArray( PwmSetting.DEFAULT_OBJECT_CLASSES );
        final List<FormConfiguration> searchAttributes = peopleSearchConfiguration.getSearchForm();

        return HelpdeskServletUtil.makeAdvancedSearchFilter( defaultObjectClasses, searchAttributes, attributesInSearchRequest );
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
            final String orgChartParentAttr = peopleSearchConfiguration.getOrgChartParentAttr( userIdentity );
            if ( !attributeHeaderMap.containsKey( orgChartParentAttr ) )
            {
                attributeHeaderMap.put( orgChartParentAttr, orgChartParentAttr );
            }
            final String orgChartChildAttr = peopleSearchConfiguration.getOrgChartParentAttr( userIdentity );
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
            final SearchRequestBean searchRequest
    )
            throws PwmUnrecoverableException
    {
        Objects.requireNonNull( searchRequest );

        final Instant startTime = Instant.now();

        final SearchRequestBean.SearchMode searchMode = searchRequest.getMode() == null
                ? SearchRequestBean.SearchMode.simple
                : searchRequest.getMode();

        final SearchConfiguration searchConfiguration;
        {
            final SearchConfiguration.SearchConfigurationBuilder builder = SearchConfiguration.builder();
            builder.contexts( pwmRequest.getConfig().readSettingAsStringArray( PwmSetting.PEOPLE_SEARCH_SEARCH_BASE ) );
            builder.enableContextValidation( false );
            builder.enableValueEscaping( false );
            builder.enableSplitWhitespace( true );

            if ( !useProxy() )
            {
                builder.ldapProfile( pwmRequest.getPwmSession().getUserInfo().getUserIdentity().getLdapProfileID() );
                builder.chaiProvider( pwmRequest.getPwmSession().getSessionManager().getChaiProvider() );
            }

            switch ( searchMode )
            {
                case simple:
                {
                    if ( StringUtil.isEmpty( searchRequest.getUsername() ) )
                    {
                        return SearchResultBean.builder().searchResults( Collections.emptyList() ).build();
                    }

                    builder.filter( makeSimpleSearchFilter() );
                    builder.username( searchRequest.getUsername() );
                }
                break;

                case advanced:
                {
                    if ( JavaHelper.isEmpty( searchRequest.nonEmptySearchValues() ) )
                    {
                        return SearchResultBean.builder().searchResults( Collections.emptyList() ).build();
                    }

                    final Map<FormConfiguration, String> formValues = new LinkedHashMap<>();
                    final Map<String, String> requestSearchValues = SearchRequestBean.searchValueToMap( searchRequest.getSearchValues() );
                    for ( final FormConfiguration formConfiguration : peopleSearchConfiguration.getSearchForm() )
                    {
                        final String attribute = formConfiguration.getName();
                        final String value = requestSearchValues.get( attribute );
                        if ( !StringUtil.isEmpty( value ) )
                        {
                            formValues.put( formConfiguration, value );
                        }
                    }

                    builder.filter( makeAdvancedFilter( requestSearchValues ) );
                    builder.formValues( formValues );
                }
                break;

                default:
                    JavaHelper.unhandledSwitchStatement( searchMode );
            }

            searchConfiguration = builder.build();
        }

        final UserSearchEngine userSearchEngine = pwmRequest.getPwmApplication().getUserSearchEngine();

        final UserSearchResults results;
        final boolean sizeExceeded;
        try
        {
            final List<FormConfiguration> searchForm = peopleSearchConfiguration.getResultForm();
            final int maxResults = peopleSearchConfiguration.getResultLimit();
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
        if ( searchRequest.isIncludeDisplayName() )
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
        LOGGER.trace( pwmRequest, () -> "finished rest peoplesearch search in "
                + searchDuration.asCompactString() + " not using cache, size=" + results.getResults().size() );


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

        return SearchResultBean.builder()
                .sizeExceeded( sizeExceeded )
                .searchResults( resultOutput )
                .aboutResultMessage( aboutMessage )
                .build();
    }

    private String readUserAttribute(
            final UserIdentity userIdentity,
            final String attribute
    )
            throws PwmUnrecoverableException
    {
        final CacheLoader<String> cacheLoader = () ->
        {
            try
            {
                return getChaiUser( userIdentity ).readStringAttribute( attribute );
            }
            catch ( ChaiOperationException e )
            {
                LOGGER.trace( pwmRequest, () -> "error reading attribute for user '" + userIdentity.toDisplayString() + "', error: " + e.getMessage() );
                return null;
            }
            catch ( ChaiUnavailableException e )
            {
                throw PwmUnrecoverableException.fromChaiException( e );
            }
        };

        return storeDataInCache( CacheIdentifier.attributeRead, userIdentity.toDelimitedKey() + "|" + attribute, String.class, cacheLoader );
    }

    public List<String> getMailToLink(
            final UserIdentity userIdentity,
            final int depth
    )
            throws PwmUnrecoverableException
    {
        final List<String> returnValues = new ArrayList<>(  );
        final String mailtoAttr = userIdentity.getLdapProfile( pwmRequest.getConfig() ).readSettingAsString( PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE );
        final String value = readUserAttribute( userIdentity, mailtoAttr );
        if ( !StringUtil.isEmpty( value ) )
        {
            returnValues.add( value );
        }

        if ( depth > 0 )
        {
            final OrgChartDataBean orgChartDataBean = this.makeOrgChartData( userIdentity, false );
            for ( final OrgChartReferenceBean orgChartReferenceBean : orgChartDataBean.getChildren() )
            {
                final String userKey = orgChartReferenceBean.getUserKey();
                final UserIdentity childIdentity = PeopleSearchServlet.readUserIdentityFromKey( pwmRequest, userKey );
                returnValues.addAll( getMailToLink( childIdentity, depth - 1 ) );
            }
        }

        return Collections.unmodifiableList( returnValues );
    }

    void writeUserOrgChartDetailToCsv(
            final CSVPrinter csvPrinter,
            final UserIdentity userIdentity,
            final int depth
    )
    {
        final Instant startTime = Instant.now();
        LOGGER.trace( pwmRequest, () -> "beginning csv export starting with user " + userIdentity.toDisplayString() + " and depth of " + depth );

        final ThreadPoolExecutor executor = pwmRequest.getPwmApplication().getPeopleSearchService().getJobExecutor();

        final AtomicInteger rowCounter = new AtomicInteger( 0 );
        final OrgChartExportState orgChartExportState = new OrgChartExportState(
                executor,
                csvPrinter,
                rowCounter,
                Collections.singleton( OrgChartExportState.IncludeData.displayForm )
        );

        final OrgChartCsvRowOutputJob job = new OrgChartCsvRowOutputJob( orgChartExportState, userIdentity, depth, null );
        executor.execute( job );

        final TimeDuration maxDuration = peopleSearchConfiguration.getExportCsvMaxDuration();
        maxDuration.pause( () -> executor.getQueue().size() + executor.getActiveCount() <= 0 );

        final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );
        LOGGER.trace( pwmRequest, () -> "completed csv export of " + rowCounter.get() + " records in " + timeDuration.asCompactString() );
    }

    @Value
    private static class OrgChartExportState
    {
        private final Executor executor;
        private final CSVPrinter csvPrinter;
        private final AtomicInteger rowCounter;
        private final Set<IncludeData> includeData;

        enum IncludeData
        {
            displayCard,
            displayForm,
        }
    }

    private class OrgChartCsvRowOutputJob implements Runnable
    {
        private final OrgChartExportState orgChartExportState;
        private final UserIdentity userIdentity;
        private final int depth;
        private final String parentWorkforceID;

        OrgChartCsvRowOutputJob(
                final OrgChartExportState orgChartExportState,
                final UserIdentity userIdentity,
                final int depth,
                final String parentWorkforceID
        )
        {
            this.orgChartExportState = orgChartExportState;
            this.userIdentity = userIdentity;
            this.depth = depth;
            this.parentWorkforceID = parentWorkforceID;
        }

        @Override
        public void run()
        {
            try
            {
                doJob();
            }
            catch ( Exception e )
            {
                LOGGER.error( pwmRequest, "error exporting csv row data: " + e.getMessage() );
            }
        }

        private void doJob()
                throws PwmUnrecoverableException, IOException
        {
            final List<String> outputRowValues = new ArrayList<>( );
            final String workforceIDattr = peopleSearchConfiguration.getOrgChartWorkforceIDAttr( userIdentity );

            final String workforceID = readUserAttribute( userIdentity, workforceIDattr );
            outputRowValues.add( workforceID == null ? "" : workforceID );
            outputRowValues.add( parentWorkforceID == null ? "" : parentWorkforceID );

            final OrgChartDataBean orgChartDataBean = makeOrgChartData( userIdentity, false );

            // export display card
            if ( orgChartExportState.getIncludeData().contains( OrgChartExportState.IncludeData.displayCard ) )
            {
                outputRowValues.addAll( orgChartDataBean.getSelf().displayNames );
            }


            // export form detail
            if ( orgChartExportState.getIncludeData().contains( OrgChartExportState.IncludeData.displayForm ) )
            {
                final UserDetailBean userDetailBean = makeUserDetailRequest( userIdentity );
                for ( final Map.Entry<String, AttributeDetailBean> entry : userDetailBean.getDetail().entrySet() )
                {
                    final List<String> values = entry.getValue().getValues();
                    if ( JavaHelper.isEmpty( values ) )
                    {
                        outputRowValues.add( " " );
                    }
                    else if ( values.size() == 1 )
                    {
                        outputRowValues.add( values.iterator().next() );
                    }
                    else
                    {
                        final String row = StringUtil.collectionToString( values, " " );
                        outputRowValues.add( row );
                    }
                }
            }

            orgChartExportState.getCsvPrinter().printRecord( outputRowValues );
            orgChartExportState.getCsvPrinter().flush();

            if ( depth > 0 && orgChartExportState.getRowCounter().get() < peopleSearchConfiguration.getExportCsvMaxItems() )
            {
                final List<OrgChartReferenceBean> children = orgChartDataBean.getChildren();
                if ( !JavaHelper.isEmpty( children ) )
                {
                    for ( final OrgChartReferenceBean child : children )
                    {
                        final String childKey = child.getUserKey();
                        final UserIdentity childIdentity = PeopleSearchServlet.readUserIdentityFromKey( pwmRequest, childKey );
                        final OrgChartCsvRowOutputJob job = new OrgChartCsvRowOutputJob( orgChartExportState, childIdentity, depth - 1, workforceID );
                        orgChartExportState.getExecutor().execute( job );
                        orgChartExportState.getRowCounter().incrementAndGet();
                    }
                }
            }
        }
    }

}
