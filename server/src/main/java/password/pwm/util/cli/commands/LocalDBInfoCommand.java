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

import password.pwm.util.cli.CliParameters;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.PwmNumberFormat;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBUtility;

import java.time.Instant;
import java.util.Map;

public class LocalDBInfoCommand extends AbstractCliCommand
{
    public void doCommand( ) throws Exception
    {
        final Instant startTime = Instant.now();
        final LocalDB localDB = cliEnvironment.getLocalDB();
        final long localDBdiskSpace = FileSystemUtility.getFileDirectorySize( localDB.getFileLocation() );
        out( "beginning LocalDBInfo" );
        out( "LocalDB total disk space = " + PwmNumberFormat.forDefaultLocale().format( localDBdiskSpace ) + " (" + StringUtil.formatDiskSize( localDBdiskSpace ) + ")" );
        out( "examining LocalDB, this may take a while.... " );
        for ( final LocalDB.DB db : LocalDB.DB.values() )
        {
            out( "---" + db.toString() + "---" );
            final Map<LocalDBUtility.StatsKey, Object> stats = LocalDBUtility.dbStats( localDB, db );
            out( JsonUtil.serializeMap( stats, JsonUtil.Flag.PrettyPrint ) );
        }
        out( "completed LocalDBInfo in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "LocalDBInfo";
        cliParameters.description = "Report information about the LocalDB";
        cliParameters.needsLocalDB = true;
        cliParameters.readOnly = true;

        return cliParameters;
    }
}
