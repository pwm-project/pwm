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

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiProviderFactory;
import com.novell.ldapchai.provider.ChaiSetting;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.*;
import password.pwm.config.option.TokenStorageMethod;
import password.pwm.error.*;
import password.pwm.event.AuditEvent;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.LoginInfoBean;
import password.pwm.http.bean.NewUserBean;
import password.pwm.i18n.Message;
import password.pwm.ldap.UserDataReader;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.token.TokenPayload;
import password.pwm.token.TokenService;
import password.pwm.util.*;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.client.rest.RestTokenDataClient;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.RestCheckPasswordServer;

import javax.servlet.ServletException;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/**
 * User interaction servlet for creating new users (self registration)
 *
 * @author Jason D. Rivard
 */
public class NewUserServlet extends PwmServlet {
    private static final PwmLogger LOGGER = PwmLogger.forClass(NewUserServlet.class);

    private static final String FIELD_PASSWORD1 = "password1";
    private static final String FIELD_PASSWORD2 = "password2";

    public enum NewUserAction implements PwmServlet.ProcessAction {
        checkProgress(PwmServlet.HttpMethod.GET),
        complete(PwmServlet.HttpMethod.GET),
        processForm(PwmServlet.HttpMethod.POST),
        validate(PwmServlet.HttpMethod.POST),
        enterCode(PwmServlet.HttpMethod.POST, HttpMethod.GET),
        reset(PwmServlet.HttpMethod.POST),
        agree(PwmServlet.HttpMethod.POST),;

        private final Collection<PwmServlet.HttpMethod> method;

        NewUserAction(PwmServlet.HttpMethod... method)
        {
            this.method = Collections.unmodifiableList(Arrays.asList(method));
        }

        public Collection<PwmServlet.HttpMethod> permittedMethods()
        {
            return method;
        }
    }

    protected NewUserAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        try {
            return NewUserAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        final NewUserAction action = this.readProcessAction(pwmRequest);
        final Configuration config = pwmApplication.getConfig();

        if (!config.readSettingAsBoolean(PwmSetting.NEWUSER_ENABLE)) {
            pwmRequest.respondWithError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            return;
        }

        // try to read the new user policy to make sure it's readable, that way an exception is thrown here instead of by the jsp
        config.getNewUserPasswordPolicy(pwmApplication, pwmSession.getSessionStateBean().getLocale());

        // convert a url command like /pwm/public/NewUserServlet/12321321 to redirect with a process action.
        if (action == null) {
            if (pwmRequest.convertURLtokenCommand()) {
                return;
            }
        }

        final NewUserBean newUserBean = pwmSession.getNewUserBean();

        if (action != null) {
            pwmRequest.validatePwmFormID();
            switch (action) {
                case checkProgress:
                    restCheckProgress(pwmRequest, newUserBean);
                    return;

                case complete:
                    handleComplete(pwmRequest, newUserBean);
                    return;
            }

            if (pwmSession.getSessionStateBean().isAuthenticated()) {
                pwmRequest.respondWithError(PwmError.ERROR_USERAUTHENTICATED.toInfo());
                return;
            }

            switch (action) {
                case processForm:
                    handleProcessFormRequest(pwmRequest);
                    return;

                case validate:
                    restValidateForm(pwmRequest);
                    return;

                case enterCode:
                    handleEnterCodeRequest(pwmRequest, newUserBean);
                    break;

                case reset:
                    pwmSession.clearSessionBean(NewUserBean.class);
                    break;

                case agree:
                    LOGGER.debug(pwmSession, "user accepted newuser agreement");
                    newUserBean.setAgreementPassed(true);
                    break;


            }
        }

        if (!pwmRequest.getPwmResponse().isCommitted()) {
            this.advancedToNextStage(pwmRequest, newUserBean);
        }
    }

    private void advancedToNextStage(
            final PwmRequest pwmRequest,
            final NewUserBean newUserBean
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Configuration config = pwmApplication.getConfig();

        if (newUserBean.getNewUserForm() == null) {
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.NEW_USER);
            return;
        }

        if (config.readSettingAsBoolean(PwmSetting.NEWUSER_EMAIL_VERIFICATION)) {
            if (!newUserBean.isEmailTokenIssued()) {
                initializeToken(pwmRequest, pwmApplication, NewUserBean.NewUserVerificationPhase.EMAIL);
            }

            if (!newUserBean.isEmailTokenPassed()) {
                pwmRequest.forwardToJsp(PwmConstants.JSP_URL.NEW_USER_ENTER_CODE);
                return;
            }
        }

