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

package password.pwm.http.servlet.newuser;

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
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.TokenVerificationProgress;
import password.pwm.bean.UserIdentity;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.TokenStorageMethod;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.NewUserBean;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoBean;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.token.TokenPayload;
import password.pwm.svc.token.TokenService;
import password.pwm.util.PasswordData;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.ValueObfuscator;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.ws.client.rest.RestTokenDataClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

class NewUserUtils {
    private static PwmLogger LOGGER = password.pwm.util.logging.PwmLogger.forClass(NewUserUtils.class);

    private NewUserUtils() {
    }


    static void passwordCheckInfoToException(final PasswordUtility.PasswordCheckInfo passwordCheckInfo)
            throws PwmOperationalException
    {
        if (!passwordCheckInfo.isPassed()) {
            final ErrorInformation errorInformation = PwmError.forErrorNumber(passwordCheckInfo.getErrorCode()).toInfo();
            throw new PwmOperationalException(errorInformation);
        }
        if (passwordCheckInfo.getMatch() != PasswordUtility.PasswordCheckInfo.MatchStatus.MATCH) {
            final ErrorInformation errorInformation = PwmError.PASSWORD_DOESNOTMATCH.toInfo();
            throw new PwmOperationalException(errorInformation);
        }

    }

    static void createUser(
            final NewUserForm newUserForm,
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
            final PasswordUtility.PasswordCheckInfo passwordCheckInfo = NewUserServlet.verifyForm(
                    pwmRequest,
                    newUserForm,
                    false
            );
            passwordCheckInfoToException(passwordCheckInfo);
        }

        NewUserUtils.LOGGER.debug(pwmSession, "beginning createUser process for " + newUserDN);

        final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile(pwmRequest);
        final boolean promptForPassword = newUserProfile.readSettingAsBoolean(PwmSetting.NEWUSER_PROMPT_FOR_PASSWORD);

        final PasswordData userPassword;
        if (promptForPassword) {
            userPassword = newUserForm.getNewUserPassword();
        } else {
            final PwmPasswordPolicy pwmPasswordPolicy = newUserProfile.getNewUserPasswordPolicy(pwmRequest.getPwmApplication(), pwmRequest.getLocale());
            userPassword = RandomPasswordGenerator.createRandomPassword(pwmRequest.getSessionLabel(), pwmPasswordPolicy, pwmRequest.getPwmApplication());
        }

        // set up the user creation attributes
        final Map<String, String> createAttributes = NewUserFormUtils.getLdapDataFromNewUserForm(NewUserServlet.getNewUserProfile(pwmRequest), newUserForm);

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

