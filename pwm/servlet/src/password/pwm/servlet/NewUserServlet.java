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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.*;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.bean.servlet.NewUserBean;
import password.pwm.config.ActionConfiguration;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.TokenStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.event.AuditEvent;
import password.pwm.i18n.Message;
import password.pwm.ldap.LdapUserDataReader;
import password.pwm.ldap.UserAuthenticator;
import password.pwm.ldap.UserDataReader;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.token.TokenPayload;
import password.pwm.util.*;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.client.rest.RestTokenDataClient;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.RestCheckPasswordServer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/**
 * User interaction servlet for creating new users (self registration)
 *
 * @author Jason D. Rivard
 */
public class NewUserServlet extends TopServlet {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(NewUserServlet.class);

    private static final String FIELD_PASSWORD = "password1";

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

        // try to read the new user policy to make sure it's readable, that way an exception is thrown here instead of by the jsp
        pwmApplication.getConfig().getNewUserPasswordPolicy(pwmApplication, pwmSession.getSessionStateBean().getLocale());

        // convert a url command like /pwm/public/NewUserServlet/12321321 to redirect with a process action.
        if (processAction == null || processAction.length() < 1) {
            if (convertURLtokenCommand(req, resp, pwmApplication, pwmSession)) {
                return;
            }
        }

        final NewUserBean newUserBean = pwmSession.getNewUserBean();

        if (processAction != null && processAction.length() > 1) {
            Validator.validatePwmFormID(req);
            if ("checkProgress".equalsIgnoreCase(processAction)) {
                restCheckProgress(pwmApplication, pwmSession, newUserBean, req, resp);
                return;
            } else if ("complete".equalsIgnoreCase(processAction)) {
                handleComplete(pwmApplication, pwmSession, newUserBean, req, resp);
                return;
            }
        }

        if (pwmSession.getSessionStateBean().isAuthenticated()) {
            ssBean.setSessionError(PwmError.ERROR_USERAUTHENTICATED.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (processAction != null && processAction.length() > 1) {
            Validator.validatePwmFormID(req);
            if ("create".equalsIgnoreCase(processAction)) {
                handleCreateRequest(req,resp);
                return;
            } else if ("validate".equalsIgnoreCase(processAction)) {
                restValidateForm(req, resp);
                return;
            } else if ("enterCode".equalsIgnoreCase(processAction)) {
                handleEnterCodeRequest(req, resp, pwmApplication, pwmSession);
            } else if ("reset".equalsIgnoreCase(processAction)) {
                pwmSession.clearSessionBean(NewUserBean.class);
            } else if ("agree".equalsIgnoreCase(processAction)) {
                // accept password change agreement
                LOGGER.debug(pwmSession, "user accepted newuser agreement");
                newUserBean.setAgreementPassed(true);
            }
        }

        if (!resp.isCommitted()) {
            this.advancedToNextStage(pwmApplication, pwmSession, req, resp);
        }
    }

    private void advancedToNextStage(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final Configuration config = pwmApplication.getConfig();
        final NewUserBean newUserBean = pwmSession.getNewUserBean();

        if (newUserBean.getFormData() == null) {
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.NEW_USER);
            return;
        }

        try {
            verifyFormAttributes(newUserBean.getFormData(), pwmSession, pwmApplication);
        } catch (PwmOperationalException e) {
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            LOGGER.error(pwmSession, e.getErrorInformation().toDebugStr());
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.NEW_USER);
            return;
        }

