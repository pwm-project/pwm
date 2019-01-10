/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.http.servlet.configmanager;

import org.apache.commons.csv.CSVPrinter;
import password.pwm.AppProperty;
import password.pwm.PwmAboutProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMonitor;
import password.pwm.health.HealthRecord;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmRequest;
import password.pwm.http.servlet.admin.AppDashboardData;
import password.pwm.http.servlet.admin.UserDebugDataBean;
import password.pwm.http.servlet.admin.UserDebugDataReader;
import password.pwm.ldap.LdapDebugDataGenerator;
import password.pwm.svc.PwmService;
import password.pwm.svc.cache.CacheService;
import password.pwm.svc.node.NodeService;
import password.pwm.util.LDAPPermissionCalculator;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DebugItemGenerator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DebugItemGenerator.class );

    private static final List<Class<? extends Generator>> DEBUG_ZIP_ITEM_GENERATORS = Collections.unmodifiableList( Arrays.asList(
            ConfigurationFileItemGenerator.class,
            ConfigurationDebugJsonItemGenerator.class,
            ConfigurationDebugTextItemGenerator.class,
            AboutItemGenerator.class,
            DashboardDataDebugItemGenerator.class,
            SystemEnvironmentItemGenerator.class,
            AppPropertiesItemGenerator.class,
            ServicesDebugItemGenerator.class,
            HealthDebugItemGenerator.class,
            ThreadDumpDebugItemGenerator.class,
            FileInfoDebugItemGenerator.class,
            LogDebugItemGenerator.class,
            LdapDebugItemGenerator.class,
            LDAPPermissionItemGenerator.class,
            LocalDBDebugGenerator.class,
            SessionDataGenerator.class,
            LdapRecentUserDebugGenerator.class,
            ClusterInfoDebugGenerator.class,
            CacheServiceDebugItemGenerator.class
    ) );

    static void outputZipDebugFile(
            final PwmRequest pwmRequest,
            final ZipOutputStream zipOutput,
            final String pathPrefix
    )
            throws IOException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final String debugFileName = "zipDebugGeneration.csv";

        final ByteArrayOutputStream debugGeneratorLogBaos = new ByteArrayOutputStream();
        final CSVPrinter debugGeneratorLogFile = JavaHelper.makeCsvPrinter( debugGeneratorLogBaos );

        for ( final Class<? extends DebugItemGenerator.Generator> serviceClass : DEBUG_ZIP_ITEM_GENERATORS )
        {
            try
            {
                final Instant startTime = Instant.now();
                LOGGER.trace( pwmRequest, () -> "beginning output of item " + serviceClass.getSimpleName() );
                final DebugItemGenerator.Generator newGeneratorItem = serviceClass.getDeclaredConstructor().newInstance();
                zipOutput.putNextEntry( new ZipEntry( pathPrefix + newGeneratorItem.getFilename() ) );
                newGeneratorItem.outputItem( pwmApplication, pwmRequest, zipOutput );
                zipOutput.closeEntry();
                zipOutput.flush();
                final String finishMsg = "completed output of " + newGeneratorItem.getFilename()
                        + " in " + TimeDuration.fromCurrent( startTime ).asCompactString();
                LOGGER.trace( pwmRequest, () -> finishMsg );
                debugGeneratorLogFile.printRecord( JavaHelper.toIsoDate( Instant.now() ), finishMsg );
            }
            catch ( Throwable e )
            {
                final String errorMsg = "unexpected error executing debug item output class '" + serviceClass.getName() + "', error: " + e.toString();
                LOGGER.error( pwmRequest, errorMsg );
                debugGeneratorLogFile.printRecord( JavaHelper.toIsoDate( Instant.now() ), errorMsg );
                final Writer stackTraceOutput = new StringWriter();
                e.printStackTrace( new PrintWriter( stackTraceOutput ) );
                debugGeneratorLogFile.printRecord( stackTraceOutput );
            }
        }

        try
        {
            zipOutput.putNextEntry( new ZipEntry( pathPrefix + debugFileName ) );
            debugGeneratorLogFile.flush();
            zipOutput.write( debugGeneratorLogBaos.toByteArray() );
            zipOutput.closeEntry();
        }
        catch ( Exception e )
        {
            LOGGER.error( "error generating " + debugFileName + ": " + e.getMessage() );
        }

        zipOutput.flush();
    }

    static class ConfigurationDebugJsonItemGenerator implements Generator
    {
        @Override
        public String getFilename( )
        {
            return "configuration-debug.json";
        }

        @Override
        public void outputItem( final PwmApplication pwmApplication, final PwmRequest pwmRequest, final OutputStream outputStream ) throws Exception
        {
            final StoredConfigurationImpl storedConfiguration = ConfigManagerServlet.readCurrentConfiguration( pwmRequest );
            storedConfiguration.resetAllPasswordValues( "value removed from " + PwmConstants.PWM_APP_NAME + "-Support configuration export" );
            final String jsonOutput = JsonUtil.serialize( storedConfiguration.toJsonDebugObject(), JsonUtil.Flag.PrettyPrint );
            outputStream.write( jsonOutput.getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
    }

    static class ConfigurationDebugTextItemGenerator implements Generator
    {
        @Override
        public String getFilename( )
        {
            return "configuration-debug.txt";
        }

        @Override
        public void outputItem( final PwmApplication pwmApplication, final PwmRequest pwmRequest, final OutputStream outputStream ) throws Exception
        {
            final StoredConfigurationImpl storedConfiguration = ConfigManagerServlet.readCurrentConfiguration( pwmRequest );
            storedConfiguration.resetAllPasswordValues( "value removed from " + PwmConstants.PWM_APP_NAME + "-Support configuration export" );

            final StringWriter writer = new StringWriter();
            writer.write( "Configuration Debug Output for "
                    + PwmConstants.PWM_APP_NAME + " "
                    + PwmConstants.SERVLET_VERSION + "\n" );
            writer.write( "Timestamp: " + JavaHelper.toIsoDate( storedConfiguration.modifyTime() ) + "\n" );
            writer.write( "This file is " + PwmConstants.DEFAULT_CHARSET.displayName() + " encoded\n" );

            writer.write( "\n" );
            final Map<String, String> modifiedSettings = new TreeMap<>(
                    storedConfiguration.getModifiedSettingDebugValues( PwmConstants.DEFAULT_LOCALE, true )
            );

            for ( final Map.Entry<String, String> entry : modifiedSettings.entrySet() )
            {
                final String key = entry.getKey();
                final String value = entry.getValue();
                writer.write( ">> Setting > " + key );
                writer.write( "\n" );
                writer.write( value );
                writer.write( "\n" );
                writer.write( "\n" );
            }

            outputStream.write( writer.toString().getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
    }

    static class ConfigurationFileItemGenerator implements Generator
    {
        @Override
        public String getFilename( )
        {
            return PwmConstants.DEFAULT_CONFIG_FILE_FILENAME;
        }

        @Override
        public void outputItem( final PwmApplication pwmApplication, final PwmRequest pwmRequest, final OutputStream outputStream ) throws Exception
        {
            final StoredConfigurationImpl storedConfiguration = ConfigManagerServlet.readCurrentConfiguration( pwmRequest );
            storedConfiguration.resetAllPasswordValues( "value removed from " + PwmConstants.PWM_APP_NAME + "-Support configuration export" );

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            storedConfiguration.toXml( baos );
            outputStream.write( baos.toByteArray() );
        }
    }

    static class AboutItemGenerator implements Generator
    {
        @Override
        public String getFilename( )
        {
            return "about.properties";
        }

        @Override
        public void outputItem( final PwmApplication pwmApplication, final PwmRequest pwmRequest, final OutputStream outputStream ) throws Exception
        {
            final Properties outputProps = new Properties()
            {
                public synchronized Enumeration<Object> keys( )
                {
                    return Collections.enumeration( new TreeSet<>( super.keySet() ) );
                }
            };

            final Map<PwmAboutProperty, String> infoBean = PwmAboutProperty.makeInfoBean( pwmApplication );
            outputProps.putAll( PwmAboutProperty.toStringMap( infoBean ) );
            try ( ByteArrayOutputStream baos = new ByteArrayOutputStream() )
            {
                outputProps.store( baos, JavaHelper.toIsoDate( Instant.now() ) );
                outputStream.write( baos.toByteArray() );
            }
        }
    }

    static class SystemEnvironmentItemGenerator implements Generator
    {
        @Override
        public String getFilename( )
        {
            return "system-environment.properties";
        }

        @Override
        public void outputItem( final PwmApplication pwmApplication, final PwmRequest pwmRequest, final OutputStream outputStream ) throws Exception
        {
            final Properties outputProps = JavaHelper.newSortedProperties();
            outputProps.putAll( System.getenv() );
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            outputProps.store( baos, JavaHelper.toIsoDate( Instant.now() ) );
            outputStream.write( baos.toByteArray() );
        }
    }

    static class AppPropertiesItemGenerator implements Generator
    {
        @Override
        public String getFilename( )
        {
            return "appProperties.properties";
        }

        @Override
        public void outputItem( final PwmApplication pwmApplication, final PwmRequest pwmRequest, final OutputStream outputStream ) throws Exception
        {

            final Configuration config = pwmRequest.getConfig();
            final Properties outputProps = JavaHelper.newSortedProperties();

            for ( final AppProperty appProperty : AppProperty.values() )
            {
                outputProps.setProperty( appProperty.getKey(), config.readAppProperty( appProperty ) );
            }

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            outputProps.store( baos, JavaHelper.toIsoDate( Instant.now() ) );
            outputStream.write( baos.toByteArray() );
        }
    }

    static class ServicesDebugItemGenerator implements Generator
    {
        @Override
        public String getFilename( )
        {
            return "services.json";
        }

        @Override
        public void outputItem( final PwmApplication pwmApplication, final PwmRequest pwmRequest, final OutputStream outputStream )
                throws Exception
        {
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


    static class HealthDebugItemGenerator implements Generator
    {
        @Override
        public String getFilename( )
        {
            return "health.json";
        }

        @Override
        public void outputItem( final PwmApplication pwmApplication, final PwmRequest pwmRequest, final OutputStream outputStream ) throws Exception
        {
            final Set<HealthRecord> records = pwmApplication.getHealthMonitor().getHealthRecords( HealthMonitor.CheckTimeliness.CurrentButNotAncient );
            final String recordJson = JsonUtil.serializeCollection( records, JsonUtil.Flag.PrettyPrint );
            outputStream.write( recordJson.getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
    }

    static class ThreadDumpDebugItemGenerator implements Generator
    {
        @Override
        public String getFilename( )
        {
            return "threads.txt";
        }

        @Override
        public void outputItem( final PwmApplication pwmApplication, final PwmRequest pwmRequest, final OutputStream outputStream ) throws Exception
        {

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final PrintWriter writer = new PrintWriter( new OutputStreamWriter( baos, PwmConstants.DEFAULT_CHARSET ) );
            final ThreadInfo[] threads = ManagementFactory.getThreadMXBean().dumpAllThreads( true, true );
            for ( final ThreadInfo threadInfo : threads )
            {
                writer.write( JavaHelper.threadInfoToString( threadInfo ) );
            }
            writer.flush();
            outputStream.write( baos.toByteArray() );
        }
    }

    static class LdapDebugItemGenerator implements Generator
    {
        @Override
        public String getFilename( )
        {
            return "ldap-servers.json";
        }

        @Override
        public void outputItem( final PwmApplication pwmApplication, final PwmRequest pwmRequest, final OutputStream outputStream ) throws Exception
        {
            final List<LdapDebugDataGenerator.LdapDebugInfo> ldapDebugInfos = LdapDebugDataGenerator.makeLdapDebugInfos(
                    pwmApplication,
                    pwmRequest.getSessionLabel(),
                    pwmApplication.getConfig(),
                    pwmRequest.getLocale()
            );
            final Writer writer = new OutputStreamWriter( outputStream, PwmConstants.DEFAULT_CHARSET );
            writer.write( JsonUtil.serializeCollection( ldapDebugInfos, JsonUtil.Flag.PrettyPrint ) );
            writer.flush();
        }
    }


    static class FileInfoDebugItemGenerator implements Generator
    {
        @Override
        public String getFilename( )
        {
            return "fileinformation.csv";
        }

        @Override
        public void outputItem( final PwmApplication pwmApplication, final PwmRequest pwmRequest, final OutputStream outputStream ) throws Exception
        {
            final List<FileSystemUtility.FileSummaryInformation> fileSummaryInformations = new ArrayList<>();
            final File applicationPath = pwmApplication.getPwmEnvironment().getApplicationPath();

            if ( pwmApplication.getPwmEnvironment().getContextManager() != null )
            {
                try
                {
                    final File webInfPath = pwmApplication.getPwmEnvironment().getContextManager().locateWebInfFilePath();
                    if ( webInfPath != null && webInfPath.exists() )
                    {
                        final File servletRootPath = webInfPath.getParentFile();

                        if ( servletRootPath != null )
                        {
                            fileSummaryInformations.addAll( FileSystemUtility.readFileInformation( webInfPath ) );
                        }
                    }
                }
                catch ( Exception e )
                {
                    LOGGER.error( pwmRequest, "unable to generate webInfPath fileMd5sums during zip debug building: " + e.getMessage() );
                }
            }

            if ( applicationPath != null )
            {
                try
                {
                    fileSummaryInformations.addAll( FileSystemUtility.readFileInformation( applicationPath ) );
                }
                catch ( Exception e )
                {
                    LOGGER.error( pwmRequest, "unable to generate appPath fileMd5sums during zip debug building: " + e.getMessage() );
                }
            }

            {
                final CSVPrinter csvPrinter = JavaHelper.makeCsvPrinter( outputStream );
                {
                    final List<String> headerRow = new ArrayList<>();
                    headerRow.add( "Filepath" );
                    headerRow.add( "Filename" );
                    headerRow.add( "Last Modified" );
                    headerRow.add( "Size" );
                    headerRow.add( "sha1sum" );
                    csvPrinter.printComment( StringUtil.join( headerRow, "," ) );
                }
                for ( final FileSystemUtility.FileSummaryInformation fileSummaryInformation : fileSummaryInformations )
                {
                    try
                    {
                        final List<String> dataRow = new ArrayList<>();
                        dataRow.add( fileSummaryInformation.getFilepath() );
                        dataRow.add( fileSummaryInformation.getFilename() );
                        dataRow.add( JavaHelper.toIsoDate( fileSummaryInformation.getModified() ) );
                        dataRow.add( String.valueOf( fileSummaryInformation.getSize() ) );
                        dataRow.add( fileSummaryInformation.getSha1sum() );
                        csvPrinter.printRecord( dataRow );
                    }
                    catch ( Exception e )
                    {
                        LOGGER.trace( () -> "error generating file summary info: " + e.getMessage() );
                    }
                }
                csvPrinter.flush();
            }
        }
    }

    static class LogDebugItemGenerator implements Generator
    {
        @Override
        public String getFilename( )
        {
            return "debug.log";
        }

        @Override
        public void outputItem(
                final PwmApplication pwmApplication,
                final PwmRequest pwmRequest,
                final OutputStream outputStream
        ) throws Exception
        {

            final int maxCount = Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.CONFIG_MANAGER_ZIPDEBUG_MAXLOGLINES ) );
            final int maxSeconds = Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.CONFIG_MANAGER_ZIPDEBUG_MAXLOGSECONDS ) );
            final LocalDBSearchQuery searchParameters = LocalDBSearchQuery.builder()
                    .minimumLevel( PwmLogLevel.TRACE )
                    .maxEvents( maxCount )
                    .maxQueryTime( TimeDuration.of( maxSeconds, TimeDuration.Unit.SECONDS ) )
                    .build();

            final LocalDBSearchResults searchResults = pwmApplication.getLocalDBLogger().readStoredEvents(
                    searchParameters );
            int counter = 0;
            while ( searchResults.hasNext() )
            {
                final PwmLogEvent event = searchResults.next();
                outputStream.write( event.toLogString().getBytes( PwmConstants.DEFAULT_CHARSET ) );
                outputStream.write( "\n".getBytes( PwmConstants.DEFAULT_CHARSET ) );
                counter++;
                if ( counter % 1000 == 0 )
                {
                    outputStream.flush();
                }
            }

            {
                final int finalCounter = counter;
                LOGGER.trace( () -> "output " + finalCounter + " lines to " + this.getFilename() );
            }
        }
    }

    static class LDAPPermissionItemGenerator implements Generator
    {
        @Override
        public String getFilename( )
        {
            return "ldapPermissionSuggestions.csv";
        }

        @Override
        public void outputItem(
                final PwmApplication pwmApplication,
                final PwmRequest pwmRequest,
                final OutputStream outputStream
        ) throws Exception
        {

            final StoredConfigurationImpl storedConfiguration = ConfigManagerServlet.readCurrentConfiguration( pwmRequest );
            final LDAPPermissionCalculator ldapPermissionCalculator = new LDAPPermissionCalculator( storedConfiguration );

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

    static class LocalDBDebugGenerator implements Generator
    {
        @Override
        public String getFilename( )
        {
            return "localDBDebug.json";
        }

        @Override
        public void outputItem(
                final PwmApplication pwmApplication,
                final PwmRequest pwmRequest,
                final OutputStream outputStream
        ) throws Exception
        {
            final LocalDB localDB = pwmApplication.getLocalDB();
            final Map<String, Serializable> serializableMap = localDB.debugInfo();
            outputStream.write( JsonUtil.serializeMap( serializableMap, JsonUtil.Flag.PrettyPrint ).getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
    }

    static class SessionDataGenerator implements Generator
    {
        @Override
        public String getFilename( )
        {
            return "sessions.csv";
        }

        @Override
        public void outputItem(
                final PwmApplication pwmApplication,
                final PwmRequest pwmRequest,
                final OutputStream outputStream
        )
                throws Exception
        {
            pwmApplication.getSessionTrackService().outputToCsv( pwmRequest.getLocale(), pwmRequest.getConfig(), outputStream );
        }
    }

    static class LdapRecentUserDebugGenerator implements Generator
    {
        @Override
        public String getFilename( )
        {
            return "recentUserDebugData.json";
        }

        @Override
        public void outputItem(
                final PwmApplication pwmApplication,
                final PwmRequest pwmRequest,
                final OutputStream outputStream
        )
                throws Exception
        {
            final List<UserIdentity> recentUsers = pwmApplication.getSessionTrackService().getRecentLogins();
            final List<UserDebugDataBean> recentDebugBeans = new ArrayList<>();

            for ( final UserIdentity userIdentity : recentUsers )
            {
                final UserDebugDataBean dataBean = UserDebugDataReader.readUserDebugData(
                        pwmApplication,
                        pwmRequest.getLocale(),
                        pwmRequest.getSessionLabel(),
                        userIdentity
                );
                recentDebugBeans.add( dataBean );
            }

            outputStream.write( JsonUtil.serializeCollection( recentDebugBeans, JsonUtil.Flag.PrettyPrint ).getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
    }

    static class ClusterInfoDebugGenerator implements Generator
    {
        @Override
        public String getFilename( )
        {
            return "cluster-info.json";
        }

        @Override
        public void outputItem(
                final PwmApplication pwmApplication,
                final PwmRequest pwmRequest,
                final OutputStream outputStream
        )
                throws Exception
        {
            final NodeService nodeService = pwmApplication.getClusterService();

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

    static class CacheServiceDebugItemGenerator implements Generator
    {
        @Override
        public String getFilename( )
        {
            return "cache-service-info.json";
        }

        @Override
        public void outputItem(
                final PwmApplication pwmApplication,
                final PwmRequest pwmRequest,
                final OutputStream outputStream
        )
                throws Exception
        {
            final CacheService cacheService = pwmApplication.getCacheService();

            final Map<String, Serializable> debugOutput = new LinkedHashMap<>( cacheService.debugInfo() );
            outputStream.write( JsonUtil.serializeMap( debugOutput, JsonUtil.Flag.PrettyPrint ).getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
    }

    static class DashboardDataDebugItemGenerator implements Generator
    {
        @Override
        public String getFilename( )
        {
            return "dashboard-data.json";
        }

        @Override
        public void outputItem(
                final PwmApplication pwmApplication,
                final PwmRequest pwmRequest,
                final OutputStream outputStream
        )
                throws Exception
        {
            final ContextManager contextManager = ContextManager.getContextManager( pwmRequest );
            final AppDashboardData appDashboardData = AppDashboardData.makeDashboardData(
                    pwmApplication,
                    contextManager,
                    pwmRequest.getLocale()
            );

            outputStream.write( JsonUtil.serialize( appDashboardData, JsonUtil.Flag.PrettyPrint ).getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
    }

    interface Generator
    {

        String getFilename( );

        void outputItem(
                PwmApplication pwmApplication,
                PwmRequest pwmRequest,
                OutputStream outputStream
        ) throws Exception;
    }

}
