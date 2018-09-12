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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.svc.report.ReportCsvUtility;
import password.pwm.svc.report.ReportService;
import password.pwm.util.cli.CliParameters;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class UserReportCommand extends AbstractCliCommand
{
    private static final String OUTPUT_FILE_OPTIONNAME = "outputFile";

    @Override
    @SuppressFBWarnings( "DM_EXIT" )
    void doCommand( )
            throws Exception
    {
        final File outputFile = ( File ) cliEnvironment.getOptions().get( OUTPUT_FILE_OPTIONNAME );

        try ( OutputStream outputFileStream = new BufferedOutputStream( new FileOutputStream( outputFile ) ) )
        {

            final PwmApplication pwmApplication = cliEnvironment.getPwmApplication();

            final ReportService userReport = pwmApplication.getReportService();
            if ( userReport.status() != PwmService.STATUS.OPEN )
            {
                out( "report service is not open or enabled" );
                final List<HealthRecord> healthIssues = userReport.healthCheck();
                if ( healthIssues != null )
                {
                    for ( final HealthRecord record : healthIssues )
                    {
                        out( "report health status: " + record.toDebugString( Locale.getDefault(), pwmApplication.getConfig() ) );
                    }
                }
                return;
            }

            final ReportCsvUtility reportCsvUtility = new ReportCsvUtility( pwmApplication );
            reportCsvUtility.outputToCsv( outputFileStream, true, PwmConstants.DEFAULT_LOCALE );
        }
        catch ( IOException e )
        {
            out( "unable to open file '" + outputFile.getAbsolutePath() + "' for writing" );
            System.exit( -1 );
            throw new Exception();
        }

        out( "report output complete." );
    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters.Option outputFileOption = new CliParameters.Option()
        {
            public boolean isOptional( )
            {
                return false;
            }

            public Type getType( )
            {
                return Type.NEW_FILE;
            }

            public String getName( )
            {
                return OUTPUT_FILE_OPTIONNAME;
            }
        };


        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ExportUserReportDetail";
        cliParameters.description = "Output user report details to the output file (csv format)";
        cliParameters.options = Collections.singletonList( outputFileOption );

        cliParameters.needsPwmApplication = true;
        cliParameters.readOnly = false;

        return cliParameters;
    }

}
