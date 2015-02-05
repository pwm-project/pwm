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

import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.error.PwmError;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Authorization servlet filter.  Manages PWM authorization levels.  Primarily,
 * this filter handles authorization filtering for the PWM Admin modules.
 *
 * @author Jason D. Rivard
 */
public class AuthorizationFilter extends AbstractPwmFilter {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(AuthenticationFilter.class);

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Filter ---------------------

    public void init(final FilterConfig filterConfig)
            throws ServletException {
    }

    public void processFilter(
            final PwmRequest pwmRequest,
            final PwmFilterChain chain
    )
            throws IOException, ServletException
    {

        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        // if the user is not authenticated as a PWM Admin, redirect to error page.
        boolean hasPermission = false;
        try {
            hasPermission = pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.PWMADMIN);
        } catch (Exception e) {
            LOGGER.warn(pwmRequest, "error during authorization check: " + e.getMessage());
        }

        try {
            if (hasPermission) {
                chain.doFilter();
                return;
            }
        } catch (Exception e) {
            LOGGER.warn(pwmRequest, "unexpected error executing filter chain: " + e.getMessage());
            return;
        }

        pwmRequest.respondWithError(PwmError.ERROR_UNAUTHORIZED.toInfo());
    }

    public void destroy() {
    }
}