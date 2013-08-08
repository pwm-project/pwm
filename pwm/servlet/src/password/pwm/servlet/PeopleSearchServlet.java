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
import password.pwm.bean.servlet.PeopleSearchBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.UserSearchEngine;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

public class PeopleSearchServlet extends TopServlet {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(PeopleSearchServlet.class);

    private static final String KEY_RESULTS_EXCEEDED = "KEY_RESULTS_EXCEEDED";

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

        this.forwardToJSP(req,resp);
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

        final String decodedUserKey = UserSearchEngine.decodeUserDetailKey(userKey, pwmSession);
        peopleSearchBean.setSearchDetails(doDetailLookup(pwmApplication, pwmSession, decodedUserKey));
        this.forwardToDetailJSP(req, resp);
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
        if (username.length() < 1) {
            pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER));
            return;
        }

        final UserSearchEngine.UserSearchResults searchResults = doSearch(pwmSession, pwmApplication, username);
        peopleSearchBean.setSearchResults(searchResults);
        if (searchResults != null && searchResults.getResults().size() == 1) {
            final String userDN = searchResults.getResults().keySet().iterator().next();
            peopleSearchBean.setSearchDetails(doDetailLookup(pwmApplication, pwmSession, userDN));
            this.forwardToDetailJSP(req, resp);
        } else {
            peopleSearchBean.setSearchDetails(null);
            this.forwardToJSP(req, resp);
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
            final ChaiProvider provider = figureChaiProvider(pwmApplication, pwmSession);
            final Map<String,Map<String,String>> ldapResults = doLdapSearch(pwmApplication, username, attributeHeaderMap.keySet(), provider);

            LOGGER.trace(pwmSession,"search results: " + ldapResults.size());
            if (pwmApplication.getStatisticsManager() != null) {
                pwmApplication.getStatisticsManager().incrementValue(Statistic.PEOPLESEARCH_SEARCHES);
            }

            if (!ldapResults.isEmpty()) {
                final boolean resultsExceeded = ldapResults.containsKey(KEY_RESULTS_EXCEEDED);
                ldapResults.remove(KEY_RESULTS_EXCEEDED);
                final Map<String,Map<String,String>> outputMap = Collections.unmodifiableMap(ldapResults);
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

    private static ChaiProvider figureChaiProvider(final PwmApplication pwmApplication, final PwmSession pwmSession)
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();

        if (config.readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_USE_PROXY)) {
            return pwmApplication.getProxyChaiProvider();
        } else {
            return pwmSession.getSessionManager().getChaiProvider();
        }
    }

    private static UserSearchEngine.UserSearchResults doDetailLookup(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final String userDN
    ) throws PwmUnrecoverableException, ChaiUnavailableException {
        final ChaiProvider chaiProvider = figureChaiProvider(pwmApplication, pwmSession);
        final Configuration config = pwmApplication.getConfig();
        final List<FormConfiguration> detailFormConfig = config.readSettingAsForm(PwmSetting.PEOPLE_SEARCH_DETAIL_FORM);
        final Map<String,String> attributeHeaderMap = UserSearchEngine.UserSearchResults.fromFormConfiguration(detailFormConfig, pwmSession.getSessionStateBean().getLocale());
        final ChaiUser theUser = ChaiFactory.createChaiUser(userDN,chaiProvider);
        Map<String,String> values = null;
        try {
            values = theUser.readStringAttributes(attributeHeaderMap.keySet());
        } catch (ChaiOperationException e) {
            LOGGER.error("unexpected error during detail lookup of '" + userDN + "', error: " + e.getMessage());
        }
        return new UserSearchEngine.UserSearchResults(attributeHeaderMap, Collections.singletonMap(userDN, values),false);
    }

    private static Map<String,Map<String,String>> doLdapSearch(
            final PwmApplication pwmApplication,
            final String username,
            final Set<String> returnAttributes,
            final ChaiProvider chaiProvider
    )
            throws ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException, PwmOperationalException
    {
        final Configuration config = pwmApplication.getConfig();
        final int maxResults = (int)config.readSettingAsLong(PwmSetting.PEOPLE_SEARCH_RESULT_LIMIT);

        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        searchConfiguration.setChaiProvider(chaiProvider);
        searchConfiguration.setContexts(config.readSettingAsStringArray(PwmSetting.PEOPLE_SEARCH_SEARCH_BASE));
        searchConfiguration.setEnableContextValidation(false);
        searchConfiguration.setUsername(username);

        final String peopleSearchFilter = config.readSettingAsString(PwmSetting.PEOPLE_SEARCH_SEARCH_FILTER);
        if (peopleSearchFilter != null && peopleSearchFilter.length() > 0) {
            searchConfiguration.setFilter(peopleSearchFilter);
        }

        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
        final Map<ChaiUser,Map<String,String>> searchResults = userSearchEngine.performMultiUserSearch(null,searchConfiguration,maxResults+1,returnAttributes);

        final Map<String,Map<String,String>> returnData = new LinkedHashMap<String, Map<String, String>>();
        for (final ChaiUser loopUser : searchResults.keySet()) {
            final String userDN = loopUser.getEntryDN();
            returnData.put(userDN, searchResults.get(loopUser));
            if (returnData.size() >= maxResults) {
                returnData.put(KEY_RESULTS_EXCEEDED,Collections.<String,String>emptyMap());
                break;
            }
        }

        if (pwmApplication.getStatisticsManager() != null) {
            pwmApplication.getStatisticsManager().incrementValue(Statistic.PEOPLESEARCH_SEARCHES);
        }

        return returnData;
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        final String url = SessionFilter.rewriteURL('/' + PwmConstants.URL_JSP_PEOPLE_SEARCH, req, resp);
        this.getServletContext().getRequestDispatcher(url).forward(req, resp);
    }

    private void forwardToDetailJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        final String url = SessionFilter.rewriteURL('/' + PwmConstants.URL_JSP_PEOPLE_SEARCH_DETAIL, req, resp);
        this.getServletContext().getRequestDispatcher(url).forward(req, resp);
    }

}
