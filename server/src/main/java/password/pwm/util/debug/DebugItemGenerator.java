/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.util.debug;

import lombok.Builder;
import lombok.Value;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.output.CountingOutputStream;
import password.pwm.AppProperty;
import password.pwm.PwmAboutProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.AppConfig;
import password.pwm.config.DomainConfig;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.config.value.StoredValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.http.ContextManager;
import password.pwm.http.servlet.admin.AppDashboardData;
import password.pwm.http.servlet.admin.UserDebugDataBean;
import password.pwm.http.servlet.admin.UserDebugDataReader;
import password.pwm.ldap.LdapConnectionService;
import password.pwm.ldap.LdapDebugDataGenerator;
import password.pwm.svc.PwmService;
import password.pwm.svc.cache.CacheService;
import password.pwm.svc.node.NodeService;
import password.pwm.svc.stats.EpsStatistic;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.LDAPPermissionCalculator;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.DebugOutputBuilder;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.logging.LocalDBSearchQuery;
import password.pwm.util.logging.LocalDBSearchResults;
import password.pwm.util.logging.PwmLogEvent;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DebugItemGenerator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DebugItemGenerator.class );

    private static final List<Class<? extends AppItemGenerator>> APP_ITEM_GENERATORS = List.of(
            ConfigurationFileItemGenerator.class,
            ConfigurationDebugJsonItemGenerator.class,
            ConfigurationDebugTextItemGenerator.class,
            AboutItemGenerator.class,
            SystemEnvironmentItemGenerator.class,
            AppPropertiesItemGenerator.class,
            ServicesDebugItemGenerator.class,
            HealthDebugItemGenerator.class,
            ThreadDumpDebugItemGenerator.class,
            FileInfoDebugItemGenerator.class,
            LogDebugItemGenerator.class,
            LogJsonItemGenerator.class,
            LocalDBDebugGenerator.class,
            SessionDataGenerator.class,
            ClusterInfoDebugGenerator.class,
            CacheServiceDebugItemGenerator.class,
            RootFileSystemDebugItemGenerator.class,
            StatisticsDataDebugItemGenerator.class,
            StatisticsEpsDataDebugItemGenerator.class,
            BuildInformationDebugItemGenerator.class );

    private static final List<Class<? extends DomainItemGenerator>> DOMAIN_ITEM_GENERATORS = List.of(
            LDAPPermissionItemGenerator.class,
            LdapDebugItemGenerator.class,
            LdapRecentUserDebugGenerator.class,
            DashboardDataDebugItemGenerator.class,
            LdapConnectionsDebugItemGenerator.class );

    private final PwmApplication pwmApplication;
    private final AppConfig obfuscatedAppConfig;
    private final SessionLabel sessionLabel;

    private static final Locale LOCALE = PwmConstants.DEFAULT_LOCALE;

    public DebugItemGenerator( final PwmApplication pwmApplication, final SessionLabel sessionLabel )
            throws PwmUnrecoverableException
    {
        this.pwmApplication = pwmApplication;
        this.sessionLabel = sessionLabel;

        final StoredConfiguration obfuscatedStoredConfig = StoredConfigurationUtil.copyConfigAndBlankAllPasswords( pwmApplication.getConfig().getStoredConfiguration() );
        this.obfuscatedAppConfig = new AppConfig( obfuscatedStoredConfig );
    }

    private String getFilenameBase()
    {
        return PwmConstants.PWM_APP_NAME + "-Support";
    }

    public String getFilename()
    {
        return getFilenameBase() + ".zip";
    }

    public void outputZipDebugFile( final ZipOutputStream zipOutput )
            throws IOException
    {
        final String debugFileName = "zipDebugGeneration.csv";
        final Instant startTime = Instant.now();
        final DebugOutputBuilder debugGeneratorLogFile = new DebugOutputBuilder();
        debugGeneratorLogFile.appendLine( "beginning debug output" );

        {
            final AppDebugItemInput debugItemInput = new AppDebugItemInput( pwmApplication, sessionLabel, this.obfuscatedAppConfig );
            final String pathPrefix = getFilenameBase() + "/";
            for ( final Class<? extends AppItemGenerator> serviceClass : APP_ITEM_GENERATORS )
            {
                executeAppDebugItem( debugItemInput, serviceClass, debugGeneratorLogFile, zipOutput, pathPrefix );
            }
        }

        {
            for ( final PwmDomain pwmDomain : pwmApplication.domains().values() )
            {
                final DomainConfig obfuscatedDomainConfig = this.obfuscatedAppConfig.getDomainConfigs().get( pwmDomain.getDomainID() );
                final DomainDebugItemInput debugItemInput = new DomainDebugItemInput( pwmDomain, sessionLabel, obfuscatedDomainConfig );
                final String pathPrefix = getFilenameBase() + "/" + pwmDomain.getDomainID() + "/";
                for ( final Class<? extends DomainItemGenerator> serviceClass : DOMAIN_ITEM_GENERATORS )
                {
                    executeDomainDebugItem( debugItemInput, serviceClass, debugGeneratorLogFile, zipOutput, pathPrefix );
                }
            }
        }

        {
            final String msg = "completed";
            debugGeneratorLogFile.appendLine( msg );
            LOGGER.trace( sessionLabel, () -> msg, () ->  TimeDuration.fromCurrent( startTime ) );
        }

        try
        {
            zipOutput.putNextEntry( new ZipEntry( getFilenameBase() + "/" + debugFileName ) );
            zipOutput.write( debugGeneratorLogFile.toString().getBytes( PwmConstants.DEFAULT_CHARSET ) );
            zipOutput.closeEntry();
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error generating " + debugFileName + ": " + e.getMessage() );
        }

        zipOutput.flush();
    }

    private void executeAppDebugItem(
            final AppDebugItemInput debugItemInput,
            final Class<? extends AppItemGenerator> serviceClass,
            final DebugOutputBuilder debugGeneratorLogFile,
            final ZipOutputStream zipOutput,
            final String pathPrefix
    )
    {
        try
        {
            final Instant itemStartTime = Instant.now();
            LOGGER.trace( sessionLabel, () -> "beginning output of item " + serviceClass.getSimpleName() );
            final AppItemGenerator newAppItemGeneratorItem = serviceClass.getDeclaredConstructor().newInstance();
            zipOutput.putNextEntry( new ZipEntry( pathPrefix + newAppItemGeneratorItem.getFilename() ) );
            newAppItemGeneratorItem.outputItem( debugItemInput, zipOutput );
            zipOutput.closeEntry();
            zipOutput.flush();
            final String finishMsg = "completed output of " + newAppItemGeneratorItem.getFilename()
                    + " in " + TimeDuration.fromCurrent( itemStartTime ).asCompactString();
            LOGGER.trace( sessionLabel, () -> finishMsg );
            debugGeneratorLogFile.appendLine( finishMsg );
        }
        catch ( final Throwable e )
        {
            final String errorMsg = "unexpected error executing debug item output class '" + serviceClass.getName() + "', error: " + e.toString();
            LOGGER.error( sessionLabel, () -> errorMsg, e );
            debugGeneratorLogFile.appendLine( errorMsg );
            final Writer stackTraceOutput = new StringWriter();
            e.printStackTrace( new PrintWriter( stackTraceOutput ) );
            debugGeneratorLogFile.appendLine( stackTraceOutput.toString() );
        }
    }


    void executeDomainDebugItem(
            final DomainDebugItemInput debugItemInput,
            final Class<? extends DomainItemGenerator> serviceClass,
            final DebugOutputBuilder debugGeneratorLogFile,
            final ZipOutputStream zipOutput,
            final String pathPrefix
    )
    {
        try
        {
            final Instant itemStartTime = Instant.now();
            LOGGER.trace( sessionLabel, () -> "beginning output of item " + serviceClass.getSimpleName() );
            final DomainItemGenerator newAppItemGeneratorItem = serviceClass.getDeclaredConstructor().newInstance();
            zipOutput.putNextEntry( new ZipEntry( pathPrefix + newAppItemGeneratorItem.getFilename() ) );
            newAppItemGeneratorItem.outputItem( debugItemInput, zipOutput );
            zipOutput.closeEntry();
            zipOutput.flush();
            final String finishMsg = "completed output of " + newAppItemGeneratorItem.getFilename()
                    + " in " + TimeDuration.fromCurrent( itemStartTime ).asCompactString();
            LOGGER.trace( sessionLabel, () -> finishMsg );
            debugGeneratorLogFile.appendLine( finishMsg );
        }
        catch ( final Throwable e )
        {
            final String errorMsg = "unexpected error executing debug item output class '" + serviceClass.getName() + "', error: " + e.toString();
            LOGGER.error( sessionLabel, () -> errorMsg, e );
            debugGeneratorLogFile.appendLine( errorMsg );
            final Writer stackTraceOutput = new StringWriter();
            e.printStackTrace( new PrintWriter( stackTraceOutput ) );
            debugGeneratorLogFile.appendLine( stackTraceOutput.toString() );
        }

    }

    static class ConfigurationDebugJsonItemGenerator implements AppItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "configuration-debug.json";
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final StoredConfiguration storedConfiguration = debugItemInput.getObfuscatedAppConfig().getStoredConfiguration();
            final TreeMap<String, Object> outputObject = new TreeMap<>();

            CollectionUtil.iteratorToStream( storedConfiguration.keys() )
                    .filter( k ->  k.getRecordType() == StoredConfigKey.RecordType.SETTING )
                    .forEach( k ->
                    {
                        final String key = k.getLabel( PwmConstants.DEFAULT_LOCALE );
                        final StoredValue value = storedConfiguration.readStoredValue( k ).orElseThrow();
                        outputObject.put( key, value );
                    } );


            final String jsonOutput = JsonUtil.serializeMap( outputObject, JsonUtil.Flag.PrettyPrint );
            outputStream.write( jsonOutput.getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
    }

    static class ConfigurationDebugTextItemGenerator implements AppItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "configuration-debug.txt";
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final Locale locale = PwmConstants.DEFAULT_LOCALE;
            final StoredConfiguration storedConfiguration = debugItemInput.getObfuscatedAppConfig().getStoredConfiguration();

            final StringWriter writer = new StringWriter();
            writer.write( "Configuration Debug Output for "
                    + PwmConstants.PWM_APP_NAME + " "
                    + PwmConstants.SERVLET_VERSION + "\n" );
            writer.write( "Timestamp: " + JavaHelper.toIsoDate( storedConfiguration.modifyTime() ) + "\n" );
            writer.write( "This file is " + PwmConstants.DEFAULT_CHARSET.displayName() + " encoded\n" );
            writer.write( "\n" );

            CollectionUtil.iteratorToStream( storedConfiguration.keys() )
                    .filter( k -> k.isRecordType( StoredConfigKey.RecordType.SETTING  ) )
                    .forEach( storedConfigKey ->
                    {
                        final String key = storedConfigKey.toPwmSetting().toMenuLocationDebug( storedConfigKey.getProfileID(), locale );
                        final String value = storedConfiguration.readStoredValue( storedConfigKey ).orElseThrow().toDebugString( locale );
                        writer.write( ">> Setting > " + key );
                        writer.write( "\n" );
                        writer.write( value );
                        writer.write( "\n" );
                        writer.write( "\n" );
                    } );

            outputStream.write( writer.toString().getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
    }

    static class ConfigurationFileItemGenerator implements AppItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return PwmConstants.DEFAULT_CONFIG_FILE_FILENAME;
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final StoredConfiguration storedConfiguration = debugItemInput.getObfuscatedAppConfig().getStoredConfiguration();

            // temporary output stream required because .toXml closes stream.
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final StoredConfigurationFactory.OutputSettings outputSettings = StoredConfigurationFactory.OutputSettings.builder()
                    .mode( StoredConfigurationFactory.OutputSettings.SecureOutputMode.STRIPPED )
                    .build();
            StoredConfigurationFactory.output( storedConfiguration, byteArrayOutputStream, outputSettings );
            outputStream.write( byteArrayOutputStream.toByteArray() );        }
    }

    static class AboutItemGenerator implements AppItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "about.properties";
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final Properties outputProps = JavaHelper.newSortedProperties();
            final Map<PwmAboutProperty, String> infoBean = PwmAboutProperty.makeInfoBean( debugItemInput.getPwmApplication() );
            outputProps.putAll( PwmAboutProperty.toStringMap( infoBean ) );
            outputProps.store( outputStream, JavaHelper.toIsoDate( Instant.now() ) );
        }
    }

    static class SystemEnvironmentItemGenerator implements AppItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "system-environment.properties";
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final Properties outputProps = JavaHelper.newSortedProperties();
            outputProps.putAll( System.getenv() );
            outputProps.store( outputStream, JavaHelper.toIsoDate( Instant.now() ) );
        }
    }

    static class AppPropertiesItemGenerator implements AppItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "appProperties.properties";
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {

            final AppConfig config = debugItemInput.getObfuscatedAppConfig();
            final Properties outputProps = JavaHelper.newSortedProperties();

            for ( final AppProperty appProperty : AppProperty.values() )
            {
                outputProps.put( appProperty.getKey(), config.readAppProperty( appProperty ) );
            }

            outputStream.write( JsonUtil.serializeMap( outputProps ).getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
    }

    static class ServicesDebugItemGenerator implements AppItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "services.json";
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final PwmApplication pwmApplication = debugItemInput.getPwmApplication();
            final LinkedHashMap<String, Object> outputMap = new LinkedHashMap<>();

            {
                // services info
                final LinkedHashMap<String, Object> servicesMap = new LinkedHashMap<>();
                for ( final PwmService service : pwmApplication.getPwmServices() )
                {
                    final LinkedHashMap<String, Object> serviceOutput = new LinkedHashMap<>();
                    serviceOutput.put( "name", service.getClass().getSimpleName() );
                    serviceOutput.put( "status", service.status() );
                    serviceOutput.put( "health", service.healthCheck() );
                    serviceOutput.put( "serviceInfo", service.serviceInfo() );
                    servicesMap.put( service.getClass().getSimpleName(), serviceOutput );
                }
                outputMap.put( "services", servicesMap );
            }

            final String recordJson = JsonUtil.serializeMap( outputMap, JsonUtil.Flag.PrettyPrint );
            outputStream.write( recordJson.getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
    }


    static class HealthDebugItemGenerator implements AppItemGenerator
    {
        @Value
        private static class HealthDebugInfo
        {
            private final HealthRecord healthRecord;
            private final String message;
        }

        @Override
        public String getFilename( )
        {
            return "health.json";
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final Locale locale = PwmConstants.DEFAULT_LOCALE;
            final PwmApplication pwmApplication = debugItemInput.getPwmApplication();
            final Set<HealthRecord> records = pwmApplication.getHealthMonitor().getHealthRecords();

            final List<HealthDebugInfo> outputInfos = new ArrayList<>();
            records.forEach( healthRecord -> outputInfos.add( new HealthDebugInfo( healthRecord, healthRecord.getDetail( locale,
                    debugItemInput.getObfuscatedAppConfig() ) ) ) );
            final String recordJson = JsonUtil.serializeCollection( outputInfos, JsonUtil.Flag.PrettyPrint );
            outputStream.write( recordJson.getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
    }

    static class ThreadDumpDebugItemGenerator implements AppItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "threads.txt";
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final PrintWriter writer = new PrintWriter( new OutputStreamWriter( byteArrayOutputStream, PwmConstants.DEFAULT_CHARSET ) );
            final ThreadInfo[] threads = ManagementFactory.getThreadMXBean().dumpAllThreads( true, true );
            for ( final ThreadInfo threadInfo : threads )
            {
                writer.write( JavaHelper.threadInfoToString( threadInfo ) );
            }
            writer.flush();
            outputStream.write( byteArrayOutputStream.toByteArray() );
        }
    }

    static class LdapDebugItemGenerator implements DomainItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "ldap-servers.json";
        }

        @Override
        public void outputItem( final DomainDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final List<LdapDebugDataGenerator.LdapDebugInfo> ldapDebugInfos = LdapDebugDataGenerator.makeLdapDebugInfos(
                    debugItemInput.getPwmDomain(),
                    debugItemInput.getSessionLabel(),
                    debugItemInput.getObfuscatedDomainConfig(),
                    LOCALE
            );
            final Writer writer = new OutputStreamWriter( outputStream, PwmConstants.DEFAULT_CHARSET );
            writer.write( JsonUtil.serializeCollection( ldapDebugInfos, JsonUtil.Flag.PrettyPrint ) );
            writer.flush();
        }
    }


    static class FileInfoDebugItemGenerator implements AppItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "fileinformation.csv";
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final PwmApplication pwmApplication = debugItemInput.getPwmApplication();
            final File applicationPath = pwmApplication.getPwmEnvironment().getApplicationPath();
            final List<File> interestedFiles = new ArrayList<>(  );

            if ( pwmApplication.getPwmEnvironment().getContextManager() != null )
            {
                try
                {
                    final Optional<File> webInfPath = pwmApplication.getPwmEnvironment().getContextManager().locateWebInfFilePath();
                    if ( webInfPath.isPresent() && webInfPath.get().exists() )
                    {
                        final File servletRootPath = webInfPath.get().getParentFile();

                        if ( servletRootPath != null )
                        {
                            interestedFiles.add( webInfPath.orElseThrow() );
                        }
                    }
                }
                catch ( final Exception e )
                {
                    LOGGER.error( debugItemInput.getSessionLabel(), () -> "unable to generate webInfPath fileMd5sums during zip debug building: " + e.getMessage() );
                }
            }

            if ( applicationPath != null )
            {
                try
                {
                    interestedFiles.add( applicationPath );
                }
                catch ( final Exception e )
                {
                    LOGGER.error( debugItemInput.getSessionLabel(), () -> "unable to generate appPath fileMd5sums during zip debug building: " + e.getMessage() );
                }
            }

            final CSVPrinter csvPrinter = JavaHelper.makeCsvPrinter( outputStream );
            {
                final List<String> headerRow = new ArrayList<>();
                headerRow.add( "Filepath" );
                headerRow.add( "Filename" );
                headerRow.add( "Last Modified" );
                headerRow.add( "Size" );
                headerRow.add( "Checksum" );
                csvPrinter.printComment( StringUtil.join( headerRow, "," ) );
            }

            final Iterator<FileSystemUtility.FileSummaryInformation> iter = FileSystemUtility.readFileInformation( interestedFiles );
            while ( iter.hasNext() )
            {
                final FileSystemUtility.FileSummaryInformation fileSummaryInformation = iter.next();
                try
                {
                    final List<String> dataRow = new ArrayList<>();
                    dataRow.add( fileSummaryInformation.getFilepath() );
                    dataRow.add( fileSummaryInformation.getFilename() );
                    dataRow.add( JavaHelper.toIsoDate( fileSummaryInformation.getModified() ) );
                    dataRow.add( String.valueOf( fileSummaryInformation.getSize() ) );
                    dataRow.add( Long.toString( fileSummaryInformation.getChecksum() ) );
                    csvPrinter.printRecord( dataRow );
                }
                catch ( final Exception e )
                {
                    LOGGER.trace( () -> "error generating file summary info: " + e.getMessage() );
                }
            }
            csvPrinter.flush();
        }
    }

    static class LogDebugItemGenerator implements AppItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "debug.log";
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final Instant startTime = Instant.now();
            final Function<PwmLogEvent, String> logEventFormatter = PwmLogEvent::toLogString;

            outputLogs( debugItemInput.getPwmApplication(), outputStream, logEventFormatter );
            LOGGER.trace( () ->  "debug log output completed in ", () -> TimeDuration.fromCurrent( startTime ) );
        }
    }

    static class LogJsonItemGenerator implements AppItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "debug.json";
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final Instant startTime = Instant.now();
            final Function<PwmLogEvent, String> logEventFormatter = JsonUtil::serialize;

            outputLogs( debugItemInput.getPwmApplication(), outputStream, logEventFormatter );
            LOGGER.trace( () ->  "debug json output completed in ", () -> TimeDuration.fromCurrent( startTime ) );
        }
    }

    private static void outputLogs(
            final PwmApplication pwmApplication,
            final OutputStream outputStream,
            final Function<PwmLogEvent, String> logEventFormatter
    )
            throws Exception
    {
        final long maxByteCount = JavaHelper.silentParseLong( pwmApplication.getConfig().readAppProperty( AppProperty.CONFIG_MANAGER_ZIPDEBUG_MAXLOGBYTES ), 10_000_000 );
        final int maxSeconds = JavaHelper.silentParseInt( pwmApplication.getConfig().readAppProperty( AppProperty.CONFIG_MANAGER_ZIPDEBUG_MAXLOGSECONDS ), 60 );
        final LocalDBSearchQuery searchParameters = LocalDBSearchQuery.builder()
                .minimumLevel( PwmLogLevel.TRACE )
                .maxEvents( Integer.MAX_VALUE )
                .maxQueryTime( TimeDuration.of( maxSeconds, TimeDuration.Unit.SECONDS ) )
                .build();

        final LocalDBSearchResults searchResults = pwmApplication.getLocalDBLogger().readStoredEvents( searchParameters );
        final CountingOutputStream countingOutputStream = new CountingOutputStream( outputStream );

        final Writer writer = new OutputStreamWriter( countingOutputStream, PwmConstants.DEFAULT_CHARSET );
        {
            while ( searchResults.hasNext() && countingOutputStream.getByteCount() < maxByteCount )
            {
                final PwmLogEvent event = searchResults.next();
                final String output = logEventFormatter.apply( event );
                writer.write( output );
                writer.write( "\n" );
            }

        }

        // do not close writer because underlying stream should not be closed.
        writer.flush();
    }

    static class LDAPPermissionItemGenerator implements DomainItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "ldapPermissionSuggestions.csv";
        }

        @Override
        public void outputItem( final DomainDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {

            final DomainConfig domainConfig = debugItemInput.getObfuscatedDomainConfig();
            final LDAPPermissionCalculator ldapPermissionCalculator = new LDAPPermissionCalculator( domainConfig );

            final CSVPrinter csvPrinter = JavaHelper.makeCsvPrinter( outputStream );
            {
                final List<String> headerRow = new ArrayList<>();
                headerRow.add( "Attribute" );
                headerRow.add( "Actor" );
                headerRow.add( "Access" );
                headerRow.add( "Setting" );
                headerRow.add( "Profile" );
                csvPrinter.printComment( StringUtil.join( headerRow, "," ) );
            }

            for ( final LDAPPermissionCalculator.PermissionRecord record : ldapPermissionCalculator.getPermissionRecords() )
            {
                final List<String> dataRow = new ArrayList<>();
                dataRow.add( record.getAttribute() );
                dataRow.add( record.getActor() == null ? "" : record.getActor().toString() );
                dataRow.add( record.getAccess() == null ? "" : record.getAccess().toString() );
                dataRow.add( record.getPwmSetting() == null ? "" : record.getPwmSetting().getKey() );
                dataRow.add( record.getProfile() == null ? "" : record.getProfile() );
                csvPrinter.printRecord( dataRow );
            }
            csvPrinter.flush();
        }
    }

    static class LocalDBDebugGenerator implements AppItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "localDBDebug.json";
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final PwmApplication pwmApplication = debugItemInput.getPwmApplication();
            final LocalDB localDB = pwmApplication.getLocalDB();
            final Map<String, Serializable> serializableMap = localDB.debugInfo();
            outputStream.write( JsonUtil.serializeMap( serializableMap, JsonUtil.Flag.PrettyPrint ).getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
    }

    static class SessionDataGenerator implements AppItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "sessions.csv";
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final PwmApplication pwmDomain = debugItemInput.getPwmApplication();
            pwmDomain.getSessionTrackService().outputToCsv( LOCALE, pwmDomain.getConfig(), outputStream );
        }
    }

    static class LdapRecentUserDebugGenerator implements DomainItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "recentUserDebugData.json";
        }

        @Override
        public void outputItem( final DomainDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final PwmDomain pwmDomain = debugItemInput.getPwmDomain();
            final List<UserIdentity> recentUsers = pwmDomain.getPwmApplication().getSessionTrackService().getRecentLogins();
            final List<UserDebugDataBean> recentDebugBeans = new ArrayList<>();

            for ( final UserIdentity userIdentity : recentUsers )
            {
                if ( Objects.equals( userIdentity.getDomainID(), pwmDomain.getDomainID() ) )
                {
                    final UserDebugDataBean dataBean = UserDebugDataReader.readUserDebugData(
                            pwmDomain,
                            LOCALE,
                            debugItemInput.getSessionLabel(),
                            userIdentity
                    );
                    recentDebugBeans.add( dataBean );
                }
            }

            outputStream.write( JsonUtil.serializeCollection( recentDebugBeans, JsonUtil.Flag.PrettyPrint ).getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
    }

    static class ClusterInfoDebugGenerator implements AppItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "node-info.json";
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final PwmApplication pwmApplication = debugItemInput.getPwmApplication();
            final NodeService nodeService = pwmApplication.getNodeService();

            final Map<String, Serializable> debugOutput = new LinkedHashMap<>();
            debugOutput.put( "status", nodeService.status() );

            if ( nodeService.status() == PwmService.STATUS.OPEN )
            {
                debugOutput.put( "isMaster", nodeService.isMaster() );
                debugOutput.put( "nodes", new ArrayList<>( nodeService.nodes() ) );
            }

            outputStream.write( JsonUtil.serializeMap( debugOutput, JsonUtil.Flag.PrettyPrint ).getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
    }

    static class CacheServiceDebugItemGenerator implements AppItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "cache-service-info.json";
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final PwmApplication pwmApplication = debugItemInput.getPwmApplication();
            final CacheService cacheService = pwmApplication.getCacheService();

            final Map<String, Serializable> debugOutput = new LinkedHashMap<>( cacheService.debugInfo() );
            outputStream.write( JsonUtil.serializeMap( debugOutput, JsonUtil.Flag.PrettyPrint ).getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
    }

    static class DashboardDataDebugItemGenerator implements DomainItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "dashboard-data.json";
        }

        @Override
        public void outputItem( final DomainDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final PwmDomain pwmDomain = debugItemInput.getPwmDomain();
            final ContextManager contextManager = pwmDomain.getPwmApplication().getPwmEnvironment().getContextManager();
            final AppDashboardData appDashboardData = AppDashboardData.makeDashboardData(
                    pwmDomain,
                    contextManager,
                    LOCALE
            );

            outputStream.write( JsonUtil.serialize( appDashboardData, JsonUtil.Flag.PrettyPrint ).getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
    }

    static class StatisticsDataDebugItemGenerator implements AppItemGenerator
    {
        @Override
        public String getFilename()
        {
            return "statistics.csv";
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final PwmApplication pwmApplication = debugItemInput.getPwmApplication();
            final StatisticsManager statsManager = pwmApplication.getStatisticsManager();
            statsManager.outputStatsToCsv( outputStream, LOCALE, true );
        }
    }

    static class LdapConnectionsDebugItemGenerator implements DomainItemGenerator
    {
        @Override
        public String getFilename()
        {
            return "ldap-connections.json";
        }

        @Override
        public void outputItem( final DomainDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final PwmDomain pwmDomain = debugItemInput.getPwmDomain();
            final List<LdapConnectionService.ConnectionInfo> connectionInfos = pwmDomain.getLdapConnectionService().getConnectionInfos();
            final Writer writer = new OutputStreamWriter( outputStream, PwmConstants.DEFAULT_CHARSET );
            writer.write( JsonUtil.serializeCollection( connectionInfos, JsonUtil.Flag.PrettyPrint ) );
            writer.flush();
        }
    }

    static class StatisticsEpsDataDebugItemGenerator implements AppItemGenerator
    {
        @Override
        public String getFilename()
        {
            return "statistics-eps.csv";
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final PwmApplication pwmDomain = debugItemInput.getPwmApplication();
            final StatisticsManager statsManager = pwmDomain.getStatisticsManager();
            final CSVPrinter csvPrinter = JavaHelper.makeCsvPrinter( outputStream );
            {
                final List<String> headerRow = new ArrayList<>();
                headerRow.add( "Counter" );
                headerRow.add( "Duration" );
                headerRow.add( "Events/Second" );
                csvPrinter.printComment( StringUtil.join( headerRow, "," ) );
            }
            for ( final EpsStatistic epsStatistic : EpsStatistic.values() )
            {
                for ( final Statistic.EpsDuration epsDuration : Statistic.EpsDuration.values() )
                {
                    try
                    {
                        final List<String> dataRow = new ArrayList<>();
                        final BigDecimal value = statsManager.readEps( epsStatistic, epsDuration );
                        final String sValue = value.toPlainString();
                        dataRow.add( epsStatistic.getLabel( LOCALE ) );
                        dataRow.add( epsDuration.getTimeDuration().asCompactString() );
                        dataRow.add( sValue );
                        csvPrinter.printRecord( dataRow );
                    }
                    catch ( final Exception e )
                    {
                        LOGGER.trace( () -> "error generating csv-stats summary info: " + e.getMessage() );
                    }
                }
            }
            csvPrinter.flush();
        }
    }

    static class BuildInformationDebugItemGenerator implements AppItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "build.properties";
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final Properties outputProps = JavaHelper.newSortedProperties();
            outputProps.putAll( PwmConstants.BUILD_MANIFEST );
            outputProps.store( outputStream, JavaHelper.toIsoDate( Instant.now() ) );
        }
    }

    static class RootFileSystemDebugItemGenerator implements AppItemGenerator
    {
        @Override
        public String getFilename( )
        {
            return "filesystem-data.json";
        }

        @Override
        public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
        {
            final Collection<RootFileSystemInfo> rootInfos = RootFileSystemInfo.forAllRootFileSystems();
            outputStream.write( JsonUtil.serializeCollection( rootInfos, JsonUtil.Flag.PrettyPrint ).getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }

        @Value
        @Builder
        private static class RootFileSystemInfo implements Serializable
        {
            private String rootPath;
            private long totalSpace;
            private long freeSpace;
            private long usableSpace;

            static Collection<RootFileSystemInfo> forAllRootFileSystems()
            {
                return Arrays.stream( File.listRoots() )
                        .map( RootFileSystemInfo::forRoot )
                        .collect( Collectors.toList() );
            }

            static RootFileSystemInfo forRoot( final File fileRoot )
            {
                return RootFileSystemInfo.builder()
                        .rootPath( fileRoot.getAbsolutePath() )
                        .totalSpace( fileRoot.getTotalSpace() )
                        .freeSpace( fileRoot.getFreeSpace() )
                        .usableSpace( fileRoot.getUsableSpace() )
                        .build();
            }
        }
    }

    interface AppItemGenerator
    {

        String getFilename( );

        void outputItem(
                AppDebugItemInput debugItemInput,
                OutputStream outputStream
        ) throws Exception;
    }

    @Value
    private static class AppDebugItemInput
    {
        private final PwmApplication pwmApplication;
        private final SessionLabel sessionLabel;
        private final AppConfig obfuscatedAppConfig;
    }

    interface DomainItemGenerator
    {

        String getFilename( );

        void outputItem(
                DomainDebugItemInput debugItemInput,
                OutputStream outputStream
        ) throws Exception;
    }

    @Value
    private static class DomainDebugItemInput
    {
        private final PwmDomain pwmDomain;
        private final SessionLabel sessionLabel;
        private final DomainConfig obfuscatedDomainConfig;
    }

}
