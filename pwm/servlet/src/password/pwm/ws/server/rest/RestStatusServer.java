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

import password.pwm.bean.PasswordStatus;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmPasswordRule;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserStatusReader;
import password.pwm.tag.PasswordRequirementsTag;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;
import password.pwm.ws.server.ServicePermissions;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
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
        public Map<String,String> atributes;

        public static JsonStatusData fromUserInfoBean(final UserInfoBean userInfoBean, final Configuration config, final Locale locale) {
            final JsonStatusData jsonStatusData = new JsonStatusData();
            jsonStatusData.userDN = userInfoBean.getUserIdentity().toDeliminatedKey();
            jsonStatusData.userID = userInfoBean.getUsername();
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

            if (userInfoBean.getCachedAttributeValues() != null && !userInfoBean.getCachedAttributeValues().isEmpty()) {
                jsonStatusData.atributes = Collections.unmodifiableMap(userInfoBean.getCachedAttributeValues());
            }

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
    public Response doGetStatusData(
            @QueryParam("username") final String username
    ) {
        final RestRequestBean restRequestBean;
        try {
            final ServicePermissions servicePermissions = new ServicePermissions();
            servicePermissions.setAdminOnly(false);
            servicePermissions.setAuthRequired(true);
            servicePermissions.setBlockExternal(true);
            restRequestBean = RestServerHelper.initializeRestRequest(request, servicePermissions, username);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        try {
            final UserInfoBean userInfoBean;
            if (restRequestBean.getUserIdentity() != null) {
                userInfoBean = new UserInfoBean();
                final UserStatusReader userStatusReader = new UserStatusReader(restRequestBean.getPwmApplication());
                userStatusReader.populateUserInfoBean(
                        restRequestBean.getPwmSession(),
                        userInfoBean,
                        restRequestBean.getPwmSession().getSessionStateBean().getLocale(),
                        restRequestBean.getUserIdentity(),
                        null,
                        restRequestBean.getPwmSession().getSessionManager().getChaiProvider(restRequestBean.getPwmApplication())
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
            return restResultBean.asJsonResponse();
        } catch (PwmException e) {
            return RestResultBean.fromError(e.getErrorInformation(), restRequestBean).asJsonResponse();
        } catch (Exception e) {
            final String errorMsg = "unexpected error building json response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return RestResultBean.fromError(errorInformation, restRequestBean).asJsonResponse();
        }
    }
}
