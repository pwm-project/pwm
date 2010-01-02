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

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ImpossiblePasswordPolicyException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.*;
import password.pwm.bean.ActivateUserServletBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.*;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmException;
import password.pwm.error.ValidationException;
import password.pwm.util.PwmLogger;
import password.pwm.util.PwmRandom;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * User interaction servlet for creating new users (self registration)
 *
 * @author Jason D. Rivard
 */
public class ActivateUserServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ActivateUserServlet.class);

    private static final String USERNAME_PARAM_NAME = "username";

// -------------------------- OTHER METHODS --------------------------

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, ChaiUnavailableException, IOException, PwmException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final ContextManager theManager = ContextManager.getContextManager(req);
        final Configuration config = theManager.getConfig();

        final String actionParam = Validator.readStringFromRequest(req, Constants.PARAM_ACTION_REQUEST, 255);

        if (!config.readSettingAsBoolean(PwmSetting.ENABLE_ACTIVATE_USER)) {
            ssBean.setSessionError(Message.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            Helper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        final ActivateUserServletBean activateBean = pwmSession.getActivateUserServletBean();

        if (actionParam != null && actionParam.equalsIgnoreCase("activate")) {
            final Map<String, ParameterConfig> validationParams = activateBean.getActivateUserParams();

            ChaiUser theUser = null;

            try {
                //read the values from the request
                Validator.updateParamValues(pwmSession, req, validationParams);

                // see if the values meet the configured form requirements.
                Validator.validateParmValuesMeetRequirements(validationParams, pwmSession);

                // get an ldap user object based on the params
                theUser = getUserObjectForParams(validationParams, pwmSession);

                // make sure the user isn't locked.
                theManager.getIntruderManager().checkUser(theUser.getEntryDN(), pwmSession);

                // see if the params match ldap values
                validateParamsAgainstLDAP(validationParams, pwmSession, theUser);

                final String queryString = config.readSettingAsString(PwmSetting.QUERY_MATCH_ACTIVATE_USER);
                if (!Permission.testQueryMatch(theUser, queryString, Permission.ACTIVATE_USER.toString(), pwmSession)) {
                    LOGGER.info(pwmSession, "user " + theUser.getEntryDN() + " attempted activation, but does not match query string");
                    ssBean.setSessionError(Message.ERROR_ACTIVATE_USER_NO_QUERY_MATCH.toInfo());
                    theManager.getIntruderManager().addBadUserAttempt(theUser.getEntryDN(), pwmSession);
                    theManager.getIntruderManager().addBadAddressAttempt(pwmSession);
                    Helper.forwardToErrorPage(req, resp, this.getServletContext());
                    return;
                }
            } catch (ValidationException e) {
                if (theUser != null) {
                    theManager.getIntruderManager().addBadUserAttempt(theUser.getEntryDN(), pwmSession);
                }
                ssBean.setSessionError(e.getError());
                theManager.getIntruderManager().addBadAddressAttempt(pwmSession);
                this.forwardToJSP(req, resp);
                LOGGER.error(pwmSession, "validation error during activation: " + e.getMessage());
                Helper.pause(PwmRandom.getInstance().nextInt(2 * 1000) + 1000); // delay penalty of 1-3 seconds
                return;
            }

            LOGGER.info(pwmSession, "new user activation requirements passed for: " + theUser.getEntryDN());

            try {
                theUser.unlock();
            } catch (ChaiOperationException e) {
                LOGGER.error(pwmSession, "error unlocking user " + theUser.getEntryDN() + ": " + e.getMessage());
            }

            try {
                // write out configured attributes.
                LOGGER.debug(pwmSession, "writing activateUser.writeAttributes to user " + theUser.getEntryDN());

                Helper.writeMapToEdir(pwmSession, theUser, theManager.getConfig().getActivateUserWriteAttributes());

                //authenticate the pwm session
                AuthenticationFilter.authUserWithUnknownPassword(theUser, pwmSession, req);

                // mark the event log
                UserHistory.updateUserHistory(pwmSession, UserHistory.Record.Event.ACTIVATE_USER, null);

                // set the session success message
                ssBean.setSessionSuccess(Message.SUCCESS_ACTIVATE_USER.toInfo());

                // update the stats bean
                pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.ACTIVATED_USERS);

                // redirect user to change password screen.
                Helper.forwardToSuccessPage(req, resp, this.getServletContext());

                return;
            } catch (ImpossiblePasswordPolicyException e) {
                final ErrorInformation info = new ErrorInformation(Message.ERROR_UNKNOWN,"unexpected ImpossiblePasswordPolicyException error while activating user");
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
        }
        this.forwardToJSP(req, resp);
    }

    private static String figureSearchFilterForParams(
            final Map<String, ParameterConfig> paramConfigs,
            final PwmSession pwmSession
    )
            throws ChaiUnavailableException, PwmException
    {
        String searchFilter = pwmSession.getConfig().readSettingAsString(PwmSetting.ACTIVATE_USER_SEARCH_FILTER);

        for (final String key : paramConfigs.keySet()) {
            final ParameterConfig loopParamConfig = paramConfigs.get(key);
            final String attrName = "%" + loopParamConfig.getAttributeName() + "%";
            searchFilter = searchFilter.replaceAll(attrName, loopParamConfig.getValue());
        }

        return searchFilter;
    }

    private static ChaiUser performUserSearch(final PwmSession pwmSession, final String searchFilter)
            throws PwmException, ChaiUnavailableException
    {
        final SearchHelper searchHelper = new SearchHelper();
        searchHelper.setMaxResults(2);
        searchHelper.setFilter(searchFilter);
        searchHelper.setAttributes("");

        final String searchBase = pwmSession.getConfig().readSettingAsString(PwmSetting.LDAP_CONTEXTLESS_ROOT);
        final ChaiProvider chaiProvider = pwmSession.getContextManager().getProxyChaiProvider();

        LOGGER.debug(pwmSession, "performing ldap search for user activation, base=" + searchBase + " filter=" + searchFilter);

        try {
            final Map<String, Properties> results = chaiProvider.search(searchBase, searchHelper);

            if (results.isEmpty()) {
                LOGGER.debug(pwmSession, "no search results for activation search");
                return null;
            } else if (results.size() > 1) {
                LOGGER.debug(pwmSession, "multiple search results for activation search, discarding");
                return null;
            }

            final String userDN = results.keySet().iterator().next();
            LOGGER.debug(pwmSession, "found userDN: " + userDN);
            return ChaiFactory.createChaiUser(userDN, chaiProvider);
        } catch (ChaiOperationException e) {
            LOGGER.warn(pwmSession, "error searching for activation user: " + pwmSession);
            return null;
        }
    }

    private static ChaiUser getUserObjectForParams(
            final Map<String, ParameterConfig> paramConfigs,
            final PwmSession pwmSession
    )
            throws ChaiUnavailableException, PwmException
    {
        final String searchFilter = figureSearchFilterForParams(paramConfigs, pwmSession);

        final ChaiUser theUser = performUserSearch(pwmSession, searchFilter);

        if (theUser == null) {
            final String usernameAttribute = pwmSession.getContextManager().getParameter(Constants.CONTEXT_PARAM.LDAP_NAMING_ATTRIBUTE);
            final ParameterConfig usernameParam = paramConfigs.get(usernameAttribute);
            if (usernameParam != null) {
                final String usernameValue = usernameParam.getValue();
                if (usernameValue != null) {
                    pwmSession.getContextManager().getIntruderManager().addBadUserAttempt(usernameValue,pwmSession);
                }
            }
            throw ValidationException.createValidationException(new ErrorInformation(Message.ERROR_NEW_USER_VALIDATION_FAILED));
        }

        return theUser;
    }

    public static void validateParamsAgainstLDAP(
            final Map<String, ParameterConfig> paramConfigs,
            final PwmSession pwmSession,
            final ChaiUser theUser
    )
            throws ChaiUnavailableException, ValidationException
    {

        final HashMap<String, ParameterConfig> localConfigs = new HashMap<String, ParameterConfig>(paramConfigs);
        localConfigs.remove(USERNAME_PARAM_NAME);

        for (final ParameterConfig paramConfig : localConfigs.values()) {
            final String attrName = paramConfig.getAttributeName();

            try {
                if (!theUser.compareStringAttribute(attrName, paramConfig.getValue())) {
                    throw ValidationException.createValidationException(new ErrorInformation(Message.ERROR_NEW_USER_VALIDATION_FAILED, "incorrect value for '" + attrName + "'",attrName));
                }
                LOGGER.trace(pwmSession,"successful validation of ldap value for '" + attrName + "'");
            } catch (ChaiOperationException e) {
                LOGGER.error(pwmSession,"error during param validation of '" + attrName + "', error: " + e.getMessage());
                throw ValidationException.createValidationException(new ErrorInformation(Message.ERROR_NEW_USER_VALIDATION_FAILED, "ldap error testing value for '" + attrName + "'",attrName));
            }
        }
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {
        this.getServletContext().getRequestDispatcher('/' + Constants.URL_JSP_ACTIVATE_USER).forward(req, resp);
    }
}

