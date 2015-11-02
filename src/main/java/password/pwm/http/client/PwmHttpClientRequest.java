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

package password.pwm.http.client;

import password.pwm.http.HttpMethod;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Map;

public class PwmHttpClientRequest implements Serializable {
    private final HttpMethod method;
    private final String url;
    private final String body;
    private final Map<String,String> headers;
    private final X509Certificate[] trustedCertificates;

    public PwmHttpClientRequest(
            final HttpMethod method,
            final String url,
            final String body,
            final Map<String, String> headers
    ) {
        this.method = method;
        this.url = url;
        this.body = body;
        this.headers = headers == null ? Collections.<String,String>emptyMap() : Collections.unmodifiableMap(headers);
        this.trustedCertificates = null;
    }

    public PwmHttpClientRequest(
            final HttpMethod method,
            final String url,
            final String body,
            final Map<String, String> headers,
            final X509Certificate[] trustedCertificates
    ) {
        this.method = method;
        this.url = url;
        this.body = body;
        this.headers = headers == null ? Collections.<String,String>emptyMap() : Collections.unmodifiableMap(headers);
        this.trustedCertificates = trustedCertificates;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public String getBody() {
        return body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public X509Certificate[] getTrustedCertificates() {
        return trustedCertificates;
    }

    public String toDebugString() {
        return PwmHttpClient.entityToDebugString("HTTP " + method + " request to " + url, headers, body);
    }
}
