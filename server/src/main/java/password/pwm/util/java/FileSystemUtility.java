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
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class FileSystemUtility
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( FileSystemUtility.class );

    public static Iterator<FileSummaryInformation> readFileInformation( final List<File> rootFiles )
    {
        return rootFiles.stream().flatMap( FileSystemUtility::readFileInformation ).iterator();
    }

    public static Stream<FileSummaryInformation> readFileInformation( final File rootFile )
    {
        try
        {
            return Files.walk( rootFile.toPath() )
                    .map( Path::toFile )
                    .filter( File::isFile )
                    .map( FileSummaryInformation::fromFile );

        }
        catch ( final IOException e )
        {
            LOGGER.trace( () -> "error during file summary load: " + e.getMessage() );
        }

        return Stream.empty();
    }

    public static long getFileDirectorySize( final File dir )
    {
        try
        {
            return Files.walk( dir.toPath() )
                    .filter( path -> path.toFile().isFile() )
                    .mapToLong( path -> path.toFile().length() )
                    .sum();
        }
        catch ( final IOException e )
        {
            LOGGER.error( () -> "error calculating disk size of '" + dir.getAbsolutePath() + "', error: " + e.getMessage(), e );
        }

        return -1;
    }

    public static File figureFilepath( final String filename, final File suggestedPath )
    {
        if ( filename == null || filename.length() < 1 )
        {
            return null;
        }

        if ( ( new File( filename ) ).isAbsolute() )
        {
            return new File( filename );
        }

        return new File( suggestedPath + File.separator + filename );
    }

    public static long diskSpaceRemaining( final File file )
    {
        return file.getFreeSpace();
    }

    public static void rotateBackups( final File inputFile, final int maxRotate )
    {
        if ( maxRotate < 1 )
        {
            return;
        }
        for ( int i = maxRotate; i >= 0; i-- )
        {
            final File thisFile = ( i == 0 ) ? inputFile : new File( inputFile.getAbsolutePath() + "-" + i );
            final File youngerFile = ( i <= 1 ) ? inputFile : new File( inputFile.getAbsolutePath() + "-" + ( i - 1 ) );

            if ( i == maxRotate )
            {
                if ( thisFile.exists() )
                {
                    LOGGER.debug( () -> "deleting old backup file: " + thisFile.getAbsolutePath() );
                    if ( !thisFile.delete() )
                    {
                        LOGGER.error( () -> "unable to delete old backup file: " + thisFile.getAbsolutePath() );
                    }
                }
            }
            else if ( i == 0 || youngerFile.exists() )
            {
                final File destFile = new File( inputFile.getAbsolutePath() + "-" + ( i + 1 ) );
                LOGGER.debug( () -> "backup file " + thisFile.getAbsolutePath() + " renamed to " + destFile.getAbsolutePath() );
                if ( !thisFile.renameTo( destFile ) )
                {
                    LOGGER.debug( () -> "unable to rename file " + thisFile.getAbsolutePath() + " to " + destFile.getAbsolutePath() );
                }
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
        private final long checksum;

        public static FileSummaryInformation fromFile( final File file )
        {
            final long crc32;
            try
            {
                crc32 = crc32( file );
            }
            catch ( final IOException exception )
            {
                throw new IllegalStateException( exception );
            }

            return new FileSummaryInformation(
                    file.getName(),
                    file.getParentFile().getAbsolutePath(),
                    Instant.ofEpochMilli( file.lastModified() ),
                    file.length(),
                    crc32
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

    private static long crc32( final File file )
            throws IOException
    {
        try ( InputStream fileInputStream = Files.newInputStream( file.toPath() ) )
        {
            try ( CrcChecksumOutputStream crcChecksumOutputStream = CrcChecksumOutputStream.newChecksumOutputStream( OutputStream.nullOutputStream() ) )
            {
                JavaHelper.copy( fileInputStream, crcChecksumOutputStream );
                return crcChecksumOutputStream.checksum();
            }
        }
    }
}
