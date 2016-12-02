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

package password.pwm.http.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.http.bean.LoginServletBean;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.util.CaptchaUtility;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User interaction servlet for form-based authentication.   Depending on how PWM is deployed,
 * users may or may not ever visit this servlet.   Generally, if PWM is behind iChain, or some
 * other SSO enabler using HTTP BASIC authentication, this form will not be invoked.
 *
 * @author Jason D. Rivard
 */
@WebServlet(
        name="LoginServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/login",
                PwmConstants.URL_PREFIX_PRIVATE + "/Login"
        }
)
public class LoginServlet extends AbstractPwmServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(LoginServlet.class.getName());

    public enum LoginServletAction implements ProcessAction {
        login(HttpMethod.POST),
        restLogin(HttpMethod.POST),
        receiveUrl(HttpMethod.GET),

        ;

        private final HttpMethod method;

        LoginServletAction(final HttpMethod method)
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods()
        {
            return Collections.singletonList(method);
        }
    }

    protected LoginServletAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        try {
            return LoginServletAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


    public void processAction(
            final PwmRequest pwmRequest
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final boolean passwordOnly = pwmRequest.isAuthenticated() &&
                pwmRequest.getPwmSession().getLoginInfoBean().getType() == AuthenticationType.AUTH_WITHOUT_PASSWORD;

        final LoginServletAction action = readProcessAction(pwmRequest);

        if (action != null) {
            switch (action) {
                case login:
                    processLogin(pwmRequest, passwordOnly);
                    break;

                case restLogin:
                    processRestLogin(pwmRequest, passwordOnly);
                    break;

                case receiveUrl:
                    processReceiveUrl(pwmRequest);
                    break;

                default:
                    JavaHelper.unhandledSwitchStatement(action);
            }

            return;
        }

        forwardToJSP(pwmRequest, passwordOnly);
    }

    private void processLogin(final PwmRequest pwmRequest, final boolean passwordOnly)
            throws PwmUnrecoverableException, ServletException, IOException, ChaiUnavailableException
    {
        final Map<String,String> valueMap = pwmRequest.readParametersAsMap();
        try {
            handleLoginRequest(pwmRequest, valueMap, passwordOnly);
        } catch (PwmOperationalException e) {
            pwmRequest.setResponseError(e.getErrorInformation());
            forwardToJSP(pwmRequest, passwordOnly);
            return;
        }

        // login has succeeded
        pwmRequest.sendRedirect(determinePostLoginUrl(pwmRequest));
    }

    private void processRestLogin(final PwmRequest pwmRequest, final boolean passwordOnly)
            throws PwmUnrecoverableException, ServletException, IOException, ChaiUnavailableException
    {
        final Map<String, String> valueMap = pwmRequest.readBodyAsJsonStringMap();

        if (valueMap == null || valueMap.isEmpty()) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"missing json request body");
            pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
            return;
        }

        try {
            handleLoginRequest(pwmRequest, valueMap, passwordOnly);
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInformation = e.getErrorInformation();
            LOGGER.trace(pwmRequest, "returning rest login error to client: " + errorInformation.toDebugStr());
            pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
            return;
        }

        pwmRequest.readParametersAsMap();

        // login has succeeded
        final String nextLoginUrl = determinePostLoginUrl(pwmRequest);
        final RestResultBean restResultBean = new RestResultBean();
        final HashMap<String,String> resultMap = new HashMap<>(Collections.singletonMap("nextURL", nextLoginUrl));
        restResultBean.setData(resultMap);
        LOGGER.debug(pwmRequest, "rest login succeeded");
        pwmRequest.outputJsonResult(restResultBean);
    }

    private void processReceiveUrl(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, IOException
    {
        final String encryptedNextUrl = pwmRequest.readParameterAsString(PwmConstants.PARAM_POST_LOGIN_URL);
        if (!StringUtil.isEmpty(encryptedNextUrl)) {
            final String nextUrl = pwmRequest.getPwmApplication().getSecureService().decryptStringValue(encryptedNextUrl);
            if (!StringUtil.isEmpty(nextUrl)) {
                final LoginServletBean loginServletBean = pwmRequest.getPwmApplication().getSessionStateService().getBean(pwmRequest, LoginServletBean.class);
                LOGGER.trace(pwmRequest, "received nextUrl and storing in module bean, value: " + nextUrl);
                loginServletBean.setNextUrl(nextUrl);
            }
        }

        pwmRequest.sendRedirect(PwmServletDefinition.Login);
    }

    private void handleLoginRequest(
            final PwmRequest pwmRequest,
            final Map<String,String> valueMap,
            final boolean passwordOnly
    )
            throws PwmOperationalException, ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final String username = valueMap.get(PwmConstants.PARAM_USERNAME);
        final String passwordStr = valueMap.get(PwmConstants.PARAM_PASSWORD);
        final PasswordData password = passwordStr != null && passwordStr.length() > 0
                ? new PasswordData(passwordStr)
                : null;
        final String context = valueMap.get(PwmConstants.PARAM_CONTEXT);
        final String ldapProfile = valueMap.get(PwmConstants.PARAM_LDAP_PROFILE);
        final String recaptchaResponse = valueMap.get("g-recaptcha-response");


        if (!passwordOnly && (username == null || username.isEmpty())) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"missing username parameter"));
        }

        if (password == null) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"missing password parameter"));
        }

        if (!CaptchaUtility.verifyReCaptcha(pwmRequest, recaptchaResponse)) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_BAD_CAPTCHA_RESPONSE, "captcha incorrect"));
        }

        final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(
                pwmRequest.getPwmApplication(),
                pwmRequest.getPwmSession(),
                PwmAuthenticationSource.LOGIN_FORM
        );

        if (passwordOnly) {
            final UserIdentity userIdentity = pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity();
            sessionAuthenticator.authenticateUser(userIdentity, password);
        } else {
            sessionAuthenticator.searchAndAuthenticateUser(username, password, context, ldapProfile);
        }

        // if here then login was successful

        // recycle the session to prevent session fixation attack.
        pwmRequest.getPwmSession().getSessionStateBean().setSessionIdRecycleNeeded(true);
    }

    private void forwardToJSP(
            final PwmRequest pwmRequest,
            final boolean passwordOnly
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmConstants.JspUrl url = passwordOnly ? PwmConstants.JspUrl.LOGIN_PW_ONLY : PwmConstants.JspUrl.LOGIN;
        pwmRequest.forwardToJsp(url);
    }

    private static String determinePostLoginUrl(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException
    {
        final LoginServletBean loginServletBean = pwmRequest.getPwmApplication().getSessionStateService().getBean(pwmRequest, LoginServletBean.class);
        final String decryptedValue = loginServletBean.getNextUrl();

        if (decryptedValue != null && !decryptedValue.isEmpty()) {
            final PwmURL originalPwmURL = new PwmURL(URI.create(decryptedValue),pwmRequest.getContextPath());
            if (!originalPwmURL.isLoginServlet()) {
                loginServletBean.setNextUrl(null);
                return decryptedValue;
            }
        }
        return pwmRequest.getContextPath();
    }

    public static void redirectToLoginServlet(final PwmRequest pwmRequest)
            throws IOException, PwmUnrecoverableException
    {
        //store the original requested url
        final String originalRequestedUrl = pwmRequest.getURLwithQueryString();

        final String encryptedRedirUrl = pwmRequest.getPwmApplication().getSecureService().encryptToString(originalRequestedUrl);

        final Map<String,String> paramMap = new HashMap<>();
        paramMap.put(PwmConstants.PARAM_POST_LOGIN_URL, encryptedRedirUrl);
        paramMap.put(PwmConstants.PARAM_ACTION_REQUEST, LoginServletAction.receiveUrl.toString());

        final String redirectUrl = PwmURL.appendAndEncodeUrlParameters(
                pwmRequest.getContextPath() + PwmServletDefinition.Login.servletUrl(),
                paramMap
        );

        LOGGER.trace(pwmRequest, "redirecting to self to set nextUrl to: " + originalRequestedUrl);

        pwmRequest.sendRedirect(redirectUrl);
    }
}

