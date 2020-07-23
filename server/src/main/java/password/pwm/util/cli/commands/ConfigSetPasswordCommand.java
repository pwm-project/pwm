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
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.util.cli.CliParameters;

import java.util.Collections;

public class ConfigSetPasswordCommand extends AbstractCliCommand
{

    public void doCommand( )
            throws Exception
    {
        final ConfigurationReader configurationReader = cliEnvironment.getConfigurationReader();
        final StoredConfiguration storedConfiguration = configurationReader.getStoredConfiguration();
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );
        final String password = getOptionalPassword();
        StoredConfigurationUtil.setPassword( modifier, password );
        configurationReader.saveConfiguration( modifier.newStoredConfiguration(), cliEnvironment.getPwmApplication(), SessionLabel.CLI_SESSION_LABEL );
        out( "success: new password has been set" );
    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ConfigSetPassword";
        cliParameters.description = "Sets the configuration password";
        cliParameters.options = Collections.singletonList( CliParameters.OPTIONAL_PASSWORD );
        cliParameters.needsPwmApplication = false;
        cliParameters.needsLocalDB = false;
        cliParameters.readOnly = true;
        return cliParameters;
    }
}
