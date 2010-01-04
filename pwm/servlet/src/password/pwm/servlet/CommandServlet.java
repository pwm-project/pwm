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

package password.pwm.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.*;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Message;
import password.pwm.config.ParameterConfig;
import password.pwm.config.PasswordStatus;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmException;
import password.pwm.error.ValidationException;
import password.pwm.util.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Processes a variety of different commands sent in an HTTP Request, including logoff.
 *
 * @author Jason D. Rivard
 */
public class CommandServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(CommandServlet.class);

// -------------------------- OTHER METHODS --------------------------

    public void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final String action = Validator.readStringFromRequest(req, Constants.PARAM_ACTION_REQUEST, 255);
        LOGGER.trace(pwmSession, "received request for action " + action);

        if (action.equalsIgnoreCase("idleUpdate")) {
            processIdleUpdate(resp);
        } else if (action.equalsIgnoreCase("checkIfResponseConfigNeeded")) {
            processCheckResponses(req, resp);
        } else if (action.equalsIgnoreCase("checkExpire")) {
            processCheckExpire(req, resp);
        } else if (action.equalsIgnoreCase("checkAttributes")) {
            processCheckAttributes(req, resp);
        } else if (action.equalsIgnoreCase("checkAll")) {
            processCheckAll(req, resp);
        } else if (action.equalsIgnoreCase("continue")) {
            processContinue(req, resp);
        } else if (action.equalsIgnoreCase("pageUnload")) {
            processPageUnload(req, resp);
        } else {
            LOGGER.debug(pwmSession, "unknown command sent to CommandServlet: " + action);
            Helper.forwardToErrorPage(req, resp, this.getServletContext());
        }
    }

    private static void processIdleUpdate(
            final HttpServletResponse resp
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmException
    {
        if (!resp.isCommitted()) {
            resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            resp.setContentType("text/plain");
        }
    }

    private static void processCheckResponses(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmException
    {
        if (!preCheckUser(req, resp)) {
            return;
        }

        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        if (!UserStatusHelper.checkIfResponseConfigNeeded(pwmSession, pwmSession.getSessionManager().getActor(),pwmSession.getUserInfoBean().getChallengeSet())) {
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(Constants.URL_SERVLET_SETUP_RESPONSES, req, resp));
        } else {
            processContinue(req, resp);
        }
    }

    private static boolean preCheckUser(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        if (!ssBean.isAuthenticated() && !AuthenticationFilter.authUserUsingBasicHeader(req)) {
            LOGGER.info("checkExpire: authentication required");
            ssBean.setSessionError(Message.ERROR_AUTHENTICATION_REQUIRED.toInfo());
            Helper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
            return false;
        }
        return true;
    }

    private static void processCheckExpire(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmException
    {
        if (!preCheckUser(req, resp)) {
            return;
        }

        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        if (checkIfPasswordExpired(pwmSession) || pwmSession.getUserInfoBean().isRequiresNewPassword()) {
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(Constants.URL_SERVLET_CHANGE_PASSWORD, req, resp));
        } else if (checkPasswordWarn(pwmSession)) {
            final String passwordWarnURL = req.getContextPath() + "/private/" + Constants.URL_JSP_PASSWORD_WARN;
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(passwordWarnURL, req, resp));
        }   else  {
            processContinue(req, resp);
        }
    }

    private static boolean checkIfPasswordExpired(final PwmSession pwmSession) {
        final PasswordStatus passwordState = pwmSession.getUserInfoBean().getPasswordState();
        final StringBuilder sb = new StringBuilder();
        boolean expired = false;
        if (passwordState.isExpired()) {
            sb.append("EXPIRED");
            expired = true;
        } else if (passwordState.isPreExpired()) {
            sb.append("PRE-EXIRED");
            expired = true;
        } else if (passwordState.isViolatesPolicy()) {
            sb.append("POLICY-VIOLATION");
            expired = true;
        }

        if (expired) {
            sb.insert(0,"checkExpire:  password state=");
            sb.append("redirecting to change screen");
            LOGGER.info(pwmSession, sb.toString());
        }

        return expired;
    }

    private static boolean checkPasswordWarn(final PwmSession pwmSession)
    {
        final PasswordStatus passwordState = pwmSession.getUserInfoBean().getPasswordState();
        if ( passwordState.isWarnPeriod()) {
            LOGGER.info(pwmSession, "checkExpire: password expiration is within warn period, redirecting to warn screen");
            return true;
        }
        return false;
    }

    private static void processCheckAttributes(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmException
    {
        if (!preCheckUser(req, resp)) {
            return;
        }

        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        if (!checkAttributes(pwmSession)) {
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(Constants.URL_SERVLET_UPDATE_ATTRIBUTES, req, resp));
        } else {
            processContinue(req, resp);
        }
    }

    private static boolean checkAttributes(
            final PwmSession pwmSession
    )
            throws ChaiUnavailableException, PwmException
    {
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();
        final String userDN = uiBean.getUserDN();

        if (!Helper.testUserMatchQueryString(pwmSession, userDN, pwmSession.getConfig().readSettingAsString(PwmSetting.QUERY_MATCH_UPDATE_USER))) {
            LOGGER.info(pwmSession, "checkAttributes: " + userDN + " is not eligable for checkAttributes due to query match");
            return true;
        }

        final Map<String, ParameterConfig> formParams = pwmSession.getLocaleConfig().getUpdateAttributesAttributes();

        // populate the map with attribute values from the uiBean, which was populated through ldap.
        for (final String key : formParams.keySet()) {
            final ParameterConfig paramConfig = formParams.get(key);
            paramConfig.setValue(uiBean.getAllUserAttributes().getProperty(paramConfig.getAttributeName()));
        }

        try {
            Validator.validateParmValuesMeetRequirements(formParams, pwmSession);
            LOGGER.info(pwmSession, "checkAttributes: " + userDN + " has good attributes");
            return true;
        } catch (ValidationException e) {
            LOGGER.info(pwmSession, "checkAttributes: " + userDN + " does not have good attributes (" + e.getMessage() + ")");
            return false;
        }
    }

    private static void processCheckAll(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmException
    {
        if (!preCheckUser(req, resp)) {
            return;
        }

        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        if (checkIfPasswordExpired(pwmSession)) {
            processCheckExpire(req, resp);
        } else if (!UserStatusHelper.checkIfResponseConfigNeeded(pwmSession, pwmSession.getSessionManager().getActor(),pwmSession.getUserInfoBean().getChallengeSet())) {
            processCheckResponses(req, resp);
        } else if (pwmSession.getConfig().readSettingAsBoolean(PwmSetting.ENABLE_UPDATE_ATTRIBUTES) && !checkAttributes(pwmSession)) {
            processCheckAttributes(req, resp);
        } else {
            processContinue(req, resp);
        }
    }

    private static void processContinue(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();
        final ContextManager theManager = pwmSession.getContextManager();

        //check if user has expired password, and expirecheck during auth is turned on.
        if (ssBean.isAuthenticated()) {
            if (uiBean.isRequiresNewPassword() || (theManager.getConfig().readSettingAsBoolean(PwmSetting.EXPIRE_CHECK_DURING_AUTH) && checkIfPasswordExpired(pwmSession))) {
                if (uiBean.isRequiresNewPassword()) {
                    LOGGER.trace(pwmSession, "user password has been marked as requiring a change");
                } else {
                    LOGGER.debug(pwmSession, "user password appears expired, redirecting to ChangePassword url");
                }
                final String changePassServletURL = theManager.getConfig().readSettingAsString(PwmSetting.URL_SERVET_RELATIVE) + "/public/" + Constants.URL_SERVLET_CHANGE_PASSWORD;

                resp.sendRedirect(SessionFilter.rewriteRedirectURL(changePassServletURL, req, resp));
                return;
            }

            //check if we force response configuration, and user requires it.
            if (uiBean.isRequiresResponseConfig() && (theManager.getConfig().readSettingAsBoolean(PwmSetting.CHALLANGE_FORCE_SETUP))) {
                LOGGER.info(pwmSession, "user response set needs to be configured, redirectiong to setupresponses page");
                final String setupResponsesURL = theManager.getConfig().readSettingAsString(PwmSetting.URL_SERVET_RELATIVE) + "/private/" + Constants.URL_SERVLET_SETUP_RESPONSES;

                resp.sendRedirect(SessionFilter.rewriteRedirectURL(setupResponsesURL, req, resp));
                return;
            }
        }

        // log the user out if our finish action is currently set to log out.
        if (ssBean.getFinishAction() == SessionStateBean.FINISH_ACTION.LOGOUT) {
            LOGGER.trace(pwmSession, "logging out user; password has been modified");
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(Constants.URL_SERVLET_LOGOUT, req, resp));
            return;
        }

        String redirectURL = ssBean.getForwardURL();
        if (redirectURL == null || redirectURL.length() < 1) {
            redirectURL = theManager.getConfig().readSettingAsString(PwmSetting.URL_FORWARD);
        }

        LOGGER.trace(pwmSession, "redirecting user to forward url: " + redirectURL);
        resp.sendRedirect(SessionFilter.rewriteRedirectURL(redirectURL,req, resp));
    }

    private void processPageUnload(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        if (pwmSession.getSessionStateBean().isAuthenticated()) {
            pwmSession.getSessionStateBean().setLastPageUnloadTime(System.currentTimeMillis());
        }
        resp.flushBuffer();
    }
}

