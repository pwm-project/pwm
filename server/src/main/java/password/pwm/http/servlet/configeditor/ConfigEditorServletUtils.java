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
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.bean.ProfileID;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.config.stored.ValueMetaData;
import password.pwm.config.value.FileValue;
import password.pwm.config.value.PrivateKeyValue;
import password.pwm.config.value.X509CertificateValue;
import password.pwm.data.FileUploadItem;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.ConfigurationChecker;
import password.pwm.health.HealthRecord;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestUtil;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.http.servlet.configeditor.function.SettingUIFunction;
import password.pwm.i18n.Message;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.PasswordData;
import password.pwm.util.PwmScheduler;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.HttpsServerCertificateManager;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.concurrent.Callable;

public class ConfigEditorServletUtils
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigEditorServletUtils.class );

    public static Optional<FileValue> readFileUploadToSettingValue(
            final PwmRequest pwmRequest,
            final int maxFileSize
    )
            throws PwmUnrecoverableException, IOException
    {
        final Map<String, FileUploadItem> fileUploads;
        try
        {
            fileUploads = PwmRequestUtil.readFileUploads( pwmRequest, maxFileSize, 1 );
        }
        catch ( final PwmException e )
        {
            pwmRequest.outputJsonResult( RestResultBean.fromError( e.getErrorInformation(), pwmRequest ) );
            LOGGER.error( pwmRequest, () -> "error during file upload: " + e.getErrorInformation().toDebugStr() );
            return Optional.empty();
        }
        catch ( final Throwable e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, "error during file upload: " + e.getMessage() );
            pwmRequest.outputJsonResult( RestResultBean.fromError( errorInformation, pwmRequest ) );
            LOGGER.error( pwmRequest, errorInformation );
            return Optional.empty();
        }

        {
            final FileUploadItem uploadItem = fileUploads.get( PwmConstants.PARAM_FILE_UPLOAD );
            if ( uploadItem != null )
            {
                return Optional.of( FileValue.newFileValue( uploadItem.getName(), uploadItem.getType(), uploadItem.getContent() ) );
            }
        }

        final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, "no file found in upload" );
        pwmRequest.outputJsonResult( RestResultBean.fromError( errorInformation, pwmRequest ) );
        LOGGER.error( pwmRequest, () -> "error during file upload: " + errorInformation.toDebugStr() );
        return Optional.empty();
    }

    static Map<String, String> outputChangeLogData(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
    {
        final Locale locale = pwmRequest.getLocale();

        final Set<StoredConfigKey> changedKeys = StoredConfigurationUtil.changedValues(
                pwmRequest.getPwmDomain().getConfig().getStoredConfiguration(),
                configManagerBean.getStoredConfiguration() );

        return StoredConfigurationUtil.makeDebugMap(
                configManagerBean.getStoredConfiguration(),
                changedKeys,
                locale );
    }

    static Map<DomainID, List<String>> configurationHealth(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
    {
        final Instant startTime = Instant.now();
        try
        {
            final Locale locale = pwmRequest.getLocale();
            final ConfigurationChecker configurationChecker = new ConfigurationChecker();

            final PwmApplication tempApplication = PwmApplication.createPwmApplication( pwmRequest.getPwmApplication()
                    .getPwmEnvironment()
                    .makeRuntimeInstance( AppConfig.forStoredConfig( configManagerBean.getStoredConfiguration() ) ) );

            final List<HealthRecord> healthRecords = configurationChecker.doHealthCheck( tempApplication, pwmRequest.getLabel() );
            final Map<DomainID, List<String>> returnData = new TreeMap<>();

            healthRecords.forEach( record ->
                    returnData.computeIfAbsent(
                                    record.getDomainID(), k -> new ArrayList<>() )
                            .add( record.getDetail( locale, pwmRequest.getAppConfig() ) ) );

            LOGGER.debug( pwmRequest.getLabel(), () -> "config health check done in ", TimeDuration.fromCurrent( startTime ) );

            return Collections.unmodifiableMap( returnData );
        }
        catch ( final Exception e )
        {
            LOGGER.error( pwmRequest, () -> "error generating health records: " + e.getMessage() );
        }

        return Collections.emptyMap();
    }

    static ReadSettingResponse handleLocaleBundleReadSetting(
            final PwmRequest pwmRequest,
            final StoredConfiguration storedConfig,
            final StoredConfigKey key

    )
            throws PwmUnrecoverableException
    {
        final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainIDForLocaleBundle();
        final ReadSettingResponse.ReadSettingResponseBuilder builder = ReadSettingResponse.builder();
        final PwmLocaleBundle pwmLocaleBundle = key.toLocaleBundle();
        final String keyName = key.getProfileID().toString();
        final Map<String, String> bundleMap = storedConfig.readLocaleBundleMap( pwmLocaleBundle, keyName, domainID );
        if ( bundleMap == null || bundleMap.isEmpty() )
        {
            final Map<String, String> defaultValueMap = new LinkedHashMap<>();
            final String defaultLocaleValue = ResourceBundle.getBundle( pwmLocaleBundle.getTheClass().getName(), PwmConstants.DEFAULT_LOCALE ).getString( keyName );
            for ( final Locale locale : pwmRequest.getAppConfig().getKnownLocales() )
            {
                final ResourceBundle localeBundle = ResourceBundle.getBundle( pwmLocaleBundle.getTheClass().getName(), locale );
                if ( locale.toString().equalsIgnoreCase( PwmConstants.DEFAULT_LOCALE.toString() ) )
                {
                    defaultValueMap.put( "", defaultLocaleValue );
                }
                else
                {
                    final String valueStr = localeBundle.getString( keyName );
                    if ( !defaultLocaleValue.equals( valueStr ) )
                    {
                        final String localeStr = locale.toString();
                        defaultValueMap.put( localeStr, localeBundle.getString( keyName ) );
                    }
                }
            }
            builder.value( defaultValueMap );
            builder.isDefault( true );
        }
        else
        {
            builder.value( bundleMap );
            builder.isDefault( false );
        }
        builder.key( keyName );
        return builder.build();
    }

    static ReadSettingResponse handleReadSetting(
            final PwmRequest pwmRequest,
            final StoredConfiguration storedConfig,
            final StoredConfigKey key
    )
            throws PwmUnrecoverableException
    {
        final ReadSettingResponse.ReadSettingResponseBuilder builder = ReadSettingResponse.builder();

        final PwmSetting pwmSetting = key.toPwmSetting();
        final boolean isDefault = StoredConfigurationUtil.isDefaultValue( storedConfig, key );

        final Object returnValue;
        switch ( pwmSetting.getSyntax() )
        {
            case PASSWORD:
                returnValue = Collections.singletonMap( "isDefault", isDefault );
                break;

            case X509CERT:
                returnValue = ( ( X509CertificateValue ) StoredConfigurationUtil.getValueOrDefault( storedConfig, key ) ).toInfoMap( true );
                break;

            case PRIVATE_KEY:
                returnValue = ( ( PrivateKeyValue ) StoredConfigurationUtil.getValueOrDefault( storedConfig, key ) ).toInfoMap( true );
                break;

            case FILE:
                returnValue = ( ( FileValue ) StoredConfigurationUtil.getValueOrDefault( storedConfig, key ) ).toInfoMap();
                break;

            default:
                returnValue = StoredConfigurationUtil.getValueOrDefault( storedConfig, key ).toNativeObject();

        }
        builder.value( returnValue );

        builder.isDefault( isDefault );
        if ( pwmSetting.getSyntax() == PwmSettingSyntax.SELECT )
        {
            builder.options( pwmSetting.getOptions() );
        }
        {
            final Optional<ValueMetaData> settingMetaData = storedConfig.readSettingMetadata( key );
            if ( settingMetaData.isPresent() )
            {
                builder.modifyTime( settingMetaData.map( ValueMetaData::getModifyDate ).orElse( null ) );
                builder.modifyUser( settingMetaData.map( ValueMetaData::getUserIdentity ).orElse( null ) );
            }
        }
        builder.key( key.toPwmSetting().getKey() );
        builder.category( pwmSetting.getCategory().toString() );
        builder.syntax( pwmSetting.getSyntax().toString() );
        return builder.build();
    }

    static void processHttpsCertificateUpload(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        try
        {
            final PasswordData passwordData = pwmRequest.readParameterAsPassword( ConfigEditorServlet.REQ_PARAM_PASSWORD )
                    .orElseThrow( () -> new NoSuchElementException( "missing 'password' field" ) );

            final String alias = pwmRequest.readParameterAsString( ConfigEditorServlet.REQ_PARAM_ALIAS );
            final HttpsServerCertificateManager.KeyStoreFormat keyStoreFormat;
            try
            {
                keyStoreFormat = HttpsServerCertificateManager.KeyStoreFormat.valueOf( pwmRequest.readParameterAsString( ConfigEditorServlet.REQ_PARAM_FORMAT ) );
            }
            catch ( final IllegalArgumentException e )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, "unknown format type: " + e.getMessage(), new String[]
                        {
                                ConfigEditorServlet.REQ_PARAM_FORMAT,
                        }
                ) );
            }

            final int maxFileSize = Integer.parseInt( pwmRequest.getDomainConfig().readAppProperty( AppProperty.CONFIG_MAX_FILEVALUE_SIZE ) );
            final Map<String, FileUploadItem> fileUploads = PwmRequestUtil.readFileUploads( pwmRequest, maxFileSize, 1 );
            final InputStream fileIs = fileUploads.get( PwmConstants.PARAM_FILE_UPLOAD ).getContent().newByteArrayInputStream();

            final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( configManagerBean.getStoredConfiguration() );

            HttpsServerCertificateManager.importKey(
                    modifier,
                    keyStoreFormat,
                    fileIs,
                    passwordData,
                    alias
            );

            configManagerBean.setStoredConfiguration( modifier.newStoredConfiguration() );
            pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
        }
        catch ( final PwmException e )
        {
            LOGGER.error( pwmRequest, () -> "error during https certificate upload: " + e.getMessage() );
            pwmRequest.respondWithError( e.getErrorInformation(), false );
        }
    }

    static StoredConfigKey readConfigKeyFromRequest( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {

        final String keyStringParam = pwmRequest.readParameterAsString( ConfigEditorServlet.REQ_PARAM_KEY );

        if ( keyStringParam.startsWith( ConfigEditorServlet.REQ_PARAM_LOCALE_BUNDLE ) )
        {
            try
            {
                final StringTokenizer st = new StringTokenizer( keyStringParam, "-" );
                st.nextToken();
                final PwmLocaleBundle pwmLocaleBundle = PwmLocaleBundle.forKey( st.nextToken() )
                        .orElseThrow( () -> new IllegalArgumentException( "invalid StoredConfigKey locale key" ) );
                final String keyName = st.nextToken();

                final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainIDForLocaleBundle();
                return StoredConfigKey.forLocaleBundle( pwmLocaleBundle, keyName, domainID );
            }
            catch ( final NoSuchElementException e )
            {
                throw new IllegalArgumentException( "invalid StoredConfigKey locale key format" );
            }
        }

        final PwmSetting setting = PwmSetting.forKey( keyStringParam )
                .orElseThrow( () -> new IllegalStateException( "invalid StoredConfigKey setting key" ) );
        final DomainID domainID = DomainStateReader.forRequest( pwmRequest ).getDomainID( setting );
        final String profileID = setting.getCategory().hasProfiles() ? pwmRequest.readParameterAsString( ConfigEditorServlet.REQ_PARAM_PROFILE ) : null;
        return StoredConfigKey.forSetting( setting, profileID == null ? null : ProfileID.create( profileID ), domainID );
    }

    static RestResultBean<?> executeSettingFunction(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean,
            final PwmSetting pwmSetting,
            final String functionName,
            final ProfileID profileID,
            final DomainID domainID,
            final String extraData
    )
    {
        try
        {
            final StoredConfigKey key = StoredConfigKey.forSetting( pwmSetting, profileID, domainID );
            final Class<?> implementingClass = Class.forName( functionName );
            final SettingUIFunction function = ( SettingUIFunction ) implementingClass.getDeclaredConstructor().newInstance();
            final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( configManagerBean.getStoredConfiguration() );

            final Serializable result = timeoutExecutor( pwmRequest,
                    () -> function.provideFunction( pwmRequest, modifier, key, extraData ) );

            configManagerBean.setStoredConfiguration( modifier.newStoredConfiguration() );
            return RestResultBean.forSuccessMessage( result, pwmRequest, Message.Success_Unknown );
        }
        catch ( final Exception e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, "error running operation: " + e.getMessage() );
            return RestResultBean.fromError( errorInformation, pwmRequest );
        }
    }

    static <T> T timeoutExecutor( final PwmRequest pwmRequest, final Callable<T> callable )
            throws PwmUnrecoverableException
    {
        final ConfigEditorSettings configEditorSettings = ConfigEditorSettings.fromAppConfig( pwmRequest.getAppConfig() );

        try
        {
            //return PwmScheduler.timeoutExecutor( pwmRequest.getPwmApplication(), pwmRequest.getLabel(), configEditorSettings.getMaxWaitSettingsFunction(), callable );
            return PwmScheduler.timeoutExecutor( pwmRequest.getPwmApplication(), pwmRequest.getLabel(), TimeDuration.HOUR, callable );
        }
        catch ( final PwmUnrecoverableException e )
        {
            throw e;
        }
        catch ( final Throwable t )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, "error running operation: : " + t.getMessage() );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

}
