/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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
import password.pwm.bean.NewUserServletBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.*;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmException;
import password.pwm.error.ValidationException;
import password.pwm.process.emailer.EmailEvent;
import password.pwm.util.IntruderManager;
import password.pwm.util.PwmLogger;
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

// -------------------------- STATIC METHODS --------------------------

    public static String validateParamsAgainstLDAP(
            final Map<String, ParameterConfig> paramConfigs,
            final PwmSession pwmSession
    )
            throws ChaiUnavailableException, ValidationException
    {
        // Returns the DN of the user objet if successfull or null if theres a failure;  @todo javadoc
        final String namingAttribute = pwmSession.getContextManager().getParameter(Constants.CONTEXT_PARAM.LDAP_NAMING_ATTRIBUTE);
        final ChaiUser theUser;

        //find the user
        {
            final ParameterConfig paramConfig = paramConfigs.get(namingAttribute);
            if (paramConfig != null) {
                final String username = paramConfig.getValue();
                final String userDN = UserStatusHelper.convertUsernameFieldtoDN(username, pwmSession, null);

                if (userDN == null) {
                    throw ValidationException.createValidationException(Message.ERROR_CANT_MATCH_USER.toInfo());
                }

                theUser = ChaiFactory.createChaiUser(userDN, pwmSession.getContextManager().getProxyChaiProvider());
            } else {
                throw ValidationException.createValidationException(Message.ERROR_CANT_MATCH_USER.toInfo());
            }
        }

        for (final String key : paramConfigs.keySet()) {
            final ParameterConfig paramConfig = paramConfigs.get(key);

            try {
                final String ldapValue = theUser.readStringAttribute(paramConfig.getAttributeName());
                boolean match = ldapValue != null && ldapValue.equalsIgnoreCase(paramConfig.getValue());

                if (!match && paramConfig.getType() == ParameterConfig.Type.INT) {
                    try {
                        final int ldapInt = Integer.parseInt(ldapValue);
                        final int paramInt = Integer.parseInt(paramConfig.getValue());

                        if (ldapInt == paramInt) {
                            match = true;
                        }
                    } catch (NumberFormatException e) {
                        //disregard
                    }
                }

                if (!match) {
                    throw ValidationException.createValidationException(Message.ERROR_NEW_USER_VALIDATION_FAILED.toInfo());
                }
            } catch (ChaiOperationException e) {
                //ignore
            }
        }
        return theUser.getEntryDN();
    }

