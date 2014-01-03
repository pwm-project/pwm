package password.pwm;

import password.pwm.bean.UserInfoBean;
import password.pwm.bean.UserStatusCacheBean;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class UserCacheService implements PwmService {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(UserCacheService.class);
    private static final LocalDB.DB DB = LocalDB.DB.USER_CACHE;

    private STATUS status;
    private LocalDB localDB;

    public STATUS status() {
        return status;
    }

    public void updateUserCache(final UserInfoBean userInfoBean) {
        final UserStatusCacheBean userStatusCacheBean = UserStatusCacheBean.cacheBeanFrmInfoBean(userInfoBean);
        try {
            store(userStatusCacheBean);
        } catch (LocalDBException e) {
            LOGGER.error("unable to store user status cache to localdb: " + e.getMessage());
        }
    }

    private UserStatusCacheBean readStorageKey(final String storageKey) throws LocalDBException {
        final String storedValue = localDB.get(DB,storageKey);
        if (storedValue == null) {
            return null;
        }

        try {
            return Helper.getGson().fromJson(storedValue,UserStatusCacheBean.class);
        } catch (Exception e) {
            LOGGER.error("unexpected error decoding json cached user status data: " + e.getMessage());
            return null;
        }
    }

    private void store(UserStatusCacheBean userStatusCacheBean) throws LocalDBException {
        if (userStatusCacheBean == null || this.localDB == null) {
            return;
        }

        if (userStatusCacheBean.getUserGUID() == null || userStatusCacheBean.getUserGUID().length() < 1) {
            throw new IllegalArgumentException("cannot store user cache data, user guid is missing");
        }

        final String storageKey;
        try {
            storageKey = Helper.md5sum(userStatusCacheBean.getUserGUID());
        } catch (IOException e) {
            throw new IllegalStateException("can't generate md5sum of user guid due to IOException: " + e.getMessage());
        }

        final String jsonBean = Helper.getGson().toJson(userStatusCacheBean);
        localDB.put(DB,storageKey,jsonBean);
        LOGGER.trace("stored cache of '" + userStatusCacheBean.getUserDN() + "', content=" + jsonBean);
    }

    public java.util.Iterator iterator() {
        try {
            return new UserStatusCacheBeanIterator();
        } catch (LocalDBException e) {
            LOGGER.error("unexpected error generating user status iterator: " + e.getMessage());
            return null;
        }
    }

    private class UserStatusCacheBeanIterator implements java.util.Iterator {

        private LocalDB.LocalDBIterator<String> innerIterator;

        private UserStatusCacheBeanIterator() throws LocalDBException {
            innerIterator = localDB.iterator(DB);
        }

        public boolean hasNext() {
            return innerIterator.hasNext();
        }

        public UserStatusCacheBean next() {
            final String nextKey = innerIterator.next();
            if (nextKey != null) {
                try {
                    return readStorageKey(nextKey);
                } catch (LocalDBException e) {
                    LOGGER.error("unexpected error iterating user status iterator: " + e.getMessage());
                }
            }
            return null;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public void init(final PwmApplication pwmApplication) throws PwmException {
        status = STATUS.OPENING;
        this.localDB = pwmApplication.getLocalDB();
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
        return new ServiceInfo(Collections.<DataStorageMethod>singletonList(DataStorageMethod.LOCALDB));
    }

}
