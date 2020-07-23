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

import lombok.Value;
import password.pwm.PwmConstants;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.i18n.Message;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.PasswordData;
import password.pwm.util.password.RandomPasswordGenerator;
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

@WebServlet(
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + PwmConstants.URL_PREFIX_REST + "/setpassword"
        }
)
@RestWebServer( webService = WebServiceUsage.SetPassword )
public class RestSetPasswordServer extends RestServlet
{

    public static final PwmLogger LOGGER = PwmLogger.forClass( RestSetPasswordServer.class );

    private static final String FIELD_RANDOM = "random";
    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_PASSWORD = "password";

    @Value
    public static class JsonInputData implements Serializable
    {
        private String username;
        private String password;
        private boolean random;
    }

    @Override
    public void preCheckRequest( final RestRequest request ) throws PwmUnrecoverableException
    {

    }

    @RestMethodHandler( method = HttpMethod.POST, consumes = HttpContentType.form, produces = HttpContentType.json )
    public RestResultBean doPostSetPasswordForm(
            final RestRequest restRequest
    )
            throws PwmUnrecoverableException
    {
        final JsonInputData jsonInputData = new JsonInputData(
                restRequest.readParameterAsString( FIELD_USERNAME, PwmHttpRequestWrapper.Flag.BypassValidation ),
                restRequest.readParameterAsString( FIELD_PASSWORD, PwmHttpRequestWrapper.Flag.BypassValidation ),
                restRequest.readParameterAsBoolean( FIELD_RANDOM )
        );

        return doSetPassword( restRequest, jsonInputData );
    }

    @RestMethodHandler( method = HttpMethod.POST, consumes = HttpContentType.json, produces = HttpContentType.json )
    public RestResultBean doPostSetPasswordJson( final RestRequest restRequest )
            throws IOException, PwmUnrecoverableException
    {
        final JsonInputData jsonInput;
        {
            final JsonInputData jsonBody = RestUtility.deserializeJsonBody( restRequest, JsonInputData.class, RestUtility.Flag.AllowNullReturn );

            jsonInput = new JsonInputData(
                    RestUtility.readValueFromJsonAndParam(
                            jsonBody == null ? null : jsonBody.getUsername(),
                            restRequest.readParameterAsString( FIELD_USERNAME ),
                            FIELD_USERNAME,
                            RestUtility.ReadValueFlag.optional
                    ),
                    RestUtility.readValueFromJsonAndParam(
                            jsonBody == null ? null : jsonBody.getPassword(),
                            restRequest.readParameterAsString( FIELD_PASSWORD ),
                            FIELD_PASSWORD,
                            RestUtility.ReadValueFlag.optional
                    ),
                    Boolean.parseBoolean( RestUtility.readValueFromJsonAndParam(
                            jsonBody == null ? "" : String.valueOf( jsonBody.isRandom() ),
                            restRequest.readParameterAsString( FIELD_RANDOM ),
                            FIELD_RANDOM,
                            RestUtility.ReadValueFlag.optional
                    ) )
            );
        }

        return doSetPassword( restRequest, jsonInput );
    }

    private static RestResultBean doSetPassword(
            final RestRequest restRequest,
            final JsonInputData jsonInputData

    )
    {
        final String password = jsonInputData.getPassword();
        final boolean random = jsonInputData.isRandom();

        if ( ( password == null || password.length() < 1 ) && !random )
        {
            final String errorMessage = "field '" + FIELD_PASSWORD + "' must have a value or field '" + FIELD_RANDOM + "' must be set to true";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, errorMessage, new String[]
                    {
                            FIELD_PASSWORD,
                    }
            );
            return RestResultBean.fromError( restRequest, errorInformation );
        }

        if ( ( password != null && password.length() > 0 ) && random )
        {
            final String errorMessage = "field '" + FIELD_PASSWORD + "' cannot have a value or field '" + FIELD_RANDOM + "' must be set to true";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, errorMessage, new String[]
                    {
                            FIELD_PASSWORD,
                    }
            );
            return RestResultBean.fromError( restRequest, errorInformation );
        }

        try
        {
            final TargetUserIdentity targetUserIdentity = RestUtility.resolveRequestedUsername( restRequest, jsonInputData.username );

            final PasswordData newPassword;
            if ( random )
            {
                final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(
                        restRequest.getPwmApplication(),
                        restRequest.getSessionLabel(),
                        targetUserIdentity.getUserIdentity(),
                        targetUserIdentity.getChaiUser(),
                        restRequest.getLocale()
                );
                newPassword = RandomPasswordGenerator.createRandomPassword(
                        restRequest.getSessionLabel(),
                        passwordPolicy, restRequest.getPwmApplication()
                );
            }
            else
            {
                newPassword = new PasswordData( password );
            }

            final PasswordData oldPassword;
            if ( targetUserIdentity.isSelf() )
            {
                final BasicAuthInfo basicAuthInfo = BasicAuthInfo.parseAuthHeader( restRequest.getPwmApplication(), restRequest.getHttpServletRequest() );
                oldPassword = basicAuthInfo == null ? null : basicAuthInfo.getPassword();
            }
            else
            {
                oldPassword = null;
            }

            final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxy(
                    restRequest.getPwmApplication(),
                    restRequest.getSessionLabel(),
                    targetUserIdentity.getUserIdentity(),
                    restRequest.getLocale()
            );

            PasswordUtility.setPassword(
                    restRequest.getPwmApplication(),
                    restRequest.getSessionLabel(),
                    targetUserIdentity.getChaiProvider(),
                    userInfo,
                    oldPassword,
                    newPassword
            );

            StatisticsManager.incrementStat( restRequest.getPwmApplication(), Statistic.REST_SETPASSWORD );
            final JsonInputData jsonResultData = new JsonInputData( targetUserIdentity.getUserIdentity().toDelimitedKey(), null, random );
            return RestResultBean.forSuccessMessage( jsonResultData, restRequest, Message.Success_PasswordChange );
        }
        catch ( final PwmException e )
        {
            LOGGER.error( () -> "error during set password REST operation: " + e.getMessage() );
            return RestResultBean.fromError( restRequest, e.getErrorInformation() );
        }
        catch ( final Exception e )
        {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMessage );
            LOGGER.error( () -> "error during set password REST operation: " + e.getMessage(), e );
            return RestResultBean.fromError( restRequest, errorInformation );
        }
    }
}
