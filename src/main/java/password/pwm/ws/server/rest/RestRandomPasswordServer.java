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
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.PasswordData;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;
import password.pwm.ws.server.ServicePermissions;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Path("/randompassword")
public class RestRandomPasswordServer extends AbstractRestServer {
    private static final PwmLogger LOGGER = PwmLogger.forClass(RestRandomPasswordServer.class);
    private static final ServicePermissions servicePermissions = new ServicePermissions();

    static {
        servicePermissions.setAdminOnly(false);
        servicePermissions.setAuthRequired(false);
        servicePermissions.setBlockExternal(true);
        servicePermissions.setHelpdeskPermitted(true);
        servicePermissions.setPublicDuringConfig(true);
    }

    public static class JsonOutput implements Serializable
    {
        public String password;
    }

    public static class JsonInput implements Serializable
    {
        public String username;
        public int strength;
        public int minLength;
        public int maxLength;
        public String chars;
        public boolean noUser;
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public Response doPostRandomPasswordForm(
            final @FormParam("username") String username,
            final @FormParam("strength") int strength,
            final @FormParam("maxLength") int maxLength,
            final @FormParam("minLength") int minLength,
            final @FormParam("chars") String chars,
            final @FormParam("noUser") boolean noUser
    )
            throws PwmUnrecoverableException
    {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, servicePermissions, username);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        final JsonInput jsonInput = new JsonInput();
        jsonInput.username = username;
        jsonInput.strength = strength;
        jsonInput.maxLength = maxLength;
        jsonInput.minLength = minLength;
        jsonInput.chars = chars;
        jsonInput.noUser = noUser;

        try {
            final JsonOutput jsonOutput = doOperation(restRequestBean, jsonInput);
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(jsonOutput);
            return restResultBean.asJsonResponse();
        } catch (PwmException e) {
            LOGGER.error(restRequestBean.getPwmSession(),"error executing rest-json random password request: " + e.getMessage(),e);
            return RestResultBean.fromError(e.getErrorInformation(), restRequestBean).asJsonResponse();
        } catch (Exception e) {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMessage);
            return RestResultBean.fromError(errorInformation, restRequestBean).asJsonResponse();
        }
    }

    // This method is called if TEXT_PLAIN is request
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String doPlainRandomPassword(
            final @QueryParam("username") String username,
            final @QueryParam("strength") int strength,
            final @QueryParam("minLength") int minLength,
            final @QueryParam("maxLength") int maxLength,
            final @QueryParam("chars") String chars,
            final @QueryParam("noUser") boolean noUser
    )
            throws PwmUnrecoverableException
    {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, servicePermissions, username);
        } catch (PwmUnrecoverableException e) {
            RestServerHelper.handleNonJsonErrorResult(e.getErrorInformation());
            return null;
        }

        final JsonInput jsonInput = new JsonInput();
        jsonInput.username = username;
        jsonInput.strength = strength;
        jsonInput.maxLength = maxLength;
        jsonInput.minLength = minLength;
        jsonInput.chars = chars;
        jsonInput.noUser = noUser;

        try {
            return doOperation(restRequestBean, jsonInput).password;
        } catch (Exception e) {
            LOGGER.error(restRequestBean.getPwmSession(),"error executing rest-json random password request: " + e.getMessage(),e);
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMessage);
            RestServerHelper.handleNonJsonErrorResult(errorInformation);
            return null;
        }
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doPostRandomPasswordJson(
            final JsonInput jsonInput
    )
            throws PwmUnrecoverableException
    {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, servicePermissions, jsonInput.username);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        try {
            final JsonOutput jsonOutput = doOperation(restRequestBean, jsonInput);
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(jsonOutput);
            return restResultBean.asJsonResponse();
        } catch (PwmException e) {
            LOGGER.error(restRequestBean.getPwmSession(),"error executing rest-form random password request: " + e.getMessage(),e);
            return RestResultBean.fromError(e.getErrorInformation(), restRequestBean).asJsonResponse();
        } catch (Exception e) {
            LOGGER.error(restRequestBean.getPwmSession(),"error executing rest-form random password request: " + e.getMessage(),e);
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMessage);
            return RestResultBean.fromError(errorInformation, restRequestBean).asJsonResponse();
        }
    }

    private static JsonOutput doOperation(
            final RestRequestBean restRequestBean,
            final JsonInput jsonInput
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final RandomPasswordGenerator.RandomGeneratorConfig randomConfig = new RandomPasswordGenerator.RandomGeneratorConfig();
        if (jsonInput.strength > 0 && jsonInput.strength <= 100) {
            randomConfig.setMinimumStrength(jsonInput.strength);
        }
        if (jsonInput.minLength > 0 && jsonInput.minLength <= 100 * 1024) {
            randomConfig.setMinimumLength(jsonInput.minLength);
        }
        if (jsonInput.maxLength > 0 && jsonInput.maxLength <= 100 * 1024) {
            randomConfig.setMaximumLength(jsonInput.maxLength);
        }
        if (jsonInput.chars != null) {
            final List<String> charValues = new ArrayList<>();
            for (int i = 0; i < jsonInput.chars.length(); i++) {
                charValues.add(String.valueOf(jsonInput.chars.charAt(i)));
            }
            randomConfig.setSeedlistPhrases(charValues);
        }

        if (!jsonInput.noUser && restRequestBean.getPwmSession().getSessionStateBean().isAuthenticated()) {
            final UserIdentity userIdentity = UserIdentity.fromKey(jsonInput.username,restRequestBean.getPwmApplication());
            if (userIdentity != null) {
                final HelpdeskProfile helpdeskProfile = restRequestBean.getPwmSession().getSessionManager().getHelpdeskProfile(restRequestBean.getPwmApplication());
                final boolean useProxy = helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY);

                final ChaiUser theUser = useProxy
                        ? restRequestBean.getPwmApplication().getProxiedChaiUser(restRequestBean.getUserIdentity())
                        : restRequestBean.getPwmSession().getSessionManager().getActor(restRequestBean.getPwmApplication(),userIdentity);

                randomConfig.setPasswordPolicy(PasswordUtility.readPasswordPolicyForUser(
                        restRequestBean.getPwmApplication(),
                        restRequestBean.getPwmSession().getLabel(),
                        restRequestBean.getUserIdentity(),
                        theUser,
                        restRequestBean.getPwmSession().getSessionStateBean().getLocale()));
            } else {
                randomConfig.setPasswordPolicy(restRequestBean.getPwmSession().getUserInfoBean().getPasswordPolicy());
            }
        } else {
            final Configuration config  = restRequestBean.getPwmApplication().getConfig();
            randomConfig.setPasswordPolicy(config.getPasswordPolicy(
                    config.getPasswordProfileIDs().iterator().next(),
                    restRequestBean.getPwmSession().getSessionStateBean().getLocale()));
        }

        final PasswordData randomPassword = RandomPasswordGenerator.createRandomPassword(restRequestBean.getPwmSession().getLabel(), randomConfig, restRequestBean.getPwmApplication());
        final JsonOutput outputMap = new JsonOutput();
        outputMap.password = randomPassword.getStringValue();

        if (restRequestBean.isExternal()) {
            StatisticsManager.incrementStat(restRequestBean.getPwmApplication(), Statistic.REST_SETPASSWORD);
        }

        return outputMap;
    }
}

