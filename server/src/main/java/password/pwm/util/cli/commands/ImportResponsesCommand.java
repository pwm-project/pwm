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

package password.pwm.util.cli.commands;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChallengeSet;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.profile.ChallengeProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.ws.server.rest.RestChallengesServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Collections;

public class ImportResponsesCommand extends AbstractCliCommand
{
    @Override
    void doCommand( )
            throws Exception
    {
        final PwmApplication pwmApplication = cliEnvironment.getPwmApplication();

        final File inputFile = ( File ) cliEnvironment.getOptions().get( CliParameters.REQUIRED_EXISTING_INPUT_FILE.getName() );
        try ( BufferedReader reader = new BufferedReader( new InputStreamReader( new FileInputStream( inputFile ), PwmConstants.DEFAULT_CHARSET.toString() ) ) )
        {
            out( "importing stored responses from " + inputFile.getAbsolutePath() + "...." );

            int counter = 0;
            String line;
            final long startTime = System.currentTimeMillis();
            while ( ( line = reader.readLine() ) != null )
            {
                counter++;
                final RestChallengesServer.JsonChallengesData inputData;
                inputData = JsonUtil.deserialize( line, RestChallengesServer.JsonChallengesData.class );

                final UserIdentity userIdentity = UserIdentity.fromDelimitedKey( inputData.username );
                final ChaiUser user = pwmApplication.getProxiedChaiUser( userIdentity );
                if ( user.exists() )
                {
                    out( "writing responses to user '" + user.getEntryDN() + "'" );
                    try
                    {
                        final ChallengeProfile challengeProfile = pwmApplication.getCrService().readUserChallengeProfile(
                                null, userIdentity, user, PwmPasswordPolicy.defaultPolicy(), PwmConstants.DEFAULT_LOCALE );
                        final ChallengeSet challengeSet = challengeProfile.getChallengeSet();
                        final String userGuid = LdapOperationsHelper.readLdapGuidValue( pwmApplication, null, userIdentity, false );
                        final ResponseInfoBean responseInfoBean = inputData.toResponseInfoBean( PwmConstants.DEFAULT_LOCALE, challengeSet.getIdentifier() );
                        pwmApplication.getCrService().writeResponses( userIdentity, user, userGuid, responseInfoBean );
                    }
                    catch ( final Exception e )
                    {
                        out( "error writing responses to user '" + user.getEntryDN() + "', error: " + e.getMessage() );
                        return;
                    }
                }
                else
                {
                    out( "user '" + user.getEntryDN() + "' is not a valid userDN" );
                    return;
                }
            }

            out( "output complete, " + counter + " responses imported in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        }
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
