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
package password.pwm.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.*;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.bean.servlet.SetupOtpBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.OtpService;
import password.pwm.util.otp.OTPUserConfiguration;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * User interaction servlet for setting up OTP secret
 *
 * @author Jason D. Rivard, Menno Pieters
 */
public class SetupOtpSecretServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(SetupOtpSecretServlet.class);

// -------------------------- OTHER METHODS --------------------------
    /**
     *
     * @param req
     * @param resp
     * @throws ServletException
     * @throws ChaiUnavailableException
     * @throws IOException
     * @throws PwmUnrecoverableException
     */
    @Override
    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp)
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException {
        LOGGER.trace(String.format("Enter: processRequest(%s,%s)", req, resp));
        // fetch the required beans / managers
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();
        final Configuration config = pwmApplication.getConfig();

        if (!config.readSettingAsBoolean(PwmSetting.OTP_ENABLED)) {
            LOGGER.error("Setup OTP Secret not enabled");
            PwmSession.getPwmSession(req).getSessionStateBean().setSessionError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        // check to see if the user is permitted to setup OTP
        if (!Permission.checkPermission(Permission.SETUP_OTP_SECRET, pwmSession, pwmApplication)) {
            LOGGER.error(String.format("User %s does not have permission to setup an OTP secret", uiBean.getUsername()));
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        // check whether the setup can be stored
        if (!canSetupOtpSecret(config)) {
            LOGGER.error("OTP Secret cannot be setup");
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_INVALID_CONFIG));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (pwmSession.getUserInfoBean().getAuthenticationType() == UserInfoBean.AuthenticationType.AUTH_WITHOUT_PASSWORD) {
            LOGGER.error("OTP Secret requires a password login");
            throw new PwmUnrecoverableException(PwmError.ERROR_PASSWORD_REQUIRED);
        }

        // read the action request parameter
        final String actionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);
        final String otpToken = Validator.readStringFromRequest(req, PwmConstants.PARAM_OTP_TOKEN);

        SetupOtpBean otpBean = (SetupOtpBean) pwmSession.getSessionBean(SetupOtpBean.class);

        // check if the locale has changed since first seen.
        if (pwmSession.getSessionStateBean().getLocale() != otpBean.getUserLocale()) {
            otpBean = (SetupOtpBean) pwmSession.getSessionBean(SetupOtpBean.class);
            otpBean.setUserLocale(pwmSession.getSessionStateBean().getLocale());
        }
        initializeBean(pwmSession, pwmApplication, otpBean, false);

        // check to see if the user has an OTP configuration
        if (otpBean.getOtp() != null && !otpBean.isCleared()) {
            if (!"clearOtp".equals(actionParam)) {
                LOGGER.info(String.format("Existing configuration found for %s", uiBean.getUsername()));
                ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.SETUP_OTP_SECRET_EXISTING);
                return;
            }
        }
        if (otpBean.getOtp() == null) {
            LOGGER.info(String.format("No existing configuration found for %s; generating new OTP secret", uiBean.getUsername()));
            initializeBean(pwmSession, pwmApplication, otpBean, true);
        }
        try {
            // check to see whether the configuration is complete
            LOGGER.debug("Checking to see whether the configuration is complete");
            if (otpBean.getOtp() != null && otpBean.isConfirmed()) {
                LOGGER.info("Setup of OTP secret complete.");
                handleSetupOtpSecret(req, resp, otpBean);
                return;
            }
            LOGGER.debug("Check for action parameter");
            // otherwise handle the action
            if (actionParam != null && actionParam.length() > 0) {
                LOGGER.debug(String.format("actionParam: %s", actionParam));
                Validator.validatePwmFormID(req);
                // handle the requested action.
                if ("clearOtp".equalsIgnoreCase(actionParam)) {
                    handleClearOtpSecret(pwmSession, pwmApplication, req, resp, otpBean);
                    return;
                } else if ("setOtpSecret".equalsIgnoreCase(actionParam)) {
                    if (otpToken != null && otpToken.length() > 0) {
                        LOGGER.debug(String.format("Received OTP token: %s", otpToken));
                        if (otpBean.validateToken(otpToken)) {
                            LOGGER.info("Correct OTP secret");
                            otpBean.setConfirmed(true);
                            otpBean.setChallenge(null);
                            handleSetupOtpSecret(req, resp, otpBean);
                            return;
                        } else {
                            LOGGER.warn("Wrong OTP secret");
                            pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE));
                        }
                    }
                    /* TODO: handle case to HOTP */
                    ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.SETUP_OTP_SECRET_TEST);
                    return;
                } else if ("testOtpSecret".equalsIgnoreCase(actionParam)) {
                    /* TODO: handle case to HOTP */
                    ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.SETUP_OTP_SECRET_TEST);
                    return;
                }
            }
        } catch (PwmOperationalException ex) {
            LOGGER.error(pwmSession, ex.getMessage(), ex);
            ssBean.setSessionError(ex.getErrorInformation());
        } catch (ChaiValidationException ex) {
            LOGGER.error(pwmSession, ex.getMessage(), ex);
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNKNOWN, ex.getMessage()));
        }
        LOGGER.warn("Not handled");
        try {
            handleSetupOtpSecret(req, resp, otpBean);
        } catch (PwmOperationalException ex) {
            LOGGER.error(pwmSession, ex.getMessage(), ex);
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, ex.getMessage()));
        } catch (ChaiValidationException ex) {
            LOGGER.error(pwmSession, ex.getMessage(), ex);
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, ex.getMessage()));
        }
    }

    private void handleClearOtpSecret(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final SetupOtpBean otpBean)
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException {
        otpBean.setOtp(null);
        otpBean.setCleared(true);
        initializeBean(pwmSession, pwmApplication, otpBean, true);
        otpBean.setConfirmed(false);
        ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.SETUP_OTP_SECRET);
    }

    private void handleSetupOtpSecret(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final SetupOtpBean otpBean)
            throws PwmOperationalException, PwmUnrecoverableException, ChaiUnavailableException, ChaiValidationException, IOException, ServletException {
        if (otpBean.isConfirmed()) {
            final PwmSession pwmSession = PwmSession.getPwmSession(req);
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
            final OtpService otpService = pwmApplication.getOtpService();
            final UserIdentity theUser = pwmSession.getUserInfoBean().getUserIdentity();
            final String userGUID = pwmSession.getUserInfoBean().getUserGuid();
            otpService.writeOTPUserConfiguration(theUser, userGUID, otpBean.getOtp());
            pwmSession.getUserInfoBean().setRequiresOtpConfig(false);
            pwmSession.clearSessionBean(SetupOtpBean.class);
            ServletHelper.forwardToSuccessPage(req, resp);
            return;
        }
        ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.SETUP_OTP_SECRET);
    }

    private void initializeBean(final PwmSession pwmSession, final PwmApplication pwmApplication, final SetupOtpBean otpBean, final boolean newOtp) throws PwmUnrecoverableException {
        LOGGER.trace(String.format("Enter: initializeBean(%s, %s, %s, %s)", pwmSession, pwmApplication, otpBean, newOtp));
        if (newOtp || !otpBean.hasValidOtp()) {
            try {
                final OtpService service = pwmApplication.getOtpService();
                final UserIdentity theUser = pwmSession.getUserInfoBean().getUserIdentity();
                if (!newOtp) {
                    otpBean.setOtp(service.readOTPUserConfiguration(theUser));
                }
                if (otpBean.getOtp() == null && newOtp) { // setup OTP
                    LOGGER.info("Setting up new OTP secret.");
                    UserInfoBean uibean = pwmSession.getUserInfoBean();
                    String user = uibean.getUsername();
                    String hostname;
                    try {
                        URL url = new URL(pwmApplication.getSiteURL());
                        hostname = url.getHost();
                    } catch (MalformedURLException e) {
                        LOGGER.error("Malformed URL, not using hostname for identifier", e);
                        hostname = "";
                    }
                    String identifier = user + ((hostname != null && hostname.length() > 0) ? ("@" + hostname) : "");
                    OTPUserConfiguration otp = OTPUserConfiguration.getInstance(identifier, false, (service.supportsRecoveryCodes())?PwmConstants.OTP_RECOVERY_TOKEN_COUNT:0);
                    otpBean.setOtp(otp);
                    otpBean.setCleared(true);
                }
            } catch (NoSuchAlgorithmException ex) {
                LOGGER.error(ex.getMessage(), ex);
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, ex.getLocalizedMessage()));
            } catch (InvalidKeyException ex) {
                LOGGER.error(ex.getMessage(), ex);
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, ex.getLocalizedMessage()));
            } catch (ChaiUnavailableException ex) {
                LOGGER.error(ex.getMessage(), ex);
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, ex.getLocalizedMessage()));
            }
        } else {
            LOGGER.info("OTP already set:" + otpBean.getOtp().getSecret());
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
}
