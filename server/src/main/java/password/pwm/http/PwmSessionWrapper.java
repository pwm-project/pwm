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

package password.pwm.http;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public class PwmSessionWrapper
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmSessionWrapper.class );

    private transient PwmSession pwmSession;

    private PwmSessionWrapper( )
    {

    }

    public static void sessionMerge(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpSession httpSession
    )
            throws PwmUnrecoverableException
    {
        httpSession.setAttribute( PwmConstants.SESSION_ATTR_PWM_SESSION, pwmSession );

        setHttpSessionIdleTimeout( pwmApplication, pwmSession, httpSession );
    }


    public static PwmSession readPwmSession( final HttpSession httpSession )
            throws PwmUnrecoverableException
    {
        final PwmSession returnSession = ( PwmSession ) httpSession.getAttribute( PwmConstants.SESSION_ATTR_PWM_SESSION );
        if ( returnSession == null )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, "attempt to read PwmSession from HttpSession failed" ) );
        }
        return returnSession;
    }

    public static PwmSession readPwmSession(
            final HttpServletRequest httpRequest
    )
            throws PwmUnrecoverableException
    {
        return readPwmSession( httpRequest.getSession() );
    }

    public static void setHttpSessionIdleTimeout(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpSession httpSession
    )
            throws PwmUnrecoverableException
    {
        final IdleTimeoutCalculator.MaxIdleTimeoutResult result = IdleTimeoutCalculator.figureMaxSessionTimeout( pwmApplication, pwmSession );
        if ( httpSession.getMaxInactiveInterval() != result.getIdleTimeout().as( TimeDuration.Unit.SECONDS ) )
        {
            httpSession.setMaxInactiveInterval( ( int ) result.getIdleTimeout().as( TimeDuration.Unit.SECONDS ) );
            LOGGER.trace( pwmSession.getLabel(), () -> "setting java servlet session timeout to " + result.getIdleTimeout().asCompactString()
                    + " due to " + result.getReason() );
        }
    }
}
