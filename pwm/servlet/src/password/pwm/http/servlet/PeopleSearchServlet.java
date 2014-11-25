/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.*;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.ldap.LdapUserDataReader;
import password.pwm.ldap.UserDataReader;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.ldap.UserStatusReader;
import password.pwm.util.Helper;
import password.pwm.util.JsonUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.cache.CacheService;
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

    public static class AttributeDetailBean implements Serializable {
        private String name;
        private String label;
        private FormConfiguration.Type type;
        private String value;
        private Collection<UserReferenceBean> userReferences;

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getLabel()
        {
            return label;
        }

        public void setLabel(String label)
        {
            this.label = label;
        }

        public FormConfiguration.Type getType()
        {
            return type;
        }

        public void setType(FormConfiguration.Type type)
        {
            this.type = type;
        }

        public String getValue()
        {
            return value;
        }

        public void setValue(String value)
        {
            this.value = value;
        }

        public Collection<UserReferenceBean> getUserReferences()
        {
            return userReferences;
        }

        public void setUserReferences(Collection<UserReferenceBean> userReferences)
        {
            this.userReferences = userReferences;
        }
    }

    public static class UserReferenceBean implements Serializable {
        private String userKey;
        private String display;

        public String getUserKey()
        {
            return userKey;
        }

        public void setUserKey(String userKey)
        {
            this.userKey = userKey;
        }

        public String getDisplay()
        {
            return display;
        }

        public void setDisplay(String display)
        {
            this.display = display;
        }
    }

    public enum PeopleSearchActions implements ProcessAction {
        search(HttpMethod.POST),
        detail(HttpMethod.POST),
        photo(HttpMethod.GET),

        ;

        private final HttpMethod method;

        PeopleSearchActions(HttpMethod method)
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods()
        {
            return Collections.singletonList(method);
        }
    }

    protected PeopleSearchActions readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
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

        if (!pwmRequest.getPwmSession().getSessionManager().checkPermission(pwmRequest.getPwmApplication(), Permission.PEOPLE_SEARCH)) {
            pwmRequest.respondWithError(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED));
            return;
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
            }
        }

        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.PEOPLE_SEARCH);
    }

    private void restSearchRequest(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final Date startTime = new Date();
        final String bodyString = pwmRequest.readRequestBodyAsString();
        final Map<String, String> valueMap = JsonUtil.getGson().fromJson(bodyString,
                new TypeToken<Map<String, String>>() {
                }.getType()
        );

        final String username = Validator.sanitizeInputValue(pwmRequest.getConfig(), valueMap.get("username"), 1024);
        final boolean useProxy = pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_USE_PROXY);
        final CacheService.CacheKey cacheKey = CacheService.CacheKey.makeCacheKey(
                this.getClass(),
                useProxy ? null : pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity(),
                username
        );
        {
            final String cachedOutput = pwmRequest.getPwmApplication().getCacheService().get(cacheKey);
            if (cachedOutput != null) {
                final HashMap<String, Object> resultOutput = JsonUtil.getGson().fromJson(cachedOutput,
                        new TypeToken<HashMap<String, Object>>() {
                        }.getType());
                final RestResultBean restResultBean = new RestResultBean();
                restResultBean.setData(resultOutput);
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
        searchConfiguration.setFilter(
                pwmRequest.getConfig().readSettingAsString(PwmSetting.PEOPLE_SEARCH_SEARCH_FILTER));
        if (!useProxy) {
            searchConfiguration.setLdapProfile(
                    pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity().getLdapProfileID());
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


        final RestResultBean restResultBean = new RestResultBean();
        final LinkedHashMap<String, Object> outputData = new LinkedHashMap<>();
        outputData.put("searchResults",
                new ArrayList<>(results.resultsAsJsonOutput(pwmRequest.getPwmApplication())));
        outputData.put("sizeExceeded", sizeExceeded);
        restResultBean.setData(outputData);
        pwmRequest.outputJsonResult(restResultBean);
        final long maxCacheSeconds = pwmRequest.getConfig().readSettingAsLong(PwmSetting.PEOPLE_SEARCH_MAX_CACHE_SECONDS);
        if (maxCacheSeconds > 0) {
            final Date expiration = new Date(System.currentTimeMillis() * maxCacheSeconds * 1000);
            pwmRequest.getPwmApplication().getCacheService().put(cacheKey, CacheService.CachePolicy.makePolicy(expiration), JsonUtil.serialize(outputData));
        }

        if (pwmRequest.getPwmApplication().getStatisticsManager() != null) {
            pwmRequest.getPwmApplication().getStatisticsManager().incrementValue(Statistic.PEOPLESEARCH_SEARCHES);
        }

        LOGGER.trace(pwmRequest.getPwmSession(), "finished rest peoplesearch search in " + TimeDuration.fromCurrent(
                startTime).asCompactString() + ", size=" + results.getResults().size());
    }

    private static UserSearchEngine.UserSearchResults doDetailLookup(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final Configuration config = pwmApplication.getConfig();
        final List<FormConfiguration> detailFormConfig = config.readSettingAsForm(PwmSetting.PEOPLE_SEARCH_DETAIL_FORM);
        final Map<String, String> attributeHeaderMap = UserSearchEngine.UserSearchResults.fromFormConfiguration(
                detailFormConfig, pwmSession.getSessionStateBean().getLocale());
        final ChaiUser theUser = config.readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_USE_PROXY)
                ? pwmApplication.getProxiedChaiUser(userIdentity)
                : pwmSession.getSessionManager().getActor(pwmApplication, userIdentity);
        Map<String, String> values = null;
        try {
            values = theUser.readStringAttributes(attributeHeaderMap.keySet());
        } catch (ChaiOperationException e) {
            LOGGER.error("unexpected error during detail lookup of '" + userIdentity + "', error: " + e.getMessage());
        }
        return new UserSearchEngine.UserSearchResults(attributeHeaderMap,
                Collections.singletonMap(userIdentity, values), false);
    }


    private void restUserDetailRequest(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final Date startTime = new Date();
        final String bodyString = pwmRequest.readRequestBodyAsString();
        final Map<String, String> valueMap = JsonUtil.getGson().fromJson(bodyString,
                new TypeToken<Map<String, String>>() {
                }.getType()
        );

        if (valueMap == null) {
            return;
        }

        final String userKey = valueMap.get("userKey");
        if (userKey == null || userKey.isEmpty()) {
            return;
        }

        final boolean useProxy = pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_USE_PROXY);
        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmRequest.getConfig());

        final CacheService.CacheKey cacheKey = CacheService.CacheKey.makeCacheKey(
                this.getClass(),
                useProxy ? null : pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity(),
                userIdentity.toDeliminatedKey()
        );
        {
            final String cachedOutput = pwmRequest.getPwmApplication().getCacheService().get(cacheKey);
            if (cachedOutput != null) {
                final HashMap<String, Object> resultOutput = JsonUtil.getGson().fromJson(cachedOutput,
                        new TypeToken<HashMap<String, Object>>() {
                        }.getType());
                final RestResultBean restResultBean = new RestResultBean();
                restResultBean.setData(resultOutput);
                pwmRequest.outputJsonResult(restResultBean);
                LOGGER.trace(pwmRequest.getPwmSession(), "finished rest detail request in " + TimeDuration.fromCurrent(
                        startTime).asCompactString() + "using CACHED details, results=" + JsonUtil.getGson(
                        new GsonBuilder().disableHtmlEscaping()).toJson(restResultBean));

                if (pwmRequest.getPwmApplication().getStatisticsManager() != null) {
                    pwmRequest.getPwmApplication().getStatisticsManager().incrementValue(Statistic.PEOPLESEARCH_DETAILS);
                }

                return;
            }
        }
        try {
            checkIfUserIdentityPermitted(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), userIdentity);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmRequest.getPwmSession(), "error during detail results request while checking if requested userIdentity is within search scope: " + e.getMessage());
            pwmRequest.outputJsonResult(RestResultBean.fromError(e.getErrorInformation(), pwmRequest));
            return;
        }

        UserSearchEngine.UserSearchResults detailResults = doDetailLookup(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), userIdentity);
        final Map<String, String> searchResults = detailResults.getResults().get(userIdentity);

        final LinkedHashMap<String, Object> resultOutput = new LinkedHashMap<>();
        final List<FormConfiguration> detailFormConfig = pwmRequest.getConfig().readSettingAsForm(
                PwmSetting.PEOPLE_SEARCH_DETAIL_FORM);
        List<AttributeDetailBean> bean = convertResultMapToBean(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), userIdentity,
                detailFormConfig, searchResults);


        resultOutput.put("detail", bean);
        final String photoURL = figurePhotoURL(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), userIdentity);
        if (photoURL != null) {
            resultOutput.put("photoURL", photoURL);
        }
        final String displayName = figureDisplaynameValue(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), userIdentity);
        if (displayName != null) {
            resultOutput.put("displayName", displayName);
        }

        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(resultOutput);
        pwmRequest.outputJsonResult(restResultBean);
        LOGGER.trace(pwmRequest.getPwmSession(), "finished rest detail request in " + TimeDuration.fromCurrent(
                startTime).asCompactString() + ", results=" + JsonUtil.getGson(
                new GsonBuilder().disableHtmlEscaping()).toJson(restResultBean));

        final long maxCacheSeconds = pwmRequest.getConfig().readSettingAsLong(PwmSetting.PEOPLE_SEARCH_MAX_CACHE_SECONDS);
        if (maxCacheSeconds > 0) {
            final Date expiration = new Date(System.currentTimeMillis() * maxCacheSeconds * 1000);
            pwmRequest.getPwmApplication().getCacheService().put(cacheKey, CacheService.CachePolicy.makePolicy(expiration),
                    JsonUtil.serializeMap(resultOutput));
        }

        StatisticsManager.incrementStat(pwmRequest, Statistic.PEOPLESEARCH_SEARCHES);
    }


    private static String figurePhotoURL(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final String overrideURL = pwmApplication.getConfig().readSettingAsString(PwmSetting.PEOPLE_SEARCH_PHOTO_URL_OVERRIDE);
        if (overrideURL != null && !overrideURL.isEmpty()) {
            final MacroMachine macroMachine = getMacroMachine(pwmApplication, pwmSession, userIdentity);
            return macroMachine.expandMacros(overrideURL);
        }

        final String attribute = pwmApplication.getConfig().readSettingAsString(PwmSetting.PEOPLE_SEARCH_PHOTO_ATTRIBUTE);
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }

        return "PeopleSearch?processAction=photo&userKey=" + userIdentity.toObfuscatedKey(pwmApplication.getConfig());
    }

    private static String figureDisplaynameValue(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final MacroMachine macroMachine = getMacroMachine(pwmApplication, pwmSession, userIdentity);
        final String settingValue = pwmApplication.getConfig().readSettingAsString(
                PwmSetting.PEOPLE_SEARCH_DISPLAY_NAME);
        return macroMachine.expandMacros(settingValue);
    }


    private void processUserPhotoImageRequest(final PwmRequest pwmRequest)
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final String userKey = pwmRequest.readParameterAsString("userKey");
        if (userKey.length() < 1) {
            pwmRequest.respondWithError(
                    new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER, "userKey parameter is missing"), false);
            return;
        }

        final String attribute = pwmRequest.getConfig().readSettingAsString(PwmSetting.PEOPLE_SEARCH_PHOTO_ATTRIBUTE);
        if (attribute == null || attribute.isEmpty()) {
            return;
        }

        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmRequest.getConfig());
        try {
            checkIfUserIdentityPermitted(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), userIdentity);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmRequest, "error during photo request while checking if requested userIdentity is within search scope: " + e.getMessage());
            pwmRequest.outputJsonResult(RestResultBean.fromError(e.getErrorInformation(), pwmRequest));
            return;
        }

        LOGGER.info(pwmRequest,
                "received user photo request by " + pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity().toString() + " for user " + userIdentity.toString());


        byte[][] photoData;
        String mimeType;
        try {
            final ChaiUser chaiUser = getChaiUser(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), userIdentity);
            photoData = chaiUser.readMultiByteAttribute(attribute);
            if (photoData == null || photoData.length == 0 || photoData[0].length == 0) {
                LOGGER.error(pwmRequest, "user has no photo data stored in LDAP attribute");
                return;
            }
            mimeType = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(photoData[0]));
        } catch (ChaiOperationException e) {
            LOGGER.error(pwmRequest, "error reading user photo ldap attribute: " + e.getMessage());
            return;
        }

        OutputStream outputStream = null;
        try {
            pwmRequest.getPwmResponse().getHttpServletResponse().setContentType(mimeType);
            outputStream = pwmRequest.getPwmResponse().getOutputStream();
            outputStream.write(photoData[0]);
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    private static List<AttributeDetailBean> convertResultMapToBean(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity,
            List<FormConfiguration> form,
            Map<String, String> searchResults
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final int MAX_VALUES = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.PEOPLESEARCH_MAX_VALUE_COUNT));
        final List<AttributeDetailBean> returnObj = new ArrayList<>();
        for (FormConfiguration formConfiguration : form) {
            if (formConfiguration.isRequired() || searchResults.containsKey(formConfiguration.getName())) {
                AttributeDetailBean bean = new AttributeDetailBean();
                bean.setName(formConfiguration.getName());
                bean.setLabel(formConfiguration.getLabel(pwmSession.getSessionStateBean().getLocale()));
                bean.setType(formConfiguration.getType());
                if (formConfiguration.getType() == FormConfiguration.Type.userDN) {
                    if (searchResults.containsKey(formConfiguration.getName())) {
                        final ChaiUser chaiUser = getChaiUser(pwmApplication, pwmSession, userIdentity);
                        final Set<String> values;
                        try {
                            values = chaiUser.readMultiStringAttribute(formConfiguration.getName());
                            final TreeMap<String,UserReferenceBean> userReferences = new TreeMap<>();
                            for (final String value : values) {
                                if (userReferences.size() < MAX_VALUES) {
                                    final UserIdentity loopIdentity = new UserIdentity(value,
                                            userIdentity.getLdapProfileID());
                                    final String displayValue = figureDisplaynameValue(pwmApplication, pwmSession,
                                            loopIdentity);
                                    final UserReferenceBean userReference = new UserReferenceBean();
                                    userReference.setUserKey(loopIdentity.toObfuscatedKey(pwmApplication.getConfig()));
                                    userReference.setDisplay(displayValue);
                                    userReferences.put(displayValue,userReference);
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
                returnObj.add(bean);
            }
        }
        return returnObj;
    }

    private static ChaiUser getChaiUser(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        return pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_USE_PROXY)
                ? pwmApplication.getProxiedChaiUser(userIdentity)
                : pwmSession.getSessionManager().getActor(pwmApplication, userIdentity);

    }

    private static MacroMachine getMacroMachine(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
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
        return new MacroMachine(pwmApplication, userInfoBean, null, userDataReader);
    }

    private static void checkIfUserIdentityPermitted(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        final String filterSetting = pwmApplication.getConfig().readSettingAsString(PwmSetting.PEOPLE_SEARCH_SEARCH_FILTER);
        String filterString = filterSetting.replace(PwmConstants.VALUE_REPLACEMENT_USERNAME,"*");
        while (filterString.contains("**")) {
            filterString = filterString.replace("**","*");
        }

        final boolean match = Helper.testQueryMatch(pwmApplication, pwmSession.getLabel(), userIdentity, filterString);
        if (!match) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,"requested userDN is not available within configured search filter"));
        }
    }
}