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
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class CacheService implements PwmService {
    private static PwmLogger LOGGER = PwmLogger.getLogger(CacheService.class);

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

    public void put(String identifer, Date expiration, String payload) {
        try {
            if (identifer == null) {
                return;
            }
            if (expiration == null || expiration.before(new Date())) {
                return;
            }
            final String key = Helper.md5sum(identifer);
            final MemoryValueWrapper wrapper = new MemoryValueWrapper(identifer, expiration, payload);
            getMemoryCache().put(key,wrapper);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String get(String identifer) {
        if (identifer == null) {
            return null;
        }
        Map<String, MemoryValueWrapper> memCache = getMemoryCache();
        try {
            final String key = Helper.md5sum(identifer);
            final MemoryValueWrapper memoryValueWrapper = memCache.get(key);
            if (memoryValueWrapper == null) {
                return null;
            }
            if (!identifer.equals(memoryValueWrapper.getIdentifier())) {
                memCache.remove(key);
                return null;
            }
            if (memoryValueWrapper.getExpiration() == null) {
                memCache.remove(key);
                return null;
            }
            if (memoryValueWrapper.getExpiration().before(new Date())) {
                memCache.remove(key);
                return null;
            }
            return memoryValueWrapper.getPayload();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static class MemoryValueWrapper {
        final String identifier;
        final Date expiration;
        final String payload;

        private MemoryValueWrapper(
                String identifier,
                Date expiration,
                String payload
        )
        {
            this.identifier = identifier;
            this.expiration = expiration;
            this.payload = payload;
        }

        public String getIdentifier()
        {
            return identifier;
        }

        public Date getExpiration()
        {
            return expiration;
        }

        public String getPayload()
        {
            return payload;
        }
    }

}
