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
import password.pwm.bean.LoginInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpHeader;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.PwmURL;
import password.pwm.http.auth.HttpAuthenticationUtilities;
import password.pwm.http.bean.ChangePasswordBean;
import password.pwm.http.servlet.LoginServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.http.servlet.oauth.OAuthMachine;
import password.pwm.http.servlet.oauth.OAuthSettings;
import password.pwm.i18n.Display;
import password.pwm.ldap.PasswordChangeProgressChecker;
import password.pwm.user.UserInfo;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    @Override
    boolean isInterested( final PwmApplicationMode mode, final PwmURL pwmURL )
    {
        return ( pwmURL.isPrivateUrl()
                || pwmURL.isAdminUrl()
                || pwmURL.isChangePasswordURL() )
                && ( !pwmURL.isResourceURL() && !pwmURL.isRestService()
        );
    }

    @Override
    public void processFilter(
            final PwmApplicationMode mode,
            final PwmRequest pwmRequest,
            final PwmFilterChain chain
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmURL pwmURL = pwmRequest.getURL();
        if ( pwmURL.isPublicUrl() && !pwmURL.matches( PwmServletDefinition.Login ) )
        {
            chain.doFilter();
            return;
        }


        try
        {
            final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
            final PwmSession pwmSession = pwmRequest.getPwmSession();

            if ( pwmDomain.getApplicationMode() == PwmApplicationMode.NEW )
            {
                if ( pwmRequest.getURL().isConfigGuideURL() )
                {
                    chain.doFilter();
                    return;
                }
            }

            if ( pwmDomain.getApplicationMode() == PwmApplicationMode.CONFIGURATION )
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
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.error( e.getErrorInformation() );
            pwmRequest.respondWithError( e.getErrorInformation(), true );
        }
    }
    
    private void processAuthenticatedSession(
            final PwmRequest pwmRequest,
            final PwmFilterChain chain
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        // read the basic auth info out of the header (if it exists);
        if ( pwmRequest.getDomainConfig().readSettingAsBoolean( PwmSetting.BASIC_AUTH_ENABLED ) )
        {
            final Optional<BasicAuthInfo> basicAuthInfo = BasicAuthInfo.parseAuthHeader( pwmDomain, pwmRequest );

            final BasicAuthInfo originalBasicAuthInfo = pwmSession.getLoginInfoBean().getBasicAuth();

            //check to make sure basic auth info is same as currently known user in session.
            if ( basicAuthInfo.isPresent() && Objects.equals( basicAuthInfo.get(), originalBasicAuthInfo ) )
            {
                // if we read here then user is using basic auth, and header has changed since last request
                // this means something is screwy, so log out the session

                // read the current user info for logging
                final UserInfo userInfo = pwmSession.getUserInfo();
                final ErrorInformation errorInformation = new ErrorInformation(
                        PwmError.ERROR_BAD_SESSION,
                        "basic auth header user '" + basicAuthInfo.get().getUsername()
                                + "' does not match currently logged in user '" + userInfo.getUserIdentity()
                                + "', session will be logged out"
                );
                LOGGER.info( pwmRequest, errorInformation );

                // log out their user
                pwmSession.unAuthenticateUser( pwmRequest );

                // send en error to user.
                pwmRequest.respondWithError( errorInformation, true );
                return;
            }
        }

        // check status of oauth expiration
        if ( pwmSession.getLoginInfoBean().getOauthExp() != null )
        {
            final OAuthSettings oauthSettings = OAuthSettings.forSSOAuthentication( pwmRequest.getDomainConfig() );
            final OAuthMachine oAuthMachine = new OAuthMachine( pwmRequest.getLabel(), oauthSettings );
            if ( oAuthMachine.checkOAuthExpiration( pwmRequest ) )
            {
                pwmRequest.respondWithError( new ErrorInformation( PwmError.ERROR_OAUTH_ERROR, "oauth access token has expired" ) );
                return;
            }
        }

        HttpAuthenticationUtilities.handleAuthenticationCookie( pwmRequest );

        if ( forceRequiredRedirects( pwmRequest ) == ProcessStatus.Halt )
        {
            return;
        }

        if ( pwmRequest.getClientConnectionHolder().isAuthenticatedWithoutPasswordAndBind() )
        {
            final Optional<PwmServletDefinition> pwmServletDefinition = pwmRequest.getURL().forServletDefinition();
            if ( pwmServletDefinition.isPresent() )
            {
                if ( pwmServletDefinition.get().getFlags().contains( PwmServletDefinition.Flag.RequiresUserPasswordAndBind ) )
                {
                    try
                    {
                        LOGGER.debug( pwmRequest, () -> "user is authenticated without a password, but module " + pwmServletDefinition.get().name()
                                + " requires user connection, redirecting to login page" );
                        LoginServlet.redirectToLoginServlet( pwmRequest );
                        return;
                    }
                    catch ( final Throwable e1 )
                    {
                        LOGGER.error( () -> "error while marking pre-login url:" + e1.getMessage() );
                    }
                }
            }
        }

        // user session is authed, and session and auth header match, so forward request on.
        chain.doFilter();
    }


    private void processUnAuthenticatedSession(
            final PwmRequest pwmRequest,
            final PwmFilterChain chain
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();

        final boolean bypassSso = pwmRequest.getPwmSession().getLoginInfoBean().isLoginFlag( LoginInfoBean.LoginFlag.noSso );
        if ( !bypassSso && pwmRequest.getPwmDomain().getApplicationMode() == PwmApplicationMode.RUNNING )
        {
            final ProcessStatus authenticationProcessStatus = HttpAuthenticationUtilities.attemptAuthenticationMethods( pwmRequest );
            if ( authenticationProcessStatus == ProcessStatus.Halt )
            {
                return;
            }
        }

        final String originalRequestedUrl = pwmRequest.getURLwithQueryString();

        if ( pwmRequest.isAuthenticated() )
        {
            // redirect back to self so request starts over as authenticated.
            LOGGER.trace( pwmRequest, () -> "inline authentication occurred during this request, redirecting to current url to restart request" );
            pwmRequest.getPwmResponse().sendRedirect( originalRequestedUrl );
            return;
        }

        // handle if authenticated during filter process.
        if ( pwmSession.isAuthenticated() )
        {
            pwmSession.getSessionStateBean().setSessionIdRecycleNeeded( true );
            LOGGER.debug( pwmRequest, () -> "session authenticated during request, issuing redirect to originally requested url: " + originalRequestedUrl );
            pwmRequest.getPwmResponse().sendRedirect( originalRequestedUrl );
            return;
        }

        if ( pwmDomain.getConfig().readSettingAsBoolean( PwmSetting.BASIC_AUTH_FORCE ) )
        {
            final String displayMessage = LocaleHelper.getLocalizedMessage( Display.Title_Application, pwmRequest );
            pwmRequest.getPwmResponse().setHeader( HttpHeader.WWW_Authenticate, "Basic realm=\"" + displayMessage + "\"" );
            pwmRequest.getPwmResponse().setStatus( 401 );
            return;
        }

        if ( pwmRequest.getURL().matches( PwmServletDefinition.Login ) )
        {
            chain.doFilter();
            return;
        }

        if ( pwmRequest.isJsonRequest() )
        {
            pwmRequest.respondWithError( new ErrorInformation( PwmError.ERROR_AUTHENTICATION_REQUIRED ) );
            return;
        }

        //user is not authenticated so forward to LoginPage.
        LOGGER.trace( pwmRequest, () -> "user requested resource requiring authentication (" + req.getRequestURI()
                + "), but is not authenticated; redirecting to LoginServlet" );

        LoginServlet.redirectToLoginServlet( pwmRequest );
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

        final List<PwmServletDefinition> ignoredServlets = List.of(
                PwmServletDefinition.Login,
                PwmServletDefinition.ConfigManager,
                PwmServletDefinition.Logout );

        if ( pwmURL.isResourceURL() || pwmURL.matches( ignoredServlets ) )
        {
            return ProcessStatus.Continue;
        }

        if ( pwmRequest.getPwmDomain().getApplicationMode() != PwmApplicationMode.RUNNING )
        {
            return ProcessStatus.Continue;
        }

        // high priority pw change
        if ( loginInfoBean.getType() == AuthenticationType.AUTH_FROM_PUBLIC_MODULE )
        {
            if ( !pwmURL.isChangePasswordURL() )
            {
                LOGGER.debug( pwmRequest, () -> "user is authenticated via forgotten password mechanism, redirecting to change password servlet" );
                pwmRequest.getPwmResponse().sendRedirect(
                        pwmRequest.getBasePath()
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
            final ChangePasswordBean cpb = pwmRequest.getPwmDomain().getSessionStateService().getBean( pwmRequest, ChangePasswordBean.class );
            final PasswordChangeProgressChecker.ProgressTracker progressTracker = cpb.getChangeProgressTracker();
            if ( progressTracker != null && progressTracker.getBeginTime() != null )
            {
                return ProcessStatus.Continue;
            }
        }


        if ( userInfo.isRequiresResponseConfig() && !pwmSession.getLoginInfoBean().isLoginFlag( LoginInfoBean.LoginFlag.skipSetupCr ) )
        {
            if ( !pwmURL.matches( PwmServletDefinition.SetupResponses ) )
            {
                LOGGER.debug( pwmRequest, () -> "user is required to setup responses, redirecting to setup responses servlet" );
                pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.SetupResponses );
                return ProcessStatus.Halt;
            }
            else
            {
                return ProcessStatus.Continue;
            }
        }

        if ( userInfo.isRequiresOtpConfig() && !pwmSession.getLoginInfoBean().isLoginFlag( LoginInfoBean.LoginFlag.skipOtp ) )
        {
            if ( !pwmURL.matches( PwmServletDefinition.SetupOtp ) )
            {
                LOGGER.debug( pwmRequest, () -> "user is required to setup OTP configuration, redirecting to OTP setup page" );
                pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.SetupOtp );
                return ProcessStatus.Halt;
            }
            else
            {
                return ProcessStatus.Continue;
            }
        }

        if ( userInfo.isRequiresUpdateProfile() )
        {
            if ( !pwmURL.matches( PwmServletDefinition.UpdateProfile ) )
            {
                LOGGER.debug( pwmRequest, () -> "user is required to update profile, redirecting to profile update servlet" );
                pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.UpdateProfile );
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
                LOGGER.debug( pwmRequest, () -> "user password in ldap requires changing, redirecting to change password servlet" );
                pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.PrivateChangePassword );
                return ProcessStatus.Halt;
            }
            else if ( loginInfoBean.getLoginFlags().contains( LoginInfoBean.LoginFlag.forcePwChange ) )
            {
                LOGGER.debug( pwmRequest, () -> "previous activity in application requires forcing pw change, redirecting to change password servlet" );
                pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.PrivateChangePassword );
                return ProcessStatus.Halt;
            }
            else
            {
                return ProcessStatus.Continue;
            }
        }

        return ProcessStatus.Continue;
    }
}

