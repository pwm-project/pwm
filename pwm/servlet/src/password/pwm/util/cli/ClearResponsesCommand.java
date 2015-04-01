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

import password.pwm.util.localdb.LocalDB;

public class ClearResponsesCommand extends AbstractCliCommand {

    @Override
    void doCommand()
            throws Exception
    {
        final String msg = "Proceeding with this operation will clear all stored responses from the LocalDB." + "\n"
                + "Please consider exporting the responses before proceeding. " + "\n"
                + "\n"
                + "The application must be stopped for this operation to succeed." + "\n";
        if (!promptForContinue(msg)) {
            return;
        }

        final LocalDB localDB = cliEnvironment.getLocalDB();

        if (localDB.size(LocalDB.DB.RESPONSE_STORAGE) == 0) {
            out("The LocalDB response database is already empty");
            return;
        }

        out("clearing " + localDB.size(LocalDB.DB.RESPONSE_STORAGE) + " responses");
        localDB.truncate(LocalDB.DB.RESPONSE_STORAGE);
        out("all saved responses are now removed from LocalDB");
    }

    @Override
    public CliParameters getCliParameters()
    {
        CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ClearLocalResponses";
        cliParameters.description = "Clear all responses from the LocalDB";

        cliParameters.needsLocalDB= true;
        cliParameters.readOnly = false;

        return cliParameters;
    }
}

