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

package password.pwm.svc.report;

import com.google.gson.JsonSyntaxException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.svc.PwmService;
import password.pwm.util.ClosableIterator;
import password.pwm.util.JsonUtil;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.SecureEngine;

import java.util.Collections;
import java.util.List;

public class UserCacheService implements PwmService {

    private static final PwmLogger LOGGER = PwmLogger.forClass(UserCacheService.class);

    private CacheStoreWrapper cacheStore;
    private STATUS status;


    public STATUS status() {
        return status;
    }

    public UserCacheRecord updateUserCache(final UserInfoBean userInfoBean)
            throws PwmUnrecoverableException
    {
        final StorageKey storageKey = StorageKey.fromUserInfoBean(userInfoBean);

        boolean preExisting = false;
        try {
            UserCacheRecord userCacheRecord = readStorageKey(storageKey);
            if (userCacheRecord == null) {
                userCacheRecord = new UserCacheRecord();
            } else {
                preExisting = true;
            }
            userCacheRecord.addUiBeanData(userInfoBean);
            store(userCacheRecord);
            return userCacheRecord;
        } catch (LocalDBException e) {
            LOGGER.error("unable to store user status cache to localdb: " + e.getMessage());
        }
        LOGGER.trace("updateCache: " + (preExisting?"updated existing":"created new") + " user cache for " + userInfoBean.getUserIdentity() + " user key " + storageKey.getKey());
        return null;
    }

    public UserCacheRecord readStorageKey(final StorageKey storageKey) throws LocalDBException {
        return cacheStore.read(storageKey);
    }

    public boolean removeStorageKey(final StorageKey storageKey)
            throws LocalDBException
    {
        return cacheStore.remove(storageKey);
    }

    public void store(UserCacheRecord userCacheRecord)
            throws LocalDBException, PwmUnrecoverableException
    {
        final StorageKey storageKey = StorageKey.fromUserGUID(userCacheRecord.getUserGUID());
        cacheStore.write(storageKey, userCacheRecord);
    }

    public void clear()
            throws LocalDBException
    {
        cacheStore.clear();
    }

    public UserStatusCacheBeanIterator<StorageKey> iterator() {
        try {
            return new UserStatusCacheBeanIterator<>();
        } catch (LocalDBException e) {
            LOGGER.error("unexpected error generating user status iterator: " + e.getMessage());
            return null;
        }
    }

    public class UserStatusCacheBeanIterator<K extends StorageKey> implements ClosableIterator {

        private LocalDB.LocalDBIterator<String> innerIterator;

        private UserStatusCacheBeanIterator() throws LocalDBException {
            innerIterator = cacheStore.localDB.iterator(CacheStoreWrapper.DB);
        }

        public boolean hasNext() {
            return innerIterator.hasNext();
        }

        public StorageKey next() {
            final String nextKey = innerIterator.next();
            return new StorageKey(nextKey);
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        public void close() {
            innerIterator.close();
        }
    }

    public void init(final PwmApplication pwmApplication) throws PwmException {
        status = STATUS.OPENING;
        this.cacheStore = new CacheStoreWrapper(pwmApplication.getLocalDB());
        status = STATUS.OPEN;
    }

    public void close() {
        status = STATUS.CLOSED;
    }

    public List<HealthRecord> healthCheck() {
        return Collections.emptyList();
    }

    public ServiceInfo serviceInfo()
    {
        return new ServiceInfo(Collections.singletonList(DataStorageMethod.LOCALDB));
    }

    public int size()
    {
        return cacheStore.size();
    }

    public static class StorageKey {
        private String key;

        private StorageKey(String key)
        {
            if (key == null || key.isEmpty()) {
                throw new IllegalArgumentException("storage key must have a value");
            }
            this.key = key;
        }

        public String getKey()
        {
            return key;
        }

        public static StorageKey fromUserInfoBean(final UserInfoBean userInfoBean)
                throws PwmUnrecoverableException
        {
            final String userGUID = userInfoBean.getUserGuid();
            return fromUserGUID(userGUID);
        }

        public static StorageKey fromUserIdentity(final PwmApplication pwmApplication, final UserIdentity userIdentity)
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            final String userGUID = LdapOperationsHelper.readLdapGuidValue(pwmApplication, null, userIdentity, true);
            return fromUserGUID(userGUID);
        }

        private static StorageKey fromUserGUID(final String userGUID)
                throws PwmUnrecoverableException
        {
            return new StorageKey(SecureEngine.md5sum(userGUID));
        }
    }

    private static class CacheStoreWrapper {
        private static final LocalDB.DB DB = LocalDB.DB.USER_CACHE;

        private final LocalDB localDB;

        private CacheStoreWrapper(LocalDB localDB)
        {
            this.localDB = localDB;
        }

        private void write(StorageKey key, UserCacheRecord cacheBean)
                throws LocalDBException
        {
            final String jsonValue = JsonUtil.serialize(cacheBean);
            localDB.put(DB,key.getKey(),jsonValue);
        }

        private UserCacheRecord read(StorageKey key)
                throws LocalDBException
        {
            final String jsonValue = localDB.get(DB,key.getKey());
            if (jsonValue != null && !jsonValue.isEmpty()) {
                try {
                    return JsonUtil.deserialize(jsonValue,UserCacheRecord.class);
                } catch (JsonSyntaxException e) {
                    LOGGER.error("error reading record from cache store for key=" + key.getKey() + ", error: " + e.getMessage());
                    localDB.remove(DB,key.getKey());
                }
            }
            return null;
        }

        private boolean remove(StorageKey key)
                throws LocalDBException
        {
            return localDB.remove(DB,key.getKey());
        }

        private void    clear()
                throws LocalDBException
        {
            localDB.truncate(DB);
        }

        private int size()
        {
            try {
                return localDB.size(DB);
            } catch (Exception e) {
                return 0;
            }
        }
    }
}
