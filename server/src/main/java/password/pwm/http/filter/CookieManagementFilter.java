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

package password.pwm.http.filter;

import password.pwm.DomainProperty;
import password.pwm.PwmApplicationMode;
import password.pwm.config.DomainConfig;
import password.pwm.error.PwmException;
import password.pwm.http.HttpHeader;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.PwmURL;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

public class CookieManagementFilter extends AbstractPwmFilter
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( CookieManagementFilter.class );

    @Override
    void processFilter( final PwmApplicationMode mode, final PwmRequest pwmRequest, final PwmFilterChain filterChain )
            throws PwmException, IOException, ServletException
    {
        filterChain.doFilter();

        if ( !pwmRequest.hasSession() )
        {
            return;
        }

        markSessionForRecycle( pwmRequest );

        addSameSiteCookieAttribute( pwmRequest );
    }

    @Override
    boolean isInterested( final PwmApplicationMode mode, final PwmURL pwmURL )
    {
        return true;
    }

    /**
     * Ensures that every session that modifies its samesite cookies also triggers a session ID
     * recycle, once per session.
     *
     * @param pwmRequest The request to be marked
     */
    private void markSessionForRecycle( final PwmRequest pwmRequest )
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        if ( pwmSession != null )
        {
            if ( !pwmSession.getSessionStateBean().isSameSiteCookieRecycleRequested() )
            {
                pwmSession.getSessionStateBean().setSameSiteCookieRecycleRequested( true );
                pwmSession.getSessionStateBean().setSessionIdRecycleNeeded( true );
            }
        }
    }

    public static void addSameSiteCookieAttribute( final PwmRequest pwmRequest )
    {
        final Optional<String> sameSiteValue = sameSiteCookieValue( pwmRequest );
        if ( sameSiteValue.isEmpty() )
        {
            return;
        }

        final HttpServletResponse response = pwmRequest.getPwmResponse().getHttpServletResponse();
        final Collection<String> rawCookieValues = response.getHeaders( HttpHeader.SetCookie.getHttpName() );
        boolean firstHeader = true;

        for ( final String rawCookieValue : rawCookieValues )
        {
            final String newHeader;
            if ( !rawCookieValue.contains( "SameSite" ) )
            {
                newHeader = rawCookieValue + "; SameSite=" + sameSiteValue.get();
            }
            else
            {
                newHeader = rawCookieValue;
            }

            if ( firstHeader )
            {
                response.setHeader( HttpHeader.SetCookie.getHttpName(), newHeader );
                firstHeader = false;
            }
            else
            {
                response.addHeader( HttpHeader.SetCookie.getHttpName(), newHeader );
            }
        }
    }

    private static Optional<String> sameSiteCookieValue( final PwmRequest pwmRequest )
    {
        final DomainConfig domainConfig = pwmRequest.getDomainConfig();
        final String value = domainConfig.readDomainProperty( DomainProperty.HTTP_COOKIE_SAMESITE_VALUE );
        if ( StringUtil.isTrimEmpty( value ) )
        {
            return Optional.of( value );
        }

        return Optional.empty();
    }
}
