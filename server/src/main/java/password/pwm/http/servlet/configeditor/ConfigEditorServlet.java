/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.http.servlet.configeditor;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.Builder;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SmsItemBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingTemplate;
import password.pwm.config.SettingUIFunction;
import password.pwm.config.profile.EmailServerProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.StoredConfigItemKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.config.value.FileValue;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.ValueFactory;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.DatabaseStatusChecker;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.health.HealthTopic;
import password.pwm.health.LDAPHealthChecker;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.http.servlet.configmanager.ConfigManagerServlet;
import password.pwm.i18n.Config;
import password.pwm.i18n.Message;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.ldap.LdapBrowser;
import password.pwm.svc.email.EmailServer;
import password.pwm.svc.email.EmailServerUtil;
import password.pwm.svc.email.EmailService;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.password.RandomPasswordGenerator;
import password.pwm.util.queue.SmsQueueManager;
import password.pwm.util.secure.HttpsServerCertificateManager;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.RestRandomPasswordServer;
import password.pwm.ws.server.rest.bean.HealthData;

import javax.mail.MessagingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@WebServlet(
        name = "ConfigEditorServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/config/editor",
                PwmConstants.URL_PREFIX_PRIVATE + "/config/editor/*",
                PwmConstants.URL_PREFIX_PRIVATE + "/config/configeditor",
                PwmConstants.URL_PREFIX_PRIVATE + "/config/configeditor/*",
                PwmConstants.URL_PREFIX_PRIVATE + "/config/ConfigEditor",
                PwmConstants.URL_PREFIX_PRIVATE + "/config/ConfigEditor/*",
        }
)
public class ConfigEditorServlet extends ControlledPwmServlet
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigEditorServlet.class );

    public enum ConfigEditorAction implements AbstractPwmServlet.ProcessAction
    {
        readSetting( HttpMethod.POST ),
        writeSetting( HttpMethod.POST ),
        resetSetting( HttpMethod.POST ),
        ldapHealthCheck( HttpMethod.POST ),
        databaseHealthCheck( HttpMethod.POST ),
        smsHealthCheck( HttpMethod.POST ),
        emailHealthCheck( HttpMethod.POST ),
        finishEditing( HttpMethod.POST ),
        executeSettingFunction( HttpMethod.POST ),
        setConfigurationPassword( HttpMethod.POST ),
        readChangeLog( HttpMethod.POST ),
        search( HttpMethod.POST ),
        cancelEditing( HttpMethod.POST ),
        uploadFile( HttpMethod.POST ),
        setOption( HttpMethod.POST ),
        menuTreeData( HttpMethod.POST ),
        settingData( HttpMethod.GET ),
        testMacro( HttpMethod.POST ),
        browseLdap( HttpMethod.POST ),
        copyProfile( HttpMethod.POST ),
        randomPassword( HttpMethod.POST ),;

        private final HttpMethod method;

        ConfigEditorAction( final HttpMethod method )
        {
            this.method = method;
        }

        @Override
        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( method );
        }
    }

    @Override
    public Class<? extends ProcessAction> getProcessActionsClass( )
    {
        return ConfigEditorAction.class;
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        ConfigManagerServlet.verifyConfigAccess( pwmRequest );

        final ConfigManagerBean configManagerBean = getBean( pwmRequest );

        if ( configManagerBean.getStoredConfiguration() == null )
        {
            final StoredConfiguration loadedConfig = ConfigManagerServlet.readCurrentConfiguration( pwmRequest );
            configManagerBean.setStoredConfiguration( loadedConfig );
        }

        return ProcessStatus.Continue;
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        pwmRequest.forwardToJsp( JspUrl.CONFIG_MANAGER_EDITOR );
    }

    private ConfigManagerBean getBean( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, ConfigManagerBean.class );
    }

    @ActionHandler( action = "executeSettingFunction" )
    private ProcessStatus restExecuteSettingFunction(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final String bodyString = pwmRequest.readRequestBodyAsString();
        final Map<String, String> requestMap = JsonUtil.deserializeStringMap( bodyString );
        final PwmSetting pwmSetting = PwmSetting.forKey( requestMap.get( "setting" ) )
                .orElseThrow( () -> new IllegalStateException( "invalid setting parameter value" ) );
        final String functionName = requestMap.get( "function" );
        final String profileID = pwmSetting.getCategory().hasProfiles() ? pwmRequest.readParameterAsString( "profile" ) : null;
        final String extraData = requestMap.get( "extraData" );

        try
        {
            final Class implementingClass = Class.forName( functionName );
            final SettingUIFunction function = ( SettingUIFunction ) implementingClass.getDeclaredConstructor().newInstance();
            final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( configManagerBean.getStoredConfiguration() );
            final Serializable result = function.provideFunction( pwmRequest, modifier, pwmSetting, profileID, extraData );
            configManagerBean.setStoredConfiguration( modifier.newStoredConfiguration() );
            final RestResultBean restResultBean = RestResultBean.forSuccessMessage( result, pwmRequest, Message.Success_Unknown );
            pwmRequest.outputJsonResult( restResultBean );
        }
        catch ( final Exception e )
        {
            final RestResultBean restResultBean;
            if ( e instanceof PwmException )
            {
                restResultBean = RestResultBean.fromError( ( ( PwmException ) e ).getErrorInformation(), pwmRequest, true );
            }
            else
            {
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, "error performing user search: " + e.getMessage() );
                restResultBean = RestResultBean.fromError( errorInformation, pwmRequest );
            }
            pwmRequest.outputJsonResult( restResultBean );
        }

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "readSetting" )
    private ProcessStatus restReadSetting(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final StoredConfiguration storedConfig = configManagerBean.getStoredConfiguration();
        final String key = pwmRequest.readParameterAsString( "key" );

        final ReadSettingResponse readSettingResponse;
        if ( key.startsWith( "localeBundle" ) )
        {
            readSettingResponse = ConfigEditorServletUtils.handleLocaleBundleReadSetting( pwmRequest, storedConfig, key );
        }
        else
        {
            readSettingResponse = ConfigEditorServletUtils.handleReadSetting( pwmRequest, storedConfig, key );
        }

        pwmRequest.outputJsonResult( RestResultBean.withData( readSettingResponse ) );
        return ProcessStatus.Halt;
    }

    @Value
    @Builder
    static class ReadSettingResponse implements Serializable
    {
        private final boolean isDefault;
        private final String key;
        private final String category;
        private final Instant modifyTime;
        private final UserIdentity modifyUser;
        private final String syntax;
        private final Object value;
        private final Map<String, String> options;
    }

    @ActionHandler( action = "writeSetting" )
    private ProcessStatus restWriteSetting(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( configManagerBean.getStoredConfiguration() );
        final String key = pwmRequest.readParameterAsString( "key" );
        final String bodyString = pwmRequest.readRequestBodyAsString();
        final UserIdentity loggedInUser = pwmRequest.getUserInfoIfLoggedIn();
        final ReadSettingResponse readSettingResponse;

        final StoredConfiguration storedConfiguration;
        if ( key.startsWith( "localeBundle" ) )
        {
            final StringTokenizer st = new StringTokenizer( key, "-" );
            st.nextToken();
            final PwmLocaleBundle pwmLocaleBundle = PwmLocaleBundle.forKey( st.nextToken() )
                    .orElseThrow( () -> new IllegalArgumentException( "unknown locale bundle name" ) );
            final String keyName = st.nextToken();
            final Map<String, String> valueMap = JsonUtil.deserializeStringMap( bodyString );
            final Map<String, String> outputMap = new LinkedHashMap<>( valueMap );

            modifier.writeLocaleBundleMap( pwmLocaleBundle, keyName, outputMap );
            storedConfiguration = modifier.newStoredConfiguration();
            readSettingResponse = ConfigEditorServletUtils.handleLocaleBundleReadSetting( pwmRequest, storedConfiguration, key );
        }
        else
        {
            final PwmSetting setting = PwmSetting.forKey( key )
                    .orElseThrow( () -> new IllegalStateException( "invalid setting parameter value" ) );
            final String profileID = setting.getCategory().hasProfiles() ? pwmRequest.readParameterAsString( "profile" ) : null;
            try
            {
                final StoredValue storedValue = ValueFactory.fromJson( setting, bodyString );
                modifier.writeSetting( setting, profileID, storedValue, loggedInUser );
                storedConfiguration = modifier.newStoredConfiguration();
            }
            catch ( final PwmOperationalException e )
            {
                throw new PwmUnrecoverableException( e.getErrorInformation() );
            }
            readSettingResponse = ConfigEditorServletUtils.handleReadSetting( pwmRequest, storedConfiguration, key );
        }
        configManagerBean.setStoredConfiguration( storedConfiguration );
        pwmRequest.outputJsonResult( RestResultBean.withData( readSettingResponse ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "resetSetting" )
    private ProcessStatus restResetSetting(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( configManagerBean.getStoredConfiguration() );
        final UserIdentity loggedInUser = pwmRequest.getUserInfoIfLoggedIn();
        final String key = pwmRequest.readParameterAsString( "key" );

        if ( key.startsWith( "localeBundle" ) )
        {
            final StringTokenizer st = new StringTokenizer( key, "-" );
            st.nextToken();
            final PwmLocaleBundle pwmLocaleBundle = PwmLocaleBundle.forKey( st.nextToken() )
                    .orElseThrow( () -> new IllegalArgumentException( "unknown locale bundle name" ) );
            final String keyName = st.nextToken();
            modifier.resetLocaleBundleMap( pwmLocaleBundle, keyName );
        }
        else
        {
            final PwmSetting setting = PwmSetting.forKey( key )
                    .orElseThrow( () -> new IllegalStateException( "invalid setting parameter value" ) );
            final String profileID = setting.getCategory().hasProfiles() ? pwmRequest.readParameterAsString( "profile" ) : null;
            modifier.resetSetting( setting, profileID, loggedInUser );
        }

        configManagerBean.setStoredConfiguration( modifier.newStoredConfiguration() );
        pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "setConfigurationPassword" )
    private ProcessStatus restSetConfigurationPassword(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( configManagerBean.getStoredConfiguration() );

        try
        {
            final Map<String, String> postData = pwmRequest.readBodyAsJsonStringMap();
            final String password = postData.get( "password" );
            StoredConfigurationUtil.setPassword( modifier, password );
            configManagerBean.setPasswordVerified( true );
            LOGGER.debug( pwmRequest, () -> "config password updated" );
            final RestResultBean restResultBean = RestResultBean.forConfirmMessage( pwmRequest, Config.Confirm_ConfigPasswordStored );

            pwmRequest.outputJsonResult( restResultBean );
        }
        catch ( final PwmOperationalException e )
        {
            final RestResultBean restResultBean = RestResultBean.fromError( e.getErrorInformation(), pwmRequest );
            pwmRequest.outputJsonResult( restResultBean );
        }

        configManagerBean.setStoredConfiguration( modifier.newStoredConfiguration() );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "finishEditing" )
    private ProcessStatus restFinishEditing( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final List<String> validationErrors = StoredConfigurationUtil.validateValues( configManagerBean.getStoredConfiguration() );
        if ( !validationErrors.isEmpty() )
        {
            final String errorString = validationErrors.get( 0 );
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, errorString, new String[]
                    {
                            errorString,
                    }
            );
            pwmRequest.outputJsonResult( RestResultBean.fromError( errorInfo, pwmRequest ) );
            LOGGER.error( pwmRequest, errorInfo );
            return ProcessStatus.Halt;
        }
        else
        {
            try
            {
                ConfigManagerServlet.saveConfiguration( pwmRequest, configManagerBean.getStoredConfiguration() );
                configManagerBean.setStoredConfiguration( null );
                configManagerBean.setStoredConfiguration( null );
                LOGGER.debug( pwmRequest, () -> "save configuration operation completed" );
                pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
            }
            catch ( final PwmUnrecoverableException e )
            {
                final ErrorInformation errorInfo = e.getErrorInformation();
                pwmRequest.outputJsonResult( RestResultBean.fromError( errorInfo, pwmRequest ) );
                LOGGER.error( pwmRequest, errorInfo );
            }
        }

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "cancelEditing" )
    private ProcessStatus restCancelEditing(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        configManagerBean.setStoredConfiguration( null );
        pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "setOption" )
    private ProcessStatus setOptions(
            final PwmRequest pwmRequest

    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final StoredConfigurationModifier modifer = StoredConfigurationModifier.newModifier( configManagerBean.getStoredConfiguration() );
        {
            final String updateDescriptionTextCmd = pwmRequest.readParameterAsString( "updateNotesText" );
            if ( StringUtil.nullSafeEqualsIgnoreCase( "true", updateDescriptionTextCmd ) )
            {
                try
                {
                    final String bodyString = pwmRequest.readRequestBodyAsString();
                    final String value = JsonUtil.deserialize( bodyString, String.class );
                    modifer.writeConfigProperty( ConfigurationProperty.NOTES, value );
                    LOGGER.trace( () -> "updated notesText" );
                }
                catch ( final Exception e )
                {
                    LOGGER.error( () -> "error updating notesText: " + e.getMessage() );
                }
            }
            {
                final String requestedTemplate = pwmRequest.readParameterAsString( "template" );
                if ( requestedTemplate != null && requestedTemplate.length() > 0 )
                {
                    try
                    {
                        final PwmSettingTemplate template = PwmSettingTemplate.valueOf( requestedTemplate );
                        modifer.writeConfigProperty( ConfigurationProperty.LDAP_TEMPLATE, template.toString() );
                        LOGGER.trace( () -> "setting template to: " + requestedTemplate );
                    }
                    catch ( final IllegalArgumentException e )
                    {
                        modifer.writeConfigProperty( ConfigurationProperty.LDAP_TEMPLATE, PwmSettingTemplate.DEFAULT.toString() );
                        LOGGER.error( () -> "unknown template set request: " + requestedTemplate );
                    }
                }
            }
        }

        configManagerBean.setStoredConfiguration( modifer.newStoredConfiguration() );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "readChangeLog" )
    private ProcessStatus restReadChangeLog(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );


        final Map<String, Object> returnObj = new ConcurrentHashMap<>();
        final ExecutorService executor = Executors.newFixedThreadPool( 3 );
        executor.execute( () -> ConfigEditorServletUtils.outputChangeLogData( pwmRequest, configManagerBean, returnObj ) );
        executor.execute( () -> returnObj.put( "health", ConfigEditorServletUtils.configurationHealth( pwmRequest, configManagerBean ) ) );
        JavaHelper.closeAndWaitExecutor( executor, TimeDuration.MINUTE );

        final RestResultBean restResultBean = RestResultBean.withData( new HashMap<>( returnObj ) );
        pwmRequest.outputJsonResult( restResultBean );

        return ProcessStatus.Halt;
    }



    @ActionHandler( action = "search" )
    private ProcessStatus restSearchSettings(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final String bodyData = pwmRequest.readRequestBodyAsString();
        final Map<String, String> valueMap = JsonUtil.deserializeStringMap( bodyData );
        final Locale locale = pwmRequest.getLocale();
        final RestResultBean restResultBean;
        final String searchTerm = valueMap.get( "search" );
        final StoredConfiguration storedConfiguration = configManagerBean.getStoredConfiguration();

        if ( searchTerm != null && !searchTerm.isEmpty() )
        {
            final Set<StoredConfigItemKey> searchResults = StoredConfigurationUtil.search( storedConfiguration, searchTerm, locale );
            final ConcurrentHashMap<String, Map<String, SearchResultItem>> returnData = new ConcurrentHashMap<>();

            searchResults
                    .parallelStream()
                    .filter( key -> key.getRecordType() == StoredConfigItemKey.RecordType.SETTING )
                    .forEach( recordID ->
                    {
                        final PwmSetting setting = recordID.toPwmSetting();
                        final SearchResultItem item = new SearchResultItem(
                                setting.getCategory().toString(),
                                storedConfiguration.readSetting( setting, recordID.getProfileID() ).toDebugString( locale ),
                                setting.getCategory().toMenuLocationDebug( recordID.getProfileID(), locale ),
                                storedConfiguration.isDefaultValue( setting, recordID.getProfileID() ),
                                recordID.getProfileID()
                        );
                        final String returnCategory = item.getNavigation();


                        returnData.putIfAbsent( returnCategory, new ConcurrentHashMap<>() );
                        returnData.get( returnCategory ).put( setting.getKey(), item );
                    } );

            final TreeMap<String, Map<String, SearchResultItem>> outputMap = new TreeMap<>();
            for ( final String key : returnData.keySet() )
            {
                outputMap.put( key, new TreeMap<>( returnData.get( key ) ) );
            }

            restResultBean = RestResultBean.withData( outputMap );
            LOGGER.trace( pwmRequest, () -> "finished search operation with " + returnData.size() + " results in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        }
        else
        {
            restResultBean = RestResultBean.withData( new ArrayList() );
        }

        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "ldapHealthCheck" )
    private ProcessStatus restLdapHealthCheck(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        LOGGER.debug( pwmRequest, () -> "beginning restLdapHealthCheck" );
        final String profileID = pwmRequest.readParameterAsString( "profile" );
        final Configuration config = new Configuration( configManagerBean.getStoredConfiguration() );
        final HealthData healthData = LDAPHealthChecker.healthForNewConfiguration( pwmRequest.getPwmApplication(), config, pwmRequest.getLocale(), profileID, true, true );
        final RestResultBean restResultBean = RestResultBean.withData( healthData );

        pwmRequest.outputJsonResult( restResultBean );
        LOGGER.debug( pwmRequest, () -> "completed restLdapHealthCheck in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "databaseHealthCheck" )
    private ProcessStatus restDatabaseHealthCheck(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        LOGGER.debug( pwmRequest, () -> "beginning restDatabaseHealthCheck" );
        final Configuration config = new Configuration( configManagerBean.getStoredConfiguration() );
        final List<HealthRecord> healthRecords = DatabaseStatusChecker.checkNewDatabaseStatus( pwmRequest.getPwmApplication(), config );
        final HealthData healthData = HealthRecord.asHealthDataBean( config, pwmRequest.getLocale(), healthRecords );
        final RestResultBean restResultBean = RestResultBean.withData( healthData );
        pwmRequest.outputJsonResult( restResultBean );
        LOGGER.debug( pwmRequest, () -> "completed restDatabaseHealthCheck in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "smsHealthCheck" )
    private ProcessStatus restSmsHealthCheck(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        LOGGER.debug( pwmRequest, () -> "beginning restSmsHealthCheck" );

        final List<HealthRecord> returnRecords = new ArrayList<>();
        final Configuration config = new Configuration( configManagerBean.getStoredConfiguration() );

        if ( !SmsQueueManager.smsIsConfigured( config ) )
        {
            returnRecords.add( new HealthRecord( HealthStatus.INFO, HealthTopic.SMS, "SMS not configured" ) );
        }
        else
        {
            final Map<String, String> testParams = pwmRequest.readBodyAsJsonStringMap();
            final SmsItemBean testSmsItem = new SmsItemBean( testParams.get( "to" ), testParams.get( "message" ), pwmRequest.getLabel() );
            try
            {
                final String responseBody = SmsQueueManager.sendDirectMessage(
                        pwmRequest.getPwmApplication(),
                        config,
                        pwmRequest.getLabel(),
                        testSmsItem
                );
                returnRecords.add( new HealthRecord( HealthStatus.INFO, HealthTopic.SMS, "message sent" ) );
                returnRecords.add( new HealthRecord( HealthStatus.INFO, HealthTopic.SMS, "response body: \n" + StringUtil.escapeHtml( responseBody ) ) );
            }
            catch ( final PwmException e )
            {
                returnRecords.add( new HealthRecord( HealthStatus.WARN, HealthTopic.SMS, "unable to send message: " + e.getMessage() ) );
            }
        }

        final HealthData healthData = HealthRecord.asHealthDataBean( config, pwmRequest.getLocale(), returnRecords );
        final RestResultBean restResultBean = RestResultBean.withData( healthData );
        pwmRequest.outputJsonResult( restResultBean );
        LOGGER.debug( pwmRequest, () -> "completed restSmsHealthCheck in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "emailHealthCheck" )
    private ProcessStatus restEmailHealthCheck(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final String profileID = pwmRequest.readParameterAsString( "profile" );

        LOGGER.debug( pwmRequest, () -> "beginning restEmailHealthCheck" );

        final Map<String, String> params = pwmRequest.readBodyAsJsonStringMap();
        final EmailItemBean testEmailItem = new EmailItemBean( params.get( "to" ), params.get( "from" ), params.get( "subject" ), params.get( "body" ), null );

        final List<HealthRecord> returnRecords = new ArrayList<>();

        final Configuration testConfiguration = new Configuration( configManagerBean.getStoredConfiguration() );

        final EmailServerProfile emailServerProfile = testConfiguration.getEmailServerProfiles().get( profileID );
        if ( emailServerProfile != null )
        {
            final Optional<EmailServer> emailServer = EmailServerUtil.makeEmailServer( testConfiguration, emailServerProfile, null );
            if ( emailServer.isPresent() )
            {
                final MacroMachine macroMachine = MacroMachine.forUser( pwmRequest, pwmRequest.getUserInfoIfLoggedIn() );

                try
                {
                    EmailService.sendEmailSynchronous( emailServer.get(), testConfiguration, testEmailItem, macroMachine );
                    returnRecords.add( new HealthRecord( HealthStatus.INFO, HealthTopic.Email, "message sent" ) );
                }
                catch ( final MessagingException | PwmException e )
                {
                    returnRecords.add( new HealthRecord( HealthStatus.WARN, HealthTopic.Email, JavaHelper.readHostileExceptionMessage( e ) ) );
                }
            }
        }

        if ( returnRecords.isEmpty() )
        {
            returnRecords.add( new HealthRecord( HealthStatus.WARN, HealthTopic.Email, "smtp service is not configured." ) );
        }

        final HealthData healthData = HealthRecord.asHealthDataBean( testConfiguration, pwmRequest.getLocale(), returnRecords );
        final RestResultBean restResultBean = RestResultBean.withData( healthData );
        pwmRequest.outputJsonResult( restResultBean );
        LOGGER.debug( pwmRequest, () -> "completed restEmailHealthCheck in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "uploadFile" )
    private ProcessStatus doUploadFile(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( configManagerBean.getStoredConfiguration() );

        final String key = pwmRequest.readParameterAsString( "key" );
        final PwmSetting setting = PwmSetting.forKey( key )
                .orElseThrow( () -> new IllegalStateException( "invalid setting parameter value" ) );
        final int maxFileSize = Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.CONFIG_MAX_JDBC_JAR_SIZE ) );

        if ( setting == PwmSetting.HTTPS_CERT )
        {
            try
            {
                final PasswordData passwordData = pwmRequest.readParameterAsPassword( "password" );
                final String alias = pwmRequest.readParameterAsString( "alias" );
                final HttpsServerCertificateManager.KeyStoreFormat keyStoreFormat;
                try
                {
                    keyStoreFormat = HttpsServerCertificateManager.KeyStoreFormat.valueOf( pwmRequest.readParameterAsString( "format" ) );
                }
                catch ( final IllegalArgumentException e )
                {
                    throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, "unknown format type: " + e.getMessage(), new String[]
                            {
                                    "format",
                            }
                    ) );
                }

                final Map<String, PwmRequest.FileUploadItem> fileUploads = pwmRequest.readFileUploads( maxFileSize, 1 );
                final InputStream fileIs = fileUploads.get( PwmConstants.PARAM_FILE_UPLOAD ).getContent().newByteArrayInputStream();

                HttpsServerCertificateManager.importKey(
                        modifier,
                        keyStoreFormat,
                        fileIs,
                        passwordData,
                        alias
                );

                configManagerBean.setStoredConfiguration( modifier.newStoredConfiguration() );
                pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
                return ProcessStatus.Halt;
            }
            catch ( final PwmException e )
            {
                LOGGER.error( pwmRequest, () -> "error during https certificate upload: " + e.getMessage() );
                pwmRequest.respondWithError( e.getErrorInformation(), false );
                return ProcessStatus.Halt;
            }
        }

        final FileValue fileValue = ConfigEditorServletUtils.readFileUploadToSettingValue( pwmRequest, maxFileSize );
        if ( fileValue != null )
        {
            final UserIdentity userIdentity = pwmRequest.isAuthenticated()
                    ? pwmRequest.getPwmSession().getUserInfo().getUserIdentity()
                    : null;

            modifier.writeSetting( setting, null, fileValue, userIdentity );
            pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
        }

        configManagerBean.setStoredConfiguration( modifier.newStoredConfiguration() );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "menuTreeData" )
    private ProcessStatus restMenuTreeData(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );

        final ArrayList<NavTreeItem> navigationData = new ArrayList<>();
        final Map<String, Object> inputParameters = pwmRequest.readBodyAsJsonMap( PwmHttpRequestWrapper.Flag.BypassValidation );
        final boolean modifiedSettingsOnly = ( boolean ) inputParameters.get( "modifiedSettingsOnly" );
        final double level = ( double ) inputParameters.get( "level" );
        final String filterText = ( String ) inputParameters.get( "text" );

        {
            // root node
            final NavTreeItem categoryInfo = new NavTreeItem();
            categoryInfo.setId( "ROOT" );
            categoryInfo.setName( "ROOT" );
            navigationData.add( categoryInfo );
        }

        {
            final StoredConfiguration storedConfiguration = configManagerBean.getStoredConfiguration();
            final List<PwmSettingCategory> categories = NavTreeHelper.filteredCategories(
                    pwmRequest.getPwmApplication(),
                    storedConfiguration,
                    pwmRequest.getLocale(),
                    modifiedSettingsOnly,
                    level,
                    filterText
            );
            navigationData.addAll( NavTreeHelper.makeSettingNavItems( categories, storedConfiguration, pwmRequest.getLocale() ) );
        }

        boolean includeDisplayText = true;
        if ( level >= 1 )
        {
            for ( final PwmLocaleBundle localeBundle : PwmLocaleBundle.values() )
            {
                if ( !localeBundle.isAdminOnly() )
                {
                    final Set<String> modifiedKeys = new TreeSet<>();
                    if ( modifiedSettingsOnly )
                    {
                        modifiedKeys.addAll( NavTreeHelper.determineModifiedKeysSettings( localeBundle, pwmRequest.getConfig(), configManagerBean.getStoredConfiguration() ) );
                    }
                    if ( !modifiedSettingsOnly || !modifiedKeys.isEmpty() )
                    {
                        final NavTreeItem categoryInfo = new NavTreeItem();
                        categoryInfo.setId( localeBundle.toString() );
                        categoryInfo.setName( localeBundle.getTheClass().getSimpleName() );
                        categoryInfo.setParent( "DISPLAY_TEXT" );
                        categoryInfo.setType( NavTreeHelper.NavItemType.displayText );
                        categoryInfo.setKeys( new TreeSet<>( modifiedSettingsOnly ? modifiedKeys : localeBundle.getDisplayKeys() ) );
                        navigationData.add( categoryInfo );
                        includeDisplayText = true;
                    }
                }
            }
        }

        if ( includeDisplayText )
        {
            final NavTreeItem categoryInfo = new NavTreeItem();
            categoryInfo.setId( "DISPLAY_TEXT" );
            categoryInfo.setName( "Display Text" );
            categoryInfo.setType( NavTreeHelper.NavItemType.navigation );
            categoryInfo.setParent( "ROOT" );
            navigationData.add( categoryInfo );
        }

        NavTreeHelper.moveNavItemToTopOfList( PwmSettingCategory.NOTES.toString(), navigationData );
        NavTreeHelper.moveNavItemToTopOfList( PwmSettingCategory.TEMPLATES.toString(), navigationData );

        LOGGER.trace( pwmRequest, () -> "completed navigation tree data request in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        pwmRequest.outputJsonResult( RestResultBean.withData( navigationData ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "settingData" )
    private ProcessStatus restConfigSettingData( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final ConfigEditorServletUtils.SettingData settingData =  ConfigEditorServletUtils.generateSettingData(
                pwmRequest.getPwmApplication(),
                configManagerBean.getStoredConfiguration(),
                pwmRequest.getLabel(),
                pwmRequest.getLocale()
        );

        final RestResultBean restResultBean = RestResultBean.withData( settingData );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "testMacro" )
    private ProcessStatus restTestMacro( final PwmRequest pwmRequest ) throws IOException, ServletException
    {
        try
        {
            final Map<String, String> inputMap = pwmRequest.readBodyAsJsonStringMap( PwmHttpRequestWrapper.Flag.BypassValidation );
            if ( inputMap == null || !inputMap.containsKey( "input" ) )
            {
                pwmRequest.outputJsonResult( RestResultBean.withData( "missing input" ) );
                return ProcessStatus.Halt;
            }

            final MacroMachine macroMachine;
            if ( pwmRequest.isAuthenticated() )
            {
                macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine();
            }
            else
            {
                macroMachine = MacroMachine.forNonUserSpecific( pwmRequest.getPwmApplication(), pwmRequest.getLabel() );
            }
            final String input = inputMap.get( "input" );
            final String output = macroMachine.expandMacros( input );
            pwmRequest.outputJsonResult( RestResultBean.withData( output ) );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.error( pwmRequest, e.getErrorInformation() );
            pwmRequest.respondWithError( e.getErrorInformation() );
        }
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "browseLdap" )
    private ProcessStatus restBrowseLdap( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final Map<String, String> inputMap = pwmRequest.readBodyAsJsonStringMap( PwmHttpRequestWrapper.Flag.BypassValidation );
        final String profile = inputMap.get( "profile" );
        final String dn = inputMap.getOrDefault( "dn", "" );

        final LdapBrowser ldapBrowser = new LdapBrowser(
                pwmRequest.getPwmApplication().getLdapConnectionService().getChaiProviderFactory(),
                configManagerBean.getStoredConfiguration()
        );

        LdapBrowser.LdapBrowseResult result;
        try
        {
            result = ldapBrowser.doBrowse( profile, dn );
        }
        catch ( final PwmUnrecoverableException e )
        {
            // Probably was given a bad dn, better just browse without a DN than error out completely
            result = ldapBrowser.doBrowse( profile, "" );
        }

        ldapBrowser.close();

        {
            final LdapBrowser.LdapBrowseResult finalResult = result;
            LOGGER.trace( pwmRequest, () -> "performed ldapBrowse operation in "
                    + TimeDuration.fromCurrent( startTime ).asCompactString()
                    + ", result=" + JsonUtil.serialize( finalResult ) );
        }

        pwmRequest.outputJsonResult( RestResultBean.withData( result ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "copyProfile" )
    private ProcessStatus restCopyProfile( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( configManagerBean.getStoredConfiguration() );
        final Map<String, String> inputMap = pwmRequest.readBodyAsJsonStringMap( PwmHttpRequestWrapper.Flag.BypassValidation );

        final String settingKey = inputMap.get( "setting" );
        final PwmSetting setting = PwmSetting.forKey( settingKey )
                .orElseThrow( () -> new IllegalStateException( "invalid setting parameter value" ) );
        PwmSettingCategory category = null;
        for ( final PwmSettingCategory loopCategory : PwmSettingCategory.values() )
        {
            if ( loopCategory.hasProfiles() )
            {
                final Optional<PwmSetting> profileSetting = loopCategory.getProfileSetting();
                if ( profileSetting.isPresent() && profileSetting.get() == setting )
                {
                    category = loopCategory;
                }
            }
        }

        final String sourceID = inputMap.get( "sourceID" );
        final String destinationID = inputMap.get( "destinationID" );

        if ( category == null )
        {
            throw new IllegalStateException();
        }

        try
        {
            modifier.copyProfileID( category, sourceID, destinationID, pwmRequest.getUserInfoIfLoggedIn() );
            pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
        }
        catch ( final PwmUnrecoverableException e )
        {
            pwmRequest.outputJsonResult( RestResultBean.fromError( e.getErrorInformation(), pwmRequest ) );
        }

        configManagerBean.setStoredConfiguration( modifier.newStoredConfiguration() );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "randomPassword" )
    private ProcessStatus restRandomPassword( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final RestRandomPasswordServer.JsonInput jsonInput = JsonUtil.deserialize( pwmRequest.readRequestBodyAsString(), RestRandomPasswordServer.JsonInput.class );
        final RandomPasswordGenerator.RandomGeneratorConfig randomConfig = RestRandomPasswordServer.jsonInputToRandomConfig( jsonInput, PwmPasswordPolicy.defaultPolicy() );
        final PasswordData randomPassword = RandomPasswordGenerator.createRandomPassword( pwmRequest.getLabel(), randomConfig, pwmRequest.getPwmApplication() );
        final RestRandomPasswordServer.JsonOutput outputMap = new RestRandomPasswordServer.JsonOutput();
        outputMap.setPassword( randomPassword.getStringValue() );

        pwmRequest.outputJsonResult( RestResultBean.withData( outputMap ) );

        return ProcessStatus.Halt;
    }
}