        if (config.readSettingAsBoolean(PwmSetting.NEWUSER_EMAIL_VERIFICATION)) {
            if (!newUserBean.isEmailTokenIssued()) {
                initializeToken(pwmSession, pwmApplication, NewUserBean.NewUserVerificationPhase.EMAIL);
            }

            if (!newUserBean.isEmailTokenPassed()) {
                ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.NEW_USER_ENTER_CODE);
                return;
            }
        }

        if (config.readSettingAsBoolean(PwmSetting.NEWUSER_SMS_VERIFICATION)) {
            if (!newUserBean.isSmsTokenIssued()) {
                initializeToken(pwmSession, pwmApplication, NewUserBean.NewUserVerificationPhase.EMAIL);
            }

            if (!newUserBean.isSmsTokenPassed()) {
                ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.NEW_USER_ENTER_CODE);
                return;
            }
        }

        final String newUserAgreementText = config.readSettingAsLocalizedString(PwmSetting.NEWUSER_AGREEMENT_MESSAGE, pwmSession.getSessionStateBean().getLocale());
        if (newUserAgreementText != null && newUserAgreementText.length() > 0) {
            if (!newUserBean.isAgreementPassed()) {
                ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.NEW_USER_AGREEMENT);
                return;
            }
        }

        if (!newUserBean.isFormPassed()) {
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.NEW_USER);
        }

        // success so create the new user.
        final Map<String,String> formValues = newUserBean.getFormData();
        final String newUserDN = determineUserDN(pwmApplication, pwmSession, formValues);

        try {
            createUser(formValues, pwmApplication, newUserDN, pwmSession);
            newUserBean.setCreateStartTime(new Date());
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.NEW_USER_WAIT);
        } catch (PwmOperationalException e) {
            if (config.readSettingAsBoolean(PwmSetting.NEWUSER_DELETE_ON_FAIL)) {
                deleteUserAccount(newUserDN, pwmSession, pwmApplication);
            }
            LOGGER.error(pwmSession, e.getErrorInformation().toDebugStr());
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.NEW_USER);
        }
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
            final RestCheckPasswordServer.JsonData jsonData = validateForm(pwmApplication, pwmSession, userValues,
                    locale);
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(jsonData);
            ServletHelper.outputJsonResult(resp,restResultBean);
        } catch (PwmOperationalException e) {
            final RestResultBean restResultBean = RestResultBean.fromError(e.getErrorInformation(), pwmSession.getSessionStateBean().getLocale(), pwmApplication.getConfig());
            ServletHelper.outputJsonResult(resp,restResultBean);
        }
    }

    private static RestCheckPasswordServer.JsonData validateForm(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final Map<String,String> formValues,
            final Locale locale
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        verifyFormAttributes(formValues, pwmSession, pwmApplication);
        { // no form errors, so check the password
            final UserInfoBean uiBean = new UserInfoBean();
            uiBean.setCachedPasswordRuleAttributes(formValues);
            uiBean.setPasswordPolicy(pwmApplication.getConfig().getNewUserPasswordPolicy(pwmApplication,locale));
            PasswordUtility.PasswordCheckInfo passwordCheckInfo = PasswordUtility.checkEnteredPassword(
                    pwmApplication,
                    pwmSession.getSessionStateBean().getLocale(),
                    null,
                    uiBean,
                    formValues.get("password1"),
                    formValues.get("password2"),
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
            return RestCheckPasswordServer.JsonData.fromPasswordCheckInfo(passwordCheckInfo);
        }
    }

    private void handleEnterCodeRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final NewUserBean newUserBean = pwmSession.getNewUserBean();
        final String userEnteredCode = Validator.readStringFromRequest(req, PwmConstants.PARAM_TOKEN);

        boolean tokenPassed = false;
        ErrorInformation errorInformation = null;
        try {
            final TokenPayload tokenPayload = pwmApplication.getTokenService().processUserEnteredCode(
                    pwmSession,
                    null,
                    newUserBean.getVerificationPhase().getTokenName(),
                    userEnteredCode
            );
            if (tokenPayload != null) {
                if (newUserBean.getVerificationPhase() == NewUserBean.NewUserVerificationPhase.EMAIL) {
                    LOGGER.debug("Email token passed");
                    newUserBean.setEmailTokenPassed(true);
                    newUserBean.setVerificationPhase(NewUserBean.NewUserVerificationPhase.NONE);
                    tokenPassed = true;
                } else if (newUserBean.getVerificationPhase() == NewUserBean.NewUserVerificationPhase.SMS) {
                    LOGGER.debug("SMS token passed");
                    newUserBean.setSmsTokenPassed(true);
                    newUserBean.setVerificationPhase(NewUserBean.NewUserVerificationPhase.NONE);
                    tokenPassed = true;
                }
                final Map<String,String> formData = tokenPayload.getData();
                newUserBean.setFormData(formData);
            }
        } catch (PwmOperationalException e) {
            final String errorMsg = "token incorrect: " + e.getMessage();
            errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,errorMsg);
        }


        if (!tokenPassed) {
            if (errorInformation == null) {
                errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT);
            }
            LOGGER.debug(pwmSession, errorInformation.toDebugStr());
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
        }

        this.advancedToNextStage(pwmApplication, pwmSession, req, resp);
    }


    private void handleCreateRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        pwmSession.clearSessionBean(NewUserBean.class);
        final NewUserBean newUserBean = pwmSession.getNewUserBean();

        try {
            final Map<String, String> formValues = Validator.readRequestParametersAsMap(req);
            validateForm(pwmApplication, pwmSession, formValues, pwmSession.getSessionStateBean().getLocale());
            newUserBean.setFormData(formValues);
            newUserBean.setFormPassed(true);
            this.advancedToNextStage(pwmApplication, pwmSession, req, resp);
        } catch (PwmOperationalException e) {
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.NEW_USER);
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

        final ChaiProvider chaiProvider = pwmApplication.getProxyChaiProvider(PwmConstants.PROFILE_ID_DEFAULT);
        try { // create the ldap entry
            chaiProvider.createEntry(newUserDN, createObjectClasses, createAttributes);

            LOGGER.info(pwmSession, "created user entry: " + newUserDN);
        } catch (ChaiOperationException e) {
            final String userMessage = "unexpected ldap error creating user entry: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,userMessage);
            throw new PwmOperationalException(errorInformation);
        }

        final ChaiUser theUser = ChaiFactory.createChaiUser(newUserDN, chaiProvider);

        try { //set password
            theUser.setPassword(userPassword);
            LOGGER.debug(pwmSession, "set user requested password for new user entry: " + newUserDN);
        } catch (ChaiOperationException e) {
            final String userMessage = "unexpected ldap error setting password for new user entry: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,userMessage);
            throw new PwmOperationalException(errorInformation);
        }

        // add AD-specific attributes
        final ChaiUser proxiedUser = ChaiFactory.createChaiUser(newUserDN, chaiProvider);
        if (ChaiProvider.DIRECTORY_VENDOR.MICROSOFT_ACTIVE_DIRECTORY == chaiProvider.getDirectoryVendor()) {
            try {
                proxiedUser.writeStringAttribute("userAccountControl", "512");
            } catch (ChaiOperationException e) {
                final String errorMsg = "error enabling AD account when writing userAccountControl attribute: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,errorMsg);
                throw new PwmOperationalException(errorInformation);
            }
        }

        LOGGER.trace(pwmSession, "new user ldap creation process complete, now authenticating user");

        //authenticate the user to pwm
        UserAuthenticator.authenticateUser(theUser.getEntryDN(), userPassword, null, null, pwmSession, pwmApplication, true);

        {  // execute configured actions
            LOGGER.debug(pwmSession, "executing configured actions to user " + theUser.getEntryDN());
            final List<ActionConfiguration> actions = pwmApplication.getConfig().readSettingAsAction(PwmSetting.NEWUSER_WRITE_ATTRIBUTES);
            final ActionExecutor.ActionExecutorSettings settings = new ActionExecutor.ActionExecutorSettings();
            settings.setExpandPwmMacros(true);
            settings.setUserInfoBean(pwmSession.getUserInfoBean());
            final ActionExecutor actionExecutor = new ActionExecutor(pwmApplication);
            actionExecutor.executeActions(actions, settings, pwmSession);
        }

        { // send user email
            final UserDataReader userDataReader = LdapUserDataReader.appProxiedReader(pwmApplication,
                    pwmSession.getUserInfoBean().getUserIdentity());
            sendNewUserEmailConfirmation(pwmSession, userDataReader, pwmApplication);
        }

        // add audit record
        pwmApplication.getAuditManager().submit(AuditEvent.CREATE_USER, pwmSession.getUserInfoBean(), pwmSession);

        // increment the new user creation statistics
        pwmApplication.getStatisticsManager().incrementValue(Statistic.NEW_USERS);

        LOGGER.debug(pwmSession,"beginning createUser process for " + newUserDN + " (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")");
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
                    formValues,
                    userLocale,
                    pwmSession.getSessionManager(),
                    Collections.<UserIdentity>emptyList()
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
            pwmApplication.getProxyChaiProvider(PwmConstants.PROFILE_ID_DEFAULT).deleteEntry(userDN);
            LOGGER.warn(pwmSession, "ldap user account " + userDN + " has been deleted");
        } catch (ChaiUnavailableException e) {
            LOGGER.error(pwmSession, "error deleting ldap user account " + userDN + ", " + e.getMessage());
        } catch (ChaiOperationException e) {
            LOGGER.error(pwmSession, "error deleting ldap user account " + userDN + ", " + e.getMessage());
        }

        pwmSession.unauthenticateUser();
    }

    private static String determineUserDN(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final Map<String, String> formValues
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final Configuration config = pwmApplication.getConfig();
        final List<String> configuredNames = config.readSettingAsStringArray(PwmSetting.NEWUSER_USERNAME_DEFINITION);
        final List<String> failedValues = new ArrayList<String>();

        int attemptCount = 0;
        String generatedDN;
        while (attemptCount < configuredNames.size()) {
            final String expandedName;
            final String expandedContext;
            {
                final MacroMachine macroMachine = createMacroMachineForNewUser(pwmApplication, formValues);

                {
                    final String configuredName = configuredNames.get(attemptCount);
                    expandedName = macroMachine.expandMacros(configuredName);
                }

                if (!testIfEntryNameExists(pwmApplication, pwmSession, expandedName)) {
                    LOGGER.trace(pwmSession, "generated entry name for new user is unique: " + expandedName);
                    final String configuredContext = config.readSettingAsString(PwmSetting.NEWUSER_CONTEXT);
                    expandedContext = macroMachine.expandMacros(configuredContext);
                    final String namingAttribute = config.getLdapProfiles().get(PwmConstants.PROFILE_ID_DEFAULT).readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
                    generatedDN = namingAttribute + "=" + expandedName + "," + expandedContext;
                    LOGGER.debug(pwmSession, "generated dn for new user: " + generatedDN);
                    return generatedDN;
                } else {
                    failedValues.add(expandedName);
                }
            }

            LOGGER.debug(pwmSession, "generated entry name for new user is not unique, will try again");
            attemptCount++;
        }
        LOGGER.error(pwmSession, "failed to generate new user DN after " + attemptCount + " attempts, failed values: " + Helper.getGson().toJson(failedValues));
        throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,
                "unable to generate a unique DN value"));
    }

    private static boolean testIfEntryNameExists(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final String rdnValue
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        searchConfiguration.setUsername(rdnValue);
        try {
            Map<UserIdentity, Map<String, String>> results = userSearchEngine.performMultiUserSearch(pwmSession, searchConfiguration, 2, Collections.<String>emptyList());
            return results != null && !results.isEmpty();
        } catch (PwmOperationalException e) {
            final String msg = "ldap error while searching for duplicate entry names: " + e.getMessage();
            LOGGER.error(pwmSession, msg);
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,msg));
        }
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

        if (configuredEmailSetting == null) {
            LOGGER.debug(pwmSession, "skipping send of new user email for '" + userInfoBean.getUserIdentity().getUserDN() + "' no email configured");
            return;
        }

        pwmApplication.getEmailQueue().submitEmail(configuredEmailSetting, pwmSession.getUserInfoBean(), userDataReader);
    }


    private static Map<String, String> readResponsesFromJsonRequest(
            final HttpServletRequest req
    )
            throws IOException, PwmUnrecoverableException
    {
        final Map<String, String> inputMap = new HashMap<String, String>();

        final String bodyString = ServletHelper.readRequestBody(req);

        final Gson gson = Helper.getGson();
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

    public void initializeToken(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final NewUserBean.NewUserVerificationPhase phase
    )
            throws PwmUnrecoverableException {
        if (pwmApplication.getConfig().getTokenStorageMethod() == TokenStorageMethod.STORE_LDAP) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"cannot generate new user tokens when storage type is configured as STORE_LDAP."));
        }

        final NewUserBean newUserBean = pwmSession.getNewUserBean();
        final Configuration config = pwmApplication.getConfig();
        final Map<String,String> formData = newUserBean.getFormData();

        switch (phase) {
            case SMS: {
                final String toNum = formData.get(config.readSettingAsString(PwmSetting.SMS_USER_PHONE_ATTRIBUTE));

                final RestTokenDataClient.TokenDestinationData inputTokenDestData = new RestTokenDataClient.TokenDestinationData(null,toNum,null);
                final RestTokenDataClient restTokenDataClient = new RestTokenDataClient(pwmApplication);
                final RestTokenDataClient.TokenDestinationData outputDestTokenData = restTokenDataClient.figureDestTokenDisplayString(
                        pwmSession,
                        inputTokenDestData,
                        null,
                        pwmSession.getSessionStateBean().getLocale());

                final String tokenKey;
                try {
                    final TokenPayload tokenPayload = pwmApplication.getTokenService().createTokenPayload(
                            NewUserBean.NewUserVerificationPhase.SMS.getTokenName(),
                            formData,
                            null,
                            Collections.singleton(outputDestTokenData.getSms())
                    );
                    tokenKey = pwmApplication.getTokenService().generateNewToken(tokenPayload,pwmSession);
                    LOGGER.trace(pwmSession, "generated new user sms token key code");
                } catch (PwmOperationalException e) {
                    throw new PwmUnrecoverableException(e.getErrorInformation());
                }

                final String message = config.readSettingAsLocalizedString(PwmSetting.SMS_NEWUSER_TOKEN_TEXT, pwmSession.getSessionStateBean().getLocale());

                try {
                    Helper.TokenSender.sendSmsToken(pwmApplication, null, null, outputDestTokenData.getSms(), message, tokenKey);
                } catch (Exception e) {
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN));
                }

                newUserBean.setSmsTokenIssued(true);
                newUserBean.setTokenDisplayText(outputDestTokenData.getDisplayValue());
                newUserBean.setVerificationPhase(NewUserBean.NewUserVerificationPhase.SMS);
            }
            break;

            case EMAIL: {
                final String toAddress = formData.get(config.readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE));

                final RestTokenDataClient.TokenDestinationData inputTokenDestData = new RestTokenDataClient.TokenDestinationData(toAddress,null,null);
                final RestTokenDataClient restTokenDataClient = new RestTokenDataClient(pwmApplication);
                final RestTokenDataClient.TokenDestinationData outputDestTokenData = restTokenDataClient.figureDestTokenDisplayString(
                        pwmSession,
                        inputTokenDestData,
                        null,
                        pwmSession.getSessionStateBean().getLocale());

                final String tokenKey;
                try {
                    final TokenPayload tokenPayload = pwmApplication.getTokenService().createTokenPayload(
                            NewUserBean.NewUserVerificationPhase.EMAIL.getTokenName(),
                            formData,
                            null,
                            Collections.singleton(outputDestTokenData.getEmail())
                    );
                    tokenKey = pwmApplication.getTokenService().generateNewToken(tokenPayload, pwmSession);
                    LOGGER.trace(pwmSession, "generated new user email token key code");
                } catch (PwmOperationalException e) {
                    throw new PwmUnrecoverableException(e.getErrorInformation());
                }

                newUserBean.setEmailTokenIssued(true);
                newUserBean.setVerificationPhase(NewUserBean.NewUserVerificationPhase.EMAIL);
                newUserBean.setTokenDisplayText(outputDestTokenData.getDisplayValue());

                final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_NEWUSER_VERIFICATION, pwmSession.getSessionStateBean().getLocale());
                final EmailItemBean emailItemBean = new EmailItemBean(
                        outputDestTokenData.getEmail(),
                        configuredEmailSetting.getFrom(),
                        configuredEmailSetting.getSubject(),
                        configuredEmailSetting.getBodyPlain().replace("%TOKEN%", tokenKey),
                        configuredEmailSetting.getBodyHtml().replace("%TOKEN%", tokenKey));

                try {
                    Helper.TokenSender.sendEmailToken(pwmApplication, null, null, emailItemBean, outputDestTokenData.getEmail(), tokenKey);
                } catch (Exception e) {
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN));
                }
            }
            break;

            default:
                LOGGER.error("Unimplemented token purpose: " + phase);
                newUserBean.setVerificationPhase(NewUserBean.NewUserVerificationPhase.NONE);
        }
    }

    private void restCheckProgress(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final NewUserBean newUserBean,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {
        final Date startTime = newUserBean.getCreateStartTime();
        if (startTime == null) {
            pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_INCORRECT_REQUEST_SEQUENCE));
            ServletHelper.forwardToErrorPage(req,resp,true);
            return;
        }

        final long minWaitTime = pwmApplication.getConfig().readSettingAsLong(PwmSetting.NEWUSER_MINIMUM_WAIT_TIME) * 1000L;
        final Date completeTime = new Date(startTime.getTime() + minWaitTime);

        final BigDecimal percentComplete;
        final boolean complete;

        // be sure minimum wait time has passed
        if (new Date().after(completeTime)) {
            percentComplete = new BigDecimal("100");
            complete = true;
        } else {
            final TimeDuration elapsedTime = TimeDuration.fromCurrent(startTime);
            complete = false;
            percentComplete = new Percent(elapsedTime.getTotalMilliseconds(), minWaitTime).asBigDecimal();
        }

        final LinkedHashMap<String,Object> outputMap = new LinkedHashMap<String, Object>();
        outputMap.put("percentComplete",percentComplete);
        outputMap.put("complete",complete);

        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(outputMap);

        LOGGER.trace(pwmSession,"returning result for restCheckProgress: " + Helper.getGson().toJson(restResultBean));
        ServletHelper.outputJsonResult(resp,restResultBean);
    }

    private void handleComplete(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final NewUserBean newUserBean,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final Date startTime = newUserBean.getCreateStartTime();
        if (startTime == null) {
            pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_INCORRECT_REQUEST_SEQUENCE));
            ServletHelper.forwardToErrorPage(req,resp,true);
            return;
        }

        final long minWaitTime = pwmApplication.getConfig().readSettingAsLong(PwmSetting.NEWUSER_MINIMUM_WAIT_TIME) * 1000L;
        final Date completeTime = new Date(startTime.getTime() + minWaitTime);

        // be sure minimum wait time has passed
        if (new Date().before(completeTime)) {
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.NEW_USER_WAIT);
            return;
        }

        pwmSession.clearSessionBean(NewUserBean.class);
        pwmSession.getSessionStateBean().setSessionSuccess(Message.SUCCESS_CREATE_USER, null);
        ServletHelper.forwardToSuccessPage(req, resp);
    }

    private static MacroMachine createMacroMachineForNewUser(
            final PwmApplication pwmApplication,
            final Map<String,String> formValues
    ) {

        final UserInfoBean stubBean = new UserInfoBean();


        final String emailAddressAttribute = pwmApplication.getConfig().readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE);
        stubBean.setUserEmailAddress(formValues.get(emailAddressAttribute));

        final String usernameAttribute = pwmApplication.getConfig().getLdapProfiles().get(PwmConstants.PROFILE_ID_DEFAULT).readSettingAsString(PwmSetting.LDAP_USERNAME_ATTRIBUTE);
        stubBean.setUsername(formValues.get(usernameAttribute));

        stubBean.setUserCurrentPassword(formValues.get(NewUserServlet.FIELD_PASSWORD));

        final UserDataReader stubReader = new NewUserUserDataReader(formValues);
        return new MacroMachine(pwmApplication, stubBean, stubReader);

    }

    private static class NewUserUserDataReader implements UserDataReader {
        private final Map<String,String> attributeData;

        private NewUserUserDataReader(final Map<String, String> attributeData)
        {
            this.attributeData = attributeData;
        }

        @Override
        public String getUserDN()
        {
            return null;
        }

        @Override
        public String readStringAttribute(String attribute)
                throws ChaiUnavailableException, ChaiOperationException
        {
            return readStringAttribute(attribute,false);
        }

        @Override
        public String readStringAttribute(
                String attribute,
                boolean ignoreCache
        )
                throws ChaiUnavailableException, ChaiOperationException
        {
            return attributeData.get(attribute);
        }

        @Override
        public Date readDateAttribute(String attribute)
                throws ChaiUnavailableException, ChaiOperationException
        {
            return null;
        }

        @Override
        public Map<String, String> readStringAttributes(Collection<String> attributes)
                throws ChaiUnavailableException, ChaiOperationException
        {
            return readStringAttributes(attributes,false);
        }

        @Override
        public Map<String, String> readStringAttributes(
                Collection<String> attributes,
                boolean ignoreCache
        )
                throws ChaiUnavailableException, ChaiOperationException
        {
            final Map<String,String> returnObj = new LinkedHashMap<String, String>();
            for (final String key : attributes) {
                returnObj.put(key, readStringAttribute(key));
            }
            return Collections.unmodifiableMap(returnObj);
        }
    }
}
