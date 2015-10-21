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

import password.pwm.PwmApplication;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.Helper;
import password.pwm.util.TimeDuration;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.Locale;

public class ExportStatsCommand extends AbstractCliCommand {

    @Override
    void doCommand()
            throws Exception
    {
        final PwmApplication pwmApplication = cliEnvironment.getPwmApplication();
        StatisticsManager statsManger = pwmApplication.getStatisticsManager();
        Helper.pause(1000);

        final File outputFile = (File)cliEnvironment.getOptions().get(CliParameters.REQUIRED_NEW_OUTPUT_FILE.getName());
        final long startTime = System.currentTimeMillis();
        out("beginning output to " + outputFile.getAbsolutePath());
        final FileOutputStream fileOutputStream = new FileOutputStream(outputFile,true);
        final int counter = statsManger.outputStatsToCsv(fileOutputStream, Locale.getDefault(),false);
        fileOutputStream.close();
        out("completed writing " + counter + " rows of stats output in " + TimeDuration.fromCurrent(startTime).asLongString());
    }

    @Override
    public CliParameters getCliParameters()
    {
        CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ExportStats";
        cliParameters.description = "Dump all statistics in the LocalDB to a csv file";
        cliParameters.options = Collections.singletonList(CliParameters.REQUIRED_NEW_OUTPUT_FILE);

        cliParameters.needsPwmApplication = true;
        cliParameters.readOnly = true;

        return cliParameters;
    }
}
