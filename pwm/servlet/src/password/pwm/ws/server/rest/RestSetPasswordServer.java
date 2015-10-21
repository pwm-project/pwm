/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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
import password.pwm.Permission;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Message;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.PasswordData;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;
import password.pwm.ws.server.ServicePermissions;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.Serializable;

@Path("/setpassword")
public class RestSetPasswordServer extends AbstractRestServer {

    public static class JsonInputData implements Serializable
    {
        public String username;
        public String password;
        public boolean random;
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public Response doPostSetPasswordForm(
            final @FormParam("username") String username,
            final @FormParam("password") String password,
            final @FormParam("random") boolean random
    )
            throws PwmUnrecoverableException
    {
        final JsonInputData jsonInputData = new JsonInputData();
        jsonInputData.username = username;
        jsonInputData.password = password;
        jsonInputData.random = random;

        return doSetPassword(request, response, jsonInputData);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doPostSetPasswordJson(
            final JsonInputData jsonInputData
    )
            throws PwmUnrecoverableException
    {
        return doSetPassword(request, response, jsonInputData);
    }

    private static Response doSetPassword(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final JsonInputData jsonInputData

    )
    {
        final RestRequestBean restRequestBean;
        try {
            final ServicePermissions servicePermissions = new ServicePermissions();
            servicePermissions.setAdminOnly(false);
            servicePermissions.setAuthRequired(true);
            servicePermissions.setBlockExternal(true);
            servicePermissions.setHelpdeskPermitted(true);
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, servicePermissions, jsonInputData.username);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        final String password = jsonInputData.password;
        final boolean random = jsonInputData.random;

        if ((password == null || password.length() < 1) && !random) {
            final String errorMessage = "field 'password' must have a value or field 'random' must be set to true";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER, errorMessage, new String[]{"password"});
            return RestResultBean.fromError(errorInformation,restRequestBean).asJsonResponse();
        }

        if ((password != null && password.length() > 0) && random) {
            final String errorMessage = "field 'password' cannot have a value or field 'random' must be set to true";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER, errorMessage, new String[]{"password"});
            return RestResultBean.fromError(errorInformation,restRequestBean).asJsonResponse();
        }

        final HelpdeskProfile helpdeskProfile = restRequestBean.getPwmSession().getSessionManager().getHelpdeskProfile(restRequestBean.getPwmApplication());
        try {
            if (restRequestBean.getUserIdentity() == null) {
                if (!restRequestBean.getPwmSession().getSessionManager().checkPermission(
                        restRequestBean.getPwmApplication(), Permission.CHANGE_PASSWORD)) {
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,
                            "actor does not have required permission"));
                }
            } else {
                if (helpdeskProfile == null) {
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,
                            "actor does not have required permission"));
                }
            }

            final JsonInputData jsonResultData = new JsonInputData();
            jsonResultData.random = random;

            /* helpdesk set password */
            if (restRequestBean.getUserIdentity() != null) {

                final boolean useProxy = helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY);
                final ChaiUser chaiUser = useProxy
                        ? restRequestBean.getPwmApplication().getProxiedChaiUser(restRequestBean.getUserIdentity())
                        : restRequestBean.getPwmSession().getSessionManager().getActor(restRequestBean.getPwmApplication(),restRequestBean.getUserIdentity());
                final PasswordData newPassword;
                if (random) {
                    final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(
                            restRequestBean.getPwmApplication(),
                            restRequestBean.getPwmSession().getLabel(),
                            restRequestBean.getUserIdentity(),
                            chaiUser,
                            restRequestBean.getPwmSession().getSessionStateBean().getLocale()
                    );
                    newPassword = RandomPasswordGenerator.createRandomPassword(restRequestBean.getPwmSession().getLabel(), passwordPolicy, restRequestBean.getPwmApplication());
                } else {
                    newPassword = new PasswordData(password);
                }
                PasswordUtility.helpdeskSetUserPassword(
                        restRequestBean.getPwmSession(),
                        chaiUser,
                        restRequestBean.getUserIdentity(),
                        restRequestBean.getPwmApplication(),
                        newPassword
                );
                jsonResultData.password = null;
                jsonResultData.username = restRequestBean.getUserIdentity().toDelimitedKey();
            } else {
                final PasswordData newPassword;
                if (random) {
                    newPassword = RandomPasswordGenerator.createRandomPassword(restRequestBean.getPwmSession(), restRequestBean.getPwmApplication());
                } else {
                    newPassword = new PasswordData(password);
                }
                PasswordUtility.setActorPassword(restRequestBean.getPwmSession(), restRequestBean.getPwmApplication(),
                        newPassword);
                restRequestBean.getPwmApplication().getAuditManager().submit(AuditEvent.CHANGE_PASSWORD, restRequestBean.getPwmSession().getUserInfoBean(), restRequestBean.getPwmSession());
                jsonResultData.password = null;
                jsonResultData.username = restRequestBean.getPwmSession().getUserInfoBean().getUserIdentity().toDelimitedKey();
            }
            if (restRequestBean.isExternal()) {
                StatisticsManager.incrementStat(restRequestBean.getPwmApplication(), Statistic.REST_SETPASSWORD);
            }
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setError(false);
            restResultBean.setData(jsonResultData);
            restResultBean.setSuccessMessage(Message.getLocalizedMessage(
                    restRequestBean.getPwmSession().getSessionStateBean().getLocale(),
                    Message.Success_PasswordChange,
                    restRequestBean.getPwmApplication().getConfig()));
            return restResultBean.asJsonResponse();
        } catch (PwmException e) {
            return RestResultBean.fromError(e.getErrorInformation(),restRequestBean).asJsonResponse();
        } catch (Exception e) {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMessage);
            return RestResultBean.fromError(errorInformation,restRequestBean).asJsonResponse();
        }
    }
}
