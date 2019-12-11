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
import java.io.FileOutputStream;
import java.util.Collections;

public class ExportLocalDBCommand extends AbstractCliCommand
{
    @Override
    void doCommand( )
            throws Exception
    {
        final LocalDB localDB = cliEnvironment.getLocalDB();

        final File outputFile = ( File ) cliEnvironment.getOptions().get( CliParameters.REQUIRED_NEW_OUTPUT_FILE.getName() );
        if ( outputFile.exists() )
        {
            out( "outputFile for exportLocalDB cannot already exist" );
            return;
        }

        final LocalDBUtility localDBUtility = new LocalDBUtility( localDB );
        try ( FileOutputStream fileOutputStream = new FileOutputStream( outputFile ) )
        {
            localDBUtility.exportLocalDB( fileOutputStream, System.out );
        }
        catch ( final PwmOperationalException e )
        {
            out( "error during export: " + e.getMessage() );
        }
    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ExportLocalDB";
        cliParameters.description = "Export the entire LocalDB contents to a backup file";
        cliParameters.options = Collections.singletonList( CliParameters.REQUIRED_NEW_OUTPUT_FILE );

        cliParameters.needsLocalDB = true;
        cliParameters.readOnly = true;

        return cliParameters;
    }
}
