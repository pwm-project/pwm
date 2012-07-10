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

import com.google.gson.Gson;
import com.novell.ldapchai.util.StringHelper;
import password.pwm.ContextManager;
import password.pwm.PwmApplication;
import password.pwm.PwmSession;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsBundle;
import password.pwm.util.stats.StatisticsManager;
import password.pwm.ws.server.RestServerHelper;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@Path("/statistics")
public class RestStatisticsServer {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(RestStatisticsServer.class);

    @Context
    HttpServletRequest request;

    // This method is called if TEXT_PLAIN is request
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String doPwmStatisticJsonGet(
            final @QueryParam("statKey") String statKey,
            final @QueryParam("statName") String statName,
            final @QueryParam("days") String days
    ) {
        try {
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
            final PwmSession pwmSession = PwmSession.getPwmSession(request);
            LOGGER.trace(pwmSession, ServletHelper.debugHttpRequest(request));
            final boolean isExternal = RestServerHelper.determineIfRestClientIsExternal(request);

            final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();

            final String resultString;
            if (statName != null && statName.length() > 0) {
                resultString = doNameStat(statisticsManager, statName, days);
            } else {
                resultString = doKeyStat(statisticsManager, statKey);
            }

            if (isExternal) {
                pwmApplication.getStatisticsManager().incrementValue(Statistic.REST_STATISTICS);
            }

            return resultString;
        } catch (Exception e) {
            LOGGER.error("unexpected error building json response for /health rest service: " + e.getMessage());
        }
        return "";
    }

    private String doNameStat(final StatisticsManager statisticsManager, final String statName, final String days) {
        final Statistic statistic = Statistic.valueOf(statName);
        final int historyDays = StringHelper.convertStrToInt(days, 30);

        final Map<String,Object> results = new HashMap<String,Object>();
        results.putAll(statisticsManager.getStatHistory(statistic, historyDays));
        results.put("EPS",addEpsStats(statisticsManager));


        final Gson gson = new Gson();
        return gson.toJson(results);
    }

    private String doKeyStat(final StatisticsManager statisticsManager, String statKey) {
        if (statKey == null || statKey.length() < 1) {
            statKey = StatisticsManager.KEY_CUMULATIVE;
        }

        final StatisticsBundle statisticsBundle = statisticsManager.getStatBundleForKey(statKey);
        final Map<String,Object> outputValueMap = new TreeMap<String,Object>();
        for (Statistic stat : Statistic.values()) {
            outputValueMap.put(stat.getKey(),statisticsBundle.getStatistic(stat));
        }

        outputValueMap.put("EPS", addEpsStats(statisticsManager));

        final Gson gson = new Gson();
        return gson.toJson(outputValueMap);
    }

    private Map<String,String> addEpsStats(final StatisticsManager statisticsManager){
        final int dailyReduceFactor = (24 * 10);
        final Map<String,String> outputMap = new TreeMap<String,String>();
        for (final StatisticsManager.EpsType loopEps : StatisticsManager.EpsType.values()) {
            outputMap.put(loopEps.toString(),statisticsManager.readEps(loopEps).toPlainString());
        }

        {
            Map<String,String> values = statisticsManager.getStatHistory(Statistic.AUTHENTICATIONS, 30);
            int counter = 50;
            for (final String key : values.keySet()) {
                final int loopValue = Integer.valueOf(values.get(key)) / dailyReduceFactor;
                if (loopValue > counter) {
                    counter = loopValue;
                }
            }

            for (final StatisticsManager.EpsType loopEps : new StatisticsManager.EpsType[]{StatisticsManager.EpsType.AUTHENTICATION_10, StatisticsManager.EpsType.AUTHENTICATION_60, StatisticsManager.EpsType.AUTHENTICATION_240}) {
                if (statisticsManager.readEps(loopEps).multiply(BigDecimal.valueOf(60)).compareTo(BigDecimal.valueOf(counter)) > 0) {
                    counter = statisticsManager.readEps(loopEps).multiply(BigDecimal.valueOf(60)).intValue();
                }
            }

            outputMap.put("AUTHENTICATION_TOP",String.valueOf(counter));
        }

        {
            Map<String,String> values = statisticsManager.getStatHistory(Statistic.PASSWORD_CHANGES, 30);
            int counter = 50;
            for (final String key : values.keySet()) {
                final int loopValue = Integer.valueOf(values.get(key)) / dailyReduceFactor;
                if (loopValue > counter) {
                    counter = loopValue;
                }
            }

            for (final StatisticsManager.EpsType loopEps : new StatisticsManager.EpsType[]{StatisticsManager.EpsType.PASSWORD_CHANGES_10, StatisticsManager.EpsType.PASSWORD_CHANGES_60, StatisticsManager.EpsType.PASSWORD_CHANGES_240}) {
                if (statisticsManager.readEps(loopEps).multiply(BigDecimal.valueOf(60)).compareTo(BigDecimal.valueOf(counter)) > 0) {
                    counter = statisticsManager.readEps(loopEps).multiply(BigDecimal.valueOf(60)).intValue();
                }
            }
            outputMap.put("PASSWORD_CHANGES_TOP",String.valueOf(counter));
        }

        return outputMap;
    }
}
