/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.function.UserMatchViewerFunction;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.config.value.FileValue;
import password.pwm.config.value.ValueFactory;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.DatabaseStatusChecker;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthMonitor;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.health.HealthTopic;
import password.pwm.health.LDAPStatusChecker;
import password.pwm.http.ContextManager;
import password.pwm.http.HttpMethod;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.http.bean.ConfigGuideBean;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.http.servlet.configeditor.ConfigEditorServlet;
import password.pwm.http.servlet.configeditor.ConfigEditorServletUtils;
import password.pwm.i18n.Message;
import password.pwm.ldap.LdapBrowser;
import password.pwm.ldap.schema.SchemaOperationResult;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.X509Utils;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.bean.HealthData;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@WebServlet(
        name = "ConfigGuideServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/config/config-guide",
                PwmConstants.URL_PREFIX_PRIVATE + "/config/ConfigGuide"
        }
)
public class ConfigGuideServlet extends ControlledPwmServlet
{

    private static final PwmLogger LOGGER = PwmLogger.getLogger( ConfigGuideServlet.class.getName() );

    private static final String LDAP_PROFILE_KEY = "default";

    public enum ConfigGuideAction implements AbstractPwmServlet.ProcessAction
    {
        ldapHealth( HttpMethod.GET ),
        updateForm( HttpMethod.POST ),
        gotoStep( HttpMethod.POST ),
        useConfiguredCerts( HttpMethod.POST ),
        uploadConfig( HttpMethod.POST ),
        extendSchema( HttpMethod.POST ),
        viewAdminMatches( HttpMethod.POST ),
        browseLdap( HttpMethod.POST ),
        uploadJDBCDriver( HttpMethod.POST ),
        skipGuide( HttpMethod.POST ),
        readSetting( HttpMethod.POST ),
        writeSetting( HttpMethod.POST ),
        settingData( HttpMethod.GET ),;


        private final HttpMethod method;

