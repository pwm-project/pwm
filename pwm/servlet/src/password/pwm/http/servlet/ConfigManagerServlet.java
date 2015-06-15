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
import org.apache.commons.csv.CSVPrinter;
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
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
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
        pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.ConfigHasPassword, LocaleHelper.booleanString(configurationReader.getStoredConfiguration().hasPassword(), pwmRequest.getLocale(), pwmRequest.getConfig()));
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

        final int persistentSeconds = Integer.parseInt(pwmRequest.getConfig().readAppProperty(AppProperty.CONFIG_MAX_PERSISTENT_LOGIN_SECONDS));
        if ((persistentLoginAccepted || passwordAccepted)) {
            configManagerBean.setPasswordVerified(true);
            pwmApplication.getIntruderManager().convenience().clearAddressAndSession(pwmSession);
            pwmApplication.getIntruderManager().clear(RecordType.USERNAME,CONFIGMANAGER_INTRUDER_USERNAME);
            if (persistentLoginEnabled && !persistentLoginAccepted && "on".equals(pwmRequest.readParameterAsString("remember"))) {
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

        final String time = new TimeDuration(persistentSeconds * 1000).asLongString(pwmRequest.getLocale());
        pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.ConfigPasswordRememberTime,time);
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

        for (final Class<? extends DebugItemGenerator> serviceClass : DEBUG_ZIP_ITEM_GENERATORS) {
            try {
                LOGGER.trace(pwmRequest, "beginning debug output of item " + serviceClass.getSimpleName());
                final Object newInstance = serviceClass.newInstance();
                final DebugItemGenerator newGeneratorItem = (DebugItemGenerator)newInstance;
                zipOutput.putNextEntry(new ZipEntry(pathPrefix + newGeneratorItem.getFilename()));
                newGeneratorItem.outputItem(pwmApplication, pwmRequest, zipOutput);
                zipOutput.closeEntry();
                zipOutput.flush();
            } catch (Exception e) {
                final String errorMsg = "unexpected error executing debug item output class '" + serviceClass.getName() + "', error: " + e.toString();
                LOGGER.error(pwmRequest, errorMsg);
            }
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

    private static final List<Class<? extends DebugItemGenerator>> DEBUG_ZIP_ITEM_GENERATORS  = Collections.unmodifiableList(Arrays.asList(
            ConfigurationFileItemGenerator.class,
            ConfigurationDebugItemGenerator.class,
            AboutItemGenerator.class,
            EnvironmentItemGenerator.class,
            AuditDebugItemGenerator.class,
            InfoDebugItemGenerator.class,
            HealthDebugItemGenerator.class,
            ThreadDumpDebugItemGenerator.class,
            FileInfoDebugItemGenerator.class,
            LogDebugItemGenerator.class
    ));

    interface DebugItemGenerator {

        String getFilename();

        void outputItem(
                PwmApplication pwmApplication,
                PwmRequest pwmRequest,
                OutputStream outputStream
        ) throws Exception;
    }

    static class ConfigurationDebugItemGenerator implements DebugItemGenerator {
        @Override
        public String getFilename() {
            return "configuration-debug.txt";
        }

        @Override
        public void outputItem(PwmApplication pwmApplication, PwmRequest pwmRequest, OutputStream outputStream) throws Exception
        {
            final StoredConfiguration storedConfiguration = readCurrentConfiguration(pwmRequest);
            storedConfiguration.resetAllPasswordValues("value removed from " + PwmConstants.PWM_APP_NAME + "-Support configuration export");

            outputStream.write(storedConfiguration.toString(true).getBytes(PwmConstants.DEFAULT_CHARSET));
        }
    }

    static class ConfigurationFileItemGenerator implements DebugItemGenerator {
        @Override
        public String getFilename() {
            return PwmConstants.DEFAULT_CONFIG_FILE_FILENAME;
        }

        @Override
        public void outputItem(PwmApplication pwmApplication, PwmRequest pwmRequest, OutputStream outputStream) throws Exception
        {
            final StoredConfiguration storedConfiguration = readCurrentConfiguration(pwmRequest);
            storedConfiguration.resetAllPasswordValues("value removed from " + PwmConstants.PWM_APP_NAME + "-Support configuration export");

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            storedConfiguration.toXml(baos);
            outputStream.write(baos.toByteArray());
        }
    }

    static class AboutItemGenerator implements DebugItemGenerator {
        @Override
        public String getFilename() {
            return "about.properties";
        }

        @Override
        public void outputItem(PwmApplication pwmApplication, PwmRequest pwmRequest, OutputStream outputStream) throws Exception
        {
            final Properties outputProps = new Properties() {
                public synchronized Enumeration<Object> keys() {
                    return Collections.enumeration(new TreeSet<>(super.keySet()));
                }
            };

            final Map<PwmAboutProperty,String> infoBean = Helper.makeInfoBean(pwmApplication);
            for (final PwmAboutProperty aboutProperty : infoBean.keySet()) {
                outputProps.put(aboutProperty.toString().replace("_","."), infoBean.get(aboutProperty));
            }
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            outputProps.store(baos,PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date()));
            outputStream.write(baos.toByteArray());
        }
    }

    static class EnvironmentItemGenerator implements DebugItemGenerator {
        @Override
        public String getFilename() {
            return "environment.properties";
        }

        @Override
        public void outputItem(PwmApplication pwmApplication, PwmRequest pwmRequest, OutputStream outputStream) throws Exception
        {
            final Properties outputProps = new Properties() {
                public synchronized Enumeration<Object> keys() {
                    return Collections.enumeration(new TreeSet<>(super.keySet()));
                }
            };

            // java threads
            final Map<String,String> envProps = System.getenv();
            for (final String key : envProps.keySet()) {
                outputProps.put(key, envProps.get(key));
            }
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            outputProps.store(baos,PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date()));
            outputStream.write(baos.toByteArray());
        }
    }


    static class AuditDebugItemGenerator implements DebugItemGenerator {
        @Override
        public String getFilename() {
            return "audit.csv";
        }

        @Override
        public void outputItem(PwmApplication pwmApplication, PwmRequest pwmRequest, OutputStream outputStream) throws Exception
        {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pwmApplication.getAuditManager().outputVaultToCsv(baos, pwmRequest.getLocale(), true);
            outputStream.write(baos.toByteArray());
        }
    }

    static class InfoDebugItemGenerator implements DebugItemGenerator {
        @Override
        public String getFilename() {
            return "info.json";
        }

        @Override
        public void outputItem(PwmApplication pwmApplication, PwmRequest pwmRequest, OutputStream outputStream) throws Exception
        {
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

            final String recordJson = JsonUtil.serializeMap(outputMap, JsonUtil.Flag.PrettyPrint);
            outputStream.write(recordJson.getBytes(PwmConstants.DEFAULT_CHARSET));
        }
    }


    static class HealthDebugItemGenerator implements DebugItemGenerator {
        @Override
        public String getFilename() {
            return "health.json";
        }

        @Override
        public void outputItem(PwmApplication pwmApplication, PwmRequest pwmRequest, OutputStream outputStream) throws Exception {
            final Set<HealthRecord> records = pwmApplication.getHealthMonitor().getHealthRecords();
            final String recordJson = JsonUtil.serializeCollection(records, JsonUtil.Flag.PrettyPrint);
            outputStream.write(recordJson.getBytes(PwmConstants.DEFAULT_CHARSET));
        }
    }

    static class ThreadDumpDebugItemGenerator implements DebugItemGenerator {
        @Override
        public String getFilename() {
            return "threads.txt";
        }

        @Override
        public void outputItem(PwmApplication pwmApplication, PwmRequest pwmRequest, OutputStream outputStream) throws Exception {

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final PrintWriter writer = new PrintWriter( new OutputStreamWriter(baos, PwmConstants.DEFAULT_CHARSET) );
            final ThreadInfo[] threads = ManagementFactory.getThreadMXBean().dumpAllThreads(true,true);
            for (final ThreadInfo threadInfo : threads) {
                writer.write(threadInfo.toString());
            }
            writer.flush();
            outputStream.write(baos.toByteArray());
        }
    }

    static class FileInfoDebugItemGenerator implements DebugItemGenerator {
        @Override
        public String getFilename() {
            return "fileinformation.csv";
        }

        @Override
        public void outputItem(PwmApplication pwmApplication, PwmRequest pwmRequest, OutputStream outputStream) throws Exception {
            final List<BuildChecksumMaker.FileInformation> fileInformations = new ArrayList<>();
            if (pwmApplication.getApplicationPath() != null) {
                try {
                    fileInformations.addAll(BuildChecksumMaker.readFileInformation(pwmApplication.getApplicationPath()));
                } catch (Exception e) {
                    LOGGER.error(pwmRequest, "unable to generate appPath fileMd5sums during zip debug building: " + e.getMessage());
                }
            }
            if (pwmApplication.getWebInfPath() != null && !pwmApplication.getWebInfPath().equals(pwmApplication.getApplicationPath())) {
                try {
                    fileInformations.addAll(BuildChecksumMaker.readFileInformation(pwmApplication.getWebInfPath()));
                } catch (Exception e) {
                    LOGGER.error(pwmRequest, "unable to generate webInfPath fileMd5sums during zip debug building: " + e.getMessage());
                }
            }
            {
                final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                final CSVPrinter csvPrinter = Helper.makeCsvPrinter(byteArrayOutputStream);
                {
                    final List<String> headerRow = new ArrayList<>();
                    headerRow.add("Filename");
                    headerRow.add("Filepath");
                    headerRow.add("Last Modified");
                    headerRow.add("Size");
                    headerRow.add("sha1sum");
                    csvPrinter.printRecord(headerRow);
                }
                for (final BuildChecksumMaker.FileInformation fileInformation : fileInformations) {
                    final List<String> headerRow = new ArrayList<>();
                    headerRow.add(fileInformation.getFilename());
                    headerRow.add(fileInformation.getFilepath());
                    headerRow.add(PwmConstants.DEFAULT_DATETIME_FORMAT.format(fileInformation.getModified()));
                    headerRow.add(String.valueOf(fileInformation.getSize()));
                    headerRow.add(fileInformation.getSha1sum());
                    csvPrinter.printRecord(headerRow);
                }
                csvPrinter.flush();
                outputStream.write(byteArrayOutputStream.toByteArray());
            }
        }
    }

    static class LogDebugItemGenerator implements DebugItemGenerator {
        @Override
        public String getFilename() {
            return "debug.log";
        }

        @Override
        public void outputItem(
                final PwmApplication pwmApplication,
                final PwmRequest pwmRequest,
                final OutputStream outputStream
        ) throws Exception {

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
                outputStream.write(event.toLogString().getBytes(PwmConstants.DEFAULT_CHARSET));
                outputStream.write("\n".getBytes(PwmConstants.DEFAULT_CHARSET));
                counter++;
                if (counter % 1000 == 0) {
                    outputStream.flush();
                }
            }
        }
    }
}

