package password.pwm.http;

public enum HttpMethod {
    POST(false),
    GET(true),
    DELETE(false),
    PUT(false),

    ;

    private final boolean idempotent;

    HttpMethod(boolean idempotent) {
        this.idempotent = idempotent;
    }

    public static HttpMethod fromString(final String input) {
        for (final HttpMethod method : HttpMethod.values()) {
            if (method.toString().equalsIgnoreCase(input)) {
                return method;
            }
        }
        return null;
    }

    public boolean isIdempotent() {
        return idempotent;
    }
}
