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

package password.pwm;

import password.pwm.bean.DomainID;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.cli.commands.ExportHttpsTomcatConfigCommand;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.CollectorUtil;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBFactory;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogManager;
import password.pwm.util.logging.PwmLogSettings;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.HttpsServerCertificateManager;
import password.pwm.util.secure.PwmRandom;
import password.pwm.util.secure.X509Utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.KeyStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class PwmApplicationUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmApplicationUtil.class );

    static final String DEFAULT_INSTANCE_ID = "-1";

    static LocalDB initializeLocalDB( final PwmApplication pwmApplication, final PwmEnvironment pwmEnvironment )
            throws PwmUnrecoverableException
    {
        final File databaseDirectory;

        try
        {
            final String localDBLocationSetting = pwmApplication.getConfig().readAppProperty( AppProperty.LOCALDB_LOCATION );
            databaseDirectory = FileSystemUtility.figureFilepath( localDBLocationSetting, pwmApplication.getPwmEnvironment().getApplicationPath() );
        }
        catch ( final Exception e )
        {
            pwmApplication.setLastLocalDBFailure( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, "error locating configured LocalDB directory: " + e.getMessage() ) );
            LOGGER.warn( pwmApplication.getSessionLabel(), () -> pwmApplication.getLastLocalDBFailure().toDebugStr() );
            throw new PwmUnrecoverableException( pwmApplication.getLastLocalDBFailure() );
        }

        LOGGER.debug( pwmApplication.getSessionLabel(), () -> "using localDB path " + databaseDirectory );

        // initialize the localDB
        try
        {
            final boolean readOnly = pwmApplication.getApplicationMode() == PwmApplicationMode.READ_ONLY;
            return LocalDBFactory.getInstance( databaseDirectory, readOnly, pwmEnvironment, pwmApplication.getConfig() );
        }
        catch ( final Exception e )
        {
            pwmApplication.setLastLocalDBFailure( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, "unable to initialize LocalDB: " + e.getMessage() ) );
            LOGGER.warn( pwmApplication.getSessionLabel(), () -> pwmApplication.getLastLocalDBFailure().toDebugStr() );
            throw new PwmUnrecoverableException( pwmApplication.getLastLocalDBFailure() );
        }
    }

    static void initializeLogging( final PwmApplication pwmApplication )
    {
        final PwmEnvironment pwmEnvironment = pwmApplication.getPwmEnvironment();

        if ( pwmEnvironment.isInternalRuntimeInstance() || pwmEnvironment.readPropertyAsBoolean( EnvironmentProperty.CommandLineInstance ) )
        {
            return;
        }

        final PwmLogSettings pwmLogSettings;
        switch ( pwmApplication.getApplicationMode() )
        {
            case ERROR:
            case NEW:
                pwmLogSettings = PwmLogSettings.defaultSettings();
                break;

            default:
                pwmLogSettings = PwmLogSettings.fromAppConfig( pwmApplication.getConfig() );
                break;
        }

        PwmLogManager.initializeLogging(
                pwmApplication,
                pwmApplication.getConfig(),
                pwmEnvironment.getApplicationPath(),
                pwmLogSettings );

    }

    static String fetchInstanceID(
            final PwmApplication pwmApplication,
            final LocalDB localDB
    )
    {
        {
            final Optional<String> newInstanceID = pwmApplication.getPwmEnvironment().readProperty( EnvironmentProperty.InstanceID );
            if ( newInstanceID.isPresent() )
            {
                return newInstanceID.get();
            }
        }

        if ( pwmApplication.getLocalDB() == null || pwmApplication.getApplicationMode() != PwmApplicationMode.RUNNING )
        {
            return DEFAULT_INSTANCE_ID;
        }

        {
            final Optional<String> optionalStoredInstanceID = pwmApplication.readAppAttribute( AppAttribute.INSTANCE_ID, String.class );
            if ( optionalStoredInstanceID.isPresent() )
            {
                final String instanceID = optionalStoredInstanceID.get();
                if ( !StringUtil.isTrimEmpty( instanceID ) )
                {
                    LOGGER.trace( pwmApplication.getSessionLabel(), () -> "retrieved instanceID " + instanceID + "" + " from localDB" );
                    return instanceID;
                }
            }
        }

        final PwmRandom pwmRandom = PwmRandom.getInstance();
        final String newInstanceID = Long.toHexString( pwmRandom.nextLong() ).toUpperCase();
        LOGGER.debug( pwmApplication.getSessionLabel(), () -> "generated new random instanceID " + newInstanceID );

        if ( localDB != null )
        {
            pwmApplication.writeAppAttribute( AppAttribute.INSTANCE_ID, newInstanceID );
        }

        return newInstanceID;
    }

    static void outputKeystore( final PwmApplication pwmApplication )
    {
        try
        {
            final PwmEnvironment pwmEnvironment = pwmApplication.getPwmEnvironment();
            final Optional<String> keystoreFileString = pwmEnvironment.readProperty( EnvironmentProperty.AutoExportHttpsKeyStoreFile );
            if ( keystoreFileString.isEmpty() )
            {
                return;
            }

            final File keyStoreFile = new File( keystoreFileString.get() );
            final String password = pwmEnvironment.readProperty( EnvironmentProperty.AutoExportHttpsKeyStorePassword )
                    .orElseThrow( () -> new IllegalArgumentException( "keystore export property is configured, but keystore password is not specified " ) );
            final String alias = pwmEnvironment.readProperty( EnvironmentProperty.AutoExportHttpsKeyStoreAlias )
                    .orElseThrow( () -> new IllegalArgumentException( "keystore export property is configured, but keystore alias is not specified " ) );
            final KeyStore keyStore = HttpsServerCertificateManager.keyStoreForApplication( pwmApplication, new PasswordData( password ), alias );
            X509Utils.outputKeystore( keyStore, keyStoreFile, password );
            LOGGER.info( pwmApplication.getSessionLabel(), () -> "exported application https key to keystore file " + keyStoreFile.getAbsolutePath() );
        }
        catch ( final Exception e )
        {
            LOGGER.debug( pwmApplication.getSessionLabel(), () -> "error while generating keystore output: " + e.getMessage() );
        }
    }

    static void outputTomcatConf( final PwmApplication pwmApplication )
    {
        final PwmEnvironment pwmEnvironment = pwmApplication.getPwmEnvironment();
        final Optional<String> tomcatOutputFileStr = pwmEnvironment.readProperty( EnvironmentProperty.AutoWriteTomcatConfOutputFile );
        if ( tomcatOutputFileStr.isEmpty() )
        {
            return;
        }

        try
        {
            LOGGER.trace( pwmApplication.getSessionLabel(),
                    () -> "attempting to output tomcat configuration file as configured by environment parameters to " + tomcatOutputFileStr );
            final File tomcatOutputFile = new File( tomcatOutputFileStr.get() );
            final File tomcatSourceFile;
            {
                final Optional<String> tomcatSourceFileStr = pwmEnvironment.readProperty( EnvironmentProperty.AutoWriteTomcatConfSourceFile );
                if ( tomcatSourceFileStr.isPresent() )
                {
                    tomcatSourceFile = new File( tomcatSourceFileStr.get() );
                    if ( !tomcatSourceFile.exists() )
                    {
                        LOGGER.error( pwmApplication.getSessionLabel(),
                                () -> "can not output tomcat configuration file, source file does not exist: " + tomcatSourceFile.getAbsolutePath() );
                        return;
                    }
                }
                else
                {
                    LOGGER.error( pwmApplication.getSessionLabel(),
                            () -> "can not output tomcat configuration file, source file parameter '"
                                    + EnvironmentProperty.AutoWriteTomcatConfSourceFile + "' is not specified." );
                    return;
                }
            }

            try ( ByteArrayOutputStream outputContents = new ByteArrayOutputStream() )
            {
                try ( InputStream fileInputStream = Files.newInputStream( tomcatOutputFile.toPath() ) )
                {
                    ExportHttpsTomcatConfigCommand.TomcatConfigWriter.writeOutputFile(
                            pwmApplication.getConfig(),
                            fileInputStream,
                            outputContents
                    );
                }

                if ( tomcatOutputFile.exists() )
                {
                    LOGGER.trace( pwmApplication.getSessionLabel(), () -> "deleting existing tomcat configuration file " + tomcatOutputFile.getAbsolutePath() );
                    if ( tomcatOutputFile.delete() )
                    {
                        LOGGER.trace( pwmApplication.getSessionLabel(), () -> "deleted existing tomcat configuration file: " + tomcatOutputFile.getAbsolutePath() );
                    }
                }

                try ( OutputStream fileOutputStream = Files.newOutputStream( tomcatOutputFile.toPath() ) )
                {
                    fileOutputStream.write( outputContents.toByteArray() );
                }
            }

            LOGGER.info( pwmApplication.getSessionLabel(), () -> "successfully wrote tomcat configuration to file " + tomcatOutputFile.getAbsolutePath() );
        }
        catch ( final Exception e )
        {
            LOGGER.debug( pwmApplication.getSessionLabel(), () -> "error while generating tomcat conf output: " + e.getMessage() );
        }

    }

    static void outputConfigurationToLog( final PwmApplication pwmApplication, final DomainID domainID )
    {
        if ( !checkIfOutputDumpingEnabled( pwmApplication ) )
        {
            return;
        }

        final Instant startTime = Instant.now();

        final Function<Map.Entry<String, String>, String> valueFormatter = entry ->
        {
            final String spacedValue = entry.getValue().replace( "\n", "\n   " );
            return " " + entry.getKey() + "\n   " + spacedValue;
        };

        final StoredConfiguration storedConfiguration = pwmApplication.getConfig().getStoredConfiguration();
        final List<StoredConfigKey> keys = CollectionUtil.iteratorToStream( storedConfiguration.keys() )
                .filter( key -> key.getDomainID().equals( domainID ) )
                .collect( Collectors.toList() );
        final Map<String, String> debugStrings = StoredConfigurationUtil.makeDebugMap(
                storedConfiguration,
                keys,
                PwmConstants.DEFAULT_LOCALE );

        LOGGER.trace( pwmApplication.getSessionLabel(), () -> "--begin current configuration output for domainID '" + domainID + "'--" );
        debugStrings.entrySet().stream()
                .map( valueFormatter )
                .map( s -> ( Supplier<String> ) () -> s )
                .forEach( s -> LOGGER.trace( pwmApplication.getSessionLabel(), s ) );

        final long itemCount = debugStrings.size();
        LOGGER.trace( pwmApplication.getSessionLabel(), () -> "--end current configuration output of " + itemCount + " items --",
                TimeDuration.fromCurrent( startTime ) );
    }

    static void outputNonDefaultPropertiesToLog( final PwmApplication pwmApplication )
    {
        final Map<String, String> data = pwmApplication.getConfig().readAllNonDefaultAppProperties().entrySet().stream()
                .collect( CollectorUtil.toUnmodifiableLinkedMap(
                        entry -> "AppProperty: " + entry.getKey().getKey(),
                        Map.Entry::getValue ) );

        outputMapToLog( pwmApplication, data, "non-default app properties" );
    }

    static void outputApplicationInfoToLog( final PwmApplication pwmApplication )
    {
        final Map<String, String> data = PwmAboutProperty.makeInfoBean( pwmApplication ).entrySet().stream()
                .collect( CollectorUtil.toUnmodifiableLinkedMap(
                        entry -> "AboutProperty: " + entry.getKey().getLabel(),
                        Map.Entry::getValue ) );

        outputMapToLog( pwmApplication, data, "about property info" );
    }

    private static void outputMapToLog(
            final PwmApplication pwmApplication,
            final Map<String, String> input,
            final String label
    )
    {
        LOGGER.trace( pwmApplication.getSessionLabel(), () -> "--begin " + label + "--" );

        if ( !CollectionUtil.isEmpty( input ) )
        {
            final String separator = " -> ";
            input.entrySet().stream()
                    .map( entry -> ( Supplier<String> ) () -> entry.getKey() + separator + entry.getValue() )
                    .forEach( s -> LOGGER.trace( pwmApplication.getSessionLabel(), s ) );
        }
        else
        {
            LOGGER.trace( pwmApplication.getSessionLabel(), () -> "no " + label + " values" );
        }

        LOGGER.trace( pwmApplication.getSessionLabel(), () -> "--end " + label + "--" );
    }

    private static boolean checkIfOutputDumpingEnabled( final PwmApplication pwmApplication )
    {
        return LOGGER.isInterestingLevel( PwmLogLevel.TRACE )
                && !pwmApplication.getPwmEnvironment().isInternalRuntimeInstance()
                && Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.LOGGING_OUTPUT_CONFIGURATION ) );
    }

    static String makeRuntimeNonce()
    {
        return PwmRandom.getInstance().randomUUID().toString();
    }
}
