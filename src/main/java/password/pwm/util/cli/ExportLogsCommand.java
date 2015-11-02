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
import password.pwm.util.localdb.LocalDBStoredQueue;
import password.pwm.util.logging.PwmLogEvent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.Iterator;

public class ExportLogsCommand extends AbstractCliCommand {

    @Override
    void doCommand()
            throws Exception
    {
        final LocalDB localDB = this.cliEnvironment.getLocalDB();
        final LocalDBStoredQueue logQueue = LocalDBStoredQueue.createLocalDBStoredQueue(null, localDB,
                LocalDB.DB.EVENTLOG_EVENTS);

        if (logQueue.isEmpty()) {
            out("no logs present");
            return;
        }

        final File outputFile = (File)cliEnvironment.getOptions().get(CliParameters.REQUIRED_NEW_OUTPUT_FILE.getName());
        out("outputting " + logQueue.size() + " log events to " + outputFile.getAbsolutePath() + "....");

        Writer outputWriter = null;
        try {
            outputWriter = new OutputStreamWriter(new FileOutputStream(outputFile));
            for (final Iterator<String> iter = logQueue.descendingIterator(); iter.hasNext();) {
                final String loopString = iter.next();
                final PwmLogEvent logEvent = PwmLogEvent.fromEncodedString(loopString);
                if (logEvent != null) {
                    outputWriter.write(logEvent.toLogString());
                    outputWriter.write("\n");
                }
            }
        } finally {
            if (outputWriter != null) {
                outputWriter.close();
            }
        }

        out("output complete");

    }

    @Override
    public CliParameters getCliParameters()
    {
        CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ExportLogs";
        cliParameters.description = "Export all logs in the LocalDB";
        cliParameters.options = Collections.singletonList(CliParameters.REQUIRED_NEW_OUTPUT_FILE);

        cliParameters.needsLocalDB = true;
        cliParameters.readOnly = true;

        return cliParameters;
    }
}
