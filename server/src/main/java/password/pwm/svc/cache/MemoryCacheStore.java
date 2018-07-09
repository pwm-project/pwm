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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.Getter;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class MemoryCacheStore implements CacheStore
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( MemoryCacheStore.class );
    private final Cache<CacheKey, CacheValueWrapper> memoryStore;
    private final CacheStoreInfo cacheStoreInfo = new CacheStoreInfo();

    MemoryCacheStore( final int maxItems )
    {
        memoryStore = Caffeine.newBuilder()
                .maximumSize( maxItems )
                .build();
    }

    @Override
    public void store( final CacheKey cacheKey, final Instant expirationDate, final Serializable data )
            throws PwmUnrecoverableException
    {
        cacheStoreInfo.incrementStoreCount();
        memoryStore.put( cacheKey, new CacheValueWrapper( cacheKey, expirationDate, data ) );
    }

    @Override
    public <T> T read( final CacheKey cacheKey, final Class<T> classOfT )
    {
        cacheStoreInfo.incrementReadCount();
        final CacheValueWrapper valueWrapper = memoryStore.getIfPresent( cacheKey );
        if ( valueWrapper != null )
        {
            if ( cacheKey.equals( valueWrapper.getCacheKey() ) )
            {
                if ( valueWrapper.getExpirationDate().isAfter( Instant.now() ) )
                {
                    cacheStoreInfo.incrementHitCount();
                    return (T) valueWrapper.getPayload();
                }
            }
        }
        memoryStore.invalidate( cacheKey );
        cacheStoreInfo.incrementMissCount();
        return null;
    }

    @Override
    public CacheStoreInfo getCacheStoreInfo( )
    {
        return cacheStoreInfo;
    }

    @Override
    public int itemCount( )
    {
        return ( int ) memoryStore.estimatedSize();
    }

    @Override
    public List<CacheDebugItem> getCacheDebugItems( )
    {
        final List<CacheDebugItem> items = new ArrayList<>();
        for ( Map.Entry<CacheKey, CacheValueWrapper> entry : memoryStore.asMap().entrySet() )
        {
            final CacheKey cacheKey = entry.getKey();
            final CacheValueWrapper cacheValueWrapper = entry.getValue();
            final Instant storeDate = cacheValueWrapper.getExpirationDate();
            final String age = Duration.between( storeDate, Instant.now() ).toString();
            final int chars = JsonUtil.serialize( cacheValueWrapper.getPayload() ).length();
            final CacheDebugItem cacheDebugItem = new CacheDebugItem( cacheKey, age, chars );
            items.add( cacheDebugItem );
        }
        return Collections.unmodifiableList( items );
    }

    @Getter
    @AllArgsConstructor
    static class CacheValueWrapper implements Serializable
    {
        private final CacheKey cacheKey;
        private final Instant expirationDate;
        private final Serializable payload;
    }
}
