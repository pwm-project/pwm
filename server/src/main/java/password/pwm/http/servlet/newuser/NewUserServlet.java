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

package password.pwm.http.servlet.newuser;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.VerificationMethodSystem;
import password.pwm.bean.ProfileID;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.NewUserProfile;
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
import password.pwm.http.PwmURL;
import password.pwm.http.bean.NewUserBean;
import password.pwm.http.filter.AuthenticationFilter;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.http.servlet.forgottenpw.RemoteVerificationMethod;
import password.pwm.i18n.Message;
import password.pwm.user.UserInfo;
import password.pwm.user.UserInfoBean;
import password.pwm.svc.token.TokenPayload;
import password.pwm.svc.token.TokenService;
import password.pwm.svc.token.TokenType;
import password.pwm.svc.token.TokenUtil;
import password.pwm.util.CaptchaUtility;
import password.pwm.util.form.FormUtility;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.Percent;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.password.PasswordUtility;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.RestCheckPasswordServer;
import password.pwm.ws.server.rest.RestFormSigningServer;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * User interaction servlet for creating new users (self registration).
 *
 * @author Jason D. Rivard
 */
@WebServlet(
        name = "NewUserServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + "/newuser",
                PwmConstants.URL_PREFIX_PUBLIC + "/newuser/*",
                PwmConstants.URL_PREFIX_PUBLIC + "/NewUser",
                PwmConstants.URL_PREFIX_PUBLIC + "/NewUser/*",
        }
)
public class NewUserServlet extends ControlledPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( NewUserServlet.class );

    @Override
    protected PwmLogger getLogger()
    {
        return LOGGER;
    }

    static final String FIELD_PASSWORD1 = "password1";
    static final String FIELD_PASSWORD2 = "password2";
    static final String TOKEN_PAYLOAD_ATTR = "p";

    public enum NewUserAction implements AbstractPwmServlet.ProcessAction
    {
        profileChoice( HttpMethod.POST, HttpMethod.POST ),
        checkProgress( HttpMethod.GET ),
        complete( HttpMethod.GET ),
        processForm( HttpMethod.POST ),
        validate( HttpMethod.POST ),
        enterCode( HttpMethod.POST, HttpMethod.GET ),
        enterRemoteResponse( HttpMethod.POST ),
        reset( HttpMethod.POST ),
        agree( HttpMethod.POST ),;

        private final Collection<HttpMethod> method;

        NewUserAction( final HttpMethod... method )
        {
            this.method = List.of( method );
        }

        @Override
        public Collection<HttpMethod> permittedMethods( )
        {
            return method;
        }
    }

    @Override
    public Optional<Class<? extends ProcessAction>> getProcessActionsClass( )
    {
        return Optional.of( NewUserAction.class );
    }


    static NewUserBean getNewUserBean( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return pwmRequest.getPwmDomain().getSessionStateService().getBean( pwmRequest, NewUserBean.class );
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();

        final DomainConfig config = pwmDomain.getConfig();

        if ( !config.readSettingAsBoolean( PwmSetting.NEWUSER_ENABLE ) )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_SERVICE_NOT_AVAILABLE );
        }

        final NewUserBean newUserBean = pwmDomain.getSessionStateService().getBean( pwmRequest, NewUserBean.class );

        final String signedFormData = pwmRequest.readParameterAsString( PwmConstants.PARAM_SIGNED_FORM, PwmHttpRequestWrapper.Flag.BypassValidation );
        if ( StringUtil.notEmpty( signedFormData ) )
        {
            final Map<String, String> jsonForm = RestFormSigningServer.readSignedFormValue( pwmDomain, signedFormData );
            LOGGER.trace( pwmRequest, () -> "detected signedForm parameter in request, will read and place in bean; keys="
                    + JsonFactory.get().serializeCollection( jsonForm.keySet() ) );
            newUserBean.setRemoteInputData( jsonForm );
        }

        // convert a url command like /public/newuser/profile/xxx to set profile.
        if ( readProfileFromUrl( pwmRequest, newUserBean ) )
        {
            return ProcessStatus.Halt;
        }

        final Optional<? extends ProcessAction> action = this.readProcessAction( pwmRequest );

        // convert a url command like /public/newuser/12321321 to redirect with a process action.
        if ( action.isEmpty() )
        {
            if ( pwmRequest.convertURLtokenCommand( PwmServletDefinition.NewUser, NewUserAction.enterCode ) )
            {
                return ProcessStatus.Halt;
            }
        }
        else if ( action.get() != NewUserAction.complete && action.get() != NewUserAction.checkProgress )
        {
            if ( pwmRequest.isAuthenticated() )
            {
                pwmRequest.respondWithError( PwmError.ERROR_USERAUTHENTICATED.toInfo() );
                return ProcessStatus.Halt;
            }
        }

        return ProcessStatus.Continue;
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final NewUserBean newUserBean = getNewUserBean( pwmRequest );
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        if ( newUserBean.getProfileID() == null )
        {
            final Set<ProfileID> newUserProfileIDs = pwmDomain.getConfig().getNewUserProfiles().keySet();
            if ( newUserProfileIDs.isEmpty() )
            {
                pwmRequest.respondWithError( new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, "no new user profiles are defined" ) );
                return;
            }

            final LinkedHashMap<String, String> visibleProfiles = new LinkedHashMap<>( NewUserUtils.figureDisplayableProfiles( pwmRequest ) );

            if ( visibleProfiles.size() == 1 )
            {
                final ProfileID singleID = newUserProfileIDs.iterator().next();
                LOGGER.trace( pwmRequest, () -> "only one new user profile is defined, auto-selecting profile " + singleID );
                newUserBean.setProfileID( singleID );
            }
            else
            {
                LOGGER.trace( pwmRequest, () -> "new user profile not yet selected, redirecting to choice page" );
                pwmRequest.setAttribute( PwmRequestAttribute.NewUser_VisibleProfiles, visibleProfiles );
                pwmRequest.forwardToJsp( JspUrl.NEW_USER_PROFILE_CHOICE );
                return;
            }
        }

        final NewUserProfile newUserProfile = getNewUserProfile( pwmRequest );
        if ( newUserBean.getCreateStartTime() != null )
        {
            forwardToWait( pwmRequest, newUserProfile );
            return;
        }


        // try to read the new user policy to make sure it's readable, that way an exception is thrown here instead of by the jsp
        {
            final Instant startTime = Instant.now();
            newUserProfile.getNewUserPasswordPolicy( pwmRequest.getPwmRequestContext() );
            LOGGER.trace( pwmRequest, () -> "read new user password policy in ", TimeDuration.fromCurrent( startTime ) );
        }

        if ( !newUserBean.isFormPassed() )
        {
            if ( showFormPage( newUserProfile ) )
            {
                forwardToFormPage( pwmRequest, newUserBean );
                return;
            }
            else
            {
                NewUserFormUtils.injectRemoteValuesIntoForm( newUserBean, newUserProfile );
                try
                {
                    verifyForm( pwmRequest, newUserBean.getNewUserForm(), false );
                }
                catch ( final PwmDataValidationException e )
                {
                    throw new PwmUnrecoverableException( e.getErrorInformation() );
                }
                newUserBean.setFormPassed( true );
            }
        }


        if ( NewUserUtils.checkForExternalResponsesVerificationProgress( pwmRequest, newUserBean, newUserProfile ) == ProcessStatus.Halt )
        {
            return;
        }

        if ( NewUserUtils.checkForTokenVerificationProgress( pwmRequest, newUserBean, newUserProfile ) == ProcessStatus.Halt )
        {
            return;
        }

        final String newUserAgreementText = newUserProfile.readSettingAsLocalizedString( PwmSetting.NEWUSER_AGREEMENT_MESSAGE,
                pwmSession.getSessionStateBean().getLocale() );
        if ( StringUtil.notEmpty( newUserAgreementText ) )
        {
            if ( !newUserBean.isAgreementPassed() )
            {
                final MacroRequest macroRequest = NewUserUtils.createMacroMachineForNewUser(
                        pwmDomain,
                        newUserProfile,
                        pwmRequest.getLabel(),
                        newUserBean.getNewUserForm(),
                        null
                );
                final String expandedText = macroRequest.expandMacros( newUserAgreementText );
                pwmRequest.setAttribute( PwmRequestAttribute.AgreementText, expandedText );
                pwmRequest.forwardToJsp( JspUrl.NEW_USER_AGREEMENT );
                return;
            }
        }

        // success so create the new user.
        final String newUserDN = NewUserUtils.determineUserDN( pwmRequest, newUserBean.getNewUserForm() );

        try
        {
            NewUserUtils.createUser( newUserBean.getNewUserForm(), pwmRequest, newUserDN );
            newUserBean.setCreateStartTime( Instant.now() );
            forwardToWait( pwmRequest, newUserProfile );
        }
        catch ( final PwmOperationalException e )
        {
            LOGGER.error( pwmRequest, () -> "error during user creation: " + e.getMessage() );
            if ( newUserProfile.readSettingAsBoolean( PwmSetting.NEWUSER_DELETE_ON_FAIL ) )
            {
                NewUserUtils.deleteUserAccount( newUserDN, pwmRequest );
            }
            LOGGER.error( pwmRequest, () -> e.getErrorInformation().toDebugStr() );
            pwmRequest.respondWithError( e.getErrorInformation() );
        }
    }

    private boolean showFormPage( final NewUserProfile profile )
    {
        {
            final boolean promptForPassword = profile.readSettingAsBoolean( PwmSetting.NEWUSER_PROMPT_FOR_PASSWORD );
            if ( promptForPassword )
            {
                return true;
            }
        }

        final List<FormConfiguration> formConfigurations = profile.readSettingAsForm( PwmSetting.NEWUSER_FORM );

        for ( final FormConfiguration formConfiguration : formConfigurations )
        {
            if ( formConfiguration.getType() != FormConfiguration.Type.hidden )
            {
                return true;
            }
        }

        return false;
    }

    private boolean readProfileFromUrl( final PwmRequest pwmRequest, final NewUserBean newUserBean )
            throws PwmUnrecoverableException, IOException
    {
        final String profileUrlSegment = "profile";
        final String urlRemainder = servletUriRemainder( pwmRequest, profileUrlSegment );

        if ( urlRemainder != null && !urlRemainder.isEmpty() )
        {
            final List<String> urlSegments = PwmURL.splitPathString( urlRemainder );
            if ( urlSegments.size() == 2 && profileUrlSegment.equals( urlSegments.get( 0 ) ) )
            {
                final ProfileID requestedProfile = ProfileID.create( urlSegments.get( 1 ) );
                final Collection<ProfileID> profileIDs = pwmRequest.getDomainConfig().getNewUserProfiles().keySet();
                if ( profileIDs.contains( requestedProfile ) )
                {
                    LOGGER.debug( pwmRequest, () -> "detected profile on request uri: " + requestedProfile );
                    newUserBean.setProfileID( requestedProfile );
                    newUserBean.setUrlSpecifiedProfile( true );
                    pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.NewUser );
                    return true;
                }
                else
                {
                    final String errorMsg = "unknown requested new user profile";
                    LOGGER.debug( pwmRequest, () -> errorMsg + ": " + requestedProfile );
                    throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE ) );
                }
            }
        }
        return false;
    }

    @ActionHandler( action = "validate" )
    public ProcessStatus restValidateForm(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final Locale locale = pwmRequest.getLocale();

        try
        {
            final NewUserBean newUserBean = getNewUserBean( pwmRequest );
            final NewUserForm newUserForm = NewUserFormUtils.readFromJsonRequest( pwmRequest, newUserBean );
            PasswordUtility.PasswordCheckInfo passwordCheckInfo = verifyForm( pwmRequest, newUserForm, true );
            if ( passwordCheckInfo.isPassed() && passwordCheckInfo.getMatch() == PasswordUtility.PasswordCheckInfo.MatchStatus.MATCH )
            {
                passwordCheckInfo = new PasswordUtility.PasswordCheckInfo(
                        Message.getLocalizedMessage( locale,
                                Message.Success_NewUserForm, pwmDomain.getConfig() ),
                        passwordCheckInfo.isPassed(),
                        passwordCheckInfo.getStrength(),
                        passwordCheckInfo.getMatch(),
                        passwordCheckInfo.getErrorCode()
                );
            }
            final RestCheckPasswordServer.JsonOutput jsonData = RestCheckPasswordServer.JsonOutput.fromPasswordCheckInfo(
                    passwordCheckInfo );

            final RestResultBean restResultBean = RestResultBean.withData( jsonData, RestCheckPasswordServer.JsonOutput.class );
            pwmRequest.outputJsonResult( restResultBean );
        }
        catch ( final PwmOperationalException e )
        {
            final RestResultBean restResultBean = RestResultBean.fromError( e.getErrorInformation(), pwmRequest );
            LOGGER.debug( pwmRequest, () -> "error while validating new user form: " + e.getMessage() );
            pwmRequest.outputJsonResult( restResultBean );
        }

        return ProcessStatus.Halt;
    }

    static PasswordUtility.PasswordCheckInfo verifyForm(
            final PwmRequest pwmRequest,
            final NewUserForm newUserForm,
            final boolean allowResultCaching
    )
            throws PwmDataValidationException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final Instant startTime = Instant.now();
        final Locale locale = pwmRequest.getLocale();
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final NewUserProfile newUserProfile = getNewUserProfile( pwmRequest );
        final List<FormConfiguration> formDefinition = newUserProfile.readSettingAsForm( PwmSetting.NEWUSER_FORM );
        final Map<FormConfiguration, String> formValueData = FormUtility.readFormValuesFromMap( newUserForm.getFormData(), formDefinition, locale );

        FormUtility.validateFormValues( pwmDomain.getConfig(), formValueData, locale );
        final List<FormUtility.ValidationFlag> validationFlags = new ArrayList<>();
        validationFlags.add( FormUtility.ValidationFlag.checkReadOnlyAndHidden );
        if ( allowResultCaching )
        {
            validationFlags.add( FormUtility.ValidationFlag.allowResultCaching );
        }
        FormUtility.validateFormValueUniqueness(
                pwmRequest.getLabel(),
                pwmDomain,
                formValueData,
                locale,
                Collections.emptyList(),
                validationFlags.toArray( new FormUtility.ValidationFlag[0] )
        );

        NewUserUtils.remoteVerifyFormData( pwmRequest, newUserForm, null );

        final UserInfo uiBean = UserInfoBean.builder()
                .cachedPasswordRuleAttributes( FormUtility.asStringMap( formValueData ) )
                .passwordPolicy( newUserProfile.getNewUserPasswordPolicy( pwmRequest.getPwmRequestContext() ) )
                .build();

        final boolean promptForPassword = newUserProfile.readSettingAsBoolean( PwmSetting.NEWUSER_PROMPT_FOR_PASSWORD );


        final PasswordUtility.PasswordCheckInfo passwordCheckInfo;
        if ( promptForPassword )
        {
            passwordCheckInfo =  PasswordUtility.checkEnteredPassword(
                    pwmRequest.getPwmRequestContext(),
                    null,
                    uiBean,
                    null,
                    newUserForm.getNewUserPassword(),
                    newUserForm.getConfirmPassword()
            );
        }
        else
        {
            passwordCheckInfo = new PasswordUtility.PasswordCheckInfo( null, true, 0, PasswordUtility.PasswordCheckInfo.MatchStatus.MATCH, 0 );
        }

        LOGGER.trace( pwmRequest, () -> "competed form validation in ", TimeDuration.fromCurrent( startTime ) );
        return passwordCheckInfo;
    }

    @ActionHandler( action = "enterCode" )
    public ProcessStatus handleEnterCodeRequest( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final NewUserBean newUserBean = getNewUserBean( pwmRequest );
        final NewUserProfile newUserProfile = getNewUserProfile( pwmRequest );
        final String userEnteredCode = pwmRequest.readParameterAsString( PwmConstants.PARAM_TOKEN );

        final TokenDestinationItem tokenDestinationItem = NewUserUtils.tokenDestinationItemForCurrentValidation(
                pwmRequest,
                newUserBean,
                newUserProfile ).orElseThrow();

        ErrorInformation errorInformation = null;
        TokenPayload tokenPayload = null;
        try
        {
            tokenPayload = TokenUtil.checkEnteredCode(
                    pwmRequest.getPwmRequestContext(),
                    userEnteredCode,
                    tokenDestinationItem,
                    null,
                    TokenType.NEWUSER,
                    TokenService.TokenEntryType.unauthenticated
            );

        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.debug( pwmRequest, () -> "error while checking entered token: " );
            errorInformation = e.getErrorInformation();
        }

        if ( tokenPayload != null )
        {
            try
            {
                final NewUserTokenData newUserTokenData = NewUserFormUtils.fromTokenPayload( pwmRequest, tokenPayload );
                newUserBean.setProfileID( newUserTokenData.getProfileID() );
                final NewUserForm newUserFormFromToken = newUserTokenData.getFormData();

                final TokenDestinationItem.Type tokenType = tokenPayload.getDestination().getType();

                if ( tokenType == TokenDestinationItem.Type.email )
                {
                    try
                    {
                        verifyForm( pwmRequest, newUserFormFromToken, false );
                        newUserBean.setRemoteInputData( newUserTokenData.getInjectionData() );
                        newUserBean.setNewUserForm( newUserFormFromToken );
                        newUserBean.setProfileID( newUserTokenData.getProfileID() );
                        newUserBean.setFormPassed( true );
                        newUserBean.getCompletedTokenFields().addAll( newUserTokenData.getCompletedTokenFields() );
                        newUserBean.setCurrentTokenField( newUserTokenData.getCurrentTokenField() );
                    }
                    catch ( final PwmUnrecoverableException | PwmOperationalException e )
                    {
                        LOGGER.error( pwmRequest, () -> "while reading stored form data in token payload, form validation error occurred: " + e.getMessage() );
                        errorInformation = e.getErrorInformation();
                    }
                }
                else if ( tokenType == TokenDestinationItem.Type.sms )
                {
                    if ( newUserBean.getNewUserForm() == null || !newUserBean.getNewUserForm().isConsistentWith( newUserFormFromToken ) )
                    {
                        LOGGER.debug( pwmRequest, () -> "token value is valid, but form data does not match current session form data" );
                        final String errorMsg = "sms token does not match current session";
                        errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT, errorMsg );
                    }
                }
            }
            catch ( final PwmOperationalException e )
            {
                errorInformation = e.getErrorInformation();
            }
        }

        if ( errorInformation != null )
        {
            LOGGER.debug( pwmRequest, errorInformation );
            setLastError( pwmRequest, errorInformation );
            return ProcessStatus.Continue;
        }

        LOGGER.debug( pwmRequest, () -> "marking token as passed " + JsonFactory.get().serialize( tokenDestinationItem ) );
        newUserBean.getCompletedTokenFields().add( newUserBean.getCurrentTokenField() );
        newUserBean.setTokenSent( false );
        newUserBean.setCurrentTokenField( null );

        if ( pwmRequest.getDomainConfig().readSettingAsBoolean( PwmSetting.DISPLAY_TOKEN_SUCCESS_BUTTON ) )
        {
            pwmRequest.setAttribute( PwmRequestAttribute.TokenDestItems, tokenPayload.getDestination() );
            pwmRequest.forwardToJsp( JspUrl.NEW_USER_TOKEN_SUCCESS );
            return ProcessStatus.Halt;
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "enterRemoteResponse" )
    public ProcessStatus processEnterRemoteResponse( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final String prefix = "remote-";
        final NewUserBean newUserBean = getNewUserBean( pwmRequest );
        final VerificationMethodSystem remoteRecoveryMethod = NewUserUtils.readRemoteVerificationMethod( pwmRequest, newUserBean );

        final Map<String, String> remoteResponses = RemoteVerificationMethod.readRemoteResponses( pwmRequest, prefix );

        final ErrorInformation errorInformation = remoteRecoveryMethod.respondToPrompts( remoteResponses );

        if ( remoteRecoveryMethod.getVerificationState() == VerificationMethodSystem.VerificationState.COMPLETE )
        {
            newUserBean.setExternalResponsesPassed( true );
        }

        if ( remoteRecoveryMethod.getVerificationState() == VerificationMethodSystem.VerificationState.FAILED )
        {
            newUserBean.setExternalResponsesPassed( false );
            pwmRequest.respondWithError( errorInformation, true );
            LOGGER.debug( pwmRequest, () -> "unsuccessful remote response verification input: " + errorInformation.toDebugStr() );
            return ProcessStatus.Continue;
        }

        if ( errorInformation != null )
        {
            setLastError( pwmRequest, errorInformation );
        }

        return ProcessStatus.Continue;
    }


    @ActionHandler( action = "profileChoice" )
    public ProcessStatus handleProfileChoiceRequest( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final Set<ProfileID> profileIDs = pwmRequest.getDomainConfig().getNewUserProfiles().keySet();
        final Optional<ProfileID> requestedProfileID = ProfileID.createNullable( pwmRequest.readParameterAsString( "profile" ) );

        final NewUserBean newUserBean = getNewUserBean( pwmRequest );

        if ( requestedProfileID.isPresent() && profileIDs.contains( requestedProfileID.get() ) )
        {
            newUserBean.setProfileID( requestedProfileID.get() );
        }
        else
        {
            newUserBean.setProfileID( null );
        }



        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "processForm" )
    public ProcessStatus handleProcessFormRequest( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final NewUserBean newUserBean = getNewUserBean( pwmRequest );

        if ( CaptchaUtility.captchaEnabledForRequest( pwmRequest ) )
        {
            if ( !CaptchaUtility.verifyReCaptcha( pwmRequest ) )
            {
                final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_BAD_CAPTCHA_RESPONSE );
                LOGGER.debug( pwmRequest, errorInfo );
                setLastError( pwmRequest, errorInfo );
                forwardToFormPage( pwmRequest, newUserBean );
                return ProcessStatus.Halt;
            }
        }

        newUserBean.setFormPassed( false );
        newUserBean.setNewUserForm( null );

        try
        {
            final NewUserForm newUserForm = NewUserFormUtils.readFromRequest( pwmRequest, newUserBean );
            final PasswordUtility.PasswordCheckInfo passwordCheckInfo = verifyForm( pwmRequest, newUserForm, true );
            NewUserUtils.passwordCheckInfoToException( passwordCheckInfo );
            newUserBean.setNewUserForm( newUserForm );
            newUserBean.setFormPassed( true );
        }
        catch ( final PwmOperationalException e )
        {
            setLastError( pwmRequest, e.getErrorInformation() );
            forwardToFormPage( pwmRequest, newUserBean );
            return ProcessStatus.Halt;
        }
        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "checkProgress" )
    public ProcessStatus restCheckProgress(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final NewUserBean newUserBean = getNewUserBean( pwmRequest );
        final Instant startTime = newUserBean.getCreateStartTime();
        if ( startTime == null )
        {
            pwmRequest.respondWithError( PwmError.ERROR_INCORRECT_REQ_SEQUENCE.toInfo(), true );
            return ProcessStatus.Halt;
        }

        final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile( pwmRequest );
        final long minWaitTime = newUserProfile.readSettingAsLong( PwmSetting.NEWUSER_MINIMUM_WAIT_TIME ) * 1000L;
        final Instant completeTime = Instant.ofEpochMilli( startTime.toEpochMilli() + minWaitTime );

        final BigDecimal percentComplete;
        final boolean complete;

        // be sure minimum wait time has passed
        if ( Instant.now().isAfter( completeTime ) )
        {
            percentComplete = new BigDecimal( "100" );
            complete = true;
        }
        else
        {
            final TimeDuration elapsedTime = TimeDuration.fromCurrent( startTime );
            complete = false;
            percentComplete = Percent.of( elapsedTime.asMillis(), minWaitTime ).asBigDecimal();
        }

        final LinkedHashMap<String, Object> outputMap = new LinkedHashMap<>();
        outputMap.put( "percentComplete", percentComplete );
        outputMap.put( "complete", complete );

        final RestResultBean restResultBean = RestResultBean.withData( outputMap, Map.class );

        LOGGER.trace( pwmRequest, () -> "returning result for restCheckProgress: " + JsonFactory.get().serialize( restResultBean ) );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "agree" )
    public ProcessStatus handleAgree(
            final PwmRequest pwmRequest
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        LOGGER.debug( pwmRequest, () -> "user accepted new-user agreement" );

        final NewUserBean newUserBean = getNewUserBean( pwmRequest );
        newUserBean.setAgreementPassed( true );

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "reset" )
    public ProcessStatus handleReset(
            final PwmRequest pwmRequest
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        pwmRequest.getPwmDomain().getSessionStateService().clearBean( pwmRequest, NewUserBean.class );
        pwmRequest.getPwmResponse().sendRedirectToContinue();

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "complete" )
    public ProcessStatus handleComplete(
            final PwmRequest pwmRequest
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final NewUserBean newUserBean = getNewUserBean( pwmRequest );
        final Instant startTime = newUserBean.getCreateStartTime();
        if ( startTime == null )
        {
            pwmRequest.respondWithError( PwmError.ERROR_INCORRECT_REQ_SEQUENCE.toInfo(), true );
            return ProcessStatus.Halt;
        }

        final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile( pwmRequest );
        final long minWaitTime = newUserProfile.readSettingAsLong( PwmSetting.NEWUSER_MINIMUM_WAIT_TIME ) * 1000L;
        final Instant completeTime = Instant.ofEpochMilli( startTime.toEpochMilli() + minWaitTime );

        // be sure minimum wait time has passed
        if ( Instant.now().isBefore( completeTime ) )
        {
            pwmRequest.forwardToJsp( JspUrl.NEW_USER_WAIT );
            return ProcessStatus.Halt;
        }

        // -- process complete -- \\
        pwmRequest.getPwmDomain().getSessionStateService().clearBean( pwmRequest, NewUserBean.class );

        if ( pwmRequest.isAuthenticated() )
        {
            final PwmSession pwmSession = pwmRequest.getPwmSession();

            if ( AuthenticationFilter.forceRequiredRedirects( pwmRequest ) == ProcessStatus.Halt )
            {
                return ProcessStatus.Halt;
            }

            // log the user out if the current profiles states so
            final boolean forceLogoutOnChange = newUserProfile.readSettingAsBoolean( PwmSetting.NEWUSER_LOGOUT_AFTER_CREATION );
            if ( forceLogoutOnChange )
            {
                LOGGER.trace( pwmRequest, () -> "logging out user; account created" );
                pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.Logout );
                return ProcessStatus.Halt;
            }
        }

        final String configuredRedirectUrl = newUserProfile.readSettingAsString( PwmSetting.NEWUSER_REDIRECT_URL );
        if ( StringUtil.notEmpty( configuredRedirectUrl ) && StringUtil.isEmpty( pwmRequest.getPwmSession().getSessionStateBean().getForwardURL() ) )
        {
            final MacroRequest macroRequest = pwmRequest.getMacroMachine();
            final String macroedUrl = macroRequest.expandMacros( configuredRedirectUrl );
            pwmRequest.getPwmResponse().sendRedirect( macroedUrl );
            return ProcessStatus.Halt;
        }

        pwmRequest.getPwmResponse().forwardToSuccessPage( Message.Success_CreateUser );
        return ProcessStatus.Halt;
    }


    static List<FormConfiguration> getFormDefinition( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        final NewUserProfile profile = getNewUserProfile( pwmRequest );
        return profile.readSettingAsForm( PwmSetting.NEWUSER_FORM );
    }

    public static NewUserProfile getNewUserProfile( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        final ProfileID profileID = pwmRequest.getPwmDomain().getSessionStateService().getBean( pwmRequest, NewUserBean.class ).getProfileID();
        if ( profileID == null )
        {
            throw new IllegalStateException( "can not read new user profile until profile is selected" );
        }
        return pwmRequest.getDomainConfig().getNewUserProfiles().get( profileID );
    }

    private void forwardToWait( final PwmRequest pwmRequest, final NewUserProfile newUserProfile )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final long pauseSeconds = newUserProfile.readSettingAsLong( PwmSetting.NEWUSER_MINIMUM_WAIT_TIME );
        if ( pauseSeconds > 0 )
        {
            pwmRequest.forwardToJsp( JspUrl.NEW_USER_WAIT );
        }
        else
        {
            final String newUserServletUrl = pwmRequest.getBasePath() + PwmServletDefinition.NewUser.servletUrl();
            final String redirectUrl = PwmURL.appendAndEncodeUrlParameters(
                    newUserServletUrl,
                    Collections.singletonMap( PwmConstants.PARAM_ACTION_REQUEST, NewUserAction.complete.name() )
            );
            pwmRequest.getPwmResponse().sendRedirect( redirectUrl );
        }
    }


    static void forwardToEnterCode( final PwmRequest pwmRequest, final NewUserProfile newUserProfile, final NewUserBean newUserBean )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final TokenDestinationItem tokenDestinationItem = NewUserUtils.tokenDestinationItemForCurrentValidation(
                pwmRequest,
                newUserBean,
                newUserProfile ).orElseThrow();
        pwmRequest.setAttribute( PwmRequestAttribute.TokenDestItems, tokenDestinationItem );
        pwmRequest.forwardToJsp( JspUrl.NEW_USER_ENTER_CODE );
    }

    private void forwardToFormPage( final PwmRequest pwmRequest, final NewUserBean newUserBean )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final List<FormConfiguration> formConfigurations = getFormDefinition( pwmRequest );
        final NewUserProfile newUserProfile = getNewUserProfile( pwmRequest );
        final boolean promptForPassword = newUserProfile.readSettingAsBoolean( PwmSetting.NEWUSER_PROMPT_FOR_PASSWORD );
        final Map<FormConfiguration, String> formData = new HashMap<>();
        if ( newUserBean.getRemoteInputData() != null )
        {
            final Map<String, String> remoteData = newUserBean.getRemoteInputData();
            for ( final FormConfiguration formConfiguration : formConfigurations )
            {
                if ( remoteData.containsKey( formConfiguration.getName() ) )
                {
                    formData.put( formConfiguration, remoteData.get( formConfiguration.getName() ) );
                }
            }
        }

        pwmRequest.addFormInfoToRequestAttr( formConfigurations, formData, false, promptForPassword );

        {
            final boolean showBack = !newUserBean.isUrlSpecifiedProfile()
                    && pwmRequest.getDomainConfig().getNewUserProfiles().size() > 1;
            pwmRequest.setAttribute( PwmRequestAttribute.NewUser_FormShowBackButton, showBack );
        }

        pwmRequest.forwardToJsp( JspUrl.NEW_USER );
    }
}
