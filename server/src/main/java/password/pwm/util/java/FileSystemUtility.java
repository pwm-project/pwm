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

package password.pwm.util.java;

import lombok.Value;
import password.pwm.PwmConstants;
import password.pwm.error.PwmException;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.SecureEngine;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FileSystemUtility
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( FileSystemUtility.class );

    public static List<FileSummaryInformation> readFileInformation( final List<Path> rootFiles )
    {
        final List<FileSummaryInformation> returnList = new ArrayList<>();
        for ( final Path path : rootFiles )
        {
            returnList.addAll( readFileInformationHierarchy( path ) );
        }
        return List.copyOf( returnList );
    }

    public static List<FileSummaryInformation> readFileInformationHierarchy( final Path rootFile )
    {
        try
        {
            final List<Path> paths =  Files.walk( rootFile )
                    .filter( Files::isRegularFile )
                    .collect( Collectors.toList() );

            final List<FileSummaryInformation> returnList = new ArrayList<>();
            for ( final Path path : paths )
            {
                returnList.add( FileSummaryInformation.fromFile( path ) );
            }
            return List.copyOf( returnList );

        }
        catch ( final IOException e )
        {
            LOGGER.trace( () -> "error during file summary load: " + e.getMessage() );
        }

        return Collections.emptyList();
    }

    public static long getFileDirectorySize( final Path dir )
    {
        try
        {
            final List<Path> files = Files.walk( dir )
                    .filter( Files::isRegularFile )
                    .collect( Collectors.toList() );

            long totalSize = 0;
            for ( final Path file : files )
            {
                totalSize += Files.size( file );
            }
            return totalSize;
        }
        catch ( final IOException e )
        {
            LOGGER.error( () -> "error calculating disk size of '" + dir + "', error: " + e.getMessage(), e );
        }

        return -1;
    }

    public static Path figureFilepath( final String filename, final Path suggestedPath )
    {
        Objects.requireNonNull( filename );
        Objects.requireNonNull( suggestedPath );

        final Path filenamePath = Path.of( filename );

        if ( filenamePath.isAbsolute() )
        {
            return filenamePath;
        }

        return suggestedPath.resolve( filename );
    }

    public static long diskSpaceRemaining( final Path file )
    {
        try
        {
            return Files.getFileStore( file ).getUsableSpace();
        }
        catch ( final IOException e )
        {
            LOGGER.error( () -> "error calculating disk space remaining of '" + file + "', error: " + e.getMessage(), e );
        }

        return -1;
    }

    public static void rotateBackups( final Path inputFile, final int maxRotate )
            throws IOException
    {
        if ( maxRotate < 1 || inputFile == null || !Files.exists( inputFile ) )
        {
            return;
        }

        for ( int i = maxRotate; i >= 0; i-- )
        {
            final Path thisFile = ( i == 0 ) ? inputFile : addFilenameSuffix( inputFile, "-" + i );
            final Path youngerFile = ( i <= 1 ) ? inputFile : addFilenameSuffix( inputFile, "-" + ( i - 1 ) );

            if ( i == maxRotate )
            {
                if ( Files.exists( thisFile ) )
                {
                    LOGGER.debug( () -> "deleting old backup file: " + thisFile );
                    Files.delete( thisFile );
                }
            }
            else if ( i == 0 || Files.exists( youngerFile ) )
            {
                final Path destFile = addFilenameSuffix( inputFile, "-" + ( i + 1 ) );
                LOGGER.debug( () -> "renaming backup file " + thisFile + " to " + destFile );
                Files.move( thisFile, destFile );
            }
        }
    }

    @Value
    public static class FileSummaryInformation implements Serializable
    {
        private final String filename;
        private final String filepath;
        private final Instant modified;
        private final long size;
        private final String sha512Hash;

        public static FileSummaryInformation fromFile( final Path file )
                throws IOException
        {
            final String sha512Hash;
            try
            {
                sha512Hash = SecureEngine.hash( file, PwmHashAlgorithm.SHA512 );
            }
            catch ( final PwmException exception )
            {
                throw new IllegalStateException( exception );
            }

            return new FileSummaryInformation(
                    LocaleHelper.orNotApplicable( file.getFileName(), PwmConstants.DEFAULT_LOCALE ),
                    LocaleHelper.orNotApplicable( file.getParent(), PwmConstants.DEFAULT_LOCALE ),
                    Files.getLastModifiedTime( file ).toInstant(),
                    Files.size( file ),
                    sha512Hash
            );
        }
    }

    public static void deleteDirectoryContentsRecursively( final Path path )
            throws IOException
    {
        Objects.requireNonNull( path );

        final Iterator<Path> pathIterator = Files.walk( path )
                .filter( other -> !path.equals( other ) )
                .sorted( Comparator.reverseOrder() )
                .iterator();

        while ( pathIterator.hasNext() )
        {
            final Path nextPath = pathIterator.next();
            Files.delete( nextPath );
        }
    }

    public static Path createDirectory( final Path basePath, final String newDirectoryName )
            throws IOException
    {
        final Path path = basePath.resolve( newDirectoryName );
        return Files.createDirectories( path );
    }

    public static Path addFilenameSuffix( final Path path, final String suffix )
            throws IOException
    {
        final Path filenamePath = path.getFileName();
        if ( filenamePath == null )
        {
            throw new IOException( "can not add suffix to empty filename" );
        }
        final String filename = filenamePath.toString();
        if ( StringUtil.isEmpty( filename ) )
        {
            throw new IOException( "can not add suffix to empty filename" );
        }
        return path.resolveSibling( filename + suffix );
    }
}
