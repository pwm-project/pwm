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
import com.novell.ldapchai.impl.edir.entry.EdirEntries;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.*;
import password.pwm.bean.EmailItemBean;
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
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.stats.Statistic;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.wordlist.SeedlistManager;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Servlet for creating new guest users (helpdesk/admin registration)
 *
 * @author Jason D. Rivard, Menno Pieters
 */
public class GuestRegistrationServlet extends TopServlet {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(GuestRegistrationServlet.class);

    private static final FormConfiguration DURATION_FORM_CONFIG = new FormConfiguration(1,5,FormConfiguration.Type.NUMBER,true,false,"Account Validity Duration (Days)","__accountDuration__");

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
        //Fetch the session state bean.
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        final String actionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST, 255);
        final Configuration config = pwmSession.getConfig();

        if (!config.readSettingAsBoolean(PwmSetting.GUEST_ENABLE)) {
            ssBean.setSessionError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (!Permission.checkPermission(Permission.GUEST_REGISTRATION, pwmSession)) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }


        checkConfiguration(config, ssBean.getLocale());

        if (actionParam != null && actionParam.equalsIgnoreCase("create")) {
            Validator.validatePwmFormID(req);
            handleCreateRequest(req,resp);
            return;
        }

        this.forwardToJSP(req, resp);
    }

    private void handleCreateRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final Configuration config = pwmSession.getConfig();
        final Locale locale = Locale.getDefault();
        Properties notifyAttrs = new Properties();
        
        final List<FormConfiguration> guestUserForm = config.readSettingAsForm(PwmSetting.GUEST_FORM, ssBean.getLocale());

        try {
            //read the values from the request
            final Map<FormConfiguration, String> formValues = Validator.readFormValuesFromRequest(req, guestUserForm);

            // see if the values meet form requirements.
            Validator.validateParmValuesMeetRequirements(formValues);

            // check unique fields against ldap
            Validator.validateAttributeUniqueness(pwmSession.getContextManager().getProxyChaiProvider(), config, formValues, config.readSettingAsStringArray(PwmSetting.GUEST_UNIQUE_ATTRIBUTES));

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

            // set duration value(s);
            createAttributes.putAll(handleDurationValue(pwmSession,req));
            notifyAttrs.put(config.readSettingAsString(PwmSetting.GUEST_EXPIRATION_ATTRIBUTE),readableDurationValue(pwmSession,req));

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

            final PwmPasswordPolicy passwordPolicy = PwmPasswordPolicy.createPwmPasswordPolicy(pwmSession, config, locale, theUser);
            final SeedlistManager seedlistManager = pwmSession.getContextManager().getSeedlistManager();
            final String newPassword = RandomPasswordGenerator.createRandomPassword(pwmSession, passwordPolicy, seedlistManager, pwmSession.getContextManager());
            theUser.setPassword(newPassword);
            notifyAttrs.put("password", newPassword);

            //everything good so forward to change password page.
            this.sendGuestUserEmailConfirmation(pwmSession, notifyAttrs);
            ssBean.setSessionSuccess(Message.SUCCESS_CREATE_GUEST, null);

            pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.NEW_USERS);

            ServletHelper.forwardToSuccessPage(req, resp, this.getServletContext());
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

    private static String readableDurationValue(
            final PwmSession pwmSession,
            final HttpServletRequest req
    )
            throws PwmOperationalException, ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException {
        final Configuration config = PwmSession.getPwmSession(req).getConfig();
        final String expirationAttribute = config.readSettingAsString(PwmSetting.GUEST_EXPIRATION_ATTRIBUTE);

        final String durationStringValue = Validator.readStringFromRequest(req, DURATION_FORM_CONFIG.getAttributeName());
        final int durationValue;
        try {
            durationValue = Integer.parseInt(durationStringValue);
        } catch (NumberFormatException e) {
            final String errorMsg = "unable to read expiration duration value: " + e.getMessage();
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_FIELD_NOT_A_NUMBER,errorMsg));
        }
        final long durationValueMs = durationValue * 24 * 60 * 60 * 1000;
        final long futureDateMs = System.currentTimeMillis() + durationValueMs;
        final Date futureDate = new Date(futureDateMs);
		final SimpleDateFormat nfmt = new SimpleDateFormat();
        final String dStr = nfmt.format(futureDate);
        LOGGER.debug(pwmSession,"figured expiration date for user attribute " + expirationAttribute + " (readable), value=" + dStr);
        return dStr;
    }
    
    private static Map<String,String> handleDurationValue(
            final PwmSession pwmSession,
            final HttpServletRequest req
    )
            throws PwmOperationalException, ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException {
        final Configuration config = PwmSession.getPwmSession(req).getConfig();
        final String expirationAttribute = config.readSettingAsString(PwmSetting.GUEST_EXPIRATION_ATTRIBUTE);

        final String durationStringValue = Validator.readStringFromRequest(req, DURATION_FORM_CONFIG.getAttributeName());
        final int durationValue;
        try {
            durationValue = Integer.parseInt(durationStringValue);
        } catch (NumberFormatException e) {
            final String errorMsg = "unable to read expiration duration value: " + e.getMessage();
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_FIELD_NOT_A_NUMBER,errorMsg));
        }

        final long durationValueMs = durationValue * 24 * 60 * 60 * 1000;
        final long futureDateMs = System.currentTimeMillis() + durationValueMs;
        final Date futureDate = new Date(futureDateMs);
        final String zuluDate = EdirEntries.convertDateToZulu(futureDate);

        final Map<String,String> props = new HashMap<String, String>();
        props.put(expirationAttribute, zuluDate);
        LOGGER.debug(pwmSession,"figured expiration date for user attribute " + expirationAttribute + ", value=" + zuluDate);
        return props;
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

    private void sendGuestUserEmailConfirmation(final PwmSession pwmSession, final Properties attrs) throws PwmUnrecoverableException {
        final ContextManager theManager = pwmSession.getContextManager();
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
        final Configuration config = pwmSession.getConfig();
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

        theManager.sendEmailUsingQueue(new EmailItemBean(toAddress, fromAddress, subject, plainBody, htmlBody));
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_GUEST_REGISTRATION).forward(req, resp);
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





