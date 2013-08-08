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

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiPasswordPolicyException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.*;
import password.pwm.bean.servlet.HelpdeskBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.ActionConfiguration;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.event.AuditEvent;
import password.pwm.event.AuditRecord;
import password.pwm.i18n.Message;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.*;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

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
        both,
        sendpassword,
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
        final HelpdeskBean helpdeskBean = (HelpdeskBean)pwmSession.getSessionBean(HelpdeskBean.class);

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

        if (req.getParameterMap().containsKey("username")) {
            final String username = Validator.readStringFromRequest(req, "username");
            helpdeskBean.setSearchString(username);
        }

        if (req.getSession().getMaxInactiveInterval() < (int)pwmApplication.getConfig().readSettingAsLong(PwmSetting.HELPDESK_IDLE_TIMEOUT_SECONDS)) {
            req.getSession().setMaxInactiveInterval((int)pwmApplication.getConfig().readSettingAsLong(PwmSetting.HELPDESK_IDLE_TIMEOUT_SECONDS));
        }

        final String processAction = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);

        if (processAction != null && processAction.length() > 0) {
            Validator.validatePwmFormID(req);
            if (processAction.equalsIgnoreCase("doUnlock")) {
                processUnlockPassword(req, resp, pwmApplication, pwmSession);
                return;
            } else if (processAction.equalsIgnoreCase("search")) {
                processSearchRequest(req, resp, pwmApplication, pwmSession);
                return;
            } else if (processAction.equalsIgnoreCase("detail")) {
                processDetailRequest(req, resp, pwmApplication, pwmSession);
                return;
            } else if (processAction.equalsIgnoreCase("executeAction")) {
                processExecuteActionRequest(req, resp, pwmApplication, pwmSession);
                return;
            } else if (processAction.equalsIgnoreCase("continue")) {
            }
        }

        forwardToSearchJSP(req, resp);
    }

    private void processExecuteActionRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final List<ActionConfiguration> actionConfigurations = pwmApplication.getConfig().readSettingAsAction(PwmSetting.HELPDESK_ACTIONS);
        final String requestedName = Validator.readStringFromRequest(req,"name");
        ActionConfiguration action = null;
        for (ActionConfiguration loopAction : actionConfigurations) {
            if (requestedName !=null && requestedName.equals(loopAction.getName())) {
                action = loopAction;
                break;
            }
        }
        if (action == null) {
            final String errorMsg = "request to execute unknown action: " + requestedName;
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg);
            LOGGER.debug(pwmSession,errorInformation.toDebugStr());
            final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInformation, pwmApplication, pwmSession);
            ServletHelper.outputJsonResult(resp, restResultBean);
            return;
        }

        final HelpdeskBean helpdeskBean = (HelpdeskBean)pwmSession.getSessionBean(HelpdeskBean.class);
        if (helpdeskBean.getUserInfoBean() == null) {
            final String errorMsg = "no user selected: " + requestedName;
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg);
            LOGGER.debug(pwmSession,errorInformation.toDebugStr());
            final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInformation, pwmApplication, pwmSession);
            ServletHelper.outputJsonResult(resp, restResultBean);
            return;
        }

        try {
            final ActionExecutor actionExecutor = new ActionExecutor(pwmApplication);
            final ActionExecutor.ActionExecutorSettings settings = new ActionExecutor.ActionExecutorSettings();
            final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
            final ChaiUser chaiUser = ChaiFactory.createChaiUser(helpdeskBean.getUserInfoBean().getUserDN(),provider);
            settings.setUserInfoBean(helpdeskBean.getUserInfoBean());
            settings.setUser(chaiUser);
            settings.setExpandPwmMacros(true);
            actionExecutor.executeAction(action,settings,pwmSession);
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setSuccessMessage(Message.getLocalizedMessage(
                    pwmSession.getSessionStateBean().getLocale(),
                    Message.SUCCESS_ACTION,
                    pwmApplication.getConfig(),
                    action.getName()
            ));
            ServletHelper.outputJsonResult(resp, restResultBean);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmSession,e.getErrorInformation().toDebugStr());
            final RestResultBean restResultBean = RestResultBean.fromErrorInformation(e.getErrorInformation(), pwmApplication, pwmSession);
            ServletHelper.outputJsonResult(resp, restResultBean);
        }
    }

    private void processDetailRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        pwmSession.getHelpdeskBean().setUserInfoBean(null);
        final String userKey = Validator.readStringFromRequest(req, "userKey");
        if (userKey.length() < 1) {
            pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"userKey parameter is missing"));
            forwardToSearchJSP(req,resp);
            return;
        }

        final String userDN = UserSearchEngine.decodeUserDetailKey(userKey,pwmSession);
        processDetailRequest(req,resp,pwmApplication,pwmSession,userDN);
    }


    private void processDetailRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final String userDN
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final HelpdeskBean helpdeskBean = (HelpdeskBean)pwmSession.getSessionBean(HelpdeskBean.class);

        if (pwmSession.getUserInfoBean().getUserDN().equalsIgnoreCase(userDN)) {
            final String errorMsg = "cannot select self";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,errorMsg);
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
            LOGGER.trace(pwmSession, errorInformation.toDebugStr());
            forwardToSearchJSP(req, resp);
            return;
        }

        populateHelpDeskBean(pwmApplication, pwmSession, helpdeskBean, userDN);
        pwmApplication.getStatisticsManager().incrementValue(Statistic.HELPDESK_USER_LOOKUP);
        forwardToDetailJSP(req, resp);
    }

    private void processSearchRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final HelpdeskBean helpdeskBean = pwmSession.getHelpdeskBean();
        pwmSession.getHelpdeskBean().setUserInfoBean(null);
        final List<FormConfiguration> searchForm = pwmApplication.getConfig().readSettingAsForm(PwmSetting.HELPDESK_SEARCH_FORM);
        final int maxResults = (int)pwmApplication.getConfig().readSettingAsLong(PwmSetting.HELPDESK_RESULT_LIMIT);

        if (helpdeskBean.getSearchString() == null || helpdeskBean.getSearchString().length() < 1) {
            pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER));
            helpdeskBean.setSearchResults(null);
            forwardToSearchJSP(req,resp);
            return;
        }

        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        searchConfiguration.setContexts(pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.HELPDESK_SEARCH_BASE));
        searchConfiguration.setEnableContextValidation(false);
        searchConfiguration.setUsername(helpdeskBean.getSearchString());
        searchConfiguration.setFilter(pwmApplication.getConfig().readSettingAsString(PwmSetting.HELPDESK_SEARCH_FILTER));
        searchConfiguration.setChaiProvider(pwmSession.getSessionManager().getChaiProvider());

        final UserSearchEngine.UserSearchResults results;
        try {
            results = userSearchEngine.performMultiUserSearchFromForm(pwmSession, searchConfiguration, maxResults, searchForm);
            helpdeskBean.setSearchResults(results);
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInformation = e.getErrorInformation();
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
            LOGGER.trace(pwmSession, errorInformation.toDebugStr());
            forwardToSearchJSP(req,resp);
            return;
        }

        if (results.getResults().size() == 1) {
            final String userDN = results.getResults().keySet().iterator().next();
            processDetailRequest(req, resp, pwmApplication, pwmSession, userDN);
            return;
        }

        forwardToSearchJSP(req,resp);
    }


    private static void populateHelpDeskBean(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HelpdeskBean helpdeskBean,
            final String userDN
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final ChaiUser theUser = ChaiFactory.createChaiUser(userDN, pwmSession.getSessionManager().getChaiProvider());
        if (!theUser.isValid()) {
            return;
        }

        final UserInfoBean uiBean = new UserInfoBean();
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        UserStatusHelper.populateUserInfoBean(pwmSession, uiBean, pwmApplication, userLocale, userDN, null, pwmSession.getSessionManager().getChaiProvider());
        helpdeskBean.setUserInfoBean(uiBean);
        HelpdeskBean.AdditionalUserInfo additionalUserInfo = new HelpdeskBean.AdditionalUserInfo();
        helpdeskBean.setAdditionalUserInfo(additionalUserInfo);

        try {
            additionalUserInfo.setIntruderLocked(theUser.isLocked());
        } catch (Exception e) {
            LOGGER.error(pwmSession,"unexpected error reading intruder lock status for user '" + userDN + "', " + e.getMessage());
        }

        try {
            additionalUserInfo.setAccountEnabled(theUser.isAccountEnabled());
        } catch (Exception e) {
            LOGGER.error(pwmSession,"unexpected error reading account enabled status for user '" + userDN + "', " + e.getMessage());
        }

        try {
            additionalUserInfo.setLastLoginTime(theUser.readLastLoginTime());
        } catch (Exception e) {
            LOGGER.error(pwmSession,"unexpected error reading last login time for user '" + userDN + "', " + e.getMessage());
        }

        try {
            additionalUserInfo.setPwmIntruder(false);
            pwmApplication.getIntruderManager().check(uiBean.getUserID(),userDN,pwmSession);
        } catch (Exception e) {
            additionalUserInfo.setPwmIntruder(true);
        }

        try {

            additionalUserInfo.setUserHistory(pwmApplication.getAuditManager().readUserAuditRecords(uiBean));
        } catch (Exception e) {
            LOGGER.error(pwmSession,"unexpected error reading userHistory for user '" + userDN + "', " + e.getMessage());
        }

        {
            final Configuration config = pwmApplication.getConfig();
            final UserDataReader userDataReader = new UserDataReader(theUser);
            final List<FormConfiguration> detailFormConfig = config.readSettingAsForm(PwmSetting.HELPDESK_DETAIL_FORM);
            final Map<FormConfiguration,String> formData = new LinkedHashMap<FormConfiguration,String>();
            for (final FormConfiguration formConfiguration : detailFormConfig) {
                formData.put(formConfiguration,"");
            }
            UpdateProfileServlet.populateFormFromLdap(detailFormConfig, pwmSession, formData, userDataReader);
            helpdeskBean.getAdditionalUserInfo().setSearchDetails(formData);
        }
    }


    private void processUnlockPassword(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final HelpdeskBean helpdeskBean = pwmSession.getHelpdeskBean();

        if (helpdeskBean.getUserInfoBean() == null) {
            final String errorMsg = "password unlock request, but no user result in search";
            LOGGER.error(pwmSession, errorMsg);
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg));
            this.forwardToDetailJSP(req, resp);
            return;
        }

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE_UNLOCK)) {
            final String errorMsg = "password unlock request, but helpdesk unlock is not enabled";
            LOGGER.error(pwmSession, errorMsg);
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,errorMsg));
            this.forwardToDetailJSP(req, resp);
            return;
        }

        //clear pwm intruder setting.
        pwmApplication.getIntruderManager().clear(helpdeskBean.getUserInfoBean().getUserID(),helpdeskBean.getUserInfoBean().getUserDN(),pwmSession);

        try {
            final String userDN = helpdeskBean.getUserInfoBean().getUserDN();
            final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
            final ChaiUser chaiUser = ChaiFactory.createChaiUser(userDN, provider);
            final String userID = Helper.readLdapUserIDValue(pwmApplication, chaiUser);
            chaiUser.unlock();
            {
                // mark the event log
                final AuditRecord auditRecord = new AuditRecord(
                        AuditEvent.HELPDESK_UNLOCK_PASSWORD,
                        pwmSession.getUserInfoBean().getUserID(),
                        pwmSession.getUserInfoBean().getUserDN(),
                        new Date(),
                        null,
                        userID,
                        chaiUser.getEntryDN(),
                        pwmSession.getSessionStateBean().getSrcAddress(),
                        pwmSession.getSessionStateBean().getSrcHostname()
                );
                pwmApplication.getAuditManager().submitAuditRecord(auditRecord);
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

        Helper.pause(1000); // delay before re-reading data
        populateHelpDeskBean(pwmApplication, pwmSession, helpdeskBean, helpdeskBean.getUserInfoBean().getUserDN());
        this.forwardToDetailJSP(req, resp);
    }

    private void forwardToSearchJSP(final HttpServletRequest req, final HttpServletResponse resp)
            throws IOException, ServletException
    {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_HELPDESK_SEARCH).forward(req, resp);
    }

    private void forwardToDetailJSP(final HttpServletRequest req, final HttpServletResponse resp)
            throws IOException, ServletException
    {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_HELPDESK_DETAIL).forward(req, resp);
    }
}
