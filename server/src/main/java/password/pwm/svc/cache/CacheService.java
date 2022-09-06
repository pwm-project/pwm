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

package password.pwm.svc.cache;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.DomainID;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public class CacheService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( CacheService.class );

    private MemoryCacheStore memoryCacheStore;

    private ConditionalTaskExecutor traceDebugOutputter;

    @Override
    protected Set<PwmApplication.Condition> openConditions()
    {
        return Collections.emptySet();
    }

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        final boolean enabled = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.CACHE_ENABLE ) );
        if ( !enabled )
        {
            LOGGER.debug( () -> "skipping cache service init due to app property setting" );
            return STATUS.CLOSED;
        }

        final int maxMemItems = Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.CACHE_MEMORY_MAX_ITEMS ) );
        memoryCacheStore = new MemoryCacheStore( maxMemItems );
        this.traceDebugOutputter = ConditionalTaskExecutor.forPeriodicTask(
                this::outputTraceInfo,
                TimeDuration.MINUTE.asDuration() );

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
        if ( status() == STATUS.CLOSED )
        {
            return ServiceInfoBean.builder().build();
        }

        final Map<String, String> debugInfo = new TreeMap<>( );
        debugInfo.put( "itemCount", String.valueOf( memoryCacheStore.itemCount() ) );
        debugInfo.put( "byteCount", String.valueOf( memoryCacheStore.byteCount() ) );
        debugInfo.putAll( JsonFactory.get().deserializeStringMap( JsonFactory.get().serializeMap( memoryCacheStore.getCacheStoreInfo().debugStats() ) ) );
        debugInfo.putAll( JsonFactory.get().deserializeStringMap( JsonFactory.get().serializeMap( memoryCacheStore.storedClassHistogram( "histogram." ) ) ) );
        return ServiceInfoBean.builder().debugProperties( debugInfo ).build();
    }

    public Map<String, Serializable> debugInfo( )
    {
        final Map<String, Serializable> debugInfo = new LinkedHashMap<>( );
        debugInfo.put( "memory-statistics", JsonFactory.get().serializeMap( memoryCacheStore.getCacheStoreInfo().debugStats() ) );
        debugInfo.put( "memory-items", new ArrayList<Serializable>( memoryCacheStore.getCacheDebugItems() ) );
        debugInfo.put( "memory-histogram", new HashMap<>( memoryCacheStore.storedClassHistogram( "" ) ) );
        return Collections.unmodifiableMap( debugInfo );
    }

    public void put( final CacheKey cacheKey, final CachePolicy cachePolicy, final Serializable payload )
            throws PwmUnrecoverableException
    {
        if ( status() != STATUS.OPEN )
        {
            return;
        }

        Objects.requireNonNull( cacheKey );
        Objects.requireNonNull( cachePolicy );
        Objects.requireNonNull( payload );

        final Instant expirationDate = cachePolicy.getExpiration();
        memoryCacheStore.store( cacheKey, expirationDate, payload );

        traceDebugOutputter.conditionallyExecuteTask();
    }

    public <T extends Serializable> T get( final CacheKey cacheKey, final Class<T> classOfT  )
    {
        Objects.requireNonNull( cacheKey );
        Objects.requireNonNull( classOfT );

        if ( status() != STATUS.OPEN )
        {
            return null;
        }

        T payload = null;
        if ( memoryCacheStore != null )
        {
            payload = memoryCacheStore.read( cacheKey, classOfT );
        }

        traceDebugOutputter.conditionallyExecuteTask();

        return payload;
    }

    public <T extends Serializable> T get( final CacheKey cacheKey, final CachePolicy cachePolicy, final Class<T> classOfT, final CacheLoader<T> cacheLoader )
            throws PwmUnrecoverableException
    {
        Objects.requireNonNull( cacheKey );
        Objects.requireNonNull( cachePolicy );
        Objects.requireNonNull( classOfT );
        Objects.requireNonNull( cacheLoader );

        if ( status() != STATUS.OPEN )
        {
            return cacheLoader.read();
        }

        traceDebugOutputter.conditionallyExecuteTask();

        final Instant expirationDate = cachePolicy.getExpiration();
        return memoryCacheStore.readAndStore( cacheKey, expirationDate, classOfT, cacheLoader );
    }

    private void outputTraceInfo( )
    {
        LOGGER.trace( getSessionLabel(), () ->
        {
            final StringBuilder traceOutput = new StringBuilder();
            if ( memoryCacheStore != null )
            {
                final StatisticCounterBundle<CacheStore.DebugKey> info = memoryCacheStore.getCacheStoreInfo();
                traceOutput.append( "memCache=" );
                traceOutput.append( JsonFactory.get().serializeMap( info.debugStats() ) );
                traceOutput.append( ", histogram=" );
                traceOutput.append( JsonFactory.get().serializeMap( memoryCacheStore.storedClassHistogram( "" ) ) );
            }
            return traceOutput.toString();
        } );
    }
}
