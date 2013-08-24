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

import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.*;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PasswordStatus;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.UserReport;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes a variety of different commands sent in an HTTP Request, including logoff.
 *
 * @author Jason D. Rivard
 */
public class CommandServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(CommandServlet.class);

// -------------------------- OTHER METHODS --------------------------

    public void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final String action = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);
        LOGGER.trace(pwmSession, "received request for action " + action);

        if (action.equalsIgnoreCase("idleUpdate")) {
            processIdleUpdate(req, resp);
        } else if (action.equalsIgnoreCase("checkResponses") || action.equalsIgnoreCase("checkIfResponseConfigNeeded")) {
            processCheckResponses(req, resp);
        } else if (action.equalsIgnoreCase("checkExpire")) {
            processCheckExpire(req, resp);
        } else if (action.equalsIgnoreCase("checkProfile") || action.equalsIgnoreCase("checkAttributes")) {
            processCheckProfile(req, resp);
        } else if (action.equalsIgnoreCase("checkAll")) {
            processCheckAll(req, resp);
        } else if (action.equalsIgnoreCase("continue")) {
            processContinue(req, resp);
        } else if (action.equalsIgnoreCase("outputUserReportCsv")) {
            outputUserReportCsv(req, resp);
        } else if (action.equalsIgnoreCase("outputAuditLogCsv")) {
            outputAuditLogCsv(req, resp);
        } else if (action.equalsIgnoreCase("pageLeaveNotice")) {
            processPageLeaveNotice(req, resp);
        } else if (action.equalsIgnoreCase("viewLog")) {
            processViewLog(req, resp);
        } else {
            LOGGER.debug(pwmSession, "unknown command sent to CommandServlet: " + action);
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
        }
    }

    private static void processIdleUpdate(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException {
        Validator.validatePwmFormID(req);
        if (!resp.isCommitted()) {
            resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            resp.setContentType("text/plain");
        }
    }

    private static void processCheckResponses(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        if (!preCheckUser(req, resp)) {
            return;
        }

        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final boolean responseConfigNeeded = pwmSession.getUserInfoBean().isRequiresResponseConfig();

        if (responseConfigNeeded) {
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(PwmConstants.URL_SERVLET_SETUP_RESPONSES, req, resp));
        } else {
            processContinue(req, resp);
        }
    }

    private static boolean preCheckUser(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        if (!ssBean.isAuthenticated()) {
            final String action = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);
            LOGGER.info(pwmSession, "authentication required for " + action);
            ssBean.setSessionError(PwmError.ERROR_AUTHENTICATION_REQUIRED.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
            return false;
        }
        return true;
    }

    private static void processCheckExpire(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException {
        if (!preCheckUser(req, resp)) {
            return;
        }

        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        if (checkIfPasswordExpired(pwmSession) || pwmSession.getUserInfoBean().isRequiresNewPassword()) {
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(PwmConstants.URL_SERVLET_CHANGE_PASSWORD, req, resp));
        } else if (checkPasswordWarn(pwmSession)) {
            final String passwordWarnURL = req.getContextPath() + "/" + PwmConstants.URL_JSP_PASSWORD_WARN;
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(passwordWarnURL, req, resp));
        } else {
            processContinue(req, resp);
        }
    }

    private static boolean checkIfPasswordExpired(final PwmSession pwmSession) {
        final PasswordStatus passwordState = pwmSession.getUserInfoBean().getPasswordState();
        final StringBuilder sb = new StringBuilder();
        boolean expired = false;
        if (passwordState.isExpired()) {
            sb.append("EXPIRED");
            expired = true;
        } else if (passwordState.isPreExpired()) {
            sb.append("PRE-EXPIRED");
            expired = true;
        } else if (passwordState.isViolatesPolicy()) {
            sb.append("POLICY-VIOLATION");
            expired = true;
        }

        if (expired) {
            sb.insert(0, "checkExpire: password state=");
            sb.append(", redirecting to change screen");
            LOGGER.info(pwmSession, sb.toString());
        }

        return expired;
    }

    private static boolean checkPasswordWarn(final PwmSession pwmSession) {
        final PasswordStatus passwordState = pwmSession.getUserInfoBean().getPasswordState();
        if (passwordState.isWarnPeriod()) {
            LOGGER.info(pwmSession, "checkExpire: password expiration is within warn period, redirecting to warn screen");
            return true;
        }
        return false;
    }

    private static void processCheckProfile(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);

        if (!preCheckUser(req, resp)) {
            return;
        }

        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();

        if (checkProfile(pwmSession, pwmApplication, uiBean)) {
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(PwmConstants.URL_SERVLET_UPDATE_PROFILE, req, resp));
        } else {
            processContinue(req, resp);
        }
    }

    public static boolean checkProfile(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final UserInfoBean uiBean
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {

        final String userDN = uiBean.getUserDN();

        if (pwmSession == null) {
            return false;
        }

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_ENABLE)) {
            return false;
        }

        if (!Permission.checkPermission(Permission.PROFILE_UPDATE, pwmSession, pwmApplication)) {
            LOGGER.info(pwmSession, "checkProfiles: " + userDN + " is not eligible for checkProfile due to query match");
            return false;
        }

        final String checkProfileQueryMatch = pwmApplication.getConfig().readSettingAsString(PwmSetting.UPDATE_PROFILE_CHECK_QUERY_MATCH);

        if (checkProfileQueryMatch != null && checkProfileQueryMatch.length() > 0) {
            if (Helper.testUserMatchQueryString(pwmApplication.getProxyChaiProvider(), userDN, checkProfileQueryMatch)) {
                LOGGER.info(pwmSession, "checkProfiles: " + userDN + " matches 'checkProfiles query match', update profile will be required by user");
                return true;
            } else {
                LOGGER.info(pwmSession, "checkProfiles: " + userDN + " does not match 'checkProfiles query match', update profile not required by user");
                return false;
            }
        } else {
            LOGGER.trace("no checkProfiles query match configured, will check to see if form attributes have values");
            final List<FormConfiguration> updateFormFields = pwmApplication.getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);

            // populate the map with attribute values from the uiBean, which was populated through ldap.
            final Map<FormConfiguration,String> formValues = new HashMap<FormConfiguration, String>();
            for (final FormConfiguration formItem : updateFormFields) {
                try {
                    formValues.put(formItem, pwmSession.getSessionManager().getUserDataReader().readStringAttribute(formItem.getName()));
                } catch (ChaiOperationException e) {
                    LOGGER.error(pwmSession,"error reading attribute while executing checkProfile, attribute=" + formItem.getName() + ", error: " + e.getMessage());
                }
            }

            try {
                Validator.validateParmValuesMeetRequirements(formValues, pwmSession.getSessionStateBean().getLocale());
                LOGGER.debug(pwmSession, "checkProfile: " + userDN + " has value for attributes, update profile will not be required");
                return false;
            } catch (PwmDataValidationException e) {
                LOGGER.debug(pwmSession, "checkProfile: " + userDN + " does not have good attributes (" + e.getMessage() + "), update profile will br required");
                return true;
            }
        }
    }

    private static void processCheckAll(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        if (!preCheckUser(req, resp)) {
            return;
        }

        if (checkIfPasswordExpired(pwmSession) || checkPasswordWarn(pwmSession)) {
            processCheckExpire(req, resp);
        } else if (pwmSession.getUserInfoBean().isRequiresResponseConfig()) {
            processCheckResponses(req, resp);
        } else if (checkProfile(pwmSession, pwmApplication, pwmSession.getUserInfoBean())) {
            processCheckProfile(req, resp);
        } else {
            processContinue(req, resp);
        }
    }

    private static void processContinue(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, PwmUnrecoverableException, ServletException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);

        if (ssBean.isAuthenticated()) {
            //check if user has expired password, and expirecheck during auth is turned on.
            if (uiBean.isRequiresNewPassword() || (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.EXPIRE_CHECK_DURING_AUTH) && checkIfPasswordExpired(pwmSession))) {
                if (uiBean.isRequiresNewPassword()) {
                    LOGGER.trace(pwmSession, "user password has been marked as requiring a change");
                } else {
                    LOGGER.debug(pwmSession, "user password appears expired, redirecting to ChangePassword url");
                }
                final String changePassServletURL = req.getContextPath() + "/public/" + PwmConstants.URL_SERVLET_CHANGE_PASSWORD;

                resp.sendRedirect(SessionFilter.rewriteRedirectURL(changePassServletURL, req, resp));
                return;
            }

            // check if password expiration is within warn period
            if (uiBean.getPasswordState().isWarnPeriod() && !Validator.readStringFromRequest(req, "passwordWarn").equalsIgnoreCase("skip")) {
                LOGGER.info(pwmSession, "password expiration is within warn period, redirecting to warn screen");
                final String passwordWarnURL = req.getContextPath() + "/" + PwmConstants.URL_JSP_PASSWORD_WARN;
                resp.sendRedirect(SessionFilter.rewriteRedirectURL(passwordWarnURL, req, resp));
                return;
            }

            //check if we force response configuration, and user requires it.
            if (uiBean.isRequiresResponseConfig() && (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.CHALLENGE_FORCE_SETUP))) {
                LOGGER.info(pwmSession, "user response set needs to be configured, redirecting to setupresponses page");
                final String setupResponsesURL = req.getContextPath() + "/private/" + PwmConstants.URL_SERVLET_SETUP_RESPONSES;

                resp.sendRedirect(SessionFilter.rewriteRedirectURL(setupResponsesURL, req, resp));
                return;
            }

            if (uiBean.isRequiresUpdateProfile() && (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_FORCE_SETUP))) {
                LOGGER.info(pwmSession, "user profile needs to be updated, redirecting to update profile page");
                final String updateProfileURL = req.getContextPath() + "/private/" + PwmConstants.URL_SERVLET_UPDATE_PROFILE;

                resp.sendRedirect(SessionFilter.rewriteRedirectURL(updateProfileURL, req, resp));
                return;
            }

            // log the user out if our finish action is currently set to log out.
            final boolean forceLogoutOnChange = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.LOGOUT_AFTER_PASSWORD_CHANGE);
            if (forceLogoutOnChange && pwmSession.getSessionStateBean().isPasswordModified()) {
                LOGGER.trace(pwmSession, "logging out user; password has been modified");
                resp.sendRedirect(SessionFilter.rewriteRedirectURL(PwmConstants.URL_SERVLET_LOGOUT, req, resp));
                return;
            }
        }

        final String redirectURL = Helper.figureForwardURL(pwmApplication, pwmSession, req);
        LOGGER.trace(pwmSession, "redirecting user to forward url: " + redirectURL);

        // after redirecting we need to clear the session forward url
        if (pwmSession.getSessionStateBean().getForwardURL() != null) {
            LOGGER.trace(pwmSession, "clearing session forward url: " +  pwmSession.getSessionStateBean().getForwardURL());
            pwmSession.getSessionStateBean().setForwardURL(null);
        }

        resp.sendRedirect(SessionFilter.rewriteRedirectURL(redirectURL, req, resp));
    }

    private void outputUserReportCsv(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        Validator.validatePwmFormID(req);
        if (!preCheckUser(req, resp)) {
            return;
        }

        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        if (!Permission.checkPermission(Permission.PWMADMIN, pwmSession, pwmApplication)) {
            LOGGER.info(pwmSession, "unable to execute output user csv report, user unauthorized");
            return;
        }

        resp.setHeader("content-disposition", "attachment;filename=UserReport.csv");
        resp.setContentType("text/csv;charset=utf-8");

        final OutputStream outputStream = new BufferedOutputStream(resp.getOutputStream());

        final UserReport userReport = new UserReport(pwmApplication);

        try {
            userReport.outputToCsv(outputStream, true, 50 * 1000);
        } catch (Exception e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,e.getMessage());
            final SessionStateBean ssBean = PwmSession.getPwmSession(req).getSessionStateBean();
            ssBean.setSessionError(errorInformation);
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
        }

        outputStream.flush();
        outputStream.close();
    }

    private void processPageLeaveNotice(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final String referrer = req.getHeader("Referer");
        final Date pageLeaveNoticeTime = new Date();
        pwmSession.getSessionStateBean().setPageLeaveNoticeTime(pageLeaveNoticeTime);
        LOGGER.debug("pageLeaveNotice indicated at " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(pageLeaveNoticeTime) + ", referer=" + referrer);
        if (!resp.isCommitted()) {
            resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            resp.setContentType("text/plain");
        }
    }

    private void processViewLog(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, IOException, ServletException {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);

        final PwmApplication.MODE configMode = pwmApplication.getApplicationMode();

        if (configMode == PwmApplication.MODE.RUNNING) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_AUTHENTICATION_REQUIRED,"cannot view log in RUNNING mode"));
        }

        final ServletContext servletContext = req.getSession().getServletContext();
        servletContext.getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER_LOGVIEW).forward(req, resp);
    }

    private void outputAuditLogCsv(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        Validator.validatePwmFormID(req);
        if (!preCheckUser(req, resp)) {
            return;
        }

        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        if (!Permission.checkPermission(Permission.PWMADMIN, pwmSession, pwmApplication)) {
            LOGGER.info(pwmSession, "unable to execute output audit log csv, user unauthorized");
            return;
        }

        resp.setHeader("content-disposition", "attachment;filename=AuditLog.csv");
        resp.setContentType("text/csv;charset=utf-8");

        final OutputStream outputStream = new BufferedOutputStream(resp.getOutputStream());

        try {
            pwmApplication.getAuditManager().outputLocalDBToCsv(new OutputStreamWriter(outputStream), true);
        } catch (Exception e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,e.getMessage());
            final SessionStateBean ssBean = PwmSession.getPwmSession(req).getSessionStateBean();
            ssBean.setSessionError(errorInformation);
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
        }

        outputStream.flush();
        outputStream.close();
    }
}

