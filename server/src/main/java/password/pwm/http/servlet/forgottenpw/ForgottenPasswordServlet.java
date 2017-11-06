/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.http.servlet.forgottenpw;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.VerificationMethodSystem;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.PasswordStatus;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.option.RecoveryAction;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.config.profile.ProfileUtility;
import password.pwm.config.profile.ProfileType;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.ForgottenPasswordBean;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.http.servlet.oauth.OAuthForgottenPasswordResults;
import password.pwm.http.servlet.oauth.OAuthMachine;
import password.pwm.http.servlet.oauth.OAuthSettings;
import password.pwm.i18n.Message;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.AuthenticationUtility;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.intruder.RecordType;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.svc.token.TokenPayload;
import password.pwm.svc.token.TokenType;
import password.pwm.util.CaptchaUtility;
import password.pwm.util.PasswordData;
import password.pwm.util.PostChangePasswordAction;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.form.FormUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.operations.cr.NMASCrOperator;
import password.pwm.util.operations.otp.OTPUserRecord;
import password.pwm.ws.server.RestResultBean;
import java.time.Instant;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * User interaction servlet for recovering user's password using secret question/answer
 *
 * @author Jason D. Rivard
 */


@WebServlet(
        name="ForgottenPasswordServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + "/forgottenpassword",
                PwmConstants.URL_PREFIX_PUBLIC + "/forgottenpassword/*",
                PwmConstants.URL_PREFIX_PUBLIC + "/ForgottenPassword",
                PwmConstants.URL_PREFIX_PUBLIC + "/ForgottenPassword/*",
        }
)
public class ForgottenPasswordServlet extends ControlledPwmServlet {
    private static final PwmLogger LOGGER = PwmLogger.forClass(ForgottenPasswordServlet.class);

    public enum ForgottenPasswordAction implements AbstractPwmServlet.ProcessAction {
        search(HttpMethod.POST),
        checkResponses(HttpMethod.POST),
        checkAttributes(HttpMethod.POST),
        enterCode(HttpMethod.POST, HttpMethod.GET),
        enterOtp(HttpMethod.POST),
        reset(HttpMethod.POST),
        actionChoice(HttpMethod.POST),
        tokenChoice(HttpMethod.POST),
        verificationChoice(HttpMethod.POST),
        enterRemoteResponse(HttpMethod.POST),
        oauthReturn(HttpMethod.GET),
        resendToken(HttpMethod.POST),

        ;

        public static boolean isUnlockOnlyFlag() {
            return unlockOnlyFlag;
        }

        public static void setUnlockOnlyFlag(final boolean unlockOnlyFlag) {
            ForgottenPasswordAction.unlockOnlyFlag = unlockOnlyFlag;
        }

        private static boolean unlockOnlyFlag = false;

        private final Collection<HttpMethod> method;

        ForgottenPasswordAction(final HttpMethod... method)
        {
            this.method = Collections.unmodifiableList(Arrays.asList(method));
        }

        public Collection<HttpMethod> permittedMethods()
        {
            return method;
        }
    }

    @Override
    public Class<? extends ProcessAction> getProcessActionsClass() {
        return ForgottenPasswordAction.class;
    }

    public enum ActionChoice {
        unlock,
        resetPassword,
    }

    public enum TokenChoice {
        email,
        sms,
    }

    @Override
    public ProcessStatus preProcessCheck(final PwmRequest pwmRequest) throws PwmUnrecoverableException, IOException, ServletException {

        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        final Configuration config = pwmApplication.getConfig();

        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean(pwmRequest);
        if (!config.readSettingAsBoolean(PwmSetting.FORGOTTEN_PASSWORD_ENABLE)) {
            pwmRequest.respondWithError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            return ProcessStatus.Halt;
        }

        if (pwmSession.isAuthenticated()) {
            pwmRequest.respondWithError(PwmError.ERROR_USERAUTHENTICATED.toInfo());
            return ProcessStatus.Halt;
        }

        if (forgottenPasswordBean.getUserIdentity() != null) {
            pwmApplication.getIntruderManager().convenience().checkUserIdentity(forgottenPasswordBean.getUserIdentity());
        }

        checkForLocaleSwitch(pwmRequest, forgottenPasswordBean);

        final ProcessAction action = this.readProcessAction(pwmRequest);

        // convert a url command like /public/newuser/12321321 to redirect with a process action.
        if (action == null) {
            if (pwmRequest.convertURLtokenCommand()) {
                return ProcessStatus.Halt;
            }
        }

        return ProcessStatus.Continue;
    }

    private static ForgottenPasswordBean forgottenPasswordBean(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        return pwmRequest.getPwmApplication().getSessionStateService().getBean(pwmRequest, ForgottenPasswordBean.class);
    }

    private static void clearForgottenPasswordBean(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        pwmRequest.getPwmApplication().getSessionStateService().clearBean(pwmRequest, ForgottenPasswordBean.class);
    }

    private static ForgottenPasswordProfile forgottenPasswordProfile(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean(pwmRequest);
        return pwmRequest.getConfig().getForgottenPasswordProfiles().get(forgottenPasswordBean.getForgottenPasswordProfileID());
    }

    private boolean insideMinimumLifetime(
            final PwmApplication pwmApp,
            final PwmSession pwmSes,
            final UserInfo userInfo
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        try {

            final Instant inst  = PasswordUtility.determinePwdLastModified(pwmApp, pwmSes.getLabel(), userInfo.getUserIdentity());

            final TimeDuration minimumLifetime;
            {
                final int minimumLifetimeSeconds = userInfo.getPasswordPolicy().getRuleHelper().readIntValue(PwmPasswordRule.MinimumLifetime);
                if (minimumLifetimeSeconds < 1) {
                    return true;
                }

                if (userInfo.getPasswordLastModifiedTime() == null) {
                    LOGGER.debug(pwmSes.getLabel(), "skipping minimum lifetime check, password last set time is unknown");
                    return false;
                }

                minimumLifetime = new TimeDuration(minimumLifetimeSeconds, TimeUnit.SECONDS);
            }

            final TimeDuration passwordAge = TimeDuration.fromCurrent(userInfo.getPasswordLastModifiedTime());
            LOGGER.trace(pwmSes.getLabel(), "beginning check for minimum lifetime, lastModified="
                    + JavaHelper.toIsoDate(userInfo.getPasswordLastModifiedTime())
                    + ", minimumLifetimeSeconds=" + minimumLifetime.asCompactString()
                    + ", passwordAge=" + passwordAge.asCompactString());


            if (userInfo.getPasswordLastModifiedTime().isAfter(Instant.now())) {
                LOGGER.debug(pwmSes.getLabel(), "skipping minimum lifetime check, password lastModified time is in the future");
                return true;
            }

            final boolean passwordTooSoon = passwordAge.isShorterThan(minimumLifetime);
            if (!passwordTooSoon) {
                LOGGER.trace(pwmSes.getLabel(), "minimum lifetime check passed, password age ");
                return true;
            }

            if (userInfo.getPasswordStatus().isExpired() || userInfo.getPasswordStatus().isPreExpired() || userInfo.getPasswordStatus().isWarnPeriod()) {
                LOGGER.debug(pwmSes.getLabel(), "current password is too young, but skipping enforcement of minimum lifetime check because current password is expired");
                return true;
            }


            //PasswordUtility.checkIfPasswordWithinMinimumLifetime(
            //        pwmSes.getSessionManager().getActor(pwmApp),
            //        pwmSes.getLabel(),
            //        userInfo.getPasswordPolicy(),
            //        userInfo.getPasswordLastModifiedTime(),
            //        userInfo.getPasswordStatus()
            //);
        } catch (PwmException e) {
            return false;
        }
        return false;
    }


