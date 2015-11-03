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

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.impl.oracleds.entry.OracleDSEntries;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.PasswordStatus;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.FormUtility;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.RequireCurrentPasswordMode;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.error.*;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.ChangePasswordBean;
import password.pwm.http.bean.LoginInfoBean;
import password.pwm.i18n.Message;
import password.pwm.ldap.PasswordChangeProgressChecker;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecord;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.JsonUtil;
import password.pwm.util.PasswordData;
import password.pwm.util.PwmPasswordRuleValidator;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.*;

/**
 * User interaction servlet for changing (self) passwords.
 *
 * @author Jason D. Rivard.
 */

@WebServlet(
        name="ChangePasswordServlet",
        urlPatterns={
                PwmConstants.URL_PREFIX_PRIVATE + "/changepassword",
                PwmConstants.URL_PREFIX_PUBLIC + "/changepassword",
                PwmConstants.URL_PREFIX_PRIVATE + "/ChangePassword",
                PwmConstants.URL_PREFIX_PUBLIC + "/ChangePassword"
        }
)
public class ChangePasswordServlet extends AbstractPwmServlet {

    private static final PwmLogger LOGGER = PwmLogger.forClass(ChangePasswordServlet.class);

    public enum ChangePasswordAction implements AbstractPwmServlet.ProcessAction {
        checkProgress(HttpMethod.POST),
        complete(HttpMethod.GET),
        change(HttpMethod.POST),
        form(HttpMethod.POST),
        agree(HttpMethod.POST),
        warnResponse(HttpMethod.POST),

        ;

        private final HttpMethod method;

        ChangePasswordAction(HttpMethod method)
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods()
        {
            return Collections.singletonList(method);
        }
    }

    protected ChangePasswordAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        try {
            return ChangePasswordAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void processAction(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final ChangePasswordBean changePasswordBean = pwmSession.getChangePasswordBean();

        if (pwmSession.getLoginInfoBean().getAuthenticationType() == AuthenticationType.AUTH_WITHOUT_PASSWORD) {
            throw new PwmUnrecoverableException(PwmError.ERROR_PASSWORD_REQUIRED);
        }

        if (!ssBean.isAuthenticated()) {
            pwmRequest.respondWithError(PwmError.ERROR_AUTHENTICATION_REQUIRED.toInfo());
            return;
        }

        if (determineIfCurrentPasswordRequired(pwmApplication,pwmSession)) {
            changePasswordBean.setCurrentPasswordRequired(true);
        }

        if (!pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.CHANGE_PASSWORD)) {
            pwmRequest.respondWithError(PwmError.ERROR_UNAUTHORIZED.toInfo());
            return;
        }

        try {
            checkMinimumLifetime(pwmApplication,pwmSession,changePasswordBean,pwmSession.getUserInfoBean());
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInformation = e.getErrorInformation();
            LOGGER.error(pwmSession, errorInformation.toDebugStr());
            pwmRequest.respondWithError(errorInformation);
        }

        final ChangePasswordAction action = readProcessAction(pwmRequest);
        if (action != null) {
            pwmRequest.validatePwmFormID();

            switch(action) {
                case checkProgress:
                    restCheckProgress(pwmRequest, changePasswordBean);
                    return;

                case complete:
                    handleComplete(pwmRequest, changePasswordBean);
                    return;

                case change:
                    handleChangeRequest(pwmRequest, changePasswordBean);
                    break;

                case form:
                    handleFormRequest(pwmRequest, changePasswordBean);
                    break;

                case warnResponse:
                    handleWarnResponseRequest(pwmRequest, changePasswordBean);
                    break;

                case agree:
                    handleAgreeRequest(pwmRequest, changePasswordBean);
            }
        }

        if (!pwmRequest.getPwmResponse().isCommitted()) {
            advancedToNextStage(pwmRequest, changePasswordBean);
        }
    }

