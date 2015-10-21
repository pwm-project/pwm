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

import com.novell.ldapchai.util.StringHelper;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsBundle;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;
import password.pwm.ws.server.ServicePermissions;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@Path("/statistics")
public class RestStatisticsServer extends AbstractRestServer {
    private static final PwmLogger LOGGER = PwmLogger.forClass(RestStatisticsServer.class);

    @Context
    HttpServletRequest request;

    @Context
    HttpServletResponse response;

    @Context
    ServletContext context;

    public static class JsonOutput implements Serializable
    {
        public Map<String,String> EPS;
        public Map<String,Object> nameData;
        public Map<String,Object> keyData;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response doPwmStatisticJsonGet(
            final @QueryParam("statKey") String statKey,
            final @QueryParam("statName") String statName,
            final @QueryParam("days") String days
    )
    {
        final ServicePermissions servicePermissions = figurePermissions();
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, servicePermissions, null);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        try {
            final StatisticsManager statisticsManager = restRequestBean.getPwmApplication().getStatisticsManager();
            JsonOutput jsonOutput = new JsonOutput();
            jsonOutput.EPS = addEpsStats(statisticsManager);

            if (statName != null && statName.length() > 0) {
                jsonOutput.nameData = doNameStat(statisticsManager, statName, days);
            } else {
                jsonOutput.keyData = doKeyStat(statisticsManager, statKey);
            }

            if (restRequestBean.isExternal()) {
                StatisticsManager.incrementStat(restRequestBean.getPwmApplication(), Statistic.REST_STATISTICS);
            }

            final RestResultBean resultBean = new RestResultBean();
            resultBean.setData(jsonOutput);
            return resultBean.asJsonResponse();
        } catch (Exception e) {
            final String errorMsg = "unexpected error building json response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return RestResultBean.fromError(errorInformation,restRequestBean).asJsonResponse();

        }
    }

    private Map<String,Object> doNameStat(final StatisticsManager statisticsManager, final String statName, final String days) {
        final Statistic statistic = Statistic.valueOf(statName);
        final int historyDays = StringHelper.convertStrToInt(days, 30);

        final Map<String,Object> results = new HashMap<>();
        results.putAll(statisticsManager.getStatHistory(statistic, historyDays));
        return results;
    }

    private Map<String,Object> doKeyStat(final StatisticsManager statisticsManager, String statKey) {
        if (statKey == null || statKey.length() < 1) {
            statKey = StatisticsManager.KEY_CUMULATIVE;
        }

        final StatisticsBundle statisticsBundle = statisticsManager.getStatBundleForKey(statKey);
        final Map<String,Object> outputValueMap = new TreeMap<>();
        for (Statistic stat : Statistic.values()) {
            outputValueMap.put(stat.getKey(),statisticsBundle.getStatistic(stat));
        }

        return outputValueMap;
    }

    private Map<String,String> addEpsStats(final StatisticsManager statisticsManager){
        final Map<String,String> outputMap = new TreeMap<>();
        for (final Statistic.EpsType loopEps : Statistic.EpsType.values()) {
            for (final Statistic.EpsDuration loopDuration : Statistic.EpsDuration.values()) {
                final BigDecimal loopValue = statisticsManager.readEps(loopEps,loopDuration);
                final BigDecimal outputValue = loopValue.setScale(3, RoundingMode.UP);
                outputMap.put(loopEps.toString() + "_" + loopDuration.toString(),outputValue.toString());
            }
        }

        return outputMap;
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
