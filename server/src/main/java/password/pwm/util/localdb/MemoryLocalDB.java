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

package password.pwm.util.localdb;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author Jason D. Rivard
 */
public class MemoryLocalDB implements LocalDBProvider
{
    private LocalDB.Status state = LocalDB.Status.NEW;

    private Map<LocalDB.DB, Map<String, String>> maps = new ConcurrentHashMap<>();

    private void operationPreCheck( ) throws LocalDBException
    {
        if ( state != LocalDB.Status.OPEN )
        {
            throw new IllegalStateException( "db is not open" );
        }
    }

    public MemoryLocalDB( )
    {
        for ( final LocalDB.DB db : LocalDB.DB.values() )
        {
            final Map<String, String> newMap = new ConcurrentHashMap<>();
            maps.put( db, newMap );
        }
    }

    @LocalDB.WriteOperation
    public void close( )
            throws LocalDBException
    {
        state = LocalDB.Status.CLOSED;
        for ( final LocalDB.DB db : LocalDB.DB.values() )
        {
            maps.get( db ).clear();
        }
    }

    public boolean contains( final LocalDB.DB db, final String key )
            throws LocalDBException
    {
        operationPreCheck();
        final Map<String, String> map = maps.get( db );
        return map.containsKey( key );
    }

    public String get( final LocalDB.DB db, final String key )
            throws LocalDBException
    {
        operationPreCheck();
        final Map<String, String> map = maps.get( db );
        return map.get( key );
    }

    @LocalDB.WriteOperation
    public void init(
            final File dbDirectory,
            final Map<String, String> initParameters,
            final Map<LocalDBProvider.Parameter, String> parameters
    )
            throws LocalDBException
    {
        final boolean readOnly = LocalDBUtility.hasBooleanParameter( Parameter.readOnly, parameters );
        if ( readOnly )
        {
            maps = Collections.unmodifiableMap( maps );
        }
        if ( state == LocalDB.Status.OPEN )
        {
            throw new IllegalStateException( "cannot init db more than one time" );
        }
        if ( state == LocalDB.Status.CLOSED )
        {
            throw new IllegalStateException( "db is closed" );
        }
        state = LocalDB.Status.OPEN;
    }

    public LocalDB.LocalDBIterator<Map.Entry<String, String>> iterator( final LocalDB.DB db ) throws LocalDBException
    {
        return new MapIterator( db );
    }

    @LocalDB.WriteOperation
    public void putAll( final LocalDB.DB db, final Map<String, String> keyValueMap )
            throws LocalDBException
    {
        operationPreCheck();

        if ( keyValueMap != null )
        {
            final Map<String, String> map = maps.get( db );
            map.putAll( keyValueMap );
        }
    }

    @LocalDB.WriteOperation
    public boolean put( final LocalDB.DB db, final String key, final String value )
            throws LocalDBException
    {
        operationPreCheck();

        final Map<String, String> map = maps.get( db );
        return null != map.put( key, value );
    }

    @LocalDB.WriteOperation
    public boolean putIfAbsent( final LocalDB.DB db, final String key, final String value )
            throws LocalDBException
    {
        operationPreCheck();

        final Map<String, String> map = maps.get( db );
        final String oldValue = map.putIfAbsent( key, value );
        return oldValue == null;
    }

    @LocalDB.WriteOperation
    public boolean remove( final LocalDB.DB db, final String key )
            throws LocalDBException
    {
        operationPreCheck();

        final Map<String, String> map = maps.get( db );
        return null != map.remove( key );
    }

    public long size( final LocalDB.DB db )
            throws LocalDBException
    {
        operationPreCheck();

        final Map<String, String> map = maps.get( db );
        return map.size();
    }

    @LocalDB.WriteOperation
    public void truncate( final LocalDB.DB db )
            throws LocalDBException
    {
        operationPreCheck();

        final Map<String, String> map = maps.get( db );
        map.clear();
    }

    public void removeAll( final LocalDB.DB db, final Collection<String> keys ) throws LocalDBException
    {
        operationPreCheck();

        maps.get( db ).keySet().removeAll( keys );
    }

    public LocalDB.Status getStatus( )
    {
        return state;
    }

    @Override
    public Map<String, Serializable> debugInfo( )
    {
        return Collections.emptyMap();
    }


    private class MapIterator implements LocalDB.LocalDBIterator<Map.Entry<String, String>>
    {
        private final Iterator<Map.Entry<String, String>> iterator;

        private MapIterator( final LocalDB.DB db )
        {
            iterator = maps.get( db ).entrySet().iterator();
        }

        public boolean hasNext( )
        {
            return iterator.hasNext();
        }

        public Map.Entry<String, String> next( )
        {
            return iterator.next();
        }

        public void remove( )
        {
            iterator.remove();
        }

        public void close( )
        {
        }
    }

    public File getFileLocation( )
    {
        return null;
    }

    @Override
    public Set<Flag> flags( )
    {
        return Collections.emptySet();
    }
}
