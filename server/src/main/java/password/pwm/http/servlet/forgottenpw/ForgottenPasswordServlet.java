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
import com.novell.ldapchai.cr.Challenge;
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
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
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
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.svc.token.TokenPayload;
import password.pwm.svc.token.TokenService;
import password.pwm.svc.token.TokenType;
import password.pwm.svc.token.TokenUtil;
import password.pwm.util.CaptchaUtility;
import password.pwm.util.form.FormUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.password.PasswordUtility;
import password.pwm.util.operations.cr.NMASCrOperator;
import password.pwm.util.operations.otp.OTPUserRecord;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
        resendToken( HttpMethod.POST ),;

        private final Collection<HttpMethod> method;

        ForgottenPasswordAction( final HttpMethod... method )
        {
            this.method = Collections.unmodifiableList( Arrays.asList( method ) );
        }

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
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        final Configuration config = pwmApplication.getConfig();

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
            pwmApplication.getIntruderManager().convenience().checkUserIdentity( forgottenPasswordBean.getUserIdentity() );
        }

        checkForLocaleSwitch( pwmRequest, forgottenPasswordBean );

        final ProcessAction action = this.readProcessAction( pwmRequest );

        // convert a url command like /public/newuser/12321321 to redirect with a process action.
        if ( action == null )
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
        return pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, ForgottenPasswordBean.class );
    }

    static void clearForgottenPasswordBean( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        pwmRequest.getPwmApplication().getSessionStateService().clearBean( pwmRequest, ForgottenPasswordBean.class );
    }

    @ActionHandler( action = "actionChoice" )
    private ProcessStatus processActionChoice( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, ServletException, IOException, ChaiUnavailableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        final ForgottenPasswordProfile forgottenPasswordProfile = ForgottenPasswordUtil.forgottenPasswordProfile( pwmRequest.getPwmApplication(), forgottenPasswordBean );

        final boolean resendEnabled = forgottenPasswordProfile.readSettingAsBoolean( PwmSetting.TOKEN_RESEND_ENABLE );

        if ( resendEnabled )
        {
            // clear token dest info in case we got here from a user 'go-back' request
            forgottenPasswordBean.getProgress().clearTokenSentStatus();
        }


        final boolean disallowAllButUnlock;
        {
            final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequest.commonValues(), forgottenPasswordBean );
            final RecoveryMinLifetimeOption minLifetimeOption = forgottenPasswordProfile.readSettingAsEnum(
                    PwmSetting.RECOVERY_MINIMUM_PASSWORD_LIFETIME_OPTIONS,
                    RecoveryMinLifetimeOption.class
            );
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
                            final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequest.commonValues(), forgottenPasswordBean );
                            PasswordUtility.throwPasswordTooSoonException( userInfo, pwmRequest.getLabel() );
                        }
                        this.executeResetPassword( pwmRequest );
                        break;

                    default:
                        JavaHelper.unhandledSwitchStatement( actionChoice );
                }
            }
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "reset" )
    private ProcessStatus processReset( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final ResetAction resetType = pwmRequest.readParameterAsEnum( PwmConstants.PARAM_RESET_TYPE, ResetAction.class, ResetAction.exitForgottenPassword );

        switch ( resetType )
        {
            case exitForgottenPassword:
                clearForgottenPasswordBean( pwmRequest );
                pwmRequest.sendRedirectToContinue();
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
                JavaHelper.unhandledSwitchStatement( resetType );

        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "tokenChoice" )
    private ProcessStatus processTokenChoice( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        final List<TokenDestinationItem> items = ForgottenPasswordUtil.figureAvailableTokenDestinations( pwmRequest.commonValues(), forgottenPasswordBean );

        final String requestedID = pwmRequest.readParameterAsString( "choice", PwmHttpRequestWrapper.Flag.BypassValidation );

        final Optional<TokenDestinationItem> tokenDestinationItem = TokenDestinationItem.tokenDestinationItemForID( items, requestedID );
        if ( tokenDestinationItem.isPresent() )
        {
            forgottenPasswordBean.getProgress().setTokenDestination( tokenDestinationItem.get() );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "verificationChoice" )
    private ProcessStatus processVerificationChoice( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, ServletException, IOException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        final String requestedChoiceStr = pwmRequest.readParameterAsString( PwmConstants.PARAM_METHOD_CHOICE );
        final LinkedHashSet<IdentityVerificationMethod> remainingAvailableOptionalMethods = new LinkedHashSet<>(
                ForgottenPasswordUtil.figureRemainingAvailableOptionalAuthMethods( pwmRequest.commonValues(), forgottenPasswordBean )
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
    private ProcessStatus processSearch( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final Locale userLocale = pwmRequest.getLocale();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        final String contextParam = pwmRequest.readParameterAsString( PwmConstants.PARAM_CONTEXT );
        final String ldapProfile = pwmRequest.readParameterAsString( PwmConstants.PARAM_LDAP_PROFILE );

        final boolean bogusUserModeEnabled = pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.RECOVERY_BOGUS_USER_ENABLE );

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

        final List<FormConfiguration> forgottenPasswordForm = pwmApplication.getConfig().readSettingAsForm(
                PwmSetting.FORGOTTEN_PASSWORD_SEARCH_FORM );

        Map<FormConfiguration, String> formValues = new LinkedHashMap<>();

        try
        {
            //read the values from the request
            formValues = FormUtility.readFormValuesFromRequest( pwmRequest, forgottenPasswordForm, userLocale );

            // check for intruder search values
            pwmApplication.getIntruderManager().convenience().checkAttributes( formValues );

            // see if the values meet the configured form requirements.
            FormUtility.validateFormValues( pwmRequest.getConfig(), formValues, userLocale );

            final String searchFilter;
            {
                final String configuredSearchFilter = pwmApplication.getConfig().readSettingAsString( PwmSetting.FORGOTTEN_PASSWORD_SEARCH_FILTER );
                if ( configuredSearchFilter == null || configuredSearchFilter.isEmpty() )
                {
                    searchFilter = FormUtility.ldapSearchFilterForForm( pwmApplication, forgottenPasswordForm );
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
                final UserSearchEngine userSearchEngine = pwmApplication.getUserSearchEngine();
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

            AuthenticationUtility.checkIfUserEligibleToAuthentication( pwmApplication, userIdentity );

            final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
            ForgottenPasswordUtil.initForgottenPasswordBean( pwmRequest.commonValues(), userIdentity, forgottenPasswordBean );

            // clear intruder search values
            pwmApplication.getIntruderManager().convenience().clearAttributes( formValues );

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
                pwmApplication.getStatisticsManager().incrementValue( Statistic.RECOVERY_FAILURES );

                pwmApplication.getIntruderManager().convenience().markAddressAndSession( pwmRequest );
                pwmApplication.getIntruderManager().convenience().markAttributes( formValues, pwmRequest.getLabel() );

                LOGGER.debug( pwmRequest, errorInfo );
                setLastError( pwmRequest, errorInfo );
                return ProcessStatus.Continue;
            }
        }

        if ( bogusUserModeEnabled )
        {
            ForgottenPasswordUtil.initBogusForgottenPasswordBean( pwmRequest.commonValues(), forgottenPasswordBean( pwmRequest ) );
            forgottenPasswordBean( pwmRequest ).setUserSearchValues( FormUtility.asStringMap( formValues ) );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "enterCode" )
    private ProcessStatus processEnterCode( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        final String userEnteredCode = pwmRequest.readParameterAsString( PwmConstants.PARAM_TOKEN );

        ErrorInformation errorInformation = null;
        try
        {
            final TokenPayload tokenPayload = TokenUtil.checkEnteredCode(
                    pwmRequest.commonValues(),
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
                        pwmRequest.commonValues(),
                        tokenPayload.getUserIdentity(),
                        forgottenPasswordBean
                );
            }
            forgottenPasswordBean.getProgress().getSatisfiedMethods().add( IdentityVerificationMethod.TOKEN );
            StatisticsManager.incrementStat( pwmRequest.getPwmApplication(), Statistic.RECOVERY_TOKENS_PASSED );

            if ( pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.DISPLAY_TOKEN_SUCCESS_BUTTON ) )
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
    private ProcessStatus processEnterRemoteResponse( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final String prefix = "remote-";
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        final VerificationMethodSystem remoteRecoveryMethod = forgottenPasswordBean.getProgress().getRemoteRecoveryMethod();

        final Map<String, String> remoteResponses = new LinkedHashMap<>();
        {
            final Map<String, String> inputMap = pwmRequest.readParametersAsMap();
            for ( final Map.Entry<String, String> entry : inputMap.entrySet() )
            {
                final String name = entry.getKey();
                if ( name != null && name.startsWith( prefix ) )
                {
                    final String strippedName = name.substring( prefix.length(), name.length() );
                    final String value = entry.getValue();
                    remoteResponses.put( strippedName, value );
                }
            }
        }

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
    private ProcessStatus processEnterOtpToken( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        final String userEnteredCode = pwmRequest.readParameterAsString( PwmConstants.PARAM_TOKEN );
        LOGGER.debug( pwmRequest, () -> String.format( "entered OTP: %s", userEnteredCode ) );

        final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequest.commonValues(), forgottenPasswordBean );
        final OTPUserRecord otpUserRecord = userInfo.getOtpUserRecord();

        final boolean otpPassed;
        if ( otpUserRecord != null )
        {
            LOGGER.info( pwmRequest, () -> "checking entered OTP for user " + userInfo.getUserIdentity().toDisplayString() );
            try
            {
                // forces service to use proxy account to update (write) updated otp record if necessary.
                otpPassed = pwmRequest.getPwmApplication().getOtpService().validateToken(
                        null,
                        forgottenPasswordBean.getUserIdentity(),
                        otpUserRecord,
                        userEnteredCode,
                        true
                );

                if ( otpPassed )
                {
                    StatisticsManager.incrementStat( pwmRequest, Statistic.RECOVERY_OTP_PASSED );
                    LOGGER.debug( pwmRequest, () -> "one time password validation has been passed" );
                    forgottenPasswordBean.getProgress().getSatisfiedMethods().add( IdentityVerificationMethod.OTP );
                }
                else
                {
                    StatisticsManager.incrementStat( pwmRequest, Statistic.RECOVERY_OTP_FAILED );
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
    private ProcessStatus processOAuthReturn( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        if ( forgottenPasswordBean.getProgress().getInProgressVerificationMethod() != IdentityVerificationMethod.OAUTH )
        {
            LOGGER.debug( pwmRequest, () -> "oauth return detected, however current session did not issue an oauth request; will restart forgotten password sequence" );
            pwmRequest.getPwmApplication().getSessionStateService().clearBean( pwmRequest, ForgottenPasswordBean.class );
            pwmRequest.sendRedirect( PwmServletDefinition.ForgottenPassword );
            return ProcessStatus.Halt;
        }

        if ( forgottenPasswordBean.getUserIdentity() == null )
        {
            LOGGER.debug( pwmRequest, () -> "oauth return detected, however current session does not have a user identity stored; will restart forgotten password sequence" );
            pwmRequest.getPwmApplication().getSessionStateService().clearBean( pwmRequest, ForgottenPasswordBean.class );
            pwmRequest.sendRedirect( PwmServletDefinition.ForgottenPassword );
            return ProcessStatus.Halt;
        }

        final String encryptedResult = pwmRequest.readParameterAsString( PwmConstants.PARAM_RECOVERY_OAUTH_RESULT, PwmHttpRequestWrapper.Flag.BypassValidation );
        final OAuthForgottenPasswordResults results = pwmRequest.getPwmApplication().getSecureService().decryptObject( encryptedResult, OAuthForgottenPasswordResults.class );
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
            final UserSearchEngine userSearchEngine = pwmRequest.getPwmApplication().getUserSearchEngine();
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
    private ProcessStatus processCheckResponses( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );

        if ( forgottenPasswordBean.getUserIdentity() == null )
        {
            return ProcessStatus.Continue;
        }
        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();

        final ResponseSet responseSet = ForgottenPasswordUtil.readResponseSet( pwmRequest.commonValues(), forgottenPasswordBean );
        if ( responseSet == null )
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
                responsesPassed = responseSet.test( crMap );
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
            if ( !responsesPassed && responseSet instanceof NMASCrOperator.NMASCRResponseSet )
            {
                forgottenPasswordBean.setPresentableChallengeSet( responseSet.getPresentableChallengeSet().asChallengeSetBean() );
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
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.forChaiError( e.getErrorCode() ) );
            handleUserVerificationBadAttempt( pwmRequest, forgottenPasswordBean, errorInformation );
            return ProcessStatus.Continue;
        }

        forgottenPasswordBean.getProgress().getSatisfiedMethods().add( IdentityVerificationMethod.CHALLENGE_RESPONSES );

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "resendToken" )
    private ProcessStatus processResendToken( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );

        {
            final ForgottenPasswordProfile forgottenPasswordProfile = ForgottenPasswordUtil.forgottenPasswordProfile( pwmRequest.getPwmApplication(), forgottenPasswordBean );
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
            final long delayTimeMs = Long.parseLong( pwmRequest.getConfig().readAppProperty( AppProperty.TOKEN_RESEND_DELAY_MS ) );
            TimeDuration.of( delayTimeMs, TimeDuration.Unit.MILLISECONDS ).pause();
        }

        {
            final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequest.commonValues(), forgottenPasswordBean );
            final TokenDestinationItem tokenDestinationItem = forgottenPasswordBean.getProgress().getTokenDestination();
            ForgottenPasswordUtil.initializeAndSendToken( pwmRequest.commonValues(), userInfo, tokenDestinationItem );
        }

        final RestResultBean restResultBean = RestResultBean.forSuccessMessage( pwmRequest, Message.Success_TokenResend );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }


    @ActionHandler( action = "checkAttributes" )
    private ProcessStatus processCheckAttributes( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        //final SessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );

        if ( forgottenPasswordBean.isBogusUser() )
        {
            final FormConfiguration formConfiguration = forgottenPasswordBean.getAttributeForm().iterator().next();

            if ( forgottenPasswordBean.getUserSearchValues() != null )
            {
                final List<FormConfiguration> formConfigurations = pwmRequest.getConfig().readSettingAsForm( PwmSetting.FORGOTTEN_PASSWORD_SEARCH_FORM );
                final Map<FormConfiguration, String> formMap = FormUtility.asFormConfigurationMap( formConfigurations, forgottenPasswordBean.getUserSearchValues() );
                pwmRequest.getPwmApplication().getIntruderManager().convenience().markAttributes( formMap, pwmRequest.getLabel() );
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
            final ChaiUser theUser = pwmRequest.getPwmApplication().getProxiedChaiUser( userIdentity );
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
                        throw new PwmDataValidationException( new ErrorInformation( PwmError.ERROR_INCORRECT_RESPONSE, "incorrect value for '" + attrName + "'", new String[]
                                {
                                        formConfiguration.getLabel( pwmRequest.getLocale() ),
                                }
                        ) );
                    }
                }
                catch ( final ChaiOperationException e )
                {
                    LOGGER.error( pwmRequest, () -> "error during param validation of '" + attrName + "', error: " + e.getMessage() );
                    throw new PwmDataValidationException( new ErrorInformation( PwmError.ERROR_INCORRECT_RESPONSE, "ldap error testing value for '" + attrName + "'", new String[]
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


    @Override
    @SuppressWarnings( "checkstyle:MethodLength" )
    protected void nextStep( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmRequest.getConfig();
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

        final ForgottenPasswordProfile forgottenPasswordProfile = ForgottenPasswordUtil.forgottenPasswordProfile( pwmRequest.getPwmApplication(), forgottenPasswordBean );
        {
            final Map<String, ForgottenPasswordProfile> profileIDList = pwmRequest.getConfig().getForgottenPasswordProfiles();
            final String profileDebugMsg = forgottenPasswordProfile != null && profileIDList != null && profileIDList.size() > 1
                    ? " profile=" + forgottenPasswordProfile.getIdentifier() + ", "
                    : "";
            LOGGER.trace( pwmRequest, () -> "entering forgotten password progress engine: "
                    + profileDebugMsg
                    + "flags=" + JsonUtil.serialize( recoveryFlags ) + ", "
                    + "progress=" + JsonUtil.serialize( progress ) );
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
                final String userGuid = LdapOperationsHelper.readLdapGuidValue( pwmApplication, pwmRequest.getLabel(), userIdentity, true );
                if ( ForgottenPasswordUtil.checkAuthRecord( pwmRequest, userGuid ) )
                {
                    LOGGER.debug( pwmRequest, () -> "marking " + IdentityVerificationMethod.PREVIOUS_AUTH + " method as satisfied" );
                    progress.getSatisfiedMethods().add( IdentityVerificationMethod.PREVIOUS_AUTH );
                }
            }
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
                        pwmRequest.commonValues(),
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
            StatisticsManager.incrementStat( pwmRequest, Statistic.RECOVERY_SUCCESSES );
        }

        final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequest.commonValues(), forgottenPasswordBean );
        if ( userInfo == null )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, "unable to load userInfo while processing forgotten password controller" );
        }

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
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();

        try
        {
            final ChaiUser theUser = pwmApplication.getProxiedChaiUser( userIdentity );
            theUser.unlockPassword();

            // mark the event log
            final UserInfo userInfoBean = ForgottenPasswordUtil.readUserInfo( pwmRequest.commonValues(), forgottenPasswordBean );
            pwmApplication.getAuditManager().submit( AuditEvent.UNLOCK_PASSWORD, userInfoBean, pwmSession );

            ForgottenPasswordUtil.sendUnlockNoticeEmail( pwmRequest.commonValues(), forgottenPasswordBean );

            pwmRequest.getPwmResponse().forwardToSuccessPage( Message.Success_UnlockAccount );
        }
        catch ( final ChaiOperationException e )
        {
            final String errorMsg = "unable to unlock user " + userIdentity + " error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNLOCK_FAILURE, errorMsg );
            LOGGER.error( pwmRequest, () -> errorInformation.toDebugStr() );
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
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );

        if ( !forgottenPasswordBean.getProgress().isAllPassed() )
        {
            return;
        }

        final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser( userIdentity );

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
        }

        try
        {
            final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(
                    pwmApplication,
                    pwmRequest,
                    PwmAuthenticationSource.FORGOTTEN_PASSWORD
            );
            sessionAuthenticator.authUserWithUnknownPassword( userIdentity, AuthenticationType.AUTH_FROM_PUBLIC_MODULE );
            pwmSession.getLoginInfoBean().getAuthFlags().add( AuthenticationType.AUTH_FROM_PUBLIC_MODULE );

            LOGGER.info( pwmRequest, () -> "user successfully supplied password recovery responses, forward to change password page: " + theUser.getEntryDN() );

            // mark the event log
            pwmApplication.getAuditManager().submit( AuditEvent.RECOVER_PASSWORD, pwmSession.getUserInfo(),
                    pwmSession );

            // mark user as requiring a new password.
            pwmSession.getLoginInfoBean().getLoginFlags().add( LoginInfoBean.LoginFlag.forcePwChange );

            // redirect user to change password screen.
            pwmRequest.sendRedirect( PwmServletDefinition.PublicChangePassword.servletUrlName() );
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
                    pwmRequest.getPwmApplication(),
                    pwmRequest,
                    PwmAuthenticationSource.FORGOTTEN_PASSWORD
            );
            sessionAuthenticator.simulateBadPassword( userIdentity );
            pwmRequest.getPwmApplication().getIntruderManager().convenience().markUserIdentity( userIdentity, pwmRequest );
        }

        pwmRequest.getPwmApplication().getIntruderManager().convenience().markAddressAndSession( pwmRequest );

        StatisticsManager.incrementStat( pwmRequest, Statistic.RECOVERY_FAILURES );
    }

    private void checkForLocaleSwitch( final PwmRequest pwmRequest, final ForgottenPasswordBean forgottenPasswordBean )
            throws PwmUnrecoverableException, IOException, ServletException
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
                    pwmRequest.commonValues(),
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
            LOGGER.error( pwmRequest, () -> errorInformation.toDebugStr() );
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
        ForgottenPasswordUtil.verifyRequirementsForAuthMethod( pwmRequest.commonValues(), forgottenPasswordBean, method );
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
                pwmRequest.setAttribute(
                        PwmRequestAttribute.ForgottenPasswordOtpRecord,
                        ForgottenPasswordUtil.readUserInfo( pwmRequest.commonValues(), forgottenPasswordBean ).getOtpUserRecord()
                );
                pwmRequest.forwardToJsp( JspUrl.RECOVER_PASSWORD_ENTER_OTP );
            }
            break;

            case TOKEN:
            {
                final ForgottenPasswordBean.Progress progress = forgottenPasswordBean.getProgress();
                final List<TokenDestinationItem> tokenDestinations = ForgottenPasswordUtil.figureAvailableTokenDestinations( pwmRequest.commonValues(), forgottenPasswordBean );
                if ( progress.getTokenDestination() == null )
                {
                    final boolean autoSelect = Boolean.parseBoolean( pwmRequest.getConfig().readAppProperty( AppProperty.FORGOTTEN_PASSWORD_TOKEN_AUTO_SELECT_DEST ) );
                    if ( autoSelect && tokenDestinations.size() == 1 )
                    {
                        final TokenDestinationItem singleItem = tokenDestinations.iterator().next();
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
                    final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequest.commonValues(), forgottenPasswordBean );
                    ForgottenPasswordUtil.initializeAndSendToken( pwmRequest.commonValues(), userInfo, progress.getTokenDestination() );
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
                final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequest.commonValues(), forgottenPasswordBean );
                final VerificationMethodSystem remoteMethod;
                if ( forgottenPasswordBean.getProgress().getRemoteRecoveryMethod() == null )
                {
                    remoteMethod = new RemoteVerificationMethod();
                    remoteMethod.init(
                            pwmRequest.getPwmApplication(),
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

                pwmRequest.setAttribute( PwmRequestAttribute.ForgottenPasswordPrompts, new ArrayList<>( prompts ) );
                pwmRequest.setAttribute( PwmRequestAttribute.ForgottenPasswordInstructions, displayInstructions );
                pwmRequest.forwardToJsp( JspUrl.RECOVER_PASSWORD_REMOTE );
            }
            break;

            case OAUTH:
                forgottenPasswordBean.getProgress().setInProgressVerificationMethod( IdentityVerificationMethod.OAUTH );
                final ForgottenPasswordProfile forgottenPasswordProfile = ForgottenPasswordUtil.forgottenPasswordProfile( pwmRequest.getPwmApplication(), forgottenPasswordBean );
                final OAuthSettings oAuthSettings = OAuthSettings.forForgottenPassword( forgottenPasswordProfile );
                final OAuthMachine oAuthMachine = new OAuthMachine( pwmRequest.getLabel(), oAuthSettings );
                pwmRequest.getPwmApplication().getSessionStateService().saveSessionBeans( pwmRequest );
                final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();
                oAuthMachine.redirectUserToOAuthServer( pwmRequest, null, userIdentity, forgottenPasswordProfile.getIdentifier() );
                break;


            default:
                throw new UnsupportedOperationException( "unexpected method during forward: " + method.toString() );
        }

    }

    private static void forwardToTokenChoiceJsp( final PwmRequest pwmRequest )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordBean( pwmRequest );
        final List<TokenDestinationItem> destItems = ForgottenPasswordUtil.figureAvailableTokenDestinations( pwmRequest.commonValues(), forgottenPasswordBean );
        pwmRequest.setAttribute( PwmRequestAttribute.TokenDestItems, new ArrayList<>( destItems ) );

        if ( ForgottenPasswordUtil.hasOtherMethodChoices( pwmRequest.commonValues(), forgottenPasswordBean, IdentityVerificationMethod.TOKEN ) )
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
                pwmRequest.getPwmApplication(),
                forgottenPasswordBean
        );

        final List<TokenDestinationItem> destItems = ForgottenPasswordUtil.figureAvailableTokenDestinations( pwmRequest.commonValues(), forgottenPasswordBean );

        ResetAction goBackAction = null;

        final boolean autoSelect = Boolean.parseBoolean( pwmRequest.getConfig().readAppProperty( AppProperty.FORGOTTEN_PASSWORD_TOKEN_AUTO_SELECT_DEST ) );
        if ( destItems.size() > 1 || !autoSelect )
        {
            goBackAction = ResetAction.clearTokenDestination;
        }
        else if ( ForgottenPasswordUtil.hasOtherMethodChoices( pwmRequest.commonValues(), forgottenPasswordBean, IdentityVerificationMethod.TOKEN ) )
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




