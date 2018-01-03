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
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.PasswordData;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.operations.PasswordUtility;
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
                PwmConstants.URL_PREFIX_PUBLIC + PwmConstants.URL_PREFIX_REST + "/setpassword"
        }
)
@RestWebServer( webService = WebServiceUsage.SetPassword, requireAuthentication = true )
public class RestSetPasswordServer extends RestServlet
{

    public static final PwmLogger LOGGER = PwmLogger.forClass( RestSetPasswordServer.class );

    @Data
    public static class JsonInputData implements Serializable
    {
        public String username;
        public String password;
        public boolean random;
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
        final JsonInputData jsonInputData = new JsonInputData();
        jsonInputData.username = restRequest.readParameterAsString( "username", PwmHttpRequestWrapper.Flag.BypassValidation );
        jsonInputData.password = restRequest.readParameterAsString( "password", PwmHttpRequestWrapper.Flag.BypassValidation );
        jsonInputData.random = restRequest.readParameterAsBoolean( "random" );

        return doSetPassword( restRequest, jsonInputData );
    }

    @RestMethodHandler( method = HttpMethod.POST, consumes = HttpContentType.json, produces = HttpContentType.json )
    public RestResultBean doPostSetPasswordJson( final RestRequest restRequest )
            throws IOException, PwmUnrecoverableException
    {
        final JsonInputData jsonInputData = deserializeJsonBody( restRequest, JsonInputData.class );
        return doSetPassword( restRequest, jsonInputData );
    }

    private static RestResultBean doSetPassword(
            final RestRequest restRequest,
            final JsonInputData jsonInputData

    ) throws PwmUnrecoverableException
    {
        final String password = jsonInputData.password;
        final boolean random = jsonInputData.random;

        if ( ( password == null || password.length() < 1 ) && !random )
        {
            final String errorMessage = "field 'password' must have a value or field 'random' must be set to true";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, errorMessage, new String[]
                    {
                            "password",
                    }
            );
            return RestResultBean.fromError( restRequest, errorInformation );
        }

        if ( ( password != null && password.length() > 0 ) && random )
        {
            final String errorMessage = "field 'password' cannot have a value or field 'random' must be set to true";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, errorMessage, new String[]
                    {
                            "password",
                    }
            );
            return RestResultBean.fromError( restRequest, errorInformation );
        }

        try
        {
            final JsonInputData jsonResultData = new JsonInputData();
            jsonResultData.random = random;

            final TargetUserIdentity targetUserIdentity = resolveRequestedUsername( restRequest, jsonInputData.username );

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
                oldPassword = basicAuthInfo.getPassword();
            }
            else
            {
                oldPassword = null;
            }

            PasswordUtility.setPassword(
                    restRequest.getPwmApplication(),
                    restRequest.getSessionLabel(),
                    targetUserIdentity.getChaiProvider(),
                    targetUserIdentity.getUserIdentity(),
                    oldPassword,
                    newPassword
            );

            StatisticsManager.incrementStat( restRequest.getPwmApplication(), Statistic.REST_SETPASSWORD );
            final RestResultBean restResultBean = RestResultBean.withData( jsonResultData );
            return restResultBean;
        }
        catch ( PwmException e )
        {
            LOGGER.error( "error during set password REST operation: " + e.getMessage() );
            return RestResultBean.fromError( restRequest, e.getErrorInformation() );
        }
        catch ( Exception e )
        {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, errorMessage );
            LOGGER.error( "error during set password REST operation: " + e.getMessage(), e );
            return RestResultBean.fromError( restRequest, errorInformation );
        }
    }
}
