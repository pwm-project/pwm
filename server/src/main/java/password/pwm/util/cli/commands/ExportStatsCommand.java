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

import password.pwm.PwmApplication;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.java.JavaHelper;
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
        JavaHelper.pause( 1000 );

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
