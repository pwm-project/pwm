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

package password.pwm.http;

import password.pwm.PwmConstants;
import password.pwm.util.java.StringUtil;

import java.nio.charset.Charset;
import java.util.List;

public enum HttpContentType
{
    json( "application/json", PwmConstants.DEFAULT_CHARSET ),
    zip( "application/zip" ),
    gzip( "application/gzip" ),
    xml( "text/xml", PwmConstants.DEFAULT_CHARSET ),
    csv( "text/csv", PwmConstants.DEFAULT_CHARSET ),
    javascript( "text/javascript", PwmConstants.DEFAULT_CHARSET ),
    plain( "text/plain", PwmConstants.DEFAULT_CHARSET ),
    html( "text/html", PwmConstants.DEFAULT_CHARSET ),
    form( "application/x-www-form-urlencoded", PwmConstants.DEFAULT_CHARSET ),
    png( "image/png" ),
    octetstream( "application/octet-stream" ),;

    private final String mimeType;
    private final String charset;

    HttpContentType( final String mimeType, final Charset charset )
    {
        this.mimeType = mimeType;
        this.charset = charset.name();
    }

    HttpContentType( final String mimeType )
    {
        this.mimeType = mimeType;
        this.charset = null;
    }

    public String getHeaderValue( )
    {
        if ( charset == null )
        {
            return mimeType;
        }
        return mimeType + "; charset=" + charset;
    }

    public String getMimeType( )
    {
        return this.mimeType;
    }

    public static HttpContentType fromContentTypeHeader( final String headerValue, final HttpContentType anyMatch )
    {
        if ( StringUtil.isEmpty( headerValue ) )
        {
            return null;
        }

        final List<String> values = StringUtil.splitAndTrim( headerValue, "," );

        for ( final String value : values )
        {
            final String mimeValue = value.contains( ";" )
                    ? value.substring( 0, value.indexOf( ";" ) )
                    : value;

            for ( final HttpContentType httpContentType : HttpContentType.values() )
            {
                if ( mimeValue.equalsIgnoreCase( httpContentType.getMimeType() ) )
                {
                    return httpContentType;
                }
            }
        }

        if ( anyMatch != null )
        {
            if ( values.contains( "*/*" ) )
            {
                return anyMatch;
            }
        }

        return null;
    }
}
