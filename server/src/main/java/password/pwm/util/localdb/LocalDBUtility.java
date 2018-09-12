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
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.svc.stats.EventRateMeter;
import password.pwm.util.ProgressInfo;
import password.pwm.util.TransactionSizeCalculator;
import password.pwm.util.java.JavaHelper;
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
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class LocalDBUtility
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( LocalDBUtility.class );

    private final LocalDB localDB;
    private int exportLineCounter;
    private int importLineCounter;

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
            throw new PwmOperationalException( PwmError.ERROR_UNKNOWN, "outputFileStream for exportLocalDB cannot be null" );
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
            throw new PwmOperationalException( PwmError.ERROR_UNKNOWN, "inputFile for importLocalDB cannot be null" );
        }

        if ( !inputFile.exists() )
        {
            throw new PwmOperationalException( PwmError.ERROR_UNKNOWN, "inputFile for importLocalDB does not exist" );
        }

        final long totalBytes = inputFile.length();

        if ( totalBytes <= 0 )
        {
            throw new PwmOperationalException( PwmError.ERROR_UNKNOWN, "inputFile for importLocalDB is empty" );
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
        this.prepareForImport();

        importLineCounter = 0;
        if ( totalBytes > 0 )
        {
            writeStringToOut( out, "total bytes in localdb import source: " + totalBytes );
        }

        writeStringToOut( out, "beginning localdb import..." );

        final Instant startTime = Instant.now();
        final TransactionSizeCalculator transactionCalculator = new TransactionSizeCalculator(
                new TransactionSizeCalculator.SettingsBuilder()
                        .setDurationGoal( new TimeDuration( 100, TimeUnit.MILLISECONDS ) )
                        .setMinTransactions( 50 )
                        .setMaxTransactions( 5 * 1000 )
                        .createSettings()
        );

        final Map<LocalDB.DB, Map<String, String>> transactionMap = new HashMap<>();
        for ( final LocalDB.DB loopDB : LocalDB.DB.values() )
        {
            transactionMap.put( loopDB, new TreeMap<>() );
        }

        final CountingInputStream countingInputStream = new CountingInputStream( inputStream );
        final EventRateMeter eventRateMeter = new EventRateMeter( TimeDuration.MINUTE );

        final Timer statTimer = new Timer( true );
        statTimer.scheduleAtFixedRate( new TimerTask()
        {
            @Override
            public void run( )
            {
                String output = "";
                if ( totalBytes > 0 )
                {
                    final ProgressInfo progressInfo = new ProgressInfo( startTime, totalBytes, countingInputStream.getByteCount() );
                    output += progressInfo.debugOutput();
                }
                else
                {
                    output += "recordsImported=" + importLineCounter;
                }
                output += ", avgTransactionSize=" + transactionCalculator.getTransactionSize()
                        + ", recordsPerMinute=" + eventRateMeter.readEventRate().setScale( 2, BigDecimal.ROUND_DOWN );
                writeStringToOut( out, output );
            }
        }, 30 * 1000, 30 * 1000 );


        Reader csvReader = null;
        try
        {
            csvReader = new InputStreamReader( new GZIPInputStream( countingInputStream, GZIP_BUFFER_SIZE ), PwmConstants.DEFAULT_CHARSET );
            for ( final CSVRecord record : PwmConstants.DEFAULT_CSV_FORMAT.parse( csvReader ) )
            {
                importLineCounter++;
                eventRateMeter.markEvents( 1 );
                final String dbNameRecordStr = record.get( 0 );
                final LocalDB.DB db = JavaHelper.readEnumFromString( LocalDB.DB.class, null, dbNameRecordStr );
                final String key = record.get( 1 );
                final String value = record.get( 2 );
                if ( db == null )
                {
                    writeStringToOut( out, "ignoring localdb import record #" + importLineCounter + ", invalid DB name '" + dbNameRecordStr + "'" );
                }
                else
                {
                    transactionMap.get( db ).put( key, value );
                    int cachedTransactions = 0;
                    for ( final LocalDB.DB loopDB : LocalDB.DB.values() )
                    {
                        cachedTransactions += transactionMap.get( loopDB ).size();
                    }
                    if ( cachedTransactions >= transactionCalculator.getTransactionSize() )
                    {
                        final long startTxnTime = System.currentTimeMillis();
                        for ( final LocalDB.DB loopDB : LocalDB.DB.values() )
                        {
                            localDB.putAll( loopDB, transactionMap.get( loopDB ) );
                            transactionMap.get( loopDB ).clear();
                        }
                        transactionCalculator.recordLastTransactionDuration( TimeDuration.fromCurrent( startTxnTime ) );
                    }
                }
            }
        }
        finally
        {
            LOGGER.trace( "import process completed" );
            statTimer.cancel();
            IOUtils.closeQuietly( csvReader );
            IOUtils.closeQuietly( countingInputStream );
        }

        for ( final LocalDB.DB loopDB : LocalDB.DB.values() )
        {
            localDB.putAll( loopDB, transactionMap.get( loopDB ) );
            transactionMap.get( loopDB ).clear();
        }

        this.markImportComplete();

        writeStringToOut( out, "restore complete, restored " + importLineCounter + " records in " + TimeDuration.fromCurrent( startTime ).asLongString() );
        statTimer.cancel();
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

    public void prepareForImport( )
            throws LocalDBException
    {
        LOGGER.info( "preparing LocalDB for import procedure" );
        localDB.put( LocalDB.DB.PWM_META, PwmApplication.AppAttribute.LOCALDB_IMPORT_STATUS.getKey(), "inprogress" );
        for ( final LocalDB.DB loopDB : LocalDB.DB.values() )
        {
            if ( loopDB != LocalDB.DB.PWM_META )
            {
                localDB.truncate( loopDB );
            }
        }

        // save meta for last so flag is cleared last.
        localDB.truncate( LocalDB.DB.PWM_META );
        localDB.put( LocalDB.DB.PWM_META, PwmApplication.AppAttribute.LOCALDB_IMPORT_STATUS.getKey(), "inprogress" );
    }

    public void markImportComplete( )
            throws LocalDBException
    {
        LOGGER.info( "marking LocalDB import procedure completed" );
        localDB.remove( LocalDB.DB.PWM_META, PwmApplication.AppAttribute.LOCALDB_IMPORT_STATUS.getKey() );
    }

    public boolean readImportInprogressFlag( )
            throws LocalDBException
    {
        return "inprogress".equals(
                localDB.get( LocalDB.DB.PWM_META, PwmApplication.AppAttribute.LOCALDB_IMPORT_STATUS.getKey() ) );
    }

    static boolean hasBooleanParameter( final LocalDBProvider.Parameter parameter, final Map<LocalDBProvider.Parameter, String> parameters )
    {
        return parameters != null && parameters.containsKey( parameter ) && Boolean.parseBoolean( parameters.get( parameter ) );
    }
}
