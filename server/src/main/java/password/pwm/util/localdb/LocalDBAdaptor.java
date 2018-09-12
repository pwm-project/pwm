/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.util.localdb;

import password.pwm.PwmApplication;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.svc.stats.EpsStatistic;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class LocalDBAdaptor implements LocalDB
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LocalDBAdaptor.class );

    private final LocalDBProvider innerDB;

    private final SizeCacheManager sizeCacheManager;
    private final PwmApplication pwmApplication;

    LocalDBAdaptor( final LocalDBProvider innerDB, final PwmApplication pwmApplication )
    {
        this.pwmApplication = pwmApplication;
        if ( innerDB == null )
        {
            throw new IllegalArgumentException( "innerDB can not be null" );
        }

        if ( innerDB.flags().contains( LocalDBProvider.Flag.SlowSizeOperations ) )
        {
            sizeCacheManager = new SizeCacheManager();
        }
        else
        {
            sizeCacheManager = null;
        }

        this.innerDB = innerDB;

    }

    public File getFileLocation( )
    {
        return innerDB.getFileLocation();
    }

    @WriteOperation
    public void close( ) throws LocalDBException
    {
        innerDB.close();
    }

    public boolean contains( final DB db, final String key ) throws LocalDBException
    {
        ParameterValidator.validateDBValue( db );
        ParameterValidator.validateKeyValue( key );

        final boolean value = innerDB.contains( db, key );
        markRead( 1 );
        return value;
    }


    public String get( final DB db, final String key ) throws LocalDBException
    {
        ParameterValidator.validateDBValue( db );
        ParameterValidator.validateKeyValue( key );

        final String value = innerDB.get( db, key );
        markRead( 1 );
        return value;
    }

    @WriteOperation
    public void init( final File dbDirectory, final Map<String, String> initParameters, final Map<LocalDBProvider.Parameter, String> parameters ) throws LocalDBException
    {
        innerDB.init( dbDirectory, initParameters, parameters );
    }

    public LocalDBIterator<String> iterator( final DB db ) throws LocalDBException
    {
        ParameterValidator.validateDBValue( db );
        final LocalDBIterator<String> innerIterator = innerDB.iterator( db );
        return new SizeIterator<String>( db, innerIterator );
    }

    private class SizeIterator<K> implements LocalDBIterator<String>
    {
        private final LocalDBIterator<String> innerIterator;
        private final DB db;
        private String key;

        SizeIterator( final DB db, final LocalDBIterator<String> innerIterator )
        {
            this.innerIterator = innerIterator;
            this.db = db;
        }

        public boolean hasNext( )
        {
            return innerIterator.hasNext();
        }

        public String next( )
        {
            key = innerIterator.next();
            return key;
        }

        public void remove( )
        {
            innerIterator.remove();
            if ( sizeCacheManager != null )
            {
                try
                {
                    sizeCacheManager.decrementSize( db );
                }
                catch ( Exception e )
                {
                    throw new RuntimeException( e );
                }
            }
        }

        @Override
        public void close( )
        {
            innerIterator.close();
        }
    }

    public Map<String, Serializable> debugInfo( )
    {
        return innerDB.debugInfo();
    }

    @WriteOperation
    public void putAll( final DB db, final Map<String, String> keyValueMap ) throws LocalDBException
    {
        ParameterValidator.validateDBValue( db );
        for ( final Map.Entry<String, String> entry : keyValueMap.entrySet() )
        {
            final String loopKey = entry.getKey();
            final String loopValue = entry.getValue();
            try
            {
                ParameterValidator.validateKeyValue( loopKey );
                ParameterValidator.validateValueValue( loopValue );
            }
            catch ( NullPointerException e )
            {
                throw new NullPointerException( e.getMessage() + " for transaction record: '" + loopKey + "'" );
            }
            catch ( IllegalArgumentException e )
            {
                throw new IllegalArgumentException( e.getMessage() + " for transaction record: '" + loopKey + "'" );
            }
        }

        try
        {
            innerDB.putAll( db, keyValueMap );
        }
        finally
        {
            if ( sizeCacheManager != null )
            {
                sizeCacheManager.clearSize( db );
            }
        }

        markWrite( keyValueMap.size() );
    }

    @WriteOperation
    public boolean put( final DB db, final String key, final String value ) throws LocalDBException
    {
        ParameterValidator.validateDBValue( db );
        ParameterValidator.validateKeyValue( key );
        ParameterValidator.validateValueValue( value );

        final boolean preExisting = innerDB.put( db, key, value );
        if ( !preExisting )
        {
            if ( sizeCacheManager != null )
            {
                sizeCacheManager.incrementSize( db );
            }
        }

        markWrite( 1 );
        return preExisting;
    }

    @WriteOperation
    public boolean putIfAbsent( final DB db, final String key, final String value ) throws LocalDBException
    {
        ParameterValidator.validateDBValue( db );
        ParameterValidator.validateKeyValue( key );
        ParameterValidator.validateValueValue( value );

        final boolean success = innerDB.putIfAbsent( db, key, value );
        if ( success )
        {
            if ( sizeCacheManager != null )
            {
                sizeCacheManager.incrementSize( db );
            }
        }

        markWrite( 1 );
        return success;
    }

    @WriteOperation
    public boolean remove( final DB db, final String key ) throws LocalDBException
    {
        ParameterValidator.validateDBValue( db );
        ParameterValidator.validateKeyValue( key );

        final boolean result = innerDB.remove( db, key );
        if ( result )
        {
            if ( sizeCacheManager != null )
            {
                sizeCacheManager.decrementSize( db );
            }
        }

        markWrite( 1 );
        return result;
    }

    @WriteOperation
    public void removeAll( final DB db, final Collection<String> keys ) throws LocalDBException
    {
        ParameterValidator.validateDBValue( db );
        for ( final String loopKey : keys )
        {
            try
            {
                ParameterValidator.validateValueValue( loopKey );
            }
            catch ( NullPointerException e )
            {
                throw new NullPointerException( e.getMessage() + " for transaction record: '" + loopKey + "'" );
            }
            catch ( IllegalArgumentException e )
            {
                throw new IllegalArgumentException( e.getMessage() + " for transaction record: '" + loopKey + "'" );
            }
        }

        if ( keys.size() > 1 )
        {
            try
            {
                innerDB.removeAll( db, keys );
            }
            finally
            {
                if ( sizeCacheManager != null )
                {
                    sizeCacheManager.clearSize( db );
                }
            }
        }
        else
        {
            for ( final String key : keys )
            {
                remove( db, key );
            }
        }

        markWrite( keys.size() );
    }

    public int size( final DB db ) throws LocalDBException
    {
        ParameterValidator.validateDBValue( db );
        if ( sizeCacheManager != null )
        {
            return sizeCacheManager.getSizeForDB( db, innerDB );
        }
        else
        {
            return innerDB.size( db );
        }
    }

    @WriteOperation
    public void truncate( final DB db ) throws LocalDBException
    {
        ParameterValidator.validateDBValue( db );
        try
        {
            innerDB.truncate( db );
        }
        finally
        {
            if ( sizeCacheManager != null )
            {
                sizeCacheManager.clearSize( db );
            }
        }
    }

    public Status status( )
    {
        if ( innerDB == null )
        {
            return Status.CLOSED;
        }

        return innerDB.getStatus();
    }

    private static class SizeCacheManager
    {
        private static final Integer CACHE_DIRTY = -1;
        private static final Integer CACHE_WORKING = -2;

        private final ConcurrentMap<DB, Integer> sizeCache = new ConcurrentHashMap<>();

        private SizeCacheManager( )
        {
            for ( final DB db : DB.values() )
            {
                sizeCache.put( db, CACHE_DIRTY );
            }
        }

        private void incrementSize( final DB db )
        {
            modifySize( db, +1 );
        }

        private void decrementSize( final DB db )
        {
            modifySize( db, -1 );
        }

        private void modifySize( final DB db, final int amount )
        {
            // retrieve the current cache size.
            final Integer cachedSize = sizeCache.get( db );

            //update the cached value only if there is a meaningful value cached.
            if ( cachedSize >= 0 )
            {

                // calculate the new value
                final int newSize = cachedSize + amount;

                // replace the cached value with the new value, only if it hasn't been touched by another thread since it was
                // retrieved from the cache a few lines ago.
                if ( !sizeCache.replace( db, cachedSize, newSize ) )
                {

                    // the cache was modified by some other thread, and so is no longer accurate.  Mark it dirty.
                    clearSize( db );
                }
            }

        }

        private void clearSize( final DB db )
        {
            sizeCache.put( db, CACHE_DIRTY );
        }

        private int getSizeForDB( final DB db, final LocalDBProvider localDBProvider ) throws LocalDBException
        {
            // read the cached size out of the cache store
            final Integer cachedSize = sizeCache.get( db );

            if ( cachedSize != null && cachedSize >= 0 )
            {
                // if there is a good cache value and its not dirty (-1) or being populated by another thread (-2)
                return cachedSize;
            }

            final long beginTime = System.currentTimeMillis();

            // mark the cache as population in progress
            sizeCache.put( db, CACHE_WORKING );

            // read the "real" value.  this is the line that might take a long time
            final int theSize = localDBProvider.size( db );
            final TimeDuration timeDuration = TimeDuration.fromCurrent( beginTime );

            // so long as nothing else has touched the cache (perhaps another thread populated it, or someone else marked it dirty, then
            // go ahead and update it.
            final boolean savedInCache = sizeCache.replace( db, CACHE_WORKING, theSize );

            final StringBuilder debugMsg = new StringBuilder();
            debugMsg.append( "performed real size lookup of " ).append( theSize ).append( " for " ).append( db );
            debugMsg.append( ": " ).append( timeDuration.asCompactString() );
            debugMsg.append( savedInCache ? ", cached" : ", not cached" );
            LOGGER.debug( debugMsg );

            return theSize;
        }

    }

    private static class ParameterValidator
    {
        private static void validateDBValue( final LocalDB.DB db )
        {
            if ( db == null )
            {
                throw new NullPointerException( "db cannot be null" );
            }
        }

        private static void validateKeyValue( final String key ) throws LocalDBException
        {
            if ( key == null )
            {
                throw new NullPointerException( "key cannot be null" );
            }

            if ( key.length() < 0 )
            {
                throw new LocalDBException( new ErrorInformation( PwmError.ERROR_UNKNOWN, "key length cannot be zero length" ) );
            }

            if ( key.length() > LocalDB.MAX_KEY_LENGTH )
            {
                throw new LocalDBException( new ErrorInformation( PwmError.ERROR_UNKNOWN, "key length " + key.length() + " is greater than max " + LocalDB.MAX_KEY_LENGTH ) );
            }
        }

        private static void validateValueValue( final String value ) throws LocalDBException
        {
            if ( value == null )
            {
                throw new NullPointerException( "value cannot be null" );
            }

            if ( value.length() > LocalDB.MAX_VALUE_LENGTH )
            {
                throw new LocalDBException( new ErrorInformation( PwmError.ERROR_UNKNOWN, "value length " + value.length() + " is greater than max " + LocalDB.MAX_VALUE_LENGTH ) );
            }
        }
    }

    private void markRead( final int events )
    {
        if ( pwmApplication != null )
        {
            if ( pwmApplication.getStatisticsManager() != null )
            {
                pwmApplication.getStatisticsManager().updateEps( EpsStatistic.PWMDB_READS, events );
            }
        }
    }

    private void markWrite( final int events )
    {
        if ( pwmApplication != null )
        {
            if ( pwmApplication.getStatisticsManager() != null )
            {
                pwmApplication.getStatisticsManager().updateEps( EpsStatistic.PWMDB_WRITES, events );
            }
        }
    }
}
