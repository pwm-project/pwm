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
import password.pwm.health.HealthMonitor;
import password.pwm.http.servlet.resource.ResourceServletService;
import password.pwm.ldap.LdapConnectionService;
import password.pwm.svc.PwmService;
import password.pwm.svc.PwmServiceManager;
import password.pwm.svc.cache.CacheService;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditService;
import password.pwm.svc.event.SystemAuditRecord;
import password.pwm.svc.intruder.IntruderManager;
import password.pwm.svc.intruder.RecordType;
import password.pwm.svc.report.ReportService;
import password.pwm.svc.sessiontrack.SessionTrackService;
import password.pwm.svc.shorturl.UrlShortenerService;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.svc.token.TokenService;
import password.pwm.svc.wordlist.SeedlistManager;
import password.pwm.svc.wordlist.SharedHistoryManager;
import password.pwm.svc.wordlist.WordlistManager;
import password.pwm.util.FileSystemUtility;
import password.pwm.util.Helper;
import password.pwm.util.JsonUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.db.DatabaseAccessorImpl;
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
import password.pwm.util.secure.PwmRandom;
import password.pwm.util.secure.SecureService;

import java.io.File;
import java.io.Serializable;
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
        WORDLIST_METADATA("wordlist.metadata"),
        SEEDLIST_METADATA("seedlist.metadata"),
        HTTPS_SELF_CERT("https.selfCert"),
        CONFIG_LOGIN_HISTORY("config.loginHistory"),

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
    private String instanceNonce = PwmRandom.getInstance().randomUUID().toString();

    private LocalDB localDB;
    private LocalDBLogger localDBLogger;

    private final Date startupTime = new Date();
    private Date installTime = new Date();
    private ErrorInformation lastLocalDBFailure = null;

    private final PwmEnvironment pwmEnvironment;

    private final PwmServiceManager pwmServiceManager = new PwmServiceManager(this);

    public PwmApplication(final PwmEnvironment pwmEnvironment)
            throws PwmUnrecoverableException
    {
        pwmEnvironment.verifyIfApplicationPathIsSetProperly();
        this.pwmEnvironment = pwmEnvironment;

        try {
            initialize();
        } catch (PwmUnrecoverableException e) {
            LOGGER.fatal(e.getMessage());
            throw e;
        }
    }

    private void initialize()
            throws PwmUnrecoverableException
    {
        final Date startTime = new Date();

        // initialize log4j
        if (!pwmEnvironment.isInternalRuntimeInstance()) {
            final String log4jFileName = pwmEnvironment.getConfig().readSettingAsString(PwmSetting.EVENTS_JAVA_LOG4JCONFIG_FILE);
            final File log4jFile = FileSystemUtility.figureFilepath(log4jFileName, pwmEnvironment.getApplicationPath());
            final String consoleLevel, fileLevel;
            switch (getApplicationMode()) {
                case ERROR:
                case NEW:
                    consoleLevel = PwmLogLevel.TRACE.toString();
                    fileLevel = PwmLogLevel.TRACE.toString();
                    break;

                default:
                    consoleLevel = pwmEnvironment.getConfig().readSettingAsString(PwmSetting.EVENTS_JAVA_STDOUT_LEVEL);
                    fileLevel = pwmEnvironment.getConfig().readSettingAsString(PwmSetting.EVENTS_FILE_LEVEL);
                    break;
            }

            PwmLogManager.initializeLogger(this, pwmEnvironment.getConfig(), log4jFile, consoleLevel, pwmEnvironment.getApplicationPath(), fileLevel);

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

        // get file lock
        pwmEnvironment.waitForFileLock();;


        LOGGER.info("initializing, application mode=" + getApplicationMode()
                        + ", applicationPath=" + (pwmEnvironment.getApplicationPath() == null ? "null" : pwmEnvironment.getApplicationPath().getAbsolutePath())
                        + ", pwmEnvironment.getConfig()File=" + (pwmEnvironment.getConfigurationFile() == null ? "null" : pwmEnvironment.getConfigurationFile().getAbsolutePath())
        );

        if (!pwmEnvironment.isInternalRuntimeInstance()) {
            if (getApplicationMode() == MODE.ERROR || getApplicationMode() == MODE.NEW) {
                LOGGER.warn("skipping LocalDB open due to application mode " + getApplicationMode());
            } else {
                this.localDB = Initializer.initializeLocalDB(this);
            }
        }

        this.localDBLogger = PwmLogManager.initializeLocalDBLogger(this);

        // log the loaded configuration
        LOGGER.info("configuration load completed");

        // read the pwm servlet instance id
        instanceID = fetchInstanceID(localDB, this);
        LOGGER.info("using '" + getInstanceID() + "' for instance's ID (instanceID)");

        // read the pwm installation date
        installTime = fetchInstallDate(startupTime);
        LOGGER.debug("this application instance first installed on " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(installTime));

        pwmServiceManager.initAllServices();

        if (!pwmEnvironment.isInternalRuntimeInstance()) {
            if (pwmEnvironment.getFlags().contains(PwmEnvironment.ApplicationFlag.CommandLineInstance)) {
                postInitTasks();
            } else {
                final TimeDuration totalTime = TimeDuration.fromCurrent(startTime);
                LOGGER.info(PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION + " open for bidness! (" + totalTime.asCompactString() + ")");
                StatisticsManager.incrementStat(this, Statistic.PWM_STARTUPS);
                LOGGER.debug("buildTime=" + PwmConstants.BUILD_TIME + ", javaLocale=" + Locale.getDefault() + ", DefaultLocale=" + PwmConstants.DEFAULT_LOCALE);

                final Thread postInitThread = new Thread() {
                    @Override
                    public void run() {
                        postInitTasks();
                    }
                };
                postInitThread.setDaemon(true);
                postInitThread.setName(Helper.makeThreadName(this, PwmApplication.class));
                postInitThread.start();
            }
        }
    }

    private void postInitTasks() {
        final Date startTime = new Date();

        LOGGER.debug("loaded configuration: " + pwmEnvironment.getConfig().toDebugString());

        // detect if config has been modified since previous startup
        try {
            final String previousHash = readAppAttribute(AppAttribute.CONFIG_HASH, String.class);
            final String currentHash = pwmEnvironment.getConfig().configurationHash();
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

        try {
            Map<PwmAboutProperty,String> infoMap = Helper.makeInfoBean(this);
            LOGGER.trace("application info: " + JsonUtil.serializeMap(infoMap));
        } catch (Exception e) {
            LOGGER.error("error generating about application bean: " + e.getMessage());
        }

        try {
            this.getIntruderManager().clear(RecordType.USERNAME, PwmConstants.CONFIGMANAGER_INTRUDER_USERNAME);
        } catch (Exception e) {
            LOGGER.debug("error while clearing configmanager-intruder-username from intruder table: " + e.getMessage());
        }

        LOGGER.trace("completed post init tasks in " + TimeDuration.fromCurrent(startTime).asCompactString());
    }

    public String getInstanceID() {
        return instanceID;
    }

    public SharedHistoryManager getSharedHistoryManager() {
        return (SharedHistoryManager)pwmServiceManager.getService(SharedHistoryManager.class);
    }

    public IntruderManager getIntruderManager() {
        return (IntruderManager)pwmServiceManager.getService(IntruderManager.class);
    }

    public ChaiUser getProxiedChaiUser(final UserIdentity userIdentity)
            throws PwmUnrecoverableException
    {
        try {
            final ChaiProvider proxiedProvider = getProxyChaiProvider(userIdentity.getLdapProfileID());
            return ChaiFactory.createChaiUser(userIdentity.getUserDN(), proxiedProvider);
        } catch (ChaiUnavailableException e) {
            throw PwmUnrecoverableException.fromChaiException(e);
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
        return (HealthMonitor)pwmServiceManager.getService(HealthMonitor.class);
    }

    public List<PwmService> getPwmServices() {
        final List<PwmService> pwmServices = new ArrayList<>();
        pwmServices.add(this.localDBLogger);
        pwmServices.addAll(this.pwmServiceManager.getRunningServices());
        pwmServices.remove(null);
        return Collections.unmodifiableList(pwmServices);
    }

    public WordlistManager getWordlistManager() {
        return (WordlistManager)pwmServiceManager.getService(WordlistManager.class);
    }

    public SeedlistManager getSeedlistManager() {
        return (SeedlistManager)pwmServiceManager.getService(SeedlistManager.class);
    }

    public ReportService getReportService() {
        return (ReportService)pwmServiceManager.getService(ReportService.class);
    }

    public EmailQueueManager getEmailQueue() {
        return (EmailQueueManager)pwmServiceManager.getService(EmailQueueManager.class);
    }

    public AuditService getAuditManager() {
        return (AuditService)pwmServiceManager.getService(AuditService.class);
    }

    public SmsQueueManager getSmsQueue() {
        return (SmsQueueManager)pwmServiceManager.getService(SmsQueueManager.class);
    }

    public UrlShortenerService getUrlShortener() {
        return (UrlShortenerService)pwmServiceManager.getService(UrlShortenerService.class);
    }

    public VersionChecker getVersionChecker() {
        return (VersionChecker)pwmServiceManager.getService(VersionChecker.class);
    }

    public ErrorInformation getLastLocalDBFailure() {
        return lastLocalDBFailure;
    }

    public TokenService getTokenService() {
        return (TokenService)pwmServiceManager.getService(TokenService.class);
    }

    public LdapConnectionService getLdapConnectionService() {
        return (LdapConnectionService)pwmServiceManager.getService(LdapConnectionService.class);
    }

    public SessionTrackService getSessionTrackService() {
        return (SessionTrackService)pwmServiceManager.getService(SessionTrackService.class);
    }

    public ResourceServletService getResourceServletService() {
        return (ResourceServletService)pwmServiceManager.getService(ResourceServletService.class);
    }

    public Configuration getConfig() {
        return pwmEnvironment.getConfig();
    }

    public MODE getApplicationMode() {
        return pwmEnvironment.getApplicationMode();
    }

    public synchronized DatabaseAccessorImpl getDatabaseAccessor()
    {
        return (DatabaseAccessorImpl)pwmServiceManager.getService(DatabaseAccessorImpl.class);
    }

    private Date fetchInstallDate(final Date startupTime) {
        if (localDB != null) {
            try {
                final String storedDateStr = readAppAttribute(AppAttribute.INSTALL_DATE,String.class);
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

        newInstanceID = readAppAttribute(AppAttribute.INSTANCE_ID, String.class);

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

    public StatisticsManager getStatisticsManager() {
        return (StatisticsManager)pwmServiceManager.getService(StatisticsManager.class);
    }

    public OtpService getOtpService() {
        return (OtpService)pwmServiceManager.getService(OtpService.class);
    }

    public CrService getCrService() {
        return (CrService)pwmServiceManager.getService(CrService.class);
    }

    public CacheService getCacheService() {
        return (CacheService)pwmServiceManager.getService(CacheService.class);
    }

    public SecureService getSecureService() {
        return (SecureService)pwmServiceManager.getService(SecureService.class);
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

        pwmServiceManager.shutdownAllServices();

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
                LOGGER.trace("beginning close of LocalDB");
                localDB.close();
            } catch (Exception e) {
                LOGGER.fatal("error closing localDB: " + e, e);
            }
            localDB = null;
        }

        pwmEnvironment.releaseFileLock();

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

        public static LocalDB initializeLocalDB(final PwmApplication pwmApplication) throws PwmUnrecoverableException {
            final File databaseDirectory;
            // see if META-INF isn't already there, then use WEB-INF.
            try {
                final String localDBLocationSetting = pwmApplication.getConfig().readSettingAsString(PwmSetting.PWMDB_LOCATION);
                databaseDirectory = FileSystemUtility.figureFilepath(localDBLocationSetting, pwmApplication.pwmEnvironment.getApplicationPath());
            } catch (Exception e) {
                pwmApplication.lastLocalDBFailure = new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,"error locating configured LocalDB directory: " + e.getMessage());
                LOGGER.warn(pwmApplication.lastLocalDBFailure.toDebugStr());
                throw new PwmUnrecoverableException(pwmApplication.lastLocalDBFailure);
            }

            LOGGER.debug("using localDB path " + databaseDirectory);

            // initialize the localDB
            try {
                final boolean readOnly = pwmApplication.getApplicationMode() == MODE.READ_ONLY;
                return LocalDBFactory.getInstance(databaseDirectory, readOnly, pwmApplication, pwmApplication.getConfig());
            } catch (Exception e) {
                pwmApplication.lastLocalDBFailure = new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,"unable to initialize LocalDB: " + e.getMessage());
                LOGGER.warn(pwmApplication.lastLocalDBFailure.toDebugStr());
                throw new PwmUnrecoverableException(pwmApplication.lastLocalDBFailure);
            }
        }
    }

    public PwmEnvironment getPwmEnvironment() {
        return pwmEnvironment;
    }

    public enum MODE {
        NEW,
        CONFIGURATION,
        RUNNING,
        READ_ONLY,
        ERROR
    }

    public String getInstanceNonce() {
        return instanceNonce;
    }

    public <T extends Serializable> T readAppAttribute(final AppAttribute appAttribute, final Class<T> returnClass) {
        if (localDB == null || localDB.status() != LocalDB.Status.OPEN) {
            LOGGER.error("error retrieving key '" + appAttribute.getKey() + "', localDB unavailable: ");
            return null;
        }

        if (appAttribute == null) {
            return null;
        }

        try {
            final String strValue = localDB.get(LocalDB.DB.PWM_META, appAttribute.getKey());
            return JsonUtil.deserialize(strValue, returnClass);
        } catch (Exception e) {
            LOGGER.error("error retrieving key '" + appAttribute.getKey() + "' installation date from localDB: " + e.getMessage());
        }
        return null;
    }

    public void writeAppAttribute(final AppAttribute appAttribute, final Serializable value) {
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
                final String jsonValue = JsonUtil.serialize(value);
                localDB.put(LocalDB.DB.PWM_META, appAttribute.getKey(), jsonValue);
            }
        } catch (Exception e) {
            LOGGER.error("error retrieving key '" + appAttribute.getKey() + "' installation date from localDB: " + e.getMessage());
            try {
                localDB.remove(LocalDB.DB.PWM_META, appAttribute.getKey());
            } catch (Exception e2) {
                LOGGER.error("error removing bogus appAttribute value for key " + appAttribute.getKey() + ", error: " + localDB);
            }
        }
    }
}



