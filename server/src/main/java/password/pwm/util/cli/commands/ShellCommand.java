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

import password.pwm.PwmConstants;
import password.pwm.util.cli.CliEnvironment;
import password.pwm.util.cli.CliException;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.cli.MainClass;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

public class ShellCommand extends AbstractCliCommand
{
    public static final Map<String, CliCommand> SHELL_COMMANDS;

    static
    {
        final List<CliCommand> commandList = new ArrayList<>();
        commandList.add( new ConfigUnlockCommand() );
        commandList.add( new ConfigLockCommand() );
        commandList.add( new ConfigSetPasswordCommand() );
        commandList.add( new VersionCommand() );
        commandList.add( new ConfigDeleteCommand() );
        commandList.add( new ConfigResetHttpsCommand() );

        final Map<String, CliCommand> sortedMap = new TreeMap<>();
        for ( final CliCommand command : commandList )
        {
            sortedMap.put( command.getCliParameters().commandName, command );
        }
        SHELL_COMMANDS = Collections.unmodifiableMap( sortedMap );
    }

    void doCommand( )
            throws Exception
    {
        boolean exitFlag = false;

        final Scanner scanner = new Scanner( new InputStreamReader( System.in, PwmConstants.DEFAULT_CHARSET ) );
        while ( !exitFlag )
        {
            System.out.print( PwmConstants.PWM_APP_NAME + ">" );
            final String command = scanner.nextLine();
            if ( "exit".equalsIgnoreCase( command ) )
            {
                exitFlag = true;
            }
            else if ( "help".equalsIgnoreCase( command ) )
            {
                out( makeHelpTextOutput() );
            }
            else if ( command != null && !command.isEmpty() )
            {
                processCommand( command );
            }
        }
    }

    final void processCommand( final String commandLine )
    {
        if ( commandLine == null )
        {
            return;
        }

        final List<String> splitCommandLine = splitString( commandLine );
        if ( splitCommandLine.isEmpty() )
        {
            return;
        }

        final String command = splitCommandLine.get( 0 );

        boolean commandExecuted = false;
        for ( final CliCommand cliCommand : SHELL_COMMANDS.values() )
        {
            if ( command.equalsIgnoreCase( cliCommand.getCliParameters().commandName ) )
            {
                try
                {
                    executeCommand( cliEnvironment, cliCommand, commandLine );
                }
                catch ( final CliException e )
                {
                    out( "error executing command: " + e.getMessage() );
                }
                commandExecuted = true;
                break;
            }
        }

        if ( !commandExecuted )
        {
            out( "unknown command.  type 'help' for help." );
        }
    }

    public static void executeCommand(
            final CliEnvironment cliEnvironment,
            final CliCommand command,
            final String commandLine
    )
            throws CliException
    {
        final Map<String, Object> cliOptions;
        {
            final List<String> tokens = new ArrayList<>( splitString( commandLine ) );
            tokens.remove( 0 );
            cliOptions = MainClass.parseCommandOptions( command.getCliParameters(), tokens );
        }
        final CliEnvironment newEnvironment = cliEnvironment.toBuilder()
                .options( cliOptions )
                .build();

        command.execute( commandLine, newEnvironment );
    }

    public static String makeHelpTextOutput( )
    {
        return MainClass.helpTextFromCommands(
                SHELL_COMMANDS.values() )
                + "Exit" + "\n"
                + "       " + "Exit this shell"
                + "\n\n"
                + "usage: \n" + " CommandName <command options>"
                + "\n";
    }


    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "Shell";
        cliParameters.description = "Command Shell";
        cliParameters.options = Collections.emptyList();

        cliParameters.needsPwmApplication = false;
        cliParameters.needsLocalDB = false;
        cliParameters.readOnly = false;
        return cliParameters;
    }

    private static List<String> splitString( final String input )
    {
        if ( input == null )
        {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList( Arrays.asList( input.trim().split( "\\s+" ) ) );
    }

}
