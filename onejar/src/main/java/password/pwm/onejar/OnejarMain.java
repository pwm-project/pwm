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

package password.pwm.onejar;

import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OnejarMain
{
    //private static final String TEMP_WAR_FILE_NAME = "embed.war";
    static final String KEYSTORE_ALIAS = "https";


    public static void main( final String[] args )
    {
        final ArgumentParser argumentParser = new ArgumentParser();
        OnejarConfig onejarConfig = null;
        try
        {
            onejarConfig = argumentParser.parseArguments( args );
        }
        catch ( ArgumentParserException | OnejarException e )
        {
            output( "error parsing command line: " + e.getMessage() );
        }

        final OnejarMain onejarMain = new OnejarMain();

        if ( onejarConfig != null )
        {
            if ( onejarConfig.getExecCommand() != null )
            {
                onejarMain.execCommand( onejarConfig );
            }
            else
            {
                onejarMain.run( onejarConfig );
            }
        }
    }

    private void execCommand( final OnejarConfig onejarConfig )
    {
        try
        {
            final String cmdLine = onejarConfig.getExecCommand();
            out( "executing command: " + cmdLine );
            final TomcatOnejarRunner runner = new TomcatOnejarRunner( this );
            final URLClassLoader classLoader = runner.warClassLoaderFromConfig( onejarConfig );

            final Class pwmMainClass = classLoader.loadClass( "password.pwm.util.cli.MainClass" );
            final Method mainMethod = pwmMainClass.getMethod( "main", String[].class );
            final List<String> cmdLineItems = new ArrayList<>( );
            cmdLineItems.add( "-applicationPath=" + onejarConfig.getApplicationPath().getAbsolutePath() );
            cmdLineItems.addAll( Arrays.asList( cmdLine.split( " " ) ) );
            final String[] arguments = cmdLineItems.toArray( new String[0] );

            mainMethod.invoke( null, ( Object ) arguments );
        }
        catch ( Exception e )
        {
            e.printStackTrace( );
        }
    }

    void run( final OnejarConfig onejarConfig )
    {
        final TomcatOnejarRunner runner = new TomcatOnejarRunner( this );
        final Instant startTime = Instant.now();

        if ( onejarConfig != null )
        {
            try
            {
                runner.startTomcat( onejarConfig );
            }
            catch ( OnejarException | ServletException | IOException e )
            {
                out( "error starting tomcat: " + e.getMessage() );
            }
        }

        final Duration duration = Duration.between( startTime, Instant.now() );
        out( "exiting after " + duration.toString() );
    }

    void out( final String output )
    {
        output( output );
    }

    static void output( final String output )
    {
        System.out.println( output );
    }
}
