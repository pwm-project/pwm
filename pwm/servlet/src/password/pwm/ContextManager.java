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

package password.pwm;

import password.pwm.config.Configuration;
import password.pwm.config.ConfigurationReader;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.Serializable;
import java.util.*;

public class ContextManager implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ContextManager.class);

    private ServletContext servletContext;
    private Timer taskMaster;

    private transient PwmApplication pwmApplication;
    private ConfigurationReader configReader;
    private ErrorInformation startupErrorInformation;

    private volatile boolean restartRequestedFlag = false;

    private final transient Map<PwmSession, Object> activeSessions = new WeakHashMap<PwmSession, Object>();

    public ContextManager(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    // -------------------------- STATIC METHODS --------------------------

    public static PwmApplication getPwmApplication(final HttpServletRequest request) throws PwmUnrecoverableException {
        return getPwmApplication(request.getSession());
    }

    public static PwmApplication getPwmApplication(final HttpSession session) throws PwmUnrecoverableException {
        return getContextManager(session.getServletContext()).getPwmApplication();
    }

    public static ContextManager getContextManager(final HttpSession session) throws PwmUnrecoverableException {
        return getContextManager(session.getServletContext());
    }

    public static ContextManager getContextManager(final ServletContext theContext) throws PwmUnrecoverableException {
        // context manager is initialized at servlet context startup.
        final Object theManager = theContext.getAttribute(PwmConstants.CONTEXT_ATTR_CONTEXT_MANAGER);
        if (theManager == null) {
            final String errorMsg = "unable to load the context manager from servlet context";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_PWM_UNAVAILABLE,errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        return (ContextManager) theManager;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public PwmApplication getPwmApplication()
            throws PwmUnrecoverableException
    {
        if (pwmApplication == null) {
            throw new PwmUnrecoverableException(startupErrorInformation);
        }
        return pwmApplication;
    }

// -------------------------- OTHER METHODS --------------------------

    void initialize() {
        javaVersionCheck();
        try {
            final String configFilePathName = servletContext.getInitParameter(PwmConstants.CONFIG_FILE_CONTEXT_PARAM);
            final File configurationFile = ServletHelper.figureFilepath(configFilePathName, "WEB-INF/", servletContext);
            configReader = new ConfigurationReader(configurationFile);
            final Configuration configuration = configReader.getConfiguration();
            final File pwmApplicationPath = (ServletHelper.figureFilepath(".", "WEB-INF/", servletContext)).getCanonicalFile();
            final PwmApplication.MODE mode = startupErrorInformation == null ? configReader.getConfigMode() : PwmApplication.MODE.ERROR;
            if (startupErrorInformation == null) {
                startupErrorInformation = configReader.getConfigFileError();
            }
            pwmApplication = new PwmApplication(configuration, mode, pwmApplicationPath);
        } catch (OutOfMemoryError e) {
            final String errorMsg = "JAVA OUT OF MEMORY ERROR!, please allocate more memory for java: " + e.getMessage();
            startupErrorInformation = new ErrorInformation(PwmError.ERROR_PWM_UNAVAILABLE, errorMsg);
            try {LOGGER.fatal(errorMsg);} catch (Exception e2) {/* we tried anyway.. */}
            System.err.println(errorMsg);
        } catch (Exception e) {
            final String errorMsg = "unable to initialize pwm due to configuration related error: " + e.getMessage();
            startupErrorInformation = new ErrorInformation(PwmError.ERROR_PWM_UNAVAILABLE, errorMsg);
            try {LOGGER.fatal(errorMsg);} catch (Exception e2) {/* we tried anyway.. */}
            System.err.println(errorMsg);
        }


        if ("true".equalsIgnoreCase(servletContext.getInitParameter("configChange-reload"))) {
            taskMaster = new Timer("pwm-ContextManager timer", true);
            taskMaster.schedule(new ConfigFileWatcher(), PwmConstants.CONFIG_FILE_SCAN_FREQUENCY, PwmConstants.CONFIG_FILE_SCAN_FREQUENCY);
            taskMaster.schedule(new SessionWatcherTask(), PwmConstants.CONFIG_FILE_SCAN_FREQUENCY, PwmConstants.CONFIG_FILE_SCAN_FREQUENCY);
        }
    }

    void shutdown() {
        startupErrorInformation = new ErrorInformation(PwmError.ERROR_PWM_UNAVAILABLE, "shutting down");
        try {
            final PwmApplication methodLocalPwmApp = this.getPwmApplication();
            methodLocalPwmApp.shutdown();
            taskMaster.cancel();

            try {
                for (final PwmSession pwmSession : this.getPwmSessions()) {
                    pwmSession.getSessionManager().closeConnections();
                }
            } catch (Exception e) {
                LOGGER.error("unexpected error attempting to close ldap connections: " + e.getMessage());
            }


        } catch (PwmUnrecoverableException e) {
            LOGGER.fatal("unexpected error during pwm shutdown: " + e.getMessage(),e);
        }
        this.pwmApplication = null;
        startupErrorInformation = null;
    }

    public void reinitialize() {
        if ("true".equalsIgnoreCase(servletContext.getInitParameter("configChange-reload"))) {
            restartRequestedFlag = true;
        } else {
            LOGGER.info("skipping application restart due to web.xml configChange-reload=false");
        }
    }

    public Set<PwmSession> getPwmSessions() {
        return Collections.unmodifiableSet(activeSessions.keySet());
    }

    public void addPwmSession(final PwmSession pwmSession) {
        try {
            activeSessions.put(pwmSession, new Object());
        } catch (Exception e) {
            LOGGER.trace("error adding new session to list of known sessions: " + e.getMessage());
        }
    }

    public ConfigurationReader getConfigReader() {
        return configReader;
    }

    // -------------------------- INNER CLASSES --------------------------

    public class SessionWatcherTask extends TimerTask {
        public void run() {
            final Map<PwmSession, Object> copiedMap = new HashMap<PwmSession, Object>();

            try {
                synchronized (activeSessions) {
                    copiedMap.putAll(activeSessions);
                }

                final Set<PwmSession> deadSessions = new HashSet<PwmSession>();

                for (final PwmSession pwmSession : copiedMap.keySet()) {
                    if (!pwmSession.isValid()) {
                        deadSessions.add(pwmSession);
                    }
                }

                synchronized (activeSessions) {
                    activeSessions.keySet().removeAll(deadSessions);
                }
            } catch (Throwable e) {
                LOGGER.error("error clearing sessions during restart: " + e.getMessage());
            }
        }
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

            if (restartRequestedFlag) {
                doReinitialize();
            }
        }

        public void doReinitialize() {
            LOGGER.info("beginning application restart");
            try {
                shutdown();
            } catch (Exception e) {
                LOGGER.fatal("unexpected error during pwm shutdown: " + e.getMessage(),e);
            }

            LOGGER.info("application restart; shutdown completed, now starting new application instance");
            initialize();

            LOGGER.info("invalidating all existing http sessions");
            for (PwmSession pwmSession: getPwmSessions()) {
                try { pwmSession.invalidate(); } catch (Exception e) { /* no error */ }
            }

            LOGGER.info("application restart completed");
            restartRequestedFlag = false;
        }
    }

    private void javaVersionCheck() {
        String sVersion = java.lang.System.getProperty("java.version");
        sVersion = sVersion.substring(0, 3);
        final Float f = Float.valueOf(sVersion);
        if (f < PwmConstants.JAVA_MINIMUM_VERSION) {
            final String errorMsg = "The minimum version required for PWM is Java v" + PwmConstants.JAVA_MINIMUM_VERSION;
            System.out.println(errorMsg);
            System.err.println(errorMsg);
            LOGGER.fatal(errorMsg);
            startupErrorInformation = new ErrorInformation(PwmError.ERROR_PWM_UNAVAILABLE, errorMsg);
        }
    }

    public ErrorInformation getStartupErrorInformation() {
        return startupErrorInformation;
    }
}