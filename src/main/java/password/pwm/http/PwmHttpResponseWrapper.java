/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.util.Validator;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.util.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.util.Arrays;

public class PwmHttpResponseWrapper {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmHttpResponseWrapper.class);

    private final HttpServletRequest httpServletRequest;
    private final HttpServletResponse httpServletResponse;
    private final Configuration configuration;

    public enum CookiePath {
        Application,
        Private,
        CurrentURL,

        ;

        String toStringPath(final HttpServletRequest httpServletRequest) {
            switch (this) {
                case Application:
                    return httpServletRequest.getServletContext().getContextPath() + "/";

                case Private:
                    return httpServletRequest.getServletContext().getContextPath() + PwmConstants.URL_PREFIX_PRIVATE;

                case CurrentURL:
                    return httpServletRequest.getRequestURI();

                default:
                    throw new IllegalStateException("undefined CookiePath type: " + this);
            }

        }
    }

    public enum Flag {
        NonHttpOnly,
        BypassSanitation,
    }

    protected PwmHttpResponseWrapper(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final Configuration configuration
    )
    {
        this.httpServletRequest = request;
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

    public void writeCookie(
            final String cookieName,
            final String cookieValue,
            final int seconds,
            final Flag... flags
    ) {
        writeCookie(cookieName, cookieValue, seconds, null, flags);
    }

    public void writeCookie(
            final String cookieName,
            final String cookieValue,
            final int seconds,
            final CookiePath path,
            final Flag... flags
    ) {
        if (this.getHttpServletResponse().isCommitted()) {
            LOGGER.warn("attempt to write cookie '" + cookieName + "' after response is committed");
        }
        boolean secureFlag;
        {

            final String configValue = configuration.readAppProperty(AppProperty.HTTP_COOKIE_DEFAULT_SECURE_FLAG);
            if (configValue == null || "auto".equalsIgnoreCase(configValue)) {
                secureFlag = this.httpServletRequest.isSecure();
                if (!secureFlag) {
                    final String siteURLstring = configuration.readSettingAsString(PwmSetting.PWM_SITE_URL);
                    if (siteURLstring != null && !siteURLstring.isEmpty()) {
                        if ("https".equals(URI.create(siteURLstring).getScheme())) {
                            secureFlag = true;
                        }
                    }
                }
            } else {
                secureFlag = Boolean.parseBoolean(configValue);
            }
        }

        final boolean httpOnly = flags == null || !Arrays.asList(flags).contains(Flag.NonHttpOnly);

        final String value;
        {
            if (cookieValue == null) {
                value = null;
            } else {
                if (flags != null && Arrays.asList(flags).contains(Flag.BypassSanitation)) {
                    value = StringUtil.urlEncode(cookieValue);
                } else {
                    value = StringUtil.urlEncode(
                            Validator.sanitizeHeaderValue(configuration, cookieValue)
                    );
                }
            }
        }

        final Cookie theCookie = new Cookie(cookieName, value);
        theCookie.setMaxAge(seconds >= -1 ? seconds : -1);
        theCookie.setHttpOnly(httpOnly);
        theCookie.setSecure(secureFlag);

        theCookie.setPath(path == null ? CookiePath.CurrentURL.toStringPath(httpServletRequest) : path.toStringPath(httpServletRequest));
        if (value != null && value.length() > 2000) {
            LOGGER.warn("writing large cookie to response: cookieName=" + cookieName + ", length=" + value.length());
        }
        this.getHttpServletResponse().addCookie(theCookie);
    }

    public void removeCookie(final String cookieName, final CookiePath path) {
        writeCookie(cookieName, null, 0, path);
    }
}
