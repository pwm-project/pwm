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
            LOGGER.debug( "Trying to shorten url: " + input );
            final String encodedUrl = StringUtil.urlEncode( input );
            final String callUrl = apiUrl + encodedUrl;
            final HttpClient httpClient = PwmHttpClient.getHttpClient( context.getConfig() );
            final HttpGet httpRequest = new HttpGet( callUrl );
            final HttpResponse httpResponse = httpClient.execute( httpRequest );
            final int httpResponseCode = httpResponse.getStatusLine().getStatusCode();
            if ( httpResponseCode == 200 )
            {
                final String responseBody = EntityUtils.toString( httpResponse.getEntity() );
                LOGGER.debug( "Result: " + responseBody );
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
