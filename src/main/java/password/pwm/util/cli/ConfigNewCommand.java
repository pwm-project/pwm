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

import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.StoredConfigurationImpl;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;

public class ConfigNewCommand extends AbstractCliCommand {
    public void doCommand()
            throws Exception
    {
        final StoredConfigurationImpl storedConfiguration = StoredConfigurationImpl.newStoredConfiguration();
        storedConfiguration.initNewRandomSecurityKey();
        storedConfiguration.writeConfigProperty(
                ConfigurationProperty.CONFIG_IS_EDITABLE, Boolean.toString(true));
        storedConfiguration.writeConfigProperty(
                ConfigurationProperty.CONFIG_EPOCH, String.valueOf(0));

        final File outputFile = (File)cliEnvironment.getOptions().get(CliParameters.REQUIRED_NEW_OUTPUT_FILE.getName());
        storedConfiguration.toXml(new FileOutputStream(outputFile, false));
        out("success");
    }

    @Override
    public CliParameters getCliParameters()
    {
        CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ConfigNew";
        cliParameters.description = "Create a new configuration file";
        cliParameters.needsPwmApplication = false;
        cliParameters.needsLocalDB = false;
        cliParameters.options = Collections.singletonList(CliParameters.REQUIRED_NEW_OUTPUT_FILE);

        cliParameters.readOnly = true;
        return cliParameters;
    }
}