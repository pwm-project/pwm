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
