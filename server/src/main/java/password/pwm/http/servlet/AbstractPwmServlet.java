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

package password.pwm.http.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmSession;
import password.pwm.http.PwmSessionWrapper;
import password.pwm.http.bean.PwmSessionBean;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.Validator;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.SecureEngine;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;

public abstract class AbstractPwmServlet extends HttpServlet implements PwmServlet
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( AbstractPwmServlet.class );

    public void doGet(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException
    {
        this.handleRequest( req, resp, HttpMethod.GET );
    }

    public void doPost(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException
    {
        this.handleRequest( req, resp, HttpMethod.POST );
    }

    private void handleRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final HttpMethod method
    )
            throws ServletException, IOException
    {
        try
        {
            final PwmRequest pwmRequest = PwmRequest.forRequest( req, resp );

            if ( !method.isIdempotent() && !pwmRequest.getURL().isCommandServletURL() )
            {
                Validator.validatePwmFormID( pwmRequest );

                try
                {
                    Validator.validatePwmRequestCounter( pwmRequest );
                }
                catch ( final PwmOperationalException e )
                {
                    if ( e.getError() == PwmError.ERROR_INCORRECT_REQ_SEQUENCE )
                    {
                        final ErrorInformation errorInformation = e.getErrorInformation();
                        final PwmSession pwmSession = PwmSessionWrapper.readPwmSession( req );
                        LOGGER.error( pwmRequest, () -> errorInformation.toDebugStr() );
                        pwmRequest.respondWithError( errorInformation, false );
                        return;
                    }
                    throw e;
                }
            }

            // check for incorrect method type.
            final ProcessAction processAction = readProcessAction( pwmRequest );
            if ( processAction != null )
            {
                if ( !processAction.permittedMethods().contains( method ) )
                {
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE,
                            "incorrect request method " + method.toString() + " on request to " + pwmRequest.getURLwithQueryString() );
                    LOGGER.error( pwmRequest, () -> errorInformation.toDebugStr() );
                    pwmRequest.respondWithError( errorInformation, false );
                    return;
                }
            }

            this.processAction( pwmRequest );
        }
        catch ( final Exception e )
        {
            final PwmRequest pwmRequest;
            try
            {
                pwmRequest = PwmRequest.forRequest( req, resp );
            }
            catch ( final Exception e2 )
            {
                try
                {
                    LOGGER.fatal(
                            () -> "exception occurred, but exception handler unable to load request instance; error=" + e.getMessage(),
                            e );
                }
                catch ( final Exception e3 )
                {
                    e3.printStackTrace();
                }
                throw new ServletException( e );
            }

            final PwmUnrecoverableException pue = convertToPwmUnrecoverableException( e, pwmRequest );

            if ( processUnrecoverableException( req, resp, pwmRequest.getPwmApplication(), pwmRequest, pue ) )
            {
                return;
            }

            outputUnrecoverableException( pwmRequest, pue );

            clearModuleBeans( pwmRequest );
        }
    }

    private void clearModuleBeans( final PwmRequest pwmRequest )
    {
        for ( final Class theClass : PwmSessionBean.getPublicBeans() )
        {
            try
            {
                pwmRequest.getPwmApplication().getSessionStateService().clearBean( pwmRequest, theClass );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.debug( pwmRequest, () -> "error while clearing module bean during after module error output: " + e.getMessage() );
            }
        }
    }

    private PwmUnrecoverableException convertToPwmUnrecoverableException(
            final Throwable e,
            final PwmRequest pwmRequest
    )
    {
        if ( e instanceof PwmUnrecoverableException )
        {
            return ( PwmUnrecoverableException ) e;
        }

        if ( e instanceof PwmException )
        {
            return new PwmUnrecoverableException( ( ( PwmException ) e ).getErrorInformation() );
        }

        if ( e instanceof ChaiUnavailableException )
        {
            final String errorMsg = "unable to contact ldap directory: " + e.getMessage();
            return new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, errorMsg ) );
        }

        final String stackTraceText;
        {
            final StringWriter errorStack = new StringWriter();
            e.printStackTrace( new PrintWriter( errorStack ) );
            stackTraceText = errorStack.toString();
        }

        String stackTraceHash = "hash";
        try
        {
            stackTraceHash = SecureEngine.hash( stackTraceText, PwmHashAlgorithm.SHA1 );
        }
        catch ( final PwmUnrecoverableException e1 )
        {
            /* */
        }
        final String errorMsg = "unexpected error processing request: " + JavaHelper.readHostileExceptionMessage( e ) + " [" + stackTraceHash + "]";

        LOGGER.error( pwmRequest, () -> errorMsg, e );
        return new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg ) );
    }


    private boolean processUnrecoverableException(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmRequest pwmRequest,
            final PwmUnrecoverableException e
    )
            throws IOException
    {
        switch ( e.getError() )
        {
            case ERROR_DIRECTORY_UNAVAILABLE:
                LOGGER.fatal( pwmRequest.getLabel(), () -> e.getErrorInformation().toDebugStr() );
                try
                {
                    pwmApplication.getStatisticsManager().incrementValue( Statistic.LDAP_UNAVAILABLE_COUNT );
                }
                catch ( final Throwable e1 )
                {
                    //noop
                }
                break;


            case ERROR_PASSWORD_REQUIRED:
                LOGGER.warn(
                        () -> "attempt to access functionality requiring password authentication, but password not yet supplied by actor, forwarding to password Login page" );
                //store the original requested url
                try
                {
                    LOGGER.debug( pwmRequest, () -> "user is authenticated without a password, redirecting to login page" );
                    LoginServlet.redirectToLoginServlet( PwmRequest.forRequest( req, resp ) );
                    return true;
                }
                catch ( final Throwable e1 )
                {
                    LOGGER.error( () -> "error while marking pre-login url:" + e1.getMessage() );
                }
                break;


            case ERROR_INTERNAL:
            default:
                LOGGER.fatal( pwmRequest.getLabel(), () -> "unexpected error: " + e.getErrorInformation().toDebugStr() );
                try
                {
                    // try to update stats
                    if ( pwmApplication != null )
                    {
                        pwmApplication.getStatisticsManager().incrementValue( Statistic.PWM_UNKNOWN_ERRORS );
                    }
                }
                catch ( final Throwable e1 )
                {
                    //noop
                }
                break;
        }
        return false;
    }

    private void outputUnrecoverableException(
            final PwmRequest pwmRequest,
            final PwmUnrecoverableException e
    )
            throws IOException, ServletException
    {
        if ( pwmRequest.isJsonRequest() )
        {
            final RestResultBean restResultBean = RestResultBean.fromError( e.getErrorInformation(), pwmRequest );
            pwmRequest.outputJsonResult( restResultBean );
        }
        else
        {
            pwmRequest.respondWithError( e.getErrorInformation() );
        }
    }


    protected abstract void processAction( PwmRequest request )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException;

    protected abstract ProcessAction readProcessAction( PwmRequest request )
            throws PwmUnrecoverableException;

    public interface ProcessAction
    {
        Collection<HttpMethod> permittedMethods( );
    }

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

    protected void setLastError( final PwmRequest pwmRequest, final ErrorInformation errorInformation ) throws PwmUnrecoverableException
    {
        final Class<? extends PwmSessionBean> beanClass = this.getServletDefinition().getPwmSessionBeanClass();
        if ( beanClass != null )
        {
            final PwmSessionBean pwmSessionBean = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, beanClass );
            pwmSessionBean.setLastError( errorInformation );
        }

        pwmRequest.setAttribute( PwmRequestAttribute.PwmErrorInfo, errorInformation );
    }

    protected void examineLastError( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        final Class<? extends PwmSessionBean> beanClass = this.getServletDefinition().getPwmSessionBeanClass();
        final PwmSessionBean pwmSessionBean = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, beanClass );
        if ( pwmSessionBean != null && pwmSessionBean.getLastError() != null )
        {
            pwmRequest.setAttribute( PwmRequestAttribute.PwmErrorInfo, pwmSessionBean.getLastError() );
            pwmSessionBean.setLastError( null );
        }
    }
}