        ConfigGuideAction( final HttpMethod method )
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( method );
        }
    }

    @Override
    public Class<? extends ProcessAction> getProcessActionsClass( )
    {
        return ConfigGuideAction.class;
    }

    private ConfigGuideBean getBean( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, ConfigGuideBean.class );
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        ConfigGuideUtils.forwardToJSP( pwmRequest );
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        if ( pwmApplication.getSessionStateService().getBean( pwmRequest, ConfigGuideBean.class ).getStep() == GuideStep.START )
        {
            pwmApplication.getSessionStateService().clearBean( pwmRequest, ConfigGuideBean.class );
        }

        final ConfigGuideBean configGuideBean = pwmApplication.getSessionStateService().getBean( pwmRequest, ConfigGuideBean.class );

        if ( pwmApplication.getApplicationMode() != PwmApplicationMode.NEW )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "ConfigGuide unavailable unless in NEW mode" );
            LOGGER.error( pwmRequest, errorInformation.toDebugStr() );
            throw new PwmUnrecoverableException( errorInformation );
        }

        if ( !configGuideBean.getFormData().containsKey( ConfigGuideFormField.PARAM_APP_SITEURL ) )
        {
            final URI uri = URI.create( pwmRequest.getHttpServletRequest().getRequestURL().toString() );
            final int port = PwmURL.portForUriSchema( uri );
            final String newUri = uri.getScheme() + "://" + uri.getHost() + ":" + port + pwmRequest.getContextPath();
            configGuideBean.getFormData().put( ConfigGuideFormField.PARAM_APP_SITEURL, newUri );
        }

        if ( configGuideBean.getStep() == GuideStep.LDAP_CERT )
        {
            final String ldapServerString = ConfigGuideForm.figureLdapUrlFromFormConfig( configGuideBean.getFormData() );
            try
            {
                final URI ldapServerUri = new URI( ldapServerString );
                if ( "ldaps".equalsIgnoreCase( ldapServerUri.getScheme() ) )
                {
                    configGuideBean.setLdapCertificates( X509Utils.readRemoteCertificates( ldapServerUri ) );
                    configGuideBean.setCertsTrustedbyKeystore( X509Utils.testIfLdapServerCertsInDefaultKeystore( ldapServerUri ) );
                }
                else
                {
                    configGuideBean.setLdapCertificates( null );
                    configGuideBean.setCertsTrustedbyKeystore( false );
                }
            }
            catch ( Exception e )
            {
                LOGGER.error( "error reading/testing ldap server certificates: " + e.getMessage() );
            }
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "uploadConfig" )
    private ProcessStatus restUploadConfig( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        ConfigGuideUtils.restUploadConfig( pwmRequest );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "useConfiguredCerts" )
    private ProcessStatus restUseConfiguredCerts(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException
    {
        final ConfigGuideBean configGuideBean = getBean( pwmRequest );

        final boolean value = Boolean.parseBoolean( pwmRequest.readParameterAsString( "value" ) );
        configGuideBean.setUseConfiguredCerts( value );
        pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "ldapHealth" )
    private ProcessStatus restLdapHealth(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigGuideBean configGuideBean = getBean( pwmRequest );

        final StoredConfigurationImpl storedConfigurationImpl = ConfigGuideForm.generateStoredConfig( configGuideBean );
        final Configuration tempConfiguration = new Configuration( storedConfigurationImpl );
        final PwmApplication tempApplication = new PwmApplication( pwmRequest.getPwmApplication()
                .getPwmEnvironment()
                .makeRuntimeInstance( tempConfiguration ) );

        final LDAPStatusChecker ldapStatusChecker = new LDAPStatusChecker();
        final List<HealthRecord> records = new ArrayList<>();
        final LdapProfile ldapProfile = tempConfiguration.getDefaultLdapProfile();

        switch ( configGuideBean.getStep() )
        {
            case LDAP_SERVER:
            {
                try
                {
                    ConfigGuideUtils.checkLdapServer( configGuideBean );
                    records.add( password.pwm.health.HealthRecord.forMessage( HealthMessage.LDAP_OK ) );
                }
                catch ( Exception e )
                {
                    records.add( new HealthRecord( HealthStatus.WARN, HealthTopic.LDAP, "Can not connect to remote server: " + e.getMessage() ) );
                }
            }
            break;


            case LDAP_PROXY:
            {
                records.addAll( ldapStatusChecker.checkBasicLdapConnectivity( tempApplication, tempConfiguration, ldapProfile, false ) );
                if ( records.isEmpty() )
                {
                    records.add( password.pwm.health.HealthRecord.forMessage( HealthMessage.LDAP_OK ) );
                }
            }
            break;

            case LDAP_CONTEXT:
            {
                records.addAll( ldapStatusChecker.checkBasicLdapConnectivity( tempApplication, tempConfiguration, ldapProfile, true ) );
                if ( records.isEmpty() )
                {
                    records.add( new HealthRecord( HealthStatus.GOOD, HealthTopic.LDAP, "LDAP Contextless Login Root validated" ) );
                }
            }
            break;

            case LDAP_ADMINS:
            {
                try
                {
                    final UserMatchViewerFunction userMatchViewerFunction = new UserMatchViewerFunction();
                    final Collection<UserIdentity> results = userMatchViewerFunction.discoverMatchingUsers(
                            pwmRequest.getPwmApplication(),
                            2,
                            storedConfigurationImpl,
                            PwmSetting.QUERY_MATCH_PWM_ADMIN,
                            null
                    );

                    if ( results.isEmpty() )
                    {
                        records.add( new HealthRecord( HealthStatus.WARN, HealthTopic.LDAP, "No matching admin users" ) );
                    }
                    else
                    {
                        records.add( new HealthRecord( HealthStatus.GOOD, HealthTopic.LDAP, "Admin group validated" ) );
                    }
                }
                catch ( PwmException e )
                {
                    records.add( new HealthRecord( HealthStatus.WARN, HealthTopic.LDAP, "Error during admin group validation: " + e.getErrorInformation().toDebugStr() ) );
                }
                catch ( Exception e )
                {
                    records.add( new HealthRecord( HealthStatus.WARN, HealthTopic.LDAP, "Error during admin group validation: " + e.getMessage() ) );
                }
            }
            break;

            case LDAP_TESTUSER:
            {
                final String testUserValue = configGuideBean.getFormData().get( ConfigGuideFormField.PARAM_LDAP_TEST_USER );
                if ( testUserValue != null && !testUserValue.isEmpty() )
                {
                    records.addAll( ldapStatusChecker.checkBasicLdapConnectivity( tempApplication, tempConfiguration, ldapProfile, false ) );
                    records.addAll( ldapStatusChecker.doLdapTestUserCheck( tempConfiguration, ldapProfile, tempApplication ) );
                }
                else
                {
                    records.add( new HealthRecord( HealthStatus.CAUTION, HealthTopic.LDAP, "No test user specified" ) );
                }
            }
            break;

            case DATABASE:
            {
                records.addAll( DatabaseStatusChecker.checkNewDatabaseStatus( pwmRequest.getPwmApplication(), tempConfiguration ) );
            }
            break;

            default:
                JavaHelper.unhandledSwitchStatement( configGuideBean.getStep() );
        }

        final HealthData jsonOutput = new HealthData();
        jsonOutput.records = password.pwm.ws.server.rest.bean.HealthRecord.fromHealthRecords( records,
                pwmRequest.getLocale(), tempConfiguration );
        jsonOutput.timestamp = Instant.now();
        jsonOutput.overall = HealthMonitor.getMostSevereHealthStatus( records ).toString();
        final RestResultBean restResultBean = RestResultBean.withData( jsonOutput );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "viewAdminMatches" )
    private ProcessStatus restViewAdminMatches(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final ConfigGuideBean configGuideBean = getBean( pwmRequest );

        try
        {
            final UserMatchViewerFunction userMatchViewerFunction = new UserMatchViewerFunction();
            final StoredConfigurationImpl storedConfiguration = ConfigGuideForm.generateStoredConfig( configGuideBean );
            final Serializable output = userMatchViewerFunction.provideFunction( pwmRequest, storedConfiguration, PwmSetting.QUERY_MATCH_PWM_ADMIN, null, null );
            pwmRequest.outputJsonResult( RestResultBean.withData( output ) );
        }
        catch ( PwmException e )
        {
            LOGGER.error( pwmRequest, e.getErrorInformation() );
            pwmRequest.respondWithError( e.getErrorInformation(), false );
        }
        catch ( Exception e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, "error while testing matches = " + e.getMessage() );
            LOGGER.error( pwmRequest, errorInformation );
            pwmRequest.respondWithError( errorInformation );
        }
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "browseLdap" )
    private ProcessStatus restBrowseLdap(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigGuideBean configGuideBean = getBean( pwmRequest );

        final StoredConfigurationImpl storedConfiguration = ConfigGuideForm.generateStoredConfig( configGuideBean );
        if ( configGuideBean.getStep() == GuideStep.LDAP_PROXY )
        {
            storedConfiguration.resetSetting( PwmSetting.LDAP_PROXY_USER_DN, LDAP_PROFILE_KEY, null );
            storedConfiguration.resetSetting( PwmSetting.LDAP_PROXY_USER_PASSWORD, LDAP_PROFILE_KEY, null );
        }

        final Instant startTime = Instant.now();
        final Map<String, String> inputMap = pwmRequest.readBodyAsJsonStringMap( PwmHttpRequestWrapper.Flag.BypassValidation );
        final String profile = inputMap.get( "profile" );
        final String dn = inputMap.getOrDefault( "dn", "" );

        final LdapBrowser ldapBrowser = new LdapBrowser(
                pwmRequest.getPwmApplication().getLdapConnectionService().getChaiProviderFactory(),
                storedConfiguration
        );
        final LdapBrowser.LdapBrowseResult result = ldapBrowser.doBrowse( profile, dn );
        ldapBrowser.close();

        LOGGER.trace( pwmRequest, "performed ldapBrowse operation in "
                + TimeDuration.fromCurrent( startTime ).asCompactString()
                + ", result=" + JsonUtil.serialize( result ) );

        pwmRequest.outputJsonResult( RestResultBean.withData( result ) );

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "updateForm" )
    private ProcessStatus restUpdateForm(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigGuideBean configGuideBean = getBean( pwmRequest );

        final String bodyString = pwmRequest.readRequestBodyAsString();
        final Map<ConfigGuideFormField, String> incomingFormData = JsonUtil.deserialize( bodyString, new TypeToken<Map<ConfigGuideFormField, String>>()
        {
        } );

        if ( incomingFormData != null )
        {
            configGuideBean.getFormData().putAll( incomingFormData );
        }

        pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "gotoStep" )
    private ProcessStatus restGotoStep( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final ConfigGuideBean configGuideBean = getBean( pwmRequest );

        final String requestedStep = pwmRequest.readParameterAsString( "step" );
        GuideStep step = GuideStep.START;
        if ( requestedStep != null && requestedStep.length() > 0 )
        {
            try
            {
                step = GuideStep.valueOf( requestedStep );
            }
            catch ( IllegalArgumentException e )
            {
                final String errorMsg = "unknown goto step request: " + requestedStep;
                LOGGER.error( pwmRequest, errorMsg );
            }
        }

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

        if ( step == GuideStep.FINISH )
        {
            final ContextManager contextManager = ContextManager.getContextManager( pwmRequest );
            try
            {
                ConfigGuideUtils.writeConfig( contextManager, configGuideBean );
                pwmRequest.getPwmSession().getSessionStateBean().setTheme( null );
            }
            catch ( PwmException e )
            {
                final RestResultBean restResultBean = RestResultBean.fromError( e.getErrorInformation(), pwmRequest );
                pwmRequest.outputJsonResult( restResultBean );
                return ProcessStatus.Halt;
            }
            catch ( Exception e )
            {
                final RestResultBean restResultBean = RestResultBean.fromError( new ErrorInformation(
                        PwmError.ERROR_UNKNOWN,
                        "error during save: " + e.getMessage()
                ), pwmRequest );
                pwmRequest.outputJsonResult( restResultBean );
                return ProcessStatus.Halt;
            }
            final HashMap<String, String> resultData = new HashMap<>();
            resultData.put( "serverRestart", "true" );
            pwmRequest.outputJsonResult( RestResultBean.withData( resultData ) );
            pwmRequest.invalidateSession();
        }
        else
        {
            configGuideBean.setStep( step );
            pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
            LOGGER.trace( "setting current step to: " + step );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "extendSchema" )
    private ProcessStatus restExtendSchema( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigGuideBean configGuideBean = getBean( pwmRequest );

        try
        {
            final SchemaOperationResult schemaOperationResult = ConfigGuideUtils.extendSchema( pwmRequest.getPwmApplication(), configGuideBean, true );
            pwmRequest.outputJsonResult( RestResultBean.withData( schemaOperationResult.getOperationLog() ) );
        }
        catch ( Exception e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, e.getMessage() );
            pwmRequest.outputJsonResult( RestResultBean.fromError( errorInformation, pwmRequest ) );
            LOGGER.error( pwmRequest, e.getMessage(), e );
        }

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "uploadJDBCDriver" )
    private ProcessStatus restUploadJDBCDriver( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        try
        {
            final ConfigGuideBean configGuideBean = getBean( pwmRequest );
            final int maxFileSize = Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.CONFIG_MAX_JDBC_JAR_SIZE ) );
            final FileValue fileValue = ConfigEditorServletUtils.readFileUploadToSettingValue( pwmRequest, maxFileSize );
            configGuideBean.setDatabaseDriver( fileValue );
            final RestResultBean restResultBean = RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown );
            pwmRequest.getPwmResponse().outputJsonResult( restResultBean );
        }
        catch ( PwmException e )
        {
            final RestResultBean restResultBean = RestResultBean.fromError( e.getErrorInformation(), pwmRequest );
            pwmRequest.getPwmResponse().outputJsonResult( restResultBean );
            LOGGER.error( pwmRequest, e.getErrorInformation().toDebugStr() );
        }

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "skipGuide" )
    private ProcessStatus restSkipGuide( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException
    {
        final Map<String, String> inputJson = pwmRequest.readBodyAsJsonStringMap( PwmHttpRequestWrapper.Flag.BypassValidation );
        final String password = inputJson.get( "password" );
        final ContextManager contextManager = ContextManager.getContextManager( pwmRequest );
        try
        {
            final StoredConfigurationImpl storedConfiguration = new StoredConfigurationImpl();
            storedConfiguration.writeConfigProperty( ConfigurationProperty.CONFIG_IS_EDITABLE, "true" );
            storedConfiguration.setPassword( password );
            ConfigGuideUtils.writeConfig( contextManager, storedConfiguration );
            pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
            pwmRequest.invalidateSession();
        }
        catch ( PwmOperationalException e )
        {
            LOGGER.error( "error during skip config guide: " + e.getMessage(), e );
        }

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "readSetting" )
    private ProcessStatus restReadSetting( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException
    {
        final String profileID = "default";
        final ConfigGuideBean configGuideBean = getBean( pwmRequest );
        final StoredConfigurationImpl storedConfigurationImpl = ConfigGuideForm.generateStoredConfig( configGuideBean );

        final String key = pwmRequest.readParameterAsString( "key" );
        final LinkedHashMap<String, Object> returnMap = new LinkedHashMap<>();
        final PwmSetting theSetting = PwmSetting.forKey( key );

        final Object returnValue;
        returnValue = storedConfigurationImpl.readSetting( theSetting, profileID ).toNativeObject();
        returnMap.put( "isDefault", storedConfigurationImpl.isDefaultValue( theSetting, profileID ) );
        returnMap.put( "key", key );
        returnMap.put( "category", theSetting.getCategory().toString() );
        returnMap.put( "syntax", theSetting.getSyntax().toString() );

        returnMap.put( "value", returnValue );
        pwmRequest.outputJsonResult( RestResultBean.withData( returnMap ) );

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "writeSetting" )
    private ProcessStatus restWriteSetting( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException
    {
        final String profileID = "default";
        final String key = pwmRequest.readParameterAsString( "key" );
        final String bodyString = pwmRequest.readRequestBodyAsString();
        final PwmSetting setting = PwmSetting.forKey( key );
        final ConfigGuideBean configGuideBean = getBean( pwmRequest );
        final StoredConfigurationImpl storedConfigurationImpl = ConfigGuideForm.generateStoredConfig( configGuideBean );


        final LinkedHashMap<String, Object> returnMap = new LinkedHashMap<>();

        try
        {
            final StoredValue storedValue = ValueFactory.fromJson( setting, bodyString );
            final List<String> errorMsgs = storedValue.validateValue( setting );
            if ( errorMsgs != null && !errorMsgs.isEmpty() )
            {
                returnMap.put( "errorMessage", setting.getLabel( pwmRequest.getLocale() ) + ": " + errorMsgs.get( 0 ) );
            }

            if ( setting == PwmSetting.CHALLENGE_RANDOM_CHALLENGES )
            {
                configGuideBean.getFormData().put( ConfigGuideFormField.CHALLENGE_RESPONSE_DATA, JsonUtil.serialize( (Serializable) storedValue.toNativeObject() ) );
            }
        }
        catch ( Exception e )
        {
            final String errorMsg = "error writing default value for setting " + setting.toString() + ", error: " + e.getMessage();
            LOGGER.error( errorMsg, e );
            throw new IllegalStateException( errorMsg, e );
        }
        returnMap.put( "key", key );
        returnMap.put( "category", setting.getCategory().toString() );
        returnMap.put( "syntax", setting.getSyntax().toString() );
        returnMap.put( "isDefault", storedConfigurationImpl.isDefaultValue( setting, profileID ) );
        pwmRequest.outputJsonResult( RestResultBean.withData( returnMap ) );


        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "settingData" )
    private ProcessStatus restSettingData( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigGuideBean configGuideBean = getBean( pwmRequest );
        final StoredConfigurationImpl storedConfigurationImpl = ConfigGuideForm.generateStoredConfig( configGuideBean );

        final LinkedHashMap<String, Object> returnMap = new LinkedHashMap<>( ConfigEditorServlet.generateSettingData(
                pwmRequest.getPwmApplication(),
                storedConfigurationImpl,
                pwmRequest.getSessionLabel(),
                pwmRequest.getLocale()
        )
        );

        final RestResultBean restResultBean = RestResultBean.withData( new LinkedHashMap<>( returnMap ) );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }
}


