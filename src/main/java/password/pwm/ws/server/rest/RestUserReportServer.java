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

package password.pwm.ws.server.rest;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.Permission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Display;
import password.pwm.svc.report.ReportService;
import password.pwm.svc.report.ReportStatusInfo;
import password.pwm.svc.report.UserCacheRecord;
import password.pwm.util.ClosableIterator;
import password.pwm.util.LocaleHelper;
import password.pwm.util.TimeDuration;
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
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;

@Path("/report")
public class RestUserReportServer extends AbstractRestServer {
    @Context
    HttpServletRequest request;

    @GET
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public Response doReportDetailData(
            @QueryParam("maximum") int maximum
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, LocalDBException
    {
        maximum = maximum > 0 ? maximum : 10 * 1000;

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
        if (reportService.getReportStatusInfo().getCurrentProcess() != ReportStatusInfo.ReportEngineProcess.None) {
            final String errorMsg = "report data not available, engine is busy.  Try again later.  status=" + reportService.getReportStatusInfo().getCurrentProcess();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg);
            return RestResultBean.fromError(errorInformation, restRequestBean).asJsonResponse();
        }

        final ArrayList<UserCacheRecord> reportData = new ArrayList<>();
        ClosableIterator<UserCacheRecord> cacheBeanIterator = null;
        try {
            cacheBeanIterator = reportService.iterator();
            while (cacheBeanIterator.hasNext() && reportData.size() < maximum) {
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


    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public Response doGetReportEngineStatusData(
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, LocalDBException
    {
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

        final ReportStatusBean returnMap = makeReportStatusData(
                restRequestBean.getPwmApplication().getReportService(),
                restRequestBean.getPwmSession().getSessionStateBean().getLocale()
        );
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(returnMap);
        return restResultBean.asJsonResponse();
    }

    @GET
    @Path("/summary")
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public Response doGetReportSummaryData(
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, LocalDBException
    {
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

        final LinkedHashMap<String,Object> returnMap = new LinkedHashMap<>();
        returnMap.put("raw",restRequestBean.getPwmApplication().getReportService().getSummaryData());
        returnMap.put("presentable", restRequestBean.getPwmApplication().getReportService().getSummaryData().asPresentableCollection(
                restRequestBean.getPwmApplication().getConfig(),
                restRequestBean.getPwmSession().getSessionStateBean().getLocale()
        ));

        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(returnMap);
        return restResultBean.asJsonResponse();
    }

    private static ReportStatusBean makeReportStatusData(ReportService reportService, Locale locale)
            throws LocalDBException
    {
        final NumberFormat numberFormat = NumberFormat.getInstance();

        final ReportStatusBean returnMap = new ReportStatusBean();
        final ReportStatusInfo reportInfo = reportService.getReportStatusInfo();
        final LinkedHashMap<String,Object> presentableMap = new LinkedHashMap<>();

        if (reportInfo.getCurrentProcess() == ReportStatusInfo.ReportEngineProcess.RollOver) {
            presentableMap.put("Job Engine", "Calculating Report Summary");
            presentableMap.put("Users Processed",
                    numberFormat.format(reportService.getSummaryData().getTotalUsers())
                            + " of " + numberFormat.format(reportInfo.getTotal()));
        } else {
            returnMap.setControllable(true);
            presentableMap.put("Job Engine",reportInfo.isInProgress() ? "Processing" : "Idle");
            presentableMap.put("Users Processed", (reportInfo.isInProgress() && reportInfo.getTotal() == 0)
                    ? "Searching LDAP..."
                    : numberFormat.format(reportInfo.getCount()) + " of " + numberFormat.format(reportInfo.getTotal()));
            if (reportInfo.getCount() > 0 && reportInfo.getUpdated() > 0) {
                presentableMap.put("Updated Records", numberFormat.format(reportInfo.getUpdated()));
            }
            if (reportInfo.getCount() > reportInfo.getUpdated()) {
                presentableMap.put("Skipped Updates", numberFormat.format(reportInfo.getCount() - reportInfo.getUpdated()));
            }
            if (reportInfo.getErrors() > 0) {
                presentableMap.put("Error Count", numberFormat.format(reportInfo.getErrors()));
            }
            if (reportInfo.getStartDate() != null) {
                presentableMap.put("Start Time", reportInfo.getStartDate());
            } else {
                presentableMap.put("Last Job", LocaleHelper.getLocalizedMessage(Display.Value_NotApplicable,null));
            }
            if (reportInfo.getFinishDate() != null) {
                presentableMap.put("Finish Time", reportInfo.getFinishDate());
            }
            if (reportInfo.getStartDate() != null && reportInfo.getFinishDate() != null) {
                presentableMap.put("Total Time", new TimeDuration(reportInfo.getStartDate(), reportInfo.getFinishDate()).asCompactString());
            }
            if (reportInfo.isInProgress() && reportInfo.getCount() > 0) {
                final BigDecimal eventRate = reportInfo.getEventRateMeter().readEventRate().setScale(2, RoundingMode.UP);
                presentableMap.put("Users/Second", eventRate);
                if (!eventRate.equals(BigDecimal.ZERO)) {
                    final int usersRemaining = reportInfo.getTotal() - reportInfo.getCount();
                    final float secondsRemaining = usersRemaining / eventRate.floatValue();
                    final TimeDuration remainingDuration = new TimeDuration(((int) secondsRemaining) * 1000);
                    presentableMap.put("Estimated Time Remaining", remainingDuration.asLongString(locale));
                }
            }
            if (reportInfo.getLastError() != null) {
                presentableMap.put("Last Error", reportInfo.getLastError().toDebugStr());
            }

            final int cachedRecords = reportInfo.getCount();
            presentableMap.put("Records in Cache", numberFormat.format(cachedRecords));
            if (cachedRecords > 0) {
                presentableMap.put("Mean Record Cache Time", reportService.getSummaryData().getMeanCacheTime());
            }
        }

        returnMap.setRaw(reportInfo);
        returnMap.setPresentable(presentableMap);
        return returnMap;
    }

    static class ReportStatusBean implements Serializable {
        private Map<String,Object> presentable = new LinkedHashMap<>();
        private ReportStatusInfo raw;
        private boolean controllable;

        public Map<String, Object> getPresentable() {
            return presentable;
        }

        public void setPresentable(Map<String, Object> presentable) {
            this.presentable = presentable;
        }

        public ReportStatusInfo getRaw() {
            return raw;
        }

        public void setRaw(ReportStatusInfo raw) {
            this.raw = raw;
        }

        public boolean isControllable() {
            return controllable;
        }

        public void setControllable(boolean controllable) {
            this.controllable = controllable;
        }
    }
}
