/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.util.cache;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import password.pwm.PwmApplication;
import password.pwm.PwmService;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.util.SecureHelper;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class CacheService implements PwmService {
    private static PwmLogger LOGGER = PwmLogger.forClass(CacheService.class);

    private transient ConcurrentMap<String,MemoryValueWrapper> memoryCache;
    private STATUS status = STATUS.OPENING;

    @Override
    public STATUS status()
    {
        return status;
    }

    @Override
    public void init(PwmApplication pwmApplication)
            throws PwmException
    {
        status = STATUS.OPEN;
    }

    @Override
    public void close()
    {
        status = STATUS.CLOSED;
    }

    @Override
    public List<HealthRecord> healthCheck()
    {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfo serviceInfo()
    {
        return new ServiceInfo(Collections.<DataStorageMethod>emptyList());
    }

    private Map<String, MemoryValueWrapper> getMemoryCache() {
        if (memoryCache == null) {
            final ConcurrentMap newCache = new ConcurrentLinkedHashMap.Builder<String, MemoryValueWrapper>()
                    .maximumWeightedCapacity(100)
                    .build();
            memoryCache = newCache;
            return newCache;
        }
        return memoryCache;
    }

    public void put(CacheKey cacheKey, CachePolicy cachePolicy, String payload)
            throws PwmUnrecoverableException
    {
        if (cacheKey == null) {
            return;
        }
        if (cachePolicy == null || cachePolicy.getExpiration() == null) {
            return;
        }
        final String key = cacheKey.getKey();
        final MemoryValueWrapper wrapper = new MemoryValueWrapper(cacheKey, cachePolicy, payload);
        getMemoryCache().put(key,wrapper);
    }

    public String get(CacheKey cacheKey)
            throws PwmUnrecoverableException
    {
        if (cacheKey == null) {
            return null;
        }
        Map<String, MemoryValueWrapper> memCache = getMemoryCache();
        final String key = cacheKey.getKey();
        final MemoryValueWrapper memoryValueWrapper = memCache.get(key);
        if (memoryValueWrapper == null) {
            return null;
        }
        if (!cacheKey.equals(memoryValueWrapper.getCacheKey())) {
            memCache.remove(key);
            return null;
        }
        if (memoryValueWrapper.getCachePolicy() == null) {
            memCache.remove(key);
            return null;
        }
        if (memoryValueWrapper.getCachePolicy().getExpiration() == null) {
            memCache.remove(key);
            return null;
        }
        if (memoryValueWrapper.getCachePolicy().getExpiration().before(new Date())) {
            memCache.remove(key);
            return null;
        }
        return memoryValueWrapper.getPayload();
    }

    public static class CachePolicy implements Serializable {
        private Date expiration;

        public Date getExpiration()
        {
            return expiration;
        }

        public void setExpiration(Date expiration)
        {
            this.expiration = expiration;
        }

        public static CachePolicy makePolicy(final Date date) {
            final CachePolicy policy = new CachePolicy();
            policy.setExpiration(date);
            return policy;
        }

        public static CachePolicy makePolicy(final long expirationMs) {
            final CachePolicy policy = new CachePolicy();
            final Date expirationDate = new Date(System.currentTimeMillis() + expirationMs);
            policy.setExpiration(expirationDate);
            return policy;
        }
    }

    private static class MemoryValueWrapper implements Serializable {
        final CacheKey cacheKey;
        final CachePolicy cachePolicy;
        final String payload;

        private MemoryValueWrapper(
                CacheKey cacheKey,
                CachePolicy cachePolicy,
                String payload
        )
        {
            this.cacheKey = cacheKey;
            this.cachePolicy = cachePolicy;
            this.payload = payload;
        }

        public CacheKey getCacheKey()
        {
            return cacheKey;
        }

        public CachePolicy getCachePolicy()
        {
            return cachePolicy;
        }

        public String getPayload()
        {
            return payload;
        }
    }

    public static class CacheKey {
        final String cacheKey;

        public CacheKey(String cacheKey)
        {
            this.cacheKey = cacheKey;
        }

        private String getKey()
                throws PwmUnrecoverableException
        {
                return SecureHelper.md5sum(this.cacheKey);
        }

        public static CacheKey makeCacheKey(
                final Class callingClass,
                final UserIdentity userIdentity,
                final String valueID
        ) {
            final StringBuilder sb = new StringBuilder();
            if (callingClass != null) {
                sb.append(callingClass.getName());
                sb.append(":");
            }
            if (userIdentity != null) {
                sb.append(userIdentity.toDeliminatedKey());
                sb.append(":");
            }
            if (valueID == null) {
                sb.append(valueID);
            }
            return new CacheKey(sb.toString());
        }
    }
}
