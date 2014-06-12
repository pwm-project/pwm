/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.*;
import password.pwm.bean.*;
import password.pwm.bean.servlet.ForgottenPasswordBean;
import password.pwm.config.*;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.option.RecoveryAction;
import password.pwm.error.*;
import password.pwm.event.AuditEvent;
import password.pwm.i18n.Message;
import password.pwm.ldap.*;
import password.pwm.token.TokenPayload;
import password.pwm.util.*;
import password.pwm.util.intruder.RecordType;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.operations.OtpService;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.operations.cr.NMASCrOperator;
import password.pwm.util.otp.OTPUserRecord;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.client.rest.RestTokenDataClient;

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

    @Override
    public void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final Configuration config = pwmApplication.getConfig();
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

        if (forgottenPasswordBean.getUserIdentity() != null) {
            pwmApplication.getIntruderManager().convenience().checkUserIdentity(forgottenPasswordBean.getUserIdentity());
        }

        final String processAction = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);

        // convert a url command like /pwm/public/ForgottenPassword/12321321 to redirect with a process action.
        if (processAction == null || processAction.length() < 1) {
            if (convertURLtokenCommand(req, resp, pwmApplication, pwmSession)) {
                return;
            }
        }

        if (processAction != null && processAction.length() > 0) {
            Validator.validatePwmFormID(req);

            final boolean tokenEnabled = MessageSendMethod.NONE != MessageSendMethod.valueOf(config.readSettingAsString(PwmSetting.CHALLENGE_TOKEN_SEND_METHOD));
            final boolean tokenNeeded = tokenEnabled && !forgottenPasswordBean.isTokenSatisfied();
            final boolean responsesEnabled = config.readSettingAsBoolean(PwmSetting.CHALLENGE_REQUIRE_RESPONSES) || !config.readSettingAsForm(PwmSetting.CHALLENGE_REQUIRED_ATTRIBUTES).isEmpty();
            final boolean responsesNeeded = responsesEnabled && !forgottenPasswordBean.isResponsesSatisfied();

            if (processAction.equalsIgnoreCase("search")) {
                this.processSearch(req, resp);
                return;
            } else if (processAction.equalsIgnoreCase("checkResponses")) {
                this.processCheckResponses(pwmApplication, pwmSession, req, resp);
                return;
            } else if (processAction.equalsIgnoreCase("enterCode")) {
                this.processEnterForgottenCode(req, resp, pwmApplication, pwmSession);
                return;
            } else if (processAction.equalsIgnoreCase("enterOtp")) {
                this.processEnterForgottenOtpToken(req, resp, pwmApplication, pwmSession);
                return;
            } else if (processAction.equalsIgnoreCase("reset")) {
                pwmSession.clearSessionBean(ForgottenPasswordBean.class);
                return;
            } else if (!tokenNeeded && !responsesNeeded && processAction.equalsIgnoreCase("selectUnlock")) {
                this.processUnlock(pwmApplication, pwmSession, req, resp);
                return;
            } else if (!tokenNeeded && !responsesNeeded && processAction.equalsIgnoreCase("selectResetPassword")) {
                this.processResetPassword(pwmApplication, pwmSession, req, resp);
                return;
            }
        }

        this.advancedToNextStage(pwmApplication, pwmSession, req, resp);
    }

    private void processSearch(final HttpServletRequest req, final HttpServletResponse resp)
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);

        final String contextParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_CONTEXT);
        final String ldapProfile = Validator.readStringFromRequest(req, PwmConstants.PARAM_LDAP_PROFILE);

        // clear the bean
        pwmSession.clearForgottenPasswordBean();
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();

        final List<FormConfiguration> forgottenPasswordForm = pwmApplication.getConfig().readSettingAsForm(
                PwmSetting.FORGOTTEN_PASSWORD_SEARCH_FORM);

        Map<FormConfiguration, String> formValues = new HashMap();

        try {
            //read the values from the request
            formValues = Validator.readFormValuesFromRequest(req, forgottenPasswordForm, userLocale);

            // check for intruder search values
            pwmApplication.getIntruderManager().convenience().checkAttributes(formValues);

            // see if the values meet the configured form requirements.
            Validator.validateParmValuesMeetRequirements(formValues, userLocale);

            // convert the username field to an identity
            final UserIdentity theUser;
            {
                final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
                final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
                searchConfiguration.setFilter(pwmApplication.getConfig().readSettingAsString(PwmSetting.FORGOTTEN_PASSWORD_SEARCH_FILTER));
                searchConfiguration.setFormValues(formValues);
                searchConfiguration.setContexts(Collections.singletonList(contextParam));
                searchConfiguration.setLdapProfile(ldapProfile);
                theUser = userSearchEngine.performSingleUserSearch(pwmSession, searchConfiguration);
            }

            if (theUser == null) {
                pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER));
                pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
                pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_FAILURES);
                ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.RECOVER_PASSWORD_SEARCH);
                return;
            }

            forgottenPasswordBean.setUserIdentity(theUser);
            pwmSession.getSessionStateBean().setLastParameterValues(new FormMap());

            // clear intruder search values
            pwmApplication.getIntruderManager().convenience().clearAttributes(formValues);
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES,e.getErrorInformation().getDetailedErrorMsg(),e.getErrorInformation().getFieldValues());
            pwmApplication.getIntruderManager().mark(RecordType.ADDRESS, pwmSession.getSessionStateBean().getSrcAddress(), pwmSession);
            pwmApplication.getIntruderManager().convenience().markAttributes(formValues, pwmSession);

            pwmSession.getSessionStateBean().setSessionError(errorInfo);
            LOGGER.debug(pwmSession,errorInfo.toDebugStr());
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.RECOVER_PASSWORD_SEARCH);
            return;
        }

        this.advancedToNextStage(pwmApplication, pwmSession, req, resp);
    }

    private void processEnterForgottenCode(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();
        final String userEnteredCode = Validator.readStringFromRequest(req, PwmConstants.PARAM_TOKEN);

        ErrorInformation errorInformation = null;
        try {
            final TokenPayload tokenPayload = pwmApplication.getTokenService().processUserEnteredCode(
                    pwmSession,
                    forgottenPasswordBean.getUserIdentity(),
                    TOKEN_NAME,
                    userEnteredCode
            );
            if (tokenPayload != null) {
                forgottenPasswordBean.setUserIdentity(tokenPayload.getUserIdentity());
                forgottenPasswordBean.setTokenSatisfied(true);
            }
        } catch (PwmOperationalException e) {
            final String errorMsg = "token incorrect: " + e.getMessage();
            errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,errorMsg);
        }

        if (!forgottenPasswordBean.isTokenSatisfied()) {
            if (errorInformation == null) {
                errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT);
            }
            LOGGER.debug(pwmSession, errorInformation.toDebugStr());
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
        }

        this.advancedToNextStage(pwmApplication, pwmSession, req, resp);
    }

    private void processEnterForgottenOtpToken(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        LOGGER.trace(pwmSession, String.format("Enter: processEnterForgottenOtpToken(%s, %s, %s, %s)", req, resp, pwmApplication, pwmSession));
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();
        final String userEnteredCode = Validator.readStringFromRequest(req, PwmConstants.PARAM_TOKEN);
        LOGGER.debug(pwmSession, String.format("entered OTP: %s", userEnteredCode));

        final OTPUserRecord otpUserRecord = forgottenPasswordBean.getOtpUserRecord();
        final OtpService otpService = pwmApplication.getOtpService();
        boolean otpPassed;
        if (otpUserRecord != null) {
            LOGGER.info(pwmSession, "checking entered OTP");
            try {
                otpPassed = otpService.validateToken(
                        null, // forces service to use proxy account to update (write) updated otp record if neccessary.
                        forgottenPasswordBean.getUserIdentity(),
                        otpUserRecord,
                        userEnteredCode,
                        true
                );

                if (otpPassed) {
                    pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_OTP_PASSED);
                    LOGGER.debug(pwmSession, "one time password validation has been passed");
                    forgottenPasswordBean.setOtpSatisfied(true);
                } else {
                    LOGGER.debug(pwmSession, "one time password validation has failed");
                    pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_INCORRECT_OTP_TOKEN));
                    final UserIdentity userDN = forgottenPasswordBean.getUserIdentity();
                    UserAuthenticator.simulateBadPassword(forgottenPasswordBean.getUserIdentity(), pwmApplication, pwmSession);
                    pwmApplication.getIntruderManager().convenience().markUserIdentity(userDN, pwmSession);
                    pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
                }
            } catch (PwmOperationalException e) {
                LOGGER.debug(pwmSession, "one time password validation has failed: " + e.getMessage());
                pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_INCORRECT_OTP_TOKEN,e.getErrorInformation().toDebugStr()));
                final UserIdentity userDN = forgottenPasswordBean.getUserIdentity();
                UserAuthenticator.simulateBadPassword(forgottenPasswordBean.getUserIdentity(), pwmApplication, pwmSession);
                pwmApplication.getIntruderManager().convenience().markUserIdentity(userDN, pwmSession);
                pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
            }
        }
        this.advancedToNextStage(pwmApplication, pwmSession, req, resp);
    }

    private static void loadResponsesIntoBean(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        // retrieve the responses for the user from ldap
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser(forgottenPasswordBean.getUserIdentity());
        final ResponseSet responseSet = pwmApplication.getCrService().readUserResponseSet(pwmSession, forgottenPasswordBean.getUserIdentity(), theUser);
        final ChallengeSet presentableChallengeSet;

        if (responseSet != null) {
            try {
                if (responseSet.getPresentableChallengeSet() != null) {
                    presentableChallengeSet = responseSet.getPresentableChallengeSet();
                } else {
                    presentableChallengeSet = responseSet.getChallengeSet();
                }
            } catch (ChaiValidationException e) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,e.getMessage()));
            }

            LOGGER.trace("loaded responseSet from user: " + responseSet.toString());

            Locale responseSetLocale = pwmSession.getSessionStateBean().getLocale();
            try {
                responseSetLocale = responseSet.getLocale();
            } catch (ChaiOperationException e) {
                LOGGER.error("error retrieving locale from stored responseSet, will use browser locale instead: " + e.getMessage());
            } catch (ChaiUnavailableException e) {
                LOGGER.error("error retrieving locale from stored responseSet, will use browser locale instead: " + e.getMessage());
            } catch (IllegalStateException e) {
                LOGGER.error("error retrieving locale from stored responseSet, will use browser locale instead: " + e.getMessage());
            }

            // read the user's assigned response set.
            final ChallengeProfile challengeProfile = pwmApplication.getCrService().readUserChallengeProfile(
                    forgottenPasswordBean.getUserIdentity(), theUser, null, responseSetLocale);
            final ChallengeSet challengeSet = challengeProfile.getChallengeSet();

            try {
                if (responseSet.meetsChallengeSetRequirements(challengeSet)) {
                    if (!challengeSet.getRequiredChallenges().isEmpty() || (challengeSet.getMinRandomRequired() > 0)) {
                        forgottenPasswordBean.setResponseSet(responseSet);
                        forgottenPasswordBean.setChallengeSet(presentableChallengeSet);
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
        forgottenPasswordBean.setChallengeSet(null);
        forgottenPasswordBean.setUserIdentity(null);

        final String errorMsg = "could not find a response set for " + theUser.getEntryDN();
        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES, errorMsg);
        throw new PwmOperationalException(errorInformation);
    }

    /**
     * Load the OTP configuration for the user into the forgotten password bean.
     *
     * @param pwmApplication
     * @param forgottenPasswordBean
     * @throws PwmUnrecoverableException
     * @throws ChaiUnavailableException
     * @throws PwmOperationalException
     */
    private static void loadOtpConfigIntoBean(
            final PwmApplication pwmApplication,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
        final OTPUserRecord otpConfig = pwmApplication.getOtpService().readOTPUserConfiguration(userIdentity);

        LOGGER.trace("loaded one time password configuration for user");
        if (otpConfig != null) {
            forgottenPasswordBean.setOtpUserRecord(otpConfig);
            return;
        }

        forgottenPasswordBean.setOtpUserRecord(null);

        final String errorMsg = "could not find a one time password configuration for " + userIdentity;
        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NO_OTP_CONFIGURATION, errorMsg);
        throw new PwmOperationalException(errorInformation);
    }


    private void processCheckResponses(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();
        final Configuration config = pwmApplication.getConfig();

        if (forgottenPasswordBean.getUserIdentity() == null) {
            this.advancedToNextStage(pwmApplication, pwmSession, req, resp);
            return;
        }

        try { // check attributes
            final ChaiUser theUser = pwmApplication.getProxiedChaiUser(forgottenPasswordBean.getUserIdentity());
            validateRequiredAttributes(theUser, req, pwmSession);
        } catch (PwmDataValidationException e) {
            ssBean.setSessionError(e.getErrorInformation());
            LOGGER.debug(pwmSession,
                    "incorrect attribute value during check for " + forgottenPasswordBean.getUserIdentity());
            pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_FAILURES);
            pwmApplication.getIntruderManager().convenience().markUserIdentity(forgottenPasswordBean.getUserIdentity(),
                    pwmSession);
            pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
            UserAuthenticator.simulateBadPassword(forgottenPasswordBean.getUserIdentity(), pwmApplication, pwmSession);
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.RECOVER_PASSWORD_RESPONSES);
            return;
        }

        if (config.readSettingAsBoolean(PwmSetting.CHALLENGE_REQUIRE_RESPONSES)) {
            if (forgottenPasswordBean.getResponseSet() == null) {
                final String errorMsg = "attempt to check responses, but responses are not loaded into session bean";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                ssBean.setSessionError(errorInformation);
                LOGGER.error(pwmSession, errorInformation.toDebugStr());
                ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
                return;
            }

            try {
                // read the supplied responses from the user
                final Map<Challenge, String> crMap = readResponsesFromHttpRequest(req, forgottenPasswordBean.getChallengeSet());

                final ResponseSet responseSet = forgottenPasswordBean.getResponseSet();

                final boolean responsesSatisfied;
                try {
                    responsesSatisfied = responseSet.test(crMap);
                } catch (ChaiUnavailableException e) {
                    if (e.getCause() instanceof PwmUnrecoverableException) {
                        throw (PwmUnrecoverableException)e.getCause();
                    }
                    throw e;
                }
                forgottenPasswordBean.setResponsesSatisfied(responsesSatisfied);

                // special case for nmas, clear out existing challenges and input fields.
                if (!responsesSatisfied && responseSet instanceof NMASCrOperator.NMASCRResponseSet) {
                    forgottenPasswordBean.setChallengeSet(responseSet.getPresentableChallengeSet());
                    pwmSession.getSessionStateBean().setLastParameterValues(Collections.<String,String>emptyMap());
                }

                if (responsesSatisfied) {
                    LOGGER.debug(pwmSession, "user '" + forgottenPasswordBean.getUserIdentity() + "' has supplied correct responses");
                } else {
                    final String errorMsg = "incorrect response to one or more challenges";
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE, errorMsg);
                    ssBean.setSessionError(errorInformation);
                    LOGGER.debug(pwmSession,errorInformation.toDebugStr());
                    pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_FAILURES);
                    UserAuthenticator.simulateBadPassword(forgottenPasswordBean.getUserIdentity(),pwmApplication,pwmSession);
                    pwmApplication.getIntruderManager().convenience().markUserIdentity(forgottenPasswordBean.getUserIdentity(), pwmSession);
                    pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
                    ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.RECOVER_PASSWORD_RESPONSES);
                    return;
                }
            } catch (ChaiValidationException e) {
                LOGGER.debug(pwmSession, "chai validation error checking user responses: " + e.getMessage());
                ssBean.setSessionError(new ErrorInformation(PwmError.forChaiError(e.getErrorCode())));
                ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.RECOVER_PASSWORD_RESPONSES);
                return;
            }
        }

        forgottenPasswordBean.setResponsesSatisfied(true);

        this.advancedToNextStage(pwmApplication, pwmSession, req, resp);
    }

    private void advancedToNextStage(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final Configuration config = pwmApplication.getConfig();
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();

        // check for proxied user;
        if (forgottenPasswordBean.getUserIdentity() == null) {
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.RECOVER_PASSWORD_SEARCH);
            return;
        }

        // check for attribute form in bean.
        if (forgottenPasswordBean.getAttributeForm() == null) {
            try {
                final List<FormConfiguration> form = figureAttributeForm(pwmApplication, pwmSession, forgottenPasswordBean.getUserIdentity());
                forgottenPasswordBean.setAttributeForm(form);
            } catch (PwmOperationalException e) {
                pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
                LOGGER.debug(pwmSession, e.getErrorInformation().toDebugStr());
                ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.RECOVER_PASSWORD_SEARCH);
                return;
            }
        }

        // if responses are required, and user has responses, then send to response screen.
        if (config.readSettingAsBoolean(PwmSetting.CHALLENGE_REQUIRE_RESPONSES)) {
            if (forgottenPasswordBean.getResponseSet() == null) {
                try {
                    loadResponsesIntoBean(pwmApplication, pwmSession, forgottenPasswordBean);
                } catch (PwmOperationalException e) {
                    pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
                    LOGGER.debug(pwmSession, e.getErrorInformation().toDebugStr());
                    ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.RECOVER_PASSWORD_SEARCH);
                    return;
                }
            }

            if (!forgottenPasswordBean.isResponsesSatisfied()) {
                ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.RECOVER_PASSWORD_RESPONSES);
                return;
            }
        }

        // if responses aren't required, but attributes are, send to response screen anyway
        {
            final List<FormConfiguration> requiredAttributesForm = config.readSettingAsForm(PwmSetting.CHALLENGE_REQUIRED_ATTRIBUTES);

            if (!requiredAttributesForm.isEmpty() && !forgottenPasswordBean.isResponsesSatisfied()) {
                ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.RECOVER_PASSWORD_RESPONSES);
                return;
            }
        }

        // process for OTP-enabled recovery
        final boolean otpEnabled = config.readSettingAsBoolean(PwmSetting.OTP_ENABLED) && config.readSettingAsBoolean(PwmSetting.FORGOTTEN_PASSWORD_REQUIRE_OTP);
        if (otpEnabled) {
            if (forgottenPasswordBean.getOtpUserRecord() == null) {
                try {
                    loadOtpConfigIntoBean(pwmApplication, forgottenPasswordBean);
                } catch (PwmOperationalException e) {
                    pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
                    LOGGER.error(pwmSession, e.getErrorInformation().toDebugStr());
                    ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.RECOVER_PASSWORD_SEARCH);
                    return;
                }
            }
            if (!forgottenPasswordBean.isOtpSatisfied()) {
                ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.RECOVER_PASSWORD_ENTER_OTP);
                return;
            }
        }

        // process for token-enabled recovery
        final boolean tokenEnabled = MessageSendMethod.NONE != MessageSendMethod.valueOf(config.readSettingAsString(PwmSetting.CHALLENGE_TOKEN_SEND_METHOD));
        if (tokenEnabled) {
            if (!forgottenPasswordBean.isTokenSatisfied()) {
                this.initializeToken(pwmSession, pwmApplication, forgottenPasswordBean.getUserIdentity());
                ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.RECOVER_PASSWORD_ENTER_CODE);
                return;
            }
        }

        // sanity check, shouldn't be possible to get here unless.....
        if (!forgottenPasswordBean.isTokenSatisfied() && !forgottenPasswordBean.isResponsesSatisfied() && !forgottenPasswordBean.isOtpSatisfied()) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, "trying to advance through forgotten password, but responses and tokens are unsatisfied, perhaps both are disabled?"));
        }

        if (!forgottenPasswordBean.isAllPassed()) {
            forgottenPasswordBean.setAllPassed(true);
            pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_SUCCESSES);
        }
        LOGGER.trace(pwmSession, "all recovery checks passed, proceeding to configured recovery action");

        if (config.getRecoveryAction() == RecoveryAction.SENDNEWPW) {
            this.processSendNewPassword(pwmApplication,pwmSession,req,resp);
            return;
        }

        if (config.readSettingAsBoolean(PwmSetting.CHALLENGE_ALLOW_UNLOCK)) {
            final ChaiUser theUser = pwmApplication.getProxiedChaiUser(forgottenPasswordBean.getUserIdentity());
            final Locale locale = pwmSession.getSessionStateBean().getLocale();
            final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(pwmApplication,
                    pwmSession, forgottenPasswordBean.getUserIdentity(), theUser, locale);
            final UserStatusReader userStatusReader = new UserStatusReader(pwmApplication);
            final PasswordStatus passwordStatus = userStatusReader.readPasswordStatus(pwmSession, null, theUser, passwordPolicy, null);

            if (!passwordStatus.isExpired() && !passwordStatus.isPreExpired()) {
                try {
                    if (theUser.isLocked()) {
                        ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.RECOVER_PASSWORD_CHOICE);
                        return;
                    }
                } catch (ChaiOperationException e) {
                    LOGGER.error(pwmSession, "chai operation error checking user lock status: " + e.getMessage());
                }
            }
        }

        this.processResetPassword(pwmApplication, pwmSession, req, resp);
    }


    private void processUnlock(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();

        try {
            final ChaiUser theUser = pwmApplication.getProxiedChaiUser(forgottenPasswordBean.getUserIdentity());
            theUser.unlock();
            pwmSession.getSessionStateBean().setSessionSuccess(Message.SUCCESS_UNLOCK_ACCOUNT, null);
            ServletHelper.forwardToSuccessPage(req, resp);
            return;
        } catch (ChaiOperationException e) {
            final String errorMsg = "unable to unlock user " + forgottenPasswordBean.getUserIdentity() + " error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNLOCK_FAILURE,errorMsg);
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
            LOGGER.error(pwmSession, errorInformation.toDebugStr());
        }

        this.advancedToNextStage(pwmApplication, pwmSession, req, resp);
    }


    private void processResetPassword(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();

        if (!forgottenPasswordBean.isAllPassed()) {
            this.advancedToNextStage(pwmApplication, pwmSession, req, resp);
            return;
        }

        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userIdentity);

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
            UserAuthenticator.authUserWithUnknownPassword(userIdentity, pwmSession, pwmApplication, req.isSecure(), UserInfoBean.AuthenticationType.AUTH_FROM_FORGOTTEN);

            LOGGER.info(pwmSession, "user successfully supplied password recovery responses, forward to change password page: " + theUser.getEntryDN());

            // mark the event log
            pwmApplication.getAuditManager().submit(AuditEvent.RECOVER_PASSWORD, pwmSession.getUserInfoBean(),
                    pwmSession);

            // add the post-forgotten password actions
            addPostChangeAction(pwmApplication, pwmSession, userIdentity);

            // mark user as requiring a new password.
            pwmSession.getUserInfoBean().setRequiresNewPassword(true);

            // redirect user to change password screen.
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(PwmConstants.URL_SERVLET_CHANGE_PASSWORD, req, resp));
        } catch (PwmUnrecoverableException e) {
            LOGGER.warn(pwmSession,"unexpected error authenticating during forgotten password recovery process user: " + e.getMessage());
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
        }
    }

    private void processSendNewPassword(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();

        LOGGER.trace(pwmSession,"beginning process to send new password to user");

        if (!forgottenPasswordBean.isAllPassed()) {
            this.advancedToNextStage(pwmApplication, pwmSession, req, resp);
            return;
        }

        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userIdentity);

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
            UserAuthenticator.authUserWithUnknownPassword(userIdentity, pwmSession, pwmApplication, req.isSecure(), UserInfoBean.AuthenticationType.AUTH_FROM_FORGOTTEN);

            LOGGER.info(pwmSession, "user successfully supplied password recovery responses, emailing new password to: " + theUser.getEntryDN());

            // add post change actions
            addPostChangeAction(pwmApplication, pwmSession, userIdentity);

            // create newpassword
            final String newPassword = RandomPasswordGenerator.createRandomPassword(pwmSession, pwmApplication);

            // set the password
            PasswordUtility.setUserPassword(pwmSession, pwmApplication, newPassword);

            // mark the event log
            pwmApplication.getAuditManager().submit(AuditEvent.RECOVER_PASSWORD, pwmSession.getUserInfoBean(), pwmSession);

            // send email or SMS
            final String toAddress = PasswordUtility.sendNewPassword(pwmSession.getUserInfoBean(), pwmApplication, LdapUserDataReader.appProxiedReader(
                    pwmApplication, userIdentity), newPassword, pwmSession.getSessionStateBean().getLocale());

            pwmSession.getSessionStateBean().setSessionSuccess(Message.SUCCESS_PASSWORDSEND, toAddress);
            ServletHelper.forwardToSuccessPage(req, resp);
        } catch (PwmException e) {
            LOGGER.warn(pwmSession,"unexpected error setting new password during recovery process for user: " + e.getMessage());
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
        } finally {
            pwmSession.unauthenticateUser();
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
            final String answer = Validator.readStringFromRequest(req, PwmConstants.PARAM_RESPONSE_PREFIX + counter);

            responses.put(loopChallenge, answer.length() > 0 ? answer : "");
        }

        return responses;
    }

    private void validateRequiredAttributes(final ChaiUser theUser, final HttpServletRequest req, final PwmSession pwmSession)
            throws ChaiUnavailableException, PwmDataValidationException, PwmUnrecoverableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();

        final List<FormConfiguration> requiredAttributesForm = forgottenPasswordBean.getAttributeForm();

        if (requiredAttributesForm.isEmpty()) {
            return;
        }

        final Map<FormConfiguration,String> formValues = Validator.readFormValuesFromRequest(req, requiredAttributesForm, userLocale);
        for (final FormConfiguration paramConfig : formValues.keySet()) {
            final String attrName = paramConfig.getName();

            try {
                if (theUser.compareStringAttribute(attrName, formValues.get(paramConfig))) {
                    LOGGER.trace(pwmSession, "successful validation of ldap attribute value for '" + attrName + "'");
                } else {
                    throw new PwmDataValidationException(new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE, "incorrect value for '" + attrName + "'", new String[]{attrName}));
                }
            } catch (ChaiOperationException e) {
                LOGGER.error(pwmSession, "error during param validation of '" + attrName + "', error: " + e.getMessage());
                throw new PwmDataValidationException(new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE, "ldap error testing value for '" + attrName + "'", new String[]{attrName}));
            }
        }
    }

    public void initializeToken(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();
        final Configuration config = pwmApplication.getConfig();

        final Map<String,String> tokenMapData = new HashMap<String, String>();

        try {
            final Date userLastPasswordChange = PasswordUtility.determinePwdLastModified(pwmApplication, pwmSession, userIdentity);
            if (userLastPasswordChange != null) {
                final String userChangeString = PwmConstants.DEFAULT_DATETIME_FORMAT.format(userLastPasswordChange);
                tokenMapData.put(PwmConstants.TOKEN_KEY_PWD_CHG_DATE, userChangeString);
            }
        } catch (ChaiUnavailableException e) {
            LOGGER.error(pwmSession, "unexpected error reading user's last password change time");
        }

        final RestTokenDataClient.TokenDestinationData inputDestinationData;
        {
            String toEmailAddr = null;

            try {
                LOGGER.trace("Reading setting " + PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE);
                final UserDataReader dataStoreReader = LdapUserDataReader.appProxiedReader(pwmApplication, userIdentity);
                toEmailAddr = dataStoreReader.readStringAttribute(config.readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE));
                if (toEmailAddr != null && toEmailAddr.length() > 0) {
                    LOGGER.trace("Email address: " + toEmailAddr);
                }
            } catch (ChaiOperationException e) {
                LOGGER.debug("error reading mail attribute from user '" + userIdentity + "': " + e.getMessage());
            } catch (ChaiUnavailableException e) {
                LOGGER.debug("error reading mail attribute from user '" + userIdentity + "': " + e.getMessage());
            } catch (PwmUnrecoverableException e) {
                LOGGER.debug("error reading mail attribute from user '" + userIdentity + "': " + e.getMessage());
            }

            String toSmsNumber = null;
            try {
                LOGGER.trace("reading setting " + PwmSetting.SMS_USER_PHONE_ATTRIBUTE);
                final UserDataReader dataStoreReader = LdapUserDataReader.appProxiedReader(pwmApplication, userIdentity);
                toSmsNumber = dataStoreReader.readStringAttribute(config.readSettingAsString(PwmSetting.SMS_USER_PHONE_ATTRIBUTE));
                if (toSmsNumber !=null && toSmsNumber.length() > 0) {
                    LOGGER.trace("SMS number: " + toSmsNumber);
                }
            } catch (ChaiOperationException e) {
                LOGGER.debug("error reading SMS attribute from user '" + userIdentity + "': " + e.getMessage());
            } catch (ChaiUnavailableException e) {
                LOGGER.debug("error reading SMS attribute from user '" + userIdentity + "': " + e.getMessage());
            } catch (PwmUnrecoverableException e) {
                LOGGER.debug("error reading SMS attribute from user '" + userIdentity + "': " + e.getMessage());
            }

            inputDestinationData = new RestTokenDataClient.TokenDestinationData(toEmailAddr,toSmsNumber,null);
        }

        final RestTokenDataClient restTokenDataClient = new RestTokenDataClient(pwmApplication);
        final RestTokenDataClient.TokenDestinationData outputDestrestTokenDataClient = restTokenDataClient.figureDestTokenDisplayString(
                pwmSession,
                inputDestinationData,
                forgottenPasswordBean.getUserIdentity(),
                pwmSession.getSessionStateBean().getLocale());

        forgottenPasswordBean.setTokenSendAddress(outputDestrestTokenDataClient.getDisplayValue());
        final Set<String> destinationValues = new HashSet<String>();
        if (outputDestrestTokenDataClient.getEmail() != null) {
            destinationValues.add(outputDestrestTokenDataClient.getEmail());
        }
        if (outputDestrestTokenDataClient.getSms() != null) {
            destinationValues.add(outputDestrestTokenDataClient.getSms());
        }

        final String tokenKey;
        TokenPayload tokenPayload;
        try {
            tokenPayload = pwmApplication.getTokenService().createTokenPayload(TOKEN_NAME, tokenMapData, userIdentity, destinationValues);
            tokenKey = pwmApplication.getTokenService().generateNewToken(tokenPayload, pwmSession);
        } catch (PwmOperationalException e) {
            throw new PwmUnrecoverableException(e.getErrorInformation());
        }
        LOGGER.debug(pwmSession, "generated token code for session");

        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        sendToken(pwmApplication, userIdentity, locale, outputDestrestTokenDataClient.getEmail(), outputDestrestTokenDataClient.getSms(), tokenKey);
    }

    private static void sendToken(
            final PwmApplication pwmApplication,
            final UserIdentity userIdentity,
            final Locale userLocale,
            final String toAddress,
            final String toSmsNumber,
            final String tokenKey
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final Configuration config = pwmApplication.getConfig();
        final MessageSendMethod pref = MessageSendMethod.valueOf(config.readSettingAsString(PwmSetting.CHALLENGE_TOKEN_SEND_METHOD));
        final EmailItemBean emailItemBean = config.readSettingAsEmail(PwmSetting.EMAIL_CHALLENGE_TOKEN, userLocale);
        final String smsMessage = config.readSettingAsLocalizedString(PwmSetting.SMS_CHALLENGE_TOKEN_TEXT, userLocale);
        final UserDataReader userDataReader = LdapUserDataReader.appProxiedReader(pwmApplication, userIdentity);

        Helper.TokenSender.sendToken(
                pwmApplication,
                null,
                userDataReader,
                emailItemBean,
                pref,
                toAddress,
                toSmsNumber,
                smsMessage,
                tokenKey
        );
    }

    private List<FormConfiguration> figureAttributeForm(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmOperationalException, PwmUnrecoverableException
    {
        final List<FormConfiguration> requiredAttributesForm = pwmApplication.getConfig().readSettingAsForm(PwmSetting.CHALLENGE_REQUIRED_ATTRIBUTES);
        if (requiredAttributesForm.isEmpty()) {
            return requiredAttributesForm;
        }

        final UserDataReader userDataReader = LdapUserDataReader.appProxiedReader(pwmApplication, userIdentity);
        final List<FormConfiguration> returnList = new ArrayList<FormConfiguration>();
        for (final FormConfiguration formItem : requiredAttributesForm) {
            if (formItem.isRequired()) {
                returnList.add(formItem);
            } else {
                try {
                    final String currentValue = userDataReader.readStringAttribute(formItem.getName());
                    if (currentValue != null && currentValue.length() > 0) {
                        returnList.add(formItem);
                    } else {
                        LOGGER.trace(pwmSession, "excluding optional required attribute(" + formItem.getName() + "), user has no value");
                    }
                } catch (ChaiOperationException e) {
                    throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_NO_CHALLENGES, "unexpected error reading value for attribute " + formItem.getName()));
                }
            }
        }

        if (returnList.isEmpty()) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_NO_CHALLENGES, "user has no values for any optional attribute"));
        }

        return returnList;
    }

    private void addPostChangeAction(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity
    )
    {
        final PostChangePasswordAction postAction = new PostChangePasswordAction() {
            @Override
            public String getLabel() {
                return "Forgotten Password Post Actions";
            }

            @Override
            public boolean doAction(final PwmSession pwmSession, final String newPassword)
                    throws PwmUnrecoverableException {
                try {
                    {  // execute configured actions
                        final ChaiUser proxiedUser = pwmApplication.getProxiedChaiUser(userIdentity);
                        LOGGER.debug(pwmSession, "executing post-forgotten password configured actions to user " + proxiedUser.getEntryDN());
                        final List<ActionConfiguration> configValues = pwmApplication.getConfig().readSettingAsAction(PwmSetting.FORGOTTEN_USER_POST_ACTIONS);
                        final ActionExecutor.ActionExecutorSettings settings = new ActionExecutor.ActionExecutorSettings();
                        settings.setExpandPwmMacros(true);
                        settings.setUserInfoBean(pwmSession.getUserInfoBean());
                        final ActionExecutor actionExecutor = new ActionExecutor(pwmApplication);
                        actionExecutor.executeActions(configValues, settings, pwmSession);
                    }
                } catch (PwmOperationalException e) {
                    final ErrorInformation info = new ErrorInformation(PwmError.ERROR_UNKNOWN, e.getErrorInformation().getDetailedErrorMsg(), e.getErrorInformation().getFieldValues());
                    final PwmUnrecoverableException newException = new PwmUnrecoverableException(info);
                    newException.initCause(e);
                    throw newException;
                } catch (ChaiUnavailableException e) {
                    final String errorMsg = "unable to reach ldap server while writing post-forgotten password attributes: " + e.getMessage();
                    final ErrorInformation info = new ErrorInformation(PwmError.ERROR_ACTIVATION_FAILURE, errorMsg);
                    final PwmUnrecoverableException newException = new PwmUnrecoverableException(info);
                    newException.initCause(e);
                    throw newException;
                }
                return true;
            }
        };

        pwmSession.getUserInfoBean().addPostChangePasswordActions("forgottenPasswordPostActions", postAction);

    }

}

