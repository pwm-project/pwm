/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.*;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.servlet.PeopleSearchBean;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

public class PeopleSearchServlet extends TopServlet {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(PeopleSearchServlet.class);

    @Override
    protected void processRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_ENABLE)) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (!Permission.checkPermission(Permission.PEOPLE_SEARCH, pwmSession, pwmApplication)) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        final String username = Validator.readStringFromRequest(req, "username");
        if (username != null && username.length() > 0) {
            ((PeopleSearchBean)pwmSession.getSessionBean(PeopleSearchBean.class)).setSearchString(username);
        }

        final String processRequestParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);
        if (processRequestParam != null && processRequestParam.length() > 0) {
            Validator.validatePwmFormID(req);
            if (processRequestParam.equalsIgnoreCase("search")) {
                processUserSearch(req,resp,pwmSession, pwmApplication);
                return;
            } else if (processRequestParam.equalsIgnoreCase("detail")) {
                processDetailRequest(req,resp,pwmSession, pwmApplication);
                return;
            }
        }

        ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.PEOPLE_SEARCH);
    }

    private void processDetailRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmSession pwmSession,
            final PwmApplication pwmApplication
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final PeopleSearchBean peopleSearchBean = (PeopleSearchBean)pwmSession.getSessionBean(PeopleSearchBean.class);
        final String userKey = Validator.readStringFromRequest(req,"userKey");
        if (userKey.length() < 1) {
            pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"userKey parameter is missing"));
            return;
        }

        final UserIdentity userIdentity = UserIdentity.fromObfuscatedKey(userKey,pwmApplication.getConfig());
        peopleSearchBean.setSearchDetails(doDetailLookup(pwmApplication, pwmSession, userIdentity));
        ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.PEOPLE_SEARCH_DETAIL);
    }

    private void processUserSearch(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmSession pwmSession,
            final PwmApplication pwmApplication
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final PeopleSearchBean peopleSearchBean = (PeopleSearchBean)pwmSession.getSessionBean(PeopleSearchBean.class);
        final String username = peopleSearchBean.getSearchString();
        if (username == null || username.length() < 1) {
            pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER));
            return;
        }

        final UserSearchEngine.UserSearchResults searchResults = doSearch(pwmSession, pwmApplication, username);
        peopleSearchBean.setSearchResults(searchResults);
        if (searchResults != null && searchResults.getResults().size() == 1) {
            final UserIdentity userIdentity = searchResults.getResults().keySet().iterator().next();
            peopleSearchBean.setSearchDetails(doDetailLookup(pwmApplication, pwmSession, userIdentity));
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.PEOPLE_SEARCH_DETAIL);
        } else {
            peopleSearchBean.setSearchDetails(null);
            ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.PEOPLE_SEARCH);
        }
    }

    private UserSearchEngine.UserSearchResults doSearch(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final String username
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        final List<FormConfiguration> configuredForm = config.readSettingAsForm(PwmSetting.PEOPLE_SEARCH_RESULT_FORM);

        final Map<String,String> attributeHeaderMap = UserSearchEngine.UserSearchResults.fromFormConfiguration(configuredForm, pwmSession.getSessionStateBean().getLocale());

        try {
            final Map<UserIdentity,Map<String,String>> ldapResults = doLdapSearch(pwmApplication, pwmSession, username, attributeHeaderMap.keySet());

            LOGGER.trace(pwmSession,"search results: " + ldapResults.size());
            if (pwmApplication.getStatisticsManager() != null) {
                pwmApplication.getStatisticsManager().incrementValue(Statistic.PEOPLESEARCH_SEARCHES);
            }

            if (!ldapResults.isEmpty()) {
                final Map<UserIdentity,Map<String,String>> outputMap = Collections.unmodifiableMap(ldapResults);

                final int maxResults = (int)config.readSettingAsLong(PwmSetting.PEOPLE_SEARCH_RESULT_LIMIT);
                final boolean resultsExceeded = ldapResults.size() > maxResults;
                return new UserSearchEngine.UserSearchResults(attributeHeaderMap,outputMap,resultsExceeded);
            }
        } catch (ChaiOperationException e) {
            final String errorMsg = "can't perform search: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg);
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
            LOGGER.warn(pwmSession, errorInformation.toDebugStr());
        } catch (PwmOperationalException e) {
            final String errorMsg = "can't perform search: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg);
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
            LOGGER.warn(pwmSession, errorInformation.toDebugStr());
        }
        return null;
    }


    private static UserSearchEngine.UserSearchResults doDetailLookup(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final Configuration config = pwmApplication.getConfig();
        final List<FormConfiguration> detailFormConfig = config.readSettingAsForm(PwmSetting.PEOPLE_SEARCH_DETAIL_FORM);
        final Map<String,String> attributeHeaderMap = UserSearchEngine.UserSearchResults.fromFormConfiguration(detailFormConfig, pwmSession.getSessionStateBean().getLocale());
        final ChaiUser theUser = pwmSession.getSessionManager().getActor(pwmApplication, userIdentity);
        Map<String,String> values = null;
        try {
            values = theUser.readStringAttributes(attributeHeaderMap.keySet());
        } catch (ChaiOperationException e) {
            LOGGER.error("unexpected error during detail lookup of '" + userIdentity + "', error: " + e.getMessage());
        }
        return new UserSearchEngine.UserSearchResults(attributeHeaderMap, Collections.singletonMap(userIdentity, values),false);
    }

    private static Map<UserIdentity,Map<String,String>> doLdapSearch(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final String username,
            final Set<String> returnAttributes
    )
            throws ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException, PwmOperationalException
    {
        final Configuration config = pwmApplication.getConfig();
        final int maxResults = (int)config.readSettingAsLong(PwmSetting.PEOPLE_SEARCH_RESULT_LIMIT);

        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        if (!config.readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_USE_PROXY)) {
            final ChaiProvider chaiProvider = pwmSession.getSessionManager().getChaiProvider(pwmApplication);
            searchConfiguration.setLdapProfile(pwmSession.getUserInfoBean().getUserIdentity().getLdapProfileID());
            searchConfiguration.setChaiProvider(chaiProvider);
        }

        searchConfiguration.setContexts(config.readSettingAsStringArray(PwmSetting.PEOPLE_SEARCH_SEARCH_BASE));
        searchConfiguration.setEnableContextValidation(false);
        searchConfiguration.setUsername(username);

        final String peopleSearchFilter = config.readSettingAsString(PwmSetting.PEOPLE_SEARCH_SEARCH_FILTER);
        if (peopleSearchFilter != null && peopleSearchFilter.length() > 0) {
            searchConfiguration.setFilter(peopleSearchFilter);
        }

        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
        final Map<UserIdentity,Map<String,String>> searchResults = userSearchEngine.performMultiUserSearch(null,searchConfiguration,maxResults+1,returnAttributes);

        final Map<UserIdentity,Map<String,String>> returnData = new LinkedHashMap<UserIdentity, Map<String, String>>();
        for (final UserIdentity loopUser : searchResults.keySet()) {
            returnData.put(loopUser, searchResults.get(loopUser));
        }

        if (pwmApplication.getStatisticsManager() != null) {
            pwmApplication.getStatisticsManager().incrementValue(Statistic.PEOPLESEARCH_SEARCHES);
        }

        return returnData;
    }
}
