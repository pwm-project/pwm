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

package password.pwm.util;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;

/**
 * Simple data object containing username/password info derived from a "Basic" Authorization HTTP Header.
 *
 * @author Jason D. Rivard
 */
public class BasicAuthInfo implements Serializable {

    private static final PwmLogger LOGGER = PwmLogger.forClass(BasicAuthInfo.class);

    private final String username;
    private final PasswordData password;

    /**
     * Extracts the basic auth info from the header
     *
     * @param pwmRequest http servlet request
     * @return a BasicAuthInfo object containing username/password, or null if the "Authorization" header doesn't exist or is malformed
     */
    public static BasicAuthInfo parseAuthHeader(
            final PwmApplication pwmApplication,
            final PwmRequest pwmRequest
    ) {
        final String authHeader = pwmRequest.readHeaderValueAsString(PwmConstants.HttpHeader.Authorization);

        if (authHeader != null) {
            if (authHeader.contains(PwmConstants.HTTP_BASIC_AUTH_PREFIX)) {
                // ***** Get the encoded username/chpass string
                // Strip off "Basic " from "Basic c2pvaG5zLmNzaTo=bm92ZWxs"
                final String toStrip = PwmConstants.HTTP_BASIC_AUTH_PREFIX+" ";
                final String encodedValue = authHeader.substring(toStrip.length(), authHeader.length());

                try {
                    // ***** Decode the username/chpass string
                    final String charSet = pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_BASIC_AUTH_CHARSET);
                    final String decoded = new String(StringUtil.base64Decode(encodedValue), charSet);

                    // The decoded string should now look something like:
                    //   "cn=user,o=company:chpass" or "user:chpass"
                    return parseHeaderString(decoded);
                } catch (Exception e) {
                    LOGGER.debug(pwmRequest, "error decoding auth header");
                }
            }
        }

        return null;
    }

    public static BasicAuthInfo parseHeaderString(final String input) {
        try {
            // The decoded string should now look something like:
            //   "cn=user,o=company:chpass" or "user:chpass"

            final int index = input.indexOf(":");
            if (index != -1) {
                // ***** Separate "username:chpass"
                final String username = input.substring(0, index);
                final PasswordData password = new PasswordData(input.substring(index + 1));
                return new BasicAuthInfo(username, password);
            } else {
                return new BasicAuthInfo(input, null);
            }
        } catch (Exception e) {
            LOGGER.error("error decoding auth header: " + e.getMessage());
            throw new IllegalArgumentException("invalid basic authentication input string: " + e.getMessage(), e);
        }
    }

    public String toAuthHeader()
            throws PwmUnrecoverableException
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(this.getUsername());
        sb.append(":");
        sb.append(this.getPassword().getStringValue());

        sb.replace(0, sb.length(), StringUtil.base64Encode(sb.toString().getBytes(PwmConstants.DEFAULT_CHARSET)));

        sb.insert(0, PwmConstants.HTTP_BASIC_AUTH_PREFIX+" ");

        return sb.toString();
    }

// --------------------------- CONSTRUCTORS ---------------------------

    public BasicAuthInfo(
            final String username,
            final PasswordData password
    ) {
        this.username = username;
        this.password = password;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public PasswordData getPassword() {
        return password;
    }

    public String getUsername() {
        return username;
    }

// ------------------------ CANONICAL METHODS ------------------------

    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BasicAuthInfo)) {
            return false;
        }

        final BasicAuthInfo basicAuthInfo = (BasicAuthInfo) o;

        return !(password != null ? !password.equals(basicAuthInfo.password) : basicAuthInfo.password != null) && !(username != null ? !username.equals(basicAuthInfo.username) : basicAuthInfo.username != null);
    }

    public int hashCode() {
        int result;
        result = (username != null ? username.hashCode() : 0);
        result = 29 * result + (password != null ? password.hashCode() : 0);
        return result;
    }
}

