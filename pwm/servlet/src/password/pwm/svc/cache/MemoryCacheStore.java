/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import password.pwm.error.PwmUnrecoverableException;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

class MemoryCacheStore implements CacheStore {
    private final Map<String,ValueWrapper> memoryStore;
    private int readCount;
    private int storeCount;
    private int hitCount;
    private int missCount;

    MemoryCacheStore(int maxItems) {
        memoryStore = new ConcurrentLinkedHashMap.Builder<String, ValueWrapper>()
            .maximumWeightedCapacity(maxItems)
            .build();
    }

    @Override
    public void store(final CacheKey cacheKey, final Date expirationDate, final String data)
            throws PwmUnrecoverableException {
        storeCount++;
        memoryStore.put(cacheKey.getHash(), new ValueWrapper(cacheKey, expirationDate, data));
    }

    @Override
    public String read(CacheKey cacheKey)
            throws PwmUnrecoverableException 
    {
        readCount++;
        final ValueWrapper valueWrapper = memoryStore.get(cacheKey.getHash());
        if (valueWrapper != null) {
            if (cacheKey.equals(valueWrapper.getCacheKey())) {
                if (valueWrapper.getExpirationDate().after(new Date())) {
                    hitCount++;
                    return valueWrapper.payload;
                }
            }
        }
        missCount++;
        return null;
    }

    @Override
    public CacheStoreInfo getCacheStoreInfo() {
        final CacheStoreInfo cacheStoreInfo = new CacheStoreInfo();
        cacheStoreInfo.setReadCount(readCount);
        cacheStoreInfo.setStoreCount(storeCount);
        cacheStoreInfo.setHitCount(hitCount);
        cacheStoreInfo.setMissCount(missCount);
        cacheStoreInfo.setItemCount(memoryStore.size());
        return cacheStoreInfo;
    }

    private static class ValueWrapper implements Serializable {
        final CacheKey cacheKey;
        final Date expirationDate;
        final String payload;

        private ValueWrapper(
                CacheKey cacheKey,
                Date expirationDate,
                String payload
        )
        {
            this.cacheKey = cacheKey;
            this.expirationDate = expirationDate;
            this.payload = payload;
        }

        public CacheKey getCacheKey()
        {
            return cacheKey;
        }

        public Date getExpirationDate() {
            return expirationDate;
        }

        public String getPayload()
        {
            return payload;
        }
    }

}
