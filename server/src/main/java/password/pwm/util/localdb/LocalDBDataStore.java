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

import password.pwm.error.PwmDataStoreException;
import password.pwm.util.DataStore;
import password.pwm.util.java.ClosableIterator;

import java.util.Map;

public class LocalDBDataStore implements DataStore
{
    private final LocalDB localDB;
    private final LocalDB.DB db;

    public LocalDBDataStore( final LocalDB localDB, final LocalDB.DB db )
    {
        this.localDB = localDB;
        this.db = db;
    }

    public void close( ) throws PwmDataStoreException
    {
        localDB.close();
    }

    public boolean contains( final String key ) throws PwmDataStoreException
    {
        return localDB.contains( db, key );
    }

    public String get( final String key ) throws PwmDataStoreException
    {
        return localDB.get( db, key );
    }

    public ClosableIterator<Map.Entry<String, String>> iterator( ) throws PwmDataStoreException
    {
        return localDB.iterator( db );
    }

    public void putAll( final Map<String, String> keyValueMap ) throws PwmDataStoreException
    {
        localDB.putAll( db, keyValueMap );
    }

    public Status status( )
    {
        final LocalDB.Status dbStatus = localDB.status();
        if ( dbStatus == null )
        {
            return null;
        }
        switch ( dbStatus )
        {
            case OPEN:
                return Status.OPEN;

            case CLOSED:
                return Status.CLOSED;

            case NEW:
                return Status.NEW;

            default:
                throw new IllegalStateException( "unknown localDB state" );
        }
    }

    public boolean put( final String key, final String value ) throws PwmDataStoreException
    {
        return localDB.put( db, key, value );
    }

    public boolean putIfAbsent( final String key, final String value ) throws PwmDataStoreException
    {
        return localDB.putIfAbsent( db, key, value );
    }

    public void remove( final String key ) throws PwmDataStoreException
    {
        localDB.remove( db, key );
    }

    public long size( ) throws PwmDataStoreException
    {
        return localDB.size( db );
    }
}
