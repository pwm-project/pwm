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
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.Permission;
import password.pwm.config.FormConfiguration;
import password.pwm.config.FormUtility;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.servlet.UpdateProfileServlet;
import password.pwm.i18n.Message;
import password.pwm.ldap.LdapUserDataReader;
import password.pwm.ldap.UserDataReader;
import password.pwm.util.FormMap;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/profile")
public class RestProfileServer extends AbstractRestServer {

    public static class JsonProfileData implements Serializable {
        public String username;
        public Map<String,String> profile;
        public List<FormConfiguration> formDefinition;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public Response doGetProfileJsonData(
            final @QueryParam("username") String username
    ) {
        try {
            final RestResultBean restResultBean = doGetProfileDataImpl(request,response,username);
            return restResultBean.asJsonResponse();
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        } catch (Exception e) {
            final String errorMsg = "unexpected error building json response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return RestResultBean.fromError(errorInformation).asJsonResponse();
        }
    }

    protected static RestResultBean doGetProfileDataImpl(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final String username
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final ServicePermissions servicePermissions = new ServicePermissions();
        servicePermissions.setAdminOnly(false);
        servicePermissions.setAuthRequired(true);
        servicePermissions.setBlockExternal(true);
        final RestRequestBean restRequestBean = RestServerHelper.initializeRestRequest(request, response, servicePermissions, username);

        if (!restRequestBean.getPwmApplication().getConfig().readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_ENABLE)) {
            throw new PwmUnrecoverableException(PwmError.ERROR_SERVICE_NOT_AVAILABLE);
        }

        if (!restRequestBean.getPwmSession().getSessionManager().checkPermission(restRequestBean.getPwmApplication(), Permission.PROFILE_UPDATE)) {
            throw new PwmUnrecoverableException(PwmError.ERROR_UNAUTHORIZED);
        }

        final Map<String,String> profileData = new HashMap<>();
        {
            final Map<FormConfiguration,String> formData = new HashMap<>();
            for (final FormConfiguration formConfiguration : restRequestBean.getPwmApplication().getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM)) {
                formData.put(formConfiguration,"");
            }
            final List<FormConfiguration> formFields = restRequestBean.getPwmApplication().getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);

            if (restRequestBean.getUserIdentity() != null) {
                final UserDataReader userDataReader = LdapUserDataReader.selfProxiedReader(
                        restRequestBean.getPwmApplication(), restRequestBean.getPwmSession(),
                        restRequestBean.getUserIdentity());
                FormUtility.populateFormMapFromLdap(formFields, restRequestBean.getPwmSession().getLabel(), formData, userDataReader);
            } else {
                FormUtility.populateFormMapFromLdap(formFields, restRequestBean.getPwmSession().getLabel(), formData, restRequestBean.getPwmSession().getSessionManager().getUserDataReader(restRequestBean.getPwmApplication()));
            }

            for (FormConfiguration formConfig : formData.keySet()) {
                profileData.put(formConfig.getName(),formData.get(formConfig));
            }
        }

        final JsonProfileData outputData = new JsonProfileData();
        outputData.profile = profileData;
        outputData.formDefinition = restRequestBean.getPwmApplication().getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(outputData);
        return restResultBean;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public Response doPostProfileData(
            final JsonProfileData jsonInput
    ) {
        try {
            final RestResultBean restResultBean = doPostProfileDataImpl(request, response, jsonInput);
            return restResultBean.asJsonResponse();
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        } catch (Exception e) {
            final String errorMsg = "unexpected error building json response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return RestResultBean.fromError(errorInformation).asJsonResponse();
        }
    }

    public static RestResultBean doPostProfileDataImpl(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final JsonProfileData jsonInput
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {

        final ServicePermissions servicePermissions = new ServicePermissions();
        servicePermissions.setAdminOnly(false);
        servicePermissions.setAuthRequired(true);
        servicePermissions.setBlockExternal(true);
        final RestRequestBean restRequestBean = RestServerHelper.initializeRestRequest(request, response, servicePermissions, jsonInput.username);

        if (!restRequestBean.getPwmApplication().getConfig().readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_ENABLE)) {
            throw new PwmUnrecoverableException(PwmError.ERROR_SERVICE_NOT_AVAILABLE);
        }

        if (!restRequestBean.getPwmSession().getSessionManager().checkPermission(restRequestBean.getPwmApplication(), Permission.PROFILE_UPDATE)) {
            throw new PwmUnrecoverableException(PwmError.ERROR_UNAUTHORIZED);
        }

        final FormMap inputFormData = new FormMap(jsonInput.profile);
        final List<FormConfiguration> profileForm = restRequestBean.getPwmApplication().getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);
        final Map<FormConfiguration,String> profileFormData = new HashMap<>();
        for (FormConfiguration formConfiguration : profileForm) {
            if (!formConfiguration.isReadonly() && inputFormData.containsKey(formConfiguration.getName())) {
                profileFormData.put(formConfiguration,inputFormData.get(formConfiguration.getName()));
            }
        }
        final PwmRequest pwmRequest = PwmRequest.forRequest(request, response);
        if (restRequestBean.getUserIdentity() != null) {
            final ChaiUser theUser = restRequestBean.getPwmSession().getSessionManager().getActor(restRequestBean.getPwmApplication(),restRequestBean.getUserIdentity());
            UpdateProfileServlet.doProfileUpdate(pwmRequest, profileFormData, theUser);
        } else {
            final ChaiUser theUser = restRequestBean.getPwmSession().getSessionManager().getActor(restRequestBean.getPwmApplication());
            UpdateProfileServlet.doProfileUpdate(pwmRequest, profileFormData, theUser);
        }
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setSuccessMessage(Message.getLocalizedMessage(
                restRequestBean.getPwmSession().getSessionStateBean().getLocale(),
                Message.Success_UpdateProfile,
                restRequestBean.getPwmApplication().getConfig()
        ));
        return restResultBean;
    }
}
