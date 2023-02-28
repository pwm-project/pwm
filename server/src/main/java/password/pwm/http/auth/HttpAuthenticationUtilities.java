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

package password.pwm.http.auth;

import password.pwm.DomainProperty;
import password.pwm.bean.LoginInfoBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmCookiePath;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public abstract class HttpAuthenticationUtilities
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HttpAuthenticationUtilities.class );

    private static final Map<AuthenticationMethod, PwmHttpFilterAuthenticationProvider> VALID_AUTH_METHODS = initAuthMethods();

    private static Map<AuthenticationMethod, PwmHttpFilterAuthenticationProvider> initAuthMethods()
    {
        final EnumMap<AuthenticationMethod, PwmHttpFilterAuthenticationProvider> methods
                = new EnumMap<>( AuthenticationMethod.class );
        for ( final AuthenticationMethod method : AuthenticationMethod.values() )
        {
            try
            {
                final String className = method.getClassName();
                final Class<?> clazz = Class.forName( className );
                final PwmHttpFilterAuthenticationProvider filter =
                        ( PwmHttpFilterAuthenticationProvider ) clazz.getDeclaredConstructor().newInstance();
                methods.put( method, filter );
            }
            catch ( final Throwable e )
            {
                LOGGER.error( () -> "could not load authentication class '" + method + "', will ignore (error: " + e.getMessage() + ")" );
            }
        }
        return Collections.unmodifiableMap( methods );
    }

    public static ProcessStatus attemptAuthenticationMethods( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        if ( pwmRequest.isAuthenticated() )
        {
            return ProcessStatus.Continue;
        }

        for ( final Map.Entry<AuthenticationMethod, PwmHttpFilterAuthenticationProvider> entry : VALID_AUTH_METHODS.entrySet() )
        {
            final AuthenticationMethod authenticationMethod = entry.getKey();
            final PwmHttpFilterAuthenticationProvider filterAuthenticationProvider = entry.getValue();

            if ( filterAuthenticationProvider != null )
            {
                try
                {
                    filterAuthenticationProvider.attemptAuthentication( pwmRequest );

                    if ( pwmRequest.isAuthenticated() )
                    {
                        LOGGER.trace( pwmRequest, () -> "authentication provided by method " + authenticationMethod.name() );
                    }

                    if ( filterAuthenticationProvider.hasRedirectedResponse() )
                    {
                        LOGGER.trace( pwmRequest, () -> "authentication provider " + authenticationMethod.name()
                                + " has issued a redirect, halting authentication process" );
                        return ProcessStatus.Halt;
                    }

                    if ( pwmRequest.isAuthenticated() )
                    {
                        return ProcessStatus.Continue;
                    }
                }
                catch ( final Exception e )
                {
                    final ErrorInformation errorInformation;
                    if ( e instanceof PwmException )
                    {
                        final String errorMsg = "error during " + authenticationMethod + " authentication attempt: " + e.getMessage();
                        errorInformation = new ErrorInformation( ( ( PwmException ) e ).getError(), errorMsg );
                        LOGGER.error( pwmRequest, errorInformation );
                    }
                    else
                    {
                        errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, e.getMessage() );
                        LOGGER.error( pwmRequest.getLabel(), errorInformation, e );
                    }
                    pwmRequest.respondWithError( errorInformation );
                    return ProcessStatus.Halt;
                }
            }
        }
        return ProcessStatus.Continue;
    }

    public static void handleAuthenticationCookie( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        if ( !pwmRequest.isAuthenticated() || pwmSession.getLoginInfoBean().getType() != AuthenticationType.AUTHENTICATED )
        {
            return;
        }

        if ( pwmSession.getLoginInfoBean().isLoginFlag( LoginInfoBean.LoginFlag.authRecordSet ) )
        {
            return;
        }

        pwmSession.getLoginInfoBean().setFlag( LoginInfoBean.LoginFlag.authRecordSet );

        final String cookieName = pwmRequest.getDomainConfig().readDomainProperty( DomainProperty.HTTP_COOKIE_AUTHRECORD_NAME );
        if ( cookieName == null || cookieName.isEmpty() )
        {
            LOGGER.debug( pwmRequest, () -> "skipping auth record cookie set, cookie name parameter is blank" );
            return;
        }

        final int cookieAgeSeconds = Integer.parseInt( pwmRequest.getDomainConfig().readDomainProperty( DomainProperty.HTTP_COOKIE_AUTHRECORD_AGE ) );
        if ( cookieAgeSeconds < 1 )
        {
            LOGGER.debug( pwmRequest, () -> "skipping auth record cookie set, cookie age parameter is less than 1" );
            return;
        }

        final Instant authTime = pwmSession.getLoginInfoBean().getAuthTime();
        final String userGuid = pwmSession.getUserInfo().getUserGuid();
        final HttpAuthRecord httpAuthRecord = new HttpAuthRecord( authTime, userGuid );

        try
        {
            pwmRequest.getPwmResponse().writeEncryptedCookie( cookieName, httpAuthRecord, cookieAgeSeconds, PwmCookiePath.Domain );
            LOGGER.debug( pwmRequest, () -> "wrote auth record cookie to user browser for use during forgotten password" );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.error( pwmRequest, () -> "error while setting authentication record cookie: " + e.getMessage() );
        }
    }

}
