/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.http.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpHeader;

import javax.net.ssl.SSLHandshakeException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

public class PwmHttpClientTest
{
    @Rule
    public WireMockRule wireMockRule = new WireMockRule( WireMockConfiguration.wireMockConfig()
            .dynamicPort()
            .dynamicHttpsPort() );

    // Create a few mock objects, in case they're needed by the tests
    private Configuration configuration = Mockito.spy( new Configuration( StoredConfigurationImpl.newStoredConfiguration() ) );

    public PwmHttpClientTest() throws PwmUnrecoverableException
    {
    }

    /**
     * Test making a simple HTTP request from the client returned by PwmHttpClient.getHttpClient(...).
     */
    @Test
    public void testGetHttpClientSimpleHello() throws Exception
    {
        // Stub out our local HTTP server
        wireMockRule.stubFor( WireMock.get( WireMock.urlEqualTo( "/simpleHello" ) )
                .willReturn( WireMock.aResponse()
                        .withHeader( "Content-Type", "text/plain" )
                        .withBody( "PwmAbout from the local mock server" ) ) );

        // Obtain the HTTP client from PWM
        final HttpClient httpClient = PwmHttpClient.getHttpClient( configuration );

        // Execute the HTTP request
        final HttpGet httpGet = new HttpGet( String.format( "http://localhost:%d/simpleHello", wireMockRule.port() ) );
        final HttpResponse response = httpClient.execute( httpGet );

        // Verify the response
        final int responseStatusCode = response.getStatusLine().getStatusCode();
        Assert.assertEquals( responseStatusCode, 200 );

        final String responseContent = IOUtils.toString( response.getEntity().getContent() );
        Assert.assertTrue( responseContent.startsWith( "PwmAbout" ) );

        // Verify the HTTP server got called as expected
        wireMockRule.verify( WireMock.getRequestedFor( WireMock.urlEqualTo( "/simpleHello" ) )
                .withHeader( "User-Agent", WireMock.equalTo( PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION ) ) );
    }

    /**
     * Test making an SSL request without setting SECURITY_HTTP_PROMISCUOUS_ENABLE to true, or supplying any certificates.
     */
    @Test( expected = SSLHandshakeException.class )
    public void testGetHttpClientSslHelloFail() throws Exception
    {
        // Stub out our local HTTP server
        wireMockRule.stubFor( WireMock.get( WireMock.urlEqualTo( "/simpleHello" ) )
                .willReturn( WireMock.aResponse()
                        .withHeader( "Content-Type", "text/plain" )
                        .withBody( "PwmAbout from the local mock server" ) ) );

        final HttpClient httpClient = PwmHttpClient.getHttpClient( configuration );

        final HttpGet httpGet = new HttpGet( String.format( "https://localhost:%d/simpleHello", wireMockRule.httpsPort() ) );

        // This should throw an exception, since we're doing https without setting SECURITY_HTTP_PROMISCUOUS_ENABLE, or setting certificates
        httpClient.execute( httpGet );
    }

    /**
     * Test making an SSL request in promiscuous mode (no certificates needed).
     */
    @Test
    public void testGetHttpClientSslHello() throws Exception
    {
        // Stub out our local HTTP server
        wireMockRule.stubFor( WireMock.get( WireMock.urlEqualTo( "/simpleHello" ) )
                .willReturn( WireMock.aResponse()
                        .withHeader( "Content-Type", "text/plain" )
                        .withBody( "PwmAbout from the local mock server" ) ) );

        // Stub out some mock object behavior
        Mockito.when( configuration.readAppProperty( AppProperty.SECURITY_HTTP_PROMISCUOUS_ENABLE ) ).thenReturn( "true" );

        final HttpClient httpClient = PwmHttpClient.getHttpClient( configuration );

        final HttpGet httpGet = new HttpGet( String.format( "https://localhost:%d/simpleHello", wireMockRule.httpsPort() ) );
        final HttpResponse response = httpClient.execute( httpGet );

        // Verify the response
        final int responseStatusCode = response.getStatusLine().getStatusCode();
        Assert.assertEquals( responseStatusCode, 200 );

        final String responseContent = IOUtils.toString( response.getEntity().getContent() );
        Assert.assertTrue( responseContent.startsWith( "PwmAbout" ) );
    }