        if (config.readSettingAsBoolean(PwmSetting.NEWUSER_SMS_VERIFICATION)) {
            if (!newUserBean.isSmsTokenIssued()) {
                initializeToken(pwmRequest, pwmApplication, NewUserBean.NewUserVerificationPhase.EMAIL);
            }

            if (!newUserBean.isSmsTokenPassed()) {
                pwmRequest.forwardToJsp(PwmConstants.JSP_URL.NEW_USER_ENTER_CODE);
                return;
            }
        }

        final String newUserAgreementText = config.readSettingAsLocalizedString(PwmSetting.NEWUSER_AGREEMENT_MESSAGE,
                pwmSession.getSessionStateBean().getLocale());
        if (newUserAgreementText != null && !newUserAgreementText.isEmpty()) {
            if (!newUserBean.isAgreementPassed()) {
                final MacroMachine macroMachine = createMacroMachineForNewUser(pwmApplication,
                        newUserBean.getNewUserForm());
                final String expandedText = macroMachine.expandMacros(newUserAgreementText);
                pwmRequest.getHttpServletRequest().setAttribute(PwmConstants.REQUEST_ATTR_AGREEMENT_TEXT, expandedText);
                pwmRequest.forwardToJsp(PwmConstants.JSP_URL.NEW_USER_AGREEMENT);
                return;
            }
        }

