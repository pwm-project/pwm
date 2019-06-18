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
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmResponse;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.http.servlet.configguide.ConfigGuideUtils;
import password.pwm.i18n.Admin;
import password.pwm.i18n.Config;
import password.pwm.i18n.Display;
import password.pwm.svc.PwmService;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecord;
import password.pwm.svc.event.AuditRecordFactory;
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
        catch ( IllegalArgumentException e )
        {
            return null;
        }
    }

    protected void processAction( final PwmRequest pwmRequest )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
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
                        configurationReader.getStoredConfiguration().hasPassword(),
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
            LOGGER.debug( pwmSession, errorInfo );
            pwmRequest.outputJsonResult( restResultBean );
            return;
        }

        if ( !pwmSession.isAuthenticated() )
        {
            final ErrorInformation errorInfo = new ErrorInformation(
                    PwmError.ERROR_AUTHENTICATION_REQUIRED,
                    "You must be authenticated before restricting the configuration"
            );
            final RestResultBean restResultBean = RestResultBean.fromError( errorInfo, pwmRequest );
            LOGGER.debug( pwmSession, errorInfo );
            pwmRequest.outputJsonResult( restResultBean );
            return;
        }

        if ( !pwmSession.getSessionManager().checkPermission( pwmApplication, Permission.PWMADMIN ) )
        {
            final ErrorInformation errorInfo = new ErrorInformation(
                    PwmError.ERROR_UNAUTHORIZED,
                    "You must be authenticated with admin privileges before restricting the configuration"
            );
            final RestResultBean restResultBean = RestResultBean.fromError( errorInfo, pwmRequest );
            LOGGER.debug( pwmSession, errorInfo );
            pwmRequest.outputJsonResult( restResultBean );
            return;
        }

        try
        {
            final StoredConfigurationImpl storedConfiguration = readCurrentConfiguration( pwmRequest );
            if ( !storedConfiguration.hasPassword() )
            {
                final ErrorInformation errorInfo = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                        {
                                "Please set a configuration password before restricting the configuration",
                        }
                );
                final RestResultBean restResultBean = RestResultBean.fromError( errorInfo, pwmRequest );
                LOGGER.debug( pwmSession, errorInfo );
                pwmRequest.outputJsonResult( restResultBean );
                return;
            }

            storedConfiguration.writeConfigProperty( ConfigurationProperty.CONFIG_IS_EDITABLE, "false" );
            saveConfiguration( pwmRequest, storedConfiguration );
            final ConfigManagerBean configManagerBean = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, ConfigManagerBean.class );
            configManagerBean.setConfiguration( null );
        }
        catch ( PwmException e )
        {
            final ErrorInformation errorInfo = e.getErrorInformation();
            final RestResultBean restResultBean = RestResultBean.fromError( errorInfo, pwmRequest );
            LOGGER.debug( pwmSession, errorInfo );
            pwmRequest.outputJsonResult( restResultBean );
            return;
        }
        catch ( Exception e )
        {
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_INTERNAL, e.getMessage() );
            final RestResultBean restResultBean = RestResultBean.fromError( errorInfo, pwmRequest );
            LOGGER.debug( pwmSession, errorInfo );
            pwmRequest.outputJsonResult( restResultBean );
            return;
        }
        final HashMap<String, String> resultData = new HashMap<>();
        LOGGER.info( pwmSession, () -> "Configuration Locked" );
        pwmRequest.outputJsonResult( RestResultBean.withData( resultData ) );
    }

    public static void saveConfiguration(
            final PwmRequest pwmRequest,
            final StoredConfigurationImpl storedConfiguration
    )
            throws PwmUnrecoverableException
    {
        {
            final List<String> errorStrings = storedConfiguration.validateValues();
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
                    pwmRequest.getSessionLabel()
            );

            final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
            if ( pwmApplication.getAuditManager() != null && pwmApplication.getAuditManager().status() == PwmService.STATUS.OPEN )
            {
                final String modifyMessage = "Configuration Changes: " + storedConfiguration.changeLogAsDebugString( PwmConstants.DEFAULT_LOCALE, false );
                final AuditRecord auditRecord = new AuditRecordFactory( pwmApplication ).createUserAuditRecord(
                        AuditEvent.MODIFY_CONFIGURATION,
                        pwmRequest.getUserInfoIfLoggedIn(),
                        pwmRequest.getSessionLabel(),
                        modifyMessage
                );
                pwmApplication.getAuditManager().submit( auditRecord );
            }

            contextManager.requestPwmApplicationRestart();
        }
        catch ( Exception e )
        {
            final String errorString = "error saving file: " + e.getMessage();
            LOGGER.error( pwmRequest, errorString );
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                    {
                            errorString,
                    }
            ) );
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
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmResponse resp = pwmRequest.getPwmResponse();

        try
        {
            final StoredConfigurationImpl storedConfiguration = readCurrentConfiguration( pwmRequest );
            final OutputStream responseWriter = resp.getOutputStream();
            resp.setHeader( HttpHeader.ContentDisposition, "attachment;filename=" + PwmConstants.DEFAULT_CONFIG_FILE_FILENAME );
            resp.setContentType( HttpContentType.xml );
            storedConfiguration.toXml( responseWriter );
            responseWriter.close();
        }
        catch ( Exception e )
        {
            LOGGER.error( pwmSession, "unable to download configuration: " + e.getMessage() );
        }
    }

    private void doGenerateSupportZip( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final DebugItemGenerator debugItemGenerator = new DebugItemGenerator( pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel() );
        final PwmResponse resp = pwmRequest.getPwmResponse();
        resp.setHeader( HttpHeader.ContentDisposition, "attachment;filename=" + PwmConstants.PWM_APP_NAME + "-Support.zip" );
        resp.setContentType( HttpContentType.zip );
        try ( ZipOutputStream zipOutput = new ZipOutputStream( resp.getOutputStream(), PwmConstants.DEFAULT_CHARSET ) )
        {
            debugItemGenerator.outputZipDebugFile( zipOutput );
        }
        catch ( Exception e )
        {
            LOGGER.error( pwmRequest, "error during zip debug building: " + e.getMessage() );
        }
    }


    public static StoredConfigurationImpl readCurrentConfiguration( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final ContextManager contextManager = ContextManager.getContextManager( pwmRequest.getHttpServletRequest().getSession() );
        final ConfigurationReader runningConfigReader = contextManager.getConfigReader();
        final StoredConfigurationImpl runningConfig = runningConfigReader.getStoredConfiguration();
        return StoredConfigurationImpl.copy( runningConfig );
    }

    private void showSummary( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final StoredConfigurationImpl storedConfiguration = readCurrentConfiguration( pwmRequest );
        final LinkedHashMap<String, Object> outputMap = new LinkedHashMap<>( storedConfiguration.toOutputMap( pwmRequest.getLocale() ) );
        pwmRequest.setAttribute( PwmRequestAttribute.ConfigurationSummaryOutput, outputMap );
        pwmRequest.forwardToJsp( JspUrl.CONFIG_MANAGER_EDITOR_SUMMARY );
    }

    private void showPermissions( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final StoredConfigurationImpl storedConfiguration = readCurrentConfiguration( pwmRequest );
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

            final StoredConfigurationImpl storedConfiguration = readCurrentConfiguration( pwmRequest );
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
        catch ( Exception e )
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

