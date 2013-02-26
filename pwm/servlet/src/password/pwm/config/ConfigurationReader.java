/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
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
    private final Configuration configuration;
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
            this.storedConfiguration = StoredConfiguration.getDefaultConfiguration();
            this.configFileError = e.getErrorInformation();
            LOGGER.warn("error reading configuration file: " + e.getMessage());
        }

        LOGGER.debug("configuration mode: " + configMode);

        if (modifiedSincePWMSave()) {
            LOGGER.warn("configuration settings have been modified since the file was saved by pwm");
        }

        configuration = new Configuration(this.storedConfiguration == null ? StoredConfiguration.getDefaultConfiguration() : this.storedConfiguration);
    }

    public PwmApplication.MODE getConfigMode() {
        return configMode;
    }

    public StoredConfiguration getStoredConfiguration() {
        return storedConfiguration;
    }

    public Configuration getConfiguration() {
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

        final String configIsEditable = storedConfiguration.readProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_IS_EDITABLE);
        if (configIsEditable != null && configIsEditable.equalsIgnoreCase("true")) {
            this.configMode = PwmApplication.MODE.CONFIGURATION;
        } else {
            this.configMode = PwmApplication.MODE.RUNNING;
        }

        storedConfiguration.lock();
        return storedConfiguration;
    }

    public void saveConfiguration(final StoredConfiguration storedConfiguration)
            throws IOException, PwmUnrecoverableException
    {

        { // increment the config epoch
            String epochStrValue = storedConfiguration.readProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_EPOCH);
            try {
                final BigInteger epochValue = epochStrValue == null || epochStrValue.length() < 0 ? BigInteger.ZERO : new BigInteger(epochStrValue);
                epochStrValue = epochValue.add(BigInteger.ONE).toString();
            } catch (Exception e) {
                LOGGER.error("error trying to parse previous config epoch property: " + e.getMessage());
                epochStrValue = "0";
            }
            storedConfiguration.writeProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_EPOCH, epochStrValue);
        }

        rotateBackups(configFile);
        Helper.writeFileAsString(configFile, storedConfiguration.toXml(), CONFIG_FILE_CHARSET);
        LOGGER.info("saved configuration " + storedConfiguration.toString());
    }

    private void rotateBackups(final File configFile) {
        final int maxRotations = PwmConstants.CONFIG_BACKUP_ROTATIONS;
        if (maxRotations < 1) {
            return;
        }

        for (int i = maxRotations; i >= 0; i--) {
            final File loopFile = new File(configFile + "-backup-" + i + ".xml");
            final File destinationFileName = new File(configFile + "-backup-" + (i+1) + ".xml");

            if (i == maxRotations) {
                if (loopFile.exists()) {
                    if (loopFile.delete()) {
                        LOGGER.debug("deleted old backup file: " + loopFile.getAbsolutePath());
                    }
                }
            } else if (i == 0) {
                if (configFile.exists()) {
                    if (configFile.renameTo(destinationFileName)) {
                        LOGGER.debug("current config file " + configFile.getAbsolutePath() + " renamed to " + destinationFileName.getAbsolutePath());
                    }
                }
            } else {
                if (loopFile.renameTo(destinationFileName)) {
                    LOGGER.debug("backup file " + loopFile.getAbsolutePath() + " renamed to " + destinationFileName.getAbsolutePath());
                }
            }
        }
    }

    public boolean modifiedSinceLoad() {
        final String currentChecksum = readFileChecksum(configFile);
        return !currentChecksum.equals(configFileChecksum);
    }

    public boolean modifiedSincePWMSave() {
        if (this.getConfigMode() == PwmApplication.MODE.NEW) {
            return false;
        }

        try {
            final String storedChecksum = storedConfiguration.readProperty(StoredConfiguration.PROPERTY_KEY_SETTING_CHECKSUM);
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

    public int getConfigurationEpoch() {
        try {
            return Integer.parseInt(storedConfiguration.readProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_EPOCH));
        } catch (Exception e) {
            return 0;
        }
    }

    public ErrorInformation getConfigFileError() {
        return configFileError;
    }

    public File getConfigFile() {
        return configFile;
    }

}


