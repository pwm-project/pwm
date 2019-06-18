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

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.util.Locale;

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
        final HttpPost httpPost = new HttpPost( url );
        httpPost.setHeader( "Accept", PwmConstants.AcceptValue.json.getHeaderValue() );
        if ( locale != null )
        {
            httpPost.setHeader( "Accept-Locale", locale.toString() );
        }
        httpPost.setHeader( "Content-Type", HttpContentType.json.getHeaderValue() );
        final HttpResponse httpResponse;
        try
        {
            final StringEntity stringEntity = new StringEntity( jsonRequestBody );
            stringEntity.setContentType( PwmConstants.AcceptValue.json.getHeaderValue() );
            httpPost.setEntity( stringEntity );
            LOGGER.debug( () -> "beginning external rest call to: " + httpPost.toString() + ", body: " + jsonRequestBody );
            httpResponse = PwmHttpClient.getHttpClient( pwmApplication.getConfig() ).execute( httpPost );
            final String responseBody = EntityUtils.toString( httpResponse.getEntity() );
            LOGGER.trace( () -> "external rest call returned: " + httpResponse.getStatusLine().toString() + ", body: " + responseBody );
            if ( httpResponse.getStatusLine().getStatusCode() != 200 )
            {
                final String errorMsg = "received non-200 response code (" + httpResponse.getStatusLine().getStatusCode() + ") when executing web-service";
                LOGGER.error( errorMsg );
                throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg ) );
            }
            return responseBody;
        }
        catch ( IOException e )
        {
            final String errorMsg = "http response error while executing external rest call, error: " + e.getMessage();
            LOGGER.error( errorMsg );
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg ), e );
        }
    }
}
