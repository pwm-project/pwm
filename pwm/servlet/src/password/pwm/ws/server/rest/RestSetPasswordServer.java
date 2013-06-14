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
import password.pwm.Permission;
import password.pwm.PwmPasswordPolicy;
import password.pwm.bean.UserInfoBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.event.AuditEvent;
import password.pwm.i18n.Message;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.operations.UserDataReader;
import password.pwm.util.operations.UserStatusHelper;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.Serializable;

@Path("/setpassword")
public class RestSetPasswordServer {
    @Context
    HttpServletRequest request;
    public static class JsonInputData implements Serializable
    {
        public String username;
        public String password;
        public boolean random;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String doPostSetPasswordForm(
            final @FormParam("username") String username,
            final @FormParam("password") String password,
            final @FormParam("random") boolean random
    )
            throws PwmUnrecoverableException
    {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, true, username);
        } catch (PwmUnrecoverableException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        }

        return doSetPassword(restRequestBean, request, password, random);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String doPostSetPasswordJson(
            final JsonInputData jsonInputData
    )
            throws PwmUnrecoverableException
    {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, true, jsonInputData.username);
        } catch (PwmUnrecoverableException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        }

        return doSetPassword(restRequestBean, request, jsonInputData.password, jsonInputData.random);
    }

    private static String doSetPassword(
            final RestRequestBean restRequestBean,
            final HttpServletRequest request,
            final String password,
            final boolean random

    )
    {
        if ((password == null || password.length() < 1) && !random) {
            final String errorMessage = "field 'password' must have a value or field 'random' must be set to true";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER, errorMessage, new String[]{"password"});
            return RestServerHelper.outputJsonErrorResult(errorInformation, request);
        }

        if ((password != null && password.length() > 0) && random) {
            final String errorMessage = "field 'password' cannot have a value or field 'random' must be set to true";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER, errorMessage, new String[]{"password"});
            return RestServerHelper.outputJsonErrorResult(errorInformation, request);
        }

        try {
            if (!Permission.checkPermission(Permission.CHANGE_PASSWORD, restRequestBean.getPwmSession(), restRequestBean.getPwmApplication())) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"actor does not have required permission"));
            }

            final JsonInputData jsonResultData = new JsonInputData();
            jsonResultData.random = random;
            if (restRequestBean.getUserDN() != null) {
                final ChaiUser chaiUser = ChaiFactory.createChaiUser(restRequestBean.getUserDN(), restRequestBean.getPwmSession().getSessionManager().getChaiProvider());
                final String newPassword;
                if (random) {
                    final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(restRequestBean.getPwmApplication(), restRequestBean.getPwmSession(), chaiUser, restRequestBean.getPwmSession().getSessionStateBean().getLocale());
                    newPassword = RandomPasswordGenerator.createRandomPassword(restRequestBean.getPwmSession(), passwordPolicy, restRequestBean.getPwmApplication());
                } else {
                    newPassword = password;
                }
                PasswordUtility.helpdeskSetUserPassword(restRequestBean.getPwmSession(), chaiUser, restRequestBean.getPwmApplication(), newPassword);
                final UserInfoBean uiBean = new UserInfoBean();
                UserStatusHelper.populateUserInfoBean(
                        restRequestBean.getPwmSession(),
                        uiBean,
                        restRequestBean.getPwmApplication(),
                        restRequestBean.getPwmSession().getSessionStateBean().getLocale(),
                        restRequestBean.getUserDN(),
                        newPassword,
                        restRequestBean.getPwmSession().getSessionManager().getChaiProvider()
                );
                PasswordUtility.sendNewPassword(
                        uiBean,
                        restRequestBean.getPwmApplication(),
                        new UserDataReader(chaiUser),
                        newPassword,
                        restRequestBean.getPwmSession().getSessionStateBean().getLocale()
                );
                jsonResultData.password = null;
                jsonResultData.username = restRequestBean.getUserDN();
            } else {
                final String newPassword;
                if (random) {
                    newPassword = RandomPasswordGenerator.createRandomPassword(restRequestBean.getPwmSession(), restRequestBean.getPwmApplication());
                } else {
                    newPassword = password;
                }
                PasswordUtility.setUserPassword(restRequestBean.getPwmSession(), restRequestBean.getPwmApplication(), newPassword);
                PasswordUtility.sendNewPassword(
                        restRequestBean.getPwmSession().getUserInfoBean(),
                        restRequestBean.getPwmApplication(),
                        restRequestBean.getPwmSession().getSessionManager().getUserDataReader(),
                        newPassword,
                        restRequestBean.getPwmSession().getSessionStateBean().getLocale()
                );
                restRequestBean.getPwmApplication().getAuditManager().submitAuditRecord(AuditEvent.CHANGE_PASSWORD, restRequestBean.getPwmSession().getUserInfoBean(),restRequestBean.getPwmSession());
                jsonResultData.password = null;
                jsonResultData.username = restRequestBean.getPwmSession().getUserInfoBean().getUserDN();
            }
            if (restRequestBean.isExternal()) {
                restRequestBean.getPwmApplication().getStatisticsManager().incrementValue(Statistic.REST_SETPASSWORD);
            }
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setError(false);
            restResultBean.setData(jsonResultData);
            restResultBean.setSuccessMessage(Message.getLocalizedMessage(
                    restRequestBean.getPwmSession().getSessionStateBean().getLocale(),
                    Message.SUCCESS_PASSWORDCHANGE,
                    restRequestBean.getPwmApplication().getConfig()));
            return restResultBean.toJson();
        } catch (PwmException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        } catch (Exception e) {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMessage);
            return RestServerHelper.outputJsonErrorResult(errorInformation, request);
        }
    }
}
