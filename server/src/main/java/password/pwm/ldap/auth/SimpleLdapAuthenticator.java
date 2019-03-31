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
        catch ( ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
        catch ( PwmOperationalException e )
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
