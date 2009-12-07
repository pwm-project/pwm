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
import password.pwm.util.PwmLogger;
import password.pwm.util.PwmRandom;
import password.pwm.util.Sleeper;
import password.pwm.util.TimeDuration;
import password.pwm.util.db.PwmDB;
import password.pwm.util.db.PwmDBException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;


public class SharedHistoryManager implements Wordlist {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(SharedHistoryManager.class);

    private static final String KEY_SALT = "salt";
    private static final String KEY_OLDEST_ENTRY = "oldest_entry";
    private static final String KEY_VERSION = "version";

    private static final String VALUE_VERSION = "1";

    private static final int MIN_CLEANER_FREQUENCY = 1000 * 60 * 60; // 1 hour
    private static final int MAX_CLENAER_FREQUENCY = 1000 * 60 * 60 * 24; // 1 day

    private static final PwmDB.DB META_DB = PwmDB.DB.SHAREDHISTORY_META;
    private static final PwmDB.DB WORDS_DB = PwmDB.DB.SHAREDHISTORY_WORDS;


    private volatile WordlistStatus wlStatus = WordlistStatus.CLOSED;

    private volatile Timer cleanerTimer = null;

    private PwmDB pwmDB;
    private String salt;
    private long oldestEntry;
    private long maxAgeMs;

    private final boolean caseSensitive;


// -------------------------- STATIC METHODS --------------------------

    public static SharedHistoryManager createSharedHistoryManager(final PwmDB pwmDB, final long maxAgeMs, final boolean caseSensitive) throws Exception {
        return new SharedHistoryManager(pwmDB, maxAgeMs, caseSensitive);
    }

// --------------------------- CONSTRUCTORS ---------------------------

    private SharedHistoryManager(final PwmDB pwmDB, final long maxAgeMs, final boolean caseSensitive) throws PwmDBException {
        this.pwmDB = pwmDB;
        this.caseSensitive = caseSensitive;

        if (pwmDB == null) {
            LOGGER.info("pwmDB is not available, will remain closed");
            return;
        }

        if (maxAgeMs < 1) {
            LOGGER.debug("max age=" + maxAgeMs + ", will remain closed");

            new Thread(new Runnable() {
                public void run()
                {
                    LOGGER.trace("clearing wordlist");
                    try {
                        pwmDB.truncate(WORDS_DB);
                    } catch (Exception e) {
                        LOGGER.error("error during wordlist truncate", e);
                    }
                }
            }, "pwm-SharedHistoryManager wordlist truncate").start();
            return;
        }

        new Thread(new Runnable() {
            public void run()
            {
                LOGGER.debug("starting up in background thread");
                init(maxAgeMs);
            }
        }, "pwm-SharedHistoryManager initializer").start();

    }

// -------------------------- OTHER METHODS --------------------------

    public void close() {
        wlStatus = WordlistStatus.CLOSED;
        LOGGER.debug("closed");
        if (cleanerTimer != null) {
            cleanerTimer.cancel();
        }
        pwmDB = null;
    }

    public boolean containsWord(final PwmSession pwmSession, final String word) {
        if (wlStatus != WordlistStatus.OPEN) {
            return false;
        }

        final String testWord = normalizeWord(word);

        if (testWord == null) {
            return false;
        }

        final long startTime = System.currentTimeMillis();
        boolean result = false;

        try {
            final String hashedWord = hashWord(testWord);
            final boolean inDB = pwmDB.contains(WORDS_DB, hashedWord);
            if (inDB) {
                final long timeStamp = Long.parseLong(pwmDB.get(WORDS_DB, hashedWord));
                final long entryAge = System.currentTimeMillis() - timeStamp;
                if (entryAge < maxAgeMs) {
                    result = true;
                }
            }

        } catch (Exception e) {
            LOGGER.warn(pwmSession, "error checking global history list: " + e.getMessage());
        }

        LOGGER.trace(pwmSession, "successfully checked word, result=" + result + ", duration=" + new TimeDuration(System.currentTimeMillis(), startTime).asCompactString());
        return result;
    }

    public WordlistStatus getStatus() {
        return wlStatus;
    }

    public long getOldestEntryAge() {
        if (size() > 0) {
            return System.currentTimeMillis() - oldestEntry;
        } else {
            return 0;
        }
    }

    public int size() {
        if (pwmDB != null) {
            try {
                return pwmDB.size(WORDS_DB);
            } catch (Exception e) {
                LOGGER.error("error checking wordlist size: " + e.getMessage());
                return 0;
            }
        } else {
            return 0;
        }
    }


