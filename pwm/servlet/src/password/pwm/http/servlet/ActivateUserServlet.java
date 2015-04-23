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

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ImpossiblePasswordPolicyException;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.*;
import password.pwm.config.*;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.error.*;
import password.pwm.event.AuditEvent;
import password.pwm.event.AuditRecord;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.ActivateUserBean;
import password.pwm.i18n.Message;
import password.pwm.ldap.LdapPermissionTester;
import password.pwm.ldap.LdapUserDataReader;
import password.pwm.ldap.UserDataReader;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.token.TokenPayload;
import password.pwm.token.TokenService;
import password.pwm.util.PostChangePasswordAction;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.client.rest.RestTokenDataClient;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;

/**
 * User interaction servlet for creating new users (self registration)
 *
 * @author Jason D. Rivard
 */
public class ActivateUserServlet extends PwmServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(ActivateUserServlet.class);
    private static final String TOKEN_NAME = ActivateUserServlet.class.getName();

    public enum ActivateUserAction implements PwmServlet.ProcessAction {
        activate(HttpMethod.POST),
        enterCode(HttpMethod.POST, HttpMethod.GET),
        reset(HttpMethod.POST),
        agree(HttpMethod.POST),

        ;

        private final Collection<HttpMethod> method;

        ActivateUserAction(HttpMethod... method)
        {
            this.method = Collections.unmodifiableList(Arrays.asList(method));
        }

        public Collection<HttpMethod> permittedMethods()
        {
            return method;
        }
    }

    protected ActivateUserAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        try {
            return ActivateUserAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }



    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
        //Fetch the session state bean.
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        final Configuration config = pwmApplication.getConfig();

        final ActivateUserBean activateUserBean = pwmSession.getActivateUserBean();

        if (!config.readSettingAsBoolean(PwmSetting.ACTIVATE_USER_ENABLE)) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,"activate user is not enabled");
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        if (pwmSession.getSessionStateBean().isAuthenticated()) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_USERAUTHENTICATED);
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        final ActivateUserAction action = readProcessAction(pwmRequest);

        // convert a url command like /pwm/public/NewUserServlet/12321321 to redirect with a process action.
        if (action == null) {
            if (pwmRequest.convertURLtokenCommand()) {
                return;
            }
        } else {
            pwmRequest.validatePwmFormID();
            switch (action) {
                case activate:
                    handleActivationRequest(pwmRequest);
                    break;

                case enterCode:
                    handleEnterTokenCode(pwmRequest);
                    break;

                case reset:
                    pwmSession.clearSessionBean(ActivateUserBean.class);
                    advanceToNextStage(pwmRequest);
                    break;

                case agree:
                    handleAgreeRequest(pwmRequest, activateUserBean);
                    advanceToNextStage(pwmRequest);
                    break;
            }
        }

        if (!pwmRequest.getPwmResponse().isCommitted()) {
            this.advanceToNextStage(pwmRequest);
        }
    }

    public void handleActivationRequest(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Configuration config = pwmApplication.getConfig();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        pwmSession.clearActivateUserBean();
        final List<FormConfiguration> configuredActivationForm = config.readSettingAsForm(PwmSetting.ACTIVATE_USER_FORM);

        Map<FormConfiguration,String> formValues = new HashMap();
        try {
            //read the values from the request
            formValues = FormUtility.readFormValuesFromRequest(pwmRequest, configuredActivationForm,
                    ssBean.getLocale());

            // check for intruders
            pwmApplication.getIntruderManager().convenience().checkAttributes(formValues);

            // read the context attr
            final String contextParam = pwmRequest.readParameterAsString(PwmConstants.PARAM_CONTEXT);

            // read the profile attr
            final String ldapProfile = pwmRequest.readParameterAsString(PwmConstants.PARAM_LDAP_PROFILE);

            // see if the values meet the configured form requirements.
            FormUtility.validateFormValues(config, formValues, ssBean.getLocale());

            final String searchFilter = figureLdapSearchFilter(pwmRequest);

            // read an ldap user object based on the params
            final UserIdentity userIdentity;
            {
                final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication, pwmSession.getLabel());
                final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
                searchConfiguration.setContexts(Collections.singletonList(contextParam));
                searchConfiguration.setFilter(searchFilter);
                searchConfiguration.setFormValues(formValues);
                searchConfiguration.setLdapProfile(ldapProfile);
                userIdentity = userSearchEngine.performSingleUserSearch(searchConfiguration);
            }

            validateParamsAgainstLDAP(pwmRequest, formValues, userIdentity);

            final List<UserPermission> userPermissions = config.readSettingAsUserPermission(PwmSetting.ACTIVATE_USER_QUERY_MATCH);
            if (!LdapPermissionTester.testUserPermissions(pwmApplication, pwmSession.getLabel(), userIdentity, userPermissions)) {
                final String errorMsg = "user " + userIdentity + " attempted activation, but does not match query string";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_ACTIVATE_USER_NO_QUERY_MATCH, errorMsg);
                pwmApplication.getIntruderManager().convenience().markUserIdentity(userIdentity, pwmSession);
                pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
                throw new PwmUnrecoverableException(errorInformation);
            }

            final ActivateUserBean activateUserBean = pwmSession.getActivateUserBean();
            activateUserBean.setUserIdentity(userIdentity);
            activateUserBean.setFormValidated(true);
            pwmApplication.getIntruderManager().convenience().clearAttributes(formValues);
            pwmApplication.getIntruderManager().convenience().clearAddressAndSession(pwmSession);
        } catch (PwmOperationalException e) {
            pwmApplication.getIntruderManager().convenience().markAttributes(formValues, pwmSession);
            pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
            pwmRequest.setResponseError(e.getErrorInformation());
            LOGGER.debug(pwmSession.getLabel(),e.getErrorInformation().toDebugStr());
        }

        // redirect user to change password screen.
        advanceToNextStage(pwmRequest);
    }

    private void handleAgreeRequest(
            final PwmRequest pwmRequest,
            final ActivateUserBean activateUserBean
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        LOGGER.debug(pwmRequest, "user accepted agreement");

        if (!activateUserBean.isAgreementPassed()) {
            activateUserBean.setAgreementPassed(true);
            AuditRecord auditRecord = pwmRequest.getPwmApplication().getAuditManager().createUserAuditRecord(
                    AuditEvent.AGREEMENT_PASSED,
                    pwmRequest.getUserInfoIfLoggedIn(),
                    pwmRequest.getSessionLabel(),
                    "ActivateUser"
            );
            pwmRequest.getPwmApplication().getAuditManager().submit(auditRecord);
        }
    }


    private void advanceToNextStage(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Configuration config = pwmApplication.getConfig();
        final ActivateUserBean activateUserBean = pwmSession.getActivateUserBean();

        if (!activateUserBean.isFormValidated() || activateUserBean.getUserIdentity() == null) {
            forwardToActivateUserForm(pwmRequest);
            return;
        }

        final boolean tokenRequired = MessageSendMethod.NONE != MessageSendMethod.valueOf(config.readSettingAsString(PwmSetting.ACTIVATE_TOKEN_SEND_METHOD));
        if (tokenRequired) {
            if (!activateUserBean.isTokenIssued()) {
                try {
                    final Locale locale = pwmSession.getSessionStateBean().getLocale();
                    initializeToken(pwmRequest, locale, activateUserBean.getUserIdentity());
                } catch (PwmOperationalException e) {
                    pwmRequest.setResponseError(e.getErrorInformation());
                    forwardToActivateUserForm(pwmRequest);
                    return;
                }
            }

            if (!activateUserBean.isTokenPassed()) {
                pwmRequest.forwardToJsp(PwmConstants.JSP_URL.ACTIVATE_USER_ENTER_CODE);
                return;
            }
        }

        final String agreementText = config.readSettingAsLocalizedString(
                PwmSetting.ACTIVATE_AGREEMENT_MESSAGE,
                pwmSession.getSessionStateBean().getLocale()
        );
        if (agreementText != null && agreementText.length() > 0 && !activateUserBean.isAgreementPassed()) {
            if (activateUserBean.getAgreementText() == null) {
                final MacroMachine macroMachine = MacroMachine.forUser(pwmRequest, activateUserBean.getUserIdentity());
                final String expandedText = macroMachine.expandMacros(agreementText);
                activateUserBean.setAgreementText(expandedText);
            }
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.ACTIVATE_USER_AGREEMENT);
            return;
        }

        try {
            activateUser(pwmRequest, activateUserBean.getUserIdentity());
            pwmRequest.forwardToSuccessPage(Message.Success_ActivateUser);
        } catch (PwmOperationalException e) {
            LOGGER.debug(pwmRequest, e.getErrorInformation());
            pwmApplication.getIntruderManager().convenience().markUserIdentity(activateUserBean.getUserIdentity(),pwmSession);
            pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
            pwmRequest.respondWithError(e.getErrorInformation());
        }
    }

    public void activateUser(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Configuration config = pwmApplication.getConfig();
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userIdentity);
        if (config.readSettingAsBoolean(PwmSetting.ACTIVATE_USER_UNLOCK)) {
            try {
                theUser.unlockPassword();
            } catch (ChaiOperationException e) {
                final String errorMsg = "error unlocking user " + userIdentity + ": " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_ACTIVATION_FAILURE, errorMsg);
                throw new PwmOperationalException(errorInformation);
            }
        }

        try {
            {  // execute configured actions
                LOGGER.debug(pwmSession.getLabel(), "executing configured pre-actions to user " + theUser.getEntryDN());
                final List<ActionConfiguration> configValues = config.readSettingAsAction(PwmSetting.ACTIVATE_USER_PRE_WRITE_ATTRIBUTES);
                if (configValues != null && !configValues.isEmpty()) {
                    final MacroMachine macroMachine = MacroMachine.forUser(pwmRequest, userIdentity);

                    final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings(pwmApplication, userIdentity)
                            .setExpandPwmMacros(true)
                            .setMacroMachine(macroMachine)
                            .createActionExecutor();

                    actionExecutor.executeActions(configValues, pwmSession);
                }
            }

            //authenticate the pwm session
            SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(pwmApplication, pwmSession);
            sessionAuthenticator.authUserWithUnknownPassword(userIdentity,AuthenticationType.AUTH_FROM_PUBLIC_MODULE);

            //ensure a change password is triggered
            pwmSession.getLoginInfoBean().setAuthenticationType(AuthenticationType.AUTH_FROM_PUBLIC_MODULE);
            pwmSession.getUserInfoBean().setRequiresNewPassword(true);

            // mark the event log
            pwmApplication.getAuditManager().submit(AuditEvent.ACTIVATE_USER, pwmSession.getUserInfoBean(), pwmSession);

            // set the session success message
            pwmSession.getSessionStateBean().setSessionSuccess(Message.Success_ActivateUser, null);

            // update the stats bean
            pwmApplication.getStatisticsManager().incrementValue(Statistic.ACTIVATED_USERS);

            // send email or sms
            sendPostActivationNotice(pwmRequest);

            // setup post-change attributes
            final PostChangePasswordAction postAction = new PostChangePasswordAction() {

                public String getLabel() {
                    return "ActivateUser write attributes";
                }

                public boolean doAction(final PwmSession pwmSession, final String newPassword)
                        throws PwmUnrecoverableException {
                    try {
                        {  // execute configured actions
                            LOGGER.debug(pwmSession.getLabel(), "executing post-activate configured actions to user " + userIdentity.toDisplayString());

                            final MacroMachine macroMachine = pwmSession.getSessionManager().getMacroMachine(pwmApplication);
                            final List<ActionConfiguration> configValues = pwmApplication.getConfig().readSettingAsAction(PwmSetting.ACTIVATE_USER_POST_WRITE_ATTRIBUTES);

                            final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings(pwmApplication, userIdentity)
                                    .setExpandPwmMacros(true)
                                    .setMacroMachine(macroMachine)
                                    .createActionExecutor();
                            actionExecutor.executeActions(configValues, pwmSession);
                        }
                    } catch (PwmOperationalException e) {
                        final ErrorInformation info = new ErrorInformation(PwmError.ERROR_ACTIVATION_FAILURE, e.getErrorInformation().getDetailedErrorMsg(), e.getErrorInformation().getFieldValues());
                        final PwmUnrecoverableException newException = new PwmUnrecoverableException(info);
                        newException.initCause(e);
                        throw newException;
                    } catch (ChaiUnavailableException e) {
                        final String errorMsg = "unable to reach ldap server while writing post-activate attributes: " + e.getMessage();
                        final ErrorInformation info = new ErrorInformation(PwmError.ERROR_ACTIVATION_FAILURE, errorMsg);
                        final PwmUnrecoverableException newException = new PwmUnrecoverableException(info);
                        newException.initCause(e);
                        throw newException;
                    }
                    return true;
                }
            };

            pwmSession.getLoginInfoBean().addPostChangePasswordActions("activateUserWriteAttributes", postAction);
        } catch (ImpossiblePasswordPolicyException e) {
            final ErrorInformation info = new ErrorInformation(PwmError.ERROR_UNKNOWN, "unexpected ImpossiblePasswordPolicyException error while activating user");
            LOGGER.warn(pwmSession, info, e);
            throw new PwmOperationalException(info);
        }
    }

    protected static void validateParamsAgainstLDAP(
            final PwmRequest pwmRequest,
            final Map<FormConfiguration, String> formValues,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmDataValidationException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final String searchFilter = figureLdapSearchFilter(pwmRequest);
        final ChaiUser chaiUser = ChaiFactory.createChaiUser(userIdentity.getUserDN(), pwmApplication.getProxyChaiProvider(userIdentity.getLdapProfileID()));

        for (final FormConfiguration formItem : formValues.keySet()) {
            final String attrName = formItem.getName();
            final String tokenizedAttrName = "%" + attrName + "%";
            if (searchFilter.contains(tokenizedAttrName)) {
                LOGGER.trace(pwmSession, "skipping validation of ldap value for '" + attrName + "' because it is in search filter");
            } else {
                final String value = formValues.get(formItem);
                try {
                    if (!chaiUser.compareStringAttribute(attrName, value)) {
                        final String errorMsg = "incorrect value for '" + attrName + "'";
                        final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_ACTIVATION_VALIDATION_FAILED, errorMsg, new String[]{attrName});
                        LOGGER.debug(pwmSession.getLabel(), errorInfo.toDebugStr());
                        throw new PwmDataValidationException(errorInfo);
                    }
                    LOGGER.trace(pwmSession.getLabel(), "successful validation of ldap value for '" + attrName + "'");
                } catch (ChaiOperationException e) {
                    LOGGER.error(pwmSession.getLabel(), "error during param validation of '" + attrName + "', error: " + e.getMessage());
                    throw new PwmDataValidationException(new ErrorInformation(PwmError.ERROR_ACTIVATION_VALIDATION_FAILED, "ldap error testing value for '" + attrName + "'", new String[]{attrName}));
                }
            }
        }
    }

    private void sendPostActivationNotice(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Configuration config = pwmApplication.getConfig();
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
        final MessageSendMethod pref = MessageSendMethod.valueOf(config.readSettingAsString(PwmSetting.ACTIVATE_TOKEN_SEND_METHOD));
        final boolean success;
        switch (pref) {
            case BOTH:
                // Send both email and SMS, success if one of both succeeds
                final boolean suc1 = sendPostActivationEmail(pwmRequest);
                final boolean suc2 = sendPostActivationSms(pwmRequest);
                success = suc1 || suc2;
                break;
            case EMAILFIRST:
                // Send email first, try SMS if email is not available
                success = sendPostActivationEmail(pwmRequest) || sendPostActivationSms(pwmRequest);
                break;
            case SMSFIRST:
                // Send SMS first, try email if SMS is not available
                success = sendPostActivationSms(pwmRequest) || sendPostActivationEmail(pwmRequest);
                break;
            case SMSONLY:
                // Only try SMS
                success = sendPostActivationSms(pwmRequest);
                break;
            case EMAILONLY:
            default:
                // Only try email
                success = sendPostActivationEmail(pwmRequest);
                break;
        }
        if (!success) {
            LOGGER.warn(pwmSession, "skipping send activation message for '" + userInfoBean.getUserIdentity() + "' no email or SMS number configured");
        }
    }

    private boolean sendPostActivationEmail(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_ACTIVATION, locale);

        if (configuredEmailSetting == null) {
            LOGGER.debug(pwmSession, "skipping send activation email for '" + userInfoBean.getUserIdentity() + "' no email configured");
            return false;
        }

        pwmApplication.getEmailQueue().submitEmail(
                configuredEmailSetting,
                pwmSession.getUserInfoBean(),
                pwmSession.getSessionManager().getMacroMachine(pwmApplication)
        );
        return true;
    }

    private Boolean sendPostActivationSms(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Configuration config = pwmApplication.getConfig();
        final UserDataReader userDataReader = pwmSession.getSessionManager().getUserDataReader(pwmApplication);
        final Locale locale = pwmSession.getSessionStateBean().getLocale();

        final String message = config.readSettingAsLocalizedString(PwmSetting.SMS_ACTIVATION_TEXT, locale);

        final String toSmsNumber;
        try {
            toSmsNumber = userDataReader.readStringAttribute(config.readSettingAsString(PwmSetting.SMS_USER_PHONE_ATTRIBUTE));
        } catch (Exception e) {
            LOGGER.debug(pwmSession.getLabel(), "error reading SMS attribute from user '" + pwmSession.getUserInfoBean().getUserIdentity() + "': " + e.getMessage());
            return false;
        }

        if (toSmsNumber == null || toSmsNumber.length() < 1) {
            LOGGER.debug(pwmSession.getLabel(), "skipping send activation SMS for '" + pwmSession.getUserInfoBean().getUserIdentity() + "' no SMS number configured");
            return false;
        }

        final SmsItemBean smsItem = new SmsItemBean(toSmsNumber, message);
        pwmApplication.sendSmsUsingQueue(smsItem, pwmSession.getSessionManager().getMacroMachine(pwmApplication));
        return true;
    }

    public static void initializeToken(
            final PwmRequest pwmRequest,
            final Locale locale,
            final UserIdentity userIdentity

    )
            throws PwmUnrecoverableException, PwmOperationalException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ActivateUserBean activateUserBean = pwmSession.getActivateUserBean();
        final Configuration config = pwmApplication.getConfig();

        final RestTokenDataClient.TokenDestinationData inputTokenDestData;
        {
            final UserDataReader dataReader = LdapUserDataReader.appProxiedReader(pwmApplication, userIdentity);
            final String toAddress;
            try {
                toAddress = dataReader.readStringAttribute(config.readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE));
            } catch (ChaiOperationException e) {
                final String errorMsg = "unable to read user email attribute due to ldap error, unable to send token: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_ACTIVATION_FAILURE, errorMsg);
                LOGGER.error(pwmSession.getLabel(), errorInformation);
                throw new PwmOperationalException(errorInformation);
            }

            final String toSmsNumber;
            try {
                toSmsNumber = dataReader.readStringAttribute(config.readSettingAsString(PwmSetting.SMS_USER_PHONE_ATTRIBUTE));
            } catch (Exception e) {
                final String errorMsg = "unable to read user SMS attribute due to ldap error, unable to send token: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_ACTIVATION_FAILURE, errorMsg);
                LOGGER.error(pwmSession.getLabel(), errorInformation);
                throw new PwmOperationalException(errorInformation);
            }
            inputTokenDestData = new RestTokenDataClient.TokenDestinationData(toAddress,toSmsNumber,null);
        }

        final RestTokenDataClient restTokenDataClient = new RestTokenDataClient(pwmApplication);
        final RestTokenDataClient.TokenDestinationData outputDestTokenData = restTokenDataClient.figureDestTokenDisplayString(
                pwmSession.getLabel(),
                inputTokenDestData,
                activateUserBean.getUserIdentity(),
                pwmSession.getSessionStateBean().getLocale());

        final Set<String> destinationValues = new HashSet<>();
        if (outputDestTokenData.getEmail() != null) {
            destinationValues.add(outputDestTokenData.getEmail());
        }
        if (outputDestTokenData.getSms() != null) {
            destinationValues.add(outputDestTokenData.getSms());
        }


        final Map<String,String> tokenMapData = new HashMap<>();

        try {
            final Date userLastPasswordChange = PasswordUtility.determinePwdLastModified(
                    pwmApplication,
                    pwmSession.getLabel(),
                    activateUserBean.getUserIdentity());
            if (userLastPasswordChange != null) {
                tokenMapData.put(PwmConstants.TOKEN_KEY_PWD_CHG_DATE, PwmConstants.DEFAULT_DATETIME_FORMAT.format(userLastPasswordChange));
            }
        } catch (ChaiUnavailableException e) {
            LOGGER.error(pwmSession.getLabel(), "unexpected error reading user's last password change time");
        }

        final String tokenKey;
        final TokenPayload tokenPayload;
        try {
            tokenPayload = pwmApplication.getTokenService().createTokenPayload(TOKEN_NAME, tokenMapData, userIdentity, destinationValues);
            tokenKey = pwmApplication.getTokenService().generateNewToken(tokenPayload, pwmRequest.getSessionLabel());
            LOGGER.debug(pwmSession.getLabel(), "generated activate user tokenKey code for session");
        } catch (PwmOperationalException e) {
            throw new PwmUnrecoverableException(e.getErrorInformation());
        }

        sendToken(pwmRequest, userIdentity, locale, outputDestTokenData.getEmail(), outputDestTokenData.getSms(), tokenKey);
        activateUserBean.setTokenDisplayText(outputDestTokenData.getDisplayValue());
        activateUserBean.setTokenIssued(true);
    }

    private void handleEnterTokenCode(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ActivateUserBean activateUserBean = pwmSession.getActivateUserBean();
        final String userEnteredCode = pwmRequest.readParameterAsString(PwmConstants.PARAM_TOKEN);

        ErrorInformation errorInformation = null;
        try {
            final TokenPayload tokenPayload = pwmApplication.getTokenService().processUserEnteredCode(
                    pwmSession,
                    activateUserBean.getUserIdentity(),
                    TOKEN_NAME,
                    userEnteredCode
            );
            if (tokenPayload != null) {
                activateUserBean.setUserIdentity(tokenPayload.getUserIdentity());
                activateUserBean.setTokenPassed(true);
                activateUserBean.setFormValidated(true);
            }
        } catch (PwmOperationalException e) {
            final String errorMsg = "token incorrect: " + e.getMessage();
            errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,errorMsg);
        }

        if (!activateUserBean.isTokenPassed()) {
            if (errorInformation == null) {
                errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT);
            }
            LOGGER.debug(pwmSession.getLabel(), errorInformation.toDebugStr());
            pwmRequest.setResponseError(errorInformation);
        }

        this.advanceToNextStage(pwmRequest);
    }

    private static void sendToken(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity,
            final Locale userLocale,
            final String toAddress,
            final String toSmsNumber,
            final String tokenKey
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmApplication.getConfig();
        final MessageSendMethod pref = MessageSendMethod.valueOf(config.readSettingAsString(PwmSetting.ACTIVATE_TOKEN_SEND_METHOD));
        final EmailItemBean emailItemBean = config.readSettingAsEmail(PwmSetting.EMAIL_ACTIVATION_VERIFICATION, userLocale);
        final String smsMessage = config.readSettingAsLocalizedString(PwmSetting.SMS_ACTIVATION_VERIFICATION_TEXT, userLocale);

        final MacroMachine macroMachine = MacroMachine.forUser(pwmRequest, userIdentity);

        TokenService.TokenSender.sendToken(
                pwmApplication,
                null,
                macroMachine,
                emailItemBean,
                pref,
                toAddress,
                toSmsNumber,
                smsMessage,
                tokenKey
        );
    }

    private static String figureLdapSearchFilter(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmApplication.getConfig();
        final List<FormConfiguration> configuredActivationForm = config.readSettingAsForm(PwmSetting.ACTIVATE_USER_FORM);

        final String configuredSearchFilter = config.readSettingAsString(PwmSetting.ACTIVATE_USER_SEARCH_FILTER);
        final String searchFilter;
        if (configuredSearchFilter == null || configuredSearchFilter.isEmpty()) {
            searchFilter = FormUtility.ldapSearchFilterForForm(pwmApplication,configuredActivationForm);
            LOGGER.trace(pwmRequest,"auto generated search filter based on activation form: " + searchFilter);
        } else {
            searchFilter = configuredSearchFilter;
        }
        return searchFilter;
    }
    
    private static void forwardToActivateUserForm(final PwmRequest pwmRequest) 
            throws ServletException, PwmUnrecoverableException, IOException 
    {
        pwmRequest.addFormInfoToRequestAttr(PwmSetting.ACTIVATE_USER_FORM,false,false);
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.ACTIVATE_USER);
    }
}
