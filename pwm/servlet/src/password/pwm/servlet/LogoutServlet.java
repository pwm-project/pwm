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
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;

public class LogoutServlet extends TopServlet {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(LogoutServlet.class);

    protected void processRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final StringBuilder debugMsg = new StringBuilder();
        debugMsg.append("processing logout request from user");
        final boolean logoutDueToIdle = Validator.readBooleanFromRequest(req, "idle");
        if (logoutDueToIdle) {
            debugMsg.append(" due to client idle timeout");
        }

        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);

        LOGGER.debug(pwmSession,debugMsg);
        pwmSession.unauthenticateUser();

        { //if there is a session url, then use that to do a redirect.
            final String sessionLogoutURL = pwmSession.getSessionStateBean().getLogoutURL();
            if (sessionLogoutURL != null && sessionLogoutURL.length() > 0) {
                LOGGER.trace(pwmSession, "redirecting user to session parameter set logout url:" + sessionLogoutURL );
                resp.sendRedirect(SessionFilter.rewriteRedirectURL(sessionLogoutURL, req, resp));
                pwmSession.invalidate();
                return;
            }
        }

        { // if the logout url hasn't been set then try seeing if one has been configured.
            final String configuredLogoutURL = pwmApplication.getConfig().readSettingAsString(PwmSetting.URL_LOGOUT);
            if (configuredLogoutURL != null && configuredLogoutURL.length() > 0) {

                // construct params
                final StringBuilder logoutURL = new StringBuilder();
                logoutURL.append(configuredLogoutURL);
                logoutURL.append(configuredLogoutURL.contains("?") ? "&" : "?");
                logoutURL.append("idle=");
                logoutURL.append(logoutDueToIdle);
                logoutURL.append("&passwordModified=");
                logoutURL.append(pwmSession.getSessionStateBean().isPasswordModified());
                logoutURL.append("&publicOnly=");
                logoutURL.append(!pwmSession.getSessionStateBean().isPrivateUrlAccessed());
                String sessionForwardURL = pwmSession.getSessionStateBean().getForwardURL();
                if (sessionForwardURL != null && sessionForwardURL.length() > 0) {
                    logoutURL.append("&" + PwmConstants.PARAM_FORWARD_URL + "=");
                    logoutURL.append(URLEncoder.encode(sessionForwardURL, "UTF-8"));
                }

                LOGGER.trace(pwmSession, "redirecting user to configured logout url:" + logoutURL.toString());
                resp.sendRedirect(SessionFilter.rewriteRedirectURL(logoutURL.toString(), req, resp));
                pwmSession.invalidate();
                return;
            }
        }

        // if we didn't go anywhere yet, then show the pwm logout jsp
        forwardToJSP(req,resp);
        pwmSession.invalidate();
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        final String url = SessionFilter.rewriteURL('/' + PwmConstants.URL_JSP_LOGOUT, req, resp);
        this.getServletContext().getRequestDispatcher(url).forward(req, resp);
    }

}
