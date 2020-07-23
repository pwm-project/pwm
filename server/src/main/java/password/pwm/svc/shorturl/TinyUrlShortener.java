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

package password.pwm.svc.shorturl;

import password.pwm.PwmApplication;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.svc.httpclient.PwmHttpClient;
import password.pwm.svc.httpclient.PwmHttpClientRequest;
import password.pwm.svc.httpclient.PwmHttpClientResponse;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.util.Properties;

public class TinyUrlShortener extends BasicUrlShortener
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( TinyUrlShortener.class );

    private final String apiUrl = "http://tinyurl.com/api-create.php?url=";

    private Properties configuration = null;

    public TinyUrlShortener( )
    {
    }

    public TinyUrlShortener( final Properties configuration )
    {
        this.configuration = configuration;
    }

    public String shorten( final String input, final PwmApplication context )
            throws PwmUnrecoverableException
    {
        LOGGER.debug( () -> "Trying to shorten url: " + input );
        final String encodedUrl = StringUtil.urlEncode( input );
        final String callUrl = apiUrl + encodedUrl;
        final PwmHttpClient pwmHttpClient = context.getHttpClientService().getPwmHttpClient(  );

        final PwmHttpClientRequest request = PwmHttpClientRequest.builder()
                .method( HttpMethod.GET )
                .url( callUrl )
                .build();
        final PwmHttpClientResponse httpResponse = pwmHttpClient.makeRequest( request, null );
        final int httpResponseCode = httpResponse.getStatusCode();
        if ( httpResponseCode == 200 )
        {
            final String responseBody = httpResponse.getBody();
            LOGGER.debug( () -> "Result: " + responseBody );
            return responseBody;
        }
        else
        {
            LOGGER.error( () -> "Failed to get shorter URL: " + httpResponse.getStatusPhrase() );
        }
        return input;
    }
}
