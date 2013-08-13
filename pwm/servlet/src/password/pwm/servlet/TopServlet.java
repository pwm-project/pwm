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
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.FormMap;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Properties;
import java.util.Set;

/**
 * An abstract parent of all PWM servlets.  This is the parent class of most, if not all, PWM
 * servlets.
 *
 * @author Jason D. Rivard
 */
public abstract class TopServlet extends HttpServlet {
// ------------------------------ FIELDS ------------------------------

    /**
     * Logger used by this class *
     */
    private static final PwmLogger LOGGER = PwmLogger.getLogger(TopServlet.class);

    // -------------------------- OTHER METHODS --------------------------
    public void doGet(
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
            throws ServletException, IOException {

        PwmSession pwmSession = null;
        PwmApplication pwmApplication = null;
        SessionStateBean ssBean = null;
        try {
            pwmApplication = ContextManager.getPwmApplication(req.getSession());
            pwmSession = PwmSession.getPwmSession(req);
            ssBean = pwmSession.getSessionStateBean();
            setLastParameters(req,ssBean);
            this.processRequest(req, resp);
        } catch (ChaiUnavailableException e) {
            try {
                pwmApplication.getStatisticsManager().incrementValue(Statistic.LDAP_UNAVAILABLE_COUNT);
                pwmApplication.setLastLdapFailure(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,e.getMessage()));
                ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,e.getMessage()));
            } catch (Throwable e1) {
                // oh well
            }
            LOGGER.fatal(pwmSession, "unable to contact ldap directory: " + e.getMessage());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
        } catch (PwmUnrecoverableException e) {
            switch (e.getError()) {
                case ERROR_UNKNOWN:
                    LOGGER.warn(pwmSession, "unexpected pwm error during page generation: " + e.getMessage(), e);
                    try { // try to update stats
                        if (pwmSession != null) {
                            pwmApplication.getStatisticsManager().incrementValue(Statistic.PWM_UNKNOWN_ERRORS);
                        }
                    } catch (Throwable e1) {
                        // oh well
                    }
                    break;

                case ERROR_PASSWORD_REQUIRED:
                    LOGGER.warn("attempt to access functionality requiring password authentication, but password not yet supplied by actor, forwarding to password Login page");
                    //store the original requested url
                    final String originalRequestedUrl = req.getRequestURI() + (req.getQueryString() != null ? ('?' + req.getQueryString()) : "");
                    pwmSession.getSessionStateBean().setOriginalRequestURL(originalRequestedUrl);

                    LOGGER.debug(pwmSession, "user is authenticated without a password, redirecting to login page");
                    resp.sendRedirect(req.getContextPath() + "/private/" + PwmConstants.URL_SERVLET_LOGIN);
                    return;

                default:
                    LOGGER.error(pwmSession, "pwm error during page generation: " + e.getMessage());
            }

            if (ssBean != null) {
                ssBean.setSessionError(e.getErrorInformation());
            }
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
        } catch (Exception e) {
            LOGGER.warn(pwmSession, "unexpected error during page generation: " + e.getMessage(), e);
            if (ssBean != null) {
                ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNKNOWN,e.getMessage()));
            }
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

    public void doPost(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException {
        this.handleRequest(req, resp);
    }

    static boolean convertURLtokenCommand(final HttpServletRequest req, final HttpServletResponse resp, final PwmSession pwmSession)
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

