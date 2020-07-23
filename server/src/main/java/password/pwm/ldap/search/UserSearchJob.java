/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.PwmService;
import password.pwm.svc.stats.AvgStatistic;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogLevel;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

class UserSearchJob implements Callable<Map<UserIdentity, Map<String, String>>>
{
    private final PwmApplication pwmApplication;
    private final UserSearchJobParameters userSearchJobParameters;
    private final UserSearchEngine userSearchEngine;
    private final FutureTask<Map<UserIdentity, Map<String, String>>> futureTask;
    private final Instant createTime = Instant.now();

    UserSearchJob( final PwmApplication pwmApplication, final UserSearchEngine userSearchEngine, final UserSearchJobParameters userSearchJobParameters )
    {
        this.pwmApplication = pwmApplication;
        this.userSearchJobParameters = userSearchJobParameters;
        this.userSearchEngine = userSearchEngine;
        this.futureTask = new FutureTask<>( this );
    }

    @Override
    public Map<UserIdentity, Map<String, String>> call()
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final TimeDuration queueLagDuration = TimeDuration.fromCurrent( createTime );

        final SearchHelper searchHelper = new SearchHelper();
        searchHelper.setMaxResults( userSearchJobParameters.getMaxResults() );
        searchHelper.setFilter( userSearchJobParameters.getSearchFilter() );
        searchHelper.setAttributes( userSearchJobParameters.getReturnAttributes() );
        searchHelper.setTimeLimit( ( int ) userSearchJobParameters.getTimeoutMs() );

        final String debugInfo;
        {
            final Map<String, String> props = new LinkedHashMap<>();
            props.put( "profile", userSearchJobParameters.getLdapProfile().getIdentifier() );
            props.put( "base", userSearchJobParameters.getContext() );
            props.put( "maxCount", String.valueOf( searchHelper.getMaxResults() ) );
            props.put( "queueLag", queueLagDuration.asCompactString() );
            debugInfo = "[" + StringUtil.mapToString( props ) + "]";
        }

        userSearchEngine.log( PwmLogLevel.TRACE, userSearchJobParameters.getSessionLabel(), userSearchJobParameters.getSearchID(), userSearchJobParameters.getJobId(),
                "performing ldap search for user, thread=" + Thread.currentThread().getId()
                        + ", timeout=" + userSearchJobParameters.getTimeoutMs() + "ms, "
                        + debugInfo );

        final Instant startTime = Instant.now();
        final Map<String, Map<String, String>> results;
        try
        {
            results = userSearchJobParameters.getChaiProvider().search( userSearchJobParameters.getContext(), searchHelper );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage() ) );
        }
        catch ( final ChaiOperationException e )
        {
            throw new PwmOperationalException( PwmError.forChaiError( e.getErrorCode() ), "ldap error during searchID="
                    + userSearchJobParameters.getSearchID() + ", context=" + userSearchJobParameters.getContext() + ", error=" + e.getMessage() );
        }

        final TimeDuration searchDuration = TimeDuration.fromCurrent( startTime );

        if ( pwmApplication.getStatisticsManager() != null && pwmApplication.getStatisticsManager().status() == PwmService.STATUS.OPEN )
        {
            pwmApplication.getStatisticsManager().updateAverageValue( AvgStatistic.AVG_LDAP_SEARCH_TIME, searchDuration.asMillis() );
        }

        if ( results.isEmpty() )
        {
            userSearchEngine.log( PwmLogLevel.TRACE, userSearchJobParameters.getSessionLabel(), userSearchJobParameters.getSearchID(), userSearchJobParameters.getJobId(),
                    "no matches from search (" + searchDuration.asCompactString() + "); " + debugInfo );
            return Collections.emptyMap();
        }

        userSearchEngine.log( PwmLogLevel.TRACE, userSearchJobParameters.getSessionLabel(), userSearchJobParameters.getSearchID(), userSearchJobParameters.getJobId(),
                "found " + results.size() + " results in " + searchDuration.asCompactString() + "; " + debugInfo );

        final Map<UserIdentity, Map<String, String>> returnMap = new LinkedHashMap<>();
        for ( final Map.Entry<String, Map<String, String>> entry : results.entrySet() )
        {
            final String userDN = entry.getKey();
            final Map<String, String> attributeMap = entry.getValue();
            final UserIdentity userIdentity = new UserIdentity( userDN, userSearchJobParameters.getLdapProfile().getIdentifier() );
            returnMap.put( userIdentity, attributeMap );
        }
        return returnMap;
    }

    public UserSearchJobParameters getUserSearchJobParameters()
    {
        return userSearchJobParameters;
    }

    public FutureTask<Map<UserIdentity, Map<String, String>>> getFutureTask()
    {
        return futureTask;
    }
}
