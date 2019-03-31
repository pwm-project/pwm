/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.http;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmEnvironment;
import password.pwm.config.Configuration;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PropertyConfigurationImporter;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ContextManager implements Serializable
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( ContextManager.class );

    private transient ServletContext servletContext;
    private transient ScheduledExecutorService taskMaster;

    private transient volatile PwmApplication pwmApplication;
    private transient ConfigurationReader configReader;
    private ErrorInformation startupErrorInformation;

    private final AtomicInteger restartCount = new AtomicInteger( 0 );
    private TimeDuration readApplicationLockMaxWait = TimeDuration.SECONDS_30;
    private final Lock restartLock = new ReentrantLock();

    private String contextPath;
    private File applicationPath;

    private static final String UNSPECIFIED_VALUE = "unspecified";

    public ContextManager( final ServletContext servletContext )
    {
        this.servletContext = servletContext;
        this.contextPath = servletContext.getContextPath();
    }


    public static PwmApplication getPwmApplication( final ServletRequest request ) throws PwmUnrecoverableException
    {
        final PwmApplication appInRequest = ( PwmApplication ) request.getAttribute( PwmConstants.REQUEST_ATTR_PWM_APPLICATION );
        if ( appInRequest != null )
        {
            return appInRequest;
        }

        final PwmApplication pwmApplication = getPwmApplication( request.getServletContext() );
        request.setAttribute( PwmConstants.REQUEST_ATTR_PWM_APPLICATION, pwmApplication );
        return pwmApplication;


    }

    public static PwmApplication getPwmApplication( final HttpSession session ) throws PwmUnrecoverableException
    {
        return getContextManager( session.getServletContext() ).getPwmApplication();
    }

    public static PwmApplication getPwmApplication( final ServletContext theContext ) throws PwmUnrecoverableException
    {
        return getContextManager( theContext ).getPwmApplication();
    }

    public static ContextManager getContextManager( final HttpSession session ) throws PwmUnrecoverableException
    {
        return getContextManager( session.getServletContext() );
    }

    public static ContextManager getContextManager( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return getContextManager( pwmRequest.getHttpServletRequest().getServletContext() );
    }

    public static ContextManager getContextManager( final ServletContext theContext ) throws PwmUnrecoverableException
    {
        // context manager is initialized at servlet context startup.
        final Object theManager = theContext.getAttribute( PwmConstants.CONTEXT_ATTR_CONTEXT_MANAGER );
        if ( theManager == null )
        {
            final String errorMsg = "unable to load the context manager from servlet context";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_APP_UNAVAILABLE, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }

        return ( ContextManager ) theManager;
    }

    public PwmApplication getPwmApplication( )
            throws PwmUnrecoverableException
    {
        PwmApplication localApplication = this.pwmApplication;

        if ( localApplication == null )
        {
            try
            {
                final Instant startTime = Instant.now();
                final boolean hasLock = restartLock.tryLock( readApplicationLockMaxWait.asMillis(), TimeUnit.MILLISECONDS );
                if ( hasLock )
                {
                    localApplication = this.pwmApplication;
                    if ( localApplication == null )
                    {
                        LOGGER.trace( () -> "could not read pwmApplication after waiting " + TimeDuration.compactFromCurrent( startTime ) );
                    }
                    else
                    {
                        LOGGER.trace( () -> "waited " + TimeDuration.compactFromCurrent( startTime )
                                + " to read pwmApplication due to restart in progress" );
                    }
                }
            }
            catch ( InterruptedException e )
            {
                LOGGER.warn( "getPwmApplication restartLock unexpectedly interrupted" );
            }
            finally
            {
                restartLock.unlock();
            }
        }

        if ( localApplication != null )
        {
            return localApplication;
        }

        final ErrorInformation errorInformation;
        if ( startupErrorInformation != null )
        {
            errorInformation = startupErrorInformation;
        }
        else
        {
            final String msg = "application is not yet available, please try again in a moment.";
            errorInformation = new ErrorInformation( PwmError.ERROR_APP_UNAVAILABLE, msg );
        }
        throw new PwmUnrecoverableException( errorInformation );
    }

    public void initialize( )
    {
        final Instant startTime = Instant.now();

        try
        {
            Locale.setDefault( PwmConstants.DEFAULT_LOCALE );
        }
        catch ( Exception e )
        {
            outputError( "unable to set default locale as Java machine default locale: " + e.getMessage() );
        }

        Configuration configuration = null;
        PwmApplicationMode mode = PwmApplicationMode.ERROR;


        final ParameterReader parameterReader = new ParameterReader( servletContext );
        {
            final String applicationPathStr = parameterReader.readApplicationPath();
            if ( applicationPathStr == null || applicationPathStr.isEmpty() )
            {
                startupErrorInformation = new ErrorInformation( PwmError.ERROR_ENVIRONMENT_ERROR, "application path is not specified" );
                return;
            }
            else
            {
                applicationPath = new File( applicationPathStr );
            }
        }

        File configurationFile = null;
        try
        {
            configurationFile = locateConfigurationFile( applicationPath, PwmConstants.DEFAULT_CONFIG_FILE_FILENAME );

            configReader = new ConfigurationReader( configurationFile );
            configReader.getStoredConfiguration().lock();
            configuration = configReader.getConfiguration();

            mode = startupErrorInformation == null ? configReader.getConfigMode() : PwmApplicationMode.ERROR;

            if ( startupErrorInformation == null )
            {
                startupErrorInformation = configReader.getConfigFileError();
            }

            if ( PwmApplicationMode.ERROR == mode )
            {
                outputError( "Startup Error: " + ( startupErrorInformation == null ? "un-specified error" : startupErrorInformation.toDebugStr() ) );
            }
        }
        catch ( Throwable e )
        {
            handleStartupError( "unable to initialize application due to configuration related error: ", e );
        }

        {
            final String filename = configurationFile == null ? "null" : configurationFile.getAbsoluteFile().getAbsolutePath();
            LOGGER.debug( () -> "configuration file was loaded from " + ( filename ) );
        }

        final Collection<PwmEnvironment.ApplicationFlag> applicationFlags = parameterReader.readApplicationFlags();
        final Map<PwmEnvironment.ApplicationParameter, String> applicationParams = parameterReader.readApplicationParams();

        try
        {
            final PwmEnvironment pwmEnvironment = new PwmEnvironment.Builder( configuration, applicationPath )
                    .setApplicationMode( mode )
                    .setConfigurationFile( configurationFile )
                    .setContextManager( this )
                    .setFlags( applicationFlags )
                    .setParams( applicationParams )
                    .createPwmEnvironment();
            pwmApplication = new PwmApplication( pwmEnvironment );
        }
        catch ( Exception e )
        {
            handleStartupError( "unable to initialize application: ", e );
        }

        taskMaster = Executors.newSingleThreadScheduledExecutor(
                JavaHelper.makePwmThreadFactory(
                        JavaHelper.makeThreadName( pwmApplication, this.getClass() ) + "-",
                        true
                ) );

        boolean reloadOnChange = true;
        long fileScanFrequencyMs = 5000;
        {
            if ( pwmApplication != null )
            {
                reloadOnChange = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.CONFIG_RELOAD_ON_CHANGE ) );
                fileScanFrequencyMs = Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.CONFIG_FILE_SCAN_FREQUENCY ) );

                this.readApplicationLockMaxWait = TimeDuration.of(
                        Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.APPLICATION_READ_APP_LOCK_MAX_WAIT_MS ) ),
                        TimeDuration.Unit.MILLISECONDS
                );
            }
            if ( reloadOnChange )
            {
                taskMaster.scheduleWithFixedDelay( new ConfigFileWatcher(), fileScanFrequencyMs, fileScanFrequencyMs, TimeUnit.MILLISECONDS );
            }

            checkConfigForSaveOnRestart( configReader, pwmApplication );
        }

        if ( pwmApplication == null || pwmApplication.getApplicationMode() == PwmApplicationMode.NEW )
        {
            taskMaster.scheduleWithFixedDelay( new SilentPropertiesFileWatcher(), fileScanFrequencyMs, fileScanFrequencyMs, TimeUnit.MILLISECONDS );
        }

        LOGGER.trace( () -> "initialization complete (" + TimeDuration.compactFromCurrent( startTime ) + ")" );
    }

    private void checkConfigForSaveOnRestart(
            final ConfigurationReader configReader,
            final PwmApplication pwmApplication
    )
    {
        if ( configReader == null || configReader.getStoredConfiguration() == null )
        {
            return;
        }

        final String saveConfigOnRestartStrValue = configReader.getStoredConfiguration().readConfigProperty(
                ConfigurationProperty.CONFIG_ON_START );

        if ( saveConfigOnRestartStrValue == null || !Boolean.parseBoolean( saveConfigOnRestartStrValue ) )
        {
            return;
        }

        LOGGER.warn( "configuration file contains property \"" + ConfigurationProperty.CONFIG_ON_START + "\"=true, will save configuration and set property to false." );

        try
        {
            final StoredConfigurationImpl newConfig = StoredConfigurationImpl.copy( configReader.getStoredConfiguration() );
            newConfig.writeConfigProperty( ConfigurationProperty.CONFIG_ON_START, "false" );
            configReader.saveConfiguration( newConfig, pwmApplication, null );
            requestPwmApplicationRestart();
        }
        catch ( Exception e )
        {
            LOGGER.error( "error while saving configuration file commanded by property \"" + ConfigurationProperty.CONFIG_ON_START + "\"=true, error: " + e.getMessage() );
        }
    }

    private void handleStartupError( final String msgPrefix, final Throwable throwable )
    {
        final String errorMsg;
        if ( throwable instanceof OutOfMemoryError )
        {
            errorMsg = "JAVA OUT OF MEMORY ERROR!, please allocate more memory for java: " + throwable.getMessage();
            startupErrorInformation = new ErrorInformation( PwmError.ERROR_STARTUP_ERROR, errorMsg );
        }
        else if ( throwable instanceof PwmException )
        {
            startupErrorInformation = ( ( PwmException ) throwable ).getErrorInformation().wrapWithNewErrorCode( PwmError.ERROR_STARTUP_ERROR );
        }
        else
        {
            errorMsg = throwable.getMessage();
            startupErrorInformation = new ErrorInformation( PwmError.ERROR_APP_UNAVAILABLE, msgPrefix + errorMsg );
            throwable.printStackTrace();
        }

        try
        {
            LOGGER.fatal( startupErrorInformation.getDetailedErrorMsg() );
        }
        catch ( Exception e2 )
        {
            // noop
        }

        outputError( startupErrorInformation.getDetailedErrorMsg() );
    }

    public void shutdown( )
    {
        startupErrorInformation = new ErrorInformation( PwmError.ERROR_APP_UNAVAILABLE, "shutting down" );

        if ( pwmApplication != null )
        {
            try
            {
                pwmApplication.shutdown();
            }
            catch ( Exception e )
            {
                LOGGER.error( "unexpected error attempting to close application: " + e.getMessage() );
            }
        }
        taskMaster.shutdown();


        this.pwmApplication = null;
        startupErrorInformation = null;
    }

    public void requestPwmApplicationRestart( )
    {
        LOGGER.debug( () -> "immediate restart requested" );
        taskMaster.schedule( new RestartFlagWatcher(), 0, TimeUnit.MILLISECONDS );
    }

    public ConfigurationReader getConfigReader( )
    {
        return configReader;
    }

    private class ConfigFileWatcher implements Runnable
    {
        @Override
        public void run( )
        {
            if ( configReader != null )
            {
                if ( configReader.modifiedSinceLoad() )
                {
                    LOGGER.info( () -> "configuration file modification has been detected" );
                    requestPwmApplicationRestart();
                }
            }
        }
    }

    private class SilentPropertiesFileWatcher implements Runnable
    {
        private final File silentPropertiesFile;

        SilentPropertiesFileWatcher()
        {
            silentPropertiesFile = locateConfigurationFile( applicationPath, PwmConstants.DEFAULT_PROPERTIES_CONFIG_FILE_FILENAME );
        }

        @Override
        public void run()
        {
            if ( pwmApplication == null || pwmApplication.getApplicationMode() == PwmApplicationMode.NEW )
            {
                if ( silentPropertiesFile.exists() )
                {
                    boolean success = false;
                    LOGGER.info( () -> "file " + silentPropertiesFile.getAbsolutePath() + " has appeared, will import as configuration" );
                    try
                    {
                        final PropertyConfigurationImporter importer = new PropertyConfigurationImporter();
                        final StoredConfigurationImpl storedConfiguration = importer.readConfiguration( new FileInputStream( silentPropertiesFile ) );
                        configReader.saveConfiguration( storedConfiguration, pwmApplication, null );
                        LOGGER.info( () -> "file " + silentPropertiesFile.getAbsolutePath() + " has been successfully imported and saved as configuration file" );
                        requestPwmApplicationRestart();
                        success = true;
                    }
                    catch ( Exception e )
                    {
                        LOGGER.error( "error importing " + silentPropertiesFile.getAbsolutePath() + ", error: " + e.getMessage() );
                    }

                    final String appendValue = success ? ".imported" : ".error";
                    final Path source = silentPropertiesFile.toPath();
                    final Path dest = source.resolveSibling( "silent.properties" + appendValue );

                    try
                    {
                        Files.move( source, dest );
                        LOGGER.info( () -> "file " + source.toString() + " has been renamed to " + dest.toString() );
                    }
                    catch ( IOException e )
                    {
                        LOGGER.error( "error renaming file " + source.toString() + " to " + dest.toString() + ", error: " + e.getMessage() );
                    }
                }
            }
        }
    }

    private class RestartFlagWatcher implements Runnable
    {

        public void run( )
        {
            doRestart();
        }

        private void doRestart( )
        {
            final Instant startTime = Instant.now();

            if ( configReader != null && configReader.isSaveInProgress() )
            {
                final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );
                LOGGER.info( () -> "delaying restart request due to in progress file save (" + timeDuration.asCompactString() + ")" );
                taskMaster.schedule( new RestartFlagWatcher(), 1, TimeUnit.SECONDS );
                return;
            }

            restartLock.lock();
            final PwmApplication oldPwmApplication = pwmApplication;
            pwmApplication = null;

            try
            {
                waitForRequestsToComplete( oldPwmApplication );

                {
                    final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );
                    LOGGER.info( () -> "beginning application restart (" + timeDuration.asCompactString() + "), restart count=" + restartCount.incrementAndGet() );
                }

                final Instant shutdownStartTime = Instant.now();
                try
                {
                    try
                    {
                        // prevent restart watcher from detecting in-progress restart in a loop
                        taskMaster.shutdown();

                        oldPwmApplication.shutdown();
                    }
                    catch ( Exception e )
                    {
                        LOGGER.error( "unexpected error attempting to close application: " + e.getMessage() );
                    }
                }
                catch ( Exception e )
                {
                    LOGGER.fatal( "unexpected error during shutdown: " + e.getMessage(), e );
                }

                {
                    final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );
                    final TimeDuration shutdownDuration = TimeDuration.fromCurrent( shutdownStartTime );
                    LOGGER.info( () -> "application restart; shutdown completed, ("
                            + shutdownDuration.asCompactString()
                            + ") now starting new application instance ("
                            + timeDuration.asCompactString() + ")" );
                }
                initialize();

                {
                    final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );
                    LOGGER.info( () -> "application restart completed (" + timeDuration.asCompactString() + ")" );
                }
            }
            finally
            {
                restartLock.unlock();
            }
        }

        private void waitForRequestsToComplete( final PwmApplication pwmApplication )
        {
            final Instant startTime = Instant.now();
            final TimeDuration maxRequestWaitTime = TimeDuration.of(
                    Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.APPLICATION_RESTART_MAX_REQUEST_WAIT_MS ) ),
                    TimeDuration.Unit.SECONDS );
            final int startingRequetsInProgress = pwmApplication.getInprogressRequests().get();

            if ( startingRequetsInProgress == 0 )
            {
                return;
            }

            LOGGER.trace( () -> "waiting up to " + maxRequestWaitTime.asCompactString()
                    + " for " + startingRequetsInProgress  + " requests to complete." );
            JavaHelper.pause(
                    maxRequestWaitTime.asMillis(),
                    10,
                    o -> pwmApplication.getInprogressRequests().get() == 0
            );

            final int requestsInPrgoress = pwmApplication.getInprogressRequests().get();
            final TimeDuration waitTime = TimeDuration.fromCurrent( startTime  );
            LOGGER.trace( () -> "after " + waitTime.asCompactString() + ", " + requestsInPrgoress
                    + " requests in progress, proceeding with restart" );
        }
    }

    public ErrorInformation getStartupErrorInformation( )
    {
        return startupErrorInformation;
    }

    public int getRestartCount( )
    {
        return restartCount.get();
    }

    private File locateConfigurationFile( final File applicationPath, final String filename )
    {
        return new File( applicationPath.getAbsolutePath() + File.separator + filename );
    }

    public File locateWebInfFilePath( )
    {
        final String realPath = servletContext.getRealPath( "/WEB-INF" );

        if ( realPath != null )
        {
            final File servletPath = new File( realPath );
            if ( servletPath.exists() )
            {
                return servletPath;
            }
        }

        return null;
    }

    private static void outputError( final String outputText )
    {
        final String msg = PwmConstants.PWM_APP_NAME + " " + JavaHelper.toIsoDate( new Date() ) + " " + outputText;
        System.out.println( msg );
        System.out.println( msg );
    }

    public InputStream getResourceAsStream( final String path )
    {
        return servletContext.getResourceAsStream( path );
    }

    private static class ParameterReader
    {
        private final ServletContext servletContext;


        ParameterReader( final ServletContext servletContext )
        {
            this.servletContext = servletContext;
        }

        String readApplicationPath( )
        {
            final String contextAppPathSetting = readEnvironmentParameter( PwmEnvironment.EnvironmentParameter.applicationPath );
            if ( contextAppPathSetting != null )
            {
                return contextAppPathSetting;
            }

            final String contextPath = servletContext.getContextPath().replace( "/", "" );
            return PwmEnvironment.ParseHelper.readValueFromSystem(
                    PwmEnvironment.EnvironmentParameter.applicationPath,
                    contextPath
            );
        }

        Collection<PwmEnvironment.ApplicationFlag> readApplicationFlags( )
        {
            final String contextAppFlagsValue = readEnvironmentParameter( PwmEnvironment.EnvironmentParameter.applicationFlags );

            if ( contextAppFlagsValue != null && !contextAppFlagsValue.isEmpty() )
            {
                return PwmEnvironment.ParseHelper.parseApplicationFlagValueParameter( contextAppFlagsValue );
            }

            final String contextPath = servletContext.getContextPath().replace( "/", "" );
            return PwmEnvironment.ParseHelper.readApplicationFlagsFromSystem( contextPath );
        }

        Map<PwmEnvironment.ApplicationParameter, String> readApplicationParams( )
        {
            final String contextAppParamsValue = readEnvironmentParameter( PwmEnvironment.EnvironmentParameter.applicationParamFile );

            if ( contextAppParamsValue != null && !contextAppParamsValue.isEmpty() )
            {
                return PwmEnvironment.ParseHelper.parseApplicationParamValueParameter( contextAppParamsValue );
            }

            final String contextPath = servletContext.getContextPath().replace( "/", "" );
            return PwmEnvironment.ParseHelper.readApplicationParmsFromSystem( contextPath );
        }


        private String readEnvironmentParameter( final PwmEnvironment.EnvironmentParameter environmentParameter )
        {
            final String value = servletContext.getInitParameter(
                    environmentParameter.toString() );

            if ( value != null && !value.isEmpty() )
            {
                if ( !UNSPECIFIED_VALUE.equalsIgnoreCase( value ) )
                {
                    return value;
                }
            }
            return null;
        }
    }

    public String getServerInfo( )
    {
        return servletContext.getServerInfo();
    }

    public String getContextPath( )
    {
        return contextPath;
    }
}
