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

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.params.HttpProtocolParams;
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

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

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
        final DefaultHttpClient httpClient;
        try {
            if (Boolean.parseBoolean(configuration.readAppProperty(AppProperty.SECURITY_HTTP_PROMISCUOUS_ENABLE))) {
                httpClient = new DefaultHttpClient(makeConnectionManager(new X509Utils.PromiscuousTrustManager()));
            } else if (pwmHttpClientConfiguration != null && pwmHttpClientConfiguration.getCertificates() != null) {
                final TrustManager trustManager = new X509Utils.CertMatchingTrustManager(configuration,pwmHttpClientConfiguration.getCertificates());
                httpClient = new DefaultHttpClient(makeConnectionManager(trustManager));
            } else {
                httpClient = new DefaultHttpClient();
            }
        } catch (Exception e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"unexpected error creating promiscuous https client: " + e.getMessage()));
        }
        final String strValue = configuration.readSettingAsString(PwmSetting.HTTP_PROXY_URL);
        if (strValue != null && strValue.length() > 0) {
            final URI proxyURI = URI.create(strValue);

            final String host = proxyURI.getHost();
            final int port = proxyURI.getPort();
            final HttpHost proxy = new HttpHost(host, port);
            httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);

            final String username = proxyURI.getUserInfo();
            if (username != null && username.length() > 0) {
                final String password = (username.contains(":")) ? username.split(":")[1] : "";
                final UsernamePasswordCredentials passwordCredentials = new UsernamePasswordCredentials(username, password);
                httpClient.getCredentialsProvider().setCredentials(new AuthScope(host, port), passwordCredentials);
            }
        }
        final String userAgent = PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION;
        httpClient.getParams().setParameter(HttpProtocolParams.USER_AGENT, userAgent);
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

    private static ClientConnectionManager makeConnectionManager(TrustManager trustManager)
            throws NoSuchAlgorithmException, KeyManagementException
    {
        final SSLContext sslContext = SSLContext.getInstance("SSL");

        sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());

        final SSLSocketFactory sf = new SSLSocketFactory(sslContext);
        final HostnameVerifier hostnameVerifier = org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;

        sf.setHostnameVerifier((X509HostnameVerifier) hostnameVerifier);
        final Scheme httpsScheme = new Scheme("https", 443, sf);
        final SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(httpsScheme);

        return new SingleClientConnManager(schemeRegistry);
    }
}

