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
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmSession;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class PwmHttpClient {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmHttpClient.class);

    private static int classCounter = 0;

    private final PwmApplication pwmApplication;
    private final PwmSession pwmSession;

    public PwmHttpClient(PwmApplication pwmApplication, PwmSession pwmSession) {
        this.pwmApplication = pwmApplication;
        this.pwmSession = pwmSession;
    }

    public static HttpClient getHttpClient(final Configuration configuration) {
        DefaultHttpClient httpClient;
        try {
            ClientConnectionManager clientConnectionManager = ccm();
            httpClient = new DefaultHttpClient(ccm());
        } catch (Exception e) {
            e.printStackTrace();
            httpClient = new DefaultHttpClient();
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

    public PwmHttpClientResponse makeRequestImpl(final PwmHttpClientRequest clientRequest)
            throws IOException, URISyntaxException {
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

        final HttpClient httpClient = getHttpClient(pwmApplication.getConfig());
        LOGGER.trace(pwmSession, "preparing to send (id=" + counter + ") " + clientRequest.toDebugString());

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
        LOGGER.trace(pwmSession, "received response (id=" + counter + ") in " + duration.asCompactString() + ": " + httpClientResponse.toDebugString());
        return httpClientResponse;
    }

    private static ClientConnectionManager ccm()
            throws NoSuchAlgorithmException, KeyManagementException
    {
        SSLContext sslContext = SSLContext.getInstance("SSL");

        // set up a TrustManager that trusts everything
        sslContext.init(null, new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                System.out.println("getAcceptedIssuers =============");
                return new X509Certificate[0];
            }

            public void checkClientTrusted(X509Certificate[] certs,
                                           String authType) {
                System.out.println("checkClientTrusted =============");
            }

            public void checkServerTrusted(X509Certificate[] certs,
                                           String authType) {
                System.out.println("checkServerTrusted =============");
            }
        }}, new SecureRandom());

        SSLSocketFactory sf = new SSLSocketFactory(sslContext);
        HostnameVerifier hostnameVerifier = org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;

        sf.setHostnameVerifier((X509HostnameVerifier) hostnameVerifier);        Scheme httpsScheme = new Scheme("https", 443, sf);
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(httpsScheme);


        return new SingleClientConnManager(schemeRegistry);
    }
}

