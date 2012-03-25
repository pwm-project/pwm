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
import password.pwm.PwmApplication;
import password.pwm.PwmSession;
import password.pwm.util.PwmLogger;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.PasswordUtility;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
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
    public String doPlainRandomPassword() {
        try {
            final PwmSession pwmSession = PwmSession.getPwmSession(request);
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
            LOGGER.trace(pwmSession, ServletHelper.debugHttpRequest(request));
            return RandomPasswordGenerator.createRandomPassword(pwmSession, pwmApplication);
        } catch (Exception e) {
            LOGGER.error("unexpected error building json response for /randompassword rest service: " + e.getMessage());
        }
        return "";
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String doPostRandomPassword(
            final @FormParam("username") String username,
            final @FormParam("strength") String strength
    ) {
        try {
            final PwmSession pwmSession = PwmSession.getPwmSession(request);
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
            LOGGER.trace(pwmSession, ServletHelper.debugHttpRequest(request));

            final RandomPasswordGenerator.RandomGeneratorConfig randomConfig = new RandomPasswordGenerator.RandomGeneratorConfig();

            if (pwmSession.getSessionStateBean().isAuthenticated()) {
                if (username != null && username.length() > 0) {
                    final ChaiUser theUser = ChaiFactory.createChaiUser(username, pwmSession.getSessionManager().getChaiProvider());
                    randomConfig.setPasswordPolicy(PasswordUtility.readPasswordPolicyForUser(pwmApplication, pwmSession, theUser, pwmSession.getSessionStateBean().getLocale()));
                } else {
                    randomConfig.setPasswordPolicy(pwmSession.getUserInfoBean().getPasswordPolicy());
                }
            } else {
                randomConfig.setPasswordPolicy(pwmApplication.getConfig().getGlobalPasswordPolicy(pwmSession.getSessionStateBean().getLocale()));
            }

            if (strength != null && strength.length() > 0) {
                randomConfig.setMinimumStrength(Integer.valueOf(strength));
            }

            final String randomPassword = RandomPasswordGenerator.createRandomPassword(pwmSession, randomConfig, pwmApplication);
            final Map<String, String> outputMap = new HashMap<String, String>();
            outputMap.put("password", randomPassword);

            final Gson gson = new Gson();
            return gson.toJson(outputMap);
        } catch (Exception e) {
            LOGGER.error("unexpected error building json response for /randompassword rest service: " + e.getMessage());
        }

        return "";
    }
}

