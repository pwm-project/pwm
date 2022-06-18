/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.http.servlet.forgottenpw;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChaiChallenge;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.cr.bean.ChallengeBean;
import com.novell.ldapchai.cr.bean.ChallengeSetBean;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.option.RecoveryAction;
import password.pwm.config.option.RecoveryMinLifetimeOption;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.config.profile.ProfileUtility;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestContext;
import password.pwm.http.PwmSession;
import password.pwm.http.auth.HttpAuthRecord;
import password.pwm.http.bean.ForgottenPasswordBean;
import password.pwm.i18n.Message;
import password.pwm.user.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecord;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.event.AuditServiceClient;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.svc.token.TokenType;
import password.pwm.svc.token.TokenUtil;
import password.pwm.util.PasswordData;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.password.PasswordUtility;
import password.pwm.util.password.RandomPasswordGenerator;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ForgottenPasswordUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ForgottenPasswordUtil.class );

    static Set<IdentityVerificationMethod> figureRemainingAvailableOptionalAuthMethods(
            final PwmRequestContext pwmRequestContext,
            final ForgottenPasswordBean forgottenPasswordBean
    )
    {
        final ForgottenPasswordBean.RecoveryFlags recoveryFlags = forgottenPasswordBean.getRecoveryFlags();
        final ForgottenPasswordBean.Progress progress = forgottenPasswordBean.getProgress();
        final Set<IdentityVerificationMethod> result = new LinkedHashSet<>( recoveryFlags.getOptionalAuthMethods() );
        result.removeAll( progress.getSatisfiedMethods() );

        for ( final IdentityVerificationMethod recoveryVerificationMethods : new LinkedHashSet<>( result ) )
        {
            try
            {
                verifyRequirementsForAuthMethod( pwmRequestContext, forgottenPasswordBean, recoveryVerificationMethods );
            }
            catch ( final PwmUnrecoverableException e )
            {
                result.remove( recoveryVerificationMethods );
            }
        }

        return Collections.unmodifiableSet( result );
    }

    static RecoveryAction getRecoveryAction( final DomainConfig domainConfig, final ForgottenPasswordBean forgottenPasswordBean )
    {
        final ForgottenPasswordProfile forgottenPasswordProfile = domainConfig.getForgottenPasswordProfiles().get( forgottenPasswordBean.getForgottenPasswordProfileID() );
        return forgottenPasswordProfile.readSettingAsEnum( PwmSetting.RECOVERY_ACTION, RecoveryAction.class );
    }


    static Set<IdentityVerificationMethod> figureSatisfiedOptionalAuthMethods(
            final ForgottenPasswordBean.RecoveryFlags recoveryFlags,
            final ForgottenPasswordBean.Progress progress )
    {
        final Set<IdentityVerificationMethod> result = new LinkedHashSet<>( recoveryFlags.getOptionalAuthMethods() );
        result.retainAll( progress.getSatisfiedMethods() );
        return Collections.unmodifiableSet( result );
    }

    public static Optional<UserInfo> readUserInfo(
            final PwmRequestContext pwmRequestContext,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws PwmUnrecoverableException
    {
        if ( forgottenPasswordBean.getUserIdentity() == null )
        {
            return Optional.empty();
        }

        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();

        return Optional.of( UserInfoFactory.newUserInfoUsingProxy(
                pwmRequestContext.getPwmApplication(),
                pwmRequestContext.getSessionLabel(),
                userIdentity,
                pwmRequestContext.getLocale() ) );
    }

    static Optional<ResponseSet> readResponseSet(
            final PwmRequestContext pwmRequestContext,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws PwmUnrecoverableException
    {

        if ( forgottenPasswordBean.getUserIdentity() == null )
        {
            return Optional.empty();
        }

        final PwmDomain pwmDomain = pwmRequestContext.getPwmDomain();
        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();

        final ChaiUser theUser = pwmDomain.getProxiedChaiUser( pwmRequestContext.getSessionLabel(), userIdentity );
        return pwmDomain.getCrService().readUserResponseSet(
                pwmRequestContext.getSessionLabel(),
                userIdentity,
                theUser );
    }

    static void sendUnlockNoticeEmail(
            final PwmRequestContext pwmRequestContext,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmRequestContext.getPwmDomain();
        final DomainConfig config = pwmRequestContext.getDomainConfig();
        final Locale locale = pwmRequestContext.getLocale();
        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail( PwmSetting.EMAIL_UNLOCK, locale );

        if ( configuredEmailSetting == null )
        {
            LOGGER.debug( pwmRequestContext.getSessionLabel(), () -> "skipping send unlock notice email for '" + userIdentity + "' no email configured" );
            return;
        }

        final UserInfo userInfo = readUserInfo( pwmRequestContext, forgottenPasswordBean ).orElseThrow();
        final MacroRequest macroRequest = MacroRequest.forUser(
                pwmRequestContext.getPwmApplication(),
                pwmRequestContext.getSessionLabel(),
                userInfo,
                null
        );

        pwmDomain.getPwmApplication().getEmailQueue().submitEmail(
                configuredEmailSetting,
                userInfo,
                macroRequest
        );
    }

    static boolean checkAuthRecord( final PwmRequest pwmRequest, final String userGuid )
    {
        if ( userGuid == null || userGuid.isEmpty() )
        {
            return false;
        }

        try
        {
            final String cookieName = pwmRequest.getDomainConfig().readAppProperty( AppProperty.HTTP_COOKIE_AUTHRECORD_NAME );
            if ( cookieName == null || cookieName.isEmpty() )
            {
                LOGGER.trace( pwmRequest, () -> "skipping auth record cookie read, cookie name parameter is blank" );
                return false;
            }

            final Optional<HttpAuthRecord> optionalHttpAuthRecord = pwmRequest.readEncryptedCookie( cookieName, HttpAuthRecord.class );
            if ( optionalHttpAuthRecord.isPresent() )
            {
                final HttpAuthRecord httpAuthRecord = optionalHttpAuthRecord.get();
                if ( httpAuthRecord.getGuid() != null && !httpAuthRecord.getGuid().isEmpty() && httpAuthRecord.getGuid().equals( userGuid ) )
                {
                    LOGGER.debug( pwmRequest, () -> "auth record cookie validated" );
                    return true;
                }
            }
        }
        catch ( final Exception e )
        {
            LOGGER.error( pwmRequest, () -> "unexpected error while examining cookie auth record: " + e.getMessage() );
        }
        return false;
    }

    static List<TokenDestinationItem> figureAvailableTokenDestinations(
            final PwmRequestContext pwmRequestContext,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws PwmUnrecoverableException
    {
        final String profileID = forgottenPasswordBean.getForgottenPasswordProfileID();
        final ForgottenPasswordProfile forgottenPasswordProfile = pwmRequestContext.getDomainConfig().getForgottenPasswordProfiles().get( profileID );
        final MessageSendMethod tokenSendMethod = forgottenPasswordProfile.readSettingAsEnum( PwmSetting.RECOVERY_TOKEN_SEND_METHOD, MessageSendMethod.class );
        final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequestContext, forgottenPasswordBean ).orElseThrow();

        return TokenUtil.figureAvailableTokenDestinations(
                pwmRequestContext.getPwmDomain(),
                pwmRequestContext.getSessionLabel(),
                pwmRequestContext.getLocale(),
                userInfo,
                tokenSendMethod
        );
    }

    static void verifyRequirementsForAuthMethod(
            final PwmRequestContext pwmRequestContext,
            final ForgottenPasswordBean forgottenPasswordBean,
            final IdentityVerificationMethod recoveryVerificationMethods
    )
            throws PwmUnrecoverableException
    {
        switch ( recoveryVerificationMethods )
        {
            case TOKEN:
            {
                ForgottenPasswordUtil.figureAvailableTokenDestinations( pwmRequestContext, forgottenPasswordBean );
            }
            break;

            case ATTRIBUTES:
            {
                final List<FormConfiguration> formConfiguration = forgottenPasswordBean.getAttributeForm();
                if ( formConfiguration == null || formConfiguration.isEmpty() )
                {
                    final String errorMsg = "user is required to complete LDAP attribute check, yet there are no LDAP attribute form items configured";
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, errorMsg );
                    throw new PwmUnrecoverableException( errorInformation );
                }
            }
            break;

            case OTP:
            {
                final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequestContext, forgottenPasswordBean ).orElseThrow();
                if ( userInfo.getOtpUserRecord() == null )
                {
                    final String errorMsg = "could not find a one time password configuration for " + userInfo.getUserIdentity();
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NO_OTP_CONFIGURATION, errorMsg );
                    throw new PwmUnrecoverableException( errorInformation );
                }
            }
            break;

            case CHALLENGE_RESPONSES:
            {
                final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequestContext, forgottenPasswordBean ).orElseThrow();
                final Optional<ResponseSet> responseSet = ForgottenPasswordUtil.readResponseSet( pwmRequestContext, forgottenPasswordBean );
                if ( responseSet.isEmpty() )
                {
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_RESPONSES_NORESPONSES );
                    throw new PwmUnrecoverableException( errorInformation );
                }

                final ChallengeSet challengeSet = userInfo.getChallengeProfile().getChallengeSet()
                        .orElseThrow( () -> new PwmUnrecoverableException( PwmError.ERROR_NO_CHALLENGES ) );

                try
                {
                    if ( responseSet.get().meetsChallengeSetRequirements( challengeSet ) )
                    {
                        if ( challengeSet.getRequiredChallenges().isEmpty() && ( challengeSet.getMinRandomRequired() <= 0 ) )
                        {
                            final String errorMsg = "configured challenge set policy for "
                                    + userInfo.getUserIdentity().toString() + " is empty, user not qualified to recover password";
                            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NO_CHALLENGES, errorMsg );
                            throw new PwmUnrecoverableException( errorInformation );
                        }
                    }
                }
                catch ( final ChaiValidationException e )
                {
                    final String errorMsg = "stored response set for user '"
                            + userInfo.getUserIdentity() + "' do not meet current challenge set requirements: " + e.getLocalizedMessage();
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_RESPONSES_NORESPONSES, errorMsg );
                    throw new PwmUnrecoverableException( errorInformation );
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
            final ChallengeSetBean challengeSet
    )
            throws PwmUnrecoverableException
    {
        final Map<Challenge, String> responses = new LinkedHashMap<>();

        int counter = 0;
        for ( final ChallengeBean loopChallenge : challengeSet.getChallenges() )
        {
            counter++;
            final String answer = req.readParameterAsString( PwmConstants.PARAM_RESPONSE_PREFIX + counter );

            responses.put( ChaiChallenge.fromChallengeBean( loopChallenge ), answer.length() > 0 ? answer : "" );
        }

        return responses;
    }

    static Map<Challenge, String> readResponsesFromMap(
            final ChallengeSetBean challengeSet,
            final Map<String, String> formData
    )
    {
        final Map<Challenge, String> responses = new LinkedHashMap<>();

        int counter = 0;
        for ( final ChallengeBean loopChallenge : challengeSet.getChallenges() )
        {
            counter++;
            final String answer = formData.get( PwmConstants.PARAM_RESPONSE_PREFIX + counter );

            responses.put( ChaiChallenge.fromChallengeBean( loopChallenge ), answer.length() > 0 ? answer : "" );
        }

        return responses;
    }


    static void initializeAndSendToken(
            final PwmRequestContext pwmRequestContext,
            final UserInfo userInfo,
            final TokenDestinationItem tokenDestinationItem

    )
            throws PwmUnrecoverableException
    {
        TokenUtil.initializeAndSendToken(
                pwmRequestContext,
                TokenUtil.TokenInitAndSendRequest.builder()
                        .userInfo( userInfo )
                        .tokenDestinationItem( tokenDestinationItem )
                        .emailToSend( PwmSetting.EMAIL_CHALLENGE_TOKEN )
                        .tokenType( TokenType.FORGOTTEN_PW )
                        .smsToSend( PwmSetting.SMS_CHALLENGE_TOKEN_TEXT )
                        .build()
        );

        StatisticsClient.incrementStat( pwmRequestContext.getPwmApplication(), Statistic.RECOVERY_TOKENS_SENT );
    }


    static void doActionSendNewPassword( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final ForgottenPasswordBean forgottenPasswordBean = ForgottenPasswordServlet.forgottenPasswordBean( pwmRequest );
        final ForgottenPasswordProfile forgottenPasswordProfile = forgottenPasswordProfile( pwmRequest.getPwmDomain(), forgottenPasswordBean );
        final RecoveryAction recoveryAction = ForgottenPasswordUtil.getRecoveryAction( pwmDomain.getConfig(), forgottenPasswordBean );

        LOGGER.trace( pwmRequest, () -> "beginning process to send new password to user" );

        if ( !forgottenPasswordBean.getProgress().isAllPassed() )
        {
            return;
        }

        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
        final ChaiUser theUser = pwmRequest.getPwmDomain().getProxiedChaiUser( pwmRequest.getLabel(), userIdentity );

        try
        {
            // try unlocking user
            theUser.unlockPassword();
            LOGGER.trace( pwmRequest, () -> "unlock account succeeded" );
        }
        catch ( final ChaiOperationException e )
        {
            final String errorMsg = "unable to unlock user " + theUser.getEntryDN() + " error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNLOCK_FAILURE, errorMsg );
            LOGGER.error( pwmRequest, errorInformation::toDebugStr );
            pwmRequest.respondWithError( errorInformation );
            return;
        }

        try
        {
            final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxy(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getLabel(),
                    userIdentity,
                    pwmRequest.getLocale()
            );

            LOGGER.info( pwmRequest, () -> "user successfully supplied password recovery responses, emailing new password to: "
                    + theUser.getEntryDN() );

            // create new password
            final PasswordData newPassword = RandomPasswordGenerator.createRandomPassword(
                    pwmRequest.getLabel(),
                    userInfo.getPasswordPolicy(),
                    pwmDomain
            );
            LOGGER.trace( pwmRequest, () -> "generated random password value based on password policy for "
                    + userIdentity.toDisplayString() );


            // set the password
            try
            {
                theUser.setPassword( newPassword.getStringValue() );
                LOGGER.trace( pwmRequest, () -> "set user " + userIdentity.toDisplayString()
                        + " password to system generated random value" );
            }
            catch ( final ChaiException e )
            {
                throw PwmUnrecoverableException.fromChaiException( e );
            }

            if ( recoveryAction == RecoveryAction.SENDNEWPW_AND_EXPIRE )
            {
                LOGGER.debug( pwmRequest, () -> "marking user " + userIdentity.toDisplayString() + " password as expired" );
                theUser.expirePassword();
            }

            // mark the event log
            {
                final AuditRecord auditRecord = AuditRecordFactory.make( pwmRequest ).createUserAuditRecord(
                        AuditEvent.RECOVER_PASSWORD,
                        userIdentity,
                        pwmRequest.getLabel()
                );

                AuditServiceClient.submit( pwmRequest, auditRecord );
            }

            final MessageSendMethod messageSendMethod = forgottenPasswordProfile.readSettingAsEnum( PwmSetting.RECOVERY_SENDNEWPW_METHOD, MessageSendMethod.class );

            // send email or SMS
            final String toAddress = PasswordUtility.sendNewPassword(
                    userInfo,
                    pwmDomain,
                    newPassword,
                    pwmRequest.getLocale(),
                    messageSendMethod
            );

            pwmRequest.getPwmResponse().forwardToSuccessPage( Message.Success_PasswordSend, toAddress );
        }
        catch ( final PwmException e )
        {
            LOGGER.warn( pwmRequest, () -> "unexpected error setting new password during recovery process for user: " + e.getMessage() );
            pwmRequest.respondWithError( e.getErrorInformation() );
        }
        catch ( final ChaiOperationException e )
        {
            final ErrorInformation errorInformation = new ErrorInformation(
                    PwmError.ERROR_INTERNAL,
                    "unexpected ldap error while processing recovery action " + recoveryAction + ", error: " + e.getMessage()
            );
            LOGGER.warn( pwmRequest, errorInformation::toDebugStr );
            pwmRequest.respondWithError( errorInformation );
        }
        finally
        {
            ForgottenPasswordServlet.clearForgottenPasswordBean( pwmRequest );
            final PwmSession pwmSession = pwmRequest.getPwmSession();

            // the user should not be authenticated, this is a safety method
            pwmSession.unAuthenticateUser( pwmRequest );

            // the password set flag should not have been set, this is a safety method
            pwmSession.getSessionStateBean().setPasswordModified( false );
        }
    }

    static void initBogusForgottenPasswordBean( final PwmRequestContext pwmRequestContext, final ForgottenPasswordBean forgottenPasswordBean )
            throws PwmUnrecoverableException
    {
        forgottenPasswordBean.setUserIdentity( null );
        forgottenPasswordBean.setPresentableChallengeSet( null );

        final List<Challenge> challengeList;
        {
            final String firstProfile = pwmRequestContext.getDomainConfig().getChallengeProfileIDs().get( 0 );
            final ChallengeSet challengeSet = pwmRequestContext.getDomainConfig().getChallengeProfile( firstProfile, PwmConstants.DEFAULT_LOCALE ).getChallengeSet()
                    .orElseThrow( () -> new PwmUnrecoverableException( PwmError.ERROR_NO_CHALLENGES.toInfo() ) );
            challengeList = new ArrayList<>( challengeSet.getRequiredChallenges() );
            for ( int i = 0; i < challengeSet.getMinRandomRequired(); i++ )
            {
                challengeList.add( challengeSet.getRandomChallenges().get( i ) );
            }
        }

        final List<FormConfiguration> formData = new ArrayList<>( challengeList.size() );
        {
            int counter = 0;
            for ( final Challenge challenge: challengeList )
            {
                final FormConfiguration formConfiguration = FormConfiguration.builder()
                        .name( "challenge" + counter++ )
                        .type( FormConfiguration.Type.text )
                        .labels( Collections.singletonMap( "", challenge.getChallengeText() ) )
                        .minimumLength( challenge.getMinLength() )
                        .maximumLength( challenge.getMaxLength() )
                        .source( FormConfiguration.Source.bogus )
                        .build();
                formData.add( formConfiguration );
            }
        }
        forgottenPasswordBean.setAttributeForm( formData );
        forgottenPasswordBean.setBogusUser( true );
        {
            final String profileID = pwmRequestContext.getDomainConfig().getForgottenPasswordProfiles().keySet().iterator().next();
            forgottenPasswordBean.setForgottenPasswordProfileID( profileID  );
        }

        final ForgottenPasswordBean.RecoveryFlags recoveryFlags = new ForgottenPasswordBean.RecoveryFlags(
                false,
                Collections.singleton( IdentityVerificationMethod.ATTRIBUTES ),
                Collections.emptySet(),
                0
        );

        forgottenPasswordBean.getProgress().setInProgressVerificationMethod( IdentityVerificationMethod.ATTRIBUTES );
        forgottenPasswordBean.setRecoveryFlags( recoveryFlags );
    }

    public static boolean permitPwChangeDuringMinLifetime(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
    {
        ForgottenPasswordProfile forgottenPasswordProfile = null;
        try
        {
            forgottenPasswordProfile = ForgottenPasswordUtil.forgottenPasswordProfile(
                    pwmDomain,
                    sessionLabel,
                    userIdentity
            );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.debug( sessionLabel, () -> "can't read user's forgotten password profile - assuming no profile assigned, error: " + e.getMessage() );
        }

        if ( forgottenPasswordProfile == null )
        {
            // default is true.
            return true;
        }

        final RecoveryMinLifetimeOption option = forgottenPasswordProfile.readSettingAsEnum(
                PwmSetting.RECOVERY_MINIMUM_PASSWORD_LIFETIME_OPTIONS,
                RecoveryMinLifetimeOption.class
        );
        return option == RecoveryMinLifetimeOption.ALLOW;
    }

    private static ForgottenPasswordProfile forgottenPasswordProfile(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final Optional<String> profileID = ProfileUtility.discoverProfileIDForUser(
                pwmDomain,
                sessionLabel,
                userIdentity,
                ProfileDefinition.ForgottenPassword
        );

        if ( profileID.isPresent() )
        {
            return pwmDomain.getConfig().getForgottenPasswordProfiles().get( profileID.get() );
        }

        final String msg = "user does not have a forgotten password profile assigned";
        throw PwmUnrecoverableException.newException( PwmError.ERROR_NO_PROFILE_ASSIGNED, msg );
    }

    static ForgottenPasswordProfile forgottenPasswordProfile(
            final PwmDomain pwmDomain,
            final ForgottenPasswordBean forgottenPasswordBean
    )
    {
        final String forgottenProfileID = forgottenPasswordBean.getForgottenPasswordProfileID();
        if ( StringUtil.isEmpty( forgottenProfileID ) )
        {
            throw new IllegalStateException( "cannot load forgotten profile without ID registered in bean" );
        }
        return pwmDomain.getConfig().getForgottenPasswordProfiles().get( forgottenProfileID );
    }


    static void initForgottenPasswordBean(
            final PwmRequestContext pwmRequestContext,
            final UserIdentity userIdentity,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {

        final PwmDomain pwmDomain = pwmRequestContext.getPwmDomain();
        final Locale locale = pwmRequestContext.getLocale();
        final SessionLabel sessionLabel = pwmRequestContext.getSessionLabel();

        forgottenPasswordBean.setUserIdentity( userIdentity );

        final UserInfo userInfo = readUserInfo( pwmRequestContext, forgottenPasswordBean ).orElseThrow();

        final ForgottenPasswordProfile forgottenPasswordProfile = forgottenPasswordProfile(
                pwmDomain,
                pwmRequestContext.getSessionLabel(),
                userIdentity
        );
        final String forgottenProfileID = forgottenPasswordProfile.getIdentifier();
        forgottenPasswordBean.setForgottenPasswordProfileID( forgottenProfileID );

        final ForgottenPasswordBean.RecoveryFlags recoveryFlags = calculateRecoveryFlags(
                pwmDomain,
                forgottenProfileID
        );

        final ChallengeSet challengeSet;
        if ( recoveryFlags.getRequiredAuthMethods().contains( IdentityVerificationMethod.CHALLENGE_RESPONSES )
                || recoveryFlags.getOptionalAuthMethods().contains( IdentityVerificationMethod.CHALLENGE_RESPONSES ) )
        {
            final Optional<ResponseSet> responseSet;
            try
            {
                final ChaiUser theUser = pwmDomain.getProxiedChaiUser( pwmRequestContext.getSessionLabel(), userInfo.getUserIdentity() );
                responseSet = pwmDomain.getCrService().readUserResponseSet(
                        sessionLabel,
                        userInfo.getUserIdentity(),
                        theUser
                );
                challengeSet = responseSet.isEmpty() ? null : responseSet.get().getPresentableChallengeSet();
            }
            catch ( final ChaiValidationException e )
            {
                final String errorMsg = "unable to determine presentable challengeSet for stored responses: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NO_CHALLENGES, errorMsg );
                throw new PwmUnrecoverableException( errorInformation );
            }
        }
        else
        {
            challengeSet = null;
        }


        if ( !recoveryFlags.isAllowWhenLdapIntruderLocked() )
        {
            try
            {
                final ChaiUser chaiUser = pwmDomain.getProxiedChaiUser( pwmRequestContext.getSessionLabel(), userInfo.getUserIdentity() );
                if ( chaiUser.isPasswordLocked() )
                {
                    throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTRUDER_LDAP ) );
                }
            }
            catch ( final ChaiOperationException e )
            {
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL,
                        "error checking user '" + userInfo.getUserIdentity() + "' ldap intruder lock status: " + e.getMessage() );
                LOGGER.error( sessionLabel, errorInformation );
                throw new PwmUnrecoverableException( errorInformation );
            }
            catch ( final ChaiUnavailableException e )
            {
                throw new PwmUnrecoverableException( PwmError.forChaiError( e.getErrorCode() ).orElse( PwmError.ERROR_INTERNAL ) );
            }
        }

        final List<FormConfiguration> attributeForm = figureAttributeForm( forgottenPasswordProfile, forgottenPasswordBean, pwmRequestContext );

        forgottenPasswordBean.setUserLocale( locale );
        forgottenPasswordBean.setPresentableChallengeSet( challengeSet == null ? null : challengeSet.asChallengeSetBean() );
        forgottenPasswordBean.setAttributeForm( attributeForm );

        forgottenPasswordBean.setRecoveryFlags( recoveryFlags );
        forgottenPasswordBean.setProgress( new ForgottenPasswordBean.Progress() );

        for ( final IdentityVerificationMethod recoveryVerificationMethods : recoveryFlags.getRequiredAuthMethods() )
        {
            verifyRequirementsForAuthMethod( pwmRequestContext, forgottenPasswordBean, recoveryVerificationMethods );
        }
    }

    static List<FormConfiguration> figureAttributeForm(
            final ForgottenPasswordProfile forgottenPasswordProfile,
            final ForgottenPasswordBean forgottenPasswordBean,
            final PwmRequestContext pwmRequestContext
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final List<FormConfiguration> requiredAttributesForm = forgottenPasswordProfile.readSettingAsForm( PwmSetting.RECOVERY_ATTRIBUTE_FORM );
        if ( requiredAttributesForm.isEmpty() )
        {
            return requiredAttributesForm;
        }

        final UserInfo userInfo = readUserInfo( pwmRequestContext, forgottenPasswordBean ).orElseThrow();
        final List<FormConfiguration> returnList = new ArrayList<>();
        for ( final FormConfiguration formItem : requiredAttributesForm )
        {
            if ( formItem.isRequired() )
            {
                returnList.add( formItem );
            }
            else
            {
                try
                {
                    final String currentValue = userInfo.readStringAttribute( formItem.getName() );
                    if ( currentValue != null && currentValue.length() > 0 )
                    {
                        returnList.add( formItem );
                    }
                    else
                    {
                        LOGGER.trace( pwmRequestContext.getSessionLabel(), () -> "excluding optional required attribute(" + formItem.getName() + "), user has no value" );
                    }
                }
                catch ( final PwmUnrecoverableException e )
                {
                    throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_NO_CHALLENGES, "unexpected error reading value for attribute " + formItem.getName() ) );
                }
            }
        }

        if ( returnList.isEmpty() )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_NO_CHALLENGES, "user has no values for any optional attribute" ) );
        }

        return returnList;
    }

    static ForgottenPasswordBean.RecoveryFlags calculateRecoveryFlags(
            final PwmDomain pwmDomain,
            final String forgottenPasswordProfileID
    )
    {
        final DomainConfig config = pwmDomain.getConfig();
        final ForgottenPasswordProfile forgottenPasswordProfile = config.getForgottenPasswordProfiles().get( forgottenPasswordProfileID );

        final Set<IdentityVerificationMethod> requiredRecoveryVerificationMethods = forgottenPasswordProfile.requiredRecoveryAuthenticationMethods();
        final Set<IdentityVerificationMethod> optionalRecoveryVerificationMethods = forgottenPasswordProfile.optionalRecoveryAuthenticationMethods();
        final int minimumOptionalRecoveryAuthMethods = forgottenPasswordProfile.getMinOptionalRequired();
        final boolean allowWhenLdapIntruderLocked = forgottenPasswordProfile.readSettingAsBoolean( PwmSetting.RECOVERY_ALLOW_WHEN_LOCKED );

        return new ForgottenPasswordBean.RecoveryFlags(
                allowWhenLdapIntruderLocked,
                requiredRecoveryVerificationMethods,
                optionalRecoveryVerificationMethods,
                minimumOptionalRecoveryAuthMethods
        );
    }

    static boolean hasOtherMethodChoices(
            final PwmRequestContext pwmRequestContext,
            final ForgottenPasswordBean forgottenPasswordBean,
            final IdentityVerificationMethod thisMethod
    )
    {
        if ( forgottenPasswordBean.getRecoveryFlags().getRequiredAuthMethods().contains( thisMethod ) )
        {
            return false;
        }

        {
            // check if previously satisfied any other optional methods.
            final Set<IdentityVerificationMethod> optionalAuthMethods = forgottenPasswordBean.getRecoveryFlags().getOptionalAuthMethods();
            final Set<IdentityVerificationMethod> satisfiedMethods = forgottenPasswordBean.getProgress().getSatisfiedMethods();
            final boolean disJoint = Collections.disjoint( optionalAuthMethods, satisfiedMethods );
            if ( !disJoint )
            {
                return true;
            }
        }

        {
            final Set<IdentityVerificationMethod> remainingAvailableOptionalMethods = ForgottenPasswordUtil.figureRemainingAvailableOptionalAuthMethods(
                    pwmRequestContext,
                    forgottenPasswordBean
            );
            final Set<IdentityVerificationMethod> otherOptionalMethodChoices = CollectionUtil.copyToEnumSet( remainingAvailableOptionalMethods, IdentityVerificationMethod.class );
            otherOptionalMethodChoices.remove( thisMethod );

            return !otherOptionalMethodChoices.isEmpty();
        }
    }
}
