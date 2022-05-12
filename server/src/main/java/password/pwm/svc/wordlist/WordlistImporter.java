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

package password.pwm.svc.wordlist;

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.TransactionSizeCalculator;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.MiscUtil;
import password.pwm.util.java.Percent;
import password.pwm.util.java.StatisticAverageBundle;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.LongAdder;
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
    private final StatisticAverageBundle<StatKey> importStatistics = new StatisticAverageBundle<>( StatKey.class );
    private final ConditionalTaskExecutor pauseTimer;

    private long charsInBuffer;
    private ErrorInformation exitError;
    private Instant startTime = Instant.now();
    private long bytesSkipped;
    private TimeDuration previousImportDuration;
    private final Map<WordType, LongAdder> seenWordTypes = new EnumMap<>( WordType.class );
    private boolean completed;

    private enum StatKey
    {
        charsPerTransaction( DebugKey.CharsPerTxn ),
        wordsPerTransaction( DebugKey.WordsPerTxn ),
        chunksPerWord( DebugKey.ChunksPerWord ),
        averageWordLength( DebugKey.AvgWordLength ),
        msPerTransaction( DebugKey.MsPerTxn ),;

        private final DebugKey debugKey;

        StatKey( final DebugKey debugKey )
        {
            this.debugKey = debugKey;
        }

        public DebugKey getDebugKey()
        {
            return debugKey;
        }
    }

    private enum DebugKey
    {
        LinesRead,
        BytesRead,
        BytesRemaining,
        BytesSkipped,
        BytesPerSecond,
        PercentComplete,
        ImportDuration,
        EstimatedRemainingTime,
        WordsImported,
        DiskFreeSpace,
        ZipFile,
        WordTypes,
        MsPerTxn,
        WordsPerTxn,
        ChunksSaved,
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

        this.transactionCalculator = new TransactionSizeCalculator(
                TransactionSizeCalculator.Settings.builder()
                        .durationGoal( wordlistConfiguration.getImportDurationGoal() )
                        .minTransactions( wordlistConfiguration.getImportMinTransactions() )
                        .maxTransactions( wordlistConfiguration.getImportMaxTransactions() )
                        .build()
        );

        {
            final TimeDuration pauseDuration = wordlistConfiguration.getImportPauseDuration();
            this.pauseTimer = ConditionalTaskExecutor.forPeriodicTask(
                    pauseDuration::pause,
                    wordlistConfiguration.getImportPauseFrequency().asDuration() );
        }
    }

    @Override
    public void run()
    {
        String errorMsg = null;
        try
        {
            doImport();
        }
        catch ( final CancellationException e )
        {
            getLogger().debug( rootWordlist.getSessionLabel(), () -> "stopped import due to cancel flag" );
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
    }

    private void cancelCheck()
    {
        if ( cancelFlag.getAsBoolean() )
        {
            throw new CancellationException();
        }
    }

    private void initImportProcess( )
            throws PwmUnrecoverableException
    {
        cancelCheck();

        if ( wordlistSourceInfo == null || !wordlistSourceInfo.equals( rootWordlist.readWordlistStatus().getRemoteInfo() ) )
        {
            rootWordlist.writeWordlistStatus( WordlistStatus.builder()
                    .sourceType( sourceType )
                    .build() );
        }

        checkWordlistSpaceRemaining();

        previousImportDuration = TimeDuration.of( rootWordlist.readWordlistStatus().getImportMs(), TimeDuration.Unit.MILLISECONDS );

        final long previousBytesRead = rootWordlist.readWordlistStatus().getBytes();

        for ( final Map.Entry<WordType, Long> entry : rootWordlist.readWordlistStatus().getWordTypes().entrySet() )
        {
            final LongAdder longAdder = new LongAdder();
            longAdder.add( entry.getValue() );
            seenWordTypes.put( entry.getKey(), longAdder );
        }

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

        final ConditionalTaskExecutor metaUpdater = ConditionalTaskExecutor.forPeriodicTask(
                this::writeCurrentWordlistStatus,
                TimeDuration.SECONDS_10.asDuration() );

        final ConditionalTaskExecutor debugOutputter = ConditionalTaskExecutor.forPeriodicTask(
                () -> getLogger().debug( rootWordlist.getSessionLabel(), this::makeStatString ),
                AbstractWordlist.DEBUG_OUTPUT_FREQUENCY.asDuration() );

        try
        {
            debugOutputter.conditionallyExecuteTask();

            initImportProcess();

            startTime = Instant.now();

            getLogger().debug( rootWordlist.getSessionLabel(), () -> "beginning import: " + JsonFactory.get().serialize( rootWordlist.readWordlistStatus() ) );
            Instant lastTxnInstant = Instant.now();

            final long importMaxChars = rootWordlist.getConfiguration().getImportMaxChars();

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
                                    || charsInBuffer > importMaxChars
                    )
                    {
                        flushBuffer();
                        metaUpdater.conditionallyExecuteTask();
                        checkWordlistSpaceRemaining();

                        importStatistics.update( StatKey.msPerTransaction, TimeDuration.fromCurrent( lastTxnInstant ).asMillis() );
                        pauseTimer.conditionallyExecuteTask();
                        lastTxnInstant = Instant.now();
                    }

                    cancelCheck();
                }
            }
            while ( line != null );

            cancelCheck();
            populationComplete();
        }
        finally
        {
            JavaHelper.closeQuietly( zipFileReader );
        }
    }

    private void addLine( final String input )
    {
        if ( StringUtil.isEmpty( input ) )
        {
            return;
        }

        if ( checkIfCommentLine( input ) )
        {
            return;
        }

        final WordType wordType = WordType.determineWordType( input );
        seenWordTypes.computeIfAbsent( wordType, t -> new LongAdder() ).increment();

        if ( wordType == WordType.RAW )
        {
            WordlistUtil.normalizeWordLength( input, rootWordlist.getConfiguration() ).ifPresent( word ->
            {
                final String normalizedWord = wordType.convertInputFromWordlist( this.rootWordlist.getConfiguration(), word );
                final Set<String> words = WordlistUtil.chunkWord( normalizedWord, rootWordlist.getConfiguration().getCheckSize() );
                importStatistics.update( StatKey.averageWordLength, normalizedWord.length() );
                importStatistics.update( StatKey.chunksPerWord, words.size() );
                incrementCharBufferCounter( words );
                bufferedWords.addAll( words );
            } );
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

    private boolean checkIfCommentLine( final String input )
    {
        for ( final String commentPrefix : rootWordlist.getConfiguration().getCommentPrefixes() )
        {
            if ( input.startsWith( commentPrefix ) )
            {
                return true;
            }
        }

        return false;
    }

    private void flushBuffer( )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        //add the elements
        wordlistBucket.addWords( bufferedWords, rootWordlist );

        cancelCheck();

        //mark how long the buffer close took
        final TimeDuration commitTime = TimeDuration.fromCurrent( startTime );
        transactionCalculator.recordLastTransactionDuration( commitTime );

        importStatistics.update( StatKey.wordsPerTransaction, bufferedWords.size() );
        importStatistics.update( StatKey.charsPerTransaction, charsInBuffer );

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

        getLogger().info( rootWordlist.getSessionLabel(), () -> "population complete, added " + wordlistSize
                + " total words", this::getImportDuration );

        completed = true;
        writeCurrentWordlistStatus();

        getLogger().debug( rootWordlist.getSessionLabel(), () -> "final post-population status: " + JsonFactory.get().serialize( rootWordlist.readWordlistStatus() ) );
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
                    () -> getLogger().debug( rootWordlist.getSessionLabel(), () -> "continuing skipping forward in wordlist: "
                            + StringUtil.formatDiskSizeforDebug( zipFileReader.getByteCount() )
                            + " of " + StringUtil.formatDiskSizeforDebug( previousBytesRead )
                            + " (" + TimeDuration.compactFromCurrent( startSkipTime ) + ")" ),
                    AbstractWordlist.DEBUG_OUTPUT_FREQUENCY.asDuration() );


            getLogger().debug( rootWordlist.getSessionLabel(), () -> "will skip forward " + StringUtil.formatDiskSizeforDebug( previousBytesRead )
                    + " in wordlist that has been previously imported" );

            while ( bytesSkipped < previousBytesRead )
            {
                zipFileReader.nextLine();
                bytesSkipped = zipFileReader.getByteCount();
                debugOutputter.conditionallyExecuteTask();
                cancelCheck();
            }
            getLogger().debug( rootWordlist.getSessionLabel(), () -> "skipped forward " + StringUtil.formatDiskSizeforDebug( previousBytesRead )
                    + " in stream (" + TimeDuration.fromCurrent( startSkipTime ).asCompactString() + ")" );
        }
    }

    private String makeStatString()
    {
        return StringUtil.mapToString( makeStatValues() );
    }

    private Map<DebugKey, String> makeStatValues()
    {
        final Map<DebugKey, String> stats = new EnumMap<>( DebugKey.class );

        if ( wordlistSourceInfo != null )
        {
            final long totalBytes = wordlistSourceInfo.getBytes();
            final long remainingBytes = totalBytes - zipFileReader.getByteCount();

            try
            {
                if ( zipFileReader.getByteCount() > 1000 && TimeDuration.fromCurrent( startTime ).isLongerThan( TimeDuration.MINUTE ) )
                {
                    final long elapsedSeconds = TimeDuration.fromCurrent( startTime ).as( TimeDuration.Unit.SECONDS );

                    if ( elapsedSeconds > 0 )
                    {
                        final long bytesPerSecond = zipFileReader.getEventRate().longValue();
                        stats.put( DebugKey.BytesPerSecond, StringUtil.formatDiskSize( bytesPerSecond ) );

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
                /* ignore - it's a long overflow or div by zero if the estimate is off */
                getLogger().debug( rootWordlist.getSessionLabel(), () -> "error calculating import statistics: " + e.getMessage() );
            }

            stats.put( DebugKey.PercentComplete, Percent.of( zipFileReader.getByteCount(), wordlistSourceInfo.getBytes() ).pretty() );
            stats.put( DebugKey.BytesRemaining, StringUtil.formatDiskSizeforDebug( remainingBytes ) );
        }

        stats.put( DebugKey.LinesRead, MiscUtil.forDefaultLocale().format( zipFileReader.getLineCount() ) );
        stats.put( DebugKey.ChunksSaved, MiscUtil.forDefaultLocale().format( rootWordlist.size() ) );
        stats.put( DebugKey.BytesRead, StringUtil.formatDiskSizeforDebug( zipFileReader.getByteCount() ) );
        stats.put( DebugKey.DiskFreeSpace, StringUtil.formatDiskSize( wordlistBucket.spaceRemaining() ) );
        stats.put( DebugKey.ImportDuration, getImportDuration().asCompactString() );
        stats.put( DebugKey.ZipFile, zipFileReader.currentZipName() );
        stats.put( DebugKey.WordTypes, JsonFactory.get().serializeMap( seenWordTypes, WordType.class, LongAdder.class ) );

        if ( bytesSkipped > 0 )
        {
            stats.put( DebugKey.BytesSkipped, StringUtil.formatDiskSize( bytesSkipped ) );
        }

        try
        {
            stats.put( DebugKey.WordsImported, MiscUtil.forDefaultLocale().format( wordlistBucket.size() ) );
        }
        catch ( final PwmUnrecoverableException e )
        {
            getLogger().debug( rootWordlist.getSessionLabel(), () -> "error while calculating wordsImported stat during wordlist import: " + e.getMessage() );
        }

        Arrays.stream( StatKey.values() )
                .forEach( statKey -> stats.put( statKey.getDebugKey(), importStatistics.getFormattedAverage( statKey ) ) );

        return Collections.unmodifiableMap( stats );
    }

    private void writeCurrentWordlistStatus()
    {
        final Map<WordType, Long> outputWordTypeMap = new EnumMap<>( WordType.class );
        seenWordTypes.forEach( ( key, value ) -> outputWordTypeMap.put( key, value.longValue() ) );

        final Instant now = Instant.now();
        rootWordlist.writeWordlistStatus( rootWordlist.readWordlistStatus().toBuilder()
                .remoteInfo( wordlistSourceInfo )
                .configHash( rootWordlist.getConfiguration().configHash() )
                .storeDate( now )
                .checkDate( now )
                .sourceType( sourceType )
                .completed( completed )
                .wordTypes( outputWordTypeMap )
                .bytes( zipFileReader.getByteCount() )
                .importMs( getImportDuration().asMillis() )
                .build() );
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

    private TimeDuration getImportDuration()
    {
        return TimeDuration.fromCurrent( startTime ).add( previousImportDuration );
    }
}
