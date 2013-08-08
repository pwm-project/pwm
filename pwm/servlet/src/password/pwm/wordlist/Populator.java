/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

package password.pwm.wordlist;

import password.pwm.PwmService;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.*;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Jason D. Rivard
 */
class Populator {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(Populator.class);


    private static final int MAX_LINE_LENGTH = 64; // words truncated to this length, prevents massive words if the input

    private static final long DEBUG_OUTPUT_FREQUENCY = 3 * 60 * 1000;  // 3 minutes

    private static final String COMMENT_PREFIX = "!#comment:"; // words tarting with this prefix are ignored.
    private static final NumberFormat PERCENT_FORMAT = DecimalFormat.getPercentInstance();

    private final ZipReader zipFileReader;

    private volatile boolean abortFlag;
    private volatile PwmService.STATUS status = PwmService.STATUS.NEW;

    private final PopulationStats overallStats = new PopulationStats();
    private PopulationStats perReportStats = new PopulationStats();
    private int totalLines;
    private TransactionSizeCalculator transactionCalculator = new TransactionSizeCalculator(600, 900, 10, 50 * 1000);
    private int loopLines;

    private final Map<String,String> bufferedWords = new TreeMap<String,String>();

    private final Sleeper sleeper;

    private final LocalDB.DB wordlistDB;
    private final LocalDB.DB wordlistMetaDB;
    private final LocalDB localDB;

    private final String DEBUG_LABEL;

    private final AbstractWordlist rootWordlist;

// -------------------------- STATIC METHODS --------------------------

    static {
        PERCENT_FORMAT.setMinimumFractionDigits(2);
    }

// --------------------------- CONSTRUCTORS ---------------------------

    public Populator(
            final ZipReader zipFileReader,
            final Sleeper sleeper,
            final AbstractWordlist rootWordlist
    )
            throws Exception
    {
        this.zipFileReader = zipFileReader;
        this.localDB = rootWordlist.localDB;
        this.wordlistDB = rootWordlist.WORD_DB;
        this.wordlistMetaDB = rootWordlist.META_DB;
        this.sleeper = sleeper;
        this.DEBUG_LABEL = rootWordlist.DEBUG_LABEL;
        this.rootWordlist = rootWordlist;

        sleeper.reset();
    }

    public void init()
            throws Exception
    {
        status = PwmService.STATUS.NEW;

        LOGGER.info(
                DEBUG_LABEL + " using source ZIP file of "
                        + zipFileReader.getSourceFile().getAbsolutePath()
                        + " (" + zipFileReader.getSourceFile().length() + " bytes)"
        );

        wordlistSize(zipFileReader.getSourceFile());

        if (abortFlag) return;

        final Object lastLineValue = localDB.get(wordlistMetaDB, WordlistManager.KEY_LASTLINE);
        if (lastLineValue != null) {
            final int startLine = Integer.parseInt(lastLineValue.toString());
            while (startLine > overallStats.getLines()) {
                overallStats.incrementLines();
            }

            final Object elapsedSecondsValue = localDB.get(wordlistMetaDB, WordlistManager.KEY_ELAPSEDSECONDS);
            if (elapsedSecondsValue != null) {
                final int elapsedSeconds = Integer.parseInt(elapsedSecondsValue.toString());
                overallStats.incrementElapsedSeconds(elapsedSeconds);
            }

            LOGGER.info(DEBUG_LABEL + " resuming from line " + startLine + " of " + totalLines + " lines (" + percentComplete() + ") elapsed time " + TimeDuration.asCompactString(overallStats.getElapsedSeconds() * 1000));
        } else {
            LOGGER.info(DEBUG_LABEL + " source ZIP has " + totalLines + " lines");
        }

        localDB.put(wordlistMetaDB, WordlistManager.KEY_STATUS, WordlistManager.VALUE_STATUS.DIRTY.toString());

        if (overallStats.getLines() > 0) {
            for (int i = 0; i < overallStats.getLines(); i++) {
                zipFileReader.nextLine();
            }
        }
        status = PwmService.STATUS.OPEN;
    }

