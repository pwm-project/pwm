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
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import password.pwm.*;
import password.pwm.config.Configuration;
import password.pwm.config.ConfigurationReader;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.http.*;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.i18n.Config;
import password.pwm.i18n.Display;
import password.pwm.i18n.LocaleHelper;
import password.pwm.i18n.Message;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.util.*;
import password.pwm.util.intruder.RecordType;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBFactory;
import password.pwm.util.localdb.LocalDBUtility;
import password.pwm.util.logging.LocalDBLogger;
import password.pwm.util.logging.PwmLogEvent;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.crypto.SecretKey;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ConfigManagerServlet extends PwmServlet {
    final static private PwmLogger LOGGER = PwmLogger.forClass(ConfigManagerServlet.class);

    private static final String CONFIGMANAGER_INTRUDER_USERNAME = "ConfigurationManagerLogin";

    public enum ConfigManagerAction implements ProcessAction {
        lockConfiguration(HttpMethod.POST),
        startEditing(HttpMethod.POST),
        downloadConfig(HttpMethod.GET),
        exportLocalDB(HttpMethod.GET),
        generateSupportZip(HttpMethod.GET),
        uploadConfig(HttpMethod.POST),
        importLocalDB(HttpMethod.POST),
        summary(HttpMethod.GET),
        viewLog(HttpMethod.GET),
        
        ;

        private final HttpMethod method;

        ConfigManagerAction(HttpMethod method)
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods()
        {
            return Collections.singletonList(method);
        }
    }

    protected ConfigManagerAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        try {
            return ConfigManagerAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();

        if (checkAuthentication(pwmRequest, configManagerBean)) {
            return;
        }

        configManagerBean.setConfigLocked(pwmApplication.getApplicationMode() != PwmApplication.MODE.CONFIGURATION);

        final ConfigManagerAction processAction = readProcessAction(pwmRequest);
        if (processAction != null) {
            switch (processAction) {
                case lockConfiguration:
                    restLockConfiguration(pwmRequest);
                    break;

                case startEditing:
                    doStartEditing(pwmRequest);
                    break;

                case downloadConfig:
                    doDownloadConfig(pwmRequest);
                    break;

                case exportLocalDB:
                    doExportLocalDB(pwmRequest);
                    break;

                case generateSupportZip:
                    doGenerateSupportZip(pwmRequest);
                    break;

                case uploadConfig:
                    ConfigGuideServlet.restUploadConfig(pwmRequest);
                    return;

                case importLocalDB:
                    restUploadLocalDB(pwmRequest);
                    return;

                case summary:
                    restSummary(pwmRequest);
                    return;
            }
            return;
        }

        initRequestAttributes(pwmRequest);
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.CONFIG_MANAGER_MODE_CONFIGURATION);
    }

    void initRequestAttributes(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException
    {
        final ConfigurationReader configurationReader = pwmRequest.getContextManager().getConfigReader();
        pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.PageTitle,LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, pwmRequest));
        pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.ApplicationPath, pwmRequest.getPwmApplication().getApplicationPath().getAbsolutePath());
        pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.ConfigFilename, configurationReader.getConfigFile().getAbsolutePath());
        {
            final Date lastModifyTime = configurationReader.getStoredConfiguration().modifyTime();
            final String output = lastModifyTime == null
                    ? LocaleHelper.getLocalizedMessage(Display.Value_NotApplicable,pwmRequest)
                    : PwmConstants.DEFAULT_DATETIME_FORMAT.format(lastModifyTime);
            pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.ConfigLastModified, output);
        }
        pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.ConfigHasPassword, LocaleHelper.booleanString(configurationReader.getStoredConfiguration().hasPassword(),pwmRequest.getLocale(),pwmRequest.getConfig()));
    }

    void restUploadLocalDB(final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException

    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();

        if (pwmApplication.getApplicationMode() == PwmApplication.MODE.RUNNING) {
            final String errorMsg = "database upload is not permitted when in running mode";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_UPLOAD_FAILURE,errorMsg,new String[]{errorMsg});
            pwmRequest.respondWithError(errorInformation, true);
            return;
        }

        if (!ServletFileUpload.isMultipartContent(req)) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,"no file found in upload");
            pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
            LOGGER.error(pwmRequest, "error during database import: " + errorInformation.toDebugStr());
            return;
        }

        final InputStream inputStream = ServletHelper.readFileUpload(pwmRequest.getHttpServletRequest(),"uploadFile");

        final ContextManager contextManager = ContextManager.getContextManager(pwmRequest);
        LocalDB localDB = null;
        try {
            final File localDBLocation = pwmApplication.getLocalDB().getFileLocation();
            final Configuration configuration = pwmApplication.getConfig();
            contextManager.shutdown();

            localDB = LocalDBFactory.getInstance(localDBLocation, false, null, configuration);
            final LocalDBUtility localDBUtility = new LocalDBUtility(localDB);
            LOGGER.info(pwmRequest, "beginning LocalDB import");
            localDBUtility.importLocalDB(inputStream,
                    LOGGER.asAppendable(PwmLogLevel.DEBUG, pwmRequest.getSessionLabel()));
            LOGGER.info(pwmRequest, "completed LocalDB import");
        } catch (Exception e) {
            final ErrorInformation errorInformation = e instanceof PwmException
                    ? ((PwmException) e).getErrorInformation()
                    : new ErrorInformation(PwmError.ERROR_UNKNOWN,e.getMessage());
            pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
            LOGGER.error(pwmRequest, "error during LocalDB import: " + errorInformation.toDebugStr());
            return;
        } finally {
            if (localDB != null) {
                try {
                    localDB.close();
                } catch (Exception e) {
                    LOGGER.error(pwmRequest, "error closing LocalDB after import process: " + e.getMessage());
                }
            }
            contextManager.initialize();
        }

        pwmRequest.outputJsonResult(RestResultBean.forSuccessMessage(pwmRequest, Message.Success_Unknown));
    }

    static boolean checkAuthentication(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ConfigurationReader runningConfigReader = ContextManager.getContextManager(pwmRequest.getHttpServletRequest().getSession()).getConfigReader();
        final StoredConfiguration storedConfig = runningConfigReader.getStoredConfiguration();

        boolean authRequired = false;
        if (storedConfig.hasPassword()) {
            authRequired = true;
        }

        if (PwmApplication.MODE.RUNNING == pwmRequest.getPwmApplication().getApplicationMode()) {
            if (!pwmSession.getSessionStateBean().isAuthenticated()) {
                throw new PwmUnrecoverableException(PwmError.ERROR_AUTHENTICATION_REQUIRED);
            }

            if (pwmSession.getLoginInfoBean().getAuthenticationType() != AuthenticationType.AUTHENTICATED) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_AUTHENTICATION_REQUIRED,
                        "Username/Password authentication is required to edit configuration.  This session has not been authenticated using a user password (SSO or other method used)."));
            }
        }

        if (PwmApplication.MODE.CONFIGURATION != pwmRequest.getPwmApplication().getApplicationMode()) {
            authRequired = true;
        }

        if (!authRequired) {
            return false;
        }

        if (!storedConfig.hasPassword()) {
            final String errorMsg = "config file does not have a configuration password";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg,new String[]{errorMsg});
            pwmRequest.respondWithError(errorInformation, true);
            return true;
        }

        if (configManagerBean.isPasswordVerified()) {
            return false;
        }

        String persistentLoginValue = null;
        boolean persistentLoginAccepted = false;
        boolean persistentLoginEnabled = false;
        if (pwmRequest.getConfig().isDefaultValue(PwmSetting.PWM_SECURITY_KEY)) {
            LOGGER.debug(pwmRequest, "security not available, persistent login not possible.");
        } else {
            persistentLoginEnabled = true;
            final SecretKey securityKey = pwmRequest.getConfig().getSecurityKey();

            if (PwmApplication.MODE.RUNNING == pwmRequest.getPwmApplication().getApplicationMode()) {
                persistentLoginValue = SecureHelper.hash(
                        storedConfig.readConfigProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_PASSWORD_HASH)
                                + pwmSession.getUserInfoBean().getUserIdentity().toDelimitedKey(),
                        SecureHelper.DEFAULT_HASH_ALGORITHM);

            } else {
                persistentLoginValue = SecureHelper.hash(
                        storedConfig.readConfigProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_PASSWORD_HASH),
                        SecureHelper.DEFAULT_HASH_ALGORITHM);
            }

            {
                final String cookieStr = ServletHelper.readCookie(
                        pwmRequest.getHttpServletRequest(),
                        PwmConstants.COOKIE_PERSISTENT_CONFIG_LOGIN
                );
                if (securityKey != null && cookieStr != null && !cookieStr.isEmpty()) {
                    try {
                        final String jsonStr = SecureHelper.decryptStringValue(cookieStr, securityKey);
                        final PersistentLoginInfo persistentLoginInfo = JsonUtil.deserialize(jsonStr, PersistentLoginInfo.class);
                        if (persistentLoginInfo != null && persistentLoginValue != null) {
                            if (persistentLoginInfo.getExpireDate().after(new Date())) {
                                if (persistentLoginValue.equals(persistentLoginInfo.getPassword())) {
                                    persistentLoginAccepted = true;
                                    LOGGER.debug(pwmRequest, "accepting persistent config login from cookie (expires "
                                                    + PwmConstants.DEFAULT_DATETIME_FORMAT.format(persistentLoginInfo.getExpireDate())
                                                    + ")"
                                    );
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error(pwmRequest, "error examining persistent config login cookie: " + e.getMessage());
                    }
                    if (!persistentLoginAccepted) {
                        Cookie removalCookie = new Cookie(PwmConstants.COOKIE_PERSISTENT_CONFIG_LOGIN, null);
                        removalCookie.setMaxAge(0);
                        pwmRequest.getPwmResponse().addCookie(removalCookie);
                        LOGGER.debug(pwmRequest, "removing non-working persistent config login cookie");
                    }
                }
            }
        }


        final String password = pwmRequest.readParameterAsString("password");
        boolean passwordAccepted = false;
        if (!persistentLoginAccepted) {
            if (password != null && password.length() > 0) {
                if (storedConfig.verifyPassword(password)) {
                    passwordAccepted = true;
                    LOGGER.trace(pwmRequest, "valid configuration password accepted");
                } else{
                    LOGGER.trace(pwmRequest, "configuration password is not correct");
                    pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
                    pwmApplication.getIntruderManager().mark(RecordType.USERNAME, CONFIGMANAGER_INTRUDER_USERNAME, pwmSession.getLabel());
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_WRONGPASSWORD);
                    pwmRequest.setResponseError(errorInformation);
                }
            }
        }

        if ((persistentLoginAccepted || passwordAccepted)) {
            configManagerBean.setPasswordVerified(true);
            pwmApplication.getIntruderManager().convenience().clearAddressAndSession(pwmSession);
            pwmApplication.getIntruderManager().clear(RecordType.USERNAME,CONFIGMANAGER_INTRUDER_USERNAME);
            if (persistentLoginEnabled && !persistentLoginAccepted && "on".equals(pwmRequest.readParameterAsString("remember"))) {
                final int persistentSeconds = Integer.parseInt(pwmRequest.getConfig().readAppProperty(AppProperty.CONFIG_MAX_PERSISTENT_LOGIN_SECONDS));
                if (persistentSeconds > 0) {
                    final Date expirationDate = new Date(System.currentTimeMillis() + (persistentSeconds * 1000));
                    final PersistentLoginInfo persistentLoginInfo = new PersistentLoginInfo(expirationDate, persistentLoginValue);
                    final String jsonPersistentLoginInfo = JsonUtil.serialize(persistentLoginInfo);
                    final String cookieValue = SecureHelper.encryptToString(jsonPersistentLoginInfo,
                            pwmRequest.getConfig().getSecurityKey());
                    pwmRequest.getPwmResponse().writeCookie(
                            PwmConstants.COOKIE_PERSISTENT_CONFIG_LOGIN,
                            cookieValue,
                            persistentSeconds,
                            true
                    );
                    LOGGER.debug(pwmRequest, "set persistent config login cookie (expires "
                                    + PwmConstants.DEFAULT_DATETIME_FORMAT.format(expirationDate)
                                    + ")"
                    );
                }
            }

            if (configManagerBean.getPrePasswordEntryUrl() != null) {
                final String originalUrl = configManagerBean.getPrePasswordEntryUrl();
                configManagerBean.setPrePasswordEntryUrl(null);
                pwmRequest.getPwmResponse().sendRedirect(originalUrl);
                return true;
            }
            return false;
        }

        if (configManagerBean.getPrePasswordEntryUrl() == null) {
            configManagerBean.setPrePasswordEntryUrl(pwmRequest.getHttpServletRequest().getRequestURL().toString());
        }
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.CONFIG_MANAGER_LOGIN);
        return true;
    }

    private void doStartEditing(final PwmRequest pwmRequest)
            throws IOException, PwmUnrecoverableException, ServletException
    {
        forwardToEditor(pwmRequest);
    }


    private void restLockConfiguration(final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        if (PwmConstants.TRIAL_MODE) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_TRIAL_VIOLATION,"configuration lock not available in trial");
            final RestResultBean restResultBean = RestResultBean.fromError(errorInfo, pwmRequest);
            LOGGER.debug(pwmSession, errorInfo);
            pwmRequest.outputJsonResult(restResultBean);
            return;
        }

        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_AUTHENTICATION_REQUIRED,"You must be authenticated before locking the configuration");
            final RestResultBean restResultBean = RestResultBean.fromError(errorInfo, pwmRequest);
            LOGGER.debug(pwmSession, errorInfo);
            pwmRequest.outputJsonResult(restResultBean);
            return;
        }

        if (!pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.PWMADMIN)) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"You must be authenticated with admin privileges before locking the configuration");
            final RestResultBean restResultBean = RestResultBean.fromError(errorInfo, pwmRequest);
            LOGGER.debug(pwmSession, errorInfo);
            pwmRequest.outputJsonResult(restResultBean);
            return;
        }

        try {
            final StoredConfiguration storedConfiguration = readCurrentConfiguration(pwmRequest);
            if (!storedConfiguration.hasPassword()) {
                final ErrorInformation errorInfo = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,null,new String[]{"Please set a configuration password before locking the configuration"});
                final RestResultBean restResultBean = RestResultBean.fromError(errorInfo, pwmRequest);
                LOGGER.debug(pwmSession, errorInfo);
                pwmRequest.outputJsonResult(restResultBean);
                return;
            }

            storedConfiguration.writeConfigProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_CONFIG_IS_EDITABLE, "false");
            saveConfiguration(pwmRequest, storedConfiguration);
            final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();
            configManagerBean.setConfiguration(null);
        } catch (PwmException e) {
            final ErrorInformation errorInfo = e.getErrorInformation();
            final RestResultBean restResultBean = RestResultBean.fromError(errorInfo, pwmRequest);
            LOGGER.debug(pwmSession, errorInfo.toDebugStr());
            pwmRequest.outputJsonResult(restResultBean);
            return;
        } catch (Exception e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_UNKNOWN,e.getMessage());
            final RestResultBean restResultBean = RestResultBean.fromError(errorInfo, pwmRequest);
            LOGGER.debug(pwmSession, errorInfo.toDebugStr());
            pwmRequest.outputJsonResult(restResultBean);
            return;
        }
        final HashMap<String,String> resultData = new HashMap<>();
        LOGGER.info(pwmSession, "Configuration Locked");
        pwmRequest.outputJsonResult(new RestResultBean(resultData));
    }

    static void saveConfiguration(
            final PwmRequest pwmRequest,
            final StoredConfiguration storedConfiguration
    )
            throws PwmUnrecoverableException
    {
        {
            final List<String> errorStrings = storedConfiguration.validateValues();
            if (errorStrings != null && !errorStrings.isEmpty()) {
                final String errorString = errorStrings.get(0);
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,null,new String[]{errorString}));
            }
        }

        try {
            ContextManager contextManager = ContextManager.getContextManager(pwmRequest.getHttpServletRequest().getSession().getServletContext());
            contextManager.getConfigReader().saveConfiguration(storedConfiguration, contextManager.getPwmApplication(), pwmRequest.getSessionLabel());
            contextManager.requestPwmApplicationRestart();
        } catch (Exception e) {
            final String errorString = "error saving file: " + e.getMessage();
            LOGGER.error(pwmRequest, errorString);
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,null,new String[]{errorString}));
        }

    }

    static void forwardToEditor(final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final String url = pwmRequest.getHttpServletRequest().getContextPath() + "/private/config/ConfigEditor";
        pwmRequest.sendRedirect(url);
    }

    private void doDownloadConfig(final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmResponse resp = pwmRequest.getPwmResponse();

        try {
            final StoredConfiguration storedConfiguration = readCurrentConfiguration(pwmRequest);
            final OutputStream responseWriter = resp.getOutputStream();
            resp.setHeader(PwmConstants.HttpHeader.ContentDisposition, "attachment;filename=" + PwmConstants.DEFAULT_CONFIG_FILE_FILENAME);
            resp.setContentType(PwmConstants.ContentTypeValue.xml);
            storedConfiguration.toXml(responseWriter);
            responseWriter.close();
        } catch (Exception e) {
            LOGGER.error(pwmSession, "unable to download configuration: " + e.getMessage());
        }
    }

    private void doGenerateSupportZip(final PwmRequest pwmRequest)
            throws IOException, ServletException
    {
        final PwmResponse resp = pwmRequest.getPwmResponse();
        resp.setHeader(PwmConstants.HttpHeader.ContentDisposition, "attachment;filename=" + PwmConstants.PWM_APP_NAME + "-Support.zip");
        resp.setContentType(PwmConstants.ContentTypeValue.zip);

        final String pathPrefix = PwmConstants.PWM_APP_NAME + "-Support" + "/";

        ZipOutputStream zipOutput = null;
        try {
            zipOutput = new ZipOutputStream(resp.getOutputStream(), PwmConstants.DEFAULT_CHARSET);
            outputZipDebugFile(pwmRequest, zipOutput, pathPrefix);
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "error during zip debug building: " + e.getMessage());
        } finally {
            if (zipOutput != null) {
                try {
                    zipOutput.close();
                } catch (Exception e) {
                    LOGGER.error(pwmRequest, "error during zip debug closing: " + e.getMessage());
                }
            }
        }

    }

    private void outputZipDebugFile(
            final PwmRequest pwmRequest,
            final ZipOutputStream zipOutput,
            final String pathPrefix
    )
            throws IOException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        { // kick off health check so that it might be faster later..
            Thread healthThread = new Thread() {
                public void run() {
                    pwmApplication.getHealthMonitor().getHealthRecords();
                }
            };
            healthThread.setName(Helper.makeThreadName(pwmApplication, ConfigManagerServlet.class) + "-HealthCheck");
            healthThread.setDaemon(true);
            healthThread.start();
        }
        final StoredConfiguration storedConfiguration = readCurrentConfiguration(pwmRequest);
        storedConfiguration.resetAllPasswordValues("value removed from " + PwmConstants.PWM_APP_NAME + "-Support configuration export");
        {
            zipOutput.putNextEntry(new ZipEntry(pathPrefix + PwmConstants.DEFAULT_CONFIG_FILE_FILENAME));
            storedConfiguration.resetAllPasswordValues("value removed from " + PwmConstants.PWM_APP_NAME + "-Support configuration export");

            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            storedConfiguration.toXml(outputStream);
            zipOutput.write(outputStream.toByteArray());
            zipOutput.closeEntry();
            zipOutput.flush();
        }
        {
            zipOutput.putNextEntry(new ZipEntry(pathPrefix + "configuration-debug.txt"));
            zipOutput.write(storedConfiguration.toString(true).getBytes(PwmConstants.DEFAULT_CHARSET));
            zipOutput.closeEntry();
            zipOutput.flush();
        }
        {
            final String aboutJson = JsonUtil.serialize(AdminServlet.makeInfoBean(pwmApplication), JsonUtil.Flag.PrettyPrint);
            zipOutput.putNextEntry(new ZipEntry(pathPrefix + "about.json"));
            zipOutput.write(aboutJson.getBytes(PwmConstants.DEFAULT_CHARSET));
            zipOutput.closeEntry();
            zipOutput.flush();
        }
        {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pwmApplication.getAuditManager().outputVaultToCsv(baos, pwmRequest.getLocale(), true);
            zipOutput.putNextEntry(new ZipEntry(pathPrefix + "audit.csv"));
            zipOutput.write(baos.toByteArray());
            zipOutput.closeEntry();
            zipOutput.flush();
        }
        {
            zipOutput.putNextEntry(new ZipEntry(pathPrefix + "info.json"));
            final LinkedHashMap<String,Object> outputMap = new LinkedHashMap<>();

            { // services info
                final LinkedHashMap<String,Object> servicesMap = new LinkedHashMap<>();
                for (final PwmService service : pwmApplication.getPwmServices()) {
                    final LinkedHashMap<String,Object> serviceOutput = new LinkedHashMap<>();
                    serviceOutput.put("name", service.getClass().getSimpleName());
                    serviceOutput.put("status",service.status());
                    serviceOutput.put("health",service.healthCheck());
                    serviceOutput.put("serviceInfo",service.serviceInfo());
                    servicesMap.put(service.getClass().getSimpleName(), serviceOutput);
                }
                outputMap.put("services",servicesMap);
            }

            // java threads
            outputMap.put("threads",Thread.getAllStackTraces());

            final String recordJson = JsonUtil.serializeMap(outputMap, JsonUtil.Flag.PrettyPrint);
            zipOutput.write(recordJson.getBytes(PwmConstants.DEFAULT_CHARSET));
            zipOutput.closeEntry();
            zipOutput.flush();
        }
        if (pwmApplication.getApplicationPath() != null) {
            try {
                zipOutput.putNextEntry(new ZipEntry(pathPrefix + "fileMd5sums.json"));
                final Map<String,String> fileChecksums = BuildChecksumMaker.readDirectorySums(pwmApplication.getApplicationPath());
                final String json = JsonUtil.serializeMap(fileChecksums, JsonUtil.Flag.PrettyPrint);
                zipOutput.write(json.getBytes(PwmConstants.DEFAULT_CHARSET));
                zipOutput.closeEntry();
                zipOutput.flush();
            } catch (Exception e) {
                LOGGER.error(pwmSession,"unable to generate fileMd5sums during zip debug building: " + e.getMessage());
            }
        }
        {
            zipOutput.putNextEntry(new ZipEntry(pathPrefix + "debug.log"));
            final int maxCount = 100 * 1000;
            final int maxSeconds = 30 * 1000;
            final LocalDBLogger.SearchParameters searchParameters = new LocalDBLogger.SearchParameters(
                    PwmLogLevel.TRACE,
                    maxCount,
                    null,
                    null,
                    maxSeconds,
                    null
            );
            final LocalDBLogger.SearchResults searchResults = pwmApplication.getLocalDBLogger().readStoredEvents(
                    searchParameters);
            int counter = 0;
            while (searchResults.hasNext()) {
                final PwmLogEvent event = searchResults.next();
                zipOutput.write(event.toLogString().getBytes(PwmConstants.DEFAULT_CHARSET));
                zipOutput.write("\n".getBytes(PwmConstants.DEFAULT_CHARSET));
                counter++;
                if (counter % 100 == 0) {
                    zipOutput.flush();
                }
            }
            zipOutput.closeEntry();
        }
        {
            zipOutput.putNextEntry(new ZipEntry(pathPrefix + "health.json"));
            final Set<HealthRecord> records = pwmApplication.getHealthMonitor().getHealthRecords();
            final String recordJson = JsonUtil.serializeCollection(records, JsonUtil.Flag.PrettyPrint);
            zipOutput.write(recordJson.getBytes(PwmConstants.DEFAULT_CHARSET));
            zipOutput.closeEntry();
            zipOutput.flush();
        }
    }

    static StoredConfiguration readCurrentConfiguration(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException
    {
        final ContextManager contextManager = ContextManager.getContextManager(pwmRequest.getHttpServletRequest().getSession());
        final ConfigurationReader runningConfigReader = contextManager.getConfigReader();
        final StoredConfiguration runningConfig = runningConfigReader.getStoredConfiguration();
        return StoredConfiguration.copy(runningConfig);
    }

    private void doExportLocalDB(final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmResponse resp = pwmRequest.getPwmResponse();
        final Date startTime = new Date();
        resp.setHeader(PwmConstants.HttpHeader.ContentDisposition, "attachment;filename=" + PwmConstants.PWM_APP_NAME + "-LocalDB.bak");
        resp.setContentType(PwmConstants.ContentTypeValue.octetstream);
        resp.setHeader(PwmConstants.HttpHeader.ContentTransferEncoding, "binary");
        final LocalDBUtility localDBUtility = new LocalDBUtility(pwmRequest.getPwmApplication().getLocalDB());
        try {
            localDBUtility.exportLocalDB(resp.getOutputStream(), LOGGER.asAppendable(PwmLogLevel.DEBUG, pwmRequest.getSessionLabel()), false);
            LOGGER.debug(pwmRequest, "completed localDBExport process in " + TimeDuration.fromCurrent(startTime).asCompactString());
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "error downloading export localdb: " + e.getMessage());
        }
    }

    private static class PersistentLoginInfo implements Serializable {
        private Date expireDate;
        private String password;

        private PersistentLoginInfo(
                Date expireDate,
                String password
        )
        {
            this.expireDate = expireDate;
            this.password = password;
        }

        public Date getExpireDate()
        {
            return expireDate;
        }

        public String getPassword()
        {
            return password;
        }
    }


    private void restSummary(final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final StoredConfiguration storedConfiguration = readCurrentConfiguration(pwmRequest);
        final LinkedHashMap<String,Object> outputMap = new LinkedHashMap<>(storedConfiguration.toOutputMap(pwmRequest.getLocale()));
        pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.ConfigurationSummaryOutput,outputMap);
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.CONFIG_MANAGER_EDITOR_SUMMARY);
    }

    private void processViewLog(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException
    {

        final PwmApplication.MODE configMode = pwmRequest.getPwmApplication().getApplicationMode();
        if (configMode != PwmApplication.MODE.CONFIGURATION) {
            if (!pwmRequest.getPwmSession().getSessionManager().checkPermission(pwmRequest.getPwmApplication(), Permission.PWMADMIN)) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,"admin permission required"));
            }
        }
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.ADMIN_LOGVIEW_WINDOW);
    }


}

