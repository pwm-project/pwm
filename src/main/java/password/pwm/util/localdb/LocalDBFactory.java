/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.util.FileSystemUtility;
import password.pwm.util.Helper;
import password.pwm.util.StringUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Jason D. Rivard
 */
public class LocalDBFactory {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(LocalDBFactory.class);

// -------------------------- STATIC METHODS --------------------------

    public static synchronized LocalDB getInstance(
            final File dbDirectory,
            final boolean readonly,
            final PwmApplication pwmApplication,
            Configuration config
    )
            throws Exception
    {
        if (config == null && pwmApplication != null) {
            config = pwmApplication.getConfig();
        }

        final long startTime = System.currentTimeMillis();

        final String className;
        final Map<String, String> initParameters;
        if (config == null) {
            className = AppProperty.LOCALDB_IMPLEMENTATION.getDefaultValue();
            final String initStrings = AppProperty.LOCALDB_INIT_STRING.getDefaultValue();
            initParameters = StringUtil.convertStringListToNameValuePair(Arrays.asList(initStrings.split(";;;")), "=");
        } else {
            className = config.readAppProperty(AppProperty.LOCALDB_IMPLEMENTATION);
            final String initStrings = config.readAppProperty(AppProperty.LOCALDB_INIT_STRING);
            initParameters = StringUtil.convertStringListToNameValuePair(Arrays.asList(initStrings.split(";;;")), "=");
        }

        final Map<LocalDBProvider.Parameter,String> parameters = pwmApplication == null
                ? Collections.<LocalDBProvider.Parameter, String>emptyMap()
                : makeParameterMap(pwmApplication.getConfig(), readonly);
        final LocalDBProvider dbProvider = createInstance(className);
        LOGGER.debug("initializing " + className + " localDBProvider instance");

        LocalDB localDB = new LocalDBAdaptor(dbProvider, pwmApplication);

        initInstance(dbProvider, dbDirectory, initParameters, className, parameters);
        final TimeDuration openTime = new TimeDuration(System.currentTimeMillis() - startTime);

        localDB = wrapWithCompressor(localDB,config);

        if (!readonly) {
            LOGGER.trace("clearing TEMP db");
            localDB.truncate(LocalDB.DB.TEMP);

            final LocalDBUtility localDBUtility = new LocalDBUtility(localDB);
            if (localDBUtility.readImportInprogressFlag()) {
                LOGGER.error("previous database import process did not complete successfully, clearing all data");
                localDBUtility.prepareForImport();
                localDBUtility.markImportComplete();
            }
        }

        final StringBuilder debugText = new StringBuilder();
        debugText.append("LocalDB open in ").append(openTime.asCompactString());
        debugText.append(", db size: ").append(Helper.formatDiskSize(FileSystemUtility.getFileDirectorySize(localDB.getFileLocation())));
        debugText.append(" at ").append(dbDirectory.toString());
        final long freeSpace = FileSystemUtility.diskSpaceRemaining(localDB.getFileLocation());
        if (freeSpace >= 0) {
            debugText.append(", ").append(Helper.formatDiskSize(freeSpace)).append(" free");
        }
        LOGGER.info(debugText);

        return localDB;
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
        } catch (Throwable e) {
            final String errorMsg = "error creating new LocalDB instance: " + e.getClass().getName() + ":" + e.getMessage();
            LOGGER.error(errorMsg,e);
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE, errorMsg));
        }

        return localDB;
    }

    private static void initInstance(
            final LocalDBProvider pwmDBProvider,
            final File dbFileLocation,
            final Map<String, String> initParameters,
            final String theClass,
            final Map<LocalDBProvider.Parameter,String> parameters
    )
            throws Exception {
        try {
            if (dbFileLocation.mkdir()) {
                LOGGER.trace("created directory at " + dbFileLocation.getAbsolutePath());
            }


            pwmDBProvider.init(dbFileLocation, initParameters, parameters);
        } catch (Exception e) {
            final String errorMsg = "error creating new LocalDB instance: " + e.getClass().getName() + ":" + e.getMessage();
            LOGGER.error(errorMsg,e);
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE, errorMsg));
        }

        LOGGER.trace("db init completed for " + theClass);
    }

    private static LocalDB wrapWithCompressor(final LocalDB localDB, final Configuration config) {
        if (config == null) {
            return LocalDBCompressor.createLocalDBCompressor(localDB, 1024, false);
        }

        final boolean enableCompression = Boolean.parseBoolean(config.readAppProperty(AppProperty.LOCALDB_COMPRESSION_ENABLED));
        final boolean enableDecompression = Boolean.parseBoolean(config.readAppProperty(AppProperty.LOCALDB_DECOMPRESSION_ENABLED));
        final int compressionMinSize = Integer.parseInt(config.readAppProperty(AppProperty.LOCALDB_COMPRESSION_MINSIZE));

        if (enableCompression || enableDecompression) {
            return LocalDBCompressor.createLocalDBCompressor(localDB, compressionMinSize, enableCompression);
        }

        return localDB;
    }

    private static Map<LocalDBProvider.Parameter, String> makeParameterMap(final Configuration configuration, final boolean readOnly) {
        final Map<LocalDBProvider.Parameter,String> parameters = new HashMap<>();
        if (readOnly) {
            parameters.put(LocalDBProvider.Parameter.readOnly, Boolean.TRUE.toString());
        }
        if (Boolean.parseBoolean(configuration.readAppProperty(AppProperty.LOCALDB_AGGRESSIVE_COMPACT_ENABLED))) {
            parameters.put(LocalDBProvider.Parameter.aggressiveCompact, Boolean.TRUE.toString());
        }
        return Collections.unmodifiableMap(parameters);
    }
}
