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

import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.util.cli.CliParameters;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;

public class ConfigNewCommand extends AbstractCliCommand
{
    public void doCommand( )
            throws Exception
    {
        final StoredConfigurationImpl storedConfiguration = StoredConfigurationImpl.newStoredConfiguration();
        storedConfiguration.initNewRandomSecurityKey();
        storedConfiguration.writeConfigProperty(
                ConfigurationProperty.CONFIG_IS_EDITABLE, Boolean.toString( true ) );
        storedConfiguration.writeConfigProperty(
                ConfigurationProperty.CONFIG_EPOCH, String.valueOf( 0 ) );

        final File outputFile = ( File ) cliEnvironment.getOptions().get( CliParameters.REQUIRED_NEW_OUTPUT_FILE.getName() );

        try ( FileOutputStream fileOutputStream = new FileOutputStream( outputFile, false ) )
        {
            storedConfiguration.toXml( fileOutputStream );
        }
        out( "success: created new configuration" );
    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ConfigNew";
        cliParameters.description = "Create a new configuration file";
        cliParameters.needsPwmApplication = false;
        cliParameters.needsLocalDB = false;
        cliParameters.options = Collections.singletonList( CliParameters.REQUIRED_NEW_OUTPUT_FILE );

        cliParameters.readOnly = true;
        return cliParameters;
    }
}
