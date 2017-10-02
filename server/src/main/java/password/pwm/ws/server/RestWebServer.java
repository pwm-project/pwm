package password.pwm.ws.server;

import password.pwm.config.option.WebServiceUsage;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface RestWebServer {
    WebServiceUsage webService();
    boolean requireAuthentication();
}
