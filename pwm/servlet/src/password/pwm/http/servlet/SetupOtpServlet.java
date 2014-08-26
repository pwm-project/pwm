/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

import com.google.gson.reflect.TypeToken;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import net.glxn.qrgen.QRCode;
import net.glxn.qrgen.image.ImageType;
import password.pwm.*;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.OTPStorageFormat;
import password.pwm.error.*;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.SetupOtpBean;
import password.pwm.http.filter.SessionFilter;
import password.pwm.util.Helper;
import password.pwm.util.JsonUtil;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.OtpService;
import password.pwm.util.otp.OTPUserRecord;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.List;
import java.util.Map;

/**
 * User interaction servlet for setting up OTP secret
 *
 * @author Jason D. Rivard, Menno Pieters
 */
public class SetupOtpServlet extends TopServlet {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(SetupOtpServlet.class);

    @Override
    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
        // fetch the required beans / managers
        final PwmRequest pwmRequest = PwmRequest.forRequest(req, resp);
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();
        final Configuration config = pwmApplication.getConfig();

        if (!config.readSettingAsBoolean(PwmSetting.OTP_ENABLED)) {
            LOGGER.error(pwmSession, "setup OTP Secret not enabled");
            PwmSession.getPwmSession(req).getSessionStateBean().setSessionError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        // check to see if the user is permitted to setup OTP
        if (!pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.SETUP_OTP_SECRET)) {
            LOGGER.error(pwmSession, String.format("user %s does not have permission to setup an OTP secret", uiBean.getUserIdentity()));
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        // check whether the setup can be stored
        if (!canSetupOtpSecret(config)) {
            LOGGER.error(pwmSession, "OTP Secret cannot be setup");
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_INVALID_CONFIG));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (pwmSession.getUserInfoBean().getAuthenticationType() == UserInfoBean.AuthenticationType.AUTH_WITHOUT_PASSWORD) {
            LOGGER.error(pwmSession, "OTP Secret requires a password login");
            throw new PwmUnrecoverableException(PwmError.ERROR_PASSWORD_REQUIRED);
        }

        final SetupOtpBean otpBean = (SetupOtpBean) pwmSession.getSessionBean(SetupOtpBean.class);

