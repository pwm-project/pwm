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

package password.pwm.http.servlet.resource;

import com.github.benmanes.caffeine.cache.Cache;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.data.ImmutableByteArray;
import password.pwm.http.servlet.PwmServlet;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;

@WebServlet(
        name = "ResourceFileServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + "/resources/*"
        }
)
public class ResourceFileServlet extends HttpServlet implements PwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ResourceFileServlet.class );

    public static final String RESOURCE_PATH = "/public/resources";
    static final String WEBJAR_BASE_URL_PATH = RESOURCE_PATH + "/webjars/";
    static final String WEBJAR_BASE_FILE_PATH = "META-INF/resources/webjars";

    public static final String THEME_CSS_PATH = "/themes/%THEME%/style.css";
    public static final String THEME_CSS_MOBILE_PATH = "/themes/%THEME%/mobileStyle.css";
    public static final String THEME_CSS_CONFIG_PATH = "/themes/%THEME%/configStyle.css";

    public static final String TOKEN_THEME = "%THEME%";
    public static final String EMBED_THEME = "embed";

    @Override
    protected void doGet( final HttpServletRequest req, final HttpServletResponse resp )
            throws IOException
    {
        try
        {
            final PwmRequest pwmRequest = PwmRequest.forRequest( req, resp );
            processAction( pwmRequest );
            return;
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.error( () -> "unable to satisfy request using standard mechanism, reverting to raw resource server" );
        }

        resp.sendError( 500, "unable to initialize request for resource url" );
    }

    protected void processAction( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        if ( pwmRequest.getMethod() != HttpMethod.GET )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_SERVICE_NOT_AVAILABLE,
                    "unable to process resource request for request method " + pwmRequest.getMethod() ) );
        }

        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final ResourceServletService resourceService = pwmDomain.getResourceServletService();
        final ResourceServletConfiguration resourceConfiguration = resourceService.getResourceServletConfiguration();

        final ResourceFileRequest resourceFileRequest = new ResourceFileRequest( pwmDomain.getConfig(), resourceConfiguration, pwmRequest.getHttpServletRequest() );
        final String requestURI = resourceFileRequest.getRequestURI();

        final FileResource file;
        {
            final Optional<FileResource> resolvedFile = doResolve( resourceService, resourceFileRequest, pwmRequest );

            if ( resolvedFile.isEmpty() )
            {
                return;
            }

            file = resolvedFile.get();
        }

        // Get content type by file name and set default GZIP support and content disposition.
        final String contentType = resourceFileRequest.getReturnContentType();
        final boolean acceptsGzip = resourceFileRequest.allowsCompression();

        final HttpServletResponse response = pwmRequest.getPwmResponse().getHttpServletResponse();

        if ( respondWithNotModified( pwmRequest, resourceConfiguration ) )
        {
            return;
        }

        // Initialize response.
        addExpirationHeaders( resourceConfiguration, response );
        response.setHeader(  HttpHeader.ETag.getHttpName(), resourceConfiguration.getNonceValue() );
        response.setContentType( contentType );

        try
        {
            boolean fromCache = false;
            String debugText;
            try
            {
                fromCache = handleCacheableResponse( resourceFileRequest, response, resourceService.getCacheMap() );
                debugText = makeDebugText( fromCache, acceptsGzip, false );
            }
            catch ( final UncacheableResourceException e )
            {
                handleUncachedResponse( response, file, acceptsGzip );
                debugText = makeDebugText( fromCache, acceptsGzip, true );
            }

            pwmRequest.debugHttpRequestToLog( debugText, TimeDuration.fromCurrent( pwmRequest.getRequestStartTime() ) );

            StatisticsClient.incrementStat( pwmDomain, Statistic.HTTP_RESOURCE_REQUESTS );
            resourceService.getAverageStats().update( ResourceServletService.AverageStat.cacheHitRatio, fromCache ? 1 : 0 );
            resourceService.getAverageStats().update( ResourceServletService.AverageStat.avgResponseTimeMS, TimeDuration.fromCurrent( startTime ).asDuration() );
            resourceService.getCountingStats().increment( ResourceServletService.CountingStat.requestsServed );
            resourceService.getCountingStats().increment( ResourceServletService.CountingStat.bytesServed, file.length() );
        }
        catch ( final Exception e )
        {
            final Supplier<CharSequence> msg = () -> "error fulfilling response for url '" + requestURI + "', error: " + e.getMessage();
            if ( e instanceof IOException )
            {
                LOGGER.trace( pwmRequest, msg );
            }
            else
            {
                LOGGER.error( pwmRequest, msg );
            }

        }
    }

    private Optional<FileResource> doResolve(
            final ResourceServletService resourceService,
            final ResourceFileRequest resourceFileRequest,
            final PwmRequest pwmRequest

    )
            throws IOException
    {
        try
        {
            final Optional<FileResource> resolvedFile = resourceFileRequest.getRequestedFileResource();

            if ( resolvedFile.isEmpty() )
            {
                pwmRequest.getPwmResponse().getHttpServletResponse().sendError( HttpServletResponse.SC_NOT_FOUND );
                resourceService.getCountingStats().increment( ResourceServletService.CountingStat.requestsNotFound );

                try
                {
                    pwmRequest.debugHttpRequestToLog( "returning HTTP 404 status", null );
                }
                catch ( final PwmUnrecoverableException e )
                {
                    /* noop */
                }
                return Optional.empty();
            }

            return resolvedFile;
        }
        catch ( final PwmUnrecoverableException e )
        {
            pwmRequest.getPwmResponse().getHttpServletResponse().sendError( HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage() );
            try
            {
                pwmRequest.debugHttpRequestToLog( "returning HTTP 500 status", null );
            }
            catch ( final PwmUnrecoverableException e2 )
            {
                /* noop */
            }
        }

        return Optional.empty();
    }

    private String makeDebugText( final boolean fromCache, final boolean acceptsGzip, final boolean uncacheable )
    {
        if ( uncacheable )
        {
            final StringBuilder debugText = new StringBuilder();
            debugText.append( "(uncacheable" );
            if ( acceptsGzip )
            {
                debugText.append( ", gzip" );
            }
            debugText.append( ')' );
            return debugText.toString();
        }

        if ( fromCache || acceptsGzip )
        {
            final StringBuilder debugText = new StringBuilder();
            debugText.append( '(' );
            if ( fromCache )
            {
                debugText.append( "cached" );
            }
            if ( fromCache && acceptsGzip )
            {
                debugText.append( ", " );
            }
            if ( acceptsGzip )
            {
                debugText.append( "gzip" );
            }
            debugText.append( ')' );
            return debugText.toString();
        }
        else
        {
            return "(not cached)";
        }
    }

    private boolean handleCacheableResponse(
            final ResourceFileRequest resourceFileRequest,
            final HttpServletResponse response,
            final Cache<CacheKey, CacheEntry> responseCache
    )
            throws UncacheableResourceException, IOException, PwmUnrecoverableException
    {
        final FileResource file = resourceFileRequest.getRequestedFileResource().orElseThrow();

        if ( file.length() > resourceFileRequest.getResourceServletConfiguration().getMaxCacheBytes() )
        {
            throw new UncacheableResourceException( "file to large to cache" );
        }

        boolean fromCache = false;
        final CacheKey cacheKey = CacheKey.createCacheKey( file, resourceFileRequest.allowsCompression() );
        CacheEntry cacheEntry = responseCache.getIfPresent( cacheKey );
        if ( cacheEntry == null )
        {
            final Map<String, String> headers = new HashMap<>();
            final ByteArrayOutputStream tempOutputStream = new ByteArrayOutputStream();

            try ( InputStream input = file.getInputStream() )
            {
                if ( resourceFileRequest.allowsCompression() )
                {
                    try ( GZIPOutputStream gzipOutputStream = new GZIPOutputStream( tempOutputStream ) )
                    {
                        headers.put( HttpHeader.ContentEncoding.getHttpName(), "gzip" );
                        JavaHelper.copy( input, gzipOutputStream );
                    }
                }
                else
                {
                    JavaHelper.copy( input, tempOutputStream );
                }
            }

            final ImmutableByteArray entity = ImmutableByteArray.of( tempOutputStream.toByteArray() );
            headers.put( HttpHeader.ContentLength.getHttpName(), String.valueOf( entity.size() ) );
            cacheEntry = new CacheEntry( entity, headers );
        }
        else
        {
            fromCache = true;
        }

        responseCache.put( cacheKey, cacheEntry );
        for ( final String key : cacheEntry.getHeaderStrings().keySet() )
        {
            response.setHeader( key, cacheEntry.getHeaderStrings().get( key ) );
        }

        try ( OutputStream responseOutputStream = response.getOutputStream() )
        {
            JavaHelper.copy( cacheEntry.getEntity().newByteArrayInputStream(), responseOutputStream );
        }

        return fromCache;
    }

    private static void handleUncachedResponse(
            final HttpServletResponse response,
            final FileResource file,
            final boolean acceptsGzip
    )
            throws IOException
    {
        try (
                OutputStream output = new BufferedOutputStream( response.getOutputStream() );
                InputStream input = new BufferedInputStream( file.getInputStream() )
        )
        {
            if ( acceptsGzip )
            {
                response.setHeader( HttpHeader.ContentEncoding.getHttpName(), "gzip" );
                JavaHelper.copy( input, new GZIPOutputStream( output ) );
            }
            else
            {
                // Content length is not directly predictable in case of GZIP.
                // So only add it if there is no means of GZIP, else browser will hang.
                if ( file.length() > 0 )
                {
                    response.setHeader( HttpHeader.ContentLength.getHttpName(), String.valueOf( file.length() ) );
                }
                JavaHelper.copy( input, output );
            }
        }

    }

    private void addExpirationHeaders( final ResourceServletConfiguration resourceServletConfiguration, final HttpServletResponse httpResponse )
    {
        httpResponse.setDateHeader( "Expires", System.currentTimeMillis() + ( resourceServletConfiguration.getCacheExpireSeconds() * 1000 ) );
        httpResponse.setHeader( "Cache-Control", "public, max-age=" + resourceServletConfiguration.getCacheExpireSeconds() );
        httpResponse.setHeader( "Vary", "Accept-Encoding" );
    }

    private boolean respondWithNotModified( final PwmRequest pwmRequest, final ResourceServletConfiguration resourceConfiguration )
    {
        final String eTagValue = resourceConfiguration.getNonceValue();
        final HttpServletResponse response = pwmRequest.getPwmResponse().getHttpServletResponse();

        final String ifNoneMatchValue = pwmRequest.readHeaderValueAsString( HttpHeader.If_None_Match );
        if ( ifNoneMatchValue != null && ifNoneMatchValue.equals( eTagValue ) )
        {
            // reply back with etag.
            response.reset();
            response.setStatus( HttpServletResponse.SC_NOT_MODIFIED );
            try
            {
                pwmRequest.debugHttpRequestToLog( "returning HTTP 304 status", null );
            }
            catch ( final PwmUnrecoverableException e2 )
            {
                /* noop */
            }
            return true;
        }

        return false;
    }
}
