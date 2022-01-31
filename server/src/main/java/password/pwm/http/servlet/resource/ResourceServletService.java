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
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.io.output.NullOutputStream;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.http.PwmRequest;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.Percent;
import password.pwm.util.java.StatisticAverageBundle;
import password.pwm.util.java.StatisticCounterBundle;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ResourceServletService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ResourceServletService.class );

    private ResourceServletConfiguration resourceServletConfiguration;
    private Cache<CacheKey, CacheEntry> cache;
    private String resourceNonce = "";

    private PwmDomain pwmDomain;

    private final StatisticAverageBundle<AverageStat> averageStats = new StatisticAverageBundle<>( AverageStat.class );
    private final StatisticCounterBundle<CountingStat> countingStats = new StatisticCounterBundle<>( CountingStat.class );

    enum AverageStat
    {
        cacheHitRatio,
        avgResponseTimeMS,
    }

    enum CountingStat
    {
        requestsServed,
        requestsNotFound,
        bytesServed,
    }

    public String getResourceNonce( )
    {
        return resourceNonce;
    }

    public Cache<CacheKey, CacheEntry> getCacheMap( )
    {
        return cache;
    }

    StatisticAverageBundle<AverageStat> getAverageStats()
    {
        return averageStats;
    }

    StatisticCounterBundle<CountingStat> getCountingStats()
    {
        return countingStats;
    }

    public long bytesInCache( )
    {
        final Map<CacheKey, CacheEntry> cacheCopy = new HashMap<>( cache.asMap() );
        long cacheByteCount = 0;
        for ( final CacheEntry cacheEntry : cacheCopy.values() )
        {
            if ( cacheEntry != null && cacheEntry.getEntity() != null )
            {
                cacheByteCount += cacheEntry.getEntity().size();
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
        final BigDecimal numerator = BigDecimal.valueOf( averageStats.getAverage( AverageStat.cacheHitRatio ) );
        final BigDecimal denominator = BigDecimal.ONE;
        return Percent.of( numerator, denominator );
    }

    @Override
    protected Set<PwmApplication.Condition> openConditions()
    {
        return Collections.emptySet();
    }

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        this.pwmDomain = pwmApplication.domains().get( domainID );
        try
        {
            this.resourceServletConfiguration = ResourceServletConfiguration.fromConfig( getSessionLabel(), pwmDomain );

            cache = Caffeine.newBuilder()
                    .maximumSize( resourceServletConfiguration.getMaxCacheItems() )
                    .build();

            setStatus( STATUS.OPEN );
        }
        catch ( final Exception e )
        {
            LOGGER.error( getSessionLabel(), () -> "error during cache initialization, will remain closed; error: " + e.getMessage() );
            return STATUS.CLOSED;
        }

        try
        {
            final Instant start = Instant.now();
            resourceNonce = makeResourcePathNonce();
            LOGGER.trace( getSessionLabel(), () -> "calculated nonce", () -> TimeDuration.fromCurrent( start ) );
        }
        catch ( final Exception e )
        {
            LOGGER.error( getSessionLabel(), () -> "error during nonce generation, will remain closed; error: " + e.getMessage() );
            return STATUS.CLOSED;
        }

        return STATUS.OPEN;
    }

    @Override
    public void close( )
    {
        setStatus( STATUS.CLOSED );
    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        final Map<String, String> debugInfo = new HashMap<>();
        debugInfo.putAll( averageStats.debugStats() );
        debugInfo.putAll( countingStats.debugStats() );
        return ServiceInfoBean.builder()
                .debugProperties( debugInfo )
                .build();
    }

    ResourceServletConfiguration getResourceServletConfiguration( )
    {
        return resourceServletConfiguration;
    }

    private String makeResourcePathNonce( )
            throws IOException
    {
        final boolean enablePathNonce = Boolean.parseBoolean( pwmDomain.getConfig().readAppProperty( AppProperty.HTTP_RESOURCES_ENABLE_PATH_NONCE ) );
        if ( !enablePathNonce )
        {
            return "";
        }

        final Instant startTime = Instant.now();
        final String nonce = checksumAllResources( pwmDomain );
        LOGGER.debug( getSessionLabel(), () -> "completed generation of nonce '" + nonce + "'", () ->  TimeDuration.fromCurrent( startTime ) );

        final String noncePrefix = pwmDomain.getConfig().readAppProperty( AppProperty.HTTP_RESOURCES_NONCE_PATH_PREFIX );
        return "/" + noncePrefix + nonce;
    }

    public boolean checkIfThemeExists( final PwmRequest pwmRequest, final String themeName )
            throws PwmUnrecoverableException
    {
        if ( themeName == null )
        {
            return false;
        }

        if ( Objects.equals( themeName, ResourceFileServlet.EMBED_THEME ) )
        {
            return true;
        }

        if ( !themeName.matches( pwmRequest.getDomainConfig().readAppProperty( AppProperty.SECURITY_INPUT_THEME_MATCH_REGEX ) ) )
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
            final Optional<FileResource> resolvedFile = ResourceFileRequest.resolveRequestedResource(
                    pwmRequest.getDomainConfig(),
                    servletContext,
                    themePathUrl,
                    getResourceServletConfiguration() );
            if ( resolvedFile.isPresent() )
            {
                LOGGER.debug( pwmRequest, () -> "check for theme validity of '" + themeName + "' returned true" );
                return true;
            }
        }

        LOGGER.debug( pwmRequest, () -> "check for theme validity of '" + themeName + "' returned false" );
        return false;
    }

    private String checksumAllResources( final PwmDomain pwmDomain )
            throws IOException
    {
        try ( ChecksumOutputStream checksumStream = new ChecksumOutputStream( new NullOutputStream() ) )
        {
            checksumResourceFilePath( pwmDomain, checksumStream );

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

    private static void checksumResourceFilePath( final PwmDomain pwmDomain, final ChecksumOutputStream checksumStream )
    {
        if ( pwmDomain.getPwmApplication().getPwmEnvironment().getContextManager() != null )
        {
            try
            {
                final Optional<File> webInfPath = pwmDomain.getPwmApplication().getPwmEnvironment().getContextManager().locateWebInfFilePath();
                if ( webInfPath.isPresent() && webInfPath.get().exists() )
                {
                    final File basePath = webInfPath.get().getParentFile();
                    if ( basePath != null && basePath.exists() )
                    {
                        final File resourcePath = new File( basePath.getAbsolutePath() + File.separator + "public" + File.separator + "resources" );
                        if ( resourcePath.exists() )
                        {
                            final Iterator<FileSystemUtility.FileSummaryInformation> iter =
                                    FileSystemUtility.readFileInformation( Collections.singletonList( resourcePath ) );
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
