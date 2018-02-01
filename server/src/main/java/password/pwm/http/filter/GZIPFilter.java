/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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
public class GZIPFilter implements Filter {
    private static final PwmLogger LOGGER = PwmLogger.forClass(GZIPFilter.class);

    private final CompressingFilter compressingFilter = new CompressingFilter();
    private boolean enabled = false;

    public void init(final FilterConfig filterConfig)
            throws ServletException
    {
        final PwmApplication pwmApplication;
        try {
            pwmApplication = ContextManager.getPwmApplication( filterConfig.getServletContext() );
            enabled = Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_ENABLE_GZIP));
        } catch (PwmUnrecoverableException e) {
            LOGGER.warn( "unable to load application configuration, defaulting to disabled" );
        }

        compressingFilter.init( filterConfig );
    }

    public void destroy()
    {
        compressingFilter.destroy();
    }

    @Override
    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse, final FilterChain filterChain)
            throws IOException, ServletException
    {
        if ( enabled && interestInRequest( servletRequest )) {
            compressingFilter.doFilter( servletRequest, servletResponse, filterChain );
        } else {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    private boolean interestInRequest( final ServletRequest servletRequest) {
        try {
            final PwmURL pwmURL = new PwmURL((HttpServletRequest) servletRequest);

            // resource servlet does its own gzip compression with fancy server-side caching
            if (pwmURL.isResourceURL()) {
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("unable to parse request url, defaulting to non-gzip: " + e.getMessage());
        }

        return true;
    }
}
