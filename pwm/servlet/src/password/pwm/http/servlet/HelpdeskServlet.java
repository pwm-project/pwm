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

package password.pwm.http.servlet;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiPasswordPolicyException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.ActionConfiguration;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.error.*;
import password.pwm.event.AuditEvent;
import password.pwm.event.UserAuditRecord;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.HelpdeskBean;
import password.pwm.i18n.Display;
import password.pwm.i18n.Message;
import password.pwm.ldap.LdapUserDataReader;
import password.pwm.ldap.UserDataReader;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.ldap.UserStatusReader;
import password.pwm.token.TokenPayload;
import password.pwm.token.TokenService;
import password.pwm.util.Helper;
import password.pwm.util.TimeDuration;
import password.pwm.util.intruder.IntruderManager;
import password.pwm.util.intruder.RecordType;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.operations.OtpService;
import password.pwm.util.otp.OTPUserRecord;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;
import password.pwm.ws.client.rest.RestTokenDataClient;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;

/**
 *
 *  Admin interaction servlet for reset user passwords.
 *
 *  @author BoAnSen
 *
 * */
public class HelpdeskServlet extends PwmServlet {

    private static final PwmLogger LOGGER = PwmLogger.forClass(HelpdeskServlet.class);
    private static final String TOKEN_NAME = HelpdeskServlet.class.getName();

    public enum HelpdeskAction implements PwmServlet.ProcessAction {
        doUnlock(PwmServlet.HttpMethod.POST),
        doClearOtpSecret(PwmServlet.HttpMethod.POST),
        search(PwmServlet.HttpMethod.POST),
        detail(PwmServlet.HttpMethod.POST),
        executeAction(PwmServlet.HttpMethod.POST),
        deleteUser(PwmServlet.HttpMethod.POST),
        validateOtpCode(PwmServlet.HttpMethod.POST),
        sendVerificationToken(PwmServlet.HttpMethod.POST),

        ;

        private final PwmServlet.HttpMethod method;

        HelpdeskAction(PwmServlet.HttpMethod method)
        {
            this.method = method;
        }

        public Collection<PwmServlet.HttpMethod> permittedMethods()
        {
            return Collections.singletonList(method);
        }
    }

    protected HelpdeskAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        try {
            return HelpdeskAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final HelpdeskBean helpdeskBean = (HelpdeskBean)pwmSession.getSessionBean(HelpdeskBean.class);

        if (!ssBean.isAuthenticated()) {
            pwmRequest.respondWithError(PwmError.ERROR_AUTHENTICATION_REQUIRED.toInfo());
            return;
        }

        if (!pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.HELPDESK)) {
            pwmRequest.respondWithError(PwmError.ERROR_UNAUTHORIZED.toInfo());
            return;
        }

        {
            final List<FormConfiguration> searchForm = pwmApplication.getConfig().readSettingAsForm(PwmSetting.HELPDESK_SEARCH_FORM);
            final Map<String,String> searchColumns = new LinkedHashMap<>();
            for (final FormConfiguration formConfiguration : searchForm) {
                searchColumns.put(formConfiguration.getName(),formConfiguration.getLabel(pwmSession.getSessionStateBean().getLocale()));
            }
            helpdeskBean.setSearchColumnHeaders(searchColumns);
        }

        final int helpdeskIdleTimeout = (int)pwmApplication.getConfig().readSettingAsLong(PwmSetting.HELPDESK_IDLE_TIMEOUT_SECONDS);
        pwmSession.setSessionTimeout(pwmRequest.getHttpServletRequest().getSession(),helpdeskIdleTimeout);

