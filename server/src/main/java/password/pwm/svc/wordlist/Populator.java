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
import password.pwm.PwmApplication;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.stats.EventRateMeter;
import password.pwm.util.TransactionSizeCalculator;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.Percent;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Jason D. Rivard
 */
class Populator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( Populator.class );

    // words truncated to this length, prevents massive words if the input
    private static final int MAX_LINE_LENGTH = 64;

    private static final TimeDuration DEBUG_OUTPUT_FREQUENCY = TimeDuration.SECONDS_30;

    // words tarting with this prefix are ignored.
    private static final String COMMENT_PREFIX = "!#comment:";

    private static final NumberFormat PERCENT_FORMAT = DecimalFormat.getPercentInstance();

    private final WordlistZipReader zipFileReader;
    private final StoredWordlistDataBean.Source source;

    private final AtomicBoolean running = new AtomicBoolean( false );
    private final AtomicBoolean abortFlag = new AtomicBoolean( false );

    private TransactionSizeCalculator transactionCalculator = new TransactionSizeCalculator(
            new TransactionSizeCalculator.SettingsBuilder()
                    .setDurationGoal( TimeDuration.of( 600, TimeDuration.Unit.MILLISECONDS ) )
                    .setMinTransactions( 10 )
                    .setMaxTransactions( 350 * 1000 )
                    .createSettings()
    );

    private final Map<String, String> bufferedWords = new TreeMap<>();

    private final LocalDB localDB;

    private final AbstractWordlist rootWordlist;

    private final StoredWordlistDataBean.RemoteWordlistInfo remoteWordlistInfo;

    private final Instant startTime = Instant.now();

    private final AtomicLong bytesSkipped = new AtomicLong( 0 );

    private final EventRateMeter.MovingAverage byteRateMeter
            = new EventRateMeter.MovingAverage( TimeDuration.of( 5, TimeDuration.Unit.MINUTES ) );


    static
    {
        PERCENT_FORMAT.setMinimumFractionDigits( 2 );
    }

    Populator(
            final StoredWordlistDataBean.RemoteWordlistInfo remoteWordlistInfo,
            final InputStream inputStream,
            final StoredWordlistDataBean.Source source,
            final AbstractWordlist rootWordlist,
            final PwmApplication pwmApplication
    )
            throws Exception
    {
        this.remoteWordlistInfo = remoteWordlistInfo;
        this.source = source;
        this.zipFileReader = new WordlistZipReader( inputStream );
        this.localDB = pwmApplication.getLocalDB();
        this.rootWordlist = rootWordlist;
    }

    private void init( ) throws IOException, LocalDBException
    {
        if ( abortFlag.get() )
        {
            return;
        }

        final long previousBytesRead = rootWordlist.readMetadata().getBytes();

        if ( previousBytesRead == 0 )
        {
            LOGGER.debug( "clearing stored wordlist" );
            localDB.truncate( rootWordlist.getWordlistDB() );
        }

        if ( previousBytesRead > 0 )
        {
            final Instant startSkip = Instant.now();
            LOGGER.debug( "skipping forward " + previousBytesRead + " bytes in stream that have been previously imported" );
            while ( !abortFlag.get() && bytesSkipped.get() < previousBytesRead )
            {
                zipFileReader.nextLine();
                bytesSkipped.set( zipFileReader.getByteCount() );
            }
            LOGGER.debug( "skipped forward " + previousBytesRead + " bytes in stream (" + TimeDuration.fromCurrent( startSkip ).asCompactString() + ")" );
        }
    }

    String makeStatString( )
    {
        if ( !running.get() )
        {
            return "not running";
        }

        return rootWordlist.debugLabel + " " + StringUtil.mapToString( makeStatValues() );
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

        if ( remoteWordlistInfo != null )
        {
            final Percent percent = new Percent( zipFileReader.getByteCount(), remoteWordlistInfo.getBytes() );
            stats.put( "PercentComplete", percent.pretty( 2 ) );
        }

        stats.put( "ImportTime", TimeDuration.fromCurrent( startTime ).asCompactString() );

        return Collections.unmodifiableMap( stats );
    }

    @SuppressWarnings( "checkstyle:InnerAssignment" )
    void populate( ) throws IOException, LocalDBException, PwmUnrecoverableException
    {
        final ConditionalTaskExecutor metaUpdater = new ConditionalTaskExecutor(
                () -> rootWordlist.writeMetadata( StoredWordlistDataBean.builder()
                        .source( source )
                        .size( rootWordlist.size() )
                        .storeDate( Instant.now() )
                        .remoteInfo( remoteWordlistInfo )
                        .bytes( zipFileReader.getByteCount() )
                        .build() ),
                new ConditionalTaskExecutor.TimeDurationPredicate( TimeDuration.SECONDS_10 )
        );

        final ConditionalTaskExecutor debugOutputter = new ConditionalTaskExecutor(
                () -> LOGGER.debug( makeStatString() ),
                new ConditionalTaskExecutor.TimeDurationPredicate( DEBUG_OUTPUT_FREQUENCY )
        );

        try
        {
            debugOutputter.conditionallyExecuteTask();
            running.set( true );
            init();

            String line;

            long lastBytes = zipFileReader.getByteCount();

            while ( !abortFlag.get() && ( line = zipFileReader.nextLine() ) != null )
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

            if ( abortFlag.get() )
            {
                LOGGER.warn( "pausing " + rootWordlist.debugLabel + " population" );
            }
            else
            {
                populationComplete();
            }
        }
        finally
        {
            running.set( false );
            IOUtils.closeQuietly( zipFileReader );
        }
    }

    private void addLine( final String word )
            throws IOException
    {
        // check for word suitability
        String normalizedWord = rootWordlist.normalizeWord( word );

        if ( normalizedWord == null || normalizedWord.length() < 1 || normalizedWord.startsWith( COMMENT_PREFIX ) )
        {
            return;
        }

        if ( normalizedWord.length() > MAX_LINE_LENGTH )
        {
            normalizedWord = normalizedWord.substring( 0, MAX_LINE_LENGTH );
        }

        final Map<String, String> wordTxn = rootWordlist.getWriteTxnForValue( normalizedWord );
        bufferedWords.putAll( wordTxn );
    }

    private void flushBuffer( )
            throws LocalDBException
    {
        final long startTime = System.currentTimeMillis();

        //add the elements
        localDB.putAll( rootWordlist.getWordlistDB(), bufferedWords );

        if ( abortFlag.get() )
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
            throws LocalDBException, PwmUnrecoverableException, IOException
    {
        flushBuffer();
        LOGGER.info( makeStatString() );
        LOGGER.trace( "beginning wordlist size query" );
        final int wordlistSize = localDB.size( rootWordlist.getWordlistDB() );
        if ( wordlistSize < 1 )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, rootWordlist.debugLabel + " population completed, but no words stored" ) );
        }

        final StringBuilder sb = new StringBuilder();
        sb.append( rootWordlist.debugLabel );
        sb.append( " population complete, added " ).append( wordlistSize );
        sb.append( " total words in " ).append( TimeDuration.fromCurrent( startTime ).asCompactString() );
        {
            final StoredWordlistDataBean storedWordlistDataBean = StoredWordlistDataBean.builder()
                    .remoteInfo( remoteWordlistInfo )
                    .size( wordlistSize )
                    .storeDate( Instant.now() )
                    .source( source )
                    .completed( !abortFlag.get() )
                    .bytes( zipFileReader.getByteCount() )
                    .build();
            rootWordlist.writeMetadata( storedWordlistDataBean );
        }
        LOGGER.info( sb.toString() );
    }

    public void cancel( ) throws PwmUnrecoverableException
    {
        LOGGER.debug( "cancelling in-progress population" );
        abortFlag.set( true );

        final int maxWaitMs = 1000 * 30;
        final Instant startWaitTime = Instant.now();
        while ( isRunning() && TimeDuration.fromCurrent( startWaitTime ).isShorterThan( maxWaitMs ) )
        {
            JavaHelper.pause( 1000 );
        }
        if ( isRunning() && TimeDuration.fromCurrent( startWaitTime ).isShorterThan( maxWaitMs ) )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, "unable to abort in progress population" ) );
        }

    }

    public boolean isRunning( )
    {
        return running.get();
    }
}
