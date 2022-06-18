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

package password.pwm.svc.httpclient;

import org.apache.commons.io.input.CountingInputStream;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpEntityDataType;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmURL;
import password.pwm.data.ImmutableByteArray;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ApachePwmHttpClient implements AutoCloseable, PwmHttpClientProvider
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ApachePwmHttpClient.class );

    private static final AtomicLoopIntIncrementer CLIENT_COUNTER = new AtomicLoopIntIncrementer();

    private final int clientID = CLIENT_COUNTER.next();

    private PwmApplication pwmApplication;
    private PwmHttpClientConfiguration pwmHttpClientConfiguration;
    private HttpClientService httpClientService;
    private SessionLabel sessionLabel;

    private TrustManager[] trustManagers;
    private CloseableHttpClient httpClient;


    private volatile boolean open = true;

    public ApachePwmHttpClient()
    {
    }

    public void init(
            final PwmApplication pwmApplication,
            final HttpClientService httpClientService,
            final PwmHttpClientConfiguration pwmHttpClientConfiguration,
            final SessionLabel sessionLabel
    )
            throws PwmUnrecoverableException
    {
        this.pwmApplication = Objects.requireNonNull( pwmApplication );
        this.sessionLabel = sessionLabel;
        this.httpClientService = Objects.requireNonNull( httpClientService );
        this.pwmHttpClientConfiguration = pwmHttpClientConfiguration;

        this.trustManagers = makeTrustManager( pwmApplication.getConfig(), pwmHttpClientConfiguration );
        this.httpClient = makeHttpClient( pwmApplication, pwmHttpClientConfiguration, this.trustManagers );
    }

    static HostnameVerifier hostnameVerifier( final HttpTrustManagerHelper httpTrustManagerHelper )
    {
        return httpTrustManagerHelper.hostnameVerificationEnabled()
                ? new DefaultHostnameVerifier()
                : NoopHostnameVerifier.INSTANCE;
    }

    @Override
    public void close()
    {
        LOGGER.trace( sessionLabel, () -> "closed client #" + clientID );
        try
        {
            httpClient.close();
        }
        catch ( final IOException e )
        {
            LOGGER.trace( sessionLabel, () -> "error closing ApacheHttpClient: " + e.getMessage() );
        }
        open = false;
    }

    public boolean isOpen()
    {
        return open;
    }

    private static TrustManager[] makeTrustManager(
            final AppConfig appConfig,
            final PwmHttpClientConfiguration pwmHttpClientConfiguration
    )
            throws PwmUnrecoverableException
    {
        final HttpTrustManagerHelper httpTrustManagerHelper = new HttpTrustManagerHelper( appConfig, pwmHttpClientConfiguration );
        return httpTrustManagerHelper.makeTrustManager();
    }

    private static CloseableHttpClient makeHttpClient(
            final PwmApplication pwmApplication,
            final PwmHttpClientConfiguration pwmHttpClientConfiguration,
            final TrustManager[] trustManagers
    )
            throws PwmUnrecoverableException
    {
        final AppConfig appConfig = pwmApplication.getConfig();
        final HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        clientBuilder.setUserAgent( PwmConstants.PWM_APP_NAME );
        final HttpTrustManagerHelper httpTrustManagerHelper = new HttpTrustManagerHelper( appConfig, pwmHttpClientConfiguration );

        try
        {
            final SSLContext sslContext = SSLContext.getInstance( "TLS" );
            sslContext.init(
                    null,
                    trustManagers,
                    new SecureRandom() );
            final SSLConnectionSocketFactory sslConnectionFactory = new SSLConnectionSocketFactory( sslContext, hostnameVerifier( httpTrustManagerHelper ) );
            final Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register( "https", sslConnectionFactory )
                    .register( "http", PlainConnectionSocketFactory.INSTANCE )
                    .build();
            final HttpClientConnectionManager ccm = new BasicHttpClientConnectionManager( registry );
            clientBuilder.setSSLHostnameVerifier( hostnameVerifier( httpTrustManagerHelper ) );
            clientBuilder.setSSLContext( sslContext );
            clientBuilder.setSSLSocketFactory( sslConnectionFactory );
            clientBuilder.setConnectionManager( ccm );
            clientBuilder.setConnectionManagerShared( true );
        }
        catch ( final Exception e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, "unexpected error creating promiscuous https client: " + e.getMessage() ) );
        }

        final String proxyUrl = appConfig.readSettingAsString( PwmSetting.HTTP_PROXY_URL );
        if ( proxyUrl != null && proxyUrl.length() > 0 )
        {
            final URI proxyURI = URI.create( proxyUrl );

            final String host = proxyURI.getHost();
            final int port = proxyURI.getPort();
            final HttpHost proxyHost = new HttpHost( host, port );

            final String userInfo = proxyURI.getUserInfo();
            if ( userInfo != null && userInfo.length() > 0 )
            {
                final String[] parts = userInfo.split( ":" );

                final String username = parts[ 0 ];
                final String password = ( parts.length > 1 ) ? parts[ 1 ] : "";

                final CredentialsProvider credsProvider = new BasicCredentialsProvider();
                credsProvider.setCredentials( new AuthScope( host, port ), new UsernamePasswordCredentials( username, password ) );
                clientBuilder.setDefaultCredentialsProvider( credsProvider );
                clientBuilder.setProxyAuthenticationStrategy( new ProxyAuthenticationStrategy() );
            }

            clientBuilder.setRoutePlanner( new ProxyRoutePlanner( proxyHost, appConfig ) );
        }

        clientBuilder.setDefaultRequestConfig( RequestConfig.copy( RequestConfig.DEFAULT )
                .setSocketTimeout( Integer.parseInt( appConfig.readAppProperty( AppProperty.HTTP_CLIENT_SOCKET_TIMEOUT_MS ) ) )
                .setConnectTimeout( Integer.parseInt( appConfig.readAppProperty( AppProperty.HTTP_CLIENT_CONNECT_TIMEOUT_MS ) ) )
                .setConnectionRequestTimeout( Integer.parseInt( appConfig.readAppProperty( AppProperty.HTTP_CLIENT_REQUEST_TIMEOUT_MS ) ) )
                .build() );

        return clientBuilder.build();
    }

    @Override
    public PwmHttpClientResponse makeRequest(
            final PwmHttpClientRequest clientRequest
    )
            throws PwmUnrecoverableException
    {
        try
        {
            return makeRequestImpl( clientRequest );
        }
        catch ( final IOException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SERVICE_UNREACHABLE, "error while making http request: " + e.getMessage() ), e );
        }
    }

    private PwmHttpClientResponse makeRequestImpl(
            final PwmHttpClientRequest clientRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        if ( LOGGER.isEnabled( PwmLogLevel.TRACE ) )
        {
            final String sslDebugText;
            if ( clientRequest.isHttps() )
            {
                final HttpTrustManagerHelper httpTrustManagerHelper = new HttpTrustManagerHelper( pwmApplication.getConfig(), pwmHttpClientConfiguration );
                sslDebugText = "using " + httpTrustManagerHelper.debugText();
            }
            else
            {
                sslDebugText = "";
            }

            LOGGER.trace( sessionLabel, () -> "client #" + clientID + " preparing to send "
                    + clientRequest.toDebugString( this, pwmApplication, pwmHttpClientConfiguration, sslDebugText ) );
        }

        final HttpResponse httpResponse = executeRequest( clientRequest );

        final PwmHttpClientResponse.PwmHttpClientResponseBuilder httpClientResponseBuilder = PwmHttpClientResponse.builder();
        httpClientResponseBuilder.requestID( clientRequest.getRequestID() );

        final Optional<HttpContentType> optionalHttpContentType = contentTypeForEntity( httpResponse.getEntity() );

        if ( httpResponse.getEntity() != null )
        {
            if ( optionalHttpContentType.isPresent() && optionalHttpContentType.get().getDataType() == HttpEntityDataType.ByteArray )
            {
                httpClientResponseBuilder.binaryBody( readBinaryEntityBody( httpResponse.getEntity() ) );
                httpClientResponseBuilder.dataType( HttpEntityDataType.ByteArray );
            }
            else
            {
                httpClientResponseBuilder.body( EntityUtils.toString( httpResponse.getEntity() ) );
                httpClientResponseBuilder.dataType( HttpEntityDataType.String );
            }
        }

        final Map<String, String> responseHeaders = new LinkedHashMap<>();
        if ( httpResponse.getAllHeaders() != null )
        {
            Arrays.stream( httpResponse.getAllHeaders() ).forEach( header -> responseHeaders.put( header.getName(), header.getValue() ) );
        }

        final PwmHttpClientResponse httpClientResponse = httpClientResponseBuilder
                .statusCode( httpResponse.getStatusLine().getStatusCode() )
                .contentType( optionalHttpContentType.orElse( HttpContentType.plain ) )
                .statusPhrase( httpResponse.getStatusLine().getReasonPhrase() )
                .headers( Collections.unmodifiableMap( responseHeaders ) )
                .build();

        final TimeDuration duration = TimeDuration.fromCurrent( startTime );
        httpClientService.getStats().increment( HttpClientService.StatsKey.responseBytes, httpClientResponse.size() );
        LOGGER.trace( sessionLabel, () -> "client #" + clientID + " received response (id=" + clientRequest.getRequestID() + ") in "
                + duration.asCompactString() + ": "
                + httpClientResponse.toDebugString( pwmApplication, pwmHttpClientConfiguration ) );
        return httpClientResponse;
    }

    private HttpResponse executeRequest( final PwmHttpClientRequest clientRequest )
            throws IOException, PwmUnrecoverableException
    {
        final String requestBody = clientRequest.getBody();

        final HttpRequestBase httpRequest;
        switch ( clientRequest.getMethod() )
        {
            case POST:
            {
                try
                {
                    httpRequest = new HttpPost( new URI( clientRequest.getUrl() ).toString() );
                    if ( requestBody != null && !requestBody.isEmpty() )
                    {
                        ( ( HttpPost ) httpRequest ).setEntity( new StringEntity( requestBody, PwmConstants.DEFAULT_CHARSET ) );
                    }
                }
                catch ( final URISyntaxException e )
                {
                    throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, "malformed url: " + clientRequest.getUrl() + ", error: " + e.getMessage() );
                }
            }
            break;

            case PUT:
                httpRequest = new HttpPut( clientRequest.getUrl() );
                if ( clientRequest.getBody() != null && !clientRequest.getBody().isEmpty() )
                {
                    ( ( HttpPut ) httpRequest ).setEntity( new StringEntity( requestBody, PwmConstants.DEFAULT_CHARSET ) );
                }
                break;

            case PATCH:
                httpRequest = new HttpPatch( clientRequest.getUrl() );
                if ( clientRequest.getBody() != null && !clientRequest.getBody().isEmpty() )
                {
                    ( ( HttpPatch ) httpRequest ).setEntity( new StringEntity( requestBody, PwmConstants.DEFAULT_CHARSET ) );
                }
                break;

            case GET:
                httpRequest = new HttpGet( clientRequest.getUrl() );
                break;

            case DELETE:
                httpRequest = new HttpDelete( clientRequest.getUrl() );
                break;

            case HEAD:
                httpRequest = new HttpHead( clientRequest.getUrl() );
                break;

            default:
                throw new IllegalStateException( "http method not yet implemented" );
        }

        if ( clientRequest.getHeaders() != null )
        {
            for ( final String key : clientRequest.getHeaders().keySet() )
            {
                final String value = clientRequest.getHeaders().get( key );
                httpRequest.addHeader( key, value );
            }
        }

        httpClientService.getStats().increment( HttpClientService.StatsKey.requests );
        httpClientService.getStats().increment( HttpClientService.StatsKey.requestBytes, clientRequest.size() );
        StatisticsClient.incrementStat( pwmApplication, Statistic.HTTP_CLIENT_REQUESTS );
        return httpClient.execute( httpRequest );
    }

    @Override
    public InputStream streamForUrl( final String inputUrl )
            throws IOException, PwmUnrecoverableException
    {
        final URL url = new URL( inputUrl );
        if ( "file".equals( url.getProtocol() ) )
        {
            return url.openStream();
        }

        if ( "http".equals( url.getProtocol() ) || "https".equals( url.getProtocol() ) )
        {

            final PwmHttpClientRequest pwmHttpClientRequest = PwmHttpClientRequest.builder()
                    .method( HttpMethod.GET )
                    .url( inputUrl )
                    .build();

            final HttpResponse httpResponse = executeRequest( pwmHttpClientRequest );
            if ( httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK )
            {
                final String errorMsg = "error retrieving stream for url '" + inputUrl + "', remote response: " + httpResponse.getStatusLine().toString();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_REMOTE_ERROR_VALUE, errorMsg );
                LOGGER.error( errorInformation );
                throw new PwmUnrecoverableException( errorInformation );
            }
            return httpResponse.getEntity().getContent();
        }

        throw new IllegalArgumentException( "unknown protocol type: " + url.getProtocol() );
    }

    private static class ProxyRoutePlanner implements HttpRoutePlanner
    {
        private final HttpHost proxyServer;
        private final AppConfig appConfig;

        ProxyRoutePlanner( final HttpHost proxyServer, final AppConfig appConfig )
        {
            this.proxyServer = proxyServer;
            this.appConfig = appConfig;
        }

        @Override
        public HttpRoute determineRoute(
                final HttpHost target,
                final HttpRequest request,
                final HttpContext context
        )
        {
            final String targetUri = target.toURI();

            final List<String> proxyExceptionUrls = appConfig.readSettingAsStringArray( PwmSetting.HTTP_PROXY_EXCEPTIONS );

            if ( PwmURL.testIfUrlMatchesAllowedPattern( targetUri, proxyExceptionUrls, null ) )
            {
                return new HttpRoute( target );
            }

            final boolean secure = "https".equalsIgnoreCase( target.getSchemeName() );
            return new HttpRoute( target, null, proxyServer, secure );
        }
    }

    private static Optional<HttpContentType> contentTypeForEntity( final HttpEntity httpEntity )
    {
        if ( httpEntity != null )
        {
            final Header header = httpEntity.getContentType();
            if ( header != null )
            {
                final HeaderElement[] headerElements = header.getElements();
                if ( headerElements != null )
                {
                    for ( final HeaderElement headerElement : headerElements )
                    {
                        if ( headerElement != null )
                        {
                            final String name = headerElement.getName();
                            if ( name != null )
                            {
                                final Optional<HttpContentType> httpContentType = HttpContentType.fromContentTypeHeader( name, null );
                                if ( httpContentType.isPresent() )
                                {
                                    return httpContentType;
                                }
                            }
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private ImmutableByteArray readBinaryEntityBody( final HttpEntity httpEntity )
            throws IOException
    {
        final long maxSize = JavaHelper.silentParseLong( pwmApplication.getConfig().readAppProperty( AppProperty.HTTP_CLIENT_RESPONSE_MAX_SIZE ), 100_000_000L );
        try ( CountingInputStream contentStream = new CountingInputStream( httpEntity.getContent() ) )
        {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream(  );
            JavaHelper.copyWhilePredicate( contentStream, baos, aLong -> contentStream.getByteCount() <= maxSize );
            return ImmutableByteArray.of( baos.toByteArray() );
        }
    }

    @Override
    public List<X509Certificate> readServerCertificates()
            throws PwmUnrecoverableException
    {
        return PwmHttpClient.readServerCertificates( trustManagers );
    }
}

