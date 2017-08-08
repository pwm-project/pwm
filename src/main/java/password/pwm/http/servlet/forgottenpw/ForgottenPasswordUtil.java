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
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.option.RecoveryAction;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.ForgottenPasswordBean;
import password.pwm.http.filter.AuthenticationFilter;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.svc.token.TokenPayload;
import password.pwm.svc.token.TokenService;
import password.pwm.svc.token.TokenType;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.ws.client.rest.RestTokenDataClient;

import javax.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

class ForgottenPasswordUtil {
    private static final PwmLogger LOGGER = PwmLogger.forClass(ForgottenPasswordUtil.class);

    static Set<IdentityVerificationMethod> figureRemainingAvailableOptionalAuthMethods(
            final PwmRequest pwmRequest,
            final ForgottenPasswordBean forgottenPasswordBean
    )
    {
        final ForgottenPasswordBean.RecoveryFlags recoveryFlags = forgottenPasswordBean.getRecoveryFlags();
        final ForgottenPasswordBean.Progress progress = forgottenPasswordBean.getProgress();
        final Set<IdentityVerificationMethod> result = new LinkedHashSet<>();
        result.addAll(recoveryFlags.getOptionalAuthMethods());
        result.removeAll(progress.getSatisfiedMethods());

        for (final IdentityVerificationMethod recoveryVerificationMethods : new LinkedHashSet<>(result)) {
            try {
                verifyRequirementsForAuthMethod(pwmRequest, forgottenPasswordBean, recoveryVerificationMethods);
            } catch (PwmUnrecoverableException e) {
                result.remove(recoveryVerificationMethods);
            }
        }

        return Collections.unmodifiableSet(result);
    }

    public static RecoveryAction getRecoveryAction(final Configuration configuration, final ForgottenPasswordBean forgottenPasswordBean) {
        final ForgottenPasswordProfile forgottenPasswordProfile = configuration.getForgottenPasswordProfiles().get(forgottenPasswordBean.getForgottenPasswordProfileID());
        return forgottenPasswordProfile.readSettingAsEnum(PwmSetting.RECOVERY_ACTION, RecoveryAction.class);
    }


    static Set<IdentityVerificationMethod> figureSatisfiedOptionalAuthMethods(
            final ForgottenPasswordBean.RecoveryFlags recoveryFlags,
            final ForgottenPasswordBean.Progress progress)
    {
        final Set<IdentityVerificationMethod> result = new LinkedHashSet<>();
        result.addAll(recoveryFlags.getOptionalAuthMethods());
        result.retainAll(progress.getSatisfiedMethods());
        return Collections.unmodifiableSet(result);
    }

    static UserInfo readUserInfo(final PwmRequest pwmRequest, final ForgottenPasswordBean forgottenPasswordBean) throws PwmUnrecoverableException {
        if (forgottenPasswordBean.getUserIdentity() == null) {
            return null;
        }

        final String CACHE_KEY = PwmConstants.SESSION_ATTR_FORGOTTEN_PW_USERINFO_CACHE;

        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();

        {
            final UserInfo userInfoFromSession = (UserInfo)pwmRequest.getHttpServletRequest().getSession().getAttribute(CACHE_KEY);
            if (userInfoFromSession != null) {
                if (userIdentity.equals(userInfoFromSession.getUserIdentity())) {
                    LOGGER.trace(pwmRequest, "using request cached userInfo");
                    return userInfoFromSession;
                } else {
                    LOGGER.trace(pwmRequest, "request cached userInfo is not for current user, clearing.");
                    pwmRequest.getHttpServletRequest().getSession().setAttribute(CACHE_KEY, null);
                }
            }
        }

        final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxy(
                pwmRequest.getPwmApplication(),
                pwmRequest.getSessionLabel(),
                userIdentity, pwmRequest.getLocale()
        );

        pwmRequest.getHttpServletRequest().getSession().setAttribute(CACHE_KEY, userInfo);

        return userInfo;
    }

    static ResponseSet readResponseSet(final PwmRequest pwmRequest, final ForgottenPasswordBean forgottenPasswordBean)
            throws PwmUnrecoverableException
    {

        if (forgottenPasswordBean.getUserIdentity() == null) {
            return null;
        }

        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
        final ResponseSet responseSet;

        try {
            final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userIdentity);
            responseSet = pwmApplication.getCrService().readUserResponseSet(
                    pwmRequest.getSessionLabel(),
                    userIdentity,
                    theUser
            );
        } catch (ChaiUnavailableException e) {
            throw PwmUnrecoverableException.fromChaiException(e);
        }

