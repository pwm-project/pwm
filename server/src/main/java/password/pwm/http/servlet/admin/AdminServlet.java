/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.http.servlet.admin;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.pub.SessionStateInfoBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmURL;
import password.pwm.http.bean.AdminBean;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.i18n.Message;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.svc.event.AuditRecord;
import password.pwm.svc.event.HelpdeskAuditRecord;
import password.pwm.svc.event.SystemAuditRecord;
import password.pwm.svc.event.UserAuditRecord;
import password.pwm.svc.intruder.RecordType;
import password.pwm.svc.report.ReportColumnFilter;
import password.pwm.svc.report.ReportCsvUtility;
import password.pwm.svc.report.ReportService;
import password.pwm.svc.report.UserCacheRecord;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.reports.ReportUtils;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.RestStatisticsServer;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;

@WebServlet(
        name = "AdminServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/admin",
                PwmConstants.URL_PREFIX_PRIVATE + "/admin/*",
                PwmConstants.URL_PREFIX_PRIVATE + "/admin/Administration",
        }
)
public class AdminServlet extends ControlledPwmServlet
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( AdminServlet.class );

    public enum AdminAction implements AbstractPwmServlet.ProcessAction
    {
        viewLogWindow( HttpMethod.GET ),
        downloadAuditLogCsv( HttpMethod.POST ),
        downloadUserReportCsv( HttpMethod.POST ),
        downloadUserSummaryCsv( HttpMethod.POST ),
        downloadStatisticsLogCsv( HttpMethod.POST ),
        clearIntruderTable( HttpMethod.POST ),
        reportCommand( HttpMethod.POST ),
        reportStatus( HttpMethod.GET ),
        reportSummary( HttpMethod.GET ),
        reportData( HttpMethod.GET ),
        downloadUserDebug( HttpMethod.GET ),
        auditData( HttpMethod.GET ),
        sessionData( HttpMethod.GET ),
        intruderData( HttpMethod.GET ),
        statistics( HttpMethod.GET ),;

        private final Collection<HttpMethod> method;

        AdminAction( final HttpMethod... method )
        {
            this.method = Collections.unmodifiableList( Arrays.asList( method ) );
        }

        public Collection<HttpMethod> permittedMethods( )
        {
            return method;
        }
    }

    @Override
    public Class<? extends ProcessAction> getProcessActionsClass( )
    {
        return AdminAction.class;
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        forwardToJsp( pwmRequest );
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        if ( !pwmRequest.isAuthenticated() )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_AUTHENTICATION_REQUIRED );
            pwmRequest.respondWithError( errorInformation );
            return ProcessStatus.Halt;
        }

        if ( !pwmRequest.getPwmSession().getSessionManager().checkPermission( pwmRequest.getPwmApplication(), Permission.PWMADMIN ) )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNAUTHORIZED );
            pwmRequest.respondWithError( errorInformation );
            return ProcessStatus.Halt;
        }
        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "viewLogWindow" )
    private ProcessStatus processViewLogWindow(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        pwmRequest.forwardToJsp( JspUrl.ADMIN_LOGVIEW_WINDOW );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "downloadAuditLogCsv" )
    private ProcessStatus downloadAuditLogCsv(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        pwmRequest.getPwmResponse().markAsDownload(
                HttpContentType.csv,
                pwmApplication.getConfig().readAppProperty( AppProperty.DOWNLOAD_FILENAME_AUDIT_RECORDS_CSV )
        );

        final OutputStream outputStream = pwmRequest.getPwmResponse().getOutputStream();
        try
        {
            pwmApplication.getAuditManager().outputVaultToCsv( outputStream, pwmRequest.getLocale(), true );
        }
        catch ( Exception e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, e.getMessage() );
            pwmRequest.respondWithError( errorInformation );
        }
        finally
        {
            outputStream.close();
        }
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "downloadUserReportCsv" )
    private ProcessStatus downloadUserReportCsv(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        pwmRequest.getPwmResponse().markAsDownload(
                HttpContentType.csv,
                pwmApplication.getConfig().readAppProperty( AppProperty.DOWNLOAD_FILENAME_USER_REPORT_RECORDS_CSV )
        );

        final OutputStream outputStream = pwmRequest.getPwmResponse().getOutputStream();
        try
        {
            final String selectedColumns = pwmRequest.readParameterAsString( "selectedColumns", "" );

            final ReportColumnFilter columnFilter = ReportUtils.toReportColumnFilter( selectedColumns );
            final ReportCsvUtility reportCsvUtility = new ReportCsvUtility( pwmApplication );
            reportCsvUtility.outputToCsv( outputStream, true, pwmRequest.getLocale(), columnFilter );
        }
        catch ( Exception e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, e.getMessage() );
            pwmRequest.respondWithError( errorInformation );
        }
        finally
        {
            outputStream.close();
        }
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "downloadUserSummaryCsv" )
    private ProcessStatus downloadUserSummaryCsv(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        pwmRequest.getPwmResponse().markAsDownload(
                HttpContentType.csv,
                pwmApplication.getConfig().readAppProperty( AppProperty.DOWNLOAD_FILENAME_USER_REPORT_SUMMARY_CSV )
        );

        final OutputStream outputStream = pwmRequest.getPwmResponse().getOutputStream();
        try
        {
            final ReportCsvUtility reportCsvUtility = new ReportCsvUtility( pwmApplication );
            reportCsvUtility.outputSummaryToCsv( outputStream, pwmRequest.getLocale() );
        }
        catch ( Exception e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, e.getMessage() );
            pwmRequest.respondWithError( errorInformation );
        }
        finally
        {
            outputStream.close();
        }
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "downloadStatisticsLogCsv" )
    private ProcessStatus downloadStatisticsLogCsv( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        pwmRequest.getPwmResponse().markAsDownload(
                HttpContentType.csv,
                pwmRequest.getPwmApplication().getConfig().readAppProperty( AppProperty.DOWNLOAD_FILENAME_STATISTICS_CSV )
        );

        final OutputStream outputStream = pwmRequest.getPwmResponse().getOutputStream();
        try
        {
            final StatisticsManager statsManager = pwmApplication.getStatisticsManager();
            statsManager.outputStatsToCsv( outputStream, pwmRequest.getLocale(), true );
        }
        catch ( Exception e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, e.getMessage() );
            pwmRequest.respondWithError( errorInformation );
        }
        finally
        {
            outputStream.close();
        }
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "clearIntruderTable" )
    private ProcessStatus processClearIntruderTable(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        if ( !pwmRequest.getPwmSession().getSessionManager().checkPermission( pwmRequest.getPwmApplication(), Permission.PWMADMIN ) )
        {
            LOGGER.info( pwmRequest, "unable to execute clear intruder records" );
            return ProcessStatus.Halt;
        }

        //pwmApplication.getIntruderManager().clear();

        final RestResultBean restResultBean = RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "reportCommand" )
    private ProcessStatus processReportCommand( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException
    {
        final ReportService.ReportCommand reportCommand = JavaHelper.readEnumFromString(
                ReportService.ReportCommand.class,
                null,
                pwmRequest.readParameterAsString( "command" )
        );

        LOGGER.trace( pwmRequest, "issuing command '" + reportCommand + "' to report engine" );
        pwmRequest.getPwmApplication().getReportService().executeCommand( reportCommand );

        final RestResultBean restResultBean = RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "reportStatus" )
    private ProcessStatus processReportStatus( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException
    {
        try
        {
            final ReportStatusBean returnMap = ReportStatusBean.makeReportStatusData(
                    pwmRequest.getPwmApplication().getReportService(),
                    pwmRequest.getPwmSession().getSessionStateBean().getLocale()
            );
            final RestResultBean restResultBean = RestResultBean.withData( returnMap );
            pwmRequest.outputJsonResult( restResultBean );
        }
        catch ( LocalDBException e )
        {
            throw new PwmUnrecoverableException( e.getErrorInformation() );
        }
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "reportSummary" )
    private ProcessStatus processReportSummary( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final LinkedHashMap<String, Object> returnMap = new LinkedHashMap<>();
        returnMap.put( "raw", pwmApplication.getReportService().getSummaryData() );
        returnMap.put( "presentable", pwmApplication.getReportService().getSummaryData().asPresentableCollection(
                pwmApplication.getConfig(),
                pwmRequest.getPwmSession().getSessionStateBean().getLocale()
        ) );

        final RestResultBean restResultBean = RestResultBean.withData( returnMap );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "reportData" )
    public ProcessStatus processReportData( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException
    {
        final int maximum = Math.min( pwmRequest.readParameterAsInt( "maximum", 1000 ), 10 * 1000 );

        final ReportService reportService = pwmRequest.getPwmApplication().getReportService();
        final ArrayList<UserCacheRecord> reportData = new ArrayList<>();
        ClosableIterator<UserCacheRecord> cacheBeanIterator = null;
        try
        {
            cacheBeanIterator = reportService.iterator();
            while ( cacheBeanIterator.hasNext() && reportData.size() < maximum )
            {
                final UserCacheRecord userCacheRecord = cacheBeanIterator.next();
                if ( userCacheRecord != null )
                {
                    reportData.add( userCacheRecord );
                }
            }
        }
        finally
        {
            if ( cacheBeanIterator != null )
            {
                cacheBeanIterator.close();
            }
        }

        final HashMap<String, Object> returnData = new HashMap<>();
        returnData.put( "users", reportData );

        final RestResultBean restResultBean = RestResultBean.withData( returnData );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "downloadUserDebug" )
    private ProcessStatus processDownloadUserDebug( final PwmRequest pwmRequest )

            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final AdminBean adminBean = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, AdminBean.class );
        final UserIdentity userIdentity = adminBean.getLastUserDebug();
        if ( userIdentity != null )
        {
            pwmRequest.getPwmResponse().markAsDownload(
                    HttpContentType.json,
                    pwmApplication.getConfig().readAppProperty( AppProperty.DOWNLOAD_FILENAME_USER_DEBUG_JSON )
            );
            final UserDebugDataBean userDebugData = UserDebugDataReader.readUserDebugData(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getLocale(),
                    pwmRequest.getSessionLabel(),
                    userIdentity
            );
            final String output = JsonUtil.serialize( userDebugData, JsonUtil.Flag.PrettyPrint );
            pwmRequest.getPwmResponse().getOutputStream().write( output.getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
        else
        {
            pwmRequest.respondWithError( new ErrorInformation( PwmError.ERROR_UNKNOWN, "no previously searched user available for download" ) );
        }

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "auditData" )
    private ProcessStatus restAuditDataHandler( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException
    {
        final int max = readMaxParameter( pwmRequest, 1000, 10 * 1000 );
        final ArrayList<UserAuditRecord> userRecords = new ArrayList<>();
        final ArrayList<HelpdeskAuditRecord> helpdeskRecords = new ArrayList<>();
        final ArrayList<SystemAuditRecord> systemRecords = new ArrayList<>();
        final Iterator<AuditRecord> iterator = pwmRequest.getPwmApplication().getAuditManager().readVault();
        int counter = 0;
        while ( iterator.hasNext() && counter <= max )
        {
            final AuditRecord loopRecord = iterator.next();
            counter++;
            if ( loopRecord instanceof SystemAuditRecord )
            {
                systemRecords.add( ( SystemAuditRecord ) loopRecord );
            }
            else if ( loopRecord instanceof HelpdeskAuditRecord )
            {
                helpdeskRecords.add( ( HelpdeskAuditRecord ) loopRecord );
            }
            else if ( loopRecord instanceof UserAuditRecord )
            {
                userRecords.add( ( UserAuditRecord ) loopRecord );
            }
        }
        final HashMap<String, List> outputMap = new HashMap<>();
        outputMap.put( "user", userRecords );
        outputMap.put( "helpdesk", helpdeskRecords );
        outputMap.put( "system", systemRecords );

        final RestResultBean restResultBean = RestResultBean.withData( outputMap );
        LOGGER.debug( pwmRequest.getPwmSession(), "output " + counter + " audit records." );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "sessionData" )
    private ProcessStatus restSessionDataHandler( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException
    {
        final int max = readMaxParameter( pwmRequest, 1000, 10 * 1000 );

        final ArrayList<SessionStateInfoBean> gridData = new ArrayList<>();
        int counter = 0;
        final Iterator<SessionStateInfoBean> infos = pwmRequest.getPwmApplication().getSessionTrackService().getSessionInfoIterator();
        while ( counter < max && infos.hasNext() )
        {
            gridData.add( infos.next() );
            counter++;
        }
        final RestResultBean restResultBean = RestResultBean.withData( gridData );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "intruderData" )
    private ProcessStatus restIntruderDataHandler( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException
    {
        final int max = readMaxParameter( pwmRequest, 1000, 10 * 1000 );

        final TreeMap<String, Object> returnData = new TreeMap<>();
        try
        {
            for ( final RecordType recordType : RecordType.values() )
            {
                returnData.put( recordType.toString(), pwmRequest.getPwmApplication().getIntruderManager().getRecords( recordType, max ) );
            }
        }
        catch ( PwmException e )
        {
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_UNKNOWN, e.getMessage() );
            LOGGER.debug( pwmRequest, errorInfo );
            pwmRequest.outputJsonResult( RestResultBean.fromError( errorInfo ) );
        }

        final RestResultBean restResultBean = RestResultBean.withData( returnData );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "statistics" )
    private ProcessStatus restStatisticsHandler( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException
    {
        final String statKey = pwmRequest.readParameterAsString( "statKey" );
        final String statName = pwmRequest.readParameterAsString( "statName" );
        final String days = pwmRequest.readParameterAsString( "days" );

        final StatisticsManager statisticsManager = pwmRequest.getPwmApplication().getStatisticsManager();
        final RestStatisticsServer.JsonOutput jsonOutput = new RestStatisticsServer.JsonOutput();
        jsonOutput.EPS = RestStatisticsServer.addEpsStats( statisticsManager );

        if ( statName != null && statName.length() > 0 )
        {
            jsonOutput.nameData = RestStatisticsServer.doNameStat( statisticsManager, statName, days );
        }
        else
        {
            jsonOutput.keyData = RestStatisticsServer.doKeyStat( statisticsManager, statKey );
        }

        final RestResultBean restResultBean = RestResultBean.withData( jsonOutput );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    private void processDebugUserSearch( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final String username = pwmRequest.readParameterAsString( "username", PwmHttpRequestWrapper.Flag.BypassValidation );
        if ( StringUtil.isEmpty( username ) )
        {
            return;
        }

        final UserSearchEngine userSearchEngine = pwmRequest.getPwmApplication().getUserSearchEngine();
        final UserIdentity userIdentity;
        try
        {
            userIdentity = userSearchEngine.resolveUsername( username, null, null, pwmRequest.getSessionLabel() );
            final AdminBean adminBean = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, AdminBean.class );
            adminBean.setLastUserDebug( userIdentity );
        }
        catch ( PwmUnrecoverableException e )
        {
            setLastError( pwmRequest, e.getErrorInformation() );
            return;
        }
        catch ( PwmOperationalException e )
        {
            setLastError( pwmRequest, e.getErrorInformation() );
            return;
        }

        final UserDebugDataBean userDebugData = UserDebugDataReader.readUserDebugData(
                pwmRequest.getPwmApplication(),
                pwmRequest.getLocale(),
                pwmRequest.getSessionLabel(),
                userIdentity
        );
        pwmRequest.setAttribute( PwmRequestAttribute.UserDebugData, userDebugData );
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
        final Page currentPage = Page.forUrl( pwmRequest.getURL() );
        if ( currentPage == Page.debugUser )
        {
            processDebugUserSearch( pwmRequest );
        }

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
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getContextManager(),
                    pwmRequest.getLocale(),
                    flags.toArray( new AppDashboardData.Flag[ flags.size() ] )
            );
            pwmRequest.setAttribute( PwmRequestAttribute.AppDashboardData, appDashboardData );
        }

        if ( currentPage != null )
        {
            pwmRequest.forwardToJsp( currentPage.getJspURL() );
            return;
        }
        pwmRequest.sendRedirect( pwmRequest.getContextPath() + PwmServletDefinition.Admin.servletUrl() + Page.dashboard.getUrlSuffix() );
    }

    private static int readMaxParameter( final PwmRequest pwmRequest, final int defaultValue, final int maxValue )
            throws PwmUnrecoverableException
    {
        final String stringMax = pwmRequest.readParameterAsString( "maximum", String.valueOf( defaultValue ) );
        return Math.max( Integer.parseInt( stringMax ), maxValue );
    }

    public enum Page
    {
        dashboard( JspUrl.ADMIN_DASHBOARD, "/dashboard" ),
        analysis( JspUrl.ADMIN_ANALYSIS, "/analysis" ),
        activity( JspUrl.ADMIN_ACTIVITY, "/activity" ),
        tokenLookup( JspUrl.ADMIN_TOKEN_LOOKUP, "/tokens" ),
        viewLog( JspUrl.ADMIN_LOGVIEW, "/logs" ),
        urlReference( JspUrl.ADMIN_URLREFERENCE, "/urls" ),
        debugUser( JspUrl.ADMIN_DEBUG, "/userdebug" ),
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

        public static Page forUrl( final PwmURL pwmURL )
        {
            final String url = pwmURL.toString();
            for ( final Page page : Page.values() )
            {
                if ( url.endsWith( page.urlSuffix ) )
                {
                    return page;
                }
            }
            return null;
        }
    }


}
