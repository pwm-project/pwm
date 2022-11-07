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
import password.pwm.PwmConstants;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;



/**
 * Simple servlet to front requests to the otherwise standard index page at '/private/admin/index.jsp'.
 */
@WebServlet(
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/admin",
                PwmConstants.URL_PREFIX_PRIVATE + "/admin/",
                PwmConstants.URL_PREFIX_PRIVATE + "/administration",
                PwmConstants.URL_PREFIX_PRIVATE + "/administration/",
        }
)
public class AdminMenuServlet extends ControlledPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( AdminMenuServlet.class );

    @Override
    protected PwmLogger getLogger()
    {
        return LOGGER;
    }

    public enum AdminAction implements AbstractPwmServlet.ProcessAction
    {
        viewLogWindow( HttpMethod.GET ),
        downloadAuditLogCsv( HttpMethod.POST ),
        downloadUserReportCsv( HttpMethod.POST ),
        downloadUserSummaryCsv( HttpMethod.POST ),
        downloadStatisticsLogCsv( HttpMethod.POST ),
        downloadSessionsCsv( HttpMethod.POST ),
        clearIntruderTable( HttpMethod.POST ),
        reportCommand( HttpMethod.POST ),
        reportStatus( HttpMethod.GET ),
        reportSummary( HttpMethod.GET ),
        reportData( HttpMethod.GET ),
        downloadUserDebug( HttpMethod.GET ),
        auditData( HttpMethod.GET ),
        sessionData( HttpMethod.GET ),
        intruderData( HttpMethod.GET ),
        startPwNotifyJob( HttpMethod.POST ),
        readPwNotifyStatus( HttpMethod.POST ),
        readPwNotifyLog( HttpMethod.POST ),
        readLogData( HttpMethod.POST ),
        downloadLogData( HttpMethod.POST ),;

        private final Collection<HttpMethod> method;

        AdminAction( final HttpMethod... method )
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
    public Optional<Class<? extends ProcessAction>> getProcessActionsClass()
    {
        return Optional.empty();
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        pwmRequest.getHttpServletRequest().getServletContext()
                .getRequestDispatcher( PwmConstants.URL_PREFIX_PRIVATE + "/admin/index.jsp" )
                .forward( pwmRequest.getHttpServletRequest(), pwmRequest.getPwmResponse().getHttpServletResponse() );
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        return SystemAdminServlet.preProcessAdminCheck( pwmRequest );
    }
}
