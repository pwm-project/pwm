/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.util.EntityUtils;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.TimeDuration;
import password.pwm.util.X509Utils;
import password.pwm.util.logging.PwmLogger;

public class PwmHttpClient {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmHttpClient.class);

    private static int classCounter = 0;

    private final PwmApplication pwmApplication;
    private final SessionLabel sessionLabel;
    private final PwmHttpClientConfiguration pwmHttpClientConfiguration;

    public PwmHttpClient(PwmApplication pwmApplication, SessionLabel sessionLabel) {
        this.pwmApplication = pwmApplication;
        this.sessionLabel = sessionLabel;
        this.pwmHttpClientConfiguration = new PwmHttpClientConfiguration(null);
    }

    public PwmHttpClient(PwmApplication pwmApplication, SessionLabel sessionLabel, final PwmHttpClientConfiguration pwmHttpClientConfiguration) {
        this.pwmApplication = pwmApplication;
        this.sessionLabel = sessionLabel;
        this.pwmHttpClientConfiguration = pwmHttpClientConfiguration;
    }

    public static HttpClient getHttpClient(final Configuration configuration)
            throws PwmUnrecoverableException
    {
        return getHttpClient(configuration, new PwmHttpClientConfiguration(null));
    }

    public static HttpClient getHttpClient(final Configuration configuration, final PwmHttpClientConfiguration pwmHttpClientConfiguration)
    throws PwmUnrecoverableException
    {
        HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        clientBuilder.setUserAgent(PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION);

        try {
            if (Boolean.parseBoolean(configuration.readAppProperty(AppProperty.SECURITY_HTTP_PROMISCUOUS_ENABLE))) {
                clientBuilder.setSSLContext(promiscuousSSLContext());
                clientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
            } else if (pwmHttpClientConfiguration != null && pwmHttpClientConfiguration.getCertificates() != null) {
                SSLContext sslContext = SSLContext.getInstance("SSL");
                TrustManager trustManager = new X509Utils.CertMatchingTrustManager(configuration, pwmHttpClientConfiguration.getCertificates());
                sslContext.init(null, new TrustManager[]{ trustManager }, new SecureRandom());

                SSLConnectionSocketFactory sslConnectionFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
                Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create().register("https", sslConnectionFactory).build();
                HttpClientConnectionManager ccm = new BasicHttpClientConnectionManager(registry);

                clientBuilder.setSSLSocketFactory(sslConnectionFactory);
                clientBuilder.setConnectionManager(ccm);
            }
        } catch (Exception e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"unexpected error creating promiscuous https client: " + e.getMessage()));
        }

        final String proxyUrl = configuration.readSettingAsString(PwmSetting.HTTP_PROXY_URL);
        if (proxyUrl != null && proxyUrl.length() > 0) {
            URI proxyURI = URI.create(proxyUrl);
            String host = proxyURI.getHost();
            int port = proxyURI.getPort();

            clientBuilder.setProxy(new HttpHost(host, port));

            final String userInfo = proxyURI.getUserInfo();
            if (userInfo != null && userInfo.length() > 0) {
                String[] parts = userInfo.split(":");

                String username = parts[0];
                String password = (parts.length > 1) ? parts[1] : "";

                CredentialsProvider credsProvider = new BasicCredentialsProvider();
                credsProvider.setCredentials(new AuthScope(host, port), new UsernamePasswordCredentials(username, password));
                clientBuilder.setDefaultCredentialsProvider(credsProvider);
                clientBuilder.setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy());
            }
        }

        HttpClient httpClient = clientBuilder.build();
        return httpClient;
    }

    static String entityToDebugString(
            final String topLine,
            final Map<String, String> headers,
            final String body
    ) {
        final StringBuilder msg = new StringBuilder();
        msg.append(topLine);
        if (body == null || body.isEmpty()) {
            msg.append(" (no body)");
        }
        msg.append("\n");
        for (final String key : headers.keySet()) {
            msg.append("  header: ").append(key).append("=").append(headers.get(key)).append("\n");
        }
        if (body != null && !body.isEmpty()) {
            msg.append("  body: ").append(body);
        }

        return msg.toString();
    }

    public PwmHttpClientResponse makeRequest(final PwmHttpClientRequest request) throws PwmUnrecoverableException {
        try {
            return makeRequestImpl(request);
        } catch (URISyntaxException | IOException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_SERVICE_UNREACHABLE, "error while making http request: " + e.getMessage()), e);
        }
    }

    PwmHttpClientResponse makeRequestImpl(final PwmHttpClientRequest clientRequest)
            throws IOException, URISyntaxException, PwmUnrecoverableException {
        final Date startTime = new Date();
        final int counter = classCounter++;

        final String requestBody = clientRequest.getBody();

        final HttpRequestBase httpRequest;
        switch (clientRequest.getMethod()) {
            case POST:
                httpRequest = new HttpPost(new URI(clientRequest.getUrl()).toString());
                if (requestBody != null && !requestBody.isEmpty()) {
                    ((HttpPost) httpRequest).setEntity(new StringEntity(requestBody, PwmConstants.DEFAULT_CHARSET));
                }
                break;

            case PUT:
                httpRequest = new HttpPut(clientRequest.getUrl());
                if (clientRequest.getBody() != null && !clientRequest.getBody().isEmpty()) {
                    ((HttpPut) httpRequest).setEntity(new StringEntity(requestBody, PwmConstants.DEFAULT_CHARSET));
                }
                break;

            case GET:
                httpRequest = new HttpGet(clientRequest.getUrl());
                break;

            case DELETE:
                httpRequest = new HttpDelete(clientRequest.getUrl());
                break;

            default:
                throw new IllegalStateException("http method not yet implemented");
        }

        if (clientRequest.getHeaders() != null) {
            for (final String key : clientRequest.getHeaders().keySet()) {
                final String value = clientRequest.getHeaders().get(key);
                httpRequest.addHeader(key, value);
            }
        }

        final HttpClient httpClient = getHttpClient(pwmApplication.getConfig(), pwmHttpClientConfiguration);
        LOGGER.trace(sessionLabel, "preparing to send (id=" + counter + ") " + clientRequest.toDebugString());

        final HttpResponse httpResponse = httpClient.execute(httpRequest);
        final String responseBody = EntityUtils.toString(httpResponse.getEntity());
        final Map<String, String> responseHeaders = new LinkedHashMap<>();
        if (httpResponse.getAllHeaders() != null) {
            for (final Header header : httpResponse.getAllHeaders()) {
                responseHeaders.put(header.getName(), header.getValue());
            }
        }

        final PwmHttpClientResponse httpClientResponse = new PwmHttpClientResponse(
                httpResponse.getStatusLine().getStatusCode(),
                httpResponse.getStatusLine().getReasonPhrase(),
                responseHeaders,
                responseBody
        );

        final TimeDuration duration = TimeDuration.fromCurrent(startTime);
        LOGGER.trace(sessionLabel, "received response (id=" + counter + ") in " + duration.asCompactString() + ": " + httpClientResponse.toDebugString());
        return httpClientResponse;
    }

    protected static SSLContext promiscuousSSLContext() throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException {
        return new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
            public boolean isTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                return true;
            }
        }).build();
    }
}