    private void handleWarnResponseRequest(
            final PwmRequest pwmRequest,
            final ChangePasswordBean changePasswordBean
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        if (pwmRequest.getPwmSession().getUserInfoBean().getPasswordState().isWarnPeriod()) {
            final String warnResponse = pwmRequest.readParameterAsString("warnResponse");
            if ("skip".equalsIgnoreCase(warnResponse)) {
                pwmRequest.getPwmSession().getSessionStateBean().setSkippedRequirePassword(true);
                pwmRequest.sendRedirectToContinue();
            } else if ("change".equalsIgnoreCase(warnResponse)) {
                changePasswordBean.setWarnPassed(true);
            }
        }
    }

    private void handleChangeRequest(
            final PwmRequest pwmRequest,
            final ChangePasswordBean changePasswordBean
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final UserInfoBean uiBean = pwmRequest.getPwmSession().getUserInfoBean();

        if (!changePasswordBean.isAllChecksPassed()) {
            this.advancedToNextStage(pwmRequest, changePasswordBean);
            return;
        }

        final PasswordData password1 = pwmRequest.readParameterAsPassword("password1");
        final PasswordData password2 = pwmRequest.readParameterAsPassword("password2");

        // check the password meets the requirements
        try {
            final ChaiUser theUser = pwmRequest.getPwmSession().getSessionManager().getActor(pwmRequest.getPwmApplication());
            final PwmPasswordRuleValidator pwmPasswordRuleValidator = new PwmPasswordRuleValidator(pwmRequest.getPwmApplication(), uiBean.getPasswordPolicy());
            pwmPasswordRuleValidator.testPassword(password1,null,uiBean,theUser);
        } catch (PwmDataValidationException e) {
            pwmRequest.setResponseError(e.getErrorInformation());
            LOGGER.debug(pwmRequest, "failed password validation check: " + e.getErrorInformation().toDebugStr());
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.PASSWORD_CHANGE);
            return;
        }

