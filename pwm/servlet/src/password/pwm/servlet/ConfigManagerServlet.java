/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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
import password.pwm.ContextManager;
import password.pwm.PwmConstants;
import password.pwm.PwmSession;
import password.pwm.Validator;
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

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ConfigManagerServlet.class);

    private static final int MAX_INPUT_LENGTH = 1024 * 100;
    private static final String DEFAULT_PW = "DEFAULT-PW";

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        //clear any errors in the session's state bean
        pwmSession.getSessionStateBean().setSessionError(null);

        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();
        final ConfigurationReader.MODE configMode = pwmSession.getContextManager().getConfigReader().getConfigMode();

        initialize(pwmSession, configMode, configManagerBean, pwmSession.getContextManager().getConfigReader().getConfigurationReadTime());

        final String processActionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST, MAX_INPUT_LENGTH);
        if (processActionParam.length() > 0) {
            if ("getOptions".equalsIgnoreCase(processActionParam)) {
                doGetOptions(req, resp);
                return;
            } else if ("editorPanel".equalsIgnoreCase(processActionParam)) {
                if ("fieldEditor".equalsIgnoreCase(Validator.readStringFromRequest(req, "category"))) {
                    req.getSession().getServletContext().getRequestDispatcher('/' + "WEB-INF/jsp/configmanager-editor-fields.jsp").forward(req, resp);
                } else {
                    req.getSession().getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER_EDITOR_PANEL).forward(req, resp);
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
                configManagerBean.setEditorMode(true);
                LOGGER.debug(pwmSession, "switching to edit mode");
            } else if ("setOption".equalsIgnoreCase(processActionParam)) {
                setOptions(req);
            }
        }

        forwardToJSP(req, resp);
    }

    private void initialize(final PwmSession pwmSession, final ConfigurationReader.MODE configMode, final ConfigManagerBean configManagerBean, final Date configurationLoadTime) {
        if (configMode != ConfigurationReader.MODE.RUNNING) {
            if (configurationLoadTime != configManagerBean.getConfigurationLoadTime()) {
                LOGGER.debug(pwmSession, "initializing configuration bean with configMode=" + configMode);
                configManagerBean.setConfigurationLoadTime(configurationLoadTime);
                configManagerBean.setConfiguration(null);
            }
        }

        // first time setup
        if (configManagerBean.getConfiguration() == null) {
            configManagerBean.setEditorMode(false);
            switch (configMode) {
                case NEW:
                    if (configManagerBean.getConfiguration() == null) {
                        configManagerBean.setConfiguration(StoredConfiguration.getDefaultConfiguration());
                    }
                    configManagerBean.getConfiguration().writeProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_IS_EDITABLE, "true");
                    break;

                case CONFIGURATION:
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
            configManagerBean.setShowNotes(configManagerBean.getConfiguration().readProperty(StoredConfiguration.PROPERTY_KEY_NOTES) != null);
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

        /*
        if (key.startsWith("Field-PwmError-")) {
            final String displayKey = key.substring(15,key.length());
            final Map<String,String> values = new TreeMap<String, String>();
            for (final Locale locale : ContextManager.getContextManager(req).getKnownLocales()) {
                values.put(locale.toString(),PwmError.getDisplayString(displayKey,locale));
            }
            returnValue = values;
            returnMap.put("key", key);
            returnMap.put("value", returnValue);
            returnMap.put("isDefault", true);
        } else */
        if (theSetting == null) {
            LOGGER.warn("readSetting request for unknown key: " + key);
            returnMap.put("key", key);
            returnMap.put("value", "UNKNOWN KEY");
            returnMap.put("isDefault", "false");
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
            returnMap.put("value", returnValue);
            returnMap.put("category", theSetting.getCategory().toString());
            returnMap.put("syntax", theSetting.getSyntax().toString());
            returnMap.put("isDefault", storedConfig.isDefaultValue(theSetting));
        }
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

        final Map<String, Object> returnMap = new HashMap<String, Object>();
        returnMap.put("key", key);
        returnMap.put("category", setting.getCategory().toString());
        returnMap.put("syntax", setting.getSyntax().toString());
        returnMap.put("isDefault", storedConfig.isDefaultValue(setting));
        final String outputString = gson.toJson(returnMap);
        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().print(outputString);
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
                    configManagerBean.getConfiguration().writeProperty(StoredConfiguration.PROPERTY_KEY_TEMPLATE,template.toString());
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
                } catch (Exception e) {
                    LOGGER.error("unknown category set request: " + requestedCategory);
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

        final Set<PwmSetting.Category> availCategories = PwmSetting.valuesByCategory(configManagerBean.getLevel()).keySet();
        if (!availCategories.contains(configManagerBean.getCategory())) {
            configManagerBean.setCategory(PwmSetting.Category.GENERAL);
        }

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
            storedConfig.resetSetting(setting);
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

        configManagerBean.setEditorMode(false);
        LOGGER.debug(pwmSession, "save configuration operation completed");
    }

    private void doCancelEditing(
            final HttpServletRequest req
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();

        configManagerBean.setConfigurationLoadTime(null);
        configManagerBean.setEditorMode(false);
        LOGGER.debug(pwmSession, "cancelled edit actions");
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
        } catch (PwmUnrecoverableException e) {
            final ErrorInformation errorInfo = e.getErrorInformation();
            pwmSession.getSessionStateBean().setSessionError(errorInfo);
        }
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
                contextManager.reinitialize();
            }
        } catch (Exception e) {
            final String errorString = "error saving file: " + e.getMessage();
            LOGGER.error(pwmSession, errorString);
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, errorString, errorString));
        }

        configManagerBean.setConfigurationLoadTime(null);
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

    static void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        final ServletContext servletContext = req.getSession().getServletContext();
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();

        if (configManagerBean.isEditorMode()) {
            servletContext.getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER_EDITOR).forward(req, resp);
        } else {
            final Configuration config = PwmSession.getPwmSession(req).getConfig();
            final ConfigurationReader.MODE configMode = PwmSession.getPwmSession(req).getContextManager().getConfigReader().getConfigMode();
            if (config == null || configMode == ConfigurationReader.MODE.NEW) {
                servletContext.getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER_MODE_NEW).forward(req, resp);
            } else if (configMode == ConfigurationReader.MODE.CONFIGURATION) {
                servletContext.getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER_MODE_CONFIGURATION).forward(req, resp);
            } else {
                servletContext.getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER_MODE_RUNNING).forward(req, resp);
            }
        }
    }
}
