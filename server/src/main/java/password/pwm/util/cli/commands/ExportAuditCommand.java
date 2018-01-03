/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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
import password.pwm.PwmConstants;
import password.pwm.svc.event.AuditService;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Instant;
import java.util.Collections;

public class ExportAuditCommand extends AbstractCliCommand
{
    @Override
    void doCommand( )
            throws Exception
    {
        final PwmApplication pwmApplication = cliEnvironment.getPwmApplication();
        final AuditService auditManager = new AuditService();
        auditManager.init( pwmApplication );
        JavaHelper.pause( 1000 );

        final File outputFile = ( File ) cliEnvironment.getOptions().get( CliParameters.REQUIRED_NEW_OUTPUT_FILE.getName() );

        final Instant startTime = Instant.now();
        out( "beginning output to " + outputFile.getAbsolutePath() );
        final int counter;
        try ( FileOutputStream fileOutputStream = new FileOutputStream( outputFile, true ) )
        {
            counter = auditManager.outputVaultToCsv( fileOutputStream, PwmConstants.DEFAULT_LOCALE, false );
            fileOutputStream.close();
        }
        out( "completed writing " + counter + " rows of audit output in " + TimeDuration.fromCurrent( startTime ).asLongString() );
    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ExportAudit";
        cliParameters.description = "Dump all audit records in the LocalDB to a csv file";
        cliParameters.options = Collections.singletonList( CliParameters.REQUIRED_NEW_OUTPUT_FILE );

        cliParameters.needsPwmApplication = true;
        cliParameters.readOnly = false;

        return cliParameters;
    }
}
