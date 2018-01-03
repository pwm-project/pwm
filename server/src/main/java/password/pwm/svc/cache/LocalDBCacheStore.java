/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalDBCacheStore implements CacheStore
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LocalDBCacheStore.class );

    private static final LocalDB.DB DB = LocalDB.DB.CACHE;
    private static final int MAX_REMOVALS_PER_CYCLE = 10 * 1000;
    private static final int TICKS_BETWEEN_PURGE_CYCLES = 1000;

    private final LocalDB localDB;
    private final ExecutorService timer;
    private final AtomicInteger ticks = new AtomicInteger( 0 );

    private final CacheStoreInfo cacheStoreInfo = new CacheStoreInfo();

    LocalDBCacheStore( final PwmApplication pwmApplication )
    {
        this.localDB = pwmApplication.getLocalDB();
        try
        {
            localDB.truncate( DB );
        }
        catch ( LocalDBException e )
        {
            LOGGER.error( "error while clearing LocalDB CACHE DB during init: " + e.getMessage() );
        }
        timer = JavaHelper.makeSingleThreadExecutorService( pwmApplication, LocalDBCacheStore.class );
    }

    @Override
    public void store( final CacheKey cacheKey, final Instant expirationDate, final String data )
            throws PwmUnrecoverableException
    {
        ticks.incrementAndGet();
        cacheStoreInfo.incrementStoreCount();
        try
        {
            localDB.put( DB, cacheKey.getHash(), JsonUtil.serialize( new CacheValueWrapper( cacheKey, expirationDate, data ) ) );
        }
        catch ( LocalDBException e )
        {
            LOGGER.error( "error while writing cache: " + e.getMessage() );
        }
        if ( ticks.get() > TICKS_BETWEEN_PURGE_CYCLES )
        {
            ticks.set( 0 );
            timer.execute( new PurgerTask() );
        }
    }

    @Override
    public String read( final CacheKey cacheKey )
            throws PwmUnrecoverableException
    {
        cacheStoreInfo.incrementReadCount();
        final String hashKey = cacheKey.getHash();
        final String storedValue;
        try
        {
            storedValue = localDB.get( DB, hashKey );
        }
        catch ( LocalDBException e )
        {
            LOGGER.error( "error while reading cache: " + e.getMessage() );
            return null;
        }
        if ( storedValue != null )
        {
            try
            {
                final CacheValueWrapper valueWrapper = JsonUtil.deserialize( storedValue, CacheValueWrapper.class );
                if ( cacheKey.equals( valueWrapper.getCacheKey() ) )
                {
                    if ( valueWrapper.getExpirationDate().isAfter( Instant.now() ) )
                    {
                        cacheStoreInfo.getHitCount();
                        return valueWrapper.getPayload();
                    }
                }
            }
            catch ( Exception e )
            {
                LOGGER.error( "error reading from cache: " + e.getMessage() );
            }
            try
            {
                localDB.remove( DB, hashKey );
            }
            catch ( LocalDBException e )
            {
                LOGGER.error( "error while purging record from cache: " + e.getMessage() );
            }
        }
        cacheStoreInfo.incrementMissCount();
        return null;
    }

    @Override
    public CacheStoreInfo getCacheStoreInfo( )
    {
        return cacheStoreInfo;
    }

    private boolean purgeExpiredRecords( ) throws LocalDBException
    {
        final List<String> removalKeys = new ArrayList<>();
        final LocalDB.LocalDBIterator<String> localDBIterator = localDB.iterator( DB );
        int counter = 0;
        try
        {
            while ( localDBIterator.hasNext() && removalKeys.size() < MAX_REMOVALS_PER_CYCLE )
            {
                final String key = localDBIterator.next();
                counter++;
                boolean keep = false;
                try
                {
                    if ( key != null )
                    {
                        final String strValue = localDB.get( DB, key );
                        if ( strValue != null )
                        {
                            final CacheValueWrapper valueWrapper = JsonUtil.deserialize( strValue, CacheValueWrapper.class );
                            if ( valueWrapper.getExpirationDate().isBefore( Instant.now() ) )
                            {
                                keep = true;
                            }
                        }

                    }
                }
                catch ( Exception e )
                {
                    LOGGER.error( "error reading from cache: " + e.getMessage() );
                }
                if ( !keep )
                {
                    removalKeys.add( key );
                }
            }
        }
        finally
        {
            if ( localDBIterator != null )
            {
                localDBIterator.close();
            }
        }
        if ( !removalKeys.isEmpty() )
        {
            LOGGER.debug( "purging " + removalKeys.size() + " expired cache records" );
            localDB.removeAll( DB, removalKeys );
        }
        else
        {
            LOGGER.trace( "purger examined " + counter + " records and did not discover any expired cache records" );
        }

        return removalKeys.size() >= MAX_REMOVALS_PER_CYCLE;
    }

    private class PurgerTask extends TimerTask
    {
        @Override
        public void run( )
        {
            try
            {
                purgeExpiredRecords();
            }
            catch ( LocalDBException e )
            {
                LOGGER.error( "error while running purger task: " + e.getMessage(), e );
            }
        }
    }

    @Override
    public int itemCount( )
    {
        try
        {
            return localDB.size( DB );
        }
        catch ( LocalDBException e )
        {
            LOGGER.error( "unexpected error reading size from localDB: " + e.getMessage(), e );
        }
        return 0;
    }
}
