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

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;

public abstract class AuthenticationUtility
{
    public static void checkIfUserEligibleToAuthentication(
            final PwmApplication pwmApplication,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        try
        {
            checkIfUserEligibleToAuthenticationImpl( pwmApplication, userIdentity );
        }
        catch ( final ChaiOperationException | ChaiUnavailableException e )
        {
            throw new PwmUnrecoverableException( PwmError.forChaiError( e.getErrorCode() ) );
        }
    }

    private static void checkIfUserEligibleToAuthenticationImpl(
            final PwmApplication pwmApplication,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, ChaiOperationException
    {
        final ChaiUser chaiUser = pwmApplication.getProxiedChaiUser( userIdentity );

        if ( !chaiUser.isAccountEnabled() )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_ACCOUNT_DISABLED );
        }

        if ( chaiUser.isAccountExpired() )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_ACCOUNT_EXPIRED );
        }
    }

}
