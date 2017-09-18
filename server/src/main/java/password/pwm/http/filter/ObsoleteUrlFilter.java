/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.http.filter;

import password.pwm.PwmApplicationMode;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import java.io.IOException;

public class ObsoleteUrlFilter extends AbstractPwmFilter {

    private static final PwmLogger LOGGER = PwmLogger.forClass(ObsoleteUrlFilter.class);

    @Override
    void processFilter(final PwmApplicationMode mode, final PwmRequest pwmRequest, final PwmFilterChain filterChain)
            throws PwmException, IOException, ServletException
    {
        final ProcessStatus processStatus = redirectOldUrls(pwmRequest);
        if (processStatus == ProcessStatus.Continue) {
            filterChain.doFilter();
        }
    }

    private ProcessStatus redirectOldUrls(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, IOException
    {
        final PwmURL pwmURL = pwmRequest.getURL();
        if (pwmURL.isResourceURL() || pwmURL.isCommandServletURL()) {
            return ProcessStatus.Continue;
        }

        if (pwmRequest.getMethod() != HttpMethod.GET) {
            return ProcessStatus.Continue;
        }

        if (!pwmRequest.readParametersAsMap().isEmpty()) {
            return ProcessStatus.Continue;
        }

        final String requestUrl = pwmRequest.getURLwithoutQueryString();
        final String requestServletUrl = requestUrl.substring(pwmRequest.getContextPath().length(), requestUrl.length());

        for (final PwmServletDefinition pwmServletDefinition : PwmServletDefinition.values()) {
            boolean match = false;
            for (final String patternUrl : pwmServletDefinition.urlPatterns()) {
                if (patternUrl.equals(requestServletUrl)) {
                    match = true;
                    break;
                }
            }

            if (match) {
                if (!pwmServletDefinition.servletUrl().equals(requestServletUrl)) {
                    LOGGER.debug(pwmRequest, "obsolete url of '"
                            +  requestServletUrl
                            + "' detected, redirecting to canonical URL of '"
                            + pwmServletDefinition.servletUrl() + "'");
                    StatisticsManager.incrementStat(pwmRequest, Statistic.OBSOLETE_URL_REQUESTS);
                    pwmRequest.sendRedirect(pwmServletDefinition);
                    return ProcessStatus.Halt;
                }
            }

        }

        return ProcessStatus.Continue;
    }

    @Override
    boolean isInterested(final PwmApplicationMode mode, final PwmURL pwmURL) {
        return !pwmURL.isStandaloneWebService();
    }
}
