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

import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Set;

public class ApplicationModeFilter extends AbstractPwmFilter
{

    private static final PwmLogger LOGGER = PwmLogger.getLogger( ApplicationModeFilter.class.getName() );

    @Override
    public void processFilter(
            final PwmApplicationMode mode,
            final PwmRequest pwmRequest,
            final PwmFilterChain chain
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        // ignore if resource request
        final PwmURL pwmURL = pwmRequest.getURL();

        if ( !pwmURL.isResourceURL() && !pwmURL.isRestService() && !pwmURL.isReferenceURL() && !pwmURL.matches( PwmServletDefinition.ClientApi ) )
        {
            // check for valid config
            try
            {
                if ( checkConfigModes( pwmRequest ) == ProcessStatus.Halt )
                {
                    return;
                }
            }
            catch ( final PwmUnrecoverableException e )
            {
                if ( e.getError() == PwmError.ERROR_INTERNAL )
                {
                    try
                    {
                        LOGGER.error( e::getMessage );
                    }
                    catch ( final Exception ignore )
                    {
                        /* noop */
                    }
                }
                pwmRequest.respondWithError( e.getErrorInformation(), true );
                return;
            }
        }

        chain.doFilter();
    }

    @Override
    boolean isInterested( final PwmApplicationMode mode, final PwmURL pwmURL )
    {
        return !pwmURL.isRestService();
    }

    private static ProcessStatus checkConfigModes(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final PwmApplicationMode mode = pwmDomain.getApplicationMode();

        final PwmURL pwmURL = pwmRequest.getURL();

        if ( mode == PwmApplicationMode.NEW )
        {
            // check if current request is actually for the config url, if it is, just do nothing.
            if ( pwmURL.isCommandServletURL() || pwmURL.isRestService() )
            {
                return ProcessStatus.Continue;
            }

            if ( pwmURL.isConfigGuideURL() || pwmURL.isReferenceURL() )
            {
                return ProcessStatus.Continue;
            }
            else
            {
                LOGGER.debug( () -> "unable to find a valid configuration, redirecting " + pwmURL + " to ConfigGuide" );
                pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.ConfigGuide );
                return ProcessStatus.Halt;
            }
        }

        if ( mode == PwmApplicationMode.ERROR )
        {
            ErrorInformation rootError = ContextManager.getContextManager( pwmRequest.getHttpServletRequest().getSession() ).getStartupErrorInformation();
            if ( rootError == null )
            {
                rootError = new ErrorInformation( PwmError.ERROR_APP_UNAVAILABLE, "Application startup failed." );
            }
            pwmRequest.respondWithError( rootError );
            return ProcessStatus.Halt;
        }

        // allow oauth
        if ( pwmURL.matches( PwmServletDefinition.OAuthConsumer ) )
        {
            return ProcessStatus.Continue;
        }

        // block if public request and not running or in trial
        if ( !PwmConstants.TRIAL_MODE )
        {
            final Set<PwmServletDefinition> permittedServlets = Set.of(
                    PwmServletDefinition.ConfigManager,
                    PwmServletDefinition.ConfigGuide,
                    PwmServletDefinition.PublicCommand,
                    PwmServletDefinition.PrivateCommand,
                    PwmServletDefinition.Login,
                    PwmServletDefinition.Logout,
                    PwmServletDefinition.OAuthConsumer,
                    PwmServletDefinition.Admin );

            if ( mode != PwmApplicationMode.RUNNING )
            {
                final boolean permittedURl = pwmURL.isResourceURL()
                        || pwmURL.isIndexPage()
                        || pwmURL.isReferenceURL()
                        || pwmURL.isRestService()
                        || pwmURL.matches( permittedServlets );

                if ( !permittedURl )
                {
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_APPLICATION_NOT_RUNNING );
                    pwmRequest.respondWithError( errorInformation );
                    return ProcessStatus.Halt;
                }
            }
        }

        return ProcessStatus.Continue;
    }
}
