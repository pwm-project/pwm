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

import lombok.Value;
import org.apache.commons.io.IOUtils;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.TransactionSizeCalculator;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.MovingAverage;
import password.pwm.util.java.Percent;
import password.pwm.util.java.PwmNumberFormat;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.text.DecimalFormat;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;

/**
 * @author Jason D. Rivard
 */
class WordlistImporter implements Runnable
{
    private final WordlistZipReader zipFileReader;
    private final WordlistSourceType sourceType;
    private final AbstractWordlist rootWordlist;

    private final TransactionSizeCalculator transactionCalculator;
    private final Set<String> bufferedWords = new TreeSet<>();
    private final WordlistBucket wordlistBucket;
    private final WordlistSourceInfo wordlistSourceInfo;
    private final BooleanSupplier cancelFlag;
    private final ImportStatistics importStatistics = new ImportStatistics();

    private long charsInBuffer;
    private ErrorInformation exitError;
    private Instant startTime = Instant.now();
    private long bytesSkipped;
    private Map<WordType, Long> seenWordTypes = new HashMap<>();
    private boolean completed;

    private enum DebugKey
    {
        LinesRead,
        BytesRead,
        BytesRemaining,
        BytesSkipped,
        BytesPerSecond,
        PercentComplete,
        ImportTime,
        EstimatedRemainingTime,
        WordsImported,
        DiskFreeSpace,
        ZipFile,
        WordTypes,
        WordsPerTxn,
        CharsPerTxn,
        ChunksPerWord,
        AvgWordLength,
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
        catch ( final PwmUnrecoverableException e )
        {
            errorMsg = "error during import: " + e.getErrorInformation().getDetailedErrorMsg();
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

    private void initImportProcess( )
            throws PwmUnrecoverableException
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

        checkWordlistSpaceRemaining();

        final long previousBytesRead = rootWordlist.readWordlistStatus().getBytes();
        seenWordTypes.putAll( rootWordlist.readWordlistStatus().getWordTypes() );

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
            throws PwmUnrecoverableException
    {
        rootWordlist.setActivity( Wordlist.Activity.Importing );

        final ConditionalTaskExecutor metaUpdater = new ConditionalTaskExecutor(
                this::writeCurrentWordlistStatus,
                new ConditionalTaskExecutor.TimeDurationPredicate( TimeDuration.SECONDS_10 )
        );

        final ConditionalTaskExecutor debugOutputter = new ConditionalTaskExecutor(
                () -> getLogger().debug( this::makeStatString ),
                new ConditionalTaskExecutor.TimeDurationPredicate( AbstractWordlist.DEBUG_OUTPUT_FREQUENCY )
        );

        try
        {
            debugOutputter.conditionallyExecuteTask();

            initImportProcess();

            startTime = Instant.now();

            getLogger().debug( () -> "beginning import: " + JsonUtil.serialize( rootWordlist.readWordlistStatus() ) );

            String line;
            do
            {
                line = zipFileReader.nextLine();
                if ( line != null )
                {
                    addLine( line );

                    debugOutputter.conditionallyExecuteTask();

                    if (
                            bufferedWords.size() > transactionCalculator.getTransactionSize()
                                    || charsInBuffer > rootWordlist.getConfiguration().getImportMaxChars()
                    )
                    {
                        flushBuffer();
                        metaUpdater.conditionallyExecuteTask();
                        checkWordlistSpaceRemaining();
                    }
                }
            }
            while ( !cancelFlag.getAsBoolean() && line != null );


            if ( cancelFlag.getAsBoolean() )
            {
                getLogger().warn( () -> "pausing import" );
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

    private void addLine( final String input )
    {
        if ( StringUtil.isEmpty( input ) )
        {
            return;
        }

        for ( final String commentPrefix : rootWordlist.getConfiguration().getCommentPrefixes() )
        {
            if ( input.startsWith( commentPrefix ) )
            {
                return;
            }
        }

        final WordType wordType = WordType.determineWordType( input );
        seenWordTypes.computeIfAbsent( wordType, wordType1 -> 0L );
        seenWordTypes.put( wordType, seenWordTypes.get( wordType ) + 1L );

        if ( wordType == WordType.RAW )
        {
            final Optional<String> word = WordlistUtil.normalizeWordLength( input, rootWordlist.getConfiguration() );
            if ( word.isPresent() )
            {
                final String normalizedWord = wordType.convertInputFromWordlist( this.rootWordlist.getConfiguration(), word.get() );
                final Set<String> words = WordlistUtil.chunkWord( normalizedWord, rootWordlist.getConfiguration().getCheckSize() );
                importStatistics.getAverageWordLength().update( normalizedWord.length() );
                importStatistics.getChunksPerWord().update( words.size() );
                incrementCharBufferCounter( words );
                bufferedWords.addAll( words );
            }
        }
        else
        {
            final String normalizedWord = wordType.convertInputFromWordlist( this.rootWordlist.getConfiguration(), input );
            incrementCharBufferCounter( Collections.singleton( normalizedWord ) );
            bufferedWords.add( normalizedWord );
        }
    }

    private void incrementCharBufferCounter( final Collection<String> words )
    {
        for ( final String word : words )
        {
            charsInBuffer += word.length();
        }
    }

    private void flushBuffer( )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        //add the elements
        wordlistBucket.addWords( bufferedWords, rootWordlist );

        if ( cancelFlag.getAsBoolean() )
        {
            return;
        }

        //mark how long the buffer close took
        final TimeDuration commitTime = TimeDuration.fromCurrent( startTime );
        transactionCalculator.recordLastTransactionDuration( commitTime );

        importStatistics.getWordsPerTransaction().update( bufferedWords.size() );
        importStatistics.getCharsPerTransaction().update( charsInBuffer );

        //clear the buffers.
        bufferedWords.clear();
        charsInBuffer = 0;
    }

    private void populationComplete( )
            throws PwmUnrecoverableException
    {
        flushBuffer();
        getLogger().info( this::makeStatString );
        final long wordlistSize = wordlistBucket.size();

        getLogger().info( () -> "population complete, added " + wordlistSize
                + " total words in " + TimeDuration.compactFromCurrent( startTime ) );

        completed = true;
        writeCurrentWordlistStatus();

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
        final Instant startSkipTime = Instant.now();

        if ( previousBytesRead > 0 )
        {
            final ConditionalTaskExecutor debugOutputter = ConditionalTaskExecutor.forPeriodicTask(
                    () -> getLogger().debug( () -> "continuing skipping forward in wordlist, "
                            + StringUtil.formatDiskSizeforDebug( zipFileReader.getByteCount() )
                            + " of " + StringUtil.formatDiskSizeforDebug( previousBytesRead )
                            + " (" + TimeDuration.compactFromCurrent( startSkipTime ) + ")" ),
                    AbstractWordlist.DEBUG_OUTPUT_FREQUENCY );


            getLogger().debug( () -> "will skip forward " + StringUtil.formatDiskSizeforDebug( previousBytesRead ) + " in wordlist that has been previously imported" );
            while ( !cancelFlag.getAsBoolean() && bytesSkipped < previousBytesRead )
            {
                zipFileReader.nextLine();
                bytesSkipped = zipFileReader.getByteCount();
                debugOutputter.conditionallyExecuteTask();
            }
            getLogger().debug( () -> "skipped forward " + StringUtil.formatDiskSizeforDebug( previousBytesRead )
                    + " in stream (" + TimeDuration.fromCurrent( startSkipTime ).asCompactString() + ")" );
        }
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
            catch ( final Exception e )
            {
                getLogger().error( () -> "error calculating import statistics: " + e.getMessage() );

                /* ignore - it's a long overflow if the estimate is off */
            }

            final Percent percent = new Percent( zipFileReader.getByteCount(), wordlistSourceInfo.getBytes() );
            stats.put( DebugKey.PercentComplete, percent.pretty( 2 ) );
        }

        stats.put( DebugKey.LinesRead, PwmNumberFormat.forDefaultLocale().format( zipFileReader.getLineCount() ) );
        stats.put( DebugKey.BytesRead, StringUtil.formatDiskSizeforDebug( zipFileReader.getByteCount() ) );

        stats.put( DebugKey.WordsPerTxn, PwmNumberFormat.forDefaultLocale().format( (long) importStatistics.getWordsPerTransaction().getAverage() ) );
        stats.put( DebugKey.CharsPerTxn, PwmNumberFormat.forDefaultLocale().format( (long) importStatistics.getCharsPerTransaction().getAverage() ) );

        stats.put( DebugKey.DiskFreeSpace, StringUtil.formatDiskSize( wordlistBucket.spaceRemaining() ) );

        if ( bytesSkipped > 0 )
        {
            stats.put( DebugKey.BytesSkipped, StringUtil.formatDiskSizeforDebug( bytesSkipped ) );
        }

        stats.put( DebugKey.ImportTime, TimeDuration.fromCurrent( startTime ).asCompactString() );
        stats.put( DebugKey.ZipFile, zipFileReader.currentZipName() );
        stats.put( DebugKey.WordTypes, JsonUtil.serializeMap( seenWordTypes ) );

        if ( importStatistics.getChunksPerWord().getAverage() > 1 )
        {
            final DecimalFormat decimalFormat = new DecimalFormat( "#.##" );
            stats.put( DebugKey.ChunksPerWord, decimalFormat.format( importStatistics.getChunksPerWord().getAverage() ) );
        }

        if ( importStatistics.getAverageWordLength().getAverage() > 1 )
        {
            final DecimalFormat decimalFormat = new DecimalFormat( "#.##" );
            stats.put( DebugKey.AvgWordLength, decimalFormat.format( importStatistics.getAverageWordLength().getAverage() ) );
        }

        try
        {
            stats.put( DebugKey.WordsImported, PwmNumberFormat.forDefaultLocale().format( wordlistBucket.size() ) );
        }
        catch ( final PwmUnrecoverableException e )
        {
            getLogger().debug( () -> "error while calculating wordsImported stat during wordlist import: " + e.getMessage() );
        }

        return Collections.unmodifiableMap( stats );
    }

    private void writeCurrentWordlistStatus()
    {
        final Instant now = Instant.now();
        rootWordlist.writeWordlistStatus( rootWordlist.readWordlistStatus().toBuilder()
                .remoteInfo( wordlistSourceInfo )
                .configHash( rootWordlist.getConfiguration().configHash() )
                .storeDate( now )
                .checkDate( now )
                .sourceType( sourceType )
                .completed( completed )
                .wordTypes( new HashMap<>( seenWordTypes ) )
                .bytes( zipFileReader.getByteCount() )
                .build() );
    }

    @Value
    private static class ImportStatistics
    {
        private final MovingAverage charsPerTransaction = new MovingAverage( TimeDuration.MINUTE );
        private final MovingAverage wordsPerTransaction = new MovingAverage( TimeDuration.MINUTE );
        private final MovingAverage chunksPerWord = new MovingAverage( TimeDuration.MINUTE );
        private final MovingAverage averageWordLength = new MovingAverage( TimeDuration.MINUTE );
    }

    private void checkWordlistSpaceRemaining()
            throws PwmUnrecoverableException
    {
        final long freeSpace = wordlistBucket.spaceRemaining();
        final long minFreeSpace = rootWordlist.getConfiguration().getImportMinFreeSpace();
        if ( freeSpace < minFreeSpace )
        {
            final String msg = "free space remaining for wordlist storage is " + StringUtil.formatDiskSizeforDebug( freeSpace )
                    + " which is less than the minimum of "
                    + StringUtil.formatDiskSizeforDebug( minFreeSpace )
                    + ", aborting import";

            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_WORDLIST_IMPORT_ERROR, msg );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }
}
