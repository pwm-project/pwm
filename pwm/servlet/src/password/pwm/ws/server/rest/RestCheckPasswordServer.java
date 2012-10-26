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
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.ContextManager;
import password.pwm.PwmApplication;
import password.pwm.PwmSession;
import password.pwm.bean.UserInfoBean;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.operations.UserStatusHelper;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestServerHelper;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

@Path("/checkpassword")
public class RestCheckPasswordServer {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(RestCheckPasswordServer.class);

    @Context
    HttpServletRequest request;

	@POST
	@Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String doPasswordRuleCheckJsonPost(
            final @FormParam("password1") String password1,
            final @FormParam("password2") String password2,
            final @FormParam("username") String username
    )
    {
        try {
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
            final PwmSession pwmSession = PwmSession.getPwmSession(request);
            LOGGER.trace(pwmSession, ServletHelper.debugHttpRequest(request));
            final boolean isExternal = RestServerHelper.determineIfRestClientIsExternal(request);

            if (!pwmSession.getSessionStateBean().isAuthenticated()) {
                throw new PwmUnrecoverableException(PwmError.ERROR_AUTHENTICATION_REQUIRED);
            }

            final String userDN;
            final UserInfoBean uiBean;
            if (username != null && username.length() > 0) { // check for another user
                userDN = username;
                uiBean = new UserInfoBean();
                UserStatusHelper.populateUserInfoBean(
                        pwmSession,
                        uiBean,
                        pwmApplication,
                        pwmSession.getSessionStateBean().getLocale(),
                        userDN,
                        null,
                        pwmSession.getSessionManager().getChaiProvider()
                );
            } else { // self check
                userDN = pwmSession.getUserInfoBean().getUserDN();
                uiBean = pwmSession.getUserInfoBean();
            }

            final PasswordCheckRequest checkRequest = new PasswordCheckRequest(userDN, password1, password2, uiBean);

            final String resultString = doPasswordRuleCheck(pwmApplication, pwmSession, checkRequest);
            if (!isExternal) {
                pwmApplication.getStatisticsManager().incrementValue(Statistic.REST_CHECKPASSWORD);
            }
            return resultString;
        } catch (Exception e) {
            LOGGER.error("unexpected error building json response for /checkpassword rest service: " + e.getMessage());
        }
        return "";
    }

    private static class PasswordCheckRequest {
        final String userDN;
        final String password1;
        final String password2;
        final UserInfoBean userInfoBean;

        private PasswordCheckRequest(String userDN, String password1, String password2, UserInfoBean userInfoBean) {
            this.userDN = userDN;
            this.password1 = password1;
            this.password2 = password2;
            this.userInfoBean = userInfoBean;
        }

        public String getUserDN() {
            return userDN;
        }

        public String getPassword1() {
            return password1;
        }

        public String getPassword2() {
            return password2;
        }

        public UserInfoBean getUserInfoBean() {
            return userInfoBean;
        }
    }


	public String doPasswordRuleCheck(PwmApplication pwmApplication, PwmSession pwmSession, PasswordCheckRequest checkRequest)
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final long startTime = System.currentTimeMillis();
        final ChaiUser user = ChaiFactory.createChaiUser(checkRequest.getUserDN(), pwmSession.getSessionManager().getChaiProvider());
        final PasswordUtility.PasswordCheckInfo passwordCheckInfo = PasswordUtility.checkEnteredPassword(
                pwmApplication,
                pwmSession.getSessionStateBean().getLocale(),
                user,
                checkRequest.getUserInfoBean(),
                checkRequest.getPassword1(),
                checkRequest.getPassword2()
        );


        final String outputString = generateJsonOutputString(passwordCheckInfo);
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("real-time password validator called for ").append(checkRequest.getUserDN());
            sb.append("\n");
            sb.append("  process time: ").append((int) (System.currentTimeMillis() - startTime)).append("ms");
            sb.append(", pass: ").append(passwordCheckInfo.isPassed());
            sb.append(", match: ").append(passwordCheckInfo.getMatch());
            sb.append(", strength: ").append(passwordCheckInfo.getStrength());
            if (!passwordCheckInfo.isPassed()) {
                sb.append(", err: ").append(passwordCheckInfo.getMessage());
            }
            sb.append("\n");
            sb.append("  passwordCheckInfo string: ").append(outputString);
            LOGGER.trace(pwmSession, sb.toString());
        }

        pwmApplication.getStatisticsManager().incrementValue(Statistic.PASSWORD_RULE_CHECKS);
        return outputString;
	}


    private static String generateJsonOutputString(
            final PasswordUtility.PasswordCheckInfo checkInfo
    ) {
        final Map<String, String> outputMap = new HashMap<String, String>();
        outputMap.put("version", "2");
        outputMap.put("strength", String.valueOf(checkInfo.getStrength()));
        outputMap.put("match", checkInfo.getMatch().toString());
        outputMap.put("message", checkInfo.getMessage());
        outputMap.put("passed", String.valueOf(checkInfo.isPassed()));
        outputMap.put("errorCode", String.valueOf(checkInfo.getErrorCode()));

        final Gson gson = new Gson();
        return gson.toJson(outputMap);
    }
}


