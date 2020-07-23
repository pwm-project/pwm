/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

import com.novell.ldapchai.ChaiUser;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;
import org.apache.log4j.xml.DOMConfigurator;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PwmLogManager
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmLogManager.class );

    public static final List<Package> LOGGING_PACKAGES = Collections.unmodifiableList( Arrays.asList(
            PwmApplication.class.getPackage(),
            ChaiUser.class.getPackage(),
            Package.getPackage( "org.jasig.cas.client" )
    ) );

    public static void deinitializeLogger( )
    {
        // clear all existing package loggers
        for ( final Package logPackage : LOGGING_PACKAGES )
        {
            if ( logPackage != null )
            {
                final Logger logger = Logger.getLogger( logPackage.getName() );
                logger.setAdditivity( false );
                logger.removeAllAppenders();
                logger.setLevel( Level.TRACE );
            }
        }

        PwmLogger.setLocalDBLogger( null, null );
        PwmLogger.setPwmApplication( null );
        PwmLogger.setFileAppender( null );
    }

    public static void initializeLogger(
            final PwmApplication pwmApplication,
            final Configuration config,
            final File log4jConfigFile,
            final String consoleLogLevel,
            final File pwmApplicationPath,
            final String fileLogLevel
    )
    {
        PwmLogger.setPwmApplication( pwmApplication );

        // try to configure using the log4j config file (if it exists)
        if ( log4jConfigFile != null )
        {
            try
            {
                if ( !log4jConfigFile.exists() )
                {
                    throw new Exception( "file not found: " + log4jConfigFile.getAbsolutePath() );
                }
                DOMConfigurator.configure( log4jConfigFile.getAbsolutePath() );
                LOGGER.debug( () -> "successfully initialized log4j using file " + log4jConfigFile.getAbsolutePath() );
                return;
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "error loading log4jconfig file '" + log4jConfigFile + "' error: " + e.getMessage() );
            }
        }

        deinitializeLogger();

        initConsoleLogger( config, consoleLogLevel );

        initFileLogger( config, fileLogLevel, pwmApplicationPath );

        // disable jersey warnings.
        java.util.logging.Logger.getLogger( "org.glassfish.jersey" ).setLevel( java.util.logging.Level.SEVERE );
    }


    public static void preInitConsoleLogLevel( final String pwmLogLevel )
    {
        try
        {
            initConsoleLogger( new Configuration( StoredConfigurationFactory.newConfig() ), pwmLogLevel );
        }
        catch ( final Exception e )
        {
            final String msg = "error pre-initializing logger: " + e.getMessage();
            System.err.println( msg );
        }
    }

    private static void initConsoleLogger(
            final Configuration config,
            final String consoleLogLevel
    )
    {
        final Layout patternLayout = new PatternLayout( config.readAppProperty( AppProperty.LOGGING_PATTERN ) );
        // configure console logging
        if ( consoleLogLevel != null && consoleLogLevel.length() > 0 && !"Off".equals( consoleLogLevel ) )
        {
            final ConsoleAppender consoleAppender = new ConsoleAppender( patternLayout );
            final Level level = Level.toLevel( consoleLogLevel );
            consoleAppender.setThreshold( level );
            for ( final Package logPackage : LOGGING_PACKAGES )
            {
                if ( logPackage != null )
                {
                    final Logger logger = Logger.getLogger( logPackage.getName() );
                    logger.setLevel( Level.TRACE );
                    logger.addAppender( consoleAppender );
                }
            }
            LOGGER.debug( () -> "successfully initialized default console log4j config at log level " + level.toString() );
        }
        else
        {
            LOGGER.debug( () -> "skipping stdout log4j initialization due to blank setting for log level" );
        }
    }

    private static void initFileLogger(
            final Configuration config,
            final String fileLogLevel,
            final File pwmApplicationPath
    )
    {
        final Layout patternLayout = new PatternLayout( config.readAppProperty( AppProperty.LOGGING_PATTERN ) );

        // configure file logging
        final String logDirectorySetting = config.readAppProperty( AppProperty.LOGGING_FILE_PATH );
        final File logDirectory = FileSystemUtility.figureFilepath( logDirectorySetting, pwmApplicationPath );

        if ( logDirectory != null && fileLogLevel != null && fileLogLevel.length() > 0 && !"Off".equals( fileLogLevel ) )
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
                final RollingFileAppender fileAppender = new RollingFileAppender( patternLayout, fileName, true );
                final Level level = Level.toLevel( fileLogLevel );
                fileAppender.setThreshold( level );
                fileAppender.setEncoding( PwmConstants.DEFAULT_CHARSET.name() );
                fileAppender.setMaxBackupIndex( Integer.parseInt( config.readAppProperty( AppProperty.LOGGING_FILE_MAX_ROLLOVER ) ) );
                fileAppender.setMaxFileSize( config.readAppProperty( AppProperty.LOGGING_FILE_MAX_SIZE ) );

                PwmLogger.setFileAppender( fileAppender );

                for ( final Package logPackage : LOGGING_PACKAGES )
                {
                    if ( logPackage != null )
                    {
                        //if (!logPackage.equals(PwmApplication.class.getPackage())) {
                        final Logger logger = Logger.getLogger( logPackage.getName() );
                        logger.setLevel( Level.TRACE );
                        logger.addAppender( fileAppender );
                        //}
                    }
                }
                LOGGER.debug( () -> "successfully initialized default file log4j config at log level " + level.toString() );
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

        if ( pwmApplication.getApplicationMode() == PwmApplicationMode.READ_ONLY )
        {
            LOGGER.trace( () -> "skipping initialization of LocalDBLogger due to read-only mode" );
            return null;
        }

        // initialize the localDBLogger
        final LocalDBLogger localDBLogger;
        final PwmLogLevel localDBLogLevel = pwmApplication.getConfig().getEventLogLocalDBLevel();
        try
        {
            localDBLogger = initLocalDBLogger( localDB, pwmApplication );
            if ( localDBLogger != null )
            {
                PwmLogger.setLocalDBLogger( localDBLogLevel, localDBLogger );
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
            final LocalDBLog4jAppender localDBLog4jAppender = new LocalDBLog4jAppender( localDBLogger );
            localDBLog4jAppender.setThreshold( localDBLogLevel.getLog4jLevel() );
            for ( final Package logPackage : LOGGING_PACKAGES )
            {
                if ( logPackage != null && !logPackage.equals( PwmApplication.class.getPackage() ) )
                {
                    final Logger logger = Logger.getLogger( logPackage.getName() );
                    logger.addAppender( localDBLog4jAppender );
                    logger.setLevel( Level.TRACE );
                }
            }
        }
        catch ( final Exception e )
        {
            LOGGER.warn( () -> "unable to initialize localDBLogger/extraAppender: " + e.getMessage() );
        }

        return localDBLogger;
    }

    static LocalDBLogger initLocalDBLogger(
            final LocalDB pwmDB,
            final PwmApplication pwmApplication
    )
    {
        try
        {
            final LocalDBLoggerSettings settings = LocalDBLoggerSettings.fromConfiguration( pwmApplication.getConfig() );
            return new LocalDBLogger( pwmApplication, pwmDB, settings );
        }
        catch ( final LocalDBException e )
        {
            //nothing to do;
        }
        return null;
    }
}
