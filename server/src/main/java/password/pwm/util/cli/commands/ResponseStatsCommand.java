/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.util.cli.commands;

import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.operations.CrService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

public class ResponseStatsCommand extends AbstractCliCommand
{

    @Override
    void doCommand( )
            throws Exception
    {
        final PwmApplication pwmApplication = cliEnvironment.getPwmApplication();
        out( "searching for users" );
        final List<UserIdentity> userIdentities = readAllUsersFromLdap( pwmApplication );
        out( "found " + userIdentities.size() + " users, reading...." );

        final ResponseStats responseStats = makeStatistics( pwmApplication, userIdentities );

        final File outputFile = ( File ) cliEnvironment.getOptions().get( CliParameters.REQUIRED_NEW_OUTPUT_FILE.getName() );
        final long startTime = System.currentTimeMillis();
        out( "beginning output to " + outputFile.getAbsolutePath() );
        try ( FileOutputStream fileOutputStream = new FileOutputStream( outputFile, true ) )
        {
            fileOutputStream.write( JsonUtil.serialize( responseStats, JsonUtil.Flag.PrettyPrint ).getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
        out( "completed writing stats output in " + TimeDuration.fromCurrent( startTime ).asLongString() );
    }

    static class ResponseStats implements Serializable
    {
        private final Map<String, Integer> challengeTextOccurrence = new TreeMap<>();
        private final Map<String, Integer> helpdeskChallengeTextOccurrence = new TreeMap<>();
    }

    static int userCounter = 0;

    ResponseStats makeStatistics(
            final PwmApplication pwmApplication,
            final List<UserIdentity> userIdentities
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final ResponseStats responseStats = new ResponseStats();
        final Timer timer = new Timer();
        timer.scheduleAtFixedRate( new TimerTask()
        {
            @Override
            public void run( )
            {
                out( "processing...  " + userCounter + " users read" );
            }
        }, 30 * 1000, 30 * 1000 );
        final CrService crService = pwmApplication.getCrService();
        for ( final UserIdentity userIdentity : userIdentities )
        {
            userCounter++;
            final ResponseInfoBean responseInfoBean = crService.readUserResponseInfo( null, userIdentity, pwmApplication.getProxiedChaiUser( userIdentity ) );
            makeStatistics( responseStats, responseInfoBean );
        }
        timer.cancel();
        return responseStats;
    }

    static void makeStatistics( final ResponseStats responseStats, final ResponseInfoBean responseInfoBean )
    {
        if ( responseInfoBean != null )
        {
            {
                final Map<Challenge, String> crMap = responseInfoBean.getCrMap();
                if ( crMap != null )
                {
                    for ( final Challenge challenge : crMap.keySet() )
                    {
                        final String challengeText = challenge.getChallengeText();
                        if ( challengeText != null && !challengeText.isEmpty() )
                        {
                            if ( !responseStats.challengeTextOccurrence.containsKey( challengeText ) )
                            {
                                responseStats.challengeTextOccurrence.put( challengeText, 0 );
                            }
                            responseStats.challengeTextOccurrence.put( challengeText,
                                    1 + responseStats.challengeTextOccurrence.get( challengeText ) );
                        }
                    }
                }
            }
            {
                final Map<Challenge, String> helpdeskCrMap = responseInfoBean.getHelpdeskCrMap();
                if ( helpdeskCrMap != null )
                {
                    for ( final Challenge challenge : helpdeskCrMap.keySet() )
                    {
                        final String challengeText = challenge.getChallengeText();
                        if ( challengeText != null && !challengeText.isEmpty() )
                        {
                            if ( !responseStats.helpdeskChallengeTextOccurrence.containsKey( challengeText ) )
                            {
                                responseStats.helpdeskChallengeTextOccurrence.put( challengeText, 0 );
                            }
                            responseStats.helpdeskChallengeTextOccurrence.put( challengeText,
                                    1 + responseStats.helpdeskChallengeTextOccurrence.get( challengeText ) );
                        }
                    }
                }
            }
        }

    }

    private static List<UserIdentity> readAllUsersFromLdap(
            final PwmApplication pwmApplication
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final List<UserIdentity> returnList = new ArrayList<>();

        for ( final LdapProfile ldapProfile : pwmApplication.getConfig().getLdapProfiles().values() )
        {
            final UserSearchEngine userSearchEngine = pwmApplication.getUserSearchEngine();
            final TimeDuration searchTimeout = TimeDuration.of(
                    Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.REPORTING_LDAP_SEARCH_TIMEOUT_MS ) ),
                    TimeDuration.Unit.MILLISECONDS );

            final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                    .enableValueEscaping( false )
                    .searchTimeout( searchTimeout )
                    .username( "*" )
                    .enableValueEscaping( false )
                    .filter( ldapProfile.readSettingAsString( PwmSetting.LDAP_USERNAME_SEARCH_FILTER ) )
                    .ldapProfile( ldapProfile.getIdentifier() )
                    .build();

            final Map<UserIdentity, Map<String, String>> searchResults = userSearchEngine.performMultiUserSearch(
                    searchConfiguration,
                    Integer.MAX_VALUE,
                    Collections.emptyList(),
                    SessionLabel.SYSTEM_LABEL
            );
            returnList.addAll( searchResults.keySet() );

        }

        return returnList;
    }


    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ResponseStats";
        cliParameters.description = "Various statistics about stored responses";
        cliParameters.options = Collections.singletonList( CliParameters.REQUIRED_NEW_OUTPUT_FILE );

        cliParameters.needsPwmApplication = true;
        cliParameters.readOnly = true;

        return cliParameters;
    }
}
