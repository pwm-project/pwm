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

package password.pwm.http.servlet;

import com.google.gson.reflect.TypeToken;
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
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.ldap.*;
import password.pwm.util.JsonUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.cache.CacheKey;
import password.pwm.util.cache.CachePolicy;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URLConnection;
import java.util.*;

public class PeopleSearchServlet extends PwmServlet {

    private static final PwmLogger LOGGER = PwmLogger.forClass(PeopleSearchServlet.class);

    public static class UserDetailBean implements Serializable {
        private String displayName;
        private String userKey;
        private Map<String,AttributeDetailBean> detail;
        private String photoURL;
        private boolean hasOrgChart;

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getUserKey() {
            return userKey;
        }

        public void setUserKey(String userKey) {
            this.userKey = userKey;
        }

        public Map<String, AttributeDetailBean> getDetail() {
            return detail;
        }

        public void setDetail(Map<String, AttributeDetailBean> detail) {
            this.detail = detail;
        }

        public String getPhotoURL() {
            return photoURL;
        }

        public void setPhotoURL(String photoURL) {
            this.photoURL = photoURL;
        }

        public boolean isHasOrgChart() {
            return hasOrgChart;
        }

        public void setHasOrgChart(boolean hasOrgChart) {
            this.hasOrgChart = hasOrgChart;
        }
    }

    public static class AttributeDetailBean implements Serializable {
        private String name;
        private String label;
        private FormConfiguration.Type type;
        private String value;
        private Collection<UserReferenceBean> userReferences;
        private boolean searchable;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public FormConfiguration.Type getType() {
            return type;
        }

        public void setType(FormConfiguration.Type type) {
            this.type = type;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public Collection<UserReferenceBean> getUserReferences() {
            return userReferences;
        }

        public void setUserReferences(Collection<UserReferenceBean> userReferences) {
            this.userReferences = userReferences;
        }

        public boolean isSearchable() {
            return searchable;
        }

        public void setSearchable(boolean searchable) {
            this.searchable = searchable;
        }


    }

    public static class UserTreeData implements Serializable {
        private UserTreeReferenceBean parent;
        private List<UserTreeReferenceBean> siblings;

        public UserTreeReferenceBean getParent() {
            return parent;
        }

        public void setParent(UserTreeReferenceBean parent) {
            this.parent = parent;
        }

        public List<UserTreeReferenceBean> getSiblings() {
            return siblings;
        }

        public void setSiblings(List<UserTreeReferenceBean> siblings) {
            this.siblings = siblings;
        }
    }

    public static class UserTreeReferenceBean extends UserReferenceBean {
        public String photoURL;
        public boolean hasMoreNodes;

        public String getPhotoURL() {
            return photoURL;
        }

        public void setPhotoURL(String photoURL) {
            this.photoURL = photoURL;
        }

        public boolean isHasMoreNodes() {
            return hasMoreNodes;
        }

        public void setHasMoreNodes(boolean hasMoreNodes) {
            this.hasMoreNodes = hasMoreNodes;
        }
    }

    public static class UserReferenceBean implements Serializable {
        private String userKey;
        private String displayName;

        public String getUserKey() {
            return userKey;
        }

        public void setUserKey(String userKey) {
            this.userKey = userKey;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    private static class PhotoData {
        private final String mimeType;
        private byte[] contents;

        private PhotoData(String mimeType, byte[] contents) {
            this.mimeType = mimeType;
            this.contents = contents;
        }

        public String getMimeType() {
            return mimeType;
        }

        public byte[] getContents() {
            return contents;
        }
    }

    public enum PeopleSearchActions implements ProcessAction {
        search(HttpMethod.POST),
        detail(HttpMethod.POST),
        photo(HttpMethod.GET),
        clientData(HttpMethod.GET),
        userTreeData(HttpMethod.POST),

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
        final boolean publicAccessEnabled = pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_ENABLE_PUBLIC);
        if (!publicAccessEnabled) {
            if (!pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_ENABLE)) {
                pwmRequest.respondWithError(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE));
                return;
            }

