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
import password.pwm.PwmConstants;
import password.pwm.Validator;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.ldap.UserAuthenticator;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.PwmServletURLHelper;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import java.io.IOException;
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
public class LoginServlet extends PwmServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(LoginServlet.class.getName());

    public enum LoginServletAction implements ProcessAction {
        login(HttpMethod.POST),
        restLogin(HttpMethod.POST),

        ;

        private final HttpMethod method;

        LoginServletAction(HttpMethod method)
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
            return LoginServletAction.valueOf(request.readStringParameter(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }



// -------------------------- OTHER METHODS --------------------------

    public void processAction(
            final PwmRequest pwmRequest
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final boolean passwordOnly = pwmRequest.getPwmSession().getSessionStateBean().isAuthenticated() &&
                pwmRequest.getPwmSession().getUserInfoBean().getAuthenticationType() == UserInfoBean.AuthenticationType.AUTH_WITHOUT_PASSWORD;

        final LoginServletAction action = readProcessAction(pwmRequest);

        if (action != null) {
            Validator.validatePwmFormID(pwmRequest.getHttpServletRequest());

            switch (action) {
                case login:
                    processLogin(pwmRequest, passwordOnly);
                    break;

                case restLogin:
                    processRestLogin(pwmRequest, passwordOnly);
                    break;
            }

            return;
        }

        forwardToJSP(pwmRequest, passwordOnly);
    }

    private void processLogin(final PwmRequest pwmRequest, final boolean passwordOnly)
            throws PwmUnrecoverableException, ServletException, IOException, ChaiUnavailableException
    {
        final String username = pwmRequest.readStringParameter("username");
        final String password = pwmRequest.readStringParameter("password");
        final String context = pwmRequest.readStringParameter(PwmConstants.PARAM_CONTEXT);
        final String ldapProfile = pwmRequest.readStringParameter(PwmConstants.PARAM_LDAP_PROFILE);

        try {
            handleLoginRequest(pwmRequest, username, password, context, ldapProfile, passwordOnly);
        } catch (PwmOperationalException e) {
            pwmRequest.getPwmSession().getSessionStateBean().setSessionError(e.getErrorInformation());
            forwardToJSP(pwmRequest, passwordOnly);
            return;
        }

        // login has succeeded
        pwmRequest.sendRedirect(determinePostLoginUrl(pwmRequest));
    }

    private void processRestLogin(final PwmRequest pwmRequest, final boolean passwordOnly)
            throws PwmUnrecoverableException, ServletException, IOException, ChaiUnavailableException
    {
        final String bodyString = pwmRequest.readRequestBody();
        final Map<String, String> valueMap = Helper.getGson().fromJson(bodyString,
                new TypeToken<Map<String, String>>() {
                }.getType()
        );

        if (valueMap == null || valueMap.isEmpty()) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"missing json request body");
            pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation,pwmRequest.getLocale(),pwmRequest.getConfig()));
            return;
        }

        final Configuration config = pwmRequest.getConfig();
        final String username = Validator.sanatizeInputValue(config, valueMap.get("username"), 1024);
        final String password = Validator.sanatizeInputValue(config, valueMap.get("password"), 1024);
        final String context = Validator.sanatizeInputValue(config, valueMap.get(PwmConstants.PARAM_CONTEXT), 1024);
        final String ldapProfile = Validator.sanatizeInputValue(config, valueMap.get(PwmConstants.PARAM_LDAP_PROFILE),
                1024);

        try {
            handleLoginRequest(pwmRequest, username, password, context, ldapProfile, passwordOnly);
        } catch (PwmOperationalException e) {
            pwmRequest.outputJsonResult(RestResultBean.fromError(e.getErrorInformation(), pwmRequest.getLocale(),
                    pwmRequest.getConfig()));
            return;
        }

        // login has succeeded
        final RestResultBean restResultBean = new RestResultBean();
        final HashMap<String,String> resultMap = new HashMap<>(Collections.singletonMap("nextURL", determinePostLoginUrl(pwmRequest)));
        restResultBean.setData(resultMap);
        pwmRequest.outputJsonResult(restResultBean);
    }

    private void handleLoginRequest(
            final PwmRequest pwmRequest,
            final String username,
            final String password,
            final String context,
            final String ldapProfile,
            final boolean passwordOnly
    )
            throws PwmOperationalException, ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        if (!passwordOnly && (username == null || username.isEmpty())) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"missing username parameter"));
        }

        if (password == null || password.isEmpty()) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER,"missing password parameter"));
        }

        if (passwordOnly) {
            final UserIdentity userIdentity = pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity();
            UserAuthenticator.authenticateUser(userIdentity, password, pwmRequest.getPwmSession(), pwmRequest.getPwmApplication(), pwmRequest.getHttpServletRequest().isSecure());
        } else {
            UserAuthenticator.authenticateUser(username, password, context, ldapProfile, pwmRequest.getPwmSession(), pwmRequest.getPwmApplication(), pwmRequest.getHttpServletRequest().isSecure());
        }

        // recycle the session to prevent session fixation attack.
        pwmRequest.recycleSessions();
    }

    private void forwardToJSP(
            final PwmRequest pwmRequest,
            final boolean passwordOnly
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmConstants.JSP_URL url = passwordOnly ? PwmConstants.JSP_URL.LOGIN_PW_ONLY : PwmConstants.JSP_URL.LOGIN;
        pwmRequest.forwardToJsp(url);
    }

    private static String determinePostLoginUrl(final PwmRequest pwmRequest) {
        final String originalURL = pwmRequest.getPwmSession().getSessionStateBean().getOriginalRequestURL();
        pwmRequest.getPwmSession().getSessionStateBean().setOriginalRequestURL(null);

        if (!PwmServletURLHelper.isLoginServlet(pwmRequest.getHttpServletRequest())) {
            return originalURL;
        } else {
            return pwmRequest.getHttpServletRequest().getContextPath();
        }
    }
}

