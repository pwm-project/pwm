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

package password.pwm;

import password.pwm.config.Configuration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.util.Helper;
import password.pwm.util.JsonUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class PwmEnvironment implements Serializable {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmEnvironment.class);

    // data elements
    private final PwmApplication.MODE applicationMode;
    private final Configuration config;
    private final File applicationPath;
    private final boolean internalRuntimeInstance;
    private final File configurationFile;
    private final ContextManager contextManager;
    private final Collection<ApplicationFlag> flags;

    private final FileLocker fileLocker;

    public enum ApplicationFlag {
        Appliance,
        ManageHttps,
        NoFileLock,
        CommandLineInstance,

        ;

        public static ApplicationFlag forString(final String input) {
            for (final ApplicationFlag flag : ApplicationFlag.values()) {
                if (flag.toString().equals(input) || flag.toString().toUpperCase().equals(input)) {
                    return flag;
                }
            }
            return null;
        }
    }

    public enum EnvironmentParameter {
        applicationPath,
        applicationFlags,

        ;

        public String conicalJavaOptionSystemName() {
            return PwmConstants.PWM_APP_NAME.toLowerCase() + "." + this.toString();
        }

        public String conicalEnvironmentSystemName() {
            return (PwmConstants.PWM_APP_NAME.toLowerCase() + "_" + this.toString()).toUpperCase();
        }

        public List<String> possibleNames(final String contextName) {
            final List<String> returnValues = new ArrayList<>();
            if (contextName != null) {
                final String value = PwmConstants.PWM_APP_NAME.toLowerCase() // java property format <app>.<context>.<paramName> like pwm.pwm.applicationFlag
                        + "."
                        + contextName
                        + "."
                        + this.toString();
                returnValues.add(value);
                returnValues.add(value.toUpperCase());
                returnValues.add(value.replace(".", "_"));
                returnValues.add(value.toUpperCase().replace(".", "_"));
            }
            {
                final String value = PwmConstants.PWM_APP_NAME.toLowerCase() // java property format <app>.<paramName> like pwm.applicationFlag
                        + "."
                        + this.toString();
                returnValues.add(value);
                returnValues.add(value.toUpperCase());
                returnValues.add(value.replace(".","_"));
                returnValues.add(value.toUpperCase().replace(".", "_"));
            }

            return Collections.unmodifiableList(returnValues);
        }
    }

    private PwmEnvironment(
            final PwmApplication.MODE applicationMode,
            final Configuration config,
            final File applicationPath,
            final boolean internalRuntimeInstance,
            final File configurationFile,
            final ContextManager contextManager,
            final Collection<ApplicationFlag> flags
    ) {
        this.applicationMode = applicationMode == null ? PwmApplication.MODE.ERROR : applicationMode;
        this.config = config;
        this.applicationPath = applicationPath;
        this.internalRuntimeInstance = internalRuntimeInstance;
        this.configurationFile = configurationFile;
        this.contextManager = contextManager;
        this.flags = flags == null ? Collections.<ApplicationFlag>emptySet() : Collections.unmodifiableSet(new HashSet<>(flags));

        this.fileLocker = new FileLocker();

        verify();
    }

    public PwmApplication.MODE getApplicationMode() {
        return applicationMode;
    }

    public Configuration getConfig() {
        return config;
    }

    public File getApplicationPath() {
        return applicationPath;
    }

    public boolean isInternalRuntimeInstance() {
        return internalRuntimeInstance;
    }

    public File getConfigurationFile() {
        return configurationFile;
    }

    public ContextManager getContextManager() {
        return contextManager;
    }

    public Collection<ApplicationFlag> getFlags() {
        return flags;
    }

    private void verify() {

    }

    public void verifyIfApplicationPathIsSetProperly()
            throws PwmUnrecoverableException
    {
        final File applicationPath = this.getApplicationPath();

        verifyApplicationPath(applicationPath);

        boolean applicationPathIsWebInfPath = false;
        if (applicationPath.getAbsolutePath().endsWith("/WEB-INF")) {
            final File webXmlFile = new File(applicationPath.getAbsolutePath() + File.separator + "web.xml");
            if (webXmlFile.exists()) {
                applicationPathIsWebInfPath = true;
            }
        }
        if (applicationPathIsWebInfPath) {
            LOGGER.trace("applicationPath appears to be servlet /WEB-INF directory");
        }

        /* scheduled for demolition....
        if (webInfPath != null) {
            final File infoFile = new File(webInfPath.getAbsolutePath() + File.separator + PwmConstants.APPLICATION_PATH_INFO_FILE);
            if (applicationPathIsWebInfPath) {
                if (this.getApplicationPathType() == PwmEnvironment.ApplicationPathType.derived) {
                    LOGGER.trace("checking " + infoFile.getAbsolutePath() + " status, (applicationPathType=" + PwmEnvironment.ApplicationPathType.derived + ")");
                    if (infoFile.exists()) {
                        final String errorMsg = "The file " + infoFile.getAbsolutePath() + " exists, and an applicationPath was not explicitly specified."
                                + "  This happens when an applicationPath was previously configured, but is not now being specified."
                                + "  An explicit applicationPath parameter must be specified, or the file can be removed if the applicationPath should be changed to the default /WEB-INF directory.";
                        throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_STARTUP_ERROR, errorMsg));
                    } else {
                        LOGGER.trace("marker file " + infoFile.getAbsolutePath() + " does not exist");
                    }
                }
            } else {
                if (this.getApplicationPathType() == PwmEnvironment.ApplicationPathType.specified) {
                    try {
                        final FileOutputStream fos = new FileOutputStream(infoFile);
                        final Properties outputProperties = new Properties();
                        outputProperties.setProperty("lastApplicationPath", applicationPath.getAbsolutePath());
                        outputProperties.store(fos, "Marker file to record a previously configured applicationPath");
                    } catch (IOException e) {
                        LOGGER.warn("unable to write applicationPath marker properties file " + infoFile.getAbsolutePath() + "");
                    }
                }
            }
        }
        */
    }

    public PwmEnvironment makeRuntimeInstance(
            final Configuration configuration
    )
            throws PwmUnrecoverableException
    {
        return new Builder(this)
                .setApplicationMode(PwmApplication.MODE.NEW)
                .setInternalRuntimeInstance(true)
                .setConfigurationFile(null)
                .setConfig(configuration)
                .createPwmEnvironment();
    }


    public static void verifyApplicationPath(final File applicationPath) throws PwmUnrecoverableException {

        if (applicationPath == null) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_STARTUP_ERROR, "unable to determine valid applicationPath"));
        }

        LOGGER.trace("examining applicationPath of " + applicationPath.getAbsolutePath() + "");

        if (!applicationPath.exists()) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_STARTUP_ERROR, "applicationPath " + applicationPath.getAbsolutePath() + " does not exist"));
        }

        if (!applicationPath.canRead()) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_STARTUP_ERROR, "unable to read from applicationPath " + applicationPath.getAbsolutePath() + ""));
        }

        if (!applicationPath.canWrite()) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_STARTUP_ERROR, "unable to write to applicationPath " + applicationPath.getAbsolutePath() + ""));
        }

        final File infoFile = new File(applicationPath.getAbsolutePath() + File.separator + PwmConstants.APPLICATION_PATH_INFO_FILE);
        LOGGER.trace("checking " + infoFile.getAbsolutePath() + " status");
        if (infoFile.exists()) {
            final String errorMsg = "The file " + infoFile.getAbsolutePath() + " exists, and an applicationPath was not explicitly specified."
                    + "  This happens when an applicationPath was previously configured, but is not now being specified."
                    + "  An explicit applicationPath parameter must be specified, or the file can be removed if the applicationPath should be changed to the default /WEB-INF directory.";
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_STARTUP_ERROR, errorMsg));
        }

    }

    public static class ParseHelper {
        public static Collection<ApplicationFlag> readApplicationFlagsFromSystem(final String contextName) {
            final String rawValue = readValueFromSystem(EnvironmentParameter.applicationFlags, contextName);
            if (rawValue != null) {
                return parseApplicationFlagValueParameter(rawValue);
            }
            return Collections.emptyList();
        }


        public static String readValueFromSystem(PwmEnvironment.EnvironmentParameter parameter, final String contextName) {
            final List<String> namePossibilities = parameter.possibleNames(contextName);

            for (final String propertyName : namePossibilities){
                final String propValue = System.getProperty(propertyName);
                if (propValue != null && !propValue.isEmpty()) {
                    return propValue;
                }
            }

            for (final String propertyName : namePossibilities){
                final String propValue = System.getenv(propertyName);
                if (propValue != null && !propValue.isEmpty()) {
                    return propValue;
                }
            }

            return null;
        }

        public static Collection<ApplicationFlag> parseApplicationFlagValueParameter(final String input) {
            if (input == null) {
                return Collections.emptyList();
            }

            try {
                final List<String> jsonValues = JsonUtil.deserializeStringList(input);
                final List<ApplicationFlag> returnFlags = new ArrayList<>();
                for (final String value : jsonValues) {
                    final ApplicationFlag flag = ApplicationFlag.forString(value);
                    if (value != null) {
                        returnFlags.add(flag);
                    } else {
                        LOGGER.warn("unknown " + EnvironmentParameter.applicationFlags.toString() + " value: " + input);
                    }
                }
                return Collections.unmodifiableList(returnFlags);
            } catch (Exception e) {
                //
            }

            final List<ApplicationFlag> returnFlags = new ArrayList<>();
            for (final String value : input.split(",")) {
                final ApplicationFlag flag = ApplicationFlag.forString(value);
                if (value != null) {
                    returnFlags.add(flag);
                } else {
                    LOGGER.warn("unknown " + EnvironmentParameter.applicationFlags.toString() + " value: " + input);
                }
            }
            return returnFlags;
        }
    }



    public static class Builder {
        private PwmApplication.MODE applicationMode;
        private Configuration config;
        private File applicationPath;
        private boolean internalRuntimeInstance;
        private File configurationFile;
        private ContextManager contextManager;
        private Collection<ApplicationFlag> flags = new HashSet<>();

        public Builder(final PwmEnvironment pwmEnvironment) {
            this.applicationMode         = pwmEnvironment.applicationMode;
            this.config                  = pwmEnvironment.config;
            this.applicationPath         = pwmEnvironment.applicationPath;
            this.internalRuntimeInstance = pwmEnvironment.internalRuntimeInstance;
            this.configurationFile       = pwmEnvironment.configurationFile;
            this.contextManager          = pwmEnvironment.contextManager;
            this.flags                   = pwmEnvironment.flags;
        }

        public Builder(Configuration config, File applicationPath) {
            this.config = config;
            this.applicationPath = applicationPath;
        }

        public Builder setApplicationMode(PwmApplication.MODE applicationMode) {
            this.applicationMode = applicationMode;
            return this;
        }

        public Builder setInternalRuntimeInstance(boolean internalRuntimeInstance) {
            this.internalRuntimeInstance = internalRuntimeInstance;
            return this;
        }

        public Builder setConfigurationFile(File configurationFile) {
            this.configurationFile = configurationFile;
            return this;
        }

        public Builder setContextManager(ContextManager contextManager) {
            this.contextManager = contextManager;
            return this;
        }

        public Builder setFlags(Collection<ApplicationFlag> flags) {
            this.flags.clear();
            if (flags != null) {
                this.flags.addAll(flags);
            }
            return this;
        }

        public Builder setConfig(Configuration config) {
            this.config = config;
            return this;
        }

        public PwmEnvironment createPwmEnvironment() {
            return new PwmEnvironment(
                    applicationMode,
                    config,
                    applicationPath,
                    internalRuntimeInstance,
                    configurationFile,
                    contextManager,
                    flags
            );
        }
    }

    public void attemptFileLock() {
        fileLocker.attemptFileLock();
    }

    public void releaseFileLock()
    {
        fileLocker.releaseFileLock();
    }

    public boolean isFileLocked() {
        return fileLocker.isLocked();
    }

    public void waitForFileLock() throws PwmUnrecoverableException {
        final int maxWaitSeconds = Integer.parseInt(getConfig().readAppProperty(AppProperty.APPLICATION_FILELOCK_WAIT_SECONDS));
        final Date startTime = new Date();
        final int attemptInterval = 5021; //ms

        while (!this.isFileLocked() && TimeDuration.fromCurrent(startTime).isShorterThan(maxWaitSeconds, TimeUnit.SECONDS)) {
            attemptFileLock();

            if (!isFileLocked()) {
                LOGGER.debug("can't establish application file lock after "
                        + TimeDuration.fromCurrent(startTime).asCompactString()
                        + ", will retry;");
                Helper.pause(attemptInterval);
            }
        }

        if (!isFileLocked()) {
            final String errorMsg = "unable to obtain application path file lock";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_STARTUP_ERROR,errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
    }

    private class FileLocker {
        private FileLock lock;
        private final File lockfile;

        public FileLocker() {
            final String lockfileName = config.readAppProperty(AppProperty.APPLICATION_FILELOCK_FILENAME);
            lockfile = new File(getApplicationPath(), lockfileName);
        }

        private boolean lockingAllowed() {
            return !isInternalRuntimeInstance() && !getFlags().contains(ApplicationFlag.NoFileLock);
        }

        public boolean isLocked() {
            return !lockingAllowed() || lock != null && lock.isValid();
        }

        public void attemptFileLock() {
            if (lockingAllowed() && !isLocked()) {
                try {
                    final RandomAccessFile file = new RandomAccessFile(lockfile, "rw");
                    final FileChannel f = file.getChannel();
                    lock = f.tryLock();
                    if (lock != null) {
                        LOGGER.debug("obtained file lock on file " + lockfile.getAbsolutePath());
                    } else {
                        LOGGER.debug("unable to obtain file lock on file " + lockfile.getAbsolutePath());
                    }
                } catch (Exception e) {
                    LOGGER.error("unable to obtain file lock on file " + lockfile.getAbsolutePath() + " due to error: " + e.getMessage());
                }
            }
        }

        public void releaseFileLock() {
            if (lock != null && lock.isValid()) {
                try {
                    lock.release();
                } catch (IOException e) {
                    LOGGER.error("error releasing file lock: " + e.getMessage());
                }
                lock = null;
                LOGGER.debug("released file lock on file " + lockfile.getAbsolutePath());
            }
        }
    }
}
