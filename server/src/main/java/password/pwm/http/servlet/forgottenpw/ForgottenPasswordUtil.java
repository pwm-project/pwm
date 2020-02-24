/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
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
import password.pwm.http.CommonValues;
import password.pwm.http.PwmRequest;
import password.pwm.http.auth.HttpAuthRecord;
import password.pwm.http.bean.ForgottenPasswordBean;
import password.pwm.i18n.Message;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecord;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.token.TokenType;
import password.pwm.svc.token.TokenUtil;
import password.pwm.util.PasswordData;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.password.PasswordUtility;
import password.pwm.util.password.RandomPasswordGenerator;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ForgottenPasswordUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ForgottenPasswordUtil.class );

    static Set<IdentityVerificationMethod> figureRemainingAvailableOptionalAuthMethods(
            final CommonValues commonValues,
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
                verifyRequirementsForAuthMethod( commonValues, forgottenPasswordBean, recoveryVerificationMethods );
            }
            catch ( final PwmUnrecoverableException e )
            {
                result.remove( recoveryVerificationMethods );
            }
        }

        return Collections.unmodifiableSet( result );
    }

    static RecoveryAction getRecoveryAction( final Configuration configuration, final ForgottenPasswordBean forgottenPasswordBean )
    {
        final ForgottenPasswordProfile forgottenPasswordProfile = configuration.getForgottenPasswordProfiles().get( forgottenPasswordBean.getForgottenPasswordProfileID() );
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

    static UserInfo readUserInfo(
            final CommonValues commonValues,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws PwmUnrecoverableException
    {
        if ( forgottenPasswordBean.getUserIdentity() == null )
        {
            return null;
        }

        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();

        return UserInfoFactory.newUserInfoUsingProxy(
                commonValues.getPwmApplication(),
                commonValues.getSessionLabel(),
                userIdentity,
                commonValues.getLocale()
        );
    }

    static ResponseSet readResponseSet(
            final CommonValues commonValues,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws PwmUnrecoverableException
    {

        if ( forgottenPasswordBean.getUserIdentity() == null )
        {
            return null;
        }

        final PwmApplication pwmApplication = commonValues.getPwmApplication();
        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
        final ResponseSet responseSet;

        try
        {
            final ChaiUser theUser = pwmApplication.getProxiedChaiUser( userIdentity );
            responseSet = pwmApplication.getCrService().readUserResponseSet(
                    commonValues.getSessionLabel(),
                    userIdentity,
                    theUser
            );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }

        return responseSet;
    }

    static void sendUnlockNoticeEmail(
            final CommonValues commonValues,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = commonValues.getPwmApplication();
        final Configuration config = commonValues.getConfig();
        final Locale locale = commonValues.getLocale();
        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail( PwmSetting.EMAIL_UNLOCK, locale );

        if ( configuredEmailSetting == null )
        {
            LOGGER.debug( commonValues.getSessionLabel(), () -> "skipping send unlock notice email for '" + userIdentity + "' no email configured" );
            return;
        }

        final UserInfo userInfo = readUserInfo( commonValues, forgottenPasswordBean );
        final MacroMachine macroMachine = MacroMachine.forUser(
                pwmApplication,
                commonValues.getSessionLabel(),
                userInfo,
                null
        );

        pwmApplication.getEmailQueue().submitEmail(
                configuredEmailSetting,
                userInfo,
                macroMachine
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
            final String cookieName = pwmRequest.getConfig().readAppProperty( AppProperty.HTTP_COOKIE_AUTHRECORD_NAME );
            if ( cookieName == null || cookieName.isEmpty() )
            {
                LOGGER.trace( pwmRequest, () -> "skipping auth record cookie read, cookie name parameter is blank" );
                return false;
            }

            final HttpAuthRecord httpAuthRecord = pwmRequest.readEncryptedCookie( cookieName, HttpAuthRecord.class );
            if ( httpAuthRecord != null )
            {
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
            final CommonValues commonValues,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws PwmUnrecoverableException
    {
        final String profileID = forgottenPasswordBean.getForgottenPasswordProfileID();
        final ForgottenPasswordProfile forgottenPasswordProfile = commonValues.getConfig().getForgottenPasswordProfiles().get( profileID );
        final MessageSendMethod tokenSendMethod = forgottenPasswordProfile.readSettingAsEnum( PwmSetting.RECOVERY_TOKEN_SEND_METHOD, MessageSendMethod.class );
        final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( commonValues, forgottenPasswordBean );

        final List<TokenDestinationItem> items = TokenUtil.figureAvailableTokenDestinations(
                commonValues.getPwmApplication(),
                commonValues.getSessionLabel(),
                commonValues.getLocale(),
                userInfo,
                tokenSendMethod
        );

        return Collections.unmodifiableList( items );
    }

    static void verifyRequirementsForAuthMethod(
            final CommonValues commonValues,
            final ForgottenPasswordBean forgottenPasswordBean,
            final IdentityVerificationMethod recoveryVerificationMethods
    )
            throws PwmUnrecoverableException
    {
        switch ( recoveryVerificationMethods )
        {
            case TOKEN:
            {
                ForgottenPasswordUtil.figureAvailableTokenDestinations( commonValues, forgottenPasswordBean );
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
                final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( commonValues, forgottenPasswordBean );
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
                final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( commonValues, forgottenPasswordBean );
                final ResponseSet responseSet = ForgottenPasswordUtil.readResponseSet( commonValues, forgottenPasswordBean );
                if ( responseSet == null )
                {
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_RESPONSES_NORESPONSES );
                    throw new PwmUnrecoverableException( errorInformation );
                }

                final ChallengeSet challengeSet = userInfo.getChallengeProfile().getChallengeSet();

                try
                {
                    if ( responseSet.meetsChallengeSetRequirements( challengeSet ) )
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
            final CommonValues commonValues,
            final UserInfo userInfo,
            final TokenDestinationItem tokenDestinationItem

    )
            throws PwmUnrecoverableException
    {
        TokenUtil.initializeAndSendToken(
                commonValues,
                TokenUtil.TokenInitAndSendRequest.builder()
                        .userInfo( userInfo )
                        .tokenDestinationItem( tokenDestinationItem )
                        .emailToSend( PwmSetting.EMAIL_CHALLENGE_TOKEN )
                        .tokenType( TokenType.FORGOTTEN_PW )
                        .smsToSend( PwmSetting.SMS_CHALLENGE_TOKEN_TEXT )
                        .build()
        );

        commonValues.getPwmApplication().getStatisticsManager().incrementValue( Statistic.RECOVERY_TOKENS_SENT );
    }


    static void doActionSendNewPassword( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final ForgottenPasswordBean forgottenPasswordBean = ForgottenPasswordServlet.forgottenPasswordBean( pwmRequest );
        final ForgottenPasswordProfile forgottenPasswordProfile = forgottenPasswordProfile( pwmRequest.getPwmApplication(), forgottenPasswordBean );
        final RecoveryAction recoveryAction = ForgottenPasswordUtil.getRecoveryAction( pwmApplication.getConfig(), forgottenPasswordBean );

        LOGGER.trace( pwmRequest, () -> "beginning process to send new password to user" );

        if ( !forgottenPasswordBean.getProgress().isAllPassed() )
        {
            return;
        }

        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
        final ChaiUser theUser = pwmRequest.getPwmApplication().getProxiedChaiUser( userIdentity );

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
            LOGGER.error( pwmRequest, () -> errorInformation.toDebugStr() );
            pwmRequest.respondWithError( errorInformation );
            return;
        }

        try
        {
            final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxy(
                    pwmApplication,
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
                    pwmApplication
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
                final AuditRecord auditRecord = new AuditRecordFactory( pwmApplication ).createUserAuditRecord(
                        AuditEvent.RECOVER_PASSWORD,
                        userIdentity,
                        pwmRequest.getLabel()
                );
                pwmApplication.getAuditManager().submit( auditRecord );
            }

            final MessageSendMethod messageSendMethod = forgottenPasswordProfile.readSettingAsEnum( PwmSetting.RECOVERY_SENDNEWPW_METHOD, MessageSendMethod.class );

            // send email or SMS
            final String toAddress = PasswordUtility.sendNewPassword(
                    userInfo,
                    pwmApplication,
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
            LOGGER.warn( pwmRequest, () -> errorInformation.toDebugStr() );
            pwmRequest.respondWithError( errorInformation );
        }
        finally
        {
            ForgottenPasswordServlet.clearForgottenPasswordBean( pwmRequest );

            // the user should not be authenticated, this is a safety method
            pwmRequest.getPwmSession().unauthenticateUser( pwmRequest );

            // the password set flag should not have been set, this is a safety method
            pwmRequest.getPwmSession().getSessionStateBean().setPasswordModified( false );
        }
    }

    static void initBogusForgottenPasswordBean( final CommonValues commonValues, final ForgottenPasswordBean forgottenPasswordBean )
            throws PwmUnrecoverableException
    {
        forgottenPasswordBean.setUserIdentity( null );
        forgottenPasswordBean.setPresentableChallengeSet( null );

        final List<Challenge> challengeList = new ArrayList<>( );
        {
            final String firstProfile = commonValues.getConfig().getChallengeProfileIDs().iterator().next();
            final ChallengeSet challengeSet = commonValues.getConfig().getChallengeProfile( firstProfile, PwmConstants.DEFAULT_LOCALE ).getChallengeSet();
            challengeList.addAll( challengeSet.getRequiredChallenges() );
            for ( int i = 0; i < challengeSet.getMinRandomRequired(); i++ )
            {
                challengeList.add( challengeSet.getRandomChallenges().get( i ) );
            }
        }

        final List<FormConfiguration> formData = new ArrayList<>(  );
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
            final String profileID = commonValues.getConfig().getForgottenPasswordProfiles().keySet().iterator().next();
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
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        ForgottenPasswordProfile forgottenPasswordProfile = null;
        try
        {
            forgottenPasswordProfile = ForgottenPasswordUtil.forgottenPasswordProfile(
                    pwmApplication,
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
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final String forgottenProfileID = ProfileUtility.discoverProfileIDforUser(
                pwmApplication,
                sessionLabel,
                userIdentity,
                ProfileDefinition.ForgottenPassword
        );

        if ( StringUtil.isEmpty( forgottenProfileID ) )
        {
            final String msg = "user does not have a forgotten password profile assigned";
            throw PwmUnrecoverableException.newException( PwmError.ERROR_NO_PROFILE_ASSIGNED, msg );
        }

        return pwmApplication.getConfig().getForgottenPasswordProfiles().get( forgottenProfileID );
    }

    static ForgottenPasswordProfile forgottenPasswordProfile(
            final PwmApplication pwmApplication,
            final ForgottenPasswordBean forgottenPasswordBean
    )
    {
        final String forgottenProfileID = forgottenPasswordBean.getForgottenPasswordProfileID();
        if ( StringUtil.isEmpty( forgottenProfileID ) )
        {
            throw new IllegalStateException( "cannot load forgotten profile without ID registered in bean" );
        }
        return pwmApplication.getConfig().getForgottenPasswordProfiles().get( forgottenProfileID );
    }


    static void initForgottenPasswordBean(
            final CommonValues commonValues,
            final UserIdentity userIdentity,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {

        final PwmApplication pwmApplication = commonValues.getPwmApplication();
        final Locale locale = commonValues.getLocale();
        final SessionLabel sessionLabel = commonValues.getSessionLabel();

        forgottenPasswordBean.setUserIdentity( userIdentity );

        final UserInfo userInfo = readUserInfo( commonValues, forgottenPasswordBean );

        final ForgottenPasswordProfile forgottenPasswordProfile = forgottenPasswordProfile(
                pwmApplication,
                commonValues.getSessionLabel(),
                userIdentity
        );
        final String forgottenProfileID = forgottenPasswordProfile.getIdentifier();
        forgottenPasswordBean.setForgottenPasswordProfileID( forgottenProfileID );

        final ForgottenPasswordBean.RecoveryFlags recoveryFlags = calculateRecoveryFlags(
                pwmApplication,
                forgottenProfileID
        );

        final ChallengeSet challengeSet;
        if ( recoveryFlags.getRequiredAuthMethods().contains( IdentityVerificationMethod.CHALLENGE_RESPONSES )
                || recoveryFlags.getOptionalAuthMethods().contains( IdentityVerificationMethod.CHALLENGE_RESPONSES ) )
        {
            final ResponseSet responseSet;
            try
            {
                final ChaiUser theUser = pwmApplication.getProxiedChaiUser( userInfo.getUserIdentity() );
                responseSet = pwmApplication.getCrService().readUserResponseSet(
                        sessionLabel,
                        userInfo.getUserIdentity(),
                        theUser
                );
                challengeSet = responseSet == null ? null : responseSet.getPresentableChallengeSet();
            }
            catch ( final ChaiValidationException e )
            {
                final String errorMsg = "unable to determine presentable challengeSet for stored responses: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NO_CHALLENGES, errorMsg );
                throw new PwmUnrecoverableException( errorInformation );
            }
            catch ( final ChaiUnavailableException e )
            {
                throw new PwmUnrecoverableException( PwmError.forChaiError( e.getErrorCode() ) );
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
                final ChaiUser chaiUser = pwmApplication.getProxiedChaiUser( userInfo.getUserIdentity() );
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
                throw new PwmUnrecoverableException( PwmError.forChaiError( e.getErrorCode() ) );
            }
        }

        final List<FormConfiguration> attributeForm = figureAttributeForm( forgottenPasswordProfile, forgottenPasswordBean, commonValues, userIdentity );

        forgottenPasswordBean.setUserLocale( locale );
        forgottenPasswordBean.setPresentableChallengeSet( challengeSet == null ? null : challengeSet.asChallengeSetBean() );
        forgottenPasswordBean.setAttributeForm( attributeForm );

        forgottenPasswordBean.setRecoveryFlags( recoveryFlags );
        forgottenPasswordBean.setProgress( new ForgottenPasswordBean.Progress() );

        for ( final IdentityVerificationMethod recoveryVerificationMethods : recoveryFlags.getRequiredAuthMethods() )
        {
            verifyRequirementsForAuthMethod( commonValues, forgottenPasswordBean, recoveryVerificationMethods );
        }
    }

    static List<FormConfiguration> figureAttributeForm(
            final ForgottenPasswordProfile forgottenPasswordProfile,
            final ForgottenPasswordBean forgottenPasswordBean,
            final CommonValues commonValues,
            final UserIdentity userIdentity
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final List<FormConfiguration> requiredAttributesForm = forgottenPasswordProfile.readSettingAsForm( PwmSetting.RECOVERY_ATTRIBUTE_FORM );
        if ( requiredAttributesForm.isEmpty() )
        {
            return requiredAttributesForm;
        }

        final UserInfo userInfo = readUserInfo( commonValues, forgottenPasswordBean );
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
                        LOGGER.trace( commonValues.getSessionLabel(), () -> "excluding optional required attribute(" + formItem.getName() + "), user has no value" );
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
            final PwmApplication pwmApplication,
            final String forgottenPasswordProfileID
    )
    {
        final Configuration config = pwmApplication.getConfig();
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
            final CommonValues commonValues,
            final ForgottenPasswordBean forgottenPasswordBean,
            final IdentityVerificationMethod thisMethod
    )
    {
        if ( forgottenPasswordBean.getRecoveryFlags().getRequiredAuthMethods().contains( thisMethod )  )
        {
            return false;
        }

        {
            // check if has previously satisfied any other optional methods.
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
                    commonValues,
                    forgottenPasswordBean
            );
            final Set<IdentityVerificationMethod> otherOptionalMethodChoices = new HashSet<>( remainingAvailableOptionalMethods );
            otherOptionalMethodChoices.remove( thisMethod );

            if ( !otherOptionalMethodChoices.isEmpty() )
            {
                return true;
            }
        }

        return false;
    }
}
