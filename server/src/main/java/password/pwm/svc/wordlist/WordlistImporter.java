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

package password.pwm.svc.wordlist;

import org.apache.commons.io.IOUtils;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.TransactionSizeCalculator;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.Percent;
import password.pwm.util.java.PwmNumberFormat;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;

/**
 * @author Jason D. Rivard
 */
class WordlistImporter implements Runnable
{
    // words tarting with this prefix are ignored.
    private static final String COMMENT_PREFIX = "!#comment:";

    private final WordlistZipReader zipFileReader;
    private final WordlistSourceType sourceType;
    private final TransactionSizeCalculator transactionCalculator;
    private final Set<String> bufferedWords = new TreeSet<>();
    private final WordlistBucket wordlistBucket;
    private final AbstractWordlist rootWordlist;
    private final WordlistSourceInfo wordlistSourceInfo;
    private final BooleanSupplier cancelFlag;

    private ErrorInformation exitError;
    private Instant startTime = Instant.now();
    private long bytesSkipped;

    private enum DebugKey
    {
        LinesRead,
        BytesRead,
        BytesRemaining,
        BufferSize,
        BytesSkipped,
        BytesPerSecond,
        PercentComplete,
        ImportTime,
        EstimatedRemainingTime,
    }

    WordlistImporter(
            final WordlistSourceInfo wordlistSourceInfo,
            final WordlistZipReader wordlistZipReader,
            final WordlistSourceType sourceType,
            final AbstractWordlist rootWordlist,
            final BooleanSupplier cancelFlag
    )
    {
        this.wordlistSourceInfo = wordlistSourceInfo;
        this.sourceType = sourceType;
        this.zipFileReader = wordlistZipReader;
        this.rootWordlist = rootWordlist;
        this.cancelFlag = cancelFlag;
        this.wordlistBucket = rootWordlist.getWordlistBucket();

        final WordlistConfiguration wordlistConfiguration = rootWordlist.getConfiguration();

        transactionCalculator = new TransactionSizeCalculator(
                TransactionSizeCalculator.Settings.builder()
                        .durationGoal( wordlistConfiguration.getImportDurationGoal() )
                        .minTransactions( wordlistConfiguration.getImportMinTransactions() )
                        .maxTransactions( wordlistConfiguration.getImportMaxTransactions() )
                        .build()
        );

    }

    @Override
    public void run()
    {
        String errorMsg = null;
        try
        {
            doImport();
        }
        catch ( PwmUnrecoverableException e )
        {
            errorMsg = "error during import: " + e.getErrorInformation().getDetailedErrorMsg();
        }
        catch ( LocalDBException e )
        {
            errorMsg = "localDB error during import: " + e.getMessage();
        }

        if ( errorMsg != null )
        {
            exitError = new ErrorInformation( PwmError.ERROR_WORDLIST_IMPORT_ERROR, errorMsg, new String[]
                    {
                            errorMsg,
                    }
            );
        }

        if ( cancelFlag.getAsBoolean() )
        {
            getLogger().debug( () -> "exiting import due to cancel flag" );
        }
    }

    private void init( )
            throws PwmUnrecoverableException,
            LocalDBException
    {
        if ( cancelFlag.getAsBoolean() )
        {
            return;
        }

        if ( wordlistSourceInfo == null || !wordlistSourceInfo.equals( rootWordlist.readWordlistStatus().getRemoteInfo() ) )
        {
            rootWordlist.writeWordlistStatus( WordlistStatus.builder()
                    .sourceType( sourceType )
                    .build() );
        }

        final long previousBytesRead = rootWordlist.readWordlistStatus().getBytes();

        if ( previousBytesRead == 0 )
        {
            rootWordlist.clearImpl( Wordlist.Activity.Importing );
        }
        else if ( previousBytesRead > 0 )
        {
            skipForward( previousBytesRead );
        }
    }

    private void doImport( )
            throws LocalDBException, PwmUnrecoverableException
    {
        rootWordlist.setActivity( Wordlist.Activity.Importing );

        final ConditionalTaskExecutor metaUpdater = new ConditionalTaskExecutor(
                () -> rootWordlist.writeWordlistStatus( rootWordlist.readWordlistStatus().toBuilder()
                        .sourceType( sourceType )
                        .storeDate( Instant.now() )
                        .remoteInfo( wordlistSourceInfo )
                        .bytes( zipFileReader.getByteCount() )
                        .build() ),
                new ConditionalTaskExecutor.TimeDurationPredicate( TimeDuration.SECONDS_10 )
        );

        final ConditionalTaskExecutor debugOutputter = new ConditionalTaskExecutor(
                () -> getLogger().debug( this::makeStatString ),
                new ConditionalTaskExecutor.TimeDurationPredicate( AbstractWordlist.DEBUG_OUTPUT_FREQUENCY )
        );

        try
        {
            debugOutputter.conditionallyExecuteTask();

            init();

            startTime = Instant.now();

            getLogger().debug( () -> "beginning import" );

            String line;
            do
            {
                line = zipFileReader.nextLine();
                if ( line != null )
                {
                    addLine( line );

                    debugOutputter.conditionallyExecuteTask();

                    if ( bufferedWords.size() > transactionCalculator.getTransactionSize() )
                    {
                        flushBuffer();
                        metaUpdater.conditionallyExecuteTask();
                    }
                }
            }
            while ( !cancelFlag.getAsBoolean() && line != null );


            if ( cancelFlag.getAsBoolean() )
            {
                getLogger().warn( "pausing import" );
            }
            else
            {
                populationComplete();
            }
        }
        finally
        {
            IOUtils.closeQuietly( zipFileReader );
        }
    }

