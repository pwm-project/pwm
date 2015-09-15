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
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.ServletHelper;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@WebServlet(
        name="LogoutServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + "/logout",
                PwmConstants.URL_PREFIX_PRIVATE+ "/logout",
                PwmConstants.URL_PREFIX_PUBLIC + "/Logout",
                PwmConstants.URL_PREFIX_PRIVATE+ "/Logout",
        }
)
public class LogoutServlet extends AbstractPwmServlet {
    private static final PwmLogger LOGGER = PwmLogger.forClass(LogoutServlet.class);

    public enum LogoutAction implements AbstractPwmServlet.ProcessAction {
        showLogout,
        ;

        public Collection<HttpMethod> permittedMethods()
        {
            return Collections.singletonList(HttpMethod.GET);
        }
    }

    protected LogoutAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        try {
            return LogoutAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final LogoutAction logoutAction = readProcessAction(pwmRequest);
        if (logoutAction == LogoutAction.showLogout) {
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.LOGOUT);
            return;
        }

        final StringBuilder debugMsg = new StringBuilder();
        debugMsg.append("processing logout request from user");
        final boolean logoutDueToIdle = Boolean.parseBoolean(pwmRequest.readParameterAsString("idle"));
        if (logoutDueToIdle) {
            debugMsg.append(" due to client idle timeout");
        }

        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        LOGGER.debug(pwmSession, debugMsg);
        pwmSession.unauthenticateUser(pwmRequest);

        { //if there is a session url, then use that to do a redirect.
            final String sessionLogoutURL = pwmSession.getSessionStateBean().getLogoutURL();
            if (sessionLogoutURL != null && sessionLogoutURL.length() > 0) {
                LOGGER.trace(pwmSession, "redirecting user to session parameter set logout url: " + sessionLogoutURL );
                pwmRequest.sendRedirect(sessionLogoutURL);
                pwmRequest.invalidateSession();
                return;
            }
        }

        { // if the logout url hasn't been set then try seeing if one has been configured.
            final String configuredLogoutURL = pwmApplication.getConfig().readSettingAsString(PwmSetting.URL_LOGOUT);
            if (configuredLogoutURL != null && configuredLogoutURL.length() > 0) {

                // construct params
                final Map<String,String> logoutUrlParameters = new LinkedHashMap<>();
                logoutUrlParameters.put("idle", String.valueOf(logoutDueToIdle));
                logoutUrlParameters.put("passwordModified", String.valueOf(pwmSession.getSessionStateBean().isPasswordModified()));
                logoutUrlParameters.put("publicOnly", String.valueOf(!pwmSession.getSessionStateBean().isPrivateUrlAccessed()));

                {
                    final String sessionForwardURL = pwmSession.getSessionStateBean().getForwardURL();
                    if (sessionForwardURL != null && sessionForwardURL.length() > 0) {
                        logoutUrlParameters.put(
                                pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_PARAM_NAME_FORWARD_URL),
                                sessionForwardURL
                        );
                    }
                }

                final String logoutURL = ServletHelper.appendAndEncodeUrlParameters(configuredLogoutURL, logoutUrlParameters);

                LOGGER.trace(pwmSession, "redirecting user to configured logout url:" + logoutURL.toString());
                pwmRequest.sendRedirect(logoutURL.toString());
                pwmRequest.invalidateSession();
                return;
            }
        }

        // if we didn't go anywhere yet, then show the pwm logout jsp
        final String logoutURL = ServletHelper.appendAndEncodeUrlParameters(
                pwmRequest.getContextPath() + PwmServletDefinition.Logout.servletUrl(),
                Collections.singletonMap(PwmConstants.PARAM_ACTION_REQUEST, LogoutAction.showLogout.toString())
        );
        pwmRequest.sendRedirect(logoutURL);
        pwmRequest.invalidateSession();
    }
}
