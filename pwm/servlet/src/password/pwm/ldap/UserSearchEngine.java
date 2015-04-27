/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.ldap;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmService;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DuplicateMode;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.*;
import password.pwm.http.PwmRequest;
import password.pwm.util.JsonUtil;
import password.pwm.util.StringUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.stats.Statistic;

import java.io.Serializable;
import java.util.*;

public class UserSearchEngine {

    private static final PwmLogger LOGGER = PwmLogger.forClass(UserSearchEngine.class);

    private static int searchCounter = 0;

    private PwmApplication pwmApplication;
    private SessionLabel sessionLabel;

    public UserSearchEngine(final PwmApplication pwmApplication, final SessionLabel sessionLabel) {
        this.pwmApplication = pwmApplication;
        this.sessionLabel = sessionLabel;
    }

    public UserSearchEngine(final PwmRequest pwmRequest) {
        this(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel());
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
                value = StringUtil.escapeLdapFilter(value);
            }
            newSearchFilter = newSearchFilter.replace(attrName, value);
        }

        return newSearchFilter;
    }

    public UserIdentity resolveUsername(
            final String username,
            final String context,
            final String profile
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        //check if username is a key
        {
            UserIdentity inputIdentity = null;
            try {
                inputIdentity = UserIdentity.fromKey(username, pwmApplication.getConfig());
            } catch (PwmException e) { /* input is not a userIdentity */ }

            if (inputIdentity != null) {
                try {
                    final ChaiUser theUser = pwmApplication.getProxiedChaiUser(inputIdentity);
                    if (theUser.isValid()) {
                        final String canonicalDN;
                        canonicalDN = theUser.readCanonicalDN();
                        return new UserIdentity(canonicalDN, inputIdentity.getLdapProfileID());
                    }
                } catch (ChaiOperationException e) {
                    throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER, e.getMessage()));
                }
            }
        }

        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication, sessionLabel);

        try {
            //see if we need to do a contextless search.
            if (userSearchEngine.checkIfStringIsDN(username)) {
                return userSearchEngine.resolveUserDN(username);
            } else {
                final SearchConfiguration searchConfiguration = new SearchConfiguration();
                searchConfiguration.setUsername(username);
                if (context != null) {
                    searchConfiguration.setContexts(Collections.singletonList(context));
                }
                if (profile != null) {
                    searchConfiguration.setLdapProfile(profile);
                }
                return userSearchEngine.performSingleUserSearch(searchConfiguration);
            }
        } catch (PwmOperationalException e) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER,e.getErrorInformation().getDetailedErrorMsg(),e.getErrorInformation().getFieldValues()));
        }
    }

    public UserIdentity performSingleUserSearch(
            final SearchConfiguration searchConfiguration
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final long startTime = System.currentTimeMillis();
        final DuplicateMode dupeMode = pwmApplication.getConfig().readSettingAsEnum(PwmSetting.LDAP_DUPLICATE_MODE, DuplicateMode.class);
        final int searchCount = (dupeMode == DuplicateMode.FIRST_ALL) ? 1 : 2;
        final Map<UserIdentity,Map<String,String>> searchResults = performMultiUserSearch(searchConfiguration, searchCount, Collections.<String>emptyList());
        final List<UserIdentity> results = searchResults == null ? Collections.<UserIdentity>emptyList() : new ArrayList<>(searchResults.keySet());
        if (results.isEmpty()) {
            final String errorMessage;
            if (searchConfiguration.getUsername() != null && searchConfiguration.getUsername().length() > 0) {
                errorMessage = "an ldap user for username value '" + searchConfiguration.getUsername() + "' was not found";
            } else {
                errorMessage = "an ldap user was not found";
            }
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER,errorMessage));
        } else if (results.size() == 1) {
            final String userDN = results.get(0).getUserDN();
            LOGGER.debug(sessionLabel, "found userDN: " + userDN + " (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")");
            return results.get(0);
        }
        if (dupeMode == DuplicateMode.FIRST_PROFILE) {
            final String profile1 = results.get(0).getLdapProfileID();
            final String profile2 = results.get(1).getLdapProfileID();
            if (profile1 == null && profile2 == null || (profile1 != null && profile1.equals(profile2))) {
                return results.get(0);
            } else {
                final String errorMessage = "multiple user matches in single profile";
                throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER, errorMessage));
            }

        }
        final String errorMessage = "multiple user matches found";
        throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER, errorMessage));
    }

    public UserSearchResults performMultiUserSearchFromForm(
            final Locale locale,
            final SearchConfiguration searchConfiguration,
            final int maxResults,
            final List<FormConfiguration> formItem
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException {
        final Map<String,String> attributeHeaderMap = UserSearchResults.fromFormConfiguration(formItem,locale);
        final Map<UserIdentity,Map<String,String>> searchResults = performMultiUserSearch(
                searchConfiguration,
                maxResults + 1,
                attributeHeaderMap.keySet()
        );
        final boolean resultsExceeded = searchResults.size() > maxResults;
        final Map<UserIdentity,Map<String,String>> returnData = new LinkedHashMap<>();
        for (final UserIdentity loopUser : searchResults.keySet()) {
            returnData.put(loopUser, searchResults.get(loopUser));
            if (returnData.size() >= maxResults) {
                break;
            }
        }
        return new UserSearchResults(attributeHeaderMap,returnData,resultsExceeded);
    }

    public Map<UserIdentity,Map<String,String>> performMultiUserSearch(
            final SearchConfiguration searchConfiguration,
            final int maxResults,
            final Collection<String> returnAttributes
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final Collection<LdapProfile> ldapProfiles;
        if (searchConfiguration.getLdapProfile() != null && !searchConfiguration.getLdapProfile().isEmpty()) {
            if (pwmApplication.getConfig().getLdapProfiles().containsKey(searchConfiguration.getLdapProfile())) {
                ldapProfiles = Collections.singletonList(pwmApplication.getConfig().getLdapProfiles().get(searchConfiguration.getLdapProfile()));
            } else {
                LOGGER.debug(sessionLabel, "attempt to search for users in unknown ldap profile '" + searchConfiguration.getLdapProfile() + "', skipping search");
                return Collections.emptyMap();
            }
        } else {
            ldapProfiles = pwmApplication.getConfig().getLdapProfiles().values();
        }

        final boolean ignoreUnreachableProfiles = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.LDAP_IGNORE_UNREACHABLE_PROFILES);
        final Map<UserIdentity,Map<String,String>> returnMap = new LinkedHashMap<>();

        final List<String> errors = new ArrayList<>();

        final long profileRetryDelayMS = Long.valueOf(pwmApplication.getConfig().readAppProperty(AppProperty.LDAP_PROFILE_RETRY_DELAY));
        for (final LdapProfile ldapProfile : ldapProfiles) {
            if (returnMap.size() < maxResults) {
                boolean skipProfile = false;
                final Date lastLdapFailure = pwmApplication.getLdapConnectionService().getLastLdapFailureTime(ldapProfile);
                if (ldapProfiles.size() > 1 && lastLdapFailure != null && TimeDuration.fromCurrent(lastLdapFailure).isShorterThan(profileRetryDelayMS)) {
                    LOGGER.info("skipping user search on ldap profile " + ldapProfile.getIdentifier() + " due to recent unreachable status (" + TimeDuration.fromCurrent(lastLdapFailure).asCompactString() + ")");
                    skipProfile = true;
                }
                if (!skipProfile) {
                    try {
                        returnMap.putAll(performMultiUserSearchImpl(
                                        ldapProfile,
                                        searchConfiguration,
                                        maxResults - returnMap.size(),
                                        returnAttributes)
                        );
                    } catch (PwmUnrecoverableException e) {
                        if (e.getError() == PwmError.ERROR_DIRECTORY_UNAVAILABLE) {
                            pwmApplication.getLdapConnectionService().setLastLdapFailure(ldapProfile,e.getErrorInformation());
                            if (ignoreUnreachableProfiles) {
                                errors.add(e.getErrorInformation().getDetailedErrorMsg());
                                if (errors.size() >= ldapProfiles.size()) {
                                    final String errorMsg = "all ldap profiles are unreachable; errors: " + JsonUtil.serializeCollection(errors);
                                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,errorMsg));
                                }
                            } else {
                                throw e;
                            }
                        }
                    }
                }
            }
        }
        return returnMap;
    }


    protected Map<UserIdentity,Map<String,String>> performMultiUserSearchImpl(
            final LdapProfile ldapProfile,
            final SearchConfiguration searchConfiguration,
            final int maxResults,
            final Collection<String> returnAttributes
    )
            throws PwmUnrecoverableException, PwmOperationalException {
        final long startTime = System.currentTimeMillis();
        LOGGER.debug(sessionLabel, "beginning user search process");

        // check the search configuration data params
        searchConfiguration.validate();

        final String input_searchFilter = searchConfiguration.getFilter() != null && searchConfiguration.getFilter().length() > 1 ?
                searchConfiguration.getFilter() :
                ldapProfile.readSettingAsString(PwmSetting.LDAP_USERNAME_SEARCH_FILTER);

        final String searchFilter;
        if (searchConfiguration.getUsername() != null) {
            final String inputQuery = searchConfiguration.isEnableValueEscaping()
                    ? StringUtil.escapeLdapFilter(searchConfiguration.getUsername())
                    : searchConfiguration.getUsername();

            if (searchConfiguration.getUsername().split("\\s").length > 1) { // split on all whitespace chars
                final StringBuilder multiSearchFilter = new StringBuilder();
                multiSearchFilter.append("(&");
                for (final String queryPart : searchConfiguration.getUsername().split(" ")) {
                    multiSearchFilter.append("(");
                    multiSearchFilter.append(input_searchFilter.replace(PwmConstants.VALUE_REPLACEMENT_USERNAME, queryPart));
                    multiSearchFilter.append(")");
                }
                multiSearchFilter.append(")");
                searchFilter = multiSearchFilter.toString();
            } else {
                searchFilter = input_searchFilter.replace(PwmConstants.VALUE_REPLACEMENT_USERNAME, inputQuery.trim());
            }
        } else if (searchConfiguration.getGroupDN() != null) {
            final String groupAttr = ldapProfile.readSettingAsString(PwmSetting.LDAP_USER_GROUP_ATTRIBUTE);
            searchFilter = "(" + groupAttr + "=" + searchConfiguration.getGroupDN() + ")";
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
                    validateSpecifiedContext(pwmApplication, ldapProfile, searchContext);
                }
            }
        } else {
            searchContexts = ldapProfile.readSettingAsStringArray(PwmSetting.LDAP_CONTEXTLESS_ROOT);
        }

        final long timeLimitMS = searchConfiguration.getSearchTimeout() != 0
                ? searchConfiguration.getSearchTimeout()
                : Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.LDAP_SEARCH_TIMEOUT));


        final ChaiProvider chaiProvider = searchConfiguration.getChaiProvider() == null ?
                pwmApplication.getProxyChaiProvider(ldapProfile.getIdentifier()) :
                searchConfiguration.getChaiProvider();

        final Map<UserIdentity,Map<String,String>> returnMap;
        returnMap = new LinkedHashMap<>();
        for (final String loopContext : searchContexts) {
            final Map<UserIdentity,Map<String,String>> singleContextResults;
            singleContextResults = doSingleContextSearch(
                    ldapProfile,
                    searchFilter,
                    loopContext,
                    returnAttributes,
                    maxResults - returnMap.size(),
                    chaiProvider,
                    timeLimitMS
            );
            returnMap.putAll(singleContextResults);
            if (returnMap.size() >= maxResults) {
                break;
            }
        }

        LOGGER.debug(sessionLabel, "completed user search process in " + TimeDuration.fromCurrent(startTime).asCompactString() + ", resultSize=" + returnMap.size());
        return returnMap;
    }

    private Map<UserIdentity,Map<String,String>> doSingleContextSearch(
            final LdapProfile ldapProfile,
            final String searchFilter,
            final String context,
            final Collection<String> returnAttributes,
            final int maxResults,
            final ChaiProvider chaiProvider,
            final long timeoutMs
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final SearchHelper searchHelper = new SearchHelper();
        searchHelper.setMaxResults(maxResults);
        searchHelper.setFilter(searchFilter);
        searchHelper.setAttributes(returnAttributes);
        searchHelper.setTimeLimit((int)timeoutMs);
        final int searchID = searchCounter++;

        final String debugInfo = "searchID=" + searchID + " profile=" + ldapProfile.getIdentifier() + " base=" + context + " filter=" + searchHelper.toString();
        LOGGER.debug(sessionLabel, "performing ldap search for user; " + debugInfo);

        final Date startTime = new Date();
        final Map<String, Map<String,String>> results;
        try {
            results = chaiProvider.search(context, searchHelper);
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,e.getMessage()));
        } catch (ChaiOperationException e) {
            throw new PwmOperationalException(PwmError.forChaiError(e.getErrorCode()),"ldap error during search: " + e.getMessage());
        }
        final TimeDuration searchDuration = TimeDuration.fromCurrent(startTime);

        if (pwmApplication.getStatisticsManager() != null && pwmApplication.getStatisticsManager().status() == PwmService.STATUS.OPEN) {
            pwmApplication.getStatisticsManager().updateAverageValue(Statistic.AVG_LDAP_SEARCH_TIME, searchDuration.getTotalMilliseconds());
        }

        if (results.isEmpty()) {
            LOGGER.trace(sessionLabel, "no matches from search (" + searchDuration.asCompactString() +"); " + debugInfo);
            return Collections.emptyMap();
        }

        LOGGER.trace(sessionLabel, "found " + results.size() + " results in " + searchDuration.asCompactString() + "; " + debugInfo);

        final Map<UserIdentity,Map<String,String>> returnMap = new LinkedHashMap<>();
        for (final String userDN : results.keySet()) {
            final UserIdentity userIdentity = new UserIdentity(userDN, ldapProfile.getIdentifier());
            final Map<String,String> attributeMap = results.get(userDN);
            returnMap.put(userIdentity, attributeMap);
        }
        return returnMap;
    }

    private static void validateSpecifiedContext(final PwmApplication pwmApplication, final LdapProfile profile, final String context)
            throws PwmOperationalException
    {
        final Collection<String> loginContexts = profile.getLoginContexts().keySet();
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
        private String ldapProfile;
        private String filter;
        private String username;
        private String groupDN;
        private List<String> contexts;
        private Map<FormConfiguration, String> formValues;
        private transient ChaiProvider chaiProvider;
        private long searchTimeout;

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

        public String getGroupDN() {
            return groupDN;
        }

        public void setGroupDN(String groupDN) {
            this.groupDN = groupDN;
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

        public String getLdapProfile()
        {
            return ldapProfile;
        }

        public void setLdapProfile(String ldapProfile)
        {
            this.ldapProfile = ldapProfile;
        }

        public long getSearchTimeout()
        {
            return searchTimeout;
        }

        public void setSearchTimeout(long searchTimeout)
        {
            this.searchTimeout = searchTimeout;
        }
    }

    public boolean checkIfStringIsDN(final String input) {
        if (input == null || input.length() < 1) {
            return false;
        }

        //if supplied user name starts with username attr assume its the full dn and skip the search
        for (final LdapProfile ldapProfile : pwmApplication.getConfig().getLdapProfiles().values()) {
            final String usernameAttribute = ldapProfile.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
            if (input.toLowerCase().startsWith(usernameAttribute.toLowerCase() + "=")) {
                LOGGER.trace(sessionLabel,
                        "username '" + input + "' appears to be a DN (starts with configured ldap naming attribute'" + usernameAttribute + "'), skipping username search");
                return true;
            } else {
                LOGGER.trace(sessionLabel,
                        "username '" + input + "' does not appear to be a DN (does not start with configured ldap naming attribute '" + usernameAttribute + "')");
            }
        }

        return false;
    }

    public static class UserSearchResults implements Serializable {
        private final Map<String,String> headerAttributeMap;
        private final Map<UserIdentity,Map<String,String>> results;
        private boolean sizeExceeded;

        public UserSearchResults(Map<String, String> headerAttributeMap, Map<UserIdentity, Map<String, String>> results, boolean sizeExceeded) {
            this.headerAttributeMap = headerAttributeMap;
            this.results = Collections.unmodifiableMap(defaultSort(results, headerAttributeMap));
            this.sizeExceeded = sizeExceeded;

        }

        private static Map<UserIdentity, Map<String, String>> defaultSort(
                final Map<UserIdentity, Map<String, String>> results,
                final Map<String,String> headerAttributeMap
        )
        {
            final Date startTime = new Date();
            if (headerAttributeMap == null || headerAttributeMap.isEmpty() || results == null) {
                return results;
            }

            final String sortAttribute = headerAttributeMap.keySet().iterator().next();
            final Comparator<UserIdentity> comparator = new Comparator<UserIdentity>() {
                @Override
                public int compare(UserIdentity o1, UserIdentity o2) {
                    final String s1 = getSortValueByIdentity(o1);
                    final String s2 = getSortValueByIdentity(o2);
                    return s1.compareTo(s2);
                }

                private String getSortValueByIdentity(final UserIdentity userIdentity) {
                    final Map<String,String> valueMap = results.get(userIdentity);
                    if (valueMap != null) {
                        final String sortValue = valueMap.get(sortAttribute);
                        if (sortValue != null) {
                            return sortValue;
                        }
                    }
                    return "";
                }
            };

            final List<UserIdentity> identitySortMap = new ArrayList<>();
            identitySortMap.addAll(results.keySet());
            Collections.sort(identitySortMap,comparator);

            final Map<UserIdentity, Map<String, String>> sortedResults = new LinkedHashMap<>();
            for (final UserIdentity userIdentity : identitySortMap) {
                sortedResults.put(userIdentity, results.get(userIdentity));
            }
            return sortedResults;
        }

        public Map<String, String> getHeaderAttributeMap() {
            return headerAttributeMap;
        }

        public Map<UserIdentity, Map<String, String>> getResults() {
            return results;
        }

        public boolean isSizeExceeded() {
            return sizeExceeded;
        }

        public List<Map<String,Object>> resultsAsJsonOutput(final PwmApplication pwmApplication)
                throws PwmUnrecoverableException
        {
            final List<Map<String,Object>> outputList = new ArrayList<>();
            int idCounter = 0;
            for (final UserIdentity userIdentity : this.getResults().keySet()) {
                final Map<String,Object> rowMap = new LinkedHashMap<>();
                for (final String attribute : this.getHeaderAttributeMap().keySet()) {
                    rowMap.put(attribute,this.getResults().get(userIdentity).get(attribute));
                }
                rowMap.put("userKey",userIdentity.toObfuscatedKey(pwmApplication.getConfig()));
                rowMap.put("id",idCounter);
                outputList.add(rowMap);
                idCounter++;
            }
            return outputList;
        }

        public static Map<String,String> fromFormConfiguration(final List<FormConfiguration> formItems, final Locale locale) {
            final Map<String,String> results = new LinkedHashMap<>();
            for (final FormConfiguration formItem : formItems) {
                results.put(formItem.getName(), formItem.getLabel(locale));
            }
            return results;
        }
    }

    public UserIdentity resolveUserDN(final String userDN) throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException {
        final Collection<LdapProfile> ldapProfiles = pwmApplication.getConfig().getLdapProfiles().values();
        final boolean ignoreUnreachableProfiles = pwmApplication.getConfig().readSettingAsBoolean(
                PwmSetting.LDAP_IGNORE_UNREACHABLE_PROFILES);
        for (final LdapProfile ldapProfile : ldapProfiles) {
            final ChaiProvider provider = pwmApplication.getProxyChaiProvider(ldapProfile.getIdentifier());
            final ChaiUser user = ChaiFactory.createChaiUser(userDN, provider);
            if (user.isValid()) {
                try {
                    return new UserIdentity(user.readCanonicalDN(), ldapProfile.getIdentifier());
                } catch (ChaiOperationException e) {
                    LOGGER.error("unexpected error reading canonical userDN for '" + userDN + "', error: " + e.getMessage());
                }
            }
        }
        throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER));
    }


}
