/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.bean.DomainID;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import java.io.IOException;

public class DomainRouterFilter extends AbstractPwmFilter
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DomainRouterFilter.class );

    @Override
    boolean isInterested( final PwmApplicationMode mode, final PwmURL pwmURL )
    {
        return true;
    }

    @Override
    void processFilter( final PwmApplicationMode mode, final PwmRequest pwmRequest, final PwmFilterChain filterChain )
            throws PwmException, IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        if ( pwmApplication.isMultiDomain() )
        {
            final DomainID domainID = pwmRequest.getDomainID();

            final String remainderPath = servletPathMinusDomain( domainID, pwmRequest );

            //LOGGER.trace( () -> "request detected domain: '" + domainID.stringValue() + "', treating servlet url as: " + remainderPath );

            final RequestDispatcher requestDispatcher = pwmRequest.getHttpServletRequest().getRequestDispatcher( remainderPath );
            requestDispatcher.forward( pwmRequest.getHttpServletRequest(), filterChain.getServletResponse() );
        }
        else
        {
            filterChain.doFilter();
        }
    }

    private static String servletPathMinusDomain( final DomainID domainID, final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final String requestPath = pwmRequest.getURLwithQueryString();
        final String stripPrefix = pwmRequest.getBasePath();

        return requestPath.startsWith( stripPrefix )
                ? requestPath.substring( stripPrefix.length() )
                : requestPath;
    }
}
