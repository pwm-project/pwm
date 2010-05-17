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


    public static Configuration loadConfiguration(final File configFile) throws Exception {
        final String theFileData = readFileAsString(configFile);
        final StoredConfiguration storedConfiguration = StoredConfiguration.fromXml(theFileData, true);
        storedConfiguration.lock();
        return new Configuration(storedConfiguration);
    }

    private static String readFileAsString(final File filePath)
    throws java.io.IOException{
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



    /**
     * Simple class loader to load ResourceBundle properties for a servlet's
     * WEB-INF directory.  Only the {@link #getResourceAsStream(String)} method
     * is modified to allow resources to be loaded from the WEB-INF directory, (or
     * anywhere in the parent ClassLoader's classpath.
     * <p/>
     * Calls to {@link #loadClass(String)} or {@link #findClass(String)} are not
     * overridden, and will be passed to the parent ClassLoader.  Thus, this classloader
     * doesn't actually change the behavior for normal class loading operations.
     * <p/>
     * Intended for use with {@link java.util.ResourceBundle#getBundle(String,java.util.Locale,ClassLoader)}
     *
     * @author Jason D. Rivard
     */
    public static class ConfigClassLoader extends ClassLoader {
        public final static String WEB_INF_DIR = "WEB-INF";

        private final String webInfPath;

        public ConfigClassLoader(final File forFile)
        {
            super(ConfigClassLoader.class.getClassLoader());
            webInfPath = forFile.getParent();
        }

        public InputStream getResourceAsStream(final String name)
        {
            final String pathName = webInfPath + File.separator + name;
            final File theFile = new File(pathName);
            if (!theFile.exists()) {
                return super.getResourceAsStream(name);
            }
            try {
                return new FileInputStream(theFile);
            } catch (FileNotFoundException e) {
                return null;
            }
        }
    }
}


