/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

import password.pwm.util.CodeIntegrityChecker;
import password.pwm.util.TimeDuration;

import java.io.File;
import java.io.FileWriter;
import java.util.Collections;

public class IntegrityReportCommand extends AbstractCliCommand {
    @Override
    void doCommand()
            throws Exception
    {
        final File outputFile = (File)cliEnvironment.getOptions().get(CliParameters.REQUIRED_NEW_FILE.getName());
        if (outputFile.exists()) {
            out("outputFile '" + outputFile.getAbsolutePath() + "' already exists");
            return;
        }

        final long startTime = System.currentTimeMillis();
        out("beginning output to " + outputFile.getAbsolutePath());
        final CodeIntegrityChecker codeIntegrityChecker = new CodeIntegrityChecker();
        final FileWriter writer = new FileWriter(outputFile,true);
        writer.write(codeIntegrityChecker.asPrettyJsonOutput());
        writer.close();
        out("completed operation in " + TimeDuration.fromCurrent(startTime).asLongString());

    }

    @Override
    public CliParameters getCliParameters()
    {
        CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "IntegrityReport";
        cliParameters.description = "Dump code integrity report (useful only for developers)";
        cliParameters.options = Collections.singletonList(CliParameters.REQUIRED_NEW_FILE);

        cliParameters.needsLocalDB = true;
        cliParameters.readOnly = true;

        return cliParameters;
    }
}
