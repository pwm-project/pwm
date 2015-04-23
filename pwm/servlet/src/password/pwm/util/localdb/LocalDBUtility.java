/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.Helper;
import password.pwm.util.ProgressInfo;
import password.pwm.util.TimeDuration;
import password.pwm.util.TransactionSizeCalculator;
import password.pwm.util.logging.PwmLogger;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class LocalDBUtility {

    private static final PwmLogger LOGGER = PwmLogger.forClass(LocalDBUtility.class);

    final static List<LocalDB.DB> BACKUP_IGNORE_DBs;
    private final LocalDB localDB;
    private int exportLineCounter;
    private int importLineCounter;

    static {
        final LocalDB.DB[] ignoredDBsArray = {
                LocalDB.DB.SEEDLIST_META,
                LocalDB.DB.SEEDLIST_WORDS,
                LocalDB.DB.WORDLIST_META,
                LocalDB.DB.WORDLIST_WORDS,
        };
        BACKUP_IGNORE_DBs = Collections.unmodifiableList(Arrays.asList(ignoredDBsArray));
    }

    public LocalDBUtility(LocalDB localDB) {
        this.localDB = localDB;
    }

    public void exportLocalDB(final OutputStream outputStream, final Appendable debugOutput, final boolean showLineCount)
            throws PwmOperationalException, IOException
    {
        if (outputStream == null) {
            throw new PwmOperationalException(PwmError.ERROR_UNKNOWN,"outputFileStream for exportLocalDB cannot be null");
        }

        writeStringToOut(debugOutput,"counting records in LocalDB...");
        final int totalLines;
        if (showLineCount) {
            exportLineCounter = 0;
            for (final LocalDB.DB loopDB : LocalDB.DB.values()) {
                if (!BACKUP_IGNORE_DBs.contains(loopDB)) {
                    exportLineCounter += localDB.size(loopDB);
                }
            }
            totalLines = exportLineCounter;
            writeStringToOut(debugOutput," total lines: " + totalLines);
        } else {
            totalLines = 0;
        }
        exportLineCounter = 0;

        writeStringToOut(debugOutput,"export beginning");
        final long startTime = System.currentTimeMillis();
        final Timer statTimer = new Timer(true);
        statTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                final String percentStr;
                if (showLineCount) {
                    final float percentComplete = (float) exportLineCounter / (float) totalLines;
                    percentStr = DecimalFormat.getPercentInstance().format(percentComplete);
                } else {
                    percentStr = "n/a";
                }

                writeStringToOut(debugOutput," exported " + exportLineCounter + " records, " + percentStr + " complete");
            }
        },30 * 1000, 30 * 1000);


        final CSVPrinter csvPrinter = Helper.makeCsvPrinter(new GZIPOutputStream(outputStream));
        try {
            csvPrinter.printComment(PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION + " LocalDB export on " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date()));
            for (LocalDB.DB loopDB : LocalDB.DB.values()) {
                if (!BACKUP_IGNORE_DBs.contains(loopDB)) {
                    csvPrinter.printComment("Export of " + loopDB.toString());
                    final LocalDB.LocalDBIterator<String> localDBIterator = localDB.iterator(loopDB);
                    try {
                        while (localDBIterator.hasNext()) {
                            final String key = localDBIterator.next();
                            final String value = localDB.get(loopDB, key);
                            csvPrinter.printRecord(loopDB.toString(), key, value);
                            exportLineCounter++;
                        }
                    } finally {
                        localDBIterator.close();
                    }
                }
            }
        } finally {
            if (csvPrinter != null) {
                csvPrinter.printComment("export completed at " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date()));
                csvPrinter.close();
            }
        }

        writeStringToOut(debugOutput, "export complete, exported " + exportLineCounter + " records in " + TimeDuration.fromCurrent(startTime).asLongString());
        statTimer.cancel();
    }

    private static void writeStringToOut(final Appendable out, final String string) {
        if (out == null) {
            return;
        }

        final String msg = PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date()) + " " + string + "\n";

        try {
            out.append(msg);
        } catch (IOException e) {
            LOGGER.error("error writing to output appender while performing operation: " + e.getMessage() + ", message:" + msg);
        }
    }

    public void importLocalDB(final File inputFile, final PrintStream out)
            throws PwmOperationalException, IOException
    {
        if (inputFile == null) {
            throw new PwmOperationalException(PwmError.ERROR_UNKNOWN,"inputFile for importLocalDB cannot be null");
        }

        if (!inputFile.exists()) {
            throw new PwmOperationalException(PwmError.ERROR_UNKNOWN,"inputFile for importLocalDB does not exist");
        }

        writeStringToOut(out, "counting records in input file...");
        importLineCounter = 0;
        Reader csvReader = null;
        try {
            csvReader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(inputFile)),PwmConstants.DEFAULT_CHARSET));
            for (final Iterator<CSVRecord> records = PwmConstants.DEFAULT_CSV_FORMAT.parse(csvReader).iterator(); records.hasNext();) {
                records.next();
                importLineCounter++;
            }
        } finally {
            if (csvReader != null) {csvReader.close();}
        }
        final int totalLines = importLineCounter;

        if (totalLines <= 0) {
            throw new PwmOperationalException(PwmError.ERROR_UNKNOWN,"inputFile for importLocalDB is empty");
        }

        final InputStream inputStream = new FileInputStream(inputFile);
        importLocalDB(inputStream, out, totalLines);
    }

    public void importLocalDB(final InputStream inputStream, final Appendable out)
            throws PwmOperationalException, IOException
    {
        importLocalDB(inputStream, out, 0);
    }

    private void importLocalDB(final InputStream inputStream, final Appendable out, final int totalLines)
            throws PwmOperationalException, IOException
    {
        this.prepareForImport();

        importLineCounter = 0;
        if (totalLines > 0) writeStringToOut(out, " total lines: " + totalLines);

        writeStringToOut(out, "beginning restore...");

        final Date startTime = new Date();
        final TransactionSizeCalculator transactionCalculator = new TransactionSizeCalculator(900, 50, 50 * 1000);

        final Map<LocalDB.DB,Map<String,String>> transactionMap = new HashMap<>();
        for (final LocalDB.DB loopDB : LocalDB.DB.values()) {
            transactionMap.put(loopDB,new TreeMap<String, String>());
        }

        final Timer statTimer = new Timer(true);
        statTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run()
            {
                if (totalLines > 0) {
                    final ProgressInfo progressInfo = new ProgressInfo(startTime, totalLines, importLineCounter);
                    writeStringToOut(out,
                            " " + progressInfo.debugOutput() + ", transactionSize=" + transactionCalculator.getTransactionSize());
                } else {
                    writeStringToOut(out, " linesImported=" + importLineCounter);
                }
            }
        }, 0, 30 * 1000);


        Reader csvReader = null;
        try {
            csvReader = new InputStreamReader(new GZIPInputStream(inputStream),PwmConstants.DEFAULT_CHARSET);
            for (final CSVRecord record : PwmConstants.DEFAULT_CSV_FORMAT.parse(csvReader)) {
                importLineCounter++;
                final LocalDB.DB db = LocalDB.DB.valueOf(record.get(0));
                final String key = record.get(1);
                final String value = record.get(2);
                transactionMap.get(db).put(key, value);
                int cachedTransactions = 0;
                importLineCounter++;
                for (final LocalDB.DB loopDB : LocalDB.DB.values()) {
                    cachedTransactions += transactionMap.get(loopDB).size();
                }
                if (cachedTransactions >= transactionCalculator.getTransactionSize()) {
                    final long startTxnTime = System.currentTimeMillis();
                    for (final LocalDB.DB loopDB : LocalDB.DB.values()) {
                        localDB.putAll(loopDB, transactionMap.get(loopDB));
                        transactionMap.get(loopDB).clear();
                    }
                    transactionCalculator.recordLastTransactionDuration(TimeDuration.fromCurrent(startTxnTime));
                }
            }
        } finally {
            if (csvReader != null) {csvReader.close();}
            statTimer.cancel();
        }

        for (final LocalDB.DB loopDB : LocalDB.DB.values()) {
            localDB.putAll(loopDB, transactionMap.get(loopDB));
            transactionMap.get(loopDB).clear();
        }

        this.markImportComplete();

        writeStringToOut(out, "restore complete, restored " + importLineCounter + " records in " + TimeDuration.fromCurrent(startTime).asLongString());
        statTimer.cancel();
    }


    public static Map<STATS_KEY, Object> dbStats(
            final LocalDB localDB,
            final LocalDB.DB db
    )
    {
        int compressedValues = 0;
        int totalValues = 0;
        int uncompressedValues = 0;
        long storedChars = 0;
        long uncompressedChars = 0;
        long compressedCharSavings = 0;

        final LocalDBCompressor compressorLocalDB = localDB instanceof LocalDBCompressor
                ? (LocalDBCompressor) localDB
                : new LocalDBCompressor(localDB, 0, true);

        LocalDB.LocalDBIterator<String> iter = null;
        try {
            iter = compressorLocalDB.iterator(db);
            while (iter.hasNext()) {
                final String key = iter.next();
                final String rawValue = compressorLocalDB.innerLocalDB.get(db, key);
                if (rawValue != null) {
                    totalValues++;
                    storedChars += rawValue.length();
                    if (rawValue.startsWith(LocalDBCompressor.COMPRESS_PREFIX)) {
                        compressedValues++;

                        final String uncompressedValue = compressorLocalDB.get(db, key);
                        uncompressedChars += uncompressedValue.length();

                        final int diff = uncompressedValue.length() - rawValue.length();
                        compressedCharSavings += diff;
                    } else {
                        uncompressedValues++;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("error while examining LocalDB: " + e.getMessage());
        } finally {
            if (iter != null) {
                iter.close();
            }
        }

        int avgValueLength = totalValues == 0 ? 0 : (int)(uncompressedChars / totalValues);
        final Map<STATS_KEY, Object> returnObj = new LinkedHashMap<>();
        returnObj.put(STATS_KEY.TOTAL_VALUES,totalValues);
        returnObj.put(STATS_KEY.COMPRESSED_VALUES,compressedValues);
        returnObj.put(STATS_KEY.UNCOMPRESSED_VALUES,uncompressedValues);
        returnObj.put(STATS_KEY.UNCOMPRESSED_CHARS,uncompressedChars);
        returnObj.put(STATS_KEY.COMPRESSED_CHAR_DIFF,compressedCharSavings);
        returnObj.put(STATS_KEY.STORED_CHARS,storedChars);
        returnObj.put(STATS_KEY.AVG_VALUE_LENGTH,avgValueLength);
        return returnObj;
    }

    public enum STATS_KEY {
        TOTAL_VALUES,
        COMPRESSED_VALUES,
        UNCOMPRESSED_VALUES,
        UNCOMPRESSED_CHARS,
        STORED_CHARS,
        COMPRESSED_CHAR_DIFF,
        AVG_VALUE_LENGTH,
    }

    public void prepareForImport()
            throws LocalDBException
    {
        LOGGER.info("preparing LocalDB for import procedure");
        localDB.put(LocalDB.DB.PWM_META, PwmApplication.AppAttribute.LOCALDB_IMPORT_STATUS.getKey(),"inprogress");
        for (final LocalDB.DB loopDB : LocalDB.DB.values()) {
            if (loopDB != LocalDB.DB.PWM_META) {
                localDB.truncate(loopDB);
            }
        }
        localDB.truncate(LocalDB.DB.PWM_META); // save meta for last so flag is cleared last.
        localDB.put(LocalDB.DB.PWM_META, PwmApplication.AppAttribute.LOCALDB_IMPORT_STATUS.getKey(),"inprogress");
    }

    public void markImportComplete()
            throws LocalDBException
    {
        LOGGER.info("marking LocalDB import procedure completed");
        localDB.remove(LocalDB.DB.PWM_META, PwmApplication.AppAttribute.LOCALDB_IMPORT_STATUS.getKey());
    }

    public boolean readImportInprogressFlag()
            throws LocalDBException
    {
        return "inprogress".equals(
                localDB.get(LocalDB.DB.PWM_META, PwmApplication.AppAttribute.LOCALDB_IMPORT_STATUS.getKey()));
    }
}
