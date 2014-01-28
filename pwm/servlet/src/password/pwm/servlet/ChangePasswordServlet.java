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

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.*;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.PasswordStatus;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.bean.servlet.ChangePasswordBean;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmPasswordRule;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.event.AuditEvent;
import password.pwm.i18n.Message;
import password.pwm.util.PwmLogger;
import password.pwm.util.PwmPasswordRuleValidator;
import password.pwm.util.ServletHelper;
import password.pwm.util.TimeDuration;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;

/**
 * User interaction servlet for changing (self) passwords.
 *
 * @author Jason D. Rivard.
 */
public class ChangePasswordServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ChangePasswordServlet.class);

// -------------------------- OTHER METHODS --------------------------

    public void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final ChangePasswordBean cpb = pwmSession.getChangePasswordBean();

        final String processRequestParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);

        if (pwmSession.getUserInfoBean().getAuthenticationType() == UserInfoBean.AuthenticationType.AUTH_WITHOUT_PASSWORD) {
            throw new PwmUnrecoverableException(PwmError.ERROR_PASSWORD_REQUIRED);
        }

        if (!ssBean.isAuthenticated()) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_AUTHENTICATION_REQUIRED));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (determineIfCurrentPasswordRequired(pwmApplication,pwmSession)) {
            cpb.setCurrentPasswordRequired(true);
        }

        if (!Permission.checkPermission(Permission.CHANGE_PASSWORD, pwmSession, pwmApplication)) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        try {
            checkMinimumLifetime(pwmApplication,pwmSession,pwmSession.getUserInfoBean());
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInformation = e.getErrorInformation();
            LOGGER.error(pwmSession,errorInformation.toDebugStr());
            ssBean.setSessionError(errorInformation);
            ServletHelper.forwardToErrorPage(req,resp,false);
        }

        if (processRequestParam != null && processRequestParam.length() > 0) {
            Validator.validatePwmFormID(req);
            if ("change".equalsIgnoreCase(processRequestParam)) {
                this.handleChangeRequest(pwmApplication, pwmSession, req, resp);
            } else if ("form".equalsIgnoreCase(processRequestParam)) {
                this.handleFormRequest(pwmApplication, pwmSession, req, resp);
            } else if ("agree".equalsIgnoreCase(processRequestParam)) {
                LOGGER.debug(pwmSession, "user accepted password change agreement");
                cpb.setAgreementPassed(true);
            } else if ("checkProgress".equalsIgnoreCase(processRequestParam)) {
                restCheckProgress(pwmApplication, pwmSession, req, resp);
                return;
            } else if ("complete".equalsIgnoreCase(processRequestParam)) {
                handleComplete(pwmApplication, pwmSession, req, resp);
                return;
            }
        }

        if (!resp.isCommitted()) {
            advancedToNextStage(req,resp);
        }
    }


    private void handleChangeRequest(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final ChangePasswordBean cpb = pwmSession.getChangePasswordBean();

        if (!cpb.isAllChecksPassed()) {
            this.advancedToNextStage(req,resp);
            return;
        }

        final String password1 = Validator.readStringFromRequest(req, "password1");
        final String password2 = Validator.readStringFromRequest(req, "password2");

        // check the password meets the requirements
        try {
            final PwmPasswordRuleValidator pwmPasswordRuleValidator = new PwmPasswordRuleValidator(pwmApplication,pwmSession.getUserInfoBean().getPasswordPolicy());
            pwmPasswordRuleValidator.testPassword(password1,null,pwmSession.getUserInfoBean(),pwmSession.getSessionManager().getActor(pwmApplication));
        } catch (PwmDataValidationException e) {
            ssBean.setSessionError(e.getErrorInformation());
            LOGGER.debug(pwmSession, "failed password validation check: " + e.getErrorInformation().toDebugStr());
            this.forwardToChangeJSP(req, resp);
            return;
        }

        //make sure the two passwords match
        boolean caseSensitive = pwmSession.getUserInfoBean().getPasswordPolicy().getRuleHelper().readBooleanValue(
                PwmPasswordRule.CaseSensitive);
        if (PasswordUtility.PasswordCheckInfo.MATCH_STATUS.MATCH != PasswordUtility.figureMatchStatus(caseSensitive,
                password1, password2)) {
            ssBean.setSessionError(new ErrorInformation(PwmError.PASSWORD_DOESNOTMATCH));
            this.forwardToChangeJSP(req, resp);
            return;
        }

        try {
            executeChangePassword(pwmApplication, pwmSession, password1);
            forwardToWaitPage(req, resp, this.getServletContext());
        } catch (PwmOperationalException e) {
            LOGGER.debug(e.getErrorInformation().toDebugStr());
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(PwmConstants.URL_SERVLET_CHANGE_PASSWORD, req, resp));
        }
    }

    private void forwardToChangeJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_PASSWORD_CHANGE).forward(req, resp);
    }

    public static void forwardToWaitPage(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final ServletContext theContext
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        try {
            final String url = SessionFilter.rewriteURL('/' + PwmConstants.URL_JSP_PASSWORD_CHANGE_WAIT, req, resp);
            theContext.getRequestDispatcher(url).forward(req, resp);
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unexpected error sending user to wait page: " + e.toString());
        }
    }

    private void handleFormRequest(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final ChangePasswordBean cpb = pwmSession.getChangePasswordBean();

        final String currentPassword = Validator.readStringFromRequest(req, "currentPassword");

        // check the current password
        if (cpb.isCurrentPasswordRequired() && pwmSession.getUserInfoBean().getUserCurrentPassword() != null) {
            if (currentPassword == null || currentPassword.length() < 1) {
                ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER));
                LOGGER.debug(pwmSession, "failed password validation check: currentPassword value is missing");
                forwardToFormJSP(req, resp);
                return;
            }

            final boolean passed;
            {
                final boolean caseSensitive = Boolean.parseBoolean(pwmSession.getUserInfoBean().getPasswordPolicy().getValue(PwmPasswordRule.CaseSensitive));
                final String storedPassword = pwmSession.getUserInfoBean().getUserCurrentPassword();
                passed = caseSensitive ? storedPassword.equals(currentPassword) : storedPassword.equalsIgnoreCase(currentPassword);
            }

            if (!passed) {
                ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_BAD_CURRENT_PASSWORD));
                pwmApplication.getIntruderManager().convenience().markUserIdentity(pwmSession.getUserInfoBean().getUserIdentity(), pwmSession);
                LOGGER.debug(pwmSession, "failed password validation check: currentPassword value is incorrect");
                forwardToFormJSP(req, resp);
                return;
            }
            cpb.setCurrentPasswordPassed(passed);
        }

        final List<FormConfiguration> formItem = pwmApplication.getConfig().readSettingAsForm(PwmSetting.PASSWORD_REQUIRE_FORM);

        try {
            //read the values from the request
            final Map<FormConfiguration,String> formValues = Validator.readFormValuesFromRequest(req, formItem, ssBean.getLocale());

            validateParamsAgainstLDAP(formValues, pwmSession, pwmSession.getSessionManager().getActor(pwmApplication));

            cpb.setFormPassed(true);
        } catch (PwmOperationalException e) {
            pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
            pwmApplication.getIntruderManager().convenience().markUserIdentity(pwmSession.getUserInfoBean().getUserIdentity(), pwmSession);
            ssBean.setSessionError(e.getErrorInformation());
            LOGGER.debug(pwmSession,e.getErrorInformation().toDebugStr());
            forwardToFormJSP(req, resp);
            return;
        }

        advancedToNextStage(req,resp);
    }

    private void executeChangePassword(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final String newPassword
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        // password accepted, setup change password
        final ChangePasswordBean cpb = pwmSession.getChangePasswordBean();

        PasswordUtility.setUserPassword(pwmSession, pwmApplication, newPassword);
        cpb.setChangeBeginTime(new Date());

        //figure maximum change time
        final long maxWaitSeconds = pwmApplication.getConfig().readSettingAsLong(PwmSetting.PASSWORD_SYNC_MAX_WAIT_TIME);
        final long futureMaxWaitTime = System.currentTimeMillis() + (maxWaitSeconds * 1000);
        cpb.setChangeLastEndTime(new Date(futureMaxWaitTime));

        // send user an email confirmation
        sendChangePasswordEmailNotice(pwmSession, pwmApplication);

        pwmApplication.getAuditManager().submit(AuditEvent.CHANGE_PASSWORD, pwmSession.getUserInfoBean(), pwmSession);
    }

    private void advancedToNextStage(final HttpServletRequest req, final HttpServletResponse resp)
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final Configuration config = pwmApplication.getConfig();
        final ChangePasswordBean cpb = pwmSession.getChangePasswordBean();

        final String agreementMsg = pwmApplication.getConfig().readSettingAsLocalizedString(PwmSetting.PASSWORD_CHANGE_AGREEMENT_MESSAGE, pwmSession.getSessionStateBean().getLocale());
        if (agreementMsg != null && agreementMsg.length() > 0 && !cpb.isAgreementPassed()) {
            forwardToAgreementJSP(req, resp);
            return;
        }

        if (determineIfCurrentPasswordRequired(pwmApplication,pwmSession) && !cpb.isCurrentPasswordPassed()) {
            forwardToFormJSP(req,resp);
            return;
        }

        if (!config.readSettingAsForm(PwmSetting.PASSWORD_REQUIRE_FORM).isEmpty() && !cpb.isFormPassed()) {
            forwardToFormJSP(req,resp);
            return;
        }

        cpb.setAllChecksPassed(true);

        forwardToChangeJSP(req,resp);
    }

    private void forwardToAgreementJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_PASSWORD_AGREEMENT).forward(req, resp);
    }

    private void forwardToFormJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_PASSWORD_FORM).forward(req, resp);
    }

    private static boolean determineIfCurrentPasswordRequired(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession)
    {
        final String stringValue = pwmApplication.getConfig().readSettingAsString(PwmSetting.PASSWORD_REQUIRE_CURRENT);
        REQUIRE_CURRENT_SETTING currentSetting;
        try {
            currentSetting = REQUIRE_CURRENT_SETTING.valueOf(stringValue);
        } catch (IllegalArgumentException e) {
            currentSetting = REQUIRE_CURRENT_SETTING.FALSE;
        }

        if (currentSetting == REQUIRE_CURRENT_SETTING.FALSE) {
            return false;
        }

        {
            final String currentPassword = pwmSession.getUserInfoBean().getUserCurrentPassword();
            if (currentPassword == null || currentPassword.length() < 1) {
                return false;
            }
        }

        if (currentSetting == REQUIRE_CURRENT_SETTING.TRUE) {
            return true;
        }

        final PasswordStatus passwordStatus = pwmSession.getUserInfoBean().getPasswordState();
        if (currentSetting == REQUIRE_CURRENT_SETTING.NOTEXPIRED && !passwordStatus.isExpired() && !passwordStatus.isPreExpired() && !passwordStatus.isViolatesPolicy() && !pwmSession.getUserInfoBean().isRequiresNewPassword()) {
            return true;
        }

        return false;
    }

