/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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
import password.pwm.bean.SessionLabel;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.event.AuditEvent;
import password.pwm.event.SystemAuditRecord;
import password.pwm.util.Helper;
import password.pwm.util.logging.PwmLogger;

import java.io.*;
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

    private final File configFile;
    private final String configFileChecksum;
    private Configuration configuration;
    private StoredConfiguration storedConfiguration;
    private ErrorInformation configFileError;

    private Date configurationReadTime;

    private PwmApplication.MODE configMode = PwmApplication.MODE.NEW;

    private volatile boolean saveInProgress;

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
            this.storedConfiguration = StoredConfiguration.newStoredConfiguration();
        }

        LOGGER.debug("configuration mode: " + configMode);
    }

    public PwmApplication.MODE getConfigMode() {
        return configMode;
    }

    public StoredConfiguration getStoredConfiguration() {
        return storedConfiguration;
    }

    public Configuration getConfiguration() {
        if (configuration == null) {
            configuration = new Configuration(this.storedConfiguration == null ? StoredConfiguration.newStoredConfiguration() : this.storedConfiguration);
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

        final InputStream theFileData;
        try {
            theFileData = new FileInputStream(configFile);
        } catch (Exception e) {
            final String errorMsg = "unable to read configuration file: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,null,new String[]{errorMsg});
            this.configMode = PwmApplication.MODE.ERROR;
            throw new PwmUnrecoverableException(errorInformation);
        }

        final StoredConfiguration storedConfiguration;
        try {
            storedConfiguration = StoredConfiguration.fromXml(theFileData);
        } catch (PwmUnrecoverableException e) {
            final String errorMsg = "unable to parse configuration file: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,null,new String[]{errorMsg});
            this.configMode = PwmApplication.MODE.ERROR;
            throw new PwmUnrecoverableException(errorInformation);
        }

        final List<String> validationErrorMsgs = storedConfiguration.validateValues();
        if (validationErrorMsgs != null && !validationErrorMsgs.isEmpty()) {
            final String errorMsg = "value error in config file, please investigate: " + validationErrorMsgs.get(0);
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,null,new String[]{errorMsg});
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

    public void saveConfiguration(
            final StoredConfiguration storedConfiguration,
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel
    )
            throws IOException, PwmUnrecoverableException, PwmOperationalException
    {
        File backupDirectory = null;
        int backupRotations = 0;
        if (pwmApplication != null) {
            final Configuration configuration = new Configuration(storedConfiguration);
            final String backupDirSetting = configuration.readAppProperty(AppProperty.BACKUP_LOCATION);
            if (backupDirSetting != null && backupDirSetting.length() > 0) {
                final File pwmPath = pwmApplication.getApplicationPath();
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
                LOGGER.error(sessionLabel, "error trying to parse previous config epoch property: " + e.getMessage());
                epochStrValue = "0";
            }
            storedConfiguration.writeConfigProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_CONFIG_EPOCH, epochStrValue);
        }

        if (backupDirectory != null && !backupDirectory.exists()) {
            if (!backupDirectory.mkdirs()) {
                throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"unable to create backup directory structure '" + backupDirectory.toString() + "'"));
            }
        }

        try {
            LOGGER.info(sessionLabel, "beginning write to configuration file " + configFile.getAbsoluteFile());
            saveInProgress = true;

            storedConfiguration.toXml(new FileOutputStream(configFile, false));
            LOGGER.info("saved configuration " + storedConfiguration.toString(true));
            if (pwmApplication != null) {
                final String actualChecksum = storedConfiguration.settingChecksum();
                pwmApplication.writeAppAttribute(PwmApplication.AppAttribute.CONFIG_HASH, actualChecksum);
            }

            if (pwmApplication != null && pwmApplication.getAuditManager() != null) {
                String modifyMessage = storedConfiguration.changeLogAsDebugString(PwmConstants.DEFAULT_LOCALE, false);
                if (sessionLabel != null && sessionLabel.getUserIdentity() != null) {
                    modifyMessage += " by " + sessionLabel.getUserIdentity().toDisplayString();
                }
                pwmApplication.getAuditManager().submit(SystemAuditRecord.create(
                        AuditEvent.MODIFY_CONFIGURATION,
                        modifyMessage,
                        pwmApplication.getInstanceID()
                ));
            }

            if (backupDirectory != null) {
                final String configFileName = configFile.getName();
                final String backupFilePath = backupDirectory.getAbsolutePath() + File.separatorChar + configFileName + "-backup";
                final File backupFile = new File(backupFilePath);
                Helper.rotateBackups(backupFile, backupRotations);
                storedConfiguration.toXml(new FileOutputStream(backupFile, false));
            }
        } finally {
            saveInProgress = false;
        }
    }

    public boolean modifiedSinceLoad() {
        final String currentChecksum = readFileChecksum(configFile);
        return !currentChecksum.equals(configFileChecksum);
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

    public boolean isSaveInProgress() {
        return saveInProgress;
    }
}


