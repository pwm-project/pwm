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

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.util.JsonUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.logging.PwmLogger;

import java.util.Collections;
import java.util.Date;
import java.util.List;

public class CacheService implements PwmService {
    private static PwmLogger LOGGER = PwmLogger.forClass(CacheService.class);

    private MemoryCacheStore memoryCacheStore;
    private LocalDBCacheStore localDBCacheStore;

    private STATUS status = STATUS.NEW;

    private Date lastTraceOutput;

    @Override
    public STATUS status() {
        return status;
    }

    @Override
    public void init(PwmApplication pwmApplication)
            throws PwmException
    {
        final boolean enabled = Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(AppProperty.CACHE_ENABLE));
        if (!enabled) {
            LOGGER.debug("skipping cache service init due to app property setting");
            status = STATUS.CLOSED;
            return;
        }

        if (pwmApplication.getLocalDB() == null) {
            LOGGER.debug("skipping cache service init due to localDB not being available");
            status = STATUS.CLOSED;
            return;
        }

        if (pwmApplication.getApplicationMode() == PwmApplication.MODE.READ_ONLY) {
            LOGGER.debug("skipping cache service init due to read-only application mode");
            status = STATUS.CLOSED;
            return;
        }

        status = STATUS.OPENING;
        final int maxMemItems = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.CACHE_MEMORY_MAX_ITEMS));
        if (pwmApplication.getLocalDB() != null && pwmApplication.getLocalDB().status() == LocalDB.Status.OPEN) {
            localDBCacheStore = new LocalDBCacheStore(pwmApplication);
        }
        memoryCacheStore = new MemoryCacheStore(maxMemItems);
        status = STATUS.OPEN;
    }

    @Override
    public void close() {
        status = STATUS.CLOSED;
        localDBCacheStore = null;
    }

    @Override
    public List<HealthRecord> healthCheck() {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfo serviceInfo() {
        return new ServiceInfo(Collections.<DataStorageMethod>emptyList());
    }

    public void put(final CacheKey cacheKey, final CachePolicy cachePolicy, final String payload)
            throws PwmUnrecoverableException {
        if (status != STATUS.OPEN) {
            return;
        }
        if (cacheKey == null) {
            throw new NullPointerException("cacheKey can not be null");
        }
        if (cachePolicy == null) {
            throw new NullPointerException("cachePolicy can not be null");
        }
        if (payload == null) {
            throw new NullPointerException("payload can not be null");
        }
        final Date expirationDate = cachePolicy.getExpiration();
        memoryCacheStore.store(cacheKey, expirationDate, payload);
        if (localDBCacheStore != null) {
            localDBCacheStore.store(cacheKey, expirationDate, payload);
        }
        outputTraceInfo();
    }

    public String get(CacheKey cacheKey)
            throws PwmUnrecoverableException {
        if (cacheKey == null) {
            return null;
        }

        if (status != STATUS.OPEN) {
            return null;
        }

        String payload = null;
        if (memoryCacheStore != null) {
            payload = memoryCacheStore.read(cacheKey);
        }

        if (payload == null && localDBCacheStore != null) {
            payload = localDBCacheStore.read(cacheKey);
        }

        outputTraceInfo();

        return payload;
    }

    private void outputTraceInfo() {
        if (lastTraceOutput == null || TimeDuration.fromCurrent(lastTraceOutput).isLongerThan(30 * 1000)) {
            lastTraceOutput = new Date();
        } else {
            return;
        }

        final StringBuilder traceOutput = new StringBuilder();
        if (memoryCacheStore != null) {
            final CacheStoreInfo info = memoryCacheStore.getCacheStoreInfo();
            traceOutput.append(", memCache=");
            traceOutput.append(JsonUtil.serialize(info));
        }
        if (localDBCacheStore != null) {
            final CacheStoreInfo info = localDBCacheStore.getCacheStoreInfo();
            traceOutput.append(", localDbCache=");
            traceOutput.append(JsonUtil.serialize(info));
        }
        LOGGER.trace(traceOutput);
    }
}
