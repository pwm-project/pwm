/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

import password.pwm.util.cli.CliException;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;

import java.io.IOException;

public class ClearResponsesCommand extends AbstractCliCommand
{

    @Override
    void doCommand( )
            throws IOException, CliException
    {
        final String msg = "Proceeding with this operation will clear all stored responses from the LocalDB." + "\n"
                + "Please consider exporting the responses before proceeding. " + "\n"
                + "\n"
                + "The application must be stopped for this operation to succeed." + "\n";
        if ( !promptForContinue( msg ) )
        {
            return;
        }

        try
        {
            clearDb();
        }
        catch ( final LocalDBException e )
        {
            throw new CliException( e.getMessage(), e );
        }

    }

    private void clearDb()
            throws LocalDBException, IOException
    {
        final LocalDB localDB = cliEnvironment.getLocalDB();

        if ( localDB.size( LocalDB.DB.RESPONSE_STORAGE ) == 0 )
        {
            out( "The LocalDB response database is already empty" );
            return;
        }

        out( "clearing " + localDB.size( LocalDB.DB.RESPONSE_STORAGE ) + " responses" );
        localDB.truncate( LocalDB.DB.RESPONSE_STORAGE );
        out( "all saved responses are now removed from LocalDB" );
    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ClearLocalResponses";
        cliParameters.description = "Clear all responses from the LocalDB";

        cliParameters.needsLocalDB = true;
        cliParameters.readOnly = false;

        return cliParameters;
    }
}

