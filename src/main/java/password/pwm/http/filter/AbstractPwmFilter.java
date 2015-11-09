/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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


import password.pwm.error.PwmException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public abstract class AbstractPwmFilter implements Filter {
    private static final PwmLogger LOGGER = PwmLogger.forClass(AbstractPwmFilter.class);

    @Override
    public void init(FilterConfig filterConfig)
            throws ServletException
    {
    }

    @Override
    public void doFilter(
            ServletRequest servletRequest,
            ServletResponse servletResponse,
            FilterChain filterChain
    )
            throws IOException, ServletException
    {
        final HttpServletRequest req = (HttpServletRequest)servletRequest;
        final HttpServletResponse resp = (HttpServletResponse)servletResponse;

        PwmRequest pwmRequest = null;
        try {
            pwmRequest = PwmRequest.forRequest(req, resp);
        } catch (PwmException e) {
            final PwmURL pwmURL = new PwmURL(req);
            if (pwmURL.isResourceURL()) {
                filterChain.doFilter(req,resp);
                return;
            }

            LOGGER.error(pwmRequest, "unexpected error processing filter chain: " + e.getMessage(), e);
        }

        try {
            final PwmFilterChain pwmFilterChain = new PwmFilterChain(servletRequest, servletResponse, filterChain);
            processFilter(pwmRequest, pwmFilterChain);
        } catch (PwmException e) {
            LOGGER.error(pwmRequest, "unexpected error processing filter chain: " + e.getMessage(), e);
        } catch (IOException e) {
            LOGGER.debug(pwmRequest, "i/o error processing request: " + e.getMessage());
        }

    }

    abstract void processFilter(
            final PwmRequest pwmRequest,
            final PwmFilterChain filterChain
    )
        throws PwmException, IOException, ServletException;

    @Override
    public void destroy()
    {
    }

    public static class PwmFilterChain {
        private final ServletRequest servletRequest;
        private final ServletResponse servletResponse;
        private final FilterChain filterChain;

        public PwmFilterChain(
                ServletRequest servletRequest,
                ServletResponse servletResponse,
                FilterChain filterChain
        )
        {
            this.servletRequest = servletRequest;
            this.servletResponse = servletResponse;
            this.filterChain = filterChain;
        }

        void doFilter()
                throws IOException, ServletException
        {
            filterChain.doFilter(servletRequest,servletResponse);
        }

        void doFilter(final HttpServletResponse newResponse)
                throws IOException, ServletException
        {
            filterChain.doFilter(servletRequest,servletResponse);
        }

        ServletResponse getServletResponse()
        {
            return servletResponse;
        }
    }
}
