/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.http.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.svc.report.ReportService;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

@WebServlet(
        name = "AdminServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/admin",
                PwmConstants.URL_PREFIX_PRIVATE + "/admin/*",
                PwmConstants.URL_PREFIX_PRIVATE + "/admin/Administration",
        }
)
public class AdminServlet extends AbstractPwmServlet {

    private static final PwmLogger LOGGER = PwmLogger.forClass(AdminServlet.class);

    public enum AdminAction implements AbstractPwmServlet.ProcessAction {
        viewLogWindow(HttpMethod.GET),
        downloadAuditLogCsv(HttpMethod.POST),
        downloadUserReportCsv(HttpMethod.POST),
        downloadUserSummaryCsv(HttpMethod.POST),
        downloadStatisticsLogCsv(HttpMethod.POST),
        ;

        private final Collection<HttpMethod> method;

        AdminAction(HttpMethod... method)
        {
            this.method = Collections.unmodifiableList(Arrays.asList(method));
        }

        public Collection<HttpMethod> permittedMethods()
        {
            return method;
        }
    }

    protected AdminAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        try {
            return AdminAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
        if (!pwmRequest.isAuthenticated()) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_AUTHENTICATION_REQUIRED);
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        if (!pwmRequest.getPwmSession().getSessionManager().checkPermission(pwmRequest.getPwmApplication(), Permission.PWMADMIN))  {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED);
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        final AdminAction action = readProcessAction(pwmRequest);
        if (action != null) {
            switch(action) {
                case viewLogWindow:
                    processViewLog(pwmRequest);
                    return;

                case downloadAuditLogCsv:
                    downloadAuditLogCsv(pwmRequest);
                    return;

                case downloadUserReportCsv:
                    downloadUserReportCsv(pwmRequest);
                    return;

                case downloadUserSummaryCsv:
                    downloadUserSummaryCsv(pwmRequest);
                    return;

                case downloadStatisticsLogCsv:
                    downloadStatisticsLogCsv(pwmRequest);
                    return;
            }
        }

        forwardToJsp(pwmRequest);
    }

    private void processViewLog(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.ADMIN_LOGVIEW_WINDOW);
    }

    private void downloadAuditLogCsv(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        pwmRequest.getPwmResponse().markAsDownload(PwmConstants.ContentTypeValue.csv, PwmConstants.DOWNLOAD_FILENAME_AUDIT_RECORDS_CSV);

        final OutputStream outputStream = pwmRequest.getPwmResponse().getOutputStream();
        try {
            pwmApplication.getAuditManager().outputVaultToCsv(outputStream, pwmRequest.getLocale(), true);
        } catch (Exception e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,e.getMessage());
            pwmRequest.respondWithError(errorInformation);
        } finally {
            outputStream.close();
        }
    }

    private void downloadUserReportCsv(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        pwmRequest.getPwmResponse().markAsDownload(PwmConstants.ContentTypeValue.csv, PwmConstants.DOWNLOAD_FILENAME_USER_REPORT_RECORDS_CSV);

        final OutputStream outputStream = pwmRequest.getPwmResponse().getOutputStream();
        try {
            final ReportService userReport = pwmApplication.getReportService();
            userReport.outputToCsv(outputStream, true, pwmRequest.getLocale());
        } catch (Exception e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,e.getMessage());
            pwmRequest.respondWithError(errorInformation);
        } finally {
            outputStream.close();
        }
    }

    private void downloadUserSummaryCsv(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        pwmRequest.getPwmResponse().markAsDownload(PwmConstants.ContentTypeValue.csv, PwmConstants.DOWNLOAD_FILENAME_USER_REPORT_SUMMARY_CSV);

        final OutputStream outputStream = pwmRequest.getPwmResponse().getOutputStream();
        try {
            final ReportService userReport = pwmApplication.getReportService();
            userReport.outputSummaryToCsv(outputStream, pwmRequest.getLocale());
        } catch (Exception e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,e.getMessage());
            pwmRequest.respondWithError(errorInformation);
        } finally {
            outputStream.close();
        }
    }

    private void downloadStatisticsLogCsv(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        pwmRequest.getPwmResponse().markAsDownload(PwmConstants.ContentTypeValue.csv, PwmConstants.DOWNLOAD_FILENAME_STATISTICS_CSV);

        final OutputStream outputStream = pwmRequest.getPwmResponse().getOutputStream();
        try {
            final StatisticsManager statsManager = pwmApplication.getStatisticsManager();
            statsManager.outputStatsToCsv(outputStream, pwmRequest.getLocale(), true);
        } catch (Exception e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,e.getMessage());
            pwmRequest.respondWithError(errorInformation);
        } finally {
            outputStream.close();
        }
    }

    public void forwardToJsp(final PwmRequest pwmRequest) throws ServletException, PwmUnrecoverableException, IOException {
        final Page currentPage = Page.forUrl(pwmRequest.getURL());
        if (currentPage != null) {
            pwmRequest.forwardToJsp(currentPage.getJspURL());
            return;
        }
        pwmRequest.sendRedirect(pwmRequest.getContextPath() + PwmServletDefinition.Admin.servletUrl() + Page.dashboard.getUrlSuffix());
    }


    public enum Page {
        dashboard(PwmConstants.JSP_URL.ADMIN_DASHBOARD,"/dashboard"),
        analysis(PwmConstants.JSP_URL.ADMIN_ANALYSIS,"/analysis"),
        activity(PwmConstants.JSP_URL.ADMIN_ACTIVITY,"/activity"),
        tokenLookup(PwmConstants.JSP_URL.ADMIN_TOKEN_LOOKUP,"/tokens"),
        viewLog(PwmConstants.JSP_URL.ADMIN_LOGVIEW,"/logs"),
        urlReference(PwmConstants.JSP_URL.ADMIN_URLREFERENCE,"/urls"),

        ;

        private final PwmConstants.JSP_URL jspURL;
        private final String urlSuffix;

        Page(PwmConstants.JSP_URL jspURL, String urlSuffix) {
            this.jspURL = jspURL;
            this.urlSuffix = urlSuffix;
        }

        public PwmConstants.JSP_URL getJspURL() {
            return jspURL;
        }

        public String getUrlSuffix() {
            return urlSuffix;
        }

        public static Page forUrl(final PwmURL pwmURL) {
            final String url = pwmURL.toString();
            for (final Page page : Page.values()) {
                if (url.endsWith(page.urlSuffix)) {
                    return page;
                }
            }
            return null;
        }
    }
}
