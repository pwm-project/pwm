/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.svc.wordlist;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.PwmException;
import password.pwm.util.Helper;
import password.pwm.util.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;

import java.util.Collections;
import java.util.Map;

public class SeedlistManager extends AbstractWordlist implements Wordlist {

    private static final PwmLogger LOGGER = PwmLogger.forClass(SeedlistManager.class);

    private int initialPopulationCounter = 0;

    public SeedlistManager() {
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
                final Object obj = localDB.get(getWordlistDB(), String.valueOf(randomKey));
                if (obj != null) {
                    returnValue = obj.toString();
                }
            }
        } catch (Exception e) {
            LOGGER.warn("error while generating random word: " + e.getMessage());
        }

        if (debugTrace) {
            LOGGER.trace("getRandomSeed fetch time: " + TimeDuration.fromCurrent(startTime).asCompactString());
        }
        return returnValue;
    }

    protected Map<String, String> getWriteTxnForValue(final String value) {
        final Map<String, String> txItem = Collections.singletonMap(String.valueOf(initialPopulationCounter), value);
        initialPopulationCounter++;
        return txItem;
    }

    public void init(final PwmApplication pwmApplication) throws PwmException {
        super.init(pwmApplication);
        final WordlistConfiguration wordlistConfiguration = new WordlistConfiguration(true, 0);

        this.DEBUG_LABEL = PwmConstants.PWM_APP_NAME + "-Seedist";

        final Thread t = new Thread(new Runnable() {
            public void run() {
                LOGGER.debug(DEBUG_LABEL + " starting up in background thread");
                startup(pwmApplication.getLocalDB(), wordlistConfiguration);
            }
        }, Helper.makeThreadName(pwmApplication,SeedlistManager.class));

        t.start();
    }

    @Override
    protected PwmApplication.AppAttribute getMetaDataAppAttribute() {
        return PwmApplication.AppAttribute.SEEDLIST_METADATA;
    }

    @Override
    protected LocalDB.DB getWordlistDB() {
        return LocalDB.DB.SEEDLIST_WORDS;
    }

    @Override
    protected AppProperty getBuiltInWordlistLocationProperty() {
        return AppProperty.SEEDLIST_BUILTIN_PATH;
    }
}
