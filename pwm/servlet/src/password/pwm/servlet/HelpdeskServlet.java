/**
 *
 */
package password.pwm.servlet;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
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
import password.pwm.config.Configuration;
import password.pwm.config.Message;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
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
    private static final int DEFAULT_INPUT_LENGTH = 1024;

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

        final String processAction = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST, DEFAULT_INPUT_LENGTH);

        if (processAction != null && processAction.length() > 0) {
            Validator.validatePwmFormID(req);
            if (processAction.equalsIgnoreCase("doReset")) {
                this.processResetPassword(req, resp);
                return;
            } else if (processAction.equalsIgnoreCase("doUnlock")) {
                this.processUnlockPassword(req, resp);
                return;
            } else if (processAction.equalsIgnoreCase("search")) {
                processUserSearch(req);
            } else {
                pwmSession.getHelpdeskBean().setUserExists(false);
            }
        }

        if (!resp.isCommitted()) {
            this.forwardToJSP(req, resp);
        }
    }

    /**
     * Extracted from UserInformationServlet to search for user to reset password for.
     * @param req
     * @throws ChaiUnavailableException
     * @throws PwmUnrecoverableException
     */
    private void processUserSearch(
            final HttpServletRequest req
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final HelpdeskBean helpdeskBean = pwmSession.getHelpdeskBean();
        final Configuration config = pwmApplication.getConfig();

        helpdeskBean.setUserExists(false);
        final String username = Validator.readStringFromRequest(req, "username", 255);
        final String context = Validator.readStringFromRequest(req, "context", 255);


        if (username.length() < 1) {
            pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER));
            return;
        }

        final String userDN;
        try {
            userDN = UserStatusHelper.convertUsernameFieldtoDN(username, pwmSession, pwmApplication, context);
        } catch (PwmOperationalException e) {
            LOGGER.trace(pwmSession, "can't find username: " + e.getMessage());
            helpdeskBean.setUserExists(false);
            return;
        }

        helpdeskBean.setUserExists(true);
        final UserInfoBean uiBean = new UserInfoBean();
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        UserStatusHelper.populateUserInfoBean(pwmSession, uiBean, pwmApplication, userLocale, userDN, null, pwmSession.getSessionManager().getChaiProvider());
        helpdeskBean.setUserInfoBean(uiBean);

        final ChaiUser theUser = ChaiFactory.createChaiUser(userDN, pwmSession.getSessionManager().getChaiProvider());

        try {
            helpdeskBean.setIntruderLocked(theUser.isLocked());
        } catch (Exception e) {
            LOGGER.error(pwmSession,"unexpected error reading responses for user '" + userDN + "', " + e.getMessage());
        }

        try {
            helpdeskBean.setLastLoginTime(theUser.readLastLoginTime());
        } catch (Exception e) {
            LOGGER.error(pwmSession,"unexpected error reading responses for user '" + userDN + "', " + e.getMessage());
        }

        helpdeskBean.setPwmIntruder(false);
        try {
            pwmApplication.getIntruderManager().checkUser(userDN,pwmSession);
            pwmApplication.getIntruderManager().checkUser(uiBean.getUserID(),pwmSession);
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
    private void processResetPassword(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final HelpdeskBean helpdeskBean = pwmSession.getHelpdeskBean();

        if (!helpdeskBean.isUserExists()) {
            final String errorMsg = "password set request, but no user result in search";
            LOGGER.error(pwmSession, errorMsg);
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg));
            this.forwardToJSP(req, resp);
            return;
        }

        final String userDN = helpdeskBean.getUserInfoBean().getUserDN();

        final String password1 = Validator.readStringFromRequest(req, "password1", DEFAULT_INPUT_LENGTH);
        final String password2 = Validator.readStringFromRequest(req, "password2", DEFAULT_INPUT_LENGTH);

        if (password1.length() < 1 || !password1.equals(password2)) {
            ssBean.setSessionError(new ErrorInformation(PwmError.PASSWORD_DOESNOTMATCH));
            this.forwardToJSP(req, resp);
            return;
        }

        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_ENFORCE_PASSWORD_POLICY)) {
            try {
                final PwmPasswordPolicy userPasswordPolicy = helpdeskBean.getUserInfoBean().getPasswordPolicy();
                Validator.testPasswordAgainstPolicy(password1, null, pwmSession, pwmApplication, userPasswordPolicy, false);
            } catch (PwmDataValidationException e) {
                ssBean.setSessionError(new ErrorInformation(e.getErrorInformation().getError()));
                this.forwardToJSP(req, resp);
                return;
            }
        }

        // read user and test Password Policy
        try {
            final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
            final ChaiUser chaiUser = ChaiFactory.createChaiUser(userDN, provider);
            chaiUser.setPassword(password1);
            {
                final ChaiProvider proxyProvider = pwmApplication.getProxyChaiProvider();
                final ChaiUser proxiedChaiUser = ChaiFactory.createChaiUser(userDN, proxyProvider);
                final String message = "(" + pwmSession.getUserInfoBean().getUserID() + ")";
                UserHistory.updateUserHistory(pwmSession, pwmApplication, Record.Event.HELPDESK_SET_PASSWORD, message);
                Helper.updateLastUpdateAttribute(pwmSession, pwmApplication, proxiedChaiUser);
            }
        } catch (ChaiUnavailableException e) {
            pwmApplication.getStatisticsManager().incrementValue(Statistic.LDAP_UNAVAILABLE_COUNT);
            pwmApplication.setLastLdapFailure(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage()));
            LOGGER.warn(pwmSession, "ChaiUnavailableException was thrown while resetting password: " + e.toString());
            throw e;
        } catch (ChaiPasswordPolicyException e) {
            final ChaiError passwordError = e.getErrorCode();
            final PwmError pwmError = PwmError.forChaiError(passwordError);
            ssBean.setSessionError(new ErrorInformation(pwmError == null ? PwmError.PASSWORD_UNKNOWN_VALIDATION : pwmError));
            LOGGER.trace(pwmSession, "ChaiPasswordPolicyException was thrown while resetting password: " + e.toString());
            this.forwardToJSP(req, resp);
            return;
        } catch (ChaiOperationException e) {
            final PwmError returnMsg = PwmError.forChaiError(e.getErrorCode()) == null ? PwmError.ERROR_UNKNOWN : PwmError.forChaiError(e.getErrorCode());
            final ErrorInformation error = new ErrorInformation(returnMsg, e.getMessage());
            ssBean.setSessionError(error);
            LOGGER.warn(pwmSession, "error resetting password for user '" + helpdeskBean.getUserInfoBean().getUserDN() + "'' " + error.toDebugStr() + ", " + e.getMessage());
            this.forwardToJSP(req, resp);
            return;
        }
        LOGGER.info(pwmSession,"helpdesk set password for '" + userDN + "' successfully.");
        ssBean.setSessionSuccess(Message.SUCCESS_PASSWORDRESET, helpdeskBean.getUserInfoBean().getUserID());
        forwardToJSP(req, resp);
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
                UserHistory.updateUserHistory(pwmSession, pwmApplication, Record.Event.HELPDESK_UNLOCK_PASSWORD, message);
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
            this.forwardToJSP(req, resp);
            return;
        } catch (ChaiOperationException e) {
            final PwmError returnMsg = PwmError.forChaiError(e.getErrorCode()) == null ? PwmError.ERROR_UNKNOWN : PwmError.forChaiError(e.getErrorCode());
            final ErrorInformation error = new ErrorInformation(returnMsg, e.getMessage());
            ssBean.setSessionError(error);
            LOGGER.warn(pwmSession, "error resetting password for user '" + helpdeskBean.getUserInfoBean().getUserDN() + "'' " + error.toDebugStr() + ", " + e.getMessage());
            this.forwardToJSP(req, resp);
            return;
        }

    }

    private void forwardToJSP(final HttpServletRequest req,
                              final HttpServletResponse resp) throws IOException,
            ServletException {
        this.getServletContext().getRequestDispatcher(
                '/' + PwmConstants.URL_JSP_HELPDESK).forward(req, resp);
    }
}
