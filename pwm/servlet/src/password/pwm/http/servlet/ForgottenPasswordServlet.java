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

package password.pwm.http.servlet;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.Validator;
import password.pwm.bean.*;
import password.pwm.config.ActionConfiguration;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.option.RecoveryAction;
import password.pwm.error.*;
import password.pwm.event.AuditEvent;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.ForgottenPasswordBean;
import password.pwm.http.filter.SessionFilter;
import password.pwm.i18n.Message;
import password.pwm.ldap.*;
import password.pwm.token.TokenPayload;
import password.pwm.token.TokenService;
import password.pwm.util.JsonUtil;
import password.pwm.util.PostChangePasswordAction;
import password.pwm.util.PwmLogger;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.intruder.RecordType;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.operations.OtpService;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.operations.cr.NMASCrOperator;
import password.pwm.util.otp.OTPUserRecord;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;
import password.pwm.ws.client.rest.RestTokenDataClient;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;

/**
 * User interaction servlet for recovering user's password using secret question/answer
 *
 * @author Jason D. Rivard
 */
public class ForgottenPasswordServlet extends PwmServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ForgottenPasswordServlet.class);

    private static final String TOKEN_NAME = ForgottenPasswordServlet.class.getName();


    public enum ForgottenPasswordAction implements PwmServlet.ProcessAction {
        search,
        checkResponses,
        enterCode,
        enterOtp,
        reset,
        actionChoice,
        tokenChoice,
        ;

        public Collection<PwmServlet.HttpMethod> permittedMethods()
        {
            return PwmServlet.GET_AND_POST_METHODS;
        }
    }

    protected ForgottenPasswordAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        try {
            return ForgottenPasswordAction.valueOf(request.readStringParameter(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


// -------------------------- OTHER METHODS --------------------------

    @Override
    public void processAction(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        final Configuration config = pwmApplication.getConfig();
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();

        if (!config.readSettingAsBoolean(PwmSetting.FORGOTTEN_PASSWORD_ENABLE)) {
            pwmRequest.respondWithError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            return;
        }

        if (pwmSession.getSessionStateBean().isAuthenticated()) {
            pwmRequest.respondWithError(PwmError.ERROR_USERAUTHENTICATED.toInfo());
            return;
        }

        if (forgottenPasswordBean.getUserInfo() != null && forgottenPasswordBean.getUserInfo().getUserIdentity() != null) {
            pwmApplication.getIntruderManager().convenience().checkUserIdentity(forgottenPasswordBean.getUserInfo().getUserIdentity());
        }

        checkForLocaleSwitch(pwmRequest, forgottenPasswordBean);

        final ForgottenPasswordAction processAction = readProcessAction(pwmRequest);

        // convert a url command like /pwm/public/ForgottenPassword/12321321 to redirect with a process action.
        if (processAction == null) {
            if (convertURLtokenCommand(pwmRequest.getHttpServletRequest(), pwmRequest.getHttpServletResponse(), pwmApplication, pwmSession)) {
                return;
            }
        }

        if (processAction != null) {
            Validator.validatePwmFormID(pwmRequest.getHttpServletRequest());

            switch (processAction) {
                case search:
                    this.processSearch(pwmRequest);
                    break;

                case checkResponses:
                    this.processCheckResponses(pwmRequest);
                    break;

                case enterCode:
                    this.processEnterToken(pwmRequest);
                    break;

                case enterOtp:
                    this.processEnterOtpToken(pwmRequest);
                    break;

                case reset:
                    pwmSession.clearSessionBean(ForgottenPasswordBean.class);
                    break;

                case actionChoice:
                    this.processActionChoice(pwmRequest);
                    break;

                case tokenChoice:
                    this.processTokenChoice(pwmRequest);
                    break;
            }
        } else {
            pwmRequest.getPwmSession().clearForgottenPasswordBean();
        }

        if (!pwmRequest.getHttpServletResponse().isCommitted()) {
            this.advancedToNextStage(pwmRequest);
        }
    }

    private void processActionChoice(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, ServletException, IOException, ChaiUnavailableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = pwmRequest.getPwmSession().getForgottenPasswordBean();
        if (forgottenPasswordBean.getProgress().isAllPassed()) {
            final String choice = pwmRequest.readStringParameter("choice");
            if (choice != null) {
                if ("unlock".equals(choice)) {
                    this.executeUnlock(pwmRequest);
                } else if ("resetPassword".equalsIgnoreCase(choice)) {
                    this.executeResetPassword(pwmRequest);
                }
            }
        }
    }

    private void processTokenChoice(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, ServletException, IOException, ChaiUnavailableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = pwmRequest.getPwmSession().getForgottenPasswordBean();
        if (forgottenPasswordBean.getProgress().getTokenSendChoice() == MessageSendMethod.CHOICE_SMS_EMAIL) {
            final String choice = pwmRequest.readStringParameter("choice");
            if (choice != null) {
                if ("email".equals(choice)) {
                    forgottenPasswordBean.getProgress().setTokenSendChoice(MessageSendMethod.EMAILONLY);
                } else if ("sms".equalsIgnoreCase(choice)) {
                    forgottenPasswordBean.getProgress().setTokenSendChoice(MessageSendMethod.SMSONLY);
                }
            }
        }
    }

    private void processSearch(final PwmRequest pwmRequest)
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Locale userLocale = pwmRequest.getLocale();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        final String contextParam = pwmRequest.readStringParameter(PwmConstants.PARAM_CONTEXT);
        final String ldapProfile = pwmRequest.readStringParameter(PwmConstants.PARAM_LDAP_PROFILE);

        // clear the bean
        pwmSession.clearForgottenPasswordBean();

        final List<FormConfiguration> forgottenPasswordForm = pwmApplication.getConfig().readSettingAsForm(
                PwmSetting.FORGOTTEN_PASSWORD_SEARCH_FORM);

        Map<FormConfiguration, String> formValues = new HashMap();

        try {
            //read the values from the request
            formValues = Validator.readFormValuesFromRequest(pwmRequest.getHttpServletRequest(), forgottenPasswordForm, userLocale);

            // check for intruder search values
            pwmApplication.getIntruderManager().convenience().checkAttributes(formValues);

            // see if the values meet the configured form requirements.
            Validator.validateParmValuesMeetRequirements(formValues, userLocale);

            // convert the username field to an identity
            final UserIdentity userIdentity;
            {
                final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmRequest);
                final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
                searchConfiguration.setFilter(pwmApplication.getConfig().readSettingAsString(PwmSetting.FORGOTTEN_PASSWORD_SEARCH_FILTER));
                searchConfiguration.setFormValues(formValues);
                searchConfiguration.setContexts(Collections.singletonList(contextParam));
                searchConfiguration.setLdapProfile(ldapProfile);
                userIdentity = userSearchEngine.performSingleUserSearch(searchConfiguration);
            }

            if (userIdentity == null) {
                pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER));
                pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
                pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_FAILURES);
                pwmRequest.forwardToJsp(PwmConstants.JSP_URL.RECOVER_PASSWORD_SEARCH);
                return;
            }

            final ForgottenPasswordBean forgottenPasswordBean = pwmRequest.getPwmSession().getForgottenPasswordBean();
            initForgottenPasswordBean(pwmApplication, pwmRequest.getLocale(), pwmRequest.getSessionLabel(),userIdentity, forgottenPasswordBean);

            // clear intruder search values
            pwmApplication.getIntruderManager().convenience().clearAttributes(formValues);
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES,e.getErrorInformation().getDetailedErrorMsg(),e.getErrorInformation().getFieldValues());
            pwmApplication.getIntruderManager().mark(RecordType.ADDRESS, pwmSession.getSessionStateBean().getSrcAddress(), pwmSession);
            pwmApplication.getIntruderManager().convenience().markAttributes(formValues, pwmSession);

            pwmSession.getSessionStateBean().setSessionError(errorInfo);
            LOGGER.debug(pwmSession,errorInfo.toDebugStr());
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.RECOVER_PASSWORD_SEARCH);
        }
    }

    private void processEnterToken(final PwmRequest pwmRequest)
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final ForgottenPasswordBean forgottenPasswordBean = pwmRequest.getPwmSession().getForgottenPasswordBean();
        final String userEnteredCode = pwmRequest.readStringParameter(PwmConstants.PARAM_TOKEN);

        ErrorInformation errorInformation = null;
        try {
            final TokenPayload tokenPayload = pwmRequest.getPwmApplication().getTokenService().processUserEnteredCode(
                    pwmRequest.getPwmSession(),
                    forgottenPasswordBean.getUserInfo().getUserIdentity(),
                    TOKEN_NAME,
                    userEnteredCode
            );
            if (tokenPayload != null) {
                // token correct
                if (forgottenPasswordBean.getUserInfo() == null) {
                    // clean session, user supplied token (clicked email, etc) and this is first request
                    initForgottenPasswordBean(
                            pwmRequest.getPwmApplication(),
                            pwmRequest.getLocale(),
                            pwmRequest.getSessionLabel(),
                            tokenPayload.getUserIdentity(),
                            forgottenPasswordBean
                    );
                }
                forgottenPasswordBean.getProgress().setTokenSatisfied(true);
                StatisticsManager.incrementStat(pwmRequest.getPwmApplication(), Statistic.RECOVERY_TOKENS_PASSED);
            }
        } catch (PwmOperationalException e) {
            final String errorMsg = "token incorrect: " + e.getMessage();
            errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,errorMsg);
        }

        if (!forgottenPasswordBean.getProgress().isTokenSatisfied()) {
            if (errorInformation == null) {
                errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT);
            }
            handleUserVerificationBadAttempt(pwmRequest, forgottenPasswordBean, errorInformation);
        }
    }

    private void processEnterOtpToken(final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = pwmRequest.getPwmSession().getForgottenPasswordBean();
        final String userEnteredCode = pwmRequest.readStringParameter(PwmConstants.PARAM_TOKEN);
        LOGGER.debug(pwmRequest, String.format("entered OTP: %s", userEnteredCode));

        final OTPUserRecord otpUserRecord = forgottenPasswordBean.getUserInfo().getOtpUserRecord();
        final OtpService otpService = pwmRequest.getPwmApplication().getOtpService();
        boolean otpPassed;
        if (otpUserRecord != null) {
            LOGGER.info(pwmRequest, "checking entered OTP");
            try {
                otpPassed = otpService.validateToken(
                        null, // forces service to use proxy account to update (write) updated otp record if neccessary.
                        forgottenPasswordBean.getUserInfo().getUserIdentity(),
                        otpUserRecord,
                        userEnteredCode,
                        true
                );

                if (otpPassed) {
                    StatisticsManager.incrementStat(pwmRequest, Statistic.RECOVERY_OTP_PASSED);
                    LOGGER.debug(pwmRequest, "one time password validation has been passed");
                    forgottenPasswordBean.getProgress().setOtpSatisfied(true);
                } else {
                    StatisticsManager.incrementStat(pwmRequest, Statistic.RECOVERY_OTP_FAILED);
                    handleUserVerificationBadAttempt(pwmRequest, forgottenPasswordBean, new ErrorInformation(PwmError.ERROR_INCORRECT_OTP_TOKEN));
                }
            } catch (PwmOperationalException e) {
                handleUserVerificationBadAttempt(pwmRequest, forgottenPasswordBean, new ErrorInformation(PwmError.ERROR_INCORRECT_OTP_TOKEN,e.getErrorInformation().toDebugStr()));
            }
        }
    }


    private void processCheckResponses(final PwmRequest pwmRequest)
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        //final SessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();
        final ForgottenPasswordBean forgottenPasswordBean = pwmRequest.getPwmSession().getForgottenPasswordBean();

        if (forgottenPasswordBean.getUserInfo() == null) {
            return;
        }
        final UserIdentity userIdentity =forgottenPasswordBean.getUserInfo().getUserIdentity();

        if (forgottenPasswordBean.getRecoveryFlags().isAttributesRequired()) {
            try { // check attributes
                final ChaiUser theUser = pwmRequest.getPwmApplication().getProxiedChaiUser(userIdentity);
                validateRequiredAttributes(pwmRequest, theUser, forgottenPasswordBean);
            } catch (PwmDataValidationException e) {
                handleUserVerificationBadAttempt(pwmRequest, forgottenPasswordBean, new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE,e.getErrorInformation().toDebugStr()));
                return;
            }
        }

        if (forgottenPasswordBean.getRecoveryFlags().isResponsesRequired()) {
            if (forgottenPasswordBean.getResponseSet() == null) {
                final String errorMsg = "attempt to check responses, but responses are not loaded into session bean";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                throw new PwmUnrecoverableException(errorInformation);
            }

            try {
                // read the supplied responses from the user
                final Map<Challenge, String> crMap = readResponsesFromHttpRequest(pwmRequest, forgottenPasswordBean.getPresentableChallengeSet());
                final ResponseSet responseSet = forgottenPasswordBean.getResponseSet();

                final boolean responsesPassed;
                try {
                    responsesPassed = responseSet.test(crMap);
                } catch (ChaiUnavailableException e) {
                    if (e.getCause() instanceof PwmUnrecoverableException) {
                        throw (PwmUnrecoverableException)e.getCause();
                    }
                    throw e;
                }

                // special case for nmas, clear out existing challenges and input fields.
                if (!responsesPassed && responseSet instanceof NMASCrOperator.NMASCRResponseSet) {
                    forgottenPasswordBean.setPresentableChallengeSet(responseSet.getPresentableChallengeSet());
                }

                if (responsesPassed) {
                    LOGGER.debug(pwmRequest, "user '" + userIdentity + "' has supplied correct responses");
                } else {
                    final String errorMsg = "incorrect response to one or more challenges";
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE, errorMsg);
                    handleUserVerificationBadAttempt(pwmRequest, forgottenPasswordBean, errorInformation);
                    return;
                }
            } catch (ChaiValidationException e) {
                LOGGER.debug(pwmRequest, "chai validation error checking user responses: " + e.getMessage());
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.forChaiError(e.getErrorCode()));
                handleUserVerificationBadAttempt(pwmRequest, forgottenPasswordBean, errorInformation);
                return;
            }
        }

        forgottenPasswordBean.getProgress().setResponsesSatisfied(true);
    }

    private void advancedToNextStage(final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmRequest.getConfig();
        final ForgottenPasswordBean forgottenPasswordBean = pwmRequest.getPwmSession().getForgottenPasswordBean();

        final ForgottenPasswordBean.RecoveryFlags recoveryFlags = forgottenPasswordBean.getRecoveryFlags();
        final ForgottenPasswordBean.Progress progress = forgottenPasswordBean.getProgress();

        LOGGER.trace(pwmRequest, "entering forgotten password progress engine:"
                + " flags=" + JsonUtil.getGson().toJson(recoveryFlags)
                + ", progress=" + JsonUtil.getGson().toJson(progress));


        // check for identified user;
        if (forgottenPasswordBean.getUserInfo() == null) {
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.RECOVER_PASSWORD_SEARCH);
            return;
        }

        // if responses are required, and user has responses, then send to response screen.
        if (recoveryFlags.isResponsesRequired() && !progress.isResponsesSatisfied()) {
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.RECOVER_PASSWORD_RESPONSES);
            return;
        }

        if (recoveryFlags.isAttributesRequired() && !progress.isResponsesSatisfied()) {
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.RECOVER_PASSWORD_RESPONSES);
            return;
        }

        if (recoveryFlags.isTokenRequired()) {
            if (progress.getTokenSendChoice() == null) {
                progress.setTokenSendChoice(figureTokenSendPreference(pwmApplication, pwmRequest.getSessionLabel(), forgottenPasswordBean.getUserInfo()));
            }

            if (progress.getTokenSendChoice() == MessageSendMethod.CHOICE_SMS_EMAIL) {
                pwmRequest.forwardToJsp(PwmConstants.JSP_URL.RECOVER_PASSWORD_TOKEN_CHOICE);
                return;
            }

            if (!progress.isTokenSent()) {
                final String destAddress = initializeAndSendToken(pwmRequest, forgottenPasswordBean.getUserInfo(), progress.getTokenSendChoice());
                progress.setTokenSentAddress(destAddress);
                progress.setTokenSent(true);
            }

            if (!progress.isTokenSatisfied()) {
                pwmRequest.forwardToJsp(PwmConstants.JSP_URL.RECOVER_PASSWORD_ENTER_TOKEN);
                return;
            }
        }

        if (recoveryFlags.isOtpRequired()) {
            if (!progress.isOtpSatisfied()) {
                pwmRequest.forwardToJsp(PwmConstants.JSP_URL.RECOVER_PASSWORD_ENTER_OTP);
                return;
            }
        }


        if (!forgottenPasswordBean.getProgress().isAllPassed()) {
            forgottenPasswordBean.getProgress().setAllPassed(true);
            StatisticsManager.incrementStat(pwmRequest, Statistic.RECOVERY_SUCCESSES);
        }
        LOGGER.trace(pwmRequest, "all recovery checks passed, proceeding to configured recovery action");

        if (config.getRecoveryAction() == RecoveryAction.SENDNEWPW || config.getRecoveryAction() == RecoveryAction.SENDNEWPW_AND_EXPIRE) {
            processSendNewPassword(pwmRequest);
            return;
        }

        if (config.readSettingAsBoolean(PwmSetting.CHALLENGE_ALLOW_UNLOCK)) {
            final PasswordStatus passwordStatus = forgottenPasswordBean.getUserInfo().getPasswordState();

            if (!passwordStatus.isExpired() && !passwordStatus.isPreExpired()) {
                try {
                    final ChaiUser theUser = pwmApplication.getProxiedChaiUser(forgottenPasswordBean.getUserInfo().getUserIdentity());
                    if (theUser.isLocked()) {
                        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.RECOVER_PASSWORD_ACTION_CHOICE);
                        return;
                    }
                } catch (ChaiOperationException e) {
                    LOGGER.error(pwmRequest, "chai operation error checking user lock status: " + e.getMessage());
                }
            }
        }

        this.executeResetPassword(pwmRequest);
    }


    private void executeUnlock(final PwmRequest pwmRequest)
            throws IOException, ServletException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();
        final UserIdentity userIdentity = forgottenPasswordBean.getUserInfo().getUserIdentity();

        try {
            final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userIdentity);
            theUser.unlock();
            pwmSession.getSessionStateBean().setSessionSuccess(Message.SUCCESS_UNLOCK_ACCOUNT, null);
            pwmRequest.forwardToSuccessPage();
        } catch (ChaiOperationException e) {
            final String errorMsg = "unable to unlock user " + userIdentity + " error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNLOCK_FAILURE,errorMsg);
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
            LOGGER.error(pwmSession, errorInformation.toDebugStr());
            pwmRequest.respondWithError(errorInformation, true);
        } finally {
            pwmSession.clearForgottenPasswordBean();
        }
    }


    private void executeResetPassword(final PwmRequest pwmRequest)
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ForgottenPasswordBean forgottenPasswordBean = pwmSession.getForgottenPasswordBean();

        if (!forgottenPasswordBean.getProgress().isAllPassed()) {
            return;
        }

        final UserIdentity userIdentity = forgottenPasswordBean.getUserInfo().getUserIdentity();
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userIdentity);

        try { // try unlocking user
            theUser.unlock();
            LOGGER.trace(pwmSession, "unlock account succeeded");
        } catch (ChaiOperationException e) {
            final String errorMsg = "unable to unlock user " + theUser.getEntryDN() + " error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNLOCK_FAILURE,errorMsg);
            LOGGER.error(pwmSession, errorInformation.toDebugStr());
        }

        try {
            UserAuthenticator.authUserWithUnknownPassword(
                    userIdentity,
                    pwmSession,
                    pwmApplication,
                    pwmRequest.getHttpServletRequest().isSecure(),
                    UserInfoBean.AuthenticationType.AUTH_FROM_FORGOTTEN
            );

            LOGGER.info(pwmSession, "user successfully supplied password recovery responses, forward to change password page: " + theUser.getEntryDN());

            // mark the event log
            pwmApplication.getAuditManager().submit(AuditEvent.RECOVER_PASSWORD, pwmSession.getUserInfoBean(),
                    pwmSession);

            // add the post-forgotten password actions
            addPostChangeAction(pwmRequest, userIdentity);

            // mark user as requiring a new password.
            pwmSession.getUserInfoBean().setRequiresNewPassword(true);

            // redirect user to change password screen.
            pwmRequest.getHttpServletResponse().sendRedirect(
                    SessionFilter.rewriteRedirectURL(PwmConstants.URL_SERVLET_CHANGE_PASSWORD,
                            pwmRequest.getHttpServletRequest(),
                            pwmRequest.getHttpServletResponse()
                    ));
        } catch (PwmUnrecoverableException e) {
            LOGGER.warn(pwmSession,"unexpected error authenticating during forgotten password recovery process user: " + e.getMessage());
            pwmRequest.respondWithError(e.getErrorInformation());
        } finally {
            pwmSession.clearForgottenPasswordBean();
        }
    }

    private void processSendNewPassword(final PwmRequest pwmRequest)
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ForgottenPasswordBean forgottenPasswordBean = pwmRequest.getPwmSession().getForgottenPasswordBean();

        LOGGER.trace(pwmRequest,"beginning process to send new password to user");

        if (!forgottenPasswordBean.getProgress().isAllPassed()) {
            return;
        }

        final UserIdentity userIdentity = forgottenPasswordBean.getUserInfo().getUserIdentity();
        final ChaiUser theUser = pwmRequest.getPwmApplication().getProxiedChaiUser(userIdentity);

        try { // try unlocking user
            theUser.unlock();
            LOGGER.trace(pwmRequest, "unlock account succeeded");
        } catch (ChaiOperationException e) {
            final String errorMsg = "unable to unlock user " + theUser.getEntryDN() + " error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNLOCK_FAILURE,errorMsg);
            LOGGER.error(pwmRequest.getPwmSession(), errorInformation.toDebugStr());
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        try {
            UserAuthenticator.authUserWithUnknownPassword(
                    userIdentity,
                    pwmSession,
                    pwmApplication,
                    pwmRequest.getHttpServletRequest().isSecure(),
                    UserInfoBean.AuthenticationType.AUTH_FROM_FORGOTTEN
            );

            LOGGER.info(pwmRequest, "user successfully supplied password recovery responses, emailing new password to: " + theUser.getEntryDN());

            // add post change actions
            addPostChangeAction(pwmRequest, userIdentity);

            // create newpassword
            final String newPassword = RandomPasswordGenerator.createRandomPassword(pwmSession, pwmApplication);

            // set the password
            LOGGER.trace(pwmRequest.getPwmSession(), "setting user password to system generated random value");
            PasswordUtility.setUserPassword(pwmSession, pwmApplication, newPassword);

            if (pwmApplication.getConfig().getRecoveryAction() == RecoveryAction.SENDNEWPW_AND_EXPIRE) {
                LOGGER.debug(pwmSession, "marking user password as expired");
                theUser.expirePassword();
            }

            // mark the event log
            pwmApplication.getAuditManager().submit(AuditEvent.RECOVER_PASSWORD, pwmSession.getUserInfoBean(), pwmSession);

            // send email or SMS
            final String toAddress = PasswordUtility.sendNewPassword(pwmSession.getUserInfoBean(), pwmApplication, LdapUserDataReader.appProxiedReader(
                    pwmApplication, userIdentity), newPassword, pwmSession.getSessionStateBean().getLocale());

            pwmSession.getSessionStateBean().setSessionSuccess(Message.SUCCESS_PASSWORDSEND, toAddress);
            pwmRequest.forwardToSuccessPage();
        } catch (PwmException e) {
            LOGGER.warn(pwmSession,"unexpected error setting new password during recovery process for user: " + e.getMessage());
            pwmRequest.respondWithError(e.getErrorInformation());
        } catch (ChaiOperationException e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,"unexpected ldap error while processing recovery action " + pwmApplication.getConfig().getRecoveryAction() + ", error: " + e.getMessage());
            LOGGER.warn(pwmSession,errorInformation.toDebugStr());
            pwmRequest.respondWithError(errorInformation);
        } finally {
            pwmSession.clearForgottenPasswordBean();
            pwmSession.unauthenticateUser();
        }
    }

    public static Map<Challenge, String> readResponsesFromHttpRequest(
            final PwmRequest req,
            final ChallengeSet challengeSet
    )
            throws ChaiValidationException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final Map<Challenge, String> responses = new LinkedHashMap<>();

        int counter = 0;
        for (final Challenge loopChallenge : challengeSet.getChallenges()) {
            counter++;
            final String answer = req.readStringParameter(PwmConstants.PARAM_RESPONSE_PREFIX + counter);

            responses.put(loopChallenge, answer.length() > 0 ? answer : "");
        }

        return responses;
    }

    private void validateRequiredAttributes(final PwmRequest pwmRequest, final ChaiUser theUser, final ForgottenPasswordBean forgottenPasswordBean)
            throws ChaiUnavailableException, PwmDataValidationException, PwmUnrecoverableException
    {
        final Locale userLocale = pwmRequest.getLocale();

        final List<FormConfiguration> requiredAttributesForm = forgottenPasswordBean.getAttributeForm();

        if (requiredAttributesForm.isEmpty()) {
            return;
        }

        final Map<FormConfiguration,String> formValues = Validator.readFormValuesFromRequest(pwmRequest.getHttpServletRequest(), requiredAttributesForm, userLocale);
        for (final FormConfiguration paramConfig : formValues.keySet()) {
            final String attrName = paramConfig.getName();

            try {
                if (theUser.compareStringAttribute(attrName, formValues.get(paramConfig))) {
                    LOGGER.trace(pwmRequest, "successful validation of ldap attribute value for '" + attrName + "'");
                } else {
                    throw new PwmDataValidationException(new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE, "incorrect value for '" + attrName + "'", new String[]{attrName}));
                }
            } catch (ChaiOperationException e) {
                LOGGER.error(pwmRequest, "error during param validation of '" + attrName + "', error: " + e.getMessage());
                throw new PwmDataValidationException(new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE, "ldap error testing value for '" + attrName + "'", new String[]{attrName}));
            }
        }
    }

    private static String initializeAndSendToken(
            final PwmRequest pwmRequest,
            final UserInfoBean userInfoBean,
            final MessageSendMethod tokenSendMethod

    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final Configuration config = pwmRequest.getConfig();
        final UserIdentity userIdentity = userInfoBean.getUserIdentity();
        final Map<String,String> tokenMapData = new HashMap<>();

        try {
            final Date userLastPasswordChange = PasswordUtility.determinePwdLastModified(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getSessionLabel(),
                    userIdentity
            );
            if (userLastPasswordChange != null) {
                final String userChangeString = PwmConstants.DEFAULT_DATETIME_FORMAT.format(userLastPasswordChange);
                tokenMapData.put(PwmConstants.TOKEN_KEY_PWD_CHG_DATE, userChangeString);
            }
        } catch (ChaiUnavailableException e) {
            LOGGER.error(pwmRequest, "unexpected error reading user's last password change time");
        }

        final RestTokenDataClient.TokenDestinationData inputDestinationData = new RestTokenDataClient.TokenDestinationData(
                userInfoBean.getUserEmailAddress(),
                userInfoBean.getUserSmsNumber(),
                null
        );

        final RestTokenDataClient restTokenDataClient = new RestTokenDataClient(pwmRequest.getPwmApplication());
        final RestTokenDataClient.TokenDestinationData outputDestrestTokenDataClient = restTokenDataClient.figureDestTokenDisplayString(
                pwmRequest.getSessionLabel(),
                inputDestinationData,
                userIdentity,
                pwmRequest.getLocale());

        final String tokenDestinationAddress = outputDestrestTokenDataClient.getDisplayValue();
        final Set<String> destinationValues = new HashSet<>();
        if (outputDestrestTokenDataClient.getEmail() != null) {
            destinationValues.add(outputDestrestTokenDataClient.getEmail());
        }
        if (outputDestrestTokenDataClient.getSms() != null) {
            destinationValues.add(outputDestrestTokenDataClient.getSms());
        }

        final String tokenKey;
        TokenPayload tokenPayload;
        try {
            tokenPayload = pwmRequest.getPwmApplication().getTokenService().createTokenPayload(TOKEN_NAME, tokenMapData, userIdentity, destinationValues);
            tokenKey = pwmRequest.getPwmApplication().getTokenService().generateNewToken(tokenPayload, pwmRequest.getPwmSession());
        } catch (PwmOperationalException e) {
            throw new PwmUnrecoverableException(e.getErrorInformation());
        }

        LOGGER.debug(pwmRequest, "generated token code for session");

        final EmailItemBean emailItemBean = config.readSettingAsEmail(PwmSetting.EMAIL_CHALLENGE_TOKEN, pwmRequest.getLocale());
        final String smsMessage = config.readSettingAsLocalizedString(PwmSetting.SMS_CHALLENGE_TOKEN_TEXT, pwmRequest.getLocale());
        final UserDataReader userDataReader = LdapUserDataReader.appProxiedReader(pwmRequest.getPwmApplication(), userIdentity);

        TokenService.TokenSender.sendToken(
                pwmRequest.getPwmApplication(),
                userInfoBean,
                userDataReader,
                emailItemBean,
                tokenSendMethod,
                outputDestrestTokenDataClient.getEmail(),
                outputDestrestTokenDataClient.getSms(),
                smsMessage,
                tokenKey
        );

        StatisticsManager.incrementStat(pwmRequest, Statistic.RECOVERY_TOKENS_SENT);
        return tokenDestinationAddress;
    }

    private static List<FormConfiguration> figureAttributeForm(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmOperationalException, PwmUnrecoverableException
    {
        final List<FormConfiguration> requiredAttributesForm = pwmApplication.getConfig().readSettingAsForm(PwmSetting.CHALLENGE_REQUIRED_ATTRIBUTES);
        if (requiredAttributesForm.isEmpty()) {
            return requiredAttributesForm;
        }

        final UserDataReader userDataReader = LdapUserDataReader.appProxiedReader(pwmApplication, userIdentity);
        final List<FormConfiguration> returnList = new ArrayList<>();
        for (final FormConfiguration formItem : requiredAttributesForm) {
            if (formItem.isRequired()) {
                returnList.add(formItem);
            } else {
                try {
                    final String currentValue = userDataReader.readStringAttribute(formItem.getName());
                    if (currentValue != null && currentValue.length() > 0) {
                        returnList.add(formItem);
                    } else {
                        LOGGER.trace(sessionLabel, "excluding optional required attribute(" + formItem.getName() + "), user has no value");
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
            final PwmRequest pwmRequest,
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
                        final ChaiUser proxiedUser = pwmRequest.getPwmApplication().getProxiedChaiUser(userIdentity);
                        LOGGER.debug(pwmSession, "executing post-forgotten password configured actions to user " + proxiedUser.getEntryDN());
                        final List<ActionConfiguration> configValues = pwmRequest.getConfig().readSettingAsAction(PwmSetting.FORGOTTEN_USER_POST_ACTIONS);
                        final ActionExecutor.ActionExecutorSettings settings = new ActionExecutor.ActionExecutorSettings();
                        settings.setExpandPwmMacros(true);
                        settings.setUserInfoBean(pwmSession.getUserInfoBean());
                        final ActionExecutor actionExecutor = new ActionExecutor(pwmRequest.getPwmApplication());
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

        pwmRequest.getPwmSession().getUserInfoBean().addPostChangePasswordActions("forgottenPasswordPostActions", postAction);

    }

    private static void verifyUserEligibility(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserInfoBean userInfoBean,
            final ResponseSet responseSet,
            final ForgottenPasswordBean.RecoveryFlags recoveryFlags
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        if (!recoveryFlags.isAllowWhenLdapIntruderLocked()) {
            final ChaiUser chaiUser = pwmApplication.getProxiedChaiUser(userInfoBean.getUserIdentity());
            try {
                if (chaiUser.isLocked()) {
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_INTRUDER_LDAP));
                }
            } catch (ChaiOperationException e) {
                LOGGER.error(sessionLabel, "error checking user '" + userInfoBean.getUserIdentity() + "' ldap intruder lock status: " + e.getMessage());
            }
        }

        if (!recoveryFlags.isOtpRequired() && !recoveryFlags.isTokenRequired() && !recoveryFlags.isAttributesRequired() && !recoveryFlags.isResponsesRequired()) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, "trying to advance through forgotten password but no recovery verification steps are enabled"));
        }

        if (recoveryFlags.isResponsesRequired()) {
            if (responseSet == null) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES);
                throw new PwmUnrecoverableException(errorInformation);
            }

            final ChallengeSet challengeSet = userInfoBean.getChallengeProfile().getChallengeSet();

            try {
                if (responseSet.meetsChallengeSetRequirements(challengeSet)) {
                    if (challengeSet.getRequiredChallenges().isEmpty() && (challengeSet.getMinRandomRequired() <= 0)) {
                        final String errorMsg = "configured challenge set policy for " + userInfoBean.getUserIdentity().toString() + " is empty, user not qualified to recover password";
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NO_CHALLENGES, errorMsg);
                        throw new PwmUnrecoverableException(errorInformation);
                    }
                }
            } catch (ChaiValidationException e) {
                final String errorMsg = "stored response set for user '" + userInfoBean.getUserIdentity() + "' do not meet current challenge set requirements: " + e.getLocalizedMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES, errorMsg);
                throw new PwmUnrecoverableException(errorInformation);
            }
        }

        if (recoveryFlags.isOtpRequired()) {
            if (userInfoBean.getOtpUserRecord() == null) {
                final String errorMsg = "could not find a one time password configuration for " + userInfoBean.getUserIdentity();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NO_OTP_CONFIGURATION, errorMsg);
                throw new PwmUnrecoverableException(errorInformation);
            }
        }

    }

    private static void initForgottenPasswordBean(
            final PwmApplication pwmApplication,
            final Locale locale,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        final UserStatusReader userStatusReader = new UserStatusReader(pwmApplication);
        final UserInfoBean userInfoBean = new UserInfoBean();
        userStatusReader.populateUserInfoBean(
                sessionLabel,
                userInfoBean,
                locale,
                userIdentity,
                null
        );

        final ForgottenPasswordBean.RecoveryFlags recoveryFlags = calculateRecoveryFlags(
                pwmApplication
        );

        final ResponseSet responseSet;
        final ChallengeSet challengeSet;
        if (recoveryFlags.isResponsesRequired()) {
            final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userInfoBean.getUserIdentity());
            responseSet = pwmApplication.getCrService().readUserResponseSet(
                    sessionLabel,
                    userInfoBean.getUserIdentity(),
                    theUser
            );
            try {
                challengeSet = responseSet == null ? null : responseSet.getChallengeSet();
            } catch (ChaiValidationException e) {
                final String errorMsg = "unable to determine presentable challengeSet for stored responses: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NO_CHALLENGES, errorMsg);
                throw new PwmUnrecoverableException(errorInformation);
            }
        } else {
            responseSet = null;
            challengeSet = null;
        }

        final List<FormConfiguration> attributeForm = figureAttributeForm(pwmApplication, sessionLabel, userIdentity);

        verifyUserEligibility(pwmApplication, sessionLabel, userInfoBean, responseSet, recoveryFlags);

        forgottenPasswordBean.setUserInfo(userInfoBean);
        forgottenPasswordBean.setUserLocale(locale);
        forgottenPasswordBean.setResponseSet(responseSet);
        forgottenPasswordBean.setPresentableChallengeSet(challengeSet);
        forgottenPasswordBean.setAttributeForm(attributeForm);

        forgottenPasswordBean.setRecoveryFlags(recoveryFlags);
        forgottenPasswordBean.setProgress(new ForgottenPasswordBean.Progress());
    }

    private static ForgottenPasswordBean.RecoveryFlags calculateRecoveryFlags(
            final PwmApplication pwmApplication
    ) {
        final Configuration config = pwmApplication.getConfig();

        final MessageSendMethod tokenSendMethod = config.readSettingAsTokenSendMethod(PwmSetting.CHALLENGE_TOKEN_SEND_METHOD);
        final List<FormConfiguration> formConfiguration = config.readSettingAsForm(PwmSetting.CHALLENGE_REQUIRED_ATTRIBUTES);

        final boolean requiresResponses = config.readSettingAsBoolean(PwmSetting.CHALLENGE_REQUIRE_RESPONSES);
        final boolean otpRequired = config.readSettingAsBoolean(PwmSetting.FORGOTTEN_PASSWORD_REQUIRE_OTP);
        final boolean tokenRequired = tokenSendMethod != null && !tokenSendMethod.equals(MessageSendMethod.NONE);
        final boolean attributesRequired = formConfiguration != null && !formConfiguration.isEmpty();
        final boolean allowWhenLdapIntruderLocked = config.readSettingAsBoolean(PwmSetting.FORGOTTEN_PASSWORD_ALLOW_WHEN_LOCKED);

        return new ForgottenPasswordBean.RecoveryFlags(
                requiresResponses,
                tokenRequired,
                otpRequired,
                attributesRequired,
                allowWhenLdapIntruderLocked
        );
    }

    private void handleUserVerificationBadAttempt(
            final PwmRequest pwmRequest,
            final ForgottenPasswordBean forgottenPasswordBean,
            final ErrorInformation errorInformation
    )
            throws PwmUnrecoverableException
    {
        LOGGER.debug(pwmRequest, errorInformation);
        pwmRequest.getPwmSession().getSessionStateBean().setSessionError(errorInformation);

        final UserIdentity userIdentity = forgottenPasswordBean.getUserInfo().getUserIdentity();
        StatisticsManager.incrementStat(pwmRequest, Statistic.RECOVERY_FAILURES);
        if (userIdentity != null) {
            UserAuthenticator.simulateBadPassword(userIdentity,
                    pwmRequest.getPwmApplication(), pwmRequest.getPwmSession());
            pwmRequest.getPwmApplication().getIntruderManager().convenience().markUserIdentity(userIdentity,
                    pwmRequest.getPwmSession());
            pwmRequest.getPwmApplication().getIntruderManager().convenience().markAddressAndSession(
                    pwmRequest.getPwmSession());
        }
    }

    private void checkForLocaleSwitch(final PwmRequest pwmRequest, final ForgottenPasswordBean forgottenPasswordBean)
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        if (forgottenPasswordBean.getUserInfo() == null || forgottenPasswordBean.getUserLocale() == null) {
            return;
        }

        if (forgottenPasswordBean.getUserLocale().equals(pwmRequest.getLocale())) {
            return;
        }

        LOGGER.debug(pwmRequest, "user initiated forgotten password recovery using '" + forgottenPasswordBean.getUserLocale() + "' locale, but current request locale is now '"
                + pwmRequest.getLocale() + "', thus, the user progress will be restart and user data will be re-read using current locale");

        try {
            initForgottenPasswordBean(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getLocale(),
                    pwmRequest.getSessionLabel(),
                    forgottenPasswordBean.getUserInfo().getUserIdentity(),
                    forgottenPasswordBean
            );
        } catch (PwmOperationalException e) {
            pwmRequest.getPwmSession().clearForgottenPasswordBean();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, "unexpected error while re-loading user data due to locale change: " + e.getErrorInformation().toDebugStr());
            LOGGER.error(pwmRequest, errorInformation.toDebugStr());
            pwmRequest.getPwmSession().getSessionStateBean().setSessionError(errorInformation);
        }
    }

    private static MessageSendMethod figureTokenSendPreference(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserInfoBean userInfoBean
    )
            throws PwmUnrecoverableException
    {
        final MessageSendMethod tokenSendMethod = pwmApplication.getConfig().readSettingAsTokenSendMethod(
                PwmSetting.CHALLENGE_TOKEN_SEND_METHOD);
        if (tokenSendMethod == null || tokenSendMethod.equals(MessageSendMethod.NONE)) {
            return MessageSendMethod.NONE;
        }

        if (!tokenSendMethod.equals(MessageSendMethod.CHOICE_SMS_EMAIL)) {
            return tokenSendMethod;
        }

        final String emailAddress = userInfoBean.getUserEmailAddress();
        final String smsAddress = userInfoBean.getUserSmsNumber();

        final boolean hasEmail = emailAddress != null && emailAddress.length() > 1;
        final boolean hasSms = smsAddress != null && smsAddress.length() > 1;

        if (hasEmail && hasSms) {
            return MessageSendMethod.CHOICE_SMS_EMAIL;
        } else if (hasEmail) {
            LOGGER.debug(sessionLabel, "though token send method is " + MessageSendMethod.CHOICE_SMS_EMAIL + ", no sms address is available for user so defaulting to email method");
            return MessageSendMethod.EMAILONLY;
        } else if (hasSms) {
            LOGGER.debug(sessionLabel, "though token send method is " + MessageSendMethod.CHOICE_SMS_EMAIL + ", no email address is available for user so defaulting to sms method");
            return MessageSendMethod.SMSONLY;
        }


        throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_TOKEN_MISSING_CONTACT));
    }
}



