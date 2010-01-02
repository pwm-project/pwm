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

import password.pwm.Helper;
import password.pwm.PwmSession;
import password.pwm.error.PwmException;
import password.pwm.util.PwmLogger;
import password.pwm.util.Sleeper;
import password.pwm.util.stats.Statistic;
import password.pwm.util.TimeDuration;
import password.pwm.util.db.PwmDB;

import java.io.File;
import java.util.Map;

abstract class AbstractWordlist implements Wordlist {
    protected PwmDB.DB META_DB = null;
    protected PwmDB.DB WORD_DB = null;

    protected boolean caseSensitive = false;
    protected int loadFactor = 0;

    protected File wordlistFile = null;

    protected volatile WordlistStatus wlStatus = WordlistStatus.CLOSED;
    protected PwmDB pwmDB;
    protected Populator populator;

    protected PwmLogger LOGGER = PwmLogger.getLogger(AbstractWordlist.class);
    protected String DEBUG_LABEL = "Generic Wordlist";


// --------------------------- CONSTRUCTORS ---------------------------

    protected AbstractWordlist(
            final File wordlistFile,
            final PwmDB pwmDB,
            final int loadFactor,
            final boolean caseSensitive
    )
    {
        this.caseSensitive = caseSensitive;
        this.wordlistFile = wordlistFile;
        this.pwmDB = pwmDB;
        this.loadFactor = loadFactor;
    }

    protected void init()
    {
        final long startTime = System.currentTimeMillis();
        wlStatus = WordlistStatus.OPENING;

        if (pwmDB == null) {
            LOGGER.warn("pwmDB is not available, " + DEBUG_LABEL + " will remain closed");
            close();
            return;
        }

        if (wordlistFile == null) {
            LOGGER.warn("wordlist file is not specified, " + DEBUG_LABEL + " will remain closed");
            try {
                resetDB("-1");
            } catch (Exception e) {
                LOGGER.error("error while clearing wordlist DB.");
            }
            close();
            return;
        }

        if (!wordlistFile.exists()) {
            LOGGER.warn("wordlist file \"" + wordlistFile.getAbsolutePath() + "\" does not exist, " + DEBUG_LABEL + "will remain closed");
            close();
            return;
        }

        try {
            checkPopulation();
        } catch (Exception e) {
            if (!(e instanceof PwmException)) {
                LOGGER.warn("unexpected error while examining wordlist db: " + e.getMessage(), e);
            }
            populator = null;
            close();
            return;
        }

        if (wlStatus == WordlistStatus.OPENING || wlStatus == WordlistStatus.POPULATING) {
            wlStatus = WordlistStatus.OPEN;
            final int wordlistSize = size();
            final TimeDuration totalTime = TimeDuration.fromCurrent(startTime);
            LOGGER.debug(DEBUG_LABEL + " open with " + wordlistSize + " words in " + totalTime.asCompactString());
        } else {
            LOGGER.warn(DEBUG_LABEL + " status changed unexpectedly during startup, closing");
            close();
        }
    }

    private String normalizeWord(final String input) {
        if (input == null) {
            return null;
        }

        String word = input.trim();

        if (!caseSensitive) {
            word = word.toLowerCase();
        }

        return word.length() > 0 ? word : null;
    }

    protected void checkPopulation()
            throws Exception
    {
        LOGGER.trace("calculating checksum of " + wordlistFile.getAbsolutePath());
        final StringBuilder checksum = new StringBuilder();
        checksum.append("checksum=").append(Helper.md5sum(wordlistFile));
        checksum.append(",caseSensitive=").append(caseSensitive);
        checksum.append(",lastModifed=").append(wordlistFile.lastModified());
        checksum.append(",length=").append(wordlistFile.length());
        LOGGER.trace("checksum of " + wordlistFile.getAbsolutePath() + " complete, result: " + checksum);

        final boolean clearRequired = !checkDbStatus() || !checkDbVersion() || !checkChecksum(checksum.toString());
        final boolean isComplete = !clearRequired && VALUE_STATUS.COMPLETE.equals(VALUE_STATUS.forString(pwmDB.get(META_DB, KEY_STATUS)));

        if (!clearRequired && isComplete) {
            return;
        }

        LOGGER.debug(DEBUG_LABEL + " population incomplete, running populator");

        if (clearRequired) {
            resetDB(checksum.toString());
        }

        if (wlStatus != WordlistStatus.OPENING) {
            LOGGER.warn(DEBUG_LABEL + " changed unexpectedly during startup, closing");
            close();
            return;
        }

        wlStatus = WordlistStatus.POPULATING;
        populator = new Populator(
                new ZipReader(wordlistFile),
                new Sleeper(loadFactor),
                this
        );
        populator.populate();
        populator = null;
    }

