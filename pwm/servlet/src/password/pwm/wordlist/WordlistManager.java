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
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmException;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.localdb.LocalDB;

import java.io.File;
import java.util.Collections;
import java.util.Map;


/**
 * @author Jason D. Rivard
 */
public class WordlistManager extends AbstractWordlist implements Wordlist {
// ------------------------------ FIELDS ------------------------------

    boolean backwards;

// -------------------------- STATIC METHODS --------------------------

    public WordlistManager() {
    }


    public void init(final LocalDB pwmDB, final WordlistConfiguration wordlistConfiguration) {
        this.LOGGER = PwmLogger.getLogger(WordlistManager.class);
        this.DEBUG_LABEL = PwmConstants.PWM_APP_NAME + "-Wordlist";
        this.META_DB = LocalDB.DB.WORDLIST_META;
        this.WORD_DB = LocalDB.DB.WORDLIST_WORDS;

        final Thread t = new Thread(new Runnable() {
            public void run()
            {
                LOGGER.debug(DEBUG_LABEL + " starting up in background thread");
                try {
                    startup(pwmDB, wordlistConfiguration);
                } catch (Exception e) {
                    try {
                        LOGGER.warn("error during startup: " + e.getMessage());
                    } catch (Exception moreE) { /* probably due to shut down */ }
                }
            }
        }, PwmConstants.PWM_APP_NAME + "-WordlistManager initializer/populator");

        t.start();
    }

    protected Map<String,String> getWriteTxnForValue(final String value) {
        return Collections.singletonMap(value,"");
    }

    public void init(PwmApplication pwmApplication) throws PwmException {
        final String setting = pwmApplication.getConfig().readSettingAsString(PwmSetting.WORDLIST_FILENAME);
        final File wordlistFile = setting == null || setting.length() < 1 ? null : Helper.figureFilepath(setting, pwmApplication.getPwmApplicationPath());
        final boolean caseSensitive = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.WORDLIST_CASE_SENSITIVE);
        final int loadFactor = PwmConstants.DEFAULT_WORDLIST_LOADFACTOR;
        final int checkSize = (int)pwmApplication.getConfig().readSettingAsLong(PwmSetting.PASSWORD_WORDLIST_WORDSIZE);
        final WordlistConfiguration wordlistConfiguration = new WordlistConfiguration(wordlistFile, loadFactor, caseSensitive, checkSize);

        init(pwmApplication.getLocalDB(),wordlistConfiguration);
    }
}
