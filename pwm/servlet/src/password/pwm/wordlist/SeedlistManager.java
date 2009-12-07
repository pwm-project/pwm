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

import password.pwm.util.PwmLogger;
import password.pwm.util.PwmRandom;
import password.pwm.util.db.PwmDB;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public class SeedlistManager extends AbstractWordlist implements Wordlist {

    private int initialPopulationCounter = 0;

    /**
     * Fetch the WordlistManager for a given database directory.  Any existing values in the database
     * will be truncated and replaced with the wordlist file.
     *
     * @param wordlistFile   ZIP file containing one or more text files with one word per line
     * @param pwmDB          Functioning instance
     * @param loadFactor     Percentage of time the populator should remain sleeping
     * @return WordlistManager for the instance.
     */
    public synchronized static SeedlistManager createSeedlistManager(
            final File wordlistFile,
            final PwmDB pwmDB,
            final int loadFactor
    )
    {
        return new SeedlistManager(
                wordlistFile,
                pwmDB,
                loadFactor,
                true
        );
    }

    protected SeedlistManager(
            final File wordlistFile,
            final PwmDB pwmDB,
            final int loadFactor,
            final boolean caseSensitive

    ) {
        super(wordlistFile, pwmDB, loadFactor, caseSensitive);

        this.LOGGER = PwmLogger.getLogger(this.getClass());
        this.DEBUG_LABEL = "pwm-seedist";                                                                        
        this.META_DB = PwmDB.DB.SEEDLIST_META;
        this.WORD_DB = PwmDB.DB.SEEDLIST_WORDS;

        final Thread t = new Thread(new Runnable() {
            public void run()
            {
                LOGGER.debug(DEBUG_LABEL + " starting up in background thread");
                init();
            }
        }, "pwm-SeedlistManager initializer/populator");

        t.start();
    }

    public String randomSeed()
    {
        if (!wlStatus.isAvailable()) {
            return null;
        }
        final long startTime = System.currentTimeMillis();
        String returnValue = null;
        try {
            final int seedCount = size();
            if (seedCount > 1000) {
                final int randomKey = PwmRandom.getInstance().nextInt(size());
                final Object obj = pwmDB.get(WORD_DB, String.valueOf(randomKey));
                if (obj != null) {
                    returnValue = obj.toString();
                }
            }
        } catch (Exception e) {
            LOGGER.warn("error while generating random word: " + e.getMessage());
        }

        final long totalTime = System.currentTimeMillis() - startTime;
        LOGGER.trace("getRandomSeed fetch time: " + totalTime + "ms");
        return returnValue;
    }

    protected Map<String,String> getWriteTxnForValue(final String value) {
        final Map<String, String> txItem = Collections.singletonMap(String.valueOf(initialPopulationCounter), value);
        initialPopulationCounter++;
        return txItem;
    }

    @Override
    protected void checkPopulation() throws Exception {
        final boolean isComplete = VALUE_STATUS.COMPLETE.equals(VALUE_STATUS.forString(pwmDB.get(META_DB, KEY_STATUS)));
        if (!isComplete) {
            LOGGER.info(DEBUG_LABEL + "prior population did not complete, clearing wordlist");
            pwmDB.truncate(META_DB);
            pwmDB.truncate(WORD_DB);
        }
        super.checkPopulation();
    }
}
