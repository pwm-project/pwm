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

import com.novell.ldapchai.cr.ChaiChallenge;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.cr.bean.ChallengeBean;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.Data;
import password.pwm.PwmConstants;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.i18n.Message;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestMethodHandler;
import password.pwm.ws.server.RestRequest;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServlet;
import password.pwm.ws.server.RestUtility;
import password.pwm.ws.server.RestWebServer;

import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@WebServlet(
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + PwmConstants.URL_PREFIX_REST + "/verifyresponses",
        }
)
@RestWebServer( webService = WebServiceUsage.VerifyResponses )
public class RestVerifyResponsesServer extends RestServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( RestVerifyResponsesServer.class );

    @Data
    public static class JsonPutChallengesInput implements Serializable
    {
        public List<ChallengeBean> challenges;
        public String username;

        public Map<Challenge, String> toCrMap( )
        {
            final Map<Challenge, String> crMap = new LinkedHashMap<>();
            if ( challenges != null )
            {
                for ( final ChallengeBean challengeBean : challenges )
                {
                    if ( challengeBean.getAnswer() == null )
                    {
                        throw new IllegalArgumentException( "json challenge object must include an answer object" );
                    }
                    if ( challengeBean.getAnswer().getAnswerText() == null )
                    {
                        throw new IllegalArgumentException( "json answer object must include answerText property" );
                    }
                    final String answerText = challengeBean.getAnswer().getAnswerText();
                    final Challenge challenge = ChaiChallenge.fromChallengeBean( challengeBean );
                    crMap.put( challenge, answerText );
                }
            }

            return crMap;
        }
    }

    @Override
    public void preCheckRequest( final RestRequest request ) throws PwmUnrecoverableException
    {
    }

    @RestMethodHandler( method = HttpMethod.POST, consumes = HttpContentType.json, produces = HttpContentType.json )
    public RestResultBean doSetChallengeDataJson( final RestRequest restRequest ) throws IOException, PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        final JsonPutChallengesInput jsonInput = RestUtility.deserializeJsonBody( restRequest, JsonPutChallengesInput.class );

        final String username = RestUtility.readValueFromJsonAndParam(
                jsonInput.getUsername(),
                restRequest.readParameterAsString( "username", PwmHttpRequestWrapper.Flag.BypassValidation ),
                "username" );

        final TargetUserIdentity targetUserIdentity = RestUtility.resolveRequestedUsername( restRequest, username );

        LOGGER.debug( restRequest.getSessionLabel(), () -> "beginning /verifyresponses REST service against "
                + ( targetUserIdentity.isSelf() ? "self" : targetUserIdentity.getUserIdentity().toDisplayString() ) );

        try
        {
            final ResponseSet responseSet = restRequest.getPwmApplication().getCrService().readUserResponseSet(
                    restRequest.getSessionLabel(),
                    targetUserIdentity.getUserIdentity(),
                    targetUserIdentity.getChaiUser()
            );

            final boolean verified = responseSet != null && responseSet.test( jsonInput.toCrMap() );

            final RestResultBean restResultBean = RestResultBean.forSuccessMessage( verified, restRequest, Message.Success_Unknown );

            LOGGER.debug( restRequest.getSessionLabel(), () -> "completed /verifyresponses REST service in "
                    + TimeDuration.compactFromCurrent( startTime )
                    + ", response: " + JsonUtil.serialize( restResultBean ) );

            return restResultBean;

        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

}

