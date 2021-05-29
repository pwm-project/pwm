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

import lombok.Builder;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SmsItemBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.AppConfig;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingTemplate;
import password.pwm.config.SettingUIFunction;
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
import password.pwm.svc.sms.SmsQueueService;
import password.pwm.util.PasswordData;
import password.pwm.util.SampleDataGenerator;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.password.RandomPasswordGenerator;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.RestRandomPasswordServer;
import password.pwm.ws.server.rest.bean.PublicHealthData;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.Serializable;
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
import java.util.StringTokenizer;
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
        final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainID( pwmSetting );
        final String extraData = requestMap.get( "extraData" );

        try
        {
            final StoredConfigKey key = StoredConfigKey.forSetting( pwmSetting, profileID, domainID );
            final Class implementingClass = Class.forName( functionName );
            final SettingUIFunction function = ( SettingUIFunction ) implementingClass.getDeclaredConstructor().newInstance();
            final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( configManagerBean.getStoredConfiguration() );
            final Serializable result = function.provideFunction( pwmRequest, modifier, key, extraData );
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
        final String settingKey = pwmRequest.readParameterAsString( "key" );
        final String bodyString = pwmRequest.readRequestBodyAsString();
        final UserIdentity loggedInUser = pwmRequest.getUserInfoIfLoggedIn();

        final ReadSettingResponse readSettingResponse;

        if ( settingKey.startsWith( "localeBundle" ) )
        {
            final StringTokenizer st = new StringTokenizer( settingKey, "-" );
            st.nextToken();
            final PwmLocaleBundle pwmLocaleBundle = PwmLocaleBundle.forKey( st.nextToken() )
                    .orElseThrow( () -> new IllegalArgumentException( "unknown locale bundle name" ) );
            final String keyName = st.nextToken();
            final Map<String, String> valueMap = JsonUtil.deserializeStringMap( bodyString );
            final Map<String, String> outputMap = new LinkedHashMap<>( valueMap );

            final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainIDForLocaleBundle();
            modifier.writeLocaleBundleMap( domainID, pwmLocaleBundle, keyName, outputMap );
            readSettingResponse = ConfigEditorServletUtils.handleLocaleBundleReadSetting( pwmRequest, modifier.newStoredConfiguration(), settingKey );
        }
        else
        {
            final PwmSetting setting = PwmSetting.forKey( settingKey )
                    .orElseThrow( () -> new IllegalStateException( "invalid setting parameter value" ) );
            final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainID( setting );
            final String profileID = setting.getCategory().hasProfiles() ? pwmRequest.readParameterAsString( "profile" ) : null;
            final StoredConfigKey key = StoredConfigKey.forSetting( setting, profileID, domainID );
            try
            {
                final StoredValue storedValue = ValueFactory.fromJson( setting, bodyString );
                modifier.writeSetting( key, storedValue, loggedInUser );
            }
            catch ( final PwmOperationalException e )
            {
                throw new PwmUnrecoverableException( e.getErrorInformation() );
            }
            readSettingResponse = ConfigEditorServletUtils.handleReadSetting( pwmRequest, modifier.newStoredConfiguration(), settingKey );
        }

        ConfigurationCleaner.postProcessStoredConfig( modifier );
        configManagerBean.setStoredConfiguration( modifier.newStoredConfiguration() );
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
        final String settingKey = pwmRequest.readParameterAsString( "key" );

        if ( settingKey.startsWith( "localeBundle" ) )
        {
            final StringTokenizer st = new StringTokenizer( settingKey, "-" );
            st.nextToken();
            final PwmLocaleBundle pwmLocaleBundle = PwmLocaleBundle.forKey( st.nextToken() )
                    .orElseThrow( () -> new IllegalArgumentException( "unknown locale bundle name" ) );
            final String keyName = st.nextToken();
            final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainIDForLocaleBundle();
            modifier.resetLocaleBundleMap( pwmLocaleBundle, keyName, domainID );
        }
        else
        {
            final PwmSetting setting = PwmSetting.forKey( settingKey )
                    .orElseThrow( () -> new IllegalStateException( "invalid setting parameter value" ) );
            final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainID( setting );
            final String profileID = setting.getCategory().hasProfiles() ? pwmRequest.readParameterAsString( "profile" ) : null;
            final StoredConfigKey key = StoredConfigKey.forSetting( setting, profileID, domainID );
            modifier.resetSetting( key, loggedInUser );
        }

        ConfigurationCleaner.postProcessStoredConfig( modifier );
        configManagerBean.setStoredConfiguration( modifier.newStoredConfiguration() );
        pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "setConfigurationPassword" )
    private ProcessStatus restSetConfigurationPassword(
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
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( configManagerBean.getStoredConfiguration() );
        {
            final String updateDescriptionTextCmd = pwmRequest.readParameterAsString( "updateNotesText" );
            if ( StringUtil.nullSafeEqualsIgnoreCase( "true", updateDescriptionTextCmd ) )
            {
                try
                {
                    final String bodyString = pwmRequest.readRequestBodyAsString();
                    final String value = JsonUtil.deserialize( bodyString, String.class );
                    modifier.writeConfigProperty( ConfigurationProperty.NOTES, value );
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
                        modifier.writeConfigProperty( ConfigurationProperty.LDAP_TEMPLATE, template.toString() );
                        LOGGER.trace( () -> "setting template to: " + requestedTemplate );
                    }
                    catch ( final IllegalArgumentException e )
                    {
                        modifier.writeConfigProperty( ConfigurationProperty.LDAP_TEMPLATE, PwmSettingTemplate.DEFAULT.toString() );
                        LOGGER.error( () -> "unknown template set request: " + requestedTemplate );
                    }
                }
            }
        }

        configManagerBean.setStoredConfiguration( modifier.newStoredConfiguration() );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "readChangeLog" )
    private ProcessStatus restReadChangeLog(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );

        final Map<String, String> returnObj = ConfigEditorServletUtils.outputChangeLogData( pwmRequest, configManagerBean );
        final RestResultBean restResultBean = RestResultBean.withData( new LinkedHashMap<>( returnObj ) );
        pwmRequest.outputJsonResult( restResultBean );

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "readWarnings" )
    private ProcessStatus restReadWarnings(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );

        final Map<DomainID, List<String>> healthData = ConfigEditorServletUtils.configurationHealth( pwmRequest, configManagerBean );
        final RestResultBean restResultBean = RestResultBean.withData( new LinkedHashMap<>( healthData ) );
        pwmRequest.outputJsonResult( restResultBean );

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "search" )
    private ProcessStatus restSearchSettings(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = getBean( pwmRequest );
        final String bodyData = pwmRequest.readRequestBodyAsString();
        final Map<String, String> valueMap = JsonUtil.deserializeStringMap( bodyData );
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

                    returnData.computeIfAbsent( returnCategory, k -> new TreeMap<>() );
                    returnData.get( returnCategory ).put( recordID.getRecordID(), item );
                } );


        restResultBean = RestResultBean.withData( returnData );
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
        final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainID( PwmSetting.LDAP_SERVER_URLS );
        final DomainConfig config = new AppConfig( configManagerBean.getStoredConfiguration() ).getDomainConfigs().get( domainID );
        final PublicHealthData healthData = LDAPHealthChecker.healthForNewConfiguration(
                pwmRequest.getLabel(),
                pwmRequest.getPwmDomain(),
                config,
                pwmRequest.getLocale(),
                profileID,
                true,
                true );
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
        final AppConfig config = new AppConfig( configManagerBean.getStoredConfiguration() );
        final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainID( PwmSetting.LDAP_SERVER_URLS );
        final List<HealthRecord> healthRecords = DatabaseStatusChecker.checkNewDatabaseStatus( pwmRequest.getPwmApplication(), config );
        final PublicHealthData healthData = HealthRecord.asHealthDataBean( config.getDomainConfigs().get( domainID ), pwmRequest.getLocale(), healthRecords );
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

        final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainID( PwmSetting.LDAP_SERVER_URLS );
        final DomainConfig config = new AppConfig( configManagerBean.getStoredConfiguration() ).getDomainConfigs().get( domainID );
        final StringBuilder output = new StringBuilder();
        output.append( "beginning SMS send process:\n" );

        if ( !SmsQueueService.smsIsConfigured( config.getAppConfig() ) )
        {
            output.append( "SMS not configured." );
        }
        else
        {
            final Map<String, String> testParams = pwmRequest.readBodyAsJsonStringMap();
            final SmsItemBean testSmsItem = new SmsItemBean( testParams.get( "to" ), testParams.get( "message" ), pwmRequest.getLabel() );
            try
            {
                final String responseBody = SmsQueueService.sendDirectMessage(
                        pwmRequest.getPwmDomain(),
                        config,
                        pwmRequest.getLabel(),
                        testSmsItem
                );
                output.append( "message sent:\n" );
                output.append( "response body: \n" ).append( StringUtil.escapeHtml( responseBody ) );
            }
            catch ( final PwmException e )
            {
                output.append( "unable to send message: " ).append( StringUtil.escapeHtml( e.getMessage() ) );
            }
        }

        final RestResultBean restResultBean = RestResultBean.withData( output.toString() );
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
        final EmailItemBean testEmailItem = new EmailItemBean(
                params.get( "to" ),
                params.get( "from" ),
                params.get( "subject" ),
                params.get( "body" ),
                null );

        final StringBuilder output = new StringBuilder();
        output.append( "beginning EMail send process:\n" );

        final AppConfig testDomainConfig = new AppConfig( configManagerBean.getStoredConfiguration() );

        final EmailServerProfile emailServerProfile = testDomainConfig.getEmailServerProfiles().get( profileID );
        if ( emailServerProfile != null )
        {
            final Optional<EmailServer> emailServer = EmailServerUtil.makeEmailServer( testDomainConfig, emailServerProfile, null );
            if ( emailServer.isPresent() )
            {
                final MacroRequest macroRequest = SampleDataGenerator.sampleMacroRequest( pwmRequest.getPwmDomain() );

                try
                {
                    EmailService.sendEmailSynchronous( emailServer.get(), testDomainConfig, testEmailItem, macroRequest );
                    output.append( "message delivered" );
                }
                catch ( final PwmException e )
                {
                    output.append( "error: " ).append( StringUtil.escapeHtml( JavaHelper.readHostileExceptionMessage( e ) ) );
                }
            }
        }
        else
        {
            output.append( "smtp service is not configured." );
        }

        final RestResultBean restResultBean = RestResultBean.withData( output.toString() );
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

        final String settingKey = pwmRequest.readParameterAsString( "key" );
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
            LOGGER.trace( pwmRequest, () -> "file upload completed for setting " + key.toString() + ", file: "
                    + fileValue.get().toDebugJsonObject( pwmRequest.getLocale() ) );
        }
        else
        {
            LOGGER.trace( pwmRequest, () -> "file upload requested but no file present in request" );
        }

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

        final NavTreeSettings navTreeSettings = NavTreeSettings.readFromRequest( pwmRequest );

        final StoredConfiguration storedConfiguration = configManagerBean.getStoredConfiguration();

        final List<NavTreeItem> navigationData = NavTreeDataMaker.makeNavTreeItems(
                pwmRequest.getPwmDomain(),
                storedConfiguration,
                navTreeSettings );

        LOGGER.trace( pwmRequest, () -> "completed navigation tree data request in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        pwmRequest.outputJsonResult( RestResultBean.withData( new ArrayList<>( navigationData ) ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "settingData" )
    private ProcessStatus restConfigSettingData( final PwmRequest pwmRequest )
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

        final RestResultBean restResultBean = RestResultBean.withData( settingData );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "testMacro" )
    private ProcessStatus restTestMacro( final PwmRequest pwmRequest ) throws IOException, ServletException, PwmUnrecoverableException
    {
        try
        {
            final Map<String, String> inputMap = pwmRequest.readBodyAsJsonStringMap( PwmHttpRequestWrapper.Flag.BypassValidation );
            if ( inputMap == null || !inputMap.containsKey( "input" ) )
            {
                pwmRequest.outputJsonResult( RestResultBean.withData( "missing input" ) );
                return ProcessStatus.Halt;
            }

            final MacroRequest macroRequest = SampleDataGenerator.sampleMacroRequest( pwmRequest.getPwmDomain() );
            final String input = inputMap.get( "input" );
            final String output = macroRequest.expandMacros( input );
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
        final String profile = inputMap.get( LdapBrowser.PARAM_PROFILE );
        final String dn = inputMap.getOrDefault( LdapBrowser.PARAM_DN, "" );
        final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainIDForDomainSetting(  );

        final LdapBrowser ldapBrowser = new LdapBrowser(
                pwmRequest.getLabel(),
                pwmRequest.getPwmDomain().getLdapConnectionService().getChaiProviderFactory(),
                configManagerBean.getStoredConfiguration()
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
                    + ", result=" + JsonUtil.serialize( finalResult ) );
        }

        pwmRequest.outputJsonResult( RestResultBean.withData( result ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "copyProfile" )
    private ProcessStatus restCopyProfile( final PwmRequest pwmRequest )
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

        final String sourceID = inputMap.get( "sourceID" );
        final String destinationID = inputMap.get( "destinationID" );

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
    private ProcessStatus restCopyDomain( final PwmRequest pwmRequest )
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
    private ProcessStatus restRandomPassword( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final RestRandomPasswordServer.JsonInput jsonInput = JsonUtil.deserialize( pwmRequest.readRequestBodyAsString(), RestRandomPasswordServer.JsonInput.class );
        final RandomPasswordGenerator.RandomGeneratorConfig randomConfig = RestRandomPasswordServer.jsonInputToRandomConfig( jsonInput, PwmPasswordPolicy.defaultPolicy() );
        final PasswordData randomPassword = RandomPasswordGenerator.createRandomPassword( pwmRequest.getLabel(), randomConfig, pwmRequest.getPwmDomain() );
        final RestRandomPasswordServer.JsonOutput outputMap = new RestRandomPasswordServer.JsonOutput();
        outputMap.setPassword( randomPassword.getStringValue() );

        pwmRequest.outputJsonResult( RestResultBean.withData( outputMap ) );

        return ProcessStatus.Halt;
    }
}
