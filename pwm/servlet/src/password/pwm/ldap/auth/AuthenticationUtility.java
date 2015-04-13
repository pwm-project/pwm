/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;

public abstract class AuthenticationUtility {
    public static void checkIfUserEligibleToAuthentication(
            final PwmApplication pwmApplication,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        try {
            checkIfUserEligibleToAuthenticationImpl(pwmApplication, userIdentity);
        } catch (ChaiOperationException | ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(PwmError.forChaiError(e.getErrorCode()));
        }
    }

    private static void checkIfUserEligibleToAuthenticationImpl(
            final PwmApplication pwmApplication,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, ChaiOperationException
    {
        final ChaiUser chaiUser = pwmApplication.getProxiedChaiUser(userIdentity);

        if (!chaiUser.isAccountEnabled()) {
            throw new PwmUnrecoverableException(PwmError.ERROR_ACCOUNT_DISABLED);
        }

        if (chaiUser.isAccountExpired()) {
            throw new PwmUnrecoverableException(PwmError.ERROR_ACCOUNT_EXPIRED);
        }
    }

    static LDAPAuthenticationRequest createLDAPAuthenticationRequest(
            PwmApplication pwmApplication,
            SessionLabel sessionLabel,
            UserIdentity userIdentity,
            AuthenticationType requestedAuthType
    ) {
        return new LDAPAuthenticationRequest(pwmApplication, sessionLabel, userIdentity, requestedAuthType);
    }
}
