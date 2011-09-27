/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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

package password.pwm.util.pwmdb;

import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Jason D. Rivard
 */
public class PwmDBFactory {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmDBFactory.class);
    private static final String DEFAULT_IMPLEMENTATION = Berkeley_PwmDb.class.getName();

    private static final Map<File, PwmDB> singletonMap = Collections.synchronizedMap(new HashMap<File, PwmDB>());


// -------------------------- STATIC METHODS --------------------------

    public static synchronized PwmDB getInstance(
            final File dbDirectory,
            final String className,
            final Map<String, String> initParameters,
            final boolean readonly
    )
            throws Exception {
        PwmDB db = singletonMap.get(dbDirectory);

        if (db == null) {
            final long startTime = System.currentTimeMillis();
            final String theClass = className != null ? className : DEFAULT_IMPLEMENTATION;
            final PwmDBProvider dbProvider = createInstance(theClass);
            LOGGER.debug("initializing " + theClass + " pwmDBProvider instance");


            db = new PwmDBAdaptor(dbProvider);

            initInstance(dbProvider, dbDirectory, initParameters, theClass, readonly);
            final TimeDuration openTime = new TimeDuration(System.currentTimeMillis() - startTime);

            final StringBuilder debugText = new StringBuilder();
            debugText.append("pwmDB open in ").append(openTime.asCompactString());
            debugText.append(", db size: ").append(Helper.formatDiskSize(Helper.getFileDirectorySize(db.getFileLocation())));
            debugText.append(" at ").append(dbDirectory.toString());
            final long freeSpace = Helper.diskSpaceRemaining(db.getFileLocation());
            if (freeSpace >= 0) {
                debugText.append(", ").append(Helper.formatDiskSize(freeSpace)).append(" free");
            }
            LOGGER.info(debugText);
        }

        //readDBSizes(db);

        return db;
    }

    private static PwmDBProvider createInstance(final String className)
            throws Exception {
        final PwmDBProvider pwmDB;
        try {
            final Class c = Class.forName(className);
            final Object impl = c.newInstance();
            if (!(impl instanceof PwmDBProvider)) {
                throw new Exception("unable to createSharedHistoryManager new PwmDB, " + className + " is not instance of " + PwmDBProvider.class.getName());
            }
            pwmDB = (PwmDBProvider) impl;
        } catch (Exception e) {
            LOGGER.warn("error creating new PwmDB instance: " + e.getClass().getName() + ":" + e.getMessage());
            throw new Exception("Messages instantiating new PwmDB instance: " + e.getMessage(), e);
        }

        return pwmDB;
    }

    private static void initInstance(final PwmDBProvider pwmDBProvider, final File dbFileLocation, final Map<String, String> initParameters, final String theClass, final boolean readonly)
            throws Exception {
        try {
            if (dbFileLocation.mkdir()) {
                LOGGER.trace("created directory at " + dbFileLocation.getAbsolutePath());
            }
            pwmDBProvider.init(dbFileLocation, initParameters, readonly);
        } catch (Exception e) {
            LOGGER.warn("error while initializing pwmDB instance: " + e.getMessage());
            throw e;
        }

        LOGGER.trace("db init completed for " + theClass);
    }

    /*
    private static void readDBSizes(final PwmDB pwmDB) {
        final Thread sizeReader = new Thread() {
            public void run() {
                try {
                    Helper.pause(90 * 1000);
                    for (final PwmDB.DB loopDB : PwmDB.DB.values()) {
                        final int size = pwmDB.size(loopDB);
                        LOGGER.debug("size of " + loopDB + " read as " + size);
                    }
                } catch (Exception e) {
                //do nothing
                }
            }
        };
        sizeReader.setDaemon(true);
        sizeReader.setName("PwmDB size reader");
        sizeReader.start();
    }
    */
}
