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

package password.pwm.util.localdb;

import password.pwm.PwmApplication;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;

import java.io.File;
import java.util.Map;

/**
 * @author Jason D. Rivard
 */
public class LocalDBFactory {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(LocalDBFactory.class);

// -------------------------- STATIC METHODS --------------------------

    public static synchronized LocalDB getInstance(
            final File dbDirectory,
            final String className,
            final Map<String, String> initParameters,
            final boolean readonly,
            final PwmApplication pwmApplication
    )
            throws Exception {

        final long startTime = System.currentTimeMillis();
        final LocalDBProvider dbProvider = createInstance(className);
        LOGGER.debug("initializing " + className + " localDBProvider instance");

        final LocalDB db = new LocalDBAdaptor(dbProvider, pwmApplication);

        initInstance(dbProvider, dbDirectory, initParameters, className, readonly);
        final TimeDuration openTime = new TimeDuration(System.currentTimeMillis() - startTime);

        LOGGER.trace("clearing TEMP db");
        if (!readonly) {
            db.truncate(LocalDB.DB.TEMP);
        }

        final StringBuilder debugText = new StringBuilder();
        debugText.append("LocalDB open in ").append(openTime.asCompactString());
        debugText.append(", db size: ").append(Helper.formatDiskSize(Helper.getFileDirectorySize(db.getFileLocation())));
        debugText.append(" at ").append(dbDirectory.toString());
        final long freeSpace = Helper.diskSpaceRemaining(db.getFileLocation());
        if (freeSpace >= 0) {
            debugText.append(", ").append(Helper.formatDiskSize(freeSpace)).append(" free");
        }
        LOGGER.info(debugText);

        return db;
    }

    private static LocalDBProvider createInstance(final String className)
            throws Exception {
        final LocalDBProvider localDB;
        try {
            final Class c = Class.forName(className);
            final Object impl = c.newInstance();
            if (!(impl instanceof LocalDBProvider)) {
                throw new Exception("unable to createSharedHistoryManager new LocalDB, " + className + " is not instance of " + LocalDBProvider.class.getName());
            }
            localDB = (LocalDBProvider) impl;
        } catch (Exception e) {
            LOGGER.warn("error creating new LocalDB instance: " + e.getClass().getName() + ":" + e.getMessage());
            throw new Exception("Messages instantiating new LocalDB instance: " + e.getMessage(), e);
        }

        return localDB;
    }

    private static void initInstance(final LocalDBProvider pwmDBProvider, final File dbFileLocation, final Map<String, String> initParameters, final String theClass, final boolean readonly)
            throws Exception {
        try {
            if (dbFileLocation.mkdir()) {
                LOGGER.trace("created directory at " + dbFileLocation.getAbsolutePath());
            }
            pwmDBProvider.init(dbFileLocation, initParameters, readonly);
        } catch (Exception e) {
            LOGGER.warn("error while initializing LocalDB instance: " + e.getMessage());
            throw e;
        }

        LOGGER.trace("db init completed for " + theClass);
    }
}
