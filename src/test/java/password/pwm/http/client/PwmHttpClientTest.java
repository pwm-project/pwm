package password.pwm.http.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLHandshakeException;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.junit.Rule;
import org.junit.Test;

import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

public class PwmHttpClientTest {
    @Rule public WireMockRule wm = new WireMockRule(wireMockConfig()
        .dynamicPort()
        .dynamicHttpsPort());

    // Create a few mock objects, in case they're needed by the tests
    private Configuration configuration = mock(Configuration.class);
    private PwmHttpClientConfiguration pwmHttpClientConfiguration = mock(PwmHttpClientConfiguration.class);

    /**
     * Test making a simple HTTP request from the client returned by PwmHttpClient.getHttpClient(...)
     */
    @Test
    public void testGetHttpClient_simpleHello() throws Exception {
        // Stub out our local HTTP server
        wm.stubFor(get(urlEqualTo("/simpleHello"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "text/plain")
                .withBody("Hello from the local mock server")));

        // Obtain the HTTP client from PWM
        HttpClient httpClient = PwmHttpClient.getHttpClient(configuration, pwmHttpClientConfiguration);

        // Execute the HTTP request
        HttpGet httpGet = new HttpGet(String.format("http://localhost:%d/simpleHello", wm.port()));
        HttpResponse response = httpClient.execute(httpGet);

        // Verify the response
        int responseStatusCode = response.getStatusLine().getStatusCode();
        assertThat(responseStatusCode).isEqualTo(200);

        String responseContent = IOUtils.toString(response.getEntity().getContent());
        assertThat(responseContent).startsWith("Hello");

        // Verify the HTTP server got called as expected
        wm.verify(getRequestedFor(urlEqualTo("/simpleHello"))
            .withHeader("User-Agent", equalTo(PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION)));
    }

    /**
     * Test making an SSL request without setting SECURITY_HTTP_PROMISCUOUS_ENABLE to true, or supplying any certificates
     */
    @Test(expected = SSLHandshakeException.class)
    public void testGetHttpClient_sslHelloFail() throws Exception {
        // Stub out our local HTTP server
        wm.stubFor(get(urlEqualTo("/simpleHello"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "text/plain")
                .withBody("Hello from the local mock server")));

        HttpClient httpClient = PwmHttpClient.getHttpClient(configuration, pwmHttpClientConfiguration);

        HttpGet httpGet = new HttpGet(String.format("https://localhost:%d/simpleHello", wm.httpsPort()));

        // This should throw an exception, since we're doing https without setting SECURITY_HTTP_PROMISCUOUS_ENABLE, or setting certificates
        httpClient.execute(httpGet);
    }

    /**
     * Test making an SSL request in promiscuous mode (no certificates needed)
     */
    @Test
    public void testGetHttpClient_sslHello() throws Exception {
        // Stub out our local HTTP server
        wm.stubFor(get(urlEqualTo("/simpleHello"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "text/plain")
                .withBody("Hello from the local mock server")));

        // Stub out some mock object behavior
        when(configuration.readAppProperty(AppProperty.SECURITY_HTTP_PROMISCUOUS_ENABLE)).thenReturn("true");

        HttpClient httpClient = PwmHttpClient.getHttpClient(configuration, pwmHttpClientConfiguration);

        HttpGet httpGet = new HttpGet(String.format("https://localhost:%d/simpleHello", wm.httpsPort()));
        HttpResponse response = httpClient.execute(httpGet);

        // Verify the response
        int responseStatusCode = response.getStatusLine().getStatusCode();
        assertThat(responseStatusCode).isEqualTo(200);

        String responseContent = IOUtils.toString(response.getEntity().getContent());
        assertThat(responseContent).startsWith("Hello");
    }

    /**
     * Test making an SSL request using the server's certificate
     */
    @Test
    public void testGetHttpClient_sslWithCertificates() throws Exception {
     // Stub out our local HTTP server
        wm.stubFor(get(urlEqualTo("/simpleHello"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "text/plain")
                .withBody("Hello from the local mock server")));

        // Stub out some mock object behavior
        X509Certificate[] certificates = new X509Certificate[] { getWireMockSelfSignedCertificate() };
        when(pwmHttpClientConfiguration.getCertificates()).thenReturn(certificates);

        HttpClient httpClient = PwmHttpClient.getHttpClient(configuration, pwmHttpClientConfiguration);

        HttpGet httpGet = new HttpGet(String.format("https://localhost:%d/simpleHello", wm.httpsPort()));
        HttpResponse response = httpClient.execute(httpGet);

        // Verify the response
        int responseStatusCode = response.getStatusLine().getStatusCode();
        assertThat(responseStatusCode).isEqualTo(200);

        String responseContent = IOUtils.toString(response.getEntity().getContent());
        assertThat(responseContent).startsWith("Hello");
    }

    /**
     * Test making a request through a proxy
     */
    @Test
    public void testGetHttpClient_proxyHello() throws Exception {
        // Stub out our local HTTP server
        wm.stubFor(get(urlEqualTo("/simpleHello"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "text/plain")
                .withBody("Hello from the local mock server")));

        // Stub out some mock object behavior
        when(configuration.readSettingAsString(PwmSetting.HTTP_PROXY_URL)).thenReturn(String.format("http://localhost:%d/simpleHello", wm.port()));

        HttpClient httpClient = PwmHttpClient.getHttpClient(configuration, pwmHttpClientConfiguration);

        // We are making a request to www.microfocus.com, but our server on localhost will receive it
        HttpGet httpGet = new HttpGet(String.format("http://www.microfocus.com/simpleHello"));
        HttpResponse response = httpClient.execute(httpGet);

        // Verify the response
        int responseStatusCode = response.getStatusLine().getStatusCode();
        assertThat(responseStatusCode).isEqualTo(200);

        String responseContent = IOUtils.toString(response.getEntity().getContent());
        assertThat(responseContent).startsWith("Hello");
    }

    private X509Certificate getWireMockSelfSignedCertificate() {
        InputStream keystoreInputStream = WireMock.class.getResourceAsStream("/keystore");

        try {
            KeyStore keyStore  = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(keystoreInputStream, "password".toCharArray());
            return (X509Certificate) keyStore.getCertificate("wiremock");
        } catch (Exception e) {
            fail("Unable to load wiremock self-signed certificate", e);
        }

        return null;
    }
}
