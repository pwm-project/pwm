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
import lombok.NoArgsConstructor;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.password.PasswordUtility;
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

@WebServlet(
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + PwmConstants.URL_PREFIX_REST + "/checkpassword"
        }
)
@RestWebServer( webService = WebServiceUsage.CheckPassword )
public class RestCheckPasswordServer extends RestServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( RestCheckPasswordServer.class );

    private static final String FIELD_PASSWORD_1 = "password1";
    private static final String FIELD_PASSWORD_2 = "password2";
    private static final String FIELD_USERNAME = "username";

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class JsonInput implements Serializable
    {
        public String password1;
        public String password2;
        public String username;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class JsonOutput implements Serializable
    {
        public int version;
        public int strength;
        public PasswordUtility.PasswordCheckInfo.MatchStatus match;
        public String message;
        public boolean passed;
        public int errorCode;

        public static JsonOutput fromPasswordCheckInfo(
                final PasswordUtility.PasswordCheckInfo checkInfo
        )
        {
            final JsonOutput outputMap = new JsonOutput();
            outputMap.version = 2;
            outputMap.strength = checkInfo.getStrength();
            outputMap.match = checkInfo.getMatch();
            outputMap.message = checkInfo.getMessage();
            outputMap.passed = checkInfo.isPassed();
            outputMap.errorCode = checkInfo.getErrorCode();
            return outputMap;
        }
    }

    @Override
    public void preCheckRequest( final RestRequest request ) throws PwmUnrecoverableException
    {

    }

    @RestMethodHandler( method = HttpMethod.POST, consumes = HttpContentType.form, produces = HttpContentType.json )
    public RestResultBean doPasswordRuleCheckFormPost( final RestRequest restRequest )
            throws PwmUnrecoverableException
    {
        final JsonInput jsonInput = new JsonInput(
                restRequest.readParameterAsString( FIELD_PASSWORD_1, PwmHttpRequestWrapper.Flag.BypassValidation ),
                restRequest.readParameterAsString( FIELD_PASSWORD_2, PwmHttpRequestWrapper.Flag.BypassValidation ),
                restRequest.readParameterAsString( FIELD_USERNAME, PwmHttpRequestWrapper.Flag.BypassValidation )
        );
        return doOperation( restRequest, jsonInput );
    }

    @RestMethodHandler( method = HttpMethod.POST, consumes = HttpContentType.json, produces = HttpContentType.json )
    public RestResultBean doPasswordRuleCheckJsonPost( final RestRequest restRequest )
            throws PwmUnrecoverableException, IOException
    {

        final JsonInput jsonInput;
        {



            final JsonInput jsonBody = RestUtility.deserializeJsonBody( restRequest, JsonInput.class, RestUtility.Flag.AllowNullReturn );

            jsonInput = new JsonInput(
                    RestUtility.readValueFromJsonAndParam(
                            jsonBody == null ? null : jsonBody.getPassword1(),
                            restRequest.readParameterAsString( FIELD_PASSWORD_1 ),
                            FIELD_PASSWORD_1
                    ),
                    RestUtility.readValueFromJsonAndParam(
                            jsonBody == null ? null : jsonBody.getPassword2(),
                            restRequest.readParameterAsString( FIELD_PASSWORD_2 ),
                            FIELD_PASSWORD_2
                    ),
                    RestUtility.readValueFromJsonAndParam(
                            jsonBody == null ? null : jsonBody.getUsername(),
                            restRequest.readParameterAsString( FIELD_USERNAME ),
                            FIELD_USERNAME,
                            RestUtility.ReadValueFlag.optional
                    )
            );
        }

        return doOperation( restRequest, jsonInput );
    }

    public RestResultBean doOperation( final RestRequest restRequest, final JsonInput jsonInput )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        if ( StringUtil.isEmpty( jsonInput.getPassword1() ) )
        {
            final String errorMessage = "missing field '" + FIELD_PASSWORD_1 + "'";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_FIELD_REQUIRED, errorMessage, new String[]
                    {
                            FIELD_PASSWORD_1,
                    }
                    );
            return RestResultBean.fromError( restRequest, errorInformation );
        }

        try
        {

            final TargetUserIdentity targetUserIdentity = RestUtility.resolveRequestedUsername( restRequest, jsonInput.getUsername() );
            final UserInfo userInfo = UserInfoFactory.newUserInfo(
                    restRequest.getPwmApplication(),
                    restRequest.getSessionLabel(),
                    restRequest.getLocale(),
                    targetUserIdentity.getUserIdentity(),
                    targetUserIdentity.getChaiProvider()
            );

            final PasswordCheckRequest checkRequest = new PasswordCheckRequest(
                    targetUserIdentity.getUserIdentity(),
                    StringUtil.isEmpty( jsonInput.getPassword1() ) ? null : new PasswordData( jsonInput.getPassword1() ),
                    StringUtil.isEmpty( jsonInput.getPassword2() ) ? null : new PasswordData( jsonInput.getPassword2() ),
                    userInfo
            );

            restRequest.getPwmApplication().getStatisticsManager().incrementValue( Statistic.REST_CHECKPASSWORD );

            final PasswordUtility.PasswordCheckInfo passwordCheckInfo = PasswordUtility.checkEnteredPassword(
                    restRequest.getPwmApplication(),
                    restRequest.getLocale(),
                    targetUserIdentity.getChaiUser(),
                    checkRequest.getUserInfo(),
                    null,
                    checkRequest.getPassword1(),
                    checkRequest.getPassword2()
            );

            final JsonOutput jsonOutput = JsonOutput.fromPasswordCheckInfo( passwordCheckInfo );
            final RestResultBean restResultBean = RestResultBean.withData( jsonOutput );
            final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );
            LOGGER.trace( restRequest.getSessionLabel(), () -> "REST /checkpassword response (" + timeDuration.asCompactString() + "): " + JsonUtil.serialize( jsonOutput ) );
            return restResultBean;
        }
        catch ( final PwmException e )
        {
            LOGGER.debug( restRequest.getSessionLabel(), () -> "REST /checkpassword error during execution: " + e.getMessage() );
            return RestResultBean.fromError( restRequest, e.getErrorInformation() );
        }
        catch ( final Exception e )
        {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMessage );
            LOGGER.error( restRequest.getSessionLabel(), () -> errorInformation.toDebugStr(), e );
            return RestResultBean.fromError( restRequest, errorInformation );
        }
    }

    @Getter
    @AllArgsConstructor
    public static class PasswordCheckRequest
    {
        final UserIdentity userIdentity;
        final PasswordData password1;
        final PasswordData password2;
        final UserInfo userInfo;
    }
}

