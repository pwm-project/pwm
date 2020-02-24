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

package password.pwm.http.filter;

import org.apache.commons.validator.routines.InetAddressValidator;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.HttpHeader;
import password.pwm.http.IdleTimeoutCalculator;
import password.pwm.http.JspUrl;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmResponse;
import password.pwm.http.PwmSession;
import password.pwm.http.PwmSessionWrapper;
import password.pwm.http.PwmURL;
import password.pwm.svc.stats.EpsStatistic;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.IPMatcher;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.secure.PwmRandom;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RequestInitializationFilter implements Filter
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( RequestInitializationFilter.class );

    @Override
    public void init( final FilterConfig filterConfig )
            throws ServletException
    {
    }

    @Override
    public void destroy( )
    {
    }

    public void doFilter(
            final ServletRequest servletRequest,
            final ServletResponse servletResponse,
            final FilterChain filterChain ) throws IOException, ServletException
    {

        final HttpServletRequest req = ( HttpServletRequest ) servletRequest;
        final HttpServletResponse resp = ( HttpServletResponse ) servletResponse;
        final PwmApplicationMode mode = PwmApplicationMode.determineMode( req );
        final PwmURL pwmURL = new PwmURL( req );

        PwmApplication localPwmApplication = null;
        try
        {
            localPwmApplication = ContextManager.getPwmApplication( req );
        }
        catch ( final PwmException e )
        {
            LOGGER.trace( () -> "unable to load pwmApplication: " + e.getMessage() );
        }

        if ( localPwmApplication != null && mode == PwmApplicationMode.RUNNING )
        {
            if ( localPwmApplication.getStatisticsManager() != null )
            {
                localPwmApplication.getStatisticsManager().updateEps( EpsStatistic.REQUESTS, 1 );
            }
        }

        if ( localPwmApplication == null && pwmURL.isResourceURL() )
        {
            filterChain.doFilter( req, resp );
            return;
        }

        if ( pwmURL.isRestService() )
        {
            filterChain.doFilter( req, resp );
            return;
        }

        if ( mode == PwmApplicationMode.ERROR || localPwmApplication == null )
        {
            try
            {
                final ContextManager contextManager = ContextManager.getContextManager( req.getServletContext() );
                if ( contextManager != null )
                {
                    final ErrorInformation startupError = contextManager.getStartupErrorInformation();
                    servletRequest.setAttribute( PwmRequestAttribute.PwmErrorInfo.toString(), startupError );
                }
            }
            catch ( final Exception e )
            {
                if ( pwmURL.isResourceURL() )
                {
                    filterChain.doFilter( servletRequest, servletResponse );
                    return;
                }

                LOGGER.error( () -> "error while trying to detect application status: " + e.getMessage() );
            }

            LOGGER.error( () -> "unable to satisfy incoming request, application is not available" );
            resp.setStatus( 500 );
            final String url = JspUrl.APP_UNAVAILABLE.getPath();
            servletRequest.getServletContext().getRequestDispatcher( url ).forward( servletRequest, servletResponse );
            return;
        }

        try
        {
            localPwmApplication.getInprogressRequests().incrementAndGet();
            initializeServletRequest( req, resp, filterChain );
        }
        finally
        {
            localPwmApplication.getInprogressRequests().decrementAndGet();
        }
    }

    private void initializeServletRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final FilterChain filterChain
    )
            throws IOException, ServletException
    {
        try
        {
            checkAndInitSessionState( req );
            PwmRequest.forRequest( req, resp );
        }
        catch ( final Throwable e )
        {
            LOGGER.error( () -> "can't load application: " + e.getMessage(), e );
            if ( !( new PwmURL( req ).isResourceURL() ) )
            {
                respondWithUnavailableError( req, resp );
                return;
            }
            return;
        }

        try
        {
            final PwmRequest pwmRequest = PwmRequest.forRequest( req, resp );

            checkIfSessionRecycleNeeded( pwmRequest );

            handleRequestInitialization( pwmRequest );

            addPwmResponseHeaders( pwmRequest );

            checkIdleTimeout( pwmRequest );

            try
            {
                handleRequestSecurityChecks( pwmRequest );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( pwmRequest, e.getErrorInformation() );
                pwmRequest.respondWithError( e.getErrorInformation() );
                if ( PwmError.ERROR_INTRUDER_SESSION != e.getError() )
                {
                    pwmRequest.invalidateSession();
                }
                return;
            }

        }
        catch ( final Throwable e )
        {
            final String logMsg = "can't init request: " + e.getMessage();
            if ( e instanceof PwmException && ( ( PwmException ) e ).getError() != PwmError.ERROR_INTERNAL )
            {
                LOGGER.error( () -> logMsg );
            }
            else
            {
                LOGGER.error( () -> logMsg, e );
            }
            if ( !( new PwmURL( req ).isResourceURL() ) )
            {
                respondWithUnavailableError( req, resp );
                return;
            }
            return;
        }

        filterChain.doFilter( req, resp );
    }

    private void respondWithUnavailableError( final HttpServletRequest req, final HttpServletResponse resp )
            throws ServletException, IOException
    {
        ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_APP_UNAVAILABLE );
        try
        {
            final ContextManager contextManager = ContextManager.getContextManager( req.getServletContext() );
            if ( contextManager != null && contextManager.getStartupErrorInformation() != null )
            {
                errorInformation = contextManager.getStartupErrorInformation();
            }
        }
        catch ( final PwmUnrecoverableException e2 )
        {
            LOGGER.error( () -> "error reading session context from servlet container: " + e2.getMessage() );
        }

        req.setAttribute( PwmRequestAttribute.PwmErrorInfo.toString(), errorInformation );
        final String url = JspUrl.APP_UNAVAILABLE.getPath();
        req.getServletContext().getRequestDispatcher( url ).forward( req, resp );
    }

    private void checkAndInitSessionState( final HttpServletRequest request )
            throws PwmUnrecoverableException
    {
        final ContextManager contextManager = ContextManager.getContextManager( request.getSession() );
        final PwmApplication pwmApplication = contextManager.getPwmApplication();

        {
            // destroy any outdated sessions
            final HttpSession httpSession = request.getSession( false );
            if ( httpSession != null )
            {
                final String sessionPwmAppNonce = ( String ) httpSession.getAttribute( PwmConstants.SESSION_ATTR_PWM_APP_NONCE );
                if ( sessionPwmAppNonce == null || !sessionPwmAppNonce.equals( pwmApplication.getRuntimeNonce() ) )
                {
                    LOGGER.debug( () -> "invalidating http session created with non-current servlet context" );
                    httpSession.invalidate();
                }
            }
        }

        {
            // handle pwmSession init and assignment.
            final HttpSession httpSession = request.getSession();
            if ( httpSession.getAttribute( PwmConstants.SESSION_ATTR_PWM_SESSION ) == null )
            {
                final PwmSession pwmSession = PwmSession.createPwmSession( pwmApplication );
                PwmSessionWrapper.sessionMerge( pwmApplication, pwmSession, httpSession );
            }
        }

    }

    private void checkIfSessionRecycleNeeded( final PwmRequest pwmRequest )
    {
        if ( pwmRequest.getPwmSession().getSessionStateBean().isSessionIdRecycleNeeded() )
        {
            pwmRequest.getHttpServletRequest().changeSessionId();
            pwmRequest.getPwmSession().getSessionStateBean().setSessionIdRecycleNeeded( false );
        }
    }

    private static void addPwmResponseHeaders(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {

        if ( pwmRequest == null )
        {
            return;
        }
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Configuration config = pwmApplication.getConfig();
        final PwmResponse resp = pwmRequest.getPwmResponse();

        if ( resp.isCommitted() )
        {
            return;
        }

        final boolean includeXSessionID = Boolean.parseBoolean( config.readAppProperty( AppProperty.HTTP_HEADER_SEND_XSESSIONID ) );
        if ( includeXSessionID && pwmSession != null )
        {
            resp.setHeader( HttpHeader.XSessionID, pwmSession.getSessionStateBean().getSessionID() );
        }

        final boolean includeContentLanguage = Boolean.parseBoolean( config.readAppProperty( AppProperty.HTTP_HEADER_SEND_CONTENT_LANGUAGE ) );
        if ( includeContentLanguage )
        {
            resp.setHeader( HttpHeader.ContentLanguage, pwmRequest.getLocale().toLanguageTag() );
        }

        addStaticResponseHeaders( pwmApplication, resp.getHttpServletResponse() );


        if ( pwmSession != null )
        {
            final String contentPolicy;
            if ( pwmRequest.getURL().isConfigGuideURL() || pwmRequest.getURL().isConfigManagerURL() )
            {
                contentPolicy = config.readAppProperty( AppProperty.SECURITY_HTTP_CONFIG_CSP_HEADER );
            }
            else
            {
                contentPolicy = config.readSettingAsString( PwmSetting.SECURITY_CSP_HEADER );
            }

            if ( contentPolicy != null && !contentPolicy.isEmpty() )
            {
                final String nonce = pwmRequest.getCspNonce();
                final String expandedPolicy = contentPolicy.replace( "%NONCE%", nonce );
                resp.setHeader( HttpHeader.ContentSecurityPolicy, expandedPolicy );
            }
        }
    }

    public static void addStaticResponseHeaders(
            final PwmApplication pwmApplication,
            final HttpServletResponse resp
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();

        final String serverHeader = config.readAppProperty( AppProperty.HTTP_HEADER_SERVER );
        final boolean includeXInstance = Boolean.parseBoolean( config.readAppProperty( AppProperty.HTTP_HEADER_SEND_XINSTANCE ) );
        final boolean includeXVersion = Boolean.parseBoolean( config.readAppProperty( AppProperty.HTTP_HEADER_SEND_XVERSION ) );
        final boolean includeXContentTypeOptions = Boolean.parseBoolean( config.readAppProperty( AppProperty.HTTP_HEADER_SEND_XCONTENTTYPEOPTIONS ) );
        final boolean includeXXSSProtection = Boolean.parseBoolean( config.readAppProperty( AppProperty.HTTP_HEADER_SEND_XXSSPROTECTION ) );
        final boolean includeXFrameDeny = config.readSettingAsBoolean( PwmSetting.SECURITY_PREVENT_FRAMING );
        final boolean includeXAmb = Boolean.parseBoolean( config.readAppProperty( AppProperty.HTTP_HEADER_SEND_XAMB ) );

        {
            final String noiseHeader = makeNoiseHeader( pwmApplication, config );
            if ( noiseHeader != null )
            {
                resp.setHeader( HttpHeader.XNoise.getHttpName(), noiseHeader );
            }
        }

        if ( includeXVersion )
        {
            resp.setHeader( HttpHeader.XVersion.getHttpName(), PwmConstants.SERVLET_VERSION );
        }

        if ( includeXContentTypeOptions )
        {
            resp.setHeader( HttpHeader.XContentTypeOptions.getHttpName(), "nosniff" );
        }

        if ( includeXXSSProtection )
        {
            resp.setHeader( HttpHeader.XXSSProtection.getHttpName(), "1" );
        }

        if ( includeXInstance )
        {
            resp.setHeader( HttpHeader.XInstance.getHttpName(), String.valueOf( pwmApplication.getInstanceID() ) );
        }

        if ( serverHeader != null && !serverHeader.isEmpty() )
        {
            final String value = MacroMachine.forNonUserSpecific( pwmApplication, null ).expandMacros( serverHeader );
            resp.setHeader( HttpHeader.Server.getHttpName(), value );
        }

        if ( includeXFrameDeny )
        {
            resp.setHeader( HttpHeader.XFrameOptions.getHttpName(), "DENY" );
        }

        if ( includeXAmb )
        {
            resp.setHeader( HttpHeader.XAmb.getHttpName(), PwmConstants.X_AMB_HEADER.get(
                    pwmApplication.getSecureService().pwmRandom().nextInt( PwmConstants.X_AMB_HEADER.size() )
            ) );
        }

        resp.setHeader( HttpHeader.CacheControl.getHttpName(), "no-cache, no-store, must-revalidate, proxy-revalidate" );
    }


    public static String readUserHostname( final HttpServletRequest request, final Configuration config ) throws PwmUnrecoverableException
    {
        if ( config != null && !config.readSettingAsBoolean( PwmSetting.REVERSE_DNS_ENABLE ) )
        {
            return "";
        }

        final String userIPAddress = readUserNetworkAddress( request, config );
        try
        {
            return InetAddress.getByName( userIPAddress ).getCanonicalHostName();
        }
        catch ( final UnknownHostException e )
        {
            LOGGER.trace( () -> "unknown host while trying to compute hostname for src request: " + e.getMessage() );
        }
        return "";
    }

    /**
     * Returns the IP address of the user.  If there is an X-Forwarded-For header in the request, that address will
     * be used.  Otherwise, the source address of the request is used.
     *
     * @param request the http request object
     * @param config the application configuration
     * @return String containing the textual representation of the source IP address, or null if the request is invalid.
     */
    public static String readUserNetworkAddress(
            final HttpServletRequest request,
            final Configuration config
    )
    {
        final List<String> candidateAddresses = new ArrayList<>();

        final boolean useXForwardedFor = config != null && config.readSettingAsBoolean( PwmSetting.USE_X_FORWARDED_FOR_HEADER );
        if ( useXForwardedFor )
        {
            final String xForwardedForValue = request.getHeader( HttpHeader.XForwardedFor.getHttpName() );
            if ( !StringUtil.isEmpty( xForwardedForValue ) )
            {
                Collections.addAll( candidateAddresses, xForwardedForValue.split( "," ) );
            }
        }

        final String sourceIP = request.getRemoteAddr();
        if ( !StringUtil.isEmpty( sourceIP ) )
        {
            candidateAddresses.add( sourceIP );
        }

        for ( final String candidateAddress : candidateAddresses )
        {
            final String trimAddr = candidateAddress.trim();
            if ( InetAddressValidator.getInstance().isValid( trimAddr ) )
            {
                return trimAddr;
            }
            else
            {
                LOGGER.warn( () -> "discarding bogus source network address '" + trimAddr + "'" );
            }
        }

        return "";
    }

    private static void handleRequestInitialization(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final LocalSessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();
        final PwmURL pwmURL = pwmRequest.getURL();

        // mark if first request
        if ( ssBean.getSessionCreationTime() == null )
        {
            ssBean.setSessionCreationTime( Instant.now() );
            ssBean.setSessionLastAccessedTime( Instant.now() );
        }

        // mark session ip address
        if ( ssBean.getSrcAddress() == null )
        {
            ssBean.setSrcAddress( readUserNetworkAddress( pwmRequest.getHttpServletRequest(), pwmRequest.getConfig() ) );
        }

        // mark the user's hostname in the session bean
        if ( ssBean.getSrcHostname() == null )
        {
            ssBean.setSrcHostname( readUserHostname( pwmRequest.getHttpServletRequest(), pwmRequest.getConfig() ) );
        }

        // update the privateUrlAccessed flag
        if ( pwmURL.isPrivateUrl() )
        {
            ssBean.setPrivateUrlAccessed( true );
        }

        // initialize the session's locale
        if ( ssBean.getLocale() == null )
        {
            initializeLocaleAndTheme( pwmRequest );
        }

        // set idle timeout
        PwmSessionWrapper.setHttpSessionIdleTimeout( pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), pwmRequest.getHttpServletRequest().getSession() );
    }

    private static void initializeLocaleAndTheme(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final String localeCookieName = pwmRequest.getConfig().readAppProperty( AppProperty.HTTP_COOKIE_LOCALE_NAME );
        final String localeCookie = pwmRequest.readCookie( localeCookieName );
        if ( localeCookieName.length() > 0 && localeCookie != null )
        {
            LOGGER.debug( pwmRequest, () -> "detected locale cookie in request, setting locale to " + localeCookie );
            pwmRequest.getPwmSession().setLocale( pwmRequest, localeCookie );
        }
        else
        {
            final List<Locale> knownLocales = pwmRequest.getConfig().getKnownLocales();
            final Locale userLocale = LocaleHelper.localeResolver( pwmRequest.getHttpServletRequest().getLocale(), knownLocales );
            pwmRequest.getPwmSession().getSessionStateBean().setLocale( userLocale == null ? PwmConstants.DEFAULT_LOCALE : userLocale );
            LOGGER.trace( pwmRequest, () -> "user locale set to '" + pwmRequest.getLocale() + "'" );
        }

        final String themeCookieName = pwmRequest.getConfig().readAppProperty( AppProperty.HTTP_COOKIE_THEME_NAME );
        final String themeCookie = pwmRequest.readCookie( themeCookieName );
        if ( localeCookieName.length() > 0 && themeCookie != null && themeCookie.length() > 0 )
        {
            if ( pwmRequest.getPwmApplication().getResourceServletService().checkIfThemeExists( pwmRequest, themeCookie ) )
            {
                LOGGER.debug( pwmRequest, () -> "detected theme cookie in request, setting theme to " + themeCookie );
                pwmRequest.getPwmSession().getSessionStateBean().setTheme( themeCookie );
            }
        }
    }

    private static void handleRequestSecurityChecks( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        // check the user's IP address
        checkIfSourceAddressChanged( pwmRequest );

        // check total time.
        checkTotalSessionTime( pwmRequest );

        // check headers
        checkRequiredHeaders( pwmRequest );

        // check permitted source IP address
        checkSourceNetworkAddress( pwmRequest );

        // csrf cross-site request forgery checks
        checkCsrfHeader( pwmRequest );

        // check trial
        checkTrial( pwmRequest );

        // check intruder
        pwmRequest.getPwmApplication().getIntruderManager().convenience().checkAddressAndSession( pwmRequest.getPwmSession() );
    }

    private static void checkIfSourceAddressChanged( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        if ( !pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.MULTI_IP_SESSION_ALLOWED ) )
        {
            final String remoteAddress = readUserNetworkAddress( pwmRequest.getHttpServletRequest(), pwmRequest.getConfig() );
            final LocalSessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();

            if ( !ssBean.getSrcAddress().equals( remoteAddress ) )
            {
                final String errorMsg = "current network address '" + remoteAddress + "' has changed from original network address '" + ssBean.getSrcAddress() + "'";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SECURITY_VIOLATION, errorMsg );
                throw new PwmUnrecoverableException( errorInformation );
            }
        }
    }

    private static void checkTotalSessionTime( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final LocalSessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();

        if ( ssBean.getSessionCreationTime() != null )
        {
            final long maxSessionSeconds = pwmRequest.getConfig().readSettingAsLong( PwmSetting.SESSION_MAX_SECONDS );
            final TimeDuration sessionAge = TimeDuration.fromCurrent( ssBean.getSessionCreationTime() );
            final int sessionSecondAge = (int) sessionAge.as( TimeDuration.Unit.SECONDS );
            if ( sessionSecondAge > maxSessionSeconds )
            {
                final String errorMsg = "session age (" + sessionAge.asCompactString() + ") is longer than maximum permitted age";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SECURITY_VIOLATION, errorMsg );
                throw new PwmUnrecoverableException( errorInformation );
            }
        }
    }

    private static void checkRequiredHeaders( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final List<String> requiredHeaders = pwmRequest.getConfig().readSettingAsStringArray( PwmSetting.REQUIRED_HEADERS );
        if ( requiredHeaders != null && !requiredHeaders.isEmpty() )
        {
            final Map<String, String> configuredValues = StringUtil.convertStringListToNameValuePair( requiredHeaders, "=" );

            for ( final Map.Entry<String, String> entry : configuredValues.entrySet() )
            {
                final String key = entry.getKey();
                if ( key != null && key.length() > 0 )
                {
                    final String requiredValue = entry.getValue();
                    if ( requiredValue != null && requiredValue.length() > 0 )
                    {
                        final String value = pwmRequest.readHeaderValueAsString( key );
                        if ( value == null || value.length() < 1 )
                        {
                            final String errorMsg = "request is missing required value for header '" + key + "'";
                            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SECURITY_VIOLATION, errorMsg );
                            throw new PwmUnrecoverableException( errorInformation );
                        }
                        else
                        {
                            if ( !requiredValue.equals( value ) )
                            {
                                final String errorMsg = "request has incorrect required value for header '" + key + "'";
                                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SECURITY_VIOLATION, errorMsg );
                                throw new PwmUnrecoverableException( errorInformation );
                            }
                        }
                    }
                }
            }
        }
    }

    private static void checkSourceNetworkAddress( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final List<String> requiredHeaders = pwmRequest.getConfig().readSettingAsStringArray( PwmSetting.IP_PERMITTED_RANGE );
        if ( requiredHeaders != null && !requiredHeaders.isEmpty() )
        {
            boolean match = false;
            final String requestAddress = pwmRequest.getHttpServletRequest().getRemoteAddr();
            for ( int i = 0; i < requiredHeaders.size() && !match; i++ )
            {
                final String ipMatchString = requiredHeaders.get( i );
                try
                {
                    final IPMatcher ipMatcher = new IPMatcher( ipMatchString );
                    try
                    {
                        if ( ipMatcher.match( requestAddress ) )
                        {
                            match = true;
                        }
                    }
                    catch ( final IPMatcher.IPMatcherException e )
                    {
                        LOGGER.error( () -> "error while attempting to match permitted address range '" + ipMatchString + "', error: " + e );
                    }
                }
                catch ( final IPMatcher.IPMatcherException e )
                {
                    LOGGER.error( () -> "error parsing permitted address range '" + ipMatchString + "', error: " + e );
                }
            }
            if ( !match )
            {
                final String errorMsg = "request network address '" + requestAddress + "' does not match any configured permitted source address";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SECURITY_VIOLATION, errorMsg );
                throw new PwmUnrecoverableException( errorInformation );
            }
        }
    }


    private static void checkCsrfHeader( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final boolean performCsrfHeaderChecks = Boolean.parseBoolean( pwmRequest.getConfig().readAppProperty( AppProperty.SECURITY_HTTP_PERFORM_CSRF_HEADER_CHECKS ) );
        if (
                performCsrfHeaderChecks
                        && !pwmRequest.getMethod().isIdempotent()
                        && !pwmRequest.getURL().isRestService()
        )
        {
            final String originValue = pwmRequest.readHeaderValueAsString( HttpHeader.Origin );
            final String referrerValue = pwmRequest.readHeaderValueAsString( HttpHeader.Referer );
            final String siteUrl = pwmRequest.getPwmApplication().getConfig().readSettingAsString( PwmSetting.PWM_SITE_URL );

            final String targetValue = pwmRequest.getHttpServletRequest().getRequestURL().toString();
            if ( StringUtil.isEmpty( targetValue ) )
            {
                final String msg = "malformed request instance, missing target uri value";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SECURITY_VIOLATION, msg );
                LOGGER.debug( pwmRequest, () -> errorInformation.toDebugStr() + " [" + makeHeaderDebugStr( pwmRequest ) + "]" );
                throw new PwmUnrecoverableException( errorInformation );
            }

            final boolean originHeaderEvaluated;
            if ( !StringUtil.isEmpty( originValue ) )
            {
                if ( !PwmURL.compareUriBase( originValue, targetValue ) )
                {
                    final String msg = "cross-origin request not permitted: origin header does not match incoming target url"
                            + " [" + makeHeaderDebugStr( pwmRequest ) + "]";
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SECURITY_VIOLATION, msg );
                    LOGGER.debug( pwmRequest, errorInformation );
                    throw new PwmUnrecoverableException( errorInformation );
                }
                originHeaderEvaluated = true;
            }
            else
            {
                originHeaderEvaluated = false;
            }

            final boolean referrerHeaderEvaluated;
            if ( !StringUtil.isEmpty( referrerValue ) )
            {
                if ( !PwmURL.compareUriBase( referrerValue, targetValue ) && !PwmURL.compareUriBase( referrerValue, siteUrl ) )
                {
                    final String msg = "cross-origin request not permitted: referrer header does not match incoming target url"
                            + " [" + makeHeaderDebugStr( pwmRequest ) + "]";
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SECURITY_VIOLATION, msg );
                    LOGGER.debug( pwmRequest, errorInformation );
                    throw new PwmUnrecoverableException( errorInformation );
                }
                referrerHeaderEvaluated = true;
            }
            else
            {
                referrerHeaderEvaluated = false;
            }

            if ( !referrerHeaderEvaluated && !originHeaderEvaluated && !PwmURL.compareUriBase( originValue, siteUrl ) )
            {
                final String msg = "neither referer nor origin header request are present on non-idempotent request";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SECURITY_VIOLATION, msg );
                LOGGER.debug( pwmRequest, () -> errorInformation.toDebugStr() + " [" + makeHeaderDebugStr( pwmRequest ) + "]" );
                throw new PwmUnrecoverableException( errorInformation );
            }
        }
    }

    private static void checkTrial ( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        if ( PwmConstants.TRIAL_MODE )
        {
            final StatisticsManager statisticsManager = pwmRequest.getPwmApplication().getStatisticsManager();
            final String currentAuthString = statisticsManager.getStatBundleForKey( StatisticsManager.KEY_CURRENT ).getStatistic( Statistic.AUTHENTICATIONS );
            if ( new BigInteger( currentAuthString ).compareTo( BigInteger.valueOf( PwmConstants.TRIAL_MAX_AUTHENTICATIONS ) ) > 0 )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_TRIAL_VIOLATION, "maximum usage per server startup exceeded" ) );
            }

            final String totalAuthString = statisticsManager.getStatBundleForKey( StatisticsManager.KEY_CUMULATIVE ).getStatistic( Statistic.AUTHENTICATIONS );
            if ( new BigInteger( totalAuthString ).compareTo( BigInteger.valueOf( PwmConstants.TRIAL_MAX_TOTAL_AUTH ) ) > 0 )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_TRIAL_VIOLATION, "maximum usage for this server has been exceeded" ) );
            }
        }
    }

    private void checkIdleTimeout( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        final TimeDuration maxDurationForRequest = IdleTimeoutCalculator.idleTimeoutForRequest( pwmRequest );
        final TimeDuration currentDuration = TimeDuration.fromCurrent( pwmRequest.getHttpServletRequest().getSession().getLastAccessedTime() );
        if ( currentDuration.isLongerThan( maxDurationForRequest ) )
        {
            LOGGER.debug( () -> "unauthenticated session due to idle time, max for request is " + maxDurationForRequest.asCompactString()
                    + ", session idle time is " + currentDuration.asCompactString() );
            pwmRequest.getPwmSession().unauthenticateUser( pwmRequest );
        }
    }

    private static String makeHeaderDebugStr( final PwmRequest pwmRequest )
    {
        final Map<String, String> values = new LinkedHashMap<>();
        for ( final HttpHeader header : new HttpHeader[]
                {
                        HttpHeader.Referer,
                        HttpHeader.Origin,
                }
        )
        {
            values.put( header.getHttpName(), pwmRequest.readHeaderValueAsString( header ) );
        }
        values.put( "target", pwmRequest.getHttpServletRequest().getRequestURL().toString() );
        values.put( "siteUrl", pwmRequest.getPwmApplication().getConfig().readSettingAsString( PwmSetting.PWM_SITE_URL ) );
        return StringUtil.mapToString( values );
    }

    private static String makeNoiseHeader( final PwmApplication pwmApplication, final Configuration configuration )
    {
        final boolean sendNoise = Boolean.parseBoolean( configuration.readAppProperty( AppProperty.HTTP_HEADER_SEND_XNOISE ) );

        if ( sendNoise )
        {
            final int noiseLength = Integer.parseInt( configuration.readAppProperty( AppProperty.HTTP_HEADER_NOISE_LENGTH ) );
            final PwmRandom pwmRandom = pwmApplication.getSecureService().pwmRandom();
            return pwmRandom.alphaNumericString( pwmRandom.nextInt( noiseLength ) + 11 );
        }

        return null;
    }

}
