/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.http.servlet.configmanager;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.IOUtils;
import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmResponse;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.http.filter.ConfigAccessFilter;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.http.servlet.configguide.ConfigGuideUtils;
import password.pwm.i18n.Admin;
import password.pwm.i18n.Config;
import password.pwm.i18n.Display;
import password.pwm.util.LDAPPermissionCalculator;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

@WebServlet(
        name = "ConfigManagerServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/config/manager",
                PwmConstants.URL_PREFIX_PRIVATE + "/config/ConfigManager"
        }
)
public class ConfigManagerServlet extends AbstractPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigManagerServlet.class );

    public enum ConfigManagerAction implements ProcessAction
    {
        lockConfiguration( HttpMethod.POST ),
        startEditing( HttpMethod.POST ),
        downloadConfig( HttpMethod.GET ),
        generateSupportZip( HttpMethod.GET ),
        uploadConfig( HttpMethod.POST ),
        summary( HttpMethod.GET ),
        permissions( HttpMethod.GET ),
        viewLog( HttpMethod.GET ),
        downloadPermissionCsv( HttpMethod.GET ),;

        private final HttpMethod method;

        ConfigManagerAction( final HttpMethod method )
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( method );
        }
    }

    protected ConfigManagerAction readProcessAction( final PwmRequest request )
            throws PwmUnrecoverableException
    {
        try
        {
            return ConfigManagerAction.valueOf( request.readParameterAsString( PwmConstants.PARAM_ACTION_REQUEST ) );
        }
        catch ( final IllegalArgumentException e )
        {
            return null;
        }
    }

    public static void verifyConfigAccess( final PwmRequest pwmRequest )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final ConfigManagerBean configManagerBean = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, ConfigManagerBean.class );
        final ProcessStatus processStatus = ConfigAccessFilter.checkAuthentication( pwmRequest, configManagerBean );
        if ( processStatus != ProcessStatus.Continue )
        {
            final String msg = "config access authentication not yet completed";
            throw PwmUnrecoverableException.newException( PwmError.ERROR_SERVICE_NOT_AVAILABLE, msg );
        }
    }

    protected void processAction( final PwmRequest pwmRequest )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        verifyConfigAccess( pwmRequest );

        final ConfigManagerAction processAction = readProcessAction( pwmRequest );
        if ( processAction != null )
        {
            switch ( processAction )
            {
                case lockConfiguration:
                    restLockConfiguration( pwmRequest );
                    break;

                case startEditing:
                    doStartEditing( pwmRequest );
                    break;

                case downloadConfig:
                    doDownloadConfig( pwmRequest );
                    break;

                case generateSupportZip:
                    doGenerateSupportZip( pwmRequest );
                    break;

                case uploadConfig:
                    ConfigGuideUtils.restUploadConfig( pwmRequest );
                    return;

                case summary:
                    showSummary( pwmRequest );
                    return;

                case permissions:
                    showPermissions( pwmRequest );
                    return;

                case downloadPermissionCsv:
                    downloadPermissionReportCsv( pwmRequest );
                    return;

                default:
                    JavaHelper.unhandledSwitchStatement( processAction );
            }
            return;
        }

        initRequestAttributes( pwmRequest );
        pwmRequest.forwardToJsp( JspUrl.CONFIG_MANAGER_MODE_CONFIGURATION );
    }

    void initRequestAttributes( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final ConfigurationReader configurationReader = pwmRequest.getContextManager().getConfigReader();
        pwmRequest.setAttribute( PwmRequestAttribute.PageTitle, LocaleHelper.getLocalizedMessage( Config.Title_ConfigManager, pwmRequest ) );
        pwmRequest.setAttribute( PwmRequestAttribute.ApplicationPath, pwmRequest.getPwmApplication().getPwmEnvironment().getApplicationPath().getAbsolutePath() );
        pwmRequest.setAttribute( PwmRequestAttribute.ConfigFilename, configurationReader.getConfigFile().getAbsolutePath() );
        {
            final Instant lastModifyTime = configurationReader.getStoredConfiguration().modifyTime();
            final String output = lastModifyTime == null
                    ? LocaleHelper.getLocalizedMessage( Display.Value_NotApplicable, pwmRequest )
                    : JavaHelper.toIsoDate( lastModifyTime );
            pwmRequest.setAttribute( PwmRequestAttribute.ConfigLastModified, output );
        }

        pwmRequest.setAttribute(
                PwmRequestAttribute.ConfigHasPassword,
                LocaleHelper.booleanString(
                        StoredConfigurationUtil.hasPassword( configurationReader.getStoredConfiguration() ),
                        pwmRequest.getLocale(),
                        pwmRequest.getConfig()
                )
        );
    }


    private void doStartEditing( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException, ServletException
    {
        forwardToEditor( pwmRequest );
    }


    private void restLockConfiguration( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        if ( PwmConstants.TRIAL_MODE )
        {
            final String msg = LocaleHelper.getLocalizedMessage( Admin.Notice_TrialRestrictConfig, pwmRequest );
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_TRIAL_VIOLATION, msg );
            final RestResultBean restResultBean = RestResultBean.fromError( errorInfo, pwmRequest );
            LOGGER.debug( pwmRequest, errorInfo );
            pwmRequest.outputJsonResult( restResultBean );
            return;
        }

        if ( !pwmSession.isAuthenticated()
                || !pwmSession.getSessionManager().checkPermission( pwmApplication, Permission.PWMADMIN ) )
        {
            final ErrorInformation errorInfo = new ErrorInformation(
                    PwmError.ERROR_AUTHENTICATION_REQUIRED,
                    "You must be authenticated with admin privileges before restricting the configuration"
            );
            final RestResultBean restResultBean = RestResultBean.fromError( errorInfo, pwmRequest );
            LOGGER.debug( pwmRequest, errorInfo );
            pwmRequest.outputJsonResult( restResultBean );
            return;
        }

        try
        {
            final StoredConfiguration storedConfiguration = readCurrentConfiguration( pwmRequest );
            if ( !StoredConfigurationUtil.hasPassword( storedConfiguration ) )
            {
                final ErrorInformation errorInfo = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                        {
                                "Please set a configuration password before restricting the configuration",
                        }
                );
                final RestResultBean restResultBean = RestResultBean.fromError( errorInfo, pwmRequest );
                LOGGER.debug( pwmRequest, errorInfo );
                pwmRequest.outputJsonResult( restResultBean );
                return;
            }

            final StoredConfigurationModifier modifiedConfig = StoredConfigurationModifier.newModifier( storedConfiguration );
            modifiedConfig.writeConfigProperty( ConfigurationProperty.CONFIG_IS_EDITABLE, "false" );
            saveConfiguration( pwmRequest, modifiedConfig.newStoredConfiguration() );
            final ConfigManagerBean configManagerBean = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, ConfigManagerBean.class );
            configManagerBean.setStoredConfiguration( null );
        }
        catch ( final PwmException e )
        {
            final ErrorInformation errorInfo = e.getErrorInformation();
            final RestResultBean restResultBean = RestResultBean.fromError( errorInfo, pwmRequest );
            LOGGER.debug( pwmRequest, errorInfo );
            pwmRequest.outputJsonResult( restResultBean );
            return;
        }
        catch ( final Exception e )
        {
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_INTERNAL, e.getMessage() );
            final RestResultBean restResultBean = RestResultBean.fromError( errorInfo, pwmRequest );
            LOGGER.debug( pwmRequest, errorInfo );
            pwmRequest.outputJsonResult( restResultBean );
            return;
        }
        final HashMap<String, String> resultData = new HashMap<>();
        LOGGER.info( pwmRequest, () -> "Configuration Locked" );
        pwmRequest.outputJsonResult( RestResultBean.withData( resultData ) );
    }

    public static void saveConfiguration(
            final PwmRequest pwmRequest,
            final StoredConfiguration storedConfiguration
    )
            throws PwmUnrecoverableException
    {
        {
            final List<String> errorStrings = StoredConfigurationUtil.validateValues( storedConfiguration );
            if ( errorStrings != null && !errorStrings.isEmpty() )
            {
                final String errorString = errorStrings.get( 0 );
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                        {
                                errorString,
                        }
                ) );
            }
        }

        try
        {
            final ContextManager contextManager = ContextManager.getContextManager( pwmRequest.getHttpServletRequest().getSession().getServletContext() );
            contextManager.getConfigReader().saveConfiguration(
                    storedConfiguration,
                    contextManager.getPwmApplication(),
                    pwmRequest.getLabel()
            );

            contextManager.requestPwmApplicationRestart();
        }
        catch ( final Exception e )
        {
            final String errorString = "error saving file: " + e.getMessage();
            LOGGER.error( pwmRequest, () -> errorString, e );
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                    {
                            errorString,
                    }
            ), e );
        }

    }

    static void forwardToEditor( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        pwmRequest.sendRedirect( PwmServletDefinition.ConfigEditor );
    }

    private void doDownloadConfig( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmResponse resp = pwmRequest.getPwmResponse();

        try
        {
            final StoredConfiguration storedConfiguration = readCurrentConfiguration( pwmRequest );
            final OutputStream responseWriter = resp.getOutputStream();
            resp.setHeader( HttpHeader.ContentDisposition, "attachment;filename=" + PwmConstants.DEFAULT_CONFIG_FILE_FILENAME );
            resp.setContentType( HttpContentType.xml );
            StoredConfigurationFactory.toXml( storedConfiguration, responseWriter );
            responseWriter.close();
        }
        catch ( final Exception e )
        {
            LOGGER.error( pwmRequest, () -> "unable to download configuration: " + e.getMessage() );
        }
    }

    private void doGenerateSupportZip( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final DebugItemGenerator debugItemGenerator = new DebugItemGenerator( pwmRequest.getPwmApplication(), pwmRequest.getLabel() );
        final PwmResponse resp = pwmRequest.getPwmResponse();
        resp.setHeader( HttpHeader.ContentDisposition, "attachment;filename=" + PwmConstants.PWM_APP_NAME + "-Support.zip" );
        resp.setContentType( HttpContentType.zip );
        try ( ZipOutputStream zipOutput = new ZipOutputStream( resp.getOutputStream(), PwmConstants.DEFAULT_CHARSET ) )
        {
            debugItemGenerator.outputZipDebugFile( zipOutput );
        }
        catch ( final Exception e )
        {
            LOGGER.error( pwmRequest, () -> "error during zip debug building: " + e.getMessage() );
        }
    }


    public static StoredConfiguration readCurrentConfiguration( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        return pwmRequest.getConfig().getStoredConfiguration();
    }

    private void showSummary( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final StoredConfiguration storedConfiguration = readCurrentConfiguration( pwmRequest );
        final Map<String, String> outputMap = StoredConfigurationUtil.makeDebugMap( storedConfiguration, storedConfiguration.modifiedItems(), pwmRequest.getLocale() );
        pwmRequest.setAttribute( PwmRequestAttribute.ConfigurationSummaryOutput, new LinkedHashMap<>( outputMap ) );
        pwmRequest.forwardToJsp( JspUrl.CONFIG_MANAGER_EDITOR_SUMMARY );
    }

    private void showPermissions( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final StoredConfiguration storedConfiguration = readCurrentConfiguration( pwmRequest );
        final LDAPPermissionCalculator ldapPermissionCalculator = new LDAPPermissionCalculator( storedConfiguration );
        pwmRequest.setAttribute( PwmRequestAttribute.LdapPermissionItems, ldapPermissionCalculator );
        pwmRequest.forwardToJsp( JspUrl.CONFIG_MANAGER_PERMISSIONS );
    }


    private void downloadPermissionReportCsv(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        pwmRequest.getPwmResponse().markAsDownload(
                HttpContentType.csv,
                pwmRequest.getConfig().readAppProperty( AppProperty.DOWNLOAD_FILENAME_LDAP_PERMISSION_CSV )
        );

        final CSVPrinter csvPrinter = JavaHelper.makeCsvPrinter( pwmRequest.getPwmResponse().getOutputStream() );
        try
        {

            final StoredConfiguration storedConfiguration = readCurrentConfiguration( pwmRequest );
            final LDAPPermissionCalculator ldapPermissionCalculator = new LDAPPermissionCalculator( storedConfiguration );

            for ( final LDAPPermissionCalculator.PermissionRecord permissionRecord : ldapPermissionCalculator.getPermissionRecords() )
            {
                final String settingTxt = permissionRecord.getPwmSetting() == null
                        ? LocaleHelper.getLocalizedMessage( Display.Value_NotApplicable, pwmRequest )
                        : permissionRecord.getPwmSetting().toMenuLocationDebug( permissionRecord.getProfile(), pwmRequest.getLocale() );
                csvPrinter.printRecord(
                        permissionRecord.getActor().getLabel( pwmRequest.getLocale(), pwmRequest.getConfig() ),
                        permissionRecord.getAttribute(),
                        permissionRecord.getAccess().toString(),
                        settingTxt
                );
            }

        }
        catch ( final Exception e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, e.getMessage() );
            LOGGER.error( pwmRequest, errorInformation );
            pwmRequest.respondWithError( errorInformation );
        }
        finally
        {
            IOUtils.closeQuietly( csvPrinter );
        }
    }
}

