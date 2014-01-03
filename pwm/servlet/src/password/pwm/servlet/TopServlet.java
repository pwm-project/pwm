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

package password.pwm.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.*;
import password.pwm.bean.SessionStateBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.FormMap;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.util.Set;

/**
 * An abstract parent of all PWM servlets.  This is the parent class of most, if not all, PWM
 * servlets.
 *
 * @author Jason D. Rivard
 */
public abstract class TopServlet extends HttpServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(TopServlet.class);

    // -------------------------- OTHER METHODS --------------------------
    public void doGet(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException {
        this.handleRequest(req, resp);
    }

    public void doPost(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException {
        this.handleRequest(req, resp);
    }

    private void handleRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException
    {
        try {
            final PwmSession pwmSession = PwmSession.getPwmSession(req);
            setLastParameters(req,pwmSession.getSessionStateBean());
        } catch (Exception e2) {
            //noop
        }


        try {
            this.processRequest(req, resp);
        } catch (Exception e) {
            final PwmApplication pwmApplication;
            try {
                pwmApplication = ContextManager.getPwmApplication(this.getServletContext());
            } catch (Exception e2) {
                try {
                    LOGGER.fatal("exception occurred, but exception handler unable to load Application instance; error=" + e.getMessage(),e);
                } catch (Exception e3) {
                    e3.printStackTrace();
                }
                throw new ServletException(e);
            }

            final PwmSession pwmSession;
            try {
                pwmSession = PwmSession.getPwmSession(req);
            } catch (Exception e2) {
                try {
                    LOGGER.fatal("exception occurred, but exception handler unable to load Session wrapper instance; error=" + e.getMessage(),e);
                } catch (Exception e3) {
                    e3.printStackTrace();
                }
                throw new ServletException(e);
            }

            final PwmUnrecoverableException pue = convertToPwmUnrecoverableException(e);

            if (processUnrecoverableException(req,resp,pwmApplication,pwmSession,pue)) {
                return;
            }

            outputUnrecoverableException(req,resp,pwmApplication,pwmSession,pue);
        }
    }

    private PwmUnrecoverableException convertToPwmUnrecoverableException(
            final Exception e
    )
    {
        if (e instanceof PwmUnrecoverableException) {
            return (PwmUnrecoverableException)e;
        }

        if (e instanceof PwmException) {
            return new PwmUnrecoverableException(((PwmException)e).getErrorInformation());
        }

        if (e instanceof ChaiUnavailableException) {
            final String errorMsg = "unable to contact ldap directory: " + e.getMessage();
            return new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,errorMsg));
        }

        final StringWriter errorStack = new StringWriter();
        e.printStackTrace(new PrintWriter(errorStack));
        final String errorMsg ="unexpected error processing request: " + e.getMessage()  + "\n" + errorStack.toString();
        return new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg));
    }


    private boolean processUnrecoverableException(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final PwmUnrecoverableException e
    )
            throws IOException
    {
        switch (e.getError()) {
            case ERROR_DIRECTORY_UNAVAILABLE:
                LOGGER.fatal(pwmSession, e.getErrorInformation().toDebugStr());
                try {
                    pwmApplication.getStatisticsManager().incrementValue(Statistic.LDAP_UNAVAILABLE_COUNT);
                    pwmApplication.setLastLdapFailure(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage()));
                } catch (Throwable e1) {
                    //noop
                }
                break;

            case ERROR_UNKNOWN:
                LOGGER.fatal(e.getErrorInformation().toDebugStr());
                try { // try to update stats
                    if (pwmSession != null) {
                        pwmApplication.getStatisticsManager().incrementValue(Statistic.PWM_UNKNOWN_ERRORS);
                    }
                } catch (Throwable e1) {
                    //noop
                }
                break;

            case ERROR_PASSWORD_REQUIRED:
                LOGGER.warn("attempt to access functionality requiring password authentication, but password not yet supplied by actor, forwarding to password Login page");
                //store the original requested url
                final String originalRequestedUrl = req.getRequestURI() + (req.getQueryString() != null ? ('?' + req.getQueryString()) : "");
                if (pwmSession != null && pwmSession.getSessionStateBean() != null) {
                    pwmSession.getSessionStateBean().setOriginalRequestURL(originalRequestedUrl);
                }

                LOGGER.debug(pwmSession, "user is authenticated without a password, redirecting to login page");
                resp.sendRedirect(req.getContextPath() + "/private/" + PwmConstants.URL_SERVLET_LOGIN);
                return true;

            default:
                LOGGER.error(pwmSession, "error during page generation: " + e.getMessage());
        }
        return false;
    }

    private void outputUnrecoverableException(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final PwmUnrecoverableException e
    )
            throws IOException, ServletException
    {

        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        ssBean.setSessionError(e.getErrorInformation());
        final String acceptHeader = req.getHeader("Accept");
        if (acceptHeader.contains("application/json")) {
            final RestResultBean restResultBean = RestResultBean.fromError(e.getErrorInformation());
            ServletHelper.outputJsonResult(resp,restResultBean);
        } else {
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
        }
    }


    private void setLastParameters(final HttpServletRequest req, final SessionStateBean ssBean) throws PwmUnrecoverableException {
        final Set keyNames = req.getParameterMap().keySet();
        final FormMap newParamProperty = new FormMap();

        for (final Object name : keyNames) {
            final String value = Validator.readStringFromRequest(req, (String) name);
            newParamProperty.put((String) name, value);
        }

        ssBean.setLastParameterValues(newParamProperty);
    }

    protected abstract void processRequest(
            HttpServletRequest req,
            HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException;


    static boolean convertURLtokenCommand(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws IOException
    {
        final String uri = req.getRequestURI();
        if (uri == null || uri.length() < 1) {
            return false;
        }
        final String servletPath = req.getServletPath();
        if (!uri.contains(servletPath)) {
            LOGGER.error("unexpected uri handler, uri '" + uri + "' does not contain servlet path '" + servletPath + "'");
            return false;
        }

        String aftPath = uri.substring(uri.indexOf(servletPath) + servletPath.length(),uri.length());
        if (aftPath.startsWith("/")) {
            aftPath = aftPath.substring(1,aftPath.length());
        }

        if (aftPath.contains("?")) {
            aftPath = aftPath.substring(0,aftPath.indexOf("?"));
        }

        if (aftPath.contains("&")) {
            aftPath = aftPath.substring(0,aftPath.indexOf("?"));
        }

        if (aftPath.length() <= 1) {
            return false;
        }

        final StringBuilder redirectURL = new StringBuilder();
        redirectURL.append(req.getContextPath());
        redirectURL.append(req.getServletPath());
        redirectURL.append("?");
        redirectURL.append(PwmConstants.PARAM_ACTION_REQUEST).append("=enterCode");
        redirectURL.append("&");
        redirectURL.append(PwmConstants.PARAM_TOKEN).append("=").append(URLEncoder.encode(aftPath,"UTF8"));
        redirectURL.append("&");
        redirectURL.append(PwmConstants.PARAM_FORM_ID).append("=").append(Helper.buildPwmFormID(pwmSession.getSessionStateBean()));

        LOGGER.debug(pwmSession, "detected long servlet url, redirecting user to " + redirectURL);
        resp.sendRedirect(redirectURL.toString());
        return true;
    }
}

