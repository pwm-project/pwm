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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import password.pwm.error.PwmException;
import password.pwm.util.cli.CliEnvironment;
import password.pwm.util.cli.CliException;
import password.pwm.util.cli.CliParameters;

import java.io.Console;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Scanner;

public abstract class AbstractCliCommand implements CliCommand
{
    protected CliEnvironment cliEnvironment;

    protected AbstractCliCommand( )
    {
    }

    void out( final CharSequence out )
            throws IOException
    {
        if ( cliEnvironment != null )
        {
            final Writer debugWriter = cliEnvironment.getDebugWriter();
            if ( debugWriter != null )
            {
                debugWriter.append( out );
                debugWriter.append( '\n' );
                debugWriter.flush();
            }
        }
    }

    @Override
    public void execute(
            final String cli,
            final CliEnvironment cliEnvironment
    )
            throws CliException
    {
        this.cliEnvironment = cliEnvironment;
        try
        {
            doCommand();
        }
        catch ( final Exception e )
        {
            throw new CliException( "error executing command: " + e.getMessage() );
        }
    }

    boolean promptForContinue( final String msg )
            throws IOException
    {
        if ( cliEnvironment.getMainOptions().isForceFlag() )
        {
            return true;
        }
        out( msg );
        out( "" );
        out( "To proceed, type 'continue'" );
        final Scanner scanner = new Scanner( System.in, Charset.defaultCharset().name() );
        final String input = scanner.nextLine();

        if ( !"continue".equalsIgnoreCase( input ) )
        {
            out( "exiting..." );
            return false;
        }
        return true;
    }

    abstract void doCommand( ) throws IOException, PwmException, CliException;

    @SuppressFBWarnings( "DM_EXIT" )
    String promptForPassword( )
            throws IOException
    {
        final Console console = System.console();
        console.writer().write( "enter password:" );
        console.writer().flush();
        final String password = new String( console.readPassword() );
        console.writer().write( "verify password:" );
        console.writer().flush();
        final String verify = new String( console.readPassword() );
        if ( !password.equals( verify ) )
        {
            out( "verify password incorrect, exiting..." );
            System.exit( -1 );
        }
        return password;
    }

    String getOptionalPassword( )
            throws IOException
    {
        final String optionName = CliParameters.OPTIONAL_PASSWORD.getName();
        if ( cliEnvironment.getOptions().containsKey( optionName ) )
        {
            return ( String ) cliEnvironment.getOptions().get( optionName );
        }
        else
        {
            return promptForPassword();
        }
    }
}
