/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.PasswordData;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.password.PasswordUtility;
import password.pwm.util.password.RandomGeneratorConfig;
import password.pwm.util.password.RandomGeneratorConfigRequest;
import password.pwm.ws.server.RestMethodHandler;
import password.pwm.ws.server.RestRequest;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServlet;
import password.pwm.ws.server.RestUtility;
import password.pwm.ws.server.RestWebServer;

import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@WebServlet(
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + PwmConstants.URL_PREFIX_REST + "/randompassword",
        }
)
@RestWebServer( webService = WebServiceUsage.RandomPassword )
public class RestRandomPasswordServer extends RestServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( RestRandomPasswordServer.class );

    public record JsonOutput(
            String password
    )
    {
    }

    public record JsonInput(
            String username,
            int strength,
            int minLength,
            int maxLength,
            String chars,
            boolean noUser
    )
    {
        static JsonInput fromRequestParameters( final RestRequest restRequest )
                throws PwmUnrecoverableException
        {
            return new JsonInput(
                    restRequest.readParameterAsString( "username", PwmHttpRequestWrapper.Flag.BypassValidation ),
                    restRequest.readParameterAsInt( "strength", 0 ),
                    restRequest.readParameterAsInt( "minLength", 0 ),
                    restRequest.readParameterAsInt( "maxLength", 0 ),
                    restRequest.readParameterAsString( "chars", PwmHttpRequestWrapper.Flag.BypassValidation ),
                    restRequest.readParameterAsBoolean( "noUser" ) );
        }
    }

    @Override
    public void preCheckRequest( final RestRequest request ) throws PwmUnrecoverableException
    {
    }

    @RestMethodHandler( method = HttpMethod.POST, consumes = HttpContentType.form, produces = HttpContentType.json )
    public RestResultBean<?> doPostRandomPasswordForm( final RestRequest restRequest )
            throws PwmUnrecoverableException
    {
        /* Check for parameter conflicts. */
        if ( restRequest.hasParameter( "username" )
                && ( restRequest.hasParameter( "strength" ) || restRequest.hasParameter( "minLength" ) || restRequest.hasParameter( "chars" ) ) )
        {
            LOGGER.error( restRequest.getSessionLabel(),
                    () -> "REST parameter conflict.  The username parameter cannot be specified if strength, minLength or chars parameters are specified." );
            final String errorMessage = "REST parameter conflict.  The username parameter cannot be specified if strength, minLength or chars parameters are specified.";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_REST_PARAMETER_CONFLICT, errorMessage );
            return RestResultBean.fromError( restRequest, errorInformation );
        }

        final JsonInput jsonInput = JsonInput.fromRequestParameters( restRequest );

        try
        {
            final JsonOutput jsonOutput = doOperation( restRequest, jsonInput );
            return RestResultBean.withData( jsonOutput, JsonOutput.class );
        }
        catch ( final PwmException e )
        {
            LOGGER.error( restRequest.getSessionLabel(), () -> "error executing rest-json random password request: " + e.getMessage(), e );
            return RestResultBean.fromError( restRequest, e.getErrorInformation() );
        }
        catch ( final Exception e )
        {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMessage );
            return RestResultBean.fromError( restRequest, errorInformation );
        }
    }

    // This method is called if TEXT_PLAIN is request
    @RestMethodHandler( method = HttpMethod.GET, produces = HttpContentType.plain )
    public RestResultBean doPlainRandomPassword( final RestRequest restRequest )
            throws PwmUnrecoverableException
    {
        /* Check for parameter conflicts. */
        if ( restRequest.hasParameter( "username" )
                && ( restRequest.hasParameter( "strength" ) || restRequest.hasParameter( "minLength" ) || restRequest.hasParameter( "chars" ) ) )
        {
            LOGGER.error( restRequest.getSessionLabel(),
                    () -> "REST parameter conflict.  The username parameter cannot be specified if strength, minLength or chars parameters are specified." );
            final String errorMessage = "REST parameter conflict.  The username parameter cannot be specified if strength, minLength or chars parameters are specified.";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_REST_PARAMETER_CONFLICT, errorMessage );
            return RestResultBean.fromError( restRequest, errorInformation );
        }

        final JsonInput jsonInput = JsonInput.fromRequestParameters( restRequest );

        try
        {
            final JsonOutput jsonOutput = doOperation( restRequest, jsonInput );
            return RestResultBean.withData( jsonOutput.password(), String.class );
        }
        catch ( final Exception e )
        {
            LOGGER.error( restRequest.getSessionLabel(), () -> "error executing rest-json random password request: " + e.getMessage(), e );
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMessage );
            return RestResultBean.fromError( restRequest, errorInformation );
        }
    }


    @RestMethodHandler( method = HttpMethod.POST, consumes = HttpContentType.json, produces = HttpContentType.json )
    public RestResultBean doPostRandomPasswordJson( final RestRequest restRequest )
            throws PwmUnrecoverableException, IOException
    {
        final JsonInput jsonInput = RestUtility.deserializeJsonBody( restRequest, JsonInput.class );

        try
        {
            final JsonOutput jsonOutput = doOperation( restRequest, jsonInput );
            return RestResultBean.withData( jsonOutput, JsonOutput.class );
        }
        catch ( final PwmException e )
        {
            LOGGER.error( restRequest.getSessionLabel(), () -> "error executing rest-form random password request: " + e.getMessage(), e );
            return RestResultBean.fromError( restRequest, e.getErrorInformation() );
        }
        catch ( final Exception e )
        {
            LOGGER.error( restRequest.getSessionLabel(), () -> "error executing rest-form random password request: " + e.getMessage(), e );
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMessage );
            return RestResultBean.fromError( restRequest, errorInformation );
        }
    }

    private static JsonOutput doOperation(
            final RestRequest restRequest,
            final JsonInput jsonInput
    )
            throws PwmUnrecoverableException
    {
        final PwmPasswordPolicy pwmPasswordPolicy;

        if ( jsonInput.noUser() || StringUtil.isEmpty( jsonInput.username() ) )
        {
            pwmPasswordPolicy = PwmPasswordPolicy.defaultPolicy();
        }
        else
        {
            final TargetUserIdentity targetUserIdentity = RestUtility.resolveRequestedUsername( restRequest, jsonInput.username() );
            pwmPasswordPolicy = PasswordUtility.readPasswordPolicyForUser(
                    restRequest.getDomain(),
                    restRequest.getSessionLabel(),
                    targetUserIdentity.userIdentity(),
                    targetUserIdentity.getChaiUser() );
        }

        final RandomGeneratorConfig randomConfig = jsonInputToRandomConfig( jsonInput, restRequest.getDomain(), pwmPasswordPolicy );
        final PasswordData randomPassword = PasswordUtility.generateRandom(
                restRequest.getSessionLabel(),
                randomConfig,
                restRequest.getDomain() );
        final JsonOutput outputMap = new JsonOutput( randomPassword.getStringValue() );

        StatisticsClient.incrementStat( restRequest.getDomain(), Statistic.REST_RANDOMPASSWORD );

        return outputMap;
    }

    public static RandomGeneratorConfig jsonInputToRandomConfig(
            final JsonInput jsonInput,
            final PwmDomain pwmDomain,
            final PwmPasswordPolicy pwmPasswordPolicy
    )
            throws PwmUnrecoverableException
    {
        final RandomGeneratorConfigRequest.RandomGeneratorConfigRequestBuilder randomConfigBuilder
                = RandomGeneratorConfigRequest.builder();

        if ( jsonInput.strength() > 0 && jsonInput.strength() <= 100 )
        {
            randomConfigBuilder.minimumStrength( jsonInput.strength() );
        }
        if ( jsonInput.minLength() > 0 && jsonInput.minLength() <= 100 * 1024 )
        {
            randomConfigBuilder.minimumLength( jsonInput.minLength() );
        }
        if ( jsonInput.maxLength() > 0 && jsonInput.maxLength() <= 100 * 1024 )
        {
            randomConfigBuilder.maximumLength( jsonInput.maxLength() );
        }
        if ( jsonInput.chars() != null )
        {
            final String inputChars = jsonInput.chars();
            final List<String> charValues = new ArrayList<>( inputChars.length() );
            for ( int i = 0; i < inputChars.length(); i++ )
            {
                charValues.add( String.valueOf( inputChars.charAt( i ) ) );
            }
            randomConfigBuilder.seedlistPhrases( charValues );
        }

        return RandomGeneratorConfig.make( pwmDomain, pwmPasswordPolicy, randomConfigBuilder.build() );
    }
}

