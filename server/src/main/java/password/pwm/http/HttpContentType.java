package password.pwm.http;

import password.pwm.PwmConstants;
import password.pwm.util.java.StringUtil;

import java.nio.charset.Charset;

public enum HttpContentType {
    json("application/json", PwmConstants.DEFAULT_CHARSET),
    zip("application/zip"),
    xml("text/xml", PwmConstants.DEFAULT_CHARSET),
    csv("text/csv", PwmConstants.DEFAULT_CHARSET),
    javascript("text/javascript", PwmConstants.DEFAULT_CHARSET),
    plain("text/plain", PwmConstants.DEFAULT_CHARSET),
    html("text/html", PwmConstants.DEFAULT_CHARSET),
    form("application/x-www-form-urlencoded", PwmConstants.DEFAULT_CHARSET),
    png("image/png"),
    octetstream("application/octet-stream"),
    ;

    private final String mimeType;
    private final String charset;

    HttpContentType(final String mimeType, final Charset charset) {
        this.mimeType = mimeType;
        this.charset = charset.name();
    }

    HttpContentType(final String mimeType) {
        this.mimeType = mimeType;
        this.charset = null;
    }

    public String getHeaderValue() {
        if (charset == null) {
            return mimeType;
        }
        return mimeType + "; charset=" + charset;
    }

    public String getMimeType() {
        return this.mimeType;
    }

    public static HttpContentType fromContentTypeHeader(final String value) {
        if (StringUtil.isEmpty(value)) {
            return null;
        }

        for (final HttpContentType httpContentType : HttpContentType.values()) {
            if (value.equalsIgnoreCase(httpContentType.getMimeType())) {
                return httpContentType;
            }
        }

        return null;
    }
}
