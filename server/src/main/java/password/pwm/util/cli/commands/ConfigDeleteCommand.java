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

import password.pwm.util.cli.CliParameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigDeleteCommand extends AbstractCliCommand
{
    @Override
    public void doCommand( )
            throws IOException
    {
        final Path configurationFile = cliEnvironment.getConfigurationFile();
        if ( configurationFile == null || !Files.exists( configurationFile ) )
        {
            out( "configuration file is not present" );
            return;
        }


        if ( !promptForContinue( "Proceeding will delete the existing configuration" ) )
        {
            return;
        }

        try
        {
            Files.delete( configurationFile );
            out( "success: configuration file has been deleted" );
        }
        catch ( final IOException e )
        {
            out( "unable to delete file: " + e.getMessage() );
        }
    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ConfigDelete";
        cliParameters.description = "Delete configuration.";
        cliParameters.needsPwmApplication = false;
        cliParameters.readOnly = true;
        return cliParameters;
    }

}
