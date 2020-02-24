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

package password.pwm.http.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import net.glxn.qrgen.QRCode;
import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.ForceSetupPolicy;
import password.pwm.config.profile.SetupOtpProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.SetupOtpBean;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.svc.PwmService;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.event.UserAuditRecord;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.Validator;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.operations.OtpService;
import password.pwm.util.operations.otp.OTPUserRecord;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User interaction servlet for setting up OTP secret.
 *
 * @author Jason D. Rivard, Menno Pieters
 */
@WebServlet(
        name = "SetupOtpServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/setup-otp",
                PwmConstants.URL_PREFIX_PRIVATE + "/SetupOtp"
        }
)
public class SetupOtpServlet extends ControlledPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SetupOtpServlet.class );

    public enum SetupOtpAction implements AbstractPwmServlet.ProcessAction
    {
        clearOtp( HttpMethod.POST ),
        testOtpSecret( HttpMethod.POST ),
        toggleSeen( HttpMethod.POST ),
        restValidateCode( HttpMethod.POST ),
        complete( HttpMethod.POST ),
        skip( HttpMethod.POST ),;

        private final HttpMethod method;

        SetupOtpAction( final HttpMethod method )
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( method );
        }
    }

    public Class<? extends ProcessAction> getProcessActionsClass( )
    {
        return SetupOtpAction.class;
    }


    private SetupOtpBean getSetupOtpBean( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, SetupOtpBean.class );
    }

    public static SetupOtpProfile getSetupOtpProfile( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return pwmRequest.getPwmSession().getSessionManager().getSetupOTPProfile( );
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        // fetch the required beans / managers
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Configuration config = pwmApplication.getConfig();

        final SetupOtpProfile setupOtpProfile = getSetupOtpProfile( pwmRequest );
        if ( setupOtpProfile == null || !setupOtpProfile.readSettingAsBoolean( PwmSetting.OTP_ALLOW_SETUP ) )
        {
            final String errorMsg = "setup OTP is not enabled";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg );
            LOGGER.error( pwmRequest, errorInformation );
            pwmRequest.respondWithError( errorInformation );
            return ProcessStatus.Halt;
        }

        // check whether the setup can be stored
        if ( !canSetupOtpSecret( config ) )
        {
            LOGGER.error( pwmRequest, () -> "OTP Secret cannot be setup" );
            pwmRequest.respondWithError( PwmError.ERROR_INVALID_CONFIG.toInfo() );
            return ProcessStatus.Halt;
        }

        if ( pwmSession.getLoginInfoBean().getType() == AuthenticationType.AUTH_WITHOUT_PASSWORD )
        {
            LOGGER.error( pwmRequest, () -> "OTP Secret requires a password login" );
            throw new PwmUnrecoverableException( PwmError.ERROR_PASSWORD_REQUIRED );
        }

        final SetupOtpBean otpBean = getSetupOtpBean( pwmRequest );
        initializeBean( pwmRequest, otpBean );
        return ProcessStatus.Continue;
    }

    @Override
    protected void nextStep( final  PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final SetupOtpBean otpBean = getSetupOtpBean( pwmRequest );
        if ( otpBean.isHasPreExistingOtp() )
        {
            pwmRequest.forwardToJsp( JspUrl.SETUP_OTP_SECRET_EXISTING );
            return;
        }

        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        if ( otpBean.isConfirmed() )
        {
            final OtpService otpService = pwmApplication.getOtpService();
            final UserIdentity theUser = pwmSession.getUserInfo().getUserIdentity();
            try
            {
                otpService.writeOTPUserConfiguration(
                        pwmRequest,
                        theUser,
                        otpBean.getOtpUserRecord()
                );
                otpBean.setWritten( true );

                // Update the current user info bean, so the user can check the code right away
                pwmSession.reloadUserInfoBean( pwmRequest );

                // mark the event log
                final UserAuditRecord auditRecord = new AuditRecordFactory( pwmRequest ).createUserAuditRecord(
                        AuditEvent.SET_OTP_SECRET,
                        pwmSession.getUserInfo(),
                        pwmSession
                );
                pwmApplication.getAuditManager().submit( auditRecord );


                if ( pwmApplication.getStatisticsManager() != null && pwmApplication.getStatisticsManager().status() == PwmService.STATUS.OPEN )
                {
                    pwmApplication.getStatisticsManager().incrementValue( Statistic.SETUP_OTP_SECRET );
                }
            }
            catch ( final Exception e )
            {
                final ErrorInformation errorInformation;
                if ( e instanceof PwmException )
                {
                    errorInformation = ( ( PwmException ) e ).getErrorInformation();
                }
                else
                {
                    errorInformation = new ErrorInformation( PwmError.ERROR_WRITING_OTP_SECRET, "unexpected error saving otp secret: " + e.getMessage() );
                }
                LOGGER.error( pwmRequest, () -> errorInformation.toDebugStr() );
                setLastError( pwmRequest, errorInformation );
            }
        }

        if ( otpBean.isCodeSeen() )
        {
            if ( otpBean.isWritten() )
            {
                pwmRequest.forwardToJsp( JspUrl.SETUP_OTP_SECRET_SUCCESS );
            }
            else
            {
                pwmRequest.forwardToJsp( JspUrl.SETUP_OTP_SECRET_TEST );
            }
        }
        else
        {
            final boolean allowSkip = checkIfAllowedToSkipSetup( pwmRequest );
            final String qrCodeValue = makeQrCodeDataImageUrl( pwmRequest, otpBean.getOtpUserRecord() );
            pwmRequest.setAttribute(  PwmRequestAttribute.SetupOtp_UserRecord, otpBean.getOtpUserRecord() );
            pwmRequest.setAttribute( PwmRequestAttribute.SetupOtp_AllowSkip, allowSkip );
            pwmRequest.setAttribute( PwmRequestAttribute.SetupOtp_QrCodeValue, qrCodeValue );
            pwmRequest.forwardToJsp( JspUrl.SETUP_OTP_SECRET );
        }
    }


    @ActionHandler( action = "skip" )
    private ProcessStatus handleSkipRequest(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final boolean allowSkip = checkIfAllowedToSkipSetup( pwmRequest );

        if ( allowSkip )
        {
            pwmRequest.getPwmSession().getLoginInfoBean().getLoginFlags().add( LoginInfoBean.LoginFlag.skipOtp );
            pwmRequest.sendRedirectToContinue();
            return ProcessStatus.Halt;
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "complete" )
    private ProcessStatus handleComplete(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        pwmSession.getLoginInfoBean().setFlag( LoginInfoBean.LoginFlag.skipOtp );
        pwmRequest.getPwmApplication().getSessionStateService().clearBean( pwmRequest, SetupOtpBean.class );

        pwmRequest.sendRedirectToContinue();
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "restValidateCode" )
    private ProcessStatus handleRestValidateCode(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        final OTPUserRecord otpUserRecord = pwmSession.getUserInfo().getOtpUserRecord();
        final OtpService otpService = pwmApplication.getOtpService();

        final String bodyString = pwmRequest.readRequestBodyAsString();
        final Map<String, String> clientValues = JsonUtil.deserializeStringMap( bodyString );
        final String code = Validator.sanitizeInputValue( pwmApplication.getConfig(), clientValues.get( "code" ), 1024 );

        try
        {
            final boolean passed = otpService.validateToken(
                    pwmRequest.getLabel(),
                    pwmSession.getUserInfo().getUserIdentity(),
                    otpUserRecord,
                    code,
                    false
            );
            final RestResultBean restResultBean = RestResultBean.withData( passed );

            LOGGER.trace( pwmRequest, () -> "returning result for restValidateCode: " + JsonUtil.serialize( restResultBean ) );
            pwmRequest.outputJsonResult( restResultBean );
        }
        catch ( final PwmOperationalException e )
        {

            final String errorMsg = "error during otp code validation: " + e.getMessage();
            LOGGER.error( pwmRequest, () -> errorMsg );
            pwmRequest.outputJsonResult( RestResultBean.fromError( new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg ), pwmRequest ) );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "clearOtp" )
    private ProcessStatus handleClearOtpSecret(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final SetupOtpBean otpBean = getSetupOtpBean( pwmRequest );

        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final OtpService service = pwmApplication.getOtpService();
        final UserIdentity theUser = pwmSession.getUserInfo().getUserIdentity();
        try
        {
            service.clearOTPUserConfiguration( pwmRequest, theUser, pwmSession.getSessionManager().getActor( ) );
        }
        catch ( final PwmOperationalException e )
        {
            setLastError( pwmRequest, e.getErrorInformation() );
            LOGGER.error( pwmRequest, e.getErrorInformation() );
            return ProcessStatus.Halt;
        }

        otpBean.setHasPreExistingOtp( false );
        initializeBean( pwmRequest, otpBean );
        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "testOtpSecret" )
    private ProcessStatus handleTestOtpSecret(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final SetupOtpBean otpBean = getSetupOtpBean( pwmRequest );

        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        final String otpToken = pwmRequest.readParameterAsString( PwmConstants.PARAM_OTP_TOKEN );
        final OtpService otpService = pwmApplication.getOtpService();
        if ( otpToken != null && otpToken.length() > 0 )
        {
            try
            {
                if ( pwmRequest.getConfig().isDevDebugMode() )
                {
                    LOGGER.trace( pwmRequest, () -> "testing against otp record: " + JsonUtil.serialize( otpBean.getOtpUserRecord() ) );
                }

                if ( otpService.validateToken(
                        pwmRequest.getLabel(),
                        pwmSession.getUserInfo().getUserIdentity(),
                        otpBean.getOtpUserRecord(),
                        otpToken,
                        false
                ) )
                {
                    LOGGER.debug( pwmRequest, () -> "test OTP token returned true, valid OTP secret provided" );
                    otpBean.setConfirmed( true );
                    otpBean.setChallenge( null );
                }
                else
                {
                    LOGGER.debug( pwmRequest, () -> "test OTP token returned false, incorrect OTP secret provided" );
                    setLastError( pwmRequest, new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT ) );
                }
            }
            catch ( final PwmOperationalException e )
            {
                LOGGER.error( pwmRequest, () -> "error validating otp token: " + e.getMessage() );
                setLastError( pwmRequest, e.getErrorInformation() );
            }
        }

        return ProcessStatus.Continue;
    }

    private void initializeBean(
            final PwmRequest pwmRequest,
            final SetupOtpBean otpBean
    )
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        // has pre-existing, nothing to do.
        if ( otpBean.isHasPreExistingOtp() )
        {
            return;
        }

        final OtpService service = pwmApplication.getOtpService();
        final UserIdentity theUser = pwmSession.getUserInfo().getUserIdentity();

        // first time here
        if ( otpBean.getOtpUserRecord() == null )
        {

            final OTPUserRecord existingUserRecord;
            try
            {
                existingUserRecord = service.readOTPUserConfiguration( pwmRequest.getLabel(), theUser );
            }
            catch ( final ChaiUnavailableException e )
            {
                throw PwmUnrecoverableException.fromChaiException( e );
            }

            if ( existingUserRecord != null )
            {
                otpBean.setHasPreExistingOtp( true );
                LOGGER.trace( pwmRequest, () -> "user has existing otp record" );
                return;
            }
        }

        // make a new user record.
        if ( otpBean.getOtpUserRecord() == null )
        {
            try
            {
                final Configuration config = pwmApplication.getConfig();
                final SetupOtpProfile setupOtpProfile = getSetupOtpProfile( pwmRequest );
                final String identifierConfigValue = setupOtpProfile.readSettingAsString( PwmSetting.OTP_SECRET_IDENTIFIER );
                final String identifier = pwmSession.getSessionManager().getMacroMachine( ).expandMacros( identifierConfigValue );
                final OTPUserRecord otpUserRecord = new OTPUserRecord();
                final List<String> rawRecoveryCodes = pwmApplication.getOtpService().initializeUserRecord(
                        setupOtpProfile,
                        otpUserRecord,
                        pwmRequest.getLabel(),
                        identifier
                );
                otpBean.setOtpUserRecord( otpUserRecord );
                otpBean.setRecoveryCodes( rawRecoveryCodes );
                LOGGER.trace( pwmRequest, () -> "generated new otp record" );
                if ( config.isDevDebugMode() )
                {
                    LOGGER.trace( pwmRequest, () -> "newly generated otp record: " + JsonUtil.serialize( otpUserRecord ) );
                }
            }
            catch ( final Exception e )
            {
                final String errorMsg = "error setting up new OTP secret: " + e.getMessage();
                LOGGER.error( pwmRequest, () -> errorMsg );
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg ) );
            }
        }
    }

    @ActionHandler( action = "toggleSeen" )
    private ProcessStatus processToggleSeen( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final SetupOtpBean otpBean = getSetupOtpBean( pwmRequest );
        otpBean.setCodeSeen( !otpBean.isCodeSeen() );
        return ProcessStatus.Continue;
    }

    private boolean canSetupOtpSecret( final Configuration config )
    {
        /* TODO */
        return true;
    }

    private static String makeQrCodeDataImageUrl(
            final PwmRequest pwmRequest,
            final OTPUserRecord otpUserRecord
    )
            throws PwmUnrecoverableException
    {
        final String otpTypeValue = otpUserRecord.getType().toString().toLowerCase();
        final String identifier = StringUtil.urlEncode( otpUserRecord.getIdentifier() );
        final String secret = StringUtil.urlEncode( otpUserRecord.getSecret() );
        final String qrCodeContent = "otpauth://" + otpTypeValue
                + "/" + identifier
                + "?secret=" + secret;

        final int height = Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.OTP_QR_IMAGE_HEIGHT ) );
        final int width = Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.OTP_QR_IMAGE_WIDTH ) );

        final byte[] imageBytes;
        try
        {
            imageBytes = QRCode.from( qrCodeContent )
                    .withCharset( PwmConstants.DEFAULT_CHARSET.toString() )
                    .withSize( width, height )
                    .stream()
                    .toByteArray();
        }
        catch ( final Exception e )
        {
            final String errorMsg = "error generating qrcode image: " + e.getMessage() + ", payload length=" + qrCodeContent.length();
            LOGGER.error( pwmRequest, () -> errorMsg, e );
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg ) );
        }

        return "data:image/png;base64," + StringUtil.base64Encode( imageBytes );
    }

    private static boolean checkIfAllowedToSkipSetup( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        if ( pwmRequest.isForcedPageView() )
        {
            final SetupOtpProfile setupOtpProfile = getSetupOtpProfile( pwmRequest );
            final ForceSetupPolicy policy = setupOtpProfile.readSettingAsEnum( PwmSetting.OTP_FORCE_SETUP, ForceSetupPolicy.class );

            if ( policy == ForceSetupPolicy.FORCE_ALLOW_SKIP )
            {
                LOGGER.trace( pwmRequest, () -> "allowing setup skipping due to setting "
                        + PwmSetting.OTP_FORCE_SETUP.toMenuLocationDebug( setupOtpProfile.getIdentifier(), pwmRequest.getLocale() ) );
                return true;
            }

            final boolean admin = pwmRequest.getPwmSession().getSessionManager().checkPermission( pwmRequest.getPwmApplication(), Permission.PWMADMIN );
            if ( admin )
            {
                if ( pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.ADMIN_ALLOW_SKIP_FORCED_ACTIVITIES ) )
                {
                    LOGGER.trace( pwmRequest, () -> "allowing OTP setup skipping due to user being admin and setting "
                            + PwmSetting.ADMIN_ALLOW_SKIP_FORCED_ACTIVITIES.toMenuLocationDebug( null, pwmRequest.getLocale() ) );
                    return true;
                }
            }
        }

        return false;
    }
}
