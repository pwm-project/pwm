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

package password.pwm.config.stored;

import password.pwm.AppAttribute;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.AppConfig;
import password.pwm.config.value.StoredValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.event.AuditServiceClient;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read and write the PWM configuration XML file from the filesystem.
 *
 * @author Jason D. Rivard
 */
public class ConfigurationFileManager
{
    private static final PwmLogger LOGGER = PwmLogger.getLogger( ConfigurationFileManager.class.getName() );

    private final Path configFile;
    private final String configFileChecksum;
    private final SessionLabel sessionLabel;

    private AppConfig domainConfig;
    private StoredConfiguration storedConfiguration;
    private ErrorInformation configFileError;

    private PwmApplicationMode configMode = PwmApplicationMode.NEW;

    private volatile boolean saveInProgress;

    public ConfigurationFileManager( final Path configFile, final SessionLabel sessionLabel )
            throws PwmUnrecoverableException
    {
        this.configFile = configFile;
        this.sessionLabel = sessionLabel;

        {
            String localChecksum = "";

            try
            {
                localChecksum = readFileChecksum( configFile );
            }
            catch ( final IOException e )
            {
                this.configFileError = new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, "i/o error: " + e.getMessage() );
                LOGGER.warn( sessionLabel, () -> "error reading configuration file: " + e.getMessage() );
            }
            configFileChecksum = localChecksum;
        }


        try
        {
            this.storedConfiguration = readStoredConfig();
            this.configFileError = null;
        }
        catch ( final PwmUnrecoverableException e )
        {
            this.configFileError = e.getErrorInformation();
            LOGGER.warn( sessionLabel, () -> "error reading configuration file: " + e.getMessage() );
        }

        if ( storedConfiguration == null )
        {
            this.storedConfiguration = StoredConfigurationFactory.newConfig();
        }

