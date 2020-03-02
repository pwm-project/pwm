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

package password.pwm.config.stored;

import password.pwm.AppAttribute;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.StoredValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Read the PWM configuration.
 *
 * @author Jason D. Rivard
 */
public class ConfigurationReader
{
    private static final PwmLogger LOGGER = PwmLogger.getLogger( ConfigurationReader.class.getName() );

    private final File configFile;
    private final String configFileChecksum;
    private Configuration configuration;
    private StoredConfiguration storedConfiguration;
    private ErrorInformation configFileError;


    private PwmApplicationMode configMode = PwmApplicationMode.NEW;

    private volatile boolean saveInProgress;

    public ConfigurationReader( final File configFile ) throws PwmUnrecoverableException
    {
        this.configFile = configFile;

        this.configFileChecksum = readFileChecksum( configFile );
        try
        {
            this.storedConfiguration = readStoredConfig();
            this.configFileError = null;
        }
        catch ( final PwmUnrecoverableException e )
        {
            this.configFileError = e.getErrorInformation();
            LOGGER.warn( () -> "error reading configuration file: " + e.getMessage() );
        }

        if ( storedConfiguration == null )
        {
            this.storedConfiguration = StoredConfigurationFactory.newConfig();
        }

        LOGGER.debug( () -> "configuration mode: " + configMode );
    }

    public PwmApplicationMode getConfigMode( )
    {
        return configMode;
    }

    public StoredConfiguration getStoredConfiguration( )
    {
        return storedConfiguration;
    }

    public Configuration getConfiguration( ) throws PwmUnrecoverableException
    {
        if ( configuration == null )
        {
            final StoredConfiguration newStoredConfig = this.storedConfiguration == null
                    ? StoredConfigurationFactory.newConfig()
                    : this.storedConfiguration;
            configuration = new Configuration( newStoredConfig );
        }
        return configuration;
    }

    private StoredConfiguration readStoredConfig( ) throws PwmUnrecoverableException
    {
        LOGGER.debug( () -> "loading configuration file: " + configFile );

        if ( !configFile.exists() )
        {
            LOGGER.warn( () -> "configuration file '" + configFile.getAbsolutePath() + "' does not exist" );
            return null;
        }

        final Instant startTime = Instant.now();

        /*
        try
        {
            final InputStream theFileData = Files.newInputStream( configFile.toPath() );
            final StoredConfiguration storedConfiguration = StoredConfigurationFactory.fromXml( theFileData );

            System.out.println( TimeDuration.compactFromCurrent( startTime ) );


            //final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final FileOutputStream fos = new FileOutputStream( new File( "/tmp/NEWCFG" ) );
            StoredConfigurationFactory.toXml( storedConfiguration, fos );

            //System.out.println( new String( baos.toByteArray(), "UTF-8" )  );
        }
        catch ( final Exception e )
        {
            e.printStackTrace(  );
        }
        */

        final InputStream theFileData;
        try
        {
            theFileData = Files.newInputStream( configFile.toPath() );
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

        final StoredConfiguration storedConfiguration;
        try
        {
            storedConfiguration = StoredConfigurationFactory.fromXml( theFileData );
        }
        catch ( final PwmUnrecoverableException e )
        {
            final String errorMsg = "unable to parse configuration file: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                    {
                            errorMsg,
                    }
            );
            this.configMode = PwmApplicationMode.ERROR;
            e.printStackTrace(  );
            throw new PwmUnrecoverableException( errorInformation );
        }

        final List<String> validationErrorMsgs = StoredConfigurationUtil.validateValues( storedConfiguration );
        if ( !JavaHelper.isEmpty( validationErrorMsgs ) )
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

        final Optional<String> configIsEditable = storedConfiguration.readConfigProperty( ConfigurationProperty.CONFIG_IS_EDITABLE );
        if ( PwmConstants.TRIAL_MODE || ( configIsEditable.isPresent() && "true".equalsIgnoreCase( configIsEditable.get() ) ) )
        {
            this.configMode = PwmApplicationMode.CONFIGURATION;
        }
        else
        {
            this.configMode = PwmApplicationMode.RUNNING;
        }

