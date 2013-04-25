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

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmService;
import password.pwm.PwmSession;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.util.*;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class SharedHistoryManager implements Wordlist {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(SharedHistoryManager.class);

    private static final String KEY_SALT = "salt";
    private static final String KEY_OLDEST_ENTRY = "oldest_entry";
    private static final String KEY_VERSION = "version";

    private static final String VALUE_VERSION = "2" + "_" + PwmConstants.SHARED_HISTORY_HASH_LOOP_COUNT;

    private static final int MIN_CLEANER_FREQUENCY = 1000 * 60 * 60; // 1 hour
    private static final int MAX_CLENAER_FREQUENCY = 1000 * 60 * 60 * 24; // 1 day

    private static final LocalDB.DB META_DB = LocalDB.DB.SHAREDHISTORY_META;
    private static final LocalDB.DB WORDS_DB = LocalDB.DB.SHAREDHISTORY_WORDS;

    private volatile PwmService.STATUS status = STATUS.NEW;

    private volatile Timer cleanerTimer = null;

    private LocalDB localDB;
    private String salt;
    private long oldestEntry;
    private long maxAgeMs;

    private boolean caseSensitive;


// --------------------------- CONSTRUCTORS ---------------------------

    public SharedHistoryManager() throws LocalDBException {
    }

// -------------------------- OTHER METHODS --------------------------

    public void close() {
        status = STATUS.CLOSED;
        LOGGER.debug("closed");
        if (cleanerTimer != null) {
            cleanerTimer.cancel();
        }
        localDB = null;
    }

    public boolean containsWord(final String word) {
        if (status != STATUS.OPEN) {
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
            final boolean inDB = localDB.contains(WORDS_DB, hashedWord);
            if (inDB) {
                final long timeStamp = Long.parseLong(localDB.get(WORDS_DB, hashedWord));
                final long entryAge = System.currentTimeMillis() - timeStamp;
                if (entryAge < maxAgeMs) {
                    result = true;
                }
            }

        } catch (Exception e) {
            LOGGER.warn("error checking global history list: " + e.getMessage());
        }

        //LOGGER.trace(pwmSession, "successfully checked word, result=" + result + ", duration=" + new TimeDuration(System.currentTimeMillis(), startTime).asCompactString());
        return result;
    }

    public PwmService.STATUS status() {
        return status;
    }

    public long getOldestEntryAge() {
        if (size() > 0) {
            return System.currentTimeMillis() - oldestEntry;
        } else {
            return 0;
        }
    }

    public int size() {
        if (localDB != null) {
            try {
                return localDB.size(WORDS_DB);
            } catch (Exception e) {
                LOGGER.error("error checking wordlist size: " + e.getMessage());
                return 0;
            }
        } else {
            return 0;
        }
    }


    private void checkSalt()
            throws Exception {
        salt = localDB.get(META_DB, KEY_SALT);
        if (salt == null || salt.length() < 1) {
            LOGGER.info("no salt found in DB, creating new salt and clearing global history");
            localDB.truncate(WORDS_DB);
            salt = PwmRandom.getInstance().alphaNumericString(64);
            localDB.put(META_DB, KEY_SALT, salt);
            localDB.remove(META_DB, KEY_OLDEST_ENTRY);
        }
    }

    private boolean checkDbVersion()
            throws Exception {
        LOGGER.trace("checking version number stored in pwmDB");

        final Object versionInDB = localDB.get(META_DB, KEY_VERSION);
        final String pwmVersion = "version=" + VALUE_VERSION + ", caseSensitive=" + String.valueOf(caseSensitive);
        final boolean result = pwmVersion.equals(versionInDB);

        if (!result) {
            LOGGER.info("existing db version does not match current db version db=(" + versionInDB + ")  pwm=(" + pwmVersion + "), clearing db");
            localDB.truncate(WORDS_DB);
            localDB.put(META_DB, KEY_VERSION, pwmVersion);
            localDB.remove(META_DB, KEY_OLDEST_ENTRY);
        } else {
            LOGGER.trace("existing db version matches current db version db=(" + versionInDB + ")  pwm=(" + pwmVersion + ")");
        }

        return result;
    }

    private void init(final long maxAgeMs) {
        this.maxAgeMs = maxAgeMs;
        status = STATUS.OPENING;
        final long startTime = System.currentTimeMillis();

        try {
            checkDbVersion();
        } catch (Exception e) {
            LOGGER.error("error checking db version", e);
            status = STATUS.CLOSED;
            return;
        }

        try {
            checkSalt();
        } catch (Exception e) {
            LOGGER.error("unexpected error examining salt in DB, will remain closed: " + e.getMessage(), e);
            status = STATUS.CLOSED;
            return;
        }

        try {
            final String oldestEntryStr = localDB.get(META_DB, KEY_OLDEST_ENTRY);
            if (oldestEntryStr == null || oldestEntryStr.length() < 1) {
                oldestEntry = 0;
                LOGGER.trace("no oldestEntry timestamp stored, will rescan");
            } else {
                oldestEntry = Long.parseLong(oldestEntryStr);
                LOGGER.trace("oldest timestamp loaded from pwmDB, age is " + TimeDuration.fromCurrent(oldestEntry).asCompactString());
            }
        } catch (LocalDBException e) {
            LOGGER.error("unexpected error loading oldest-entry meta record, will remain closed: " + e.getMessage(), e);
            status = STATUS.CLOSED;
            return;
        }

        try {
            final int size = localDB.size(WORDS_DB);
            final StringBuilder sb = new StringBuilder();
            sb.append("open with ").append(size).append(" words (");
            sb.append(new TimeDuration(System.currentTimeMillis(), startTime).asCompactString()).append(")");
            sb.append(", maxAgeMs=").append(new TimeDuration(maxAgeMs).asCompactString());
            sb.append(", oldestEntry=").append(new TimeDuration(System.currentTimeMillis(), oldestEntry).asCompactString());
            LOGGER.info(sb.toString());
        } catch (LocalDBException e) {
            LOGGER.error("unexpected error examing size of DB, will remain closed: " + e.getMessage(), e);
            status = STATUS.CLOSED;
            return;
        }

        status = STATUS.OPEN;
        //populateFromWordlist();  //only used for debugging!!!

        {
            long frequencyMs = maxAgeMs > MAX_CLENAER_FREQUENCY ? MAX_CLENAER_FREQUENCY : maxAgeMs;
            frequencyMs = frequencyMs < MIN_CLEANER_FREQUENCY ? MIN_CLEANER_FREQUENCY : frequencyMs;

            LOGGER.debug("scheduling cleaner task to run once every " + new TimeDuration(frequencyMs).asCompactString());
            cleanerTimer = new Timer(PwmConstants.PWM_APP_NAME + "-SharedHistoryManager timer", true);
            cleanerTimer.schedule(new CleanerTask(), 1000, frequencyMs);
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
            while (iter.hasNext() && status == WordlistStatus.OPEN) {
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
        if (status != STATUS.OPEN) {
            return;
        }

        final String addWord = normalizeWord(word);

        if (addWord == null) {
            return;
        }

        final long startTime = System.currentTimeMillis();

        try {
            final String hashedWord = hashWord(addWord);

            final boolean preExisting = localDB.contains(WORDS_DB, hashedWord);
            localDB.put(WORDS_DB, hashedWord, Long.toString(System.currentTimeMillis()));

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
        final int hashLoopCount = PwmConstants.SHARED_HISTORY_HASH_LOOP_COUNT;
        byte[] hashedAnswer = md.digest((wordWithSalt).getBytes());

        for (int i = 0; i < hashLoopCount; i++) {
            hashedAnswer = md.digest(hashedAnswer);
        }

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
            } catch (LocalDBException e) {
                LOGGER.error("error during execution of reduce: " + e.getMessage(), e);
            }
        }


        private void reduceWordDB()
                throws LocalDBException {
            final long oldestEntryAge = System.currentTimeMillis() - oldestEntry;
            if (oldestEntryAge < maxAgeMs) {
                LOGGER.debug("skipping wordDB reduce operation, eldestEntry="
                        + TimeDuration.asCompactString(oldestEntryAge)
                        + ", maxAge="
                        + TimeDuration.asCompactString(maxAgeMs));
                return;
            }

            final long startTime = System.currentTimeMillis();
            final int initialSize = size();
            int removeCount = 0;
            long localOldestEntry = System.currentTimeMillis();

            LOGGER.debug("beginning wordDB reduce operation, examining " + initialSize + " words for entries older than " + TimeDuration.asCompactString(maxAgeMs));

            LocalDB.PwmDBIterator<String> keyIterator = null;
            try {
                keyIterator = localDB.iterator(WORDS_DB);
                while (status == STATUS.OPEN && keyIterator.hasNext()) {
                    final String key = keyIterator.next();
                    final String value = localDB.get(WORDS_DB, key);
                    final long timeStamp = Long.parseLong(value);
                    final long entryAge = System.currentTimeMillis() - timeStamp;

                    if (entryAge > maxAgeMs) {
                        localDB.remove(WORDS_DB, key);
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
                try {
                    if (keyIterator != null) {
                        keyIterator.close();
                    }
                } catch (Exception e) {
                    LOGGER.warn("error returning LocalDB iterator: " + e.getMessage());
                }
            }

            //update the oldest entry
            if (status == STATUS.OPEN) {
                oldestEntry = localOldestEntry;
                localDB.put(META_DB, KEY_OLDEST_ENTRY, Long.toString(oldestEntry));
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

    public List<HealthRecord> healthCheck() {
        return null;
    }

    public void init(final PwmApplication pwmApplication)
            throws PwmException
    {
        this.maxAgeMs = 1000 *  pwmApplication.getConfig().readSettingAsLong(PwmSetting.PASSWORD_SHAREDHISTORY_MAX_AGE); // convert to MS;
        this.caseSensitive = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.WORDLIST_CASE_SENSITIVE);
        this.localDB = pwmApplication.getLocalDB();

        if (localDB == null) {
            LOGGER.info("LocalDB is not available, will remain closed");
            status = STATUS.CLOSED;
            return;
        }

        if (maxAgeMs < 1) {
            LOGGER.debug("max age=" + maxAgeMs + ", will remain closed");

            new Thread(new Runnable() {
                public void run() {
                    LOGGER.trace("clearing wordlist");
                    try {
                        localDB.truncate(WORDS_DB);
                    } catch (Exception e) {
                        LOGGER.error("error during wordlist truncate", e);
                    }
                }
            }, PwmConstants.PWM_APP_NAME + "-SharedHistoryManager wordlist truncate").start();
            return;
        }

        new Thread(new Runnable() {
            public void run() {
                LOGGER.debug("starting up in background thread");
                init(maxAgeMs);
            }
        }, PwmConstants.PWM_APP_NAME + "-SharedHistoryManager initializer").start();
    }
}
