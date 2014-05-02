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

import com.google.gson.reflect.TypeToken;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiPasswordPolicyException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.*;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.bean.servlet.HelpdeskBean;
import password.pwm.config.ActionConfiguration;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.event.AuditEvent;
import password.pwm.event.UserAuditRecord;
import password.pwm.i18n.Message;
import password.pwm.ldap.LdapUserDataReader;
import password.pwm.ldap.UserDataReader;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.ldap.UserStatusReader;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.intruder.RecordType;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.operations.OtpService;
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

        {
            final List<FormConfiguration> searchForm = pwmApplication.getConfig().readSettingAsForm(PwmSetting.HELPDESK_SEARCH_FORM);
            final Map<String,String> searchColumns = new LinkedHashMap<String, String>();
            for (final FormConfiguration formConfiguration : searchForm) {
                searchColumns.put(formConfiguration.getName(),formConfiguration.getLabel(pwmSession.getSessionStateBean().getLocale()));
            }
            helpdeskBean.setSearchColumnHeaders(searchColumns);
        }

        final int helpdeskIdleTimeout = (int)pwmApplication.getConfig().readSettingAsLong(PwmSetting.HELPDESK_IDLE_TIMEOUT_SECONDS);
        pwmSession.setSessionTimeout(req.getSession(),helpdeskIdleTimeout);

        final String processAction = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);

        if (processAction != null && processAction.length() > 0) {
            Validator.validatePwmFormID(req);
            if (processAction.equalsIgnoreCase("doUnlock")) {
                processUnlockPassword(req, resp, pwmApplication, pwmSession);
                return;
            } else if (processAction.equalsIgnoreCase("doClearOtpSecret")) {
                processClearOtpSecret(req, resp, pwmApplication, pwmSession);
                return;
            } else if (processAction.equalsIgnoreCase("search")) {
                restSearchRequest(req, resp, pwmApplication, pwmSession);
                return;
            } else if (processAction.equalsIgnoreCase("detail")) {
                processDetailRequest(req, resp, pwmApplication, pwmSession);
                return;
            } else if (processAction.equalsIgnoreCase("executeAction")) {
                processExecuteActionRequest(req, resp, pwmApplication, pwmSession);
                return;
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
            final RestResultBean restResultBean = RestResultBean.fromError(errorInformation, pwmSession.getSessionStateBean().getLocale(), pwmApplication.getConfig());
            ServletHelper.outputJsonResult(resp, restResultBean);
            return;
        }

        final HelpdeskBean helpdeskBean = (HelpdeskBean)pwmSession.getSessionBean(HelpdeskBean.class);
        if (helpdeskBean.getUserInfoBean() == null) {
            final String errorMsg = "no user selected: " + requestedName;
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg);
            LOGGER.debug(pwmSession,errorInformation.toDebugStr());
            final RestResultBean restResultBean = RestResultBean.fromError(errorInformation, pwmSession.getSessionStateBean().getLocale(), pwmApplication.getConfig());
            ServletHelper.outputJsonResult(resp, restResultBean);
            return;
        }

        final boolean useProxy = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY);
        try {
            final UserIdentity userIdentity = helpdeskBean.getUserInfoBean().getUserIdentity();
            final ActionExecutor actionExecutor = new ActionExecutor(pwmApplication);
            final ActionExecutor.ActionExecutorSettings settings = new ActionExecutor.ActionExecutorSettings();
            final ChaiUser chaiUser = useProxy ?
                    pwmApplication.getProxiedChaiUser(userIdentity) :
                    pwmSession.getSessionManager().getActor(pwmApplication,userIdentity);
            settings.setUserInfoBean(helpdeskBean.getUserInfoBean());
            settings.setChaiUser(chaiUser);
            settings.setExpandPwmMacros(true);
            actionExecutor.executeAction(action,settings,pwmSession);
            final RestResultBean restResultBean = new RestResultBean();
            // mark the event log
            {
                final UserAuditRecord auditRecord = pwmApplication.getAuditManager().createUserAuditRecord(
                        AuditEvent.HELPDESK_ACTION,
                        pwmSession.getUserInfoBean().getUserIdentity(),
                        new Date(),
                        action.getName(),
                        helpdeskBean.getUserInfoBean().getUserIdentity(),
                        pwmSession.getSessionStateBean().getSrcAddress(),
                        pwmSession.getSessionStateBean().getSrcHostname()
                );
                pwmApplication.getAuditManager().submit(auditRecord);
            }

            restResultBean.setSuccessMessage(Message.getLocalizedMessage(
                    pwmSession.getSessionStateBean().getLocale(),
                    Message.SUCCESS_ACTION,
                    pwmApplication.getConfig(),
                    action.getName()
            ));
            ServletHelper.outputJsonResult(resp, restResultBean);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmSession,e.getErrorInformation().toDebugStr());
            final RestResultBean restResultBean = RestResultBean.fromError(e.getErrorInformation(), pwmSession.getSessionStateBean().getLocale(), pwmApplication.getConfig());
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
            ServletHelper.forwardToErrorPage(req,resp,false);
            return;
        }

        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmApplication.getConfig());
        processDetailRequest(req, resp, pwmApplication, pwmSession, userIdentity);
    }


    private void processDetailRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final HelpdeskBean helpdeskBean = (HelpdeskBean)pwmSession.getSessionBean(HelpdeskBean.class);

        if (pwmSession.getUserInfoBean().getUserIdentity().equals(userIdentity)) {
            final String errorMsg = "cannot select self";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,errorMsg);
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
            LOGGER.trace(pwmSession, errorInformation.toDebugStr());
            ServletHelper.forwardToErrorPage(req,resp,false);
            return;
        }

        populateHelpDeskBean(pwmApplication, pwmSession, helpdeskBean, userIdentity);
        pwmApplication.getStatisticsManager().incrementValue(Statistic.HELPDESK_USER_LOOKUP);
        forwardToDetailJSP(req, resp);
    }

    private void restSearchRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final String bodyString = ServletHelper.readRequestBody(req);
        final Map<String, String> valueMap = Helper.getGson().fromJson(bodyString,
                new TypeToken<Map<String, String>>() {
                }.getType());

        final String username = valueMap.get("username");

        final boolean useProxy = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY);
        final HelpdeskBean helpdeskBean = pwmSession.getHelpdeskBean();
        helpdeskBean.setSearchString(username);
        pwmSession.getHelpdeskBean().setUserInfoBean(null);
        final List<FormConfiguration> searchForm = pwmApplication.getConfig().readSettingAsForm(PwmSetting.HELPDESK_SEARCH_FORM);
        final int maxResults = (int)pwmApplication.getConfig().readSettingAsLong(PwmSetting.HELPDESK_RESULT_LIMIT);

        if (helpdeskBean.getSearchString() == null || helpdeskBean.getSearchString().length() < 1) {
            final HashMap<String,Object> emptyResults = new HashMap<String, Object>();
            emptyResults.put("searchResults",new ArrayList<Map<String,String>>());
            emptyResults.put("sizeExceeded",false);
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(emptyResults);
            ServletHelper.outputJsonResult(resp, restResultBean);
            return;
        }

        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        searchConfiguration.setContexts(
                pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.HELPDESK_SEARCH_BASE));
        searchConfiguration.setEnableContextValidation(false);
        searchConfiguration.setUsername(username);
        searchConfiguration.setFilter(pwmApplication.getConfig().readSettingAsString(PwmSetting.HELPDESK_SEARCH_FILTER));
        if (!useProxy) {
            searchConfiguration.setLdapProfile(pwmSession.getUserInfoBean().getUserIdentity().getLdapProfileID());
            searchConfiguration.setChaiProvider(pwmSession.getSessionManager().getChaiProvider(pwmApplication));
        }

        final UserSearchEngine.UserSearchResults results;
        final boolean sizeExceeded;
        try {
            results = userSearchEngine.performMultiUserSearchFromForm(pwmSession, searchConfiguration, maxResults, searchForm);
            sizeExceeded = results.isSizeExceeded();
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInformation = e.getErrorInformation();
            LOGGER.error(pwmSession, errorInformation.toDebugStr());
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(new ArrayList<Map<String,String>>());
            ServletHelper.outputJsonResult(resp, restResultBean);
            return;
        }

        final RestResultBean restResultBean = new RestResultBean();
        final LinkedHashMap<String,Object> outputData = new LinkedHashMap<String, Object>();
        outputData.put("searchResults",new ArrayList<Map<String,String>>(results.resultsAsJsonOutput(pwmApplication)));
        outputData.put("sizeExceeded", sizeExceeded);
        restResultBean.setData(outputData);
        ServletHelper.outputJsonResult(resp,restResultBean);
    }



    private static void populateHelpDeskBean(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HelpdeskBean helpdeskBean,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final boolean useProxy = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY);
        final ChaiUser theUser = useProxy ?
                pwmApplication.getProxiedChaiUser(userIdentity) :
                pwmSession.getSessionManager().getActor(pwmApplication, userIdentity);

        if (!theUser.isValid()) {
            return;
        }

        final UserInfoBean uiBean = new UserInfoBean();
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final UserStatusReader userStatusReader = new UserStatusReader(pwmApplication);
        userStatusReader.populateUserInfoBean(pwmSession, uiBean, userLocale, userIdentity, null, theUser.getChaiProvider());
        helpdeskBean.setUserInfoBean(uiBean);
        HelpdeskBean.AdditionalUserInfo additionalUserInfo = new HelpdeskBean.AdditionalUserInfo();
        helpdeskBean.setAdditionalUserInfo(additionalUserInfo);

        try {
            additionalUserInfo.setIntruderLocked(theUser.isLocked());
        } catch (Exception e) {
            LOGGER.error(pwmSession,"unexpected error reading intruder lock status for user '" + userIdentity + "', " + e.getMessage());
        }

        try {
            additionalUserInfo.setAccountEnabled(theUser.isAccountEnabled());
        } catch (Exception e) {
            LOGGER.error(pwmSession,"unexpected error reading account enabled status for user '" + userIdentity + "', " + e.getMessage());
        }

        try {
            additionalUserInfo.setLastLoginTime(theUser.readLastLoginTime());
        } catch (Exception e) {
            LOGGER.error(pwmSession,"unexpected error reading last login time for user '" + userIdentity + "', " + e.getMessage());
        }

        try {
            additionalUserInfo.setPwmIntruder(false);
            pwmApplication.getIntruderManager().check(RecordType.USERNAME, uiBean.getUsername());
            pwmApplication.getIntruderManager().convenience().checkUserIdentity(userIdentity);
        } catch (Exception e) {
            additionalUserInfo.setPwmIntruder(true);
        }

        try {

            additionalUserInfo.setUserHistory(pwmApplication.getAuditManager().readUserHistory(uiBean));
        } catch (Exception e) {
            LOGGER.error(pwmSession,"unexpected error reading userHistory for user '" + userIdentity + "', " + e.getMessage());
        }

        {
            final Configuration config = pwmApplication.getConfig();
            final UserDataReader userDataReader = useProxy
                    ? LdapUserDataReader.appProxiedReader(pwmApplication, userIdentity)
                    : LdapUserDataReader.selfProxiedReader(pwmApplication, pwmSession, userIdentity);
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
        pwmApplication.getIntruderManager().clear(RecordType.USERNAME, helpdeskBean.getUserInfoBean().getUsername());
        pwmApplication.getIntruderManager().convenience().clearUserIdentity(
                helpdeskBean.getUserInfoBean().getUserIdentity());

        final boolean useProxy = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY);
        try {
            final UserIdentity userIdentity = helpdeskBean.getUserInfoBean().getUserIdentity();
            final ChaiUser chaiUser = useProxy ?
                    pwmApplication.getProxiedChaiUser(userIdentity) :
                    pwmSession.getSessionManager().getActor(pwmApplication, userIdentity);
            chaiUser.unlock();
            {
                // mark the event log
                final UserAuditRecord auditRecord = pwmApplication.getAuditManager().createUserAuditRecord(
                        AuditEvent.HELPDESK_UNLOCK_PASSWORD,
                        pwmSession.getUserInfoBean().getUserIdentity(),
                        new Date(),
                        null,
                        userIdentity,
                        pwmSession.getSessionStateBean().getSrcAddress(),
                        pwmSession.getSessionStateBean().getSrcHostname()
                );
                pwmApplication.getAuditManager().submit(auditRecord);
            }
        } catch (ChaiUnavailableException e) {
            pwmApplication.getStatisticsManager().incrementValue(Statistic.LDAP_UNAVAILABLE_COUNT);
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
            LOGGER.warn(pwmSession, "error resetting password for user '" + helpdeskBean.getUserInfoBean().getUserIdentity() + "'' " + error.toDebugStr() + ", " + e.getMessage());
        }

        Helper.pause(1000); // delay before re-reading data
        populateHelpDeskBean(pwmApplication, pwmSession, helpdeskBean, helpdeskBean.getUserInfoBean().getUserIdentity());
        this.forwardToDetailJSP(req, resp);
    }

    private void processClearOtpSecret(HttpServletRequest req, HttpServletResponse resp, PwmApplication pwmApplication, PwmSession pwmSession) throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final HelpdeskBean helpdeskBean = pwmSession.getHelpdeskBean();

        if (helpdeskBean.getUserInfoBean() == null) {
            final String errorMsg = "password unlock request, but no user result in search";
            LOGGER.error(pwmSession, errorMsg);
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg));
            this.forwardToDetailJSP(req, resp);
            return;
        }

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_CLEAR_RESPONSES_BUTTON)) {
            final String errorMsg = "clear responses request, but helpdesk clear responses button is not enabled";
            LOGGER.error(pwmSession, errorMsg);
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,errorMsg));
            this.forwardToDetailJSP(req, resp);
            return;
        }

        //clear pwm intruder setting.
        pwmApplication.getIntruderManager().clear(RecordType.USERNAME, helpdeskBean.getUserInfoBean().getUsername());
        pwmApplication.getIntruderManager().convenience().clearUserIdentity(
                helpdeskBean.getUserInfoBean().getUserIdentity());

        try {
            final UserIdentity userIdentity = helpdeskBean.getUserInfoBean().getUserIdentity();

            OtpService service = pwmApplication.getOtpService();
            service.clearOTPUserConfiguration(userIdentity, helpdeskBean.getUserInfoBean().getUserGuid());
            {
                // mark the event log
                //@todo
                /*
                final UserAuditRecord auditRecord = pwmApplication.getAuditManager().createUserAuditRecord(
                        AuditEvent.EVENT,
                        pwmSession.getUserInfoBean().getUserIdentity(),
                        new Date(),
                        null,
                        userIdentity,
                        pwmSession.getSessionStateBean().getSrcAddress(),
                        pwmSession.getSessionStateBean().getSrcHostname()
                );
                pwmApplication.getAuditManager().submit(auditRecord);
                */
            }
        } catch (PwmOperationalException e) {
            final PwmError returnMsg = e.getError();
            final ErrorInformation error = new ErrorInformation(returnMsg, e.getMessage());
            ssBean.setSessionError(error);
            LOGGER.warn(pwmSession, "error clearing OTP secret for user '" + helpdeskBean.getUserInfoBean().getUserIdentity() + "'' " + error.toDebugStr() + ", " + e.getMessage());
        } catch (ChaiUnavailableException e) {
            pwmApplication.getStatisticsManager().incrementValue(Statistic.LDAP_UNAVAILABLE_COUNT);
            LOGGER.warn(pwmSession, "ChaiUnavailableException was thrown while clearing OTP secret: " + e.toString());
            throw e;
        }

        Helper.pause(1000); // delay before re-reading data
        populateHelpDeskBean(pwmApplication, pwmSession, helpdeskBean, helpdeskBean.getUserInfoBean().getUserIdentity());
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