    @ActionHandler(action = "actionChoice")
    private ProcessStatus processActionChoice(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, ServletException, IOException, ChaiUnavailableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean(pwmRequest);
        final ForgottenPasswordProfile forgottenPasswordProfile = forgottenPasswordProfile(pwmRequest);

        final boolean resendEnabled = forgottenPasswordProfile.readSettingAsBoolean(PwmSetting.TOKEN_RESEND_ENABLE);

        if (resendEnabled) {
            // clear token dest info in case we got here from a user 'go-back' request
            forgottenPasswordBean.getProgress().clearTokenSentStatus();
        }

        if (forgottenPasswordBean.getProgress().isAllPassed()) {
            final String choice = pwmRequest.readParameterAsString("choice");

            final ActionChoice actionChoice = JavaHelper.readEnumFromString(ActionChoice.class, null, choice);
            if (actionChoice != null) {
                switch (actionChoice) {
                    case unlock:
                        this.executeUnlock(pwmRequest);
                        break;

                    case resetPassword:
                        final ForgottenPasswordProfile fpp = forgottenPasswordProfile(pwmRequest);
                        if (fpp.readSettingAsBoolean(PwmSetting.RECOVERY_ALLOW_WHEN_LOCKED)) {
                            try {
                                final boolean insideTime = insideMinimumLifetime(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), pwmRequest.getPwmSession().getUserInfo());
                                if (!insideTime) {
                                    this.executeResetPassword(pwmRequest);
                                }
                            } catch (Exception e) {
                                return ProcessStatus.Halt;
                            }
                        }
                        break;

                    default:
                        JavaHelper.unhandledSwitchStatement(actionChoice);
                }
            }
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler(action = "reset")
    private ProcessStatus processReset(final PwmRequest pwmRequest)
            throws IOException, PwmUnrecoverableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean(pwmRequest);

        clearForgottenPasswordBean(pwmRequest);

        if (forgottenPasswordBean.getUserIdentity() == null) {
            pwmRequest.sendRedirectToContinue();
            return ProcessStatus.Halt;
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler(action = "tokenChoice")
    private ProcessStatus processTokenChoice(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, ServletException, IOException, ChaiUnavailableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean(pwmRequest);
        if (forgottenPasswordBean.getProgress().getTokenSendChoice() == MessageSendMethod.CHOICE_SMS_EMAIL) {
            final String choice = pwmRequest.readParameterAsString("choice");
            final TokenChoice tokenChoice = JavaHelper.readEnumFromString(TokenChoice.class, null, choice);
            if (tokenChoice != null) {
                switch (tokenChoice) {
                    case email:
                        forgottenPasswordBean.getProgress().setTokenSendChoice(MessageSendMethod.EMAILONLY);
                        break;

                    case sms:
                        forgottenPasswordBean.getProgress().setTokenSendChoice(MessageSendMethod.SMSONLY);
                        break;

                    default:
                        JavaHelper.unhandledSwitchStatement(tokenChoice);
                }
            }
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler(action = "verificationChoice")
    private ProcessStatus processVerificationChoice(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, ServletException, IOException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean(pwmRequest);
        final String requestedChoiceStr = pwmRequest.readParameterAsString("choice");
        final LinkedHashSet<IdentityVerificationMethod> remainingAvailableOptionalMethods = new LinkedHashSet<>(
                ForgottenPasswordUtil.figureRemainingAvailableOptionalAuthMethods(pwmRequest, forgottenPasswordBean)
        );
        pwmRequest.setAttribute(PwmRequestAttribute.AvailableAuthMethods, remainingAvailableOptionalMethods);

        IdentityVerificationMethod requestedChoice = null;
        if (requestedChoiceStr != null && !requestedChoiceStr.isEmpty()) {
            try {
                requestedChoice = IdentityVerificationMethod.valueOf(requestedChoiceStr);
            } catch (IllegalArgumentException e) {
                final String errorMsg = "unknown verification method requested";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,errorMsg);
                setLastError(pwmRequest, errorInformation);
                pwmRequest.forwardToJsp(JspUrl.RECOVER_PASSWORD_METHOD_CHOICE);
                return ProcessStatus.Halt;
            }
        }

        if (remainingAvailableOptionalMethods.contains(requestedChoice)) {
            forgottenPasswordBean.getProgress().setInProgressVerificationMethod(requestedChoice);
            pwmRequest.setAttribute(PwmRequestAttribute.ForgottenPasswordOptionalPageView,"true");
            forwardUserBasedOnRecoveryMethod(pwmRequest, requestedChoice);
            return ProcessStatus.Continue;
        } else if (requestedChoice != null) {
            final String errorMsg = "requested verification method is not available at this time";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,errorMsg);
            setLastError(pwmRequest, errorInformation);
        }

        pwmRequest.forwardToJsp(JspUrl.RECOVER_PASSWORD_METHOD_CHOICE);

        return ProcessStatus.Halt;
    }

    @ActionHandler(action = "search")
    private ProcessStatus processSearch(final PwmRequest pwmRequest)
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Locale userLocale = pwmRequest.getLocale();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        final String contextParam = pwmRequest.readParameterAsString(PwmConstants.PARAM_CONTEXT);
        final String ldapProfile = pwmRequest.readParameterAsString(PwmConstants.PARAM_LDAP_PROFILE);

        // clear the bean
        clearForgottenPasswordBean(pwmRequest);

        if (CaptchaUtility.captchaEnabledForRequest(pwmRequest)) {
            if (!CaptchaUtility.verifyReCaptcha(pwmRequest)) {
                final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_BAD_CAPTCHA_RESPONSE);
                LOGGER.debug(pwmRequest, errorInfo);
                setLastError(pwmRequest, errorInfo);
                return ProcessStatus.Continue;
            }
        }

        final List<FormConfiguration> forgottenPasswordForm = pwmApplication.getConfig().readSettingAsForm(
                PwmSetting.FORGOTTEN_PASSWORD_SEARCH_FORM);

        Map<FormConfiguration, String> formValues = new LinkedHashMap<>();

        try {
            //read the values from the request
            formValues = FormUtility.readFormValuesFromRequest(pwmRequest, forgottenPasswordForm, userLocale);

            // check for intruder search values
            pwmApplication.getIntruderManager().convenience().checkAttributes(formValues);

            // see if the values meet the configured form requirements.
            FormUtility.validateFormValues(pwmRequest.getConfig(), formValues, userLocale);

            final String searchFilter;
            {
                final String configuredSearchFilter = pwmApplication.getConfig().readSettingAsString(PwmSetting.FORGOTTEN_PASSWORD_SEARCH_FILTER);
                if (configuredSearchFilter == null || configuredSearchFilter.isEmpty()) {
                    searchFilter = FormUtility.ldapSearchFilterForForm(pwmApplication, forgottenPasswordForm);
                    LOGGER.trace(pwmSession,"auto generated ldap search filter: " + searchFilter);
                } else {
                    searchFilter = configuredSearchFilter;
                }
            }

            // convert the username field to an identity
            final UserIdentity userIdentity;
            {
                final UserSearchEngine userSearchEngine = pwmApplication.getUserSearchEngine();
                final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                        .filter(searchFilter)
                        .formValues(formValues)
                        .contexts(Collections.singletonList(contextParam))
                        .ldapProfile(ldapProfile)
                        .build();

                userIdentity = userSearchEngine.performSingleUserSearch(searchConfiguration, pwmRequest.getSessionLabel());
            }

            if (userIdentity == null) {
                pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
                pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_FAILURES);
                setLastError(pwmRequest, PwmError.ERROR_CANT_MATCH_USER.toInfo());
                return ProcessStatus.Continue;
            }

            AuthenticationUtility.checkIfUserEligibleToAuthentication(pwmApplication, userIdentity);

            final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean(pwmRequest);
            initForgottenPasswordBean(pwmRequest, userIdentity, forgottenPasswordBean);

            // clear intruder search values
            pwmApplication.getIntruderManager().convenience().clearAttributes(formValues);
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES,e.getErrorInformation().getDetailedErrorMsg(),e.getErrorInformation().getFieldValues());
            pwmApplication.getIntruderManager().mark(RecordType.ADDRESS, pwmSession.getSessionStateBean().getSrcAddress(), pwmRequest.getSessionLabel());
            pwmApplication.getIntruderManager().convenience().markAttributes(formValues, pwmSession);

