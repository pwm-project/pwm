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

import com.novell.ldapchai.ChaiConstant;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.bean.SmsItemBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.event.AuditEvent;
import password.pwm.event.AuditManager;
import password.pwm.event.SystemAuditRecord;
import password.pwm.health.HealthMonitor;
import password.pwm.ldap.LdapConnectionService;
import password.pwm.token.TokenService;
import password.pwm.util.*;
import password.pwm.util.cache.CacheService;
import password.pwm.util.db.DatabaseAccessorImpl;
import password.pwm.util.intruder.IntruderManager;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBFactory;
import password.pwm.util.logging.LocalDBLogger;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogManager;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.CrService;
import password.pwm.util.operations.OtpService;
import password.pwm.util.queue.EmailQueueManager;
import password.pwm.util.queue.SmsQueueManager;
import password.pwm.util.report.ReportService;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;
import password.pwm.wordlist.SeedlistManager;
import password.pwm.wordlist.SharedHistoryManager;
import password.pwm.wordlist.WordlistManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmApplication.class);
    private static final String DEFAULT_INSTANCE_ID = "-1";

    public enum AppAttribute {
        INSTANCE_ID("context_instanceID"),
        INSTALL_DATE("DB_KEY_INSTALL_DATE"),
        CONFIG_HASH("configurationSettingHash"),
        LAST_LDAP_ERROR("lastLdapError"),
        TOKEN_COUNTER("tokenCounter"),
        REPORT_STATUS("reporting.status"),
        REPORT_CLEAN_FLAG("reporting.cleanFlag"),
        SMS_ITEM_COUNTER("smsQueue.itemCount"),
        EMAIL_ITEM_COUNTER("itemQueue.itemCount"),
        LOCALDB_IMPORT_STATUS("localDB.import.status"),

        ;

        private String key;

        AppAttribute(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }


    private String instanceID = DEFAULT_INSTANCE_ID;
    private final Configuration configuration;

    private LocalDB localDB;
    private LocalDBLogger localDBLogger;

    private final Map<Class<? extends PwmService>,PwmService> pwmServices = new LinkedHashMap<>();

    private final Date startupTime = new Date();
    private Date installTime = new Date();
    private ErrorInformation lastLocalDBFailure = null;

    private final PwmEnvironment pwmEnvironment;
    private final File applicationPath;
    private final File webInfPath;
    private final File configurationFile;

    private MODE applicationMode;

    private static final List<Class<? extends PwmService>> PWM_SERVICE_CLASSES  = Collections.unmodifiableList(Arrays.asList(
            LdapConnectionService.class,
            DatabaseAccessorImpl.class,
            SharedHistoryManager.class,
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
            ReportService.class,
            CrService.class,
            OtpService.class,
            CacheService.class
    ));


    private PwmApplication(final PwmEnvironment pwmEnvironment)
            throws PwmUnrecoverableException
    {
        verifyIfApplicationPathIsSetProperly(pwmEnvironment);

        this.pwmEnvironment = pwmEnvironment;
        this.configuration = pwmEnvironment.config;
        this.applicationMode = pwmEnvironment.applicationMode;
        this.applicationPath = pwmEnvironment.applicationPath;
        this.configurationFile = pwmEnvironment.configurationFile;
        this.webInfPath = pwmEnvironment.webInfPath;

        try {
            initialize(pwmEnvironment.initLogging);
        } catch (PwmUnrecoverableException e) {
            LOGGER.fatal(e.getMessage());
            throw e;
        }
    }

    private void initialize(final boolean initLogging)
            throws PwmUnrecoverableException
    {
        final Date startTime = new Date();

        // initialize log4j
        if (initLogging) {
            final String log4jFileName = configuration.readSettingAsString(PwmSetting.EVENTS_JAVA_LOG4JCONFIG_FILE);
            final File log4jFile = Helper.figureFilepath(log4jFileName, applicationPath);
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

            PwmLogManager.initializeLogger(this, configuration, log4jFile, consoleLevel, applicationPath, fileLevel);

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

        LOGGER.info("initializing, application mode=" + getApplicationMode()
                        + ", applicationPath=" + (applicationPath == null ? "null" : applicationPath.getAbsolutePath())
                        + ", configurationFile=" + (configurationFile == null ? "null" : configurationFile.getAbsolutePath())
        );

        this.localDB = Initializer.initializeLocalDB(this);
        this.localDBLogger = PwmLogManager.initializeLocalDBLogger(this);

        // log the loaded configuration
        LOGGER.info("loaded configuration: \n" + configuration.toString());

        // read the pwm servlet instance id
        instanceID = fetchInstanceID(localDB, this);
        LOGGER.info("using '" + getInstanceID() + "' for instance's ID (instanceID)");

        // read the pwm installation date
        installTime = fetchInstallDate(startupTime);
        LOGGER.debug("this application instance first installed on " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(installTime));

        LOGGER.info(logEnvironment());
        LOGGER.info(logDebugInfo());

        for (final Class<? extends PwmService> serviceClass : PWM_SERVICE_CLASSES) {
            final PwmService newServiceInstance;
            try {
                final Object newInstance = serviceClass.newInstance();
                newServiceInstance = (PwmService)newInstance;
            } catch (Exception e) {
                final String errorMsg = "unexpected error instantiating service class '" + serviceClass.getName() + "', error: " + e.toString();
                LOGGER.fatal(errorMsg,e);
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_STARTUP_ERROR,errorMsg));
            }

            try {
                LOGGER.debug("initializing service " + serviceClass.getName());
                newServiceInstance.init(this);
                LOGGER.debug("initialization of service " + serviceClass.getName() + " has completed successfully");
            } catch (PwmException e) {
                LOGGER.warn("error instantiating service class '" + serviceClass.getName() + "', service will remain unavailable, error: " + e.getMessage());
            } catch (Exception e) {
                String errorMsg = "unexpected error instantiating service class '" + serviceClass.getName() + "', cannot load, error: " + e.getMessage();
                if (e.getCause() != null) {
                    errorMsg += ", cause: " + e.getCause();
                }
                LOGGER.fatal(errorMsg);
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_STARTUP_ERROR,errorMsg));
            }
            pwmServices.put(serviceClass,newServiceInstance);
        }

        final TimeDuration totalTime = TimeDuration.fromCurrent(startTime);
        LOGGER.info(PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION + " open for bidness! (" + totalTime.asCompactString() + ")");
        getStatisticsManager().incrementValue(Statistic.PWM_STARTUPS);
        LOGGER.debug("buildTime=" + PwmConstants.BUILD_TIME + ", javaLocale=" + Locale.getDefault() + ", DefaultLocale=" + PwmConstants.DEFAULT_LOCALE );

        // detect if config has been modified since previous startup
        try {
            final String previousHash = readAppAttribute(AppAttribute.CONFIG_HASH);
            final String currentHash = configuration.configurationHash();
            if (previousHash == null || !previousHash.equals(currentHash)) {
                writeAppAttribute(AppAttribute.CONFIG_HASH, currentHash);
                LOGGER.warn("configuration checksum does not match previously seen checksum, configuration has been modified since last startup");
                if (this.getAuditManager() != null) {
                    final String modifyMessage = "configuration was modified directly (not using ConfigEditor UI)";
                    this.getAuditManager().submit(SystemAuditRecord.create(
                            AuditEvent.MODIFY_CONFIGURATION,
                            modifyMessage,
                            this.getInstanceID()
                    ));
                }
            }
        } catch (Exception e) {
            LOGGER.debug("unable to detect if configuration has been modified since previous startup: " + e.getMessage());
        }

        if (this.getConfig() != null) {
            final Map<AppProperty,String> nonDefaultProperties = getConfig().readAllNonDefaultAppProperties();
            if (nonDefaultProperties != null && !nonDefaultProperties.isEmpty()) {
                final Map<String,String> tempMap = new LinkedHashMap<>();
                for (final AppProperty loopProperty : nonDefaultProperties.keySet()) {
                    tempMap.put(loopProperty.getKey(), nonDefaultProperties.get(loopProperty));
                }
                LOGGER.trace("non-default app properties read from configuration: " + JsonUtil.serializeMap(tempMap));
            } else {
                LOGGER.trace("no non-default app properties in configuration");
            }
        }

        // send system audit event
        final SystemAuditRecord auditRecord = SystemAuditRecord.create(
                AuditEvent.STARTUP,
                null,
                getInstanceID()
        );
        try {
            getAuditManager().submit(auditRecord);
        } catch (PwmException e) {
            LOGGER.warn("unable to submit alert event " + JsonUtil.serialize(auditRecord));
        }
    }

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
            throws PwmUnrecoverableException
    {
        try {
            final ChaiProvider proxiedProvider = getProxyChaiProvider(userIdentity.getLdapProfileID());
            return ChaiFactory.createChaiUser(userIdentity.getUserDN(), proxiedProvider);
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(PwmError.forChaiError(e.getErrorCode()));
        }
    }

    public ChaiProvider getProxyChaiProvider(final String identifier)
            throws PwmUnrecoverableException
    {
        return getLdapConnectionService().getProxyChaiProvider(identifier);
    }

    public LocalDBLogger getLocalDBLogger() {
        return localDBLogger;
    }

    public HealthMonitor getHealthMonitor() {
        return (HealthMonitor)pwmServices.get(HealthMonitor.class);
    }

    public List<PwmService> getPwmServices() {
        final List<PwmService> pwmServices = new ArrayList<>();
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

    public ReportService getUserReportService() {
        return (ReportService)pwmServices.get(ReportService.class);
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

    public ErrorInformation getLastLocalDBFailure() {
        return lastLocalDBFailure;
    }

    public TokenService getTokenService() {
        return (TokenService)pwmServices.get(TokenService.class);
    }

    public LdapConnectionService getLdapConnectionService() {
        return (LdapConnectionService)pwmServices.get(LdapConnectionService.class);
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

    public synchronized DatabaseAccessorImpl getDatabaseAccessor()
    {
        return (DatabaseAccessorImpl)pwmServices.get(DatabaseAccessorImpl.class);
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

        if (newInstanceID.length() < 1) {
            newInstanceID = DEFAULT_INSTANCE_ID;
        }

        return newInstanceID;
    }

    private static String logEnvironment() {
        final Map<String,Object> envStats = new LinkedHashMap<>();
        envStats.put("java.vm.vendor",System.getProperty("java.vm.vendor"));
        envStats.put("java.vm.version",System.getProperty("java.vm.version"));
        envStats.put("java.vm.name",System.getProperty("java.vm.name"));
        envStats.put("java.home",System.getProperty("java.home"));

        envStats.put("memmax",Runtime.getRuntime().maxMemory());
        envStats.put("threads",Thread.activeCount());
        envStats.put("chaiApi",ChaiConstant.CHAI_API_VERSION + ", b" + ChaiConstant.CHAI_API_BUILD_INFO);

        return "environment info: " + JsonUtil.serializeMap(envStats);
    }

    private static String logDebugInfo() {
        final Map<String,Object> debugStats = new LinkedHashMap<>();
        debugStats.put("memfree",Runtime.getRuntime().freeMemory());
        debugStats.put("memallocd",Runtime.getRuntime().totalMemory());
        debugStats.put("memmax",Runtime.getRuntime().maxMemory());
        debugStats.put("threads",Thread.activeCount());
        return "debug info:" + JsonUtil.serializeMap(debugStats);
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

    public CacheService getCacheService() {
        return (CacheService)pwmServices.get(CacheService.class);
    }

    public void sendSmsUsingQueue(
            final SmsItemBean smsItem,
            final MacroMachine macroMachine
    ) {
        final SmsQueueManager smsQueue = getSmsQueue();
        if (smsQueue == null) {
            LOGGER.error("SMS queue is unavailable, unable to send SMS: " + smsItem.toString());
            return;
        }

        final SmsItemBean rewrittenSmsItem = new SmsItemBean(
                macroMachine.expandMacros(smsItem.getTo()),
                macroMachine.expandMacros(smsItem.getMessage())
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
            final SystemAuditRecord auditRecord = SystemAuditRecord.create(
                    AuditEvent.SHUTDOWN,
                    null,
                    getInstanceID()
            );
            try {
                getAuditManager().submit(auditRecord);
            } catch (PwmException e) {
                LOGGER.warn("unable to submit alert event " + JsonUtil.serialize(auditRecord));
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

        LOGGER.info(PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION + " closed for bidness, cya!");
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

    private static class Initializer {

        public static LocalDB initializeLocalDB(final PwmApplication pwmApplication) {
            if (pwmApplication.getApplicationMode() == MODE.ERROR || pwmApplication.getApplicationMode() == MODE.NEW) {
                LOGGER.warn("skipping LocalDB open due to application mode " + pwmApplication.getApplicationMode());
                return null;
            }

            final File databaseDirectory;
            // see if META-INF isn't already there, then use WEB-INF.
            try {
                final String localDBLocationSetting = pwmApplication.getConfig().readSettingAsString(PwmSetting.PWMDB_LOCATION);
                databaseDirectory = Helper.figureFilepath(localDBLocationSetting, pwmApplication.applicationPath);
            } catch (Exception e) {
                pwmApplication.lastLocalDBFailure = new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,"error locating configured LocalDB directory: " + e.getMessage());
                LOGGER.warn(pwmApplication.lastLocalDBFailure.toDebugStr());
                return null;
            }

            LOGGER.debug("using localDB path " + databaseDirectory);

            // initialize the localDB
            try {
                final boolean readOnly = pwmApplication.getApplicationMode() == MODE.READ_ONLY;
                return LocalDBFactory.getInstance(databaseDirectory, readOnly, pwmApplication, pwmApplication.getConfig());
            } catch (Exception e) {
                pwmApplication.lastLocalDBFailure = new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,"unable to initialize LocalDB: " + e.getMessage());
                LOGGER.warn(pwmApplication.lastLocalDBFailure.toDebugStr());
            }

            return null;
        }
    }

    public File getApplicationPath() {
        return applicationPath;
    }

    public enum MODE {
        NEW,
        CONFIGURATION,
        RUNNING,
        READ_ONLY,
        ERROR
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

    public File getWebInfPath() {
        return webInfPath;
    }

    private void verifyIfApplicationPathIsSetProperly(final PwmEnvironment pwmEnvironment)
            throws PwmUnrecoverableException
    {
        final File applicationPath = pwmEnvironment.applicationPath;
        File webInfPath = pwmEnvironment.webInfPath;

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

        boolean applicationPathIsWebInfPath = false;
        if (applicationPath.equals(webInfPath)) {
            applicationPathIsWebInfPath = true;
        } else if (applicationPath.getAbsolutePath().endsWith("/WEB-INF")) {
            final File webXmlFile = new File(applicationPath.getAbsolutePath() + File.separator + "web.xml");
            if (webXmlFile.exists()) {
                applicationPathIsWebInfPath = true;
            }
        }
        if (applicationPathIsWebInfPath) {
            if (webInfPath == null) {
                webInfPath = applicationPath;
                pwmEnvironment.webInfPath = applicationPath;
            }

            LOGGER.trace("applicationPath appears to be servlet /WEB-INF directory");
        }

        final File infoFile = new File(webInfPath.getAbsolutePath() + File.separator + PwmConstants.APPLICATION_PATH_INFO_FILE);
        if (applicationPathIsWebInfPath) {
            if (pwmEnvironment.applicationPathType == PwmEnvironment.ApplicationPathType.derived) {
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
            if (pwmEnvironment.applicationPathType == PwmEnvironment.ApplicationPathType.specified) {
                try {
                    final FileOutputStream fos = new FileOutputStream(infoFile);
                    final Properties outputProperties = new Properties();
                    outputProperties.setProperty("lastApplicationPath", applicationPath.getAbsolutePath());
                    outputProperties.store(fos, "Marker file to record a previously specified applicationPath");
                } catch (IOException e) {
                    LOGGER.warn("unable to write applicationPath marker properties file " + infoFile.getAbsolutePath() + "");
                }
            }
        }
    }

    public static class PwmEnvironment {
        private MODE applicationMode = MODE.ERROR;

        private Configuration config;
        private File applicationPath;
        private boolean initLogging;
        private File configurationFile;
        private File webInfPath;
        private ApplicationPathType applicationPathType = ApplicationPathType.derived;

        public enum ApplicationPathType {
            derived,
            specified,
        }

        public PwmEnvironment setConfig(Configuration config) {
            this.config = config;
            return this;
        }

        public PwmEnvironment setApplicationMode(MODE applicationMode) {
            this.applicationMode = applicationMode;
            return this;
        }

        public PwmEnvironment setApplicationPath(File applicationPath) {
            this.applicationPath = applicationPath;
            return this;
        }

        public PwmEnvironment setInitLogging(boolean initLogging) {
            this.initLogging = initLogging;
            return this;
        }

        public PwmEnvironment setConfigurationFile(File configurationFile) {
            this.configurationFile = configurationFile;
            return this;
        }

        public PwmEnvironment setWebInfPath(File webInfPath) {
            this.webInfPath = webInfPath;
            return this;
        }

        public PwmEnvironment setApplicationPathType(ApplicationPathType applicationPathType) {
            this.applicationPathType = applicationPathType;
            return this;
        }

        public PwmApplication createPwmApplication()
                throws PwmUnrecoverableException
        {
            return new PwmApplication(this);
        }
    }
}


