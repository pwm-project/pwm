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

import password.pwm.PwmConstants;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBStoredQueue;
import password.pwm.util.logging.PwmLogEvent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.Iterator;

public class ExportLogsCommand extends AbstractCliCommand
{

    @Override
    void doCommand( )
            throws Exception
    {
        final LocalDB localDB = this.cliEnvironment.getLocalDB();
        final LocalDBStoredQueue logQueue = LocalDBStoredQueue.createLocalDBStoredQueue( null, localDB,
                LocalDB.DB.EVENTLOG_EVENTS );

        if ( logQueue.isEmpty() )
        {
            out( "no logs present" );
            return;
        }

        final File outputFile = ( File ) cliEnvironment.getOptions().get( CliParameters.REQUIRED_NEW_OUTPUT_FILE.getName() );
        out( "outputting " + logQueue.size() + " log events to " + outputFile.getAbsolutePath() + "...." );

        try ( Writer outputWriter = new OutputStreamWriter( new FileOutputStream( outputFile ), PwmConstants.DEFAULT_CHARSET ) )
        {
            for ( final Iterator<String> iter = logQueue.descendingIterator(); iter.hasNext(); )
            {
                final String loopString = iter.next();
                final PwmLogEvent logEvent = PwmLogEvent.fromEncodedString( loopString );
                if ( logEvent != null )
                {
                    outputWriter.write( logEvent.toLogString() );
                    outputWriter.write( "\n" );
                }
            }
        }

        out( "output complete" );

    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ExportLogs";
        cliParameters.description = "Export all logs in the LocalDB";
        cliParameters.options = Collections.singletonList( CliParameters.REQUIRED_NEW_OUTPUT_FILE );

        cliParameters.needsLocalDB = true;
        cliParameters.readOnly = true;

        return cliParameters;
    }
}
