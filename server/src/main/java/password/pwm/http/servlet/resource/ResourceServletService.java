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
import password.pwm.util.Percent;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StatisticAverageBundle;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;

import javax.servlet.ServletContext;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ResourceServletService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ResourceServletService.class );

    private static final List<String> TEST_URLS = List.of(
            ResourceFileServlet.THEME_CSS_PATH,
            ResourceFileServlet.THEME_CSS_MOBILE_PATH );

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
        final Set<CacheEntry> cacheCopy = new HashSet<>( cache.asMap().values() );

        return cacheCopy.stream()
                .filter( Objects::nonNull )
                .filter( entry -> entry.entity() != null )
                .mapToLong( entry -> entry.entity().size() )
                .sum();
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
            LOGGER.trace( getSessionLabel(), () -> "calculated nonce", TimeDuration.fromCurrent( start ) );
        }
        catch ( final Exception e )
        {
            LOGGER.error( getSessionLabel(), () -> "error during nonce generation, will remain closed; error: " + e.getMessage() );
            return STATUS.CLOSED;
        }

        // prevents null cache entry for requests that happened before service init
        cache.invalidateAll();

        return STATUS.OPEN;
    }

    @Override
    public void shutdownImpl( )
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
        debugInfo.putAll( countingStats.debugStats( PwmConstants.DEFAULT_LOCALE ) );
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
        LOGGER.debug( getSessionLabel(), () -> "completed generation of nonce '" + nonce + "'", TimeDuration.fromCurrent( startTime ) );

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



        for ( final String testUrl : TEST_URLS )
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
        try ( DigestOutputStream checksumStream = new DigestOutputStream( OutputStream.nullOutputStream(), PwmHashAlgorithm.SHA1.newMessageDigest() ) )
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
            return JavaHelper.binaryArrayToHex( checksumStream.getMessageDigest().digest() ).toLowerCase();
        }
    }

    private static void checksumResourceFilePath( final PwmDomain pwmDomain, final DigestOutputStream checksumStream )
    {
        if ( pwmDomain.getPwmApplication().getPwmEnvironment().getContextManager() == null )
        {
            return;
        }

        final Consumer<FileSystemUtility.FileSummaryInformation> consumer = fileSummaryInformation ->
        {
            try
            {
                checksumStream.write( fileSummaryInformation.sha512Hash().getBytes( StandardCharsets.UTF_8 ) );
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "unable to generate resource path nonce: " + e.getMessage() );
            }

        };

        pwmDomain.getPwmApplication().getPwmEnvironment().getContextManager().locateWebInfFilePath().ifPresent( webInfPath ->
        {
            final Path basePath = webInfPath.getParent();
            if ( basePath != null && Files.exists( basePath ) )
            {
                final Path resourcePath = basePath.resolve( "public" ).resolve( "resources" );
                if ( Files.exists( resourcePath ) )
                {
                    FileSystemUtility.readFileInformation( Collections.singletonList( resourcePath ) )
                            .forEach( consumer );
                }
            }
        } );
    }
}
