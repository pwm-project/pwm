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
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.config.stored.StoredConfigurationImpl;

import java.util.Collections;

public class ConfigSetPasswordCommand extends AbstractCliCommand {
    protected static final String PASSWORD_OPTIONNAME = "password";

    public void doCommand()
            throws Exception
    {
        final ConfigurationReader configurationReader = cliEnvironment.getConfigurationReader();
        final StoredConfigurationImpl storedConfiguration = configurationReader.getStoredConfiguration();
        final String password;
        if (cliEnvironment.getOptions().containsKey(PASSWORD_OPTIONNAME)) {
            password = (String)cliEnvironment.getOptions().get(PASSWORD_OPTIONNAME);
        } else {
            password = promptForPassword();
        }
        storedConfiguration.setPassword(password);
        configurationReader.saveConfiguration(storedConfiguration, cliEnvironment.getPwmApplication(), PwmConstants.CLI_SESSION_LABEL);
        out("success");
    }

    @Override
    public CliParameters getCliParameters()
    {
        final CliParameters.Option passwordValueOption = new CliParameters.Option() {
            public boolean isOptional()
            {
                return true;
            }

            public type getType()
            {
                return type.STRING;
            }

            public String getName()
            {
                return PASSWORD_OPTIONNAME;
            }
        };

        CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ConfigSetPassword";
        cliParameters.description = "Sets the configuration password";
        cliParameters.options = Collections.singletonList(passwordValueOption);
        cliParameters.needsPwmApplication = true;
        cliParameters.needsLocalDB = false;
        cliParameters.readOnly = true;
        return cliParameters;
    }
}
