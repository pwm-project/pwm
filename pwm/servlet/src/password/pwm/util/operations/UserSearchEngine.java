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
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;

import javax.crypto.SecretKey;
import java.io.Serializable;
import java.util.*;

public class UserSearchEngine {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(UserSearchEngine.class);

    private PwmApplication pwmApplication;

    public UserSearchEngine(PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
    }

    private static String figureSearchFilterForParams(
            final Map<FormConfiguration, String> formValues,
            final String searchFilter,
            final boolean enableValueEscaping
    )
    {
        String newSearchFilter = searchFilter;

        for (final FormConfiguration formItem : formValues.keySet()) {
            final String attrName = "%" + formItem.getName() + "%";
            String value = formValues.get(formItem);
            if (enableValueEscaping) {
                value = escapeLdapString(value);
            }
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

    public static String makeUserDetailKey(final String userDN, final PwmSession pwmSession) {
        try {
            final SecretKey secretKey = Helper.SimpleTextCrypto.makeKey(pwmSession.getSessionStateBean().getSessionVerificationKey());
            return Helper.SimpleTextCrypto.encryptValue(userDN, secretKey, true);
        } catch (Exception e) {
            LOGGER.error("unexpected error making user detail key: " + e.getMessage());
        }
        return "error";
    }

    public static String decodeUserDetailKey(final String userKey, final PwmSession pwmSession) {
        try {
            final SecretKey secretKey = Helper.SimpleTextCrypto.makeKey(pwmSession.getSessionStateBean().getSessionVerificationKey());
            return Helper.SimpleTextCrypto.decryptValue(userKey, secretKey, true);
        } catch (Exception e) {
            LOGGER.error("unexpected error decoding user detail key: " + e.getMessage());
        }
        return "error";
    }

    public ChaiUser performSingleUserSearch(final PwmSession pwmSession, final SearchConfiguration searchConfiguration)
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        final long startTime = System.currentTimeMillis();
        final Map<ChaiUser,Map<String,String>> searchResults = performMultiUserSearch(pwmSession, searchConfiguration, 2, Collections.<String>emptyList());
        final List<ChaiUser> results = searchResults == null ? Collections.<ChaiUser>emptyList() : new ArrayList<ChaiUser>(searchResults.keySet());
        if (results.isEmpty()) {
            final String errorMessage;
            if (searchConfiguration.getUsername() != null && searchConfiguration.getUsername().length() > 0) {
                errorMessage = "an ldap user for username value '" + searchConfiguration.getUsername() + "' was not found";
            } else {
                errorMessage = "an ldap user was not found";
            }
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER,errorMessage));
        } else if (results.size() == 1) {
            final ChaiUser theUser = results.get(0);
            final String userDN = theUser.getEntryDN();
            LOGGER.debug(pwmSession, "found userDN: " + userDN + " (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")");
            return theUser;
        } else {
            final String errorMessage = "multiple user matches found";
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER, errorMessage));
        }
    }

    public UserSearchResults performMultiUserSearchFromForm(
            final PwmSession pwmSession,
            final SearchConfiguration searchConfiguration,
            final int maxResults,
            final List<FormConfiguration> formItem
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException {
        final Map<String,String> attributeHeaderMap = UserSearchResults.fromFormConfiguration(formItem,pwmSession.getSessionStateBean().getLocale());
        final Map<ChaiUser,Map<String,String>> searchResults = performMultiUserSearch(
                pwmSession,
                searchConfiguration,
                maxResults + 1,
                attributeHeaderMap.keySet()
        );
        final boolean resultsExceeded = searchResults.size() > maxResults;
        final Map<String,Map<String,String>> returnData = new LinkedHashMap<String, Map<String, String>>();
        for (final ChaiUser loopUser : searchResults.keySet()) {
            final String userDN = loopUser.getEntryDN();
            returnData.put(userDN, searchResults.get(loopUser));
            if (returnData.size() >= maxResults) {
                break;
            }
        }
        return new UserSearchResults(attributeHeaderMap,returnData,resultsExceeded);
    }


    public Map<ChaiUser,Map<String,String>> performMultiUserSearch(
            final PwmSession pwmSession,
            final SearchConfiguration searchConfiguration,
            final int maxResults,
            final Collection<String> returnAttributes
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException {
        final long startTime = System.currentTimeMillis();
        LOGGER.debug(pwmSession, "beginning user search process");

        // check the search configuration data params
        searchConfiguration.validate();

        final String input_searchFilter = searchConfiguration.getFilter() != null && searchConfiguration.getFilter().length() > 1 ?
                searchConfiguration.getFilter() :
                pwmApplication.getConfig().readSettingAsString(PwmSetting.LDAP_USERNAME_SEARCH_FILTER);

        final String searchFilter;
        if (searchConfiguration.getUsername() != null) {
            if (searchConfiguration.isEnableValueEscaping()) {
                searchFilter = input_searchFilter.replace(PwmConstants.VALUE_REPLACEMENT_USERNAME, escapeLdapString(searchConfiguration.getUsername()));
            } else {
                searchFilter = input_searchFilter.replace(PwmConstants.VALUE_REPLACEMENT_USERNAME, searchConfiguration.getUsername());
            }
        } else if (searchConfiguration.getFormValues() != null) {
            searchFilter = figureSearchFilterForParams(searchConfiguration.getFormValues(),input_searchFilter,searchConfiguration.isEnableValueEscaping());
        } else {
            searchFilter = input_searchFilter;
        }

        final List<String> searchContexts;
        if (searchConfiguration.getContexts() != null &&
                !searchConfiguration.getContexts().isEmpty() &&
                searchConfiguration.getContexts().iterator().next() != null &&
                searchConfiguration.getContexts().iterator().next().length() > 0
                )
        {
            searchContexts = searchConfiguration.getContexts();

            if (searchConfiguration.isEnableContextValidation()) {
                for (final String searchContext : searchContexts) {
                    validateSpecifiedContext(pwmApplication, searchContext);
                }
            }
        } else {
            searchContexts = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.LDAP_CONTEXTLESS_ROOT);
        }

        final ChaiProvider chaiProvider = searchConfiguration.getChaiProvider() == null ?
                pwmApplication.getProxyChaiProvider() :
                searchConfiguration.getChaiProvider();

        final Map<ChaiUser,Map<String,String>> returnMap;
        returnMap = new HashMap<ChaiUser,Map<String, String>>();
        for (final String loopContext : searchContexts) {
            final Map<ChaiUser,Map<String,String>> singleContextResults;
            try {
                singleContextResults = doSingleContextSearch(
                        pwmSession,
                        searchFilter,
                        loopContext,
                        returnAttributes,
                        maxResults - returnMap.size(),
                        chaiProvider
                );
            } catch (ChaiOperationException e) {
                throw new PwmOperationalException(PwmError.forChaiError(e.getErrorCode()),"ldap error during search: " + e.getMessage());
            }
            returnMap.putAll(singleContextResults);
            if (returnMap.size() >= maxResults) {
                break;
            }
        }

        LOGGER.debug(pwmSession, "completed user search process in " + TimeDuration.fromCurrent(startTime).asCompactString() + ", resultSize=" + returnMap.size());
        return returnMap;
    }

    private Map<ChaiUser,Map<String,String>> doSingleContextSearch(
            final PwmSession pwmSession,
            final String searchFilter,
            final String context,
            final Collection<String> returnAttributes,
            final int maxResults,
            final ChaiProvider chaiProvider
    )
            throws ChaiUnavailableException, PwmOperationalException, ChaiOperationException
    {
        final long startTime = System.currentTimeMillis();
        final SearchHelper searchHelper = new SearchHelper();
        searchHelper.setMaxResults(maxResults);
        searchHelper.setFilter(searchFilter);
        searchHelper.setAttributes(returnAttributes);

        LOGGER.debug(pwmSession, "performing ldap search for user, base=" + context + " filter=" + searchHelper.toString());

        final Map<String, Map<String,String>> results = chaiProvider.search(context, searchHelper);

        if (results.isEmpty()) {
            LOGGER.trace(pwmSession, "user not found in context " + context);
            return Collections.emptyMap();
        }

        LOGGER.trace(pwmSession, "found " + results.size() + " results in context: " + context + " (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")");

        final Map<ChaiUser,Map<String,String>> returnMap = new LinkedHashMap<ChaiUser, Map<String, String>>();
        for (final String userDN : results.keySet()) {
            final ChaiUser chaiUser = ChaiFactory.createChaiUser(userDN, chaiProvider);
            final Map<String,String> attributeMap = results.get(userDN);
            returnMap.put(chaiUser, attributeMap);
        }
        return returnMap;
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
        private List<String> contexts;
        private Map<FormConfiguration, String> formValues;
        private transient ChaiProvider chaiProvider;

        private boolean enableValueEscaping = true;
        private boolean enableContextValidation = true;

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

        public List<String> getContexts() {
            return contexts;
        }

        public void setContexts(List<String> contexts) {
            this.contexts = contexts;
        }

        public ChaiProvider getChaiProvider() {
            return chaiProvider;
        }

        public void setChaiProvider(ChaiProvider chaiProvider) {
            this.chaiProvider = chaiProvider;
        }

        public boolean isEnableValueEscaping() {
            return enableValueEscaping;
        }

        public void setEnableValueEscaping(boolean enableValueEscaping) {
            this.enableValueEscaping = enableValueEscaping;
        }

        public boolean isEnableContextValidation() {
            return enableContextValidation;
        }

        public void setEnableContextValidation(boolean enableContextValidation) {
            this.enableContextValidation = enableContextValidation;
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

    public static class UserSearchResults implements Serializable {
        private final Map<String,String> headerAttributeMap;
        private final Map<String,Map<String,String>> results;
        private boolean sizeExceeded;

        public UserSearchResults(Map<String, String> headerAttributeMap, Map<String, Map<String, String>> results, boolean sizeExceeded) {
            this.headerAttributeMap = headerAttributeMap;
            this.results = results;
            this.sizeExceeded = sizeExceeded;
        }

        public Map<String, String> getHeaderAttributeMap() {
            return headerAttributeMap;
        }

        public Map<String, Map<String, String>> getResults() {
            return results;
        }

        public boolean isSizeExceeded() {
            return sizeExceeded;
        }

        public List<Map<String,String>> resultsAsJsonOutput(final PwmSession pwmSession) {
            final List<Map<String,String>> outputList = new ArrayList<Map<String, String>>();
            for (final String userDN : this.getResults().keySet()) {
                final Map<String,String> rowMap = new LinkedHashMap<String, String>();
                for (final String attribute : this.getHeaderAttributeMap().keySet()) {
                    rowMap.put(attribute,this.getResults().get(userDN).get(attribute));
                }
                rowMap.put("userKey",makeUserDetailKey(userDN,pwmSession));
                outputList.add(rowMap);
            }
            return outputList;
        }

        public static Map<String,String> fromFormConfiguration(final List<FormConfiguration> formItems, final Locale locale) {
            final Map<String,String> results = new LinkedHashMap<String, String>();
            for (final FormConfiguration formItem : formItems) {
                results.put(formItem.getName(), formItem.getLabel(locale));
            }
            return results;
        }
    }
}
