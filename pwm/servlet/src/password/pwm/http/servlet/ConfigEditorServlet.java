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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.Validator;
import password.pwm.bean.ConfigEditorCookie;
import password.pwm.bean.UserIdentity;
import password.pwm.config.*;
import password.pwm.config.value.ValueFactory;
import password.pwm.error.*;
import password.pwm.health.LDAPStatusChecker;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.i18n.Message;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.TimeDuration;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.bean.HealthData;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

public class ConfigEditorServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ConfigEditorServlet.class);
    public static final String DEFAULT_PW = "DEFAULT-PW";

    private static final String COOKIE_NAME_PREFERENCES = "ConfigEditor_preferences";

    public static ConfigEditorCookie readConfigEditorCookie(
            final HttpServletRequest request,
            final HttpServletResponse response
    ) {
        ConfigEditorCookie cookie = null;
        try {
            final String jsonString = ServletHelper.readCookie(request, COOKIE_NAME_PREFERENCES);
            cookie = Helper.getGson().fromJson(jsonString, ConfigEditorCookie.class);
        } catch (Exception e) {
            LOGGER.warn("error parsing cookie preferences: " + e.getMessage());
        }
        if (cookie == null) {
            cookie = new ConfigEditorCookie();
            final String jsonString = Helper.getGson().toJson(cookie);
            ServletHelper.writeCookie(response, COOKIE_NAME_PREFERENCES, jsonString, 60 * 60 * 24 * 3);
        }

        return cookie;
    }


