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

package password.pwm.http;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PwmSessionFactory
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmSessionFactory.class );

    private PwmSessionFactory( )
    {
    }

    private static PwmSession createSession( final PwmRequest pwmRequest, final PwmDomain pwmDomain )
    {
        final PwmSession pwmSession = PwmSession.createPwmSession( pwmDomain );

        pwmSession.getSessionStateBean().setLocale( pwmRequest.getLocale() );

        return pwmSession;
    }

    @SuppressFBWarnings( "J2EE_STORE_OF_NON_SERIALIZABLE_OBJECT_INTO_SESSION" )
    public static void sessionMerge(
            final PwmRequest pwmRequest,
            final PwmDomain pwmDomain,
            final PwmSession pwmSession,
            final HttpSession httpSession
    )
            throws PwmUnrecoverableException
    {
        httpSession.setAttribute( PwmConstants.SESSION_ATTR_PWM_SESSION, pwmSession );

        setHttpSessionIdleTimeout( pwmDomain, pwmRequest, httpSession );
    }

    public static PwmSession readPwmSession( final PwmRequest pwmRequest, final PwmDomain pwmdomain )
    {
        final HttpSession httpSession = pwmRequest.getHttpServletRequest().getSession();
        final Map<DomainID, PwmSession> map = getDomainSessionMap( httpSession );
        return map.computeIfAbsent( pwmdomain.getDomainID(), k -> createSession( pwmRequest, pwmdomain ) );
    }

    public static Map<DomainID, PwmSession> getDomainSessionMap( final HttpSession httpSession )
    {
        Map<DomainID, PwmSession> map = ( Map<DomainID, PwmSession> ) httpSession.getAttribute( PwmConstants.SESSION_ATTR_PWM_SESSION );
        if ( map == null )
        {
            map = new ConcurrentHashMap<>();
            httpSession.setAttribute( PwmConstants.SESSION_ATTR_PWM_SESSION, map );
        }
        return map;
    }

    public static void setHttpSessionIdleTimeout(
            final PwmDomain pwmDomain,
            final PwmRequest pwmRequest,
            final HttpSession httpSession
    )
            throws PwmUnrecoverableException
    {
        final IdleTimeoutCalculator.MaxIdleTimeoutResult result = IdleTimeoutCalculator.figureMaxSessionTimeout( pwmDomain, pwmRequest );
        if ( httpSession.getMaxInactiveInterval() != result.getIdleTimeout().as( TimeDuration.Unit.SECONDS ) )
        {
            httpSession.setMaxInactiveInterval( ( int ) result.getIdleTimeout().as( TimeDuration.Unit.SECONDS ) );
            LOGGER.trace( pwmRequest, () -> "setting java servlet session timeout to " + result.getIdleTimeout().asCompactString()
                    + " due to " + result.getReason() );
        }
    }
}
