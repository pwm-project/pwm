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

package password.pwm.util.localdb;

import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.CountingInputStream;
import password.pwm.AppAttribute;
import password.pwm.PwmConstants;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.EventRateMeter;
import password.pwm.util.ProgressInfo;
import password.pwm.util.TransactionSizeCalculator;
import password.pwm.util.java.AverageTracker;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.Percent;
import password.pwm.util.java.PwmNumberFormat;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class LocalDBUtility
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( LocalDBUtility.class );
    private static final String IN_PROGRESS_STATUS_VALUE = "in-progress";

    private final LocalDB localDB;

    private static final int GZIP_BUFFER_SIZE = 1024 * 1024;


    public LocalDBUtility( final LocalDB localDB )
    {
        this.localDB = localDB;
    }

    private long countBackupableRecords( final Appendable debugOutput )
            throws LocalDBException
    {
        long counter = 0;
        writeStringToOut( debugOutput, "counting records in LocalDB..." );
        for ( final LocalDB.DB loopDB : LocalDB.DB.values() )
        {
            if ( loopDB.isBackup() )
            {
                counter += localDB.size( loopDB );
            }
        }
        writeStringToOut( debugOutput, " total lines: " + counter );
        return counter;
    }

    public void exportLocalDB( final OutputStream outputStream, final Appendable debugOutput )
            throws PwmOperationalException
    {
        Objects.requireNonNull( outputStream );
        final AtomicLong exportLineCounter = new AtomicLong( 0 );

        final long totalLines = countBackupableRecords( debugOutput );


        writeStringToOut( debugOutput, "LocalDB export beginning of " + totalLines + " records" );
        final Instant startTime = Instant.now();

        final EventRateMeter eventRateMeter = new EventRateMeter( TimeDuration.MINUTE );
        final ConditionalTaskExecutor debugOutputter = ConditionalTaskExecutor.forPeriodicTask( () ->
                        outputExportDebugStats( totalLines, exportLineCounter.get(), eventRateMeter, startTime, debugOutput ),
                TimeDuration.MINUTE );

        try ( CSVPrinter csvPrinter = JavaHelper.makeCsvPrinter( new GZIPOutputStream( outputStream, GZIP_BUFFER_SIZE ) ) )
        {
            csvPrinter.printComment( PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION + " LocalDB export on " + JavaHelper.toIsoDate( Instant.now() ) );
            for ( final LocalDB.DB loopDB : LocalDB.DB.values() )
            {
                if ( loopDB.isBackup() )
                {
                    csvPrinter.printComment( "Export of " + loopDB.toString() );
                    try ( LocalDB.LocalDBIterator<Map.Entry<String, String>> localDBIterator = localDB.iterator( loopDB ) )
                    {
                        while ( localDBIterator.hasNext() )
                        {
                            final Map.Entry<String, String> entry = localDBIterator.next();
                            final String key = entry.getKey();
                            final String value = entry.getValue();
                            csvPrinter.printRecord( loopDB.toString(), key, value );
                            exportLineCounter.incrementAndGet();
                            eventRateMeter.markEvents( 1 );
                            debugOutputter.conditionallyExecuteTask();
                        }
                    }
                    csvPrinter.flush();
                }
            }
            csvPrinter.printComment( "export completed at " + JavaHelper.toIsoDate( Instant.now() ) );
        }
        catch ( final IOException e )
        {
            writeStringToOut( debugOutput, "IO error during localDB export: " + e.getMessage() );
        }

        writeStringToOut( debugOutput, "export complete, exported " + exportLineCounter + " records in " + TimeDuration.fromCurrent( startTime ).asLongString() );
    }

    public void exportWordlist( final OutputStream outputStream, final Appendable debugOutput )
            throws PwmOperationalException, IOException
    {
        Objects.requireNonNull( outputStream );

        final long totalLines = localDB.size( LocalDB.DB.WORDLIST_WORDS );

        final AtomicLong exportLineCounter = new AtomicLong( 0 );

        writeStringToOut( debugOutput, "Wordlist ZIP export beginning of "
                + StringUtil.formatDiskSize( totalLines ) + " records" );
        final Instant startTime = Instant.now();

        final EventRateMeter eventRateMeter = new EventRateMeter( TimeDuration.MINUTE );
        final ConditionalTaskExecutor debugOutputter = ConditionalTaskExecutor.forPeriodicTask( () ->
                        outputExportDebugStats( totalLines, exportLineCounter.get(), eventRateMeter, startTime, debugOutput ),
                TimeDuration.MINUTE );

        try ( ZipOutputStream zipOutputStream = new ZipOutputStream( outputStream, PwmConstants.DEFAULT_CHARSET ) )
        {
            zipOutputStream.putNextEntry( new ZipEntry( "wordlist.txt" ) );
            try ( LocalDB.LocalDBIterator<Map.Entry<String, String>> localDBIterator = localDB.iterator( LocalDB.DB.WORDLIST_WORDS ) )
            {
                while ( localDBIterator.hasNext() )
                {
                    final Map.Entry<String, String> entry = localDBIterator.next();
                    final String key = entry.getKey();
                    zipOutputStream.write( key.getBytes( PwmConstants.DEFAULT_CHARSET ) );
                    zipOutputStream.write( '\n' );
                    exportLineCounter.incrementAndGet();
                    eventRateMeter.markEvents( 1 );
                    debugOutputter.conditionallyExecuteTask();
                }
            }
        }
        catch ( final IOException e )
        {
            writeStringToOut( debugOutput, "IO error during localDB export: " + e.getMessage() );
        }

        writeStringToOut( debugOutput, "export complete, exported " + exportLineCounter + " records in " + TimeDuration.fromCurrent( startTime ).asLongString() );
    }

    private void outputExportDebugStats(
            final long totalLines,
            final long exportLineCounter,
            final EventRateMeter eventRateMeter,
            final Instant startTime,
            final Appendable debugOutput
    )
    {
        final Percent percentComplete = new Percent( exportLineCounter, totalLines );
        final String percentStr = percentComplete.pretty( 2 );
        final long secondsRemaining = totalLines / eventRateMeter.readEventRate().longValue();

        final String msg = "export stats: recordsOut=" + PwmNumberFormat.forDefaultLocale().format( exportLineCounter )
                + ", duration=" + TimeDuration.fromCurrent( startTime ).asCompactString()
                + ", percentComplete=" + percentStr
                + ", recordsPerSecond=" + PwmNumberFormat.forDefaultLocale().format( eventRateMeter.readEventRate().longValue() )
                + ", remainingTime=" + TimeDuration.of( secondsRemaining, TimeDuration.Unit.SECONDS ).asCompactString();
        writeStringToOut( debugOutput, msg );
    }

    private static void writeStringToOut( final Appendable out, final String string )
    {
        if ( out == null )
        {
            return;
        }

        final String msg = string + "\n";

        try
        {
            out.append( msg );
        }
        catch ( final IOException e )
        {
            LOGGER.error( () -> "error writing to output appender while performing operation: " + e.getMessage() );
        }
    }

    public void importLocalDB( final File inputFile, final PrintStream out )
            throws PwmOperationalException, IOException
    {
        if ( inputFile == null )
        {
            throw new PwmOperationalException( PwmError.ERROR_INTERNAL, "inputFile for importLocalDB cannot be null" );
        }

        if ( !inputFile.exists() )
        {
            throw new PwmOperationalException( PwmError.ERROR_INTERNAL, "inputFile for importLocalDB does not exist" );
        }

        final long totalBytes = inputFile.length();

        if ( totalBytes <= 0 )
        {
            throw new PwmOperationalException( PwmError.ERROR_INTERNAL, "inputFile for importLocalDB is empty" );
        }

        try ( InputStream inputStream = new FileInputStream( inputFile ) )
        {
            importLocalDB( inputStream, out, totalBytes );
        }
    }

    public void importLocalDB( final InputStream inputStream, final Appendable out )
            throws PwmOperationalException, IOException
    {
        importLocalDB( inputStream, out, 0 );
    }

    private void importLocalDB( final InputStream inputStream, final Appendable out, final long totalBytes )
            throws PwmOperationalException, IOException
    {

        final ImportLocalDBMachine importLocalDBMachine = new ImportLocalDBMachine( localDB, totalBytes, out );
        importLocalDBMachine.doImport( inputStream );
    }

    private static class ImportLocalDBMachine
    {
        private static final long MAX_CHAR_PER_TRANSACTIONS = 50_000_000;

        private int lineReaderCounter;
        private long byteReaderCounter;
        private int recordImportCounter;
        private long transactionCharCounter;

        private final Instant startTime = Instant.now();
        final Map<LocalDB.DB, Map<String, String>> transactionMap = new HashMap<>();
        private final EventRateMeter eventRateMeter = new EventRateMeter( TimeDuration.MINUTE );
        private final AverageTracker charsPerTransactionAverageTracker = new AverageTracker( 50 );
        private final TransactionSizeCalculator transactionCalculator = new TransactionSizeCalculator(
                TransactionSizeCalculator.Settings.builder()
                        .durationGoal( TimeDuration.of( 1000, TimeDuration.Unit.MILLISECONDS ) )
                        .minTransactions( 5 )
                        .maxTransactions( 5_000_000 )
                        .build()
        );

        private final long totalBytes;
        private final Appendable debugOutput;
        private final LocalDB localDB;

        private final ConditionalTaskExecutor debugOutputWriter;

        ImportLocalDBMachine( final LocalDB localDB, final long totalBytes, final Appendable debugOutput )
        {
            this.localDB = localDB;
            this.totalBytes = totalBytes;
            this.debugOutput = debugOutput;

            for ( final LocalDB.DB loopDB : LocalDB.DB.values() )
            {
                transactionMap.put( loopDB, new TreeMap<>() );
            }

            this.debugOutputWriter = ConditionalTaskExecutor.forPeriodicTask( () ->
            {
                writeStringToOut( debugOutput, debugStatsString() );
            }, TimeDuration.of( 30, TimeDuration.Unit.SECONDS ) );
        }

        void doImport( final InputStream inputStream )
                throws IOException, LocalDBException
        {
            this.prepareForImport();

            if ( totalBytes > 0 )
            {
                writeStringToOut( debugOutput, "total bytes in localdb import source: " + totalBytes );
            }

            writeStringToOut( debugOutput, "beginning localdb import..." );

            try ( CountingInputStream countingInputStream = new CountingInputStream( inputStream ) )
            {
                try ( Reader csvReader = new InputStreamReader( new GZIPInputStream( countingInputStream, GZIP_BUFFER_SIZE ), PwmConstants.DEFAULT_CHARSET ) )
                {
                    int cachedTransactions = 0;
                    for ( final CSVRecord record : PwmConstants.DEFAULT_CSV_FORMAT.parse( csvReader ) )
                    {
                        lineReaderCounter++;
                        eventRateMeter.markEvents( 1 );
                        byteReaderCounter = countingInputStream.getByteCount();
                        final String dbNameRecordStr = record.get( 0 );
                        final LocalDB.DB db = JavaHelper.readEnumFromString( LocalDB.DB.class, null, dbNameRecordStr );
                        final String key = record.get( 1 );
                        final String value = record.get( 2 );
                        if ( db == null )
                        {
                            writeStringToOut( debugOutput, "ignoring localdb import record #" + lineReaderCounter + ", invalid DB name '" + dbNameRecordStr + "'" );
                        }
                        else
                        {
                            transactionCharCounter += key.length() + value.length();
                            transactionMap.get( db ).put( key, value );
                            cachedTransactions++;
                            if ( cachedTransactions >= transactionCalculator.getTransactionSize() || transactionCharCounter > MAX_CHAR_PER_TRANSACTIONS )
                            {
                                flushCachedTransactions();
                                cachedTransactions = 0;
                            }
                        }
                        debugOutputWriter.conditionallyExecuteTask();
                    }
                }
            }

            flushCachedTransactions();
            this.markImportComplete();

            final String completeMsg = "import process completed: " + debugStatsString();
            LOGGER.info( () -> completeMsg );
            writeStringToOut( debugOutput, completeMsg );
        }

        private void flushCachedTransactions( )
                throws LocalDBException
        {
            final Instant startTxnTime = Instant.now();
            for ( final LocalDB.DB loopDB : LocalDB.DB.values() )
            {
                localDB.putAll( loopDB, transactionMap.get( loopDB ) );
                recordImportCounter += transactionMap.get( loopDB ).size();
                transactionMap.get( loopDB ).clear();
            }
            transactionCalculator.recordLastTransactionDuration( TimeDuration.fromCurrent( startTxnTime ) );
            charsPerTransactionAverageTracker.addSample( transactionCharCounter );
            transactionCharCounter = 0;
        }

        private void prepareForImport( )
                throws LocalDBException
        {
            LOGGER.info( () -> "preparing LocalDB for import procedure" );
            localDB.put( LocalDB.DB.PWM_META, AppAttribute.LOCALDB_IMPORT_STATUS.getKey(), IN_PROGRESS_STATUS_VALUE );
            for ( final LocalDB.DB loopDB : LocalDB.DB.values() )
            {
                if ( loopDB != LocalDB.DB.PWM_META )
                {
                    localDB.truncate( loopDB );
                }
            }

            // save meta for last so flag is cleared last.
            localDB.truncate( LocalDB.DB.PWM_META );
            localDB.put( LocalDB.DB.PWM_META, AppAttribute.LOCALDB_IMPORT_STATUS.getKey(), IN_PROGRESS_STATUS_VALUE  );
        }

        private void markImportComplete()
                throws LocalDBException
        {
            LOGGER.info( () -> "marking LocalDB import procedure completed" );
            localDB.remove( LocalDB.DB.PWM_META, AppAttribute.LOCALDB_IMPORT_STATUS.getKey() );
        }

        private String debugStatsString()
        {
            final Map<String, String> stats = new LinkedHashMap<>();
            if ( totalBytes > 0 && byteReaderCounter > 0 )
            {
                final ProgressInfo progressInfo = new ProgressInfo( startTime, totalBytes, byteReaderCounter );
                stats.put( "progress", progressInfo.debugOutput() );
            }

            stats.put( "linesRead", Integer.toString( lineReaderCounter ) );
            stats.put( "bytesRead", Long.toString( byteReaderCounter ) );
            stats.put( "recordsImported", Integer.toString( recordImportCounter ) );
            stats.put( "rowsPerTransaction", Integer.toString( transactionCalculator.getTransactionSize() ) );
            stats.put( "charsPerTransaction", charsPerTransactionAverageTracker.avg().toPlainString() );
            stats.put( "rowsPerMinute", eventRateMeter.readEventRate().setScale( 2, RoundingMode.DOWN ).toString() );
            stats.put( "duration", TimeDuration.compactFromCurrent( startTime ) );
            return StringUtil.mapToString( stats );
        }
    }

    public static Map<StatsKey, Object> dbStats(
            final LocalDB localDB,
            final LocalDB.DB db
    )
    {
        int totalValues = 0;
        long storedChars = 0;
        final long totalChars = 0;

        LocalDB.LocalDBIterator<Map.Entry<String, String>> iter = null;
        try
        {
            iter = localDB.iterator( db );
            while ( iter.hasNext() )
            {
                final Map.Entry<String, String> entry = iter.next();
                final String rawValue = entry.getValue();
                if ( rawValue != null )
                {
                    totalValues++;
                    storedChars += rawValue.length();
                }
            }
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error while examining LocalDB: " + e.getMessage() );
        }
        finally
        {
            if ( iter != null )
            {
                iter.close();
            }
        }

        final int avgValueLength = totalValues == 0 ? 0 : ( int ) ( totalChars / totalValues );
        final Map<StatsKey, Object> returnObj = new LinkedHashMap<>();
        returnObj.put( StatsKey.TOTAL_VALUES, totalValues );
        returnObj.put( StatsKey.STORED_CHARS, storedChars );
        returnObj.put( StatsKey.AVG_VALUE_LENGTH, avgValueLength );
        return returnObj;
    }

    public enum StatsKey
    {
        TOTAL_VALUES,
        STORED_CHARS,
        AVG_VALUE_LENGTH,
    }

    public boolean readImportInprogressFlag( )
            throws LocalDBException
    {
        return IN_PROGRESS_STATUS_VALUE.equals(
                localDB.get( LocalDB.DB.PWM_META, AppAttribute.LOCALDB_IMPORT_STATUS.getKey() ) );
    }

    static boolean hasBooleanParameter( final LocalDBProvider.Parameter parameter, final Map<LocalDBProvider.Parameter, String> parameters )
    {
        return parameters != null && parameters.containsKey( parameter ) && Boolean.parseBoolean( parameters.get( parameter ) );
    }

    public void cancelImportProcess()
            throws LocalDBException
    {
        final ImportLocalDBMachine importLocalDBMachine = new ImportLocalDBMachine( localDB, 0, new StringBuilder() );
        importLocalDBMachine.prepareForImport();
        importLocalDBMachine.markImportComplete();
    }
}
