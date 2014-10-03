/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.util.ServletHelper;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ApplicationModeFilter implements Filter {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ApplicationModeFilter.class.getName());

    private ServletContext servletContext;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.servletContext = filterConfig.getServletContext();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException
    {
        final HttpServletRequest req = (HttpServletRequest)servletRequest;
        final HttpServletResponse resp = (HttpServletResponse)servletResponse;

        // add request url to request attribute
        servletRequest.setAttribute(PwmConstants.REQUEST_ATTR_ORIGINAL_URI, req.getRequestURI());

        // check if app is available.
        boolean applicationIsAvailable = false;
        try {
            ContextManager.getPwmApplication(req);
            applicationIsAvailable = true;
        } catch (Throwable e) {
            LOGGER.error("can't load application: " + e.getMessage());
        }

        if (!applicationIsAvailable) {
            if (!(new PwmURL(req).isResourceURL())) {
                final String url = PwmConstants.JSP_URL.APP_UNAVAILABLE.getPath();
                servletContext.getRequestDispatcher(url).forward(req, resp);
                return;
            }
        }

        // ignore if resource request
        final PwmURL pwmURL = new PwmURL(req);
        if (!pwmURL.isResourceURL() && !pwmURL.isWebServiceURL()) {
            // check for valid config
            try {
                if (checkConfigModes(req, resp, servletContext)) {
                    return;
                }
            } catch (PwmUnrecoverableException e) {
                if (e.getError() == PwmError.ERROR_UNKNOWN) {
                    try { LOGGER.error(e.getMessage()); } catch (Exception ignore) { /* noop */ }
                }
                servletRequest.setAttribute(PwmConstants.REQUEST_ATTR_PWM_ERRORINFO, e.getErrorInformation());
                ServletHelper.forwardToErrorPage(req, resp, true);
                return;
            }
        }

        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } catch (Throwable e) {
            final String errorMsg = "uncaught error while processing filter chain: " + e.getMessage() +
                    (e.getCause() == null ? "" : ", cause: " + e.getCause().getMessage());
            if (e instanceof IOException) {
                LOGGER.trace(errorMsg);
            } else {
                LOGGER.error(errorMsg,e);
            }
        }
    }

    @Override
    public void destroy() {
    }

    private static boolean checkConfigModes(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final ServletContext servletContext
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication theManager = ContextManager.getPwmApplication(servletContext);

        if (theManager == null) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,"unable to load PwmApplication instance"));
        }

        final PwmApplication.MODE mode = theManager.getApplicationMode();

        final PwmURL pwmURL = new PwmURL(req);
        if (pwmURL.isResourceURL()) {
            return false;
        }

        if (mode == PwmApplication.MODE.NEW) {
            // check if current request is actually for the config url, if it is, just do nothing.
            if (pwmURL.isCommandServletURL() || pwmURL.isWebServiceURL()) {
                return false;
            }

            if (pwmURL.isConfigGuideURL()) {
                return false;
            } else {
                LOGGER.debug("unable to find a valid configuration, redirecting " + req.getRequestURI() + " to ConfigGuide");
                resp.sendRedirect(req.getContextPath() + "/private/config/" + PwmConstants.URL_SERVLET_CONFIG_GUIDE);
                return true;
            }
        }

        if (mode == PwmApplication.MODE.ERROR) {
            ErrorInformation rootError = ContextManager.getContextManager(req.getSession()).getStartupErrorInformation();
            if (rootError == null) {
                rootError = new ErrorInformation(PwmError.ERROR_APP_UNAVAILABLE, "Application startup failed.");
            }
            final PwmRequest pwmRequest = PwmRequest.forRequest(req, resp);
            pwmRequest.respondWithError(rootError);
            return true;
        }

        return false;
    }

}
