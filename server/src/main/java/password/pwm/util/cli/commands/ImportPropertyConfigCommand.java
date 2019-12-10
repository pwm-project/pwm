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

import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.util.PropertyConfigurationImporter;
import password.pwm.util.cli.CliParameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collections;

/**
 * Import properties configuration file and create new configuration.
 */
public class ImportPropertyConfigCommand extends AbstractCliCommand
{
    @Override
    void doCommand( )
            throws Exception
    {
        final File configFile = cliEnvironment.getConfigurationFile();

        if ( configFile.exists() )
        {
            out( "this command can not be run with an existing configuration in place.  Exiting..." );
            return;
        }

        final File inputFile = ( File ) cliEnvironment.getOptions().get( CliParameters.REQUIRED_EXISTING_INPUT_FILE.getName() );

        try
        {
            final PropertyConfigurationImporter importer = new PropertyConfigurationImporter();
            final StoredConfiguration storedConfiguration = importer.readConfiguration( new FileInputStream( inputFile ) );

            try ( OutputStream outputStream = new FileOutputStream( configFile ) )
            {
                StoredConfigurationFactory.toXml( storedConfiguration,  outputStream );
                out( "output configuration file " + configFile.getAbsolutePath() );
            }
        }
        catch ( final Exception e )
        {
            out( "error during import: " + e.getMessage() );
        }
    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ImportPropertyConfig";
        cliParameters.description = "Import an property based configuration and create a new configuration";
        cliParameters.options = Collections.singletonList( CliParameters.REQUIRED_EXISTING_INPUT_FILE );

        cliParameters.needsPwmApplication = false;
        cliParameters.needsLocalDB = false;
        cliParameters.readOnly = false;

        return cliParameters;
    }

}
