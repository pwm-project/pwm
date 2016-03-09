package password.pwm.ws.server.rest;

import org.glassfish.jersey.message.DeflateEncoder;
import org.glassfish.jersey.message.GZipEncoder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.EncodingFilter;

public class PwmResourceConfig extends ResourceConfig {
    public PwmResourceConfig() {
        registerClasses(EncodingFilter.class, GZipEncoder.class, DeflateEncoder.class);
    }
}