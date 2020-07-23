/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

import password.pwm.error.PwmOperationalException;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBUtility;

import java.io.File;
import java.util.Collections;

public class ImportLocalDBCommand extends AbstractCliCommand
{
    @Override
    void doCommand( )
            throws Exception
    {
        final LocalDB localDB = cliEnvironment.getLocalDB();

        final String msg = "Proceeding with this operation will clear ALL data from the LocalDB." + "\n"
                + "Please consider backing up the LocalDB before proceeding. " + "\n"
                + "\n"
                + "The application must be stopped for this operation to succeed.";
        if ( !promptForContinue( msg ) )
        {
            out( "exiting..." );
            return;
        }

        final LocalDBUtility pwmDBUtility = new LocalDBUtility( localDB );
        final File inputFile = ( File ) cliEnvironment.getOptions().get( CliParameters.REQUIRED_EXISTING_INPUT_FILE.getName() );
        try
        {
            pwmDBUtility.importLocalDB( inputFile, System.out );
        }
        catch ( final PwmOperationalException e )
        {
            out( "error during import: " + e.getMessage() );
        }
    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ImportLocalDB";
        cliParameters.description = "Import the entire LocalDB contents from a backup file";
        cliParameters.options = Collections.singletonList( CliParameters.REQUIRED_EXISTING_INPUT_FILE );

        cliParameters.needsLocalDB = true;
        cliParameters.readOnly = false;

        return cliParameters;
    }
}
