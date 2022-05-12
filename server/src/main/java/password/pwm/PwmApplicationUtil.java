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
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.cli.commands.ExportHttpsTomcatConfigCommand;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBFactory;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogManager;
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

        if ( !pwmEnvironment.isInternalRuntimeInstance() && !pwmEnvironment.getFlags().contains( PwmEnvironment.ApplicationFlag.CommandLineInstance ) )
        {
            final String log4jFileName = pwmEnvironment.getConfig().readSettingAsString( PwmSetting.EVENTS_JAVA_LOG4JCONFIG_FILE );
            final File log4jFile = FileSystemUtility.figureFilepath( log4jFileName, pwmEnvironment.getApplicationPath() );
            final String consoleLevel;
            final String fileLevel;

            switch ( pwmApplication.getApplicationMode() )
            {
                case ERROR:
                case NEW:
                    consoleLevel = PwmLogLevel.TRACE.toString();
                    fileLevel = PwmLogLevel.TRACE.toString();
                    break;

                default:
                    consoleLevel = pwmEnvironment.getConfig().readSettingAsString( PwmSetting.EVENTS_JAVA_STDOUT_LEVEL );
                    fileLevel = pwmEnvironment.getConfig().readSettingAsString( PwmSetting.EVENTS_FILE_LEVEL );
                    break;
            }

            PwmLogManager.initializeLogger(
                    pwmApplication,
                    pwmApplication.getConfig(),
                    log4jFile,
                    consoleLevel,
                    pwmEnvironment.getApplicationPath(),
                    fileLevel );

            switch ( pwmApplication.getApplicationMode() )
            {
                case RUNNING:
                    break;

                case ERROR:
                    LOGGER.fatal( pwmApplication.getSessionLabel(), () -> "starting up in ERROR mode! Check log or health check information for cause" );
                    break;

                default:
                    LOGGER.trace( pwmApplication.getSessionLabel(), () -> "setting log level to TRACE because application mode is " + pwmApplication.getApplicationMode() );
                    break;
            }
        }
    }

    static String fetchInstanceID(
            final PwmApplication pwmApplication,
            final LocalDB localDB
    )
    {
        {
            final String newInstanceID = pwmApplication.getPwmEnvironment().getParameters().get( PwmEnvironment.ApplicationParameter.InstanceID );

            if ( !StringUtil.isTrimEmpty( newInstanceID ) )
            {
                return newInstanceID;
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

            final Map<PwmEnvironment.ApplicationParameter, String> applicationParams = pwmApplication.getPwmEnvironment().getParameters();
            final String keystoreFileString = applicationParams.get( PwmEnvironment.ApplicationParameter.AutoExportHttpsKeyStoreFile );
            if ( StringUtil.isEmpty( keystoreFileString ) )
            {
                return;
            }

            final File keyStoreFile = new File( keystoreFileString );
            final String password = applicationParams.get( PwmEnvironment.ApplicationParameter.AutoExportHttpsKeyStorePassword );
            final String alias = applicationParams.get( PwmEnvironment.ApplicationParameter.AutoExportHttpsKeyStoreAlias );
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
        try
        {
            final Map<PwmEnvironment.ApplicationParameter, String> applicationParams = pwmApplication.getPwmEnvironment().getParameters();
            final String tomcatOutputFileStr = applicationParams.get( PwmEnvironment.ApplicationParameter.AutoWriteTomcatConfOutputFile );
            if ( tomcatOutputFileStr != null && !tomcatOutputFileStr.isEmpty() )
            {
                LOGGER.trace( pwmApplication.getSessionLabel(),
                        () -> "attempting to output tomcat configuration file as configured by environment parameters to " + tomcatOutputFileStr );
                final File tomcatOutputFile = new File( tomcatOutputFileStr );
                final File tomcatSourceFile;
                {
                    final String tomcatSourceFileStr = applicationParams.get( PwmEnvironment.ApplicationParameter.AutoWriteTomcatConfSourceFile );
                    if ( tomcatSourceFileStr != null && !tomcatSourceFileStr.isEmpty() )
                    {
                        tomcatSourceFile = new File( tomcatSourceFileStr );
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
                                        + PwmEnvironment.ApplicationParameter.AutoWriteTomcatConfSourceFile + "' is not specified." );
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
        }
        catch ( final Exception e )
        {
            LOGGER.debug( pwmApplication.getSessionLabel(), () -> "error while generating tomcat conf output: " + e.getMessage() );
        }

    }

    static void outputConfigurationToLog( final PwmApplication pwmApplication, final DomainID domainID )
    {
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
                .map( s -> ( Supplier<CharSequence> ) () -> s )
                .forEach( s -> LOGGER.trace( pwmApplication.getSessionLabel(), s ) );

        final long itemCount = debugStrings.size();
        LOGGER.trace( pwmApplication.getSessionLabel(), () -> "--end current configuration output of " + itemCount + " items --",
                () -> TimeDuration.fromCurrent( startTime ) );
    }

    static void outputNonDefaultPropertiesToLog( final PwmApplication pwmApplication )
    {
        final Instant startTime = Instant.now();

        final Map<AppProperty, String> nonDefaultProperties = pwmApplication.getConfig().readAllNonDefaultAppProperties();
        if ( !CollectionUtil.isEmpty( nonDefaultProperties ) )
        {
            LOGGER.trace( pwmApplication.getSessionLabel(), () -> "--begin non-default app properties output--" );
            nonDefaultProperties.entrySet().stream()
                    .map( entry -> "AppProperty: " + entry.getKey().getKey() + " -> " + entry.getValue() )
                    .map( s -> ( Supplier<CharSequence> ) () -> s )
                    .forEach( s -> LOGGER.trace( pwmApplication.getSessionLabel(), s ) );
            LOGGER.trace( pwmApplication.getSessionLabel(), () -> "--end non-default app properties output--", () -> TimeDuration.fromCurrent( startTime ) );
        }
        else
        {
            LOGGER.trace( pwmApplication.getSessionLabel(), () -> "no non-default app properties in configuration" );
        }
    }

    static void outputApplicationInfoToLog( final PwmApplication pwmApplication )
    {
        final Instant startTime = Instant.now();

        final Map<PwmAboutProperty, String> aboutProperties = PwmAboutProperty.makeInfoBean( pwmApplication );
        if ( !CollectionUtil.isEmpty( aboutProperties ) )
        {
            LOGGER.trace( pwmApplication.getSessionLabel(), () -> "--begin application info--" );
            aboutProperties.entrySet().stream()
                    .map( entry -> "AppProperty: " + entry.getKey().getLabel() + " -> " + entry.getValue() )
                    .map( s -> ( Supplier<CharSequence> ) () -> s )
                    .forEach( s -> LOGGER.trace( pwmApplication.getSessionLabel(), s ) );
            LOGGER.trace( pwmApplication.getSessionLabel(), () -> "--end application info--", () -> TimeDuration.fromCurrent( startTime ) );
        }
        else
        {
            LOGGER.trace( pwmApplication.getSessionLabel(), () -> "no non-default app properties in configuration" );
        }
    }
}
