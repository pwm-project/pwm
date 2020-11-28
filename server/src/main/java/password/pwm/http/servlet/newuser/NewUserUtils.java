/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.http.servlet.newuser;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiSetting;
import com.novell.ldapchai.provider.DirectoryVendor;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.VerificationMethodSystem;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.TokenStorageMethod;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.NewUserBean;
import password.pwm.http.servlet.forgottenpw.RemoteVerificationMethod;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoBean;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.token.TokenType;
import password.pwm.svc.token.TokenUtil;
import password.pwm.util.PasswordData;
import password.pwm.util.form.FormUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.macro.MacroReplacer;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.password.PasswordUtility;
import password.pwm.util.password.RandomPasswordGenerator;
import password.pwm.ws.client.rest.form.FormDataRequestBean;
import password.pwm.ws.client.rest.form.FormDataResponseBean;
import password.pwm.ws.client.rest.form.RestFormDataClient;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

class NewUserUtils
{
    private static final PwmLogger LOGGER = password.pwm.util.logging.PwmLogger.forClass( NewUserUtils.class );

    private NewUserUtils( )
    {
    }


    static void passwordCheckInfoToException( final PasswordUtility.PasswordCheckInfo passwordCheckInfo )
            throws PwmOperationalException
    {
        if ( !passwordCheckInfo.isPassed() )
        {
            final ErrorInformation errorInformation = PwmError.forErrorNumber( passwordCheckInfo.getErrorCode() ).toInfo();
            throw new PwmOperationalException( errorInformation );
        }
        if ( passwordCheckInfo.getMatch() != PasswordUtility.PasswordCheckInfo.MatchStatus.MATCH )
        {
            final ErrorInformation errorInformation = PwmError.PASSWORD_DOESNOTMATCH.toInfo();
            throw new PwmOperationalException( errorInformation );
        }

    }

