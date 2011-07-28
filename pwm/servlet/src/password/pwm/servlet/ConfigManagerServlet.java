/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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

package password.pwm.servlet;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.*;
import password.pwm.bean.ConfigManagerBean;
import password.pwm.config.Configuration;
import password.pwm.config.ConfigurationReader;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

public class ConfigManagerServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ConfigManagerServlet.class);

    private static final int MAX_INPUT_LENGTH = 1024 * 100;
    private static final String DEFAULT_PW = "DEFAULT-PW";

// -------------------------- OTHER METHODS --------------------------

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();
        final ConfigurationReader.MODE configMode = pwmSession.getContextManager().getConfigReader().getConfigMode();

        initialize(pwmSession, configMode, configManagerBean, pwmSession.getContextManager().getConfigReader().getConfigurationReadTime());

        final String processActionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST, MAX_INPUT_LENGTH);
        if (processActionParam.length() > 0) {
            if ("getOptions".equalsIgnoreCase(processActionParam)) {
                doGetOptions(req, resp);
                return;
            } else if ("editorPanel".equalsIgnoreCase(processActionParam)) {
                switch (configManagerBean.getEditMode()) {
                    case SETTINGS:
                        req.getSession().getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER_EDITOR_SETTINGS).forward(req, resp);
                        break;

                    case LOCALEBUNDLE:
                        req.getSession().getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER_EDITOR_LOCALEBUNDLE).forward(req, resp);
                        break;
                }
                return;
            } else if ("viewLog".equalsIgnoreCase(processActionParam)) {
                doViewLog(req, resp);
                return;
            }

            Validator.validatePwmFormID(req);

            if ("readSetting".equalsIgnoreCase(processActionParam)) {
                this.readSetting(req, resp);
                return;
            } else if ("writeSetting".equalsIgnoreCase(processActionParam)) {
                this.writeSetting(req, resp);
                return;
            } else if ("resetSetting".equalsIgnoreCase(processActionParam)) {
                this.resetSetting(req);
                return;
            } else if ("generateXml".equalsIgnoreCase(processActionParam)) {
                if (doGenerateXml(req, resp)) {
                    return;
                }
            } else if ("lockConfiguration".equalsIgnoreCase(processActionParam) && configMode != ConfigurationReader.MODE.RUNNING) {
                doLockConfiguration(req);
            } else if ("finishEditing".equalsIgnoreCase(processActionParam)) {
                doFinishEditing(req);
            } else if ("cancelEditing".equalsIgnoreCase(processActionParam)) {
                doCancelEditing(req);
            } else if ("editMode".equalsIgnoreCase(processActionParam)) {
                final EDIT_MODE mode = EDIT_MODE.valueOf(Validator.readStringFromRequest(req,"mode"));
                configManagerBean.setEditMode(mode);
                LOGGER.debug(pwmSession, "switching to editMode " + mode);
            } else if ("setOption".equalsIgnoreCase(processActionParam)) {
                setOptions(req);
            }
        }

        forwardToJSP(req, resp);
    }

    private void initialize(final PwmSession pwmSession, final ConfigurationReader.MODE configMode, final ConfigManagerBean configManagerBean, final Date configurationLoadTime) throws PwmUnrecoverableException {
        if (configMode != ConfigurationReader.MODE.RUNNING) {
            if (configurationLoadTime != configManagerBean.getConfigurationLoadTime()) {
                LOGGER.debug(pwmSession, "initializing configuration bean with configMode=" + configMode);
                configManagerBean.setConfigurationLoadTime(configurationLoadTime);
                configManagerBean.setConfiguration(null);
            }
        }

        // first time setup
        if (configManagerBean.getConfiguration() == null) {
            configManagerBean.setEditMode(EDIT_MODE.NONE);
            switch (configMode) {
                case NEW:
                    if (configManagerBean.getConfiguration() == null) {
                        configManagerBean.setConfiguration(StoredConfiguration.getDefaultConfiguration());
                    }
                    configManagerBean.getConfiguration().writeProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_IS_EDITABLE, "true");
                    break;

                case CONFIGURING:
                    try {
                        final StoredConfiguration runningConfig = pwmSession.getContextManager().getConfigReader().getStoredConfiguration();
                        final StoredConfiguration clonedConfiguration = (StoredConfiguration) runningConfig.clone();
                        clonedConfiguration.writeProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_IS_EDITABLE, "true");
                        configManagerBean.setConfiguration(clonedConfiguration);
                    } catch (CloneNotSupportedException e) {
                        throw new RuntimeException("unexpected error cloning StoredConfiguration", e);
                    }
                    break;

                case RUNNING:
                    if (configManagerBean.getConfiguration() == null) {
                        configManagerBean.setConfiguration(StoredConfiguration.getDefaultConfiguration());
                    }
                    configManagerBean.getConfiguration().writeProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_IS_EDITABLE, "false");
                    break;
            }

            {
                final String notesText = configManagerBean.getConfiguration().readProperty(StoredConfiguration.PROPERTY_KEY_NOTES);
                configManagerBean.setShowNotes(notesText != null && notesText.length() > 0);
            }
        }
    }

    private void doGetOptions(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, PwmUnrecoverableException {
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();
        final StoredConfiguration storedConfig = configManagerBean.getConfiguration();
        final String configEpoch = storedConfig.readProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_EPOCH);
        final Map<String, Object> returnMap = new HashMap<String, Object>();
        if (configEpoch != null && configEpoch.length() > 0) {
            returnMap.put("configEpoch", configEpoch);
        } else {
            returnMap.put("configEpoch", "none");
        }
        if (configManagerBean.getErrorInformation() != null) {
            returnMap.put("error", true);
            returnMap.put("errorCode", configManagerBean.getErrorInformation().getError().getErrorCode());
            returnMap.put("errorDetail", configManagerBean.getErrorInformation().getDetailedErrorMsg());
        }

        {   // otherdata
            final String notesText = configManagerBean.getConfiguration().readPropertyKeys().contains(StoredConfiguration.PROPERTY_KEY_NOTES) ? configManagerBean.getConfiguration().readProperty(StoredConfiguration.PROPERTY_KEY_NOTES) : "";
            returnMap.put("notesText",notesText);
        }

        final Gson gson = new Gson();
        final String outputString = gson.toJson(returnMap);
        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().print(outputString);
    }

    static void doViewLog(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, IOException, ServletException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        final ConfigurationReader.MODE configMode = pwmSession.getContextManager().getConfigReader().getConfigMode();

        if (configMode == ConfigurationReader.MODE.RUNNING) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_AUTHENTICATION_REQUIRED,"cannot view log in RUNNING mode"));
        }

        final ServletContext servletContext = req.getSession().getServletContext();
        servletContext.getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER_LOGVIEW).forward(req, resp);
    }

    private void readSetting(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, PwmUnrecoverableException {
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();
        final StoredConfiguration storedConfig = configManagerBean.getConfiguration();

        final String key = Validator.readStringFromRequest(req, "key", 255);
        final Object returnValue;
        final Map<String, Object> returnMap = new HashMap<String, Object>();
        final PwmSetting theSetting = PwmSetting.forKey(key);

        if (key.startsWith("localeBundle")) {
            final StringTokenizer st = new StringTokenizer(key,"-");
            st.nextToken();
            final PwmConstants.EDITABLE_LOCALE_BUNDLES bundleName = PwmConstants.EDITABLE_LOCALE_BUNDLES.valueOf(st.nextToken());
            final String keyName = st.nextToken();
            final Map<String,String> bundleMap = storedConfig.readLocaleBundleMap(bundleName.getTheClass().getName(),keyName);
            if (bundleMap == null || bundleMap.isEmpty()) {
                final Map<String,String> defaultValueMap = new LinkedHashMap<String, String>();
                for (final Locale locale : PwmSession.getPwmSession(req).getContextManager().getKnownLocales()) {
                    final ResourceBundle localeBundle = ResourceBundle.getBundle(bundleName.getTheClass().getName(),locale);
                    final String localeStr = locale.toString().equalsIgnoreCase("en") ? "" : locale.toString();
                    defaultValueMap.put(localeStr,localeBundle.getString(keyName));
                }
                returnValue = defaultValueMap;
                returnMap.put("isDefault", true);
            } else {
                returnValue = bundleMap;
                returnMap.put("isDefault", false);
            }
            returnMap.put("key", key);
        } else if (theSetting == null) {
            LOGGER.warn("readSetting request for unknown key: " + key);
            returnMap.put("key", key);
            returnMap.put("isDefault", "false");
            returnValue = "UNKNOWN KEY";
        } else {
            switch (theSetting.getSyntax()) {
                case STRING_ARRAY: {
                    final List<String> values = storedConfig.readStringArraySetting(theSetting);
                    final Map<String, String> outputMap = new TreeMap<String, String>();
                    for (int i = 0; i < values.size(); i++) {
                        outputMap.put(String.valueOf(i), values.get(i));
                    }
                    returnValue = outputMap;
                }
                break;

                case LOCALIZED_STRING_ARRAY: {
                    final Map<String, List<String>> values = storedConfig.readLocalizedStringArraySetting(theSetting);
                    final Map<String, Map<String, String>> outputMap = new TreeMap<String, Map<String, String>>();
                    for (final String localeKey : values.keySet()) {
                        final List<String> loopValues = values.get(localeKey);
                        final Map<String, String> loopMap = new TreeMap<String, String>();
                        for (int i = 0; i < loopValues.size(); i++) {
                            loopMap.put(String.valueOf(i), loopValues.get(i));
                        }
                        outputMap.put(localeKey, loopMap);
                    }
                    returnValue = outputMap;
                }
                break;

                case LOCALIZED_STRING:
                case LOCALIZED_TEXT_AREA:
                    returnValue = new TreeMap<String, String>(storedConfig.readLocalizedStringSetting(theSetting));
                    break;
                case PASSWORD:
                    returnValue = DEFAULT_PW;
                    break;
                case SELECT:
                    returnValue = storedConfig.readSetting(theSetting);
                    returnMap.put("options",theSetting.getOptions());
                    break;

                default:
                    returnValue = storedConfig.readSetting(theSetting);
            }
            returnMap.put("key", key);
            returnMap.put("category", theSetting.getCategory().toString());
            returnMap.put("syntax", theSetting.getSyntax().toString());
            returnMap.put("isDefault", storedConfig.isDefaultValue(theSetting));
        }
        returnMap.put("value", returnValue);
        final Gson gson = new Gson();
        final String outputString = gson.toJson(returnMap);
        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().print(outputString);
    }

    private void writeSetting(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, PwmUnrecoverableException {
        Validator.validatePwmFormID(req);
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();
        final StoredConfiguration storedConfig = configManagerBean.getConfiguration();
        final String key = Validator.readStringFromRequest(req, "key");
        final String bodyString = ServletHelper.readRequestBody(req, MAX_INPUT_LENGTH);
        final Gson gson = new Gson();
        final PwmSetting setting = PwmSetting.forKey(key);
        final Map<String, Object> returnMap = new HashMap<String, Object>();

        if (key.startsWith("localeBundle")) {
            final StringTokenizer st = new StringTokenizer(key,"-");
            st.nextToken();
            final PwmConstants.EDITABLE_LOCALE_BUNDLES bundleName = PwmConstants.EDITABLE_LOCALE_BUNDLES.valueOf(st.nextToken());
            final String keyName = st.nextToken();
            final Map<String, String> valueMap = gson.fromJson(bodyString, new TypeToken<Map<String, String>>() {
            }.getType());
            final Map<String, String> outputMap = new TreeMap<String, String>(valueMap);

            storedConfig.writeLocaleBundleMap(bundleName.getTheClass().getName(),keyName, outputMap);
            returnMap.put("isDefault", outputMap.isEmpty());
            returnMap.put("key", key);
            returnMap.put("syntax", PwmSetting.Syntax.LOCALIZED_TEXT_AREA.toString());
        } else {
            switch (setting.getSyntax()) {
                case STRING_ARRAY: {
                    final Map<String, String> valueMap = gson.fromJson(bodyString, new TypeToken<Map<String, String>>() {
                    }.getType());
                    final Map<String, String> outputMap = new TreeMap<String, String>(valueMap);
                    storedConfig.writeStringArraySetting(setting, new ArrayList<String>(outputMap.values()));
                }
                break;

                case LOCALIZED_STRING:
                case LOCALIZED_TEXT_AREA: {
                    final Map<String, String> valueMap = gson.fromJson(bodyString, new TypeToken<Map<String, String>>() {
                    }.getType());
                    final Map<String, String> outputMap = new TreeMap<String, String>(valueMap);
                    storedConfig.writeLocalizedSetting(setting, outputMap);
                }
                break;

                case LOCALIZED_STRING_ARRAY: {
                    final Map<String, Map<String, String>> valueMap = gson.fromJson(bodyString, new TypeToken<Map<String, Map<String, String>>>() {
                    }.getType());
                    final Map<String, List<String>> outputMap = new HashMap<String, List<String>>();
                    for (final String localeKey : valueMap.keySet()) {
                        final List<String> returnList = new LinkedList<String>();
                        for (final String iterKey : new TreeMap<String, String>(valueMap.get(localeKey)).keySet()) {
                            returnList.add(valueMap.get(localeKey).get(iterKey));
                        }
                        outputMap.put(localeKey, returnList);
                    }
                    storedConfig.writeLocalizedStringArraySetting(setting, outputMap);
                }
                break;

                case PASSWORD: {
                    final String value = gson.fromJson(bodyString, new TypeToken<String>() {
                    }.getType());
                    if (!bodyString.equals(DEFAULT_PW)) {
                        storedConfig.writeSetting(setting, value);
                    }
                }
                break;

                case NUMERIC: {
                    final Long value = gson.fromJson(bodyString, new TypeToken<Long>() {
                    }.getType());
                    storedConfig.writeSetting(setting, value.toString());
                }
                break;

                default:
                    final String value = gson.fromJson(bodyString, new TypeToken<String>() {
                    }.getType());
                    storedConfig.writeSetting(setting, value);
            }
            returnMap.put("key", key);
            returnMap.put("category", setting.getCategory().toString());
            returnMap.put("syntax", setting.getSyntax().toString());
            returnMap.put("isDefault", storedConfig.isDefaultValue(setting));
        }
        final String outputString = gson.toJson(returnMap);
        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().print(outputString);
    }

    private void resetSetting(
            final HttpServletRequest req
    ) throws IOException, PwmUnrecoverableException {
        Validator.validatePwmFormID(req);
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();
        final StoredConfiguration storedConfig = configManagerBean.getConfiguration();

        final String bodyString = ServletHelper.readRequestBody(req, MAX_INPUT_LENGTH);

        final Gson gson = new Gson();
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
                storedConfig.resetSetting(setting);
            }
        }
    }

    private boolean doGenerateXml(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();
        final StoredConfiguration configuration = configManagerBean.getConfiguration();

        final List<String> errorStrings = configuration.validateValues();
        if (errorStrings != null && !errorStrings.isEmpty()) {
            final String errorString = errorStrings.get(0);
            PwmSession.getPwmSession(req).getSessionStateBean().setSessionError(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, errorString, errorString));
            return false;
        }

        final String output = configuration.toXml();
        resp.setHeader("content-disposition", "attachment;filename=PwmConfiguration.xml");
        resp.setContentType("text/xml;charset=utf-8");
        resp.getWriter().print(output);
        return true;
    }

    private void doLockConfiguration(
            final HttpServletRequest req
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();

        final StoredConfiguration storedConfiguration = configManagerBean.getConfiguration();
        storedConfiguration.writeProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_IS_EDITABLE, "false");

        try {
            saveConfiguration(pwmSession);
            configManagerBean.setConfiguration(null);
        } catch (PwmUnrecoverableException e) {
            final ErrorInformation errorInfo = e.getErrorInformation();
            pwmSession.getSessionStateBean().setSessionError(errorInfo);
        }
    }

    private void doFinishEditing(
            final HttpServletRequest req
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();
        final ConfigurationReader.MODE configMode = pwmSession.getContextManager().getConfigReader().getConfigMode();

        if (configMode != ConfigurationReader.MODE.RUNNING) {
            configManagerBean.setErrorInformation(null);
            if (!configManagerBean.getConfiguration().validateValues().isEmpty()) {
                final String errorString = configManagerBean.getConfiguration().validateValues().get(0);
                final ErrorInformation errorInfo = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorString);
                configManagerBean.setErrorInformation(errorInfo);
                return;
            }

            try {
                saveConfiguration(pwmSession);
            } catch (PwmUnrecoverableException e) {
                final ErrorInformation errorInfo = e.getErrorInformation();
                pwmSession.getSessionStateBean().setSessionError(errorInfo);
                configManagerBean.setErrorInformation(errorInfo);
                LOGGER.warn(pwmSession, "unable to save configuration: " + e.getMessage());
                return;
            }
        }

        configManagerBean.setEditMode(EDIT_MODE.NONE);
        LOGGER.debug(pwmSession, "save configuration operation completed");
    }

    static void saveConfiguration(final PwmSession pwmSession)
            throws PwmUnrecoverableException {
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();
        final StoredConfiguration storedConfiguration = configManagerBean.getConfiguration();

        {
            final List<String> errorStrings = storedConfiguration.validateValues();
            if (errorStrings != null && !errorStrings.isEmpty()) {
                final String errorString = errorStrings.get(0);
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, errorString, errorString));
            }
        }

        try {
            if (pwmSession.getContextManager().getConfigReader().getConfigMode() != ConfigurationReader.MODE.RUNNING) {
                final ContextManager contextManager = pwmSession.getContextManager();
                contextManager.getConfigReader().saveConfiguration(storedConfiguration);
                contextManager.setLastLdapFailure(null);
                EventManager.reinitializeContext(pwmSession.getContextManager().getServletContext());
            }
        } catch (Exception e) {
            final String errorString = "error saving file: " + e.getMessage();
            LOGGER.error(pwmSession, errorString);
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, errorString, errorString));
        }

        resetInMemoryBean(pwmSession);
    }

    private void doCancelEditing(
            final HttpServletRequest req
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        resetInMemoryBean(pwmSession);
        LOGGER.debug(pwmSession, "cancelled edit actions");
    }

    private void setOptions(
            final HttpServletRequest req
    ) throws IOException, PwmUnrecoverableException {
        Validator.validatePwmFormID(req);
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();

        {
            final String requestedLevelstr = Validator.readStringFromRequest(req, "level", 255);
            if (requestedLevelstr != null && requestedLevelstr.length() > 0) {
                try {
                    configManagerBean.setLevel(PwmSetting.Level.valueOf(requestedLevelstr));
                    LOGGER.trace("setting level to: " + configManagerBean.getLevel());
                } catch (Exception e) {
                    LOGGER.error("unknown level set request: " + requestedLevelstr);
                }
            }
        }
        {
            final String requestedShowDesc = Validator.readStringFromRequest(req, "showDesc", 255);
            if (requestedShowDesc != null && requestedShowDesc.length() > 0) {
                try {
                    configManagerBean.setShowDescr(Boolean.valueOf(requestedShowDesc));
                    LOGGER.trace("setting showDesc to: " + configManagerBean.isShowDescr());
                } catch (Exception e) {
                    LOGGER.error("unknown showDesc set request: " + requestedShowDesc);
                }
            }
        }
        {
            final String requestedShowNotes = Validator.readStringFromRequest(req, "showNotes", 255);
            if (requestedShowNotes != null && requestedShowNotes.length() > 0) {
                try {
                    configManagerBean.setShowNotes(Boolean.valueOf(requestedShowNotes));
                    LOGGER.trace("setting showDesc to: " + configManagerBean.isShowDescr());
                } catch (Exception e) {
                    LOGGER.error("unknown showDesc set request: " + requestedShowNotes);
                }
            }
        }
        {
            final String requestedTemplate = Validator.readStringFromRequest(req, "template", 255);
            if (requestedTemplate != null && requestedTemplate.length() > 0) {
                try {
                    final PwmSetting.Template template = PwmSetting.Template.valueOf(requestedTemplate);
                    configManagerBean.getConfiguration().writeProperty(StoredConfiguration.PROPERTY_KEY_TEMPLATE, template.toString());
                    LOGGER.trace("setting template to: " + requestedTemplate);
                } catch (IllegalArgumentException e) {
                    configManagerBean.getConfiguration().writeProperty(StoredConfiguration.PROPERTY_KEY_TEMPLATE,PwmSetting.Template.DEFAULT.toString());
                    LOGGER.error("unknown template set request: " + requestedTemplate);
                }
            }
        }
        {
            final String requestedCategory = Validator.readStringFromRequest(req, "category", 255);
            if (requestedCategory != null && requestedCategory.length() > 0) {
                try {
                    configManagerBean.setCategory(PwmSetting.Category.valueOf(requestedCategory));
                    LOGGER.trace("setting category to: " + configManagerBean.isShowDescr());
                    configManagerBean.setEditMode(EDIT_MODE.SETTINGS);
                } catch (Exception e) {
                    LOGGER.error("unknown category set request: " + requestedCategory);
                }
            }
        }
        {
            final String requestedLocaleBundle = Validator.readStringFromRequest(req, "localeBundle", 255);
            if (requestedLocaleBundle != null && requestedLocaleBundle.length() > 0) {
                try {
                    configManagerBean.setLocaleBundle(PwmConstants.EDITABLE_LOCALE_BUNDLES.valueOf(requestedLocaleBundle));
                    LOGGER.trace("setting localeBundle to: " + configManagerBean.isShowDescr());
                    configManagerBean.setEditMode(EDIT_MODE.LOCALEBUNDLE);
                } catch (Exception e) {
                    LOGGER.error("unknown localeBundle set request: " + requestedLocaleBundle);
                }
            }
        }
        {
            final String updateDescriptionTextCmd = Validator.readStringFromRequest(req, "updateNotesText", 255);
            if (updateDescriptionTextCmd != null && updateDescriptionTextCmd.equalsIgnoreCase("true")) {
                try {
                    final Gson gson = new Gson();
                    final String bodyString = ServletHelper.readRequestBody(req, MAX_INPUT_LENGTH);
                    final String value = gson.fromJson(bodyString, new TypeToken<String>() {
                    }.getType());
                    configManagerBean.getConfiguration().writeProperty(StoredConfiguration.PROPERTY_KEY_NOTES, value);
                    LOGGER.trace("updated notesText");
                } catch (Exception e) {
                    LOGGER.error("error updating notesText: " + e.getMessage());
                }
            }
        }
    }

    static void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        final ServletContext servletContext = req.getSession().getServletContext();
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();

        if (configManagerBean.getEditMode() != EDIT_MODE.NONE) {
            servletContext.getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER_EDITOR).forward(req, resp);
        } else {
            final Configuration config = PwmSession.getPwmSession(req).getConfig();
            final ConfigurationReader.MODE configMode = PwmSession.getPwmSession(req).getContextManager().getConfigReader().getConfigMode();
            if (config == null || configMode == ConfigurationReader.MODE.NEW) {
                servletContext.getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER_MODE_NEW).forward(req, resp);
            } else if (configMode == ConfigurationReader.MODE.CONFIGURING) {
                servletContext.getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER_MODE_CONFIGURATION).forward(req, resp);
            } else {
                servletContext.getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER_MODE_RUNNING).forward(req, resp);
            }
        }
    }

    private static void resetInMemoryBean(final PwmSession pwmSession) {
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();
        configManagerBean.setConfigurationLoadTime(null);
        configManagerBean.setEditMode(EDIT_MODE.NONE);
    }

// -------------------------- ENUMERATIONS --------------------------

    public static enum EDIT_MODE {
        SETTINGS,
        LOCALEBUNDLE,
        NONE
    }
}
