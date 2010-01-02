/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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
import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiProviderFactory;
import com.novell.ldapchai.provider.ChaiSetting;
import org.apache.log4j.xml.DOMConfigurator;
import password.pwm.config.*;
import password.pwm.error.PwmException;
import password.pwm.process.emailer.EmailEvent;
import password.pwm.process.emailer.EmailQueueManager;
import password.pwm.util.*;
import password.pwm.util.db.PwmDB;
import password.pwm.util.db.PwmDBFactory;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;
import password.pwm.wordlist.SeedlistManager;
import password.pwm.wordlist.SharedHistoryManager;
import password.pwm.wordlist.WordlistManager;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.util.*;

/**
 * A repository for objects common to the servlet context.  A singleton
 * of this object is stored in the servlet context.
 *
 * @author Jason D. Rivard
 */
public class ContextManager implements Serializable
{
// ------------------------------ FIELDS ------------------------------

    // ----------------------------- CONSTANTS ----------------------------
    private static final PwmLogger LOGGER = PwmLogger.getLogger(ContextManager.class);
    private static final String DB_KEY_INSTANCE_ID = "context_instanceID";
    private static final String DEFAULT_INSTANCE_ID = "-1";
    private static final String KEY_INSTALL_DATE = "KEY_INSTALL_DATE";

    int sessionTimeout = -1;
    private String instanceID = "-1";

    private final transient Set<PwmSession> activeSessions = Collections.synchronizedSet(new HashSet<PwmSession>());

    private final IntruderManager intruderManager = new IntruderManager(this);

    private transient ServletContext servletContext;
    private transient ConfigReader configReader;
    private transient EmailQueueManager emailQueue;

    private transient StatisticsManager statisticsManager;
    private transient WordlistManager wordlistManager;
    private transient SharedHistoryManager sharedlHistoryManager;
    private transient SeedlistManager seedlistManager;
    private transient Timer taskMaster;
    private transient PwmDB pwmDB;
    private transient PwmDBLogger pwmDBLogger;
    private transient volatile ChaiProvider proxyChaiProvider;

    private Date startupTime = new Date();
    private Date installTime = new Date();
    private Date lastLdapFailure = null;

// -------------------------- STATIC METHODS --------------------------

    public static ContextManager getContextManager(final HttpServletRequest request) {
        return getContextManager(request.getSession());
    }

    public static ContextManager getContextManager(final HttpSession session) {
        return getContextManager(session.getServletContext());
    }

    public static ContextManager getContextManager(final ServletContext theContext)
    {
        // context manager is initialized at servlet context startup.
        final Object theManager = theContext.getAttribute(Constants.CONTEXT_ATTR_CONTEXT_MANAGER);
        return (ContextManager) theManager;
    }

// --------------------------- CONSTRUCTORS ---------------------------

    ContextManager()
    {
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public Set<PwmSession> getActiveSessions()
    {
        return activeSessions;
    }

    public ConfigReader getConfigReader()
    {
        return this.configReader;
    }

    public String getInstanceID()
    {
        return instanceID;
    }

    public SharedHistoryManager getSharedHistoryManager() {
        return sharedlHistoryManager;
    }

    public IntruderManager getIntruderManager()
    {
        return intruderManager;
    }

    public ChaiProvider getProxyChaiProvider()
            throws ChaiUnavailableException
    {
        if (proxyChaiProvider == null) {
            openProxyChaiProvider();
        }

        return proxyChaiProvider;
    }

    public PwmDBLogger getPwmDBLogger() {
        return pwmDBLogger;
    }

    private void openProxyChaiProvider() throws ChaiUnavailableException {
        if (proxyChaiProvider == null) {
            final StringBuilder debugLogText = new StringBuilder();
            debugLogText.append("opening new ldap proxy connection");
            LOGGER.trace(debugLogText.toString());

            final String proxyDN = this.getConfig().readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
            final String proxyPW = this.getConfig().readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD);

            final ChaiConfiguration chaiConfig = Helper.createChaiConfiguration(this, proxyDN, proxyPW);
            chaiConfig.setSetting(ChaiSetting.WATCHDOG_ENABLE, "false");

            try {
                proxyChaiProvider = ChaiProviderFactory.createProvider(chaiConfig);
            } catch (ChaiUnavailableException e) {
                getStatisticsManager().incrementValue(Statistic.LDAP_UNAVAILABLE_COUNT);
                setLastLdapFailure();
                LOGGER.fatal("check ldap proxy settings: " + e.getMessage());
                throw e;
            }
        }
    }

