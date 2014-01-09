/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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


import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.PwmSession;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserStatusHelper;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;
import password.pwm.ws.server.ServicePermissions;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;

@Path("/checkpassword")
public class RestCheckPasswordServer {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(RestCheckPasswordServer.class);

    @Context
    HttpServletRequest request;

    public static class JsonInput implements Serializable
    {
        public String password1;
        public String password2;
        public String username;
    }

    public static class JsonData implements Serializable
    {
        public int version;
        public int strength;
        public PasswordUtility.PasswordCheckInfo.MATCH_STATUS match;
        public String message;
        public boolean passed;
        public int errorCode;

        public static JsonData fromPasswordCheckInfo(
                final PasswordUtility.PasswordCheckInfo checkInfo
        ) {
            final JsonData outputMap = new JsonData();
            outputMap.version = 2;
            outputMap.strength = checkInfo.getStrength();
            outputMap.match = checkInfo.getMatch();
            outputMap.message = checkInfo.getMessage();
            outputMap.passed = checkInfo.isPassed();
            outputMap.errorCode = checkInfo.getErrorCode();
            return outputMap;
        }
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public javax.ws.rs.core.Response doHtmlRedirect() throws URISyntaxException {
        final URI uri = javax.ws.rs.core.UriBuilder.fromUri("../rest.jsp?forwardedFromRestServer=true").build();
        return javax.ws.rs.core.Response.temporaryRedirect(uri).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response doPasswordRuleCheckFormPost(
            final @FormParam("password1") String password1,
            final @FormParam("password2") String password2,
            final @FormParam("username") String username
    )
            throws PwmUnrecoverableException
    {
        final JsonInput jsonInput = new JsonInput();
        jsonInput.password1 = password1;
        jsonInput.password2 = password2;
        jsonInput.username = username;

        return doOperation(jsonInput);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doPasswordRuleCheckJsonPost(JsonInput jsonInput)
            throws PwmUnrecoverableException
    {
        return doOperation(jsonInput);
    }

    public Response doOperation(JsonInput jsonInput)
            throws PwmUnrecoverableException
    {
        final RestRequestBean restRequestBean;
        try {
            final ServicePermissions servicePermissions = new ServicePermissions();
            servicePermissions.setAdminOnly(false);
            servicePermissions.setAuthRequired(true);
            servicePermissions.setBlockExternal(true);
            servicePermissions.setHelpdeskPermitted(true);
            restRequestBean = RestServerHelper.initializeRestRequest(request, servicePermissions, jsonInput.username);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        if (jsonInput.password1 == null || jsonInput.password1.length() < 1) {
            final String errorMessage = "missing field 'password1'";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_FIELD_REQUIRED, errorMessage, new String[]{"password1"});
            return RestResultBean.fromError(errorInformation, restRequestBean).asJsonResponse();
        }

        try {
            final UserIdentity userDN;
            final UserInfoBean uiBean;
            if (restRequestBean.getUserIdentity() != null) { // check for another user
                userDN = restRequestBean.getUserIdentity();
                uiBean = new UserInfoBean();
                UserStatusHelper.populateUserInfoBean(
                        restRequestBean.getPwmApplication(), restRequestBean.getPwmSession(),
                        uiBean,
                        restRequestBean.getPwmSession().getSessionStateBean().getLocale(),
                        userDN,
                        null,
                        restRequestBean.getPwmSession().getSessionManager().getChaiProvider(restRequestBean.getPwmApplication())
                );
            } else { // self check
                userDN = restRequestBean.getPwmSession().getUserInfoBean().getUserIdentity();
                uiBean = restRequestBean.getPwmSession().getUserInfoBean();
            }

            final PasswordCheckRequest checkRequest = new PasswordCheckRequest(userDN, jsonInput.password1, jsonInput.password2, uiBean);

            if (!restRequestBean.isExternal()) {
                restRequestBean.getPwmApplication().getStatisticsManager().incrementValue(Statistic.REST_CHECKPASSWORD);
            }

            final JsonData jsonData = doPasswordRuleCheck(restRequestBean.getPwmApplication(), restRequestBean.getPwmSession(), checkRequest);
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(jsonData);
            return restResultBean.asJsonResponse();
        } catch (PwmException e) {
            return RestResultBean.fromError(e.getErrorInformation(), restRequestBean).asJsonResponse();
        } catch (Exception e) {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMessage);
            return RestResultBean.fromError(errorInformation, restRequestBean).asJsonResponse();
        }
    }

    private static class PasswordCheckRequest {
        final UserIdentity userDN;
        final String password1;
        final String password2;
        final UserInfoBean userInfoBean;

        private PasswordCheckRequest(UserIdentity userDN, String password1, String password2, UserInfoBean userInfoBean) {
            this.userDN= userDN;
            this.password1 = password1;
            this.password2 = password2;
            this.userInfoBean = userInfoBean;
        }

        public UserIdentity getUserIdentity() {
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


    public JsonData doPasswordRuleCheck(PwmApplication pwmApplication, PwmSession pwmSession, PasswordCheckRequest checkRequest)
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final long startTime = System.currentTimeMillis();
        final ChaiUser user = pwmSession.getSessionManager().getActor(pwmApplication, checkRequest.getUserIdentity());
        final PasswordUtility.PasswordCheckInfo passwordCheckInfo = PasswordUtility.checkEnteredPassword(
                pwmApplication,
                pwmSession.getSessionStateBean().getLocale(),
                user,
                checkRequest.getUserInfoBean(),
                checkRequest.getPassword1(),
                checkRequest.getPassword2(),
                pwmSession.getSessionManager()
        );

        final JsonData result = JsonData.fromPasswordCheckInfo(passwordCheckInfo);
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("real-time password validator called for ").append(checkRequest.getUserIdentity());
            sb.append("\n");
            sb.append("  process time: ").append((int) (System.currentTimeMillis() - startTime)).append("ms");
            sb.append("\n");
            sb.append("  passwordCheckInfo string: ").append(Helper.getGson().toJson(result));
            LOGGER.trace(pwmSession, sb.toString());
        }

        pwmApplication.getStatisticsManager().incrementValue(Statistic.PASSWORD_RULE_CHECKS);
        return result;
    }
}


