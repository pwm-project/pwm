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

package password.pwm.http.servlet.oauth;

import org.apache.http.HttpStatus;
import password.pwm.AppProperty;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.http.client.PwmHttpClientConfiguration;
import password.pwm.http.client.PwmHttpClientRequest;
import password.pwm.http.client.PwmHttpClientResponse;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OAuthMachine
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( OAuthMachine.class );

    private final OAuthSettings settings;

    public OAuthMachine( final OAuthSettings settings )
    {
        this.settings = settings;
    }

    public static Optional<OAuthRequestState> readOAuthRequestState(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final String requestStateStr = pwmRequest.readParameterAsString( pwmRequest.getConfig().readAppProperty( AppProperty.HTTP_PARAM_OAUTH_STATE ) );
        if ( requestStateStr != null )
        {
            final String stateJson = pwmRequest.getPwmApplication().getSecureService().decryptStringValue( requestStateStr );
            final OAuthState oAuthState = JsonUtil.deserialize( stateJson, OAuthState.class );
            if ( oAuthState != null )
            {
                final boolean sessionMatch = oAuthState.getSessionID().equals( pwmRequest.getPwmSession().getSessionStateBean().getSessionVerificationKey() );
                LOGGER.trace( pwmRequest, "read state while parsing oauth consumer request with match=" + sessionMatch + ", " + JsonUtil.serialize( oAuthState ) );
                return Optional.of( new OAuthRequestState( oAuthState, sessionMatch ) );
            }
        }


        return Optional.empty();
    }

    public void redirectUserToOAuthServer(
            final PwmRequest pwmRequest,
            final String nextUrl,
            final UserIdentity userIdentity,
            final String forgottenPasswordProfile
    )
            throws PwmUnrecoverableException, IOException
    {

        LOGGER.trace( pwmRequest, "preparing to redirect user to oauth authentication service, setting nextUrl to " + nextUrl );
        pwmRequest.getPwmSession().getSessionStateBean().setOauthInProgress( true );

        final Configuration config = pwmRequest.getConfig();
        final String state = makeStateStringForRequest( pwmRequest, nextUrl, forgottenPasswordProfile );
        final String redirectUri = figureOauthSelfEndPointUrl( pwmRequest );
        final String code = config.readAppProperty( AppProperty.OAUTH_ID_REQUEST_TYPE );

        final Map<String, String> urlParams = new LinkedHashMap<>();
        urlParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_CLIENT_ID ), settings.getClientID() );
        urlParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_RESPONSE_TYPE ), code );
        urlParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_STATE ), state );
        urlParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_REDIRECT_URI ), redirectUri );

        if ( userIdentity != null )
        {
            final String parametersValue = figureUsernameGrantParam( pwmRequest, userIdentity );
            if ( !StringUtil.isEmpty( parametersValue ) )
            {
                urlParams.put( "parameters", parametersValue );
            }
        }

        final String redirectUrl = PwmURL.appendAndEncodeUrlParameters( settings.getLoginURL(), urlParams );

        try
        {
            pwmRequest.sendRedirect( redirectUrl );
            pwmRequest.getPwmSession().getSessionStateBean().setOauthInProgress( true );
            LOGGER.debug( pwmRequest, "redirecting user to oauth id server, url: " + redirectUrl );
        }
        catch ( PwmUnrecoverableException e )
        {
            final String errorMsg = "unexpected error redirecting user to oauth page: " + e.toString();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    OAuthResolveResults makeOAuthResolveRequest(
            final PwmRequest pwmRequest,
            final String requestCode
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        final String requestUrl = settings.getCodeResolveUrl();
        final String grantType = config.readAppProperty( AppProperty.OAUTH_ID_ACCESS_GRANT_TYPE );
        final String redirectUri = figureOauthSelfEndPointUrl( pwmRequest );
        final String clientID = settings.getClientID();

        final Map<String, String> requestParams = new HashMap<>();
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_CODE ), requestCode );
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_GRANT_TYPE ), grantType );
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_REDIRECT_URI ), redirectUri );
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_CLIENT_ID ), clientID );

        final PwmHttpClientResponse restResults = makeHttpRequest( pwmRequest, "oauth code resolver", settings, requestUrl, requestParams );

        final String resolveResponseBodyStr = restResults.getBody();

        final Map<String, String> resolveResultValues = JsonUtil.deserializeStringMap( resolveResponseBodyStr );
        final OAuthResolveResults oAuthResolveResults = new OAuthResolveResults();

        oAuthResolveResults.setAccessToken( resolveResultValues.get( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_ACCESS_TOKEN ) ) );
        oAuthResolveResults.setRefreshToken( resolveResultValues.get( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_REFRESH_TOKEN ) ) );
        oAuthResolveResults.setExpiresSeconds( 0 );
        try
        {
            oAuthResolveResults.setExpiresSeconds( Integer.parseInt( resolveResultValues.get( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_EXPIRES ) ) ) );
        }
        catch ( Exception e )
        {
            LOGGER.warn( pwmRequest, "error parsing oauth expires value in code resolver response from server at " + requestUrl + ", error: " + e.getMessage() );
        }

        return oAuthResolveResults;
    }


    private OAuthResolveResults makeOAuthRefreshRequest(
            final PwmRequest pwmRequest,
            final String refreshCode
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        final String requestUrl = settings.getCodeResolveUrl();
        final String grantType = config.readAppProperty( AppProperty.OAUTH_ID_REFRESH_GRANT_TYPE );

        final Map<String, String> requestParams = new HashMap<>();
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_REFRESH_TOKEN ), refreshCode );
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_GRANT_TYPE ), grantType );

        final PwmHttpClientResponse restResults = makeHttpRequest( pwmRequest, "OAuth refresh resolver", settings, requestUrl, requestParams );

        final String resolveResponseBodyStr = restResults.getBody();

        final Map<String, String> resolveResultValues = JsonUtil.deserializeStringMap( resolveResponseBodyStr );
        final OAuthResolveResults oAuthResolveResults = new OAuthResolveResults();

        oAuthResolveResults.setAccessToken( resolveResultValues.get( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_ACCESS_TOKEN ) ) );
        oAuthResolveResults.setRefreshToken( refreshCode );
        oAuthResolveResults.setExpiresSeconds( 0 );
        try
        {
            oAuthResolveResults.setExpiresSeconds( Integer.parseInt( resolveResultValues.get( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_EXPIRES ) ) ) );
        }
        catch ( Exception e )
        {
            LOGGER.warn( pwmRequest, "error parsing oauth expires value in resolve request: " + e.getMessage() );
        }

        return oAuthResolveResults;
    }

    String makeOAuthGetAttributeRequest(
            final PwmRequest pwmRequest,
            final String accessToken
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        final String requestUrl = settings.getAttributesUrl();
        final Map<String, String> requestParams = new HashMap<>();
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_ACCESS_TOKEN ), accessToken );
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_ATTRIBUTES ), settings.getDnAttributeName() );

        final PwmHttpClientResponse restResults = makeHttpRequest( pwmRequest, "OAuth getattribute", settings, requestUrl, requestParams );

        return restResults.getBody();
    }

    private static PwmHttpClientResponse makeHttpRequest(
            final PwmRequest pwmRequest,
            final String debugText,
            final OAuthSettings settings,
            final String requestUrl,
            final Map<String, String> requestParams
    )
            throws PwmUnrecoverableException
    {
        final String requestBody = PwmURL.encodeParametersToFormBody( requestParams );
        final List<X509Certificate> certs = settings.getCertificates();

        final PwmHttpClientRequest pwmHttpClientRequest;
        {
            final Map<String, String> headers = new HashMap<>( );
            headers.put( HttpHeader.Authorization.getHttpName(),
                    new BasicAuthInfo( settings.getClientID(), settings.getSecret() ).toAuthHeader() );
            headers.put( HttpHeader.ContentType.getHttpName(), HttpContentType.form.getHeaderValue() );

            pwmHttpClientRequest = new PwmHttpClientRequest( HttpMethod.POST, requestUrl, requestBody, headers );
        }

        final PwmHttpClientResponse pwmHttpClientResponse;
        try
        {
            final PwmHttpClientConfiguration config = PwmHttpClientConfiguration.builder()
                    .certificates( JavaHelper.isEmpty( certs ) ? null : certs )
                    .maskBodyDebugOutput( true )
                    .build();
            final PwmHttpClient pwmHttpClient = new PwmHttpClient( pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel(), config );
            pwmHttpClientResponse = pwmHttpClient.makeRequest( pwmHttpClientRequest );
        }
        catch ( PwmException e )
        {
            final String errorMsg = "error during " + debugText + " http request to oauth server, remote error: " + e.getErrorInformation().toDebugStr();
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_OAUTH_ERROR, errorMsg ) );
        }


        if ( pwmHttpClientResponse.getStatusCode() != HttpStatus.SC_OK )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_OAUTH_ERROR,
                    "unexpected HTTP status code (" + pwmHttpClientResponse.getStatusCode() + ") during " + debugText + " request to " + requestUrl
            ) );
        }

        return pwmHttpClientResponse;
    }

    private static String figureOauthSelfEndPointUrl( final PwmRequest pwmRequest )
    {
        final String debugSource;
        final String redirectUri;

        {
            final String returnUrlOverride = pwmRequest.getConfig().readAppProperty( AppProperty.OAUTH_RETURN_URL_OVERRIDE );
            final String siteURL = pwmRequest.getConfig().readSettingAsString( PwmSetting.PWM_SITE_URL );
            if ( returnUrlOverride != null && !returnUrlOverride.trim().isEmpty() )
            {
                debugSource = "AppProperty(\"" + AppProperty.OAUTH_RETURN_URL_OVERRIDE.getKey() + "\")";
                redirectUri = returnUrlOverride
                        + PwmServletDefinition.OAuthConsumer.servletUrl();
            }
            else if ( siteURL != null && !siteURL.trim().isEmpty() )
            {
                debugSource = "SiteURL Setting";
                redirectUri = siteURL
                        + PwmServletDefinition.OAuthConsumer.servletUrl();
            }
            else
            {
                debugSource = "Input Request URL";
                final String inputURI = pwmRequest.getHttpServletRequest().getRequestURL().toString();
                try
                {
                    final URI requestUri = new URI( inputURI );
                    final int port = requestUri.getPort();
                    redirectUri = requestUri.getScheme() + "://" + requestUri.getHost()
                            + ( port > 0 && port != 80 && port != 443 ? ":" + requestUri.getPort() : "" )
                            + pwmRequest.getContextPath()
                            + PwmServletDefinition.OAuthConsumer.servletUrl();
                }
                catch ( URISyntaxException e )
                {
                    throw new IllegalStateException( "unable to parse inbound request uri while generating oauth redirect: " + e.getMessage() );
                }
            }
        }

        LOGGER.trace( "calculated oauth self end point URI as '" + redirectUri + "' using method " + debugSource );
        return redirectUri;
    }

    public boolean checkOAuthExpiration(
            final PwmRequest pwmRequest
    )
    {
        if ( !Boolean.parseBoolean( pwmRequest.getConfig().readAppProperty( AppProperty.OAUTH_ENABLE_TOKEN_REFRESH ) ) )
        {
            return false;
        }

        final LoginInfoBean loginInfoBean = pwmRequest.getPwmSession().getLoginInfoBean();
        final Instant expirationDate = loginInfoBean.getOauthExp();

        if ( expirationDate == null || Instant.now().isBefore( expirationDate ) )
        {
            //not expired
            return false;
        }

        LOGGER.trace( pwmRequest, "oauth access token has expired, attempting to refresh" );

        try
        {
            final OAuthResolveResults resolveResults = makeOAuthRefreshRequest( pwmRequest,
                    loginInfoBean.getOauthRefToken() );
            if ( resolveResults != null )
            {
                if ( resolveResults.getExpiresSeconds() > 0 )
                {
                    final Instant accessTokenExpirationDate = Instant.ofEpochMilli( System.currentTimeMillis() + 1000 * resolveResults.getExpiresSeconds() );
                    LOGGER.trace( pwmRequest, "noted oauth access token expiration at timestamp " + JavaHelper.toIsoDate( accessTokenExpirationDate ) );
                    loginInfoBean.setOauthExp( accessTokenExpirationDate );
                    loginInfoBean.setOauthRefToken( resolveResults.getRefreshToken() );
                    return false;
                }
            }
        }
        catch ( PwmUnrecoverableException e )
        {
            LOGGER.error( pwmRequest, "error while processing oauth token refresh: " + e.getMessage() );
        }
        LOGGER.error( pwmRequest, "unable to refresh oauth token for user, unauthenticated session" );
        pwmRequest.getPwmSession().unauthenticateUser( pwmRequest );
        return true;
    }


    private String makeStateStringForRequest(
            final PwmRequest pwmRequest,
            final String nextUrl,
            final String forgottenPasswordProfileID
    )
            throws PwmUnrecoverableException
    {
        final OAuthUseCase oAuthUseCase = settings.getUse();

        final String sessionId = pwmRequest.getPwmSession().getSessionStateBean().getSessionVerificationKey();

        final OAuthState oAuthState;
        switch ( oAuthUseCase )
        {
            case Authentication:
                oAuthState = OAuthState.newSSOAuthenticationState( sessionId, nextUrl );
                break;

            case ForgottenPassword:
                oAuthState = OAuthState.newForgottenPasswordState( sessionId, forgottenPasswordProfileID );
                break;

            default:
                throw new IllegalStateException( "unexpected oAuthUseCase: " + oAuthUseCase );
        }

        LOGGER.trace( pwmRequest, "issuing oauth state id="
                + oAuthState.getStateID() + " with the next destination URL set to " + oAuthState.getNextUrl() );


        final String jsonValue = JsonUtil.serialize( oAuthState );
        return pwmRequest.getPwmApplication().getSecureService().encryptToString( jsonValue );
    }

    private String figureUsernameGrantParam(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        if ( userIdentity == null )
        {
            return null;
        }

        final String macroText = settings.getUsernameSendValue();
        if ( StringUtil.isEmpty( macroText ) )
        {
            return null;
        }

        final MacroMachine macroMachine = MacroMachine.forUser( pwmRequest, userIdentity );
        final String username = macroMachine.expandMacros( macroText );
        LOGGER.debug( pwmRequest, "calculated username value for user as: " + username );

        final String grantUrl = settings.getLoginURL();
        final String signUrl = grantUrl.replace( "/grant", "/sign" );

        final Map<String, String> requestPayload;
        {
            final Map<String, String> dataPayload = new HashMap<>();
            dataPayload.put( "username", username );

            final List<Map<String, String>> listWrapper = new ArrayList<>();
            listWrapper.add( dataPayload );

            requestPayload = new HashMap<>();
            requestPayload.put( "data", JsonUtil.serializeCollection( listWrapper ) );
        }

        LOGGER.debug( pwmRequest, "preparing to send username to OAuth /sign endpoint for future injection to /grant redirect" );
        final PwmHttpClientResponse restResults = makeHttpRequest( pwmRequest, "OAuth pre-inject username signing service", settings, signUrl, requestPayload );

        final String resultBody = restResults.getBody();
        final Map<String, String> resultBodyMap = JsonUtil.deserializeStringMap( resultBody );
        final String data = resultBodyMap.get( "data" );
        LOGGER.debug( pwmRequest, "oauth /sign endpoint returned signed username data: " + data );
        return data;
    }
}
