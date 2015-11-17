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

package password.pwm.http.servlet.configmanager;

import com.novell.ldapchai.ChaiEntry;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.ChaiUtility;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import password.pwm.*;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.http.*;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.configguide.ConfigGuideServlet;
import password.pwm.i18n.Config;
import password.pwm.i18n.Display;
import password.pwm.i18n.Message;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.svc.PwmService;
import password.pwm.util.FileSystemUtility;
import password.pwm.util.Helper;
import password.pwm.util.JsonUtil;
import password.pwm.util.LocaleHelper;
import password.pwm.util.logging.LocalDBLogger;
import password.pwm.util.logging.PwmLogEvent;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@WebServlet(
        name = "ConfigManagerServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/config/manager",
                PwmConstants.URL_PREFIX_PRIVATE + "/config/ConfigManager"
        }
)
public class ConfigManagerServlet extends AbstractPwmServlet {
    final static private PwmLogger LOGGER = PwmLogger.forClass(ConfigManagerServlet.class);

    public enum ConfigManagerAction implements ProcessAction {
        lockConfiguration(HttpMethod.POST),
        startEditing(HttpMethod.POST),
        downloadConfig(HttpMethod.GET),
        generateSupportZip(HttpMethod.GET),
        uploadConfig(HttpMethod.POST),
        uploadWordlist(HttpMethod.POST),
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
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();

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

                case generateSupportZip:
                    doGenerateSupportZip(pwmRequest);
                    break;

                case uploadConfig:
                    ConfigGuideServlet.restUploadConfig(pwmRequest);
                    return;

                case uploadWordlist:
                    restUploadWordlist(pwmRequest);
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
        pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.ApplicationPath, pwmRequest.getPwmApplication().getPwmEnvironment().getApplicationPath().getAbsolutePath());
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

