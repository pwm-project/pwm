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
import password.pwm.http.servlet.PwmServletDefinition;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.*;

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
        return isPwmServletURL(PwmServletDefinition.Login);
    }

    public boolean isResourceURL() {
        return checkIfStartsWithURL("/public/resources/") || isReferenceURL();
    }

    public boolean isReferenceURL() {
        return checkIfStartsWithURL("/public/reference/");
    }

    public boolean isLogoutURL() {
        return isPwmServletURL(PwmServletDefinition.Logout);
    }

    public boolean isCaptchaURL() {
        return isPwmServletURL(PwmServletDefinition.Captcha);
    }

    public boolean isForgottenPasswordServlet() {
        return isPwmServletURL(PwmServletDefinition.ForgottenPassword);
    }

    public boolean isForgottenUsernameServlet() {
        return isPwmServletURL(PwmServletDefinition.ForgottenUsername);
    }

    public boolean isUserActivationServlet() {
        return isPwmServletURL(PwmServletDefinition.ActivateUser);
    }

    public boolean isNewUserRegistrationServlet() {
        return isPwmServletURL(PwmServletDefinition.NewUser);
    }

    public boolean isOauthConsumer() {
        return isPwmServletURL(PwmServletDefinition.OAuthConsumer);
    }

    public boolean isPrivateUrl() {
        return checkIfStartsWithURL(PwmConstants.URL_PREFIX_PRIVATE + "/");
    }

    public boolean isPublicUrl() {
        return checkIfStartsWithURL(PwmConstants.URL_PREFIX_PUBLIC + "/");
    }

    public boolean isCommandServletURL() {
        return isPwmServletURL(PwmServletDefinition.Command);
    }

    public boolean isWebServiceURL() {
        return checkIfStartsWithURL("/public/rest/");
    }

    public boolean isConfigManagerURL() {
        return checkIfStartsWithURL("/private/config/");
    }

    public boolean isConfigGuideURL() {
        return isPwmServletURL(PwmServletDefinition.ConfigGuide);
    }

    public boolean isPwmServletURL(final PwmServletDefinition pwmServletDefinition) {
        return checkIfStartsWithURL(pwmServletDefinition.urlPatterns());
    }

    public boolean isChangePasswordURL() {
        return isPwmServletURL(PwmServletDefinition.ChangePassword);
    }

    public boolean isSetupResponsesURL() {
        return isPwmServletURL(PwmServletDefinition.SetupResponses);
    }

    public boolean isSetupOtpSecretURL() {
        return isPwmServletURL(PwmServletDefinition.SetupOtp);
    }

    public boolean isProfileUpdateURL() {
        return isPwmServletURL(PwmServletDefinition.UpdateProfile);
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

    public static List<String> splitPathString(final String input) {
        if (input == null) {
            return Collections.emptyList();
        }
        final List<String> urlSegments = new ArrayList<>(Arrays.asList(input.split("/")));
        for (Iterator<String> iterator = urlSegments.iterator(); iterator.hasNext(); ) {
            final String segment = iterator.next();
            if (segment == null || segment.isEmpty()) {
                iterator.remove();
            }
        }
        return urlSegments;
    }
}
