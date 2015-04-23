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
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.*;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.GuestRegistrationBean;
import password.pwm.i18n.Message;
import password.pwm.ldap.LdapUserDataReader;
import password.pwm.ldap.UserDataReader;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.ldap.UserStatusReader;
import password.pwm.util.FormMap;
import password.pwm.util.Helper;
import password.pwm.util.PasswordData;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Servlet for creating new guest users (helpdesk/admin registration)
 *
 * @author Jason D. Rivard, Menno Pieters
 */
public class GuestRegistrationServlet extends PwmServlet {
    private static final PwmLogger LOGGER = PwmLogger.forClass(GuestRegistrationServlet.class);

    public static final String HTTP_PARAM_EXPIRATION_DATE = "expirationDateFormInput";
    
    public enum Page {
        create,
        search
        
    }

    public enum GuestRegistrationAction implements PwmServlet.ProcessAction {
        create,
        search,
        update,
        selectPage,
        ;

        public Collection<HttpMethod> permittedMethods()
        {
            return Collections.singletonList(HttpMethod.POST);
        }
    }

    protected GuestRegistrationAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        try {
            return GuestRegistrationAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
        //Fetch the session state bean.
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final GuestRegistrationBean guestRegistrationBean = pwmSession.getGuestRegistrationBean();

        final Configuration config = pwmApplication.getConfig();

        if (!config.readSettingAsBoolean(PwmSetting.GUEST_ENABLE)) {
            pwmRequest.respondWithError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            return;
        }

        if (!pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.GUEST_REGISTRATION)) {
            pwmRequest.respondWithError(PwmError.ERROR_UNAUTHORIZED.toInfo());
            return;
        }

        checkConfiguration(config);

        final GuestRegistrationAction action = readProcessAction(pwmRequest);
        if (action != null) {
            pwmRequest.validatePwmFormID();
            switch (action) {
                case create:
                    handleCreateRequest(pwmRequest, guestRegistrationBean);
                    return;

                case search:
                    handleSearchRequest(pwmRequest, guestRegistrationBean);
                    return;

                case update:
                    handleUpdateRequest(pwmRequest);
                    return;
                
                case selectPage:
                    handleSelectPageRequest(pwmRequest, guestRegistrationBean);
                    return;
            }
        }

