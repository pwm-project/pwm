/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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

package password.pwm;

import password.pwm.bean.SessionStateBean;
import password.pwm.error.PwmError;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Authorization servlet filter.  Manages PWM authorization levels.  Primariliy,
 * this filter handles authorization filtering for the PWM Admin modules.
 *
 * @author Jason D. Rivard
 */
public class AuthorizationFilter implements Filter {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(AuthenticationFilter.class);

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Filter ---------------------

    public void init(final FilterConfig filterConfig)
            throws ServletException {
    }

    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse, final FilterChain filterChain)
            throws IOException, ServletException {
        final HttpServletRequest req = (HttpServletRequest) servletRequest;
        final HttpServletResponse resp = (HttpServletResponse) servletResponse;
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        // if the user is not authenticated as a PWM Admin, redirect to error page.
        boolean hasPermission = false;
        try {
            hasPermission = Permission.checkPermission(Permission.PWMADMIN, pwmSession);
        } catch (Exception e) {
            LOGGER.warn("error during authorization check: " + e.getMessage());
        }

        try {
            if (hasPermission) {
                filterChain.doFilter(servletRequest, servletResponse);
                return;
            }
        } catch (Exception e) {
            LOGGER.warn("unexpected error executing filter chain: " + e.getMessage());
            return;
        }

        ssBean.setSessionError(PwmError.ERROR_UNAUTHORIZED.toInfo());
        ServletHelper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
    }

    public void destroy() {
    }
}