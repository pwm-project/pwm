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

package password.pwm.http.auth;

import password.pwm.AppProperty;
import password.pwm.bean.LoginInfoBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmHttpResponseWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

public abstract class HttpAuthenticationUtilities
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HttpAuthenticationUtilities.class );

    private static final Set<AuthenticationMethod> IGNORED_AUTH_METHODS = new HashSet<>();


    public static ProcessStatus attemptAuthenticationMethods( final PwmRequest pwmRequest ) throws IOException, ServletException
    {
        if ( pwmRequest.isAuthenticated() )
        {
            return ProcessStatus.Continue;
        }

        for ( final AuthenticationMethod authenticationMethod : AuthenticationMethod.values() )
        {
            if ( !IGNORED_AUTH_METHODS.contains( authenticationMethod ) )
            {
                PwmHttpFilterAuthenticationProvider filterAuthenticationProvider = null;
                try
                {
                    final String className = authenticationMethod.getClassName();
                    final Class clazz = Class.forName( className );
                    final Object newInstance = clazz.newInstance();
                    filterAuthenticationProvider = ( PwmHttpFilterAuthenticationProvider ) newInstance;
                }
                catch ( Exception e )
                {
                    LOGGER.trace( () -> "could not load authentication class '" + authenticationMethod + "', will ignore" );
                    IGNORED_AUTH_METHODS.add( authenticationMethod );
                }

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

                    }
                    catch ( Exception e )
                    {
                        final ErrorInformation errorInformation;
                        if ( e instanceof PwmException )
                        {
                            final String errorMsg = "error during " + authenticationMethod + " authentication attempt: " + e.getMessage();
                            errorInformation = new ErrorInformation( ( ( PwmException ) e ).getError(), errorMsg );
                        }
                        else
                        {
                            errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, e.getMessage() );

                        }
                        LOGGER.error( pwmRequest, errorInformation );
                        pwmRequest.respondWithError( errorInformation );
                        return ProcessStatus.Halt;
                    }
                }
            }
        }
        return ProcessStatus.Continue;
    }

    public static void handleAuthenticationCookie( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        if ( !pwmRequest.isAuthenticated() || pwmRequest.getPwmSession().getLoginInfoBean().getType() != AuthenticationType.AUTHENTICATED )
        {
            return;
        }

        if ( pwmRequest.getPwmSession().getLoginInfoBean().isLoginFlag( LoginInfoBean.LoginFlag.authRecordSet ) )
        {
            return;
        }

        pwmRequest.getPwmSession().getLoginInfoBean().setFlag( LoginInfoBean.LoginFlag.authRecordSet );

        final String cookieName = pwmRequest.getConfig().readAppProperty( AppProperty.HTTP_COOKIE_AUTHRECORD_NAME );
        if ( cookieName == null || cookieName.isEmpty() )
        {
            LOGGER.debug( pwmRequest, () -> "skipping auth record cookie set, cookie name parameter is blank" );
            return;
        }

        final int cookieAgeSeconds = Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.HTTP_COOKIE_AUTHRECORD_AGE ) );
        if ( cookieAgeSeconds < 1 )
        {
            LOGGER.debug( pwmRequest, () -> "skipping auth record cookie set, cookie age parameter is less than 1" );
            return;
        }

        final Instant authTime = pwmRequest.getPwmSession().getLoginInfoBean().getAuthTime();
        final String userGuid = pwmRequest.getPwmSession().getUserInfo().getUserGuid();
        final HttpAuthRecord httpAuthRecord = new HttpAuthRecord( authTime, userGuid );

        try
        {
            pwmRequest.getPwmResponse().writeEncryptedCookie( cookieName, httpAuthRecord, cookieAgeSeconds, PwmHttpResponseWrapper.CookiePath.Application );
            LOGGER.debug( pwmRequest, () -> "wrote auth record cookie to user browser for use during forgotten password" );
        }
        catch ( PwmUnrecoverableException e )
        {
            LOGGER.error( pwmRequest, "error while setting authentication record cookie: " + e.getMessage() );
        }
    }

}