// -------------------------- OTHER METHODS --------------------------

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmRequest pwmRequest = PwmRequest.forRequest(req, resp);
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();

        ConfigManagerServlet.checkAuthentication(pwmRequest,configManagerBean);

        if (configManagerBean.getConfiguration() == null) {
            final StoredConfiguration loadedConfig = ConfigManagerServlet.readCurrentConfiguration(ContextManager.getContextManager(req.getSession()));
            configManagerBean.setConfiguration(loadedConfig);
        }

        validateCookieProfile(readConfigEditorCookie(req, resp), configManagerBean.getConfiguration(), resp);

        final String processActionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);

        if (processActionParam != null && processActionParam.length() > 0) {
            Validator.validatePwmFormID(req);
        }

        if ("readSetting".equalsIgnoreCase(processActionParam)) {
            this.restReadSetting(configManagerBean, req, resp);
            return;
        } else if ("writeSetting".equalsIgnoreCase(processActionParam)) {
            this.restWriteSetting(pwmSession, configManagerBean, req, resp);
            return;
        } else if ("resetSetting".equalsIgnoreCase(processActionParam)) {
            this.restResetSetting(pwmSession, configManagerBean, req, resp);
            return;
        } else if ("ldapHealthCheck".equalsIgnoreCase(processActionParam)) {
            restLdapHealthCheck(pwmSession, configManagerBean, req, resp);
            return;
        } else if ("finishEditing".equalsIgnoreCase(processActionParam)) {
            restFinishEditing(req, resp);
            return;
        } else if ("executeSettingFunction".equalsIgnoreCase(processActionParam)) {
            restExecuteSettingFunction(req, resp, pwmApplication, pwmSession, configManagerBean);
            return;
        } else if ("setConfigurationPassword".equalsIgnoreCase(processActionParam)) {
            restSetConfigurationPassword(pwmApplication, pwmSession, req, resp);
            return;
        } else if ("readChangeLog".equalsIgnoreCase(processActionParam)) {
            restReadChangeLog(resp, pwmSession, configManagerBean);
            return;
        } else if ("search".equalsIgnoreCase(processActionParam)) {
            restSearchSettings(req, resp, pwmSession, configManagerBean);
            return;
        } else if ("cancelEditing".equalsIgnoreCase(processActionParam)) {
            doCancelEditing(req, resp, configManagerBean);
            return;
        } else if ("setOption".equalsIgnoreCase(processActionParam)) {
            setOptions(req);
        }

        if (!resp.isCommitted()) {
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.CONFIG_MANAGER_EDITOR);
        }
    }

    private void restExecuteSettingFunction(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException
    {
        final String bodyString = ServletHelper.readRequestBody(req);
        final Map<String, String> requestMap = Helper.getGson().fromJson(bodyString,
                new TypeToken<Map<String, String>>() {
                }.getType());
        final PwmSetting pwmSetting = PwmSetting.forKey(requestMap.get("setting"));
        final String functionName = requestMap.get("function");
        final String profileID = requestMap.get("profile");

        try {
            Class implementingClass = Class.forName(functionName);
            SettingUIFunction function = (SettingUIFunction)implementingClass.newInstance();
            final String result = function.provideFunction(pwmApplication, pwmSession, configManagerBean.getConfiguration(), pwmSetting, profileID);
            RestResultBean restResultBean = new RestResultBean();
            restResultBean.setSuccessMessage(result);
            ServletHelper.outputJsonResult(resp, restResultBean);
        } catch (Exception e) {
            final RestResultBean restResultBean;
            if (e instanceof PwmException) {
                final String errorMsg = "error while searching for users: " + ((PwmException) e).getErrorInformation().getDetailedErrorMsg();
                final ErrorInformation errorInformation = new ErrorInformation(((PwmException) e).getError(),errorMsg);
                restResultBean = RestResultBean.fromError(errorInformation);
            } else {
                restResultBean = new RestResultBean();
                restResultBean.setError(true);
                restResultBean.setErrorDetail(e.getMessage());
                restResultBean.setErrorMessage("error performing user search: " + e.getMessage());
            }
            ServletHelper.outputJsonResult(resp, restResultBean);
        }
    }

    private void restReadSetting(
            final ConfigManagerBean configManagerBean,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, PwmUnrecoverableException {
        final StoredConfiguration storedConfig = configManagerBean.getConfiguration();

        final String key = Validator.readStringFromRequest(req, "key");
        final Object returnValue;
        final Map<String, Object> returnMap = new LinkedHashMap<>();
        final PwmSetting theSetting = PwmSetting.forKey(key);

        if (key.startsWith("localeBundle")) {
            final StringTokenizer st = new StringTokenizer(key,"-");
            st.nextToken();
            final PwmConstants.EDITABLE_LOCALE_BUNDLES bundleName = PwmConstants.EDITABLE_LOCALE_BUNDLES.valueOf(st.nextToken());
            final String keyName = st.nextToken();
            final Map<String,String> bundleMap = storedConfig.readLocaleBundleMap(bundleName.getTheClass().getName(),keyName);
            if (bundleMap == null || bundleMap.isEmpty()) {
                final Map<String,String> defaultValueMap = new LinkedHashMap<>();
                final String defaultLocaleValue = ResourceBundle.getBundle(bundleName.getTheClass().getName(),PwmConstants.DEFAULT_LOCALE).getString(keyName);
                for (final Locale locale : ContextManager.getPwmApplication(req).getConfig().getKnownLocales()) {
                    final ResourceBundle localeBundle = ResourceBundle.getBundle(bundleName.getTheClass().getName(),locale);
                    if (locale.toString().equalsIgnoreCase(PwmConstants.DEFAULT_LOCALE.toString())) {
                        defaultValueMap.put("", defaultLocaleValue);
                    } else {
                        final String valueStr = localeBundle.getString(keyName);
                        if (!defaultLocaleValue.equals(valueStr)) {
                            final String localeStr = locale.toString();
                            defaultValueMap.put(localeStr, localeBundle.getString(keyName));
                        }
                    }
                }
                returnValue = defaultValueMap;
                returnMap.put("isDefault", true);
            } else {
                returnValue = bundleMap;
                returnMap.put("isDefault", false);
            }
            returnMap.put("key", key);
        } else if (theSetting == null) {
            LOGGER.warn("readSettingAsString request for unknown key: " + key);
            returnMap.put("key", key);
            returnMap.put("isDefault", false);
            returnValue = "UNKNOWN KEY";
        } else {
            if (theSetting.getSyntax() == PwmSettingSyntax.PASSWORD) {
                returnValue = DEFAULT_PW;
            } else {
                if (theSetting.getCategory().getType() == PwmSetting.Category.Type.PROFILE) {
                    final String profile = readConfigEditorCookie(req, resp).getProfile();
                    returnValue = storedConfig.readSetting(theSetting,profile).toNativeObject();
                } else {
                    returnValue = storedConfig.readSetting(theSetting).toNativeObject();
                }
            }

            if (theSetting.getSyntax() == PwmSettingSyntax.SELECT) {
                returnMap.put("options",theSetting.getOptions());
            }
            returnMap.put("key", key);
            returnMap.put("category", theSetting.getCategory().toString());
            returnMap.put("syntax", theSetting.getSyntax().toString());
            returnMap.put("isDefault", storedConfig.isDefaultValue(theSetting));
        }
        returnMap.put("value", returnValue);
        final Gson gson = Helper.getGson();
        final String outputString = gson.toJson(returnMap);
        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().print(outputString);
    }

    private void restWriteSetting(
            final PwmSession pwmSession,
            final ConfigManagerBean configManagerBean,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, PwmUnrecoverableException {
        final ConfigEditorCookie cookie = readConfigEditorCookie(req, resp);
        final StoredConfiguration storedConfig = configManagerBean.getConfiguration();
        final String key = Validator.readStringFromRequest(req, "key");
        final String bodyString = ServletHelper.readRequestBody(req);
        final PwmSetting setting = PwmSetting.forKey(key);
        final Map<String, Object> returnMap = new LinkedHashMap<>();
        final UserIdentity loggedInUser = pwmSession.getSessionStateBean().isAuthenticated() ? pwmSession.getUserInfoBean().getUserIdentity() : null;

        if (key.startsWith("localeBundle")) {
            final StringTokenizer st = new StringTokenizer(key,"-");
            st.nextToken();
            final PwmConstants.EDITABLE_LOCALE_BUNDLES bundleName = PwmConstants.EDITABLE_LOCALE_BUNDLES.valueOf(st.nextToken());
            final String keyName = st.nextToken();
            final Map<String, String> valueMap = Helper.getGson().fromJson(bodyString,
                    new TypeToken<Map<String, String>>() {
                    }.getType());
            final Map<String, String> outputMap = new LinkedHashMap<>(valueMap);

            storedConfig.writeLocaleBundleMap(bundleName.getTheClass().getName(),keyName, outputMap);
            returnMap.put("isDefault", outputMap.isEmpty());
            returnMap.put("key", key);
        } else {
            try {
                final StoredValue storedValue = ValueFactory.fromJson(setting, bodyString);
                final List<String> errorMsgs = storedValue.validateValue(setting);
                if (errorMsgs != null && !errorMsgs.isEmpty()) {
                    returnMap.put("errorMessage",setting.getLabel(pwmSession.getSessionStateBean().getLocale()) + ": " + errorMsgs.get(0));
                }
                if (setting.getCategory().getType() == PwmSetting.Category.Type.PROFILE) {
                    storedConfig.writeSetting(setting, cookie.getProfile(), storedValue, loggedInUser);
                } else {
                    storedConfig.writeSetting(setting,storedValue, loggedInUser);
                }
            } catch (Exception e) {
                final String errorMsg = "error writing default value for setting " + setting.toString() + ", error: " + e.getMessage();
                LOGGER.error(errorMsg,e);
                throw new IllegalStateException(errorMsg,e);
            }
            returnMap.put("key", key);
            returnMap.put("category", setting.getCategory().toString());
            returnMap.put("syntax", setting.getSyntax().toString());
            returnMap.put("isDefault", storedConfig.isDefaultValue(setting));
        }
        final String outputString = Helper.getGson().toJson(returnMap);
        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().print(outputString);
    }

    private void restResetSetting(
            final PwmSession pwmSession,
            final ConfigManagerBean configManagerBean,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, PwmUnrecoverableException
    {
        final StoredConfiguration storedConfig = configManagerBean.getConfiguration();
        final UserIdentity loggedInUser = pwmSession.getSessionStateBean().isAuthenticated() ? pwmSession.getUserInfoBean().getUserIdentity() : null;

        final String bodyString = ServletHelper.readRequestBody(req);

        final Gson gson = Helper.getGson();
        final Map<String, String> srcMap = gson.fromJson(bodyString, new TypeToken<Map<String, String>>() {
        }.getType());

        if (srcMap != null) {
            final String key = srcMap.get("key");
            final PwmSetting setting = PwmSetting.forKey(key);

            if (key.startsWith("localeBundle")) {
                final StringTokenizer st = new StringTokenizer(key,"-");
                st.nextToken();
                final PwmConstants.EDITABLE_LOCALE_BUNDLES bundleName = PwmConstants.EDITABLE_LOCALE_BUNDLES.valueOf(st.nextToken());
                final String keyName = st.nextToken();
                storedConfig.resetLocaleBundleMap(bundleName.getTheClass().getName(), keyName);
            } else {
                if (setting.getCategory().getType() == PwmSetting.Category.Type.PROFILE) {
                    final String profile = readConfigEditorCookie(req, resp).getProfile();
                    storedConfig.resetSetting(setting, profile, loggedInUser);
                } else {
                    storedConfig.resetSetting(setting, loggedInUser);
                }
            }
        }
    }


    private void restSetConfigurationPassword(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest req,
            final HttpServletResponse resp

    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();

        try {
            final String password = ServletHelper.readRequestBody(req);
            configManagerBean.getConfiguration().setPassword(password);
            configManagerBean.setPasswordVerified(true);
            LOGGER.debug(pwmSession, "config password updated");
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setSuccessMessage(
                    Message.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(),
                            Message.SUCCESS_UNKNOWN,
                            pwmApplication.getConfig()
                    ));
            ServletHelper.outputJsonResult(resp, restResultBean);
        } catch (PwmOperationalException e) {
            final RestResultBean restResultBean = RestResultBean.fromError(e.getErrorInformation());
            ServletHelper.outputJsonResult(resp, restResultBean);
        }
    }

    private void restFinishEditing(
            final HttpServletRequest req,
            final HttpServletResponse resp

    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();
        RestResultBean restResultBean = new RestResultBean();
        final HashMap<String,String> resultData = new HashMap<>();
        restResultBean.setData(resultData);

        if (!configManagerBean.getConfiguration().validateValues().isEmpty()) {
            final String errorString = configManagerBean.getConfiguration().validateValues().get(0);
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorString);
            restResultBean = RestResultBean.fromError(errorInfo, pwmSession.getSessionStateBean().getLocale(), pwmApplication.getConfig());
        } else {
            try {
                ConfigManagerServlet.saveConfiguration(pwmSession, req.getSession().getServletContext(), configManagerBean.getConfiguration());
                configManagerBean.setConfiguration(null);
                restResultBean.setError(false);
            } catch (PwmUnrecoverableException e) {
                final ErrorInformation errorInfo = e.getErrorInformation();
                pwmSession.getSessionStateBean().setSessionError(errorInfo);
                restResultBean = RestResultBean.fromError(errorInfo, pwmSession.getSessionStateBean().getLocale(), pwmApplication.getConfig());
                LOGGER.warn(pwmSession, "unable to save configuration: " + e.getMessage());
            }
        }

        configManagerBean.setConfiguration(null);
        LOGGER.debug(pwmSession, "save configuration operation completed");
        ServletHelper.outputJsonResult(resp, restResultBean);
    }

    private void doCancelEditing(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        configManagerBean.setConfiguration(null);
        forwardToManager(req,resp);
    }

    private void setOptions(
            final HttpServletRequest req
    ) throws IOException, PwmUnrecoverableException {
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();
        {
            final String updateDescriptionTextCmd = Validator.readStringFromRequest(req, "updateNotesText");
            if (updateDescriptionTextCmd != null && updateDescriptionTextCmd.equalsIgnoreCase("true")) {
                try {
                    final Gson gson = Helper.getGson();
                    final String bodyString = ServletHelper.readRequestBody(req);
                    final String value = gson.fromJson(bodyString, new TypeToken<String>() {
                    }.getType());
                    configManagerBean.getConfiguration().writeConfigProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_NOTES,
                            value);
                    LOGGER.trace("updated notesText");
                } catch (Exception e) {
                    LOGGER.error("error updating notesText: " + e.getMessage());
                }
            }
            {
                final String requestedTemplate = Validator.readStringFromRequest(req, "template");
                if (requestedTemplate != null && requestedTemplate.length() > 0) {
                    try {
                        final PwmSetting.Template template = PwmSetting.Template.valueOf(requestedTemplate);
                        configManagerBean.getConfiguration().writeConfigProperty(
                                StoredConfiguration.ConfigProperty.PROPERTY_KEY_TEMPLATE, template.toString());
                        LOGGER.trace("setting template to: " + requestedTemplate);
                    } catch (IllegalArgumentException e) {
                        configManagerBean.getConfiguration().writeConfigProperty(
                                StoredConfiguration.ConfigProperty.PROPERTY_KEY_TEMPLATE, PwmSetting.Template.DEFAULT.toString());
                        LOGGER.error("unknown template set request: " + requestedTemplate);
                    }
                }
            }
        }
    }

    static void forwardToManager(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final String url = req.getContextPath() + "/private/config/ConfigManager";
        resp.sendRedirect(url);
    }

    static void validateCookieProfile(
            final ConfigEditorCookie configEditorCookie,
            final StoredConfiguration storedConfiguration,
            final HttpServletResponse resp
    ) {
        if (null == configEditorCookie.getProfile() || "".equals(configEditorCookie.getProfile())) {
            return;
        }


        if (configEditorCookie.getCategory() == null) {
            configEditorCookie.setCategory(PwmSetting.Category.LDAP_PROFILE);
        }

        if (configEditorCookie.getEditMode() == null) {
            configEditorCookie.setEditMode(ConfigEditorCookie.EDIT_MODE.SETTINGS);
        }

        final PwmSetting.Category category = configEditorCookie.getCategory();

        if (category.getType() != PwmSetting.Category.Type.PROFILE) {
            configEditorCookie.setProfile("");
        } else {
            final Collection<String> validProfiles = storedConfiguration.profilesForSetting(category.getProfileSetting());
            if (!validProfiles.contains(configEditorCookie.getProfile())) {
                configEditorCookie.setProfile("");
            }
        }

        ServletHelper.writeCookie(resp,COOKIE_NAME_PREFERENCES,Helper.getGson().toJson(configEditorCookie), 60 * 60 * 24 * 3);
    }

    void restReadChangeLog(
            final HttpServletResponse resp,
            final PwmSession pwmSession,
            final ConfigManagerBean configManagerBean
    )
            throws IOException
    {
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(configManagerBean.getConfiguration().changeLogAsDebugString(locale,true));
        ServletHelper.outputJsonResult(resp,restResultBean);
    }

    void restSearchSettings(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmSession pwmSession,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException
    {
        final Date startTime = new Date();
        final String bodyData = ServletHelper.readRequestBody(req);
        final Map<String, String> valueMap = Helper.getGson().fromJson(bodyData,
                new TypeToken<Map<String, String>>() {
                }.getType());
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final RestResultBean restResultBean = new RestResultBean();
        final String searchTerm = valueMap.get("search");
        if (searchTerm != null && !searchTerm.isEmpty()) {
            final ArrayList<StoredConfiguration.ConfigRecordID> searchResults = new ArrayList(configManagerBean.getConfiguration().search(searchTerm,locale));
            final LinkedHashMap<String,Map<String,Map<String,String>>> returnData = new LinkedHashMap<>();

            for (final StoredConfiguration.ConfigRecordID recordID : searchResults) {
                if (recordID.getRecordType() == StoredConfiguration.ConfigRecordID.RecordType.SETTING) {
                    final PwmSetting setting = (PwmSetting)recordID.getRecordID();
                    final LinkedHashMap<String,String> settingData = new LinkedHashMap<>();
                    settingData.put("category", setting.getCategory().toString());
                    settingData.put("description", setting.getDescription(locale));
                    settingData.put("value", configManagerBean.getConfiguration().readSetting(setting,recordID.getProfileID()).toDebugString(false,null));
                    settingData.put("label", setting.getLabel(locale));

                    String returnCategory = setting.getCategory().getLabel(locale);
                    if (recordID.getProfileID() != null && !recordID.getProfileID().isEmpty()) {
                        settingData.put("profile", recordID.getProfileID());
                        returnCategory += " -> " + recordID.getProfileID();
                    }

                    if (!returnData.containsKey(returnCategory)) {
                        returnData.put(returnCategory,new LinkedHashMap<String, Map<String, String>>());
                    }

                    returnData.get(returnCategory).put(setting.getKey(), settingData);
                }
            }

            restResultBean.setData(returnData);
            LOGGER.trace(pwmSession,"finished search operation with " + returnData.size() + " results in " + TimeDuration.fromCurrent(startTime).asCompactString());
        } else {
            restResultBean.setData(new ArrayList<StoredConfiguration.ConfigRecordID>());
        }

        ServletHelper.outputJsonResult(resp, restResultBean);
    }

    private void restReadProperties(
            final ConfigManagerBean configManagerBean,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, PwmUnrecoverableException {
        final StoredConfiguration storedConfig = configManagerBean.getConfiguration();
        final LinkedHashMap<String,String> returnMap = new LinkedHashMap<>();
        for (final AppProperty appProperty : AppProperty.values()) {
            final String value = storedConfig.readAppProperty(appProperty);
            if (value != null) {
                returnMap.put(appProperty.getKey(),value);
            }
        }
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(returnMap);
        ServletHelper.outputJsonResult(resp, restResultBean);
    }

    private void restWriteProperties(
            final ConfigManagerBean configManagerBean,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, PwmUnrecoverableException
    {
        final String bodyString = ServletHelper.readRequestBody(req);
        final Map<String, String> valueMap = Helper.getGson().fromJson(bodyString,
                new TypeToken<Map<String, String>>() {
                }.getType());


        final Set<AppProperty> storedProperties = new LinkedHashSet<>();
        for (final AppProperty appProperty : AppProperty.values()) {
            final String value = configManagerBean.getConfiguration().readAppProperty(appProperty);
            if (value != null) {
                storedProperties.add(appProperty);
            }
        }

        final Set<AppProperty> seenProperties = new LinkedHashSet<>();
        for (final String key : valueMap.keySet()) {
            final AppProperty appProperty = AppProperty.forKey(key);
            if (appProperty != null) {
                configManagerBean.getConfiguration().writeAppProperty(appProperty,valueMap.get(key));
                seenProperties.add(appProperty);
            }
        }

        final Set<AppProperty> removedProps = new LinkedHashSet<>();
        removedProps.addAll(storedProperties);
        removedProps.removeAll(seenProperties);
        for (final AppProperty appProperty : removedProps) {
            configManagerBean.getConfiguration().writeAppProperty(appProperty,null);
        }

        final RestResultBean restResultBean = new RestResultBean();
        ServletHelper.outputJsonResult(resp,restResultBean);
    }

    private void restLdapHealthCheck(
            final PwmSession pwmSession,
            final ConfigManagerBean configManagerBean,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, PwmUnrecoverableException
    {
        final Date startTime = new Date();
        LOGGER.debug(pwmSession, "beginning restLdapHealthCheck");
        ConfigEditorCookie cookie = readConfigEditorCookie(req, resp);
        final String profileID = cookie.getProfile();
        final Configuration config = new Configuration(configManagerBean.getConfiguration());
        final HealthData healthData = LDAPStatusChecker.healthForNewConfiguration(config, pwmSession.getSessionStateBean().getLocale(), profileID, true, true);
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(healthData);

        ServletHelper.outputJsonResult(resp,restResultBean);
        LOGGER.debug(pwmSession, "completed restLdapHealthCheck in " + TimeDuration.fromCurrent(startTime));
    }
}
