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

package password.pwm.http.servlet.helpdesk;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiPasswordPolicyException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.*;
import password.pwm.config.option.HelpdeskClearResponseMode;
import password.pwm.config.option.HelpdeskUIMode;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.error.*;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.i18n.Display;
import password.pwm.i18n.Message;
import password.pwm.ldap.*;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.HelpdeskAuditRecord;
import password.pwm.svc.intruder.IntruderManager;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.svc.token.TokenService;
import password.pwm.util.Helper;
import password.pwm.util.JsonUtil;
import password.pwm.util.LocaleHelper;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.operations.OtpService;
import password.pwm.util.otp.OTPUserRecord;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.*;

/**
 *
 *  Admin interaction servlet for reset user passwords.
 *
 *  @author BoAnSen
 *
 * */

@WebServlet(
        name="HelpdeskServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/helpdesk",
                PwmConstants.URL_PREFIX_PRIVATE + "/Helpdesk",
        }
)
public class HelpdeskServlet extends AbstractPwmServlet {

    private static final PwmLogger LOGGER = PwmLogger.forClass(HelpdeskServlet.class);

    public enum HelpdeskAction implements AbstractPwmServlet.ProcessAction {
        unlockIntruder(HttpMethod.POST),
        clearOtpSecret(HttpMethod.POST),
        search(HttpMethod.POST),
        detail(HttpMethod.POST),
        executeAction(HttpMethod.POST),
        deleteUser(HttpMethod.POST),
        validateOtpCode(HttpMethod.POST),
        sendVerificationToken(HttpMethod.POST),
        clientData(HttpMethod.GET),

        ;

        private final HttpMethod method;

        HelpdeskAction(HttpMethod method)
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods()
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

        if (!ssBean.isAuthenticated()) {
            pwmRequest.respondWithError(PwmError.ERROR_AUTHENTICATION_REQUIRED.toInfo());
            return;
        }

        final HelpdeskProfile helpdeskProfile = pwmRequest.getPwmSession().getSessionManager().getHelpdeskProfile(pwmApplication);
        if (helpdeskProfile == null) {
            pwmRequest.respondWithError(PwmError.ERROR_UNAUTHORIZED.toInfo());
            return;
        }

        final int helpdeskIdleTimeout = (int)helpdeskProfile.readSettingAsLong(PwmSetting.HELPDESK_IDLE_TIMEOUT_SECONDS);
        if (helpdeskIdleTimeout > 0) {
            pwmSession.setSessionTimeout(pwmRequest.getHttpServletRequest().getSession(), helpdeskIdleTimeout);
        }

