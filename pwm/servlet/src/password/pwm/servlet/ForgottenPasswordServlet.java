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

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.*;
import password.pwm.bean.ForgottenPasswordBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.Message;
import password.pwm.config.ParameterConfig;
import password.pwm.config.PasswordStatus;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmException;
import password.pwm.error.ValidationException;
import password.pwm.util.PwmLogger;
import password.pwm.util.PwmRandom;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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
            throws ServletException, IOException, ChaiUnavailableException, PwmException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ContextManager theManager = pwmSession.getContextManager();
        final ForgottenPasswordBean recoverServletBean = PwmSession.getForgottenPasswordBean(req);

        if (recoverServletBean.getProxiedUser() != null) {
            theManager.getIntruderManager().checkUser(recoverServletBean.getProxiedUser().getEntryDN(), pwmSession);
        }

        final String processRequestParam = Validator.readStringFromRequest(req, Constants.PARAM_ACTION_REQUEST, 255);

        if (recoverServletBean.getProxiedUser() != null) {
            theManager.getIntruderManager().checkUser(recoverServletBean.getProxiedUser().getEntryDN(), pwmSession);
        }

        if (processRequestParam.equalsIgnoreCase("search")) {
            this.processSearch(req, resp);
        } else if (processRequestParam.equalsIgnoreCase("checkResponses")) {
            this.processCheckResponses(req, resp);
        /*
        } else if (processRequestParam.equalsIgnoreCase("forgottenCode")) {
            this.processEnterForgottenCode(req, resp);
        */
        } else if (recoverServletBean.isResponsesSatisfied() && processRequestParam.equalsIgnoreCase("selectUnlock")) {
            this.processUnlock(req,resp);
        } else if (recoverServletBean.isResponsesSatisfied() && processRequestParam.equalsIgnoreCase("selectResetPassword")) {
            this.processResetPassword(req,resp);
        } else {
            this.forwardToSearchJSP(req, resp);
        }
    }

    private void processSearch(final HttpServletRequest req, final HttpServletResponse resp)
            throws ChaiUnavailableException, PwmException, IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ContextManager theManager = pwmSession.getContextManager();
        final ForgottenPasswordBean forgottenPasswordBean = PwmSession.getForgottenPasswordBean(req);

        final String usernameParam = Validator.readStringFromRequest(req, "username", 256);
        final String contextParam = Validator.readStringFromRequest(req, "context", 256);

        // convert the username field to a DN.
        final String userDN = UserStatusHelper.convertUsernameFieldtoDN(usernameParam, pwmSession, contextParam);

        if (userDN == null) {
            theManager.getIntruderManager().addBadUserAttempt(usernameParam,pwmSession);
            theManager.getIntruderManager().checkUser(usernameParam, pwmSession);
            pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(Message.ERROR_RESPONSES_NORESPONSES));
            Helper.pause(PwmRandom.getInstance().nextInt(2 * 1000) + 1000); // delay penalty of 1-3 seconds
            this.forwardToSearchJSP(req, resp);
            return;
        }

        final ChaiUser proxiedUser = ChaiFactory.createChaiUser(userDN, theManager.getProxyChaiProvider());

        // write a token to the user entry.
        /* {
            final String token = generateRecoverCode();
            final String attributeName = pwmSession.getConfig().readSettingAsString(PwmSetting.CHALLANGE_TOKEN_ATTRIBUTE);
            try {
                proxiedUser.writeStringAttribute(attributeName, token);
                LOGGER.debug("wrote forgotten password token to user " + proxiedUser.getEntryDN());
            } catch (ChaiOperationException e) {
                LOGGER.error("error writing forgotten password token to user " + proxiedUser.getEntryDN() + ", " + e.getMessage());
            }
        } */

        if (checkIfUserHasValidResponses(pwmSession, proxiedUser, forgottenPasswordBean)) {
            this.forwardToResponsesJSP(req, resp);
            return;
        }

        pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(Message.ERROR_RESPONSES_NORESPONSES));
        Helper.pause(PwmRandom.getInstance().nextInt(2 * 1000) + 1000); // delay penalty of 1-3 seconds
        this.forwardToSearchJSP(req, resp);
    }

    /*
    private void processEnterForgottenCode(final HttpServletRequest req, final HttpServletResponse resp)
            throws ChaiUnavailableException, PwmException, IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ForgottenPasswordBean forgottenPasswordBean = PwmSession.getForgottenPasswordBean(req);

        final String recoverCode = Validator.readStringFromRequest(req, "code", 256);

        final ChaiUser proxiedUser = forgottenPasswordBean.getProxiedUser();

        final boolean codeIsCorrect = testRecoverCode(pwmSession, proxiedUser, recoverCode);

        if (codeIsCorrect) {
            this.forwardToResponsesJSP(req, resp);
            return;
        }

        pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(Message.ERROR_RESPONSES_NORESPONSES));
        Helper.pause(PwmRandom.getInstance().nextInt(2 * 1000) + 1000); // delay penalty of 1-3 seconds
        this.forwardToEnterCodeJSP(req, resp);
    }
    */

    private static boolean checkIfUserHasValidResponses(final PwmSession pwmSession, final ChaiUser theUser, final ForgottenPasswordBean forgottenPasswordBean)
            throws ChaiUnavailableException
    {
        // retrieve the responses for the user from ldap
        final ResponseSet responseSet = PasswordUtility.readUserResponseSet(pwmSession, theUser);

        if (responseSet != null) {
            LOGGER.trace("loaded responseSet from user: " + responseSet.toString());

            Locale responseSetLocale = pwmSession.getSessionStateBean().getLocale();
            try {
                responseSetLocale = responseSet.getLocale();
            } catch (Exception e) {
                LOGGER.error("error retreiving locale from stored responseSet, will use browser locale instead: " + e.getMessage());
            }

            // read the user's assigned response set.
            final ChallengeSet challengeSet = PasswordUtility.readUserChallengeSet(pwmSession, theUser, null, responseSetLocale);

            try {
                if (responseSet.meetsChallengeSetRequirements(challengeSet)) {
                    if (!challengeSet.getRequiredChallenges().isEmpty() || (challengeSet.getMinRandomRequired() > 0)) {
                        forgottenPasswordBean.setChallengeSet(challengeSet);
                        forgottenPasswordBean.setResponseSet(responseSet);
                        forgottenPasswordBean.setProxiedUser(theUser);
                        return true;
                    } else {
                        LOGGER.info(pwmSession, "configured challenge set policy for " + theUser.getEntryDN() + " is empty, user not qualified to recover password");
                    }
                }
            } catch(ChaiValidationException e){
                LOGGER.error(pwmSession, "stored response set for user '" + theUser.getEntryDN() + "' do not meet current challenge set requirements: " + e.getLocalizedMessage());
            }
        } else {
            LOGGER.info(pwmSession, "could not find a response set for " + theUser.getEntryDN()
            );
        }

        forgottenPasswordBean.setChallengeSet(null);
        forgottenPasswordBean.setResponseSet(null);
        forgottenPasswordBean.setProxiedUser(null);
        return false;
    }

    private void forwardToResponsesJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {
        this.getServletContext().getRequestDispatcher('/' + Constants.URL_JSP_RECOVER_PASSWORD_RESPONSES).forward(req, resp);
    }

    private void processCheckResponses(final HttpServletRequest req, final HttpServletResponse resp)
            throws ChaiUnavailableException, IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ContextManager theManager = pwmSession.getContextManager();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final ForgottenPasswordBean forgottenPasswordBean = PwmSession.getForgottenPasswordBean(req);

        final ChaiUser theUser = forgottenPasswordBean.getProxiedUser();

        if (theUser == null) {
            this.forwardToSearchJSP(req, resp);
            return;
        }

        try {
            // validate the required ldap attributes (throws validation exception if incorrect attributes)
            validateRequiredAttributes(theUser, req, pwmSession);

            // read the suppled responses from the user
            final Map<Challenge, String> crMap = readResponsesFromHttpRequest(req, forgottenPasswordBean.getResponseSet().getChallengeSet());

            final ResponseSet responseSet = forgottenPasswordBean.getResponseSet();

            final boolean responsesSatisfied = responseSet.test(crMap);
            forgottenPasswordBean.setResponsesSatisfied(responsesSatisfied);

            if (responsesSatisfied) {
                // update the status bean
                theManager.getStatisticsManager().incrementValue(Statistic.RECOVERY_SUCCESSES);
                LOGGER.debug(pwmSession, "user '" + theUser.getEntryDN() + "' has supplied correct responses");

                if (theManager.getConfig().readSettingAsBoolean(PwmSetting.CHALLANGE_ALLOW_UNLOCK)) {
                    final PwmPasswordPolicy passwordPolicy = PwmPasswordPolicy.createPwmPasswordPolicy(pwmSession, theUser);
                    final PasswordStatus passwordStatus = UserStatusHelper.readPasswordStatus(pwmSession, theUser, passwordPolicy);

                    if (!passwordStatus.isExpired() && !passwordStatus.isPreExpired()) {
                        try {
                            if (theUser.isLocked()) {
                                this.forwardToChoiceJSP(req, resp);
                                return;
                            }
                        } catch (ChaiOperationException e) {
                            LOGGER.error(pwmSession, "chai operation error checking user lock status: " + e.getMessage());
                        }
                    }
                }

                this.processResetPassword(req, resp);
                return;
            }

            // note the recovery failure to the statistics manager
            theManager.getStatisticsManager().incrementValue(Statistic.RECOVERY_FAILURES);
            ssBean.setSessionError(new ErrorInformation(Message.ERROR_WRONGANSWER));
            LOGGER.debug(pwmSession,"incorrect response answer during check for " + theUser.getEntryDN());
        } catch (ChaiValidationException e) {
            LOGGER.debug(pwmSession, "chai validation error checking user responses: " + e.getMessage());
            ssBean.setSessionError(new ErrorInformation(Message.forResourceKey(e.getValidationError().getErrorKey())));
        } catch (ValidationException e) {
            LOGGER.debug(pwmSession, "validation error checking user responses: " + e.getMessage());
            ssBean.setSessionError(new ErrorInformation(Message.ERROR_WRONGANSWER));
        }

        theManager.getIntruderManager().addBadAddressAttempt(pwmSession);
        theManager.getIntruderManager().addBadUserAttempt(forgottenPasswordBean.getProxiedUser().getEntryDN(), pwmSession);
        Helper.pause(PwmRandom.getInstance().nextInt(2 * 1000) + 1000); // delay penalty of 1-3 seconds
        this.forwardToResponsesJSP(req, resp);
    }

    private void processUnlock(final HttpServletRequest req, final HttpServletResponse resp)
            throws IOException, ServletException, ChaiUnavailableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ForgottenPasswordBean forgottenPasswordBean = PwmSession.getForgottenPasswordBean(req);

        if (forgottenPasswordBean.isResponsesSatisfied()) {
            final ChaiUser theUser = forgottenPasswordBean.getProxiedUser();
            try {
                theUser.unlock();
                pwmSession.getSessionStateBean().setSessionSuccess(new ErrorInformation(Message.SUCCESS_UNLOCK_ACCOUNT));
                Helper.forwardToSuccessPage(req,resp, this.getServletContext());
                return;
            } catch (ChaiOperationException e) {
                LOGGER.info(pwmSession, "error unlocking user: " + theUser.getEntryDN());
            }
        }

        this.forwardToChoiceJSP(req, resp);
    }


    private void processResetPassword(final HttpServletRequest req, final HttpServletResponse resp)
            throws ChaiUnavailableException, IOException, ServletException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ForgottenPasswordBean forgottenPasswordBean = PwmSession.getForgottenPasswordBean(req);

        try {

            if (!forgottenPasswordBean.isResponsesSatisfied()) {
                forwardToResponsesJSP(req,resp);
                return;
            }

            final ChaiUser theUser = forgottenPasswordBean.getProxiedUser();

            AuthenticationFilter.authUserWithUnknownPassword(theUser, pwmSession, req);

            LOGGER.info(pwmSession, "user successfully supplied password recovery responses, forward to change password page: " + theUser.getEntryDN());

            // mark the event log
            UserHistory.updateUserHistory(pwmSession, UserHistory.Record.Event.RECOVER_PASSWORD, null);

            // redirect user to change password screen.
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(Constants.URL_SERVLET_CHANGE_PASSWORD, req, resp));
        } catch (PwmException e) {
            if (e.getError().getError().equals(Message.ERROR_BAD_SESSION_PASSWORD)) {
                LOGGER.warn(pwmSession, "unable to set session password for user, proxy ldap user does not have enough rights");
            }
        }
    }

    public static Map<Challenge, String> readResponsesFromHttpRequest(
            final HttpServletRequest req,
            final ChallengeSet challengeSet)
            throws ChaiValidationException, ChaiUnavailableException
    {
        final Map<Challenge, String> responses = new LinkedHashMap<Challenge, String>();

        int counter = 0;
        for (final Challenge loopChallenge : challengeSet.getChallenges()) {
            counter++;
            final String answer = Validator.readStringFromRequest(req, Constants.PARAM_RESPONSE_PREFIX + counter, 1024);

            responses.put(loopChallenge, answer.length() > 0 ? answer : "");
        }

        return responses;
    }

    private void forwardToSearchJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {
        this.getServletContext().getRequestDispatcher('/' + Constants.URL_JSP_RECOVER_PASSWORD_SEARCH).forward(req, resp);
    }

    private void forwardToChoiceJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {
        this.getServletContext().getRequestDispatcher('/' + Constants.URL_JSP_RECOVER_PASSWORD_CHOICE).forward(req, resp);
    }

    private void forwardToEnterCodeJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {
        this.getServletContext().getRequestDispatcher('/' + Constants.URL_JSP_RECOVER_PASSWORD_ENTER_CODE).forward(req, resp);
    }

    private void validateRequiredAttributes(final ChaiUser theUser, final HttpServletRequest req, final PwmSession pwmSession)
            throws ChaiUnavailableException, ValidationException
    {
        final Map<String, ParameterConfig> paramConfigs = pwmSession.getLocaleConfig().getChallengeRequiredAttributes();

        if (paramConfigs.isEmpty()) {
            return;
        }

        Validator.updateParamValues(pwmSession, req, paramConfigs);

        for (final ParameterConfig paramConfig : paramConfigs.values()) {
            final String attrName = paramConfig.getAttributeName();

            try {
                if (!theUser.compareStringAttribute(attrName, paramConfig.getValue())) {
                    throw ValidationException.createValidationException(new ErrorInformation(Message.ERROR_WRONGANSWER, "incorrect value for '" + attrName + "'",attrName));
                }
                LOGGER.trace(pwmSession,"successful validation of ldap value for '" + attrName + "'");
            } catch (ChaiOperationException e) {
                LOGGER.error(pwmSession,"error during param validation of '" + attrName + "', error: " + e.getMessage());
                throw ValidationException.createValidationException(new ErrorInformation(Message.ERROR_WRONGANSWER, "ldap error testing value for '" + attrName + "'",attrName));
            }
        }
    }

    public static String generateRecoverCode() {
        final String RANDOM_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        final PwmRandom RANDOM = PwmRandom.getInstance();
        final long currentTime = System.currentTimeMillis();

        final StringBuilder sb = new StringBuilder();
        sb.append(Long.toHexString(currentTime).toUpperCase());

        while (sb.length() < 12) {
            sb.insert(0, "0");
        }

        for (int i = 0; i < 32; i++) {
            sb.insert(0,(RANDOM_CHARS.charAt(RANDOM.nextInt(RANDOM_CHARS.length()))));
        }

        return sb.toString().toUpperCase();
    }

    /*
    public static boolean testRecoverCode(final PwmSession pwmSession, final ChaiUser proxedUser, final String recoverCode)
            throws ChaiUnavailableException
    {
        if (recoverCode == null || recoverCode.length() != 12 + 32){
            return false;
        }

        // read directory value
        final String storedCode;
        try {
            storedCode = proxedUser.readStringAttribute(pwmSession.getConfig().readSettingAsString(PwmSetting.CHALLANGE_TOKEN_ATTRIBUTE));
        } catch (ChaiOperationException e) {
            LOGGER.error(pwmSession,"error reading recover key from directory: " + e.getMessage());
            return false;
        }

        // test for match
        if (!recoverCode.equals(storedCode)) {
            LOGGER.debug(pwmSession,"user supplied and stored recovery codes do not match");
        }

        final String timeStampStr = recoverCode.substring(32, 12 + 32);
        final long timeStampLng;
        try {
            timeStampLng = parseUnsignedLong(timeStampStr);
        } catch (Exception e) {
            LOGGER.error(pwmSession,"unexpected error determining timestamp of recover key: " + e.getMessage());
            return false;
        }


        final long maxTokenAgeMs = pwmSession.getConfig().readSettingAsInt(PwmSetting.CHALLANGE_TOKEN_MAX_AGE) * 1000;
        if (TimeDuration.fromCurrent(timeStampLng).isLongerThan(maxTokenAgeMs)) {
            LOGGER.debug(pwmSession,"recovery code is too old for match");
            return false;

        }

        return true;
    }
    */

    private static long parseUnsignedLong(final String s) throws NumberFormatException {
        final BigInteger maxUnsignedLong = new BigInteger(new byte[]{1, 0, 0, 0, 0, 0, 0, 0, 0});
        final BigInteger big = new BigInteger(s, 16);
        if (big.compareTo(BigInteger.ZERO) < 0 || big.compareTo(maxUnsignedLong) >= 0)
            throw new NumberFormatException();
        return big.longValue();
    }
}

