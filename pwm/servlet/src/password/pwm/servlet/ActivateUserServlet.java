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

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ImpossiblePasswordPolicyException;
import password.pwm.*;
import password.pwm.bean.*;
import password.pwm.bean.servlet.ActivateUserBean;
import password.pwm.config.*;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.error.*;
import password.pwm.event.AuditEvent;
import password.pwm.i18n.Message;
import password.pwm.ldap.LdapUserDataReader;
import password.pwm.ldap.UserAuthenticator;
import password.pwm.ldap.UserDataReader;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.token.TokenPayload;
import password.pwm.util.Helper;
import password.pwm.util.PostChangePasswordAction;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.client.rest.RestTokenDataClient;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 * User interaction servlet for creating new users (self registration)
 *
 * @author Jason D. Rivard
 */
public class ActivateUserServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ActivateUserServlet.class);
    private static final String TOKEN_NAME = ActivateUserServlet.class.getName();


// -------------------------- OTHER METHODS --------------------------

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
        //Fetch the session state bean.
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        final Configuration config = pwmApplication.getConfig();
        final String processAction = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);

        final ActivateUserBean activateUserBean = pwmSession.getActivateUserBean();

        if (!config.readSettingAsBoolean(PwmSetting.ACTIVATE_USER_ENABLE)) {
            ssBean.setSessionError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (pwmSession.getSessionStateBean().isAuthenticated()) {
            ssBean.setSessionError(PwmError.ERROR_USERAUTHENTICATED.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        // convert a url command like /pwm/public/NewUserServlet/12321321 to redirect with a process action.
        if (processAction == null || processAction.length() < 1) {
            if (convertURLtokenCommand(req, resp, pwmApplication, pwmSession)) {
                return;
            }
        }

        if (processAction != null && processAction.length() > 0) {
            Validator.validatePwmFormID(req);
            if ("activate".equalsIgnoreCase(processAction)) {
                handleActivationRequest(req, resp);
            } else if ("enterCode".equalsIgnoreCase(processAction)) {
                handleEnterTokenCode(req, resp, pwmApplication, pwmSession);
            } else if ("reset".equalsIgnoreCase(processAction)) {
                pwmSession.clearSessionBean(ActivateUserBean.class);
                advanceToNextStage(req, resp);
            } else if ("agree".equalsIgnoreCase(processAction)) {         // accept password change agreement
                LOGGER.debug(pwmSession, "user accepted activate user agreement");
                activateUserBean.setAgreementPassed(true);
                advanceToNextStage(req, resp);
            }
        }

        if (!resp.isCommitted()) {
            this.advanceToNextStage(req, resp);
        }
    }

    public void handleActivationRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final Configuration config = pwmApplication.getConfig();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        pwmSession.clearActivateUserBean();
        final List<FormConfiguration> formItem = config.readSettingAsForm(PwmSetting.ACTIVATE_USER_FORM);

        Map<FormConfiguration,String> formValues = new HashMap();
        try {
            //read the values from the request
            formValues = Validator.readFormValuesFromRequest(req, formItem, ssBean.getLocale());

            // check for intruders
            pwmApplication.getIntruderManager().convenience().checkAttributes(formValues);

            // read the context attr
            final String contextParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_CONTEXT, 1024, "");

            // read the profile attr
            final String ldapProfile = Validator.readStringFromRequest(req, PwmConstants.PARAM_LDAP_PROFILE, 1024, "");

            // see if the values meet the configured form requirements.
            Validator.validateParmValuesMeetRequirements(formValues, ssBean.getLocale());

            // get an ldap user object based on the params
            final UserIdentity userIdentity;
            {
                final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
                final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
                searchConfiguration.setContexts(Collections.singletonList(contextParam));
                searchConfiguration.setFilter(config.readSettingAsString(PwmSetting.ACTIVATE_USER_SEARCH_FILTER));
                searchConfiguration.setFormValues(formValues);
                searchConfiguration.setLdapProfile(ldapProfile);
                userIdentity = userSearchEngine.performSingleUserSearch(pwmSession, searchConfiguration);
            }

            validateParamsAgainstLDAP(formValues, pwmApplication, pwmSession, userIdentity, config);

            final List<UserPermission> userPermissions = config.readSettingAsUserPermission(PwmSetting.ACTIVATE_USER_QUERY_MATCH);
            if (!Helper.testUserPermissions(pwmApplication, pwmSession, userIdentity, userPermissions)) {
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
            ssBean.setSessionError(e.getErrorInformation());
            LOGGER.debug(pwmSession,e.getErrorInformation().toDebugStr());
        }

        // redirect user to change password screen.
        advanceToNextStage(req,resp);
    }

    private void advanceToNextStage(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final Configuration config = pwmApplication.getConfig();
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ActivateUserBean activateUserBean = pwmSession.getActivateUserBean();

        if (!activateUserBean.isFormValidated() || activateUserBean.getUserIdentity() == null) {
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.ACTIVATE_USER);
            return;
        }

        final boolean tokenRequired = MessageSendMethod.NONE != MessageSendMethod.valueOf(config.readSettingAsString(PwmSetting.ACTIVATE_TOKEN_SEND_METHOD));
        if (tokenRequired) {
            if (!activateUserBean.isTokenIssued()) {
                try {
                    final Locale locale = pwmSession.getSessionStateBean().getLocale();
                    initializeToken(pwmSession, pwmApplication, locale, activateUserBean.getUserIdentity());
                } catch (PwmOperationalException e) {
                    pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
                    ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.ACTIVATE_USER);
                    return;
                }
            }

            if (!activateUserBean.isTokenPassed()) {
                ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.ACTIVATE_USER_ENTER_CODE);
                return;
            }
        }

        final String agreementMessage = config.readSettingAsLocalizedString(PwmSetting.ACTIVATE_AGREEMENT_MESSAGE,
                pwmSession.getSessionStateBean().getLocale());
        if (agreementMessage != null && agreementMessage.length() > 0 && !activateUserBean.isAgreementPassed()) {
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.ACTIVATE_USER_AGREEMENT);
            return;
        }

        try {
            activateUser(pwmSession, pwmApplication, activateUserBean.getUserIdentity());
            ServletHelper.forwardToSuccessPage(req, resp);
        } catch (PwmOperationalException e) {
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            LOGGER.debug(pwmSession, e.getErrorInformation().toDebugStr());
            pwmApplication.getIntruderManager().convenience().markUserIdentity(activateUserBean.getUserIdentity(),pwmSession);
            pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
        }
    }

    public void activateUser(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        Configuration config = pwmApplication.getConfig();
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userIdentity);
        if (config.readSettingAsBoolean(PwmSetting.ACTIVATE_USER_UNLOCK)) {
            try {
                theUser.unlock();
            } catch (ChaiOperationException e) {
                final String errorMsg = "error unlocking user " + userIdentity + ": " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_ACTIVATION_FAILURE, errorMsg);
                throw new PwmOperationalException(errorInformation);
            }
        }

        try {
            {  // execute configured actions
                LOGGER.debug(pwmSession, "executing configured actions to user " + theUser.getEntryDN());
                final List<ActionConfiguration> configValues = config.readSettingAsAction(PwmSetting.ACTIVATE_USER_PRE_WRITE_ATTRIBUTES);
                final ActionExecutor.ActionExecutorSettings settings = new ActionExecutor.ActionExecutorSettings();
                settings.setExpandPwmMacros(true);
                settings.setUserInfoBean(pwmSession.getUserInfoBean());
                final ActionExecutor actionExecutor = new ActionExecutor(pwmApplication);
                actionExecutor.executeActions(configValues, settings, pwmSession);
            }

            //authenticate the pwm session
            UserAuthenticator.authUserWithUnknownPassword(userIdentity, pwmSession, pwmApplication, true, UserInfoBean.AuthenticationType.AUTH_FROM_FORGOTTEN);

            //ensure a change password is triggered
            pwmSession.getUserInfoBean().setAuthenticationType(UserInfoBean.AuthenticationType.AUTH_FROM_FORGOTTEN);
            pwmSession.getUserInfoBean().setRequiresNewPassword(true);

            // mark the event log
            pwmApplication.getAuditManager().submit(AuditEvent.ACTIVATE_USER, pwmSession.getUserInfoBean(), pwmSession);

            // set the session success message
            pwmSession.getSessionStateBean().setSessionSuccess(Message.SUCCESS_ACTIVATE_USER, null);

            // update the stats bean
            pwmApplication.getStatisticsManager().incrementValue(Statistic.ACTIVATED_USERS);

            // send email or sms
            sendPostActivationNotice(pwmSession, pwmApplication);

            // setup post-change attributes
            final PostChangePasswordAction postAction = new PostChangePasswordAction() {

                public String getLabel() {
                    return "ActivateUser write attributes";
                }

                public boolean doAction(final PwmSession pwmSession, final String newPassword)
                        throws PwmUnrecoverableException {
                    try {
                        {  // execute configured actions
                            final ChaiUser proxiedUser = pwmApplication.getProxiedChaiUser(userIdentity);
                            LOGGER.debug(pwmSession, "executing post-activate configured actions to user " + proxiedUser.getEntryDN());
                            final List<ActionConfiguration> configValues = pwmApplication.getConfig().readSettingAsAction(PwmSetting.ACTIVATE_USER_POST_WRITE_ATTRIBUTES);
                            final ActionExecutor.ActionExecutorSettings settings = new ActionExecutor.ActionExecutorSettings();
                            settings.setExpandPwmMacros(true);
                            settings.setUserInfoBean(pwmSession.getUserInfoBean());
                            final ActionExecutor actionExecutor = new ActionExecutor(pwmApplication);
                            actionExecutor.executeActions(configValues, settings, pwmSession);
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

            pwmSession.getUserInfoBean().addPostChangePasswordActions("activateUserWriteAttributes", postAction);
        } catch (ImpossiblePasswordPolicyException e) {
            final ErrorInformation info = new ErrorInformation(PwmError.ERROR_UNKNOWN, "unexpected ImpossiblePasswordPolicyException error while activating user");
            LOGGER.warn(pwmSession, info, e);
            throw new PwmOperationalException(info);
        }
    }

    public static void validateParamsAgainstLDAP(
            final Map<FormConfiguration, String> formValues,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity,
            final Configuration config
    )
            throws ChaiUnavailableException, PwmDataValidationException, PwmUnrecoverableException
    {
        final String searchFilter = config.readSettingAsString(PwmSetting.ACTIVATE_USER_SEARCH_FILTER);
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
                        LOGGER.debug(pwmSession, errorInfo.toDebugStr());
                        throw new PwmDataValidationException(errorInfo);
                    }
                    LOGGER.trace(pwmSession, "successful validation of ldap value for '" + attrName + "'");
                } catch (ChaiOperationException e) {
                    LOGGER.error(pwmSession, "error during param validation of '" + attrName + "', error: " + e.getMessage());
                    throw new PwmDataValidationException(new ErrorInformation(PwmError.ERROR_ACTIVATION_VALIDATION_FAILED, "ldap error testing value for '" + attrName + "'", new String[]{attrName}));
                }
            }
        }
    }

    private void sendPostActivationNotice(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final Configuration config = pwmApplication.getConfig();
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
        final MessageSendMethod pref = MessageSendMethod.valueOf(config.readSettingAsString(PwmSetting.ACTIVATE_TOKEN_SEND_METHOD));
        final boolean success;
        switch (pref) {
            case BOTH:
                // Send both email and SMS, success if one of both succeeds
                final boolean suc1 = sendPostActivationEmail(pwmSession, pwmApplication);
                final boolean suc2 = sendPostActivationSms(pwmSession, pwmApplication);
                success = suc1 || suc2;
                break;
            case EMAILFIRST:
                // Send email first, try SMS if email is not available
                success = sendPostActivationEmail(pwmSession, pwmApplication) || sendPostActivationSms(pwmSession, pwmApplication);
                break;
            case SMSFIRST:
                // Send SMS first, try email if SMS is not available
                success = sendPostActivationSms(pwmSession, pwmApplication) || sendPostActivationEmail(pwmSession, pwmApplication);
                break;
            case SMSONLY:
                // Only try SMS
                success = sendPostActivationSms(pwmSession, pwmApplication);
                break;
            case EMAILONLY:
            default:
                // Only try email
                success = sendPostActivationEmail(pwmSession, pwmApplication);
                break;
        }
        if (!success) {
            LOGGER.warn(pwmSession, "skipping send activation message for '" + userInfoBean.getUserIdentity() + "' no email or SMS number configured");
        }
    }

    private Boolean sendPostActivationEmail(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_ACTIVATION, locale);

        if (configuredEmailSetting == null) {
            LOGGER.debug(pwmSession, "skipping send activation email for '" + userInfoBean.getUserIdentity() + "' no email configured");
            return false;
        }

        pwmApplication.getEmailQueue().submitEmail(configuredEmailSetting, pwmSession.getUserInfoBean(),
                pwmSession.getSessionManager().getUserDataReader(pwmApplication));
        return true;
    }

    private Boolean sendPostActivationSms(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication
    )
            throws PwmUnrecoverableException, ChaiUnavailableException {
        final Configuration config = pwmApplication.getConfig();
        final UserDataReader userDataReader = pwmSession.getSessionManager().getUserDataReader(pwmApplication);
        final Locale locale = pwmSession.getSessionStateBean().getLocale();

        String senderId = config.readSettingAsString(PwmSetting.SMS_SENDER_ID);
        if (senderId == null) { senderId = ""; }
        final String message = config.readSettingAsLocalizedString(PwmSetting.SMS_ACTIVATION_TEXT, locale);

        final String toSmsNumber;
        try {
            toSmsNumber = userDataReader.readStringAttribute(config.readSettingAsString(PwmSetting.SMS_USER_PHONE_ATTRIBUTE));
        } catch (Exception e) {
            LOGGER.debug("error reading SMS attribute from user '" + pwmSession.getUserInfoBean().getUserIdentity() + "': " + e.getMessage());
            return false;
        }

        if (toSmsNumber == null || toSmsNumber.length() < 1) {
            LOGGER.debug(pwmSession, "skipping send activation SMS for '" + pwmSession.getUserInfoBean().getUserIdentity() + "' no SMS number configured");
            return false;
        }

        final Integer maxlen = ((Long) config.readSettingAsLong(PwmSetting.SMS_MAX_TEXT_LENGTH)).intValue();
        final SmsItemBean smsItem = new SmsItemBean(toSmsNumber, senderId, message, maxlen);
        pwmApplication.sendSmsUsingQueue(smsItem, null, userDataReader);
        return true;
    }

    public static void initializeToken(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final Locale locale,
            final UserIdentity userIdentity

    )
            throws PwmUnrecoverableException, PwmOperationalException, ChaiUnavailableException {
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
                LOGGER.error(pwmSession, errorInformation);
                throw new PwmOperationalException(errorInformation);
            }

            final String toSmsNumber;
            try {
                toSmsNumber = dataReader.readStringAttribute(config.readSettingAsString(PwmSetting.SMS_USER_PHONE_ATTRIBUTE));
            } catch (Exception e) {
                final String errorMsg = "unable to read user SMS attribute due to ldap error, unable to send token: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_ACTIVATION_FAILURE, errorMsg);
                LOGGER.error(pwmSession, errorInformation);
                throw new PwmOperationalException(errorInformation);
            }
            inputTokenDestData = new RestTokenDataClient.TokenDestinationData(toAddress,toSmsNumber,null);
        }

        final RestTokenDataClient restTokenDataClient = new RestTokenDataClient(pwmApplication);
        final RestTokenDataClient.TokenDestinationData outputDestTokenData = restTokenDataClient.figureDestTokenDisplayString(
                pwmSession,
                inputTokenDestData,
                activateUserBean.getUserIdentity(),
                pwmSession.getSessionStateBean().getLocale());

        final Set<String> destinationValues = new HashSet<String>();
        if (outputDestTokenData.getEmail() != null) {
            destinationValues.add(outputDestTokenData.getEmail());
        }
        if (outputDestTokenData.getSms() != null) {
            destinationValues.add(outputDestTokenData.getSms());
        }


        final Map<String,String> tokenMapData = new HashMap<String, String>();

        try {
            final Date userLastPasswordChange = PasswordUtility.determinePwdLastModified(
                    pwmApplication,
                    pwmSession,
                    activateUserBean.getUserIdentity());
            if (userLastPasswordChange != null) {
                tokenMapData.put(PwmConstants.TOKEN_KEY_PWD_CHG_DATE, PwmConstants.DEFAULT_DATETIME_FORMAT.format(userLastPasswordChange));
            }
        } catch (ChaiUnavailableException e) {
            LOGGER.error(pwmSession, "unexpected error reading user's last password change time");
        }

        final String tokenKey;
        final TokenPayload tokenPayload;
        try {
            tokenPayload = pwmApplication.getTokenService().createTokenPayload(TOKEN_NAME, tokenMapData, userIdentity, destinationValues);
            tokenKey = pwmApplication.getTokenService().generateNewToken(tokenPayload, pwmSession);
            LOGGER.debug(pwmSession, "generated activate user tokenKey code for session");
        } catch (PwmOperationalException e) {
            throw new PwmUnrecoverableException(e.getErrorInformation());
        }

        sendToken(pwmApplication, userIdentity, locale, outputDestTokenData.getEmail(), outputDestTokenData.getSms(), tokenKey);
        activateUserBean.setTokenDisplayText(outputDestTokenData.getDisplayValue());
        activateUserBean.setTokenIssued(true);
    }

    private void handleEnterTokenCode(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final ActivateUserBean activateUserBean = pwmSession.getActivateUserBean();
        final String userEnteredCode = Validator.readStringFromRequest(req, PwmConstants.PARAM_TOKEN);

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
            LOGGER.debug(pwmSession, errorInformation.toDebugStr());
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
        }

        this.advanceToNextStage(req, resp);
    }

    private static void sendToken(
            final PwmApplication pwmApplication,
            final UserIdentity theUser,
            final Locale userLocale,
            final String toAddress,
            final String toSmsNumber,
            final String tokenKey
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final Configuration config = pwmApplication.getConfig();
        final MessageSendMethod pref = MessageSendMethod.valueOf(config.readSettingAsString(PwmSetting.ACTIVATE_TOKEN_SEND_METHOD));
        final EmailItemBean emailItemBean = config.readSettingAsEmail(PwmSetting.EMAIL_ACTIVATION_VERIFICATION, userLocale);
        final String smsMessage = config.readSettingAsLocalizedString(PwmSetting.SMS_ACTIVATION_VERIFICATION_TEXT, userLocale);
        final UserDataReader userDataReader = LdapUserDataReader.appProxiedReader(pwmApplication, theUser);

        Helper.TokenSender.sendToken(
                pwmApplication,
                null,
                userDataReader,
                emailItemBean,
                pref,
                toAddress,
                toSmsNumber,
                smsMessage,
                tokenKey
        );
    }
}
