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
import password.pwm.util.*;
import password.pwm.util.operations.UserAuthenticator;
import password.pwm.util.stats.Statistic;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

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
            return;
        }

        try {
            if (forceRequiredRedirects(req,resp,ContextManager.getPwmApplication(req),pwmSession)) {
                return;
            }
        } catch (ChaiUnavailableException e) {
            LOGGER.error("unexpected ldap error when checking for user redirects: " + e.getMessage());
        }



        // user session is authed, and session and auth header match, so forward request on.
        chain.doFilter(req, resp);
    }

    private void processUnAuthenticatedSession(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final FilterChain chain
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        // attempt external methods;
        if (processExternalAuthMethods(pwmApplication,pwmSession,req,resp)) {
            return;
        }

        //try to authenticate user with basic auth
        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            final BasicAuthInfo authInfo = BasicAuthInfo.parseAuthHeader(req);
            if (authInfo != null) {
                try {
                    authUserUsingBasicHeader(req, authInfo);
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
        }

        // attempt sso header authentication
        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            if (processAuthHeader(pwmApplication, pwmSession, req, resp)) {
                return;
            }
        }

        // try to authenticate user with CAS
        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            if (processCASAuthentication(pwmApplication, pwmSession, req, resp)) {
                return;
            }
        }

        //store the original requested url
        final String originalRequestedUrl = req.getRequestURI() + (req.getQueryString() != null ? ('?' + req.getQueryString()) : "");
        if (ssBean.getOriginalRequestURL() == null) {
            ssBean.setOriginalRequestURL(originalRequestedUrl);
        }

        // handle if authenticated during filter process.
        if (pwmSession.getSessionStateBean().isAuthenticated()) {
            ServletHelper.recycleSessions(pwmSession,req);
            LOGGER.debug(pwmSession,"session authenticated during request, issuing redirect to originally requested url: " + originalRequestedUrl);
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(originalRequestedUrl,req,resp));
            return;
        }

        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.FORCE_BASIC_AUTH)) {
            final String displayMessage = Display.getLocalizedMessage(ssBean.getLocale(),"Title_Application",pwmApplication.getConfig());
            resp.setHeader("WWW-Authenticate", "Basic realm=\"" + displayMessage + "\"");
            resp.setStatus(401);
            return;
        }

        if (PwmServletURLHelper.isLoginServlet(req)) {
            chain.doFilter(req, resp);
            return;
        }

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

    private static boolean processExternalAuthMethods(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest req,
            final HttpServletResponse resp

    )
            throws IOException, ServletException
    {

        final List<String> externalWebAuthMethods = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.EXTERNAL_WEB_AUTH_METHODS);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        for (final String classNameString : externalWebAuthMethods) {
            if (classNameString != null && classNameString.length() > 0) {
                try {
                    // load up the class and get an instance.
                    final Class<?> theClass = Class.forName(classNameString);
                    final ExternalWebAuthMethod eWebAuthMethod = (ExternalWebAuthMethod) theClass.newInstance();
                    eWebAuthMethod.authenticate(req, resp, pwmSession);
                }
                catch(ClassNotFoundException classNotFoundEx)
                {
                    LOGGER.error("Error loading external interface login class" + classNotFoundEx.getMessage());
                    ssBean.setSessionError(PwmError.ERROR_UNKNOWN.toInfo());
                    ServletHelper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
                    return true;
                }
                catch(ClassCastException classCastEx)
                {
                    classCastEx.printStackTrace();
                    LOGGER.error("Error loading external interface login class" + classCastEx.getMessage());
                    ssBean.setSessionError(PwmError.ERROR_UNKNOWN.toInfo());
                    ServletHelper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
                    return true;
                }
                catch(IllegalAccessException iLLex)
                {
                    LOGGER.error("Error loading external interface login class" + iLLex.getMessage());
                    ssBean.setSessionError(PwmError.ERROR_UNKNOWN.toInfo());
                    ServletHelper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
                    return true;
                } catch (InstantiationException instEx)
                {
                    LOGGER.error("Error loading external interface login class" + instEx.getMessage());
                    ssBean.setSessionError(PwmError.ERROR_UNKNOWN.toInfo());
                    ServletHelper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
                    return true;
                }
            }
        }
        return false;
    }

    final static boolean processAuthHeader(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException

    {
        final String headerName = pwmApplication.getConfig().readSettingAsString(PwmSetting.SSO_AUTH_HEADER_NAME);
        if (req == null || headerName == null || headerName.length() < 1) {
            return false;
        }

        final String headerValue = Validator.sanatizeInputValue(pwmApplication.getConfig(), req.getHeader(headerName), 1024);
        if (headerValue == null || headerValue.length() < 1) {
            return false;
        }
        LOGGER.debug(pwmSession, "SSO Authentication header present in request, will search for user value of '" + headerValue + "'");
        try {
            UserAuthenticator.authUserWithUnknownPassword(
                    headerValue,
                    pwmSession,
                    pwmApplication,
                    req.isSecure(),
                    UserInfoBean.AuthenticationType.AUTH_WITHOUT_PASSWORD
            );
            return false;
        } catch (ChaiUnavailableException e) {
            LOGGER.error(pwmSession, "unable to reach ldap server during SSO auth header authentication attempt: " + e.getMessage());
            pwmSession.getSessionStateBean().setSessionError(PwmError.ERROR_UNKNOWN.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
            return true;
        } catch (PwmException e) {
            LOGGER.error(pwmSession, "error during SSO auth header authentication attempt: " + e.getMessage());
            pwmSession.getSessionStateBean().setSessionError(PwmError.ERROR_UNKNOWN.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
            return true;
        }
    }

    final static boolean processCASAuthentication(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest req,
            final HttpServletResponse resp

    )
            throws IOException, ServletException

    {
        try {
            final String clearPassUrl = pwmApplication.getConfig().readSettingAsString(PwmSetting.CAS_CLEAR_PASS_URL);
            if (clearPassUrl != null && clearPassUrl.length() > 0) {
                LOGGER.trace(pwmSession, "checking for authentication via CAS");
                if (CASAuthenticationHelper.authUserUsingCASClearPass(req,clearPassUrl)) {
                    LOGGER.debug(pwmSession, "login via CAS successful");
                    return false;
                }
            }
        } catch (ChaiUnavailableException e) {
            pwmApplication.getStatisticsManager().incrementValue(Statistic.LDAP_UNAVAILABLE_COUNT);
            pwmApplication.setLastLdapFailure(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage()));
            pwmSession.getSessionStateBean().setSessionError(PwmError.ERROR_DIRECTORY_UNAVAILABLE.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
            return true;
        } catch (PwmException e) {
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            ServletHelper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
            return true;
        }
        return false;
    }

    public static boolean forceRequiredRedirects(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException
    {
        if (PwmServletURLHelper.isResourceURL(req) || PwmServletURLHelper.isConfigManagerURL(req) || PwmServletURLHelper.isLogoutURL(req) || PwmServletURLHelper.isLoginServlet(req)) {
            return false;
        }

        switch (pwmSession.getUserInfoBean().getAuthenticationType()) {
            case AUTH_FROM_FORGOTTEN:
                if (!PwmServletURLHelper.isChangePasswordURL(req)) {
                    LOGGER.debug(pwmSession, "user is authenticated via forgotten password mechanism, redirecting to change password servlet");
                    resp.sendRedirect(req.getContextPath() + "/private/" + PwmConstants.URL_SERVLET_CHANGE_PASSWORD);
                    return true;
                }
                break;

            /*
            case AUTH_WITHOUT_PASSWORD:
                if (!PwmServletURLHelper.isLoginServlet(req) && !PwmServletURLHelper.isCommandServletURL(req) && !PwmServletURLHelper.isMenuURL(req)) {
                    //store the original requested url
                    final String originalRequestedUrl = req.getRequestURI() + (req.getQueryString() != null ? ('?' + req.getQueryString()) : "");
                    pwmSession.getSessionStateBean().setOriginalRequestURL(originalRequestedUrl);

                    LOGGER.debug(pwmSession, "user is authenticated without a password, redirecting to login page");
                    resp.sendRedirect(req.getContextPath() + "/private/" + PwmConstants.URL_SERVLET_LOGIN);
                    return true;
                }
                break;
                */
        }

        // high priority password changes.
        if (Permission.checkPermission(Permission.CHANGE_PASSWORD, pwmSession, pwmApplication)) {
            if (pwmSession.getUserInfoBean().getAuthenticationType() == UserInfoBean.AuthenticationType.AUTH_FROM_FORGOTTEN) {
                if (!PwmServletURLHelper.isChangePasswordURL(req)) {
                    LOGGER.debug(pwmSession, "user password is unknown to application, redirecting to change password servlet");
                    resp.sendRedirect(req.getContextPath() + "/private/" + PwmConstants.URL_SERVLET_CHANGE_PASSWORD);
                    return true;
                } else {
                    return false;
                }
            }
        }

        if (!PwmServletURLHelper.isSetupResponsesURL(req)) {
            if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.CHALLENGE_ENABLE)) {
                if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.CHALLENGE_FORCE_SETUP)) {
                    if (Permission.checkPermission(Permission.SETUP_RESPONSE, pwmSession, pwmApplication)) {
                        if (pwmSession.getUserInfoBean().isRequiresResponseConfig()) {
                            LOGGER.debug(pwmSession, "user is required to setup responses, redirecting to setup responses servlet");
                            resp.sendRedirect(req.getContextPath() + "/private/" + PwmConstants.URL_SERVLET_SETUP_RESPONSES);
                            return true;
                        }
                    }
                }
            }
        } else {
            return false;
        }

        if (!PwmServletURLHelper.isProfileUpdateURL(req)) {
            if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_ENABLE)) {
                if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_FORCE_SETUP)) {
                    if (Permission.checkPermission(Permission.PROFILE_UPDATE, pwmSession, pwmApplication)) {
                        if (pwmSession.getUserInfoBean().isRequiresUpdateProfile()) {
                            LOGGER.debug(pwmSession, "user is required to update profile, redirecting to profile update servlet");
                            resp.sendRedirect(req.getContextPath() + "/private/" + PwmConstants.URL_SERVLET_UPDATE_PROFILE);
                            return true;
                        }
                    }
                }
            }
        } else {
            return false;
        }

        if (!PwmServletURLHelper.isChangePasswordURL(req)) {
            if (Permission.checkPermission(Permission.CHANGE_PASSWORD, pwmSession, pwmApplication)) {
                boolean doRedirect = false;
                if (pwmSession.getUserInfoBean().getPasswordState().isExpired()) {
                    LOGGER.debug(pwmSession, "user password is expired, redirecting to change password servlet");
                    doRedirect = true;
                } else if (pwmSession.getUserInfoBean().getPasswordState().isPreExpired() ) {
                    LOGGER.debug(pwmSession, "user password is pre-expired, redirecting to change password servlet ");
                    doRedirect = true;
                } else if (pwmSession.getUserInfoBean().getPasswordState().isViolatesPolicy() ) {
                    LOGGER.debug(pwmSession, "user password violates policy, redirecting to change password servlet ");
                    doRedirect = true;
                } else if (pwmSession.getUserInfoBean().isRequiresNewPassword()) {
                    LOGGER.debug(pwmSession, "user password requires changing due to a previous operation, redirecting to change password servlet");
                    doRedirect = true;
                }

                if (doRedirect) {
                    resp.sendRedirect(req.getContextPath() + "/public/" + PwmConstants.URL_SERVLET_CHANGE_PASSWORD);
                    return true;
                }
            }
        } else {
            return false;
        }

        return false;
    }

}

