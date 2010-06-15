/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

import password.pwm.ContextManager;
import password.pwm.util.PwmLogger;

import java.io.*;

/**
 * Read the PWM configuration.
 *
 * @author Jason D. Rivard
 */
public class ConfigReader {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ConfigReader.class.getName());
    private static final int MAX_FILE_CHARS = 100 * 1024;

    private final File configFile;
    private final String configFileChecksum;
    private final StoredConfiguration storedConfiguration;

    public ConfigReader(final File configFile) {
        this.configFile = configFile;
        this.configFileChecksum = readFileChecksum(configFile);
        this.storedConfiguration = readStoredConfig();
        final Configuration.MODE mode = determineConfigMode(storedConfiguration);
        LOGGER.debug("detected configuration mode: " + mode);
    }

    private static Configuration.MODE determineConfigMode(final StoredConfiguration storedConfig) {
        if (storedConfig == null) {
           return Configuration.MODE.NEW;
        }

        final String configIsEditable = storedConfig.readProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_IS_EDITABLE);
        if (configIsEditable != null && configIsEditable.equalsIgnoreCase("true")) {
            return Configuration.MODE.CONFIGURATION;
        }

        return Configuration.MODE.RUNNING;
    }

    public StoredConfiguration getStoredConfiguration() {
        return storedConfiguration;
    }

    public Configuration getConfiguration() throws Exception {
        final Configuration.MODE mode = determineConfigMode(this.storedConfiguration);

        if (mode == Configuration.MODE.NEW) {
            return new Configuration(StoredConfiguration.getDefaultConfiguration(),mode);
        } else {
            return new Configuration(this.storedConfiguration,mode);
        }
    }

    private StoredConfiguration readStoredConfig()  {
        LOGGER.debug("loading configuration file: " + configFile);

        final String theFileData;
        try {
            theFileData = readFileAsString(configFile);
        } catch (Exception e) {
            LOGGER.warn("unable to read configuration file: " + e.getMessage());
            return null;
        }

        final StoredConfiguration storedConfiguration;
        try {
            storedConfiguration = StoredConfiguration.fromXml(theFileData);
        } catch (Exception e) {
            LOGGER.warn("unable to parse configuration file: " + e.getMessage());
            return null;
        }

        for (final String errorString : storedConfiguration.validateValues()) {
            LOGGER.error("error in config file, please investigate: " + errorString);
        }

        try {
            final String storedChecksum = storedConfiguration.readProperty(StoredConfiguration.PROPERTY_KEY_SETTING_CHECKSUM);
            final String actualChecksum = storedConfiguration.settingChecksum();
            if (!actualChecksum.equals(storedChecksum)) {
                LOGGER.warn("configuration settings have been modified after the file was generated");
            }
        } catch (Exception e) {
            LOGGER.warn("unable to evaluate checksum file: " + e.getMessage());
        }

        return storedConfiguration;
    }

    public void saveConfiguration(final StoredConfiguration storedConfiguration, final ContextManager contextManager)
            throws IOException
    {
        if (contextManager.getConfig().getConfigMode() == Configuration.MODE.RUNNING) {
            throw new IllegalStateException("running config mode does now allow saving of configuration");
        }

        final String xmlBlob = storedConfiguration.toXml();
        //configFile.delete();
        final FileWriter fileWriter = new FileWriter(configFile, false);
        fileWriter.write(xmlBlob);
        fileWriter.close();
        LOGGER.info("saved configuration " + storedConfiguration.toString());
    }

    public boolean configHasChanged() {
        final String currentChecksum = readFileChecksum(configFile);
        return !currentChecksum.equals(configFileChecksum);
    }

    private static String readFileAsString(final File filePath)
            throws java.io.IOException
    {
        final StringBuffer fileData = new StringBuffer(1000);
        final BufferedReader reader = new BufferedReader(
                new FileReader(filePath));
        char[] buf = new char[1024];
        int numRead;
        int charsRead = 0;
        while((numRead=reader.read(buf)) != -1 && (charsRead < MAX_FILE_CHARS)){
            final String readData = String.valueOf(buf, 0, numRead);
            fileData.append(readData);
            buf = new char[1024];
            charsRead += numRead;
        }
        reader.close();
        return fileData.toString();
    }

    private static String readFileChecksum(final File file) {
        if (!file.exists()) {
            return "";
        }

        return String.valueOf(file.lastModified());
    }

}