        this.forwardToJSP(pwmRequest, guestRegistrationBean);
    }
    
    protected void handleSelectPageRequest(
            final PwmRequest pwmRequest,
            final GuestRegistrationBean guestRegistrationBean
    )
            throws PwmUnrecoverableException, IOException, ServletException 
    {
        final String requestedPage = pwmRequest.readParameterAsString("page");
        try {
            guestRegistrationBean.setCurrentPage(Page.valueOf(requestedPage));
        } catch (IllegalArgumentException e) {
            LOGGER.error(pwmRequest,"unknown page select request: " + requestedPage);
        }
        this.forwardToJSP(pwmRequest, guestRegistrationBean);
    }
    
    
    protected void handleUpdateRequest(
            final PwmRequest pwmRequest
    )
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
        //Fetch the session state bean.
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final GuestRegistrationBean guBean = pwmSession.getGuestRegistrationBean();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmApplication.getConfig();

        final List<FormConfiguration> formItems = pwmApplication.getConfig().readSettingAsForm(PwmSetting.GUEST_UPDATE_FORM);
        final String expirationAttribute = config.readSettingAsString(PwmSetting.GUEST_EXPIRATION_ATTRIBUTE);

        try {
            //read the values from the request
            final Map<FormConfiguration,String> formValues = FormUtility.readFormValuesFromRequest(
                    pwmRequest, formItems, pwmRequest.getLocale());

            // see if the values meet form requirements.
            FormUtility.validateFormValues(config, formValues, ssBean.getLocale());

            //read current values from user.
            final ChaiUser theGuest = pwmSession.getSessionManager().getActor(pwmApplication, guBean.getUpdateUserIdentity());

            // check unique fields against ldap
            FormUtility.validateFormValueUniqueness(
                    pwmApplication,
                    formValues,
                    ssBean.getLocale(),
                    Collections.singletonList(guBean.getUpdateUserIdentity()),
                    false
            );

            final Date expirationDate = readExpirationFromRequest(pwmRequest);

            // Update user attributes
            Helper.writeFormValuesToLdap(pwmApplication, pwmSession, theGuest, formValues, false);

            // Write expirationDate
            if (expirationDate != null) {
                theGuest.writeDateAttribute(expirationAttribute, expirationDate);
            }

            // send email.
            final UserStatusReader userStatusReader = new UserStatusReader(pwmApplication,pwmSession.getLabel());
            final UserInfoBean guestUserInfoBean = new UserInfoBean();
            userStatusReader.populateUserInfoBean(
                    guestUserInfoBean,
                    pwmSession.getSessionStateBean().getLocale(),
                    guBean.getUpdateUserIdentity(),
                    theGuest.getChaiProvider()
            );
            this.sendUpdateGuestEmailConfirmation(pwmRequest, guestUserInfoBean);

            pwmApplication.getStatisticsManager().incrementValue(Statistic.UPDATED_GUESTS);

            //everything good so forward to confirmation page.
            pwmRequest.forwardToSuccessPage(Message.Success_UpdateGuest);
            return;
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmSession, e.getErrorInformation().toDebugStr());
            pwmRequest.setResponseError(e.getErrorInformation());
        } catch (ChaiOperationException e) {
            final ErrorInformation info = new ErrorInformation(PwmError.ERROR_UNKNOWN, "unexpected error writing to ldap: " + e.getMessage());
            LOGGER.error(pwmSession, info);
            pwmRequest.setResponseError(info);
        }
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.GUEST_UPDATE);
    }

    private void sendUpdateGuestEmailConfirmation(
            final PwmRequest pwmRequest,
            final UserInfoBean guestUserInfoBean
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        final Locale locale = pwmRequest.getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_UPDATEGUEST, locale);

        if (configuredEmailSetting == null) {
            LOGGER.debug(pwmRequest, "unable to send updated guest user email: no email configured");
            return;
        }

        pwmRequest.getPwmApplication().getEmailQueue().submitEmail(configuredEmailSetting, guestUserInfoBean, null);
    }

    protected void handleSearchRequest(
            final PwmRequest pwmRequest,
            final GuestRegistrationBean guestRegistrationBean
    )
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
        LOGGER.trace(pwmRequest, "Enter: handleSearchRequest(...)");
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final ChaiProvider chaiProvider = pwmSession.getSessionManager().getChaiProvider();
        final Configuration config = pwmApplication.getConfig();

        final String adminDnAttribute = config.readSettingAsString(PwmSetting.GUEST_ADMIN_ATTRIBUTE);
        final Boolean origAdminOnly = config.readSettingAsBoolean(PwmSetting.GUEST_EDIT_ORIG_ADMIN_ONLY);

        final String usernameParam = pwmRequest.readParameterAsString("username");
        final GuestRegistrationBean guBean = pwmSession.getGuestRegistrationBean();

        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        searchConfiguration.setChaiProvider(chaiProvider);
        searchConfiguration.setContexts(Collections.singletonList(config.readSettingAsString(PwmSetting.GUEST_CONTEXT)));
        searchConfiguration.setEnableContextValidation(false);
        searchConfiguration.setUsername(usernameParam);
        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication, pwmSession.getLabel());

        try {
            final UserIdentity theGuest = userSearchEngine.performSingleUserSearch(searchConfiguration);
            final FormMap formProps = guBean.getFormValues();
            try {
                final List<FormConfiguration> guestUpdateForm = config.readSettingAsForm(PwmSetting.GUEST_UPDATE_FORM);
                final Set<String> involvedAttrs = new HashSet<>();
                for (final FormConfiguration formItem : guestUpdateForm) {
                    if (!formItem.getName().equalsIgnoreCase(HTTP_PARAM_EXPIRATION_DATE)) {
                        involvedAttrs.add(formItem.getName());
                    }
                }
                final UserDataReader userDataReader = LdapUserDataReader.selfProxiedReader(pwmApplication, pwmSession,
                        theGuest);
                final Map<String,String> userAttrValues = userDataReader.readStringAttributes(involvedAttrs);
                if (origAdminOnly && adminDnAttribute != null && adminDnAttribute.length() > 0) {
                    final String origAdminDn = userAttrValues.get(adminDnAttribute);
                    if (origAdminDn != null && origAdminDn.length() > 0) {
                        if (!pwmSession.getUserInfoBean().getUserIdentity().getUserDN().equalsIgnoreCase(origAdminDn)) {
                            final ErrorInformation info = new ErrorInformation(PwmError.ERROR_ORIG_ADMIN_ONLY);
                            pwmRequest.setResponseError(info);
                            LOGGER.warn(pwmSession, info);
                            this.forwardToJSP(pwmRequest, guestRegistrationBean);
                        }
                    }
                }
                final String expirationAttribute = config.readSettingAsString(PwmSetting.GUEST_EXPIRATION_ATTRIBUTE);
                if (expirationAttribute != null && expirationAttribute.length() > 0) {
                    final Date expiration = userDataReader.readDateAttribute(expirationAttribute);
                    if (expiration != null) {
                        guBean.setUpdateUserExpirationDate(expiration);
                    }
                }

                for (final FormConfiguration formItem : guestUpdateForm) {
                    final String key = formItem.getName();
                    final String value = userAttrValues.get(key);
                    if (value != null) {
                        formProps.put(key, value);
                    }
                }

                guBean.setUpdateUserIdentity(theGuest);

                pwmRequest.forwardToJsp(PwmConstants.JSP_URL.GUEST_UPDATE);
                return;
            } catch (ChaiOperationException e) {
                LOGGER.warn(pwmSession, "error reading current attributes for user: " + e.getMessage());
            }
        } catch (PwmOperationalException e) {
            final ErrorInformation error = e.getErrorInformation();
            pwmRequest.setResponseError(error);
            this.forwardToJSP(pwmRequest, guestRegistrationBean);
            return;
        }
        this.forwardToJSP(pwmRequest, guestRegistrationBean);
    }


    private void handleCreateRequest(
            final PwmRequest pwmRequest,
            final GuestRegistrationBean guestRegistrationBean
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = ssBean.getLocale();

        final List<FormConfiguration> guestUserForm = config.readSettingAsForm(PwmSetting.GUEST_FORM);

        try {
            //read the values from the request
            final Map<FormConfiguration, String> formValues = FormUtility.readFormValuesFromRequest(
                    pwmRequest, guestUserForm, locale);

            //read the expiration date from the request.
            final Date expirationDate = readExpirationFromRequest(pwmRequest);

            // see if the values meet form requirements.
            FormUtility.validateFormValues(config, formValues, locale);

            // read new user DN
            final String guestUserDN = determineUserDN(formValues, config);

            // read a chai provider to make the user
            final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();

            // set up the user creation attributes
            final Map<String,String> createAttributes = new HashMap<>();
            for (final FormConfiguration formItem : formValues.keySet()) {
                LOGGER.debug(pwmSession, "Attribute from form: "+ formItem.getName()+" = "+formValues.get(formItem));
                final String n = formItem.getName();
                final String v = formValues.get(formItem);
                if (n != null && n.length() > 0 && v != null && v.length() > 0) {
                    createAttributes.put(n, v);
                }
            }

            // Write creator DN
            createAttributes.put(config.readSettingAsString(PwmSetting.GUEST_ADMIN_ATTRIBUTE), pwmSession.getUserInfoBean().getUserIdentity().getUserDN());

            // read the creation object classes.
            final Set<String> createObjectClasses = new HashSet<>(config.readSettingAsStringArray(PwmSetting.DEFAULT_OBJECT_CLASSES));

            provider.createEntry(guestUserDN, createObjectClasses, createAttributes);
            LOGGER.info(pwmSession, "created user object: " + guestUserDN);

            final ChaiUser theUser = ChaiFactory.createChaiUser(guestUserDN, provider);
            final UserIdentity userIdentity = new UserIdentity(guestUserDN, pwmSession.getUserInfoBean().getUserIdentity().getLdapProfileID());

            // write the expiration date:
            if (expirationDate != null) {
                final String expirationAttr =config.readSettingAsString(PwmSetting.GUEST_EXPIRATION_ATTRIBUTE);
                theUser.writeDateAttribute(expirationAttr, expirationDate);
            }

            final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(pwmApplication, pwmSession.getLabel(), userIdentity, theUser, locale);
            final PasswordData newPassword = RandomPasswordGenerator.createRandomPassword(pwmSession.getLabel(), passwordPolicy, pwmApplication);
            theUser.setPassword(newPassword.getStringValue());
            /*
            final UserInfoBean guestUserInfoBean = new UserInfoBean();
            final UserStatusReader userStatusReader = new UserStatusReader(pwmApplication);
            userStatusReader.populateUserInfoBean(
                    pwmSession.getLabel(),
                    guestUserInfoBean,
                    pwmSession.getSessionStateBean().getLocale(),
                    userIdentity,
                    theUser.getChaiProvider()
            );
            */


            {  // execute configured actions
                LOGGER.debug(pwmSession, "executing configured actions to user " + theUser.getEntryDN());
                final List<ActionConfiguration> actions = pwmApplication.getConfig().readSettingAsAction(PwmSetting.GUEST_WRITE_ATTRIBUTES);
                if (actions != null && !actions.isEmpty()) {
                    final MacroMachine macroMachine = MacroMachine.forUser(pwmRequest, userIdentity);


                    final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings(pwmApplication, theUser)
                            .setExpandPwmMacros(true)
                            .setMacroMachine(macroMachine)
                            .createActionExecutor();

                    actionExecutor.executeActions(actions, pwmSession);
                }
            }

            //everything good so forward to success page.
            this.sendGuestUserEmailConfirmation(pwmRequest, userIdentity);

            pwmApplication.getStatisticsManager().incrementValue(Statistic.NEW_USERS);

            pwmRequest.forwardToSuccessPage(Message.Success_CreateGuest);
        } catch (ChaiOperationException e) {
            final ErrorInformation info = new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE, "error creating user: " + e.getMessage());
            pwmRequest.setResponseError(info);
            LOGGER.warn(pwmSession, info);
            this.forwardToJSP(pwmRequest, guestRegistrationBean);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmSession, e.getErrorInformation().toDebugStr());
            pwmRequest.setResponseError(e.getErrorInformation());
            this.forwardToJSP(pwmRequest, guestRegistrationBean);
        }
    }

    private static Date readExpirationFromRequest(
            final PwmRequest pwmRequest
    )
            throws PwmOperationalException, ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmApplication.getConfig();
        final long durationValueDays = config.readSettingAsLong(PwmSetting.GUEST_MAX_VALID_DAYS);
        final String expirationAttribute = config.readSettingAsString(PwmSetting.GUEST_EXPIRATION_ATTRIBUTE);

        if (durationValueDays == 0 || expirationAttribute == null || expirationAttribute.length() <= 0) {
            return null;
        }

        final String expirationDateStr = pwmRequest.readParameterAsString(HTTP_PARAM_EXPIRATION_DATE);

        Date expirationDate;
        try {
            expirationDate = new SimpleDateFormat("yyyy-MM-dd").parse(expirationDateStr);
        } catch (ParseException e) {
            final String errorMsg = "unable to read expiration date value: " + e.getMessage();
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_FIELD_REQUIRED,errorMsg,new String[]{"expiration date"}));
        }

        if (expirationDate.before(new Date())) {
            final String errorMsg = "expiration date must be in the future";
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_FIELD_REQUIRED,errorMsg));
        }

        final long durationValueMs = durationValueDays * 24 * 60 * 60 * 1000;
        final long futureDateMs = System.currentTimeMillis() + durationValueMs;
        final Date futureDate = new Date(futureDateMs);

        if (expirationDate.after(futureDate)) {
            final String errorMsg = "expiration date must be sooner than " + futureDate.toString();
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_FIELD_REQUIRED,errorMsg));
        }

        LOGGER.trace(pwmRequest, "read expiration date as " + expirationDate.toString());
        return expirationDate;
    }

    private static String determineUserDN(final Map<FormConfiguration, String> formValues, final Configuration config)
            throws PwmUnrecoverableException
    {
        final String namingAttribute = config.getDefaultLdapProfile().readSettingAsString(
                PwmSetting.LDAP_NAMING_ATTRIBUTE);
        for (final FormConfiguration formItem : formValues.keySet()) {
            if (namingAttribute.equals(formItem.getName())) {
                final String namingValue = formValues.get(formItem);
                final String gestUserContextDN = config.readSettingAsString(PwmSetting.GUEST_CONTEXT);
                return namingAttribute + "=" + namingValue + "," + gestUserContextDN;
            }
        }
        final String errorMsg = "unable to determine new user DN due to missing form value for naming attribute '" + namingAttribute + '"';
        throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg));
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
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_GUEST, locale);

        if (configuredEmailSetting == null) {
            LOGGER.debug(pwmSession, "unable to send guest registration email for '" + userIdentity + "' no email configured");
            return;
        }

        final MacroMachine macroMachine = MacroMachine.forUser(pwmRequest, userIdentity);

        pwmApplication.getEmailQueue().submitEmail(configuredEmailSetting, null, macroMachine);
    }

    private void forwardToJSP(
            final PwmRequest pwmRequest,
            final GuestRegistrationBean guestRegistrationBean
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        if (Page.search == guestRegistrationBean.getCurrentPage()) {
            pwmRequest.addFormInfoToRequestAttr(PwmSetting.GUEST_UPDATE_FORM, false, false);
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.GUEST_UPDATE_SEARCH);
        } else {
            pwmRequest.addFormInfoToRequestAttr(PwmSetting.GUEST_FORM, false, false);
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.GUEST_REGISTRATION);
        }
    }

    private static void checkConfiguration(final Configuration configuration)
            throws PwmUnrecoverableException
    {
        final String namingAttribute = configuration.getDefaultLdapProfile().readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
        final List<FormConfiguration> formItems = configuration.readSettingAsForm(PwmSetting.GUEST_FORM);

        {
            boolean namingIsInForm = false;
            for (final FormConfiguration formItem : formItems) {
                if (namingAttribute.equalsIgnoreCase(formItem.getName())) {
                    namingIsInForm = true;
                }
            }

            if (!namingIsInForm) {
                final String errorMsg = "ldap naming attribute '" + namingAttribute + "' is not in form configuration, but is required";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG, errorMsg, new String[]{namingAttribute});
                throw new PwmUnrecoverableException(errorInformation);
            }
        }
    }
}





