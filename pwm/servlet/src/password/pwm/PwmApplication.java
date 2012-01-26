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
import password.pwm.health.HealthMonitor;
import password.pwm.util.*;
import password.pwm.util.db.DatabaseAccessor;
import password.pwm.util.pwmdb.PwmDB;
import password.pwm.util.pwmdb.PwmDBException;
import password.pwm.util.pwmdb.PwmDBFactory;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;
import password.pwm.wordlist.SeedlistManager;
import password.pwm.wordlist.SharedHistoryManager;
import password.pwm.wordlist.WordlistManager;

import java.io.File;
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
    private final IntruderManager intruderManager = new IntruderManager(this);
    private final Configuration configuration;

    private Timer taskMaster;
    private PwmDB pwmDB;
    private PwmDBLogger pwmDBLogger;
    private volatile ChaiProvider proxyChaiProvider;
    private volatile DatabaseAccessor databaseAccessor;

    private final Map<Class,PwmService> pwmServices = new LinkedHashMap<Class, PwmService>();

    private final Date startupTime = new Date();
    private Date installTime = new Date();
    private ErrorInformation lastLdapFailure = null;
    private File pwmApplicationPath; //typically the WEB-INF servlet path

    private MODE applicationMode;

    private static final List<Class> PWM_SERVICE_CLASSES  = Collections.unmodifiableList(Arrays.<Class>asList(SharedHistoryManager.class,
            HealthMonitor.class,
            StatisticsManager.class,
            WordlistManager.class,
            SeedlistManager.class,
            EmailQueueManager.class,
            SmsQueueManager.class,
            UrlShortenerService.class,
            TokenManager.class,
            VersionChecker.class
    ));


