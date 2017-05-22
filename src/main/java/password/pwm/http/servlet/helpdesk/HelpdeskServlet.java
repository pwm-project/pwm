/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiPasswordPolicyException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.UserIdentity;
import password.pwm.ldap.UserInfo;
import password.pwm.config.ActionConfiguration;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.HelpdeskClearResponseMode;
import password.pwm.config.option.HelpdeskUIMode;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmSession;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.i18n.Message;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.LdapPermissionTester;
import password.pwm.ldap.LdapUserDataReader;
import password.pwm.ldap.UserDataReader;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.ldap.search.UserSearchResults;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.event.HelpdeskAuditRecord;
import password.pwm.svc.intruder.IntruderManager;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.svc.token.TokenService;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.operations.OtpService;
import password.pwm.util.operations.otp.OTPUserRecord;
import password.pwm.util.secure.SecureService;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
public class HelpdeskServlet extends ControlledPwmServlet {

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
        verifyVerificationToken(HttpMethod.POST),
        clientData(HttpMethod.GET),
        checkVerification(HttpMethod.POST),
        showVerifications(HttpMethod.POST),
        validateAttributes(HttpMethod.POST),
        ;

        private final HttpMethod method;

        HelpdeskAction(final HttpMethod method)
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods()
        {
            return Collections.singletonList(method);
        }
    }

    public Class<? extends ProcessAction> getProcessActionsClass() {
        return HelpdeskAction.class;
    }


    private HelpdeskProfile getHelpdeskRProfile(final PwmRequest pwmRequest) throws PwmUnrecoverableException
    {
        return pwmRequest.getPwmSession().getSessionManager().getHelpdeskProfile(pwmRequest.getPwmApplication());
    }

    @Override
    protected void nextStep(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        final HelpdeskProfile helpdeskProfile = getHelpdeskRProfile(pwmRequest);
        pwmRequest.setAttribute(PwmRequestAttribute.HelpdeskVerificationEnabled, !helpdeskProfile.readRequiredVerificationMethods().isEmpty());
        pwmRequest.forwardToJsp(JspUrl.HELPDESK_SEARCH);
    }

    @Override
    public ProcessStatus preProcessCheck(final PwmRequest pwmRequest) throws PwmUnrecoverableException, IOException, ServletException {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        if (!pwmRequest.isAuthenticated()) {
            pwmRequest.respondWithError(PwmError.ERROR_AUTHENTICATION_REQUIRED.toInfo());
            return ProcessStatus.Halt;
        }

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE)) {
            pwmRequest.respondWithError(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, "Setting " + PwmSetting.HELPDESK_ENABLE.toMenuLocationDebug(null,null) + " is not enabled."));
            return ProcessStatus.Halt;
        }

        final HelpdeskProfile helpdeskProfile = pwmRequest.getPwmSession().getSessionManager().getHelpdeskProfile(pwmApplication);
        if (helpdeskProfile == null) {
            pwmRequest.respondWithError(PwmError.ERROR_UNAUTHORIZED.toInfo());
            return ProcessStatus.Halt;
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler(action = "clientData")
    private ProcessStatus restClientData(final PwmRequest pwmRequest)
            throws IOException, PwmUnrecoverableException {
        final HelpdeskProfile helpdeskProfile = getHelpdeskRProfile(pwmRequest);
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
            final Map<String,HelpdeskClientDataBean.ActionInformation> actions = new LinkedHashMap<>();
            for (final ActionConfiguration actionConfiguration : actionConfigurations) {
                final HelpdeskClientDataBean.ActionInformation actionInformation = new HelpdeskClientDataBean.ActionInformation();
                actionInformation.setName(actionConfiguration.getName());
                actionInformation.setDescription(actionConfiguration.getDescription());
                actions.put(actionConfiguration.getName(), actionInformation);
            }

            returnValues.setActions(actions);
        }
        {
            final Map<String,Collection<IdentityVerificationMethod>> verificationMethodsMap = new HashMap<>();
            verificationMethodsMap.put("optional", helpdeskProfile.readOptionalVerificationMethods());
            verificationMethodsMap.put("required", helpdeskProfile.readRequiredVerificationMethods());
            returnValues.setVerificationMethods(verificationMethodsMap);
        }
        {
            final List<FormConfiguration> attributeVerificationForm = helpdeskProfile.readSettingAsForm(PwmSetting.HELPDESK_VERIFICATION_FORM);
            final List<HelpdeskClientDataBean.FormInformation> formInformations = new ArrayList<>();
            if (attributeVerificationForm != null) {
                for (final FormConfiguration formConfiguration : attributeVerificationForm) {
                    final HelpdeskClientDataBean.FormInformation formInformation = new HelpdeskClientDataBean.FormInformation();
                    formInformation.setName(formConfiguration.getName());
                    final String label = formConfiguration.getLabel(pwmRequest.getLocale());
                    formInformation.setLabel((label != null && !label.isEmpty()) ? label : formConfiguration.getName());
                    formInformations.add(formInformation);
                }
            }
            returnValues.setVerificationForm(formInformations);
        }

        final RestResultBean restResultBean = new RestResultBean(returnValues);
        LOGGER.trace(pwmRequest, "returning clientData: " + JsonUtil.serialize(restResultBean));
        pwmRequest.outputJsonResult(restResultBean);
        return ProcessStatus.Halt;
    }

    @ActionHandler(action = "executeAction")
    private ProcessStatus processExecuteActionRequest(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final HelpdeskProfile helpdeskProfile = getHelpdeskRProfile(pwmRequest);
        final String userKey = pwmRequest.readBodyAsJsonStringMap(PwmHttpRequestWrapper.Flag.BypassValidation).get("userKey");
        if (userKey == null || userKey.length() < 1) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"userKey parameter is missing");
            setLastError(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation, false);
            return ProcessStatus.Halt;
        }
        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmRequest.getPwmApplication());
        LOGGER.debug(pwmRequest, "received executeAction request for user " + userIdentity.toString());

        final List<ActionConfiguration> actionConfigurations = helpdeskProfile.readSettingAsAction(PwmSetting.HELPDESK_ACTIONS);
        final String requestedName = pwmRequest.readParameterAsString("name");
        ActionConfiguration action = null;
        for (final ActionConfiguration loopAction : actionConfigurations) {
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
            return ProcessStatus.Halt;
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
                final HelpdeskAuditRecord auditRecord = new AuditRecordFactory(pwmRequest).createHelpdeskAuditRecord(
                        AuditEvent.HELPDESK_ACTION,
                        pwmSession.getUserInfo().getUserIdentity(),
                        action.getName(),
                        userIdentity,
                        pwmSession.getSessionStateBean().getSrcAddress(),
                        pwmSession.getSessionStateBean().getSrcHostname()
                );
                pwmRequest.getPwmApplication().getAuditManager().submit(auditRecord);
            }
            final RestResultBean restResultBean = RestResultBean.forSuccessMessage(pwmRequest.getLocale(), pwmRequest.getConfig(), Message.Success_Action, action.getName());

            pwmRequest.outputJsonResult(restResultBean);
            return ProcessStatus.Halt;
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmRequest, e.getErrorInformation().toDebugStr());
            final RestResultBean restResultBean = RestResultBean.fromError(e.getErrorInformation(), pwmRequest);
            pwmRequest.outputJsonResult(restResultBean);
            return ProcessStatus.Halt;
        }
    }

    @ActionHandler(action = "deleteUser")
    private ProcessStatus restDeleteUserRequest(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final HelpdeskProfile helpdeskProfile = getHelpdeskRProfile(pwmRequest);
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        final String userKey = pwmRequest.readParameterAsString("userKey", PwmHttpRequestWrapper.Flag.BypassValidation);
        if (userKey.length() < 1) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"userKey parameter is missing");
            setLastError(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation, false);
            return ProcessStatus.Halt;
        }

        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmApplication);
        LOGGER.info(pwmSession, "received deleteUser request by " + pwmSession.getUserInfo().getUserIdentity().toString() + " for user " + userIdentity.toString());

        // check if user should be seen by actor
        checkIfUserIdentityViewable(pwmRequest, helpdeskProfile, userIdentity);

        // read the userID for later logging.
        String userID = null;
        try {
            userID = LdapOperationsHelper.readLdapUsernameValue(pwmApplication, userIdentity);
        } catch (ChaiOperationException e) {
            LOGGER.warn(pwmSession, "unable to read username of deleted user while creating audit record");
        }

        // execute user delete operation
        final ChaiProvider provider = helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY)
                ? pwmApplication.getProxyChaiProvider(userIdentity.getLdapProfileID())
                : pwmSession.getSessionManager().getChaiProvider();


        try {
            provider.deleteEntry(userIdentity.getUserDN());
        } catch (ChaiOperationException e) {
            final String errorMsg = "error while attempting to delete user " + userIdentity.toString() + ", error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            LOGGER.debug(pwmRequest, errorMsg);
            pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
            return ProcessStatus.Halt;
        }

        // mark the event log
        {
            //normally the audit record builder reads the userID while constructing the record, but because the target user is already deleted,
            //it will be included here explicitly.
            final AuditRecordFactory.AuditUserDefinition auditUserDefinition = new AuditRecordFactory.AuditUserDefinition(
                    userID,
                    userIdentity.getUserDN(),
                    userIdentity.getLdapProfileID()
            );
            final HelpdeskAuditRecord auditRecord = new AuditRecordFactory(pwmRequest).createHelpdeskAuditRecord(
                    AuditEvent.HELPDESK_DELETE_USER,
                    pwmSession.getUserInfo().getUserIdentity(),
                    null,
                    auditUserDefinition,
                    pwmSession.getSessionStateBean().getSrcAddress(),
                    pwmSession.getSessionStateBean().getSrcHostname()
            );
            pwmApplication.getAuditManager().submit(auditRecord);
        }

        LOGGER.info(pwmSession, "user " + userIdentity + " has been deleted");
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setSuccessMessage(Message.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(),Message.Success_Unknown,pwmApplication.getConfig()));
        pwmRequest.outputJsonResult(restResultBean);
        return ProcessStatus.Halt;
    }

    @ActionHandler(action = "detail")
    private ProcessStatus processDetailRequest(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final HelpdeskProfile helpdeskProfile = getHelpdeskRProfile(pwmRequest);
        final String userKey = pwmRequest.readParameterAsString("userKey", PwmHttpRequestWrapper.Flag.BypassValidation);
        if (userKey.length() < 1) {
            pwmRequest.respondWithError(
                    new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER, "userKey parameter is missing"));
            return ProcessStatus.Halt;
        }

        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmRequest.getPwmApplication()).canonicalized(pwmRequest.getPwmApplication());
        processDetailRequest(pwmRequest, helpdeskProfile, userIdentity);
        final HelpdeskAuditRecord auditRecord = new AuditRecordFactory(pwmRequest).createHelpdeskAuditRecord(
                AuditEvent.HELPDESK_VIEW_DETAIL,
                pwmRequest.getPwmSession().getUserInfo().getUserIdentity(),
                null,
                userIdentity,
                pwmRequest.getSessionLabel().getSrcAddress(),
                pwmRequest.getSessionLabel().getSrcHostname()
        );
        pwmRequest.getPwmApplication().getAuditManager().submit(auditRecord);
        return ProcessStatus.Halt;
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

        final HelpdeskVerificationStateBean verificationStateBean = HelpdeskVerificationStateBean.fromClientString(
                pwmRequest,
                pwmRequest.readParameterAsString(HelpdeskVerificationStateBean.PARAMETER_VERIFICATION_STATE_KEY, PwmHttpRequestWrapper.Flag.BypassValidation)
        );

        if (!checkIfRequiredVerificationPassed(userIdentity, verificationStateBean, helpdeskProfile)) {
            final String errorMsg = "selected user has not been verified";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,errorMsg);
            LOGGER.debug(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation, false);
            return;
        }

        final HelpdeskDetailInfoBean helpdeskDetailInfoBean = HelpdeskDetailInfoBean.makeHelpdeskDetailInfo(pwmRequest, helpdeskProfile, userIdentity);
        pwmRequest.setAttribute(PwmRequestAttribute.HelpdeskDetail, helpdeskDetailInfoBean);

        if (helpdeskDetailInfoBean != null && helpdeskDetailInfoBean.getUserInfo() != null) {
            final String obfuscatedDN = helpdeskDetailInfoBean.getUserInfo().getUserIdentity().toObfuscatedKey(pwmRequest.getPwmApplication());
            pwmRequest.setAttribute(PwmRequestAttribute.HelpdeskObfuscatedDN, obfuscatedDN);
            pwmRequest.setAttribute(PwmRequestAttribute.HelpdeskUsername, helpdeskDetailInfoBean.getUserInfo().getUsername());
        }

        StatisticsManager.incrementStat(pwmRequest, Statistic.HELPDESK_USER_LOOKUP);
        pwmRequest.setAttribute(PwmRequestAttribute.HelpdeskVerificationEnabled, !helpdeskProfile.readOptionalVerificationMethods().isEmpty());
        pwmRequest.forwardToJsp(JspUrl.HELPDESK_DETAIL);
    }

    @ActionHandler(action = "search")
    private ProcessStatus restSearchRequest(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final HelpdeskProfile helpdeskProfile = getHelpdeskRProfile(pwmRequest);
        final Map<String, String> valueMap = pwmRequest.readBodyAsJsonStringMap();
        final String username = valueMap.get("username");

        final boolean useProxy = helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY);
        final List<FormConfiguration> searchForm = helpdeskProfile.readSettingAsForm(PwmSetting.HELPDESK_SEARCH_FORM);
        final int maxResults = (int)helpdeskProfile.readSettingAsLong(PwmSetting.HELPDESK_RESULT_LIMIT);

        if (username == null ||username.isEmpty()) {
            final HelpdeskSearchResultsBean emptyResults = new HelpdeskSearchResultsBean();
            emptyResults.setSearchResults(new ArrayList<>());
            emptyResults.setSizeExceeded(false);
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(emptyResults);
            pwmRequest.outputJsonResult(restResultBean);
            return ProcessStatus.Halt;
        }

        final UserSearchEngine userSearchEngine = pwmRequest.getPwmApplication().getUserSearchEngine();


        final SearchConfiguration searchConfiguration;
        {
            final SearchConfiguration.SearchConfigurationBuilder builder = SearchConfiguration.builder();
            builder.contexts(helpdeskProfile.readSettingAsStringArray(PwmSetting.HELPDESK_SEARCH_BASE));
            builder.enableContextValidation(false);
            builder.username(username);
            builder.enableValueEscaping(false);
            builder.filter(getSearchFilter(pwmRequest.getConfig(), helpdeskProfile));
            builder.enableSplitWhitespace(true);

            if (!useProxy) {
                final UserIdentity loggedInUser = pwmRequest.getPwmSession().getUserInfo().getUserIdentity();
                builder.ldapProfile(loggedInUser.getLdapProfileID());
                builder.chaiProvider(getChaiUser(pwmRequest, helpdeskProfile, loggedInUser).getChaiProvider());
            }

            searchConfiguration = builder.build();
        }



        final UserSearchResults results;
        final boolean sizeExceeded;
        try {
            final Locale locale = pwmRequest.getLocale();
            results = userSearchEngine.performMultiUserSearchFromForm(locale, searchConfiguration, maxResults, searchForm, pwmRequest.getSessionLabel());
            sizeExceeded = results.isSizeExceeded();
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInformation = e.getErrorInformation();
            LOGGER.error(pwmRequest, errorInformation);
            final RestResultBean restResultBean = RestResultBean.fromError(errorInformation, pwmRequest);
            restResultBean.setData(new ArrayList<Map<String, String>>());
            pwmRequest.outputJsonResult(restResultBean);
            return ProcessStatus.Halt;
        }

        final RestResultBean restResultBean = new RestResultBean();
        final HelpdeskSearchResultsBean outputData = new HelpdeskSearchResultsBean();
        outputData.setSearchResults(results.resultsAsJsonOutput(pwmRequest.getPwmApplication(),pwmRequest.getUserInfoIfLoggedIn()));
        outputData.setSizeExceeded(sizeExceeded);
        restResultBean.setData(outputData);
        pwmRequest.outputJsonResult(restResultBean);
        return ProcessStatus.Halt;
    }

    @ActionHandler(action = "unlockIntruder")
    private ProcessStatus restUnlockIntruder(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final HelpdeskProfile helpdeskProfile = getHelpdeskRProfile(pwmRequest);
        final String userKey = pwmRequest.readParameterAsString("userKey", PwmHttpRequestWrapper.Flag.BypassValidation);
        if (userKey.length() < 1) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"userKey parameter is missing");
            pwmRequest.respondWithError(errorInformation, false);
            return ProcessStatus.Halt;
        }
        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmRequest.getPwmApplication());

        if (!helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE_UNLOCK)) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED, "password unlock request, but helpdesk unlock is not enabled");
            LOGGER.error(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation);
            return ProcessStatus.Halt;
        }

        //clear pwm intruder setting.
        {
            final IntruderManager intruderManager = pwmRequest.getPwmApplication().getIntruderManager();
            intruderManager.convenience().clearUserIdentity(userIdentity);
        }

        // send notice email
        sendUnlockNoticeEmail(pwmRequest, helpdeskProfile, userIdentity);

        final boolean useProxy = helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY);
        try {
            final ChaiUser chaiUser = useProxy ?
                    pwmRequest.getPwmApplication().getProxiedChaiUser(userIdentity) :
                    pwmRequest.getPwmSession().getSessionManager().getActor(pwmRequest.getPwmApplication(), userIdentity);
            chaiUser.unlockPassword();
            {
                // mark the event log
                final HelpdeskAuditRecord auditRecord = new AuditRecordFactory(pwmRequest).createHelpdeskAuditRecord(
                        AuditEvent.HELPDESK_UNLOCK_PASSWORD,
                        pwmRequest.getPwmSession().getUserInfo().getUserIdentity(),
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
            return ProcessStatus.Halt;
        } catch (ChaiOperationException e) {
            final PwmError returnMsg = PwmError.forChaiError(e.getErrorCode()) == null ? PwmError.ERROR_UNKNOWN : PwmError.forChaiError(e.getErrorCode());
            final ErrorInformation error = new ErrorInformation(returnMsg, e.getMessage());
            pwmRequest.respondWithError(error);
            LOGGER.warn(pwmRequest, "error resetting password for user '" + userIdentity.toDisplayString() + "'' " + error.toDebugStr() + ", " + e.getMessage());
            return ProcessStatus.Halt;
        }

        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setSuccessMessage(Message.getLocalizedMessage(pwmRequest.getLocale(),Message.Success_Unknown,pwmRequest.getConfig()));
        pwmRequest.outputJsonResult(restResultBean);
        return ProcessStatus.Halt;
    }

    @ActionHandler(action = "validateOtpCode")
    private ProcessStatus restValidateOtpCodeRequest(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException, ServletException, ChaiUnavailableException
    {
        final HelpdeskProfile helpdeskProfile = getHelpdeskRProfile(pwmRequest);

        final Instant startTime = Instant.now();

        final HelpdeskVerificationRequestBean helpdeskVerificationRequestBean = JsonUtil.deserialize(
                pwmRequest.readRequestBodyAsString(),
                HelpdeskVerificationRequestBean.class
        );
        final String userKey = helpdeskVerificationRequestBean.getUserKey();
        if (userKey == null || userKey.isEmpty()) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"userKey parameter is missing");
            pwmRequest.respondWithError(errorInformation, false);
            return ProcessStatus.Halt;
        }
        final UserIdentity userIdentity = UserIdentity.fromKey(userKey, pwmRequest.getPwmApplication());

        if (!helpdeskProfile.readOptionalVerificationMethods().contains(IdentityVerificationMethod.OTP)) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED, "password otp verification request, but otp verify is not enabled");
            LOGGER.error(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation);
            return ProcessStatus.Halt;
        }

        final String code = helpdeskVerificationRequestBean.getCode();
        final OTPUserRecord otpUserRecord = pwmRequest.getPwmApplication().getOtpService().readOTPUserConfiguration(pwmRequest.getSessionLabel(), userIdentity);
        try {
            final boolean passed = pwmRequest.getPwmApplication().getOtpService().validateToken(
                    pwmRequest.getPwmSession(),
                    userIdentity,
                    otpUserRecord,
                    code,
                    false
            );

            final HelpdeskVerificationStateBean verificationStateBean = HelpdeskVerificationStateBean.fromClientString(pwmRequest, helpdeskVerificationRequestBean.getVerificationState());

            if (passed) {
                final PwmSession pwmSession = pwmRequest.getPwmSession();
                final HelpdeskAuditRecord auditRecord = new AuditRecordFactory(pwmRequest).createHelpdeskAuditRecord(
                        AuditEvent.HELPDESK_VERIFY_OTP,
                        pwmSession.getUserInfo().getUserIdentity(),
                        null,
                        userIdentity,
                        pwmSession.getSessionStateBean().getSrcAddress(),
                        pwmSession.getSessionStateBean().getSrcHostname()
                );
                pwmRequest.getPwmApplication().getAuditManager().submit(auditRecord);

                StatisticsManager.incrementStat(pwmRequest, Statistic.HELPDESK_VERIFY_OTP);
                verificationStateBean.addRecord(userIdentity, IdentityVerificationMethod.OTP);
            } else {
                final PwmSession pwmSession = pwmRequest.getPwmSession();
                final HelpdeskAuditRecord auditRecord = new AuditRecordFactory(pwmRequest).createHelpdeskAuditRecord(
                        AuditEvent.HELPDESK_VERIFY_OTP_INCORRECT,
                        pwmSession.getUserInfo().getUserIdentity(),
                        null,
                        userIdentity,
                        pwmSession.getSessionStateBean().getSrcAddress(),
                        pwmSession.getSessionStateBean().getSrcHostname()
                );
                pwmRequest.getPwmApplication().getAuditManager().submit(auditRecord);
            }

            // add a delay to prevent continuous checks
            final long delayMs = Long.parseLong(pwmRequest.getConfig().readAppProperty(AppProperty.HELPDESK_VERIFICATION_INVALID_DELAY_MS));
            while (TimeDuration.fromCurrent(startTime).isShorterThan(delayMs)) {
                JavaHelper.pause(100);
            }

            final HelpdeskVerificationResponseBean responseBean = new HelpdeskVerificationResponseBean(passed, verificationStateBean.toClientString(pwmRequest.getPwmApplication()));
            final RestResultBean restResultBean = new RestResultBean(responseBean);
            pwmRequest.outputJsonResult(restResultBean);
        } catch (PwmOperationalException e) {
            pwmRequest.outputJsonResult(RestResultBean.fromError(e.getErrorInformation(), pwmRequest));
        }
        return ProcessStatus.Halt;
    }

    @ActionHandler(action = "sendVerificationToken")
    private ProcessStatus restSendVerificationTokenRequest(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException, ServletException, ChaiUnavailableException
    {
        final HelpdeskProfile helpdeskProfile = getHelpdeskRProfile(pwmRequest);

        final Instant startTime = Instant.now();
        final Configuration config = pwmRequest.getConfig();
        final Map<String,String> bodyParams = pwmRequest.readBodyAsJsonStringMap();
        MessageSendMethod tokenSendMethod = helpdeskProfile.readSettingAsEnum(PwmSetting.HELPDESK_TOKEN_SEND_METHOD, MessageSendMethod.class);
        if (tokenSendMethod == MessageSendMethod.CHOICE_SMS_EMAIL) {
            final String METHOD_PARAM_NAME = "method";
            if (bodyParams != null && bodyParams.containsKey(METHOD_PARAM_NAME)) {
                final String methodParam = bodyParams.getOrDefault(METHOD_PARAM_NAME,"");
                switch (methodParam) {
                    case "sms":
                        tokenSendMethod = MessageSendMethod.SMSONLY;
                        break;

                    case "email":
                        tokenSendMethod = MessageSendMethod.EMAILONLY;
                        break;

                    default:
                        throw new UnsupportedOperationException("unknown tokenSendMethod: " + methodParam);
                }
            }
            if (tokenSendMethod == MessageSendMethod.CHOICE_SMS_EMAIL) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_MISSING_CONTACT, "unable to determine appropriate send method, missing method parameter indication from operator");
                LOGGER.error(pwmRequest,errorInformation);
                pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation,pwmRequest));
                return ProcessStatus.Halt;
            }
        }

        final UserIdentity userIdentity = UserIdentity.fromKey(bodyParams.get("userKey"), pwmRequest.getPwmApplication());

        final HelpdeskDetailInfoBean helpdeskDetailInfoBean = HelpdeskDetailInfoBean.makeHelpdeskDetailInfo(pwmRequest, helpdeskProfile, userIdentity);
        if (helpdeskDetailInfoBean == null) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER, "unable to read helpdesk detail data for specified user");
            LOGGER.error(pwmRequest,errorInformation);
            pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation,pwmRequest));
            return ProcessStatus.Halt;
        }
        final UserInfo userInfo = helpdeskDetailInfoBean.getUserInfo();
        final UserDataReader userDataReader = LdapUserDataReader.appProxiedReader(pwmRequest.getPwmApplication(), userIdentity);
        final MacroMachine macroMachine = new MacroMachine(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel(), userInfo, null, userDataReader);
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
        if (userInfo.getUserSmsNumber() != null && !userInfo.getUserSmsNumber().isEmpty()) {
            if (tokenSendMethod == MessageSendMethod.BOTH || tokenSendMethod == MessageSendMethod.SMSFIRST || tokenSendMethod == MessageSendMethod.SMSONLY) {
                if (destDisplayString.length() > 0) {
                    destDisplayString.append(", ");
                }
                destDisplayString.append(userInfo.getUserSmsNumber());
            }
        }

        LOGGER.debug(pwmRequest, "generated token code for " + userIdentity.toDelimitedKey());

        final String smsMessage = config.readSettingAsLocalizedString(PwmSetting.SMS_HELPDESK_TOKEN_TEXT, pwmRequest.getLocale());

        try {
            TokenService.TokenSender.sendToken(
                    pwmRequest.getPwmApplication(),
                    userInfo,
                    macroMachine,
                    emailItemBean,
                    tokenSendMethod,
                    destEmailAddress,
                    userInfo.getUserSmsNumber(),
                    smsMessage,
                    tokenKey
            );
        } catch (PwmException e) {
            LOGGER.error(pwmRequest, e.getErrorInformation());
            pwmRequest.outputJsonResult(RestResultBean.fromError(e.getErrorInformation(),pwmRequest));
            return ProcessStatus.Halt;
        }

        StatisticsManager.incrementStat(pwmRequest,Statistic.HELPDESK_TOKENS_SENT);
        final HelpdeskVerificationRequestBean helpdeskVerificationRequestBean = new HelpdeskVerificationRequestBean();
        helpdeskVerificationRequestBean.setDestination(destDisplayString.toString());
        helpdeskVerificationRequestBean.setUserKey(bodyParams.get("userKey"));

        final HelpdeskVerificationRequestBean.TokenData tokenData = new HelpdeskVerificationRequestBean.TokenData();
        tokenData.setToken(tokenKey);
        tokenData.setIssueDate(new Date());

        final SecureService secureService = pwmRequest.getPwmApplication().getSecureService();
        helpdeskVerificationRequestBean.setTokenData(secureService.encryptObjectToString(tokenData));

        final RestResultBean restResultBean = new RestResultBean(helpdeskVerificationRequestBean);
        pwmRequest.outputJsonResult(restResultBean);
        LOGGER.debug(pwmRequest, "helpdesk operator "
                + pwmRequest.getUserInfoIfLoggedIn().toDisplayString()
                + " issued token for verification against user "
                + userIdentity.toDisplayString()
                + " sent to destination(s) "
                + destDisplayString
                + " (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")");
        return ProcessStatus.Halt;
    }

    @ActionHandler(action = "verifyVerificationToken")
    private ProcessStatus restVerifyVerificationTokenRequest(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException, ServletException
    {
        final Instant startTime = Instant.now();
        final HelpdeskVerificationRequestBean helpdeskVerificationRequestBean = JsonUtil.deserialize(
                pwmRequest.readRequestBodyAsString(),
                HelpdeskVerificationRequestBean.class
        );
        final String token = helpdeskVerificationRequestBean.getCode();

        final SecureService secureService = pwmRequest.getPwmApplication().getSecureService();
        final HelpdeskVerificationRequestBean.TokenData tokenData = secureService.decryptObject(
                helpdeskVerificationRequestBean.getTokenData(),
                HelpdeskVerificationRequestBean.TokenData.class
        );

        final UserIdentity userIdentity = UserIdentity.fromKey(helpdeskVerificationRequestBean.getUserKey(), pwmRequest.getPwmApplication());

        if (tokenData == null || tokenData.getIssueDate() == null || tokenData.getToken() == null || tokenData.getToken().isEmpty()) {
            final String errorMsg = "token data is corrupted";
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT, errorMsg));
        }

        final TimeDuration maxTokenAge = new TimeDuration(Long.parseLong(pwmRequest.getConfig().readAppProperty(AppProperty.HELPDESK_TOKEN_MAX_AGE)) * 1000);
        final Date maxTokenAgeTimestamp = new Date(System.currentTimeMillis() - maxTokenAge.getTotalMilliseconds());
        if (tokenData.getIssueDate().before(maxTokenAgeTimestamp)) {
            final String errorMsg = "token is older than maximum issue time (" + maxTokenAge.asCompactString() + ")";
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_TOKEN_EXPIRED, errorMsg));
        }

        final boolean passed = tokenData.getToken().equals(token);

        final HelpdeskVerificationStateBean verificationStateBean = HelpdeskVerificationStateBean.fromClientString(pwmRequest, helpdeskVerificationRequestBean.getVerificationState());

        if (passed) {
            final PwmSession pwmSession = pwmRequest.getPwmSession();
            final HelpdeskAuditRecord auditRecord = new AuditRecordFactory(pwmRequest).createHelpdeskAuditRecord(
                    AuditEvent.HELPDESK_VERIFY_TOKEN,
                    pwmSession.getUserInfo().getUserIdentity(),
                    null,
                    userIdentity,
                    pwmSession.getSessionStateBean().getSrcAddress(),
                    pwmSession.getSessionStateBean().getSrcHostname()
            );
            pwmRequest.getPwmApplication().getAuditManager().submit(auditRecord);
            verificationStateBean.addRecord(userIdentity, IdentityVerificationMethod.TOKEN);
        } else {
            final PwmSession pwmSession = pwmRequest.getPwmSession();
            final HelpdeskAuditRecord auditRecord = new AuditRecordFactory(pwmRequest).createHelpdeskAuditRecord(
                    AuditEvent.HELPDESK_VERIFY_TOKEN_INCORRECT,
                    pwmSession.getUserInfo().getUserIdentity(),
                    null,
                    userIdentity,
                    pwmSession.getSessionStateBean().getSrcAddress(),
                    pwmSession.getSessionStateBean().getSrcHostname()
            );
            pwmRequest.getPwmApplication().getAuditManager().submit(auditRecord);
        }

        // add a delay to prevent continuous checks
        final long delayMs = Long.parseLong(pwmRequest.getConfig().readAppProperty(AppProperty.HELPDESK_VERIFICATION_INVALID_DELAY_MS));
        while (TimeDuration.fromCurrent(startTime).isShorterThan(delayMs)) {
            JavaHelper.pause(100);
        }

        final HelpdeskVerificationResponseBean responseBean = new HelpdeskVerificationResponseBean(passed, verificationStateBean.toClientString(pwmRequest.getPwmApplication()));
        final RestResultBean restResultBean = new RestResultBean(responseBean);
        pwmRequest.outputJsonResult(restResultBean);
        return ProcessStatus.Halt;
    }

    @ActionHandler(action = "clearOtpSecret")
    private ProcessStatus restClearOtpSecret(
            final PwmRequest pwmRequest
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final HelpdeskProfile helpdeskProfile = getHelpdeskRProfile(pwmRequest);

        final Map<String,String> bodyMap = pwmRequest.readBodyAsJsonStringMap(PwmHttpRequestWrapper.Flag.BypassValidation);
        final UserIdentity userIdentity = userIdentityFromMap(pwmRequest, bodyMap);

        if (!helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_CLEAR_OTP_BUTTON)) {
            final String errorMsg = "clear otp request, but helpdesk clear otp button is not enabled";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg);
            LOGGER.error(pwmRequest, errorMsg);
            pwmRequest.respondWithError(errorInformation);
            return ProcessStatus.Halt;
        }

        //clear pwm intruder setting.
        pwmRequest.getPwmApplication().getIntruderManager().convenience().clearUserIdentity(userIdentity);

        try {

            final OtpService service = pwmRequest.getPwmApplication().getOtpService();
            service.clearOTPUserConfiguration(pwmRequest.getPwmSession(), userIdentity);
            {
                // mark the event log
                final HelpdeskAuditRecord auditRecord = new AuditRecordFactory(pwmRequest).createHelpdeskAuditRecord(
                        AuditEvent.HELPDESK_CLEAR_OTP_SECRET,
                        pwmRequest.getPwmSession().getUserInfo().getUserIdentity(),
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
            return ProcessStatus.Halt;
        }

        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setSuccessMessage(Message.getLocalizedMessage(pwmRequest.getLocale(),Message.Success_Unknown,pwmRequest.getConfig()));
        pwmRequest.outputJsonResult(restResultBean);
        return ProcessStatus.Halt;
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


    static ChaiUser getChaiUser(
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

    @ActionHandler(action = "checkVerification")
    private ProcessStatus restCheckVerification(final PwmRequest pwmRequest)
            throws IOException, PwmUnrecoverableException, ServletException {

        final HelpdeskProfile helpdeskProfile = getHelpdeskRProfile(pwmRequest);
        final Map<String,String> bodyMap = pwmRequest.readBodyAsJsonStringMap(PwmHttpRequestWrapper.Flag.BypassValidation);

        final UserIdentity userIdentity = userIdentityFromMap(pwmRequest, bodyMap);
        checkIfUserIdentityViewable(pwmRequest, helpdeskProfile, userIdentity);

        final String rawVerificationStr = bodyMap.get(HelpdeskVerificationStateBean.PARAMETER_VERIFICATION_STATE_KEY);
        final HelpdeskVerificationStateBean state = HelpdeskVerificationStateBean.fromClientString(pwmRequest, rawVerificationStr);
        final boolean passed = checkIfRequiredVerificationPassed(userIdentity, state, helpdeskProfile);
        final HashMap<String,Object> results = new HashMap<>();
        results.put("passed",passed);
        final RestResultBean restResultBean = new RestResultBean(results);
        pwmRequest.outputJsonResult(restResultBean);
        return ProcessStatus.Halt;
    }

    private boolean checkIfRequiredVerificationPassed(final UserIdentity userIdentity, final HelpdeskVerificationStateBean verificationStateBean, final HelpdeskProfile helpdeskProfile) {
        final Collection<IdentityVerificationMethod> requiredMethods = helpdeskProfile.readRequiredVerificationMethods();
        if (requiredMethods == null || requiredMethods.isEmpty()) {
            return true;
        }
        for (final IdentityVerificationMethod method : requiredMethods) {
            if (verificationStateBean.hasRecord(userIdentity, method)) {
                return true;
            }
        }
        return false;
    }

    @ActionHandler(action = "showVerifications")
    private ProcessStatus restShowVerifications(final PwmRequest pwmRequest)
            throws IOException, PwmUnrecoverableException, ServletException, ChaiUnavailableException
    {
        final Map<String,String> bodyMap = pwmRequest.readBodyAsJsonStringMap(PwmHttpRequestWrapper.Flag.BypassValidation);
        final String rawVerificationStr = bodyMap.get(HelpdeskVerificationStateBean.PARAMETER_VERIFICATION_STATE_KEY);
        final HelpdeskVerificationStateBean state = HelpdeskVerificationStateBean.fromClientString(pwmRequest, rawVerificationStr);
        final HashMap<String,Object> results = new HashMap<>();
        try {
            results.put("records",state.asViewableValidationRecords(pwmRequest.getPwmApplication(), pwmRequest.getLocale()));
        } catch (ChaiOperationException e) {
            throw PwmUnrecoverableException.fromChaiException(e);
        }
        final RestResultBean restResultBean = new RestResultBean(results);
        pwmRequest.outputJsonResult(restResultBean);
        return ProcessStatus.Halt;
    }

    private UserIdentity userIdentityFromMap(final PwmRequest pwmRequest, final Map<String,String> bodyMap) throws PwmUnrecoverableException {
        final String userKey = bodyMap.get("userKey");
        if (userKey == null || userKey.length() < 1) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"userKey parameter is missing");
            throw new PwmUnrecoverableException(errorInformation);
        }

        return UserIdentity.fromObfuscatedKey(userKey, pwmRequest.getPwmApplication());
    }

    @ActionHandler(action = "validateAttributes")
    private ProcessStatus restValidateAttributes(final PwmRequest pwmRequest)
            throws IOException, PwmUnrecoverableException, ServletException
    {
        final HelpdeskProfile helpdeskProfile = getHelpdeskRProfile(pwmRequest);
        final Instant startTime = Instant.now();
        final String bodyString = pwmRequest.readRequestBodyAsString();
        final HelpdeskVerificationRequestBean helpdeskVerificationRequestBean = JsonUtil.deserialize(
                bodyString,
                HelpdeskVerificationRequestBean.class
        );

        final UserIdentity userIdentity = UserIdentity.fromKey(helpdeskVerificationRequestBean.getUserKey(), pwmRequest.getPwmApplication());

        boolean passed = false;
        {
            final List<FormConfiguration> verificationForms = helpdeskProfile.readSettingAsForm(PwmSetting.HELPDESK_VERIFICATION_FORM);
            if (verificationForms == null || verificationForms.isEmpty()) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,"attempt to verify ldap attributes with no ldap verification attributes configured");
                throw new PwmUnrecoverableException(errorInformation);
            }

            final Map<String,String> bodyMap = JsonUtil.deserializeStringMap(bodyString);
            final ChaiUser chaiUser;
            try {
                chaiUser = getChaiUser(pwmRequest, helpdeskProfile, userIdentity);
            } catch (ChaiUnavailableException e) {
                throw new PwmUnrecoverableException(PwmError.forChaiError(e.getErrorCode()));
            }

            int successCount = 0;
            for (final FormConfiguration formConfiguration : verificationForms) {
                final String name = formConfiguration.getName();
                final String suppliedValue = bodyMap.get(name);
                try {
                    if (chaiUser.compareStringAttribute(name, suppliedValue)) {
                        successCount++;
                    }
                } catch (ChaiException e) {
                    LOGGER.error(pwmRequest, "error comparing ldap attribute during verification " + e.getMessage());
                }
            }
            if (successCount == verificationForms.size()) {
                passed = true;
            }
        }

        final HelpdeskVerificationStateBean verificationStateBean = HelpdeskVerificationStateBean.fromClientString(pwmRequest, helpdeskVerificationRequestBean.getVerificationState());

        if (passed) {
            final PwmSession pwmSession = pwmRequest.getPwmSession();
            final HelpdeskAuditRecord auditRecord = new AuditRecordFactory(pwmRequest).createHelpdeskAuditRecord(
                    AuditEvent.HELPDESK_VERIFY_ATTRIBUTES,
                    pwmSession.getUserInfo().getUserIdentity(),
                    null,
                    userIdentity,
                    pwmSession.getSessionStateBean().getSrcAddress(),
                    pwmSession.getSessionStateBean().getSrcHostname()
            );
            pwmRequest.getPwmApplication().getAuditManager().submit(auditRecord);
            verificationStateBean.addRecord(userIdentity, IdentityVerificationMethod.ATTRIBUTES);
        } else {
            final PwmSession pwmSession = pwmRequest.getPwmSession();
            final HelpdeskAuditRecord auditRecord = new AuditRecordFactory(pwmRequest).createHelpdeskAuditRecord(
                    AuditEvent.HELPDESK_VERIFY_ATTRIBUTES_INCORRECT,
                    pwmSession.getUserInfo().getUserIdentity(),
                    null,
                    userIdentity,
                    pwmSession.getSessionStateBean().getSrcAddress(),
                    pwmSession.getSessionStateBean().getSrcHostname()
            );
            pwmRequest.getPwmApplication().getAuditManager().submit(auditRecord);
        }

        // add a delay to prevent continuous checks
        final long delayMs = Long.parseLong(pwmRequest.getConfig().readAppProperty(AppProperty.HELPDESK_VERIFICATION_INVALID_DELAY_MS));
        while (TimeDuration.fromCurrent(startTime).isShorterThan(delayMs)) {
            JavaHelper.pause(100);
        }

        final HelpdeskVerificationResponseBean responseBean = new HelpdeskVerificationResponseBean(passed, verificationStateBean.toClientString(pwmRequest.getPwmApplication()));
        final RestResultBean restResultBean = new RestResultBean(responseBean);
        pwmRequest.outputJsonResult(restResultBean);
        return ProcessStatus.Halt;
    }

    private static void sendUnlockNoticeEmail(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmRequest.getConfig();
        final Locale locale = pwmRequest.getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_HELPDESK_UNLOCK, locale);

        if (configuredEmailSetting == null) {
            LOGGER.debug(pwmRequest, "skipping send helpdesk unlock notice email for '" + userIdentity + "' no email configured");
            return;
        }

        final HelpdeskDetailInfoBean helpdeskDetailInfoBean = HelpdeskDetailInfoBean.makeHelpdeskDetailInfo(pwmRequest, helpdeskProfile, userIdentity);
        final MacroMachine macroMachine = new MacroMachine(
                pwmApplication,
                pwmRequest.getSessionLabel(),
                helpdeskDetailInfoBean.getUserInfo(),
                null,
                LdapUserDataReader.appProxiedReader(pwmApplication, userIdentity)
        );

        pwmApplication.getEmailQueue().submitEmail(
                configuredEmailSetting,
                helpdeskDetailInfoBean.getUserInfo(),
                macroMachine
        );
    }
}
