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

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.config.Configuration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.JsonUtil;
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
    private StoredConfigurationImpl storedConfiguration;
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
        catch ( PwmUnrecoverableException e )
        {
            this.configFileError = e.getErrorInformation();
            LOGGER.warn( "error reading configuration file: " + e.getMessage() );
        }

        if ( storedConfiguration == null )
        {
            this.storedConfiguration = StoredConfigurationImpl.newStoredConfiguration();
        }

        LOGGER.debug( () -> "configuration mode: " + configMode );
    }

    public PwmApplicationMode getConfigMode( )
    {
        return configMode;
    }

    public StoredConfigurationImpl getStoredConfiguration( )
    {
        return storedConfiguration;
    }

    public Configuration getConfiguration( ) throws PwmUnrecoverableException
    {
        if ( configuration == null )
        {
            final StoredConfigurationImpl newStoredConfig = this.storedConfiguration == null
                    ? StoredConfigurationImpl.newStoredConfiguration()
                    : this.storedConfiguration;
            configuration = new Configuration( newStoredConfig );
            if ( storedConfiguration != null )
            {
                storedConfiguration.lock();
            }
        }
        return configuration;
    }

    private StoredConfigurationImpl readStoredConfig( ) throws PwmUnrecoverableException
    {
        LOGGER.debug( () -> "loading configuration file: " + configFile );

        if ( !configFile.exists() )
        {
            LOGGER.warn( "configuration file '" + configFile.getAbsolutePath() + "' does not exist" );
            return null;
        }

        final Instant startTime = Instant.now();
        final InputStream theFileData;
        try
        {
            theFileData = Files.newInputStream( configFile.toPath() );
        }
        catch ( Exception e )
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

        final StoredConfigurationImpl storedConfiguration;
        try
        {
            storedConfiguration = StoredConfigurationImpl.fromXml( theFileData );
            //restoredConfiguration = (new NGStoredConfigurationFactory()).fromXml(theFileData);
        }
        catch ( PwmUnrecoverableException e )
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

        final List<String> validationErrorMsgs = storedConfiguration.validateValues();
        if ( validationErrorMsgs != null && !validationErrorMsgs.isEmpty() )
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

        final String configIsEditable = storedConfiguration.readConfigProperty( ConfigurationProperty.CONFIG_IS_EDITABLE );
        if ( PwmConstants.TRIAL_MODE || ( configIsEditable != null && "true".equalsIgnoreCase( configIsEditable ) ) )
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
            final StoredConfigurationImpl storedConfiguration,
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
            String epochStrValue = storedConfiguration.readConfigProperty( ConfigurationProperty.CONFIG_EPOCH );
            try
            {
                final BigInteger epochValue = epochStrValue == null || epochStrValue.length() < 0 ? BigInteger.ZERO : new BigInteger( epochStrValue );
                epochStrValue = epochValue.add( BigInteger.ONE ).toString();
            }
            catch ( Exception e )
            {
                LOGGER.error( sessionLabel, "error trying to parse previous config epoch property: " + e.getMessage() );
                epochStrValue = "0";
            }
            storedConfiguration.writeConfigProperty( ConfigurationProperty.CONFIG_EPOCH, epochStrValue );
        }

        if ( backupDirectory != null && !backupDirectory.exists() )
        {
            if ( !backupDirectory.mkdirs() )
            {
                throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_INTERNAL,
                        "unable to create backup directory structure '" + backupDirectory.toString() + "'" ) );
            }
        }

        try
        {
            final File tempWriteFile = new File( configFile.getAbsoluteFile() + ".new" );
            LOGGER.info( sessionLabel, () -> "beginning write to configuration file " + tempWriteFile );
            saveInProgress = true;

            try ( FileOutputStream fileOutputStream = new FileOutputStream( tempWriteFile, false ) )
            {
                storedConfiguration.toXml( fileOutputStream );
            }

            LOGGER.info( () -> "saved configuration " + JsonUtil.serialize( storedConfiguration.toJsonDebugObject() ) );
            if ( pwmApplication != null )
            {
                final String actualChecksum = storedConfiguration.settingChecksum();
                pwmApplication.writeAppAttribute( PwmApplication.AppAttribute.CONFIG_HASH, actualChecksum );
            }

            LOGGER.trace( () -> "renaming file " + tempWriteFile.getAbsolutePath() + " to " + configFile.getAbsolutePath() );
            try
            {
                Files.move( tempWriteFile.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE );
            }
            catch ( Exception e )
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
                    storedConfiguration.toXml( fileOutputStream );
                }
            }
        }
        finally
        {
            saveInProgress = false;
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


