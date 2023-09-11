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

package password.pwm.http.servlet.helpdesk;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiPasswordPolicyException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.HelpdeskUIMode;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.error.ErrorInformation;
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
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.http.servlet.helpdesk.data.HelpdeskCheckVerificationRequest;
import password.pwm.http.servlet.helpdesk.data.HelpdeskCheckVerificationResponse;
import password.pwm.http.servlet.helpdesk.data.HelpdeskTargetUserRequest;
import password.pwm.i18n.Message;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.cr.CrService;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.event.AuditServiceClient;
import password.pwm.svc.event.HelpdeskAuditRecord;
import password.pwm.svc.intruder.IntruderServiceClient;
import password.pwm.svc.otp.OtpService;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.user.UserInfo;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.password.PasswordUtility;
import password.pwm.util.password.RandomGeneratorConfig;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.RestCheckPasswordServer;
import password.pwm.ws.server.rest.RestRandomPasswordServer;
import password.pwm.ws.server.rest.RestSetPasswordServer;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


@WebServlet(
        name = "HelpdeskDetailServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/helpdesk/detail",
                PwmConstants.URL_PREFIX_PRIVATE + "/helpdesk/detail/",
        }
)
public class HelpdeskDetailServlet extends ControlledPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HelpdeskDetailServlet.class );

    @Override
    protected PwmLogger getLogger()
    {
        return LOGGER;
    }

    public enum HelpdeskDetailAction implements AbstractPwmServlet.ProcessAction
    {
        checkPassword( HttpMethod.POST ),
        setPassword( HttpMethod.POST ),
        randomPassword( HttpMethod.POST ),
        unlockIntruder( HttpMethod.POST ),
        clearOtpSecret( HttpMethod.POST ),
        clearResponses( HttpMethod.POST ),

        checkOptionalVerification( HttpMethod.POST ),
        validateOtpCode( HttpMethod.POST ),
        sendVerificationToken( HttpMethod.POST ),
        verifyVerificationToken( HttpMethod.POST ),
        validateAttributes( HttpMethod.POST ),
        executeAction( HttpMethod.POST ),
        deleteUser( HttpMethod.POST ),;

        private final HttpMethod method;

        HelpdeskDetailAction( final HttpMethod method )
        {
            this.method = method;
        }

        @Override
        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( method );
        }
    }

    @Override
    public Optional<Class<? extends ProcessAction>> getProcessActionsClass( )
    {
        return Optional.of( HelpdeskDetailServlet.HelpdeskDetailAction.class );
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        gotoDetailJSP( pwmRequest );
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        return null;
    }

    private static void gotoDetailJSP( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException
    {
        try
        {
            final UserIdentity targetIdentity = HelpdeskServlet.readUserKeyRequestParameter( pwmRequest );
            final String rawVerificationStr = pwmRequest.readParameterAsString( "verificationState", PwmHttpRequestWrapper.Flag.BypassValidation );
            final HelpdeskClientState state = HelpdeskClientState.fromClientString( pwmRequest, rawVerificationStr );
            final HelpdeskClientData helpdeskClientData = HelpdeskClientData.fromConfig( pwmRequest.getHelpdeskProfile(), pwmRequest.getLocale() );

            HelpdeskServletUtil.verifyIfRequiredVerificationPassed( pwmRequest, targetIdentity, state );

            final String outputUserKey = HelpdeskServletUtil.obfuscateUserIdentity( pwmRequest, targetIdentity );
            LOGGER.trace( pwmRequest,
                    () -> "helpdesk detail view request for user details of " + targetIdentity.toString() );

            final HelpdeskUserDetail helpdeskDetailInfoBean =
                    HelpdeskUserDetail.makeHelpdeskDetailInfo( pwmRequest, targetIdentity );

            HelpdeskServletUtil.submitAuditEvent( pwmRequest, targetIdentity, AuditEvent.HELPDESK_VIEW_DETAIL, null );

            StatisticsClient.incrementStat( pwmRequest, Statistic.HELPDESK_USER_LOOKUP );
            pwmRequest.setAttribute( PwmRequestAttribute.HelpdeskClientData, helpdeskClientData );
            pwmRequest.setAttribute( PwmRequestAttribute.HelpdeskUserKey, outputUserKey );
            pwmRequest.setAttribute( PwmRequestAttribute.HelpdeskDetailInfo, helpdeskDetailInfoBean );
            pwmRequest.forwardToJsp( JspUrl.HELPDESK_DETAIL );
        }
        catch ( final Exception e )
        {
            LOGGER.debug( pwmRequest, () -> "ignoring detail page view request for target identity "
                    + " reason: " + e.getMessage() );
            pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.Helpdesk );
        }
    }

    @ActionHandler( action = "checkPassword" )
    public ProcessStatus processCheckPasswordAction( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final HelpdeskPasswordOperationRequest passwordOperationRequest = pwmRequest.readBodyAsJsonObject( HelpdeskPasswordOperationRequest.class );

        final UserIdentity targetUser = HelpdeskServletUtil.readUserIdentity( pwmRequest, passwordOperationRequest.userKey() );

        final HelpdeskClientState verificationState = HelpdeskClientState.fromClientString( pwmRequest, passwordOperationRequest.verificationState() );
        HelpdeskServletUtil.verifyButtonEnabled( pwmRequest, targetUser, HelpdeskDetailButton.changePassword );
        HelpdeskServletUtil.verifyIfRequiredVerificationPassed( pwmRequest, targetUser, verificationState );

        final ChaiUser chaiUser = HelpdeskServletUtil.getChaiUser( pwmRequest, targetUser );
        final UserInfo userInfo = UserInfoFactory.newUserInfo(
                pwmRequest.getPwmApplication(),
                pwmRequest.getLabel(),
                pwmRequest.getLocale(),
                targetUser,
                chaiUser.getChaiProvider()
        );

        {
            final HelpdeskProfile helpdeskProfile = HelpdeskServlet.getHelpdeskProfile( pwmRequest );
            final HelpdeskUIMode mode = helpdeskProfile.readSettingAsEnum( PwmSetting.HELPDESK_SET_PASSWORD_MODE, HelpdeskUIMode.class );
            if ( mode == HelpdeskUIMode.none )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SECURITY_VIOLATION, "setting "
                        + PwmSetting.HELPDESK_SET_PASSWORD_MODE.toMenuLocationDebug( helpdeskProfile.getId(), pwmRequest.getLocale() )
                        + " must not be set to none" ) );
            }
        }

        final PasswordUtility.PasswordCheckInfo passwordCheckInfo = PasswordUtility.checkEnteredPassword(
                pwmRequest.getPwmRequestContext(),
                chaiUser,
                userInfo,
                null,
                PasswordData.forStringValue( passwordOperationRequest.password1() ),
                PasswordData.forStringValue( passwordOperationRequest.password2() )
        );

        final RestCheckPasswordServer.JsonOutput jsonResponse = RestCheckPasswordServer.JsonOutput.fromPasswordCheckInfo( passwordCheckInfo );

        final RestResultBean<RestCheckPasswordServer.JsonOutput> restResultBean = RestResultBean.withData( jsonResponse, RestCheckPasswordServer.JsonOutput.class );
        pwmRequest.outputJsonResult( restResultBean );

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "setPassword" )
    public ProcessStatus processSetPasswordAction( final PwmRequest pwmRequest ) throws IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final HelpdeskProfile helpdeskProfile = pwmRequest.getHelpdeskProfile( );

        final RestSetPasswordServer.JsonInputData jsonInput =
                pwmRequest.readBodyAsJsonObject( RestSetPasswordServer.JsonInputData.class );

        final UserIdentity userIdentity = HelpdeskServletUtil.readUserIdentity( pwmRequest, jsonInput.username() );
        final ChaiUser chaiUser = HelpdeskServletUtil.getChaiUser( pwmRequest, userIdentity );
        final UserInfo userInfo = UserInfoFactory.newUserInfo(
                pwmRequest.getPwmApplication(),
                pwmRequest.getLabel(),
                pwmRequest.getLocale(),
                userIdentity,
                chaiUser.getChaiProvider()
        );

        HelpdeskServletUtil.checkIfUserIdentityViewable( pwmRequest, userIdentity );
        final HelpdeskUIMode mode = helpdeskProfile.readSettingAsEnum( PwmSetting.HELPDESK_SET_PASSWORD_MODE, HelpdeskUIMode.class );

        if ( mode == HelpdeskUIMode.none )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SECURITY_VIOLATION, "setting "
                    + PwmSetting.HELPDESK_SET_PASSWORD_MODE.toMenuLocationDebug( helpdeskProfile.getId(), pwmRequest.getLocale() )
                    + " must not be set to none" ) );
        }


        final PasswordData newPassword;
        if ( jsonInput.password() == null )
        {
            if ( mode != HelpdeskUIMode.random )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SECURITY_VIOLATION, "setting "
                        + PwmSetting.HELPDESK_SET_PASSWORD_MODE.toMenuLocationDebug( helpdeskProfile.getId(), pwmRequest.getLocale() )
                        + " is set to " + mode + " and no password is included in request" ) );
            }
            final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(
                    pwmRequest.getPwmDomain(),
                    pwmRequest.getLabel(),
                    userIdentity,
                    chaiUser );
            newPassword = PasswordUtility.generateRandom(
                    pwmRequest.getLabel(),
                    passwordPolicy,
                    pwmRequest.getPwmDomain()
            );
        }
        else
        {
            if ( mode == HelpdeskUIMode.random )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SECURITY_VIOLATION, "setting "
                        + PwmSetting.HELPDESK_SET_PASSWORD_MODE.toMenuLocationDebug( helpdeskProfile.getId(), pwmRequest.getLocale() )
                        + " is set to autogen yet a password is included in request" ) );
            }

            newPassword = new PasswordData( jsonInput.password() );
        }


        try
        {
            PasswordUtility.helpdeskSetUserPassword(
                    pwmRequest,
                    chaiUser,
                    userInfo,
                    pwmRequest.getPwmDomain(),
                    newPassword
            );
        }
        catch ( final PwmException e )
        {
            LOGGER.error( () -> "error during set password REST operation: " + e.getMessage() );
            pwmRequest.outputJsonResult( RestResultBean.fromError( e.getErrorInformation(), pwmRequest ) );
            return ProcessStatus.Halt;
        }

        pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_ChangedHelpdeskPassword, userInfo.getUsername() ) );
        return ProcessStatus.Halt;
    }

    private static <T extends HelpdeskTargetUserRequest> T helpdeskDetailActionReader(
            final PwmRequest pwmRequest, final Class<T> classOfT,
            final HelpdeskDetailButton button
    )
            throws PwmUnrecoverableException, IOException
    {
        final T actionRequest = pwmRequest.readBodyAsJsonObject( classOfT );
        final HelpdeskClientState verificationState = actionRequest.readVerificationState( pwmRequest );
        final UserIdentity targetUser = actionRequest.readTargetUser( pwmRequest );

        HelpdeskServletUtil.verifyButtonEnabled( pwmRequest, targetUser, button );
        HelpdeskServletUtil.checkIfRequiredVerificationPassed( pwmRequest, targetUser, verificationState );

        return actionRequest;
    }

    @ActionHandler( action = "clearOtpSecret" )
    public ProcessStatus restClearOtpSecret(
            final PwmRequest pwmRequest
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final HelpdeskDetailActionRequest actionRequest = helpdeskDetailActionReader(
                pwmRequest,
                HelpdeskDetailActionRequest.class,
                HelpdeskDetailButton.clearOtpSecret );

        final UserIdentity targetUser = actionRequest.readTargetUser( pwmRequest );

        //clear pwm intruder setting.
        IntruderServiceClient.clearUserIdentity( pwmRequest, targetUser );

        try
        {

            final OtpService service = pwmRequest.getPwmDomain().getOtpService();
            final ChaiUser chaiUser = HelpdeskServletUtil.getChaiUser( pwmRequest,  targetUser );
            service.clearOTPUserConfiguration( pwmRequest, targetUser, chaiUser );
            {
                // mark the event log
                final HelpdeskAuditRecord auditRecord = AuditRecordFactory.make( pwmRequest ).createHelpdeskAuditRecord(
                        AuditEvent.HELPDESK_CLEAR_OTP_SECRET,
                        pwmRequest.getPwmSession().getUserInfo().getUserIdentity(),
                        null,
                        targetUser,
                        pwmRequest.getLabel().getSourceAddress(),
                        pwmRequest.getLabel().getSourceHostname()
                );
                AuditServiceClient.submit( pwmRequest, auditRecord );
            }
        }
        catch ( final PwmOperationalException e )
        {
            final PwmError returnMsg = e.getError();
            final ErrorInformation error = new ErrorInformation( returnMsg, e.getMessage() );
            pwmRequest.respondWithError( error );
            LOGGER.warn( pwmRequest, () -> "error clearing OTP secret for user '" + targetUser + "'' " + error.toDebugStr() + ", " + e.getMessage() );
            return ProcessStatus.Halt;
        }

        final RestResultBean<?> restResultBean = RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "clearResponses" )
    public ProcessStatus restClearResponsesHandler( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException, ServletException, ChaiUnavailableException, PwmOperationalException
    {
        final HelpdeskDetailActionRequest actionRequest = helpdeskDetailActionReader(
                pwmRequest,
                HelpdeskDetailActionRequest.class,
                HelpdeskDetailButton.clearResponses );

        final UserIdentity targetUser = actionRequest.readTargetUser( pwmRequest );
        final ChaiUser chaiUser = HelpdeskServletUtil.getChaiUser( pwmRequest, targetUser );

        final CrService crService = pwmRequest.getPwmDomain().getCrService();
        crService.clearResponses(
                pwmRequest.getLabel(),
                targetUser,
                chaiUser
        );

        HelpdeskServletUtil.submitAuditEvent( pwmRequest, targetUser, AuditEvent.HELPDESK_CLEAR_RESPONSES, null );

        final RestResultBean<?> restResultBean = RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "unlockIntruder" )
    public ProcessStatus restUnlockIntruder(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final HelpdeskDetailActionRequest actionRequest = helpdeskDetailActionReader(
                pwmRequest,
                HelpdeskDetailActionRequest.class,
                HelpdeskDetailButton.unlock );

        final UserIdentity targetUser = actionRequest.readTargetUser( pwmRequest );

        //clear pwm intruder setting.
        IntruderServiceClient.clearUserIdentity( pwmRequest, targetUser );

        try
        {
            final ChaiUser chaiUser = HelpdeskServletUtil.getChaiUser( pwmRequest, targetUser );

            // send notice email
            HelpdeskServletUtil.sendUnlockNoticeEmail( pwmRequest, targetUser, chaiUser );

            chaiUser.unlockPassword();

            HelpdeskServletUtil.submitAuditEvent( pwmRequest, targetUser, AuditEvent.HELPDESK_UNLOCK_PASSWORD, null );
        }
        catch ( final ChaiPasswordPolicyException e )
        {
            final ChaiError passwordError = e.getErrorCode();
            final PwmError pwmError = PwmError.forChaiError( passwordError ).orElse( PwmError.PASSWORD_UNKNOWN_VALIDATION );
            pwmRequest.respondWithError( new ErrorInformation( pwmError ) );
            LOGGER.trace( pwmRequest, () -> "ChaiPasswordPolicyException was thrown while resetting password: " + e.getMessage() );
            return ProcessStatus.Halt;
        }
        catch ( final ChaiOperationException e )
        {
            final PwmError returnMsg = PwmError.forChaiError( e.getErrorCode() ).orElse( PwmError.ERROR_INTERNAL );
            final ErrorInformation error = new ErrorInformation( returnMsg, e.getMessage() );
            pwmRequest.respondWithError( error );
            LOGGER.warn( pwmRequest, () -> "error resetting password for user '"
                    + targetUser.toDisplayString()
                    + "'' " + error.toDebugStr() + ", " + e.getMessage() );
            return ProcessStatus.Halt;
        }

        final RestResultBean<?> restResultBean = RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "checkOptionalVerification" )
    public ProcessStatus restCheckOptionalVerification( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException, ServletException
    {
        final HelpdeskProfile helpdeskProfile = HelpdeskServlet.getHelpdeskProfile( pwmRequest );
        final var checkRequest = pwmRequest.readBodyAsJsonObject( HelpdeskCheckVerificationRequest.class );

        final UserIdentity userIdentity = HelpdeskServletUtil.readUserIdentity( pwmRequest, checkRequest.userKey() );
        HelpdeskServletUtil.checkIfUserIdentityViewable( pwmRequest, userIdentity );

        final String rawVerificationStr = checkRequest.verificationState();
        final HelpdeskClientState state = HelpdeskClientState.fromClientString( pwmRequest, rawVerificationStr );
        final boolean passed = HelpdeskServletUtil.checkIfRequiredVerificationPassed( pwmRequest, userIdentity, state );
        if ( !passed )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_SECURITY_VIOLATION, "attempt to request optional verification check when required verifications not passed" );
        }
        final HelpdeskVerificationOptions optionsBean = HelpdeskVerificationOptions.fromConfig( pwmRequest, helpdeskProfile, userIdentity );
        final HelpdeskCheckVerificationResponse responseBean = new HelpdeskCheckVerificationResponse( false,
                optionsBean );
        final RestResultBean<HelpdeskCheckVerificationResponse> restResultBean = RestResultBean.withData( responseBean, HelpdeskCheckVerificationResponse.class );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "randomPassword" )
    public ProcessStatus processRandomPasswordAction( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final RestRandomPasswordServer.JsonInput input = pwmRequest.readBodyAsJsonObject( RestRandomPasswordServer.JsonInput.class );
        final UserIdentity userIdentity = HelpdeskServletUtil.readUserIdentity( pwmRequest, input.username() );

        HelpdeskServletUtil.checkIfUserIdentityViewable( pwmRequest, userIdentity );

        final ChaiUser chaiUser = HelpdeskServletUtil.getChaiUser( pwmRequest, userIdentity );
        final UserInfo userInfo = UserInfoFactory.newUserInfo(
                pwmRequest.getPwmApplication(),
                pwmRequest.getLabel(),
                pwmRequest.getLocale(),
                userIdentity,
                chaiUser.getChaiProvider()
        );

        final RandomGeneratorConfig randomConfig = RandomGeneratorConfig.make( pwmRequest.getPwmDomain(), userInfo.getPasswordPolicy() );
        final PasswordData randomPassword = PasswordUtility.generateRandom( pwmRequest.getLabel(), randomConfig, pwmRequest.getPwmDomain() );
        final RestRandomPasswordServer.JsonOutput jsonOutput = new RestRandomPasswordServer.JsonOutput( randomPassword.getStringValue() );

        final RestResultBean<RestRandomPasswordServer.JsonOutput> restResultBean
                = RestResultBean.withData( jsonOutput, RestRandomPasswordServer.JsonOutput.class );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "executeAction" )
    public ProcessStatus processExecuteActionRequest(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException
    {
        final HelpdeskDetailActionRequest actionRequest = helpdeskDetailActionReader(
                pwmRequest,
                HelpdeskDetailActionRequest.class,
                null );

        final UserIdentity targetUserIdentity = actionRequest.readTargetUser( pwmRequest );

        final HelpdeskProfile helpdeskProfile = HelpdeskServlet.getHelpdeskProfile( pwmRequest );
        LOGGER.debug( pwmRequest, () -> "received executeAction request for user " + targetUserIdentity.toString() );

        final List<ActionConfiguration> actionConfigurations = helpdeskProfile.readSettingAsAction( PwmSetting.HELPDESK_ACTIONS );
        final String requestedName = JavaHelper.requireNonEmpty( actionRequest.actionName() );
        final ActionConfiguration requestedAction = actionConfigurations.stream()
                .filter( action -> action.getName().equals( requestedName ) )
                .findFirst()
                .orElseThrow( () ->
                {
                    final String errorMsg = "request to execute unknown action: " + requestedName;
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
                    return new PwmUnrecoverableException( errorInformation );
                } );

        // check if user should be seen by actor
        HelpdeskServletUtil.checkIfUserIdentityViewable( pwmRequest, targetUserIdentity );

        try
        {
            final ChaiUser chaiUser = HelpdeskServletUtil.getChaiUser( pwmRequest, targetUserIdentity );
            final MacroRequest macroRequest = HelpdeskServletUtil.getTargetUserMacroRequest( pwmRequest, targetUserIdentity );
            final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings( pwmRequest.getPwmDomain(), chaiUser )
                    .setExpandPwmMacros( true )
                    .setMacroMachine( macroRequest )
                    .createActionExecutor();

            actionExecutor.executeAction( requestedAction, pwmRequest.getLabel() );

            // mark the event log
            HelpdeskServletUtil.submitAuditEvent( pwmRequest, targetUserIdentity, AuditEvent.HELPDESK_ACTION, requestedAction.getName() );
            final RestResultBean<?> restResultBean = RestResultBean.forSuccessMessage(
                    pwmRequest.getLocale(),
                    pwmRequest.getDomainConfig(),
                    Message.Success_Action,
                    requestedAction.getName() );

            pwmRequest.outputJsonResult( restResultBean );
            return ProcessStatus.Halt;
        }
        catch ( final PwmOperationalException e )
        {
            LOGGER.error( pwmRequest, () -> e.getErrorInformation().toDebugStr() );
            final RestResultBean<?> restResultBean = RestResultBean.fromError( e.getErrorInformation(), pwmRequest );
            pwmRequest.outputJsonResult( restResultBean );
            return ProcessStatus.Halt;
        }
    }

    @ActionHandler( action = "deleteUser" )
    public ProcessStatus restDeleteUserRequest(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException
    {
        final HelpdeskDetailActionRequest actionRequest = helpdeskDetailActionReader(
                pwmRequest,
                HelpdeskDetailActionRequest.class,
                HelpdeskDetailButton.deleteUser );

        final UserIdentity targetUser = actionRequest.readTargetUser( pwmRequest );

        final HelpdeskProfile helpdeskProfile = HelpdeskServlet.getHelpdeskProfile( pwmRequest );
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        // read the userID for later logging.
        String userID = null;
        try
        {
            final UserInfo deletedUserInfo = UserInfoFactory.newUserInfoUsingProxy(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getLabel(),
                    targetUser,
                    pwmRequest.getLocale() );
            userID = deletedUserInfo.getUsername();
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.warn( pwmRequest, () -> "unable to read username of deleted user while creating audit record" );
        }

        // execute user delete operation
        final ChaiProvider provider = helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_USE_PROXY )
                ? pwmDomain.getProxyChaiProvider( pwmRequest.getLabel(), targetUser.getLdapProfileID() )
                : pwmRequest.getClientConnectionHolder().getActor().getChaiProvider();

        try
        {
            provider.deleteEntry( targetUser.getUserDN() );
        }
        catch ( final ChaiOperationException e )
        {
            final String errorMsg = "error while attempting to delete user " + targetUser + ", error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            LOGGER.debug( pwmRequest, () -> errorMsg );
            pwmRequest.outputJsonResult( RestResultBean.fromError( errorInformation, pwmRequest ) );
            return ProcessStatus.Halt;
        }

        // mark the event log
        {
            //normally the audit record builder reads the userID while constructing the record, but because the target user is already deleted,
            //it will be included here explicitly.
            final AuditRecordFactory.AuditUserDefinition auditUserDefinition = new AuditRecordFactory.AuditUserDefinition(
                    userID,
                    targetUser.getUserDN(),
                    targetUser.getLdapProfileID()
            );
            final HelpdeskAuditRecord auditRecord = AuditRecordFactory.make( pwmRequest ).createHelpdeskAuditRecord(
                    AuditEvent.HELPDESK_DELETE_USER,
                    pwmSession.getUserInfo().getUserIdentity(),
                    null,
                    auditUserDefinition,
                    pwmSession.getSessionStateBean().getSrcAddress(),
                    pwmSession.getSessionStateBean().getSrcHostname()
            );

            AuditServiceClient.submit( pwmRequest, auditRecord );
        }

        LOGGER.info( pwmRequest, () -> "user " + targetUser + " has been deleted" );

        final RestResultBean<?> restResultBean = RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "validateOtpCode" )
    public ProcessStatus restValidateOtpCodeRequest(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException, ServletException
    {
        final HelpdeskVerificationRequest actionRequest = helpdeskDetailActionReader(
                pwmRequest,
                HelpdeskVerificationRequest.class,
                HelpdeskDetailButton.verification );


        return HelpdeskServletUtil.validateOtpCodeImpl( pwmRequest, actionRequest );
    }

    @ActionHandler( action = "sendVerificationToken" )
    public ProcessStatus restSendVerificationTokenRequest(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final HelpdeskSendVerificationTokenRequest helpdeskVerificationRequest = helpdeskDetailActionReader(
                pwmRequest,
                HelpdeskSendVerificationTokenRequest.class,
                HelpdeskDetailButton.verification );

        HelpdeskServletUtil.sendVerificationTokenRequestImpl( pwmRequest, helpdeskVerificationRequest );

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "verifyVerificationToken" )
    public ProcessStatus restVerifyVerificationTokenRequest(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final HelpdeskVerificationRequest helpdeskVerificationRequest = helpdeskDetailActionReader(
                pwmRequest,
                HelpdeskVerificationRequest.class,
                HelpdeskDetailButton.verification );

        return HelpdeskServletUtil.verifyVerificationTokenRequestImpl( pwmRequest, helpdeskVerificationRequest );
    }

    @ActionHandler( action = "validateAttributes" )
    public ProcessStatus restValidateAttributes( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final HelpdeskVerificationRequest actionRequest = helpdeskDetailActionReader(
                pwmRequest,
                HelpdeskVerificationRequest.class,
                HelpdeskDetailButton.verification );

        return HelpdeskServletUtil.validateAttributesImpl( pwmRequest, actionRequest );
    }
}
