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

package password.pwm.ws.client.rest;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.svc.httpclient.PwmHttpClient;
import password.pwm.svc.httpclient.PwmHttpClientConfiguration;
import password.pwm.svc.httpclient.PwmHttpClientRequest;
import password.pwm.svc.httpclient.PwmHttpClientResponse;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.logging.PwmLogger;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class RestClientHelper
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( RestClientHelper.class );

    public static String makeOutboundRestWSCall(
            final PwmApplication pwmApplication,
            final Locale locale,
            final String url,
            final String jsonRequestBody
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final PwmHttpClientConfiguration clientConfig = PwmHttpClientConfiguration.builder()
                .trustManagerType( PwmHttpClientConfiguration.TrustManagerType.promiscuous )
                .build();
        final PwmHttpClient pwmHttpClient = pwmApplication.getHttpClientService().getPwmHttpClient( clientConfig );

        final Map<String, String> httpPost = new LinkedHashMap<>();
        if ( locale != null )
        {
            httpPost.put( HttpHeader.ContentLanguage.getHttpName(), LocaleHelper.getBrowserLocaleString( locale ) );
        }

        httpPost.put( HttpHeader.Accept.getHttpName(), PwmConstants.AcceptValue.json.getHeaderValue() );
        httpPost.put( HttpHeader.ContentType.getHttpName(), HttpContentType.json.getHeaderValueWithEncoding() );

        final PwmHttpClientRequest pwmHttpClientRequest = PwmHttpClientRequest.builder()
                .url( url )
                .method( HttpMethod.POST )
                .headers( httpPost )
                .body( jsonRequestBody )
                .build();

        final PwmHttpClientResponse httpResponse;
        try
        {
            LOGGER.debug( () -> "beginning external rest call to: " + url + ", body: " + jsonRequestBody );
            httpResponse = pwmHttpClient.makeRequest( pwmHttpClientRequest, SessionLabel.SYSTEM_LABEL  );
            final String responseBody = httpResponse.getBody();
            LOGGER.trace( () -> "external rest call returned: " + httpResponse.getStatusPhrase()  );
            if ( httpResponse.getStatusCode() != 200 )
            {
                final String errorMsg = "received non-200 response code (" + httpResponse.getStatusCode() + ") when executing web-service";
                LOGGER.error( () -> errorMsg );
                throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg ) );
            }
            return responseBody;
        }
        catch ( final Exception e )
        {
            final String errorMsg = "http response error while executing external rest call, error: " + e.getMessage();
            LOGGER.error( () -> errorMsg );
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg ), e );
        }
    }
}
