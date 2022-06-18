/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.ldap.search;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DuplicateMode;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.PwmScheduler;
import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
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
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;


public class UserSearchEngine extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( UserSearchEngine.class );

    private final StatisticCounterBundle<SearchStatistic> counters = new StatisticCounterBundle<>( SearchStatistic.class );
    private final AtomicLoopIntIncrementer searchIdCounter = new AtomicLoopIntIncrementer();

    enum SearchStatistic
    {
        searchCounter,
        foregroundJobCounter,
        backgroundJobCounter,
        backgroundRejectionJobCounter,
        backgroundCanceledJobCounter,
        backgroundJobTimeoutCounter,
    }

    private PwmDomain pwmDomain;

    private ThreadPoolExecutor executor;

    private final ConditionalTaskExecutor debugOutputTask = ConditionalTaskExecutor.forPeriodicTask(
            this::periodicDebugOutput,
            TimeDuration.of( 1, TimeDuration.Unit.MINUTES ).asDuration()
    );

    public UserSearchEngine( )
    {
    }

    @Override
    protected Set<PwmApplication.Condition> openConditions()
    {
        return Collections.emptySet();
    }

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        this.pwmDomain = pwmApplication.domains().get( domainID );
        this.executor = createExecutor( pwmDomain );
        this.periodicDebugOutput();

        return STATUS.OPEN;
    }

    @Override
    public void shutdownImpl( )
    {
        if ( executor != null )
        {
            executor.shutdown();
        }
        executor = null;
    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return ServiceInfoBean.builder().debugProperties( debugProperties() ).build();
    }

    public UserIdentity resolveUsername(
            final String username,
            final String context,
            final String profile,
            final SessionLabel sessionLabel
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        //check if username is a key
        {
            UserIdentity inputIdentity = null;
            try
            {
                inputIdentity = UserIdentity.fromKey( sessionLabel, username, pwmDomain.getPwmApplication() );
            }
            catch ( final PwmException e )
            {
                /* input is not a userIdentity */
            }

            if ( inputIdentity != null )
            {
                try
                {
                    final ChaiUser theUser = pwmDomain.getProxiedChaiUser( sessionLabel, inputIdentity );
                    if ( theUser.exists() )
                    {
                        final String canonicalDN;
                        canonicalDN = theUser.readCanonicalDN();
                        return UserIdentity.create( canonicalDN, inputIdentity.getLdapProfileID(), pwmDomain.getDomainID() );
                    }
                }
                catch ( final ChaiOperationException e )
                {
                    throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_CANT_MATCH_USER, e.getMessage() ) );
                }
                catch ( final ChaiUnavailableException e )
                {
                    throw PwmUnrecoverableException.fromChaiException( e );
                }
            }
        }

        try
        {
            //see if we need to do a contextless search.
            if ( checkIfStringIsDN( username, sessionLabel ) )
            {
                return resolveUserDN( username, sessionLabel );
            }
            else
            {
                final SearchConfiguration.SearchConfigurationBuilder builder = SearchConfiguration.builder();
                builder.username( username );
                if ( context != null )
                {
                    builder.contexts( Collections.singletonList( context ) );
                }
                if ( profile != null )
                {
                    builder.ldapProfile( profile );
                }
                final SearchConfiguration searchConfiguration = builder.build();
                return performSingleUserSearch( searchConfiguration, sessionLabel );
            }
        }
        catch ( final PwmOperationalException e )
        {
            throw new PwmOperationalException( new ErrorInformation(
                    PwmError.ERROR_CANT_MATCH_USER,
                    e.getErrorInformation().getDetailedErrorMsg(),
                    e.getErrorInformation().getFieldValues() )
            );
        }
    }

    public UserIdentity performSingleUserSearch(
            final SearchConfiguration searchConfiguration,
            final SessionLabel sessionLabel
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final Instant startTime = Instant.now();
        final DuplicateMode dupeMode = pwmDomain.getConfig().readSettingAsEnum( PwmSetting.LDAP_DUPLICATE_MODE, DuplicateMode.class );
        final int searchCount = ( dupeMode == DuplicateMode.FIRST_ALL ) ? 1 : 2;
        final Map<UserIdentity, Map<String, String>> searchResults = performMultiUserSearch( searchConfiguration, searchCount, Collections.emptyList(), sessionLabel );
        final List<UserIdentity> results = searchResults == null ? Collections.emptyList() : new ArrayList<>( searchResults.keySet() );
        if ( results.isEmpty() )
        {
            final String errorMessage;
            if ( searchConfiguration.getUsername() != null && searchConfiguration.getUsername().length() > 0 )
            {
                errorMessage = "an ldap user for username value '" + searchConfiguration.getUsername() + "' was not found";
            }
            else
            {
                errorMessage = "an ldap user was not found";
            }
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_CANT_MATCH_USER, errorMessage ) );
        }
        else if ( results.size() == 1 )
        {
            final String userDN = results.get( 0 ).getUserDN();
            LOGGER.debug( sessionLabel, () -> "found userDN: " + userDN + " (" + TimeDuration.compactFromCurrent( startTime ) + ")" );
            return results.get( 0 );
        }
        if ( dupeMode == DuplicateMode.FIRST_PROFILE )
        {
            final String profile1 = results.get( 0 ).getLdapProfileID();
            final String profile2 = results.get( 1 ).getLdapProfileID();
            final boolean sameProfile = ( profile1 == null && profile2 == null )
                    || ( profile1 != null && profile1.equals( profile2 ) );

            if ( sameProfile )
            {
                final String errorMessage = "multiple user matches in single profile";
                throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_CANT_MATCH_USER, errorMessage ) );
            }

            LOGGER.trace( sessionLabel, () -> "found multiple matches, but will use first match since second match"
                    + " is in a different profile and dupeMode is set to "
                    + DuplicateMode.FIRST_PROFILE );
            return results.get( 0 );
        }
        final String errorMessage = "multiple user matches found";
        throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_CANT_MATCH_USER, errorMessage ) );
    }

    public UserSearchResults performMultiUserSearchFromForm(
            final Locale locale,
            final SearchConfiguration searchConfiguration,
            final int maxResults,
            final List<FormConfiguration> formItem,
            final SessionLabel sessionLabel
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final Map<String, String> attributeHeaderMap = UserSearchResults.fromFormConfiguration( formItem, locale );
        final Map<UserIdentity, Map<String, String>> searchResults = performMultiUserSearch(
                searchConfiguration,
                maxResults + 1,
                attributeHeaderMap.keySet(),
                sessionLabel
        );
        final boolean resultsExceeded = searchResults.size() > maxResults;
        final Map<UserIdentity, Map<String, String>> returnData = new LinkedHashMap<>( Math.min( maxResults, searchResults.size() ) );
        for ( final Map.Entry<UserIdentity, Map<String, String>> entry : searchResults.entrySet() )
        {
            final UserIdentity loopUser = entry.getKey();
            returnData.put( loopUser, entry.getValue() );
            if ( returnData.size() >= maxResults )
            {
                break;
            }
        }
        return new UserSearchResults( attributeHeaderMap, returnData, resultsExceeded );
    }

    public Map<UserIdentity, Map<String, String>> performMultiUserSearch(
            final SearchConfiguration searchConfiguration,
            final int maxResults,
            final Collection<String> returnAttributes,
            final SessionLabel sessionLabel
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final Collection<LdapProfile> ldapProfiles;
        if ( searchConfiguration.getLdapProfile() != null && !searchConfiguration.getLdapProfile().isEmpty() )
        {
            if ( pwmDomain.getConfig().getLdapProfiles().containsKey( searchConfiguration.getLdapProfile() ) )
            {
                ldapProfiles = Collections.singletonList( pwmDomain.getConfig().getLdapProfiles().get( searchConfiguration.getLdapProfile() ) );
            }
            else
            {
                LOGGER.debug( sessionLabel, () -> "attempt to search for users in unknown ldap profile '"
                        + searchConfiguration.getLdapProfile() + "', skipping search" );
                return Collections.emptyMap();
            }
        }
        else
        {
            ldapProfiles = pwmDomain.getConfig().getLdapProfiles().values();
        }

        final boolean ignoreUnreachableProfiles = pwmDomain.getConfig().readSettingAsBoolean( PwmSetting.LDAP_IGNORE_UNREACHABLE_PROFILES );

        final List<String> errors = new ArrayList<>();

        counters.increment( SearchStatistic.searchCounter );
        final int searchID = searchIdCounter.next();
        final long profileRetryDelayMS = Long.parseLong( pwmDomain.getConfig().readAppProperty( AppProperty.LDAP_PROFILE_RETRY_DELAY ) );
        final AtomicLoopIntIncrementer jobIncrementer = AtomicLoopIntIncrementer.builder().build();

        final List<UserSearchJob> searchJobs = new ArrayList<>();

        for ( final LdapProfile ldapProfile : ldapProfiles )
        {
            boolean skipProfile = false;
            final Instant lastLdapFailure = pwmDomain.getLdapConnectionService().getLastLdapFailureTime( ldapProfile );

            if ( ldapProfiles.size() > 1 && lastLdapFailure != null && TimeDuration.fromCurrent( lastLdapFailure ).isShorterThan( profileRetryDelayMS ) )
            {
                LOGGER.info( () -> "skipping user search on ldap profile " + ldapProfile.getIdentifier() + " due to recent unreachable status ("
                        + TimeDuration.fromCurrent( lastLdapFailure ).asCompactString() + ")" );
                skipProfile = true;
            }
            if ( !skipProfile )
            {
                try
                {
                    searchJobs.addAll( this.makeSearchJobs(
                            ldapProfile,
                            searchConfiguration,
                            maxResults,
                            returnAttributes,
                            sessionLabel,
                            searchID,
                            jobIncrementer
                    ) );
                }
                catch ( final PwmUnrecoverableException e )
                {
                    if ( e.getError() == PwmError.ERROR_DIRECTORY_UNAVAILABLE )
                    {
                        pwmDomain.getLdapConnectionService().setLastLdapFailure( ldapProfile, e.getErrorInformation() );
                        if ( ignoreUnreachableProfiles )
                        {
                            errors.add( e.getErrorInformation().getDetailedErrorMsg() );
                            if ( errors.size() >= ldapProfiles.size() )
                            {
                                final String errorMsg = "all ldap profiles are unreachable; errors: " + JsonFactory.get().serializeCollection( errors );
                                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, errorMsg ) );
                            }
                        }
                    }
                    else
                    {
                        throw e;
                    }
                }
            }
        }

        final Map<UserIdentity, Map<String, String>> resultsMap = new LinkedHashMap<>( executeSearchJobs( searchJobs ) );
        return trimOrderedMap( resultsMap, maxResults );
    }


    private Collection<UserSearchJob> makeSearchJobs(
            final LdapProfile ldapProfile,
            final SearchConfiguration searchConfiguration,
            final int maxResults,
            final Collection<String> returnAttributes,
            final SessionLabel sessionLabel,
            final int searchID,
            final AtomicLoopIntIncrementer jobIncrementer
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        // check the search configuration data params
        searchConfiguration.validate();

        final String inputSearchFilter = searchConfiguration.getFilter() != null && searchConfiguration.getFilter().length() > 1
                ? searchConfiguration.getFilter()
                : ldapProfile.readSettingAsString( PwmSetting.LDAP_USERNAME_SEARCH_FILTER );

        final String searchFilter = makeSearchFilter( ldapProfile, searchConfiguration, inputSearchFilter );

        final List<String> searchContexts;
        if ( searchConfiguration.getContexts() != null
                && !searchConfiguration.getContexts().isEmpty()
                && searchConfiguration.getContexts().iterator().next() != null
                && searchConfiguration.getContexts().iterator().next().length() > 0
                )
        {
            searchContexts = searchConfiguration.getContexts();

            if ( searchConfiguration.isEnableContextValidation() )
            {
                for ( final String searchContext : searchContexts )
                {
                    validateSpecifiedContext( sessionLabel, ldapProfile, searchContext );
                }
            }
        }
        else
        {
            searchContexts = ldapProfile.getRootContexts( sessionLabel, pwmDomain );
        }

        final long timeLimitMS = searchConfiguration.getSearchTimeout() != null
                ? searchConfiguration.getSearchTimeout().asMillis()
                : ( ldapProfile.readSettingAsLong( PwmSetting.LDAP_SEARCH_TIMEOUT ) * 1000 );

        final ChaiProvider chaiProvider = searchConfiguration.getChaiProvider() == null
                ? pwmDomain.getProxyChaiProvider( sessionLabel, ldapProfile.getIdentifier() )
                : searchConfiguration.getChaiProvider();

        final List<UserSearchJob> returnMap = new ArrayList<>( searchContexts.size() );
        for ( final String loopContext : searchContexts )
        {
            final UserSearchJobParameters userSearchJobParameters = UserSearchJobParameters.builder()
                    .ldapProfile( ldapProfile )
                    .searchFilter( searchFilter )
                    .context( loopContext )
                    .returnAttributes( returnAttributes )
                    .maxResults( maxResults )
                    .chaiProvider( chaiProvider )
                    .timeoutMs( timeLimitMS )
                    .sessionLabel( sessionLabel )
                    .searchID( searchID )
                    .jobId( jobIncrementer.next() )
                    .searchScope( searchConfiguration.getSearchScope() )
                    .ignoreOperationalErrors( searchConfiguration.isIgnoreOperationalErrors() )
                    .build();
            final UserSearchJob userSearchJob = new UserSearchJob( pwmDomain, this, userSearchJobParameters );
            returnMap.add( userSearchJob );
        }

        return returnMap;
    }

    private String makeSearchFilter( final LdapProfile ldapProfile, final SearchConfiguration searchConfiguration, final String inputSearchFilter )
    {
        final String searchFilter;
        if ( searchConfiguration.getUsername() != null )
        {
            final String inputQuery = searchConfiguration.isEnableValueEscaping()
                    ? StringUtil.escapeLdapFilter( searchConfiguration.getUsername() )
                    : searchConfiguration.getUsername();

            if ( searchConfiguration.isEnableSplitWhitespace()
                    && ( searchConfiguration.getUsername().split( "\\s" ).length > 1 ) )
            {
                // split on all whitespace chars
                final StringBuilder multiSearchFilter = new StringBuilder();
                multiSearchFilter.append( "(&" );
                for ( final String queryPart : searchConfiguration.getUsername().split( " " ) )
                {
                    multiSearchFilter.append( '(' );
                    multiSearchFilter.append( inputSearchFilter.replace( PwmConstants.VALUE_REPLACEMENT_USERNAME, queryPart ) );
                    multiSearchFilter.append( ')' );
                }
                multiSearchFilter.append( ')' );
                searchFilter = multiSearchFilter.toString();
            }
            else
            {
                searchFilter = inputSearchFilter.replace( PwmConstants.VALUE_REPLACEMENT_USERNAME, inputQuery.trim() );
            }
        }
        else if ( searchConfiguration.getGroupDN() != null )
        {
            final String groupAttr = ldapProfile.readSettingAsString( PwmSetting.LDAP_USER_GROUP_ATTRIBUTE );
            searchFilter = '(' + groupAttr + '=' + searchConfiguration.getGroupDN() + ')';
        }
        else if ( searchConfiguration.getFormValues() != null )
        {
            searchFilter = figureSearchFilterForParams( searchConfiguration.getFormValues(), inputSearchFilter, searchConfiguration.isEnableValueEscaping() );
        }
        else
        {
            searchFilter = inputSearchFilter;
        }
        return searchFilter;
    }


    private void validateSpecifiedContext( final SessionLabel sessionLabel, final LdapProfile profile, final String context )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        Objects.requireNonNull( profile, "ldapProfile can not be null for ldap search context validation" );
        Objects.requireNonNull( context, "context can not be null for ldap search context validation" );

        final String canonicalContext = profile.readCanonicalDN( sessionLabel, pwmDomain, context );

        {
            final Map<String, String> selectableContexts = profile.getSelectableContexts( sessionLabel, pwmDomain );
            if ( !CollectionUtil.isEmpty( selectableContexts ) && selectableContexts.containsKey( canonicalContext ) )
            {
                // config pre-validates selectable contexts so this should be permitted
                return;
            }
        }

        {
            final List<String> rootContexts = profile.getRootContexts( sessionLabel, pwmDomain );
            if ( !CollectionUtil.isEmpty( rootContexts ) )
            {
                for ( final String rootContext : rootContexts )
                {
                    if ( canonicalContext.endsWith( rootContext ) )
                    {
                        return;
                    }
                }

                final String msg = "specified search context '" + canonicalContext + "' is not contained by a configured root context";
                throw new PwmUnrecoverableException( PwmError.CONFIG_FORMAT_ERROR, msg );
            }
        }

        final String msg = "specified search context '" + canonicalContext + "', but no selectable contexts or root are configured";
        throw new PwmOperationalException( PwmError.ERROR_INTERNAL, msg );
    }

    private boolean checkIfStringIsDN(
            final String input,
            final SessionLabel sessionLabel
    )
    {
        if ( StringUtil.isEmpty( input ) )
        {
            return false;
        }

        //if supplied user name starts with username attr assume its the full dn and skip the search
        final Set<String> namingAttributes = new HashSet<>( pwmDomain.getConfig().getLdapProfiles().size() );
        for ( final LdapProfile ldapProfile : pwmDomain.getConfig().getLdapProfiles().values() )
        {
            final String usernameAttribute = ldapProfile.readSettingAsString( PwmSetting.LDAP_NAMING_ATTRIBUTE );
            if ( input.toLowerCase().startsWith( usernameAttribute.toLowerCase() + "=" ) )
            {
                LOGGER.trace( sessionLabel, () -> "username '" + input
                        + "' appears to be a DN (starts with configured ldap naming attribute '"
                        + usernameAttribute + "'), skipping username search" );
                return true;
            }
            namingAttributes.add( usernameAttribute );
        }

        LOGGER.trace( sessionLabel, () -> "username '" + input + "' does not appear to be a DN (does not start with any of the configured ldap naming attributes '"
                + StringUtil.collectionToString( namingAttributes, "," )
                + "')" );

        return false;
    }

    private UserIdentity resolveUserDN(
            final String userDN,
            final SessionLabel sessionLabel
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        LOGGER.trace( sessionLabel, () -> "finding profile for userDN " + userDN );
        final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                .filter( "(objectClass=*)" )
                .enableContextValidation( false )
                .contexts( Collections.singletonList( userDN ) )
                .searchScope( SearchConfiguration.SearchScope.base )
                .ignoreOperationalErrors( true )
                .build();
        final Map<UserIdentity, Map<String, String>> results = performMultiUserSearch(
                searchConfiguration,
                1,
                Collections.singleton( "objectClass" ),
                sessionLabel );

        if ( results.size() < 1 )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_CANT_MATCH_USER ) );
        }
        else if ( results.size() > 1 )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_CANT_MATCH_USER, "duplicate DN matches discovered" ) );
        }

        final UserIdentity userIdentity = results.keySet().iterator().next();
        validateSpecifiedContext( sessionLabel, userIdentity.getLdapProfile( pwmDomain.getPwmApplication().getConfig() ), userIdentity.getUserDN() );
        return userIdentity;
    }

    private Map<UserIdentity, Map<String, String>> executeSearchJobs(
            final Collection<UserSearchJob> userSearchJobs
    )
            throws PwmUnrecoverableException
    {
        if ( CollectionUtil.isEmpty( userSearchJobs ) )
        {
            return Collections.emptyMap();
        }

        debugOutputTask.conditionallyExecuteTask();

        final UserSearchJobParameters firstParam = userSearchJobs.iterator().next().getUserSearchJobParameters();

        final Instant startTime = Instant.now();
        {
            final String filterText = ", filter: " + firstParam.getSearchFilter();
            final SessionLabel sessionLabel = firstParam.getSessionLabel();
            final int searchID = firstParam.getSearchID();
            log( PwmLogLevel.DEBUG, sessionLabel, searchID, -1, "beginning user search process with " + userSearchJobs.size() + " search jobs" + filterText );
        }

        // execute jobs
        for ( final Iterator<UserSearchJob> iterator = userSearchJobs.iterator(); iterator.hasNext(); )
        {

            final UserSearchJob jobInfo = iterator.next();

            boolean submittedToExecutor = false;

            // use current thread to execute one (the last in the loop) task.
            if ( executor != null && iterator.hasNext() )
            {
                try
                {
                    executor.submit( jobInfo.getFutureTask() );
                    submittedToExecutor = true;
                    counters.increment( SearchStatistic.backgroundJobCounter );
                }
                catch ( final RejectedExecutionException e )
                {
                    // executor is full, so revert to running locally
                    counters.increment( SearchStatistic.backgroundRejectionJobCounter );
                }
            }

            if ( !submittedToExecutor )
            {
                try
                {
                    jobInfo.getFutureTask().run();
                    counters.increment( SearchStatistic.foregroundJobCounter );
                }
                catch ( final Throwable t )
                {
                    log( PwmLogLevel.ERROR, firstParam.getSessionLabel(), firstParam.getSearchID(), firstParam.getJobId(),
                            "unexpected error running job in local thread: " + t.getMessage() );
                }
            }
        }

        final Map<UserIdentity, Map<String, String>> results = aggregateJobResults( userSearchJobs );

        log( PwmLogLevel.DEBUG, firstParam.getSessionLabel(), firstParam.getSearchID(), -1, "completed user search process in "
                + TimeDuration.fromCurrent( startTime ).asCompactString()
                + ", intermediate result size=" + results.size() );

        return Collections.unmodifiableMap( results );
    }

    private Map<UserIdentity, Map<String, String>> aggregateJobResults(
            final Collection<UserSearchJob> userSearchJobs
    )
            throws PwmUnrecoverableException
    {
        final Map<UserIdentity, Map<String, String>> results = new LinkedHashMap<>();

        for ( final UserSearchJob jobInfo : userSearchJobs )
        {
            final UserSearchJobParameters params = jobInfo.getUserSearchJobParameters();
            if ( results.size() > jobInfo.getUserSearchJobParameters().getMaxResults() )
            {
                final FutureTask<Map<UserIdentity, Map<String, String>>> futureTask = jobInfo.getFutureTask();
                if ( !futureTask.isDone() )
                {
                    counters.increment( SearchStatistic.backgroundCanceledJobCounter );
                }
                jobInfo.getFutureTask().cancel( false );
            }
            else
            {
                try
                {
                    results.putAll( jobInfo.getFutureTask().get( ) );
                }
                catch ( final InterruptedException e )
                {
                    final String errorMsg = "unexpected interruption during search job execution: " + e.getMessage();
                    log( PwmLogLevel.WARN, params.getSessionLabel(), params.getSearchID(), params.getJobId(), errorMsg );
                    LOGGER.error( params.getSessionLabel(), () -> errorMsg, e );
                    throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg ) );
                }
                catch ( final ExecutionException e )
                {
                    final Throwable t = e.getCause();
                    final ErrorInformation errorInformation;
                    final String errorMsg = "unexpected error during ldap search ("
                            + "domain=" + pwmDomain.getDomainID() + " "
                            + "profile=" + jobInfo.getUserSearchJobParameters().getLdapProfile().getIdentifier() + ")"
                            + ", error: " + ( t instanceof PwmException ? t.getMessage() : JavaHelper.readHostileExceptionMessage( t ) );
                    if ( t instanceof PwmException )
                    {
                        errorInformation = new ErrorInformation( ( ( PwmException ) t ).getError(), errorMsg );
                    }
                    else
                    {
                        errorInformation = new ErrorInformation( PwmError.ERROR_LDAP_DATA_ERROR, errorMsg );
                    }
                    log( PwmLogLevel.WARN, params.getSessionLabel(), params.getSearchID(), params.getJobId(), "error during user search: " + errorInformation.toDebugStr() );
                    throw new PwmUnrecoverableException( errorInformation );
                }
            }
        }
        return results;
    }

    private Map<String, String> debugProperties( )
    {
        final Map<String, String> properties = new TreeMap<>( counters.debugStats() );
        properties.put( "jvmThreadCount", Integer.toString( Thread.activeCount() ) );
        if ( executor == null )
        {
            properties.put( "background-enabled", "false" );
        }
        else
        {
            properties.put( "background-enabled", "true" );
            properties.put( "background-maxPoolSize", Integer.toString( executor.getMaximumPoolSize() ) );
            properties.put( "background-activeCount", Integer.toString( executor.getActiveCount() ) );
            properties.put( "background-largestPoolSize", Integer.toString( executor.getLargestPoolSize() ) );
            properties.put( "background-poolSize", Integer.toString( executor.getPoolSize() ) );
            properties.put( "background-queue-size", Integer.toString( executor.getQueue().size() ) );
        }
        return Collections.unmodifiableMap( properties );
    }

    private void periodicDebugOutput( )
    {
        LOGGER.trace( getSessionLabel(), () -> "periodic debug status: " + StringUtil.mapToString( debugProperties() ) );
    }

    void log( final PwmLogLevel level, final SessionLabel sessionLabel, final int searchID, final int jobID, final String message )
    {
        final String idMsg = "domain=" + pwmDomain.getDomainID() + " " + logIdString( searchID, jobID );
        LOGGER.log( level, sessionLabel, () -> idMsg + " " + message );
    }

    private static String logIdString( final int searchID, final int jobID )
    {
        String idMsg = "searchID=" + searchID;
        if ( jobID >= 0 )
        {
            idMsg += "-" + jobID;
        }
        return idMsg;
    }

    private ThreadPoolExecutor createExecutor( final PwmDomain pwmDomain )
    {
        final DomainConfig domainConfig = pwmDomain.getConfig();

        final boolean enabled = Boolean.parseBoolean( domainConfig.readAppProperty( AppProperty.LDAP_SEARCH_PARALLEL_ENABLE ) );
        if ( !enabled )
        {
            return null;
        }

        final int endPoints;
        {
            int counter = 0;
            for ( final LdapProfile ldapProfile : domainConfig.getLdapProfiles().values() )
            {
                final List<String> rootContexts = ldapProfile.readSettingAsStringArray( PwmSetting.LDAP_CONTEXTLESS_ROOT );
                counter += rootContexts.size();
            }
            endPoints = counter;
        }

        if ( endPoints > 1 )
        {
            final int factor = Integer.parseInt( domainConfig.readAppProperty( AppProperty.LDAP_SEARCH_PARALLEL_FACTOR ) );
            final int maxThreads = Integer.parseInt( domainConfig.readAppProperty( AppProperty.LDAP_SEARCH_PARALLEL_THREAD_MAX ) );
            final int threads = Math.min( maxThreads, ( endPoints ) * factor );
            final int minThreads = JavaHelper.rangeCheck( 1, 10, endPoints );

            LOGGER.trace( getSessionLabel(), () -> "initialized with threads min=" + minThreads + " max=" + threads );

            return PwmScheduler.makeMultiThreadExecutor( threads, getPwmApplication(), getSessionLabel(), UserSearchEngine.class );
        }
        return null;
    }

    private static <K, V> Map<K, V> trimOrderedMap( final Map<K, V> inputMap, final int maxEntries )
    {
        final Map<K, V> returnMap = new LinkedHashMap<>( inputMap );
        if ( returnMap.size() > maxEntries )
        {
            int counter = 0;
            for ( final Iterator<K> iterator = returnMap.keySet().iterator(); iterator.hasNext(); )
            {
                iterator.next();
                counter++;
                if ( counter > maxEntries )
                {
                    iterator.remove();
                }
            }
        }
        return Collections.unmodifiableMap( returnMap );
    }

    private static String figureSearchFilterForParams(
            final Map<FormConfiguration, String> formValues,
            final String searchFilter,
            final boolean enableValueEscaping
    )
    {
        String newSearchFilter = searchFilter;

        for ( final Map.Entry<FormConfiguration, String> entry : formValues.entrySet() )
        {
            final FormConfiguration formItem = entry.getKey();
            final String attrName = "%" + formItem.getName() + "%";
            String value = entry.getValue();

            if ( enableValueEscaping )
            {
                value = StringUtil.escapeLdapFilter( value );
            }

            if ( !formItem.isRequired() )
            {
                if ( StringUtil.isEmpty( value ) )
                {
                    value = "*";
                }
            }

            newSearchFilter = newSearchFilter.replace( attrName, value );
        }

        return newSearchFilter;
    }
}
