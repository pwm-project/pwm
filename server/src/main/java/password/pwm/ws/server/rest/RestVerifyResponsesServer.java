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
        catch ( ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

}

