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

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.HttpHeader;
import password.pwm.http.IdleTimeoutCalculator;
import password.pwm.http.JspUrl;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmRequestUtil;
import password.pwm.http.PwmResponse;
import password.pwm.http.PwmSession;
import password.pwm.http.PwmSessionFactory;
import password.pwm.http.PwmURL;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.svc.intruder.IntruderServiceClient;
import password.pwm.svc.stats.EpsStatistic;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsService;
import password.pwm.util.java.MutableReference;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogManager;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
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
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
        final PwmApplicationMode mode = PwmApplicationMode.determineMode( req );

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

        final PwmURL pwmURL = PwmURL.create( req, localPwmApplication.getConfig() );

        if ( pwmURL.isRestService() )
        {
            filterChain.doFilter( req, resp );
            return;
        }

        try
        {
            // for servlet requests make sure the session is initialized
            req.getSession( true );
        }
        catch ( final Exception e )
        {
            LOGGER.trace( () -> "error reading session for servlet request: " + e.getMessage() );
        }

        if ( pwmURL.isResourceURL() )
        {
            filterChain.doFilter( req, resp );
            return;
        }

        if ( mode == PwmApplicationMode.ERROR )
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

            LOGGER.error( () -> "request does not indicate domain value" );
            resp.setStatus( 500 );
            final String url = JspUrl.APP_UNAVAILABLE.getPath();
            servletRequest.getServletContext().getRequestDispatcher( url ).forward( servletRequest, servletResponse );
            return;
        }

        initializeServletRequest( req, resp, filterChain );
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
            try
            {
                if ( !( PwmURL.create( req ).isResourceURL() ) )
                {
                    respondWithUnavailableError( req, resp );
                    return;
                }
            }
            catch ( final PwmUnrecoverableException pwmUnrecoverableException )
            {
                LOGGER.debug( () -> "error initializing http request for " + PwmConstants.PWM_APP_NAME + ": " + e.getMessage() );
            }
            return;
        }

        final PwmRequest pwmRequest;
        try
        {
            pwmRequest = PwmRequest.forRequest( req, resp );
            doTheThing( pwmRequest );
        }
        catch ( final Throwable t )
        {
            processInitThrowable( t, req, resp );
            return;
        }

        final MutableReference<Throwable> throwableReference = new MutableReference<>();

        PwmLogManager.executeWithThreadSessionData( pwmRequest.getLabel(), () ->
        {
            try
            {
                pwmRequest.getPwmDomain().getActiveServletRequests().incrementAndGet();
                filterChain.doFilter( req, resp );
            }
            catch ( final Throwable e )
            {
                throwableReference.set( e );
            }
            finally
            {
                pwmRequest.getPwmDomain().getActiveServletRequests().decrementAndGet();
            }
        } );

        if ( throwableReference.get() != null )
        {
            processInitThrowable( throwableReference.get(), req, resp );
        }
    }

    private void doTheThing( final PwmRequest pwmRequest )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        try
        {
            checkIfSessionRecycleNeeded( pwmRequest );

            handleRequestInitialization( pwmRequest );

            addPwmResponseHeaders( pwmRequest );

            checkIdleTimeout( pwmRequest );

            updateStats( pwmRequest.getPwmApplication() );

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
        }
    }

    private void processInitThrowable( final Throwable e, final HttpServletRequest req, final HttpServletResponse resp )
            throws ServletException, IOException
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
        try
        {
            if ( !( PwmURL.create( req ).isResourceURL() ) )
            {
                respondWithUnavailableError( req, resp );
            }
        }
        catch ( final PwmUnrecoverableException pwmUnrecoverableException )
        {
            LOGGER.debug( () -> "error initializing http request for " + PwmConstants.PWM_APP_NAME + ": " + e.getMessage() );
        }
    }

    private void updateStats( final PwmApplication localPwmApplication )
    {
        if ( localPwmApplication != null && localPwmApplication.getApplicationMode() == PwmApplicationMode.RUNNING )
        {
            if ( localPwmApplication.getStatisticsManager() != null )
            {
                localPwmApplication.getStatisticsManager().updateEps( EpsStatistic.REQUESTS, 1 );
            }
        }
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
        final ContextManager contextManager = ContextManager.getContextManager( request.getServletContext() );
        final PwmApplication pwmApplication = contextManager.getPwmApplication();

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

    private static final List<PwmServletDefinition> NON_API_SERVLETS = Arrays.stream( PwmServletDefinition.values() )
            .filter( definition -> definition != PwmServletDefinition.ClientApi )
            .collect( Collectors.toList() );

    private void checkIfSessionRecycleNeeded( final PwmRequest pwmRequest )
    {
        if ( pwmRequest.getPwmSession().getSessionStateBean().isSessionIdRecycleNeeded()
                && pwmRequest.getURL().matches( NON_API_SERVLETS )
        )
        {
            if ( pwmRequest.getAppConfig().readBooleanAppProperty( AppProperty.HTTP_SESSION_RECYCLE_AT_AUTH ) )
            {
                pwmRequest.getHttpServletRequest().changeSessionId();
                pwmRequest.getPwmSession().getSessionStateBean().setSessionIdRecycleNeeded( false );
                LOGGER.trace( pwmRequest, () -> "changeSessionId() requested from servlet container" );
            }
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
        final AppConfig config = pwmApplication.getConfig();
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

        addStaticResponseHeaders( pwmApplication, pwmRequest.getHttpServletRequest(), resp.getHttpServletResponse() );

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
                final String replacedPolicy = contentPolicy.replace( "%NONCE%", nonce );
                final String expandedPolicy = MacroRequest.forNonUserSpecific( pwmRequest.getPwmApplication(), null ).expandMacros( replacedPolicy );
                resp.setHeader( HttpHeader.ContentSecurityPolicy, expandedPolicy );
            }
        }
    }

    public static void addStaticResponseHeaders(
            final PwmApplication pwmApplication,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws PwmUnrecoverableException
    {
        final AppConfig config = pwmApplication.getConfig();

        final String serverHeader = config.readAppProperty( AppProperty.HTTP_HEADER_SERVER );
        final boolean includeXInstance = Boolean.parseBoolean( config.readAppProperty( AppProperty.HTTP_HEADER_SEND_XINSTANCE ) );
        final boolean includeXVersion = Boolean.parseBoolean( config.readAppProperty( AppProperty.HTTP_HEADER_SEND_XVERSION ) );
        final boolean includeXContentTypeOptions = Boolean.parseBoolean( config.readAppProperty( AppProperty.HTTP_HEADER_SEND_XCONTENTTYPEOPTIONS ) );
        final boolean includeXXSSProtection = Boolean.parseBoolean( config.readAppProperty( AppProperty.HTTP_HEADER_SEND_XXSSPROTECTION ) );
        final boolean includeXFrameDeny = config.readSettingAsBoolean( PwmSetting.SECURITY_PREVENT_FRAMING );
        final boolean includeXAmb = Boolean.parseBoolean( config.readAppProperty( AppProperty.HTTP_HEADER_SEND_XAMB ) );
        final boolean includeDomain = Boolean.parseBoolean( config.readAppProperty( AppProperty.HTTP_HEADER_SEND_XDOMAIN ) );

        makeNoiseHeader( pwmApplication, config ).ifPresent( noiseHeader -> resp.setHeader( HttpHeader.XNoise.getHttpName(), noiseHeader ) );

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
            final String value = MacroRequest.forNonUserSpecific( pwmApplication, null ).expandMacros( serverHeader );
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

        if ( includeDomain && pwmApplication.isMultiDomain() )
        {
            resp.setHeader( HttpHeader.XDomain.getHttpName(), PwmHttpRequestWrapper.readDomainIdFromRequest( req ).stringValue() );
        }

        {
            final String cacheControl = pwmApplication.getConfig().readAppProperty( AppProperty.HTTP_HEADER_CACHE_CONTROL );
            if ( StringUtil.notEmpty( cacheControl ) )
            {
                resp.setHeader( HttpHeader.CacheControl.getHttpName(), cacheControl );
            }
        }
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
            ssBean.setSrcAddress( pwmRequest.getSrcAddress().orElse( "" ) );
        }

        // mark the user's hostname in the session bean
        if ( ssBean.getSrcHostname() == null )
        {
            ssBean.setSrcHostname( pwmRequest.getSrcHostname().orElse( "" ) );
        }

        // update the privateUrlAccessed flag
        if ( pwmURL.isPrivateUrl() )
        {
            ssBean.setPrivateUrlAccessed( true );
        }

        initializeTheme( pwmRequest );

        // set idle timeout
        PwmSessionFactory.setHttpSessionIdleTimeout( pwmRequest.getPwmDomain(), pwmRequest, pwmRequest.getHttpServletRequest().getSession() );
    }

    private static void initializeTheme(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final String themeCookieName = pwmRequest.getDomainConfig().readAppProperty( AppProperty.HTTP_COOKIE_THEME_NAME );
        if ( StringUtil.notEmpty( themeCookieName ) )
        {
            final Optional<String> themeCookie = pwmRequest.readCookie( themeCookieName );
            if ( themeCookie.isPresent() )
            {
                if ( pwmRequest.getPwmDomain().getResourceServletService().checkIfThemeExists( pwmRequest, themeCookie.get() ) )
                {
                    LOGGER.debug( pwmRequest, () -> "detected theme cookie in request, setting theme to " + themeCookie );
                    pwmRequest.getPwmSession().getSessionStateBean().setTheme( themeCookie.get() );
                }
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

        // csrf cross-site request forgery checks
        checkCsrfHeader( pwmRequest );

        // check trial
        checkTrial( pwmRequest );

        // check intruder
        IntruderServiceClient.checkAddressAndSession( pwmRequest.getPwmDomain(), pwmRequest.getPwmSession() );
    }

    private static void checkIfSourceAddressChanged( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        if ( !pwmRequest.getAppConfig().readSettingAsBoolean( PwmSetting.MULTI_IP_SESSION_ALLOWED ) )
        {
            final Optional<String> optionalRemoteAddress = PwmRequestUtil.readUserNetworkAddress( pwmRequest.getHttpServletRequest(), pwmRequest.getAppConfig() );
            final LocalSessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();

            if ( optionalRemoteAddress.isPresent() )
            {
                final String remoteAddress = optionalRemoteAddress.get();
                if ( !ssBean.getSrcAddress().equals( remoteAddress ) )
                {
                    final String errorMsg = "current network address '" + remoteAddress + "' has changed from original network address '" + ssBean.getSrcAddress() + "'";
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SECURITY_VIOLATION, errorMsg );
                    throw new PwmUnrecoverableException( errorInformation );
                }
            }
        }
    }

    private static void checkTotalSessionTime( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final LocalSessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();

        if ( ssBean.getSessionCreationTime() != null )
        {
            final long maxSessionSeconds = pwmRequest.getAppConfig().readSettingAsLong( PwmSetting.SESSION_MAX_SECONDS );
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
        final List<String> requiredHeaders = pwmRequest.getAppConfig().readSettingAsStringArray( PwmSetting.REQUIRED_HEADERS );
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

    private static void checkCsrfHeader( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final boolean performCsrfHeaderChecks = Boolean.parseBoolean( pwmRequest.getDomainConfig().readAppProperty( AppProperty.SECURITY_HTTP_PERFORM_CSRF_HEADER_CHECKS ) );
        if (
                performCsrfHeaderChecks
                        && !pwmRequest.getMethod().isIdempotent()
                        && !pwmRequest.getURL().isRestService()
        )
        {
            final String originValue = pwmRequest.readHeaderValueAsString( HttpHeader.Origin );
            final String referrerValue = pwmRequest.readHeaderValueAsString( HttpHeader.Referer );
            final String siteUrl = pwmRequest.getPwmDomain().getConfig().readSettingAsString( PwmSetting.PWM_SITE_URL );

            final String targetValue = pwmRequest.getHttpServletRequest().getRequestURL().toString();
            if ( StringUtil.isEmpty( targetValue ) )
            {
                final String msg = "malformed request instance, missing target uri value";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SECURITY_VIOLATION, msg );
                LOGGER.debug( pwmRequest, () -> errorInformation.toDebugStr() + " [" + makeHeaderDebugStr( pwmRequest ) + "]" );
                throw new PwmUnrecoverableException( errorInformation );
            }

            final boolean originHeaderEvaluated;
            if ( StringUtil.notEmpty( originValue ) )
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
            if ( StringUtil.notEmpty( referrerValue ) )
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
            final StatisticsService statisticsManager = pwmRequest.getPwmDomain().getStatisticsManager();
            final String currentAuthString = statisticsManager.getStatBundleForKey( StatisticsService.KEY_CURRENT ).getStatistic( Statistic.AUTHENTICATIONS );
            if ( new BigInteger( currentAuthString ).compareTo( BigInteger.valueOf( PwmConstants.TRIAL_MAX_AUTHENTICATIONS ) ) > 0 )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_TRIAL_VIOLATION, "maximum usage per server startup exceeded" ) );
            }

            final String totalAuthString = statisticsManager.getStatBundleForKey( StatisticsService.KEY_CUMULATIVE ).getStatistic( Statistic.AUTHENTICATIONS );
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
            pwmRequest.getPwmSession().unAuthenticateUser( pwmRequest );
        }
    }

    private static final List<HttpHeader> DEBUG_HEADERS = List.of( HttpHeader.Referer, HttpHeader.Origin );

    private static String makeHeaderDebugStr( final PwmRequest pwmRequest )
    {
        final Map<String, String> values = DEBUG_HEADERS.stream().collect( Collectors.toMap(
                HttpHeader::getHttpName,
                pwmRequest::readHeaderValueAsString
        ) );

        values.put( "target", pwmRequest.getHttpServletRequest().getRequestURL().toString() );
        values.put( "siteUrl", pwmRequest.getPwmDomain().getConfig().readSettingAsString( PwmSetting.PWM_SITE_URL ) );

        return StringUtil.mapToString( values );
    }

    private static Optional<String> makeNoiseHeader( final PwmApplication pwmApplication, final AppConfig appConfig )
    {
        final boolean sendNoise = Boolean.parseBoolean( appConfig.readAppProperty( AppProperty.HTTP_HEADER_SEND_XNOISE ) );

        if ( sendNoise )
        {
            final int noiseLength = Integer.parseInt( appConfig.readAppProperty( AppProperty.HTTP_HEADER_NOISE_LENGTH ) );
            final PwmRandom pwmRandom = pwmApplication.getSecureService().pwmRandom();
            return Optional.of( pwmRandom.alphaNumericString( pwmRandom.nextInt( noiseLength ) + 11 ) );
        }

        return Optional.empty();
    }
}
