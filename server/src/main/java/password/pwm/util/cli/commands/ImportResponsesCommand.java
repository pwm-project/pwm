/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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
                    catch ( Exception e )
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