    private void checkSalt()
            throws Exception
    {
        salt = pwmDB.get(META_DB, KEY_SALT);
        if (salt == null || salt.length() < 1) {
            LOGGER.info("no salt found in DB, creating new salt and clearing global history");
            pwmDB.truncate(WORDS_DB);
            salt = PwmRandom.getInstance().nextLongHex() + PwmRandom.getInstance().nextLongHex();
            pwmDB.put(META_DB, KEY_SALT, salt);
            pwmDB.remove(META_DB, KEY_OLDEST_ENTRY);
        }
    }

    private boolean checkDbVersion()
            throws Exception
    {
        LOGGER.trace("checking version number stored in pwmDB");

        final Object versionInDB = pwmDB.get(META_DB, KEY_VERSION);
        final String pwmVersion = "version=" + VALUE_VERSION + ", caseSensitive=" + String.valueOf(caseSensitive);
        final boolean result = pwmVersion.equals(versionInDB);

        if (!result) {
            LOGGER.info("existing db version does not match current db version db=(" + versionInDB + ")  pwm=(" + pwmVersion + "), clearing db");
            pwmDB.truncate(WORDS_DB);
            pwmDB.put(META_DB, KEY_VERSION, pwmVersion);
            pwmDB.remove(META_DB, KEY_OLDEST_ENTRY);
        } else {
            LOGGER.trace("existing db version matches current db version db=(" + versionInDB + ")  pwm=(" + pwmVersion + ")");
        }

        return result;
    }

    private void init(final long maxAgeMs) {
        this.maxAgeMs = maxAgeMs;
        wlStatus = WordlistStatus.OPENING;
        final long startTime = System.currentTimeMillis();

        try {
            checkDbVersion();
        } catch (Exception e) {
            LOGGER.error("error checking db version", e);
            wlStatus = WordlistStatus.CLOSED;
            return;
        }

        try {
            checkSalt();
        } catch (Exception e) {
            LOGGER.error("unexpected error examing salt in DB, will remain closed: " + e.getMessage(),e);
            wlStatus = WordlistStatus.CLOSED;
            return;
        }

        try {
            final String oldestEntryStr = pwmDB.get(META_DB, KEY_OLDEST_ENTRY);
            if (oldestEntryStr == null || oldestEntryStr.length() < 1) {
                oldestEntry = 0;
                LOGGER.trace("no oldestEntry timestamp stored, will rescan");
            } else {
                oldestEntry = Long.parseLong(oldestEntryStr);
                LOGGER.trace("oldest timestamp loaded from pwmDB, age is " + TimeDuration.fromCurrent(oldestEntry).asCompactString());
            }
        } catch (PwmDBException e) {
            LOGGER.error("unexpected error loading oldest-entry meta record, will remain closed: " + e.getMessage(),e);
            wlStatus = WordlistStatus.CLOSED;
            return;
        }

        try {
            final int size = pwmDB.size(WORDS_DB);
            final StringBuilder sb = new StringBuilder();
            sb.append("open with ").append(size).append(" words (");
            sb.append(new TimeDuration(System.currentTimeMillis(), startTime).asCompactString()).append(")");
            sb.append(", maxAgeMs=").append(new TimeDuration(maxAgeMs).asCompactString());
            sb.append(", oldestEntry=").append(new TimeDuration(System.currentTimeMillis(), oldestEntry).asCompactString());
            LOGGER.info(sb.toString());
        } catch (PwmDBException e) {
            LOGGER.error("unexpected error examing size of DB, will remain closed: " + e.getMessage(),e);
            wlStatus = WordlistStatus.CLOSED;
            return;
        }

        wlStatus = WordlistStatus.OPEN;
        //populateFromWordlist();  //only used for debugging!!!

        {
            long frequencyMs = maxAgeMs > MAX_CLENAER_FREQUENCY ? MAX_CLENAER_FREQUENCY : maxAgeMs;
            frequencyMs = frequencyMs < MIN_CLEANER_FREQUENCY ? MIN_CLEANER_FREQUENCY : frequencyMs;

            LOGGER.debug("scheduling cleaner task to run once every " + new TimeDuration(frequencyMs).asCompactString());
            cleanerTimer = new Timer("pwm-SharedHistoryManager timer", true);
            cleanerTimer.schedule(new CleanerTask(),1000, frequencyMs);
        }
    }

    /*
    private void populateFromWordlist() { // Useful to populate the wordlist during debugging, but otherwise this is an irrelavant method.
        final Iterator<PwmDB.TransactionItem> iter;
        try {
            iter = pwmDB.iterator(PwmDB.DB.WORDLIST_WORDS);

            int counter = 0;
            while (counter < this.size() && iter.hasNext()) {
                counter ++;
                iter.next();
            }

            final long startTime = System.currentTimeMillis();
            while (iter.hasNext() && wlStatus == WordlistStatus.OPEN) {
                counter++;
                final PwmDB.TransactionItem item = iter.next();
                final String key = item.getKey();
                addWord(null, key);
            }

            System.out.println("Niter time = " + new TimeDuration(System.currentTimeMillis(), startTime).asCompactString());
        } catch (PwmDBException e) {
            e.printStackTrace();
        } finally {
            try {
                pwmDB.returnIterator(PwmDB.DB.WORDLIST_WORDS);
            } catch (PwmDBException e) {
                e.printStackTrace();
            }
        }
    }
    */

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

