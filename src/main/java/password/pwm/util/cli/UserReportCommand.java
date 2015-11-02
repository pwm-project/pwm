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
import password.pwm.PwmConstants;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.svc.report.ReportService;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class UserReportCommand extends AbstractCliCommand {
    protected static final String OUTPUT_FILE_OPTIONNAME = "outputFile";

    @Override
    void doCommand()
            throws Exception
    {
        final File outputFile = (File)cliEnvironment.options.get(OUTPUT_FILE_OPTIONNAME);
        final OutputStream outputFileStream;
        try {
            outputFileStream = new BufferedOutputStream(new FileOutputStream(outputFile));
        } catch (Exception e) {
            out("unable to open file '" + outputFile.getAbsolutePath() + "' for writing");
            System.exit(-1);
            throw new Exception();
        }

        final PwmApplication pwmApplication = cliEnvironment.getPwmApplication();

        final ReportService userReport = pwmApplication.getReportService();
        if (userReport.status() != PwmService.STATUS.OPEN) {
            out("report service is not open or enabled");
            final List<HealthRecord> healthIssues = userReport.healthCheck();
            if (healthIssues != null) {
                for (final HealthRecord record : healthIssues) {
                    out("report health status: " + record.toDebugString(Locale.getDefault(), pwmApplication.getConfig()));
                }
            }
            return;
        }
        userReport.outputToCsv(outputFileStream, true, PwmConstants.DEFAULT_LOCALE);

        try { outputFileStream.close(); } catch (Exception e) { /* nothing */ }
        out("report output complete.");
    }

    @Override
    public CliParameters getCliParameters()
    {
        final CliParameters.Option outputFileOption = new CliParameters.Option() {
            public boolean isOptional()
            {
                return false;
            }

            public type getType()
            {
                return type.NEW_FILE;
            }

            public String getName()
            {
                return OUTPUT_FILE_OPTIONNAME;
            }
        };


        CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ExportUserReportDetail";
        cliParameters.description = "Output user report details to the output file (csv format)";
        cliParameters.options = Collections.singletonList(outputFileOption);

        cliParameters.needsPwmApplication = true;
        cliParameters.readOnly = false;

        return cliParameters;
    }

}
