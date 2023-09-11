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

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.pub.SessionStateInfoBean;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmURL;
import password.pwm.http.bean.DisplayElement;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.i18n.Message;
import password.pwm.svc.event.AuditEventType;
import password.pwm.svc.event.AuditRecord;
import password.pwm.svc.intruder.IntruderRecordType;
import password.pwm.svc.intruder.PublicIntruderRecord;
import password.pwm.svc.pwnotify.PwNotifyService;
import password.pwm.svc.pwnotify.PwNotifyStoredJobState;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.EnumUtil;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmTimeUtil;
import password.pwm.util.java.PwmUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.LocalDBLogger;
import password.pwm.util.logging.LocalDBSearchQuery;
import password.pwm.util.logging.LocalDBSearchResults;
import password.pwm.util.logging.PwmLogEvent;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@WebServlet(
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/admin/system/dashboard",
                PwmConstants.URL_PREFIX_PRIVATE + "/admin/system/Administration",
        }
)
public class SystemAdminServlet extends ControlledPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SystemAdminServlet.class );

    @Override
    protected PwmLogger getLogger()
    {
        return LOGGER;
    }

    public enum SystemAdminAction implements AbstractPwmServlet.ProcessAction
    {
        viewLogWindow( HttpMethod.GET ),
        downloadAuditLogCsv( HttpMethod.POST ),
        downloadSessionsCsv( HttpMethod.POST ),
        clearIntruderTable( HttpMethod.POST ),
        auditData( HttpMethod.GET ),
        sessionData( HttpMethod.GET ),
        intruderData( HttpMethod.GET ),
        startPwNotifyJob( HttpMethod.POST ),
        readPwNotifyStatus( HttpMethod.POST ),
        readPwNotifyLog( HttpMethod.POST ),
        readLogData( HttpMethod.POST ),
        downloadLogData( HttpMethod.POST ),;

        private final Collection<HttpMethod> method;

        SystemAdminAction( final HttpMethod... method )
        {
            this.method = List.of( method );
        }

        @Override
        public Collection<HttpMethod> permittedMethods( )
        {
            return method;
        }
    }

    @Override
    public Optional<Class<? extends ProcessAction>> getProcessActionsClass( )
    {
        return Optional.of( SystemAdminAction.class );
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        forwardToJsp( pwmRequest );
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        return preProcessAdminCheck( pwmRequest );
    }

    public static ProcessStatus preProcessAdminCheck( final PwmRequest pwmRequest )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        if ( !pwmRequest.isAuthenticated() )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_AUTHENTICATION_REQUIRED );
            pwmRequest.respondWithError( errorInformation );
            return ProcessStatus.Halt;
        }

        if ( !pwmRequest.checkPermission( Permission.PWMADMIN ) )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNAUTHORIZED );
            pwmRequest.respondWithError( errorInformation );
            return ProcessStatus.Halt;
        }
        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "viewLogWindow" )
    public ProcessStatus processViewLogWindow(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        pwmRequest.forwardToJsp( JspUrl.ADMIN_LOGVIEW_WINDOW );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "downloadAuditLogCsv" )
    public ProcessStatus downloadAuditLogCsv(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();

        pwmRequest.getPwmResponse().markAsDownload(
                HttpContentType.csv,
                pwmDomain.getConfig().readAppProperty( AppProperty.DOWNLOAD_FILENAME_AUDIT_RECORDS_CSV )
        );

        final OutputStream outputStream = pwmRequest.getPwmResponse().getOutputStream();
        try
        {
            pwmDomain.getAuditService().outputVaultToCsv( outputStream, pwmRequest.getLocale(), true );
        }
        catch ( final Exception e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, e.getMessage() );
            pwmRequest.respondWithError( errorInformation );
        }
        finally
        {
            outputStream.close();
        }
        return ProcessStatus.Halt;
    }


    @ActionHandler( action = "downloadSessionsCsv" )
    public ProcessStatus downloadSessionsCsv( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();

        pwmRequest.getPwmResponse().markAsDownload(
                HttpContentType.csv,
                pwmRequest.getPwmDomain().getConfig().readAppProperty( AppProperty.DOWNLOAD_FILENAME_SESSIONS_CSV )
        );

        final OutputStream outputStream = pwmRequest.getPwmResponse().getOutputStream();
        try
        {
            pwmDomain.getSessionTrackService().outputToCsv( pwmRequest.getLocale(), pwmRequest.getDomainConfig(), outputStream );
        }
        catch ( final Exception e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, e.getMessage() );
            pwmRequest.respondWithError( errorInformation );
        }
        finally
        {
            outputStream.close();
        }
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "clearIntruderTable" )
    public ProcessStatus processClearIntruderTable(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        if ( !pwmRequest.checkPermission( Permission.PWMADMIN ) )
        {
            LOGGER.info( pwmRequest, () -> "unable to execute clear intruder records" );
            return ProcessStatus.Halt;
        }

        //pwmApplication.getIntruderManager().clear();

        final RestResultBean restResultBean = RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "auditData" )
    public ProcessStatus restAuditDataHandler( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException
    {
        final Instant startTime = Instant.now();
        final TimeDuration maxSearchTime = TimeDuration.SECONDS_10;
        final int max = readMaxParameter( pwmRequest, 100, 10 * 1000 );
        final AuditEventType auditDataType = AuditEventType.valueOf( pwmRequest.readParameterAsString( "type", AuditEventType.USER.name() ) );
        final ArrayList<AuditRecord> records = new ArrayList<>();
        final Iterator<AuditRecord> iterator = pwmRequest.getPwmDomain().getAuditService().readVault();

        while (
                iterator.hasNext()
                        && records.size() < max
                        && TimeDuration.fromCurrent( startTime ).isShorterThan( maxSearchTime )
        )
        {
            final AuditRecord loopRecord = iterator.next();
            if ( auditDataType == loopRecord.type() )
            {
                records.add( loopRecord );
            }
        }

        final HashMap<String, Object> resultData = new HashMap<>( Collections.singletonMap( "records", records ) );

        final RestResultBean<Map> restResultBean = RestResultBean.withData( resultData, Map.class );
        LOGGER.debug( pwmRequest, () -> "output " + records.size() + " audit records." );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "sessionData" )
    public ProcessStatus restSessionDataHandler( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException
    {
        final int max = readMaxParameter( pwmRequest, 1000, 10 * 1000 );

        final ArrayList<SessionStateInfoBean> gridData = new ArrayList<>();
        int counter = 0;
        final Iterator<SessionStateInfoBean> infos = pwmRequest.getPwmDomain().getSessionTrackService().getSessionInfoIterator();
        while ( counter < max && infos.hasNext() )
        {
            gridData.add( infos.next() );
            counter++;
        }
        final RestResultBean restResultBean = RestResultBean.withData( gridData, List.class );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "intruderData" )
    public ProcessStatus restIntruderDataHandler( final PwmRequest pwmRequest )
            throws  PwmUnrecoverableException, IOException
    {
        final int max = readMaxParameter( pwmRequest, 1000, 10 * 1000 );

        final SortedMap<String, List<PublicIntruderRecord>> returnData = new TreeMap<>();
        try
        {
            for ( final IntruderRecordType recordType : IntruderRecordType.values() )
            {
                returnData.put( recordType.toString(), pwmRequest.getPwmApplication().getIntruderSystemService().getRecords( recordType, max ) );
            }
        }
        catch ( final PwmException e )
        {
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_INTERNAL, e.getMessage() );
            LOGGER.debug( pwmRequest, errorInfo );
            pwmRequest.outputJsonResult( RestResultBean.fromError( errorInfo ) );
        }

        final RestResultBean restResultBean = RestResultBean.withData( returnData, Map.class );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }



    private void processThreadPageView( final PwmRequest pwmRequest ) throws IOException
    {
        pwmRequest.getPwmResponse().setContentType( HttpContentType.plain );
        final Writer writer = pwmRequest.getPwmResponse().getWriter();
        final ThreadInfo[] threads = ManagementFactory.getThreadMXBean().dumpAllThreads( true, true );
        for ( final ThreadInfo threadInfo : threads )
        {
            writer.write( JavaHelper.threadInfoToString( threadInfo ) );
        }
    }

    private void forwardToJsp( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException
    {
        final Optional<Page> requestedPage = Page.forUrl( pwmRequest.getURL() );
        if ( requestedPage.isEmpty() )
        {
            final String url = pwmRequest.getBasePath() + PwmServletDefinition.SystemAdmin.servletUrl() + Page.dashboard.getUrlSuffix();
            pwmRequest.getPwmResponse().sendRedirect( url );
            return;
        }

        final Page currentPage = requestedPage.get();

        if ( currentPage == Page.threads )
        {
            processThreadPageView( pwmRequest );
            return;
        }

        if ( currentPage == Page.dashboard )
        {
            final List<AppDashboardData.Flag> flags = new ArrayList<>();
            if ( pwmRequest.readParameterAsBoolean( "showLocalDBCounts" ) )
            {
                flags.add( AppDashboardData.Flag.IncludeLocalDbTableSizes );
            }

            if ( pwmRequest.readParameterAsBoolean( "showThreadDetails" ) )
            {
                flags.add( AppDashboardData.Flag.ShowThreadData );
            }

            final AppDashboardData appDashboardData = AppDashboardData.makeDashboardData(
                    pwmRequest.getPwmDomain(),
                    pwmRequest.getContextManager(),
                    pwmRequest.getLocale(),
                    flags.toArray( new AppDashboardData.Flag[0] )
            );
            pwmRequest.setAttribute( PwmRequestAttribute.AppDashboardData, appDashboardData );
        }

        pwmRequest.forwardToJsp( currentPage.getJspURL() );
    }

    private static int readMaxParameter( final PwmRequest pwmRequest, final int defaultValue, final int maxValue )
            throws PwmUnrecoverableException
    {
        final String stringMax = pwmRequest.readParameterAsString( "maximum", String.valueOf( defaultValue ) );
        return Math.min( Integer.parseInt( stringMax ), maxValue );
    }

    public enum Page
    {
        dashboard( JspUrl.ADMIN_DASHBOARD, "/dashboard" ),
        activity( JspUrl.ADMIN_ACTIVITY, "/activity" ),
        tokenLookup( JspUrl.ADMIN_TOKEN_LOOKUP, "/tokens" ),
        viewLog( JspUrl.ADMIN_LOGVIEW, "/logs" ),
        urlReference( JspUrl.ADMIN_URLREFERENCE, "/urls" ),
        threads( JspUrl.ADMIN_DEBUG, "/threads" ),;

        private final JspUrl jspURL;
        private final String urlSuffix;

        Page( final JspUrl jspURL, final String urlSuffix )
        {
            this.jspURL = jspURL;
            this.urlSuffix = urlSuffix;
        }

        public JspUrl getJspURL( )
        {
            return jspURL;
        }

        public String getUrlSuffix( )
        {
            return urlSuffix;
        }

        public static Optional<Page> forUrl( final PwmURL pwmURL )
        {
            final String url = pwmURL.toString();
            return Stream.of( Page.values() )
                    .filter( page -> url.endsWith( page.getUrlSuffix() ) )
                    .findFirst();
        }
    }

    @ActionHandler( action = "readPwNotifyStatus" )
    public ProcessStatus restreadPwNotifyStatus( final PwmRequest pwmRequest ) throws IOException, PwmUnrecoverableException
    {
        int key = 0;
        if ( !pwmRequest.getDomainConfig().readSettingAsBoolean( PwmSetting.PW_EXPY_NOTIFY_ENABLE ) )
        {
            final DisplayElement displayElement = DisplayElement.create( String.valueOf( key++ ), DisplayElement.Type.string, "Status",
                    "Password Notification Feature is not enabled.  See setting: "
                            + PwmSetting.PW_EXPY_NOTIFY_ENABLE.toMenuLocationDebug( null, pwmRequest.getLocale() ) );
            final PwNotifyStatusBean pwNotifyStatusBean = new PwNotifyStatusBean( Collections.singletonList( displayElement ), false );
            pwmRequest.outputJsonResult( RestResultBean.withData( pwNotifyStatusBean, PwNotifyStatusBean.class ) );
            return ProcessStatus.Halt;
        }

        final List<DisplayElement> statusData = new ArrayList<>( );
        final DomainConfig config = pwmRequest.getDomainConfig();
        final Locale locale = pwmRequest.getLocale();
        final PwNotifyService pwNotifyService = pwmRequest.getPwmDomain().getPwNotifyService();
        final PwNotifyStoredJobState pwNotifyStoredJobState = pwNotifyService.getJobState();
        final boolean canRunOnthisServer = pwNotifyService.canRunOnThisServer();

        statusData.add( DisplayElement.create( String.valueOf( key++ ), DisplayElement.Type.string,
                "Currently Processing (on this server)", LocaleHelper.booleanString( pwNotifyService.isRunning(), locale, config ) ) );

        statusData.add( DisplayElement.create( String.valueOf( key++ ), DisplayElement.Type.string,
                "This Server is the Job Processor", LocaleHelper.booleanString( canRunOnthisServer, locale, config ) ) );

        if ( canRunOnthisServer )
        {
            statusData.add( DisplayElement.create( String.valueOf( key++ ), DisplayElement.Type.timestamp,
                    "Next Job Scheduled Time", LocaleHelper.instantString( pwNotifyService.getNextExecutionTime(), locale, config ) ) );
        }

        if ( pwNotifyStoredJobState != null )
        {
            statusData.add( DisplayElement.create( String.valueOf( key++ ), DisplayElement.Type.timestamp,
                    "Last Job Start Time", LocaleHelper.instantString( pwNotifyStoredJobState.lastStart(), locale, config ) ) );

            statusData.add( DisplayElement.create( String.valueOf( key++ ), DisplayElement.Type.timestamp,
                    "Last Job Completion Time", LocaleHelper.instantString( pwNotifyStoredJobState.lastCompletion(), locale, config ) ) );

            if ( pwNotifyStoredJobState.lastStart() != null && pwNotifyStoredJobState.lastCompletion() != null )
            {
                final TimeDuration lastJobDuration = TimeDuration.between( pwNotifyStoredJobState.lastStart(), pwNotifyStoredJobState.lastCompletion() );
                statusData.add( DisplayElement.create( String.valueOf( key++ ), DisplayElement.Type.timestamp,
                        "Last Job Duration", PwmTimeUtil.asLongString( lastJobDuration, locale ) ) );
            }

            if ( StringUtil.notEmpty( pwNotifyStoredJobState.serverInstance() ) )
            {
                statusData.add( DisplayElement.create( String.valueOf( key++ ), DisplayElement.Type.string,
                        "Last Job Server Instance", pwNotifyStoredJobState.serverInstance() ) );
            }

            if ( pwNotifyStoredJobState.lastError() != null )
            {
                statusData.add( DisplayElement.create( String.valueOf( key++ ), DisplayElement.Type.string,
                        "Last Job Error",  pwNotifyStoredJobState.lastError().toDebugStr() ) );
            }
        }

        final boolean startButtonEnabled = !pwNotifyService.isRunning() && canRunOnthisServer;
        final PwNotifyStatusBean pwNotifyStatusBean = new PwNotifyStatusBean( statusData, startButtonEnabled );
        pwmRequest.outputJsonResult( RestResultBean.withData( pwNotifyStatusBean, PwNotifyStatusBean.class ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "readPwNotifyLog" )
    public ProcessStatus restreadPwNotifyLog( final PwmRequest pwmRequest )
            throws IOException
    {
        final PwNotifyService pwNotifyService = pwmRequest.getPwmDomain().getPwNotifyService();

        pwmRequest.outputJsonResult( RestResultBean.withData( pwNotifyService.debugLog(), String.class ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "startPwNotifyJob" )
    public ProcessStatus restStartPwNotifyJob( final PwmRequest pwmRequest ) throws IOException
    {
        pwmRequest.getPwmDomain().getPwNotifyService().executeJob();
        pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
        return ProcessStatus.Halt;
    }

    public enum LogDisplayType
    {
        grid,
        lines,
    }

    @ActionHandler( action = "readLogData" )
    public ProcessStatus readLogData( final PwmRequest pwmRequest ) throws IOException, PwmUnrecoverableException
    {
        final LocalDBLogger localDBLogger = pwmRequest.getPwmApplication().getLocalDBLogger();

        final LogDisplayType logDisplayType;
        final LocalDBSearchQuery searchParameters;
        {
            final Map<String, String> inputMap = pwmRequest.readBodyAsJsonStringMap( PwmHttpRequestWrapper.Flag.BypassValidation );
            final int eventCount = Integer.parseInt( inputMap.getOrDefault( "count", "0" ) );
            final TimeDuration maxTimeSeconds = TimeDuration.of( Integer.parseInt( inputMap.getOrDefault( "maxTime", "5" ) ), TimeDuration.Unit.SECONDS );
            final String username = inputMap.getOrDefault( "username", "" );
            final String text = inputMap.getOrDefault( "text", "" );
            final PwmLogLevel logLevel = EnumUtil.readEnumFromString( PwmLogLevel.class, inputMap.get( "level" ) ).orElse( PwmLogLevel.TRACE );
            final LocalDBLogger.EventType logType = EnumUtil.readEnumFromString( LocalDBLogger.EventType.class, inputMap.get( "type" ) )
                    .orElse( LocalDBLogger.EventType.Both );
            logDisplayType = EnumUtil.readEnumFromString( LogDisplayType.class, inputMap.get( "displayType" ) ).orElse( LogDisplayType.grid );

            searchParameters = LocalDBSearchQuery.builder()
                    .minimumLevel( logLevel )
                    .maxEvents( eventCount )
                    .username( username )
                    .text( text )
                    .maxQueryTime( maxTimeSeconds )
                    .eventType( logType )
                    .build();
        }

        final LocalDBSearchResults searchResults = localDBLogger.readStoredEvents( searchParameters );

        final LinkedHashMap<String, Object> returnData = new LinkedHashMap<>(  );
        if ( logDisplayType == LogDisplayType.grid )
        {
            final ArrayList<PwmLogEvent> pwmLogEvents = new ArrayList<>();
            while ( searchResults.hasNext() )
            {
                pwmLogEvents.add( searchResults.next() );
            }
            returnData.put( "records", pwmLogEvents );
        }
        else if ( logDisplayType == LogDisplayType.lines )
        {
            final ArrayList<String> pwmLogEvents = new ArrayList<>();
            while ( searchResults.hasNext() )
            {
                pwmLogEvents.add( searchResults.next().toLogString() );
            }
            returnData.put( "records", pwmLogEvents );
        }
        returnData.put( "display", logDisplayType );
        returnData.put( "size", searchResults.getReturnedEvents() );
        returnData.put( "duration", searchResults.getSearchTime() );
        pwmRequest.outputJsonResult( RestResultBean.withData( returnData, Map.class ) );

        return ProcessStatus.Halt;
    }

    public enum LogDownloadType
    {
        plain,
        csv,
    }

    public enum LogDownloadCompression
    {
        none,
        zip,
        gzip,
    }

    @ActionHandler( action = "downloadLogData" )
    public ProcessStatus downloadLogData( final PwmRequest pwmRequest ) throws IOException, PwmUnrecoverableException
    {
        final LocalDBLogger localDBLogger = pwmRequest.getPwmApplication().getLocalDBLogger();

        final LogDownloadType logDownloadType = EnumUtil.readEnumFromString( LogDownloadType.class, pwmRequest.readParameterAsString( "downloadType" ) )
                .orElse( LogDownloadType.plain );

        final LocalDBSearchQuery searchParameters = LocalDBSearchQuery.builder()
                .minimumLevel( PwmLogLevel.TRACE )
                .eventType( LocalDBLogger.EventType.Both )
                .build();

        final LocalDBSearchResults searchResults = localDBLogger.readStoredEvents( searchParameters );

        final String compressFileNameSuffix;
        final HttpContentType compressedContentType;
        final Writer writer;
        {
            final LogDownloadCompression logDownloadCompression = EnumUtil.readEnumFromString(
                            LogDownloadCompression.class,
                            pwmRequest.readParameterAsString( "compressionType" ) )
                    .orElse( SystemAdminServlet.LogDownloadCompression.none );

            final OutputStream compressedStream;

            switch ( logDownloadCompression )
            {
                case zip:
                    final ZipOutputStream zipOutputStream = new ZipOutputStream( pwmRequest.getPwmResponse().getOutputStream() );
                    zipOutputStream.putNextEntry( new ZipEntry( "debug.txt"  ) );
                    compressedStream = zipOutputStream;
                    compressFileNameSuffix = ".zip";
                    compressedContentType = HttpContentType.zip;
                    break;

                case gzip:
                    compressedStream = new GZIPOutputStream( pwmRequest.getPwmResponse().getOutputStream() );
                    compressFileNameSuffix = ".gz";
                    compressedContentType = HttpContentType.gzip;
                    break;

                default:
                    compressedStream = pwmRequest.getPwmResponse().getOutputStream();
                    compressFileNameSuffix = "";
                    compressedContentType = null;
            }
            writer = new OutputStreamWriter( compressedStream, PwmConstants.DEFAULT_CHARSET );
        }

        switch ( logDownloadType )
        {
            case plain:
            {
                while ( searchResults.hasNext() )
                {
                    writer.write( searchResults.next().toLogString( true ) );
                    writer.write( "\n" );
                    pwmRequest.getPwmResponse().markAsDownload(
                            compressedContentType != null ? compressedContentType : HttpContentType.plain,
                            "debug.txt" + compressFileNameSuffix );
                }
            }
            break;

            case csv:
            {
                pwmRequest.getPwmResponse().markAsDownload(
                        compressedContentType != null ? compressedContentType : HttpContentType.csv,
                        "debug.csv" + compressFileNameSuffix );
                while ( searchResults.hasNext() )
                {
                    writer.write( searchResults.next().toCsvLine( ) );
                    writer.write( "\n" );
                }
            }
            break;

            default:
                PwmUtil.unhandledSwitchStatement( logDownloadType );

        }

        writer.close();

        return ProcessStatus.Halt;
    }

    public record PwNotifyStatusBean(
            List<DisplayElement> statusData,
            boolean enableStartButton
    )
    {
        public PwNotifyStatusBean(
                final List<DisplayElement> statusData,
                final boolean enableStartButton
        )
        {
            this.statusData = CollectionUtil.stripNulls( statusData );
            this.enableStartButton = enableStartButton;
        }
    }
}
