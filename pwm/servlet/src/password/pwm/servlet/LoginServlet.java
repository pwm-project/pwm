/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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
import password.pwm.config.Configuration;
import password.pwm.config.Message;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmException;
import password.pwm.util.PwmLogger;

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
public class LoginServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(LoginServlet.class.getName());

// -------------------------- OTHER METHODS --------------------------

    public void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final String actionParam = Validator.readStringFromRequest(req, Constants.PARAM_ACTION_REQUEST, 1024);

        if (actionParam != null && actionParam.equalsIgnoreCase("login")) {
            final String username = Validator.readStringFromRequest(req, "username", 255);
            final String password = Validator.readStringFromRequest(req, "password", 255);
            final String context = Validator.readStringFromRequest(req, "context", 255);

            if (username.length() < 1 || password.length() < 1) {
                ssBean.setSessionError(new ErrorInformation(Message.ERROR_MISSING_PARAMETER));
                this.forwardToJSP(req, resp);
                return;
            }

            final boolean authSuccessful = AuthenticationFilter.authenticateUser(username, password, context, pwmSession, req.isSecure());

            if (!authSuccessful) {
                ssBean.setSessionError(new ErrorInformation(Message.ERROR_WRONGPASSWORD));
                this.forwardToJSP(req, resp);
                return;
            }

            //if there is a basic auth header, make sure its the same, otherwise, kill the session.
            final BasicAuthInfo basic = BasicAuthInfo.parseAuthHeader(req);
            if (basic != null) {
                boolean mismatch = false;
                final UserInfoBean uiBean = pwmSession.getUserInfoBean();
                if (!basic.getUsername().equalsIgnoreCase(uiBean.getUserID()) && !basic.getUsername().equalsIgnoreCase(uiBean.getUserDN())) {
                    LOGGER.info(pwmSession, "user " + uiBean.getUserDN() + " username mismatch between supplied username and username in basic auth header");
                    mismatch = true;
                }
                if (!basic.getPassword().equalsIgnoreCase(password)) {
                    LOGGER.info(pwmSession, "user " + uiBean.getUserDN() + " password mismatch between supplied password and password in basic auth header");
                    mismatch = true;
                }
                if (mismatch) {
                    pwmSession.unauthenticateUser();
                    ssBean.setSessionError(new ErrorInformation(Message.ERROR_USER_MISMATCH));
                    Helper.forwardToErrorPage(req, resp, this.getServletContext());
                    return;
                }
            }
        }

        // if user is already authenticated then redirect them elsewhere.
        if (ssBean.isAuthenticated()) {
            // see if there is a an original request url
            final String originalURL = ssBean.getOriginalRequestURL();

            if (originalURL != null && originalURL.indexOf(Constants.URL_SERVLET_LOGIN) == -1) {
                resp.sendRedirect(SessionFilter.rewriteRedirectURL(ssBean.getOriginalRequestURL(), req, resp));
            } else {
                final Configuration config = ContextManager.getContextManager(this.getServletContext()).getConfig();
                resp.sendRedirect(SessionFilter.rewriteRedirectURL(config.readSettingAsString(PwmSetting.URL_SERVET_RELATIVE), req, resp));
            }
        } else {
            forwardToJSP(req,resp);
        }
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {                                        
        final String url = SessionFilter.rewriteURL('/' + Constants.URL_JSP_LOGIN, req, resp);
        this.getServletContext().getRequestDispatcher(url).forward(req, resp);
    }
}

