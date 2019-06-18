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

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import password.pwm.PwmApplication;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.client.PwmHttpClient;
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

    public String shorten( final String input, final PwmApplication context ) throws PwmUnrecoverableException
    {
        try
        {
            LOGGER.debug( () -> "Trying to shorten url: " + input );
            final String encodedUrl = StringUtil.urlEncode( input );
            final String callUrl = apiUrl + encodedUrl;
            final HttpClient httpClient = PwmHttpClient.getHttpClient( context.getConfig() );
            final HttpGet httpRequest = new HttpGet( callUrl );
            final HttpResponse httpResponse = httpClient.execute( httpRequest );
            final int httpResponseCode = httpResponse.getStatusLine().getStatusCode();
            if ( httpResponseCode == 200 )
            {
                final String responseBody = EntityUtils.toString( httpResponse.getEntity() );
                LOGGER.debug( () -> "Result: " + responseBody );
                return responseBody;
            }
            else
            {
                LOGGER.error( "Failed to get shorter URL: " + httpResponse.getStatusLine().getReasonPhrase() );
            }
        }
        catch ( java.io.IOException e )
        {
            LOGGER.error( "IOException: " + e.getMessage() );
        }
        return input;
    }
}