    public ServletContext getServletContext()
    {
        return servletContext;
    }

    public int getSessionTimeout()
    {
        return sessionTimeout;
    }

    public WordlistManager getWordlistManager()
    {
        return wordlistManager;
    }

    public SeedlistManager getSeedlistManager()
    {
        return seedlistManager;
    }

    public Date getLastLdapFailure() {
        return lastLdapFailure;
    }

    public void setLastLdapFailure() {
        this.lastLdapFailure = new Date();
    }

    // -------------------------- OTHER METHODS --------------------------

    public Configuration getConfig()
    {
        if (configReader == null) {
            return null;
        }
        return configReader.getGlobalConfig();
    }

    public LocalizedConfiguration getLocaleConfig(final Locale locale)
    {
        if (configReader == null) {
            return null;
        }
        return configReader.getLocalizedConfiguration(locale);
    }

    public ChaiUser getProxyChaiUserActor(final PwmSession pwmSession)
            throws PwmException, ChaiUnavailableException
    {
        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            throw PwmException.createPwmException(Message.ERROR_AUTHENTICATION_REQUIRED);
        }
        final String userDN = pwmSession.getUserInfoBean().getUserDN();


        return ChaiFactory.createChaiUser(userDN, this.getProxyChaiProvider());
    }

    public Set<PwmSession> getPwmSessions()
    {
        return Collections.unmodifiableSet(activeSessions);
    }

    void initialize(final ServletContext servletContext)
            throws Exception
    {
        final long startTime = System.currentTimeMillis();
        this.servletContext = servletContext;

        // initialize log4j
        try {
            initLogging();
        } catch (Exception e) {
            final String text = "PWM: Unable to load log configuration file: " + e.getMessage();
            System.out.println(text);
            System.err.println(text);
        }

        LOGGER.info("initializing pwm");

        // initialize configuration
        try {
            configReader = new ConfigReader(figureFilepath(getParameter(Constants.CONTEXT_PARAM.CONFIG_FILE), "WEB-INF", servletContext));
        } catch (Exception e) {
            LOGGER.fatal("unable to load configuration file", e);
            LOGGER.fatal("unable to initialize pwm due to missing or malformed configuration: " + e.getMessage());
            return;
        }

        // initialize the pwmDB
        try {
            final File databaseDirectory = figureFilepath(getParameter(Constants.CONTEXT_PARAM.PWMDB_LOCATION), "META-INF", servletContext);
            final String dbClassname = getParameter(Constants.CONTEXT_PARAM.PWMDB_IMPLEMENTATION);
            final String initString = getParameter(Constants.CONTEXT_PARAM.PWMDB_INITSTRING);
            pwmDB = PwmDBFactory.getInstance(databaseDirectory, dbClassname, initString);
        } catch (Exception e) {
            LOGGER.warn("unable to initialize pwmDB: " + e.getMessage());
        }

        // initialize the pwmDBLogger
        try {
            final int maxEvents = configReader.getGlobalConfig().readSettingAsInt(PwmSetting.EVENT_LOG_MAX_LOCAL_EVENTS);
            final int maxAge = configReader.getGlobalConfig().readSettingAsInt(PwmSetting.EVENT_LOG_MAX_LOCAL_AGE);
            pwmDBLogger = PwmLogger.initContextManager(pwmDB, maxEvents, maxAge);
        } catch (Exception e) {
            LOGGER.warn("unable to initialize pwmDBLogger: " + e.getMessage());
        }

        // log the loaded configuration
        LOGGER.info(logContextParams());
        LOGGER.info("loaded configuration: " + configReader.toDebugString());
        LOGGER.info("loaded pwm global password policy: " + configReader.getGlobalConfig().getGlobalPasswordPolicy());

        // get the pwm servlet instance id
        instanceID = fetchInstanceID(pwmDB, this);
        LOGGER.info("using '" + getInstanceID() + "' for this pwm instance's ID (instanceID)");

        // get the pwm installation date
        installTime = fetchInstallDate(pwmDB, startupTime);
        LOGGER.debug("this pwm instance first installed on " + installTime.toString());

        // startup the stats engine;
        statisticsManager = new StatisticsManager(pwmDB);
        getStatisticsManager().incrementValue(Statistic.PWM_STARTUPS);

        // initialize wordlist
        {
            final int loadFactor = Integer.parseInt(getParameter(Constants.CONTEXT_PARAM.WORDLIST_LOAD_FACTOR));
            try {
                LOGGER.trace("opening wordlistDB");

                final String setting = configReader.getGlobalConfig().readSettingAsString(PwmSetting.WORDLIST_FILENAME);
                final File wordlistFile = setting == null || setting.length() < 1 ? null : figureFilepath(setting, "WEB-INF", servletContext);
                final boolean caseSensitive = Boolean.parseBoolean(getParameter(Constants.CONTEXT_PARAM.WORDLIST_CASE_SENSITIVE));

                wordlistManager = WordlistManager.createWordlistManager(
                        wordlistFile,
                        pwmDB,
                        loadFactor,
                        caseSensitive
                );
            } catch (Exception e) {
                LOGGER.warn("unable to initialize wordlist-db: " + e.getMessage());
            }

            // initialize wordlist
            try {
                LOGGER.trace("opening seedlistDB");

                final String setting = configReader.getGlobalConfig().readSettingAsString(PwmSetting.SEEDLIST_FILENAME);
                final File seedlistFile = setting == null || setting.length() < 1 ? null : figureFilepath(setting, "WEB-INF", servletContext);
                seedlistManager = SeedlistManager.createSeedlistManager(
                        seedlistFile,
                        pwmDB,
                        loadFactor
                );
            } catch (Exception e) {
                LOGGER.warn("unable to initialize seedlist-db: " + e.getMessage());
            }
        }

        // initialize shared history;
        try {
            final long maxAgeSeconds = configReader.getGlobalConfig().readSettingAsInt(PwmSetting.PASSWORD_SHAREDHISTORY_MAX_AGE);
            final long maxAgeMS = maxAgeSeconds * 1000;  // convert to MS;
            final boolean caseSensitive = Boolean.parseBoolean(getParameter(Constants.CONTEXT_PARAM.WORDLIST_CASE_SENSITIVE));

            sharedlHistoryManager = SharedHistoryManager.createSharedHistoryManager(pwmDB, maxAgeMS, caseSensitive);
        } catch (Exception e) {
            LOGGER.warn("unable to initialize sharedhistory-db: " + e.getMessage());
        }

        LOGGER.info(logEnvironment());
        LOGGER.info(logDebugInfo(activeSessions.size(), getStatisticsManager()));

        emailQueue = new EmailQueueManager(this);
        LOGGER.trace("email queue manager started");

        taskMaster = new Timer("pwm-ContextManager timer", true);
        taskMaster.schedule(new IntruderManager.CleanerTask(intruderManager), 90 * 1000, 90 * 1000);
        taskMaster.schedule(emailQueue, 5000, 5000);
        taskMaster.schedule(new SessionWatcher(), 5 * 1000, 5 * 1000);
        taskMaster.scheduleAtFixedRate(new DebugLogOutputter(), 60 * 60 * 1000, 60 * 60 * 1000); //once every hour

        final TimeDuration totalTime = new TimeDuration(System.currentTimeMillis() - startTime);
        LOGGER.info("PWM " + Constants.SERVLET_VERSION + " (" + Constants.BUILD_NUMBER + ") open for bidness! (" + totalTime.asCompactString() + ")");

        // warmup the proxy ldap connection
        new Thread(new Runnable() {public void run() { try {
            final ChaiProvider provider = getProxyChaiProvider();
            LOGGER.debug("detected ldap directory vendor: " + provider.getDirectoryVendor());
        } catch (Exception e) { /**/ }} }).start();

    }

    private void initLogging()
            throws Exception
    {
        final File theFile = figureFilepath(Constants.DEFAULT_LOG4JCONFIG_FILENAME, "WEB-INF/", servletContext);
        final String output = "PWM: reading log4j config file: " + theFile.getAbsoluteFile() + " (check stderr for log4j errors)";
        System.out.println(output);
        DOMConfigurator.configureAndWatch(theFile.getAbsolutePath());
    }

    public String getParameter(final Constants.CONTEXT_PARAM param)
    {
        return servletContext.getInitParameter(param.getKey());
    }

    public long getPwmDbDiskSize() {
        if (pwmDB != null) {
            try {
                return pwmDB.diskSpaceUsed();
            } catch (Exception e) {
                LOGGER.error("error reading pwmDB disk space size: " + e.getMessage());
            }
        }
        return 0;
    }

    private static Date fetchInstallDate(final PwmDB pwmDB, final Date startupTime) {
        if (pwmDB != null) {
            try {
                final String storedDateStr = pwmDB.get(PwmDB.DB.PWM_META, KEY_INSTALL_DATE);
                if (storedDateStr == null || storedDateStr.length() < 1) {
                    pwmDB.put(PwmDB.DB.PWM_META, KEY_INSTALL_DATE, String.valueOf(startupTime.getTime()));
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

        String newInstanceID = contextManager.getParameter(Constants.CONTEXT_PARAM.INSTANCE_ID);

        if (newInstanceID != null && newInstanceID.trim().length() > 0) {
            return newInstanceID;
        }

        if (pwmDB != null) {
            try {
                newInstanceID = pwmDB.get(PwmDB.DB.PWM_META,DB_KEY_INSTANCE_ID);
                LOGGER.trace("retrieved instanceID " + newInstanceID + "" + " from pwmDB");
            } catch (Exception e) {
                LOGGER.warn("error retrieving instanceID from pwmDB: " + e.getMessage(),e);
            }
        }

        if (newInstanceID == null || newInstanceID.length() < 1) {
            newInstanceID = Long.toHexString(PwmRandom.getInstance().nextLong()).toUpperCase();
            LOGGER.info("generated new random instanceID " + newInstanceID);

            if (pwmDB != null) {
                try {
                    pwmDB.put(PwmDB.DB.PWM_META,DB_KEY_INSTANCE_ID,String.valueOf(newInstanceID));
                    LOGGER.debug("saved instanceID " + newInstanceID + "" + " to pwmDB");
                } catch (Exception e) {
                    LOGGER.warn("error saving instanceID to pwmDB: " + e.getMessage(),e);
                }
            }
        }

        if (newInstanceID == null || newInstanceID.length() < 1) {
            newInstanceID = DEFAULT_INSTANCE_ID;
        }

        return newInstanceID;
    }

    private static String logEnvironment()
    {
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

    private static String logDebugInfo(final int activeSessionCount, final StatisticsManager statsMgr)
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("debug info:");
        sb.append(" sessions=").append(activeSessionCount);
        sb.append(", memfree=").append(Runtime.getRuntime().freeMemory());
        sb.append(", memallocd=").append(Runtime.getRuntime().totalMemory());
        sb.append(", memmax=").append(Runtime.getRuntime().maxMemory());
        sb.append(", threads=").append(Thread.activeCount());
        sb.append(", status={").append(statsMgr.toString()).append("}");
        return sb.toString();
    }

    public String logContextParams() {
        final StringBuilder sb = new StringBuilder();
        sb.append("context-params: ");

        for (final Constants.CONTEXT_PARAM contextParam : Constants.CONTEXT_PARAM.values()) {
            final String value = getParameter(contextParam);
            sb.append(contextParam.getKey()).append("=").append(value);
            sb.append(", ");
        }

        sb.delete(sb.length() - 2, sb.length());
        return sb.toString();
    }

    public StatisticsManager getStatisticsManager()
    {
        return statisticsManager;
    }

    /**
     * Try to find the real path to a file.  Used for configuration, database, and temporary files.
     *
     * Multiple strategies are used to determine the real path of files because different servlet containers
     * have different symantics.  In principal, servlets are not supposed
     * @param filename A filename that will be appeneded to the end of the verified direcotry
     * @param suggestedPath The desired path of the file, either relative to the servlet directory or an absolute path
     *   on the file system
     * @param servletContext The HttpServletContext to be used to retrieve a path.
     * @return a File referencing the desired suggestedPath and filename.
     * @throws Exception if unabble to discover a path.
     */
    private static File figureFilepath(final String filename, final String suggestedPath, final ServletContext servletContext)
            throws Exception
    {
        if (filename == null || filename.trim().length() < 1) {
            throw new Exception("unable to locate resource file path=" + suggestedPath + ", name=" + filename);
        }

        if ((new File(filename)).isAbsolute()) {
            return new File(filename);
        }

        if ((new File(suggestedPath).isAbsolute())) {
            return new File(suggestedPath + File.separator + filename);
        }

        { // tomcat, and some other containers will correctly return the "real path", so try that first.
            final String relativePath = servletContext.getRealPath(suggestedPath);
            if (relativePath != null) {
                final File finalDirectory = new File(relativePath);
                if (finalDirectory.exists()) {
                    return new File(finalDirectory.getAbsolutePath() + File.separator + filename);
                }
            }
        }

        // for containers which do not retrieve the real path, try to use the classloader to find the path.
        final String cManagerName = ContextManager.class.getCanonicalName();
        final String resourcePathname = "/" + cManagerName.replace(".","/") + ".class";
        final URL fileURL = ContextManager.class.getResource(resourcePathname);
        if (fileURL != null) {
            final String newString = fileURL.toString().replace("WEB-INF/classes" + resourcePathname,"");
            final File finalDirectory = new File(new URL(newString + suggestedPath).toURI());
            if (finalDirectory.exists()) {
                return new File(finalDirectory.getAbsolutePath() + File.separator + filename);
            }
        }

        throw new Exception("unable to locate resource file path=" + suggestedPath + ", name=" + filename);
    }

    public void sendEmailUsingQueue(final EmailEvent event)
    {
        emailQueue.addMailToQueue(event);
    }

    public void shutdown()
    {
        LOGGER.warn("shutting down");

        getStatisticsManager().flush();
        taskMaster.cancel();

        if (wordlistManager != null) {
            wordlistManager.close();
            wordlistManager = null;
        }

        if (seedlistManager != null) {
            seedlistManager.close();
            seedlistManager = null;
        }

        if (sharedlHistoryManager != null) {
            sharedlHistoryManager.close();
            sharedlHistoryManager = null;
        }

        Helper.pause(1000);

        this.getPwmDBLogger().close();

        if (pwmDB != null) {
            try {
                pwmDB.close();
            } catch (Exception e) {
                LOGGER.fatal("error destroying pwm context DB: " + e, e);
            }
            pwmDB = null;
        }

        closeProxyChaiProvider();

        LOGGER.info("PWM " + Constants.SERVLET_VERSION + " closed for bidness, cya!");
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
        activeSessions.add(pwmSession);
    }

    public Date getStartupTime() {
        return startupTime;
    }

    public Date getInstallTime() {
        return installTime;
    }

    // -------------------------- INNER CLASSES --------------------------

    public class DebugLogOutputter extends TimerTask {
        public void run()
        {
            LOGGER.debug(logDebugInfo(activeSessions.size(), getStatisticsManager()));
        }
    }

    public class SessionWatcher extends TimerTask {
        public void run()
        {
            final Set<PwmSession> copiedMap = new HashSet<PwmSession>();

            synchronized (activeSessions) {
                copiedMap.addAll(activeSessions);
            }

            final Set<PwmSession> deadSessions = new HashSet<PwmSession>();

            for (final PwmSession pwmSession : copiedMap) {
                if (!pwmSession.isValid()) {
                    deadSessions.add(pwmSession);
                }
            }

            synchronized (activeSessions) {
                activeSessions.removeAll(deadSessions);
            }
        }
    }
}

