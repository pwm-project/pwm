/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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
import password.pwm.util.java.StringUtil;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

    /**
     * Compare two uri strings for equality of 'base'.  Specifically, the schema, host and port
     * are compared for equality.
     * @param uri1
     * @param uri2
     * @return
     */
    public static boolean compareUriBase(final String uri1, final String uri2) {
        if (uri1 == null && uri2 == null) {
            return true;
        }

        if (uri1 == null || uri2 == null) {
            return false;
        }

        final URI parsedUri1 = URI.create(uri1);
        final URI parsedUri2 = URI.create(uri2);

        if (!StringUtil.equals(parsedUri1.getScheme(), parsedUri2.getScheme())) {
            return false;
        }

        if (!StringUtil.equals(parsedUri1.getHost(), parsedUri2.getHost())) {
            return false;
        }

        if (parsedUri1.getPort() != parsedUri2.getPort()) {
            return false;
        }

        return true;
    }

    public boolean isLoginServlet() {
        return isPwmServletURL(PwmServletDefinition.Login);
    }

    public boolean isResourceURL() {
        return checkIfStartsWithURL(PwmConstants.URL_PREFIX_PUBLIC + "/resources/") || isReferenceURL();
    }

    public boolean isReferenceURL() {
        return checkIfMatchesURL(PwmConstants.URL_PREFIX_PUBLIC + "/reference") || checkIfStartsWithURL(PwmConstants.URL_PREFIX_PUBLIC + "/reference/");
    }

    public boolean isLogoutURL() {
        return isPwmServletURL(PwmServletDefinition.Logout);
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

    public boolean isAdminUrl() {
        return isPwmServletURL(PwmServletDefinition.Admin);
    }

    public boolean isIndexPage() {
        return checkIfMatchesURL(
                "",
                "/",
                PwmConstants.URL_PREFIX_PRIVATE,
                PwmConstants.URL_PREFIX_PUBLIC,
                PwmConstants.URL_PREFIX_PRIVATE + "/",
                PwmConstants.URL_PREFIX_PUBLIC + "/"
        );
    }

    public boolean isPublicUrl() {
        return checkIfStartsWithURL(PwmConstants.URL_PREFIX_PUBLIC + "/");
    }

    public boolean isCommandServletURL() {
        return isPwmServletURL(PwmServletDefinition.PublicCommand)
                || isPwmServletURL(PwmServletDefinition.PrivateCommand);

    }

    public boolean isRestService() {
        return checkIfStartsWithURL(PwmConstants.URL_PREFIX_PUBLIC + "/rest/");

    }

    public boolean isConfigManagerURL() {
        return checkIfStartsWithURL(PwmConstants.URL_PREFIX_PRIVATE + "/config/");
    }

    public boolean isClientApiServlet() {
        return isPwmServletURL(PwmServletDefinition.ClientApi);
    }

    public boolean isConfigGuideURL() {
        return isPwmServletURL(PwmServletDefinition.ConfigGuide);
    }

    public boolean isPwmServletURL(final PwmServletDefinition pwmServletDefinition) {
        return checkIfStartsWithURL(pwmServletDefinition.urlPatterns());
    }

    public boolean isChangePasswordURL() {
        return isPwmServletURL(PwmServletDefinition.PrivateChangePassword)
                || isPwmServletURL(PwmServletDefinition.PublicChangePassword);
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

    public boolean isLocalizable() {
        return !isConfigGuideURL()
                && !isAdminUrl()
                && !isReferenceURL()
                && !isConfigManagerURL();
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
        for (final Iterator<String> iterator = urlSegments.iterator(); iterator.hasNext(); ) {
            final String segment = iterator.next();
            if (segment == null || segment.isEmpty()) {
                iterator.remove();
            }
        }
        return urlSegments;
    }

    public static String appendAndEncodeUrlParameters(
            final String inputUrl,
            final Map<String, String> parameters
    )
    {
        final StringBuilder output = new StringBuilder();
        output.append(inputUrl == null ? "" : inputUrl);

        if (parameters != null) {
            parameters.forEach((key, value) -> {
                final String encodedValue = value == null
                        ? ""
                        : StringUtil.urlEncode(value);

                output.append(output.toString().contains("?") ? "&" : "?");
                output.append(key);
                output.append("=");
                output.append(encodedValue);
            });
        }

        if (output.charAt(0) == '?' || output.charAt(0) == '&') {
            output.deleteCharAt(0);
        }

        return output.toString();
    }

    public static int portForUriSchema(final URI uri) {
        final int port = uri.getPort();
        if (port < 1) {
            return portForUriScheme(uri.getScheme());
        }
        return port;
    }

    private static int portForUriScheme(final String scheme) {
        if (scheme == null) {
            throw new NullPointerException("scheme cannot be null");
        }
        switch (scheme) {
            case "http":
                return 80;

            case "https":
                return 443;

            case "ldap":
                return 389;

            case "ldaps":
                return 636;

            default:
                throw new IllegalArgumentException("unknown scheme: " + scheme);
        }
    }

    public String getPostServletPath(final PwmServletDefinition pwmServletDefinition) {
        final String path = this.uri.getPath();
        for (final String pattern : pwmServletDefinition.urlPatterns()) {
            final String patternWithContext = this.contextPath + pattern;
            if (path.startsWith(patternWithContext)) {
                return path.substring(patternWithContext.length());
            }
        }
        return "";
    }

    public String determinePwmServletPath() {
        final String requestPath = this.uri.getPath();
        for (final PwmServletDefinition servletDefinition : PwmServletDefinition.values()) {
            for (final String pattern : servletDefinition.urlPatterns()) {
                final String testPath = contextPath + pattern;
                if (requestPath.startsWith(testPath)) {
                    return testPath;
                }
            }
        }
        return requestPath;
    }
}
