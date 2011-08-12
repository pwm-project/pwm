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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.PwmRandom;
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

        final String actionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);
        final Configuration config = pwmSession.getConfig();

        if (!config.readSettingAsBoolean(PwmSetting.NEWUSER_ENABLE)) {
            ssBean.setSessionError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (pwmSession.getSessionStateBean().isAuthenticated()) {
            PwmSession.getPwmSession(req).getSessionStateBean().setSessionError(PwmError.ERROR_USERAUTHENTICATED.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        final List<HealthRecord> healthIssues = checkConfiguration(config,ssBean.getLocale());
        if (healthIssues != null && !healthIssues.isEmpty()) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,healthIssues.get(0).getDetail());
            throw new PwmUnrecoverableException(errorInformation);
        }

        if (actionParam != null && actionParam.length() > 1) {
            Validator.validatePwmFormID(req);
            if ("create".equalsIgnoreCase(actionParam)) {
                handleCreateRequest(req,resp);
            } else if ("validate".equalsIgnoreCase(actionParam)) {
                handleValidateForm(req, resp, pwmSession);
            }
            return;
        }

        this.forwardToJSP(req, resp);
    }


    /**
     * Handle requests for ajax feedback of user supplied responses.
     *
     * @param req          HttpRequest
     * @param resp         HttpResponse
     * @param pwmSession   An instance of the pwm session
     * @throws IOException              for an IO error
     * @throws ServletException         for an http servlet error
     * @throws password.pwm.error.PwmUnrecoverableException             for any unexpected error
     * @throws ChaiUnavailableException if the ldap directory becomes unavailable
     */
    protected static void handleValidateForm(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmSession pwmSession
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException {
        Validator.validatePwmFormID(req);

        boolean success = true;
        String userMessage = Message.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(), Message.SUCCESS_UNKNOWN, pwmSession.getConfig());

        final List<FormConfiguration> newUserForm = pwmSession.getConfig().readSettingAsForm(PwmSetting.NEWUSER_FORM, pwmSession.getSessionStateBean().getLocale());
        final Map<FormConfiguration, String> formValues;
        Map<String,String> userValues = readResponsesFromJsonRequest(req);

        try {
            //read the values from the request
            formValues = Validator.readFormValuesFromMap(userValues, newUserForm);

            // see if the values meet form requirements.
            Validator.validateParmValuesMeetRequirements(pwmSession, formValues);

            // check unique fields against ldap
            Validator.validateAttributeUniqueness(pwmSession, formValues, pwmSession.getConfig().readSettingAsStringArray(PwmSetting.NEWUSER_UNIQUE_ATTRIBUES));

            // test the password
            if (userValues.get("password1") == null || userValues.get("password1").length() <1 ) {
                throw new PwmOperationalException(PwmError.PASSWORD_MISSING);
            }
            PwmPasswordPolicy passwordPolicy=pwmSession.getConfig().getGlobalPasswordPolicy(pwmSession.getSessionStateBean().getLocale());
            Validator.testPasswordAgainstPolicy(userValues.get("password1"),pwmSession,false,passwordPolicy, false);

            if (userValues.get("password2") == null || userValues.get("password2").length() <1 ) {
                throw new PwmOperationalException(PwmError.PASSWORD_MISSING_CONFIRM);
            }

            if (!userValues.get("password1").equals(userValues.get("password2"))) {
                throw new PwmOperationalException(PwmError.PASSWORD_DOESNOTMATCH);
            }
        } catch (ChaiOperationException e) {
            success = false;
            userMessage = "ldap error checking unique attributes: " + e.getMessage();
        } catch (PwmOperationalException e) {
            success = false;
            userMessage = e.getErrorInformation().toUserStr(pwmSession);
        }

        final Map<String, String> outputMap = new HashMap<String, String>();
        outputMap.put("version", "1");
        outputMap.put("message", userMessage);
        outputMap.put("success", String.valueOf(success));

        final Gson gson = new Gson();
        final String output = gson.toJson(outputMap);

        resp.setContentType("text/plain;charset=utf-8");
        resp.getWriter().print(output);

        LOGGER.trace(pwmSession, "ajax validate responses: " + output);
    }





    private void handleCreateRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final Configuration config = pwmSession.getConfig();

        final List<FormConfiguration> newUserForm = config.readSettingAsForm(PwmSetting.NEWUSER_FORM, ssBean.getLocale());
        final Map<FormConfiguration, String> formValues;
        final String userPassword = Validator.readStringFromRequest(req,"password1");

        try {
            //read the values from the request
            formValues = Validator.readFormValuesFromRequest(req, newUserForm);

            // see if the values meet form requirements.
            Validator.validateParmValuesMeetRequirements(pwmSession, formValues);

            // check unique fields against ldap
            Validator.validateAttributeUniqueness(pwmSession, formValues, config.readSettingAsStringArray(PwmSetting.NEWUSER_UNIQUE_ATTRIBUES));

            // test the password
            PwmPasswordPolicy passwordPolicy=pwmSession.getConfig().getGlobalPasswordPolicy(ssBean.getLocale());
            Validator.testPasswordAgainstPolicy(userPassword,pwmSession,false,passwordPolicy, false);
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

        // get new user DN
        final String newUserDN = determineUserDN(formValues, config);

        try {

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

            //set password
            theUser.setPassword(Validator.readStringFromRequest(req,"password1"));

            // write out configured attributes.
            LOGGER.debug(pwmSession, "writing newUser.writeAttributes to user " + theUser.getEntryDN());
            final List<String> configValues = config.readSettingAsStringArray(PwmSetting.NEWUSER_WRITE_ATTRIBUTES);
            final Map<String, String> configNameValuePairs = Configuration.convertStringListToNameValuePair(configValues, "=");
            Helper.writeMapToLdap(pwmSession, theUser, configNameValuePairs);

            AuthenticationFilter.authenticateUser(theUser.getEntryDN(), userPassword, null, pwmSession, req.isSecure());

            //everything good so forward to change password page.
            this.sendNewUserEmailConfirmation(pwmSession);
            ssBean.setSessionSuccess(Message.SUCCESS_CREATE_USER, null);

            pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.NEW_USERS);

            ServletHelper.forwardToSuccessPage(req, resp, this.getServletContext());
        } catch (ChaiOperationException e) {
            if (config.readSettingAsBoolean(PwmSetting.NEWUSER_DELETE_ON_FAIL)) {
                deleteUserAccount(newUserDN,pwmSession);
            }
            final ErrorInformation info = new ErrorInformation(PwmError.ERROR_NEW_USER_FAILURE, "error creating user: " + e.getMessage());
            ssBean.setSessionError(info);
            LOGGER.warn(pwmSession, info);
            this.forwardToJSP(req, resp);
        } catch (PwmOperationalException e) {
            if (config.readSettingAsBoolean(PwmSetting.NEWUSER_DELETE_ON_FAIL)) {
                deleteUserAccount(newUserDN,pwmSession);
            }
            LOGGER.error(pwmSession, e.getErrorInformation().toDebugStr());
            ssBean.setSessionError(e.getErrorInformation());
            this.forwardToJSP(req, resp);
        }
    }

    private static void deleteUserAccount(final String userDN, PwmSession pwmSession)
            throws PwmUnrecoverableException
    {
        try {
            LOGGER.warn("deleting ldap user account " + userDN);
            pwmSession.getContextManager().getProxyChaiProvider().deleteEntry(userDN);
            LOGGER.warn("ldap user account " + userDN + " has been deleted");
        } catch (ChaiUnavailableException e) {
            LOGGER.error("error deleting ldap user account " + userDN + ", " + e.getMessage());
        } catch (ChaiOperationException e) {
            LOGGER.error("error deleting ldap user account " + userDN + ", " + e.getMessage());
        }
    }

    private static String determineUserDN(final Map<FormConfiguration, String> formValues, final Configuration config)
            throws PwmUnrecoverableException
    {
        final String namingAttribute = config.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
        String rdnValue = null;

        final String randUsernameChars = config.readSettingAsString(PwmSetting.NEWUSER_USERNAME_CHARS);
        final String newUserContextDN = config.readSettingAsString(PwmSetting.NEWUSER_CONTEXT);
        final int randUsernameLength = (int)config.readSettingAsLong(PwmSetting.NEWUSER_USERNAME_LENGTH);

        if (randUsernameChars != null && randUsernameChars.length() > 0) {
            final PwmRandom RANDOM = PwmRandom.getInstance();

            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < randUsernameLength; i++) {
                sb.append(randUsernameChars.charAt(RANDOM.nextInt(randUsernameChars.length())));
            }

            rdnValue = sb.toString();
        } else {
            for (final FormConfiguration formConfiguration : formValues.keySet()) {
                if (namingAttribute.equals(formConfiguration.getAttributeName())) {
                    rdnValue = formValues.get(formConfiguration);
                }
            }
        }

        if (rdnValue == null) {
            final String errorMsg = "unable to determine new user DN due to missing form value for naming attribute '" + namingAttribute + '"';
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg));
        }

        return namingAttribute + "=" + rdnValue + "," + newUserContextDN;
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

    public static List<HealthRecord> checkConfiguration(final Configuration configuration, final Locale locale)
    {
        final List<HealthRecord> returnRecords = new ArrayList<HealthRecord>();

        final String ldapNamingattribute = configuration.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
        final List<FormConfiguration> formConfigurations = configuration.readSettingAsForm(PwmSetting.NEWUSER_FORM,locale);
        final List<String> uniqueAttributes = configuration.readSettingAsStringArray(PwmSetting.NEWUSER_UNIQUE_ATTRIBUES);

        boolean usernameIsGenerated = false;
        {
            final String randUsernameChars = configuration.readSettingAsString(PwmSetting.NEWUSER_USERNAME_CHARS);
            final int randUsernameLength = (int)configuration.readSettingAsLong(PwmSetting.NEWUSER_USERNAME_LENGTH);
            if (randUsernameChars != null && randUsernameChars.length() > 0 && randUsernameLength > 0) {
                usernameIsGenerated = true;
            }
        }

        {
            boolean namingIsInForm = false;
            for (final FormConfiguration formConfiguration : formConfigurations) {
                if (ldapNamingattribute.equalsIgnoreCase(formConfiguration.getAttributeName())) {
                    namingIsInForm = true;
                }
            }

            if (!namingIsInForm && !usernameIsGenerated) {
                final StringBuilder errorMsg = new StringBuilder();
                errorMsg.append(PwmSetting.NEWUSER_FORM.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE));
                errorMsg.append(" -> ");
                errorMsg.append(PwmSetting.NEWUSER_FORM.getLabel(PwmConstants.DEFAULT_LOCALE));
                errorMsg.append(" does not contain the ldap naming attribute '").append(ldapNamingattribute).append("'");
                errorMsg.append(", but is required because it is the ldap naming attribute and the username is not being automatically generated.");
                returnRecords.add(new HealthRecord(HealthStatus.WARN,"Configuration",errorMsg.toString()));
            }
        }

        {
            boolean namingIsInUnique = false;
            for (final String uniqueAttr : uniqueAttributes) {
                if (ldapNamingattribute.equalsIgnoreCase(uniqueAttr)) {
                    namingIsInUnique = true;
                }
            }

            if (!namingIsInUnique && !usernameIsGenerated) {
                final StringBuilder errorMsg = new StringBuilder();
                errorMsg.append(PwmSetting.NEWUSER_UNIQUE_ATTRIBUES.getCategory().getLabel(PwmConstants.DEFAULT_LOCALE));
                errorMsg.append(" -> ");
                errorMsg.append(PwmSetting.NEWUSER_UNIQUE_ATTRIBUES.getLabel(PwmConstants.DEFAULT_LOCALE));
                errorMsg.append(" does not contain the ldap naming attribute '").append(ldapNamingattribute).append("'");
                errorMsg.append(", but is required because it is the ldap naming attribute and the username is not being automatically generated.");
                returnRecords.add(new HealthRecord(HealthStatus.WARN,"Configuration",errorMsg.toString()));
            }
        }
        return returnRecords;
    }

    private static Map<String, String> readResponsesFromJsonRequest(
            final HttpServletRequest req
    )
            throws IOException
    {
        final Map<String, String> inputMap = new HashMap<String, String>();

        final String bodyString = ServletHelper.readRequestBody(req, 10 * 1024);

        final Gson gson = new Gson();
        final Map<String, String> srcMap = gson.fromJson(bodyString, new TypeToken<Map<String, String>>() {
        }.getType());

        if (srcMap == null) {
            return Collections.emptyMap();
        }

        for (final String key : srcMap.keySet()) {
            final String paramValue = srcMap.get(key);
            inputMap.put(key, paramValue);
        }

        return inputMap;
    }

}