    private void wordlistSize(final File wordlistFile)
            throws Exception
    {
        LOGGER.trace(DEBUG_LABEL + " beginning to count total input ZIP file lines");
        final ZipReader zipFileReader = new ZipReader(wordlistFile);
        totalLines = 0;

        while (zipFileReader.nextLine() != null) {
            totalLines++;
            if (abortFlag) return;
        }

        LOGGER.trace(DEBUG_LABEL + " input file line counting complete at " + totalLines + " lines");
    }

    public String percentComplete()
    {
        if (totalLines <= 0) {
            return "0%";
        }

        final float percentComplete = ((float) overallStats.getLines() / (float) totalLines);
        return PERCENT_FORMAT.format(percentComplete);
    }

// -------------------------- OTHER METHODS --------------------------

    public void pause()
    {
        abortFlag = true;
        final long startCloseTime = System.currentTimeMillis();

        try {
            LOGGER.info(makeStatString());
        } catch (Exception e) {
            System.currentTimeMillis();
        }
        LOGGER.info(DEBUG_LABEL + " pausing population (" + percentComplete() + ") elapsed time " + TimeDuration.asCompactString(overallStats.getElapsedSeconds() * 1000));

        try {
            localDB.put(wordlistMetaDB, WordlistManager.KEY_ELAPSEDSECONDS, String.valueOf(overallStats.getElapsedSeconds()));
            localDB.put(wordlistMetaDB, WordlistManager.KEY_STATUS, WordlistManager.VALUE_STATUS.IN_PROGRESS.toString());
        } catch (Exception e) {
            LOGGER.warn(DEBUG_LABEL + " unable to cleanly pause wordlist population: " + e.getMessage());
        }

        while (status != PwmService.STATUS.CLOSED && TimeDuration.fromCurrent(startCloseTime).isShorterThan(120 * 1000)) {
            LOGGER.info("waiting for populator to close");
            Helper.pause(1000);
        }
    }

    public String makeStatString()
    {
        if (status == PwmService.STATUS.NEW) {
            return "initializing, examining wordlist, lines read=" + totalLines;
        }

        final int lps = perReportStats.getElapsedSeconds() <= 0 ? 0 : perReportStats.getLines() / perReportStats.getElapsedSeconds();
        final int linesRemaining = totalLines - overallStats.getLines();
        final int msRemaining = lps <= 0 ? 0 : (linesRemaining / lps) * 1000;

        final StringBuilder sb = new StringBuilder();

        sb.append(DEBUG_LABEL);
        sb.append(" population status: ").append(percentComplete());

        if (msRemaining > 0) {
            sb.append(" (");
            sb.append(new TimeDuration(msRemaining).asCompactString());
            sb.append(" remaining)");
        }

        sb.append(", lines/second=").append(lps);
        sb.append(", line=").append(overallStats.getLines());
        sb.append(")");
        sb.append(" current zipEntry=").append(zipFileReader.currentZipName());

        perReportStats = new PopulationStats();
        return sb.toString();
    }

    void populate() throws IOException, LocalDBException, PwmUnrecoverableException {

        try {
            long lastReportTime = System.currentTimeMillis() - (long)(DEBUG_OUTPUT_FREQUENCY * 0.33);

            sleeper.reset();
            String line;
            while (!abortFlag && (line = zipFileReader.nextLine()) != null) {
                sleeper.sleep();

                overallStats.incrementLines();
                perReportStats.incrementLines();

                addLine(line);
                loopLines++;

                if (TimeDuration.fromCurrent(lastReportTime).isLongerThan(DEBUG_OUTPUT_FREQUENCY)) {
                    LOGGER.info(makeStatString());
                    lastReportTime = System.currentTimeMillis();
                }

                if (bufferedWords.size() > transactionCalculator.getTransactionSize()) {
                    flushBuffer();
                }
            }
        } finally {
            zipFileReader.close();
        }

        if (abortFlag) {
            LOGGER.warn("pausing " + DEBUG_LABEL + " population");
        } else {
            populationComplete();
        }

        status = PwmService.STATUS.CLOSED;
    }

