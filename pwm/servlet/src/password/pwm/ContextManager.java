/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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

import com.google.gson.Gson;
import com.novell.ldapchai.ChaiConstant;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import org.apache.log4j.*;
import org.apache.log4j.xml.DOMConfigurator;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SmsItemBean;
import password.pwm.config.Configuration;
import password.pwm.config.ConfigurationReader;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.*;
import password.pwm.util.*;
import password.pwm.util.db.DatabaseAccessor;
import password.pwm.util.pwmdb.PwmDB;
import password.pwm.util.pwmdb.PwmDBException;
import password.pwm.util.pwmdb.PwmDBFactory;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;
import password.pwm.wordlist.SeedlistManager;
import password.pwm.wordlist.SharedHistoryManager;
import password.pwm.wordlist.WordlistConfiguration;
import password.pwm.wordlist.WordlistManager;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.Serializable;
import java.util.*;

/**
 * A repository for objects common to the servlet context.  A singleton
 * of this object is stored in the servlet context.
 *
 * @author Jason D. Rivard
 */
public class ContextManager implements Serializable {
// ------------------------------ FIELDS ------------------------------

    // ----------------------------- CONSTANTS ----------------------------
    private static final PwmLogger LOGGER = PwmLogger.getLogger(ContextManager.class);
    private static final String DB_KEY_INSTANCE_ID = "context_instanceID";
    private static final String DB_KEY_CONFIG_SETTING_HASH = "configurationSettingHash";
    private static final String DB_KEY_INSTALL_DATE = "DB_KEY_INSTALL_DATE";
    private static final String DB_KEY_LAST_LDAP_ERROR = "lastLdapError";

    private static final String DEFAULT_INSTANCE_ID = "-1";

    private String instanceID = DEFAULT_INSTANCE_ID;

    private final transient Map<PwmSession, Object> activeSessions = new WeakHashMap<PwmSession, Object>();

    private final IntruderManager intruderManager = new IntruderManager(this);

    private transient ServletContext servletContext;
    private transient Configuration configuration;
    private transient ConfigurationReader configReader;
    private transient EmailQueueManager emailQueue;
    private transient SmsQueueManager smsQueue;

    private transient HealthMonitor healthMonitor;
    private transient StatisticsManager statisticsManager;
    private transient WordlistManager wordlistManager;
    private transient SharedHistoryManager sharedHistoryManager;
    private transient SeedlistManager seedlistManager;
    private transient Timer taskMaster;
    private transient PwmDB pwmDB;
    private transient PwmDBLogger pwmDBLogger;
    private transient volatile ChaiProvider proxyChaiProvider;
    private transient boolean restartRequested;
    private transient volatile DatabaseAccessor databaseAccessor;

    private final Date startupTime = new Date();
    private Date installTime = new Date();
    private ErrorInformation lastLdapFailure = null;

    private List<Locale> knownLocales;


// -------------------------- STATIC METHODS --------------------------

