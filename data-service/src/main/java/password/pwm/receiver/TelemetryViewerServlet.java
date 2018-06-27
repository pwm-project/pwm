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
 *
 */

package password.pwm.receiver;

import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@WebServlet(
        name="TelemetryViewer",
        urlPatterns={
                "/viewer",
        }
)
public class TelemetryViewerServlet extends HttpServlet {
    private static final String PARAM_DAYS = "days";

    public static String SUMMARY_ATTR = "SummaryBean";

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException
    {
        final String daysString = req.getParameter( PARAM_DAYS );
        final int days = StringUtil.isEmpty( daysString ) ? 30 : Integer.parseInt( daysString );
        final ContextManager contextManager = ContextManager.getContextManager(req.getServletContext());
        final PwmReceiverApp app = contextManager.getApp();

        {
            final String errorState = app.getStatus().getErrorState();
            if (!StringUtil.isEmpty(errorState)) {
                resp.sendError(500, errorState);
                final String htmlBody = "<html>Error: " + errorState + "</html>";
                resp.getWriter().print(htmlBody);
                return;
            }
        }

        final Storage storage = app.getStorage();
        final SummaryBean summaryBean = SummaryBean.fromStorage(storage, new TimeDuration(days, TimeUnit.DAYS ) );
        req.setAttribute(SUMMARY_ATTR, summaryBean);
        req.getServletContext().getRequestDispatcher("/WEB-INF/jsp/telemetry-viewer.jsp").forward(req,resp);
    }
}
