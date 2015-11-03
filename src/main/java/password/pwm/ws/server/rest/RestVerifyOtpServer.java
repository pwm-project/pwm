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
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.Permission;
import password.pwm.bean.UserIdentity;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Message;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.util.operations.OtpService;
import password.pwm.util.otp.OTPUserRecord;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;
import password.pwm.ws.server.ServicePermissions;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.Serializable;
import java.net.URISyntaxException;

@Path("/verifyotp")
public class RestVerifyOtpServer extends AbstractRestServer {

    public static class JsonPutOtpInput implements Serializable {
        public String token;
        public String username;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response doHtmlRedirect() throws URISyntaxException {
        return RestServerHelper.doHtmlRedirect();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doSetOtpDataJson(final RestVerifyOtpServer.JsonPutOtpInput jsonInput) {
        final RestRequestBean restRequestBean;
        try {
            final ServicePermissions servicePermissions = new ServicePermissions();
            servicePermissions.setAdminOnly(false);
            servicePermissions.setAuthRequired(true);
            servicePermissions.setBlockExternal(true);
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, servicePermissions, jsonInput.username);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }
        try {
            if (!restRequestBean.getPwmSession().getSessionManager().checkPermission(restRequestBean.getPwmApplication(), Permission.SETUP_OTP_SECRET)) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"actor does not have required permission"));
            }

            final UserSearchEngine userSearchEngine = new UserSearchEngine(restRequestBean.getPwmApplication(), restRequestBean.getPwmSession().getLabel());
            UserIdentity userIdentity = restRequestBean.getUserIdentity();
            if (userIdentity == null) {
                ChaiUser chaiUser = restRequestBean.getPwmSession().getSessionManager().getActor(restRequestBean.getPwmApplication());
                userIdentity = userSearchEngine.resolveUsername(chaiUser.readUsername(), null, null);
            }

            final OtpService otpService = restRequestBean.getPwmApplication().getOtpService();
            final OTPUserRecord otpUserRecord = otpService.readOTPUserConfiguration(restRequestBean.getPwmSession().getLabel(),userIdentity);
            final boolean verified = otpUserRecord !=null && otpService.validateToken(
                    restRequestBean.getPwmSession(),
                    userIdentity,
                    otpUserRecord,
                    jsonInput.token,
                    false
            );
            final String successMsg = Message.Success_Unknown.getLocalizedMessage(request.getLocale(),restRequestBean.getPwmApplication().getConfig());
            RestResultBean resultBean = new RestResultBean();
            resultBean.setError(false);
            resultBean.setData(verified);
            resultBean.setSuccessMessage(successMsg);
            return resultBean.asJsonResponse();                    
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation(),restRequestBean).asJsonResponse();
        } catch (ChaiUnavailableException e) {
            final String errorMsg = "unexpected error reading json input: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return RestResultBean.fromError(errorInformation,restRequestBean).asJsonResponse();
        } catch (ChaiOperationException e) {
            final String errorMsg = "unexpected error reading json input: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return RestResultBean.fromError(errorInformation,restRequestBean).asJsonResponse();
        } catch (PwmOperationalException e) {
            final String errorMsg = "unexpected error reading json input: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return RestResultBean.fromError(errorInformation,restRequestBean).asJsonResponse();
        }
        
    }

}
