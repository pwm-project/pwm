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
import password.pwm.config.option.WebServiceUsage;
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
    private static final AtomicLoopIntIncrementer REQUEST_COUNTER = new AtomicLoopIntIncrementer();

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
        catch ( final PwmUnrecoverableException e )
        {
            outputRestResultBean( restResultBean, req, resp );
            return;
        }

        final Locale locale;
        {
            final List<Locale> knownLocales = pwmApplication.getConfig().getKnownLocales();
            locale = LocaleHelper.localeResolver( req.getLocale(), knownLocales );
            resp.setHeader( HttpHeader.ContentLanguage.getHttpName(), LocaleHelper.getBrowserLocaleString( locale ) );
        }

        final SessionLabel sessionLabel;
        try
        {
            sessionLabel =  SessionLabel.builder()
                    .sessionID( "rest-" + REQUEST_COUNTER.next() )
                    .sourceAddress( RequestInitializationFilter.readUserNetworkAddress( req, pwmApplication.getConfig() ) )
                    .sourceHostname( RequestInitializationFilter.readUserHostname( req, pwmApplication.getConfig() ) )
                    .build();
        }
        catch ( final PwmUnrecoverableException e )
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
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.error( () -> "error while trying to log HTTP request data " + e.getMessage(), e );
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
        catch ( final PwmUnrecoverableException e )
        {
            restResultBean = RestResultBean.fromError(
                    e.getErrorInformation(),
                    pwmApplication,
                    locale,
                    pwmApplication.getConfig(),
                    pwmApplication.determineIfDetailErrorMsgShown()
            );
        }
        catch ( final Throwable e )
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
            catch ( final InvocationTargetException e )
            {
                final Throwable rootException = e.getTargetException();
                if ( rootException instanceof PwmUnrecoverableException )
                {
                    throw ( PwmUnrecoverableException ) rootException;
                }
                LOGGER.error( restRequest.getSessionLabel(), () -> "internal error executing rest request: " + e.getMessage(), e );
                throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, e.getMessage() );
            }
            catch ( final IllegalAccessException e )
            {
                LOGGER.error( restRequest.getSessionLabel(), () -> "internal error executing rest request: " + e.getMessage(), e );
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
            throws PwmUnrecoverableException, IOException
    {
        final HttpMethod reqMethod = restRequest.getMethod();
        final HttpContentType reqContent = restRequest.readContentType();
        final HttpContentType reqAccept = restRequest.readAcceptType();

        final boolean careAboutContentType = reqMethod.isHasBody();

        final MethodMatcher anyMatch = new MethodMatcher();

        final Collection<Method> methods = JavaHelper.getAllMethodsForClass( clazz );
        for ( final Method method : methods )
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
            errorMsg = "HTTP method unavailable";
        }
        else if ( reqAccept == null && !anyMatch.isAcceptMatch() )
        {
            errorMsg = HttpHeader.Accept.getHttpName() + " header is required";
        }
        else if ( reqContent == null && !anyMatch.isContentMatch() )
        {
            errorMsg = HttpHeader.ContentType.getHttpName() + " header is required";
        }
        else if ( !anyMatch.isAcceptMatch() )
        {
            errorMsg = HttpHeader.Accept.getHttpName() + " value is not accepted for this service";
        }
        else if ( !anyMatch.isContentMatch() )
        {
            errorMsg = HttpHeader.ContentType.getHttpName() + " value is not accepted for this service";
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

        {
            final RestAuthenticationType requestAuthType = restRequest.getRestAuthentication().getType();
            final Collection<RestAuthenticationType> supportedTypes = classAnnotation.webService().getTypes();
            if ( !supportedTypes.contains( requestAuthType ) )
            {

                final String msg;
                if ( requestAuthType == RestAuthenticationType.PUBLIC )
                {
                    msg = "this service requires authentication";
                }
                else
                {
                    msg = "authentication type " + requestAuthType + " is not supported for this service";
                }
                LOGGER.trace( restRequest.getSessionLabel(), () -> msg );
                throw PwmUnrecoverableException.newException( PwmError.ERROR_UNAUTHORIZED, msg );
            }
        }

        {
            final WebServiceUsage thisWebService = classAnnotation.webService();
            if ( !restRequest.getRestAuthentication().getUsages().contains( thisWebService ) )
            {
                LOGGER.trace( restRequest.getSessionLabel(), () -> "permission denied for request to " + thisWebService
                        + ", not a permitted usage for authentication type " + restRequest.getRestAuthentication().getType() );
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNAUTHORIZED, null );
                throw new PwmUnrecoverableException( errorInformation );
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
                    resp.setHeader( HttpHeader.ContentType.getHttpName(), HttpContentType.json.getHeaderValueWithEncoding() );
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
                    resp.setHeader( HttpHeader.ContentType.getHttpName(), HttpContentType.plain.getHeaderValueWithEncoding() );
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
        response.setHeader( HttpHeader.ContentType.getHttpName(), HttpContentType.json.getHeaderValueWithEncoding() );
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
            catch ( final ChaiUnavailableException e )
            {
                throw PwmUnrecoverableException.fromChaiException( e );
            }
        }
    }

}
