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
                catch ( CliException e )
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
