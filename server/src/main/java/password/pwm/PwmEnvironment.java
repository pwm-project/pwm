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

package password.pwm;

import password.pwm.config.Configuration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class PwmEnvironment
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmEnvironment.class );

    // data elements
    private final PwmApplicationMode applicationMode;
    private final Configuration config;
    private final File applicationPath;
    private final boolean internalRuntimeInstance;
    private final File configurationFile;
    private final ContextManager contextManager;
    private final Collection<ApplicationFlag> flags;
    private final Map<ApplicationParameter, String> parameters;

    private final FileLocker fileLocker;

    public enum ApplicationParameter
    {
        AutoExportHttpsKeyStoreFile,
        AutoExportHttpsKeyStorePassword,
        AutoExportHttpsKeyStoreAlias,
        AutoWriteTomcatConfSourceFile,
        AutoWriteTomcatConfOutputFile,
        AppliancePort,
        ApplianceHostnameFile,
        ApplianceTokenFile,
        InstanceID,
        InitConsoleLogLevel,;

        public static ApplicationParameter forString( final String input )
        {
            return JavaHelper.readEnumFromString( ApplicationParameter.class, null, input );
        }
    }

    public enum ApplicationFlag
    {
        Appliance,
        Docker,
        ManageHttps,
        NoFileLock,
        CommandLineInstance,;

        public static ApplicationFlag forString( final String input )
        {
            return JavaHelper.readEnumFromString( ApplicationFlag.class, null, input );
        }
    }

    public enum EnvironmentParameter
    {
        applicationPath,
        applicationFlags,
        applicationParamFile,;

        public String conicalJavaOptionSystemName( )
        {
            return PwmConstants.PWM_APP_NAME.toLowerCase() + "." + this.toString();
        }

        public String conicalEnvironmentSystemName( )
        {
            return ( PwmConstants.PWM_APP_NAME.toLowerCase() + "_" + this.toString() ).toUpperCase();
        }

        public List<String> possibleNames( final String contextName )
        {
            final List<String> returnValues = new ArrayList<>();
            if ( contextName != null )
            {
                // java property format <app>.<context>.<paramName> like pwm.pwm.applicationFlag
                final String value = PwmConstants.PWM_APP_NAME.toLowerCase()
                        + "."
                        + contextName
                        + "."
                        + this.toString();
                returnValues.add( value );
                returnValues.add( value.toUpperCase() );
                returnValues.add( value.replace( ".", "_" ) );
                returnValues.add( value.toUpperCase().replace( ".", "_" ) );
            }
            {
                // java property format <app>.<paramName> like pwm.applicationFlag
                final String value = PwmConstants.PWM_APP_NAME.toLowerCase()
                        + "."
                        + this.toString();
                returnValues.add( value );
                returnValues.add( value.toUpperCase() );
                returnValues.add( value.replace( ".", "_" ) );
                returnValues.add( value.toUpperCase().replace( ".", "_" ) );
            }

            return Collections.unmodifiableList( returnValues );
        }
    }

    @SuppressWarnings( "checkstyle:ParameterNumber" )
    private PwmEnvironment(
            final PwmApplicationMode applicationMode,
            final Configuration config,
            final File applicationPath,
            final boolean internalRuntimeInstance,
            final File configurationFile,
            final ContextManager contextManager,
            final Collection<ApplicationFlag> flags,
            final Map<ApplicationParameter, String> parameters
    )
    {
        this.applicationMode = applicationMode == null ? PwmApplicationMode.ERROR : applicationMode;
        this.config = config;
        this.applicationPath = applicationPath;
        this.internalRuntimeInstance = internalRuntimeInstance;
        this.configurationFile = configurationFile;
        this.contextManager = contextManager;
        this.flags = flags == null ? Collections.emptySet() : Collections.unmodifiableSet( new HashSet<>( flags ) );
        this.parameters = parameters == null ? Collections.emptyMap() : Collections.unmodifiableMap( parameters );

        this.fileLocker = new FileLocker();

        verify();
    }

    public PwmApplicationMode getApplicationMode( )
    {
        return applicationMode;
    }

    public Configuration getConfig( )
    {
        return config;
    }

    public File getApplicationPath( )
    {
        return applicationPath;
    }

    public boolean isInternalRuntimeInstance( )
    {
        return internalRuntimeInstance;
    }

    public File getConfigurationFile( )
    {
        return configurationFile;
    }

    public ContextManager getContextManager( )
    {
        return contextManager;
    }

    public Collection<ApplicationFlag> getFlags( )
    {
        return flags;
    }

    public Map<ApplicationParameter, String> getParameters( )
    {
        return parameters;
    }

    private void verify( )
    {

    }

    public void verifyIfApplicationPathIsSetProperly( )
            throws PwmUnrecoverableException
    {
        final File applicationPath = this.getApplicationPath();

        verifyApplicationPath( applicationPath );

        boolean applicationPathIsWebInfPath = false;
        if ( applicationPath.getAbsolutePath().endsWith( "/WEB-INF" ) )
        {
            final File webXmlFile = new File( applicationPath.getAbsolutePath() + File.separator + "web.xml" );
            if ( webXmlFile.exists() )
            {
                applicationPathIsWebInfPath = true;
            }
        }
        if ( applicationPathIsWebInfPath )
        {
            LOGGER.trace( () -> "applicationPath appears to be servlet /WEB-INF directory" );
        }
    }

    public PwmEnvironment makeRuntimeInstance(
            final Configuration configuration
    )
            throws PwmUnrecoverableException
    {
        return new Builder( this )
                .setApplicationMode( PwmApplicationMode.NEW )
                .setInternalRuntimeInstance( true )
                .setConfigurationFile( null )
                .setConfig( configuration )
                .createPwmEnvironment();
    }


    public static void verifyApplicationPath( final File applicationPath ) throws PwmUnrecoverableException
    {

        if ( applicationPath == null )
        {
            throw new PwmUnrecoverableException(
                    new ErrorInformation( PwmError.ERROR_STARTUP_ERROR,
                            "unable to determine valid applicationPath" )
            );
        }

        LOGGER.trace( () -> "examining applicationPath of " + applicationPath.getAbsolutePath() + "" );

        if ( !applicationPath.exists() )
        {
            throw new PwmUnrecoverableException(
                    new ErrorInformation( PwmError.ERROR_STARTUP_ERROR,
                            "applicationPath " + applicationPath.getAbsolutePath() + " does not exist" )
            );
        }

        if ( !applicationPath.canRead() )
        {
            throw new PwmUnrecoverableException(
                    new ErrorInformation( PwmError.ERROR_STARTUP_ERROR,
                            "unable to read from applicationPath " + applicationPath.getAbsolutePath() + "" )
            );
        }

        if ( !applicationPath.canWrite() )
        {
            throw new PwmUnrecoverableException(
                    new ErrorInformation( PwmError.ERROR_STARTUP_ERROR,
                            "unable to write to applicationPath " + applicationPath.getAbsolutePath() + "" )
            );
        }

        final File infoFile = new File( applicationPath.getAbsolutePath() + File.separator + PwmConstants.APPLICATION_PATH_INFO_FILE );
        LOGGER.trace( () -> "checking " + infoFile.getAbsolutePath() + " status" );
        if ( infoFile.exists() )
        {
            final String errorMsg = "The file " + infoFile.getAbsolutePath() + " exists, and an applicationPath was not explicitly specified."
                    + "  This happens when an applicationPath was previously configured, but is not now being specified."
                    + "  An explicit applicationPath parameter must be specified, or the file can be removed if the applicationPath"
                    + " should be changed to the default /WEB-INF directory.";
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_STARTUP_ERROR, errorMsg ) );
        }

    }

    public static class ParseHelper
    {
        public static Collection<ApplicationFlag> readApplicationFlagsFromSystem( final String contextName )
        {
            final String rawValue = readValueFromSystem( EnvironmentParameter.applicationFlags, contextName );
            if ( rawValue != null )
            {
                return parseApplicationFlagValueParameter( rawValue );
            }
            return Collections.emptyList();
        }

        public static Map<ApplicationParameter, String> readApplicationParmsFromSystem( final String contextName )
        {
            final String rawValue = readValueFromSystem( EnvironmentParameter.applicationParamFile, contextName );
            if ( rawValue != null )
            {
                return readAppParametersFromPath( rawValue );
            }
            return Collections.emptyMap();
        }

        public static String readValueFromSystem( final PwmEnvironment.EnvironmentParameter parameter, final String contextName )
        {
            final List<String> namePossibilities = parameter.possibleNames( contextName );

            for ( final String propertyName : namePossibilities )
            {
                final String propValue = System.getProperty( propertyName );
                if ( propValue != null && !propValue.isEmpty() )
                {
                    return propValue;
                }
            }

            for ( final String propertyName : namePossibilities )
            {
                final String propValue = System.getenv( propertyName );
                if ( propValue != null && !propValue.isEmpty() )
                {
                    return propValue;
                }
            }

            return null;
        }

        public static Collection<ApplicationFlag> parseApplicationFlagValueParameter( final String input )
        {
            if ( input == null )
            {
                return Collections.emptyList();
            }

            try
            {
                final List<String> jsonValues = JsonUtil.deserializeStringList( input );
                final List<ApplicationFlag> returnFlags = new ArrayList<>();
                for ( final String value : jsonValues )
                {
                    final ApplicationFlag flag = ApplicationFlag.forString( value );
                    if ( value != null )
                    {
                        returnFlags.add( flag );
                    }
                    else
                    {
                        LOGGER.warn( () -> "unknown " + EnvironmentParameter.applicationFlags.toString() + " value: " + input );
                    }
                }
                return Collections.unmodifiableList( returnFlags );
            }
            catch ( final Exception e )
            {
                //
            }

            final List<ApplicationFlag> returnFlags = new ArrayList<>();
            for ( final String value : input.split( "," ) )
            {
                final ApplicationFlag flag = ApplicationFlag.forString( value );
                if ( value != null )
                {
                    returnFlags.add( flag );
                }
                else
                {
                    LOGGER.warn( () -> "unknown " + EnvironmentParameter.applicationFlags.toString() + " value: " + input );
                }
            }
            return returnFlags;
        }

        public static Map<ApplicationParameter, String> readAppParametersFromPath( final String input )
        {
            if ( input == null )
            {
                return Collections.emptyMap();
            }

            final Properties propValues = new Properties();
            try ( FileInputStream fileInputStream = new FileInputStream( new File( input ) ) )
            {
                propValues.load( fileInputStream );
            }
            catch ( final Exception e )
            {
                LOGGER.warn( () -> "error reading properties file '" + input + "' specified by environment setting "
                        + EnvironmentParameter.applicationParamFile.toString() + ", error: " + e.getMessage() );
            }

            try
            {
                final Map<ApplicationParameter, String> returnParams = new HashMap<>();
                for ( final Object key : propValues.keySet() )
                {

                    final ApplicationParameter param = ApplicationParameter.forString( key.toString() );
                    if ( param != null )
                    {
                        returnParams.put( param, propValues.getProperty( key.toString() ) );
                    }
                    else
                    {
                        LOGGER.warn( () -> "unknown " + EnvironmentParameter.applicationParamFile.toString() + " value: " + input );
                    }
                }
                return Collections.unmodifiableMap( returnParams );
            }
            catch ( final Exception e )
            {
                LOGGER.warn( () -> "unable to parse jason value of " + EnvironmentParameter.applicationParamFile.toString() + ", error: " + e.getMessage() );
            }

            return Collections.emptyMap();
        }
    }


    public static class Builder
    {
        private PwmApplicationMode applicationMode;
        private Configuration config;
        private File applicationPath;
        private boolean internalRuntimeInstance;
        private File configurationFile;
        private ContextManager contextManager;
        private Collection<ApplicationFlag> flags = new HashSet<>();
        private Map<ApplicationParameter, String> params = new HashMap<>();

        public Builder( final PwmEnvironment pwmEnvironment )
        {
            this.applicationMode = pwmEnvironment.applicationMode;
            this.config = pwmEnvironment.config;
            this.applicationPath = pwmEnvironment.applicationPath;
            this.internalRuntimeInstance = pwmEnvironment.internalRuntimeInstance;
            this.configurationFile = pwmEnvironment.configurationFile;
            this.contextManager = pwmEnvironment.contextManager;
            this.flags = pwmEnvironment.flags;
            this.params = pwmEnvironment.parameters;
        }

        public Builder( final Configuration config, final File applicationPath )
        {
            this.config = config;
            this.applicationPath = applicationPath;
        }

        public Builder setApplicationMode( final PwmApplicationMode applicationMode )
        {
            if ( PwmConstants.TRIAL_MODE && applicationMode == PwmApplicationMode.RUNNING )
            {
                LOGGER.info( () -> "application is in trial mode" );
                this.applicationMode = PwmApplicationMode.CONFIGURATION;
            }
            else
            {
                this.applicationMode = applicationMode;
            }
            return this;
        }

        public Builder setInternalRuntimeInstance( final boolean internalRuntimeInstance )
        {
            this.internalRuntimeInstance = internalRuntimeInstance;
            return this;
        }

        public Builder setConfigurationFile( final File configurationFile )
        {
            this.configurationFile = configurationFile;
            return this;
        }

        public Builder setContextManager( final ContextManager contextManager )
        {
            this.contextManager = contextManager;
            return this;
        }

        public Builder setFlags( final Collection<ApplicationFlag> flags )
        {
            this.flags.clear();
            if ( flags != null )
            {
                this.flags.addAll( flags );
            }
            return this;
        }

        public Builder setParams( final Map<ApplicationParameter, String> params )
        {
            this.params.clear();
            if ( params != null )
            {
                this.params.putAll( params );
            }
            return this;
        }

        public Builder setConfig( final Configuration config )
        {
            this.config = config;
            return this;
        }

        public PwmEnvironment createPwmEnvironment( )
        {
            return new PwmEnvironment(
                    applicationMode,
                    config,
                    applicationPath,
                    internalRuntimeInstance,
                    configurationFile,
                    contextManager,
                    flags,
                    params
            );
        }
    }

    public void attemptFileLock( )
    {
        fileLocker.attemptFileLock();
    }

    public void releaseFileLock( )
    {
        fileLocker.releaseFileLock();
    }

    public boolean isFileLocked( )
    {
        return fileLocker.isLocked();
    }

    public void waitForFileLock( ) throws PwmUnrecoverableException
    {
        final int maxWaitSeconds = this.getFlags().contains( ApplicationFlag.CommandLineInstance )
                ? 1
                : Integer.parseInt( getConfig().readAppProperty( AppProperty.APPLICATION_FILELOCK_WAIT_SECONDS ) );
        final Instant startTime = Instant.now();
        final TimeDuration attemptInterval = TimeDuration.of( 5021, TimeDuration.Unit.MILLISECONDS );

        while ( !this.isFileLocked() && TimeDuration.fromCurrent( startTime ).isShorterThan( maxWaitSeconds, TimeDuration.Unit.SECONDS ) )
        {
            attemptFileLock();

            if ( !isFileLocked() )
            {
                LOGGER.debug( () -> "can't establish application file lock after "
                        + TimeDuration.fromCurrent( startTime ).asCompactString()
                        + ", will retry;" );
                attemptInterval.pause();
            }
        }

        if ( !isFileLocked() )
        {
            final String errorMsg = "unable to obtain application path file lock";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_STARTUP_ERROR, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    private class FileLocker
    {
        private FileLock lock;
        private final File lockfile;

        FileLocker( )
        {
            final String lockfileName = config.readAppProperty( AppProperty.APPLICATION_FILELOCK_FILENAME );
            lockfile = new File( getApplicationPath(), lockfileName );
        }

        private boolean lockingAllowed( )
        {
            return !isInternalRuntimeInstance() && !getFlags().contains( ApplicationFlag.NoFileLock );
        }

        public boolean isLocked( )
        {
            return !lockingAllowed() || lock != null && lock.isValid();
        }

        public void attemptFileLock( )
        {
            if ( lockingAllowed() && !isLocked() )
            {
                try
                {
                    final RandomAccessFile file = new RandomAccessFile( lockfile, "rw" );
                    final FileChannel f = file.getChannel();
                    lock = f.tryLock();
                    if ( lock != null )
                    {
                        LOGGER.debug( () -> "obtained file lock on file " + lockfile.getAbsolutePath() + " lock is valid=" + lock.isValid() );
                        writeLockFileContents( file );
                    }
                    else
                    {
                        LOGGER.debug( () -> "unable to obtain file lock on file " + lockfile.getAbsolutePath() );
                    }
                }
                catch ( final Exception e )
                {
                    LOGGER.error( () -> "unable to obtain file lock on file " + lockfile.getAbsolutePath() + " due to error: " + e.getMessage() );
                }
            }
        }

        void writeLockFileContents( final RandomAccessFile file )
        {
            try
            {
                final Properties props = new Properties();
                props.put( "timestamp", JavaHelper.toIsoDate( Instant.now() ) );
                props.put( "applicationPath", PwmEnvironment.this.getApplicationPath() == null ? "n/a" : PwmEnvironment.this.getApplicationPath().getAbsolutePath() );
                props.put( "configurationFile", PwmEnvironment.this.getConfigurationFile() == null ? "n/a" : PwmEnvironment.this.getConfigurationFile().getAbsolutePath() );
                final String comment = PwmConstants.PWM_APP_NAME + " file lock";
                final StringWriter stringWriter = new StringWriter();
                props.store( stringWriter, comment );
                file.write( stringWriter.getBuffer().toString().getBytes( PwmConstants.DEFAULT_CHARSET ) );
            }
            catch ( final IOException e )
            {
                LOGGER.error( () -> "unable to write contents of application lock file: " + e.getMessage() );
            }
            // do not close FileWriter, otherwise lock is released.
        }

        public void releaseFileLock( )
        {
            if ( lock != null && lock.isValid() )
            {
                try
                {
                    lock.release();
                }
                catch ( final IOException e )
                {
                    LOGGER.error( () -> "error releasing file lock: " + e.getMessage() );
                }

                LOGGER.debug( () -> "released file lock on file " + lockfile.getAbsolutePath() );
            }
        }
    }
}
