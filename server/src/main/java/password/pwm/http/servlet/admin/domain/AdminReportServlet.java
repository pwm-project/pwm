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

package password.pwm.http.servlet.admin.domain;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmConstants;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.http.servlet.admin.SystemAdminServlet;
import password.pwm.i18n.Message;
import password.pwm.svc.report.ReportProcess;
import password.pwm.svc.report.ReportProcessRequest;
import password.pwm.svc.report.ReportProcessStatus;
import password.pwm.svc.report.ReportService;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@WebServlet(
        name = "AdminReportServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/admin/report",
                PwmConstants.URL_PREFIX_PRIVATE + "/admin/report/*",
        }
)
public class AdminReportServlet extends ControlledPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( AdminReportServlet.class );

    @Override
    protected PwmLogger getLogger()
    {
        return LOGGER;
    }

    enum AdminReportAction implements AbstractPwmServlet.ProcessAction
    {
        reportProcessStatus( HttpMethod.POST ),
        cancelDownload( HttpMethod.POST ),
        downloadReportZip( HttpMethod.GET ),;

        private final Collection<HttpMethod> method;

        AdminReportAction( final HttpMethod... method )
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
    protected PwmServletDefinition getServletDefinition()
    {
        return PwmServletDefinition.AdminReport;
    }

    @Override
    public Optional<Class<? extends ProcessAction>> getProcessActionsClass()
    {
        return Optional.of( AdminReportAction.class );
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        pwmRequest.forwardToJsp( JspUrl.ADMIN_REPORTING );
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        return SystemAdminServlet.preProcessAdminCheck( pwmRequest );
    }

    @ActionHandler( action = "reportProcessStatus" )
    public ProcessStatus processReportProcessStatus( final PwmRequest pwmRequest )
            throws IOException
    {
        final ReportProcessStatus reportProcessStatus = pwmRequest.getPwmSession().getReportProcess()
                .map( process -> process.getStatus( pwmRequest.getLocale() ) )
                .orElse( ReportProcessStatus.builder().build() );

        final RestResultBean<ReportProcessStatus> restResultBean = RestResultBean.withData( reportProcessStatus, ReportProcessStatus.class );

        pwmRequest.outputJsonResult( restResultBean );

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "cancelDownload" )
    public ProcessStatus processCancelDownload( final PwmRequest pwmRequest )
            throws IOException
    {
        pwmRequest.getPwmSession().getReportProcess().ifPresent( ReportProcess::close );
        pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "downloadReportZip" )
    public ProcessStatus processDownloadReportZip( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        pwmRequest.getPwmResponse().markAsDownload(
                HttpContentType.zip,
                "report.zip"
        );

        final ReportProcessRequest reportProcessRequest = ReportProcessRequest.builder()
                .domainID( pwmRequest.getDomainID() )
                .sessionLabel( pwmRequest.getLabel() )
                .locale( pwmRequest.getLocale() )
                .maximumRecords( pwmRequest.readParameterAsInt( "recordCount", 1000 ) )
                .reportType( pwmRequest.readParameterAsEnum( "recordType", ReportProcessRequest.ReportType.class )
                        .orElse( ReportProcessRequest.ReportType.json ) )
                .build();

        try ( OutputStream outputStream = pwmRequest.getPwmResponse().getOutputStream() )
        {
            final ReportService reportService = pwmRequest.getPwmDomain().getReportService();
            try ( ReportProcess reportProcess = reportService.createReportProcess( reportProcessRequest ) )
            {
                pwmRequest.getPwmSession().setReportProcess( reportProcess );
                reportProcess.startReport( outputStream );
            }
        }
        catch ( final PwmException e )
        {
            LOGGER.debug( pwmRequest, () -> "error during report download: " + e.getMessage() );
            pwmRequest.respondWithError( e.getErrorInformation() );
        }
        return ProcessStatus.Halt;
    }
}
