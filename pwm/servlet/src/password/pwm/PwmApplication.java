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

import com.google.gson.Gson;
import com.novell.ldapchai.ChaiConstant;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import org.apache.log4j.*;
import org.apache.log4j.xml.DOMConfigurator;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SmsItemBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.event.AuditManager;
import password.pwm.health.HealthMonitor;
import password.pwm.util.*;
import password.pwm.util.db.DatabaseAccessor;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.localdb.LocalDBFactory;
import password.pwm.util.operations.CrService;
import password.pwm.util.operations.UserDataReader;
import password.pwm.util.queue.EmailQueueManager;
import password.pwm.util.queue.SmsQueueManager;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;
import password.pwm.wordlist.SeedlistManager;
import password.pwm.wordlist.SharedHistoryManager;
import password.pwm.wordlist.WordlistManager;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.*;

/**
 * A repository for objects common to the servlet context.  A singleton
 * of this object is stored in the servlet context.
 *
 * @author Jason D. Rivard
 */
public class PwmApplication {
// ------------------------------ FIELDS ------------------------------

    // ----------------------------- CONSTANTS ----------------------------
    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmApplication.class);
    private static final String DB_KEY_INSTANCE_ID = "context_instanceID";
    private static final String DB_KEY_CONFIG_SETTING_HASH = "configurationSettingHash";
    private static final String DB_KEY_INSTALL_DATE = "DB_KEY_INSTALL_DATE";
    private static final String DB_KEY_LAST_LDAP_ERROR = "lastLdapError";
    private static final String DEFAULT_INSTANCE_ID = "-1";


    private String instanceID = DEFAULT_INSTANCE_ID;
    private String autoSiteUrl;
    private final Configuration configuration;

    private LocalDB localDB;
    private LocalDBLogger localDBLogger;
    private volatile ChaiProvider proxyChaiProvider;

    private final Map<Class,PwmService> pwmServices = new LinkedHashMap<Class, PwmService>();

    private final Date startupTime = new Date();
    private Date installTime = new Date();
    private ErrorInformation lastLdapFailure = null;
    private ErrorInformation lastLocalDBFailure = null;
    private File pwmApplicationPath; //typically the WEB-INF servlet path

    private MODE applicationMode;

    private static final List<Class> PWM_SERVICE_CLASSES  = Collections.unmodifiableList(Arrays.<Class>asList(SharedHistoryManager.class,
            DatabaseAccessor.class,
            HealthMonitor.class,
            AuditManager.class,
            StatisticsManager.class,
            WordlistManager.class,
            SeedlistManager.class,
            EmailQueueManager.class,
            SmsQueueManager.class,
            UrlShortenerService.class,
            TokenManager.class,
            VersionChecker.class,
            IntruderManager.class,
            CrService.class
    ));


