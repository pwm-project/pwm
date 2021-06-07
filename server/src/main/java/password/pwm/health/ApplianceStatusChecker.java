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

package password.pwm.health;

import org.apache.commons.io.FileUtils;
import password.pwm.PwmApplication;
import password.pwm.PwmEnvironment;
import password.pwm.bean.DomainID;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.svc.httpclient.PwmHttpClient;
import password.pwm.svc.httpclient.PwmHttpClientConfiguration;
import password.pwm.svc.httpclient.PwmHttpClientRequest;
import password.pwm.svc.httpclient.PwmHttpClientResponse;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ApplianceStatusChecker implements HealthSupplier
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ApplianceStatusChecker.class );

    private static class UpdateStatus implements Serializable
    {
        boolean pendingInstallation;
        boolean autoUpdatesEnabled;
        boolean updateServiceConfigured;
    }

    @Override
    public List<Supplier<List<HealthRecord>>> jobs( final HealthSupplier.HealthSupplierRequest request )
    {
        final Supplier<List<HealthRecord>> supplier = () -> doHealthCheck( request );
        return Collections.singletonList( supplier );
    }

    private List<HealthRecord> doHealthCheck( final HealthSupplier.HealthSupplierRequest request )
    {
        final PwmApplication pwmApplication = request.getPwmApplication();
        final boolean isApplianceAvailable = pwmApplication.getPwmEnvironment().getFlags().contains( PwmEnvironment.ApplicationFlag.Appliance );

        if ( !isApplianceAvailable )
        {
            return Collections.emptyList();
        }

        try
        {
            return List.copyOf( readApplianceHealthStatus( request ) );
        }
        catch ( final Exception e )
        {
            LOGGER.error( request.getSessionLabel(), () -> "error communicating with client " + e.getMessage() );
        }

        return Collections.emptyList();
    }

    private List<HealthRecord> readApplianceHealthStatus( final HealthSupplier.HealthSupplierRequest request  )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final PwmApplication pwmApplication = request.getPwmApplication();
        final List<HealthRecord> healthRecords = new ArrayList<>();

        final String url = figureUrl( request );
        final Map<String, String> requestHeaders = Collections.singletonMap( "sspr-authorization-token", getApplianceAccessToken( pwmApplication ) );

        final PwmHttpClientConfiguration pwmHttpClientConfiguration = PwmHttpClientConfiguration.builder()
                .trustManagerType( PwmHttpClientConfiguration.TrustManagerType.promiscuous )
                .build();

        final PwmHttpClient pwmHttpClient = pwmApplication.getHttpClientService().getPwmHttpClient( pwmHttpClientConfiguration );
        final PwmHttpClientRequest pwmHttpClientRequest = PwmHttpClientRequest.builder()
                .method( HttpMethod.GET )
                .url( url )
                .headers( requestHeaders )
                .build();

        final PwmHttpClientResponse response = pwmHttpClient.makeRequest( pwmHttpClientRequest, request.getSessionLabel() );

        LOGGER.trace( request.getSessionLabel(), () -> "https response from appliance server request: " + response.getBody() );

        final String jsonString = response.getBody();

        LOGGER.debug( () -> "response from /sspr/appliance-update-status: " + jsonString );

        final UpdateStatus updateStatus = JsonUtil.deserialize( jsonString, UpdateStatus.class );

        if ( updateStatus.pendingInstallation )
        {
            healthRecords.add( HealthRecord.forMessage( DomainID.systemId(), HealthMessage.Appliance_PendingUpdates ) );
        }

        if ( !updateStatus.autoUpdatesEnabled )
        {
            healthRecords.add( HealthRecord.forMessage( DomainID.systemId(), HealthMessage.Appliance_UpdatesNotEnabled ) );
        }

        if ( !updateStatus.updateServiceConfigured )
        {
            healthRecords.add( HealthRecord.forMessage( DomainID.systemId(), HealthMessage.Appliance_UpdateServiceNotConfigured ) );
        }

        return Collections.unmodifiableList( healthRecords );

    }

    private String getApplianceAccessToken( final PwmApplication pwmApplication ) throws PwmOperationalException
    {
        final String tokenFile = pwmApplication.getPwmEnvironment().getParameters().get( PwmEnvironment.ApplicationParameter.ApplianceTokenFile );
        if ( StringUtil.isEmpty( tokenFile ) )
        {
            final String msg = "unable to determine appliance token, token file environment param "
                    + PwmEnvironment.ApplicationParameter.ApplianceTokenFile.toString() + " is not set";
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_INTERNAL, msg ) );
        }
        final String fileInput = readFileContents( tokenFile );
        if ( fileInput != null )
        {
            return fileInput.trim();
        }
        return "";
    }

    private String figureUrl( final HealthSupplier.HealthSupplierRequest request ) throws PwmOperationalException
    {
        final PwmApplication pwmApplication = request.getPwmApplication();
        final String hostnameFile = pwmApplication.getPwmEnvironment().getParameters().get( PwmEnvironment.ApplicationParameter.ApplianceHostnameFile );
        if ( StringUtil.isEmpty( hostnameFile ) )
        {
            final String msg = "unable to determine appliance hostname, hostname file environment param "
                    + PwmEnvironment.ApplicationParameter.ApplianceHostnameFile.toString() + " is not set";
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_INTERNAL, msg ) );
        }

        final String hostname = readFileContents( hostnameFile );
        final String port = pwmApplication.getPwmEnvironment().getParameters().get( PwmEnvironment.ApplicationParameter.AppliancePort );

        final String url = "https://" + hostname + ":" + port + "/sspr/appliance-update-status";
        LOGGER.trace( request.getSessionLabel(), () -> "calculated appliance host url as: " + url );
        return url;
    }

    private String readFileContents( final String filename ) throws PwmOperationalException
    {
        try
        {
            final String fileInput = FileUtils.readFileToString( new File( filename ) );
            if ( fileInput != null )
            {
                final String trimmedStr = fileInput.trim();
                return trimmedStr.replace( "\n", "" );
            }
            return "";
        }
        catch ( final IOException e )
        {
            final String msg = "unable to read contents of file '" + filename + "', error: " + e.getMessage();
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_INTERNAL, msg ), e );
        }
    }
}
