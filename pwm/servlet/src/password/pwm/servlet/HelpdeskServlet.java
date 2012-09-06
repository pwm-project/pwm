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

/**
 *
 */
package password.pwm.servlet;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiPasswordPolicyException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.*;
import password.pwm.UserHistory.Record;
import password.pwm.bean.HelpdeskBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.CrUtility;
import password.pwm.util.operations.UserSearchEngine;
import password.pwm.util.operations.UserStatusHelper;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;

/**
 *
 *  Admin interaction servlet for reset user passwords.
 *
 *  @author BoAnSen
 *
 * */
public class HelpdeskServlet extends TopServlet {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(HelpdeskServlet.class);

    public static enum SETTING_PW_UI_MODE {
        none,
        type,
        autogen,
        both
    }

    public static enum SETTING_CLEAR_RESPONSES {
        yes,
        ask,
        no
    }

    @Override
    protected void processRequest(
            HttpServletRequest req,
            HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        ssBean.setSessionSuccess(null, null);

        if (!ssBean.isAuthenticated()) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_AUTHENTICATION_REQUIRED));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (!Permission.checkPermission(Permission.HELPDESK, pwmSession, pwmApplication)) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (req.getSession().getMaxInactiveInterval() < (int)pwmApplication.getConfig().readSettingAsLong(PwmSetting.HELPDESK_IDLE_TIMEOUT_SECONDS)) {
            req.getSession().setMaxInactiveInterval((int)pwmApplication.getConfig().readSettingAsLong(PwmSetting.HELPDESK_IDLE_TIMEOUT_SECONDS));
        }

        final String processAction = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);

        if (processAction != null && processAction.length() > 0) {
            Validator.validatePwmFormID(req);
            if (processAction.equalsIgnoreCase("doUnlock")) {
                processUnlockPassword(req, resp);
            } else if (processAction.equalsIgnoreCase("search")) {
                processUserSearch(req, resp);
            } else {
                pwmSession.getHelpdeskBean().setUserExists(false);
            }
        } else {
            pwmSession.getHelpdeskBean().setUserExists(false);
        }

        if (!resp.isCommitted()) {
            forwardToJSP(req, resp);
        }
    }

    /**
     * Extracted from UserInformationServlet to search for user to reset password for.
     * @param req
     * @throws ChaiUnavailableException
     * @throws PwmUnrecoverableException
     */
    private void processUserSearch(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final HelpdeskBean helpdeskBean = pwmSession.getHelpdeskBean();

        helpdeskBean.setUserExists(false);
        final String username = Validator.readStringFromRequest(req, "username");
        final String context = Validator.readStringFromRequest(req, "context");

        if (username.length() < 1) {
            pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER));
            return;
        }

        final String userDN;
        try {
            final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
            final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
            searchConfiguration.setContext(context);
            searchConfiguration.setUsername(username);
            searchConfiguration.setFilter(pwmApplication.getConfig().readSettingAsString(PwmSetting.HELPDESK_SEARCH_FILTER));
            searchConfiguration.setChaiProvider(pwmSession.getSessionManager().getChaiProvider());
            final ChaiUser theUser = userSearchEngine.performUserSearch(pwmSession, searchConfiguration);
            userDN = theUser == null ? null : theUser.getEntryDN();
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInformation = PwmError.ERROR_CANT_MATCH_USER.toInfo();
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
            LOGGER.trace(pwmSession, errorInformation.toDebugStr());
            helpdeskBean.setUserExists(false);
            return;
        }
        
        // check if user found is the actor, if so throw error
        if (pwmSession.getUserInfoBean().getUserDN().equalsIgnoreCase(userDN)) {
            final String errorMsg = "cannot select self";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,errorMsg);
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
            LOGGER.trace(pwmSession, errorInformation.toDebugStr());
            helpdeskBean.setUserExists(false);
            return;
        }

        populateHelpDeskBean(pwmApplication, pwmSession, helpdeskBean, userDN);
        pwmApplication.getStatisticsManager().incrementValue(Statistic.HELPDESK_USER_LOOKUP);
    }


    private static void populateHelpDeskBean(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HelpdeskBean helpdeskBean,
            final String userDN
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        helpdeskBean.setUserExists(false);

        final ChaiUser theUser = ChaiFactory.createChaiUser(userDN, pwmSession.getSessionManager().getChaiProvider());
        if (!theUser.isValid()) {
            return;
        }

        helpdeskBean.setUserExists(true);
        final UserInfoBean uiBean = new UserInfoBean();
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        UserStatusHelper.populateUserInfoBean(pwmSession, uiBean, pwmApplication, userLocale, userDN, null, pwmSession.getSessionManager().getChaiProvider());
        helpdeskBean.setUserInfoBean(uiBean);

        try {
            helpdeskBean.setIntruderLocked(theUser.isLocked());
        } catch (Exception e) {
            LOGGER.error(pwmSession,"unexpected error reading intruder lock status for user '" + userDN + "', " + e.getMessage());
        }

        try {
            helpdeskBean.setAccountEnabled(theUser.isAccountEnabled());
        } catch (Exception e) {
            LOGGER.error(pwmSession,"unexpected error reading account enabled status for user '" + userDN + "', " + e.getMessage());
        }


        try {
            helpdeskBean.setLastLoginTime(theUser.readLastLoginTime());
        } catch (Exception e) {
            LOGGER.error(pwmSession,"unexpected error reading last login time for user '" + userDN + "', " + e.getMessage());
        }

        helpdeskBean.setPwmIntruder(false);
        try {
            pwmApplication.getIntruderManager().checkUser(userDN,pwmSession);
        } catch (Exception e) {
            helpdeskBean.setPwmIntruder(true);
        }

        {
            UserHistory userHistory = new UserHistory(0);
            try {
                userHistory = UserHistory.readUserHistory(pwmSession, pwmApplication, theUser);
            } catch (Exception e) {
                LOGGER.error(pwmSession,"unexpected error reading userHistory for user '" + userDN + "', " + e.getMessage());
            }
            helpdeskBean.setUserHistory(userHistory);
        }
        
        {
            final ResponseSet responseSet = CrUtility.readUserResponseSet(pwmSession, pwmApplication, theUser);
            helpdeskBean.setResponseSet(responseSet);
        }
    }




    /**
     * Performs the actual password reset.
     * @param req
     * @param resp
     * @throws PwmUnrecoverableException
     * @throws ChaiUnavailableException
     * @throws IOException
     * @throws ServletException
     */
    private void processUnlockPassword(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final HelpdeskBean helpdeskBean = pwmSession.getHelpdeskBean();


        if (!helpdeskBean.isUserExists()) {
            final String errorMsg = "password unlock request, but no user result in search";
            LOGGER.error(pwmSession, errorMsg);
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg));
            this.forwardToJSP(req, resp);
            return;
        }

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE_UNLOCK)) {
            final String errorMsg = "password unlock request, but no helpdesk unlock is not enabled";
            LOGGER.error(pwmSession, errorMsg);
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,errorMsg));
            this.forwardToJSP(req, resp);
            return;
        }

        //clear pwm intruder setting.
        pwmApplication.getIntruderManager().addGoodUserAttempt(helpdeskBean.getUserInfoBean().getUserDN(),pwmSession);
        pwmApplication.getIntruderManager().addGoodUserAttempt(helpdeskBean.getUserInfoBean().getUserID(),pwmSession);

        try {
            final String userDN = helpdeskBean.getUserInfoBean().getUserDN();
            final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
            final ChaiUser chaiUser = ChaiFactory.createChaiUser(userDN, provider);
            chaiUser.unlock();
            {
                final String message = "(by " + pwmSession.getUserInfoBean().getUserID() + ")";
                UserHistory.updateUserHistory(pwmSession, pwmApplication, chaiUser, Record.Event.HELPDESK_UNLOCK_PASSWORD, message);
            }
        } catch (ChaiUnavailableException e) {
            pwmApplication.getStatisticsManager().incrementValue(Statistic.LDAP_UNAVAILABLE_COUNT);
            pwmApplication.setLastLdapFailure(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,e.getMessage()));
            LOGGER.warn(pwmSession, "ChaiUnavailableException was thrown while resetting password: " + e.toString());
            throw e;
        } catch (ChaiPasswordPolicyException e) {
            final ChaiError passwordError = e.getErrorCode();
            final PwmError pwmError = PwmError.forChaiError(passwordError);
            ssBean.setSessionError(new ErrorInformation(pwmError == null ? PwmError.PASSWORD_UNKNOWN_VALIDATION : pwmError));
            LOGGER.trace(pwmSession, "ChaiPasswordPolicyException was thrown while resetting password: " + e.toString());
        } catch (ChaiOperationException e) {
            final PwmError returnMsg = PwmError.forChaiError(e.getErrorCode()) == null ? PwmError.ERROR_UNKNOWN : PwmError.forChaiError(e.getErrorCode());
            final ErrorInformation error = new ErrorInformation(returnMsg, e.getMessage());
            ssBean.setSessionError(error);
            LOGGER.warn(pwmSession, "error resetting password for user '" + helpdeskBean.getUserInfoBean().getUserDN() + "'' " + error.toDebugStr() + ", " + e.getMessage());
        }

        Helper.pause(1000);
        populateHelpDeskBean(pwmApplication, pwmSession, helpdeskBean, helpdeskBean.getUserInfoBean().getUserDN());
        this.forwardToJSP(req, resp);
    }

    private void forwardToJSP(final HttpServletRequest req,
                              final HttpServletResponse resp) throws IOException,
            ServletException {
        this.getServletContext().getRequestDispatcher(
                '/' + PwmConstants.URL_JSP_HELPDESK).forward(req, resp);
    }
}
