/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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
import password.pwm.*;
import password.pwm.bean.ChangePasswordBean;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.Message;
import password.pwm.config.PwmPasswordRule;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.PasswordUtility;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;

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

        if (!ssBean.isAuthenticated()) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_AUTHENTICATION_REQUIRED));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.PASSWORD_REQUIRE_CURRENT)) {
            if (!pwmSession.getUserInfoBean().isAuthFromUnknownPw()) {
                cpb.setCurrentPasswordRequired(true);
            }
        }

        if (!Permission.checkPermission(Permission.CHANGE_PASSWORD, pwmSession, pwmApplication)) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (processRequestParam != null && processRequestParam.length() > 0) {
            Validator.validatePwmFormID(req);
            if (processRequestParam.equalsIgnoreCase("change")) {        // change request
                this.handleChangeRequest(req, resp);
            } else if (processRequestParam.equalsIgnoreCase("doChange")) {      // wait page call-back
                this.handleDoChangeRequest(req, resp);
            } else if (processRequestParam.equalsIgnoreCase("agree")) {         // accept password change agreement
                LOGGER.debug(pwmSession, "user accepted password change agreement");
                cpb.setAgreementPassed(true);
            }
        }

        if (!resp.isCommitted()) {
            this.forwardToJSP(req, resp);
        }
    }



    private static MATCH_STATUS figureMatchStatus(final PwmSession session, final String password1, final String password2) {
        final MATCH_STATUS matchStatus;
        if (password2.length() < 1) {
            matchStatus = MATCH_STATUS.EMPTY;
        } else {
            if (session.getUserInfoBean().getPasswordPolicy().getRuleHelper().readBooleanValue(PwmPasswordRule.CaseSensitive)) {
                matchStatus = password1.equals(password2) ? MATCH_STATUS.MATCH : MATCH_STATUS.NO_MATCH;
            } else {
                matchStatus = password1.equalsIgnoreCase(password2) ? MATCH_STATUS.MATCH : MATCH_STATUS.NO_MATCH;
            }
        }

        return matchStatus;
    }

    /**
     * Action handler for when user clicks "change password" button.  This copies the
     * password into the changepasswordbean, redirects to the please wait screen, then
     * directs back to the actual doChange.
     *
     * @param req  http request
     * @param resp http response
     * @throws ServletException should never throw
     * @throws IOException      if error writing response
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException
     *                          if ldap server becomes unavailable
     * @throws password.pwm.error.PwmUnrecoverableException
     *                          if an unexpected error occurs
     */
    private void handleChangeRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException {
        //Fetch the required managers/beans
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final ChangePasswordBean cpb = pwmSession.getChangePasswordBean();

        final String currentPassword = Validator.readStringFromRequest(req, "currentPassword");
        final String password1 = Validator.readStringFromRequest(req, "password1");
        final String password2 = Validator.readStringFromRequest(req, "password2");

        // check the current password
        if (cpb.isCurrentPasswordRequired() && pwmSession.getUserInfoBean().getUserCurrentPassword() != null) {
            if (currentPassword == null || currentPassword.length() < 1) {
                ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER));
                LOGGER.debug(pwmSession, "failed password validation check: currentPassword value is missing");
                this.forwardToJSP(req, resp);
                return;
            }

            final boolean caseSensitive = Boolean.parseBoolean(pwmSession.getUserInfoBean().getPasswordPolicy().getValue(PwmPasswordRule.CaseSensitive));
            final boolean passed;
            if (caseSensitive) {
                passed = pwmSession.getUserInfoBean().getUserCurrentPassword().equals(currentPassword);
            } else {
                passed = pwmSession.getUserInfoBean().getUserCurrentPassword().equalsIgnoreCase(currentPassword);
            }

            if (!passed) {
                ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_BAD_CURRENT_PASSWORD));
                pwmApplication.getIntruderManager().addBadUserAttempt(pwmSession.getUserInfoBean().getUserDN(), pwmSession);
                LOGGER.debug(pwmSession, "failed password validation check: currentPassword value is incorrect");
                this.forwardToJSP(req, resp);
                return;
            }
        }

        // check the password meets the requirements
        {
            try {
                Validator.testPasswordAgainstPolicy(password1, pwmSession, pwmApplication);
            } catch (PwmDataValidationException e) {
                ssBean.setSessionError(e.getErrorInformation());
                LOGGER.debug(pwmSession, "failed password validation check: " + e.getErrorInformation().toDebugStr());
                this.forwardToJSP(req, resp);
                return;
            }
        }

        //make sure the two passwords match
        if (MATCH_STATUS.MATCH != figureMatchStatus(pwmSession, password1, password2)) {
            ssBean.setSessionError(new ErrorInformation(PwmError.PASSWORD_DOESNOTMATCH));
            this.forwardToJSP(req, resp);
            return;
        }

        // password accepted, setup change password
        {
            cpb.setNewPassword(password1);
            LOGGER.trace(pwmSession, "wrote password to changePasswordBean");

            forwardToWaitPage(req, resp, this.getServletContext());
        }
    }

    /**
     * Handles the actual change password request.  This action is called via a redirect
     * from the "Please Wait" screen.
     *
     * @param req  http request
     * @param resp http response
     * @throws ServletException         should never throw
     * @throws IOException              if error writing response
     * @throws ChaiUnavailableException if ldap disappears
     * @throws password.pwm.error.PwmUnrecoverableException
     *                                  if there is an unexpected error setting password
     */
    private void handleDoChangeRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);

        final ChangePasswordBean cpb = pwmSession.getChangePasswordBean();
        final String newPassword = cpb.getNewPassword();

        if (newPassword == null || newPassword.length() < 1) {
            LOGGER.warn(pwmSession, "entered doChange, but bean does not have a valid password stored in server session");
            cpb.clearPassword();
            return;
        }

        LOGGER.trace(pwmSession, "retrieved password from server session");
        cpb.clearPassword();

        try {
            PasswordUtility.setUserPassword(pwmSession, pwmApplication, newPassword);
        } catch (PwmOperationalException e) {
            LOGGER.debug(e.getErrorInformation().toDebugStr());
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(PwmConstants.URL_SERVLET_CHANGE_PASSWORD, req, resp));
            return;
        }

        // send user an email confirmation
        sendChangePasswordEmailNotice(pwmSession, pwmApplication);

        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.LOGOUT_AFTER_PASSWORD_CHANGE)) {
            ssBean.setFinishAction(SessionStateBean.FINISH_ACTION.LOGOUT);
        }

        ssBean.setSessionSuccess(Message.SUCCESS_PASSWORDCHANGE, null);
        UserHistory.updateUserHistory(pwmSession, pwmApplication, UserHistory.Record.Event.CHANGE_PASSWORD, null);
        ServletHelper.forwardToSuccessPage(req, resp);
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final String agreementMsg = pwmApplication.getConfig().readSettingAsLocalizedString(PwmSetting.PASSWORD_CHANGE_AGREEMENT_MESSAGE, userLocale);
        final ChangePasswordBean cpb = pwmSession.getChangePasswordBean();

        if (agreementMsg != null && agreementMsg.length() > 0 && !cpb.isAgreementPassed()) {
            this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_PASSWORD_AGREEMENT).forward(req, resp);
        } else {
            this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_PASSWORD_CHANGE).forward(req, resp);
        }
    }

    public static void forwardToWaitPage(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final ServletContext theContext
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        final StringBuilder returnURL = new StringBuilder();
        returnURL.append(req.getContextPath());
        returnURL.append(req.getServletPath());
        returnURL.append("?" + PwmConstants.PARAM_ACTION_REQUEST + "=" + "doChange");
        returnURL.append("&" + PwmConstants.PARAM_FORM_ID + "=").append(Helper.buildPwmFormID(pwmSession.getSessionStateBean()));
        final String rewrittenURL = SessionFilter.rewriteURL(returnURL.toString(), req, resp);
        req.setAttribute("nextURL",rewrittenURL );

        try {
            final String url = SessionFilter.rewriteURL('/' + PwmConstants.URL_JSP_PASSWORD_CHANGE_WAIT, req, resp);
            theContext.getRequestDispatcher(url).forward(req, resp);
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unexpected error sending user to wait page: " + e.toString());
        }

    }

    public static void sendChangePasswordEmailNotice(final PwmSession pwmSession, final PwmApplication pwmApplication) throws PwmUnrecoverableException {
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();

        final String fromAddress = config.readSettingAsLocalizedString(PwmSetting.EMAIL_CHANGEPASSWORD_FROM, locale);
        final String subject = config.readSettingAsLocalizedString(PwmSetting.EMAIL_CHANGEPASSWORD_SUBJECT, locale);
        final String plainBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_CHANGEPASSWORD_BODY, locale);
        final String htmlBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_CHANGEPASSWORD_BODY_HMTL, locale);

        final String toAddress = pwmSession.getUserInfoBean().getUserEmailAddress();
        if (toAddress == null || toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send change password email for '" + pwmSession.getUserInfoBean().getUserDN() + "' no ' user email address available");
            return;
        }

        pwmApplication.sendEmailUsingQueue(new EmailItemBean(toAddress, fromAddress, subject, plainBody, htmlBody), pwmSession.getUserInfoBean());
    }

    private enum MATCH_STATUS {
        MATCH, NO_MATCH, EMPTY
    }
}

