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
import password.pwm.util.db.PwmDB;

import java.io.File;
import java.util.Collections;
import java.util.Map;


/**
 * @author Jason D. Rivard
 */
public class WordlistManager extends AbstractWordlist implements Wordlist {
// ------------------------------ FIELDS ------------------------------

// -------------------------- STATIC METHODS --------------------------

    /**
     * Fetch the WordlistManager for a given database directory.  Any existing values in the database
     * will be truncated and replaced with the wordlist file.
     *
     * @param wordlistFile   ZIP file containing one or more text files with one word per line
     * @param pwmDB          Functioning instance
     * @param loadFactor     Percentage of time the populator should remain sleeping
     * @param caseSensitive  If true, wordlist will be populated and tested using case sensitivity
     * @return WordlistManager for the instance.
     */
    public synchronized static WordlistManager createWordlistManager(
            final File wordlistFile,
            final PwmDB pwmDB,
            final int loadFactor,
            final boolean caseSensitive
    )
    {
        return new WordlistManager(
                wordlistFile,
                pwmDB,
                loadFactor,
                caseSensitive
        );
    }

    protected WordlistManager(
            final File wordlistFile,
            final PwmDB pwmDB,
            final int loadFactor,
            final boolean caseSensitive

    ) {
        super(wordlistFile, pwmDB, loadFactor, caseSensitive);

        this.LOGGER = PwmLogger.getLogger(this.getClass());
        this.DEBUG_LABEL = "pwm-wordlist";
        this.META_DB = PwmDB.DB.WORDLIST_META;
        this.WORD_DB = PwmDB.DB.WORDLIST_WORDS;

        final Thread t = new Thread(new Runnable() {
            public void run()
            {
                LOGGER.debug(DEBUG_LABEL + " starting up in background thread");
                try {
                    init();
                } catch (Exception e) {
                    try {
                    LOGGER.warn("error during startup: " + e.getMessage());
                    } catch (Exception moreE) { /* probably due to shut down */ }
                }
            }
        }, "pwm-WordlistManager initializer/populator");

        t.start();
    }

    protected Map<String,String> getWriteTxnForValue(final String value) {
        return Collections.singletonMap(value,"");
    }
}
