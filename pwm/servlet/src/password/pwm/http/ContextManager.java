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

package password.pwm.http;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.ConfigurationReader;
import password.pwm.config.StoredConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.Helper;
import password.pwm.util.PwmRandom;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ContextManager implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(ContextManager.class);

    private ServletContext servletContext;
    private Timer taskMaster;

    private transient PwmApplication pwmApplication;
    private ConfigurationReader configReader;
    private ErrorInformation startupErrorInformation;

    private volatile boolean restartRequestedFlag = false;
    private int restartCount = 0;
    private final String instanceGuid;

    private final transient Set<HttpSession> activeSessions = Collections.newSetFromMap(new ConcurrentHashMap<HttpSession, Boolean>());

    private enum ContextParameter {
        applicationPath,
        configurationFile,
    }

    public ContextManager(ServletContext servletContext) {
        this.servletContext = servletContext;
        this.instanceGuid = PwmRandom.getInstance().randomUUID().toString();
    }

    // -------------------------- STATIC METHODS --------------------------

    public static PwmApplication getPwmApplication(final HttpServletRequest request) throws PwmUnrecoverableException {
        return getPwmApplication(request.getSession());
    }

    public static PwmApplication getPwmApplication(final HttpSession session) throws PwmUnrecoverableException {
        return getContextManager(session.getServletContext()).getPwmApplication();
    }

    public static PwmApplication getPwmApplication(final ServletContext theContext) throws PwmUnrecoverableException {
        return getContextManager(theContext).getPwmApplication();
    }

    public static ContextManager getContextManager(final HttpSession session) throws PwmUnrecoverableException {
        return getContextManager(session.getServletContext());
    }

    public static ContextManager getContextManager(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        return getContextManager(pwmRequest.getHttpServletRequest().getSession());
    }

    public static ContextManager getContextManager(final ServletContext theContext) throws PwmUnrecoverableException {
        // context manager is initialized at servlet context startup.
        final Object theManager = theContext.getAttribute(PwmConstants.CONTEXT_ATTR_CONTEXT_MANAGER);
        if (theManager == null) {
            final String errorMsg = "unable to load the context manager from servlet context";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_APP_UNAVAILABLE,errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        return (ContextManager) theManager;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public PwmApplication getPwmApplication()
            throws PwmUnrecoverableException
    {
        if (pwmApplication == null) {
            final ErrorInformation errorInformation;
            if (startupErrorInformation != null) {
                errorInformation = startupErrorInformation;
            } else {
                errorInformation = new ErrorInformation(PwmError.ERROR_APP_UNAVAILABLE,"application is not yet available, please try again in a moment.");
            }
            throw new PwmUnrecoverableException(errorInformation);
        }
        return pwmApplication;
    }

// -------------------------- OTHER METHODS --------------------------

    public void initialize() {
        try {
            Locale.setDefault(PwmConstants.DEFAULT_LOCALE);
        } catch (Exception e) {
            outputError("unable to set default locale as Java machine default locale: " + e.getMessage());
        }

        final EnvironmentTest[] tests = new EnvironmentTest[]{
                new JavaVersionCheck()
        };
        for (final EnvironmentTest doTest : tests) {
            startupErrorInformation = doTest.doTest();
        }

        Configuration configuration = null;
        File applicationPath = null;
        PwmApplication.MODE mode = PwmApplication.MODE.ERROR;
        File configurationFile = null;
        try {
            applicationPath = locateApplicationPath();
            configurationFile = locateConfigurationFile(applicationPath);

            configReader = new ConfigurationReader(configurationFile);
            configReader.getStoredConfiguration().lock();
            configuration = configReader.getConfiguration();

            if (configReader == null) {
                mode = startupErrorInformation == null ? PwmApplication.MODE.ERROR : PwmApplication.MODE.ERROR;
            } else {
                mode = startupErrorInformation == null ? configReader.getConfigMode() : PwmApplication.MODE.ERROR;
            }

            if (startupErrorInformation == null) {
                startupErrorInformation = configReader.getConfigFileError();
            }

            if (PwmApplication.MODE.ERROR == mode) {
                outputError("Startup Error: " + (startupErrorInformation == null ? "un-specified error" : startupErrorInformation.toDebugStr()));
            }
        } catch (Throwable e) {
            handleStartupError("unable to initialize application due to configuration related error: ",e);
        }

        try {
            pwmApplication = new PwmApplication(configuration, mode, applicationPath, true, configurationFile);
        } catch (Exception e) {
            handleStartupError("unable to initialize application: ",e);
        }

        final String threadName = Helper.makeThreadName(pwmApplication, this.getClass()) + " timer";
        taskMaster = new Timer(threadName, true);
        taskMaster.schedule(new RestartFlagWatcher(), 1031, 1031);

        final boolean reloadOnChange = Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(AppProperty.CONFIG_RELOAD_ON_CHANGE));
        if (reloadOnChange) {
            final long fileScanFrequencyMs = Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.CONFIG_FILE_SCAN_FREQUENCY));
            taskMaster.schedule(new ConfigFileWatcher(), fileScanFrequencyMs, fileScanFrequencyMs);
        }

        LOGGER.debug(
                "configuration file was loaded from " + (configurationFile == null ? "null" : configurationFile.getAbsoluteFile()));

        checkConfigForSaveOnRestart(configReader, pwmApplication);
    }

    private void checkConfigForSaveOnRestart(
            final ConfigurationReader configReader,
            final PwmApplication pwmApplication
    )
    {
        if (configReader == null || configReader.getStoredConfiguration() == null) {
            return;
        }

        final String saveConfigOnRestartStrValue = configReader.getStoredConfiguration().readConfigProperty(
                StoredConfiguration.ConfigProperty.PROPERTY_KEY_SAVE_CONFIG_ON_START);

        if (saveConfigOnRestartStrValue == null ||  !Boolean.parseBoolean(saveConfigOnRestartStrValue)) {
            return;
        }

        LOGGER.warn("configuration file contains property \"" + StoredConfiguration.ConfigProperty.PROPERTY_KEY_SAVE_CONFIG_ON_START + "\"=true, will save configuration and set property to false.");

        try {
            final StoredConfiguration newConfig = StoredConfiguration.copy(configReader.getStoredConfiguration());
            newConfig.writeConfigProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_SAVE_CONFIG_ON_START, "false");
            configReader.saveConfiguration(newConfig, pwmApplication, null);
            restartRequestedFlag = true;
        } catch (Exception e) {
            LOGGER.error("error while saving configuration file commanded by property \"" + StoredConfiguration.ConfigProperty.PROPERTY_KEY_SAVE_CONFIG_ON_START + "\"=true, error: " + e.getMessage());
        }
    }

    private void handleStartupError(final String msgPrefix, final Throwable throwable) {
        final String errorMsg;
        if (throwable instanceof OutOfMemoryError) {
            errorMsg = "JAVA OUT OF MEMORY ERROR!, please allocate more memory for java: " + throwable.getMessage();
        } else {
            errorMsg = throwable.getMessage();
        }

        startupErrorInformation = new ErrorInformation(PwmError.ERROR_APP_UNAVAILABLE, msgPrefix + errorMsg);

        try {
            LOGGER.fatal(errorMsg);
        } catch (Exception e2) {
            // noop
        }

        outputError(errorMsg);
        throwable.printStackTrace();
    }

    public void shutdown() {
        startupErrorInformation = new ErrorInformation(PwmError.ERROR_APP_UNAVAILABLE, "shutting down");

        if (pwmApplication != null) {
            try {
                pwmApplication.shutdown();
            } catch (Exception e) {
                LOGGER.error("unexpected error attempting to close application: " + e.getMessage());
            }
        }
        taskMaster.cancel();


        this.pwmApplication = null;
        startupErrorInformation = null;
    }

    public void requestPwmApplicationRestart() {
        restartRequestedFlag = true;
        try {
            taskMaster.schedule(new ConfigFileWatcher(),0);
        } catch (IllegalStateException e) {
            LOGGER.debug("could not schedule config file watcher, timer is in illegal state: " + e.getMessage());
        }
    }

    public Set<HttpSession> getHttpSessions() {
        return Collections.unmodifiableSet(activeSessions);
    }

    public Map<HttpSession,PwmSession> getPwmSessions() {
        final Collection<HttpSession> copiedSet = getHttpSessions();
        final Map<HttpSession,PwmSession> returnSet = new HashMap<>();
        for (final HttpSession httpSession : copiedSet) {
            try {
                returnSet.put(httpSession, PwmSessionWrapper.readPwmSession(httpSession));
            } catch (PwmUnrecoverableException e) {
                /* */
            }
        }
        return returnSet;
    }

    public void addHttpSession(final HttpSession httpSession) {
        try {
            activeSessions.add(httpSession);
        } catch (Exception e) {
            LOGGER.trace("error adding new session to list of known sessions: " + e.getMessage());
        }
    }

    public void removeHttpSession(final HttpSession pwmSession) {
        try {
            activeSessions.remove(pwmSession);
        } catch (Exception e) {
            LOGGER.trace("error adding removing session from list of known sessions: " + e.getMessage());
        }
    }

    public ConfigurationReader getConfigReader() {
        return configReader;
    }

    private class ConfigFileWatcher extends TimerTask {
        @Override
        public void run() {
            if (configReader != null) {
                if (configReader.modifiedSinceLoad()) {
                    LOGGER.info("configuration file modification has been detected");
                    restartRequestedFlag = true;
                }
            }
        }
    }

    private class RestartFlagWatcher extends TimerTask {

        public void run() {
            if (restartRequestedFlag) {
                doReinitialize();
            }
        }

        private void doReinitialize() {
            if (configReader != null && configReader.isSaveInProgress()) {
                LOGGER.info("delaying restart request due to in progress file save");
                return;
            }

            LOGGER.info("beginning application restart");
            try {
                shutdown();
            } catch (Exception e) {
                LOGGER.fatal("unexpected error during shutdown: " + e.getMessage(),e);
            }

            LOGGER.info("application restart; shutdown completed, now starting new application instance");
            restartCount++;
            initialize();

            LOGGER.info("application restart completed");
            restartRequestedFlag = false;
        }
    }

    public ErrorInformation getStartupErrorInformation() {
        return startupErrorInformation;
    }

    private static interface EnvironmentTest {
        ErrorInformation doTest();
    }

    private static class JavaVersionCheck implements EnvironmentTest {
        public ErrorInformation doTest() {
            String sVersion = java.lang.System.getProperty("java.version");
            sVersion = sVersion.substring(0, 3);
            final Float f = Float.valueOf(sVersion);
            if (f < PwmConstants.JAVA_MINIMUM_VERSION) {
                final String errorMsg = "the minimum java version required is Java v" + PwmConstants.JAVA_MINIMUM_VERSION;
                outputError(errorMsg);
                LOGGER.fatal(errorMsg);
                return new ErrorInformation(PwmError.ERROR_APP_UNAVAILABLE, errorMsg);
            }
            return null;
        }
    }

    public enum DebugKey {
        HttpSessionCount,
        HttpSessionTotalSize,
        HttpSessionAvgSize,
    }

    public Map<DebugKey, String> getDebugData() {
        try {
            final Collection<PwmSession> sessionCopy = this.getPwmSessions().values();
            int sessionCounter = 0;
            long sizeTotal = 0;
            for (final PwmSession pwmSession : sessionCopy) {
                try {
                    sizeTotal += pwmSession.size();
                    sessionCounter++;
                } catch (Exception e) {
                    LOGGER.error("error during session size calculation: " + e.getMessage());
                }
            }
            Map<DebugKey, String> returnMap = new HashMap<>();
            returnMap.put(DebugKey.HttpSessionCount, String.valueOf(sessionCounter));
            returnMap.put(DebugKey.HttpSessionTotalSize, String.valueOf(sizeTotal));
            returnMap.put(DebugKey.HttpSessionAvgSize,
                    sessionCounter < 1 ? "0" : String.valueOf((int) (sizeTotal / sessionCounter)));
            return returnMap;
        } catch (Exception e) {
            LOGGER.error("error during session debug generation: " + e.getMessage());
        }
        return Collections.emptyMap();
    }

    public int getRestartCount()
    {
        return restartCount;
    }

    public File locateConfigurationFile(final File applicationPath)
            throws Exception
    {
        final String configurationFileSetting = servletContext.getInitParameter(
                ContextParameter.configurationFile.toString());

        try {
            File file = new File(configurationFileSetting);
            if (file.isAbsolute()) {
                return file;
            }
        } catch (Exception e) {
            outputError("error testing context " + ContextParameter.configurationFile.toString() + " parameter to verify if it is a valid file path: " + e.getMessage());
        }

        return new File(applicationPath.getAbsolutePath() + File.separator + configurationFileSetting);
    }

    public File locateApplicationPath()
            throws PwmException
    {
        if (this.servletContext == null) {
            return null;
        }

        // read system property.
        {
            final String propertyName = PwmConstants.PWM_APP_NAME.toLowerCase() + ".sspr.applicationPath";
            final String propertyAppPath = System.getProperty(propertyName);
            if (propertyAppPath != null && !propertyAppPath.isEmpty()) {
                return new File(propertyAppPath);
            }
        }


        final String contextAppPathSetting = servletContext.getInitParameter(
                ContextParameter.applicationPath.toString());

        // first try to check if context setting is a real directory.
        try {
            File file = new File(contextAppPathSetting);
            if (file.exists() && file.isDirectory()) {
                return file;
            }
        } catch (Exception e) {
            outputError("error testing context " + ContextParameter.applicationPath.toString() + " parameter to verify if it is a valid file path: " + e.getMessage());
        }

        final String prefixedRealPath = contextAppPathSetting == null
                ? "/" :
                contextAppPathSetting.startsWith("/")
                        ? contextAppPathSetting
                        : "/" + contextAppPathSetting;

        final String realPath = servletContext.getRealPath(prefixedRealPath);

        if (realPath != null) {
            final File servletPath = new File(realPath);
            if (servletPath.exists()) {
                return servletPath;
            }
        }

        outputError("servlet container unable to return real file system path, will attempt discovery using classloader");

        // for containers which do not return the real path, try to use the class loader to find the path.
        final String cManagerName = PwmApplication.class.getCanonicalName();
        final String resourcePathname = "/" + cManagerName.replace(".", "/") + ".class";
        final URL fileURL = PwmApplication.class.getResource(resourcePathname);
        if (fileURL != null) {
            final String newString = fileURL.toString().replace("WEB-INF/classes" + resourcePathname, "");
            try {
                return new File(new URL(newString).toURI());
            } catch (Exception e) {
                outputError("unable to determine application path using class loader: " + e.getMessage());
            }
        }

        throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_APP_UNAVAILABLE,"unable to determine applicationPath working directory"));
    }

    static void outputError(String outputText) {
        final String msg = PwmConstants.PWM_APP_NAME + " " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date()) + " " + outputText;
        System.out.println(msg);
        System.out.println(msg);
    }

    public String getInstanceGuid() {
        return instanceGuid;
    }
}