    public static ContextManager getContextManager(final HttpServletRequest request) throws PwmUnrecoverableException {
        return getContextManager(request.getSession());
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

// --------------------------- CONSTRUCTORS ---------------------------

    ContextManager() {
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getInstanceID() {
        return instanceID;
    }

    public SharedHistoryManager getSharedHistoryManager() {
        return sharedHistoryManager;
    }

    public IntruderManager getIntruderManager() {
        return intruderManager;
    }

    public ChaiProvider getProxyChaiProvider()
            throws ChaiUnavailableException {
        if (proxyChaiProvider == null) {
            openProxyChaiProvider();
        }

        return proxyChaiProvider;
    }

    public PwmDBLogger getPwmDBLogger() {
        return pwmDBLogger;
    }

    public HealthMonitor getHealthMonitor() {
        return healthMonitor;
    }

    public Set<PwmService> getPwmServices() {
        final Set<PwmService> pwmServices = new HashSet<PwmService>();
        pwmServices.add(this.emailQueue);
        pwmServices.add(this.smsQueue);
        pwmServices.add(this.wordlistManager);
        pwmServices.add(this.databaseAccessor);
        pwmServices.remove(null);
        return Collections.unmodifiableSet(pwmServices);
    }

    private void openProxyChaiProvider() throws ChaiUnavailableException {
        if (proxyChaiProvider == null) {
            final StringBuilder debugLogText = new StringBuilder();
            debugLogText.append("opening new ldap proxy connection");
            LOGGER.trace(debugLogText.toString());

            final String proxyDN = this.getConfig().readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
            final String proxyPW = this.getConfig().readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD);

            try {
                proxyChaiProvider = Helper.createChaiProvider(this.getConfig(), proxyDN, proxyPW);
            } catch (ChaiUnavailableException e) {
                getStatisticsManager().incrementValue(Statistic.LDAP_UNAVAILABLE_COUNT);
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,e.getMessage());
                setLastLdapFailure(errorInformation);
                LOGGER.fatal("check ldap proxy settings: " + e.getMessage());
                throw e;
            }
        }
    }

    public ServletContext getServletContext() {
        return servletContext;
    }

    public WordlistManager getWordlistManager() {
        return wordlistManager;
    }

    public SeedlistManager getSeedlistManager() {
        return seedlistManager;
    }

    public EmailQueueManager getEmailQueue() {
        return emailQueue;
    }

    public SmsQueueManager getSmsQueue() {
        return smsQueue;
    }

    public ErrorInformation getLastLdapFailure() {
        return lastLdapFailure;
    }

    public void setLastLdapFailure(final ErrorInformation errorInformation) {
        this.lastLdapFailure = errorInformation;
        if (pwmDB != null) {
            try {
                if (errorInformation == null) {
                    pwmDB.remove(PwmDB.DB.PWM_META,DB_KEY_LAST_LDAP_ERROR);
                } else {
                    final Gson gson = new Gson();
                    final String jsonString = gson.toJson(errorInformation);
                    pwmDB.put(PwmDB.DB.PWM_META,DB_KEY_LAST_LDAP_ERROR,jsonString);
                }
            } catch (PwmDBException e) {
                LOGGER.error("error writing lastLdapFailure time to pwmDB: " + e.getMessage());
            }
        }
    }

    // -------------------------- OTHER METHODS --------------------------

    public Configuration getConfig() {
        if (configuration == null) {
            return null;
        }
        return configuration;
    }

    public ChaiUser getProxyChaiUserActor(final PwmSession pwmSession)
            throws PwmUnrecoverableException, ChaiUnavailableException {
        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            throw new PwmUnrecoverableException(PwmError.ERROR_AUTHENTICATION_REQUIRED);
        }
        final String userDN = pwmSession.getUserInfoBean().getUserDN();


