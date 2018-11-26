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

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

public class LocalDBAdaptor implements LocalDB
{
    private final LocalDBProvider innerDB;

    private final PwmApplication pwmApplication;

    LocalDBAdaptor( final LocalDBProvider innerDB, final PwmApplication pwmApplication )
    {
        this.pwmApplication = pwmApplication;
        if ( innerDB == null )
        {
            throw new IllegalArgumentException( "innerDB can not be null" );
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
        markRead();
        return value;
    }


    public String get( final DB db, final String key ) throws LocalDBException
    {
        ParameterValidator.validateDBValue( db );
        ParameterValidator.validateKeyValue( key );

        final String value = innerDB.get( db, key );
        markRead();
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
        return innerDB.iterator( db );
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

        innerDB.putAll( db, keyValueMap );

        markWrite( keyValueMap.size() );
    }

    @WriteOperation
    public boolean put( final DB db, final String key, final String value ) throws LocalDBException
    {
        ParameterValidator.validateDBValue( db );
        ParameterValidator.validateKeyValue( key );
        ParameterValidator.validateValueValue( value );

        final boolean preExisting = innerDB.put( db, key, value );

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
        markWrite( 1 );
        return success;
    }

    @WriteOperation
    public boolean remove( final DB db, final String key ) throws LocalDBException
    {
        ParameterValidator.validateDBValue( db );
        ParameterValidator.validateKeyValue( key );

        final boolean result = innerDB.remove( db, key );
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
            innerDB.removeAll( db, keys );
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

    public long size( final DB db ) throws LocalDBException
    {
        ParameterValidator.validateDBValue( db );
        return innerDB.size( db );
    }

    @WriteOperation
    public void truncate( final DB db ) throws LocalDBException
    {
        ParameterValidator.validateDBValue( db );
            innerDB.truncate( db );
    }

    public Status status( )
    {
        return innerDB.getStatus();
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

            if ( key.length() <= 0 )
            {
                throw new LocalDBException( new ErrorInformation( PwmError.ERROR_INTERNAL, "key length cannot be zero length" ) );
            }

            if ( key.length() > LocalDB.MAX_KEY_LENGTH )
            {
                throw new LocalDBException( new ErrorInformation( PwmError.ERROR_INTERNAL, "key length " + key.length() + " is greater than max " + LocalDB.MAX_KEY_LENGTH ) );
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
                final String errorMsg = "value length " + value.length() + " is greater than max " + LocalDB.MAX_VALUE_LENGTH;
                throw new LocalDBException( new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg ) );
            }
        }
    }

    private void markRead()
    {
        if ( pwmApplication != null )
        {
            if ( pwmApplication.getStatisticsManager() != null )
            {
                pwmApplication.getStatisticsManager().updateEps( EpsStatistic.PWMDB_READS, 1 );
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
