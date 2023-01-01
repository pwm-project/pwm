/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm;

import lombok.Value;
import password.pwm.bean.DomainID;
import password.pwm.bean.ProfileID;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingScope;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthService;
import password.pwm.http.state.SessionStateService;
import password.pwm.svc.PwmService;
import password.pwm.svc.PwmServiceEnum;
import password.pwm.svc.PwmServiceManager;
import password.pwm.svc.db.DatabaseAccessor;
import password.pwm.svc.db.DatabaseService;
import password.pwm.svc.email.EmailService;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditService;
import password.pwm.svc.event.AuditServiceClient;
import password.pwm.svc.httpclient.HttpClientService;
import password.pwm.svc.intruder.IntruderRecordType;
import password.pwm.svc.intruder.IntruderSystemService;
import password.pwm.svc.node.NodeService;
import password.pwm.svc.secure.SystemSecureService;
import password.pwm.svc.sessiontrack.SessionTrackService;
import password.pwm.svc.sessiontrack.UserAgentUtils;
import password.pwm.svc.shorturl.UrlShortenerService;
import password.pwm.svc.sms.SmsQueueService;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.svc.stats.StatisticsService;
import password.pwm.svc.wordlist.SharedHistoryService;
import password.pwm.svc.wordlist.WordlistService;
import password.pwm.util.MBeanUtility;
import password.pwm.util.PwmScheduler;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.logging.LocalDBLogger;
import password.pwm.util.logging.PwmLogManager;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PwmApplication
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmApplication.class );

    static final int DOMAIN_STARTUP_THREADS = 10;

    private volatile Map<DomainID, PwmDomain> domains = new HashMap<>();
    private String runtimeNonce = PwmApplicationUtil.makeRuntimeNonce();

    private final SessionLabel sessionLabel;
    private final PwmServiceManager pwmServiceManager;

    private final Instant startupTime = Instant.now();

    private Instant installTime = Instant.now();
    private ErrorInformation lastLocalDBFailure;
    private PwmEnvironment pwmEnvironment;
    private FileLocker fileLocker;
    private PwmScheduler pwmScheduler;
    private String instanceID = PwmApplicationUtil.DEFAULT_INSTANCE_ID;
    private LocalDB localDB;
    private LocalDBLogger localDBLogger;

    public PwmApplication( final PwmEnvironment pwmEnvironment )
            throws PwmUnrecoverableException
    {
        this.pwmEnvironment = Objects.requireNonNull( pwmEnvironment );
        this.sessionLabel = SessionLabel.forSystem( pwmEnvironment, DomainID.systemId() );

        this.pwmServiceManager = new PwmServiceManager(
                sessionLabel, this, DomainID.systemId(), PwmServiceEnum.forScope( PwmSettingScope.SYSTEM ) );

        if ( !pwmEnvironment.isInternalRuntimeInstance() )
        {
            pwmEnvironment.verifyIfApplicationPathIsSetProperly();
        }

        try
        {
            initialize();
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.fatal( sessionLabel, e::getMessage );
            throw e;
        }
    }

    private void initRuntimeNonce()
    {
        runtimeNonce = PwmApplicationUtil.makeRuntimeNonce();
    }

    private void initialize()
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        initRuntimeNonce();

        // initialize logger
        PwmApplicationUtil.initializeLogging( this );

        // get file lock
        if ( !pwmEnvironment.isInternalRuntimeInstance() )
        {
            fileLocker = new FileLocker( pwmEnvironment );
            fileLocker.waitForFileLock();
        }

        // clear temp dir
        if ( !pwmEnvironment.isInternalRuntimeInstance() )
        {
            final Path tempFileDirectory = getTempDirectory();
            try
            {
                LOGGER.debug( sessionLabel, () -> "deleting directory (and sub-directory) contents in " + tempFileDirectory );
                FileSystemUtility.deleteDirectoryContentsRecursively( tempFileDirectory );
            }
            catch ( final Exception e )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_STARTUP_ERROR,
                        "unable to clear temp file directory '" + tempFileDirectory + "', error: " + e.getMessage()
                ) );
            }
        }

        if ( getApplicationMode() != PwmApplicationMode.READ_ONLY )
        {
            LOGGER.info( sessionLabel, () -> "initializing, application mode=" + getApplicationMode()
                    + ", applicationPath=" + ( pwmEnvironment.getApplicationPath() == null ? "null" : pwmEnvironment.getApplicationPath() )
                    + ", configFile=" + ( pwmEnvironment.getConfigurationFile() == null ? "null" : pwmEnvironment.getConfigurationFile() )
            );
        }

        if ( !pwmEnvironment.isInternalRuntimeInstance() )
        {
            if ( getApplicationMode() == PwmApplicationMode.ERROR )
            {
                LOGGER.warn( sessionLabel, () -> "skipping LocalDB open due to application mode " + getApplicationMode() );
            }
            else
            {
                if ( localDB == null )
                {
                    this.localDB = PwmApplicationUtil.initializeLocalDB( this, pwmEnvironment );
                }
            }
        }

        // read the instance id
        instanceID = PwmApplicationUtil.fetchInstanceID( this, localDB );
        LOGGER.debug( sessionLabel, () -> "using '" + getInstanceID() + "' for instance's ID (instanceID)" );

        this.localDBLogger = PwmLogManager.initializeLocalDBLogger( this );

        // log the loaded configuration
        LOGGER.debug( sessionLabel, () -> "configuration load completed" );

        // read the pwm installation date
        installTime = fetchInstallDate( startupTime );
        LOGGER.debug( sessionLabel, () -> "this application instance first installed on " + StringUtil.toIsoDate( installTime ) );

        pwmScheduler = new PwmScheduler( this );

        domains = PwmDomainUtil.createDomainInstances( this );

        pwmServiceManager.initAllServices();

        PwmDomainUtil.initDomains( this, domains().values() );

        final boolean skipPostInit = pwmEnvironment.isInternalRuntimeInstance()
                || pwmEnvironment.readPropertyAsBoolean( EnvironmentProperty.CommandLineInstance );

        if ( !skipPostInit )
        {
            final TimeDuration totalTime = TimeDuration.fromCurrent( startTime );
            LOGGER.info( sessionLabel, () -> PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION + " open for bidness! (" + totalTime.asCompactString() + ")" );
            StatisticsClient.incrementStat( this, Statistic.PWM_STARTUPS );
            LOGGER.debug( sessionLabel, () -> "buildTime=" + PwmConstants.BUILD_TIME + ", javaLocale=" + Locale.getDefault() + ", DefaultLocale=" + PwmConstants.DEFAULT_LOCALE );

            pwmScheduler.immediateExecuteRunnableInNewThread( this::postInitTasks, sessionLabel, this.getClass().getSimpleName() + " postInit tasks" );
        }
    }

    public void reInit( final PwmEnvironment pwmEnvironment )
            throws PwmException
    {
        final Instant startTime = Instant.now();
        LOGGER.trace( sessionLabel, () -> "beginning application restart" );
        final AppConfig oldConfig = this.pwmEnvironment.getConfig();
        this.pwmEnvironment = pwmEnvironment;
        final AppConfig newConfig = this.pwmEnvironment.getConfig();

        if ( !Objects.equals( oldConfig.getValueHash(), newConfig.getValueHash() ) )
        {
            processPwmAppRestart( );
        }
        else
        {
            LOGGER.debug( sessionLabel, () -> "no system-level settings have been changed, restart of system services is not required" );
        }

        PwmDomainUtil.reInitDomains( this, newConfig, oldConfig );

        runtimeNonce = PwmApplicationUtil.makeRuntimeNonce();

        LOGGER.debug( sessionLabel, () -> "completed application restart with " + domains().size() + " domains", TimeDuration.fromCurrent( startTime ) );
    }

    private void postInitTasks()
    {
        final Instant startTime = Instant.now();

        // send system audit event
        AuditServiceClient.submitSystemEvent( this, sessionLabel, AuditEvent.STARTUP );

        try
        {
            this.getAdminDomain().getIntruderService().clear( IntruderRecordType.USERNAME, PwmConstants.CONFIGMANAGER_INTRUDER_USERNAME );
        }
        catch ( final Exception e )
        {
            LOGGER.warn( sessionLabel, () -> "error while clearing config manager-intruder-username from intruder table: " + e.getMessage() );
        }

        PwmApplicationUtil.outputKeystore( this );
        PwmApplicationUtil.outputTomcatConf( this );

        LOGGER.debug( sessionLabel, () -> "application environment parameters: "
                + StringUtil.mapToString( pwmEnvironment.readProperties() ) );

        PwmApplicationUtil.outputApplicationInfoToLog( this );
        PwmApplicationUtil.outputConfigurationToLog( this, DomainID.systemId() );
        PwmApplicationUtil.outputNonDefaultPropertiesToLog( this );

        MBeanUtility.registerMBean( this );

        UserAgentUtils.initializeCache();

        LOGGER.trace( sessionLabel, () -> "completed post init tasks", TimeDuration.fromCurrent( startTime ) );
    }

    public static PwmApplication createPwmApplication( final PwmEnvironment pwmEnvironment )
            throws PwmUnrecoverableException
    {
        return new PwmApplication( pwmEnvironment );
    }

    public SessionLabel getSessionLabel()
    {
        return sessionLabel;
    }

    public Map<DomainID, PwmDomain> domains()
    {
        return domains;
    }

    protected void setDomains( final Map<DomainID, PwmDomain> domains )
    {
        this.domains = Map.copyOf( domains );
    }

    public AppConfig getConfig()
    {
        return pwmEnvironment.getConfig();
    }

    public PwmEnvironment getPwmEnvironment()
    {
        return pwmEnvironment;
    }

    public int getTotalActiveServletRequests( )
    {
        return domains().values().stream().map( domain -> domain.getActiveServletRequests().get() )
                .reduce( 0, Integer::sum );
    }

    public PwmApplicationMode getApplicationMode( )
    {
        return pwmEnvironment.getApplicationMode();
    }

    public String getRuntimeNonce( )
    {
        return runtimeNonce;
    }

    public PwmDomain getAdminDomain()
            throws PwmUnrecoverableException
    {
        return domains().get( getConfig().getAdminDomainID() );
    }

    private void processPwmAppRestart()
            throws PwmUnrecoverableException
    {
        LOGGER.debug( sessionLabel, () -> "system config settings modified, system services restart required" );
        AuditServiceClient.submitSystemEvent( this, sessionLabel, AuditEvent.RESTART );

        initRuntimeNonce();

        pwmServiceManager.shutdownAllServices();


        // initialize logger
        PwmApplicationUtil.initializeLogging( this );

        pwmServiceManager.initAllServices();

        if ( !pwmEnvironment.isInternalRuntimeInstance() )
        {
            PwmApplicationUtil.outputKeystore( this );
            PwmApplicationUtil.outputTomcatConf( this );
        }
    }

    public void shutdown( )
    {
        final Instant startTime = Instant.now();

        LOGGER.warn( sessionLabel, () -> "shutting down" );
        AuditServiceClient.submitSystemEvent( this, sessionLabel, AuditEvent.SHUTDOWN );

        MBeanUtility.unregisterMBean( this );

        try
        {
            final List<Callable<Object>> callables = domains.values().stream()
                    .map( pwmDomain -> Executors.callable( pwmDomain::shutdown ) )
                    .collect( Collectors.toList() );

            final Instant startDomainShutdown = Instant.now();
            LOGGER.trace( sessionLabel, () -> "beginning shutdown of " + callables.size() + " running domains" );
            pwmScheduler.executeImmediateThreadPerJobAndAwaitCompletion( DOMAIN_STARTUP_THREADS, callables, sessionLabel, PwmApplication.class );
            LOGGER.trace( sessionLabel, () -> "shutdown of " + callables.size() + " running domains completed", TimeDuration.fromCurrent( startDomainShutdown ) );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.error( sessionLabel, () -> "error shutting down domain services: " + e.getMessage(), e );
        }

        pwmServiceManager.shutdownAllServices();

        if ( localDBLogger != null )
        {
            try
            {
                localDBLogger.shutdownImpl();
            }
            catch ( final Exception e )
            {
                LOGGER.error( sessionLabel, () -> "error closing localDBLogger: " + e.getMessage(), e );
            }
            localDBLogger = null;
        }

        if ( localDB != null )
        {
            try
            {
                final Instant startCloseDbTime = Instant.now();
                LOGGER.debug( sessionLabel, () -> "beginning close of LocalDB" );
                localDB.close();
                final TimeDuration closeLocalDbDuration = TimeDuration.fromCurrent( startCloseDbTime );
                if ( closeLocalDbDuration.isLongerThan( TimeDuration.SECONDS_10 ) )
                {
                    LOGGER.info( sessionLabel, () -> "completed close of LocalDB", closeLocalDbDuration );
                }
            }
            catch ( final Exception e )
            {
                LOGGER.fatal( () -> "error closing localDB: " + e, e );
            }
            localDB = null;
        }

        if ( fileLocker != null )
        {
            fileLocker.releaseFileLock();
        }

        pwmScheduler.shutdown();

        LOGGER.info( sessionLabel, () -> PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION
                + " closed for bidness, cya!", TimeDuration.fromCurrent( startTime ) );

        if ( !pwmEnvironment.isInternalRuntimeInstance() )
        {
            PwmLogManager.disableLogging();
        }
    }

    public String getInstanceID( )
    {
        return instanceID;
    }


    public <T> Optional<T> readAppAttribute( final AppAttribute appAttribute, final Class<T> returnClass )
    {
        final LocalDB localDB = getLocalDB();

        if ( localDB == null || localDB.status() != LocalDB.Status.OPEN )
        {
            return Optional.empty();
        }

        if ( appAttribute == null )
        {
            return Optional.empty();
        }

        try
        {
            final Optional<String> strValue = localDB.get( LocalDB.DB.PWM_META, appAttribute.getKey() );
            if ( strValue.isPresent() )
            {
                return Optional.of( JsonFactory.get().deserialize( strValue.get(), returnClass ) );
            }
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error retrieving key '" + appAttribute.getKey() + "' value from localDB: " + e.getMessage() );
        }
        return Optional.empty();
    }

    public void writeLastLdapFailure( final DomainID domainID, final Map<ProfileID, ErrorInformation> errorInformationMap )
    {
        try
        {
            final StoredErrorRecords currentRecords = readLastLdapFailure();
            final StoredErrorRecords updatedRecords = currentRecords.addDomainErrorMap( domainID, errorInformationMap );
            writeAppAttribute( AppAttribute.LAST_LDAP_ERROR, JsonFactory.get().serialize( updatedRecords, StoredErrorRecords.class ) );
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "unexpected error writing lastLdapFailure statuses: " + e.getMessage() );
        }
    }

    public Map<ProfileID, ErrorInformation> readLastLdapFailure( final DomainID domainID )
    {
        return readLastLdapFailure().getRecords().getOrDefault( domainID, Collections.emptyMap() );
    }

    private StoredErrorRecords readLastLdapFailure()
    {
        try
        {
            final TimeDuration maxAge = TimeDuration.of(
                    Long.parseLong( getConfig().readAppProperty( AppProperty.HEALTH_LDAP_ERROR_LIFETIME_MS ) ),
                    TimeDuration.Unit.MILLISECONDS );
            final Optional<String> optionalLastLdapError = readAppAttribute( AppAttribute.LAST_LDAP_ERROR, String.class );
            if ( optionalLastLdapError.isPresent() && !StringUtil.isEmpty( optionalLastLdapError.get() ) )
            {
                final String lastLdapFailureStr = optionalLastLdapError.get();
                final StoredErrorRecords records = JsonFactory.get().deserialize( lastLdapFailureStr, StoredErrorRecords.class );
                return records.stripOutdatedLdapErrors( maxAge );
            }
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "unexpected error loading cached lastLdapFailure statuses: " + e.getMessage() );
        }
        return new StoredErrorRecords( Collections.emptyMap() );
    }

    @Value
    private static class StoredErrorRecords
    {
        private final Map<DomainID, Map<ProfileID, ErrorInformation>> records;

        StoredErrorRecords( final Map<DomainID, Map<ProfileID, ErrorInformation>> records )
        {
            this.records = records == null ? Collections.emptyMap() : Map.copyOf( records );
        }

        public Map<DomainID, Map<ProfileID, ErrorInformation>> getRecords()
        {
            // required because json deserialization can still set records == null
            return records == null ? Collections.emptyMap() : records;
        }

        StoredErrorRecords addDomainErrorMap(
                final DomainID domainID,
                final Map<ProfileID, ErrorInformation> errorInformationMap )
        {
            final Map<DomainID, Map<ProfileID, ErrorInformation>> newRecords = new HashMap<>( getRecords() );
            newRecords.put( domainID, Map.copyOf( errorInformationMap ) );
            return new StoredErrorRecords( newRecords );
        }

        StoredErrorRecords stripOutdatedLdapErrors( final TimeDuration maxAge )
        {
            return new StoredErrorRecords( getRecords().entrySet().stream()
                    // outer map
                    .collect( Collectors.toUnmodifiableMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().entrySet().stream()

                                    // keep outdated entries
                                    .filter( innerEntry -> TimeDuration.fromCurrent( innerEntry.getValue().getDate() ).isShorterThan( maxAge ) )

                                    // inner map
                                    .collect( Collectors.toUnmodifiableMap(
                                            Map.Entry::getKey,
                                            Map.Entry::getValue ) ) ) ) );
        }
    }


    public void writeAppAttribute( final AppAttribute appAttribute, final Object value )
    {
        final LocalDB localDB = getLocalDB();

        if ( localDB == null || localDB.status() != LocalDB.Status.OPEN )
        {
            return;
        }

        if ( appAttribute == null )
        {
            return;
        }

        try
        {
            if ( value == null )
            {
                localDB.remove( LocalDB.DB.PWM_META, appAttribute.getKey() );
            }
            else
            {
                final String jsonValue = JsonFactory.get().serialize( value );
                localDB.put( LocalDB.DB.PWM_META, appAttribute.getKey(), jsonValue );
            }
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error retrieving key '" + appAttribute.getKey() + "' from localDB: " + e.getMessage() );
            try
            {
                localDB.remove( LocalDB.DB.PWM_META, appAttribute.getKey() );
            }
            catch ( final Exception e2 )
            {
                LOGGER.error( () -> "error removing bogus appAttribute value for key " + appAttribute.getKey() + ", error: " + localDB );
            }
        }
    }


    private Instant fetchInstallDate( final Instant startupTime )
    {
        if ( localDB != null )
        {
            try
            {
                final Optional<String> storedDateStr = readAppAttribute( AppAttribute.INSTALL_DATE, String.class );
                if ( storedDateStr.isPresent() )
                {
                    return Instant.ofEpochMilli( Long.parseLong( storedDateStr.get() ) );
                }
                else
                {
                    writeAppAttribute( AppAttribute.INSTALL_DATE, String.valueOf( startupTime.toEpochMilli() ) );
                }
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "error retrieving installation date from localDB: " + e.getMessage() );
            }
        }
        return Instant.now();
    }

    public SharedHistoryService getSharedHistoryManager( )
    {
        return ( SharedHistoryService ) pwmServiceManager.getService( PwmServiceEnum.SharedHistoryManager );
    }

    public IntruderSystemService getIntruderSystemService( ) throws PwmUnrecoverableException
    {
        return ( IntruderSystemService ) pwmServiceManager.getService( PwmServiceEnum.IntruderSystemService );
    }

    public LocalDBLogger getLocalDBLogger( )
    {
        return localDBLogger;
    }

    public HealthService getHealthMonitor( )
    {
        return ( HealthService ) pwmServiceManager.getService( PwmServiceEnum.HealthMonitor );
    }

    public HttpClientService getHttpClientService()
    {
        return ( HttpClientService ) pwmServiceManager.getService( PwmServiceEnum.HttpClientService );
    }

    public List<PwmService> getPwmServices( )
    {
        final List<PwmService> pwmServices = new ArrayList<>( this.pwmServiceManager.getRunningServices() );
        pwmServices.add( this.localDBLogger );
        return Collections.unmodifiableList( pwmServices );
    }

    public Map<DomainID, List<PwmService>> getAppAndDomainPwmServices( )
    {
        final Map<DomainID, List<PwmService>> pwmServices = new LinkedHashMap<>();

        for ( final PwmService pwmService : getPwmServices() )
        {
            pwmServices.computeIfAbsent( DomainID.systemId(), k -> new ArrayList<>() ).add( pwmService );
        }

        for ( final PwmDomain pwmDomain : domains().values() )
        {
            for ( final PwmService pwmService : pwmDomain.getPwmServices() )
            {
                pwmServices.computeIfAbsent( pwmDomain.getDomainID(), k -> new ArrayList<>() ).add( pwmService );
            }
        }

        return Collections.unmodifiableMap( pwmServices );
    }

    public WordlistService getWordlistService( )
    {
        return ( WordlistService ) pwmServiceManager.getService( PwmServiceEnum.WordlistService );
    }

    public EmailService getEmailQueue( )
    {
        return ( EmailService ) pwmServiceManager.getService( PwmServiceEnum.EmailService );
    }

    public AuditService getAuditService( )
    {
        return ( AuditService ) pwmServiceManager.getService( PwmServiceEnum.AuditService );
    }

    public SmsQueueService getSmsQueue( )
    {
        return ( SmsQueueService ) pwmServiceManager.getService( PwmServiceEnum.SmsQueueManager );
    }

    public UrlShortenerService getUrlShortener( )
    {
        return ( UrlShortenerService ) pwmServiceManager.getService( PwmServiceEnum.UrlShortenerService );
    }

    public NodeService getNodeService( )
    {
        return ( NodeService ) pwmServiceManager.getService( PwmServiceEnum.NodeService );
    }

    public ErrorInformation getLastLocalDBFailure( )
    {
        return lastLocalDBFailure;
    }

    void setLastLocalDBFailure( final ErrorInformation lastLocalDBFailure )
    {
        this.lastLocalDBFailure = lastLocalDBFailure;
    }

    public SessionTrackService getSessionTrackService( )
    {
        return ( SessionTrackService ) pwmServiceManager.getService( PwmServiceEnum.SessionTrackService );
    }

    public DatabaseAccessor getDatabaseAccessor( )

            throws PwmUnrecoverableException
    {
        return getDatabaseService().getAccessor();
    }

    public DatabaseService getDatabaseService( )
    {
        return ( DatabaseService ) pwmServiceManager.getService( PwmServiceEnum.DatabaseService );
    }

    public StatisticsService getStatisticsService( )
    {
        return ( StatisticsService ) pwmServiceManager.getService( PwmServiceEnum.StatisticsService );
    }

    public SessionStateService getSessionStateService( )
    {
        return ( SessionStateService ) pwmServiceManager.getService( PwmServiceEnum.SessionStateSvc );
    }

    public SystemSecureService getSecureService( )
    {
        return ( SystemSecureService ) pwmServiceManager.getService( PwmServiceEnum.SystemSecureService );
    }

    public Instant getStartupTime( )
    {
        return startupTime;
    }

    public Instant getInstallTime( )
    {
        return installTime;
    }

    public LocalDB getLocalDB( )
    {
        return localDB;
    }

    public boolean isMultiDomain()
    {
        return this.getConfig().isMultiDomain();
    }

    public Path getTempDirectory( )
            throws PwmUnrecoverableException
    {
        if ( pwmEnvironment.getApplicationPath() == null )
        {
            final ErrorInformation errorInformation = new ErrorInformation(
                    PwmError.ERROR_STARTUP_ERROR,
                    "unable to establish temp work directory: application path unavailable"
            );
            throw new PwmUnrecoverableException( errorInformation );
        }
        final Path tempDirectory = pwmEnvironment.getApplicationPath().resolve( "temp" );
        if ( !Files.exists( tempDirectory ) )
        {
            LOGGER.trace( () -> "preparing to create temporary directory " + tempDirectory );
            try
            {
                Files.createDirectories( tempDirectory );
                LOGGER.debug( () -> "created " + tempDirectory );
            }
            catch ( final IOException e )
            {
                LOGGER.debug( () -> "unable to create temporary directory " + tempDirectory );
                final ErrorInformation errorInformation = new ErrorInformation(
                        PwmError.ERROR_STARTUP_ERROR,
                        "unable to establish create temp work directory " + tempDirectory );
                throw new PwmUnrecoverableException( errorInformation );
            }
        }
        return tempDirectory;
    }


    public PwmScheduler getPwmScheduler()
    {
        return pwmScheduler;
    }

    public boolean determineIfDetailErrorMsgShown( )
    {
        final PwmApplicationMode mode = this.getApplicationMode();
        if ( mode == PwmApplicationMode.CONFIGURATION || mode == PwmApplicationMode.NEW )
        {
            return true;
        }
        if ( mode == PwmApplicationMode.RUNNING )
        {
            if ( this.getConfig() != null )
            {
                if ( this.getConfig().readSettingAsBoolean( PwmSetting.DISPLAY_SHOW_DETAILED_ERRORS ) )
                {
                    return true;
                }
            }
        }
        return false;
    }

    public enum Condition
    {
        RunningMode( ( pwmApplication ) -> pwmApplication.getApplicationMode() == PwmApplicationMode.RUNNING ),
        LocalDBOpen( ( pwmApplication ) -> pwmApplication.getLocalDB() != null && LocalDB.Status.OPEN == pwmApplication.getLocalDB().status() ),
        NotInternalInstance( pwmApplication -> !pwmApplication.getPwmEnvironment().isInternalRuntimeInstance() ),;

        private final Function<PwmApplication, Boolean> function;

        Condition( final Function<PwmApplication, Boolean> function )
        {
            this.function = function;
        }

        private boolean matches( final PwmApplication pwmApplication )
        {
            return function.apply( pwmApplication );
        }
    }

    public boolean checkConditions( final Set<Condition> conditions )
    {
        if ( conditions == null )
        {
            return true;
        }

        return conditions.stream().allMatch( ( c ) -> c.matches( this ) );
    }


}
