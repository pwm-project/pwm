/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.http.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.ServletHelper;
import password.pwm.http.bean.LoginInfoBean;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.http.client.PwmHttpClientConfiguration;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.JsonUtil;
import password.pwm.util.PasswordData;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@WebServlet(
        name="OAuthConsumerServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + "/oauth"
        }
)
public class OAuthConsumerServlet extends AbstractPwmServlet {
    private static final PwmLogger LOGGER = PwmLogger.forClass(OAuthConsumerServlet.class);
    private static int oauthStateIdCounter = 0;


    private static class OAuthState implements Serializable {
        private final int stateID = oauthStateIdCounter++;
        private final Date issueTime = new Date();
        private String sessionID;
        private String nextUrl;
    }

    @Override
    protected ProcessAction readProcessAction(PwmRequest request)
            throws PwmUnrecoverableException
    {
        return null;
    }

    @Override
    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmRequest.getConfig();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Settings settings = Settings.fromConfiguration(pwmApplication.getConfig());

        final boolean userIsAuthenticated = pwmSession.getSessionStateBean().isAuthenticated();
        final OAuthRequestState oAuthRequestState = readOAuthRequestState(pwmRequest);

        if (!userIsAuthenticated && !pwmSession.getSessionStateBean().isOauthInProgress()) {
            if (oAuthRequestState != null) {
                final String nextUrl = oAuthRequestState.oAuthState.nextUrl;
                LOGGER.debug(pwmSession, "received unrecognized oauth response, ignoring authcode and redirecting to embedded next url: " + nextUrl);
                pwmRequest.sendRedirect(nextUrl);
                return;
            }
            final String errorMsg = "oauth consumer reached, but oauth authentication has not yet been initiated.";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,errorMsg);
            pwmRequest.respondWithError(errorInformation);
            LOGGER.error(pwmSession, errorMsg);
            return;
        }

        final String oauthRequestError = pwmRequest.readParameterAsString("error");
        if (oauthRequestError != null && !oauthRequestError.isEmpty()) {
            final String errorMsg = "error detected from oauth request parameter: " + oauthRequestError;
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,errorMsg,"Remote Error: " + oauthRequestError,null);
            LOGGER.error(pwmSession,errorMsg);
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        if (userIsAuthenticated) {
            LOGGER.debug(pwmSession, "oauth consumer reached, but user is already authenticated; will proceed and verify authcode matches current user identity.");
        }

        // mark the inprogress flag to false, if we get this far and fail user needs to start over.
        pwmSession.getSessionStateBean().setOauthInProgress(false);

        if (oAuthRequestState == null) {
            final String errorMsg = "state parameter is missing from oauth request";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,errorMsg);
            LOGGER.error(pwmSession,errorMsg);
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        if (!oAuthRequestState.sessionMatch) {
            LOGGER.debug(pwmSession, "oauth consumer reached but response is not for a request issued during the current session, will redirect back to oauth server for verification update");

            try{
                final String nextURL = oAuthRequestState.oAuthState.nextUrl;
                redirectUserToOAuthServer(pwmRequest, nextURL);
                return;
            } catch (PwmUnrecoverableException e) {
                final String errorMsg = "unexpected error redirecting user to oauth page: " + e.toString();
                ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                pwmRequest.setResponseError(errorInformation);
                LOGGER.error(errorInformation.toDebugStr());
            }
        }

        final String requestCodeStr = pwmRequest.readParameterAsString(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_CODE));
        LOGGER.trace(pwmSession,"received code from oauth server: " + requestCodeStr);

        final OAuthResolveResults resolveResults;
        try {
            resolveResults = makeOAuthResolveRequest(pwmRequest, settings, requestCodeStr);
        } catch (IOException | PwmException e) {
            final ErrorInformation errorInformation;
            final String errorMsg = "unexpected error communicating with oauth server: " + e.toString();
            if (e instanceof PwmException) {
                errorInformation = new ErrorInformation(((PwmException) e).getError(),errorMsg);
            } else {
                errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            }
            pwmRequest.setResponseError(errorInformation);
            LOGGER.error(errorInformation.toDebugStr());
            return;
        }

        if (resolveResults == null || resolveResults.getAccessToken() == null || resolveResults.getAccessToken().isEmpty()) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,"OAuth server did not respond with an access token");
            LOGGER.error(pwmRequest, errorInformation);
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        if (resolveResults.getExpiresSeconds() > 0) {
            if (resolveResults.getRefreshToken() == null || resolveResults.getRefreshToken().isEmpty()) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,"OAuth server gave expiration for access token, but did not provide a refresh token");
                LOGGER.error(pwmRequest, errorInformation);
                pwmRequest.respondWithError(errorInformation);
                return;
            }
        }

        final String oauthSuppliedUsername;
        {
            final String getAttributeResponseBodyStr = makeOAuthGetAttributeRequest(pwmRequest, resolveResults.getAccessToken(), settings);
            final Map<String, String> getAttributeResultValues = JsonUtil.deserializeStringMap(getAttributeResponseBodyStr);
            oauthSuppliedUsername = getAttributeResultValues.get(settings.getDnAttributeName());
            if (oauthSuppliedUsername == null || oauthSuppliedUsername.isEmpty()) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,"OAuth server did not respond with an username attribute value");
                LOGGER.error(pwmRequest, errorInformation);
                pwmRequest.respondWithError(errorInformation);
                return;
            }
        }

        LOGGER.debug(pwmSession, "received user login id value from OAuth server: " + oauthSuppliedUsername);

        if (userIsAuthenticated) {
            try {
                final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmRequest);
                final UserIdentity resolvedIdentity = userSearchEngine.resolveUsername(oauthSuppliedUsername, null, null);
                if (resolvedIdentity != null && resolvedIdentity.canonicalEquals(pwmSession.getUserInfoBean().getUserIdentity(),pwmApplication)) {
                    LOGGER.debug(pwmSession, "verified incoming oauth code for already authenticated session does resolve to same as logged in user");
                } else {
                    final String errorMsg = "incoming oauth code for already authenticated session does not resolve to same as logged in user ";
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,errorMsg);
                    LOGGER.error(pwmSession,errorMsg);
                    pwmRequest.respondWithError(errorInformation);
                    pwmSession.unauthenticateUser(pwmRequest);
                    return;
                }
            } catch (PwmOperationalException e) {
                final String errorMsg = "error while examining incoming oauth code for already authenticated session: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,errorMsg);
                LOGGER.error(pwmSession,errorMsg);
                pwmRequest.respondWithError(errorInformation);
                return;
            }
        }

        try {
            if (!userIsAuthenticated) {
                final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(pwmApplication, pwmSession, PwmAuthenticationSource.OAUTH);
                sessionAuthenticator.authUserWithUnknownPassword(oauthSuppliedUsername, AuthenticationType.AUTH_WITHOUT_PASSWORD);
            }

            // recycle the session to prevent session fixation attack.
            pwmRequest.getPwmSession().getSessionStateBean().setSessionIdRecycleNeeded(true);

            // forward to nextUrl
            final String nextUrl = oAuthRequestState.oAuthState.nextUrl;
            LOGGER.debug(pwmSession, "oauth authentication completed, redirecting to originally requested URL: " + nextUrl);
            pwmRequest.sendRedirect(nextUrl);
        } catch (PwmException e) {
            LOGGER.error(pwmSession, "error during OAuth authentication attempt: " + e.getMessage());
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR, e.getMessage());
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        LOGGER.trace(pwmSession, "OAuth login sequence successfully completed");
    }

    private static OAuthResolveResults makeOAuthResolveRequest(
            final PwmRequest pwmRequest,
            final Settings settings,
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

        final RestResults restResults = makeHttpRequest(pwmRequest, "OAuth code resolver", settings, requestUrl, requestParams);
        final HttpResponse httpResponse = restResults.getHttpResponse();

        if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new PwmUnrecoverableException(new ErrorInformation(
                    PwmError.ERROR_OAUTH_ERROR,
                    "unexpected HTTP status code (" + httpResponse.getStatusLine().getStatusCode() + ")"
            ));
        }

        final String resolveResponseBodyStr = restResults.getResponseBody();

        final Map<String, String> resolveResultValues = JsonUtil.deserializeStringMap(resolveResponseBodyStr);
        final OAuthResolveResults oAuthResolveResults = new OAuthResolveResults();

        oAuthResolveResults.setAccessToken(resolveResultValues.get(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_ACCESS_TOKEN)));
        oAuthResolveResults.setRefreshToken(resolveResultValues.get(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_REFRESH_TOKEN)));
        oAuthResolveResults.setExpiresSeconds(0);
        try {
            oAuthResolveResults.setExpiresSeconds(Integer.parseInt(resolveResultValues.get(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_EXPIRES))));
        } catch (Exception e) {
            LOGGER.warn(pwmRequest, "error parsing oauth expires value in resolve request: " + e.getMessage());
        }

        return oAuthResolveResults;
    }

    public static boolean checkOAuthExpiration(
            final PwmRequest pwmRequest
    )
    {
        if (!Boolean.parseBoolean(pwmRequest.getConfig().readAppProperty(AppProperty.OAUTH_ENABLE_TOKEN_REFRESH))) {
            return false;
        }

        final LoginInfoBean loginInfoBean = pwmRequest.getPwmSession().getLoginInfoBean();
        final Date expirationDate = loginInfoBean.getOauthExpiration();

        if (expirationDate == null || (new Date()).before(expirationDate)) {
            //not expired
            return false;
        }

        LOGGER.trace(pwmRequest, "oauth access token has expired, attempting to refresh");

        final Settings settings = Settings.fromConfiguration(pwmRequest.getConfig());
        try {
            OAuthResolveResults resolveResults = makeOAuthRefreshRequest(pwmRequest, settings,
                    loginInfoBean.getOauthRefreshToken());
            if (resolveResults != null) {
                if (resolveResults.getExpiresSeconds() > 0) {
                    final Date accessTokenExpirationDate = new Date(System.currentTimeMillis() + 1000 * resolveResults.getExpiresSeconds());
                    LOGGER.trace(pwmRequest, "noted oauth access token expiration at timestamp " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(accessTokenExpirationDate));
                    loginInfoBean.setOauthExpiration(accessTokenExpirationDate);
                    loginInfoBean.setOauthRefreshToken(resolveResults.getRefreshToken());
                    return false;
                }
            }
        } catch (PwmUnrecoverableException | IOException e) {
            LOGGER.error(pwmRequest, "error while processing oauth token refresh:" + e.getMessage());
        }
        LOGGER.error(pwmRequest, "unable to refresh oauth token for user, unauthenticated session");
        pwmRequest.getPwmSession().unauthenticateUser(pwmRequest);
        return true;
    }

    private static OAuthResolveResults makeOAuthRefreshRequest(
            final PwmRequest pwmRequest,
            final Settings settings,
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
        final HttpResponse httpResponse = restResults.getHttpResponse();

        if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new PwmUnrecoverableException(new ErrorInformation(
                    PwmError.ERROR_OAUTH_ERROR,
                    "unexpected HTTP status code (" + httpResponse.getStatusLine().getStatusCode() + ")"
            ));
        }

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

    private static String makeOAuthGetAttributeRequest(
            final PwmRequest pwmRequest,
            final String accessToken,
            final Settings settings
    )
            throws IOException, PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        final String requestUrl = settings.getAttributesUrl();
        final Map<String,String> requestParams = new HashMap<>();
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_ACCESS_TOKEN),accessToken);
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_ATTRIBUTES),settings.getDnAttributeName());

        final RestResults restResults = makeHttpRequest(pwmRequest, "OAuth getattribute", settings, requestUrl, requestParams);
        final HttpResponse httpResponse = restResults.getHttpResponse();

        if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new PwmUnrecoverableException(new ErrorInformation(
                    PwmError.ERROR_OAUTH_ERROR,
                    "unexpected HTTP status code (" + httpResponse.getStatusLine().getStatusCode() + ")"
            ));
        }

        return restResults.getResponseBody();
    }

    private static RestResults makeHttpRequest(
            final PwmRequest pwmRequest,
            final String debugText,
            final Settings settings,
            final String requestUrl,
            final Map<String,String> requestParams
    )
            throws IOException, PwmUnrecoverableException
    {
        final Date startTime = new Date();
        final String requestBody = ServletHelper.appendAndEncodeUrlParameters("", requestParams);
        LOGGER.trace(pwmRequest, "beginning " + debugText + " request to " + requestUrl + ", body: \n" + requestBody);
        final HttpPost httpPost = new HttpPost(requestUrl);
        httpPost.setHeader(PwmConstants.HttpHeader.Authorization.getHttpName(),
                new BasicAuthInfo(settings.getClientID(), settings.getSecret()).toAuthHeader());
        final StringEntity bodyEntity = new StringEntity(requestBody);
        bodyEntity.setContentType(PwmConstants.ContentTypeValue.form.getHeaderValue());
        httpPost.setEntity(bodyEntity);

        final X509Certificate[] certs = pwmRequest.getConfig().readSettingAsCertificate(PwmSetting.OAUTH_ID_CERTIFICATE);
        final HttpResponse httpResponse;
        if (certs == null || certs.length == 0) {
            httpResponse = PwmHttpClient.getHttpClient(pwmRequest.getConfig()).execute(httpPost);
        } else {
            httpResponse = PwmHttpClient.getHttpClient(pwmRequest.getConfig(),new PwmHttpClientConfiguration(certs)).execute(httpPost);
        }
        final String bodyResponse = EntityUtils.toString(httpResponse.getEntity());

        final StringBuilder debugOutput = new StringBuilder();
        debugOutput.append(debugText).append(
                TimeDuration.fromCurrent(startTime).asCompactString()).append(", status: ").append(
                httpResponse.getStatusLine()).append("\n");
        for (Header responseHeader : httpResponse.getAllHeaders()) {
            debugOutput.append(" response header: ").append(responseHeader.getName()).append(": ").append(
                    responseHeader.getValue()).append("\n");
        }

        debugOutput.append(" body:\n ").append(bodyResponse);
        LOGGER.trace(pwmRequest, debugOutput.toString());
        return new RestResults(httpResponse, bodyResponse);
    }

    public static String figureOauthSelfEndPointUrl(final PwmRequest pwmRequest) {
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();
        final String redirect_uri;
        try {
            final URI requestUri = new URI(req.getRequestURL().toString());
            redirect_uri = requestUri.getScheme() + "://" + requestUri.getHost()
                    + (requestUri.getPort() > 0 ? ":" + requestUri.getPort() : "")
                    + PwmServletDefinition.OAuthConsumer.servletUrl();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("unable to parse inbound request uri while generating oauth redirect: " + e.getMessage());
        }
        return redirect_uri;
    }

    public static class Settings implements Serializable {
        private String loginURL;
        private String codeResolveUrl;
        private String attributesUrl;
        private String clientID;
        private PasswordData secret;
        private String dnAttributeName;

        public String getLoginURL()
        {
            return loginURL;
        }

        public String getCodeResolveUrl()
        {
            return codeResolveUrl;
        }

        public String getAttributesUrl()
        {
            return attributesUrl;
        }

        public String getClientID()
        {
            return clientID;
        }

        public PasswordData getSecret()
        {
            return secret;
        }

        public String getDnAttributeName()
        {
            return dnAttributeName;
        }

        public boolean oAuthIsConfigured() {
            return (loginURL != null && !loginURL.isEmpty())
                    && (codeResolveUrl != null && !codeResolveUrl.isEmpty())
                    && (attributesUrl != null && !attributesUrl.isEmpty())
                    && (clientID != null && !clientID.isEmpty())
                    && (secret != null)
                    && (dnAttributeName != null && !dnAttributeName.isEmpty());
        }

        public static Settings fromConfiguration(final Configuration config) {
            final Settings settings = new Settings();
            settings.loginURL = config.readSettingAsString(PwmSetting.OAUTH_ID_LOGIN_URL);
            settings.codeResolveUrl = config.readSettingAsString(PwmSetting.OAUTH_ID_CODERESOLVE_URL);
            settings.attributesUrl = config.readSettingAsString(PwmSetting.OAUTH_ID_ATTRIBUTES_URL);
            settings.clientID = config.readSettingAsString(PwmSetting.OAUTH_ID_CLIENTNAME);
            settings.secret = config.readSettingAsPassword(PwmSetting.OAUTH_ID_SECRET);
            settings.dnAttributeName = config.readSettingAsString(PwmSetting.OAUTH_ID_DN_ATTRIBUTE_NAME);
            return settings;
        }
    }

    static class RestResults {
        final HttpResponse httpResponse;
        final String responseBody;

        RestResults(
                HttpResponse httpResponse,
                String responseBody
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

    static class OAuthResolveResults implements Serializable {
        private String accessToken;
        private int expiresSeconds;
        private String refreshToken;


        public String getAccessToken()
        {
            return accessToken;
        }

        public void setAccessToken(String accessToken)
        {
            this.accessToken = accessToken;
        }

        public int getExpiresSeconds()
        {
            return expiresSeconds;
        }

        public void setExpiresSeconds(int expiresSeconds)
        {
            this.expiresSeconds = expiresSeconds;
        }

        public String getRefreshToken()
        {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken)
        {
            this.refreshToken = refreshToken;
        }
    }

    public static String makeStateStringForRequest(
            final PwmRequest pwmRequest,
            final String nextUrl
    )
            throws PwmUnrecoverableException
    {
        OAuthState oAuthState = new OAuthState();
        oAuthState.sessionID = pwmRequest.getPwmSession().getSessionStateBean().getSessionVerificationKey();
        oAuthState.nextUrl = nextUrl;

        LOGGER.trace(pwmRequest, "issuing oauth state id="
                + oAuthState.stateID + " with the next destination URL set to " + oAuthState.nextUrl);



        final String jsonValue = JsonUtil.serialize(oAuthState);
        return pwmRequest.getPwmApplication().getSecureService().encryptToString(jsonValue);
    }

    public static class OAuthRequestState {
        private OAuthState oAuthState;
        private boolean sessionMatch;

        public OAuthRequestState(OAuthState oAuthState, boolean sessionMatch) {
            this.oAuthState = oAuthState;
            this.sessionMatch = sessionMatch;
        }
    }

    public static void redirectUserToOAuthServer(
            final PwmRequest pwmRequest,
            final String nextUrl
    )
            throws PwmUnrecoverableException, IOException
    {

        LOGGER.trace(pwmRequest, "preparing to redirect user to oauth authentication service, setting nextUrl to " + nextUrl);
        pwmRequest.getPwmSession().getSessionStateBean().setOauthInProgress(true);

        final Configuration config = pwmRequest.getConfig();
        final OAuthConsumerServlet.Settings settings = OAuthConsumerServlet.Settings.fromConfiguration(config);
        final String state = OAuthConsumerServlet.makeStateStringForRequest(pwmRequest, nextUrl);
        final String redirectUri = OAuthConsumerServlet.figureOauthSelfEndPointUrl(pwmRequest);
        final String code = config.readAppProperty(AppProperty.OAUTH_ID_REQUEST_TYPE);

        final Map<String,String> urlParams = new LinkedHashMap<>();
        urlParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_CLIENT_ID),settings.getClientID());
        urlParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_RESPONSE_TYPE),code);
        urlParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_STATE),state);
        urlParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_REDIRECT_URI), redirectUri);

        final String redirectUrl = ServletHelper.appendAndEncodeUrlParameters(settings.getLoginURL(), urlParams);

        try{
            pwmRequest.sendRedirect(redirectUrl);
            pwmRequest.getPwmSession().getSessionStateBean().setOauthInProgress(true);
            LOGGER.debug(pwmRequest,"redirecting user to oauth id server, url: " + redirectUrl);
        } catch (PwmUnrecoverableException e) {
            final String errorMsg = "unexpected error redirecting user to oauth page: " + e.toString();
            ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            pwmRequest.setResponseError(errorInformation);
            LOGGER.error(errorInformation.toDebugStr());
        }

    }

    public static OAuthRequestState readOAuthRequestState(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final String requestStateStr = pwmRequest.readParameterAsString(pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_PARAM_OAUTH_STATE));
        if (requestStateStr != null) {
            final String stateJson = pwmRequest.getPwmApplication().getSecureService().decryptStringValue(requestStateStr);
            final OAuthState oAuthState = JsonUtil.deserialize(stateJson, OAuthState.class);
            if (oAuthState != null) {
                final boolean sessionMatch = oAuthState.sessionID.equals(pwmRequest.getPwmSession().getSessionStateBean().getSessionVerificationKey());
                LOGGER.trace(pwmRequest, "read state while parsing oauth consumer request id="
                        + oAuthState.stateID + ", issueTime="
                        + PwmConstants.DEFAULT_DATETIME_FORMAT.format(oAuthState.issueTime) + ", nextUrl="
                        + oAuthState.nextUrl + ", sessionIDmatch=" + sessionMatch);

                return new OAuthRequestState(oAuthState, sessionMatch);
            }
        }


        return null;
    }
}
