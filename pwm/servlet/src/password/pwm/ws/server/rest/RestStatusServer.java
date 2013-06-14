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

import password.pwm.PwmPasswordPolicy;
import password.pwm.PwmSession;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PasswordStatus;
import password.pwm.config.PwmPasswordRule;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.tag.PasswordRequirementsTag;
import password.pwm.util.operations.UserStatusHelper;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.*;

@Path("/status")
public class RestStatusServer {

    public static class JsonStatusData implements Serializable {
        public String userDN;
        public String userID;
        public String userEmailAddress;
        public Date passwordExpirationTime;
        public Date passwordLastModifiedTime;
        public boolean requiresNewPassword;
        public boolean requiresResponseConfig;
        public boolean requiresUpdateProfile;

        public PasswordStatus passwordStatus;
        public Map<String,String> passwordPolicy;
        public List<String> passwordRules;

        public static JsonStatusData fromUserInfoBean(final UserInfoBean userInfoBean, final Configuration config, final Locale locale) {
            final JsonStatusData jsonStatusData = new JsonStatusData();
            jsonStatusData.userDN = userInfoBean.getUserDN();
            jsonStatusData.userID = userInfoBean.getUserID();
            jsonStatusData.userEmailAddress = userInfoBean.getUserEmailAddress();
            jsonStatusData.passwordExpirationTime = userInfoBean.getPasswordExpirationTime();
            jsonStatusData.passwordLastModifiedTime = userInfoBean.getPasswordLastModifiedTime();
            jsonStatusData.passwordStatus = userInfoBean.getPasswordState();

            jsonStatusData.requiresNewPassword = userInfoBean.isRequiresNewPassword();
            jsonStatusData.requiresResponseConfig = userInfoBean.isRequiresResponseConfig();
            jsonStatusData.requiresUpdateProfile = userInfoBean.isRequiresResponseConfig();

            jsonStatusData.passwordPolicy = new HashMap<String,String>();
            for (final PwmPasswordRule rule : PwmPasswordRule.values()) {
                jsonStatusData.passwordPolicy.put(rule.name(),userInfoBean.getPasswordPolicy().getValue(rule));
            }

            jsonStatusData.passwordRules = PasswordRequirementsTag.getPasswordRequirementsStrings(
                    userInfoBean.getPasswordPolicy(),
                    config,
                    locale
            );

            return jsonStatusData;
        }
    }

    @Context
    HttpServletRequest request;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public javax.ws.rs.core.Response doHtmlRedirect() throws URISyntaxException {
        return RestServerHelper.doHtmlRedirect();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String doGetStatusData(
            @QueryParam("username") final String username
    ) {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, true, username);
        } catch (PwmUnrecoverableException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        }

        try {
            final UserInfoBean userInfoBean;
            if (restRequestBean.getUserDN() != null && restRequestBean.getUserDN().length() > 0) {
                userInfoBean = new UserInfoBean();
                UserStatusHelper.populateUserInfoBean(
                        restRequestBean.getPwmSession(),
                        userInfoBean,
                        restRequestBean.getPwmApplication(),
                        restRequestBean.getPwmSession().getSessionStateBean().getLocale(),
                        restRequestBean.getUserDN(),
                        null,
                        restRequestBean.getPwmSession().getSessionManager().getChaiProvider()
                );
            } else {
                userInfoBean = restRequestBean.getPwmSession().getUserInfoBean();
            }
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(JsonStatusData.fromUserInfoBean(
                    userInfoBean,
                    restRequestBean.getPwmApplication().getConfig(),
                    restRequestBean.getPwmSession().getSessionStateBean().getLocale()
            ));
            return restResultBean.toJson();
        } catch (PwmException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        } catch (Exception e) {
            final String errorMsg = "unexpected error building json response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return RestServerHelper.outputJsonErrorResult(errorInformation, request);
        }
    }

}
