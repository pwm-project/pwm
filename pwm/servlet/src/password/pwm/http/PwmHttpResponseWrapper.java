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
import password.pwm.Validator;
import password.pwm.config.Configuration;
import password.pwm.util.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Arrays;

public class PwmHttpResponseWrapper {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmHttpRequestWrapper.class);

    private final HttpServletResponse httpServletResponse;
    private final Configuration configuration;

    public enum Flag {
        NonHttpOnly
    }

    protected PwmHttpResponseWrapper(HttpServletResponse response, final Configuration configuration)
    {
        this.httpServletResponse = response;
        this.configuration = configuration;
    }

    public HttpServletResponse getHttpServletResponse()
    {
        return this.httpServletResponse;
    }

    public void sendRedirect(final String url)
            throws IOException
    {
        this.httpServletResponse.sendRedirect(Validator.sanitizeHeaderValue(configuration, url));
    }

    public boolean isCommitted() {
        return this.httpServletResponse.isCommitted();
    }

    public void setHeader(final PwmConstants.HttpHeader headerName, final String value) {
        this.httpServletResponse.setHeader(
                Validator.sanitizeHeaderValue(configuration, headerName.getHttpName()),
                Validator.sanitizeHeaderValue(configuration, value)
        );
    }

    public void addCookie(final Cookie cookie) {
        cookie.setValue(Validator.sanitizeHeaderValue(configuration, cookie.getValue()));
        httpServletResponse.addCookie(cookie);
    }

    public void setStatus(final int status) {
        httpServletResponse.setStatus(status);
    }

    public void setContentType(final PwmConstants.ContentTypeValue contentType) {
        this.getHttpServletResponse().setContentType(contentType.getHeaderValue());
    }

    public PrintWriter getWriter()
            throws IOException
    {
        return this.getHttpServletResponse().getWriter();
    }

    public OutputStream getOutputStream()
            throws IOException
    {
        return this.getHttpServletResponse().getOutputStream();
    }

    public void writeCookie(final String cookieName, final String cookieValue, final int seconds, final Flag... flags) {
        writeCookie(cookieName, cookieValue, seconds, null, flags);
    }

    public void writeCookie(final String cookieName, final String cookieValue, final int seconds, final String path, final Flag... flags) {
        final boolean httpOnly = flags == null || !Arrays.asList(flags).contains(Flag.NonHttpOnly);
        final Cookie theCookie = new Cookie(cookieName, cookieValue == null ? null : StringUtil.urlEncode(cookieValue));
        if (seconds > 0) {
            theCookie.setMaxAge(seconds);
        }
        theCookie.setHttpOnly(httpOnly);
        if (path != null) {
            theCookie.setPath(path);
        }
        this.getHttpServletResponse().addCookie(theCookie);
    }

    public void removeCookie(final String cookieName, final String path) {
        writeCookie(cookieName, null, 0, path);
    }
}
