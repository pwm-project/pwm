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

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Path("/randompassword")
public class RestRandomPasswordServer {
    @Context
    HttpServletRequest request;

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
    }

    // This method is called if TEXT_PLAIN is request
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String doPlainRandomPassword(
            @QueryParam("username") String username,
            @QueryParam("strength") int strength,
            @QueryParam("minLength") int minLength,
            @QueryParam("maxLength") int maxLength,
            @QueryParam("chars") String chars
    )
            throws PwmUnrecoverableException
    {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, false, username);
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

        try {
            return doOperation(restRequestBean, jsonInput).password;
        } catch (Exception e) {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMessage);
            RestServerHelper.handleNonJsonErrorResult(errorInformation);
            return null;
        }
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String doPostRandomPasswordForm(
            final @FormParam("username") String username,
            final @FormParam("strength") int strength,
            final @FormParam("maxLength") int maxLength,
            final @FormParam("minLength") int minLength,
            final @FormParam("chars") String chars
    )
            throws PwmUnrecoverableException
    {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, false, username);
        } catch (PwmUnrecoverableException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        }

        final JsonInput jsonInput = new JsonInput();
        jsonInput.username = username;
        jsonInput.strength = strength;
        jsonInput.maxLength = maxLength;
        jsonInput.minLength = minLength;
        jsonInput.chars = chars;

        try {
            final JsonOutput jsonOutput = doOperation(restRequestBean, jsonInput);
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(jsonOutput);
            return restResultBean.toJson();
        } catch (PwmException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        } catch (Exception e) {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMessage);
            return RestServerHelper.outputJsonErrorResult(errorInformation, request);
        }
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String doPostRandomPasswordJson(
            final JsonInput jsonInput
    )
            throws PwmUnrecoverableException
    {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, false, jsonInput.username);
        } catch (PwmUnrecoverableException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        }

        try {
            final JsonOutput jsonOutput = doOperation(restRequestBean, jsonInput);
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(jsonOutput);
            return restResultBean.toJson();
        } catch (PwmException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        } catch (Exception e) {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMessage);
            return RestServerHelper.outputJsonErrorResult(errorInformation, request);
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
            final List<String> charValues = new ArrayList<String>();
            for (int i = 0; i < jsonInput.chars.length(); i++) {
                charValues.add(String.valueOf(jsonInput.chars.charAt(i)));
            }
            randomConfig.setSeedlistPhrases(charValues);
        }

        if (restRequestBean.getPwmSession().getSessionStateBean().isAuthenticated()) {
            if (jsonInput.username != null && jsonInput.username.length() > 0) {
                final ChaiUser theUser = ChaiFactory.createChaiUser(jsonInput.username, restRequestBean.getPwmSession().getSessionManager().getChaiProvider());
                randomConfig.setPasswordPolicy(PasswordUtility.readPasswordPolicyForUser(
                        restRequestBean.getPwmApplication(),
                        restRequestBean.getPwmSession(),
                        theUser,
                        restRequestBean.getPwmSession().getSessionStateBean().getLocale()));
            } else {
                randomConfig.setPasswordPolicy(restRequestBean.getPwmSession().getUserInfoBean().getPasswordPolicy());
            }
        } else {
            randomConfig.setPasswordPolicy(restRequestBean.getPwmApplication().getConfig().getGlobalPasswordPolicy(restRequestBean.getPwmSession().getSessionStateBean().getLocale()));
        }

        final String randomPassword = RandomPasswordGenerator.createRandomPassword(restRequestBean.getPwmSession(), randomConfig, restRequestBean.getPwmApplication());
        final JsonOutput outputMap = new JsonOutput();
        outputMap.password = randomPassword;

        if (restRequestBean.isExternal()) {
            restRequestBean.getPwmApplication().getStatisticsManager().incrementValue(Statistic.REST_RANDOMPASSWORD);
        }

        return outputMap;
    }
}

