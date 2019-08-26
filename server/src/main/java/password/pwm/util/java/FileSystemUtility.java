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

package password.pwm.util.java;

import lombok.Value;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

public class FileSystemUtility
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( FileSystemUtility.class );

    private static final AtomicLoopIntIncrementer OP_COUNTER = new AtomicLoopIntIncrementer();

    public static List<FileSummaryInformation> readFileInformation( final File rootFile )
    {
        final Instant startTime = Instant.now();
        final int operation = OP_COUNTER.next();
        LOGGER.trace( () -> "begin file summary load for file '" + rootFile.getAbsolutePath() + ", operation=" + operation );
        final ForkJoinPool pool = new ForkJoinPool();
        final RecursiveFileReaderTask task = new RecursiveFileReaderTask( rootFile );
        final List<FileSummaryInformation> fileSummaryInformations = pool.invoke( task );
        final AtomicLong byteCount = new AtomicLong( 0 );
        final AtomicInteger fileCount = new AtomicInteger( 0 );
        fileSummaryInformations.forEach( fileSummaryInformation -> byteCount.addAndGet( fileSummaryInformation.getSize() ) );
        fileSummaryInformations.forEach( fileSummaryInformation -> fileCount.incrementAndGet() );
        final Map<String, String> debugInfo = new LinkedHashMap<>();
        debugInfo.put( "operation", Integer.toString( operation ) );
        debugInfo.put( "bytes", StringUtil.formatDiskSizeforDebug( byteCount.get() ) );
        debugInfo.put( "files", Integer.toString( fileCount.get() ) );
        debugInfo.put( "duration", TimeDuration.compactFromCurrent( startTime ) );
        LOGGER.trace( () -> "completed file summary load for file '" + rootFile.getAbsolutePath() + ", " + StringUtil.mapToString( debugInfo ) );
        return fileSummaryInformations;
    }

    private static class RecursiveFileReaderTask extends RecursiveTask<List<FileSummaryInformation>>
    {
        private final File theFile;

        RecursiveFileReaderTask( final File theFile )
        {
            Objects.requireNonNull( theFile );
            this.theFile = theFile;
        }

        @Override
        protected List<FileSummaryInformation> compute()
        {
            final List<FileSummaryInformation> results = new ArrayList<>();

            if ( theFile.isDirectory() )
            {
                final File[] subFiles = theFile.listFiles();
                if ( subFiles != null )
                {
                    final List<RecursiveFileReaderTask> tasks = new ArrayList<>();
                    for ( final File file : subFiles )
                    {
                        final RecursiveFileReaderTask newTask = new RecursiveFileReaderTask( file );
                        newTask.fork();
                        tasks.add( newTask );
                    }
                    tasks.forEach( recursiveFileReaderTask -> results.addAll( recursiveFileReaderTask.join() ) );
                }
            }
            else
            {
                try
                {
                    results.add( fileInformationForFile( theFile ) );
                }
                catch ( Exception e )
                {
                    LOGGER.debug( () -> "error executing file summary reader: " + e.getMessage() );
                }
            }

            return Collections.unmodifiableList( results );
        }
    }

    private static FileSummaryInformation fileInformationForFile( final File file )
            throws IOException
    {
        if ( file == null || !file.exists() )
        {
            return null;
        }
        return new FileSummaryInformation(
                file.getName(),
                file.getParentFile().getAbsolutePath(),
                Instant.ofEpochMilli( file.lastModified() ),
                file.length(),
                crc32( file )
        );
    }

    public static long getFileDirectorySize( final File dir )
    {
        long size = 0;
        try
        {
            if ( dir.isFile() )
            {
                size = dir.length();
            }
            else
            {
                final File[] subFiles = dir.listFiles();
                if ( subFiles != null )
                {
                    for ( final File file : subFiles )
                    {
                        if ( file.isFile() )
                        {
                            size += file.length();
                        }
                        else
                        {
                            size += getFileDirectorySize( file );
                        }

                    }
                }
            }
        }
        catch ( NullPointerException e )
        {
            // file was deleted before file size could be read
        }

        return size;
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
        try
        {
            final Method getFreeSpaceMethod = File.class.getMethod( "getFreeSpace" );
            final Object rawResult = getFreeSpaceMethod.invoke( file );
            return ( Long ) rawResult;
        }
        catch ( NoSuchMethodException e )
        {
            /* no error, pre java 1.6 doesn't have this method */
        }
        catch ( Exception e )
        {
            LOGGER.debug( () -> "error reading file space remaining for " + file.toString() + ",: " + e.getMessage() );
        }
        return -1;
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
                        LOGGER.error( "unable to delete old backup file: " + thisFile.getAbsolutePath() );
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
    }

    public static void deleteDirectoryContents( final File path ) throws IOException
    {
        deleteDirectoryContents( path, false );
    }

    private static void deleteDirectoryContents( final File path, final boolean deleteThisLevel )
            throws IOException
    {
        if ( !path.exists() )
        {
            throw new FileNotFoundException( path.getAbsolutePath() );
        }

        if ( path.isDirectory() )
        {
            final File[] files = path.listFiles();
            if ( files != null )
            {
                for ( final File f : files )
                {
                    deleteDirectoryContents( f, true );
                }
            }
        }

        if ( deleteThisLevel )
        {
            LOGGER.debug( () -> "deleting temporary file " + path.getAbsolutePath() );
            try
            {
                Files.delete( path.toPath() );
            }
            catch ( IOException e )
            {
                LOGGER.warn( "error deleting temporary file '" + path.getAbsolutePath() + "', error: " + e.getMessage() );
            }
        }
    }

    private static long crc32( final File file )
            throws IOException
    {
        final CRC32 crc32 = new CRC32();
        final FileInputStream fileInputStream = new FileInputStream( file );
        final FileChannel fileChannel = fileInputStream.getChannel();
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect( 1024 );

        while ( fileChannel.read( byteBuffer ) > 0 )
        {
            // redundant cast to buffer to solve jdk8/9 inter-op issue
            ( ( Buffer ) byteBuffer ).flip();

            crc32.update( byteBuffer );

            // redundant cast to buffer to solve jdk8/9 inter-op issue
            ( ( Buffer ) byteBuffer ).clear();
        }

        return crc32.getValue();
    }

    public static void mkdirs( final File file )
            throws PwmUnrecoverableException
    {
        if ( !file.exists() )
        {
            if ( !file.mkdirs() )
            {
                throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, "unable to create directory: " + file.getAbsolutePath() );
            }
        }
        else if ( !file.isDirectory() )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, "unable to create directory, file already exists: " + file.getAbsolutePath() );
        }
    }
}