// -------------------------- OTHER METHODS --------------------------

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, ChaiUnavailableException, IOException, PwmException
    {
        //Fetch the session state bean.
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        final String actionParam = Validator.readStringFromRequest(req, Constants.PARAM_ACTION_REQUEST, 255);
        final IntruderManager intruderMgr = pwmSession.getContextManager().getIntruderManager();
        final Configuration config = pwmSession.getConfig();

        if (!config.readSettingAsBoolean(PwmSetting.ENABLE_NEW_USER)) {
            ssBean.setSessionError(Message.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            Helper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        final NewUserServletBean nuBean = pwmSession.getNewUserServletBean();

        if (actionParam != null && actionParam.equalsIgnoreCase("create")) {
            final Map<String, ParameterConfig> creationParams = nuBean.getCreationParams();

            //read the values from the request
            try {
                Validator.updateParamValues(pwmSession, req, creationParams);
            } catch (ValidationException e) {
                ssBean.setSessionError(e.getError());
                this.forwardToJSP(req, resp);
                return;
            }

            // see if the values meet requirements.
            try {
                Validator.validateParmValuesMeetRequirements(creationParams, pwmSession);
            } catch (ValidationException e) {
                ssBean.setSessionError(e.getError());
                intruderMgr.addBadAddressAttempt(pwmSession);
                this.forwardToJSP(req, resp);
                return;
            }

            // check unique feilds
            for (final String attr : config.getNewUserCreationUniqueAttributes()) {
                final ParameterConfig paramConfig = creationParams.get(attr);
                try {
                    validateAttributeUniqueness(pwmSession, paramConfig, nuBean.getCreateUserDN());
                } catch (ValidationException e) {
                    ssBean.setSessionError(e.getError());
                    intruderMgr.addBadAddressAttempt(pwmSession);
                    this.forwardToJSP(req, resp);
                    return;
                }
            }

            //create user
            try {
                final ChaiProvider provider = pwmSession.getContextManager().getProxyChaiProvider();
                final String cn = creationParams.get(ChaiConstant.ATTR_LDAP_COMMON_NAME).getValue();
                final String sn = creationParams.get(ChaiConstant.ATTR_LDAP_SURNAME).getValue();

                final StringBuilder dn = new StringBuilder();
                dn.append(ChaiConstant.ATTR_LDAP_COMMON_NAME).append("=");
                dn.append(cn);
                dn.append(",");
                dn.append(config.readSettingAsString(PwmSetting.NEWUSER_CONTEXT));

                final Properties createAttrs = new Properties();
                createAttrs.put(ChaiConstant.ATTR_LDAP_SURNAME, sn);

                provider.createEntry(dn.toString(), ChaiConstant.OBJECTCLASS_BASE_LDAP_USER, createAttrs);

                nuBean.setCreateUserDN(dn.toString());

                LOGGER.info(pwmSession, "created user object: " + dn.toString());
            } catch (ChaiOperationException e) {
                final ErrorInformation info = new ErrorInformation(Message.ERROR_UNKNOWN,"error creating user: " + e.getMessage());
                ssBean.setSessionError(info);
                LOGGER.warn(pwmSession, info);
                this.forwardToJSP(req, resp);
                return;
            } catch (NullPointerException e) {
                final ErrorInformation info = new ErrorInformation(Message.ERROR_UNKNOWN,"error creating user (missing cn/sn?): " + e.getMessage());
                ssBean.setSessionError(info);
                LOGGER.warn(pwmSession, info);
                this.forwardToJSP(req, resp);
                return;
            }

            try {
                // write the params to edir
                final ChaiUser theUser = ChaiFactory.createChaiUser(nuBean.getCreateUserDN(), pwmSession.getContextManager().getProxyChaiProvider());
                final Map createAttributesFromForm = new HashMap<String, ParameterConfig>(creationParams);
                createAttributesFromForm.remove(ChaiUser.ATTR_COMMON_NAME);

                // write out form attributes
                LOGGER.debug(pwmSession, "writing new user form attributes for user " + theUser.getEntryDN());
                Helper.writeMapToEdir(pwmSession, theUser, createAttributesFromForm);

                // write out configured attributes.
                LOGGER.debug(pwmSession, "writing newUser.writeAttributes to user " + theUser.getEntryDN());
                Helper.writeMapToEdir(pwmSession, theUser, config.getNewUserWriteAttributes());

                AuthenticationFilter.authUserWithUnknownPassword(theUser, pwmSession, req);
            } catch (ImpossiblePasswordPolicyException e) {
                final ErrorInformation info = new ErrorInformation(Message.ERROR_UNKNOWN,"unexpected ImpossiblePasswordPolicyException error while creating user");
                LOGGER.warn(pwmSession, info, e);
                ssBean.setSessionError(info);
                this.forwardToJSP(req, resp);
                return;
            } catch (ChaiOperationException e) {
                final ErrorInformation info = new ErrorInformation(Message.ERROR_UNKNOWN,"unexpected error writing to ldap: " + e.getMessage());
                LOGGER.warn(pwmSession, info, e);
                ssBean.setSessionError(info);
                Helper.forwardToErrorPage(req, resp, this.getServletContext());
                return;
            }

            //everything good so forward to change password page.
            this.sendNewUserEmailConfirmation(pwmSession);
            ssBean.setSessionSuccess(Message.SUCCESS_CREATE_USER.toInfo());

            pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.NEW_USERS);
            Helper.forwardToSuccessPage(req, resp, this.getServletContext());
            return;
        }
        this.forwardToJSP(req, resp);
    }

    public static void validateAttributeUniqueness(
            final PwmSession pwmSession,
            final ParameterConfig paramConfig,
            final String userDN
    )
            throws ValidationException, ChaiUnavailableException
    {
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
                final ErrorInformation error = new ErrorInformation(Message.ERROR_FIELD_DUPLICATE, null, paramConfig.getLabel());
                throw ValidationException.createValidationException(error);
            }
        } catch (ChaiOperationException e) {
            LOGGER.debug(e);
        }
    }

    private void sendNewUserEmailConfirmation(final PwmSession pwmSession)
    {
        final ContextManager theManager = ContextManager.getContextManager(this.getServletContext());
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();

        final EmailInfo emailInfo = pwmSession.getLocaleConfig().getNewUserEmail();

        final String fromAddress = emailInfo.getFrom();
        final String subject = emailInfo.getSubject();
        final String body = emailInfo.getBody();


        final String toAddress = userInfoBean.getAllUserAttributes().getProperty(ChaiConstant.ATTR_LDAP_EMAIL, "");
        if (toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send new user email for '" + userInfoBean.getUserDN() + "' no email configured");
            return;
        }

        theManager.sendEmailUsingQueue(new EmailEvent(toAddress, fromAddress, subject, body));
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {
        this.getServletContext().getRequestDispatcher('/' + Constants.URL_JSP_NEW_USER).forward(req, resp);
    }
}

