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

import java.io.File;

public class ConfigDeleteCommand extends AbstractCliCommand {
    public void doCommand()
            throws Exception
    {
        final File configurationFile = cliEnvironment.configurationFile;
        if (configurationFile == null || !configurationFile.exists()) {
            out("configuration file is not present");
            return;
        }


        if (!promptForContinue("Proceeding will delete the existing configuration")) {
            return;
        }

        if (configurationFile.delete()) {
            out("success");
        } else {
            out("unable to delete file");
        }
    }

    @Override
    public CliParameters getCliParameters()
    {
        CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ConfigDelete";
        cliParameters.description = "Delete configuration.";
        cliParameters.needsPwmApplication = false;
        cliParameters.readOnly = true;
        return cliParameters;
    }

}
