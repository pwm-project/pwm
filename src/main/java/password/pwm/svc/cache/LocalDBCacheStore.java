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

import password.pwm.PwmApplication;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.Helper;
import password.pwm.util.JsonUtil;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.*;

public class LocalDBCacheStore implements CacheStore {
    private static final PwmLogger LOGGER = PwmLogger.forClass(LocalDBCacheStore.class);
    
    private static final LocalDB.DB DB = LocalDB.DB.CACHE;
    private static final int MAX_REMOVALS_PER_CYCLE = 10 * 1000;
    private static final int TICKS_BETWEEN_PURGE_CYCLES = 1000;

    private final LocalDB localDB;
    private final Timer timer;
    private int ticks = 0;

    private int readCount;
    private int storeCount;
    private int hitCount;
    private int missCount;

    LocalDBCacheStore(final PwmApplication pwmApplication) {
        this.localDB = pwmApplication.getLocalDB();
        try {
            localDB.truncate(DB);
        } catch (LocalDBException e) {
            LOGGER.error("error while clearing LocalDB CACHE DB during init: " + e.getMessage());
        }
        timer = new Timer(Helper.makeThreadName(pwmApplication,LocalDBCacheStore.class),true);
    }

    @Override
    public void store(final CacheKey cacheKey, final Date expirationDate, final String data)
            throws PwmUnrecoverableException
    {
        ticks++;
        storeCount++;
        try {
            localDB.put(DB,cacheKey.getHash(),JsonUtil.serialize(new ValueWrapper(cacheKey, expirationDate, data)));
        } catch (LocalDBException e) {
            LOGGER.error("error while writing cache: " + e.getMessage());
        }
        if (ticks > TICKS_BETWEEN_PURGE_CYCLES) {
            ticks = 0;
            timer.schedule(new PurgerTask(),1);
        }
    }

    @Override
    public String read(CacheKey cacheKey)
            throws PwmUnrecoverableException 
    {
        readCount++;
        final String hashKey = cacheKey.getHash();
        final String storedValue; 
        try {
            storedValue = localDB.get(DB,hashKey);
        } catch (LocalDBException e) {
            LOGGER.error("error while reading cache: " + e.getMessage());
            return null;
        }
        if (storedValue != null) {
            try {
                final ValueWrapper valueWrapper = JsonUtil.deserialize(storedValue, ValueWrapper.class);
                if (cacheKey.equals(valueWrapper.getCacheKey())) {
                    if (valueWrapper.getExpirationDate().after(new Date())) {
                        hitCount++;
                        return valueWrapper.getPayload();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("error reading from cache: " + e.getMessage());
            }
            try {
                localDB.remove(DB,hashKey);
            } catch (LocalDBException e) {
                LOGGER.error("error while purging record from cache: " + e.getMessage());
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
        try {
            cacheStoreInfo.setItemCount(localDB.size(DB));
        } catch (LocalDBException e) {
            LOGGER.error("error generating cacheStoreInfo: " + e.getMessage());
        }
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
    
   
    private boolean purgeExpiredRecords() throws LocalDBException {
        final List<String> removalKeys = new ArrayList<>();
        final LocalDB.LocalDBIterator<String> localDBIterator = localDB.iterator(DB);
        int counter = 0;
        try {
            while (localDBIterator.hasNext() && removalKeys.size() < MAX_REMOVALS_PER_CYCLE) {
                final String key = localDBIterator.next();
                counter++;
                boolean keep = false;
                try {
                    if (key != null) {
                        final String strValue = localDB.get(DB, key);
                        if (strValue != null) {
                            final ValueWrapper valueWrapper = JsonUtil.deserialize(strValue, ValueWrapper.class);
                            if (valueWrapper.expirationDate.before(new Date())) {
                                keep = true;
                            }
                        }

                    }
                } catch (Exception e) {
                    LOGGER.error("error reading from cache: " + e.getMessage());
                }
                if (!keep) {
                    removalKeys.add(key);
                }
            }
        } finally {
            if (localDBIterator != null) {
                localDBIterator.close();
            }
        }
        if (!removalKeys.isEmpty()) {
            LOGGER.debug("purging " + removalKeys.size() + " expired cache records");
            localDB.removeAll(DB, removalKeys);
        } else {
            LOGGER.trace("purger examined " + counter + " records and did not discover any expired cache records");
        }
        
        return removalKeys.size() >= MAX_REMOVALS_PER_CYCLE;
    }
    
    private class PurgerTask extends TimerTask {
        @Override
        public void run() {
            try {
                purgeExpiredRecords();
            } catch (LocalDBException e) {
                LOGGER.error("error while running purger task: " + e.getMessage(),e);
            }
        }
    }
}