    @SuppressWarnings( "checkstyle:MethodLength" )
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
            passwordCheckInfoToException( passwordCheckInfo );
        }

        NewUserUtils.LOGGER.debug( pwmRequest, () -> "beginning createUser process for " + newUserDN );

        final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile( pwmRequest );
        final boolean promptForPassword = newUserProfile.readSettingAsBoolean( PwmSetting.NEWUSER_PROMPT_FOR_PASSWORD );

        final PasswordData userPassword;
        if ( promptForPassword )
        {
            userPassword = newUserForm.getNewUserPassword();
        }
        else
        {
            final PwmPasswordPolicy pwmPasswordPolicy = newUserProfile.getNewUserPasswordPolicy( pwmRequest.getPwmApplication(), pwmRequest.getLocale() );
            userPassword = RandomPasswordGenerator.createRandomPassword( pwmRequest.getLabel(), pwmPasswordPolicy, pwmRequest.getPwmApplication() );
        }

        // set up the user creation attributes
        final Map<String, String> createAttributes = NewUserFormUtils.getLdapDataFromNewUserForm( NewUserServlet.getNewUserProfile( pwmRequest ), newUserForm );

        // read the creation object classes from configuration
        final Set<String> createObjectClasses = new LinkedHashSet<>(
                pwmApplication.getConfig().readSettingAsStringArray( PwmSetting.DEFAULT_OBJECT_CLASSES ) );

        // add the auto-add object classes
        {
            final LdapProfile defaultLDAPProfile = newUserProfile.getLdapProfile();
            createObjectClasses.addAll( defaultLDAPProfile.readSettingAsStringArray( PwmSetting.AUTO_ADD_OBJECT_CLASSES ) );
        }

        final ChaiProvider chaiProvider = newUserProfile.getLdapProfile().getProxyChaiProvider( pwmApplication );
        try
        {
            // create the ldap entry
            chaiProvider.createEntry( newUserDN, createObjectClasses, createAttributes );

            NewUserUtils.LOGGER.info( pwmRequest, () -> "created user entry: " + newUserDN );
        }
        catch ( final ChaiOperationException e )
        {
            final String userMessage = "unexpected ldap error creating user entry: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NEW_USER_FAILURE,
                    userMessage );
            throw new PwmOperationalException( errorInformation );
        }

        final ChaiUser theUser = chaiProvider.getEntryFactory().newChaiUser( newUserDN );

        final boolean useTempPw;
        {
            final String settingValue = pwmApplication.getConfig().readAppProperty( AppProperty.NEWUSER_LDAP_USE_TEMP_PW );
            if ( "auto".equalsIgnoreCase( settingValue ) )
            {
                useTempPw = chaiProvider.getDirectoryVendor() == DirectoryVendor.ACTIVE_DIRECTORY;
            }
            else
            {
                useTempPw = Boolean.parseBoolean( settingValue );
            }
        }

        if ( useTempPw )
        {
            NewUserUtils.LOGGER.trace( pwmRequest, () -> "will use temporary password process for new user entry: " + newUserDN );
            final PasswordData temporaryPassword;
            {
                final RandomPasswordGenerator.RandomGeneratorConfig randomGeneratorConfig = RandomPasswordGenerator.RandomGeneratorConfig.builder()
                        .passwordPolicy( newUserProfile.getNewUserPasswordPolicy( pwmApplication, pwmRequest.getLocale() ) )
                        .build();
                temporaryPassword = RandomPasswordGenerator.createRandomPassword( pwmRequest.getLabel(), randomGeneratorConfig, pwmApplication );
            }
            final ChaiUser proxiedUser = chaiProvider.getEntryFactory().newChaiUser( newUserDN );
            try
            {
                //set password as admin
                proxiedUser.setPassword( temporaryPassword.getStringValue() );
                NewUserUtils.LOGGER.debug( pwmRequest, () -> "set temporary password for new user entry: " + newUserDN );
            }
            catch ( final ChaiOperationException e )
            {
                final String userMessage = "unexpected ldap error setting temporary password for new user entry: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NEW_USER_FAILURE,
                        userMessage );
                throw new PwmOperationalException( errorInformation );
            }

            // add AD-specific attributes
            if ( DirectoryVendor.ACTIVE_DIRECTORY == chaiProvider.getDirectoryVendor() )
            {
                try
                {
                    NewUserUtils.LOGGER.debug( pwmRequest, () ->
                            "setting userAccountControl attribute to enable account " + theUser.getEntryDN() );
                    theUser.writeStringAttribute( "userAccountControl", "512" );
                }
                catch ( final ChaiOperationException e )
                {
                    final String errorMsg = "error enabling AD account when writing userAccountControl attribute: " + e.getMessage();
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NEW_USER_FAILURE,
                            errorMsg );
                    throw new PwmOperationalException( errorInformation );
                }
            }

            try
            {
                // bind as user
                NewUserUtils.LOGGER.debug( pwmRequest, () ->
                        "attempting bind as user to then allow changing to requested password for new user entry: " + newUserDN );
                final ChaiConfiguration chaiConfiguration = ChaiConfiguration.builder( chaiProvider.getChaiConfiguration() )
                        .setSetting( ChaiSetting.BIND_DN, newUserDN )
                        .setSetting( ChaiSetting.BIND_PASSWORD, temporaryPassword.getStringValue() )
                        .build();
                final ChaiProvider bindAsProvider = pwmApplication.getLdapConnectionService().getChaiProviderFactory().newProvider( chaiConfiguration );
                final ChaiUser bindAsUser = bindAsProvider.getEntryFactory().newChaiUser( newUserDN );
                bindAsUser.changePassword( temporaryPassword.getStringValue(), userPassword.getStringValue() );
                NewUserUtils.LOGGER.debug( pwmRequest, () -> "changed to user requested password for new user entry: " + newUserDN );
                bindAsProvider.close();
            }
            catch ( final ChaiOperationException e )
            {
                final String userMessage = "unexpected ldap error setting user password for new user entry: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NEW_USER_FAILURE,
                        userMessage );
                throw new PwmOperationalException( errorInformation );
            }
        }
        else
        {
            try
            {
                //set password
                theUser.setPassword( userPassword.getStringValue() );
                NewUserUtils.LOGGER.debug( pwmRequest, () -> "set user requested password for new user entry: " + newUserDN );
            }
            catch ( final ChaiOperationException e )
            {
                final String userMessage = "unexpected ldap error setting password for new user entry: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NEW_USER_FAILURE,
                        userMessage );
                throw new PwmOperationalException( errorInformation );
            }

            // add AD-specific attributes
            if ( DirectoryVendor.ACTIVE_DIRECTORY == chaiProvider.getDirectoryVendor() )
            {
                try
                {
                    theUser.writeStringAttribute( "userAccountControl", "512" );
                }
                catch ( final ChaiOperationException e )
                {
                    final String errorMsg = "error enabling AD account when writing userAccountControl attribute: " + e.getMessage();
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NEW_USER_FAILURE,
                            errorMsg );
                    throw new PwmOperationalException( errorInformation );
                }
            }
        }

        NewUserUtils.LOGGER.trace( pwmRequest, () -> "new user ldap creation process complete, now authenticating user" );

        // write data to remote web service
        remoteWriteFormData( pwmRequest, newUserForm );

        // authenticate the user to pwm
        final UserIdentity userIdentity = UserIdentity.createUserIdentity( newUserDN, newUserProfile.getLdapProfile().getIdentifier() );
        final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator( pwmApplication, pwmRequest, PwmAuthenticationSource.NEW_USER_REGISTRATION );
        sessionAuthenticator.authenticateUser( userIdentity, userPassword );

        {
            // execute configured actions
            final List<ActionConfiguration> actions = newUserProfile.readSettingAsAction(
                    PwmSetting.NEWUSER_WRITE_ATTRIBUTES );
            if ( actions != null && !actions.isEmpty() )
            {
                NewUserUtils.LOGGER.debug( pwmRequest, () -> "executing configured actions to user " + theUser.getEntryDN() );

                final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings( pwmApplication, userIdentity )
                        .setExpandPwmMacros( true )
                        .setMacroMachine( pwmSession.getSessionManager().getMacroMachine( ) )
                        .createActionExecutor();

                actionExecutor.executeActions( actions, pwmRequest.getLabel() );
            }
        }

        // send user email
        sendNewUserEmailConfirmation( pwmRequest );


        // add audit record
        pwmApplication.getAuditManager().submit( AuditEvent.CREATE_USER, pwmSession.getUserInfo(), pwmSession );

        // increment the new user creation statistics
        pwmApplication.getStatisticsManager().incrementValue( Statistic.NEW_USERS );

        NewUserUtils.LOGGER.debug( pwmRequest, () -> "completed createUser process for " + newUserDN + " (" + TimeDuration.fromCurrent(
                startTime ).asCompactString() + ")" );
    }

    static void deleteUserAccount(
            final String userDN,
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        try
        {
            final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile( pwmRequest );
            NewUserUtils.LOGGER.warn( pwmRequest, () -> "deleting ldap user account " + userDN );
            newUserProfile.getLdapProfile().getProxyChaiProvider( pwmRequest.getPwmApplication() ).deleteEntry( userDN );
            NewUserUtils.LOGGER.warn( pwmRequest, () -> "ldap user account " + userDN + " has been deleted" );
        }
        catch ( final ChaiUnavailableException | ChaiOperationException e )
        {
            NewUserUtils.LOGGER.error( pwmRequest, () -> "error deleting ldap user account " + userDN + ", " + e.getMessage() );
        }

        pwmRequest.getPwmSession().unauthenticateUser( pwmRequest );
    }

    static String determineUserDN(
            final PwmRequest pwmRequest,
            final NewUserForm formValues
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile( pwmRequest );
        final MacroRequest macroRequest = createMacroMachineForNewUser( pwmRequest.getPwmApplication(), newUserProfile, pwmRequest.getLabel(), formValues, null );
        final List<String> configuredNames = newUserProfile.readSettingAsStringArray( PwmSetting.NEWUSER_USERNAME_DEFINITION );
        final List<String> failedValues = new ArrayList<>();

        final String configuredContext = newUserProfile.readSettingAsString( PwmSetting.NEWUSER_CONTEXT );
        final String expandedContext = macroRequest.expandMacros( configuredContext );


        if ( configuredNames == null || configuredNames.isEmpty() || configuredNames.iterator().next().isEmpty() )
        {
            final String namingAttribute = newUserProfile.getLdapProfile().readSettingAsString( PwmSetting.LDAP_NAMING_ATTRIBUTE );
            String namingValue = null;
            for ( final String formKey : formValues.getFormData().keySet() )
            {
                if ( formKey.equals( namingAttribute ) )
                {
                    namingValue = formValues.getFormData().get( formKey );
                }
            }
            if ( namingValue == null || namingValue.isEmpty() )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_NEW_USER_FAILURE,
                        "username definition not set, and naming attribute is not present in form" ) );
            }
            final String escapedName = StringUtil.escapeLdapDN( namingValue );
            final String generatedDN = namingAttribute + "=" + escapedName + "," + expandedContext;
            NewUserUtils.LOGGER.debug( pwmRequest, () -> "generated dn for new user: " + generatedDN );
            return generatedDN;
        }

        int attemptCount = 0;
        final String generatedDN;
        while ( attemptCount < configuredNames.size() )
        {
            final String expandedName;
            {
                {
                    final String configuredName = configuredNames.get( attemptCount );
                    expandedName = macroRequest.expandMacros( configuredName );
                }

                if ( !testIfEntryNameExists( pwmRequest, expandedName ) )
                {
                    NewUserUtils.LOGGER.trace( pwmRequest, () -> "generated entry name for new user is unique: " + expandedName );
                    final String namingAttribute = newUserProfile.getLdapProfile().readSettingAsString( PwmSetting.LDAP_NAMING_ATTRIBUTE );
                    final String escapedName = StringUtil.escapeLdapDN( expandedName );
                    generatedDN = namingAttribute + "=" + escapedName + "," + expandedContext;
                    NewUserUtils.LOGGER.debug( pwmRequest, () -> "generated dn for new user: " + generatedDN );
                    return generatedDN;
                }
                else
                {
                    failedValues.add( expandedName );
                }
            }

            NewUserUtils.LOGGER.debug( pwmRequest, () -> "generated entry name for new user is not unique, will try again" );
            attemptCount++;
        }

        final int attemptCountFinal = attemptCount;
        NewUserUtils.LOGGER.error( pwmRequest,
                () -> "failed to generate new user DN after " + attemptCountFinal + " attempts, failed values: " + JsonUtil.serializeCollection(
                        failedValues ) );
        throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_NEW_USER_FAILURE,
                "unable to generate a unique DN value" ) );
    }

    private static boolean testIfEntryNameExists(
            final PwmRequest pwmRequest,
            final String rdnValue
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final UserSearchEngine userSearchEngine = pwmRequest.getPwmApplication().getUserSearchEngine();
        final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                .username( rdnValue )
                .build();

        try
        {
            final Map<UserIdentity, Map<String, String>> results = userSearchEngine.performMultiUserSearch(
                    searchConfiguration, 2, Collections.emptyList(), pwmRequest.getLabel() );
            return results != null && !results.isEmpty();
        }
        catch ( final PwmOperationalException e )
        {
            final String msg = "ldap error while searching for duplicate entry names: " + e.getMessage();
            NewUserUtils.LOGGER.error( pwmRequest, () -> msg );
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_NEW_USER_FAILURE, msg ) );
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
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail( PwmSetting.EMAIL_NEWUSER, locale );

        if ( configuredEmailSetting == null )
        {
            NewUserUtils.LOGGER.debug( pwmRequest, () ->
                    "skipping send of new user email for '" + userInfo.getUserIdentity().getUserDN() + "' no email configured" );
            return;
        }

        pwmRequest.getPwmApplication().getEmailQueue().submitEmail(
                configuredEmailSetting,
                pwmSession.getUserInfo(),
                pwmSession.getSessionManager().getMacroMachine( )
        );
    }

    static UserInfoBean createUserInfoBeanForNewUser(
            final PwmApplication pwmApplication,
            final NewUserProfile newUserProfile,
            final NewUserForm newUserForm
    )
        throws PwmUnrecoverableException

    {
        final Map<String, String> formValues = newUserForm.getFormData();

        final String emailAddressAttribute = newUserProfile.getLdapProfile().readSettingAsString(
            PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE );

        final String usernameAttribute = newUserProfile.getLdapProfile().readSettingAsString( PwmSetting.LDAP_USERNAME_ATTRIBUTE );

        return UserInfoBean.builder()
            .userEmailAddress( formValues.get( emailAddressAttribute ) )
            .username( formValues.get( usernameAttribute ) )
            .attributes( formValues )
            .build();
    }

    static MacroRequest createMacroMachineForNewUser(
            final PwmApplication pwmApplication,
            final NewUserProfile newUserProfile,
            final SessionLabel sessionLabel,
            final NewUserForm newUserForm,
            final TokenDestinationItem tokenDestinationItem
    )
            throws PwmUnrecoverableException
    {
        final LoginInfoBean stubLoginBean = new LoginInfoBean();
        stubLoginBean.setUserCurrentPassword( newUserForm.getNewUserPassword() );

        final UserInfoBean stubUserBean = createUserInfoBeanForNewUser( pwmApplication, newUserProfile, newUserForm );

        final MacroReplacer macroReplacer = tokenDestinationItem == null
                ? null
                : TokenUtil.makeTokenDestStringReplacer( tokenDestinationItem );

        return MacroRequest.forUser( pwmApplication, sessionLabel, stubUserBean, stubLoginBean, macroReplacer );
    }

    static Map<String, String> figureDisplayableProfiles( final PwmRequest pwmRequest )
    {
        final Map<String, String> returnMap = new LinkedHashMap<>();
        for ( final NewUserProfile newUserProfile : pwmRequest.getConfig().getNewUserProfiles().values() )
        {
            final boolean visible = newUserProfile.readSettingAsBoolean( PwmSetting.NEWUSER_PROFILE_DISPLAY_VISIBLE );
            if ( visible )
            {
                returnMap.put( newUserProfile.getIdentifier(), newUserProfile.getDisplayName( pwmRequest.getLocale() ) );
            }
        }
        return Collections.unmodifiableMap( returnMap );
    }

    static void remoteVerifyFormData(
            final PwmRequest pwmRequest,
            final NewUserForm newUserForm

    )
            throws PwmUnrecoverableException, PwmDataValidationException
    {
        remoteSendFormData(
                pwmRequest,
                newUserForm,
                FormDataRequestBean.Mode.verify
        );
    }

    static void remoteWriteFormData(
            final PwmRequest pwmRequest,
            final NewUserForm newUserForm

    )
            throws PwmUnrecoverableException, PwmDataValidationException
    {
        remoteSendFormData(
                pwmRequest,
                newUserForm,
                FormDataRequestBean.Mode.write
        );
    }

    private static void remoteSendFormData(
            final PwmRequest pwmRequest,
            final NewUserForm newUserForm,
            final FormDataRequestBean.Mode mode

    )
            throws PwmUnrecoverableException, PwmDataValidationException
    {
        final RestFormDataClient restFormDataClient = new RestFormDataClient( pwmRequest.getPwmApplication(), pwmRequest.getLabel() );
        if ( !restFormDataClient.isEnabled() )
        {
            return;
        }

        final NewUserBean newUserBean = NewUserServlet.getNewUserBean( pwmRequest );
        final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile( pwmRequest );

        final FormDataRequestBean.FormInfo formInfo = FormDataRequestBean.FormInfo.builder()
                .mode( mode )
                .moduleProfileID( newUserBean.getProfileID() )
                .sessionID( pwmRequest.getPwmSession().getLoginInfoBean().getGuid() )
                .module( FormDataRequestBean.FormType.NewUser )
                .build();

        final FormDataRequestBean formDataRequestBean = FormDataRequestBean.builder()
                .formInfo( formInfo )
                .formConfigurations( newUserProfile.readSettingAsForm( PwmSetting.NEWUSER_FORM ) )
                .formValues( newUserForm.getFormData() )
                .build();

        final FormDataResponseBean formDataResponseBean = restFormDataClient.invoke( formDataRequestBean, pwmRequest.getLocale() );
        if ( formDataResponseBean.isError() )
        {
            final ErrorInformation error = new ErrorInformation(
                    PwmError.ERROR_REMOTE_ERROR_VALUE,
                    formDataResponseBean.getErrorDetail(),
                    new String[]
                            {
                                    formDataResponseBean.getErrorMessage(),
                            }
            );
            throw new PwmDataValidationException( error );
        }
    }

    static Map<String, TokenDestinationItem.Type> determineTokenValidationsRequired(
            final PwmRequest pwmRequest,
            final NewUserBean newUserBean,
            final NewUserProfile newUserProfile
    )
            throws PwmUnrecoverableException
    {
        final List<FormConfiguration> formFields = newUserProfile.readSettingAsForm( PwmSetting.NEWUSER_FORM );
        final LdapProfile defaultLDAPProfile = newUserProfile.getLdapProfile();

        final Map<String, TokenDestinationItem.Type> workingMap = new LinkedHashMap<>( FormUtility.identifyFormItemsNeedingPotentialTokenValidation(
                defaultLDAPProfile,
                formFields
        ) );

        final Set<TokenDestinationItem.Type> interestedTypes = EnumSet.noneOf( TokenDestinationItem.Type.class );
        if ( newUserProfile.readSettingAsBoolean( PwmSetting.NEWUSER_EMAIL_VERIFICATION ) )
        {
            interestedTypes.add( TokenDestinationItem.Type.email );
        }
        if ( newUserProfile.readSettingAsBoolean( PwmSetting.NEWUSER_SMS_VERIFICATION ) )
        {
            interestedTypes.add( TokenDestinationItem.Type.sms );
        }

        if ( !JavaHelper.isEmpty( workingMap ) )
        {
            final Map<String, String> formData = newUserBean.getNewUserForm().getFormData();

            for ( final Iterator<Map.Entry<String, TokenDestinationItem.Type>> iter = workingMap.entrySet().iterator(); iter.hasNext(); )
            {
                final Map.Entry<String, TokenDestinationItem.Type> entry = iter.next();
                final String attrName = entry.getKey();
                final TokenDestinationItem.Type type = entry.getValue();

                if ( !interestedTypes.contains( type ) )
                {
                    iter.remove();
                }
                if ( !formData.containsKey( attrName ) )
                {
                    iter.remove();
                }
            }
        }

        return Collections.unmodifiableMap( workingMap );
    }

    static ProcessStatus checkForExternalResponsesVerificationProgress(
            final PwmRequest pwmRequest,
            final NewUserBean newUserBean,
            final NewUserProfile newUserProfile
    )
            throws PwmUnrecoverableException, ServletException, IOException
    {
        if ( newUserProfile.readSettingAsBoolean( PwmSetting.NEWUSER_EXTERNAL_VERIFICATION ) )
        {
            if ( !newUserBean.isExternalResponsesPassed() )
            {
                final VerificationMethodSystem remoteMethod = readRemoteVerificationMethod( pwmRequest, newUserBean );

                final List<VerificationMethodSystem.UserPrompt> prompts = remoteMethod.getCurrentPrompts();
                final String displayInstructions = remoteMethod.getCurrentDisplayInstructions();

                pwmRequest.setAttribute( PwmRequestAttribute.ExternalResponsePrompts, new ArrayList<>( prompts ) );
                pwmRequest.setAttribute( PwmRequestAttribute.ExternalResponseInstructions, displayInstructions );
                pwmRequest.forwardToJsp( JspUrl.NEW_USER_REMOTE );
                return ProcessStatus.Halt;
            }
        }

        return ProcessStatus.Continue;
    }

    static VerificationMethodSystem readRemoteVerificationMethod(
            final PwmRequest pwmRequest,
            final NewUserBean newUserBean
    )
            throws PwmUnrecoverableException
    {
        final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile( pwmRequest );
        final UserInfo userInfo = createUserInfoBeanForNewUser( pwmRequest.getPwmApplication(), newUserProfile, newUserBean.getNewUserForm() );

        final VerificationMethodSystem remoteMethod;
        if ( newUserBean.getRemoteRecoveryMethod() == null )
        {
            remoteMethod = new RemoteVerificationMethod();
            remoteMethod.init(
                    pwmRequest.getPwmApplication(),
                    userInfo,
                    pwmRequest.getLabel(),
                    pwmRequest.getLocale()
            );
        }
        else
        {
            remoteMethod = newUserBean.getRemoteRecoveryMethod();
        }
        newUserBean.setRemoteRecoveryMethod( remoteMethod );
        return remoteMethod;
    }

    static ProcessStatus checkForTokenVerificationProgress(
            final PwmRequest pwmRequest,
            final NewUserBean newUserBean,
            final NewUserProfile newUserProfile
    )
            throws PwmUnrecoverableException, ServletException, IOException
    {
        final Map<String, TokenDestinationItem.Type> requiredTokenValidations = determineTokenValidationsRequired(
                pwmRequest,
                newUserBean,
                newUserProfile
        );

        if ( !requiredTokenValidations.isEmpty() )
        {
            final Set<String> remainingValidations = new HashSet<>( requiredTokenValidations.keySet() );
            remainingValidations.removeAll( newUserBean.getCompletedTokenFields() );

            if ( !remainingValidations.isEmpty() )
            {
                if ( StringUtil.isEmpty( newUserBean.getCurrentTokenField() ) )
                {
                    newUserBean.setCurrentTokenField( remainingValidations.iterator().next() );
                    newUserBean.setTokenSent( false );
                }

                if ( !newUserBean.isTokenSent() )
                {
                    final TokenDestinationItem tokenDestinationItem = tokenDestinationItemForCurrentValidation( pwmRequest, newUserBean, newUserProfile );

                    if ( pwmRequest.getConfig().getTokenStorageMethod() == TokenStorageMethod.STORE_LDAP )
                    {
                        throw new PwmUnrecoverableException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[] {
                                "cannot generate new user tokens when storage type is configured as STORE_LDAP.",
                        } ) );
                    }

                    final Map<String, String> tokenPayloadMap = NewUserFormUtils.toTokenPayload( pwmRequest, newUserBean );
                    final MacroRequest macroRequest = createMacroMachineForNewUser(
                            pwmRequest.getPwmApplication(),
                            newUserProfile,
                            pwmRequest.getLabel(),
                            newUserBean.getNewUserForm(),
                            tokenDestinationItem );

                    final TimeDuration tokenLifetime = figureTokenLifetime( pwmRequest.getConfig(), newUserProfile, tokenDestinationItem );


                    TokenUtil.initializeAndSendToken(
                            pwmRequest.getPwmRequestContext(),
                            TokenUtil.TokenInitAndSendRequest.builder()
                                    .userInfo(  null )
                                    .tokenDestinationItem( tokenDestinationItem )
                                    .emailToSend( PwmSetting.EMAIL_NEWUSER_VERIFICATION )
                                    .tokenType( TokenType.NEWUSER )
                                    .smsToSend( PwmSetting.SMS_NEWUSER_TOKEN_TEXT )
                                    .inputTokenData( tokenPayloadMap )
                                    .macroRequest( macroRequest )
                                    .tokenLifetime( tokenLifetime )
                                    .build()
                    );
                    newUserBean.setTokenSent( true );
                }

                NewUserServlet.forwardToEnterCode( pwmRequest, newUserProfile, newUserBean );
                return ProcessStatus.Halt;
            }
        }

        return ProcessStatus.Continue;
    }

    static TokenDestinationItem tokenDestinationItemForCurrentValidation(
            final PwmRequest pwmRequest,
            final NewUserBean newUserBean,
            final NewUserProfile newUserProfile
    )
            throws PwmUnrecoverableException
    {
        if ( !newUserBean.isFormPassed() )
        {
            return null;
        }

        final List<FormConfiguration> formFields = newUserProfile.readSettingAsForm( PwmSetting.NEWUSER_FORM );
        final LdapProfile defaultLDAPProfile = newUserProfile.getLdapProfile();

        final Map<String, TokenDestinationItem.Type> tokenTypeMap = FormUtility.identifyFormItemsNeedingPotentialTokenValidation(
                defaultLDAPProfile,
                formFields
        );

        final String value = newUserBean.getNewUserForm().getFormData().get( newUserBean.getCurrentTokenField() );
        final TokenDestinationItem.Type type = tokenTypeMap.get( newUserBean.getCurrentTokenField() );
        return TokenDestinationItem.builder()
                .display( value )
                .id( "1" )
                .value( value )
                .type( type )
                .build();
    }

    static TimeDuration figureTokenLifetime(
            final Configuration configuration,
            final NewUserProfile newUserProfile,
            final TokenDestinationItem tokenDestinationItem
    )
    {
        switch ( tokenDestinationItem.getType() )
        {
            case email:
                return newUserProfile.getTokenDurationEmail( configuration );

            case sms:
                return newUserProfile.getTokenDurationSMS( configuration );

            default:
                JavaHelper.unhandledSwitchStatement( tokenDestinationItem );
        }

        return null;
    }
}
