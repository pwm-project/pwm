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

import password.pwm.bean.SessionLabel;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.util.cli.CliParameters;

public class ConfigUnlockCommand extends AbstractCliCommand
{
    public void doCommand( )
            throws Exception
    {
        final ConfigurationReader configurationReader = cliEnvironment.getConfigurationReader();
        final StoredConfigurationImpl storedConfiguration = configurationReader.getStoredConfiguration();
        if ( Boolean.parseBoolean( storedConfiguration.readConfigProperty( ConfigurationProperty.CONFIG_IS_EDITABLE ) ) )
        {
            out( "configuration is already unlocked" );
            return;
        }

        storedConfiguration.writeConfigProperty( ConfigurationProperty.CONFIG_IS_EDITABLE, Boolean.toString( true ) );
        configurationReader.saveConfiguration( storedConfiguration, cliEnvironment.getPwmApplication(), SessionLabel.CLI_SESSION_LABEL );
        out( "success" );
    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ConfigUnlock";
        cliParameters.description = "Unlock a configuration, allows config to be edited without LDAP authentication.";
        cliParameters.needsPwmApplication = false;
        cliParameters.readOnly = true;
        return cliParameters;
    }
}