        return ChaiFactory.createChaiUser(userDN, this.getProxyChaiProvider());
    }

    public Set<PwmSession> getPwmSessions() {
        return Collections.unmodifiableSet(activeSessions.keySet());
    }

    public synchronized DatabaseAccessor getDatabaseAccessor()
            throws PwmUnrecoverableException
    {
        return databaseAccessor;
    }

    void initialize(final ServletContext servletContext)
            throws Exception {
        final long startTime = System.currentTimeMillis();
        this.servletContext = servletContext;

        // default log4j logging
        PwmInitializer.initializeLogger(null, "TRACE", servletContext);

        // initialize known locales.
        PwmInitializer.initializeKnownLocales(this);

        // initialize configuration
        try {
            final File configFile = ServletHelper.figureFilepath(getParameter(PwmConstants.CONTEXT_PARAM.CONFIG_FILE), "WEB-INF", servletContext);
            configReader = new ConfigurationReader(configFile);
            configuration = configReader.getConfiguration();

            switch (configReader.getConfigMode()) {
                case ERROR:
                case NEW:
                    taskMaster = new Timer("pwm-ContextManager timer", true);
                    taskMaster.schedule(new ConfigFileWatcher(), 5 * 1000, 5 * 1000);
                    return;
            }
        } catch (Exception e) {
            LOGGER.fatal("unable to initialize pwm due to missing or malformed configuration: " + e.getMessage());
            return;
        }

        // initialize log4j
        {
            final String log4jFile = configuration.readSettingAsString(PwmSetting.EVENTS_JAVA_LOG4JCONFIG_FILE);
            final String logLevel = configuration.readSettingAsString(PwmSetting.EVENTS_JAVA_STDOUT_LEVEL);
            PwmInitializer.initializeLogger(log4jFile, logLevel, servletContext);
        }

        PwmInitializer.initializePwmDB(this);
        PwmInitializer.initializePwmDBLogger(this);

        PwmInitializer.initializeHealthMonitor(this);

        LOGGER.info("initializing pwm");
        // log the loaded configuration
        LOGGER.info(logContextParams());
        LOGGER.info("loaded configuration: \n" + configuration.toString());
        LOGGER.info("loaded pwm global password policy: " + configuration.getGlobalPasswordPolicy(PwmConstants.DEFAULT_LOCALE));

        // get the pwm servlet instance id
        instanceID = fetchInstanceID(pwmDB, this);
        LOGGER.info("using '" + getInstanceID() + "' for this pwm instance's ID (instanceID)");



        // read the lastLoginTime
        lastLastLdapFailure(pwmDB, this);

        // get the pwm installation date
        installTime = fetchInstallDate(pwmDB, startupTime);
        LOGGER.debug("this pwm instance first installed on " + installTime.toString());

        // startup the stats engine;
        PwmInitializer.initializeStatisticsManager(this);

        PwmInitializer.initializeWordlist(this);
        PwmInitializer.initializeSeedlist(this);
        PwmInitializer.initializeSharedHistory(this);

        LOGGER.info(logEnvironment());
        LOGGER.info(logDebugInfo(activeSessions.size()));

        emailQueue = new EmailQueueManager(this);
        LOGGER.trace("email queue manager started");

        smsQueue = new SmsQueueManager(this);
        LOGGER.trace("sms queue manager started");

        taskMaster = new Timer("pwm-ContextManager timer", true);
        taskMaster.schedule(new IntruderManager.CleanerTask(intruderManager), 90 * 1000, 90 * 1000);
        taskMaster.schedule(new SessionWatcherTask(), 5 * 1000, 5 * 1000);
        taskMaster.scheduleAtFixedRate(new DebugLogOutputter(), 60 * 60 * 1000, 60 * 60 * 1000); //once every hour
        taskMaster.schedule(new ConfigFileWatcher(), 5 * 1000, 5 * 1000);

        final TimeDuration totalTime = new TimeDuration(System.currentTimeMillis() - startTime);
        LOGGER.info("PWM " + PwmConstants.SERVLET_VERSION + " open for bidness! (" + totalTime.asCompactString() + ")");
        LOGGER.debug("buildTime=" + PwmConstants.BUILD_TIME + ", javaLocale=" + Locale.getDefault() + ", pwmDefaultLocale=" + PwmConstants.DEFAULT_LOCALE );

        // detect if config has been modified since previous startup
        try {
            if (pwmDB != null) {
                final String previousHash = pwmDB.get(PwmDB.DB.PWM_META, DB_KEY_CONFIG_SETTING_HASH);
                final String currentHash = configuration.readProperty(StoredConfiguration.PROPERTY_KEY_SETTING_CHECKSUM);
                if (previousHash == null || !previousHash.equals(currentHash)) {
                    pwmDB.put(PwmDB.DB.PWM_META, DB_KEY_CONFIG_SETTING_HASH, currentHash);
                    LOGGER.warn("pwm configuration has been modified since last startup");
                    AlertHandler.alertConfigModify(this, configuration);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("unable to detect if configuration has been modified since previous startup: " + e.getMessage());
        }

        {
            final DatabaseAccessor.DBConfiguration dbConfiguration = new DatabaseAccessor.DBConfiguration(
                    getConfig().readSettingAsString(PwmSetting.DATABASE_CLASS),
                    getConfig().readSettingAsString(PwmSetting.DATABASE_URL),
                    getConfig().readSettingAsString(PwmSetting.DATABASE_USERNAME),
                    getConfig().readSettingAsString(PwmSetting.DATABASE_PASSWORD));

            databaseAccessor = new DatabaseAccessor(dbConfiguration, this.getInstanceID());
        }

        AlertHandler.alertStartup(this);

        if (configReader.getConfigMode() != ConfigurationReader.MODE.RUNNING) {
            taskMaster.schedule(new TimerTask(){ public void run() {
                        getHealthMonitor().getHealthRecords(true);
                    }},100);
        }
    }

    public String getParameter(final PwmConstants.CONTEXT_PARAM param) {
        return servletContext.getInitParameter(param.getKey());
    }

    private static Date fetchInstallDate(final PwmDB pwmDB, final Date startupTime) {
        if (pwmDB != null) {
            try {
                final String storedDateStr = pwmDB.get(PwmDB.DB.PWM_META, DB_KEY_INSTALL_DATE);
                if (storedDateStr == null || storedDateStr.length() < 1) {
                    pwmDB.put(PwmDB.DB.PWM_META, DB_KEY_INSTALL_DATE, String.valueOf(startupTime.getTime()));
                } else {
                    return new Date(Long.parseLong(storedDateStr));
                }
            } catch (Exception e) {
                LOGGER.error("error retrieving installation date from pwmDB: " + e.getMessage());
            }
        }
        return new Date();
    }

    private static String fetchInstanceID(final PwmDB pwmDB, final ContextManager contextManager) {
        String newInstanceID = contextManager.getConfig().readSettingAsString(PwmSetting.PWM_INSTANCE_NAME);

        if (newInstanceID != null && newInstanceID.trim().length() > 0) {
            return newInstanceID;
        }

        if (pwmDB != null) {
            try {
                newInstanceID = pwmDB.get(PwmDB.DB.PWM_META, DB_KEY_INSTANCE_ID);
                LOGGER.trace("retrieved instanceID " + newInstanceID + "" + " from pwmDB");
            } catch (Exception e) {
                LOGGER.warn("error retrieving instanceID from pwmDB: " + e.getMessage(), e);
            }
        }

        if (newInstanceID == null || newInstanceID.length() < 1) {
            newInstanceID = Long.toHexString(PwmRandom.getInstance().nextLong()).toUpperCase();
            LOGGER.info("generated new random instanceID " + newInstanceID);

            if (pwmDB != null) {
                try {
                    pwmDB.put(PwmDB.DB.PWM_META, DB_KEY_INSTANCE_ID, String.valueOf(newInstanceID));
                    LOGGER.debug("saved instanceID " + newInstanceID + "" + " to pwmDB");
                } catch (Exception e) {
                    LOGGER.warn("error saving instanceID to pwmDB: " + e.getMessage(), e);
                }
            }
        }

        if (newInstanceID == null || newInstanceID.length() < 1) {
            newInstanceID = DEFAULT_INSTANCE_ID;
        }

        return newInstanceID;
    }

    private static void lastLastLdapFailure(final PwmDB pwmDB, final ContextManager contextManager) {
        if (pwmDB != null) {
            try {
                final String lastLdapFailureStr = pwmDB.get(PwmDB.DB.PWM_META, DB_KEY_LAST_LDAP_ERROR);
                if (lastLdapFailureStr != null && lastLdapFailureStr.length() > 0) {
                    final Gson gson = new Gson();
                    contextManager.lastLdapFailure = gson.fromJson(lastLdapFailureStr, ErrorInformation.class);
                }
            } catch (Exception e) {
                LOGGER.error("error reading lastLdapFailure from pwmDB: " + e.getMessage(), e);
            }
        }
    }

    private static String logEnvironment() {
        final StringBuilder sb = new StringBuilder();
        sb.append("environment info: ");
        sb.append("java.vm.vendor=").append(System.getProperty("java.vm.vendor"));
        sb.append(", java.vm.version=").append(System.getProperty("java.vm.version"));
        sb.append(", java.vm.name=").append(System.getProperty("java.vm.name"));
        sb.append(", java.home=").append(System.getProperty("java.home"));
        sb.append(", memmax=").append(Runtime.getRuntime().maxMemory());
        sb.append(", threads=").append(Thread.activeCount());
        sb.append(", ldapChai API version: ").append(ChaiConstant.CHAI_API_VERSION).append(", b").append(ChaiConstant.CHAI_API_BUILD_INFO);
        return sb.toString();
    }

    private static String logDebugInfo(final int activeSessionCount) {
        final StringBuilder sb = new StringBuilder();
        sb.append("debug info:");
        sb.append(" sessions=").append(activeSessionCount);
        sb.append(", memfree=").append(Runtime.getRuntime().freeMemory());
        sb.append(", memallocd=").append(Runtime.getRuntime().totalMemory());
        sb.append(", memmax=").append(Runtime.getRuntime().maxMemory());
        sb.append(", threads=").append(Thread.activeCount());
        return sb.toString();
    }

    public String logContextParams() {
        final StringBuilder sb = new StringBuilder();
        sb.append("context-params: ");

        for (final PwmConstants.CONTEXT_PARAM contextParam : PwmConstants.CONTEXT_PARAM.values()) {
            final String value = getParameter(contextParam);
            sb.append(contextParam.getKey()).append("=").append(value);
            sb.append(", ");
        }

        sb.delete(sb.length() - 2, sb.length());
        return sb.toString();
    }

    public StatisticsManager getStatisticsManager() {
        return statisticsManager;
    }

    public void sendEmailUsingQueue(final EmailItemBean emailItem) {
        if (emailQueue == null) {
            LOGGER.error("email queue is unavailable, unable to send email: " + emailItem.toString());
            return;
        }

        try {
            emailQueue.addMailToQueue(emailItem);
        } catch (PwmUnrecoverableException e) {
            LOGGER.warn("unable to add email to queue: " + e.getMessage());
        }
    }

    public void sendSmsUsingQueue(final SmsItemBean smsItem) {
        try {
            smsQueue.addSmsToQueue(smsItem);
        } catch (PwmUnrecoverableException e) {
            LOGGER.warn("unable to add sms to queue: " + e.getMessage());
        }
}

    public void shutdown() {
        LOGGER.warn("shutting down");
        AlertHandler.alertShutdown(this);

        if (getStatisticsManager() != null) {
            getStatisticsManager().close();
        }

        if (taskMaster != null) {
            taskMaster.cancel();
            taskMaster = null;
        }

        if (wordlistManager != null) {
            wordlistManager.close();
            wordlistManager = null;
        }

        if (seedlistManager != null) {
            seedlistManager.close();
            seedlistManager = null;
        }

        if (sharedHistoryManager != null) {
            sharedHistoryManager.close();
            sharedHistoryManager = null;
        }

        if (emailQueue != null) {
            emailQueue.close();
            emailQueue = null;
        }

        if (smsQueue != null) {
            smsQueue.close();
            smsQueue = null;
        }

        if (databaseAccessor != null) {
            databaseAccessor.close();
            databaseAccessor = null;
        }

        if (pwmDBLogger != null) {
            pwmDBLogger.close();
            pwmDBLogger = null;
        }

        if (healthMonitor != null) {
            healthMonitor.close();
            healthMonitor = null;
        }

        if (pwmDB != null) {
            try {
                pwmDB.close();
            } catch (Exception e) {
                LOGGER.fatal("error destroying pwm context DB: " + e, e);
            }
            pwmDB = null;
        }

        closeProxyChaiProvider();

        LOGGER.info("PWM " + PwmConstants.SERVLET_VERSION + " closed for bidness, cya!");
    }

    private void closeProxyChaiProvider() {
        if (proxyChaiProvider != null) {
            LOGGER.trace("closing ldap proxy connection");
            final ChaiProvider existingProvider = proxyChaiProvider;
            proxyChaiProvider = null;

            try {
                existingProvider.close();
            } catch (Exception e) {
                LOGGER.error("error closing ldap proxy connection: " + e.getMessage(), e);
            }
        }
    }

    public void addPwmSession(final PwmSession pwmSession) {
        try {
            activeSessions.put(pwmSession, new Object());
        } catch (Exception e) {
            LOGGER.trace("error adding new session to list of known sessions: " + e.getMessage());
        }
    }

    public Date getStartupTime() {
        return startupTime;
    }

    public Date getInstallTime() {
        return installTime;
    }

    public PwmDB getPwmDB() {
        return pwmDB;
    }

    public List<Locale> getKnownLocales() {
        return knownLocales;
    }

// -------------------------- INNER CLASSES --------------------------

    public class DebugLogOutputter extends TimerTask {
        public void run() {
            LOGGER.trace(logDebugInfo(activeSessions.size()));
        }
    }

    public class SessionWatcherTask extends TimerTask {
        public void run() {
            final Map<PwmSession, Object> copiedMap = new HashMap<PwmSession, Object>();

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
        }
    }

    private static class PwmInitializer {
        private static void initializeLogger(final String log4jFilename, final String logLevel, final ServletContext servletContext) {
            // clear all existing package loggers
            final String pwmPackageName = ContextManager.class.getPackage().getName();
            final Logger pwmPackageLogger = Logger.getLogger(pwmPackageName);
            final String chaiPackageName = ChaiUser.class.getPackage().getName();
            final Logger chaiPackageLogger = Logger.getLogger(chaiPackageName);
            pwmPackageLogger.removeAllAppenders();
            chaiPackageLogger.removeAllAppenders();

            Exception configException = null;
            boolean configured = false;

            // try to configure using the log4j config file (if it exists)
            if (log4jFilename != null && log4jFilename.length() > 0) {
                try {
                    final File theFile = ServletHelper.figureFilepath(log4jFilename, "WEB-INF/", servletContext);
                    if (!theFile.exists()) {
                        throw new Exception("file not found: " + theFile.getAbsolutePath());
                    }
                    DOMConfigurator.configure(theFile.getAbsolutePath());
                    LOGGER.debug("successfully initialized log4j using file " + theFile.getAbsolutePath());
                    configured = true;
                } catch (Exception e) {
                    configException = e;
                }
            }

            // if we haven't yet configured log4j for whatever reason, do so using the hardcoded defaults and level (if supplied)
            if (!configured) {
                if (logLevel != null && logLevel.length() > 0) {
                    final Layout patternLayout = new PatternLayout("%d{yyyy-MM-dd HH:mm:ss}, %-5p, %c{2}, %m%n");
                    final ConsoleAppender consoleAppender = new ConsoleAppender(patternLayout);
                    final Level level = Level.toLevel(logLevel);
                    pwmPackageLogger.addAppender(consoleAppender);
                    pwmPackageLogger.setLevel(level);
                    chaiPackageLogger.addAppender(consoleAppender);
                    chaiPackageLogger.setLevel(level);
                    LOGGER.debug("successfully initialized default log4j config at log level " + level.toString());
                } else {
                    LOGGER.debug("skipping stdout log4j initializtion due to blank setting for log level");
                }
            }

            // if there was an exception trying to load the log4j file, then log it (hopefully the defaults worked)
            if (configException != null) {
                LOGGER.error("error loading log4jconfig file '" + log4jFilename + "' error: " + configException.getMessage());
            }
        }

        public static void initializePwmDB(final ContextManager contextManager) {
            final File databaseDirectory;
            // see if META-INF isn't already there, then use WEB-INF.
            try {
                final String pwmDBLocationSetting = contextManager.getConfig().readSettingAsString(PwmSetting.PWMDB_LOCATION);
                databaseDirectory = ServletHelper.figureFilepath(pwmDBLocationSetting, "WEB-INF", contextManager.getServletContext());
            } catch (Exception e) {
                LOGGER.warn("error locating configured pwmDB directory: " + e.getMessage());
                return;
            }

            LOGGER.debug("using pwmDB path " + databaseDirectory);

            // initialize the pwmDB
            try {
                final String classname = contextManager.getConfig().readSettingAsString(PwmSetting.PWMDB_IMPLEMENTATION);
                final List<String> initStrings = contextManager.getConfig().readSettingAsStringArray(PwmSetting.PWMDB_INIT_STRING);
                final Map<String, String> initParamers = Configuration.convertStringListToNameValuePair(initStrings, "=");
                contextManager.pwmDB = PwmDBFactory.getInstance(databaseDirectory, classname, initParamers, false);
            } catch (Exception e) {
                LOGGER.warn("unable to initialize pwmDB: " + e.getMessage());
            }
        }

        public static void initializePwmDBLogger(final ContextManager contextManager) {
            // initialize the pwmDBLogger
            try {
                final int maxEvents = (int) contextManager.getConfig().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_EVENTS);
                final long maxAgeMS = 1000 * contextManager.getConfig().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_AGE);
                final PwmLogLevel localLogLevel = contextManager.getConfig().getEventLogLocalLevel();
                contextManager.pwmDBLogger = PwmLogger.initContextManager(contextManager.pwmDB, maxEvents, maxAgeMS, localLogLevel, contextManager);
            } catch (Exception e) {
                LOGGER.warn("unable to initialize pwmDBLogger: " + e.getMessage());
            }
        }

        public static void initializeHealthMonitor(final ContextManager contextManager) {
            // initialize the pwmDBLogger
            try {
                contextManager.healthMonitor = new HealthMonitor(contextManager);
                contextManager.healthMonitor.registerHealthCheck(new LDAPStatusChecker());
                contextManager.healthMonitor.registerHealthCheck(new JavaChecker());
                contextManager.healthMonitor.registerHealthCheck(new ConfigurationChecker());
                contextManager.healthMonitor.registerHealthCheck(new PwmDBHealthChecker());
            } catch (Exception e) {
                LOGGER.warn("unable to initialize password.pwm.health.HealthMonitor: " + e.getMessage());
            }
        }

        public static void initializeWordlist(final ContextManager contextManager) {

            try {
                LOGGER.trace("opening wordlist");

                final String setting = contextManager.getConfig().readSettingAsString(PwmSetting.WORDLIST_FILENAME);
                final File wordlistFile = setting == null || setting.length() < 1 ? null : ServletHelper.figureFilepath(setting, "WEB-INF", contextManager.servletContext);
                final boolean caseSensitive = contextManager.getConfig().readSettingAsBoolean(PwmSetting.WORDLIST_CASE_SENSITIVE);
                final int loadFactor = Integer.parseInt(contextManager.getParameter(PwmConstants.CONTEXT_PARAM.WORDLIST_LOAD_FACTOR));
                final WordlistConfiguration wordlistConfiguration = new WordlistConfiguration(wordlistFile, loadFactor, caseSensitive);

                contextManager.wordlistManager = WordlistManager.createWordlistManager(
                        wordlistConfiguration,
                        contextManager.pwmDB
                );
            } catch (Exception e) {
                LOGGER.warn("unable to initialize wordlist-db: " + e.getMessage());
            }
        }

        public static void initializeSeedlist(final ContextManager contextManager) {
            try {
                LOGGER.trace("opening seedlist");

                final String setting = contextManager.getConfig().readSettingAsString(PwmSetting.SEEDLIST_FILENAME);
                final File seedlistFile = setting == null || setting.length() < 1 ? null : ServletHelper.figureFilepath(setting, "WEB-INF", contextManager.servletContext);
                final int loadFactor = Integer.parseInt(contextManager.getParameter(PwmConstants.CONTEXT_PARAM.WORDLIST_LOAD_FACTOR));
                final WordlistConfiguration wordlistConfiguration = new WordlistConfiguration(seedlistFile, loadFactor, true);

                contextManager.seedlistManager = SeedlistManager.createSeedlistManager(
                        wordlistConfiguration,
                        contextManager.pwmDB
                );
            } catch (Exception e) {
                LOGGER.warn("unable to initialize seedlist-db: " + e.getMessage());
            }
        }

        public static void initializeSharedHistory(final ContextManager contextManager) {

            try {
                final long maxAgeSeconds = contextManager.getConfig().readSettingAsLong(PwmSetting.PASSWORD_SHAREDHISTORY_MAX_AGE);
                final long maxAgeMS = maxAgeSeconds * 1000;  // convert to MS;
                final boolean caseSensitive = contextManager.getConfig().readSettingAsBoolean(PwmSetting.WORDLIST_CASE_SENSITIVE);

                contextManager.sharedHistoryManager = SharedHistoryManager.createSharedHistoryManager(contextManager.pwmDB, maxAgeMS, caseSensitive);
            } catch (Exception e) {
                LOGGER.warn("unable to initialize sharedhistory-db: " + e.getMessage());
            }
        }

        public static void initializeStatisticsManager(final ContextManager contextManager) {
            final StatisticsManager statisticsManager = new StatisticsManager(contextManager.pwmDB, contextManager);
            statisticsManager.incrementValue(Statistic.PWM_STARTUPS);

            final PwmDB.PwmDBEventListener statsEventListener = new PwmDB.PwmDBEventListener() {
                public void processAction(final PwmDB.PwmDBEvent event) {
                    if (event != null && event.getEventType() != null) {
                        if (event.getEventType() == PwmDB.EventType.READ) {
                            statisticsManager.incrementValue(Statistic.PWMDB_READS);
                            // System.out.println("----pwmDB Read: " + event.getDB() + "," + event.getKey() + "," + event.getValue());
                        } else if (event.getEventType() == PwmDB.EventType.WRITE) {
                            statisticsManager.incrementValue(Statistic.PWMDB_WRITES);
                            // System.out.println("----pwmDB Write: " + event.getDB() + "," + event.getKey() + "," + event.getValue());
                        }
                    }
                }
            };

            if (contextManager.pwmDB != null) {
                contextManager.pwmDB.addEventListener(statsEventListener);
            }

            contextManager.statisticsManager = statisticsManager;
        }

        public static void initializeKnownLocales(final ContextManager contextManager) {
            final List<Locale> returnList = new ArrayList<Locale>();
            final String localeList = contextManager.getParameter(PwmConstants.CONTEXT_PARAM.KNOWN_LOCALES);
            if (localeList != null) {
                final String[] splitLocales = localeList.split(";;;");
                for (final String localeString : splitLocales) {
                    final Locale theLocale = Helper.parseLocaleString(localeString);
                    if (theLocale != null && !returnList.contains(theLocale)) {
                        returnList.add(theLocale);
                    }
                }
            }
            if (!returnList.contains(new Locale(""))) {
                returnList.add(0, new Locale(""));
            }

            contextManager.knownLocales = Collections.unmodifiableList(returnList);
        }
    }

    public ConfigurationReader getConfigReader() {
        return this.configReader;
    }

    private class ConfigFileWatcher extends TimerTask {
        @Override
        public void run() {
            if (configReader != null) {
                if (!restartRequested && configReader.modifiedSinceLoad()) {
                    LOGGER.info("configuration file modification has been detected");
                    EventManager.reinitializeContext(servletContext);
                    restartRequested = true;
                }
            }
        }
    }
}