        LOGGER.debug( sessionLabel, () -> "configuration mode: " + configMode );
    }

    public PwmApplicationMode getConfigMode( )
    {
        return configMode;
    }

    public StoredConfiguration getStoredConfiguration( )
    {
        return storedConfiguration;
    }

    public AppConfig getConfiguration( ) throws PwmUnrecoverableException
    {
        if ( domainConfig == null )
        {
            final StoredConfiguration newStoredConfig = this.storedConfiguration == null
                    ? StoredConfigurationFactory.newConfig()
                    : this.storedConfiguration;
            domainConfig = AppConfig.forStoredConfig( newStoredConfig );
        }
        return domainConfig;
    }

    private StoredConfiguration readStoredConfig( ) throws PwmUnrecoverableException
    {
        LOGGER.debug( sessionLabel, () -> "loading configuration file: " + configFile );

        if ( !Files.exists( configFile ) )
        {
            LOGGER.warn( sessionLabel, () -> "configuration file '" + configFile + "' does not exist" );
            return null;
        }

        final StoredConfiguration storedConfiguration;
        final Instant startTime = Instant.now();

        try ( InputStream theFileData = Files.newInputStream( configFile ) )
        {
            try
            {
                storedConfiguration = StoredConfigurationFactory.input( theFileData );
            }
            catch ( final Exception e )
            {
                final String errorMsg = "unable to parse configuration file: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                        {
                                errorMsg,
                        }
                );
                this.configMode = PwmApplicationMode.ERROR;
                throw new PwmUnrecoverableException( errorInformation, e );
            }

            final List<String> validationErrorMsgs = StoredConfigurationUtil.validateValues( storedConfiguration );
            if ( !CollectionUtil.isEmpty( validationErrorMsgs ) )
            {
                final String errorMsg = "value error in config file, please investigate: " + validationErrorMsgs.get( 0 );
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                        {
                                errorMsg,
                        }
                );
                this.configMode = PwmApplicationMode.ERROR;
                throw new PwmUnrecoverableException( errorInformation );
            }

            ConfigurationVerifier.verifyConfiguration( storedConfiguration );

            final String fileSize = StringUtil.formatDiskSize( Files.size( configFile ) );
            LOGGER.debug( sessionLabel, () -> "configuration reading/parsing of " + fileSize + " complete", TimeDuration.fromCurrent( startTime ) );
        }
        catch ( final Exception e )
        {
            final String errorMsg = "unable to read configuration file: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                    {
                            errorMsg,
                    }
            );
            this.configMode = PwmApplicationMode.ERROR;
            throw new PwmUnrecoverableException( errorInformation );
        }

        final Optional<String> configIsEditable = storedConfiguration.readConfigProperty( ConfigurationProperty.CONFIG_IS_EDITABLE );
        if ( PwmConstants.TRIAL_MODE || ( configIsEditable.isPresent() && "true".equalsIgnoreCase( configIsEditable.get() ) ) )
        {
            this.configMode = PwmApplicationMode.CONFIGURATION;
        }
        else
        {
            this.configMode = PwmApplicationMode.RUNNING;
        }

        return storedConfiguration;
    }

    public void saveConfiguration(
            final StoredConfiguration storedConfiguration,
            final PwmApplication pwmApplication
    )
            throws IOException, PwmUnrecoverableException, PwmOperationalException
    {
        Path backupDirectory = null;
        int backupRotations = 0;
        if ( pwmApplication != null )
        {
            final AppConfig domainConfig = AppConfig.forStoredConfig( storedConfiguration );
            final String backupDirSetting = domainConfig.readAppProperty( AppProperty.BACKUP_LOCATION );
            if ( backupDirSetting != null && backupDirSetting.length() > 0 )
            {
                final Path pwmPath = pwmApplication.getPwmEnvironment().getApplicationPath();
                backupDirectory = FileSystemUtility.figureFilepath( backupDirSetting, pwmPath );
            }
            backupRotations = Integer.parseInt( domainConfig.readAppProperty( AppProperty.BACKUP_CONFIG_COUNT ) );
        }


        {
            // increment the config epoch
            String newEpochStrValue = "0";
            try
            {
                final Optional<String> storedEpochStrValue = storedConfiguration.readConfigProperty( ConfigurationProperty.CONFIG_EPOCH );
                final BigInteger epochValue = storedEpochStrValue.map( BigInteger::new ).orElse( BigInteger.ZERO );
                newEpochStrValue = epochValue.add( BigInteger.ONE ).toString();
            }
            catch ( final Exception e )
            {
                LOGGER.error( sessionLabel, () -> "error trying to parse previous config epoch property: " + e.getMessage() );
            }

            final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );
            modifier.writeConfigProperty( ConfigurationProperty.CONFIG_EPOCH, newEpochStrValue );
            this.storedConfiguration = modifier.newStoredConfiguration();
        }

        if ( backupDirectory != null && !Files.exists( backupDirectory ) )
        {
            Files.createDirectories( backupDirectory );
        }

        if ( pwmApplication != null && pwmApplication.getAuditService() != null )
        {
            auditModifiedSettings( pwmApplication, storedConfiguration );
        }

        try
        {
            outputConfigurationFile( storedConfiguration, pwmApplication, backupRotations, backupDirectory );
        }
        finally
        {
            saveInProgress = false;
        }
    }

    private void auditModifiedSettings( final PwmApplication pwmApplication, final StoredConfiguration newConfig )
    {
        final Instant startTime = Instant.now();

        final StoredConfiguration oldConfig = pwmApplication.getConfig().getStoredConfiguration();
        final List<StoredConfigKey> changedKeys = StoredConfigurationUtil.changedValues( newConfig, oldConfig ).stream()
                .filter( key -> key.isRecordType( StoredConfigKey.RecordType.SETTING ) || key.isRecordType( StoredConfigKey.RecordType.LOCALE_BUNDLE ) )
                .sorted()
                .collect( Collectors.toUnmodifiableList() );

        int changeCount = 0;

        for ( final StoredConfigKey key : changedKeys )
        {
            final Optional<ValueMetaData> valueMetaData = newConfig.readMetaData( key );
            final UserIdentity userIdentity = valueMetaData.map( ValueMetaData::userIdentity ).orElse( null );

            final Optional<StoredValue> storedValue = newConfig.readStoredValue( key );
            final StringBuilder modifyMessage = new StringBuilder();
            modifyMessage.append( "configuration record '" ).append( key.getLabel( PwmConstants.DEFAULT_LOCALE ) ).append( "' has been " );

            modifyMessage.append( storedValue.map( value -> "modified, new value: " + value.toDebugString( PwmConstants.DEFAULT_LOCALE ) )
                    .orElse( "removed" ) );

            LOGGER.trace( sessionLabel, () -> "sending audit notice: " + modifyMessage );

            AuditServiceClient.submit( pwmApplication, sessionLabel, AuditRecordFactory.make( sessionLabel, pwmApplication ).createUserAuditRecord(
                    AuditEvent.MODIFY_CONFIGURATION,
                    userIdentity,
                    sessionLabel,
                    modifyMessage.toString() ) );

            changeCount++;
        }

        final int finalChangeCount = changeCount;
        LOGGER.debug( sessionLabel, () -> "sent " + finalChangeCount + " audit notifications about changed settings", TimeDuration.fromCurrent( startTime ) );
    }

    private void outputConfigurationFile(
            final StoredConfiguration storedConfiguration,
            final PwmApplication pwmApplication,
            final int backupRotations,
            final Path backupDirectory
    )
            throws IOException, PwmUnrecoverableException
    {
        final Instant saveFileStartTime = Instant.now();
        final Path tempWriteFile = FileSystemUtility.addFilenameSuffix( configFile, ".new" );
        LOGGER.info( sessionLabel, () -> "beginning write to configuration file " + tempWriteFile );
        saveInProgress = true;

        try ( OutputStream fileOutputStream = Files.newOutputStream( tempWriteFile ) )
        {
            StoredConfigurationFactory.output( storedConfiguration, fileOutputStream );
        }

        LOGGER.info( sessionLabel, () -> "saved configuration", TimeDuration.fromCurrent( saveFileStartTime ) );
        if ( pwmApplication != null )
        {
            final String actualChecksum = StoredConfigurationUtil.valueHash( storedConfiguration );
            pwmApplication.writeAppAttribute( AppAttribute.CONFIG_HASH, actualChecksum );
        }

        LOGGER.trace( sessionLabel, () -> "renaming file " + tempWriteFile + " to " + configFile );
        try
        {
            Files.move( tempWriteFile, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE );
        }
        catch ( final Exception e )
        {
            final String errorMsg = "unable to rename temporary save file from " + tempWriteFile
                    + " to " + configFile + "; error: " + e.getMessage();
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg ) );
        }

        if ( backupDirectory != null && configFile != null && configFile.getFileName() != null )
        {
            final String configFileName = configFile.getFileName().toString();
            final Path backupFile = backupDirectory.resolve( configFileName + "-backup" );
            FileSystemUtility.rotateBackups( backupFile, backupRotations );
            try ( OutputStream fileOutputStream = Files.newOutputStream( backupFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING ) )
            {
                StoredConfigurationFactory.output( storedConfiguration, fileOutputStream );
            }
        }
    }

    public boolean modifiedSinceLoad( )
            throws IOException
    {
        final String currentChecksum = readFileChecksum( configFile );
        return !currentChecksum.equals( configFileChecksum );
    }

    private static String readFileChecksum( final Path file )
            throws IOException
    {
        if ( !Files.exists( file ) )
        {
            return "";
        }

        return Files.getLastModifiedTime( file ) + "+" + Files.size( file );
    }

    public ErrorInformation getConfigFileError( )
    {
        return configFileError;
    }

    public Path getConfigFile( )
    {
        return configFile;
    }

    public boolean isSaveInProgress( )
    {
        return saveInProgress;
    }
}


