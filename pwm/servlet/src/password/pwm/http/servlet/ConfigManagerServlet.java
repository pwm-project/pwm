/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

import com.google.gson.GsonBuilder;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.*;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.ConfigurationReader;
import password.pwm.config.StoredConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.http.filter.SessionFilter;
import password.pwm.util.*;
import password.pwm.util.intruder.RecordType;
import password.pwm.util.localdb.LocalDBUtility;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ConfigManagerServlet extends TopServlet {
    final static private PwmLogger LOGGER = PwmLogger.getLogger(ConfigManagerServlet.class);

    private static final String CONFIGMANAGER_INTRUDER_USERNAME = "ConfigurationManagerLogin";

    protected void processRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();

        if (checkAuthentication(pwmApplication, pwmSession, configManagerBean, req, resp)) {
            return;
        }

        configManagerBean.setConfigLocked(pwmApplication.getApplicationMode() != PwmApplication.MODE.CONFIGURATION);

        final String processActionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);

        if ("lockConfiguration".equalsIgnoreCase(processActionParam)) {
            restLockConfiguration(req, resp);
            return;
        } else if ("startEditing".equalsIgnoreCase(processActionParam)) {
            doStartEditing(req, resp);
            return;
        } else if ("generateXml".equalsIgnoreCase(processActionParam)) {
            doGenerateXml(req, resp, pwmSession);
            return;
        } else if ("exportLocalDB".equalsIgnoreCase(processActionParam)) {
            doExportLocalDB(resp, pwmApplication);
            return;
        } else if ("generateSupportZip".equalsIgnoreCase(processActionParam)) {
            doGenerateSupportZip(req, resp, pwmApplication, pwmSession);
            return;
        } else if ("uploadConfig".equalsIgnoreCase(processActionParam)) {
            if (pwmApplication.getApplicationMode() != PwmApplication.MODE.CONFIGURATION) {
                final String errorMsg = "config upload is only permitted when in configuration mode";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_UPLOAD_FAILURE,errorMsg,new String[]{errorMsg});
                pwmSession.getSessionStateBean().setSessionError(errorInformation);
                ServletHelper.forwardToErrorPage(req,resp,true);
                return;
            }
            ConfigGuideServlet.restUploadConfig(req, resp, pwmApplication, pwmSession);
            return;
        }

        ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.CONFIG_MANAGER_MODE_CONFIGURATION);
    }

    static boolean checkAuthentication(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final ConfigManagerBean configManagerBean,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, PwmUnrecoverableException, ServletException {
        final ConfigurationReader runningConfigReader = ContextManager.getContextManager(req.getSession()).getConfigReader();
        final StoredConfiguration storedConfig = runningConfigReader.getStoredConfiguration();

        boolean authRequired = false;
        if (storedConfig.hasPassword()) {
            authRequired = true;
        }

        if (PwmApplication.MODE.RUNNING == pwmApplication.getApplicationMode()) {
            if (!pwmSession.getSessionStateBean().isAuthenticated()) {
                throw new PwmUnrecoverableException(PwmError.ERROR_AUTHENTICATION_REQUIRED);
            }

            if (pwmSession.getUserInfoBean().getAuthenticationType() != UserInfoBean.AuthenticationType.AUTHENTICATED) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_AUTHENTICATION_REQUIRED,
                        "Username/Password authentication is required to edit configuration.  This session has not been authenticated using a user password (SSO or other method used)."));
            }
        }

        if (PwmApplication.MODE.CONFIGURATION != pwmApplication.getApplicationMode()) {
            authRequired = true;
        }

        if (!authRequired) {
            return false;
        }

        if (!storedConfig.hasPassword()) {
            final String errorMsg = "config file does not have a configuration password";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg,new String[]{errorMsg});
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
            ServletHelper.forwardToErrorPage(req,resp,true);
            return true;
        }

        if (configManagerBean.isPasswordVerified()) {
            return false;
        }

        final String password = Validator.readStringFromRequest(req,"password");
        if (password != null && password.length() > 0) {
            final boolean passed = storedConfig.verifyPassword(password);
            configManagerBean.setPasswordVerified(passed);
            if (passed) {
                pwmApplication.getIntruderManager().convenience().clearAddressAndSession(pwmSession);
                pwmApplication.getIntruderManager().clear(RecordType.USERNAME,CONFIGMANAGER_INTRUDER_USERNAME);
                if (configManagerBean.getPrePasswordEntryUrl() != null) {
                    final String originalUrl = configManagerBean.getPrePasswordEntryUrl();
                    configManagerBean.setPrePasswordEntryUrl(null);
                    resp.sendRedirect(SessionFilter.rewriteRedirectURL(originalUrl, req, resp));
                    return true;
                }
                return false;
            } else {
                pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
                pwmApplication.getIntruderManager().mark(RecordType.USERNAME,CONFIGMANAGER_INTRUDER_USERNAME,null);
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_WRONGPASSWORD);
                pwmSession.getSessionStateBean().setSessionError(errorInformation);
            }
        }

        if (configManagerBean.getPrePasswordEntryUrl() == null) {
            configManagerBean.setPrePasswordEntryUrl(req.getRequestURL().toString());
        }
        ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.CONFIG_MANAGER_LOGIN);
        return true;
    }

    private void doStartEditing(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, PwmUnrecoverableException, ServletException
    {
        forwardToEditor(req, resp);
    }


    private void restLockConfiguration(
            final HttpServletRequest req,
            final HttpServletResponse resp

    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        if (PwmConstants.TRIAL_MODE) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_TRIAL_VIOLATION,"configuration lock not available in trial");
            final RestResultBean restResultBean = RestResultBean.fromError(errorInfo, pwmSession.getSessionStateBean().getLocale(), pwmApplication.getConfig());
            LOGGER.debug(pwmSession, errorInfo);
            ServletHelper.outputJsonResult(resp, restResultBean);
            return;
        }

        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_AUTHENTICATION_REQUIRED,"You must be authenticated before locking the configuration");
            final RestResultBean restResultBean = RestResultBean.fromError(errorInfo, pwmSession.getSessionStateBean().getLocale(), pwmApplication.getConfig());
            LOGGER.debug(pwmSession, errorInfo);
            ServletHelper.outputJsonResult(resp, restResultBean);
            return;
        }

        if (!pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.PWMADMIN)) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"You must be authenticated with admin privileges before locking the configuration");
            final RestResultBean restResultBean = RestResultBean.fromError(errorInfo, pwmSession.getSessionStateBean().getLocale(), pwmApplication.getConfig());
            LOGGER.debug(pwmSession, errorInfo);
            ServletHelper.outputJsonResult(resp, restResultBean);
            return;
        }

        try {
            final StoredConfiguration storedConfiguration = readCurrentConfiguration(ContextManager.getContextManager(req.getSession()));
            if (!storedConfiguration.hasPassword()) {
                final ErrorInformation errorInfo = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"Please set a configuration password before locking the configuration");
                final RestResultBean restResultBean = RestResultBean.fromError(errorInfo, pwmSession.getSessionStateBean().getLocale(), pwmApplication.getConfig());
                LOGGER.debug(pwmSession, errorInfo);
                ServletHelper.outputJsonResult(resp, restResultBean);
                return;
            }

            storedConfiguration.writeConfigProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_CONFIG_IS_EDITABLE, "false");
            saveConfiguration(pwmSession, req.getSession().getServletContext(), storedConfiguration);
            final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();
            configManagerBean.setConfiguration(null);
        } catch (PwmException e) {
            final ErrorInformation errorInfo = e.getErrorInformation();
            final RestResultBean restResultBean = RestResultBean.fromError(errorInfo, pwmSession.getSessionStateBean().getLocale(), pwmApplication.getConfig());
            LOGGER.debug(pwmSession, errorInfo.toDebugStr());
            ServletHelper.outputJsonResult(resp, restResultBean);
            return;
        } catch (Exception e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_UNKNOWN,e.getMessage());
            final RestResultBean restResultBean = RestResultBean.fromError(errorInfo, pwmSession.getSessionStateBean().getLocale(), pwmApplication.getConfig());
            LOGGER.debug(pwmSession, errorInfo.toDebugStr());
            ServletHelper.outputJsonResult(resp, restResultBean);
            return;
        }
        final HashMap<String,String> resultData = new HashMap<>();
        LOGGER.info(pwmSession, "Configuration Locked");
        ServletHelper.outputJsonResult(resp, new RestResultBean(resultData));
    }

    static void saveConfiguration(
            final PwmSession pwmSession,
            final ServletContext servletContext,
            final StoredConfiguration storedConfiguration
    )
            throws PwmUnrecoverableException
    {
        {
            final List<String> errorStrings = storedConfiguration.validateValues();
            if (errorStrings != null && !errorStrings.isEmpty()) {
                final String errorString = errorStrings.get(0);
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, errorString));
            }
        }

        try {
            ContextManager contextManager = ContextManager.getContextManager(servletContext);
            contextManager.getConfigReader().saveConfiguration(storedConfiguration, contextManager.getPwmApplication());
            contextManager.reinitialize();
        } catch (Exception e) {
            final String errorString = "error saving file: " + e.getMessage();
            LOGGER.error(pwmSession, errorString);
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, errorString));
        }

    }

    static void forwardToEditor(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final String url = req.getContextPath() + "/private/config/ConfigEditor";
        resp.sendRedirect(url);
    }

    private void doGenerateXml(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmSession pwmSession
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        try {
            final StoredConfiguration storedConfiguration = readCurrentConfiguration(
                    ContextManager.getContextManager(req.getSession()));
            final String output = storedConfiguration.toXml();
            resp.setHeader("content-disposition", "attachment;filename=" + PwmConstants.DEFAULT_CONFIG_FILE_FILENAME);
            resp.setContentType("text/xml;charset=utf-8");
            resp.getWriter().print(output);
        } catch (Exception e) {
            LOGGER.error(pwmSession, "unable to download configuration: " + e.getMessage());
        }
    }

    private void doGenerateSupportZip(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws IOException, ServletException
    {
        resp.setHeader("content-disposition", "attachment;filename=" + PwmConstants.PWM_APP_NAME + "-Support.zip");
        resp.setContentType("application/zip");
        resp.setContentLength(0);

        final String pathPrefix = PwmConstants.PWM_APP_NAME + "-Support" + "/";

        ZipOutputStream zipOutput = null;
        try {
            zipOutput = new ZipOutputStream(resp.getOutputStream(), Charset.forName("UTF8"));
            final ContextManager contextManager = ContextManager.getContextManager(req.getSession());
            outputZipDebugFile(pwmApplication,pwmSession,contextManager,zipOutput,pathPrefix);
        } catch (Exception e) {
            LOGGER.error(pwmSession, "error during zip debug building: " + e.getMessage());
        } finally {
            if (zipOutput != null) {
                try {
                    zipOutput.close();
                } catch (Exception e) {
                    LOGGER.error(pwmSession, "error during zip debug closing: " + e.getMessage());
                }
            }
        }

    }

    private void outputZipDebugFile(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final ContextManager contextManager,
            final ZipOutputStream zipOutput,
            final String pathPrefix
    )
            throws IOException, PwmUnrecoverableException
    {
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
        {
            zipOutput.putNextEntry(new ZipEntry(pathPrefix + PwmConstants.DEFAULT_CONFIG_FILE_FILENAME));
            final StoredConfiguration storedConfiguration = readCurrentConfiguration(contextManager);
            final String output = storedConfiguration.toXml();
            zipOutput.write(output.getBytes("UTF8"));
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

            final String recordJson = Helper.getGson(new GsonBuilder().setPrettyPrinting()).toJson(outputMap);
            zipOutput.write(recordJson.getBytes("UTF8"));
            zipOutput.closeEntry();
            zipOutput.flush();
        }
        if (pwmApplication.getApplicationPath() != null) {
            try {
                zipOutput.putNextEntry(new ZipEntry(pathPrefix + "fileMd5sums.json"));
                final Map<String,String> fileChecksums = BuildChecksumMaker.readDirectorySums(pwmApplication.getApplicationPath());
                final String json = Helper.getGson(new GsonBuilder().setPrettyPrinting()).toJson(fileChecksums);
                zipOutput.write(json.getBytes("UTF8"));
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
                zipOutput.write(event.toLogString(false).getBytes("UTF8"));
                zipOutput.write("\n".getBytes("UTF8"));
                counter++;
                if (counter % 100 == 0) {
                    zipOutput.flush();
                }
                System.out.println(counter);
            }
            zipOutput.closeEntry();
        }
        {
            zipOutput.putNextEntry(new ZipEntry(pathPrefix + "health.json"));
            final Set<HealthRecord> records = pwmApplication.getHealthMonitor().getHealthRecords();
            final String recordJson = Helper.getGson(new GsonBuilder().setPrettyPrinting()).toJson(records);
            zipOutput.write(recordJson.getBytes("UTF8"));
            zipOutput.closeEntry();
            zipOutput.flush();
        }
    }

    static StoredConfiguration readCurrentConfiguration(final ContextManager contextManager)
            throws PwmUnrecoverableException
    {
        final ConfigurationReader runningConfigReader = contextManager.getConfigReader();
        final StoredConfiguration runningConfig = runningConfigReader.getStoredConfiguration();
        return StoredConfiguration.copy(runningConfig);
    }

    private void doExportLocalDB(
            final HttpServletResponse resp,
            final PwmApplication pwmApplication
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        resp.setHeader("Content-Disposition", "attachment;filename=" + PwmConstants.PWM_APP_NAME + "-LocalDB.bak");
        resp.setContentType("application/octet-stream");
        resp.setHeader("Content-Transfer-Encoding", "binary");
        final LocalDBUtility localDBUtility = new LocalDBUtility(pwmApplication.getLocalDB());
        try {
            localDBUtility.exportLocalDB(resp.getOutputStream(),new PrintStream(new ByteArrayOutputStream()),false);
        } catch (Exception e) {
            LOGGER.error("error downloading export localdb: " + e.getMessage());
        }
    }
}

