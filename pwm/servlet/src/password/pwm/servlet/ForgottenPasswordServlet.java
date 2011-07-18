/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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
import password.pwm.util.PwmRandom;
import password.pwm.util.ServletHelper;
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

// -------------------------- OTHER METHODS --------------------------

    public void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ContextManager theManager = pwmSession.getContextManager();
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();

        if (!theManager.getConfig().readSettingAsBoolean(PwmSetting.FORGOTTEN_PASSWORD_ENABLE)) {
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
            theManager.getIntruderManager().checkUser(forgottenPasswordBean.getProxiedUser().getEntryDN(), pwmSession);
        }

        final String processAction = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST, 255);

        // convert a url command like /pwm/public/ForgottenPassword/12321321 to redirect with a process action.
        if (processAction == null || processAction.length() < 1) {
            if (checkForURLcommand(req, resp, pwmSession)) {
                return;
            }
        }

        if (processAction != null && processAction.length() > 0) {
            Validator.validatePwmFormID(req);

            final boolean tokenEnabled = pwmSession.getConfig().readSettingAsBoolean(PwmSetting.CHALLENGE_TOKEN_ENABLE);
            final boolean tokenNeeded = tokenEnabled && !forgottenPasswordBean.isTokenSatisfied();
            final boolean responsesNeeded = !forgottenPasswordBean.isResponsesSatisfied();

            if (processAction.equalsIgnoreCase("search")) {
                this.processSearch(req, resp);
                return;
            } else if (processAction.equalsIgnoreCase("checkResponses")) {
                this.processCheckResponses(req, resp);
                return;
            } else if (processAction.equalsIgnoreCase("forgottenCode")) {
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
        final ContextManager theManager = pwmSession.getContextManager();

        final String usernameParam = Validator.readStringFromRequest(req, "username", 256);
        final String contextParam = Validator.readStringFromRequest(req, "context", 256);

        // clear the bean
        pwmSession.clearForgottenPasswordBean();
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();

        // convert the username field to a DN.
        final String userDN;
        try {
            userDN = UserStatusHelper.convertUsernameFieldtoDN(usernameParam, pwmSession, contextParam);
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES,e.getErrorInformation().getDetailedErrorMsg(),e.getErrorInformation().getFieldValues());
            theManager.getIntruderManager().addBadUserAttempt(usernameParam, pwmSession);
            theManager.getIntruderManager().checkUser(usernameParam, pwmSession);
            pwmSession.getSessionStateBean().setSessionError(errorInfo);
            LOGGER.debug(pwmSession,errorInfo.toDebugStr());
            Helper.pause(PwmRandom.getInstance().nextInt(2 * 1000) + 1000); // delay penalty of 1-3 seconds
            this.forwardToSearchJSP(req, resp);
            return;
        }

        final ChaiUser proxiedUser = ChaiFactory.createChaiUser(userDN, theManager.getProxyChaiProvider());
        forgottenPasswordBean.setProxiedUser(proxiedUser);

        this.advancedToNextStage(req, resp);
    }

    private void processEnterForgottenCode(final HttpServletRequest req, final HttpServletResponse resp)
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();

        if (forgottenPasswordBean.getProxiedUser() == null || forgottenPasswordBean.getToken() == null) {
            pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_TOKEN_EXPIRED));
            pwmSession.getContextManager().getIntruderManager().addBadAddressAttempt(pwmSession);
            Helper.pause(PwmRandom.getInstance().nextInt(2 * 1000) + 1000); // delay penalty of 1-3 seconds
            this.advancedToNextStage(req, resp);
            return;
        }

        final String recoverCode = Validator.readStringFromRequest(req, "code", 10 * 1024);

        final boolean codeIsCorrect = (recoverCode != null) && recoverCode.equalsIgnoreCase(forgottenPasswordBean.getToken());

        if (codeIsCorrect) {
            forgottenPasswordBean.setTokenSatisfied(true);
            pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_PASSED);
            LOGGER.debug(pwmSession, "token validation has been passed");
            this.advancedToNextStage(req, resp);
            return;
        }

        LOGGER.debug(pwmSession, "token validation has failed");
        pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT));
        pwmSession.getContextManager().getIntruderManager().addBadUserAttempt(forgottenPasswordBean.getProxiedUser().getEntryDN(), pwmSession);
        pwmSession.getContextManager().getIntruderManager().addBadAddressAttempt(pwmSession);
        Helper.pause(PwmRandom.getInstance().nextInt(2 * 1000) + 1000); // delay penalty of 1-3 seconds
        this.forwardToEnterCodeJSP(req, resp);
    }

    private static void loadResponsesIntoBean(final PwmSession pwmSession, final ForgottenPasswordBean forgottenPasswordBean)
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException {
        // retrieve the responses for the user from ldap
        final ChaiUser theUser = forgottenPasswordBean.getProxiedUser();
        final ResponseSet responseSet = CrUtility.readUserResponseSet(pwmSession, theUser);

        if (responseSet != null) {
            LOGGER.trace("loaded responseSet from user: " + responseSet.toString());

            Locale responseSetLocale = pwmSession.getSessionStateBean().getLocale();
            try {
                responseSetLocale = responseSet.getLocale();
            } catch (Exception e) {
                LOGGER.error("error retrieving locale from stored responseSet, will use browser locale instead: " + e.getMessage());
            }

            // read the user's assigned response set.
            final ChallengeSet challengeSet = CrUtility.readUserChallengeSet(pwmSession, theUser, null, responseSetLocale);

            try {
                if (responseSet.meetsChallengeSetRequirements(challengeSet)) {
                    if (!challengeSet.getRequiredChallenges().isEmpty() || (challengeSet.getMinRandomRequired() > 0)) {
                        forgottenPasswordBean.setResponseSet(responseSet);
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

        forgottenPasswordBean.setResponseSet(null);
        forgottenPasswordBean.setProxiedUser(null);

        final String errorMsg = "could not find a response set for " + theUser.getEntryDN();
        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES, errorMsg);
        throw new PwmOperationalException(errorInformation);
    }

    private void forwardToResponsesJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_RECOVER_PASSWORD_RESPONSES).forward(req, resp);
    }

    private void processCheckResponses(final HttpServletRequest req, final HttpServletResponse resp)
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ContextManager theManager = pwmSession.getContextManager();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();
        final Configuration config = pwmSession.getConfig();

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
            theManager.getStatisticsManager().incrementValue(Statistic.RECOVERY_FAILURES);
            theManager.getIntruderManager().addBadAddressAttempt(pwmSession);
            theManager.getIntruderManager().addBadUserAttempt(forgottenPasswordBean.getProxiedUser().getEntryDN(), pwmSession);
            this.forwardToResponsesJSP(req, resp);
            return;
        }

        if (config.readSettingAsBoolean(PwmSetting.CHALLENGE_REQUIRE_RESPONSES)) {
            try {
                // read the supplied responses from the user
                final Map<Challenge, String> crMap = readResponsesFromHttpRequest(req, forgottenPasswordBean.getResponseSet().getChallengeSet());

                final ResponseSet responseSet = forgottenPasswordBean.getResponseSet();

                final boolean responsesSatisfied = responseSet.test(crMap);
                forgottenPasswordBean.setResponsesSatisfied(responsesSatisfied);

                if (responsesSatisfied) {
                    // update the status bean
                    theManager.getStatisticsManager().incrementValue(Statistic.RECOVERY_SUCCESSES);
                    LOGGER.debug(pwmSession, "user '" + theUser.getEntryDN() + "' has supplied correct responses");
                } else {
                    final String errorMsg = "incorrect response to one or more challenges";
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE, errorMsg);
                    ssBean.setSessionError(errorInformation);
                    LOGGER.debug(pwmSession,errorInformation.toDebugStr());
                    theManager.getStatisticsManager().incrementValue(Statistic.RECOVERY_FAILURES);
                    theManager.getIntruderManager().addBadAddressAttempt(pwmSession);
                    theManager.getIntruderManager().addBadUserAttempt(forgottenPasswordBean.getProxiedUser().getEntryDN(), pwmSession);
                    Helper.pause(PwmRandom.getInstance().nextInt(2 * 1000) + 1000); // delay penalty of 1-3 seconds
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
        final Configuration config = pwmSession.getConfig();
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();

        // check for proxied user;
        if (forgottenPasswordBean.getProxiedUser() == null) {
            this.forwardToSearchJSP(req, resp);
            return;
        }

        // if responses are required, and user has responses, then send to response screen.
        if (config.readSettingAsBoolean(PwmSetting.CHALLENGE_REQUIRE_RESPONSES)) {
            if (forgottenPasswordBean.getResponseSet() == null) {
                try {
                    loadResponsesIntoBean(pwmSession, forgottenPasswordBean);
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
                this.initializeToken(pwmSession, forgottenPasswordBean.getProxiedUser());
                this.forwardToEnterCodeJSP(req, resp);
                return;
            }
        }

        // sanity check, shouldn't be possible to get here unless.....
        if (!forgottenPasswordBean.isTokenSatisfied() && !forgottenPasswordBean.isResponsesSatisfied()) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, "trying to advanced through forgotten password, but responses and tokens are unsatisifed, perhaps both are disabled?"));
        }

        forgottenPasswordBean.setAllPassed(true);

        if (config.readSettingAsBoolean(PwmSetting.CHALLENGE_ALLOW_UNLOCK)) {
            final PwmPasswordPolicy passwordPolicy = PwmPasswordPolicy.createPwmPasswordPolicy(pwmSession, forgottenPasswordBean.getProxiedUser());
            final PasswordStatus passwordStatus = UserStatusHelper.readPasswordStatus(pwmSession, forgottenPasswordBean.getProxiedUser(), passwordPolicy);

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

        if (forgottenPasswordBean.isResponsesSatisfied()) {
            final ChaiUser theUser = forgottenPasswordBean.getProxiedUser();
            try {
                theUser.unlock();
                pwmSession.getSessionStateBean().setSessionSuccess(Message.SUCCESS_UNLOCK_ACCOUNT, null);
                ServletHelper.forwardToSuccessPage(req, resp, this.getServletContext());
                return;
            } catch (ChaiOperationException e) {
                final String errorMsg = "unable to unlock user " + theUser.getEntryDN() + " error: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNLOCK_FAILURE,errorMsg);
                pwmSession.getSessionStateBean().setSessionError(errorInformation);
                LOGGER.error(pwmSession, errorInformation.toDebugStr());
            }
        }

        this.forwardToChoiceJSP(req, resp);
    }


    private void processResetPassword(final HttpServletRequest req, final HttpServletResponse resp)
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
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
            AuthenticationFilter.authUserWithUnknownPassword(theUser, pwmSession, req.isSecure());

            LOGGER.info(pwmSession, "user successfully supplied password recovery responses, forward to change password page: " + theUser.getEntryDN());

            // mark the event log
            UserHistory.updateUserHistory(pwmSession, UserHistory.Record.Event.RECOVER_PASSWORD, null);

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
            throws ChaiValidationException, ChaiUnavailableException {
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
            throws ChaiUnavailableException, PwmDataValidationException, PwmUnrecoverableException {
        final List<FormConfiguration> requiredAttributesForm = pwmSession.getConfig().readSettingAsForm(PwmSetting.CHALLENGE_REQUIRED_ATTRIBUTES, pwmSession.getSessionStateBean().getLocale());

        if (requiredAttributesForm.isEmpty()) {
            return;
        }

        final Map<FormConfiguration,String> formValues = Validator.readFormValuesFromRequest(req, requiredAttributesForm);

        for (final FormConfiguration paramConfig : formValues.keySet()) {
            final String attrName = paramConfig.getAttributeName();

            try {
                if (!theUser.compareStringAttribute(attrName, formValues.get(paramConfig))) {
                    throw new PwmDataValidationException(new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE, "incorrect value for '" + attrName + "'", attrName));
                }
                LOGGER.trace(pwmSession, "successful validation of ldap value for '" + attrName + "'");
            } catch (ChaiOperationException e) {
                LOGGER.error(pwmSession, "error during param validation of '" + attrName + "', error: " + e.getMessage());
                throw new PwmDataValidationException(new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE, "ldap error testing value for '" + attrName + "'", attrName));            }
        }
    }

    public void initializeToken(final PwmSession pwmSession, final ChaiUser proxiedUser)
            throws PwmUnrecoverableException {
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();
        final Configuration config = pwmSession.getConfig();

        final String token;
        if (forgottenPasswordBean.getToken() == null) {
            token = generateRecoverCode(config);
            LOGGER.debug(pwmSession, "generated token code for session: " + token);
            forgottenPasswordBean.setToken(token);
        }

        final String toAddress;
        try {
            toAddress = proxiedUser.readStringAttribute(config.readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE));
            forgottenPasswordBean.setTokenEmailAddress(toAddress);
        } catch (Exception e) {
            LOGGER.debug("error reading mail attribute from user '" + proxiedUser.getEntryDN() + "': " + e.getMessage());
        }

        final String toSmsNumber;
        try {
            toSmsNumber = proxiedUser.readStringAttribute(config.readSettingAsString(PwmSetting.SMS_USER_PHONE_ATTRIBUTE));
            forgottenPasswordBean.setTokenSmsNumber(toSmsNumber);
        } catch (Exception e) {
            LOGGER.debug("error reading SMS attribute from user '" + proxiedUser.getEntryDN() + "': " + e.getMessage());
        }

        sendToken(pwmSession, proxiedUser);
    }

    private void sendToken(final PwmSession pwmSession, final ChaiUser proxiedUser)
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmSession.getConfig();
        final SmsPriority pref = SmsPriority.valueOf(config.readSettingAsString(PwmSetting.CHALLENGE_TOKEN_SEND_METHOD));
        final boolean success;
        switch (pref) {
            case BOTH:
                // Send both email and SMS, success if one of both succeeds
                final boolean suc1 = sendEmailToken(pwmSession, proxiedUser);
                final boolean suc2 = sendSmsToken(pwmSession, proxiedUser);
                success = suc1 || suc2;
                break;
            case EMAILFIRST:
                // Send email first, try SMS if email is not available
                success = sendEmailToken(pwmSession, proxiedUser) || sendSmsToken(pwmSession, proxiedUser);
                break;
            case SMSFIRST:
                // Send SMS first, try email if SMS is not available
                success = sendSmsToken(pwmSession, proxiedUser) || sendEmailToken(pwmSession, proxiedUser);
                break;
            case SMSONLY:
                // Only try SMS
                success = sendSmsToken(pwmSession, proxiedUser);
                break;
            case EMAILONLY:
            default:
                // Only try email
                success = sendEmailToken(pwmSession, proxiedUser);
                break;
        }
        if (!success) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_TOKEN_MISSING_CONTACT));
        }
    }

    private boolean sendEmailToken(final PwmSession pwmSession, final ChaiUser proxiedUser) throws PwmUnrecoverableException {
        final ContextManager theManager = pwmSession.getContextManager();
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final Configuration config = pwmSession.getConfig();
        final String fromAddress = config.readSettingAsLocalizedString(PwmSetting.EMAIL_CHALLENGE_TOKEN_FROM, userLocale);
        final String subject = config.readSettingAsLocalizedString(PwmSetting.EMAIL_CHALLENGE_TOKEN_SUBJECT, userLocale);
        String plainBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_CHALLENGE_TOKEN_BODY, userLocale);
        String htmlBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_CHALLENGE_TOKEN_BODY_HTML, userLocale);
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();
        final String toAddress = forgottenPasswordBean.getTokenEmailAddress();
        final String token = forgottenPasswordBean.getToken();

        if (toAddress == null || toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send token email for '" + proxiedUser.getEntryDN() + "' no email address available in ldap");
            forgottenPasswordBean.setEmailUsed(false);
            return false;
        }

        plainBody = plainBody.replaceAll("%TOKEN%", token);
        htmlBody = htmlBody.replaceAll("%TOKEN%", token);

        theManager.sendEmailUsingQueue(new EmailItemBean(toAddress, fromAddress, subject, plainBody, htmlBody));
        theManager.getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_SENT);
        LOGGER.debug(pwmSession, "token email added to send queue for " + toAddress);
        forgottenPasswordBean.setEmailUsed(true);
        return true;
    }

    private boolean sendSmsToken(final PwmSession pwmSession, final ChaiUser proxiedUser) throws PwmUnrecoverableException {
        final ContextManager theManager = pwmSession.getContextManager();
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final Configuration config = pwmSession.getConfig();
        String senderId = config.readSettingAsString(PwmSetting.SMS_SENDER_ID);
        if (senderId == null) { senderId = ""; }
        String message = config.readSettingAsLocalizedString(PwmSetting.SMS_CHALLENGE_TOKEN_TEXT, userLocale);
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();
        final String toSmsNumber = forgottenPasswordBean.getTokenSmsNumber();
        final String token = forgottenPasswordBean.getToken();

        if (toSmsNumber == null || toSmsNumber.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send token sms for '" + proxiedUser.getEntryDN() + "' no SMS number available in ldap");
            forgottenPasswordBean.setSmsUsed(false);
            return false;
        }

        message = message.replaceAll("%TOKEN%", token);

        final Integer maxlen = ((Long) config.readSettingAsLong(PwmSetting.SMS_MAX_TEXT_LENGTH)).intValue();
        theManager.sendSmsUsingQueue(new SmsItemBean(toSmsNumber, senderId, message, maxlen, userLocale));
        theManager.getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_SENT);
        LOGGER.debug(pwmSession, "token SMS added to send queue for " + toSmsNumber);
        forgottenPasswordBean.setSmsUsed(true);
        return true;
    }

    public static String generateRecoverCode(final Configuration config) {
        final String RANDOM_CHARS = config.readSettingAsString(PwmSetting.CHALLENGE_TOKEN_CHARACTERS);
        final int CODE_LENGTH = (int) config.readSettingAsLong(PwmSetting.CHALLENGE_TOKEN_LENGTH);
        final PwmRandom RANDOM = PwmRandom.getInstance();

        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(RANDOM_CHARS.charAt(RANDOM.nextInt(RANDOM_CHARS.length())));
        }

        return sb.toString();
    }

    private static boolean checkForURLcommand(final HttpServletRequest req, final HttpServletResponse resp, final PwmSession pwmSession)
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
        redirectURL.append(PwmConstants.PARAM_ACTION_REQUEST).append("=forgottenCode");
        redirectURL.append("&");
        redirectURL.append("code=").append(aftPath);
        redirectURL.append("&");
        redirectURL.append("pwmFormID=").append(pwmSession.getSessionStateBean().getSessionVerificationKey());

        LOGGER.debug(pwmSession, "detected long servlet url, redirecting user to " + redirectURL);
        resp.sendRedirect(redirectURL.toString());
        return true;
    }

    public enum SmsPriority {
        EMAILONLY,
        BOTH,
        EMAILFIRST,
        SMSFIRST,
        SMSONLY
    }
}

