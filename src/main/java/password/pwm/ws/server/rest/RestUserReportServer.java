/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

package password.pwm.ws.server.rest;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.Permission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.report.ReportService;
import password.pwm.svc.report.UserCacheRecord;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;
import password.pwm.ws.server.ServicePermissions;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;

@Path("/report")
public class RestUserReportServer extends AbstractRestServer {
    @Context
    HttpServletRequest request;

    @GET
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public Response doReportDetailData(
            @QueryParam("maximum") final int maximum
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, LocalDBException
    {
        final int max = (maximum > 0)
                ? maximum
                : 10 * 1000;

        final RestRequestBean restRequestBean;
        try {
            final ServicePermissions servicePermissions = ServicePermissions.ADMIN_LOCAL_OR_EXTERNAL;
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, servicePermissions, null);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        if (!restRequestBean.getPwmSession().getSessionManager().checkPermission(restRequestBean.getPwmApplication(), Permission.PWMADMIN)) {
            final ErrorInformation errorInformation = PwmError.ERROR_UNAUTHORIZED.toInfo();
            return RestResultBean.fromError(errorInformation, restRequestBean).asJsonResponse();
        }

        final ReportService reportService = restRequestBean.getPwmApplication().getReportService();
        //if (reportService.getReportStatusInfo().getCurrentProcess() != ReportStatusInfo.ReportEngineProcess.None) {
        //    final String errorMsg = "report data not available, engine is busy.  Try again later.  status=" + reportService.getReportStatusInfo().getCurrentProcess();
        //    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg);
        //    return RestResultBean.fromError(errorInformation, restRequestBean).asJsonResponse();
        //}

        final ArrayList<UserCacheRecord> reportData = new ArrayList<>();
        ClosableIterator<UserCacheRecord> cacheBeanIterator = null;
        try {
            cacheBeanIterator = reportService.iterator();
            while (cacheBeanIterator.hasNext() && reportData.size() < max) {
                final UserCacheRecord userCacheRecord = cacheBeanIterator.next();
                if (userCacheRecord != null) {
                    reportData.add(userCacheRecord);
                }
            }
        } finally {
            if (cacheBeanIterator != null) {
                cacheBeanIterator.close();
            }
        }

        final HashMap<String,Object> returnData = new HashMap<>();
        returnData.put("users",reportData);

        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(returnData);
        return restResultBean.asJsonResponse();
    }
}