// -------------------------- STATIC METHODS --------------------------

    // --------------------------- CONSTRUCTORS ---------------------------

    public PwmApplication(final Configuration config, final MODE applicationMode, final File pwmApplicationPath)
            throws PwmDBException
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
        return (HealthMonitor)pwmServices.get(HealthMonitor.class);
    }

    public Set<PwmService> getPwmServices() {
        final Set<PwmService> pwmServices = new HashSet<PwmService>();
        pwmServices.add(this.databaseAccessor);
        pwmServices.add(this.pwmDBLogger);
        pwmServices.addAll(this.pwmServices.values());
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

    public WordlistManager getWordlistManager() {
        return (WordlistManager)pwmServices.get(WordlistManager.class);
    }

    public SeedlistManager getSeedlistManager() {
        return (SeedlistManager)pwmServices.get(SeedlistManager.class);
    }

    public EmailQueueManager getEmailQueue() {
        return (EmailQueueManager)pwmServices.get(EmailQueueManager.class);
    }

    public SmsQueueManager getSmsQueue() {
        return (SmsQueueManager)pwmServices.get(SmsQueueManager.class);
    }

    public UrlShortenerService getUrlShortener() {
        return (UrlShortenerService)pwmServices.get(UrlShortenerService.class);
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

    public ChaiUser getProxyChaiUserActor(final PwmSession pwmSession)
            throws PwmUnrecoverableException, ChaiUnavailableException {
        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            throw new PwmUnrecoverableException(PwmError.ERROR_AUTHENTICATION_REQUIRED);
        }
        final String userDN = pwmSession.getUserInfoBean().getUserDN();


        return ChaiFactory.createChaiUser(userDN, this.getProxyChaiProvider());
    }


    public synchronized DatabaseAccessor getDatabaseAccessor()
            throws PwmUnrecoverableException
    {
        if (databaseAccessor == null) {
            final DatabaseAccessor.DBConfiguration dbConfiguration = new DatabaseAccessor.DBConfiguration(
                    getConfig().readSettingAsString(PwmSetting.DATABASE_CLASS),
                    getConfig().readSettingAsString(PwmSetting.DATABASE_URL),
                    getConfig().readSettingAsString(PwmSetting.DATABASE_USERNAME),
                    getConfig().readSettingAsString(PwmSetting.DATABASE_PASSWORD));

            databaseAccessor = new DatabaseAccessor(dbConfiguration, this.getInstanceID());
        }
        return databaseAccessor;
    }

    private void initialize() throws PwmDBException {
        final long startTime = System.currentTimeMillis();

        // initialize log4j
        {
            final String log4jFileName = configuration.readSettingAsString(PwmSetting.EVENTS_JAVA_LOG4JCONFIG_FILE);
            final File log4jFile = Helper.figureFilepath(log4jFileName,pwmApplicationPath);
            final String logLevel;
            switch (getApplicationMode()) {
                case RUNNING:
                    logLevel = configuration.readSettingAsString(PwmSetting.EVENTS_JAVA_STDOUT_LEVEL);
                    break;

                default:
                    LOGGER.trace("setting log level to TRACE because PWM is not in RUNNING mode.");
                    logLevel = PwmLogLevel.TRACE.toString();
            }
            PwmInitializer.initializeLogger(log4jFile, logLevel);
        }

        PwmInitializer.initializePwmDB(this);
        PwmInitializer.initializePwmDBLogger(this);

        LOGGER.info("initializing pwm");
        // log the loaded configuration
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
                throw new IllegalStateException(errorMsg);
            }
            pwmServices.put(serviceClass,newServiceInstance);
        }

        taskMaster = new Timer("pwm-PwmApplication timer", true);
        taskMaster.schedule(new IntruderManager.CleanerTask(intruderManager), 90 * 1000, 90 * 1000);


        final TimeDuration totalTime = new TimeDuration(System.currentTimeMillis() - startTime);
        LOGGER.info("PWM " + PwmConstants.SERVLET_VERSION + " open for bidness! (" + totalTime.asCompactString() + ")");
        getStatisticsManager().incrementValue(Statistic.PWM_STARTUPS);
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

        AlertHandler.alertStartup(this);

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

    private static String fetchInstanceID(final PwmDB pwmDB, final PwmApplication pwmApplication) {
        String newInstanceID = pwmApplication.getConfig().readSettingAsString(PwmSetting.PWM_INSTANCE_NAME);

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

    private static void lastLastLdapFailure(final PwmDB pwmDB, final PwmApplication pwmApplication) {
        if (pwmDB != null) {
            try {
                final String lastLdapFailureStr = pwmDB.get(PwmDB.DB.PWM_META, DB_KEY_LAST_LDAP_ERROR);
                if (lastLdapFailureStr != null && lastLdapFailureStr.length() > 0) {
                    final Gson gson = new Gson();
                    pwmApplication.lastLdapFailure = gson.fromJson(lastLdapFailureStr, ErrorInformation.class);
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

    public void sendEmailUsingQueue(final EmailItemBean emailItem, final UserInfoBean uiBean) {
        final EmailQueueManager emailQueue = this.getEmailQueue();
        if (emailQueue == null) {
            LOGGER.error("email queue is unavailable, unable to send email: " + emailItem.toString());
            return;
        }

        final EmailItemBean rewrittenEmailItem = new EmailItemBean(
                emailItem.getTo(),
                emailItem.getFrom(),
                emailItem.getSubject(),
                PwmMacroMachine.expandMacros(emailItem.getBodyPlain(), this, uiBean),
                PwmMacroMachine.expandMacros(emailItem.getBodyHtml(), this, uiBean)
        );

        try {
            emailQueue.addMailToQueue(rewrittenEmailItem);
        } catch (PwmUnrecoverableException e) {
            LOGGER.warn("unable to add email to queue: " + e.getMessage());
        }
    }

    public void sendSmsUsingQueue(final SmsItemBean smsItem) {
        final SmsQueueManager smsQueue = getSmsQueue();
        if (smsQueue == null) {
            LOGGER.error("SMS queue is unavailable, unable to send SMS: " + smsItem.toString());
            return;
        }

        try {
            smsQueue.addSmsToQueue(smsItem);
        } catch (PwmUnrecoverableException e) {
            LOGGER.warn("unable to add sms to queue: " + e.getMessage());
        }
    }

    public void shutdown() {
        LOGGER.warn("shutting down");
        AlertHandler.alertShutdown(this);

        if (taskMaster != null) {
            try {
                taskMaster.cancel();
            } catch (Exception e) {
                LOGGER.error("error closing taskMaster: " + e.getMessage(),e);
            }
            taskMaster = null;
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

        if (databaseAccessor != null) {
            try {
                databaseAccessor.close();
            } catch (Exception e) {
                LOGGER.error("error closing databaseAccessor: " + e.getMessage(),e);
            }
            databaseAccessor = null;
        }

        if (pwmDBLogger != null) {
            try {
                pwmDBLogger.close();
            } catch (Exception e) {
                LOGGER.error("error closing pwmDBLogger: " + e.getMessage(),e);
            }
            pwmDBLogger = null;
        }

        if (pwmDB != null) {
            try {
                pwmDB.close();
            } catch (Exception e) {
                LOGGER.fatal("error closing pwmDB: " + e, e);
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


    public Date getStartupTime() {
        return startupTime;
    }

    public Date getInstallTime() {
        return installTime;
    }

    public PwmDB getPwmDB() {
        return pwmDB;
    }

// -------------------------- INNER CLASSES --------------------------

    private static class PwmInitializer {
        private static void initializeLogger(final File log4jConfigFile, final String logLevel) {
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
                if (logLevel != null && logLevel.length() > 0) {
                    final Layout patternLayout = new PatternLayout("%d{yyyy-MM-dd HH:mm:ss}, %-5p, %c{2}, %m%n");
                    final ConsoleAppender consoleAppender = new ConsoleAppender(patternLayout);
                    final Level level = Level.toLevel(logLevel);
                    pwmPackageLogger.addAppender(consoleAppender);
                    pwmPackageLogger.setLevel(level);
                    chaiPackageLogger.addAppender(consoleAppender);
                    chaiPackageLogger.setLevel(level);
                    casPackageLogger.addAppender(consoleAppender);
                    casPackageLogger.setLevel(level);
                    LOGGER.debug("successfully initialized default log4j config at log level " + level.toString());
                } else {
                    LOGGER.debug("skipping stdout log4j initializtion due to blank setting for log level");
                }
            }

            // if there was an exception trying to load the log4j file, then log it (hopefully the defaults worked)
            if (configException != null) {
                LOGGER.error("error loading log4jconfig file '" + log4jConfigFile + "' error: " + configException.getMessage());
            }
        }

        public static void initializePwmDB(final PwmApplication pwmApplication) {
            if (pwmApplication.getApplicationMode() == MODE.ERROR || pwmApplication.getApplicationMode() == MODE.NEW) {
                LOGGER.warn("skipping pwmDB open due to application mode " + pwmApplication.getApplicationMode());
                return;
            }


            final File databaseDirectory;
            // see if META-INF isn't already there, then use WEB-INF.
            try {
                final String pwmDBLocationSetting = pwmApplication.getConfig().readSettingAsString(PwmSetting.PWMDB_LOCATION);
                databaseDirectory = Helper.figureFilepath(pwmDBLocationSetting, pwmApplication.pwmApplicationPath);
            } catch (Exception e) {
                LOGGER.warn("error locating configured pwmDB directory: " + e.getMessage());
                return;
            }

            LOGGER.debug("using pwmDB path " + databaseDirectory);

            // initialize the pwmDB
            try {
                final String classname = pwmApplication.getConfig().readSettingAsString(PwmSetting.PWMDB_IMPLEMENTATION);
                final List<String> initStrings = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.PWMDB_INIT_STRING);
                final Map<String, String> initParamers = Configuration.convertStringListToNameValuePair(initStrings, "=");
                final boolean readOnly = pwmApplication.getApplicationMode() == MODE.READ_ONLY;
                pwmApplication.pwmDB = PwmDBFactory.getInstance(databaseDirectory, classname, initParamers, readOnly);
            } catch (Exception e) {
                LOGGER.warn("unable to initialize pwmDB: " + e.getMessage());
            }
        }

        public static void initializePwmDBLogger(final PwmApplication pwmApplication) {
            if (pwmApplication.getApplicationMode() == MODE.READ_ONLY) {
                LOGGER.trace("skipping pwmDBLogger due to read-only mode");
                return;
            }

            // initialize the pwmDBLogger
            try {
                final int maxEvents = (int) pwmApplication.getConfig().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_EVENTS);
                final long maxAgeMS = 1000 * pwmApplication.getConfig().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_AGE);
                final PwmLogLevel localLogLevel = pwmApplication.getConfig().getEventLogLocalLevel();
                pwmApplication.pwmDBLogger = PwmLogger.initPwmApplication(pwmApplication.pwmDB, maxEvents, maxAgeMS, localLogLevel, pwmApplication);
            } catch (Exception e) {
                LOGGER.warn("unable to initialize pwmDBLogger: " + e.getMessage());
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
}


