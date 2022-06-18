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
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.VerificationMethodSystem;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.PasswordStatus;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.option.RecoveryAction;
import password.pwm.config.option.RecoveryMinLifetimeOption;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmRequestContext;
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
import password.pwm.user.UserInfo;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.AuthenticationUtility;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecord;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.event.AuditServiceClient;
import password.pwm.svc.intruder.IntruderServiceClient;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.svc.token.TokenPayload;
import password.pwm.svc.token.TokenService;
import password.pwm.svc.token.TokenType;
import password.pwm.svc.token.TokenUtil;
import password.pwm.util.CaptchaUtility;
import password.pwm.util.form.FormUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.MiscUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.svc.cr.NMASCrOperator;
import password.pwm.svc.otp.OTPUserRecord;
import password.pwm.util.password.PasswordUtility;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * User interaction servlet for recovering user's password using secret question/answer.
 *
 * @author Jason D. Rivard
 */
@WebServlet(
        name = "ForgottenPasswordServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + "/forgottenpassword",
                PwmConstants.URL_PREFIX_PUBLIC + "/forgottenpassword/*",
                PwmConstants.URL_PREFIX_PUBLIC + "/ForgottenPassword",
                PwmConstants.URL_PREFIX_PUBLIC + "/ForgottenPassword/*",
        }
)
public class ForgottenPasswordServlet extends ControlledPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ForgottenPasswordServlet.class );

    public enum ForgottenPasswordAction implements AbstractPwmServlet.ProcessAction
    {
        search( HttpMethod.POST ),
        checkResponses( HttpMethod.POST ),
        checkAttributes( HttpMethod.POST ),
        enterCode( HttpMethod.POST, HttpMethod.GET ),
        enterOtp( HttpMethod.POST ),
        reset( HttpMethod.POST ),
        actionChoice( HttpMethod.POST ),
        tokenChoice( HttpMethod.POST ),
        verificationChoice( HttpMethod.POST ),
        enterRemoteResponse( HttpMethod.POST ),
        oauthReturn( HttpMethod.GET ),
        resendToken( HttpMethod.POST ),
        agree( HttpMethod.POST ),;

        private final Collection<HttpMethod> method;

        ForgottenPasswordAction( final HttpMethod... method )
        {
            this.method = List.of( method );
        }

        @Override
        public Collection<HttpMethod> permittedMethods( )
        {
            return method;
        }
    }

    public enum ResetAction
    {
        exitForgottenPassword,
        gotoSearch,
        clearTokenDestination, clearActionChoice,
    }


    @Override
    public Class<? extends ProcessAction> getProcessActionsClass( )
    {
        return ForgottenPasswordAction.class;
    }

    public enum ActionChoice
    {
        unlock,
        resetPassword,
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {

        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();

        final DomainConfig config = pwmDomain.getConfig();

        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        if ( !config.readSettingAsBoolean( PwmSetting.FORGOTTEN_PASSWORD_ENABLE ) )
        {
            pwmRequest.respondWithError( PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo() );
            return ProcessStatus.Halt;
        }

        if ( pwmSession.isAuthenticated() )
        {
            pwmRequest.respondWithError( PwmError.ERROR_USERAUTHENTICATED.toInfo() );
            return ProcessStatus.Halt;
        }

        if ( forgottenPasswordBean.getUserIdentity() != null )
        {
            IntruderServiceClient.checkUserIdentity( pwmDomain, forgottenPasswordBean.getUserIdentity() );
        }

        checkForLocaleSwitch( pwmRequest, forgottenPasswordBean );

        final Optional<? extends ProcessAction> action = this.readProcessAction( pwmRequest );

        // convert a url command like /public/newuser/12321321 to redirect with a process action.
        if ( action.isEmpty() )
        {
            if ( pwmRequest.convertURLtokenCommand( PwmServletDefinition.ForgottenPassword, ForgottenPasswordAction.enterCode ) )
            {
                return ProcessStatus.Halt;
            }
        }

        return ProcessStatus.Continue;
    }

    static ForgottenPasswordBean forgottenPasswordBean( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return pwmRequest.getPwmDomain().getSessionStateService().getBean( pwmRequest, ForgottenPasswordBean.class );
    }

    static void clearForgottenPasswordBean( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        pwmRequest.getPwmDomain().getSessionStateService().clearBean( pwmRequest, ForgottenPasswordBean.class );
    }

    @ActionHandler( action = "actionChoice" )
    public ProcessStatus processActionChoice( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, ServletException, IOException, ChaiUnavailableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        final ForgottenPasswordProfile forgottenPasswordProfile = ForgottenPasswordUtil.forgottenPasswordProfile( pwmRequest.getPwmDomain(), forgottenPasswordBean );

        final boolean resendEnabled = forgottenPasswordProfile.readSettingAsBoolean( PwmSetting.TOKEN_RESEND_ENABLE );

        if ( resendEnabled )
        {
            // clear token dest info in case we got here from a user 'go-back' request
            forgottenPasswordBean.getProgress().clearTokenSentStatus();
        }

        final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequest.getPwmRequestContext(), forgottenPasswordBean )
                .orElseThrow( () -> PwmUnrecoverableException.newException( PwmError.ERROR_RECOVERY_SEQUENCE_INCOMPLETE, "actionChoice without userInfo present" ) );

        final boolean disallowAllButUnlock;
        {

            final RecoveryMinLifetimeOption minLifetimeOption = forgottenPasswordProfile.readSettingAsEnum(
                    PwmSetting.RECOVERY_MINIMUM_PASSWORD_LIFETIME_OPTIONS,
                    RecoveryMinLifetimeOption.class );
            disallowAllButUnlock = minLifetimeOption == RecoveryMinLifetimeOption.UNLOCKONLY
                    && userInfo.isPasswordLocked();
        }


        if ( forgottenPasswordBean.getProgress().isAllPassed() )
        {
            final String choice = pwmRequest.readParameterAsString( "choice" );

            final ActionChoice actionChoice = JavaHelper.readEnumFromString( ActionChoice.class, null, choice );
            if ( actionChoice != null )
            {
                switch ( actionChoice )
                {
                    case unlock:
                        this.executeUnlock( pwmRequest );
                        break;

                    case resetPassword:
                        if ( disallowAllButUnlock )
                        {
                            PasswordUtility.throwPasswordTooSoonException( userInfo, pwmRequest.getLabel() );
                        }
                        this.executeResetPassword( pwmRequest );
                        break;

                    default:
                        MiscUtil.unhandledSwitchStatement( actionChoice );
                }
            }
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "reset" )
    public ProcessStatus processReset( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final ResetAction resetType = pwmRequest.readParameterAsEnum( PwmConstants.PARAM_RESET_TYPE, ResetAction.class ).orElse( ResetAction.exitForgottenPassword );

        switch ( resetType )
        {
            case exitForgottenPassword:
                clearForgottenPasswordBean( pwmRequest );
                pwmRequest.getPwmResponse().sendRedirectToContinue();
                return ProcessStatus.Halt;

            case gotoSearch:
                clearForgottenPasswordBean( pwmRequest );
                break;

            case clearTokenDestination:
                forgottenPasswordBean( pwmRequest ).getProgress().setTokenDestination( null );
                forgottenPasswordBean( pwmRequest ).getProgress().setTokenSent( false );
                break;

            case clearActionChoice:
                forgottenPasswordBean( pwmRequest ).getProgress().setTokenDestination( null );
                forgottenPasswordBean( pwmRequest ).getProgress().setTokenSent( false );
                forgottenPasswordBean( pwmRequest ).getProgress().setInProgressVerificationMethod( null );
                break;

            default:
                MiscUtil.unhandledSwitchStatement( resetType );

        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "tokenChoice" )
    public ProcessStatus processTokenChoice( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        final List<TokenDestinationItem> items = ForgottenPasswordUtil.figureAvailableTokenDestinations( pwmRequest.getPwmRequestContext(), forgottenPasswordBean );

        final String requestedID = pwmRequest.readParameterAsString( "choice", PwmHttpRequestWrapper.Flag.BypassValidation );

        final Optional<TokenDestinationItem> tokenDestinationItem = TokenDestinationItem.tokenDestinationItemForID( items, requestedID );
        if ( tokenDestinationItem.isPresent() )
        {
            forgottenPasswordBean.getProgress().setTokenDestination( tokenDestinationItem.get() );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "verificationChoice" )
    public ProcessStatus processVerificationChoice( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, ServletException, IOException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        final String requestedChoiceStr = pwmRequest.readParameterAsString( PwmConstants.PARAM_METHOD_CHOICE );
        final LinkedHashSet<IdentityVerificationMethod> remainingAvailableOptionalMethods = new LinkedHashSet<>(
                ForgottenPasswordUtil.figureRemainingAvailableOptionalAuthMethods( pwmRequest.getPwmRequestContext(), forgottenPasswordBean )
        );
        pwmRequest.setAttribute( PwmRequestAttribute.AvailableAuthMethods, remainingAvailableOptionalMethods );

        IdentityVerificationMethod requestedChoice = null;
        if ( requestedChoiceStr != null && !requestedChoiceStr.isEmpty() )
        {
            try
            {
                requestedChoice = IdentityVerificationMethod.valueOf( requestedChoiceStr );
            }
            catch ( final IllegalArgumentException e )
            {
                final String errorMsg = "unknown verification method requested";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, errorMsg );
                setLastError( pwmRequest, errorInformation );
                pwmRequest.forwardToJsp( JspUrl.RECOVER_PASSWORD_METHOD_CHOICE );
                return ProcessStatus.Halt;
            }
        }

        if ( remainingAvailableOptionalMethods.contains( requestedChoice ) )
        {
            forgottenPasswordBean.getProgress().setInProgressVerificationMethod( requestedChoice );
            pwmRequest.setAttribute( PwmRequestAttribute.ForgottenPasswordOptionalPageView, "true" );
            forwardUserBasedOnRecoveryMethod( pwmRequest, requestedChoice );
            return ProcessStatus.Continue;
        }
        else if ( requestedChoice != null )
        {
            final String errorMsg = "requested verification method is not available at this time";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, errorMsg );
            setLastError( pwmRequest, errorInformation );
        }

        pwmRequest.forwardToJsp( JspUrl.RECOVER_PASSWORD_METHOD_CHOICE );

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "search" )
    public ProcessStatus processSearch( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final Locale userLocale = pwmRequest.getLocale();
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();

        final String contextParam = pwmRequest.readParameterAsString( PwmConstants.PARAM_CONTEXT );
        final String ldapProfile = pwmRequest.readParameterAsString( PwmConstants.PARAM_LDAP_PROFILE );

        final boolean bogusUserModeEnabled = pwmRequest.getDomainConfig().readSettingAsBoolean( PwmSetting.RECOVERY_BOGUS_USER_ENABLE );

        // clear the bean
        clearForgottenPasswordBean( pwmRequest );

        if ( CaptchaUtility.captchaEnabledForRequest( pwmRequest ) )
        {
            if ( !CaptchaUtility.verifyReCaptcha( pwmRequest ) )
            {
                final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_BAD_CAPTCHA_RESPONSE );
                LOGGER.debug( pwmRequest, errorInfo );
                setLastError( pwmRequest, errorInfo );
                return ProcessStatus.Continue;
            }
        }

        final List<FormConfiguration> forgottenPasswordForm = pwmDomain.getConfig().readSettingAsForm(
                PwmSetting.FORGOTTEN_PASSWORD_SEARCH_FORM );

        Map<FormConfiguration, String> formValues = new LinkedHashMap<>();

        try
        {
            //read the values from the request
            formValues = FormUtility.readFormValuesFromRequest( pwmRequest, forgottenPasswordForm, userLocale );

            // check for intruder search values
            IntruderServiceClient.checkAttributes( pwmDomain, formValues );

            // see if the values meet the configured form requirements.
            FormUtility.validateFormValues( pwmRequest.getDomainConfig(), formValues, userLocale );

            final String searchFilter;
            {
                final String configuredSearchFilter = pwmDomain.getConfig().readSettingAsString( PwmSetting.FORGOTTEN_PASSWORD_SEARCH_FILTER );
                if ( configuredSearchFilter == null || configuredSearchFilter.isEmpty() )
                {
                    searchFilter = FormUtility.ldapSearchFilterForForm( pwmDomain, forgottenPasswordForm );
                    LOGGER.trace( pwmRequest, () -> "auto generated ldap search filter: " + searchFilter );
                }
                else
                {
                    searchFilter = configuredSearchFilter;
                }
            }

            // convert the username field to an identity
            final UserIdentity userIdentity;
            {
                final UserSearchEngine userSearchEngine = pwmDomain.getUserSearchEngine();
                final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                        .filter( searchFilter )
                        .formValues( formValues )
                        .contexts( Collections.singletonList( contextParam ) )
                        .ldapProfile( ldapProfile )
                        .build();

                userIdentity = userSearchEngine.performSingleUserSearch( searchConfiguration, pwmRequest.getLabel() );
            }

            if ( userIdentity == null )
            {
                throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_CANT_MATCH_USER ) );
            }

            AuthenticationUtility.checkIfUserEligibleToAuthentication( pwmRequest.getLabel(), pwmDomain, userIdentity );

            final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
            ForgottenPasswordUtil.initForgottenPasswordBean( pwmRequest.getPwmRequestContext(), userIdentity, forgottenPasswordBean );

            // clear intruder search values
            IntruderServiceClient.clearAttributes( pwmDomain, formValues );

            return ProcessStatus.Continue;
        }
        catch ( final PwmOperationalException e )
        {
            if ( e.getError() != PwmError.ERROR_CANT_MATCH_USER || !bogusUserModeEnabled )
            {
                final ErrorInformation errorInfo = new ErrorInformation(
                        PwmError.ERROR_RESPONSES_NORESPONSES,
                        e.getErrorInformation().getDetailedErrorMsg(), e.getErrorInformation().getFieldValues()
                );

                StatisticsClient.incrementStat( pwmRequest, Statistic.RECOVERY_FAILURES );

                IntruderServiceClient.markAddressAndSession( pwmDomain, pwmRequest.getPwmSession() );
                IntruderServiceClient.markAttributes( pwmRequest, formValues );

                LOGGER.debug( pwmRequest, errorInfo );
                setLastError( pwmRequest, errorInfo );
                return ProcessStatus.Continue;
            }
        }

        if ( bogusUserModeEnabled )
        {
            ForgottenPasswordUtil.initBogusForgottenPasswordBean( pwmRequest.getPwmRequestContext(), forgottenPasswordBean( pwmRequest ) );
            forgottenPasswordBean( pwmRequest ).setUserSearchValues( FormUtility.asStringMap( formValues ) );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "enterCode" )
    public ProcessStatus processEnterCode( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        final String userEnteredCode = pwmRequest.readParameterAsString( PwmConstants.PARAM_TOKEN );

        ErrorInformation errorInformation = null;
        try
        {
            final PwmRequestContext pwmRequestContext = pwmRequest.getPwmRequestContext();
            final TokenPayload tokenPayload = TokenUtil.checkEnteredCode(
                    pwmRequestContext,
                    userEnteredCode,
                    forgottenPasswordBean.getProgress().getTokenDestination(),
                    null,
                    TokenType.FORGOTTEN_PW,
                    TokenService.TokenEntryType.unauthenticated
            );

            // token correct
            if ( forgottenPasswordBean.getUserIdentity() == null )
            {
                // clean session, user supplied token (clicked email, etc) and this is first request
                ForgottenPasswordUtil.initForgottenPasswordBean(
                        pwmRequestContext,
                        tokenPayload.getUserIdentity(),
                        forgottenPasswordBean
                );
            }
            forgottenPasswordBean.getProgress().getSatisfiedMethods().add( IdentityVerificationMethod.TOKEN );
            StatisticsClient.incrementStat( pwmRequest.getPwmDomain(), Statistic.RECOVERY_TOKENS_PASSED );

            if ( pwmRequest.getDomainConfig().readSettingAsBoolean( PwmSetting.DISPLAY_TOKEN_SUCCESS_BUTTON ) )
            {
                pwmRequest.setAttribute( PwmRequestAttribute.TokenDestItems, tokenPayload.getDestination() );
                pwmRequest.forwardToJsp( JspUrl.RECOVER_PASSWORD_TOKEN_SUCCESS );
                return ProcessStatus.Halt;
            }
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.debug( pwmRequest, () -> "error while checking entered token: " );
            errorInformation = e.getErrorInformation();
        }
        catch ( final PwmOperationalException e )
        {
            final String errorMsg = "token incorrect: " + e.getMessage();
            errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT, errorMsg );
        }

        if ( !forgottenPasswordBean.getProgress().getSatisfiedMethods().contains( IdentityVerificationMethod.TOKEN ) )
        {
            if ( errorInformation == null )
            {
                errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT );
            }
            handleUserVerificationBadAttempt( pwmRequest, forgottenPasswordBean, errorInformation );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "enterRemoteResponse" )
    public ProcessStatus processEnterRemoteResponse( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final String prefix = "remote-";
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        final VerificationMethodSystem remoteRecoveryMethod = forgottenPasswordBean.getProgress().getRemoteRecoveryMethod();

        final Map<String, String> remoteResponses = RemoteVerificationMethod.readRemoteResponses( pwmRequest, prefix );

        final ErrorInformation errorInformation = remoteRecoveryMethod.respondToPrompts( remoteResponses );

        if ( remoteRecoveryMethod.getVerificationState() == VerificationMethodSystem.VerificationState.COMPLETE )
        {
            forgottenPasswordBean.getProgress().getSatisfiedMethods().add( IdentityVerificationMethod.REMOTE_RESPONSES );
        }

        if ( remoteRecoveryMethod.getVerificationState() == VerificationMethodSystem.VerificationState.FAILED )
        {
            forgottenPasswordBean.getProgress().setRemoteRecoveryMethod( null );
            pwmRequest.respondWithError( errorInformation, true );
            handleUserVerificationBadAttempt( pwmRequest, forgottenPasswordBean, errorInformation );
            LOGGER.debug( pwmRequest, () -> "unsuccessful remote response verification input: " + errorInformation.toDebugStr() );
            return ProcessStatus.Continue;
        }

        if ( errorInformation != null )
        {
            setLastError( pwmRequest, errorInformation );
            handleUserVerificationBadAttempt( pwmRequest, forgottenPasswordBean, errorInformation );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "enterOtp" )
    public ProcessStatus processEnterOtpToken( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        final String userEnteredCode = pwmRequest.readParameterAsString( PwmConstants.PARAM_TOKEN );
        LOGGER.debug( pwmRequest, () -> String.format( "entered OTP: %s", userEnteredCode ) );

        final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequest.getPwmRequestContext(), forgottenPasswordBean )
                .orElseThrow( () -> PwmUnrecoverableException.newException( PwmError.ERROR_RECOVERY_SEQUENCE_INCOMPLETE, "enterOtp without userInfo present" ) );
        final OTPUserRecord otpUserRecord = userInfo.getOtpUserRecord();

        final boolean otpPassed;
        if ( otpUserRecord != null )
        {
            LOGGER.info( pwmRequest, () -> "checking entered OTP for user " + userInfo.getUserIdentity().toDisplayString() );
            try
            {
                // forces service to use proxy account to update (write) updated otp record if necessary.
                otpPassed = pwmRequest.getPwmDomain().getOtpService().validateToken(
                        null,
                        forgottenPasswordBean.getUserIdentity(),
                        otpUserRecord,
                        userEnteredCode,
                        true
                );

                if ( otpPassed )
                {
                    StatisticsClient.incrementStat( pwmRequest, Statistic.RECOVERY_OTP_PASSED );
                    LOGGER.debug( pwmRequest, () -> "one time password validation has been passed" );
                    forgottenPasswordBean.getProgress().getSatisfiedMethods().add( IdentityVerificationMethod.OTP );
                }
                else
                {
                    StatisticsClient.incrementStat( pwmRequest, Statistic.RECOVERY_OTP_FAILED );
                    handleUserVerificationBadAttempt( pwmRequest, forgottenPasswordBean, new ErrorInformation( PwmError.ERROR_INCORRECT_OTP_TOKEN ) );
                }
            }
            catch ( final PwmOperationalException e )
            {
                handleUserVerificationBadAttempt( pwmRequest, forgottenPasswordBean, new ErrorInformation(
                        PwmError.ERROR_INCORRECT_OTP_TOKEN,
                        e.getErrorInformation().toDebugStr() )
                );
            }
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "oauthReturn" )
    public ProcessStatus processOAuthReturn( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        if ( forgottenPasswordBean.getProgress().getInProgressVerificationMethod() != IdentityVerificationMethod.OAUTH )
        {
            LOGGER.debug( pwmRequest, () -> "oauth return detected, however current session did not issue an oauth request; will restart forgotten password sequence" );
            pwmRequest.getPwmDomain().getSessionStateService().clearBean( pwmRequest, ForgottenPasswordBean.class );
            pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.ForgottenPassword );
            return ProcessStatus.Halt;
        }

        if ( forgottenPasswordBean.getUserIdentity() == null )
        {
            LOGGER.debug( pwmRequest, () -> "oauth return detected, however current session does not have a user identity stored; will restart forgotten password sequence" );
            pwmRequest.getPwmDomain().getSessionStateService().clearBean( pwmRequest, ForgottenPasswordBean.class );
            pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.ForgottenPassword );
            return ProcessStatus.Halt;
        }

        final String encryptedResult = pwmRequest.readParameterAsString( PwmConstants.PARAM_RECOVERY_OAUTH_RESULT, PwmHttpRequestWrapper.Flag.BypassValidation );
        final OAuthForgottenPasswordResults results = pwmRequest.getPwmDomain().getSecureService().decryptObject( encryptedResult, OAuthForgottenPasswordResults.class );
        LOGGER.trace( pwmRequest, () -> "received" );

        final String userDNfromOAuth = results.getUsername();
        if ( userDNfromOAuth == null || userDNfromOAuth.isEmpty() )
        {
            final String errorMsg = "oauth server coderesolver endpoint did not return a username value";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_OAUTH_ERROR, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }

        final UserIdentity oauthUserIdentity;
        {
            final UserSearchEngine userSearchEngine = pwmRequest.getPwmDomain().getUserSearchEngine();
            try
            {
                oauthUserIdentity = userSearchEngine.resolveUsername( userDNfromOAuth, null, null, pwmRequest.getLabel() );
            }
            catch ( final PwmOperationalException e )
            {
                final String errorMsg = "unexpected error searching for oauth supplied username in ldap; error: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_OAUTH_ERROR, errorMsg );
                throw new PwmUnrecoverableException( errorInformation );
            }
        }

        final boolean userMatch;
        {
            final UserIdentity userIdentityInBean = forgottenPasswordBean.getUserIdentity();
            userMatch = userIdentityInBean != null && userIdentityInBean.equals( oauthUserIdentity );
        }

        if ( userMatch )
        {
            forgottenPasswordBean.getProgress().getSatisfiedMethods().add( IdentityVerificationMethod.OAUTH );
        }
        else
        {
            final String errorMsg = "oauth server username does not match previously identified user";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_OAUTH_ERROR, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "checkResponses" )
    public ProcessStatus processCheckResponses( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );

        if ( forgottenPasswordBean.getUserIdentity() == null )
        {
            return ProcessStatus.Continue;
        }
        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();

        final Optional<ResponseSet> responseSet = ForgottenPasswordUtil.readResponseSet( pwmRequest.getPwmRequestContext(), forgottenPasswordBean );
        if ( responseSet.isEmpty() )
        {
            final String errorMsg = "attempt to check responses, but responses are not loaded into session bean";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }

        try
        {
            // read the supplied responses from the user
            final Map<Challenge, String> crMap = ForgottenPasswordUtil.readResponsesFromHttpRequest(
                    pwmRequest,
                    forgottenPasswordBean.getPresentableChallengeSet()
            );

            final boolean responsesPassed;
            try
            {
                responsesPassed = responseSet.get().test( crMap );
            }
            catch ( final ChaiUnavailableException e )
            {
                if ( e.getCause() instanceof PwmUnrecoverableException )
                {
                    throw ( PwmUnrecoverableException ) e.getCause();
                }
                throw e;
            }

            // special case for nmas, clear out existing challenges and input fields.
            if ( !responsesPassed && responseSet.get() instanceof NMASCrOperator.NMASCRResponseSet )
            {
                forgottenPasswordBean.setPresentableChallengeSet( responseSet.get().getPresentableChallengeSet().asChallengeSetBean() );
            }

            if ( responsesPassed )
            {
                LOGGER.debug( pwmRequest, () -> "user '" + userIdentity + "' has supplied correct responses" );
            }
            else
            {
                final String errorMsg = "incorrect response to one or more challenges";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INCORRECT_RESPONSE, errorMsg );
                handleUserVerificationBadAttempt( pwmRequest, forgottenPasswordBean, errorInformation );
                return ProcessStatus.Continue;
            }
        }
        catch ( final ChaiValidationException e )
        {
            LOGGER.debug( pwmRequest, () -> "chai validation error checking user responses: " + e.getMessage() );
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.forChaiError( e.getErrorCode() ).orElse( PwmError.ERROR_INTERNAL ) );
            handleUserVerificationBadAttempt( pwmRequest, forgottenPasswordBean, errorInformation );
            return ProcessStatus.Continue;
        }

        forgottenPasswordBean.getProgress().getSatisfiedMethods().add( IdentityVerificationMethod.CHALLENGE_RESPONSES );

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "resendToken" )
    public ProcessStatus processResendToken( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );

        {
            final ForgottenPasswordProfile forgottenPasswordProfile = ForgottenPasswordUtil.forgottenPasswordProfile( pwmRequest.getPwmDomain(), forgottenPasswordBean );
            final boolean resendEnabled = forgottenPasswordProfile.readSettingAsBoolean( PwmSetting.TOKEN_RESEND_ENABLE );
            if ( !resendEnabled )
            {
                final String errorMsg = "token resend is not enabled";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg );
                throw new PwmUnrecoverableException( errorInformation );
            }
        }


        if ( !forgottenPasswordBean.getProgress().isTokenSent() )
        {
            final String errorMsg = "attempt to resend token, but initial token has not yet been sent";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }

        {
            LOGGER.trace( pwmRequest, () -> "preparing to send a new token to user" );
            final long delayTimeMs = Long.parseLong( pwmRequest.getDomainConfig().readAppProperty( AppProperty.TOKEN_RESEND_DELAY_MS ) );
            TimeDuration.of( delayTimeMs, TimeDuration.Unit.MILLISECONDS ).pause();
        }

        {
            final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequest.getPwmRequestContext(), forgottenPasswordBean )
                    .orElseThrow( () -> PwmUnrecoverableException.newException( PwmError.ERROR_RECOVERY_SEQUENCE_INCOMPLETE, "resendToken without userInfo present" ) );
            final TokenDestinationItem tokenDestinationItem = forgottenPasswordBean.getProgress().getTokenDestination();
            ForgottenPasswordUtil.initializeAndSendToken( pwmRequest.getPwmRequestContext(), userInfo, tokenDestinationItem );
        }

        final RestResultBean restResultBean = RestResultBean.forSuccessMessage( pwmRequest, Message.Success_TokenResend );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }


    @ActionHandler( action = "checkAttributes" )
    public ProcessStatus processCheckAttributes( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );

        if ( forgottenPasswordBean.isBogusUser() )
        {
            final FormConfiguration formConfiguration = forgottenPasswordBean.getAttributeForm().get( 0 );

            if ( forgottenPasswordBean.getUserSearchValues() != null )
            {
                final List<FormConfiguration> formConfigurations = pwmRequest.getDomainConfig().readSettingAsForm( PwmSetting.FORGOTTEN_PASSWORD_SEARCH_FORM );
                final Map<FormConfiguration, String> formMap = FormUtility.asFormConfigurationMap( formConfigurations, forgottenPasswordBean.getUserSearchValues() );
                IntruderServiceClient.markAttributes( pwmRequest, formMap );
            }

            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INCORRECT_RESPONSE,
                    "incorrect value for attribute '" + formConfiguration.getName() + "'", new String[]
                    {
                            formConfiguration.getLabel( pwmRequest.getLocale() ),
                    }
            );

            forgottenPasswordBean.getProgress().setInProgressVerificationMethod( IdentityVerificationMethod.ATTRIBUTES );
            setLastError( pwmRequest, errorInformation );
            return ProcessStatus.Continue;
        }

        if ( forgottenPasswordBean.getUserIdentity() == null )
        {
            return ProcessStatus.Continue;
        }
        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();

        try
        {
            // check attributes
            final ChaiUser theUser = pwmRequest.getPwmDomain().getProxiedChaiUser( pwmRequest.getLabel(), userIdentity );
            final Locale userLocale = pwmRequest.getLocale();

            final List<FormConfiguration> requiredAttributesForm = forgottenPasswordBean.getAttributeForm();

            if ( requiredAttributesForm.isEmpty() )
            {
                return ProcessStatus.Continue;
            }

            final Map<FormConfiguration, String> formValues = FormUtility.readFormValuesFromRequest(
                    pwmRequest, requiredAttributesForm, userLocale );

            for ( final Map.Entry<FormConfiguration, String> entry : formValues.entrySet() )
            {
                final FormConfiguration formConfiguration = entry.getKey();
                final String attrName = formConfiguration.getName();

                try
                {
                    if ( theUser.compareStringAttribute( attrName, entry.getValue() ) )
                    {
                        LOGGER.trace( pwmRequest, () -> "successful validation of ldap attribute value for '" + attrName + "'" );
                    }
                    else
                    {
                        throw new PwmDataValidationException( new ErrorInformation(
                                PwmError.ERROR_INCORRECT_RESPONSE, "incorrect value for '" + attrName + "'", new String[]
                                {
                                        formConfiguration.getLabel( pwmRequest.getLocale() ),
                                }
                        ) );
                    }
                }
                catch ( final ChaiOperationException e )
                {
                    LOGGER.error( pwmRequest, () -> "error during param validation of '" + attrName + "', error: " + e.getMessage() );
                    throw new PwmDataValidationException( new ErrorInformation(
                            PwmError.ERROR_INCORRECT_RESPONSE, "ldap error testing value for '" + attrName + "'", new String[]
                            {
                                    formConfiguration.getLabel( pwmRequest.getLocale() ),
                            }
                    ) );
                }
            }

            forgottenPasswordBean.getProgress().getSatisfiedMethods().add( IdentityVerificationMethod.ATTRIBUTES );
        }
        catch ( final PwmDataValidationException e )
        {
            handleUserVerificationBadAttempt( pwmRequest, forgottenPasswordBean, new ErrorInformation( PwmError.ERROR_INCORRECT_RESPONSE, e.getErrorInformation().toDebugStr() ) );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "agree" )
    public ProcessStatus processAgreeAction( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );

        LOGGER.debug( pwmRequest, () -> "user accepted forgotten password agreement" );
        if ( !forgottenPasswordBean.isAgreementPassed() )
        {
            forgottenPasswordBean.setAgreementPassed( true );
            final AuditRecord auditRecord = AuditRecordFactory.make( pwmRequest ).createUserAuditRecord(
                    AuditEvent.AGREEMENT_PASSED,
                    pwmRequest.getUserInfoIfLoggedIn(),
                    pwmRequest.getLabel(),
                    "ForgottenPassword"
            );
            AuditServiceClient.submit( pwmRequest, auditRecord );
        }

        return ProcessStatus.Continue;
    }

    @Override
    @SuppressWarnings( "checkstyle:MethodLength" )
    protected void nextStep( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final DomainConfig config = pwmRequest.getDomainConfig();
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );

        final ForgottenPasswordBean.RecoveryFlags recoveryFlags = forgottenPasswordBean.getRecoveryFlags();
        final ForgottenPasswordBean.Progress progress = forgottenPasswordBean.getProgress();

        // check for identified user;
        if ( forgottenPasswordBean.getUserIdentity() == null && !forgottenPasswordBean.isBogusUser() )
        {
            pwmRequest.addFormInfoToRequestAttr( PwmSetting.FORGOTTEN_PASSWORD_SEARCH_FORM, false, false );
            pwmRequest.forwardToJsp( JspUrl.RECOVER_PASSWORD_SEARCH );
            return;
        }

        final ForgottenPasswordProfile forgottenPasswordProfile = ForgottenPasswordUtil.forgottenPasswordProfile( pwmRequest.getPwmDomain(), forgottenPasswordBean );
        {
            final Map<String, ForgottenPasswordProfile> profiles = pwmRequest.getDomainConfig().getForgottenPasswordProfiles();
            final String profileDebugMsg = forgottenPasswordProfile != null && profiles != null && profiles.size() > 1
                    ? " profile=" + forgottenPasswordProfile.getIdentifier() + ", "
                    : "";
            LOGGER.trace( pwmRequest, () -> "entering forgotten password progress engine: "
                    + profileDebugMsg
                    + "flags=" + JsonFactory.get().serialize( recoveryFlags ) + ", "
                    + "progress=" + JsonFactory.get().serialize( progress ) );
        }

        if ( forgottenPasswordProfile == null )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_NO_PROFILE_ASSIGNED ) );
        }

        // check for previous authentication
        if (
                recoveryFlags.getRequiredAuthMethods().contains( IdentityVerificationMethod.PREVIOUS_AUTH )
                        || recoveryFlags.getOptionalAuthMethods().contains( IdentityVerificationMethod.PREVIOUS_AUTH )
                )
        {
            if ( !progress.getSatisfiedMethods().contains( IdentityVerificationMethod.PREVIOUS_AUTH ) )
            {
                final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
                final String userGuid = LdapOperationsHelper.readLdapGuidValue( pwmDomain, pwmRequest.getLabel(), userIdentity, true );
                if ( ForgottenPasswordUtil.checkAuthRecord( pwmRequest, userGuid ) )
                {
                    LOGGER.debug( pwmRequest, () -> "marking " + IdentityVerificationMethod.PREVIOUS_AUTH + " method as satisfied" );
                    progress.getSatisfiedMethods().add( IdentityVerificationMethod.PREVIOUS_AUTH );
                }
            }
        }

        final String agreementMsg = forgottenPasswordProfile.readSettingAsLocalizedString( PwmSetting.RECOVERY_AGREEMENT_MESSAGE, pwmRequest.getLocale() );
        if ( StringUtil.notEmpty( agreementMsg ) && !forgottenPasswordBean.isAgreementPassed() )
        {
            final MacroRequest macroRequest = pwmRequest.getMacroMachine();
            final String expandedText = macroRequest.expandMacros( agreementMsg );
            pwmRequest.setAttribute( PwmRequestAttribute.AgreementText, expandedText );
            pwmRequest.forwardToJsp( JspUrl.RECOVER_USER_AGREEMENT );
            return;
        }

        // dispatch required auth methods.
        for ( final IdentityVerificationMethod method : recoveryFlags.getRequiredAuthMethods() )
        {
            if ( !progress.getSatisfiedMethods().contains( method ) )
            {
                forwardUserBasedOnRecoveryMethod( pwmRequest, method );
                return;
            }
        }

        // redirect if an verification method is in progress
        if ( progress.getInProgressVerificationMethod() != null )
        {
            if ( progress.getSatisfiedMethods().contains( progress.getInProgressVerificationMethod() ) )
            {
                progress.setInProgressVerificationMethod( null );
            }
            else
            {
                pwmRequest.setAttribute( PwmRequestAttribute.ForgottenPasswordOptionalPageView, "true" );
                forwardUserBasedOnRecoveryMethod( pwmRequest, progress.getInProgressVerificationMethod() );
                return;
            }
        }

        // check if more optional methods required
        if ( recoveryFlags.getMinimumOptionalAuthMethods() > 0 )
        {
            final Set<IdentityVerificationMethod> satisfiedOptionalMethods = ForgottenPasswordUtil.figureSatisfiedOptionalAuthMethods( recoveryFlags, progress );
            if ( satisfiedOptionalMethods.size() < recoveryFlags.getMinimumOptionalAuthMethods() )
            {
                final Set<IdentityVerificationMethod> remainingAvailableOptionalMethods = ForgottenPasswordUtil.figureRemainingAvailableOptionalAuthMethods(
                        pwmRequest.getPwmRequestContext(),
                        forgottenPasswordBean
                );
                if ( remainingAvailableOptionalMethods.isEmpty() )
                {
                    final String errorMsg = "additional optional verification methods are needed, however all available optional verification methods have been satisfied by user";
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_RECOVERY_SEQUENCE_INCOMPLETE, errorMsg );
                    LOGGER.error( pwmRequest, errorInformation );
                    pwmRequest.respondWithError( errorInformation );
                    return;
                }
                else
                {
                    if ( remainingAvailableOptionalMethods.size() == 1 )
                    {
                        final IdentityVerificationMethod remainingMethod = remainingAvailableOptionalMethods.iterator().next();
                        LOGGER.debug( pwmRequest, () -> "only 1 remaining available optional verification method, will redirect to " + remainingMethod.toString() );
                        forwardUserBasedOnRecoveryMethod( pwmRequest, remainingMethod );
                        progress.setInProgressVerificationMethod( remainingMethod );
                        return;
                    }
                }
                processVerificationChoice( pwmRequest );
                return;
            }
        }

        if ( progress.getSatisfiedMethods().isEmpty() )
        {
            final String errorMsg = "forgotten password recovery sequence completed, but user has not actually satisfied any verification methods";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_RECOVERY_SEQUENCE_INCOMPLETE, errorMsg );
            LOGGER.error( pwmRequest, errorInformation );
            clearForgottenPasswordBean( pwmRequest );
            throw new PwmUnrecoverableException( errorInformation );
        }

        {
            final int satisfiedMethods = progress.getSatisfiedMethods().size();
            final int totalMethodsNeeded = recoveryFlags.getRequiredAuthMethods().size() + recoveryFlags.getMinimumOptionalAuthMethods();
            if ( satisfiedMethods < totalMethodsNeeded )
            {
                final String errorMsg = "forgotten password recovery sequence completed " + satisfiedMethods + " methods, "
                        + " but policy requires a total of " + totalMethodsNeeded + " methods";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_RECOVERY_SEQUENCE_INCOMPLETE, errorMsg );
                LOGGER.error( pwmRequest, errorInformation );
                clearForgottenPasswordBean( pwmRequest );
                throw new PwmUnrecoverableException( errorInformation );
            }
        }

        if ( !forgottenPasswordBean.getProgress().isAllPassed() )
        {
            forgottenPasswordBean.getProgress().setAllPassed( true );
            StatisticsClient.incrementStat( pwmRequest, Statistic.RECOVERY_SUCCESSES );
        }

        final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequest.getPwmRequestContext(), forgottenPasswordBean )
                .orElseThrow( () -> PwmUnrecoverableException.newException( PwmError.ERROR_RECOVERY_SEQUENCE_INCOMPLETE, "controller execution without userInfo present" ) );

        // check if user's pw is within min lifetime window
        final RecoveryMinLifetimeOption minLifetimeOption = forgottenPasswordProfile.readSettingAsEnum(
                PwmSetting.RECOVERY_MINIMUM_PASSWORD_LIFETIME_OPTIONS,
                RecoveryMinLifetimeOption.class
        );
        if ( minLifetimeOption == RecoveryMinLifetimeOption.NONE
                || (
                !userInfo.isPasswordLocked()
                        &&  minLifetimeOption == RecoveryMinLifetimeOption.UNLOCKONLY )
                )
        {
            if ( userInfo.isWithinPasswordMinimumLifetime() )
            {
                PasswordUtility.throwPasswordTooSoonException( userInfo, pwmRequest.getLabel() );
            }
        }

        final boolean disallowAllButUnlock = minLifetimeOption == RecoveryMinLifetimeOption.UNLOCKONLY
                && userInfo.isPasswordLocked();

        LOGGER.trace( pwmRequest, () -> "all recovery checks passed, proceeding to configured recovery action" );

        final RecoveryAction recoveryAction = ForgottenPasswordUtil.getRecoveryAction( config, forgottenPasswordBean );
        if ( recoveryAction == RecoveryAction.SENDNEWPW || recoveryAction == RecoveryAction.SENDNEWPW_AND_EXPIRE )
        {
            if ( disallowAllButUnlock )
            {
                PasswordUtility.throwPasswordTooSoonException( userInfo, pwmRequest.getLabel() );
            }
            ForgottenPasswordUtil.doActionSendNewPassword( pwmRequest );
            return;
        }

        if ( forgottenPasswordProfile.readSettingAsBoolean( PwmSetting.RECOVERY_ALLOW_UNLOCK ) )
        {
            final PasswordStatus passwordStatus = userInfo.getPasswordStatus();

            if ( !passwordStatus.isExpired() && !passwordStatus.isPreExpired() )
            {
                if ( userInfo.isPasswordLocked() )
                {
                    final boolean inhibitReset = minLifetimeOption != RecoveryMinLifetimeOption.ALLOW
                            && userInfo.isWithinPasswordMinimumLifetime();

                    pwmRequest.setAttribute( PwmRequestAttribute.ForgottenPasswordInhibitPasswordReset, inhibitReset );
                    pwmRequest.forwardToJsp( JspUrl.RECOVER_PASSWORD_ACTION_CHOICE );
                    return;
                }
            }
        }

        this.executeResetPassword( pwmRequest );
    }


    private void executeUnlock( final PwmRequest pwmRequest )
            throws IOException, ServletException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();

        try
        {
            final ChaiUser theUser = pwmDomain.getProxiedChaiUser( pwmRequest.getLabel(), userIdentity );
            theUser.unlockPassword();

            // mark the event log
            final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequest.getPwmRequestContext(), forgottenPasswordBean )
                    .orElseThrow( () -> PwmUnrecoverableException.newException( PwmError.ERROR_RECOVERY_SEQUENCE_INCOMPLETE, "executeUnlock without userInfo present" ) );
            AuditServiceClient.submitUserEvent( pwmRequest, AuditEvent.UNLOCK_PASSWORD, userInfo );

            ForgottenPasswordUtil.sendUnlockNoticeEmail( pwmRequest.getPwmRequestContext(), forgottenPasswordBean );

            pwmRequest.getPwmResponse().forwardToSuccessPage( Message.Success_UnlockAccount );
        }
        catch ( final ChaiOperationException e )
        {
            final String errorMsg = "unable to unlock user " + userIdentity + " error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNLOCK_FAILURE, errorMsg );
            LOGGER.error( pwmRequest, errorInformation::toDebugStr );
            pwmRequest.respondWithError( errorInformation, true );
        }
        finally
        {
            clearForgottenPasswordBean( pwmRequest );
        }
    }


    private void executeResetPassword( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );

        if ( !forgottenPasswordBean.getProgress().isAllPassed() )
        {
            return;
        }

        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
        final ChaiUser theUser = pwmDomain.getProxiedChaiUser( pwmRequest.getLabel(), userIdentity );

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
        }

        try
        {
            final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(
                    pwmDomain,
                    pwmRequest,
                    PwmAuthenticationSource.FORGOTTEN_PASSWORD
            );
            sessionAuthenticator.authUserWithUnknownPassword( userIdentity, AuthenticationType.AUTH_FROM_PUBLIC_MODULE );
            pwmSession.getLoginInfoBean().getAuthFlags().add( AuthenticationType.AUTH_FROM_PUBLIC_MODULE );

            LOGGER.info( pwmRequest, () -> "user successfully supplied password recovery responses, forward to change password page: " + theUser.getEntryDN() );

            // mark the event log
            AuditServiceClient.submitUserEvent( pwmRequest, AuditEvent.RECOVER_PASSWORD, pwmSession.getUserInfo() );

            // mark user as requiring a new password.
            pwmSession.getLoginInfoBean().getLoginFlags().add( LoginInfoBean.LoginFlag.forcePwChange );

            // redirect user to change password screen.
            pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.PublicChangePassword );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.warn( pwmRequest,
                    () -> "unexpected error authenticating during forgotten password recovery process user: " + e.getMessage() );
            pwmRequest.respondWithError( e.getErrorInformation() );
        }
        finally
        {
            clearForgottenPasswordBean( pwmRequest );
        }
    }

    private void handleUserVerificationBadAttempt(
            final PwmRequest pwmRequest,
            final ForgottenPasswordBean forgottenPasswordBean,
            final ErrorInformation errorInformation
    )
            throws PwmUnrecoverableException
    {
        LOGGER.debug( pwmRequest, errorInformation );
        setLastError( pwmRequest, errorInformation );

        final UserIdentity userIdentity = forgottenPasswordBean == null
                ? null
                : forgottenPasswordBean.getUserIdentity();

        if ( userIdentity != null )
        {
            final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(
                    pwmRequest.getPwmDomain(),
                    pwmRequest,
                    PwmAuthenticationSource.FORGOTTEN_PASSWORD
            );
            sessionAuthenticator.simulateBadPassword( userIdentity );
            IntruderServiceClient.markUserIdentity( pwmRequest, userIdentity );
        }

        IntruderServiceClient.markAddressAndSession( pwmRequest.getPwmDomain(), pwmRequest.getPwmSession() );

        StatisticsClient.incrementStat( pwmRequest, Statistic.RECOVERY_FAILURES );
    }

    private void checkForLocaleSwitch( final PwmRequest pwmRequest, final ForgottenPasswordBean forgottenPasswordBean )
            throws PwmUnrecoverableException
    {
        if ( forgottenPasswordBean.getUserIdentity() == null || forgottenPasswordBean.getUserLocale() == null )
        {
            return;
        }

        if ( forgottenPasswordBean.getUserLocale().equals( pwmRequest.getLocale() ) )
        {
            return;
        }

        LOGGER.debug( pwmRequest, () -> "user initiated forgotten password recovery using '"
                + forgottenPasswordBean.getUserLocale() + "' locale, but current request locale is now '"
                + pwmRequest.getLocale()
                + "', thus, the user progress will be restart and user data will be re-read using current locale" );

        try
        {
            ForgottenPasswordUtil.initForgottenPasswordBean(
                    pwmRequest.getPwmRequestContext(),
                    forgottenPasswordBean.getUserIdentity(),
                    forgottenPasswordBean
            );
        }
        catch ( final PwmOperationalException e )
        {
            clearForgottenPasswordBean( pwmRequest );
            final ErrorInformation errorInformation = new ErrorInformation(
                    PwmError.ERROR_INTERNAL,
                    "unexpected error while re-loading user data due to locale change: " + e.getErrorInformation().toDebugStr()
            );
            LOGGER.error( pwmRequest, errorInformation::toDebugStr );
            setLastError( pwmRequest, errorInformation );
        }
    }

    private void forwardUserBasedOnRecoveryMethod(
            final PwmRequest pwmRequest,
            final IdentityVerificationMethod method
    )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        LOGGER.debug( pwmRequest, () -> "attempting to forward request to handle verification method " + method.toString() );
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        ForgottenPasswordUtil.verifyRequirementsForAuthMethod( pwmRequest.getPwmRequestContext(), forgottenPasswordBean, method );
        switch ( method )
        {
            case PREVIOUS_AUTH:
            {
                throw new PwmUnrecoverableException( new ErrorInformation(
                        PwmError.ERROR_INTERNAL,
                        "previous authentication is required, but user has not previously authenticated" )
                );
            }

            case ATTRIBUTES:
            {
                pwmRequest.addFormInfoToRequestAttr( forgottenPasswordBean.getAttributeForm(), Collections.emptyMap(), false, false );
                pwmRequest.forwardToJsp( JspUrl.RECOVER_PASSWORD_ATTRIBUTES );
            }
            break;

            case CHALLENGE_RESPONSES:
            {
                pwmRequest.setAttribute( PwmRequestAttribute.ForgottenPasswordChallengeSet, forgottenPasswordBean.getPresentableChallengeSet() );
                pwmRequest.forwardToJsp( JspUrl.RECOVER_PASSWORD_RESPONSES );
            }
            break;

            case OTP:
            {
                final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequest.getPwmRequestContext(), forgottenPasswordBean )
                        .orElseThrow( () -> PwmUnrecoverableException.newException( PwmError.ERROR_RECOVERY_SEQUENCE_INCOMPLETE, "dispatch otp method without userInfo present" ) );
                pwmRequest.setAttribute(
                        PwmRequestAttribute.ForgottenPasswordOtpRecord,
                        userInfo.getOtpUserRecord()
                );
                pwmRequest.forwardToJsp( JspUrl.RECOVER_PASSWORD_ENTER_OTP );
            }
            break;

            case TOKEN:
            {
                final ForgottenPasswordBean.Progress progress = forgottenPasswordBean.getProgress();
                final List<TokenDestinationItem> tokenDestinations =
                        ForgottenPasswordUtil.figureAvailableTokenDestinations( pwmRequest.getPwmRequestContext(), forgottenPasswordBean );

                if ( progress.getTokenDestination() == null )
                {
                    final boolean autoSelect = Boolean.parseBoolean( pwmRequest.getDomainConfig().readAppProperty( AppProperty.FORGOTTEN_PASSWORD_TOKEN_AUTO_SELECT_DEST ) );
                    if ( autoSelect && tokenDestinations.size() == 1 )
                    {
                        final TokenDestinationItem singleItem = tokenDestinations.get( 0 );
                        progress.setTokenDestination( singleItem );
                    }
                }

                if ( progress.getTokenDestination() == null )
                {
                    forwardToTokenChoiceJsp( pwmRequest );
                    return;
                }

                if ( !progress.isTokenSent() )
                {
                    final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequest.getPwmRequestContext(), forgottenPasswordBean )
                            .orElseThrow( () -> PwmUnrecoverableException.newException(
                                    PwmError.ERROR_RECOVERY_SEQUENCE_INCOMPLETE, "dispatch token method without userInfo present" ) );
                    ForgottenPasswordUtil.initializeAndSendToken( pwmRequest.getPwmRequestContext(), userInfo, progress.getTokenDestination() );
                    progress.setTokenSent( true );
                }

                if ( !progress.getSatisfiedMethods().contains( IdentityVerificationMethod.TOKEN ) )
                {
                    forwardToEnterTokenJsp( pwmRequest );
                    return;
                }
            }
            break;

            case REMOTE_RESPONSES:
            {
                final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequest.getPwmRequestContext(), forgottenPasswordBean )
                        .orElseThrow( () -> PwmUnrecoverableException.newException(
                                PwmError.ERROR_RECOVERY_SEQUENCE_INCOMPLETE, "dispatch remote responses method without userInfo present" ) );
                final VerificationMethodSystem remoteMethod;
                if ( forgottenPasswordBean.getProgress().getRemoteRecoveryMethod() == null )
                {
                    remoteMethod = new RemoteVerificationMethod();
                    remoteMethod.init(
                            pwmRequest.getPwmDomain(),
                            userInfo,
                            pwmRequest.getLabel(),
                            pwmRequest.getLocale()
                    );
                    forgottenPasswordBean.getProgress().setRemoteRecoveryMethod( remoteMethod );
                }
                else
                {
                    remoteMethod = forgottenPasswordBean.getProgress().getRemoteRecoveryMethod();
                }

                final List<VerificationMethodSystem.UserPrompt> prompts = remoteMethod.getCurrentPrompts();
                final String displayInstructions = remoteMethod.getCurrentDisplayInstructions();

                pwmRequest.setAttribute( PwmRequestAttribute.ExternalResponsePrompts, new ArrayList<>( prompts ) );
                pwmRequest.setAttribute( PwmRequestAttribute.ExternalResponseInstructions, displayInstructions );
                pwmRequest.forwardToJsp( JspUrl.RECOVER_PASSWORD_REMOTE );
            }
            break;

            case OAUTH:
                forgottenPasswordBean.getProgress().setInProgressVerificationMethod( IdentityVerificationMethod.OAUTH );
                final ForgottenPasswordProfile forgottenPasswordProfile = ForgottenPasswordUtil.forgottenPasswordProfile( pwmRequest.getPwmDomain(), forgottenPasswordBean );
                final OAuthSettings oAuthSettings = OAuthSettings.forForgottenPassword( forgottenPasswordProfile );
                final OAuthMachine oAuthMachine = new OAuthMachine( pwmRequest.getLabel(), oAuthSettings );
                pwmRequest.getPwmDomain().getSessionStateService().saveSessionBeans( pwmRequest );
                final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
                oAuthMachine.redirectUserToOAuthServer( pwmRequest, null, userIdentity, forgottenPasswordProfile.getIdentifier() );
                break;


            default:
                throw new UnsupportedOperationException( "unexpected method during forward: " + method );
        }

    }

    private static void forwardToTokenChoiceJsp( final PwmRequest pwmRequest )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        final List<TokenDestinationItem> destItems = ForgottenPasswordUtil.figureAvailableTokenDestinations( pwmRequest.getPwmRequestContext(), forgottenPasswordBean );
        pwmRequest.setAttribute( PwmRequestAttribute.TokenDestItems, new ArrayList<>( destItems ) );

        if ( ForgottenPasswordUtil.hasOtherMethodChoices( pwmRequest.getPwmRequestContext(), forgottenPasswordBean, IdentityVerificationMethod.TOKEN ) )
        {
            pwmRequest.setAttribute( PwmRequestAttribute.GoBackAction, ResetAction.clearActionChoice.name() );
        }

        pwmRequest.forwardToJsp( JspUrl.RECOVER_PASSWORD_TOKEN_CHOICE );
    }

    private static void forwardToEnterTokenJsp( final PwmRequest pwmRequest )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        final ForgottenPasswordProfile forgottenPasswordProfile = ForgottenPasswordUtil.forgottenPasswordProfile(
                pwmRequest.getPwmDomain(),
                forgottenPasswordBean
        );

        final List<TokenDestinationItem> destItems = ForgottenPasswordUtil.figureAvailableTokenDestinations( pwmRequest.getPwmRequestContext(), forgottenPasswordBean );

        ResetAction goBackAction = null;

        final boolean autoSelect = Boolean.parseBoolean( pwmRequest.getDomainConfig().readAppProperty( AppProperty.FORGOTTEN_PASSWORD_TOKEN_AUTO_SELECT_DEST ) );
        if ( destItems.size() > 1 || !autoSelect )
        {
            goBackAction = ResetAction.clearTokenDestination;
        }
        else if ( ForgottenPasswordUtil.hasOtherMethodChoices( pwmRequest.getPwmRequestContext(), forgottenPasswordBean, IdentityVerificationMethod.TOKEN ) )
        {
            goBackAction = ResetAction.clearActionChoice;
        }

        if ( goBackAction != null )
        {
            pwmRequest.setAttribute( PwmRequestAttribute.GoBackAction, goBackAction );
        }

        {
            final boolean resendEnabled = forgottenPasswordProfile.readSettingAsBoolean( PwmSetting.TOKEN_RESEND_ENABLE );
            pwmRequest.setAttribute( PwmRequestAttribute.ForgottenPasswordResendTokenEnabled, resendEnabled );
        }
        pwmRequest.forwardToJsp( JspUrl.RECOVER_PASSWORD_ENTER_TOKEN );
    }
}




