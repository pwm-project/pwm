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

import password.pwm.bean.SessionLabel;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.util.cli.CliParameters;

import java.util.Optional;

public class ConfigLockCommand extends AbstractCliCommand
{
    public void doCommand( )
            throws Exception
    {
        final ConfigurationReader configurationReader = cliEnvironment.getConfigurationReader();
        final StoredConfiguration storedConfiguration = configurationReader.getStoredConfiguration();
        final Optional<String> configIsEditable = storedConfiguration.readConfigProperty( ConfigurationProperty.CONFIG_IS_EDITABLE );
        if ( configIsEditable.isPresent() && !Boolean.parseBoolean( configIsEditable.get() ) )
        {
            out( "configuration is already locked" );
            return;
        }

        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );
        modifier.writeConfigProperty( ConfigurationProperty.CONFIG_IS_EDITABLE, Boolean.toString( false ) );
        configurationReader.saveConfiguration( modifier.newStoredConfiguration(), cliEnvironment.getPwmApplication(), SessionLabel.CLI_SESSION_LABEL );
        out( "success: configuration has been locked" );
    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ConfigLock";
        cliParameters.description = "Lock a configuration, prevents config from being edited without LDAP authentication.";
        cliParameters.needsPwmApplication = false;
        cliParameters.readOnly = true;
        return cliParameters;
    }
}
