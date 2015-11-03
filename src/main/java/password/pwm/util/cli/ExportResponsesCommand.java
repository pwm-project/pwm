/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.util.cli;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ResponseSet;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.util.Helper;
import password.pwm.util.JsonUtil;
import password.pwm.util.TimeDuration;
import password.pwm.ws.server.rest.RestChallengesServer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.Map;

public class ExportResponsesCommand extends AbstractCliCommand {

    @Override
    void doCommand()
            throws Exception
    {
        final PwmApplication pwmApplication = cliEnvironment.getPwmApplication();

        final File outputFile = (File)cliEnvironment.getOptions().get(CliParameters.REQUIRED_NEW_OUTPUT_FILE.getName());
        Helper.pause(2000);

        final long startTime = System.currentTimeMillis();
        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication, SessionLabel.SYSTEM_LABEL);
        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        searchConfiguration.setEnableValueEscaping(false);
        searchConfiguration.setUsername("*");

        final String systemRecordDelimiter = System.getProperty("line.separator");
        final Writer writer = new BufferedWriter(new PrintWriter(outputFile, PwmConstants.DEFAULT_CHARSET.toString()));
        final Map<UserIdentity,Map<String,String>> results = userSearchEngine.performMultiUserSearch(searchConfiguration, Integer.MAX_VALUE, Collections.<String>emptyList());
        out("searching " + results.size() + " users for stored responses to write to " + outputFile.getAbsolutePath() + "....");
        int counter = 0;
        for (final UserIdentity identity : results.keySet()) {
            final ChaiUser user = pwmApplication.getProxiedChaiUser(identity);
            final ResponseSet responseSet = pwmApplication.getCrService().readUserResponseSet(null, identity, user);
            if (responseSet != null) {
                counter++;
                out("found responses for '" + user + "', writing to output.");
                final RestChallengesServer.JsonChallengesData outputData = new RestChallengesServer.JsonChallengesData();
                outputData.challenges = responseSet.asChallengeBeans(true);
                outputData.helpdeskChallenges = responseSet.asHelpdeskChallengeBeans(true);
                outputData.minimumRandoms = responseSet.getChallengeSet().minimumResponses();
                outputData.username = identity.toDelimitedKey();
                writer.write(JsonUtil.serialize(outputData));
                writer.write(systemRecordDelimiter);
            } else {
                out("skipping '" + user.toString() + "', no stored responses.");
            }
        }
        writer.close();
        out("output complete, " + counter + " responses exported in " + TimeDuration.fromCurrent(startTime).asCompactString());
    }

    @Override
    public CliParameters getCliParameters()
    {
        CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ExportResponses";
        cliParameters.description = "Export all saved responses";
        cliParameters.options = Collections.singletonList(CliParameters.REQUIRED_NEW_OUTPUT_FILE);

        cliParameters.needsPwmApplication = true;
        cliParameters.readOnly = true;

        return cliParameters;
    }
}
