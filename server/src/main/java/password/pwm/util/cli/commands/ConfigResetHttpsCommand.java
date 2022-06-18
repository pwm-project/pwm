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

import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.stored.ConfigurationFileManager;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.cli.CliParameters;

import java.io.File;
import java.io.IOException;

public class ConfigResetHttpsCommand
        extends AbstractCliCommand
{
    @Override
    public void doCommand( )
            throws IOException, PwmUnrecoverableException, PwmOperationalException
    {
        final File configurationFile = cliEnvironment.getConfigurationFile();
        if ( configurationFile == null || !configurationFile.exists() )
        {
            out( "configuration file is not present" );
            return;
        }

        if ( !promptForContinue( "Proceeding will reset all https server settings to their default" ) )
        {
            return;
        }

        final ConfigurationFileManager configurationFileManager = new ConfigurationFileManager( cliEnvironment.getConfigurationFile(), SessionLabel.CLI_SESSION_LABEL );
        final StoredConfiguration storedConfiguration = configurationFileManager.getStoredConfiguration();

        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );
        for ( final PwmSetting setting : PwmSettingCategory.HTTPS_SERVER.getSettings() )
        {
            final StoredConfigKey key = StoredConfigKey.forSetting( setting, null, DomainID.systemId() );
            modifier.resetSetting( key, null );
        }
        configurationFileManager.saveConfiguration( modifier.newStoredConfiguration(), cliEnvironment.getPwmApplication() );
        out( "success" );
    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ConfigResetHttps";
        cliParameters.description = "Reset HTTPS settings to default";
        cliParameters.needsPwmApplication = false;
        cliParameters.needsLocalDB = false;
        cliParameters.readOnly = true;
        return cliParameters;
    }
}