            if (!pwmRequest.getPwmSession().getSessionManager().checkPermission(pwmRequest.getPwmApplication(), Permission.PEOPLE_SEARCH)) {
                pwmRequest.respondWithError(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED));
                return;
            }
        }

        final int peopleSearchIdleTimeout = (int)pwmRequest.getConfig().readSettingAsLong(PwmSetting.PEOPLE_SEARCH_IDLE_TIMEOUT_SECONDS);
        if (peopleSearchIdleTimeout > 0 && pwmRequest.getURL().isPrivateUrl()) {
            pwmRequest.getPwmSession().setSessionTimeout(pwmRequest.getHttpServletRequest().getSession(), peopleSearchIdleTimeout);
        }

        final PeopleSearchActions peopleSearchAction = this.readProcessAction(pwmRequest);
        if (peopleSearchAction != null) {
            switch (peopleSearchAction) {
                case search:
                    restSearchRequest(pwmRequest);
                    return;

                case detail:
                    restUserDetailRequest(pwmRequest);
                    return;

                case photo:
                    processUserPhotoImageRequest(pwmRequest);
                    return;

                case clientData:
                    restLoadClientData(pwmRequest);
                    return;

                case userTreeData:
                    restUserTreeData(pwmRequest);
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
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {

        final Map<String, String> searchColumns = new LinkedHashMap<>();
        final String photoStyle = pwmRequest.getConfig().readSettingAsString(PwmSetting.PEOPLE_SEARCH_PHOTO_STYLE_ATTR);
        final List<FormConfiguration> searchForm = pwmRequest.getConfig().readSettingAsForm(PwmSetting.PEOPLE_SEARCH_RESULT_FORM);
        for (final FormConfiguration formConfiguration : searchForm) {
            searchColumns.put(formConfiguration.getName(),
                    formConfiguration.getLabel(pwmRequest.getLocale()));
        }

        final boolean orgChartEnabled = orgChartIsEnabled(pwmRequest.getConfig());

        final HashMap<String,Object> returnValues = new HashMap<>();
        returnValues.put("peoplesearch_search_columns",searchColumns);
        returnValues.put("photo_style_attribute",photoStyle);
        returnValues.put("peoplesearch_orgChart_enabled",orgChartEnabled);

        final RestResultBean restResultBean = new RestResultBean(returnValues);
        LOGGER.trace(pwmRequest, "returning clientData: " + JsonUtil.serialize(restResultBean));
        pwmRequest.outputJsonResult(restResultBean);
    };


    private void restSearchRequest(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException {
        final Date startTime = new Date();
        final String bodyString = pwmRequest.readRequestBodyAsString();
        final Map<String, String> valueMap = JsonUtil.deserializeStringMap(bodyString);

        final String username = Validator.sanitizeInputValue(pwmRequest.getConfig(), valueMap.get("username"), 1024);
        final boolean useProxy = useProxy(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession());
        final CacheKey cacheKey = CacheKey.makeCacheKey(
                this.getClass(),
                useProxy ? null : pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity(),
                "search-" + username
        );
        {
            final String cachedOutput = pwmRequest.getPwmApplication().getCacheService().get(cacheKey);
            if (cachedOutput != null) {
                final HashMap<String, Object> resultOutput = JsonUtil.deserialize(cachedOutput,
                        new TypeToken<HashMap<String, Object>>() {}
                );
                final RestResultBean restResultBean = new RestResultBean();
                restResultBean.setData(new LinkedHashMap<>(resultOutput));
                pwmRequest.outputJsonResult(restResultBean);
                LOGGER.trace(pwmRequest.getPwmSession(), "finished rest peoplesearch search using CACHE in " + TimeDuration.fromCurrent(
                        startTime).asCompactString() + ", size=" + resultOutput.size());

                if (pwmRequest.getPwmApplication().getStatisticsManager() != null) {
                    pwmRequest.getPwmApplication().getStatisticsManager().incrementValue(Statistic.PEOPLESEARCH_SEARCHES);
                }

                return;
            }
        }

        final List<FormConfiguration> searchForm = pwmRequest.getConfig().readSettingAsForm(
                PwmSetting.PEOPLE_SEARCH_RESULT_FORM);
        final int maxResults = (int) pwmRequest.getConfig().readSettingAsLong(
                PwmSetting.PEOPLE_SEARCH_RESULT_LIMIT);

        if (username == null || username.length() < 1) {
            final HashMap<String, Object> emptyResults = new HashMap<>();
            emptyResults.put("searchResults", new ArrayList<Map<String, String>>());
            emptyResults.put("sizeExceeded", false);
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(emptyResults);
            pwmRequest.outputJsonResult(restResultBean);
            return;
        }

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
            final Locale locale = pwmRequest.getLocale();
            results = userSearchEngine.performMultiUserSearchFromForm(locale, searchConfiguration, maxResults, searchForm);
            sizeExceeded = results.isSizeExceeded();
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInformation = e.getErrorInformation();
            LOGGER.error(pwmRequest.getSessionLabel(), errorInformation.toDebugStr());
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(new ArrayList<Map<String, String>>());
            pwmRequest.outputJsonResult(restResultBean);
            return;
        }

        final LinkedHashMap<String, Object> outputData = new LinkedHashMap<>();
        outputData.put("searchResults", new ArrayList<>(results.resultsAsJsonOutput(pwmRequest.getPwmApplication())));
        outputData.put("sizeExceeded", sizeExceeded);

        final RestResultBean restResultBean = new RestResultBean(outputData);
        pwmRequest.outputJsonResult(restResultBean);
        final long maxCacheSeconds = pwmRequest.getConfig().readSettingAsLong(PwmSetting.PEOPLE_SEARCH_MAX_CACHE_SECONDS);
        if (maxCacheSeconds > 0) {
            final Date expiration = new Date(System.currentTimeMillis() * maxCacheSeconds * 1000);
            pwmRequest.getPwmApplication().getCacheService().put(cacheKey, CachePolicy.makePolicy(expiration), JsonUtil.serialize(outputData));
        }

        if (pwmRequest.getPwmApplication().getStatisticsManager() != null) {
            pwmRequest.getPwmApplication().getStatisticsManager().incrementValue(Statistic.PEOPLESEARCH_SEARCHES);
        }

        LOGGER.trace(pwmRequest.getPwmSession(), "finished rest peoplesearch search in " + TimeDuration.fromCurrent(
                startTime).asCompactString() + " using CACHE, size=" + results.getResults().size());
    }

    private static UserSearchEngine.UserSearchResults doDetailLookup(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        final List<FormConfiguration> detailFormConfig = config.readSettingAsForm(PwmSetting.PEOPLE_SEARCH_DETAIL_FORM);
        final Map<String, String> attributeHeaderMap = UserSearchEngine.UserSearchResults.fromFormConfiguration(
                detailFormConfig, pwmSession.getSessionStateBean().getLocale());

        if (orgChartIsEnabled(pwmApplication.getConfig())) {
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
            final ChaiUser theUser = useProxy(pwmApplication,pwmSession)
                    ? pwmApplication.getProxiedChaiUser(userIdentity)
                    : pwmSession.getSessionManager().getActor(pwmApplication, userIdentity);
            final Map<String, String> values = theUser.readStringAttributes(attributeHeaderMap.keySet());
            return new UserSearchEngine.UserSearchResults(attributeHeaderMap,
                    Collections.singletonMap(userIdentity, values), false);
        } catch (ChaiException e) {
            LOGGER.error("unexpected error during detail lookup of '" + userIdentity + "', error: " + e.getMessage());
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.forChaiError(e.getErrorCode()),e.getMessage()));
        }
    }

