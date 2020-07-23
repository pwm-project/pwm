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
import password.pwm.util.macro.MacroMachine;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OAuthMachine
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( OAuthMachine.class );

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

        if ( !StringUtil.isEmpty( settings.getScope() ) )
        {
            urlParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_SCOPE ), settings.getScope() );
        }

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
        final String clientID = settings.getClientID();

        final Map<String, String> requestParams = new HashMap<>();
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_CODE ), requestCode );
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_GRANT_TYPE ), grantType );
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_REDIRECT_URI ), redirectUri );
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_CLIENT_ID ), clientID );
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_CLIENT_SECRET ), settings.getSecret().getStringValue() );

        final PwmHttpClientResponse restResults = makeHttpRequest( pwmRequest, "oauth code resolver", settings, requestUrl, requestParams, null );

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

        final long expireSeconds = JavaHelper.silentParseLong( readAttributeFromBodyMap( resolveResponseBodyStr, oauthExpiresParam ), 0 );
        final String accessToken = readAttributeFromBodyMap( resolveResponseBodyStr, oauthAccessTokenParam );
        final String refreshToken = readAttributeFromBodyMap( resolveResponseBodyStr, refreshTokenParam );

        return OAuthResolveResults.builder()
                .accessToken( accessToken )
                .refreshToken( refreshToken  )
                .expiresSeconds( expireSeconds )
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

        final Map<String, String> requestParams = new HashMap<>();
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_REFRESH_TOKEN ), refreshCode );
        requestParams.put( config.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_GRANT_TYPE ), grantType );

        final PwmHttpClientResponse restResults = makeHttpRequest( pwmRequest, "OAuth refresh resolver", settings, requestUrl, requestParams, null );

        return resolveResultsFromResponseBody( pwmRequest, restResults.getBody() );
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
            restResults = makeHttpRequest( pwmRequest, "OAuth userinfo", settings, requestUrl, requestParams, accessToken );
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
            final String accessToken
    )
            throws PwmUnrecoverableException
    {
        final String requestBody = PwmURL.encodeParametersToFormBody( requestParams );
        final List<X509Certificate> certs = settings.getCertificates();

        final PwmHttpClientRequest pwmHttpClientRequest;
        {
            final Map<String, String> headers = new HashMap<>( );
            if ( StringUtil.isEmpty(  accessToken ) )
            {
                headers.put( HttpHeader.Authorization.getHttpName(),
                        new BasicAuthInfo( settings.getClientID(), settings.getSecret() ).toAuthHeader() );
            }
            else
            {
                headers.put( HttpHeader.Authorization.getHttpName(),
                        "Bearer " + accessToken );
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

        final MacroMachine macroMachine = MacroMachine.forUser( pwmRequest, userIdentity );
        final String username = macroMachine.expandMacros( macroText );
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
        final PwmHttpClientResponse restResults = makeHttpRequest( pwmRequest, "OAuth pre-inject username signing service", settings, signUrl, requestPayload, null );

        final String resultBody = restResults.getBody();
        final Map<String, String> resultBodyMap = JsonUtil.deserializeStringMap( resultBody );
        final String data = resultBodyMap.get( "data" );
        LOGGER.debug( sessionLabel, () -> "oauth /sign endpoint returned signed username data: " + data );
        return data;
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
