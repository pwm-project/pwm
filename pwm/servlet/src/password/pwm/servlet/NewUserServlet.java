/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

import com.novell.ldapchai.ChaiConstant;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ImpossiblePasswordPolicyException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.*;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.NewUserServletBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.*;
import password.pwm.error.*;
import password.pwm.util.Helper;
import password.pwm.util.IntruderManager;
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
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(NewUserServlet.class);

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException {
        //Fetch the session state bean.
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        final String actionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST, 255);
        final IntruderManager intruderMgr = pwmSession.getContextManager().getIntruderManager();
        final Configuration config = pwmSession.getConfig();

        if (!config.readSettingAsBoolean(PwmSetting.NEWUSER_ENABLE)) {
            ssBean.setSessionError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        final NewUserServletBean nuBean = pwmSession.getNewUserServletBean();

        if (actionParam != null && actionParam.equalsIgnoreCase("create")) {
            final Map<String, FormConfiguration> creationParams = nuBean.getCreationParams();

            Validator.validatePwmFormID(req);

            //read the values from the request
            try {
                Validator.updateParamValues(pwmSession, req, creationParams);
            } catch (PwmDataValidationException e) {
                ssBean.setSessionError(e.getErrorInformation());
                this.forwardToJSP(req, resp);
                return;
            }

            // see if the values meet form requirements.
            try {
                Validator.validateParmValuesMeetRequirements(creationParams, pwmSession);
            } catch (PwmDataValidationException e) {
                ssBean.setSessionError(e.getErrorInformation());
                intruderMgr.addBadAddressAttempt(pwmSession);
                this.forwardToJSP(req, resp);
                return;
            }

            // verify naming attribute is present
            {
                final FormConfiguration formConfig = creationParams.get(config.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE));
                if (formConfig == null || formConfig.getValue() == null || formConfig.getValue().length() < 1) {
                    ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_MISSING_NAMING_ATTR));
                    intruderMgr.addBadAddressAttempt(pwmSession);
                    this.forwardToJSP(req, resp);
                    return;
                }
            }

            // check unique fields against ldap
            for (final String attr : config.readStringArraySetting(PwmSetting.NEWUSER_UNIQUE_ATTRIBUES)) {
                final FormConfiguration paramConfig = creationParams.get(attr);
                try {
                    validateAttributeUniqueness(pwmSession, paramConfig, nuBean.getCreateUserDN());
                } catch (PwmDataValidationException e) {
                    ssBean.setSessionError(e.getErrorInformation());
                    intruderMgr.addBadAddressAttempt(pwmSession);
                    this.forwardToJSP(req, resp);
                    return;
                }
            }

            //create user
            try {
                final ChaiProvider provider = pwmSession.getContextManager().getProxyChaiProvider();
                final String namingValue = creationParams.get(config.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE)).getValue();

                final StringBuilder dn = new StringBuilder();
                dn.append(config.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE)).append("=");
                dn.append(namingValue);
                dn.append(",");
                dn.append(config.readSettingAsString(PwmSetting.NEWUSER_CONTEXT));

                final Properties createAttrs = new Properties();
                for (final String key : creationParams.keySet()) {
                    final FormConfiguration formConfig = creationParams.get(key);
                    createAttrs.put(formConfig.getAttributeName(), formConfig.getValue());
                }

                List<String> createObjectClasses = config.readStringArraySetting(PwmSetting.DEFAULT_OBJECT_CLASSES);
                if (createObjectClasses == null || createObjectClasses.isEmpty()) {
                	createObjectClasses = new ArrayList<String>();
                	createObjectClasses.add(ChaiConstant.OBJECTCLASS_BASE_LDAP_USER);
                }
                final Set<String> createObjectClassesSet = new HashSet<String>(createObjectClasses);
                provider.createEntry(dn.toString(), createObjectClassesSet, createAttrs);

                nuBean.setCreateUserDN(dn.toString());

                LOGGER.info(pwmSession, "created user object: " + dn.toString());
            } catch (ChaiOperationException e) {
                final ErrorInformation info = new ErrorInformation(PwmError.ERROR_UNKNOWN, "error creating user: " + e.getMessage());
                ssBean.setSessionError(info);
                LOGGER.warn(pwmSession, info);
                this.forwardToJSP(req, resp);
                return;
            } catch (NullPointerException e) {
                final ErrorInformation info = new ErrorInformation(PwmError.ERROR_UNKNOWN, "error creating user (missing cn/sn?): " + e.getMessage());
                ssBean.setSessionError(info);
                LOGGER.warn(pwmSession, info);
                this.forwardToJSP(req, resp);
                return;
            }

            try {
                final ChaiUser theUser = ChaiFactory.createChaiUser(nuBean.getCreateUserDN(), pwmSession.getContextManager().getProxyChaiProvider());

                // write out configured attributes.
                LOGGER.debug(pwmSession, "writing newUser.writeAttributes to user " + theUser.getEntryDN());
                final List<String> configValues = config.readStringArraySetting(PwmSetting.NEWUSER_WRITE_ATTRIBUTES);
                final Map<String, String> configNameValuePairs = Configuration.convertStringListToNameValuePair(configValues, "=");
                Helper.writeMapToEdir(pwmSession, theUser, configNameValuePairs);

                AuthenticationFilter.authUserWithUnknownPassword(theUser, pwmSession, req);
            } catch (ImpossiblePasswordPolicyException e) {
                final ErrorInformation info = new ErrorInformation(PwmError.ERROR_UNKNOWN, "unexpected ImpossiblePasswordPolicyException error while creating user");
                LOGGER.warn(pwmSession, info, e);
                ssBean.setSessionError(info);
                this.forwardToJSP(req, resp);
                return;
            } catch (PwmOperationalException e) {
                final ErrorInformation info = new ErrorInformation(PwmError.ERROR_UNKNOWN, "unexpected error writing to ldap: " + e.getMessage());
                LOGGER.warn(pwmSession, info, e);
                ssBean.setSessionError(info);
                ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
                return;
            }

            //everything good so forward to change password page.
            this.sendNewUserEmailConfirmation(pwmSession);
            ssBean.setSessionSuccess(Message.SUCCESS_CREATE_USER, null);

            pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.NEW_USERS);
            ServletHelper.forwardToSuccessPage(req, resp, this.getServletContext());
            return;
        }
        this.forwardToJSP(req, resp);
    }

    public static void validateAttributeUniqueness(
            final PwmSession pwmSession,
            final FormConfiguration paramConfig,
            final String userDN
    )
            throws PwmDataValidationException, ChaiUnavailableException {
        try {
            final ChaiProvider provider = pwmSession.getContextManager().getProxyChaiProvider();

            final Map<String, String> filterClauses = new HashMap<String, String>();
            filterClauses.put(ChaiConstant.ATTR_LDAP_OBJECTCLASS, ChaiConstant.OBJECTCLASS_BASE_LDAP_USER);
            filterClauses.put(paramConfig.getAttributeName(), paramConfig.getValue());

            final SearchHelper searchHelper = new SearchHelper();
            searchHelper.setFilterAnd(filterClauses);

            final Set<String> resultDNs = new HashSet<String>(provider.search(pwmSession.getConfig().readSettingAsString(PwmSetting.LDAP_CONTEXTLESS_ROOT), searchHelper).keySet());

            // remove the user DN from the result set.
            resultDNs.remove(userDN);

            if (resultDNs.size() > 0) {
                final ErrorInformation error = new ErrorInformation(PwmError.ERROR_FIELD_DUPLICATE, null, paramConfig.getLabel());
                throw new PwmDataValidationException(error);
            }
        } catch (ChaiOperationException e) {
            LOGGER.debug(e);
        }
    }

    private void sendNewUserEmailConfirmation(final PwmSession pwmSession) {
        final ContextManager theManager = pwmSession.getContextManager();
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
        final Configuration config = pwmSession.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();

        final String fromAddress = config.readLocalizedStringSetting(PwmSetting.EMAIL_NEWUSER_FROM, locale);
        final String subject = config.readLocalizedStringSetting(PwmSetting.EMAIL_NEWUSER_SUBJECT, locale);
        final String plainBody = config.readLocalizedStringSetting(PwmSetting.EMAIL_NEWUSER_BODY, locale);
        final String htmlBody = config.readLocalizedStringSetting(PwmSetting.EMAIL_NEWUSER_BODY_HTML, locale);

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
}

