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
    SetCookie( "Set-Cookie" ),
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
