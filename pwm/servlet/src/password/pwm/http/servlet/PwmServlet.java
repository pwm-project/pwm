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

package password.pwm.http.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.Validator;
import password.pwm.bean.SessionStateBean;
import password.pwm.error.*;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

public abstract class PwmServlet extends HttpServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmServlet.class);

    // -------------------------- OTHER METHODS --------------------------
    public void doGet(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException
    {
        this.handleRequest(req, resp, HttpMethod.GET);
    }

    public void doPost(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException
    {
        this.handleRequest(req, resp, HttpMethod.POST);
    }

    private void handleRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final HttpMethod method
    )
            throws ServletException, IOException
    {
        try {
            final PwmRequest pwmRequest = PwmRequest.forRequest(req, resp);

            // check for duplicate form submit.
            try {
                Validator.validatePwmRequestCounter(req);
            } catch (PwmOperationalException e) {
                if (e.getError() == PwmError.ERROR_INCORRECT_REQUEST_SEQUENCE) {
                    final ErrorInformation errorInformation = e.getErrorInformation();
                    final PwmSession pwmSession = PwmSession.getPwmSession(req);
                    LOGGER.error(pwmSession, errorInformation.toDebugStr());
                    pwmRequest.respondWithError(errorInformation);
                    return;
                }
                throw e;
            }


            // check for incorrect method type.
            final ProcessAction processAction = readProcessAction(pwmRequest);
            if (processAction != null) {
                if (!processAction.permittedMethods().contains(method)) {
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,
                            "incorrect request method " + method.toString());
                    LOGGER.error(pwmRequest.getPwmSession(), errorInformation.toDebugStr());
                    pwmRequest.respondWithError(errorInformation, false);
                    return;
                }
            }

            if (method == HttpMethod.POST) {
                Validator.validatePwmFormID(req);
            }

            this.processAction(pwmRequest);
        } catch (Exception e) {
            final PwmRequest pwmRequest;
            try {
                pwmRequest = PwmRequest.forRequest(req, resp);
            } catch (Exception e2) {
                try {
                    LOGGER.fatal(
                            "exception occurred, but exception handler unable to load request instance; error=" + e.getMessage(),
                            e);
                } catch (Exception e3) {
                    e3.printStackTrace();
                }
                throw new ServletException(e);
            }

            final PwmApplication pwmApplication;
            try {
                pwmApplication = ContextManager.getPwmApplication(this.getServletContext());
            } catch (Exception e2) {
                try {
                    LOGGER.fatal(
                            "exception occurred, but exception handler unable to load Application instance; error=" + e.getMessage(),
                            e);
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
                    LOGGER.fatal(
                            "exception occurred, but exception handler unable to load Session wrapper instance; error=" + e.getMessage(),
                            e);
                } catch (Exception e3) {
                    e3.printStackTrace();
                }
                throw new ServletException(e);
            }

            final PwmUnrecoverableException pue = convertToPwmUnrecoverableException(e);

            if (processUnrecoverableException(req, resp, pwmApplication, pwmSession, pue)) {
                return;
            }

            outputUnrecoverableException(pwmRequest, pue);
        }
    }

    private PwmUnrecoverableException convertToPwmUnrecoverableException(
            final Exception e
    )
    {
        if (e instanceof PwmUnrecoverableException) {
            return (PwmUnrecoverableException) e;
        }

        if (e instanceof PwmException) {
            return new PwmUnrecoverableException(((PwmException) e).getErrorInformation());
        }

        if (e instanceof ChaiUnavailableException) {
            final String errorMsg = "unable to contact ldap directory: " + e.getMessage();
            return new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE, errorMsg));
        }

        final StringWriter errorStack = new StringWriter();
        e.printStackTrace(new PrintWriter(errorStack));
        final String errorMsg = "unexpected error processing request: " + e.getMessage() + "\n" + errorStack.toString();
        return new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg));
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
                LOGGER.warn(
                        "attempt to access functionality requiring password authentication, but password not yet supplied by actor, forwarding to password Login page");
                //store the original requested url
                final String originalRequestedUrl = req.getRequestURI() + (req.getQueryString() != null ? ('?' + req.getQueryString()) : "");
                if (pwmSession != null && pwmSession.getSessionStateBean() != null) {
                    pwmSession.getSessionStateBean().setOriginalRequestURL(originalRequestedUrl);
                }

                LOGGER.debug(pwmSession, "user is authenticated without a password, redirecting to login page");
                resp.sendRedirect(req.getContextPath() + "/private/" + PwmConstants.URL_SERVLET_LOGIN);
                return true;

            default:
                String errorMsg = "error during page generation: " + e.getMessage();
                try {
                    errorMsg = ServletHelper.debugHttpRequest(pwmApplication, req, errorMsg);
                } catch (Exception e2) { /* noop */ }
                LOGGER.error(pwmSession, errorMsg);
        }
        return false;
    }

    private void outputUnrecoverableException(
            final PwmRequest pwmRequest,
            final PwmUnrecoverableException e
    )
            throws IOException, ServletException
    {

        final SessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();
        ssBean.setSessionError(e.getErrorInformation());
        final String acceptHeader = pwmRequest.getHttpServletRequest().getHeader("Accept");
        if (acceptHeader.contains("application/json")) {
            final RestResultBean restResultBean = RestResultBean.fromError(e.getErrorInformation(), pwmRequest);
            pwmRequest.outputJsonResult(restResultBean);
        } else {
            pwmRequest.respondWithError(e.getErrorInformation());
        }
    }



    protected abstract void processAction(PwmRequest request)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException;

    protected abstract ProcessAction readProcessAction(PwmRequest request)
            throws PwmUnrecoverableException;

    public interface ProcessAction {
        public Collection<HttpMethod> permittedMethods();
    }

    public enum HttpMethod {
        POST,
        GET,

    }

    public static final Collection<HttpMethod> GET_AND_POST_METHODS;

    static {
        final HashSet<HttpMethod> methods = new HashSet<>();
        methods.add(HttpMethod.GET);
        methods.add(HttpMethod.POST);
        GET_AND_POST_METHODS = Collections.unmodifiableSet(methods);
    }

    static boolean convertURLtokenCommand(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws IOException
    {
        return TopServlet.convertURLtokenCommand(req, resp, pwmApplication, pwmSession);
    }
}