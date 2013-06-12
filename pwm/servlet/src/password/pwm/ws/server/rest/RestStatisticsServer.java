/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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
import password.pwm.Permission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsBundle;
import password.pwm.util.stats.StatisticsManager;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.Serializable;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@Path("/statistics")
public class RestStatisticsServer {

    @Context
    HttpServletRequest request;

    @Context
    HttpServletResponse response;

    public static class JsonOutput implements Serializable
    {
        public Map<String,String> EPS;
        public Map<String,Object> nameData;
        public Map<String,Object> keyData;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String doPwmStatisticJsonGet(
            final @QueryParam("statKey") String statKey,
            final @QueryParam("statName") String statName,
            final @QueryParam("days") String days
    )
    {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, false, null);
        } catch (PwmUnrecoverableException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
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
                restRequestBean.getPwmApplication().getStatisticsManager().incrementValue(Statistic.REST_STATISTICS);
            }

            final RestResultBean resultBean = new RestResultBean();
            resultBean.setData(jsonOutput);
            return resultBean.toJson();
        //} catch (PwmException e) {
        //    return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        } catch (Exception e) {
            final String errorMsg = "unexpected error building json response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return RestServerHelper.outputJsonErrorResult(errorInformation, request);
        }
    }

    @GET
    @Produces("text/csv")
    @Path("/file")
    public String doPwmStatisticFileGet() {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, true, null);
        } catch (PwmUnrecoverableException e) {
            RestServerHelper.handleNonJsonErrorResult(e.getErrorInformation());
            return null;
        }

        try {
            if (!Permission.checkPermission(Permission.PWMADMIN, restRequestBean.getPwmSession(), restRequestBean.getPwmApplication())) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"actor does not have required permission"));
            }

            final StatisticsManager statsManager = restRequestBean.getPwmApplication().getStatisticsManager();
            final StringWriter stringWriter = new StringWriter();
            statsManager.outputStatsToCsv(stringWriter, true);
            response.setHeader("Content-Disposition","attachment; fileName=statistics.csv");
            return stringWriter.toString();
        } catch (Exception e) {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMessage);
            RestServerHelper.handleNonJsonErrorResult(errorInformation);
            return null;
        }
    }

    private Map<String,Object> doNameStat(final StatisticsManager statisticsManager, final String statName, final String days) {
        final Statistic statistic = Statistic.valueOf(statName);
        final int historyDays = StringHelper.convertStrToInt(days, 30);

        final Map<String,Object> results = new HashMap<String,Object>();
        results.putAll(statisticsManager.getStatHistory(statistic, historyDays));
        return results;
    }

    private Map<String,Object> doKeyStat(final StatisticsManager statisticsManager, String statKey) {
        if (statKey == null || statKey.length() < 1) {
            statKey = StatisticsManager.KEY_CUMULATIVE;
        }

        final StatisticsBundle statisticsBundle = statisticsManager.getStatBundleForKey(statKey);
        final Map<String,Object> outputValueMap = new TreeMap<String,Object>();
        for (Statistic stat : Statistic.values()) {
            outputValueMap.put(stat.getKey(),statisticsBundle.getStatistic(stat));
        }

        return outputValueMap;
    }

    private Map<String,String> addEpsStats(final StatisticsManager statisticsManager){
        final Map<String,String> outputMap = new TreeMap<String,String>();
        for (final Statistic.EpsType loopEps : Statistic.EpsType.values()) {
            for (final Statistic.EpsDuration loopDuration : Statistic.EpsDuration.values()) {
                final BigDecimal loopValue = statisticsManager.readEps(loopEps,loopDuration);
                final BigDecimal outputValue = loopValue.setScale(3, RoundingMode.UP);
                outputMap.put(loopEps.toString() + "_" + loopDuration.toString(),outputValue.toString());
            }
        }

        return outputMap;
    }
}
