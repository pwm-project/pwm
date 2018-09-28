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

package password.pwm.health;

import org.apache.commons.io.FileUtils;
import password.pwm.PwmApplication;
import password.pwm.PwmEnvironment;
import password.pwm.bean.SessionLabel;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.http.client.PwmHttpClientConfiguration;
import password.pwm.http.client.PwmHttpClientRequest;
import password.pwm.http.client.PwmHttpClientResponse;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.X509Utils;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ApplianceStatusChecker implements HealthChecker
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ApplianceStatusChecker.class );

    private static class UpdateStatus implements Serializable
    {
        boolean pendingInstallation;
        boolean autoUpdatesEnabled;
        boolean updateServiceConfigured;
    }

    @Override
    public List<HealthRecord> doHealthCheck( final PwmApplication pwmApplication )
    {
        final boolean isApplianceAvailable = pwmApplication.getPwmEnvironment().getFlags().contains( PwmEnvironment.ApplicationFlag.Appliance );

        if ( !isApplianceAvailable )
        {
            return Collections.emptyList();
        }

        final List<HealthRecord> healthRecords = new ArrayList<>();

        try
        {
            healthRecords.addAll( readApplianceHealthStatus( pwmApplication ) );
        }
        catch ( Exception e )
        {
            LOGGER.error( SessionLabel.HEALTH_SESSION_LABEL, "error communicating with client " + e.getMessage() );
        }

        return healthRecords;
    }

    private List<HealthRecord> readApplianceHealthStatus( final PwmApplication pwmApplication ) throws IOException, PwmUnrecoverableException, PwmOperationalException
    {
        final List<HealthRecord> healthRecords = new ArrayList<>();

        final String url = figureUrl( pwmApplication );
        final Map<String, String> requestHeaders = Collections.singletonMap( "sspr-authorization-token", getApplianceAccessToken( pwmApplication ) );

        final PwmHttpClientConfiguration pwmHttpClientConfiguration = PwmHttpClientConfiguration.builder()
                .trustManager( new X509Utils.PromiscuousTrustManager() )
                .build();

        final PwmHttpClient pwmHttpClient = new PwmHttpClient( pwmApplication, SessionLabel.HEALTH_SESSION_LABEL, pwmHttpClientConfiguration );
        final PwmHttpClientRequest pwmHttpClientRequest = new PwmHttpClientRequest( HttpMethod.GET, url, null, requestHeaders );
        final PwmHttpClientResponse response = pwmHttpClient.makeRequest( pwmHttpClientRequest );

        LOGGER.trace( SessionLabel.HEALTH_SESSION_LABEL, "https response from appliance server request: " + response.getBody() );

        final String jsonString = response.getBody();

        LOGGER.debug( "response from /sspr/appliance-update-status: " + jsonString );

        final UpdateStatus updateStatus = JsonUtil.deserialize( jsonString, UpdateStatus.class );

        if ( updateStatus.pendingInstallation )
        {
            healthRecords.add( HealthRecord.forMessage( HealthMessage.Appliance_PendingUpdates ) );
        }

        if ( !updateStatus.autoUpdatesEnabled )
        {
            healthRecords.add( HealthRecord.forMessage( HealthMessage.Appliance_UpdatesNotEnabled ) );
        }

        if ( !updateStatus.updateServiceConfigured )
        {
            healthRecords.add( HealthRecord.forMessage( HealthMessage.Appliance_UpdateServiceNotConfigured ) );
        }

        return healthRecords;

    }

    private String getApplianceAccessToken( final PwmApplication pwmApplication ) throws IOException, PwmOperationalException
    {
        final String tokenFile = pwmApplication.getPwmEnvironment().getParameters().get( PwmEnvironment.ApplicationParameter.ApplianceTokenFile );
        if ( StringUtil.isEmpty( tokenFile ) )
        {
            final String msg = "unable to determine appliance token, token file environment param "
                    + PwmEnvironment.ApplicationParameter.ApplianceTokenFile.toString() + " is not set";
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_UNKNOWN, msg ) );
        }
        final String fileInput = readFileContents( tokenFile );
        if ( fileInput != null )
        {
            return fileInput.trim();
        }
        return "";
    }

    private String figureUrl( final PwmApplication pwmApplication ) throws IOException, PwmOperationalException
    {
        final String hostnameFile = pwmApplication.getPwmEnvironment().getParameters().get( PwmEnvironment.ApplicationParameter.ApplianceHostnameFile );
        if ( StringUtil.isEmpty( hostnameFile ) )
        {
            final String msg = "unable to determine appliance hostname, hostname file environment param "
                    + PwmEnvironment.ApplicationParameter.ApplianceHostnameFile.toString() + " is not set";
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_UNKNOWN, msg ) );
        }

        final String hostname = readFileContents( hostnameFile );
        final String port = pwmApplication.getPwmEnvironment().getParameters().get( PwmEnvironment.ApplicationParameter.AppliancePort );

        final String url = "https://" + hostname + ":" + port + "/sspr/appliance-update-status";
        LOGGER.trace( SessionLabel.HEALTH_SESSION_LABEL, "calculated appliance host url as: " + url );
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
        catch ( IOException e )
        {
            final String msg = "unable to read contents of file '" + filename + "', error: " + e.getMessage();
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_UNKNOWN, msg ), e );
        }
    }
}
