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
import password.pwm.util.PwmRandom;
import password.pwm.util.localdb.LocalDB;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public class SeedlistManager extends AbstractWordlist implements Wordlist {

    private int initialPopulationCounter = 0;

    public SeedlistManager() {
    }

    public void init(
            final WordlistConfiguration wordlistConfiguration,
            final LocalDB pwmDB

    ) {
        this.LOGGER = PwmLogger.getLogger(this.getClass());
        this.DEBUG_LABEL = PwmConstants.PWM_APP_NAME + "-Seedist";
        this.META_DB = LocalDB.DB.SEEDLIST_META;
        this.WORD_DB = LocalDB.DB.SEEDLIST_WORDS;

        final Thread t = new Thread(new Runnable() {
            public void run() {
                LOGGER.debug(DEBUG_LABEL + " starting up in background thread");
                startup(pwmDB, wordlistConfiguration);
            }
        }, PwmConstants.PWM_APP_NAME + "-SeedlistManager initializer/populator");

        t.start();
    }

    public String randomSeed() {
        if (wlStatus != STATUS.OPEN) {
            return null;
        }
        final long startTime = System.currentTimeMillis();
        String returnValue = null;
        try {
            final int seedCount = size();
            if (seedCount > 1000) {
                final int randomKey = PwmRandom.getInstance().nextInt(size());
                final Object obj = localDB.get(WORD_DB, String.valueOf(randomKey));
                if (obj != null) {
                    returnValue = obj.toString();
                }
            }
        } catch (Exception e) {
            LOGGER.warn("error while generating random word: " + e.getMessage());
        }

        final long totalTime = System.currentTimeMillis() - startTime;
        //LOGGER.trace("getRandomSeed fetch time: " + totalTime + "ms");
        return returnValue;
    }

    protected Map<String, String> getWriteTxnForValue(final String value) {
        final Map<String, String> txItem = Collections.singletonMap(String.valueOf(initialPopulationCounter), value);
        initialPopulationCounter++;
        return txItem;
    }

    @Override
    protected void checkPopulation() throws Exception {
        final boolean isComplete = VALUE_STATUS.COMPLETE.equals(VALUE_STATUS.forString(localDB.get(META_DB, KEY_STATUS)));
        if (!isComplete) {
            LOGGER.info(DEBUG_LABEL + " prior population did not complete, clearing wordlist");
            localDB.truncate(META_DB);
            localDB.truncate(WORD_DB);
        }
        super.checkPopulation();
    }

    public void init(PwmApplication pwmApplication) throws PwmException {
        final String setting = pwmApplication.getConfig().readSettingAsString(PwmSetting.SEEDLIST_FILENAME);
        final File seedlistFile = setting == null || setting.length() < 1 ? null : Helper.figureFilepath(setting, pwmApplication.getPwmApplicationPath());
        final int loadFactor = PwmConstants.DEFAULT_WORDLIST_LOADFACTOR;
        final WordlistConfiguration wordlistConfiguration = new WordlistConfiguration(seedlistFile, loadFactor, true, 0);

        init(wordlistConfiguration, pwmApplication.getLocalDB());
    }
}
