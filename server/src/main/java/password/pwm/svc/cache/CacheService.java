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

package password.pwm.svc.cache;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
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
import java.util.TreeMap;

public class CacheService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( CacheService.class );

    private MemoryCacheStore memoryCacheStore;

    private STATUS status = STATUS.NEW;

    private ConditionalTaskExecutor traceDebugOutputter;

    @Override
    public STATUS status( )
    {
        return status;
    }

    @Override
    public void init( final PwmApplication pwmApplication )
            throws PwmException
    {
        final boolean enabled = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.CACHE_ENABLE ) );
        if ( !enabled )
        {
            LOGGER.debug( "skipping cache service init due to app property setting" );
            status = STATUS.CLOSED;
            return;
        }

        if ( pwmApplication.getLocalDB() == null )
        {
            LOGGER.debug( "skipping cache service init due to localDB not being available" );
            status = STATUS.CLOSED;
            return;
        }

        if ( pwmApplication.getApplicationMode() == PwmApplicationMode.READ_ONLY )
        {
            LOGGER.debug( "skipping cache service init due to read-only application mode" );
            status = STATUS.CLOSED;
            return;
        }

        status = STATUS.OPENING;
        final int maxMemItems = Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.CACHE_MEMORY_MAX_ITEMS ) );
        memoryCacheStore = new MemoryCacheStore( maxMemItems );
        this.traceDebugOutputter = new ConditionalTaskExecutor(
                ( ) -> outputTraceInfo(),
                new ConditionalTaskExecutor.TimeDurationPredicate( 1, TimeDuration.Unit.MINUTES )
        );
        status = STATUS.OPEN;
    }

    @Override
    public void close( )
    {
        status = STATUS.CLOSED;
    }

    @Override
    public List<HealthRecord> healthCheck( )
    {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        final Map<String, String> debugInfo = new TreeMap<>( );
        debugInfo.put( "itemCount", String.valueOf( memoryCacheStore.itemCount() ) );
        debugInfo.put( "byteCount", String.valueOf( memoryCacheStore.byteCount() ) );
        debugInfo.putAll( JsonUtil.deserializeStringMap( JsonUtil.serialize( memoryCacheStore.getCacheStoreInfo() ) ) );
        debugInfo.putAll( JsonUtil.deserializeStringMap( JsonUtil.serializeMap( memoryCacheStore.storedClassHistogram( "histogram." ) ) ) );
        return new ServiceInfoBean( Collections.emptyList(), debugInfo );
    }

    public Map<String, Serializable> debugInfo( )
    {
        final Map<String, Serializable> debugInfo = new LinkedHashMap<>( );
        debugInfo.put( "memory-statistics", memoryCacheStore.getCacheStoreInfo() );
        debugInfo.put( "memory-items", new ArrayList<Serializable>( memoryCacheStore.getCacheDebugItems() ) );
        debugInfo.put( "memory-histogram", new HashMap<>( memoryCacheStore.storedClassHistogram( "" ) ) );
        return Collections.unmodifiableMap( debugInfo );
    }

    public void put( final CacheKey cacheKey, final CachePolicy cachePolicy, final Serializable payload )
            throws PwmUnrecoverableException
    {
        if ( status != STATUS.OPEN )
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

        if ( status != STATUS.OPEN )
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

        if ( status != STATUS.OPEN )
        {
            return cacheLoader.read();
        }

        traceDebugOutputter.conditionallyExecuteTask();

        final Instant expirationDate = cachePolicy.getExpiration();
        return memoryCacheStore.readAndStore( cacheKey, expirationDate, classOfT, cacheLoader );
    }

    private void outputTraceInfo( )
    {
        final StringBuilder traceOutput = new StringBuilder();
        if ( memoryCacheStore != null )
        {
            final CacheStoreInfo info = memoryCacheStore.getCacheStoreInfo();
            traceOutput.append( "memCache=" );
            traceOutput.append( JsonUtil.serialize( info ) );
            traceOutput.append( ", histogram=" );
            traceOutput.append( JsonUtil.serializeMap( memoryCacheStore.storedClassHistogram( "" ) ) );
        }
        LOGGER.trace( traceOutput );
    }
}
