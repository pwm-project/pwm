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

import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
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
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

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
        catch ( final PwmUnrecoverableException e )
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

        if ( pwmSession.getSessionManager().isAuthenticatedWithoutPasswordAndBind() )
        {
            final PwmServletDefinition pwmServletDefinition = pwmRequest.getURL().forServletDefinition();
            if ( pwmServletDefinition != null
                    && pwmServletDefinition.getFlags().contains( PwmServletDefinition.Flag.RequiresUserPasswordAndBind ) )
            {
                try
                {
                    LOGGER.debug( pwmRequest, () -> "user is authenticated without a password, but module " + pwmServletDefinition.name()
                            +  " requires user connection, redirecting to login page" );
                    LoginServlet.redirectToLoginServlet( pwmRequest );
                    return;
                }
                catch ( final Throwable e1 )
                {
                    LOGGER.error( () -> "error while marking pre-login url:" + e1.getMessage() );
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
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();

        final boolean bypassSso = pwmRequest.getPwmSession().getLoginInfoBean().isLoginFlag( LoginInfoBean.LoginFlag.noSso );
        if ( !bypassSso && pwmRequest.getPwmApplication().getApplicationMode() == PwmApplicationMode.RUNNING )
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
                LOGGER.debug( pwmRequest, () -> "user is authenticated via forgotten password mechanism, redirecting to change password servlet" );
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


        if ( userInfo.isRequiresResponseConfig() && !pwmSession.getLoginInfoBean().isLoginFlag( LoginInfoBean.LoginFlag.skipSetupCr ) )
        {
            if ( !pwmURL.isSetupResponsesURL() )
            {
                LOGGER.debug( pwmRequest, () -> "user is required to setup responses, redirecting to setup responses servlet" );
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
                LOGGER.debug( pwmRequest, () -> "user is required to setup OTP configuration, redirecting to OTP setup page" );
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
                LOGGER.debug( pwmRequest, () -> "user is required to update profile, redirecting to profile update servlet" );
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
                LOGGER.debug( pwmRequest, () -> "user password in ldap requires changing, redirecting to change password servlet" );
                pwmRequest.sendRedirect( PwmServletDefinition.PrivateChangePassword );
                return ProcessStatus.Halt;
            }
            else if ( loginInfoBean.getLoginFlags().contains( LoginInfoBean.LoginFlag.forcePwChange ) )
            {
                LOGGER.debug( pwmRequest, () -> "previous activity in application requires forcing pw change, redirecting to change password servlet" );
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
}

