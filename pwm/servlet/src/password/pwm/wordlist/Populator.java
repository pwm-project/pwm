/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

import password.pwm.config.Message;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmException;
import password.pwm.util.PwmLogger;
import password.pwm.util.PwmRandom;
import password.pwm.util.Sleeper;
import password.pwm.util.TimeDuration;
import password.pwm.util.db.PwmDB;

import java.io.File;
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

    private static final long LOW_GOAL = 500; //try to do as many transacations as possible above this time limit (ms)
    private static final long HIGH_GOAL = 800; //try to do as many transacations as possible below this time limit (ms)
    private static final long OUT_OF_RANGE = 2000;

    private static final int MAX_TRANSACTION_SIZE = 50 * 1000; // maximum number of transactions (to big would cause OO<)
    private static final int MIN_TRANSACTION_SIZE = 10; // minimum number of transactions (to big

    private static final int MAX_LINE_LENGTH = 64; // words truncated to this length, prevents massive words if the input

    private static final long DEBUG_OUTPUT_FREQUENCY = 3 * 60 * 1000;  // 3 minutes

    private static final String COMMENT_PREFIX = "!#comment:"; // words tarting with this prefix are ignored.
    private static final NumberFormat PERCENT_FORMAT = DecimalFormat.getPercentInstance();

    private final ZipReader zipFileReader;
    private final boolean caseSensitive;

    private volatile boolean abortFlag;
    private final PopulationStats overallStats = new PopulationStats();
    private PopulationStats perReportStats = new PopulationStats();
    private final int totalLines;
    private int transactionSize = 200;
    private int loopLines;

    private final Map<String,String> bufferedWords = new TreeMap<String,String>();

    private final Sleeper sleeper;

    private final PwmDB.DB wordlistDB;
    private final PwmDB.DB wordlistMetaDB;
    private final PwmDB pwmDB;

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
        this.pwmDB = rootWordlist.pwmDB;
        this.wordlistDB = rootWordlist.WORD_DB;
        this.wordlistMetaDB = rootWordlist.META_DB;
        this.sleeper = sleeper;
        this.caseSensitive = rootWordlist.caseSensitive;
        this.DEBUG_LABEL = rootWordlist.DEBUG_LABEL;
        this.rootWordlist = rootWordlist;

        sleeper.reset();

        LOGGER.info(
                DEBUG_LABEL + " using source ZIP file of "
                        + zipFileReader.getSourceFile().getAbsolutePath()
                        + " (" + zipFileReader.getSourceFile().length() + " bytes)"
        );

        totalLines = wordlistSize(zipFileReader.getSourceFile());

        if (abortFlag) return;

        final Object lastLineValue = pwmDB.get(wordlistMetaDB, WordlistManager.KEY_LASTLINE);
        if (lastLineValue != null) {
            final int startLine = Integer.parseInt(lastLineValue.toString());
            while (startLine > overallStats.getLines()) {
                overallStats.incrementLines();
            }

            final Object elapsedSecondsValue = pwmDB.get(wordlistMetaDB, WordlistManager.KEY_ELAPSEDSECONDS);
            if (elapsedSecondsValue != null) {
                final int elapsedSeconds = Integer.parseInt(elapsedSecondsValue.toString());
                overallStats.incrementElapsedSeconds(elapsedSeconds);
            }

            LOGGER.info(DEBUG_LABEL + " resuming from line " + startLine + " of " + totalLines + " lines (" + percentComplete() + ") elapsed time " + TimeDuration.asCompactString(overallStats.getElapsedSeconds() * 1000));
        } else {
            LOGGER.info(DEBUG_LABEL + " source ZIP has " + totalLines + " lines");
        }

        pwmDB.put(wordlistMetaDB, WordlistManager.KEY_STATUS, WordlistManager.VALUE_STATUS.DIRTY.toString());
    }

    private int wordlistSize(final File wordlistFile)
            throws Exception
    {
        LOGGER.trace(DEBUG_LABEL + " beginning to count total input ZIP file lines");
        final ZipReader zipFileReader = new ZipReader(wordlistFile);
        int counter = 0;

        sleeper.reset();

        while (zipFileReader.nextLine() != null) {
            counter++;
            sleeper.sleep();

            if (abortFlag) return -1;
        }

        LOGGER.trace(DEBUG_LABEL + " input file line counting complete at " + counter + " lines");
        return counter;
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
        try {
            LOGGER.info(makeStatString());
        } catch (Exception e) {
            System.currentTimeMillis();
        }
        LOGGER.info(DEBUG_LABEL + " pausing population (" + percentComplete() + ") elapsed time " + TimeDuration.asCompactString(overallStats.getElapsedSeconds() * 1000));

        try {
            pwmDB.put(wordlistMetaDB, WordlistManager.KEY_ELAPSEDSECONDS, String.valueOf(overallStats.getElapsedSeconds()));
            pwmDB.put(wordlistMetaDB, WordlistManager.KEY_STATUS, WordlistManager.VALUE_STATUS.IN_PROGRESS.toString());
        } catch (Exception e) {
            LOGGER.warn(DEBUG_LABEL + " unable to cleanly pause wordlist population: " + e.getMessage());
        }
    }

    private String makeStatString()
            throws Exception
    {
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

    void populate()
            throws Exception
    {
        try {
            long lastReportTime = System.currentTimeMillis() - (long)(DEBUG_OUTPUT_FREQUENCY * 0.33);

            if (overallStats.getLines() > 0) {
                for (int i = 0; i < overallStats.getLines(); i++) {
                    zipFileReader.nextLine();

                    if (abortFlag) {
                        throw new Exception("pausing " + DEBUG_LABEL + " population");
                    }
                }
            }

            sleeper.reset();
            String line;
            while ((line = zipFileReader.nextLine()) != null) {
                sleeper.sleep();

                if (abortFlag) {
                    throw PwmException.createPwmException(new ErrorInformation(Message.ERROR_UNKNOWN,"pausing " + DEBUG_LABEL + " population"));
                }

                overallStats.incrementLines();
                perReportStats.incrementLines();

                addLine(line);

                if (!abortFlag && (System.currentTimeMillis() - lastReportTime) > DEBUG_OUTPUT_FREQUENCY) {
                    LOGGER.info(makeStatString());
                    lastReportTime = System.currentTimeMillis();
                }

                if (bufferedWords.size() > transactionSize) {
                    flushBuffer();
                }
            }

            populationComplete();
        } finally {
            zipFileReader.close();
        }
    }

    private void addLine(String line)
            throws Exception
    {
        // check for word suitability
        if (line == null) {
            return;
        }

        line = line.trim();

        if (line.length() < 1 || line.startsWith(COMMENT_PREFIX)) {
            return;
        }

        if (line.length() > MAX_LINE_LENGTH) {
            line = line.substring(0, MAX_LINE_LENGTH);
        }

        if (!caseSensitive) line = line.toLowerCase();

        final Map<String,String> wordTxn = rootWordlist.getWriteTxnForValue(line);

        // add word to buffered word list
        bufferedWords.putAll(wordTxn);

        loopLines++;
    }

    private void flushBuffer()
            throws Exception
    {
        final long startTime = System.currentTimeMillis();

        //add the elements
        pwmDB.putAll(wordlistDB, bufferedWords);

        //update the src ZIP line counter in the db.
        pwmDB.put(wordlistMetaDB, WordlistManager.KEY_LASTLINE, String.valueOf(overallStats.getLines()));

        if (abortFlag) {
            return;
        }

        //mark how long the buffer flush took
        final long commitTime = System.currentTimeMillis() - startTime;
        transactionSize = calcTransactionSize(commitTime, bufferedWords.size());

        if (transactionSize > 0) {
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

    private static int calcTransactionSize(final long lastCommitTime, final int transactionSize)
    {
        int newTransactionSize;

        if (lastCommitTime < LOW_GOAL) {
            newTransactionSize = ((int) (transactionSize + (transactionSize * 0.1)) + 1);
        } else if (lastCommitTime > HIGH_GOAL && lastCommitTime < OUT_OF_RANGE) {
            newTransactionSize = ((int) (transactionSize - (transactionSize * 0.1)) - 1);
        } else if (lastCommitTime > OUT_OF_RANGE) {
            newTransactionSize = (int) (transactionSize * 0.5);
        } else {
            newTransactionSize = transactionSize + PwmRandom.getInstance().nextInt(10);
        }

        newTransactionSize = newTransactionSize > MAX_TRANSACTION_SIZE ? MAX_TRANSACTION_SIZE : newTransactionSize;
        newTransactionSize = newTransactionSize < MIN_TRANSACTION_SIZE ? MIN_TRANSACTION_SIZE : newTransactionSize;
        return newTransactionSize;
    }

    private void populationComplete()
            throws Exception
    {
        flushBuffer();
        LOGGER.info(makeStatString());

        final int wordlistSize = pwmDB.size(wordlistDB);
        if (wordlistSize > 0) {
            pwmDB.put(wordlistMetaDB, WordlistManager.KEY_STATUS, WordlistManager.VALUE_STATUS.COMPLETE.toString());
        } else {
            throw new Exception(DEBUG_LABEL + " population completed, but no words stored");
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

        public long getStartTime()
        {
            return startTime;
        }

    // -------------------------- OTHER METHODS --------------------------

        public void incrementLines()
        {
            lines++;
        }

        public int linesPerSecond()
        {
            final int elapsedSeconds = getElapsedSeconds();
            return elapsedSeconds == 0 ? lines : lines / elapsedSeconds;
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