        final HelpdeskAction action = readProcessAction(pwmRequest);
        if (action != null) {
            pwmRequest.validatePwmFormID();

            switch (action) {
                case doUnlock:
                    processUnlockPassword(pwmRequest, helpdeskBean);
                    return;

                case doClearOtpSecret:
                    processClearOtpSecret(pwmRequest, helpdeskBean);
                    return;

                case search:
                    restSearchRequest(pwmRequest, helpdeskBean);
                    return;

                case detail:
                    processDetailRequest(pwmRequest, helpdeskBean);
                    return;

                case executeAction:
                    processExecuteActionRequest(pwmRequest, helpdeskBean);
                    return;

                case deleteUser:
                    restDeleteUserRequest(pwmRequest, helpdeskBean);
                    return;

                case validateOtpCode:
                    restValidateOtpCodeRequest(pwmRequest, helpdeskBean);
                    return;

                case sendVerificationToken:
                    restSendVerificationTokenRequest(pwmRequest, helpdeskBean);
                    return;
            }
        }

        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.HELPDESK_SEARCH);
    }

    private void processExecuteActionRequest(final PwmRequest pwmRequest, final HelpdeskBean helpdeskBean)
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final List<ActionConfiguration> actionConfigurations = pwmRequest.getConfig().readSettingAsAction(PwmSetting.HELPDESK_ACTIONS);
        final String requestedName = pwmRequest.readParameterAsString("name");
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
            LOGGER.debug(pwmRequest, errorInformation.toDebugStr());
            final RestResultBean restResultBean = RestResultBean.fromError(errorInformation, pwmRequest);
            pwmRequest.outputJsonResult(restResultBean);
            return;
        }

        if (helpdeskBean.getUserInfoBean() == null) {
            final String errorMsg = "no user selected: " + requestedName;
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg);
            LOGGER.debug(pwmRequest, errorInformation.toDebugStr());
            final RestResultBean restResultBean = RestResultBean.fromError(errorInformation, pwmRequest);
            pwmRequest.outputJsonResult(restResultBean);
            return;
        }

        final boolean useProxy = pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY);
        try {
            final UserIdentity userIdentity = helpdeskBean.getUserInfoBean().getUserIdentity();
            final PwmSession pwmSession = pwmRequest.getPwmSession();
            final ActionExecutor actionExecutor = new ActionExecutor(pwmRequest.getPwmApplication());
            final ActionExecutor.ActionExecutorSettings settings = new ActionExecutor.ActionExecutorSettings();
            final ChaiUser chaiUser = useProxy ?
                    pwmRequest.getPwmApplication().getProxiedChaiUser(userIdentity) :
                    pwmRequest.getPwmSession().getSessionManager().getActor(pwmRequest.getPwmApplication(), userIdentity);
            settings.setUserIdentity(userIdentity);
            settings.setChaiUser(chaiUser);
            settings.setExpandPwmMacros(true);
            actionExecutor.executeAction(action,settings,pwmRequest.getPwmSession());
            // mark the event log
            {
                final UserAuditRecord auditRecord = pwmRequest.getPwmApplication().getAuditManager().createUserAuditRecord(
                        AuditEvent.HELPDESK_ACTION,
                        pwmSession.getUserInfoBean().getUserIdentity(),
                        action.getName(),
                        helpdeskBean.getUserInfoBean().getUserIdentity(),
                        pwmSession.getSessionStateBean().getSrcAddress(),
                        pwmSession.getSessionStateBean().getSrcHostname()
                );
                pwmRequest.getPwmApplication().getAuditManager().submit(auditRecord);
            }
            final RestResultBean restResultBean = RestResultBean.forSuccessMessage(pwmRequest, Message.SUCCESS_ACTION);

            pwmRequest.outputJsonResult(restResultBean);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmRequest, e.getErrorInformation().toDebugStr());
            final RestResultBean restResultBean = RestResultBean.fromError(e.getErrorInformation(), pwmRequest);
            pwmRequest.outputJsonResult(restResultBean);
        }
    }

    private void restDeleteUserRequest(
            final PwmRequest pwmRequest,
            final HelpdeskBean helpdeskBean
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        final String userKey = pwmRequest.readParameterAsString("userKey");
        if (userKey.length() < 1) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"userKey parameter is missing");
            pwmRequest.setResponseError(errorInformation);
            pwmRequest.respondWithError(errorInformation, false);
            return;
        }

        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmApplication.getConfig());
        LOGGER.info(pwmSession, "received deleteUser request by " + pwmSession.getUserInfoBean().getUserIdentity().toString() + " for user " + userIdentity.toString());

        // check user identity matches helpdesk bean user
        if (userIdentity == null || helpdeskBean == null || helpdeskBean.getUserInfoBean() == null || !userIdentity.equals(helpdeskBean.getUserInfoBean().getUserIdentity())) {
            pwmRequest.setResponseError(new ErrorInformation(PwmError.ERROR_UNKNOWN,"requested user for delete  is not currently selected user"));
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.HELPDESK_SEARCH);
            return;
        }

        // execute user delete operation
        ChaiProvider provider = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY)
                ? pwmApplication.getProxyChaiProvider(userIdentity.getLdapProfileID())
                : pwmSession.getSessionManager().getChaiProvider();


        try {
            provider.deleteEntry(userIdentity.getUserDN());
        } catch (ChaiOperationException e) {
            final String errorMsg = "error while attempting to delete user " + userIdentity.toString() + ", error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            LOGGER.debug(pwmRequest, errorMsg);
            pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
            return;
        }

        // mark the event log
        {
            final UserAuditRecord auditRecord = pwmApplication.getAuditManager().createUserAuditRecord(
                    AuditEvent.HELPDESK_DELETE_USER,
                    pwmSession.getUserInfoBean().getUserIdentity(),
                    null,
                    userIdentity,
                    pwmSession.getSessionStateBean().getSrcAddress(),
                    pwmSession.getSessionStateBean().getSrcHostname()
            );
            pwmApplication.getAuditManager().submit(auditRecord);
        }

        LOGGER.info(pwmSession, "user " + userIdentity + " has been deleted");
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setSuccessMessage(Message.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(),Message.SUCCESS_UNKNOWN,pwmApplication.getConfig()));
        pwmRequest.outputJsonResult(restResultBean);
    }

    private void processDetailRequest(
            final PwmRequest pwmRequest,
            final HelpdeskBean helpdeskBean
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        helpdeskBean.setUserInfoBean(null);
        final String userKey = pwmRequest.readParameterAsString("userKey");
        if (userKey.length() < 1) {
            pwmRequest.respondWithError(
                    new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER, "userKey parameter is missing"));
            return;
        }

        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmRequest.getConfig());
        processDetailRequest(pwmRequest, helpdeskBean, userIdentity);
        final UserAuditRecord auditRecord = pwmRequest.getPwmApplication().getAuditManager().createUserAuditRecord(
                AuditEvent.HELPDESK_VIEW_DETAIL,
                pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity(),
                null,
                userIdentity,
                pwmRequest.getSessionLabel().getSrcAddress(),
                pwmRequest.getSessionLabel().getSrcHostname()
        );
        pwmRequest.getPwmApplication().getAuditManager().submit(auditRecord);

    }


    private void processDetailRequest(
            final PwmRequest pwmRequest,
            final HelpdeskBean helpdeskBean,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        if (pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity().equals(userIdentity)) {
            final String errorMsg = "cannot select self";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,errorMsg);
            LOGGER.debug(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        populateHelpDeskBean(pwmRequest, helpdeskBean, userIdentity);

        StatisticsManager.incrementStat(pwmRequest, Statistic.HELPDESK_USER_LOOKUP);
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.HELPDESK_DETAIL);
    }

    private void restSearchRequest(
            final PwmRequest pwmRequest,
            final HelpdeskBean helpdeskBean
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final Map<String, String> valueMap = pwmRequest.readBodyAsJsonStringMap();
        final String username = valueMap.get("username");

        final boolean useProxy = pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY);
        helpdeskBean.setSearchString(username);
        helpdeskBean.setUserInfoBean(null);
        final List<FormConfiguration> searchForm = pwmRequest.getConfig().readSettingAsForm(PwmSetting.HELPDESK_SEARCH_FORM);
        final int maxResults = (int)pwmRequest.getConfig().readSettingAsLong(PwmSetting.HELPDESK_RESULT_LIMIT);

        if (helpdeskBean.getSearchString() == null || helpdeskBean.getSearchString().length() < 1) {
            final HashMap<String,Object> emptyResults = new HashMap<>();
            emptyResults.put("searchResults",new ArrayList<Map<String,String>>());
            emptyResults.put("sizeExceeded",false);
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(emptyResults);
            pwmRequest.outputJsonResult(restResultBean);
            return;
        }

        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel());
        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        searchConfiguration.setContexts(
                pwmRequest.getConfig().readSettingAsStringArray(PwmSetting.HELPDESK_SEARCH_BASE));
        searchConfiguration.setEnableContextValidation(false);
        searchConfiguration.setUsername(username);
        searchConfiguration.setEnableValueEscaping(false);
        searchConfiguration.setFilter(pwmRequest.getConfig().readSettingAsString(PwmSetting.HELPDESK_SEARCH_FILTER));
        if (!useProxy) {
            final UserIdentity loggedInUser = pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity();
            searchConfiguration.setLdapProfile(loggedInUser.getLdapProfileID());
            searchConfiguration.setChaiProvider(getChaiUser(pwmRequest, loggedInUser).getChaiProvider());
        }

        final UserSearchEngine.UserSearchResults results;
        final boolean sizeExceeded;
        try {
            final Locale locale = pwmRequest.getLocale();
            results = userSearchEngine.performMultiUserSearchFromForm(locale, searchConfiguration, maxResults, searchForm);
            sizeExceeded = results.isSizeExceeded();
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInformation = e.getErrorInformation();
            LOGGER.error(pwmRequest, errorInformation);
            final RestResultBean restResultBean = RestResultBean.fromError(errorInformation, pwmRequest);
            restResultBean.setData(new ArrayList<Map<String, String>>());
            pwmRequest.outputJsonResult(restResultBean);
            return;
        }

        final RestResultBean restResultBean = new RestResultBean();
        final LinkedHashMap<String,Object> outputData = new LinkedHashMap<>();
        outputData.put("searchResults",new ArrayList<>(results.resultsAsJsonOutput(pwmRequest.getPwmApplication())));
        outputData.put("sizeExceeded", sizeExceeded);
        restResultBean.setData(outputData);
        pwmRequest.outputJsonResult(restResultBean);
    }



    private static void populateHelpDeskBean(
            final PwmRequest pwmRequest,
            final HelpdeskBean helpdeskBean,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final ChaiUser theUser = getChaiUser(pwmRequest, userIdentity);

        if (!theUser.isValid()) {
            return;
        }

        final UserInfoBean uiBean = new UserInfoBean();
        final Locale userLocale = pwmRequest.getLocale();
        final UserStatusReader userStatusReader = new UserStatusReader(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel());
        userStatusReader.populateUserInfoBean(uiBean, userLocale, userIdentity, theUser.getChaiProvider());
        helpdeskBean.setUserInfoBean(uiBean);
        HelpdeskBean.AdditionalUserInfo additionalUserInfo = new HelpdeskBean.AdditionalUserInfo();
        helpdeskBean.setAdditionalUserInfo(additionalUserInfo);

        try {
            additionalUserInfo.setIntruderLocked(theUser.isPasswordLocked());
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "unexpected error reading intruder lock status for user '" + userIdentity + "', " + e.getMessage());
        }

        try {
            additionalUserInfo.setAccountEnabled(theUser.isAccountEnabled());
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "unexpected error reading account enabled status for user '" + userIdentity + "', " + e.getMessage());
        }

        try {
            additionalUserInfo.setLastLoginTime(theUser.readLastLoginTime());
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "unexpected error reading last login time for user '" + userIdentity + "', " + e.getMessage());
        }

        try {
            additionalUserInfo.setPwmIntruder(false);
            pwmRequest.getPwmApplication().getIntruderManager().check(RecordType.USERNAME, uiBean.getUsername());
            pwmRequest.getPwmApplication().getIntruderManager().convenience().checkUserIdentity(userIdentity);
        } catch (Exception e) {
            additionalUserInfo.setPwmIntruder(true);
        }

        try {
            additionalUserInfo.setUserHistory(pwmRequest.getPwmApplication().getAuditManager().readUserHistory(uiBean));
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "unexpected error reading userHistory for user '" + userIdentity + "', " + e.getMessage());
        }

        if (uiBean.getPasswordLastModifiedTime() != null) {
            final TimeDuration passwordSetDelta = TimeDuration.fromCurrent(uiBean.getPasswordLastModifiedTime());
            additionalUserInfo.setPasswordSetDelta(passwordSetDelta.asLongString(pwmRequest.getLocale()));
        } else {
            additionalUserInfo.setPasswordSetDelta(Display.getLocalizedMessage(pwmRequest.getLocale(),"Value_NotApplicable",pwmRequest.getConfig()));
        }

        final Configuration config = pwmRequest.getConfig();
        final UserDataReader userDataReader = config.readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY)
                ? LdapUserDataReader.appProxiedReader(pwmRequest.getPwmApplication(), userIdentity)
                : LdapUserDataReader.selfProxiedReader(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), userIdentity);

        {
            final List<FormConfiguration> detailFormConfig = config.readSettingAsForm(PwmSetting.HELPDESK_DETAIL_FORM);
            final Map<FormConfiguration,String> formData = new LinkedHashMap<>();
            for (final FormConfiguration formConfiguration : detailFormConfig) {
                formData.put(formConfiguration,"");
            }
            UpdateProfileServlet.populateFormFromLdap(detailFormConfig, pwmRequest.getPwmSession(), formData, userDataReader);
            helpdeskBean.getAdditionalUserInfo().setSearchDetails(formData);
        }

        final String configuredDisplayName = pwmRequest.getConfig().readSettingAsString(PwmSetting.HELPDESK_DETAIL_DISPLAY_NAME);
        if (configuredDisplayName != null && !configuredDisplayName.isEmpty()) {
            final MacroMachine macroMachine = new MacroMachine(pwmRequest.getPwmApplication(), helpdeskBean.getUserInfoBean(), null, userDataReader);
            final String displayName = macroMachine.expandMacros(configuredDisplayName);
            helpdeskBean.setUserDisplayName(displayName);
        }
    }

    private void processUnlockPassword(
            final PwmRequest pwmRequest,
            final HelpdeskBean helpdeskBean
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        if (helpdeskBean.getUserInfoBean() == null) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, "password unlock request, but no user result in search");
            LOGGER.error(pwmRequest, errorInformation);
            pwmRequest.setResponseError(errorInformation);
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.HELPDESK_DETAIL);
            return;
        }

        if (!pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE_UNLOCK)) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, "password unlock request, but helpdesk unlock is not enabled");
            LOGGER.error(pwmRequest, errorInformation);
            pwmRequest.setResponseError(errorInformation);
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.HELPDESK_DETAIL);
            return;
        }

        //clear pwm intruder setting.
        final IntruderManager intruderManager = pwmRequest.getPwmApplication().getIntruderManager();
        intruderManager.clear(RecordType.USERNAME, helpdeskBean.getUserInfoBean().getUsername());
        intruderManager.convenience().clearUserIdentity(
                helpdeskBean.getUserInfoBean().getUserIdentity());

        final boolean useProxy = pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY);
        try {
            final UserIdentity userIdentity = helpdeskBean.getUserInfoBean().getUserIdentity();
            final ChaiUser chaiUser = useProxy ?
                    pwmRequest.getPwmApplication().getProxiedChaiUser(userIdentity) :
                    pwmRequest.getPwmSession().getSessionManager().getActor(pwmRequest.getPwmApplication(), userIdentity);
            chaiUser.unlockPassword();
            {
                // mark the event log
                final UserAuditRecord auditRecord = pwmRequest.getPwmApplication().getAuditManager().createUserAuditRecord(
                        AuditEvent.HELPDESK_UNLOCK_PASSWORD,
                        pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity(),
                        null,
                        userIdentity,
                        pwmRequest.getSessionLabel().getSrcAddress(),
                        pwmRequest.getSessionLabel().getSrcHostname()
                );
                pwmRequest.getPwmApplication().getAuditManager().submit(auditRecord);
            }
        } catch (ChaiPasswordPolicyException e) {
            final ChaiError passwordError = e.getErrorCode();
            final PwmError pwmError = PwmError.forChaiError(passwordError);
            pwmRequest.setResponseError(new ErrorInformation(pwmError == null ? PwmError.PASSWORD_UNKNOWN_VALIDATION : pwmError));
            LOGGER.trace(pwmRequest, "ChaiPasswordPolicyException was thrown while resetting password: " + e.toString());
        } catch (ChaiOperationException e) {
            final PwmError returnMsg = PwmError.forChaiError(e.getErrorCode()) == null ? PwmError.ERROR_UNKNOWN : PwmError.forChaiError(e.getErrorCode());
            final ErrorInformation error = new ErrorInformation(returnMsg, e.getMessage());
            pwmRequest.setResponseError(error);
            LOGGER.warn(pwmRequest, "error resetting password for user '" + helpdeskBean.getUserInfoBean().getUserIdentity() + "'' " + error.toDebugStr() + ", " + e.getMessage());
        }

        Helper.pause(1000); // delay before re-reading data
        populateHelpDeskBean(pwmRequest, helpdeskBean, helpdeskBean.getUserInfoBean().getUserIdentity());
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.HELPDESK_DETAIL);
    }

    private void restValidateOtpCodeRequest(
            final PwmRequest pwmRequest,
            final HelpdeskBean helpdeskBean
    )
            throws IOException, PwmUnrecoverableException
    {
        if (!pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE_OTP_VERIFY)) {
            pwmRequest.outputJsonResult(RestResultBean.fromError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo()));
            return;
        }

        final Map<String,String> inputRecord = pwmRequest.readBodyAsJsonStringMap();
        final String code = inputRecord.get("code");
        final OTPUserRecord otpUserRecord = helpdeskBean.getUserInfoBean().getOtpUserRecord();
        try {
            final boolean passed = pwmRequest.getPwmApplication().getOtpService().validateToken(
                    pwmRequest.getPwmSession(),
                    helpdeskBean.getUserInfoBean().getUserIdentity(),
                    otpUserRecord,
                    code,
                    false
            );
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(passed);
            pwmRequest.outputJsonResult(restResultBean);
        } catch (PwmOperationalException e) {
            pwmRequest.outputJsonResult(RestResultBean.fromError(e.getErrorInformation(), pwmRequest));
        }
    }

    private void restSendVerificationTokenRequest(
            final PwmRequest pwmRequest,
            final HelpdeskBean helpdeskBean
    )
            throws IOException, PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        MessageSendMethod tokenSendMethod = config.readSettingAsEnum(PwmSetting.HELPDESK_TOKEN_SEND_METHOD, MessageSendMethod.class);
        if (tokenSendMethod == MessageSendMethod.CHOICE_SMS_EMAIL) {
            final Map<String,String> bodyParams = pwmRequest.readBodyAsJsonStringMap();
            if (bodyParams != null && bodyParams.containsKey("method")) {
                switch (bodyParams.get("method")) {
                    case "sms":
                        tokenSendMethod = MessageSendMethod.SMSONLY;
                        break;

                    case "email":
                        tokenSendMethod = MessageSendMethod.EMAILONLY;
                        break;
                }
            }
            if (tokenSendMethod == MessageSendMethod.CHOICE_SMS_EMAIL) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_MISSING_CONTACT, "unable to determine appropriate send method, missing method parameter indicaton from operator");
                LOGGER.error(pwmRequest,errorInformation);
                pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation,pwmRequest));
                return;
            }
        }



        final UserInfoBean userInfoBean = helpdeskBean.getUserInfoBean();
        final UserIdentity userIdentity = userInfoBean.getUserIdentity();
        final Map<String,String> tokenMapData = new HashMap<>();

        final RestTokenDataClient.TokenDestinationData inputDestinationData = new RestTokenDataClient.TokenDestinationData(
                userInfoBean.getUserEmailAddress(),
                userInfoBean.getUserSmsNumber(),
                null
        );

        final RestTokenDataClient restTokenDataClient = new RestTokenDataClient(pwmRequest.getPwmApplication());
        final RestTokenDataClient.TokenDestinationData outputDestrestTokenDataClient = restTokenDataClient.figureDestTokenDisplayString(
                pwmRequest.getSessionLabel(),
                inputDestinationData,
                userIdentity,
                pwmRequest.getLocale());

        final String tokenDestinationAddress = outputDestrestTokenDataClient.getDisplayValue();
        final Set<String> destinationValues = new HashSet<>();
        if (outputDestrestTokenDataClient.getEmail() != null) {
            destinationValues.add(outputDestrestTokenDataClient.getEmail());
        }
        if (outputDestrestTokenDataClient.getSms() != null) {
            destinationValues.add(outputDestrestTokenDataClient.getSms());
        }

        final String tokenKey;
        TokenPayload tokenPayload;
        try {
            tokenPayload = pwmRequest.getPwmApplication().getTokenService().createTokenPayload(TOKEN_NAME, tokenMapData, userIdentity, destinationValues);
            tokenKey = pwmRequest.getPwmApplication().getTokenService().generateNewToken(tokenPayload, pwmRequest.getSessionLabel());
        } catch (PwmOperationalException e) {
            throw new PwmUnrecoverableException(e.getErrorInformation());
        }

        LOGGER.debug(pwmRequest, "generated token code for " + userIdentity.toDeliminatedKey());

        final EmailItemBean emailItemBean = config.readSettingAsEmail(PwmSetting.EMAIL_HELPDESK_TOKEN, pwmRequest.getLocale());
        final String smsMessage = config.readSettingAsLocalizedString(PwmSetting.SMS_HELPDESK_TOKEN_TEXT, pwmRequest.getLocale());

        final MacroMachine macroMachine = MacroMachine.forUser(pwmRequest, userIdentity);

        try {
            TokenService.TokenSender.sendToken(
                    pwmRequest.getPwmApplication(),
                    userInfoBean,
                    macroMachine,
                    emailItemBean,
                    tokenSendMethod,
                    outputDestrestTokenDataClient.getEmail(),
                    outputDestrestTokenDataClient.getSms(),
                    smsMessage,
                    tokenKey
            );
        } catch (ChaiUnavailableException e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage());
            LOGGER.error(pwmRequest,errorInformation);
            pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation,pwmRequest));
            return;
        } catch (PwmException e) {
            LOGGER.error(pwmRequest,e.getErrorInformation());
            pwmRequest.outputJsonResult(RestResultBean.fromError(e.getErrorInformation(),pwmRequest));
            return;
        }

        StatisticsManager.incrementStat(pwmRequest,Statistic.HELPDESK_TOKENS_SENT);
        final HashMap<String,String> output = new HashMap<>();
        output.put("destination",tokenDestinationAddress);
        output.put("token",tokenKey);
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(output);
        pwmRequest.outputJsonResult(restResultBean);
    }

    private void processClearOtpSecret(
            final PwmRequest pwmRequest,
            final HelpdeskBean helpdeskBean
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        if (helpdeskBean.getUserInfoBean() == null) {
            final String errorMsg = "password unlock request, but no user result in search";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            LOGGER.error(pwmRequest, errorMsg);
            pwmRequest.setResponseError(errorInformation);
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.HELPDESK_DETAIL);
            return;
        }

        if (!pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_CLEAR_RESPONSES_BUTTON)) {
            final String errorMsg = "clear responses request, but helpdesk clear responses button is not enabled";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg);
            LOGGER.error(pwmRequest, errorMsg);
            pwmRequest.setResponseError(errorInformation);
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.HELPDESK_DETAIL);
            return;
        }

        //clear pwm intruder setting.
        pwmRequest.getPwmApplication().getIntruderManager().clear(RecordType.USERNAME, helpdeskBean.getUserInfoBean().getUsername());
        pwmRequest.getPwmApplication().getIntruderManager().convenience().clearUserIdentity(
                helpdeskBean.getUserInfoBean().getUserIdentity());

        try {
            final UserIdentity userIdentity = helpdeskBean.getUserInfoBean().getUserIdentity();

            OtpService service = pwmRequest.getPwmApplication().getOtpService();
            service.clearOTPUserConfiguration(pwmRequest.getPwmSession(), userIdentity);
            {
                // mark the event log
                final UserAuditRecord auditRecord = pwmRequest.getPwmApplication().getAuditManager().createUserAuditRecord(
                        AuditEvent.HELPDESK_CLEAR_OTP_SECRET,
                        pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity(),
                        null,
                        userIdentity,
                        pwmRequest.getSessionLabel().getSrcAddress(),
                        pwmRequest.getSessionLabel().getSrcHostname()
                );
                pwmRequest.getPwmApplication().getAuditManager().submit(auditRecord);
            }
        } catch (PwmOperationalException e) {
            final PwmError returnMsg = e.getError();
            final ErrorInformation error = new ErrorInformation(returnMsg, e.getMessage());
            pwmRequest.setResponseError(error);
            LOGGER.warn(pwmRequest, "error clearing OTP secret for user '" + helpdeskBean.getUserInfoBean().getUserIdentity() + "'' " + error.toDebugStr() + ", " + e.getMessage());
        }

        Helper.pause(1000); // delay before re-reading data
        populateHelpDeskBean(pwmRequest, helpdeskBean, helpdeskBean.getUserInfoBean().getUserIdentity());
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.HELPDESK_DETAIL);
    }

    private static ChaiUser getChaiUser(final PwmRequest pwmRequest, final UserIdentity userIdentity)
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final boolean useProxy = pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY);
        return useProxy ?
                pwmRequest.getPwmApplication().getProxiedChaiUser(userIdentity) :
                pwmRequest.getPwmSession().getSessionManager().getActor(pwmRequest.getPwmApplication(), userIdentity);
    }
}
