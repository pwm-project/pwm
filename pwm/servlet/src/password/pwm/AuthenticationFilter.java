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

import com.novell.ldapchai.ChaiConstant;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.*;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Display;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.util.*;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;

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

    public static void authenticateUser(
            final String username,
            final String password,
            final String context,
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final boolean secure
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException {
        final long methodStartTime = System.currentTimeMillis();
        final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final IntruderManager intruderManager = pwmApplication.getIntruderManager();

        //see if we need to a contextless search.
        final String userDN;
        try {
            userDN = UserStatusHelper.convertUsernameFieldtoDN(username, pwmSession, pwmApplication, context);
        } catch (PwmOperationalException e) {
            intruderManager.addBadAddressAttempt(pwmSession);
            intruderManager.addBadUserAttempt(username, pwmSession);
            intruderManager.checkUser(username, pwmSession);
            statisticsManager.incrementValue(Statistic.AUTHENTICATION_FAILURES);
            pwmApplication.getIntruderManager().delayPenalty(username, pwmSession);
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_WRONGPASSWORD,e.getErrorInformation().getDetailedErrorMsg(),e.getErrorInformation().getFieldValues()));
        }

        intruderManager.checkUser(userDN, pwmSession);
        intruderManager.checkAddress(pwmSession);

        try {
            testCredentials(userDN, password, pwmSession, pwmApplication);
        } catch (PwmOperationalException e) {
            // auth failed, presumably due to wrong password.
            ssBean.setAuthenticated(false);
            intruderManager.addBadAddressAttempt(pwmSession);
            intruderManager.addBadUserAttempt(userDN, pwmSession);
            LOGGER.info(pwmSession, "login attempt for " + userDN + " failed: " + e.getErrorInformation().toDebugStr());
            statisticsManager.incrementValue(Statistic.AUTHENTICATION_FAILURES);
            pwmApplication.getIntruderManager().delayPenalty(userDN, pwmSession);
            throw e;
        }

        // auth succeed
        ssBean.setAuthenticated(true);

        final StringBuilder debugMsg = new StringBuilder();
        debugMsg.append("successful ");
        debugMsg.append(secure ? "ssl" : "plaintext");
        debugMsg.append(" authentication for ").append(userDN);
        debugMsg.append(" (").append(TimeDuration.fromCurrent(methodStartTime).asCompactString()).append(")");
        LOGGER.info(pwmSession, debugMsg);
        statisticsManager.incrementValue(Statistic.AUTHENTICATIONS);
        statisticsManager.updateEps(StatisticsManager.EpsType.AUTHENTICATION_10, 1);
        statisticsManager.updateEps(StatisticsManager.EpsType.AUTHENTICATION_60, 1);
        statisticsManager.updateEps(StatisticsManager.EpsType.AUTHENTICATION_240,1);

        // update the actor user info bean
        UserStatusHelper.populateActorUserInfoBean(pwmSession, pwmApplication, userDN, password);

        if (pwmSession.getUserInfoBean().getPasswordState().isWarnPeriod()) {
            statisticsManager.incrementValue(Statistic.AUTHENTICATION_EXPIRED_WARNING);
        } else if (pwmSession.getUserInfoBean().getPasswordState().isPreExpired()) {
            statisticsManager.incrementValue(Statistic.AUTHENTICATION_PRE_EXPIRED);
        } else if (pwmSession.getUserInfoBean().getPasswordState().isExpired()) {
            statisticsManager.incrementValue(Statistic.AUTHENTICATION_EXPIRED);
        }

        //notify the intruder manager with a successfull login
        intruderManager.addGoodAddressAttempt(pwmSession);
        intruderManager.addGoodUserAttempt(userDN, pwmSession);

        if (pwmApplication.getStatisticsManager() != null) {
            pwmApplication.getStatisticsManager().updateAverageValue(Statistic.AVG_AUTHENTICATION_TIME, TimeDuration.fromCurrent(methodStartTime).getTotalMilliseconds());
        }

    }

    public static void testCredentials(
            final String userDN,
            final String password,
            final PwmSession pwmSession,
            final PwmApplication pwmApplication
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException {
        LOGGER.trace(pwmSession, "beginning testCredentials process");

        final boolean alwaysUseProxy = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.LDAP_ALWAYS_USE_PROXY);
        final boolean ldapIsEdirectory = pwmApplication.getProxyChaiProvider().getDirectoryVendor() == ChaiProvider.DIRECTORY_VENDOR.NOVELL_EDIRECTORY;

        if (userDN == null || userDN.length() < 1) {
            final String errorMsg = "attempt to authenticate with null userDN";
            LOGGER.debug(pwmSession, errorMsg);
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_WRONGPASSWORD,errorMsg));
        }

        if (password == null || password.length() < 1) {
            final String errorMsg = "attempt to authenticate with null password";
            LOGGER.debug(pwmSession, errorMsg);
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_WRONGPASSWORD,errorMsg));
        }

        if (alwaysUseProxy && ldapIsEdirectory) { //try authenticating user by binding as admin proxy, and using ldap COMPARE operation.
            LOGGER.trace(pwmSession, "attempting authentication using ldap compare operation");
            final ChaiProvider provider = pwmApplication.getProxyChaiProvider();
            final ChaiUser theUser = ChaiFactory.createChaiUser(userDN, provider);

            try {
                final boolean correctPassword = theUser.compareStringAttribute(ChaiUser.ATTR_PASSWORD, password);

                if (correctPassword) {
                    // check if user's login is disabled
                    final boolean loginDisabled = theUser.readBooleanAttribute(ChaiUser.ATTR_LOGIN_DISABLED);
                    if (loginDisabled) {
                        final String errorMsg = "ldap compare operation is failed due to ldap account being disabled";
                        LOGGER.debug(pwmSession, errorMsg);
                        throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_WRONGPASSWORD,errorMsg));
                    }
                }

                return;
            } catch (ChaiOperationException e) {
                LOGGER.warn(pwmSession, "ldap bind compare failed, check ldap proxy user account");

                if (e.getErrorCode() != null && e.getErrorCode() == ChaiError.INTRUDER_LOCKOUT) {
                    final String errorMsg = "intruder lockout detected for user " + userDN + " marking session as locked out: " + e.getMessage();
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INTRUDER_USER, errorMsg);
                    LOGGER.warn(pwmSession, errorInformation.toDebugStr());
                    pwmApplication.getIntruderManager().addBadUserAttempt(userDN, pwmSession);
                    if (!PwmConstants.DEFAULT_BAD_PASSWORD_ATTEMPT.equals(password)) {
                        pwmSession.getSessionStateBean().setSessionError(errorInformation);
                    }
                    throw new PwmUnrecoverableException(errorInformation);
                }
                final String errorMsg = "ldap error during password check: " + e.getMessage();
                LOGGER.debug(pwmSession, errorMsg);
                throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_WRONGPASSWORD,errorMsg));
            }
        }

        //try authenticating the user using a normal ldap BIND operation.
        LOGGER.trace(pwmSession, "attempting authentication using ldap BIND");
        try {
            //get a provider using the user's DN and password.
            final ChaiProvider testProvider = pwmSession.getSessionManager().getChaiProvider(userDN, password);

            //issue a read operation to trigger a bind.
            testProvider.readStringAttribute(userDN, ChaiConstant.ATTR_LDAP_OBJECTCLASS);
        } catch (ChaiException e) {
            if (e.getErrorCode() != null && e.getErrorCode() == ChaiError.INTRUDER_LOCKOUT) {
                final String errorMsg = "intruder lockout detected for user " + userDN + " marking session as locked out: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INTRUDER_USER, errorMsg);
                LOGGER.warn(pwmSession, errorInformation.toDebugStr());
                pwmApplication.getIntruderManager().addBadUserAttempt(userDN, pwmSession);
                pwmSession.getSessionStateBean().setSessionError(errorInformation);
                throw new PwmUnrecoverableException(errorInformation);
            }
            final String errorMsg = "ldap error during password check: " + e.getMessage();
            LOGGER.debug(pwmSession, errorMsg);
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_WRONGPASSWORD,errorMsg));
        }
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
        authenticateUser(basicAuthInfo.getUsername(), basicAuthInfo.getPassword(), null, pwmSession, pwmApplication, req.isSecure());

        pwmSession.getSessionStateBean().setOriginalBasicAuthInfo(basicAuthInfo);
    }

    /**
     * Caused by various modules, this method will cause the PWM session to become
     * authenticated without having the users password.  Depending on configuration
     * and nmas availability this may cause the users ldap password to be set to a random
     * value.  Typically the user would be redirectde to the change password servlet immediately
     * after this method is called.
     * <p/>
     * It is up to the caller to insure that any security requirements have been met BEFORE calling
     * this method, such as validiting challenge/responses.
     *
     * @param theUser    User to authenticate
     * @param pwmSession A PwmSession instance
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException
     *          If ldap becomes unreachable
     * @throws password.pwm.error.PwmUnrecoverableException
     *          If there is some reason the session can't be authenticated
     *          If the user's password policy is determined to be impossible to satisfy
     * @throws com.novell.ldapchai.exception.ImpossiblePasswordPolicyException
     *          if the temporary password generated can't be due to an impossible policy
     */
    public static void authUserWithUnknownPassword(
            final ChaiUser theUser,
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final boolean secure
    )
            throws ChaiUnavailableException, ImpossiblePasswordPolicyException, PwmUnrecoverableException
    {
        LOGGER.trace(pwmSession, "beginning auth processes for user with unknown password");
        if (theUser == null || theUser.getEntryDN() == null) {
            throw new NullPointerException("invalid user (null)");
        }

        String currentPass = null;

        // use chai (nmas) to retrieve user password
        try {
            final String readPassword = theUser.readPassword();
            if (readPassword != null && readPassword.length() > 0) {
                currentPass = readPassword;
                LOGGER.debug(pwmSession, "successfully retrieved password from directory");
            }
        } catch (Exception e) {
            if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.EDIRECTORY_ENABLE_NMAS) && theUser.getChaiProvider().getDirectoryVendor() == ChaiProvider.DIRECTORY_VENDOR.NOVELL_EDIRECTORY) {
                LOGGER.debug(pwmSession, "unable to retrieve user password from directory; " + e.getMessage());
            } else {
                LOGGER.debug(pwmSession, "unable to retrieve user password from directory; " + e.getMessage());
            }
        }

        // if retrieval didn't work, set to random password.
        if (currentPass == null || currentPass.length() <= 0) {
            LOGGER.debug(pwmSession, "attempting to set temporary random password");
            try {
                final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(pwmApplication, pwmSession, theUser, pwmSession.getSessionStateBean().getLocale());
                pwmSession.getUserInfoBean().setPasswordPolicy(passwordPolicy);

                // createSharedHistoryManager random password for user
                currentPass = RandomPasswordGenerator.createRandomPassword(pwmSession, pwmApplication);

                // write the random password for the user.
                try {
                    theUser.setPassword(currentPass);
                    LOGGER.info(pwmSession, "user " + theUser.getEntryDN() + " password has been set to random value for pwm to use for user authentication");

                } catch (ChaiPasswordPolicyException e) {
                    final String errorStr = "error setting random password for user " + theUser.getEntryDN() + " " + e.getMessage();
                    LOGGER.warn(pwmSession, errorStr);
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_BAD_SESSION_PASSWORD, errorStr));
                } catch (ChaiOperationException e) {
                    final String errorStr = "error setting random password for user " + theUser.getEntryDN() + " " + e.getMessage();
                    LOGGER.warn(pwmSession, errorStr);
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_BAD_SESSION_PASSWORD, errorStr));
                }
            } finally {
                pwmSession.getUserInfoBean().setPasswordPolicy(PwmPasswordPolicy.defaultPolicy());
            }
        }

        //close any outstanding ldap connections (since they cache the old password)
        pwmSession.getSessionManager().closeConnections();

        // mark the session as being authenticated
        try {
            authenticateUser(theUser.getEntryDN(), currentPass, null, pwmSession, pwmApplication, secure);
        } catch (PwmOperationalException e) {
            final String errorStr = "unable to authenticate user with temporary or retrieved password, check proxy rights, ldap logs, and ensure " + PwmSetting.LDAP_NAMING_ATTRIBUTE.getKey() + " setting is correct";
            LOGGER.error(errorStr);
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_BAD_SESSION_PASSWORD, errorStr));
        }

        // repopulate the uib, including setting the currentPass as the current password.
        UserStatusHelper.populateActorUserInfoBean(pwmSession, pwmApplication, theUser.getEntryDN(), currentPass);

        // get the uib out of the session again (it may have been replaced) and mark
        // the password as expired to force a user password change.
        pwmSession.getUserInfoBean().setRequiresNewPassword(true);

        // mark the uib as coming from unknown pw.
        pwmSession.getUserInfoBean().setAuthFromUnknownPw(true);
    }
}