    /**
     * Test making an SSL request using the server's certificate.
     */
    @Test
    public void testGetHttpClientSslWithCertificates() throws Exception
    {
        // Stub out our local HTTP server
        wireMockRule.stubFor( WireMock.get( WireMock.urlEqualTo( "/simpleHello" ) )
                .willReturn( WireMock.aResponse()
                        .withHeader( HttpHeader.ContentType.getHttpName(), ContentType.TEXT_PLAIN.getMimeType() )
                        .withBody( "PwmAbout from the local mock server" ) ) );

        final PwmHttpClientConfiguration pwmHttpClientConfiguration = PwmHttpClientConfiguration.builder()
                .certificates( getWireMockSelfSignedCertificate() )
                .build();

        Mockito.when( configuration.readAppProperty( AppProperty.HTTP_CLIENT_ENABLE_HOSTNAME_VERIFICATION ) ).thenReturn( "false" );


        final HttpClient httpClient = PwmHttpClient.getHttpClient( configuration, pwmHttpClientConfiguration, null  );

        final HttpGet httpGet = new HttpGet( String.format( "https://localhost:%d/simpleHello", wireMockRule.httpsPort() ) );
        final HttpResponse response = httpClient.execute( httpGet );

        // Verify the response
        final int responseStatusCode = response.getStatusLine().getStatusCode();
        Assert.assertEquals( responseStatusCode, 200 );

        final String responseContent = IOUtils.toString( response.getEntity().getContent() );
        Assert.assertTrue( responseContent.startsWith( "PwmAbout" ) );
    }

    /**
     * Test making a request through a proxy.
     */
    @Test
    public void testGetHttpClientProxyHello() throws Exception
    {
        // Stub out our local HTTP server
        wireMockRule.stubFor( WireMock.get( WireMock.urlEqualTo( "/simpleHello" ) )
                .willReturn( WireMock.aResponse()
                        .withHeader( HttpHeader.ContentType.getHttpName(), ContentType.TEXT_PLAIN.getMimeType() )
                        .withBody( "PwmAbout from the local mock server" ) ) );

        // Stub out some mock object behavior
        Mockito.when( configuration.readSettingAsString( PwmSetting.HTTP_PROXY_URL ) ).thenReturn( String.format( "http://localhost:%d/simpleHello", wireMockRule.port() ) );

        final HttpClient httpClient = PwmHttpClient.getHttpClient( configuration );

        // We are making a request to www.microfocus.com, but our server on localhost will receive it
        final HttpGet httpGet = new HttpGet( "http://www.microfocus.com/simpleHello" );
        final HttpResponse response = httpClient.execute( httpGet );

        // Verify the response
        final int responseStatusCode = response.getStatusLine().getStatusCode();
        Assert.assertEquals( responseStatusCode, 200 );

        final String responseContent = IOUtils.toString( response.getEntity().getContent() );
        Assert.assertTrue( responseContent.startsWith( "PwmAbout" ) );
    }

    private List<X509Certificate> getWireMockSelfSignedCertificate()
    {
        final InputStream keystoreInputStream = WireMock.class.getResourceAsStream( "/keystore" );

        try
        {
            final KeyStore keyStore = KeyStore.getInstance( KeyStore.getDefaultType() );
            keyStore.load( keystoreInputStream, "password".toCharArray() );
            final X509Certificate cert = ( X509Certificate ) keyStore.getCertificate( "wiremock" );
            return Collections.singletonList( cert );
        }
        catch ( final Exception e )
        {
            Assert.fail( "Unable to load wiremock self-signed certificate: " + e.getMessage() );
        }

        return Collections.emptyList();
    }
}
