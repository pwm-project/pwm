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

package password.pwm.ldap.auth;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.logging.PwmLogger;

import java.util.Arrays;
import java.util.Collection;

public class SimpleLdapAuthenticator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SimpleLdapAuthenticator.class );

    private static final Collection ACCEPTABLE_AUTH_TYPES = Arrays.asList(
                    AuthenticationType.AUTHENTICATED,
                    AuthenticationType.AUTH_BIND_INHIBIT
            );

    public static AuthenticationResult authenticateUser(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final PasswordData password
    )
            throws PwmUnrecoverableException
    {
        final AuthenticationRequest authEngine = LDAPAuthenticationRequest.createLDAPAuthenticationRequest(
                pwmApplication,
                sessionLabel,
                userIdentity,
                AuthenticationType.AUTHENTICATED,
                PwmAuthenticationSource.BASIC_AUTH
        );

        final AuthenticationResult authResult;
        try
        {
            authResult = authEngine.authenticateUser( password );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
        catch ( final PwmOperationalException e )
        {
            throw new PwmUnrecoverableException( e.getErrorInformation() );
        }

        if ( ACCEPTABLE_AUTH_TYPES.contains( authResult.getAuthenticationType() ) )
        {
            return authResult;
        }

        final ErrorInformation errorInformation = new ErrorInformation(
                PwmError.ERROR_INTERNAL,
                "auth with unexpected auth type: " + authResult.getAuthenticationType()
        );
        LOGGER.error( errorInformation );
        throw new PwmUnrecoverableException( errorInformation );
    }
}
