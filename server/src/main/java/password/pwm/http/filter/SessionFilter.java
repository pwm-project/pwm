/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

import password.pwm.AppProperty;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.SessionVerificationMode;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpHeader;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmCookiePath;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmResponse;
import password.pwm.http.PwmSession;
import password.pwm.http.PwmURL;
import password.pwm.svc.stats.AvgStatistic;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;

/**
 * <p>This session filter (invoked by the container through the web.xml descriptor) wraps all calls to the
 * servlets in the container.</p>
 *
 * <p>It is responsible for managing some aspects of the user session and also for enforcing security
 * functionality such as intruder lockout.</p>
 *
 * @author Jason D. Rivard
 */
public class SessionFilter extends AbstractPwmFilter
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SessionFilter.class );

    private static final List<CheckingFunction> CHECKING_FUNCTIONS = List.of(
            new SessionVerificationChecker(),
            new LocaleParamChecker(),
            new ThemeParamChecker(),
            new SsoOverrideParamChecker(),
            new ForwardParamChecker(),
            new LogoutParamChecker(),
            new PasswordExpiredParamChecker(),
            new PageLeaveNoticeChecker() );


    private interface CheckingFunction
    {
        ProcessStatus processCheck( PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException;
    }

    @Override
    boolean isInterested( final PwmApplicationMode mode, final PwmURL pwmURL )
    {
        return !pwmURL.isRestService();
    }

    @Override
    public void processFilter(
            final PwmApplicationMode mode,
            final PwmRequest pwmRequest,
            final PwmFilterChain chain
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        // output request information to debug log
        final Instant startTime = Instant.now();
        final PwmURL pwmURL = pwmRequest.getURL();

        if ( !pwmURL.isResourceURL() )
        {
            pwmRequest.debugHttpRequestToLog( "received", null );
        }

        if ( !pwmURL.isRestService() && !pwmURL.isResourceURL() )
        {
            if ( handleStandardRequestOperations( pwmRequest ) == ProcessStatus.Halt )
            {
                return;
            }
        }

        try
        {
            chain.doFilter();
        }
        catch ( final IOException e )
        {
            LOGGER.trace( pwmRequest, () -> "IO exception during servlet processing: " + e.getMessage() );
            throw new ServletException( e );
        }
        catch ( final Throwable e )
        {
            LOGGER.error( pwmRequest, () -> "unhandled exception " + e.getMessage(), e );
            throw new ServletException( e );
        }

        final TimeDuration requestExecuteTime = TimeDuration.fromCurrent( startTime );
        pwmRequest.debugHttpRequestToLog( "completed", () -> requestExecuteTime );
        pwmRequest.getPwmDomain().getStatisticsManager().updateAverageValue( AvgStatistic.AVG_REQUEST_PROCESS_TIME, requestExecuteTime.asMillis() );
        pwmRequest.getPwmSession().getSessionStateBean().getRequestCount().incrementAndGet();
        pwmRequest.getPwmSession().getSessionStateBean().getAvgRequestDuration().update( requestExecuteTime );
    }

    private ProcessStatus handleStandardRequestOperations(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final LocalSessionStateBean ssBean = pwmSession.getSessionStateBean();

        // debug the http session headers
        if ( !pwmSession.getSessionStateBean().isDebugInitialized() )
        {
            LOGGER.trace( pwmRequest, pwmRequest::debugHttpHeaders );
            pwmSession.getSessionStateBean().setDebugInitialized( true );
        }

        try
        {
            pwmDomain.getSessionStateService().readLoginSessionState( pwmRequest );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.warn( pwmRequest, () -> "error while reading login session state: " + e.getMessage() );
        }

        // mark last url
        if ( !PwmURL.create( pwmRequest.getHttpServletRequest() ).isCommandServletURL() )
        {
            ssBean.setLastRequestURL( pwmRequest.getHttpServletRequest().getRequestURI() );
        }

        // mark last request time.
        ssBean.setSessionLastAccessedTime( Instant.now() );

        for ( final CheckingFunction checkingFunction : CHECKING_FUNCTIONS )
        {
            final ProcessStatus status = checkingFunction.processCheck( pwmRequest );
            if ( status == ProcessStatus.Halt )
            {
                return ProcessStatus.Halt;
            }
        }

        // update last request time.
        ssBean.setSessionLastAccessedTime( Instant.now() );

        StatisticsClient.incrementStat( pwmDomain.getPwmApplication(), Statistic.HTTP_REQUESTS );

        return ProcessStatus.Continue;
    }


    //check for session verification failure
    private static class SessionVerificationChecker implements CheckingFunction
    {
        @Override
        public ProcessStatus processCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
        {
            final LocalSessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();
            if ( !ssBean.isSessionVerified() )
            {
                // ignore resource requests
                final SessionVerificationMode mode = pwmRequest.getAppConfig().readSettingAsEnum( PwmSetting.ENABLE_SESSION_VERIFICATION,
                        SessionVerificationMode.class );
                if ( mode == SessionVerificationMode.OFF )
                {
                    ssBean.setSessionVerified( true );
                }
                else
                {
                    if ( verifySession( pwmRequest, mode ) == ProcessStatus.Halt )
                    {
                        return ProcessStatus.Halt;
                    }
                }
            }
            return ProcessStatus.Continue;
        }

        /**
         * Attempt to determine if user agent is able to track sessions (either via url rewriting or cookies).
         */
        private static ProcessStatus verifySession(
                final PwmRequest pwmRequest,
                final SessionVerificationMode mode
        )
                throws IOException, ServletException, PwmUnrecoverableException
        {
            final HttpServletRequest req = pwmRequest.getHttpServletRequest();
            final PwmResponse pwmResponse = pwmRequest.getPwmResponse();

            if ( !pwmRequest.getMethod().isIdempotent() && pwmRequest.hasParameter( PwmConstants.PARAM_FORM_ID ) )
            {
                LOGGER.debug( pwmRequest, () -> "session is unvalidated but can not be validated during a " + pwmRequest.getMethod().toString() + " request, will allow" );
                return ProcessStatus.Continue;
            }

            {
                final String acceptEncodingHeader = pwmRequest.getHttpServletRequest().getHeader( HttpHeader.Accept.getHttpName() );
                if ( acceptEncodingHeader != null && acceptEncodingHeader.contains( "json" ) )
                {
                    LOGGER.debug( pwmRequest, () -> "session is unvalidated but can not be validated during a json request, will allow" );
                    return ProcessStatus.Continue;
                }
            }

            if ( pwmRequest.getURL().isCommandServletURL() )
            {
                LOGGER.debug( pwmRequest, () -> "session is unvalidated but can not be validated during a command servlet request, will allow" );
                return ProcessStatus.Continue;
            }

            if ( pwmRequest.getURL().isResourceURL() )
            {
                LOGGER.debug( pwmRequest, () -> "session is unvalidated but can not be validated during a resource request, will allow" );
                return ProcessStatus.Continue;
            }

            final LocalSessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();
            final String verificationParamName = pwmRequest.getDomainConfig().readAppProperty( AppProperty.HTTP_PARAM_SESSION_VERIFICATION );
            final String keyFromRequest = pwmRequest.readParameterAsString( verificationParamName, PwmHttpRequestWrapper.Flag.BypassValidation );

            // request doesn't have key, so make a new one, store it in the session, and redirect back here with the new key.
            if ( StringUtil.isEmpty( keyFromRequest ) )
            {
                if ( StringUtil.isEmpty( ssBean.getSessionVerificationKey() ) )
                {
                    ssBean.setSessionVerificationKey( pwmRequest.getPwmDomain().getSecureService().pwmRandom().randomUUID().toString() );
                }

                final String returnURL = figureValidationURL( pwmRequest, ssBean.getSessionVerificationKey() );

                LOGGER.trace( pwmRequest, () -> "session has not been validated, redirecting with verification key to " + returnURL );

                {
                    final String httpVersion = pwmRequest.getHttpServletRequest().getProtocol();
                    if ( "HTTP/1.0".equals( httpVersion ) || "HTTP/1.1".equals( httpVersion ) )
                    {
                        // better chance of detecting un-sticky sessions this way (closing connection not available in HTTP/2)
                        pwmResponse.setHeader( HttpHeader.Connection, "close" );
                    }
                }

                if ( mode == SessionVerificationMode.VERIFY_AND_CACHE )
                {
                    req.setAttribute( "Location", returnURL );
                    pwmResponse.forwardToJsp( JspUrl.INIT );
                }
                else
                {
                    pwmResponse.sendRedirect( returnURL );
                }
                return ProcessStatus.Halt;
            }

            // else, request has a key, so investigate.
            if ( keyFromRequest.equals( ssBean.getSessionVerificationKey() ) )
            {
                final String returnURL = figureValidationURL( pwmRequest, null );

                // session looks, good, mark it as such and return;
                LOGGER.trace( pwmRequest, () -> "session validated, redirecting to original request url: " + returnURL );
                ssBean.setSessionVerified( true );
                pwmRequest.getPwmResponse().sendRedirect( returnURL );
                return ProcessStatus.Halt;
            }

            // user's session is messed up.  send to error page.
            final String errorMsg = "client unable to reply with session key";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_BAD_SESSION, errorMsg );
            LOGGER.error( pwmRequest, errorInformation );
            pwmRequest.respondWithError( errorInformation, true );
            return ProcessStatus.Halt;
        }

        private static String figureValidationURL( final PwmRequest pwmRequest, final String validationKey )
        {
            final HttpServletRequest req = pwmRequest.getHttpServletRequest();
            final String queryString = req.getQueryString();
            final String verificationParamName = pwmRequest.getDomainConfig().readAppProperty( AppProperty.HTTP_PARAM_SESSION_VERIFICATION );

            String redirectURL = req.getRequestURI();

            if ( StringUtil.notEmpty( queryString ) )
            {
                for ( final String paramName : pwmRequest.parameterNames() )
                {
                    // check to make sure param is in query string
                    if ( queryString.contains( StringUtil.urlDecode( paramName ) ) )
                    {
                        if ( !verificationParamName.equals( paramName ) )
                        {
                            // raw read of param value to avoid sanitization checks
                            for ( final String value : req.getParameterValues( paramName ) )
                            {
                                redirectURL = PwmURL.appendAndEncodeUrlParameters( redirectURL, paramName, value );
                            }
                        }
                    }
                    else
                    {
                        LOGGER.debug( () -> "dropping non-query string (body?) parameter '" + paramName + "' during redirect validation)" );
                    }
                }
            }

            if ( validationKey != null )
            {
                redirectURL = PwmURL.appendAndEncodeUrlParameters( redirectURL, verificationParamName, validationKey );
            }

            return redirectURL;
        }
    }

    //override session locale due to parameter
    private static class LocaleParamChecker implements CheckingFunction
    {
        @Override
        public ProcessStatus processCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
        {
            final DomainConfig config = pwmRequest.getDomainConfig();
            final String localeParamName = config.readAppProperty( AppProperty.HTTP_PARAM_NAME_LOCALE );
            final String localeCookieName = config.readAppProperty( AppProperty.HTTP_COOKIE_LOCALE_NAME );
            final String requestedLocale = pwmRequest.readParameterAsString( localeParamName );
            final int cookieAgeSeconds = ( int ) pwmRequest.getAppConfig().readSettingAsLong( PwmSetting.LOCALE_COOKIE_MAX_AGE );
            if ( requestedLocale != null && requestedLocale.length() > 0 )
            {
                LOGGER.debug( pwmRequest, () -> "detected locale request parameter " + localeParamName + " with value " + requestedLocale );
                if ( pwmRequest.getPwmSession().setLocale( pwmRequest, requestedLocale ) )
                {
                    if ( cookieAgeSeconds > 0 )
                    {
                        pwmRequest.getPwmResponse().writeCookie(
                                localeCookieName,
                                requestedLocale,
                                cookieAgeSeconds,
                                PwmCookiePath.Domain
                        );
                    }
                }
            }
            return ProcessStatus.Continue;
        }
    }

    //set the session's theme
    private static class ThemeParamChecker implements CheckingFunction
    {
        @Override
        public ProcessStatus processCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
        {
            final DomainConfig config = pwmRequest.getDomainConfig();
            final String themeParameterName = config.readAppProperty( AppProperty.HTTP_PARAM_NAME_THEME );
            final String themeReqParameter = pwmRequest.readParameterAsString( themeParameterName );

            if ( themeReqParameter != null && !themeReqParameter.isEmpty() )
            {
                if ( pwmRequest.getPwmDomain().getResourceServletService().checkIfThemeExists( pwmRequest, themeReqParameter ) )
                {
                    pwmRequest.getPwmSession().getSessionStateBean().setTheme( themeReqParameter );
                    final String themeCookieName = config.readAppProperty( AppProperty.HTTP_COOKIE_THEME_NAME );
                    if ( themeCookieName != null && themeCookieName.length() > 0 )
                    {
                        final String configuredTheme = config.readSettingAsString( PwmSetting.INTERFACE_THEME );

                        if ( configuredTheme != null && configuredTheme.equalsIgnoreCase( themeReqParameter ) )
                        {
                            pwmRequest.getPwmResponse().removeCookie( themeCookieName, PwmCookiePath.Domain );
                        }
                        else
                        {
                            final int maxAge = Integer.parseInt( config.readAppProperty( AppProperty.HTTP_COOKIE_THEME_AGE ) );
                            pwmRequest.getPwmResponse().writeCookie( themeCookieName, themeReqParameter, maxAge, PwmCookiePath.Domain );
                        }
                    }
                }
            }
            return ProcessStatus.Continue;
        }
    }

    //check the sso override flag
    private static class SsoOverrideParamChecker implements CheckingFunction
    {
        @Override
        public ProcessStatus processCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
        {
            final String ssoOverrideParameterName = pwmRequest.getDomainConfig().readAppProperty( AppProperty.HTTP_PARAM_NAME_SSO_OVERRIDE );
            if ( pwmRequest.hasParameter( ssoOverrideParameterName ) )
            {
                final String ssoParamValue = pwmRequest.readParameterAsString( ssoOverrideParameterName );
                if ( pwmRequest.readParameterAsBoolean( ssoOverrideParameterName ) )
                {
                    LOGGER.trace( pwmRequest, () -> "enabling sso authentication due to parameter "
                            + ssoOverrideParameterName + "=" + ssoParamValue );
                    pwmRequest.getPwmSession().getLoginInfoBean().removeFlag( LoginInfoBean.LoginFlag.noSso );
                }
                else
                {
                    LOGGER.trace( pwmRequest, () -> "disabling sso authentication due to parameter "
                            + ssoOverrideParameterName + "=" + ssoParamValue );
                    pwmRequest.getPwmSession().getLoginInfoBean().setFlag( LoginInfoBean.LoginFlag.noSso );
                }
            }
            return ProcessStatus.Continue;
        }
    }

    private static class ForwardParamChecker implements CheckingFunction
    {
        @Override
        public ProcessStatus processCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
        {
            final String forwardURLParamName = pwmRequest.getAppConfig().readAppProperty( AppProperty.HTTP_PARAM_NAME_FORWARD_URL );
            final String forwardURL = pwmRequest.readParameterAsString( forwardURLParamName );
            if ( forwardURL != null && forwardURL.length() > 0 )
            {
                try
                {
                    checkUrlAgainstWhitelist( pwmRequest.getPwmDomain(), pwmRequest.getLabel(), forwardURL );
                }
                catch ( final PwmOperationalException e )
                {
                    LOGGER.error( pwmRequest, e.getErrorInformation() );
                    pwmRequest.respondWithError( e.getErrorInformation() );
                    return ProcessStatus.Halt;
                }
                pwmRequest.getPwmSession().getSessionStateBean().setForwardURL( forwardURL );
                LOGGER.debug( pwmRequest, () -> "forwardURL parameter detected in request, setting session forward url to " + forwardURL );
            }
            return ProcessStatus.Continue;
        }
    }

    private static class LogoutParamChecker implements CheckingFunction
    {
        @Override
        public ProcessStatus processCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
        {
            final String logoutURLParamName = pwmRequest.getAppConfig().readAppProperty( AppProperty.HTTP_PARAM_NAME_LOGOUT_URL );
            final String logoutURL = pwmRequest.readParameterAsString( logoutURLParamName );
            if ( StringUtil.notEmpty( logoutURL ) )
            {
                try
                {
                    checkUrlAgainstWhitelist( pwmRequest.getPwmDomain(), pwmRequest.getLabel(), logoutURL );
                }
                catch ( final PwmOperationalException e )
                {
                    LOGGER.error( pwmRequest, e.getErrorInformation() );
                    pwmRequest.respondWithError( e.getErrorInformation() );
                    return ProcessStatus.Halt;
                }
                pwmRequest.getPwmSession().getSessionStateBean().setLogoutURL( logoutURL );
                LOGGER.debug( pwmRequest, () -> "logoutURL parameter detected in request, setting session logout url to " + logoutURL );
            }
            return ProcessStatus.Continue;
        }
    }

    private static class PasswordExpiredParamChecker implements CheckingFunction
    {
        @Override
        public ProcessStatus processCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
        {
            final String expireParamName = pwmRequest.getDomainConfig().readAppProperty( AppProperty.HTTP_PARAM_NAME_PASSWORD_EXPIRED );
            if ( "true".equalsIgnoreCase( pwmRequest.readParameterAsString( expireParamName ) ) )
            {
                LOGGER.debug( pwmRequest, () -> "detected param '" + expireParamName + "'=true in request, will force pw change" );
                pwmRequest.getPwmSession().getLoginInfoBean().getLoginFlags().add( LoginInfoBean.LoginFlag.forcePwChange );
            }
            return ProcessStatus.Continue;
        }
    }

    private static class PageLeaveNoticeChecker implements CheckingFunction
    {
        @Override
        public ProcessStatus processCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException
        {
            final PwmSession pwmSession = pwmRequest.getPwmSession();
            final long configuredSeconds = pwmRequest.getAppConfig().readSettingAsLong( PwmSetting.SECURITY_PAGE_LEAVE_NOTICE_TIMEOUT );
            if ( configuredSeconds <= 0 )
            {
                return ProcessStatus.Continue;
            }

            final Instant currentPageLeaveNotice = pwmSession.getSessionStateBean().getPageLeaveNoticeTime();
            pwmSession.getSessionStateBean().setPageLeaveNoticeTime( null );
            if ( currentPageLeaveNotice == null )
            {
                return ProcessStatus.Continue;
            }

            if ( TimeDuration.fromCurrent( currentPageLeaveNotice ).as( TimeDuration.Unit.SECONDS ) <= configuredSeconds )
            {
                return ProcessStatus.Continue;
            }

            LOGGER.warn( () -> "invalidating session due to dirty page leave time greater then configured timeout" );
            pwmRequest.invalidateSession();
            pwmRequest.getPwmResponse().sendRedirect( pwmRequest.getHttpServletRequest().getRequestURI() );
            return ProcessStatus.Halt;
        }
    }

    @Override
    public void destroy( )
    {
    }

    private static void checkUrlAgainstWhitelist(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final String inputURL
    )
            throws PwmOperationalException
    {
        LOGGER.trace( sessionLabel, () -> "beginning test of requested redirect URL: " + inputURL );
        if ( inputURL == null || inputURL.isEmpty() )
        {
            return;
        }

        final URI inputURI;
        try
        {
            inputURI = URI.create( inputURL );
        }
        catch ( final IllegalArgumentException e )
        {
            LOGGER.error( sessionLabel, () -> "unable to parse requested redirect url '" + inputURL + "', error: " + e.getMessage() );
            // dont put input uri in error response
            final String errorMsg = "unable to parse url: " + e.getMessage();
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_REDIRECT_ILLEGAL, errorMsg ) );
        }

        {
            // check to make sure we werent handed a non-http uri.
            final String scheme = inputURI.getScheme();
            if ( scheme != null && !scheme.isEmpty() && !"http".equalsIgnoreCase( scheme ) && !"https".equals( scheme ) )
            {
                final String errorMsg = "unsupported url scheme";
                throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_REDIRECT_ILLEGAL, errorMsg ) );
            }
        }

        if ( inputURI.getHost() != null && !inputURI.getHost().isEmpty() )
        {
            // disallow localhost uri
            try
            {
                final InetAddress inetAddress = InetAddress.getByName( inputURI.getHost() );
                if ( inetAddress.isLoopbackAddress() )
                {
                    final String errorMsg = "redirect to loopback host is not permitted";
                    throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_REDIRECT_ILLEGAL, errorMsg ) );
                }
            }
            catch ( final UnknownHostException e )
            {
                /* noop */
            }
        }

        final StringBuilder sb = new StringBuilder();
        if ( inputURI.getScheme() != null )
        {
            sb.append( inputURI.getScheme() );
            sb.append( "://" );
        }
        if ( inputURI.getHost() != null )
        {
            sb.append( inputURI.getHost() );
        }
        if ( inputURI.getPort() != -1 )
        {
            sb.append( ":" );
            sb.append( inputURI.getPort() );
        }
        if ( inputURI.getPath() != null )
        {
            sb.append( inputURI.getPath() );
        }

        final String testURI = sb.toString();
        LOGGER.trace( sessionLabel, () -> "preparing to whitelist test parsed and decoded URL: " + testURI );

        final List<String> whiteList = pwmDomain.getConfig().readSettingAsStringArray( PwmSetting.SECURITY_REDIRECT_WHITELIST );

        if ( PwmURL.testIfUrlMatchesAllowedPattern( testURI, whiteList, sessionLabel ) )
        {
            return;
        }

        final String errorMsg = testURI + " is not a match for any configured redirect whitelist, see setting: "
                + PwmSetting.SECURITY_REDIRECT_WHITELIST.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE );
        throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_REDIRECT_ILLEGAL, errorMsg ) );
    }
}
