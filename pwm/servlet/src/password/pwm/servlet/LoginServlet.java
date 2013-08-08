/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

package password.pwm.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.*;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.UserAuthenticator;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * User interaction servlet for form-based authentication.   Depending on how PWM is deployed,
 * users may or may not ever visit this servlet.   Generally, if PWM is behind iChain, or some
 * other SSO enabler using HTTP BASIC authentication, this form will not be invoked.
 *
 * @author Jason D. Rivard
 */
public class
        LoginServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(LoginServlet.class.getName());

// -------------------------- OTHER METHODS --------------------------

    public void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final String actionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);

        final boolean passwordOnly = ssBean.isAuthenticated() && pwmSession.getUserInfoBean().getAuthenticationType() == UserInfoBean.AuthenticationType.AUTH_WITHOUT_PASSWORD;

        if (actionParam != null && actionParam.equalsIgnoreCase("login")) {
            Validator.validatePwmFormID(req);
            final String username = Validator.readStringFromRequest(req, "username");
            final String password = Validator.readStringFromRequest(req, "password");
            final String context = Validator.readStringFromRequest(req, "context");

            if (!passwordOnly && (username.length() < 1)) {
                ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER));
                this.forwardToJSP(req, resp, passwordOnly);
                return;
            }

            if (password.length() < 1) {
                ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER));
                this.forwardToJSP(req, resp, passwordOnly);
                return;
            }

            try {
                if (passwordOnly) {
                    final String userDN = pwmSession.getUserInfoBean().getUserDN();
                    UserAuthenticator.authenticateUser(userDN, password, null, pwmSession,pwmApplication, req.isSecure());
                } else {
                    UserAuthenticator.authenticateUser(username, password, context, pwmSession, pwmApplication, req.isSecure());
                }

                // recycle the session to prevent session fixation attack.
                ServletHelper.recycleSessions(pwmSession, req);

                // see if there is a an original request url
                final String originalURL = ssBean.getOriginalRequestURL();
                ssBean.setOriginalRequestURL(null);

                if (originalURL != null && originalURL.indexOf(PwmConstants.URL_SERVLET_LOGIN) == -1) {
                    resp.sendRedirect(SessionFilter.rewriteRedirectURL(originalURL, req, resp));
                } else {
                    resp.sendRedirect(SessionFilter.rewriteRedirectURL(req.getContextPath(), req, resp));
                }
                return;
            } catch (PwmOperationalException e) {
                ssBean.setSessionError(e.getErrorInformation());
            }
        }

        // if user is already authenticated then redirect them elsewhere.
        this.forwardToJSP(req, resp, passwordOnly);
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final boolean passwordOnly
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final String url;
        if (passwordOnly) {
            url = SessionFilter.rewriteURL('/' + PwmConstants.URL_JSP_LOGIN_PW_ONLY, req, resp);
        } else {
            url = SessionFilter.rewriteURL('/' + PwmConstants.URL_JSP_LOGIN, req, resp);
        }

        this.getServletContext().getRequestDispatcher(url).forward(req, resp);
    }
}

