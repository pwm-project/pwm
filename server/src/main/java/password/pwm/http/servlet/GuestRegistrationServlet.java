/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

package password.pwm.http.servlet;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.GuestRegistrationBean;
import password.pwm.i18n.Message;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.FormMap;
import password.pwm.util.PasswordData;
import password.pwm.util.form.FormUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmDateFormat;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.password.PasswordUtility;
import password.pwm.util.password.RandomPasswordGenerator;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Servlet for creating new guest users (helpdesk/admin registration).
 *
 * @author Jason D. Rivard, Menno Pieters
 */

@WebServlet(
        name = "GuestRegistrationServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/guest-registration",
                PwmConstants.URL_PREFIX_PRIVATE + "/GuestRegistration",
        }
)
public class GuestRegistrationServlet extends AbstractPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( GuestRegistrationServlet.class );

    public static final String HTTP_PARAM_EXPIRATION_DATE = "_expirationDateFormInput";

    public enum Page
    {
        create,
        search

    }

    public enum GuestRegistrationAction implements AbstractPwmServlet.ProcessAction
    {
        create,
        search,
        update,
        selectPage,;

        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( HttpMethod.POST );
        }
    }

    protected GuestRegistrationAction readProcessAction( final PwmRequest request )
            throws PwmUnrecoverableException
    {
        try
        {
            return GuestRegistrationAction.valueOf( request.readParameterAsString( PwmConstants.PARAM_ACTION_REQUEST ) );
        }
        catch ( final IllegalArgumentException e )
        {
            return null;
        }
    }


    protected void processAction( final PwmRequest pwmRequest )
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
        //Fetch the session state bean.
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final GuestRegistrationBean guestRegistrationBean = pwmApplication.getSessionStateService().getBean( pwmRequest, GuestRegistrationBean.class );

        final Configuration config = pwmApplication.getConfig();

        if ( !config.readSettingAsBoolean( PwmSetting.GUEST_ENABLE ) )
        {
            pwmRequest.respondWithError( PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo() );
            return;
        }

        if ( !pwmSession.getSessionManager().checkPermission( pwmApplication, Permission.GUEST_REGISTRATION ) )
        {
            pwmRequest.respondWithError( PwmError.ERROR_UNAUTHORIZED.toInfo() );
            return;
        }

        checkConfiguration( config );

        final GuestRegistrationAction action = readProcessAction( pwmRequest );
        if ( action != null )
        {
            pwmRequest.validatePwmFormID();
            switch ( action )
            {
                case create:
                    handleCreateRequest( pwmRequest, guestRegistrationBean );
                    return;

                case search:
                    handleSearchRequest( pwmRequest, guestRegistrationBean );
                    return;

                case update:
                    handleUpdateRequest( pwmRequest, guestRegistrationBean );
                    return;

                case selectPage:
                    handleSelectPageRequest( pwmRequest, guestRegistrationBean );
                    return;

                default:
                    JavaHelper.unhandledSwitchStatement( action );
            }
        }

        this.forwardToJSP( pwmRequest, guestRegistrationBean );
    }

    protected void handleSelectPageRequest(
            final PwmRequest pwmRequest,
            final GuestRegistrationBean guestRegistrationBean
    )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final String requestedPage = pwmRequest.readParameterAsString( "page" );
        try
        {
            guestRegistrationBean.setCurrentPage( Page.valueOf( requestedPage ) );
        }
        catch ( final IllegalArgumentException e )
        {
            LOGGER.error( pwmRequest, () -> "unknown page select request: " + requestedPage );
        }
        this.forwardToJSP( pwmRequest, guestRegistrationBean );
    }


    protected void handleUpdateRequest(
            final PwmRequest pwmRequest,
            final GuestRegistrationBean guestRegistrationBean

    )
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
        //Fetch the session state bean.
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final LocalSessionStateBean ssBean = pwmSession.getSessionStateBean();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmApplication.getConfig();

        final List<FormConfiguration> formItems = pwmApplication.getConfig().readSettingAsForm( PwmSetting.GUEST_UPDATE_FORM );
        final String expirationAttribute = config.readSettingAsString( PwmSetting.GUEST_EXPIRATION_ATTRIBUTE );

        try
        {
            //read the values from the request
            final Map<FormConfiguration, String> formValues = FormUtility.readFormValuesFromRequest(
                    pwmRequest, formItems, pwmRequest.getLocale() );

            // see if the values meet form requirements.
            FormUtility.validateFormValues( config, formValues, ssBean.getLocale() );

            //read current values from user.
            final ChaiUser theGuest = pwmSession.getSessionManager().getActor( guestRegistrationBean.getUpdateUserIdentity() );

            // check unique fields against ldap
            FormUtility.validateFormValueUniqueness(
                    pwmApplication,
                    formValues,
                    ssBean.getLocale(),
                    Collections.singletonList( guestRegistrationBean.getUpdateUserIdentity() )
            );

            final Instant expirationDate = readExpirationFromRequest( pwmRequest );

            // Update user attributes
            LdapOperationsHelper.writeFormValuesToLdap( theGuest, formValues, pwmSession.getSessionManager().getMacroMachine( ), false );

            // Write expirationDate
            if ( expirationDate != null )
            {
                theGuest.writeDateAttribute( expirationAttribute, expirationDate );
            }

            // send email.
            final UserInfo guestUserInfoBean = UserInfoFactory.newUserInfo(
                    pwmApplication,
                    pwmRequest.getLabel(),
                    pwmRequest.getLocale(),
                    guestRegistrationBean.getUpdateUserIdentity(),
                    theGuest.getChaiProvider()
            );
            this.sendUpdateGuestEmailConfirmation( pwmRequest, guestUserInfoBean );

            pwmApplication.getStatisticsManager().incrementValue( Statistic.UPDATED_GUESTS );

            //everything good so forward to confirmation page.
            pwmRequest.getPwmResponse().forwardToSuccessPage( Message.Success_UpdateGuest );
            return;
        }
        catch ( final PwmOperationalException e )
        {
            LOGGER.error( pwmRequest, () -> e.getErrorInformation().toDebugStr() );
            setLastError( pwmRequest, e.getErrorInformation() );
        }
        catch ( final ChaiOperationException e )
        {
            final ErrorInformation info = new ErrorInformation( PwmError.ERROR_INTERNAL, "unexpected error writing to ldap: " + e.getMessage() );
            LOGGER.error( pwmRequest, info );
            setLastError( pwmRequest, info );
        }
        this.forwardToUpdateJSP( pwmRequest, guestRegistrationBean );
    }

    private void sendUpdateGuestEmailConfirmation(
            final PwmRequest pwmRequest,
            final UserInfo guestUserInfo
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        final Locale locale = pwmRequest.getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail( PwmSetting.EMAIL_UPDATEGUEST, locale );

        if ( configuredEmailSetting == null )
        {
            LOGGER.debug( pwmRequest, () -> "unable to send updated guest user email: no email configured" );
            return;
        }

        pwmRequest.getPwmApplication().getEmailQueue().submitEmail( configuredEmailSetting, guestUserInfo, null );
    }

    protected void handleSearchRequest(
            final PwmRequest pwmRequest,
            final GuestRegistrationBean guestRegistrationBean
    )
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
        LOGGER.trace( pwmRequest, () -> "Enter: handleSearchRequest(...)" );
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final ChaiProvider chaiProvider = pwmSession.getSessionManager().getChaiProvider();
        final Configuration config = pwmApplication.getConfig();

        final String adminDnAttribute = config.readSettingAsString( PwmSetting.GUEST_ADMIN_ATTRIBUTE );
        final Boolean origAdminOnly = config.readSettingAsBoolean( PwmSetting.GUEST_EDIT_ORIG_ADMIN_ONLY );

        final String usernameParam = pwmRequest.readParameterAsString( "username" );
        final GuestRegistrationBean guBean = pwmApplication.getSessionStateService().getBean( pwmRequest, GuestRegistrationBean.class );

        final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                .chaiProvider( chaiProvider )
                .contexts( Collections.singletonList( config.readSettingAsString( PwmSetting.GUEST_CONTEXT ) ) )
                .enableContextValidation( false )
                .username( usernameParam )
                .build();

        final UserSearchEngine userSearchEngine = pwmApplication.getUserSearchEngine();

        try
        {
            final UserIdentity theGuest = userSearchEngine.performSingleUserSearch( searchConfiguration, pwmRequest.getLabel() );
            final FormMap formProps = guBean.getFormValues();
            try
            {
                final List<FormConfiguration> guestUpdateForm = config.readSettingAsForm( PwmSetting.GUEST_UPDATE_FORM );
                final Set<String> involvedAttrs = new HashSet<>();
                for ( final FormConfiguration formItem : guestUpdateForm )
                {
                    if ( !formItem.getName().equalsIgnoreCase( HTTP_PARAM_EXPIRATION_DATE ) )
                    {
                        involvedAttrs.add( formItem.getName() );
                    }
                }
                final UserInfo guestUserInfo = UserInfoFactory.newUserInfo(
                        pwmApplication,
                        pwmRequest.getLabel(),
                        pwmRequest.getLocale(),
                        theGuest,
                        pwmSession.getSessionManager().getChaiProvider()
                );
                final Map<String, String> userAttrValues = guestUserInfo.readStringAttributes( involvedAttrs );
                if ( origAdminOnly && adminDnAttribute != null && adminDnAttribute.length() > 0 )
                {
                    final String origAdminDn = userAttrValues.get( adminDnAttribute );
                    if ( origAdminDn != null && origAdminDn.length() > 0 )
                    {
                        if ( !pwmSession.getUserInfo().getUserIdentity().getUserDN().equalsIgnoreCase( origAdminDn ) )
                        {
                            final ErrorInformation info = new ErrorInformation( PwmError.ERROR_ORIG_ADMIN_ONLY );
                            setLastError( pwmRequest, info );
                            LOGGER.error( pwmRequest, info );
                            this.forwardToJSP( pwmRequest, guestRegistrationBean );
                        }
                    }
                }
                final String expirationAttribute = config.readSettingAsString( PwmSetting.GUEST_EXPIRATION_ATTRIBUTE );
                if ( expirationAttribute != null && expirationAttribute.length() > 0 )
                {
                    final Instant expiration = guestUserInfo.readDateAttribute( expirationAttribute );
                    if ( expiration != null )
                    {
                        guBean.setUpdateUserExpirationDate( expiration );
                    }
                }

                for ( final FormConfiguration formItem : guestUpdateForm )
                {
                    final String key = formItem.getName();
                    final String value = userAttrValues.get( key );
                    if ( value != null )
                    {
                        formProps.put( key, value );
                    }
                }

                guBean.setUpdateUserIdentity( theGuest );

                this.forwardToUpdateJSP( pwmRequest, guestRegistrationBean );
                return;
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.warn( pwmRequest, () -> "error reading current attributes for user: " + e.getMessage() );
            }
        }
        catch ( final PwmOperationalException e )
        {
            final ErrorInformation error = e.getErrorInformation();
            setLastError( pwmRequest, error );
            this.forwardToJSP( pwmRequest, guestRegistrationBean );
            return;
        }
        this.forwardToJSP( pwmRequest, guestRegistrationBean );
    }


    private void handleCreateRequest(
            final PwmRequest pwmRequest,
            final GuestRegistrationBean guestRegistrationBean
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final LocalSessionStateBean ssBean = pwmSession.getSessionStateBean();
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = ssBean.getLocale();

        final List<FormConfiguration> guestUserForm = config.readSettingAsForm( PwmSetting.GUEST_FORM );

        try
        {
            //read the values from the request
            final Map<FormConfiguration, String> formValues = FormUtility.readFormValuesFromRequest(
                    pwmRequest, guestUserForm, locale );

            //read the expiration date from the request.
            final Instant expirationDate = readExpirationFromRequest( pwmRequest );

            // see if the values meet form requirements.
            FormUtility.validateFormValues( config, formValues, locale );

            // read new user DN
            final String guestUserDN = determineUserDN( formValues, config );

            // read a chai provider to make the user
            final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();

            // set up the user creation attributes
            final Map<String, String> createAttributes = new HashMap<>();
            for ( final Map.Entry<FormConfiguration, String> entry : formValues.entrySet() )
            {
                final FormConfiguration formItem = entry.getKey();
                final String value = entry.getValue();
                LOGGER.debug( pwmRequest, () -> "Attribute from form: " + formItem.getName() + " = " + value );
                final String n = formItem.getName();
                final String v = formValues.get( formItem );
                if ( n != null && n.length() > 0 && v != null && v.length() > 0 )
                {
                    createAttributes.put( n, v );
                }
            }

            // Write creator DN
            createAttributes.put( config.readSettingAsString( PwmSetting.GUEST_ADMIN_ATTRIBUTE ), pwmSession.getUserInfo().getUserIdentity().getUserDN() );

            // read the creation object classes.
            final Set<String> createObjectClasses = new HashSet<>( config.readSettingAsStringArray( PwmSetting.DEFAULT_OBJECT_CLASSES ) );

            provider.createEntry( guestUserDN, createObjectClasses, createAttributes );
            LOGGER.info( pwmRequest, () -> "created user object: " + guestUserDN );

            final ChaiUser theUser = provider.getEntryFactory().newChaiUser( guestUserDN );
            final UserIdentity userIdentity = new UserIdentity( guestUserDN, pwmSession.getUserInfo().getUserIdentity().getLdapProfileID() );

            // write the expiration date:
            if ( expirationDate != null )
            {
                final String expirationAttr = config.readSettingAsString( PwmSetting.GUEST_EXPIRATION_ATTRIBUTE );
                theUser.writeDateAttribute( expirationAttr, expirationDate );
            }

            final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(
                    pwmApplication,
                    pwmRequest.getLabel(),
                    userIdentity,
                    theUser,
                    locale
            );

            final PasswordData newPassword = RandomPasswordGenerator.createRandomPassword( pwmRequest.getLabel(), passwordPolicy, pwmApplication );
            theUser.setPassword( newPassword.getStringValue() );


            {
                // execute configured actions
                LOGGER.debug( pwmRequest, () -> "executing configured actions to user " + theUser.getEntryDN() );
                final List<ActionConfiguration> actions = pwmApplication.getConfig().readSettingAsAction( PwmSetting.GUEST_WRITE_ATTRIBUTES );
                if ( actions != null && !actions.isEmpty() )
                {
                    final MacroMachine macroMachine = MacroMachine.forUser( pwmRequest, userIdentity );


                    final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings( pwmApplication, theUser )
                            .setExpandPwmMacros( true )
                            .setMacroMachine( macroMachine )
                            .createActionExecutor();

                    actionExecutor.executeActions( actions, pwmRequest.getLabel() );
                }
            }

            //everything good so forward to success page.
            this.sendGuestUserEmailConfirmation( pwmRequest, userIdentity );

            pwmApplication.getStatisticsManager().incrementValue( Statistic.NEW_USERS );

            pwmRequest.getPwmResponse().forwardToSuccessPage( Message.Success_CreateGuest );
        }
        catch ( final ChaiOperationException e )
        {
            final ErrorInformation info = new ErrorInformation( PwmError.ERROR_NEW_USER_FAILURE, "error creating user: " + e.getMessage() );
            setLastError( pwmRequest, info );
            LOGGER.error( pwmRequest, info );
            this.forwardToJSP( pwmRequest, guestRegistrationBean );
        }
        catch ( final PwmOperationalException e )
        {
            LOGGER.error( pwmRequest, () -> e.getErrorInformation().toDebugStr() );
            setLastError( pwmRequest, e.getErrorInformation() );
            this.forwardToJSP( pwmRequest, guestRegistrationBean );
        }
    }

    private static Instant readExpirationFromRequest(
            final PwmRequest pwmRequest
    )
            throws PwmOperationalException, ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmApplication.getConfig();
        final long durationValueDays = config.readSettingAsLong( PwmSetting.GUEST_MAX_VALID_DAYS );
        final String expirationAttribute = config.readSettingAsString( PwmSetting.GUEST_EXPIRATION_ATTRIBUTE );

        if ( durationValueDays == 0 || expirationAttribute == null || expirationAttribute.length() <= 0 )
        {
            return null;
        }

        final String expirationDateStr = pwmRequest.readParameterAsString( HTTP_PARAM_EXPIRATION_DATE );

        final Instant expirationDate;
        try
        {
            expirationDate = PwmDateFormat.parse( "yyyy-MM-dd", expirationDateStr );
        }
        catch ( final ParseException e )
        {
            final String errorMsg = "unable to read expiration date value: " + e.getMessage();
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_FIELD_REQUIRED, errorMsg, new String[]
                    {
                            "expiration date",
                    }
            ) );
        }

        if ( expirationDate.isBefore( Instant.now() ) )
        {
            final String errorMsg = "expiration date must be in the future";
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_FIELD_REQUIRED, errorMsg ) );
        }

        final long durationValueMs = durationValueDays * 24 * 60 * 60 * 1000;
        final long futureDateMs = System.currentTimeMillis() + durationValueMs;
        final Instant futureDate = Instant.ofEpochMilli( futureDateMs );

        if ( expirationDate.isAfter( futureDate ) )
        {
            final String errorMsg = "expiration date must be sooner than " + futureDate.toString();
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_FIELD_REQUIRED, errorMsg ) );
        }

        LOGGER.trace( pwmRequest, () -> "read expiration date as " + expirationDate.toString() );
        return expirationDate;
    }

    private static String determineUserDN( final Map<FormConfiguration, String> formValues, final Configuration config )
            throws PwmUnrecoverableException
    {
        final String namingAttribute = config.getDefaultLdapProfile().readSettingAsString(
                PwmSetting.LDAP_NAMING_ATTRIBUTE );
        for ( final Map.Entry<FormConfiguration, String> entry : formValues.entrySet() )
        {
            final FormConfiguration formItem = entry.getKey();
            if ( namingAttribute.equals( formItem.getName() ) )
            {
                final String namingValue = entry.getValue();
                final String gestUserContextDN = config.readSettingAsString( PwmSetting.GUEST_CONTEXT );
                return namingAttribute + "=" + namingValue + "," + gestUserContextDN;
            }
        }
        final String errorMsg = "unable to determine new user DN due to missing form value for naming attribute '" + namingAttribute + '"';
        throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg ) );
    }

    private void sendGuestUserEmailConfirmation(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail( PwmSetting.EMAIL_GUEST, locale );

        if ( configuredEmailSetting == null )
        {
            LOGGER.debug( pwmRequest, () -> "unable to send guest registration email for '" + userIdentity + "' no email configured" );
            return;
        }

        final MacroMachine macroMachine = MacroMachine.forUser( pwmRequest, userIdentity );

        pwmApplication.getEmailQueue().submitEmail( configuredEmailSetting, null, macroMachine );
    }

    private void forwardToJSP(
            final PwmRequest pwmRequest,
            final GuestRegistrationBean guestRegistrationBean
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        calculateFutureDateFlags( pwmRequest, guestRegistrationBean );
        if ( Page.search == guestRegistrationBean.getCurrentPage() )
        {
            pwmRequest.addFormInfoToRequestAttr( PwmSetting.GUEST_UPDATE_FORM, false, false );
            pwmRequest.forwardToJsp( JspUrl.GUEST_UPDATE_SEARCH );
        }
        else
        {
            pwmRequest.addFormInfoToRequestAttr( PwmSetting.GUEST_FORM, false, false );
            pwmRequest.forwardToJsp( JspUrl.GUEST_REGISTRATION );
        }
    }

    private void forwardToUpdateJSP(
            final PwmRequest pwmRequest,
            final GuestRegistrationBean guestRegistrationBean
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        calculateFutureDateFlags( pwmRequest, guestRegistrationBean );
        final List<FormConfiguration> guestUpdateForm = pwmRequest.getConfig().readSettingAsForm( PwmSetting.GUEST_UPDATE_FORM );
        final Map<FormConfiguration, String> formValueMap = new LinkedHashMap<>();
        for ( final FormConfiguration formConfiguration : guestUpdateForm )
        {
            final String value = guestRegistrationBean.getFormValues().get( formConfiguration.getName() );
            formValueMap.put( formConfiguration, value );
        }

        pwmRequest.addFormInfoToRequestAttr( guestUpdateForm, formValueMap, false, false );
        pwmRequest.forwardToJsp( JspUrl.GUEST_UPDATE );
    }

    private static void checkConfiguration( final Configuration configuration )
            throws PwmUnrecoverableException
    {
        final String namingAttribute = configuration.getDefaultLdapProfile().readSettingAsString( PwmSetting.LDAP_NAMING_ATTRIBUTE );
        final List<FormConfiguration> formItems = configuration.readSettingAsForm( PwmSetting.GUEST_FORM );

        {
            boolean namingIsInForm = false;
            for ( final FormConfiguration formItem : formItems )
            {
                if ( namingAttribute.equalsIgnoreCase( formItem.getName() ) )
                {
                    namingIsInForm = true;
                }
            }

            if ( !namingIsInForm )
            {
                final String errorMsg = "ldap naming attribute '" + namingAttribute + "' is not in form configuration, but is required";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, errorMsg, new String[]
                        {
                                namingAttribute,
                        }
                        );
                throw new PwmUnrecoverableException( errorInformation );
            }
        }
    }

    private void calculateFutureDateFlags( final PwmRequest pwmRequest, final GuestRegistrationBean guestRegistrationBean )
    {
        final PwmDateFormat dateFormat = PwmDateFormat.newPwmDateFormat( "yyyy-MM-dd" );

        final long maxValidDays = pwmRequest.getConfig().readSettingAsLong( PwmSetting.GUEST_MAX_VALID_DAYS );
        pwmRequest.setAttribute( PwmRequestAttribute.GuestMaximumValidDays, String.valueOf( maxValidDays ) );


        final String maxExpirationDate;
        {
            if ( maxValidDays > 0 )
            {
                final long futureMS = maxValidDays * 24 * 60 * 60 * 1000;
                final Instant maxValidDate = Instant.ofEpochMilli( System.currentTimeMillis() + futureMS );
                maxExpirationDate = dateFormat.format( maxValidDate );
            }
            else
            {
                maxExpirationDate = "";
            }

        }
        final String currentExpirationDate;
        {
            final String selectedDate = guestRegistrationBean.getFormValues().get( HTTP_PARAM_EXPIRATION_DATE );
            if ( selectedDate == null || selectedDate.isEmpty() )
            {
                final Instant currentDate = guestRegistrationBean.getUpdateUserExpirationDate();

                if ( currentDate == null )
                {
                    currentExpirationDate = maxExpirationDate;
                }
                else
                {
                    currentExpirationDate = dateFormat.format( currentDate );
                }
            }
            else
            {
                currentExpirationDate = dateFormat.format( Instant.now() );
            }
        }

        pwmRequest.setAttribute( PwmRequestAttribute.GuestCurrentExpirationDate, currentExpirationDate );
        pwmRequest.setAttribute( PwmRequestAttribute.GuestMaximumExpirationDate, maxExpirationDate );
    }
}





