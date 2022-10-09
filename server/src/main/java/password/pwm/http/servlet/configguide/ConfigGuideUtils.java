/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.http.servlet.configguide;

import com.novell.ldapchai.ChaiEntry;
import com.novell.ldapchai.ChaiEntryFactory;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiSetting;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.ConfigurationFileManager;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.http.ContextManager;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.bean.ConfigGuideBean;
import password.pwm.http.servlet.configeditor.function.UserMatchViewerFunction;
import password.pwm.i18n.ConfigGuide;
import password.pwm.i18n.Message;
import password.pwm.ldap.LdapPermissionCalculator;
import password.pwm.ldap.schema.SchemaManager;
import password.pwm.ldap.schema.SchemaOperationResult;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.Percent;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.X509Utils;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ConfigGuideUtils
{
    private static final PwmLogger LOGGER = PwmLogger.getLogger( ConfigGuideUtils.class.getName() );

    static void writeConfig(
            final ContextManager contextManager,
            final ConfigGuideBean configGuideBean
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final StoredConfigurationModifier storedConfiguration = StoredConfigurationModifier.newModifier( ConfigGuideForm.generateStoredConfig( configGuideBean ) );
        final String configPassword = configGuideBean.getFormData().get( ConfigGuideFormField.PARAM_CONFIG_PASSWORD );
        if ( configPassword != null && configPassword.length() > 0 )
        {
            StoredConfigurationUtil.setPassword( storedConfiguration, configPassword );
        }
        else
        {
            storedConfiguration.writeConfigProperty( ConfigurationProperty.PASSWORD_HASH, null );
        }

        storedConfiguration.writeConfigProperty( ConfigurationProperty.CONFIG_IS_EDITABLE, "false" );
        ConfigGuideUtils.writeConfig( contextManager, storedConfiguration.newStoredConfiguration() );
    }

    static void writeConfig(
            final ContextManager contextManager,
            final StoredConfiguration storedConfiguration
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final ConfigurationFileManager configReader = contextManager.getConfigReader();
        final PwmApplication pwmApplication = contextManager.getPwmApplication();

        try
        {
            final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );
            // add a random security key
            StoredConfigurationUtil.initNewRandomSecurityKey( modifier );

            configReader.saveConfiguration( modifier.newStoredConfiguration(), pwmApplication );

            contextManager.requestPwmApplicationRestart();
        }
        catch ( final PwmException e )
        {
            throw new PwmOperationalException( e.getErrorInformation() );
        }
        catch ( final Exception e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, "unable to save configuration: " + e.getLocalizedMessage() );
            throw new PwmOperationalException( errorInformation );
        }
    }

    public static SchemaOperationResult extendSchema(
            final PwmDomain pwmDomain,
            final ConfigGuideBean configGuideBean,
            final boolean doSchemaExtension
    )
    {
        final Map<ConfigGuideFormField, String> form = configGuideBean.getFormData();
        final boolean ldapServerSecure = ConfigGuideForm.readCheckedFormField( form.get( ConfigGuideFormField.PARAM_LDAP_SECURE ) );
        final String ldapUrl = "ldap" + ( ldapServerSecure ? "s" : "" )
                + "://"
                + form.get( ConfigGuideFormField.PARAM_LDAP_HOST )
                + ":"
                + form.get( ConfigGuideFormField.PARAM_LDAP_PORT );
        try
        {
            final ChaiConfiguration chaiConfiguration = ChaiConfiguration.builder(
                            ldapUrl,
                            form.get( ConfigGuideFormField.PARAM_LDAP_PROXY_DN ),
                            form.get( ConfigGuideFormField.PARAM_LDAP_PROXY_PW )
                    )
                    .setSetting( ChaiSetting.PROMISCUOUS_SSL, "true" )
                    .build();

            final ChaiProvider chaiProvider = pwmDomain.getLdapService().getChaiProviderFactory().newProvider( chaiConfiguration );
            if ( doSchemaExtension )
            {
                return SchemaManager.extendSchema( chaiProvider );
            }
            else
            {
                return SchemaManager.checkExistingSchema( chaiProvider );
            }
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "unable to create schema extender object: " + e.getMessage() );
            return null;
        }
    }

    static void forwardToJSP(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final ConfigGuideBean configGuideBean = pwmRequest.getPwmDomain().getSessionStateService().getBean( pwmRequest, ConfigGuideBean.class );

        if ( configGuideBean.getStep() == GuideStep.LDAP_PERMISSIONS )
        {
            final LdapPermissionCalculator ldapPermissionCalculator = new LdapPermissionCalculator(
                    AppConfig.forStoredConfig( ConfigGuideForm.generateStoredConfig( configGuideBean ) ).getDomainConfigs().get( ConfigGuideForm.DOMAIN_ID ) );
            pwmRequest.setAttribute( PwmRequestAttribute.LdapPermissionItems, ldapPermissionCalculator );
        }

        final HttpServletRequest req = pwmRequest.getHttpServletRequest();
        final ServletContext servletContext = req.getSession().getServletContext();
        String destURL = '/' + PwmConstants.URL_JSP_CONFIG_GUIDE;
        destURL = destURL.replace( "%1%", configGuideBean.getStep().toString().toLowerCase() );
        servletContext.getRequestDispatcher( destURL ).forward( req, pwmRequest.getPwmResponse().getHttpServletResponse() );
    }

    public static Percent stepProgress( final GuideStep step )
    {
        final int ordinal = step.ordinal();
        final int total = GuideStep.values().length - 2;
        return Percent.of( ordinal, total );
    }

    static void checkLdapServer( final ConfigGuideBean configGuideBean )
            throws PwmOperationalException, IOException, PwmUnrecoverableException
    {
        final Map<ConfigGuideFormField, String> formData = configGuideBean.getFormData();
        final String host = formData.get( ConfigGuideFormField.PARAM_LDAP_HOST );
        final int port = Integer.parseInt( formData.get( ConfigGuideFormField.PARAM_LDAP_PORT ) );

        {
            // socket test
            final InetAddress inetAddress = InetAddress.getByName( host );
            final SocketAddress socketAddress = new InetSocketAddress( inetAddress, port );
            final Socket socket = new Socket();

            final int timeout = 2000;
            socket.connect( socketAddress, timeout );
        }

        if ( Boolean.parseBoolean( formData.get( ConfigGuideFormField.PARAM_LDAP_SECURE ) ) )
        {
            final AppConfig tempConfig = AppConfig.forStoredConfig( ConfigGuideForm.generateStoredConfig( configGuideBean ) );
            X509Utils.readRemoteCertificates( host, port, tempConfig );
        }
    }


    public static void restUploadConfig( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();

        if ( pwmDomain.getApplicationMode() == PwmApplicationMode.RUNNING )
        {
            final String errorMsg = "config upload is not permitted when in running mode";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.CONFIG_UPLOAD_FAILURE, errorMsg, new String[]
                    {
                            errorMsg,
                    }
            );
            pwmRequest.respondWithError( errorInformation, true );
        }

        if ( ServletFileUpload.isMultipartContent( req ) )
        {
            final Optional<InputStream> uploadedFile = pwmRequest.readFileUploadStream( PwmConstants.PARAM_FILE_UPLOAD );
            if ( uploadedFile.isPresent() )
            {
                try ( InputStream inputStream = uploadedFile.get() )
                {
                    final StoredConfiguration storedConfig = StoredConfigurationFactory.input( inputStream );
                    final List<String> configErrors = StoredConfigurationUtil.validateValues( storedConfig );
                    if ( !CollectionUtil.isEmpty( configErrors ) )
                    {
                        throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, configErrors.get( 0 ) ) );
                    }
                    ConfigGuideUtils.writeConfig( ContextManager.getContextManager( pwmRequest ), storedConfig );
                    LOGGER.trace( pwmRequest, () -> "read config from file: " + storedConfig );
                    final RestResultBean restResultBean = RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown );
                    pwmRequest.getPwmResponse().outputJsonResult( restResultBean );
                    req.getSession().invalidate();
                }
                catch ( final PwmException e )
                {
                    final RestResultBean restResultBean = RestResultBean.fromError( e.getErrorInformation(), pwmRequest );
                    pwmRequest.getPwmResponse().outputJsonResult( restResultBean );
                    LOGGER.error( pwmRequest, () -> e.getErrorInformation().toDebugStr() );
                }
            }
            else
            {
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.CONFIG_UPLOAD_FAILURE, "error reading config file: no file present in upload" );
                final RestResultBean restResultBean = RestResultBean.fromError( errorInformation, pwmRequest );
                pwmRequest.getPwmResponse().outputJsonResult( restResultBean );
                LOGGER.error( pwmRequest, errorInformation::toDebugStr );
            }
        }
    }

    static List<HealthRecord> checkAdminHealth( final PwmRequest pwmRequest, final StoredConfiguration storedConfiguration )
    {
        final List<HealthRecord> records = new ArrayList<>();

        try
        {
            final ConfigGuideBean configGuideBean = ConfigGuideServlet.getBean( pwmRequest );
            final Map<ConfigGuideFormField, String> form = configGuideBean.getFormData();
            final PwmApplication tempApplication = PwmApplication.createPwmApplication(
                    pwmRequest.getPwmApplication().getPwmEnvironment().makeRuntimeInstance( AppConfig.forStoredConfig( storedConfiguration ) ) );

            final String adminDN = form.get( ConfigGuideFormField.PARAM_LDAP_ADMIN_USER );
            final UserIdentity adminIdentity = UserIdentity.create( adminDN, ConfigGuideForm.LDAP_PROFILE_NAME, ConfigGuideForm.DOMAIN_ID );

            checkAdminInContext( tempApplication, pwmRequest.getLabel(), configGuideBean, adminIdentity )
                    .ifPresent( records::add );

            if ( records.isEmpty() )
            {
                checkAdminUserExists( tempApplication, pwmRequest.getLabel(), adminIdentity )
                        .ifPresent( records::add );
            }
        }
        catch ( final Exception e )
        {
            records.add( HealthRecord.forMessage(
                    ConfigGuideForm.DOMAIN_ID,
                    HealthMessage.Config_SettingIssue,
                    PwmSetting.LDAP_PROXY_USER_DN.getLabel( pwmRequest.getLocale() ),
                    e.getMessage() ) );
        }

        if ( records.isEmpty() )
        {
            records.add( HealthRecord.forMessage(
                    ConfigGuideForm.DOMAIN_ID,
                    HealthMessage.Config_SettingIssue,
                    PwmSetting.LDAP_PROXY_USER_DN.getLabel( pwmRequest.getLocale() ),
                    "User not found" ) );
        }

        return records;
    }

    private static Optional<HealthRecord> checkAdminUserExists(
            final PwmApplication tempApplication,
            final SessionLabel sessionLabel,
            final UserIdentity adminIdentity
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final PwmDomain pwmDomain = tempApplication.domains().get( ConfigGuideForm.DOMAIN_ID );

        final UserMatchViewerFunction userMatchViewerFunction = new UserMatchViewerFunction();
        final List<UserIdentity> results = userMatchViewerFunction.discoverMatchingUsers(
                sessionLabel,
                pwmDomain,
                1,
                tempApplication.getConfig().getStoredConfiguration(),
                StoredConfigKey.forSetting( PwmSetting.QUERY_MATCH_PWM_ADMIN, null, ConfigGuideForm.DOMAIN_ID ) );

        if ( !results.isEmpty() )
        {
            final UserIdentity foundIdentity = results.get( 0 );
            if ( foundIdentity.canonicalEquals( sessionLabel, adminIdentity, tempApplication ) )
            {
                return Optional.of( HealthRecord.forMessage( ConfigGuideForm.DOMAIN_ID, HealthMessage.LDAP_AdminUserOk ) );
            }
        }

        return Optional.empty();
    }

    private static Optional<HealthRecord> checkAdminInContext(
            final PwmApplication tempApplication,
            final SessionLabel sessionLabel,
            final ConfigGuideBean configGuideBean,
            final UserIdentity adminIdentity
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, ChaiOperationException
    {
        final Map<ConfigGuideFormField, String> form = configGuideBean.getFormData();

        final ChaiProvider chaiProvider = tempApplication.domains().get( ConfigGuideForm.DOMAIN_ID ).getProxyChaiProvider( sessionLabel, ConfigGuideForm.LDAP_PROFILE_NAME );
        final ChaiEntry contextEntry = ChaiEntryFactory.newChaiFactory( chaiProvider ).newChaiEntry( form.get( ConfigGuideFormField.PARAM_LDAP_CONTEXT ) );
        final String canonicalContextDN = contextEntry.readCanonicalDN();
        final UserIdentity canonicalAdmin = adminIdentity.canonicalized( sessionLabel, tempApplication );
        if ( !canonicalAdmin.getUserDN().endsWith( canonicalContextDN ) )
        {
            final String contextTitle = LocaleHelper.getLocalizedMessage(
                    PwmConstants.DEFAULT_LOCALE,
                    "ldap_context_title",
                    tempApplication.getConfig(),
                    ConfigGuide.class );

            return Optional.of( HealthRecord.forMessage(
                    ConfigGuideForm.DOMAIN_ID,
                    HealthMessage.LDAP_AdminNotInContext,
                    contextTitle,
                    canonicalContextDN
            ) );
        }

        return Optional.empty();
    }


    static GuideStep figureNextEffectiveStep(
            final ConfigGuideBean configGuideBean,
            final GuideStep inputStep
    )
    {
        GuideStep step = inputStep;
        if ( step == GuideStep.START )
        {
            configGuideBean.getFormData().clear();
            configGuideBean.getFormData().putAll( ConfigGuideForm.defaultForm() );
        }
        else if ( step == GuideStep.NEXT )
        {
            step = configGuideBean.getStep().next();
            while ( step != GuideStep.FINISH && !step.visible( configGuideBean ) )
            {
                step = step.next();
            }
        }
        else if ( step == GuideStep.PREVIOUS )
        {
            step = configGuideBean.getStep().previous();
            while ( step != GuideStep.START && !step.visible( configGuideBean ) )
            {
                step = step.previous();
            }
        }

        return step;
    }

    static ProcessStatus executeNextStep(
            final PwmRequest pwmRequest,
            final ConfigGuideBean configGuideBean,
            final GuideStep step
    )
            throws IOException, PwmUnrecoverableException
    {
        if ( step == GuideStep.FINISH )
        {
            final ContextManager contextManager = ContextManager.getContextManager( pwmRequest );
            try
            {
                ConfigGuideUtils.writeConfig( contextManager, configGuideBean );
                pwmRequest.getPwmSession().getSessionStateBean().setTheme( null );
            }
            catch ( final PwmException e )
            {
                final RestResultBean restResultBean = RestResultBean.fromError( e.getErrorInformation(), pwmRequest );
                pwmRequest.outputJsonResult( restResultBean );
                return ProcessStatus.Halt;
            }
            catch ( final Exception e )
            {
                LOGGER.error( pwmRequest, () -> "error during save: " + e.getMessage(), e );
                final RestResultBean restResultBean = RestResultBean.fromError( new ErrorInformation(
                        PwmError.ERROR_INTERNAL,
                        "error during save: " + e.getMessage()
                ), pwmRequest );
                pwmRequest.outputJsonResult( restResultBean );
                return ProcessStatus.Halt;
            }
            final HashMap<String, String> resultData = new HashMap<>();
            resultData.put( "serverRestart", "true" );
            pwmRequest.outputJsonResult( RestResultBean.withData( resultData, Map.class ) );
            pwmRequest.invalidateSession();
        }
        else
        {
            configGuideBean.setStep( step );
            pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );

            {
                final GuideStep finalStep = step;
                LOGGER.trace( pwmRequest, () -> "setting current step to: " + finalStep );
            }
        }

        return ProcessStatus.Continue;
    }
}
