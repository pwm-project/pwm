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

package password.pwm.http.servlet.oauth;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.PwmURL;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.http.servlet.forgottenpw.ForgottenPasswordServlet;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@WebServlet(
        name = "OAuthConsumerServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + "/oauth"
        }
)
public class OAuthConsumerServlet extends AbstractPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( OAuthConsumerServlet.class );


    @Override
    protected ProcessAction readProcessAction( final PwmRequest request )
            throws PwmUnrecoverableException
    {
        return null;
    }

    @Override
    @SuppressWarnings( "checkstyle:MethodLength" )
    protected void processAction( final PwmRequest pwmRequest )
            throws ServletException, IOException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmRequest.getConfig();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        final boolean userIsAuthenticated = pwmSession.isAuthenticated();
        final Optional<OAuthRequestState> oAuthRequestState = OAuthMachine.readOAuthRequestState( pwmRequest );

        final OAuthUseCase oAuthUseCaseCase = oAuthRequestState.isPresent()
                ? oAuthRequestState.get().getOAuthState().getUseCase()
                : OAuthUseCase.Authentication;

        LOGGER.trace( pwmRequest, () -> "processing oauth return request, useCase=" + oAuthUseCaseCase
                + ", incoming oAuthRequestState="
                + ( oAuthRequestState.isPresent() ? JsonUtil.serialize( oAuthRequestState.get() ) : "none" )
        );

        // make sure it's okay to be processing this request.
        // for non-auth requests its okay to continue
        if ( oAuthUseCaseCase == OAuthUseCase.Authentication )
        {
            if ( !userIsAuthenticated && !pwmSession.getSessionStateBean().isOauthInProgress() )
            {
                if ( oAuthRequestState.isPresent() )
                {
                    final String nextUrl = oAuthRequestState.get().getOAuthState().getNextUrl();
                    LOGGER.debug( pwmRequest, () -> "received unrecognized oauth response, ignoring authcode and redirecting to embedded next url: " + nextUrl );
                    pwmRequest.sendRedirect( nextUrl );
                    return;
                }
                final String errorMsg = "oauth consumer reached, but oauth authentication has not yet been initiated.";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_OAUTH_ERROR, errorMsg );
                pwmRequest.respondWithError( errorInformation );
                LOGGER.error( pwmRequest, () -> errorMsg );
                return;
            }
        }

        // check if there is an "error" on the request sent from the oauth server., if there is then halt.
        {
            final String oauthRequestError = pwmRequest.readParameterAsString( "error" );
            if ( oauthRequestError != null && !oauthRequestError.isEmpty() )
            {
                final String errorMsg = "incoming request from remote oauth server is indicating an error: " + oauthRequestError;
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_OAUTH_ERROR, errorMsg, "Remote Error: " + oauthRequestError, null );
                LOGGER.error( pwmRequest, () -> errorMsg );
                pwmRequest.respondWithError( errorInformation );
                return;
            }
        }

        // check if user is already authenticated - shouldn't be in nominal usage.
        if ( userIsAuthenticated )
        {
            switch ( oAuthUseCaseCase )
            {
                case Authentication:
                    LOGGER.debug( pwmRequest, () -> "oauth consumer reached, but user is already authenticated; will proceed and verify authcode matches current user identity." );
                    break;

                case ForgottenPassword:
                    final String errorMsg = "oauth consumer reached via " + OAuthUseCase.ForgottenPassword + ", but user is already authenticated";
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_OAUTH_ERROR, errorMsg );
                    pwmRequest.respondWithError( errorInformation );
                    LOGGER.error( pwmRequest, () -> errorMsg );
                    return;

                default:
                    JavaHelper.unhandledSwitchStatement( oAuthUseCaseCase );
            }

        }

        // mark the inprogress flag to false, if we get this far and fail user needs to start over.
        pwmSession.getSessionStateBean().setOauthInProgress( false );

        if ( !oAuthRequestState.isPresent() )
        {
            final String errorMsg = "state parameter is missing from oauth request";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_OAUTH_ERROR, errorMsg );
            LOGGER.error( pwmRequest, () -> errorMsg );
            pwmRequest.respondWithError( errorInformation );
            return;
        }

        final OAuthState oauthState = oAuthRequestState.get().getOAuthState();
        final OAuthSettings oAuthSettings = makeOAuthSettings( pwmRequest, oauthState );
        final OAuthMachine oAuthMachine = new OAuthMachine( pwmRequest.getLabel(), oAuthSettings );

        // make sure request was initiated in users current session
        if ( !oAuthRequestState.get().isSessionMatch() )
        {
            try
            {
                switch ( oAuthUseCaseCase )
                {
                    case Authentication:
                        LOGGER.debug( pwmRequest, () -> "oauth consumer reached but response is not for a request issued during the current session,"
                                + " will redirect back to oauth server for verification update" );
                        final String nextURL = oauthState.getNextUrl();
                        oAuthMachine.redirectUserToOAuthServer( pwmRequest, nextURL, null, null );
                        return;

                    case ForgottenPassword:
                        LOGGER.debug( pwmRequest, () -> "oauth consumer reached but response is not for a request issued during the current session,"
                                + " will redirect back to forgotten password servlet" );
                        pwmRequest.sendRedirect( PwmServletDefinition.ForgottenPassword );
                        return;

                    default:
                        JavaHelper.unhandledSwitchStatement( oAuthUseCaseCase );
                }
            }
            catch ( final PwmUnrecoverableException e )
            {
                final String errorMsg = "unexpected error redirecting user to oauth page: " + e.toString();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_OAUTH_ERROR, errorMsg );
                setLastError( pwmRequest, errorInformation );
                LOGGER.error( () -> errorInformation.toDebugStr() );
            }
        }

        final String requestCodeStr = pwmRequest.readParameterAsString( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_CODE ) );
        LOGGER.trace( pwmRequest, () -> "received code from oauth server: " + requestCodeStr );

        final OAuthResolveResults resolveResults;
        try
        {
            resolveResults = oAuthMachine.makeOAuthResolveRequest( pwmRequest, requestCodeStr );
        }
        catch ( final PwmException e )
        {
            final String errorMsg = "unexpected error communicating with oauth server: " + e.toString();
            final ErrorInformation errorInformation = new ErrorInformation( e.getError(), errorMsg );
            setLastError( pwmRequest, errorInformation );
            LOGGER.error( () -> errorInformation.toDebugStr() );
            return;
        }

        if ( resolveResults == null || resolveResults.getAccessToken() == null || resolveResults.getAccessToken().isEmpty() )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_OAUTH_ERROR,
                    "browser redirect from oauth server did not include an access token" );
            LOGGER.error( pwmRequest, errorInformation );
            pwmRequest.respondWithError( errorInformation );
            return;
        }

        /*
        if ( resolveResults.getExpiresSeconds() > 0 )
        {
            if ( resolveResults.getRefreshToken() == null || resolveResults.getRefreshToken().isEmpty() )
            {
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_OAUTH_ERROR,
                        "oauth server gave expiration for access token, but did not provide a refresh token" );
                LOGGER.error( pwmRequest, errorInformation );
                pwmRequest.respondWithError( errorInformation );
                return;
            }
        }
        */

        final String oauthSuppliedUsername = oAuthMachine.makeOAuthGetUserInfoRequest( pwmRequest, resolveResults.getAccessToken() );

        if ( oAuthUseCaseCase == OAuthUseCase.ForgottenPassword )
        {
            redirectToForgottenPasswordServlet( pwmRequest, oauthSuppliedUsername );
            return;
        }

        if ( userIsAuthenticated )
        {
            try
            {
                final UserSearchEngine userSearchEngine = pwmApplication.getUserSearchEngine();
                final UserIdentity resolvedIdentity = userSearchEngine.resolveUsername(
                        oauthSuppliedUsername,
                        null,
                        null,
                        pwmRequest.getLabel()
                );
                if ( resolvedIdentity != null && resolvedIdentity.canonicalEquals( pwmSession.getUserInfo().getUserIdentity(), pwmApplication ) )
                {
                    LOGGER.debug( pwmRequest, () -> "verified incoming oauth code for already authenticated session does resolve to same as logged in user" );
                }
                else
                {
                    final String errorMsg = "incoming oauth code for already authenticated session does not resolve to same as logged in user ";
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_OAUTH_ERROR, errorMsg );
                    LOGGER.error( pwmRequest, () -> errorMsg );
                    pwmRequest.respondWithError( errorInformation );
                    pwmSession.unauthenticateUser( pwmRequest );
                    return;
                }
            }
            catch ( final PwmOperationalException e )
            {
                final String errorMsg = "error while examining incoming oauth code for already authenticated session: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_OAUTH_ERROR, errorMsg );
                LOGGER.error( pwmRequest, () -> errorMsg );
                pwmRequest.respondWithError( errorInformation );
                return;
            }
        }

        try
        {
            if ( !userIsAuthenticated )
            {
                final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator( pwmApplication, pwmRequest, PwmAuthenticationSource.OAUTH );
                sessionAuthenticator.authUserWithUnknownPassword( oauthSuppliedUsername, AuthenticationType.AUTH_WITHOUT_PASSWORD );
            }

            // recycle the session to prevent session fixation attack.
            pwmRequest.getPwmSession().getSessionStateBean().setSessionIdRecycleNeeded( true );

            // forward to nextUrl
            final String nextUrl = oauthState.getNextUrl();
            LOGGER.debug( pwmRequest, () -> "oauth authentication completed, redirecting to originally requested URL: " + nextUrl );
            pwmRequest.sendRedirect( nextUrl );
        }
        catch ( final PwmException e )
        {
            LOGGER.error( pwmRequest, () -> "error during OAuth authentication attempt: " + e.getMessage() );
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_OAUTH_ERROR, e.getMessage() );
            pwmRequest.respondWithError( errorInformation );
            return;
        }

        LOGGER.trace( pwmRequest, () -> "OAuth login sequence successfully completed" );
    }

    private static OAuthSettings makeOAuthSettings( final PwmRequest pwmRequest, final OAuthState oAuthState ) throws IOException, ServletException, PwmUnrecoverableException
    {
        final OAuthUseCase oAuthUseCase = oAuthState.getUseCase();
        switch ( oAuthUseCase )
        {
            case Authentication:
                return OAuthSettings.forSSOAuthentication( pwmRequest.getConfig() );

            case ForgottenPassword:
                final String profileId = oAuthState.getForgottenProfileId();
                final ForgottenPasswordProfile profile = pwmRequest.getConfig().getForgottenPasswordProfiles().get( profileId );
                return OAuthSettings.forForgottenPassword( profile );

            default:
                JavaHelper.unhandledSwitchStatement( oAuthUseCase );

        }

        final String errorMsg = "unable to calculate oauth settings for incoming request state";
        final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_OAUTH_ERROR, errorMsg );
        LOGGER.error( pwmRequest, () -> errorMsg );
        throw new PwmUnrecoverableException( errorInformation );
    }

    private void redirectToForgottenPasswordServlet( final PwmRequest pwmRequest, final String oauthSuppliedUsername ) throws IOException, PwmUnrecoverableException
    {
        final OAuthForgottenPasswordResults results = new OAuthForgottenPasswordResults( true, oauthSuppliedUsername );
        final String encryptedResults = pwmRequest.getPwmApplication().getSecureService().encryptObjectToString( results );

        final Map<String, String> httpParams = new HashMap<>();
        httpParams.put( PwmConstants.PARAM_RECOVERY_OAUTH_RESULT, encryptedResults );
        httpParams.put( PwmConstants.PARAM_ACTION_REQUEST, ForgottenPasswordServlet.ForgottenPasswordAction.oauthReturn.toString() );

        final String nextUrl = pwmRequest.getContextPath() + PwmServletDefinition.ForgottenPassword.servletUrl();
        final String redirectUrl = PwmURL.appendAndEncodeUrlParameters( nextUrl, httpParams );
        LOGGER.debug( pwmRequest, () -> "forgotten password oauth sequence complete, redirecting to forgotten password with result data: " + JsonUtil.serialize( results ) );
        pwmRequest.sendRedirect( redirectUrl );
    }
}
