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

package password.pwm.onejar;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
        catch ( final ArgumentParserException | OnejarException e )
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
                onejarMain.deployWebApp( onejarConfig );
            }
        }
    }

    private void execCommand( final OnejarConfig onejarConfig )
    {
        try
        {
            purgeDirectory( onejarConfig.getWorkingPath().toPath() );
            this.explodeWar( onejarConfig );
            final String cmdLine = onejarConfig.getExecCommand();
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
        catch ( final Exception e )
        {
            e.printStackTrace( );
        }
    }

    void deployWebApp( final OnejarConfig onejarConfig )
    {
        final Instant startTime = Instant.now();

        if ( onejarConfig != null )
        {
            try
            {
                purgeDirectory( onejarConfig.getWorkingPath().toPath() );
                this.explodeWar( onejarConfig );
                final TomcatOnejarRunner runner = new TomcatOnejarRunner( this );
                runner.startTomcat( onejarConfig );

            }
            catch ( final OnejarException | ServletException | IOException e )
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
        final Instant now = Instant.now().truncatedTo( ChronoUnit.SECONDS );
        System.out.println( now.toString() + ", OneJar, " + output );
    }

    private void explodeWar( final OnejarConfig onejarConfig ) throws IOException
    {
        final InputStream warSource = onejarConfig.getWar();
        final ZipInputStream zipInputStream = new ZipInputStream( warSource );
        final File outputFolder = onejarConfig.getWarFolder( );

        ArgumentParser.mkdirs( outputFolder );

        ZipEntry zipEntry = zipInputStream.getNextEntry();

        while ( zipEntry != null )
        {
            final String fileName = zipEntry.getName();
            final File newFile = new File( outputFolder + File.separator + fileName );

            if ( !zipEntry.isDirectory() )
            {
                ArgumentParser.mkdirs( newFile.getParentFile() );
                Files.copy( zipInputStream, newFile.toPath() );
            }
            zipEntry = zipInputStream.getNextEntry();
        }
        out( "deployed war" );
    }

    private void purgeDirectory( final Path rootPath )
            throws IOException
    {
        if ( rootPath.toFile().exists() )
        {
            out( "purging work directory: " + rootPath );
            Files.walk( rootPath, FileVisitOption.FOLLOW_LINKS )
                    .sorted( Comparator.reverseOrder() )
                    .map( Path::toFile )
                    .filter( file -> !rootPath.toString().equals( file.getPath() ) )
                    .forEach( File::delete );
        }
    }
}
