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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

public class FileSystemUtility
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( FileSystemUtility.class );

    private static final int CRC_BUFFER_SIZE = 60 * 1024;

    private static final AtomicLoopIntIncrementer OP_COUNTER = new AtomicLoopIntIncrementer();


    public static ClosableIterator<FileSummaryInformation> readFileInformation( final List<File> rootFiles )
    {
        final Instant startTime = Instant.now();
        final int operation = OP_COUNTER.next();

        final int cpus = Runtime.getRuntime().availableProcessors();
        final BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(  );
        final ExecutorService executor = new ThreadPoolExecutor( cpus, cpus, Long.MAX_VALUE, TimeUnit.MILLISECONDS, workQueue );
        final TaskData taskData = new TaskData( executor );

        for ( final File rootFile : rootFiles )
        {
            LOGGER.trace( () -> "begin file summary load for file '" + rootFile.getAbsolutePath() + ", operation=" + operation );
            executor.execute( new RecursiveFileReaderTask( rootFile, taskData ) );
        }

        return new ConcurrentClosableIteratorWrapper<>( () ->
        {
            while ( taskData.getWorkInProgress().get() > 0 )
            {
                final FileSummaryInformation next = taskData.getOutputQueue().poll();

                if ( next == null )
                {
                    TimeDuration.of( 20, TimeDuration.Unit.MILLISECONDS ).pause();
                }
                else
                {
                    return Optional.of( next );
                }
            }
            return Optional.empty();
        },
                () ->
        {
            executor.shutdown();
            final Map<String, String> debugInfo = new LinkedHashMap<>();
            debugInfo.put( "bytes", StringUtil.formatDiskSizeforDebug( taskData.getByteCount().get() ) );
            debugInfo.put( "files", Integer.toString( taskData.getFileCount().get() ) );
            debugInfo.put( "duration", TimeDuration.compactFromCurrent( startTime ) );
            LOGGER.trace( () -> "completed file summary load for operation '" + operation + ", " + StringUtil.mapToString( debugInfo ) );
        } );
    }

    @Value
    private static class TaskData
    {
        private final AtomicLong byteCount = new AtomicLong( 0 );
        private final AtomicInteger fileCount = new AtomicInteger( 0 );
        private final AtomicInteger workInProgress = new AtomicInteger( 0 );
        private final Queue<FileSummaryInformation> outputQueue = new ConcurrentLinkedQueue<>();

        private Executor executor;

        TaskData( final Executor executor )
        {
            this.executor = executor;
        }
    }

    private static class RecursiveFileReaderTask implements Runnable
    {
        private final File theFile;
        private final TaskData taskData;

        RecursiveFileReaderTask( final File theFile, final TaskData taskData )
        {
            Objects.requireNonNull( theFile );
            Objects.requireNonNull( taskData );
            this.theFile = theFile;
            this.taskData = taskData;
            this.taskData.getWorkInProgress().incrementAndGet();
        }

        @Override
        public void run()
        {
            try
            {
                if ( theFile.isDirectory() )
                {
                    final File[] subFiles = theFile.listFiles();
                    if ( subFiles != null )
                    {
                        for ( final File file : subFiles )
                        {
                            final RecursiveFileReaderTask newTask = new RecursiveFileReaderTask( file, taskData );
                            taskData.getExecutor().execute( newTask );
                        }
                    }
                }
                else
                {
                    try
                    {
                        if ( theFile.exists() )
                        {
                            final FileSummaryInformation fileSummaryInformation = new FileSummaryInformation(
                                    theFile.getName(),
                                    theFile.getParentFile().getAbsolutePath(),
                                    Instant.ofEpochMilli( theFile.lastModified() ),
                                    theFile.length(),
                                    crc32( theFile )
                            );
                            taskData.getByteCount().addAndGet( fileSummaryInformation.getSize() );
                            taskData.getFileCount().incrementAndGet();
                            taskData.getOutputQueue().offer( fileSummaryInformation );
                        }
                    }
                    catch ( final Exception e )
                    {
                        LOGGER.debug( () -> "error executing file summary reader: " + e.getMessage() );
                    }
                }
            }
            finally
            {
                this.taskData.getWorkInProgress().decrementAndGet();
            }
        }
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
            catch ( final IOException e )
            {
                LOGGER.warn( () -> "error deleting temporary file '" + path.getAbsolutePath() + "', error: " + e.getMessage() );
            }
        }
    }

    private static long crc32( final File file )
            throws IOException
    {
        final CRC32 crc32 = new CRC32();
        final FileChannel fileChannel = FileChannel.open( file.toPath() );
        final int bufferSize = (int) Math.min( file.length(), CRC_BUFFER_SIZE );
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect( bufferSize );

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
