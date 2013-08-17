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

package password.pwm.util;

import password.pwm.PwmConstants;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;

public abstract class PwmServletURLHelper {

    public static boolean isLoginServlet(final HttpServletRequest req) {
        return checkIfStartsWithURL(req, "/private/" + PwmConstants.URL_SERVLET_LOGIN);
    }

    public static boolean isResourceURL(final HttpServletRequest req) {
        return checkIfStartsWithURL(req, "/public/resources/") ||
                checkIfMatchesURL(req, "/public/jsClientValues.jsp");
    }

    public static boolean isLogoutURL(final HttpServletRequest req) {
        return checkIfStartsWithURL(req, "/private/" + PwmConstants.URL_SERVLET_LOGOUT) ||
                checkIfStartsWithURL(req, "/public/" + PwmConstants.URL_SERVLET_LOGOUT);
    }

    public static boolean isAdminUrl(final HttpServletRequest req) {
        return checkIfStartsWithURL(req, "/admin/");
    }

    public static boolean isPrivateUrl(final HttpServletRequest req) {
        return checkIfStartsWithURL(req, "/private/");
    }

    public static boolean isMenuURL(final HttpServletRequest req) {
        return checkIfMatchesURL(req, "/", "/private/" , "/public/");
    }

    public static boolean isCommandServletURL(final HttpServletRequest req) {
        return checkIfStartsWithURL(req, "/private/" + PwmConstants.URL_SERVLET_COMMAND) ||
                checkIfStartsWithURL(req, "/public/" + PwmConstants.URL_SERVLET_COMMAND);
    }

    public static boolean isWebServiceURL(final HttpServletRequest req) {
        return checkIfStartsWithURL(req, "/public/rest/");
    }

    public static boolean isConfigManagerURL(final HttpServletRequest req) {
        return checkIfStartsWithURL(req, "/config/ConfigManager", "/private/admin/ConfigManager");
    }

    public static boolean isConfigGuideURL(final HttpServletRequest req) {
        return checkIfStartsWithURL(req, "/config/" + PwmConstants.URL_SERVLET_CONFIG_GUIDE);
    }

    public static boolean isChangePasswordURL(final HttpServletRequest req) {
        return checkIfStartsWithURL(req, "/private/" + PwmConstants.URL_SERVLET_CHANGE_PASSWORD,
                "/public/" + PwmConstants.URL_SERVLET_CHANGE_PASSWORD);
    }

    public static boolean isSetupResponsesURL(final HttpServletRequest req) {
        return checkIfStartsWithURL(req, "/private/" + PwmConstants.URL_SERVLET_SETUP_RESPONSES);
    }

    public static boolean isProfileUpdateURL(final HttpServletRequest req) {
        return checkIfStartsWithURL(req, "/private/" + PwmConstants.URL_SERVLET_UPDATE_PROFILE);
    }

    private static boolean checkIfStartsWithURL(final HttpServletRequest req, final String... url) {
        if (req == null) {
            return false;
        }

        final URI originalRequestURI;
        try {
            final String originalRequestValue = (String)req.getAttribute(PwmConstants.REQUEST_ATTR_ORIGINAL_URI);
            originalRequestURI = new URI(originalRequestValue);
        } catch (URISyntaxException e) {
            return false;
        }

        final String servletRequestPath = originalRequestURI.getPath();
        if (servletRequestPath == null) {
            return false;
        }

        for (final String loopURL : url) {
            if (servletRequestPath.startsWith(req.getContextPath() + loopURL)) {
                return true;
            }
        }

        return false;
    }

    private static boolean checkIfMatchesURL(final HttpServletRequest req, final String... url) {
        if (req == null) {
            return false;
        }

        final String requestedURL = req.getRequestURI();
        if (requestedURL == null) {
            return false;
        }

        for (final String loopURL : url) {
            if (requestedURL.equals(req.getContextPath() + loopURL)) {
                return true;
            }
        }

        return false;
    }
}
