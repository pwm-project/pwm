/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

import org.apache.http.HttpStatus;
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
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.http.bean.ImmutableByteArray;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class JavaPwmHttpClient implements PwmHttpClientProvider
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( JavaPwmHttpClient.class );

    private PwmApplication pwmApplication;
    private HttpClientService httpClientService;
    private HttpClient httpClient;
    private TrustManager[] trustManagers;

    public JavaPwmHttpClient()
    {
    }

    public void init(
            final PwmApplication pwmApplication,
            final HttpClientService httpClientService,
            final PwmHttpClientConfiguration pwmHttpClientConfiguration
    )
            throws PwmUnrecoverableException
    {
        this.pwmApplication = Objects.requireNonNull( pwmApplication );
        this.httpClientService = Objects.requireNonNull( httpClientService );
        final AppConfig appConfig = pwmApplication.getConfig();
        final HttpTrustManagerHelper trustManagerHelper = new HttpTrustManagerHelper( pwmApplication.getConfig(), pwmHttpClientConfiguration );
        this.trustManagers = trustManagerHelper.makeTrustManager();

        try
        {
            final SSLContext sslContext = SSLContext.getInstance( "TLS" );
            sslContext.init( null, this.trustManagers, pwmApplication.getSecureService().pwmRandom() );

            final SSLParameters sslParameters = new SSLParameters();

            if ( !trustManagerHelper.hostnameVerificationEnabled() )
            {
                sslParameters.setEndpointIdentificationAlgorithm( null );
            }

            final int connectTimeoutMs = Integer.parseInt( appConfig.readAppProperty( AppProperty.HTTP_CLIENT_CONNECT_TIMEOUT_MS ) );
            final HttpClient.Builder builder = HttpClient.newBuilder()
                    .followRedirects( HttpClient.Redirect.NORMAL )
                    .connectTimeout( Duration.ofMillis( connectTimeoutMs ) )
                    .sslContext( sslContext )
                    .sslParameters( sslParameters );
            applyProxyConfig( pwmApplication, builder );
            this.httpClient = builder.build();
        }
        catch ( final NoSuchAlgorithmException | KeyManagementException e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, "error creating Java HTTP Client: " + e.getMessage() );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    @Override
    public void close() throws Exception
    {

    }

    @Override
    public boolean isOpen()
    {
        return true;
    }

    @Override
    public PwmHttpClientResponse makeRequest( final PwmHttpClientRequest clientRequest, final SessionLabel sessionLabel )
            throws PwmUnrecoverableException
    {
        try
        {
            final HttpRequest httpRequest = makeJavaHttpRequest( clientRequest );
            final HttpResponse<byte[]> response = httpClient.send( httpRequest, HttpResponse.BodyHandlers.ofByteArray() );
            final Optional<HttpContentType> httpContentType = contentTypeForResponse( response.headers() );

            final PwmHttpClientResponse.PwmHttpClientResponseBuilder builder = PwmHttpClientResponse.builder()
                    .statusCode( response.statusCode() )
                    .requestID( clientRequest.getRequestID() )
                    .headers( convertResponseHeaders( response.headers() ) );

            if ( response.body() != null )
            {
                if ( httpContentType.isPresent() && httpContentType.get().getDataType() == HttpEntityDataType.ByteArray )
                {
                    builder.dataType( HttpEntityDataType.ByteArray );
                    builder.binaryBody( ImmutableByteArray.of( response.body() ) );
                }
                else
                {
                    builder.dataType( HttpEntityDataType.String );
                    builder.body( new String( response.body(), PwmConstants.DEFAULT_CHARSET ) );
                }
            }
            httpClientService.getStats().increment( HttpClientService.StatsKey.requestBytes, clientRequest.size() );
            StatisticsClient.incrementStat( pwmApplication, Statistic.HTTP_CLIENT_REQUESTS );
            final PwmHttpClientResponse pwmHttpClientResponse = builder.build();
            httpClientService.getStats().increment( HttpClientService.StatsKey.responseBytes, pwmHttpClientResponse.size() );
            return pwmHttpClientResponse;

        }
        catch ( final IOException | InterruptedException exception )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_HTTP_CLIENT, exception.getMessage() );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    private static Optional<HttpContentType> contentTypeForResponse( final HttpHeaders httpHeaders )
    {
        if ( httpHeaders != null )
        {
            return httpHeaders.map().entrySet().stream()
                    .filter( entry ->  StringUtil.nullSafeEqualsIgnoreCase( entry.getKey(), HttpHeader.ContentType.getHttpName() ) )
                    .flatMap( entry -> entry.getValue().stream() )
                    .flatMap( headerValue -> StringUtil.splitAndTrim( headerValue, ";" ).stream() )
                    .flatMap( segmentValue -> HttpContentType.fromContentTypeHeader( segmentValue, null  ).stream() )
                    .findFirst();
        }



        return Optional.empty();
    }

    final HttpRequest makeJavaHttpRequest( final PwmHttpClientRequest clientRequest )
    {
        final HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder()
                .uri( URI.create( clientRequest.getUrl() ) );


        final HttpRequest.BodyPublisher bodyPublisher;
        if ( clientRequest.getDataType() == HttpEntityDataType.ByteArray  && clientRequest.getBinaryBody() != null )
        {
            bodyPublisher = HttpRequest.BodyPublishers.ofInputStream( () -> clientRequest.getBinaryBody().newByteArrayInputStream() );
        }
        else if ( clientRequest.getDataType() == HttpEntityDataType.String  && clientRequest.getBody() != null )
        {
            bodyPublisher = HttpRequest.BodyPublishers.ofString( clientRequest.getBody() );
        }
        else
        {
            bodyPublisher = HttpRequest.BodyPublishers.noBody();
        }

        httpRequestBuilder.method( clientRequest.getMethod().name(), bodyPublisher );

        if ( clientRequest.getHeaders() != null )
        {
            for ( final Map.Entry<String, String> headerEntry : clientRequest.getHeaders().entrySet() )
            {
                httpRequestBuilder.setHeader( headerEntry.getKey(), headerEntry.getValue() );
            }
        }

        httpRequestBuilder.setHeader( HttpHeader.UserAgent.getHttpName(), PwmConstants.PWM_APP_NAME );

        return httpRequestBuilder.build();
    }

    private static Map<String, String> convertResponseHeaders( final HttpHeaders httpHeaders )
    {
        final Map<String, String> returnHeaders = new HashMap<>();
        if ( httpHeaders != null )
        {
            for ( final String name : httpHeaders.map().keySet() )
            {
                returnHeaders.put( name, httpHeaders.firstValue( name ).orElseThrow() );
            }
        }

        return Collections.unmodifiableMap( returnHeaders );
    }

    private static void applyProxyConfig( final PwmApplication pwmApplication, final HttpClient.Builder builder )
    {
        final AppConfig appConfig = pwmApplication.getConfig();
        final String proxyUrl = appConfig.readSettingAsString( PwmSetting.HTTP_PROXY_URL );
        if ( StringUtil.notEmpty( proxyUrl ) )
        {
            final URI proxyURI = URI.create( proxyUrl );
            final String host = proxyURI.getHost();
            final int port = proxyURI.getPort();
            final InetSocketAddress inetSocketAddress = new InetSocketAddress( host, port );
            builder.proxy( ProxySelector.of( inetSocketAddress ) );
        }
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
            try
            {
                final HttpRequest httpRequest = makeJavaHttpRequest( PwmHttpClientRequest.builder().method( HttpMethod.GET ).url( inputUrl ).build() );
                final HttpResponse<InputStream> response = httpClient.send( httpRequest, HttpResponse.BodyHandlers.ofInputStream() );
                if ( response.statusCode() != HttpStatus.SC_OK )
                {
                    final String errorMsg = "error retrieving stream for url '" + inputUrl + "', remote response: " + response.statusCode();
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_HTTP_CLIENT, errorMsg );
                    LOGGER.error( errorInformation );
                    throw new PwmUnrecoverableException( errorInformation );
                }
                return response.body();
            }
            catch ( final IOException | InterruptedException exception )
            {
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_HTTP_CLIENT, exception.getMessage() );
                throw new PwmUnrecoverableException( errorInformation );
            }
        }

        throw new IllegalArgumentException( "unknown protocol type: " + url.getProtocol() );
    }

    @Override
    public List<X509Certificate> readServerCertificates()
            throws PwmUnrecoverableException
    {
        return PwmHttpClient.readServerCertificates( trustManagers );
    }

}
