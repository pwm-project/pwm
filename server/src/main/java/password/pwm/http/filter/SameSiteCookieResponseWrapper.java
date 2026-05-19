/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2023 The PWM Project
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

package password.pwm.http.filter;

import password.pwm.http.HttpHeader;
import password.pwm.util.java.StringUtil;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.util.Locale;

/**
 * Response wrapper that appends a {@code SameSite} attribute to every {@code Set-Cookie}
 * header emitted through the wrapped response.  The wrapper intercepts cookies at the
 * moment they are written, so it works with response implementations (such as Jetty 12)
 * that make the response immutable as soon as it is committed.
 *
 * <p>The previous design rewrote {@code Set-Cookie} headers in
 * {@link CookieManagementFilter} <em>after</em> {@code FilterChain#doFilter} returned;
 * by that point Jetty 12 considers the response read-only and throws
 * {@link UnsupportedOperationException}.  Doing the work via this wrapper keeps the
 * mutation strictly within the writable phase of the response lifecycle, which is
 * compliant with the Servlet specification and works on every container.</p>
 */
class SameSiteCookieResponseWrapper extends HttpServletResponseWrapper
{
    private static final String SET_COOKIE_HEADER = HttpHeader.SetCookie.getHttpName();
    private static final String SAME_SITE_ATTR = "SameSite";

    private final String sameSiteValue;

    SameSiteCookieResponseWrapper( final HttpServletResponse response, final String sameSiteValue )
    {
        super( response );
        this.sameSiteValue = sameSiteValue;
    }

    @Override
    public void setHeader( final String name, final String value )
    {
        if ( isSetCookie( name ) )
        {
            super.setHeader( name, appendSameSite( value ) );
        }
        else
        {
            super.setHeader( name, value );
        }
    }

    @Override
    public void addHeader( final String name, final String value )
    {
        if ( isSetCookie( name ) )
        {
            super.addHeader( name, appendSameSite( value ) );
        }
        else
        {
            super.addHeader( name, value );
        }
    }

    @Override
    public void addCookie( final Cookie cookie )
    {
        if ( cookie == null || StringUtil.isEmpty( sameSiteValue ) )
        {
            super.addCookie( cookie );
            return;
        }

        // The javax.servlet 4.x Cookie API does not expose a SameSite attribute, so we
        // serialize the cookie ourselves and emit it as a Set-Cookie header (which is what
        // every modern servlet container does internally).  Routing through addHeader gives
        // us a single code path for appending the SameSite attribute.
        super.addHeader( SET_COOKIE_HEADER, appendSameSite( serializeCookie( cookie ) ) );
    }

    private boolean isSetCookie( final String headerName )
    {
        return SET_COOKIE_HEADER.equalsIgnoreCase( headerName ) && !StringUtil.isEmpty( sameSiteValue );
    }

    private String appendSameSite( final String headerValue )
    {
        if ( headerValue == null || StringUtil.isEmpty( sameSiteValue ) )
        {
            return headerValue;
        }
        if ( headerValue.toLowerCase( Locale.ROOT ).contains( SAME_SITE_ATTR.toLowerCase( Locale.ROOT ) ) )
        {
            return headerValue;
        }
        return headerValue + "; " + SAME_SITE_ATTR + "=" + sameSiteValue;
    }

    /**
     * RFC 6265-style serialization of a {@link Cookie}.  Covers the attributes exposed by
     * the {@code javax.servlet} 4.x API; SameSite is added separately via {@link #appendSameSite}.
     */
    private static String serializeCookie( final Cookie cookie )
    {
        final StringBuilder sb = new StringBuilder();
        sb.append( cookie.getName() ).append( '=' );
        if ( cookie.getValue() != null )
        {
            sb.append( cookie.getValue() );
        }

        if ( cookie.getMaxAge() >= 0 )
        {
            sb.append( "; Max-Age=" ).append( cookie.getMaxAge() );
        }

        if ( !StringUtil.isEmpty( cookie.getPath() ) )
        {
            sb.append( "; Path=" ).append( cookie.getPath() );
        }

        if ( !StringUtil.isEmpty( cookie.getDomain() ) )
        {
            sb.append( "; Domain=" ).append( cookie.getDomain() );
        }

        if ( cookie.getSecure() )
        {
            sb.append( "; Secure" );
        }

        if ( cookie.isHttpOnly() )
        {
            sb.append( "; HttpOnly" );
        }

        return sb.toString();
    }
}
