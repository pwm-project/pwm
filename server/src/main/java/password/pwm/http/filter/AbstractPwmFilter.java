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

package password.pwm.http.filter;


import password.pwm.PwmApplicationMode;
import password.pwm.error.PwmException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public abstract class AbstractPwmFilter implements Filter
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( AbstractPwmFilter.class );

    @Override
    public void init( final FilterConfig filterConfig )
            throws ServletException
    {
    }

    @Override
    public void doFilter(
            final ServletRequest servletRequest,
            final ServletResponse servletResponse,
            final FilterChain filterChain
    )
            throws IOException, ServletException
    {
        final HttpServletRequest req = ( HttpServletRequest ) servletRequest;
        final HttpServletResponse resp = ( HttpServletResponse ) servletResponse;
        final PwmApplicationMode mode = PwmApplicationMode.determineMode( req );

        final boolean interested;
        try
        {
            final PwmURL pwmURL = new PwmURL( req );
            interested = isInterested( mode, pwmURL );
        }
        catch ( Exception e )
        {
            LOGGER.error( "unexpected error processing filter chain during isInterested(): " + e.getMessage(), e );
            resp.sendError( 500, "unexpected error processing filter chain during isInterested" );
            return;
        }

        if ( interested )
        {
            PwmRequest pwmRequest = null;
            try
            {
                pwmRequest = PwmRequest.forRequest( req, resp );
            }
            catch ( PwmException e )
            {
                final PwmURL pwmURL = new PwmURL( req );
                if ( pwmURL.isResourceURL() )
                {
                    filterChain.doFilter( req, resp );
                    return;
                }

                LOGGER.error( pwmRequest, "unexpected error processing filter chain: " + e.getMessage(), e );
            }

            try
            {
                final PwmFilterChain pwmFilterChain = new PwmFilterChain( servletRequest, servletResponse, filterChain );
                processFilter( mode, pwmRequest, pwmFilterChain );
            }
            catch ( PwmException e )
            {
                LOGGER.error( pwmRequest, "unexpected error processing filter chain: " + e.getMessage(), e );
            }
            catch ( IOException e )
            {
                LOGGER.debug( pwmRequest, "i/o error processing request: " + e.getMessage() );
            }

        }
        else
        {
            filterChain.doFilter( req, resp );
        }

    }

    abstract void processFilter(
            PwmApplicationMode mode,
            PwmRequest pwmRequest,
            PwmFilterChain filterChain
    )
            throws PwmException, IOException, ServletException;

    abstract boolean isInterested(
            PwmApplicationMode mode,
            PwmURL pwmURL
    );

    @Override
    public void destroy( )
    {
    }

    public static class PwmFilterChain
    {
        private final ServletRequest servletRequest;
        private final ServletResponse servletResponse;
        private final FilterChain filterChain;

        public PwmFilterChain(
                final ServletRequest servletRequest,
                final ServletResponse servletResponse,
                final FilterChain filterChain
        )
        {
            this.servletRequest = servletRequest;
            this.servletResponse = servletResponse;
            this.filterChain = filterChain;
        }

        void doFilter( )
                throws IOException, ServletException
        {
            filterChain.doFilter( servletRequest, servletResponse );
        }

        void doFilter( final HttpServletResponse newResponse )
                throws IOException, ServletException
        {
            filterChain.doFilter( servletRequest, servletResponse );
        }

        ServletResponse getServletResponse( )
        {
            return servletResponse;
        }
    }

}
