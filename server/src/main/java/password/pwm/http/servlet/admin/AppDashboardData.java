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

package password.pwm.http.servlet.admin;

import lombok.Builder;
import lombok.Value;
import password.pwm.PwmAboutProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.http.ContextManager;
import password.pwm.http.bean.DisplayElement;
import password.pwm.i18n.Admin;
import password.pwm.i18n.Display;
import password.pwm.ldap.LdapConnectionService;
import password.pwm.svc.PwmService;
import password.pwm.svc.node.NodeInfo;
import password.pwm.svc.node.NodeService;
import password.pwm.svc.sessiontrack.SessionTrackService;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmNumberFormat;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.LocalDBLogger;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;


@Value
@Builder
public class AppDashboardData implements Serializable
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( AppDashboardData.class );

    @Value
    public static class ServiceData implements Serializable, Comparable<ServiceData>
    {
        private static final Comparator<ServiceData> COMPARATOR = Comparator.comparing(
                ServiceData::getDomainID,
                Comparator.nullsLast( Comparator.naturalOrder() ) )
                .thenComparing(
                        ServiceData::getName,
                        Comparator.nullsLast( Comparator.naturalOrder() ) )
                .thenComparing(
                        ServiceData::getStatus,
                        Comparator.nullsLast( Comparator.naturalOrder() ) );

        private String guid;
        private DomainID domainID;
        private String name;
        private PwmService.STATUS status;
        private Collection<DataStorageMethod> storageMethod;
        private List<HealthRecord> health;
        private Map<String, String> debugData;

        @Override
        public int compareTo( final ServiceData otherServiceData )
        {
            return COMPARATOR.compare( this, otherServiceData );
        }
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
    private long ldapConnectionCount;
    private int sessionCount;
    private int requestsInProgress;


    public static AppDashboardData makeDashboardData(
            final PwmDomain pwmDomain,
            final ContextManager contextManager,
            final Locale locale,
            final Flag... flags
    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        final AppDashboardDataBuilder builder = AppDashboardData.builder();
        builder.about( makeAboutData( pwmDomain, contextManager, locale ) );
        builder.services( makeServiceData( pwmDomain.getPwmApplication() ) );
        builder.localDbInfo( makeLocalDbInfo( pwmDomain, locale ) );
        builder.javaAbout( makeAboutJavaData( pwmDomain, locale ) );

        if ( JavaHelper.enumArrayContainsValue( flags, Flag.IncludeLocalDbTableSizes ) )
        {
            builder.localDbSizes( makeLocalDbTableSizes( pwmDomain, locale ) );
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

        builder.nodeData = makeNodeData( pwmDomain, locale );
        builder.nodeSummary = pwmDomain.getPwmApplication().getNodeService().isMaster()
                ? "This node is the current master"
                : "This node is not the current master";
        {
            final Collection<DataStorageMethod> dataStorageMethods = pwmDomain.getPwmApplication().getNodeService().serviceInfo().getStorageMethods();
            if ( !CollectionUtil.isEmpty( dataStorageMethods ) )
            {
                builder.nodeStorageMethod = dataStorageMethods.iterator().next();
            }
        }

        builder.ldapConnectionCount( LdapConnectionService.totalLdapConnectionCount( pwmDomain.getPwmApplication() ) );
        builder.sessionCount( pwmDomain.getSessionTrackService().sessionCount() );
        builder.requestsInProgress( pwmDomain.getPwmApplication().getActiveServletRequests().get() );

        LOGGER.trace( () -> "AppDashboardData bean created", () -> TimeDuration.fromCurrent( startTime ) );
        return builder.build();
    }

    private static List<DisplayElement> makeAboutData(
            final PwmDomain pwmDomain,
            final ContextManager contextManager,
            final Locale locale
    )
    {
        final LocaleHelper.DisplayMaker l = new LocaleHelper.DisplayMaker( locale, Admin.class, pwmDomain );
        final String notApplicableValue = Display.getLocalizedMessage( locale, Display.Value_NotApplicable, pwmDomain.getConfig() );
        final PwmNumberFormat numberFormat = PwmNumberFormat.forLocale( locale );

        return List.of( new DisplayElement(
                "appVersion",
                DisplayElement.Type.string,
                l.forKey( "Field_AppVersion", PwmConstants.PWM_APP_NAME ),
                PwmConstants.SERVLET_VERSION
        ), new DisplayElement(
                "appBuildTime",
                DisplayElement.Type.timestamp,
                l.forKey( "Field_AppBuildTime" ),
                PwmConstants.BUILD_TIME
        ), new DisplayElement(
                "currentTime",
                DisplayElement.Type.timestamp,
                l.forKey( "Field_CurrentTime" ),
                JavaHelper.toIsoDate( Instant.now() )
        ), new DisplayElement(
                "startupTime",
                DisplayElement.Type.timestamp,
                l.forKey( "Field_StartTime" ),
                JavaHelper.toIsoDate( pwmDomain.getPwmApplication().getStartupTime() )
        ), new DisplayElement(
                "runningDuration",
                DisplayElement.Type.string,
                l.forKey( "Field_UpTime" ),
                TimeDuration.fromCurrent( pwmDomain.getPwmApplication().getStartupTime() ).asLongString( locale )
        ), new DisplayElement(
                "installTime",
                DisplayElement.Type.timestamp,
                l.forKey( "Field_InstallTime" ),
                JavaHelper.toIsoDate( pwmDomain.getPwmApplication().getInstallTime() )
        ), new DisplayElement(
                "siteURL",
                DisplayElement.Type.string,
                l.forKey( "Field_SiteURL" ),
                pwmDomain.getConfig().getAppConfig().readSettingAsString( PwmSetting.PWM_SITE_URL )
        ), new DisplayElement(
                "instanceID",
                DisplayElement.Type.string,
                l.forKey( "Field_InstanceID" ),
                pwmDomain.getPwmApplication().getInstanceID()
        ), new DisplayElement(
                "configRestartCounter",
                DisplayElement.Type.number,
                "Configuration Restart Counter",
                contextManager == null ? notApplicableValue : numberFormat.format( contextManager.getRestartCount() )
        ), new DisplayElement(
                "chaiApiVersion",
                DisplayElement.Type.string,
                l.forKey( "Field_ChaiAPIVersion" ),
                com.novell.ldapchai.ChaiConstant.CHAI_API_VERSION
        ) );
    }

    public static List<ServiceData> makeServiceData( final PwmApplication pwmApplication )
            throws PwmUnrecoverableException
    {
        final List<ServiceData> returnData = new ArrayList<>();
        for ( final Map.Entry<DomainID, List<PwmService>> domainIDListEntry : pwmApplication.getAppAndDomainPwmServices().entrySet() )
        {
            final DomainID domainID = domainIDListEntry.getKey();
            for ( final PwmService pwmService : domainIDListEntry.getValue() )
            {
                final PwmService.ServiceInfo serviceInfo = pwmService.serviceInfo();
                final Collection<DataStorageMethod> storageMethods = serviceInfo == null
                        ? Collections.emptyList()
                        : serviceInfo.getStorageMethods() == null
                                ? Collections.emptyList()
                                : serviceInfo.getStorageMethods();

                final Map<String, String> debugData = serviceInfo == null
                        ? Collections.emptyMap()
                        : serviceInfo.getDebugProperties() == null
                                ? Collections.emptyMap()
                                : serviceInfo.getDebugProperties();

                final String guid = pwmApplication.getSecureService().hash( domainID + pwmService.getClass().getSimpleName() );

                returnData.add( new ServiceData(
                        guid,
                        domainID,
                        pwmService.getClass().getSimpleName(),
                        pwmService.status(),
                        storageMethods,
                        pwmService.healthCheck(),
                        debugData
                ) );
            }
        }

        Collections.sort( returnData );
        return List.copyOf( returnData );
    }

    private static List<DisplayElement> makeLocalDbInfo( final PwmDomain pwmDomain, final Locale locale )
            throws PwmUnrecoverableException
    {
        final List<DisplayElement> localDbInfo = new ArrayList<>();
        final String notApplicable = Display.getLocalizedMessage( locale, Display.Value_NotApplicable, pwmDomain.getConfig() );
        final PwmNumberFormat numberFormat = PwmNumberFormat.forLocale( locale );

        localDbInfo.add( new DisplayElement(
                "worlistSize",
                DisplayElement.Type.number,
                "Word List Dictionary Size",
                numberFormat.format( pwmDomain.getPwmApplication().getWordlistService().size() )
        ) );

        localDbInfo.add( new DisplayElement(
                "seedlistSize",
                DisplayElement.Type.number,
                "Seed List Dictionary Size",
                numberFormat.format( pwmDomain.getPwmApplication().getSeedlistManager().size() )
        ) );

        localDbInfo.add( new DisplayElement(
                "sharedHistorySize",
                DisplayElement.Type.number,
                "Shared Password History Size",
                numberFormat.format( pwmDomain.getSharedHistoryManager().size() )
        ) );
        {
            final Instant oldestEntryAge = pwmDomain.getSharedHistoryManager().getOldestEntryTime();
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
                numberFormat.format( pwmDomain.getPwmApplication().getEmailQueue().queueSize() )
        ) );
        localDbInfo.add( new DisplayElement(
                "smsQueueSize",
                DisplayElement.Type.number,
                "SMS Queue Size",
                numberFormat.format( pwmDomain.getPwmApplication().getSmsQueue().queueSize() )
        ) );
        localDbInfo.add( new DisplayElement(
                "sharedHistorySize",
                DisplayElement.Type.number,
                "Syslog Queue Size",
                String.valueOf( pwmDomain.getAuditService().syslogQueueSize() )
        ) );
        localDbInfo.add( new DisplayElement(
                "localAuditRecords",
                DisplayElement.Type.number,
                "Audit Records",
                pwmDomain.getAuditService().sizeToDebugString()
        ) );
        {
            final Optional<Instant> eldestAuditRecord = pwmDomain.getAuditService().eldestVaultRecord();
            final String display = eldestAuditRecord.isPresent()
                    ? TimeDuration.fromCurrent( eldestAuditRecord.get() ).asLongString()
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
                pwmDomain.getPwmApplication().getLocalDBLogger().sizeToDebugString()
        ) );
        {
            final LocalDBLogger localDBLogger = pwmDomain.getPwmApplication().getLocalDBLogger();
            final String display = localDBLogger != null
                    && localDBLogger.getTailDate().isPresent()
                    ? TimeDuration.fromCurrent( localDBLogger.getTailDate().get() ).asLongString()
                    : notApplicable;
            localDbInfo.add( new DisplayElement(
                    "oldestLogEvents",
                    DisplayElement.Type.string,
                    "Oldest Log Event",
                    display
            ) );
        }
        {
            final String display = pwmDomain.getPwmApplication().getLocalDB() == null
                    ? notApplicable
                    : pwmDomain.getPwmApplication().getLocalDB().getFileLocation() == null
                            ? notApplicable
                            : StringUtil.formatDiskSize( FileSystemUtility.getFileDirectorySize(
                                    pwmDomain.getPwmApplication().getLocalDB().getFileLocation() ) );
            localDbInfo.add( new DisplayElement(
                    "localDbSizeOnDisk",
                    DisplayElement.Type.string,
                    "LocalDB Size On Disk",
                    display
            ) );
        }
        {
            final String display = pwmDomain.getPwmApplication().getLocalDB() == null
                    ? notApplicable
                    : pwmDomain.getPwmApplication().getLocalDB().getFileLocation() == null
                            ? notApplicable
                            : StringUtil.formatDiskSize( FileSystemUtility.diskSpaceRemaining( pwmDomain.getPwmApplication().getLocalDB().getFileLocation() ) );
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
            final PwmDomain pwmDomain,
            final Locale locale
    )
    {
        final Map<LocalDB.DB, String> returnData = new LinkedHashMap<>();
        final LocalDB localDB = pwmDomain.getPwmApplication().getLocalDB();
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
            final PwmDomain pwmDomain,
            final Locale locale
    )
    {
        final Map<PwmAboutProperty, String> aboutMap = PwmAboutProperty.makeInfoBean( pwmDomain.getPwmApplication() );
        final List<DisplayElement> javaInfo = new ArrayList<>();
        final String notApplicable = Display.getLocalizedMessage( locale, Display.Value_NotApplicable, pwmDomain.getConfig() );

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

            final String display = numberFormat.format( pwmDomain.getResourceServletService().itemsInCache() )
                    + " items (" + numberFormat.format( pwmDomain.getResourceServletService().bytesInCache() ) + " bytes)";

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
                pwmDomain.getResourceServletService().cacheHitRatio().pretty( 2 )
        ) );

        {
            final Map<SessionTrackService.DebugKey, String> debugInfoMap = pwmDomain.getSessionTrackService().getDebugData();

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
        return List.copyOf( returnData.values() );
    }

    private static List<NodeData> makeNodeData(
            final PwmDomain pwmDomain,
            final Locale locale
    )
    {
        final NodeService nodeService = pwmDomain.getPwmApplication().getNodeService();
        if ( nodeService.status() != PwmService.STATUS.OPEN )
        {
            return Collections.emptyList();
        }

        final String notApplicable = Display.getLocalizedMessage( locale, Display.Value_NotApplicable, pwmDomain.getConfig() );
        final List<NodeData> nodeData = new ArrayList<>();

        try
        {
            for ( final NodeInfo nodeInfo : nodeService.nodes() )
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
