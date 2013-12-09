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
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import org.apache.log4j.*;
import org.apache.log4j.xml.DOMConfigurator;
import password.pwm.bean.SmsItemBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.LdapProfile;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.event.AuditEvent;
import password.pwm.event.AuditManager;
import password.pwm.event.SystemAuditRecord;
import password.pwm.health.HealthMonitor;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.token.TokenService;
import password.pwm.util.*;
import password.pwm.util.db.DatabaseAccessor;
import password.pwm.util.intruder.IntruderManager;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBFactory;
import password.pwm.util.operations.CrService;
import password.pwm.ldap.UserDataReader;
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
import password.pwm.util.operations.OtpService;

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
    private static final String DEFAULT_INSTANCE_ID = "-1";


    public enum AppAttribute {
        INSTANCE_ID("context_instanceID"),
        INSTALL_DATE("DB_KEY_INSTALL_DATE"),
        CONFIG_HASH("configurationSettingHash"),
        LAST_LDAP_ERROR("lastLdapError"),
        TOKEN_COUNTER("tokenCounter"),

        ;

        private String key;

        private AppAttribute(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }


    private String instanceID = DEFAULT_INSTANCE_ID;
    private String autoSiteUrl;
    private final Configuration configuration;

    private LocalDB localDB;
    private LocalDBLogger localDBLogger;
    private final Map<String,ChaiProvider> proxyChaiProviders = new HashMap<String, ChaiProvider>();

    private final Map<Class,PwmService> pwmServices = new LinkedHashMap<Class, PwmService>();

    private final Date startupTime = new Date();
    private Date installTime = new Date();
    private ErrorInformation lastLdapFailure = null;
    private ErrorInformation lastLocalDBFailure = null;
    private File pwmApplicationPath; //typically the WEB-INF servlet path

    private MODE applicationMode;

    private static final List<Class> PWM_SERVICE_CLASSES  = Collections.unmodifiableList(Arrays.<Class>asList(
            SharedHistoryManager.class,
            DatabaseAccessor.class,
            HealthMonitor.class,
            AuditManager.class,
            StatisticsManager.class,
            WordlistManager.class,
            SeedlistManager.class,
            EmailQueueManager.class,
            SmsQueueManager.class,
            UrlShortenerService.class,
            TokenService.class,
            VersionChecker.class,
            IntruderManager.class,
            CrService.class,
            UserCacheService.class,
            CrService.class,
            OtpService.class
    ));

    private static final List<Package> LOGGING_PACKAGES  = Collections.unmodifiableList(Arrays.<Package>asList(
            PwmApplication.class.getPackage(),
            ChaiUser.class.getPackage(),
            Package.getPackage("org.jasig.cas.client")
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

    public ChaiUser getProxiedChaiUser(final UserIdentity userIdentity)
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final ChaiProvider proxiedProvider = getProxyChaiProvider(userIdentity.getLdapProfileID());
        return ChaiFactory.createChaiUser(userIdentity.getUserDN(), proxiedProvider);

    }

    public ChaiProvider getProxyChaiProvider(final String identifier)
            throws PwmUnrecoverableException
    {
        final ChaiProvider proxyChaiProvider = proxyChaiProviders.get(identifier == null ? "" : identifier);
        if (proxyChaiProvider != null) {
            return proxyChaiProvider;
        }

        final LdapProfile ldapProfile = getConfig().getLdapProfiles().get(identifier == null ? "" : identifier);
        if (ldapProfile == null) {
            throw new IllegalStateException("unknown ldap profile specified: " + identifier);
        }

        try {
            final ChaiProvider newProvider = LdapOperationsHelper.openProxyChaiProvider(ldapProfile, configuration, getStatisticsManager());
            proxyChaiProviders.put(identifier, newProvider);
            return newProvider;
        } catch (PwmUnrecoverableException e) {
            setLastLdapFailure(e.getErrorInformation());
            throw e;
        }
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
        if (errorInformation == null) {
            writeAppAttribute(AppAttribute.LAST_LDAP_ERROR, null);
        } else {
            final Gson gson = Helper.getGson();
            final String jsonString = gson.toJson(errorInformation);
            writeAppAttribute(AppAttribute.LAST_LDAP_ERROR, jsonString);
        }
    }

    // -------------------------- OTHER METHODS --------------------------


    public TokenService getTokenService() {
        return (TokenService)pwmServices.get(TokenService.class);
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
    {
        return (DatabaseAccessor)pwmServices.get(DatabaseAccessor.class);
    }

    public UserCacheService getUserStatusCacheManager() {
        return (UserCacheService)pwmServices.get(UserCacheService.class);
    }

    private void initialize() {
        final long startTime = System.currentTimeMillis();

        // initialize log4j
        {
            final String log4jFileName = configuration.readSettingAsString(PwmSetting.EVENTS_JAVA_LOG4JCONFIG_FILE);
            final File log4jFile = Helper.figureFilepath(log4jFileName, pwmApplicationPath);
            final String consoleLevel, fileLevel;
            switch (getApplicationMode()) {
                case ERROR:
                case NEW:
                    consoleLevel = PwmLogLevel.TRACE.toString();
                    fileLevel = PwmLogLevel.TRACE.toString();
                    break;

                default:
                    consoleLevel = configuration.readSettingAsString(PwmSetting.EVENTS_JAVA_STDOUT_LEVEL);
                    fileLevel = configuration.readSettingAsString(PwmSetting.EVENTS_FILE_LEVEL);
                    break;
            }

            PwmInitializer.initializeLogger(configuration, log4jFile, consoleLevel, pwmApplicationPath, fileLevel);

            switch (getApplicationMode()) {
                case RUNNING:
                    break;

                case ERROR:
                    LOGGER.fatal("starting up in ERROR mode! Check log or health check information for cause");
                    break;

                default:
                    LOGGER.trace("setting log level to TRACE because application mode is " + getApplicationMode());
                    break;
            }
        }

        PwmInitializer.initializeLocalDB(this);
        PwmInitializer.initializePwmDBLogger(this);

        LOGGER.info("initializing, application mode=" + getApplicationMode());
        // log the loaded configuration
        LOGGER.info("loaded configuration: \n" + configuration.toString());
        LOGGER.info("loaded global password policy: " + configuration.getGlobalPasswordPolicy(PwmConstants.DEFAULT_LOCALE));

        // get the pwm servlet instance id
        instanceID = fetchInstanceID(localDB, this);
        LOGGER.info("using '" + getInstanceID() + "' for instance's ID (instanceID)");

        // read the lastLoginTime
        this.lastLdapFailure = readLastLdapFailure();

        // get the pwm installation date
        installTime = fetchInstallDate(startupTime);
        LOGGER.debug("this application instance first installed on " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(installTime));

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
                final String errorMsg = "unexpected error instantiating service class '" + serviceClass.getName() + "', cannot load, error: " + e.getMessage();
                LOGGER.fatal(errorMsg);
                throw new IllegalStateException(errorMsg,e);
            }
            pwmServices.put(serviceClass,newServiceInstance);
        }

        final TimeDuration totalTime = new TimeDuration(System.currentTimeMillis() - startTime);
        LOGGER.info(PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION + " open for bidness! (" + totalTime.asCompactString() + ")");
        getStatisticsManager().incrementValue(Statistic.PWM_STARTUPS);
        LOGGER.debug("buildTime=" + PwmConstants.BUILD_TIME + ", javaLocale=" + Locale.getDefault() + ", DefaultLocale=" + PwmConstants.DEFAULT_LOCALE );

        // detect if config has been modified since previous startup
        try {
            final String previousHash = readAppAttribute(AppAttribute.CONFIG_HASH);
            final String currentHash = configuration.readProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_SETTING_CHECKSUM);
            if (previousHash == null || !previousHash.equals(currentHash)) {
                writeAppAttribute(AppAttribute.CONFIG_HASH, currentHash);
                LOGGER.warn("configuration has been modified since last startup");
                if (this.getAuditManager() != null) {
                    final String modifyMessage = "configuration was modified directly (not using ConfigEditor UI)";
                    this.getAuditManager().submit(new SystemAuditRecord(
                            AuditEvent.MODIFY_CONFIGURATION,
                            new Date(),
                            modifyMessage,
                            this.getInstanceID()
                    ));
                }
            }
        } catch (Exception e) {
            LOGGER.debug("unable to detect if configuration has been modified since previous startup: " + e.getMessage());
        }

        // send system audit event
        final SystemAuditRecord auditRecord = new SystemAuditRecord(
                AuditEvent.STARTUP,
                new Date(),
                null,
                getInstanceID()
        );
        try {
            getAuditManager().submit(auditRecord);
        } catch (PwmException e) {
            LOGGER.warn("unable to submit alert event " + Helper.getGson().toJson(auditRecord));
        }
    }

    private Date fetchInstallDate(final Date startupTime) {
        if (localDB != null) {
            try {
                final String storedDateStr = readAppAttribute(AppAttribute.INSTALL_DATE);
                if (storedDateStr == null || storedDateStr.length() < 1) {
                    writeAppAttribute(AppAttribute.INSTALL_DATE, String.valueOf(startupTime.getTime()));
                } else {
                    return new Date(Long.parseLong(storedDateStr));
                }
            } catch (Exception e) {
                LOGGER.error("error retrieving installation date from localDB: " + e.getMessage());
            }
        }
        return new Date();
    }

    private String fetchInstanceID(final LocalDB localDB, final PwmApplication pwmApplication) {
        String newInstanceID = pwmApplication.getConfig().readSettingAsString(PwmSetting.PWM_INSTANCE_NAME);

        if (newInstanceID != null && newInstanceID.trim().length() > 0) {
            return newInstanceID;
        }

        newInstanceID = readAppAttribute(AppAttribute.INSTANCE_ID);

        if (newInstanceID == null || newInstanceID.length() < 1) {
            newInstanceID = Long.toHexString(PwmRandom.getInstance().nextLong()).toUpperCase();
            LOGGER.info("generated new random instanceID " + newInstanceID);

            if (localDB != null) {
                writeAppAttribute(AppAttribute.INSTANCE_ID, newInstanceID);
            }
        } else {
            LOGGER.trace("retrieved instanceID " + newInstanceID + "" + " from localDB");
        }

        if (newInstanceID == null || newInstanceID.length() < 1) {
            newInstanceID = DEFAULT_INSTANCE_ID;
        }

        return newInstanceID;
    }

    private ErrorInformation readLastLdapFailure() {
        final String lastLdapFailureStr = readAppAttribute(AppAttribute.LAST_LDAP_ERROR);
        if (lastLdapFailureStr != null && lastLdapFailureStr.length() > 0) {
            final Gson gson = Helper.getGson();
            return gson.fromJson(lastLdapFailureStr, ErrorInformation.class);
        }
        return null;
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

    public OtpService getOtpService() {
        return (OtpService)pwmServices.get(OtpService.class);
    }

    public CrService getCrService() {
        return (CrService)pwmServices.get(CrService.class);
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
        {
            // send system audit event
            final SystemAuditRecord auditRecord = new SystemAuditRecord(
                    AuditEvent.SHUTDOWN,
                    new Date(),
                    null,
                    getInstanceID()
            );
            try {
                getAuditManager().submit(auditRecord);
            } catch (PwmException e) {
                LOGGER.warn("unable to submit alert event " + Helper.getGson().toJson(auditRecord));
            }
        }

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

        LOGGER.info(PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION + " closed for bidness, cya!");
    }

    private void closeProxyChaiProvider() {
        LOGGER.trace("closing ldap proxy connections");
        for (final String id : proxyChaiProviders.keySet()) {
            final ChaiProvider existingProvider = proxyChaiProviders.get(id);

            try {
                existingProvider.close();
            } catch (Exception e) {
                LOGGER.error("error closing ldap proxy connection: " + e.getMessage(), e);
            }
        }
        proxyChaiProviders.clear();
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
                final Configuration config,
                final File log4jConfigFile,
                final String consoleLogLevel,
                final File pwmApplicationPath,
                final String fileLogLevel
        ) {
            // clear all existing package loggers
            for (final Package logPackage : LOGGING_PACKAGES) {
                if (logPackage != null) {
                    final Logger logger = Logger.getLogger(logPackage.getName());
                    logger.removeAllAppenders();
                    logger.setLevel(Level.TRACE);
                }
            }

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
                final Layout patternLayout = new PatternLayout(config.readAppProperty(AppProperty.LOGGING_PATTERN));

                // configure console logging
                if (consoleLogLevel != null && consoleLogLevel.length() > 0 && !"Off".equals(consoleLogLevel)) {
                    final ConsoleAppender consoleAppender = new ConsoleAppender(patternLayout);
                    final Level level = Level.toLevel(consoleLogLevel);
                    consoleAppender.setThreshold(level);
                    for (final Package logPackage : LOGGING_PACKAGES) {
                        if (logPackage != null) {
                            final Logger logger = Logger.getLogger(logPackage.getName());
                            logger.addAppender(consoleAppender);
                        }
                    }
                    LOGGER.debug("successfully initialized default console log4j config at log level " + level.toString());
                } else {
                    LOGGER.debug("skipping stdout log4j initialization due to blank setting for log level");
                }

                // configure file logging
                final String logDirectorySetting = config.readAppProperty(AppProperty.LOGGING_FILE_PATH);
                final File logDirectory = Helper.figureFilepath(logDirectorySetting,pwmApplicationPath);

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
                        fileAppender.setMaxBackupIndex(Integer.parseInt(config.readAppProperty(AppProperty.LOGGING_FILE_MAX_ROLLOVER)));
                        fileAppender.setMaxFileSize(config.readAppProperty(AppProperty.LOGGING_FILE_MAX_SIZE));
                        for (final Package logPackage : LOGGING_PACKAGES) {
                            if (logPackage != null) {
                                final Logger logger = Logger.getLogger(logPackage.getName());
                                logger.addAppender(fileAppender);
                            }
                        }
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
                final boolean readOnly = pwmApplication.getApplicationMode() == MODE.READ_ONLY;
                pwmApplication.localDB = LocalDBFactory.getInstance(databaseDirectory, readOnly, pwmApplication, pwmApplication.getConfig());
            } catch (Exception e) {
                pwmApplication.lastLocalDBFailure = new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,"unable to initialize LocalDB: " + e.getMessage());
                LOGGER.warn(pwmApplication.lastLocalDBFailure.toDebugStr());
            }
        }

        public static void initializePwmDBLogger(final PwmApplication pwmApplication) {
            if (pwmApplication.getApplicationMode() == MODE.READ_ONLY) {
                LOGGER.trace("skipping initialization of LocalDBLogger due to read-only mode");
                return;
            }

            // initialize the localDBLogger
            final PwmLogLevel localLogLevel = pwmApplication.getConfig().getEventLogLocalDBLevel();
            try {
                final int maxEvents = (int) pwmApplication.getConfig().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_EVENTS);
                final long maxAgeMS = 1000 * pwmApplication.getConfig().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_AGE);
                pwmApplication.localDBLogger = PwmLogger.initPwmApplication(pwmApplication.localDB, maxEvents, maxAgeMS, localLogLevel, pwmApplication);
            } catch (Exception e) {
                LOGGER.warn("unable to initialize localDBLogger: " + e.getMessage());
            }

            // add appender for other packages;
            try {
                final LocalDBLog4jAppender localDBLog4jAppender = new LocalDBLog4jAppender(pwmApplication.localDBLogger);
                for (final Package logPackage : LOGGING_PACKAGES) {
                    if (logPackage != null && !logPackage.equals(PwmApplication.class.getPackage())) {
                        final Logger logger = Logger.getLogger(logPackage.getName());
                        logger.addAppender(localDBLog4jAppender);
                        logger.setLevel(localLogLevel.getLog4jLevel());
                    }
                }
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

    public String getInstanceNonce() {
        return Long.toString(getStartupTime().getTime(),36);
    }

    public String readAppAttribute(final AppAttribute appAttribute) {
        if (localDB == null || localDB.status() != LocalDB.Status.OPEN) {
            LOGGER.error("error retrieving key '" + appAttribute.getKey() + "', localDB unavailable: ");
            return null;
        }

        if (appAttribute == null) {
            return null;
        }

        try {
            return localDB.get(LocalDB.DB.PWM_META, appAttribute.getKey());
        } catch (Exception e) {
            LOGGER.error("error retrieving key '" + appAttribute.getKey() + "' installation date from localDB: " + e.getMessage());
        }
        return null;
    }

    public void writeAppAttribute(final AppAttribute appAttribute, final String value) {
        if (localDB == null || localDB.status() != LocalDB.Status.OPEN) {
            LOGGER.error("error writing key '" + appAttribute.getKey() + "', localDB unavailable: ");
            return;
        }

        if (appAttribute == null) {
            return;
        }

        try {
            if (value == null) {
                localDB.remove(LocalDB.DB.PWM_META, appAttribute.getKey());
            } else {
                localDB.put(LocalDB.DB.PWM_META, appAttribute.getKey(), value);
            }
        } catch (Exception e) {
            LOGGER.error("error retrieving key '" + appAttribute.getKey() + "' installation date from localDB: " + e.getMessage());
        }
    }
}


