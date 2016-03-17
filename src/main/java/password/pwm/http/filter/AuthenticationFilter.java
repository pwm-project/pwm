/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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
import password.pwm.*;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.http.PwmHttpResponseWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.PwmURL;
import password.pwm.http.bean.ChangePasswordBean;
import password.pwm.http.servlet.LoginServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.http.servlet.oauth.OAuthConsumerServlet;
import password.pwm.http.servlet.oauth.OAuthSettings;
import password.pwm.i18n.Display;
import password.pwm.ldap.PasswordChangeProgressChecker;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.LocaleHelper;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

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
            final PwmApplicationMode mode,
            final PwmRequest pwmRequest,
            final PwmFilterChain chain
    )
            throws IOException, ServletException
    {
        final PwmURL pwmURL = pwmRequest.getURL();
        if (pwmURL.isPublicUrl() && !pwmURL.isLoginServlet()) {
            chain.doFilter();
            return;
        }


        try {
            final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
            final PwmSession pwmSession = pwmRequest.getPwmSession();

            if (pwmApplication.getApplicationMode() == PwmApplicationMode.NEW) {
                if (pwmRequest.getURL().isConfigGuideURL()) {
                    chain.doFilter();
                    return;
                }
            }

            if (pwmApplication.getApplicationMode() == PwmApplicationMode.CONFIGURATION) {
                if (pwmRequest.getURL().isConfigManagerURL()) {
                    chain.doFilter();
                    return;
                }
            }

            //user is already authenticated
            if (pwmSession.isAuthenticated()) {
                this.processAuthenticatedSession(pwmRequest, chain);
            } else {
                this.processUnAuthenticatedSession(pwmRequest, chain);
            }
        } catch (PwmUnrecoverableException e) {
            LOGGER.error(e.toString());
            throw new ServletException(e.toString());
        }
    }

    @Override
    boolean isInterested(PwmApplicationMode mode, PwmURL pwmURL) {
        return !pwmURL.isResourceURL();
    }

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

        final BasicAuthInfo originalBasicAuthInfo = pwmSession.getLoginInfoBean().getBasicAuth();

        //check to make sure basic auth info is same as currently known user in session.
        if (basicAuthInfo != null && originalBasicAuthInfo != null && !(originalBasicAuthInfo.equals(basicAuthInfo))) {
            // if we read here then user is using basic auth, and header has changed since last request
            // this means something is screwy, so log out the session

            // read the current user info for logging
            final UserInfoBean uiBean = pwmSession.getUserInfoBean();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_BAD_SESSION,"basic auth header user '" + basicAuthInfo.getUsername() + "' does not match currently logged in user '" + uiBean.getUserIdentity() + "', session will be logged out");
            LOGGER.info(pwmRequest, errorInformation);

            // log out their user
            pwmSession.unauthenticateUser(pwmRequest);

            // send en error to user.
            pwmRequest.respondWithError(errorInformation, true);
            return;
        }

        // check status of oauth expiration
        if (pwmSession.getLoginInfoBean().getOauthExp() != null) {
            if (OAuthConsumerServlet.checkOAuthExpiration(pwmRequest)) {
                pwmRequest.respondWithError(new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,"oauth access token has expired"));
                return;
            }
        }
        handleAuthenticationCookie(pwmRequest);

        if (forceRequiredRedirects(pwmRequest) == ProcessStatus.Halt) {
            return;
        }

        // user session is authed, and session and auth header match, so forward request on.
        chain.doFilter();
    }

    private static void handleAuthenticationCookie(final PwmRequest pwmRequest) {
        if (!pwmRequest.isAuthenticated() || pwmRequest.getPwmSession().getLoginInfoBean().getType() != AuthenticationType.AUTHENTICATED) {
            return;
        }

        if (pwmRequest.getPwmSession().getLoginInfoBean().isLoginFlag(LoginInfoBean.LoginFlag.authRecordSet)) {
            return;
        }

        pwmRequest.getPwmSession().getLoginInfoBean().setFlag(LoginInfoBean.LoginFlag.authRecordSet);

        final String cookieName = pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_COOKIE_AUTHRECORD_NAME);
        if (cookieName == null || cookieName.isEmpty()) {
            LOGGER.debug(pwmRequest, "skipping auth record cookie set, cookie name parameter is blank" );
            return;
        }

        final int cookieAgeSeconds = Integer.parseInt(pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_COOKIE_AUTHRECORD_AGE));
        if (cookieAgeSeconds < 1) {
            LOGGER.debug(pwmRequest, "skipping auth record cookie set, cookie age parameter is less than 1" );
            return;
        }

        final Date authTime = pwmRequest.getPwmSession().getLoginInfoBean().getAuthTime();
        final String userGuid = pwmRequest.getPwmSession().getUserInfoBean().getUserGuid();
        final AuthRecord authRecord = new AuthRecord(authTime, userGuid);

        try {
            pwmRequest.getPwmResponse().writeEncryptedCookie(cookieName, authRecord, cookieAgeSeconds, PwmHttpResponseWrapper.CookiePath.Application);
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

        final boolean bypassSso = pwmRequest.getPwmSession().getLoginInfoBean().isLoginFlag(LoginInfoBean.LoginFlag.noSso);
        if (!bypassSso && pwmRequest.getPwmApplication().getApplicationMode() == PwmApplicationMode.RUNNING) {
            final ProcessStatus authenticationProcessStatus = attemptAuthenticationMethods(pwmRequest);
            if (authenticationProcessStatus == ProcessStatus.Halt) {
                return;
            }
        }

        final String originalRequestedUrl = pwmRequest.getURLwithQueryString();

        if (pwmRequest.isAuthenticated()) {
            // redirect back to self so request starts over as authenticated.
            LOGGER.trace(pwmRequest, "inline authentication occurred during this request, redirecting to current url to restart request");
            pwmRequest.getPwmResponse().sendRedirect(originalRequestedUrl);
            return;
        }

        // handle if authenticated during filter process.
        if (pwmSession.isAuthenticated()) {
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

        LoginServlet.redirectToLoginServlet(pwmRequest);
    }

    public static ProcessStatus attemptAuthenticationMethods(final PwmRequest pwmRequest) throws IOException, ServletException {
        final Set<AuthenticationMethod> authenticationMethods = new HashSet<>(Arrays.asList(AuthenticationMethod.values()));
        {
            final String casUrl = pwmRequest.getConfig().readSettingAsString(PwmSetting.CAS_CLEAR_PASS_URL);
            if (casUrl == null || casUrl.trim().isEmpty()) {
                authenticationMethods.remove(AuthenticationMethod.CAS);
            }
        }
        for (final AuthenticationMethod authenticationMethod : authenticationMethods) {
            if (!pwmRequest.isAuthenticated()) {
                try {
                    final Class<? extends PwmHttpFilterAuthenticationProvider> clazz = authenticationMethod.getImplementationClass();
                    final PwmHttpFilterAuthenticationProvider filterAuthenticationProvider = clazz.newInstance();
                    filterAuthenticationProvider.attemptAuthentication(pwmRequest);

                    if (pwmRequest.isAuthenticated()) {
                        LOGGER.trace(pwmRequest, "authentication provided by method " + clazz.getName());
                    }

                    if (filterAuthenticationProvider.hasRedirectedResponse()) {
                        LOGGER.trace(pwmRequest, "authentication provider " + clazz.getName() + " has issued a redirect, halting authentication process");
                        return ProcessStatus.Halt;
                    }

                } catch (Exception e) {
                    final ErrorInformation errorInformation;
                    if (e instanceof PwmException) {
                        final String erorrMessage = "error during " + authenticationMethod + " authentication attempt: " + e.getMessage();
                        errorInformation = new ErrorInformation(((PwmException) e).getError(), erorrMessage);
                    } else {
                        errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, e.getMessage());

                    }
                    LOGGER.error(pwmRequest, errorInformation);
                    pwmRequest.respondWithError(errorInformation);
                    return ProcessStatus.Halt;
                }
            }
        }
        return ProcessStatus.Continue;
    }


    public static ProcessStatus forceRequiredRedirects(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmURL pwmURL = pwmRequest.getURL();

        final UserInfoBean uiBean = pwmSession.getUserInfoBean();

        if (pwmURL.isResourceURL() || pwmURL.isConfigManagerURL() || pwmURL.isLogoutURL() || pwmURL.isLoginServlet()) {
            return ProcessStatus.Continue;
        }

        if (pwmRequest.getPwmApplication().getApplicationMode() != PwmApplicationMode.RUNNING) {
            return ProcessStatus.Continue;
        }

        // high priority pw change
        if (pwmRequest.getPwmSession().getLoginInfoBean().getType() == AuthenticationType.AUTH_FROM_PUBLIC_MODULE) {
            if (!pwmURL.isChangePasswordURL()) {
                LOGGER.debug(pwmRequest, "user is authenticated via forgotten password mechanism, redirecting to change password servlet");
                pwmRequest.sendRedirect(
                        pwmRequest.getContextPath()
                                + PwmConstants.URL_PREFIX_PUBLIC
                                + "/"
                                + PwmServletDefinition.ChangePassword.servletUrlName());
                return ProcessStatus.Halt;
            } else {
                return ProcessStatus.Continue;
            }
        }

        // if change password in progress and req is for ChangePassword servlet, then allow request as is
        if (pwmURL.isChangePasswordURL()) {
            final ChangePasswordBean cpb = pwmRequest.getPwmApplication().getSessionStateService().getBean(pwmRequest, ChangePasswordBean.class);
            final PasswordChangeProgressChecker.ProgressTracker progressTracker = cpb.getChangeProgressTracker();
            if (progressTracker != null && progressTracker.getBeginTime() != null) {
                return ProcessStatus.Continue;
            }
        }


        if (uiBean.isRequiresResponseConfig()) {
            if (!pwmURL.isSetupResponsesURL()) {
                LOGGER.debug(pwmRequest, "user is required to setup responses, redirecting to setup responses servlet");
                pwmRequest.sendRedirect(PwmServletDefinition.SetupResponses);
                return ProcessStatus.Halt;
            } {
                return ProcessStatus.Continue;
            }
        }

        if (uiBean.isRequiresOtpConfig() && !pwmSession.getLoginInfoBean().isLoginFlag(LoginInfoBean.LoginFlag.skipOtp)) {
            if (!pwmURL.isSetupOtpSecretURL()) {
                LOGGER.debug(pwmRequest, "user is required to setup OTP configuration, redirecting to OTP setup page");
                pwmRequest.sendRedirect(PwmServletDefinition.SetupOtp);
                return ProcessStatus.Halt;
            } else {
                return ProcessStatus.Continue;
            }
        }

        if (uiBean.isRequiresUpdateProfile()) {
            if (!pwmURL.isProfileUpdateURL()) {
                LOGGER.debug(pwmRequest, "user is required to update profile, redirecting to profile update servlet");
                pwmRequest.sendRedirect(PwmServletDefinition.UpdateProfile);
                return ProcessStatus.Halt;
            } else {
                return ProcessStatus.Continue;
            }
        }

        if (uiBean.isRequiresNewPassword() && !pwmSession.getLoginInfoBean().isLoginFlag(LoginInfoBean.LoginFlag.skipNewPw)) {
            if (!pwmURL.isChangePasswordURL()) {
                LOGGER.debug(pwmRequest, "user password requires changing, redirecting to change password servlet");
                pwmRequest.sendRedirect(PwmServletDefinition.ChangePassword);
                return ProcessStatus.Halt;
            } else {
                return ProcessStatus.Continue;
            }
        }

        return ProcessStatus.Continue;
    }

    enum AuthenticationMethod {
        BASIC_AUTH(BasicFilterAuthenticationProvider.class.getName()),
        SSO_AUTH_HEADER(SSOHeaderFilterAuthenticationProvider.class.getName()),
        CAS("password.pwm.util.CASFilterAuthenticationProvider"),
        OAUTH(OAuthFilterAuthenticationProvider.class.getName())

        ;

        private final String className;

        AuthenticationMethod(String className) {
            this.className = className;
        }

        public Class<? extends PwmHttpFilterAuthenticationProvider> getImplementationClass() throws PwmUnrecoverableException {
            try {
                return (Class<? extends PwmHttpFilterAuthenticationProvider>) Class.forName(className);
            } catch (ClassNotFoundException | ClassCastException e) {
                final String errorMsg = "error loading authentication method: " + this.getImplementationClass() + ", error: " + e.getMessage();
                LOGGER.error(errorMsg,e);
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg));
            }
        }
    }

    public static class BasicFilterAuthenticationProvider implements PwmHttpFilterAuthenticationProvider {

        @Override
        public void attemptAuthentication(
                final PwmRequest pwmRequest
        )
                throws PwmUnrecoverableException
        {
            if (!pwmRequest.isAuthenticated()) {
                final BasicAuthInfo basicAuthInfo = BasicAuthInfo.parseAuthHeader(pwmRequest.getPwmApplication(), pwmRequest);
                if (basicAuthInfo != null) {
                    try {
                        final PwmSession pwmSession = pwmRequest.getPwmSession();
                        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

                        //user isn't already authenticated and has an auth header, so try to auth them.
                        LOGGER.debug(pwmSession, "attempting to authenticate user using basic auth header (username=" + basicAuthInfo.getUsername() + ")");
                        final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(
                                pwmApplication,
                                pwmSession,
                                PwmAuthenticationSource.BASIC_AUTH
                        );
                        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication, pwmSession.getLabel());
                        final UserIdentity userIdentity = userSearchEngine.resolveUsername(basicAuthInfo.getUsername(), null, null);
                        sessionAuthenticator.authenticateUser(userIdentity, basicAuthInfo.getPassword());
                        pwmSession.getLoginInfoBean().setBasicAuth(basicAuthInfo);

                    } catch (ChaiUnavailableException e) {
                        StatisticsManager.incrementStat(pwmRequest, Statistic.LDAP_UNAVAILABLE_COUNT);
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage());
                        throw new PwmUnrecoverableException(errorInformation);
                    } catch (PwmException e) {
                        throw new PwmUnrecoverableException(e.getError());
                    }
                }
            }
        }

        @Override
        public boolean hasRedirectedResponse() {
            return false;
        }
    }

    static class SSOHeaderFilterAuthenticationProvider implements PwmHttpFilterAuthenticationProvider {

        @Override
        public void attemptAuthentication(
                final PwmRequest pwmRequest
        )
                throws PwmUnrecoverableException
        {
            {
                final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
                final PwmSession pwmSession = pwmRequest.getPwmSession();

                final String headerName = pwmApplication.getConfig().readSettingAsString(PwmSetting.SSO_AUTH_HEADER_NAME);
                if (headerName == null || headerName.length() < 1) {
                    return;
                }


                final String headerValue = pwmRequest.readHeaderValueAsString(headerName);
                if (headerValue == null || headerValue.length() < 1) {
                    return;
                }

                LOGGER.debug(pwmRequest, "SSO Authentication header present in request, will search for user value of '" + headerValue + "'");
                final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(
                        pwmApplication,
                        pwmSession,
                        PwmAuthenticationSource.SSO_HEADER
                );

                try {
                    sessionAuthenticator.authUserWithUnknownPassword(headerValue, AuthenticationType.AUTH_WITHOUT_PASSWORD);
                } catch (PwmOperationalException e) {
                    throw new PwmUnrecoverableException(e.getErrorInformation());
                }
            }
        }

        @Override
        public boolean hasRedirectedResponse() {
            return false;
        }
    }


    static class OAuthFilterAuthenticationProvider implements PwmHttpFilterAuthenticationProvider {

        private boolean redirected = false;

        public void attemptAuthentication(
                final PwmRequest pwmRequest
        )
                throws PwmUnrecoverableException, IOException
        {
            final OAuthSettings settings = OAuthSettings.fromConfiguration(pwmRequest.getConfig());
            if (!settings.oAuthIsConfigured()) {
                return;
            }

            final String originalURL = pwmRequest.getURLwithQueryString();
            OAuthConsumerServlet.redirectUserToOAuthServer(pwmRequest, originalURL);
            redirected = true;
        }

        @Override
        public boolean hasRedirectedResponse() {
            return redirected;
        }
    }
}

