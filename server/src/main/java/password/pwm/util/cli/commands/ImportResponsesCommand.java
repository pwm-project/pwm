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
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.profile.ChallengeProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.cli.CliException;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.ws.server.rest.RestChallengesServer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;

public class ImportResponsesCommand extends AbstractCliCommand
{
    @Override
    void doCommand( )
            throws IOException, CliException
    {
        final PwmApplication pwmApplication = cliEnvironment.getPwmApplication();

        final File inputFile = ( File ) cliEnvironment.getOptions().get( CliParameters.REQUIRED_EXISTING_INPUT_FILE.getName() );

        try
        {
            doImport( pwmApplication, inputFile );
        }
        catch ( final PwmUnrecoverableException | ChaiUnavailableException e )
        {
            throw new CliException( "error during response import command: " + e.getMessage(),  e );
        }
    }

    private void doImport( final PwmApplication pwmApplication,  final File inputFile )
            throws IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final Iterator<String> lineIterator = Files.lines( inputFile.toPath() ).iterator();

        out( "importing stored responses from " + inputFile.getAbsolutePath() + "...." );

        int counter = 0;
        final Instant startTime = Instant.now();

        for ( final String line = lineIterator.next(); lineIterator.hasNext(); )
        {
            counter++;
            processInputLine( pwmApplication, line );
        }

        out( "output complete, " + counter + " responses imported in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
    }

    private void processInputLine( final PwmApplication pwmApplication, final String line )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException
    {
        final RestChallengesServer.JsonChallengesData inputData;
        inputData = JsonFactory.get().deserialize( line, RestChallengesServer.JsonChallengesData.class );

        final UserIdentity userIdentity = UserIdentity.fromDelimitedKey( SessionLabel.CLI_SESSION_LABEL, inputData.username );
        final PwmDomain pwmDomain = figureDomain( userIdentity, pwmApplication );
        final ChaiUser user = pwmDomain.getProxiedChaiUser( SessionLabel.CLI_SESSION_LABEL, userIdentity );
        if ( user.exists() )
        {
            out( "writing responses to user '" + user.getEntryDN() + "'" );
            try
            {
                final ChallengeProfile challengeProfile = pwmDomain.getCrService().readUserChallengeProfile(
                        null, userIdentity, user, PwmPasswordPolicy.defaultPolicy(), PwmConstants.DEFAULT_LOCALE );
                final ChallengeSet challengeSet = challengeProfile.getChallengeSet()
                        .orElseThrow( () -> new PwmUnrecoverableException( PwmError.ERROR_NO_CHALLENGES.toInfo() ) );
                final ResponseInfoBean responseInfoBean = inputData.toResponseInfoBean( PwmConstants.DEFAULT_LOCALE, challengeSet.getIdentifier() );
                pwmDomain.getCrService().writeResponses( SessionLabel.CLI_SESSION_LABEL, userIdentity, user, responseInfoBean );
            }
            catch ( final Exception e )
            {
                out( "error writing responses to user '" + user.getEntryDN() + "', error: " + e.getMessage() );
            }
        }
        else
        {
            out( "user '" + user.getEntryDN() + "' is not a valid userDN" );
        }
    }

    private static PwmDomain figureDomain( final UserIdentity userIdentity, final PwmApplication pwmApplication )
    {
        if ( pwmApplication.isMultiDomain() )
        {
            final DomainID domainID = userIdentity.getDomainID();
            if ( domainID == null )
            {
                throw new IllegalArgumentException( "user '" + userIdentity + " does not have a domain specified" );
            }
            final PwmDomain pwmDomain = pwmApplication.domains().get( domainID );
            if ( pwmDomain == null )
            {
                throw new IllegalArgumentException( "user '" + userIdentity + " has an invalid domain specified" );
            }
            return pwmDomain;
        }
        return pwmApplication.domains().values().iterator().next();
    }

    @Override
    public CliParameters getCliParameters( )
    {

        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ImportResponses";
        cliParameters.description = "Import responses from file";
        cliParameters.options = Collections.singletonList( CliParameters.REQUIRED_EXISTING_INPUT_FILE );

        cliParameters.needsPwmApplication = true;
        cliParameters.readOnly = false;

        return cliParameters;
    }
}
