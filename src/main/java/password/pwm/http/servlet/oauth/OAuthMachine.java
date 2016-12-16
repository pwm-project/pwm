/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.http.client.PwmHttpClientConfiguration;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OAuthMachine {
    private static final PwmLogger LOGGER = PwmLogger.forClass(OAuthMachine.class);

    private final OAuthSettings settings;

    public OAuthMachine(final OAuthSettings settings) {
        this.settings = settings;
    }

    public static Optional<OAuthRequestState> readOAuthRequestState(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final String requestStateStr = pwmRequest.readParameterAsString(pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_PARAM_OAUTH_STATE));
        if (requestStateStr != null) {
            final String stateJson = pwmRequest.getPwmApplication().getSecureService().decryptStringValue(requestStateStr);
            final OAuthState oAuthState = JsonUtil.deserialize(stateJson, OAuthState.class);
            if (oAuthState != null) {
                final boolean sessionMatch = oAuthState.getSessionID().equals(pwmRequest.getPwmSession().getSessionStateBean().getSessionVerificationKey());
                LOGGER.trace(pwmRequest, "read state while parsing oauth consumer request with match=" + sessionMatch + ", " + JsonUtil.serialize(oAuthState));
                return Optional.of(new OAuthRequestState(oAuthState, sessionMatch));
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

        LOGGER.trace(pwmRequest, "preparing to redirect user to oauth authentication service, setting nextUrl to " + nextUrl);
        pwmRequest.getPwmSession().getSessionStateBean().setOauthInProgress(true);

        final Configuration config = pwmRequest.getConfig();
        final String state = makeStateStringForRequest(pwmRequest, nextUrl, forgottenPasswordProfile);
        final String redirectUri = figureOauthSelfEndPointUrl(pwmRequest);
        final String code = config.readAppProperty(AppProperty.OAUTH_ID_REQUEST_TYPE);

        final Map<String,String> urlParams = new LinkedHashMap<>();
        urlParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_CLIENT_ID),settings.getClientID());
        urlParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_RESPONSE_TYPE),code);
        urlParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_STATE),state);
        urlParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_REDIRECT_URI), redirectUri);

        if (userIdentity != null) {
            final String parametersValue = figureUsernameGrantParam(pwmRequest, userIdentity);
            if (!StringUtil.isEmpty(parametersValue)) {
                urlParams.put("parameters", parametersValue);
            }
        }

        final String redirectUrl = PwmURL.appendAndEncodeUrlParameters(settings.getLoginURL(), urlParams);

        try{
            pwmRequest.sendRedirect(redirectUrl);
            pwmRequest.getPwmSession().getSessionStateBean().setOauthInProgress(true);
            LOGGER.debug(pwmRequest,"redirecting user to oauth id server, url: " + redirectUrl);
        } catch (PwmUnrecoverableException e) {
            final String errorMsg = "unexpected error redirecting user to oauth page: " + e.toString();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            pwmRequest.setResponseError(errorInformation);
            LOGGER.error(errorInformation.toDebugStr());
        }
    }

    OAuthResolveResults makeOAuthResolveRequest(
            final PwmRequest pwmRequest,
            final String requestCode
    )
            throws IOException, PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        final String requestUrl = settings.getCodeResolveUrl();
        final String grant_type = config.readAppProperty(AppProperty.OAUTH_ID_ACCESS_GRANT_TYPE);
        final String redirect_uri = figureOauthSelfEndPointUrl(pwmRequest);
        final String clientID = settings.getClientID();

        final Map<String,String> requestParams = new HashMap<>();
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_CODE),requestCode);
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_GRANT_TYPE),grant_type);
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_REDIRECT_URI), redirect_uri);
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_CLIENT_ID), clientID);

        final RestResults restResults = makeHttpRequest(pwmRequest, "oauth code resolver", settings, requestUrl, requestParams);

        final String resolveResponseBodyStr = restResults.getResponseBody();

        final Map<String, String> resolveResultValues = JsonUtil.deserializeStringMap(resolveResponseBodyStr);
        final OAuthResolveResults oAuthResolveResults = new OAuthResolveResults();

        oAuthResolveResults.setAccessToken(resolveResultValues.get(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_ACCESS_TOKEN)));
        oAuthResolveResults.setRefreshToken(resolveResultValues.get(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_REFRESH_TOKEN)));
        oAuthResolveResults.setExpiresSeconds(0);
        try {
            oAuthResolveResults.setExpiresSeconds(Integer.parseInt(resolveResultValues.get(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_EXPIRES))));
        } catch (Exception e) {
            LOGGER.warn(pwmRequest, "error parsing oauth expires value in code resolver response from server at " + requestUrl + ", error: " + e.getMessage());
        }

        return oAuthResolveResults;
    }


    private OAuthResolveResults makeOAuthRefreshRequest(
            final PwmRequest pwmRequest,
            final String refreshCode
    )
            throws IOException, PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        final String requestUrl = settings.getCodeResolveUrl();
        final String grant_type = config.readAppProperty(AppProperty.OAUTH_ID_REFRESH_GRANT_TYPE);

        final Map<String,String> requestParams = new HashMap<>();
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_REFRESH_TOKEN),refreshCode);
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_GRANT_TYPE),grant_type);

        final RestResults restResults = makeHttpRequest(pwmRequest, "OAuth refresh resolver", settings, requestUrl, requestParams);

        final String resolveResponseBodyStr = restResults.getResponseBody();

        final Map<String, String> resolveResultValues = JsonUtil.deserializeStringMap(resolveResponseBodyStr);
        final OAuthResolveResults oAuthResolveResults = new OAuthResolveResults();

        oAuthResolveResults.setAccessToken(resolveResultValues.get(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_ACCESS_TOKEN)));
        oAuthResolveResults.setRefreshToken(refreshCode);
        oAuthResolveResults.setExpiresSeconds(0);
        try {
            oAuthResolveResults.setExpiresSeconds(Integer.parseInt(resolveResultValues.get(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_EXPIRES))));
        } catch (Exception e) {
            LOGGER.warn(pwmRequest, "error parsing oauth expires value in resolve request: " + e.getMessage());
        }

        return oAuthResolveResults;
    }

    String makeOAuthGetAttributeRequest(
            final PwmRequest pwmRequest,
            final String accessToken
    )
            throws IOException, PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        final String requestUrl = settings.getAttributesUrl();
        final Map<String,String> requestParams = new HashMap<>();
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_ACCESS_TOKEN),accessToken);
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_ATTRIBUTES),settings.getDnAttributeName());

        final RestResults restResults = makeHttpRequest(pwmRequest, "OAuth getattribute", settings, requestUrl, requestParams);

        return restResults.getResponseBody();
    }

    private static RestResults makeHttpRequest(
            final PwmRequest pwmRequest,
            final String debugText,
            final OAuthSettings settings,
            final String requestUrl,
            final Map<String,String> requestParams
    )
            throws IOException, PwmUnrecoverableException
    {
        final Date startTime = new Date();
        final String requestBody = PwmURL.appendAndEncodeUrlParameters("", requestParams);
        LOGGER.trace(pwmRequest, "beginning " + debugText + " request to " + requestUrl + ", body: \n" + requestBody);
        final HttpPost httpPost = new HttpPost(requestUrl);
        httpPost.setHeader(PwmConstants.HttpHeader.Authorization.getHttpName(),
                new BasicAuthInfo(settings.getClientID(), settings.getSecret()).toAuthHeader());
        final StringEntity bodyEntity = new StringEntity(requestBody);
        bodyEntity.setContentType(PwmConstants.ContentTypeValue.form.getHeaderValue());
        httpPost.setEntity(bodyEntity);

        final X509Certificate[] certs = settings.getCertificates();

        final HttpResponse httpResponse;
        final String bodyResponse;
        try {
            if (certs == null || certs.length == 0) {
                httpResponse = PwmHttpClient.getHttpClient(pwmRequest.getConfig()).execute(httpPost);
            } else {
                httpResponse = PwmHttpClient.getHttpClient(pwmRequest.getConfig(), new PwmHttpClientConfiguration.Builder().setCertificate(certs).create()).execute(httpPost);
            }
            bodyResponse = EntityUtils.toString(httpResponse.getEntity());
        } catch (PwmException | IOException e) {
            final String errorMsg;
            if (e instanceof PwmException) {
                errorMsg = "error during " + debugText + " http request to oauth server, remote error: " + ((PwmException) e).getErrorInformation().toDebugStr();
            } else {
                errorMsg = "io error during " + debugText + " http request to oauth server: " + e.getMessage();
            }
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_OAUTH_ERROR, errorMsg));
        }

        final StringBuilder debugOutput = new StringBuilder();
        debugOutput.append(debugText).append(" ").append(
                TimeDuration.fromCurrent(startTime).asCompactString()).append(", status: ").append(
                httpResponse.getStatusLine()).append("\n");
        for (final Header responseHeader : httpResponse.getAllHeaders()) {
            debugOutput.append(" response header: ").append(responseHeader.getName()).append(": ").append(
                    responseHeader.getValue()).append("\n");
        }

        debugOutput.append(" body:\n ").append(bodyResponse);
        LOGGER.trace(pwmRequest, debugOutput.toString());

        if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new PwmUnrecoverableException(new ErrorInformation(
                    PwmError.ERROR_OAUTH_ERROR,
                    "unexpected HTTP status code (" + httpResponse.getStatusLine().getStatusCode() + ") during " + debugText + " request to " + requestUrl
            ));
        }

        return new RestResults(httpResponse, bodyResponse);
    }

    public static String figureOauthSelfEndPointUrl(final PwmRequest pwmRequest) {
        final String debugSource;
        final String redirect_uri;

        {
            final String returnUrlOverride = pwmRequest.getConfig().readAppProperty(AppProperty.OAUTH_RETURN_URL_OVERRIDE);
            final String siteURL = pwmRequest.getConfig().readSettingAsString(PwmSetting.PWM_SITE_URL);
            if (returnUrlOverride != null && !returnUrlOverride.trim().isEmpty()) {
                debugSource = "AppProperty(\"" + AppProperty.OAUTH_RETURN_URL_OVERRIDE.getKey() + "\")";
                redirect_uri = returnUrlOverride
                        + PwmServletDefinition.OAuthConsumer.servletUrl();
            } else if (siteURL != null && !siteURL.trim().isEmpty()) {
                debugSource = "SiteURL Setting";
                redirect_uri = siteURL
                        + PwmServletDefinition.OAuthConsumer.servletUrl();
            } else {
                debugSource = "Input Request URL";
                final String inputURI = pwmRequest.getHttpServletRequest().getRequestURL().toString();
                try {
                    final URI requestUri = new URI(inputURI);
                    final int port = requestUri.getPort();
                    redirect_uri = requestUri.getScheme() + "://" + requestUri.getHost()
                            + (port > 0 && port != 80 && port != 443 ? ":" + requestUri.getPort() : "")
                            + pwmRequest.getContextPath()
                            + PwmServletDefinition.OAuthConsumer.servletUrl();
                } catch (URISyntaxException e) {
                    throw new IllegalStateException("unable to parse inbound request uri while generating oauth redirect: " + e.getMessage());
                }
            }
        }

        LOGGER.trace("calculated oauth self end point URI as '" + redirect_uri + "' using method " + debugSource);
        return redirect_uri;
    }

    static class RestResults {
        final HttpResponse httpResponse;
        final String responseBody;

        RestResults(
                final HttpResponse httpResponse,
                final String responseBody
        )
        {
            this.httpResponse = httpResponse;
            this.responseBody = responseBody;
        }

        public HttpResponse getHttpResponse()
        {
            return httpResponse;
        }

        public String getResponseBody()
        {
            return responseBody;
        }
    }

    public boolean checkOAuthExpiration(
            final PwmRequest pwmRequest
    )
    {
        if (!Boolean.parseBoolean(pwmRequest.getConfig().readAppProperty(AppProperty.OAUTH_ENABLE_TOKEN_REFRESH))) {
            return false;
        }

        final LoginInfoBean loginInfoBean = pwmRequest.getPwmSession().getLoginInfoBean();
        final Date expirationDate = loginInfoBean.getOauthExp();

        if (expirationDate == null || (new Date()).before(expirationDate)) {
            //not expired
            return false;
        }

        LOGGER.trace(pwmRequest, "oauth access token has expired, attempting to refresh");

        try {
            final OAuthResolveResults resolveResults = makeOAuthRefreshRequest(pwmRequest,
                    loginInfoBean.getOauthRefToken());
            if (resolveResults != null) {
                if (resolveResults.getExpiresSeconds() > 0) {
                    final Date accessTokenExpirationDate = new Date(System.currentTimeMillis() + 1000 * resolveResults.getExpiresSeconds());
                    LOGGER.trace(pwmRequest, "noted oauth access token expiration at timestamp " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(accessTokenExpirationDate));
                    loginInfoBean.setOauthExp(accessTokenExpirationDate);
                    loginInfoBean.setOauthRefToken(resolveResults.getRefreshToken());
                    return false;
                }
            }
        } catch (PwmUnrecoverableException | IOException e) {
            LOGGER.error(pwmRequest, "error while processing oauth token refresh: " + e.getMessage());
        }
        LOGGER.error(pwmRequest, "unable to refresh oauth token for user, unauthenticated session");
        pwmRequest.getPwmSession().unauthenticateUser(pwmRequest);
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
        switch (oAuthUseCase) {
            case Authentication:
                oAuthState = OAuthState.newSSOAuthenticationState(sessionId, nextUrl);
                break;

            case ForgottenPassword:
                oAuthState = OAuthState.newForgottenPasswordState(sessionId, forgottenPasswordProfileID);
                break;

            default:
                throw new IllegalStateException("unexpected oAuthUseCase: " + oAuthUseCase);
        }

        LOGGER.trace(pwmRequest, "issuing oauth state id="
                + oAuthState.getStateID() + " with the next destination URL set to " + oAuthState.getNextUrl());


        final String jsonValue = JsonUtil.serialize(oAuthState);
        return pwmRequest.getPwmApplication().getSecureService().encryptToString(jsonValue);
    }

    private String figureUsernameGrantParam(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws IOException, PwmUnrecoverableException
    {
        if (userIdentity == null) {
            return null;
        }

        final String macroText = settings.getUsernameSendValue();
        if (StringUtil.isEmpty(macroText)) {
            return null;
        }

        final MacroMachine macroMachine = MacroMachine.forUser(pwmRequest, userIdentity);
        final String username = macroMachine.expandMacros(macroText);
        LOGGER.debug(pwmRequest, "calculated username value for user as: " + username);

        final String grantUrl = settings.getLoginURL();
        final String signUrl = grantUrl.replace("/grant","/sign");

        final Map<String, String> requestPayload;
        {
            final Map<String, String> dataPayload = new HashMap<>();
            dataPayload.put("username", username);

            final List<Map<String,String>> listWrapper = new ArrayList<>();
            listWrapper.add(dataPayload);

            requestPayload = new HashMap<>();
            requestPayload.put("data",  JsonUtil.serializeCollection(listWrapper));
        }

        LOGGER.debug(pwmRequest, "preparing to send username to OAuth /sign endpoint for future injection to /grant redirect");
        final RestResults restResults = makeHttpRequest(pwmRequest, "OAuth pre-inject username signing service",settings, signUrl, requestPayload);

        final String resultBody = restResults.getResponseBody();
        final Map<String,String> resultBodyMap = JsonUtil.deserializeStringMap(resultBody);
        final String data = resultBodyMap.get("data");
        LOGGER.debug(pwmRequest, "oauth /sign endpoint returned signed username data: " + data);
        return data;
    }
}
