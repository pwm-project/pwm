/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.util.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.EncoderBase;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.joran.spi.ConfigurationWatchList;
import ch.qos.logback.core.joran.util.ConfigurationWatchListUtil;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.util.FileSize;
import org.slf4j.LoggerFactory;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.config.AppConfig;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class PwmLogManager
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmLogManager.class );

    private static final String LOGGER_NAME_LOCALDB = "pwmLocalDBLogger";
    private static final String LOGGER_NAME_FILE = "pwmFileLogger";
    private static final String LOGGER_NAME_CONSOLE = "pwmConsoleLogger";

    private static PwmApplication pwmApplication;
    private static LocalDBLogger localDBLogger;
    private static PwmLogSettings pwmLogSettings = PwmLogSettings.defaultSettings();
    private static PwmLogLevel lowestLogLevelConfigured = PwmLogLevel.TRACE;

    public static void disableLogging( )
    {
        if ( logbackConfigFileExists() )
        {
            LOGGER.info( () -> "skipping " + PwmConstants.PWM_APP_NAME
                    + " logback configuration, logback is already configured" );

            return;
        }

        final LoggerContext logCtx = getLoggerContext();

        final Logger rootLogger = logCtx.getLogger( Logger.ROOT_LOGGER_NAME );
        for ( final Iterator<Appender<ILoggingEvent>> iter = rootLogger.iteratorForAppenders(); iter.hasNext(); )
        {
            final Appender<ILoggingEvent> appender = iter.next();
            rootLogger.detachAppender( appender );
            appender.stop();
        }

        logCtx.reset();
        logCtx.stop();

        PwmLogManager.localDBLogger = null;
        PwmLogManager.pwmApplication = null;
        PwmLogManager.pwmLogSettings = PwmLogSettings.defaultSettings();
        lowestLogLevelConfigured = PwmLogLevel.TRACE;
    }

    public static void initializeLogging(
            final PwmApplication pwmApplication,
            final AppConfig config,
            final File pwmApplicationPath,
            final PwmLogSettings pwmLogSettings
    )
    {
        if ( pwmApplicationPath != null )
        {
            final File logbackXmlInAppPath = new File( pwmApplicationPath.getPath() + File.separator + "logback.xml" );
            if ( logbackXmlInAppPath.exists() )
            {
                if ( PwmLogUtil.initLogbackFromXmlFile( logbackXmlInAppPath ) )
                {
                    LOGGER.info( () -> "used appPath logback xml file '" + logbackXmlInAppPath.getPath()
                            + "' to configure logging system, will ignore configured logging settings " );
                }
            }
        }

        if ( logbackConfigFileExists() )
        {
            LOGGER.info( () -> "skipping " + PwmConstants.PWM_APP_NAME
                    + " logback configuration, logback is already configured" );

            return;
        }

        disableLogging();

        // all initialize lines start here (above line disables everything !!)

        PwmLogManager.pwmLogSettings = pwmLogSettings;

        PwmLogManager.pwmApplication = pwmApplication;

        lowestLogLevelConfigured = pwmLogSettings.calculateLowestLevel();

        initConsoleLogger( config, pwmLogSettings.getStdoutLevel() );

        initFileLogger( config, pwmLogSettings.getFileLevel(), pwmApplicationPath );
    }

    static PwmLogLevel getLowestLogLevelConfigured()
    {
        return lowestLogLevelConfigured;
    }

    static PwmLogSettings getPwmLogSettings()
    {
        return pwmLogSettings;
    }

    static LocalDBLogger getLocalDbLogger()
    {
        return localDBLogger;
    }

    static PwmApplication getPwmApplication()
    {
        return pwmApplication;
    }

    private static void initConsoleLogger(
            final AppConfig config,
            final PwmLogLevel consoleLogLevel
    )
    {
        final LoggerContext logCtx = getLoggerContext();

        final ConsoleAppender<ILoggingEvent> logConsoleAppender = new ConsoleAppender<>();
        logConsoleAppender.setContext( logCtx );
        logConsoleAppender.setName( LOGGER_NAME_CONSOLE );
        logConsoleAppender.setEncoder( makePatternLayoutEncoder( config ) );
        logConsoleAppender.addFilter( makeLevelFilter( consoleLogLevel ) );
        logConsoleAppender.start();

        attachAppender( logConsoleAppender );
    }

    static boolean logbackConfigFileExists()
    {
        final LoggerContext context = getLoggerContext();
        final ConfigurationWatchList configurationWatchList = ConfigurationWatchListUtil.getConfigurationWatchList( context );

        if ( configurationWatchList != null )
        {
            final List<File> watchList = ConfigurationWatchListUtil.getConfigurationWatchList( context ).getCopyOfFileWatchList();
            return !watchList.isEmpty();
        }

        return false;
    }

    static LoggerContext getLoggerContext()
    {
        return ( LoggerContext ) LoggerFactory.getILoggerFactory();
    }

    private static void attachAppender( final Appender<ILoggingEvent> appender )
    {
        final LoggerContext logCtx = getLoggerContext();

        PwmLogManager.getPwmLogSettings().getLoggingPackages().stream()
                .filter( Objects::nonNull )
                .map( logCtx::getLogger )
                .forEach( logger ->
                {
                    logger.setLevel( Level.TRACE );
                    logger.detachAppender( appender.getName() );
                    logger.addAppender( appender );
                } );
    }

    private static Filter<ILoggingEvent> makeLevelFilter( final PwmLogLevel pwmLogLevel )
    {
        final ThresholdFilter levelFilter = new ThresholdFilter();
        levelFilter.setContext( getLoggerContext() );
        levelFilter.setLevel( pwmLogLevel.getLogbackLevel().levelStr );
        levelFilter.start();
        return levelFilter;
    }

    private static EncoderBase<ILoggingEvent> makePatternLayoutEncoder( final AppConfig appConfig )
    {
        final String loggingPatternStr;
        if ( appConfig == null )
        {
            loggingPatternStr = "no-pattern %-12date{YYYY-MM-dd HH:mm:ss.SSS} %-5level – %msg%n";
        }
        else
        {
            if ( pwmLogSettings.getLogOutputMode() == PwmLogSettings.LogOutputMode.json )
            {
                loggingPatternStr = "%msg%n";
            }
            else
            {
                final PwmLogbackPattern pwmLogbackPattern = new PwmLogbackPattern();
                pwmLogbackPattern.setContext( getLoggerContext() );
                pwmLogbackPattern.start();
                final LayoutWrappingEncoder<ILoggingEvent> layoutWrappingEncoder = new LayoutWrappingEncoder<>();
                layoutWrappingEncoder.setLayout( pwmLogbackPattern );
                layoutWrappingEncoder.setContext( getLoggerContext() );
                layoutWrappingEncoder.start();
                return layoutWrappingEncoder;
            }
        }

        final PatternLayoutEncoder logEncoder = new PatternLayoutEncoder();
        logEncoder.setContext( getLoggerContext() );
        logEncoder.setPattern( loggingPatternStr );
        logEncoder.start();
        return logEncoder;
    }

    private static void initFileLogger(
            final AppConfig config,
            final PwmLogLevel fileLogLevel,
            final File pwmApplicationPath
    )
    {
        // configure file logging
        final String logDirectorySetting = config.readAppProperty( AppProperty.LOGGING_FILE_PATH );
        final File logDirectory = FileSystemUtility.figureFilepath( logDirectorySetting, pwmApplicationPath );

        if ( logDirectory != null && fileLogLevel != null )
        {
            try
            {
                if ( !logDirectory.exists() )
                {
                    if ( logDirectory.mkdir() )
                    {
                        LOGGER.info( () -> "created directory " + logDirectory.getAbsoluteFile() );
                    }
                    else
                    {
                        throw new IOException( "failed to create directory " + logDirectory.getAbsoluteFile() );
                    }
                }

                final String fileName = logDirectory.getAbsolutePath() + File.separator + PwmConstants.PWM_APP_NAME + ".log";
                final String fileNamePattern = logDirectory.getAbsolutePath() + File.separator + PwmConstants.PWM_APP_NAME + ".log.%i.zip";

                final LoggerContext logCtx = getLoggerContext();

                final FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
                rollingPolicy.setContext( logCtx );
                rollingPolicy.setMinIndex( 1 );
                rollingPolicy.setMaxIndex( Integer.parseInt( config.readAppProperty( AppProperty.LOGGING_FILE_MAX_ROLLOVER ) )  );
                rollingPolicy.setFileNamePattern( fileNamePattern );

                final SizeBasedTriggeringPolicy<ILoggingEvent> sizeBasedTriggeringPolicy = new SizeBasedTriggeringPolicy<>();
                sizeBasedTriggeringPolicy.setMaxFileSize( FileSize.valueOf( config.readAppProperty( AppProperty.LOGGING_FILE_MAX_SIZE ) ) );
                sizeBasedTriggeringPolicy.setContext( logCtx );
                sizeBasedTriggeringPolicy.start();

                final RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
                fileAppender.setContext( logCtx );
                fileAppender.setName( LOGGER_NAME_FILE );
                fileAppender.setEncoder( makePatternLayoutEncoder( config ) );
                fileAppender.setFile( fileName );
                fileAppender.setPrudent( false );
                fileAppender.addFilter( makeLevelFilter( fileLogLevel ) );
                fileAppender.setRollingPolicy( rollingPolicy );
                fileAppender.setTriggeringPolicy( sizeBasedTriggeringPolicy );

                rollingPolicy.setParent( fileAppender );

                rollingPolicy.start();
                fileAppender.start();

                attachAppender( fileAppender );

                LOGGER.debug( () -> "successfully initialized default file log4j config at log level "
                        + fileLogLevel );
            }
            catch ( final IOException e )
            {
                LOGGER.debug( () -> "error initializing RollingFileAppender: " + e.getMessage() );
            }
        }
    }

    public static LocalDBLogger initializeLocalDBLogger( final PwmApplication pwmApplication )
    {
        final LocalDB localDB = pwmApplication.getLocalDB();

        if ( pwmApplication.getApplicationMode() == PwmApplicationMode.READ_ONLY || pwmApplication.getPwmEnvironment().isInternalRuntimeInstance() )
        {
            LOGGER.trace( () -> "skipping initialization of LocalDBLogger due to read-only mode" );
            return null;
        }

        // initialize the localDBLogger
        final LocalDBLogger localDBLogger;
        final PwmLogSettings pwmLogSettings = PwmLogSettings.fromAppConfig( pwmApplication.getConfig() );
        try
        {
            localDBLogger = initLocalDBLogger( localDB, pwmApplication, pwmLogSettings.getLocalDbLevel() );
            if ( localDBLogger != null )
            {
                PwmLogManager.localDBLogger = localDBLogger;
            }
        }
        catch ( final Exception e )
        {
            LOGGER.warn( () -> "unable to initialize localDBLogger: " + e.getMessage() );
            return null;
        }

        // add appender for other packages;
        try
        {
            final LocalDBLogbackAppender localDBLogbackAppender = new LocalDBLogbackAppender( localDBLogger );
            localDBLogbackAppender.setContext( getLoggerContext() );
            localDBLogbackAppender.setName( LOGGER_NAME_LOCALDB );
            localDBLogbackAppender.addFilter( makeLevelFilter( pwmLogSettings.getLocalDbLevel() ) );
            localDBLogbackAppender.start();

            attachAppender( localDBLogbackAppender );
        }
        catch ( final Exception e )
        {
            LOGGER.warn( () -> "unable to initialize localDBLogger/extraAppender: " + e.getMessage() );
        }

        return localDBLogger;
    }

    static LocalDBLogger initLocalDBLogger(
            final LocalDB localDB,
            final PwmApplication pwmApplication,
            final PwmLogLevel level
    )
    {
        try
        {
            final LocalDBLoggerSettings settings = LocalDBLoggerSettings.fromConfiguration( pwmApplication.getConfig() );
            return new LocalDBLogger( pwmApplication, localDB, level, settings );
        }
        catch ( final LocalDBException e )
        {
            //nothing to do;
        }
        return null;
    }
}
