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

import lombok.AllArgsConstructor;
import lombok.Getter;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.secure.SecureService;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;
import password.pwm.ws.server.ServicePermissions;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Path("/signing")
public class RestSigningServer extends AbstractRestServer {
    private static final ServicePermissions SERVICE_PERMISSIONS = ServicePermissions.builder()
            .adminOnly(false)
            .authRequired(false)
            .blockExternal(false)
            .build();


    @POST
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/form")
    public Response doGetProfileJsonData(
            final Map<String,String> inputFormData

    )
            throws PwmUnrecoverableException
    {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, SERVICE_PERMISSIONS, null);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        try {
            if (inputFormData != null) {
                final SecureService securityService = restRequestBean.getPwmApplication().getSecureService();
                final SignedFormData signedFormData = new SignedFormData(Instant.now(), inputFormData);
                final String signedValue = securityService.encryptObjectToString(signedFormData);
                final RestResultBean restResultBean = new RestResultBean(signedValue);
                return restResultBean.asJsonResponse();
            }
            return RestResultBean.fromError(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"no json form in body")).asJsonResponse();
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        } catch (Exception e) {
            final String errorMsg = "unexpected error building json response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return RestResultBean.fromError(errorInformation).asJsonResponse();
        }
    }

    public static Map<String,String> readSignedFormValue(final PwmApplication pwmApplication, final String input) throws PwmUnrecoverableException
    {
        final Integer maxAgeSeconds = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.WS_REST_SERVER_SIGNING_FORM_TIMEOUT_SECONDS));
        final TimeDuration maxAge = new TimeDuration(maxAgeSeconds, TimeUnit.SECONDS);
        final SignedFormData signedFormData = pwmApplication.getSecureService().decryptObject(input, SignedFormData.class);
        if (signedFormData != null) {
            if (signedFormData.getTimestamp() != null) {
                if (TimeDuration.fromCurrent(signedFormData.getTimestamp()).isLongerThan(maxAge)) {
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_SECURITY_VIOLATION,"signedForm data is too old"));
                }

                return signedFormData.getFormData();
            }
        }
        return null;
    }

    @Getter
    @AllArgsConstructor
    private static class SignedFormData implements Serializable  {
        private Instant timestamp;
        private Map<String,String> formData;
    }
}
