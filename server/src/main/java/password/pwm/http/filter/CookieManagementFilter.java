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

package password.pwm.http.filter;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.HttpHeader;
import password.pwm.http.PwmSession;
import password.pwm.http.PwmSessionWrapper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Collection;

public class CookieManagementFilter implements Filter
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( CookieManagementFilter.class );

    private String value;

    @Override
    public void init( final FilterConfig filterConfig )
            throws ServletException
    {
        final PwmApplication pwmApplication;
        try
        {
            pwmApplication = ContextManager.getPwmApplication( filterConfig.getServletContext() );
            value = pwmApplication.getConfig().readAppProperty( AppProperty.HTTP_COOKIE_SAMESITE_VALUE );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.trace( () -> "unable to load application configuration while checking samesite cookie attribute config", e );
        }
    }

    @Override
    public void destroy()
    {

    }

    @Override
    public void doFilter( final ServletRequest servletRequest, final ServletResponse servletResponse, final FilterChain filterChain )
            throws IOException, ServletException
    {
        filterChain.doFilter( servletRequest, servletResponse );
        addSameSiteCookieAttribute( ( HttpServletResponse ) servletResponse, value );
        markSessionForRecycle( ( HttpServletRequest ) servletRequest );
    }

    private void markSessionForRecycle( final HttpServletRequest httpServletRequest )
    {
        if ( StringUtil.isEmpty( value ) )
        {
            return;
        }

        final HttpSession httpSession = httpServletRequest.getSession( false );
        if ( httpSession != null )
        {
            PwmSession pwmSession = null;
            try
            {
                pwmSession = PwmSessionWrapper.readPwmSession( httpSession );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.trace( () -> "unable to load session while checking samesite cookie attribute config", e );
            }

            if ( pwmSession != null )
            {
                if ( !pwmSession.getSessionStateBean().isSameSiteCookieRecycleRequested() )
                {
                    pwmSession.getSessionStateBean().setSameSiteCookieRecycleRequested( true );
                    pwmSession.getSessionStateBean().setSessionIdRecycleNeeded( true );
                }
            }
        }
    }

    public static void addSameSiteCookieAttribute( final HttpServletResponse response, final String value )
    {
        if ( StringUtil.isEmpty( value ) )
        {
            return;
        }

        final Collection<String> headers = response.getHeaders( HttpHeader.SetCookie.getHttpName() );
        boolean firstHeader = true;

        for ( final String header : headers )
        {
            final String newHeader;
            if ( !header.contains( "SameSite" ) )
            {
                newHeader = header + "; SameSite=" + value;
            }
            else
            {
                newHeader = header;
            }

            if ( firstHeader )
            {
                response.setHeader( HttpHeader.SetCookie.getHttpName(), newHeader );
                firstHeader = false;
            }
            else
            {
                response.addHeader( HttpHeader.SetCookie.getHttpName(), newHeader );
            }
        }
    }
}