// -------------------------- ENUMERATIONS --------------------------

    private enum REQUIRE_CURRENT_SETTING {
        TRUE, FALSE, NOTEXPIRED
    }

    public static void validateParamsAgainstLDAP(
            final Map<FormConfiguration, String> formValues,
            final PwmSession pwmSession,
            final ChaiUser theUser
    )
            throws ChaiUnavailableException, PwmDataValidationException
    {
        for (final FormConfiguration formItem : formValues.keySet()) {
            final String attrName = formItem.getName();
            final String value = formValues.get(formItem);
            try {
                if (!theUser.compareStringAttribute(attrName, value)) {
                    final String errorMsg = "incorrect value for '" + attrName + "'";
                    final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE, errorMsg, new String[]{attrName});
                    LOGGER.debug(pwmSession, errorInfo.toDebugStr());
                    throw new PwmDataValidationException(errorInfo);
                }
                LOGGER.trace(pwmSession, "successful validation of ldap value for '" + attrName + "'");
            } catch (ChaiOperationException e) {
                LOGGER.error(pwmSession, "error during param validation of '" + attrName + "', error: " + e.getMessage());
                throw new PwmDataValidationException(new ErrorInformation(PwmError.ERROR_INCORRECT_RESPONSE, "ldap error testing value for '" + attrName + "'", new String[]{attrName}));
            }
        }
    }

    private static void sendChangePasswordEmailNotice(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication
    ) throws PwmUnrecoverableException, ChaiUnavailableException {
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_CHANGEPASSWORD, locale);

        if (configuredEmailSetting == null) {
            LOGGER.debug(pwmSession, "skipping change password email for '" + pwmSession.getUserInfoBean().getUserIdentity() + "' no email configured");
            return;
        }

        pwmApplication.getEmailQueue().submit(configuredEmailSetting, pwmSession.getUserInfoBean(), pwmSession.getSessionManager().getUserDataReader(pwmApplication));
    }

    private static void checkMinimumLifetime(final PwmApplication pwmApplication, final PwmSession pwmSession, final UserInfoBean userInfoBean)
            throws PwmOperationalException
    {
        final int minimumLifetime = userInfoBean.getPasswordPolicy().getRuleHelper().readIntValue(PwmPasswordRule.MinimumLifetime);
        if (minimumLifetime < 1) {
            return;
        }

        final Date lastModified = userInfoBean.getPasswordLastModifiedTime();
        if (lastModified == null || lastModified.after(new Date())) {
            LOGGER.debug(pwmSession, "skipping minimum lifetime check, password last set time is unknown");
            return;
        }

        final TimeDuration passwordAge = TimeDuration.fromCurrent(lastModified);
        final boolean passwordTooSoon = passwordAge.getTotalSeconds() < minimumLifetime;
        if (!passwordTooSoon) {
            return;
        }

        final PasswordStatus passwordStatus = userInfoBean.getPasswordState();
        if (passwordStatus.isExpired() || passwordStatus.isPreExpired() || passwordStatus.isWarnPeriod()) {
            LOGGER.debug(pwmSession, "current password is too young, but skipping enforcement of minimum lifetime check because current password is expired");
            return;
        }

        final boolean enforceFromForgotten = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.CHALLENGE_ENFORCE_MINIMUM_PASSWORD_LIFETIME);
        if (!enforceFromForgotten) {
            if (userInfoBean.isRequiresNewPassword()) {
                LOGGER.debug(pwmSession, "current password is too young, but skipping enforcement of minimum lifetime check because user authenticated with unknown password");
                return;
            }
        }

        final Date allowedChangeDate = new Date(System.currentTimeMillis() + (minimumLifetime * 1000));
        final String errorMsg = "last password change is too recent, password cannot be changed until after " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(allowedChangeDate);
        final ErrorInformation errorInformation = new ErrorInformation(PwmError.PASSWORD_TOO_SOON,errorMsg);
        throw new PwmOperationalException(errorInformation);
    }

    public class PasswordChangeProgress implements Serializable {
        private boolean complete;
        private BigDecimal percentComplete;
        private Map<String,BigDecimal> itemCompletion;

        public PasswordChangeProgress(
                boolean complete,
                BigDecimal percentComplete,
                Map<String, BigDecimal> itemCompletion
        )
        {
            this.complete = complete;
            this.percentComplete = percentComplete;
            this.itemCompletion = itemCompletion;
        }

        public boolean isComplete()
        {
            return complete;
        }

        public BigDecimal getPercentComplete()
        {
            return percentComplete;
        }

        public Map<String, BigDecimal> getItemCompletion()
        {
            return itemCompletion;
        }
    }

    private void restCheckProgress(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException
    {
        final ChangePasswordBean cpb = pwmSession.getChangePasswordBean();
        final PasswordChangeProgress passwordChangeProgress = figureProgress(pwmApplication, cpb);
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(passwordChangeProgress);
        ServletHelper.outputJsonResult(resp,restResultBean);
    }

    private PasswordChangeProgress figureProgress(final PwmApplication pwmApplication, final ChangePasswordBean cpb) {
        final PasswordChangeProgress result;
        final TimeDuration minWait = new TimeDuration(pwmApplication.getConfig().readSettingAsLong(PwmSetting.PASSWORD_SYNC_MIN_WAIT_TIME) * 1000 * 2);
        //final TimeDuration maxWait = new TimeDuration(pwmApplication.getConfig().readSettingAsLong(PwmSetting.PASSWORD_SYNC_MAX_WAIT_TIME) * 1000);

        if (cpb.getChangeBeginTime() == null) {
            result = new PasswordChangeProgress(true,new BigDecimal("100"), Collections.<String,BigDecimal>emptyMap());
        } else {
            final Date changeTime = cpb.getChangeBeginTime();
            BigDecimal percentage;
            final boolean complete;
            final TimeDuration elapsedTime = TimeDuration.fromCurrent(cpb.getChangeBeginTime());
            if (TimeDuration.fromCurrent(changeTime).isLongerThan(minWait)) {
                percentage = new BigDecimal(100);
                complete = true;
            } else {
                final BigDecimal totalMs = new BigDecimal(minWait.getTotalMilliseconds());
                final BigDecimal elapsedMs = new BigDecimal(elapsedTime.getTotalMilliseconds());
                percentage = elapsedMs.divide(totalMs,MathContext.DECIMAL32).multiply(new BigDecimal("100"));
                percentage = percentage.round(MathContext.DECIMAL32);
                complete = false;
            }
            result = new PasswordChangeProgress(complete,percentage,Collections.<String,BigDecimal>emptyMap());
        }
        return result;
    }

    private void handleComplete(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final ChangePasswordBean cpb = pwmSession.getChangePasswordBean();
        final PasswordChangeProgress passwordChangeProgress = figureProgress(pwmApplication, cpb);
        if (passwordChangeProgress.isComplete()) {
            final SessionStateBean ssBean = pwmSession.getSessionStateBean();
            pwmSession.clearSessionBean(ChangePasswordBean.class);
            ssBean.setSessionSuccess(Message.SUCCESS_PASSWORDCHANGE, null);
            ServletHelper.forwardToSuccessPage(req,resp);
        } else {
            forwardToWaitPage(req, resp, this.getServletContext());
        }
    }
}
