/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

import password.pwm.config.stored.StoredConfigurationImpl;
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
            final StoredConfigurationImpl storedConfiguration = importer.readConfiguration( new FileInputStream( inputFile ) );

            try ( OutputStream outputStream = new FileOutputStream( configFile ) )
            {
                storedConfiguration.toXml( outputStream );
                out( "output configuration file " + configFile.getAbsolutePath() );
            }
        }
        catch ( Exception e )
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
