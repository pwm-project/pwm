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

import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.*;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.configguide.ConfigGuideServlet;
import password.pwm.i18n.Config;
import password.pwm.i18n.Display;
import password.pwm.i18n.Message;
import password.pwm.util.LDAPPermissionCalculator;
import password.pwm.util.LocaleHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
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
        permissions(HttpMethod.GET),
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
        final ConfigManagerBean configManagerBean = pwmRequest.getPwmApplication().getSessionBeanService().getBean(pwmRequest, ConfigManagerBean.class);

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
                    showSummary(pwmRequest);
                    return;

                case permissions:
                    showPermissions(pwmRequest);
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
        pwmRequest.setAttribute(PwmRequest.Attribute.PageTitle,LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, pwmRequest));
        pwmRequest.setAttribute(PwmRequest.Attribute.ApplicationPath, pwmRequest.getPwmApplication().getPwmEnvironment().getApplicationPath().getAbsolutePath());
        pwmRequest.setAttribute(PwmRequest.Attribute.ConfigFilename, configurationReader.getConfigFile().getAbsolutePath());
        {
            final Date lastModifyTime = configurationReader.getStoredConfiguration().modifyTime();
            final String output = lastModifyTime == null
                    ? LocaleHelper.getLocalizedMessage(Display.Value_NotApplicable,pwmRequest)
                    : PwmConstants.DEFAULT_DATETIME_FORMAT.format(lastModifyTime);
            pwmRequest.setAttribute(PwmRequest.Attribute.ConfigLastModified, output);
        }
        pwmRequest.setAttribute(PwmRequest.Attribute.ConfigHasPassword, LocaleHelper.booleanString(configurationReader.getStoredConfiguration().hasPassword(), pwmRequest.getLocale(), pwmRequest.getConfig()));
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
            final ConfigManagerBean configManagerBean = pwmRequest.getPwmApplication().getSessionBeanService().getBean(pwmRequest, ConfigManagerBean.class);
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
            DebugItemGenerator.outputZipDebugFile(pwmRequest, zipOutput, pathPrefix);
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



    public static StoredConfigurationImpl readCurrentConfiguration(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException
    {
        final ContextManager contextManager = ContextManager.getContextManager(pwmRequest.getHttpServletRequest().getSession());
        final ConfigurationReader runningConfigReader = contextManager.getConfigReader();
        final StoredConfigurationImpl runningConfig = runningConfigReader.getStoredConfiguration();
        return StoredConfigurationImpl.copy(runningConfig);
    }

    private void showSummary(final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final StoredConfigurationImpl storedConfiguration = readCurrentConfiguration(pwmRequest);
        final LinkedHashMap<String,Object> outputMap = new LinkedHashMap<>(storedConfiguration.toOutputMap(pwmRequest.getLocale()));
        pwmRequest.setAttribute(PwmRequest.Attribute.ConfigurationSummaryOutput,outputMap);
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.CONFIG_MANAGER_EDITOR_SUMMARY);
    }

    private void showPermissions(final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final StoredConfigurationImpl storedConfiguration = readCurrentConfiguration(pwmRequest);
        LDAPPermissionCalculator ldapPermissionCalculator = new LDAPPermissionCalculator(storedConfiguration);
        pwmRequest.setAttribute(PwmRequest.Attribute.ConfigurationSummaryOutput,ldapPermissionCalculator);
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.CONFIG_MANAGER_PERMISSIONS);
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

