/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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
import password.pwm.bean.servlet.ConfigManagerBean;
import password.pwm.config.ConfigurationReader;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredConfiguration;
import password.pwm.config.StoredValue;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.ValueFactory;
import password.pwm.config.value.X509CertificateValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.X509Utils;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.*;

public class ConfigManagerServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ConfigManagerServlet.class);
    private static final String CONFIGMANAGER_INTRUDER_USERNAME = "ConfigurationManagerLogin";
    public static final String DEFAULT_PW = "DEFAULT-PW";

// -------------------------- OTHER METHODS --------------------------

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();

        initialize(pwmSession, ContextManager.getContextManager(req.getSession().getServletContext()).getConfigReader(), configManagerBean);
        final String processActionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);

        if ("getEpoch".equalsIgnoreCase(processActionParam)) {
            restGetEpoch(req, resp);
            return;
        }

        final String requestURI = req.getRequestURI();
        if (PwmApplication.MODE.RUNNING == pwmApplication.getApplicationMode() && !requestURI.startsWith(req.getContextPath() + "/private/admin/ConfigManager")) {
            resp.sendRedirect(req.getContextPath() + "/private/admin/ConfigManager");
            return;
        }

        if (processActionParam != null && processActionParam.length() > 0) {
            Validator.validatePwmFormID(req);
        }

        if ("startEditing".equalsIgnoreCase(processActionParam)) {
            doStartEditing(pwmApplication, pwmSession, configManagerBean, req, resp);
            return;
        } else if ("lockConfiguration".equalsIgnoreCase(processActionParam)) {
            restLockConfiguration(req, resp);
            return;
        }

        if (!configManagerBean.isPasswordRequired() || configManagerBean.isPasswordVerified()) {
            if ("readSetting".equalsIgnoreCase(processActionParam)) {
                this.restReadSetting(req, resp);
                return;
            } else if ("writeSetting".equalsIgnoreCase(processActionParam)) {
                this.restWriteSetting(req, resp);
                return;
            } else if ("resetSetting".equalsIgnoreCase(processActionParam)) {
                this.resetSetting(req);
                return;
            } else if ("generateXml".equalsIgnoreCase(processActionParam)) {
                if (doGenerateXml(req, resp)) {
                    return;
                }
            } else if ("finishEditing".equalsIgnoreCase(processActionParam)) {
                restFinishEditing(req, resp);
                return;
            } else if ("setConfigurationPassword".equalsIgnoreCase(processActionParam)) {
                doSetConfigurationPassword(req);
                return;
            } else if ("cancelEditing".equalsIgnoreCase(processActionParam)) {
                doCancelEditing(req);
            } else if ("editMode".equalsIgnoreCase(processActionParam)) {
                final EDIT_MODE mode = EDIT_MODE.valueOf(Validator.readStringFromRequest(req,"mode"));
                configManagerBean.setEditMode(mode);
                LOGGER.debug(pwmSession, "switching to editMode " + mode);
            } else if ("setOption".equalsIgnoreCase(processActionParam)) {
                setOptions(req);
            } else if ("manageLdapCerts".equalsIgnoreCase(processActionParam)) {
                restManageLdapCerts(req, resp, pwmApplication, pwmSession);
                return;
            } else if ("uploadConfig".equalsIgnoreCase(processActionParam)) {
                ConfigGuideServlet.restUploadConfig(req, resp, pwmApplication, pwmSession);
                return;
            }
        }

        forwardToJSP(req, resp);
    }

    private static void initialize(final PwmSession pwmSession,
                                   final ConfigurationReader configReader,
                                   final ConfigManagerBean configManagerBean
    )
            throws PwmUnrecoverableException
    {
        pwmSession.getSessionStateBean().setLocale(PwmConstants.DEFAULT_LOCALE);
        final Date configurationLoadTime = configReader.getConfigurationReadTime();
        if (configReader.getConfigMode() != PwmApplication.MODE.RUNNING) {
            if (configurationLoadTime != configManagerBean.getConfigurationLoadTime()) {
                LOGGER.debug(pwmSession, "initializing configuration bean with configMode=" + configReader.getConfigMode());
                configManagerBean.setConfigurationLoadTime(configurationLoadTime);
                configManagerBean.setConfiguration(null);
            }
        }

        // first time setup
        if (configManagerBean.getConfiguration() == null) {
            configManagerBean.setEditMode(EDIT_MODE.NONE);
            switch (configReader.getConfigMode()) {
                case NEW:
                    if (configManagerBean.getConfiguration() == null) {
                        configManagerBean.setConfiguration(StoredConfiguration.getDefaultConfiguration());
                    }
                    configManagerBean.getConfiguration().writeProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_IS_EDITABLE, "true");
                    configManagerBean.setPasswordVerified(true);
                    break;

                case CONFIGURATION:
                case RUNNING:
                    try {
                        final StoredConfiguration runningConfig = configReader.getStoredConfiguration();
                        final StoredConfiguration clonedConfiguration = (StoredConfiguration) runningConfig.clone();
                        configManagerBean.setConfiguration(clonedConfiguration);
                    } catch (CloneNotSupportedException e) {
                        throw new RuntimeException("unexpected error cloning StoredConfiguration", e);
                    }
                    break;
            }
        }

        if (configReader.getConfigMode() == PwmApplication.MODE.RUNNING) {
            if (configManagerBean.getEditMode() == EDIT_MODE.NONE) {
                configManagerBean.setEditMode(EDIT_MODE.SETTINGS);
            }
        }

        if (configReader.getStoredConfiguration().hasPassword() || PwmApplication.MODE.RUNNING == configReader.getConfigMode()) {
            configManagerBean.setPasswordRequired(true);
        }
    }

    private void restGetEpoch(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();
        final StoredConfiguration storedConfig = configManagerBean.getConfiguration();
        final String configEpoch = storedConfig.readProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_EPOCH);
        final HashMap<String, Object> dataMap = new LinkedHashMap<String, Object>();
        final RestResultBean restResultBean = new RestResultBean();

        if (configEpoch != null && configEpoch.length() > 0) {
            dataMap.put("currentEpoch", configEpoch);
        }

        restResultBean.setData(dataMap);
        ServletHelper.outputJsonResult(resp,restResultBean);
    }

    private void restManageLdapCerts(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException, IOException {
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();
        final String certAction = Validator.readStringFromRequest(req,"certAction");
        if ("autoImport".equalsIgnoreCase(certAction)) {
            final StringArrayValue ldapUrlsValue = (StringArrayValue)configManagerBean.getConfiguration().readSetting(PwmSetting.LDAP_SERVER_URLS);
            final Set<X509Certificate> resultCertificates = new LinkedHashSet<X509Certificate>();
            try {
                if (ldapUrlsValue != null && ldapUrlsValue.toNativeObject() != null) {
                    final List<String> ldapUrlStrings = ldapUrlsValue.toNativeObject();
                    for (final String ldapUrlString : ldapUrlStrings) {
                        final URI ldapURI = new URI(ldapUrlString);
                        final X509Certificate[] certs = X509Utils.readLdapServerCerts(ldapURI);
                        if (certs != null) {
                            resultCertificates.addAll(Arrays.asList(certs));
                        }
                    }
                }
            } catch (Exception e) {
                ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,"error importing certificates: " + e.getMessage());
                if (e instanceof PwmException) {
                    errorInformation = ((PwmException) e).getErrorInformation();
                }
                final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInformation, pwmApplication, pwmSession);
                ServletHelper.outputJsonResult(resp, restResultBean);
                return;
            }
            configManagerBean.getConfiguration().writeSetting(PwmSetting.LDAP_SERVER_CERTS,new X509CertificateValue(resultCertificates));
            ServletHelper.outputJsonResult(resp, new RestResultBean());
            return;
        } else if ("clear".equalsIgnoreCase(certAction)) {
            configManagerBean.getConfiguration().writeSetting(PwmSetting.LDAP_SERVER_CERTS,new X509CertificateValue(Collections.<X509Certificate>emptyList()));
            ServletHelper.outputJsonResult(resp, new RestResultBean());
            return;
        }
        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,"invalid certAction parameter");
        final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInformation, pwmApplication, pwmSession);
        ServletHelper.outputJsonResult(resp, restResultBean);
    }

    private void doStartEditing(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final ConfigManagerBean configManagerBean,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, PwmUnrecoverableException, ServletException {
        final ConfigurationReader runningConfigReader = ContextManager.getContextManager(req.getSession()).getConfigReader();
        final StoredConfiguration storedConfig = runningConfigReader.getStoredConfiguration();

        if (configManagerBean.isPasswordRequired()) {
            if (!storedConfig.hasPassword()) {
                final String errorMsg = "config file does not have a configuration password";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg,new String[]{errorMsg});
                pwmSession.getSessionStateBean().setSessionError(errorInformation);
                return;
            }

            pwmApplication.getIntruderManager().check(CONFIGMANAGER_INTRUDER_USERNAME,null,null);

            if (!configManagerBean.isPasswordVerified()) {
                final String password = Validator.readStringFromRequest(req,"password");
                if (password != null && password.length() > 0) {
                    final boolean passed = storedConfig.verifyPassword(password);
                    configManagerBean.setPasswordVerified(passed);
                    if (!passed) {
                        pwmApplication.getIntruderManager().mark(CONFIGMANAGER_INTRUDER_USERNAME,null,null);
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_WRONGPASSWORD,"incorrect password");
                        Helper.pause(5000);
                        pwmSession.getSessionStateBean().setSessionError(errorInformation);
                    }
                }
            }
        }

        if (!configManagerBean.isPasswordRequired() || configManagerBean.isPasswordVerified()) {
            configManagerBean.setEditMode(EDIT_MODE.SETTINGS);
            forwardToJSP(req,resp);
        } else {
            final ServletContext servletContext = req.getSession().getServletContext();
            servletContext.getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER_LOGIN).forward(req, resp);
        }
    }

    private void restReadSetting(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, PwmUnrecoverableException {
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();
        final StoredConfiguration storedConfig = configManagerBean.getConfiguration();

        final String key = Validator.readStringFromRequest(req, "key");
        final Object returnValue;
        final Map<String, Object> returnMap = new LinkedHashMap<String, Object>();
        final PwmSetting theSetting = PwmSetting.forKey(key);

        if (key.startsWith("localeBundle")) {
            final StringTokenizer st = new StringTokenizer(key,"-");
            st.nextToken();
            final PwmConstants.EDITABLE_LOCALE_BUNDLES bundleName = PwmConstants.EDITABLE_LOCALE_BUNDLES.valueOf(st.nextToken());
            final String keyName = st.nextToken();
            final Map<String,String> bundleMap = storedConfig.readLocaleBundleMap(bundleName.getTheClass().getName(),keyName);
            if (bundleMap == null || bundleMap.isEmpty()) {
                final Map<String,String> defaultValueMap = new LinkedHashMap<String, String>();
                for (final Locale locale : ContextManager.getPwmApplication(req).getConfig().getKnownLocales()) {
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
            LOGGER.warn("readSettingAsString request for unknown key: " + key);
            returnMap.put("key", key);
            returnMap.put("isDefault", false);
            returnValue = "UNKNOWN KEY";
        } else {
            switch (theSetting.getSyntax()) {
                case PASSWORD:
                    returnValue = DEFAULT_PW;
                    break;
                case SELECT:
                    returnValue = storedConfig.readSetting(theSetting).toNativeObject();
                    returnMap.put("options",theSetting.getOptions());
                    break;

                default:
                    returnValue = storedConfig.readSetting(theSetting).toNativeObject();
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

    private void restWriteSetting(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, PwmUnrecoverableException {
        Validator.validatePwmFormID(req);
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();
        final StoredConfiguration storedConfig = configManagerBean.getConfiguration();
        final String key = Validator.readStringFromRequest(req, "key");
        final String bodyString = ServletHelper.readRequestBody(req);
        final Gson gson = new Gson();
        final PwmSetting setting = PwmSetting.forKey(key);
        final Map<String, Object> returnMap = new LinkedHashMap<String, Object>();

        if (key.startsWith("localeBundle")) {
            final StringTokenizer st = new StringTokenizer(key,"-");
            st.nextToken();
            final PwmConstants.EDITABLE_LOCALE_BUNDLES bundleName = PwmConstants.EDITABLE_LOCALE_BUNDLES.valueOf(st.nextToken());
            final String keyName = st.nextToken();
            final Map<String, String> valueMap = gson.fromJson(bodyString, new TypeToken<Map<String, String>>() {
            }.getType());
            final Map<String, String> outputMap = new LinkedHashMap<String, String>(valueMap);

            storedConfig.writeLocaleBundleMap(bundleName.getTheClass().getName(),keyName, outputMap);
            returnMap.put("isDefault", outputMap.isEmpty());
            returnMap.put("key", key);
        } else {
            try {
                final StoredValue storedValue = ValueFactory.fromJson(setting, bodyString);
                storedConfig.writeSetting(setting,storedValue);
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

        final String bodyString = ServletHelper.readRequestBody(req);

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
            PwmSession.getPwmSession(req).getSessionStateBean().setSessionError(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, errorString));
            return false;
        }

        final String output = configuration.toXml();
        resp.setHeader("content-disposition", "attachment;filename=" + PwmConstants.CONFIG_FILE_FILENAME);
        resp.setContentType("text/xml;charset=utf-8");
        resp.getWriter().print(output);
        return true;
    }

    private void restLockConfiguration(
            final HttpServletRequest req,
            final HttpServletResponse resp

    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();
        final String currentEpoch = configManagerBean.getConfiguration().readProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_EPOCH);

        final StoredConfiguration storedConfiguration = configManagerBean.getConfiguration();
        if (!storedConfiguration.hasPassword()) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"Please set a configuration password before locking the configuration");
            final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInfo, pwmApplication, pwmSession);
            LOGGER.debug(pwmSession, errorInfo);
            ServletHelper.outputJsonResult(resp, restResultBean);
            return;
        }

        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_AUTHENTICATION_REQUIRED,"You must be authenticated before locking the configuration");
            final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInfo, pwmApplication, pwmSession);
            LOGGER.debug(pwmSession, errorInfo);
            ServletHelper.outputJsonResult(resp, restResultBean);
            return;
        }

        if (!Permission.checkPermission(Permission.PWMADMIN,pwmSession,pwmApplication)) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"You must be authenticated with admin privileges before locking the configuration");
            final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInfo, pwmApplication, pwmSession);
            LOGGER.debug(pwmSession, errorInfo);
            ServletHelper.outputJsonResult(resp, restResultBean);
            return;
        }

        storedConfiguration.writeProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_IS_EDITABLE, "false");
        try {
            saveConfiguration(pwmSession, req.getSession().getServletContext());
            configManagerBean.setConfiguration(null);
        } catch (PwmUnrecoverableException e) {
            final ErrorInformation errorInfo = e.getErrorInformation();
            final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInfo, pwmApplication, pwmSession);
            LOGGER.debug(pwmSession, errorInfo);
            ServletHelper.outputJsonResult(resp, restResultBean);
            return;
        }
        final HashMap<String,String> resultData = new HashMap<String,String>();
        resultData.put("currentEpoch", currentEpoch);
        LOGGER.info(pwmSession, "Configuration Locked");
        ServletHelper.outputJsonResult(resp, new RestResultBean(resultData));
    }

    private void doSetConfigurationPassword(
            final HttpServletRequest req

    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();

        final String password = ServletHelper.readRequestBody(req);
        configManagerBean.getConfiguration().setPassword(password);
        configManagerBean.setPasswordVerified(true);
    }

    private void restFinishEditing(
            final HttpServletRequest req,
            final HttpServletResponse resp

    )
            throws IOException, ServletException, PwmUnrecoverableException {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();
        RestResultBean restResultBean = new RestResultBean();
        final HashMap<String,String> resultData = new HashMap<String,String>();
        resultData.put("currentEpoch", configManagerBean.getConfiguration().readProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_EPOCH));
        restResultBean.setData(resultData);

        if (!configManagerBean.getConfiguration().validateValues().isEmpty()) {
            final String errorString = configManagerBean.getConfiguration().validateValues().get(0);
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorString);
            restResultBean = RestResultBean.fromErrorInformation(errorInfo, pwmApplication, pwmSession);
        } else {
            try {
                saveConfiguration(pwmSession, req.getSession().getServletContext());
                restResultBean.setError(false);
            } catch (PwmUnrecoverableException e) {
                final ErrorInformation errorInfo = e.getErrorInformation();
                pwmSession.getSessionStateBean().setSessionError(errorInfo);
                restResultBean = RestResultBean.fromErrorInformation(errorInfo, pwmApplication, pwmSession);
                LOGGER.warn(pwmSession, "unable to save configuration: " + e.getMessage());
            }
        }

        resetInMemoryBean(pwmSession,req.getSession().getServletContext());
        LOGGER.debug(pwmSession, "save configuration operation completed");
        ServletHelper.outputJsonResult(resp, restResultBean);
    }

    static void saveConfiguration(
            final PwmSession pwmSession,
            final ServletContext servletContext
    )
            throws PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();
        final StoredConfiguration storedConfiguration = configManagerBean.getConfiguration();

        {
            final List<String> errorStrings = storedConfiguration.validateValues();
            if (errorStrings != null && !errorStrings.isEmpty()) {
                final String errorString = errorStrings.get(0);
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, errorString));
            }
        }

        try {
            ContextManager.getContextManager(servletContext).getConfigReader().saveConfiguration(storedConfiguration);
            ContextManager.getContextManager(servletContext).reinitialize();
            Helper.pause(5000);
        } catch (Exception e) {
            final String errorString = "error saving file: " + e.getMessage();
            LOGGER.error(pwmSession, errorString);
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, errorString));
        }

        resetInMemoryBean(pwmSession, servletContext);
    }

    private void doCancelEditing(
            final HttpServletRequest req
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        resetInMemoryBean(pwmSession,req.getSession().getServletContext());
        LOGGER.debug(pwmSession, "cancelled edit actions");
    }

    private void setOptions(
            final HttpServletRequest req
    ) throws IOException, PwmUnrecoverableException {
        Validator.validatePwmFormID(req);
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();

        {
            final String requestedLevelstr = Validator.readStringFromRequest(req, "level");
            if (requestedLevelstr != null && requestedLevelstr.length() > 0) {
                try {
                    configManagerBean.setLevel(Integer.valueOf(requestedLevelstr));
                    LOGGER.trace("setting level to: " + configManagerBean.getLevel());
                } catch (Exception e) {
                    LOGGER.error("unknown level set request: " + requestedLevelstr);
                }
            }
        }
        {
            final String requestedShowDesc = Validator.readStringFromRequest(req, "showDesc");
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
            final String requestedTemplate = Validator.readStringFromRequest(req, "template");
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
            final String requestedCategory = Validator.readStringFromRequest(req, "category");
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
            final String requestedLocaleBundle = Validator.readStringFromRequest(req, "localeBundle");
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
            final String updateDescriptionTextCmd = Validator.readStringFromRequest(req, "updateNotesText");
            if (updateDescriptionTextCmd != null && updateDescriptionTextCmd.equalsIgnoreCase("true")) {
                try {
                    final Gson gson = new Gson();
                    final String bodyString = ServletHelper.readRequestBody(req);
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


        if (configManagerBean.getEditMode() == EDIT_MODE.NONE) {
            servletContext.getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER_MODE_CONFIGURATION).forward(req, resp);
            return;
        }

        if (configManagerBean.isPasswordRequired() && !configManagerBean.isPasswordVerified()) {
            servletContext.getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER_LOGIN).forward(req, resp);
            return;
        }

        servletContext.getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER_EDITOR).forward(req, resp);
    }

    private static void resetInMemoryBean(final PwmSession pwmSession, final ServletContext servletContext)
            throws PwmUnrecoverableException
    {
        pwmSession.clearUserBean(ConfigManagerBean.class);
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();
        initialize(pwmSession, ContextManager.getContextManager(servletContext).getConfigReader(), configManagerBean);
        pwmSession.getConfigManagerBean().setEditMode(EDIT_MODE.NONE);
    }
// -------------------------- ENUMERATIONS --------------------------

    public static enum EDIT_MODE {
        SETTINGS,
        LOCALEBUNDLE,
        NONE
    }
}
