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
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import password.pwm.*;
import password.pwm.bean.servlet.ConfigGuideBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.*;
import password.pwm.config.value.*;
import password.pwm.error.*;
import password.pwm.health.HealthMonitor;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.health.LDAPStatusChecker;
import password.pwm.i18n.Admin;
import password.pwm.i18n.Display;
import password.pwm.i18n.LocaleHelper;
import password.pwm.util.PwmLogger;
import password.pwm.util.PwmRandom;
import password.pwm.util.ServletHelper;
import password.pwm.util.X509Utils;
import password.pwm.util.operations.UserSearchEngine;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.RestHealthServer;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.*;

public class ConfigGuideServlet extends TopServlet {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ConfigGuideServlet.class.getName());

    public static final String PARAM_LDAP_HOST = "ldap-server-ip";
    public static final String PARAM_LDAP_PORT = "ldap-server-port";
    public static final String PARAM_LDAP_SECURE = "ldap-server-secure";
    public static final String PARAM_LDAP_ADMIN_DN = "ldap-user-dn";
    public static final String PARAM_LDAP_ADMIN_PW = "ldap-user-pw";

    public static final String PARAM_LDAP2_CONTEXT = "ldap2-context";
    public static final String PARAM_LDAP2_TEST_USER = "ldap2-testUser";
    public static final String PARAM_LDAP2_ADMINS = "ldap2-adminsQuery";

    public static final String PARAM_CONFIG_PASSWORD = "config-password";
    public static final String PARAM_CONFIG_PASSWORD_VERIFY = "config-password-verify";

    public static Map<String,String> defaultForm(PwmSetting.Template template) {
        final Map<String,String> defaultLdapForm = new HashMap<String,String>();

        try {
            final String defaultLdapUrlString = ((List<String>)PwmSetting.LDAP_SERVER_URLS.getDefaultValue(template).toNativeObject()).get(0);
            final URI uri = new URI(defaultLdapUrlString);

            defaultLdapForm.put(PARAM_LDAP_HOST, uri.getHost());
            defaultLdapForm.put(PARAM_LDAP_PORT, String.valueOf(uri.getPort()));
            defaultLdapForm.put(PARAM_LDAP_SECURE, "ldaps".equalsIgnoreCase(uri.getScheme()) ? "true" : "false");

            defaultLdapForm.put(PARAM_LDAP_ADMIN_DN, (String)PwmSetting.LDAP_PROXY_USER_DN.getDefaultValue(template).toNativeObject());
            defaultLdapForm.put(PARAM_LDAP_ADMIN_PW, (String)PwmSetting.LDAP_PROXY_USER_PASSWORD.getDefaultValue(template).toNativeObject());

            defaultLdapForm.put(PARAM_LDAP2_CONTEXT, ((List<String>)PwmSetting.LDAP_CONTEXTLESS_ROOT.getDefaultValue(template).toNativeObject()).get(0));
            defaultLdapForm.put(PARAM_LDAP2_TEST_USER, (String)PwmSetting.LDAP_TEST_USER_DN.getDefaultValue(template).toNativeObject());
            defaultLdapForm.put(PARAM_LDAP2_ADMINS, (String) PwmSetting.QUERY_MATCH_PWM_ADMIN.getDefaultValue(template).toNativeObject());

            defaultLdapForm.put(PARAM_CONFIG_PASSWORD, "");
            defaultLdapForm.put(PARAM_CONFIG_PASSWORD_VERIFY, "");
        } catch (Exception e) {
            LOGGER.error("error building static form values using default configuration: " + e.getMessage());
            e.printStackTrace();
        }

        return Collections.unmodifiableMap(defaultLdapForm);
    }

    @Override
    protected void processRequest(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final String actionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);
        final ConfigGuideBean configGuideBean = (ConfigGuideBean)PwmSession.getPwmSession(req).getSessionBean(ConfigGuideBean.class);

        if (pwmApplication.getApplicationMode() != PwmApplication.MODE.NEW) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"ConfigGuide unavailable unless in NEW mode");
            ssBean.setSessionError(errorInformation);
            LOGGER.error(pwmSession,errorInformation.toDebugStr());
            return;
        }

        req.getSession().setMaxInactiveInterval(15 * 60);

        if (configGuideBean.getStep() == STEP.LDAPCERT) {
            final String ldapServerString = ((List<String>) configGuideBean.getStoredConfiguration().readSetting(PwmSetting.LDAP_SERVER_URLS).toNativeObject()).get(0);
            try {
                final URI ldapServerUri = new URI(ldapServerString);
                if ("ldaps".equalsIgnoreCase(ldapServerUri.getScheme())) {
                    configGuideBean.setLdapCertificates(X509Utils.readLdapServerCerts(ldapServerUri));
                    configGuideBean.setCertsTrustedbyKeystore(X509Utils.testIfLdapServerCertsInDefaultKeystore(ldapServerUri));
                } else {
                    configGuideBean.setLdapCertificates(null);
                    configGuideBean.setCertsTrustedbyKeystore(false);
                }
            } catch (Exception e) {
                LOGGER.error("error reading/testing ldap server certificates: " + e.getMessage());
            }
        }

        if (actionParam != null && actionParam.length() > 0) {
            Validator.validatePwmFormID(req);
            if (actionParam.equalsIgnoreCase("selectTemplate")) {
                restSelectTemplate(req, resp, pwmApplication, pwmSession);
                return;
            } else if (actionParam.equalsIgnoreCase("ldapHealth")) {
                restLdapHealth(resp, pwmSession);
                return;
            } else if (actionParam.equalsIgnoreCase("updateForm")) {
                restUpdateLdapForm(req, resp, pwmSession);
                return;
            } else if (actionParam.equalsIgnoreCase("gotoStep")) {
                restGotoStep(req, resp, pwmApplication, pwmSession);
                return;
            } else if (actionParam.equalsIgnoreCase("useConfiguredCerts")) {
                restUseConfiguredCerts(req, resp);
                return;
            } else if (actionParam.equalsIgnoreCase("uploadConfig")) {
                restUploadConfig(req, resp, pwmApplication, pwmSession);
                return;
            }
        }

        forwardToJSP(req,resp);
    }



    public static void restUploadConfig(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        if (ServletFileUpload.isMultipartContent(req)) {
            final String uploadedFile = ServletHelper.readFileUpload(req,"uploadFile",PwmConstants.MAX_CONFIG_FILE_CHARS);
            if (uploadedFile != null && uploadedFile.length() > 0) {
                try {
                    final StoredConfiguration storedConfig = StoredConfiguration.fromXml(uploadedFile);
                    writeConfig(ContextManager.getContextManager(req.getSession()),storedConfig);
                    LOGGER.trace(pwmSession, "read config from file: " + storedConfig.toString());
                    final RestResultBean restResultBean = new RestResultBean();
                    restResultBean.setSuccessMessage("read message");
                    ServletHelper.outputJsonResult(resp, restResultBean);
                    req.getSession().invalidate();
                } catch (PwmException e) {
                    final RestResultBean restResultBean = RestResultBean.fromErrorInformation(e.getErrorInformation(),pwmApplication,pwmSession);
                    ServletHelper.outputJsonResult(resp, restResultBean);
                    LOGGER.error(pwmSession, e.getErrorInformation().toDebugStr());
                }
            } else {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_UPLOAD_FAILURE, "error reading config file: no file present in upload");
                final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInformation,pwmApplication,pwmSession);
                ServletHelper.outputJsonResult(resp, restResultBean);
                LOGGER.error(pwmSession, errorInformation.toDebugStr());
            }
        }
    }


    private void restUseConfiguredCerts(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws PwmUnrecoverableException, IOException
    {
        final boolean value = Validator.readBooleanFromRequest(req, "value");
        final ConfigGuideBean configGuideBean = (ConfigGuideBean)PwmSession.getPwmSession(req).getSessionBean(ConfigGuideBean.class);
        configGuideBean.setUseConfiguredCerts(value);
        final StoredValue newStoredValue = value ?
                new X509CertificateValue(configGuideBean.getLdapCertificates()) :
                new X509CertificateValue(new X509Certificate[0]);
        configGuideBean.getStoredConfiguration().writeSetting(
                PwmSetting.LDAP_SERVER_CERTS,
                newStoredValue
        );
        ServletHelper.outputJsonResult(resp,new RestResultBean());
    }

    private void restSelectTemplate(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException, IOException
    {
        final String requestedTemplate = Validator.readStringFromRequest(req, "template");
        if ("NOTSELECTED".equals(requestedTemplate)) {
            return;
        }
        PwmSetting.Template template;
        if (requestedTemplate == null || requestedTemplate.length() <= 0) {
            final String errorMsg = "missing template value in template set request";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg);
            final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInformation, pwmApplication, pwmSession);
            LOGGER.error(pwmSession,errorInformation.toDebugStr());
            ServletHelper.outputJsonResult(resp,restResultBean);
            return;
        }

        try {
            template = PwmSetting.Template.valueOf(requestedTemplate);
        } catch (IllegalArgumentException e) {
            final String errorMsg = "unknown template set request: " + requestedTemplate;
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg);
            final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInformation, pwmApplication, pwmSession);
            LOGGER.error(pwmSession,errorInformation.toDebugStr());
            ServletHelper.outputJsonResult(resp,restResultBean);
            return;
        }

        final ConfigGuideBean configGuideBean = (ConfigGuideBean)PwmSession.getPwmSession(req).getSessionBean(ConfigGuideBean.class);
        final StoredConfiguration newStoredConfig = configGuideBean.getStoredConfiguration();
        LOGGER.trace("setting template to: " + requestedTemplate);
        newStoredConfig.writeProperty(StoredConfiguration.PROPERTY_KEY_TEMPLATE, template.toString());

        newStoredConfig.writeSetting(PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE,new StringValue(""));
        newStoredConfig.writeSetting(PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE,new StringValue(""));

        configGuideBean.setFormData(new HashMap<String,String>(defaultForm(template)));
        updateLdapInfo(newStoredConfig, new HashMap<String,String>(defaultForm(template)),Collections.<String,String>emptyMap());
        ServletHelper.outputJsonResult(resp,new RestResultBean());
    }

    private void restLdapHealth(
            final HttpServletResponse resp,
            final PwmSession pwmSession
    )
            throws IOException
    {
        final ConfigGuideBean configGuideBean = (ConfigGuideBean)pwmSession.getSessionBean(ConfigGuideBean.class);
        final Configuration tempConfiguration = new Configuration(configGuideBean.getStoredConfiguration());
        final PwmApplication tempApplication = new PwmApplication(tempConfiguration, PwmApplication.MODE.NEW, null);
        final LDAPStatusChecker ldapStatusChecker = new LDAPStatusChecker();
        final List<HealthRecord> records = new ArrayList<HealthRecord>();
        switch (configGuideBean.getStep()) {
            case LDAP:
                records.addAll(ldapStatusChecker.checkBasicLdapConnectivity(tempApplication,tempConfiguration,false));
                if (records.isEmpty()) {
                    records.add(new HealthRecord(
                            HealthStatus.GOOD,
                            "LDAP",
                            LocaleHelper.getLocalizedMessage("Health_LDAP_OK",tempConfiguration,Admin.class)
                    ));
                }
                break;

            case LDAP2:
                records.addAll(ldapStatusChecker.checkBasicLdapConnectivity(tempApplication, tempConfiguration, true));
                if (records.isEmpty()) {
                    records.add(new HealthRecord(
                            HealthStatus.GOOD,
                            "LDAP",
                            LocaleHelper.getLocalizedMessage("Health_LDAP_OK",tempConfiguration,Admin.class)
                    ));
                }
                if (configGuideBean.getFormData().containsKey(PARAM_LDAP2_ADMINS) && configGuideBean.getFormData().get(PARAM_LDAP2_ADMINS).length() > 0) {
                    final int maxSearchSize = 500;
                    final UserSearchEngine userSearchEngine = new UserSearchEngine(tempApplication);
                    final UserSearchEngine.SearchConfiguration searchConfig = new UserSearchEngine.SearchConfiguration();
                    searchConfig.setFilter(configGuideBean.getFormData().get(PARAM_LDAP2_ADMINS));
                    try {
                        final Map<ChaiUser,Map<String,String>> results = userSearchEngine.performMultiUserSearch(pwmSession, searchConfig, maxSearchSize, Collections.<String>emptyList());
                        if (results == null || results.isEmpty()) {
                            records.add(new HealthRecord(HealthStatus.WARN,"Admin Users","No admin users are defined with the current Administration Search Filter"));
                        } else {
                            final StringBuilder sb = new StringBuilder();
                            sb.append("<ul>");
                            for (final ChaiUser user : results.keySet()) {
                                sb.append("<li>");
                                sb.append(user.getEntryDN());
                                sb.append("</li>");
                            }
                            sb.append("</ul>");
                            if (results.size() == maxSearchSize) {
                                sb.append(LocaleHelper.getLocalizedMessage("Display_SearchResultsExceeded",tempConfiguration, Display.class));
                            }
                            records.add(new HealthRecord(HealthStatus.GOOD,"Admin Users","Users matching current Administration Search Filter: " + sb.toString()));
                        }
                    } catch (Exception e) {
                        final String errorMsg = "error while attempting to search for Admin users: " + e.getMessage();
                        LOGGER.warn(pwmSession,errorMsg);
                        records.add(new HealthRecord(HealthStatus.WARN,"Admin Users",errorMsg));
                    }
                }
                break;

            case LDAP3:
                records.addAll(ldapStatusChecker.checkBasicLdapConnectivity(tempApplication, tempConfiguration, false));
                records.addAll(ldapStatusChecker.doLdapTestUserCheck(tempConfiguration, tempApplication));
                break;
        }

        RestHealthServer.JsonOutput jsonOutput = new RestHealthServer.JsonOutput();
        jsonOutput.records = RestHealthServer.HealthRecordBean.fromHealthRecords(records,pwmSession.getSessionStateBean().getLocale(),tempConfiguration);
        jsonOutput.timestamp = new Date();
        jsonOutput.overall = HealthMonitor.getMostSevereHealthStatus(records).toString();
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(jsonOutput);
        ServletHelper.outputJsonResult(resp, restResultBean);
    }

    private void restUpdateLdapForm(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmSession pwmSession
    )
            throws IOException
    {
        final String bodyString = ServletHelper.readRequestBody(req);
        final ConfigGuideBean configGuideBean = (ConfigGuideBean)pwmSession.getSessionBean(ConfigGuideBean.class);
        final StoredConfiguration storedConfiguration = configGuideBean.getStoredConfiguration();
        final Map<String,String> incomingFormData = new Gson().fromJson(bodyString,new TypeToken<Map<String, String>>() {}.getType());        if (incomingFormData != null) {
        configGuideBean.getFormData().putAll(incomingFormData);
    }
        final RestResultBean restResultBean = new RestResultBean();
        ServletHelper.outputJsonResult(resp, restResultBean);
        updateLdapInfo(storedConfiguration, configGuideBean.getFormData(), incomingFormData);
        //LOGGER.info("config: " + storedConfiguration.toString());
    }

    private void restGotoStep(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException, IOException, ServletException {
        final String requestedStep = Validator.readStringFromRequest(req, "step");
        STEP step = null;
        if (requestedStep != null && requestedStep.length() > 0) {
            try {
                step = STEP.valueOf(requestedStep);
            } catch (IllegalArgumentException e) {
                final String errorMsg = "unknown goto step request: " + requestedStep;
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg);
                final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInformation, pwmApplication, pwmSession);
                LOGGER.error(pwmSession,errorInformation.toDebugStr());
                ServletHelper.outputJsonResult(resp,restResultBean);
                return;
            }
        }

        final ConfigGuideBean configGuideBean = (ConfigGuideBean)PwmSession.getPwmSession(req).getSessionBean(ConfigGuideBean.class);
        if (step == STEP.FINISH) {
            final ContextManager contextManager = ContextManager.getContextManager(req.getSession());
            try {
                writeConfig(contextManager, configGuideBean);
            } catch (PwmOperationalException e) {
                pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
                final RestResultBean restResultBean = RestResultBean.fromErrorInformation(e.getErrorInformation(), pwmApplication, pwmSession);
                ServletHelper.outputJsonResult(resp, restResultBean);
                return;
            }
            final HashMap<String,String> resultData = new HashMap<String,String>();
            resultData.put("serverRestart","true");
            ServletHelper.outputJsonResult(resp, new RestResultBean(resultData));
            pwmSession.invalidate();
        } else {
            configGuideBean.setStep(step);
            ServletHelper.outputJsonResult(resp, new RestResultBean());
            LOGGER.trace("setting current step to: " + step);
        }
    }

    public static void updateLdapInfo(
            final StoredConfiguration storedConfiguration,
            final Map<String,String> ldapForm,
            final Map<String,String> incomingLdapForm
    ) {
        {
            final String ldapServerIP = ldapForm.get(PARAM_LDAP_HOST);
            final String ldapServerPort = ldapForm.get(PARAM_LDAP_PORT);
            final boolean ldapServerSecure = "true".equalsIgnoreCase(ldapForm.get(PARAM_LDAP_SECURE));

            final String newLdapURI = "ldap" + (ldapServerSecure ? "s" : "") +  "://" + ldapServerIP + ":" + ldapServerPort;
            final StringArrayValue newValue = new StringArrayValue(Collections.singletonList(newLdapURI));
            storedConfiguration.writeSetting(PwmSetting.LDAP_SERVER_URLS, newValue);
        }

        { // proxy/admin account
            final String ldapAdminDN = ldapForm.get(PARAM_LDAP_ADMIN_DN);
            final String ldapAdminPW = ldapForm.get(PARAM_LDAP_ADMIN_PW);
            storedConfiguration.writeSetting(PwmSetting.LDAP_PROXY_USER_DN, new StringValue(ldapAdminDN));
            storedConfiguration.writeSetting(PwmSetting.LDAP_PROXY_USER_PASSWORD, new PasswordValue(ldapAdminPW));
        }

        // set context based on ldap dn
        if (incomingLdapForm.containsKey(PARAM_LDAP_ADMIN_DN)) {
            final String ldapAdminDN = ldapForm.get(PARAM_LDAP_ADMIN_DN);
            String contextDN = "";
            if (ldapAdminDN != null && ldapAdminDN.contains(",")) {
                contextDN = ldapAdminDN.substring(ldapAdminDN.indexOf(",") + 1,ldapAdminDN.length());
            }
            ldapForm.put(PARAM_LDAP2_CONTEXT, contextDN);
        }
        storedConfiguration.writeSetting(PwmSetting.LDAP_CONTEXTLESS_ROOT, new StringArrayValue(Collections.singletonList(ldapForm.get(PARAM_LDAP2_CONTEXT))));

        {  // set context based on ldap dn
            final String ldapContext = ldapForm.get(PARAM_LDAP2_CONTEXT);
            storedConfiguration.writeSetting(PwmSetting.LDAP_CONTEXTLESS_ROOT, new StringArrayValue(Collections.singletonList(ldapContext)));
        }

        {  // set context based on ldap dn
            final String ldapTestUserDN = ldapForm.get(PARAM_LDAP2_TEST_USER);
            storedConfiguration.writeSetting(PwmSetting.LDAP_TEST_USER_DN, new StringValue(ldapTestUserDN));
        }

        {  // set admin query
            final String ldapTestUserDN = ldapForm.get(PARAM_LDAP2_ADMINS);
            storedConfiguration.writeSetting(PwmSetting.QUERY_MATCH_PWM_ADMIN, new StringValue(ldapTestUserDN));
        }
    }

    private void writeConfig(
            final ContextManager contextManager,
            final ConfigGuideBean configGuideBean
    ) throws PwmOperationalException {
        final StoredConfiguration storedConfiguration = configGuideBean.getStoredConfiguration();
        final String configPassword = configGuideBean.getFormData().get(PARAM_CONFIG_PASSWORD);
        if (configPassword != null && configPassword.length() > 0) {
            storedConfiguration.setPassword(configPassword);
        } else {
            storedConfiguration.writeProperty(StoredConfiguration.PROPERTY_KEY_PASSWORD_HASH,null);
        }


        { //determine promiscuous ssl setting
            if ("true".equalsIgnoreCase(configGuideBean.getFormData().get(PARAM_LDAP_SECURE))) {
                if (configGuideBean.isUseConfiguredCerts() || configGuideBean.isCertsTrustedbyKeystore()) {
                    storedConfiguration.writeSetting(PwmSetting.LDAP_PROMISCUOUS_SSL, new BooleanValue(false));
                } else {
                    storedConfiguration.writeSetting(PwmSetting.LDAP_PROMISCUOUS_SSL, new BooleanValue(true));
                }
            } else {
                storedConfiguration.writeSetting(PwmSetting.LDAP_PROMISCUOUS_SSL, new BooleanValue(false));
            }
        }

        writeConfig(contextManager, storedConfiguration);
    }

    private static void writeConfig(
            final ContextManager contextManager,
            final StoredConfiguration storedConfiguration
    ) throws PwmOperationalException {
        ConfigurationReader configReader = contextManager.getConfigReader();

        storedConfiguration.resetSetting(PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE);
        storedConfiguration.resetSetting(PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE);

        try {
            // add a random security key
            storedConfiguration.writeSetting(PwmSetting.PWM_SECURITY_KEY, new PasswordValue(PwmRandom.getInstance().alphaNumericString(512)));

            storedConfiguration.writeProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_IS_EDITABLE,"true");
            configReader.saveConfiguration(storedConfiguration);

            contextManager.reinitialize();
        } catch (PwmException e) {
            throw new PwmOperationalException(e.getErrorInformation());
        } catch (Exception e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,"unable to save configuration: " + e.getLocalizedMessage());
            throw new PwmOperationalException(errorInformation);
        }
    }


    static void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final ServletContext servletContext = req.getSession().getServletContext();
        final ConfigGuideBean configGuideBean = (ConfigGuideBean)PwmSession.getPwmSession(req).getSessionBean(ConfigGuideBean.class);
        String destURL = '/' + PwmConstants.URL_JSP_CONFIG_GUIDE;
        destURL = destURL.replace("%1%", configGuideBean.getStep().toString().toLowerCase());
        servletContext.getRequestDispatcher(destURL).forward(req, resp);
    }

    public enum STEP {
        START, TEMPLATE, LDAP, LDAPCERT, LDAP2, LDAP3, PASSWORD, END, FINISH
    }
}
