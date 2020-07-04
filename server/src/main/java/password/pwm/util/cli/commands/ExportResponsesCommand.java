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
import java.util.Collections;
import java.util.Map;

public class ExportResponsesCommand extends AbstractCliCommand
{

    @Override
    void doCommand( )
            throws Exception
    {
        final PwmApplication pwmApplication = cliEnvironment.getPwmApplication();

        final File outputFile = ( File ) cliEnvironment.getOptions().get( CliParameters.REQUIRED_NEW_OUTPUT_FILE.getName() );

        final long startTime = System.currentTimeMillis();
        final UserSearchEngine userSearchEngine = pwmApplication.getUserSearchEngine();
        final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                .enableValueEscaping( false )
                .username( "*" )
                .build();

        final String systemRecordDelimiter = System.getProperty( "line.separator" );
        final Writer writer = new BufferedWriter( new PrintWriter( outputFile, PwmConstants.DEFAULT_CHARSET.toString() ) );
        final Map<UserIdentity, Map<String, String>> results = userSearchEngine.performMultiUserSearch(
                searchConfiguration,
                Integer.MAX_VALUE,
                Collections.emptyList(),
                SessionLabel.SYSTEM_LABEL
        );
        out( "searching " + results.size() + " users for stored responses to write to " + outputFile.getAbsolutePath() + "...." );
        int counter = 0;
        for ( final UserIdentity identity : results.keySet() )
        {
            final ChaiUser user = pwmApplication.getProxiedChaiUser( identity );
            final ResponseSet responseSet = pwmApplication.getCrService().readUserResponseSet( null, identity, user );
            if ( responseSet != null )
            {
                counter++;
                out( "found responses for '" + user + "', writing to output." );
                final RestChallengesServer.JsonChallengesData outputData = new RestChallengesServer.JsonChallengesData();
                outputData.challenges = responseSet.asChallengeBeans( true );
                outputData.helpdeskChallenges = responseSet.asHelpdeskChallengeBeans( true );
                outputData.minimumRandoms = responseSet.getChallengeSet().minimumResponses();
                outputData.username = identity.toDelimitedKey();
                writer.write( JsonUtil.serialize( outputData ) );
                writer.write( systemRecordDelimiter );
            }
            else
            {
                out( "skipping '" + user.toString() + "', no stored responses." );
            }
        }
        writer.close();
        out( "output complete, " + counter + " responses exported in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
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