    private void restUserTreeData(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException, ServletException
    {
        if (!orgChartIsEnabled(pwmRequest.getConfig())) {
            throw new PwmUnrecoverableException(PwmError.ERROR_SERVICE_NOT_AVAILABLE);
        }

        final Map<String,String> requestInputMap = pwmRequest.readBodyAsJsonStringMap();
        if (requestInputMap == null) {
            return;
        }
        final String userKey = requestInputMap.get("userKey");
        if (userKey == null || userKey.isEmpty()) {
            return;
        }
        final boolean asParent = Boolean.parseBoolean(requestInputMap.get("asParent"));

        final String parentAttribute = pwmRequest.getConfig().readSettingAsString(PwmSetting.PEOPLE_SEARCH_ORGCHART_PARENT_ATTRIBUTE);
        final String childAttribute = pwmRequest.getConfig().readSettingAsString(PwmSetting.PEOPLE_SEARCH_ORGCHART_CHILD_ATTRIBUTE);
        try {

            UserDetailBean parentDetail = null;
            if (asParent) {
                parentDetail = makeUserDetailRequestImpl(pwmRequest, userKey);
            } else {
                final UserDetailBean selfDetailData = makeUserDetailRequestImpl(pwmRequest, userKey);
                if (selfDetailData.getDetail().containsKey(parentAttribute)) {
                    final UserReferenceBean parentReference = selfDetailData.getDetail().get(parentAttribute).getUserReferences().iterator().next();
                    parentDetail = makeUserDetailRequestImpl(pwmRequest, parentReference.getUserKey());
                }
            }
            final UserTreeData userTreeData = new UserTreeData();
            if (parentDetail != null) {
                final UserTreeReferenceBean managerTreeReference = userDetailToTreeReference(parentDetail, parentAttribute);
                userTreeData.setParent(managerTreeReference);

                if (parentDetail.getDetail() != null) {
                    if (parentDetail.getDetail().containsKey(childAttribute)) {
                        final List<UserTreeReferenceBean> siblings = new ArrayList<>();
                        for (final UserReferenceBean siblingReferenceBean : parentDetail.getDetail().get(childAttribute).getUserReferences()) {
                            final UserDetailBean siblingDetail = makeUserDetailRequestImpl(pwmRequest, siblingReferenceBean.getUserKey());
                            final UserTreeReferenceBean siblingTreeReferenceBean = userDetailToTreeReference(siblingDetail, childAttribute);
                            siblings.add(siblingTreeReferenceBean);
                        }
                        userTreeData.setSiblings(siblings);
                    }
                }
            }
            pwmRequest.outputJsonResult(new RestResultBean(userTreeData));
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmRequest, "error generating user detail object: " + e.getMessage());
            pwmRequest.respondWithError(e.getErrorInformation());
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.forChaiError(e.getErrorCode()),e.getMessage()));
        }
    }


    private void restUserDetailRequest(
            final PwmRequest pwmRequest
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
            final UserDetailBean detailData = makeUserDetailRequestImpl(pwmRequest, userKey);
            pwmRequest.outputJsonResult(new RestResultBean(detailData));
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmRequest, "error generating user detail object: " + e.getMessage());
            pwmRequest.respondWithError(e.getErrorInformation());
        }

    }

    private UserDetailBean makeUserDetailRequestImpl(
            final PwmRequest pwmRequest,
            final String userKey
    )
            throws PwmUnrecoverableException, IOException, ServletException, PwmOperationalException, ChaiUnavailableException
    {
        final Date startTime = new Date();
        final boolean useProxy = useProxy(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession());
        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmRequest.getConfig());

        final CacheKey cacheKey = CacheKey.makeCacheKey(
                this.getClass(),
                useProxy ? null : pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity(),
                "detail-" + userIdentity.toDelimitedKey()
        );
        {
            final String cachedOutput = pwmRequest.getPwmApplication().getCacheService().get(cacheKey);
            if (cachedOutput != null) {
                final UserDetailBean resultOutput = JsonUtil.deserialize(cachedOutput, UserDetailBean.class);
                final RestResultBean restResultBean = new RestResultBean(resultOutput);
                LOGGER.debug(pwmRequest.getPwmSession(), "finished rest detail request in " + TimeDuration.fromCurrent(
                        startTime).asCompactString() + " using cached details, results=" + JsonUtil.serialize(restResultBean));

                if (pwmRequest.getPwmApplication().getStatisticsManager() != null) {
                    pwmRequest.getPwmApplication().getStatisticsManager().incrementValue(Statistic.PEOPLESEARCH_DETAILS);
                }

                return resultOutput;
            }
        }

        try {
            checkIfUserIdentityViewable(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), userIdentity);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmRequest.getPwmSession(), "error during detail results request while checking if requested userIdentity is within search scope: " + e.getMessage());
            throw e;
        }

        final UserSearchEngine.UserSearchResults detailResults = doDetailLookup(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), userIdentity);
        final Map<String, String> searchResults = detailResults.getResults().get(userIdentity);

        final UserDetailBean userDetailBean = new UserDetailBean();
        userDetailBean.setUserKey(userKey);
        final List<FormConfiguration> detailFormConfig = pwmRequest.getConfig().readSettingAsForm( PwmSetting.PEOPLE_SEARCH_DETAIL_FORM);
        final Map<String,AttributeDetailBean> attributeBeans = convertResultMapToBeans(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), userIdentity,
                detailFormConfig, searchResults);

        userDetailBean.setDetail(attributeBeans);
        final String photoURL = figurePhotoURL(pwmRequest.getPwmApplication(), pwmRequest, userIdentity);
        if (photoURL != null) {
            userDetailBean.setPhotoURL(photoURL);
        }
        final String displayName = figureDisplaynameValue(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), userIdentity);
        if (displayName != null) {
            userDetailBean.setDisplayName(displayName);
        }

        if (orgChartIsEnabled(pwmRequest.getConfig())) {
            final String parentAttr = pwmRequest.getConfig().readSettingAsString(PwmSetting.PEOPLE_SEARCH_ORGCHART_PARENT_ATTRIBUTE);
            if (searchResults.containsKey(parentAttr)) {
                userDetailBean.setHasOrgChart(true);
            }
        }

        LOGGER.debug(pwmRequest.getPwmSession(), "finished non-cached rest detail request in " + TimeDuration.fromCurrent(
                startTime).asCompactString() + ", results=" + JsonUtil.serialize(userDetailBean));

        final long maxCacheSeconds = pwmRequest.getConfig().readSettingAsLong(PwmSetting.PEOPLE_SEARCH_MAX_CACHE_SECONDS);
        if (maxCacheSeconds > 0) {
            final Date expiration = new Date(System.currentTimeMillis() * maxCacheSeconds * 1000);
            pwmRequest.getPwmApplication().getCacheService().put(cacheKey, CachePolicy.makePolicy(expiration),
                    JsonUtil.serialize(userDetailBean));
        }

        StatisticsManager.incrementStat(pwmRequest, Statistic.PEOPLESEARCH_SEARCHES);
        return userDetailBean;
    }

    private static String figurePhotoURL(
            final PwmApplication pwmApplication,
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException, ChaiUnavailableException {
        final List<UserPermission> showPhotoPermission = pwmApplication.getConfig().readSettingAsUserPermission(PwmSetting.PEOPLE_SEARCH_PHOTO_QUERY_FILTER);
        if (!LdapPermissionTester.testUserPermissions(pwmApplication, pwmRequest.getSessionLabel(), userIdentity, showPhotoPermission)) {
            LOGGER.debug(pwmRequest, "detailed user data lookup for " + userIdentity.toString() + ", failed photo query filter, denying photo view");
            return null;
        }

        final String overrideURL = pwmApplication.getConfig().readSettingAsString(PwmSetting.PEOPLE_SEARCH_PHOTO_URL_OVERRIDE);
        if (overrideURL != null && !overrideURL.isEmpty()) {
            final MacroMachine macroMachine = getMacroMachine(pwmApplication, pwmRequest.getPwmSession(), userIdentity);
            return macroMachine.expandMacros(overrideURL);
        }

        try {
            readPhotoDataFromLdap(pwmRequest, userIdentity);
        } catch (PwmOperationalException e) {
            LOGGER.debug(pwmRequest, "determined " + userIdentity + " does not have photo data available while generating detail data");
            return null;
        }

        return "PeopleSearch?processAction=photo&userKey=" + userIdentity.toObfuscatedKey(pwmApplication.getConfig());
    }

    private static String figureDisplaynameValue(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException, ChaiUnavailableException {
        final MacroMachine macroMachine = getMacroMachine(pwmApplication, pwmSession, userIdentity);
        final String settingValue = pwmApplication.getConfig().readSettingAsString(
                PwmSetting.PEOPLE_SEARCH_DISPLAY_NAME);
        return macroMachine.expandMacros(settingValue);
    }


    private void processUserPhotoImageRequest(final PwmRequest pwmRequest)
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException {
        final String userKey = pwmRequest.readParameterAsString("userKey");
        if (userKey.length() < 1) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER, "userKey parameter is missing");
            LOGGER.error(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation, false);
            return;
        }


        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmRequest.getConfig());
        try {
            checkIfUserIdentityViewable(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), userIdentity);
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED, "error during photo request while checking if requested userIdentity is within search scope: " + e.getMessage());
            LOGGER.error(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation, false);
            return;
        }

        LOGGER.info(pwmRequest, "received user photo request by "
                + pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity().toString() + " for user " + userIdentity.toString());

        final PhotoData photoData;
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
            pwmRequest.getPwmResponse().getHttpServletResponse().setContentType(photoData.getMimeType());
            outputStream = pwmRequest.getPwmResponse().getOutputStream();
            outputStream.write(photoData.getContents());
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    private static Map<String,AttributeDetailBean> convertResultMapToBeans(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity,
            final List<FormConfiguration> detailForm,
            final Map<String, String> searchResults
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final int MAX_VALUES = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.PEOPLESEARCH_MAX_VALUE_COUNT));
        final Set<String> searchAttributes = getSearchAttributes(pwmApplication.getConfig());
        final Map<String,AttributeDetailBean> returnObj = new LinkedHashMap<>();
        for (FormConfiguration formConfiguration : detailForm) {
            if (formConfiguration.isRequired() || searchResults.containsKey(formConfiguration.getName())) {
                AttributeDetailBean bean = new AttributeDetailBean();
                bean.setName(formConfiguration.getName());
                bean.setLabel(formConfiguration.getLabel(pwmSession.getSessionStateBean().getLocale()));
                bean.setType(formConfiguration.getType());
                if (searchAttributes.contains(formConfiguration.getName())) {
                    bean.setSearchable(true);
                }
                if (formConfiguration.getType() == FormConfiguration.Type.userDN) {
                    if (searchResults.containsKey(formConfiguration.getName())) {
                        final ChaiUser chaiUser = getChaiUser(pwmApplication, pwmSession, userIdentity);
                        final Set<String> values;
                        try {
                            values = chaiUser.readMultiStringAttribute(formConfiguration.getName());
                            final TreeMap<String, UserReferenceBean> userReferences = new TreeMap<>();
                            for (final String value : values) {
                                if (userReferences.size() < MAX_VALUES) {
                                    final UserIdentity loopIdentity = new UserIdentity(value,
                                            userIdentity.getLdapProfileID());
                                    final String displayValue = figureDisplaynameValue(pwmApplication, pwmSession,
                                            loopIdentity);
                                    final UserReferenceBean userReference = new UserReferenceBean();
                                    userReference.setUserKey(loopIdentity.toObfuscatedKey(pwmApplication.getConfig()));
                                    userReference.setDisplayName(displayValue);
                                    userReferences.put(displayValue, userReference);
                                }
                            }
                            bean.setUserReferences(userReferences.values());
                        } catch (ChaiOperationException e) {
                            LOGGER.error(pwmSession, "error during user detail lookup: " + e.getMessage());
                        }
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

    private static boolean useProxy(final PwmApplication pwmApplication, final PwmSession pwmSession) {

        final boolean useProxy = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_USE_PROXY);
        final boolean publicAccessEnabled = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_ENABLE_PUBLIC);

        if (useProxy) {
            return true;
        }

        return !pwmSession.getSessionStateBean().isAuthenticated() && publicAccessEnabled;
    }

    private static ChaiUser getChaiUser(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException {
        final boolean useProxy = useProxy(pwmApplication, pwmSession);
        return useProxy
                ? pwmApplication.getProxiedChaiUser(userIdentity)
                : pwmSession.getSessionManager().getActor(pwmApplication, userIdentity);

    }

    private static MacroMachine getMacroMachine(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException {
        final ChaiUser chaiUser = getChaiUser(pwmApplication, pwmSession, userIdentity);
        final UserInfoBean userInfoBean;
        if (Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(AppProperty.PEOPLESEARCH_DISPLAYNAME_USEALLMACROS))) {
            final Locale locale = pwmSession.getSessionStateBean().getLocale();
            final ChaiProvider chaiProvider = pwmApplication.getProxiedChaiUser(userIdentity).getChaiProvider();
            userInfoBean = new UserInfoBean();
            final UserStatusReader userStatusReader = new UserStatusReader(pwmApplication, pwmSession.getLabel());
            userStatusReader.populateUserInfoBean(userInfoBean, locale, userIdentity, chaiProvider);
        } else {
            userInfoBean = null;
        }
        UserDataReader userDataReader = new LdapUserDataReader(userIdentity, chaiUser);
        return new MacroMachine(pwmApplication, pwmSession.getLabel(), userInfoBean, null, userDataReader);
    }

    private static void checkIfUserIdentityViewable(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity
    )
            throws  PwmUnrecoverableException, PwmOperationalException {
        final String filterSetting = getSearchFilter(pwmApplication.getConfig());
        String filterString = filterSetting.replace(PwmConstants.VALUE_REPLACEMENT_USERNAME, "*");
        while (filterString.contains("**")) {
            filterString = filterString.replace("**", "*");
        }

        final boolean match = LdapPermissionTester.testQueryMatch(pwmApplication, pwmSession.getLabel(), userIdentity, filterString);
        if (!match) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, "requested userDN is not available within configured search filter"));
        }
    }

    private static PhotoData readPhotoDataFromLdap(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        final String attribute = pwmRequest.getConfig().readSettingAsString(PwmSetting.PEOPLE_SEARCH_PHOTO_ATTRIBUTE);
        if (attribute == null || attribute.isEmpty()) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, "ldap photo attribute is not configured"));
        }

        byte[][] photoData;
        String mimeType;
        try {
            final ChaiUser chaiUser = getChaiUser(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), userIdentity);
            photoData = chaiUser.readMultiByteAttribute(attribute);
            if (photoData == null || photoData.length == 0 || photoData[0].length == 0) {
                throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED, "user has no photo data stored in LDAP attribute"));
            }
            mimeType = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(photoData[0]));
        } catch (IOException | ChaiOperationException e) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_UNKNOWN, "error reading user photo ldap attribute: " + e.getMessage()));
        }
        return new PhotoData(mimeType, photoData[0]);
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

    private static UserTreeReferenceBean userDetailToTreeReference(final UserDetailBean userDetailBean, final String nextNodeAttribute) {
        final UserTreeReferenceBean userTreeReferenceBean = new UserTreeReferenceBean();
        userTreeReferenceBean.setUserKey(userDetailBean.getUserKey());
        userTreeReferenceBean.setPhotoURL(userDetailBean.getPhotoURL());
        userTreeReferenceBean.setDisplayName(userDetailBean.getDisplayName());
        if (userDetailBean.getDetail() != null && userDetailBean.getDetail().containsKey(nextNodeAttribute)) {
            userTreeReferenceBean.setHasMoreNodes(true);
        }
        return userTreeReferenceBean;
    }

    private static boolean orgChartIsEnabled(final Configuration config) {
        final String orgChartParentAttr = config.readSettingAsString(PwmSetting.PEOPLE_SEARCH_ORGCHART_PARENT_ATTRIBUTE);
        final String orgChartChildAttr = config.readSettingAsString(PwmSetting.PEOPLE_SEARCH_ORGCHART_CHILD_ATTRIBUTE);
        return orgChartParentAttr != null && !orgChartParentAttr.isEmpty() && orgChartChildAttr != null && !orgChartChildAttr.isEmpty();
    }
}
