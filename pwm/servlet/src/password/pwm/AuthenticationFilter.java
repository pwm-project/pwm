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

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.i18n.Display;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.CASAuthenticationHelper;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.UserAuthenticator;
import password.pwm.util.stats.Statistic;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Authentication servlet filter.  This filter wraps all servlet requests and requests direct to *.jsp
 * URLs and provides user authentication services.  Users must provide valid credentials to login.  This
 * filter checks for a Basic Authorization header in the request and will attempt to use that to validate
 * the user, if not, then the user will be passed to a form based login page (LoginServlet;
 *
 * @author Jason D. Rivard
 */
public class AuthenticationFilter implements Filter {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(AuthenticationFilter.class.getName());

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Filter ---------------------

    public void init(final FilterConfig filterConfig)
            throws ServletException {
    }

    public void doFilter(
            final ServletRequest servletRequest,
            final ServletResponse servletResponse,
            final FilterChain chain
    )
            throws IOException, ServletException {
        final HttpServletRequest req = (HttpServletRequest) servletRequest;
        final HttpServletResponse resp = (HttpServletResponse) servletResponse;

        try {
            final PwmSession pwmSession = PwmSession.getPwmSession(req);
            final SessionStateBean ssBean = pwmSession.getSessionStateBean();

            //user is already authenticated
            if (ssBean.isAuthenticated()) {
                this.processAuthenticatedSession(req, resp, chain);
            } else {
                this.processUnAuthenticatedSession(req, resp, chain);
            }
        } catch (PwmUnrecoverableException e) {
            LOGGER.error(e.toString());
            throw new ServletException(e.toString());
        }
    }

    /**
     * Method requird to implement filterservlet
     */
    public void destroy() {
    }

// -------------------------- OTHER METHODS --------------------------

    private void processAuthenticatedSession(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final FilterChain chain
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        SessionStateBean ssBean = pwmSession.getSessionStateBean();

        // get the basic auth info out of the header (if it exists);
        final BasicAuthInfo basicAuthInfo = BasicAuthInfo.parseAuthHeader(req);

        final BasicAuthInfo originalBasicAuthInfo = ssBean.getOriginalBasicAuthInfo();

        //check to make sure basic auth info is same as currently known user in session.
        if (basicAuthInfo != null && originalBasicAuthInfo != null && !(originalBasicAuthInfo.equals(basicAuthInfo))) {
            // if we get here then user is using basic auth, and header has changed since last request
            // this means something is screwy, so log out the session

            // get the current user info for logging
            final UserInfoBean uiBean = pwmSession.getUserInfoBean();
            LOGGER.info(pwmSession, "user info for " + uiBean.getUserDN() + " does not match current basic auth header, un-authenticating user.");

            // log out their user
            pwmSession.unauthenticateUser();

            // update the ssBean variable with the new sessionStateBean.
            ssBean = pwmSession.getSessionStateBean();

            // send en error to user.
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_BAD_SESSION,"basic auth header user '" + basicAuthInfo.getUsername() + "' does not match currently logged in user '" + uiBean.getUserDN() + "', session will be logged out"));
            ServletHelper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
        } else {
            // user session is authed, and session and auth header match, so forward request on.
            chain.doFilter(req, resp);
        }
    }

    private void processUnAuthenticatedSession(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final FilterChain chain
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        final String loginServletURL = req.getContextPath() + "/private/" + PwmConstants.URL_SERVLET_LOGIN;
        String requestedURL = req.getRequestURI();

        if (requestedURL.contains(";")) {
            requestedURL = requestedURL.substring(0, requestedURL.indexOf(";"));
        }

        // check if current request is actually for the login servlet url, if it is, just do nothing.
        if (requestedURL.equals(loginServletURL)) {
            LOGGER.trace(pwmSession, "permitting unauthenticated request of login page");
            chain.doFilter(req, resp);
            return;
        }

        //try to authenticate user with basic auth
        final BasicAuthInfo authInfo = BasicAuthInfo.parseAuthHeader(req);
        if (authInfo != null) {
            try {
                authUserUsingBasicHeader(req, authInfo);
                chain.doFilter(req, resp);
                return;
            } catch (ChaiUnavailableException e) {
                pwmApplication.getStatisticsManager().incrementValue(Statistic.LDAP_UNAVAILABLE_COUNT);
                pwmApplication.setLastLdapFailure(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage()));
                ssBean.setSessionError(PwmError.ERROR_DIRECTORY_UNAVAILABLE.toInfo());
                ServletHelper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
                return;
            } catch (PwmException e) {
                ssBean.setSessionError(e.getErrorInformation());
                ServletHelper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
                return;
            }
        }

        // try to authenticate user with CAS
        try {
            final String clearPassUrl = pwmApplication.getConfig().readSettingAsString(PwmSetting.CAS_CLEAR_PASS_URL);
            if (clearPassUrl != null && clearPassUrl.length() > 0) {
                LOGGER.trace(pwmSession, "checking for authentication via CAS");
                if (CASAuthenticationHelper.authUserUsingCASClearPass(req,clearPassUrl)) {
                    LOGGER.debug(pwmSession, "login via CAS successful");
                    chain.doFilter(req,resp);
                    return;
                }
            }
        } catch (ChaiUnavailableException e) {
            pwmApplication.getStatisticsManager().incrementValue(Statistic.LDAP_UNAVAILABLE_COUNT);
            pwmApplication.setLastLdapFailure(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage()));
            ssBean.setSessionError(PwmError.ERROR_DIRECTORY_UNAVAILABLE.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
            return;
        } catch (PwmException e) {
            ssBean.setSessionError(e.getErrorInformation());
            ServletHelper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
            return;
        }

        // user is not logged in, and should be (otherwise this filter would not be invoked).
        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.FORCE_BASIC_AUTH)) {
            final String displayMessage = Display.getLocalizedMessage(ssBean.getLocale(),"Title_Application",pwmApplication.getConfig());

            resp.setHeader("WWW-Authenticate", "Basic realm=\"" + displayMessage + "\"");
            resp.setStatus(401);
            return;
        }

        // therefore, redirect to logged in page.

        //store the original requested url
        final String urlToStore = req.getRequestURI() + (req.getQueryString() != null ? ('?' + req.getQueryString()) : "");
        ssBean.setOriginalRequestURL(urlToStore);

        //user is not authenticated so forward to LoginPage.
        LOGGER.trace(pwmSession, "user requested resource requiring authentication (" + req.getRequestURI() + "), but is not authenticated; redirecting to LoginServlet");
        ServletHelper.forwardToLoginPage(req, resp);
    }

    public static void authUserUsingBasicHeader(
            final HttpServletRequest req,
            final BasicAuthInfo basicAuthInfo
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        //make sure user session isn't already authenticated
        if (ssBean.isAuthenticated()) {
            return;
        }

        if (basicAuthInfo == null) {
            return;
        }

        //user isn't already authed and has an auth header, so try to auth them.
        LOGGER.debug(pwmSession, "attempting to authenticate user using basic auth header (username=" + basicAuthInfo.getUsername() + ")");
        UserAuthenticator.authenticateUser(basicAuthInfo.getUsername(), basicAuthInfo.getPassword(), null, pwmSession, pwmApplication, req.isSecure());

        pwmSession.getSessionStateBean().setOriginalBasicAuthInfo(basicAuthInfo);
    }

}

