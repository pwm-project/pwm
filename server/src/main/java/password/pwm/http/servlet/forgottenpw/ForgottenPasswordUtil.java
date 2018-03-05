/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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
import password.pwm.config.profile.ProfileType;
import password.pwm.config.profile.ProfileUtility;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.ForgottenPasswordBean;
import password.pwm.http.filter.AuthenticationFilter;
import password.pwm.i18n.Message;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecord;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.svc.token.TokenPayload;
import password.pwm.svc.token.TokenService;
import password.pwm.svc.token.TokenType;
import password.pwm.util.PasswordData;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.ws.client.rest.RestTokenDataClient;

import javax.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ForgottenPasswordUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ForgottenPasswordUtil.class );

    static Set<IdentityVerificationMethod> figureRemainingAvailableOptionalAuthMethods(
            final PwmRequest pwmRequest,
            final ForgottenPasswordBean forgottenPasswordBean
    )
    {
        final ForgottenPasswordBean.RecoveryFlags recoveryFlags = forgottenPasswordBean.getRecoveryFlags();
        final ForgottenPasswordBean.Progress progress = forgottenPasswordBean.getProgress();
        final Set<IdentityVerificationMethod> result = new LinkedHashSet<>();
        result.addAll( recoveryFlags.getOptionalAuthMethods() );
        result.removeAll( progress.getSatisfiedMethods() );

        for ( final IdentityVerificationMethod recoveryVerificationMethods : new LinkedHashSet<>( result ) )
        {
            try
            {
                verifyRequirementsForAuthMethod( pwmRequest, forgottenPasswordBean, recoveryVerificationMethods );
            }
            catch ( PwmUnrecoverableException e )
            {
                result.remove( recoveryVerificationMethods );
            }
        }

        return Collections.unmodifiableSet( result );
    }

    public static RecoveryAction getRecoveryAction( final Configuration configuration, final ForgottenPasswordBean forgottenPasswordBean )
    {
        final ForgottenPasswordProfile forgottenPasswordProfile = configuration.getForgottenPasswordProfiles().get( forgottenPasswordBean.getForgottenPasswordProfileID() );
        return forgottenPasswordProfile.readSettingAsEnum( PwmSetting.RECOVERY_ACTION, RecoveryAction.class );
    }


    static Set<IdentityVerificationMethod> figureSatisfiedOptionalAuthMethods(
            final ForgottenPasswordBean.RecoveryFlags recoveryFlags,
            final ForgottenPasswordBean.Progress progress )
    {
        final Set<IdentityVerificationMethod> result = new LinkedHashSet<>();
        result.addAll( recoveryFlags.getOptionalAuthMethods() );
        result.retainAll( progress.getSatisfiedMethods() );
        return Collections.unmodifiableSet( result );
    }

    static UserInfo readUserInfo(
            final PwmRequest pwmRequest,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws PwmUnrecoverableException
    {
        if ( forgottenPasswordBean.getUserIdentity() == null )
        {
            return null;
        }

        final String cacheKey = PwmConstants.REQUEST_ATTR_FORGOTTEN_PW_USERINFO_CACHE;

        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();

        {
            final UserInfo userInfoFromSession = ( UserInfo ) pwmRequest.getHttpServletRequest().getAttribute( cacheKey );
            if ( userInfoFromSession != null )
            {
                if ( userIdentity.equals( userInfoFromSession.getUserIdentity() ) )
                {
                    LOGGER.trace( pwmRequest, "using request cached userInfo" );
                    return userInfoFromSession;
                }
                else
                {
                    LOGGER.trace( pwmRequest, "request cached userInfo is not for current user, clearing." );
                    pwmRequest.getHttpServletRequest().getSession().setAttribute( cacheKey, null );
                }
            }
        }

        final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxy(
                pwmRequest.getPwmApplication(),
                pwmRequest.getSessionLabel(),
                userIdentity, pwmRequest.getLocale()
        );

        pwmRequest.getHttpServletRequest().setAttribute( cacheKey, userInfo );

        return userInfo;
    }

    static ResponseSet readResponseSet( final PwmRequest pwmRequest, final ForgottenPasswordBean forgottenPasswordBean )
            throws PwmUnrecoverableException
    {

        if ( forgottenPasswordBean.getUserIdentity() == null )
        {
            return null;
        }

        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
        final ResponseSet responseSet;

        try
        {
            final ChaiUser theUser = pwmApplication.getProxiedChaiUser( userIdentity );
            responseSet = pwmApplication.getCrService().readUserResponseSet(
                    pwmRequest.getSessionLabel(),
                    userIdentity,
                    theUser
            );
        }
        catch ( ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
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
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail( PwmSetting.EMAIL_UNLOCK, locale );

        if ( configuredEmailSetting == null )
        {
            LOGGER.debug( pwmRequest, "skipping send unlock notice email for '" + userIdentity + "' no email configured" );
            return;
        }

        final UserInfo userInfo = readUserInfo( pwmRequest, forgottenPasswordBean );
        final MacroMachine macroMachine = MacroMachine.forUser(
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

    static boolean checkAuthRecord( final PwmRequest pwmRequest, final String userGuid )
            throws PwmUnrecoverableException
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
                LOGGER.trace( pwmRequest, "skipping auth record cookie read, cookie name parameter is blank" );
                return false;
            }

            final AuthenticationFilter.AuthRecord authRecord = pwmRequest.readEncryptedCookie( cookieName, AuthenticationFilter.AuthRecord.class );
            if ( authRecord != null )
            {
                if ( authRecord.getGuid() != null && !authRecord.getGuid().isEmpty() && authRecord.getGuid().equals( userGuid ) )
                {
                    LOGGER.debug( pwmRequest, "auth record cookie validated" );
                    return true;
                }
            }
        }
        catch ( Exception e )
        {
            LOGGER.error( pwmRequest, "unexpected error while examining cookie auth record: " + e.getMessage() );
        }
        return false;
    }

    static List<TokenDestinationItem> figureAvailableTokenDestinations(
            final PwmRequest pwmRequest,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws PwmUnrecoverableException
    {
        {
            @SuppressWarnings( "unchecked" )
            final List<TokenDestinationItem> cachedItems = (List<TokenDestinationItem>) pwmRequest.getHttpServletRequest().getAttribute(
                    PwmConstants.REQUEST_ATTR_FORGOTTEN_PW_AVAIL_TOKEN_DEST_CACHE
            );
            if ( cachedItems != null )
            {
                return cachedItems;
            }
        }

        final String profileID = forgottenPasswordBean.getForgottenPasswordProfileID();
        final ForgottenPasswordProfile forgottenPasswordProfile = pwmRequest.getConfig().getForgottenPasswordProfiles().get( profileID );
        final MessageSendMethod tokenSendMethod = forgottenPasswordProfile.readSettingAsEnum( PwmSetting.RECOVERY_TOKEN_SEND_METHOD, MessageSendMethod.class );
        if ( tokenSendMethod == null || tokenSendMethod.equals( MessageSendMethod.NONE ) )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_TOKEN_MISSING_CONTACT, "no token send methods configured in profile" );
        }

        final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequest, forgottenPasswordBean );
        List<TokenDestinationItem> tokenDestinations = new ArrayList<>( TokenDestinationItem.allFromConfig( pwmRequest.getPwmApplication(), userInfo ) );

        if ( tokenSendMethod != MessageSendMethod.CHOICE_SMS_EMAIL )
        {
            tokenDestinations = tokenDestinations
                    .stream()
                    .filter( tokenDestinationItem -> tokenSendMethod == tokenDestinationItem.getType().getMessageSendMethod() )
                    .collect( Collectors.toList() );
        }

        final List<TokenDestinationItem> effectiveItems = new ArrayList<>(  );
        for ( final TokenDestinationItem item : tokenDestinations )
        {
            final TokenDestinationItem effectiveItem = invokeExternalTokenDestRestClient( pwmRequest, userInfo.getUserIdentity(), item );
            effectiveItems.add( effectiveItem );
        }

        LOGGER.trace( pwmRequest, "calculated available token send destinations: " + JsonUtil.serializeCollection( effectiveItems ) );

        if ( tokenDestinations.isEmpty() )
        {
            final String msg = "no available contact methods of type " + tokenSendMethod.name() + " available";
            throw PwmUnrecoverableException.newException( PwmError.ERROR_TOKEN_MISSING_CONTACT, msg );
        }

        final List<TokenDestinationItem> finalList = Collections.unmodifiableList( effectiveItems );
        pwmRequest.getHttpServletRequest().setAttribute( PwmConstants.REQUEST_ATTR_FORGOTTEN_PW_AVAIL_TOKEN_DEST_CACHE, finalList );

        return finalList;
    }

    static void verifyRequirementsForAuthMethod(
            final PwmRequest pwmRequest,
            final ForgottenPasswordBean forgottenPasswordBean,
            final IdentityVerificationMethod recoveryVerificationMethods
    )
            throws PwmUnrecoverableException
    {
        switch ( recoveryVerificationMethods )
        {
            case TOKEN:
            {
                ForgottenPasswordUtil.figureAvailableTokenDestinations( pwmRequest, forgottenPasswordBean );
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
                final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequest, forgottenPasswordBean );
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
                final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequest, forgottenPasswordBean );
                final ResponseSet responseSet = ForgottenPasswordUtil.readResponseSet( pwmRequest, forgottenPasswordBean );
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
                catch ( ChaiValidationException e )
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
            final ChallengeSet challengeSet
    )
            throws PwmUnrecoverableException
    {
        final Map<Challenge, String> responses = new LinkedHashMap<>();

        int counter = 0;
        for ( final Challenge loopChallenge : challengeSet.getChallenges() )
        {
            counter++;
            final String answer = req.readParameterAsString( PwmConstants.PARAM_RESPONSE_PREFIX + counter );

            responses.put( loopChallenge, answer.length() > 0 ? answer : "" );
        }

        return responses;
    }

    static void initializeAndSendToken(
            final PwmRequest pwmRequest,
            final UserInfo userInfo,
            final TokenDestinationItem tokenDestinationItem

    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        final UserIdentity userIdentity = userInfo.getUserIdentity();
        final Map<String, String> tokenMapData = new LinkedHashMap<>();

        try
        {
            final Instant userLastPasswordChange = PasswordUtility.determinePwdLastModified(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getSessionLabel(),
                    userIdentity
            );
            if ( userLastPasswordChange != null )
            {
                final String userChangeString = JavaHelper.toIsoDate( userLastPasswordChange );
                tokenMapData.put( PwmConstants.TOKEN_KEY_PWD_CHG_DATE, userChangeString );
            }
        }
        catch ( ChaiUnavailableException e )
        {
            LOGGER.error( pwmRequest, "unexpected error reading user's last password change time" );
        }

        final EmailItemBean emailItemBean = config.readSettingAsEmail( PwmSetting.EMAIL_CHALLENGE_TOKEN, pwmRequest.getLocale() );
        final MacroMachine.StringReplacer stringReplacer = ( matchedMacro, newValue ) ->
        {
            if ( "@User:Email@".equals( matchedMacro )  )
            {
                return tokenDestinationItem.getValue();
            }

            return newValue;
        };
        final MacroMachine macroMachine = MacroMachine.forUser( pwmRequest, userIdentity, stringReplacer );

        final String tokenKey;
        final TokenPayload tokenPayload;
        try
        {
            tokenPayload = pwmRequest.getPwmApplication().getTokenService().createTokenPayload(
                    TokenType.FORGOTTEN_PW,
                    new TimeDuration( config.readSettingAsLong( PwmSetting.TOKEN_LIFETIME ), TimeUnit.SECONDS ),
                    tokenMapData,
                    userIdentity,
                    Collections.singleton( tokenDestinationItem.getValue() )
            );
            tokenKey = pwmRequest.getPwmApplication().getTokenService().generateNewToken( tokenPayload, pwmRequest.getSessionLabel() );
        }
        catch ( PwmOperationalException e )
        {
            throw new PwmUnrecoverableException( e.getErrorInformation() );
        }

        final String smsMessage = config.readSettingAsLocalizedString( PwmSetting.SMS_CHALLENGE_TOKEN_TEXT, pwmRequest.getLocale() );

        TokenService.TokenSender.sendToken(
                TokenService.TokenSendInfo.builder()
                        .pwmApplication( pwmRequest.getPwmApplication() )
                        .userInfo( userInfo )
                        .macroMachine( macroMachine )
                        .configuredEmailSetting( emailItemBean )
                        .tokenSendMethod( tokenDestinationItem.getType().getMessageSendMethod() )
                        .emailAddress( tokenDestinationItem.getValue() )
                        .smsNumber( tokenDestinationItem.getValue() )
                        .smsMessage( smsMessage )
                        .tokenKey( tokenKey )
                        .sessionLabel( pwmRequest.getSessionLabel() )
                        .build()
        );

        StatisticsManager.incrementStat( pwmRequest, Statistic.RECOVERY_TOKENS_SENT );
    }

    private static TokenDestinationItem invokeExternalTokenDestRestClient(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity,
            final TokenDestinationItem tokenDestinationItem
    )
            throws PwmUnrecoverableException
    {
        final RestTokenDataClient.TokenDestinationData inputDestinationData = new RestTokenDataClient.TokenDestinationData(
                tokenDestinationItem.getType() == TokenDestinationItem.Type.email ? tokenDestinationItem.getValue() : null,
                tokenDestinationItem.getType() == TokenDestinationItem.Type.sms ? tokenDestinationItem.getValue() : null,
                tokenDestinationItem.getDisplay()
        );

        final RestTokenDataClient restTokenDataClient = new RestTokenDataClient( pwmRequest.getPwmApplication() );
        final RestTokenDataClient.TokenDestinationData outputDestrestTokenDataClient = restTokenDataClient.figureDestTokenDisplayString(
                pwmRequest.getSessionLabel(),
                inputDestinationData,
                userIdentity,
                pwmRequest.getLocale() );

        final String outputValue = tokenDestinationItem.getType() == TokenDestinationItem.Type.email
                ? outputDestrestTokenDataClient.getEmail()
                : outputDestrestTokenDataClient.getSms();

        return TokenDestinationItem.builder()
                .type( tokenDestinationItem.getType() )
                .display( outputDestrestTokenDataClient.getDisplayValue() )
                .value( outputValue )
                .id( tokenDestinationItem.getId() )
                .build();
    }

    static void doActionSendNewPassword( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final ForgottenPasswordBean forgottenPasswordBean = ForgottenPasswordServlet.forgottenPasswordBean( pwmRequest );
        final ForgottenPasswordProfile forgottenPasswordProfile = forgottenPasswordProfile( pwmRequest.getPwmApplication(), forgottenPasswordBean );
        final RecoveryAction recoveryAction = ForgottenPasswordUtil.getRecoveryAction( pwmApplication.getConfig(), forgottenPasswordBean );

        LOGGER.trace( pwmRequest, "beginning process to send new password to user" );

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
            LOGGER.trace( pwmRequest, "unlock account succeeded" );
        }
        catch ( ChaiOperationException e )
        {
            final String errorMsg = "unable to unlock user " + theUser.getEntryDN() + " error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNLOCK_FAILURE, errorMsg );
            LOGGER.error( pwmRequest.getPwmSession(), errorInformation.toDebugStr() );
            pwmRequest.respondWithError( errorInformation );
            return;
        }

        try
        {
            final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxy(
                    pwmApplication,
                    pwmRequest.getSessionLabel(),
                    userIdentity,
                    pwmRequest.getLocale()
            );

            LOGGER.info( pwmRequest, "user successfully supplied password recovery responses, emailing new password to: " + theUser.getEntryDN() );

            // add post change actions
            ForgottenPasswordServlet.addPostChangeAction( pwmRequest, userIdentity );

            // create new password
            final PasswordData newPassword = RandomPasswordGenerator.createRandomPassword(
                    pwmRequest.getSessionLabel(),
                    userInfo.getPasswordPolicy(),
                    pwmApplication
            );
            LOGGER.trace( pwmRequest, "generated random password value based on password policy for "
                    + userIdentity.toDisplayString() );


            // set the password
            try
            {
                theUser.setPassword( newPassword.getStringValue() );
                LOGGER.trace( pwmRequest, "set user " + userIdentity.toDisplayString()
                        + " password to system generated random value" );
            }
            catch ( ChaiException e )
            {
                throw PwmUnrecoverableException.fromChaiException( e );
            }

            if ( recoveryAction == RecoveryAction.SENDNEWPW_AND_EXPIRE )
            {
                LOGGER.debug( pwmRequest, "marking user " + userIdentity.toDisplayString() + " password as expired" );
                theUser.expirePassword();
            }

            // mark the event log
            {
                final AuditRecord auditRecord = new AuditRecordFactory( pwmApplication ).createUserAuditRecord(
                        AuditEvent.RECOVER_PASSWORD,
                        userIdentity,
                        pwmRequest.getSessionLabel()
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
        catch ( PwmException e )
        {
            LOGGER.warn( pwmRequest, "unexpected error setting new password during recovery process for user: " + e.getMessage() );
            pwmRequest.respondWithError( e.getErrorInformation() );
        }
        catch ( ChaiOperationException e )
        {
            final ErrorInformation errorInformation = new ErrorInformation(
                    PwmError.ERROR_UNKNOWN,
                    "unexpected ldap error while processing recovery action " + recoveryAction + ", error: " + e.getMessage()
            );
            LOGGER.warn( pwmRequest, errorInformation.toDebugStr() );
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

    static void initBogusForgottenPasswordBean( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = ForgottenPasswordServlet.forgottenPasswordBean( pwmRequest );
        forgottenPasswordBean.setUserIdentity( null );
        forgottenPasswordBean.setPresentableChallengeSet( null );


        final List<Challenge> challengeList = new ArrayList<>( );
        {
            final String firstProfile = pwmRequest.getConfig().getChallengeProfileIDs().iterator().next();
            final ChallengeSet challengeSet = pwmRequest.getConfig().getChallengeProfile( firstProfile, PwmConstants.DEFAULT_LOCALE ).getChallengeSet();
            challengeList.addAll( challengeSet.getRequiredChallenges() );
            for ( int i = 0; i < challengeSet.getMinRandomRequired(); i++ )
            {
                challengeList.add( challengeSet.getRandomChallenges().get( i ) );
            }
        }

        final List<FormConfiguration> formData = new ArrayList<>(  );
        {
            int counter = 0;
            for ( Challenge challenge: challengeList )
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
            final String profileID = pwmRequest.getConfig().getForgottenPasswordProfiles().keySet().iterator().next();
            forgottenPasswordBean.setForgottenPasswordProfileID( profileID  );
        }

        final ForgottenPasswordBean.RecoveryFlags recoveryFlags = new ForgottenPasswordBean.RecoveryFlags(
                false,
                Collections.singleton( IdentityVerificationMethod.ATTRIBUTES ),
                Collections.emptySet(),
                0
        );

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
        catch ( PwmUnrecoverableException e )
        {
            LOGGER.debug( sessionLabel, "can't read user's forgotten password profile - assuming no profile assigned, error: " + e.getMessage() );
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
                ProfileType.ForgottenPassword
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
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {

        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Locale locale = pwmRequest.getLocale();
        final SessionLabel sessionLabel = pwmRequest.getSessionLabel();

        forgottenPasswordBean.setUserIdentity( userIdentity );

        final UserInfo userInfo = readUserInfo( pwmRequest, forgottenPasswordBean );

        final ForgottenPasswordProfile forgottenPasswordProfile = forgottenPasswordProfile(
                pwmApplication,
                pwmRequest.getSessionLabel(),
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
            catch ( ChaiValidationException e )
            {
                final String errorMsg = "unable to determine presentable challengeSet for stored responses: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NO_CHALLENGES, errorMsg );
                throw new PwmUnrecoverableException( errorInformation );
            }
            catch ( ChaiUnavailableException e )
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
            catch ( ChaiOperationException e )
            {
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN,
                        "error checking user '" + userInfo.getUserIdentity() + "' ldap intruder lock status: " + e.getMessage() );
                LOGGER.error( sessionLabel, errorInformation );
                throw new PwmUnrecoverableException( errorInformation );
            }
            catch ( ChaiUnavailableException e )
            {
                throw new PwmUnrecoverableException( PwmError.forChaiError( e.getErrorCode() ) );
            }
        }

        final List<FormConfiguration> attributeForm;
        try
        {
            attributeForm = figureAttributeForm( forgottenPasswordProfile, forgottenPasswordBean, pwmRequest, userIdentity );
        }
        catch ( ChaiUnavailableException e )
        {
            throw new PwmUnrecoverableException( PwmError.forChaiError( e.getErrorCode() ) );
        }

        forgottenPasswordBean.setUserLocale( locale );
        forgottenPasswordBean.setPresentableChallengeSet( challengeSet );
        forgottenPasswordBean.setAttributeForm( attributeForm );

        forgottenPasswordBean.setRecoveryFlags( recoveryFlags );
        forgottenPasswordBean.setProgress( new ForgottenPasswordBean.Progress() );

        for ( final IdentityVerificationMethod recoveryVerificationMethods : recoveryFlags.getRequiredAuthMethods() )
        {
            verifyRequirementsForAuthMethod( pwmRequest, forgottenPasswordBean, recoveryVerificationMethods );
        }
    }

    static List<FormConfiguration> figureAttributeForm(
            final ForgottenPasswordProfile forgottenPasswordProfile,
            final ForgottenPasswordBean forgottenPasswordBean,
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmOperationalException, PwmUnrecoverableException
    {
        final List<FormConfiguration> requiredAttributesForm = forgottenPasswordProfile.readSettingAsForm( PwmSetting.RECOVERY_ATTRIBUTE_FORM );
        if ( requiredAttributesForm.isEmpty() )
        {
            return requiredAttributesForm;
        }

        final UserInfo userInfo = readUserInfo( pwmRequest, forgottenPasswordBean );
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
                        LOGGER.trace( pwmRequest, "excluding optional required attribute(" + formItem.getName() + "), user has no value" );
                    }
                }
                catch ( PwmUnrecoverableException e )
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
}