        return responseSet;
    }

    static void sendUnlockNoticeEmail(
            final PwmRequest pwmRequest,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmRequest.getConfig();
        final Locale locale = pwmRequest.getLocale();
        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_UNLOCK, locale);

        if (configuredEmailSetting == null) {
            LOGGER.debug(pwmRequest, "skipping send unlock notice email for '" + userIdentity + "' no email configured");
            return;
        }

        final UserInfo userInfo = readUserInfo(pwmRequest, forgottenPasswordBean);
        final MacroMachine macroMachine = new MacroMachine(
                pwmApplication,
                pwmRequest.getSessionLabel(),
                userInfo,
                null
        );

        pwmApplication.getEmailQueue().submitEmail(
                configuredEmailSetting,
                userInfo,
                macroMachine
        );
    }

    static boolean checkAuthRecord(final PwmRequest pwmRequest, final String userGuid)
            throws PwmUnrecoverableException
    {
        if (userGuid == null || userGuid.isEmpty()) {
            return false;
        }

        try {
            final String cookieName = pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_COOKIE_AUTHRECORD_NAME);
            if (cookieName == null || cookieName.isEmpty()) {
                LOGGER.trace(pwmRequest, "skipping auth record cookie read, cookie name parameter is blank");
                return false;
            }

            final AuthenticationFilter.AuthRecord authRecord = pwmRequest.readEncryptedCookie(cookieName, AuthenticationFilter.AuthRecord.class);
            if (authRecord != null) {
                if (authRecord.getGuid() != null && !authRecord.getGuid().isEmpty() && authRecord.getGuid().equals(userGuid)) {
                    LOGGER.debug(pwmRequest, "auth record cookie validated");
                    return true;
                }
            }
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "unexpected error while examining cookie auth record: " + e.getMessage());
        }
        return false;
    }

    static MessageSendMethod figureTokenSendPreference(
            final PwmRequest pwmRequest,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws PwmUnrecoverableException
    {
        final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo(pwmRequest, forgottenPasswordBean);
        final MessageSendMethod tokenSendMethod = forgottenPasswordBean.getRecoveryFlags().getTokenSendMethod();
        if (tokenSendMethod == null || tokenSendMethod.equals(MessageSendMethod.NONE)) {
            return MessageSendMethod.NONE;
        }

        if (!tokenSendMethod.equals(MessageSendMethod.CHOICE_SMS_EMAIL)) {
            return tokenSendMethod;
        }

        final String emailAddress = userInfo.getUserEmailAddress();
        final String smsAddress = userInfo.getUserSmsNumber();

        final boolean hasEmail = emailAddress != null && emailAddress.length() > 1;
        final boolean hasSms = smsAddress != null && smsAddress.length() > 1;

        if (hasEmail && hasSms) {
            return MessageSendMethod.CHOICE_SMS_EMAIL;
        } else if (hasEmail) {
            LOGGER.debug(pwmRequest, "though token send method is " + MessageSendMethod.CHOICE_SMS_EMAIL + ", no sms address is available for user so defaulting to email method");
            return MessageSendMethod.EMAILONLY;
        } else if (hasSms) {
            LOGGER.debug(pwmRequest, "though token send method is " + MessageSendMethod.CHOICE_SMS_EMAIL + ", no email address is available for user so defaulting to sms method");
            return MessageSendMethod.SMSONLY;
        }

        throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_TOKEN_MISSING_CONTACT));
    }

    static void verifyRequirementsForAuthMethod(
            final PwmRequest pwmRequest,
            final ForgottenPasswordBean forgottenPasswordBean,
            final IdentityVerificationMethod recoveryVerificationMethods
    )
            throws PwmUnrecoverableException
    {
        switch (recoveryVerificationMethods) {
            case TOKEN: {
                final MessageSendMethod tokenSendMethod = forgottenPasswordBean.getRecoveryFlags().getTokenSendMethod();
                if (tokenSendMethod == null || tokenSendMethod == MessageSendMethod.NONE) {
                    final String errorMsg = "user is required to complete token validation, yet there is not a token send method configured";
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG, errorMsg);
                    throw new PwmUnrecoverableException(errorInformation);
                }
            }
            break;

            case ATTRIBUTES: {
                final List<FormConfiguration> formConfiguration = forgottenPasswordBean.getAttributeForm();
                if (formConfiguration == null || formConfiguration.isEmpty()) {
                    final String errorMsg = "user is required to complete LDAP attribute check, yet there are no LDAP attribute form items configured";
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG, errorMsg);
                    throw new PwmUnrecoverableException(errorInformation);
                }
            }
            break;

            case OTP: {
                final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo(pwmRequest, forgottenPasswordBean);
                if (userInfo.getOtpUserRecord() == null) {
                    final String errorMsg = "could not find a one time password configuration for " + userInfo.getUserIdentity();
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NO_OTP_CONFIGURATION, errorMsg);
                    throw new PwmUnrecoverableException(errorInformation);
                }
            }
            break;

            case CHALLENGE_RESPONSES: {
                final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo(pwmRequest, forgottenPasswordBean);
                final ResponseSet responseSet = ForgottenPasswordUtil.readResponseSet(pwmRequest, forgottenPasswordBean);
                if (responseSet == null) {
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES);
                    throw new PwmUnrecoverableException(errorInformation);
                }

                final ChallengeSet challengeSet = userInfo.getChallengeProfile().getChallengeSet();

                try {
                    if (responseSet.meetsChallengeSetRequirements(challengeSet)) {
                        if (challengeSet.getRequiredChallenges().isEmpty() && (challengeSet.getMinRandomRequired() <= 0)) {
                            final String errorMsg = "configured challenge set policy for " + userInfo.getUserIdentity().toString() + " is empty, user not qualified to recover password";
                            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NO_CHALLENGES, errorMsg);
                            throw new PwmUnrecoverableException(errorInformation);
                        }
                    }
                } catch (ChaiValidationException e) {
                    final String errorMsg = "stored response set for user '" + userInfo.getUserIdentity() + "' do not meet current challenge set requirements: " + e.getLocalizedMessage();
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES, errorMsg);
                    throw new PwmUnrecoverableException(errorInformation);
                }
            }
            break;

            default:
                // continue, assume no data requirements for method.
                break;
        }
    }

    static Map<Challenge, String> readResponsesFromHttpRequest(
            final PwmRequest req,
            final ChallengeSet challengeSet
    )
            throws ChaiValidationException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final Map<Challenge, String> responses = new LinkedHashMap<>();

        int counter = 0;
        for (final Challenge loopChallenge : challengeSet.getChallenges()) {
            counter++;
            final String answer = req.readParameterAsString(PwmConstants.PARAM_RESPONSE_PREFIX + counter);

            responses.put(loopChallenge, answer.length() > 0 ? answer : "");
        }

        return responses;
    }

    static String initializeAndSendToken(
            final PwmRequest pwmRequest,
            final UserInfo userInfo,
            final MessageSendMethod tokenSendMethod

    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        final UserIdentity userIdentity = userInfo.getUserIdentity();
        final Map<String,String> tokenMapData = new LinkedHashMap<>();


        try {
            final Instant userLastPasswordChange = PasswordUtility.determinePwdLastModified(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getSessionLabel(),
                    userIdentity
            );
            if (userLastPasswordChange != null) {
                final String userChangeString = JavaHelper.toIsoDate(userLastPasswordChange);
                tokenMapData.put(PwmConstants.TOKEN_KEY_PWD_CHG_DATE, userChangeString);
            }
        } catch (ChaiUnavailableException e) {
            LOGGER.error(pwmRequest, "unexpected error reading user's last password change time");
        }

        final EmailItemBean emailItemBean = config.readSettingAsEmail(PwmSetting.EMAIL_CHALLENGE_TOKEN, pwmRequest.getLocale());
        final MacroMachine macroMachine = MacroMachine.forUser(pwmRequest, userIdentity);

        final RestTokenDataClient.TokenDestinationData inputDestinationData = new RestTokenDataClient.TokenDestinationData(
                macroMachine.expandMacros(emailItemBean.getTo()),
                userInfo.getUserSmsNumber(),
                null
        );

        final RestTokenDataClient restTokenDataClient = new RestTokenDataClient(pwmRequest.getPwmApplication());
        final RestTokenDataClient.TokenDestinationData outputDestrestTokenDataClient = restTokenDataClient.figureDestTokenDisplayString(
                pwmRequest.getSessionLabel(),
                inputDestinationData,
                userIdentity,
                pwmRequest.getLocale());

        final Set<String> destinationValues = new LinkedHashSet<>();
        if (outputDestrestTokenDataClient.getEmail() != null) {
            destinationValues.add(outputDestrestTokenDataClient.getEmail());
        }
        if (outputDestrestTokenDataClient.getSms() != null) {
            destinationValues.add(outputDestrestTokenDataClient.getSms());
        }

        final String tokenKey;
        final TokenPayload tokenPayload;
        try {
            tokenPayload = pwmRequest.getPwmApplication().getTokenService().createTokenPayload(
                    TokenType.FORGOTTEN_PW,
                    new TimeDuration(config.readSettingAsLong(PwmSetting.TOKEN_LIFETIME), TimeUnit.SECONDS),
                    tokenMapData,
                    userIdentity,
                    destinationValues
            );
            tokenKey = pwmRequest.getPwmApplication().getTokenService().generateNewToken(tokenPayload, pwmRequest.getSessionLabel());
        } catch (PwmOperationalException e) {
            throw new PwmUnrecoverableException(e.getErrorInformation());
        }

        final String smsMessage = config.readSettingAsLocalizedString(PwmSetting.SMS_CHALLENGE_TOKEN_TEXT, pwmRequest.getLocale());

        final List<TokenDestinationItem.Type> sentTypes = TokenService.TokenSender.sendToken(
                pwmRequest.getPwmApplication(),
                userInfo,
                macroMachine,
                emailItemBean,
                tokenSendMethod,
                outputDestrestTokenDataClient.getEmail(),
                outputDestrestTokenDataClient.getSms(),
                smsMessage,
                tokenKey
        );

        StatisticsManager.incrementStat(pwmRequest, Statistic.RECOVERY_TOKENS_SENT);

        final String displayDestAddress = TokenService.TokenSender.figureDisplayString(
                pwmRequest.getConfig(),
                sentTypes,
                outputDestrestTokenDataClient.getEmail(),
                outputDestrestTokenDataClient.getSms()
        );
        return displayDestAddress;
    }

}
