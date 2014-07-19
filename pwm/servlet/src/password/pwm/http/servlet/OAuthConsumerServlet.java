/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

import com.google.gson.reflect.TypeToken;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.Validator;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmSession;
import password.pwm.http.filter.SessionFilter;
import password.pwm.ldap.UserAuthenticator;
import password.pwm.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class OAuthConsumerServlet extends TopServlet {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(OAuthConsumerServlet.class);

    @Override
    protected void processRequest(
            HttpServletRequest req,
            HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final Configuration config = pwmApplication.getConfig();
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final Settings settings = Settings.fromConfiguration(pwmApplication.getConfig());

        if (!pwmSession.getSessionStateBean().isOauthInProgress()) {
            final String errorMsg = "oauth consumer reached, but oauth authentication has not yet been initiated.";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,errorMsg);
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
            LOGGER.error(pwmSession,errorMsg);
            ServletHelper.forwardToErrorPage(req,resp,false);
            return;
        }

        final String oauthRequestError = Validator.readStringFromRequest(req,"error");
        if (oauthRequestError != null && !oauthRequestError.isEmpty()) {
            final String errorMsg = "error detected from oauth request parameter: " + oauthRequestError;
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,errorMsg,"Remote Error: " + oauthRequestError,null);
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
            LOGGER.error(pwmSession,errorMsg);
            ServletHelper.forwardToErrorPage(req,resp,false);
            return;
        }

        // mark the inprogress flag to false, if we get this far and fail user needs to start over.
        pwmSession.getSessionStateBean().setOauthInProgress(false);

        final String requestStateStr = Validator.readStringFromRequest(req,config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_STATE));
        if (requestStateStr == null || requestStateStr.isEmpty()) {
            final String errorMsg = "state parameter is missing from oauth request";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,errorMsg);
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
            LOGGER.error(pwmSession,errorMsg);
            ServletHelper.forwardToErrorPage(req,resp,false);
            return;
        } else if (!requestStateStr.equals(pwmSession.getSessionStateBean().getSessionVerificationKey())) {
            final String errorMsg = "state value does not match current session key value";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,errorMsg);
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
            LOGGER.error(pwmSession,errorMsg);
            ServletHelper.forwardToErrorPage(req,resp,false);
            return;
        }

        final String requestCodeStr = Validator.readStringFromRequest(req,config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_CODE));
        LOGGER.trace(pwmSession,"received code from oauth server: " + requestCodeStr);

        final String resolveResponseBodyStr = makeOAuthResolveRequest(pwmApplication, pwmSession, settings, req, requestCodeStr);

        final Map<String, String> resolveResultValues = Helper.getGson().fromJson(resolveResponseBodyStr,
                new TypeToken<Map<String, String>>() {
                }.getType());

        final String accessToken = resolveResultValues.get(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_ACCESS_TOKEN));
        final String getAttributeResponseBodyStr = makeOAuthGetAttribute(pwmApplication, pwmSession, accessToken, settings);

        final Map<String, String> getAttributeResultValues = Helper.getGson().fromJson(getAttributeResponseBodyStr,
                new TypeToken<Map<String, String>>() {
                }.getType());

        final String userDN = getAttributeResultValues.get(settings.getDnAttributeName());
        LOGGER.debug(pwmSession, "received user login id value from OAuth server: " + userDN);

        try {
            UserAuthenticator.authUserWithUnknownPassword(
                    userDN,
                    pwmSession,
                    pwmApplication,
                    req.isSecure(),
                    UserInfoBean.AuthenticationType.AUTH_WITHOUT_PASSWORD
            );
            // recycle the session to prevent session fixation attack.
            ServletHelper.recycleSessions(pwmApplication, pwmSession, req, resp);

            // see if there is a an original request url
            final String originalURL = pwmSession.getSessionStateBean().getOriginalRequestURL();
            pwmSession.getSessionStateBean().setOriginalRequestURL(null);

            if (originalURL != null && !originalURL.contains(PwmConstants.URL_SERVLET_LOGIN)) {
                resp.sendRedirect(SessionFilter.rewriteRedirectURL(originalURL, req, resp));
            } else {
                resp.sendRedirect(SessionFilter.rewriteRedirectURL(req.getContextPath(), req, resp));
            }
        } catch (ChaiUnavailableException e) {
            LOGGER.error(pwmSession, "unable to reach ldap server during Auth authentication attempt: " + e.getMessage());
            pwmSession.getSessionStateBean().setSessionError(PwmError.ERROR_OAUTH_ERROR.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
            return;
        } catch (PwmException e) {
            LOGGER.error(pwmSession, "error during OAuth authentication attempt: " + e.getMessage());
            pwmSession.getSessionStateBean().setSessionError(PwmError.ERROR_OAUTH_ERROR.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, req.getSession().getServletContext());
            return;
        }

        LOGGER.trace(pwmSession, "OAuth login sequence successfully completed");
    }

    private static String makeOAuthResolveRequest(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final Settings settings,
            final HttpServletRequest req,
            final String requestCode
    )
            throws IOException, PwmUnrecoverableException
    {
        final Date startTime = new Date();
        final Configuration config = pwmApplication.getConfig();
        final String requestUrl = settings.getCodeResolveUrl();
        final String grant_type=config.readAppProperty(AppProperty.OAUTH_ID_GRANT_TYPE);
        final String redirect_uri = figureOauthSelfEndPointUrl(req);

        final Map<String,String> requestParams = new HashMap<>();
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_CODE),requestCode);
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_GRANT_TYPE),grant_type);
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_REDIRECT_URI), redirect_uri);
        final String requestBody = ServletHelper.appendAndEncodeUrlParameters("", requestParams);
        LOGGER.trace(pwmSession, "beginning OAuth coderesolver request to " + requestUrl + ", body: \n" + requestBody);
        final HttpPost httpPost = new HttpPost(requestUrl);
        httpPost.setHeader(PwmConstants.HTTP_HEADER_BASIC_AUTH,
                new BasicAuthInfo(settings.getClientID(), settings.getSecret()).toAuthHeader());
        final StringEntity bodyEntity = new StringEntity(requestBody);
        bodyEntity.setContentType(PwmConstants.MIMETYPE_FORM);
        httpPost.setEntity(bodyEntity);

        final HttpResponse httpResponse = Helper.getHttpClient(pwmApplication.getConfig()).execute(httpPost);
        final String bodyResponse = EntityUtils.toString(httpResponse.getEntity());

        LOGGER.trace(pwmSession, "finishedOAuth coderesolver request in "
                + TimeDuration.fromCurrent(startTime).asCompactString() + ", status: "
                + httpResponse.getStatusLine()+ ", body: \n" + bodyResponse);

        if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new PwmUnrecoverableException(new ErrorInformation(
                    PwmError.ERROR_OAUTH_ERROR,
                    "unexpected HTTP status code (" + httpResponse.getStatusLine().getStatusCode() + ") body: \n" + bodyResponse
            ));
        }

        return bodyResponse;
    }

    private static String makeOAuthGetAttribute(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final String accessToken,
            final Settings settings
    )
            throws IOException, PwmUnrecoverableException
    {
        final Date startTime = new Date();
        final Configuration config = pwmApplication.getConfig();
        final String requestUrl = settings.getAttributesUrl();
        final Map<String,String> requestParams = new HashMap<>();
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_ACCESS_TOKEN),accessToken);
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_ATTRIBUTES),settings.getDnAttributeName());
        final String requestBody = ServletHelper.appendAndEncodeUrlParameters("",requestParams);
        LOGGER.trace(pwmSession, "beginning OAuth getattribute request to " + requestUrl + ", body: \n" + requestBody);

        final HttpPost httpPost = new HttpPost(requestUrl);
        httpPost.setHeader(PwmConstants.HTTP_HEADER_BASIC_AUTH,
                new BasicAuthInfo(settings.getClientID(), settings.getSecret()).toAuthHeader());
        final StringEntity bodyEntity = new StringEntity(requestBody);
        bodyEntity.setContentType(PwmConstants.MIMETYPE_FORM);
        httpPost.setEntity(bodyEntity);

        final HttpResponse httpResponse = Helper.getHttpClient(pwmApplication.getConfig()).execute(httpPost);
        final String bodyResponse = EntityUtils.toString(httpResponse.getEntity());

        LOGGER.trace(pwmSession, "finishedOAuth getattribute request in "
                + TimeDuration.fromCurrent(startTime).asCompactString() + ", status: "
                + httpResponse.getStatusLine()+ ", body: \n" + bodyResponse);

        if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new PwmUnrecoverableException(new ErrorInformation(
                    PwmError.ERROR_OAUTH_ERROR,
                    "unexpected HTTP status code (" + httpResponse.getStatusLine().getStatusCode() + ") body: \n" + bodyResponse
            ));
        }

        return bodyResponse;
    }

    public static String figureOauthSelfEndPointUrl(final HttpServletRequest req) {
        final String redirect_uri;
        try {
            final URI requestUri = new URI(req.getRequestURL().toString());
            redirect_uri = requestUri.getScheme() + "://" + requestUri.getHost()
                    + (requestUri.getPort() > 0 ? ":" + requestUri.getPort() : "")
                    + req.getContextPath() + "/public/" + PwmConstants.URL_SERVLET_OAUTH_COSUMER;
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
        private String secret;
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

        public String getSecret()
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
                    && (secret != null && !secret.isEmpty() 
                    && (dnAttributeName != null && !dnAttributeName.isEmpty()));
        }

        public static Settings fromConfiguration(final Configuration config) {
            final Settings settings = new Settings();
            settings.loginURL = config.readSettingAsString(PwmSetting.OAUTH_ID_LOGIN_URL);
            settings.codeResolveUrl = config.readSettingAsString(PwmSetting.OAUTH_ID_CODERESOLVE_URL);
            settings.attributesUrl = config.readSettingAsString(PwmSetting.OAUTH_ID_ATTRIBUTES_URL);
            settings.clientID = config.readSettingAsString(PwmSetting.OAUTH_ID_CLIENTNAME);
            settings.secret = config.readSettingAsString(PwmSetting.OAUTH_ID_SECRET);
            settings.dnAttributeName = config.readSettingAsString(PwmSetting.OAUTH_ID_DN_ATTRIBUTE_NAME);
            return settings;
        }
    }
}
