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

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmResponse;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class ControlledPwmServlet extends AbstractPwmServlet implements PwmServlet
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( AbstractPwmServlet.class );

    private Map<String, Method> actionMethodCache;

    public String servletUriRemainder( final PwmRequest pwmRequest, final String command ) throws PwmUnrecoverableException
    {
        String uri = pwmRequest.getURLwithoutQueryString();
        if ( uri.startsWith( pwmRequest.getContextPath() ) )
        {
            uri = uri.substring( pwmRequest.getContextPath().length(), uri.length() );
        }
        for ( final String servletUri : getServletDefinition().urlPatterns() )
        {
            if ( uri.startsWith( servletUri ) )
            {
                uri = uri.substring( servletUri.length(), uri.length() );
            }
        }
        return uri;
    }

    protected PwmServletDefinition getServletDefinition( )
    {
        for ( final PwmServletDefinition pwmServletDefinition : PwmServletDefinition.values() )
        {
            final Class pwmServletClass = pwmServletDefinition.getPwmServletClass();
            if ( pwmServletClass.isInstance( this ) )
            {
                return pwmServletDefinition;
            }
        }
        throw new IllegalStateException( "unable to determine PwmServletDefinition for class " + this.getClass().getName() );
    }

    public abstract Class<? extends ProcessAction> getProcessActionsClass( );

    protected ProcessAction readProcessAction( final PwmRequest request )
            throws PwmUnrecoverableException
    {
        try
        {
            final String inputParameter = request.readParameterAsString( PwmConstants.PARAM_ACTION_REQUEST );
            final Class processStatusClass = getProcessActionsClass();
            final Enum answer = JavaHelper.readEnumFromString( processStatusClass, null, inputParameter );
            return ( ProcessAction ) answer;
        }
        catch ( Exception e )
        {
            LOGGER.error( "error", e );
        }
        return null;
    }

    private ProcessStatus dispatchMethod(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {

        final ProcessAction action = readProcessAction( pwmRequest );
        if ( action == null )
        {
            return ProcessStatus.Continue;
        }
        try
        {
            final Method interestedMethod = discoverMethodForAction( this.getClass(), action );
            if ( interestedMethod != null )
            {
                interestedMethod.setAccessible( true );
                return ( ProcessStatus ) interestedMethod.invoke( this, pwmRequest );
            }
        }
        catch ( InvocationTargetException e )
        {
            final Throwable cause = e.getCause();
            if ( cause != null )
            {
                if ( cause instanceof PwmUnrecoverableException )
                {
                    throw ( PwmUnrecoverableException ) cause;
                }
                final String msg = "unexpected error during action handler for '"
                        + this.getClass().getName()
                        + ":" + action + "', error: " + cause.getMessage();
                LOGGER.error( pwmRequest, msg, e.getCause() );
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_UNKNOWN, msg ) );
            }
            LOGGER.error( "uncased invocation error: " + e.getMessage(), e );
        }
        catch ( Throwable e )
        {
            final String msg = "unexpected error invoking action handler for '" + action + "', error: " + e.getMessage();
            LOGGER.error( msg, e );
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_UNKNOWN, msg ) );
        }

        final String msg = "missing action handler for '" + action + "'";
        LOGGER.error( msg );
        throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_UNKNOWN, msg ) );
    }

    protected void processAction( final PwmRequest pwmRequest )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        preProcessCheck( pwmRequest );

        final ProcessAction action = readProcessAction( pwmRequest );
        if ( action != null )
        {
            final ProcessStatus status = dispatchMethod( pwmRequest );
            if ( status == ProcessStatus.Halt )
            {
                if ( !pwmRequest.getPwmResponse().isCommitted() )
                {
                    if ( pwmRequest.getConfig().isDevDebugMode() )
                    {
                        final String msg = "processing complete, handler returned halt but response is not committed";
                        LOGGER.error( pwmRequest, msg, new IllegalStateException( msg ) );
                    }
                }
                return;
            }

            final boolean enablePostRedirectGet = Boolean.parseBoolean( pwmRequest.getConfig().readAppProperty( AppProperty.HTTP_SERVLET_ENABLE_POST_REDIRECT_GET ) );
            if ( enablePostRedirectGet )
            {
                final String servletUrl = pwmRequest.getURL().determinePwmServletPath();
                LOGGER.debug( pwmRequest, "this request is not idempotent, redirecting to self with no action" );
                sendOtherRedirect( pwmRequest, servletUrl );
                return;
            }
        }

        examineLastError( pwmRequest );

        if ( !pwmRequest.getPwmResponse().isCommitted() )
        {
            nextStep( pwmRequest );
        }
    }

    protected abstract void nextStep( PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException;

    public abstract ProcessStatus preProcessCheck( PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException;

    private void sendOtherRedirect( final PwmRequest pwmRequest, final String location ) throws IOException, PwmUnrecoverableException
    {
        final String protocol = pwmRequest.getHttpServletRequest().getProtocol();
        if ( protocol != null && protocol.startsWith( "HTTP/1.0" ) )
        {
            pwmRequest.sendRedirect( location );
        }
        else
        {
            pwmRequest.getPwmResponse().sendRedirect( location, PwmResponse.RedirectType.Other_303 );
        }
    }

    @Retention( RetentionPolicy.RUNTIME )
    public @interface ActionHandler
    {
        String action( );
    }

    private Method discoverMethodForAction( final Class clazz, final ProcessAction action )
    {
        if ( actionMethodCache == null )
        {
            final Map<String, Method> map = new HashMap<>();
            final Collection<Method> methods = JavaHelper.getAllMethodsForClass( clazz );
            for ( Method method : methods )
            {
                if ( method.getAnnotation( ActionHandler.class ) != null )
                {
                    final String actionName = method.getAnnotation( ActionHandler.class ).action();
                    map.put( actionName, method );

                }
            }
            actionMethodCache = Collections.unmodifiableMap( map );
        }

        return actionMethodCache.get( action.toString() );
    }
}

