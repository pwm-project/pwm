/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.http.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import net.glxn.qrgen.QRCode;
import password.pwm.*;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.ForceSetupPolicy;
import password.pwm.error.*;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.SetupOtpBean;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.svc.PwmService;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.UserAuditRecord;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.JsonUtil;
import password.pwm.util.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.operations.OtpService;
import password.pwm.util.otp.OTPUserRecord;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User interaction servlet for setting up OTP secret
 *
 * @author Jason D. Rivard, Menno Pieters
 */
@WebServlet(
        name="SetupOtpServlet",
        urlPatterns={
                PwmConstants.URL_PREFIX_PRIVATE + "/setup-otp",
                PwmConstants.URL_PREFIX_PRIVATE + "/SetupOtp"
        }
)
public class SetupOtpServlet extends AbstractPwmServlet {
    private static final PwmLogger LOGGER = PwmLogger.forClass(SetupOtpServlet.class);

    public enum SetupOtpAction implements AbstractPwmServlet.ProcessAction {
        clearOtp(HttpMethod.POST),
        testOtpSecret(HttpMethod.POST),
        toggleSeen(HttpMethod.POST),
        restValidateCode(HttpMethod.POST),
        complete(HttpMethod.POST),
        showQrImage(HttpMethod.GET),
        skip(HttpMethod.POST),

        ;

        private final HttpMethod method;

        SetupOtpAction(HttpMethod method)
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods()
        {
            return Collections.singletonList(method);
        }
    }

    protected SetupOtpAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        try {
            return SetupOtpAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
        // fetch the required beans / managers
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();
        final Configuration config = pwmApplication.getConfig();

        if (!config.readSettingAsBoolean(PwmSetting.OTP_ENABLED)) {
            final String errorMsg = "setup OTP Secret service is not enabled";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg);
            LOGGER.error(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        // check to see if the user is permitted to setup OTP
        if (!pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.SETUP_OTP_SECRET)) {
            final String errorMsg = String.format("user %s does not have permission to setup an OTP secret", uiBean.getUserIdentity());
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED, errorMsg);
            LOGGER.error(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        // check whether the setup can be stored
        if (!canSetupOtpSecret(config)) {
            LOGGER.error(pwmSession, "OTP Secret cannot be setup");
            pwmRequest.respondWithError(PwmError.ERROR_INVALID_CONFIG.toInfo());
            return;
        }

        if (pwmSession.getLoginInfoBean().getAuthenticationType() == AuthenticationType.AUTH_WITHOUT_PASSWORD) {
            LOGGER.error(pwmSession, "OTP Secret requires a password login");
            throw new PwmUnrecoverableException(PwmError.ERROR_PASSWORD_REQUIRED);
        }

        final SetupOtpBean otpBean = (SetupOtpBean) pwmSession.getSessionBean(SetupOtpBean.class);

        initializeBean(pwmRequest, otpBean);

        final SetupOtpAction action = readProcessAction(pwmRequest);
        if (action != null) {
            pwmRequest.validatePwmFormID();

            switch (action) {
                case clearOtp:
                    handleClearOtpSecret(pwmRequest, otpBean);
                    break;

                case testOtpSecret:
                    handleTestOtpSecret(pwmRequest, otpBean);
                    break;

                case toggleSeen:
                    otpBean.setCodeSeen(!otpBean.isCodeSeen());
                    break;

                case restValidateCode:
                    handleRestValidateCode(pwmRequest);
                    return;

                case complete:
                    handleComplete(pwmRequest);
                    return;

                case showQrImage:
                    handleQrImageRequest(pwmRequest, otpBean);
                    return;

                case skip: {
                    handleSkip(pwmRequest, otpBean);
                    return;
                }
            }
        }
        this.advanceToNextStage(pwmRequest, otpBean);
    }

    private void advanceToNextStage(
            final PwmRequest pwmRequest,
            final SetupOtpBean otpBean
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        if (otpBean.isHasPreExistingOtp()) {
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.SETUP_OTP_SECRET_EXISTING);
            return;
        }

        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        if (otpBean.isConfirmed()) {
            final OtpService otpService = pwmApplication.getOtpService();
            final UserIdentity theUser = pwmSession.getUserInfoBean().getUserIdentity();
            try {
                otpService.writeOTPUserConfiguration(
                        pwmSession,
                        theUser,
                        otpBean.getOtpUserRecord()
                );
                otpBean.setWritten(true);

                // mark the event log
                final UserAuditRecord auditRecord = pwmApplication.getAuditManager().createUserAuditRecord(
                        AuditEvent.SET_OTP_SECRET,
                        pwmSession.getUserInfoBean(),
                        pwmSession
                );
                pwmApplication.getAuditManager().submit(auditRecord);


                if (pwmApplication.getStatisticsManager() != null && pwmApplication.getStatisticsManager().status() == PwmService.STATUS.OPEN) {
                    pwmApplication.getStatisticsManager().incrementValue(Statistic.SETUP_OTP_SECRET);
                }
            } catch (Exception e) {
                final ErrorInformation errorInformation;
                if (e instanceof PwmException) {
                    errorInformation = ((PwmException) e).getErrorInformation();
                } else {
                    errorInformation = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET,"unexpected error saving otp secret: " + e.getMessage());
                }
                LOGGER.error(pwmSession, errorInformation.toDebugStr());
                pwmRequest.setResponseError(errorInformation);
            }
        }

