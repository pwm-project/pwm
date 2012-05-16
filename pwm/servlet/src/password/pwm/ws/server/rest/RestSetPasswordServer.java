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
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import password.pwm.ContextManager;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmSession;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestServerHelper;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

@Path("/setpassword")
public class RestSetPasswordServer {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(RestSetPasswordServer.class);

    @Context
    HttpServletRequest request;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String doPostSetPassword(
            final @FormParam("username") String username,
            final @FormParam("password") String password
    ) {
        final Gson gson = new Gson();
        final Map<String,String> outputMap = new HashMap<String,String>();

        try {
            final PwmSession pwmSession = PwmSession.getPwmSession(request);
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
            LOGGER.trace(pwmSession, ServletHelper.debugHttpRequest(request));
            final boolean isExternal = RestServerHelper.determineIfRestClientIsExternal(request);

            if (!pwmSession.getSessionStateBean().isAuthenticated()) {
                outputMap.put("success","false");
                outputMap.put("errorMsg", PwmError.ERROR_AUTHENTICATION_REQUIRED.toInfo().toDebugStr());
                return gson.toJson(outputMap);
            }

            try {
                if (username != null && username.length() > 0) {

                    if (!Permission.checkPermission(Permission.HELPDESK, pwmSession, pwmApplication)) {
                        outputMap.put("success","false");
                        outputMap.put("errorMsg", PwmError.ERROR_UNAUTHORIZED.toInfo().toDebugStr());
                        return gson.toJson(outputMap);
                    }

                    final ChaiUser chaiUser = ChaiFactory.createChaiUser(username, pwmSession.getSessionManager().getChaiProvider());
                    PasswordUtility.helpdeskSetUserPassword(pwmSession, chaiUser, pwmApplication, password);
                } else {
                    PasswordUtility.setUserPassword(pwmSession, pwmApplication, password);
                }
                outputMap.put("success", "true");
                final String resultString = gson.toJson(outputMap);
                if (isExternal) {
                    pwmApplication.getStatisticsManager().incrementValue(Statistic.REST_SETPASSWORD);
                }
                return resultString;
            } catch (PwmOperationalException e) {
                outputMap.put("success","false");
                outputMap.put("errorCode", String.valueOf(e.getError().getErrorCode()));
                outputMap.put("errorMsg", e.getErrorInformation().getDetailedErrorMsg());
                return gson.toJson(outputMap);
            }

        } catch (Exception e) {
            outputMap.put("success","false");
            outputMap.put("errorCode", String.valueOf(PwmError.ERROR_UNKNOWN.getErrorCode()));
            outputMap.put("errorMsg", "unexpected error building json response for /randompassword rest service: " + e.getMessage());
            return gson.toJson(outputMap);
        }
    }
}