            LOGGER.debug(pwmSession,errorInfo.toDebugStr());
            setLastError(pwmRequest, errorInfo);
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler(action = "enterCode")
    private ProcessStatus processEnterCode(final PwmRequest pwmRequest)
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean(pwmRequest);
        final String userEnteredCode = pwmRequest.readParameterAsString(PwmConstants.PARAM_TOKEN);
        final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo(pwmRequest, forgottenPasswordBean);
        TokenPayload tokenPayload = null;

        ErrorInformation errorInformation = null;
        try {
            tokenPayload = pwmRequest.getPwmApplication().getTokenService().processUserEnteredCode(
                    pwmRequest.getPwmSession(),
                    forgottenPasswordBean.getUserIdentity() == null ? null : forgottenPasswordBean.getUserIdentity(),
                    TokenType.FORGOTTEN_PW,
                    userEnteredCode
            );
            if (tokenPayload != null) {
                // token correct
                if (forgottenPasswordBean.getUserIdentity() == null) {
                    // clean session, user supplied token (clicked email, etc) and this is first request
                    initForgottenPasswordBean(
                            pwmRequest,
                            tokenPayload.getUserIdentity(),
                            forgottenPasswordBean
                    );
                }
                forgottenPasswordBean.getProgress().getSatisfiedMethods().add(IdentityVerificationMethod.TOKEN);
                StatisticsManager.incrementStat(pwmRequest.getPwmApplication(), Statistic.RECOVERY_TOKENS_PASSED);
            }
        } catch (PwmOperationalException e) {
            final String errorMsg = "token incorrect: " + e.getMessage();
            errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,errorMsg);
        }

        if (!forgottenPasswordBean.getProgress().getSatisfiedMethods().contains(IdentityVerificationMethod.TOKEN)) {
            if (errorInformation == null) {
                errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT);
            }
            handleUserVerificationBadAttempt(pwmRequest, forgottenPasswordBean, errorInformation);
        }

        // bug fix location
        final ForgottenPasswordProfile fpp = forgottenPasswordProfile(pwmRequest);
        if (fpp.readSettingAsBoolean(PwmSetting.RECOVERY_ALLOW_WHEN_LOCKED)) {
            try {
                final boolean insideTime = insideMinimumLifetime(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), userInfo);
                if (insideTime) {
                    ForgottenPasswordAction.setUnlockOnlyFlag(true);
                }
            } catch (Exception e) {
                LOGGER.debug(pwmRequest, "ERROR: " + e.getMessage() + " getting minimum lifetime value.");
            }
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler(action = "enterRemoteResponse")
    private ProcessStatus processEnterRemoteResponse(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final String PREFIX = "remote-";
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean(pwmRequest);
        final VerificationMethodSystem remoteRecoveryMethod = forgottenPasswordBean.getProgress().getRemoteRecoveryMethod();

        final Map<String,String> remoteResponses = new LinkedHashMap<>();
        {
            final Map<String,String> inputMap = pwmRequest.readParametersAsMap();
            for (final Map.Entry<String, String> entry : inputMap.entrySet()) {
                final String name = entry.getKey();
                if (name != null && name.startsWith(PREFIX)) {
                    final String strippedName = name.substring(PREFIX.length(), name.length());
                    final String value = entry.getValue();
                    remoteResponses.put(strippedName, value);
                }
            }
        }

        final ErrorInformation errorInformation = remoteRecoveryMethod.respondToPrompts(remoteResponses);

        if (remoteRecoveryMethod.getVerificationState() == VerificationMethodSystem.VerificationState.COMPLETE) {
            forgottenPasswordBean.getProgress().getSatisfiedMethods().add(IdentityVerificationMethod.REMOTE_RESPONSES);
        }

        if (remoteRecoveryMethod.getVerificationState() == VerificationMethodSystem.VerificationState.FAILED) {
            forgottenPasswordBean.getProgress().setRemoteRecoveryMethod(null);
            pwmRequest.respondWithError(errorInformation,true);
            handleUserVerificationBadAttempt(pwmRequest, forgottenPasswordBean, errorInformation);
            LOGGER.debug(pwmRequest, "unsuccessful remote response verification input: " + errorInformation.toDebugStr());
            return ProcessStatus.Continue;
        }

        if (errorInformation != null) {
            setLastError(pwmRequest, errorInformation);
            handleUserVerificationBadAttempt(pwmRequest, forgottenPasswordBean, errorInformation);
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler(action = "enterOtp")
    private ProcessStatus processEnterOtpToken(final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean(pwmRequest);
        final String userEnteredCode = pwmRequest.readParameterAsString(PwmConstants.PARAM_TOKEN);
        LOGGER.debug(pwmRequest, String.format("entered OTP: %s", userEnteredCode));

        final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo(pwmRequest, forgottenPasswordBean);
        final OTPUserRecord otpUserRecord = userInfo.getOtpUserRecord();

        final boolean otpPassed;
        if (otpUserRecord != null) {
            LOGGER.info(pwmRequest, "checking entered OTP");
            try {
                // forces service to use proxy account to update (write) updated otp record if necessary.
                otpPassed = pwmRequest.getPwmApplication().getOtpService().validateToken(
                        null,
                        forgottenPasswordBean.getUserIdentity(),
                        otpUserRecord,
                        userEnteredCode,
                        true
                );

                if (otpPassed) {
                    StatisticsManager.incrementStat(pwmRequest, Statistic.RECOVERY_OTP_PASSED);
                    LOGGER.debug(pwmRequest, "one time password validation has been passed");
                    forgottenPasswordBean.getProgress().getSatisfiedMethods().add(IdentityVerificationMethod.OTP);
                } else {
                    StatisticsManager.incrementStat(pwmRequest, Statistic.RECOVERY_OTP_FAILED);
                    handleUserVerificationBadAttempt(pwmRequest, forgottenPasswordBean, new ErrorInformation(PwmError.ERROR_INCORRECT_OTP_TOKEN));
                }
            } catch (PwmOperationalException e) {
                handleUserVerificationBadAttempt(pwmRequest, forgottenPasswordBean, new ErrorInformation(PwmError.ERROR_INCORRECT_OTP_TOKEN,e.getErrorInformation().toDebugStr()));
            }
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler(action = "oauthReturn")
    private ProcessStatus processOAuthReturn(final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean(pwmRequest);
        if (forgottenPasswordBean.getProgress().getInProgressVerificationMethod() != IdentityVerificationMethod.OAUTH) {
            LOGGER.debug(pwmRequest, "oauth return detected, however current session did not issue an oauth request; will restart forgotten password sequence");
            pwmRequest.getPwmApplication().getSessionStateService().clearBean(pwmRequest, ForgottenPasswordBean.class);
            pwmRequest.sendRedirect(PwmServletDefinition.ForgottenPassword);
            return ProcessStatus.Halt;
        }

        if (forgottenPasswordBean.getUserIdentity() == null) {
            LOGGER.debug(pwmRequest, "oauth return detected, however current session does not have a user identity stored; will restart forgotten password sequence");
            pwmRequest.getPwmApplication().getSessionStateService().clearBean(pwmRequest, ForgottenPasswordBean.class);
            pwmRequest.sendRedirect(PwmServletDefinition.ForgottenPassword);
            return ProcessStatus.Halt;
        }

        final String encryptedResult = pwmRequest.readParameterAsString(PwmConstants.PARAM_RECOVERY_OAUTH_RESULT, PwmHttpRequestWrapper.Flag.BypassValidation);
        final OAuthForgottenPasswordResults results = pwmRequest.getPwmApplication().getSecureService().decryptObject(encryptedResult, OAuthForgottenPasswordResults.class);
        LOGGER.trace(pwmRequest, "received ");

        final String userDNfromOAuth = results.getUsername();
        if (userDNfromOAuth == null || userDNfromOAuth.isEmpty()) {
            final String errorMsg = "oauth server coderesolver endpoint did not return a username value";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        final UserIdentity oauthUserIdentity;
        {
            final UserSearchEngine userSearchEngine =pwmRequest.getPwmApplication().getUserSearchEngine();
            try {
                oauthUserIdentity = userSearchEngine.resolveUsername(userDNfromOAuth, null, null, pwmRequest.getSessionLabel());
            } catch (PwmOperationalException e) {
                final String errorMsg = "unexpected error searching for oauth supplied username in ldap; error: " + e.getMessage() ;
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR, errorMsg);
                throw new PwmUnrecoverableException(errorInformation);
            }
        }

        final boolean userMatch;
        {
            final UserIdentity userIdentityInBean = forgottenPasswordBean.getUserIdentity();
            userMatch = userIdentityInBean != null && userIdentityInBean.equals(oauthUserIdentity);
        }

        if (userMatch) {
            forgottenPasswordBean.getProgress().getSatisfiedMethods().add(IdentityVerificationMethod.OAUTH);
        } else {
            final String errorMsg = "oauth server username does not match previously identified user";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler(action = "checkResponses")
    private ProcessStatus processCheckResponses(final PwmRequest pwmRequest)
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean(pwmRequest);

        if (forgottenPasswordBean.getUserIdentity() == null) {
            return ProcessStatus.Continue;
        }
        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();

        final ResponseSet responseSet = ForgottenPasswordUtil.readResponseSet(pwmRequest, forgottenPasswordBean);
        if (responseSet == null) {
            final String errorMsg = "attempt to check responses, but responses are not loaded into session bean";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        try {
            // read the supplied responses from the user
            final Map<Challenge, String> crMap = ForgottenPasswordUtil.readResponsesFromHttpRequest(
                    pwmRequest,
                    forgottenPasswordBean.getPresentableChallengeSet()
            );

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
                return ProcessStatus.Continue;
            }
        } catch (ChaiValidationException e) {
            LOGGER.debug(pwmRequest, "chai validation error checking user responses: " + e.getMessage());
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.forChaiError(e.getErrorCode()));
            handleUserVerificationBadAttempt(pwmRequest, forgottenPasswordBean, errorInformation);
            return ProcessStatus.Continue;
        }

        forgottenPasswordBean.getProgress().getSatisfiedMethods().add(IdentityVerificationMethod.CHALLENGE_RESPONSES);

        return ProcessStatus.Continue;
    }

    @ActionHandler(action = "resendToken")
    private ProcessStatus processResendToken(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, IOException
    {
        {
            final ForgottenPasswordProfile forgottenPasswordProfile = forgottenPasswordProfile(pwmRequest);
            final boolean resendEnabled = forgottenPasswordProfile.readSettingAsBoolean(PwmSetting.TOKEN_RESEND_ENABLE);
            if (!resendEnabled) {
                final String errorMsg = "token resend is not enabled";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg);
                throw new PwmUnrecoverableException(errorInformation);
            }
        }

        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean(pwmRequest);

        if (!forgottenPasswordBean.getProgress().isTokenSent()) {
            final String errorMsg = "attempt to resend token, but initial token has not yet been sent";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        {
            LOGGER.trace(pwmRequest, "preparing to send a new token to user");
            final long delayTime = Long.parseLong(pwmRequest.getConfig().readAppProperty(AppProperty.TOKEN_RESEND_DELAY_MS));
            JavaHelper.pause(delayTime);
        }

        {
            final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo(pwmRequest, forgottenPasswordBean);
            final MessageSendMethod tokenSendMethod = forgottenPasswordBean.getProgress().getTokenSendChoice();
            ForgottenPasswordUtil.initializeAndSendToken(pwmRequest, userInfo, tokenSendMethod);
        }

        final RestResultBean restResultBean = RestResultBean.forSuccessMessage(pwmRequest, Message.Success_TokenResend);
        pwmRequest.outputJsonResult(restResultBean);
        return ProcessStatus.Halt;
    }



    @ActionHandler(action = "checkAttributes")
    private ProcessStatus processCheckAttributes(final PwmRequest pwmRequest)
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        //final SessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean(pwmRequest);

        if (forgottenPasswordBean.getUserIdentity() == null) {
            return ProcessStatus.Continue;
        }
        final UserIdentity userIdentity =forgottenPasswordBean.getUserIdentity();

        try { // check attributes
            final ChaiUser theUser = pwmRequest.getPwmApplication().getProxiedChaiUser(userIdentity);
            final Locale userLocale = pwmRequest.getLocale();

            final List<FormConfiguration> requiredAttributesForm = forgottenPasswordBean.getAttributeForm();

            if (requiredAttributesForm.isEmpty()) {
                return ProcessStatus.Continue;
            }

            final Map<FormConfiguration,String> formValues = FormUtility.readFormValuesFromRequest(
                    pwmRequest, requiredAttributesForm, userLocale);

            for (final Map.Entry<FormConfiguration, String> entry : formValues.entrySet()) {
                final FormConfiguration paramConfig = entry.getKey();
                final String attrName = paramConfig.getName();

                try {
                    if (theUser.compareStringAttribute(attrName, entry.getValue())) {
                        LOGGER.trace(pwmRequest, "successful validation of ldap attribute value for '" + attrName + "'");
                    } else {
                        throw new PwmDataValidationException(new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE, "incorrect value for '" + attrName + "'", new String[]{attrName}));
                    }
                } catch (ChaiOperationException e) {
                    LOGGER.error(pwmRequest, "error during param validation of '" + attrName + "', error: " + e.getMessage());
                    throw new PwmDataValidationException(new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE, "ldap error testing value for '" + attrName + "'", new String[]{attrName}));
                }
            }

            forgottenPasswordBean.getProgress().getSatisfiedMethods().add(IdentityVerificationMethod.ATTRIBUTES);
        } catch (PwmDataValidationException e) {
            handleUserVerificationBadAttempt(pwmRequest, forgottenPasswordBean, new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE,e.getErrorInformation().toDebugStr()));
        }

        return ProcessStatus.Continue;
    }

    @Override
    protected void nextStep(final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmRequest.getConfig();
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean(pwmRequest);

        final ForgottenPasswordBean.RecoveryFlags recoveryFlags = forgottenPasswordBean.getRecoveryFlags();
        final ForgottenPasswordBean.Progress progress = forgottenPasswordBean.getProgress();

        // check for identified user;
        if (forgottenPasswordBean.getUserIdentity() == null) {
            pwmRequest.addFormInfoToRequestAttr(PwmSetting.FORGOTTEN_PASSWORD_SEARCH_FORM,false,false);
            pwmRequest.forwardToJsp(JspUrl.RECOVER_PASSWORD_SEARCH);
            return;
        }

        final ForgottenPasswordProfile forgottenPasswordProfile = forgottenPasswordProfile(pwmRequest);
        {
            final Map<String, ForgottenPasswordProfile> profileIDList = pwmRequest.getConfig().getForgottenPasswordProfiles();
            final String profileDebugMsg = forgottenPasswordProfile != null && profileIDList != null && profileIDList.size() > 1
                    ? " profile=" + forgottenPasswordProfile.getIdentifier() + ", "
                    : "";
            LOGGER.trace(pwmRequest, "entering forgotten password progress engine: "
                    + profileDebugMsg
                    + "flags=" + JsonUtil.serialize(recoveryFlags) + ", "
                    + "progress=" + JsonUtil.serialize(progress));
        }

        if (forgottenPasswordProfile == null) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_NO_PROFILE_ASSIGNED));
        }

        // check for previous authentication
        if (recoveryFlags.getRequiredAuthMethods().contains(IdentityVerificationMethod.PREVIOUS_AUTH) || recoveryFlags.getOptionalAuthMethods().contains(IdentityVerificationMethod.PREVIOUS_AUTH)) {
            if (!progress.getSatisfiedMethods().contains(IdentityVerificationMethod.PREVIOUS_AUTH)) {
                final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
                final String userGuid = LdapOperationsHelper.readLdapGuidValue(pwmApplication, pwmRequest.getSessionLabel(), userIdentity, true);
                if (ForgottenPasswordUtil.checkAuthRecord(pwmRequest, userGuid)) {
                    LOGGER.debug(pwmRequest, "marking " + IdentityVerificationMethod.PREVIOUS_AUTH + " method as satisfied");
                    progress.getSatisfiedMethods().add(IdentityVerificationMethod.PREVIOUS_AUTH);
                }
            }
        }

        // dispatch required auth methods.
        for (final IdentityVerificationMethod method : recoveryFlags.getRequiredAuthMethods()) {
            if (!progress.getSatisfiedMethods().contains(method)) {
                forwardUserBasedOnRecoveryMethod(pwmRequest, method);
                return;
            }
        }

        // redirect if an verification method is in progress
        if (progress.getInProgressVerificationMethod() != null) {
            if (progress.getSatisfiedMethods().contains(progress.getInProgressVerificationMethod())) {
                progress.setInProgressVerificationMethod(null);
            } else {
                pwmRequest.setAttribute(PwmRequestAttribute.ForgottenPasswordOptionalPageView,"true");
                forwardUserBasedOnRecoveryMethod(pwmRequest, progress.getInProgressVerificationMethod());
                return;
            }
        }

        // check if more optional methods required
        if (recoveryFlags.getMinimumOptionalAuthMethods() > 0) {
            final Set<IdentityVerificationMethod> satisfiedOptionalMethods = ForgottenPasswordUtil.figureSatisfiedOptionalAuthMethods(recoveryFlags,progress);
            if (satisfiedOptionalMethods.size() < recoveryFlags.getMinimumOptionalAuthMethods()) {
                final Set<IdentityVerificationMethod> remainingAvailableOptionalMethods = ForgottenPasswordUtil.figureRemainingAvailableOptionalAuthMethods(pwmRequest, forgottenPasswordBean);
                if (remainingAvailableOptionalMethods.isEmpty()) {
                    final String errorMsg = "additional optional verification methods are needed, however all available optional verification methods have been satisified by user";
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,errorMsg);
                    LOGGER.error(pwmRequest, errorInformation);
                    throw new PwmUnrecoverableException(errorInformation);
                } else {
                    if (remainingAvailableOptionalMethods.size() == 1) {
                        final IdentityVerificationMethod remainingMethod = remainingAvailableOptionalMethods.iterator().next();
                        LOGGER.debug(pwmRequest, "only 1 remaining available optional verification method, will redirect to " + remainingMethod.toString());
                        forwardUserBasedOnRecoveryMethod(pwmRequest, remainingMethod);
                        progress.setInProgressVerificationMethod(remainingMethod);
                        return;
                    }
                }
                processVerificationChoice(pwmRequest);
                return;
            }
        }

        if (progress.getSatisfiedMethods().isEmpty()) {
            final String errorMsg = "forgotten password recovery sequence completed, but user has not actually satisfied any verification methods";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,errorMsg);
            LOGGER.error(pwmRequest, errorInformation);
            throw new PwmUnrecoverableException(errorInformation);
        }

        if (!forgottenPasswordBean.getProgress().isAllPassed()) {
            forgottenPasswordBean.getProgress().setAllPassed(true);
            StatisticsManager.incrementStat(pwmRequest, Statistic.RECOVERY_SUCCESSES);
        }

        final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo(pwmRequest, forgottenPasswordBean);
        try {
            final boolean enforceFromForgotten = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.CHALLENGE_ENFORCE_MINIMUM_PASSWORD_LIFETIME);
            if (enforceFromForgotten) {
                final ChaiUser theUser = pwmApplication.getProxiedChaiUser(forgottenPasswordBean.getUserIdentity());
                PasswordUtility.checkIfPasswordWithinMinimumLifetime(
                        theUser,
                        pwmRequest.getSessionLabel(),
                        userInfo.getPasswordPolicy(),
                        userInfo.getPasswordLastModifiedTime(),
                        userInfo.getPasswordStatus()
                );
            }
        } catch (PwmOperationalException e) {
            if (!forgottenPasswordProfile.readSettingAsBoolean(PwmSetting.RECOVERY_ALLOW_UNLOCK)) {
                throw new PwmUnrecoverableException(e.getErrorInformation());
            }
        }

        LOGGER.trace(pwmRequest, "all recovery checks passed, proceeding to configured recovery action");

        final RecoveryAction recoveryAction = ForgottenPasswordUtil.getRecoveryAction(config, forgottenPasswordBean);
        if (recoveryAction == RecoveryAction.SENDNEWPW || recoveryAction == RecoveryAction.SENDNEWPW_AND_EXPIRE) {
            processSendNewPassword(pwmRequest);
            return;
        }

        if (forgottenPasswordProfile.readSettingAsBoolean(PwmSetting.RECOVERY_ALLOW_UNLOCK)) {
            final PasswordStatus passwordStatus = userInfo.getPasswordStatus();

            if (!passwordStatus.isExpired() && !passwordStatus.isPreExpired()) {
                try {
                    final ChaiUser theUser = pwmApplication.getProxiedChaiUser(forgottenPasswordBean.getUserIdentity());
                    if (theUser.isPasswordLocked()) {
                        pwmRequest.forwardToJsp(JspUrl.RECOVER_PASSWORD_ACTION_CHOICE);
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
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean(pwmRequest);
        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();

        try {
            final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userIdentity);
            theUser.unlockPassword();

            // mark the event log
            final UserInfo userInfoBean = ForgottenPasswordUtil.readUserInfo(pwmRequest, forgottenPasswordBean);
            pwmApplication.getAuditManager().submit(AuditEvent.UNLOCK_PASSWORD, userInfoBean, pwmSession);

            ForgottenPasswordUtil.sendUnlockNoticeEmail(pwmRequest, forgottenPasswordBean);

            pwmRequest.getPwmResponse().forwardToSuccessPage(Message.Success_UnlockAccount);
        } catch (ChaiOperationException e) {
            final String errorMsg = "unable to unlock user " + userIdentity + " error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNLOCK_FAILURE,errorMsg);
            LOGGER.error(pwmSession, errorInformation.toDebugStr());
            pwmRequest.respondWithError(errorInformation, true);
        } finally {
            clearForgottenPasswordBean(pwmRequest);
        }
    }


    private void executeResetPassword(final PwmRequest pwmRequest)
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean(pwmRequest);

        if (!forgottenPasswordBean.getProgress().isAllPassed()) {
            return;
        }

        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userIdentity);

        try { // try unlocking user
            theUser.unlockPassword();
            LOGGER.trace(pwmSession, "unlock account succeeded");
        } catch (ChaiOperationException e) {
            final String errorMsg = "unable to unlock user " + theUser.getEntryDN() + " error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNLOCK_FAILURE,errorMsg);
            LOGGER.error(pwmSession, errorInformation.toDebugStr());
        }

        try {
            final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(
                    pwmApplication,
                    pwmSession,
                    PwmAuthenticationSource.FORGOTTEN_PASSWORD
            );
            sessionAuthenticator.authUserWithUnknownPassword(userIdentity,AuthenticationType.AUTH_FROM_PUBLIC_MODULE);
            pwmSession.getLoginInfoBean().getAuthFlags().add(AuthenticationType.AUTH_FROM_PUBLIC_MODULE);

            LOGGER.info(pwmSession, "user successfully supplied password recovery responses, forward to change password page: " + theUser.getEntryDN());

            // mark the event log
            pwmApplication.getAuditManager().submit(AuditEvent.RECOVER_PASSWORD, pwmSession.getUserInfo(),
                    pwmSession);

            // add the post-forgotten password actions
            addPostChangeAction(pwmRequest, userIdentity);

            // mark user as requiring a new password.
            pwmSession.getLoginInfoBean().getLoginFlags().add(LoginInfoBean.LoginFlag.forcePwChange);

            // redirect user to change password screen.
            pwmRequest.sendRedirect(PwmServletDefinition.PublicChangePassword.servletUrlName());
        } catch (PwmUnrecoverableException e) {
            LOGGER.warn(pwmSession,
                    "unexpected error authenticating during forgotten password recovery process user: " + e.getMessage());
            pwmRequest.respondWithError(e.getErrorInformation());
        } finally {
            clearForgottenPasswordBean(pwmRequest);
        }
    }

    private static void processSendNewPassword(final PwmRequest pwmRequest)
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean(pwmRequest);
        final ForgottenPasswordProfile forgottenPasswordProfile = forgottenPasswordProfile(pwmRequest);
        final RecoveryAction recoveryAction = ForgottenPasswordUtil.getRecoveryAction(pwmApplication.getConfig(), forgottenPasswordBean);

        LOGGER.trace(pwmRequest,"beginning process to send new password to user");

        if (!forgottenPasswordBean.getProgress().isAllPassed()) {
            return;
        }

        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
        final ChaiUser theUser = pwmRequest.getPwmApplication().getProxiedChaiUser(userIdentity);

        try { // try unlocking user
            theUser.unlockPassword();
            LOGGER.trace(pwmRequest, "unlock account succeeded");
        } catch (ChaiOperationException e) {
            final String errorMsg = "unable to unlock user " + theUser.getEntryDN() + " error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNLOCK_FAILURE,errorMsg);
            LOGGER.error(pwmRequest.getPwmSession(), errorInformation.toDebugStr());
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        try {
            pwmSession.getLoginInfoBean().setAuthenticated(true);
            pwmSession.getLoginInfoBean().getAuthFlags().add(AuthenticationType.AUTH_FROM_PUBLIC_MODULE);
            pwmSession.getLoginInfoBean().setUserIdentity(userIdentity);

            LOGGER.info(pwmRequest, "user successfully supplied password recovery responses, emailing new password to: " + theUser.getEntryDN());

            // add post change actions
            addPostChangeAction(pwmRequest, userIdentity);

            // create newpassword
            final PasswordData newPassword = RandomPasswordGenerator.createRandomPassword(pwmSession, pwmApplication);

            // set the password
            LOGGER.trace(pwmRequest.getPwmSession(), "setting user password to system generated random value");
            PasswordUtility.setActorPassword(pwmSession, pwmApplication, newPassword);

            if (recoveryAction == RecoveryAction.SENDNEWPW_AND_EXPIRE) {
                LOGGER.debug(pwmSession, "marking user password as expired");
                theUser.expirePassword();
            }

            // mark the event log
            pwmApplication.getAuditManager().submit(AuditEvent.RECOVER_PASSWORD, pwmSession.getUserInfo(), pwmSession);

            final MessageSendMethod messageSendMethod = forgottenPasswordProfile.readSettingAsEnum(PwmSetting.RECOVERY_SENDNEWPW_METHOD,MessageSendMethod.class);

            // send email or SMS
            final String toAddress = PasswordUtility.sendNewPassword(
                    pwmSession.getUserInfo(),
                    pwmApplication,
                    pwmSession.getSessionManager().getMacroMachine(pwmApplication),
                    newPassword,
                    pwmSession.getSessionStateBean().getLocale(),
                    messageSendMethod
            );

            pwmRequest.getPwmResponse().forwardToSuccessPage(Message.Success_PasswordSend, toAddress);
        } catch (PwmException e) {
            LOGGER.warn(pwmSession,"unexpected error setting new password during recovery process for user: " + e.getMessage());
            pwmRequest.respondWithError(e.getErrorInformation());
        } catch (ChaiOperationException e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,"unexpected ldap error while processing recovery action " + recoveryAction + ", error: " + e.getMessage());
            LOGGER.warn(pwmSession,errorInformation.toDebugStr());
            pwmRequest.respondWithError(errorInformation);
        } finally {
            clearForgottenPasswordBean(pwmRequest);
            pwmSession.unauthenticateUser(pwmRequest);
            pwmSession.getSessionStateBean().setPasswordModified(false);
        }
    }


    private static List<FormConfiguration> figureAttributeForm(
            final ForgottenPasswordProfile forgottenPasswordProfile,
            final ForgottenPasswordBean forgottenPasswordBean,
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmOperationalException, PwmUnrecoverableException
    {
        final List<FormConfiguration> requiredAttributesForm = forgottenPasswordProfile.readSettingAsForm(PwmSetting.RECOVERY_ATTRIBUTE_FORM);
        if (requiredAttributesForm.isEmpty()) {
            return requiredAttributesForm;
        }

        final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo(pwmRequest, forgottenPasswordBean);
        final List<FormConfiguration> returnList = new ArrayList<>();
        for (final FormConfiguration formItem : requiredAttributesForm) {
            if (formItem.isRequired()) {
                returnList.add(formItem);
            } else {
                try {
                    final String currentValue = userInfo.readStringAttribute(formItem.getName());
                    if (currentValue != null && currentValue.length() > 0) {
                        returnList.add(formItem);
                    } else {
                        LOGGER.trace(pwmRequest, "excluding optional required attribute(" + formItem.getName() + "), user has no value");
                    }
                } catch (PwmUnrecoverableException e) {
                    throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_NO_CHALLENGES, "unexpected error reading value for attribute " + formItem.getName()));
                }
            }
        }

        if (returnList.isEmpty()) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_NO_CHALLENGES, "user has no values for any optional attribute"));
        }

        return returnList;
    }

    private static void addPostChangeAction(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
    {
        final PostChangePasswordAction postAction = new PostChangeAction(pwmRequest.getPwmApplication(), userIdentity);
        pwmRequest.getPwmSession().getUserSessionDataCacheBean().addPostChangePasswordActions("forgottenPasswordPostActions", postAction);
    }

    private static class PostChangeAction implements PostChangePasswordAction, Serializable {

        private final transient PwmApplication pwmApplication;
        private final transient UserIdentity userIdentity;

        PostChangeAction(final PwmApplication pwmApplication, final UserIdentity userIdentity) {
            this.pwmApplication = pwmApplication;
            this.userIdentity = userIdentity;
        }

        private void readObject(final ObjectInputStream in) throws IOException,ClassNotFoundException {
            throw new IllegalStateException("this class does not support deserialization");
        }

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
                    final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings(pwmApplication, userIdentity)
                            .setMacroMachine(pwmSession.getSessionManager().getMacroMachine(pwmApplication))
                            .setExpandPwmMacros(true)
                            .createActionExecutor();

                    actionExecutor.executeActions(configValues, pwmSession.getLabel());
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
    }


    private static void initForgottenPasswordBean(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {

        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Locale locale = pwmRequest.getLocale();
        final SessionLabel sessionLabel = pwmRequest.getSessionLabel();

        forgottenPasswordBean.setUserIdentity(userIdentity);

        final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo(pwmRequest, forgottenPasswordBean);

        final String forgottenProfileID = ProfileUtility.discoverProfileIDforUser(pwmApplication, sessionLabel, userIdentity, ProfileType.ForgottenPassword);
        if (forgottenProfileID == null || forgottenProfileID.isEmpty()) {
            throw new PwmUnrecoverableException(PwmError.ERROR_NO_PROFILE_ASSIGNED.toInfo());
        }
        forgottenPasswordBean.setForgottenPasswordProfileID(forgottenProfileID);
        final ForgottenPasswordProfile forgottenPasswordProfile = forgottenPasswordProfile(pwmRequest);

        final ForgottenPasswordBean.RecoveryFlags recoveryFlags = calculateRecoveryFlags(
                pwmApplication,
                forgottenProfileID
        );

        final ChallengeSet challengeSet;
        if (recoveryFlags.getRequiredAuthMethods().contains(IdentityVerificationMethod.CHALLENGE_RESPONSES)
                || recoveryFlags.getOptionalAuthMethods().contains(IdentityVerificationMethod.CHALLENGE_RESPONSES)) {
            final ResponseSet responseSet;
            try {
                final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userInfo.getUserIdentity());
                responseSet = pwmApplication.getCrService().readUserResponseSet(
                        sessionLabel,
                        userInfo.getUserIdentity(),
                        theUser
                );
                challengeSet = responseSet == null ? null : responseSet.getPresentableChallengeSet();
            } catch (ChaiValidationException e) {
                final String errorMsg = "unable to determine presentable challengeSet for stored responses: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NO_CHALLENGES, errorMsg);
                throw new PwmUnrecoverableException(errorInformation);
            } catch (ChaiUnavailableException e) {
                throw new PwmUnrecoverableException(PwmError.forChaiError(e.getErrorCode()));
            }
        } else {
            challengeSet = null;
        }


        if (!recoveryFlags.isAllowWhenLdapIntruderLocked()) {
            try {
                final ChaiUser chaiUser = pwmApplication.getProxiedChaiUser(userInfo.getUserIdentity());
                if (chaiUser.isPasswordLocked()) {
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_INTRUDER_LDAP));
                }
            } catch (ChaiOperationException e) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,
                        "error checking user '" + userInfo.getUserIdentity() + "' ldap intruder lock status: " + e.getMessage());
                LOGGER.error(sessionLabel, errorInformation);
                throw new PwmUnrecoverableException(errorInformation);
            } catch (ChaiUnavailableException e) {
                throw new PwmUnrecoverableException(PwmError.forChaiError(e.getErrorCode()));
            }
        }

        final List<FormConfiguration> attributeForm;
        try {
            attributeForm = figureAttributeForm(forgottenPasswordProfile, forgottenPasswordBean, pwmRequest, userIdentity);
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(PwmError.forChaiError(e.getErrorCode()));
        }

        forgottenPasswordBean.setUserLocale(locale);
        forgottenPasswordBean.setPresentableChallengeSet(challengeSet);
        forgottenPasswordBean.setAttributeForm(attributeForm);

        forgottenPasswordBean.setRecoveryFlags(recoveryFlags);
        forgottenPasswordBean.setProgress(new ForgottenPasswordBean.Progress());

        for (final IdentityVerificationMethod recoveryVerificationMethods : recoveryFlags.getRequiredAuthMethods()) {
            ForgottenPasswordUtil.verifyRequirementsForAuthMethod(pwmRequest, forgottenPasswordBean, recoveryVerificationMethods);
        }
    }

    private static ForgottenPasswordBean.RecoveryFlags calculateRecoveryFlags(
            final PwmApplication pwmApplication,
            final String forgottenPasswordProfileID
    ) {
        final Configuration config = pwmApplication.getConfig();
        final ForgottenPasswordProfile forgottenPasswordProfile = config.getForgottenPasswordProfiles().get(forgottenPasswordProfileID);

        final MessageSendMethod tokenSendMethod = config.getForgottenPasswordProfiles().get(forgottenPasswordProfileID).readSettingAsEnum(PwmSetting.RECOVERY_TOKEN_SEND_METHOD, MessageSendMethod.class);

        final Set<IdentityVerificationMethod> requiredRecoveryVerificationMethods = forgottenPasswordProfile.requiredRecoveryAuthenticationMethods();
        final Set<IdentityVerificationMethod> optionalRecoveryVerificationMethods = forgottenPasswordProfile.optionalRecoveryAuthenticationMethods();
        final int minimumOptionalRecoveryAuthMethods = forgottenPasswordProfile.getMinOptionalRequired();
        final boolean allowWhenLdapIntruderLocked = forgottenPasswordProfile.readSettingAsBoolean(PwmSetting.RECOVERY_ALLOW_WHEN_LOCKED);

        return new ForgottenPasswordBean.RecoveryFlags(
                allowWhenLdapIntruderLocked,
                requiredRecoveryVerificationMethods,
                optionalRecoveryVerificationMethods,
                minimumOptionalRecoveryAuthMethods,
                tokenSendMethod
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
        setLastError(pwmRequest, errorInformation);

        final UserIdentity userIdentity = forgottenPasswordBean == null
                ? null
                : forgottenPasswordBean.getUserIdentity();
        if (userIdentity != null) {
            final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getPwmSession(),
                    PwmAuthenticationSource.FORGOTTEN_PASSWORD
            );
            sessionAuthenticator.simulateBadPassword(userIdentity);
            pwmRequest.getPwmApplication().getIntruderManager().convenience().markUserIdentity(userIdentity,
                    pwmRequest.getPwmSession());
            pwmRequest.getPwmApplication().getIntruderManager().convenience().markAddressAndSession(
                    pwmRequest.getPwmSession());
        }
        StatisticsManager.incrementStat(pwmRequest, Statistic.RECOVERY_FAILURES);
    }

    private void checkForLocaleSwitch(final PwmRequest pwmRequest, final ForgottenPasswordBean forgottenPasswordBean)
            throws PwmUnrecoverableException, IOException, ServletException
    {
        if (forgottenPasswordBean.getUserIdentity() == null || forgottenPasswordBean.getUserLocale() == null) {
            return;
        }

        if (forgottenPasswordBean.getUserLocale().equals(pwmRequest.getLocale())) {
            return;
        }

        LOGGER.debug(pwmRequest, "user initiated forgotten password recovery using '" + forgottenPasswordBean.getUserLocale() + "' locale, but current request locale is now '"
                + pwmRequest.getLocale() + "', thus, the user progress will be restart and user data will be re-read using current locale");

        try {
            initForgottenPasswordBean(
                    pwmRequest,
                    forgottenPasswordBean.getUserIdentity(),
                    forgottenPasswordBean
            );
        } catch (PwmOperationalException e) {
            clearForgottenPasswordBean(pwmRequest);
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, "unexpected error while re-loading user data due to locale change: " + e.getErrorInformation().toDebugStr());
            LOGGER.error(pwmRequest, errorInformation.toDebugStr());
            setLastError(pwmRequest, errorInformation);
        }
    }

    private void forwardUserBasedOnRecoveryMethod(
            final PwmRequest pwmRequest,
            final IdentityVerificationMethod method
    )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        LOGGER.debug(pwmRequest,"attempting to forward request to handle verification method " + method.toString());
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean(pwmRequest);
        ForgottenPasswordUtil.verifyRequirementsForAuthMethod(pwmRequest,forgottenPasswordBean,method);
        switch (method) {
            case PREVIOUS_AUTH: {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"previous authentication is required, but user has not previously authenticated"));
            }

            case ATTRIBUTES: {
                pwmRequest.addFormInfoToRequestAttr(forgottenPasswordBean.getAttributeForm(), Collections.emptyMap(), false, false);
                pwmRequest.forwardToJsp(JspUrl.RECOVER_PASSWORD_ATTRIBUTES);
            }
            break;

            case CHALLENGE_RESPONSES: {
                pwmRequest.setAttribute(PwmRequestAttribute.ForgottenPasswordChallengeSet, forgottenPasswordBean.getPresentableChallengeSet());
                pwmRequest.forwardToJsp(JspUrl.RECOVER_PASSWORD_RESPONSES);
            }
            break;

            case OTP: {
                pwmRequest.setAttribute(PwmRequestAttribute.ForgottenPasswordOtpRecord, ForgottenPasswordUtil.readUserInfo(pwmRequest, forgottenPasswordBean).getOtpUserRecord());
                pwmRequest.forwardToJsp(JspUrl.RECOVER_PASSWORD_ENTER_OTP);
            }
            break;

            case TOKEN: {
                final ForgottenPasswordBean.Progress progress = forgottenPasswordBean.getProgress();
                if (progress.getTokenSendChoice() == null) {
                    progress.setTokenSendChoice(ForgottenPasswordUtil.figureTokenSendPreference(pwmRequest, forgottenPasswordBean));
                }

                if (progress.getTokenSendChoice() == MessageSendMethod.CHOICE_SMS_EMAIL) {
                    forwardToTokenChoiceJsp(pwmRequest);
                    return;
                }

                if (!progress.isTokenSent()) {
                    final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo(pwmRequest, forgottenPasswordBean);
                    final String destAddress = ForgottenPasswordUtil.initializeAndSendToken(pwmRequest, userInfo, progress.getTokenSendChoice());
                    progress.setTokenSentAddress(destAddress);
                    progress.setTokenSent(true);
                }

                if (!progress.getSatisfiedMethods().contains(IdentityVerificationMethod.TOKEN)) {
                    final boolean resendEnabled = forgottenPasswordProfile(pwmRequest).readSettingAsBoolean(PwmSetting.TOKEN_RESEND_ENABLE);
                    pwmRequest.setAttribute(PwmRequestAttribute.ForgottenPasswordResendTokenEnabled, resendEnabled);
                    pwmRequest.forwardToJsp(JspUrl.RECOVER_PASSWORD_ENTER_TOKEN);
                    return;
                }
            }
            break;

            case REMOTE_RESPONSES: {
                final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo(pwmRequest, forgottenPasswordBean);
                final VerificationMethodSystem remoteMethod;
                if (forgottenPasswordBean.getProgress().getRemoteRecoveryMethod() == null) {
                    remoteMethod = new RemoteVerificationMethod();
                    remoteMethod.init(
                            pwmRequest.getPwmApplication(),
                            userInfo,
                            pwmRequest.getSessionLabel(),
                            pwmRequest.getLocale()
                    );
                    forgottenPasswordBean.getProgress().setRemoteRecoveryMethod(remoteMethod);
                } else {
                    remoteMethod = forgottenPasswordBean.getProgress().getRemoteRecoveryMethod();
                }

                final List<VerificationMethodSystem.UserPrompt> prompts = remoteMethod.getCurrentPrompts();
                final String displayInstructions = remoteMethod.getCurrentDisplayInstructions();

                pwmRequest.setAttribute(PwmRequestAttribute.ForgottenPasswordPrompts, new ArrayList<>(prompts));
                pwmRequest.setAttribute(PwmRequestAttribute.ForgottenPasswordInstructions, displayInstructions);
                pwmRequest.forwardToJsp(JspUrl.RECOVER_PASSWORD_REMOTE);
            }
            break;

            case OAUTH:
                forgottenPasswordBean.getProgress().setInProgressVerificationMethod(IdentityVerificationMethod.OAUTH);
                final ForgottenPasswordProfile forgottenPasswordProfile = forgottenPasswordProfile(pwmRequest);
                final OAuthSettings oAuthSettings = OAuthSettings.forForgottenPassword(forgottenPasswordProfile);
                final OAuthMachine oAuthMachine = new OAuthMachine(oAuthSettings);
                pwmRequest.getPwmApplication().getSessionStateService().saveSessionBeans(pwmRequest);
                final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
                oAuthMachine.redirectUserToOAuthServer(pwmRequest, null, userIdentity, forgottenPasswordProfile.getIdentifier());
                break;


            default:
                throw new UnsupportedOperationException("unexpected method during forward: " + method.toString());
        }

    }

    private void forwardToTokenChoiceJsp(final PwmRequest pwmRequest)
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo(pwmRequest, forgottenPasswordBean(pwmRequest));
        final ArrayList<TokenDestinationItem> destItems = new ArrayList<>(TokenDestinationItem.allFromConfig(pwmRequest.getConfig(), userInfo));
        pwmRequest.setAttribute(PwmRequestAttribute.ForgottenPasswordTokenDestItems, destItems);
        pwmRequest.forwardToJsp(JspUrl.RECOVER_PASSWORD_TOKEN_CHOICE);
    }
}



