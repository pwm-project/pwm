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

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ResponseSet;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.ws.server.rest.RestChallengesServer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.PrintWriter;
import java.io.Writer;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class ExportResponsesCommand extends AbstractCliCommand
{

    @Override
    void doCommand( )
            throws Exception
    {
        final PwmApplication pwmApplication = cliEnvironment.getPwmApplication();

        final Instant startTime = Instant.now();
        final File outputFile = ( File ) cliEnvironment.getOptions().get( CliParameters.REQUIRED_NEW_OUTPUT_FILE.getName() );

        long counter = 0;

        try ( Writer writer = new BufferedWriter( new PrintWriter( outputFile, PwmConstants.DEFAULT_CHARSET.toString() ) ); )
        {
            for ( final PwmDomain pwmDomain : pwmApplication.domains().values() )
            {
                counter += doExport( pwmDomain, writer );
            }
        }

        out( "output complete, " + counter + " responses exported " + TimeDuration.fromCurrent( startTime ).asCompactString() );
    }

    private long doExport( final PwmDomain pwmDomain, final Writer writer ) throws Exception
    {
        final String systemRecordDelimiter = System.getProperty( "line.separator" );

        final UserSearchEngine userSearchEngine = pwmDomain.getUserSearchEngine();
        final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                .searchTimeout( TimeDuration.MINUTE )
                .enableValueEscaping( false )
                .username( "*" )
                .build();

        final Map<UserIdentity, Map<String, String>> results = userSearchEngine.performMultiUserSearch(
                searchConfiguration,
                Integer.MAX_VALUE,
                Collections.emptyList(),
                SessionLabel.SYSTEM_LABEL
        );
        out( "searching " + results.size() + " users for stored responses...." );
        int counter = 0;
        for ( final UserIdentity identity : results.keySet() )
        {
            final ChaiUser user = pwmDomain.getProxiedChaiUser( identity );
            final Optional<ResponseSet> responseSet = pwmDomain.getCrService().readUserResponseSet( null, identity, user );
            if ( responseSet.isPresent() )
            {
                counter++;
                out( "found responses for '" + user + "', writing to output." );
                final RestChallengesServer.JsonChallengesData outputData = new RestChallengesServer.JsonChallengesData();
                outputData.challenges = responseSet.get().asChallengeBeans( true );
                outputData.helpdeskChallenges = responseSet.get().asHelpdeskChallengeBeans( true );
                outputData.minimumRandoms = responseSet.get().getChallengeSet().minimumResponses();
                outputData.username = identity.toDelimitedKey();
                writer.write( JsonUtil.serialize( outputData ) );
                writer.write( systemRecordDelimiter );
            }
            else
            {
                out( "skipping '" + user.toString() + "', no stored responses." );
            }
        }
        return counter;

    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ExportResponses";
        cliParameters.description = "Export all saved responses";
        cliParameters.options = Collections.singletonList( CliParameters.REQUIRED_NEW_OUTPUT_FILE );

        cliParameters.needsPwmApplication = true;
        cliParameters.readOnly = true;

        return cliParameters;
    }
}
