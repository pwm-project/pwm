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

package password.pwm.http;

import password.pwm.PwmConstants;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;

public class PwmURL {
    private URI uri;
    private String contextPath;

    public PwmURL(
            final URI uri,
            final String contextPath
    )
    {
        this.uri = uri;
        this.contextPath = contextPath;
    }

    public PwmURL(
            final HttpServletRequest req
    )
    {
        this(URI.create(req.getRequestURL().toString()), req.getContextPath());
    }

    public boolean isLoginServlet() {
        return checkIfStartsWithURL("/private/" + PwmConstants.URL_SERVLET_LOGIN);
    }

    public boolean isResourceURL() {
        return checkIfStartsWithURL("/public/resources/");
    }

    public boolean isReferenceURL() {
        return checkIfStartsWithURL("/public/reference/");
    }

    public boolean isLogoutURL() {
        return checkIfStartsWithURL("/private/" + PwmConstants.URL_SERVLET_LOGOUT)
                || checkIfStartsWithURL("/public/" + PwmConstants.URL_SERVLET_LOGOUT);
    }

    public boolean isCaptchaURL() {
        return checkIfStartsWithURL("/public/" + PwmConstants.URL_SERVLET_CAPTCHA);
    }

    public boolean isForgottenPasswordServlet() {
        return checkIfStartsWithURL("/public/" + PwmConstants.URL_SERVLET_RECOVER_PASSWORD);
    }

    public boolean isForgottenUsernameServlet() {
        return checkIfStartsWithURL("/public/" + PwmConstants.URL_SERVLET_RECOVER_USERNAME);
    }

    public boolean isUserActivationServlet() {
        return checkIfStartsWithURL("/public/" + PwmConstants.URL_SERVLET_USER_ACTIVATION);
    }

    public boolean isNewUserRegistrationServlet() {
        return checkIfStartsWithURL("/public/" + PwmConstants.URL_SERVLET_NEW_USER);
    }

    public boolean isPrivateUrl() {
        return checkIfStartsWithURL("/private/");
    }

    public boolean isPublicUrl() {
        return checkIfStartsWithURL("/public/");
    }

    public boolean isCommandServletURL() {
        return checkIfStartsWithURL("/private/" + PwmConstants.URL_SERVLET_COMMAND)
                || checkIfStartsWithURL("/public/" + PwmConstants.URL_SERVLET_COMMAND);
    }

    public boolean isWebServiceURL() {
        return checkIfStartsWithURL("/public/rest/");
    }

    public boolean isConfigManagerURL() {
        return checkIfStartsWithURL("/private/config/");
    }

    public boolean isConfigGuideURL() {
        return checkIfStartsWithURL("/private/config/" + PwmConstants.URL_SERVLET_CONFIG_GUIDE);
    }

    public boolean isChangePasswordURL() {
        return checkIfStartsWithURL("/private/" + PwmConstants.URL_SERVLET_CHANGE_PASSWORD,
                "/public/" + PwmConstants.URL_SERVLET_CHANGE_PASSWORD);
    }

    public boolean isSetupResponsesURL() {
        return checkIfStartsWithURL("/private/" + PwmConstants.URL_SERVLET_SETUP_RESPONSES);
    }

    public boolean isSetupOtpSecretURL() {
        return checkIfStartsWithURL("/private/" + PwmConstants.URL_SERVLET_SETUP_OTP_SECRET);
    }

    public boolean isProfileUpdateURL() {
        return checkIfStartsWithURL("/private/" + PwmConstants.URL_SERVLET_UPDATE_PROFILE);
    }

    public String toString() {
        return uri.toString();
    }

    private boolean checkIfStartsWithURL(final String... url) {
        final String servletRequestPath = uri.getPath();
        if (servletRequestPath == null) {
            return false;
        }

        for (final String loopURL : url) {
            if (servletRequestPath.startsWith(contextPath + loopURL)) {
                return true;
            }
        }

        return false;
    }

    private boolean checkIfMatchesURL(final String... url) {
        final String servletRequestPath = uri.getPath();
        if (servletRequestPath == null) {
            return false;
        }

        for (final String loopURL : url) {
            final String testURL = contextPath + loopURL;
            if (servletRequestPath.equals(testURL)) {
                return true;
            }
        }

        return false;
    }
}
