/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2023 The PWM Project
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

import org.apache.http.HttpStatus;
import password.pwm.AppProperty;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.SessionLabel;
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
import password.pwm.svc.httpclient.PwmHttpClient;
import password.pwm.svc.httpclient.PwmHttpClientConfiguration;
import password.pwm.svc.httpclient.PwmHttpClientRequest;
import password.pwm.svc.httpclient.PwmHttpClientResponse;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OAuthMachine
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( OAuthMachine.class );

    private static final long DEFAULT_MAX_CLOCK_SKEW_SECONDS = 60;

    private final SessionLabel sessionLabel;
    private final OAuthSettings settings;

    public OAuthMachine(
            final SessionLabel sessionLabel,
            final OAuthSettings settings
    )
    {
        this.sessionLabel = sessionLabel;
        this.settings = settings;
    }

    static Optional<OAuthRequestState> readOAuthRequestState(
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
                LOGGER.trace( pwmRequest, () -> "read state while parsing oauth consumer request with match=" + sessionMatch + ", " + JsonUtil.serialize( oAuthState ) );
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

        LOGGER.trace( sessionLabel, () -> "preparing to redirect user to oauth authentication service, setting nextUrl to " + nextUrl );
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

        final String scope = figureScope( config );
        if ( !StringUtil.isEmpty( scope ) )
        {
            urlParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_SCOPE ), scope );
        }

        if ( userIdentity != null )
        {
            final String parametersValue = figureUsernameGrantParam( pwmRequest, userIdentity );
            if ( !StringUtil.isEmpty( parametersValue ) )
            {
                urlParams.put( "parameters", parametersValue );
            }

            final String loginHintValue = figureLoginHintValue( pwmRequest, userIdentity );
            if ( !StringUtil.isEmpty( loginHintValue ) )
            {
                urlParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_LOGIN_HINT ), loginHintValue );
            }
        }

        final String redirectUrl = PwmURL.appendAndEncodeUrlParameters( settings.getLoginURL(), urlParams );

        try
        {
            pwmRequest.sendRedirect( redirectUrl );
            pwmRequest.getPwmSession().getSessionStateBean().setOauthInProgress( true );
            LOGGER.debug( sessionLabel, () -> "redirecting user to oauth id server, url: " + redirectUrl );
        }
        catch ( final PwmUnrecoverableException e )
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
        final OAuthClientAuthMethod clientAuthMethod = settings.getEffectiveClientAuthMethod();

        final Map<String, String> requestParams = new HashMap<>();
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_CODE ), requestCode );
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_GRANT_TYPE ), grantType );
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_REDIRECT_URI ), redirectUri );

        if ( clientAuthMethod.sendCredentialsInBody() )
        {
            requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_CLIENT_ID ), settings.getClientID() );
            requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_CLIENT_SECRET ), settings.getSecret().getStringValue() );
        }

        final PwmHttpClientResponse restResults = makeHttpRequest( pwmRequest, "oauth code resolver", settings, requestUrl,
                requestParams, null, clientAuthMethod.sendCredentialsInHeader() );

        final OAuthResolveResults results = resolveResultsFromResponseBody( pwmRequest, restResults.getBody() );

        LOGGER.trace( sessionLabel, () -> "successfully received access token" );

        return results;
    }

    private OAuthResolveResults resolveResultsFromResponseBody(
            final PwmRequest pwmRequest,
            final String resolveResponseBodyStr
    )
    {
        final Configuration config = pwmRequest.getConfig();
        final String oauthExpiresParam = config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_EXPIRES );
        final String oauthAccessTokenParam = config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_ACCESS_TOKEN );
        final String refreshTokenParam = config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_REFRESH_TOKEN );
        final String idTokenParam = config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_ID_TOKEN );

        final long expireSeconds = JavaHelper.silentParseLong( readAttributeFromBodyMap( resolveResponseBodyStr, oauthExpiresParam ), 0 );
        final String accessToken = readAttributeFromBodyMap( resolveResponseBodyStr, oauthAccessTokenParam );
        final String refreshToken = readAttributeFromBodyMap( resolveResponseBodyStr, refreshTokenParam );
        final String idToken = readAttributeFromBodyMap( resolveResponseBodyStr, idTokenParam );

        return OAuthResolveResults.builder()
                .accessToken( accessToken )
                .refreshToken( refreshToken  )
                .expiresSeconds( expireSeconds )
                .idToken( idToken )
                .build();
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

        final OAuthClientAuthMethod clientAuthMethod = settings.getEffectiveClientAuthMethod();

        final Map<String, String> requestParams = new HashMap<>();
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_REFRESH_TOKEN ), refreshCode );
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_GRANT_TYPE ), grantType );

        if ( clientAuthMethod.requiresCredentialsInBody() )
        {
            requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_CLIENT_ID ), settings.getClientID() );
            requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_CLIENT_SECRET ), settings.getSecret().getStringValue() );
        }

        final PwmHttpClientResponse restResults = makeHttpRequest( pwmRequest, "OAuth refresh resolver", settings, requestUrl,
                requestParams, null, clientAuthMethod.sendCredentialsInHeader() );

        return resolveResultsFromResponseBody( pwmRequest, restResults.getBody() );
    }

    /**
     * Resolves the user name PWM will authenticate locally.  When a user name claim is
     * configured the value is read from the OIDC <code>id_token</code> already returned by the token
     * endpoint; otherwise the remote profile/userinfo web service is called as before.
     */
    String resolveUsername(
            final PwmRequest pwmRequest,
            final OAuthResolveResults resolveResults
    )
            throws PwmUnrecoverableException
    {
        if ( !settings.usernameClaimIsConfigured() )
        {
            return makeOAuthGetUserInfoRequest( pwmRequest, resolveResults.getAccessToken() );
        }

        return readUsernameFromIdToken( pwmRequest, resolveResults.getIdToken() );
    }

    private String readUsernameFromIdToken(
            final PwmRequest pwmRequest,
            final String idToken
    )
            throws PwmUnrecoverableException
    {
        final String claimNames = settings.getUsernameClaim();

        final long maxClockSkewSeconds = JavaHelper.silentParseLong(
                pwmRequest.getConfig().readAppProperty( AppProperty.OAUTH_ID_TOKEN_MAX_CLOCK_SKEW ),
                DEFAULT_MAX_CLOCK_SKEW_SECONDS );

        final String payloadJson = OAuthIdTokenReader.readValidatedPayload( idToken, settings.getClientID(), maxClockSkewSeconds );

        final String oauthSuppliedUsername = readAttributeFromBodyMap( payloadJson, claimNames );

        if ( StringUtil.isEmpty( oauthSuppliedUsername ) )
        {
            final String msg = "id_token returned by oauth server does not contain a value for the configured user name claim '" + claimNames + "'";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_OAUTH_ERROR, msg );
            LOGGER.error( sessionLabel, errorInformation );
            throw new PwmUnrecoverableException( errorInformation );
        }

        LOGGER.debug( sessionLabel, () -> "received user login id value from oauth id_token claim: " + oauthSuppliedUsername );

        return oauthSuppliedUsername;
    }

    String makeOAuthGetUserInfoRequest(
            final PwmRequest pwmRequest,
            final String accessToken
    )
            throws PwmUnrecoverableException
    {
        final PwmHttpClientResponse restResults;
        {
            final Configuration config = pwmRequest.getConfig();
            final String requestUrl = settings.getAttributesUrl();
            final Map<String, String> requestParams = new HashMap<>();
            requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_ACCESS_TOKEN ), accessToken );
            requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_ATTRIBUTES ), settings.getDnAttributeName() );
            restResults = makeHttpRequest( pwmRequest, "OAuth userinfo", settings, requestUrl, requestParams, accessToken, false );
        }

        final String resultBody = restResults.getBody();

        LOGGER.trace( sessionLabel, () -> "received attribute values from OAuth IdP for attributes: " );

        final String oauthSuppliedUsername = readAttributeFromBodyMap( resultBody, settings.getDnAttributeName() );

        if ( StringUtil.isEmpty( oauthSuppliedUsername ) )
        {
            final String msg = "OAuth server did not respond with an username value for configured attribute '" + settings.getDnAttributeName() + "'";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_OAUTH_ERROR, msg );
            LOGGER.error( sessionLabel, errorInformation );
            throw new PwmUnrecoverableException( errorInformation );
        }

        LOGGER.debug( sessionLabel, () -> "received user login id value from OAuth server: " + oauthSuppliedUsername );

        return oauthSuppliedUsername;
    }

    private static PwmHttpClientResponse makeHttpRequest(
            final PwmRequest pwmRequest,
            final String debugText,
            final OAuthSettings settings,
            final String requestUrl,
            final Map<String, String> requestParams,
            final String accessToken,
            final boolean clientAuthInHeader
    )
            throws PwmUnrecoverableException
    {
        final String requestBody = PwmURL.encodeParametersToFormBody( requestParams );
        final List<X509Certificate> certs = settings.getCertificates();

        final PwmHttpClientRequest pwmHttpClientRequest;
        {
            final Map<String, String> headers = new HashMap<>( );
            if ( !StringUtil.isEmpty(  accessToken ) )
            {
                headers.put( HttpHeader.Authorization.getHttpName(),
                        "Bearer " + accessToken );
            }
            else if ( clientAuthInHeader )
            {
                headers.put( HttpHeader.Authorization.getHttpName(),
                        new BasicAuthInfo( settings.getClientID(), settings.getSecret() ).toAuthHeader() );
            }
            headers.put( HttpHeader.ContentType.getHttpName(), HttpContentType.form.getHeaderValueWithEncoding() );

            pwmHttpClientRequest = PwmHttpClientRequest.builder()
                    .method( HttpMethod.POST )
                    .url( requestUrl )
                    .body( requestBody )
                    .headers( headers )
                    .build();
        }

        final PwmHttpClientResponse pwmHttpClientResponse;
        try
        {
            final PwmHttpClientConfiguration config = PwmHttpClientConfiguration.builder()
                    .trustManagerType( PwmHttpClientConfiguration.TrustManagerType.configuredCertificates )
                    .certificates( JavaHelper.isEmpty( certs ) ? null : certs )
                    .maskBodyDebugOutput( true )
                    .build();
            final PwmHttpClient pwmHttpClient = pwmRequest.getPwmApplication().getHttpClientService().getPwmHttpClient( config );
            pwmHttpClientResponse = pwmHttpClient.makeRequest( pwmHttpClientRequest, pwmRequest.getLabel() );
        }
        catch ( final PwmException e )
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
                catch ( final URISyntaxException e )
                {
                    throw new IllegalStateException( "unable to parse inbound request uri while generating oauth redirect: " + e.getMessage() );
                }
            }
        }

        LOGGER.trace( pwmRequest, () -> "calculated oauth self end point URI as '" + redirectUri + "' using method " + debugSource );
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

        LOGGER.trace( sessionLabel, () -> "oauth access token has expired, attempting to refresh" );

        try
        {
            final OAuthResolveResults resolveResults = makeOAuthRefreshRequest( pwmRequest,
                    loginInfoBean.getOauthRefToken() );
            if ( resolveResults != null )
            {
                if ( resolveResults.getExpiresSeconds() > 0 )
                {
                    final Instant accessTokenExpirationDate = Instant.ofEpochMilli( System.currentTimeMillis() + 1000 * resolveResults.getExpiresSeconds() );
                    LOGGER.trace( sessionLabel, () -> "noted oauth access token expiration at timestamp " + JavaHelper.toIsoDate( accessTokenExpirationDate ) );
                    loginInfoBean.setOauthExp( accessTokenExpirationDate );
                    loginInfoBean.setOauthRefToken( resolveResults.getRefreshToken() );
                    return false;
                }
            }
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.error( sessionLabel, () -> "error while processing oauth token refresh: " + e.getMessage() );
        }
        LOGGER.error( sessionLabel, () -> "unable to refresh oauth token for user, unauthenticated session" );
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

        LOGGER.trace( sessionLabel, () -> "issuing oauth state id="
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

        final MacroRequest macroRequest = MacroRequest.forUser( pwmRequest, userIdentity );
        final String username = macroRequest.expandMacros( macroText );
        LOGGER.debug( sessionLabel, () -> "calculated username value for user as: " + username );

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

        LOGGER.debug( sessionLabel, () -> "preparing to send username to OAuth /sign endpoint for future injection to /grant redirect" );
        final PwmHttpClientResponse restResults = makeHttpRequest( pwmRequest, "OAuth pre-inject username signing service",
                settings, signUrl, requestPayload, null, true );

        final String resultBody = restResults.getBody();
        final Map<String, String> resultBodyMap = JsonUtil.deserializeStringMap( resultBody );
        final String data = resultBodyMap.get( "data" );
        LOGGER.debug( sessionLabel, () -> "oauth /sign endpoint returned signed username data: " + data );
        return data;
    }

    /**
     * Computes the value to send as the standard OIDC <code>login_hint</code> query parameter
     * on the authorize redirect, so the remote OAuth/OIDC server can pre-fill the username
     * field and avoid prompting the user a second time (issue #723).  Returns {@code null}
     * if no macro is configured, the macro expands to empty, or no user identity is known.
     */
    private String figureLoginHintValue(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        if ( userIdentity == null )
        {
            return null;
        }

        final String macroText = settings.getLoginHintValue();
        if ( StringUtil.isEmpty( macroText ) )
        {
            return null;
        }

        final MacroRequest macroRequest = MacroRequest.forUser( pwmRequest, userIdentity );
        final String expanded = macroRequest.expandMacros( macroText );
        LOGGER.debug( sessionLabel, () -> "calculated login_hint value for user as: " + expanded );
        return expanded;
    }

    /**
     * Returns the scope to send on the authorize redirect.  An authorization server only issues an
     * <code>id_token</code> when the <code>openid</code> scope is requested, so when the user name is
     * read from an id_token claim that scope is added if the administrator has not already included it.
     */
    private String figureScope( final Configuration config )
    {
        final String configuredScope = settings.getScope();

        if ( !settings.usernameClaimIsConfigured() )
        {
            return configuredScope;
        }

        final String requiredScope = config.readAppProperty( AppProperty.OAUTH_ID_TOKEN_REQUIRED_SCOPE );
        if ( StringUtil.isEmpty( requiredScope ) )
        {
            return configuredScope;
        }

        if ( StringUtil.isEmpty( configuredScope ) )
        {
            return requiredScope;
        }

        final List<String> scopes = new ArrayList<>( Arrays.asList( configuredScope.trim().split( "\\s+" ) ) );
        if ( scopes.contains( requiredScope ) )
        {
            return configuredScope;
        }

        scopes.add( 0, requiredScope );
        final String effectiveScope = String.join( " ", scopes );
        LOGGER.debug( sessionLabel, () -> "added '" + requiredScope
                + "' to the configured oauth scope because a user name claim is configured, effective scope: " + effectiveScope );
        return effectiveScope;
    }

    public String readAttributeFromBodyMap(
            final String bodyString,
            final String attributeNames
    )
    {
        try
        {
            final Map<String, Object> bodyMap = JsonUtil.deserializeMap( bodyString );
            final List<String> attributeValues = StringUtil.splitAndTrim( attributeNames, "," );

            for ( final String attribute : attributeValues )
            {
                final Object objValue = bodyMap.get( attribute );
                if ( objValue != null )
                {
                    if ( objValue instanceof Double && JavaHelper.doubleContainsLongValue( (Double) objValue ) )
                    {
                        final long longValue = ( ( Double ) objValue ).longValue();
                        return Long.toString( longValue );
                    }

                    final Object singleObjValue;
                    if ( objValue instanceof Collection )
                    {
                        if ( ( ( Collection ) objValue ).isEmpty() )
                        {
                            return null;
                        }

                        singleObjValue = ( ( Collection ) objValue ).iterator().next();
                    }
                    else
                    {
                        singleObjValue = objValue;
                    }

                    final String strValue = singleObjValue.toString();
                    if ( !StringUtil.isEmpty( strValue ) )
                    {
                        return strValue;
                    }
                }
            }
        }
        catch ( final Exception e )
        {
            LOGGER.debug( sessionLabel, () -> "unexpected error parsing json response: " + e.getMessage() );
        }

        return null;
    }
}