        final String fileSize = StringUtil.formatDiskSize( configFile.length() );
        final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );
        LOGGER.debug( () -> "configuration reading/parsing of " + fileSize + " complete in " + timeDuration.asLongString() );



        return storedConfiguration;
    }

    public void saveConfiguration(
            final StoredConfiguration storedConfiguration,
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel
    )
            throws IOException, PwmUnrecoverableException, PwmOperationalException
    {
        File backupDirectory = null;
        int backupRotations = 0;
        if ( pwmApplication != null )
        {
            final Configuration configuration = new Configuration( storedConfiguration );
            final String backupDirSetting = configuration.readAppProperty( AppProperty.BACKUP_LOCATION );
            if ( backupDirSetting != null && backupDirSetting.length() > 0 )
            {
                final File pwmPath = pwmApplication.getPwmEnvironment().getApplicationPath();
                backupDirectory = FileSystemUtility.figureFilepath( backupDirSetting, pwmPath );
            }
            backupRotations = Integer.parseInt( configuration.readAppProperty( AppProperty.BACKUP_CONFIG_COUNT ) );
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

        if ( backupDirectory != null && !backupDirectory.exists() )
        {
            if ( !backupDirectory.mkdirs() )
            {
                throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_INTERNAL,
                        "unable to create backup directory structure '" + backupDirectory.toString() + "'" ) );
            }
        }

        if ( pwmApplication != null && pwmApplication.getAuditManager() != null )
        {
            auditModifiedSettings( pwmApplication, storedConfiguration, sessionLabel );
        }

        try
        {
            outputConfigurationFile( storedConfiguration, pwmApplication, sessionLabel, backupRotations, backupDirectory );
        }
        finally
        {
            saveInProgress = false;
        }
    }

    private static void auditModifiedSettings( final PwmApplication pwmApplication, final StoredConfiguration newConfig, final SessionLabel sessionLabel )
            throws PwmUnrecoverableException
    {
        final Set<StoredConfigItemKey> changedKeys = StoredConfigurationUtil.changedValues( newConfig, pwmApplication.getConfig().getStoredConfiguration() );

        for ( final StoredConfigItemKey key : changedKeys )
        {
            if ( key.getRecordType() == StoredConfigItemKey.RecordType.SETTING
                    || key.getRecordType() == StoredConfigItemKey.RecordType.LOCALE_BUNDLE )
            {
                final Optional<StoredValue> storedValue = newConfig.readStoredValue( key );
                if ( storedValue.isPresent() )
                {
                    final Optional<ValueMetaData> valueMetaData = newConfig.readMetaData( key );
                    final UserIdentity userIdentity = valueMetaData.map( ValueMetaData::getUserIdentity ).orElse( null );
                    final String modifyMessage = "configuration record '" + key.getLabel( PwmConstants.DEFAULT_LOCALE )
                            + "' has been modified, new value: " + storedValue.get().toDebugString( PwmConstants.DEFAULT_LOCALE );
                    pwmApplication.getAuditManager().submit( new AuditRecordFactory( pwmApplication ).createUserAuditRecord(
                            AuditEvent.MODIFY_CONFIGURATION,
                            userIdentity,
                            sessionLabel,
                            modifyMessage
                    ) );
                }
            }
        }
    }

    private void outputConfigurationFile(
            final StoredConfiguration storedConfiguration,
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final int backupRotations,
            final File backupDirectory
    )
            throws IOException, PwmUnrecoverableException
    {
        final Instant saveFileStartTime = Instant.now();
        final File tempWriteFile = new File( configFile.getAbsoluteFile() + ".new" );
        LOGGER.info( sessionLabel, () -> "beginning write to configuration file " + tempWriteFile );
        saveInProgress = true;

        try ( FileOutputStream fileOutputStream = new FileOutputStream( tempWriteFile, false ) )
        {
            StoredConfigurationFactory.toXml( storedConfiguration, fileOutputStream );
        }

        LOGGER.info( () -> "saved configuration in " + TimeDuration.compactFromCurrent( saveFileStartTime ) );
        if ( pwmApplication != null )
        {
            final String actualChecksum = storedConfiguration.valueHash();
            pwmApplication.writeAppAttribute( AppAttribute.CONFIG_HASH, actualChecksum );
        }

        LOGGER.trace( () -> "renaming file " + tempWriteFile.getAbsolutePath() + " to " + configFile.getAbsolutePath() );
        try
        {
            Files.move( tempWriteFile.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE );
        }
        catch ( final Exception e )
        {
            final String errorMsg = "unable to rename temporary save file from " + tempWriteFile.getAbsolutePath()
                    + " to " + configFile.getAbsolutePath() + "; error: " + e.getMessage();
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg ) );
        }

        if ( backupDirectory != null )
        {
            final String configFileName = configFile.getName();
            final String backupFilePath = backupDirectory.getAbsolutePath() + File.separatorChar + configFileName + "-backup";
            final File backupFile = new File( backupFilePath );
            FileSystemUtility.rotateBackups( backupFile, backupRotations );
            try ( FileOutputStream fileOutputStream = new FileOutputStream( backupFile, false ) )
            {
                StoredConfigurationFactory.toXml( storedConfiguration, fileOutputStream );
            }
        }
    }

    public boolean modifiedSinceLoad( )
    {
        final String currentChecksum = readFileChecksum( configFile );
        return !currentChecksum.equals( configFileChecksum );
    }

    private static String readFileChecksum( final File file )
    {
        if ( !file.exists() )
        {
            return "";
        }

        return String.valueOf( file.lastModified() + String.valueOf( file.length() ) );
    }

    public ErrorInformation getConfigFileError( )
    {
        return configFileError;
    }

    public File getConfigFile( )
    {
        return configFile;
    }

    public boolean isSaveInProgress( )
    {
        return saveInProgress;
    }
}


