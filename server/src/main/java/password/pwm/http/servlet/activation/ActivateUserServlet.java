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

package password.pwm.http.servlet.activation;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.profile.ActivateUserProfile;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
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
import password.pwm.http.bean.ActivateUserBean;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.i18n.Message;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecord;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.event.AuditServiceClient;
import password.pwm.svc.intruder.IntruderServiceClient;
import password.pwm.svc.token.TokenPayload;
import password.pwm.svc.token.TokenService;
import password.pwm.svc.token.TokenType;
import password.pwm.svc.token.TokenUtil;
import password.pwm.util.CaptchaUtility;
import password.pwm.util.form.FormUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * User interaction servlet for creating new users (self registration).
 *
 * @author Jason D. Rivard
 */
@WebServlet(
        name = "ActivateUserServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + "/activate",
                PwmConstants.URL_PREFIX_PUBLIC + "/activate/*",
                PwmConstants.URL_PREFIX_PUBLIC + "/ActivateUser",
                PwmConstants.URL_PREFIX_PUBLIC + "/ActivateUser/*",
        }
)
public class ActivateUserServlet extends ControlledPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ActivateUserServlet.class );

    public enum ActivateUserAction implements ProcessAction
    {
        search( HttpMethod.POST ),
        tokenChoice( HttpMethod.POST ),
        enterCode( HttpMethod.POST, HttpMethod.GET ),
        reset( HttpMethod.POST ),
        agree( HttpMethod.POST ),;

        private final Collection<HttpMethod> method;

        ActivateUserAction( final HttpMethod... method )
        {
            this.method = List.of( method );
        }

        @Override
        public Collection<HttpMethod> permittedMethods( )
        {
            return method;
        }
    }

    public enum ResetType
    {
        exitActivation,
        clearTokenDestination,
    }

    @Override
    public Class<? extends ProcessAction> getProcessActionsClass( )
    {
        return ActivateUserAction.class;
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        final DomainConfig config = pwmRequest.getDomainConfig();

        if ( !config.readSettingAsBoolean( PwmSetting.ACTIVATE_USER_ENABLE ) )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "activate user is not enabled" );
            throw new PwmUnrecoverableException( errorInformation );
        }

        if ( pwmRequest.isAuthenticated() )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_USERAUTHENTICATED );
            throw new PwmUnrecoverableException( errorInformation );
        }

        final Optional<? extends ProcessAction> action = this.readProcessAction( pwmRequest );

        // convert a url command like /public/newuser/12321321 to redirect with a process action.
        if ( action.isEmpty() )
        {
            if ( pwmRequest.convertURLtokenCommand( PwmServletDefinition.ActivateUser, ActivateUserAction.enterCode ) )
            {
                return ProcessStatus.Halt;
            }
        }

        return ProcessStatus.Continue;
    }

    static UserInfo userInfo ( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final ActivateUserBean activateUserBean = activateUserBean( pwmRequest );
        return UserInfoFactory.newUserInfoUsingProxy(
                pwmRequest.getPwmApplication(),
                pwmRequest.getLabel(),
                activateUserBean.getUserIdentity(),
                pwmRequest.getLocale() );

    }

    static ActivateUserBean activateUserBean( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return pwmRequest.getPwmDomain().getSessionStateService().getBean( pwmRequest, ActivateUserBean.class );
    }

    static ActivateUserProfile activateUserProfile( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final ActivateUserBean activateUserBean = activateUserBean( pwmRequest );
        final String profileID = activateUserBean.getProfileID();
        final ActivateUserProfile activateUserProfile = pwmRequest.getDomainConfig().getUserActivationProfiles().get( profileID );
        if ( activateUserProfile == null )
        {
            throw  PwmUnrecoverableException.newException( PwmError.ERROR_NO_PROFILE_ASSIGNED, "unable to load activate user profile" );
        }
        return activateUserProfile;
    }

    @ActionHandler( action = "reset" )
    public ProcessStatus handleResetRequest( final PwmRequest pwmRequest )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final ResetType resetType = pwmRequest.readParameterAsEnum( PwmConstants.PARAM_RESET_TYPE, ResetType.class, ResetType.exitActivation );

        switch ( resetType )
        {
            case exitActivation:
                pwmRequest.getPwmDomain().getSessionStateService().clearBean( pwmRequest, ActivateUserBean.class );
                ActivateUserUtils.forwardToSearchUserForm( pwmRequest );
                return ProcessStatus.Halt;

            case clearTokenDestination:
                activateUserBean( pwmRequest ).setTokenDestination( null );
                activateUserBean( pwmRequest ).setTokenSent( false );
                break;

            default:
                JavaHelper.unhandledSwitchStatement( resetType );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "search" )
    public ProcessStatus handleSearchRequest( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final DomainConfig config = pwmDomain.getConfig();
        final LocalSessionStateBean ssBean = pwmSession.getSessionStateBean();

        if ( CaptchaUtility.captchaEnabledForRequest( pwmRequest ) )
        {
            if ( !CaptchaUtility.verifyReCaptcha( pwmRequest ) )
            {
                final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_BAD_CAPTCHA_RESPONSE );
                throw new PwmUnrecoverableException( errorInfo );
            }
        }

        pwmDomain.getSessionStateService().clearBean( pwmRequest, ActivateUserBean.class );
        final List<FormConfiguration> configuredActivationForm = config.readSettingAsForm( PwmSetting.ACTIVATE_USER_FORM );

        Map<FormConfiguration, String> formValues = new HashMap<>();
        try
        {
            //read the values from the request
            formValues = FormUtility.readFormValuesFromRequest( pwmRequest, configuredActivationForm,
                    ssBean.getLocale() );

            // check for intruders
            IntruderServiceClient.checkAttributes( pwmDomain, formValues );

            // read the context attr
            final String contextParam = pwmRequest.readParameterAsString( PwmConstants.PARAM_CONTEXT );

            // read the profile attr
            final String ldapProfile = pwmRequest.readParameterAsString( PwmConstants.PARAM_LDAP_PROFILE );

            // see if the values meet the configured form requirements.
            FormUtility.validateFormValues( config, formValues, ssBean.getLocale() );

            final String searchFilter = ActivateUserUtils.figureLdapSearchFilter( pwmRequest );

            // read an ldap user object based on the params
            final UserIdentity userIdentity;
            {
                final UserSearchEngine userSearchEngine = pwmDomain.getUserSearchEngine();
                final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                        .contexts( Collections.singletonList( contextParam ) )
                        .filter( searchFilter )
                        .formValues( formValues )
                        .ldapProfile( ldapProfile )
                        .build();

                userIdentity = userSearchEngine.performSingleUserSearch( searchConfiguration, pwmRequest.getLabel() );
            }

            ActivateUserUtils.validateParamsAgainstLDAP( pwmRequest, formValues, userIdentity );

            ActivateUserUtils.initUserActivationBean( pwmRequest, userIdentity );
            IntruderServiceClient.clearAttributes( pwmDomain, formValues );
            IntruderServiceClient.clearAddressAndSession( pwmRequest.getPwmDomain(), pwmSession );
        }
        catch ( final PwmOperationalException e )
        {
            IntruderServiceClient.markAttributes( pwmRequest, formValues );
            IntruderServiceClient.markAddressAndSession( pwmDomain, pwmSession );
            setLastError( pwmRequest, e.getErrorInformation() );
            LOGGER.debug( pwmRequest, e.getErrorInformation() );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "tokenChoice" )
    private ProcessStatus processTokenChoice( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final UserInfo userInfo = userInfo( pwmRequest );
        final ActivateUserProfile activateUserProfile = activateUserProfile( pwmRequest );
        final MessageSendMethod tokenSendMethod = activateUserProfile.readSettingAsEnum( PwmSetting.ACTIVATE_TOKEN_SEND_METHOD, MessageSendMethod.class );

        final List<TokenDestinationItem> tokenDestinationItems = TokenUtil.figureAvailableTokenDestinations(
                pwmRequest.getPwmDomain(),
                pwmRequest.getLabel(),
                pwmRequest.getLocale(),
                userInfo,
                tokenSendMethod
        );

        final String requestedID = pwmRequest.readParameterAsString( "choice", PwmHttpRequestWrapper.Flag.BypassValidation );

        final Optional<TokenDestinationItem> tokenDestinationItem = TokenDestinationItem.tokenDestinationItemForID( tokenDestinationItems, requestedID );
        if ( tokenDestinationItem.isPresent() )
        {
            final ActivateUserBean activateUserBean = activateUserBean( pwmRequest );
            activateUserBean.setTokenDestination( tokenDestinationItem.get() );
        }

        return ProcessStatus.Continue;
    }


    @ActionHandler( action = "enterCode" )
    public ProcessStatus handleEnterCode(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final ActivateUserBean activateUserBean = pwmDomain.getSessionStateService().getBean( pwmRequest, ActivateUserBean.class );
        final String userEnteredCode = pwmRequest.readParameterAsString( PwmConstants.PARAM_TOKEN );

        ErrorInformation errorInformation = null;
        try
        {
            final TokenPayload tokenPayload = TokenUtil.checkEnteredCode(
                    pwmRequest.getPwmRequestContext(),
                    userEnteredCode,
                    activateUserBean.getTokenDestination(),
                    activateUserBean.getUserIdentity(),
                    TokenType.ACTIVATION,
                    TokenService.TokenEntryType.unauthenticated
            );

            if ( activateUserBean.getUserIdentity() == null )
            {
                ActivateUserUtils.initUserActivationBean( pwmRequest, tokenPayload.getUserIdentity() );
            }

            activateUserBean.setTokenPassed( true );
            activateUserBean.setTokenSent( true );

            if ( pwmRequest.getDomainConfig().readSettingAsBoolean( PwmSetting.DISPLAY_TOKEN_SUCCESS_BUTTON ) )
            {
                pwmRequest.setAttribute( PwmRequestAttribute.TokenDestItems, tokenPayload.getDestination() );
                pwmRequest.forwardToJsp( JspUrl.ACTIVATE_USER_TOKEN_SUCCESS );
                return ProcessStatus.Halt;
            }
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.debug( pwmRequest, () -> "error while checking entered token: " );
            errorInformation = e.getErrorInformation();
        }


        if ( !activateUserBean.isTokenPassed() )
        {
            if ( errorInformation == null )
            {
                errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT );
            }
            LOGGER.debug( pwmRequest, errorInformation );
            setLastError( pwmRequest, errorInformation );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "agree" )
    public ProcessStatus handleAgreeRequest(
            final PwmRequest pwmRequest
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        LOGGER.debug( pwmRequest, () -> "user accepted agreement" );

        final ActivateUserBean activateUserBean = activateUserBean( pwmRequest );

        if ( !activateUserBean.isAgreementPassed() )
        {
            activateUserBean.setAgreementPassed( true );
            final AuditRecord auditRecord = AuditRecordFactory.make( pwmRequest ).createUserAuditRecord(
                    AuditEvent.AGREEMENT_PASSED,
                    pwmRequest.getUserInfoIfLoggedIn(),
                    pwmRequest.getLabel(),
                    "ActivateUser"
            );

            AuditServiceClient.submit( pwmRequest, auditRecord );
        }

        return ProcessStatus.Continue;
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ActivateUserBean activateUserBean = activateUserBean( pwmRequest );

        if ( !activateUserBean.isFormValidated() || activateUserBean.getUserIdentity() == null )
        {
            ActivateUserUtils.forwardToSearchUserForm( pwmRequest );
            return;
        }

        final UserInfo userInfo = userInfo( pwmRequest );
        final ActivateUserProfile activateUserProfile = activateUserProfile( pwmRequest );

        final MessageSendMethod tokenSendMethod = activateUserProfile.readSettingAsEnum( PwmSetting.ACTIVATE_TOKEN_SEND_METHOD, MessageSendMethod.class );
        if ( !activateUserBean.isTokenPassed() && tokenSendMethod != MessageSendMethod.NONE && tokenSendMethod != null )
        {
            final List<TokenDestinationItem> tokenDestinationItems = TokenUtil.figureAvailableTokenDestinations(
                    pwmDomain,
                    pwmRequest.getLabel(),
                    pwmRequest.getLocale(),
                    userInfo,
                    tokenSendMethod
            );

            if ( activateUserBean.getTokenDestination() == null )
            {
                final boolean autoSelect = Boolean.parseBoolean( pwmRequest.getDomainConfig().readAppProperty( AppProperty.ACTIVATE_USER_TOKEN_AUTO_SELECT_DEST ) );
                if ( tokenDestinationItems.size() == 1 && autoSelect )
                {
                    activateUserBean.setTokenDestination( tokenDestinationItems.iterator().next() );
                }
                else
                {
                    forwardToTokenChoiceJsp( pwmRequest, tokenDestinationItems );
                    return;
                }
            }

            if ( !activateUserBean.isTokenSent() && activateUserBean.getTokenDestination() != null )
            {
                TokenUtil.initializeAndSendToken(
                        pwmRequest.getPwmRequestContext(),
                        TokenUtil.TokenInitAndSendRequest.builder()
                                .userInfo( userInfo )
                                .tokenDestinationItem( activateUserBean.getTokenDestination() )
                                .emailToSend( PwmSetting.EMAIL_ACTIVATION_VERIFICATION )
                                .tokenType( TokenType.ACTIVATION )
                                .smsToSend( PwmSetting.SMS_ACTIVATION_VERIFICATION_TEXT )
                                .build()
                );
                activateUserBean.setTokenSent( true );
            }

            if ( !activateUserBean.isTokenPassed() )
            {
                forwardToEnterCodeJsp( pwmRequest, tokenDestinationItems );
                return;
            }
        }

        {
            final String agreementText = activateUserProfile.readSettingAsLocalizedString(
                    PwmSetting.ACTIVATE_AGREEMENT_MESSAGE,
                    pwmSession.getSessionStateBean().getLocale() );

            if ( StringUtil.notEmpty( agreementText ) && !activateUserBean.isAgreementPassed() )
            {
                ActivateUserUtils.forwardToAgreementPage( pwmRequest );
                return;
            }
        }

        try
        {
            ActivateUserUtils.activateUser( pwmRequest, activateUserBean.getUserIdentity() );
            pwmRequest.getPwmResponse().forwardToSuccessPage( Message.Success_ActivateUser );
        }
        catch ( final PwmOperationalException e )
        {
            LOGGER.debug( pwmRequest, e.getErrorInformation() );
            IntruderServiceClient.markUserIdentity( pwmRequest, activateUserBean.getUserIdentity() );
            IntruderServiceClient.markAddressAndSession( pwmDomain, pwmSession );
            pwmRequest.respondWithError( e.getErrorInformation() );
        }
    }

    private static void forwardToEnterCodeJsp( final PwmRequest pwmRequest, final List<TokenDestinationItem> tokenDestinationItems )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final boolean autoSelect = Boolean.parseBoolean( pwmRequest.getDomainConfig().readAppProperty( AppProperty.ACTIVATE_USER_TOKEN_AUTO_SELECT_DEST ) );
        final ResetType goBackAction = tokenDestinationItems.size() > 1 || !autoSelect
                ? ResetType.clearTokenDestination
                : null;

        if ( goBackAction != null )
        {
            pwmRequest.setAttribute( PwmRequestAttribute.GoBackAction, goBackAction.name() );
        }
        pwmRequest.forwardToJsp( JspUrl.ACTIVATE_USER_ENTER_CODE );
    }

    private static void forwardToTokenChoiceJsp( final PwmRequest pwmRequest, final List<TokenDestinationItem> tokenDestinationItems )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        pwmRequest.setAttribute( PwmRequestAttribute.TokenDestItems, new ArrayList<>( tokenDestinationItems ) );
        pwmRequest.forwardToJsp( JspUrl.ACTIVATE_USER_TOKEN_CHOICE );
    }

}
