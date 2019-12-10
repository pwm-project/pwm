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

package password.pwm.svc.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Value;
import password.pwm.bean.UserIdentity;
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
import java.util.TreeMap;

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

        final String jsonData = JsonUtil.serialize( data );
        memoryStore.put( cacheKey, new CacheValueWrapper( cacheKey, expirationDate, jsonData ) );
    }

    @Override
    public <T extends Serializable> T readAndStore( final CacheKey cacheKey, final Instant expirationDate, final Class<T> classOfT, final CacheLoader<T> cacheLoader )
            throws PwmUnrecoverableException
    {
        cacheStoreInfo.incrementReadCount();
        {
            final CacheValueWrapper valueWrapper = memoryStore.getIfPresent( cacheKey );
            final T extractedValue = extractValue( classOfT, valueWrapper, cacheKey );
            if ( extractedValue != null )
            {
                return extractedValue;
            }
        }

        final T data = cacheLoader.read();
        final String jsonIfiedData = JsonUtil.serialize( data );
        cacheStoreInfo.incrementMissCount();
        memoryStore.put( cacheKey, new CacheValueWrapper( cacheKey, expirationDate, jsonIfiedData ) );
        return data;
    }

    private <T extends Serializable> T extractValue( final Class<T> classOfT, final CacheValueWrapper valueWrapper, final CacheKey cacheKey )
    {
        if ( valueWrapper != null )
        {
            if ( cacheKey.equals( valueWrapper.getCacheKey() ) )
            {
                if ( valueWrapper.getExpirationDate().isAfter( Instant.now() ) )
                {
                    cacheStoreInfo.incrementHitCount();
                    final String jsonValue  = valueWrapper.getPayload();
                    return JsonUtil.deserialize( jsonValue, classOfT );
                }
            }
        }

        return null;
    }

    @Override
    public <T extends Serializable> T read( final CacheKey cacheKey, final Class<T> classOfT )
    {
        cacheStoreInfo.incrementReadCount();
        final CacheValueWrapper valueWrapper = memoryStore.getIfPresent( cacheKey );
        final T extractedValue = extractValue( classOfT, valueWrapper, cacheKey );
        if ( extractedValue != null )
        {
            return extractedValue;
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
        for ( final Map.Entry<CacheKey, CacheValueWrapper> entry : memoryStore.asMap().entrySet() )
        {
            final CacheKey cacheKey = entry.getKey();
            final CacheValueWrapper cacheValueWrapper = entry.getValue();
            final Instant storeDate = cacheValueWrapper.getExpirationDate();
            final String age = Duration.between( storeDate, Instant.now() ).toString();
            final int chars = cacheValueWrapper.getPayload().length();
            final String keyClass = cacheKey.getSrcClass() == null ? "null" : cacheKey.getSrcClass().getName();
            final String keyUserID = cacheKey.getUserIdentity() == null ? "null" : cacheKey.getUserIdentity().toDisplayString();
            final String keyValue = cacheKey.getValueID() == null ? "null" : cacheKey.getValueID();

            final CacheDebugItem cacheDebugItem = CacheDebugItem.builder()
                    .srcClass( keyClass )
                    .userIdentity( keyUserID )
                    .valueID( keyValue )
                    .age( age )
                    .chars( chars )
                    .build();

            items.add( cacheDebugItem );
        }
        return Collections.unmodifiableList( items );
    }

    @Value
    private static class CacheValueWrapper implements Serializable
    {
        private final CacheKey cacheKey;
        private final Instant expirationDate;

        // serialize to json even though stored in memory, this prevents object-reuse because we don't know
        // if the object is immutable.  Thus an effective clone is made for each store/read.
        private final String payload;
    }

    Map<String, Integer> storedClassHistogram( final String prefix )
    {
        final Map<String, Integer> output = new TreeMap<>(  );
        for ( final CacheKey cacheKey : memoryStore.asMap().keySet() )
        {
            final String className = cacheKey.getSrcClass() == null ? "n/a" : cacheKey.getSrcClass().getSimpleName();
            final String key = prefix + className;
            final Integer currentValue = output.getOrDefault( key, 0 );
            final Integer newValue = currentValue + 1;
            output.put( key, newValue );
        }
        return output;
    }

    @Override
    public long byteCount()
    {
        long byteCount = 0;
        for ( final Map.Entry<CacheKey, CacheValueWrapper> entry : memoryStore.asMap().entrySet() )
        {
            final CacheKey cacheKey = entry.getKey();
            final UserIdentity userIdentity = cacheKey.getUserIdentity();
            byteCount += userIdentity == null ? 0 : cacheKey.getUserIdentity().toDelimitedKey().length();
            final String valueID = cacheKey.getValueID();
            byteCount += valueID == null ? 0 : cacheKey.getValueID().length();
            final CacheValueWrapper cacheValueWrapper = entry.getValue();
            byteCount += cacheValueWrapper.payload.length();
        }
        return byteCount;
    }
}
