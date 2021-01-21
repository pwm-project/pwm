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

import password.pwm.PwmApplication;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.java.TimeDuration;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.Locale;

public class ExportStatsCommand extends AbstractCliCommand
{

    @Override
    void doCommand( )
            throws Exception
    {
        final PwmApplication pwmApplication = cliEnvironment.getPwmApplication();
        final StatisticsManager statsManger = pwmApplication.getStatisticsManager();

        final File outputFile = ( File ) cliEnvironment.getOptions().get( CliParameters.REQUIRED_NEW_OUTPUT_FILE.getName() );
        final long startTime = System.currentTimeMillis();
        out( "beginning output to " + outputFile.getAbsolutePath() );
        final int counter;
        try ( FileOutputStream fileOutputStream = new FileOutputStream( outputFile, true ) )
        {
            counter = statsManger.outputStatsToCsv( fileOutputStream, Locale.getDefault(), true );
            fileOutputStream.close();
        }
        out( "completed writing " + counter + " rows of stats output in " + TimeDuration.fromCurrent( startTime ).asLongString() );
    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ExportStats";
        cliParameters.description = "Dump all statistics in the LocalDB to a csv file";
        cliParameters.options = Collections.singletonList( CliParameters.REQUIRED_NEW_OUTPUT_FILE );

        cliParameters.needsPwmApplication = true;
        cliParameters.readOnly = true;

        return cliParameters;
    }
}
