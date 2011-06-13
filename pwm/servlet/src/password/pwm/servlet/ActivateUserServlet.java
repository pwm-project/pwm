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
import com.novell.ldapchai.exception.ImpossiblePasswordPolicyException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.*;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.Message;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.util.*;
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
public class ActivateUserServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ActivateUserServlet.class);

    private static final String USERNAME_PARAM_NAME = "username";
    private static final String CONTEXT_PARAM_NAME = "context";

// -------------------------- OTHER METHODS --------------------------

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException {
        final Configuration config = PwmSession.getPwmSession(req).getConfig();

        final String actionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST, 255);

        if (!config.readSettingAsBoolean(PwmSetting.ACTIVATE_USER_ENABLE)) {
            PwmSession.getPwmSession(req).getSessionStateBean().setSessionError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (actionParam != null && actionParam.equalsIgnoreCase("activate")) {
            handleActivationRequest(req, resp);
            return;
        }

        forwardToJSP(req, resp);
    }

    public void handleActivationRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException {
        final ContextManager theManager = ContextManager.getContextManager(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        //final ActivateUserServletBean activateBean = pwmSession.getActivateUserServletBean();
        final Configuration config = theManager.getConfig();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        Validator.validatePwmFormID(req);

        final List<FormConfiguration> formConfiguration = config.readSettingAsForm(PwmSetting.ACTIVATE_USER_FORM, ssBean.getLocale());

        ChaiUser theUser = null;

        try {
            //read the values from the request
            final Map<FormConfiguration,String> formValues = Validator.readFormValuesFromRequest(req, formConfiguration);

            // see if the values meet the configured form requirements.
            Validator.validateParmValuesMeetRequirements(pwmSession, formValues);

            // read the context attr
            final String contextParam = Validator.readStringFromRequest(req, CONTEXT_PARAM_NAME, 1024, "");

            // get an ldap user object based on the params
            {
                final String searchFilter = figureSearchFilterForParams(formValues, pwmSession);
                final String searchContext = UserStatusHelper.determineContextForSearch(pwmSession, contextParam, pwmSession.getConfig());
                theUser = performUserSearch(pwmSession, searchFilter, searchContext);
            }

            // make sure the user isn't locked.
            theManager.getIntruderManager().checkUser(theUser.getEntryDN(), pwmSession);

            // see if the params match ldap values
            validateParamsAgainstLDAP(formValues, pwmSession, theUser);

            final String queryString = config.readSettingAsString(PwmSetting.ACTIVATE_USER_QUERY_MATCH);
            if (!Permission.testQueryMatch(theUser, queryString, Permission.ACTIVATE_USER.toString(), pwmSession)) {
                final String errorMsg = "user " + theUser.getEntryDN() + " attempted activation, but does not match query string";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_ACTIVATE_USER_NO_QUERY_MATCH, errorMsg);
                ssBean.setSessionError(errorInformation);
                theManager.getIntruderManager().addBadUserAttempt(theUser.getEntryDN(), pwmSession);
                theManager.getIntruderManager().addBadAddressAttempt(pwmSession);
                ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
                return;
            }

            LOGGER.info(pwmSession, "user activation requirements passed for " + theUser.getEntryDN() );

            activateUser(pwmSession, theUser, req.isSecure());

            // redirect user to change password screen.
            ServletHelper.forwardToSuccessPage(req, resp, this.getServletContext());

            return;
        } catch (PwmDataValidationException e) {
            final String errorMsg;
            if (theUser != null) {
                theManager.getIntruderManager().addBadUserAttempt(theUser.getEntryDN(), pwmSession);
                errorMsg = "validation error during activation for user '" + theUser.getEntryDN() + "', error: " + e.getMessage();
            } else {
                errorMsg = "validation error during activation, error: " + e.getMessage();
            }
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_ACTIVATION, errorMsg);
            ssBean.setSessionError(errorInformation);
            theManager.getIntruderManager().addBadAddressAttempt(pwmSession);
            Helper.pause(PwmRandom.getInstance().nextInt(2 * 1000) + 1000); // delay penalty of 1-3 seconds
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_ACTIVATION,e.getErrorInformation().getDetailedErrorMsg(),e.getErrorInformation().getFieldValues());
            LOGGER.warn(pwmSession, errorInformation.toDebugStr());
            ssBean.setSessionError(errorInformation);
            Helper.pause(PwmRandom.getInstance().nextInt(2 * 1000) + 1000); // delay penalty of 1-3 seconds
        }
        forwardToJSP(req, resp);
    }

    public void activateUser(
            final PwmSession pwmSession,
            final ChaiUser theUser,
            final boolean secure

    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        try {
            theUser.unlock();
        } catch (ChaiOperationException e) {
            final String errorMsg = "error unlocking user " + theUser.getEntryDN() + ": " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_ACTIVATION_FAILURE, errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        try {
            // write out configured attributes.
            {
                LOGGER.debug(pwmSession, "writing pre-activate user attribute write values to user " + theUser.getEntryDN());
                final Collection<String> configValues = pwmSession.getConfig().readSettingAsStringArray(PwmSetting.ACTIVATE_USER_PRE_WRITE_ATTRIBUTES);
                final Map<String, String> writeAttributesSettings = Configuration.convertStringListToNameValuePair(configValues, "=");
                Helper.writeMapToLdap(pwmSession, theUser, writeAttributesSettings);
            }


            //authenticate the pwm session
            AuthenticationFilter.authUserWithUnknownPassword(theUser, pwmSession, secure);

            // mark the event log
            UserHistory.updateUserHistory(pwmSession, UserHistory.Record.Event.ACTIVATE_USER, null);

            // set the session success message
            pwmSession.getSessionStateBean().setSessionSuccess(Message.SUCCESS_ACTIVATE_USER, null);

            // update the stats bean
            pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.ACTIVATED_USERS);

            // send email
            sendActivationEmail(pwmSession);

            // setup post-change attributes
            final PostChangePasswordAction postAction = new PostChangePasswordAction() {

                public String getLabel() {
                    return "ActivateUser write attributes";
                }

                public boolean doAction(final PwmSession pwmSession, final String newPassword)
                        throws PwmUnrecoverableException {
                    try {
                        final ChaiUser theUser = pwmSession.getContextManager().getProxyChaiUserActor(pwmSession);
                        LOGGER.debug(pwmSession, "writing post-activate user attribute write values to user " + theUser.getEntryDN());
                        final Collection<String> configValues = pwmSession.getConfig().readSettingAsStringArray(PwmSetting.ACTIVATE_USER_POST_WRITE_ATTRIBUTES);
                        final Map<String, String> writeAttributesSettings = Configuration.convertStringListToNameValuePair(configValues, "=");
                        Helper.writeMapToLdap(pwmSession, theUser, writeAttributesSettings);
                    } catch (PwmOperationalException e) {
                        final ErrorInformation info = new ErrorInformation(PwmError.ERROR_ACTIVATION_FAILURE, e.getErrorInformation().getDetailedErrorMsg(), e.getErrorInformation().getFieldValues());
                        final PwmUnrecoverableException newException = new PwmUnrecoverableException(info);
                        newException.initCause(e);
                        throw newException;
                    } catch (ChaiUnavailableException e) {
                        final String errorMsg = "unable to reach ldap server while writing post-activate attributes: " + e.getMessage();
                        final ErrorInformation info = new ErrorInformation(PwmError.ERROR_ACTIVATION_FAILURE, errorMsg);
                        final PwmUnrecoverableException newException = new PwmUnrecoverableException(info);
                        newException.initCause(e);
                        throw newException;
                    }
                    return true;
                }
            };

            pwmSession.getUserInfoBean().addPostChangePasswordActions("activateUserWriteAttributes", postAction);
        } catch (ImpossiblePasswordPolicyException e) {
            final ErrorInformation info = new ErrorInformation(PwmError.ERROR_UNKNOWN, "unexpected ImpossiblePasswordPolicyException error while activating user");
            LOGGER.warn(pwmSession, info, e);
            throw new PwmOperationalException(info);
        }
    }

    private static String figureSearchFilterForParams(
            final Map<FormConfiguration,String> formValues,
            final PwmSession pwmSession
    )
            throws ChaiUnavailableException
    {
        String searchFilter = pwmSession.getConfig().readSettingAsString(PwmSetting.ACTIVATE_USER_SEARCH_FILTER);

        for (final FormConfiguration formConfiguration : formValues.keySet()) {
            final String attrName = "%" + formConfiguration.getAttributeName() + "%";
            searchFilter = searchFilter.replaceAll(attrName, formValues.get(formConfiguration));
        }

        return searchFilter;
    }

    private static ChaiUser performUserSearch(final PwmSession pwmSession, final String searchFilter, final String searchBase)
            throws ChaiUnavailableException, PwmOperationalException {
        final SearchHelper searchHelper = new SearchHelper();
        searchHelper.setMaxResults(2);
        searchHelper.setFilter(searchFilter);
        searchHelper.setAttributes("");

        final ChaiProvider chaiProvider = pwmSession.getContextManager().getProxyChaiProvider();

        LOGGER.debug(pwmSession, "performing ldap search for user activation, base=" + searchBase + " filter=" + searchFilter);

        try {
            final Map<String, Map<String,String>> results = chaiProvider.search(searchBase, searchHelper);

            if (results.isEmpty()) {
                final String errorMsg = "user not found using search filter " + searchFilter + ", in " + searchBase;
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER, errorMsg);
                throw new PwmOperationalException(errorInformation);
            } else if (results.size() > 1) {
                final String errorMsg = "multiple matches results for activation search";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER, errorMsg);
                throw new PwmOperationalException(errorInformation);
            }

            final String userDN = results.keySet().iterator().next();
            LOGGER.debug(pwmSession, "found userDN: " + userDN);
            return ChaiFactory.createChaiUser(userDN, chaiProvider);
        } catch (ChaiOperationException e) {
            final String errorMsg = "ldap error during activation search: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER, errorMsg);
            final PwmOperationalException newException = new PwmOperationalException(errorInformation);
            newException.initCause(e);
            throw newException;
        }
    }


    public static void validateParamsAgainstLDAP(
            final Map<FormConfiguration, String> formValues,
            final PwmSession pwmSession,
            final ChaiUser theUser
    )
            throws ChaiUnavailableException, PwmDataValidationException
    {
        for (final FormConfiguration formConfiguration : formValues.keySet()) {
            final String attrName = formConfiguration.getAttributeName();
            if (!USERNAME_PARAM_NAME.equalsIgnoreCase(attrName)) {
                final String value = formValues.get(formConfiguration);
                try {
                    if (!theUser.compareStringAttribute(attrName, value)) {
                        throw new PwmDataValidationException(new ErrorInformation(PwmError.ERROR_NEW_USER_VALIDATION_FAILED, "incorrect value for '" + attrName + "'", attrName));
                    }
                    LOGGER.trace(pwmSession, "successful validation of ldap value for '" + attrName + "'");
                } catch (ChaiOperationException e) {
                    LOGGER.error(pwmSession, "error during param validation of '" + attrName + "', error: " + e.getMessage());
                    throw new PwmDataValidationException(new ErrorInformation(PwmError.ERROR_NEW_USER_VALIDATION_FAILED, "ldap error testing value for '" + attrName + "'", attrName));
                }



            }
        }
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_ACTIVATE_USER).forward(req, resp);
    }

    private void sendActivationEmail(final PwmSession pwmSession) {
        final ContextManager theManager = pwmSession.getContextManager();
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
        final Configuration config = pwmSession.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();

        final String fromAddress = config.readSettingAsLocalizedString(PwmSetting.EMAIL_ACTIVATION_FROM, locale);
        final String subject = config.readSettingAsLocalizedString(PwmSetting.EMAIL_ACTIVATION_SUBJECT, locale);
        final String plainBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_ACTIVATION_BODY, locale);
        final String htmlBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_ACTIVATION_BODY_HTML, locale);

        final String toAddress = userInfoBean.getUserEmailAddress();

        if (toAddress == null || toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "skipping send activation email for '" + userInfoBean.getUserDN() + "' no email configured");
            return;
        }

        theManager.sendEmailUsingQueue(new EmailItemBean(toAddress, fromAddress, subject, plainBody, htmlBody));
    }
}

