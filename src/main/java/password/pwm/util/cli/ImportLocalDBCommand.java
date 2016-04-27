/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

import password.pwm.config.Configuration;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBUtility;

import java.io.File;
import java.util.Collections;

public class ImportLocalDBCommand extends AbstractCliCommand {
    @Override
    void doCommand()
            throws Exception
    {
        final Configuration config = cliEnvironment.getConfig();
        final LocalDB localDB = cliEnvironment.getLocalDB();

        final String msg = "Proceeding with this operation will clear ALL data from the LocalDB." + "\n"
                + "Please consider backing up the LocalDB before proceeding. " + "\n"
                + "\n"
                + "The application must be stopped for this operation to succeed.";
        if (!promptForContinue(msg)) {
            out("exiting...");
            return;
        }

        final LocalDBUtility pwmDBUtility = new LocalDBUtility(localDB);
        final File inputFile = (File)cliEnvironment.getOptions().get(CliParameters.REQUIRED_EXISTING_INPUT_FILE.getName());
        try {
            pwmDBUtility.importLocalDB(inputFile, System.out);
        } catch (PwmOperationalException e) {
            out("error during import: " + e.getMessage());
        }
    }

    @Override
    public CliParameters getCliParameters()
    {
        CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ImportLocalDB";
        cliParameters.description = "Import the entire LocalDB contents from a backup file";
        cliParameters.options = Collections.singletonList(CliParameters.REQUIRED_EXISTING_INPUT_FILE);

        cliParameters.needsLocalDB = true;
        cliParameters.readOnly = false;

        return cliParameters;
    }
}
