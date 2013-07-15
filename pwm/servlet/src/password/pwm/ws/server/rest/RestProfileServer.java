package password.pwm.ws.server.rest;

import password.pwm.Permission;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Message;
import password.pwm.servlet.UpdateProfileServlet;
import password.pwm.util.FormMap;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/profile")
public class RestProfileServer {

    public static class JsonProfileData implements Serializable {
        public Map<String,String> profile;
        public List<FormConfiguration> formDefinition;
    }

    @Context
    HttpServletRequest request;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String doGetProfileData(
    ) {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, true, null);
        } catch (PwmUnrecoverableException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        }

        try {
            if (!restRequestBean.getPwmApplication().getConfig().readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_ENABLE)) {
                throw new PwmUnrecoverableException(PwmError.ERROR_SERVICE_NOT_AVAILABLE);
            }

            if (!Permission.checkPermission(Permission.PROFILE_UPDATE,restRequestBean.getPwmSession(),restRequestBean.getPwmApplication())) {
                throw new PwmUnrecoverableException(PwmError.ERROR_UNAUTHORIZED);
            }

            final Map<String,String> profileData = new HashMap<String,String>();
            {
                final Map<FormConfiguration,String> formData = new HashMap<FormConfiguration,String>();
                for (final FormConfiguration formConfiguration : restRequestBean.getPwmApplication().getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM)) {
                    formData.put(formConfiguration,"");
                }
                final List<FormConfiguration> formFields = restRequestBean.getPwmApplication().getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);
                UpdateProfileServlet.populateFormFromLdap(formFields,restRequestBean.getPwmSession(),formData,restRequestBean.getPwmSession().getSessionManager().getUserDataReader());
                for (FormConfiguration formConfig : formData.keySet()) {
                    profileData.put(formConfig.getName(),formData.get(formConfig));
                }
            }

            final RestResultBean restResultBean = new RestResultBean();
            JsonProfileData outputData = new JsonProfileData();
            outputData.profile = profileData;
            outputData.formDefinition = restRequestBean.getPwmApplication().getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);
            restResultBean.setData(outputData);
            return restResultBean.toJson();
        } catch (PwmException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        } catch (Exception e) {
            final String errorMsg = "unexpected error building json response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return RestServerHelper.outputJsonErrorResult(errorInformation, request);
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String doPostProfileData(
            final JsonProfileData jsonInput
    ) {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, true, null);
        } catch (PwmUnrecoverableException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        }

        try {
            if (!restRequestBean.getPwmApplication().getConfig().readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_ENABLE)) {
                throw new PwmUnrecoverableException(PwmError.ERROR_SERVICE_NOT_AVAILABLE);
            }

            if (!Permission.checkPermission(Permission.PROFILE_UPDATE,restRequestBean.getPwmSession(),restRequestBean.getPwmApplication())) {
                throw new PwmUnrecoverableException(PwmError.ERROR_UNAUTHORIZED);
            }

            final FormMap inputFormData = new FormMap(jsonInput.profile);
            final List<FormConfiguration> profileForm = restRequestBean.getPwmApplication().getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);
            final Map<FormConfiguration,String> profileFormData = new HashMap<FormConfiguration,String>();
            for (FormConfiguration formConfiguration : profileForm) {
                if (!formConfiguration.isReadonly() && inputFormData.containsKey(formConfiguration.getName())) {
                    profileFormData.put(formConfiguration,inputFormData.get(formConfiguration.getName()));
                }
            }
            UpdateProfileServlet.doProfileUpdate(restRequestBean.getPwmApplication(),restRequestBean.getPwmSession(),profileFormData);
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setSuccessMessage(Message.getLocalizedMessage(
                    restRequestBean.getPwmSession().getSessionStateBean().getLocale(),
                    Message.SUCCESS_UPDATE_ATTRIBUTES,
                    restRequestBean.getPwmApplication().getConfig()
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