        //make sure the two passwords match
        boolean caseSensitive = uiBean.getPasswordPolicy().getRuleHelper().readBooleanValue(
                PwmPasswordRule.CaseSensitive);
        if (PasswordUtility.PasswordCheckInfo.MATCH_STATUS.MATCH != PasswordUtility.figureMatchStatus(caseSensitive,
                password1, password2)) {
            pwmRequest.setResponseError(PwmError.PASSWORD_DOESNOTMATCH.toInfo());
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.PASSWORD_CHANGE);
            return;
        }

        try {
            executeChangePassword(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), password1);
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.PASSWORD_CHANGE_WAIT);
        } catch (PwmOperationalException e) {
            LOGGER.debug(e.getErrorInformation().toDebugStr());
            pwmRequest.setResponseError(e.getErrorInformation());
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.PASSWORD_CHANGE);
        }
    }

    private void handleAgreeRequest(
            final PwmRequest pwmRequest,
            final ChangePasswordBean cpb
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        LOGGER.debug(pwmRequest, "user accepted password change agreement");
        if (!cpb.isAgreementPassed()) {
            cpb.setAgreementPassed(true);
            AuditRecord auditRecord = pwmRequest.getPwmApplication().getAuditManager().createUserAuditRecord(
                    AuditEvent.AGREEMENT_PASSED,
                    pwmRequest.getUserInfoIfLoggedIn(),
                    pwmRequest.getSessionLabel(),
                    "ChangePassword"
            );
            pwmRequest.getPwmApplication().getAuditManager().submit(auditRecord);
        }
    }

    private void handleFormRequest(
            final PwmRequest pwmRequest,
            final ChangePasswordBean cpb
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final SessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();
        final UserInfoBean uiBean = pwmRequest.getPwmSession().getUserInfoBean();
        final LoginInfoBean loginBean = pwmRequest.getPwmSession().getLoginInfoBean();

        final PasswordData currentPassword = pwmRequest.readParameterAsPassword("currentPassword");

        // check the current password
        if (cpb.isCurrentPasswordRequired() && loginBean.getUserCurrentPassword() != null) {
            if (currentPassword == null) {
                LOGGER.debug(pwmRequest, "failed password validation check: currentPassword value is missing");
                pwmRequest.setResponseError(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER));
                forwardToFormPage(pwmRequest);
                return;
            }

            final boolean passed;
            {
                final boolean caseSensitive = Boolean.parseBoolean(
                        uiBean.getPasswordPolicy().getValue(PwmPasswordRule.CaseSensitive));
                final PasswordData storedPassword = loginBean.getUserCurrentPassword();
                passed = caseSensitive ? storedPassword.equals(currentPassword) : storedPassword.equalsIgnoreCase(currentPassword);
            }

            if (!passed) {
                pwmRequest.getPwmApplication().getIntruderManager().convenience().markUserIdentity(
                        uiBean.getUserIdentity(), pwmRequest.getSessionLabel());
                LOGGER.debug(pwmRequest, "failed password validation check: currentPassword value is incorrect");
                pwmRequest.setResponseError(new ErrorInformation(PwmError.ERROR_BAD_CURRENT_PASSWORD));
                forwardToFormPage(pwmRequest);
                return;
            }
            cpb.setCurrentPasswordPassed(true);
        }

        final List<FormConfiguration> formItem = pwmRequest.getConfig().readSettingAsForm(PwmSetting.PASSWORD_REQUIRE_FORM);

        try {
            //read the values from the request
            final Map<FormConfiguration,String> formValues = FormUtility.readFormValuesFromRequest(
                    pwmRequest, formItem, ssBean.getLocale());

            validateParamsAgainstLDAP(formValues, pwmRequest.getPwmSession(),
                    pwmRequest.getPwmSession().getSessionManager().getActor(pwmRequest.getPwmApplication()));

            cpb.setFormPassed(true);
        } catch (PwmOperationalException e) {
            pwmRequest.getPwmApplication().getIntruderManager().convenience().markAddressAndSession(pwmRequest.getPwmSession());
            pwmRequest.getPwmApplication().getIntruderManager().convenience().markUserIdentity(uiBean.getUserIdentity(), pwmRequest.getSessionLabel());
            LOGGER.debug(pwmRequest,e.getErrorInformation());
            pwmRequest.setResponseError(e.getErrorInformation());
            forwardToFormPage(pwmRequest);
            return;
        }

        advancedToNextStage(pwmRequest, cpb);
    }

    private void executeChangePassword(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final PasswordData newPassword
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        // password accepted, setup change password
        final ChangePasswordBean cpb = pwmSession.getChangePasswordBean();

        // change password
        PasswordUtility.setActorPassword(pwmSession, pwmApplication, newPassword);

        //init values for progress screen
        {
            final PasswordChangeProgressChecker.ProgressTracker tracker = new PasswordChangeProgressChecker.ProgressTracker();
            final PasswordChangeProgressChecker checker = new PasswordChangeProgressChecker(
                    pwmApplication,
                    pwmSession.getUserInfoBean().getUserIdentity(),
                    pwmSession.getLabel(),
                    pwmSession.getSessionStateBean().getLocale()
            );
            cpb.setChangeProgressTracker(tracker);
            cpb.setChangePasswordMaxCompletion(checker.maxCompletionTime(tracker));
        }

        // send user an email confirmation
        sendChangePasswordEmailNotice(pwmSession, pwmApplication);

        // send audit event
        pwmApplication.getAuditManager().submit(AuditEvent.CHANGE_PASSWORD, pwmSession.getUserInfoBean(), pwmSession);
    }

    private void advancedToNextStage(
            final PwmRequest pwmRequest,
            final ChangePasswordBean changePasswordBean
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmApplication.getConfig();

        if (changePasswordBean.getChangeProgressTracker() != null) {
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.PASSWORD_CHANGE_WAIT);
            return;
        }

        if (warnPageShouldBeShown(pwmRequest, changePasswordBean)) {
            LOGGER.trace(pwmRequest, "pasword expiration is within password warn period, forwarding user to warning page");
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.PASSWORD_WARN);
            return;
        }

        final String agreementMsg = pwmApplication.getConfig().readSettingAsLocalizedString(PwmSetting.PASSWORD_CHANGE_AGREEMENT_MESSAGE, pwmRequest.getLocale());
        if (agreementMsg != null && agreementMsg.length() > 0 && !changePasswordBean.isAgreementPassed()) {
            final MacroMachine macroMachine = pwmSession.getSessionManager().getMacroMachine(pwmApplication);
            final String expandedText = macroMachine.expandMacros(agreementMsg);
            pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.AgreementText,expandedText);
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.PASSWORD_AGREEMENT);
            return;
        }

        if (determineIfCurrentPasswordRequired(pwmApplication, pwmSession) && !changePasswordBean.isCurrentPasswordPassed()) {
            forwardToFormPage(pwmRequest);
            return;
        }

        if (!config.readSettingAsForm(PwmSetting.PASSWORD_REQUIRE_FORM).isEmpty() && !changePasswordBean.isFormPassed()) {
            forwardToFormPage(pwmRequest);
            return;
        }

        changePasswordBean.setAllChecksPassed(true);
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.PASSWORD_CHANGE);
    }

    private static boolean determineIfCurrentPasswordRequired(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException
    {
        final RequireCurrentPasswordMode currentSetting = pwmApplication.getConfig().readSettingAsEnum(PwmSetting.PASSWORD_REQUIRE_CURRENT, RequireCurrentPasswordMode.class);

        if (currentSetting == RequireCurrentPasswordMode.FALSE) {
            return false;
        }

        if (pwmSession.getLoginInfoBean().getAuthenticationType() == AuthenticationType.AUTH_FROM_PUBLIC_MODULE) {
            LOGGER.debug(pwmSession, "skipping user current password requirement, authentication type is " + AuthenticationType.AUTH_FROM_PUBLIC_MODULE);
            return false;
        }

        {
            final PasswordData currentPassword = pwmSession.getLoginInfoBean().getUserCurrentPassword();
            if (currentPassword == null) {
                LOGGER.debug(pwmSession, "skipping user current password requirement, current password is not known to application");
                return false;
            }
        }

        if (currentSetting == RequireCurrentPasswordMode.TRUE) {
            return true;
        }

        final PasswordStatus passwordStatus = pwmSession.getUserInfoBean().getPasswordState();
        return currentSetting == RequireCurrentPasswordMode.NOTEXPIRED
                && !passwordStatus.isExpired()
                && !passwordStatus.isPreExpired()
                && !passwordStatus.isViolatesPolicy()
                && !pwmSession.getUserInfoBean().isRequiresNewPassword();

    }

// -------------------------- ENUMERATIONS --------------------------

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
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_CHANGEPASSWORD, locale);

        if (configuredEmailSetting == null) {
            LOGGER.debug(pwmSession, "skipping change password email for '" + pwmSession.getUserInfoBean().getUserIdentity() + "' no email configured");
            return;
        }

        pwmApplication.getEmailQueue().submitEmail(
                configuredEmailSetting,
                pwmSession.getUserInfoBean(),

                pwmSession.getSessionManager().getMacroMachine(pwmApplication));
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
        if (ChaiProvider.DIRECTORY_VENDOR.ORACLE_DS == pwmSession.getSessionManager().getChaiProvider().getDirectoryVendor()) {
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
            final PwmRequest pwmRequest,
            final ChangePasswordBean changePasswordBean
    )
            throws IOException
    {

        final PasswordChangeProgressChecker.ProgressTracker progressTracker = changePasswordBean.getChangeProgressTracker();
        final PasswordChangeProgressChecker.PasswordChangeProgress passwordChangeProgress;
        if (progressTracker == null) {
            passwordChangeProgress = PasswordChangeProgressChecker.PasswordChangeProgress.COMPLETE;
        } else {
            final PasswordChangeProgressChecker checker = new PasswordChangeProgressChecker(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity(),
                    pwmRequest.getSessionLabel(),
                    pwmRequest.getLocale()
            );
            passwordChangeProgress = checker.figureProgress(progressTracker);
        }
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(passwordChangeProgress);

        LOGGER.trace(pwmRequest, "returning result for restCheckProgress: " + JsonUtil.serialize(restResultBean));
        pwmRequest.outputJsonResult(restResultBean);
    }

    private void handleComplete(
            final PwmRequest pwmRequest,
            final ChangePasswordBean cpb
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PasswordChangeProgressChecker.ProgressTracker progressTracker = cpb.getChangeProgressTracker();
        boolean isComplete = true;
        if (progressTracker != null) {
            final PasswordChangeProgressChecker checker = new PasswordChangeProgressChecker(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity(),
                    pwmRequest.getSessionLabel(),
                    pwmRequest.getLocale()
            );
            final PasswordChangeProgressChecker.PasswordChangeProgress passwordChangeProgress = checker.figureProgress(progressTracker);
            isComplete = passwordChangeProgress.isComplete();
        }

        if (isComplete) {
            if (progressTracker != null) {
                final TimeDuration totalTime = TimeDuration.fromCurrent(progressTracker.getBeginTime());
                try {
                    pwmRequest.getPwmApplication().getStatisticsManager().updateAverageValue(Statistic.AVG_PASSWORD_SYNC_TIME,totalTime.getTotalMilliseconds());
                    LOGGER.trace(pwmRequest,"password sync process marked completed (" + totalTime.asCompactString() + ")");
                } catch (Exception e) {
                    LOGGER.error(pwmRequest,"unable to update average password sync time statistic: " + e.getMessage());
                }
            }
            cpb.setChangeProgressTracker(null);
            final Locale locale = pwmRequest.getLocale();
            final String completeMessage = pwmRequest.getConfig().readSettingAsLocalizedString(PwmSetting.PASSWORD_COMPLETE_MESSAGE,locale);
            if (completeMessage != null && !completeMessage.isEmpty()) {
                final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine(pwmRequest.getPwmApplication());
                final String expandedText = macroMachine.expandMacros(completeMessage);
                pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.CompleteText, expandedText);
                pwmRequest.forwardToJsp(PwmConstants.JSP_URL.PASSWORD_COMPLETE);
            } else {
                pwmRequest.getPwmSession().clearSessionBean(ChangePasswordBean.class);
                pwmRequest.forwardToSuccessPage(Message.Success_PasswordChange);
            }
        } else {
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.PASSWORD_CHANGE_WAIT);
        }
    }

    private boolean warnPageShouldBeShown(final PwmRequest pwmRequest, final ChangePasswordBean changePasswordBean) {
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        if (!pwmSession.getUserInfoBean().getPasswordState().isWarnPeriod()) {
            return false;
        }

        if (pwmRequest.getPwmSession().getSessionStateBean().isSkippedRequireNewPassword()) {
            return false;
        }

        if (changePasswordBean.isWarnPassed()) {
            return false;
        }

        if (pwmRequest.getPwmSession().getLoginInfoBean().getAuthenticationFlags().contains(AuthenticationType.AUTH_FROM_PUBLIC_MODULE)) {
            return false;
        }

        if (pwmRequest.getPwmSession().getLoginInfoBean().getAuthenticationType() == AuthenticationType.AUTH_FROM_PUBLIC_MODULE) {
            return false;
        }

        return true;
    }

    protected void forwardToFormPage(final PwmRequest pwmRequest)
            throws ServletException, PwmUnrecoverableException, IOException
    {
        pwmRequest.addFormInfoToRequestAttr(PwmSetting.PASSWORD_REQUIRE_FORM,false,false);
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.PASSWORD_FORM);

    }
}