        if (otpBean.isCodeSeen()) {
            if (otpBean.isWritten()) {
                pwmRequest.forwardToJsp(PwmConstants.JSP_URL.SETUP_OTP_SECRET_SUCCESS);
            } else {
                pwmRequest.forwardToJsp(PwmConstants.JSP_URL.SETUP_OTP_SECRET_TEST);
            }
        } else {
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.SETUP_OTP_SECRET);
        }
    }


    private void handleSkip(
            final PwmRequest pwmRequest,
            final SetupOtpBean otpBean
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        boolean allowSkip = false;
        if (!pwmRequest.isForcedPageView()) {
            allowSkip = true;
        } else {
            final ForceSetupPolicy policy = pwmRequest.getConfig().readSettingAsEnum(PwmSetting.OTP_FORCE_SETUP, ForceSetupPolicy.class);
            if (policy == ForceSetupPolicy.FORCE_ALLOW_SKIP) {
                allowSkip = true;
            }
        }
        
        if (allowSkip) {
            pwmRequest.getPwmSession().getUserInfoBean().setRequiresOtpConfig(false);
            pwmRequest.sendRedirectToContinue();
            return;
        }
        
        this.advanceToNextStage(pwmRequest, otpBean);
    }

    private void handleComplete(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        pwmSession.getSessionStateBean().setSkippedOtpSetup(true);
        pwmSession.clearSessionBean(SetupOtpBean.class);

        pwmRequest.sendRedirectToContinue();
    }



    private void handleRestValidateCode(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        final OTPUserRecord otpUserRecord = pwmSession.getUserInfoBean().getOtpUserRecord();
        final OtpService otpService = pwmApplication.getOtpService();

        final String bodyString = pwmRequest.readRequestBodyAsString();
        final Map<String, String> clientValues = JsonUtil.deserializeStringMap(bodyString);
        final String code = Validator.sanitizeInputValue(pwmApplication.getConfig(), clientValues.get("code"), 1024);

        try {
            boolean passed = otpService.validateToken(
                    pwmSession,
                    pwmSession.getUserInfoBean().getUserIdentity(),
                    otpUserRecord,
                    code,
                    false
            );
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(passed);

            LOGGER.trace(pwmSession,"returning result for restValidateCode: " + JsonUtil.serialize(restResultBean));
            pwmRequest.outputJsonResult(restResultBean);
        } catch (PwmOperationalException e) {

            final String errorMsg = "error during otp code validation: " + e.getMessage();
            LOGGER.error(pwmSession, errorMsg);
            pwmRequest.outputJsonResult(RestResultBean.fromError(new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg),pwmRequest));
        }
    }

    private void handleClearOtpSecret(
            final PwmRequest pwmRequest,
            final SetupOtpBean otpBean
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final OtpService service = pwmApplication.getOtpService();
        final UserIdentity theUser = pwmSession.getUserInfoBean().getUserIdentity();
        try {
            service.clearOTPUserConfiguration(pwmSession, theUser);
        } catch (PwmOperationalException e) {
            pwmRequest.setResponseError(e.getErrorInformation());
            LOGGER.error(pwmRequest, e.getErrorInformation());
            return;
        }

        otpBean.setHasPreExistingOtp(false);
        initializeBean(pwmRequest, otpBean);
    }

    private void handleTestOtpSecret(
            final PwmRequest pwmRequest,
            final SetupOtpBean otpBean
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        final String otpToken = pwmRequest.readParameterAsString(PwmConstants.PARAM_OTP_TOKEN);
        final OtpService otpService = pwmApplication.getOtpService();
        if (otpToken != null && otpToken.length() > 0) {
            try {
                if (otpService.validateToken(
                        pwmSession,
                        pwmSession.getUserInfoBean().getUserIdentity(),
                        otpBean.getOtpUserRecord(),
                        otpToken,
                        false
                )) {
                    LOGGER.debug(pwmRequest, "request OTP token returned true, valid OTP secret provided");
                    otpBean.setConfirmed(true);
                    otpBean.setChallenge(null);
                } else {
                    LOGGER.debug(pwmRequest, "request OTP token returned false, incorrect OTP secret provided");
                            pwmRequest.setResponseError(new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE));
                }
            } catch (PwmOperationalException e) {
                LOGGER.error(pwmRequest, "error validating otp token: " + e.getMessage());
                pwmRequest.setResponseError(e.getErrorInformation());
            }
        }

        /* TODO: handle case to HOTP */
    }

    private void initializeBean(
            final PwmRequest pwmRequest,
            final SetupOtpBean otpBean
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        // has pre-existing, nothing to do.
        if (otpBean.isHasPreExistingOtp()) {
            return;
        }

        final OtpService service = pwmApplication.getOtpService();
        final UserIdentity theUser = pwmSession.getUserInfoBean().getUserIdentity();

        // first time here
        if (otpBean.getOtpUserRecord() == null) {
            final OTPUserRecord existingUserRecord = service.readOTPUserConfiguration(pwmRequest.getSessionLabel(),theUser);
            if (existingUserRecord != null) {
                otpBean.setHasPreExistingOtp(true);
                LOGGER.trace(pwmSession, "user has existing otp record");
                return;
            }
        }

        // make a new user record.
        if (otpBean.getOtpUserRecord() == null) {
            try {
                final Configuration config = pwmApplication.getConfig();
                final String identifierConfigValue = config.readSettingAsString(PwmSetting.OTP_SECRET_IDENTIFIER);
                final String identifier = pwmSession.getSessionManager().getMacroMachine(pwmApplication).expandMacros(identifierConfigValue);
                final OTPUserRecord otpUserRecord = new OTPUserRecord();
                final List<String> rawRecoveryCodes = pwmApplication.getOtpService().initializeUserRecord(
                        otpUserRecord,
                        pwmRequest.getSessionLabel(),
                        identifier
                );
                otpBean.setOtpUserRecord(otpUserRecord);
                otpBean.setRecoveryCodes(rawRecoveryCodes);
                LOGGER.trace(pwmSession, "generated new otp record");
            } catch (Exception e) {
                final String errorMsg = "error setting up new OTP secret: " + e.getMessage();
                LOGGER.error(pwmSession, errorMsg);
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg));
            }
        }
    }

    private boolean canSetupOtpSecret(Configuration config) {
        /* TODO */
        return true;
    }


    private void handleQrImageRequest(
            final PwmRequest pwmRequest,
            final SetupOtpBean setupOtpBean
    )
            throws IOException, PwmUnrecoverableException
    {
        final OTPUserRecord otpUserRecord = setupOtpBean.getOtpUserRecord();
        final String qrCodeContent;
        {
            final String otpTypeValue = otpUserRecord.getType().toString().toLowerCase();
            final String identifier = StringUtil.urlEncode(otpUserRecord.getIdentifier());
            final String secret = StringUtil.urlEncode(otpUserRecord.getSecret());
            qrCodeContent = "otpauth://" + otpTypeValue 
                    + "/" + identifier
                    + "?secret=" + secret;
            if (pwmRequest.getConfig().isDevDebugMode()) {
                LOGGER.trace(pwmRequest, "qrCodeContent: " + qrCodeContent);
            }
        }

        final int height = Integer.parseInt(pwmRequest.getConfig().readAppProperty(AppProperty.OTP_QR_IMAGE_HEIGHT));
        final int width = Integer.parseInt(pwmRequest.getConfig().readAppProperty(AppProperty.OTP_QR_IMAGE_WIDTH));

        final byte[] imageBytes;
        try {
            imageBytes = QRCode.from(qrCodeContent)
                    .withCharset(PwmConstants.DEFAULT_CHARSET.toString())
                    .withSize(width, height)
                    .stream()
                    .toByteArray();
        } catch (Exception e) {
            final String errorMsg = "error generating qrcode image: " + e.getMessage() + ", payload length=" + qrCodeContent.length();
            LOGGER.error(pwmRequest, errorMsg, e);
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg));
        }

        OutputStream outputStream = null;
        try {
            pwmRequest.getPwmResponse().setContentType(PwmConstants.ContentTypeValue.png);
            outputStream = pwmRequest.getPwmResponse().getOutputStream();
            outputStream.write(imageBytes);
            outputStream.flush();
        } catch (Exception e) {
            final String errorMsg = "error while sending qrcode image to http client: " + e.getMessage();
            LOGGER.error(pwmRequest, errorMsg);
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg));
        } finally {
            if (outputStream != null) {
                try { outputStream.close(); } catch (Exception e) { /*noop*/ }
            }
        }
    }
}
