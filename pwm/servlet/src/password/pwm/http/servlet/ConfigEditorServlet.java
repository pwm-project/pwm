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
import password.pwm.config.value.X509CertificateValue;
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
import java.io.Serializable;
import java.util.*;

public class ConfigEditorServlet extends PwmServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(ConfigEditorServlet.class);

    private static final String COOKIE_NAME_PREFERENCES = "ConfigEditor_preferences";

    public enum ConfigEditorAction implements PwmServlet.ProcessAction {
        readSetting(HttpMethod.POST),
        writeSetting(HttpMethod.POST),
        resetSetting(HttpMethod.POST),
        ldapHealthCheck(HttpMethod.POST),
        databaseHealthCheck(HttpMethod.POST),
        smsHealthCheck(HttpMethod.POST),
        finishEditing(HttpMethod.POST),
        executeSettingFunction(HttpMethod.POST),
        setConfigurationPassword(HttpMethod.POST),
        readChangeLog(HttpMethod.POST),
        search(HttpMethod.POST),
        cancelEditing(HttpMethod.POST),
        uploadFile(HttpMethod.POST),
        setOption(HttpMethod.POST),
        menuTreeData(HttpMethod.GET),
        settingData(HttpMethod.GET),

        ;

        private final HttpMethod method;

        ConfigEditorAction(HttpMethod method)
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods()
        {
            return Collections.singletonList(method);
        }
    }

    public static class SettingInfo implements Serializable {
        public String key;
        public String label;
        public String description;
        public PwmSettingCategory category;
        public PwmSettingSyntax syntax;
        public boolean hidden;
        public boolean required;
        public Map<String,String> options;
        public String pattern;
        public String placeholder;
    }

    public static class CategoryInfo implements Serializable {
        public int level;
        public String key;
        public String description;
        public String label;
        public PwmSettingSyntax syntax;
        public String parent;
        public boolean hidden;
    }

    public static class LocaleInfo implements Serializable {
        public String description;
        public String key;
        public boolean adminOnly;
    }

    public static class TemplateInfo implements Serializable {
        public String description;
        public String key;
    }

    protected ConfigEditorAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException {
        try {
            return ConfigEditorAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static ConfigEditorCookie readConfigEditorCookie(final PwmRequest pwmRequest) {
        ConfigEditorCookie cookie = null;
        try {
            final String jsonString = ServletHelper.readCookie(pwmRequest.getHttpServletRequest(), COOKIE_NAME_PREFERENCES);
            cookie = JsonUtil.getGson().fromJson(jsonString, ConfigEditorCookie.class);
        } catch (Exception e) {
            LOGGER.warn("error parsing cookie preferences: " + e.getMessage());
        }
        if (cookie == null) {
            cookie = new ConfigEditorCookie();
        }

        return cookie;
    }


// -------------------------- OTHER METHODS --------------------------

    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();

        ConfigManagerServlet.checkAuthentication(pwmRequest, configManagerBean);

        if (configManagerBean.getConfiguration() == null) {
            final StoredConfiguration loadedConfig = ConfigManagerServlet.readCurrentConfiguration(pwmRequest);
            configManagerBean.setConfiguration(loadedConfig);
        }

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

                case menuTreeData:
                    restMenuTreeData(pwmRequest, configManagerBean);
                    return;

                case settingData:
                    restConfigSettingData(pwmRequest);
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
            throws IOException, PwmUnrecoverableException {
        final String bodyString = pwmRequest.readRequestBodyAsString();
        final Map<String, String> requestMap = JsonUtil.deserializeStringMap(bodyString);
        final PwmSetting pwmSetting = PwmSetting.forKey(requestMap.get("setting"));
        final String functionName = requestMap.get("function");
        final String profileID = readConfigEditorCookie(pwmRequest).getProfile();

        try {
            Class implementingClass = Class.forName(functionName);
            SettingUIFunction function = (SettingUIFunction) implementingClass.newInstance();
            final String result = function.provideFunction(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), configManagerBean.getConfiguration(), pwmSetting, profileID);
            RestResultBean restResultBean = new RestResultBean();
            restResultBean.setSuccessMessage(result);
            pwmRequest.outputJsonResult(restResultBean);
        } catch (Exception e) {
            final RestResultBean restResultBean;
            if (e instanceof PwmException) {
                final String errorMsg = "error while loading data: " + ((PwmException) e).getErrorInformation().getDetailedErrorMsg();
                final ErrorInformation errorInformation = new ErrorInformation(((PwmException) e).getError(), errorMsg);
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
            final StringTokenizer st = new StringTokenizer(key, "-");
            st.nextToken();
            final PwmConstants.EDITABLE_LOCALE_BUNDLES bundleName = PwmConstants.EDITABLE_LOCALE_BUNDLES.valueOf(st.nextToken());
            final String keyName = st.nextToken();
            final Map<String, String> bundleMap = storedConfig.readLocaleBundleMap(bundleName.getTheClass().getName(), keyName);
            if (bundleMap == null || bundleMap.isEmpty()) {
                final Map<String, String> defaultValueMap = new LinkedHashMap<>();
                final String defaultLocaleValue = ResourceBundle.getBundle(bundleName.getTheClass().getName(), PwmConstants.DEFAULT_LOCALE).getString(keyName);
                for (final Locale locale : pwmRequest.getConfig().getKnownLocales()) {
                    final ResourceBundle localeBundle = ResourceBundle.getBundle(bundleName.getTheClass().getName(), locale);
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
            final String errorStr = "readSettingAsString request for unknown key: " + key;
            LOGGER.warn(errorStr);
            pwmRequest.outputJsonResult(RestResultBean.fromError(new ErrorInformation(PwmError.ERROR_UNKNOWN,errorStr)));
            return;
        } else {
            final String profile = theSetting.getCategory().hasProfiles() ? readConfigEditorCookie(pwmRequest).getProfile() : null;
            switch(theSetting.getSyntax()) {
                case PASSWORD:
                    returnValue = Collections.singletonMap("isDefault", storedConfig.isDefaultValue(theSetting,profile));
                    break;

                case X509CERT:
                    returnValue = ((X509CertificateValue)storedConfig.readSetting(theSetting, profile)).toInfoMap(true);
                    break;

                case FILE:
                    returnValue = ((FileValue)storedConfig.readSetting(theSetting, profile)).toInfoMap();
                    break;

                default:
                    returnValue = storedConfig.readSetting(theSetting, profile).toNativeObject();

            }

            returnMap.put("isDefault", storedConfig.isDefaultValue(theSetting,profile));
            if (theSetting.getSyntax() == PwmSettingSyntax.SELECT) {
                returnMap.put("options", theSetting.getOptions());
            }
            returnMap.put("key", key);
            returnMap.put("category", theSetting.getCategory().toString());
            returnMap.put("syntax", theSetting.getSyntax().toString());
        }
        returnMap.put("value", returnValue);
        pwmRequest.outputJsonResult(new RestResultBean(returnMap));
    }

    private void restWriteSetting(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException {
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
            final StringTokenizer st = new StringTokenizer(key, "-");
            st.nextToken();
            final PwmConstants.EDITABLE_LOCALE_BUNDLES bundleName = PwmConstants.EDITABLE_LOCALE_BUNDLES.valueOf(st.nextToken());
            final String keyName = st.nextToken();
            final Map<String, String> valueMap = JsonUtil.getGson().fromJson(bodyString,
                    new TypeToken<Map<String, String>>() {
                    }.getType());
            final Map<String, String> outputMap = new LinkedHashMap<>(valueMap);

            storedConfig.writeLocaleBundleMap(bundleName.getTheClass().getName(), keyName, outputMap);
            returnMap.put("isDefault", outputMap.isEmpty());
            returnMap.put("key", key);
        } else {
            try {
                final StoredValue storedValue = ValueFactory.fromJson(setting, bodyString);
                final List<String> errorMsgs = storedValue.validateValue(setting);
                if (errorMsgs != null && !errorMsgs.isEmpty()) {
                    returnMap.put("errorMessage", setting.getLabel(pwmRequest.getLocale()) + ": " + errorMsgs.get(0));
                }
                if (setting.getCategory().hasProfiles()) {
                    storedConfig.writeSetting(setting, cookie.getProfile(), storedValue, loggedInUser);
                } else {
                    storedConfig.writeSetting(setting, storedValue, loggedInUser);
                }
            } catch (Exception e) {
                final String errorMsg = "error writing default value for setting " + setting.toString() + ", error: " + e.getMessage();
                LOGGER.error(errorMsg, e);
                throw new IllegalStateException(errorMsg, e);
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
            throws IOException, PwmUnrecoverableException {
        final StoredConfiguration storedConfig = configManagerBean.getConfiguration();
        final UserIdentity loggedInUser = pwmRequest.getUserInfoIfLoggedIn();
        final String key = pwmRequest.readParameterAsString("key");
        final PwmSetting setting = PwmSetting.forKey(key);

        if (key.startsWith("localeBundle")) {
            final StringTokenizer st = new StringTokenizer(key, "-");
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

        pwmRequest.outputJsonResult(RestResultBean.forSuccessMessage(pwmRequest, Message.SUCCESS_UNKNOWN));
    }


    private void restSetConfigurationPassword(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        try {
            final Map<String, String> postData = pwmRequest.readBodyAsJsonStringMap();
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
            throws IOException, ServletException, PwmUnrecoverableException {
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        RestResultBean restResultBean = new RestResultBean();
        final HashMap<String, String> resultData = new HashMap<>();
        restResultBean.setData(resultData);

        if (!configManagerBean.getConfiguration().validateValues().isEmpty()) {
            final String errorString = configManagerBean.getConfiguration().validateValues().get(0);
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, errorString);
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
            throws IOException, ServletException, PwmUnrecoverableException {
        configManagerBean.setConfiguration(null);
        pwmRequest.outputJsonResult(RestResultBean.forSuccessMessage(pwmRequest, Message.SUCCESS_UNKNOWN));
    }

    private void setOptions(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException {
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

    void restReadChangeLog(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException {
        final Locale locale = pwmRequest.getLocale();
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(configManagerBean.getConfiguration().changeLogAsDebugString(locale, true));
        pwmRequest.outputJsonResult(restResultBean);
    }

    void restSearchSettings(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException {
        final Date startTime = new Date();
        final String bodyData = pwmRequest.readRequestBodyAsString();
        final Map<String, String> valueMap = JsonUtil.deserializeStringMap(bodyData);
        final Locale locale = pwmRequest.getLocale();
        final RestResultBean restResultBean = new RestResultBean();
        final String searchTerm = valueMap.get("search");
        if (searchTerm != null && !searchTerm.isEmpty()) {
            final ArrayList<StoredConfiguration.ConfigRecordID> searchResults = new ArrayList(configManagerBean.getConfiguration().search(searchTerm, locale));
            final TreeMap<String, Map<String, Map<String, Object>>> returnData = new TreeMap<>();

            for (final StoredConfiguration.ConfigRecordID recordID : searchResults) {
                if (recordID.getRecordType() == StoredConfiguration.ConfigRecordID.RecordType.SETTING) {
                    final PwmSetting setting = (PwmSetting) recordID.getRecordID();
                    final LinkedHashMap<String, Object> settingData = new LinkedHashMap<>();
                    settingData.put("category", setting.getCategory().toString());
                    settingData.put("value", configManagerBean.getConfiguration().readSetting(setting, recordID.getProfileID()).toDebugString(true, pwmRequest.getLocale()));
                    settingData.put("navigation", setting.toMenuLocationDebug(null,locale));
                    settingData.put("default", configManagerBean.getConfiguration().isDefaultValue(setting, recordID.getProfileID()));

                    final StringBuilder returnCategory = new StringBuilder();
                    returnCategory.append(setting.getCategory().getLabel(locale));

                    if (recordID.getProfileID() != null && !recordID.getProfileID().isEmpty()) {
                        settingData.put("profile", recordID.getProfileID());
                        returnCategory.append(" -> " + recordID.getProfileID());
                    }

                    PwmSettingCategory parentCategory = setting.getCategory().getParent();
                    while (parentCategory != null) {
                        returnCategory.insert(0,parentCategory.getLabel(locale) + " -> ");
                        parentCategory = parentCategory.getParent();
                    }


                    if (!returnData.containsKey(returnCategory.toString())) {
                        returnData.put(returnCategory.toString(), new LinkedHashMap<String, Map<String, Object>>());
                    }

                    returnData.get(returnCategory.toString()).put(setting.getKey(), settingData);
                }
            }

            restResultBean.setData(returnData);
            LOGGER.trace(pwmRequest, "finished search operation with " + returnData.size() + " results in " + TimeDuration.fromCurrent(startTime).asCompactString());
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
        final LinkedHashMap<String, String> returnMap = new LinkedHashMap<>();
        for (final AppProperty appProperty : AppProperty.values()) {
            final String value = storedConfig.readAppProperty(appProperty);
            if (value != null) {
                returnMap.put(appProperty.getKey(), value);
            }
        }
        pwmRequest.outputJsonResult(new RestResultBean(returnMap));
    }

    private void restWriteProperties(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException {
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
                configManagerBean.getConfiguration().writeAppProperty(appProperty, valueMap.get(key));
                seenProperties.add(appProperty);
            }
        }

        final Set<AppProperty> removedProps = new LinkedHashSet<>();
        removedProps.addAll(storedProperties);
        removedProps.removeAll(seenProperties);
        for (final AppProperty appProperty : removedProps) {
            configManagerBean.getConfiguration().writeAppProperty(appProperty, null);
        }

        final RestResultBean restResultBean = new RestResultBean();
        pwmRequest.outputJsonResult(restResultBean);
    }

    private void restLdapHealthCheck(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException {
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
            throws IOException, PwmUnrecoverableException {
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
            throws IOException, PwmUnrecoverableException {
        final Date startTime = new Date();
        LOGGER.debug(pwmRequest, "beginning restSmsHealthCheck");

        final List<HealthRecord> returnRecords = new ArrayList<>();
        final Configuration config = new Configuration(configManagerBean.getConfiguration());

        if (!SmsQueueManager.smsIsConfigured(config)) {
            returnRecords.add(new HealthRecord(HealthStatus.INFO, HealthTopic.SMS, "SMS not configured"));
        } else {
            final Map<String, String> testParams = pwmRequest.readBodyAsJsonStringMap();
            final SmsItemBean testSmsItem = new SmsItemBean(testParams.get("to"), testParams.get("message"));
            try {
                final String responseBody = SmsQueueManager.sendDirectMessage(config, testSmsItem);
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
            throws PwmUnrecoverableException, IOException, ServletException {
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
            newFileValueMap.put(new FileValue.FileInformation(uploadItem.getName(), uploadItem.getType()), new FileValue.FileContent(uploadItem.getContent()));

            final UserIdentity userIdentity = pwmRequest.isAuthenticated()
                    ? pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity()
                    : null;
            configManagerBean.getConfiguration().writeSetting(setting, new FileValue(newFileValueMap), userIdentity);

            pwmRequest.outputJsonResult(RestResultBean.forSuccessMessage(pwmRequest, Message.SUCCESS_UNKNOWN));

            return;
        }

        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, "no file found in upload");
        pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
        LOGGER.error(pwmRequest, "error during file upload: " + errorInformation.toDebugStr());
    }

    private void restMenuTreeData(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException
    {
        final ArrayList<Map<String,Object>> navigationData = new ArrayList<>();

        { // root node
            final Map<String,Object> categoryInfo = new HashMap<>();
            categoryInfo.put("id","ROOT");
            categoryInfo.put("name","ROOT");
            navigationData.add(categoryInfo);
        }

        {
            final Map<String,Object> categoryInfo = new HashMap<>();
            categoryInfo.put("id","HOME");
            categoryInfo.put("name","Home");
            categoryInfo.put("parent","ROOT");
            navigationData.add(categoryInfo);
        }

        for (final PwmSettingCategory loopCategory : PwmSettingCategory.values()) {
            if (!loopCategory.isHidden()) {
                final Map<String, Object> categoryInfo = new HashMap<>();
                categoryInfo.put("id", loopCategory.getKey());
                categoryInfo.put("name", loopCategory.getLabel(pwmRequest.getLocale()));


                if (loopCategory.getParent() != null && loopCategory != PwmSettingCategory.LDAP_PROFILE) {
                    categoryInfo.put("parent", loopCategory.getParent().getKey());
                } else {
                    categoryInfo.put("parent", "ROOT");
                }

                if (loopCategory.hasProfiles()) {
                    {
                        final Map<String, Object> profileEditorInfo = new HashMap<>();
                        profileEditorInfo.put("id", loopCategory.getKey() + "-EDITOR");
                        profileEditorInfo.put("name", "Edit Profiles");
                        profileEditorInfo.put("type", "profile-definition");
                        profileEditorInfo.put("profile-setting", loopCategory.getProfileSetting().getKey());
                        profileEditorInfo.put("parent", loopCategory.getKey());
                        navigationData.add(profileEditorInfo);
                    }

                    final List<PwmSetting> childSettings = loopCategory.getSettings();
                    if (!childSettings.isEmpty()) {
                        final PwmSetting childSetting = childSettings.iterator().next();

                        List<String> profiles = configManagerBean.getConfiguration().profilesForSetting(childSetting);
                        for (final String profile : profiles) {
                            final Map<String, Object> profileInfo = new HashMap<>();
                            profileInfo.put("id", profile);
                            profileInfo.put("name", profile.isEmpty() ? "Default" : profile);
                            profileInfo.put("parent", loopCategory.getKey());
                            profileInfo.put("category", loopCategory.getKey());
                            profileInfo.put("type", "profile");
                            navigationData.add(profileInfo);
                        }
                    }
                } else {
                    if (loopCategory.getChildCategories().isEmpty()) {
                        categoryInfo.put("type", "category");
                    }else {
                        categoryInfo.put("type", "navigation");
                    }
                }

                if (loopCategory == PwmSettingCategory.LDAP_PROFILE) {
                    navigationData.add(2, categoryInfo); // after 'HOME'
                } else {
                    navigationData.add(categoryInfo);
                }
            }
        }

        {
            final Map<String, Object> categoryInfo = new HashMap<>();
            categoryInfo.put("id", "DISPLAY_TEXT");
            categoryInfo.put("name", "Display Text");
            categoryInfo.put("parent", "ROOT");
            navigationData.add(categoryInfo);
        }
        for (final PwmConstants.EDITABLE_LOCALE_BUNDLES localeBundle : PwmConstants.EDITABLE_LOCALE_BUNDLES.values()) {
            if (!localeBundle.isAdminOnly()) {
                final Map<String, Object> categoryInfo = new HashMap<>();
                categoryInfo.put("id", localeBundle.toString());
                categoryInfo.put("name", localeBundle.getTheClass().getSimpleName());
                categoryInfo.put("parent", "DISPLAY_TEXT");
                categoryInfo.put("type", "displayText");
                final ResourceBundle bundle = ResourceBundle.getBundle(localeBundle.getTheClass().getName());
                final List<String> keys = new ArrayList<>();
                for (final String key : new TreeSet<>(Collections.list(bundle.getKeys()))) {
                    keys.add(key);
                }
                categoryInfo.put("keys", keys);
                navigationData.add(categoryInfo);
            }
        }

        pwmRequest.outputJsonResult(new RestResultBean(navigationData));
    }

    private void restConfigSettingData(final PwmRequest pwmRequest) throws IOException {
        final LinkedHashMap<String,Object> returnMap = new LinkedHashMap<>();
        final Locale locale = pwmRequest.getLocale();
        {
            final LinkedHashMap<String,Object> settingMap = new LinkedHashMap<>();
            for (final PwmSetting setting : PwmSetting.values()) {
                final SettingInfo settingInfo = new SettingInfo();
                settingInfo.key = setting.getKey();
                settingInfo.description = setting.getDescription(locale);
                settingInfo.label = setting.getLabel(locale);
                settingInfo.syntax = setting.getSyntax();
                settingInfo.category = setting.getCategory();
                settingInfo.required = setting.isRequired();
                settingInfo.hidden = setting.isHidden();
                settingInfo.options = setting.getOptions();
                settingInfo.pattern = setting.getRegExPattern().toString();
                settingInfo.placeholder = setting.getPlaceholder(locale);
                settingMap.put(setting.getKey(),settingInfo);
            }
            returnMap.put("settings",settingMap);
        }
        {
            final LinkedHashMap<String,Object> categoryMap = new LinkedHashMap<>();
            for (final PwmSettingCategory category : PwmSettingCategory.values()) {
                final CategoryInfo categoryInfo = new CategoryInfo();
                categoryInfo.key = category.getKey();
                categoryInfo.level = category.getLevel();
                categoryInfo.description = category.getDescription(locale);
                categoryInfo.label = category.getLabel(locale);
                categoryInfo.hidden = category.isHidden();
                if (category.getParent() != null) {
                    categoryInfo.parent = category.getParent().getKey();
                }
                categoryMap.put(category.getKey(),categoryInfo);
            }
            returnMap.put("categories",categoryMap);
        }
        {
            final LinkedHashMap<String,Object> labelMap = new LinkedHashMap<>();
            for (final PwmConstants.EDITABLE_LOCALE_BUNDLES localeBundle : PwmConstants.EDITABLE_LOCALE_BUNDLES.values()) {
                final LocaleInfo localeInfo = new LocaleInfo();
                localeInfo.description = localeBundle.getTheClass().getSimpleName();
                localeInfo.key = localeBundle.toString();
                localeInfo.adminOnly = localeBundle.isAdminOnly();
                labelMap.put(localeBundle.getTheClass().getSimpleName(),localeInfo);
            }
            returnMap.put("locales",labelMap);
        }
        {
            final LinkedHashMap<String,Object> templateMap = new LinkedHashMap<>();
            for (final PwmSetting.Template template : PwmSetting.Template.values()) {
                final TemplateInfo templateInfo = new TemplateInfo();
                templateInfo.description = template.getLabel(locale);
                templateInfo.key = template.toString();
                templateMap.put(template.toString(),templateInfo);
            }
            returnMap.put("templates",templateMap);
        }

        final RestResultBean restResultBean = new RestResultBean();

        restResultBean.setData(returnMap);
        pwmRequest.outputJsonResult(restResultBean);
    }
}