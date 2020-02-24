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
import password.pwm.svc.httpclient.PwmHttpClient;
import password.pwm.svc.httpclient.PwmHttpClientConfiguration;
import password.pwm.svc.httpclient.PwmHttpClientRequest;
import password.pwm.svc.httpclient.PwmHttpClientResponse;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.security.cert.X509Certificate;
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
        httpHeaders.put( HttpHeader.ContentType.getHttpName(), HttpContentType.json.getHeaderValueWithEncoding() );
        if ( locale != null )
        {
            httpHeaders.put( HttpHeader.AcceptLanguage.getHttpName(), locale.toString() );
        }

        {
            final Map<String, String> configuredHeaders = new LinkedHashMap<>( remoteWebServiceConfiguration.getHeaders() );

            // add basic auth header;
            if ( !StringUtil.isEmpty( remoteWebServiceConfiguration.getUsername() ) && !StringUtil.isEmpty( remoteWebServiceConfiguration.getPassword() ) )
            {
                final String authHeaderValue = new BasicAuthInfo( remoteWebServiceConfiguration.getUsername(),
                        new PasswordData( remoteWebServiceConfiguration.getPassword() ) )
                        .toAuthHeader();
                configuredHeaders.put( HttpHeader.Authorization.getHttpName(), authHeaderValue );
            }

            httpHeaders.putAll( configuredHeaders );
        }

        final String jsonRequestBody = JsonUtil.serialize( formDataRequestBean );

        final PwmHttpClientRequest pwmHttpClientRequest = PwmHttpClientRequest.builder()
                .method( HttpMethod.POST )
                .url( remoteWebServiceConfiguration.getUrl() )
                .body( jsonRequestBody )
                .headers( httpHeaders )
                .build();

        final PwmHttpClientResponse httpResponse;
        try
        {
            httpResponse = getHttpClient( pwmApplication.getConfig() ).makeRequest( pwmHttpClientRequest, sessionLabel );
            final String responseBody = httpResponse.getBody();
            LOGGER.trace( () -> "external rest call returned: " + httpResponse.getStatusPhrase() + ", body: " + responseBody );
            if ( httpResponse.getStatusCode() != 200 )
            {
                final String errorMsg = "received non-200 response code (" + httpResponse.getStatusCode() + ") when executing web-service";
                LOGGER.error( () -> errorMsg );
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SERVICE_UNREACHABLE, errorMsg ) );
            }
            final FormDataResponseBean formDataResponseBean = JsonUtil.deserialize( responseBody, FormDataResponseBean.class );
            return formDataResponseBean;
        }
        catch ( final PwmUnrecoverableException e )
        {
            final String errorMsg = "http response error while executing external rest call, error: " + e.getMessage();
            LOGGER.error( () -> errorMsg );
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SERVICE_UNREACHABLE, errorMsg ), e );
        }

    }

    private PwmHttpClient getHttpClient( final Configuration configuration )
            throws PwmUnrecoverableException
    {

        final List<X509Certificate> certificates = remoteWebServiceConfiguration.getCertificates();

        final PwmHttpClientConfiguration pwmHttpClientConfiguration = PwmHttpClientConfiguration.builder()
                .trustManagerType( PwmHttpClientConfiguration.TrustManagerType.configuredCertificates )
                .certificates( certificates )
                .build();
        return pwmApplication.getHttpClientService().getPwmHttpClient( pwmHttpClientConfiguration );
    }

}
