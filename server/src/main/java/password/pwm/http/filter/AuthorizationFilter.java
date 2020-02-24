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

package password.pwm.http.filter;

import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.error.PwmError;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.PwmURL;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Authorization servlet filter.  Manages PWM authorization levels.  Primarily,
 * this filter handles authorization filtering for the PWM Admin modules.
 *
 * @author Jason D. Rivard
 */
public class AuthorizationFilter extends AbstractPwmFilter
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( AuthenticationFilter.class );

    public void init( final FilterConfig filterConfig )
            throws ServletException
    {
    }

    @Override
    boolean isInterested( final PwmApplicationMode mode, final PwmURL pwmURL )
    {
        return !pwmURL.isRestService();
    }

    public void processFilter(
            final PwmApplicationMode mode,
            final PwmRequest pwmRequest,
            final PwmFilterChain chain
    )
            throws IOException, ServletException
    {

        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        // if the user is not authenticated as a PWM Admin, redirect to error page.
        boolean hasPermission = false;
        try
        {
            hasPermission = pwmSession.getSessionManager().checkPermission( pwmApplication, Permission.PWMADMIN );
        }
        catch ( final Exception e )
        {
            LOGGER.warn( pwmRequest, () -> "error during authorization check: " + e.getMessage() );
        }

        try
        {
            if ( hasPermission )
            {
                chain.doFilter();
                return;
            }
        }
        catch ( final Exception e )
        {
            LOGGER.warn( pwmRequest, () -> "unexpected error executing filter chain: " + e.getMessage() );
            return;
        }

        pwmRequest.respondWithError( PwmError.ERROR_UNAUTHORIZED.toInfo() );
    }

    public void destroy( )
    {
    }
}
