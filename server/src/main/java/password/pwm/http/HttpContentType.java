/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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
import java.util.Optional;

public enum HttpContentType
{
    json( HttpEntityDataType.String, PwmConstants.DEFAULT_CHARSET, "application/json", "application/javascript" ),
    zip( HttpEntityDataType.ByteArray, null, "application/zip" ),
    gzip( HttpEntityDataType.ByteArray, null, "application/gzip" ),
    xml( HttpEntityDataType.String, PwmConstants.DEFAULT_CHARSET, "text/xml" ),
    csv( HttpEntityDataType.String, PwmConstants.DEFAULT_CHARSET, "text/csv" ),
    javascript( HttpEntityDataType.String, PwmConstants.DEFAULT_CHARSET, "text/javascript" ),
    plain( HttpEntityDataType.String, PwmConstants.DEFAULT_CHARSET, "text/plain" ),
    html( HttpEntityDataType.String, PwmConstants.DEFAULT_CHARSET, "text/html" ),
    form( HttpEntityDataType.String, PwmConstants.DEFAULT_CHARSET, "application/x-www-form-urlencoded" ),
    png( HttpEntityDataType.ByteArray, null, "image/png" ),
    jpg( HttpEntityDataType.ByteArray, null, "image/jpg", "image/jpeg" ),
    bmp( HttpEntityDataType.ByteArray, null, "image/bmp" ),
    webp( HttpEntityDataType.ByteArray, null, "image/webp" ),
    octetstream( HttpEntityDataType.ByteArray, null, "application/octet-stream" ),;

    private final String[] mimeType;
    private final Charset charset;
    private final HttpEntityDataType dataType;

    HttpContentType( final HttpEntityDataType dataType, final Charset charset, final String... mimeType )
    {
        this.mimeType = mimeType;
        this.dataType = dataType;
        this.charset = charset;
    }

    public String getHeaderValueWithEncoding( )
    {
        String output = getMimeType();
        if ( charset != null )
        {
            output += "; charset=" + charset.name();
        }

        return output;
    }

    public String getMimeType( )
    {
        return this.mimeType[0];
    }

    public HttpEntityDataType getDataType()
    {
        return dataType;
    }

    public static Optional<HttpContentType> fromContentTypeHeader( final String headerValue, final HttpContentType anyMatch )
    {
        if ( StringUtil.isEmpty( headerValue ) )
        {
            return Optional.empty();
        }

        final List<String> values = StringUtil.splitAndTrim( headerValue, "," );

        for ( final String value : values )
        {
            final String mimeValue = value.contains( ";" )
                    ? value.substring( 0, value.indexOf( ";" ) )
                    : value;

            for ( final HttpContentType httpContentType : HttpContentType.values() )
            {
                for ( final String type : httpContentType.mimeType )
                {
                    if ( mimeValue.equalsIgnoreCase( type ) )
                    {
                        return Optional.of( httpContentType );
                    }
                }
            }
        }

        if ( anyMatch != null )
        {
            if ( values.contains( "*/*" ) )
            {
                return Optional.of ( anyMatch );
            }
        }

        return Optional.empty();
    }
}
