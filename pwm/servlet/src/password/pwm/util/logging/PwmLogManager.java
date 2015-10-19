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

package password.pwm.util.logging;

import com.novell.ldapchai.ChaiUser;
import org.apache.log4j.*;
import org.apache.log4j.xml.DOMConfigurator;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.util.FileSystemUtility;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PwmLogManager {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmLogManager.class);

    public static final List<Package> LOGGING_PACKAGES = Collections.unmodifiableList(Arrays.asList(
            PwmApplication.class.getPackage(),
            ChaiUser.class.getPackage(),
            Package.getPackage("org.jasig.cas.client")
    ));

    public static void deinitializeLogger() {
        // clear all existing package loggers
        for (final Package logPackage : LOGGING_PACKAGES) {
            if (logPackage != null) {
                final Logger logger = Logger.getLogger(logPackage.getName());
                logger.setAdditivity(false);
                logger.removeAllAppenders();
                logger.setLevel(Level.TRACE);
            }
        }

        PwmLogger.setLocalDBLogger(null,null);
        PwmLogger.setPwmApplication(null);
        PwmLogger.setFileAppender(null);
    }

    public static void initializeLogger(
            final PwmApplication pwmApplication,
            final Configuration config,
            final File log4jConfigFile,
            final String consoleLogLevel,
            final File pwmApplicationPath,
            final String fileLogLevel
    ) {
        PwmLogger.setPwmApplication(pwmApplication);

        // try to configure using the log4j config file (if it exists)
        if (log4jConfigFile != null) {
            try {
                if (!log4jConfigFile.exists()) {
                    throw new Exception("file not found: " + log4jConfigFile.getAbsolutePath());
                }
                DOMConfigurator.configure(log4jConfigFile.getAbsolutePath());
                LOGGER.debug("successfully initialized log4j using file " + log4jConfigFile.getAbsolutePath());
                return;
            } catch (Exception e) {
                LOGGER.error("error loading log4jconfig file '" + log4jConfigFile + "' error: " + e.getMessage());
            }
        }

        deinitializeLogger();

        initConsoleLogger(config, consoleLogLevel);

        initFileLogger(config, fileLogLevel, pwmApplicationPath);

        // disable jersey warnings.
        java.util.logging.LogManager.getLogManager().addLogger(java.util.logging.Logger.getLogger("com.sun.jersey.spi.container.servlet.WebComponent"));
        java.util.logging.LogManager.getLogManager().getLogger("com.sun.jersey.spi.container.servlet.WebComponent").setLevel(java.util.logging.Level.OFF);
    }

    private static void initConsoleLogger(
            final Configuration config,
            final String consoleLogLevel
    )
    {
        final Layout patternLayout = new PatternLayout(config.readAppProperty(AppProperty.LOGGING_PATTERN));
        // configure console logging
        if (consoleLogLevel != null && consoleLogLevel.length() > 0 && !"Off".equals(consoleLogLevel)) {
            final ConsoleAppender consoleAppender = new ConsoleAppender(patternLayout);
            final Level level = Level.toLevel(consoleLogLevel);
            consoleAppender.setThreshold(level);
            for (final Package logPackage : LOGGING_PACKAGES) {
                if (logPackage != null) {
                    final Logger logger = Logger.getLogger(logPackage.getName());
                    logger.setLevel(Level.TRACE);
                    logger.addAppender(consoleAppender);
                }
            }
            LOGGER.debug("successfully initialized default console log4j config at log level " + level.toString());
        } else {
            LOGGER.debug("skipping stdout log4j initialization due to blank setting for log level");
        }
    }

    private static void initFileLogger(
            final Configuration config,
            final String fileLogLevel,
            final File pwmApplicationPath
    )
    {
        final Layout patternLayout = new PatternLayout(config.readAppProperty(AppProperty.LOGGING_PATTERN));

        // configure file logging
        final String logDirectorySetting = config.readAppProperty(AppProperty.LOGGING_FILE_PATH);
        final File logDirectory = FileSystemUtility.figureFilepath(logDirectorySetting, pwmApplicationPath);

        if (logDirectory != null && fileLogLevel != null && fileLogLevel.length() > 0 && !"Off".equals(fileLogLevel)) {
            try {
                if (!logDirectory.exists()) {
                    if (logDirectory.mkdir()) {
                        LOGGER.info("created directory " + logDirectory.getAbsoluteFile());
                    } else {
                        throw new IOException("failed to create directory " + logDirectory.getAbsoluteFile());
                    }
                }

                final String fileName = logDirectory.getAbsolutePath() + File.separator + PwmConstants.PWM_APP_NAME + ".log";
                final RollingFileAppender fileAppender = new RollingFileAppender(patternLayout,fileName,true);
                final Level level = Level.toLevel(fileLogLevel);
                fileAppender.setThreshold(level);
                fileAppender.setMaxBackupIndex(Integer.parseInt(config.readAppProperty(AppProperty.LOGGING_FILE_MAX_ROLLOVER)));
                fileAppender.setMaxFileSize(config.readAppProperty(AppProperty.LOGGING_FILE_MAX_SIZE));

                PwmLogger.setFileAppender(fileAppender);

                for (final Package logPackage : LOGGING_PACKAGES) {
                    if (logPackage != null) {
                        //if (!logPackage.equals(PwmApplication.class.getPackage())) {
                        final Logger logger = Logger.getLogger(logPackage.getName());
                        logger.setLevel(Level.TRACE);
                        logger.addAppender(fileAppender);
                        //}
                    }
                }
                LOGGER.debug("successfully initialized default file log4j config at log level " + level.toString());
            } catch (IOException e) {
                LOGGER.debug("error initializing RollingFileAppender: " + e.getMessage());
            }
        }
    }

    public static LocalDBLogger initializeLocalDBLogger(final PwmApplication pwmApplication) {
        final LocalDB localDB = pwmApplication.getLocalDB();

        if (pwmApplication.getApplicationMode() == PwmApplication.MODE.READ_ONLY) {
            LOGGER.trace("skipping initialization of LocalDBLogger due to read-only mode");
            return null;
        }

        // initialize the localDBLogger
        final LocalDBLogger localDBLogger;
        final PwmLogLevel localDBLogLevel = pwmApplication.getConfig().getEventLogLocalDBLevel();
        try {
            final int maxEvents = (int) pwmApplication.getConfig().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_EVENTS);
            final long maxAgeMS = 1000 * pwmApplication.getConfig().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_AGE);
            localDBLogger = initLocalDBLogger(localDB, maxEvents, maxAgeMS, pwmApplication);
            if (localDBLogger != null) {
                PwmLogger.setLocalDBLogger(localDBLogLevel, localDBLogger);
            }
        } catch (Exception e) {
            LOGGER.warn("unable to initialize localDBLogger: " + e.getMessage());
            return null;
        }

        // add appender for other packages;
        try {
            final LocalDBLog4jAppender localDBLog4jAppender = new LocalDBLog4jAppender(localDBLogger);
            localDBLog4jAppender.setThreshold(localDBLogLevel.getLog4jLevel());
            for (final Package logPackage : LOGGING_PACKAGES) {
                if (logPackage != null && !logPackage.equals(PwmApplication.class.getPackage())) {
                    final Logger logger = Logger.getLogger(logPackage.getName());
                    logger.addAppender(localDBLog4jAppender);
                    logger.setLevel(Level.TRACE);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("unable to initialize localDBLogger/extraAppender: " + e.getMessage());
        }

        return localDBLogger;
    }

    static LocalDBLogger initLocalDBLogger(
            final LocalDB pwmDB,
            final int maxEvents,
            final long maxAgeMS,
            final PwmApplication pwmApplication
    ) {
        final boolean devDebugMode = pwmApplication.getConfig().isDevDebugMode();
        try {
            final LocalDBLogger.Settings settings = new LocalDBLogger.Settings();
            settings.setMaxEvents(maxEvents);
            settings.setMaxAgeMs(maxAgeMS);
            settings.setDevDebug(devDebugMode);
            return new LocalDBLogger(pwmApplication, pwmDB, settings);
        } catch (LocalDBException e) {
            //nothing to do;
        }
        return null;
    }
}
