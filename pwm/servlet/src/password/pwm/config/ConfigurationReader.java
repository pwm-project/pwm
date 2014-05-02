/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.config;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.event.AuditEvent;
import password.pwm.event.SystemAuditRecord;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Date;
import java.util.List;

/**
 * Read the PWM configuration.
 *
 * @author Jason D. Rivard
 */
public class ConfigurationReader {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ConfigurationReader.class.getName());
    private static final String CONFIG_FILE_CHARSET = "UTF-8";

    private final File configFile;
    private final String configFileChecksum;
    private Configuration configuration;
    private StoredConfiguration storedConfiguration;
    private ErrorInformation configFileError;

    private Date configurationReadTime;

    private PwmApplication.MODE configMode = PwmApplication.MODE.NEW;

    public ConfigurationReader(final File configFile) {
        this.configFile = configFile;

        this.configFileChecksum = readFileChecksum(configFile);
        try {
            this.storedConfiguration = readStoredConfig();
            this.configFileError = null;
        } catch (PwmUnrecoverableException e) {
            this.configFileError = e.getErrorInformation();
            LOGGER.warn("error reading configuration file: " + e.getMessage());
        }

        if (storedConfiguration == null) {
            this.storedConfiguration = StoredConfiguration.getDefaultConfiguration();
        }

        LOGGER.debug("configuration mode: " + configMode);

        if (modifiedSinceSave()) {
            LOGGER.warn("configuration settings have been modified since the file was saved using the Configuration Editor");
        }
    }

    public PwmApplication.MODE getConfigMode() {
        return configMode;
    }

    public StoredConfiguration getStoredConfiguration() {
        return storedConfiguration;
    }

    public Configuration getConfiguration() {
        if (configuration == null) {
            configuration = new Configuration(this.storedConfiguration == null ? StoredConfiguration.getDefaultConfiguration() : this.storedConfiguration);
            storedConfiguration.lock();
        }
        return configuration;
    }

    private StoredConfiguration readStoredConfig() throws PwmUnrecoverableException {
        LOGGER.debug("loading configuration file: " + configFile);

        configurationReadTime = new Date();

        if (!configFile.exists()) {
            LOGGER.warn("configuration file '" + configFile.getAbsolutePath() + "' does not exist");
            return null;
        }

        final String theFileData;
        try {
            theFileData = Helper.readFileAsString(configFile, PwmConstants.MAX_CONFIG_FILE_CHARS, CONFIG_FILE_CHARSET);
        } catch (Exception e) {
            final String errorMsg = "unable to read configuration file: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg);
            this.configMode = PwmApplication.MODE.ERROR;
            throw new PwmUnrecoverableException(errorInformation);
        }

        final StoredConfiguration storedConfiguration;
        try {
            storedConfiguration = StoredConfiguration.fromXml(theFileData);
        } catch (PwmUnrecoverableException e) {
            final String errorMsg = "unable to parse configuration file: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg);
            this.configMode = PwmApplication.MODE.ERROR;
            throw new PwmUnrecoverableException(errorInformation);
        }

        final List<String> validationErrorMsgs = storedConfiguration.validateValues();
        if (validationErrorMsgs != null && !validationErrorMsgs.isEmpty()) {
            final String errorMsg = "value error in config file, please investigate: " + validationErrorMsgs.get(0);
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg);
            this.configMode = PwmApplication.MODE.ERROR;
            throw new PwmUnrecoverableException(errorInformation);
        }

        final String configIsEditable = storedConfiguration.readConfigProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_CONFIG_IS_EDITABLE);
        if (PwmConstants.TRIAL_MODE || (configIsEditable != null && configIsEditable.equalsIgnoreCase("true"))) {
            this.configMode = PwmApplication.MODE.CONFIGURATION;
        } else {
            this.configMode = PwmApplication.MODE.RUNNING;
        }

        return storedConfiguration;
    }

    public void saveConfiguration(final StoredConfiguration storedConfiguration, final PwmApplication pwmApplication)
            throws IOException, PwmUnrecoverableException, PwmOperationalException
    {
        File backupDirectory = null;
        int backupRotations = 0;
        if (pwmApplication != null) {
            final Configuration configuration = new Configuration(storedConfiguration);
            final String backupDirSetting = configuration.readAppProperty(AppProperty.BACKUP_LOCATION);
            if (backupDirSetting != null && backupDirSetting.length() > 0) {
                final File pwmPath = pwmApplication.getPwmApplicationPath();
                backupDirectory = Helper.figureFilepath(backupDirSetting, pwmPath);
            }
            backupRotations = Integer.parseInt(configuration.readAppProperty(AppProperty.BACKUP_CONFIG_COUNT));
        }


        { // increment the config epoch
            String epochStrValue = storedConfiguration.readConfigProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_CONFIG_EPOCH);
            try {
                final BigInteger epochValue = epochStrValue == null || epochStrValue.length() < 0 ? BigInteger.ZERO : new BigInteger(epochStrValue);
                epochStrValue = epochValue.add(BigInteger.ONE).toString();
            } catch (Exception e) {
                LOGGER.error("error trying to parse previous config epoch property: " + e.getMessage());
                epochStrValue = "0";
            }
            storedConfiguration.writeConfigProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_CONFIG_EPOCH, epochStrValue);
        }

        if (backupDirectory != null && !backupDirectory.exists()) {
            if (!backupDirectory.mkdirs()) {
                throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"unable to create backup directory structure '" + backupDirectory.toString() + "'"));
            }
        }

        LOGGER.trace("generating xml string configuration blob");
        final String configXmlBlob = storedConfiguration.toXml();
        LOGGER.info("beginning write to configuration file " + configFile.getAbsoluteFile());
        Helper.writeFileAsString(configFile, configXmlBlob, CONFIG_FILE_CHARSET);
        LOGGER.info("saved configuration " + storedConfiguration.toString());
        if (pwmApplication != null) {
            final String actualChecksum = storedConfiguration.settingChecksum();
            pwmApplication.writeAppAttribute(PwmApplication.AppAttribute.CONFIG_HASH, actualChecksum);
        }

        if (pwmApplication != null && pwmApplication.getAuditManager() != null) {
            final String modifyMessage = storedConfiguration.changeLogAsDebugString(PwmConstants.DEFAULT_LOCALE, false);
            pwmApplication.getAuditManager().submit(new SystemAuditRecord(
                    AuditEvent.MODIFY_CONFIGURATION,
                    new Date(),
                    modifyMessage,
                    pwmApplication.getInstanceID()
            ));
        }

        if (backupDirectory != null) {
            final String configFileName = configFile.getName();
            final String backupFilePath = backupDirectory.getAbsolutePath() + File.separatorChar + configFileName + "-backup";
            final File backupFile = new File(backupFilePath);
            Helper.rotateBackups(backupFile, backupRotations);
            Helper.writeFileAsString(backupFile, configXmlBlob, CONFIG_FILE_CHARSET);
        }
    }

    public boolean modifiedSinceLoad() {
        final String currentChecksum = readFileChecksum(configFile);
        return !currentChecksum.equals(configFileChecksum);
    }

    public boolean modifiedSinceSave() {
        if (this.getConfigMode() == PwmApplication.MODE.NEW) {
            return false;
        }

        try {
            final String storedChecksum = storedConfiguration.readConfigProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_SETTING_CHECKSUM);
            final String actualChecksum = storedConfiguration.settingChecksum();
            return !actualChecksum.equals(storedChecksum);
        } catch (Exception e) {
            LOGGER.warn("unable to evaluate checksum file: " + e.getMessage());
        }
        return true;
    }

    private static String readFileChecksum(final File file) {
        if (!file.exists()) {
            return "";
        }

        return String.valueOf(file.lastModified() + String.valueOf(file.length()));
    }

    public Date getConfigurationReadTime() {
        return configurationReadTime;
    }

    public ErrorInformation getConfigFileError() {
        return configFileError;
    }

    public File getConfigFile() {
        return configFile;
    }

}


