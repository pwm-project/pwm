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

public abstract class PwmServletURLHelper {

    public static boolean isResourceURL(final HttpServletRequest req) {
        return checkIfStartsWithURL(req, "/public/resources/") ||
                checkIfMatchsURL(req, "/public/jsClientValues.jsp");
    }

    public static boolean isConfigManagerURL(final HttpServletRequest req) {
        return checkIfStartsWithURL(req, "/config/");
    }

    public static boolean isChangePasswordURL(final HttpServletRequest req) {
        return checkIfStartsWithURL(req, "/private/" + PwmConstants.URL_SERVLET_CHANGE_PASSWORD) ||
                checkIfStartsWithURL(req, "/public/" + PwmConstants.URL_SERVLET_CHANGE_PASSWORD);
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

        final String requestedURL = req.getRequestURI();
        if (requestedURL == null) {
            return false;
        }

        for (final String loopURL : url) {
            if (requestedURL.startsWith(req.getContextPath() + loopURL)) {
                return true;
            }
        }

        return false;
    }

    private static boolean checkIfMatchsURL(final HttpServletRequest req, final String... url) {
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
