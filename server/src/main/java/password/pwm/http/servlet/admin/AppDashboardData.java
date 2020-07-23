/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

package password.pwm.http.servlet.admin;

import lombok.Builder;
import lombok.Value;
import password.pwm.PwmAboutProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.http.ContextManager;
import password.pwm.http.bean.DisplayElement;
import password.pwm.i18n.Admin;
import password.pwm.i18n.Display;
import password.pwm.svc.PwmService;
import password.pwm.svc.node.NodeInfo;
import password.pwm.svc.sessiontrack.SessionTrackService;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmNumberFormat;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;


@Value
@Builder
public class AppDashboardData implements Serializable
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( AppDashboardData.class );

    @Value
    public static class ServiceData implements Serializable
    {
        private String name;
        private PwmService.STATUS status;
        private Collection<DataStorageMethod> storageMethod;
        private List<HealthRecord> health;
        private Map<String, String> debugData;
    }

    @Value
    public static class ThreadData implements Serializable
    {
        private String id;
        private String name;
        private String state;
        private String trace;
    }

    @Value
    public static class NodeData implements Serializable
    {
        private String instanceID;
        private String uptime;
        private String lastSeen;
        private NodeInfo.NodeState state;
        private boolean configMatch;
    }

    public enum Flag
    {
        IncludeLocalDbTableSizes,
        ShowThreadData,
    }

    private List<DisplayElement> about;
    private List<ServiceData> services;
    private List<DisplayElement> localDbInfo;
    private List<DisplayElement> javaAbout;
    private List<ThreadData> threads;
    private Map<LocalDB.DB, String> localDbSizes;
    private List<NodeData> nodeData;
    private String nodeSummary;
    private DataStorageMethod nodeStorageMethod;
    private int ldapConnectionCount;
    private int sessionCount;
    private int requestsInProgress;


    public static AppDashboardData makeDashboardData(
            final PwmApplication pwmApplication,
            final ContextManager contextManager,
            final Locale locale,
            final Flag... flags
    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        final AppDashboardDataBuilder builder = AppDashboardData.builder();
        builder.about( makeAboutData( pwmApplication, contextManager, locale ) );
        builder.services( getServiceData( pwmApplication ) );
        builder.localDbInfo( makeLocalDbInfo( pwmApplication, locale ) );
        builder.javaAbout( makeAboutJavaData( pwmApplication, locale ) );

        if ( JavaHelper.enumArrayContainsValue( flags, Flag.IncludeLocalDbTableSizes ) )
        {
            builder.localDbSizes( makeLocalDbTableSizes( pwmApplication, locale ) );
        }
        else
        {
            builder.localDbSizes( Collections.emptyMap() );
        }

        if ( JavaHelper.enumArrayContainsValue( flags, Flag.ShowThreadData ) )
        {
            builder.threads( makeThreadInfo() );
        }
        else
        {
            builder.threads( Collections.emptyList() );
        }

        builder.nodeData = makeNodeData( pwmApplication, locale );
        builder.nodeSummary = pwmApplication.getClusterService().isMaster()
                ? "This node is the current master"
                : "This node is not the current master";
        {
            final Collection<DataStorageMethod> dataStorageMethods = pwmApplication.getClusterService().serviceInfo().getUsedStorageMethods();
            if ( !JavaHelper.isEmpty( dataStorageMethods ) )
            {
                builder.nodeStorageMethod = dataStorageMethods.iterator().next();
            }
        }

        builder.ldapConnectionCount( ldapConnectionCount( pwmApplication ) );
        builder.sessionCount( pwmApplication.getSessionTrackService().sessionCount() );
        builder.requestsInProgress( pwmApplication.getInprogressRequests().get() );

        LOGGER.trace( () -> "AppDashboardData bean created in " + TimeDuration.compactFromCurrent( startTime ) );
        return builder.build();
    }

    private static int ldapConnectionCount( final PwmApplication pwmApplication )
    {
        return pwmApplication.getLdapConnectionService().connectionCount();
    }

    private static List<DisplayElement> makeAboutData(
            final PwmApplication pwmApplication,
            final ContextManager contextManager,
            final Locale locale
    )
    {
        final LocaleHelper.DisplayMaker l = new LocaleHelper.DisplayMaker( locale, Admin.class, pwmApplication );
        final String notApplicableValue = Display.getLocalizedMessage( locale, Display.Value_NotApplicable, pwmApplication.getConfig() );
        final PwmNumberFormat numberFormat = PwmNumberFormat.forLocale( locale );

        final List<DisplayElement> aboutData = new ArrayList<>();
        aboutData.add( new DisplayElement(
                "appVersion",
                DisplayElement.Type.string,
                l.forKey( "Field_AppVersion", PwmConstants.PWM_APP_NAME ),
                PwmConstants.SERVLET_VERSION
        ) );
        aboutData.add( new DisplayElement(
                "currentTime",
                DisplayElement.Type.timestamp,
                l.forKey( "Field_CurrentTime" ),
                JavaHelper.toIsoDate( Instant.now() )
        ) );
        aboutData.add( new DisplayElement(
                "startupTime",
                DisplayElement.Type.timestamp,
                l.forKey( "Field_StartTime" ),
                JavaHelper.toIsoDate( pwmApplication.getStartupTime() )
        ) );
        aboutData.add( new DisplayElement(
                "runningDuration",
                DisplayElement.Type.string,
                l.forKey( "Field_UpTime" ),
                TimeDuration.fromCurrent( pwmApplication.getStartupTime() ).asLongString( locale )
        ) );
        aboutData.add( new DisplayElement(
                "installTime",
                DisplayElement.Type.timestamp,
                l.forKey( "Field_InstallTime" ),
                JavaHelper.toIsoDate( pwmApplication.getInstallTime() )
        ) );
        aboutData.add( new DisplayElement(
                "siteURL",
                DisplayElement.Type.string,
                l.forKey( "Field_SiteURL" ),
                pwmApplication.getConfig().readSettingAsString( PwmSetting.PWM_SITE_URL )
        ) );
        aboutData.add( new DisplayElement(
                "instanceID",
                DisplayElement.Type.string,
                l.forKey( "Field_InstanceID" ),
                pwmApplication.getInstanceID()
        ) );
        aboutData.add( new DisplayElement(
                "configRestartCounter",
                DisplayElement.Type.number,
                "Configuration Restart Counter",
                contextManager == null ? notApplicableValue : numberFormat.format( contextManager.getRestartCount() )
        ) );
        aboutData.add( new DisplayElement(
                "chaiApiVersion",
                DisplayElement.Type.string,
                l.forKey( "Field_ChaiAPIVersion" ),
                com.novell.ldapchai.ChaiConstant.CHAI_API_VERSION
        ) );

        return Collections.unmodifiableList( aboutData );
    }

    private static List<ServiceData> getServiceData( final PwmApplication pwmApplication )
    {
        final Map<String, ServiceData> returnData = new TreeMap<>();
        for ( final PwmService pwmService : pwmApplication.getPwmServices() )
        {
            final PwmService.ServiceInfo serviceInfo = pwmService.serviceInfo();
            final Collection<DataStorageMethod> storageMethods = serviceInfo == null
                    ? Collections.emptyList()
                    : serviceInfo.getUsedStorageMethods() == null
                    ? Collections.emptyList()
                    : serviceInfo.getUsedStorageMethods();

            final Map<String, String> debugData = serviceInfo == null
                    ? Collections.emptyMap()
                    : serviceInfo.getDebugProperties() == null
                    ? Collections.emptyMap()
                    : serviceInfo.getDebugProperties();

            returnData.put( pwmService.getClass().getSimpleName(), new ServiceData(
                    pwmService.getClass().getSimpleName(),
                    pwmService.status(),
                    storageMethods,
                    pwmService.healthCheck(),
                    debugData
            ) );
        }

        return Collections.unmodifiableList( new ArrayList<>( returnData.values() ) );
    }

    private static List<DisplayElement> makeLocalDbInfo( final PwmApplication pwmApplication, final Locale locale )
            throws PwmUnrecoverableException
    {
        final List<DisplayElement> localDbInfo = new ArrayList<>();
        final String notApplicable = Display.getLocalizedMessage( locale, Display.Value_NotApplicable, pwmApplication.getConfig() );
        final PwmNumberFormat numberFormat = PwmNumberFormat.forLocale( locale );

        localDbInfo.add( new DisplayElement(
                "worlistSize",
                DisplayElement.Type.number,
                "Word List Dictionary Size",
                numberFormat.format( pwmApplication.getWordlistService().size() )
        ) );

        localDbInfo.add( new DisplayElement(
                "seedlistSize",
                DisplayElement.Type.number,
                "Seed List Dictionary Size",
                numberFormat.format( pwmApplication.getSeedlistManager().size() )
        ) );

        localDbInfo.add( new DisplayElement(
                "sharedHistorySize",
                DisplayElement.Type.number,
                "Shared Password History Size",
                numberFormat.format( pwmApplication.getSharedHistoryManager().size() )
        ) );
        {
            final Instant oldestEntryAge = pwmApplication.getSharedHistoryManager().getOldestEntryTime();
            final String display = oldestEntryAge == null
                    ? notApplicable
                    : TimeDuration.fromCurrent( oldestEntryAge ).asCompactString();
            localDbInfo.add( new DisplayElement(
                    "oldestSharedHistory",
                    DisplayElement.Type.string,
                    "Oldest Shared Password Entry",
                    display
            ) );
        }
        localDbInfo.add( new DisplayElement(
                "emailQueueSize",
                DisplayElement.Type.number,
                "Email Queue Size",
                numberFormat.format( pwmApplication.getEmailQueue().queueSize() )
        ) );
        localDbInfo.add( new DisplayElement(
                "smsQueueSize",
                DisplayElement.Type.number,
                "SMS Queue Size",
                numberFormat.format( pwmApplication.getSmsQueue().queueSize() )
        ) );
        localDbInfo.add( new DisplayElement(
                "sharedHistorySize",
                DisplayElement.Type.number,
                "Syslog Queue Size",
                String.valueOf( pwmApplication.getAuditManager().syslogQueueSize() )
        ) );
        localDbInfo.add( new DisplayElement(
                "localAuditRecords",
                DisplayElement.Type.number,
                "Audit Records",
                pwmApplication.getAuditManager().sizeToDebugString()
        ) );
        {
            final Instant eldestAuditRecord = pwmApplication.getAuditManager().eldestVaultRecord();
            final String display = eldestAuditRecord != null
                    ? TimeDuration.fromCurrent( eldestAuditRecord ).asLongString()
                    : notApplicable;
            localDbInfo.add( new DisplayElement(
                    "oldestLocalAuditRecords",
                    DisplayElement.Type.string,
                    "Oldest Audit Record",
                    display
            ) );
        }
        localDbInfo.add( new DisplayElement(
                "logEvents",
                DisplayElement.Type.number,
                "Log Events",
                pwmApplication.getLocalDBLogger().sizeToDebugString()
        ) );
        {
            final String display = pwmApplication.getLocalDBLogger() != null && pwmApplication.getLocalDBLogger().getTailDate() != null
                    ? TimeDuration.fromCurrent( pwmApplication.getLocalDBLogger().getTailDate() ).asLongString()
                    : notApplicable;
            localDbInfo.add( new DisplayElement(
                    "oldestLogEvents",
                    DisplayElement.Type.string,
                    "Oldest Log Event",
                    display
            ) );
        }
        {
            final String display = pwmApplication.getLocalDB() == null
                    ? notApplicable
                    : pwmApplication.getLocalDB().getFileLocation() == null
                    ? notApplicable
                    : StringUtil.formatDiskSize( FileSystemUtility.getFileDirectorySize(
                    pwmApplication.getLocalDB().getFileLocation() ) );
            localDbInfo.add( new DisplayElement(
                    "localDbSizeOnDisk",
                    DisplayElement.Type.string,
                    "LocalDB Size On Disk",
                    display
            ) );
        }
        {
            final String display = pwmApplication.getLocalDB() == null
                    ? notApplicable
                    : pwmApplication.getLocalDB().getFileLocation() == null
                    ? notApplicable
                    : StringUtil.formatDiskSize( FileSystemUtility.diskSpaceRemaining( pwmApplication.getLocalDB().getFileLocation() ) );
            localDbInfo.add( new DisplayElement(
                    "localDbFreeSpace",
                    DisplayElement.Type.string,
                    "LocalDB Free Space",
                    display
            ) );
        }

        return Collections.unmodifiableList( localDbInfo );
    }

    private static Map<LocalDB.DB, String> makeLocalDbTableSizes(
            final PwmApplication pwmApplication,
            final Locale locale
    )
    {
        final Map<LocalDB.DB, String> returnData = new LinkedHashMap<>();
        final LocalDB localDB = pwmApplication.getLocalDB();
        final PwmNumberFormat numberFormat = PwmNumberFormat.forLocale( locale );
        try
        {
            for ( final LocalDB.DB db : LocalDB.DB.values() )
            {
                returnData.put( db, numberFormat.format( localDB.size( db ) ) );
            }
        }
        catch ( final LocalDBException e )
        {
            LOGGER.error( () -> "error making localDB size bean: " + e.getMessage() );
        }
        return Collections.unmodifiableMap( returnData );
    }


    private static List<DisplayElement> makeAboutJavaData(
            final PwmApplication pwmApplication,
            final Locale locale
    )
    {
        final Map<PwmAboutProperty, String> aboutMap = PwmAboutProperty.makeInfoBean( pwmApplication );
        final List<DisplayElement> javaInfo = new ArrayList<>();
        final String notApplicable = Display.getLocalizedMessage( locale, Display.Value_NotApplicable, pwmApplication.getConfig() );

        {
            final List<PwmAboutProperty> interestedProperties = Arrays.asList(
                    PwmAboutProperty.java_vmName,
                    PwmAboutProperty.java_vmVendor,
                    PwmAboutProperty.java_vmVersion,
                    PwmAboutProperty.java_runtimeVersion,
                    PwmAboutProperty.java_vmLocation,
                    PwmAboutProperty.java_appServerInfo,
                    PwmAboutProperty.java_osName,
                    PwmAboutProperty.java_osVersion,
                    PwmAboutProperty.java_osArch,
                    PwmAboutProperty.java_memoryFree,
                    PwmAboutProperty.java_memoryAllocated,
                    PwmAboutProperty.java_memoryMax,
                    PwmAboutProperty.java_threadCount
            );

            for ( final PwmAboutProperty property : interestedProperties )
            {
                javaInfo.add( new DisplayElement(
                        property.name(),
                        DisplayElement.Type.string,
                        property.getLabel(),
                        aboutMap.getOrDefault( property, notApplicable )
                ) );
            }
        }

        {
            final PwmNumberFormat numberFormat = PwmNumberFormat.forLocale( locale );

            final String display = numberFormat.format( pwmApplication.getResourceServletService().itemsInCache() )
                    + " items (" + numberFormat.format( pwmApplication.getResourceServletService().bytesInCache() ) + " bytes)";

            javaInfo.add( new DisplayElement(
                    "resourceFileServletCacheSize",
                    DisplayElement.Type.string,
                    "ResourceFileServlet Cache",
                    display
            ) );
        }

        javaInfo.add( new DisplayElement(
                "resourceFileServletCacheHitRatio",
                DisplayElement.Type.string,
                "ResourceFileServlet Cache Hit Ratio",
                pwmApplication.getResourceServletService().cacheHitRatio().pretty( 2 )
        ) );

        {
            final Map<SessionTrackService.DebugKey, String> debugInfoMap = pwmApplication.getSessionTrackService().getDebugData();

            javaInfo.add( new DisplayElement(
                    "sessionTotalSize",
                    DisplayElement.Type.string,
                    "Estimated Session Total Size",
                    debugInfoMap.get( SessionTrackService.DebugKey.HttpSessionTotalSize )
            ) );

            javaInfo.add( new DisplayElement(
                    "sessionAverageSize",
                    DisplayElement.Type.string,
                    "Estimated Session Average Size",
                    debugInfoMap.get( SessionTrackService.DebugKey.HttpSessionAvgSize )
            ) );
        }

        return Collections.unmodifiableList( javaInfo );
    }

    private static List<ThreadData> makeThreadInfo( )
    {
        final Map<Long, ThreadData> returnData = new TreeMap<>();
        final ThreadInfo[] threads = ManagementFactory.getThreadMXBean().dumpAllThreads( true, true );
        for ( final ThreadInfo threadInfo : threads )
        {
            returnData.put( threadInfo.getThreadId(), new ThreadData(
                    "thread-" + Long.toString( threadInfo.getThreadId() ),
                    threadInfo.getThreadName(),
                    threadInfo.getThreadState().toString().toLowerCase(),
                    JavaHelper.threadInfoToString( threadInfo )
            ) );
        }
        return Collections.unmodifiableList( new ArrayList<>( returnData.values() ) );
    }

    private static List<NodeData> makeNodeData(
            final PwmApplication pwmApplication,
            final Locale locale
    )
    {
        if ( pwmApplication.getClusterService().status() != PwmService.STATUS.OPEN )
        {
            return Collections.emptyList();
        }

        final String notApplicable = Display.getLocalizedMessage( locale, Display.Value_NotApplicable, pwmApplication.getConfig() );
        final List<NodeData> nodeData = new ArrayList<>();

        try
        {
            for ( final NodeInfo nodeInfo : pwmApplication.getClusterService().nodes() )
            {

                final String uptime = nodeInfo.getStartupTime() == null
                        ? notApplicable
                        : TimeDuration.fromCurrent( nodeInfo.getStartupTime() ).asLongString( locale );

                nodeData.add( new NodeData(
                        nodeInfo.getInstanceID(),
                        uptime,
                        JavaHelper.toIsoDate( nodeInfo.getLastSeen() ),
                        nodeInfo.getNodeState(),
                        nodeInfo.isConfigMatch()
                ) );
            }
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.trace( () -> "error building AppDashboardData node-state: " + e.getMessage() );
        }

        return Collections.unmodifiableList( nodeData );
    }
}
