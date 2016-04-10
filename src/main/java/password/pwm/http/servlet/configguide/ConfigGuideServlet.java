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

package password.pwm.http.servlet.configguide;

import com.google.gson.reflect.TypeToken;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiProviderFactory;
import com.novell.ldapchai.provider.ChaiSetting;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.function.UserMatchViewerFunction;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.config.value.FileValue;
import password.pwm.error.*;
import password.pwm.health.*;
import password.pwm.http.*;
import password.pwm.http.bean.ConfigGuideBean;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.configeditor.ConfigEditorServlet;
import password.pwm.ldap.LdapBrowser;
import password.pwm.ldap.schema.SchemaManager;
import password.pwm.ldap.schema.SchemaOperationResult;
import password.pwm.util.*;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.bean.HealthData;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.*;
import java.util.*;


@WebServlet(
        name = "ConfigGuideServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/config/config-guide",
                PwmConstants.URL_PREFIX_PRIVATE + "/config/ConfigGuide"
        }
)
public class ConfigGuideServlet extends AbstractPwmServlet {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ConfigGuideServlet.class.getName());


    private static final String LDAP_PROFILE_KEY = "default";


    public enum ConfigGuideAction implements AbstractPwmServlet.ProcessAction {
        ldapHealth(HttpMethod.GET),
        updateForm(HttpMethod.POST),
        gotoStep(HttpMethod.POST),
        useConfiguredCerts(HttpMethod.POST),
        uploadConfig(HttpMethod.POST),
        extendSchema(HttpMethod.POST),
        viewAdminMatches(HttpMethod.POST),
        browseLdap(HttpMethod.POST),
        uploadJDBCDriver(HttpMethod.POST),
        skipGuide(HttpMethod.POST),

        ;

        private final HttpMethod method;

        ConfigGuideAction(HttpMethod method)
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods()
        {
            return Collections.singletonList(method);
        }
    }

    protected ConfigGuideAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        try {
            return ConfigGuideAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


    @Override
    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        if (pwmApplication.getSessionStateService().getBean(pwmRequest, ConfigGuideBean.class).getStep() == GuideStep.START) {
            pwmApplication.getSessionStateService().clearBean(pwmRequest, ConfigGuideBean.class);
        }

        final ConfigGuideBean configGuideBean = pwmApplication.getSessionStateService().getBean(pwmRequest, ConfigGuideBean.class);

        if (pwmApplication.getApplicationMode() != PwmApplicationMode.NEW) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,"ConfigGuide unavailable unless in NEW mode");
            LOGGER.error(pwmSession, errorInformation.toDebugStr());
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        if (!configGuideBean.getFormData().containsKey(ConfigGuideForm.FormParameter.PARAM_APP_SITEURL)) {
            final URI uri = URI.create(pwmRequest.getHttpServletRequest().getRequestURL().toString());
            final int port = PwmURL.portForUriSchema(uri);
            final String newUri = uri.getScheme() + "://" + uri.getHost() + ":" + port + pwmRequest.getContextPath();
            configGuideBean.getFormData().put(ConfigGuideForm.FormParameter.PARAM_APP_SITEURL,newUri);
        }

        if (configGuideBean.getStep() == GuideStep.LDAP_CERT) {
            final String ldapServerString = ConfigGuideForm.figureLdapUrlFromFormConfig(configGuideBean.getFormData());
            try {
                final URI ldapServerUri = new URI(ldapServerString);
                if ("ldaps".equalsIgnoreCase(ldapServerUri.getScheme())) {
                    configGuideBean.setLdapCertificates(X509Utils.readRemoteCertificates(ldapServerUri));
                    configGuideBean.setCertsTrustedbyKeystore(X509Utils.testIfLdapServerCertsInDefaultKeystore(ldapServerUri));
                } else {
                    configGuideBean.setLdapCertificates(null);
                    configGuideBean.setCertsTrustedbyKeystore(false);
                }
            } catch (Exception e) {
                LOGGER.error("error reading/testing ldap server certificates: " + e.getMessage());
            }
        }

        final ConfigGuideAction action = readProcessAction(pwmRequest);
        if (action != null) {
            pwmRequest.validatePwmFormID();
            switch (action) {
                case ldapHealth:
                    restLdapHealth(pwmRequest, configGuideBean);
                    return;

                case updateForm:
                    restUpdateLdapForm(pwmRequest, configGuideBean);
                    return;

                case gotoStep:
                    restGotoStep(pwmRequest, configGuideBean);
                    return;

                case useConfiguredCerts:
                    restUseConfiguredCerts(pwmRequest, configGuideBean);
                    return;

                case uploadConfig:
                    restUploadConfig(pwmRequest);
                    return;

                case extendSchema:
                    restExtendSchema(pwmRequest, configGuideBean);
                    return;

                case viewAdminMatches:
                    restViewAdminMatches(pwmRequest, configGuideBean);
                    return;

                case browseLdap:
                    restBrowseLdap(pwmRequest, configGuideBean);
                    return;

                case uploadJDBCDriver:
                    restUploadJDBCDriver(pwmRequest, configGuideBean);
                    return;

                case skipGuide:
                    restSkipGuide(pwmRequest);
                    return;
            }
        }

        if (!pwmRequest.getPwmResponse().getHttpServletResponse().isCommitted()) {
            forwardToJSP(pwmRequest);
        }
    }

    public static void restUploadConfig(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();

        if (pwmApplication.getApplicationMode() == PwmApplicationMode.RUNNING) {
            final String errorMsg = "config upload is not permitted when in running mode";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_UPLOAD_FAILURE,errorMsg,new String[]{errorMsg});
            pwmRequest.respondWithError(errorInformation, true);
            return;
        }

        if (ServletFileUpload.isMultipartContent(req)) {
            final InputStream uploadedFile = pwmRequest.readFileUploadStream(PwmConstants.PARAM_FILE_UPLOAD);
            if (uploadedFile != null) {
                try {
                    final StoredConfigurationImpl storedConfig = StoredConfigurationImpl.fromXml(uploadedFile);
                    final List<String> configErrors = storedConfig.validateValues();
                    if (configErrors != null && !configErrors.isEmpty()) {
                        throw new PwmOperationalException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,configErrors.get(0)));
                    }
                    writeConfig(ContextManager.getContextManager(req.getSession()),storedConfig);
                    LOGGER.trace(pwmSession, "read config from file: " + storedConfig.toString());
                    final RestResultBean restResultBean = new RestResultBean();
                    restResultBean.setSuccessMessage("read message");
                    pwmRequest.getPwmResponse().outputJsonResult(restResultBean);
                    req.getSession().invalidate();
                } catch (PwmException e) {
                    final RestResultBean restResultBean = RestResultBean.fromError(e.getErrorInformation(), pwmRequest);
                    pwmRequest.getPwmResponse().outputJsonResult(restResultBean);
                    LOGGER.error(pwmSession, e.getErrorInformation().toDebugStr());
                }
            } else {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_UPLOAD_FAILURE, "error reading config file: no file present in upload");
                final RestResultBean restResultBean = RestResultBean.fromError(errorInformation, pwmRequest);
                pwmRequest.getPwmResponse().outputJsonResult(restResultBean);
                LOGGER.error(pwmSession, errorInformation.toDebugStr());
            }
        }
    }


    private void restUseConfiguredCerts(
            final PwmRequest pwmRequest,
            final ConfigGuideBean configGuideBean
    )
            throws PwmUnrecoverableException, IOException
    {
        final boolean value = Boolean.parseBoolean(pwmRequest.readParameterAsString("value"));
        configGuideBean.setUseConfiguredCerts(value);
        pwmRequest.outputJsonResult(new RestResultBean());
    }


    private void restLdapHealth(
            final PwmRequest pwmRequest,
            final ConfigGuideBean configGuideBean
    )
            throws IOException, PwmUnrecoverableException
    {

        final StoredConfigurationImpl storedConfigurationImpl = ConfigGuideForm.generateStoredConfig(configGuideBean);
        final Configuration tempConfiguration = new Configuration(storedConfigurationImpl);
        final PwmApplication tempApplication = new PwmApplication(pwmRequest.getPwmApplication()
                .getPwmEnvironment()
                .makeRuntimeInstance(tempConfiguration));

        final LDAPStatusChecker ldapStatusChecker = new LDAPStatusChecker();
        final List<HealthRecord> records = new ArrayList<>();
        final LdapProfile ldapProfile = tempConfiguration.getDefaultLdapProfile();

        switch (configGuideBean.getStep()) {
            case LDAP_SERVER: {
                try {
                    checkLdapServer(configGuideBean);
                    records.add(password.pwm.health.HealthRecord.forMessage(HealthMessage.LDAP_OK));
                } catch (Exception e) {
                    records.add(new HealthRecord(HealthStatus.WARN, HealthTopic.LDAP, "Can not connect to remote server: " + e.getMessage()));
                }
            }
            break;


            case LDAP_PROXY: {
                records.addAll(ldapStatusChecker.checkBasicLdapConnectivity(tempApplication, tempConfiguration, ldapProfile, false));
                if (records.isEmpty()) {
                    records.add(password.pwm.health.HealthRecord.forMessage(HealthMessage.LDAP_OK));
                }
            }
            break;

            case LDAP_CONTEXT: {
                records.addAll(ldapStatusChecker.checkBasicLdapConnectivity(tempApplication, tempConfiguration, ldapProfile, true));
                if (records.isEmpty()) {
                    records.add(new HealthRecord(HealthStatus.GOOD, HealthTopic.LDAP, "LDAP Contextless Login Root validated"));
                }
            }
            break;

            case LDAP_ADMINS: {
                try {
                    final UserMatchViewerFunction userMatchViewerFunction = new UserMatchViewerFunction();
                    final Collection<UserIdentity> results = userMatchViewerFunction.discoverMatchingUsers(
                            pwmRequest.getPwmApplication(),
                            2,
                            storedConfigurationImpl,
                            PwmSetting.QUERY_MATCH_PWM_ADMIN,
                            null
                    );

                    if (results.isEmpty()) {
                        records.add(new HealthRecord(HealthStatus.WARN, HealthTopic.LDAP, "No matching admin users"));
                    } else {
                        records.add(new HealthRecord(HealthStatus.GOOD, HealthTopic.LDAP, "Admin group validated"));
                    }
                } catch (PwmException e) {
                    records.add(new HealthRecord(HealthStatus.WARN, HealthTopic.LDAP, "Error during admin group validation: " + e.getErrorInformation().toDebugStr()));
                } catch (Exception e) {
                    records.add(new HealthRecord(HealthStatus.WARN, HealthTopic.LDAP, "Error during admin group validation: " + e.getMessage()));
                }
            }
            break;

            case LDAP_TESTUSER: {
                final String testUserValue = configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_LDAP_TEST_USER);
                if (testUserValue != null && !testUserValue.isEmpty()) {
                    records.addAll(ldapStatusChecker.checkBasicLdapConnectivity(tempApplication, tempConfiguration, ldapProfile, false));
                    records.addAll(ldapStatusChecker.doLdapTestUserCheck(tempConfiguration, ldapProfile, tempApplication));
                } else {
                    records.add(new HealthRecord(HealthStatus.CAUTION, HealthTopic.LDAP, "No test user specified"));
                }
            }
            break;

            case DATABASE: {
                records.addAll(DatabaseStatusChecker.checkNewDatabaseStatus(tempConfiguration));
            }
            break;
        }

        HealthData jsonOutput = new HealthData();
        jsonOutput.records = password.pwm.ws.server.rest.bean.HealthRecord.fromHealthRecords(records,
                pwmRequest.getLocale(), tempConfiguration);
        jsonOutput.timestamp = new Date();
        jsonOutput.overall = HealthMonitor.getMostSevereHealthStatus(records).toString();
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(jsonOutput);
        pwmRequest.outputJsonResult(restResultBean);
    }

    public static Percent stepProgress(GuideStep step) {
        final int ordinal = step.ordinal();
        final int total = GuideStep.values().length - 2;
        return new Percent(ordinal,total);
    }

    private void restViewAdminMatches(
            final PwmRequest pwmRequest,
            final ConfigGuideBean configGuideBean
    )
            throws IOException, ServletException
    {

        try {
            final UserMatchViewerFunction userMatchViewerFunction = new UserMatchViewerFunction();
            final StoredConfigurationImpl storedConfiguration = ConfigGuideForm.generateStoredConfig(configGuideBean);
            final Serializable output = userMatchViewerFunction.provideFunction(pwmRequest, storedConfiguration, PwmSetting.QUERY_MATCH_PWM_ADMIN, null, null);
            pwmRequest.outputJsonResult(new RestResultBean(output));
        } catch (PwmException e) {
            LOGGER.error(pwmRequest,e.getErrorInformation());
            pwmRequest.respondWithError(e.getErrorInformation());
        } catch (Exception e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, "error while testing matches = " + e.getMessage());
            LOGGER.error(pwmRequest,errorInformation);
            pwmRequest.respondWithError(errorInformation);
        }
    }

    private void restBrowseLdap(
            final PwmRequest pwmRequest,
            final ConfigGuideBean configGuideBean
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final StoredConfigurationImpl storedConfiguration = ConfigGuideForm.generateStoredConfig(configGuideBean);
        if (configGuideBean.getStep() == GuideStep.LDAP_PROXY) {
            storedConfiguration.resetSetting(PwmSetting.LDAP_PROXY_USER_DN, LDAP_PROFILE_KEY, null);
            storedConfiguration.resetSetting(PwmSetting.LDAP_PROXY_USER_PASSWORD, LDAP_PROFILE_KEY, null);
        }

        final Date startTime = new Date();
        final Map<String, String> inputMap = pwmRequest.readBodyAsJsonStringMap(PwmHttpRequestWrapper.Flag.BypassValidation);
        final String profile = inputMap.get("profile");
        final String dn = inputMap.containsKey("dn") ? inputMap.get("dn") : "";

        final LdapBrowser ldapBrowser = new LdapBrowser(storedConfiguration);
        final LdapBrowser.LdapBrowseResult result = ldapBrowser.doBrowse(profile, dn);
        ldapBrowser.close();

        LOGGER.trace(pwmRequest, "performed ldapBrowse operation in "
                + TimeDuration.fromCurrent(startTime).asCompactString()
                + ", result=" + JsonUtil.serialize(result));

        pwmRequest.outputJsonResult(new RestResultBean(result));
    }

    private void restUpdateLdapForm(
            final PwmRequest pwmRequest,
            final ConfigGuideBean configGuideBean
    )
            throws IOException, PwmUnrecoverableException
    {
        final String bodyString = pwmRequest.readRequestBodyAsString();
        final Map<ConfigGuideForm.FormParameter,String> incomingFormData = JsonUtil.deserialize(bodyString, new TypeToken<Map<ConfigGuideForm.FormParameter, String>>() {
        });

        if (incomingFormData != null) {
            configGuideBean.getFormData().putAll(incomingFormData);
        }

        final RestResultBean restResultBean = new RestResultBean();
        pwmRequest.outputJsonResult(restResultBean);
    }

    private void restGotoStep(final PwmRequest pwmRequest, final ConfigGuideBean configGuideBean)
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final String requestedStep = pwmRequest.readParameterAsString("step");
        GuideStep step = null;
        if (requestedStep != null && requestedStep.length() > 0) {
            try {
                step = GuideStep.valueOf(requestedStep);
            } catch (IllegalArgumentException e) { /* */ }
        }

        if (GuideStep.START.equals(requestedStep)) {
            configGuideBean.setFormData(ConfigGuideForm.defaultForm());
        }

        if ("NEXT".equals(requestedStep)) {
            step = configGuideBean.getStep().next();
            while (step != GuideStep.FINISH && !step.visible(configGuideBean)) {
                step =step.next();
            }
        } else if ("PREVIOUS".equals(requestedStep)) {
            step = configGuideBean.getStep().previous();
            while (step != GuideStep.START && !step.visible(configGuideBean)) {
                step = step.previous();
            }
        }

        if (step == null) {
            final String errorMsg = "unknown goto step request: " + requestedStep;
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg);
            final RestResultBean restResultBean = RestResultBean.fromError(errorInformation, pwmRequest);
            LOGGER.error(pwmRequest,errorInformation.toDebugStr());
            pwmRequest.outputJsonResult(restResultBean);
            return;
        }

        if (step == GuideStep.FINISH) {
            final ContextManager contextManager = ContextManager.getContextManager(pwmRequest);
            try {
                writeConfig(contextManager, configGuideBean);
                pwmRequest.getPwmSession().getSessionStateBean().setTheme(null);
            } catch (PwmException e) {
                final RestResultBean restResultBean = RestResultBean.fromError(e.getErrorInformation(), pwmRequest);
                pwmRequest.outputJsonResult(restResultBean);
                return;
            } catch (Exception e) {
                final RestResultBean restResultBean = RestResultBean.fromError(new ErrorInformation(PwmError.ERROR_UNKNOWN,"error during save: " + e.getMessage()), pwmRequest);
                pwmRequest.outputJsonResult(restResultBean);
                return;
            }
            final HashMap<String,String> resultData = new HashMap<>();
            resultData.put("serverRestart","true");
            pwmRequest.outputJsonResult(new RestResultBean(resultData));
            pwmRequest.invalidateSession();
        } else {
            configGuideBean.setStep(step);
            pwmRequest.outputJsonResult(new RestResultBean());
            LOGGER.trace("setting current step to: " + step);
        }
    }



    private void writeConfig(
            final ContextManager contextManager,
            final ConfigGuideBean configGuideBean
    ) throws PwmOperationalException, PwmUnrecoverableException {
        final StoredConfigurationImpl storedConfiguration = ConfigGuideForm.generateStoredConfig(configGuideBean);
        final String configPassword = configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_CONFIG_PASSWORD);
        if (configPassword != null && configPassword.length() > 0) {
            storedConfiguration.setPassword(configPassword);
        } else {
            storedConfiguration.writeConfigProperty(ConfigurationProperty.PASSWORD_HASH, null);
        }

        storedConfiguration.writeConfigProperty(ConfigurationProperty.CONFIG_IS_EDITABLE, "false");
        writeConfig(contextManager, storedConfiguration);
    }

    private static void writeConfig(
            final ContextManager contextManager,
            final StoredConfigurationImpl storedConfiguration
    ) throws PwmOperationalException, PwmUnrecoverableException {
        ConfigurationReader configReader = contextManager.getConfigReader();
        PwmApplication pwmApplication = contextManager.getPwmApplication();

        try {
            // add a random security key
            storedConfiguration.initNewRandomSecurityKey();

            configReader.saveConfiguration(storedConfiguration, pwmApplication, null);

            contextManager.requestPwmApplicationRestart();
        } catch (PwmException e) {
            throw new PwmOperationalException(e.getErrorInformation());
        } catch (Exception e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,"unable to save configuration: " + e.getLocalizedMessage());
            throw new PwmOperationalException(errorInformation);
        }
    }


    static void forwardToJSP(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final ConfigGuideBean configGuideBean = pwmRequest.getPwmApplication().getSessionStateService().getBean(pwmRequest,ConfigGuideBean.class);

        if (configGuideBean.getStep() == GuideStep.LDAP_PERMISSIONS) {
            LDAPPermissionCalculator ldapPermissionCalculator = new LDAPPermissionCalculator(ConfigGuideForm.generateStoredConfig(configGuideBean));
            pwmRequest.setAttribute(PwmRequest.Attribute.LdapPermissionItems,ldapPermissionCalculator);
        }

        final HttpServletRequest req = pwmRequest.getHttpServletRequest();
        final ServletContext servletContext = req.getSession().getServletContext();
        String destURL = '/' + PwmConstants.URL_JSP_CONFIG_GUIDE;
        destURL = destURL.replace("%1%", configGuideBean.getStep().toString().toLowerCase());
        servletContext.getRequestDispatcher(destURL).forward(req, pwmRequest.getPwmResponse().getHttpServletResponse());
    }

    public static SchemaOperationResult extendSchema(ConfigGuideBean configGuideBean, final boolean doSchemaExtension) {
        final Map<ConfigGuideForm.FormParameter,String> form = configGuideBean.getFormData();
        final boolean ldapServerSecure = "true".equalsIgnoreCase(form.get(ConfigGuideForm.FormParameter.PARAM_LDAP_SECURE));
        final String ldapUrl = "ldap" + (ldapServerSecure ? "s" : "") + "://" + form.get(ConfigGuideForm.FormParameter.PARAM_LDAP_HOST) + ":" + form.get(ConfigGuideForm.FormParameter.PARAM_LDAP_PORT);
        try {
            final ChaiConfiguration chaiConfiguration = new ChaiConfiguration(ldapUrl, form.get(ConfigGuideForm.FormParameter.PARAM_LDAP_PROXY_DN), form.get(ConfigGuideForm.FormParameter.PARAM_LDAP_PROXY_PW));
            chaiConfiguration.setSetting(ChaiSetting.PROMISCUOUS_SSL,"true");
            final ChaiProvider chaiProvider = ChaiProviderFactory.createProvider(chaiConfiguration);
            if (doSchemaExtension) {
                return SchemaManager.extendSchema(chaiProvider);
            } else {
                return SchemaManager.checkExistingSchema(chaiProvider);
            }
        } catch (Exception e) {
            LOGGER.error("unable to create schema extender object: " + e.getMessage());
            return null;
        }
    }

    private void restExtendSchema(final PwmRequest pwmRequest, final ConfigGuideBean configGuideBean)
            throws IOException
    {
        try {
            SchemaOperationResult schemaOperationResult = extendSchema(configGuideBean, true);
            pwmRequest.outputJsonResult(new RestResultBean(schemaOperationResult.getOperationLog()));
        } catch (Exception e) {
            ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,e.getMessage());
            pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
            LOGGER.error(pwmRequest, e.getMessage(), e);
        }
    }



    private void checkLdapServer(ConfigGuideBean configGuideBean) throws PwmOperationalException, IOException {
        final Map<ConfigGuideForm.FormParameter,String> formData = configGuideBean.getFormData();
        final String host = formData.get(ConfigGuideForm.FormParameter.PARAM_LDAP_HOST);
        final int port = Integer.parseInt(formData.get(ConfigGuideForm.FormParameter.PARAM_LDAP_PORT));

        { // socket test
            final InetAddress inetAddress = InetAddress.getByName(host);
            final SocketAddress socketAddress = new InetSocketAddress(inetAddress, port);
            final Socket socket = new Socket();

            final int timeout = 2000;
            socket.connect(socketAddress, timeout);
        }

        if (Boolean.parseBoolean(formData.get(ConfigGuideForm.FormParameter.PARAM_LDAP_SECURE))) {
            X509Utils.readRemoteCertificates(host, port);
        }
    }

    static void restUploadJDBCDriver(final PwmRequest pwmRequest, final ConfigGuideBean configGuideBean)
            throws PwmUnrecoverableException, IOException, ServletException
    {
        try {
            final int maxFileSize = Integer.parseInt(pwmRequest.getConfig().readAppProperty(AppProperty.CONFIG_MAX_JDBC_JAR_SIZE));
            final FileValue fileValue = ConfigEditorServlet.readFileUploadToSettingValue(pwmRequest, maxFileSize);
            configGuideBean.setDatabaseDriver(fileValue);
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setSuccessMessage("upload completed");
            pwmRequest.getPwmResponse().outputJsonResult(restResultBean);
        } catch (PwmException e) {
            final RestResultBean restResultBean = RestResultBean.fromError(e.getErrorInformation(), pwmRequest);
            pwmRequest.getPwmResponse().outputJsonResult(restResultBean);
            LOGGER.error(pwmRequest, e.getErrorInformation().toDebugStr());
        }
    }

    private void restSkipGuide(final PwmRequest pwmRequest) throws PwmUnrecoverableException, IOException {
        final Map<String,String> inputJson = pwmRequest.readBodyAsJsonStringMap(PwmHttpRequestWrapper.Flag.BypassValidation);
        final String password = inputJson.get("password");
        final ContextManager contextManager = ContextManager.getContextManager(pwmRequest);
        try {
            final StoredConfigurationImpl storedConfiguration = new StoredConfigurationImpl();
            storedConfiguration.writeConfigProperty(ConfigurationProperty.CONFIG_IS_EDITABLE, "true");
            storedConfiguration.setPassword(password);
            writeConfig(contextManager, storedConfiguration);
            pwmRequest.outputJsonResult(new RestResultBean());
            pwmRequest.invalidateSession();
        } catch (PwmOperationalException e) {
            LOGGER.error("error during skip config guide: " + e.getMessage(),e);
        }
    }

}


