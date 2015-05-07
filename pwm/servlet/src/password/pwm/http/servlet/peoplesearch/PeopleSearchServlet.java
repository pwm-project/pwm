/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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
import password.pwm.*;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.config.UserPermission;
import password.pwm.error.*;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.servlet.PwmServlet;
import password.pwm.ldap.*;
import password.pwm.util.JsonUtil;
import password.pwm.util.SecureHelper;
import password.pwm.util.TimeDuration;
import password.pwm.util.cache.CacheKey;
import password.pwm.util.cache.CachePolicy;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URLConnection;
import java.util.*;

public class PeopleSearchServlet extends PwmServlet {

    private static final PwmLogger LOGGER = PwmLogger.forClass(PeopleSearchServlet.class);

    public enum PeopleSearchActions implements ProcessAction {
        search(HttpMethod.POST),
        detail(HttpMethod.POST),
        photo(HttpMethod.GET),
        clientData(HttpMethod.GET),
        orgChartData(HttpMethod.POST),

        ;

        private final HttpMethod method;

        PeopleSearchActions(HttpMethod method) {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods() {
            return Collections.singletonList(method);
        }
    }

    protected PeopleSearchActions readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException {
        try {
            return PeopleSearchActions.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    protected void processAction(
            final PwmRequest pwmRequest
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        if (!pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_ENABLE)) {
            pwmRequest.respondWithError(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE));
            return;
        }

        if (pwmRequest.getURL().isPublicUrl()) {
            if (!pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_ENABLE_PUBLIC)) {
                pwmRequest.respondWithError(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,"public peoplesearch service is not enabled"));
                return;
            }
        } else {
            if (!pwmRequest.getPwmSession().getSessionManager().checkPermission(pwmRequest.getPwmApplication(), Permission.PEOPLE_SEARCH)) {
                pwmRequest.respondWithError(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED));
                return;
            }

        }
        final int peopleSearchIdleTimeout = (int)pwmRequest.getConfig().readSettingAsLong(PwmSetting.PEOPLE_SEARCH_IDLE_TIMEOUT_SECONDS);
        if (peopleSearchIdleTimeout > 0 && pwmRequest.getURL().isPrivateUrl()) {
            pwmRequest.getPwmSession().setSessionTimeout(pwmRequest.getHttpServletRequest().getSession(), peopleSearchIdleTimeout);
        }

        final PeopleSearchConfiguration peopleSearchConfiguration = new PeopleSearchConfiguration(pwmRequest.getConfig());

        final PeopleSearchActions peopleSearchAction = this.readProcessAction(pwmRequest);
        if (peopleSearchAction != null) {
            switch (peopleSearchAction) {
                case search:
                    restSearchRequest(pwmRequest);
                    return;

                case detail:
                    restUserDetailRequest(pwmRequest, peopleSearchConfiguration);
                    return;

                case photo:
                    processUserPhotoImageRequest(pwmRequest);
                    return;

                case clientData:
                    restLoadClientData(pwmRequest, peopleSearchConfiguration);
                    return;

                case orgChartData:
                    restOrgChartData(pwmRequest, peopleSearchConfiguration);
                    return;
            }
        }

        if (pwmRequest.getURL().isPublicUrl()) {
            pwmRequest.setFlag(PwmRequest.Flag.HIDE_IDLE, true);
            pwmRequest.setFlag(PwmRequest.Flag.NO_IDLE_TIMEOUT, true);
        }
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.PEOPLE_SEARCH);
    }

    private void restLoadClientData(
            final PwmRequest pwmRequest,
            final PeopleSearchConfiguration peopleSearchConfiguration

    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {

        final Map<String, String> searchColumns = new LinkedHashMap<>();
        final List<FormConfiguration> searchForm = pwmRequest.getConfig().readSettingAsForm(PwmSetting.PEOPLE_SEARCH_RESULT_FORM);
        for (final FormConfiguration formConfiguration : searchForm) {
            searchColumns.put(formConfiguration.getName(),
                    formConfiguration.getLabel(pwmRequest.getLocale()));
        }

        final HashMap<String,Object> returnValues = new HashMap<>();
        returnValues.put("peoplesearch_search_columns", searchColumns);
        returnValues.put("peoplesearch_enablePhoto", peopleSearchConfiguration.isPhotosEnabled());

        final RestResultBean restResultBean = new RestResultBean(returnValues);
        LOGGER.trace(pwmRequest, "returning clientData: " + JsonUtil.serialize(restResultBean));
        pwmRequest.outputJsonResult(restResultBean);
    }


    private void restSearchRequest(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final String bodyString = pwmRequest.readRequestBodyAsString();
        final Map<String, String> valueMap = JsonUtil.deserializeStringMap(bodyString);

        final String username = Validator.sanitizeInputValue(pwmRequest.getConfig(), valueMap.get("username"), 1024);
        final CacheKey cacheKey = makeCacheKey(pwmRequest, "search", username);
        {
            final String cachedOutput = pwmRequest.getPwmApplication().getCacheService().get(cacheKey);
            if (cachedOutput != null) {
                final SearchResultBean resultOutput = JsonUtil.deserialize(cachedOutput, SearchResultBean.class);
                pwmRequest.outputJsonResult(new RestResultBean(resultOutput));
                StatisticsManager.incrementStat(pwmRequest, Statistic.PEOPLESEARCH_CACHE_HITS);
                return;
            } else {
                StatisticsManager.incrementStat(pwmRequest, Statistic.PEOPLESEARCH_CACHE_MISSES);
            }
        }

        final SearchResultBean outputData = makeSearchResultsImpl(pwmRequest, username);
        final RestResultBean restResultBean = new RestResultBean(outputData);
        pwmRequest.outputJsonResult(restResultBean);
        StatisticsManager.incrementStat(pwmRequest, Statistic.PEOPLESEARCH_SEARCHES);
        storeDataInCache(pwmRequest.getPwmApplication(), cacheKey, outputData);
    }

    private SearchResultBean makeSearchResultsImpl(
            final PwmRequest pwmRequest,
            final String username
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final Date startTime = new Date();

        if (username == null || username.length() < 1) {
            return new SearchResultBean();
        }

        final boolean useProxy = useProxy(pwmRequest);
        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmRequest);
        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        searchConfiguration.setContexts(
                pwmRequest.getConfig().readSettingAsStringArray(PwmSetting.PEOPLE_SEARCH_SEARCH_BASE));
        searchConfiguration.setEnableContextValidation(false);
        searchConfiguration.setUsername(username);
        searchConfiguration.setEnableValueEscaping(false);
        searchConfiguration.setFilter(getSearchFilter(pwmRequest.getConfig()));
        if (!useProxy) {
            searchConfiguration.setLdapProfile(pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity().getLdapProfileID());
            searchConfiguration.setChaiProvider(pwmRequest.getPwmSession().getSessionManager().getChaiProvider());
        }

        final UserSearchEngine.UserSearchResults results;
        final boolean sizeExceeded;
        try {
            final List<FormConfiguration> searchForm = pwmRequest.getConfig().readSettingAsForm(
                    PwmSetting.PEOPLE_SEARCH_RESULT_FORM);
            final int maxResults = (int) pwmRequest.getConfig().readSettingAsLong(
                    PwmSetting.PEOPLE_SEARCH_RESULT_LIMIT);
            final Locale locale = pwmRequest.getLocale();
            results = userSearchEngine.performMultiUserSearchFromForm(locale, searchConfiguration, maxResults, searchForm);
            sizeExceeded = results.isSizeExceeded();
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInformation = e.getErrorInformation();
            LOGGER.error(pwmRequest.getSessionLabel(), errorInformation.toDebugStr());
            throw new PwmUnrecoverableException(errorInformation);
        }

        LOGGER.trace(pwmRequest.getPwmSession(), "finished rest peoplesearch search in " + TimeDuration.fromCurrent(
                startTime).asCompactString() + " not using cache, size=" + results.getResults().size());

        final SearchResultBean searchResultBean = new SearchResultBean();
        searchResultBean.setSearchResults(new ArrayList<>(results.resultsAsJsonOutput(pwmRequest.getPwmApplication())));
        searchResultBean.setSizeExceeded(sizeExceeded);
        return searchResultBean;
    }

    private static UserSearchEngine.UserSearchResults doDetailLookup(
            final PwmRequest pwmRequest,
            final PeopleSearchConfiguration peopleSearchConfiguration,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        final List<FormConfiguration> detailFormConfig = config.readSettingAsForm(PwmSetting.PEOPLE_SEARCH_DETAIL_FORM);
        final Map<String, String> attributeHeaderMap = UserSearchEngine.UserSearchResults.fromFormConfiguration(
                detailFormConfig, pwmRequest.getLocale());

        if (peopleSearchConfiguration.orgChartIsEnabled()) {
            final String orgChartParentAttr = config.readSettingAsString(PwmSetting.PEOPLE_SEARCH_ORGCHART_PARENT_ATTRIBUTE);
            if (!attributeHeaderMap.containsKey(orgChartParentAttr)) {
                attributeHeaderMap.put(orgChartParentAttr, orgChartParentAttr);
            }
            final String orgChartChildAttr = config.readSettingAsString(PwmSetting.PEOPLE_SEARCH_ORGCHART_CHILD_ATTRIBUTE);
            if (!attributeHeaderMap.containsKey(orgChartChildAttr)) {
                attributeHeaderMap.put(orgChartChildAttr, orgChartChildAttr);
            }
        }

        try {
            final ChaiUser theUser = getChaiUser(pwmRequest, userIdentity);
            final Map<String, String> values = theUser.readStringAttributes(attributeHeaderMap.keySet());
            return new UserSearchEngine.UserSearchResults(
                    attributeHeaderMap,
                    Collections.singletonMap(userIdentity, values),
                    false
            );
        } catch (ChaiException e) {
            LOGGER.error("unexpected error during detail lookup of '" + userIdentity + "', error: " + e.getMessage());
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.forChaiError(e.getErrorCode()),e.getMessage()));
        }
    }

    private void restOrgChartData(
            final PwmRequest pwmRequest,
            final PeopleSearchConfiguration peopleSearchConfiguration
    )
            throws IOException, PwmUnrecoverableException, ServletException
    {
        if (!peopleSearchConfiguration.orgChartIsEnabled()) {
            throw new PwmUnrecoverableException(PwmError.ERROR_SERVICE_NOT_AVAILABLE);
        }

        final Map<String, String> requestInputMap = pwmRequest.readBodyAsJsonStringMap();
        if (requestInputMap == null) {
            return;
        }
        final String userKey = requestInputMap.get("userKey");
        if (userKey == null || userKey.isEmpty()) {
            return;
        }
        final boolean asParent = Boolean.parseBoolean(requestInputMap.get("asParent"));
        final UserIdentity userIdentity = UserIdentity.fromObfuscatedKey(userKey, pwmRequest.getConfig());

        final UserIdentity parentIdentity;
        try {
            if (asParent) {
                parentIdentity = userIdentity;
            } else {
                final UserDetailBean userDetailBean = makeUserDetailRequestImpl(pwmRequest, peopleSearchConfiguration, userKey);
                parentIdentity = UserIdentity.fromObfuscatedKey(userDetailBean.getOrgChartParentKey(), pwmRequest.getConfig());
            }

            final OrgChartData orgChartData = makeOrgChartData(pwmRequest, parentIdentity);
            pwmRequest.outputJsonResult(new RestResultBean(orgChartData));
            StatisticsManager.incrementStat(pwmRequest, Statistic.PEOPLESEARCH_ORGCHART);
        } catch (PwmException e) {
            LOGGER.error(pwmRequest, "error generating user detail object: " + e.getMessage());
            pwmRequest.respondWithError(e.getErrorInformation());
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.forChaiError(e.getErrorCode()),e.getMessage()));
        }
    }

    private OrgChartData makeOrgChartData(
            final PwmRequest pwmRequest,
            final UserIdentity parentIdentity

    )
            throws PwmUnrecoverableException
    {
        final Date startTime = new Date();

        final CacheKey cacheKey = makeCacheKey(pwmRequest, "orgChartData", parentIdentity.toDelimitedKey());
        { // if value is cached then return;
            final String cachedOutput = pwmRequest.getPwmApplication().getCacheService().get(cacheKey);
            if (cachedOutput != null) {
                StatisticsManager.incrementStat(pwmRequest, Statistic.PEOPLESEARCH_CACHE_HITS);
                return JsonUtil.deserialize(cachedOutput, OrgChartData.class);
            } else {
                StatisticsManager.incrementStat(pwmRequest, Statistic.PEOPLESEARCH_CACHE_MISSES);
            }
        }

        final String parentAttribute = pwmRequest.getConfig().readSettingAsString(PwmSetting.PEOPLE_SEARCH_ORGCHART_PARENT_ATTRIBUTE);
        final String childAttribute = pwmRequest.getConfig().readSettingAsString(PwmSetting.PEOPLE_SEARCH_ORGCHART_CHILD_ATTRIBUTE);

        final OrgChartData orgChartData = new OrgChartData();
        final OrgChartReferenceBean parentReference = makeOrgChartReferenceForIdentity(pwmRequest, parentIdentity, parentAttribute);
        orgChartData.setParent(parentReference);

        final Map<String,OrgChartReferenceBean> sortedSiblings = new TreeMap<>();
        final List<UserIdentity> childIdentities = readUserDNAttributeValues(pwmRequest, parentIdentity, childAttribute);
        int counter = 0;
        for (final UserIdentity  childIdentity : childIdentities) {
            final OrgChartReferenceBean childReference = makeOrgChartReferenceForIdentity(pwmRequest, childIdentity, childAttribute);
            if (childReference != null) {
                if (childReference.getDisplayNames() != null && !childReference.getDisplayNames().isEmpty()) {
                    final String firstDisplayName = childReference.getDisplayNames().iterator().next();
                    sortedSiblings.put(firstDisplayName, childReference);
                } else {
                    sortedSiblings.put(String.valueOf(counter), childReference);
                }
                counter++;
            }
            orgChartData.setSiblings(new ArrayList<>(sortedSiblings.values()));
        }
        storeDataInCache(pwmRequest.getPwmApplication(), cacheKey, orgChartData);
        LOGGER.trace(pwmRequest, "completed building orgChartData in " + TimeDuration.fromCurrent(startTime).asCompactString());
        return orgChartData;
    }

    private void restUserDetailRequest(
            final PwmRequest pwmRequest,
            final PeopleSearchConfiguration peopleSearchConfiguration
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final Map<String, String> valueMap = pwmRequest.readBodyAsJsonStringMap();

        if (valueMap == null) {
            return;
        }

        final String userKey = valueMap.get("userKey");
        if (userKey == null || userKey.isEmpty()) {
            return;
        }

        try {
            final UserDetailBean detailData = makeUserDetailRequestImpl(pwmRequest, peopleSearchConfiguration, userKey);
            pwmRequest.outputJsonResult(new RestResultBean(detailData));
            pwmRequest.getPwmApplication().getStatisticsManager().incrementValue(Statistic.PEOPLESEARCH_DETAILS);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmRequest, "error generating user detail object: " + e.getMessage());
            pwmRequest.respondWithError(e.getErrorInformation());
        }

    }

    private UserDetailBean makeUserDetailRequestImpl(
            final PwmRequest pwmRequest,
            final PeopleSearchConfiguration peopleSearchConfiguration,
            final String userKey
    )
            throws PwmUnrecoverableException, IOException, ServletException, PwmOperationalException, ChaiUnavailableException
    {
        final Date startTime = new Date();
        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmRequest.getConfig());

        final CacheKey cacheKey = makeCacheKey(pwmRequest, "detail", userIdentity.toDelimitedKey());
        {
            final String cachedOutput = pwmRequest.getPwmApplication().getCacheService().get(cacheKey);
            if (cachedOutput != null) {
                StatisticsManager.incrementStat(pwmRequest, Statistic.PEOPLESEARCH_CACHE_HITS);
                return JsonUtil.deserialize(cachedOutput, UserDetailBean.class);
            } else {
                StatisticsManager.incrementStat(pwmRequest, Statistic.PEOPLESEARCH_CACHE_MISSES);
            }
        }

        try {
            checkIfUserIdentityViewable(pwmRequest, userIdentity);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmRequest.getPwmSession(), "error during detail results request while checking if requested userIdentity is within search scope: " + e.getMessage());
            throw e;
        }

        final UserSearchEngine.UserSearchResults detailResults = doDetailLookup(pwmRequest, peopleSearchConfiguration, userIdentity);
        final Map<String, String> searchResults = detailResults.getResults().get(userIdentity);

        final UserDetailBean userDetailBean = new UserDetailBean();
        userDetailBean.setUserKey(userKey);
        final List<FormConfiguration> detailFormConfig = pwmRequest.getConfig().readSettingAsForm( PwmSetting.PEOPLE_SEARCH_DETAIL_FORM);
        final Map<String,AttributeDetailBean> attributeBeans = convertResultMapToBeans(pwmRequest, userIdentity, detailFormConfig, searchResults);

        userDetailBean.setDetail(attributeBeans);
        final String photoURL = figurePhotoURL(pwmRequest, userIdentity);
        if (photoURL != null) {
            userDetailBean.setPhotoURL(photoURL);
        }
        final List<String> displayName = figureDisplaynames(pwmRequest, userIdentity);
        if (displayName != null) {
            userDetailBean.setDisplayNames(displayName);
        }

        if (peopleSearchConfiguration.orgChartIsEnabled()) {
            final String parentAttr = pwmRequest.getConfig().readSettingAsString(PwmSetting.PEOPLE_SEARCH_ORGCHART_PARENT_ATTRIBUTE);
            final String parentDN = searchResults.get(parentAttr);
            if (parentDN != null && !parentDN.isEmpty()) {
                userDetailBean.setHasOrgChart(true);
                final UserIdentity parentIdentity = new UserIdentity(parentDN,userIdentity.getLdapProfileID());
                userDetailBean.setOrgChartParentKey(parentIdentity.toObfuscatedKey(pwmRequest.getConfig()));
            } else {
                final String childAttr = pwmRequest.getConfig().readSettingAsString(PwmSetting.PEOPLE_SEARCH_ORGCHART_CHILD_ATTRIBUTE);
                final String childDN = searchResults.get(childAttr);
                if (childDN != null && !childDN.isEmpty()) {
                    userDetailBean.setHasOrgChart(true);
                    // no parent so use self as parent.
                    userDetailBean.setOrgChartParentKey(userIdentity.toObfuscatedKey(pwmRequest.getConfig()));
                }
            }
        }

        LOGGER.trace(pwmRequest.getPwmSession(), "finished building userDetail result in " + TimeDuration.fromCurrent(startTime).asCompactString());
        storeDataInCache(pwmRequest.getPwmApplication(), cacheKey, userDetailBean);
        return userDetailBean;
    }

    private static void storeDataInCache(
            final PwmApplication pwmApplication,
            final CacheKey cacheKey,
            final Serializable data
    )
            throws PwmUnrecoverableException
    {
        final long maxCacheSeconds = pwmApplication.getConfig().readSettingAsLong(PwmSetting.PEOPLE_SEARCH_MAX_CACHE_SECONDS);
        if (maxCacheSeconds > 0) {
            final CachePolicy cachePolicy = CachePolicy.makePolicyWithExpirationMS(maxCacheSeconds * 1000);
            pwmApplication.getCacheService().put(cacheKey, cachePolicy, JsonUtil.serialize(data));
        }
    }

    private static String figurePhotoURL(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final List<UserPermission> showPhotoPermission = pwmApplication.getConfig().readSettingAsUserPermission(PwmSetting.PEOPLE_SEARCH_PHOTO_QUERY_FILTER);
        if (!LdapPermissionTester.testUserPermissions(pwmApplication, pwmRequest.getSessionLabel(), userIdentity, showPhotoPermission)) {
            LOGGER.debug(pwmRequest, "detailed user data lookup for " + userIdentity.toString() + ", failed photo query filter, denying photo view");
            return null;
        }

        final String overrideURL = pwmApplication.getConfig().readSettingAsString(PwmSetting.PEOPLE_SEARCH_PHOTO_URL_OVERRIDE);
        try {
            if (overrideURL != null && !overrideURL.isEmpty()) {
                final MacroMachine macroMachine = getMacroMachine(pwmRequest, userIdentity);
                return macroMachine.expandMacros(overrideURL);
            }

            try {
                readPhotoDataFromLdap(pwmRequest, userIdentity);
            } catch (PwmOperationalException e) {
                LOGGER.debug(pwmRequest, "determined " + userIdentity + " does not have photo data available while generating detail data");
                return null;
            }
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(PwmError.forChaiError(e.getErrorCode()));
        }

        return "PeopleSearch?processAction=photo&userKey=" + userIdentity.toObfuscatedKey(pwmApplication.getConfig());
    }

    private static String figureDisplaynameValue(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final MacroMachine macroMachine = getMacroMachine(pwmRequest, userIdentity);
        final String settingValue = pwmRequest.getConfig().readSettingAsString(PwmSetting.PEOPLE_SEARCH_DISPLAY_NAME);
        return macroMachine.expandMacros(settingValue);
    }

    private static List<String> figureDisplaynames(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final List<String> displayLabels = new ArrayList<>();
        final List<String> displayStringSettings = pwmRequest.getConfig().readSettingAsStringArray(PwmSetting.PEOPLE_SEARCH_DISPLAY_NAMES_CARD_LABELS);
        if (displayStringSettings != null) {
            final MacroMachine macroMachine = getMacroMachine(pwmRequest, userIdentity);
            for (final String displayStringSetting : displayStringSettings) {
                final String displayLabel = macroMachine.expandMacros(displayStringSetting);
                displayLabels.add(displayLabel);
            }
        }
        return displayLabels;
    }


    private void processUserPhotoImageRequest(final PwmRequest pwmRequest)
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final String userKey = pwmRequest.readParameterAsString("userKey");
        if (userKey.length() < 1) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER, "userKey parameter is missing");
            LOGGER.error(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation, false);
            return;
        }


        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmRequest.getConfig());
        try {
            checkIfUserIdentityViewable(pwmRequest, userIdentity);
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED, "error during photo request while checking if requested userIdentity is within search scope: " + e.getMessage());
            LOGGER.error(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation, false);
            return;
        }

        LOGGER.debug(pwmRequest, "received user photo request to view user " + userIdentity.toString());

        final PhotoDataBean photoData;
        try {
            photoData = readPhotoDataFromLdap(pwmRequest, userIdentity);
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInformation = e.getErrorInformation();
            LOGGER.error(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation, false);
            return;
        }

        OutputStream outputStream = null;
        try {
            final long maxCacheSeconds = pwmRequest.getConfig().readSettingAsLong(PwmSetting.PEOPLE_SEARCH_MAX_CACHE_SECONDS);
            final HttpServletResponse resp = pwmRequest.getPwmResponse().getHttpServletResponse();
            resp.setContentType(photoData.getMimeType());
            resp.setDateHeader("Expires", System.currentTimeMillis() + (maxCacheSeconds * 1000l));
            resp.setHeader("Cache-Control", "public, max-age=" + maxCacheSeconds);

            outputStream = pwmRequest.getPwmResponse().getOutputStream();
            outputStream.write(photoData.getContents());

        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    private static Map<String,AttributeDetailBean> convertResultMapToBeans(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity,
            final List<FormConfiguration> detailForm,
            final Map<String, String> searchResults
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final Set<String> searchAttributes = getSearchAttributes(pwmRequest.getConfig());
        final Map<String,AttributeDetailBean> returnObj = new LinkedHashMap<>();
        for (FormConfiguration formConfiguration : detailForm) {
            if (formConfiguration.isRequired() || searchResults.containsKey(formConfiguration.getName())) {
                AttributeDetailBean bean = new AttributeDetailBean();
                bean.setName(formConfiguration.getName());
                bean.setLabel(formConfiguration.getLabel(pwmRequest.getLocale()));
                bean.setType(formConfiguration.getType());
                if (searchAttributes.contains(formConfiguration.getName())) {
                    bean.setSearchable(true);
                }
                if (formConfiguration.getType() == FormConfiguration.Type.userDN) {
                    if (searchResults.containsKey(formConfiguration.getName())) {
                        final List<UserIdentity> identityValues = readUserDNAttributeValues(pwmRequest, userIdentity, formConfiguration.getName());
                        final TreeMap<String, UserReferenceBean> userReferences = new TreeMap<>();
                        for (final UserIdentity loopIdentity : identityValues) {
                            final String displayValue = figureDisplaynameValue(pwmRequest, loopIdentity);
                            final UserReferenceBean userReference = new UserReferenceBean();
                            userReference.setUserKey(loopIdentity.toObfuscatedKey(pwmRequest.getConfig()));
                            userReference.setDisplayName(displayValue);
                            userReferences.put(displayValue, userReference);
                        }
                        bean.setUserReferences(userReferences.values());
                    }
                } else {
                    bean.setValue(searchResults.containsKey(formConfiguration.getName()) ? searchResults.get(
                            formConfiguration.getName()) : "");
                }
                returnObj.put(formConfiguration.getName(),bean);
            }
        }
        return returnObj;
    }

    private static boolean useProxy(final PwmRequest pwmRequest) {

        final boolean useProxy = pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_USE_PROXY);
        final boolean publicAccessEnabled = pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_ENABLE_PUBLIC);

        return useProxy || !pwmRequest.isAuthenticated() && publicAccessEnabled;

    }

    private static ChaiUser getChaiUser(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final boolean useProxy = useProxy(pwmRequest);
        return useProxy
                ? pwmRequest.getPwmApplication().getProxiedChaiUser(userIdentity)
                : pwmRequest.getPwmSession().getSessionManager().getActor(pwmRequest.getPwmApplication(), userIdentity);
    }

    private static MacroMachine getMacroMachine(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final ChaiUser chaiUser = getChaiUser(pwmRequest, userIdentity);
        final UserInfoBean userInfoBean;
        if (Boolean.parseBoolean(pwmRequest.getConfig().readAppProperty(AppProperty.PEOPLESEARCH_DISPLAYNAME_USEALLMACROS))) {
            final Locale locale = pwmRequest.getLocale();
            final ChaiProvider chaiProvider = pwmRequest.getPwmApplication().getProxiedChaiUser(userIdentity).getChaiProvider();
            userInfoBean = new UserInfoBean();
            final UserStatusReader userStatusReader = new UserStatusReader(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel());
            userStatusReader.populateUserInfoBean(userInfoBean, locale, userIdentity, chaiProvider);
        } else {
            userInfoBean = null;
        }
        UserDataReader userDataReader = new LdapUserDataReader(userIdentity, chaiUser);
        return new MacroMachine(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel(), userInfoBean, null, userDataReader);
    }

    private static void checkIfUserIdentityViewable(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws  PwmUnrecoverableException, PwmOperationalException
    {
        final String filterSetting = getSearchFilter(pwmRequest.getConfig());
        String filterString = filterSetting.replace(PwmConstants.VALUE_REPLACEMENT_USERNAME, "*");
        while (filterString.contains("**")) {
            filterString = filterString.replace("**", "*");
        }

        final boolean match = LdapPermissionTester.testQueryMatch(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel(), userIdentity, filterString);
        if (!match) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, "requested userDN is not available within configured search filter"));
        }
    }

    private static PhotoDataBean readPhotoDataFromLdap(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        final String attribute = pwmRequest.getConfig().readSettingAsString(PwmSetting.PEOPLE_SEARCH_PHOTO_ATTRIBUTE);
        if (attribute == null || attribute.isEmpty()) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, "ldap photo attribute is not configured"));
        }

        byte[] photoData;
        String mimeType;
        try {
            final ChaiUser chaiUser = getChaiUser(pwmRequest, userIdentity);
            final byte[][] photoAttributeData = chaiUser.readMultiByteAttribute(attribute);
            if (photoAttributeData == null || photoAttributeData.length == 0 || photoAttributeData[0].length == 0) {
                throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, "user has no photo data stored in LDAP attribute"));
            }
            photoData = photoAttributeData[0];
            mimeType = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(photoData));
        } catch (IOException | ChaiOperationException e) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_UNKNOWN, "error reading user photo ldap attribute: " + e.getMessage()));
        }
        return new PhotoDataBean(mimeType, photoData);
    }

    private static String getSearchFilter(final Configuration configuration) {
        final String configuredFilter = configuration.readSettingAsString(PwmSetting.PEOPLE_SEARCH_SEARCH_FILTER);
        if (configuredFilter != null && !configuredFilter.isEmpty()) {
            return configuredFilter;
        }

        final List<String> defaultObjectClasses = configuration.readSettingAsStringArray(PwmSetting.DEFAULT_OBJECT_CLASSES);
        final Set<String> searchAttributes = getSearchAttributes(configuration);
        final StringBuilder filter = new StringBuilder();
        filter.append("(&"); //open AND clause for objectclasses and attributes
        for (final String objectClass : defaultObjectClasses) {
            filter.append("(objectClass=").append(objectClass).append(")");
        }
        filter.append("(|"); // open OR clause for attributes
        for (final String searchAttribute : searchAttributes) {
            filter.append("(").append(searchAttribute).append("=*").append(PwmConstants.VALUE_REPLACEMENT_USERNAME).append("*)");
        }
        filter.append(")"); // close OR clause
        filter.append(")"); // close AND clause
        return filter.toString();
    }

    private static Set<String> getSearchAttributes(final Configuration configuration) {
        final List<String> searchResultForm = configuration.readSettingAsStringArray(PwmSetting.PEOPLE_SEARCH_SEARCH_ATTRIBUTES);
        return Collections.unmodifiableSet(new HashSet<>(searchResultForm));
    }

    private static OrgChartReferenceBean makeOrgChartReferenceForIdentity(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity,
            final String nextNodeAttribute
    )
            throws PwmUnrecoverableException
    {
        final OrgChartReferenceBean orgChartReferenceBean = new OrgChartReferenceBean();
        orgChartReferenceBean.setUserKey(userIdentity.toObfuscatedKey(pwmRequest.getConfig()));
        orgChartReferenceBean.setPhotoURL(figurePhotoURL(pwmRequest, userIdentity));

            final List<String> displayLabels = figureDisplaynames(pwmRequest, userIdentity);
            orgChartReferenceBean.setDisplayNames(displayLabels);

        orgChartReferenceBean.setHasMoreNodes(false);
        try {
            final UserDataReader userDataReader = new LdapUserDataReader(userIdentity, getChaiUser(pwmRequest, userIdentity));
            final String nextNodeValue = userDataReader.readStringAttribute(nextNodeAttribute);
            if (nextNodeValue != null && !nextNodeValue.isEmpty()) {
                orgChartReferenceBean.setHasMoreNodes(true);
            }
        } catch (ChaiException e) {
            LOGGER.debug(pwmRequest, "error reading nextNodeAttribute during orgChratReference construction: " + e.getMessage());
        }

        return orgChartReferenceBean;
    }

    private static List<UserIdentity> readUserDNAttributeValues(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity,
            final String attributeName
    )
            throws PwmUnrecoverableException
    {

        final List<UserIdentity> returnObj = new ArrayList<>();

        final int MAX_VALUES = Integer.parseInt(pwmRequest.getConfig().readAppProperty(AppProperty.PEOPLESEARCH_VALUE_MAXCOUNT));
        final ChaiUser chaiUser = getChaiUser(pwmRequest, userIdentity);
        final Set<String> ldapValues;
        try {
            ldapValues = chaiUser.readMultiStringAttribute(attributeName);
        } catch (ChaiOperationException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE, "error reading attribute value '" + attributeName + "', error:" +  e.getMessage()));
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage()));
        }


        final boolean checkUserDNValues = Boolean.parseBoolean(pwmRequest.getConfig().readAppProperty(AppProperty.PEOPLESEARCH_MAX_VALUE_VERIFYUSERDN));
        for (final String userDN : ldapValues) {
            final UserIdentity loopIdentity = new UserIdentity(userDN, userIdentity.getLdapProfileID());
            if (returnObj.size() < MAX_VALUES) {
                try {
                    if (checkUserDNValues) {
                        checkIfUserIdentityViewable(pwmRequest, loopIdentity);
                    }
                    returnObj.add(loopIdentity);
                } catch (PwmOperationalException e) {
                    LOGGER.debug(pwmRequest, "discarding userDN " + userDN + " from attribute " + attributeName + " because it does not match search filter");
                }
            } else {
                LOGGER.trace(pwmRequest, "discarding userDN " + userDN + " from attribute " + attributeName + " because maximum value count has been reached");
            }

        }
        return returnObj;
    }

    private CacheKey makeCacheKey(
            final PwmRequest pwmRequest,
            final String operationIdentifer,
            final String dataIdentifer
    )
            throws PwmUnrecoverableException
    {
        final UserIdentity userIdentity;
        if (pwmRequest.isAuthenticated() && !useProxy(pwmRequest)) {
            userIdentity = pwmRequest.getUserInfoIfLoggedIn();
        } else {
            userIdentity = null;
        }
        return CacheKey.makeCacheKey(
                this.getClass(),
                userIdentity,
                operationIdentifer + "|" + SecureHelper.hash(dataIdentifer, SecureHelper.HashAlgorithm.SHA1));
    }

    private static class PeopleSearchConfiguration {
        private final Configuration configuration;

        public PeopleSearchConfiguration(Configuration configuration) {
            this.configuration = configuration;
        }

        public String getPhotoAttribute() {
            return configuration.readSettingAsString(PwmSetting.PEOPLE_SEARCH_PHOTO_ATTRIBUTE);
        }

        public String getPhotoUrlOverride() {
            return configuration.readSettingAsString(PwmSetting.PEOPLE_SEARCH_PHOTO_URL_OVERRIDE);
        }

        public boolean isPhotosEnabled() {
            return (getPhotoAttribute() != null
                    && !getPhotoAttribute().isEmpty())
                    ||
                    (getPhotoUrlOverride() != null
                    && !getPhotoUrlOverride().isEmpty());
        }

        public boolean orgChartIsEnabled() {
            final String orgChartParentAttr = configuration.readSettingAsString(PwmSetting.PEOPLE_SEARCH_ORGCHART_PARENT_ATTRIBUTE);
            final String orgChartChildAttr = configuration.readSettingAsString(PwmSetting.PEOPLE_SEARCH_ORGCHART_CHILD_ATTRIBUTE);
            return orgChartParentAttr != null && !orgChartParentAttr.isEmpty() && orgChartChildAttr != null && !orgChartChildAttr.isEmpty();
        }
    }
}
