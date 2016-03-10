/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

package password.pwm.http.servlet.configeditor;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.*;
import password.pwm.bean.SmsItemBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.*;
import password.pwm.config.function.HttpsCertParseFunction;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.config.stored.ValueMetaData;
import password.pwm.config.value.FileValue;
import password.pwm.config.value.ValueFactory;
import password.pwm.config.value.X509CertificateValue;
import password.pwm.error.*;
import password.pwm.health.*;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.configmanager.ConfigManagerServlet;
import password.pwm.i18n.Config;
import password.pwm.i18n.Message;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.ldap.LdapBrowser;
import password.pwm.util.*;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.queue.SmsQueueManager;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.bean.HealthData;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

@WebServlet(
        name = "ConfigEditorServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/config/editor",
                PwmConstants.URL_PREFIX_PRIVATE + "/config/editor/*",
                PwmConstants.URL_PREFIX_PRIVATE + "/config/configeditor",
                PwmConstants.URL_PREFIX_PRIVATE + "/config/configeditor/*",
                PwmConstants.URL_PREFIX_PRIVATE + "/config/ConfigEditor",
                PwmConstants.URL_PREFIX_PRIVATE + "/config/ConfigEditor/*",
        }
)
public class ConfigEditorServlet extends AbstractPwmServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(ConfigEditorServlet.class);

    public enum ConfigEditorAction implements AbstractPwmServlet.ProcessAction {
        readSetting(HttpMethod.POST),
        writeSetting(HttpMethod.POST),
        resetSetting(HttpMethod.POST),
        ldapHealthCheck(HttpMethod.POST),
        databaseHealthCheck(HttpMethod.POST),
        httpsCertificateView(HttpMethod.POST),
        smsHealthCheck(HttpMethod.POST),
        finishEditing(HttpMethod.POST),
        executeSettingFunction(HttpMethod.POST),
        setConfigurationPassword(HttpMethod.POST),
        readChangeLog(HttpMethod.POST),
        search(HttpMethod.POST),
        cancelEditing(HttpMethod.POST),
        uploadFile(HttpMethod.POST),
        setOption(HttpMethod.POST),
        menuTreeData(HttpMethod.POST),
        settingData(HttpMethod.GET),
        testMacro(HttpMethod.POST),
        browseLdap(HttpMethod.POST),
        copyProfile(HttpMethod.POST),

        ;

        private final HttpMethod method;

        ConfigEditorAction(HttpMethod method) {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods() {
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
        public Map<String, String> options;
        public Map<PwmSettingProperty, String> properties;
        public String pattern;
        public String placeholder;
        public int level;
        public List<PwmSettingFlag> flags;
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

    protected ConfigEditorAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException {
        try {
            return ConfigEditorAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


// -------------------------- OTHER METHODS --------------------------

    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ConfigManagerBean configManagerBean = pwmRequest.getPwmApplication().getSessionStateService().getBean(pwmRequest, ConfigManagerBean.class);

        if (configManagerBean.getStoredConfiguration() == null) {
            final StoredConfigurationImpl loadedConfig = ConfigManagerServlet.readCurrentConfiguration(pwmRequest);
            configManagerBean.setConfiguration(loadedConfig);
        }

        pwmSession.setSessionTimeout(
                pwmRequest.getHttpServletRequest().getSession(),
                Integer.parseInt(pwmRequest.getConfig().readAppProperty(AppProperty.CONFIG_EDITOR_IDLE_TIMEOUT)));


        final ConfigEditorAction action = readProcessAction(pwmRequest);

        if (action != null) {
            Validator.validatePwmFormID(pwmRequest);

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

                case httpsCertificateView:
                    restHttpsCertificateView(pwmRequest, configManagerBean);
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
                    restConfigSettingData(pwmRequest, configManagerBean);
                    return;

                case testMacro:
                    restTestMacro(pwmRequest);
                    return;

                case browseLdap:
                    restBrowseLdap(pwmRequest, configManagerBean);
                    return;

                case copyProfile:
                    restCopyProfile(pwmRequest, configManagerBean);
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
        final String profileID = pwmSetting.getCategory().hasProfiles() ? pwmRequest.readParameterAsString("profile") : null;

        try {
            Class implementingClass = Class.forName(functionName);
            SettingUIFunction function = (SettingUIFunction) implementingClass.newInstance();
            final Serializable result = function.provideFunction(pwmRequest, configManagerBean.getStoredConfiguration(), pwmSetting, profileID);
            RestResultBean restResultBean = new RestResultBean();
            restResultBean.setSuccessMessage(Message.Success_Unknown.getLocalizedMessage(pwmRequest.getLocale(),pwmRequest.getConfig()));
            restResultBean.setData(result);
            pwmRequest.outputJsonResult(restResultBean);
        } catch (Exception e) {
            final RestResultBean restResultBean;
            if (e instanceof PwmException) {
                restResultBean = RestResultBean.fromError(((PwmException) e).getErrorInformation(), pwmRequest, true);
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
            throws IOException, PwmUnrecoverableException
    {
        final StoredConfigurationImpl storedConfig = configManagerBean.getStoredConfiguration();

        final String key = pwmRequest.readParameterAsString("key");
        final Object returnValue;
        final LinkedHashMap<String, Object> returnMap = new LinkedHashMap<>();
        final PwmSetting theSetting = PwmSetting.forKey(key);

        if (key.startsWith("localeBundle")) {
            final StringTokenizer st = new StringTokenizer(key, "-");
            st.nextToken();
            final PwmLocaleBundle bundleName = PwmLocaleBundle.valueOf(st.nextToken());
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
            pwmRequest.outputJsonResult(RestResultBean.fromError(new ErrorInformation(PwmError.ERROR_UNKNOWN, errorStr)));
            return;
        } else {
            final String profile = theSetting.getCategory().hasProfiles() ? pwmRequest.readParameterAsString("profile") : null;
            switch (theSetting.getSyntax()) {
                case PASSWORD:
                    returnValue = Collections.singletonMap("isDefault", storedConfig.isDefaultValue(theSetting, profile));
                    break;

                case X509CERT:
                    returnValue = ((X509CertificateValue) storedConfig.readSetting(theSetting, profile)).toInfoMap(true);
                    break;

                case FILE:
                    returnValue = ((FileValue) storedConfig.readSetting(theSetting, profile)).toInfoMap();
                    break;

                default:
                    returnValue = storedConfig.readSetting(theSetting, profile).toNativeObject();

            }

            returnMap.put("isDefault", storedConfig.isDefaultValue(theSetting, profile));
            if (theSetting.getSyntax() == PwmSettingSyntax.SELECT) {
                returnMap.put("options", theSetting.getOptions());
            }
            {
                final ValueMetaData settingMetaData = storedConfig.readSettingMetadata(theSetting, profile);
                if (settingMetaData != null) {
                    if (settingMetaData.getModifyDate() != null) {
                        returnMap.put("modifyTime", settingMetaData.getModifyDate());
                    }
                    if (settingMetaData.getUserIdentity() != null) {
                        returnMap.put("modifyUser", settingMetaData.getUserIdentity());
                    }
                }
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
        final StoredConfigurationImpl storedConfig = configManagerBean.getStoredConfiguration();
        final String key = pwmRequest.readParameterAsString("key");
        final String bodyString = pwmRequest.readRequestBodyAsString();
        final PwmSetting setting = PwmSetting.forKey(key);
        final LinkedHashMap<String, Object> returnMap = new LinkedHashMap<>();
        final UserIdentity loggedInUser = pwmRequest.getPwmSession().isAuthenticated()
                ? pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity()
                : null;

        if (key.startsWith("localeBundle")) {
            final StringTokenizer st = new StringTokenizer(key, "-");
            st.nextToken();
            final PwmLocaleBundle bundleName = PwmLocaleBundle.valueOf(st.nextToken());
            final String keyName = st.nextToken();
            final Map<String, String> valueMap = JsonUtil.deserializeStringMap(bodyString);
            final Map<String, String> outputMap = new LinkedHashMap<>(valueMap);

            storedConfig.writeLocaleBundleMap(bundleName.getTheClass().getName(), keyName, outputMap);
            returnMap.put("isDefault", outputMap.isEmpty());
            returnMap.put("key", key);
        } else {
            final String profileID = setting.getCategory().hasProfiles() ? pwmRequest.readParameterAsString("profile") : null;
            try {
                final StoredValue storedValue = ValueFactory.fromJson(setting, bodyString);
                final List<String> errorMsgs = storedValue.validateValue(setting);
                if (errorMsgs != null && !errorMsgs.isEmpty()) {
                    returnMap.put("errorMessage", setting.getLabel(pwmRequest.getLocale()) + ": " + errorMsgs.get(0));
                }
                storedConfig.writeSetting(setting, profileID, storedValue, loggedInUser);
            } catch (Exception e) {
                final String errorMsg = "error writing default value for setting " + setting.toString() + ", error: " + e.getMessage();
                LOGGER.error(errorMsg, e);
                throw new IllegalStateException(errorMsg, e);
            }
            returnMap.put("key", key);
            returnMap.put("category", setting.getCategory().toString());
            returnMap.put("syntax", setting.getSyntax().toString());
            returnMap.put("isDefault", storedConfig.isDefaultValue(setting, profileID));
        }
        pwmRequest.outputJsonResult(new RestResultBean(returnMap));
    }

    private void restResetSetting(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException {
        final StoredConfigurationImpl storedConfig = configManagerBean.getStoredConfiguration();
        final UserIdentity loggedInUser = pwmRequest.getUserInfoIfLoggedIn();
        final String key = pwmRequest.readParameterAsString("key");
        final PwmSetting setting = PwmSetting.forKey(key);

        if (key.startsWith("localeBundle")) {
            final StringTokenizer st = new StringTokenizer(key, "-");
            st.nextToken();
            final PwmLocaleBundle bundleName = PwmLocaleBundle.valueOf(st.nextToken());
            final String keyName = st.nextToken();
            storedConfig.resetLocaleBundleMap(bundleName.getTheClass().getName(), keyName);
        } else {
            final String profileID = setting.getCategory().hasProfiles() ? pwmRequest.readParameterAsString("profile") : null;
            storedConfig.resetSetting(setting, profileID, loggedInUser);
        }

        pwmRequest.outputJsonResult(RestResultBean.forSuccessMessage(pwmRequest, Message.Success_Unknown));
    }


    private void restSetConfigurationPassword(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        try {
            final Map<String, String> postData = pwmRequest.readBodyAsJsonStringMap();
            final String password = postData.get("password");
            configManagerBean.getStoredConfiguration().setPassword(password);
            configManagerBean.setPasswordVerified(true);
            LOGGER.debug(pwmRequest, "config password updated");
            final RestResultBean restResultBean = RestResultBean.forSuccessMessage(pwmRequest, Message.Success_Unknown);
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

        final List<String> validationErrors = configManagerBean.getStoredConfiguration().validateValues();
        if (!validationErrors.isEmpty()) {
            final String errorString = validationErrors.get(0);
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, errorString, new String[]{errorString});
            restResultBean = RestResultBean.fromError(errorInfo, pwmRequest);
            LOGGER.error(pwmSession, "save configuration aborted, error: " + errorString);
        } else {
            try {
                ConfigManagerServlet.saveConfiguration(pwmRequest, configManagerBean.getStoredConfiguration());
                configManagerBean.setConfiguration(null);
                restResultBean.setError(false);
                configManagerBean.setConfiguration(null);
                LOGGER.debug(pwmSession, "save configuration operation completed");
            } catch (PwmUnrecoverableException e) {
                final ErrorInformation errorInfo = e.getErrorInformation();
                restResultBean = RestResultBean.fromError(errorInfo, pwmRequest);
                LOGGER.warn(pwmSession, "unable to save configuration: " + e.getMessage());
            }
        }

        pwmRequest.outputJsonResult(restResultBean);
    }

    private void restCancelEditing(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        configManagerBean.setConfiguration(null);
        pwmRequest.outputJsonResult(RestResultBean.forSuccessMessage(pwmRequest, Message.Success_Unknown));
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
                    configManagerBean.getStoredConfiguration().writeConfigProperty(ConfigurationProperty.NOTES,
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
                        final PwmSettingTemplate template = PwmSettingTemplate.valueOf(requestedTemplate);
                        configManagerBean.getStoredConfiguration().writeConfigProperty(
                                ConfigurationProperty.LDAP_TEMPLATE, template.toString());
                        LOGGER.trace("setting template to: " + requestedTemplate);
                    } catch (IllegalArgumentException e) {
                        configManagerBean.getStoredConfiguration().writeConfigProperty(
                                ConfigurationProperty.LDAP_TEMPLATE, PwmSettingTemplate.DEFAULT.toString());
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
        final HashMap<String, Object> returnObj = new HashMap<>();
        returnObj.put("html", configManagerBean.getStoredConfiguration().changeLogAsDebugString(locale, true));
        returnObj.put("modified", configManagerBean.getStoredConfiguration().isModified());

        /*
        try {
            final ConfigurationChecker configurationChecker = new ConfigurationChecker();
            final Configuration config = new Configuration(configManagerBean.getConfiguration());
            final List<HealthRecord> healthRecords = configurationChecker.doHealthCheck(
                    config,
                    pwmRequest.getLocale()
            );
            final List<password.pwm.ws.server.rest.bean.HealthRecord> healthRecordBeans = password.pwm.ws.server.rest.bean.HealthRecord.fromHealthRecords(healthRecords, locale,
                    config);

            returnObj.put("health", healthRecordBeans);
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "error generating health records: " + e.getMessage());
        }
        */

        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(returnObj);
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
            final ArrayList<StoredConfigurationImpl.ConfigRecordID> searchResults = new ArrayList<>(configManagerBean.getStoredConfiguration().search(searchTerm, locale));
            final TreeMap<String, Map<String, Map<String, Object>>> returnData = new TreeMap<>();

            for (final StoredConfigurationImpl.ConfigRecordID recordID : searchResults) {
                if (recordID.getRecordType() == StoredConfigurationImpl.ConfigRecordID.RecordType.SETTING) {
                    final PwmSetting setting = (PwmSetting) recordID.getRecordID();
                    final LinkedHashMap<String, Object> settingData = new LinkedHashMap<>();
                    settingData.put("category", setting.getCategory().toString());
                    settingData.put("value", configManagerBean.getStoredConfiguration().readSetting(setting, recordID.getProfileID()).toDebugString(pwmRequest.getLocale()));
                    settingData.put("navigation", setting.getCategory().toMenuLocationDebug(recordID.getProfileID(), locale));
                    settingData.put("default", configManagerBean.getStoredConfiguration().isDefaultValue(setting, recordID.getProfileID()));
                    settingData.put("profile",recordID.getProfileID());

                    final String returnCategory = settingData.get("navigation").toString();
                    if (!returnData.containsKey(returnCategory)) {
                        returnData.put(returnCategory, new LinkedHashMap<String, Map<String, Object>>());
                    }

                    returnData.get(returnCategory).put(setting.getKey(), settingData);
                }
            }

            restResultBean.setData(returnData);
            LOGGER.trace(pwmRequest, "finished search operation with " + returnData.size() + " results in " + TimeDuration.fromCurrent(startTime).asCompactString());
        } else {
            restResultBean.setData(new ArrayList<StoredConfigurationImpl.ConfigRecordID>());
        }

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
        final String profileID = pwmRequest.readParameterAsString("profile");
        final Configuration config = new Configuration(configManagerBean.getStoredConfiguration());
        final HealthData healthData = LDAPStatusChecker.healthForNewConfiguration(pwmRequest.getPwmApplication(), config, pwmRequest.getLocale(), profileID, true, true);
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
        final Configuration config = new Configuration(configManagerBean.getStoredConfiguration());
        final List<HealthRecord> healthRecords = DatabaseStatusChecker.checkNewDatabaseStatus(config);
        final HealthData healthData = HealthRecord.asHealthDataBean(config, pwmRequest.getLocale(), healthRecords);
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(healthData);
        pwmRequest.outputJsonResult(restResultBean);
        LOGGER.debug(pwmRequest, "completed restDatabaseHealthCheck in " + TimeDuration.fromCurrent(startTime).asCompactString());
    }

    private void restHttpsCertificateView(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException {
        final Date startTime = new Date();
        LOGGER.debug(pwmRequest, "beginning restHttpsCertificateView");
        final HttpsCertParseFunction httpsCertParseFunction = new HttpsCertParseFunction();
        final String output = httpsCertParseFunction.provideFunction(pwmRequest, configManagerBean.getStoredConfiguration(),null,null);
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(output);
        pwmRequest.outputJsonResult(restResultBean);
        LOGGER.debug(pwmRequest, "completed restHttpsCertificateView in " + TimeDuration.fromCurrent(startTime).asCompactString());
    }


    private void restSmsHealthCheck(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException {
        final Date startTime = new Date();
        LOGGER.debug(pwmRequest, "beginning restSmsHealthCheck");

        final List<HealthRecord> returnRecords = new ArrayList<>();
        final Configuration config = new Configuration(configManagerBean.getStoredConfiguration());

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
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final String key = pwmRequest.readParameterAsString("key");
        final PwmSetting setting = PwmSetting.forKey(key);
        final int maxFileSize = Integer.parseInt(pwmRequest.getConfig().readAppProperty(AppProperty.CONFIG_MAX_JDBC_JAR_SIZE));

        final FileValue fileValue = readFileUploadToSettingValue(pwmRequest, maxFileSize);
        if (fileValue != null) {
            final UserIdentity userIdentity = pwmRequest.isAuthenticated()
                    ? pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity()
                    : null;

            configManagerBean.getStoredConfiguration().writeSetting(setting, fileValue, userIdentity);
            pwmRequest.outputJsonResult(RestResultBean.forSuccessMessage(pwmRequest, Message.Success_Unknown));
        }
    }

    public static FileValue readFileUploadToSettingValue(
            final PwmRequest pwmRequest,
            final int maxFileSize
    )
            throws PwmUnrecoverableException, IOException, ServletException
    {

        final Map<String, PwmRequest.FileUploadItem> fileUploads;
        try {
            fileUploads = pwmRequest.readFileUploads(maxFileSize, 1);
        } catch (PwmException e) {
            pwmRequest.outputJsonResult(RestResultBean.fromError(e.getErrorInformation(), pwmRequest));
            LOGGER.error(pwmRequest, "error during file upload: " + e.getErrorInformation().toDebugStr());
            return null;
        } catch (Throwable e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, "error during file upload: " + e.getMessage());
            pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
            LOGGER.error(pwmRequest, errorInformation);
            return null;
        }

        if (fileUploads.containsKey("uploadFile")) {
            final PwmRequest.FileUploadItem uploadItem = fileUploads.get("uploadFile");

            final Map<FileValue.FileInformation, FileValue.FileContent> newFileValueMap = new LinkedHashMap<>();
            newFileValueMap.put(new FileValue.FileInformation(uploadItem.getName(), uploadItem.getType()), new FileValue.FileContent(uploadItem.getContent()));

            return new FileValue(newFileValueMap);
        }

        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, "no file found in upload");
        pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
        LOGGER.error(pwmRequest, "error during file upload: " + errorInformation.toDebugStr());
        return null;
    }

    private void restMenuTreeData(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException
    {
        final Date startTime = new Date();
        final ArrayList<NavTreeHelper.NavTreeItem> navigationData = new ArrayList<>();

        final Map<String,Object> inputParameters = pwmRequest.readBodyAsJsonMap(false);
        final boolean modifiedSettingsOnly = (boolean)inputParameters.get("modifiedSettingsOnly");
        final double level = (double)inputParameters.get("level");
        final String filterText = (String)inputParameters.get("text");

        { // root node
            final NavTreeHelper.NavTreeItem categoryInfo = new NavTreeHelper.NavTreeItem();
            categoryInfo.setId("ROOT");
            categoryInfo.setName("ROOT");
            navigationData.add(categoryInfo);
        }

        final StoredConfigurationImpl storedConfiguration = configManagerBean.getStoredConfiguration();
        for (final PwmSettingCategory loopCategory : PwmSettingCategory.sortedValues(pwmRequest.getLocale())) {
            if (NavTreeHelper.categoryMatcher(
                    pwmRequest.getPwmApplication(),
                    loopCategory,
                    storedConfiguration,
                    modifiedSettingsOnly,
                    (int)level,
                    filterText
            )) {
                final NavTreeHelper.NavTreeItem categoryInfo = new NavTreeHelper.NavTreeItem();
                categoryInfo.setId(loopCategory.getKey());
                categoryInfo.setName(loopCategory.getLabel(pwmRequest.getLocale()));

                if (loopCategory.getParent() != null) {
                    categoryInfo.setParent(loopCategory.getParent().getKey());
                } else {
                    categoryInfo.setParent("ROOT");
                }

                if (loopCategory.hasProfiles()) {
                    {
                        final NavTreeHelper.NavTreeItem profileEditorInfo = new NavTreeHelper.NavTreeItem();
                        profileEditorInfo.setId(loopCategory.getKey() + "-EDITOR");
                        final String editItemName = LocaleHelper.getLocalizedMessage(Config.Label_ProfileListEditMenuItem,pwmRequest);
                        profileEditorInfo.setName(editItemName);
                        profileEditorInfo.setType(NavTreeHelper.NavItemType.profileDefinition);
                        profileEditorInfo.setProfileSetting(loopCategory.getProfileSetting().getKey());
                        profileEditorInfo.setParent(loopCategory.getKey());
                        navigationData.add(profileEditorInfo);
                    }

                    final List<PwmSetting> childSettings = loopCategory.getSettings();
                    if (!childSettings.isEmpty()) {
                        final PwmSetting childSetting = childSettings.iterator().next();

                        final List<String> profiles = configManagerBean.getStoredConfiguration().profilesForSetting(childSetting);
                        for (final String profile : profiles) {
                            final NavTreeHelper.NavTreeItem profileInfo = new NavTreeHelper.NavTreeItem();
                            profileInfo.setId(profile);
                            profileInfo.setName(profile.isEmpty() ? "Default" : profile);
                            profileInfo.setParent(loopCategory.getKey());
                            profileInfo.setCategory(loopCategory.getKey());
                            profileInfo.setType(NavTreeHelper.NavItemType.profile);
                            profileInfo.setMenuLocation(loopCategory.toMenuLocationDebug(profile, pwmRequest.getLocale()));
                            navigationData.add(profileInfo);
                        }
                    }
                } else {
                    if (loopCategory.getChildCategories().isEmpty()) {
                        categoryInfo.setType(NavTreeHelper.NavItemType.category);
                    } else {
                        categoryInfo.setType(NavTreeHelper.NavItemType.navigation);
                    }
                }

                navigationData.add(categoryInfo);
            }
        }

        boolean includeDisplayText = false;
        if (level >= 1) {
            for (final PwmLocaleBundle localeBundle : PwmLocaleBundle.values()) {
                if (!localeBundle.isAdminOnly()) {
                    final Set<String> modifiedKeys = new TreeSet<>();
                    if (modifiedSettingsOnly) {
                        modifiedKeys.addAll(NavTreeHelper.determineModifiedKeysSettings(localeBundle, pwmRequest.getConfig(), configManagerBean.getStoredConfiguration()));
                    }
                    if (!modifiedSettingsOnly || !modifiedKeys.isEmpty()) {
                        final NavTreeHelper.NavTreeItem categoryInfo = new NavTreeHelper.NavTreeItem();
                        categoryInfo.setId(localeBundle.toString());
                        categoryInfo.setName(localeBundle.getTheClass().getSimpleName());
                        categoryInfo.setParent("DISPLAY_TEXT");
                        categoryInfo.setType(NavTreeHelper.NavItemType.displayText);
                        categoryInfo.setKeys(new TreeSet<>(modifiedSettingsOnly ? modifiedKeys : localeBundle.getKeys()));
                        navigationData.add(categoryInfo);
                        includeDisplayText = true;
                    }
                }
            }
        }

        if (includeDisplayText) {
            final NavTreeHelper.NavTreeItem categoryInfo = new NavTreeHelper.NavTreeItem();
            categoryInfo.setId("DISPLAY_TEXT");
            categoryInfo.setName("Display Text");
            categoryInfo.setParent("ROOT");
            navigationData.add(categoryInfo);
        }

        NavTreeHelper.moveNavItemToTopOfList(PwmSettingCategory.NOTES.toString(), navigationData);
        NavTreeHelper.moveNavItemToTopOfList(PwmSettingCategory.TEMPLATES.toString(), navigationData);

        LOGGER.trace(pwmRequest,"completed navigation tree data request in " + TimeDuration.fromCurrent(startTime).asCompactString());
        pwmRequest.outputJsonResult(new RestResultBean(navigationData));
    }

    private void restConfigSettingData(final PwmRequest pwmRequest, final ConfigManagerBean configManagerBean)
            throws IOException
    {
        final PwmSettingTemplateSet template = configManagerBean.getStoredConfiguration().getTemplateSet();
        final LinkedHashMap<String, Object> returnMap = new LinkedHashMap<>();
        final Locale locale = pwmRequest.getLocale();
        {
            final LinkedHashMap<String, Object> settingMap = new LinkedHashMap<>();
            for (final PwmSetting setting : PwmSetting.values()) {
                final SettingInfo settingInfo = new SettingInfo();
                settingInfo.key = setting.getKey();
                settingInfo.description = setting.getDescription(locale);
                settingInfo.level = setting.getLevel();
                settingInfo.label = setting.getLabel(locale);
                settingInfo.syntax = setting.getSyntax();
                settingInfo.category = setting.getCategory();
                settingInfo.properties = setting.getProperties();
                settingInfo.required = setting.isRequired();
                settingInfo.hidden = setting.isHidden();
                settingInfo.options = setting.getOptions();
                settingInfo.pattern = setting.getRegExPattern().toString();
                settingInfo.placeholder = setting.getExample(template);
                settingInfo.flags = new ArrayList<>(setting.getFlags());
                settingMap.put(setting.getKey(), settingInfo);
            }
            returnMap.put("settings", settingMap);
        }
        {
            final LinkedHashMap<String, Object> categoryMap = new LinkedHashMap<>();
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
                categoryMap.put(category.getKey(), categoryInfo);
            }
            returnMap.put("categories", categoryMap);
        }
        {
            final LinkedHashMap<String, Object> labelMap = new LinkedHashMap<>();
            for (final PwmLocaleBundle localeBundle : PwmLocaleBundle.values()) {
                final LocaleInfo localeInfo = new LocaleInfo();
                localeInfo.description = localeBundle.getTheClass().getSimpleName();
                localeInfo.key = localeBundle.toString();
                localeInfo.adminOnly = localeBundle.isAdminOnly();
                labelMap.put(localeBundle.getTheClass().getSimpleName(), localeInfo);
            }
            returnMap.put("locales", labelMap);
        }
        {
            final LinkedHashMap<String, Object> varMap = new LinkedHashMap<>();
            varMap.put("ldapProfileIds", configManagerBean.getStoredConfiguration().readSetting(PwmSetting.LDAP_PROFILE_LIST).toNativeObject());
            varMap.put("currentTemplate",configManagerBean.getStoredConfiguration().getTemplateSet());
            if (pwmRequest.getPwmApplication().getApplicationMode() == PwmApplicationMode.CONFIGURATION && !PwmConstants.TRIAL_MODE) {
                if (!configManagerBean.isConfigUnlockedWarningShown()) {
                    varMap.put("configUnlocked",true);
                    configManagerBean.setConfigUnlockedWarningShown(true);
                }
            }
            varMap.put("configurationNotes", configManagerBean.getStoredConfiguration().readConfigProperty(ConfigurationProperty.NOTES));
            returnMap.put("var", varMap);
        }

        final RestResultBean restResultBean = new RestResultBean();

        restResultBean.setData(returnMap);
        pwmRequest.outputJsonResult(restResultBean);
    }

    private void restTestMacro(final PwmRequest pwmRequest) throws IOException, ServletException {
        try {
            final Map<String, String> inputMap = pwmRequest.readBodyAsJsonStringMap(PwmHttpRequestWrapper.Flag.BypassValidation);
            if (inputMap == null || !inputMap.containsKey("input")) {
                pwmRequest.outputJsonResult(new RestResultBean("missing input"));
                return;
            }

            final MacroMachine macroMachine;
            if (pwmRequest.isAuthenticated()) {
                macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine(pwmRequest.getPwmApplication());
            } else {
                macroMachine = MacroMachine.forNonUserSpecific(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel());
            }
            final String input = inputMap.get("input");
            final String output = macroMachine.expandMacros(input);
            pwmRequest.outputJsonResult(new RestResultBean(output));
        } catch (PwmUnrecoverableException e) {
            LOGGER.error(pwmRequest, e.getErrorInformation());
            pwmRequest.respondWithError(e.getErrorInformation());
        }
    }

    private void restBrowseLdap(final PwmRequest pwmRequest, final ConfigManagerBean configManagerBean)
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final Date startTime = new Date();
        final Map<String, String> inputMap = pwmRequest.readBodyAsJsonStringMap(PwmHttpRequestWrapper.Flag.BypassValidation);
        final String profile = inputMap.get("profile");
        final String dn = inputMap.containsKey("dn") ? inputMap.get("dn") : "";

        final LdapBrowser ldapBrowser = new LdapBrowser(configManagerBean.getStoredConfiguration());
        final LdapBrowser.LdapBrowseResult result = ldapBrowser.doBrowse(profile, dn);
        ldapBrowser.close();

        LOGGER.trace(pwmRequest, "performed ldapBrowse operation in "
                + TimeDuration.fromCurrent(startTime).asCompactString()
                + ", result=" + JsonUtil.serialize(result));

        pwmRequest.outputJsonResult(new RestResultBean(result));
    }

    private void restCopyProfile(final PwmRequest pwmRequest, final ConfigManagerBean configManagerBean)
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final Map<String, String> inputMap = pwmRequest.readBodyAsJsonStringMap(PwmHttpRequestWrapper.Flag.BypassValidation);

        final String settingKey = inputMap.get("setting");
        final PwmSetting setting = PwmSetting.forKey(settingKey);
        PwmSettingCategory category = null;
        for (final PwmSettingCategory loopCategory : PwmSettingCategory.values()) {
            if (loopCategory.hasProfiles()) {
                if (loopCategory.getProfileSetting() == setting) {
                    category = loopCategory;
                }
            }
        }

        final String sourceID = inputMap.get("sourceID");
        final String destinationID = inputMap.get("destinationID");
        try {
            configManagerBean.getStoredConfiguration().copyProfileID(category, sourceID, destinationID, pwmRequest.getUserInfoIfLoggedIn());
            pwmRequest.outputJsonResult(RestResultBean.forSuccessMessage(pwmRequest,Message.Success_Unknown));
        } catch (PwmUnrecoverableException e) {
            pwmRequest.outputJsonResult(RestResultBean.fromError(e.getErrorInformation(), pwmRequest));
        }
    }

}