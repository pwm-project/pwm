package password.pwm.http;

public enum HttpMethod {
    POST,
    GET,
    DELETE,
    PUT,

    ;

    public static HttpMethod fromString(final String input) {
        for (final HttpMethod method : HttpMethod.values()) {
            if (method.toString().equalsIgnoreCase(input)) {
                return method;
            }
        }
        return null;
    }
}
