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

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.filter.CookieManagementFilter;
import password.pwm.util.Validator;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

public class PwmHttpResponseWrapper
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmHttpResponseWrapper.class );

    private final HttpServletRequest httpServletRequest;
    private final HttpServletResponse httpServletResponse;
    private final Configuration configuration;

    public enum CookiePath
    {
        Application,
        Private,
        CurrentURL,
        PwmServlet,;

        String toStringPath( final HttpServletRequest httpServletRequest )
        {
            switch ( this )
            {
                case Application:
                    return httpServletRequest.getServletContext().getContextPath() + "/";

                case Private:
                    return httpServletRequest.getServletContext().getContextPath() + PwmConstants.URL_PREFIX_PRIVATE;

                case CurrentURL:
                    return httpServletRequest.getRequestURI();

                case PwmServlet:
                    return new PwmURL( httpServletRequest ).determinePwmServletPath();

                default:
                    throw new IllegalStateException( "undefined CookiePath type: " + this );
            }

        }
    }

    public enum Flag
    {
        NonHttpOnly,
        BypassSanitation,
    }

    protected PwmHttpResponseWrapper(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final Configuration configuration
    )
    {
        this.httpServletRequest = request;
        this.httpServletResponse = response;
        this.configuration = configuration;
    }

    public HttpServletResponse getHttpServletResponse( )
    {
        return this.httpServletResponse;
    }

    public void sendRedirect( final String url )
            throws IOException
    {
        this.httpServletResponse.sendRedirect( Validator.sanitizeHeaderValue( configuration, url ) );
    }

    public boolean isCommitted( )
    {
        return this.httpServletResponse.isCommitted();
    }

    public void setHeader( final HttpHeader headerName, final String value )
    {
        this.httpServletResponse.setHeader(
                Validator.sanitizeHeaderValue( configuration, headerName.getHttpName() ),
                Validator.sanitizeHeaderValue( configuration, value )
        );
    }

    public void setStatus( final int status )
    {
        httpServletResponse.setStatus( status );
    }

    public void setContentType( final HttpContentType contentType )
    {
        this.getHttpServletResponse().setContentType( contentType.getHeaderValueWithEncoding() );
    }

    public PrintWriter getWriter( )
            throws IOException
    {
        return this.getHttpServletResponse().getWriter();
    }

    public OutputStream getOutputStream( )
            throws IOException
    {
        return this.getHttpServletResponse().getOutputStream();
    }

    public void writeCookie(
            final String cookieName,
            final String cookieValue,
            final int seconds,
            final Flag... flags
    )
    {
        writeCookie( cookieName, cookieValue, seconds, null, flags );
    }

    public void writeCookie(
            final String cookieName,
            final String cookieValue,
            final int seconds,
            final CookiePath path,
            final Flag... flags
    )
    {
        if ( this.getHttpServletResponse().isCommitted() )
        {
            LOGGER.warn( () -> "attempt to write cookie '" + cookieName + "' after response is committed" );
        }

        final boolean secureFlag;
        {
            final String configValue = configuration.readAppProperty( AppProperty.HTTP_COOKIE_DEFAULT_SECURE_FLAG );
            if ( configValue == null || "auto".equalsIgnoreCase( configValue ) )
            {
                secureFlag = this.httpServletRequest.isSecure();
            }
            else
            {
                secureFlag = Boolean.parseBoolean( configValue );
            }
        }

        final boolean httpOnlyEnabled = Boolean.parseBoolean( configuration.readAppProperty( AppProperty.HTTP_COOKIE_HTTPONLY_ENABLE ) );
        final boolean httpOnly = httpOnlyEnabled && !JavaHelper.enumArrayContainsValue( flags, Flag.NonHttpOnly );

        final String value;
        {
            if ( cookieValue == null )
            {
                value = null;
            }
            else
            {
                if ( JavaHelper.enumArrayContainsValue( flags, Flag.BypassSanitation ) )
                {
                    value = StringUtil.urlEncode( cookieValue );
                }
                else
                {
                    value = StringUtil.urlEncode(
                            Validator.sanitizeHeaderValue( configuration, cookieValue )
                    );
                }
            }
        }

        final Cookie theCookie = new Cookie( cookieName, value );
        theCookie.setMaxAge( seconds >= -1 ? seconds : -1 );
        theCookie.setHttpOnly( httpOnly );
        theCookie.setSecure( secureFlag );

        theCookie.setPath( path == null ? CookiePath.CurrentURL.toStringPath( httpServletRequest ) : path.toStringPath( httpServletRequest ) );
        if ( value != null && value.length() > 2000 )
        {
            LOGGER.warn( () -> "writing large cookie to response: cookieName=" + cookieName + ", length=" + value.length() );
        }
        this.getHttpServletResponse().addCookie( theCookie );
        addSameSiteCookieAttribute();
    }

    void addSameSiteCookieAttribute( )
    {
        final PwmApplication pwmApplication;
        try
        {
            pwmApplication = ContextManager.getPwmApplication( this.httpServletRequest );
            final String value = pwmApplication.getConfig().readAppProperty( AppProperty.HTTP_COOKIE_SAMESITE_VALUE );
            CookieManagementFilter.addSameSiteCookieAttribute( httpServletResponse, value );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.trace( () -> "unable to load application configuration while checking samesite cookie attribute config", e );
        }
    }

    public void removeCookie( final String cookieName, final CookiePath path )
    {
        writeCookie( cookieName, null, 0, path );
    }
}