    private void addLine( final String word )
    {

        if ( StringUtil.isEmpty( word ) || word.startsWith( COMMENT_PREFIX ) )
        {
            return;
        }

        bufferedWords.add( word );
    }

    private void flushBuffer( )
            throws LocalDBException
    {
        final long startTime = System.currentTimeMillis();

        //add the elements
        wordlistBucket.addWords( bufferedWords, rootWordlist );

        if ( cancelFlag.getAsBoolean() )
        {
            return;
        }

        //mark how long the buffer close took
        final long commitTime = System.currentTimeMillis() - startTime;
        transactionCalculator.recordLastTransactionDuration( commitTime );

        //clear the buffers.
        bufferedWords.clear();
    }

    private void populationComplete( )
            throws LocalDBException
    {
        flushBuffer();
        getLogger().info( () -> makeStatString() );
        getLogger().trace( () -> "beginning wordlist size query" );
        final long wordlistSize = wordlistBucket.size();

        getLogger().info( () -> "population complete, added " + wordlistSize
                + " total words in " + TimeDuration.compactFromCurrent( startTime ) );

        rootWordlist.writeWordlistStatus( rootWordlist.readWordlistStatus().toBuilder()
                .remoteInfo( wordlistSourceInfo )
                .storeDate( Instant.now() )
                .sourceType( sourceType )
                .completed( true )
                .bytes( zipFileReader.getByteCount() )
                .build()
        );

        getLogger().debug( () -> "final post-population status: " + JsonUtil.serialize( rootWordlist.readWordlistStatus() ) );
    }

    private PwmLogger getLogger()
    {
        return this.rootWordlist.getLogger();
    }

    ErrorInformation getExitError()
    {
        return exitError;
    }

    private void skipForward( final long previousBytesRead )
            throws PwmUnrecoverableException
    {
        final Instant startSkip = Instant.now();
        final ConditionalTaskExecutor debugOutputter = new ConditionalTaskExecutor(
                () -> getLogger().debug( () -> "continuing skipping forward in wordlist"
                        + ", " + StringUtil.formatDiskSizeforDebug( zipFileReader.getByteCount() )
                        + " of " + StringUtil.formatDiskSizeforDebug( previousBytesRead )
                        + " (" + TimeDuration.compactFromCurrent( startSkip ) + ")" ),
                new ConditionalTaskExecutor.TimeDurationPredicate( AbstractWordlist.DEBUG_OUTPUT_FREQUENCY )
        );

        getLogger().debug( () -> "will skip forward " + StringUtil.formatDiskSizeforDebug( previousBytesRead ) + " in stream that have been previously imported" );
        while ( !cancelFlag.getAsBoolean() && bytesSkipped < ( previousBytesRead + 1024 ) )
        {
            zipFileReader.nextLine();
            bytesSkipped = zipFileReader.getByteCount();
            debugOutputter.conditionallyExecuteTask();
        }
        getLogger().debug( () -> "skipped forward " + StringUtil.formatDiskSizeforDebug( previousBytesRead )
                + " in stream (" + TimeDuration.fromCurrent( startSkip ).asCompactString() + ")" );
    }

    private String makeStatString()
    {
        return StringUtil.mapToString( makeStatValues(), "=", ", " );
    }

    private Map<DebugKey, String> makeStatValues()
    {
        final Map<DebugKey, String> stats = new TreeMap<>();

        if ( wordlistSourceInfo != null )
        {
            final long totalBytes = wordlistSourceInfo.getBytes();
            final long remainingBytes = totalBytes - zipFileReader.getByteCount();
            stats.put( DebugKey.BytesRemaining, StringUtil.formatDiskSizeforDebug( remainingBytes ) );

            try
            {
                if ( zipFileReader.getByteCount() > 1000 && TimeDuration.fromCurrent( startTime ).isLongerThan( TimeDuration.MINUTE ) )
                {
                    final long bytesSinceStart = zipFileReader.getByteCount() - bytesSkipped;
                    final long elapsedSeconds = TimeDuration.fromCurrent( startTime ).as( TimeDuration.Unit.SECONDS );

                    if ( elapsedSeconds > 0 )
                    {
                        final long bytesPerSecond = bytesSinceStart / elapsedSeconds;
                        stats.put( DebugKey.BytesPerSecond, StringUtil.formatDiskSizeforDebug( bytesPerSecond ) );

                        if ( remainingBytes > 0 )
                        {
                            final long remainingSeconds = remainingBytes / bytesPerSecond;
                            stats.put( DebugKey.EstimatedRemainingTime, TimeDuration.of( remainingSeconds, TimeDuration.Unit.SECONDS ).asCompactString() );
                        }
                    }
                }
            }
            catch ( Exception e )
            {
                getLogger().error( "error calculating " );

                /* ignore - it's a long overflow if the estimate is off */
            }

            final Percent percent = new Percent( zipFileReader.getByteCount(), wordlistSourceInfo.getBytes() );
            stats.put( DebugKey.PercentComplete, percent.pretty( 2 ) );
        }

        stats.put( DebugKey.LinesRead, PwmNumberFormat.forDefaultLocale().format( zipFileReader.getLineCount() ) );
        stats.put( DebugKey.BytesRead, StringUtil.formatDiskSizeforDebug( zipFileReader.getByteCount() ) );

        stats.put( DebugKey.BufferSize, PwmNumberFormat.forDefaultLocale().format( transactionCalculator.getTransactionSize() ) );

        if ( bytesSkipped > 0 )
        {
            stats.put( DebugKey.BytesSkipped, StringUtil.formatDiskSizeforDebug( bytesSkipped ) );
        }

        stats.put( DebugKey.ImportTime, TimeDuration.fromCurrent( startTime ).asCompactString() );

        return Collections.unmodifiableMap( stats );
    }

}
