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

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.stats.EpsStatistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet event listener, defined in web.xml.
 *
 * @author Jason D. Rivard
 */
public class HttpEventManager implements
        ServletContextListener,
        HttpSessionListener,
        HttpSessionActivationListener,
        ServletRequestListener
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HttpEventManager.class );

    public HttpEventManager( )
    {
    }

    @Override
    public void sessionCreated( final HttpSessionEvent httpSessionEvent )
    {
        final HttpSession httpSession = httpSessionEvent.getSession();
        try
        {
            final ContextManager contextManager = ContextManager.getContextManager( httpSession.getServletContext() );
            final PwmApplication pwmApplication = contextManager.getPwmApplication();
            httpSession.setAttribute( PwmConstants.SESSION_ATTR_PWM_APP_NONCE, pwmApplication.getRuntimeNonce() );

            StatisticsClient.updateEps( pwmApplication, EpsStatistic.SESSIONS );

            LOGGER.trace( () -> "new http session created" );

        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.warn( () -> "error during sessionCreated event: " + e.getMessage() );
        }
    }

    @Override
    public void sessionDestroyed( final HttpSessionEvent httpSessionEvent )
    {
        final HttpSession httpSession = httpSessionEvent.getSession();
        final Map<DomainID, PwmSession> domainIDPwmSessionMap = PwmSessionFactory.getDomainSessionMap( httpSession );
        for ( final PwmSession pwmSession : domainIDPwmSessionMap.values() )
        {
            try
            {
                if ( httpSession.getAttribute( PwmConstants.SESSION_ATTR_PWM_SESSION ) != null )
                {
                    final String debugMsg = "destroyed session" + ": " + makeSessionDestroyedDebugMsg( pwmSession );
                    pwmSession.unAuthenticateUser( null );

                    final PwmApplication pwmApplication = ContextManager.getPwmApplication( httpSession.getServletContext() );
                    if ( pwmApplication != null )
                    {
                        pwmApplication.getSessionTrackService().removeSessionData( pwmSession );
                    }
                    LOGGER.trace( () -> debugMsg );
                }
                else
                {
                    LOGGER.trace( () -> "invalidated uninitialized session" );
                }
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.warn( () -> "error during httpSessionDestroyed: " + e.getMessage() );
            }
        }
    }


    @Override
    public void contextInitialized( final ServletContextEvent servletContextEvent )
    {
        Logger.getLogger( "org.glassfish.jersey" ).setLevel( Level.SEVERE );

        if ( null != servletContextEvent.getServletContext().getAttribute( PwmConstants.CONTEXT_ATTR_CONTEXT_MANAGER ) )
        {
            LOGGER.warn( () -> "notice, previous servlet ContextManager exists" );
        }

        try
        {
            final ContextManager newContextManager = new ContextManager( servletContextEvent.getServletContext() );
            newContextManager.initialize();
            servletContextEvent.getServletContext().setAttribute( PwmConstants.CONTEXT_ATTR_CONTEXT_MANAGER, newContextManager );
        }
        catch ( final OutOfMemoryError e )
        {
            LOGGER.fatal( () -> "JAVA OUT OF MEMORY ERROR!, please allocate more memory for java: " + e.getMessage(), e );
            throw e;
        }
        catch ( final Exception e )
        {
            LOGGER.fatal( () -> "error initializing context: " + e, e );
            System.err.println( "error initializing context: " + e );
            System.out.println( "error initializing context: " + e );
        }
    }

    @Override
    public void contextDestroyed( final ServletContextEvent servletContextEvent )
    {
        try
        {
            final ContextManager contextManager = ContextManager.getContextManager( servletContextEvent.getServletContext() );
            contextManager.shutdown();
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.error( () -> "unable to destroy context: " + e.getMessage() );
        }
    }


    @Override
    public void sessionWillPassivate( final HttpSessionEvent event )
    {
    }

    @Override
    public void sessionDidActivate( final HttpSessionEvent event )
    {
    }

    private static String makeSessionDestroyedDebugMsg( final PwmSession pwmSession )
    {
        final LocalSessionStateBean sessionStateBean = pwmSession.getSessionStateBean();
        final Map<String, String> debugItems = new LinkedHashMap<>();
        debugItems.put( "requests", sessionStateBean.getRequestCount().toString() );
        final Instant startTime = sessionStateBean.getSessionCreationTime();
        final Instant lastAccessedTime = sessionStateBean.getSessionLastAccessedTime();
        if ( startTime != null && lastAccessedTime != null )
        {
            final TimeDuration timeDuration = TimeDuration.between( startTime, lastAccessedTime );
            debugItems.put( "firstToLastRequestInterval", timeDuration.asCompactString() );
        }
        final TimeDuration avgReqDuration =  TimeDuration.fromDuration( sessionStateBean.getAvgRequestDuration().getAverageAsDuration() );
        debugItems.put( "avgRequestDuration", avgReqDuration.asCompactString() );
        return StringUtil.mapToString( debugItems );
    }

    @Override
    public void requestDestroyed( final ServletRequestEvent sre )
    {
        ServletRequestListener.super.requestDestroyed( sre );
    }

    @Override
    public void requestInitialized( final ServletRequestEvent sre )
    {
        ServletRequestListener.super.requestInitialized( sre );
    }
}

