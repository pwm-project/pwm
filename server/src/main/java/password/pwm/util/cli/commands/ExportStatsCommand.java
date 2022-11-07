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

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.svc.stats.StatisticsService;
import password.pwm.svc.stats.StatisticsUtils;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.java.PwmTimeUtil;
import password.pwm.util.java.TimeDuration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;

public class ExportStatsCommand extends AbstractCliCommand
{

    @Override
    void doCommand( )
            throws IOException
    {
        final PwmApplication pwmApplication = cliEnvironment.getPwmApplication();
        final StatisticsService statsManger = pwmApplication.getStatisticsService();

        final File outputFile = ( File ) cliEnvironment.getOptions().get( CliParameters.REQUIRED_NEW_OUTPUT_FILE.getName() );
        final long startTime = System.currentTimeMillis();
        out( "beginning output to " + outputFile.getAbsolutePath() );
        final int counter;
        try ( FileOutputStream fileOutputStream = new FileOutputStream( outputFile, true ) )
        {
            counter = StatisticsUtils.outputStatsToCsv(
                    SessionLabel.CLI_SESSION_LABEL,
                    statsManger,
                    fileOutputStream,
                    PwmConstants.DEFAULT_LOCALE,
                    StatisticsUtils.CsvOutputFlag.includeHeader );
        }
        out( "completed writing " + counter + " rows of stats output in " + PwmTimeUtil.asLongString( TimeDuration.fromCurrent( startTime ) ) );
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
