/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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
import password.pwm.PwmApplication;
import password.pwm.bean.DomainID;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.user.UserInfo;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ReportRecordLocalDBStorageService extends AbstractPwmService implements PwmService
{
    private static final LocalDB.DB DB = LocalDB.DB.USER_CACHE;

    private static final PwmLogger LOGGER = PwmLogger.forClass( ReportRecordLocalDBStorageService.class );

    private CacheStoreWrapper cacheStore;

    Optional<UserReportRecord> updateUserCache( final UserInfo userInfo )
            throws PwmUnrecoverableException
    {
        try
        {
            final UserReportRecord userReportRecord = UserReportRecord.fromUserInfo( userInfo );
            store( userReportRecord );
            return Optional.of( userReportRecord );
        }
        catch ( final LocalDBException e )
        {
            LOGGER.error( getSessionLabel(), () -> "unable to store user status cache to localdb: " + e.getMessage() );
        }

        {
            LOGGER.trace( getSessionLabel(), () -> "updateCache: read user cache for "
                    + userInfo.getUserIdentity() + " user key " + userInfo.getUserIdentity().toDelimitedKey() );
        }
        return Optional.empty();
    }

    Optional<UserReportRecord> readStorageKey( final UserIdentity storageKey ) throws LocalDBException
    {
        return cacheStore.read( storageKey );
    }

    public void store( final UserReportRecord userReportRecord )
            throws LocalDBException, PwmUnrecoverableException
    {
        cacheStore.write( userReportRecord );
    }

    public void clear( )
            throws LocalDBException
    {
        cacheStore.clear();
    }

    public UserStatusCacheBeanIterator<UserIdentity> iterator( )
    {
        try
        {
            return new UserStatusCacheBeanIterator<>();
        }
        catch ( final LocalDBException e )
        {
            LOGGER.error( getSessionLabel(), () -> "unexpected error generating user status iterator: " + e.getMessage() );
            return null;
        }
    }

    public class UserStatusCacheBeanIterator<K extends UserIdentity> implements ClosableIterator
    {

        private final LocalDB.LocalDBIterator<Map.Entry<String, String>> innerIterator;

        private UserStatusCacheBeanIterator( ) throws LocalDBException
        {
            innerIterator = cacheStore.localDB.iterator( DB );
        }

        @Override
        public boolean hasNext( )
        {
            return innerIterator.hasNext();
        }

        @Override
        public UserIdentity next( )
        {
            final String nextKey = innerIterator.next().getKey();
            try
            {
                return UserIdentity.fromDelimitedKey( getSessionLabel(), nextKey );
            }
            catch ( final PwmUnrecoverableException e )
            {
                throw new IllegalStateException( e );
            }
        }

        @Override
        public void remove( )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close( )
        {
            innerIterator.close();
        }
    }

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        this.cacheStore = new CacheStoreWrapper( pwmApplication.getLocalDB() );
        return STATUS.OPEN;
    }

    @Override
    public void shutdownImpl( )
    {
        setStatus( STATUS.CLOSED );
    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return ServiceInfoBean.builder().storageMethod( DataStorageMethod.LOCALDB ).build();
    }

    public long size( )
    {
        return cacheStore.size();
    }

    private class CacheStoreWrapper
    {

        private final LocalDB localDB;

        private CacheStoreWrapper( final LocalDB localDB )
        {
            this.localDB = localDB;
        }

        private void write( final UserReportRecord cacheBean )
                throws LocalDBException
        {
            final String jsonValue = JsonFactory.get().serialize( cacheBean );
            final String jsonKey = UserIdentity.create( cacheBean.getUserDN(), cacheBean.getLdapProfile(), cacheBean.getDomainID() ).toDelimitedKey();
            localDB.put( DB, jsonKey, jsonValue );
        }

        private Optional<UserReportRecord> read( final UserIdentity key )
                throws LocalDBException
        {
            final String jsonKey = key.toDelimitedKey();
            final Optional<String> jsonValue = localDB.get( DB, jsonKey );
            if ( jsonValue.isPresent() )
            {
                try
                {
                    return Optional.of( JsonFactory.get().deserialize( jsonValue.get(), UserReportRecord.class ) );
                }
                catch ( final JsonSyntaxException e )
                {
                    LOGGER.error( getSessionLabel(), () -> "error reading record from cache store for key=" + jsonKey + ", error: " + e.getMessage() );
                    localDB.remove( DB, jsonKey );
                }
            }
            return Optional.empty();
        }

        private boolean remove( final UserIdentity key )
                throws LocalDBException
        {
            return localDB.remove( DB, key.toDelimitedKey() );
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
