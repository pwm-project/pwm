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

package password.pwm.http.servlet.resource;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.io.output.NullOutputStream;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.http.PwmRequest;
import password.pwm.svc.PwmService;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.MovingAverage;
import password.pwm.util.java.Percent;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.ChecksumOutputStream;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ResourceServletService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ResourceServletService.class );


    private ResourceServletConfiguration resourceServletConfiguration;
    private Cache<CacheKey, CacheEntry> cache;
    private MovingAverage cacheHitRatio = new MovingAverage( 60 * 60 * 1000 );
    private String resourceNonce;
    private STATUS status = STATUS.NEW;

    private PwmApplication pwmApplication;

    public String getResourceNonce( )
    {
        return resourceNonce;
    }

    public Cache<CacheKey, CacheEntry> getCacheMap( )
    {
        return cache;
    }

    public MovingAverage getCacheHitRatio( )
    {
        return cacheHitRatio;
    }


    public long bytesInCache( )
    {
        final Map<CacheKey, CacheEntry> cacheCopy = new HashMap<>( cache.asMap() );
        long cacheByteCount = 0;
        for ( final CacheEntry cacheEntry : cacheCopy.values() )
        {
            if ( cacheEntry != null && cacheEntry.getEntity() != null )
            {
                cacheByteCount += cacheEntry.getEntity().length;
            }
        }
        return cacheByteCount;
    }

    public int itemsInCache( )
    {
        final Cache<CacheKey, CacheEntry> responseCache = getCacheMap();
        return ( int ) responseCache.estimatedSize();
    }

    public Percent cacheHitRatio( )
    {
        final BigDecimal numerator = BigDecimal.valueOf( getCacheHitRatio().getAverage() );
        final BigDecimal denominator = BigDecimal.ONE;
        return new Percent( numerator, denominator );
    }

    @Override
    public STATUS status( )
    {
        return status;
    }

    @Override
    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
        this.pwmApplication = pwmApplication;
        status = STATUS.OPENING;
        try
        {
            this.resourceServletConfiguration = ResourceServletConfiguration.createResourceServletConfiguration( pwmApplication );

            cache = Caffeine.newBuilder()
                    .maximumSize( resourceServletConfiguration.getMaxCacheItems() )
                    .build();

            status = STATUS.OPEN;
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error during cache initialization, will remain closed; error: " + e.getMessage() );
            status = STATUS.CLOSED;
            return;
        }

        try
        {
            resourceNonce = makeResourcePathNonce();
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error during nonce generation, will remain closed; error: " + e.getMessage() );
            status = STATUS.CLOSED;
        }
    }

    @Override
    public void close( )
    {

    }

    @Override
    public List<HealthRecord> healthCheck( )
    {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return null;
    }

    ResourceServletConfiguration getResourceServletConfiguration( )
    {
        return resourceServletConfiguration;
    }

    private String makeResourcePathNonce( )
            throws IOException
    {
        final boolean enablePathNonce = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.HTTP_RESOURCES_ENABLE_PATH_NONCE ) );
        if ( !enablePathNonce )
        {
            return "";
        }

        final Instant startTime = Instant.now();
        final String nonce = checksumAllResources( pwmApplication );
        LOGGER.debug( () -> "completed generation of nonce '" + nonce + "' in " + TimeDuration.fromCurrent( startTime ).asCompactString() );

        final String noncePrefix = pwmApplication.getConfig().readAppProperty( AppProperty.HTTP_RESOURCES_NONCE_PATH_PREFIX );
        return "/" + noncePrefix + nonce;
    }

    public boolean checkIfThemeExists( final PwmRequest pwmRequest, final String themeName )
            throws PwmUnrecoverableException
    {
        if ( themeName == null )
        {
            return false;
        }

        if ( themeName.equals( ResourceFileServlet.EMBED_THEME ) )
        {
            return true;
        }

        if ( !themeName.matches( pwmRequest.getConfig().readAppProperty( AppProperty.SECURITY_INPUT_THEME_MATCH_REGEX ) ) )
        {
            LOGGER.warn( pwmRequest, () -> "discarding suspicious theme name in request: " + themeName );
            return false;
        }

        final ServletContext servletContext = pwmRequest.getHttpServletRequest().getServletContext();

        final String[] testUrls = new String[]
                {
                        ResourceFileServlet.THEME_CSS_PATH,
                        ResourceFileServlet.THEME_CSS_MOBILE_PATH,
                };

        for ( final String testUrl : testUrls )
        {
            final String themePathUrl = ResourceFileServlet.RESOURCE_PATH + testUrl.replace( ResourceFileServlet.TOKEN_THEME, themeName );
            final FileResource resolvedFile = ResourceFileRequest.resolveRequestedResource(
                    pwmRequest.getConfig(),
                    servletContext,
                    themePathUrl,
                    getResourceServletConfiguration() );
            if ( resolvedFile != null && resolvedFile.exists() )
            {
                LOGGER.debug( pwmRequest, () -> "check for theme validity of '" + themeName + "' returned true" );
                return true;
            }
        }

        LOGGER.debug( pwmRequest, () -> "check for theme validity of '" + themeName + "' returned false" );
        return false;
    }

    private String checksumAllResources( final PwmApplication pwmApplication )
            throws IOException
    {
        try ( ChecksumOutputStream checksumStream = new ChecksumOutputStream( new NullOutputStream() ) )
        {
            checksumResourceFilePath( pwmApplication, checksumStream );

            for ( final FileResource fileResource : getResourceServletConfiguration().getCustomFileBundle().values() )
            {
                JavaHelper.copy( fileResource.getInputStream(), checksumStream );
            }

            if ( getResourceServletConfiguration().getZipResources() != null )
            {
                for ( final String key : getResourceServletConfiguration().getZipResources().keySet() )
                {
                    final ZipFile zipFile = getResourceServletConfiguration().getZipResources().get( key );
                    checksumStream.write( key.getBytes( PwmConstants.DEFAULT_CHARSET ) );
                    for ( Enumeration<? extends ZipEntry> zipEnum = zipFile.entries(); zipEnum.hasMoreElements(); )
                    {
                        final ZipEntry entry = zipEnum.nextElement();
                        JavaHelper.copy( zipFile.getInputStream( entry ), checksumStream );
                    }
                }
            }
            return checksumStream.checksum();
        }
    }

    private static void checksumResourceFilePath( final PwmApplication pwmApplication, final ChecksumOutputStream checksumStream )
    {
        if ( pwmApplication.getPwmEnvironment().getContextManager() != null )
        {
            try
            {
                final File webInfPath = pwmApplication.getPwmEnvironment().getContextManager().locateWebInfFilePath();
                if ( webInfPath != null && webInfPath.exists() )
                {
                    final File basePath = webInfPath.getParentFile();
                    if ( basePath != null && basePath.exists() )
                    {
                        final File resourcePath = new File( basePath.getAbsolutePath() + File.separator + "public" + File.separator + "resources" );
                        if ( resourcePath.exists() )
                        {
                            try ( ClosableIterator<FileSystemUtility.FileSummaryInformation> iter =
                                          FileSystemUtility.readFileInformation( Collections.singletonList( resourcePath ) ) )
                            {
                                while ( iter.hasNext()  )
                                {
                                    final FileSystemUtility.FileSummaryInformation fileSummaryInformation = iter.next();
                                    checksumStream.write( JavaHelper.longToBytes( fileSummaryInformation.getChecksum() ) );
                                }

                            }
                        }
                    }
                }
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "unable to generate resource path nonce: " + e.getMessage() );
            }
        }
    }
}
