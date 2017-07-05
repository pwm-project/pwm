/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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
import lombok.AllArgsConstructor;
import lombok.Getter;
import password.pwm.PwmApplication;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.ldap.UserInfo;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmSession;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;
import password.pwm.ws.server.ServicePermissions;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;

@Path("/checkpassword")
public class RestCheckPasswordServer extends AbstractRestServer {
    private static final PwmLogger LOGGER = PwmLogger.forClass(RestCheckPasswordServer.class);

    @Getter
    @AllArgsConstructor
    public static class JsonInput implements Serializable
    {
        public final String password1;
        public final String password2;
        public final String username;
    }

    public static class JsonData implements Serializable
    {
        public int version;
        public int strength;
        public PasswordUtility.PasswordCheckInfo.MatchStatus match;
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
        final URI uri = javax.ws.rs.core.UriBuilder.fromUri("../reference/rest.jsp?forwardedFromRestServer=true").build();
        return javax.ws.rs.core.Response.temporaryRedirect(uri).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response doPasswordRuleCheckFormPost(
            @FormParam("password1") final String password1,
            @FormParam("password2") final String password2,
            @FormParam("username") final String username
    )
            throws PwmUnrecoverableException
    {
        final JsonInput jsonInput = new JsonInput(password1, password2, username);
        return doOperation(jsonInput);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doPasswordRuleCheckJsonPost(final JsonInput jsonInput)
            throws PwmUnrecoverableException
    {
        return doOperation(jsonInput);
    }

    public Response doOperation(final JsonInput jsonInput)
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final RestRequestBean restRequestBean;
        try {
            final ServicePermissions servicePermissions = ServicePermissions.builder()
                    .adminOnly(false)
                    .authRequired(true)
                    .blockExternal(true)
                    .helpdeskPermitted(true)
                    .build();
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, servicePermissions, jsonInput.username);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }
        LOGGER.trace("beginning check password operation for user " + restRequestBean.getPwmSession().getUserInfo().getUserIdentity());

        if (jsonInput.password1 == null || jsonInput.password1.length() < 1) {
            final String errorMessage = "missing field 'password1'";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_FIELD_REQUIRED, errorMessage, new String[]{"password1"});
            return RestResultBean.fromError(errorInformation, restRequestBean).asJsonResponse();
        }

        try {
            final UserIdentity userIdentity;
            final UserInfo userInfo;
            if (restRequestBean.getUserIdentity() != null) { // check for another user
                userIdentity = restRequestBean.getUserIdentity();
                userInfo = UserInfoFactory.newUserInfo(
                        restRequestBean.getPwmApplication(),
                        restRequestBean.getPwmSession().getLabel(),
                        restRequestBean.getPwmSession().getSessionStateBean().getLocale(),
                        userIdentity,
                        restRequestBean.getPwmSession().getSessionManager().getChaiProvider()
                );
            } else { // self check
                userIdentity = restRequestBean.getPwmSession().getUserInfo().getUserIdentity();
                userInfo = restRequestBean.getPwmSession().getUserInfo();
            }

            final PasswordCheckRequest checkRequest = new PasswordCheckRequest(
                    userIdentity,
                    jsonInput.password1 == null || jsonInput.password1.isEmpty() ? null : new PasswordData(jsonInput.password1),
                    jsonInput.password2 == null || jsonInput.password2.isEmpty() ? null : new PasswordData(jsonInput.password2),
                    userInfo
            );

            if (restRequestBean.isExternal()) {
                restRequestBean.getPwmApplication().getStatisticsManager().incrementValue(Statistic.REST_CHECKPASSWORD);
            } else {
                restRequestBean.getPwmApplication().getStatisticsManager().incrementValue(Statistic.PASSWORD_RULE_CHECKS);
            }

            final JsonData jsonData = doPasswordRuleCheck(restRequestBean.getPwmApplication(), restRequestBean.getPwmSession(), checkRequest);
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(jsonData);
            final TimeDuration timeDuration = TimeDuration.fromCurrent(startTime);
            LOGGER.trace(restRequestBean.getPwmSession(), "REST /checkpassword response (" + timeDuration.asCompactString() + "): " + JsonUtil.serialize(jsonData));
            return restResultBean.asJsonResponse();
        } catch (PwmException e) {
            LOGGER.debug(restRequestBean.getPwmSession(), "REST /checkpassword error during execution: " + e.getMessage(), e);
            return RestResultBean.fromError(e.getErrorInformation(), restRequestBean).asJsonResponse();
        } catch (Exception e) {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMessage);
            LOGGER.error(restRequestBean.getPwmSession(),errorInformation.toDebugStr(),e);
            return RestResultBean.fromError(errorInformation, restRequestBean).asJsonResponse();
        }
    }

    public static class PasswordCheckRequest {
        final UserIdentity userIdentity;
        final PasswordData password1;
        final PasswordData password2;
        final UserInfo userInfo;

        public PasswordCheckRequest(
                final UserIdentity userDN,
                final PasswordData password1,
                final PasswordData password2,
                final UserInfo userInfo
        ) {
            this.userIdentity = userDN;
            this.password1 = password1;
            this.password2 = password2;
            this.userInfo = userInfo;
        }

        public UserIdentity getUserIdentity() {
            return userIdentity;
        }

        public PasswordData getPassword1() {
            return password1;
        }

        public PasswordData getPassword2() {
            return password2;
        }

        public UserInfo getUserInfo() {
            return userInfo;
        }
    }


    public static JsonData doPasswordRuleCheck(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final PasswordCheckRequest checkRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final HelpdeskProfile helpdeskProfile = pwmSession.getSessionManager().getHelpdeskProfile(pwmApplication);
        final boolean useProxy = helpdeskProfile != null && helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY);
        final boolean thirdParty = checkRequest.getUserIdentity() != null
                && !checkRequest.getUserIdentity().canonicalEquals(pwmSession.getUserInfo().getUserIdentity(), pwmApplication);

        final ChaiUser user = useProxy && thirdParty
                ? pwmApplication.getProxiedChaiUser(checkRequest.getUserIdentity())
                : pwmSession.getSessionManager().getActor(pwmApplication, checkRequest.getUserIdentity());
        final LoginInfoBean loginInfoBean = thirdParty
                ? null
                : pwmSession.getLoginInfoBean();
        final PasswordUtility.PasswordCheckInfo passwordCheckInfo = PasswordUtility.checkEnteredPassword(
                pwmApplication,
                pwmSession.getSessionStateBean().getLocale(),
                user,
                checkRequest.getUserInfo(),
                loginInfoBean,
                checkRequest.getPassword1(),
                checkRequest.getPassword2()
        );

        return JsonData.fromPasswordCheckInfo(passwordCheckInfo);
    }
}
