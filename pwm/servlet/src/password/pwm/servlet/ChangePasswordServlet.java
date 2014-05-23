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
import com.novell.ldapchai.impl.oracleds.entry.OracleDSEntries;
import com.novell.ldapchai.provider.ChaiProvider;
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
import password.pwm.util.*;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * User interaction servlet for changing (self) passwords.
 *
 * @author Jason D. Rivard.
 */
public class ChangePasswordServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ChangePasswordServlet.class);


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

        if (!pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.CHANGE_PASSWORD)) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        try {
            checkMinimumLifetime(pwmApplication,pwmSession,cpb,pwmSession.getUserInfoBean());
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInformation = e.getErrorInformation();
            LOGGER.error(pwmSession,errorInformation.toDebugStr());
            ssBean.setSessionError(errorInformation);
            ServletHelper.forwardToErrorPage(req,resp,false);
        }

        if (processRequestParam != null && processRequestParam.length() > 0) {
            Validator.validatePwmFormID(req);
            if ("checkProgress".equalsIgnoreCase(processRequestParam)) {
                restCheckProgress(pwmApplication, pwmSession, cpb, resp);
                return;
            } else if ("complete".equalsIgnoreCase(processRequestParam)) {
                handleComplete(pwmApplication, pwmSession, cpb, req, resp);
                return;
            }

            if ("change".equalsIgnoreCase(processRequestParam)) {
                this.handleChangeRequest(pwmApplication, pwmSession, req, resp);
            } else if ("form".equalsIgnoreCase(processRequestParam)) {
                this.handleFormRequest(pwmApplication, pwmSession, req, resp);
            } else if ("agree".equalsIgnoreCase(processRequestParam)) {
                LOGGER.debug(pwmSession, "user accepted password change agreement");
                cpb.setAgreementPassed(true);
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
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.PASSWORD_CHANGE);
            return;
        }

        //make sure the two passwords match
        boolean caseSensitive = pwmSession.getUserInfoBean().getPasswordPolicy().getRuleHelper().readBooleanValue(
                PwmPasswordRule.CaseSensitive);
        if (PasswordUtility.PasswordCheckInfo.MATCH_STATUS.MATCH != PasswordUtility.figureMatchStatus(caseSensitive,
                password1, password2)) {
            ssBean.setSessionError(new ErrorInformation(PwmError.PASSWORD_DOESNOTMATCH));
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.PASSWORD_CHANGE);
            return;
        }

        try {
            executeChangePassword(pwmApplication, pwmSession, password1);
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.PASSWORD_CHANGE_WAIT);
        } catch (PwmOperationalException e) {
            LOGGER.debug(e.getErrorInformation().toDebugStr());
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(PwmConstants.URL_SERVLET_CHANGE_PASSWORD, req, resp));
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
                ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.PASSWORD_FORM);
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
                ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.PASSWORD_FORM);
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
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.PASSWORD_FORM);
            return;
        }

        advancedToNextStage(req, resp);
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

        // change password
        PasswordUtility.setUserPassword(pwmSession, pwmApplication, newPassword);

        //init values for progress screen
        {
            final PasswordChangeProgressChecker.ProgressTracker tracker = new PasswordChangeProgressChecker.ProgressTracker();
            final PasswordChangeProgressChecker checker = new PasswordChangeProgressChecker(pwmApplication,pwmSession,null);
            cpb.setChangeProgressTracker(tracker);
            cpb.setChangePasswordMaxCompletion(checker.maxCompletionTime(tracker));
        }

        // send user an email confirmation
        sendChangePasswordEmailNotice(pwmSession, pwmApplication);

        // send audit event
        pwmApplication.getAuditManager().submit(AuditEvent.CHANGE_PASSWORD, pwmSession.getUserInfoBean(), pwmSession);
    }

    private void advancedToNextStage(final HttpServletRequest req, final HttpServletResponse resp)
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final Configuration config = pwmApplication.getConfig();
        final ChangePasswordBean cpb = pwmSession.getChangePasswordBean();

        if (cpb.getChangeProgressTracker() != null) {
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.PASSWORD_CHANGE_WAIT);
            return;
        }

        if (pwmSession.getUserInfoBean().getPasswordState().isWarnPeriod()) {
            if (!pwmSession.getUserInfoBean().isRequiresNewPassword()) {

            }
        }


        final String agreementMsg = pwmApplication.getConfig().readSettingAsLocalizedString(PwmSetting.PASSWORD_CHANGE_AGREEMENT_MESSAGE, pwmSession.getSessionStateBean().getLocale());
        if (agreementMsg != null && agreementMsg.length() > 0 && !cpb.isAgreementPassed()) {
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.PASSWORD_AGREEMENT);
            return;
        }

        if (determineIfCurrentPasswordRequired(pwmApplication,pwmSession) && !cpb.isCurrentPasswordPassed()) {
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.PASSWORD_FORM);
            return;
        }

        if (!config.readSettingAsForm(PwmSetting.PASSWORD_REQUIRE_FORM).isEmpty() && !cpb.isFormPassed()) {
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.PASSWORD_FORM);
            return;
        }

        cpb.setAllChecksPassed(true);
        ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.PASSWORD_CHANGE);
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

        pwmApplication.getEmailQueue().submitEmail(configuredEmailSetting, pwmSession.getUserInfoBean(),
                pwmSession.getSessionManager().getUserDataReader(pwmApplication));
    }

    private static void checkMinimumLifetime(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final ChangePasswordBean changePasswordBean,
            final UserInfoBean userInfoBean
    )
            throws PwmOperationalException, ChaiUnavailableException, PwmUnrecoverableException
    {
        if (changePasswordBean.isNextAllowedTimePassed()) {
            return;
        }

        // for oracle DS; this check is also handled in UserAuthenticator.
        if (ChaiProvider.DIRECTORY_VENDOR.ORACLE_DS == pwmSession.getSessionManager().getChaiProvider(pwmApplication).getDirectoryVendor()) {
            try {
                final String oracleDS_PrePasswordAllowChangeTime = pwmSession.getSessionManager().getActor(pwmApplication).readStringAttribute(
                        "passwordAllowChangeTime");
                if (oracleDS_PrePasswordAllowChangeTime != null && !oracleDS_PrePasswordAllowChangeTime.isEmpty()) {
                    final Date date = OracleDSEntries.convertZuluToDate(oracleDS_PrePasswordAllowChangeTime);
                    if (new Date().before(date)) {
                        LOGGER.debug("discovered oracleds allowed change time is set to: " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(date) + ", won't permit password change");
                        final String errorMsg = "change not permitted until " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(date);
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.PASSWORD_TOO_SOON, errorMsg);
                        throw new PwmUnrecoverableException(errorInformation);
                    }
                }
            } catch (ChaiOperationException e) {
                LOGGER.debug(pwmSession, "unexpected error reading OracleDS password allow modification time: " + e.getMessage());
            }
            changePasswordBean.setNextAllowedTimePassed(true);
            return;
        }

        final int minimumLifetime = userInfoBean.getPasswordPolicy().getRuleHelper().readIntValue(PwmPasswordRule.MinimumLifetime);
        if (minimumLifetime < 1) {
            return;
        }

        final Date lastModified = userInfoBean.getPasswordLastModifiedTime();
        if (lastModified == null || lastModified.after(new Date())) {
            LOGGER.debug(pwmSession, "skipping minimum lifetime check, password last set time is unknown");
            changePasswordBean.setNextAllowedTimePassed(true);
            return;
        }

        final TimeDuration passwordAge = TimeDuration.fromCurrent(lastModified);
        final boolean passwordTooSoon = passwordAge.getTotalSeconds() < minimumLifetime;
        if (!passwordTooSoon) {
            changePasswordBean.setNextAllowedTimePassed(true);
            return;
        }

        final PasswordStatus passwordStatus = userInfoBean.getPasswordState();
        if (passwordStatus.isExpired() || passwordStatus.isPreExpired() || passwordStatus.isWarnPeriod()) {
            LOGGER.debug(pwmSession, "current password is too young, but skipping enforcement of minimum lifetime check because current password is expired");
            changePasswordBean.setNextAllowedTimePassed(true);
            return;
        }

        final boolean enforceFromForgotten = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.CHALLENGE_ENFORCE_MINIMUM_PASSWORD_LIFETIME);
        if (!enforceFromForgotten) {
            if (userInfoBean.isRequiresNewPassword()) {
                LOGGER.debug(pwmSession, "current password is too young, but skipping enforcement of minimum lifetime check because user authenticated with unknown password");
                changePasswordBean.setNextAllowedTimePassed(true);
                return;
            }
        }

        final Date allowedChangeDate = new Date(System.currentTimeMillis() + (minimumLifetime * 1000));
        final String errorMsg = "last password change is too recent, password cannot be changed until after " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(allowedChangeDate);
        final ErrorInformation errorInformation = new ErrorInformation(PwmError.PASSWORD_TOO_SOON,errorMsg);
        throw new PwmOperationalException(errorInformation);
    }

    private void restCheckProgress(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final ChangePasswordBean cpb,
            final HttpServletResponse resp
    )
            throws IOException
    {

        final PasswordChangeProgressChecker.ProgressTracker progressTracker = cpb.getChangeProgressTracker();
        final PasswordChangeProgressChecker.PasswordChangeProgress passwordChangeProgress;
        if (progressTracker == null) {
            passwordChangeProgress = PasswordChangeProgressChecker.PasswordChangeProgress.COMPLETE;
        } else {
            final PasswordChangeProgressChecker checker = new PasswordChangeProgressChecker(
                    pwmApplication,
                    pwmSession,
                    pwmSession.getSessionStateBean().getLocale()
            );
            passwordChangeProgress = checker.figureProgress(progressTracker);
        }
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(passwordChangeProgress);

        LOGGER.trace(pwmSession,"returning result for restCheckProgress: " + Helper.getGson().toJson(restResultBean));
        ServletHelper.outputJsonResult(resp,restResultBean);
    }


    private void handleComplete(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final ChangePasswordBean cpb,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PasswordChangeProgressChecker.ProgressTracker progressTracker = cpb.getChangeProgressTracker();
        boolean isComplete = true;
        if (progressTracker != null) {
            final PasswordChangeProgressChecker checker = new PasswordChangeProgressChecker(
                    pwmApplication,
                    pwmSession,
                    pwmSession.getSessionStateBean().getLocale()
            );
            final PasswordChangeProgressChecker.PasswordChangeProgress passwordChangeProgress = checker.figureProgress(progressTracker);
            isComplete = passwordChangeProgress.isComplete();
        }

        if (isComplete) {
            if (progressTracker != null) {
                final TimeDuration totalTime = TimeDuration.fromCurrent(progressTracker.getBeginTime());
                try {
                    pwmApplication.getStatisticsManager().updateAverageValue(Statistic.AVG_PASSWORD_SYNC_TIME,totalTime.getTotalMilliseconds());
                    LOGGER.trace(pwmSession,"password sync process marked completed (" + totalTime.asCompactString() + ")");
                } catch (Exception e) {
                    LOGGER.error(pwmSession,"unable to update average password sync time statistic: " + e.getMessage());
                }
            }
            cpb.setChangeProgressTracker(null);
            final Locale locale = pwmSession.getSessionStateBean().getLocale();
            final String completeMessage = pwmApplication.getConfig().readSettingAsLocalizedString(PwmSetting.PASSWORD_COMPLETE_MESSAGE,locale);
            if (completeMessage != null && !completeMessage.isEmpty()) {
                ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.PASSWORD_COMPLETE);
            } else {
                final SessionStateBean ssBean = pwmSession.getSessionStateBean();
                pwmSession.clearSessionBean(ChangePasswordBean.class);
                ssBean.setSessionSuccess(Message.SUCCESS_PASSWORDCHANGE, null);
                ServletHelper.forwardToSuccessPage(req,resp);
            }
        } else {
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.PASSWORD_CHANGE_WAIT);
        }
    }
}