// -------------------------- STATIC METHODS --------------------------

    // --------------------------- CONSTRUCTORS ---------------------------

    public PwmApplication(final Configuration config, final MODE applicationMode, final File pwmApplicationPath)
    {
        this.configuration = config;
        this.applicationMode = applicationMode;
        this.pwmApplicationPath = pwmApplicationPath;
        initialize();
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getInstanceID() {
        return instanceID;
    }

    public SharedHistoryManager getSharedHistoryManager() {
        return (SharedHistoryManager)pwmServices.get(SharedHistoryManager.class);
    }

    public IntruderManager getIntruderManager() {
        return (IntruderManager)pwmServices.get(IntruderManager.class);
    }

    public ChaiProvider getProxyChaiProvider()
            throws PwmUnrecoverableException {
        if (proxyChaiProvider == null) {
            try {
                proxyChaiProvider = openProxyChaiProvider(configuration, getStatisticsManager());
            } catch (PwmUnrecoverableException e) {
                setLastLdapFailure(e.getErrorInformation());
                throw e;
            }
        }
        return proxyChaiProvider;
    }

    public LocalDBLogger getLocalDBLogger() {
        return localDBLogger;
    }

    public HealthMonitor getHealthMonitor() {
        return (HealthMonitor)pwmServices.get(HealthMonitor.class);
    }

    public List<PwmService> getPwmServices() {
        final List<PwmService> pwmServices = new ArrayList<PwmService>();
        pwmServices.add(this.localDBLogger);
        pwmServices.addAll(this.pwmServices.values());
        pwmServices.remove(null);
        return Collections.unmodifiableList(pwmServices);
    }

    private static ChaiProvider openProxyChaiProvider(final Configuration config, final StatisticsManager statsMangager)
            throws PwmUnrecoverableException
    {
        final StringBuilder debugLogText = new StringBuilder();
        debugLogText.append("opening new ldap proxy connection");
        LOGGER.trace(debugLogText.toString());

        final String proxyDN = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
        final String proxyPW = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD);

        try {
            final int idleTimeoutMs = PwmConstants.LDAP_PROXY_CONNECTION_TIMEOUT;
            return Helper.createChaiProvider(config, proxyDN, proxyPW, idleTimeoutMs);
        } catch (ChaiUnavailableException e) {
            if (statsMangager != null) {
                statsMangager.incrementValue(Statistic.LDAP_UNAVAILABLE_COUNT);
            }
            final StringBuilder errorMsg = new StringBuilder();
            errorMsg.append(" error connecting as proxy user: ");
            final PwmError pwmError = PwmError.forChaiError(e.getErrorCode());
            if (pwmError != null && pwmError != PwmError.ERROR_UNKNOWN) {
                errorMsg.append(new ErrorInformation(pwmError,e.getMessage()).toDebugStr());
            } else {
                errorMsg.append(e.getMessage());
            }
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,errorMsg.toString());
            LOGGER.fatal("check ldap proxy settings: " + errorInformation.toDebugStr());
            throw new PwmUnrecoverableException(errorInformation);
        }
    }

    public WordlistManager getWordlistManager() {
        return (WordlistManager)pwmServices.get(WordlistManager.class);
    }

    public SeedlistManager getSeedlistManager() {
        return (SeedlistManager)pwmServices.get(SeedlistManager.class);
    }

    public EmailQueueManager getEmailQueue() {
        return (EmailQueueManager)pwmServices.get(EmailQueueManager.class);
    }

    public AuditManager getAuditManager() {
        return (AuditManager)pwmServices.get(AuditManager.class);
    }

    public SmsQueueManager getSmsQueue() {
        return (SmsQueueManager)pwmServices.get(SmsQueueManager.class);
    }

    public UrlShortenerService getUrlShortener() {
        return (UrlShortenerService)pwmServices.get(UrlShortenerService.class);
    }

    public VersionChecker getVersionChecker() {
        return (VersionChecker)pwmServices.get(VersionChecker.class);
    }

    public ErrorInformation getLastLdapFailure() {
        return lastLdapFailure;
    }

    public ErrorInformation getLastLocalDBFailure() {
        return lastLocalDBFailure;
    }

    public void setLastLdapFailure(final ErrorInformation errorInformation) {
        this.lastLdapFailure = errorInformation;
        if (localDB != null && localDB.status() == LocalDB.Status.OPEN) {
            try {
                if (errorInformation == null) {
                    localDB.remove(LocalDB.DB.PWM_META, DB_KEY_LAST_LDAP_ERROR);
                } else {
                    final Gson gson = new Gson();
                    final String jsonString = gson.toJson(errorInformation);
                    localDB.put(LocalDB.DB.PWM_META, DB_KEY_LAST_LDAP_ERROR, jsonString);
                }
            } catch (LocalDBException e) {
                LOGGER.error("error writing lastLdapFailure time to localDB: " + e.getMessage());
            }
        }
    }

    // -------------------------- OTHER METHODS --------------------------


    public TokenManager getTokenManager() {
        return (TokenManager)pwmServices.get(TokenManager.class);
    }

    public Configuration getConfig() {
        if (configuration == null) {
            return null;
        }
        return configuration;
    }

    public MODE getApplicationMode() {
        return applicationMode;
    }

    public synchronized DatabaseAccessor getDatabaseAccessor()
            throws PwmUnrecoverableException
    {
        return (DatabaseAccessor)pwmServices.get(DatabaseAccessor.class);
    }

    private void initialize() {
        final long startTime = System.currentTimeMillis();

        // initialize log4j
        {
            final String log4jFileName = configuration.readSettingAsString(PwmSetting.EVENTS_JAVA_LOG4JCONFIG_FILE);
            final File log4jFile = Helper.figureFilepath(log4jFileName, pwmApplicationPath);
            final File log4jAppenderFolder = Helper.figureFilepath("logs",pwmApplicationPath);
            final String consoleLevel, fileLevel;
            switch (getApplicationMode()) {
                case ERROR:
                    consoleLevel = PwmLogLevel.TRACE.toString();
                    fileLevel = PwmLogLevel.TRACE.toString();
                    break;

                default:
                    consoleLevel = configuration.readSettingAsString(PwmSetting.EVENTS_JAVA_STDOUT_LEVEL);
                    fileLevel = configuration.readSettingAsString(PwmSetting.EVENTS_FILE_LEVEL);
                    break;
            }

            PwmInitializer.initializeLogger(log4jFile, consoleLevel, log4jAppenderFolder, fileLevel);

            switch (getApplicationMode()) {
                case RUNNING:
                    break;

                case ERROR:
                    LOGGER.fatal("starting up in ERROR mode! Check log or health check information for cause");
                    break;

                default:
                    LOGGER.trace("setting log level to TRACE because mode is not RUNNING.");
                    break;
            }
        }

        PwmInitializer.initializeLocalDB(this);
        PwmInitializer.initializePwmDBLogger(this);

        LOGGER.info("initializing pwm");
        // log the loaded configuration
        LOGGER.info("loaded configuration: \n" + configuration.toString());
        LOGGER.info("loaded pwm global password policy: " + configuration.getGlobalPasswordPolicy(PwmConstants.DEFAULT_LOCALE));

        // get the pwm servlet instance id
        instanceID = fetchInstanceID(localDB, this);
        LOGGER.info("using '" + getInstanceID() + "' for instance's ID (instanceID)");

        // read the lastLoginTime
        lastLastLdapFailure(localDB, this);

        // get the pwm installation date
        installTime = fetchInstallDate(localDB, startupTime);
        LOGGER.debug("this pwm instance first installed on " + installTime.toString());

        LOGGER.info(logEnvironment());
        LOGGER.info(logDebugInfo());

        for (final Class serviceClass : PWM_SERVICE_CLASSES) {
            final PwmService newServiceInstance;
            try {
                final Object newInstance = serviceClass.newInstance();
                newServiceInstance = (PwmService)newInstance;
            } catch (Exception e) {
                final String errorMsg = "unexpected error instantiating service class '" + serviceClass.getName() + "', error: " + e.toString();
                LOGGER.fatal(errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            try {
                LOGGER.debug("initializing service " + serviceClass.getName());
                newServiceInstance.init(this);
                LOGGER.debug("initialization of service " + serviceClass.getName() + " has completed successfully");
            } catch (PwmException e) {
                LOGGER.warn("error instantiating service class '" + serviceClass.getName() + "', service will remain unavailable, error: " + e.getMessage());
            } catch (Exception e) {
                final String errorMsg = "unexpected error instantiating service class '" + serviceClass.getName() + "', pwm cannot load, error: " + e.getMessage();
                LOGGER.fatal(errorMsg);
                throw new IllegalStateException(errorMsg,e);
            }
            pwmServices.put(serviceClass,newServiceInstance);
        }

        final TimeDuration totalTime = new TimeDuration(System.currentTimeMillis() - startTime);
        LOGGER.info(PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION + " open for bidness! (" + totalTime.asCompactString() + ")");
        getStatisticsManager().incrementValue(Statistic.PWM_STARTUPS);
        LOGGER.debug("buildTime=" + PwmConstants.BUILD_TIME + ", javaLocale=" + Locale.getDefault() + ", pwmDefaultLocale=" + PwmConstants.DEFAULT_LOCALE );

        // detect if config has been modified since previous startup
        try {
            if (localDB != null) {
                final String previousHash = localDB.get(LocalDB.DB.PWM_META, DB_KEY_CONFIG_SETTING_HASH);
                final String currentHash = configuration.readProperty(StoredConfiguration.PROPERTY_KEY_SETTING_CHECKSUM);
                if (previousHash == null || !previousHash.equals(currentHash)) {
                    localDB.put(LocalDB.DB.PWM_META, DB_KEY_CONFIG_SETTING_HASH, currentHash);
                    LOGGER.warn("pwm configuration has been modified since last startup");
                    AlertHandler.alertConfigModify(this, configuration);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("unable to detect if configuration has been modified since previous startup: " + e.getMessage());
        }

        AlertHandler.alertStartup(this);

    }

    private static Date fetchInstallDate(final LocalDB localDB, final Date startupTime) {
        if (localDB != null) {
            try {
                final String storedDateStr = localDB.get(LocalDB.DB.PWM_META, DB_KEY_INSTALL_DATE);
                if (storedDateStr == null || storedDateStr.length() < 1) {
                    localDB.put(LocalDB.DB.PWM_META, DB_KEY_INSTALL_DATE, String.valueOf(startupTime.getTime()));
                } else {
                    return new Date(Long.parseLong(storedDateStr));
                }
            } catch (Exception e) {
                LOGGER.error("error retrieving installation date from localDB: " + e.getMessage());
            }
        }
        return new Date();
    }

    private static String fetchInstanceID(final LocalDB localDB, final PwmApplication pwmApplication) {
        String newInstanceID = pwmApplication.getConfig().readSettingAsString(PwmSetting.PWM_INSTANCE_NAME);

        if (newInstanceID != null && newInstanceID.trim().length() > 0) {
            return newInstanceID;
        }

        if (localDB != null) {
            try {
                newInstanceID = localDB.get(LocalDB.DB.PWM_META, DB_KEY_INSTANCE_ID);
                LOGGER.trace("retrieved instanceID " + newInstanceID + "" + " from localDB");
            } catch (Exception e) {
                LOGGER.warn("error retrieving instanceID from localDB: " + e.getMessage(), e);
            }
        }

        if (newInstanceID == null || newInstanceID.length() < 1) {
            newInstanceID = Long.toHexString(PwmRandom.getInstance().nextLong()).toUpperCase();
            LOGGER.info("generated new random instanceID " + newInstanceID);

            if (localDB != null) {
                try {
                    localDB.put(LocalDB.DB.PWM_META, DB_KEY_INSTANCE_ID, String.valueOf(newInstanceID));
                    LOGGER.debug("saved instanceID " + newInstanceID + "" + " to localDB");
                } catch (Exception e) {
                    LOGGER.warn("error saving instanceID to localDB: " + e.getMessage(), e);
                }
            }
        }

        if (newInstanceID == null || newInstanceID.length() < 1) {
            newInstanceID = DEFAULT_INSTANCE_ID;
        }

        return newInstanceID;
    }

    private static void lastLastLdapFailure(final LocalDB localDB, final PwmApplication pwmApplication) {
        if (localDB != null) {
            try {
                final String lastLdapFailureStr = localDB.get(LocalDB.DB.PWM_META, DB_KEY_LAST_LDAP_ERROR);
                if (lastLdapFailureStr != null && lastLdapFailureStr.length() > 0) {
                    final Gson gson = new Gson();
                    pwmApplication.lastLdapFailure = gson.fromJson(lastLdapFailureStr, ErrorInformation.class);
                }
            } catch (Exception e) {
                LOGGER.error("error reading lastLdapFailure from localDB: " + e.getMessage(), e);
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

    private static String logDebugInfo() {
        final StringBuilder sb = new StringBuilder();
        sb.append("debug info:");
        sb.append(", memfree=").append(Runtime.getRuntime().freeMemory());
        sb.append(", memallocd=").append(Runtime.getRuntime().totalMemory());
        sb.append(", memmax=").append(Runtime.getRuntime().maxMemory());
        sb.append(", threads=").append(Thread.activeCount());
        return sb.toString();
    }

    public StatisticsManager getStatisticsManager() {
        return (StatisticsManager)pwmServices.get(StatisticsManager.class);
    }

    public CrService getCrService() {
        return (CrService)pwmServices.get(CrService.class);
    }

    public void sendEmailUsingQueue(final EmailItemBean emailItem, final UserInfoBean uiBean, final UserDataReader userDataReader) {
        final EmailQueueManager emailQueue = this.getEmailQueue();
        if (emailQueue == null) {
            LOGGER.error("email queue is unavailable, unable to send email: " + emailItem.toString());
            return;
        }

        final EmailItemBean expandedEmailItem = new EmailItemBean(
                MacroMachine.expandMacros(emailItem.getTo(), this, uiBean, userDataReader),
                MacroMachine.expandMacros(emailItem.getFrom(), this, uiBean, userDataReader),
                MacroMachine.expandMacros(emailItem.getSubject(), this, uiBean, userDataReader),
                MacroMachine.expandMacros(emailItem.getBodyPlain(), this, uiBean, userDataReader),
                MacroMachine.expandMacros(emailItem.getBodyHtml(), this, uiBean, userDataReader)
        );

        try {
            emailQueue.addMailToQueue(expandedEmailItem);
        } catch (PwmUnrecoverableException e) {
            LOGGER.warn("unable to add email to queue: " + e.getMessage());
        }
    }

    public void sendSmsUsingQueue(final SmsItemBean smsItem, final UserInfoBean uiBean, final UserDataReader userDataReader) {
        final SmsQueueManager smsQueue = getSmsQueue();
        if (smsQueue == null) {
            LOGGER.error("SMS queue is unavailable, unable to send SMS: " + smsItem.toString());
            return;
        }

        final SmsItemBean rewrittenSmsItem = new SmsItemBean(
                MacroMachine.expandMacros(smsItem.getTo(), this, uiBean, userDataReader),
                MacroMachine.expandMacros(smsItem.getFrom(), this, uiBean, userDataReader),
                MacroMachine.expandMacros(smsItem.getMessage(), this, uiBean, userDataReader),
                smsItem.getPartlength()
        );

        try {
            smsQueue.addSmsToQueue(rewrittenSmsItem);
        } catch (PwmUnrecoverableException e) {
            LOGGER.warn("unable to add sms to queue: " + e.getMessage());
        }
    }

    public void shutdown() {
        LOGGER.warn("shutting down");
        AlertHandler.alertShutdown(this);

        {
            final List<Class> reverseServiceList = new ArrayList<Class>(PWM_SERVICE_CLASSES);
            Collections.reverse(reverseServiceList);
            for (final Class serviceClass : reverseServiceList) {
                if (pwmServices.containsKey(serviceClass)) {
                    LOGGER.trace("closing service " + serviceClass.getName());
                    final PwmService loopService = pwmServices.get(serviceClass);
                    LOGGER.trace("successfully closed service " + serviceClass.getName());
                    try {
                        loopService.close();
                    } catch (Exception e) {
                        LOGGER.error("error closing " + loopService.getClass().getSimpleName() + ": " + e.getMessage(),e);
                    }
                }
            }
        }

        if (localDBLogger != null) {
            try {
                localDBLogger.close();
            } catch (Exception e) {
                LOGGER.error("error closing localDBLogger: " + e.getMessage(),e);
            }
            localDBLogger = null;
        }

        if (localDB != null) {
            try {
                localDB.close();
            } catch (Exception e) {
                LOGGER.fatal("error closing localDB: " + e, e);
            }
            localDB = null;
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


    public Date getStartupTime() {
        return startupTime;
    }

    public Date getInstallTime() {
        return installTime;
    }

    public LocalDB getLocalDB() {
        return localDB;
    }

// -------------------------- INNER CLASSES --------------------------

    private static class PwmInitializer {
        private static void initializeLogger(
                final File log4jConfigFile,
                final String consoleLogLevel,
                final File logDirectory,
                final String fileLogLevel
        ) {
            // clear all existing package loggers
            final String pwmPackageName = PwmApplication.class.getPackage().getName();
            final Logger pwmPackageLogger = Logger.getLogger(pwmPackageName);
            final String chaiPackageName = ChaiUser.class.getPackage().getName();
            final Logger chaiPackageLogger = Logger.getLogger(chaiPackageName);
            final String casPackageName = "org.jasig.cas.client";
            final Logger casPackageLogger = Logger.getLogger(casPackageName);
            pwmPackageLogger.removeAllAppenders();
            chaiPackageLogger.removeAllAppenders();
            casPackageLogger.removeAllAppenders();
            pwmPackageLogger.setLevel(Level.TRACE);
            chaiPackageLogger.setLevel(Level.TRACE);
            casPackageLogger.setLevel(Level.TRACE);

            Exception configException = null;
            boolean configured = false;

            // try to configure using the log4j config file (if it exists)
            if (log4jConfigFile != null) {
                try {
                    if (!log4jConfigFile.exists()) {
                        throw new Exception("file not found: " + log4jConfigFile.getAbsolutePath());
                    }
                    DOMConfigurator.configure(log4jConfigFile.getAbsolutePath());
                    LOGGER.debug("successfully initialized log4j using file " + log4jConfigFile.getAbsolutePath());
                    configured = true;
                } catch (Exception e) {
                    configException = e;
                }
            }

            // if we haven't yet configured log4j for whatever reason, do so using the hardcoded defaults and level (if supplied)
            if (!configured) {
                final Layout patternLayout = new PatternLayout(PwmConstants.LOGGING_PATTERN);

                // configure console logging
                if (consoleLogLevel != null && consoleLogLevel.length() > 0 && !"Off".equals(consoleLogLevel)) {
                    final ConsoleAppender consoleAppender = new ConsoleAppender(patternLayout);
                    final Level level = Level.toLevel(consoleLogLevel);
                    consoleAppender.setThreshold(level);
                    pwmPackageLogger.addAppender(consoleAppender);
                    chaiPackageLogger.addAppender(consoleAppender);
                    casPackageLogger.addAppender(consoleAppender);
                    LOGGER.debug("successfully initialized default console log4j config at log level " + level.toString());
                } else {
                    LOGGER.debug("skipping stdout log4j initialization due to blank setting for log level");
                }

                // configure file logging
                if (logDirectory != null && fileLogLevel != null && fileLogLevel.length() > 0 && !"Off".equals(fileLogLevel)) {
                    try {
                        if (!logDirectory.exists()) {
                            if (logDirectory.mkdir()) {
                                LOGGER.info("created directory " + logDirectory.getAbsoluteFile());
                            } else {
                                throw new IOException("failed to create directory " + logDirectory.getAbsoluteFile());
                            }
                        }

                        final String fileName = logDirectory.getAbsolutePath() + File.separator + PwmConstants.PWM_APP_NAME + ".log";
                        final RollingFileAppender fileAppender = new RollingFileAppender(patternLayout,fileName,true);
                        final Level level = Level.toLevel(fileLogLevel);
                        fileAppender.setThreshold(level);
                        fileAppender.setMaxBackupIndex(PwmConstants.LOGGING_FILE_MAX_ROLLOVER);
                        fileAppender.setMaxFileSize(PwmConstants.LOGGING_FILE_MAX_SIZE);
                        pwmPackageLogger.addAppender(fileAppender);
                        chaiPackageLogger.addAppender(fileAppender);
                        casPackageLogger.addAppender(fileAppender);
                        LOGGER.debug("successfully initialized default file log4j config at log level " + level.toString());
                    } catch (IOException e) {
                        LOGGER.debug("error initializing RollingFileAppender: " + e.getMessage());
                    }
                }
            }

            // if there was an exception trying to load the log4j file, then log it (hopefully the defaults worked)
            if (configException != null) {
                LOGGER.error("error loading log4jconfig file '" + log4jConfigFile + "' error: " + configException.getMessage());
            }

            // disable jersey warnings.
            java.util.logging.LogManager.getLogManager().addLogger(java.util.logging.Logger.getLogger("com.sun.jersey.spi.container.servlet.WebComponent"));
            java.util.logging.LogManager.getLogManager().getLogger("com.sun.jersey.spi.container.servlet.WebComponent").setLevel(java.util.logging.Level.OFF);
        }

        public static void initializeLocalDB(final PwmApplication pwmApplication) {
            if (pwmApplication.getApplicationMode() == MODE.ERROR || pwmApplication.getApplicationMode() == MODE.NEW) {
                LOGGER.warn("skipping LocalDB open due to application mode " + pwmApplication.getApplicationMode());
                return;
            }

            final File databaseDirectory;
            // see if META-INF isn't already there, then use WEB-INF.
            try {
                final String pwmDBLocationSetting = pwmApplication.getConfig().readSettingAsString(PwmSetting.PWMDB_LOCATION);
                databaseDirectory = Helper.figureFilepath(pwmDBLocationSetting, pwmApplication.pwmApplicationPath);
            } catch (Exception e) {
                pwmApplication.lastLocalDBFailure = new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,"error locating configured LocalDB directory: " + e.getMessage());
                LOGGER.warn(pwmApplication.lastLocalDBFailure.toDebugStr());
                return;
            }

            LOGGER.debug("using localDB path " + databaseDirectory);

            // initialize the localDB
            try {
                final String classname = pwmApplication.getConfig().readSettingAsString(PwmSetting.PWMDB_IMPLEMENTATION);
                final List<String> initStrings = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.PWMDB_INIT_STRING);
                final Map<String, String> initParamers = Configuration.convertStringListToNameValuePair(initStrings, "=");
                final boolean readOnly = pwmApplication.getApplicationMode() == MODE.READ_ONLY;
                pwmApplication.localDB = LocalDBFactory.getInstance(databaseDirectory, classname, initParamers, readOnly, pwmApplication);
            } catch (Exception e) {
                pwmApplication.lastLocalDBFailure = new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,"unable to initialize LocalDB: " + e.getMessage());
                LOGGER.warn(pwmApplication.lastLocalDBFailure.toDebugStr());
            }
        }

        public static void initializePwmDBLogger(final PwmApplication pwmApplication) {
            if (pwmApplication.getApplicationMode() == MODE.READ_ONLY) {
                LOGGER.trace("skipping localDBLogger due to read-only mode");
                return;
            }

            // initialize the localDBLogger
            final PwmLogLevel localLogLevel = pwmApplication.getConfig().getEventLogLocalLevel();
            try {
                final int maxEvents = (int) pwmApplication.getConfig().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_EVENTS);
                final long maxAgeMS = 1000 * pwmApplication.getConfig().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_AGE);
                pwmApplication.localDBLogger = PwmLogger.initPwmApplication(pwmApplication.localDB, maxEvents, maxAgeMS, localLogLevel, pwmApplication);
            } catch (Exception e) {
                LOGGER.warn("unable to initialize localDBLogger: " + e.getMessage());
            }

            // add appender for other packages;
            try {
                final String chaiPackageName = ChaiUser.class.getPackage().getName();
                final Logger chaiPackageLogger = Logger.getLogger(chaiPackageName);
                final String casPackageName = "org.jasig.cas.client";
                final Logger casPackageLogger = Logger.getLogger(casPackageName);
                final LocalDBLog4jAppender localDBLog4jAppender = new LocalDBLog4jAppender(pwmApplication.localDBLogger);
                chaiPackageLogger.addAppender(localDBLog4jAppender);
                casPackageLogger.setLevel(localLogLevel.getLog4jLevel());
                chaiPackageLogger.addAppender(localDBLog4jAppender);
                casPackageLogger.setLevel(localLogLevel.getLog4jLevel());
            } catch (Exception e) {
                LOGGER.warn("unable to initialize localDBLogger/extraAppender: " + e.getMessage());
            }

        }
    }

    public File getPwmApplicationPath() {
        return pwmApplicationPath;
    }

    public enum MODE {
        NEW,
        CONFIGURATION,
        RUNNING,
        READ_ONLY,
        ERROR
    }

    public String getSiteURL() {
        final String configuredURL = configuration.readSettingAsString(PwmSetting.PWM_URL);
        if (configuredURL == null || configuredURL.length() < 1) {
            return autoSiteUrl == null ? PwmConstants.UNCONFIGURED_URL_VALUE : autoSiteUrl;
        }
        return configuredURL;
    }

    public void setAutoSiteURL(final HttpServletRequest request) {
        if (autoSiteUrl == null && request != null) {
            try {
                final URL url = new URL(request.getRequestURL().toString());

                final String hostname = url.getHost();

                //ignore localhost;
                if (hostname.equalsIgnoreCase("localhost") || hostname.equalsIgnoreCase("127.0.0.1")) {
                    //LOGGER.debug("ignoring loopback host during autoSiteURL detection: " + url.toString());
                    return;
                }

                { //ignore if numeric
                    try {
                        InetAddress inetAddress = InetAddress.getByName(hostname);
                        if (hostname.equals(inetAddress.getHostAddress())) {
                            return;
                        }
                    } catch (UnknownHostException e) {
                        /* noop */
                        //LOGGER.debug("exception examining hostname as siteURL candidate: " + e.getMessage());
                    }
                }

                final StringBuilder sb = new StringBuilder();
                sb.append(url.getProtocol());
                sb.append("://");
                sb.append(url.getHost());
                if (url.getPort() != -1) {
                    sb.append(":");
                    sb.append(url.getPort());
                }
                sb.append(request.getSession().getServletContext().getContextPath());

                autoSiteUrl = sb.toString();
                LOGGER.debug("autoSiteURL detected as: " + autoSiteUrl);

            } catch (MalformedURLException e) {
                LOGGER.error("unexpected malformed url error trying to set autoSiteURL: " + e.getMessage());
            }
        }
    }


}


