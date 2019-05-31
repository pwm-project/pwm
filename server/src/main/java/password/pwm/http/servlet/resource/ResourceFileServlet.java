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

package password.pwm.http.servlet.resource;

import com.github.benmanes.caffeine.cache.Cache;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.servlet.PwmServlet;
import password.pwm.util.EventRateMeter;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
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
            throws ServletException, IOException
    {
        PwmRequest pwmRequest = null;
        try
        {
            pwmRequest = PwmRequest.forRequest( req, resp );
        }
        catch ( PwmUnrecoverableException e )
        {
            LOGGER.error( "unable to satisfy request using standard mechanism, reverting to raw resource server" );
        }

        if ( pwmRequest != null )
        {
            try
            {
                processAction( pwmRequest );
            }
            catch ( PwmUnrecoverableException e )
            {
                LOGGER.error( pwmRequest, "error during resource servlet request processing: " + e.getMessage() );
            }
        }
        else
        {
            try
            {
                rawRequestProcessor( req, resp );
            }
            catch ( PwmUnrecoverableException e )
            {
                LOGGER.error( "error serving raw resource request: " + e.getMessage() );
            }
        }
    }

    private void rawRequestProcessor( final HttpServletRequest req, final HttpServletResponse resp )
            throws IOException, PwmUnrecoverableException
    {

        final ResourceFileRequest resourceFileRequest = new ResourceFileRequest( null, ResourceServletConfiguration.defaultConfiguration(), req );

        final FileResource file = resourceFileRequest.getRequestedFileResource();

        if ( file == null || !file.exists() )
        {
            resp.sendError( HttpServletResponse.SC_NOT_FOUND );
            return;
        }

        handleUncachedResponse( resp, file, false );
    }

    protected void processAction( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        if ( pwmRequest.getMethod() != HttpMethod.GET )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_SERVICE_NOT_AVAILABLE,
                    "unable to process resource request for request method " + pwmRequest.getMethod() ) );
        }

        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final ResourceServletService resourceService = pwmApplication.getResourceServletService();
        final ResourceServletConfiguration resourceConfiguration = resourceService.getResourceServletConfiguration();

        final ResourceFileRequest resourceFileRequest = new ResourceFileRequest( pwmApplication.getConfig(), resourceConfiguration, pwmRequest.getHttpServletRequest() );
        final String requestURI = resourceFileRequest.getRequestURI();

        final FileResource file;
        try
        {
            file = resourceFileRequest.getRequestedFileResource();
        }
        catch ( PwmUnrecoverableException e )
        {
            pwmRequest.getPwmResponse().getHttpServletResponse().sendError( HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage() );
            try
            {
                pwmRequest.debugHttpRequestToLog( "returning HTTP 500 status" );
            }
            catch ( PwmUnrecoverableException e2 )
            { /* noop */ }
            return;
        }

        if ( file == null || !file.exists() )
        {
            pwmRequest.getPwmResponse().getHttpServletResponse().sendError( HttpServletResponse.SC_NOT_FOUND );
            try
            {
                pwmRequest.debugHttpRequestToLog( "returning HTTP 404 status" );
            }
            catch ( PwmUnrecoverableException e )
            { /* noop */ }
            return;
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
            catch ( UncacheableResourceException e )
            {
                handleUncachedResponse( response, file, acceptsGzip );
                debugText = makeDebugText( fromCache, acceptsGzip, true );
            }

            pwmRequest.debugHttpRequestToLog( debugText );

            final EventRateMeter.MovingAverage cacheHitRatio = resourceService.getCacheHitRatio();
            StatisticsManager.incrementStat( pwmApplication, Statistic.HTTP_RESOURCE_REQUESTS );
            cacheHitRatio.update( fromCache ? 1 : 0 );
        }
        catch ( Exception e )
        {
            LOGGER.error( pwmRequest, "error fulfilling response for url '" + requestURI + "', error: " + e.getMessage() );
        }
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
            debugText.append( ")" );
            return debugText.toString();
        }

        if ( fromCache || acceptsGzip )
        {
            final StringBuilder debugText = new StringBuilder();
            debugText.append( "(" );
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
            debugText.append( ")" );
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
        final FileResource file = resourceFileRequest.getRequestedFileResource();

        if ( file.length() > resourceFileRequest.getResourceServletConfiguration().getMaxCacheBytes() )
        {
            throw new UncacheableResourceException( "file to large to cache" );
        }

        boolean fromCache = false;
        final CacheKey cacheKey = new CacheKey( file, resourceFileRequest.allowsCompression() );
        CacheEntry cacheEntry = responseCache.getIfPresent( cacheKey );
        if ( cacheEntry == null )
        {
            final Map<String, String> headers = new HashMap<>();
            final ByteArrayOutputStream tempOutputStream = new ByteArrayOutputStream();

            try ( InputStream input = resourceFileRequest.getRequestedFileResource().getInputStream() )
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

            final byte[] entity = tempOutputStream.toByteArray();
            headers.put( HttpHeader.ContentLength.getHttpName(), String.valueOf( entity.length ) );
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
            JavaHelper.copy( new ByteArrayInputStream( cacheEntry.getEntity() ), responseOutputStream );
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
                InputStream input = new BufferedInputStream( file.getInputStream() );
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
                pwmRequest.debugHttpRequestToLog( "returning HTTP 304 status" );
            }
            catch ( PwmUnrecoverableException e2 )
            { /* noop */ }
            return true;
        }

        return false;
    }
}
