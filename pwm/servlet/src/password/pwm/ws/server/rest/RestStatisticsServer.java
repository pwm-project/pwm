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
import password.pwm.ContextManager;
import password.pwm.PwmApplication;
import password.pwm.PwmSession;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsBundle;
import password.pwm.util.stats.StatisticsManager;
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

    private static final PwmLogger LOGGER = PwmLogger.getLogger(RestStatisticsServer.class);

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
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
        final PwmSession pwmSession = PwmSession.getPwmSession(request);
        try {
            LOGGER.trace(pwmSession, ServletHelper.debugHttpRequest(request));
            final boolean isExternal = RestServerHelper.determineIfRestClientIsExternal(request);

            final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
            JsonOutput jsonOutput = new JsonOutput();
            jsonOutput.EPS = addEpsStats(statisticsManager);

            if (statName != null && statName.length() > 0) {
                jsonOutput.nameData = doNameStat(statisticsManager, statName, days);
            } else {
                jsonOutput.keyData = doKeyStat(statisticsManager, statKey);
            }

            if (isExternal) {
                pwmApplication.getStatisticsManager().incrementValue(Statistic.REST_STATISTICS);
            }

            final RestResultBean resultBean = new RestResultBean();
            resultBean.setData(jsonOutput);
            return resultBean.toJson();
        } catch (PwmException e) {
            final RestResultBean resultBean = RestResultBean.fromErrorInformation(e.getErrorInformation(),pwmApplication,pwmSession);
            LOGGER.error(pwmSession, e.getErrorInformation().toDebugStr());
            return resultBean.toJson();
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
            outputMap.put(loopEps.toString() + "_TOP",String.valueOf(figureTopValue(loopEps, statisticsManager)));
        }

        return outputMap;
    }

    private int figureTopValue(final Statistic.EpsType epsType, final StatisticsManager statisticsManager) {
        final int dailyReduceFactor = (24 * 10);
        final int epsMultiplier = 60 * 60; // hour

        int counter = 100; // minimum

        final Statistic relatedStatistic = epsType.getRelatedStatistic();
        if (relatedStatistic != null) {
            final Map<String, String> statistics = statisticsManager.getStatHistory(relatedStatistic, 30);
            for (final String key : statistics.keySet()) {
                final int loopValue = Integer.valueOf(statistics.get(key)) / dailyReduceFactor;
                if (loopValue > counter) {
                    counter = loopValue;
                }
            }
        }

        for (final Statistic.EpsDuration loopDuration : Statistic.EpsDuration.values()) {
            final BigDecimal loopEps = statisticsManager.readEps(epsType, loopDuration);
            if (loopEps.multiply(BigDecimal.valueOf(epsMultiplier)).compareTo(BigDecimal.valueOf(counter)) > 0) {
                counter = loopEps.multiply(BigDecimal.valueOf(epsMultiplier)).intValue();
            }
        }

        if (counter < 200) {
            while(counter % 20 != 0) {
                counter++;
            }
        } else {
            while(counter % 200 != 0) {
                counter++;
            }
        }

        return counter;
    }

    @GET
    @Produces("text/csv")
    @Path("/file")
    public String doPwmStatisticFileGet() {
        try {
            RestServerHelper.determineIfRestClientIsExternal(request);
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
            final StatisticsManager statsManager = pwmApplication.getStatisticsManager();
            final StringWriter stringWriter = new StringWriter();
            statsManager.outputStatsToCsv(stringWriter,true);
            response.setHeader("Content-Disposition","attachment; fileName=statistics.csv");
            return stringWriter.toString();
        } catch (Exception e) {
            LOGGER.error("unexpected error building response for /statistics/file rest service: " + e.getMessage());
        }
        return "";

    }

}
