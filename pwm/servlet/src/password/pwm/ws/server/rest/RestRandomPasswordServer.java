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
import password.pwm.*;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.TimeDuration;
import password.pwm.util.stats.Statistic;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

@Path("/randompassword")
public class RestRandomPasswordServer {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(RestRandomPasswordServer.class);

    @Context
    HttpServletRequest request;

	// This method is called if TEXT_PLAIN is request
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public String sayPlainTextHello() {
        try {
            final PwmSession pwmSession = PwmSession.getPwmSession(request);
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
            return makeRandomPassword(pwmApplication, pwmSession);
        } catch (Exception e) {
            LOGGER.error("unexpected error building json response for /randompassword rest service: " + e.getMessage());
        }
        return "";
	}

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String sayJsonHealth() {
        try {
            final PwmSession pwmSession = PwmSession.getPwmSession(request);
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
            final String randomPassword = makeRandomPassword(pwmApplication, pwmSession);
            final Map<String, String> outputMap = new HashMap<String, String>();
            outputMap.put("version", "1");
            outputMap.put("password", randomPassword);

            final Gson gson = new Gson();
            return gson.toJson(outputMap);
        } catch (Exception e) {
            LOGGER.error("unexpected error building json response for /randompassword rest service: " + e.getMessage());
        }

        return "";
    }


    private static String makeRandomPassword(final PwmApplication pwmApplication, final PwmSession pwmSession)
            throws PwmUnrecoverableException
    {
        final long startTime = System.currentTimeMillis();
        final String randomPassword = RandomPasswordGenerator.createRandomPassword(pwmSession, pwmApplication);

        {
            final StringBuilder sb = new StringBuilder();
            sb.append("real-time random password generator called");
            sb.append(" (").append(TimeDuration.fromCurrent(startTime).asCompactString());
            sb.append(")");
            pwmApplication.getStatisticsManager().incrementValue(Statistic.GENERATED_PASSWORDS);
            LOGGER.trace(pwmSession, sb.toString());
        }

        return randomPassword;
    }
}