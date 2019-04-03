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

package password.pwm.ws.server;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import lombok.Data;
import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.filter.RequestInitializationFilter;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public abstract class RestServlet extends HttpServlet
{
    private static final AtomicLoopIntIncrementer REQUEST_COUNTER = new AtomicLoopIntIncrementer( Integer.MAX_VALUE );

    private static final PwmLogger LOGGER = PwmLogger.forClass( RestServlet.class );

    protected void service( final HttpServletRequest req, final HttpServletResponse resp )
            throws ServletException, IOException
    {
        final Instant startTime = Instant.now();

        RestResultBean restResultBean = RestResultBean.fromError( new ErrorInformation( PwmError.ERROR_APP_UNAVAILABLE ), true );

        final PwmApplication pwmApplication;
        try
        {
            pwmApplication = ContextManager.getContextManager( req.getServletContext() ).getPwmApplication();
        }
        catch ( PwmUnrecoverableException e )
        {
            outputRestResultBean( restResultBean, req, resp );
            return;
        }

        final Locale locale;
        {
            final List<Locale> knownLocales = pwmApplication.getConfig().getKnownLocales();
            locale = LocaleHelper.localeResolver( req.getLocale(), knownLocales );
        }

        final SessionLabel sessionLabel;
        try
        {
            sessionLabel = new SessionLabel(
                    "rest-" + REQUEST_COUNTER.next(),
                    null,
                    null,
                    RequestInitializationFilter.readUserIPAddress( req, pwmApplication.getConfig() ),
                    RequestInitializationFilter.readUserHostname( req, pwmApplication.getConfig() )
            );
        }
        catch ( PwmUnrecoverableException e )
        {
            restResultBean = RestResultBean.fromError(
                    e.getErrorInformation(),
                    pwmApplication,
                    locale,
                    pwmApplication.getConfig(),
                    pwmApplication.determineIfDetailErrorMsgShown()
            );
            outputRestResultBean( restResultBean, req, resp );
            return;
        }

        try
        {
            if ( LOGGER.isEnabled( PwmLogLevel.TRACE ) )
            {
                final PwmHttpRequestWrapper httpRequestWrapper = new PwmHttpRequestWrapper( req, pwmApplication.getConfig() );
                final String debutTxt = httpRequestWrapper.debugHttpRequestToString( null, true );
                LOGGER.trace( sessionLabel, () -> "incoming HTTP REST request: " + debutTxt );
            }
        }
        catch ( PwmUnrecoverableException e )
        {
            LOGGER.error( "error while trying to log HTTP request data " + e.getMessage(), e );
        }

        if ( pwmApplication.getApplicationMode() != PwmApplicationMode.RUNNING )
        {
            outputRestResultBean( restResultBean, req, resp );
            return;
        }

        if ( !pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.ENABLE_EXTERNAL_WEBSERVICES ) )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "webservices are not enabled" );
            restResultBean = RestResultBean.fromError( errorInformation, pwmApplication, locale, pwmApplication.getConfig(), pwmApplication.determineIfDetailErrorMsgShown() );
            outputRestResultBean( restResultBean, req, resp );
            return;
        }

        try
        {
            final RestAuthentication restAuthentication = new RestAuthenticationProcessor( pwmApplication, sessionLabel, req ).readRestAuthentication();
            LOGGER.debug( sessionLabel, () -> "rest request authentication status: " + JsonUtil.serialize( restAuthentication ) );

            final RestRequest restRequest = RestRequest.forRequest( pwmApplication, restAuthentication, sessionLabel, req );

            RequestInitializationFilter.addStaticResponseHeaders( pwmApplication, resp );

            preCheck( restRequest );

            preCheckRequest( restRequest );

            restResultBean = invokeWebService( restRequest );
        }
        catch ( PwmUnrecoverableException e )
        {
            restResultBean = RestResultBean.fromError(
                    e.getErrorInformation(),
                    pwmApplication,
                    locale,
                    pwmApplication.getConfig(),
                    pwmApplication.determineIfDetailErrorMsgShown()
            );
        }
        catch ( Throwable e )
        {
            final String errorMsg = "internal error during rest service invocation: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            restResultBean = RestResultBean.fromError( errorInformation, pwmApplication, locale, pwmApplication.getConfig(), pwmApplication.determineIfDetailErrorMsgShown() );
            LOGGER.error( sessionLabel, errorInformation, e );
        }

        outputRestResultBean( restResultBean, req, resp );
        final boolean success = restResultBean != null && !restResultBean.isError();
        LOGGER.trace( sessionLabel, () -> "completed rest invocation in " + TimeDuration.compactFromCurrent( startTime ) + " success=" + success );
    }

    private RestResultBean invokeWebService( final RestRequest restRequest ) throws IOException, PwmUnrecoverableException
    {
        final Method interestedMethod = discoverMethodForAction( this.getClass(), restRequest );

        if ( interestedMethod != null )
        {
            interestedMethod.setAccessible( true );
            try
            {
                return ( RestResultBean ) interestedMethod.invoke( this, restRequest );
            }
            catch ( InvocationTargetException e )
            {
                final Throwable rootException = e.getTargetException();
                if ( rootException instanceof PwmUnrecoverableException )
                {
                    throw ( PwmUnrecoverableException ) rootException;
                }
                throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, e.getMessage() );
            }
            catch ( IllegalAccessException e )
            {
                throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, e.getMessage() );
            }
        }
        return null;
    }

    @Data
    private static class MethodMatcher
    {
        boolean methodMatch;
        boolean contentMatch;
        boolean acceptMatch;
    }

    private Method discoverMethodForAction( final Class clazz, final RestRequest restRequest )
            throws PwmUnrecoverableException
    {
        final HttpMethod reqMethod = restRequest.getMethod();
        final HttpContentType reqContent = restRequest.readContentType();
        final HttpContentType reqAccept = restRequest.readAcceptType();

        final boolean careAboutContentType = reqMethod.isHasBody();

        final MethodMatcher anyMatch = new MethodMatcher();

        final Collection<Method> methods = JavaHelper.getAllMethodsForClass( clazz );
        for ( Method method : methods )
        {
            final RestMethodHandler annotation = method.getAnnotation( RestMethodHandler.class );
            final MethodMatcher loopMatch = new MethodMatcher();

            if ( annotation != null )
            {
                if ( annotation.method().length == 0 || Arrays.asList( annotation.method() ).contains( reqMethod ) )
                {
                    loopMatch.setMethodMatch( true );
                    anyMatch.setMethodMatch( true );
                }

                if ( !careAboutContentType || annotation.consumes().length == 0 || Arrays.asList( annotation.consumes() ).contains( reqContent ) )
                {
                    loopMatch.setContentMatch( true );
                    anyMatch.setContentMatch( true );
                }

                if ( annotation.produces().length == 0 || Arrays.asList( annotation.produces() ).contains( reqAccept ) )
                {
                    loopMatch.setAcceptMatch( true );
                    anyMatch.setAcceptMatch( true );
                }

                if ( loopMatch.isMethodMatch() && loopMatch.isContentMatch() && loopMatch.isAcceptMatch() )
                {
                    return method;
                }
            }
        }

        final String errorMsg;
        if ( !anyMatch.isMethodMatch() )
        {
            errorMsg = "HTTP method invalid";
        }
        else if ( reqAccept == null && !anyMatch.isAcceptMatch() )
        {
            errorMsg = HttpHeader.Accept.getHttpName() + " header is required";
        }
        else if ( !anyMatch.isAcceptMatch() )
        {
            errorMsg = HttpHeader.Accept.getHttpName() + " header value does not match an available processor";
        }
        else if ( reqContent == null && !anyMatch.isContentMatch() )
        {
            errorMsg = HttpHeader.ContentType.getHttpName() + " header is required";
        }
        else if ( !anyMatch.isContentMatch() )
        {
            errorMsg = HttpHeader.ContentType.getHttpName() + " header value does not match an available processor";
        }
        else
        {
            errorMsg = "incorrect method, Content-Type header, or Accept header.";
        }

        throw PwmUnrecoverableException.newException( PwmError.ERROR_REST_INVOCATION_ERROR, errorMsg );
    }

    private void preCheck( final RestRequest restRequest )
            throws PwmUnrecoverableException
    {
        final RestWebServer classAnnotation = this.getClass().getDeclaredAnnotation( RestWebServer.class );
        if ( classAnnotation == null )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, "class is missing " + RestWebServer.class.getSimpleName() + " annotation" );
        }


        if ( classAnnotation.requireAuthentication() )
        {
            if ( restRequest.getRestAuthentication().getType() == RestAuthenticationType.PUBLIC )
            {
                throw PwmUnrecoverableException.newException( PwmError.ERROR_UNAUTHORIZED, "this service requires authentication" );
            }

            if ( !restRequest.getRestAuthentication().getUsages().contains( classAnnotation.webService() ) )
            {
                throw PwmUnrecoverableException.newException(
                        PwmError.ERROR_UNAUTHORIZED,
                        "access to " + classAnnotation.webService() + " service is not permitted for this login"
                );
            }
        }

        if ( restRequest.getMethod().isHasBody() )
        {
            if ( restRequest.readContentType() == null )
            {
                final String message = restRequest.getMethod() + " method requires " + HttpHeader.ContentType.getHttpName() + " header";
                throw PwmUnrecoverableException.newException( PwmError.ERROR_UNAUTHORIZED, message );
            }
        }
    }

    public abstract void preCheckRequest( RestRequest request ) throws PwmUnrecoverableException;

    private void outputRestResultBean(
            final RestResultBean restResultBean,
            final HttpServletRequest request,
            final HttpServletResponse resp
    )
            throws IOException
    {
        final HttpContentType acceptType = RestRequest.readAcceptType( request );
        resp.setHeader( HttpHeader.Server.getHttpName(), PwmConstants.PWM_APP_NAME );

        if ( acceptType != null )
        {
            switch ( acceptType )
            {
                case json:
                {
                    resp.setHeader( HttpHeader.ContentType.getHttpName(), HttpContentType.json.getHeaderValue() );
                    final String formatParameter = request.getParameter( "format" );
                    try ( PrintWriter pw = resp.getWriter() )
                    {
                        if ( "pretty".equalsIgnoreCase( formatParameter ) )
                        {
                            pw.write( JsonUtil.serialize( restResultBean, JsonUtil.Flag.PrettyPrint ) );
                        }
                        else
                        {
                            pw.write( restResultBean.toJson() );
                        }
                    }
                }
                break;

                case plain:
                {
                    resp.setHeader( HttpHeader.ContentType.getHttpName(), HttpContentType.plain.getHeaderValue() );
                    if ( restResultBean.isError() )
                    {
                        resp.sendError( HttpServletResponse.SC_INTERNAL_SERVER_ERROR, restResultBean.getErrorMessage() );
                    }
                    else
                    {
                        if ( restResultBean.getData() != null )
                        {
                            try ( PrintWriter pw = resp.getWriter() )
                            {
                                final String output = String.valueOf( restResultBean.getData() );
                                pw.write( output );
                            }
                        }
                    }
                }
                break;

                default:
                {
                    final String msg = "unhandled " + HttpHeader.Accept.getHttpName() + " header value in request";
                    outputLastHopeError( msg, resp );
                }
            }
        }
        else
        {
            final String msg;
            if ( StringUtil.isEmpty( request.getHeader( HttpHeader.Accept.getHttpName() ) ) )
            {
                msg = "missing " + HttpHeader.Accept.getHttpName() + " header value in request";
            }
            else
            {
                msg = "unknown value for " + HttpHeader.Accept.getHttpName() + " header value in request";
            }
            outputLastHopeError( msg, resp );
        }
    }

    private static void outputLastHopeError( final String msg, final HttpServletResponse response ) throws IOException
    {
        response.setStatus( HttpServletResponse.SC_INTERNAL_SERVER_ERROR );
        response.setHeader( HttpHeader.ContentType.getHttpName(), HttpContentType.json.getHeaderValue() );
        try ( PrintWriter pw = response.getWriter() )
        {
            pw.write( "Error: " );
            pw.write( msg );
            pw.write( "\n" );
        }
    }

    @Value
    public static class TargetUserIdentity
    {
        private RestRequest restRequest;
        private UserIdentity userIdentity;
        private boolean self;

        public ChaiProvider getChaiProvider( ) throws PwmUnrecoverableException
        {
            return restRequest.getChaiProvider( userIdentity.getLdapProfileID() );
        }

        public ChaiUser getChaiUser( ) throws PwmUnrecoverableException
        {
            try
            {
                return getChaiProvider().getEntryFactory().newChaiUser( userIdentity.getUserDN() );
            }
            catch ( ChaiUnavailableException e )
            {
                throw PwmUnrecoverableException.fromChaiException( e );
            }
        }
    }

}
