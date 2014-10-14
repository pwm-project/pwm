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

import com.google.gson.reflect.TypeToken;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.Validator;
import password.pwm.bean.ConfigEditorCookie;
import password.pwm.bean.SmsItemBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.*;
import password.pwm.config.value.FileValue;
import password.pwm.config.value.ValueFactory;
import password.pwm.error.*;
import password.pwm.health.*;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.i18n.Message;
import password.pwm.util.JsonUtil;
import password.pwm.util.ServletHelper;
import password.pwm.util.StringUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.queue.SmsQueueManager;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.bean.HealthData;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;

public class ConfigEditorServlet extends PwmServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(ConfigEditorServlet.class);
    public static final String DEFAULT_PW = "DEFAULT-PW";

    private static final String COOKIE_NAME_PREFERENCES = "ConfigEditor_preferences";

    public enum ConfigEditorAction implements PwmServlet.ProcessAction {
        readSetting,
        writeSetting,
        resetSetting,
        ldapHealthCheck,
        databaseHealthCheck,
        smsHealthCheck,
        finishEditing,
        executeSettingFunction,
        setConfigurationPassword,
        readChangeLog,
        search,
        cancelEditing,
        uploadFile,
        setOption,
        ;

        public Collection<PwmServlet.HttpMethod> permittedMethods()
        {
            return Collections.singletonList(HttpMethod.POST);
        }
    }

    protected ConfigEditorAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        try {
            return ConfigEditorAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static ConfigEditorCookie readConfigEditorCookie(final PwmRequest pwmRequest)
    {
        ConfigEditorCookie cookie = null;
        try {
            final String jsonString = ServletHelper.readCookie(pwmRequest.getHttpServletRequest(), COOKIE_NAME_PREFERENCES);
            cookie = JsonUtil.getGson().fromJson(jsonString, ConfigEditorCookie.class);
        } catch (Exception e) {
            LOGGER.warn("error parsing cookie preferences: " + e.getMessage());
        }
        if (cookie == null) {
            cookie = new ConfigEditorCookie();
            final String jsonString = JsonUtil.serialize(cookie);
            pwmRequest.getPwmResponse().writeCookie(COOKIE_NAME_PREFERENCES, jsonString, 60 * 60 * 24 * 3);
        }

        return cookie;
    }


// -------------------------- OTHER METHODS --------------------------

    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();

        ConfigManagerServlet.checkAuthentication(pwmRequest,configManagerBean);

        if (configManagerBean.getConfiguration() == null) {
            final StoredConfiguration loadedConfig = ConfigManagerServlet.readCurrentConfiguration(pwmRequest);
            configManagerBean.setConfiguration(loadedConfig);
        }

        validateCookieProfile(readConfigEditorCookie(pwmRequest), configManagerBean.getConfiguration(), pwmRequest);

        final ConfigEditorAction action = readProcessAction(pwmRequest);

        if (action != null) {
            Validator.validatePwmFormID(pwmRequest.getHttpServletRequest());

            switch (action) {
                case readSetting:
                    restReadSetting(pwmRequest, configManagerBean);
                    return;

                case writeSetting:
                    restWriteSetting(pwmRequest, configManagerBean);
                    return;

                case resetSetting:
                    restResetSetting(pwmRequest, configManagerBean);
                    return;

                case ldapHealthCheck:
                    restLdapHealthCheck(pwmRequest, configManagerBean);
                    return;

                case databaseHealthCheck:
                    restDatabaseHealthCheck(pwmRequest, configManagerBean);
                    return;

                case smsHealthCheck:
                    restSmsHealthCheck(pwmRequest, configManagerBean);
                    return;

                case finishEditing:
                    restFinishEditing(pwmRequest, configManagerBean);
                    return;

                case executeSettingFunction:
                    restExecuteSettingFunction(pwmRequest, configManagerBean);
                    return;

                case setConfigurationPassword:
                    restSetConfigurationPassword(pwmRequest, configManagerBean);
                    return;

                case readChangeLog:
                    restReadChangeLog(pwmRequest, configManagerBean);
                    return;

                case search:
                    restSearchSettings(pwmRequest, configManagerBean);
                    return;

                case cancelEditing:
                    restCancelEditing(pwmRequest, configManagerBean);
                    return;

                case uploadFile:
                    doUploadFile(pwmRequest, configManagerBean);
                    return;

                case setOption:
                    setOptions(pwmRequest, configManagerBean);
                    return;
            }

        }

        if (!pwmRequest.getPwmResponse().isCommitted()) {
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.CONFIG_MANAGER_EDITOR);
        }
    }

    private void restExecuteSettingFunction(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException
    {
        final String bodyString = pwmRequest.readRequestBodyAsString();
        final Map<String, String> requestMap = JsonUtil.deserializeStringMap(bodyString);
        final PwmSetting pwmSetting = PwmSetting.forKey(requestMap.get("setting"));
        final String functionName = requestMap.get("function");
        final String profileID = readConfigEditorCookie(pwmRequest).getProfile();

        try {
            Class implementingClass = Class.forName(functionName);
            SettingUIFunction function = (SettingUIFunction)implementingClass.newInstance();
            final String result = function.provideFunction(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), configManagerBean.getConfiguration(), pwmSetting, profileID);
            RestResultBean restResultBean = new RestResultBean();
            restResultBean.setSuccessMessage(result);
            pwmRequest.outputJsonResult(restResultBean);
        } catch (Exception e) {
            final RestResultBean restResultBean;
            if (e instanceof PwmException) {
                final String errorMsg = "error while loading data: " + ((PwmException) e).getErrorInformation().getDetailedErrorMsg();
                final ErrorInformation errorInformation = new ErrorInformation(((PwmException) e).getError(),errorMsg);
                restResultBean = RestResultBean.fromError(errorInformation, pwmRequest);
            } else {
                restResultBean = new RestResultBean();
                restResultBean.setError(true);
                restResultBean.setErrorDetail(e.getMessage());
                restResultBean.setErrorMessage("error performing user search: " + e.getMessage());
            }
            pwmRequest.outputJsonResult(restResultBean);
        }
    }

    private void restReadSetting(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException {
        final StoredConfiguration storedConfig = configManagerBean.getConfiguration();

        final String key = pwmRequest.readParameterAsString("key");
        final Object returnValue;
        final LinkedHashMap<String, Object> returnMap = new LinkedHashMap<>();
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
                for (final Locale locale : pwmRequest.getConfig().getKnownLocales()) {
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
                if (theSetting.getCategory().hasProfiles()) {
                    final String profile = readConfigEditorCookie(pwmRequest).getProfile();
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
        pwmRequest.outputJsonResult(new RestResultBean(returnMap));
    }

    private void restWriteSetting(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigEditorCookie cookie = readConfigEditorCookie(pwmRequest);
        final StoredConfiguration storedConfig = configManagerBean.getConfiguration();
        final String key = pwmRequest.readParameterAsString("key");
        final String bodyString = pwmRequest.readRequestBodyAsString();
        final PwmSetting setting = PwmSetting.forKey(key);
        final LinkedHashMap<String, Object> returnMap = new LinkedHashMap<>();
        final UserIdentity loggedInUser = pwmRequest.getPwmSession().getSessionStateBean().isAuthenticated()
                ? pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity()
                : null;

        if (key.startsWith("localeBundle")) {
            final StringTokenizer st = new StringTokenizer(key,"-");
            st.nextToken();
            final PwmConstants.EDITABLE_LOCALE_BUNDLES bundleName = PwmConstants.EDITABLE_LOCALE_BUNDLES.valueOf(st.nextToken());
            final String keyName = st.nextToken();
            final Map<String, String> valueMap = JsonUtil.getGson().fromJson(bodyString,
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
                    returnMap.put("errorMessage",setting.getLabel(pwmRequest.getLocale()) + ": " + errorMsgs.get(0));
                }
                if (setting.getCategory().hasProfiles()) {
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
        pwmRequest.outputJsonResult(new RestResultBean(returnMap));
    }

    private void restResetSetting(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException
    {
        final StoredConfiguration storedConfig = configManagerBean.getConfiguration();
        final UserIdentity loggedInUser = pwmRequest.getUserInfoIfLoggedIn();

        final String bodyString = pwmRequest.readRequestBodyAsString();

        final Map<String, String> srcMap = JsonUtil.deserializeStringMap(bodyString);

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
                if (setting.getCategory().hasProfiles()) {
                    final String profile = readConfigEditorCookie(pwmRequest).getProfile();
                    storedConfig.resetSetting(setting, profile, loggedInUser);
                } else {
                    storedConfig.resetSetting(setting, loggedInUser);
                }
            }
        }

        pwmRequest.outputJsonResult(RestResultBean.forSuccessMessage(pwmRequest,Message.SUCCESS_UNKNOWN));
    }


    private void restSetConfigurationPassword(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        try {
            final Map<String,String> postData = pwmRequest.readBodyAsJsonStringMap();
            final String password = postData.get("password");
            configManagerBean.getConfiguration().setPassword(password);
            configManagerBean.setPasswordVerified(true);
            LOGGER.debug(pwmRequest, "config password updated");
            final RestResultBean restResultBean = RestResultBean.forSuccessMessage(pwmRequest, Message.SUCCESS_UNKNOWN);
            pwmRequest.outputJsonResult(restResultBean);
        } catch (PwmOperationalException e) {
            final RestResultBean restResultBean = RestResultBean.fromError(e.getErrorInformation(), pwmRequest);
            pwmRequest.outputJsonResult(restResultBean);
        }
    }

    private void restFinishEditing(final PwmRequest pwmRequest, final ConfigManagerBean configManagerBean)
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        RestResultBean restResultBean = new RestResultBean();
        final HashMap<String,String> resultData = new HashMap<>();
        restResultBean.setData(resultData);

        if (!configManagerBean.getConfiguration().validateValues().isEmpty()) {
            final String errorString = configManagerBean.getConfiguration().validateValues().get(0);
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorString);
            restResultBean = RestResultBean.fromError(errorInfo, pwmRequest);
        } else {
            try {
                ConfigManagerServlet.saveConfiguration(pwmRequest, configManagerBean.getConfiguration());
                configManagerBean.setConfiguration(null);
                restResultBean.setError(false);
            } catch (PwmUnrecoverableException e) {
                final ErrorInformation errorInfo = e.getErrorInformation();
                restResultBean = RestResultBean.fromError(errorInfo, pwmRequest);
                LOGGER.warn(pwmSession, "unable to save configuration: " + e.getMessage());
            }
        }

        configManagerBean.setConfiguration(null);
        LOGGER.debug(pwmSession, "save configuration operation completed");
        pwmRequest.outputJsonResult(restResultBean);
    }

    private void restCancelEditing(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        configManagerBean.setConfiguration(null);
        pwmRequest.outputJsonResult(RestResultBean.forSuccessMessage(pwmRequest,Message.SUCCESS_UNKNOWN));
    }

    private void setOptions(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException
    {
        {
            final String updateDescriptionTextCmd = pwmRequest.readParameterAsString("updateNotesText");
            if (updateDescriptionTextCmd != null && updateDescriptionTextCmd.equalsIgnoreCase("true")) {
                try {
                    final String bodyString = pwmRequest.readRequestBodyAsString();
                    final String value = JsonUtil.deserialize(bodyString, String.class);
                    configManagerBean.getConfiguration().writeConfigProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_NOTES,
                            value);
                    LOGGER.trace("updated notesText");
                } catch (Exception e) {
                    LOGGER.error("error updating notesText: " + e.getMessage());
                }
            }
            {
                final String requestedTemplate = pwmRequest.readParameterAsString("template");
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

    static void forwardToManager(final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final String url = pwmRequest.getHttpServletRequest().getContextPath() + "/private/config/ConfigManager";
        pwmRequest.getPwmResponse().sendRedirect(url);
    }

    static void validateCookieProfile(
            final ConfigEditorCookie configEditorCookie,
            final StoredConfiguration storedConfiguration,
            final PwmRequest pwmRequest
    ) {
        if (null == configEditorCookie.getProfile() || "".equals(configEditorCookie.getProfile())) {
            return;
        }


        if (configEditorCookie.getCategory() == null) {
            configEditorCookie.setCategory(PwmSettingCategory.LDAP_PROFILE);
        }

        if (configEditorCookie.getEditMode() == null) {
            configEditorCookie.setEditMode(ConfigEditorCookie.EDIT_MODE.SETTINGS);
        }

        final PwmSettingCategory category = configEditorCookie.getCategory();

        if (category.hasProfiles()) {
            configEditorCookie.setProfile("");
        } else {
            final Collection<String> validProfiles = storedConfiguration.profilesForSetting(category.getProfileSetting());
            if (!validProfiles.contains(configEditorCookie.getProfile())) {
                configEditorCookie.setProfile("");
            }
        }

        pwmRequest.getPwmResponse().writeCookie(COOKIE_NAME_PREFERENCES, JsonUtil.serialize(configEditorCookie), 60 * 60 * 24 * 3);
    }

    void restReadChangeLog(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException
    {
        final Locale locale = pwmRequest.getLocale();
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(configManagerBean.getConfiguration().changeLogAsDebugString(locale, true));
        pwmRequest.outputJsonResult(restResultBean);
    }

    void restSearchSettings(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException
    {
        final Date startTime = new Date();
        final String bodyData = pwmRequest.readRequestBodyAsString();
        final Map<String, String> valueMap = JsonUtil.deserializeStringMap(bodyData);
        final Locale locale = pwmRequest.getLocale();
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
            LOGGER.trace(pwmRequest,"finished search operation with " + returnData.size() + " results in " + TimeDuration.fromCurrent(startTime).asCompactString());
        } else {
            restResultBean.setData(new ArrayList<StoredConfiguration.ConfigRecordID>());
        }

        pwmRequest.outputJsonResult(restResultBean);
    }

    private void restReadProperties(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
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
        pwmRequest.outputJsonResult(new RestResultBean(returnMap));
    }

    private void restWriteProperties(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException
    {
        final String bodyString = pwmRequest.readRequestBodyAsString();
        final Map<String, String> valueMap = JsonUtil.deserializeStringMap(bodyString);

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
        pwmRequest.outputJsonResult(restResultBean);
    }

    private void restLdapHealthCheck(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException
    {
        final Date startTime = new Date();
        LOGGER.debug(pwmRequest, "beginning restLdapHealthCheck");
        ConfigEditorCookie cookie = readConfigEditorCookie(pwmRequest);
        final String profileID = cookie.getProfile();
        final Configuration config = new Configuration(configManagerBean.getConfiguration());
        final HealthData healthData = LDAPStatusChecker.healthForNewConfiguration(config, pwmRequest.getLocale(), profileID, true, true);
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(healthData);

        pwmRequest.outputJsonResult(restResultBean);
        LOGGER.debug(pwmRequest, "completed restLdapHealthCheck in " + TimeDuration.fromCurrent(startTime).asCompactString());
    }

    private void restDatabaseHealthCheck(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException
    {
        final Date startTime = new Date();
        LOGGER.debug(pwmRequest, "beginning restDatabaseHealthCheck");
        final Configuration config = new Configuration(configManagerBean.getConfiguration());
        final List<HealthRecord> healthRecords = DatabaseStatusChecker.checkNewDatabaseStatus(config);
        final HealthData healthData = HealthRecord.asHealthDataBean(config, pwmRequest.getLocale(), healthRecords);
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(healthData);
        pwmRequest.outputJsonResult(restResultBean);
        LOGGER.debug(pwmRequest, "completed restDatabaseHealthCheck in " + TimeDuration.fromCurrent(startTime).asCompactString());
    }

    private void restSmsHealthCheck(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException
    {
        final Date startTime = new Date();
        LOGGER.debug(pwmRequest, "beginning restSmsHealthCheck");

        final List<HealthRecord> returnRecords = new ArrayList<>();
        final Configuration config = new Configuration(configManagerBean.getConfiguration());

        if (!SmsQueueManager.smsIsConfigured(config)) {
            returnRecords.add(new HealthRecord(HealthStatus.INFO, HealthTopic.SMS, "SMS not configured"));
        } else {
            final Map<String,String> testParams = pwmRequest.readBodyAsJsonStringMap();
            final SmsItemBean testSmsItem = new SmsItemBean(testParams.get("to"),testParams.get("message"));
            try {
                final String responseBody = SmsQueueManager.sendDirectMessage(config,testSmsItem);
                returnRecords.add(new HealthRecord(HealthStatus.INFO, HealthTopic.SMS, "message sent"));
                returnRecords.add(new HealthRecord(HealthStatus.INFO, HealthTopic.SMS, "response body: \n" + StringUtil.escapeHtml(responseBody)));
            } catch (PwmException e) {
                returnRecords.add(new HealthRecord(HealthStatus.WARN, HealthTopic.SMS, "unable to send message: " + e.getMessage()));
            }
        }

        final RestResultBean restResultBean = new RestResultBean();
        final HealthData healthData = HealthRecord.asHealthDataBean(config, pwmRequest.getLocale(), returnRecords);
        restResultBean.setData(healthData);
        pwmRequest.outputJsonResult(restResultBean);
        LOGGER.debug(pwmRequest, "completed restSmsHealthCheck in " + TimeDuration.fromCurrent(startTime).asCompactString());
    }

    private void doUploadFile(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final String key = pwmRequest.readParameterAsString("key");
        final PwmSetting setting = PwmSetting.forKey(key);

        final Map<String, PwmRequest.FileUploadItem> fileUploads;
        try {
            final int maxFileSize = Integer.parseInt(
                    pwmRequest.getConfig().readAppProperty(AppProperty.CONFIG_MAX_JDBC_JAR_SIZE));
            fileUploads = pwmRequest.readFileUploads(maxFileSize, 1);
        } catch (PwmException e) {
            pwmRequest.outputJsonResult(RestResultBean.fromError(e.getErrorInformation(), pwmRequest));
            LOGGER.error(pwmRequest, "error during file upload: " + e.getErrorInformation().toDebugStr());
            return;
        } catch (Throwable e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, "error during file upload: " + e.getMessage());
            pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
            LOGGER.error(pwmRequest, errorInformation);
            return;
        }

        if (fileUploads.containsKey("uploadFile")) {
            final PwmRequest.FileUploadItem uploadItem = fileUploads.get("uploadFile");

            final Map<FileValue.FileInformation, FileValue.FileContent> newFileValueMap = new LinkedHashMap<>();
            newFileValueMap.put(new FileValue.FileInformation(uploadItem.getName(),uploadItem.getType()),new FileValue.FileContent(uploadItem.getContent()));

            final UserIdentity userIdentity = pwmRequest.isAuthenticated()
                    ? pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity()
                    : null;
            configManagerBean.getConfiguration().writeSetting(setting, new FileValue(newFileValueMap), userIdentity);

            pwmRequest.outputJsonResult(RestResultBean.forSuccessMessage(pwmRequest, Message.SUCCESS_UNKNOWN));

            return;
        }

        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,"no file found in upload");
        pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
        LOGGER.error(pwmRequest, "error during file upload: " + errorInformation.toDebugStr());
    }
}
