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

package password.pwm.util.operations;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmSession;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class UserSearchEngine {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(UserSearchEngine.class);

    private PwmApplication pwmApplication;

    public UserSearchEngine(PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
    }

    private static String figureSearchFilterForParams(
            final Map<FormConfiguration, String> formValues,
            final String searchFilter
    )
    {
        String newSearchFilter = searchFilter;

        for (final FormConfiguration formConfiguration : formValues.keySet()) {
            final String attrName = "%" + formConfiguration.getAttributeName() + "%";
            String value = escapeLdapString(formValues.get(formConfiguration));
            newSearchFilter = newSearchFilter.replace(attrName, value);
        }

        return newSearchFilter;
    }

    /**
     * Based on http://www.owasp.org/index.php/Preventing_LDAP_Injection_in_Java.
     *
     * @param input string to have escaped
     * @return ldap escaped script
     *
     */
    public static String escapeLdapString(final String input) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char curChar = input.charAt(i);
            switch (curChar) {
                case '\\':
                    sb.append("\\5c");
                    break;
                case '*':
                    sb.append("\\2a");
                    break;
                case '(':
                    sb.append("\\28");
                    break;
                case ')':
                    sb.append("\\29");
                    break;
                case '\u0000':
                    sb.append("\\00");
                    break;
                default:
                    sb.append(curChar);
            }
        }
        return sb.toString();
    }

    public ChaiUser performUserSearch(final PwmSession pwmSession, final SearchConfiguration searchConfiguration)
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        final long startTime = System.currentTimeMillis();
        LOGGER.debug(pwmSession, "beginning user search process");

        // check the search configuration data params
        searchConfiguration.validate();

        final String input_searchFilter = searchConfiguration.getFilter() != null && searchConfiguration.getFilter().length() > 1 ?
                searchConfiguration.getFilter() :
                pwmApplication.getConfig().readSettingAsString(PwmSetting.USERNAME_SEARCH_FILTER);

        final String searchFilter;
        if (searchConfiguration.getUsername() != null) {
            searchFilter = input_searchFilter.replace(PwmConstants.VALUE_REPLACEMENT_USERNAME, escapeLdapString(searchConfiguration.getUsername()));
        } else if (searchConfiguration.getFormValues() != null) {
            searchFilter = figureSearchFilterForParams(searchConfiguration.getFormValues(),input_searchFilter);
        } else {
            searchFilter = input_searchFilter;
        }

        final ChaiProvider chaiProvider = searchConfiguration.getChaiProvider() == null ?
                pwmApplication.getProxyChaiProvider() :
                searchConfiguration.getChaiProvider();

        ChaiUser returnUser = null;
        if (searchConfiguration.getContext() != null && searchConfiguration.getContext().length() > 0) {
            validateSpecifiedContext(pwmApplication, searchConfiguration.getContext());
            returnUser = doSearch(
                    pwmSession,
                    searchFilter,
                    searchConfiguration.getContext(),
                    chaiProvider
            );
        } else {
            final List<String> searchContexts = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.LDAP_CONTEXTLESS_ROOT);
            for (final String loopContext : searchContexts) {
                returnUser = doSearch(
                        pwmSession,
                        searchFilter,
                        loopContext,
                        chaiProvider
                );
                if (returnUser != null) {
                    break;
                }
            }
        }

        LOGGER.debug(pwmSession, "completed user search process in " + TimeDuration.fromCurrent(startTime).asCompactString() + ", result=" + (returnUser == null ? "none" : returnUser.getEntryDN()));

        if (returnUser == null) {
            final String errorMessage;
            if (searchConfiguration.getUsername() != null && searchConfiguration.getUsername().length() > 0) {
                errorMessage = "an ldap user for username value '" + searchConfiguration.getUsername() + "' was not found";
            } else {
                errorMessage = "an ldap user for was not found";
            }
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER,errorMessage));
        }

        return returnUser;
    }

    private ChaiUser doSearch(
            final PwmSession pwmSession,
            final String searchFilter,
            final String context,
            final ChaiProvider chaiProvider
    )
            throws ChaiUnavailableException, PwmOperationalException {
        final SearchHelper searchHelper = new SearchHelper();
        searchHelper.setMaxResults(2);
        searchHelper.setFilter(searchFilter);
        searchHelper.setAttributes("");

        LOGGER.debug(pwmSession, "performing ldap search for user, base=" + context + " filter=" + searchHelper.toString());

        try {
            final Map<String, Map<String,String>> results = chaiProvider.search(context, searchHelper);

            if (results.isEmpty()) {
                LOGGER.debug(pwmSession, "no users found in context " + context);
                return null;
            } else if (results.size() > 1) {
                final String errorMessage = "multiple user matches found in context '" + context + "'";
                throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER, errorMessage));
            }

            final String userDN = results.keySet().iterator().next();
            LOGGER.debug(pwmSession, "found userDN: " + userDN);
            return ChaiFactory.createChaiUser(userDN, chaiProvider);
        } catch (ChaiOperationException e) {
            LOGGER.warn(pwmSession, "error searching for user: " + e.getMessage());
            return null;
        }
    }

    private static void validateSpecifiedContext(final PwmApplication pwmApplication, final String context)
            throws PwmOperationalException
    {
        Collection<String> loginContexts = pwmApplication.getConfig().getLoginContexts().keySet();
        if (loginContexts == null || loginContexts.isEmpty()) {
            throw new PwmOperationalException(PwmError.ERROR_UNKNOWN,"context specified, but no selectable contexts are configured");
        }

        for (final String loopContext : loginContexts) {
            if (loopContext.equals(context)) {
                return;
            }
        }

        throw new PwmOperationalException(PwmError.ERROR_UNKNOWN,"context '" + context + "' is specified, but is not in configuration");
    }

    public static class SearchConfiguration implements Serializable {
        private String filter;
        private String username;
        private String context;
        private Map<FormConfiguration, String> formValues;
        private ChaiProvider chaiProvider;

        public String getFilter() {
            return filter;
        }

        public void setFilter(String filter) {
            this.filter = filter;
        }

        public Map<FormConfiguration, String> getFormValues() {
            return formValues;
        }

        public void setFormValues(Map<FormConfiguration, String> formValues) {
            this.formValues = formValues;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getContext() {
            return context;
        }

        public void setContext(String context) {
            this.context = context;
        }

        public ChaiProvider getChaiProvider() {
            return chaiProvider;
        }

        public void setChaiProvider(ChaiProvider chaiProvider) {
            this.chaiProvider = chaiProvider;
        }

        private void validate() {
            if (this.username != null && this.formValues != null) {
                throw new IllegalArgumentException("username OR formValues cannot both be supplied");
            }
        }
    }

    public boolean checkIfStringIsDN(final PwmSession pwmSession, final String input) {
        if (input == null || input.length() < 1) {
            return false;
        }

        //if supplied user name starts with username attr assume its the full dn and skip the search
        final String usernameAttribute = pwmApplication.getConfig().readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
        if (input.toLowerCase().startsWith(usernameAttribute.toLowerCase() + "=")) {
            LOGGER.trace(pwmSession, "username appears to be a DN (starts with configured ldap naming attribute'" + usernameAttribute + "'), skipping username search");
            return true;
        } else {
            LOGGER.trace(pwmSession, "username does not appear to be a DN (does not start with configured ldap naming attribute '" + usernameAttribute + "')");
        }

        return false;
    }
}
