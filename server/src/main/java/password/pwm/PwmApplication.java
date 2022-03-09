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
import password.pwm.bean.SessionLabel;
import password.pwm.bean.SmsItemBean;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingMetaDataReader;
import password.pwm.config.PwmSettingScope;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthService;
import password.pwm.http.state.SessionStateService;
import password.pwm.svc.PwmService;
import password.pwm.svc.PwmServiceEnum;
import password.pwm.svc.PwmServiceManager;
import password.pwm.svc.cache.CacheService;
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
import password.pwm.svc.report.ReportService;
import password.pwm.svc.secure.SystemSecureService;
import password.pwm.svc.sessiontrack.SessionTrackService;
import password.pwm.svc.sessiontrack.UserAgentUtils;
import password.pwm.svc.shorturl.UrlShortenerService;
import password.pwm.svc.sms.SmsQueueService;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.svc.stats.StatisticsService;
import password.pwm.svc.wordlist.SeedlistService;
import password.pwm.svc.wordlist.SharedHistoryService;
import password.pwm.svc.wordlist.WordlistService;
import password.pwm.util.MBeanUtility;
import password.pwm.util.PasswordData;
import password.pwm.util.PwmScheduler;
import password.pwm.util.cli.commands.ExportHttpsTomcatConfigCommand;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBFactory;
import password.pwm.util.logging.LocalDBLogger;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogManager;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.secure.HttpsServerCertificateManager;
import password.pwm.util.secure.PwmRandom;
import password.pwm.util.secure.X509Utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URI;
import java.nio.file.Files;
import java.security.KeyStore;
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
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PwmApplication
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmApplication.class );
    private static final String DEFAULT_INSTANCE_ID = "-1";

    private final AtomicInteger activeServletRequests = new AtomicInteger( 0 );

    private Map<DomainID, PwmDomain> domains;
    private String runtimeNonce = PwmRandom.getInstance().randomUUID().toString();

    private final PwmServiceManager pwmServiceManager = new PwmServiceManager(
            SessionLabel.SYSTEM_LABEL,
            this, DomainID.systemId(), PwmServiceEnum.forScope( PwmSettingScope.SYSTEM ) );

    private final Instant startupTime = Instant.now();
    private Instant installTime = Instant.now();
    private ErrorInformation lastLocalDBFailure;
    private PwmEnvironment pwmEnvironment;
    private FileLocker fileLocker;
    private PwmScheduler pwmScheduler;
    private String instanceID = DEFAULT_INSTANCE_ID;
    private LocalDB localDB;
    private LocalDBLogger localDBLogger;


    public PwmApplication( final PwmEnvironment pwmEnvironment )
            throws PwmUnrecoverableException
    {
        this.pwmEnvironment = Objects.requireNonNull( pwmEnvironment );

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
            LOGGER.fatal( e::getMessage );
            throw e;
        }
    }

    public static Optional<String> deriveLocalServerHostname( final AppConfig appConfig )
    {
        if ( appConfig != null )
        {
            final String siteUrl = appConfig.readSettingAsString( PwmSetting.PWM_SITE_URL );
            if ( StringUtil.notEmpty( siteUrl ) )
            {
                try
                {
                    final URI parsedUri = URI.create( siteUrl );
                    {
                        final String uriHost = parsedUri.getHost();
                        return Optional.ofNullable( uriHost );
                    }
                }
                catch ( final IllegalArgumentException e )
                {
                    LOGGER.trace( () -> " error parsing siteURL hostname: " + e.getMessage() );
                }
            }
        }
        return Optional.empty();
    }

    private void initialize( )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        runtimeNonce = PwmRandom.getInstance().randomUUID().toString();

        this.domains = Initializer.initializeDomains( this );

        // initialize log4j
        Initializer.initializeLogging( this );

        // get file lock
        if ( !pwmEnvironment.isInternalRuntimeInstance() )
        {
            fileLocker = new FileLocker( pwmEnvironment );
            fileLocker.waitForFileLock();
        }

        // clear temp dir
        if ( !pwmEnvironment.isInternalRuntimeInstance() )
        {
            final File tempFileDirectory = getTempDirectory();
            try
            {
                LOGGER.debug( () -> "deleting directory (and sub-directory) contents in " + tempFileDirectory );
                FileSystemUtility.deleteDirectoryContentsRecursively( tempFileDirectory.toPath() );
            }
            catch ( final Exception e )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_STARTUP_ERROR,
                        "unable to clear temp file directory '" + tempFileDirectory.getAbsolutePath() + "', error: " + e.getMessage()
                ) );
            }
        }

        if ( getApplicationMode() != PwmApplicationMode.READ_ONLY )
        {
            LOGGER.info( () -> "initializing, application mode=" + getApplicationMode()
                    + ", applicationPath=" + ( pwmEnvironment.getApplicationPath() == null ? "null" : pwmEnvironment.getApplicationPath().getAbsolutePath() )
                    + ", configFile=" + ( pwmEnvironment.getConfigurationFile() == null ? "null" : pwmEnvironment.getConfigurationFile().getAbsolutePath() )
            );
        }

        if ( !pwmEnvironment.isInternalRuntimeInstance() )
        {
            if ( getApplicationMode() == PwmApplicationMode.ERROR )
            {
                LOGGER.warn( () -> "skipping LocalDB open due to application mode " + getApplicationMode() );
            }
            else
            {
                if ( localDB == null )
                {
                    this.localDB = Initializer.initializeLocalDB( this, pwmEnvironment );
                }
            }
        }

        this.localDBLogger = PwmLogManager.initializeLocalDBLogger( this );

        // log the loaded configuration
        LOGGER.debug( () -> "configuration load completed" );

        // read the pwm servlet instance id
        instanceID = fetchInstanceID( localDB, this );
        LOGGER.debug( () -> "using '" + getInstanceID() + "' for instance's ID (instanceID)" );

        // read the pwm installation date
        installTime = fetchInstallDate( startupTime );
        LOGGER.debug( () -> "this application instance first installed on " + StringUtil.toIsoDate( installTime ) );

        LOGGER.debug( () -> "application environment flags: " + JsonFactory.get().serializeCollection( pwmEnvironment.getFlags() ) );
        LOGGER.debug( () -> "application environment parameters: "
                + JsonFactory.get().serializeMap( pwmEnvironment.getParameters(), PwmEnvironment.ApplicationParameter.class, String.class ) );

        pwmScheduler = new PwmScheduler( this );

        pwmServiceManager.initAllServices();

        initAllDomains();

        final boolean skipPostInit = pwmEnvironment.isInternalRuntimeInstance()
                || pwmEnvironment.getFlags().contains( PwmEnvironment.ApplicationFlag.CommandLineInstance );

        if ( !skipPostInit )
        {
            final TimeDuration totalTime = TimeDuration.fromCurrent( startTime );
            LOGGER.info( () -> PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION + " open for bidness! (" + totalTime.asCompactString() + ")" );
            StatisticsClient.incrementStat( this, Statistic.PWM_STARTUPS );
            LOGGER.debug( () -> "buildTime=" + PwmConstants.BUILD_TIME + ", javaLocale=" + Locale.getDefault() + ", DefaultLocale=" + PwmConstants.DEFAULT_LOCALE );

            pwmScheduler.immediateExecuteRunnableInNewThread( this::postInitTasks, this.getClass().getSimpleName() + " postInit tasks" );
        }

    }

    private void initAllDomains()
            throws PwmUnrecoverableException
    {
        final Instant domainInitStartTime = Instant.now();
        LOGGER.trace( () -> "beginning domain initializations" );

        final List<Callable<?>> callables = domains.values().stream().<Callable<?>>map( pwmDomain -> () ->
        {
            pwmDomain.initialize();
            return null;
        } ).collect( Collectors.toList() );
        pwmScheduler.executeImmediateThreadPerJobAndAwaitCompletion( callables, "domain initializer" );

        LOGGER.trace( () -> "completed domain initialization for all domains", () -> TimeDuration.fromCurrent( domainInitStartTime ) );
    }


    public void reInit( final PwmEnvironment pwmEnvironment )
            throws PwmException
    {
        final Instant startTime = Instant.now();
        LOGGER.debug( () -> "beginning application restart" );
        shutdown( true );
        this.pwmEnvironment = pwmEnvironment;
        initialize();
        LOGGER.debug( () -> "completed application restart", () -> TimeDuration.fromCurrent( startTime ) );
    }

    private void postInitTasks( )
    {
        final Instant startTime = Instant.now();

        getPwmScheduler().immediateExecuteRunnableInNewThread( UserAgentUtils::initializeCache, "initialize useragent cache" );
        getPwmScheduler().immediateExecuteRunnableInNewThread( PwmSettingMetaDataReader::initCache, "initialize PwmSetting cache" );

        if ( Boolean.parseBoolean( getConfig().readAppProperty( AppProperty.LOGGING_OUTPUT_CONFIGURATION ) ) )
        {
            outputConfigurationToLog( this );
            outputNonDefaultPropertiesToLog( this );
        }

        // send system audit event
        AuditServiceClient.submitSystemEvent( this, SessionLabel.SYSTEM_LABEL, AuditEvent.STARTUP );

        try
        {
            final Map<PwmAboutProperty, String> infoMap = PwmAboutProperty.makeInfoBean( this );
            LOGGER.trace( () ->  "application info: " + JsonFactory.get().serializeMap( infoMap, PwmAboutProperty.class, String.class ) );
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error generating about application bean: " + e.getMessage(), e );
        }

        try
        {
            this.getAdminDomain().getIntruderService().clear( IntruderRecordType.USERNAME, PwmConstants.CONFIGMANAGER_INTRUDER_USERNAME );
        }
        catch ( final Exception e )
        {
            LOGGER.warn( () -> "error while clearing configmanager-intruder-username from intruder table: " + e.getMessage() );
        }

        if ( !pwmEnvironment.isInternalRuntimeInstance() )
        {
            try
            {
                outputKeystore( this );
            }
            catch ( final Exception e )
            {
                LOGGER.debug( () -> "error while generating keystore output: " + e.getMessage() );
            }

            try
            {
                outputTomcatConf( this );
            }
            catch ( final Exception e )
            {
                LOGGER.debug( () -> "error while generating tomcat conf output: " + e.getMessage() );
            }
        }

        if ( Boolean.parseBoolean( getConfig().readAppProperty( AppProperty.LOGGING_OUTPUT_CONFIGURATION ) ) )
        {
            getPwmScheduler().immediateExecuteRunnableInNewThread( () ->
            {
                outputConfigurationToLog( this );
                outputNonDefaultPropertiesToLog( this );
            }, "output configuration to log" );
        }

        MBeanUtility.registerMBean( this );
        LOGGER.trace( () -> "completed post init tasks", () -> TimeDuration.fromCurrent( startTime ) );
    }

    public static PwmApplication createPwmApplication( final PwmEnvironment pwmEnvironment )
            throws PwmUnrecoverableException
    {
        return new PwmApplication( pwmEnvironment );
    }

    public Map<DomainID, PwmDomain> domains()
    {
        return domains;
    }

    public AppConfig getConfig()
    {
        return pwmEnvironment.getConfig();
    }

    public PwmEnvironment getPwmEnvironment()
    {
        return pwmEnvironment;
    }

    public AtomicInteger getActiveServletRequests( )
    {
        return activeServletRequests;
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

    public void shutdown( )
    {
        shutdown( false );
    }

    public void shutdown( final boolean keepServicesRunning )
    {
        final Instant startTime = Instant.now();

        if ( keepServicesRunning )
        {
            LOGGER.warn( () -> "preparing for restart" );
            AuditServiceClient.submitSystemEvent( this, SessionLabel.SYSTEM_LABEL, AuditEvent.RESTART );
        }
        else
        {
            LOGGER.warn( () -> "shutting down" );
            AuditServiceClient.submitSystemEvent( this, SessionLabel.SYSTEM_LABEL, AuditEvent.SHUTDOWN );
        }

        MBeanUtility.unregisterMBean( this );

        if ( !keepServicesRunning )
        {
            try
            {
                final List<Callable<?>> callables = domains.values().stream().<Callable<?>>map( pwmDomain -> () ->
                {
                    pwmDomain.shutdown();
                    return null;
                } ).collect( Collectors.toList() );
                pwmScheduler.executeImmediateThreadPerJobAndAwaitCompletion( callables, "domain shutdown task" );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( () -> "error shutting down domain services: " + e.getMessage(), e );
            }

            pwmServiceManager.shutdownAllServices();
        }

        if ( localDBLogger != null )
        {
            try
            {
                localDBLogger.close();
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "error closing localDBLogger: " + e.getMessage(), e );
            }
            localDBLogger = null;
        }

        if ( keepServicesRunning )
        {
            LOGGER.trace( () -> "skipping close of LocalDB (restart request)" );
        }
        else if ( localDB != null )
        {
            try
            {
                final Instant startCloseDbTime = Instant.now();
                LOGGER.debug( () -> "beginning close of LocalDB" );
                localDB.close();
                final TimeDuration closeLocalDbDuration = TimeDuration.fromCurrent( startCloseDbTime );
                if ( closeLocalDbDuration.isLongerThan( TimeDuration.SECONDS_10 ) )
                {
                    LOGGER.info( () -> "completed close of LocalDB", () -> closeLocalDbDuration );
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

        LOGGER.info( () -> PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION
                + " closed for bidness, cya!", () -> TimeDuration.fromCurrent( startTime ) );
    }

    private static void outputKeystore( final PwmApplication pwmApplication ) throws Exception
    {
        final Map<PwmEnvironment.ApplicationParameter, String> applicationParams = pwmApplication.getPwmEnvironment().getParameters();
        final String keystoreFileString = applicationParams.get( PwmEnvironment.ApplicationParameter.AutoExportHttpsKeyStoreFile );
        if ( StringUtil.isEmpty( keystoreFileString ) )
        {
            return;
        }

        final File keyStoreFile = new File( keystoreFileString );
        final String password = applicationParams.get( PwmEnvironment.ApplicationParameter.AutoExportHttpsKeyStorePassword );
        final String alias = applicationParams.get( PwmEnvironment.ApplicationParameter.AutoExportHttpsKeyStoreAlias );
        final KeyStore keyStore = HttpsServerCertificateManager.keyStoreForApplication( pwmApplication, new PasswordData( password ), alias );
        X509Utils.outputKeystore( keyStore, keyStoreFile, password );
        PwmApplication.LOGGER.info( () -> "exported application https key to keystore file " + keyStoreFile.getAbsolutePath() );
    }

    private static void outputTomcatConf( final PwmApplication pwmDomain ) throws IOException
    {
        final Map<PwmEnvironment.ApplicationParameter, String> applicationParams = pwmDomain.getPwmEnvironment().getParameters();
        final String tomcatOutputFileStr = applicationParams.get( PwmEnvironment.ApplicationParameter.AutoWriteTomcatConfOutputFile );
        if ( tomcatOutputFileStr != null && !tomcatOutputFileStr.isEmpty() )
        {
            LOGGER.trace( () -> "attempting to output tomcat configuration file as configured by environment parameters to " + tomcatOutputFileStr );
            final File tomcatOutputFile = new File( tomcatOutputFileStr );
            final File tomcatSourceFile;
            {
                final String tomcatSourceFileStr = applicationParams.get( PwmEnvironment.ApplicationParameter.AutoWriteTomcatConfSourceFile );
                if ( tomcatSourceFileStr != null && !tomcatSourceFileStr.isEmpty() )
                {
                    tomcatSourceFile = new File( tomcatSourceFileStr );
                    if ( !tomcatSourceFile.exists() )
                    {
                        LOGGER.error( () -> "can not output tomcat configuration file, source file does not exist: " + tomcatSourceFile.getAbsolutePath() );
                        return;
                    }
                }
                else
                {
                    LOGGER.error( () -> "can not output tomcat configuration file, source file parameter '"
                            + PwmEnvironment.ApplicationParameter.AutoWriteTomcatConfSourceFile.toString() + "' is not specified." );
                    return;
                }
            }

            try ( ByteArrayOutputStream outputContents = new ByteArrayOutputStream() )
            {
                try ( InputStream fileInputStream = Files.newInputStream( tomcatOutputFile.toPath() ) )
                {
                    ExportHttpsTomcatConfigCommand.TomcatConfigWriter.writeOutputFile(
                            pwmDomain.getConfig(),
                            fileInputStream,
                            outputContents
                    );
                }

                if ( tomcatOutputFile.exists() )
                {
                    LOGGER.trace( () -> "deleting existing tomcat configuration file " + tomcatOutputFile.getAbsolutePath() );
                    if ( tomcatOutputFile.delete() )
                    {
                        LOGGER.trace( () -> "deleted existing tomcat configuration file: " + tomcatOutputFile.getAbsolutePath() );
                    }
                }

                try ( OutputStream fileOutputStream = Files.newOutputStream( tomcatOutputFile.toPath() ) )
                {
                    fileOutputStream.write( outputContents.toByteArray() );
                }
            }

            LOGGER.info( () -> "successfully wrote tomcat configuration to file " + tomcatOutputFile.getAbsolutePath() );
        }
    }

    private static void outputConfigurationToLog( final PwmApplication pwmApplication )
    {
        final Instant startTime = Instant.now();

        final Function<Map.Entry<String, String>, String> valueFormatter = entry ->
        {
            final String spacedValue = entry.getValue().replace( "\n", "\n   " );
            return " " + entry.getKey() + "\n   " + spacedValue + "\n";
        };

        final StoredConfiguration storedConfiguration = pwmApplication.getConfig().getStoredConfiguration();
        final List<StoredConfigKey> keys = CollectionUtil.iteratorToStream( storedConfiguration.keys() ).collect( Collectors.toList() );
        final Map<String, String> debugStrings = StoredConfigurationUtil.makeDebugMap(
                storedConfiguration,
                keys,
                PwmConstants.DEFAULT_LOCALE );

        LOGGER.trace( () -> "--begin current configuration output--" );
        final long itemCount = debugStrings.entrySet().stream()
                .map( valueFormatter )
                .map( s -> ( Supplier<CharSequence> ) () -> s )
                .peek( LOGGER::trace )
                .count();

        LOGGER.trace( () -> "--end current configuration output of " + itemCount + " items --",
                () -> TimeDuration.fromCurrent( startTime ) );
    }

    private static void outputNonDefaultPropertiesToLog( final PwmApplication pwmApplication )
    {
        final Instant startTime = Instant.now();

        final Map<AppProperty, String> nonDefaultProperties = pwmApplication.getConfig().readAllNonDefaultAppProperties();
        if ( !CollectionUtil.isEmpty( nonDefaultProperties ) )
        {
            LOGGER.trace( () -> "--begin non-default app properties output--" );
            nonDefaultProperties.entrySet().stream()
                    .map( entry -> "AppProperty: " + entry.getKey().getKey() + " -> " + entry.getValue() )
                    .map( s -> ( Supplier<CharSequence> ) () -> s )
                    .forEach( LOGGER::trace );
            LOGGER.trace( () -> "--end non-default app properties output--", () -> TimeDuration.fromCurrent( startTime ) );
        }
        else
        {
            LOGGER.trace( () -> "no non-default app properties in configuration" );
        }
    }

    public String getInstanceID( )
    {
        return instanceID;
    }


    public <T extends Serializable> Optional<T> readAppAttribute( final AppAttribute appAttribute, final Class<T> returnClass )
    {
        final LocalDB localDB = getLocalDB();

        if ( localDB == null || localDB.status() != LocalDB.Status.OPEN )
        {
            LOGGER.debug( () -> "error retrieving key '" + appAttribute.getKey() + "', localDB unavailable: " );
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

    public void writeLastLdapFailure( final DomainID domainID, final Map<String, ErrorInformation> errorInformationMap )
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

    public Map<String, ErrorInformation> readLastLdapFailure( final DomainID domainID )
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
        private final Map<DomainID, Map<String, ErrorInformation>> records;

        StoredErrorRecords( final Map<DomainID, Map<String, ErrorInformation>> records )
        {
            this.records = records == null ? Collections.emptyMap() : Map.copyOf( records );
        }

        public Map<DomainID, Map<String, ErrorInformation>> getRecords()
        {
            // required because json deserialization can still set records == null
            return records == null ? Collections.emptyMap() : records;
        }

        StoredErrorRecords addDomainErrorMap(
                final DomainID domainID,
                final Map<String, ErrorInformation> errorInformationMap )
        {
            final Map<DomainID, Map<String, ErrorInformation>> newRecords = new HashMap<>( getRecords() );
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


    public void writeAppAttribute( final AppAttribute appAttribute, final Serializable value )
    {
        final LocalDB localDB = getLocalDB();

        if ( localDB == null || localDB.status() != LocalDB.Status.OPEN )
        {
            LOGGER.error( () -> "error writing key '" + appAttribute.getKey() + "', localDB unavailable: " );
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
            LOGGER.error( () -> "error retrieving key '" + appAttribute.getKey() + "' installation date from localDB: " + e.getMessage() );
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

    private String fetchInstanceID( final LocalDB localDB, final PwmApplication pwmApplication )
    {
        {
            final String newInstanceID = pwmApplication.getPwmEnvironment().getParameters().get( PwmEnvironment.ApplicationParameter.InstanceID );

            if ( !StringUtil.isTrimEmpty( newInstanceID ) )
            {
                return newInstanceID;
            }
        }

        if ( pwmApplication.getLocalDB() == null || pwmApplication.getApplicationMode() != PwmApplicationMode.RUNNING )
        {
            return DEFAULT_INSTANCE_ID;
        }

        {
            final Optional<String> optionalStoredInstanceID = readAppAttribute( AppAttribute.INSTANCE_ID, String.class );
            if ( optionalStoredInstanceID.isPresent() )
            {
                final String instanceID = optionalStoredInstanceID.get();
                if ( !StringUtil.isTrimEmpty( instanceID ) )
                {
                    LOGGER.trace( () -> "retrieved instanceID " + instanceID + "" + " from localDB" );
                    return instanceID;
                }
            }
        }

        final PwmRandom pwmRandom = PwmRandom.getInstance();
        final String newInstanceID = Long.toHexString( pwmRandom.nextLong() ).toUpperCase();
        LOGGER.debug( () -> "generated new random instanceID " + newInstanceID );

        if ( localDB != null )
        {
            writeAppAttribute( AppAttribute.INSTANCE_ID, newInstanceID );
        }

        return newInstanceID;
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

    public SeedlistService getSeedlistManager( )
    {
        return ( SeedlistService ) pwmServiceManager.getService( PwmServiceEnum.SeedlistService );
    }

    public ReportService getReportService( )
    {
        return ( ReportService ) pwmServiceManager.getService( PwmServiceEnum.ReportService );
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

    public StatisticsService getStatisticsManager( )
    {
        return ( StatisticsService ) pwmServiceManager.getService( PwmServiceEnum.StatisticsService );
    }

    public SessionStateService getSessionStateService( )
    {
        return ( SessionStateService ) pwmServiceManager.getService( PwmServiceEnum.SessionStateSvc );
    }


    public CacheService getCacheService( )
    {
        return ( CacheService ) pwmServiceManager.getService( PwmServiceEnum.CacheService );
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

    public void sendSmsUsingQueue(
            final String to,
            final String message,
            final SessionLabel sessionLabel,
            final MacroRequest macroRequest
    )
    {
        final SmsQueueService smsQueue = getSmsQueue();
        if ( smsQueue == null )
        {
            LOGGER.error( sessionLabel, () -> "SMS queue is unavailable, unable to send SMS to: " + to );
            return;
        }

        final SmsItemBean smsItemBean = new SmsItemBean(
                macroRequest.expandMacros( to ),
                macroRequest.expandMacros( message ),
                sessionLabel
        );

        try
        {
            smsQueue.addSmsToQueue( smsItemBean );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.warn( () -> "unable to add sms to queue: " + e.getMessage() );
        }
    }

    private static class Initializer
    {
        public static LocalDB initializeLocalDB( final PwmApplication pwmApplication, final PwmEnvironment pwmEnvironment )
                throws PwmUnrecoverableException
        {
            final File databaseDirectory;

            try
            {
                final String localDBLocationSetting = pwmApplication.getConfig().readAppProperty( AppProperty.LOCALDB_LOCATION );
                databaseDirectory = FileSystemUtility.figureFilepath( localDBLocationSetting, pwmApplication.pwmEnvironment.getApplicationPath() );
            }
            catch ( final Exception e )
            {
                pwmApplication.lastLocalDBFailure = new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, "error locating configured LocalDB directory: " + e.getMessage() );
                LOGGER.warn( () -> pwmApplication.lastLocalDBFailure.toDebugStr() );
                throw new PwmUnrecoverableException( pwmApplication.lastLocalDBFailure );
            }

            LOGGER.debug( () -> "using localDB path " + databaseDirectory );

            // initialize the localDB
            try
            {
                final boolean readOnly = pwmApplication.getApplicationMode() == PwmApplicationMode.READ_ONLY;
                return LocalDBFactory.getInstance( databaseDirectory, readOnly, pwmEnvironment, pwmApplication.getConfig() );
            }
            catch ( final Exception e )
            {
                pwmApplication.lastLocalDBFailure = new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, "unable to initialize LocalDB: " + e.getMessage() );
                LOGGER.warn( () -> pwmApplication.lastLocalDBFailure.toDebugStr() );
                throw new PwmUnrecoverableException( pwmApplication.lastLocalDBFailure );
            }
        }

        private static Map<DomainID, PwmDomain> initializeDomains( final PwmApplication pwmApplication )
        {
            final Map<DomainID, PwmDomain> domainMap = new TreeMap<>();
            for ( final String domainIdString : pwmApplication.getPwmEnvironment().getConfig().getDomainIDs() )
            {
                final DomainID domainID = DomainID.create( domainIdString );
                final PwmDomain newDomain = new PwmDomain( pwmApplication, domainID );
                domainMap.put( domainID, newDomain );
            }

            return Collections.unmodifiableMap( domainMap );
        }

        public static void initializeLogging( final PwmApplication pwmApplication )
        {
            final PwmEnvironment pwmEnvironment = pwmApplication.getPwmEnvironment();

            if ( !pwmEnvironment.isInternalRuntimeInstance() && !pwmEnvironment.getFlags().contains( PwmEnvironment.ApplicationFlag.CommandLineInstance ) )
            {
                final String log4jFileName = pwmEnvironment.getConfig().readSettingAsString( PwmSetting.EVENTS_JAVA_LOG4JCONFIG_FILE );
                final File log4jFile = FileSystemUtility.figureFilepath( log4jFileName, pwmEnvironment.getApplicationPath() );
                final String consoleLevel;
                final String fileLevel;

                switch ( pwmApplication.getApplicationMode() )
                {
                    case ERROR:
                    case NEW:
                        consoleLevel = PwmLogLevel.TRACE.toString();
                        fileLevel = PwmLogLevel.TRACE.toString();
                        break;

                    default:
                        consoleLevel = pwmEnvironment.getConfig().readSettingAsString( PwmSetting.EVENTS_JAVA_STDOUT_LEVEL );
                        fileLevel = pwmEnvironment.getConfig().readSettingAsString( PwmSetting.EVENTS_FILE_LEVEL );
                        break;
                }

                PwmLogManager.initializeLogger(
                        pwmApplication,
                        pwmApplication.getConfig(),
                        log4jFile,
                        consoleLevel,
                        pwmEnvironment.getApplicationPath(),
                        fileLevel );

                switch ( pwmApplication.getApplicationMode() )
                {
                    case RUNNING:
                        break;

                    case ERROR:
                        LOGGER.fatal( () -> "starting up in ERROR mode! Check log or health check information for cause" );
                        break;

                    default:
                        LOGGER.trace( () -> "setting log level to TRACE because application mode is " + pwmApplication.getApplicationMode() );
                        break;
                }
            }
        }
    }

    public File getTempDirectory( ) throws PwmUnrecoverableException
    {
        if ( pwmEnvironment.getApplicationPath() == null )
        {
            final ErrorInformation errorInformation = new ErrorInformation(
                    PwmError.ERROR_STARTUP_ERROR,
                    "unable to establish temp work directory: application path unavailable"
            );
            throw new PwmUnrecoverableException( errorInformation );
        }
        final File tempDirectory = new File( pwmEnvironment.getApplicationPath() + File.separator + "temp" );
        if ( !tempDirectory.exists() )
        {
            LOGGER.trace( () -> "preparing to create temporary directory " + tempDirectory.getAbsolutePath() );
            if ( tempDirectory.mkdir() )
            {
                LOGGER.debug( () -> "created " + tempDirectory.getAbsolutePath() );
            }
            else
            {
                LOGGER.debug( () -> "unable to create temporary directory " + tempDirectory.getAbsolutePath() );
                final ErrorInformation errorInformation = new ErrorInformation(
                        PwmError.ERROR_STARTUP_ERROR,
                        "unable to establish create temp work directory " + tempDirectory.getAbsolutePath()
                );
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
