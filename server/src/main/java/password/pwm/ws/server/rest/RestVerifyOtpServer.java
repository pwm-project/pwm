/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.ws.server.rest;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.Value;
import password.pwm.PwmConstants;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.i18n.Message;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.operations.OtpService;
import password.pwm.util.operations.otp.OTPUserRecord;
import password.pwm.ws.server.RestMethodHandler;
import password.pwm.ws.server.RestRequest;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServlet;
import password.pwm.ws.server.RestUtility;
import password.pwm.ws.server.RestWebServer;

import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.Serializable;

@WebServlet(
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + PwmConstants.URL_PREFIX_REST + "/verifyotp",
        }
)
@RestWebServer( webService = WebServiceUsage.VerifyOtp )
public class RestVerifyOtpServer extends RestServlet
{

    @Value
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
        final RestVerifyOtpServer.JsonPutOtpInput jsonInput;
        {
            final RestVerifyOtpServer.JsonPutOtpInput jsonBody = RestUtility.deserializeJsonBody(
                    restRequest,
                    RestVerifyOtpServer.JsonPutOtpInput.class,
                    RestUtility.Flag.AllowNullReturn );

            jsonInput = new RestVerifyOtpServer.JsonPutOtpInput(
                    RestUtility.readValueFromJsonAndParam(
                            jsonBody == null ? null : jsonBody.getToken(),
                            restRequest.readParameterAsString( "token" ),
                            "token"
                    ),
                    RestUtility.readValueFromJsonAndParam(
                            jsonBody == null ? null : jsonBody.getUsername(),
                            restRequest.readParameterAsString( "username" ),
                            "username"
                    )
            );
        }

        final TargetUserIdentity targetUserIdentity = RestUtility.resolveRequestedUsername( restRequest, jsonInput.getUsername() );

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

            StatisticsManager.incrementStat( restRequest.getPwmApplication(), Statistic.REST_VERIFYOTP );
            return RestResultBean.forSuccessMessage( verified, restRequest, Message.Success_Unknown );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
        catch ( final PwmOperationalException e )
        {
            final String errorMsg = "unexpected error reading json input: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            return RestResultBean.fromError( restRequest, errorInformation );
        }

    }

}
