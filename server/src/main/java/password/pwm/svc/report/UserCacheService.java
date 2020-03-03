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

package password.pwm.svc.report;

import com.google.gson.JsonSyntaxException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.UserInfo;
import password.pwm.svc.PwmService;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.SecureService;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UserCacheService implements PwmService
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( UserCacheService.class );

    private CacheStoreWrapper cacheStore;
    private STATUS status;

    private PwmApplication pwmApplication;


    public STATUS status( )
    {
        return status;
    }

    UserCacheRecord updateUserCache( final UserInfo userInfo )
            throws PwmUnrecoverableException
    {
        final StorageKey storageKey = StorageKey.fromUserInfo( userInfo, pwmApplication );

        try
        {
            final UserCacheRecord userCacheRecord = UserCacheRecord.fromUserInfo( userInfo );
            store( userCacheRecord );
            return userCacheRecord;
        }
        catch ( final LocalDBException e )
        {
            LOGGER.error( () -> "unable to store user status cache to localdb: " + e.getMessage() );
        }

        {
            LOGGER.trace( () -> "updateCache: read user cache for "
                    + userInfo.getUserIdentity() + " user key " + storageKey.getKey() );
        }
        return null;
    }

    UserCacheRecord readStorageKey( final StorageKey storageKey ) throws LocalDBException
    {
        return cacheStore.read( storageKey );
    }

    public void store( final UserCacheRecord userCacheRecord )
            throws LocalDBException, PwmUnrecoverableException
    {
        final StorageKey storageKey = StorageKey.fromUserGUID( userCacheRecord.getUserGUID(), pwmApplication );
        cacheStore.write( storageKey, userCacheRecord );
    }

    public void clear( )
            throws LocalDBException
    {
        cacheStore.clear();
    }

    public UserStatusCacheBeanIterator<StorageKey> iterator( )
    {
        try
        {
            return new UserStatusCacheBeanIterator<>();
        }
        catch ( final LocalDBException e )
        {
            LOGGER.error( () -> "unexpected error generating user status iterator: " + e.getMessage() );
            return null;
        }
    }

    public class UserStatusCacheBeanIterator<K extends StorageKey> implements ClosableIterator
    {

        private LocalDB.LocalDBIterator<Map.Entry<String, String>> innerIterator;

        private UserStatusCacheBeanIterator( ) throws LocalDBException
        {
            innerIterator = cacheStore.localDB.iterator( CacheStoreWrapper.DB );
        }

        public boolean hasNext( )
        {
            return innerIterator.hasNext();
        }

        public StorageKey next( )
        {
            final String nextKey = innerIterator.next().getKey();
            return new StorageKey( nextKey );
        }

        public void remove( )
        {
            throw new UnsupportedOperationException();
        }

        public void close( )
        {
            innerIterator.close();
        }
    }

    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
        status = STATUS.OPENING;
        this.pwmApplication = pwmApplication;
        this.cacheStore = new CacheStoreWrapper( pwmApplication.getLocalDB() );
        status = STATUS.OPEN;
    }

    public void close( )
    {
        status = STATUS.CLOSED;
    }

    public List<HealthRecord> healthCheck( )
    {
        return Collections.emptyList();
    }

    public ServiceInfoBean serviceInfo( )
    {
        return new ServiceInfoBean( Collections.singletonList( DataStorageMethod.LOCALDB ) );
    }

    public long size( )
    {
        return cacheStore.size();
    }

    public static class StorageKey
    {
        private String key;

        private StorageKey( final String key )
        {
            if ( key == null || key.isEmpty() )
            {
                throw new IllegalArgumentException( "storage key must have a value" );
            }
            this.key = key;
        }

        public String getKey( )
        {
            return key;
        }

        static StorageKey fromUserInfo( final UserInfo userInfo, final PwmApplication pwmApplication )
                throws PwmUnrecoverableException
        {
            final String userGUID = userInfo.getUserGuid();
            return fromUserGUID( userGUID, pwmApplication );
        }

        static StorageKey fromUserIdentity( final PwmApplication pwmApplication, final UserIdentity userIdentity )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            final String userGUID = LdapOperationsHelper.readLdapGuidValue( pwmApplication, null, userIdentity, true );
            return fromUserGUID( userGUID, pwmApplication );
        }

        private static StorageKey fromUserGUID( final String userGUID, final PwmApplication pwmApplication )
                throws PwmUnrecoverableException
        {
            final SecureService secureService = pwmApplication.getSecureService();
            return new StorageKey( secureService.hash( userGUID ) );
        }
    }

    private static class CacheStoreWrapper
    {
        private static final LocalDB.DB DB = LocalDB.DB.USER_CACHE;

        private final LocalDB localDB;

        private CacheStoreWrapper( final LocalDB localDB )
        {
            this.localDB = localDB;
        }

        private void write( final StorageKey key, final UserCacheRecord cacheBean )
                throws LocalDBException
        {
            final String jsonValue = JsonUtil.serialize( cacheBean );
            localDB.put( DB, key.getKey(), jsonValue );
        }

        private UserCacheRecord read( final StorageKey key )
                throws LocalDBException
        {
            final String jsonValue = localDB.get( DB, key.getKey() );
            if ( jsonValue != null && !jsonValue.isEmpty() )
            {
                try
                {
                    return JsonUtil.deserialize( jsonValue, UserCacheRecord.class );
                }
                catch ( final JsonSyntaxException e )
                {
                    LOGGER.error( () -> "error reading record from cache store for key=" + key.getKey() + ", error: " + e.getMessage() );
                    localDB.remove( DB, key.getKey() );
                }
            }
            return null;
        }

        private boolean remove( final StorageKey key )
                throws LocalDBException
        {
            return localDB.remove( DB, key.getKey() );
        }

        private void clear( )
                throws LocalDBException
        {
            localDB.truncate( DB );
        }

        private long size( )
        {
            try
            {
                return localDB.size( DB );
            }
            catch ( final Exception e )
            {
                return 0;
            }
        }
    }
}
