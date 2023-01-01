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

package password.pwm.onejar;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.EnumMap;
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
            catch ( final ParseException e )
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

                if ( argumentMap.containsKey( Argument.command ) && argumentMap.get( Argument.command ) == null )
                {
                    throw new ArgumentParserException( Argument.command.name() + " requires arguments" );
                }

                final OnejarConfig onejarConfig;
                try
                {
                    onejarConfig = makeTomcatConfig( argumentMap );
                }
                catch ( final IOException e )
                {
                    throw new ArgumentParserException( "error while reading input: " + e.getMessage() );
                }
                return onejarConfig;
            }
        }

        return null;
    }

    private Map<Argument, String> mapFromProperties( final String filename )
            throws ArgumentParserException
    {
        final Properties props = new Properties();
        try ( InputStream is = Files.newInputStream( Path.of( filename ) ) )
        {
            props.load( is );
        }
        catch ( final IOException e )
        {
            throw new ArgumentParserException( "unable to read properties input file: " + e.getMessage() );
        }

        final Map<Argument, String> map = new EnumMap<>( Argument.class );
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

    static Map<Argument, String> mapFromCommandLine( final CommandLine commandLine )
    {
        final Map<Argument, String> map = new EnumMap<>( Argument.class );
        for ( final Option option : Argument.asOptionMap().values() )
        {
            if ( option.hasArg() )
            {
                if ( commandLine.hasOption( option.getOpt() ) )
                {
                    final Argument argument = Argument.valueOf( option.getOpt() );
                    {
                        final String[] values = commandLine.getOptionValues( option.getOpt() );
                        if ( values != null )
                        {
                            final String joined = String.join( " ", values );
                            map.put( argument, joined );
                        }
                        else
                        {
                            map.put( argument, null );
                        }
                    }
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
            final Path inputWarFile = Path.of( argumentMap.get( Argument.war ) );
            if ( !Files.exists( inputWarFile ) )
            {
                final String msg = "output war file " + inputWarFile + "does not exist";
                System.out.println( msg );
                throw new IllegalStateException( msg );
            }

            try ( InputStream inputStream = Files.newInputStream( inputWarFile ) )
            {
                onejarConfig.war( inputStream );
            }
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
                catch ( final NumberFormatException e )
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

        if ( !argumentMap.containsKey( Argument.command ) )
        {
            try
            {
                final ServerSocket socket = new ServerSocket( port, 100, InetAddress.getByName( localAddress ) );
                socket.close();
            }
            catch ( final Exception e )
            {
                throw new ArgumentParserException( "port or address conflict: " + e.getMessage() );
            }
        }

        if ( argumentMap.containsKey( Argument.workPath ) )
        {
            onejarConfig.workingPath( parseFileOption( argumentMap, Argument.workPath ) );
        }
        else
        {
            final boolean isCommandExec = argumentMap.containsKey( Argument.command );
            onejarConfig.workingPath( figureDefaultWorkPath( localAddress, context, port, isCommandExec ) );
        }

        if ( argumentMap.containsKey( Argument.command ) )
        {
            final String value = argumentMap.get( Argument.command );
            onejarConfig.execCommand( value );
        }

        return onejarConfig.build();
    }


    private static void outputHelp( ) throws OnejarException
    {
        final HelpFormatter formatter = new HelpFormatter();
        System.out.println( "PWM " + TomcatOnejarRunner.getVersion() );
        System.out.println( "usage:" );
        formatter.printOptions(
                System.console().writer(),
                HelpFormatter.DEFAULT_WIDTH,
                Argument.asOptions(),
                3,
                8 );
    }


    private static Path parseFileOption( final Map<Argument, String> argumentMap, final Argument argName )
            throws ArgumentParserException
    {
        if ( !argumentMap.containsKey( argName ) )
        {
            throw new ArgumentParserException( "option " + argName + " required" );
        }
        final Path file = Path.of( argumentMap.get( argName ) );
        if ( !file.isAbsolute() )
        {
            throw new ArgumentParserException( "a fully qualified file path name is required for " + argName );
        }
        if ( !Files.exists( file ) )
        {
            throw new ArgumentParserException( "path specified by " + argName + " must exist" );
        }
        return file;
    }

    private static Path figureDefaultWorkPath(
            final String localAddress,
            final String context,
            final int port,
            final boolean isCommandExec
    )
            throws ArgumentParserException, IOException
    {
        final String userHomePath = System.getProperty( "user.home" );
        if ( userHomePath != null && !userHomePath.isEmpty() )
        {
            final Path basePath = Path.of( userHomePath ).resolve( Resource.defaultWorkPathName.getValue() );

            final String escapedLocalAddr = ( localAddress != null && !localAddress.isEmpty() )
                    ? "-" + escapeFilename( localAddress )
                    : "";
            final String workPath = "work"
                    + "-"
                    + escapeFilename( context )
                    + "-"
                    + escapeFilename( Integer.toString( port ) )
                    + ( isCommandExec ? "-cmd" : "" )
                    + escapedLocalAddr;

            final Path workFile = basePath.resolve( workPath );
            Files.createDirectories( workFile );
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
        final String warPath = classPath.substring( 0, classPath.lastIndexOf( '!' ) + 1 )
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
}
