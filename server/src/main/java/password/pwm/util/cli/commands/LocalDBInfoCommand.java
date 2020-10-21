/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    @Override
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
