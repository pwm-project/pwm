/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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
import password.pwm.util.ClosableIterator;
import password.pwm.util.TimeDuration;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.report.ReportService;
import password.pwm.util.report.UserCacheRecord;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;

@Path("/report")
public class RestUserReportServer {
    @Context
    HttpServletRequest request;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGetAppAuditData(
            @QueryParam("maximum") int maximum
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, LocalDBException
    {
        maximum = maximum > 0 ? maximum : 10 * 1000;

        final RestRequestBean restRequestBean;
        try {
            final ServicePermissions servicePermissions = ServicePermissions.ADMIN_LOCAL_OR_EXTERNAL;
            restRequestBean = RestServerHelper.initializeRestRequest(request, servicePermissions, null);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        if (!Permission.checkPermission(Permission.PWMADMIN, restRequestBean.getPwmSession(), restRequestBean.getPwmApplication())) {
            final ErrorInformation errorInformation = PwmError.ERROR_UNAUTHORIZED.toInfo();
            return RestResultBean.fromError(errorInformation, restRequestBean).asJsonResponse();
        }

        final ArrayList<UserCacheRecord> reportData = new ArrayList<UserCacheRecord>();
        ClosableIterator<UserCacheRecord> cacheBeanIterator = null;
        try {
            cacheBeanIterator = restRequestBean.getPwmApplication().getUserReportService().iterator();
            while (cacheBeanIterator.hasNext() && reportData.size() < maximum) {
                final UserCacheRecord userCacheRecord = cacheBeanIterator.next();
                if (userCacheRecord != null) {
                    reportData.add(userCacheRecord);
                }
            }
        } finally {
            if (cacheBeanIterator != null) {
                //cacheBeanIterator.close();
            }
        }

        final HashMap<String,Object> returnData = new HashMap<String, Object>();
        returnData.put("users",reportData);

        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(returnData);
        return restResultBean.asJsonResponse();
    }


    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGetReportEngineStatusData(
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, LocalDBException
    {
        final RestRequestBean restRequestBean;
        try {
            final ServicePermissions servicePermissions = ServicePermissions.ADMIN_LOCAL_OR_EXTERNAL;
            restRequestBean = RestServerHelper.initializeRestRequest(request, servicePermissions, null);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        if (!Permission.checkPermission(Permission.PWMADMIN, restRequestBean.getPwmSession(), restRequestBean.getPwmApplication())) {
            final ErrorInformation errorInformation = PwmError.ERROR_UNAUTHORIZED.toInfo();
            return RestResultBean.fromError(errorInformation, restRequestBean).asJsonResponse();
        }

        final LinkedHashMap<String,Object> returnMap = new LinkedHashMap(makeReportStatusData(
                restRequestBean.getPwmApplication().getUserReportService(),
                restRequestBean.getPwmSession().getSessionStateBean().getLocale()
        ));
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(returnMap);
        return restResultBean.asJsonResponse();
    }

    @GET
    @Path("/summary")
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGetReportSummaryData(
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, LocalDBException
    {
        final RestRequestBean restRequestBean;
        try {
            final ServicePermissions servicePermissions = ServicePermissions.ADMIN_LOCAL_OR_EXTERNAL;
            restRequestBean = RestServerHelper.initializeRestRequest(request, servicePermissions, null);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        if (!Permission.checkPermission(Permission.PWMADMIN, restRequestBean.getPwmSession(), restRequestBean.getPwmApplication())) {
            final ErrorInformation errorInformation = PwmError.ERROR_UNAUTHORIZED.toInfo();
            return RestResultBean.fromError(errorInformation, restRequestBean).asJsonResponse();
        }

        final LinkedHashMap<String,Object> returnMap = new LinkedHashMap();
        returnMap.put("raw",restRequestBean.getPwmApplication().getUserReportService().getSummaryData());
        returnMap.put("presentable",restRequestBean.getPwmApplication().getUserReportService().getSummaryData().asPresentableCollection(
                restRequestBean.getPwmApplication().getConfig(),
                restRequestBean.getPwmSession().getSessionStateBean().getLocale()
        ));

        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(returnMap);
        return restResultBean.asJsonResponse();
    }

    private static Map<String,Object> makeReportStatusData(ReportService reportService, Locale locale)
            throws LocalDBException
    {

        final LinkedHashMap<String,Object> returnMap = new LinkedHashMap<String, Object>();
        final ReportService.ReportStatusInfo reportInfo = reportService.getReportStatusInfo();
        returnMap.put("raw",reportInfo);

        final LinkedHashMap<String,Object> presentableMap = new LinkedHashMap<String, Object>();
        final NumberFormat numberFormat = NumberFormat.getInstance();
        presentableMap.put("Job Engine",reportInfo.isInprogress() ? "Running" : "Not Running");
        presentableMap.put("Users Processed",(reportInfo.isInprogress() && reportInfo.getTotal() == 0)
                ? "Counting..."
                : numberFormat.format(reportInfo.getCount()) + " of " + numberFormat.format(
                reportInfo.getTotal()));
        if (reportInfo.getCount() > 0 && reportInfo.getUpdated() > 0) {
            presentableMap.put("Updated Records",numberFormat.format(reportInfo.getUpdated()));
        }
        if (reportInfo.getCount() > reportInfo.getUpdated()) {
            presentableMap.put("Skipped Records",numberFormat.format(reportInfo.getCount() - reportInfo.getUpdated()));
        }
        if (reportInfo.getErrors() > 0) {
            presentableMap.put("Error Count", numberFormat.format(reportInfo.getErrors()));
        }
        if (reportInfo.getStartDate() != null) {
            presentableMap.put("Start Time",reportInfo.getStartDate());
        }
        if (reportInfo.getFinishDate() != null) {
            presentableMap.put("Finish Time",reportInfo.getFinishDate());
        }
        if (reportInfo.getStartDate() != null && reportInfo.getFinishDate() != null) {
            presentableMap.put("Total Time",new TimeDuration(reportInfo.getStartDate(),reportInfo.getFinishDate()).asCompactString());
        }
        if (reportInfo.isInprogress() && reportInfo.getCount() > 0) {
            final BigDecimal eventRate = reportInfo.getEventRateMeter().readEventRate().setScale(2,RoundingMode.UP);
            presentableMap.put("Users/Second",eventRate);
            if (!eventRate.equals(BigDecimal.ZERO)) {
                final int usersRemaining = reportInfo.getTotal() - reportInfo.getCount();
                final float secondsRemaining = usersRemaining / eventRate.floatValue();
                final TimeDuration remainingDuration = new TimeDuration(((int)secondsRemaining) * 1000);
                presentableMap.put("Estimated Time Remaining",remainingDuration.asLongString(locale));
            }
        }
        if (reportInfo.getLastError() != null) {
            presentableMap.put("Last Error", reportInfo.getLastError().toDebugStr());
        }

        int cachedRecords = reportService.recordsInCache();
        presentableMap.put("Records in Cache",cachedRecords);
        if (cachedRecords > 0) {
            presentableMap.put("Mean Record Cache Time",reportService.getSummaryData().getMeanCacheTime());
        }

        //presentableMap.put("Cached Records", reportInfo.size());
        returnMap.put("presentable",presentableMap);
        return returnMap;
    }
}
