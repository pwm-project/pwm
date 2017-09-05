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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Path("/randompassword")
public class RestRandomPasswordServer extends AbstractRestServer {
    private static final PwmLogger LOGGER = PwmLogger.forClass(RestRandomPasswordServer.class);

    private static final ServicePermissions SERVICE_PERMISSIONS = ServicePermissions.builder()
            .adminOnly(false)
            .authRequired(false)
            .blockExternal(true)
            .helpdeskPermitted(true)
            .publicDuringConfig(true)
            .build();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class JsonOutput implements Serializable
    {
        private String password;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class JsonInput implements Serializable
    {
        private String username;
        private int strength;
        private int minLength;
        private int maxLength;
        private String chars;
        private boolean noUser;
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public Response doPostRandomPasswordForm(
            @FormParam("username") final String username,
            @FormParam("strength") final int strength,
            @FormParam("maxLength") final int maxLength,
            @FormParam("minLength") final int minLength,
            @FormParam("chars") final String chars,
            @FormParam("noUser") final boolean noUser
    )
            throws PwmUnrecoverableException
    {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, SERVICE_PERMISSIONS, username);
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
            @QueryParam("username") final String username,
            @QueryParam("strength") final int strength,
            @QueryParam("minLength") final int minLength,
            @QueryParam("maxLength") final int maxLength,
            @QueryParam("chars") final String chars,
            @QueryParam("noUser") final boolean noUser
    )
            throws PwmUnrecoverableException
    {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, SERVICE_PERMISSIONS, username);
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
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, SERVICE_PERMISSIONS, jsonInput.username);
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
        final RandomPasswordGenerator.RandomGeneratorConfig.RandomGeneratorConfigBuilder randomConfigBuilder
                = RandomPasswordGenerator.RandomGeneratorConfig.builder();
        if (jsonInput.strength > 0 && jsonInput.strength <= 100) {
            randomConfigBuilder.minimumStrength(jsonInput.strength);
        }
        if (jsonInput.minLength > 0 && jsonInput.minLength <= 100 * 1024) {
            randomConfigBuilder.minimumLength(jsonInput.minLength);
        }
        if (jsonInput.maxLength > 0 && jsonInput.maxLength <= 100 * 1024) {
            randomConfigBuilder.maximumLength(jsonInput.maxLength);
        }
        if (jsonInput.chars != null) {
            final List<String> charValues = new ArrayList<>();
            for (int i = 0; i < jsonInput.chars.length(); i++) {
                charValues.add(String.valueOf(jsonInput.chars.charAt(i)));
            }
            randomConfigBuilder.seedlistPhrases(charValues);
        }

        if (!jsonInput.noUser && restRequestBean.getPwmSession().isAuthenticated()) {
            if (jsonInput.username != null && !jsonInput.username.isEmpty()) {
                final UserIdentity userIdentity = restRequestBean.getUserIdentity();
                final HelpdeskProfile helpdeskProfile = restRequestBean.getPwmSession().getSessionManager().getHelpdeskProfile(restRequestBean.getPwmApplication());
                final boolean useProxy = helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY);

                final ChaiUser theUser = useProxy
                        ? restRequestBean.getPwmApplication().getProxiedChaiUser(restRequestBean.getUserIdentity())
                        : restRequestBean.getPwmSession().getSessionManager().getActor(restRequestBean.getPwmApplication(),userIdentity);

                randomConfigBuilder.passwordPolicy(PasswordUtility.readPasswordPolicyForUser(
                        restRequestBean.getPwmApplication(),
                        restRequestBean.getPwmSession().getLabel(),
                        restRequestBean.getUserIdentity(),
                        theUser,
                        restRequestBean.getPwmSession().getSessionStateBean().getLocale()));
            } else {
                randomConfigBuilder.passwordPolicy(restRequestBean.getPwmSession().getUserInfo().getPasswordPolicy());
            }
        } else {
            final Configuration config  = restRequestBean.getPwmApplication().getConfig();
            randomConfigBuilder.passwordPolicy(config.getPasswordPolicy(
                    config.getPasswordProfileIDs().iterator().next(),
                    restRequestBean.getPwmSession().getSessionStateBean().getLocale()));
        }

        final RandomPasswordGenerator.RandomGeneratorConfig randomConfig = randomConfigBuilder.build();
        final PasswordData randomPassword = RandomPasswordGenerator.createRandomPassword(restRequestBean.getPwmSession().getLabel(), randomConfig, restRequestBean.getPwmApplication());
        final JsonOutput outputMap = new JsonOutput();
        outputMap.password = randomPassword.getStringValue();

        if (restRequestBean.isExternal()) {
            StatisticsManager.incrementStat(restRequestBean.getPwmApplication(), Statistic.REST_SETPASSWORD);
        }

        return outputMap;
    }
}

