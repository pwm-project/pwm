package password.pwm.http.client;

import java.security.cert.X509Certificate;

public class PwmHttpClientConfiguration {
    private X509Certificate[] certificates;

    public PwmHttpClientConfiguration(X509Certificate[] certificate) {
        this.certificates = certificate;
    }

    public X509Certificate[] getCertificates() {
        return certificates;
    }
}
