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
            @QueryParam("key") String key
    ) {
        try {
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
            final PwmSession pwmSession = PwmSession.getPwmSession(request);
            LOGGER.trace(pwmSession, ServletHelper.debugHttpRequest(request));
            final boolean isExternal = RestServerHelper.determineIfRestClientIsExternal(request);

            if (key == null || key.length() < 1) {
                key = StatisticsManager.KEY_CUMULATIVE;
            }

            final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
            final StatisticsBundle statisticsBundle = statisticsManager.getStatBundleForKey(key);
            final Map<String,String> outputValueMap = new TreeMap<String,String>();
            for (Statistic stat : Statistic.values()) {
                outputValueMap.put(stat.getKey(),statisticsBundle.getStatistic(stat));
            }

            final Gson gson = new Gson();
            final String resultString = gson.toJson(outputValueMap);
            if (isExternal) {
                pwmApplication.getStatisticsManager().incrementValue(Statistic.REST_STATISTICS);
            }
            return resultString;
        } catch (Exception e) {
            LOGGER.error("unexpected error building json response for /health rest service: " + e.getMessage());
        }
        return "";
    }
}
