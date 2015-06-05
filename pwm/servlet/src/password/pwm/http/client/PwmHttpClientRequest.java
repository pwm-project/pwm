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
