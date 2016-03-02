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
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMonitor;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmSession;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;
import password.pwm.ws.server.ServicePermissions;
import password.pwm.ws.server.rest.bean.HealthData;
import password.pwm.ws.server.rest.bean.HealthRecord;

import javax.servlet.ServletException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Path("/health")
public class RestHealthServer extends AbstractRestServer {
    final private static PwmLogger LOGGER = PwmLogger.forClass(RestHealthServer.class);

    final private static String PARAM_IMMEDIATE_REFRESH = "refreshImmediate";

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String doPwmHealthPlainGet(
            @QueryParam(PARAM_IMMEDIATE_REFRESH) final boolean requestImmediateParam
    ) {
        final ServicePermissions servicePermissions = figurePermissions();
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, servicePermissions, null);
        } catch (PwmUnrecoverableException e) {
            RestServerHelper.handleNonJsonErrorResult(e.getErrorInformation());
            return null;
        }

        try {
            final HealthMonitor.CheckTimeliness timeliness = determineDataTimeliness(restRequestBean.getPwmApplication(), restRequestBean.getPwmSession(), requestImmediateParam);
            final String resultString = restRequestBean.getPwmApplication().getHealthMonitor().getMostSevereHealthStatus(timeliness).toString() + "\n";
            if (restRequestBean.isExternal()) {
                StatisticsManager.incrementStat(restRequestBean.getPwmApplication(), Statistic.REST_HEALTH);
            }
            return resultString;
        } catch (Exception e) {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMessage);
            RestServerHelper.handleNonJsonErrorResult(errorInformation);
            return null;
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public Response doPwmHealthJsonGet(
            @QueryParam(PARAM_IMMEDIATE_REFRESH) final boolean requestImmediateParam
    ) {
        final ServicePermissions servicePermissions = figurePermissions();
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, servicePermissions, null);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        try {
            final HealthData jsonOutput = processGetHealthCheckData(
                    restRequestBean.getPwmApplication(),
                    restRequestBean.getPwmSession(),
                    requestImmediateParam
            );

            if (restRequestBean.isExternal()) {
                StatisticsManager.incrementStat(restRequestBean.getPwmApplication(), Statistic.REST_HEALTH);
            }
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(jsonOutput);
            return restResultBean.asJsonResponse();
        } catch (PwmException e) {
            return RestResultBean.fromError(e.getErrorInformation(), restRequestBean).asJsonResponse();
        } catch (Exception e) {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMessage);
            return RestResultBean.fromError(errorInformation, restRequestBean).asJsonResponse();
        }
    }

    private static HealthMonitor.CheckTimeliness determineDataTimeliness(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final boolean refreshImmediate
    )
            throws PwmUnrecoverableException
    {
        boolean allowImmediate = pwmApplication.getApplicationMode() == PwmApplicationMode.CONFIGURATION
                && pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.PWMADMIN);

        return refreshImmediate && allowImmediate
                ? HealthMonitor.CheckTimeliness.Immediate
                : HealthMonitor.CheckTimeliness.CurrentButNotAncient;

    }

    private static HealthData processGetHealthCheckData(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final boolean refreshImmediate
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        final HealthMonitor healthMonitor = pwmApplication.getHealthMonitor();
        final HealthMonitor.CheckTimeliness timeliness = determineDataTimeliness(pwmApplication, pwmSession, refreshImmediate);
        final List<password.pwm.health.HealthRecord> healthRecords = new ArrayList<>(healthMonitor.getHealthRecords(timeliness));
        final List<HealthRecord> healthRecordBeans = HealthRecord.fromHealthRecords(healthRecords, pwmSession.getSessionStateBean().getLocale(),
                pwmApplication.getConfig());
        final HealthData healthData = new HealthData();
        healthData.timestamp = healthMonitor.getLastHealthCheckTime();
        healthData.overall = healthMonitor.getMostSevereHealthStatus(timeliness).toString();
        healthData.records = healthRecordBeans;
        return healthData;
    }

    private ServicePermissions figurePermissions() {
        ServicePermissions servicePermissions = ServicePermissions.ADMIN_OR_CONFIGMODE;
        try {
            final Configuration config = ContextManager.getContextManager(context).getPwmApplication().getConfig();
            if (config.readSettingAsBoolean(PwmSetting.PUBLIC_HEALTH_STATS_WEBSERVICES)) {
                servicePermissions = ServicePermissions.PUBLIC;
            }
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unable to read service permissions, defaulting to non-public; error: " + e.getMessage());
        }
        return servicePermissions;
    }
}