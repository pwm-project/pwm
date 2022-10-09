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

package password.pwm.util.cli.commands;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchService;
import password.pwm.util.cli.CliException;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.ws.server.rest.RestChallengesServer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
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
            throws IOException, CliException
    {
        final PwmApplication pwmApplication = cliEnvironment.getPwmApplication();

        final Instant startTime = Instant.now();
        final File outputFile = ( File ) cliEnvironment.getOptions().get( CliParameters.REQUIRED_NEW_OUTPUT_FILE.getName() );

        long counter = 0;

        try ( Writer writer = new BufferedWriter( new PrintWriter( outputFile, PwmConstants.DEFAULT_CHARSET.toString() ) ); )
        {
            for ( final PwmDomain pwmDomain : pwmApplication.domains().values() )
            {
                try
                {
                    counter += doExport( pwmDomain, writer );
                }
                catch ( final PwmUnrecoverableException | PwmOperationalException | ChaiValidationException e )
                {
                    throw new CliException( "error during export responses: " + e.getMessage(), e );
                }
            }
        }

        out( "output complete, " + counter + " responses exported " + TimeDuration.fromCurrent( startTime ).asCompactString() );
    }

    private long doExport(
            final PwmDomain pwmDomain,
            final Writer writer
    )
            throws PwmUnrecoverableException, PwmOperationalException, IOException, ChaiValidationException
    {
        final UserSearchService userSearchService = pwmDomain.getUserSearchEngine();
        final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                .searchTimeout( TimeDuration.MINUTE )
                .enableValueEscaping( false )
                .username( "*" )
                .build();

        final Map<UserIdentity, Map<String, String>> results = userSearchService.performMultiUserSearch(
                searchConfiguration,
                Integer.MAX_VALUE,
                Collections.emptyList(),
                SessionLabel.CLI_SESSION_LABEL
        );
        out( "searching " + results.size() + " users for stored responses...." );
        int counter = 0;
        for ( final UserIdentity identity : results.keySet() )
        {
            counter++;
            outputUser( pwmDomain, identity, writer );

        }
        return counter;
    }

    private void outputUser(
            final PwmDomain pwmDomain,
            final UserIdentity identity,
            final Writer writer
    )
            throws PwmUnrecoverableException, ChaiValidationException, IOException
    {
        final String systemRecordDelimiter = System.getProperty( "line.separator" );

        final ChaiUser user = pwmDomain.getProxiedChaiUser( SessionLabel.CLI_SESSION_LABEL, identity );
        final Optional<ResponseSet> responseSet = pwmDomain.getCrService().readUserResponseSet( null, identity, user );
        if ( responseSet.isPresent() )
        {
            out( "found responses for '" + user + "', writing to output." );
            final RestChallengesServer.JsonChallengesData outputData = new RestChallengesServer.JsonChallengesData();
            outputData.challenges = responseSet.get().asChallengeBeans( true );
            outputData.helpdeskChallenges = responseSet.get().asHelpdeskChallengeBeans( true );
            outputData.minimumRandoms = responseSet.get().getChallengeSet().minimumResponses();
            outputData.username = identity.toDelimitedKey();
            writer.write( JsonFactory.get().serialize( outputData ) );
            writer.write( systemRecordDelimiter );
        }
        else
        {
            out( "skipping '" + user.toString() + "', no stored responses." );
        }
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
