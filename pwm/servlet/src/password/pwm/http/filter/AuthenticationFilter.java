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

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.Validator;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.PwmURL;
import password.pwm.http.servlet.OAuthConsumerServlet;
import password.pwm.i18n.Display;
import password.pwm.i18n.LocaleHelper;
import password.pwm.ldap.PasswordChangeProgressChecker;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.CASAuthenticationHelper;
import password.pwm.util.ServletHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Authentication servlet filter.  This filter wraps all servlet requests and requests direct to *.jsp
 * URLs and provides user authentication services.  Users must provide valid credentials to login.  This
 * filter checks for a Basic Authorization header in the request and will attempt to use that to validate
 * the user, if not, then the user will be passed to a form based login page (LoginServlet;
 *
 * @author Jason D. Rivard
 */
public class AuthenticationFilter extends AbstractPwmFilter {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(AuthenticationFilter.class.getName());

    public void processFilter(
            final PwmRequest pwmRequest,
            final PwmFilterChain chain
    )
            throws IOException, ServletException
    {

        try {
            final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
            final PwmSession pwmSession = pwmRequest.getPwmSession();
            final SessionStateBean ssBean = pwmSession.getSessionStateBean();

            if (pwmApplication.getApplicationMode() == PwmApplication.MODE.NEW) {
                if (pwmRequest.getURL().isConfigGuideURL()) {
                    chain.doFilter();
                    return;
                }
            }

            if (pwmApplication.getApplicationMode() == PwmApplication.MODE.CONFIGURATION) {
                if (pwmRequest.getURL().isConfigManagerURL()) {
                    chain.doFilter();
                    return;
                }
            }

            //user is already authenticated
            if (ssBean.isAuthenticated()) {
                this.processAuthenticatedSession(pwmRequest, chain);
            } else {
                this.processUnAuthenticatedSession(pwmRequest, chain);
            }
        } catch (PwmUnrecoverableException e) {
            LOGGER.error(e.toString());
            throw new ServletException(e.toString());
        }
    }

// -------------------------- OTHER METHODS --------------------------

    private void processAuthenticatedSession(
            final PwmRequest pwmRequest,
            final PwmFilterChain chain
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        // read the basic auth info out of the header (if it exists);
        final BasicAuthInfo basicAuthInfo = BasicAuthInfo.parseAuthHeader(pwmApplication, pwmRequest);

        final BasicAuthInfo originalBasicAuthInfo = pwmSession.getLoginInfoBean().getOriginalBasicAuthInfo();

        //check to make sure basic auth info is same as currently known user in session.
        if (basicAuthInfo != null && originalBasicAuthInfo != null && !(originalBasicAuthInfo.equals(basicAuthInfo))) {
            // if we read here then user is using basic auth, and header has changed since last request
            // this means something is screwy, so log out the session

            // read the current user info for logging
            final UserInfoBean uiBean = pwmSession.getUserInfoBean();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_BAD_SESSION,"basic auth header user '" + basicAuthInfo.getUsername() + "' does not match currently logged in user '" + uiBean.getUserIdentity() + "', session will be logged out");
            LOGGER.info(pwmRequest, errorInformation);

            // log out their user
            pwmSession.unauthenticateUser();

            // send en error to user.
            pwmRequest.respondWithError(errorInformation, true);
            return;
        }

        // check status of oauth expiration
        if (pwmSession.getLoginInfoBean().getOauthExpiration() != null) {
            if (OAuthConsumerServlet.checkOAuthExpiration(pwmRequest)) {
                pwmRequest.respondWithError(new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,"oauth access token has expired"));
                return;
            }
        }

        handleAuthenticationCookie(pwmRequest);

        if (forceRequiredRedirects(pwmRequest)) {
            return;
        }

        // user session is authed, and session and auth header match, so forward request on.
        chain.doFilter();
    }

    private static void handleAuthenticationCookie(final PwmRequest pwmRequest) {
        if (!pwmRequest.isAuthenticated() || pwmRequest.getPwmSession().getLoginInfoBean().getAuthenticationType() != AuthenticationType.AUTHENTICATED) {
            return;
        }

        if (pwmRequest.getPwmSession().getLoginInfoBean().isAuthRecordCookieSet()) {
            return;
        }

        pwmRequest.getPwmSession().getLoginInfoBean().setAuthRecordCookieSet(true);

        final String cookieName = pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_COOKIE_AUTHRECORD_NAME);
        if (cookieName == null || cookieName.isEmpty()) {
            LOGGER.debug(pwmRequest, "skipping auth record cookie set, cookie name parameter is blank");
            return;
        }

        final int cookieAgeSeconds = Integer.parseInt(pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_COOKIE_AUTHRECORD_AGE));
        if (cookieAgeSeconds < 1) {
            LOGGER.debug(pwmRequest, "skipping auth record cookie set, cookie age parameter is less than 1");
            return;
        }

        final Date authTime = pwmRequest.getPwmSession().getLoginInfoBean().getLocalAuthTime();
        final String userGuid = pwmRequest.getPwmSession().getUserInfoBean().getUserGuid();
        final AuthRecord authRecord = new AuthRecord(authTime, userGuid);

        try {
            pwmRequest.getPwmResponse().writeCookie(cookieName, authRecord, cookieAgeSeconds, true, pwmRequest.getContextPath());
            LOGGER.debug(pwmRequest,"wrote auth record cookie to user browser for use during forgotten password");
        } catch (PwmUnrecoverableException e) {
            LOGGER.error(pwmRequest, "error while setting authentication record cookie: " + e.getMessage());
        }
    }

    public static class AuthRecord implements Serializable {
        private Date date;
        private String guid;

        public AuthRecord(Date date, String guid) {
            this.date = date;
            this.guid = guid;
        }

        public Date getDate() {
            return date;
        }

        public String getGuid() {
            return guid;
        }
    }

    private void processUnAuthenticatedSession(
            final PwmRequest pwmRequest,
            final PwmFilterChain chain
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();

        //try to authenticate user with basic auth
        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            final BasicAuthInfo authInfo = BasicAuthInfo.parseAuthHeader(pwmApplication, pwmRequest);
            if (authInfo != null) {
                try {
                    authUserUsingBasicHeader(pwmRequest, authInfo);
                } catch (ChaiUnavailableException e) {
                    StatisticsManager.incrementStat(pwmRequest, Statistic.LDAP_UNAVAILABLE_COUNT);
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,e.getMessage());
                    pwmRequest.respondWithError(errorInformation);
                    return;
                } catch (PwmException e) {
                    pwmRequest.respondWithError(e.getErrorInformation());
                    return;
                }
            }
        }

        // attempt sso header authentication
        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            if (processAuthHeader(pwmRequest)) {
                return;
            }
        }

        // try to authenticate user with CAS
        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            if (processCASAuthentication(pwmRequest)) {
                return;
            }
        }

        // process OAuth
        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            if (processOAuthAuthenticationRequest(pwmRequest)) {
                return;
            }
        }

        //store the original requested url
        final String originalRequestedUrl = figureOriginalRequestUrl(pwmRequest);

        pwmRequest.markPreLoginUrl();

        // handle if authenticated during filter process.
        if (pwmSession.getSessionStateBean().isAuthenticated()) {
            pwmSession.getSessionStateBean().setSessionIdRecycleNeeded(true);
            LOGGER.debug(pwmSession,"session authenticated during request, issuing redirect to originally requested url: " + originalRequestedUrl);
            pwmRequest.sendRedirect(originalRequestedUrl);
            return;
        }

        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.FORCE_BASIC_AUTH)) {
            final String displayMessage = LocaleHelper.getLocalizedMessage(Display.Title_Application, pwmRequest);
            pwmRequest.getPwmResponse().setHeader(PwmConstants.HttpHeader.WWW_Authenticate,"Basic realm=\"" + displayMessage + "\"");
            pwmRequest.getPwmResponse().setStatus(401);
            return;
        }

        if (pwmRequest.getURL().isLoginServlet()) {
            chain.doFilter();
            return;
        }

        //user is not authenticated so forward to LoginPage.
        LOGGER.trace(pwmSession.getLabel(), "user requested resource requiring authentication (" + req.getRequestURI() + "), but is not authenticated; redirecting to LoginServlet");
        pwmRequest.getPwmResponse().forwardToLoginPage();
    }

    public static void authUserUsingBasicHeader(
            final PwmRequest pwmRequest,
            final BasicAuthInfo basicAuthInfo
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        //make sure user session isn't already authenticated
        if (ssBean.isAuthenticated()) {
            return;
        }

        if (basicAuthInfo == null) {
            return;
        }

        //user isn't already authenticated and has an auth header, so try to auth them.
        LOGGER.debug(pwmSession, "attempting to authenticate user using basic auth header (username=" + basicAuthInfo.getUsername() + ")");
        final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(pwmApplication, pwmSession);
        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication, pwmSession.getLabel());
        final UserIdentity userIdentity = userSearchEngine.resolveUsername(basicAuthInfo.getUsername(), null, null);
        sessionAuthenticator.authenticateUser(userIdentity, basicAuthInfo.getPassword());
        pwmSession.getLoginInfoBean().setOriginalBasicAuthInfo(basicAuthInfo);
    }

    static boolean processAuthHeader(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException

    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        final String headerName = pwmApplication.getConfig().readSettingAsString(PwmSetting.SSO_AUTH_HEADER_NAME);
        if (headerName == null || headerName.length() < 1) {
            return false;
        }

        final String headerValue = Validator.sanitizeInputValue(
                pwmRequest.getConfig(),
                pwmRequest.getHttpServletRequest().getHeader(headerName),
                1024
        );

        if (headerValue == null || headerValue.length() < 1) {
            return false;
        }

        LOGGER.debug(pwmRequest, "SSO Authentication header present in request, will search for user value of '" + headerValue + "'");
        try {
            SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(pwmApplication, pwmSession);
            sessionAuthenticator.authUserWithUnknownPassword(headerValue, AuthenticationType.AUTH_WITHOUT_PASSWORD);
            return false;
        } catch (ChaiUnavailableException e) {
            final ErrorInformation errorInformation = new ErrorInformation(
                    PwmError.ERROR_DIRECTORY_UNAVAILABLE,
                    "unable to reach ldap server during SSO auth header authentication attempt: " + e.getMessage());
            LOGGER.error(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation);
            return true;
        } catch (PwmException e) {
            final ErrorInformation errorInformation = new ErrorInformation(
                    e.getError(),
                    "error during SSO auth header authentication attempt: " + e.getMessage());
            LOGGER.error(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation);
            return true;
        }
    }

    static boolean processCASAuthentication(
            final PwmRequest pwmRequest

    )
            throws IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();


        try {
            final String clearPassUrl = pwmApplication.getConfig().readSettingAsString(PwmSetting.CAS_CLEAR_PASS_URL);
            if (clearPassUrl != null && clearPassUrl.length() > 0) {
                LOGGER.trace(pwmSession.getLabel(), "checking for authentication via CAS");
                if (CASAuthenticationHelper.authUserUsingCASClearPass(pwmRequest, clearPassUrl)) {
                    LOGGER.debug(pwmSession, "login via CAS successful");
                    return false;
                }
            }
        } catch (ChaiUnavailableException e) {
            StatisticsManager.incrementStat(pwmRequest, Statistic.LDAP_UNAVAILABLE_COUNT);
            final ErrorInformation errorInformation = new ErrorInformation(
                    PwmError.ERROR_DIRECTORY_UNAVAILABLE,
                    e.getMessage()
            );
            pwmRequest.respondWithError(errorInformation);
            return true;
        } catch (PwmException e) {
            pwmRequest.respondWithError(e.getErrorInformation());
            return true;
        }
        return false;
    }

    static boolean processOAuthAuthenticationRequest(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException

    {
        final Configuration config = pwmRequest.getConfig();
        final OAuthConsumerServlet.Settings settings = OAuthConsumerServlet.Settings.fromConfiguration(config);
        if (!settings.oAuthIsConfigured()) {
            return false;
        }

        final String originalURL = figureOriginalRequestUrl(pwmRequest);

        final String state = OAuthConsumerServlet.makeStateStringForRequest(pwmRequest, originalURL);
        final String redirectUri = OAuthConsumerServlet.figureOauthSelfEndPointUrl(pwmRequest);
        final String code = config.readAppProperty(AppProperty.OAUTH_ID_REQUEST_TYPE);

        final Map<String,String> urlParams = new HashMap<>();
        urlParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_CLIENT_ID),settings.getClientID());
        urlParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_RESPONSE_TYPE),code);
        urlParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_STATE),state);
        urlParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_REDIRECT_URI),redirectUri);

        final String redirectUrl = ServletHelper.appendAndEncodeUrlParameters(settings.getLoginURL(), urlParams);

        LOGGER.trace(pwmRequest, "preparing to start oauth authentication request sequence, set originally requested url: " + originalURL);

        try{
            pwmRequest.getPwmSession().getSessionStateBean().setOauthInProgress(true);
            pwmRequest.sendRedirect(redirectUrl);
            LOGGER.debug(pwmRequest, "redirecting user to oauth id server, url: " + redirectUrl);
        } catch (PwmUnrecoverableException e) {
            final String errorMsg = "unexpected error redirecting user to oauth page: " + e.toString();
            ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            LOGGER.error(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation);
        }

        return true;
    }

    public static boolean forceRequiredRedirects(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmURL pwmURL = pwmRequest.getURL();

        final UserInfoBean uiBean = pwmSession.getUserInfoBean();

        if (pwmURL.isResourceURL() || pwmURL.isConfigManagerURL() || pwmURL.isLogoutURL() || pwmURL.isLoginServlet()) {
            return false;
        }

        // high priority pw change
        if (pwmRequest.getPwmSession().getLoginInfoBean().getAuthenticationType() == AuthenticationType.AUTH_FROM_PUBLIC_MODULE) {
            if (!pwmURL.isChangePasswordURL()) {
                LOGGER.debug(pwmRequest, "user is authenticated via forgotten password mechanism, redirecting to change password servlet");
                pwmRequest.sendRedirect(pwmRequest.getHttpServletRequest().getContextPath() + "/public/" + PwmConstants.URL_SERVLET_CHANGE_PASSWORD);
                return true;
            } else {
                return false;
            }
        }

        // if change password in progress and req is for ChangePassword servlet, then allow request as is
        if (pwmURL.isChangePasswordURL()) {
            final PasswordChangeProgressChecker.ProgressTracker progressTracker = pwmSession.getChangePasswordBean().getChangeProgressTracker();
            if (progressTracker != null && progressTracker.getBeginTime() != null) {
                return false;
            }
        }


        if (uiBean.isRequiresResponseConfig()) {
            if (!pwmURL.isSetupResponsesURL()) {
                LOGGER.debug(pwmRequest, "user is required to setup responses, redirecting to setup responses servlet");
                pwmRequest.sendRedirect(pwmRequest.getHttpServletRequest().getContextPath() + "/private/" + PwmConstants.URL_SERVLET_SETUP_RESPONSES);
                return true;
            } {
                return false;
            }
        }

        if (uiBean.isRequiresOtpConfig() && !pwmSession.getSessionStateBean().isSkippedOtpSetup()) {
            if (!pwmURL.isSetupOtpSecretURL()) {
                LOGGER.debug(pwmRequest, "user is required to setup OTP configuration, redirecting to OTP setup page");
                pwmRequest.sendRedirect(pwmRequest.getHttpServletRequest().getContextPath() + "/private/" + PwmConstants.URL_SERVLET_SETUP_OTP_SECRET);
                return true;
            } else {
                return false;
            }
        }

        if (uiBean.isRequiresUpdateProfile()) {
            if (!pwmURL.isProfileUpdateURL()) {
                LOGGER.debug(pwmRequest, "user is required to update profile, redirecting to profile update servlet");
                pwmRequest.sendRedirect(pwmRequest.getHttpServletRequest().getContextPath() + "/private/" + PwmConstants.URL_SERVLET_UPDATE_PROFILE);
                return true;
            } else {
                return false;
            }
        }

        if (uiBean.isRequiresNewPassword() && !pwmSession.getSessionStateBean().isSkippedRequireNewPassword()) {
            if (!pwmURL.isChangePasswordURL()) {
                LOGGER.debug(pwmRequest, "user password requires changing, redirecting to change password servlet");
                pwmRequest.sendRedirect(pwmRequest.getHttpServletRequest().getContextPath() + "/public/" + PwmConstants.URL_SERVLET_CHANGE_PASSWORD);
                return true;
            } else {
                return false;
            }
        }

        return false;
    }

    private static String figureOriginalRequestUrl(final PwmRequest pwmRequest) {
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();
        final String queryString = req.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            return req.getRequestURI() + '?' + queryString;
        } else {
            return req.getRequestURI();
        }
    }
}

