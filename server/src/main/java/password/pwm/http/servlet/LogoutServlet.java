/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.http.servlet;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmSession;
import password.pwm.http.PwmURL;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@WebServlet(
        name = "LogoutServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + "/logout",
                PwmConstants.URL_PREFIX_PRIVATE + "/logout",
                PwmConstants.URL_PREFIX_PUBLIC + "/Logout",
                PwmConstants.URL_PREFIX_PRIVATE + "/Logout",
        }
)
public class LogoutServlet extends ControlledPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LogoutServlet.class );

    private static final String PARAM_URL = "url";
    private static final String PARAM_IDLE = "idle";
    private static final String PARAM_PASSWORD_MODIFIED = "passwordModified";
    private static final String PARAM_PUBLIC_ONLY = "publicOnly";

    private enum LogoutAction implements ControlledPwmServlet.ProcessAction
    {
        showLogout,
        showTimeout,;


        @Override
        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( HttpMethod.GET );
        }
    }

    @Override
    public Class<? extends ProcessAction> getProcessActionsClass( )
    {
        return LogoutAction.class;
    }

    @ActionHandler( action = "showLogout" )
    public ProcessStatus processShowLogout(
            final PwmRequest pwmRequest
    )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final Optional<String> nextUrl = readAndValidateNextUrlParameter( pwmRequest );
        if ( nextUrl.isPresent() )
        {
            pwmRequest.setAttribute( PwmRequestAttribute.NextUrl, nextUrl.get() );
        }
        pwmRequest.forwardToJsp( JspUrl.LOGOUT );
        return ProcessStatus.Halt;
    }


    @ActionHandler( action = "showTimeout" )
    public ProcessStatus processShowTimeout(
            final PwmRequest pwmRequest
    )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        pwmRequest.forwardToJsp( JspUrl.LOGOUT_PUBLIC );
        return ProcessStatus.Halt;
    }

    public void nextStep(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException
    {
        final String nextUrl;
        if ( !pwmRequest.getPwmSession().getSessionStateBean().isPasswordModified() )
        {
            nextUrl = readAndValidateNextUrlParameter( pwmRequest ).orElse( null );
        }
        else
        {
            nextUrl = null;
        }

        // no process action so this is the first time through this method;
        final boolean authenticated = pwmRequest.isAuthenticated();
        final boolean logoutDueToIdle = Boolean.parseBoolean( pwmRequest.readParameterAsString( PARAM_IDLE ) );

        String debugMsg = "processing " + ( authenticated ? "authenticated" : "unauthenticated" ) + " logout request";
        if ( logoutDueToIdle )
        {
            debugMsg += " due to client idle timeout";
        }
        LOGGER.debug( pwmRequest, debugMsg );

        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        pwmSession.unauthenticateUser( pwmRequest );

        {
            //if there is a session url, then use that to do a redirect.
            final String sessionLogoutURL = pwmSession.getSessionStateBean().getLogoutURL();
            if ( sessionLogoutURL != null && sessionLogoutURL.length() > 0 )
            {
                LOGGER.trace( pwmSession, "redirecting user to session parameter set logout url: " + sessionLogoutURL );
                pwmRequest.sendRedirect( sessionLogoutURL );
                pwmRequest.invalidateSession();
                return;
            }
        }

        {
            // if the logout url hasn't been set then try seeing if one has been configured.
            final String configuredLogoutURL = pwmApplication.getConfig().readSettingAsString( PwmSetting.URL_LOGOUT );
            if ( configuredLogoutURL != null && configuredLogoutURL.length() > 0 )
            {

                // construct params
                final Map<String, String> logoutUrlParameters = new LinkedHashMap<>();
                logoutUrlParameters.put( PARAM_IDLE, String.valueOf( logoutDueToIdle ) );
                logoutUrlParameters.put( PARAM_PASSWORD_MODIFIED, String.valueOf( pwmSession.getSessionStateBean().isPasswordModified() ) );
                logoutUrlParameters.put( PARAM_PUBLIC_ONLY, String.valueOf( !pwmSession.getSessionStateBean().isPrivateUrlAccessed() ) );

                {
                    final String sessionForwardURL = pwmSession.getSessionStateBean().getForwardURL();
                    if ( sessionForwardURL != null && sessionForwardURL.length() > 0 )
                    {
                        logoutUrlParameters.put(
                                pwmApplication.getConfig().readAppProperty( AppProperty.HTTP_PARAM_NAME_FORWARD_URL ),
                                sessionForwardURL
                        );
                    }
                }

                final String logoutURL = PwmURL.appendAndEncodeUrlParameters( configuredLogoutURL, logoutUrlParameters );

                LOGGER.trace( pwmSession, "redirecting user to configured logout url:" + logoutURL );
                pwmRequest.sendRedirect( logoutURL );
                pwmRequest.invalidateSession();
                return;
            }
        }

        // if we didn't go anywhere yet, then show the pwm logout jsp
        final LogoutAction nextAction = authenticated ? LogoutAction.showLogout : LogoutAction.showTimeout;

        final Map<String, String> logoutUrlParameters = new LinkedHashMap<>();
        logoutUrlParameters.put( PwmConstants.PARAM_ACTION_REQUEST, nextAction.toString() );
        if ( nextUrl != null )
        {
            logoutUrlParameters.put( PARAM_URL, nextUrl );
        }
        final String logoutURL = PwmURL.appendAndEncodeUrlParameters(
                pwmRequest.getContextPath() + PwmServletDefinition.Logout.servletUrl(),
                logoutUrlParameters
        );
        pwmRequest.invalidateSession();
        pwmRequest.sendRedirect( logoutURL );
    }


    private static Optional<String> readAndValidateNextUrlParameter(
            final PwmRequest pwmRequest
    )
    {
        try
        {
            if ( !pwmRequest.hasParameter( PARAM_URL ) )
            {
                return Optional.empty();
            }

            if ( pwmRequest.getPwmSession().getSessionStateBean().isPasswordModified() )
            {
                return Optional.empty();
            }


            final String urlParameter = pwmRequest.readParameterAsString( PARAM_URL );
            final URI uri = URI.create( urlParameter );
            String path = uri.getPath();
            if ( path != null && path.startsWith( pwmRequest.getContextPath() ) )
            {
                path = path.substring( pwmRequest.getContextPath().length(), path.length() );

            }
            PwmServletDefinition matchedServlet = null;

            outerLoop:
            for ( final PwmServletDefinition pwmServletDefinition : PwmServletDefinition.values() )
            {
                for ( final String urlPattern : pwmServletDefinition.urlPatterns() )
                {
                    if ( urlPattern.equals( path ) )
                    {
                        matchedServlet = pwmServletDefinition;
                        break outerLoop;
                    }
                }
            }

            if ( matchedServlet == PwmServletDefinition.Logout )
            {
                matchedServlet = null;
            }

            if ( matchedServlet != null )
            {
                LOGGER.trace( pwmRequest, "matched next url to servlet definition " + matchedServlet.toString() );
                return Optional.of( pwmRequest.getContextPath() + matchedServlet.servletUrl() );
            }
            else
            {
                LOGGER.trace( pwmRequest, "unable to match next url parameter to servlet definition" );
            }


        }
        catch ( Exception e )
        {
            LOGGER.debug( "error parsing client specified url parameter: " + e.getMessage() );
        }
        return Optional.empty();
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest )
    {
        // no checks required, this is a public module.
        return ProcessStatus.Continue;
    }
}
