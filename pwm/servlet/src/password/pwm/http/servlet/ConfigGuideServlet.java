/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiProviderFactory;
import com.novell.ldapchai.provider.ChaiSetting;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.*;
import password.pwm.config.function.UserMatchViewerFunction;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.value.*;
import password.pwm.error.*;
import password.pwm.health.*;
import password.pwm.http.ContextManager;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.ConfigGuideBean;
import password.pwm.ldap.schema.SchemaManager;
import password.pwm.ldap.schema.SchemaOperationResult;
import password.pwm.util.PasswordData;
import password.pwm.util.ServletHelper;
import password.pwm.util.X509Utils;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.bean.HealthData;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.*;

public class ConfigGuideServlet extends PwmServlet {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ConfigGuideServlet.class.getName());

    public static final String PARAM_TEMPLATE_NAME = "template-name";

    public static final String PARAM_APP_SITEURL = "app-siteurl";

    public static final String PARAM_LDAP_HOST = "ldap-server-ip";
    public static final String PARAM_LDAP_PORT = "ldap-server-port";
    public static final String PARAM_LDAP_SECURE = "ldap-server-secure";
    public static final String PARAM_LDAP_ADMIN_DN = "ldap-user-dn";
    public static final String PARAM_LDAP_ADMIN_PW = "ldap-user-pw";

    public static final String PARAM_LDAP2_CONTEXT = "ldap2-context";
    public static final String PARAM_LDAP2_TEST_USER = "ldap2-testUser";
    public static final String PARAM_LDAP2_ADMIN_GROUP = "ldap2-adminGroup";

    public static final String PARAM_CR_STORAGE_PREF = "cr_storage-pref";

    public static final String PARAM_CONFIG_PASSWORD = "config-password";
    public static final String PARAM_CONFIG_PASSWORD_VERIFY = "config-password-verify";

    private static final String LDAP_PROFILE_KEY = "default";


    public enum STEP {
        START,
        TEMPLATE,
        LDAP,
        LDAPCERT,
        LDAP2,
        LDAP3,
        CR_STORAGE,
        LDAP_SCHEMA,
        APP,
        PASSWORD,
        END,
        FINISH,

        ;

        private static final STEP[] enumValues = values();

        public STEP next() {
            return enumValues[(this.ordinal()+1) % enumValues.length];
        }

        public STEP previous() {
            return enumValues[(this.ordinal()-1) % enumValues.length];
        }
    }

    public enum ConfigGuideAction implements PwmServlet.ProcessAction {
        ldapHealth(HttpMethod.GET),
        updateForm(HttpMethod.POST),
        gotoStep(HttpMethod.POST),
        useConfiguredCerts(HttpMethod.POST),
        uploadConfig(HttpMethod.POST),
        extendSchema(HttpMethod.POST),
        viewAdminMatches(HttpMethod.POST),
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


    public static Map<String,String> defaultForm(PwmSetting.Template template) {
        final Map<String,String> defaultLdapForm = new HashMap<>();

        try {
            final String defaultLdapUrlString = ((List<String>)PwmSetting.LDAP_SERVER_URLS.getDefaultValue(template).toNativeObject()).get(0);
            final URI uri = new URI(defaultLdapUrlString);

            defaultLdapForm.put(PARAM_LDAP_HOST, uri.getHost());
            defaultLdapForm.put(PARAM_LDAP_PORT, String.valueOf(uri.getPort()));
            defaultLdapForm.put(PARAM_LDAP_SECURE, "ldaps".equalsIgnoreCase(uri.getScheme()) ? "true" : "false");

            defaultLdapForm.put(PARAM_LDAP_ADMIN_DN, (String)PwmSetting.LDAP_PROXY_USER_DN.getDefaultValue(template).toNativeObject());
            defaultLdapForm.put(PARAM_LDAP_ADMIN_PW, "");

            defaultLdapForm.put(PARAM_LDAP2_CONTEXT, ((List<String>)PwmSetting.LDAP_CONTEXTLESS_ROOT.getDefaultValue(template).toNativeObject()).get(0));
            defaultLdapForm.put(PARAM_LDAP2_TEST_USER, (String)PwmSetting.LDAP_TEST_USER_DN.getDefaultValue(template).toNativeObject());
            {
                List<UserPermission> userPermissions = (List<UserPermission>)PwmSetting.QUERY_MATCH_PWM_ADMIN.getDefaultValue(template).toNativeObject();
                final String groupDN = userPermissions != null && userPermissions.size() > 0 ? userPermissions.get(0).getLdapBase() : "";
                defaultLdapForm.put(PARAM_LDAP2_ADMIN_GROUP,groupDN);
            }

            defaultLdapForm.put(PARAM_CR_STORAGE_PREF, (String) PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE.getDefaultValue(template).toNativeObject());

            defaultLdapForm.put(PARAM_CONFIG_PASSWORD, "");
            defaultLdapForm.put(PARAM_CONFIG_PASSWORD_VERIFY, "");
        } catch (Exception e) {
            LOGGER.error("error building static form values using default configuration: " + e.getMessage());
            e.printStackTrace();
        }

        return Collections.unmodifiableMap(defaultLdapForm);
    }

    @Override
    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        if (((ConfigGuideBean)pwmSession.getSessionBean(ConfigGuideBean.class)).getStep() == STEP.START) {
            pwmSession.clearSessionBeans();
            pwmSession.getSessionStateBean().setTheme(null);
        }

        final ConfigGuideBean configGuideBean = (ConfigGuideBean)pwmSession.getSessionBean(ConfigGuideBean.class);

        if (pwmApplication.getApplicationMode() != PwmApplication.MODE.NEW) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"ConfigGuide unavailable unless in NEW mode");
            LOGGER.error(pwmSession, errorInformation.toDebugStr());
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        if (!configGuideBean.getFormData().containsKey(PARAM_APP_SITEURL)) {
            final URI uri = URI.create(pwmRequest.getHttpServletRequest().getRequestURL().toString());
            final String newUri = uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort() + pwmRequest.getContextPath();
            configGuideBean.getFormData().put(PARAM_APP_SITEURL,newUri);
        }

        pwmSession.setSessionTimeout(
                pwmRequest.getHttpServletRequest().getSession(),
                Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.CONFIG_GUIDE_IDLE_TIMEOUT)));

        if (configGuideBean.getStep() == STEP.LDAPCERT) {
            final String ldapServerString = ((List<String>) configGuideBean.getStoredConfiguration().readSetting(PwmSetting.LDAP_SERVER_URLS, LDAP_PROFILE_KEY).toNativeObject()).get(0);
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
            }
        }

        forwardToJSP(pwmRequest);
    }

    public static void restUploadConfig(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();

        if (pwmApplication.getApplicationMode() == PwmApplication.MODE.RUNNING) {
            final String errorMsg = "config upload is not permitted when in running mode";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_UPLOAD_FAILURE,errorMsg,new String[]{errorMsg});
            pwmRequest.respondWithError(errorInformation, true);
            return;
        }

        if (ServletFileUpload.isMultipartContent(req)) {
            final InputStream uploadedFile = ServletHelper.readFileUpload(req,"uploadFile");
            if (uploadedFile != null) {
                try {
                    final StoredConfiguration storedConfig = StoredConfiguration.fromXml(uploadedFile);
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
        final StoredValue newStoredValue = value ?
                new X509CertificateValue(configGuideBean.getLdapCertificates()) :
                new X509CertificateValue(new X509Certificate[0]);
        configGuideBean.getStoredConfiguration().writeSetting(
                PwmSetting.LDAP_SERVER_CERTS,
                LDAP_PROFILE_KEY,
                newStoredValue,
                null
        );
        pwmRequest.outputJsonResult(new RestResultBean());
    }


    private void restLdapHealth(
            final PwmRequest pwmRequest,
            final ConfigGuideBean configGuideBean
    )
            throws IOException, PwmUnrecoverableException {
        final Configuration tempConfiguration = new Configuration(configGuideBean.getStoredConfiguration());
        final PwmApplication tempApplication = new PwmApplication.PwmEnvironment(tempConfiguration, pwmRequest.getPwmApplication().getApplicationPath())
                .setApplicationMode(PwmApplication.MODE.NEW)
                .setInitLogging(false)
                .setWebInfPath(pwmRequest.getPwmApplication().getWebInfPath())
                .createPwmApplication();
        final LDAPStatusChecker ldapStatusChecker = new LDAPStatusChecker();
        final List<HealthRecord> records = new ArrayList<>();
        final LdapProfile ldapProfile = tempConfiguration.getDefaultLdapProfile();
        switch (configGuideBean.getStep()) {
            case LDAP: {
                records.addAll(ldapStatusChecker.checkBasicLdapConnectivity(tempApplication, tempConfiguration, ldapProfile, false));
                if (records.isEmpty()) {
                    records.add(password.pwm.health.HealthRecord.forMessage(HealthMessage.LDAP_OK));
                }
            }
            break;

            case LDAP2: {
                records.addAll(ldapStatusChecker.checkBasicLdapConnectivity(tempApplication, tempConfiguration, ldapProfile, true));
                if (records.isEmpty()) {
                    records.add(new HealthRecord(HealthStatus.GOOD, HealthTopic.LDAP, "LDAP Contextless Login Root validated"));
                }
                try {
                    final UserMatchViewerFunction userMatchViewerFunction = new UserMatchViewerFunction();
                    final Map<String, List<String>> results = userMatchViewerFunction.discoverMatchingUsers(
                            pwmRequest.getPwmApplication(),
                            2,
                            configGuideBean.getStoredConfiguration(),
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

            case LDAP3: {
                final String testUserValue = configGuideBean.getFormData().get(PARAM_LDAP2_TEST_USER);
                if (testUserValue != null && !testUserValue.isEmpty()) {
                    records.addAll(ldapStatusChecker.checkBasicLdapConnectivity(tempApplication, tempConfiguration, ldapProfile, false));
                    records.addAll(ldapStatusChecker.doLdapTestUserCheck(tempConfiguration, ldapProfile, tempApplication));
                } else {
                    records.add(new HealthRecord(HealthStatus.CAUTION, HealthTopic.LDAP, "No test user specified"));
                }
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

    private void restViewAdminMatches(
            final PwmRequest pwmRequest,
            final ConfigGuideBean configGuideBean
    ) throws IOException, ServletException {
        final UserMatchViewerFunction userMatchViewerFunction = new UserMatchViewerFunction();
        final int maxResults = 1000;
        try {
            final Map<String, List<String>> results = userMatchViewerFunction.discoverMatchingUsers(
                    pwmRequest.getPwmApplication(),
                    1000,
                    configGuideBean.getStoredConfiguration(),
                    PwmSetting.QUERY_MATCH_PWM_ADMIN,
                    null
            );
            final String htmlResults = userMatchViewerFunction.convertResultsToHtmlTable(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getLocale(),
                    results,
                    maxResults
            );
            pwmRequest.outputJsonResult(new RestResultBean(htmlResults));
        } catch (PwmException e) {
            LOGGER.error(pwmRequest,e.getErrorInformation());
            pwmRequest.respondWithError(e.getErrorInformation());
        } catch (Exception e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, "error while testing matches = " + e.getMessage());
            LOGGER.error(pwmRequest,errorInformation);
            pwmRequest.respondWithError(errorInformation);
        }
    }

    private void restUpdateLdapForm(
            final PwmRequest pwmRequest,
            final ConfigGuideBean configGuideBean
    )
            throws IOException, PwmUnrecoverableException
    {
        final StoredConfiguration storedConfiguration = configGuideBean.getStoredConfiguration();
        final Map<String,String> incomingFormData = pwmRequest.readBodyAsJsonStringMap();

        if (incomingFormData != null) {
            configGuideBean.getFormData().putAll(incomingFormData);
        }

        if (incomingFormData != null && incomingFormData.get(PARAM_TEMPLATE_NAME) != null && !incomingFormData.get(PARAM_TEMPLATE_NAME).isEmpty()) {
            try {
                final PwmSetting.Template template = PwmSetting.Template.valueOf(incomingFormData.get(PARAM_TEMPLATE_NAME));
                if (configGuideBean.getSelectedTemplate() != template) {
                    LOGGER.debug(pwmRequest, "resetting form defaults using " + template.toString() + " template");
                    final Map<String, String> defaultForm = defaultForm(template);
                    configGuideBean.getFormData().putAll(defaultForm);
                    configGuideBean.setSelectedTemplate(template);
                    storedConfiguration.setTemplate(template);
                    {   
                        final String settingValue = AppProperty.LDAP_PROMISCUOUS_ENABLE.getKey() + "=true";
                                storedConfiguration.writeSetting(
                                        PwmSetting.APP_PROPERTY_OVERRIDES,
                                        new StringArrayValue(Collections.singletonList(settingValue)),
                                null);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("unknown template set request: " + e.getMessage());
            }
        }



        final RestResultBean restResultBean = new RestResultBean();
        pwmRequest.outputJsonResult(restResultBean);
        convertFormToConfiguration(storedConfiguration, configGuideBean.getFormData(), incomingFormData);
        //LOGGER.info("config: " + storedConfiguration.toString());
    }

    private void restGotoStep(final PwmRequest pwmRequest, final ConfigGuideBean configGuideBean)
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final String requestedStep = pwmRequest.readParameterAsString("step");
        STEP step = null;
        if (requestedStep != null && requestedStep.length() > 0) {
            try {
                step = STEP.valueOf(requestedStep);
            } catch (IllegalArgumentException e) { /* */ }
        }

        final boolean ldapSchemaPermitted = "LDAP".equals(configGuideBean.getFormData().get(PARAM_CR_STORAGE_PREF))
                && configGuideBean.getSelectedTemplate() == PwmSetting.Template.NOVL;

        if ("NEXT".equals(requestedStep)) {
            step = configGuideBean.getStep().next();
            if (step == STEP.LDAP_SCHEMA && !ldapSchemaPermitted) {
                step = step.next();
            }
        } else if ("PREVIOUS".equals(requestedStep)) {
            step = configGuideBean.getStep().previous();
            if (step == STEP.LDAP_SCHEMA && !ldapSchemaPermitted) {
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

        if (step == STEP.FINISH) {
            final ContextManager contextManager = ContextManager.getContextManager(pwmRequest);
            try {
                writeConfig(contextManager, configGuideBean);
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

    public static void convertFormToConfiguration(
            final StoredConfiguration storedConfiguration,
            final Map<String, String> ldapForm,
            final Map<String, String> incomingLdapForm
    )
            throws PwmUnrecoverableException
    {
        {
            final String ldapServerIP = ldapForm.get(PARAM_LDAP_HOST);
            final String ldapServerPort = ldapForm.get(PARAM_LDAP_PORT);
            final boolean ldapServerSecure = "true".equalsIgnoreCase(ldapForm.get(PARAM_LDAP_SECURE));

            final String newLdapURI = "ldap" + (ldapServerSecure ? "s" : "") +  "://" + ldapServerIP + ":" + ldapServerPort;
            final StringArrayValue newValue = new StringArrayValue(Collections.singletonList(newLdapURI));
            storedConfiguration.writeSetting(PwmSetting.LDAP_SERVER_URLS, LDAP_PROFILE_KEY, newValue, null);
        }

        { // proxy/admin account
            final String ldapAdminDN = ldapForm.get(PARAM_LDAP_ADMIN_DN);
            final String ldapAdminPW = ldapForm.get(PARAM_LDAP_ADMIN_PW);
            storedConfiguration.writeSetting(PwmSetting.LDAP_PROXY_USER_DN, LDAP_PROFILE_KEY, new StringValue(ldapAdminDN), null);
            final PasswordValue passwordValue = new PasswordValue(PasswordData.forStringValue(ldapAdminPW));
            storedConfiguration.writeSetting(PwmSetting.LDAP_PROXY_USER_PASSWORD, LDAP_PROFILE_KEY, passwordValue, null);
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
        storedConfiguration.writeSetting(PwmSetting.LDAP_CONTEXTLESS_ROOT, LDAP_PROFILE_KEY, new StringArrayValue(Collections.singletonList(ldapForm.get(PARAM_LDAP2_CONTEXT))), null);

        {  // set context based on ldap dn
            final String ldapContext = ldapForm.get(PARAM_LDAP2_CONTEXT);
            storedConfiguration.writeSetting(PwmSetting.LDAP_CONTEXTLESS_ROOT, LDAP_PROFILE_KEY, new StringArrayValue(Collections.singletonList(ldapContext)), null);
        }

        {  // set context based on ldap dn
            final String ldapTestUserDN = ldapForm.get(PARAM_LDAP2_TEST_USER);
            storedConfiguration.writeSetting(PwmSetting.LDAP_TEST_USER_DN, LDAP_PROFILE_KEY, new StringValue(ldapTestUserDN), null);
        }

        {  // set admin query
            final String groupDN = ldapForm.get(PARAM_LDAP2_ADMIN_GROUP);
            final List<UserPermission> userPermissions = Collections.singletonList(new UserPermission(UserPermission.Type.ldapGroup, null, null, groupDN));
            storedConfiguration.writeSetting(PwmSetting.QUERY_MATCH_PWM_ADMIN, new UserPermissionValue(userPermissions), null);
        }

        // set context based on ldap dn
        if (incomingLdapForm.containsKey(PARAM_APP_SITEURL)) {
            ldapForm.put(PARAM_APP_SITEURL, incomingLdapForm.get(PARAM_APP_SITEURL));
        }
        storedConfiguration.writeSetting(PwmSetting.PWM_SITE_URL, new StringValue(ldapForm.get(PARAM_APP_SITEURL)), null);
    }

    private void writeConfig(
            final ContextManager contextManager,
            final ConfigGuideBean configGuideBean
    ) throws PwmOperationalException, PwmUnrecoverableException {
        final StoredConfiguration storedConfiguration = configGuideBean.getStoredConfiguration();
        final String configPassword = configGuideBean.getFormData().get(PARAM_CONFIG_PASSWORD);
        if (configPassword != null && configPassword.length() > 0) {
            storedConfiguration.setPassword(configPassword);
        } else {
            storedConfiguration.writeConfigProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_PASSWORD_HASH, null);
        }

        { // determine Cr Preference setting.
            final String crPref = configGuideBean.getFormData().get(PARAM_CR_STORAGE_PREF);
            if (crPref != null && crPref.length() > 0) {
                storedConfiguration.writeSetting(PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE, new StringValue(crPref), null);
                storedConfiguration.writeSetting(PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE, new StringValue(crPref), null);
            }
        }

        storedConfiguration.readSetting(PwmSetting.APP_PROPERTY_OVERRIDES);
        writeConfig(contextManager, storedConfiguration);
    }

    private static void writeConfig(
            final ContextManager contextManager,
            final StoredConfiguration storedConfiguration
    ) throws PwmOperationalException, PwmUnrecoverableException {
        ConfigurationReader configReader = contextManager.getConfigReader();
        PwmApplication pwmApplication = contextManager.getPwmApplication();

        try {
            // add a random security key
            storedConfiguration.initNewRandomSecurityKey();

            storedConfiguration.writeConfigProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_CONFIG_IS_EDITABLE, "true");
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
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();
        final ServletContext servletContext = req.getSession().getServletContext();
        final ConfigGuideBean configGuideBean = pwmRequest.getPwmSession().getSessionBean(ConfigGuideBean.class);
        String destURL = '/' + PwmConstants.URL_JSP_CONFIG_GUIDE;
        destURL = destURL.replace("%1%", configGuideBean.getStep().toString().toLowerCase());
        servletContext.getRequestDispatcher(destURL).forward(req, pwmRequest.getPwmResponse().getHttpServletResponse());
    }

    public static SchemaOperationResult extendSchema(ConfigGuideBean configGuideBean, final boolean doSchemaExtension) {
        final Map<String,String> form = configGuideBean.getFormData();
        final boolean ldapServerSecure = "true".equalsIgnoreCase(form.get(PARAM_LDAP_SECURE));
        final String ldapUrl = "ldap" + (ldapServerSecure ? "s" : "") + "://" + form.get(PARAM_LDAP_HOST) + ":" + form.get(PARAM_LDAP_PORT);
        try {
            final ChaiConfiguration chaiConfiguration = new ChaiConfiguration(ldapUrl, form.get(PARAM_LDAP_ADMIN_DN), form.get(PARAM_LDAP_ADMIN_PW));
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
}
