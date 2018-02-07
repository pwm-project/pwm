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
import password.pwm.error.PwmUnrecoverableException;

import java.time.Instant;

class MemoryCacheStore implements CacheStore
{
    private final Cache<String, CacheValueWrapper> memoryStore;
    private final CacheStoreInfo cacheStoreInfo = new CacheStoreInfo();

    MemoryCacheStore( final int maxItems )
    {
        memoryStore = Caffeine.newBuilder()
                .maximumSize( maxItems )
                .build();
    }

    @Override
    public void store( final CacheKey cacheKey, final Instant expirationDate, final String data )
            throws PwmUnrecoverableException
    {
        cacheStoreInfo.getStoreCount();
        memoryStore.put( cacheKey.getHash(), new CacheValueWrapper( cacheKey, expirationDate, data ) );
    }

    @Override
    public String read( final CacheKey cacheKey )
            throws PwmUnrecoverableException
    {
        cacheStoreInfo.getReadCount();
        final CacheValueWrapper valueWrapper = memoryStore.getIfPresent( cacheKey.getHash() );
        if ( valueWrapper != null )
        {
            if ( cacheKey.equals( valueWrapper.getCacheKey() ) )
            {
                if ( valueWrapper.getExpirationDate().isAfter( Instant.now() ) )
                {
                    cacheStoreInfo.incrementHitCount();
                    return valueWrapper.getPayload();
                }
            }
        }
        memoryStore.invalidate( cacheKey.getHash() );
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
}
