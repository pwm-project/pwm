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
import password.pwm.util.TransactionSizeCalculator;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.ChecksumInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Jason D. Rivard
 */
class Populator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( Populator.class );

    // words truncated to this length, prevents massive words if the input
    private static final int MAX_LINE_LENGTH = 64;

    private static final TimeDuration DEBUG_OUTPUT_FREQUENCY = new TimeDuration( 3, TimeUnit.MINUTES );

    // words tarting with this prefix are ignored.
    private static final String COMMENT_PREFIX = "!#comment:";

    private static final NumberFormat PERCENT_FORMAT = DecimalFormat.getPercentInstance();

    private final ZipReader zipFileReader;
    private final StoredWordlistDataBean.Source source;

    private volatile boolean running;
    private volatile boolean abortFlag;

    private final PopulationStats overallStats = new PopulationStats();
    private PopulationStats perReportStats = new PopulationStats();
    private TransactionSizeCalculator transactionCalculator = new TransactionSizeCalculator(
            new TransactionSizeCalculator.SettingsBuilder()
                    .setDurationGoal( new TimeDuration( 600, TimeUnit.MILLISECONDS ) )
                    .setMinTransactions( 10 )
                    .setMaxTransactions( 350 * 1000 )
                    .createSettings()
    );

    private int loopLines;

    private final Map<String, String> bufferedWords = new TreeMap<>();

    private final LocalDB localDB;

    private final ChecksumInputStream checksumInputStream;

    private final AbstractWordlist rootWordlist;


    static
    {
        PERCENT_FORMAT.setMinimumFractionDigits( 2 );
    }

    Populator(
            final InputStream inputStream,
            final StoredWordlistDataBean.Source source,
            final AbstractWordlist rootWordlist,
            final PwmApplication pwmApplication
    )
            throws Exception
    {
        this.source = source;
        this.checksumInputStream = new ChecksumInputStream( AbstractWordlist.CHECKSUM_HASH_ALG, inputStream );
        this.zipFileReader = new ZipReader( checksumInputStream );
        this.localDB = pwmApplication.getLocalDB();
        this.rootWordlist = rootWordlist;
    }

    private void init( ) throws LocalDBException, IOException
    {
        if ( abortFlag )
        {
            return;
        }

        localDB.truncate( rootWordlist.getWordlistDB() );

        if ( overallStats.getLines() > 0 )
        {
            for ( int i = 0; i < overallStats.getLines(); i++ )
            {
                zipFileReader.nextLine();
            }
        }
    }

    public String makeStatString( )
    {
        if ( !running )
        {
            return "not running";
        }

        final int lps = perReportStats.getElapsedSeconds() <= 0 ? 0 : perReportStats.getLines() / perReportStats.getElapsedSeconds();

        perReportStats = new PopulationStats();
        return rootWordlist.debugLabel + ", lines/second="
                + lps + ", line=" + overallStats.getLines() + ""
                + " current zipEntry=" + zipFileReader.currentZipName();
    }

    @SuppressWarnings( "checkstyle:InnerAssignment" )
    void populate( ) throws IOException, LocalDBException, PwmUnrecoverableException
    {
        try
        {
            rootWordlist.writeMetadata( StoredWordlistDataBean.builder().source( source ).build() );
            running = true;
            init();

            long lastReportTime = System.currentTimeMillis() - ( long ) ( DEBUG_OUTPUT_FREQUENCY.getTotalMilliseconds() * 0.33 );

            String line;

            while ( !abortFlag && ( line = zipFileReader.nextLine() ) != null )
            {

                overallStats.incrementLines();
                perReportStats.incrementLines();

                addLine( line );
                loopLines++;

                if ( TimeDuration.fromCurrent( lastReportTime ).isLongerThan( DEBUG_OUTPUT_FREQUENCY.getTotalMilliseconds() ) )
                {
                    LOGGER.info( makeStatString() );
                    lastReportTime = System.currentTimeMillis();
                }

                if ( bufferedWords.size() > transactionCalculator.getTransactionSize() )
                {
                    flushBuffer();
                }
            }

            if ( abortFlag )
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
            running = false;
            IOUtils.closeQuietly( checksumInputStream );
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

        if ( abortFlag )
        {
            return;
        }

        //mark how long the buffer close took
        final long commitTime = System.currentTimeMillis() - startTime;
        transactionCalculator.recordLastTransactionDuration( commitTime );

        if ( bufferedWords.size() > 0 )
        {
            final StringBuilder sb = new StringBuilder();
            sb.append( rootWordlist.debugLabel ).append( " " );
            sb.append( "read " ).append( loopLines ).append( ", " );
            sb.append( "saved " );
            sb.append( bufferedWords.size() ).append( " words" );
            sb.append( " (" ).append( new TimeDuration( commitTime ).asCompactString() ).append( ")" );

            LOGGER.trace( sb.toString() );
        }

        //clear the buffers.
        bufferedWords.clear();
        loopLines = 0;
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
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_UNKNOWN, rootWordlist.debugLabel + " population completed, but no words stored" ) );
        }

        final StringBuilder sb = new StringBuilder();
        sb.append( rootWordlist.debugLabel );
        sb.append( " population complete, added " ).append( wordlistSize );
        sb.append( " total words in " ).append( new TimeDuration( overallStats.getElapsedSeconds() * 1000 ).asCompactString() );
        {
            final StoredWordlistDataBean storedWordlistDataBean = StoredWordlistDataBean.builder()
                    .sha1hash( JavaHelper.binaryArrayToHex( checksumInputStream.closeAndFinalChecksum() ) )
                    .size( wordlistSize )
                    .storeDate( Instant.now() )
                    .source( source )
                    .completed( !abortFlag )
                    .build();
            rootWordlist.writeMetadata( storedWordlistDataBean );
        }
        LOGGER.info( sb.toString() );
    }

    public void cancel( ) throws PwmUnrecoverableException
    {
        LOGGER.debug( "cancelling in-progress population" );
        abortFlag = true;

        final int maxWaitMs = 1000 * 30;
        final Date startWaitTime = new Date();
        while ( isRunning() && TimeDuration.fromCurrent( startWaitTime ).isShorterThan( maxWaitMs ) )
        {
            JavaHelper.pause( 1000 );
        }
        if ( isRunning() && TimeDuration.fromCurrent( startWaitTime ).isShorterThan( maxWaitMs ) )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_UNKNOWN, "unable to abort in progress population" ) );
        }

    }

    public boolean isRunning( )
    {
        return running;
    }

    private static class PopulationStats
    {

        private long startTime = System.currentTimeMillis();
        private int lines;

        public int getLines( )
        {
            return lines;
        }

        public void incrementLines( )
        {
            lines++;
        }

        public int getElapsedSeconds( )
        {
            return ( int ) ( System.currentTimeMillis() - startTime ) / 1000;
        }
    }
}
