/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.http.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
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
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.error.PwmUnrecoverableException;

import javax.net.ssl.SSLHandshakeException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class PwmHttpClientTest {
    @Rule public WireMockRule wm = new WireMockRule(wireMockConfig()
        .dynamicPort()
        .dynamicHttpsPort());

    // Create a few mock objects, in case they're needed by the tests
    private Configuration configuration = spy(new Configuration(StoredConfigurationImpl.newStoredConfiguration()));

    public PwmHttpClientTest() throws PwmUnrecoverableException {
    }

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
        HttpClient httpClient = PwmHttpClient.getHttpClient(configuration);

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

        HttpClient httpClient = PwmHttpClient.getHttpClient(configuration);

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

        HttpClient httpClient = PwmHttpClient.getHttpClient(configuration);

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

        PwmHttpClientConfiguration pwmHttpClientConfiguration = PwmHttpClientConfiguration.builder()
                .certificates(getWireMockSelfSignedCertificate())
                .build();

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

        HttpClient httpClient = PwmHttpClient.getHttpClient(configuration);

        // We are making a request to www.microfocus.com, but our server on localhost will receive it
        HttpGet httpGet = new HttpGet(String.format("http://www.microfocus.com/simpleHello"));
        HttpResponse response = httpClient.execute(httpGet);

        // Verify the response
        int responseStatusCode = response.getStatusLine().getStatusCode();
        assertThat(responseStatusCode).isEqualTo(200);

        String responseContent = IOUtils.toString(response.getEntity().getContent());
        assertThat(responseContent).startsWith("Hello");
    }

    private List<X509Certificate> getWireMockSelfSignedCertificate() {
        InputStream keystoreInputStream = WireMock.class.getResourceAsStream("/keystore");

        try {
            KeyStore keyStore  = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(keystoreInputStream, "password".toCharArray());
            final X509Certificate cert = (X509Certificate) keyStore.getCertificate("wiremock");
            return Collections.singletonList(cert);
        } catch (Exception e) {
            fail("Unable to load wiremock self-signed certificate", e);
        }

        return Collections.emptyList();
    }
}
