/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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
import password.pwm.*;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.NewUserBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.Message;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.util.*;
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

        final List<HealthRecord> healthIssues = checkConfiguration(config,ssBean.getLocale());
        if (healthIssues != null && !healthIssues.isEmpty()) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,healthIssues.get(0).getDetail());
            throw new PwmUnrecoverableException(errorInformation);
        }

        // convert a url command like /pwm/public/NewUserServlet/12321321 to redirect with a process action.
        if (processAction == null || processAction.length() < 1) {
            if (checkForURLcommand(req, resp, pwmSession)) {
                return;
            }
        }

        // populate the newuser bean with the new user password policy.
        final NewUserBean newUserBean = pwmSession.getNewUserBean();
        if (newUserBean.getPasswordPolicy() == null) {
            final PwmPasswordPolicy pwmPasswordPolicy = getNewUserPasswordPolicy(pwmApplication, pwmSession);
            newUserBean.setPasswordPolicy(pwmPasswordPolicy);
        }

        if (!newUserBean.isAgreementPassed() && config.readSettingAsLocalizedString(PwmSetting.NEWUSER_AGREEMENT_MESSAGE, ssBean.getLocale()).length() > 0 ) {
            if (!"agree".equalsIgnoreCase(processAction)) {
                this.forwardToAgreementJSP(req,resp);
                return;
            }
        }

        if (processAction != null && processAction.length() > 1) {
            Validator.validatePwmFormID(req);
            if ("create".equalsIgnoreCase(processAction)) {
                handleCreateRequest(req,resp);
            } else if ("validate".equalsIgnoreCase(processAction)) {
                handleValidateForm(req, resp);
            } else if ("enterCode".equalsIgnoreCase(processAction)) {
                handleEnterCodeRequest(req, resp);
            } else if ("doCreate".equalsIgnoreCase(processAction)) {
                handleDoCreateRequest(req, resp);
            } else if ("agree".equalsIgnoreCase(processAction)) {         // accept password change agreement
                LOGGER.debug(pwmSession, "user accepted newuser agreement");
                newUserBean.setAgreementPassed(true);
                this.forwardToJSP(req,resp);
            }
            return;
        }

        this.forwardToJSP(req, resp);
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
    protected static void handleValidateForm(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        boolean success = true;
        String userMessage = Message.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(), Message.SUCCESS_NEWUSER_FORM, pwmApplication.getConfig());

        final Map<String,String> userValues = readResponsesFromJsonRequest(req);

        try {
            verifyFormAttributes(userValues, pwmSession, pwmApplication);
        } catch (PwmOperationalException e) {
            success = false;
            userMessage = e.getErrorInformation().toUserStr(pwmSession, pwmApplication);
        }

        final Map<String, String> outputMap = new HashMap<String, String>();
        outputMap.put("version", "1");
        outputMap.put("message", userMessage);
        outputMap.put("success", String.valueOf(success));

        final Gson gson = new Gson();
        final String output = gson.toJson(outputMap);

        resp.setContentType("text/plain;charset=utf-8");
        resp.getWriter().print(output);

        LOGGER.trace(pwmSession, "ajax validate responses: " + output);
    }

    private void handleEnterCodeRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        final String userSuppliedTokenKey = Validator.readStringFromRequest(req, "code");

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

        if (tokenPayload != null) {
            final Map<String,String> formData;
            formData = tokenPayload.getPayloadData();
            pwmSession.getNewUserBean().setFormData(formData);
            pwmSession.getNewUserBean().setTokenIssued(true);

            pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_PASSED);
            LOGGER.debug(pwmSession, "token validation has been passed");

            this.forwardToWaitJSP(req, resp);
            return;
        }

        LOGGER.debug(pwmSession, "token validation has failed");
        pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT));
        pwmApplication.getIntruderManager().addBadAddressAttempt(pwmSession);
        Helper.pause(PwmRandom.getInstance().nextInt(2 * 1000) + 1000); // delay penalty of 1-3 seconds
        this.forwardToEnterCodeJSP(req, resp);
    }

    private void handleCreateRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final Configuration config = pwmApplication.getConfig();

        final Map<String, String> formValues = Validator.readRequestParametersAsMap(req);

        final NewUserBean newUserBean = pwmSession.getNewUserBean();

        if (config.readSettingAsBoolean(PwmSetting.NEWUSER_EMAIL_VERIFICATION) && !newUserBean.isTokenIssued()) {
            initializeToken(pwmSession, pwmApplication, formValues);
            this.forwardToEnterCodeJSP(req,resp);
        } else {
            newUserBean.setFormData(formValues);
            this.forwardToWaitJSP(req, resp);
        }
    }

    private void handleDoCreateRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final Configuration config = pwmApplication.getConfig();

        final NewUserBean newUserBean = pwmSession.getNewUserBean();

        newUserBean.setTokenIssued(false);
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

        // re-perform verification before proceeding
        verifyFormAttributes(formValues, pwmSession, pwmApplication);

        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final List<FormConfiguration> newUserForm = pwmApplication.getConfig().readSettingAsForm(PwmSetting.NEWUSER_FORM, ssBean.getLocale());
        final String userPassword = formValues.get(FIELD_PASSWORD);

        // set up the user creation attributes
        final Map<String,String> createAttributes = new HashMap<String,String>();
        for (final FormConfiguration formConfiguration : newUserForm) {
            final String attributeName = formConfiguration.getAttributeName();
            createAttributes.put(attributeName, formValues.get(attributeName));
        }

        // read the creation object classes from configuration
        final Set<String> createObjectClasses = new HashSet<String>(pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.DEFAULT_OBJECT_CLASSES));

        try { // create the ldap entry
            pwmApplication.getProxyChaiProvider().createEntry(newUserDN, createObjectClasses, createAttributes);

            LOGGER.info(pwmSession, "created user entry: " + newUserDN);
        } catch (ChaiOperationException e) {
            final String userMessage = "unexpected ldap error creating user entry: " + e.getMessage();
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,userMessage));
        }

        final ChaiUser theUser = ChaiFactory.createChaiUser(newUserDN, pwmApplication.getProxyChaiProvider());
        final String temporaryPassword = RandomPasswordGenerator.createRandomPassword(
                pwmSession,
                pwmSession.getNewUserBean().getPasswordPolicy(),
                pwmApplication.getSeedlistManager(),
                pwmApplication
        );

        try { //set password
            theUser.setPassword(temporaryPassword);

            LOGGER.debug(pwmSession, "set temporary password for new user entry: " + newUserDN);
        } catch (ChaiOperationException e) {
            final String userMessage = "unexpected ldap error setting password for new user entry: " + e.getMessage();
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,userMessage));
        }


        {  // write out configured attributes.
            LOGGER.debug(pwmSession, "writing newUser.writeAttributes to user " + theUser.getEntryDN());
            final List<String> configValues = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.NEWUSER_WRITE_ATTRIBUTES);
            final Map<String, String> configNameValuePairs = Configuration.convertStringListToNameValuePair(configValues, "=");
            Helper.writeMapToLdap(pwmSession, theUser, configNameValuePairs);
        }

        LOGGER.trace(pwmSession, "new user creation process complete, now authenticating user to PWM using temporary password");

        //authenticate the user to pwm
        AuthenticationFilter.authenticateUser(theUser.getEntryDN(), temporaryPassword, null, pwmSession, pwmApplication, true);

        //set user requested password
        PasswordUtility.setUserPassword(pwmSession, pwmApplication, userPassword);

        //everything good so forward to change password page.
        sendNewUserEmailConfirmation(pwmSession, pwmApplication);

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

        final List<FormConfiguration> newUserForm = config.readSettingAsForm(PwmSetting.NEWUSER_FORM, userLocale);

        final Map<FormConfiguration, String> formValues = Validator.readFormValuesFromMap(userValues, newUserForm);

        // check unique fields against ldap
        try {
            Validator.validateAttributeUniqueness(pwmApplication.getProxyChaiProvider(), config, formValues, config.readSettingAsStringArray(PwmSetting.NEWUSER_UNIQUE_ATTRIBUES));
        } catch (ChaiOperationException e) {
            final String userMessage = "unexpected ldap error checking attributes value uniqueness: " + e.getMessage();
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,userMessage));
        }

        // see if the values meet form requirements.
        Validator.validateParmValuesMeetRequirements(formValues);

        // test the password
        final String password = userValues.get(FIELD_PASSWORD);
        final String passwordConfirm = userValues.get(FIELD_PASSWORD_CONFIRM);

        if (password == null || password.length() <1 ) {
            throw new PwmOperationalException(PwmError.PASSWORD_MISSING);
        }

        final PwmPasswordPolicy passwordPolicy = pwmSession.getNewUserBean().getPasswordPolicy();

        Validator.testPasswordAgainstPolicy(password, null, pwmSession, pwmApplication, passwordPolicy, false);

        if (passwordConfirm == null || passwordConfirm.length() <1 ) {
            throw new PwmOperationalException(PwmError.PASSWORD_MISSING_CONFIRM);
        }

        if (!password.equals(passwordConfirm)) {
            throw new PwmOperationalException(PwmError.PASSWORD_DOESNOTMATCH);
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

    private static void sendNewUserEmailConfirmation(final PwmSession pwmSession, final PwmApplication pwmApplication) throws PwmUnrecoverableException {
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();

        final String fromAddress = config.readSettingAsLocalizedString(PwmSetting.EMAIL_NEWUSER_FROM, locale);
        final String subject = config.readSettingAsLocalizedString(PwmSetting.EMAIL_NEWUSER_SUBJECT, locale);
        final String plainBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_NEWUSER_BODY, locale);
        final String htmlBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_NEWUSER_BODY_HTML, locale);

        final String toAddress = userInfoBean.getUserEmailAddress();

        if (toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send new user email for '" + userInfoBean.getUserDN() + "' no email configured");
            return;
        }

        pwmApplication.sendEmailUsingQueue(new EmailItemBean(toAddress, fromAddress, subject, plainBody, htmlBody));
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
        final List<FormConfiguration> formConfigurations = configuration.readSettingAsForm(PwmSetting.NEWUSER_FORM,locale);
        final List<String> uniqueAttributes = configuration.readSettingAsStringArray(PwmSetting.NEWUSER_UNIQUE_ATTRIBUES);

        boolean usernameIsGenerated = isUsernameRandomGenerated(configuration);

        {
            boolean namingIsInForm = false;
            for (final FormConfiguration formConfiguration : formConfigurations) {
                if (ldapNamingattribute.equalsIgnoreCase(formConfiguration.getAttributeName())) {
                    namingIsInForm = true;
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
        }

        {
            boolean namingIsInUnique = false;
            for (final String uniqueAttr : uniqueAttributes) {
                if (ldapNamingattribute.equalsIgnoreCase(uniqueAttr)) {
                    namingIsInUnique = true;
                }
            }

            if (!namingIsInUnique && !usernameIsGenerated) {
                final StringBuilder errorMsg = new StringBuilder();
                errorMsg.append(PwmSetting.NEWUSER_UNIQUE_ATTRIBUES.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE));
                errorMsg.append(" -> ");
                errorMsg.append(PwmSetting.NEWUSER_UNIQUE_ATTRIBUES.getLabel(PwmConstants.DEFAULT_LOCALE));
                errorMsg.append(" does not contain the ldap naming attribute '").append(ldapNamingattribute).append("'");
                errorMsg.append(", but is required because it is the ldap naming attribute and the username is not being automatically generated.");
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

        final String bodyString = ServletHelper.readRequestBody(req, 10 * 1024);

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

    public void initializeToken(final PwmSession pwmSession, final PwmApplication pwmApplication, final Map<String,String> newUserForm)
            throws PwmUnrecoverableException {
        final NewUserBean newUserBean = pwmSession.getNewUserBean();
        final Configuration config = pwmApplication.getConfig();

        final String tokenKey;
        try {
            final TokenManager.TokenPayload tokenPayload = new TokenManager.TokenPayload(TOKEN_NAME,newUserForm);
            tokenKey = pwmApplication.getTokenManager().generateNewToken(tokenPayload);
            LOGGER.debug(pwmSession, "generated new user tokenKey code for session: " + tokenKey);
        } catch (PwmOperationalException e) {
            throw new PwmUnrecoverableException(e.getErrorInformation());
        }

        final String toAddress = newUserForm.get(config.readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE));

        newUserBean.setTokenIssued(true);
        newUserBean.setTokenEmailAddress(toAddress);

        sendEmailToken(pwmSession, pwmApplication, tokenKey);
    }

    private void sendEmailToken(final PwmSession pwmSession, final PwmApplication pwmApplication, final String tokenKey)
            throws PwmUnrecoverableException
    {
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final Configuration config = pwmApplication.getConfig();
        final String fromAddress = config.readSettingAsLocalizedString(PwmSetting.EMAIL_NEWUSER_VERIFICATION_FROM, userLocale);
        final String subject = config.readSettingAsLocalizedString(PwmSetting.EMAIL_NEWUSER_VERIFICATION_SUBJECT, userLocale);
        String plainBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_NEWUSER_VERIFICATION_BODY, userLocale);
        String htmlBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_NEWUSER_VERIFICATION_BODY_HTML, userLocale);

        final NewUserBean newUserBean = pwmSession.getNewUserBean();
        final String toAddress = newUserBean.getTokenEmailAddress();

        if (toAddress == null || toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send new user token email; no email address available in form");
        }

        plainBody = plainBody.replaceAll("%TOKEN%", tokenKey);
        htmlBody = htmlBody.replaceAll("%TOKEN%", tokenKey);

        pwmApplication.sendEmailUsingQueue(new EmailItemBean(toAddress, fromAddress, subject, plainBody, htmlBody));
        pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_SENT);
        LOGGER.debug(pwmSession, "token email added to send queue for " + toAddress);
    }

    public static PwmPasswordPolicy getNewUserPasswordPolicy(final PwmApplication pwmApplication, final PwmSession pwmSession)
            throws PwmUnrecoverableException, ChaiUnavailableException {
        final String configuredNewUserPasswordDN = pwmApplication.getConfig().readSettingAsString(PwmSetting.NEWUSER_PASSWORD_POLICY_USER);
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();

        if (configuredNewUserPasswordDN == null || configuredNewUserPasswordDN.length() < 1) {
            return pwmApplication.getConfig().getGlobalPasswordPolicy(userLocale);
        }

        final String lookupDN;
        if (configuredNewUserPasswordDN.equalsIgnoreCase("TESTUSER") ) {
            lookupDN = pwmApplication.getConfig().readSettingAsString(PwmSetting.LDAP_TEST_USER_DN);
        } else {
            lookupDN = configuredNewUserPasswordDN;
        }

        final ChaiUser chaiUser = ChaiFactory.createChaiUser(lookupDN, pwmApplication.getProxyChaiProvider());
        return PwmPasswordPolicy.createPwmPasswordPolicy(
                pwmSession, pwmApplication,
                userLocale,
                chaiUser
        );
    }

    private static boolean checkForURLcommand(final HttpServletRequest req, final HttpServletResponse resp, final PwmSession pwmSession)
            throws IOException
    {
        final String uri = req.getRequestURI();
        if (uri == null || uri.length() < 1) {
            return false;
        }
        final String servletPath = req.getServletPath();
        if (!uri.contains(servletPath)) {
            LOGGER.error("unexpected uri handler, uri '" + uri + "' does not contain servlet path '" + servletPath + "'");
            return false;
        }

        String aftPath = uri.substring(uri.indexOf(servletPath) + servletPath.length(),uri.length());
        if (aftPath.startsWith("/")) {
            aftPath = aftPath.substring(1,aftPath.length());
        }

        if (aftPath.contains("?")) {
            aftPath = aftPath.substring(0,aftPath.indexOf("?"));
        }

        if (aftPath.contains("&")) {
            aftPath = aftPath.substring(0,aftPath.indexOf("?"));
        }

        if (aftPath.length() <= 1) {
            return false;
        }

        final StringBuilder redirectURL = new StringBuilder();
        redirectURL.append(req.getContextPath());
        redirectURL.append(req.getServletPath());
        redirectURL.append("?");
        redirectURL.append(PwmConstants.PARAM_ACTION_REQUEST).append("=enterCode");
        redirectURL.append("&");
        redirectURL.append("code=").append(aftPath);
        redirectURL.append("&");
        redirectURL.append("pwmFormID=").append(Helper.buildPwmFormID(pwmSession.getSessionStateBean()));

        LOGGER.debug(pwmSession, "detected long servlet url, redirecting user to " + redirectURL);
        resp.sendRedirect(redirectURL.toString());
        return true;
    }
}
