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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 * User interaction servlet for creating new users (self registration)
 *
 * @author Jason D. Rivard
 */
public class NewUserServlet extends TopServlet {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(NewUserServlet.class);

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

        if (!config.readSettingAsBoolean(PwmSetting.NEWUSER_ENABLE)) {
            ssBean.setSessionError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        checkConfiguration(config,ssBean.getLocale());

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

        final List<FormConfiguration> newUserForm = config.readSettingAsForm(PwmSetting.NEWUSER_FORM, ssBean.getLocale());
        final Map<FormConfiguration, String> formValues;
        try {
            //read the values from the request
            formValues = Validator.readFormValuesFromRequest(req, newUserForm);

            // see if the values meet form requirements.
            Validator.validateParmValuesMeetRequirements(pwmSession, formValues);

            // check unique fields against ldap
            Validator.validateAttributeUniqueness(pwmSession, formValues, config.readSettingAsStringArray(PwmSetting.NEWUSER_UNIQUE_ATTRIBUES));
        } catch (ChaiOperationException e) {
            final ErrorInformation info = new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE, "error creating user: " + e.getMessage());
            ssBean.setSessionError(info);
            LOGGER.debug(pwmSession, info);
            this.forwardToJSP(req, resp);
            return;
        } catch (PwmOperationalException e) {
            LOGGER.debug(pwmSession, e.getErrorInformation().toDebugStr());
            ssBean.setSessionError(e.getErrorInformation());
            this.forwardToJSP(req, resp);
            return;
        }

        try {
            // get new user DN
            final String newUserDN = determineUserDN(formValues, config);

            // get a chai provider to make the user
            final ChaiProvider provider = pwmSession.getContextManager().getProxyChaiProvider();

            // set up the user creation attributes
            final Map<String,String> createAttributes = new HashMap<String,String>();
            for (final FormConfiguration formConfiguration : formValues.keySet()) {
                createAttributes.put(formConfiguration.getAttributeName(), formValues.get(formConfiguration));
            }

            // read the creation object classes.
            final Set<String> createObjectClasses = new HashSet<String>(config.readSettingAsStringArray(PwmSetting.DEFAULT_OBJECT_CLASSES));

            provider.createEntry(newUserDN, createObjectClasses, createAttributes);
            LOGGER.info(pwmSession, "created user object: " + newUserDN);

            final ChaiUser theUser = ChaiFactory.createChaiUser(newUserDN, pwmSession.getContextManager().getProxyChaiProvider());

            // write out configured attributes.
            LOGGER.debug(pwmSession, "writing newUser.writeAttributes to user " + theUser.getEntryDN());
            final List<String> configValues = config.readSettingAsStringArray(PwmSetting.NEWUSER_WRITE_ATTRIBUTES);
            final Map<String, String> configNameValuePairs = Configuration.convertStringListToNameValuePair(configValues, "=");
            Helper.writeMapToLdap(pwmSession, theUser, configNameValuePairs);

            AuthenticationFilter.authUserWithUnknownPassword(theUser, pwmSession, req.isSecure());

            //everything good so forward to change password page.
            this.sendNewUserEmailConfirmation(pwmSession);
            ssBean.setSessionSuccess(Message.SUCCESS_CREATE_USER, null);

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

    private static String determineUserDN(final Map<FormConfiguration, String> formValues, final Configuration config)
            throws PwmUnrecoverableException
    {
        final String namingAttribute = config.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
        for (final FormConfiguration formConfiguration : formValues.keySet()) {
            if (namingAttribute.equals(formConfiguration.getAttributeName())) {
                final String namingValue = formValues.get(formConfiguration);
                final String newUserContextDN = config.readSettingAsString(PwmSetting.NEWUSER_CONTEXT);
                return namingAttribute + "=" + namingValue + "," + newUserContextDN;
            }
        }
        final String errorMsg = "unable to determine new user DN due to missing form value for naming attribute '" + namingAttribute + '"';
        throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg));
    }

    private void sendNewUserEmailConfirmation(final PwmSession pwmSession) throws PwmUnrecoverableException {
        final ContextManager theManager = pwmSession.getContextManager();
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
        final Configuration config = pwmSession.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();

        final String fromAddress = config.readSettingAsLocalizedString(PwmSetting.EMAIL_NEWUSER_FROM, locale);
        final String subject = config.readSettingAsLocalizedString(PwmSetting.EMAIL_NEWUSER_SUBJECT, locale);
        final String plainBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_NEWUSER_BODY, locale);
        final String htmlBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_NEWUSER_BODY_HTML, locale);

        final String toAddress = userInfoBean.getUserEmailAddress();

        if (toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send new user email for '" + userInfoBean.getUserDN() + "' no email configured");
            return;
        }

        theManager.sendEmailUsingQueue(new EmailItemBean(toAddress, fromAddress, subject, plainBody, htmlBody));
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_NEW_USER).forward(req, resp);
    }

    private static void checkConfiguration(final Configuration configuration, final Locale locale)
            throws PwmUnrecoverableException
    {
        final String ldapNamingattribute = configuration.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
        final List<FormConfiguration> formConfigurations = configuration.readSettingAsForm(PwmSetting.NEWUSER_FORM,locale);
        final List<String> uniqueAttributes = configuration.readSettingAsStringArray(PwmSetting.NEWUSER_UNIQUE_ATTRIBUES);

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


