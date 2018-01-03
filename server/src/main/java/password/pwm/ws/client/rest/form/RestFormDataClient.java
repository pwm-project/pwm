/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.ws.client.rest.form;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.data.RemoteWebServiceConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.http.client.PwmHttpClientConfiguration;
import password.pwm.http.client.PwmHttpClientRequest;
import password.pwm.http.client.PwmHttpClientResponse;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RestFormDataClient
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( RestFormDataClient.class );

    private final PwmApplication pwmApplication;
    private final SessionLabel sessionLabel;
    private RemoteWebServiceConfiguration remoteWebServiceConfiguration;

    public RestFormDataClient( final PwmApplication pwmApplication, final SessionLabel sessionLabel )
    {
        this.sessionLabel = sessionLabel;
        this.pwmApplication = pwmApplication;
        final List<RemoteWebServiceConfiguration> values = pwmApplication.getConfig().readSettingAsRemoteWebService( PwmSetting.EXTERNAL_REMOTE_DATA_URL );
        if ( values != null && !values.isEmpty() )
        {
            remoteWebServiceConfiguration = values.iterator().next();
        }
    }

    public boolean isEnabled( )
    {
        return remoteWebServiceConfiguration != null;
    }

    public FormDataResponseBean invoke(
            final FormDataRequestBean formDataRequestBean,
            final Locale locale
    )
            throws PwmUnrecoverableException
    {
        final Map<String, String> httpHeaders = new LinkedHashMap<>();
        httpHeaders.put( HttpHeader.Accept.getHttpName(), PwmConstants.AcceptValue.json.getHeaderValue() );
        httpHeaders.put( HttpHeader.Content_Type.getHttpName(), HttpContentType.json.getHeaderValue() );
        if ( locale != null )
        {
            httpHeaders.put( HttpHeader.Accept_Language.getHttpName(), locale.toString() );
        }

        {
            final List<RemoteWebServiceConfiguration> webServiceConfigurations = pwmApplication.getConfig().readSettingAsRemoteWebService( PwmSetting.EXTERNAL_REMOTE_DATA_URL );
            final Map<String, String> configuredHeaders = webServiceConfigurations != null && !webServiceConfigurations.isEmpty()
                    ? webServiceConfigurations.iterator().next().getHeaders()
                    : Collections.emptyMap();

            httpHeaders.putAll( configuredHeaders );
        }

        final String jsonRequestBody = JsonUtil.serialize( formDataRequestBean );

        final PwmHttpClientRequest pwmHttpClientRequest = new PwmHttpClientRequest(
                HttpMethod.POST,
                remoteWebServiceConfiguration.getUrl(),
                jsonRequestBody,
                httpHeaders

        );

        final PwmHttpClientResponse httpResponse;
        try
        {
            httpResponse = getHttpClient( pwmApplication.getConfig() ).makeRequest( pwmHttpClientRequest );
            final String responseBody = httpResponse.getBody();
            LOGGER.trace( "external rest call returned: " + httpResponse.getStatusPhrase() + ", body: " + responseBody );
            if ( httpResponse.getStatusCode() != 200 )
            {
                final String errorMsg = "received non-200 response code (" + httpResponse.getStatusCode() + ") when executing web-service";
                LOGGER.error( errorMsg );
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SERVICE_UNREACHABLE, errorMsg ) );
            }
            final FormDataResponseBean formDataResponseBean = JsonUtil.deserialize( responseBody, FormDataResponseBean.class );
            return formDataResponseBean;
        }
        catch ( PwmUnrecoverableException e )
        {
            final String errorMsg = "http response error while executing external rest call, error: " + e.getMessage();
            LOGGER.error( errorMsg );
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SERVICE_UNREACHABLE, errorMsg ), e );
        }

    }

    private PwmHttpClient getHttpClient( final Configuration configuration )
            throws PwmUnrecoverableException
    {
        final List<RemoteWebServiceConfiguration> webServiceConfigurations = configuration.readSettingAsRemoteWebService( PwmSetting.EXTERNAL_REMOTE_DATA_URL );

        final List<X509Certificate> certificates;
        certificates = webServiceConfigurations != null && !webServiceConfigurations.isEmpty()
                ? webServiceConfigurations.iterator().next().getCertificates()
                : null;

        final PwmHttpClientConfiguration pwmHttpClientConfiguration = PwmHttpClientConfiguration.builder()
                .certificates( certificates )
                .build();
        return new PwmHttpClient( pwmApplication, null, pwmHttpClientConfiguration );
    }

}