        // read the action request parameter
        final String actionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);

        initializeBean(pwmSession, pwmApplication, otpBean);

        // process requested action
        if (actionParam != null && !actionParam.isEmpty()) {
            Validator.validatePwmFormID(req);
            // handle the requested action.

            if ("clearOtp".equalsIgnoreCase(actionParam)) {
                handleClearOtpSecret(pwmSession, pwmApplication, req, resp, otpBean);
            } else if ("testOtpSecret".equalsIgnoreCase(actionParam)) {
                handleTestOtpSecret(pwmApplication, pwmSession, req, resp, otpBean);
            } else if ("toggleSeen".equalsIgnoreCase(actionParam)) {
                otpBean.setCodeSeen(!otpBean.isCodeSeen());
                    /* TODO: handle case to HOTP */
            } else if ("restValidateCode".equalsIgnoreCase(actionParam)) {
                handleRestValidateCode(pwmRequest, otpBean);
                return;
            } else if ("complete".equalsIgnoreCase(actionParam)) {
                handleComplete(pwmSession, pwmApplication, req, resp, otpBean);
                return;
            } else if ("showQrImage".equalsIgnoreCase(actionParam)) {
                handleQrImageRequest(pwmSession, req, resp, otpBean);
                return;
            }
        }

        this.advanceToNextStage(pwmApplication, pwmSession, req, resp, otpBean);
    }

    private void advanceToNextStage(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final SetupOtpBean otpBean
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {

        if (otpBean.isHasPreExistingOtp()) {
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.SETUP_OTP_SECRET_EXISTING);
            return;
        }

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
                pwmSession.getSessionStateBean().setSessionError(errorInformation);
            }
        }

        if (otpBean.isCodeSeen()) {
            if (otpBean.isWritten()) {
                ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.SETUP_OTP_SECRET_SUCCESS);
            } else {
                ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.SETUP_OTP_SECRET_TEST);
            }
        } else {
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.SETUP_OTP_SECRET);
        }
    }


    private void handleComplete(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final SetupOtpBean otpBean
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {

        pwmSession.getUserInfoBean().setRequiresOtpConfig(false);
        pwmSession.clearSessionBean(SetupOtpBean.class);

        final String redirectURL = PwmConstants.URL_SERVLET_COMMAND + "?" + "processAction=continue&pwmFormID="
                + Helper.buildPwmFormID(pwmSession.getSessionStateBean());
        resp.sendRedirect(SessionFilter.rewriteRedirectURL(redirectURL, req, resp));
    }



    private void handleRestValidateCode(
            final PwmRequest pwmRequest,
            final SetupOtpBean otpBean
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        final OTPUserRecord otpUserRecord = pwmSession.getUserInfoBean().getOtpUserRecord();
        final OtpService otpService = pwmApplication.getOtpService();

        final String bodyString = pwmRequest.readRequestBody();
        final Map<String, String> clientValues = JsonUtil.getGson().fromJson(bodyString, new TypeToken<Map<String, String>>() {
        }.getType());
        final String code = Validator.sanatizeInputValue(pwmApplication.getConfig(), clientValues.get("code"),1024);

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

            LOGGER.trace(pwmSession,"returning result for restValidateCode: " + JsonUtil.getGson().toJson(restResultBean));
            pwmRequest.outputJsonResult(restResultBean);
        } catch (PwmOperationalException e) {

            final String errorMsg = "error during otp code validation: " + e.getMessage();
            LOGGER.error(pwmSession, errorMsg);
            pwmRequest.outputJsonResult(RestResultBean.fromError(new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg),pwmRequest));
        }
    }

    private void handleClearOtpSecret(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final SetupOtpBean otpBean
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final OtpService service = pwmApplication.getOtpService();
        final UserIdentity theUser = pwmSession.getUserInfoBean().getUserIdentity();
        try {
            service.clearOTPUserConfiguration(pwmSession, theUser);
        } catch (PwmOperationalException e) {
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            LOGGER.error(e.getErrorInformation().toDebugStr());
            return;
        }

        otpBean.setHasPreExistingOtp(false);
        initializeBean(pwmSession, pwmApplication, otpBean);
    }

    private void handleTestOtpSecret(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final SetupOtpBean otpBean
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final String otpToken = Validator.readStringFromRequest(req, PwmConstants.PARAM_OTP_TOKEN);
        final OtpService otpService = pwmApplication.getOtpService();
        if (otpToken != null && otpToken.length() > 0) {
            LOGGER.debug(pwmSession, String.format("received OTP token: %s", otpToken));
            try {
                if (otpService.validateToken(
                        pwmSession,
                        pwmSession.getUserInfoBean().getUserIdentity(),
                        otpBean.getOtpUserRecord(),
                        otpToken,
                        false
                )) {
                    LOGGER.info(pwmSession, "correct OTP secret");
                    otpBean.setConfirmed(true);
                    otpBean.setChallenge(null);
                } else {
                    LOGGER.warn(pwmSession, "wrong OTP secret");
                    pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE));
                }
            } catch (PwmOperationalException e) {
                LOGGER.error(pwmSession, "error validating otp token: " + e.getMessage());
                pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            }
        }

        /* TODO: handle case to HOTP */
    }

    private void initializeBean(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final SetupOtpBean otpBean
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {

        // has pre-existing, nothing to do.
        if (otpBean.isHasPreExistingOtp()) {
            return;
        }

        final OtpService service = pwmApplication.getOtpService();
        final UserIdentity theUser = pwmSession.getUserInfoBean().getUserIdentity();

        // first time here
        if (otpBean.getOtpUserRecord() == null) {
            final OTPUserRecord existingUserRecord = service.readOTPUserConfiguration(theUser);
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
                final OTPStorageFormat format = config.readSettingAsEnum(PwmSetting.OTP_SECRET_STORAGEFORMAT,OTPStorageFormat.class);
                final List<String> rawRecoveryCodes = OtpService.initializeUserRecord(
                        otpUserRecord,
                        identifier,
                        OTPUserRecord.Type.TOTP,
                        format,
                        PwmConstants.OTP_RECOVERY_TOKEN_COUNT
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

    private static class AjaxValidationBean {

        final private int version = 1;
        private String message;
        private boolean success;

        private AjaxValidationBean(String message, boolean success) {
            this.message = message;
            this.success = success;
        }

        public int getVersion() {
            return version;
        }

        public String getMessage() {
            return message;
        }

        public boolean isSuccess() {
            return success;
        }
    }

    private void handleQrImageRequest(
            final PwmSession pwmSession,
            final HttpServletRequest request,
            final HttpServletResponse response,
            final SetupOtpBean setupOtpBean
    )
            throws IOException
    {
        final OTPUserRecord otp = setupOtpBean.getOtpUserRecord();
        final String otptype = otp.getType().toString();
        final String content = String.format("otpauth://%s/%s?secret=%s", otptype.toLowerCase(), otp.getIdentifier(),
                otp.getSecret());
        if (content == null || content.length() == 0) {
            LOGGER.error(pwmSession, "unable to produce qrcode image, missing content parameter");
        }

        int height = 200;
        int width = 200;
        try {
            width = Integer.parseInt(request.getParameter("width"));
        } catch (Exception e) {
            LOGGER.error(pwmSession, "error parsing width parameter: " + e.getMessage());
        }
        try {
            height = Integer.parseInt(request.getParameter("height"));
        } catch (NumberFormatException e) {
            LOGGER.error(pwmSession, "error parsing height parameter: " + e.getMessage());
        }

        final QRCode code = QRCode.from(URLDecoder.decode(content, "UTF-8")).withCharset("UTF-8").withSize(width, height);
        response.setContentType("image/png");
        code.to(ImageType.PNG).writeTo(response.getOutputStream());
        response.getOutputStream().close();
    }
}
