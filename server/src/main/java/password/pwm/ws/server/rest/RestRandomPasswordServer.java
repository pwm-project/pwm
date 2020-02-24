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

import lombok.Data;
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
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.PasswordData;
import password.pwm.util.password.RandomPasswordGenerator;
import password.pwm.util.java.StringUtil;
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

    @Data
    public static class JsonOutput implements Serializable
    {
        private String password;
    }

    @Data
    public static class JsonInput implements Serializable
    {
        private String username;
        private int strength;
        private int minLength;
        private int maxLength;
        private String chars;
        private boolean noUser;

    }

    @Override
    public void preCheckRequest( final RestRequest request ) throws PwmUnrecoverableException
    {
    }

    @RestMethodHandler( method = HttpMethod.POST, consumes = HttpContentType.form, produces = HttpContentType.json )
    public RestResultBean doPostRandomPasswordForm( final RestRequest restRequest )
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

        final JsonInput jsonInput = new JsonInput();
        jsonInput.username = restRequest.readParameterAsString( "username", PwmHttpRequestWrapper.Flag.BypassValidation );
        jsonInput.strength = restRequest.readParameterAsInt( "strength", 0 );
        jsonInput.maxLength = restRequest.readParameterAsInt( "maxLength", 0 );
        jsonInput.minLength = restRequest.readParameterAsInt( "minLength", 0 );
        jsonInput.chars = restRequest.readParameterAsString( "chars", PwmHttpRequestWrapper.Flag.BypassValidation );
        jsonInput.noUser = restRequest.readParameterAsBoolean( "noUser" );

        try
        {
            final JsonOutput jsonOutput = doOperation( restRequest, jsonInput );
            final RestResultBean restResultBean = RestResultBean.withData( jsonOutput );
            return restResultBean;
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

        final JsonInput jsonInput = new JsonInput();
        jsonInput.username = restRequest.readParameterAsString( "username", PwmHttpRequestWrapper.Flag.BypassValidation );
        jsonInput.strength = restRequest.readParameterAsInt( "strength", 0 );
        jsonInput.maxLength = restRequest.readParameterAsInt( "maxLength", 0 );
        jsonInput.minLength = restRequest.readParameterAsInt( "minLength", 0 );
        jsonInput.chars = restRequest.readParameterAsString( "chars", PwmHttpRequestWrapper.Flag.BypassValidation );
        jsonInput.noUser = restRequest.readParameterAsBoolean( "noUser" );

        try
        {
            final JsonOutput jsonOutput = doOperation( restRequest, jsonInput );
            return RestResultBean.withData( jsonOutput.getPassword() );
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
            return RestResultBean.withData( jsonOutput );
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

        if ( jsonInput.isNoUser() || StringUtil.isEmpty( jsonInput.getUsername() ) )
        {
            pwmPasswordPolicy = PwmPasswordPolicy.defaultPolicy();
        }
        else
        {
            final TargetUserIdentity targetUserIdentity = RestUtility.resolveRequestedUsername( restRequest, jsonInput.getUsername() );
            pwmPasswordPolicy = PasswordUtility.readPasswordPolicyForUser(
                    restRequest.getPwmApplication(),
                    restRequest.getSessionLabel(),
                    targetUserIdentity.getUserIdentity(),
                    targetUserIdentity.getChaiUser(),
                    restRequest.getLocale()
            );
        }

        final RandomPasswordGenerator.RandomGeneratorConfig randomConfig = jsonInputToRandomConfig( jsonInput, pwmPasswordPolicy );
        final PasswordData randomPassword = RandomPasswordGenerator.createRandomPassword( restRequest.getSessionLabel(), randomConfig, restRequest.getPwmApplication() );
        final JsonOutput outputMap = new JsonOutput();
        outputMap.password = randomPassword.getStringValue();

        StatisticsManager.incrementStat( restRequest.getPwmApplication(), Statistic.REST_SETPASSWORD );

        return outputMap;
    }

    public static RandomPasswordGenerator.RandomGeneratorConfig jsonInputToRandomConfig(
            final JsonInput jsonInput,
            final PwmPasswordPolicy pwmPasswordPolicy
    )
    {
        final RandomPasswordGenerator.RandomGeneratorConfig.RandomGeneratorConfigBuilder randomConfigBuilder
                = RandomPasswordGenerator.RandomGeneratorConfig.builder();

        if ( jsonInput.getStrength() > 0 && jsonInput.getStrength() <= 100 )
        {
            randomConfigBuilder.minimumStrength( jsonInput.getStrength() );
        }
        if ( jsonInput.getMinLength() > 0 && jsonInput.getMinLength() <= 100 * 1024 )
        {
            randomConfigBuilder.minimumLength( jsonInput.getMinLength() );
        }
        if ( jsonInput.getMaxLength() > 0 && jsonInput.getMaxLength() <= 100 * 1024 )
        {
            randomConfigBuilder.maximumLength( jsonInput.getMaxLength() );
        }
        if ( jsonInput.getChars() != null )
        {
            final List<String> charValues = new ArrayList<>();
            for ( int i = 0; i < jsonInput.getChars().length(); i++ )
            {
                charValues.add( String.valueOf( jsonInput.getChars().charAt( i ) ) );
            }
            randomConfigBuilder.seedlistPhrases( charValues );
        }

        randomConfigBuilder.passwordPolicy( pwmPasswordPolicy );

        return randomConfigBuilder.build();
    }
}

