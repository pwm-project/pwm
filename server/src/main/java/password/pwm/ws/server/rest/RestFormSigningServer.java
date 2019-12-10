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

import lombok.AllArgsConstructor;
import lombok.Getter;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.secure.SecureService;
import password.pwm.ws.server.RestAuthenticationType;
import password.pwm.ws.server.RestMethodHandler;
import password.pwm.ws.server.RestRequest;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServlet;
import password.pwm.ws.server.RestWebServer;

import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

@WebServlet(
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + PwmConstants.URL_PREFIX_REST + "/signing/form",
        }
)
@RestWebServer( webService = WebServiceUsage.SigningForm )
public class RestFormSigningServer extends RestServlet
{

    @Override
    public void preCheckRequest( final RestRequest restRequest )
            throws PwmUnrecoverableException
    {
        if ( restRequest.getRestAuthentication().getType() == RestAuthenticationType.PUBLIC )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_AUTHENTICATION_REQUIRED );
        }
    }

    @RestMethodHandler( method = HttpMethod.POST, produces = HttpContentType.json, consumes = HttpContentType.json )
    private RestResultBean handleRestJsonPostRequest( final RestRequest restRequest )
            throws IOException, PwmUnrecoverableException
    {
        final Map<String, String> inputFormData = restRequest.readBodyAsJsonStringMap( PwmHttpRequestWrapper.Flag.BypassValidation );
        return handleRestPostRequest( restRequest, inputFormData );
    }

    @RestMethodHandler( method = HttpMethod.POST, produces = HttpContentType.json, consumes = HttpContentType.form )
    private RestResultBean handleRestFormPostRequest( final RestRequest restRequest )
            throws IOException, PwmUnrecoverableException
    {
        final Map<String, String> inputFormData = restRequest.readParametersAsMap();
        return handleRestPostRequest( restRequest, inputFormData );
    }

    private RestResultBean handleRestPostRequest(
            final RestRequest restRequest,
            final Map<String, String> inputFormData
    )
            throws PwmUnrecoverableException
    {
        try
        {
            if ( !JavaHelper.isEmpty( inputFormData ) )
            {
                final SecureService securityService = restRequest.getPwmApplication().getSecureService();
                final SignedFormData signedFormData = new SignedFormData( Instant.now(), inputFormData );
                final String signedValue = securityService.encryptObjectToString( signedFormData );
                StatisticsManager.incrementStat( restRequest.getPwmApplication(), Statistic.REST_SIGNING_FORM );
                return RestResultBean.withData( signedValue );
            }
            throw PwmUnrecoverableException.newException( PwmError.ERROR_MISSING_PARAMETER, "POST body should be a json object" );
        }
        catch ( final Exception e )
        {
            if ( e instanceof PwmUnrecoverableException )
            {
                throw e;
            }

            final String errorMsg = "unexpected error building json response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    public static Map<String, String> readSignedFormValue( final PwmApplication pwmApplication, final String input ) throws PwmUnrecoverableException
    {
        final Integer maxAgeSeconds = Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.WS_REST_SERVER_SIGNING_FORM_TIMEOUT_SECONDS ) );
        final TimeDuration maxAge = TimeDuration.of( maxAgeSeconds, TimeDuration.Unit.SECONDS );
        final SignedFormData signedFormData = pwmApplication.getSecureService().decryptObject( input, SignedFormData.class );
        if ( signedFormData != null )
        {
            if ( signedFormData.getTimestamp() != null )
            {
                if ( TimeDuration.fromCurrent( signedFormData.getTimestamp() ).isLongerThan( maxAge ) )
                {
                    throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SECURITY_VIOLATION, "signedForm data is too old" ) );
                }

                return signedFormData.getFormData();
            }
        }
        return null;
    }

    @Getter
    @AllArgsConstructor
    private static class SignedFormData implements Serializable
    {
        private Instant timestamp;
        private Map<String, String> formData;
    }
}
