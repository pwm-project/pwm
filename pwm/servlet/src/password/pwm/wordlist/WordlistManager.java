/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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
import password.pwm.util.JsonUtil;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


/**
 * @author Jason D. Rivard
 */
public class WordlistManager extends AbstractWordlist implements Wordlist {

    private static final PwmLogger LOGGER = PwmLogger.forClass(WordlistManager.class);

// ------------------------------ FIELDS ------------------------------

    boolean backwards;

// -------------------------- STATIC METHODS --------------------------

    public WordlistManager() {
    }


    protected Map<String,String> getWriteTxnForValue(final String value) {
        final Map<String,String> returnSet = new TreeMap<>();
        final Set<String> chunkedWords = chunkWord(value,this.wordlistConfiguration.getCheckSize());
        for (final String word : chunkedWords) {
            returnSet.put(word,"");
        }
        return returnSet;
    }

    public void init(final PwmApplication pwmApplication) throws PwmException {
        super.init(pwmApplication);
        final String setting = pwmApplication.getConfig().readSettingAsString(PwmSetting.WORDLIST_FILENAME);
        final File wordlistFile = setting == null || setting.length() < 1 ? null : Helper.figureFilepath(setting, pwmApplication.getWebInfPath());
        final boolean caseSensitive = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.WORDLIST_CASE_SENSITIVE);
        final int loadFactor = PwmConstants.DEFAULT_WORDLIST_LOADFACTOR;
        final int checkSize = (int)pwmApplication.getConfig().readSettingAsLong(PwmSetting.PASSWORD_WORDLIST_WORDSIZE);
        final WordlistConfiguration wordlistConfiguration = new WordlistConfiguration(wordlistFile, loadFactor, caseSensitive, checkSize);

        this.DEBUG_LABEL = PwmConstants.PWM_APP_NAME + "-Wordlist";
        this.META_DB = LocalDB.DB.WORDLIST_META;
        this.WORD_DB = LocalDB.DB.WORDLIST_WORDS;

        final Thread t = new Thread(new Runnable() {
            public void run()
            {
                LOGGER.debug(DEBUG_LABEL + " starting up in background thread");
                try {
                    startup(pwmApplication.getLocalDB(), wordlistConfiguration);
                } catch (Exception e) {
                    try {
                        LOGGER.warn("error during startup: " + e.getMessage());
                    } catch (Exception moreE) { /* probably due to shut down */ }
                }
            }
        }, Helper.makeThreadName(pwmApplication, WordlistManager.class));

        t.start();
    }

    @Override
    protected String makeVersionString()
    {
        return VALUE_VERSION + JsonUtil.serialize(wordlistConfiguration);
    }
}
