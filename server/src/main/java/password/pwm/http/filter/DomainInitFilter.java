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

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmURL;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DomainInitFilter implements Filter
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DomainInitFilter.class );

    @Override
    public void init( final FilterConfig filterConfig )
            throws ServletException
    {
    }

    @Override
    public void destroy()
    {
    }

    @Override
    public void doFilter(
            final ServletRequest servletRequest,
            final ServletResponse servletResponse,
            final FilterChain filterChain
    )
            throws IOException, ServletException
    {
        final HttpServletRequest req = ( HttpServletRequest ) servletRequest;
        final HttpServletResponse resp = ( HttpServletResponse ) servletResponse;

        final PwmApplication localPwmApplication;
        try
        {
            localPwmApplication = ContextManager.getPwmApplication( req );
        }
        catch ( final PwmException e )
        {
            LOGGER.error( () -> "unable to load pwmApplication: " + e.getMessage() );
            throw new ServletException( e.getMessage() );
        }

        if ( initializeDomainIdInRequest( localPwmApplication, req, resp ) == ProcessStatus.Halt )
        {
            return;
        }

        filterChain.doFilter( req, resp );
    }

    ProcessStatus initializeDomainIdInRequest(
            final PwmApplication pwmApplication,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException
    {
        if ( pwmApplication.isMultiDomain() )
        {
            final Optional<DomainID> requestDomainID = readDomainFromRequest( pwmApplication, req );
            if ( requestDomainID.isPresent() )
            {
                req.setAttribute( PwmConstants.REQUEST_ATTR_DOMAIN, requestDomainID.get().stringValue() );
            }
            else
            {
                try
                {
                    final boolean pathMode = pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.DOMAIN_DOMAIN_PATHS );
                    if ( pathMode )
                    {
                        final DomainID redirectDomain = pwmApplication.getAdminDomain().getDomainID();
                        final String redirectUrl = req.getContextPath() + "/" + redirectDomain.stringValue() + "/";
                        resp.sendRedirect( redirectUrl );
                        LOGGER.debug( () -> "request does not indicate domain, redirecting to admin domain url: " + redirectUrl );
                        return ProcessStatus.Halt;
                    }
                    else
                    {
                        throw new IllegalStateException( "domain not specified in request and admin domain is not configured." );
                    }
                }
                catch ( final PwmUnrecoverableException e )
                {
                    final String msg = "error redirecting non-domain request to admin domain: " + e.getMessage();
                    resp.sendError( 500, msg );
                    LOGGER.error( () -> msg );
                    return ProcessStatus.Halt;
                }
            }
        }
        else
        {
            final String domainStr = pwmApplication.getConfig().getDomainIDs().iterator().next();
            req.setAttribute( PwmConstants.REQUEST_ATTR_DOMAIN, domainStr );
        }

        return ProcessStatus.Continue;
    }

    public static Optional<DomainID> readDomainFromRequest( final PwmApplication pwmApplication, final HttpServletRequest req )
    {
        final boolean pathMode = pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.DOMAIN_DOMAIN_PATHS );
        if ( pathMode )
        {
            final Optional<DomainID> readDomainID = readDomainFromPathRequest( pwmApplication, req );
            if ( readDomainID.isPresent() )
            {
                return readDomainID;
            }
        }

        final Optional<DomainID> readDomainID = readDomainFromDomainRequest( pwmApplication, req );
        if ( readDomainID.isPresent() )
        {
            return readDomainID;
        }

        if ( !pathMode )
        {
            try
            {
                return Optional.of( pwmApplication.getAdminDomain().getDomainID() );
            }
            catch ( final PwmUnrecoverableException e )
            {
                throw new IllegalStateException( "domain not specified in request and admin domain is not configured." );
            }
        }

        return Optional.empty();
    }

    private static Optional<DomainID> readDomainFromDomainRequest( final PwmApplication pwmApplication, final HttpServletRequest req )
    {
        final URI uri = URI.create( req.getRequestURL().toString() );
        final String host = uri.getHost();

        for ( final PwmDomain pwmDomain : pwmApplication.domains().values() )
        {
            final List<String> hostMatches = pwmDomain.getConfig().readSettingAsStringArray( PwmSetting.DOMAIN_HOSTS );
            if ( hostMatches.contains( host ) )
            {
                return Optional.of( pwmDomain.getDomainID() );
            }
        }

        return Optional.empty();
    }

    private static Optional<DomainID> readDomainFromPathRequest( final PwmApplication pwmApplication, final HttpServletRequest req )
    {
        final PwmURL pwmURL = PwmURL.create( req, pwmApplication.getConfig() );
        final List<String> urlPaths = pwmURL.splitPaths();
        if ( urlPaths.size() <= 1 )
        {
            return Optional.empty();
        }

        final String domainPath = urlPaths.get( 1 );

        final Set<String> domains = pwmApplication.getConfig().getDomainIDs();

        if ( domains.contains( domainPath ) )
        {
            return Optional.of( DomainID.create( domainPath ) );
        }
        return Optional.empty();
    }
}
