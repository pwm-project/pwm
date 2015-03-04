package password.pwm.http.client;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

public class PwmHttpClientResponse implements Serializable {
    private final int statusCode;
    private final String statusPhrase;
    private final Map<String,String> headers;
    private final String body;

    public PwmHttpClientResponse(int statusCode, String statusPhrase, Map<String, String> headers, String body) {
        this.statusCode = statusCode;
        this.statusPhrase = statusPhrase;
        this.headers = headers == null ? Collections.<String,String>emptyMap() : Collections.unmodifiableMap(headers);;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusPhrase() {
        return statusPhrase;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public String toDebugString() {
        return PwmHttpClient.entityToDebugString("HTTP response status " + statusCode + " " + statusPhrase, headers, body);
    }

}
