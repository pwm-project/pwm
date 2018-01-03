/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class ContextManager implements Serializable
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( ContextManager.class );

    private transient ServletContext servletContext;
    private transient Timer taskMaster;

    private transient PwmApplication pwmApplication;
    private transient ConfigurationReader configReader;
    private ErrorInformation startupErrorInformation;

    private volatile boolean restartRequestedFlag = false;
    private int restartCount = 0;
    private final String instanceGuid;

    private String contextPath;

    private static final String UNSPECIFIED_VALUE = "unspecified";

    public ContextManager( final ServletContext servletContext )
    {
        this.servletContext = servletContext;
        this.instanceGuid = PwmRandom.getInstance().randomUUID().toString();
        this.contextPath = servletContext.getContextPath();
    }


    public static PwmApplication getPwmApplication( final HttpServletRequest request ) throws PwmUnrecoverableException
    {
        return getPwmApplication( request.getServletContext() );
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
        if ( pwmApplication == null )
        {
            final ErrorInformation errorInformation;
            if ( startupErrorInformation != null )
            {
                errorInformation = startupErrorInformation;
            }
            else
            {
                errorInformation = new ErrorInformation( PwmError.ERROR_APP_UNAVAILABLE, "application is not yet available, please try again in a moment." );
            }
            throw new PwmUnrecoverableException( errorInformation );
        }
        return pwmApplication;
    }

    public void initialize( )
    {

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
        final File applicationPath;
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
            configurationFile = locateConfigurationFile( applicationPath );

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
        LOGGER.debug( "configuration file was loaded from " + ( configurationFile == null ? "null" : configurationFile.getAbsoluteFile() ) );

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

        final String threadName = JavaHelper.makeThreadName( pwmApplication, this.getClass() ) + " timer";
        taskMaster = new Timer( threadName, true );
        taskMaster.schedule( new RestartFlagWatcher(), 1031, 1031 );

        boolean reloadOnChange = true;
        long fileScanFrequencyMs = 5000;
        {
            if ( pwmApplication != null )
            {
                reloadOnChange = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.CONFIG_RELOAD_ON_CHANGE ) );
                fileScanFrequencyMs = Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.CONFIG_FILE_SCAN_FREQUENCY ) );
            }
            if ( reloadOnChange )
            {
                taskMaster.schedule( new ConfigFileWatcher(), fileScanFrequencyMs, fileScanFrequencyMs );
            }

            checkConfigForSaveOnRestart( configReader, pwmApplication );
        }
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
            restartRequestedFlag = true;
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
        taskMaster.cancel();


        this.pwmApplication = null;
        startupErrorInformation = null;
    }

    public void requestPwmApplicationRestart( )
    {
        restartRequestedFlag = true;
        try
        {
            taskMaster.schedule( new ConfigFileWatcher(), 0 );
        }
        catch ( IllegalStateException e )
        {
            LOGGER.debug( "could not schedule config file watcher, timer is in illegal state: " + e.getMessage() );
        }
    }

    public ConfigurationReader getConfigReader( )
    {
        return configReader;
    }

    private class ConfigFileWatcher extends TimerTask
    {
        @Override
        public void run( )
        {
            if ( configReader != null )
            {
                if ( configReader.modifiedSinceLoad() )
                {
                    LOGGER.info( "configuration file modification has been detected" );
                    restartRequestedFlag = true;
                }
            }
        }
    }

    private class RestartFlagWatcher extends TimerTask
    {

        public void run( )
        {
            if ( restartRequestedFlag )
            {
                doReinitialize();
            }
        }

        private void doReinitialize( )
        {
            if ( configReader != null && configReader.isSaveInProgress() )
            {
                LOGGER.info( "delaying restart request due to in progress file save" );
                return;
            }

            LOGGER.info( "beginning application restart" );
            try
            {
                shutdown();
            }
            catch ( Exception e )
            {
                LOGGER.fatal( "unexpected error during shutdown: " + e.getMessage(), e );
            }

            LOGGER.info( "application restart; shutdown completed, now starting new application instance" );
            restartCount++;
            initialize();

            LOGGER.info( "application restart completed" );
            restartRequestedFlag = false;
        }
    }

    public ErrorInformation getStartupErrorInformation( )
    {
        return startupErrorInformation;
    }

    public int getRestartCount( )
    {
        return restartCount;
    }

    public File locateConfigurationFile( final File applicationPath )
            throws Exception
    {
        return new File( applicationPath.getAbsolutePath() + File.separator + PwmConstants.DEFAULT_CONFIG_FILE_FILENAME );
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

    static void outputError( final String outputText )
    {
        final String msg = PwmConstants.PWM_APP_NAME + " " + JavaHelper.toIsoDate( new Date() ) + " " + outputText;
        System.out.println( msg );
        System.out.println( msg );
    }

    public String getInstanceGuid( )
    {
        return instanceGuid;
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