        final HelpdeskAction action = readProcessAction(pwmRequest);
        if (action != null) {
            pwmRequest.validatePwmFormID();

            switch (action) {
                case search:
                    restSearchRequest(pwmRequest, helpdeskProfile);
                    return;

                case detail:
                    processDetailRequest(pwmRequest, helpdeskProfile);
                    return;

                case executeAction:
                    processExecuteActionRequest(pwmRequest, helpdeskProfile);
                    return;

                case deleteUser:
                    restDeleteUserRequest(pwmRequest, helpdeskProfile);
                    return;

                case validateOtpCode:
                    restValidateOtpCodeRequest(pwmRequest, helpdeskProfile);
                    return;

                case unlockIntruder:
                    restUnlockPassword(pwmRequest, helpdeskProfile);
                    return;

                case clearOtpSecret:
                    restClearOtpSecret(pwmRequest, helpdeskProfile);
                    return;

                case sendVerificationToken:
                    restSendVerificationTokenRequest(pwmRequest, helpdeskProfile);
                    return;

                case clientData:
                    restClientData(pwmRequest, helpdeskProfile);
                    return;
            }
        }

        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.HELPDESK_SEARCH);
    }

    private void restClientData(final PwmRequest pwmRequest, final HelpdeskProfile helpdeskProfile)
            throws IOException, PwmUnrecoverableException {
        final HelpdeskClientDataBean returnValues = new HelpdeskClientDataBean();
        { // search page
            final List<FormConfiguration> searchForm = helpdeskProfile.readSettingAsForm(PwmSetting.HELPDESK_SEARCH_FORM);
            final Map<String, String> searchColumns = new LinkedHashMap<>();
            for (final FormConfiguration formConfiguration : searchForm) {
                searchColumns.put(formConfiguration.getName(), formConfiguration.getLabel(pwmRequest.getLocale()));
            }
            returnValues.setHelpdesk_search_columns(searchColumns);
        }
        { /// detail page
            returnValues.setHelpdesk_setting_maskPasswords(helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_PASSWORD_MASKVALUE));
            returnValues.setHelpdesk_setting_clearResponses(helpdeskProfile.readSettingAsEnum(PwmSetting.HELPDESK_CLEAR_RESPONSES,HelpdeskClearResponseMode.class));
            returnValues.setHelpdesk_setting_PwUiMode(helpdeskProfile.readSettingAsEnum(PwmSetting.HELPDESK_SET_PASSWORD_MODE,HelpdeskUIMode.class));
            returnValues.setHelpdesk_setting_tokenSendMethod(helpdeskProfile.readSettingAsEnum(PwmSetting.HELPDESK_TOKEN_SEND_METHOD, MessageSendMethod.class));
        }
        { //actions
            final List<ActionConfiguration> actionConfigurations = helpdeskProfile.readSettingAsAction(PwmSetting.HELPDESK_ACTIONS);
            final Map<String,Map<String,String>> actions = new LinkedHashMap<>();
            for (final ActionConfiguration actionConfiguration : actionConfigurations) {
                final Map<String,String> actionInfoMap = new LinkedHashMap<>();
                actionInfoMap.put("name", actionConfiguration.getName());
                actionInfoMap.put("description", actionConfiguration.getDescription());
                actions.put(actionConfiguration.getName(), actionInfoMap);
            }

            returnValues.setActions(actions);
        }
        final RestResultBean restResultBean = new RestResultBean(returnValues);
        LOGGER.trace(pwmRequest, "returning clientData: " + JsonUtil.serialize(restResultBean));
        pwmRequest.outputJsonResult(restResultBean);
    }

    private void processExecuteActionRequest(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final String userKey = pwmRequest.readBodyAsJsonStringMap(PwmHttpRequestWrapper.Flag.BypassValidation).get("userKey");
        if (userKey == null || userKey.length() < 1) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"userKey parameter is missing");
            pwmRequest.setResponseError(errorInformation);
            pwmRequest.respondWithError(errorInformation, false);
            return;
        }
        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmRequest.getPwmApplication());
        LOGGER.debug(pwmRequest, "received executeAction request for user " + userIdentity.toString());

        final List<ActionConfiguration> actionConfigurations = helpdeskProfile.readSettingAsAction(PwmSetting.HELPDESK_ACTIONS);
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

        // check if user should be seen by actor
        checkIfUserIdentityViewable(pwmRequest, helpdeskProfile, userIdentity);

        final boolean useProxy = helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY);
        try {
            final PwmSession pwmSession = pwmRequest.getPwmSession();

            final ChaiUser chaiUser = useProxy ?
                    pwmRequest.getPwmApplication().getProxiedChaiUser(userIdentity) :
                    pwmRequest.getPwmSession().getSessionManager().getActor(pwmRequest.getPwmApplication(), userIdentity);
            final MacroMachine macroMachine = MacroMachine.forUser(pwmRequest, userIdentity);
            final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings(pwmRequest.getPwmApplication(),chaiUser)
                    .setExpandPwmMacros(true)
                    .setMacroMachine(macroMachine)
                    .createActionExecutor();

            actionExecutor.executeAction(action,pwmRequest.getPwmSession());

            // mark the event log
            {
                final HelpdeskAuditRecord auditRecord = pwmRequest.getPwmApplication().getAuditManager().createHelpdeskAuditRecord(
                        AuditEvent.HELPDESK_ACTION,
                        pwmSession.getUserInfoBean().getUserIdentity(),
                        action.getName(),
                        userIdentity,
                        pwmSession.getSessionStateBean().getSrcAddress(),
                        pwmSession.getSessionStateBean().getSrcHostname()
                );
                pwmRequest.getPwmApplication().getAuditManager().submit(auditRecord);
            }
            final RestResultBean restResultBean = RestResultBean.forSuccessMessage(pwmRequest.getLocale(), pwmRequest.getConfig(), Message.Success_Action, action.getName());

            pwmRequest.outputJsonResult(restResultBean);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmRequest, e.getErrorInformation().toDebugStr());
            final RestResultBean restResultBean = RestResultBean.fromError(e.getErrorInformation(), pwmRequest);
            pwmRequest.outputJsonResult(restResultBean);
        }
    }

    private void restDeleteUserRequest(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        final String userKey = pwmRequest.readParameterAsString("userKey", PwmHttpRequestWrapper.Flag.BypassValidation);
        if (userKey.length() < 1) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"userKey parameter is missing");
            pwmRequest.setResponseError(errorInformation);
            pwmRequest.respondWithError(errorInformation, false);
            return;
        }

        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmApplication);
        LOGGER.info(pwmSession, "received deleteUser request by " + pwmSession.getUserInfoBean().getUserIdentity().toString() + " for user " + userIdentity.toString());

        // check if user should be seen by actor
        checkIfUserIdentityViewable(pwmRequest, helpdeskProfile, userIdentity);

        // execute user delete operation
        ChaiProvider provider = helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY)
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
            final HelpdeskAuditRecord auditRecord = pwmApplication.getAuditManager().createHelpdeskAuditRecord(
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
        restResultBean.setSuccessMessage(Message.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(),Message.Success_Unknown,pwmApplication.getConfig()));
        pwmRequest.outputJsonResult(restResultBean);
    }

    private void processDetailRequest(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final String userKey = pwmRequest.readParameterAsString("userKey", PwmHttpRequestWrapper.Flag.BypassValidation);
        if (userKey.length() < 1) {
            pwmRequest.respondWithError(
                    new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER, "userKey parameter is missing"));
            return;
        }

        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmRequest.getPwmApplication()).canonicalized(pwmRequest.getPwmApplication());
        processDetailRequest(pwmRequest, helpdeskProfile, userIdentity);
        final HelpdeskAuditRecord auditRecord = pwmRequest.getPwmApplication().getAuditManager().createHelpdeskAuditRecord(
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
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final UserIdentity actorUserIdentity = pwmRequest.getUserInfoIfLoggedIn().canonicalized(pwmRequest.getPwmApplication());

        if (actorUserIdentity.canonicalEquals(userIdentity, pwmRequest.getPwmApplication())) {
            final String errorMsg = "cannot select self";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,errorMsg);
            LOGGER.debug(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation, false);
            return;
        }
        LOGGER.trace(pwmRequest, "helpdesk detail view request for user details of " + userIdentity.toString() + " by actor " + actorUserIdentity.toString());

        final HelpdeskDetailInfoBean helpdeskDetailInfoBean = makeHelpdeskDetailInfo(pwmRequest, helpdeskProfile, userIdentity);
        pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.HelpdeskDetail, helpdeskDetailInfoBean);

        if (helpdeskDetailInfoBean != null && helpdeskDetailInfoBean.getUserInfoBean() != null) {
            final String obfuscatedDN = helpdeskDetailInfoBean.getUserInfoBean().getUserIdentity().toObfuscatedKey(pwmRequest.getPwmApplication());
            pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.HelpdeskObfuscatedDN, obfuscatedDN);
            pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.HelpdeskUsername, helpdeskDetailInfoBean.getUserInfoBean().getUsername());
        }

        StatisticsManager.incrementStat(pwmRequest, Statistic.HELPDESK_USER_LOOKUP);
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.HELPDESK_DETAIL);
    }

    private void restSearchRequest(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final Map<String, String> valueMap = pwmRequest.readBodyAsJsonStringMap();
        final String username = valueMap.get("username");

        final boolean useProxy = helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY);
        final List<FormConfiguration> searchForm = helpdeskProfile.readSettingAsForm(PwmSetting.HELPDESK_SEARCH_FORM);
        final int maxResults = (int)helpdeskProfile.readSettingAsLong(PwmSetting.HELPDESK_RESULT_LIMIT);

        if (username == null ||username.isEmpty()) {
            final HelpdeskSearchResultsBean emptyResults = new HelpdeskSearchResultsBean();
            emptyResults.setSearchResults(new ArrayList<Map<String,Object>>());
            emptyResults.setSizeExceeded(false);
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(emptyResults);
            pwmRequest.outputJsonResult(restResultBean);
            return;
        }

        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel());
        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        searchConfiguration.setContexts(helpdeskProfile.readSettingAsStringArray(PwmSetting.HELPDESK_SEARCH_BASE));
        searchConfiguration.setEnableContextValidation(false);
        searchConfiguration.setUsername(username);
        searchConfiguration.setEnableValueEscaping(false);
        searchConfiguration.setFilter(getSearchFilter(pwmRequest.getConfig(),helpdeskProfile));

        if (!useProxy) {
            final UserIdentity loggedInUser = pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity();
            searchConfiguration.setLdapProfile(loggedInUser.getLdapProfileID());
            searchConfiguration.setChaiProvider(getChaiUser(pwmRequest, helpdeskProfile, loggedInUser).getChaiProvider());
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
        final HelpdeskSearchResultsBean outputData = new HelpdeskSearchResultsBean();
        outputData.setSearchResults(results.resultsAsJsonOutput(pwmRequest.getPwmApplication()));
        outputData.setSizeExceeded(sizeExceeded);
        restResultBean.setData(outputData);
        pwmRequest.outputJsonResult(restResultBean);
    }



    private static HelpdeskDetailInfoBean makeHelpdeskDetailInfo(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final Date startTime = new Date();
        LOGGER.trace(pwmRequest, "beginning to assemble detail data report for user " + userIdentity);
        final Locale actorLocale = pwmRequest.getLocale();
        final ChaiUser theUser = getChaiUser(pwmRequest, helpdeskProfile, userIdentity);

        if (!theUser.isValid()) {
            return null;
        }

        final HelpdeskDetailInfoBean detailInfo = new HelpdeskDetailInfoBean();
        final UserInfoBean uiBean = detailInfo.getUserInfoBean();
        final UserStatusReader userStatusReader = new UserStatusReader(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel());
        userStatusReader.populateUserInfoBean(uiBean, actorLocale, userIdentity, theUser.getChaiProvider());

        try {
            detailInfo.setIntruderLocked(theUser.isPasswordLocked());
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "unexpected error reading intruder lock status for user '" + userIdentity + "', " + e.getMessage());
        }

        try {
            detailInfo.setAccountEnabled(theUser.isAccountEnabled());
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "unexpected error reading account enabled status for user '" + userIdentity + "', " + e.getMessage());
        }

        try {
            detailInfo.setAccountExpired(theUser.isAccountExpired());
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "unexpected error reading account expired status for user '" + userIdentity + "', " + e.getMessage());
        }

        try {
            detailInfo.setLastLoginTime(theUser.readLastLoginTime());
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "unexpected error reading last login time for user '" + userIdentity + "', " + e.getMessage());
        }

        try {
            detailInfo.setUserHistory(pwmRequest.getPwmApplication().getAuditManager().readUserHistory(uiBean));
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "unexpected error reading userHistory for user '" + userIdentity + "', " + e.getMessage());
        }

        if (uiBean.getPasswordLastModifiedTime() != null) {
            final TimeDuration passwordSetDelta = TimeDuration.fromCurrent(uiBean.getPasswordLastModifiedTime());
            detailInfo.setPasswordSetDelta(passwordSetDelta.asLongString(pwmRequest.getLocale()));
        } else {
            detailInfo.setPasswordSetDelta(LocaleHelper.getLocalizedMessage(Display.Value_NotApplicable, pwmRequest));
        }

        final UserDataReader userDataReader = helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY)
                ? LdapUserDataReader.appProxiedReader(pwmRequest.getPwmApplication(), userIdentity)
                : LdapUserDataReader.selfProxiedReader(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), userIdentity);

        {
            final List<FormConfiguration> detailFormConfig = helpdeskProfile.readSettingAsForm(PwmSetting.HELPDESK_DETAIL_FORM);
            final Map<FormConfiguration,String> formData = new LinkedHashMap<>();
            for (final FormConfiguration formConfiguration : detailFormConfig) {
                formData.put(formConfiguration,"");
            }
            FormUtility.populateFormMapFromLdap(detailFormConfig, pwmRequest.getPwmSession().getLabel(), formData, userDataReader);
            detailInfo.setSearchDetails(formData);
        }

        final String configuredDisplayName = helpdeskProfile.readSettingAsString(PwmSetting.HELPDESK_DETAIL_DISPLAY_NAME);
        if (configuredDisplayName != null && !configuredDisplayName.isEmpty()) {
            final MacroMachine macroMachine = new MacroMachine(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel(), detailInfo.getUserInfoBean(), null, userDataReader);
            final String displayName = macroMachine.expandMacros(configuredDisplayName);
            detailInfo.setUserDisplayName(displayName);
        }

        final TimeDuration timeDuration = TimeDuration.fromCurrent(startTime);
        if (pwmRequest.getConfig().isDevDebugMode()) {
            LOGGER.trace(pwmRequest, "completed assembly of detail data report for user " + userIdentity
                    + " in " + timeDuration.asCompactString() + ", contents: " + JsonUtil.serialize(detailInfo));
        }
        return detailInfo;
    }

    private void restUnlockPassword(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final String userKey = pwmRequest.readParameterAsString("userKey", PwmHttpRequestWrapper.Flag.BypassValidation);
        if (userKey.length() < 1) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"userKey parameter is missing");
            pwmRequest.respondWithError(errorInformation, false);
            return;
        }
        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmRequest.getPwmApplication());

        if (!helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE_UNLOCK)) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED, "password unlock request, but helpdesk unlock is not enabled");
            LOGGER.error(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        //clear pwm intruder setting.
        {
            final IntruderManager intruderManager = pwmRequest.getPwmApplication().getIntruderManager();
            intruderManager.convenience().clearUserIdentity(userIdentity);
        }

        final boolean useProxy = helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY);
        try {
            final ChaiUser chaiUser = useProxy ?
                    pwmRequest.getPwmApplication().getProxiedChaiUser(userIdentity) :
                    pwmRequest.getPwmSession().getSessionManager().getActor(pwmRequest.getPwmApplication(), userIdentity);
            chaiUser.unlockPassword();
            {
                // mark the event log
                final HelpdeskAuditRecord auditRecord = pwmRequest.getPwmApplication().getAuditManager().createHelpdeskAuditRecord(
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
            pwmRequest.respondWithError(new ErrorInformation(pwmError == null ? PwmError.PASSWORD_UNKNOWN_VALIDATION : pwmError));
            LOGGER.trace(pwmRequest, "ChaiPasswordPolicyException was thrown while resetting password: " + e.toString());
            return;
        } catch (ChaiOperationException e) {
            final PwmError returnMsg = PwmError.forChaiError(e.getErrorCode()) == null ? PwmError.ERROR_UNKNOWN : PwmError.forChaiError(e.getErrorCode());
            final ErrorInformation error = new ErrorInformation(returnMsg, e.getMessage());
            pwmRequest.respondWithError(error);
            LOGGER.warn(pwmRequest, "error resetting password for user '" + userIdentity.toDisplayString() + "'' " + error.toDebugStr() + ", " + e.getMessage());
            return;
        }

        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setSuccessMessage(Message.getLocalizedMessage(pwmRequest.getLocale(),Message.Success_Unknown,pwmRequest.getConfig()));
        pwmRequest.outputJsonResult(restResultBean);
    }

    private void restValidateOtpCodeRequest(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile
    )
            throws IOException, PwmUnrecoverableException, ServletException, ChaiUnavailableException
    {
        final long DELAY_MS = 1000;
        final Date startTime = new Date();

        final Map<String,String> inputRecord = pwmRequest.readBodyAsJsonStringMap();
        final String userKey = inputRecord.get("userKey");
        if (userKey == null || userKey.isEmpty()) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"userKey parameter is missing");
            pwmRequest.respondWithError(errorInformation, false);
            return;
        }
        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmRequest.getPwmApplication());

        if (!helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE_OTP_VERIFY)) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED, "password unlock request, but helpdesk otp verify is not enabled");
            LOGGER.error(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        final String code = inputRecord.get("code");
        final OTPUserRecord otpUserRecord = pwmRequest.getPwmApplication().getOtpService().readOTPUserConfiguration(pwmRequest.getSessionLabel(), userIdentity);
        try {
            final boolean passed = pwmRequest.getPwmApplication().getOtpService().validateToken(
                    pwmRequest.getPwmSession(),
                    userIdentity,
                    otpUserRecord,
                    code,
                    false
            );
            if (passed) {
                // mark the event log
                {
                    final PwmSession pwmSession = pwmRequest.getPwmSession();
                    final HelpdeskAuditRecord auditRecord = pwmRequest.getPwmApplication().getAuditManager().createHelpdeskAuditRecord(
                            AuditEvent.HELPDESK_VERIFY_OTP,
                            pwmSession.getUserInfoBean().getUserIdentity(),
                            null,
                            userIdentity,
                            pwmSession.getSessionStateBean().getSrcAddress(),
                            pwmSession.getSessionStateBean().getSrcHostname()
                    );
                    pwmRequest.getPwmApplication().getAuditManager().submit(auditRecord);
                }

                StatisticsManager.incrementStat(pwmRequest, Statistic.HELPDESK_VERIFY_OTP);
            }

            // add a delay to prevent continuous checks
            while (TimeDuration.fromCurrent(startTime).isShorterThan(DELAY_MS)) {
                Helper.pause(100);
            }

            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(passed);
            pwmRequest.outputJsonResult(restResultBean);
        } catch (PwmOperationalException e) {
            pwmRequest.outputJsonResult(RestResultBean.fromError(e.getErrorInformation(), pwmRequest));
        }
    }

    private void restSendVerificationTokenRequest(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile
    )
            throws IOException, PwmUnrecoverableException, ServletException, ChaiUnavailableException
    {
        final Configuration config = pwmRequest.getConfig();
        final Map<String,String> bodyParams = pwmRequest.readBodyAsJsonStringMap();
        MessageSendMethod tokenSendMethod = helpdeskProfile.readSettingAsEnum(PwmSetting.HELPDESK_TOKEN_SEND_METHOD, MessageSendMethod.class);
        if (tokenSendMethod == MessageSendMethod.CHOICE_SMS_EMAIL) {
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

        final String userKey = bodyParams.get("userKey");
        if (userKey == null || userKey.length() < 1) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"userKey parameter is missing");
            pwmRequest.respondWithError(errorInformation, false);
            return;
        }
        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmRequest.getPwmApplication());


        final HelpdeskDetailInfoBean helpdeskDetailInfoBean = makeHelpdeskDetailInfo(pwmRequest, helpdeskProfile, userIdentity);
        final UserInfoBean userInfoBean = helpdeskDetailInfoBean.getUserInfoBean();
        final UserDataReader userDataReader = LdapUserDataReader.appProxiedReader(pwmRequest.getPwmApplication(), userIdentity);
        final MacroMachine macroMachine = new MacroMachine(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel(), userInfoBean, null, userDataReader);
        final String configuredTokenString = config.readAppProperty(AppProperty.HELPDESK_TOKEN_VALUE);
        final String tokenKey = macroMachine.expandMacros(configuredTokenString);
        final EmailItemBean emailItemBean = config.readSettingAsEmail(PwmSetting.EMAIL_HELPDESK_TOKEN, pwmRequest.getLocale());

        final String destEmailAddress = macroMachine.expandMacros(emailItemBean.getTo());
        final StringBuilder destDisplayString = new StringBuilder();
        if (destEmailAddress != null && !destEmailAddress.isEmpty()) {
            if (tokenSendMethod == MessageSendMethod.BOTH || tokenSendMethod == MessageSendMethod.EMAILFIRST || tokenSendMethod == MessageSendMethod.EMAILONLY) {
                destDisplayString.append(destEmailAddress);
            }
        }
        if (userInfoBean.getUserSmsNumber() != null && !userInfoBean.getUserSmsNumber().isEmpty()) {
            if (tokenSendMethod == MessageSendMethod.BOTH || tokenSendMethod == MessageSendMethod.SMSFIRST || tokenSendMethod == MessageSendMethod.SMSONLY) {
                if (destDisplayString.length() > 0) {
                    destDisplayString.append(", ");
                }
                destDisplayString.append(userInfoBean.getUserSmsNumber());
            }
        }

        LOGGER.debug(pwmRequest, "generated token code for " + userIdentity.toDelimitedKey());

        final String smsMessage = config.readSettingAsLocalizedString(PwmSetting.SMS_HELPDESK_TOKEN_TEXT, pwmRequest.getLocale());

        try {
            TokenService.TokenSender.sendToken(
                    pwmRequest.getPwmApplication(),
                    userInfoBean,
                    macroMachine,
                    emailItemBean,
                    tokenSendMethod,
                    destEmailAddress,
                    userInfoBean.getUserSmsNumber(),
                    smsMessage,
                    tokenKey
            );
        } catch (PwmException e) {
            LOGGER.error(pwmRequest, e.getErrorInformation());
            pwmRequest.outputJsonResult(RestResultBean.fromError(e.getErrorInformation(),pwmRequest));
            return;
        }

        StatisticsManager.incrementStat(pwmRequest,Statistic.HELPDESK_TOKENS_SENT);
        final HashMap<String,String> output = new HashMap<>();
        output.put("destination", destDisplayString.toString());
        output.put("token", tokenKey);
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(output);
        pwmRequest.outputJsonResult(restResultBean);
    }

    private void restClearOtpSecret(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final String userKey = pwmRequest.readBodyAsJsonStringMap(PwmHttpRequestWrapper.Flag.BypassValidation).get("userKey");
        if (userKey == null || userKey.length() < 1) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"userKey parameter is missing");
            pwmRequest.respondWithError(errorInformation, false);
            return;
        }
        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmRequest.getPwmApplication());

        if (!helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_CLEAR_OTP_BUTTON)) {
            final String errorMsg = "clear otp request, but helpdesk clear otp button is not enabled";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg);
            LOGGER.error(pwmRequest, errorMsg);
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        //clear pwm intruder setting.
        pwmRequest.getPwmApplication().getIntruderManager().convenience().clearUserIdentity(userIdentity);

        try {

            OtpService service = pwmRequest.getPwmApplication().getOtpService();
            service.clearOTPUserConfiguration(pwmRequest.getPwmSession(), userIdentity);
            {
                // mark the event log
                final HelpdeskAuditRecord auditRecord = pwmRequest.getPwmApplication().getAuditManager().createHelpdeskAuditRecord(
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
            pwmRequest.respondWithError(error);
            LOGGER.warn(pwmRequest, "error clearing OTP secret for user '" + userIdentity + "'' " + error.toDebugStr() + ", " + e.getMessage());
            return;
        }

        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setSuccessMessage(Message.getLocalizedMessage(pwmRequest.getLocale(),Message.Success_Unknown,pwmRequest.getConfig()));
        pwmRequest.outputJsonResult(restResultBean);
    }

    private static String getSearchFilter(final Configuration configuration, final HelpdeskProfile helpdeskProfile) {
        final String configuredFilter = helpdeskProfile.readSettingAsString(PwmSetting.HELPDESK_SEARCH_FILTER);
        if (configuredFilter != null && !configuredFilter.isEmpty()) {
            return configuredFilter;
        }

        final List<String> defaultObjectClasses = configuration.readSettingAsStringArray(PwmSetting.DEFAULT_OBJECT_CLASSES);
        final List<FormConfiguration> searchAttributes = helpdeskProfile.readSettingAsForm(PwmSetting.HELPDESK_SEARCH_FORM);
        final StringBuilder filter = new StringBuilder();
        filter.append("(&"); //open AND clause for objectclasses and attributes
        for (final String objectClass : defaultObjectClasses) {
            filter.append("(objectClass=").append(objectClass).append(")");
        }
        filter.append("(|"); // open OR clause for attributes
        for (final FormConfiguration formConfiguration : searchAttributes) {
            if (formConfiguration != null && formConfiguration.getName() != null) {
                final String searchAttribute = formConfiguration.getName();
                filter.append("(").append(searchAttribute).append("=*").append(PwmConstants.VALUE_REPLACEMENT_USERNAME).append("*)");
            }
        }
        filter.append(")"); // close OR clause
        filter.append(")"); // close AND clause
        return filter.toString();
    }


    private static ChaiUser getChaiUser(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final boolean useProxy = helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY);
        return useProxy ?
                pwmRequest.getPwmApplication().getProxiedChaiUser(userIdentity) :
                pwmRequest.getPwmSession().getSessionManager().getActor(pwmRequest.getPwmApplication(), userIdentity);
    }

    private static void checkIfUserIdentityViewable(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException {
        final String filterSetting = getSearchFilter(pwmRequest.getConfig(), helpdeskProfile);
        String filterString = filterSetting.replace(PwmConstants.VALUE_REPLACEMENT_USERNAME, "*");
        while (filterString.contains("**")) {
            filterString = filterString.replace("**", "*");
        }

        final boolean match = LdapPermissionTester.testQueryMatch(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel(), userIdentity, filterString);
        if (!match) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, "requested userDN is not available within configured search filter"));
        }
    }

}
