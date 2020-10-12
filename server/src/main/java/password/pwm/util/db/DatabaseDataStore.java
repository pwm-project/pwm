/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.util.db;

import password.pwm.error.PwmDataStoreException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.DataStore;
import password.pwm.util.java.ClosableIterator;

import java.util.Map;

public class DatabaseDataStore implements DataStore
{
    private final DatabaseService databaseService;
    private final DatabaseTable table;

    public DatabaseDataStore( final DatabaseService databaseService, final DatabaseTable table )
    {
        this.databaseService = databaseService;
        this.table = table;
    }

    @Override
    public void close( ) throws PwmDataStoreException
    {
    }

    @Override
    public boolean contains( final String key ) throws PwmDataStoreException, PwmUnrecoverableException
    {
        return databaseService.getAccessor().contains( table, key );
    }

    @Override
    public String get( final String key ) throws PwmDataStoreException, PwmUnrecoverableException
    {
        return databaseService.getAccessor().get( table, key );
    }

    @Override
    public ClosableIterator<Map.Entry<String, String>> iterator( ) throws PwmDataStoreException, PwmUnrecoverableException
    {
        return databaseService.getAccessor().iterator( table );
    }

    @Override
    public Status status( )
    {
        if ( databaseService == null )
        {
            return null;
        }

        return Status.OPEN;
    }

    @Override
    public boolean put( final String key, final String value ) throws PwmDataStoreException, PwmUnrecoverableException
    {
        return databaseService.getAccessor().put( table, key, value );
    }

    @Override
    public boolean putIfAbsent( final String key, final String value ) throws PwmDataStoreException, PwmUnrecoverableException
    {
        return databaseService.getAccessor().putIfAbsent( table, key, value );
    }

    @Override
    public void remove( final String key ) throws PwmDataStoreException, PwmUnrecoverableException
    {
        databaseService.getAccessor().remove( table, key );
    }

    @Override
    public long size( ) throws PwmDataStoreException, PwmUnrecoverableException
    {
        return databaseService.getAccessor().size( table );
    }
}