    void restUploadWordlist(final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException

    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();

        if (!ServletFileUpload.isMultipartContent(req)) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,"no file found in upload");
            pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
            LOGGER.error(pwmRequest, "error during import: " + errorInformation.toDebugStr());
            return;
        }

        final InputStream inputStream = ServletHelper.readFileUpload(pwmRequest.getHttpServletRequest(),"uploadFile");
        try {
            pwmApplication.getWordlistManager().populate(inputStream);
        } catch (PwmUnrecoverableException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_UNKNOWN, e.getMessage());
            final RestResultBean restResultBean = RestResultBean.fromError(errorInfo, pwmRequest);
            LOGGER.debug(pwmRequest, errorInfo.toDebugStr());
            pwmRequest.outputJsonResult(restResultBean);
            return;
        }

        pwmRequest.outputJsonResult(RestResultBean.forSuccessMessage(pwmRequest, Message.Success_Unknown));
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
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_AUTHENTICATION_REQUIRED,"You must be authenticated before restricting the configuration");
            final RestResultBean restResultBean = RestResultBean.fromError(errorInfo, pwmRequest);
            LOGGER.debug(pwmSession, errorInfo);
            pwmRequest.outputJsonResult(restResultBean);
            return;
        }

        if (!pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.PWMADMIN)) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"You must be authenticated with admin privileges before restricting the configuration");
            final RestResultBean restResultBean = RestResultBean.fromError(errorInfo, pwmRequest);
            LOGGER.debug(pwmSession, errorInfo);
            pwmRequest.outputJsonResult(restResultBean);
            return;
        }

        try {
            final StoredConfigurationImpl storedConfiguration = readCurrentConfiguration(pwmRequest);
            if (!storedConfiguration.hasPassword()) {
                final ErrorInformation errorInfo = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,null,new String[]{"Please set a configuration password before restricting the configuration"});
                final RestResultBean restResultBean = RestResultBean.fromError(errorInfo, pwmRequest);
                LOGGER.debug(pwmSession, errorInfo);
                pwmRequest.outputJsonResult(restResultBean);
                return;
            }

            storedConfiguration.writeConfigProperty(ConfigurationProperty.CONFIG_IS_EDITABLE, "false");
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

    public static void saveConfiguration(
            final PwmRequest pwmRequest,
            final StoredConfigurationImpl storedConfiguration
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
            final StoredConfigurationImpl storedConfiguration = readCurrentConfiguration(pwmRequest);
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

    public static StoredConfigurationImpl readCurrentConfiguration(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException
    {
        final ContextManager contextManager = ContextManager.getContextManager(pwmRequest.getHttpServletRequest().getSession());
        final ConfigurationReader runningConfigReader = contextManager.getConfigReader();
        final StoredConfigurationImpl runningConfig = runningConfigReader.getStoredConfiguration();
        return StoredConfigurationImpl.copy(runningConfig);
    }

    private void restSummary(final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final StoredConfigurationImpl storedConfiguration = readCurrentConfiguration(pwmRequest);
        final LinkedHashMap<String,Object> outputMap = new LinkedHashMap<>(storedConfiguration.toOutputMap(pwmRequest.getLocale()));
        pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.ConfigurationSummaryOutput,outputMap);
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.CONFIG_MANAGER_EDITOR_SUMMARY);
    }

    private static final List<Class<? extends DebugItemGenerator>> DEBUG_ZIP_ITEM_GENERATORS  = Collections.unmodifiableList(Arrays.asList(
            ConfigurationFileItemGenerator.class,
            ConfigurationDebugJsonItemGenerator.class,
            ConfigurationDebugTextItemGenerator.class,
            AboutItemGenerator.class,
            EnvironmentItemGenerator.class,
            AppPropertiesItemGenerator.class,
            AuditDebugItemGenerator.class,
            InfoDebugItemGenerator.class,
            HealthDebugItemGenerator.class,
            ThreadDumpDebugItemGenerator.class,
            FileInfoDebugItemGenerator.class,
            LogDebugItemGenerator.class,
            LdapDebugItemGenerator.class
    ));

    interface DebugItemGenerator {

        String getFilename();

        void outputItem(
                PwmApplication pwmApplication,
                PwmRequest pwmRequest,
                OutputStream outputStream
        ) throws Exception;
    }

    static class ConfigurationDebugJsonItemGenerator implements DebugItemGenerator {
        @Override
        public String getFilename() {
            return "configuration-debug.json";
        }

        @Override
        public void outputItem(PwmApplication pwmApplication, PwmRequest pwmRequest, OutputStream outputStream) throws Exception
        {
            final StoredConfigurationImpl storedConfiguration = readCurrentConfiguration(pwmRequest);
            storedConfiguration.resetAllPasswordValues("value removed from " + PwmConstants.PWM_APP_NAME + "-Support configuration export");
            final String jsonOutput = JsonUtil.serialize(storedConfiguration.toJsonDebugObject(), JsonUtil.Flag.PrettyPrint);
            outputStream.write(jsonOutput.getBytes(PwmConstants.DEFAULT_CHARSET));
        }
    }

    static class ConfigurationDebugTextItemGenerator implements DebugItemGenerator {
        @Override
        public String getFilename() {
            return "configuration-debug.txt";
        }

        @Override
        public void outputItem(PwmApplication pwmApplication, PwmRequest pwmRequest, OutputStream outputStream) throws Exception
        {
            final StoredConfigurationImpl storedConfiguration = readCurrentConfiguration(pwmRequest);
            storedConfiguration.resetAllPasswordValues("value removed from " + PwmConstants.PWM_APP_NAME + "-Support configuration export");

            final StringWriter writer = new StringWriter();
            writer.write("Configuration Debug Output for "
                    + PwmConstants.PWM_APP_NAME + " "
                    + PwmConstants.SERVLET_VERSION + "\n");
            writer.write("Timestamp: " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(storedConfiguration.modifyTime()) + "\n");
            writer.write("This file is encoded using " + PwmConstants.DEFAULT_CHARSET.displayName() + "\n");

            writer.write("\n");
            final Map<String,String> modifiedSettings = storedConfiguration.getModifiedSettingDebugValues(PwmConstants.DEFAULT_LOCALE, true);
            for (final String key : modifiedSettings.keySet()) {
                final String value = modifiedSettings.get(key);
                writer.write(">> Setting > " + key);
                writer.write("\n");
                writer.write(value);
                writer.write("\n");
                writer.write("\n");
            }

            outputStream.write(writer.toString().getBytes(PwmConstants.DEFAULT_CHARSET));
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
            final StoredConfigurationImpl storedConfiguration = readCurrentConfiguration(pwmRequest);
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
            outputProps.store(baos, PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date()));
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
            final Properties outputProps = Helper.newSortedProperties();

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

    static class AppPropertiesItemGenerator implements DebugItemGenerator {
        @Override
        public String getFilename() {
            return "appProperties.properties";
        }

        @Override
        public void outputItem(PwmApplication pwmApplication, PwmRequest pwmRequest, OutputStream outputStream) throws Exception
        {

            final Configuration config = pwmRequest.getConfig();
            final Properties outputProps = Helper.newSortedProperties();

            for (final AppProperty appProperty : AppProperty.values()) {
                outputProps.setProperty(appProperty.getKey(), config.readAppProperty(appProperty));
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

    static class LdapDebugItemGenerator implements DebugItemGenerator {
        @Override
        public String getFilename() {
            return "ldap.txt";
        }

        @Override
        public void outputItem(PwmApplication pwmApplication, PwmRequest pwmRequest, OutputStream outputStream) throws Exception {
            final Writer writer = new OutputStreamWriter(outputStream, PwmConstants.DEFAULT_CHARSET);
            for (LdapProfile ldapProfile : pwmApplication.getConfig().getLdapProfiles().values()) {
                writer.write("ldap profile: " + ldapProfile.getIdentifier() + "\n");
                try {
                    final ChaiProvider chaiProvider = ldapProfile.getProxyChaiProvider(pwmApplication);
                    {
                        final ChaiEntry rootDSEentry = ChaiUtility.getRootDSE(chaiProvider);
                        final Map<String, List<String>> rootDSEdata = LdapOperationsHelper.readAllEntryAttributeValues(rootDSEentry);
                        writer.write("Root DSE: " + JsonUtil.serializeMap(rootDSEdata, JsonUtil.Flag.PrettyPrint) + "\n");
                    }
                    {
                        final String proxyUserDN = ldapProfile.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
                        final ChaiEntry proxyUserEntry = ChaiFactory.createChaiEntry(proxyUserDN, chaiProvider);
                        final Map<String, List<String>> proxyUserData = LdapOperationsHelper.readAllEntryAttributeValues(proxyUserEntry);
                        writer.write("Proxy User: " + JsonUtil.serializeMap(proxyUserData, JsonUtil.Flag.PrettyPrint) + "\n");
                    }
                    {
                        final String testUserDN = ldapProfile.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
                        if (testUserDN != null) {
                            final ChaiEntry testUserEntry = ChaiFactory.createChaiEntry(testUserDN, chaiProvider);
                            if (testUserEntry.isValid()) {
                                final Map<String, List<String>> testUserdata = LdapOperationsHelper.readAllEntryAttributeValues(testUserEntry);
                                writer.write("Test User: " + JsonUtil.serializeMap(testUserdata, JsonUtil.Flag.PrettyPrint) + "\n");
                            }
                        }
                    }
                    writer.write("\n\n");
                } catch (Exception e) {
                    LOGGER.error("error during output of ldap profile debug data profile: " + ldapProfile + ", error: " + e.getMessage());
                }
            }

            writer.flush();
        }
    }

    static class FileInfoDebugItemGenerator implements DebugItemGenerator {
        @Override
        public String getFilename() {
            return "fileinformation.csv";
        }

        @Override
        public void outputItem(PwmApplication pwmApplication, PwmRequest pwmRequest, OutputStream outputStream) throws Exception {
            final List<FileSystemUtility.FileSummaryInformation> fileSummaryInformations = new ArrayList<>();
            final File applicationPath = pwmApplication.getPwmEnvironment().getApplicationPath();

            if (pwmApplication.getPwmEnvironment().getContextManager() != null) {
                try {
                    final File webInfPath = pwmApplication.getPwmEnvironment().getContextManager().locateWebInfFilePath();
                    if (webInfPath != null && webInfPath.exists()) {
                        final File servletRootPath = webInfPath.getParentFile();

                        if (servletRootPath != null) {
                            fileSummaryInformations.addAll(FileSystemUtility.readFileInformation(webInfPath));
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error(pwmRequest, "unable to generate webInfPath fileMd5sums during zip debug building: " + e.getMessage());
                }
            }

            if (applicationPath != null ) {
                try {
                    fileSummaryInformations.addAll(FileSystemUtility.readFileInformation(applicationPath));
                } catch (Exception e) {
                    LOGGER.error(pwmRequest, "unable to generate appPath fileMd5sums during zip debug building: " + e.getMessage());
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
                for (final FileSystemUtility.FileSummaryInformation fileSummaryInformation : fileSummaryInformations) {
                    final List<String> headerRow = new ArrayList<>();
                    headerRow.add(fileSummaryInformation.getFilename());
                    headerRow.add(fileSummaryInformation.getFilepath());
                    headerRow.add(PwmConstants.DEFAULT_DATETIME_FORMAT.format(fileSummaryInformation.getModified()));
                    headerRow.add(String.valueOf(fileSummaryInformation.getSize()));
                    headerRow.add(fileSummaryInformation.getSha1sum());
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

            final int maxCount = Integer.parseInt(pwmRequest.getConfig().readAppProperty(AppProperty.CONFIG_MANAGER_ZIPDEBUG_MAXLOGLINES));
            final int maxSeconds = Integer.parseInt(pwmRequest.getConfig().readAppProperty(AppProperty.CONFIG_MANAGER_ZIPDEBUG_MAXLOGSECONDS));
            final LocalDBLogger.SearchParameters searchParameters = new LocalDBLogger.SearchParameters(
                    PwmLogLevel.TRACE,
                    maxCount,
                    null,
                    null,
                    (maxSeconds * 1000),
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
            LOGGER.trace("output " + counter + " lines to " + this.getFilename());
        }
    }

    public enum Page {
        manager(PwmConstants.JSP_URL.ADMIN_DASHBOARD,"/manager"),
        wordlists(PwmConstants.JSP_URL.ADMIN_ANALYSIS,"/wordlists"),

        ;

        private final PwmConstants.JSP_URL jspURL;
        private final String urlSuffix;

        Page(PwmConstants.JSP_URL jspURL, String urlSuffix) {
            this.jspURL = jspURL;
            this.urlSuffix = urlSuffix;
        }

        public PwmConstants.JSP_URL getJspURL() {
            return jspURL;
        }

        public String getUrlSuffix() {
            return urlSuffix;
        }

        public static Page forUrl(final PwmURL pwmURL) {
            final String url = pwmURL.toString();
            for (final Page page : Page.values()) {
                if (url.endsWith(page.urlSuffix)) {
                    return page;
                }
            }
            return null;
        }
    }

}