    public synchronized void addWord(final PwmSession pwmSession, final String word) {
        if (wlStatus != WordlistStatus.OPEN) {
            return;
        }

        final String addWord = normalizeWord(word);

        if (addWord == null) {
            return;
        }

        final long startTime = System.currentTimeMillis();

        try {
            final String hashedWord = hashWord(addWord);

            final boolean preExisting = pwmDB.contains(WORDS_DB, hashedWord);
            pwmDB.put(WORDS_DB, hashedWord, Long.toString(System.currentTimeMillis()));

            {
                final StringBuilder logOutput = new StringBuilder();
                logOutput.append(preExisting ? "updated" : "added").append(" word");
                logOutput.append(" (").append(new TimeDuration(System.currentTimeMillis(), startTime).asCompactString()).append(")");
                logOutput.append(" (").append(this.size()).append(" total words)");
                LOGGER.trace(logOutput.toString());
            }
        } catch (Exception e) {
            LOGGER.warn(pwmSession, "error adding word to global history list: " + e.getMessage());
        }
    }

    private String hashWord(final String word) throws NoSuchAlgorithmException {
        final MessageDigest md = MessageDigest.getInstance("SHA1");
        final String wordWithSalt = salt + word;
        final byte[] hashedAnswer = md.digest((wordWithSalt).getBytes());

        return Helper.binaryArrayToHex(hashedAnswer);
    }

    // -------------------------- INNER CLASSES --------------------------

    private class CleanerTask extends TimerTask {
        final Sleeper sleeper = new Sleeper(10);

        private CleanerTask() {
        }

        public void run() {
            try {
                reduceWordDB();
            } catch (PwmDBException e) {
                LOGGER.error("error during execution of reduce: " + e.getMessage(), e);
            }
        }


        private void reduceWordDB()
                throws PwmDBException
        {
            final long oldestEntryAge = System.currentTimeMillis() - oldestEntry;
            if (oldestEntryAge < maxAgeMs) {
                LOGGER.debug("skipping wordDB reduce operation, eldestEntry="
                        + TimeDuration.asCompactString(oldestEntryAge)
                        + ", maxAge="
                        + TimeDuration.asCompactString(maxAgeMs) );
                return;
            }

            final long startTime = System.currentTimeMillis();
            final int initialSize = size();
            int removeCount = 0;
            long localOldestEntry = System.currentTimeMillis();

            LOGGER.debug("beginning wordDB reduce operation, examining " + initialSize + " words for entries older than " + TimeDuration.asCompactString(maxAgeMs));

            try {
                final Iterator<PwmDB.TransactionItem> iter = pwmDB.iterator(WORDS_DB);
                while (wlStatus == WordlistStatus.OPEN && iter.hasNext()) {
                    final PwmDB.TransactionItem loopItem = iter.next();
                    final long timeStamp = Long.parseLong(loopItem.getValue());
                    final long entryAge = System.currentTimeMillis() - timeStamp;

                    if (entryAge > maxAgeMs) {
                        iter.remove();
                        removeCount++;

                        if (removeCount % 1000 == 0) {
                            LOGGER.trace("wordDB reduce operation in progress, removed=" + removeCount + ", total=" + (initialSize - removeCount));
                        }
                    } else {
                        localOldestEntry = timeStamp < localOldestEntry ? timeStamp : localOldestEntry;
                    }
                    sleeper.sleep();
                }
            } finally {
                try { pwmDB.returnIterator(WORDS_DB); } catch (Exception e) { LOGGER.warn("error returning pwmDB iterator: " + e.getMessage()); }
            }

            //update the oldest entry
            if (wlStatus == WordlistStatus.OPEN) {
                oldestEntry = localOldestEntry;
                pwmDB.put(META_DB, KEY_OLDEST_ENTRY, Long.toString(oldestEntry));
            }

            final StringBuilder sb = new StringBuilder();
            sb.append("completed wordDB reduce operation");
            sb.append(", removed=").append(removeCount);
            sb.append(", totalRemaining=").append(size());
            sb.append(", oldestEntry=").append(TimeDuration.asCompactString(oldestEntry));
            sb.append(" in ").append(TimeDuration.asCompactString(System.currentTimeMillis() - startTime));
            LOGGER.debug(sb.toString());
        }
    }
}
