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

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
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

/**
 * Outer-most filter responsible for stamping a {@code SameSite} attribute on every
 * {@code Set-Cookie} header emitted by the application or container.
 *
 * <p>Historically this filter rewrote {@code Set-Cookie} headers <em>after</em> calling
 * {@link FilterChain#doFilter}.  That worked on older Jetty / Tomcat releases that
 * allowed post-commit header mutation, but Jetty 12 strictly enforces the Servlet
 * specification and makes the response read-only once committed, throwing
 * {@code UnsupportedOperationException: Read Only} (issue #716).</p>
 *
 * <p>The filter now wraps the response in a {@link SameSiteCookieResponseWrapper} before
 * descending the chain, so the SameSite attribute is added at the moment each cookie is
 * written.  This works on every spec-compliant container.</p>
 */
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
            LOGGER.trace( () -> "unable to load application configuration while checking samesite cookie attribute config" );
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
        final HttpServletResponse httpServletResponse = ( HttpServletResponse ) servletResponse;
        final HttpServletRequest httpServletRequest = ( HttpServletRequest ) servletRequest;

        markSessionForRecycle( httpServletRequest );

        if ( StringUtil.isEmpty( value ) )
        {
            filterChain.doFilter( servletRequest, servletResponse );
            return;
        }

        final SameSiteCookieResponseWrapper wrappedResponse = new SameSiteCookieResponseWrapper( httpServletResponse, value );
        filterChain.doFilter( servletRequest, wrappedResponse );
    }

    /**
     * Ensures that every session that modifies its samesite cookies also triggers a session ID
     * recycle, once per session.  This only mutates session-scoped state and is safe to call
     * either before or after the filter chain executes.
     *
     * @param httpServletRequest The request to be marked
     */
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
                LOGGER.trace( () -> "unable to load session while checking samesite cookie attribute config" );
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
}
