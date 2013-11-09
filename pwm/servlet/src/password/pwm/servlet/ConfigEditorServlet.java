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
import password.pwm.bean.ConfigEditorCookie;
import password.pwm.bean.servlet.ConfigManagerBean;
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

public class ConfigEditorServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ConfigEditorServlet.class);
    public static final String DEFAULT_PW = "DEFAULT-PW";

    public static ConfigEditorCookie readConfigEditorCookie(
            final HttpServletRequest request,
            final HttpServletResponse response
    ) {
        ConfigEditorCookie cookie = null;
        try {
            final String jsonString = ServletHelper.readCookie(request, "preferences");
            cookie = Helper.getGson().fromJson(jsonString, ConfigEditorCookie.class);
        } catch (Exception e) {
            LOGGER.warn("error parsing cookie preferences: " + e.getMessage());
        }
        if (cookie == null) {
            cookie = new ConfigEditorCookie();
            final String jsonString = Helper.getGson().toJson(cookie);
            ServletHelper.writeCookie(response, "preferences", jsonString);
        }

        return cookie;
    }


// -------------------------- OTHER METHODS --------------------------

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();

        final String processActionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);

        if (configManagerBean.getConfiguration() == null) {
            forwardToManager(req,resp);
            return;
        }

        if (processActionParam != null && processActionParam.length() > 0) {
            Validator.validatePwmFormID(req);
        }

        if ("readSetting".equalsIgnoreCase(processActionParam)) {
            this.restReadSetting(req, resp);
            return;
        } else if ("writeSetting".equalsIgnoreCase(processActionParam)) {
            this.restWriteSetting(req, resp);
            return;
        } else if ("resetSetting".equalsIgnoreCase(processActionParam)) {
            this.resetSetting(req);
            return;
        } else if ("finishEditing".equalsIgnoreCase(processActionParam)) {
            restFinishEditing(req, resp);
            return;
        } else if ("setConfigurationPassword".equalsIgnoreCase(processActionParam)) {
            doSetConfigurationPassword(req);
            return;
        } else if ("cancelEditing".equalsIgnoreCase(processActionParam)) {
            doCancelEditing(req,resp,configManagerBean);
        } else if ("setOption".equalsIgnoreCase(processActionParam)) {
            setOptions(req);
        } else if ("manageLdapCerts".equalsIgnoreCase(processActionParam)) {
            restManageLdapCerts(req, resp, pwmApplication, pwmSession);
            return;
        }

        forwardToJSP(req, resp);
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
                final RestResultBean restResultBean = RestResultBean.fromError(errorInformation, pwmSession.getSessionStateBean().getLocale(), pwmApplication.getConfig());
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
        final RestResultBean restResultBean = RestResultBean.fromError(errorInformation, pwmSession.getSessionStateBean().getLocale(), pwmApplication.getConfig());
        ServletHelper.outputJsonResult(resp, restResultBean);
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
        final Gson gson = Helper.getGson();
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
        final Gson gson = Helper.getGson();
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
    )
            throws IOException, PwmUnrecoverableException
    {
        Validator.validatePwmFormID(req);
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();
        final StoredConfiguration storedConfig = configManagerBean.getConfiguration();

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
                storedConfig.resetSetting(setting);
            }
        }
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
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();
        RestResultBean restResultBean = new RestResultBean();
        final HashMap<String,String> resultData = new HashMap<String,String>();
        resultData.put("currentEpoch", configManagerBean.getConfiguration().readConfigProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_EPOCH));
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
        Validator.validatePwmFormID(req);
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();
        {
            final String updateDescriptionTextCmd = Validator.readStringFromRequest(req, "updateNotesText");
            if (updateDescriptionTextCmd != null && updateDescriptionTextCmd.equalsIgnoreCase("true")) {
                try {
                    final Gson gson = Helper.getGson();
                    final String bodyString = ServletHelper.readRequestBody(req);
                    final String value = gson.fromJson(bodyString, new TypeToken<String>() {
                    }.getType());
                    configManagerBean.getConfiguration().writeProperty(StoredConfiguration.PROPERTY_KEY_NOTES, value);
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
                        configManagerBean.getConfiguration().writeProperty(StoredConfiguration.PROPERTY_KEY_TEMPLATE, template.toString());
                        LOGGER.trace("setting template to: " + requestedTemplate);
                    } catch (IllegalArgumentException e) {
                        configManagerBean.getConfiguration().writeProperty(StoredConfiguration.PROPERTY_KEY_TEMPLATE,PwmSetting.Template.DEFAULT.toString());
                        LOGGER.error("unknown template set request: " + requestedTemplate);
                    }
                }
            }
        }
    }

    static void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final ServletContext servletContext = req.getSession().getServletContext();
        servletContext.getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER_EDITOR).forward(req, resp);
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


// -------------------------- ENUMERATIONS --------------------------


}
