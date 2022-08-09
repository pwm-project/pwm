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

package password.pwm.ws.server;

import com.google.gson.stream.MalformedJsonException;
import password.pwm.PwmDomain;
import password.pwm.bean.UserIdentity;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.search.UserSearchService;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.StringUtil;

import java.util.Optional;

public class RestUtility
{
    public enum Flag
    {
        AllowNullReturn
    }

    public static <T> T deserializeJsonBody( final RestRequest restRequest, final Class<T> classOfT, final Flag... flags )
            throws PwmUnrecoverableException
    {
        try
        {
            final T jsonData = JsonFactory.get().deserialize( restRequest.readRequestBodyAsString(), classOfT );
            if ( jsonData == null && !JavaHelper.enumArrayContainsValue( flags, Flag.AllowNullReturn ) )
            {
                throw PwmUnrecoverableException.newException( PwmError.ERROR_REST_INVOCATION_ERROR, "missing json body" );
            }
            return jsonData;
        }
        catch ( final Exception e )
        {
            if ( e.getCause() instanceof MalformedJsonException )
            {
                throw PwmUnrecoverableException.newException( PwmError.ERROR_REST_INVOCATION_ERROR, "json parse error: " + e.getCause().getMessage() );
            }
            throw PwmUnrecoverableException.newException( PwmError.ERROR_REST_INVOCATION_ERROR, "json parse error: " + e.getMessage() );
        }
    }

    public static RestServlet.TargetUserIdentity resolveRequestedUsername(
            final RestRequest restRequest,
            final String username
    )
            throws PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = restRequest.getDomain();

        if ( StringUtil.isEmpty( username ) )
        {
            if ( restRequest.getRestAuthentication().getType() == RestAuthenticationType.NAMED_SECRET )
            {
                throw PwmUnrecoverableException.newException( PwmError.ERROR_REST_INVOCATION_ERROR,
                        "username field required when using external web services secrets for authentication " );
            }
        }
        else
        {
            if ( !restRequest.getRestAuthentication().isThirdPartyEnabled() )
            {
                throw PwmUnrecoverableException.newException(
                        PwmError.ERROR_UNAUTHORIZED,
                        "username specified in request, however third party permission is not granted to the authenticated login."
                );
            }
        }

        if ( StringUtil.isEmpty( username ) )
        {
            if ( restRequest.getRestAuthentication().getType() == RestAuthenticationType.LDAP )
            {
                return new RestServlet.TargetUserIdentity( restRequest, restRequest.getRestAuthentication().getLdapIdentity(), true );
            }
        }

        final String ldapProfileID;
        final String effectiveUsername;
        if ( username.contains( "|" ) )
        {
            final int pipeIndex = username.indexOf( '|' );
            ldapProfileID = username.substring( 0, pipeIndex );
            effectiveUsername = username.substring( pipeIndex + 1 );
        }
        else
        {
            ldapProfileID = null;
            effectiveUsername = username;
        }

        try
        {
            final UserSearchService userSearchService = pwmDomain.getUserSearchEngine();
            final UserIdentity userIdentity = userSearchService.resolveUsername( effectiveUsername, null, ldapProfileID, restRequest.getSessionLabel() );

            final LdapProfile ldapProfile = pwmDomain.getConfig().getLdapProfiles().get( userIdentity.getLdapProfileID() );
            if ( ldapProfile != null )
            {
                {
                    final Optional<UserIdentity> optionalTestUser = ldapProfile.getTestUser( restRequest.getSessionLabel(), pwmDomain );
                    if ( optionalTestUser.isPresent() && optionalTestUser.get().canonicalEquals( restRequest.getSessionLabel(), userIdentity, restRequest.getPwmApplication() ) )
                    {
                        final String msg = "rest services can not be invoked against the configured LDAP profile test user";
                        final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_REST_INVOCATION_ERROR, msg );
                        throw new PwmUnrecoverableException( errorInformation );
                    }
                }

                {
                    final UserIdentity proxyUser = ldapProfile.getProxyUser( restRequest.getSessionLabel(), pwmDomain );
                    if ( proxyUser != null && proxyUser.canonicalEquals( restRequest.getSessionLabel(), userIdentity, restRequest.getPwmApplication() ) )
                    {
                        final String msg = "rest services can not be invoked against the configured LDAP profile proxy user";
                        final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_REST_INVOCATION_ERROR, msg );
                        throw new PwmUnrecoverableException( errorInformation );
                    }
                }
            }

            return new RestServlet.TargetUserIdentity( restRequest, userIdentity, false );
        }
        catch ( final PwmOperationalException e )
        {
            throw new PwmUnrecoverableException( e.getErrorInformation() );
        }
    }

    public enum ReadValueFlag
    {
        optional
    }

    public static Optional<String> readValueFromJsonAndParam(
            final String jsonValue,
            final String paramValue,
            final String paramName,
            final ReadValueFlag... flags
    )
            throws PwmUnrecoverableException
    {
        if ( StringUtil.isEmpty( jsonValue ) && StringUtil.isEmpty( paramValue ) )
        {
            if ( JavaHelper.enumArrayContainsValue( flags, ReadValueFlag.optional ) )
            {
                return Optional.empty();
            }
            final String msg = paramName + " parameter is not specified";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, msg, new String[]
                    {
                            paramName,
                    }
            );
            throw new PwmUnrecoverableException( errorInformation );
        }
        else if ( StringUtil.notEmpty( jsonValue ) && StringUtil.notEmpty( paramValue ) )
        {
            final String msg = paramName + " parameter can not be specified in both request parameter and json body";
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_REST_INVOCATION_ERROR, msg ) );
        }
        else if ( StringUtil.notEmpty( jsonValue ) )
        {
            return Optional.of( jsonValue );
        }

        return Optional.of( paramValue );
    }
}
