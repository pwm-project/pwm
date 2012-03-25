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
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ImpossiblePasswordPolicyException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.*;
import password.pwm.bean.*;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.Message;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.util.Helper;
import password.pwm.util.PostChangePasswordAction;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.stats.Statistic;

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

    private static final String CONTEXT_PARAM_NAME = "context";

    private static final String TOKEN_NAME = ForgottenPasswordServlet.class.getName();


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
        final String processAction = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST, 255);

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
            if (convertURLtokenCommand(req, resp, pwmSession)) {
                return;
            }
        }

        if (processAction != null && processAction.length() > 0) {
            Validator.validatePwmFormID(req);
            if ("activate".equalsIgnoreCase(processAction)) {
                handleActivationRequest(req, resp);
            } else if ("enterCode".equalsIgnoreCase(processAction)) {
                handleEnterForgottenCode(req,resp);
            } else if ("agree".equalsIgnoreCase(processAction)) {         // accept password change agreement
                LOGGER.debug(pwmSession, "user accepted activate user agreement");
                activateUserBean.setAgreementPassed(true);
                advanceToNextStage(req, resp);
            }
        }

        if (!resp.isCommitted()) {
            forwardToJSP(req, resp);
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
        final List<FormConfiguration> formConfiguration = config.readSettingAsForm(PwmSetting.ACTIVATE_USER_FORM, ssBean.getLocale());

        try {
            //read the values from the request
            final Map<FormConfiguration,String> formValues = Validator.readFormValuesFromRequest(req, formConfiguration);

            // read the context attr
            final String contextParam = Validator.readStringFromRequest(req, CONTEXT_PARAM_NAME, 1024, "");

            // get an ldap user object based on the params
            final ChaiUser theUser;
            {
                final String searchFilter = figureSearchFilterForParams(formValues, pwmApplication.getConfig());
                final String searchContext = UserStatusHelper.determineContextForSearch(pwmSession, contextParam, pwmApplication.getConfig());
                theUser = performUserSearch(pwmSession, pwmApplication, searchFilter, searchContext);
            }

            // see if the values meet the configured form requirements.
            Validator.validateParmValuesMeetRequirements(pwmApplication, formValues);

            validateParamsAgainstLDAP(formValues, pwmSession, theUser, config);

            final String queryString = config.readSettingAsString(PwmSetting.ACTIVATE_USER_QUERY_MATCH);
            if (!Permission.testQueryMatch(theUser, queryString, Permission.ACTIVATE_USER.toString(), pwmSession)) {
                final String errorMsg = "user " + theUser.getEntryDN() + " attempted activation, but does not match query string";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_ACTIVATE_USER_NO_QUERY_MATCH, errorMsg);
                pwmApplication.getIntruderManager().addBadUserAttempt(theUser.getEntryDN(), pwmSession);
                pwmApplication.getIntruderManager().addBadAddressAttempt(pwmSession);
                throw new PwmOperationalException(errorInformation);
            }

            final ActivateUserBean activateUserBean = pwmSession.getActivateUserBean();
            activateUserBean.setTheUser(theUser);
            activateUserBean.setFormValidated(true);
        } catch (PwmOperationalException e) {
            pwmApplication.getIntruderManager().addBadAddressAttempt(pwmSession);
            pwmApplication.getIntruderManager().delayPenalty(null, pwmSession);
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

        if (!activateUserBean.isFormValidated() || activateUserBean.getTheUser() == null) {
            forwardToJSP(req,resp);
            return;
        }

        final String agreementMessage = config.readSettingAsLocalizedString(PwmSetting.ACTIVATE_AGREEMENT_MESSAGE,pwmSession.getSessionStateBean().getLocale());
        if (agreementMessage != null && agreementMessage.length() > 0 && !activateUserBean.isAgreementPassed()) {
            forwardToAgreementJSP(req,resp);
            return;
        }

        final boolean tokenRequired = config.readSettingAsBoolean(PwmSetting.ACTIVATE_USER_TOKEN_VERIFICATION);
        if (tokenRequired) {
            if (!activateUserBean.isTokenIssued()) {
                try {
                    initializeToken(pwmSession, pwmApplication, activateUserBean.getTheUser());
                } catch (PwmOperationalException e) {
                    pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
                    forwardToJSP(req, resp);
                    return;
                }
            }

            if (!activateUserBean.isTokenPassed()) {
                forwardToEnterCodeJSP(req,resp);
                return;
            }
        }

        try {
            activateUser(pwmSession, pwmApplication, activateUserBean.getTheUser());
            ServletHelper.forwardToSuccessPage(req, resp);
        } catch (PwmOperationalException e) {
            pwmApplication.getIntruderManager().addBadAddressAttempt(pwmSession);
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            LOGGER.debug(pwmSession, e.getErrorInformation().toDebugStr());
            pwmApplication.getIntruderManager().delayPenalty(null, pwmSession);
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
        }
    }

    public void activateUser(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final ChaiUser theUser
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        try {
            theUser.unlock();
        } catch (ChaiOperationException e) {
            final String errorMsg = "error unlocking user " + theUser.getEntryDN() + ": " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_ACTIVATION_FAILURE, errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        try {
            // write out configured attributes.
            {
                LOGGER.debug(pwmSession, "writing pre-activate user attribute write values to user " + theUser.getEntryDN());
                final Collection<String> configValues = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.ACTIVATE_USER_PRE_WRITE_ATTRIBUTES);
                final Map<String, String> writeAttributesSettings = Configuration.convertStringListToNameValuePair(configValues, "=");
                Helper.writeMapToLdap(pwmApplication, pwmSession, theUser, writeAttributesSettings, true);
            }

            //authenticate the pwm session
            AuthenticationFilter.authUserWithUnknownPassword(theUser, pwmSession, pwmApplication, true);

            // mark the event log
            UserHistory.updateUserHistory(pwmSession, pwmApplication, UserHistory.Record.Event.ACTIVATE_USER, null);

            // set the session success message
            pwmSession.getSessionStateBean().setSessionSuccess(Message.SUCCESS_ACTIVATE_USER, null);

            // update the stats bean
            pwmApplication.getStatisticsManager().incrementValue(Statistic.ACTIVATED_USERS);

            // send email or sms
            sendActivationMessage(pwmSession, pwmApplication, theUser);

            // setup post-change attributes
            final PostChangePasswordAction postAction = new PostChangePasswordAction() {

                public String getLabel() {
                    return "ActivateUser write attributes";
                }

                public boolean doAction(final PwmSession pwmSession, final String newPassword)
                        throws PwmUnrecoverableException {
                    try {
                        final ChaiUser theUser = pwmApplication.getProxyChaiUserActor(pwmSession);
                        LOGGER.debug(pwmSession, "writing post-activate user attribute write values to user " + theUser.getEntryDN());
                        final Collection<String> configValues = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.ACTIVATE_USER_POST_WRITE_ATTRIBUTES);
                        final Map<String, String> writeAttributesSettings = Configuration.convertStringListToNameValuePair(configValues, "=");
                        Helper.writeMapToLdap(pwmApplication, pwmSession, theUser, writeAttributesSettings, true);
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

    private static String figureSearchFilterForParams(
            final Map<FormConfiguration,String> formValues,
            final Configuration config
    )
            throws ChaiUnavailableException, PwmUnrecoverableException {
        String searchFilter = config.readSettingAsString(PwmSetting.ACTIVATE_USER_SEARCH_FILTER);

        for (final FormConfiguration formConfiguration : formValues.keySet()) {
            final String attrName = "%" + formConfiguration.getAttributeName() + "%";
            searchFilter = searchFilter.replaceAll(attrName, formValues.get(formConfiguration));
        }

        return searchFilter;
    }

    private static ChaiUser performUserSearch(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final String searchFilter,
            final String searchBase
    )
            throws ChaiUnavailableException, PwmOperationalException, PwmUnrecoverableException
    {
        final SearchHelper searchHelper = new SearchHelper();
        searchHelper.setMaxResults(2);
        searchHelper.setFilter(searchFilter);
        searchHelper.setAttributes("");

        final ChaiProvider chaiProvider = pwmApplication.getProxyChaiProvider();

        LOGGER.debug(pwmSession, "performing ldap search for user activation, base=" + searchBase + " filter=" + searchFilter);

        try {
            final Map<String, Map<String,String>> results = chaiProvider.search(searchBase, searchHelper);

            if (results.isEmpty()) {
                final String errorMsg = "user not found using search filter " + searchFilter + ", in " + searchBase;
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_ACTIVATION_VALIDATION_FAILED, errorMsg);
                throw new PwmOperationalException(errorInformation);
            } else if (results.size() > 1) {
                final String errorMsg = "multiple matches results for activation search";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_ACTIVATION_VALIDATION_FAILED, errorMsg);
                throw new PwmOperationalException(errorInformation);
            }

            final String userDN = results.keySet().iterator().next();
            LOGGER.debug(pwmSession, "found userDN: " + userDN);
            return ChaiFactory.createChaiUser(userDN, chaiProvider);
        } catch (ChaiOperationException e) {
            final String errorMsg = "ldap error during activation search: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_ACTIVATION_VALIDATION_FAILED, errorMsg);
            final PwmOperationalException newException = new PwmOperationalException(errorInformation);
            newException.initCause(e);
            throw newException;
        }
    }


    public static void validateParamsAgainstLDAP(
            final Map<FormConfiguration, String> formValues,
            final PwmSession pwmSession,
            final ChaiUser theUser,
            final Configuration config
    )
            throws ChaiUnavailableException, PwmDataValidationException
    {
        final String searchFilter = config.readSettingAsString(PwmSetting.ACTIVATE_USER_SEARCH_FILTER);
        for (final FormConfiguration formConfiguration : formValues.keySet()) {
            final String attrName = formConfiguration.getAttributeName();
            final String tokenizedAttrName = "%" + attrName + "%";
            if (searchFilter.contains(tokenizedAttrName)) {
                LOGGER.trace(pwmSession, "skipping validation of ldap value for '" + attrName + "' because it is in search filter");
            } else {
                final String value = formValues.get(formConfiguration);
                try {
                    if (!theUser.compareStringAttribute(attrName, value)) {
                        final String errorMsg = "incorrect value for '" + attrName + "'";
                        final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_ACTIVATION_VALIDATION_FAILED, errorMsg, attrName);
                        LOGGER.debug(pwmSession, errorInfo.toDebugStr());
                        throw new PwmDataValidationException(errorInfo);
                    }
                    LOGGER.trace(pwmSession, "successful validation of ldap value for '" + attrName + "'");
                } catch (ChaiOperationException e) {
                    LOGGER.error(pwmSession, "error during param validation of '" + attrName + "', error: " + e.getMessage());
                    throw new PwmDataValidationException(new ErrorInformation(PwmError.ERROR_ACTIVATION_VALIDATION_FAILED, "ldap error testing value for '" + attrName + "'", attrName));
                }
            }
        }
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_ACTIVATE_USER).forward(req, resp);
    }

    private void forwardToEnterCodeJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_ACTIVATE_USER_ENTER_CODE).forward(req, resp);
    }

    private void forwardToAgreementJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_ACTIVATE_USER_AGREEMENT).forward(req, resp);
    }

    private void sendActivationMessage(final PwmSession pwmSession, final PwmApplication pwmApplication, final ChaiUser theUser) throws PwmUnrecoverableException {
        final Configuration config = pwmApplication.getConfig();
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
        final PwmSetting.SmsPriority pref = PwmSetting.SmsPriority.valueOf(config.readSettingAsString(PwmSetting.ACTIVATE_TOKEN_SEND_METHOD));
        final boolean success;
        switch (pref) {
            case BOTH:
                // Send both email and SMS, success if one of both succeeds
                final boolean suc1 = sendActivationEmail(pwmSession, pwmApplication);
                final boolean suc2 = sendActivationSms(pwmSession, pwmApplication, theUser);
                success = suc1 || suc2;
                break;
            case EMAILFIRST:
                // Send email first, try SMS if email is not available
                success = sendActivationEmail(pwmSession, pwmApplication) || sendActivationSms(pwmSession, pwmApplication, theUser);
                break;
            case SMSFIRST:
                // Send SMS first, try email if SMS is not available
                success = sendActivationSms(pwmSession, pwmApplication, theUser) || sendActivationEmail(pwmSession, pwmApplication);
                break;
            case SMSONLY:
                // Only try SMS
                success = sendActivationSms(pwmSession, pwmApplication, theUser);
                break;
            case EMAILONLY:
            default:
                // Only try email
                success = sendActivationEmail(pwmSession, pwmApplication);
                break;
        }
        if (!success) {
            LOGGER.warn(pwmSession, "skipping send activation message for '" + userInfoBean.getUserDN() + "' no email or SMS number configured");
        }
    }
    
    private Boolean sendActivationEmail(final PwmSession pwmSession, final PwmApplication pwmApplication) throws PwmUnrecoverableException {
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();

        final String fromAddress = config.readSettingAsLocalizedString(PwmSetting.EMAIL_ACTIVATION_FROM, locale);
        final String subject = config.readSettingAsLocalizedString(PwmSetting.EMAIL_ACTIVATION_SUBJECT, locale);
        final String plainBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_ACTIVATION_BODY, locale);
        final String htmlBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_ACTIVATION_BODY_HTML, locale);

        final String toAddress = userInfoBean.getUserEmailAddress();

        if (toAddress == null || toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "skipping send activation email for '" + userInfoBean.getUserDN() + "' no email configured");
            return false;
        }

        final EmailItemBean emailItem = new EmailItemBean(toAddress, fromAddress, subject, plainBody, htmlBody);
        pwmApplication.sendEmailUsingQueue(emailItem, pwmSession.getUserInfoBean());
        return true;
    }

    private Boolean sendActivationSms(final PwmSession pwmSession, final PwmApplication pwmApplication, final ChaiUser theUser) throws PwmUnrecoverableException {
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();

        String senderId = config.readSettingAsString(PwmSetting.SMS_SENDER_ID);
        if (senderId == null) { senderId = ""; }
        final String message = config.readSettingAsLocalizedString(PwmSetting.SMS_ACTIVATION_TEXT, locale);

        final String toSmsNumber;
        try {
            toSmsNumber = theUser.readStringAttribute(config.readSettingAsString(PwmSetting.SMS_USER_PHONE_ATTRIBUTE));
        } catch (Exception e) {
            LOGGER.debug("error reading SMS attribute from user '" + userInfoBean.getUserDN() + "': " + e.getMessage());
            return false;
        }

        if (toSmsNumber == null || toSmsNumber.length() < 1) {
            LOGGER.debug(pwmSession, "skipping send activation SMS for '" + userInfoBean.getUserDN() + "' no SMS number configured");
            return false;
        }

        final Integer maxlen = ((Long) config.readSettingAsLong(PwmSetting.SMS_MAX_TEXT_LENGTH)).intValue();
        final SmsItemBean smsItem = new SmsItemBean(toSmsNumber, senderId, message, maxlen, locale);
        pwmApplication.sendSmsUsingQueue(smsItem, userInfoBean);
        return true;
    }

    public static void initializeToken(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final ChaiUser theUser

    )
            throws PwmUnrecoverableException, PwmOperationalException, ChaiUnavailableException {
        final ActivateUserBean activateUserBean = pwmSession.getActivateUserBean();
        final Configuration config = pwmApplication.getConfig();

        final String toAddress;
        try {
            toAddress = theUser.readStringAttribute(config.readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE));
        } catch (ChaiOperationException e) {
            final String errorMsg = "unable to read user email attribute due to ldap error, unable to send token: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_ACTIVATION_FAILURE, errorMsg);
            LOGGER.error(pwmSession, errorInformation);
            throw new PwmOperationalException(errorInformation);
        }

        final String toSmsNumber;
        try {
            toSmsNumber = theUser.readStringAttribute(config.readSettingAsString(PwmSetting.SMS_USER_PHONE_ATTRIBUTE));
        } catch (Exception e) {
            final String errorMsg = "unable to read user SMS attribute due to ldap error, unable to send token: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_ACTIVATION_FAILURE, errorMsg);
            LOGGER.error(pwmSession, errorInformation);
            throw new PwmOperationalException(errorInformation);
        }

        final String tokenKey;
        try {
            final TokenManager.TokenPayload tokenPayload = new TokenManager.TokenPayload(TOKEN_NAME,Collections.<String,String>emptyMap(),theUser.getEntryDN());
            tokenKey = pwmApplication.getTokenManager().generateNewToken(tokenPayload);
            LOGGER.debug(pwmSession, "generated activate user tokenKey code for session: " + tokenKey);
        } catch (PwmOperationalException e) {
            throw new PwmUnrecoverableException(e.getErrorInformation());
        }

        sendToken(pwmSession, pwmApplication, toAddress, toSmsNumber, tokenKey);
        activateUserBean.setTokenIssued(true);
    }

    private void handleEnterForgottenCode(final HttpServletRequest req, final HttpServletResponse resp)
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final ActivateUserBean activateUserBean = pwmSession.getActivateUserBean();

        final String userEnteredCode = Validator.readStringFromRequest(req, "code");

        boolean tokenPass = false;
        final String userDN;
        try {
            TokenManager.TokenPayload tokenPayload = pwmApplication.getTokenManager().retrieveTokenData(userEnteredCode);
            if (tokenPayload != null) {
                if (!TOKEN_NAME.equals(tokenPayload.getName()) && pwmApplication.getTokenManager().supportsName()) {
                    throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,"incorrect token/name format"));
                }
                final String dnFromToken = tokenPayload.getUserDN();
                {
                    final ChaiUser proxiedUser = activateUserBean.getTheUser();
                    if (proxiedUser == null) {
                        userDN = dnFromToken;
                        if (userDN != null) {
                            tokenPass = true;
                        }
                    } else {
                        final String proxiedUserDN = proxiedUser.getEntryDN();
                        userDN = proxiedUserDN == null ? dnFromToken : proxiedUserDN;
                        if (proxiedUserDN != null && proxiedUserDN.equals(dnFromToken)) {
                            tokenPass = true;
                        } else {
                            LOGGER.warn(pwmSession, "user in session '" + proxiedUserDN + "' entered code for user '" + dnFromToken + "', counting as invalid attempt");
                        }
                    }
                }
            } else {
                userDN = activateUserBean.getTheUser() == null ? null : activateUserBean.getTheUser().getEntryDN();
            }
        } catch (PwmOperationalException e) {
            final String errorMsg = "unexpected error attempting to read token from storage: " + e.getMessage();
            LOGGER.error(errorMsg);
            pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(e.getError(),e.getMessage()));
            this.forwardToEnterCodeJSP(req, resp);
            return;
        }

        if (tokenPass) {
            final ChaiUser proxiedUser = ChaiFactory.createChaiUser(userDN,pwmApplication.getProxyChaiProvider());
            activateUserBean.setTheUser(proxiedUser);
            activateUserBean.setTokenPassed(true);
            activateUserBean.setFormValidated(true);
            pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_PASSED);
            LOGGER.debug(pwmSession, "token validation has been passed");
            advanceToNextStage(req, resp);
            return;
        }

        LOGGER.debug(pwmSession, "token validation has failed");
        pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT));
        pwmApplication.getIntruderManager().addBadUserAttempt(userDN, pwmSession);
        pwmApplication.getIntruderManager().addBadAddressAttempt(pwmSession);
        pwmApplication.getIntruderManager().delayPenalty(null, pwmSession);
        this.forwardToEnterCodeJSP(req, resp);
    }

    private static void sendToken(final PwmSession pwmSession, final PwmApplication pwmApplication, 
            final String toAddress, final String toSmsNumber, final String tokenKey)
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        final PwmSetting.SmsPriority pref = PwmSetting.SmsPriority.valueOf(config.readSettingAsString(PwmSetting.ACTIVATE_TOKEN_SEND_METHOD));
        final boolean success;
        switch (pref) {
            case BOTH:
                // Send both email and SMS, success if one of both succeeds
                final boolean suc1 = sendEmailToken(pwmSession, pwmApplication, toAddress, tokenKey);
                final boolean suc2 = sendSmsToken(pwmSession, pwmApplication, toSmsNumber, tokenKey);
                success = suc1 || suc2;
                break;
            case EMAILFIRST:
                // Send email first, try SMS if email is not available
                success = sendEmailToken(pwmSession, pwmApplication, toAddress, tokenKey) || sendSmsToken(pwmSession, pwmApplication, toSmsNumber, tokenKey);
                break;
            case SMSFIRST:
                // Send SMS first, try email if SMS is not available
                success = sendSmsToken(pwmSession, pwmApplication, toSmsNumber, tokenKey) || sendEmailToken(pwmSession, pwmApplication, toAddress, tokenKey);
                break;
            case SMSONLY:
                // Only try SMS
                success = sendSmsToken(pwmSession, pwmApplication, toSmsNumber, tokenKey);
                break;
            case EMAILONLY:
            default:
                // Only try email
                success = sendEmailToken(pwmSession, pwmApplication, toAddress, tokenKey);
                break;
        }
        if (!success) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_TOKEN_MISSING_CONTACT));
        }
    }

    private static Boolean sendEmailToken(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final String toAddress,
            final String tokenKey
    )
            throws PwmUnrecoverableException
    {
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final Configuration config = pwmApplication.getConfig();
        final String fromAddress = config.readSettingAsLocalizedString(PwmSetting.EMAIL_ACTIVATION_VERIFICATION_FROM, userLocale);
        final String subject = config.readSettingAsLocalizedString(PwmSetting.EMAIL_ACTIVATION_VERIFICATION_SUBJECT, userLocale);
        String plainBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_ACTIVATION_VERIFICATION_BODY, userLocale);
        String htmlBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_ACTIVATION_VERIFICATION_BODY_HTML, userLocale);

        if (toAddress == null || toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send activate user token email; no email address available in form");
            return false;
        }

        plainBody = plainBody.replaceAll("%TOKEN%", tokenKey);
        htmlBody = htmlBody.replaceAll("%TOKEN%", tokenKey);

        pwmApplication.sendEmailUsingQueue(new EmailItemBean(toAddress, fromAddress, subject, plainBody, htmlBody), pwmSession.getUserInfoBean());
        pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_SENT);
        LOGGER.debug(pwmSession, "token email added to send queue for " + toAddress);
        return true;
    }

    private static Boolean sendSmsToken(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final String toSmsNumber,
            final String tokenKey
    )
            throws PwmUnrecoverableException
    {
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final Configuration config = pwmApplication.getConfig();
        String senderId = config.readSettingAsString(PwmSetting.SMS_SENDER_ID);
        if (senderId == null) { senderId = ""; }
        String message = config.readSettingAsLocalizedString(PwmSetting.SMS_ACTIVATION_VERIFICATION_TEXT, userLocale);

        if (toSmsNumber == null || toSmsNumber.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send activate user token SMS; no SMS number available in form");
            return false;
        }

        message = message.replaceAll("%TOKEN%", tokenKey);

        final Integer maxlen = ((Long) config.readSettingAsLong(PwmSetting.SMS_MAX_TEXT_LENGTH)).intValue();
        pwmApplication.sendSmsUsingQueue(new SmsItemBean(toSmsNumber, senderId, message, maxlen, userLocale), pwmSession.getUserInfoBean());
        pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_SENT);
        LOGGER.debug(pwmSession, "token SMS added to send queue for " + toSmsNumber);
        return true;
    }
}

