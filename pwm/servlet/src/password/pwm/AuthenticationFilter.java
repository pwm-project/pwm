/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.Message;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmException;
import password.pwm.util.*;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

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
            throws ServletException
    {
    }

    public void doFilter(
            final ServletRequest servletRequest,
            final ServletResponse servletResponse,
            final FilterChain chain
    )
            throws IOException, ServletException
    {
        final HttpServletRequest req = (HttpServletRequest) servletRequest;
        final HttpServletResponse resp = (HttpServletResponse) servletResponse;

        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        //user is already authenticated
        if (ssBean.isAuthenticated()) {
            this.processAuthenticatedSession(req, resp, chain);
        } else {
            this.processUnAuthenticatedSession(req, resp, chain);
        }
    }

    /**
     * Method requird to implement filterservlet
     */
    public void destroy()
    {
    }

// -------------------------- OTHER METHODS --------------------------

    private void processAuthenticatedSession(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final FilterChain chain
    )
            throws IOException, ServletException
    {
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
            LOGGER.info(pwmSession, "user info for " + uiBean.getUserDN() + " does not match current basic auth header, unauthenticating user.");

            // log out their user
            pwmSession.unauthenticateUser();

            // update the ssBean variable with the new sessionStateBean.
            ssBean = pwmSession.getSessionStateBean();

            // send en error to user.
            ssBean.setSessionError(Message.ERROR_FIELDS_DONT_MATCH.toInfo());
            Helper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
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
            throws IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        final Configuration config = pwmSession.getConfig();
        final String loginServletURL = config.readSettingAsString(PwmSetting.URL_SERVET_RELATIVE) + "/private/" + Constants.URL_SERVLET_LOGIN;
        final String requestedURL = req.getRequestURI();

        // check if current request is actually for the login servlet url, if it is, just do nothing.
        if (requestedURL.equals(loginServletURL)) {
            LOGGER.trace(pwmSession, "permitting unauthenticated request of login page");
            chain.doFilter(req, resp);
            return;
        }

        //try to authenticate user with basic auth
        try {
            if (authUserUsingBasicHeader(req)) {
                chain.doFilter(req, resp);
                return;
            }
        } catch (ChaiUnavailableException e) {
            pwmSession.getContextManager().getStatisticsManager().incrementValue(StatisticsManager.Statistic.LDAP_UNAVAILABLE_COUNT);
            pwmSession.getContextManager().getStatisticsManager().updateTimestamp(StatisticsManager.Statistic.LDAP_UNAVAILABLE_TIME);
            ssBean.setSessionError(Message.ERROR_DIRECTORY_UNAVAILABLE.toInfo());
            Helper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
            return;
        } catch (PwmException e) {
            ssBean.setSessionError(new ErrorInformation(Message.ERROR_UNKNOWN,e.getMessage()));
            Helper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
            return;
        }

        // user is not logged in, and should be (otherwise this filter would not be invoked).
        if (Boolean.parseBoolean(pwmSession.getContextManager().getParameter(Constants.CONTEXT_PARAM.FORCE_BASIC_AUTH))) {
            String displayMessage = PwmSession.getPwmSession(req).getContextManager().getLocaleConfig(ssBean.getLocale()).getApplicationTitle();
            if (displayMessage == null) {
                displayMessage =  "Password Self Service";
            }

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
        Helper.forwardToLoginPage(req, resp);
    }

    public static boolean authenticateUser(
            final String username,
            final String password,
            final String context,
            final PwmSession pwmSession,
            final boolean secure
    )
            throws ChaiUnavailableException, PwmException
    {
        final long methodStartTime = System.currentTimeMillis();
        final ContextManager theManager = pwmSession.getContextManager();
        final StatisticsManager statusBean = theManager.getStatisticsManager();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final IntruderManager intruderManager = theManager.getIntruderManager();

        //see if we need to a contextless search.
        final String userDN = convertUsernameFieldtoDN(username, pwmSession, context);

        if (userDN == null) { // if no user info then end authentication attempt
            LOGGER.debug(pwmSession, "DN for user " + username + " not found");
            intruderManager.addBadAddressAttempt(pwmSession);
            intruderManager.addBadUserAttempt(username,pwmSession);
            intruderManager.checkUser(username,pwmSession);
            statusBean.incrementValue(StatisticsManager.Statistic.FAILED_LOGIN_ATTEMPTS);
            Helper.pause(PwmRandom.getInstance().nextInt(2 * 1000) + 1000); // delay penalty of 1-3 seconds
            return false;
        }

        intruderManager.checkUser(userDN, pwmSession);
        intruderManager.checkAddress(pwmSession);

        final boolean successPasswordCheck = testCredentials(userDN, password, pwmSession, theManager);

        if (successPasswordCheck) { // auth succeed
            ssBean.setAuthenticated(true);
            UserStatusHelper.populateUserInfoBean(pwmSession, userDN, password);

            final StringBuilder debugMsg = new StringBuilder();
            debugMsg.append("successful ");
            debugMsg.append(secure ? "ssl" : "plaintext");
            debugMsg.append(" authentication for ").append(userDN);
            debugMsg.append(" (").append(TimeDuration.fromCurrent(methodStartTime).asCompactString()).append(")");
            LOGGER.info(pwmSession, debugMsg);

            statusBean.incrementValue(StatisticsManager.Statistic.PWM_AUTHENTICATIONS);

            //attempt to add the object class to the user
            Helper.addConfiguredUserObjectClass(userDN, pwmSession);

            //notify the intruder manager with a successfull login
            intruderManager.addGoodAddressAttempt(pwmSession);
            intruderManager.addGoodUserAttempt(userDN,pwmSession);

            return true;
        }

        // auth failed, presumably due to wrong password.
        ssBean.setAuthenticated(false);
        intruderManager.addBadAddressAttempt(pwmSession);
        intruderManager.addBadUserAttempt(userDN, pwmSession);
        LOGGER.info(pwmSession, "login attempt for " + userDN + " failed: wrong password");
        statusBean.incrementValue(StatisticsManager.Statistic.FAILED_LOGIN_ATTEMPTS);
        Helper.pause(PwmRandom.getInstance().nextInt(2 * 1000) + 1000); // delay penalty of 1-3 seconds
        return false;
    }

    /**
     * For a given username, find an appropriate objectDN.  Uses parameters in the PWM
     * configuration to specify how the search should be performed.
     * <p/>
     * If exactly one match is discovered, then that value is returned.  Otherwise if
     * no matches or if multiple matches are discovered then null is returned.  Multiple
     * matches are considered an error condition.
     * <p/>
     * If the username appears to already be a valid DN, then the context search is not performed
     * and instead the username value is returned.
     *
     * @param username          username to search for
     * @param pwmSession        for grabbing required beans
     * @param context           specify context to use to search, or null to use pwm configured attribute
     * @return the discovered objectDN of the user, or null if none found.
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException of directory is unavailable
     */
    public static String convertUsernameFieldtoDN(
            final String username,
            final PwmSession pwmSession,
            final String context
    )
            throws ChaiUnavailableException
    {
        if (username == null || username.length() < 1) {
            return "";
        }

        String baseDN = pwmSession.getConfig().readSettingAsString(PwmSetting.LDAP_CONTEXTLESS_ROOT);

        // see if the baseDN should be the context parameter
        if (context != null && context.length() > 0) {
            if (pwmSession.getConfig().getLoginContexts().containsKey(context)) {
                if (context.endsWith(baseDN)) {
                    baseDN = context;
                } else {
                    LOGGER.debug(pwmSession, "attempt to use '" + context + "' context for search, but does not end with configured contextless root: " + baseDN);
                }
            }
        }

        if (baseDN == null || baseDN.length() < 1) {
            return username;
        }

        final String usernameAttribute = pwmSession.getContextManager().getParameter(Constants.CONTEXT_PARAM.LDAP_NAMING_ATTRIBUTE);

        //if supplied user name starts with username attr assume its the full dn and skip the contextless login
        if (username.toLowerCase().startsWith(usernameAttribute.toLowerCase() + "=")) {
            LOGGER.trace(pwmSession, "username appears to be a DN; skipping contextless search");
            return username;
        }

        LOGGER.trace(pwmSession, "attempting contextless login search for '" + username + "'");

        final String filterSetting = pwmSession.getConfig().readSettingAsString(PwmSetting.USERNAME_SEARCH_FILTER);
        final String filter = filterSetting.replace(Constants.VALUE_REPLACEMENT_USERNAME,username);

        final SearchHelper searchHelper = new SearchHelper();
        searchHelper.setFilter(filter);
        searchHelper.setAttributes("");
        searchHelper.setSearchScope(ChaiProvider.SEARCH_SCOPE.SUBTREE);

        LOGGER.trace(pwmSession, "search for contextless login: " + searchHelper.getFilter() + ", baseDN: " + baseDN);

        try {
            final SessionManager sessionMgr = pwmSession.getSessionManager();
            assert sessionMgr != null;

            final ChaiProvider provider = pwmSession.getContextManager().getProxyChaiProvider();
            assert provider != null;

            final Map<String, Properties> results = provider.search(baseDN, searchHelper);

            if (results == null || results.size() == 0) {
                LOGGER.trace(pwmSession, "no matches found");
                return null;
            } else if (results.size() > 1) {
                LOGGER.trace(pwmSession, "multiple matches found");
                LOGGER.warn(pwmSession, "multiple matches found when doing search for userID: " + username);
            } else {
                final String userDN = results.keySet().iterator().next();
                LOGGER.trace(pwmSession, "match found: " + userDN);
                return userDN;
            }
        } catch (ChaiOperationException e) {
            LOGGER.warn(pwmSession, "error during contextless search: " + e.getMessage());
        }
        return null;
    }

    public static boolean testCredentials(
            final String userDN,
            final String password,
            final PwmSession pwmSession,
            final ContextManager theManager
    )
            throws ChaiUnavailableException, PwmException
    {
        final boolean alwaysUseProxy = pwmSession.getConfig().readSettingAsBoolean(PwmSetting.EDIRECTORY_ALWAYS_USE_PROXY);
        final boolean ldapIsEdirectory = theManager.getProxyChaiProvider().getDirectoryVendor() == ChaiProvider.DIRECTORY_VENDOR.NOVELL_EDIRECTORY;

        if (userDN == null || userDN.length() < 1) {
            throw PwmException.createPwmException(new ErrorInformation(Message.ERROR_UNKNOWN,"attempt to authenticate with null userDN"));
        }

        if (password == null || password.length() < 1) {
            throw PwmException.createPwmException(new ErrorInformation(Message.ERROR_UNKNOWN,"attempt to authenticate with null password"));
        }

        if (alwaysUseProxy && ldapIsEdirectory) { //try authenticating user by binding as admin proxy, and using ldap COMPARE operation.
            LOGGER.trace(pwmSession, "attempting authentication using ldap compare operation");
            final ChaiProvider provider = theManager.getProxyChaiProvider();
            final ChaiUser theUser = ChaiFactory.createChaiUser(userDN, provider);

            try {
                final boolean correctPassword = theUser.compareStringAttribute(ChaiUser.ATTR_PASSWORD, password);

                if (correctPassword) {
                    // check if user's login is disabled
                    final boolean loginDisabled = theUser.readBooleanAttribute(ChaiUser.ATTR_LOGIN_DISABLED);
                    if (!loginDisabled) {
                        return true;
                    }
                }
            } catch (ChaiOperationException e) {
                LOGGER.warn(pwmSession, "ldap bind compare failed, check ldap proxy user account");
                if (e.getMessage() != null && e.getMessage().indexOf("(-197)") != -1) {
                    LOGGER.warn(pwmSession, "intruder lockout detected for user " + userDN + " marking session as locked out");
                    theManager.getIntruderManager().addBadUserAttempt(userDN, pwmSession);
                    pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(Message.ERROR_INTRUDER_USER));
                    throw PwmException.createPwmException(Message.ERROR_INTRUDER_USER);
                } else {
                    LOGGER.debug(pwmSession, "ldap error during credential test: " + e.getMessage());
                }
            }
        } else { //try authetnicating the user using a normal ldap BIND operation.
            LOGGER.trace(pwmSession, "attempting authentication using ldap BIND");
            ChaiProvider provider = null;
            try {
                //get a provider using the user's DN and password.
                provider = Helper.createChaiProvider(theManager, userDN, password);

                //issue a read operation to trigger a bind.
                provider.readStringAttribute(userDN, ChaiConstant.ATTR_LDAP_OBJECTCLASS);

                //save the provider in the session manager so that it doesn't issue two BINDs.
                pwmSession.getSessionManager().setChaiProvider(provider);

                return true;
            } catch (ChaiException e) {
                if (e.getMessage() != null && e.getMessage().indexOf("(-197)") != -1) {
                    LOGGER.warn(pwmSession, "intruder lockout detected for user " + userDN + " marking session as locked out");
                    theManager.getIntruderManager().addBadUserAttempt(userDN, pwmSession);
                    pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(Message.ERROR_INTRUDER_USER));
                    throw PwmException.createPwmException(Message.ERROR_INTRUDER_USER);
                } else {
                    LOGGER.debug(pwmSession, "ldap error during credential test: " + e.getMessage());
                }

                if (provider != null) {
                    try { provider.close(); } catch (Exception e2) { /* ignore */ }
                }
            }
        }
        return false;
    }

    public static boolean authUserUsingBasicHeader(
            final HttpServletRequest req
    )
            throws ChaiUnavailableException, PwmException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        //make sure user session isn't already authenticated
        if (ssBean.isAuthenticated()) {
            return true;
        }

        // get the basic auth info out of the header (if it exists);
        final BasicAuthInfo basicAuthInfo = BasicAuthInfo.parseAuthHeader(req);

        if (basicAuthInfo == null) {
            return false;
        }

        //user isn't already authed and has an auth header, so try to auth them.
        LOGGER.debug(pwmSession, "attempting to authenticate user using basic auth header");
        if (authenticateUser(basicAuthInfo.getUsername(), basicAuthInfo.getPassword(), null, pwmSession, req.isSecure())) {
            pwmSession.getSessionStateBean().setOriginalBasicAuthInfo(basicAuthInfo);
            return true;
        }

        return false;
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
     * @param req http request
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException If ldap becomes unreachable
     * @throws password.pwm.error.PwmException             If there is some reason the session can't be authenticated
     *                                  If the user's password policy is determined to be impossible to satisfy
     * @throws com.novell.ldapchai.exception.ImpossiblePasswordPolicyException  if the temporary password generated can't be due to an impossible policy
     */
    public static void authUserWithUnknownPassword(
            final ChaiUser theUser,
            final PwmSession pwmSession,
            final HttpServletRequest req
    )
            throws ChaiUnavailableException, ImpossiblePasswordPolicyException, PwmException
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
            if (pwmSession.getConfig().readSettingAsBoolean(PwmSetting.EDIRECTORY_ENABLE_NMAS)) {
                LOGGER.error(pwmSession, "error retrieving user password from directory; " + e.getMessage());
            } else {
                LOGGER.trace(pwmSession, "error retrieving user password from directory; " + e.getMessage());
            }
        }

        // if retrieval didn't work, set to random password.
        if (currentPass == null || currentPass.length() <= 0) {
            LOGGER.debug(pwmSession, "unable to retrieving user password from directory (allow retrieving passwords for admin is probably disabled), will set to temporary random password");
            try {
                final PwmPasswordPolicy passwordPolicy = PwmPasswordPolicy.createPwmPasswordPolicy(pwmSession, theUser);
                pwmSession.getUserInfoBean().setPasswordPolicy(passwordPolicy);

                // createSharedHistoryManager random password for user
                currentPass = RandomPasswordGenerator.createRandomPassword(pwmSession);

                // write the random password for the user.
                try {
                    theUser.setPassword(currentPass);

                    // if old password is not known, then mark the UIB accordingly.
                    pwmSession.getUserInfoBean().setAuthFromUnknownPw(true);
                } catch (ChaiOperationException e) {
                    LOGGER.warn(pwmSession, "error setting random password for user " + theUser.getEntryDN() + " " + e.getMessage());
                    throw PwmException.createPwmException(Message.ERROR_BAD_SESSION_PASSWORD);
                } catch (ChaiPasswordPolicyException e) {
                    LOGGER.warn(pwmSession, "error setting random password for user " + theUser.getEntryDN() + " " + e.getMessage());
                    throw PwmException.createPwmException(Message.ERROR_BAD_SESSION_PASSWORD);
                }
            } finally {
                pwmSession.getUserInfoBean().setPasswordPolicy(PwmPasswordPolicy.defaultPolicy());
            }
        }

        //close any outstanding ldap connections (since they cache the old password)
        pwmSession.getSessionManager().closeConnections();

        // mark the session as being authenticated
        authenticateUser(theUser.getEntryDN(), currentPass, null, pwmSession, req.isSecure());
        // repopulate the uib, including setting the currentPass as the current password.

        UserStatusHelper.populateUserInfoBean(pwmSession, theUser.getEntryDN(), currentPass);

        // get the uib out of the session again (it may have been replaced) and mark
        // the password as expired to force a user password change.
        pwmSession.getUserInfoBean().setRequiresNewPassword(true);
    }
}

