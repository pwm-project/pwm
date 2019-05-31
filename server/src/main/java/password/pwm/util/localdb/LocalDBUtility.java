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

package password.pwm.util.localdb;

import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.CountingInputStream;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.EventRateMeter;
import password.pwm.util.ProgressInfo;
import password.pwm.util.TransactionSizeCalculator;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JavaHelper;
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
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class LocalDBUtility
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( LocalDBUtility.class );
    private static final String IN_PROGRESS_STATUS_VALUE = "in-progress";

    private final LocalDB localDB;
    private int exportLineCounter;

    private static final int GZIP_BUFFER_SIZE = 1024 * 512;


    public LocalDBUtility( final LocalDB localDB )
    {
        this.localDB = localDB;
    }

    public void exportLocalDB( final OutputStream outputStream, final Appendable debugOutput, final boolean showLineCount )
            throws PwmOperationalException, IOException
    {
        if ( outputStream == null )
        {
            throw new PwmOperationalException( PwmError.ERROR_INTERNAL, "outputFileStream for exportLocalDB cannot be null" );
        }


        final int totalLines;
        if ( showLineCount )
        {
            writeStringToOut( debugOutput, "counting records in LocalDB..." );
            exportLineCounter = 0;
            for ( final LocalDB.DB loopDB : LocalDB.DB.values() )
            {
                if ( loopDB.isBackup() )
                {
                    exportLineCounter += localDB.size( loopDB );
                }
            }
            totalLines = exportLineCounter;
            writeStringToOut( debugOutput, " total lines: " + totalLines );
        }
        else
        {
            totalLines = 0;
        }
        exportLineCounter = 0;

        writeStringToOut( debugOutput, "export beginning" );
        final long startTime = System.currentTimeMillis();
        final Timer statTimer = new Timer( true );
        statTimer.schedule( new TimerTask()
        {
            @Override
            public void run( )
            {
                if ( showLineCount )
                {
                    final float percentComplete = ( float ) exportLineCounter / ( float ) totalLines;
                    final String percentStr = DecimalFormat.getPercentInstance().format( percentComplete );
                    writeStringToOut( debugOutput, "exported " + exportLineCounter + " records, " + percentStr + " complete" );
                }
                else
                {
                    writeStringToOut( debugOutput, "exported " + exportLineCounter + " records" );
                }
            }
        }, 30 * 1000, 30 * 1000 );


        try ( CSVPrinter csvPrinter = JavaHelper.makeCsvPrinter( new GZIPOutputStream( outputStream, GZIP_BUFFER_SIZE ) ) )
        {
            csvPrinter.printComment( PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION + " LocalDB export on " + JavaHelper.toIsoDate( new Date() ) );
            for ( final LocalDB.DB loopDB : LocalDB.DB.values() )
            {
                if ( loopDB.isBackup() )
                {
                    csvPrinter.printComment( "Export of " + loopDB.toString() );
                    final LocalDB.LocalDBIterator<String> localDBIterator = localDB.iterator( loopDB );
                    try
                    {
                        while ( localDBIterator.hasNext() )
                        {
                            final String key = localDBIterator.next();
                            final String value = localDB.get( loopDB, key );
                            csvPrinter.printRecord( loopDB.toString(), key, value );
                            exportLineCounter++;
                        }
                    }
                    finally
                    {
                        localDBIterator.close();
                    }
                    csvPrinter.flush();
                }
            }
            csvPrinter.printComment( "export completed at " + JavaHelper.toIsoDate( new Date() ) );
        }
        catch ( IOException e )
        {
            writeStringToOut( debugOutput, "IO error during localDB export: " + e.getMessage() );
        }
        finally
        {
            statTimer.cancel();
        }

        writeStringToOut( debugOutput, "export complete, exported " + exportLineCounter + " records in " + TimeDuration.fromCurrent( startTime ).asLongString() );
    }

    private static void writeStringToOut( final Appendable out, final String string )
    {
        if ( out == null )
        {
            return;
        }

        final String msg = JavaHelper.toIsoDate( new Date() ) + " " + string + "\n";

        try
        {
            out.append( msg );
        }
        catch ( IOException e )
        {
            LOGGER.error( "error writing to output appender while performing operation: " + e.getMessage() + ", message:" + msg );
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
        private int lineReaderCounter;
        private long byteReaderCounter;
        private int recordImportCounter;
        private final Instant startTime = Instant.now();
        final Map<LocalDB.DB, Map<String, String>> transactionMap = new HashMap<>();
        private final EventRateMeter eventRateMeter = new EventRateMeter( TimeDuration.MINUTE );
        private final TransactionSizeCalculator transactionCalculator = new TransactionSizeCalculator(
                TransactionSizeCalculator.Settings.builder()
                        .durationGoal( TimeDuration.of( 100, TimeDuration.Unit.MILLISECONDS ) )
                        .minTransactions( 50 )
                        .maxTransactions( 5 * 1000 )
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
                            transactionMap.get( db ).put( key, value );
                            cachedTransactions++;
                            if ( cachedTransactions >= transactionCalculator.getTransactionSize() )
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
        }

        private void prepareForImport( )
                throws LocalDBException
        {
            LOGGER.info( () -> "preparing LocalDB for import procedure" );
            localDB.put( LocalDB.DB.PWM_META, PwmApplication.AppAttribute.LOCALDB_IMPORT_STATUS.getKey(), IN_PROGRESS_STATUS_VALUE );
            for ( final LocalDB.DB loopDB : LocalDB.DB.values() )
            {
                if ( loopDB != LocalDB.DB.PWM_META )
                {
                    localDB.truncate( loopDB );
                }
            }

            // save meta for last so flag is cleared last.
            localDB.truncate( LocalDB.DB.PWM_META );
            localDB.put( LocalDB.DB.PWM_META, PwmApplication.AppAttribute.LOCALDB_IMPORT_STATUS.getKey(), IN_PROGRESS_STATUS_VALUE  );
        }

        private void markImportComplete()
                throws LocalDBException
        {
            LOGGER.info( () -> "marking LocalDB import procedure completed" );
            localDB.remove( LocalDB.DB.PWM_META, PwmApplication.AppAttribute.LOCALDB_IMPORT_STATUS.getKey() );
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
            stats.put( "avgTransactionSize", Integer.toString( transactionCalculator.getTransactionSize() ) );
            stats.put( "recordsPerMinute", eventRateMeter.readEventRate().setScale( 2, RoundingMode.DOWN ).toString() );
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

        LocalDB.LocalDBIterator<String> iter = null;
        try
        {
            iter = localDB.iterator( db );
            while ( iter.hasNext() )
            {
                final String key = iter.next();
                final String rawValue = localDB.get( db, key );
                if ( rawValue != null )
                {
                    totalValues++;
                    storedChars += rawValue.length();
                }
            }
        }
        catch ( Exception e )
        {
            LOGGER.error( "error while examining LocalDB: " + e.getMessage() );
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
                localDB.get( LocalDB.DB.PWM_META, PwmApplication.AppAttribute.LOCALDB_IMPORT_STATUS.getKey() ) );
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