    private void addLine(String line)
            throws IOException
    {
        // check for word suitability
        line = rootWordlist.normalizeWord(line);

        if (line == null || line.length() < 1 || line.startsWith(COMMENT_PREFIX)) {
            return;
        }

        if (line.length() > MAX_LINE_LENGTH) {
            line = line.substring(0,MAX_LINE_LENGTH);
        }

        final Map<String,String> wordTxn = rootWordlist.getWriteTxnForValue(line);
        bufferedWords.putAll(wordTxn);
    }

    private void flushBuffer()
            throws LocalDBException
    {
        final long startTime = System.currentTimeMillis();

        //add the elements
        localDB.putAll(wordlistDB, bufferedWords);

        //update the src ZIP line counter in the localdb.
        localDB.put(wordlistMetaDB, WordlistManager.KEY_LASTLINE, String.valueOf(overallStats.getLines()));

        if (abortFlag) {
            return;
        }

        //mark how long the buffer close took
        final long commitTime = System.currentTimeMillis() - startTime;
        transactionCalculator.recordLastTransactionDuration(commitTime);

        if (bufferedWords.size() > 0) {
            final StringBuilder sb = new StringBuilder();
            sb.append(DEBUG_LABEL).append(" ");
            sb.append("read ").append(loopLines).append(", ");
            sb.append("saved ");
            sb.append(bufferedWords.size()).append(" words");
            sb.append(" (").append(new TimeDuration(commitTime).asCompactString()).append(")");
            sb.append(" ").append(percentComplete()).append(" complete");

            LOGGER.trace(sb.toString());
        }

        //clear the buffers.
        bufferedWords.clear();
        loopLines = 0;
    }

    private void populationComplete()
            throws LocalDBException, PwmUnrecoverableException
    {
        flushBuffer();
        localDB.put(wordlistMetaDB, WordlistManager.KEY_STATUS, WordlistManager.VALUE_STATUS.IN_PROGRESS.toString());
        LOGGER.info(makeStatString());
        LOGGER.trace("beginning wordlist size query");
        final int wordlistSize = localDB.size(wordlistDB);
        if (wordlistSize > 0) {
            localDB.put(wordlistMetaDB, WordlistManager.KEY_SIZE, String.valueOf(wordlistSize));
            localDB.put(wordlistMetaDB, WordlistManager.KEY_STATUS, WordlistManager.VALUE_STATUS.COMPLETE.toString());
        } else {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, DEBUG_LABEL + " population completed, but no words stored"));
        }

        final StringBuilder sb = new StringBuilder();
        sb.append(DEBUG_LABEL);
        sb.append(" population complete, added ").append(wordlistSize);
        sb.append(" total words in ").append(new TimeDuration(overallStats.getElapsedSeconds() * 1000).asCompactString());
        sb.append(", ignored ").append(totalLines - wordlistSize).append(" words");
        LOGGER.info(sb.toString());
    }

    private static class PopulationStats {
        // ------------------------------ FIELDS ------------------------------

        private long startTime = System.currentTimeMillis();
        private int lines;

        // --------------------- GETTER / SETTER METHODS ---------------------

        public int getLines()
        {
            return lines;
        }

        // -------------------------- OTHER METHODS --------------------------

        public void incrementLines()
        {
            lines++;
        }

        public int getElapsedSeconds()
        {
            return (int) (System.currentTimeMillis() - startTime) / 1000;
        }

        public void incrementElapsedSeconds(final int seconds) {
            startTime = startTime - (seconds * 1000);
        }
    }
}
