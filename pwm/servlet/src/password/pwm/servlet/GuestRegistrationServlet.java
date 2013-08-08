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
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.*;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.servlet.GuestRegistrationBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.ActionConfiguration;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Message;
import password.pwm.util.*;
import password.pwm.util.operations.*;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Servlet for creating new guest users (helpdesk/admin registration)
 *
 * @author Jason D. Rivard, Menno Pieters
 */
public class GuestRegistrationServlet extends TopServlet {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(GuestRegistrationServlet.class);

    private static final String HTTP_PARAM_EXPIRATION_DATE = "__expirationDate__";

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp) throws ServletException, 
            	ChaiUnavailableException, IOException, PwmUnrecoverableException {
        //Fetch the session state bean.
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        final String actionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);
        final Configuration config = pwmApplication.getConfig();

        if (!config.readSettingAsBoolean(PwmSetting.GUEST_ENABLE)) {
            ssBean.setSessionError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (!Permission.checkPermission(Permission.GUEST_REGISTRATION, pwmSession, pwmApplication)) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        final String menuSelection = Validator.readStringFromRequest(req,"menuSelect");
        if (menuSelection != null && menuSelection.length() > 0) {
            pwmSession.getGuestRegistrationBean().setMenumode(menuSelection);
        }


        checkConfiguration(config, ssBean.getLocale());

        if (actionParam != null && actionParam.length() > 0) {
            Validator.validatePwmFormID(req);
            if ("create".equalsIgnoreCase(actionParam)) {
                handleCreateRequest(req,resp);
                return;
            } else if ("search".equalsIgnoreCase(actionParam)) {
                handleSearchRequest(req, resp);
                return;
            } else if ("update".equalsIgnoreCase(actionParam)) {
                handleUpdateRequest(req, resp);
                return;
            }
        }

        this.forwardToJSP(req, resp);
    }

    protected void handleUpdateRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    ) throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException {
        //Fetch the session state bean.
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final GuestRegistrationBean guBean = pwmSession.getGuestRegistrationBean();
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final Configuration config = pwmApplication.getConfig();

        final List<FormConfiguration> formItems = pwmApplication.getConfig().readSettingAsForm(PwmSetting.GUEST_UPDATE_FORM);
        final String expirationAttribute = config.readSettingAsString(PwmSetting.GUEST_EXPIRATION_ATTRIBUTE);

        Validator.validatePwmFormID(req);

        try {
            //read the values from the request
            final Map<FormConfiguration,String> formValues = Validator.readFormValuesFromRequest(req, formItems, ssBean.getLocale());

            // see if the values meet form requirements.
            Validator.validateParmValuesMeetRequirements(formValues, ssBean.getLocale());

            //read current values from user.
            final ChaiUser theGuest = ChaiFactory.createChaiUser(guBean.getUpdateUserDN(), pwmSession.getSessionManager().getChaiProvider());

            // check unique fields against ldap
            Validator.validateAttributeUniqueness(
                    pwmApplication,
                    pwmApplication.getProxyChaiProvider(),
                    formValues,
                    ssBean.getLocale(),
                    pwmSession.getSessionManager(),
                    Collections.singletonList(guBean.getUpdateUserDN())
            );

            final Date expirationDate = readExpirationFromRequest(pwmSession, req);

            // Update user attributes
            Helper.writeFormValuesToLdap(pwmApplication, pwmSession, theGuest, formValues, false);

            // Write expirationDate
            if (expirationDate != null) {
                theGuest.writeDateAttribute(expirationAttribute, expirationDate);
            }

            // send email.
            final UserInfoBean guestUserInfoBean = new UserInfoBean();
            UserStatusHelper.populateUserInfoBean(
                    pwmSession,
                    guestUserInfoBean,
                    pwmApplication,
                    pwmSession.getSessionStateBean().getLocale(),
                    theGuest.getEntryDN(),
                    null,
                    theGuest.getChaiProvider()
            );
            this.sendUpdateGuestEmailConfirmation(pwmSession, pwmApplication, theGuest, guestUserInfoBean);

            //everything good so forward to confirmation page.
            ssBean.setSessionSuccess(Message.SUCCESS_UPDATE_GUEST, null);

            pwmApplication.getStatisticsManager().incrementValue(Statistic.UPDATED_GUESTS);
            ServletHelper.forwardToSuccessPage(req, resp);
            return;
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmSession, e.getErrorInformation().toDebugStr());
            ssBean.setSessionError(e.getErrorInformation());
        } catch (ChaiOperationException e) {
            final ErrorInformation info = new ErrorInformation(PwmError.ERROR_UNKNOWN, "unexpected error writing to ldap: " + e.getMessage());
            LOGGER.error(pwmSession, info);
            ssBean.setSessionError(info);
        }
        forwardToUpdateJSP(req,resp);
    }

    private void sendUpdateGuestEmailConfirmation(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final ChaiUser theGuest,
            final UserInfoBean guestUserInfoBean
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_UPDATEGUEST, locale);

        final String toAddress = guestUserInfoBean.getUserEmailAddress();
        if (toAddress == null || toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send updated guest user email: no email configured");
            return;
        }

        pwmApplication.sendEmailUsingQueue(new EmailItemBean(
                toAddress,
                configuredEmailSetting.getFrom(),
                configuredEmailSetting.getSubject(),
                configuredEmailSetting.getBodyPlain(),
                configuredEmailSetting.getBodyHtml()
        ), guestUserInfoBean, new UserDataReader(theGuest));
    }

    protected void handleSearchRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
    	LOGGER.trace("Enter: handleSearchRequest(...)");
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final ChaiProvider chaiProvider = pwmSession.getSessionManager().getChaiProvider();
        final Configuration config = pwmApplication.getConfig();

        final String adminDnAttribute = config.readSettingAsString(PwmSetting.GUEST_ADMIN_ATTRIBUTE);
        final Boolean origAdminOnly = config.readSettingAsBoolean(PwmSetting.GUEST_EDIT_ORIG_ADMIN_ONLY);

        final String usernameParam = Validator.readStringFromRequest(req, "username");
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final GuestRegistrationBean guBean = pwmSession.getGuestRegistrationBean();

        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        searchConfiguration.setChaiProvider(chaiProvider);
        searchConfiguration.setContexts(Collections.singletonList(config.readSettingAsString(PwmSetting.GUEST_CONTEXT)));
        searchConfiguration.setEnableContextValidation(false);
        searchConfiguration.setUsername(usernameParam);
        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);

        try {
            final ChaiUser theGuest = userSearchEngine.performSingleUserSearch(pwmSession, searchConfiguration);
            final FormMap formProps = pwmSession.getSessionStateBean().getLastParameterValues();
            try {
                final List<FormConfiguration> guestUpdateForm = config.readSettingAsForm(PwmSetting.GUEST_UPDATE_FORM);
                final Set<String> involvedAttrs = new HashSet<String>();
                for (final FormConfiguration formItem : guestUpdateForm) {
                    if (!formItem.getName().equalsIgnoreCase("__accountDuration__")) {
                        involvedAttrs.add(formItem.getName());
                    }
                }
                final Map<String,String> userAttrValues = theGuest.readStringAttributes(involvedAttrs);
                if (origAdminOnly && adminDnAttribute != null && adminDnAttribute.length() > 0) {
                    final String origAdminDn = userAttrValues.get(adminDnAttribute);
                    if (origAdminDn != null && origAdminDn.length() > 0) {
                        if (!pwmSession.getUserInfoBean().getUserDN().equalsIgnoreCase(origAdminDn)) {
                            final ErrorInformation info = new ErrorInformation(PwmError.ERROR_ORIG_ADMIN_ONLY);
                            ssBean.setSessionError(info);
                            LOGGER.warn(pwmSession, info);
                            this.forwardToJSP(req, resp);
                        }
                    }
                }
                final String expirationAttribute = config.readSettingAsString(PwmSetting.GUEST_EXPIRATION_ATTRIBUTE);
                if (expirationAttribute != null && expirationAttribute.length() > 0) {
                    final Date expiration = theGuest.readDateAttribute(expirationAttribute);
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

                guBean.setUpdateUserDN(theGuest.getEntryDN());

                this.forwardToUpdateJSP(req, resp);
                return;
            } catch (ChaiOperationException e) {
                LOGGER.warn(pwmSession, "error reading current attributes for user: " + e.getMessage());
            }
        } catch (PwmOperationalException e) {
            final ErrorInformation error = e.getErrorInformation();
            ssBean.setSessionError(error);
            this.forwardToJSP(req, resp);
            return;
        }
        this.forwardToJSP(req, resp);
    }


    private void handleCreateRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = ssBean.getLocale();

        final List<FormConfiguration> guestUserForm = config.readSettingAsForm(PwmSetting.GUEST_FORM);

        try {
            //read the values from the request
            final Map<FormConfiguration, String> formValues = Validator.readFormValuesFromRequest(req, guestUserForm, locale);

            //read the expiration date from the request.
            final Date expirationDate = readExpirationFromRequest(pwmSession, req);

            // see if the values meet form requirements.
            Validator.validateParmValuesMeetRequirements(formValues, locale);

            // get new user DN
            final String guestUserDN = determineUserDN(formValues, config);

            // get a chai provider to make the user
            final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();

            // set up the user creation attributes
            final Map<String,String> createAttributes = new HashMap<String, String>();
            for (final FormConfiguration formItem : formValues.keySet()) {
                LOGGER.debug(pwmSession, "Attribute from form: "+ formItem.getName()+" = "+formValues.get(formItem));
                final String n = formItem.getName();
                final String v = formValues.get(formItem);
                if (n != null && n.length() > 0 && v != null && v.length() > 0) {
                    createAttributes.put(n, v);
                }
            }

            // Write creator DN
            createAttributes.put(config.readSettingAsString(PwmSetting.GUEST_ADMIN_ATTRIBUTE), pwmSession.getUserInfoBean().getUserDN());

            // read the creation object classes.
            final Set<String> createObjectClasses = new HashSet<String>(config.readSettingAsStringArray(PwmSetting.DEFAULT_OBJECT_CLASSES));

            provider.createEntry(guestUserDN, createObjectClasses, createAttributes);
            LOGGER.info(pwmSession, "created user object: " + guestUserDN);

            final ChaiUser theUser = ChaiFactory.createChaiUser(guestUserDN, provider);

            // write the expiration date:
            if (expirationDate != null) {
                final String expirationAttr =config.readSettingAsString(PwmSetting.GUEST_EXPIRATION_ATTRIBUTE);
                theUser.writeDateAttribute(expirationAttr, expirationDate);
            }

            final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(pwmApplication, pwmSession, theUser, locale);
            final String newPassword = RandomPasswordGenerator.createRandomPassword(pwmSession, passwordPolicy, pwmApplication);
            theUser.setPassword(newPassword);
            final UserInfoBean guestUserInfoBean = new UserInfoBean();
            UserStatusHelper.populateUserInfoBean(
                    pwmSession,
                    guestUserInfoBean,
                    pwmApplication,
                    pwmSession.getSessionStateBean().getLocale(),
                    theUser.getEntryDN(),
                    newPassword,
                    theUser.getChaiProvider()
            );


            {  // execute configured actions
                LOGGER.debug(pwmSession, "executing configured actions to user " + theUser.getEntryDN());
                final List<ActionConfiguration> actions = pwmApplication.getConfig().readSettingAsAction(PwmSetting.GUEST_WRITE_ATTRIBUTES);
                final ActionExecutor.ActionExecutorSettings settings = new ActionExecutor.ActionExecutorSettings();
                settings.setExpandPwmMacros(true);
                settings.setUserInfoBean(guestUserInfoBean);
                settings.setUser(theUser);
                final ActionExecutor actionExecutor = new ActionExecutor(pwmApplication);
                actionExecutor.executeActions(actions, settings, pwmSession);
            }

            //everything good so forward to success page.
            this.sendGuestUserEmailConfirmation(pwmSession, pwmApplication, theUser, guestUserInfoBean);
            ssBean.setSessionSuccess(Message.SUCCESS_CREATE_GUEST, null);

            pwmApplication.getStatisticsManager().incrementValue(Statistic.NEW_USERS);

            ServletHelper.forwardToSuccessPage(req, resp);
        } catch (ChaiOperationException e) {
            final ErrorInformation info = new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE, "error creating user: " + e.getMessage());
            ssBean.setSessionError(info);
            LOGGER.warn(pwmSession, info);
            this.forwardToJSP(req, resp);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmSession, e.getErrorInformation().toDebugStr());
            ssBean.setSessionError(e.getErrorInformation());
            this.forwardToJSP(req, resp);
        }
    }

    private static Date readExpirationFromRequest(
            final PwmSession pwmSession,
            final HttpServletRequest req
    )
            throws PwmOperationalException, ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final Configuration config = pwmApplication.getConfig();
        final long durationValueDays = config.readSettingAsLong(PwmSetting.GUEST_MAX_VALID_DAYS);
        final String expirationAttribute = config.readSettingAsString(PwmSetting.GUEST_EXPIRATION_ATTRIBUTE);

        if (durationValueDays == 0 || expirationAttribute == null || expirationAttribute.length() <= 0) {
            return null;
        }

        final String expirationDateStr = Validator.readStringFromRequest(req, HTTP_PARAM_EXPIRATION_DATE);

        Date expirationDate;
        try {
            expirationDate = new SimpleDateFormat("yyyy-MM-dd").parse(expirationDateStr);
        } catch (ParseException e) {
            final String errorMsg = "unable to read expiration date value: " + e.getMessage();
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_FIELD_REQUIRED,errorMsg));
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

        LOGGER.trace(pwmSession,"read expiration date as " + expirationDate.toString());
        return expirationDate;
    }

    private static String determineUserDN(final Map<FormConfiguration, String> formValues, final Configuration config)
            throws PwmUnrecoverableException
    {
        final String namingAttribute = config.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
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
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final ChaiUser theGuest,
            final UserInfoBean guestUserInfoBean
    )
            throws PwmUnrecoverableException
    {
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_GUEST, locale);

        final String toAddress = guestUserInfoBean.getUserEmailAddress();

        if (toAddress == null || toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send guest registration email for '" + userInfoBean.getUserDN() + "' no email configured");
            return;
        }

        pwmApplication.sendEmailUsingQueue(new EmailItemBean(
                toAddress,
                configuredEmailSetting.getFrom(),
                configuredEmailSetting.getSubject(),
                configuredEmailSetting.getBodyPlain(),
                configuredEmailSetting.getBodyHtml()
        ), guestUserInfoBean, new UserDataReader(theGuest));
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        final GuestRegistrationBean grBean = PwmSession.getPwmSession(req).getGuestRegistrationBean();
        if ("search".equalsIgnoreCase(grBean.getMenumode())) {
            this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_GUEST_UPDATE_SEARCH).forward(req, resp);
        } else {
            this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_GUEST_REGISTRATION).forward(req, resp);
        }
    }

    private void forwardToUpdateJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_GUEST_UPDATE).forward(req, resp);
    }

    private static void checkConfiguration(final Configuration configuration, final Locale locale)
            throws PwmUnrecoverableException
    {
        final String ldapNamingattribute = configuration.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
        final List<FormConfiguration> formItems = configuration.readSettingAsForm(PwmSetting.GUEST_FORM);

        {
            boolean namingIsInForm = false;
            for (final FormConfiguration formItem : formItems) {
                if (ldapNamingattribute.equalsIgnoreCase(formItem.getName())) {
                    namingIsInForm = true;
                }
            }

            if (!namingIsInForm) {
                final String errorMsg = "ldap naming attribute '" + ldapNamingattribute + "' is not in form configuration, but is required";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG, errorMsg, new String[]{ldapNamingattribute});
                throw new PwmUnrecoverableException(errorInformation);
            }
        }
    }

}





