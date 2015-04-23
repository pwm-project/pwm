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
import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiProviderFactory;
import com.novell.ldapchai.provider.ChaiSetting;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.*;
import password.pwm.config.option.TokenStorageMethod;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.error.*;
import password.pwm.event.AuditEvent;
import password.pwm.http.HttpMethod;
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
    private static final String TOKEN_PAYLOAD_ATTR = "_______profileID";
    
    public enum Page {
        ProfileSelect,
        Form,
    }

    public enum NewUserAction implements PwmServlet.ProcessAction {
        profileChoice(HttpMethod.POST),
        checkProgress(HttpMethod.GET),
        complete(HttpMethod.GET),
        processForm(HttpMethod.POST),
        validate(HttpMethod.POST),
        enterCode(HttpMethod.POST, HttpMethod.GET),
        reset(HttpMethod.POST),
        agree(HttpMethod.POST),

        ;

        private final Collection<HttpMethod> method;

        NewUserAction(HttpMethod... method)
        {
            this.method = Collections.unmodifiableList(Arrays.asList(method));
        }

        public Collection<HttpMethod> permittedMethods()
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
        
        // convert a url command like /pwm/public/NewUserServlet/12321321 to redirect with a process action.
        if (action == null) {
            if (pwmRequest.convertURLtokenCommand()) {
                return;
            }
        }

        final NewUserBean newUserBean = pwmSession.getNewUserBean();

        if (action != null) {
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
                case profileChoice:
                    handleProfileChoiceRequest(pwmRequest, newUserBean);
                    return;

                case processForm:
                    handleProcessFormRequest(pwmRequest, newUserBean);
                    return;

                case validate:
                    restValidateForm(pwmRequest);
                    return;

                case enterCode:
                    handleEnterCodeRequest(pwmRequest, newUserBean);
                    break;

                case reset:
                    pwmSession.clearSessionBean(NewUserBean.class);
                    pwmRequest.sendRedirectToContinue();
                    break;

                case agree:
                    LOGGER.debug(pwmSession, "user accepted new-user agreement");
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

        if (newUserBean.getProfileID() == null) {
            final Set<String> newUserProfileIDs = pwmApplication.getConfig().getNewUserProfiles().keySet();
            if (newUserProfileIDs.isEmpty()) {
                pwmRequest.respondWithError(new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,"no new user profiles are defined"));
                return;
            }

            if (newUserProfileIDs.size() == 1) {
                final String singleID =  newUserProfileIDs.iterator().next();
                LOGGER.trace(pwmRequest, "only one new user profile is defined, auto-selecting profile " + singleID);
                newUserBean.setProfileID(singleID);
            } else {
                LOGGER.trace(pwmRequest, "new user profile not yet selected, redirecting to choice page");
                pwmRequest.forwardToJsp(PwmConstants.JSP_URL.NEW_USER_PROFILE_CHOICE);
                return;
            }
        }

        final NewUserProfile newUserProfile = getNewUserProfile(pwmRequest);

        // try to read the new user policy to make sure it's readable, that way an exception is thrown here instead of by the jsp
        newUserProfile.getNewUserPasswordPolicy(pwmApplication, pwmSession.getSessionStateBean().getLocale());//

        if (newUserBean.getNewUserForm() == null) {
            forwardToFormPage(pwmRequest);
            return;
        }

        if (newUserProfile.readSettingAsBoolean(PwmSetting.NEWUSER_EMAIL_VERIFICATION)) {
            if (!newUserBean.isEmailTokenIssued()) {
                initializeToken(pwmRequest, NewUserBean.NewUserVerificationPhase.EMAIL);
            }

            if (!newUserBean.isEmailTokenPassed()) {
                pwmRequest.forwardToJsp(PwmConstants.JSP_URL.NEW_USER_ENTER_CODE);
                return;
            }
        }

        if (newUserProfile.readSettingAsBoolean(PwmSetting.NEWUSER_SMS_VERIFICATION)) {
            if (!newUserBean.isSmsTokenIssued()) {
                initializeToken(pwmRequest, NewUserBean.NewUserVerificationPhase.EMAIL);
            }

            if (!newUserBean.isSmsTokenPassed()) {
                pwmRequest.forwardToJsp(PwmConstants.JSP_URL.NEW_USER_ENTER_CODE);
                return;
            }
        }

        final String newUserAgreementText = newUserProfile.readSettingAsLocalizedString(PwmSetting.NEWUSER_AGREEMENT_MESSAGE,
                pwmSession.getSessionStateBean().getLocale());
        if (newUserAgreementText != null && !newUserAgreementText.isEmpty()) {
            if (!newUserBean.isAgreementPassed()) {
                final MacroMachine macroMachine = createMacroMachineForNewUser(
                        pwmApplication,
                        pwmRequest.getSessionLabel(),
                        newUserBean.getNewUserForm()
                );
                final String expandedText = macroMachine.expandMacros(newUserAgreementText);
                pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.AgreementText, expandedText);
                pwmRequest.forwardToJsp(PwmConstants.JSP_URL.NEW_USER_AGREEMENT);
                return;
            }
        }

        if (!newUserBean.isFormPassed()) {
            forwardToFormPage(pwmRequest);
        }

        // success so create the new user.
        final String newUserDN = determineUserDN(pwmRequest, newUserBean.getNewUserForm());

        try {
            createUser(newUserBean.getNewUserForm(), pwmRequest, newUserDN);
            newUserBean.setCreateStartTime(new Date());
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.NEW_USER_WAIT);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmRequest, "error during user creation: " + e.getMessage());
            if (newUserProfile.readSettingAsBoolean(PwmSetting.NEWUSER_DELETE_ON_FAIL)) {
                deleteUserAccount(newUserDN, pwmSession, pwmApplication);
            }
            LOGGER.error(pwmSession, e.getErrorInformation().toDebugStr());
            pwmRequest.respondWithError(e.getErrorInformation());
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
            PasswordUtility.PasswordCheckInfo passwordCheckInfo = verifyForm(pwmRequest, newUserForm, true);
            if (passwordCheckInfo.isPassed() && passwordCheckInfo.getMatch() == PasswordUtility.PasswordCheckInfo.MATCH_STATUS.MATCH) {
                passwordCheckInfo = new PasswordUtility.PasswordCheckInfo(
                        Message.getLocalizedMessage(locale,
                                Message.Success_NewUserForm, pwmApplication.getConfig()),
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
            LOGGER.error(pwmRequest, "error while validating new user form: " + e.getMessage());
            pwmRequest.outputJsonResult(restResultBean);
        }
    }

    static PasswordUtility.PasswordCheckInfo verifyForm(
            final PwmRequest pwmRequest,
            final NewUserBean.NewUserForm newUserForm,
            final boolean allowResultCaching
    )
            throws PwmDataValidationException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final Locale locale = pwmRequest.getLocale();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        FormUtility.validateFormValues(pwmApplication.getConfig(), newUserForm.getFormData(), locale);
        FormUtility.validateFormValueUniqueness(
                pwmApplication,
                newUserForm.getFormData(),
                locale,
                Collections.<UserIdentity>emptyList(),
                allowResultCaching
        );
        final UserInfoBean uiBean = new UserInfoBean();
        final NewUserProfile newUserProfile = getNewUserProfile(pwmRequest);
        uiBean.setCachedPasswordRuleAttributes(FormUtility.asStringMap(newUserForm.getFormData()));
        uiBean.setPasswordPolicy(newUserProfile.getNewUserPasswordPolicy(pwmApplication, locale));
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
                    null,
                    userEnteredCode
            );
            if (tokenPayload != null) {
                final NewUserTokenData newUserTokenData = NewUserFormUtils.fromTokenPayload(pwmRequest, tokenPayload);
                newUserBean.setProfileID(newUserTokenData.profileID);
                final NewUserBean.NewUserForm newUserFormFromToken = newUserTokenData.formData;
                if (NewUserBean.NewUserVerificationPhase.EMAIL.getTokenName().equals(tokenPayload.getName())) {
                    LOGGER.debug(pwmRequest, "email token passed");

                    try {
                        verifyForm(pwmRequest, newUserFormFromToken, false);
                    } catch (PwmUnrecoverableException | PwmOperationalException e) {
                        LOGGER.error(pwmRequest,"while reading stored form data in token payload, form validation error occurred: " + e.getMessage());
                        throw e;
                    }

                    newUserBean.setNewUserForm(newUserFormFromToken);
                    newUserBean.setFormPassed(true);
                    newUserBean.setEmailTokenPassed(true);
                    newUserBean.setEmailTokenIssued(true);
                    newUserBean.setVerificationPhase(NewUserBean.NewUserVerificationPhase.NONE);
                    tokenPassed = true;
                } else if (NewUserBean.NewUserVerificationPhase.SMS.getTokenName().equals(tokenPayload.getName())) {
                    if (newUserBean.getNewUserForm() != null && newUserBean.getNewUserForm().isConsistentWith(newUserFormFromToken)) {
                        LOGGER.debug(pwmRequest, "SMS token passed");
                        newUserBean.setSmsTokenPassed(true);
                        newUserBean.setSmsTokenIssued(true);
                        newUserBean.setVerificationPhase(NewUserBean.NewUserVerificationPhase.NONE);
                        tokenPassed = true;
                    } else {
                        LOGGER.debug(pwmRequest, "SMS token value is valid, but form data does not match current session form data");
                        final String errorMsg = "sms token does not match current session";
                        errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,errorMsg);
                    }
                } else {
                    final String errorMsg = "token name/type is not recognized: " + tokenPayload.getName();
                    errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,errorMsg);
                }
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


    private void handleProfileChoiceRequest(final PwmRequest pwmRequest, final NewUserBean newUserBean)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException 
    {
        final Set<String> profileIDs = pwmRequest.getConfig().getNewUserProfiles().keySet();
        final String requestedProfileID = pwmRequest.readParameterAsString("profile");
        
        if (requestedProfileID == null || requestedProfileID.isEmpty()) {
            newUserBean.setProfileID(null);
        } if (profileIDs.contains(requestedProfileID)) {
            newUserBean.setProfileID(requestedProfileID);
        }
        
        this.advancedToNextStage(pwmRequest, newUserBean);
    }

    private void handleProcessFormRequest(final PwmRequest pwmRequest, final NewUserBean newUserBean)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        newUserBean.setFormPassed(false);
        newUserBean.setNewUserForm(null);

        try {
            NewUserBean.NewUserForm newUserForm = NewUserFormUtils.readFromRequest(pwmRequest);
            final PasswordUtility.PasswordCheckInfo passwordCheckInfo = verifyForm(pwmRequest, newUserForm, true);
            passwordCheckInfoToException(passwordCheckInfo);
            newUserBean.setNewUserForm(newUserForm);
            newUserBean.setFormPassed(true);
            this.advancedToNextStage(pwmRequest, newUserBean);
        } catch (PwmOperationalException e) {
            pwmRequest.setResponseError(e.getErrorInformation());
            forwardToFormPage(pwmRequest);
        }
    }


    private static void createUser(
            final NewUserBean.NewUserForm newUserForm,
            final PwmRequest pwmRequest,
            final String newUserDN
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        final long startTime = System.currentTimeMillis();

        // re-perform verification before proceeding
        {
            final PasswordUtility.PasswordCheckInfo passwordCheckInfo = verifyForm(
                    pwmRequest,
                    newUserForm,
                    false
            );
            passwordCheckInfoToException(passwordCheckInfo);
        }

        LOGGER.debug(pwmSession, "beginning createUser process for " + newUserDN);
        final PasswordData userPassword = newUserForm.getNewUserPassword();

        // set up the user creation attributes
        final Map<String, String> createAttributes = new LinkedHashMap<>();
        createAttributes.putAll(FormUtility.asStringMap(newUserForm.getFormData()));

        // read the creation object classes from configuration
        final Set<String> createObjectClasses = new LinkedHashSet<>(
                pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.DEFAULT_OBJECT_CLASSES));

        // add the auto-add object classes
        {
            final LdapProfile defaultLDAPProfile = pwmApplication.getConfig().getDefaultLdapProfile();
            createObjectClasses.addAll(defaultLDAPProfile.readSettingAsStringArray(PwmSetting.AUTO_ADD_OBJECT_CLASSES));
        }

        final ChaiProvider chaiProvider = pwmApplication.getConfig().getDefaultLdapProfile().getProxyChaiProvider(pwmApplication);
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
        final NewUserProfile newUserProfile = getNewUserProfile(pwmRequest);

        final boolean useTempPw;
        {
            final String settingValue = pwmApplication.getConfig().readAppProperty(AppProperty.NEWUSER_LDAP_USE_TEMP_PW);
            if ("auto".equalsIgnoreCase(settingValue)) {
                useTempPw = chaiProvider.getDirectoryVendor() == ChaiProvider.DIRECTORY_VENDOR.MICROSOFT_ACTIVE_DIRECTORY;
            } else {
                useTempPw = Boolean.parseBoolean(settingValue);
            }
        }

        if (useTempPw) {
            LOGGER.trace(pwmSession, "will use temporary password process for new user entry: " + newUserDN);
            final PasswordData temporaryPassword;
            {
                final RandomPasswordGenerator.RandomGeneratorConfig randomGeneratorConfig = new RandomPasswordGenerator.RandomGeneratorConfig();
                randomGeneratorConfig.setPasswordPolicy(newUserProfile.getNewUserPasswordPolicy(pwmApplication, pwmRequest.getLocale()));
                temporaryPassword = RandomPasswordGenerator.createRandomPassword(pwmSession.getLabel(), randomGeneratorConfig, pwmApplication);
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
        final UserIdentity userIdentity = new UserIdentity(newUserDN, pwmApplication.getConfig().getDefaultLdapProfile().getIdentifier());
        final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(pwmApplication, pwmSession);
        sessionAuthenticator.authenticateUser(userIdentity, userPassword);

        {  // execute configured actions
            final List<ActionConfiguration> actions = newUserProfile.readSettingAsAction(
                    PwmSetting.NEWUSER_WRITE_ATTRIBUTES);
            if (actions != null && !actions.isEmpty()) {
                LOGGER.debug(pwmSession, "executing configured actions to user " + theUser.getEntryDN());

                final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings(pwmApplication, userIdentity)
                        .setExpandPwmMacros(true)
                        .setMacroMachine(pwmSession.getSessionManager().getMacroMachine(pwmApplication))
                        .createActionExecutor();

                actionExecutor.executeActions(actions, pwmSession);
            }
        }

        // send user email
        sendNewUserEmailConfirmation(pwmRequest);


        // add audit record
        pwmApplication.getAuditManager().submit(AuditEvent.CREATE_USER, pwmSession.getUserInfoBean(), pwmSession);

        // increment the new user creation statistics
        pwmApplication.getStatisticsManager().incrementValue(Statistic.NEW_USERS);

        LOGGER.debug(pwmSession, "completed createUser process for " + newUserDN + " (" + TimeDuration.fromCurrent(
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
            pwmApplication.getConfig().getDefaultLdapProfile().getProxyChaiProvider(pwmApplication).deleteEntry(userDN);
            LOGGER.warn(pwmSession, "ldap user account " + userDN + " has been deleted");
        } catch (ChaiUnavailableException | ChaiOperationException e) {
            LOGGER.error(pwmSession, "error deleting ldap user account " + userDN + ", " + e.getMessage());
        }

        pwmSession.unauthenticateUser();
    }

    private static String determineUserDN(
            final PwmRequest pwmRequest,
            final NewUserBean.NewUserForm formValues
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final MacroMachine macroMachine = createMacroMachineForNewUser(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel(), formValues);
        final NewUserProfile newUserProfile = getNewUserProfile(pwmRequest);
        final List<String> configuredNames = newUserProfile.readSettingAsStringArray(PwmSetting.NEWUSER_USERNAME_DEFINITION);
        final List<String> failedValues = new ArrayList<>();

        final String configuredContext = newUserProfile.readSettingAsString(PwmSetting.NEWUSER_CONTEXT);
        final String expandedContext = macroMachine.expandMacros(configuredContext);


        if (configuredNames == null || configuredNames.isEmpty() || configuredNames.iterator().next().isEmpty()) {
            final String namingAttribute = pwmRequest.getConfig().getDefaultLdapProfile().readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
            String namingValue = null;
            for (final FormConfiguration formConfiguration : formValues.getFormData().keySet()) {
                if (formConfiguration.getName().equals(namingAttribute)) {
                    namingValue = formValues.getFormData().get(formConfiguration);
                }
            }
            if (namingValue == null || namingValue.isEmpty()) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,
                        "username definition not set, and naming attribute is not present in form"));
            }
            final String escapedName = StringUtil.escapeLdapDN(namingValue);
            final String generatedDN = namingAttribute + "=" + escapedName + "," + expandedContext;
            LOGGER.debug(pwmRequest, "generated dn for new user: " + generatedDN);
            return generatedDN;
        }

        int attemptCount = 0;
        String generatedDN;
        while (attemptCount < configuredNames.size()) {
            final String expandedName;
            {
                {
                    final String configuredName = configuredNames.get(attemptCount);
                    expandedName = macroMachine.expandMacros(configuredName);
                }

                if (!testIfEntryNameExists(pwmRequest, expandedName)) {
                    LOGGER.trace(pwmRequest, "generated entry name for new user is unique: " + expandedName);
                    final String namingAttribute = pwmRequest.getConfig().getDefaultLdapProfile().readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
                    final String escapedName = StringUtil.escapeLdapDN(expandedName);
                    generatedDN = namingAttribute + "=" + escapedName + "," + expandedContext;
                    LOGGER.debug(pwmRequest, "generated dn for new user: " + generatedDN);
                    return generatedDN;
                } else {
                    failedValues.add(expandedName);
                }
            }

            LOGGER.debug(pwmRequest, "generated entry name for new user is not unique, will try again");
            attemptCount++;
        }
        LOGGER.error(pwmRequest,
                "failed to generate new user DN after " + attemptCount + " attempts, failed values: " + JsonUtil.serializeCollection(
                        failedValues));
        throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,
                "unable to generate a unique DN value"));
    }

    private static boolean testIfEntryNameExists(
            final PwmRequest pwmRequest,
            final String rdnValue
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmRequest);
        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        searchConfiguration.setUsername(rdnValue);
        try {
            Map<UserIdentity, Map<String, String>> results = userSearchEngine.performMultiUserSearch(
                    searchConfiguration, 2, Collections.<String>emptyList());
            return results != null && !results.isEmpty();
        } catch (PwmOperationalException e) {
            final String msg = "ldap error while searching for duplicate entry names: " + e.getMessage();
            LOGGER.error(pwmRequest, msg);
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE, msg));
        }
    }

    private static void sendNewUserEmailConfirmation(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
        final Configuration config = pwmRequest.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_NEWUSER, locale);

        if (configuredEmailSetting == null) {
            LOGGER.debug(pwmSession,
                    "skipping send of new user email for '" + userInfoBean.getUserIdentity().getUserDN() + "' no email configured");
            return;
        }

        pwmRequest.getPwmApplication().getEmailQueue().submitEmail(
                configuredEmailSetting,
                pwmSession.getUserInfoBean(),
                pwmSession.getSessionManager().getMacroMachine(pwmRequest.getPwmApplication())
        );
    }

    public void initializeToken(
            final PwmRequest pwmRequest,
            final NewUserBean.NewUserVerificationPhase phase
    )
            throws PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        
        if (pwmApplication.getConfig().getTokenStorageMethod() == TokenStorageMethod.STORE_LDAP) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,null,new String[]{
                    "cannot generate new user tokens when storage type is configured as STORE_LDAP."}));
        }

        final NewUserBean newUserBean = pwmRequest.getPwmSession().getNewUserBean();
        final Configuration config = pwmApplication.getConfig();
        final Map<String, String> tokenPayloadMap = NewUserFormUtils.toTokenPayload(pwmRequest, newUserBean.getNewUserForm());
        final MacroMachine macroMachine = createMacroMachineForNewUser(pwmApplication, pwmRequest.getSessionLabel(), newUserBean.getNewUserForm());

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

        final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile(pwmRequest);
        final long minWaitTime = newUserProfile.readSettingAsLong(PwmSetting.NEWUSER_MINIMUM_WAIT_TIME) * 1000L;
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

        final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile(pwmRequest);
        final long minWaitTime = newUserProfile.readSettingAsLong(PwmSetting.NEWUSER_MINIMUM_WAIT_TIME) * 1000L;
        final Date completeTime = new Date(startTime.getTime() + minWaitTime);

        // be sure minimum wait time has passed
        if (new Date().before(completeTime)) {
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.NEW_USER_WAIT);
            return;
        }

        pwmRequest.getPwmSession().clearSessionBean(NewUserBean.class);
        pwmRequest.forwardToSuccessPage(Message.Success_CreateUser);
    }

    private static MacroMachine createMacroMachineForNewUser(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final NewUserBean.NewUserForm newUserForm
    )
            throws PwmUnrecoverableException
    {
        final Map<String, String> formValues = FormUtility.asStringMap(newUserForm.getFormData());
        final UserInfoBean stubUserBean = new UserInfoBean();

        final String emailAddressAttribute = pwmApplication.getConfig().readSettingAsString(
                PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE);
        stubUserBean.setUserEmailAddress(formValues.get(emailAddressAttribute));

        final String usernameAttribute = pwmApplication.getConfig().getDefaultLdapProfile().readSettingAsString(PwmSetting.LDAP_USERNAME_ATTRIBUTE);
        stubUserBean.setUsername(formValues.get(usernameAttribute));

        final LoginInfoBean stubLoginBean = new LoginInfoBean();
        stubLoginBean.setUserCurrentPassword(newUserForm.getNewUserPassword());

        final UserDataReader stubReader = new NewUserUserDataReader(formValues);
        return new MacroMachine(pwmApplication, sessionLabel, stubUserBean, stubLoginBean, stubReader);
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

    static List<FormConfiguration> getFormDefinition(PwmRequest pwmRequest) {
        final NewUserProfile profile = getNewUserProfile(pwmRequest);
        return profile.readSettingAsForm(PwmSetting.NEWUSER_FORM);
    }

    public static class NewUserTokenData {
        private String profileID;
        private NewUserBean.NewUserForm formData;

        public NewUserTokenData(String profileID, NewUserBean.NewUserForm formData) {
            this.profileID = profileID;
            this.formData = formData;
        }
    }
    
    private static class NewUserFormUtils {
        
        
        static NewUserBean.NewUserForm readFromRequest(PwmRequest pwmRequest)
                throws PwmDataValidationException, PwmUnrecoverableException
        {
            final Locale userLocale = pwmRequest.getLocale();
            final List<FormConfiguration> newUserForm = getFormDefinition(pwmRequest);
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
            final List<FormConfiguration> newUserForm = getFormDefinition(pwmRequest);
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

        static NewUserTokenData fromTokenPayload(
                final PwmRequest pwmRequest,
                final TokenPayload tokenPayload
        )
                throws PwmOperationalException, PwmUnrecoverableException
        {
            final Locale userLocale = pwmRequest.getLocale();

            final Map<String, String> payloadMap = tokenPayload.getData();
            final String profileID = payloadMap.get(TOKEN_PAYLOAD_ATTR);
            payloadMap.remove(TOKEN_PAYLOAD_ATTR);

            final NewUserProfile newUserProfile = pwmRequest.getConfig().getNewUserProfiles().get(profileID);
            if (newUserProfile == null) {
                throw new PwmOperationalException(PwmError.ERROR_TOKEN_INCORRECT,"token data references an invalid new user profileID");
            }

            final List<FormConfiguration> newUserFormDefinition = newUserProfile.readSettingAsForm(PwmSetting.NEWUSER_FORM);
            final Map<FormConfiguration, String> userFormValues = FormUtility.readFormValuesFromMap(payloadMap,
                    newUserFormDefinition, userLocale);
            final PasswordData passwordData;
            if (payloadMap.containsKey(FIELD_PASSWORD1)) {
                final String rawPassword = payloadMap.get(FIELD_PASSWORD1);
                final String realPassword = SecureHelper.decryptStringValue(rawPassword,
                        pwmRequest.getConfig().getSecurityKey(), true);
                passwordData = new PasswordData(realPassword);
            } else {
                passwordData = null;
            }
            final NewUserBean.NewUserForm newUserForm = new NewUserBean.NewUserForm(userFormValues, passwordData, passwordData);
            return new NewUserTokenData(profileID,newUserForm);
        }

        static Map<String, String> toTokenPayload(
                final PwmRequest pwmRequest,
                NewUserBean.NewUserForm newUserForm
        )
                throws PwmUnrecoverableException
        {
            final Map<String, String> payloadMap = new LinkedHashMap<>();
            payloadMap.put(TOKEN_PAYLOAD_ATTR, pwmRequest.getPwmSession().getNewUserBean().getProfileID());
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
    
    public static NewUserProfile getNewUserProfile(final PwmRequest pwmRequest) {
        final String profileID = pwmRequest.getPwmSession().getNewUserBean().getProfileID();
        if (profileID == null) {
            throw new IllegalStateException("can not read new user profile until profile is selected");
        }
        return pwmRequest.getConfig().getNewUserProfiles().get(profileID);
    }
    
    void forwardToFormPage(final PwmRequest pwmRequest) 
            throws ServletException, PwmUnrecoverableException, IOException 
    {
        final List<FormConfiguration> formConfiguration = getFormDefinition(pwmRequest);
        pwmRequest.addFormInfoToRequestAttr(formConfiguration, null, false, true);
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.NEW_USER);
    }
}
