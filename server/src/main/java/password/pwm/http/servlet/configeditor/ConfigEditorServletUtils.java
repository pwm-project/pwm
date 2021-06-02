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

import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.stored.StoredConfigItemKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.config.stored.ValueMetaData;
import password.pwm.config.value.ActionValue;
import password.pwm.config.value.FileValue;
import password.pwm.config.value.PrivateKeyValue;
import password.pwm.config.value.RemoteWebServiceValue;
import password.pwm.config.value.X509CertificateValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.ConfigurationChecker;
import password.pwm.health.HealthRecord;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.i18n.Message;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.PasswordData;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.HttpsServerCertificateManager;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.bean.HealthData;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringTokenizer;

public class ConfigEditorServletUtils
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigEditorServletUtils.class );


    public static FileValue readFileUploadToSettingValue(
            final PwmRequest pwmRequest,
            final int maxFileSize
    )
            throws IOException
    {

        final Map<String, PwmRequest.FileUploadItem> fileUploads;
        try
        {
            fileUploads = pwmRequest.readFileUploads( maxFileSize, 1 );
        }
        catch ( final PwmException e )
        {
            pwmRequest.outputJsonResult( RestResultBean.fromError( e.getErrorInformation(), pwmRequest ) );
            LOGGER.error( pwmRequest, () -> "error during file upload: " + e.getErrorInformation().toDebugStr() );
            return null;
        }
        catch ( final Throwable e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, "error during file upload: " + e.getMessage() );
            pwmRequest.outputJsonResult( RestResultBean.fromError( errorInformation, pwmRequest ) );
            LOGGER.error( pwmRequest, errorInformation );
            return null;
        }

        if ( fileUploads.containsKey( PwmConstants.PARAM_FILE_UPLOAD ) )
        {
            final PwmRequest.FileUploadItem uploadItem = fileUploads.get( PwmConstants.PARAM_FILE_UPLOAD );

            final Map<FileValue.FileInformation, FileValue.FileContent> newFileValueMap = new LinkedHashMap<>();
            newFileValueMap.put( new FileValue.FileInformation( uploadItem.getName(), uploadItem.getType() ), new FileValue.FileContent( uploadItem.getContent() ) );

            return new FileValue( newFileValueMap );
        }

        final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, "no file found in upload" );
        pwmRequest.outputJsonResult( RestResultBean.fromError( errorInformation, pwmRequest ) );
        LOGGER.error( pwmRequest, () -> "error during file upload: " + errorInformation.toDebugStr() );
        return null;
    }

    static void outputChangeLogData(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean,
            final Map<String, Object> outputMap
    )
    {
            final Locale locale = pwmRequest.getLocale();

            final Set<StoredConfigItemKey> changeLog = StoredConfigurationUtil.changedValues(
                    pwmRequest.getPwmApplication().getConfig().getStoredConfiguration(),
                    configManagerBean.getStoredConfiguration() );

            final Map<String, String> changeLogMap = StoredConfigurationUtil.makeDebugMap(
                    configManagerBean.getStoredConfiguration(),
                    changeLog,
                    locale );

            final StringBuilder output = new StringBuilder();
            if ( changeLogMap.isEmpty() )
            {
                output.append( "No setting changes." );
            }
            else
            {
                for ( final Map.Entry<String, String> entry : changeLogMap.entrySet() )
                {
                    output.append( "<div class=\"changeLogKey\">" );
                    output.append( entry.getKey() );
                    output.append( "</div><div class=\"changeLogValue\">" );
                    output.append( StringUtil.escapeHtml( entry.getValue() ) );
                    output.append( "</div>" );
                }
            }
            outputMap.put( "html", output.toString() );
            outputMap.put( "modified", !changeLog.isEmpty() );

    }

    static HealthData configurationHealth(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
    {
        final Instant startTime = Instant.now();
        try
        {
            final Locale locale = pwmRequest.getLocale();
            final ConfigurationChecker configurationChecker = new ConfigurationChecker();
            final Configuration config = new Configuration( configManagerBean.getStoredConfiguration() );
            final List<HealthRecord> healthRecords = configurationChecker.doHealthCheck(
                    config,
                    pwmRequest.getLocale()
            );

            LOGGER.debug( () -> "config health check done in ", () -> TimeDuration.fromCurrent( startTime ) );

            return HealthData.builder()
                    .overall( "CONFIG" )
                    .records( password.pwm.ws.server.rest.bean.HealthRecord.fromHealthRecords( healthRecords, locale, config ) )
                    .build();
        }
        catch ( final Exception e )
        {
            LOGGER.error( pwmRequest, () -> "error generating health records: " + e.getMessage() );
        }

        return HealthData.builder().build();
    }

    static ConfigEditorServlet.ReadSettingResponse handleLocaleBundleReadSetting(
            final PwmRequest pwmRequest,
            final StoredConfiguration storedConfig,
            final String key

    )
    {
        final ConfigEditorServlet.ReadSettingResponse.ReadSettingResponseBuilder builder = ConfigEditorServlet.ReadSettingResponse.builder();
        final StringTokenizer st = new StringTokenizer( key, "-" );
        st.nextToken();
        final String localeBundleName = st.nextToken();
        final PwmLocaleBundle pwmLocaleBundle = PwmLocaleBundle.forKey( localeBundleName )
                .orElseThrow( () -> new IllegalArgumentException( "unknown locale bundle name '" + localeBundleName + "'" ) );
        final String keyName = st.nextToken();
        final Map<String, String> bundleMap = storedConfig.readLocaleBundleMap( pwmLocaleBundle, keyName );
        if ( bundleMap == null || bundleMap.isEmpty() )
        {
            final Map<String, String> defaultValueMap = new LinkedHashMap<>();
            final String defaultLocaleValue = ResourceBundle.getBundle( pwmLocaleBundle.getTheClass().getName(), PwmConstants.DEFAULT_LOCALE ).getString( keyName );
            for ( final Locale locale : pwmRequest.getConfig().getKnownLocales() )
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
        builder.key( key );
        return builder.build();
    }

    static ConfigEditorServlet.ReadSettingResponse handleReadSetting(
            final PwmRequest pwmRequest,
            final StoredConfiguration storedConfig,
            final String key
    )
            throws PwmUnrecoverableException
    {
        final ConfigEditorServlet.ReadSettingResponse.ReadSettingResponseBuilder builder = ConfigEditorServlet.ReadSettingResponse.builder();
        final PwmSetting theSetting = PwmSetting.forKey( key )
                .orElseThrow( () -> new IllegalStateException( "invalid setting parameter value" ) );

        final Object returnValue;
        final String profile = theSetting.getCategory().hasProfiles() ? pwmRequest.readParameterAsString( "profile" ) : null;
        switch ( theSetting.getSyntax() )
        {
            case PASSWORD:
                returnValue = Collections.singletonMap( "isDefault", storedConfig.isDefaultValue( theSetting, profile ) );
                break;

            case X509CERT:
                returnValue = ( ( X509CertificateValue ) storedConfig.readSetting( theSetting, profile ) ).toInfoMap( true );
                break;

            case PRIVATE_KEY:
                returnValue = ( ( PrivateKeyValue ) storedConfig.readSetting( theSetting, profile ) ).toInfoMap( true );
                break;

            case ACTION:
                returnValue = ( ( ActionValue ) storedConfig.readSetting( theSetting, profile ) ).toInfoMap();
                break;

            case REMOTE_WEB_SERVICE:
                returnValue = ( ( RemoteWebServiceValue ) storedConfig.readSetting( theSetting, profile ) ).toInfoMap();
                break;

            case FILE:
                returnValue = ( ( FileValue ) storedConfig.readSetting( theSetting, profile ) ).toInfoMap();
                break;

            default:
                returnValue = storedConfig.readSetting( theSetting, profile ).toNativeObject();

        }
        builder.value( returnValue );

        builder.isDefault( storedConfig.isDefaultValue( theSetting, profile ) );
        if ( theSetting.getSyntax() == PwmSettingSyntax.SELECT )
        {
            builder.options( theSetting.getOptions() );
        }
        {
            final ValueMetaData settingMetaData = storedConfig.readSettingMetadata( theSetting, profile );
            if ( settingMetaData != null )
            {
                if ( settingMetaData.getModifyDate() != null )
                {
                    builder.modifyTime( settingMetaData.getModifyDate() );
                }
                if ( settingMetaData.getUserIdentity() != null )
                {
                    builder.modifyUser( settingMetaData.getUserIdentity() );
                }
            }
        }
        builder.key( key );
        builder.category( theSetting.getCategory().toString() );
        builder.syntax( theSetting.getSyntax().toString() );
        return builder.build();
    }

    static void processHttpsCertificateUpload(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, ServletException
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

            final int maxFileSize = Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.CONFIG_MAX_JDBC_JAR_SIZE ) );
            final Map<String, PwmRequest.FileUploadItem> fileUploads = pwmRequest.readFileUploads( maxFileSize, 1 );
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
}
