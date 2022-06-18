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

import password.pwm.util.cli.CliParameters;
import password.pwm.util.cli.MainClass;

import java.io.IOException;
import java.util.Collections;

public class HelpCommand extends AbstractCliCommand
{

    @Override
    void doCommand( )
            throws IOException
    {
        out( MainClass.helpTextFromCommands( MainClass.COMMANDS.values() ) );
    }


    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "Help";
        cliParameters.description = "Display Help Text";
        cliParameters.options = Collections.emptyList();

        cliParameters.needsPwmApplication = false;
        cliParameters.needsLocalDB = false;
        cliParameters.readOnly = false;
        return cliParameters;
    }
}
