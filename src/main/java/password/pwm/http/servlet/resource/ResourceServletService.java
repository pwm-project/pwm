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

package password.pwm.http.servlet.resource;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.svc.stats.EventRateMeter;
import password.pwm.util.Percent;
import password.pwm.util.logging.PwmLogger;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResourceServletService implements PwmService {
    private static final PwmLogger LOGGER = PwmLogger.forClass(ResourceServletService.class);


    private ResourceServletConfiguration resourceServletConfiguration;
    private Map<CacheKey, CacheEntry> cacheMap;
    private EventRateMeter.MovingAverage cacheHitRatio = new EventRateMeter.MovingAverage(60 * 60 * 1000);
    private String resourceNonce;
    private STATUS status = STATUS.NEW;

    public String getResourceNonce() {
        return resourceNonce;
    }

    public Map<CacheKey, CacheEntry> getCacheMap() {
        return cacheMap;
    }

    public EventRateMeter.MovingAverage getCacheHitRatio() {
        return cacheHitRatio;
    }


    public long bytesInCache() {
        final ResourceServletConfiguration cpv = resourceServletConfiguration;
        final Map<CacheKey, CacheEntry> responseCache = getCacheMap();
        final Map<CacheKey, CacheEntry> cacheCopy = new HashMap<>();
        cacheCopy.putAll(responseCache);
        long cacheByteCount = 0;
        for (final CacheKey cacheKey : cacheCopy.keySet()) {
            final CacheEntry cacheEntry = responseCache.get(cacheKey);
            if (cacheEntry != null && cacheEntry.getEntity() != null) {
                cacheByteCount += cacheEntry.getEntity().length;
            }
        }
        return cacheByteCount;
    }

    public int itemsInCache() {
        final ResourceServletConfiguration cpv = resourceServletConfiguration;
        final Map<CacheKey, CacheEntry> responseCache = getCacheMap();
        return responseCache.size();
    }

    public Percent cacheHitRatio() {
        final BigDecimal numerator = BigDecimal.valueOf(getCacheHitRatio().getAverage());
        final BigDecimal denominator = BigDecimal.ONE;
        return new Percent(numerator, denominator);
    }

    @Override
    public STATUS status() {
        return status;
    }

    @Override
    public void init(PwmApplication pwmApplication) throws PwmException {
        status = STATUS.OPENING;
        try {
            this.resourceServletConfiguration = new ResourceServletConfiguration(pwmApplication);

            cacheMap = new ConcurrentLinkedHashMap.Builder<CacheKey, CacheEntry>()
                    .maximumWeightedCapacity(resourceServletConfiguration.getMaxCacheItems())
                    .build();

            resourceNonce = makeResourcePathNonce(pwmApplication);
            status = STATUS.OPEN;
        } catch (Exception e) {
            LOGGER.error("error during initialization, will remain closed; error: " + e.getMessage());
            status = STATUS.CLOSED;
        }
    }

    @Override
    public void close() {

    }

    @Override
    public List<HealthRecord> healthCheck() {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfo serviceInfo() {
        return null;
    }

    ResourceServletConfiguration getResourceServletConfiguration() {
        return resourceServletConfiguration;
    }

    private static String makeResourcePathNonce(final PwmApplication pwmApplication) {
        final boolean enablePathNonce = Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_RESOURCES_ENABLE_PATH_NONCE));
        final String noncePrefix = pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_RESOURCES_NONCE_PATH_PREFIX);
        final String nonceValue = Long.toString(pwmApplication.getStartupTime().getTime(),36);

        if (enablePathNonce) {
            return "/" + noncePrefix + nonceValue;
        } else {
            return "";
        }
    }

}
