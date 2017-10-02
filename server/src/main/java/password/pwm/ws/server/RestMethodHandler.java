package password.pwm.ws.server;

import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface RestMethodHandler {
    HttpContentType[] produces() default {};
    HttpContentType[] consumes() default {};
    HttpMethod[] method() default {};
}
