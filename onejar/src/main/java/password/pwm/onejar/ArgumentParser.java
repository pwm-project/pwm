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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ArgumentParser
{
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public OnejarConfig parseArguments( final String[] args )
            throws ArgumentParserException, OnejarException
    {
        if ( args == null || args.length == 0 )
        {
            ArgumentParser.outputHelp();
        }
        else
        {
            final CommandLine commandLine;

            try
            {
                commandLine = new DefaultParser().parse( Argument.asOptions(), args );
            }
            catch ( ParseException e )
            {
                throw new ArgumentParserException( "unable to parse command line: " + e.getMessage() );
            }

            if ( commandLine.hasOption( Argument.version.name() ) )
            {
                OnejarMain.output( TomcatOnejarRunner.getVersion() );
                return null;
            }
            else if ( commandLine.hasOption( Argument.help.name() ) )
            {
                ArgumentParser.outputHelp();
                return null;
            }
            else
            {
                final Map<Argument, String> argumentMap;
                if ( commandLine.hasOption( Argument.properties.name() ) )
                {
                    if ( args.length > 2 )
                    {
                        throw new ArgumentParserException( Argument.properties.name() + " must be the only argument specified" );
                    }
                    final String filename = commandLine.getOptionValue( Argument.properties.name() );
                    argumentMap = mapFromProperties( filename );
                }
                else
                {
                    argumentMap = mapFromCommandLine( commandLine );
                }
                final OnejarConfig onejarConfig;
                try
                {
                    onejarConfig = makeTomcatConfig( argumentMap );
                }
                catch ( IOException e )
                {
                    throw new ArgumentParserException( "error while reading input: " + e.getMessage() );
                }
                return onejarConfig;
            }
        }

        return null;
    }

    private Map<Argument, String> mapFromProperties( final String filename ) throws ArgumentParserException
    {
        final Properties props = new Properties();
        try ( InputStream is = new FileInputStream( new File( filename ) ) )
        {
            props.load( is );
        }
        catch ( IOException e )
        {
            throw new ArgumentParserException( "unable to read properties input file: " + e.getMessage() );
        }

        final Map<Argument, String> map = new HashMap<>();
        for ( final Option option : Argument.asOptionMap().values() )
        {
            if ( option.hasArg() )
            {
                final Argument argument = Argument.valueOf( option.getOpt() );
                final String value = props.getProperty( argument.name() );
                if ( value != null )
                {
                    map.put( argument, value );
                }
            }
        }
        return Collections.unmodifiableMap( map );
    }

    private Map<Argument, String> mapFromCommandLine( final CommandLine commandLine )
    {
        final Map<Argument, String> map = new HashMap<>();
        for ( final Option option : Argument.asOptionMap().values() )
        {
            if ( option.hasArg() )
            {
                if ( commandLine.hasOption( option.getOpt() ) )
                {
                    final Argument argument = Argument.valueOf( option.getOpt() );
                    final String value = commandLine.getOptionValue( option.getOpt() );
                    map.put( argument, value );
                }
            }
        }
        return Collections.unmodifiableMap( map );
    }


    private OnejarConfig makeTomcatConfig( final Map<Argument, String> argumentMap )
            throws IOException, ArgumentParserException
    {
        final OnejarConfig.OnejarConfigBuilder onejarConfig = OnejarConfig.builder();
        onejarConfig.keystorePass( genRandomString( 32 ) );
        onejarConfig.applicationPath( parseFileOption( argumentMap, Argument.applicationPath ) );

        final String context = argumentMap.getOrDefault( Argument.context, Resource.defaultContext.getValue() );
        onejarConfig.context( context );

        if ( argumentMap.containsKey( Argument.war ) )
        {
            final File inputWarFile = new File( argumentMap.get( Argument.war ) );
            if ( !inputWarFile.exists() )
            {
                final String msg = "output war file " + inputWarFile.getAbsolutePath() + "does not exist";
                System.out.println( msg );
                throw new IllegalStateException( msg );
            }
            onejarConfig.war( new FileInputStream( inputWarFile ) );
        }
        else
        {
            onejarConfig.war( getEmbeddedWar() );
        }

        final int port;
        {
            final int defaultPort = Integer.parseInt( Resource.defaultPort.getValue() );
            if ( argumentMap.containsKey( Argument.port ) )
            {
                try
                {
                    port = Integer.parseInt( argumentMap.get( Argument.port ) );
                    onejarConfig.port( port );
                }
                catch ( NumberFormatException e )
                {
                    final String msg = Argument.port.name() + " argument must be numeric";
                    System.out.println( msg );
                    throw new IllegalStateException( msg );
                }
            }
            else
            {
                port = defaultPort;
            }
        }
        onejarConfig.port( port );

        final String localAddress = argumentMap.getOrDefault( Argument.localAddress, Resource.defaultLocalAddress.getValue() );
        onejarConfig.localAddress( localAddress );

        try
        {
            final ServerSocket socket = new ServerSocket( port, 100, InetAddress.getByName( localAddress ) );
            socket.close();
        }
        catch ( Exception e )
        {
            throw new ArgumentParserException( "port or address conflict: " + e.getMessage() );
        }

        if ( argumentMap.containsKey( Argument.workPath ) )
        {
            onejarConfig.workingPath( parseFileOption( argumentMap, Argument.workPath ) );
        }
        else
        {
            onejarConfig.workingPath( figureDefaultWorkPath( localAddress, context, port ) );
        }

        return onejarConfig.build();
    }


    private static void outputHelp( ) throws OnejarException
    {
        final HelpFormatter formatter = new HelpFormatter();
        System.out.println( TomcatOnejarRunner.getVersion() );
        System.out.println( "usage:" );
        formatter.printOptions(
                System.console().writer(),
                HelpFormatter.DEFAULT_WIDTH,
                Argument.asOptions(),
                3,
                8 );
    }


    private static File parseFileOption( final Map<Argument, String> argumentMap, final Argument argName ) throws ArgumentParserException
    {
        if ( !argumentMap.containsKey( argName ) )
        {
            throw new ArgumentParserException( "option " + argName + " required" );
        }
        final File file = new File( argumentMap.get( argName ) );
        if ( !file.isAbsolute() )
        {
            throw new ArgumentParserException( "a fully qualified file path name is required for " + argName );
        }
        if ( !file.exists() )
        {
            throw new ArgumentParserException( "path specified by " + argName + " must exist" );
        }
        return file;
    }

    private static File figureDefaultWorkPath(
            final String localAddress,
            final String context,
            final int port
    )
            throws ArgumentParserException, IOException
    {
        final String userHomePath = System.getProperty( "user.home" );
        if ( userHomePath != null && !userHomePath.isEmpty() )
        {
            final File basePath = new File( userHomePath + File.separator
                    + Resource.defaultWorkPathName.getValue() );

            mkdirs( basePath );

            final String workPath;
            {
                String workPathStr = basePath.getPath() + File.separator + "work"
                        + "-"
                        + escapeFilename( context )
                        + "-"
                        + escapeFilename( Integer.toString( port ) );

                if ( localAddress != null && !localAddress.isEmpty() )
                {
                    workPathStr += "-" + escapeFilename( localAddress );

                }
                workPath = workPathStr;
            }
            final File workFile = new File( workPath );
            mkdirs( workFile );
            OnejarMain.output( "using work directory: " + workPath );
            return workFile;
        }

        throw new ArgumentParserException( "cant locate user home directory" );
    }

    private static InputStream getEmbeddedWar( ) throws IOException, ArgumentParserException
    {
        final Class clazz = TomcatOnejarRunner.class;
        final String className = clazz.getSimpleName() + ".class";
        final String classPath = clazz.getResource( className ).toString();
        if ( !classPath.startsWith( "jar" ) )
        {
            throw new ArgumentParserException( "not running from war, war option must be specified" );
        }
        final String warPath = classPath.substring( 0, classPath.lastIndexOf( "!" ) + 1 )
                + "/" + Resource.defaultWarFileName.getValue();
        return new URL( warPath ).openStream();
    }

    private static String escapeFilename( final String input )
    {
        return input.replaceAll( "\\W+", "_" );
    }

    private static String genRandomString( final int length )
    {
        final SecureRandom secureRandom = new SecureRandom();
        final StringBuilder stringBuilder = new StringBuilder();
        while ( stringBuilder.length() < length )
        {
            stringBuilder.append( ALPHABET.charAt( secureRandom.nextInt( ALPHABET.length() ) ) );
        }
        return stringBuilder.toString();
    }

    static void mkdirs( final File file ) throws IOException
    {
        if ( !file.mkdirs() && !file.exists() )
        {
            throw new IOException( "unable to create path " + file.getAbsolutePath() );
        }
    }
}
