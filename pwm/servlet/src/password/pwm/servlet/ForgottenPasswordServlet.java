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

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.*;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.ForgottenPasswordBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.SmsItemBean;
import password.pwm.config.*;
import password.pwm.error.*;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.CrUtility;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 * User interaction servlet for recovering user's password using secret question/answer
 *
 * @author Jason D. Rivard
 */
public class ForgottenPasswordServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ForgottenPasswordServlet.class);

    private static final String TOKEN_NAME = ForgottenPasswordServlet.class.getName();

// -------------------------- OTHER METHODS --------------------------

    public void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final Configuration config = pwmApplication.getConfig();
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();

        if (!config.readSettingAsBoolean(PwmSetting.FORGOTTEN_PASSWORD_ENABLE)) {
            PwmSession.getPwmSession(req).getSessionStateBean().setSessionError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (pwmSession.getSessionStateBean().isAuthenticated()) {
            PwmSession.getPwmSession(req).getSessionStateBean().setSessionError(PwmError.ERROR_USERAUTHENTICATED.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (forgottenPasswordBean.getProxiedUser() != null) {
            pwmApplication.getIntruderManager().checkUser(forgottenPasswordBean.getProxiedUser().getEntryDN(), pwmSession);
        }

        final String processAction = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST, 255);

        // convert a url command like /pwm/public/ForgottenPassword/12321321 to redirect with a process action.
        if (processAction == null || processAction.length() < 1) {
            if (convertURLtokenCommand(req, resp, pwmSession)) {
                return;
            }
        }

        if (processAction != null && processAction.length() > 0) {
            Validator.validatePwmFormID(req);

            final boolean tokenEnabled = config.readSettingAsBoolean(PwmSetting.CHALLENGE_TOKEN_ENABLE);
            final boolean tokenNeeded = tokenEnabled && !forgottenPasswordBean.isTokenSatisfied();
            final boolean responsesEnabled = config.readSettingAsBoolean(PwmSetting.CHALLENGE_REQUIRE_RESPONSES) || !config.readSettingAsForm(PwmSetting.CHALLENGE_REQUIRED_ATTRIBUTES, userLocale).isEmpty();
            final boolean responsesNeeded = responsesEnabled && !forgottenPasswordBean.isResponsesSatisfied();

            if (processAction.equalsIgnoreCase("search")) {
                this.processSearch(req, resp);
                return;
            } else if (processAction.equalsIgnoreCase("checkResponses")) {
                this.processCheckResponses(req, resp);
                return;
            } else if (processAction.equalsIgnoreCase("enterCode")) {
                this.processEnterForgottenCode(req, resp);
                return;
            } else if (!tokenNeeded && !responsesNeeded && processAction.equalsIgnoreCase("selectUnlock")) {
                this.processUnlock(req, resp);
                return;
            } else if (!tokenNeeded && !responsesNeeded && processAction.equalsIgnoreCase("selectResetPassword")) {
                this.processResetPassword(req, resp);
                return;
            }
        }
        this.forwardToSearchJSP(req, resp);
    }

    private void processSearch(final HttpServletRequest req, final HttpServletResponse resp)
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);

        final String usernameParam = Validator.readStringFromRequest(req, "username", 256);
        final String contextParam = Validator.readStringFromRequest(req, "context", 256);

        // clear the bean
        pwmSession.clearForgottenPasswordBean();
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();

        // convert the username field to a DN.
        final String userDN;
        try {
            userDN = UserStatusHelper.convertUsernameFieldtoDN(usernameParam, pwmSession, pwmApplication, contextParam);
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES,e.getErrorInformation().getDetailedErrorMsg(),e.getErrorInformation().getFieldValues());
            pwmApplication.getIntruderManager().addBadUserAttempt(usernameParam, pwmSession);
            pwmApplication.getIntruderManager().checkUser(usernameParam, pwmSession);
            pwmSession.getSessionStateBean().setSessionError(errorInfo);
            LOGGER.debug(pwmSession,errorInfo.toDebugStr());
            pwmApplication.getIntruderManager().delayPenalty(usernameParam, pwmSession);
            this.forwardToSearchJSP(req, resp);
            return;
        }

        final String forgottenPasswordQueryMatch = pwmApplication.getConfig().readSettingAsString(PwmSetting.FORGOTTEN_PASSWORD_QUERY_MATCH);
        if (forgottenPasswordQueryMatch != null && forgottenPasswordQueryMatch.length() > 0) {
            if (!Helper.testUserMatchQueryString(pwmApplication.getProxyChaiProvider(), userDN, forgottenPasswordQueryMatch)) {
                final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"does not match forgotten password query match");
                LOGGER.info(pwmSession, errorInfo.toDebugStr());
                pwmSession.getSessionStateBean().setSessionError(errorInfo);
                ServletHelper.forwardToErrorPage(req,resp,true);
                return;
            }
        }

        final ChaiUser proxiedUser = ChaiFactory.createChaiUser(userDN, pwmApplication.getProxyChaiProvider());
        forgottenPasswordBean.setProxiedUser(proxiedUser);
        this.advancedToNextStage(req, resp);
    }

    private void processEnterForgottenCode(final HttpServletRequest req, final HttpServletResponse resp)
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);

        final String userEnteredCode = Validator.readStringFromRequest(req, "code");

        boolean tokenPass = false;
        final String userDN;
        try {
            TokenManager.TokenPayload tokenPayload = pwmApplication.getTokenManager().retrieveTokenData(userEnteredCode);
            if (tokenPayload != null) {
                if (!TOKEN_NAME.equals(tokenPayload.getName()) && pwmApplication.getTokenManager().supportsName()) {
                    throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,"incorrect token/name format"));
                }
                final String dnFromToken = tokenPayload.getUserDN();
                {
                    final ChaiUser proxiedUser = forgottenPasswordBean.getProxiedUser();
                    if (proxiedUser == null) {
                        userDN = dnFromToken;
                        if (userDN != null) {
                            tokenPass = true;
                        }
                    } else {
                        final String proxiedUserDN = proxiedUser.getEntryDN();
                        userDN = proxiedUserDN == null ? dnFromToken : proxiedUserDN;
                        if (proxiedUserDN != null && proxiedUserDN.equals(dnFromToken)) {
                            tokenPass = true;
                        } else {
                            LOGGER.warn(pwmSession, "user in session '" + proxiedUserDN + "' entered code for user '" + dnFromToken + "', counting as invalid attempt");
                        }
                    }
                }
            } else {
                userDN = forgottenPasswordBean.getProxiedUser() == null ? null : forgottenPasswordBean.getProxiedUser().getEntryDN();
            }
        } catch (PwmOperationalException e) {
            final String errorMsg = "unexpected error attempting to read token from storage: " + e.getMessage();
            LOGGER.error(errorMsg);
            pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(e.getError(),e.getMessage()));
            this.forwardToEnterCodeJSP(req, resp);
            return;
        }

        if (tokenPass) {
            final ChaiUser proxiedUser = ChaiFactory.createChaiUser(userDN,pwmApplication.getProxyChaiProvider());
            forgottenPasswordBean.setProxiedUser(proxiedUser);
            forgottenPasswordBean.setTokenSatisfied(true);
            pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_PASSED);
            LOGGER.debug(pwmSession, "token validation has been passed");
            this.advancedToNextStage(req, resp);
            return;
        }

        LOGGER.debug(pwmSession, "token validation has failed");
        pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT));
        pwmApplication.getIntruderManager().addBadUserAttempt(userDN, pwmSession);
        pwmApplication.getIntruderManager().addBadAddressAttempt(pwmSession);
        simulateBadLogin(pwmApplication, pwmSession, userDN);
        pwmApplication.getIntruderManager().delayPenalty(userDN, pwmSession);
        this.forwardToEnterCodeJSP(req, resp);
    }

    private static void loadResponsesIntoBean(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        // retrieve the responses for the user from ldap
        final ChaiUser theUser = forgottenPasswordBean.getProxiedUser();
        final ResponseSet responseSet = CrUtility.readUserResponseSet(pwmSession, pwmApplication, theUser);

        if (responseSet != null) {
            LOGGER.trace("loaded responseSet from user: " + responseSet.toString());

            Locale responseSetLocale = pwmSession.getSessionStateBean().getLocale();
            try {
                responseSetLocale = responseSet.getLocale();
            } catch (Exception e) {
                LOGGER.error("error retrieving locale from stored responseSet, will use browser locale instead: " + e.getMessage());
            }

            // read the user's assigned response set.
            final ChallengeSet challengeSet = CrUtility.readUserChallengeSet(pwmSession, pwmApplication.getConfig(), theUser, null, responseSetLocale);

            try {
                if (responseSet.meetsChallengeSetRequirements(challengeSet)) {
                    if (!challengeSet.getRequiredChallenges().isEmpty() || (challengeSet.getMinRandomRequired() > 0)) {
                        forgottenPasswordBean.setChallengeSet(responseSet.getPresentableChallengeSet());
                        forgottenPasswordBean.setProxiedUser(theUser);
                        return;
                    } else {
                        final String errorMsg = "configured challenge set policy for " + theUser.getEntryDN() + " is empty, user not qualified to recover password";
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NO_CHALLENGES, errorMsg);
                        throw new PwmOperationalException(errorInformation);
                    }
                }
            } catch (ChaiValidationException e) {
                final String errorMsg = "stored response set for user '" + theUser.getEntryDN() + "' do not meet current challenge set requirements: " + e.getLocalizedMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES, errorMsg);
                throw new PwmOperationalException(errorInformation);
            }
        }

        forgottenPasswordBean.setChallengeSet(null);
        forgottenPasswordBean.setProxiedUser(null);

        final String errorMsg = "could not find a response set for " + theUser.getEntryDN();
        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES, errorMsg);
        throw new PwmOperationalException(errorInformation);
    }

    private void forwardToResponsesJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_RECOVER_PASSWORD_RESPONSES).forward(req, resp);
    }

    private void processCheckResponses(final HttpServletRequest req, final HttpServletResponse resp)
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();
        final Configuration config = pwmApplication.getConfig();

        final ChaiUser theUser = forgottenPasswordBean.getProxiedUser();

        if (theUser == null) {
            this.forwardToSearchJSP(req, resp);
            return;
        }

        try { // check attributes
            validateRequiredAttributes(theUser, req, pwmSession);
        } catch (PwmDataValidationException e) {
            ssBean.setSessionError(e.getErrorInformation());
            LOGGER.debug(pwmSession, "incorrect attribute value during check for " + theUser.getEntryDN());
            pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_FAILURES);
            pwmApplication.getIntruderManager().addBadAddressAttempt(pwmSession);
            pwmApplication.getIntruderManager().addBadUserAttempt(forgottenPasswordBean.getProxiedUser().getEntryDN(), pwmSession);
            simulateBadLogin(pwmApplication, pwmSession, theUser.getEntryDN());
            this.forwardToResponsesJSP(req, resp);
            return;
        }

        if (config.readSettingAsBoolean(PwmSetting.CHALLENGE_REQUIRE_RESPONSES)) {
            try {
                // read the supplied responses from the user
                final Map<Challenge, String> crMap = readResponsesFromHttpRequest(req, forgottenPasswordBean.getChallengeSet());

                final ResponseSet responseSet = CrUtility.readUserResponseSet(pwmSession, pwmApplication, theUser);

                final boolean responsesSatisfied = responseSet.test(crMap);
                forgottenPasswordBean.setResponsesSatisfied(responsesSatisfied);

                if (responsesSatisfied) {
                    // update the status bean
                    pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_SUCCESSES);
                    LOGGER.debug(pwmSession, "user '" + theUser.getEntryDN() + "' has supplied correct responses");
                } else {
                    final String errorMsg = "incorrect response to one or more challenges";
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE, errorMsg);
                    ssBean.setSessionError(errorInformation);
                    LOGGER.debug(pwmSession,errorInformation.toDebugStr());
                    pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_FAILURES);
                    pwmApplication.getIntruderManager().addBadAddressAttempt(pwmSession);
                    pwmApplication.getIntruderManager().addBadUserAttempt(forgottenPasswordBean.getProxiedUser().getEntryDN(), pwmSession);
                    simulateBadLogin(pwmApplication, pwmSession, theUser.getEntryDN());
                    pwmApplication.getIntruderManager().delayPenalty(theUser.getEntryDN(), pwmSession);
                    this.forwardToResponsesJSP(req, resp);
                    return;
                }
            } catch (ChaiValidationException e) {
                LOGGER.debug(pwmSession, "chai validation error checking user responses: " + e.getMessage());
                ssBean.setSessionError(new ErrorInformation(PwmError.forChaiError(e.getErrorCode())));
                this.forwardToResponsesJSP(req, resp);
                return;
            }
        }

        forgottenPasswordBean.setResponsesSatisfied(true);

        this.advancedToNextStage(req, resp);
    }

    private void advancedToNextStage(final HttpServletRequest req, final HttpServletResponse resp)
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final Configuration config = pwmApplication.getConfig();
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();

        // check for proxied user;
        if (forgottenPasswordBean.getProxiedUser() == null) {
            this.forwardToSearchJSP(req, resp);
            return;
        }

        // check for attribute form in bean.
        if (forgottenPasswordBean.getAttributeForm() == null) {
            try {
                final List<FormConfiguration> form = figureAttributeForm(pwmApplication, pwmSession, forgottenPasswordBean.getProxiedUser());
                forgottenPasswordBean.setAttributeForm(form);
            } catch (PwmOperationalException e) {
                pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
                LOGGER.debug(pwmSession, e.getErrorInformation().toDebugStr());
                this.forwardToSearchJSP(req, resp);
                return;
            }
        }

        // if responses are required, and user has responses, then send to response screen.
        if (config.readSettingAsBoolean(PwmSetting.CHALLENGE_REQUIRE_RESPONSES)) {
            if (forgottenPasswordBean.getChallengeSet() == null) {
                try {
                    loadResponsesIntoBean(pwmSession, pwmApplication, forgottenPasswordBean);
                } catch (PwmOperationalException e) {
                    pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
                    LOGGER.debug(pwmSession, e.getErrorInformation().toDebugStr());
                    this.forwardToSearchJSP(req, resp);
                    return;
                }
            }

            if (!forgottenPasswordBean.isResponsesSatisfied()) {
                this.forwardToResponsesJSP(req, resp);
                return;
            }
        }

        // if responses aren't required, but attributes are, send to response screen anyway
        {
            final List<FormConfiguration> requiredAttributesForm = config.readSettingAsForm(PwmSetting.CHALLENGE_REQUIRED_ATTRIBUTES, pwmSession.getSessionStateBean().getLocale());

            if (!requiredAttributesForm.isEmpty() && !forgottenPasswordBean.isResponsesSatisfied()) {
                this.forwardToResponsesJSP(req, resp);
                return;
            }
        }

        // process for token-enabled recovery
        if (config.readSettingAsBoolean(PwmSetting.CHALLENGE_TOKEN_ENABLE)) {
            if (!forgottenPasswordBean.isTokenSatisfied()) {
                this.initializeToken(pwmSession, pwmApplication, forgottenPasswordBean.getProxiedUser());
                this.forwardToEnterCodeJSP(req, resp);
                return;
            }
        }

        // sanity check, shouldn't be possible to get here unless.....
        if (!forgottenPasswordBean.isTokenSatisfied() && !forgottenPasswordBean.isResponsesSatisfied()) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, "trying to advance through forgotten password, but responses and tokens are unsatisifed, perhaps both are disabled?"));
        }

        forgottenPasswordBean.setAllPassed(true);

        if (config.readSettingAsBoolean(PwmSetting.CHALLENGE_ALLOW_UNLOCK)) {
            final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(pwmApplication, pwmSession, forgottenPasswordBean.getProxiedUser(), pwmSession.getSessionStateBean().getLocale());
            final PasswordStatus passwordStatus = UserStatusHelper.readPasswordStatus(pwmSession, null, pwmApplication, forgottenPasswordBean.getProxiedUser(), passwordPolicy);

            if (!passwordStatus.isExpired() && !passwordStatus.isPreExpired()) {
                try {
                    if (forgottenPasswordBean.getProxiedUser().isLocked()) {
                        this.forwardToChoiceJSP(req, resp);
                        return;
                    }
                } catch (ChaiOperationException e) {
                    LOGGER.error(pwmSession, "chai operation error checking user lock status: " + e.getMessage());
                }
            }
        }

        this.processResetPassword(req, resp);
    }


    private void processUnlock(final HttpServletRequest req, final HttpServletResponse resp)
            throws IOException, ServletException, ChaiUnavailableException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();

        final ChaiUser theUser = forgottenPasswordBean.getProxiedUser();
        try {
            theUser.unlock();
            pwmSession.getSessionStateBean().setSessionSuccess(Message.SUCCESS_UNLOCK_ACCOUNT, null);
            ServletHelper.forwardToSuccessPage(req, resp);
            return;
        } catch (ChaiOperationException e) {
            final String errorMsg = "unable to unlock user " + theUser.getEntryDN() + " error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNLOCK_FAILURE,errorMsg);
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
            LOGGER.error(pwmSession, errorInformation.toDebugStr());
        }

        this.forwardToChoiceJSP(req, resp);
    }


    private void processResetPassword(final HttpServletRequest req, final HttpServletResponse resp)
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();

        if (!forgottenPasswordBean.isAllPassed()) {
            this.advancedToNextStage(req, resp);
            return;
        }

        final ChaiUser theUser = forgottenPasswordBean.getProxiedUser();

        try { // try unlocking user
            theUser.unlock();
            LOGGER.trace(pwmSession, "unlock account succeeded");
        } catch (ChaiOperationException e) {
            final String errorMsg = "unable to unlock user " + theUser.getEntryDN() + " error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNLOCK_FAILURE,errorMsg);
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
            LOGGER.error(pwmSession, errorInformation.toDebugStr());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        try {
            AuthenticationFilter.authUserWithUnknownPassword(theUser, pwmSession, pwmApplication, req.isSecure());

            LOGGER.info(pwmSession, "user successfully supplied password recovery responses, forward to change password page: " + theUser.getEntryDN());

            // mark the event log
            UserHistory.updateUserHistory(pwmSession, pwmApplication, UserHistory.Record.Event.RECOVER_PASSWORD, null);

            // redirect user to change password screen.
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(PwmConstants.URL_SERVLET_CHANGE_PASSWORD, req, resp));
        } catch (PwmUnrecoverableException e) {
            LOGGER.warn("unexpected error authenticating during forgotten password recovery process user: " + e.getMessage());
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
        }
    }

    public static Map<Challenge, String> readResponsesFromHttpRequest(
            final HttpServletRequest req,
            final ChallengeSet challengeSet)
            throws ChaiValidationException, ChaiUnavailableException, PwmUnrecoverableException {
        final Map<Challenge, String> responses = new LinkedHashMap<Challenge, String>();

        int counter = 0;
        for (final Challenge loopChallenge : challengeSet.getChallenges()) {
            counter++;
            final String answer = Validator.readStringFromRequest(req, PwmConstants.PARAM_RESPONSE_PREFIX + counter, 1024);

            responses.put(loopChallenge, answer.length() > 0 ? answer : "");
        }

        return responses;
    }

    private void forwardToSearchJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_RECOVER_PASSWORD_SEARCH).forward(req, resp);
    }

    private void forwardToChoiceJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_RECOVER_PASSWORD_CHOICE).forward(req, resp);
    }

    private void forwardToEnterCodeJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_RECOVER_PASSWORD_ENTER_CODE).forward(req, resp);
    }

    private void validateRequiredAttributes(final ChaiUser theUser, final HttpServletRequest req, final PwmSession pwmSession)
            throws ChaiUnavailableException, PwmDataValidationException, PwmUnrecoverableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();

        final List<FormConfiguration> requiredAttributesForm = forgottenPasswordBean.getAttributeForm();

        if (requiredAttributesForm.isEmpty()) {
            return;
        }

        final Map<FormConfiguration,String> formValues = Validator.readFormValuesFromRequest(req, requiredAttributesForm);
        for (final FormConfiguration paramConfig : formValues.keySet()) {
            final String attrName = paramConfig.getAttributeName();

            try {
                if (theUser.compareStringAttribute(attrName, formValues.get(paramConfig))) {
                    LOGGER.trace(pwmSession, "successful validation of ldap attribute value for '" + attrName + "'");
                } else {
                    throw new PwmDataValidationException(new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE, "incorrect value for '" + attrName + "'", attrName));
                }
            } catch (ChaiOperationException e) {
                LOGGER.error(pwmSession, "error during param validation of '" + attrName + "', error: " + e.getMessage());
                throw new PwmDataValidationException(new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE, "ldap error testing value for '" + attrName + "'", attrName));
            }
        }
    }

    public void initializeToken(final PwmSession pwmSession, final PwmApplication pwmApplication, final ChaiUser proxiedUser)
            throws PwmUnrecoverableException {
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();
        final Configuration config = pwmApplication.getConfig();

        final String token;
        try {
            final TokenManager.TokenPayload tokenPayload = new TokenManager.TokenPayload(TOKEN_NAME, Collections.<String,String>emptyMap(), proxiedUser.getEntryDN());
            token = pwmApplication.getTokenManager().generateNewToken(tokenPayload);
        } catch (PwmOperationalException e) {
            throw new PwmUnrecoverableException(e.getErrorInformation());
        }
        LOGGER.debug(pwmSession, "generated token code for session: " + token);

        final StringBuilder tokenSendDisplay = new StringBuilder();
        String toEmailAddr = null, toSmsAddr = null;
        try {
            toEmailAddr = proxiedUser.readStringAttribute(config.readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE));
            if (toEmailAddr != null && toEmailAddr.length() > 0) {
                tokenSendDisplay.append(toEmailAddr);
            }
        } catch (Exception e) {
            LOGGER.debug("error reading mail attribute from user '" + proxiedUser.getEntryDN() + "': " + e.getMessage());
        }

        final String toSmsNumber;
        try {
            toSmsNumber = proxiedUser.readStringAttribute(config.readSettingAsString(PwmSetting.SMS_USER_PHONE_ATTRIBUTE));
            if (toSmsNumber !=null && toSmsNumber.length() > 0) {
                if (tokenSendDisplay.length() > 0) {
                    tokenSendDisplay.append(" / ");
                }
                tokenSendDisplay.append(toSmsNumber);
            }
        } catch (Exception e) {
            LOGGER.debug("error reading SMS attribute from user '" + proxiedUser.getEntryDN() + "': " + e.getMessage());
        }
        forgottenPasswordBean.setTokenSendAddress(tokenSendDisplay.toString());

        sendToken(pwmSession, pwmApplication, proxiedUser, token, toEmailAddr, toSmsAddr);
    }

    private void sendToken(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final ChaiUser proxiedUser,
            final String tokenKey,
            final String toEmailAddr,
            final String toSmsAddr

    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        final PwmSetting.SmsPriority pref = PwmSetting.SmsPriority.valueOf(config.readSettingAsString(PwmSetting.CHALLENGE_TOKEN_SEND_METHOD));
        final boolean success;
        switch (pref) {
            case BOTH:
                // Send both email and SMS, success if one of both succeeds
                final boolean suc1 = sendEmailToken(pwmSession, pwmApplication, proxiedUser,tokenKey,toEmailAddr);
                final boolean suc2 = sendSmsToken(pwmSession, pwmApplication, proxiedUser,tokenKey, toSmsAddr);
                success = suc1 || suc2;
                break;
            case EMAILFIRST:
                // Send email first, try SMS if email is not available
                success = sendEmailToken(pwmSession, pwmApplication, proxiedUser,tokenKey, toEmailAddr) || sendSmsToken(pwmSession, pwmApplication, proxiedUser, tokenKey, toSmsAddr);
                break;
            case SMSFIRST:
                // Send SMS first, try email if SMS is not available
                success = sendSmsToken(pwmSession, pwmApplication, proxiedUser, tokenKey, toSmsAddr) || sendEmailToken(pwmSession, pwmApplication, proxiedUser, tokenKey, toEmailAddr);
                break;
            case SMSONLY:
                // Only try SMS
                success = sendSmsToken(pwmSession, pwmApplication, proxiedUser, tokenKey, toSmsAddr);
                break;
            case EMAILONLY:
            default:
                // Only try email
                success = sendEmailToken(pwmSession, pwmApplication, proxiedUser, tokenKey, toEmailAddr);
                break;
        }
        if (!success) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_TOKEN_MISSING_CONTACT));
        }
    }

    private boolean sendEmailToken(final PwmSession pwmSession, final PwmApplication pwmApplication, final ChaiUser proxiedUser, final String tokenKey, final String toAddress)
            throws PwmUnrecoverableException
    {
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final Configuration config = pwmApplication.getConfig();
        final String fromAddress = config.readSettingAsLocalizedString(PwmSetting.EMAIL_CHALLENGE_TOKEN_FROM, userLocale);
        final String subject = config.readSettingAsLocalizedString(PwmSetting.EMAIL_CHALLENGE_TOKEN_SUBJECT, userLocale);
        String plainBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_CHALLENGE_TOKEN_BODY, userLocale);
        String htmlBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_CHALLENGE_TOKEN_BODY_HTML, userLocale);

        if (toAddress == null || toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send token email for '" + proxiedUser.getEntryDN() + "' no email address available in ldap");
            return false;
        }

        plainBody = plainBody.replaceAll("%TOKEN%", tokenKey);
        htmlBody = htmlBody.replaceAll("%TOKEN%", tokenKey);

        final EmailItemBean emailItem = new EmailItemBean(toAddress, fromAddress, subject, plainBody, htmlBody);
        pwmApplication.sendEmailUsingQueue(emailItem, pwmSession.getUserInfoBean());
        pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_SENT);
        LOGGER.debug(pwmSession, "token email added to send queue for " + toAddress);
        return true;
    }

    private boolean sendSmsToken(final PwmSession pwmSession, final PwmApplication theManager, final ChaiUser proxiedUser, final String token, final String toSmsNumber)
            throws PwmUnrecoverableException
    {
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final Configuration config = theManager.getConfig();
        String senderId = config.readSettingAsString(PwmSetting.SMS_SENDER_ID);
        if (senderId == null) { senderId = ""; }
        String message = config.readSettingAsLocalizedString(PwmSetting.SMS_CHALLENGE_TOKEN_TEXT, userLocale);

        if (toSmsNumber == null || toSmsNumber.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send token sms for '" + proxiedUser.getEntryDN() + "' no SMS number available in ldap");
            return false;
        }

        message = message.replaceAll("%TOKEN%", token);

        final Integer maxlen = ((Long) config.readSettingAsLong(PwmSetting.SMS_MAX_TEXT_LENGTH)).intValue();
        theManager.sendSmsUsingQueue(new SmsItemBean(toSmsNumber, senderId, message, maxlen, userLocale), pwmSession.getUserInfoBean());
        theManager.getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_SENT);
        LOGGER.debug(pwmSession, "token SMS added to send queue for " + toSmsNumber);
        return true;
    }

    private static void simulateBadLogin(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final String userDN
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        if (userDN == null) {
            return;
        }


        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.SECURITY_SIMULATE_LDAP_BAD_PASSWORD)) {

            try {
                LOGGER.trace(pwmSession, "performing bad-password login attempt against ldap directory as a result of forgotten password recovery invalid attempt against " + userDN);
                AuthenticationFilter.testCredentials(userDN, PwmConstants.DEFAULT_BAD_PASSWORD_ATTEMPT, pwmSession, pwmApplication);
                LOGGER.warn(pwmSession, "bad-password login attempt succeeded for " + userDN + "! (this should always fail)");
            } catch (PwmOperationalException e) {
                if (e.getError() == PwmError.ERROR_WRONGPASSWORD) {
                    LOGGER.trace(pwmSession, "bad-password login attempt succeeded for; " + userDN +" result: " + e.getMessage());
                } else {
                    LOGGER.debug(pwmSession, "unexpected error during bad-password login attempt for " + userDN + "; result: " + e.getMessage());
                }
            }
        }
    }

    private List<FormConfiguration> figureAttributeForm(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final ChaiUser theUser
    )
            throws ChaiUnavailableException, PwmOperationalException
    {
        final List<FormConfiguration> requiredAttributesForm = pwmApplication.getConfig().readSettingAsForm(PwmSetting.CHALLENGE_REQUIRED_ATTRIBUTES, pwmSession.getSessionStateBean().getLocale());
        if (requiredAttributesForm.isEmpty()) {
            return requiredAttributesForm;
        }

        final List<FormConfiguration> returnList = new ArrayList<FormConfiguration>();
        for (final FormConfiguration formConfiguration : requiredAttributesForm) {
            if (formConfiguration.isRequired()) {
                returnList.add(formConfiguration);
            } else {
                try {
                    final String currentValue = theUser.readStringAttribute(formConfiguration.getAttributeName());
                    if (currentValue != null && currentValue.length() > 0) {
                        returnList.add(formConfiguration);
                    } else {
                        LOGGER.trace(pwmSession, "excluding optional required attribute(" + formConfiguration.getAttributeName() + "), user has no value");
                    }
                } catch (ChaiOperationException e) {
                    throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_NO_CHALLENGES, "unexpected error reading value for attribute " + formConfiguration.getAttributeName()));
                }
            }
        }

        if (returnList.isEmpty()) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_NO_CHALLENGES, "user has no values for any optional attribute"));
        }

        return returnList;
    }
}

