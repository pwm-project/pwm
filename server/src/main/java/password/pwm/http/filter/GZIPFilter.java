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

import com.github.ziplet.filter.compression.CompressingFilter;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmURL;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * GZip Filter Wrapper.  This filter must be invoked _before_ a PwmRequest object is instantiated, else
 * it will cache a reference to the original response and break the application.
 */
public class GZIPFilter implements Filter
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( GZIPFilter.class );

    private final CompressingFilter compressingFilter = new CompressingFilter();
    private boolean enabled = false;

    public void init( final FilterConfig filterConfig )
            throws ServletException
    {
        final PwmApplication pwmApplication;
        try
        {
            pwmApplication = ContextManager.getPwmApplication( filterConfig.getServletContext() );
            enabled = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.HTTP_ENABLE_GZIP ) );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.warn( () -> "unable to load application configuration, defaulting to disabled" );
        }

        compressingFilter.init( filterConfig );
    }

    public void destroy( )
    {
        compressingFilter.destroy();
    }

    @Override
    public void doFilter( final ServletRequest servletRequest, final ServletResponse servletResponse, final FilterChain filterChain )
            throws IOException, ServletException
    {
        if ( enabled && interestInRequest( servletRequest ) )
        {
            compressingFilter.doFilter( servletRequest, servletResponse, filterChain );
        }
        else
        {
            filterChain.doFilter( servletRequest, servletResponse );
        }
    }

    private boolean interestInRequest( final ServletRequest servletRequest )
    {
        try
        {
            final PwmURL pwmURL = new PwmURL( ( HttpServletRequest ) servletRequest );

            // resource servlet does its own gzip compression with fancy server-side caching
            if ( pwmURL.isResourceURL() )
            {
                return false;
            }
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "unable to parse request url, defaulting to non-gzip: " + e.getMessage() );
        }

        return true;
    }
}
