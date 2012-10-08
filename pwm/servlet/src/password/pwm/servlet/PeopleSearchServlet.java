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

import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.*;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

        final String processRequestParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);
        if (processRequestParam != null && processRequestParam.equalsIgnoreCase("search")) {
            Validator.validatePwmFormID(req);

            final String username = Validator.readStringFromRequest(req, "username");
            final String context = Validator.readStringFromRequest(req, "context");

            if (username.length() < 1) {
                ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER));
                this.forwardToJSP(req, resp);
                return;
            }

            processUserSearch(req, pwmSession, pwmApplication, username, context);
        }

        this.forwardToJSP(req,resp);
    }

    private void processUserSearch(
            final HttpServletRequest request,
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final String username,
            final String context
    )
            throws ChaiUnavailableException, PwmUnrecoverableException {
        final PeopleSearchResults searchResults = doSearch(pwmSession, pwmApplication, username,context);

        request.setAttribute("searchResults",searchResults);
    }

    private PeopleSearchResults doSearch(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final String username,
            final String context
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        final String filter = config.readSettingAsString(PwmSetting.PEOPLE_SEARCH_SEARCH_FILTER).replaceAll("%USERNAME%", username);
        final String configuredBase = config.readSettingAsString(PwmSetting.PEOPLE_SEARCH_SEARCH_BASE);
        final List<FormConfiguration> configuredForm = config.readSettingAsForm(PwmSetting.PEOPLE_SEARCH_RESULT_FORM, pwmSession.getSessionStateBean().getLocale());

        final List<String> headers = new ArrayList<String>();
        final List<String> attributes = new ArrayList<String>();
        for (final FormConfiguration formConfiguration : configuredForm) {
            headers.add(formConfiguration.getLabel());
            attributes.add(formConfiguration.getName());
        }

        final SearchHelper searchHelper = new SearchHelper();
        searchHelper.setFilter(filter);
        searchHelper.setAttributes(attributes);
        searchHelper.setMaxResults((int)config.readSettingAsLong(PwmSetting.PEOPLE_SEARCH_RESULT_LIMIT));

        final String searchBase = context == null || context.length() < 1 ? (configuredBase == null || configuredBase.length() < 1 ? config.readSettingAsString(PwmSetting.LDAP_CONTEXTLESS_ROOT) : configuredBase) : context;

        LOGGER.trace(pwmSession,"about to perform search: " + searchHelper.toString() + ", at context: " + searchBase);
        try {
            final ChaiProvider provider;
            if (config.readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_USE_PROXY)) {
                provider = pwmApplication.getProxyChaiProvider();
            } else {
                provider = pwmSession.getSessionManager().getChaiProvider();
            }
            final Map<String,Map<String,String>> ldapResults = provider.search(searchBase,searchHelper);

            LOGGER.trace(pwmSession,"search results: " + ldapResults.size());
            if (pwmApplication.getStatisticsManager() != null) {
                pwmApplication.getStatisticsManager().incrementValue(Statistic.PEOPLESEARCH_SEARCHES);
            }

            if (!ldapResults.isEmpty()) {
                final Map<String,Map<String,String>> outputMap = new TreeMap<String, Map<String, String>>(ldapResults);
                return new PeopleSearchResults(headers,attributes,outputMap);
            }
        } catch (ChaiOperationException e) {
            LOGGER.trace(pwmSession, "can't find username: " + e.getMessage());
        }
        return null;
    }


    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        final String url = SessionFilter.rewriteURL('/' + PwmConstants.URL_JSP_PEOPLE_SEARCH, req, resp);
        this.getServletContext().getRequestDispatcher(url).forward(req, resp);
    }


    public static class PeopleSearchResults implements Serializable {
        private final List<String> headers;
        private final List<String> attributes;
        private final Map<String,Map<String,String>> results;

        public PeopleSearchResults(final List<String> headers, final List<String> attributes, final Map<String, Map<String, String>> results) {
            this.headers = headers;
            this.attributes= attributes;
            this.results = results;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public List<String> getAttributes() {
            return attributes;
        }

        public Map<String, Map<String, String>> getResults() {
            return results;
        }
    }
}
