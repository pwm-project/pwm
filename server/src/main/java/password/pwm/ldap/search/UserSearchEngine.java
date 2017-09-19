/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.ldap.search;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.SearchHelper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DuplicateMode;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class UserSearchEngine implements PwmService {

    private static final PwmLogger LOGGER = PwmLogger.forClass(UserSearchEngine.class);

    private final AtomicInteger searchCounter = new AtomicInteger(0);
    private final AtomicInteger foregroundJobCounter = new AtomicInteger(0);
    private final AtomicInteger backgroundJobCounter = new AtomicInteger(0);
    private final AtomicInteger rejectionJobCounter = new AtomicInteger(0);
    private final AtomicInteger canceledJobCounter = new AtomicInteger(0);
    private final AtomicInteger jobTimeoutCounter = new AtomicInteger(0);

    private PwmApplication pwmApplication;

    private ThreadPoolExecutor executor;

    private final ConditionalTaskExecutor debugOutputTask = new ConditionalTaskExecutor(
            () -> periodicDebugOutput(),
            new ConditionalTaskExecutor.TimeDurationPredicate(1, TimeUnit.MINUTES)
    );

    public UserSearchEngine() {
    }

    @Override
    public STATUS status() {
        return STATUS.OPEN;
    }

    @Override
    public void init(final PwmApplication pwmApplication) throws PwmException {
        this.pwmApplication = pwmApplication;
        this.executor = createExecutor(pwmApplication);
        this.periodicDebugOutput();
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdown();
        }
        executor = null;
    }

    @Override
    public List<HealthRecord> healthCheck() {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfoBean serviceInfo() {
        return new ServiceInfoBean(Collections.emptyList(),debugProperties());
    }

    public UserIdentity resolveUsername(
            final String username,
            final String context,
            final String profile,
            final SessionLabel sessionLabel
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        //check if username is a key
        {
            UserIdentity inputIdentity = null;
            try {
                inputIdentity = UserIdentity.fromKey(username, pwmApplication);
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

        try {
            //see if we need to do a contextless search.
            if (checkIfStringIsDN(username, sessionLabel)) {
                return resolveUserDN(username);
            } else {
                final SearchConfiguration.SearchConfigurationBuilder builder = SearchConfiguration.builder();
                builder.username(username);
                if (context != null) {
                    builder.contexts(Collections.singletonList(context));
                }
                if (profile != null) {
                    builder.ldapProfile(profile);
                }
                final SearchConfiguration searchConfiguration = builder.build();
                return performSingleUserSearch(searchConfiguration, sessionLabel);
            }
        } catch (PwmOperationalException e) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER,e.getErrorInformation().getDetailedErrorMsg(),e.getErrorInformation().getFieldValues()));
        }
    }

    public UserIdentity performSingleUserSearch(
            final SearchConfiguration searchConfiguration,
            final SessionLabel sessionLabel
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final long startTime = System.currentTimeMillis();
        final DuplicateMode dupeMode = pwmApplication.getConfig().readSettingAsEnum(PwmSetting.LDAP_DUPLICATE_MODE, DuplicateMode.class);
        final int searchCount = (dupeMode == DuplicateMode.FIRST_ALL) ? 1 : 2;
        final Map<UserIdentity,Map<String,String>> searchResults = performMultiUserSearch(searchConfiguration, searchCount, Collections.emptyList(), sessionLabel);
        final List<UserIdentity> results = searchResults == null ? Collections.emptyList() : new ArrayList<>(searchResults.keySet());
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
            final List<FormConfiguration> formItem,
            final SessionLabel sessionLabel
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        final Map<String,String> attributeHeaderMap = UserSearchResults.fromFormConfiguration(formItem,locale);
        final Map<UserIdentity,Map<String,String>> searchResults = performMultiUserSearch(
                searchConfiguration,
                maxResults + 1,
                attributeHeaderMap.keySet(),
                sessionLabel
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
            final Collection<String> returnAttributes,
            final SessionLabel sessionLabel
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

        final List<String> errors = new ArrayList<>();

        final long profileRetryDelayMS = Long.valueOf(pwmApplication.getConfig().readAppProperty(AppProperty.LDAP_PROFILE_RETRY_DELAY));

        final List<UserSearchJob> searchJobs = new ArrayList<>();
        for (final LdapProfile ldapProfile : ldapProfiles) {
            boolean skipProfile = false;
            final Instant lastLdapFailure = pwmApplication.getLdapConnectionService().getLastLdapFailureTime(ldapProfile);

            if (ldapProfiles.size() > 1 && lastLdapFailure != null && TimeDuration.fromCurrent(lastLdapFailure).isShorterThan(profileRetryDelayMS)) {
                LOGGER.info("skipping user search on ldap profile " + ldapProfile.getIdentifier() + " due to recent unreachable status (" + TimeDuration.fromCurrent(lastLdapFailure).asCompactString() + ")");
                skipProfile = true;
            }
            if (!skipProfile) {
                try {
                    searchJobs.addAll(this.makeSearchJobs(
                            ldapProfile,
                            searchConfiguration,
                            maxResults,
                            returnAttributes
                    ));
                } catch (PwmUnrecoverableException e) {
                    if (e.getError() == PwmError.ERROR_DIRECTORY_UNAVAILABLE) {
                        pwmApplication.getLdapConnectionService().setLastLdapFailure(ldapProfile,e.getErrorInformation());
                        if (ignoreUnreachableProfiles) {
                            errors.add(e.getErrorInformation().getDetailedErrorMsg());
                            if (errors.size() >= ldapProfiles.size()) {
                                final String errorMsg = "all ldap profiles are unreachable; errors: " + JsonUtil.serializeCollection(errors);
                                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE, errorMsg));
                            }
                        }
                    } else {
                        throw e;
                    }
                }
            }
        }

        final Map<UserIdentity,Map<String,String>> resultsMap = new LinkedHashMap<>(executeSearchJobs(searchJobs, sessionLabel, searchCounter.getAndIncrement()));
        final Map<UserIdentity,Map<String,String>> returnMap = trimOrderedMap(resultsMap, maxResults);
        return Collections.unmodifiableMap(returnMap);
    }


    private Collection<UserSearchJob> makeSearchJobs(
            final LdapProfile ldapProfile,
            final SearchConfiguration searchConfiguration,
            final int maxResults,
            final Collection<String> returnAttributes
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
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

            if (searchConfiguration.isEnableSplitWhitespace()
                    && (searchConfiguration.getUsername().split("\\s").length > 1))
            { // split on all whitespace chars
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
                    validateSpecifiedContext(ldapProfile, searchContext);
                }
            }
        } else {
            searchContexts = ldapProfile.getRootContexts(pwmApplication);
        }

        final long timeLimitMS = searchConfiguration.getSearchTimeout() != 0
                ? searchConfiguration.getSearchTimeout()
                : (ldapProfile.readSettingAsLong(PwmSetting.LDAP_SEARCH_TIMEOUT) * 1000);


        final ChaiProvider chaiProvider = searchConfiguration.getChaiProvider() == null ?
                pwmApplication.getProxyChaiProvider(ldapProfile.getIdentifier()) :
                searchConfiguration.getChaiProvider();

        final List<UserSearchJob> returnMap = new ArrayList<>();
        for (final String loopContext : searchContexts) {
            final UserSearchJob userSearchJob = UserSearchJob.builder()
                    .ldapProfile(ldapProfile)
                    .searchFilter(searchFilter)
                    .context(loopContext)
                    .returnAttributes(returnAttributes)
                    .maxResults(maxResults)
                    .chaiProvider(chaiProvider)
                    .timeoutMs(timeLimitMS)
                    .build();
            returnMap.add(userSearchJob);
        }

        return returnMap;
    }

    private Map<UserIdentity,Map<String,String>> executeSearch(
            final UserSearchJob userSearchJob,
            final SessionLabel sessionLabel,
            final int searchID,
            final int jobID
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        debugOutputTask.conditionallyExecuteTask();

        final SearchHelper searchHelper = new SearchHelper();
        searchHelper.setMaxResults(userSearchJob.getMaxResults());
        searchHelper.setFilter(userSearchJob.getSearchFilter());
        searchHelper.setAttributes(userSearchJob.getReturnAttributes());
        searchHelper.setTimeLimit((int)userSearchJob.getTimeoutMs());

        final String debugInfo;
        {
            final Map<String,String> props = new LinkedHashMap<>();
            props.put("profile", userSearchJob.getLdapProfile().getIdentifier());
            props.put("base", userSearchJob.getContext());
            props.put("maxCount", String.valueOf(searchHelper.getMaxResults()));
            debugInfo = "[" + StringUtil.mapToString(props) + "]";
        }
        log(PwmLogLevel.TRACE, sessionLabel, searchID, jobID, "performing ldap search for user; " + debugInfo);

        final Instant startTime = Instant.now();
        final Map<String, Map<String,String>> results;
        try {
            results = userSearchJob.getChaiProvider().search(userSearchJob.getContext(), searchHelper);
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,e.getMessage()));
        } catch (ChaiOperationException e) {
            throw new PwmOperationalException(PwmError.forChaiError(e.getErrorCode()),"ldap error during searchID="
                    + searchID + ", error=" + e.getMessage());
        }
        final TimeDuration searchDuration = TimeDuration.fromCurrent(startTime);

        if (pwmApplication.getStatisticsManager() != null && pwmApplication.getStatisticsManager().status() == PwmService.STATUS.OPEN) {
            pwmApplication.getStatisticsManager().updateAverageValue(Statistic.AVG_LDAP_SEARCH_TIME, searchDuration.getTotalMilliseconds());
        }

        if (results.isEmpty()) {
            log(PwmLogLevel.TRACE, sessionLabel, searchID, jobID, "no matches from search (" + searchDuration.asCompactString() +"); " + debugInfo);
            return Collections.emptyMap();
        }

        log(PwmLogLevel.TRACE, sessionLabel, searchID, jobID, "found " + results.size() + " results in " + searchDuration.asCompactString() + "; " + debugInfo);

        final Map<UserIdentity,Map<String,String>> returnMap = new LinkedHashMap<>();
        for (final String userDN : results.keySet()) {
            final UserIdentity userIdentity = new UserIdentity(userDN, userSearchJob.getLdapProfile().getIdentifier());
            final Map<String,String> attributeMap = results.get(userDN);
            returnMap.put(userIdentity, attributeMap);
        }
        return returnMap;
    }

    private void validateSpecifiedContext(final LdapProfile profile, final String context)
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final Map<String,String> selectableContexts = profile.getSelectableContexts(pwmApplication);
        if (selectableContexts == null || selectableContexts.isEmpty()) {
            throw new PwmOperationalException(PwmError.ERROR_UNKNOWN,"context specified, but no selectable contexts are configured");
        }

        for (final String loopContext : selectableContexts.keySet()) {
            if (loopContext.equals(context)) {
                return;
            }
        }

        throw new PwmOperationalException(PwmError.ERROR_UNKNOWN,"context '" + context + "' is specified, but is not in configuration");
    }

    private boolean checkIfStringIsDN(
            final String input,
            final SessionLabel sessionLabel
    )
    {
        if (input == null || input.length() < 1) {
            return false;
        }

        //if supplied user name starts with username attr assume its the full dn and skip the search
        final Set<String> namingAttributes = new HashSet<>();
        for (final LdapProfile ldapProfile : pwmApplication.getConfig().getLdapProfiles().values()) {
            final String usernameAttribute = ldapProfile.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
            if (input.toLowerCase().startsWith(usernameAttribute.toLowerCase() + "=")) {
                LOGGER.trace(sessionLabel,
                        "username '" + input + "' appears to be a DN (starts with configured ldap naming attribute'" + usernameAttribute + "'), skipping username search");
                return true;
            }
            namingAttributes.add(usernameAttribute);
        }

        LOGGER.trace(sessionLabel, "username '" + input + "' does not appear to be a DN (does not start with any of the configured ldap naming attributes '"
                + StringUtil.collectionToString(namingAttributes,",")
                + "')");

        return false;
    }


    private UserIdentity resolveUserDN(
            final String userDN
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        final Collection<LdapProfile> ldapProfiles = pwmApplication.getConfig().getLdapProfiles().values();
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

    private Map<UserIdentity,Map<String,String>> executeSearchJobs(
            final Collection<UserSearchJob> userSearchJobs,
            final SessionLabel sessionLabel,
            final int searchID
    )
            throws PwmUnrecoverableException
    {
        // create jobs
        final List<JobInfo> jobs = new ArrayList<>();
        {
            int jobID = 0;
            for (UserSearchJob userSearchJob : userSearchJobs) {
                final int loopJobID = jobID++;

                final FutureTask<Map<UserIdentity, Map<String, String>>> futureTask = new FutureTask<>(()
                        -> executeSearch(userSearchJob, sessionLabel, searchID, loopJobID));

                final JobInfo jobInfo = new JobInfo(searchID, loopJobID, userSearchJob, futureTask);

                jobs.add(jobInfo);
            }
        }

        final Instant startTime = Instant.now();
        {
            final String filterText = jobs.isEmpty() ? "" : ", filter: " + jobs.iterator().next().getUserSearchJob().getSearchFilter();
            log(PwmLogLevel.DEBUG, sessionLabel, searchID, -1, "beginning user search process with " + jobs.size() + " search jobs" + filterText);
        }

        // execute jobs
        for (Iterator<JobInfo> iterator = jobs.iterator(); iterator.hasNext(); ) {
            final JobInfo jobInfo = iterator.next();

            boolean submittedToExecutor = false;

            // use current thread to execute one (the last in the loop) task.
            if (executor != null && iterator.hasNext()) {
                try {
                    executor.submit(jobInfo.getFutureTask());
                    submittedToExecutor = true;
                    backgroundJobCounter.incrementAndGet();
                } catch (RejectedExecutionException e) {
                    // executor is full, so revert to running locally
                    rejectionJobCounter.incrementAndGet();
                }
            }

            if (!submittedToExecutor) {
                try {
                    jobInfo.getFutureTask().run();
                    foregroundJobCounter.incrementAndGet();
                } catch (Throwable t) {
                    log(PwmLogLevel.ERROR, sessionLabel, searchID, jobInfo.getJobID(), "unexpected error running job in local thread: " + t.getMessage());
                }
            }
        }

        // aggregate results
        final Map<UserIdentity,Map<String,String>> results = new LinkedHashMap<>();
        for (final JobInfo jobInfo : jobs) {
            if (results.size() > jobInfo.getUserSearchJob().getMaxResults()) {
                final FutureTask futureTask = jobInfo.getFutureTask();
                if (!futureTask.isDone()) {
                    canceledJobCounter.incrementAndGet();
                }
                jobInfo.getFutureTask().cancel(false);
            } else {
                final long maxWaitTime = jobInfo.getUserSearchJob().getTimeoutMs() * 3;
                try {
                    results.putAll(jobInfo.getFutureTask().get(maxWaitTime, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    final String errorMsg = "unexpected interruption during search job execution: " + e.getMessage();
                    log(PwmLogLevel.WARN, sessionLabel, searchID, jobInfo.getJobID(), errorMsg);
                    LOGGER.error(sessionLabel, errorMsg, e);
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg));
                } catch (ExecutionException e) {
                    final Throwable t = e.getCause();
                    final ErrorInformation errorInformation;
                    final String errorMsg = "unexpected error during ldap search ("
                            + "profile=" + jobInfo.getUserSearchJob().getLdapProfile().getIdentifier() + ")"
                            + ", error: " + (t instanceof PwmException ? t.getMessage() : JavaHelper.readHostileExceptionMessage(t));
                    if (t instanceof PwmException) {
                        errorInformation = new ErrorInformation(((PwmException) t).getError(), errorMsg);
                    } else {
                        errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                    }
                    log(PwmLogLevel.WARN, sessionLabel, searchID, jobInfo.getJobID(), "error during user search: " + errorInformation.toDebugStr());
                    throw new PwmUnrecoverableException(errorInformation);
                } catch (TimeoutException e) {
                    final String errorMsg = "background search job timeout after " + jobInfo.getUserSearchJob().getTimeoutMs()
                            + "ms, to ldapProfile '"
                            + jobInfo.getUserSearchJob().getLdapProfile() + "'";
                    log(PwmLogLevel.WARN, sessionLabel, searchID, jobInfo.getJobID(), "error during user search: " + errorMsg);
                    jobTimeoutCounter.incrementAndGet();
                }
            }
        }

        log(PwmLogLevel.DEBUG, sessionLabel, searchID, -1, "completed user search process in "
                + TimeDuration.fromCurrent(startTime).asCompactString()
                + ", intermediate result size=" + results.size());
        return Collections.unmodifiableMap(results);
    }

    @Getter
    @AllArgsConstructor
    private static class JobInfo {
        private final int searchID;
        private final int jobID;
        private final UserSearchJob userSearchJob;
        private final FutureTask<Map<UserIdentity,Map<String,String>>> futureTask;
    }

    private Map<String,String> debugProperties() {
        final Map<String,String> properties = new TreeMap<>();
        properties.put("searchCount", this.searchCounter.toString());
        properties.put("backgroundJobCounter", Integer.toString(this.backgroundJobCounter.get()));
        properties.put("foregroundJobCounter", Integer.toString(this.foregroundJobCounter.get()));
        properties.put("jvmThreadCount", Integer.toString(Thread.activeCount()));
        if (executor == null) {
            properties.put("background-enabled","false");
        } else {
            properties.put("background-enabled","true");
            properties.put("background-maxPoolSize", Integer.toString(executor.getMaximumPoolSize()));
            properties.put("background-activeCount", Integer.toString(executor.getActiveCount()));
            properties.put("background-largestPoolSize", Integer.toString(executor.getLargestPoolSize()));
            properties.put("background-poolSize", Integer.toString(executor.getPoolSize()));
            properties.put("background-queue-size", Integer.toString(executor.getQueue().size()));
            properties.put("background-rejectionJobCounter", Integer.toString(rejectionJobCounter.get()));
            properties.put("background-canceledJobCounter", Integer.toString(canceledJobCounter.get()));
            properties.put("background-jobTimeoutCounter", Integer.toString(jobTimeoutCounter.get()));
        }
        return Collections.unmodifiableMap(properties);
    }

    private void periodicDebugOutput() {
        LOGGER.debug("periodic debug status: " + StringUtil.mapToString(debugProperties()));
    }

    private void log(final PwmLogLevel level, final SessionLabel sessionLabel, final int searchID, final int jobID, final String message) {
        final String idMsg = logIdString(searchID, jobID);
        LOGGER.log(level, sessionLabel, idMsg + " " + message);
    }

    private static String logIdString(final int searchID, final int jobID) {
        String idMsg = "searchID=" + searchID;
        if (jobID >= 0) {
            idMsg += "-" + jobID;
        }
        return idMsg;
    }

    private static ThreadPoolExecutor createExecutor(final PwmApplication pwmApplication) {
        final Configuration configuration = pwmApplication.getConfig();

        final boolean enabled = Boolean.parseBoolean(configuration.readAppProperty(AppProperty.LDAP_SEARCH_PARALLEL_ENABLE));
        if (!enabled) {
            return null;
        }

        final int endPoints;
        {
            int counter = 0;
            for (final LdapProfile ldapProfile : configuration.getLdapProfiles().values()) {
                final List<String> rootContexts = ldapProfile.readSettingAsStringArray(PwmSetting.LDAP_CONTEXTLESS_ROOT);
                counter += rootContexts.size();
            }
            endPoints = counter;
        }

        if (endPoints > 1) {
            final int factor = Integer.parseInt(configuration.readAppProperty(AppProperty.LDAP_SEARCH_PARALLEL_FACTOR));
            final int maxThreads = Integer.parseInt(configuration.readAppProperty(AppProperty.LDAP_SEARCH_PARALLEL_THREAD_MAX));
            final int threads = Math.min(maxThreads, (endPoints) * factor);
            final ThreadFactory threadFactory = JavaHelper.makePwmThreadFactory(JavaHelper.makeThreadName(pwmApplication, UserSearchEngine.class), true);
            return  new ThreadPoolExecutor(
                    threads,
                    threads,
                    1,
                    TimeUnit.MINUTES,
                    new ArrayBlockingQueue<>(threads),
                    threadFactory
            );
        }
        return null;
    }

    private static <K,V> Map<K,V> trimOrderedMap(final Map<K,V> inputMap, final int maxEntries) {
        final Map<K,V> returnMap = new LinkedHashMap<>(inputMap);
        if (returnMap.size() > maxEntries) {
            int counter = 0;
            for (final Iterator<K> iterator = returnMap.keySet().iterator() ; iterator.hasNext(); ) {
                iterator.next();
                counter++;
                if (counter > maxEntries) {
                    iterator.remove();
                }
            }
        }
        return Collections.unmodifiableMap(returnMap);
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
}
