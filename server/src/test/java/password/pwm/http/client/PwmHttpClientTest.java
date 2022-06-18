/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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
import org.apache.http.entity.ContentType;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.StringValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.svc.httpclient.PwmHttpClient;
import password.pwm.svc.httpclient.PwmHttpClientConfiguration;
import password.pwm.svc.httpclient.PwmHttpClientRequest;
import password.pwm.svc.httpclient.PwmHttpClientResponse;
import password.pwm.util.PasswordData;
import password.pwm.util.java.StringUtil;
import password.pwm.util.localdb.TestHelper;
import password.pwm.util.secure.X509Utils;
import password.pwm.util.secure.self.SelfCertFactory;
import password.pwm.util.secure.self.SelfCertSettings;

import java.io.File;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PwmHttpClientTest
{
    private static final String CERT_PASSWORD = "password";
    private static final String CERT_ALIAS = "wiremock";
    private static final String CERT_HOSTNAME = "localhost";

    private X509Certificate httpsCertificate;

    @Rule
    public TemporaryFolder tempAppPath = new TemporaryFolder();

    @Rule
    public WireMockRule wireMockRule = initWireMock();


    private WireMockRule initWireMock()
    {
        try
        {
            return initWireMockImpl();
        }
        catch ( final Exception e )
        {
            throw new IllegalStateException( e );
        }
    }

    private WireMockRule initWireMockImpl()
            throws Exception
    {
        tempAppPath.create();

        final File httpsKeystoreFile = File.createTempFile(
                "pwm-" + PwmHttpClientTest.class.getName() + "-",
                ".jks",
                tempAppPath.newFolder() );

        final KeyStore keyStore = SelfCertFactory.generateNewCert(
                SelfCertSettings.builder().subjectAlternateName( CERT_HOSTNAME ).build(),
                null,
                new PasswordData( CERT_PASSWORD ),
                CERT_ALIAS );

        httpsCertificate = ( X509Certificate ) keyStore.getCertificate( CERT_ALIAS );

        X509Utils.outputKeystore( keyStore, httpsKeystoreFile, CERT_PASSWORD );

        return new WireMockRule( WireMockConfiguration.wireMockConfig()
                .keystorePassword( CERT_PASSWORD )
                .keystorePath( httpsKeystoreFile.getAbsolutePath() )
                .dynamicPort()
                .dynamicHttpsPort() );
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


        final String url = String.format( "http://localhost:%d/simpleHello", wireMockRule.port() );

        // Obtain the HTTP client
        final PwmApplication pwmDomain = TestHelper.makeTestPwmApplication( tempAppPath.newFolder(), makeAppConfig( url, false, false ) );
        final PwmHttpClient httpClient = pwmDomain.getHttpClientService().getPwmHttpClient( SessionLabel.TEST_SESSION_LABEL );

        // Execute the HTTP request
        final PwmHttpClientRequest pwmHttpClientRequest = PwmHttpClientRequest.builder().method( HttpMethod.GET ).url( url ).build();
        final PwmHttpClientResponse response = httpClient.makeRequest( pwmHttpClientRequest );

        // Verify the response
        final int responseStatusCode = response.getStatusCode();
        Assert.assertEquals( 200, responseStatusCode );

        final String responseContent = response.getBody();
        Assert.assertTrue( responseContent.startsWith( "PwmAbout" ) );

        // Verify the HTTP server got called as expected
        wireMockRule.verify( WireMock.getRequestedFor( WireMock.urlEqualTo( "/simpleHello" ) )
                .withHeader( "User-Agent", WireMock.equalTo( PwmConstants.PWM_APP_NAME ) ) );
    }

    /**
     * Test making an SSL request without setting SECURITY_HTTP_PROMISCUOUS_ENABLE to true, or supplying any certificates.
     */
    @Test( expected = PwmUnrecoverableException.class )
    public void testGetHttpClientSslHelloFail() throws Exception
    {
        // Stub out our local HTTP server
        wireMockRule.stubFor( WireMock.get( WireMock.urlEqualTo( "/simpleHello" ) )
                .willReturn( WireMock.aResponse()
                        .withHeader( "Content-Type", "text/plain" )
                        .withBody( "PwmAbout from the local mock server" ) ) );

        final String url = String.format( "https://localhost:%d/simpleHello", wireMockRule.httpsPort() );

        // Obtain the HTTP client
        final PwmApplication pwmDomain = TestHelper.makeTestPwmApplication( tempAppPath.newFolder(), makeAppConfig( null, false, false ) );
        final PwmHttpClient httpClient = pwmDomain.getHttpClientService().getPwmHttpClient( SessionLabel.TEST_SESSION_LABEL );

        // Execute the HTTP request
        final PwmHttpClientRequest pwmHttpClientRequest = PwmHttpClientRequest.builder().method( HttpMethod.GET ).url( url ).build();


        // This should throw an exception, since we're doing https without setting SECURITY_HTTP_PROMISCUOUS_ENABLE, or setting certificates
        httpClient.makeRequest( pwmHttpClientRequest );
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
                        .withHeader( HttpHeader.ContentType.getHttpName(), "text/plain" )
                        .withBody( "PwmAbout from the local mock server" ) ) );

        final String url = String.format( "https://localhost:%d/simpleHello", wireMockRule.httpsPort() );

        // Obtain the HTTP client
        final PwmApplication pwmApplication = TestHelper.makeTestPwmApplication( tempAppPath.newFolder(), makeAppConfig( null, true, false ) );
        final PwmHttpClient httpClient = pwmApplication.getHttpClientService().getPwmHttpClient(
                PwmHttpClientConfiguration.builder().trustManagerType( PwmHttpClientConfiguration.TrustManagerType.promiscuous ).build(), SessionLabel.TEST_SESSION_LABEL
        );

        // Execute the HTTP request
        final PwmHttpClientRequest pwmHttpClientRequest = PwmHttpClientRequest.builder().method( HttpMethod.GET ).url( url ).build();
        final PwmHttpClientResponse response = httpClient.makeRequest( pwmHttpClientRequest );

        final int responseStatusCode = response.getStatusCode();
        Assert.assertEquals( 200, responseStatusCode );

        final String responseContent = response.getBody();
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

        final String url = String.format( "https://localhost:%d/simpleHello", wireMockRule.httpsPort() );


        // Obtain the HTTP client
        final PwmApplication pwmDomain = TestHelper.makeTestPwmApplication( tempAppPath.newFolder(), makeAppConfig( null, false, true ) );
        final PwmHttpClient httpClient = pwmDomain.getHttpClientService().getPwmHttpClient(
                PwmHttpClientConfiguration.builder().trustManagerType( PwmHttpClientConfiguration.TrustManagerType.configuredCertificates )
                        .certificates( getWireMockSelfSignedCertificate() ).build(), SessionLabel.TEST_SESSION_LABEL
        );

        // Execute the HTTP request
        final PwmHttpClientRequest pwmHttpClientRequest = PwmHttpClientRequest.builder().method( HttpMethod.GET ).url( url ).build();
        final PwmHttpClientResponse response = httpClient.makeRequest( pwmHttpClientRequest );

        // Verify the response
        final int responseStatusCode = response.getStatusCode();
        Assert.assertEquals( 200, responseStatusCode );

        final String responseContent = response.getBody();
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
        final String proxyUrl = String.format( "http://localhost:%d/simpleHello", wireMockRule.port() );

        // Obtain the HTTP client
        final PwmApplication pwmDomain = TestHelper.makeTestPwmApplication( tempAppPath.newFolder(), makeAppConfig( proxyUrl, false, false ) );
        final PwmHttpClient httpClient = pwmDomain.getHttpClientService().getPwmHttpClient(
                PwmHttpClientConfiguration.builder().trustManagerType( PwmHttpClientConfiguration.TrustManagerType.configuredCertificates )
                        .certificates( getWireMockSelfSignedCertificate() ).build(), SessionLabel.TEST_SESSION_LABEL
        );


        // We are making a request to www.example.com, but our server on localhost will receive it
        final String url = "http://www.example.com/simpleHello";
        final PwmHttpClientRequest pwmHttpClientRequest = PwmHttpClientRequest.builder().method( HttpMethod.GET ).url( url ).build();
        final PwmHttpClientResponse response = httpClient.makeRequest( pwmHttpClientRequest );

        // Verify the response
        final int responseStatusCode = response.getStatusCode();
        Assert.assertEquals( 200, responseStatusCode );

        final String responseContent = response.getBody();
        Assert.assertTrue( responseContent.startsWith( "PwmAbout" ) );
    }

    private List<X509Certificate> getWireMockSelfSignedCertificate()
    {
        return Collections.singletonList( this.httpsCertificate );
    }

    private AppConfig makeAppConfig( final String proxyUrl, final boolean promiscuousEnable, final boolean disableHostnameVerification )
            throws PwmUnrecoverableException
    {
        final StoredConfigurationModifier modifier = StoredConfigurationFactory.newModifiableConfig();
        if ( StringUtil.notEmpty( proxyUrl ) )
        {
            modifier.writeSetting(
                    StoredConfigKey.forSetting( PwmSetting.HTTP_PROXY_URL, null, DomainID.systemId() ),
                    new StringValue( proxyUrl ), null );
        }

        {
            final List<String> array = new ArrayList<>();
            if ( disableHostnameVerification )
            {
                array.add( AppProperty.HTTP_CLIENT_ENABLE_HOSTNAME_VERIFICATION.getKey() + "=" + "false" );
            }

            if ( promiscuousEnable )
            {
                array.add( AppProperty.SECURITY_HTTP_PROMISCUOUS_ENABLE.getKey() + "=" + "true" );
            }
            modifier.writeSetting(
                    StoredConfigKey.forSetting( PwmSetting.APP_PROPERTY_OVERRIDES, null, DomainID.systemId() ),
                    new StringArrayValue( array ), null );

        }
        return new AppConfig( modifier.newStoredConfiguration() );
    }

}
