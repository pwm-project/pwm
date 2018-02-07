/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.Data;
import password.pwm.PwmConstants;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.i18n.Message;
import password.pwm.util.operations.OtpService;
import password.pwm.util.operations.otp.OTPUserRecord;
import password.pwm.ws.server.RestMethodHandler;
import password.pwm.ws.server.RestRequest;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServlet;
import password.pwm.ws.server.RestWebServer;

import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.Serializable;

@WebServlet(
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + PwmConstants.URL_PREFIX_REST + "/verifyotp",
        }
)
@RestWebServer( webService = WebServiceUsage.VerifyOtp, requireAuthentication = true )
public class RestVerifyOtpServer extends RestServlet
{

    @Data
    public static class JsonPutOtpInput implements Serializable
    {
        public String token;
        public String username;
    }

    @Override
    public void preCheckRequest( final RestRequest request ) throws PwmUnrecoverableException
    {
    }

    @RestMethodHandler( method = HttpMethod.POST, consumes = HttpContentType.json, produces = HttpContentType.json )
    public RestResultBean doSetOtpDataJson( final RestRequest restRequest )
            throws IOException, PwmUnrecoverableException
    {
        final RestVerifyOtpServer.JsonPutOtpInput jsonInput = deserializeJsonBody( restRequest, JsonPutOtpInput.class );

        final TargetUserIdentity targetUserIdentity = resolveRequestedUsername( restRequest, jsonInput.getUsername() );

        try
        {
            final OtpService otpService = restRequest.getPwmApplication().getOtpService();
            final OTPUserRecord otpUserRecord = otpService.readOTPUserConfiguration( restRequest.getSessionLabel(), targetUserIdentity.getUserIdentity() );

            final boolean verified = otpUserRecord != null && otpService.validateToken(
                    restRequest.getSessionLabel(),
                    targetUserIdentity.getUserIdentity(),
                    otpUserRecord,
                    jsonInput.getToken(),
                    false
            );

            return RestResultBean.forSuccessMessage( verified, restRequest, Message.Success_Unknown );
        }
        catch ( ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
        catch ( PwmOperationalException e )
        {
            final String errorMsg = "unexpected error reading json input: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, errorMsg );
            return RestResultBean.fromError( restRequest, errorInformation );
        }

    }

}