    private boolean checkChecksum(final String checksum)
            throws Exception
    {
        LOGGER.trace("checking wordlist file checksum stored in pwmDB");

        final Object checksumInDb = pwmDB.get(META_DB, KEY_CHECKSUM);
        final boolean result = checksum.equals(checksumInDb);

        if (!result) {
            LOGGER.info("existing ZIP checksum does not match current wordlist file, db=(" + checksumInDb + "), file=(" + checksum + "), clearing db");
        } else {
            LOGGER.trace("existing ZIP checksum matches current wordlist file, db=(" + checksumInDb + "), file=(" + checksum + ")");
        }

        return result;
    }

    private boolean checkDbStatus()
            throws Exception
    {
        LOGGER.trace("checking " + DEBUG_LABEL + " db status");

        final VALUE_STATUS statusInDb = VALUE_STATUS.forString(pwmDB.get(META_DB, KEY_STATUS));

        if (statusInDb == null || statusInDb.equals(VALUE_STATUS.DIRTY)) {
            LOGGER.info("existing db was not shut down cleanly during population, clearing db");
            return false;
        } else {
            LOGGER.trace("existing db is clean");
            return true;
        }
    }

    private boolean checkDbVersion()
            throws Exception
    {
        LOGGER.trace("checking version number stored in pwmDB");

        final Object versionInDB = pwmDB.get(META_DB, KEY_VERSION);
        final boolean result = VALUE_VERSION.equals(versionInDB);

        if (!result) {
            LOGGER.info("existing db version does not match current db version db=(" + versionInDB + ")  pwm=(" + VALUE_VERSION + "), clearing db");
        } else {
            LOGGER.trace("existing db version matches current db version db=(" + versionInDB + ")  pwm=(" + VALUE_VERSION + ")");
        }

        return result;
    }

    private void resetDB(final String checksum)
            throws Exception
    {
        pwmDB.put(META_DB, KEY_VERSION, VALUE_VERSION + "_ClearInProgress");

        for (final PwmDB.DB db : new PwmDB.DB[]{META_DB, WORD_DB}) {
            LOGGER.debug("clearing " + db + " (" + pwmDB.size(db) + ")");
            pwmDB.truncate(db);
        }

        pwmDB.put(META_DB, KEY_VERSION, VALUE_VERSION);
        pwmDB.put(META_DB, KEY_CHECKSUM, checksum);
    }

    public boolean containsWord(final PwmSession pwmSession, final String word)
    {
        if (!wlStatus.isAvailable()) {
            return false;
        }

        final String testWord = normalizeWord(word);

        if (testWord == null) {
            return false;
        }

        try {
            final long startTime = System.currentTimeMillis();
            final boolean result = pwmDB.contains(WORD_DB, testWord);
            final long totalTime = (System.currentTimeMillis() - startTime);

            if (pwmSession != null) {
                LOGGER.trace(pwmSession, "successfully checked word, result=" + result + ", duration=" + TimeDuration.asCompactString(totalTime));
                pwmSession.getContextManager().getStatisticsManager().updateAverageValue(Statistic.AVG_WORDLIST_CHECK_TIME,totalTime);
            }

            return result;
        } catch (Exception e) {
            LOGGER.error("database error checking for word: " + e.getMessage());
        }

        return false;
    }

    public int size()
    {
        return getDbSize(WORD_DB);
    }

    private int getDbSize(final PwmDB.DB db)
    {
        if (!wlStatus.isAvailable()) {
            return 0;
        }

        try {
            return pwmDB.size(db);
        } catch (Exception e) {
            LOGGER.error("database error: " + e.getMessage());
        }

        return 0;
    }

    public synchronized void close()
    {
        if (populator != null) {
            populator.pause();
            populator = null;
        }

        if (wlStatus != WordlistStatus.CLOSED) {
            LOGGER.debug("closed");
        }

        wlStatus = WordlistStatus.CLOSED;
        pwmDB = null;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public File getWordlistFile()
    {
        return wordlistFile;
    }

// -------------------------- OTHER METHODS --------------------------

    public WordlistStatus getStatus()
    {
        return wlStatus;
    }

    public String getDebugStatus()
    {
        if (wlStatus == WordlistStatus.POPULATING && populator != null) {
            return wlStatus.toString() + " " + populator.percentComplete();
        } else {
            return wlStatus.toString();
        }
    }

    protected abstract Map<String,String> getWriteTxnForValue(String value);

// -------------------------- ENUMERATIONS --------------------------

    static enum VALUE_STATUS {
        COMPLETE,
        DIRTY,
        IN_PROGRESS;

        static VALUE_STATUS forString(final String status)
        {
            for (final VALUE_STATUS s : VALUE_STATUS.values()) {
                if (s.toString().equals(status)) {
                    return s;
                }
            }

            return null;
        }
    }
}