            NewUserUtils.LOGGER.info(pwmSession, "created user entry: " + newUserDN);
        } catch (ChaiOperationException e) {
            final String userMessage = "unexpected ldap error creating user entry: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,
                    userMessage);
            throw new PwmOperationalException(errorInformation);
        }

        final ChaiUser theUser = ChaiFactory.createChaiUser(newUserDN, chaiProvider);

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
            NewUserUtils.LOGGER.trace(pwmSession, "will use temporary password process for new user entry: " + newUserDN);
            final PasswordData temporaryPassword;
            {
                final RandomPasswordGenerator.RandomGeneratorConfig randomGeneratorConfig = RandomPasswordGenerator.RandomGeneratorConfig.builder()
                        .passwordPolicy(newUserProfile.getNewUserPasswordPolicy(pwmApplication, pwmRequest.getLocale()))
                        .build();
                temporaryPassword = RandomPasswordGenerator.createRandomPassword(pwmSession.getLabel(), randomGeneratorConfig, pwmApplication);
            }
            final ChaiUser proxiedUser = ChaiFactory.createChaiUser(newUserDN, chaiProvider);
            try { //set password as admin
                proxiedUser.setPassword(temporaryPassword.getStringValue());
                NewUserUtils.LOGGER.debug(pwmSession, "set temporary password for new user entry: " + newUserDN);
            } catch (ChaiOperationException e) {
                final String userMessage = "unexpected ldap error setting temporary password for new user entry: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,
                        userMessage);
                throw new PwmOperationalException(errorInformation);
            }

            // add AD-specific attributes
            if (ChaiProvider.DIRECTORY_VENDOR.MICROSOFT_ACTIVE_DIRECTORY == chaiProvider.getDirectoryVendor()) {
                try {
                    NewUserUtils.LOGGER.debug(pwmSession,
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
                NewUserUtils.LOGGER.debug(pwmSession,
                        "attempting bind as user to then allow changing to requested password for new user entry: " + newUserDN);
                final ChaiConfiguration chaiConfiguration = new ChaiConfiguration(chaiProvider.getChaiConfiguration());
                chaiConfiguration.setSetting(ChaiSetting.BIND_DN, newUserDN);
                chaiConfiguration.setSetting(ChaiSetting.BIND_PASSWORD, temporaryPassword.getStringValue());
                final ChaiProvider bindAsProvider = ChaiProviderFactory.createProvider(chaiConfiguration);
                final ChaiUser bindAsUser = ChaiFactory.createChaiUser(newUserDN, bindAsProvider);
                bindAsUser.changePassword(temporaryPassword.getStringValue(), userPassword.getStringValue());
                NewUserUtils.LOGGER.debug(pwmSession, "changed to user requested password for new user entry: " + newUserDN);
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
                NewUserUtils.LOGGER.debug(pwmSession, "set user requested password for new user entry: " + newUserDN);
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

        NewUserUtils.LOGGER.trace(pwmSession, "new user ldap creation process complete, now authenticating user");

        //authenticate the user to pwm
        final UserIdentity userIdentity = new UserIdentity(newUserDN, pwmApplication.getConfig().getDefaultLdapProfile().getIdentifier());
        final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(pwmApplication, pwmSession, PwmAuthenticationSource.NEW_USER_REGISTRATION);
        sessionAuthenticator.authenticateUser(userIdentity, userPassword);

        {  // execute configured actions
            final List<ActionConfiguration> actions = newUserProfile.readSettingAsAction(
                    PwmSetting.NEWUSER_WRITE_ATTRIBUTES);
            if (actions != null && !actions.isEmpty()) {
                NewUserUtils.LOGGER.debug(pwmSession, "executing configured actions to user " + theUser.getEntryDN());

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
        pwmApplication.getAuditManager().submit(AuditEvent.CREATE_USER, pwmSession.getUserInfo(), pwmSession);

        // increment the new user creation statistics
        pwmApplication.getStatisticsManager().incrementValue(Statistic.NEW_USERS);

        NewUserUtils.LOGGER.debug(pwmSession, "completed createUser process for " + newUserDN + " (" + TimeDuration.fromCurrent(
                startTime).asCompactString() + ")");
    }

    static void deleteUserAccount(
            final String userDN,
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        try {
            NewUserUtils.LOGGER.warn(pwmRequest, "deleting ldap user account " + userDN);
            pwmRequest.getConfig().getDefaultLdapProfile().getProxyChaiProvider(pwmRequest.getPwmApplication()).deleteEntry(userDN);
            NewUserUtils.LOGGER.warn(pwmRequest, "ldap user account " + userDN + " has been deleted");
        } catch (ChaiUnavailableException | ChaiOperationException e) {
            NewUserUtils.LOGGER.error(pwmRequest, "error deleting ldap user account " + userDN + ", " + e.getMessage());
        }

        pwmRequest.getPwmSession().unauthenticateUser(pwmRequest);
    }

    static String determineUserDN(
            final PwmRequest pwmRequest,
            final NewUserForm formValues
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final MacroMachine macroMachine = createMacroMachineForNewUser(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel(), formValues);
        final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile(pwmRequest);
        final List<String> configuredNames = newUserProfile.readSettingAsStringArray(PwmSetting.NEWUSER_USERNAME_DEFINITION);
        final List<String> failedValues = new ArrayList<>();

        final String configuredContext = newUserProfile.readSettingAsString(PwmSetting.NEWUSER_CONTEXT);
        final String expandedContext = macroMachine.expandMacros(configuredContext);


        if (configuredNames == null || configuredNames.isEmpty() || configuredNames.iterator().next().isEmpty()) {
            final String namingAttribute = pwmRequest.getConfig().getDefaultLdapProfile().readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
            String namingValue = null;
            for (final String formKey : formValues.getFormData().keySet()) {
                if (formKey.equals(namingAttribute)) {
                    namingValue = formValues.getFormData().get(formKey);
                }
            }
            if (namingValue == null || namingValue.isEmpty()) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE,
                        "username definition not set, and naming attribute is not present in form"));
            }
            final String escapedName = StringUtil.escapeLdapDN(namingValue);
            final String generatedDN = namingAttribute + "=" + escapedName + "," + expandedContext;
            NewUserUtils.LOGGER.debug(pwmRequest, "generated dn for new user: " + generatedDN);
            return generatedDN;
        }

        int attemptCount = 0;
        final String generatedDN;
        while (attemptCount < configuredNames.size()) {
            final String expandedName;
            {
                {
                    final String configuredName = configuredNames.get(attemptCount);
                    expandedName = macroMachine.expandMacros(configuredName);
                }

                if (!testIfEntryNameExists(pwmRequest, expandedName)) {
                    NewUserUtils.LOGGER.trace(pwmRequest, "generated entry name for new user is unique: " + expandedName);
                    final String namingAttribute = pwmRequest.getConfig().getDefaultLdapProfile().readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
                    final String escapedName = StringUtil.escapeLdapDN(expandedName);
                    generatedDN = namingAttribute + "=" + escapedName + "," + expandedContext;
                    NewUserUtils.LOGGER.debug(pwmRequest, "generated dn for new user: " + generatedDN);
                    return generatedDN;
                } else {
                    failedValues.add(expandedName);
                }
            }

            NewUserUtils.LOGGER.debug(pwmRequest, "generated entry name for new user is not unique, will try again");
            attemptCount++;
        }
        NewUserUtils.LOGGER.error(pwmRequest,
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
        final UserSearchEngine userSearchEngine = pwmRequest.getPwmApplication().getUserSearchEngine();
        final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                .username(rdnValue)
                .build();

        try {
            final Map<UserIdentity, Map<String, String>> results = userSearchEngine.performMultiUserSearch(
                    searchConfiguration, 2, Collections.emptyList(), pwmRequest.getSessionLabel());
            return results != null && !results.isEmpty();
        } catch (PwmOperationalException e) {
            final String msg = "ldap error while searching for duplicate entry names: " + e.getMessage();
            NewUserUtils.LOGGER.error(pwmRequest, msg);
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE, msg));
        }
    }

    private static void sendNewUserEmailConfirmation(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final UserInfo userInfo = pwmSession.getUserInfo();
        final Configuration config = pwmRequest.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_NEWUSER, locale);

        if (configuredEmailSetting == null) {
            NewUserUtils.LOGGER.debug(pwmSession,
                    "skipping send of new user email for '" + userInfo.getUserIdentity().getUserDN() + "' no email configured");
            return;
        }

        pwmRequest.getPwmApplication().getEmailQueue().submitEmail(
                configuredEmailSetting,
                pwmSession.getUserInfo(),
                pwmSession.getSessionManager().getMacroMachine(pwmRequest.getPwmApplication())
        );
    }

    static MacroMachine createMacroMachineForNewUser(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final NewUserForm newUserForm
    )
            throws PwmUnrecoverableException
    {
        final Map<String, String> formValues = newUserForm.getFormData();

        final String emailAddressAttribute = pwmApplication.getConfig().getDefaultLdapProfile().readSettingAsString(
                PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE);

        final String usernameAttribute = pwmApplication.getConfig().getDefaultLdapProfile().readSettingAsString(PwmSetting.LDAP_USERNAME_ATTRIBUTE);

        final LoginInfoBean stubLoginBean = new LoginInfoBean();
        stubLoginBean.setUserCurrentPassword(newUserForm.getNewUserPassword());

        final UserInfoBean stubUserBean = UserInfoBean.builder()
                .userEmailAddress(formValues.get(emailAddressAttribute))
                .username(formValues.get(usernameAttribute))
                .attributes(formValues)
                .build();

        return new MacroMachine(pwmApplication, sessionLabel, stubUserBean, stubLoginBean);
    }

    static void initializeToken(
            final PwmRequest pwmRequest,
            final NewUserBean newUserBean,
            final TokenVerificationProgress.TokenChannel tokenType
    )
            throws PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        if (pwmApplication.getConfig().getTokenStorageMethod() == TokenStorageMethod.STORE_LDAP) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,null,new String[]{
                    "cannot generate new user tokens when storage type is configured as STORE_LDAP.",
            }));
        }

        final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile(pwmRequest);
        final Configuration config = pwmApplication.getConfig();
        final Map<String, String> tokenPayloadMap = NewUserFormUtils.toTokenPayload(pwmRequest, newUserBean);
        final MacroMachine macroMachine = createMacroMachineForNewUser(pwmApplication, pwmRequest.getSessionLabel(), newUserBean.getNewUserForm());

        switch (tokenType) {
            case SMS: {
                final String toNum = tokenPayloadMap.get(pwmApplication.getConfig().getDefaultLdapProfile().readSettingAsString(PwmSetting.SMS_USER_PHONE_ATTRIBUTE));

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
                            password.pwm.svc.token.TokenType.NEWUSER_SMS,
                            newUserProfile.getTokenDurationSMS(config),
                            tokenPayloadMap,
                            null,
                            Collections.singleton(outputDestTokenData.getSms())
                    );
                    tokenKey = pwmApplication.getTokenService().generateNewToken(tokenPayload,
                            pwmRequest.getSessionLabel());
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

                newUserBean.getTokenVerificationProgress().getIssuedTokens().add(TokenVerificationProgress.TokenChannel.SMS);
                final ValueObfuscator valueObfuscator = new ValueObfuscator(pwmApplication.getConfig());
                newUserBean.getTokenVerificationProgress().setTokenDisplayText(valueObfuscator.maskPhone(toNum));
                newUserBean.getTokenVerificationProgress().setPhase(TokenVerificationProgress.TokenChannel.SMS);
            }
            break;

            case EMAIL: {
                final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(
                        PwmSetting.EMAIL_NEWUSER_VERIFICATION, pwmSession.getSessionStateBean().getLocale());
                final String toAddress = macroMachine.expandMacros(configuredEmailSetting.getTo());

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
                            password.pwm.svc.token.TokenType.NEWUSER_EMAIL,
                            newUserProfile.getTokenDurationEmail(config),
                            tokenPayloadMap,
                            null,
                            Collections.singleton(outputDestTokenData.getEmail())
                    );
                    tokenKey = pwmApplication.getTokenService().generateNewToken(tokenPayload,
                            pwmRequest.getSessionLabel());
                } catch (PwmOperationalException e) {
                    throw new PwmUnrecoverableException(e.getErrorInformation());
                }

                newUserBean.getTokenVerificationProgress().getIssuedTokens().add(TokenVerificationProgress.TokenChannel.EMAIL);
                newUserBean.getTokenVerificationProgress().setPhase(TokenVerificationProgress.TokenChannel.EMAIL);
                final ValueObfuscator valueObfuscator = new ValueObfuscator(pwmApplication.getConfig());
                newUserBean.getTokenVerificationProgress().setTokenDisplayText(valueObfuscator.maskEmail(toAddress));

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
                newUserBean.getTokenVerificationProgress().setPhase(null);
                JavaHelper.unhandledSwitchStatement(tokenType);
        }
    }

    static Map<String,String> figureDisplayableProfiles(final PwmRequest pwmRequest) {
        final Map<String,String> returnMap = new LinkedHashMap<>();
        for (final NewUserProfile newUserProfile : pwmRequest.getConfig().getNewUserProfiles().values()) {
            final boolean visible = newUserProfile.readSettingAsBoolean(PwmSetting.NEWUSER_PROFILE_DISPLAY_VISIBLE);
            if (visible) {
                returnMap.put(newUserProfile.getIdentifier(), newUserProfile.getDisplayName(pwmRequest.getLocale()));
            }
        }
        return Collections.unmodifiableMap(returnMap);
    }
}
