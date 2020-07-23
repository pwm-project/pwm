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

package password.pwm.http.servlet.configeditor;

import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.stored.StoredConfigItemKey;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.config.value.FileValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.ConfigurationChecker;
import password.pwm.health.HealthRecord;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.bean.HealthData;

import javax.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ConfigEditorServletUtils
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigEditorServletUtils.class );


    public static FileValue readFileUploadToSettingValue(
            final PwmRequest pwmRequest,
            final int maxFileSize
    )
            throws PwmUnrecoverableException, IOException, ServletException
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

            LOGGER.debug( () -> "config health check done in " + TimeDuration.compactFromCurrent( startTime ) );

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
}
