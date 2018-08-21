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
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;

public enum HttpHeader
{
    Authorization( "Authorization", Property.Sensitive ),
    Accept( "Accept" ),
    AcceptEncoding( "Accept-Encoding" ),
    AcceptLanguage( "Accept-Language" ),
    CacheControl( "Cache-Control" ),
    Connection( "Connection" ),
    ContentEncoding( "Content-Encoding" ),
    ContentDisposition( "content-disposition" ),
    ContentLanguage( "Content-Language" ),
    ContentLength( "Content-Length" ),
    ContentSecurityPolicy( "Content-Security-Policy" ),
    ContentTransferEncoding( "Content-Transfer-Encoding" ),
    ContentType( "Content-Type" ),
    ETag( "ETag" ),
    Expires( "Expires" ),
    If_None_Match( "If-None-Match" ),
    Location( "Location" ),
    Origin( "Origin" ),
    Referer( "Referer" ),
    Server( "Server" ),
    UserAgent( "User-Agent" ),
    WWW_Authenticate( "WWW-Authenticate" ),
    XContentTypeOptions( "X-Content-Type-Options" ),
    XForwardedFor( "X-Forwarded-For" ),
    XFrameOptions( "X-Frame-Options" ),
    XXSSProtection( "X-XSS-Protection" ),


    XAmb( "X-" + PwmConstants.PWM_APP_NAME + "-Amb" ),
    XVersion( "X-" + PwmConstants.PWM_APP_NAME + "-Version" ),
    XInstance( "X-" + PwmConstants.PWM_APP_NAME + "-Instance" ),
    XSessionID( "X-" + PwmConstants.PWM_APP_NAME + "-SessionID" ),
    XNoise( "X-" + PwmConstants.PWM_APP_NAME + "-Noise" ),;

    private enum Property
    {
        Sensitive
    }

    private final String httpName;
    private final Property[] properties;

    HttpHeader( final String httpName, final Property... properties )
    {
        this.httpName = httpName;
        this.properties = properties;
    }

    public String getHttpName( )
    {
        return httpName;
    }

    public boolean isSensitive( )
    {
        return JavaHelper.enumArrayContainsValue( properties, Property.Sensitive );
    }

    public static HttpHeader forHttpHeader( final String header )
    {
        if ( StringUtil.isEmpty( header ) )
        {
            return null;
        }

        for ( final HttpHeader httpHeader : values() )
        {
            if ( header.equals( httpHeader.getHttpName() ) )
            {
                return httpHeader;
            }
        }

        return null;
    }
}
