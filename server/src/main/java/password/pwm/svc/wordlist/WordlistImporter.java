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

package password.pwm.svc.wordlist;

import org.apache.commons.io.IOUtils;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.stats.EventRateMeter;
import password.pwm.util.TransactionSizeCalculator;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.Percent;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * @author Jason D. Rivard
 */
class WordlistImporter implements Runnable
{
    private static final TimeDuration DEBUG_OUTPUT_FREQUENCY = TimeDuration.SECONDS_30;

    // words tarting with this prefix are ignored.
    private static final String COMMENT_PREFIX = "!#comment:";

    private static final NumberFormat PERCENT_FORMAT = DecimalFormat.getPercentInstance();

    private final WordlistZipReader zipFileReader;
    private final WordlistSourceType sourceType;

    private TransactionSizeCalculator transactionCalculator = new TransactionSizeCalculator(
            new TransactionSizeCalculator.SettingsBuilder()
                    .setDurationGoal( TimeDuration.of( 600, TimeDuration.Unit.MILLISECONDS ) )
                    .setMinTransactions( 10 )
                    .setMaxTransactions( 350 * 1000 )
                    .createSettings()
    );

    private final Set<String> bufferedWords = new HashSet<>();

    private final WordlistBucket wordlistBucket;

    private final AbstractWordlist rootWordlist;

    private final WordlistSourceInfo wordlistSourceInfo;

    private final Instant startTime = Instant.now();

    private final AtomicLong bytesSkipped = new AtomicLong( 0 );

    private final EventRateMeter.MovingAverage byteRateMeter
            = new EventRateMeter.MovingAverage( TimeDuration.MINUTE );

    private final BooleanSupplier cancelFlag;

    private ErrorInformation exitError;


    static
    {
        PERCENT_FORMAT.setMinimumFractionDigits( 2 );
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
    }

    @Override
    public void run()
    {
        String errorMsg = null;
        try
        {
            populate();
        }
        catch ( IOException e )
        {
            errorMsg = "i/o error during import: " + e.getMessage();
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
            getLogger().debug( "exiting import due to cancel flag" );
        }
    }

    private void init( ) throws PwmUnrecoverableException, LocalDBException
    {
        if ( cancelFlag.getAsBoolean() )
        {
            return;
        }

        if ( wordlistSourceInfo == null || !wordlistSourceInfo.equals( rootWordlist.readWordlistStatus().getRemoteInfo() ) )
        {
            rootWordlist.writeMetadata( WordlistStatus.builder()
                    .sourceType( sourceType )
                    .build() );
        }

        final long previousBytesRead = rootWordlist.readWordlistStatus().getBytes();

        if ( previousBytesRead == 0 )
        {
            getLogger().debug( "clearing stored wordlist" );
            wordlistBucket.clear();
            rootWordlist.writeMetadata( WordlistStatus.builder().build() );
        }

        if ( previousBytesRead > 0 )
        {
            final Instant startSkip = Instant.now();
            getLogger().debug( "skipping forward " + previousBytesRead + " bytes in stream that have been previously imported" );
            while ( !cancelFlag.getAsBoolean() && bytesSkipped.get() < previousBytesRead )
            {
                zipFileReader.nextLine();
                bytesSkipped.set( zipFileReader.getByteCount() );
            }
            getLogger().debug( "skipped forward " + previousBytesRead + " bytes in stream (" + TimeDuration.fromCurrent( startSkip ).asCompactString() + ")" );
        }
    }

