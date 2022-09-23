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

package password.pwm.http.servlet.configeditor;

import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.ProfileID;
import password.pwm.bean.SmsItemBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.AppConfig;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingTemplate;
import password.pwm.config.profile.EmailServerProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.stored.ConfigSearchMachine;
import password.pwm.config.stored.ConfigurationCleaner;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.StoredConfigKey;
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
import password.pwm.health.LDAPHealthChecker;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.http.servlet.configeditor.data.NavTreeDataMaker;
import password.pwm.http.servlet.configeditor.data.NavTreeItem;
import password.pwm.http.servlet.configeditor.data.NavTreeSettings;
import password.pwm.http.servlet.configeditor.data.SettingData;
import password.pwm.http.servlet.configeditor.data.SettingDataMaker;
import password.pwm.http.servlet.configmanager.ConfigManagerServlet;
import password.pwm.i18n.Config;
import password.pwm.i18n.Message;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.ldap.LdapBrowser;
import password.pwm.svc.email.EmailServer;
import password.pwm.svc.email.EmailServerUtil;
import password.pwm.svc.email.EmailService;
import password.pwm.svc.httpclient.PwmHttpClientResponse;
import password.pwm.svc.sms.SmsQueueService;
import password.pwm.util.PasswordData;
import password.pwm.util.SampleDataGenerator;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.password.RandomGeneratorConfig;
import password.pwm.util.password.RandomPasswordGenerator;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.RestRandomPasswordServer;
import password.pwm.ws.server.rest.bean.PublicHealthData;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

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

    public static final String REQ_PARAM_ALIAS = "alias";
    public static final String REQ_PARAM_PASSWORD = "password";
    public static final String REQ_PARAM_FORMAT = "format";
    public static final String REQ_PARAM_LOCALE_BUNDLE = "localeBundle";
    public static final String REQ_PARAM_KEY = "key";
    public static final String REQ_PARAM_PROFILE = "profile";
    public static final String REQ_PARAM_TEMPLATE = "template";

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
        readWarnings( HttpMethod.POST ),
        search( HttpMethod.POST ),
        cancelEditing( HttpMethod.POST ),
        uploadFile( HttpMethod.POST ),
        setOption( HttpMethod.POST ),
        menuTreeData( HttpMethod.POST ),
        settingData( HttpMethod.POST ),
        testMacro( HttpMethod.POST ),
        browseLdap( HttpMethod.POST ),
        copyProfile( HttpMethod.POST ),
        copyDomain( HttpMethod.POST ),
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

        final DomainStateReader domainStateReader = DomainStateReader.forRequest( pwmRequest );
        final DomainManageMode mode = domainStateReader.getMode();

        if ( !domainStateReader.isCorrectlyIndicated() )
        {
            if ( mode == DomainManageMode.single )
            {
                pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.ConfigEditor );
            }
            else
            {
                pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.ConfigEditor.servletUrl()
                        + "/" + DomainID.systemId().stringValue() );
            }
            return ProcessStatus.Halt;
        }

        return ProcessStatus.Continue;
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        final DomainStateReader domainStateReader = DomainStateReader.forRequest( pwmRequest );
        final DomainManageMode mode = domainStateReader.getMode();

        {
            final String value;
            switch ( mode )
            {
                case system:
                    value = DomainID.systemId().stringValue();
                    break;
                case domain:
                    value = domainStateReader.getDomainID( PwmSetting.LDAP_PROXY_USER_DN ).stringValue();
                    break;
                default:
                    value = "";
                    break;
            }
            pwmRequest.setAttribute( PwmRequestAttribute.DomainId, value );
        }

        pwmRequest.forwardToJsp( JspUrl.CONFIG_MANAGER_EDITOR );
    }

    static ConfigManagerBean getBean( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return pwmRequest.getPwmDomain().getSessionStateService().getBean( pwmRequest, ConfigManagerBean.class );
    }

    @ActionHandler( action = "executeSettingFunction" )
    public ProcessStatus restExecuteSettingFunction(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final Map<String, String> requestMap = pwmRequest.readBodyAsJsonStringMap();
        final PwmSetting pwmSetting = PwmSetting.forKey( requestMap.get( "setting" ) )
                .orElseThrow( () -> new IllegalStateException( "invalid setting parameter value" ) );

        final String functionName = requestMap.get( "function" );
        final ProfileID profileID = pwmSetting.getCategory().hasProfiles() ? ProfileID.create( pwmRequest.readParameterAsString( REQ_PARAM_PROFILE ) ) : null;
        final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainID( pwmSetting );
        final String extraData = requestMap.get( "extraData" );

        final RestResultBean<?> restResultBean = ConfigEditorServletUtils.executeSettingFunction(
                pwmRequest, configManagerBean, pwmSetting, functionName, profileID, domainID, extraData );
        pwmRequest.outputJsonResult( restResultBean );

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "readSetting" )
    public ProcessStatus restReadSetting(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final StoredConfiguration storedConfig = configManagerBean.getStoredConfiguration();
        final StoredConfigKey storedConfigKey = ConfigEditorServletUtils.readConfigKeyFromRequest( pwmRequest );

        final ReadSettingResponse readSettingResponse;
        if ( storedConfigKey.getRecordType() == StoredConfigKey.RecordType.LOCALE_BUNDLE )
        {
            readSettingResponse = ConfigEditorServletUtils.handleLocaleBundleReadSetting( pwmRequest, storedConfig, storedConfigKey );
        }
        else
        {
            readSettingResponse = ConfigEditorServletUtils.handleReadSetting( pwmRequest, storedConfig, storedConfigKey );
        }

        pwmRequest.outputJsonResult( RestResultBean.withData( readSettingResponse, ReadSettingResponse.class ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "writeSetting" )
    public ProcessStatus restWriteSetting(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( configManagerBean.getStoredConfiguration() );
        final UserIdentity loggedInUser = pwmRequest.getUserInfoIfLoggedIn();

        final ReadSettingResponse readSettingResponse;

        final StoredConfigKey key = ConfigEditorServletUtils.readConfigKeyFromRequest( pwmRequest );

        if ( key.getRecordType() == StoredConfigKey.RecordType.LOCALE_BUNDLE )
        {
            final Map<String, String> valueMap = pwmRequest.readBodyAsJsonStringMap();
            final Map<String, String> outputMap = new LinkedHashMap<>( valueMap );

            final PwmLocaleBundle pwmLocaleBundle = key.toLocaleBundle();
            final String keyName = key.getLocaleKey();
            modifier.writeLocaleBundleMap( key.getDomainID(), pwmLocaleBundle, keyName, outputMap );
            readSettingResponse = ConfigEditorServletUtils.handleLocaleBundleReadSetting( pwmRequest, modifier.newStoredConfiguration(), key );
        }
        else
        {
            try
            {
                final String bodyString = pwmRequest.readRequestBodyAsString();
                final StoredValue storedValue = ValueFactory.fromJson( key.toPwmSetting(), bodyString );
                modifier.writeSetting( key, storedValue, loggedInUser );
            }
            catch ( final PwmOperationalException e )
            {
                throw new PwmUnrecoverableException( e.getErrorInformation() );
            }
            readSettingResponse = ConfigEditorServletUtils.handleReadSetting( pwmRequest, modifier.newStoredConfiguration(), key );
        }

        ConfigurationCleaner.postProcessStoredConfig( modifier );
        configManagerBean.setStoredConfiguration( modifier.newStoredConfiguration() );
        pwmRequest.outputJsonResult( RestResultBean.withData( readSettingResponse, ReadSettingResponse.class ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "resetSetting" )
    public ProcessStatus restResetSetting(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( configManagerBean.getStoredConfiguration() );
        final UserIdentity loggedInUser = pwmRequest.getUserInfoIfLoggedIn();
        final StoredConfigKey key = ConfigEditorServletUtils.readConfigKeyFromRequest( pwmRequest );

        if ( key.getRecordType() == StoredConfigKey.RecordType.LOCALE_BUNDLE )
        {
            final PwmLocaleBundle pwmLocaleBundle = key.toLocaleBundle();
            final String keyName = key.getLocaleKey();
            final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainIDForLocaleBundle();
            modifier.resetLocaleBundleMap( pwmLocaleBundle, keyName, domainID );
        }
        else
        {
            modifier.resetSetting( key, loggedInUser );
        }

        ConfigurationCleaner.postProcessStoredConfig( modifier );
        configManagerBean.setStoredConfiguration( modifier.newStoredConfiguration() );
        pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "setConfigurationPassword" )
    public ProcessStatus restSetConfigurationPassword(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
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
    public ProcessStatus restFinishEditing( final PwmRequest pwmRequest )
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
    public ProcessStatus restCancelEditing(
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
    public ProcessStatus setOptions(
            final PwmRequest pwmRequest

    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( configManagerBean.getStoredConfiguration() );
        {

            final String requestedTemplate = pwmRequest.readParameterAsString( REQ_PARAM_TEMPLATE );
            if ( requestedTemplate != null && requestedTemplate.length() > 0 )
            {
                try
                {
                    final PwmSettingTemplate template = PwmSettingTemplate.valueOf( requestedTemplate );
                    modifier.writeConfigProperty( ConfigurationProperty.LDAP_TEMPLATE, template.toString() );
                    LOGGER.trace( pwmRequest, () -> "setting template to: " + requestedTemplate );
                }
                catch ( final IllegalArgumentException e )
                {
                    modifier.writeConfigProperty( ConfigurationProperty.LDAP_TEMPLATE, PwmSettingTemplate.DEFAULT.toString() );
                    LOGGER.error( pwmRequest, () -> "unknown template set request: " + requestedTemplate );
                }
            }
        }

        configManagerBean.setStoredConfiguration( modifier.newStoredConfiguration() );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "readChangeLog" )
    public ProcessStatus restReadChangeLog(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );

        final Map<String, String> returnObj = ConfigEditorServletUtils.outputChangeLogData( pwmRequest, configManagerBean );
        final RestResultBean restResultBean = RestResultBean.withData( returnObj, Map.class );
        pwmRequest.outputJsonResult( restResultBean );

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "readWarnings" )
    public ProcessStatus restReadWarnings(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );

        final Map<DomainID, List<String>> healthData = ConfigEditorServletUtils.configurationHealth( pwmRequest, configManagerBean );
        final RestResultBean restResultBean = RestResultBean.withData( healthData, Map.class );
        pwmRequest.outputJsonResult( restResultBean );

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "search" )
    public ProcessStatus restSearchSettings(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final Map<String, String> valueMap = pwmRequest.readBodyAsJsonStringMap();
        final Locale locale = pwmRequest.getLocale();
        final RestResultBean restResultBean;
        final String searchTerm = valueMap.get( "search" );
        final StoredConfiguration storedConfiguration = configManagerBean.getStoredConfiguration();

        if ( StringUtil.isEmpty( searchTerm ) )
        {
            pwmRequest.outputJsonResult( RestResultBean.fromError( new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, "missing search parameter" ) ) );
            return ProcessStatus.Halt;
        }

        final Set<DomainID> searchDomains = DomainStateReader.forRequest( pwmRequest ).searchIDs();

        final Set<StoredConfigKey> searchResults = new ConfigSearchMachine( storedConfiguration, locale ).search( searchTerm, searchDomains );

        final TreeMap<String, Map<String, SearchResultItem>> returnData = new TreeMap<>();

        searchResults
                .stream()
                .filter( key -> key.getRecordType() == StoredConfigKey.RecordType.SETTING )
                .forEach( recordID ->
                {
                    final SearchResultItem item = SearchResultItem.fromKey( recordID, storedConfiguration, locale );
                    final String returnCategory = item.getNavigation();

                    returnData.computeIfAbsent( returnCategory, k -> new TreeMap<>() )
                            .put( recordID.getRecordID(), item );
                } );


        restResultBean = RestResultBean.withData( returnData, Map.class );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "ldapHealthCheck" )
    public ProcessStatus restLdapHealthCheck(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        LOGGER.debug( pwmRequest, () -> "beginning restLdapHealthCheck" );

        final Instant startTime = Instant.now();
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final ProfileID profileID = ProfileID.create( pwmRequest.readParameterAsString( REQ_PARAM_PROFILE ) );
        final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainID( PwmSetting.LDAP_SERVER_URLS );
        final DomainConfig config = AppConfig.forStoredConfig( configManagerBean.getStoredConfiguration() ).getDomainConfigs().get( domainID );

        final PublicHealthData healthData = ConfigEditorServletUtils.timeoutExecutor( pwmRequest, () ->
                LDAPHealthChecker.healthForNewConfiguration(
                        pwmRequest.getLabel(),
                        pwmRequest.getPwmDomain(),
                        config,
                        pwmRequest.getLocale(),
                        profileID,
                        true,
                        true ) );

        final RestResultBean<PublicHealthData> restResultBean = RestResultBean.withData( healthData, PublicHealthData.class );

        pwmRequest.outputJsonResult( restResultBean );
        LOGGER.debug( pwmRequest, () -> "completed restLdapHealthCheck in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "databaseHealthCheck" )
    public ProcessStatus restDatabaseHealthCheck(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        LOGGER.debug( pwmRequest, () -> "beginning restDatabaseHealthCheck" );
        final AppConfig config = AppConfig.forStoredConfig( configManagerBean.getStoredConfiguration() );
        final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainID( PwmSetting.LDAP_SERVER_URLS );
        final List<HealthRecord> healthRecords = DatabaseStatusChecker.checkNewDatabaseStatus( pwmRequest.getPwmApplication(), config );
        final PublicHealthData healthData = HealthRecord.asHealthDataBean( config.getDomainConfigs().get( domainID ), pwmRequest.getLocale(), healthRecords );
        final RestResultBean restResultBean = RestResultBean.withData( healthData, PublicHealthData.class );
        pwmRequest.outputJsonResult( restResultBean );
        LOGGER.debug( pwmRequest, () -> "completed restDatabaseHealthCheck in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "smsHealthCheck" )
    public ProcessStatus restSmsHealthCheck(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        LOGGER.debug( pwmRequest, () -> "beginning restSmsHealthCheck" );

        final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainID( PwmSetting.LDAP_SERVER_URLS );
        final DomainConfig config = AppConfig.forStoredConfig( configManagerBean.getStoredConfiguration() ).getDomainConfigs().get( domainID );
        final StringBuilder output = new StringBuilder();
        output.append( "beginning SMS send process.\n" );

        if ( !config.getAppConfig().isSmsConfigured() )
        {
            output.append( "SMS not configured." );
        }
        else
        {
            final Map<String, String> testParams = pwmRequest.readBodyAsJsonStringMap();
            final SmsItemBean testSmsItem = new SmsItemBean( testParams.get( "to" ), testParams.get( "message" ), pwmRequest.getLabel() );
            try
            {
                final PwmHttpClientResponse responseBody = SmsQueueService.sendDirectMessage(
                        pwmRequest.getPwmDomain(),
                        config,
                        pwmRequest.getLabel(),
                        testSmsItem
                );
                output.append( "message sent.\n" );
                output.append( "response status: " ).append( responseBody.getStatusLine() ).append( "\n" );
                if ( responseBody.getHeaders() != null )
                {
                    responseBody.getHeaders().forEach( ( key, value ) ->
                            output.append( "response header: " ).append( key ).append( ": " ).append( value ).append( "\n" ) );
                }
                output.append( "response body: \n" ).append( StringUtil.escapeHtml( responseBody.getBody() ) );
            }
            catch ( final PwmException e )
            {
                output.append( "unable to send message: " ).append( StringUtil.escapeHtml( e.getMessage() ) );
            }
        }

        final RestResultBean<String> restResultBean = RestResultBean.withData( output.toString(), String.class );
        pwmRequest.outputJsonResult( restResultBean );
        LOGGER.debug( pwmRequest, () -> "completed restSmsHealthCheck in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "emailHealthCheck" )
    public ProcessStatus restEmailHealthCheck(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final ProfileID profileID = ProfileID.create( pwmRequest.readParameterAsString( REQ_PARAM_PROFILE ) );

        LOGGER.debug( pwmRequest, () -> "beginning restEmailHealthCheck" );

        final Map<String, String> params = pwmRequest.readBodyAsJsonStringMap();
        final EmailItemBean testEmailItem = new EmailItemBean(
                params.get( "to" ),
                params.get( "from" ),
                params.get( "subject" ),
                params.get( "body" ),
                null );

        final List<HealthRecord> returnRecords = new ArrayList<>();

        final StringBuilder output = new StringBuilder();
        output.append( "Beginning EMail send process.\n" );

        final AppConfig testDomainConfig = AppConfig.forStoredConfig( configManagerBean.getStoredConfiguration() );

        final EmailServerProfile emailServerProfile = testDomainConfig.getEmailServerProfiles().get( profileID );
        if ( emailServerProfile != null )
        {
            final Optional<EmailServer> emailServer = EmailServerUtil.makeEmailServer( testDomainConfig, emailServerProfile, null );
            if ( emailServer.isPresent() )
            {
                final MacroRequest macroRequest = SampleDataGenerator.sampleMacroRequest( pwmRequest.getPwmDomain() );

                try
                {
                    ConfigEditorServletUtils.timeoutExecutor( pwmRequest,
                            () ->
                            {
                                EmailService.sendEmailSynchronous( emailServer.get(), testDomainConfig, testEmailItem, macroRequest, pwmRequest.getLabel() );
                                return Boolean.FALSE;
                            } );

                    output.append( "Test message delivered to server.\n" );
                }
                catch ( final Throwable e )
                {
                    output.append( "error: " ).append( StringUtil.escapeHtml( JavaHelper.readHostileExceptionMessage( e ) ) ).append( "\n" );
                }
            }
        }
        else
        {
            output.append( "EMail service is not configured.\n" );
        }

        final RestResultBean<String> restResultBean = RestResultBean.withData( output.toString(), String.class );
        pwmRequest.outputJsonResult( restResultBean );
        LOGGER.debug( pwmRequest, () -> "completed restEmailHealthCheck in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "uploadFile" )
    public ProcessStatus doUploadFile(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );

        final String settingKey = pwmRequest.readParameterAsString( REQ_PARAM_KEY );
        final PwmSetting pwmSetting = PwmSetting.forKey( settingKey )
                .orElseThrow( () -> new IllegalStateException( "invalid setting parameter value" ) );
        final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainID( pwmSetting );
        final int maxFileSize = Integer.parseInt( pwmRequest.getDomainConfig().readAppProperty( AppProperty.CONFIG_MAX_FILEVALUE_SIZE ) );

        if ( pwmSetting == PwmSetting.HTTPS_CERT )
        {
            ConfigEditorServletUtils.processHttpsCertificateUpload( pwmRequest, configManagerBean );
            return ProcessStatus.Halt;
        }

        final Optional<FileValue> fileValue = ConfigEditorServletUtils.readFileUploadToSettingValue( pwmRequest, maxFileSize );
        if ( fileValue.isPresent() )
        {
            final UserIdentity userIdentity = pwmRequest.isAuthenticated()
                    ? pwmRequest.getPwmSession().getUserInfo().getUserIdentity()
                    : null;

            final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( configManagerBean.getStoredConfiguration() );
            final StoredConfigKey key = StoredConfigKey.forSetting( pwmSetting, null, domainID );
            modifier.writeSetting( key, fileValue.get(), userIdentity );
            configManagerBean.setStoredConfiguration( modifier.newStoredConfiguration() );
            pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
            LOGGER.trace( pwmRequest, () -> "file upload completed for setting " + key + ", file: "
                    + fileValue.get().toDebugJsonObject( pwmRequest.getLocale() ) );
        }
        else
        {
            LOGGER.trace( pwmRequest, () -> "file upload requested but no file present in request" );
        }

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "menuTreeData" )
    public ProcessStatus restMenuTreeData(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );

        final NavTreeSettings navTreeSettings = NavTreeSettings.readFromRequest( pwmRequest );

        final StoredConfiguration storedConfiguration = configManagerBean.getStoredConfiguration();
        final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainIDForLocaleBundle();

        final List<NavTreeItem> navigationData = NavTreeDataMaker.makeNavTreeItems(
                domainID,
                storedConfiguration,
                navTreeSettings );

        LOGGER.trace( pwmRequest, () -> "completed navigation tree data request in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        pwmRequest.outputJsonResult( RestResultBean.withData( navigationData, List.class ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "settingData" )
    public ProcessStatus restConfigSettingData( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainIDForLocaleBundle();
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );

        final NavTreeSettings navTreeSettings = NavTreeSettings.readFromRequest( pwmRequest );

        final SettingData settingData = SettingDataMaker.generateSettingData(
                domainID,
                configManagerBean.getStoredConfiguration(),
                pwmRequest.getLabel(),
                pwmRequest.getLocale(),
                navTreeSettings
        );

        final RestResultBean restResultBean = RestResultBean.withData( settingData, SettingData.class );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "testMacro" )
    public ProcessStatus restTestMacro( final PwmRequest pwmRequest ) throws IOException, ServletException, PwmUnrecoverableException
    {
        try
        {
            final Map<String, String> inputMap = pwmRequest.readBodyAsJsonStringMap( PwmHttpRequestWrapper.Flag.BypassValidation );
            if ( inputMap == null || !inputMap.containsKey( "input" ) )
            {
                pwmRequest.outputJsonResult( RestResultBean.withData( "missing input", String.class ) );
                return ProcessStatus.Halt;
            }

            final MacroRequest macroRequest = SampleDataGenerator.sampleMacroRequest( pwmRequest.getPwmDomain() );
            final String input = inputMap.get( "input" );
            final String output = macroRequest.expandMacros( input );
            pwmRequest.outputJsonResult( RestResultBean.withData( output, String.class ) );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.error( pwmRequest, e.getErrorInformation() );
            pwmRequest.respondWithError( e.getErrorInformation() );
        }
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "browseLdap" )
    public ProcessStatus restBrowseLdap( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final Map<String, String> inputMap = pwmRequest.readBodyAsJsonStringMap( PwmHttpRequestWrapper.Flag.BypassValidation );

        final StoredConfiguration storedConfiguration = configManagerBean.getStoredConfiguration();
        final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainIDForDomainSetting(  );

        final ProfileID profile;
        {
            final AppConfig appConfig = AppConfig.forStoredConfig( storedConfiguration );
            final DomainConfig domainConfig = appConfig.getDomainConfigs().getOrDefault( domainID, AppConfig.defaultConfig().getAdminDomain() );
            final Optional<ProfileID> selectedProfile = domainConfig.ldapProfileForStringId( inputMap.get( LdapBrowser.PARAM_PROFILE ) );
            profile = selectedProfile.orElse( domainConfig.getLdapProfiles().keySet().iterator().next() );
        }
        final String dn = inputMap.getOrDefault( LdapBrowser.PARAM_DN, "" );

        final LdapBrowser ldapBrowser = new LdapBrowser(
                pwmRequest.getLabel(),
                pwmRequest.getPwmDomain().getLdapService().getChaiProviderFactory(),
                storedConfiguration
        );

        LdapBrowser.LdapBrowseResult result;
        try
        {
            result = ldapBrowser.doBrowse( domainID, profile, dn );
        }
        catch ( final PwmUnrecoverableException e )
        {
            // Probably was given a bad dn, better just browse without a DN than error out completely
            result = ldapBrowser.doBrowse( domainID, profile, "" );
        }

        ldapBrowser.close();

        {
            final LdapBrowser.LdapBrowseResult finalResult = result;
            LOGGER.trace( pwmRequest, () -> "performed ldapBrowse operation in "
                    + TimeDuration.fromCurrent( startTime ).asCompactString()
                    + ", result=" + JsonFactory.get().serialize( finalResult ) );
        }

        pwmRequest.outputJsonResult( RestResultBean.withData( result, LdapBrowser.LdapBrowseResult.class ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "copyProfile" )
    public ProcessStatus restCopyProfile( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final Map<String, String> inputMap = pwmRequest.readBodyAsJsonStringMap( PwmHttpRequestWrapper.Flag.BypassValidation );

        final String settingKey = inputMap.get( "setting" );
        final PwmSetting setting = PwmSetting.forKey( settingKey )
                .orElseThrow( () -> new IllegalStateException( "invalid setting parameter value" ) );
        final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainID( setting );

        final PwmSettingCategory category = PwmSettingCategory.forProfileSetting( setting )
                .orElseThrow( () -> new IllegalStateException( "specified key does not associated with a profile-enabled category" ) );

        final ProfileID sourceID = ProfileID.create( inputMap.get( "sourceID" ) );
        final ProfileID destinationID = ProfileID.create( inputMap.get( "destinationID" ) );

        try
        {
            final StoredConfiguration newStoredConfig = StoredConfigurationUtil.copyProfileID(
                    configManagerBean.getStoredConfiguration(),
                    domainID,
                    category,
                    sourceID,
                    destinationID,
                    pwmRequest.getUserInfoIfLoggedIn() );
            pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
            configManagerBean.setStoredConfiguration( newStoredConfig );
        }
        catch ( final PwmUnrecoverableException e )
        {
            pwmRequest.outputJsonResult( RestResultBean.fromError( e.getErrorInformation(), pwmRequest ) );
        }

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "copyDomain" )
    public ProcessStatus restCopyDomain( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final Map<String, String> inputMap = pwmRequest.readBodyAsJsonStringMap( PwmHttpRequestWrapper.Flag.BypassValidation );
        final String sourceID = inputMap.get( "sourceID" );
        final String destinationID = inputMap.get( "destinationID" );

        try
        {
            final StoredConfiguration newStoredConfig = StoredConfigurationUtil.copyDomainID(
                    configManagerBean.getStoredConfiguration(),
                    sourceID,
                    destinationID,
                    pwmRequest.getUserInfoIfLoggedIn() );
            pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
            configManagerBean.setStoredConfiguration( newStoredConfig );
        }
        catch ( final PwmUnrecoverableException e )
        {
            pwmRequest.outputJsonResult( RestResultBean.fromError( e.getErrorInformation(), pwmRequest ) );
        }

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "randomPassword" )
    public ProcessStatus restRandomPassword( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final RestRandomPasswordServer.JsonInput jsonInput = pwmRequest.readBodyAsJsonObject( RestRandomPasswordServer.JsonInput.class );
        final RandomGeneratorConfig randomConfig = RestRandomPasswordServer.jsonInputToRandomConfig( jsonInput, pwmRequest.getPwmDomain(), PwmPasswordPolicy.defaultPolicy() );
        final PasswordData randomPassword = RandomPasswordGenerator.createRandomPassword( pwmRequest.getLabel(), randomConfig, pwmRequest.getPwmDomain() );
        final RestRandomPasswordServer.JsonOutput outputMap = new RestRandomPasswordServer.JsonOutput();
        outputMap.setPassword( randomPassword.getStringValue() );

        pwmRequest.outputJsonResult( RestResultBean.withData( outputMap, RestRandomPasswordServer.JsonOutput.class ) );

        return ProcessStatus.Halt;
    }
}
