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

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.*;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.GuestRegistrationBean;
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
import password.pwm.util.*;
import password.pwm.util.stats.Statistic;
import password.pwm.wordlist.SeedlistManager;

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
            final HttpServletResponse resp
    )
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
        //Fetch the session state bean.
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        final String actionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST, 255);
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
        final Properties notifyAttrs = new Properties();
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final Configuration config = pwmApplication.getConfig();

        final List<FormConfiguration> formConfigurations = pwmApplication.getConfig().readSettingAsForm(PwmSetting.GUEST_UPDATE_FORM,ssBean.getLocale());
        final String expirationAttribute = config.readSettingAsString(PwmSetting.GUEST_EXPIRATION_ATTRIBUTE);

        Validator.validatePwmFormID(req);

        try {
            //read the values from the request
            final Map<FormConfiguration,String> formValues = Validator.readFormValuesFromRequest(req, formConfigurations);

            // see if the values meet form requirements.
            Validator.validateParmValuesMeetRequirements(formValues);

            //read current values from user.
            final ChaiUser theGuest = ChaiFactory.createChaiUser(guBean.getUpdateUserDN(), pwmSession.getSessionManager().getChaiProvider());

            final Map<String, String> updateAttrs = new HashMap<String, String>();
            for (final FormConfiguration formConfiguration : formValues.keySet()) {
                if ( formConfiguration.getType() != FormConfiguration.Type.READONLY) {
                    final String attrName = formConfiguration.getAttributeName();
                    updateAttrs.put(attrName, formValues.get(formConfiguration));
                    notifyAttrs.put(attrName, formValues.get(formConfiguration));
                }
            }

            // strip out non-changing values
            final Map<String, String> currentValues = theGuest.readStringAttributes(updateAttrs.keySet());
            for (Iterator<FormConfiguration> iterator = formValues.keySet().iterator(); iterator.hasNext(); ) {
                FormConfiguration formConfiguration = iterator.next();
                final String attrName = formConfiguration.getAttributeName();
                if (updateAttrs.get(attrName) == null || updateAttrs.get(attrName).equals(currentValues.get(attrName))) {
                    updateAttrs.remove(attrName);
                    iterator.remove();
                }
            }

            // check unique fields against ldap
            final List<String> uniqueAttributes = config.readSettingAsStringArray(PwmSetting.NEWUSER_UNIQUE_ATTRIBUES);
            Validator.validateAttributeUniqueness(pwmApplication.getProxyChaiProvider(), config, formValues, uniqueAttributes);

            final Date expirationDate = readExpirationFromRequest(pwmSession, req);


            // Update user attributes
            Helper.writeMapToLdap(pwmSession, theGuest, updateAttrs);

            // Write expirationDate
            if (expirationDate != null) {
                theGuest.writeDateAttribute(expirationAttribute, expirationDate);
                notifyAttrs.put(expirationAttribute, expirationDate.toString());
            }

            //everything good so forward to confirmation page.
            this.sendUpdateGuestEmailConfirmation(pwmSession, pwmApplication, notifyAttrs);
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

    private void sendUpdateGuestEmailConfirmation(final PwmSession pwmSession, final PwmApplication pwmApplication, final Properties attrs)
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();

        final String fromAddress = config.readSettingAsLocalizedString(PwmSetting.EMAIL_UPDATEGUEST_FROM, locale);
        final String subject = config.readSettingAsLocalizedString(PwmSetting.EMAIL_UPDATEGUEST_SUBJECT, locale);
        final String plainBody = Helper.replaceAllPatterns(config.readSettingAsLocalizedString(PwmSetting.EMAIL_UPDATEGUEST_BODY, locale), attrs);
        final String htmlBody = Helper.replaceAllPatterns(config.readSettingAsLocalizedString(PwmSetting.EMAIL_UPDATEGUEST_BODY_HTML, locale), attrs);

        final String toAddress = attrs.getProperty(config.readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE));
        if (toAddress == null || toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send updated guest user email: no email configured");
            return;
        }

        pwmApplication.sendEmailUsingQueue(new EmailItemBean(toAddress, fromAddress, subject, plainBody, htmlBody));
    }

    protected void handleSearchRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    ) throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final Configuration config = pwmApplication.getConfig();
        final String namingAttribute = config.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
        final String usernameParam = Validator.readStringFromRequest(req, "username", 256);
        final String searchContext = config.readSettingAsString(PwmSetting.GUEST_CONTEXT);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final GuestRegistrationBean guBean = pwmSession.getGuestRegistrationBean();

        try {
            final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
            final Map<String, String> filterClauses = new HashMap<String, String>();
            filterClauses.put(namingAttribute, usernameParam);
            final SearchHelper searchHelper = new SearchHelper();
            searchHelper.setFilterAnd(filterClauses);

            final Set<String> resultDNs = new HashSet<String>(provider.search(searchContext, searchHelper).keySet());
            if (resultDNs.size() > 1) {
                final ErrorInformation error = new ErrorInformation(PwmError.ERROR_MULTI_USERNAME, null, usernameParam);
                ssBean.setSessionError(error);
                this.forwardToJSP(req, resp);
                return;
            }
            if (resultDNs.size() == 0) {
                final ErrorInformation error = new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER, null, usernameParam);
                ssBean.setSessionError(error);
                this.forwardToJSP(req, resp);
                return;
            }
            final String userDN = resultDNs.iterator().next();
            guBean.setUpdateUserDN(null);
            final ChaiUser theGuest = ChaiFactory.createChaiUser(userDN, provider);
            final Properties formProps = pwmSession.getSessionStateBean().getLastParameterValues();
            try {
                final List<FormConfiguration> updateParams = config.readSettingAsForm(PwmSetting.GUEST_UPDATE_FORM,ssBean.getLocale());
                final Set<String> involvedAttrs = new HashSet<String>();
                for (final FormConfiguration formConfiguration : updateParams) {
                    if (!formConfiguration.getAttributeName().equalsIgnoreCase("__accountDuration__")) {
                        involvedAttrs.add(formConfiguration.getAttributeName());
                    }
                }
                final Map<String,String> userAttrValues = provider.readStringAttributes(userDN, involvedAttrs);
                final String adminDnAttribute = config.readSettingAsString(PwmSetting.GUEST_ADMIN_ATTRIBUTE);
                final Boolean origAdminOnly = config.readSettingAsBoolean(PwmSetting.GUEST_EDIT_ORIG_ADMIN_ONLY);
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

                for (final FormConfiguration formConfiguration : updateParams) {
                    final String key = formConfiguration.getAttributeName();
                    final String value = userAttrValues.get(key);
                    if (value != null) {
                        formProps.setProperty(key, value);
                    }
                }

                guBean.setUpdateUserDN(userDN);

                this.forwardToUpdateJSP(req, resp);
                return;
            } catch (ChaiOperationException e) {
                LOGGER.warn(pwmSession, "error reading current attributes for user: " + e.getMessage());
            }
        } catch (ChaiOperationException e) {
            final ErrorInformation info = new ErrorInformation(PwmError.ERROR_UNKNOWN, "error searching for guest user: " + e.getMessage());
            ssBean.setSessionError(info);
            LOGGER.warn(pwmSession, info);
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
        final Locale locale = Locale.getDefault();
        Properties notifyAttrs = new Properties();

        final List<FormConfiguration> guestUserForm = config.readSettingAsForm(PwmSetting.GUEST_FORM, ssBean.getLocale());

        try {
            //read the values from the request
            final Map<FormConfiguration, String> formValues = Validator.readFormValuesFromRequest(req, guestUserForm);

            //read the expiration date from the request.
            final Date expirationDate = readExpirationFromRequest(pwmSession, req);

            // see if the values meet form requirements.
            Validator.validateParmValuesMeetRequirements(formValues);

            // check unique fields against ldap
            Validator.validateAttributeUniqueness(pwmApplication.getProxyChaiProvider(), config, formValues, config.readSettingAsStringArray(PwmSetting.GUEST_UNIQUE_ATTRIBUTES));

            // get new user DN
            final String guestUserDN = determineUserDN(formValues, config);

            // get a chai provider to make the user
            final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();

            // set up the user creation attributes
            final Map<String,String> createAttributes = new HashMap<String, String>();
            for (final FormConfiguration formConfiguration : formValues.keySet()) {
                LOGGER.debug(pwmSession, "Attribute from form: "+formConfiguration.getAttributeName()+" = "+formValues.get(formConfiguration));
                final String n = formConfiguration.getAttributeName();
                final String v = formValues.get(formConfiguration);
                if (n != null && n.length() > 0 && v != null && v.length() > 0) {
                    createAttributes.put(n, v);
                    notifyAttrs.put(n, v);
                }
            }

            // Write creator DN
            createAttributes.put(config.readSettingAsString(PwmSetting.GUEST_ADMIN_ATTRIBUTE), pwmSession.getUserInfoBean().getUserDN());

            // read the creation object classes.
            final Set<String> createObjectClasses = new HashSet<String>(config.readSettingAsStringArray(PwmSetting.DEFAULT_OBJECT_CLASSES));

            provider.createEntry(guestUserDN, createObjectClasses, createAttributes);
            LOGGER.info(pwmSession, "created user object: " + guestUserDN);

            final ChaiUser theUser = ChaiFactory.createChaiUser(guestUserDN, provider);

            // write out configured attributes.
            LOGGER.debug(pwmSession, "writing guestUser.writeAttributes to user " + theUser.getEntryDN());
            final List<String> configValues = config.readSettingAsStringArray(PwmSetting.GUEST_WRITE_ATTRIBUTES);
            final Map<String, String> configNameValuePairs = Configuration.convertStringListToNameValuePair(configValues, "=");
            Helper.writeMapToLdap(pwmSession, theUser, configNameValuePairs);
            for (final String key : configNameValuePairs.keySet()) {
                notifyAttrs.put(key, configNameValuePairs.get(key));
            }

            // write the expiration date:
            if (expirationDate != null) {
                final String expirationAttr =config.readSettingAsString(PwmSetting.GUEST_EXPIRATION_ATTRIBUTE);
                theUser.writeDateAttribute(expirationAttr,expirationDate);
                notifyAttrs.put(expirationAttr,new SimpleDateFormat().format(expirationDate));
            }

            final PwmPasswordPolicy passwordPolicy = PwmPasswordPolicy.createPwmPasswordPolicy(pwmSession, pwmApplication, locale, theUser);
            final SeedlistManager seedlistManager = pwmApplication.getSeedlistManager();
            final String newPassword = RandomPasswordGenerator.createRandomPassword(pwmSession, passwordPolicy, seedlistManager, pwmApplication);
            theUser.setPassword(newPassword);
            notifyAttrs.put("password", newPassword);

            //everything good so forward to success page.
            this.sendGuestUserEmailConfirmation(pwmSession, pwmApplication, notifyAttrs);
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
        for (final FormConfiguration formConfiguration : formValues.keySet()) {
            if (namingAttribute.equals(formConfiguration.getAttributeName())) {
                final String namingValue = formValues.get(formConfiguration);
                final String gestUserContextDN = config.readSettingAsString(PwmSetting.GUEST_CONTEXT);
                return namingAttribute + "=" + namingValue + "," + gestUserContextDN;
            }
        }
        final String errorMsg = "unable to determine new user DN due to missing form value for naming attribute '" + namingAttribute + '"';
        throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg));
    }

    private void sendGuestUserEmailConfirmation(final PwmSession pwmSession, final PwmApplication pwmApplication, final Properties attrs) throws PwmUnrecoverableException {
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();

        final String fromAddress = config.readSettingAsLocalizedString(PwmSetting.EMAIL_GUEST_FROM, locale);
        final String subject = config.readSettingAsLocalizedString(PwmSetting.EMAIL_GUEST_SUBJECT, locale);
        final String plainBody = Helper.replaceAllPatterns(config.readSettingAsLocalizedString(PwmSetting.EMAIL_GUEST_BODY, locale), attrs);
        final String htmlBody = Helper.replaceAllPatterns(config.readSettingAsLocalizedString(PwmSetting.EMAIL_GUEST_BODY_HTML, locale), attrs);

        final String toAddress = attrs.getProperty(config.readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE));

        if (toAddress == null || toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send guest registration email for '" + userInfoBean.getUserDN() + "' no email configured");
            return;
        }

        pwmApplication.sendEmailUsingQueue(new EmailItemBean(toAddress, fromAddress, subject, plainBody, htmlBody));
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
        final List<FormConfiguration> formConfigurations = configuration.readSettingAsForm(PwmSetting.GUEST_FORM,locale);
        final List<String> uniqueAttributes = configuration.readSettingAsStringArray(PwmSetting.GUEST_UNIQUE_ATTRIBUTES);

        {
            boolean namingIsInForm = false;
            for (final FormConfiguration formConfiguration : formConfigurations) {
                if (ldapNamingattribute.equalsIgnoreCase(formConfiguration.getAttributeName())) {
                    namingIsInForm = true;
                }
            }

            if (!namingIsInForm) {
                final String errorMsg = "ldap naming attribute '" + ldapNamingattribute + "' is not in form configuration, but is required";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG, errorMsg, ldapNamingattribute);
                throw new PwmUnrecoverableException(errorInformation);
            }
        }

        {
            boolean namingIsInUnique = false;
            for (final String uniqueAttr : uniqueAttributes) {
                if (ldapNamingattribute.equalsIgnoreCase(uniqueAttr)) {
                    namingIsInUnique = true;
                }
            }

            if (!namingIsInUnique) {
                final String errorMsg = "ldap naming attribute '" + ldapNamingattribute + "' is not in unique attribute configuration, but is required";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG, errorMsg, ldapNamingattribute);
                throw new PwmUnrecoverableException(errorInformation);
            }
        }
    }

}





