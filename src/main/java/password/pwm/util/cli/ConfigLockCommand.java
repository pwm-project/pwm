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

import password.pwm.PwmConstants;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.config.stored.StoredConfigurationImpl;

public class ConfigLockCommand extends AbstractCliCommand {
    public void doCommand()
            throws Exception
    {
        final ConfigurationReader configurationReader = cliEnvironment.getConfigurationReader();
        final StoredConfigurationImpl storedConfiguration = configurationReader.getStoredConfiguration();
        if (!Boolean.parseBoolean(storedConfiguration.readConfigProperty(ConfigurationProperty.CONFIG_IS_EDITABLE))) {
            out("configuration is already locked");
            return;
        }

        storedConfiguration.writeConfigProperty(ConfigurationProperty.CONFIG_IS_EDITABLE,Boolean.toString(false));
        configurationReader.saveConfiguration(storedConfiguration, cliEnvironment.getPwmApplication(), PwmConstants.CLI_SESSION_LABEL);
        out("success");
    }

    @Override
    public CliParameters getCliParameters()
    {
        CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ConfigLock";
        cliParameters.description = "Lock a configuration, prevents config from being edited without LDAP authentication.";
        cliParameters.needsPwmApplication = false;
        cliParameters.readOnly = true;
        return cliParameters;
    }
}
