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

import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ObsoleteUrlFilter extends AbstractPwmFilter
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( ObsoleteUrlFilter.class );

    private static final Map<String, String> STATIC_REDIRECTS;

    static
    {
        final Map<String, String> staticRedirects = new HashMap<>();
        staticRedirects.put( PwmConstants.URL_PREFIX_PRIVATE, PwmConstants.URL_PREFIX_PRIVATE + "/" );
        STATIC_REDIRECTS = Collections.unmodifiableMap( staticRedirects );
    }


    @Override
    void processFilter( final PwmApplicationMode mode, final PwmRequest pwmRequest, final PwmFilterChain filterChain )
            throws PwmException, IOException, ServletException
    {
        final ProcessStatus processStatus = redirectOldUrls( pwmRequest );
        if ( processStatus == ProcessStatus.Continue )
        {
            filterChain.doFilter();
        }
    }

    private ProcessStatus redirectOldUrls( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException
    {
        if ( pwmRequest == null || pwmRequest.getURL() == null )
        {
            return ProcessStatus.Continue;
        }
        final PwmURL pwmURL = pwmRequest.getURL();
        if ( pwmURL.isResourceURL() || pwmURL.isCommandServletURL() )
        {
            return ProcessStatus.Continue;
        }

        if ( pwmRequest.getMethod() != HttpMethod.GET )
        {
            return ProcessStatus.Continue;
        }

        if ( !pwmRequest.readParametersAsMap().isEmpty() )
        {
            return ProcessStatus.Continue;
        }

        final String requestUrl = pwmRequest.getURLwithoutQueryString();
        final String requestServletUrl = requestUrl.substring( pwmRequest.getContextPath().length() );

        for ( final PwmServletDefinition pwmServletDefinition : PwmServletDefinition.values() )
        {
            boolean match = false;
            for ( final String patternUrl : pwmServletDefinition.urlPatterns() )
            {
                if ( patternUrl.equals( requestServletUrl ) )
                {
                    match = true;
                    break;
                }
            }

            if ( match )
            {
                if ( !pwmServletDefinition.servletUrl().equals( requestServletUrl ) )
                {
                    LOGGER.debug( pwmRequest, () -> "obsolete url of '"
                            + requestServletUrl
                            + "' detected, redirecting to canonical URL of '"
                            + pwmServletDefinition.servletUrl() + "'" );
                    StatisticsManager.incrementStat( pwmRequest, Statistic.OBSOLETE_URL_REQUESTS );
                    pwmRequest.sendRedirect( pwmServletDefinition );
                    return ProcessStatus.Halt;
                }
            }

        }

        return doStaticMapRedirects( pwmRequest );
    }

    private ProcessStatus doStaticMapRedirects( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final String requestUrl = pwmRequest.getURLwithQueryString();

        for ( final Map.Entry<String, String> entry : STATIC_REDIRECTS.entrySet() )
        {
            final String testUrl = pwmRequest.getContextPath() + entry.getKey();
            if ( StringUtil.nullSafeEquals( requestUrl, testUrl ) )
            {
                final String nextUrl = pwmRequest.getContextPath() + entry.getValue();
                pwmRequest.sendRedirect( nextUrl );
                return ProcessStatus.Halt;
            }
        }

        return ProcessStatus.Continue;
    }

    @Override
    boolean isInterested( final PwmApplicationMode mode, final PwmURL pwmURL )
    {
        return !pwmURL.isRestService();
    }
}