    private Map<String, String> makeStatValues()
    {
        final Map<String, String> stats = new LinkedHashMap<>();
        stats.put( "LinesRead", Long.toString( zipFileReader.getLineCount() ) );
        stats.put( "BytesRead", Long.toString( zipFileReader.getByteCount() ) );
        stats.put( "BufferSize", Integer.toString( transactionCalculator.getTransactionSize() ) );

        final long elapsedSeconds = TimeDuration.fromCurrent( startTime ).as( TimeDuration.Unit.SECONDS );

        if ( bytesSkipped.get() > 0 )
        {
            stats.put( "BytesSkipped", Long.toString( bytesSkipped.get() ) );
        }

        if ( elapsedSeconds > 10 )
        {
            stats.put( "BytesPerSecond", Double.toString( byteRateMeter.getAverage() * 1000 ) );
        }

        if ( wordlistSourceInfo != null )
        {
            final Percent percent = new Percent( zipFileReader.getByteCount(), wordlistSourceInfo.getBytes() );
            stats.put( "PercentComplete", percent.pretty( 2 ) );
        }

        stats.put( "ImportTime", TimeDuration.fromCurrent( startTime ).asCompactString() );

        try
        {
            if ( wordlistSourceInfo != null && zipFileReader.getByteCount() > 1000 )
            {
                final long totalBytes = wordlistSourceInfo.getBytes();
                final long remainingBytes = totalBytes - zipFileReader.getByteCount();
                final double bytesPerSecond = byteRateMeter.getAverage() * 1000;
                final long remainingSeconds = (long) ( remainingBytes / bytesPerSecond );

                getLogger().trace( "splat remainingSeconds=" + remainingSeconds );

                stats.put( "EstimatedRemainingTime", TimeDuration.of( remainingSeconds, TimeDuration.Unit.SECONDS ).asCompactString() );
            }
        }
        catch ( Exception e )
        {
            getLogger().error( "error calculating wordlist remaining seconds: " + e.getMessage() );
        }

        return Collections.unmodifiableMap( stats );
    }

    private void populate( ) throws IOException, LocalDBException, PwmUnrecoverableException
    {
        final ConditionalTaskExecutor metaUpdater = new ConditionalTaskExecutor(
                () -> rootWordlist.writeMetadata( WordlistStatus.builder()
                        .sourceType( sourceType )
                        .storeDate( Instant.now() )
                        .remoteInfo( wordlistSourceInfo )
                        .bytes( zipFileReader.getByteCount() )
                        .build() ),
                new ConditionalTaskExecutor.TimeDurationPredicate( TimeDuration.SECONDS_10 )
        );

        final ConditionalTaskExecutor debugOutputter = new ConditionalTaskExecutor(
                () -> getLogger().debug( makeStatString() ),
                new ConditionalTaskExecutor.TimeDurationPredicate( DEBUG_OUTPUT_FREQUENCY )
        );

        try
        {
            debugOutputter.conditionallyExecuteTask();

            init();

            long lastBytes = zipFileReader.getByteCount();

            String line;
            do
            {
                line = zipFileReader.nextLine();
                if ( line != null )
                {
                    addLine( line );

                    debugOutputter.conditionallyExecuteTask();
                    final long cycleBytes = zipFileReader.getByteCount() - lastBytes;
                    lastBytes = zipFileReader.getByteCount();
                    byteRateMeter.update( cycleBytes );

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
                getLogger().warn( "pausing population" );
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
        wordlistBucket.addWords( bufferedWords );

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

    private String makeStatString()
    {
        return StringUtil.mapToString( makeStatValues() );
    }

    private void populationComplete( )
            throws LocalDBException
    {
        flushBuffer();
        getLogger().info( makeStatString() );
        getLogger().trace( "beginning wordlist size query" );
        final int wordlistSize = wordlistBucket.size();

        final String logMsg = "population complete, added " + wordlistSize
                + " total words in " + TimeDuration.fromCurrent( startTime ).asCompactString();
        getLogger().info( logMsg );

        {
            final WordlistStatus wordlistStatus = WordlistStatus.builder()
                    .remoteInfo( wordlistSourceInfo )
                    .storeDate( Instant.now() )
                    .sourceType( sourceType )
                    .completed( true )
                    .bytes( zipFileReader.getByteCount() )
                    .build();
            rootWordlist.writeMetadata( wordlistStatus );
        }
    }

    private PwmLogger getLogger()
    {
        return this.rootWordlist.getLogger();
    }

    public ErrorInformation getExitError()
    {
        return exitError;
    }
}
