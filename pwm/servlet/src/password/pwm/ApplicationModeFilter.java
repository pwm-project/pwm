/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.PwmServletURLHelper;
import password.pwm.util.ServletHelper;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ApplicationModeFilter implements Filter {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ApplicationModeFilter.class.getName());

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException
    {
        // check for valid config
        servletRequest.setAttribute(PwmConstants.REQUEST_ATTR_ORIGINAL_URI, ((HttpServletRequest) servletRequest).getRequestURI());
        try {
            if (checkConfigModes((HttpServletRequest)servletRequest, (HttpServletResponse)servletResponse)) {
                return;
            }
        } catch (PwmUnrecoverableException e) {
            if (e.getError() == PwmError.ERROR_UNKNOWN) {
                try { LOGGER.error(e.getMessage()); } catch (Exception ignore) { /* noop */ }
            }
            throw new ServletException(e.getErrorInformation().toDebugStr());
        }

        filterChain.doFilter(servletRequest,servletResponse);
    }

    @Override
    public void destroy() {
    }

    private static boolean checkConfigModes(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req.getSession());
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final PwmApplication theManager = ContextManager.getPwmApplication(req.getSession());

        if (theManager == null) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,"unable to load PwmApplication instance"));
        }

        final PwmApplication.MODE mode = theManager.getApplicationMode();

        if (PwmServletURLHelper.isResourceURL(req)) {
            return false;
        }

        if (mode == PwmApplication.MODE.NEW) {
            // check if current request is actually for the config url, if it is, just do nothing.
            if (PwmServletURLHelper.isCommandServletURL(req) || PwmServletURLHelper.isWebServiceURL(req)) {
                return false;
            }

            if (!PwmServletURLHelper.isConfigGuideURL(req)) {
                LOGGER.debug(pwmSession, "unable to find a valid configuration, redirecting " + req.getRequestURI() + " to ConfigGuide");
                resp.sendRedirect(req.getContextPath() + "/config/" + PwmConstants.URL_SERVLET_CONFIG_GUIDE);
                return true;
            }
        }

        if (mode == PwmApplication.MODE.ERROR) {
            ErrorInformation rootError = ContextManager.getContextManager(req.getSession()).getStartupErrorInformation();
            if (rootError == null) {
                rootError = new ErrorInformation(PwmError.ERROR_PWM_UNAVAILABLE, "Application startup failed.");
            }
            ssBean.setSessionError(rootError);
            ServletHelper.forwardToErrorPage(req, resp, true);
            return true;
        }

        return false;
    }

}
