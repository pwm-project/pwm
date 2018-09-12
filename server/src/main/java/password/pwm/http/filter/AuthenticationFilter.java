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

package password.pwm.http.filter;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmHttpFilterAuthenticationProvider;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpHeader;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmHttpResponseWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.PwmURL;
import password.pwm.http.bean.ChangePasswordBean;
import password.pwm.http.servlet.LoginServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.http.servlet.oauth.OAuthMachine;
import password.pwm.http.servlet.oauth.OAuthSettings;
import password.pwm.i18n.Display;
import password.pwm.ldap.PasswordChangeProgressChecker;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.LocaleHelper;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Authentication servlet filter.  This filter wraps all servlet requests and requests direct to *.jsp
 * URLs and provides user authentication services.  Users must provide valid credentials to login.  This
 * filter checks for a Basic Authorization header in the request and will attempt to use that to validate
 * the user, if not, then the user will be passed to a form based login page (LoginServlet;
 *
 * @author Jason D. Rivard
 */
public class AuthenticationFilter extends AbstractPwmFilter
{

    private static final PwmLogger LOGGER = PwmLogger.getLogger( AuthenticationFilter.class.getName() );

    public void processFilter(
            final PwmApplicationMode mode,
            final PwmRequest pwmRequest,
            final PwmFilterChain chain
    )
            throws IOException, ServletException
    {
        final PwmURL pwmURL = pwmRequest.getURL();
        if ( pwmURL.isPublicUrl() && !pwmURL.isLoginServlet() )
        {
            chain.doFilter();
            return;
        }


        try
        {
            final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
            final PwmSession pwmSession = pwmRequest.getPwmSession();

            if ( pwmApplication.getApplicationMode() == PwmApplicationMode.NEW )
            {
                if ( pwmRequest.getURL().isConfigGuideURL() )
                {
                    chain.doFilter();
                    return;
                }
            }

            if ( pwmApplication.getApplicationMode() == PwmApplicationMode.CONFIGURATION )
            {
                if ( pwmRequest.getURL().isConfigManagerURL() )
                {
                    chain.doFilter();
                    return;
                }
            }

            //user is already authenticated
            if ( pwmSession.isAuthenticated() )
            {
                this.processAuthenticatedSession( pwmRequest, chain );
            }
            else
            {
                this.processUnAuthenticatedSession( pwmRequest, chain );
            }
        }
        catch ( PwmUnrecoverableException e )
        {
            LOGGER.error( e.getErrorInformation() );
            pwmRequest.respondWithError( e.getErrorInformation(), true );
        }
    }

    @Override
    boolean isInterested( final PwmApplicationMode mode, final PwmURL pwmURL )
    {
        return !pwmURL.isResourceURL() && !pwmURL.isRestService();
    }

    private void processAuthenticatedSession(
            final PwmRequest pwmRequest,
            final PwmFilterChain chain
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        // read the basic auth info out of the header (if it exists);
        if ( pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.BASIC_AUTH_ENABLED ) )
        {
            final BasicAuthInfo basicAuthInfo = BasicAuthInfo.parseAuthHeader( pwmApplication, pwmRequest );

            final BasicAuthInfo originalBasicAuthInfo = pwmSession.getLoginInfoBean().getBasicAuth();

            //check to make sure basic auth info is same as currently known user in session.
            if ( basicAuthInfo != null && originalBasicAuthInfo != null && !( originalBasicAuthInfo.equals( basicAuthInfo ) ) )
            {
                // if we read here then user is using basic auth, and header has changed since last request
                // this means something is screwy, so log out the session

                // read the current user info for logging
                final UserInfo userInfo = pwmSession.getUserInfo();
                final ErrorInformation errorInformation = new ErrorInformation(
                        PwmError.ERROR_BAD_SESSION,
                        "basic auth header user '" + basicAuthInfo.getUsername()
                                + "' does not match currently logged in user '" + userInfo.getUserIdentity()
                                + "', session will be logged out"
                );
                LOGGER.info( pwmRequest, errorInformation );

                // log out their user
                pwmSession.unauthenticateUser( pwmRequest );

                // send en error to user.
                pwmRequest.respondWithError( errorInformation, true );
                return;
            }
        }

        // check status of oauth expiration
        if ( pwmSession.getLoginInfoBean().getOauthExp() != null )
        {
            final OAuthSettings oauthSettings = OAuthSettings.forSSOAuthentication( pwmRequest.getConfig() );
            final OAuthMachine oAuthMachine = new OAuthMachine( oauthSettings );
            if ( oAuthMachine.checkOAuthExpiration( pwmRequest ) )
            {
                pwmRequest.respondWithError( new ErrorInformation( PwmError.ERROR_OAUTH_ERROR, "oauth access token has expired" ) );
                return;
            }
        }
        handleAuthenticationCookie( pwmRequest );

        if ( forceRequiredRedirects( pwmRequest ) == ProcessStatus.Halt )
        {
            return;
        }

        // user session is authed, and session and auth header match, so forward request on.
        chain.doFilter();
    }

    private static void handleAuthenticationCookie( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        if ( !pwmRequest.isAuthenticated() || pwmRequest.getPwmSession().getLoginInfoBean().getType() != AuthenticationType.AUTHENTICATED )
        {
            return;
        }

        if ( pwmRequest.getPwmSession().getLoginInfoBean().isLoginFlag( LoginInfoBean.LoginFlag.authRecordSet ) )
        {
            return;
        }

        pwmRequest.getPwmSession().getLoginInfoBean().setFlag( LoginInfoBean.LoginFlag.authRecordSet );

        final String cookieName = pwmRequest.getConfig().readAppProperty( AppProperty.HTTP_COOKIE_AUTHRECORD_NAME );
        if ( cookieName == null || cookieName.isEmpty() )
        {
            LOGGER.debug( pwmRequest, "skipping auth record cookie set, cookie name parameter is blank" );
            return;
        }

        final int cookieAgeSeconds = Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.HTTP_COOKIE_AUTHRECORD_AGE ) );
        if ( cookieAgeSeconds < 1 )
        {
            LOGGER.debug( pwmRequest, "skipping auth record cookie set, cookie age parameter is less than 1" );
            return;
        }

        final Instant authTime = pwmRequest.getPwmSession().getLoginInfoBean().getAuthTime();
        final String userGuid = pwmRequest.getPwmSession().getUserInfo().getUserGuid();
        final AuthRecord authRecord = new AuthRecord( authTime, userGuid );

        try
        {
            pwmRequest.getPwmResponse().writeEncryptedCookie( cookieName, authRecord, cookieAgeSeconds, PwmHttpResponseWrapper.CookiePath.Application );
            LOGGER.debug( pwmRequest, "wrote auth record cookie to user browser for use during forgotten password" );
        }
        catch ( PwmUnrecoverableException e )
        {
            LOGGER.error( pwmRequest, "error while setting authentication record cookie: " + e.getMessage() );
        }
    }

    public static class AuthRecord implements Serializable
    {
        private Instant date;
        private String guid;

        public AuthRecord( final Instant date, final String guid )
        {
            this.date = date;
            this.guid = guid;
        }

        public Instant getDate( )
        {
            return date;
        }

        public String getGuid( )
        {
            return guid;
        }
    }

    private void processUnAuthenticatedSession(
            final PwmRequest pwmRequest,
            final PwmFilterChain chain
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();

        final boolean bypassSso = pwmRequest.getPwmSession().getLoginInfoBean().isLoginFlag( LoginInfoBean.LoginFlag.noSso );
        if ( !bypassSso && pwmRequest.getPwmApplication().getApplicationMode() == PwmApplicationMode.RUNNING )
        {
            final ProcessStatus authenticationProcessStatus = attemptAuthenticationMethods( pwmRequest );
            if ( authenticationProcessStatus == ProcessStatus.Halt )
            {
                return;
            }
        }

        final String originalRequestedUrl = pwmRequest.getURLwithQueryString();

        if ( pwmRequest.isAuthenticated() )
        {
            // redirect back to self so request starts over as authenticated.
            LOGGER.trace( pwmRequest, "inline authentication occurred during this request, redirecting to current url to restart request" );
            pwmRequest.getPwmResponse().sendRedirect( originalRequestedUrl );
            return;
        }

        // handle if authenticated during filter process.
        if ( pwmSession.isAuthenticated() )
        {
            pwmSession.getSessionStateBean().setSessionIdRecycleNeeded( true );
            LOGGER.debug( pwmSession, "session authenticated during request, issuing redirect to originally requested url: " + originalRequestedUrl );
            pwmRequest.sendRedirect( originalRequestedUrl );
            return;
        }

        if ( pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.BASIC_AUTH_FORCE ) )
        {
            final String displayMessage = LocaleHelper.getLocalizedMessage( Display.Title_Application, pwmRequest );
            pwmRequest.getPwmResponse().setHeader( HttpHeader.WWW_Authenticate, "Basic realm=\"" + displayMessage + "\"" );
            pwmRequest.getPwmResponse().setStatus( 401 );
            return;
        }

        if ( pwmRequest.getURL().isLoginServlet() )
        {
            chain.doFilter();
            return;
        }

        //user is not authenticated so forward to LoginPage.
        LOGGER.trace( pwmSession.getLabel(),
                "user requested resource requiring authentication (" + req.getRequestURI()
                        + "), but is not authenticated; redirecting to LoginServlet" );

        LoginServlet.redirectToLoginServlet( pwmRequest );
    }

    private static final Set<AuthenticationMethod> IGNORED_AUTH_METHODS = new HashSet<>();

    private static ProcessStatus attemptAuthenticationMethods( final PwmRequest pwmRequest ) throws IOException, ServletException
    {
        if ( pwmRequest.isAuthenticated() )
        {
            return ProcessStatus.Continue;
        }

        for ( final AuthenticationMethod authenticationMethod : AuthenticationMethod.values() )
        {
            if ( !IGNORED_AUTH_METHODS.contains( authenticationMethod ) )
            {
                PwmHttpFilterAuthenticationProvider filterAuthenticationProvider = null;
                try
                {
                    final String className = authenticationMethod.getClassName();
                    final Class clazz = Class.forName( className );
                    final Object newInstance = clazz.newInstance();
                    filterAuthenticationProvider = ( PwmHttpFilterAuthenticationProvider ) newInstance;
                }
                catch ( Exception e )
                {
                    LOGGER.trace( "could not load authentication class '" + authenticationMethod + "', will ignore" );
                    IGNORED_AUTH_METHODS.add( authenticationMethod );
                }

                if ( filterAuthenticationProvider != null )
                {
                    try
                    {
                        filterAuthenticationProvider.attemptAuthentication( pwmRequest );

                        if ( pwmRequest.isAuthenticated() )
                        {
                            LOGGER.trace( pwmRequest, "authentication provided by method " + authenticationMethod.name() );
                        }

                        if ( filterAuthenticationProvider.hasRedirectedResponse() )
                        {
                            LOGGER.trace( pwmRequest, "authentication provider " + authenticationMethod.name()
                                    + " has issued a redirect, halting authentication process" );
                            return ProcessStatus.Halt;
                        }

                    }
                    catch ( Exception e )
                    {
                        final ErrorInformation errorInformation;
                        if ( e instanceof PwmException )
                        {
                            final String errorMsg = "error during " + authenticationMethod + " authentication attempt: " + e.getMessage();
                            errorInformation = new ErrorInformation( ( ( PwmException ) e ).getError(), errorMsg );
                        }
                        else
                        {
                            errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, e.getMessage() );

                        }
                        LOGGER.error( pwmRequest, errorInformation );
                        pwmRequest.respondWithError( errorInformation );
                        return ProcessStatus.Halt;
                    }
                }
            }
        }
        return ProcessStatus.Continue;
    }


    public static ProcessStatus forceRequiredRedirects(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmURL pwmURL = pwmRequest.getURL();

        final UserInfo userInfo = pwmSession.getUserInfo();
        final LoginInfoBean loginInfoBean = pwmSession.getLoginInfoBean();

        if ( pwmURL.isResourceURL() || pwmURL.isConfigManagerURL() || pwmURL.isLogoutURL() || pwmURL.isLoginServlet() )
        {
            return ProcessStatus.Continue;
        }

        if ( pwmRequest.getPwmApplication().getApplicationMode() != PwmApplicationMode.RUNNING )
        {
            return ProcessStatus.Continue;
        }

        // high priority pw change
        if ( loginInfoBean.getType() == AuthenticationType.AUTH_FROM_PUBLIC_MODULE )
        {
            if ( !pwmURL.isChangePasswordURL() )
            {
                LOGGER.debug( pwmRequest, "user is authenticated via forgotten password mechanism, redirecting to change password servlet" );
                pwmRequest.sendRedirect(
                        pwmRequest.getContextPath()
                                + PwmConstants.URL_PREFIX_PUBLIC
                                + "/"
                                + PwmServletDefinition.PrivateChangePassword.servletUrlName() );
                return ProcessStatus.Halt;
            }
            else
            {
                return ProcessStatus.Continue;
            }
        }

        // if change password in progress and req is for ChangePassword servlet, then allow request as is
        if ( pwmURL.isChangePasswordURL() )
        {
            final ChangePasswordBean cpb = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, ChangePasswordBean.class );
            final PasswordChangeProgressChecker.ProgressTracker progressTracker = cpb.getChangeProgressTracker();
            if ( progressTracker != null && progressTracker.getBeginTime() != null )
            {
                return ProcessStatus.Continue;
            }
        }


        if ( userInfo.isRequiresResponseConfig() )
        {
            if ( !pwmURL.isSetupResponsesURL() )
            {
                LOGGER.debug( pwmRequest, "user is required to setup responses, redirecting to setup responses servlet" );
                pwmRequest.sendRedirect( PwmServletDefinition.SetupResponses );
                return ProcessStatus.Halt;
            }
            else
            {
                return ProcessStatus.Continue;
            }
        }

        if ( userInfo.isRequiresOtpConfig() && !pwmSession.getLoginInfoBean().isLoginFlag( LoginInfoBean.LoginFlag.skipOtp ) )
        {
            if ( !pwmURL.isSetupOtpSecretURL() )
            {
                LOGGER.debug( pwmRequest, "user is required to setup OTP configuration, redirecting to OTP setup page" );
                pwmRequest.sendRedirect( PwmServletDefinition.SetupOtp );
                return ProcessStatus.Halt;
            }
            else
            {
                return ProcessStatus.Continue;
            }
        }

        if ( userInfo.isRequiresUpdateProfile() )
        {
            if ( !pwmURL.isProfileUpdateURL() )
            {
                LOGGER.debug( pwmRequest, "user is required to update profile, redirecting to profile update servlet" );
                pwmRequest.sendRedirect( PwmServletDefinition.UpdateProfile );
                return ProcessStatus.Halt;
            }
            else
            {
                return ProcessStatus.Continue;
            }
        }


        if ( !pwmURL.isChangePasswordURL() )
        {
            if ( userInfo.isRequiresNewPassword() && !loginInfoBean.isLoginFlag( LoginInfoBean.LoginFlag.skipNewPw ) )
            {
                LOGGER.debug( pwmRequest, "user password in ldap requires changing, redirecting to change password servlet" );
                pwmRequest.sendRedirect( PwmServletDefinition.PrivateChangePassword );
                return ProcessStatus.Halt;
            }
            else if ( loginInfoBean.getLoginFlags().contains( LoginInfoBean.LoginFlag.forcePwChange ) )
            {
                LOGGER.debug( pwmRequest, "previous activity in application requires forcing pw change, redirecting to change password servlet" );
                pwmRequest.sendRedirect( PwmServletDefinition.PrivateChangePassword );
                return ProcessStatus.Halt;
            }
            else
            {
                return ProcessStatus.Continue;
            }
        }

        return ProcessStatus.Continue;
    }

    enum AuthenticationMethod
    {
        BASIC_AUTH( BasicFilterAuthenticationProvider.class.getName() ),
        SSO_AUTH_HEADER( SSOHeaderFilterAuthenticationProvider.class.getName() ),
        CAS( "password.pwm.util.CASFilterAuthenticationProvider" ),
        OAUTH( OAuthFilterAuthenticationProvider.class.getName() );

        private final String className;

        AuthenticationMethod( final String className )
        {
            this.className = className;
        }

        public String getClassName( )
        {
            return className;
        }
    }

    public static class BasicFilterAuthenticationProvider implements PwmHttpFilterAuthenticationProvider
    {

        @Override
        public void attemptAuthentication(
                final PwmRequest pwmRequest
        )
                throws PwmUnrecoverableException
        {
            if ( !pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.BASIC_AUTH_ENABLED ) )
            {
                return;
            }

            if ( pwmRequest.isAuthenticated() )
            {
                return;
            }

            final BasicAuthInfo basicAuthInfo = BasicAuthInfo.parseAuthHeader( pwmRequest.getPwmApplication(), pwmRequest );
            if ( basicAuthInfo == null )
            {
                return;
            }

            try
            {
                final PwmSession pwmSession = pwmRequest.getPwmSession();
                final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

                //user isn't already authenticated and has an auth header, so try to auth them.
                LOGGER.debug( pwmSession, "attempting to authenticate user using basic auth header (username=" + basicAuthInfo.getUsername() + ")" );
                final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(
                        pwmApplication,
                        pwmSession,
                        PwmAuthenticationSource.BASIC_AUTH
                );
                final UserSearchEngine userSearchEngine = pwmApplication.getUserSearchEngine();
                final UserIdentity userIdentity = userSearchEngine.resolveUsername( basicAuthInfo.getUsername(), null, null, pwmSession.getLabel() );
                sessionAuthenticator.authenticateUser( userIdentity, basicAuthInfo.getPassword() );
                pwmSession.getLoginInfoBean().setBasicAuth( basicAuthInfo );

            }
            catch ( PwmException e )
            {
                if ( e.getError() == PwmError.ERROR_DIRECTORY_UNAVAILABLE )
                {
                    StatisticsManager.incrementStat( pwmRequest, Statistic.LDAP_UNAVAILABLE_COUNT );
                }
                throw new PwmUnrecoverableException( e.getError() );
            }
        }

        @Override
        public boolean hasRedirectedResponse( )
        {
            return false;
        }
    }

    static class SSOHeaderFilterAuthenticationProvider implements PwmHttpFilterAuthenticationProvider
    {

        @Override
        public void attemptAuthentication(
                final PwmRequest pwmRequest
        )
                throws PwmUnrecoverableException
        {
            {
                final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
                final PwmSession pwmSession = pwmRequest.getPwmSession();

                final String headerName = pwmApplication.getConfig().readSettingAsString( PwmSetting.SSO_AUTH_HEADER_NAME );
                if ( headerName == null || headerName.length() < 1 )
                {
                    return;
                }


                final String headerValue = pwmRequest.readHeaderValueAsString( headerName );
                if ( headerValue == null || headerValue.length() < 1 )
                {
                    return;
                }

                LOGGER.debug( pwmRequest, "SSO Authentication header present in request, will search for user value of '" + headerValue + "'" );
                final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(
                        pwmApplication,
                        pwmSession,
                        PwmAuthenticationSource.SSO_HEADER
                );

                try
                {
                    sessionAuthenticator.authUserWithUnknownPassword( headerValue, AuthenticationType.AUTH_WITHOUT_PASSWORD );
                }
                catch ( PwmOperationalException e )
                {
                    throw new PwmUnrecoverableException( e.getErrorInformation() );
                }
            }
        }

        @Override
        public boolean hasRedirectedResponse( )
        {
            return false;
        }
    }


    static class OAuthFilterAuthenticationProvider implements PwmHttpFilterAuthenticationProvider
    {

        private boolean redirected = false;

        public void attemptAuthentication(
                final PwmRequest pwmRequest
        )
                throws PwmUnrecoverableException, IOException
        {
            final OAuthSettings oauthSettings = OAuthSettings.forSSOAuthentication( pwmRequest.getConfig() );
            if ( !oauthSettings.oAuthIsConfigured() )
            {
                return;
            }

            final String originalURL = pwmRequest.getURLwithQueryString();
            final OAuthMachine oAuthMachine = new OAuthMachine( oauthSettings );
            oAuthMachine.redirectUserToOAuthServer( pwmRequest, originalURL, null, null );
            redirected = true;
        }

        @Override
        public boolean hasRedirectedResponse( )
        {
            return redirected;
        }
    }
}

