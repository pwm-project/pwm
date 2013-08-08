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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.*;
import password.pwm.bean.*;
import password.pwm.bean.servlet.NewUserBean;
import password.pwm.config.ActionConfiguration;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.event.AuditEvent;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.i18n.Message;
import password.pwm.util.*;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.operations.UserAuthenticator;
import password.pwm.util.operations.UserDataReader;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.RestCheckPasswordServer;

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
public class NewUserServlet extends TopServlet {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(NewUserServlet.class);

    private static final String FIELD_PASSWORD = "password1";
    private static final String FIELD_PASSWORD_CONFIRM = "password2";

    private static final String TOKEN_NAME = NewUserServlet.class.getName();

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        final String processAction = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);
        final Configuration config = pwmApplication.getConfig();

        if (!config.readSettingAsBoolean(PwmSetting.NEWUSER_ENABLE)) {
            ssBean.setSessionError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (pwmSession.getSessionStateBean().isAuthenticated()) {
            ssBean.setSessionError(PwmError.ERROR_USERAUTHENTICATED.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        // try to read the new user policy to make sure it's readable, that way an exception is thrown here instead of by the jsp
        pwmApplication.getConfig().getNewUserPasswordPolicy(pwmApplication, pwmSession.getSessionStateBean().getLocale());

        final List<HealthRecord> healthIssues = checkConfiguration(config,ssBean.getLocale());
        if (healthIssues != null && !healthIssues.isEmpty()) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,healthIssues.get(0).getDetail(pwmSession.getSessionStateBean().getLocale(),pwmApplication.getConfig()));
            throw new PwmUnrecoverableException(errorInformation);
        }

        // convert a url command like /pwm/public/NewUserServlet/12321321 to redirect with a process action.
        if (processAction == null || processAction.length() < 1) {
            if (convertURLtokenCommand(req, resp, pwmSession)) {
                return;
            }
        }

        if (processAction != null && processAction.length() > 1) {
            Validator.validatePwmFormID(req);
            if ("create".equalsIgnoreCase(processAction)) {
                handleCreateRequest(req,resp);
            } else if ("validate".equalsIgnoreCase(processAction)) {
                restValidateForm(req, resp);
                return;
            } else if ("enterCode".equalsIgnoreCase(processAction)) {
                handleEnterCodeRequest(req, resp);
            } else if ("doCreate".equalsIgnoreCase(processAction)) {
                handleDoCreateRequest(req, resp);
            } else if ("reset".equalsIgnoreCase(processAction)) {
                pwmSession.clearUserBean(NewUserBean.class);
                advancedToNextStage(req,resp);
            } else if ("agree".equalsIgnoreCase(processAction)) {         // accept password change agreement
                LOGGER.debug(pwmSession, "user accepted newuser agreement");
                final NewUserBean newUserBean = pwmSession.getNewUserBean();
                newUserBean.setAgreementPassed(true);
                advancedToNextStage(req,resp);
            }
            return;
        }

        this.advancedToNextStage(req,resp);
    }

    private void advancedToNextStage(final HttpServletRequest req, final HttpServletResponse resp)
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final Configuration config = pwmApplication.getConfig();
        final NewUserBean newUserBean = pwmSession.getNewUserBean();

        if (newUserBean.getFormData() == null) {
            this.forwardToJSP(req,resp);
            return;
        }

        try {
            verifyFormAttributes(newUserBean.getFormData(), pwmSession, pwmApplication);
        } catch (PwmOperationalException e) {
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            LOGGER.error(pwmSession, e.getErrorInformation().toDebugStr());
            this.forwardToJSP(req,resp);
            return;
        }

        if (config.readSettingAsBoolean(PwmSetting.NEWUSER_EMAIL_VERIFICATION)) {
            if (!newUserBean.isEmailTokenIssued()) {
                initializeToken(pwmSession, pwmApplication, "EMAIL");
            }

            if (!newUserBean.isEmailTokenPassed()) {
                this.forwardToEnterCodeJSP(req,resp);
                return;
            }
        }

        if (config.readSettingAsBoolean(PwmSetting.NEWUSER_SMS_VERIFICATION)) {
            if (!newUserBean.isSmsTokenIssued()) {
                initializeToken(pwmSession, pwmApplication, "SMS");
            }

            if (!newUserBean.isSmsTokenPassed()) {
                this.forwardToEnterCodeJSP(req,resp);
                return;
            }
        }

        final String newUserAgreementText = config.readSettingAsLocalizedString(PwmSetting.NEWUSER_AGREEMENT_MESSAGE, pwmSession.getSessionStateBean().getLocale());
        if (newUserAgreementText != null && newUserAgreementText.length() > 0) {
            if (!newUserBean.isAgreementPassed()) {
                this.forwardToAgreementJSP(req,resp);
                return;
            }
        }

        newUserBean.setAllPassed(true);
        this.forwardToWaitJSP(req,resp);
    }

    /**
     * Handle requests for ajax feedback of user supplied responses.
     *
     * @param req          HttpRequest
     * @param resp         HttpResponse
     * @throws IOException              for an IO error
     * @throws ServletException         for an http servlet error
     * @throws password.pwm.error.PwmUnrecoverableException             for any unexpected error
     * @throws ChaiUnavailableException if the ldap directory becomes unavailable
     */
    protected static void restValidateForm(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final Map<String,String> userValues = readResponsesFromJsonRequest(req);

        try {
            verifyFormAttributes(userValues, pwmSession, pwmApplication);
            { // no form errors, so check the password
                final UserInfoBean uiBean = new UserInfoBean();
                uiBean.setCachedPasswordRuleAttributes(userValues);
                uiBean.setPasswordPolicy(pwmApplication.getConfig().getNewUserPasswordPolicy(pwmApplication,locale));
                PasswordUtility.PasswordCheckInfo passwordCheckInfo = PasswordUtility.checkEnteredPassword(
                        pwmApplication,
                        pwmSession.getSessionStateBean().getLocale(),
                        null,
                        uiBean,
                        userValues.get("password1"),
                        userValues.get("password2"),
                        pwmSession.getSessionManager()
                );
                if (passwordCheckInfo.isPassed() && passwordCheckInfo.getMatch() == PasswordUtility.PasswordCheckInfo.MATCH_STATUS.MATCH) {
                    passwordCheckInfo = new PasswordUtility.PasswordCheckInfo(
                            Message.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(),Message.SUCCESS_NEWUSER_FORM,pwmApplication.getConfig()),
                            passwordCheckInfo.isPassed(),
                            passwordCheckInfo.getStrength(),
                            passwordCheckInfo.getMatch(),
                            passwordCheckInfo.getErrorCode()
                    );
                }
                final RestCheckPasswordServer.JsonData jsonData = RestCheckPasswordServer.JsonData.fromPasswordCheckInfo(passwordCheckInfo);
                final RestResultBean restResultBean = new RestResultBean();
                restResultBean.setData(jsonData);
                ServletHelper.outputJsonResult(resp,restResultBean);
            }
        } catch (PwmOperationalException e) {
            final RestResultBean restResultBean = RestResultBean.fromErrorInformation(e.getErrorInformation(),pwmApplication,pwmSession);
            ServletHelper.outputJsonResult(resp,restResultBean);
        }
    }

    private void handleEnterCodeRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        final String userSuppliedTokenKey = Validator.readStringFromRequest(req, PwmConstants.PARAM_TOKEN);

        final TokenManager.TokenPayload tokenPayload;
        try {
            tokenPayload = pwmApplication.getTokenManager().retrieveTokenData(userSuppliedTokenKey);
            if (tokenPayload != null && !TOKEN_NAME.equals(tokenPayload.getName())) {
                throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,"incorrect token name/type"));
            }
        } catch (PwmOperationalException e) {
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            this.forwardToEnterCodeJSP(req, resp);
            return;
        }

        if (tokenPayload != null) {  // success
            final Map<String,String> formData = tokenPayload.getPayloadData();
            final NewUserBean newUserBean = pwmSession.getNewUserBean();
            newUserBean.setFormData(formData);

            if (newUserBean.getVerificationPhase() == NewUserBean.NewUserVerificationPhase.EMAIL) {
                LOGGER.debug("Email token passed");
                newUserBean.setEmailTokenIssued(true);
                newUserBean.setEmailTokenPassed(true);
                newUserBean.setVerificationPhase(NewUserBean.NewUserVerificationPhase.NONE);
            }
            if (newUserBean.getVerificationPhase() == NewUserBean.NewUserVerificationPhase.SMS) {
                LOGGER.debug("SMS token passed");
                newUserBean.setSmsTokenIssued(true);
                newUserBean.setSmsTokenPassed(true);
                newUserBean.setVerificationPhase(NewUserBean.NewUserVerificationPhase.NONE);
            }

            pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_PASSED);
            LOGGER.debug(pwmSession, "token validation has been passed");
            this.advancedToNextStage(req,resp);
            return;
        }

        LOGGER.debug(pwmSession, "token validation has failed");
        pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT));
        pwmApplication.getIntruderManager().mark(null,null,pwmSession);
        this.forwardToEnterCodeJSP(req, resp);
    }


    private void handleCreateRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        pwmSession.clearUserBean(NewUserBean.class);
        final NewUserBean newUserBean = pwmSession.getNewUserBean();

        final Map<String, String> formValues = Validator.readRequestParametersAsMap(req);

        newUserBean.setFormData(formValues);
        this.advancedToNextStage(req,resp);
    }

    private void handleDoCreateRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final Configuration config = pwmApplication.getConfig();
        final NewUserBean newUserBean = pwmSession.getNewUserBean();

        if (!newUserBean.isAllPassed()) {
            this.advancedToNextStage(req,resp);
            return;
        }

        // get form data
        final Map<String, String> formValues = newUserBean.getFormData();

        // get new user DN
        final String newUserDN = determineUserDN(formValues, config);

        try {
            createUser(formValues, pwmApplication, newUserDN, pwmSession);
            pwmSession.getSessionStateBean().setSessionSuccess(Message.SUCCESS_CREATE_USER, null);
            ServletHelper.forwardToSuccessPage(req,resp);
        } catch (PwmOperationalException e) {
            if (config.readSettingAsBoolean(PwmSetting.NEWUSER_DELETE_ON_FAIL)) {
                deleteUserAccount(newUserDN, pwmSession, pwmApplication);
            }
            LOGGER.error(pwmSession, e.getErrorInformation().toDebugStr());
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            this.forwardToJSP(req, resp);
        }
    }


    private static void createUser(
            final Map<String,String> formValues,
            final PwmApplication pwmApplication,
            final String newUserDN,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        final long startTime = System.currentTimeMillis();
        LOGGER.debug(pwmSession,"beginning createUser process for " + newUserDN);

        // re-perform verification before proceeding
        verifyFormAttributes(formValues, pwmSession, pwmApplication);

        final List<FormConfiguration> newUserForm = pwmApplication.getConfig().readSettingAsForm(PwmSetting.NEWUSER_FORM);
        final String userPassword = formValues.get(FIELD_PASSWORD);

        // set up the user creation attributes
        final Map<String,String> createAttributes = new HashMap<String,String>();
        for (final FormConfiguration formItem : newUserForm) {
            final String attributeName = formItem.getName();
            createAttributes.put(attributeName, formValues.get(attributeName));
        }

        // read the creation object classes from configuration
        final Set<String> createObjectClasses = new HashSet<String>(pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.DEFAULT_OBJECT_CLASSES));

        try { // create the ldap entry
            pwmApplication.getProxyChaiProvider().createEntry(newUserDN, createObjectClasses, createAttributes);

            LOGGER.info(pwmSession, "created user entry: " + newUserDN);
        } catch (ChaiOperationException e) {
            final String userMessage = "unexpected ldap error creating user entry: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,userMessage);
            throw new PwmOperationalException(errorInformation);
        }

        final ChaiUser theUser = ChaiFactory.createChaiUser(newUserDN, pwmApplication.getProxyChaiProvider());

        try { //set password
            theUser.setPassword(userPassword);
            LOGGER.debug(pwmSession, "set user requested password for new user entry: " + newUserDN);
        } catch (ChaiOperationException e) {
            final String userMessage = "unexpected ldap error setting password for new user entry: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,userMessage);
            throw new PwmOperationalException(errorInformation);
        }

        // add AD-specific attributes
        final ChaiUser proxiedUser = ChaiFactory.createChaiUser(newUserDN, pwmApplication.getProxyChaiProvider());
        if (ChaiProvider.DIRECTORY_VENDOR.MICROSOFT_ACTIVE_DIRECTORY == pwmApplication.getProxyChaiProvider().getDirectoryVendor()) {
            try {
                proxiedUser.writeStringAttribute("userAccountControl", "512");
            } catch (ChaiOperationException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }

        LOGGER.trace(pwmSession, "new user creation process complete, now authenticating user");

        //authenticate the user to pwm
        UserAuthenticator.authenticateUser(theUser.getEntryDN(), userPassword, null, pwmSession, pwmApplication, true);

        {  // execute configured actions
            LOGGER.debug(pwmSession, "executing configured actions to user " + theUser.getEntryDN());
            final List<ActionConfiguration> actions = pwmApplication.getConfig().readSettingAsAction(PwmSetting.NEWUSER_WRITE_ATTRIBUTES);
            final ActionExecutor.ActionExecutorSettings settings = new ActionExecutor.ActionExecutorSettings();
            settings.setExpandPwmMacros(true);
            settings.setUserInfoBean(pwmSession.getUserInfoBean());
            settings.setUser(theUser);
            final ActionExecutor actionExecutor = new ActionExecutor(pwmApplication);
            actionExecutor.executeActions(actions, settings, pwmSession);
        }

        //everything good so forward to change password page.
        sendNewUserEmailConfirmation(pwmSession, new UserDataReader(proxiedUser), pwmApplication);

        // add audit record
        pwmApplication.getAuditManager().submitAuditRecord(AuditEvent.CREATE_USER, pwmSession.getUserInfoBean(), pwmSession);

        // increment the new user creation statistics
        pwmApplication.getStatisticsManager().incrementValue(Statistic.NEW_USERS);

        // be sure minimum wait time has passed
        final long minWaitTime = pwmApplication.getConfig().readSettingAsLong(PwmSetting.NEWUSER_MINIMUM_WAIT_TIME) * 1000L;
        if ((System.currentTimeMillis() - startTime) < minWaitTime) {
            LOGGER.trace(pwmSession, "waiting for minimum new user create time (" + minWaitTime + "ms)...");
            while ((System.currentTimeMillis() - startTime) < minWaitTime) {
                Helper.pause(500);
            }
        }
    }

    private static void verifyFormAttributes(
            final Map<String, String> userValues,
            final PwmSession pwmSession,
            final PwmApplication pwmApplication
    )
            throws PwmOperationalException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final Configuration config = pwmApplication.getConfig();
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();

        final List<FormConfiguration> newUserForm = config.readSettingAsForm(PwmSetting.NEWUSER_FORM);
        final Map<FormConfiguration, String> formValues = Validator.readFormValuesFromMap(userValues, newUserForm,userLocale);

        // see if the values meet form requirements.
        Validator.validateParmValuesMeetRequirements(formValues, userLocale);

        // check unique fields against ldap
        try {
            Validator.validateAttributeUniqueness(
                    pwmApplication,
                    pwmApplication.getProxyChaiProvider(),
                    formValues,
                    userLocale,
                    pwmSession.getSessionManager(),
                    Collections.<String>emptyList()
            );
        } catch (ChaiOperationException e) {
            final String userMessage = "unexpected ldap error checking attributes value uniqueness: " + e.getMessage();
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,userMessage));
        }
    }

    private static void deleteUserAccount(final String userDN, PwmSession pwmSession, final PwmApplication pwmApplication)
            throws PwmUnrecoverableException
    {
        try {
            LOGGER.warn(pwmSession, "deleting ldap user account " + userDN);
            pwmApplication.getProxyChaiProvider().deleteEntry(userDN);
            LOGGER.warn(pwmSession, "ldap user account " + userDN + " has been deleted");
        } catch (ChaiUnavailableException e) {
            LOGGER.error(pwmSession, "error deleting ldap user account " + userDN + ", " + e.getMessage());
        } catch (ChaiOperationException e) {
            LOGGER.error(pwmSession, "error deleting ldap user account " + userDN + ", " + e.getMessage());
        }

        pwmSession.unauthenticateUser();
    }

    private static String determineUserDN(final Map<String, String> formValues, final Configuration config)
            throws PwmUnrecoverableException
    {
        final String namingAttribute = config.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
        String rdnValue;

        final String newUserContextDN = config.readSettingAsString(PwmSetting.NEWUSER_CONTEXT);

        if (isUsernameRandomGenerated(config)) {
            final String randUsernameChars = config.readSettingAsString(PwmSetting.NEWUSER_USERNAME_CHARS);
            final int randUsernameLength = (int)config.readSettingAsLong(PwmSetting.NEWUSER_USERNAME_LENGTH);
            final PwmRandom RANDOM = PwmRandom.getInstance();

            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < randUsernameLength; i++) {
                sb.append(randUsernameChars.charAt(RANDOM.nextInt(randUsernameChars.length())));
            }

            rdnValue = sb.toString();
        } else {
            rdnValue = formValues.get(namingAttribute);
        }

        if (rdnValue == null || rdnValue.length() == 0) {
            final String errorMsg = "unable to determine new user DN due to missing form value for naming attribute '" + namingAttribute + '"';
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg));
        }

        return namingAttribute + "=" + rdnValue + "," + newUserContextDN;
    }

    private static void sendNewUserEmailConfirmation(
            final PwmSession pwmSession,
            final UserDataReader userDataReader,
            final PwmApplication pwmApplication
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_NEWUSER, locale);


        final String toAddress = userInfoBean.getUserEmailAddress();

        if (toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send new user email for '" + userInfoBean.getUserDN() + "' no email configured");
            return;
        }

        pwmApplication.sendEmailUsingQueue(new EmailItemBean(
                toAddress,
                configuredEmailSetting.getFrom(),
                configuredEmailSetting.getSubject(),
                configuredEmailSetting.getBodyPlain(),
                configuredEmailSetting.getBodyHtml()
        ), pwmSession.getUserInfoBean(), userDataReader);
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_NEW_USER).forward(req, resp);
    }

    private void forwardToAgreementJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_NEW_USER_AGREEMENT).forward(req, resp);
    }

    private void forwardToEnterCodeJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_NEW_USER_ENTER_CODE).forward(req, resp);
    }

    private void forwardToWaitJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException {

        final StringBuilder returnURL = new StringBuilder();
        returnURL.append(req.getContextPath());
        returnURL.append(req.getServletPath());
        returnURL.append("?" + PwmConstants.PARAM_ACTION_REQUEST + "=" + "doCreate");
        returnURL.append("&" + PwmConstants.PARAM_FORM_ID + "=").append(Helper.buildPwmFormID(PwmSession.getPwmSession(req).getSessionStateBean()));
        final String rewrittenURL = SessionFilter.rewriteURL(returnURL.toString(), req, resp);
        req.setAttribute("nextURL",rewrittenURL );
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_NEW_USER_WAIT).forward(req, resp);
    }


    public static List<HealthRecord> checkConfiguration(final Configuration configuration, final Locale locale)
    {
        final List<HealthRecord> returnRecords = new ArrayList<HealthRecord>();

        final String ldapNamingattribute = configuration.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
        final List<FormConfiguration> formItems = configuration.readSettingAsForm(PwmSetting.NEWUSER_FORM);

        boolean usernameIsGenerated = isUsernameRandomGenerated(configuration);

        {
            boolean namingIsInForm = false;
            boolean namingIsUnique = false;
            for (final FormConfiguration formItem : formItems) {
                if (ldapNamingattribute.equalsIgnoreCase(formItem.getName())) {
                    namingIsInForm = true;
                    if (formItem.isUnique()) {
                        namingIsUnique = true;
                    }
                }
            }

            if (!namingIsInForm && !usernameIsGenerated) {
                final StringBuilder errorMsg = new StringBuilder();
                errorMsg.append(PwmSetting.NEWUSER_FORM.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE));
                errorMsg.append(" -> ");
                errorMsg.append(PwmSetting.NEWUSER_FORM.getLabel(PwmConstants.DEFAULT_LOCALE));
                errorMsg.append(" does not contain the ldap naming attribute '").append(ldapNamingattribute).append("'");
                errorMsg.append(", but is required because it is the ldap naming attribute and the username is not being automatically generated.");
                returnRecords.add(new HealthRecord(HealthStatus.WARN,"Configuration",errorMsg.toString()));
            }

            if (!namingIsUnique && !usernameIsGenerated) {
                final StringBuilder errorMsg = new StringBuilder();
                errorMsg.append(PwmSetting.NEWUSER_FORM.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE));
                errorMsg.append(" -> ");
                errorMsg.append(PwmSetting.NEWUSER_FORM.getLabel(PwmConstants.DEFAULT_LOCALE));
                errorMsg.append(" the ldap naming attribute '").append(ldapNamingattribute).append("'");
                errorMsg.append(", must be configured with the unique flag enabled.");
                returnRecords.add(new HealthRecord(HealthStatus.WARN,"Configuration",errorMsg.toString()));
            }
        }
        return returnRecords;
    }

    private static boolean isUsernameRandomGenerated(final Configuration configuration) {
        boolean usernameIsGenerated = false;
        {
            final String randUsernameChars = configuration.readSettingAsString(PwmSetting.NEWUSER_USERNAME_CHARS);
            final int randUsernameLength = (int)configuration.readSettingAsLong(PwmSetting.NEWUSER_USERNAME_LENGTH);
            if (randUsernameChars != null && randUsernameChars.length() > 0 && randUsernameLength > 0) {
                usernameIsGenerated = true;
            }
        }

        return usernameIsGenerated;
    }

    private static Map<String, String> readResponsesFromJsonRequest(
            final HttpServletRequest req
    )
            throws IOException
    {
        final Map<String, String> inputMap = new HashMap<String, String>();

        final String bodyString = ServletHelper.readRequestBody(req);

        final Gson gson = new Gson();
        final Map<String, String> srcMap = gson.fromJson(bodyString, new TypeToken<Map<String, String>>() {
        }.getType());

        if (srcMap == null) {
            return Collections.emptyMap();
        }

        for (final String key : srcMap.keySet()) {
            final String paramValue = srcMap.get(key);
            inputMap.put(key, paramValue);
        }

        return inputMap;
    }

    public void initializeToken(final PwmSession pwmSession, final PwmApplication pwmApplication, final String tokenPurpose)
            throws PwmUnrecoverableException {
        if (pwmApplication.getConfig().getTokenStorageMethod() == Configuration.TokenStorageMethod.STORE_LDAP) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"cannot generate new user tokens when storage type is configured as STORE_LDAP."));
        }

        final NewUserBean newUserBean = pwmSession.getNewUserBean();
        final Configuration config = pwmApplication.getConfig();
        final Map<String,String> formData = newUserBean.getFormData();

        final String tokenKey;
        try {
            final TokenManager.TokenPayload tokenPayload = new TokenManager.TokenPayload(TOKEN_NAME, formData,null);
            tokenKey = pwmApplication.getTokenManager().generateNewToken(tokenPayload);
            LOGGER.trace(pwmSession, "generated new user tokenKey code for session");
        } catch (PwmOperationalException e) {
            throw new PwmUnrecoverableException(e.getErrorInformation());
        }

        if ("SMS".equals(tokenPurpose)) {
            final String toNum = formData.get(config.readSettingAsString(PwmSetting.SMS_USER_PHONE_ATTRIBUTE));

            newUserBean.setSmsTokenIssued(true);
            newUserBean.setTokenSmsNumber(toNum);
            newUserBean.setVerificationPhase(NewUserBean.NewUserVerificationPhase.SMS);

            sendSmsToken(pwmSession, pwmApplication, tokenKey);
        } else if ("EMAIL".equals(tokenPurpose)) {
            final String toAddress = formData.get(config.readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE));

            newUserBean.setEmailTokenIssued(true);
            newUserBean.setTokenEmailAddress(toAddress);
            newUserBean.setVerificationPhase(NewUserBean.NewUserVerificationPhase.EMAIL);

            sendEmailToken(pwmSession, pwmApplication, tokenKey);
        } else {
            LOGGER.error("Unimplemented token purpose: "+tokenPurpose);
            newUserBean.setVerificationPhase(NewUserBean.NewUserVerificationPhase.NONE);
        }
    }

    private void sendEmailToken(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final String tokenKey
    )
            throws PwmUnrecoverableException {
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final Configuration config = pwmApplication.getConfig();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_NEWUSER_VERIFICATION, userLocale);

        final NewUserBean newUserBean = pwmSession.getNewUserBean();
        final String toAddress = newUserBean.getTokenEmailAddress();

        if (toAddress == null || toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send new user token email; no email address available in form");
            return;
        }

        pwmApplication.sendEmailUsingQueue(new EmailItemBean(
                toAddress,
                configuredEmailSetting.getFrom(),
                configuredEmailSetting.getSubject(),
                configuredEmailSetting.getBodyPlain().replace("%TOKEN%", tokenKey),
                configuredEmailSetting.getBodyHtml().replace("%TOKEN%", tokenKey)
        ), pwmSession.getUserInfoBean(), null);
        pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_SENT);
        LOGGER.debug(pwmSession, "token email added to send queue for " + toAddress);
    }

    private void sendSmsToken(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final String tokenKey
    )
            throws PwmUnrecoverableException {
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final Configuration config = pwmApplication.getConfig();
        String senderId = config.readSettingAsString(PwmSetting.SMS_SENDER_ID);
        if (senderId == null) { senderId = ""; }
        String message = config.readSettingAsLocalizedString(PwmSetting.SMS_NEWUSER_TOKEN_TEXT, userLocale);

        final NewUserBean newUserBean = pwmSession.getNewUserBean();
        final String toSmsNumber = newUserBean.getTokenSmsNumber();

        if (toSmsNumber == null || toSmsNumber.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send token sms; no SMS number available in form");
            return;
        }

        message = message.replace("%TOKEN%", tokenKey);

        final Integer maxlen = ((Long) config.readSettingAsLong(PwmSetting.SMS_MAX_TEXT_LENGTH)).intValue();
        pwmApplication.sendSmsUsingQueue(new SmsItemBean(toSmsNumber, senderId, message, maxlen), null, null);

        pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_SENT);
        LOGGER.debug(pwmSession, "token sms added to send queue for " + toSmsNumber);
    }

}
