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

package password.pwm.http;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmEnvironment;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.stored.ConfigurationFileManager;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.X509CertificateValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PropertyConfigurationImporter;
import password.pwm.util.PwmScheduler;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmTimeUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.X509Utils;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpSession;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ContextManager implements Serializable
{
    private static final long serialVersionUID = 1L;

    private static final PwmLogger LOGGER = PwmLogger.forClass( ContextManager.class );
    private static final SessionLabel SESSION_LABEL = SessionLabel.CONTEXT_SESSION_LABEL;

    private static final TimeDuration RESTART_DELAY = TimeDuration.of( 5, TimeDuration.Unit.SECONDS );

    private final transient ServletContext servletContext;
    private final String contextPath;

    private transient ScheduledExecutorService taskMaster;

    private transient volatile PwmApplication pwmApplication;
    private transient ConfigurationFileManager configReader;
    private ErrorInformation startupErrorInformation;

    private final AtomicInteger restartCount = new AtomicInteger( 0 );
    private TimeDuration readApplicationLockMaxWait = TimeDuration.of( 10, TimeDuration.Unit.SECONDS );
    private final AtomicBoolean restartInProgressFlag = new AtomicBoolean();

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

        final PwmApplication pwmDomain = getPwmApplication( request.getServletContext() );
        request.setAttribute( PwmConstants.REQUEST_ATTR_PWM_APPLICATION, pwmDomain );
        return pwmDomain;
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

    public static String readEulaText( final ContextManager contextManager, final String filename )
            throws IOException
    {
        final String path = PwmConstants.URL_PREFIX_PUBLIC + "/resources/text/" + filename;
        final InputStream inputStream = contextManager.getResourceAsStream( path );
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        JavaHelper.copyWhilePredicate( inputStream, byteArrayOutputStream, o -> true );
        return byteArrayOutputStream.toString( PwmConstants.DEFAULT_CHARSET.name() );
    }

    public PwmApplication getPwmApplication( )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        PwmApplication localApplication = pwmApplication;

        while (
                ( restartInProgressFlag.get() || pwmApplication == null )
                        &&  TimeDuration.fromCurrent( startTime ).isShorterThan( readApplicationLockMaxWait )
        )
        {
            TimeDuration.SECOND.pause();
            localApplication = pwmApplication;
        }

        if ( localApplication != null )
        {
            if ( TimeDuration.fromCurrent( startTime ).isLongerThan( TimeDuration.SECOND ) )
            {
                LOGGER.trace( () -> "waited " + TimeDuration.compactFromCurrent( startTime )
                        + " to read pwmApplication due to restart in progress" );
            }
            return localApplication;
        }
        else
        {
            LOGGER.trace( () -> "could not read pwmApplication after waiting " + TimeDuration.compactFromCurrent( startTime ) );
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

    @SuppressFBWarnings( "MDM_SETDEFAULTLOCALE" )
    public void initialize( )
    {
        final Instant startTime = Instant.now();

        try
        {
            Locale.setDefault( PwmConstants.DEFAULT_LOCALE );
        }
        catch ( final Exception e )
        {
            outputError( "unable to set default locale as Java machine default locale: " + e.getMessage() );
        }

        AppConfig appConfig = null;
        PwmApplicationMode mode = PwmApplicationMode.ERROR;

        final ParameterReader parameterReader = new ParameterReader( servletContext );
        {
            final Optional<String> applicationPathStr = parameterReader.readApplicationPath();
            if ( applicationPathStr.isEmpty() )
            {
                startupErrorInformation = new ErrorInformation( PwmError.ERROR_ENVIRONMENT_ERROR, "application path is not specified" );
                return;
            }
            else
            {
                applicationPath = new File( applicationPathStr.get() );
            }
        }

        File configurationFile = null;
        try
        {
            configurationFile = locateConfigurationFile( applicationPath, PwmConstants.DEFAULT_CONFIG_FILE_FILENAME );

            configReader = new ConfigurationFileManager( configurationFile, SESSION_LABEL );
            appConfig = configReader.getConfiguration();

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
        catch ( final Throwable e )
        {
            handleStartupError( "unable to initialize application due to configuration related error: ", e );
        }

        {
            final String filename = configurationFile == null ? "null" : configurationFile.getAbsoluteFile().getAbsolutePath();
            LOGGER.debug( SESSION_LABEL, () -> "configuration file was loaded from " + ( filename ) );
        }

        final Collection<PwmEnvironment.ApplicationFlag> applicationFlags = parameterReader.readApplicationFlags();
        final Map<PwmEnvironment.ApplicationParameter, String> applicationParams = parameterReader.readApplicationParams( applicationPath );

        mode = PwmEnvironment.checkForTrial( mode );

        try
        {
            final PwmEnvironment pwmEnvironment = PwmEnvironment.builder()
                    .config( appConfig )
                    .applicationPath( applicationPath )
                    .applicationMode( mode )
                    .configurationFile( configurationFile )
                    .contextManager( this )
                    .flags( applicationFlags )
                    .parameters( applicationParams )
                    .build();

            if ( pwmApplication == null )
            {
                pwmApplication = PwmApplication.createPwmApplication( pwmEnvironment );
            }
            else
            {
                pwmApplication.reInit( pwmEnvironment );
            }
        }
        catch ( final Exception e )
        {
            handleStartupError( "unable to initialize application: ", e );
        }

        taskMaster = Executors.newSingleThreadScheduledExecutor(
                PwmScheduler.makePwmThreadFactory(
                        PwmScheduler.makeThreadName( SESSION_LABEL, pwmApplication, this.getClass() ) + "-",
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

            checkConfigForAutoImportLdapCerts( configReader );
        }

        if ( pwmApplication == null || pwmApplication.getApplicationMode() == PwmApplicationMode.NEW )
        {
            taskMaster.scheduleWithFixedDelay( new SilentPropertiesFileWatcher(), fileScanFrequencyMs, fileScanFrequencyMs, TimeUnit.MILLISECONDS );
        }

        LOGGER.trace( SESSION_LABEL, () -> "initialization complete (" + TimeDuration.compactFromCurrent( startTime ) + ")" );
    }

    private void checkConfigForAutoImportLdapCerts(
            final ConfigurationFileManager configReader
    )
    {
        if ( configReader == null || configReader.getStoredConfiguration() == null )
        {
            return;
        }

        {
            final Optional<String> importLdapCerts = configReader.getStoredConfiguration().readConfigProperty( ConfigurationProperty.IMPORT_LDAP_CERTIFICATES );
            if ( !importLdapCerts.isPresent() || !Boolean.parseBoolean( importLdapCerts.get() ) )
            {
                return;
            }
        }

        LOGGER.info( SESSION_LABEL, () -> "configuration file contains property \"" + ConfigurationProperty.IMPORT_LDAP_CERTIFICATES.getKey()
                + "\"=true, will import attempt ldap certificate import every " + PwmTimeUtil.asLongString( RESTART_DELAY ) + " until successful" );
        taskMaster.scheduleWithFixedDelay( new AutoImportLdapCertJob(), RESTART_DELAY.asMillis(), RESTART_DELAY.asMillis(), TimeUnit.MILLISECONDS );
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
            LOGGER.fatal( SESSION_LABEL, () -> startupErrorInformation.getDetailedErrorMsg() );
        }
        catch ( final Exception e2 )
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
            catch ( final Exception e )
            {
                LOGGER.error( () -> "unexpected error attempting to close application: " + e.getMessage() );
            }
        }
        taskMaster.shutdown();


        this.pwmApplication = null;
        startupErrorInformation = null;
    }

    public void requestPwmApplicationRestart( )
    {
        LOGGER.debug( SESSION_LABEL, () -> "immediate restart requested" );
        taskMaster.schedule( new RestartFlagWatcher(), 0, TimeUnit.MILLISECONDS );
    }

    public ConfigurationFileManager getConfigReader( )
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
                    LOGGER.info( SESSION_LABEL, () -> "configuration file modification has been detected" );
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
                    LOGGER.info( SESSION_LABEL, () -> "file " + silentPropertiesFile.getAbsolutePath() + " has appeared, will import as configuration" );
                    try
                    {
                        final PropertyConfigurationImporter importer = new PropertyConfigurationImporter();

                        final StoredConfiguration storedConfiguration;
                        try ( InputStream fileInputStream = Files.newInputStream( silentPropertiesFile.toPath() ) )
                        {
                            storedConfiguration = importer.readConfiguration( fileInputStream );
                        }

                        configReader.saveConfiguration( storedConfiguration, pwmApplication );
                        LOGGER.info( SESSION_LABEL, () -> "file " + silentPropertiesFile.getAbsolutePath() + " has been successfully imported and saved as configuration file" );
                        requestPwmApplicationRestart();
                        success = true;
                    }
                    catch ( final Exception e )
                    {
                        LOGGER.error( SESSION_LABEL, () -> "error importing " + silentPropertiesFile.getAbsolutePath() + ", error: " + e.getMessage() );
                    }

                    final String appendValue = success ? ".imported" : ".error";
                    final Path source = silentPropertiesFile.toPath();
                    final Path dest = source.resolveSibling( "silent.properties" + appendValue );

                    try
                    {
                        Files.move( source, dest );
                        LOGGER.info( SESSION_LABEL, () -> "file " + source + " has been renamed to " + dest );
                    }
                    catch ( final IOException e )
                    {
                        LOGGER.error( SESSION_LABEL, () -> "error renaming file " + source + " to " + dest + ", error: " + e.getMessage() );
                    }
                }
            }
        }
    }

    private class RestartFlagWatcher implements Runnable
    {

        @Override
        public void run( )
        {
            doRestart();
        }

        private void doRestart( )
        {
            final Instant startTime = Instant.now();

            if ( restartInProgressFlag.get() )
            {
                return;
            }

            if ( configReader != null && configReader.isSaveInProgress() )
            {
                final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );
                LOGGER.info( SESSION_LABEL, () -> "delaying restart request due to in progress file save (" + timeDuration.asCompactString() + ")" );
                taskMaster.schedule( new RestartFlagWatcher(), 1, TimeUnit.SECONDS );
                return;
            }

            try
            {
                restartInProgressFlag.set( true );
                waitForRequestsToComplete( pwmApplication );

                {
                    final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );
                    LOGGER.info( SESSION_LABEL, () -> "beginning application restart (" + timeDuration.asCompactString() + "), restart count=" + restartCount.incrementAndGet() );
                }

                try
                {
                    reInitialize();
                }
                catch ( final Exception e )
                {
                    LOGGER.fatal( () -> "unexpected error during shutdown: " + e.getMessage(), e );
                }

                LOGGER.info( SESSION_LABEL, () -> "application restart completed", TimeDuration.fromCurrent( startTime ) );
            }
            finally
            {
                restartInProgressFlag.set( false );
            }
        }

        private void waitForRequestsToComplete( final PwmApplication pwmApplication )
        {
            final Instant startTime = Instant.now();
            final TimeDuration maxRequestWaitTime = TimeDuration.of(
                    Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.APPLICATION_RESTART_MAX_REQUEST_WAIT_MS ) ),
                    TimeDuration.Unit.MILLISECONDS );
            final int startingRequestInProgress = pwmApplication.getTotalActiveServletRequests();

            if ( startingRequestInProgress == 0 )
            {
                return;
            }

            LOGGER.trace( SESSION_LABEL, () -> "waiting up to " + maxRequestWaitTime.asCompactString()
                    + " for " + startingRequestInProgress  + " requests to complete." );
            maxRequestWaitTime.pause( TimeDuration.of( 10, TimeDuration.Unit.MILLISECONDS ), () -> pwmApplication.getTotalActiveServletRequests() == 0
            );

            final int requestsInProgress = pwmApplication.getTotalActiveServletRequests();
            final TimeDuration waitTime = TimeDuration.fromCurrent( startTime  );
            LOGGER.trace( SESSION_LABEL, () -> "after " + waitTime.asCompactString() + ", " + requestsInProgress
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

    public Optional<File> locateWebInfFilePath( )
    {
        final String realPath = servletContext.getRealPath( "/WEB-INF" );

        if ( realPath != null )
        {
            final File servletPath = new File( realPath );
            if ( servletPath.exists() )
            {
                return Optional.of( servletPath );
            }
        }

        return Optional.empty();
    }

    private static void outputError( final String outputText )
    {
        final String msg = PwmConstants.PWM_APP_NAME + " " + StringUtil.toIsoDate( Instant.now() ) + " " + outputText;
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

        Optional<String> readApplicationPath( )
        {
            final Optional<String> contextAppPathSetting = readEnvironmentParameter( PwmEnvironment.EnvironmentParameter.applicationPath );
            if ( contextAppPathSetting.isPresent() )
            {
                return contextAppPathSetting;
            }

            final String contextPath = servletContext.getContextPath().replace( "/", "" );
            return PwmEnvironment.ParseHelper.readValueFromSystem(
                    PwmEnvironment.EnvironmentParameter.applicationPath,
                    contextPath
            );
        }

        Set<PwmEnvironment.ApplicationFlag> readApplicationFlags( )
        {
            final Optional<String> contextAppFlagsValue = readEnvironmentParameter( PwmEnvironment.EnvironmentParameter.applicationFlags );
            if ( contextAppFlagsValue.isPresent() )
            {
                return PwmEnvironment.ParseHelper.parseApplicationFlagValueParameter( contextAppFlagsValue.get() );
            }

            final String contextPath = servletContext.getContextPath().replace( "/", "" );
            return PwmEnvironment.ParseHelper.readApplicationFlagsFromSystem( contextPath );
        }

        Map<PwmEnvironment.ApplicationParameter, String> readApplicationParams( final File applicationPath  )
        {
            // attempt to read app params file from specified env param file value
            {
                final Optional<String> contextAppParamsValue = readEnvironmentParameter( PwmEnvironment.EnvironmentParameter.applicationParamFile );
                if ( contextAppParamsValue.isPresent() )
                {
                    return PwmEnvironment.ParseHelper.readAppParametersFromPath( contextAppParamsValue.get() );
                }
            }

            // attempt to read app params file from specified system file value
            {
                final String contextPath = servletContext.getContextPath().replace( "/", "" );
                final Map<PwmEnvironment.ApplicationParameter, String> results = PwmEnvironment.ParseHelper.readApplicationParmsFromSystem( contextPath );
                if ( !results.isEmpty() )
                {
                    return results;
                }
            }

            // attempt to read via application.properties in applicationPath
            if ( applicationPath != null && applicationPath.exists() )
            {
                final File appPropertiesFile = new File( applicationPath.getPath() + File.separator + "application.properties" );
                if ( appPropertiesFile.exists() )
                {
                    return PwmEnvironment.ParseHelper.readAppParametersFromPath( appPropertiesFile.getPath() );
                }
            }

            return Collections.emptyMap();
        }


        private Optional<String> readEnvironmentParameter( final PwmEnvironment.EnvironmentParameter environmentParameter )
        {
            final String value = servletContext.getInitParameter( environmentParameter.toString() );
            if ( StringUtil.notEmpty( value ) )
            {
                if ( !UNSPECIFIED_VALUE.equalsIgnoreCase( value ) )
                {
                    return Optional.of( value );
                }
            }
            return Optional.empty();
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



    private class AutoImportLdapCertJob implements Runnable
    {
        @Override
        public void run()
        {
            try
            {
                importLdapCert();
            }
            catch ( final Exception e )
            {
                LOGGER.error( SESSION_LABEL, () -> "error trying to auto-import certs: " + e.getMessage() );
            }
        }

        private void importLdapCert() throws PwmUnrecoverableException, IOException, PwmOperationalException
        {
            LOGGER.trace( SESSION_LABEL, () -> "beginning auto-import ldap cert due to config property '"
                    + ConfigurationProperty.IMPORT_LDAP_CERTIFICATES.getKey() + "'" );
            final AppConfig appConfig = new AppConfig( configReader.getStoredConfiguration() );
            final StoredConfigurationModifier modifiedConfig = StoredConfigurationModifier.newModifier( configReader.getStoredConfiguration() );

            int importedCerts = 0;
            for ( final DomainConfig domainConfig : appConfig.getDomainConfigs().values() )
            {
                for ( final LdapProfile ldapProfile : domainConfig.getLdapProfiles().values() )
                {
                    final List<String> ldapUrls = ldapProfile.getLdapUrls();
                    if ( !CollectionUtil.isEmpty( ldapUrls ) )
                    {
                        final Set<X509Certificate> certs = X509Utils.readCertsForListOfLdapUrls( ldapUrls, appConfig );
                        if ( !CollectionUtil.isEmpty( certs ) )
                        {
                            importedCerts += certs.size();
                            for ( final X509Certificate cert : certs )
                            {
                                LOGGER.trace( SESSION_LABEL, () -> "imported cert: " + X509Utils.makeDebugText( cert ) );
                            }
                            final StoredValue storedValue = X509CertificateValue.fromX509( certs );

                            final StoredConfigKey key = StoredConfigKey.forSetting( PwmSetting.LDAP_SERVER_CERTS, ldapProfile.getIdentifier(), domainConfig.getDomainID() );
                            modifiedConfig.writeSetting( key, storedValue, null );
                        }
                    }
                }
            }

            if ( importedCerts > 0 )
            {
                final int totalImportedCerts = importedCerts;
                LOGGER.trace( SESSION_LABEL, () -> "completed auto-import ldap cert due to config property '"
                        + ConfigurationProperty.IMPORT_LDAP_CERTIFICATES.getKey() + "'"
                        + ", imported " + totalImportedCerts + " certificates" );
                modifiedConfig.writeConfigProperty( ConfigurationProperty.IMPORT_LDAP_CERTIFICATES, "false" );
                configReader.saveConfiguration( modifiedConfig.newStoredConfiguration(), pwmApplication );
                requestPwmApplicationRestart();
            }
            else
            {
                LOGGER.trace( SESSION_LABEL, () -> "unable to completed auto-import ldap cert due to config property '"
                        + ConfigurationProperty.IMPORT_LDAP_CERTIFICATES.getKey() + "'"
                        + ", no LDAP urls are configured" );
            }
        }
    }

    private void reInitialize()
    {
        initialize();
    }
}