        if (!newUserBean.isFormPassed()) {
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.NEW_USER);
        }

        // success so create the new user.
        final String newUserDN = determineUserDN(pwmApplication, pwmSession, newUserBean.getNewUserForm());

        try {
            createUser(newUserBean.getNewUserForm(), pwmApplication, newUserDN, pwmSession);
            newUserBean.setCreateStartTime(new Date());
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.NEW_USER_WAIT);
        } catch (PwmOperationalException e) {
            if (config.readSettingAsBoolean(PwmSetting.NEWUSER_DELETE_ON_FAIL)) {
                deleteUserAccount(newUserDN, pwmSession, pwmApplication);
            }
            LOGGER.error(pwmSession, e.getErrorInformation().toDebugStr());
            pwmRequest.setResponseError(e.getErrorInformation());
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.NEW_USER);
        }
    }

    protected static void restValidateForm(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Locale locale = pwmRequest.getLocale();

        try {
            final NewUserBean.NewUserForm newUserForm = NewUserFormUtils.readFromJsonRequest(pwmRequest);
            PasswordUtility.PasswordCheckInfo passwordCheckInfo = verifyForm(pwmApplication, newUserForm, locale);
            if (passwordCheckInfo.isPassed() && passwordCheckInfo.getMatch() == PasswordUtility.PasswordCheckInfo.MATCH_STATUS.MATCH) {
                passwordCheckInfo = new PasswordUtility.PasswordCheckInfo(
                        Message.getLocalizedMessage(locale,
                                Message.SUCCESS_NEWUSER_FORM, pwmApplication.getConfig()),
                        passwordCheckInfo.isPassed(),
                        passwordCheckInfo.getStrength(),
                        passwordCheckInfo.getMatch(),
                        passwordCheckInfo.getErrorCode()
                );
            }
            final RestCheckPasswordServer.JsonData jsonData = RestCheckPasswordServer.JsonData.fromPasswordCheckInfo(
                    passwordCheckInfo);
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(jsonData);
            pwmRequest.outputJsonResult(restResultBean);
        } catch (PwmOperationalException e) {
            final RestResultBean restResultBean = RestResultBean.fromError(e.getErrorInformation(), pwmRequest);
            pwmRequest.outputJsonResult(restResultBean);
        }
    }

    static PasswordUtility.PasswordCheckInfo verifyForm(
            final PwmApplication pwmApplication,
            final NewUserBean.NewUserForm newUserForm,
            final Locale locale
    )
            throws PwmDataValidationException, PwmUnrecoverableException, ChaiUnavailableException
    {
        FormUtility.validateFormValues(pwmApplication.getConfig(), newUserForm.getFormData(), locale);
        FormUtility.validateFormValueUniqueness(
                pwmApplication,
                newUserForm.getFormData(),
                locale,
                Collections.<UserIdentity>emptyList()
        );
        final UserInfoBean uiBean = new UserInfoBean();
        uiBean.setCachedPasswordRuleAttributes(FormUtility.asStringMap(newUserForm.getFormData()));
        uiBean.setPasswordPolicy(pwmApplication.getConfig().getNewUserPasswordPolicy(pwmApplication, locale));
        return PasswordUtility.checkEnteredPassword(
                pwmApplication,
                locale,
                null,
                uiBean,
                null,
                newUserForm.getNewUserPassword(),
                newUserForm.getConfirmPassword()
        );
    }

    static void passwordCheckInfoToException(final PasswordUtility.PasswordCheckInfo passwordCheckInfo)
            throws PwmOperationalException
    {
        if (!passwordCheckInfo.isPassed()) {
            final ErrorInformation errorInformation = PwmError.forErrorNumber(passwordCheckInfo.getErrorCode()).toInfo();
            throw new PwmOperationalException(errorInformation);
        }
        if (passwordCheckInfo.getMatch() != PasswordUtility.PasswordCheckInfo.MATCH_STATUS.MATCH) {
            final ErrorInformation errorInformation = PwmError.PASSWORD_DOESNOTMATCH.toInfo();
            throw new PwmOperationalException(errorInformation);
        }

    }

    private void handleEnterCodeRequest(
            final PwmRequest pwmRequest,
            final NewUserBean newUserBean
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final String userEnteredCode = pwmRequest.readParameterAsString(PwmConstants.PARAM_TOKEN);

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
                final NewUserBean.NewUserForm newUserForm = NewUserFormUtils.fromTokenPayload(pwmRequest, tokenPayload);
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
                newUserBean.setNewUserForm(newUserForm);
            }
        } catch (PwmOperationalException e) {
            final String errorMsg = "token incorrect: " + e.getMessage();
            errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT, errorMsg);
        }


        if (!tokenPassed) {
            if (errorInformation == null) {
                errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT);
            }
            LOGGER.debug(pwmSession, errorInformation.toDebugStr());
            pwmRequest.setResponseError(errorInformation);
        }

        this.advancedToNextStage(pwmRequest, newUserBean);
    }


    private void handleProcessFormRequest(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        pwmSession.clearSessionBean(NewUserBean.class);
        final NewUserBean newUserBean = pwmSession.getNewUserBean();

        try {
            NewUserBean.NewUserForm newUserForm = NewUserFormUtils.readFromRequest(pwmRequest);
            final PasswordUtility.PasswordCheckInfo passwordCheckInfo = verifyForm(pwmApplication, newUserForm, pwmRequest.getLocale());
            passwordCheckInfoToException(passwordCheckInfo);
            newUserBean.setNewUserForm(newUserForm);
            newUserBean.setFormPassed(true);
            this.advancedToNextStage(pwmRequest, newUserBean);
        } catch (PwmOperationalException e) {
            pwmRequest.setResponseError(e.getErrorInformation());
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.NEW_USER);
        }
    }


    private static void createUser(
            final NewUserBean.NewUserForm newUserForm,
            final PwmApplication pwmApplication,
            final String newUserDN,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        final long startTime = System.currentTimeMillis();
        LOGGER.debug(pwmSession, "beginning createUser process for " + newUserDN);

        // re-perform verification before proceeding
        {
            final PasswordUtility.PasswordCheckInfo passwordCheckInfo = verifyForm(
                    pwmApplication,
                    newUserForm,
                    pwmSession.getSessionStateBean().getLocale()
            );
            passwordCheckInfoToException(passwordCheckInfo);
        }

        final PasswordData userPassword = newUserForm.getNewUserPassword();

        // set up the user creation attributes
        final Map<String, String> createAttributes = new LinkedHashMap<>();
        createAttributes.putAll(FormUtility.asStringMap(newUserForm.getFormData()));

        // read the creation object classes from configuration
        final Set<String> createObjectClasses = new LinkedHashSet<>(
                pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.DEFAULT_OBJECT_CLASSES));

        // add the auto-add object classes
        {
            final LdapProfile defaultLDAPProfile = pwmApplication.getConfig().getLdapProfiles().get(
                    PwmConstants.PROFILE_ID_DEFAULT);
            createObjectClasses.addAll(defaultLDAPProfile.readSettingAsStringArray(PwmSetting.AUTO_ADD_OBJECT_CLASSES));
        }

        final ChaiProvider chaiProvider = pwmApplication.getProxyChaiProvider(PwmConstants.PROFILE_ID_DEFAULT);
        try { // create the ldap entry
            chaiProvider.createEntry(newUserDN, createObjectClasses, createAttributes);

            LOGGER.info(pwmSession, "created user entry: " + newUserDN);
        } catch (ChaiOperationException e) {
            final String userMessage = "unexpected ldap error creating user entry: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,
                    userMessage);
            throw new PwmOperationalException(errorInformation);
        }

        final ChaiUser theUser = ChaiFactory.createChaiUser(newUserDN, chaiProvider);

        if (Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(AppProperty.NEWUSER_LDAP_USE_TEMP_PW))) {
            LOGGER.trace(pwmSession, "will use temporary password process for new user entry: " + newUserDN);
            final PasswordData temporaryPassword;
            {
                final RandomPasswordGenerator.RandomGeneratorConfig randomGeneratorConfig = new RandomPasswordGenerator.RandomGeneratorConfig();
                randomGeneratorConfig.setPasswordPolicy(
                        pwmApplication.getConfig().getNewUserPasswordPolicy(pwmApplication,
                                pwmSession.getSessionStateBean().getLocale()));
                temporaryPassword = RandomPasswordGenerator.createRandomPassword(pwmSession.getLabel(),
                        randomGeneratorConfig, pwmApplication);
            }
            final ChaiUser proxiedUser = ChaiFactory.createChaiUser(newUserDN, chaiProvider);
            try { //set password as admin
                proxiedUser.setPassword(temporaryPassword.getStringValue());
                LOGGER.debug(pwmSession, "set temporary password for new user entry: " + newUserDN);
            } catch (ChaiOperationException e) {
                final String userMessage = "unexpected ldap error setting temporary password for new user entry: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,
                        userMessage);
                throw new PwmOperationalException(errorInformation);
            }

            // add AD-specific attributes
            if (ChaiProvider.DIRECTORY_VENDOR.MICROSOFT_ACTIVE_DIRECTORY == chaiProvider.getDirectoryVendor()) {
                try {
                    LOGGER.debug(pwmSession,
                            "setting userAccountControl attribute to enable account " + theUser.getEntryDN());
                    theUser.writeStringAttribute("userAccountControl", "512");
                } catch (ChaiOperationException e) {
                    final String errorMsg = "error enabling AD account when writing userAccountControl attribute: " + e.getMessage();
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,
                            errorMsg);
                    throw new PwmOperationalException(errorInformation);
                }
            }

            try { // bind as user
                LOGGER.debug(pwmSession,
                        "attempting bind as user to then allow changing to requested password for new user entry: " + newUserDN);
                final ChaiConfiguration chaiConfiguration = new ChaiConfiguration(chaiProvider.getChaiConfiguration());
                chaiConfiguration.setSetting(ChaiSetting.BIND_DN, newUserDN);
                chaiConfiguration.setSetting(ChaiSetting.BIND_PASSWORD, temporaryPassword.getStringValue());
                final ChaiProvider bindAsProvider = ChaiProviderFactory.createProvider(chaiConfiguration);
                final ChaiUser bindAsUser = ChaiFactory.createChaiUser(newUserDN, bindAsProvider);
                bindAsUser.changePassword(temporaryPassword.getStringValue(), userPassword.getStringValue());
                LOGGER.debug(pwmSession, "changed to user requested password for new user entry: " + newUserDN);
                bindAsProvider.close();
            } catch (ChaiOperationException e) {
                final String userMessage = "unexpected ldap error setting user password for new user entry: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,
                        userMessage);
                throw new PwmOperationalException(errorInformation);
            }
        } else {
            try { //set password
                theUser.setPassword(userPassword.getStringValue());
                LOGGER.debug(pwmSession, "set user requested password for new user entry: " + newUserDN);
            } catch (ChaiOperationException e) {
                final String userMessage = "unexpected ldap error setting password for new user entry: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,
                        userMessage);
                throw new PwmOperationalException(errorInformation);
            }

            // add AD-specific attributes
            if (ChaiProvider.DIRECTORY_VENDOR.MICROSOFT_ACTIVE_DIRECTORY == chaiProvider.getDirectoryVendor()) {
                try {
                    theUser.writeStringAttribute("userAccountControl", "512");
                } catch (ChaiOperationException e) {
                    final String errorMsg = "error enabling AD account when writing userAccountControl attribute: " + e.getMessage();
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,
                            errorMsg);
                    throw new PwmOperationalException(errorInformation);
                }
            }
        }

        LOGGER.trace(pwmSession, "new user ldap creation process complete, now authenticating user");

        //authenticate the user to pwm
        final UserIdentity userIdentity = new UserIdentity(newUserDN, PwmConstants.PROFILE_ID_DEFAULT);
        final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(pwmApplication, pwmSession);
        sessionAuthenticator.authenticateUser(userIdentity, userPassword);

        {  // execute configured actions
            LOGGER.debug(pwmSession, "executing configured actions to user " + theUser.getEntryDN());
            final List<ActionConfiguration> actions = pwmApplication.getConfig().readSettingAsAction(
                    PwmSetting.NEWUSER_WRITE_ATTRIBUTES);
            if (actions != null && actions.isEmpty()) {
                final ActionExecutor.ActionExecutorSettings settings = new ActionExecutor.ActionExecutorSettings();
                settings.setExpandPwmMacros(true);
                settings.setUserIdentity(userIdentity);
                final ActionExecutor actionExecutor = new ActionExecutor(pwmApplication);
                actionExecutor.executeActions(actions, settings, pwmSession);
            }
        }

        // send user email
        sendNewUserEmailConfirmation(pwmSession, pwmApplication);


        // add audit record
        pwmApplication.getAuditManager().submit(AuditEvent.CREATE_USER, pwmSession.getUserInfoBean(), pwmSession);

        // increment the new user creation statistics
        pwmApplication.getStatisticsManager().incrementValue(Statistic.NEW_USERS);

        LOGGER.debug(pwmSession, "beginning createUser process for " + newUserDN + " (" + TimeDuration.fromCurrent(
                startTime).asCompactString() + ")");
    }

    private static void deleteUserAccount(
            final String userDN,
            PwmSession pwmSession,
            final PwmApplication pwmApplication
    )
            throws PwmUnrecoverableException
    {
        try {
            LOGGER.warn(pwmSession, "deleting ldap user account " + userDN);
            pwmApplication.getProxyChaiProvider(PwmConstants.PROFILE_ID_DEFAULT).deleteEntry(userDN);
            LOGGER.warn(pwmSession, "ldap user account " + userDN + " has been deleted");
        } catch (ChaiUnavailableException | ChaiOperationException e) {
            LOGGER.error(pwmSession, "error deleting ldap user account " + userDN + ", " + e.getMessage());
        }

        pwmSession.unauthenticateUser();
    }

    private static String determineUserDN(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final NewUserBean.NewUserForm formValues
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final MacroMachine macroMachine = createMacroMachineForNewUser(pwmApplication, formValues);
        final Configuration config = pwmApplication.getConfig();
        final List<String> configuredNames = config.readSettingAsStringArray(PwmSetting.NEWUSER_USERNAME_DEFINITION);
        final List<String> failedValues = new ArrayList<>();

        int attemptCount = 0;
        String generatedDN;
        while (attemptCount < configuredNames.size()) {
            final String expandedName;
            final String expandedContext;
            {

                {
                    final String configuredName = configuredNames.get(attemptCount);
                    expandedName = macroMachine.expandMacros(configuredName);
                }

                if (!testIfEntryNameExists(pwmApplication, pwmSession, expandedName)) {
                    LOGGER.trace(pwmSession, "generated entry name for new user is unique: " + expandedName);
                    final String configuredContext = config.readSettingAsString(PwmSetting.NEWUSER_CONTEXT);
                    expandedContext = macroMachine.expandMacros(configuredContext);
                    final String namingAttribute = config.getLdapProfiles().get(
                            PwmConstants.PROFILE_ID_DEFAULT).readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
                    final String escapedName = StringUtil.escapeLdap(expandedName);
                    generatedDN = namingAttribute + "=" + escapedName + "," + expandedContext;
                    LOGGER.debug(pwmSession, "generated dn for new user: " + generatedDN);
                    return generatedDN;
                } else {
                    failedValues.add(expandedName);
                }
            }

            LOGGER.debug(pwmSession, "generated entry name for new user is not unique, will try again");
            attemptCount++;
        }
        LOGGER.error(pwmSession,
                "failed to generate new user DN after " + attemptCount + " attempts, failed values: " + JsonUtil.serializeCollection(
                        failedValues));
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
        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication, pwmSession.getLabel());
        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        searchConfiguration.setUsername(rdnValue);
        try {
            Map<UserIdentity, Map<String, String>> results = userSearchEngine.performMultiUserSearch(
                    searchConfiguration, 2, Collections.<String>emptyList());
            return results != null && !results.isEmpty();
        } catch (PwmOperationalException e) {
            final String msg = "ldap error while searching for duplicate entry names: " + e.getMessage();
            LOGGER.error(pwmSession, msg);
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE, msg));
        }
    }

    private static void sendNewUserEmailConfirmation(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_NEWUSER, locale);

        if (configuredEmailSetting == null) {
            LOGGER.debug(pwmSession,
                    "skipping send of new user email for '" + userInfoBean.getUserIdentity().getUserDN() + "' no email configured");
            return;
        }

        pwmApplication.getEmailQueue().submitEmail(
                configuredEmailSetting,
                pwmSession.getUserInfoBean(),
                pwmSession.getSessionManager().getMacroMachine(pwmApplication)
        );
    }

    public void initializeToken(
            final PwmRequest pwmRequest,
            final PwmApplication pwmApplication,
            final NewUserBean.NewUserVerificationPhase phase
    )
            throws PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        if (pwmApplication.getConfig().getTokenStorageMethod() == TokenStorageMethod.STORE_LDAP) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,
                    "cannot generate new user tokens when storage type is configured as STORE_LDAP."));
        }

        final NewUserBean newUserBean = pwmRequest.getPwmSession().getNewUserBean();
        final Configuration config = pwmApplication.getConfig();
        final Map<String, String> tokenPayloadMap = NewUserFormUtils.toTokenPayload(pwmRequest, newUserBean.getNewUserForm());
        final MacroMachine macroMachine = createMacroMachineForNewUser(pwmApplication, newUserBean.getNewUserForm());

        switch (phase) {
            case SMS: {
                final String toNum = tokenPayloadMap.get(config.readSettingAsString(PwmSetting.SMS_USER_PHONE_ATTRIBUTE));

                final RestTokenDataClient.TokenDestinationData inputTokenDestData = new RestTokenDataClient.TokenDestinationData(
                        null, toNum, null);
                final RestTokenDataClient restTokenDataClient = new RestTokenDataClient(pwmApplication);
                final RestTokenDataClient.TokenDestinationData outputDestTokenData = restTokenDataClient.figureDestTokenDisplayString(
                        pwmRequest.getSessionLabel(),
                        inputTokenDestData,
                        null,
                        pwmRequest.getLocale());

                final String tokenKey;
                try {
                    final TokenPayload tokenPayload = pwmApplication.getTokenService().createTokenPayload(
                            NewUserBean.NewUserVerificationPhase.SMS.getTokenName(),
                            tokenPayloadMap,
                            null,
                            Collections.singleton(outputDestTokenData.getSms())
                    );
                    tokenKey = pwmApplication.getTokenService().generateNewToken(tokenPayload,
                            pwmRequest.getSessionLabel());
                    LOGGER.trace(pwmRequest, "generated new user sms token key code");
                } catch (PwmOperationalException e) {
                    throw new PwmUnrecoverableException(e.getErrorInformation());
                }

                final String message = config.readSettingAsLocalizedString(PwmSetting.SMS_NEWUSER_TOKEN_TEXT,
                        pwmSession.getSessionStateBean().getLocale());

                try {
                    TokenService.TokenSender.sendSmsToken(pwmApplication, null, macroMachine,
                            outputDestTokenData.getSms(), message, tokenKey);
                } catch (Exception e) {
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN));
                }

                newUserBean.setSmsTokenIssued(true);
                newUserBean.setTokenDisplayText(outputDestTokenData.getDisplayValue());
                newUserBean.setVerificationPhase(NewUserBean.NewUserVerificationPhase.SMS);
            }
            break;

            case EMAIL: {
                final String toAddress = tokenPayloadMap.get(config.readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE));

                final RestTokenDataClient.TokenDestinationData inputTokenDestData = new RestTokenDataClient.TokenDestinationData(
                        toAddress, null, null);
                final RestTokenDataClient restTokenDataClient = new RestTokenDataClient(pwmApplication);
                final RestTokenDataClient.TokenDestinationData outputDestTokenData = restTokenDataClient.figureDestTokenDisplayString(
                        pwmRequest.getSessionLabel(),
                        inputTokenDestData,
                        null,
                        pwmRequest.getLocale());

                final String tokenKey;
                try {
                    final TokenPayload tokenPayload = pwmApplication.getTokenService().createTokenPayload(
                            NewUserBean.NewUserVerificationPhase.EMAIL.getTokenName(),
                            tokenPayloadMap,
                            null,
                            Collections.singleton(outputDestTokenData.getEmail())
                    );
                    tokenKey = pwmApplication.getTokenService().generateNewToken(tokenPayload,
                            pwmRequest.getSessionLabel());
                    LOGGER.trace(pwmRequest, "generated new user email token");
                } catch (PwmOperationalException e) {
                    throw new PwmUnrecoverableException(e.getErrorInformation());
                }

                newUserBean.setEmailTokenIssued(true);
                newUserBean.setVerificationPhase(NewUserBean.NewUserVerificationPhase.EMAIL);
                newUserBean.setTokenDisplayText(outputDestTokenData.getDisplayValue());

                final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(
                        PwmSetting.EMAIL_NEWUSER_VERIFICATION, pwmSession.getSessionStateBean().getLocale());
                final EmailItemBean emailItemBean = new EmailItemBean(
                        outputDestTokenData.getEmail(),
                        configuredEmailSetting.getFrom(),
                        configuredEmailSetting.getSubject(),
                        configuredEmailSetting.getBodyPlain().replace("%TOKEN%", tokenKey),
                        configuredEmailSetting.getBodyHtml().replace("%TOKEN%", tokenKey));

                try {
                    TokenService.TokenSender.sendEmailToken(pwmApplication, null, macroMachine, emailItemBean,
                            outputDestTokenData.getEmail(), tokenKey);
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
            final PwmRequest pwmRequest,
            final NewUserBean newUserBean
    )
            throws IOException, ServletException
    {
        final Date startTime = newUserBean.getCreateStartTime();
        if (startTime == null) {
            pwmRequest.respondWithError(PwmError.ERROR_INCORRECT_REQUEST_SEQUENCE.toInfo(), true);
            return;
        }

        final long minWaitTime = pwmRequest.getConfig().readSettingAsLong(PwmSetting.NEWUSER_MINIMUM_WAIT_TIME) * 1000L;
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

        final LinkedHashMap<String, Object> outputMap = new LinkedHashMap<>();
        outputMap.put("percentComplete", percentComplete);
        outputMap.put("complete", complete);

        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(outputMap);

        LOGGER.trace(pwmRequest, "returning result for restCheckProgress: " + JsonUtil.serialize(restResultBean));
        pwmRequest.outputJsonResult(restResultBean);
    }

    private void handleComplete(
            final PwmRequest pwmRequest,
            final NewUserBean newUserBean
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final Date startTime = newUserBean.getCreateStartTime();
        if (startTime == null) {
            pwmRequest.respondWithError(PwmError.ERROR_INCORRECT_REQUEST_SEQUENCE.toInfo(), true);
            return;
        }

        final long minWaitTime = pwmRequest.getConfig().readSettingAsLong(PwmSetting.NEWUSER_MINIMUM_WAIT_TIME) * 1000L;
        final Date completeTime = new Date(startTime.getTime() + minWaitTime);

        // be sure minimum wait time has passed
        if (new Date().before(completeTime)) {
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.NEW_USER_WAIT);
            return;
        }

        pwmRequest.getPwmSession().clearSessionBean(NewUserBean.class);
        pwmRequest.forwardToSuccessPage(Message.SUCCESS_CREATE_USER);
    }

    private static MacroMachine createMacroMachineForNewUser(
            final PwmApplication pwmApplication,
            final NewUserBean.NewUserForm newUserForm
    )
            throws PwmUnrecoverableException
    {
        final Map<String, String> formValues = FormUtility.asStringMap(newUserForm.getFormData());
        final UserInfoBean stubUserBean = new UserInfoBean();

        final String emailAddressAttribute = pwmApplication.getConfig().readSettingAsString(
                PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE);
        stubUserBean.setUserEmailAddress(formValues.get(emailAddressAttribute));

        final String usernameAttribute = pwmApplication.getConfig().getLdapProfiles().get(
                PwmConstants.PROFILE_ID_DEFAULT).readSettingAsString(PwmSetting.LDAP_USERNAME_ATTRIBUTE);
        stubUserBean.setUsername(formValues.get(usernameAttribute));

        final LoginInfoBean stubLoginBean = new LoginInfoBean();
        stubLoginBean.setUserCurrentPassword(newUserForm.getNewUserPassword());

        final UserDataReader stubReader = new NewUserUserDataReader(formValues);
        return new MacroMachine(pwmApplication, stubUserBean, stubLoginBean, stubReader);
    }


    private static class NewUserUserDataReader implements UserDataReader {
        private final Map<String, String> attributeData;

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
            return readStringAttribute(attribute, false);
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
            return readStringAttributes(attributes, false);
        }

        @Override
        public Map<String, String> readStringAttributes(
                Collection<String> attributes,
                boolean ignoreCache
        )
                throws ChaiUnavailableException, ChaiOperationException
        {
            final Map<String, String> returnObj = new LinkedHashMap<>();
            for (final String key : attributes) {
                returnObj.put(key, readStringAttribute(key));
            }
            return Collections.unmodifiableMap(returnObj);
        }
    }

    private static class NewUserFormUtils {
        static NewUserBean.NewUserForm readFromRequest(PwmRequest pwmRequest)
                throws PwmDataValidationException, PwmUnrecoverableException
        {
            final Locale userLocale = pwmRequest.getLocale();
            final List<FormConfiguration> newUserForm = pwmRequest.getConfig().readSettingAsForm(
                    PwmSetting.NEWUSER_FORM);
            final Map<FormConfiguration, String> userFormValues = FormUtility.readFormValuesFromRequest(pwmRequest,
                    newUserForm, userLocale);
            final PasswordData passwordData1 = pwmRequest.readParameterAsPassword(FIELD_PASSWORD1);
            final PasswordData passwordData2 = pwmRequest.readParameterAsPassword(FIELD_PASSWORD2);
            return new NewUserBean.NewUserForm(userFormValues, passwordData1, passwordData2);
        }

        static NewUserBean.NewUserForm readFromJsonRequest(final PwmRequest pwmRequest)
                throws IOException, PwmUnrecoverableException, PwmDataValidationException
        {
            final Locale userLocale = pwmRequest.getLocale();
            final List<FormConfiguration> newUserForm = pwmRequest.getConfig().readSettingAsForm(
                    PwmSetting.NEWUSER_FORM);
            final Map<String, String> jsonBodyMap = pwmRequest.readBodyAsJsonStringMap();
            final Map<FormConfiguration, String> userFormValues = FormUtility.readFormValuesFromMap(jsonBodyMap,
                    newUserForm, userLocale);
            final PasswordData passwordData1 = jsonBodyMap.containsKey(FIELD_PASSWORD1) && !jsonBodyMap.get(
                    FIELD_PASSWORD1).isEmpty()
                    ? new PasswordData(jsonBodyMap.get(FIELD_PASSWORD1))
                    : null;
            final PasswordData passwordData2 = jsonBodyMap.containsKey(FIELD_PASSWORD2) && !jsonBodyMap.get(
                    FIELD_PASSWORD2).isEmpty()
                    ? new PasswordData(jsonBodyMap.get(FIELD_PASSWORD2))
                    : null;
            return new NewUserBean.NewUserForm(userFormValues, passwordData1, passwordData2);
        }

        static NewUserBean.NewUserForm fromTokenPayload(
                final PwmRequest pwmRequest,
                final TokenPayload tokenPayload
        )
                throws PwmOperationalException, PwmUnrecoverableException
        {
            final Locale userLocale = pwmRequest.getLocale();
            final List<FormConfiguration> newUserForm = pwmRequest.getConfig().readSettingAsForm(
                    PwmSetting.NEWUSER_FORM);

            final Map<String, String> payloadMap = tokenPayload.getData();
            final Map<FormConfiguration, String> userFormValues = FormUtility.readFormValuesFromMap(payloadMap,
                    newUserForm, userLocale);
            final PasswordData passwordData;
            if (payloadMap.containsKey(FIELD_PASSWORD1)) {
                final String rawPassword = payloadMap.get(FIELD_PASSWORD1);
                final String realPassword = SecureHelper.decryptStringValue(rawPassword,
                        pwmRequest.getConfig().getSecurityKey(), true);
                passwordData = new PasswordData(realPassword);
            } else {
                passwordData = null;
            }
            return new NewUserBean.NewUserForm(userFormValues, passwordData, passwordData);
        }

        static Map<String, String> toTokenPayload(
                final PwmRequest pwmRequest,
                NewUserBean.NewUserForm newUserForm
        )
                throws PwmUnrecoverableException
        {
            final Map<String, String> payloadMap = new LinkedHashMap<>();
            payloadMap.putAll(FormUtility.asStringMap(newUserForm.getFormData()));
            final String encryptedPassword = SecureHelper.encryptToString(
                    newUserForm.getNewUserPassword().getStringValue(),
                    pwmRequest.getConfig().getSecurityKey(),
                    true
            );
            payloadMap.put(FIELD_PASSWORD1, encryptedPassword);
            return payloadMap;
        }
    }
}
