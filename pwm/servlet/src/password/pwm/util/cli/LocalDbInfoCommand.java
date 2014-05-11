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

import password.pwm.util.Helper;
import password.pwm.util.localdb.LocalDB;

public class LocalDbInfoCommand extends AbstractCliCommand {
    public void doCommand() throws Exception {
        final LocalDB localDB = cliEnvironment.getLocalDB();
        final long pwmDBdiskSpace = Helper.getFileDirectorySize(localDB.getFileLocation());
        out("LocalDB Total Disk Space = " + pwmDBdiskSpace + " (" + Helper.formatDiskSize(pwmDBdiskSpace) + ")");
        out("Checking row counts, this may take a moment.... ");
        for (final LocalDB.DB db : LocalDB.DB.values()) {
            out("  " + db.toString() + "=" + localDB.size(db));
        }
    }

    @Override
    public CliParameters getCliParameters()
    {
        CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "LocalDBInfo";
        cliParameters.description = "Report information about the LocalDB";
        cliParameters.needsLocalDB = true;

        return cliParameters;
    }
}
